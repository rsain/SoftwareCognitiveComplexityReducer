package neo.reducecognitivecomplexity.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.DefaultPositionUpdater;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IPositionUpdater;
import org.eclipse.jface.text.Position;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.jdt.Utils;

/**
 * Deterministically applies method extractions ({@code ExtractMethodRefactoring}) to workspace
 * Java source files based on specifications in {@code .results} files.
 * <p>
 * <b>Core Refactoring Pipeline:</b>
 * <ol>
 *   <li><b>Pristine AST Mapping:</b> Resolves initial statement offsets in a single pass over the
 *       unmodified AST using exact offset matching.</li>
 *   <li><b>Live Marker Tracking:</b> Tracks group boundaries using zero-length {@link Position} points
 *       to prevent range ambiguity during edits. Shared-end telescopic extractions automatically redirect 
 *       enclosing sibling end markers to newly inserted call locations.</li>
 *   <li><b>Global Bottom-Up Execution:</b> Executes extractions globally in descending start-offset order,
 *       prioritizing nested (shorter) groups when start offsets coincide.</li>
 * </ol>
 * <p>
 * Invalid live ranges (length <= 0) are discarded prior to JDT invocation to avoid generic refactoring errors.
 * Individual extractions causing compilation errors are safely reverted via undo changes, leaving valid edits intact on disk.
 */
public class SavedSolutionProcessor {

    private static final Logger LOGGER = Logger.getLogger(SavedSolutionProcessor.class.getName());
    private static final String LOG_FILE_NAME = "refactoring_execution.log";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int RESULTS_MIN_COLUMNS = 9;

    /**
     * The number of nearby AST statements to log for diagnostic purposes when an exact match is not found.
     * These statements are strictly informational and are never used to determine match decisions.
     */
    private static final int NEARBY_STATEMENTS_TO_LOG = 5;

    /**
     * Data container representing a parsed refactoring solution and its execution status.
     * <p>
     * Encapsulates target source metadata, cognitive complexity metrics, parsed extraction offset groups,
     * and aggregated execution metrics accumulated during refactoring processing.
     */
    private static class ParsedResult {
        String project, folder, packageName, className, methodName, solutionString;
        int initialComplexity;
        int lineNumber;
        String resultsFileName;

     // Read directly from the .results file to export them as-is
        // to the final execution CSV—see writeExecutionResultsCsv(). These are properties of
        // the solution selected by the solver, rather than values computed by SavedSolutionProcessor.
        int reductionComplexity;
        int finalComplexity;
        long executionTimeMs = 0;

        List<List<Integer>> parsedGroups = new ArrayList<>();

        int totalExtractions = 0;
        int successfulExtractions = 0;
        boolean failed = false;
    }

    /**
     * Represents a single extraction group tracked across sequential AST modifications.
     * <p>
     * Uses zero-length {@link Position} markers for start and end boundaries to avoid range adjustment
     * ambiguities during document updates.
     */
    private static class TrackedExtraction {
        Position startMarker;
        Position endMarker;
        int initialStartOffset;
        int initialEndOffset;
        int initialLength;
        int extractionIndex;
        String methodOwner;
        List<Integer> rawOffsets;
        ParsedResult parentSolution;
    }

    /**
     * Tracks and calculates execution metrics for batch refactoring operations.
     * <p>
     * Maintains aggregate statistics, success rates, and failure classifications 
     * for individual extractions and overall solutions.
     */
    private static class ExecutionMetrics {
        int totalSolutions = 0;
        int successfulSolutions = 0;
        int failedSolutions = 0;

        int totalExtractions = 0;
        int successfulExtractions = 0;
        int failedExtractions = 0;
        Map<String, Integer> extractionFailureBreakdown = new HashMap<>();

        void recordExtractionSuccess() {
            successfulExtractions++;
            totalExtractions++;
        }

        void recordExtractionFailure(String type) {
            failedExtractions++;
            totalExtractions++;
            extractionFailureBreakdown.put(type, extractionFailureBreakdown.getOrDefault(type, 0) + 1);
        }

        void computeSolutionMetrics(List<ParsedResult> allSolutions) {
            totalSolutions = allSolutions.size();
            successfulSolutions = 0;
            failedSolutions = 0;

            for (ParsedResult sol : allSolutions) {
                if (!sol.failed && sol.totalExtractions > 0 && sol.successfulExtractions == sol.totalExtractions) {
                    successfulSolutions++;
                } else {
                    failedSolutions++;
                }
            }
        }
    }

    /**
     * Executes batch refactoring solutions from all {@code .results} files in the specified directory.
     * <p>
     * Parses extraction groups, aggregates actions by target Java source file, applies the AST
     * transformations sequentially, and outputs execution logs alongside a CSV summary.
     *
     * @param resultsFolderPath the directory path containing the {@code .results} solution files
     */
    public void applySavedSolutions(String resultsFolderPath) {
        File folder = new File(resultsFolderPath);
        File[] rawFiles = folder.listFiles((dir, name) -> name.endsWith(".results"));

        if (rawFiles == null || rawFiles.length == 0) {
            LOGGER.warning("No tracking execution .results files found in: " + resultsFolderPath);
            return;
        }

        File[] files = Arrays.stream(rawFiles)
                .filter(this::isOptimalResult)
                .toArray(File[]::new);

        if (files.length == 0) {
            LOGGER.warning("No .results files with optimal solution status found in: " + resultsFolderPath);
            return;
        }

        ExecutionMetrics metrics = new ExecutionMetrics();
        File logFile = new File(folder, LOG_FILE_NAME);
        List<ParsedResult> allParsedSolutions = new ArrayList<>();

        LOGGER.info("Starting refactoring execution process. Found " + files.length + " .results file(s).");

        try (PrintWriter logWriter = new PrintWriter(new BufferedWriter(new FileWriter(logFile, false)))) {

            writeLogHeader(logWriter);
            Map<String, List<ParsedResult>> filesGroupedByWorkspacePath = new HashMap<>();

            for (File f : files) {
                try {
                    ParsedResult result = parseResultsFile(f);
                    if (result != null && result.solutionString != null && !result.solutionString.equals("[]")) {
                        result.parsedGroups = parseSolutionGroups(result.solutionString);
                        result.totalExtractions = result.parsedGroups.size();

                        allParsedSolutions.add(result);
                        String workspacePath = resolveWorkspaceRelativeJavaPath(result);
                        filesGroupedByWorkspacePath.computeIfAbsent(workspacePath, k -> new ArrayList<>()).add(result);

                        logParsedSolution(logWriter, result, workspacePath);
                    }
                } catch (Exception e) {
                    String errorMsg = "Failed parsing metadata source sheet: " + f.getName() + " -> " + e.getMessage();
                    LOGGER.log(Level.SEVERE, errorMsg, e);
                    logFailure(logWriter, f.getName(), f.getName(), "N/A", "N/A", "PARSING_ERROR", errorMsg);

                    ParsedResult failedParse = new ParsedResult();
                    failedParse.resultsFileName = f.getName();
                    failedParse.failed = true;
                    allParsedSolutions.add(failedParse);
                }
            }

            int currentExtractionIndex = 0;
            int totalGlobalExtractions = allParsedSolutions.stream().mapToInt(s -> s.totalExtractions).sum();

            for (Map.Entry<String, List<ParsedResult>> entry : filesGroupedByWorkspacePath.entrySet()) {
                String workspacePath = entry.getKey();
                List<ParsedResult> actions = entry.getValue();

                try {
                    LOGGER.info(String.format("Processing Java file context: %s (%d solution(s))", workspacePath, actions.size()));
                    currentExtractionIndex = processSingleFileRefactorings(workspacePath, actions, logWriter, metrics, currentExtractionIndex, totalGlobalExtractions);
                } catch (Exception e) {
                    String errorMsg = "Critical crash applying changes to file context " + workspacePath + ": " + e.getMessage();
                    LOGGER.log(Level.SEVERE, errorMsg, e);
                    for (ParsedResult action : actions) {
                        action.failed = true;
                        logFailure(logWriter, action.resultsFileName, workspacePath, "N/A", action.solutionString, "CRITICAL_FILE_ERROR", errorMsg);
                    }
                }
            }

            metrics.computeSolutionMetrics(allParsedSolutions);
            writeSummaryHeader(logWriter, metrics);
            logExecutionSummary(metrics, logFile.getAbsolutePath());

            writeExecutionResultsCsv(folder, allParsedSolutions);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not initialize execution log file: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a single result file containing cognitive complexity reduction output into a {@link ParsedResult} instance.
     * <p>
     * Reads the semicolon-delimited data row, validates structural column requirements, extracts project, class,
     * and method metadata alongside cognitive complexity metrics, and resolves the target line number from the filename.
     *
     * @param file the result file to parse
     * @return a populated {@link ParsedResult} object, or {@code null} if the data row is empty or missing
     * @throws IOException if an I/O error occurs, required columns are missing, or mandatory numerical metrics cannot be parsed
     */
    private ParsedResult parseResultsFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine();
            String dataLine = br.readLine();

            if (dataLine == null || dataLine.trim().isEmpty()) return null;

            String[] columns = dataLine.split(";", -1);
            if (columns.length < RESULTS_MIN_COLUMNS) {
                throw new IOException(String.format(
                        "Wrong date in file %s: excepting %d columns but %d found",
                        file.getName(), RESULTS_MIN_COLUMNS, columns.length));
            }

            ParsedResult pr = new ParsedResult();

            pr.project = columns[1].trim();
            pr.folder = columns[2].trim();
            pr.packageName = columns[3].trim();
            pr.className = columns[4].trim();
            pr.methodName = columns[5].trim();
            try {
                pr.initialComplexity = Integer.parseInt(columns[7].trim());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid initial cognitive complexity in file " + file.getName() + ": '" + columns[7] + "'", e);
            }
            pr.solutionString = columns[8].trim();

            if (columns.length > 10) {
                try {
                    pr.reductionComplexity = Integer.parseInt(columns[10].trim());
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid reduction of cognitive complexity in file " + file.getName() + ": '" + columns[10] + "'");
                }
            }
            if (columns.length > 11) {
                try {
                    pr.finalComplexity = Integer.parseInt(columns[11].trim());
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid final cognitive complexity in file " + file.getName() + ": '" + columns[11] + "'");
                }
            }

            pr.lineNumber = parseLineNumberFromFileName(file.getName());
            pr.resultsFileName = file.getName();

            return pr;
        }
    }

    /**
     * Extracts the target line number suffix from a results filename.
     * <p>
     * Strips the {@code .results} extension if present, locates the final hyphen delimiter,
     * and attempts to parse the trailing substring as an integer line number.
     * If no hyphen is present or numerical parsing fails, logs a diagnostic warning and defaults to {@code 0}.
     *
     * @param fileName the filename from which to extract the line number
     * @return the parsed line number, or {@code 0} if no valid line number suffix is present
     */
    private int parseLineNumberFromFileName(String fileName) {
        String name = fileName.endsWith(".results") ?
                fileName.substring(0, fileName.length() - 8) : fileName;
        int lastHyphen = name.lastIndexOf('-');
        if (lastHyphen != -1) {
            try {
                return Integer.parseInt(name.substring(lastHyphen + 1));
            } catch (NumberFormatException e) {
                LOGGER.warning("Could not parse line number suffix from filename: " + fileName);
            }
        }
        return 0;
    }

    /**
     * Resolves the workspace-relative path to a Java source file from a parsed refactoring result.
     * <p>
     * Converts package dot-notation into directory paths and constructs
     * a formatted path string in the format {@code /<project>/<folder>/<package_path>/<className>.java}.
     *
     * @param pr the parsed result containing target project, package, and class metadata
     * @return the workspace-relative path to the corresponding Java source file
     */
    private String resolveWorkspaceRelativeJavaPath(ParsedResult pr) {
        String packageSegment = (pr.packageName == null || pr.packageName.trim().isEmpty())
                ? ""
                : pr.packageName.replace('.', '/') + "/";
        return "/" + pr.project + "/" + pr.folder + "/" + packageSegment + pr.className + ".java";
    }

    /**
     * Parses a solution string into structured groups of integer source offsets.
     * <p>
     * Extracts bracket-enclosed lists of integers (e.g., {@code "[10, 11] [20, 21]"}) 
     * using regular expressions, ignoring empty groups and invalid numerical values.
     *
     * @param solutionString the raw solution string containing bracketed offset groups
     * @return a list of integer lists, where each inner list represents the offsets 
     *         for a single extraction group; returns an empty list if input is null or invalid
     */
    private static List<List<Integer>> parseSolutionGroups(String solutionString) {
        List<List<Integer>> result = new ArrayList<>();
        if (solutionString == null || solutionString.trim().isEmpty()) return result;

        Pattern pattern = Pattern.compile("\\[([0-9\\s,]+)\\]");
        Matcher matcher = pattern.matcher(solutionString);
        while (matcher.find()) {
            String groupStr = matcher.group(1).trim();
            if (groupStr.isEmpty()) continue;
            String[] parts = groupStr.split(",");
            List<Integer> offsets = new ArrayList<>();
            for (String p : parts) {
                p = p.trim();
                if (!p.isEmpty()) {
                    try {
                        offsets.add(Integer.parseInt(p));
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (!offsets.isEmpty()) {
                result.add(offsets);
            }
        }
        return result;
    }

    /**
     * Executes all planned refactoring operations for a single source file through a three-phase pipeline:
     * target node resolution on the pristine AST, bottom-up ordering, and sequential JDT refactoring application.
     *
     * @param workspacePath          the workspace path of the target Java source file
     * @param actions                the list of parsed refactoring solutions to apply
     * @param logWriter              the output writer for detailed execution logs
     * @param metrics                the accumulator for tracking refactoring success/failure metrics
     * @param currentExtractionIndex the current running total of processed extractions
     * @param totalExtractions       the overall count of extractions across all files
     * @return the updated extraction index after processing extractions in this file
     * @throws Exception if an error occurs during file buffer or refactoring operations
     */
    private int processSingleFileRefactorings(String workspacePath, List<ParsedResult> actions, PrintWriter logWriter,
                                              ExecutionMetrics metrics, int currentExtractionIndex, int totalExtractions) throws Exception {
        if (actions.isEmpty()) return currentExtractionIndex;

        String projectName = actions.get(0).project;
        IProject project = neo.reducecognitivecomplexity.core.eclipse.Utils.getProject(projectName);
        if (project == null || !project.isOpen()) {
            String reason = "Cannot apply solutions because target workspace project is unavailable or closed: " + projectName;
            LOGGER.severe(reason);
            for (ParsedResult action : actions) {
                action.failed = true;
                logFailure(logWriter, action.resultsFileName, workspacePath, "ALL_METHODS", action.solutionString, "PROJECT_UNAVAILABLE", reason);
            }
            return currentExtractionIndex;
        }

	    // AST of the ORIGINAL, unmodified file. It is used ONLY ONCE to
	    // locate, via exact matching, the start nodes of ALL extractions
	    // across ALL solutions for this file before applying any changes.
        CompilationUnit pristineCU = neo.reducecognitivecomplexity.core.jdt.Utils.createCompilationUnitFromFileInWorkspace(workspacePath);
        ICompilationUnit icu = (ICompilationUnit) pristineCU.getJavaElement();

        IFile file = (IFile) icu.getResource();
        IPath path = file.getFullPath();
        ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();

        try {
            bufferManager.connect(path, LocationKind.IFILE, new NullProgressMonitor());
            ITextFileBuffer textFileBuffer = bufferManager.getTextFileBuffer(path, LocationKind.IFILE);
            IDocument liveDocument = textFileBuffer.getDocument();

            String positionCategory = "EXTRACTION_MARKERS_" + path.toString().hashCode() + "_" + System.nanoTime();
            liveDocument.addPositionCategory(positionCategory);
            IPositionUpdater positionUpdater = new DefaultPositionUpdater(positionCategory);
            liveDocument.addPositionUpdater(positionUpdater);

            try {
                List<TrackedExtraction> allExtractions = new ArrayList<>();

                // Locate, on the ORIGINAL AST, all nodes for all
                // solutions in the file and register their live positions.
                for (ParsedResult action : actions) {
                    List<List<Integer>> groups = action.parsedGroups;

                    if (groups.isEmpty()) {
                        action.failed = true;
                        metrics.recordExtractionFailure("EMPTY_SOLUTION");
                        logFailure(logWriter, action.resultsFileName, workspacePath, action.methodName, action.solutionString, "EMPTY_SOLUTION", "Solution string contains no extraction groups.");
                        continue;
                    }

                    MethodDeclaration method = findMethodDeclaration(pristineCU, action.methodName, action.lineNumber);
                    if (method == null) {
                        String reason = "Method '" + action.methodName + "' not found in the original AST of the file.";
                        LOGGER.warning(reason);
                        action.failed = true;
                        for (int i = 0; i < groups.size(); i++) {
                            metrics.recordExtractionFailure("METHOD_NOT_FOUND");
                        }
                        logFailure(logWriter, action.resultsFileName, workspacePath, action.methodName, action.solutionString, "METHOD_NOT_FOUND", reason);
                        continue;
                    }

                    for (int i = 0; i < groups.size(); i++) {
                        List<Integer> rawOffsets = groups.get(i);
                        int firstOffset = rawOffsets.get(0);
                        int lastOffset = rawOffsets.get(rawOffsets.size() - 1);

                        // Exact match only: no tolerance, no "closest node" heuristic.
                        // If there is no exact match, it is an input data error that
                        // must be reported, never guessed.
                        ASTNode startNode = findExactStatementNode(method, firstOffset);
                        ASTNode endNode = findExactStatementNode(method, lastOffset);

                        if (startNode == null || endNode == null) {
                        	String reason = "No EXACT match found in original AST for offset(s): "
                        	        + firstOffset + " .. " + lastOffset
                        	        + (startNode == null ? " [start not found]" : "")
                        	        + (endNode == null ? " [end not found]" : "")
                        	        + ". Nearest actual statements in method (diagnostic only, not used for decisions):"
                        	        + (startNode == null ? " start~[" + describeNearbyStatements(pristineCU, method, firstOffset) + "]" : "")
                        	        + (endNode == null ? " end~[" + describeNearbyStatements(pristineCU, method, lastOffset) + "]" : "");
                        	LOGGER.warning(reason);
                            action.failed = true;
                            metrics.recordExtractionFailure("TARGET_NODE_NOT_FOUND");
                            logFailure(logWriter, action.resultsFileName, workspacePath, action.methodName, action.solutionString, "TARGET_NODE_NOT_FOUND", reason);
                            continue;
                        }

                        int exactStart = startNode.getStartPosition();
                        int exactEnd = endNode.getStartPosition() + endNode.getLength();
                        int exactLength = exactEnd - exactStart;

                        // Two POINTS (length 0) are registered: one at the start of the
                        // first statement in the group, and another at the START (not the end)
                        // of the last statement in the group. The actual end of the selection
                        // will be recalculated at execution time by re-reading the CURRENT
                        // AST at that point: this ensures the selection end
                        // is always exact, regardless of how many prior edits have occurred,
                        // including those aligning right at that same boundary.
                        Position startMarker = new Position(exactStart, 0);
                        Position endMarker = new Position(endNode.getStartPosition(), 0);
                        liveDocument.addPosition(positionCategory, startMarker);
                        liveDocument.addPosition(positionCategory, endMarker);

                        TrackedExtraction target = new TrackedExtraction();
                        target.startMarker = startMarker;
                        target.endMarker = endMarker;
                        target.initialStartOffset = exactStart;
                        target.initialEndOffset = exactEnd;
                        target.initialLength = exactLength;
                        target.extractionIndex = i + 1;
                        target.methodOwner = action.methodName;
                        target.rawOffsets = rawOffsets;
                        target.parentSolution = action;

                        allExtractions.add(target);
                    }
                }

                // GLOBAL bottom-up ordering, by actual ORIGINAL start offset.
                //
                // Primary criterion: descending start offset. This prevents any group from having
                // its range shifted by the extraction of a group that appears AFTER it in the file.
                //
                // Tie-breaking criterion (EQUAL start offsets): the group with the SHORTER
                // length (the nested/inner one) is processed FIRST. This allows two nested groups 
                // sharing the same start offset to BOTH be extracted successfully, rather than having the larger group collapse the smaller one.
                allExtractions.sort((a, b) -> {
                    int cmp = Integer.compare(b.initialStartOffset, a.initialStartOffset);
                    if (cmp == 0) {
                        return Integer.compare(a.initialLength, b.initialLength);
                    }
                    return cmp;
                });

                // Execution. The start offset is taken directly from the tracked live point
                // (startMarker). The END offset is recalculated during each execution step by re-reading
                // the CURRENT AST at the tracked live point (endMarker) and obtaining the FRESH length
                // of the statement found there—instead of relying on an auto-adjusted length from
                // IPositionUpdater (which is ambiguous when the boundary of a prior edit coincides EXACTLY
                // with the boundary of this group).
                for (TrackedExtraction executionStep : allExtractions) {
                    currentExtractionIndex++;
                    ParsedResult action = executionStep.parentSolution;
                    String targetLabel = executionStep.methodOwner + " (line " + action.lineNumber + ")";

                    // The method's line number is included in the generated name to prevent
                    // collisions when the class contains multiple OVERLOADS with the same name.
                    //
                    // The name is generated in VALID camelCase WITHOUT underscores.
                    String generatedMethodName = executionStep.methodOwner
                            + "Extracted" + executionStep.extractionIndex
                            + "At" + action.lineNumber;
                    
                    generatedMethodName = Character.toLowerCase(generatedMethodName.charAt(0))
                            + generatedMethodName.substring(1);

                    StringBuilder traceBuffer = new StringBuilder();
                    traceBuffer.append(String.format("\n  * Full Parsed Solution    : %s", action.solutionString));
                    traceBuffer.append(String.format("\n  * Multi-Block Raw Offsets : %s", executionStep.rawOffsets.toString()));
                    traceBuffer.append(String.format("\n  * Initial Computed Range  : [%d .. %d] (len %d)", executionStep.initialStartOffset, executionStep.initialEndOffset, executionStep.initialLength));
                    traceBuffer.append(String.format("\n  * Generated Method Name   : %s", generatedMethodName));

                    long stepStartTime = System.currentTimeMillis();
                    try {
                        icu.makeConsistent(new NullProgressMonitor());
                        icu.reconcile(AST.JLS18, ICompilationUnit.FORCE_PROBLEM_DETECTION, null, new NullProgressMonitor());
                        CompilationUnit currentCU = neo.reducecognitivecomplexity.core.jdt.Utils.createCompilationUnitFromFileInWorkspace(workspacePath);

                        int liveStart = executionStep.startMarker.getOffset();
                        int liveEndAnchor = executionStep.endMarker.getOffset();

                        MethodDeclaration currentMethod = findMethodDeclaration(currentCU, action.methodName, action.lineNumber);
                        ASTNode currentEndNode = findExactStatementNode(currentMethod, liveEndAnchor);

                        int docLenNow = liveDocument.getLength();

                        // If no statement starting exactly at the tracked end point
                        // is found in the CURRENT AST, or if the start point falls outside the document boundaries,
                        // it is an unambiguous sign that this group has been rendered unrecoverable by a prior
                        // edit within the same solution. It is discarded BEFORE invoking JDT, with an explicit cause.
                        if (currentEndNode == null || liveStart < 0 || liveStart > docLenNow || liveEndAnchor < liveStart) {
                            String docContentNow = liveDocument.get();
                            String startContext = snippetAround(docContentNow, liveStart, 60);
                            String endContext = snippetAround(docContentNow, liveEndAnchor, 60);

                            String reason = "Could not accurately recalculate selection end boundary in current AST "
                                    + "(live start=" + liveStart + ", live end anchor=" + liveEndAnchor + ", current document length=" + docLenNow + "). "
                                    + "This indicates that the code in this group no longer exists in its original form, likely "
                                    + "consumed by a prior extraction within the same solution. Skipping without invoking JDT."
                                    + "\n  * DIAGNOSTIC — Method found in current AST: " + (currentMethod != null)
                                    + "\n  * DIAGNOSTIC — Actual context at live start (±60): " + startContext
                                    + "\n  * DIAGNOSTIC — Actual context at live end anchor (±60): " + endContext
                                    + (currentMethod != null ? "\n  * DIAGNOSTIC — Actual statements near end point: " + describeNearbyStatements(currentCU, currentMethod, liveEndAnchor) : "");
                            LOGGER.warning("Selection end boundary not recalculable for " + executionStep.methodOwner + " extraction #" + executionStep.extractionIndex);action.failed = true;
                            metrics.recordExtractionFailure("RANGE_ALREADY_CONSUMED");
                            logFailure(logWriter, action.resultsFileName, workspacePath, targetLabel, action.solutionString, "RANGE_ALREADY_CONSUMED", reason + traceBuffer);
                            continue;
                        }

                        int liveEnd = currentEndNode.getStartPosition() + currentEndNode.getLength();
                        int liveLength = liveEnd - liveStart;

                        traceBuffer.append(String.format("\n  * Recalculated Live Range : [%d .. %d] (len %d)", liveStart, liveEnd, liveLength));

                        if (liveLength <= 0 || liveEnd > docLenNow) {
                            String reason = "Recalculated range is invalid (live position [" + liveStart + " .. " + liveEnd + "), "
                                    + "current document length " + docLenNow + "). Skipping without invoking JDT.";
                            LOGGER.warning("Invalid recalculated range for " + executionStep.methodOwner + " extraction #" + executionStep.extractionIndex);
                            action.failed = true;
                            metrics.recordExtractionFailure("RANGE_ALREADY_CONSUMED");
                            logFailure(logWriter, action.resultsFileName, workspacePath, targetLabel, action.solutionString, "RANGE_ALREADY_CONSUMED", reason + traceBuffer);
                            continue;
                        }

                        String documentContent = liveDocument.get();
                        String selectedText = documentContent.substring(liveStart, liveEnd);
                        traceBuffer.append("\n  * Target Code Snippet     :\n");
                        traceBuffer.append(formatCodeForLog(selectedText, "    | "));

                        LOGGER.info(String.format("[%d/%d] Executing extraction -> Method: %s | Live Offset: [%d .. %d]",
                                currentExtractionIndex, totalExtractions, executionStep.methodOwner, liveStart, liveEnd));

                        org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring refactoring =
                                new org.eclipse.jdt.internal.corext.refactoring.code.ExtractMethodRefactoring(currentCU, liveStart, liveLength);

                        refactoring.setMethodName(generatedMethodName);

                        // IMPORTANT: DO NOT call setReplaceDuplicates(false) here.
                        // JDT's checkAllConditions() -> checkInitialConditions() internally invokes
                        // initializeDuplicates(), which unconditionally overrides fReplaceDuplicates
                        // based on whether it detects duplicate code across the ENTIRE CLASS (not just
                        // within the current method). Any value set prior to checkAllConditions() will be overwritten.
                        // Therefore, it must be set AFTERWARDS, right before createChange(), which is the
                        // only location that actually queries it (inside replaceDuplicates()).
                        // Without this ordering, an extraction with duplicate code anywhere in the class—even
                        // inside an unrelated method—would replace those copies with a call, corrupting
                        // the offsets of future extractions targeting that other method.
                        RefactoringStatus status = refactoring.checkAllConditions(new NullProgressMonitor());
                        refactoring.setReplaceDuplicates(false);

                        if (!status.isOK()) {
                            traceBuffer.append("\n  * Refactoring Preconditions FAILED:");
                            for (RefactoringStatusEntry entry : status.getEntries()) {
                                traceBuffer.append(String.format("\n    - [%s] %s", entry.getSeverity(), entry.getMessage()));
                            }

                            RefactoringStatusEntry highest = status.getEntryWithHighestSeverity();
                            String errorMsg = "Preconditions failed: " + (highest != null ? highest.getMessage() : "Unknown status error");

                            LOGGER.severe("Preconditions failed for extraction in method: " + executionStep.methodOwner);
                            action.failed = true;
                            metrics.recordExtractionFailure("PRECONDITION_FAILED");
                            logFailure(logWriter, action.resultsFileName, workspacePath, targetLabel, action.solutionString, "PRECONDITION_FAILED", errorMsg + traceBuffer);
                            continue;
                        }

                        int docLenBefore = liveDocument.getLength();
                        Change change = refactoring.createChange(new NullProgressMonitor());

                        Change undoChange = change.perform(new NullProgressMonitor());
                        textFileBuffer.commit(new NullProgressMonitor(), true);

                        icu.makeConsistent(new NullProgressMonitor());
                        icu.reconcile(AST.JLS18, ICompilationUnit.FORCE_PROBLEM_DETECTION, null, new NullProgressMonitor());

                        CompilationUnit verifiedCU = neo.reducecognitivecomplexity.core.jdt.Utils.createCompilationUnitFromFileInWorkspace(workspacePath);

                        int docLenAfter = liveDocument.getLength();
                        traceBuffer.append(String.format("\n  * Refactoring Delta       : Doc len before=%d, after=%d (diff: %+d)", docLenBefore, docLenAfter, (docLenAfter - docLenBefore)));

                        if (neo.reducecognitivecomplexity.core.jdt.Utils.builtWithCompilationErrors(verifiedCU)) {
                            String errorMsg = "Compilation errors introduced into file after applying extraction; extraction has been rolled back, previous successful extractions in this file are kept.";
                            LOGGER.severe("Compilation error after refactoring, rolling back this extraction only: " + action.resultsFileName);

                            undoChange.perform(new NullProgressMonitor());
                            textFileBuffer.commit(new NullProgressMonitor(), true);
                            icu.makeConsistent(new NullProgressMonitor());
                            icu.reconcile(AST.JLS18, ICompilationUnit.FORCE_PROBLEM_DETECTION, null, new NullProgressMonitor());

                            action.failed = true;
                            metrics.recordExtractionFailure("COMPILATION_ERROR");
                            logFailure(logWriter, action.resultsFileName, workspacePath, targetLabel, action.solutionString, "COMPILATION_ERROR", errorMsg + traceBuffer);
                        } else {
                            LOGGER.info("SUCCESS -> Applied and persisted extraction for " + action.resultsFileName);
                            action.successfulExtractions++;
                            metrics.recordExtractionSuccess();
                            logSuccess(logWriter, action.resultsFileName, workspacePath, targetLabel, action.solutionString, "Successfully extracted and saved block" + traceBuffer);

                            // If other groups from the SAME solution share the
                            // same ending point (in their original offset list) as this group, that shared point
                            // has just been CONSUMED by this extraction (it now lies inside the replaced text).
                            // For those siblings, which by construction have a SMALLER start offset (i.e., they are
                            // "enclosing containers" of this group), the correct new end boundary for their selection
                            // is no longer that original point, but the invocation statement JDT just inserted here.
                            //
                            // IMPORTANT: Do not assume that this invocation starts EXACTLY at "liveStart" or any
                            // other derived offset—JDT may shift the actual edit boundary to absorb leading line
                            // indentation or whitespace, shifting it relative to the original selection's start offset.
                            // Instead, the ACTUAL invocation is located in the committed document by searching for the
                            // statement whose TEXT literally contains the call to the generated method name (which is
                            // unique by construction). This is a deterministic verification based on actual source content
                            // rather than offset arithmetic.
                            int thisOriginalLastOffset = executionStep.rawOffsets.get(executionStep.rawOffsets.size() - 1);
                            boolean hasSharedEndSibling = allExtractions.stream().anyMatch(sib ->
                                    sib != executionStep && sib.parentSolution == executionStep.parentSolution
                                    && sib.rawOffsets.get(sib.rawOffsets.size() - 1) == thisOriginalLastOffset);

                            if (hasSharedEndSibling) {
                                MethodDeclaration verifiedMethod = findMethodDeclaration(verifiedCU, action.methodName, action.lineNumber);
                                String verifiedDocContent = liveDocument.get();
                                ASTNode insertedCallNode = findStatementInvoking(verifiedMethod, verifiedDocContent, generatedMethodName);
                                int redirectAnchor = (insertedCallNode != null) ? insertedCallNode.getStartPosition() : liveStart;

                                if (insertedCallNode == null) {
                                    LOGGER.warning("Could not locate newly inserted call by text ('" + generatedMethodName
                                            + "(') to redirect siblings of " + executionStep.methodOwner + " extraction #" + executionStep.extractionIndex
                                            + " (method found=" + (verifiedMethod != null) + "); using liveStart as approximation, may fail downstream.");
                                }

                                for (TrackedExtraction sibling : allExtractions) {
                                    if (sibling == executionStep) continue;
                                    if (sibling.parentSolution != executionStep.parentSolution) continue;
                                    int siblingOriginalLastOffset = sibling.rawOffsets.get(sibling.rawOffsets.size() - 1);
                                    if (siblingOriginalLastOffset == thisOriginalLastOffset) {
                                        Position redirected = new Position(redirectAnchor, 0);
                                        liveDocument.addPosition(positionCategory, redirected);
                                        sibling.endMarker = redirected;
                                    }
                                }
                            }
                        }

                    } catch (Exception e) {
                        String errorMsg = "Exception thrown during extraction: " + e.getMessage();
                        LOGGER.log(Level.SEVERE, errorMsg, e);
                        action.failed = true;
                        metrics.recordExtractionFailure("EXECUTION_EXCEPTION");
                        logFailure(logWriter, action.resultsFileName, workspacePath, targetLabel, action.solutionString, "EXECUTION_EXCEPTION", errorMsg + traceBuffer);
                    } finally {
                        action.executionTimeMs += System.currentTimeMillis() - stepStartTime;
                    }
                }

            } finally {
                try {
                    liveDocument.removePositionUpdater(positionUpdater);
                } catch (RuntimeException ignored) {}
                try {
                    liveDocument.removePositionCategory(positionCategory);
                } catch (BadPositionCategoryException ignored) {}
            }

            LOGGER.info("Successfully completed processing file context: " + workspacePath);

        } finally {
            bufferManager.disconnect(path, LocationKind.IFILE, new NullProgressMonitor());
        }

        return currentExtractionIndex;
    }

    /**
     * Verifies whether a results file contains an optimal solution status.
     * <p>
     * Reads the data row and checks if the third column from the end contains
     * the keyword {@code "Optimal"}.
     *
     * @param file the results file to inspect
     * @return {@code true} if the status is optimal, {@code false} otherwise
     */
    private boolean isOptimalResult(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine(); // Skip header row
            String dataLine = br.readLine();
            if (dataLine != null && !dataLine.trim().isEmpty()) {
                String[] columns = dataLine.split(";", -1);
                return columns.length >= 3 && columns[columns.length - 3].trim().contains("Optimal");
            }
        } catch (IOException e) {
            LOGGER.warning("Could not check optimal status for file " + file.getName() + ": " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Finds a method declaration by name, using line number proximity to disambiguate overloads.
     * <p>
     * If a valid line number is provided, selects the closest matching method; otherwise,
     * defaults to the first declaration encountered in the AST.
     *
     * @param cu         the compilation unit containing the AST
     * @param methodName the name of the target method
     * @param lineNumber the target line number for disambiguation, or {@code <= 0} if unknown
     * @return the matching method declaration, or {@code null} if not found
     */
    private static MethodDeclaration findMethodDeclaration(CompilationUnit cu, String methodName, int lineNumber) {
        final MethodDeclaration[] result = new MethodDeclaration[1];
        final int[] candidateCount = new int[]{0};
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getName().getIdentifier().equals(methodName)) {
                    candidateCount[0]++;
                    if (result[0] == null) {
                        result[0] = node;
                    } else if (lineNumber > 0) {
                        int currentLine = cu.getLineNumber(node.getStartPosition());
                        if (Math.abs(currentLine - lineNumber) < Math.abs(cu.getLineNumber(result[0].getStartPosition()) - lineNumber)) {
                            result[0] = node;
                        }
                    }
                }
                return super.visit(node);
            }
        });
        if (candidateCount[0] > 1 && lineNumber <= 0) {
            LOGGER.warning("Found " + candidateCount[0] + " methods named '" + methodName
                    + "' and no reliable line number for disambiguation; using the first one found in the AST. "
                    + "This may select the wrong overload/method.");
        }
        return result[0];
    }

    /**
     * Finds the AST {@link Statement} node within a method that starts at an exact target offset.
     * <p>
     * Performs strict offset matching with no proximity heuristics. Includes statements inside 
     * nested constructs (e.g., lambdas, anonymous classes) while excluding structural body blocks.
     *
     * @param method      the enclosing method declaration to search within
     * @param targetStart the exact starting character offset
     * @return the matching statement node, or {@code null} if no exact match exists
     */
    private static ASTNode findExactStatementNode(MethodDeclaration method, int targetStart) {
        if (method == null) return null;

        final ASTNode[] match = new ASTNode[1];

        method.accept(new ASTVisitor() {
            @Override
            public void preVisit(ASTNode node) {
                if (match[0] != null) return;
                if (!(node instanceof Statement)) return;
                if (node instanceof Block && !(node.getParent() instanceof Block)) return;
                if (node.getStartPosition() != targetStart) return;
                match[0] = node;
            }
        });

        return match[0];
    }

    /**
     * Finds the innermost AST statement within a method that contains an invocation of a target method.
     * <p>
     * Matches against source text to reliably identify the call statement regardless of JDT offset shifts,
     * selecting the smallest matching statement node.
     *
     * @param method           the method AST node to search within
     * @param documentContent  the full source document content
     * @param calledMethodName the exact name of the invoked method
     * @return the smallest statement node invoking the method, or {@code null} if not found
     */
    private static ASTNode findStatementInvoking(MethodDeclaration method, String documentContent, String calledMethodName) {
        if (method == null || documentContent == null) return null;

        final String needle = calledMethodName + "(";
        final ASTNode[] best = new ASTNode[1];

        method.accept(new ASTVisitor() {
            @Override
            public void preVisit(ASTNode node) {
                if (!(node instanceof Statement)) return;
                if (node instanceof Block && !(node.getParent() instanceof Block)) return;
                int start = node.getStartPosition();
                int len = node.getLength();
                if (start < 0 || len < 0 || start + len > documentContent.length()) return;
                String text = documentContent.substring(start, start + len);
                if (!text.contains(needle)) return;

	            // Select the smallest (most specific) statement containing the call rather than the first found.
	            // Top-down traversal visits ancestor nodes first (e.g., an enclosing 'if' statement), whose source 
	            // text also encompasses the target invocation. The innermost statement is always the one with the shortest length.
                if (best[0] == null || len < best[0].getLength()) {
                    best[0] = node;
                }
            }
        });

        return best[0];
    }

    /**
     * Describes the closest AST statements to an unmatched target offset within a method for diagnostic purposes.
     * <p>
     * Helps determine whether offset mismatches are systematic (e.g., a constant delta) or localized.
     *
     * @param cu           the compilation unit containing line number mappings
     * @param method       the target method declaration to inspect
     * @param targetOffset the target character offset
     * @return a formatted string listing nearby statement offsets, line numbers, and deltas
     */
    private static String describeNearbyStatements(CompilationUnit cu, MethodDeclaration method, int targetOffset) {
        if (method == null) return "method not found";

        List<Integer> starts = new ArrayList<>();
        method.accept(new ASTVisitor() {
            @Override
            public void preVisit(ASTNode node) {
                if ((node instanceof Statement)
                        && !(node instanceof Block && !(node.getParent() instanceof Block))) {
                    starts.add(node.getStartPosition());
                }
            }
        });

        if (starts.isEmpty()) return "no sentences in this method";

        starts.sort((x, y) -> Integer.compare(Math.abs(x - targetOffset), Math.abs(y - targetOffset)));

        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int s : starts) {
            if (shown >= NEARBY_STATEMENTS_TO_LOG) break;
            int line = cu.getLineNumber(s);
            if (shown > 0) sb.append(", ");
            sb.append(String.format("offset=%d (line %d, delta=%+d)", s, line, s - targetOffset));
            shown++;
        }
        return sb.toString();
    }

    /**
     * Logs a formatted entry summarizing a parsed refactoring solution to a writer.
     *
     * @param writer        the output writer to receive the log entry
     * @param pr            the parsed result containing solution details
     * @param workspacePath the file path of the target Java source file
     */
    private void logParsedSolution(PrintWriter writer, ParsedResult pr, String workspacePath) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String logLine = String.format(
            "[%s] [PARSED_RESULTS] [FILE: %s] [JAVA_FILE: %s] [METHOD: %s] [LINE: %d] [COMPLEXITY: %d] -> SOLUTION: %s", 
            timestamp, pr.resultsFileName, workspacePath, pr.methodName, pr.lineNumber, pr.initialComplexity, pr.solutionString
        );
        writer.println(logLine);
        writer.flush();
    }

    /**
     * Generates a diagnostic text snippet surrounding a specified character offset,
     * marking the target position with "»«" and escaping newlines/tabs.
     *
     * @param documentContent the source document content
     * @param offset          the target character index
     * @param radius          the number of surrounding characters to include on each side
     * @return a formatted text snippet around the offset, or an error indicator if invalid
     */
    private static String snippetAround(String documentContent, int offset, int radius) {
        if (documentContent == null) return "(documento nulo)";
        int len = documentContent.length();
        if (offset < 0 || offset > len) {
            return "(offset " + offset + " fuera de límites, longitud documento=" + len + ")";
        }
        int from = Math.max(0, offset - radius);
        int to = Math.min(len, offset + radius);
        String before = documentContent.substring(from, offset).replace("\n", "\\n").replace("\t", "\\t");
        String after = documentContent.substring(offset, to).replace("\n", "\\n").replace("\t", "\\t");
        return "..." + before + "»«" + after + "...";
    }

    /**
     * Formats a code snippet for logging by prepending an indentation prefix to each line.
     * Returns a placeholder if the code is null or empty.
     *
     * @param code   the code snippet to format
     * @param indent the indentation string to prepend to each line
     * @return the formatted, indented code string
     */
    private String formatCodeForLog(String code, String indent) {
        if (code == null || code.isEmpty()) {
            return indent + "<EMPTY_OR_NULL_SELECTION>\n";
        }
        String[] lines = code.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(indent).append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * Writes a CSV file summarizing the execution metrics of all parsed refactoring solutions.
     * <p>
     * Each row records code location metadata, complexity reductions, calculated technical
     * debt (effort metrics), execution status (whether all extractions succeeded), and
     * total execution time in milliseconds.
     *
     * @param resultsFolder      the target directory where the CSV file will be saved
     * @param allParsedSolutions the list of parsed refactoring results to export
     */
    private void writeExecutionResultsCsv(File resultsFolder, List<ParsedResult> allParsedSolutions) {
        File csvFile = new File(resultsFolder, "refactoring_execution_results.csv");

        try (PrintWriter csvWriter = new PrintWriter(new BufferedWriter(new FileWriter(csvFile, false)))) {
            csvWriter.println("project" + Constants.CSV_SEPARATOR + "folder" + Constants.CSV_SEPARATOR
                    + "package" + Constants.CSV_SEPARATOR + "class" + Constants.CSV_SEPARATOR
                    + "method" + Constants.CSV_SEPARATOR + "lineNumber" + Constants.CSV_SEPARATOR
                    + "initialComplexity" + Constants.CSV_SEPARATOR + "solution" + Constants.CSV_SEPARATOR
                    + "applied" + Constants.CSV_SEPARATOR
                    + "extractions" + Constants.CSV_SEPARATOR + "reductionComplexity" + Constants.CSV_SEPARATOR
                    + "finalComplexity" + Constants.CSV_SEPARATOR + "initialEffort" + Constants.CSV_SEPARATOR
                    + "finalEffort" + Constants.CSV_SEPARATOR + "reductionEffort" + Constants.CSV_SEPARATOR
                    + "executionTime");

            for (ParsedResult pr : allParsedSolutions) {
                if (pr.project == null) {
                    continue;
                }

                boolean applied = !pr.failed && pr.totalExtractions > 0
                        && pr.successfulExtractions == pr.totalExtractions;

                int initialEffort = Utils.computeEffort(pr.initialComplexity, Constants.COGNITIVE_COMPLEXITY_THRESHOLD,
                        Constants.INITIAL_EFFORT_IN_MINUTES_FOR_CC_ISSUE,
                        Constants.LINEAR_EFFORT_INCREMENT_IN_MINUTES_FOR_CC_ISSUE);
                int finalEffort = Utils.computeEffort(pr.finalComplexity, Constants.COGNITIVE_COMPLEXITY_THRESHOLD,
                        Constants.INITIAL_EFFORT_IN_MINUTES_FOR_CC_ISSUE,
                        Constants.LINEAR_EFFORT_INCREMENT_IN_MINUTES_FOR_CC_ISSUE);
                int reductionEffort = initialEffort - finalEffort;

                csvWriter.println(
                        csvField(pr.project) + Constants.CSV_SEPARATOR
                        + csvField(pr.folder) + Constants.CSV_SEPARATOR
                        + csvField(pr.packageName) + Constants.CSV_SEPARATOR
                        + csvField(pr.className) + Constants.CSV_SEPARATOR
                        + csvField(pr.methodName) + Constants.CSV_SEPARATOR
                        + pr.lineNumber + Constants.CSV_SEPARATOR
                        + pr.initialComplexity + Constants.CSV_SEPARATOR
                        + csvField(pr.solutionString) + Constants.CSV_SEPARATOR
                        + applied + Constants.CSV_SEPARATOR
                        + pr.totalExtractions + Constants.CSV_SEPARATOR
                        + pr.reductionComplexity + Constants.CSV_SEPARATOR
                        + pr.finalComplexity + Constants.CSV_SEPARATOR
                        + initialEffort + Constants.CSV_SEPARATOR
                        + finalEffort + Constants.CSV_SEPARATOR
                        + reductionEffort + Constants.CSV_SEPARATOR
                        + pr.executionTimeMs);
            }

            LOGGER.info("Execution results CSV written to: " + csvFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not write execution results CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Escapes and wraps a string in double quotes for CSV formatting if it contains
     * the CSV separator or double quotes. Converts {@code null} to an empty string.
     *
     * @param value the field value to format
     * @return the CSV-safe string representation
     */
    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(Constants.CSV_SEPARATOR) || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void writeLogHeader(PrintWriter writer) {
        writer.println("====================================================================================================");
        writer.println("REFACTORING EXECUTION LOG - " + LocalDateTime.now().format(DATE_FORMATTER));
        writer.println("====================================================================================================");
        writer.flush();
    }

    private void writeSummaryHeader(PrintWriter writer, ExecutionMetrics metrics) {
        double solSuccessPct = metrics.totalSolutions > 0 ? (metrics.successfulSolutions * 100.0) / metrics.totalSolutions : 0.0;
        double solFailurePct = metrics.totalSolutions > 0 ? (metrics.failedSolutions * 100.0) / metrics.totalSolutions : 0.0;

        double extSuccessPct = metrics.totalExtractions > 0 ? (metrics.successfulExtractions * 100.0) / metrics.totalExtractions : 0.0;
        double extFailurePct = metrics.totalExtractions > 0 ? (metrics.failedExtractions * 100.0) / metrics.totalExtractions : 0.0;

        writer.println();
        writer.println("====================================================================================================");
        writer.println("EXECUTION SUMMARY STATISTICS - " + LocalDateTime.now().format(DATE_FORMATTER));
        writer.println("====================================================================================================");
        writer.println("--- SUMMARY BY SOLUTION (.results) ---");
        writer.println(String.format("Total Solutions/Refactorings      : %d", metrics.totalSolutions));
        writer.println(String.format("Soliutions Successed (100%% OK)   : %d (%.2f%%)", metrics.successfulSolutions, solSuccessPct));
        writer.println(String.format("Soliutions Failed                 : %d (%.2f%%)", metrics.failedSolutions, solFailurePct));
        writer.println();
        writer.println("--- SUMMARY BY EXTRACTIONS ---");
        writer.println(String.format("Total Extractions Tried  : %d", metrics.totalExtractions));
        writer.println(String.format("Extractions Successed    : %d (%.2f%%)", metrics.successfulExtractions, extSuccessPct));
        writer.println(String.format("Extractions Failed       : %d (%.2f%%)", metrics.failedExtractions, extFailurePct));
        writer.println("----------------------------------------------------------------------------------------------------");
        writer.println("Failures in Extractions by Type:");

        if (metrics.extractionFailureBreakdown.isEmpty()) {
            writer.println("  (None)");
        } else {
            metrics.extractionFailureBreakdown.forEach((type, count) -> {
                double typePct = metrics.failedExtractions > 0 ? (count * 100.0) / metrics.failedExtractions : 0.0;
                writer.println(String.format("  - %-25s : %d (%.2f%% failures)", type, count, typePct));
            });
        }

        writer.println("====================================================================================================");
        writer.flush();
    }

    private void logExecutionSummary(ExecutionMetrics metrics, String logPath) {
        double solSuccessPct = metrics.totalSolutions > 0 ? (metrics.successfulSolutions * 100.0) / metrics.totalSolutions : 0.0;
        double solFailurePct = metrics.totalSolutions > 0 ? (metrics.failedSolutions * 100.0) / metrics.totalSolutions : 0.0;

        double extSuccessPct = metrics.totalExtractions > 0 ? (metrics.successfulExtractions * 100.0) / metrics.totalExtractions : 0.0;
        double extFailurePct = metrics.totalExtractions > 0 ? (metrics.failedExtractions * 100.0) / metrics.totalExtractions : 0.0;

        StringBuilder summary = new StringBuilder();
        summary.append("\n==================================================================================\n");
        summary.append(" SUMMARY\n");
        summary.append("==================================================================================\n");
        summary.append(" [SOLUTIONS / REFACTORINGS (.results)]\n");
        summary.append(String.format("  Total solutions      : %d\n", metrics.totalSolutions));
        summary.append(String.format("  Successed (100%% OK)  : %d (%.2f%%)\n", metrics.successfulSolutions, solSuccessPct));
        summary.append(String.format("  Failed               : %d (%.2f%%)\n", metrics.failedSolutions, solFailurePct));
        summary.append("----------------------------------------------------------------------------------\n");
        summary.append(" [ALL EXTRACTIONS]\n");
        summary.append(String.format("  Total                : %d\n", metrics.totalExtractions));
        summary.append(String.format("  Successed            : %d (%.2f%%)\n", metrics.successfulExtractions, extSuccessPct));
        summary.append(String.format("  Failed               : %d (%.2f%%)\n", metrics.failedExtractions, extFailurePct));

        if (!metrics.extractionFailureBreakdown.isEmpty()) {
            summary.append("  Failures by type:\n");
            metrics.extractionFailureBreakdown.forEach((type, count) ->
                summary.append(String.format("    - %-22s : %d\n", type, count))
            );
        }
        summary.append("----------------------------------------------------------------------------------\n");
        summary.append(" Detailed report saved in: ").append(logPath).append("\n");
        summary.append("==================================================================================");

        LOGGER.info(summary.toString());
    }

    private void logSuccess(PrintWriter writer, String resultsFile, String fileContext, String targetElement, String solutionStr, String message) {
        logEntry(writer, resultsFile, fileContext, targetElement, solutionStr, "SUCCESS", message);
    }

    private void logFailure(PrintWriter writer, String resultsFile, String fileContext, String targetElement, String solutionStr, String errorType, String message) {
        logEntry(writer, resultsFile, fileContext, targetElement, solutionStr, errorType, message);
    }

    private void logEntry(PrintWriter writer, String resultsFile, String fileContext, String targetElement, String solutionStr, String statusType, String message) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String logLine = String.format("[%s] [RESULTS_FILE: %s] [JAVA_FILE: %s] [TARGET: %s] [SOLUTION: %s] [TYPE: %s] -> %s", 
                timestamp, resultsFile, fileContext, targetElement, solutionStr, statusType, message);
        writer.println(logLine);
        writer.flush();
    }
}