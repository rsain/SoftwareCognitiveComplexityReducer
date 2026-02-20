package neo.reducecognitivecomplexity.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import neo.reducecognitivecomplexity.app.Config;
import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.graphs.GraphBundle;
import neo.reducecognitivecomplexity.core.graphs.GraphService;
import neo.reducecognitivecomplexity.core.io.CsvResultWriter;
import neo.reducecognitivecomplexity.core.jdt.CompilationUnitPathExtractor;
import neo.reducecognitivecomplexity.core.jdt.JavaMethodProcessor.MethodComplexityRecord;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCache;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCacheFiller;
import neo.reducecognitivecomplexity.core.solvers.RefactoringSolver;
import neo.reducecognitivecomplexity.core.solvers.SolverContext;
import neo.reducecognitivecomplexity.core.solvers.SolverFactory;
import neo.reducecognitivecomplexity.core.solvers.SolverType;

/**
 * Orchestrates the complete analysis and refactoring pipeline for a single Java method.
 * <p>
 * This class acts as the coordinator for the following workflow:
 * <ol>
 * <li><b>Analysis:</b> Scans the method to identify all valid refactoring opportunities (Extract Method) 
 * using an exhaustive enumeration strategy.</li>
 * <li><b>Context Preparation:</b> Initializes a {@link SolverContext} to manage the lifecycle of 
 * resources (ASTs, graphs) required for solving.</li>
 * <li><b>Graph Generation (Optional):</b> If configured, builds Conflict and otrher graphs 
 * representing the relationships between refactoring opportunities.</li>
 * <li><b>Solving:</b> Delegates to a configured {@link RefactoringSolver} (e.g., ILP, Genetic Algorithm) 
 * to select the optimal set of refactorings.</li>
 * <li><b>Reporting:</b> Writes the solution, metrics, and applied changes to output files (CSV/TXT).</li>
 * </ol>
 * </p>
 */
public class MethodRefactoringPipeline {

    private static final Logger LOGGER = Logger.getLogger(MethodRefactoringPipeline.class.getName());

    private final Config config;
    private final CsvResultWriter resultWriter;

    /**
     * Constructs a new pipeline instance.
     *
     * @param config       The application configuration containing solver settings and paths.
     * @param resultWriter The writer handle for appending analysis metrics to the central CSV report.
     */
    public MethodRefactoringPipeline(Config config, CsvResultWriter resultWriter) {
        this.config = config;
        this.resultWriter = resultWriter;
    }

    /**
     * Executes the refactoring pipeline for the specified method.
     * <p>
     * This is the main entry point. It handles the full lifecycle from cache generation to 
     * solver execution. It creates a {@code try-with-resources} block for the {@link SolverContext} 
     * to ensure heavy resources (like JGraphT graphs) are cleaned up immediately after processing.
     * </p>
     *
     * @param unit   The JDT {@link CompilationUnit} containing the method to refactor.
     * @param record The {@link MethodComplexityRecord} identifying the specific method (name, line number) 
     * and its initial complexity.
     */
    public void process(CompilationUnit unit, MethodComplexityRecord record) {
        try {
            // 1. Generate Cache: Identify all potential refactorings
            RefactoringCache cache = generateCache(unit, record);

            // 2. Create Context: Initialize environment and manage resource lifecycle
            try (SolverContext ctx = new SolverContext(unit, record, config)) {

                // 3. Optional: Build & Export Graphs (Visualization/Debug step)
                if (config.isGenerateGraphs()) {
                    LOGGER.info("Building and exporting graphs...");
                    GraphBundle graphs = GraphService.buildGraphs(cache, record.methodDeclaration);
                    GraphService.exportGraphs(graphs, ctx);
                    
                    // Store graphs in context so the solver can reuse them without rebuilding
                    ctx.setPrecomputedGraphs(graphs);
                }

                // 4. Run Solver: Execute the selected optimization strategy
                if (config.getSolver() != null && !config.getSolver().isEmpty()) {
                    runSolver(ctx, cache);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing method " + record.methodName, e);
        }
    }

    /**
     * Generates a cache of all possible "Extract Method" refactoring opportunities.
     * <p>
     * uses the {@link RefactoringCacheFiller#exhaustiveEnumerationAlgorithm} to find every 
     * valid contiguous block of statements that can be extracted.
     * </p>
     *
     * @param unit   The compilation unit.
     * @param record The method metadata.
     * @return A populated {@link RefactoringCache}.
     * @throws IOException If writing the cache to disk fails.
     */
    private RefactoringCache generateCache(CompilationUnit unit, MethodComplexityRecord record) throws IOException {
        LOGGER.info("Processing method: " + record.methodName + " (CC: " + record.complexity + ")");

        MethodDeclaration methodNode = record.methodDeclaration;
        RefactoringCache refactoringCache = new RefactoringCache(unit, methodNode);

        long startTime = System.currentTimeMillis();
        RefactoringCacheFiller.exhaustiveEnumerationAlgorithm(refactoringCache, methodNode);
        long runtime = System.currentTimeMillis() - startTime;

        // Determine filename for the cache output
        CompilationUnitPathExtractor.PathComponents components = CompilationUnitPathExtractor.computeAllComponents(unit);
        String cacheFileName = generatePrefixFileName(components, record) + Constants.FILE_EXTENSION_FOR_REFACTORING_CACHE;

        // Persist cache and write initial analysis results
        refactoringCache.writeToCSV(Constants.OUTPUT_FOLDER, cacheFileName);
        resultWriter.writeResult(components, record, runtime);

        return refactoringCache;
    }

    /**
     * Instantiates the configured solver and solves the optimization problem.
     *
     * @param ctx              The solver context containing ASTs and graphs.
     * @param refactoringCache The cache of candidate refactorings.
     */
    private void runSolver(SolverContext ctx, RefactoringCache refactoringCache) {
        RefactoringSolver solver;
        try {
            solver = SolverFactory.getSolver(SolverType.fromKey(config.getSolver()));
        } catch (IllegalArgumentException e) {
            LOGGER.severe("Solver selection failed: " + e.getMessage());
            return;
        }

        // Initialize results writer for this specific solver run
        try (BufferedWriter writer = initializeSolverWriter(ctx, solver)) {
            LOGGER.info("Running solver strategy: " + config.getSolver());

            Solution solution = solver.solve(ctx, refactoringCache, writer);

            if (solution != null) {
                processSolution(ctx, solution);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Solver execution failed", e);
        }
    }

    /**
     * Prepares the BufferedWriter for solver results.
     * <p>
     * Checks if the output file exists; if not, writes the CSV header row.
     * </p>
     *
     * @param ctx    The solver context.
     * @param solver The specific solver instance (used to get extra CSV headers).
     * @return A ready-to-use {@link BufferedWriter}.
     * @throws IOException If file creation fails.
     */
    private BufferedWriter initializeSolverWriter(SolverContext ctx, RefactoringSolver solver) throws IOException {
        String outputFilePath = Constants.OUTPUT_FOLDER + ctx.algorithm + "-" + ctx.generatePrefixForSolverFileNames()
                + Constants.FILE_EXTENSION_FOR_RESULTS;
        
        File file = new File(outputFilePath);
        boolean isNewFile = !file.exists();
        BufferedWriter bf = new BufferedWriter(new FileWriter(file, true));

        if (isNewFile) {
            StringBuilder header = new StringBuilder();
            header.append("algorithm;project;folder;package;class;method;lineNumber;initialComplexity;solution;extractions;")
                  .append("reductionComplexity;finalComplexity;")
                  .append("minExtractedLOC;maxExtractedLOC;meanExtractedLOC;totalExtractedLOC;")
                  .append("minParamsExtractedMethods;maxParamsExtractedMethods;meanParamsExtractedMethods;totalParamsExtractedMethods;")
                  .append("minReductionOfCC;maxReductionOfCC;meanReductionOfCC;totalReductionOfCC;optimo;");
            
            // Append solver-specific headers (e.g., "generations" for GA, "gap" for ILP)
            header.append(solver.getExtraCsvHeaders());
            header.append("executionTime");
            
            bf.append(header.toString());
            bf.newLine();
        }
        return bf;
    }

    /**
     * Writes the final solution details to a specific output file.
     *
     * @param ctx      The solver context.
     * @param solution The solution object containing the selected refactorings.
     * @throws IOException If writing to disk fails.
     */
    private void processSolution(SolverContext ctx, Solution solution) throws IOException {
        String fileName = Constants.OUTPUT_FOLDER + ctx.generatePrefixForSolverFileNames()
                + Constants.FILE_EXTENSION_FOR_SOLUTION;
        
        solution.writeInFile(fileName);
        LOGGER.info("Refactoring operations:\n" + solution.getSequenceList());
    }

    /**
     * Helper to generate unique file name prefixes based on the method location.
     * <p>
     * Format: {@code Project@Folder-Package-File-Method-Line}
     * </p>
     * <p>
     * Note: This logic is also mirrored inside {@link SolverContext#generatePrefixForSolverFileNames()},
     * but is kept here as a static helper to allow generating cache filenames before a Context exists.
     * </p>
     */
    private static String generatePrefixFileName(CompilationUnitPathExtractor.PathComponents c, MethodComplexityRecord r) {
        return c.getProjectName() + "@" + c.getSourceFolder().replace('/', '.') + "-"
                + c.getPackageDirectory().replace('/', '.') + "-" + c.getFileName() + "-" + r.methodName + "-"
                + r.lineNumber;
    }
}