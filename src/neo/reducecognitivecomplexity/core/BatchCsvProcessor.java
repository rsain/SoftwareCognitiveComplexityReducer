package neo.reducecognitivecomplexity.core;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import neo.reducecognitivecomplexity.app.Config;
import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.io.CsvResultWriter;
import neo.reducecognitivecomplexity.core.jdt.JavaMethodProcessor.MethodComplexityRecord;
import neo.reducecognitivecomplexity.core.eclipse.Utils;
import neo.reducecognitivecomplexity.core.jdt.CognitiveComplexityVisitor;

/**
 * Processes a batch of target methods defined in an external CSV file.
 * <p>
 * This class orchestrates the automated analysis and refactoring of specific methods
 * across multiple projects within the Eclipse workspace. For each target method specified
 * in the input CSV, it performs the following steps:
 * <ol>
 * <li>Resolves the corresponding {@link IProject} and {@link IJavaProject}.</li>
 * <li>Locates the specific class and parses it into an Abstract Syntax Tree (AST).</li>
 * <li>Finds all {@link MethodDeclaration} instances matching the target name (supporting overloaded methods).</li>
 * <li>Calculates the initial cognitive complexity for each matching method.</li>
 * <li>Filters methods, executing refactoring only for those that exceed the {@link Constants.COGNITIVE_COMPLEXITY_THRESHOLD}.</li>
 * <li>Passes candidate methods to the {@link MethodRefactoringPipeline} for refactoring optimization.</li>
 * </ol>
 * </p>
 * <p>
 * It handles errors gracefully on a per-method basis, ensuring that a failure in one
 * target does not halt the entire batch process.
 * </p>
 */
public class BatchCsvProcessor {

    private static final Logger LOGGER = Logger.getLogger(BatchCsvProcessor.class.getName());

    private final Config config;
    private final CsvResultWriter resultWriter;

    /**
     * Initializes the processor with the necessary dependencies.
     *
     * @param config       The application configuration settings (e.g., selected solver, graph generation flags).
     * @param resultWriter The centralized writer used to output analysis metrics and refactoring results.
     */
    public BatchCsvProcessor(Config config, CsvResultWriter resultWriter) {
        this.config = config;
        this.resultWriter = resultWriter;
    }

    /**
     * Executes the batch processing workflow using the provided CSV file.
     * <p>
     * The CSV file is expected to have a header row, followed by rows containing:
     * {@code project;sourceFolder;package;class;method}
     * </p>
     * * @param csvFilePath The absolute or relative path to the input CSV file containing target methods.
     */
    public void processCsv(String csvFilePath) {
    	List<TargetMethod> targets = readCsv(csvFilePath);
        LOGGER.info("Found " + targets.size() + " target method definitions in CSV.");

        // Initialize the pipeline once
        MethodRefactoringPipeline pipeline = new MethodRefactoringPipeline(config, resultWriter);

        for (TargetMethod target : targets) {
            try {
                LOGGER.info("=======================================================");
                LOGGER.info("Processing target: " + target.project + " -> " + target.className + "." + target.methodName);
                
                // --- RESOLVE PROJECT ---
                IProject project = Utils.getProject(target.project);
                if (project == null) continue;

                IJavaProject javaProject = Utils.getJavaProject(project);
                if (javaProject == null) continue;

                // --- FORMAT CSV DATA FOR JDT ---
                String formattedPackage = target.packageName.replace("/", ".").replace("\\", ".");
                String formattedClass = target.className;
                if (formattedClass.endsWith(".java")) {
                    formattedClass = formattedClass.substring(0, formattedClass.length() - 5);
                }

                String fullyQualifiedName = formattedPackage.isEmpty() ? 
                        formattedClass : formattedPackage + "." + formattedClass;

                // --- FIND COMPILATION UNIT ---
                IType type = javaProject.findType(fullyQualifiedName);
                if (type == null) {
                    LOGGER.warning("Could not find class " + fullyQualifiedName);
                    continue;
                }

                ICompilationUnit icu = type.getCompilationUnit();
                if (icu == null || !icu.exists()) continue;

                // --- PARSE AST AND FIND ALL MATCHING METHODS ---
                CompilationUnit astRoot = neo.reducecognitivecomplexity.core.jdt.Utils.parse(icu);
                
                List<MethodDeclaration> matchingMethods = neo.reducecognitivecomplexity.core.jdt.Utils.findMethodsByName(astRoot, target.methodName);

                if (matchingMethods.isEmpty()) {
                    LOGGER.warning("Could not find any method named " + target.methodName + " in class " + formattedClass);
                    continue;
                }

                // If this row specifies a line number (6-column CSV format),
                // narrow down to the SPECIFIC overload declared at that line,
                // instead of processing every method sharing this name.
                //
                // Rows from an older, 5-column CSV (no line number) fall back
                // to the original behavior: process every overload found.
                List<MethodDeclaration> methodsToProcess;
                if (target.lineNumber >= 0) {
                    methodsToProcess = new ArrayList<>();
                    for (MethodDeclaration m : matchingMethods) {
                        if (astRoot.getLineNumber(m.getStartPosition()) == target.lineNumber) {
                            methodsToProcess.add(m);
                        }
                    }

                    if (methodsToProcess.isEmpty()) {
                        LOGGER.warning("Could not find method '" + target.methodName + "' at line " + target.lineNumber
                                + " in class " + formattedClass + " (found " + matchingMethods.size()
                                + " method(s) with that name, but none at the specified line - the source may have "
                                + "changed since the input CSV was generated).");
                        continue;
                    }
                } else {
                    methodsToProcess = matchingMethods;
                }

                LOGGER.info("Found " + methodsToProcess.size() + " method(s) named '" + target.methodName + "'"
                        + (target.lineNumber >= 0 ? " at line " + target.lineNumber : "") + ". Processing each...");

                // --- EXECUTE PIPELINE FOR EACH MATCHING METHOD ---
                for (MethodDeclaration methodNode : methodsToProcess) {
                    int lineNumber = astRoot.getLineNumber(methodNode.getStartPosition());

                    // Calculate the real initial cognitive complexity
                    CognitiveComplexityVisitor.Result ccResult = CognitiveComplexityVisitor.methodComplexity(methodNode);
                    int initialComplexity = ccResult.complexity;

                    // Filter out methods that do not exceed the configured threshold
                    if (initialComplexity <= Constants.COGNITIVE_COMPLEXITY_THRESHOLD) {
                        LOGGER.info("Skipping method '" + target.methodName + "' at line " + lineNumber + 
                                    " | CC is " + initialComplexity + " (Threshold is " + Constants.COGNITIVE_COMPLEXITY_THRESHOLD + ")");
                        continue;
                    }
                    
                    MethodComplexityRecord record = new MethodComplexityRecord(
                            target.methodName, 
                            lineNumber, 
                            initialComplexity, 
                            methodNode
                    );

                    LOGGER.info("Processing method '" + target.methodName + "' at line " + lineNumber + 
                                " | Initial CC: " + initialComplexity);
                    
                    // Execute the solver pipeline
                    pipeline.process(astRoot, record);
                    
                    LOGGER.info("Successfully completed method at line: " + lineNumber);
                }

            } catch (Exception e) {
                LOGGER.severe("Error processing target " + target.className + "." + target.methodName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Parses the CSV file into a list of {@link TargetMethod} objects.
     * <p>
     * Skips the first line (header) and splits the remaining lines using the 
     * delimiter defined in {@link Constants#CSV_SEPARATOR}. Supports both the
     * original 5-column format ({@code project;sourceFolder;package;class;method})
     * and the newer 6-column format that additionally specifies the line
     * number of the intended overload
     * ({@code project;sourceFolder;package;class;method;lineNumber}).
     * </p>
     * <p>
     * <b>Deduplicates rows:</b> if the exact same target (identical
     * {@code project;sourceFolder;package;class;method;lineNumber}
     * combination - with {@code lineNumber} defaulting to {@code -1} when
     * absent) appears more than once, only the FIRST occurrence is kept;
     * subsequent duplicates are skipped and logged as a warning. The line
     * number is included in the key precisely so that two DIFFERENT
     * overloaded methods sharing the same name (each with its own distinct
     * line number) are correctly treated as separate targets, not
     * deduplicated away.
     * </p>
     *
     * @param path The path to the CSV file.
     * @return A list of valid, de-duplicated target methods extracted from the file.
     */
    private List<TargetMethod> readCsv(String path) {
        List<TargetMethod> targets = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine(); // Skip header
            int csvLineNumber = 1;
            while ((line = br.readLine()) != null) {
                csvLineNumber++;
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(Constants.CSV_SEPARATOR); // e.g. ";"

                if (cols.length >= 5) {
                    String project = cols[0].trim();
                    String sourceFolder = cols[1].trim();
                    String packageName = cols[2].trim();
                    String className = cols[3].trim();
                    String methodName = cols[4].trim();

                    // Optional 6th column: the specific line number of the
                    // intended overload. Defaults to -1 (unknown) for older,
                    // 5-column input files.
                    int targetLine = -1;
                    if (cols.length >= 6 && !cols[5].trim().isEmpty()) {
                        try {
                            targetLine = Integer.parseInt(cols[5].trim());
                        } catch (NumberFormatException e) {
                            LOGGER.warning("Invalid line number '" + cols[5].trim() + "' at CSV line "
                                    + csvLineNumber + "; treating it as unspecified.");
                        }
                    }

                    // Deduplication key includes the line number: two rows
                    // are duplicates only if ALL SIX fields match exactly.
                    // This does NOT collapse genuinely different overloaded
                    // methods that share the same name but have different
                    // line numbers - only exact repeats of the same row.
                    String key = project + "|" + sourceFolder + "|" + packageName + "|" + className + "|"
                            + methodName + "|" + targetLine;

                    if (!seen.add(key)) {
                        LOGGER.warning("Duplicate target skipped at CSV line " + csvLineNumber + ": " + key
                                + " (an identical row was already read earlier in this file).");
                        continue;
                    }

                    targets.add(new TargetMethod(project, sourceFolder, packageName, className, methodName, targetLine));
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to read CSV file: " + path);
        }
        return targets;
    }
}