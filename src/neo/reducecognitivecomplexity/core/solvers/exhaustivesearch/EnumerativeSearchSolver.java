package neo.reducecognitivecomplexity.core.solvers.exhaustivesearch;

import java.io.BufferedWriter;
import java.io.IOException;

import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.Solution;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCache;
import neo.reducecognitivecomplexity.core.solvers.RefactoringSolver;
import neo.reducecognitivecomplexity.core.solvers.SolverContext;
import neo.reducecognitivecomplexity.core.solvers.config.EnumerativeSearchConfig;

/**
 * Enumerative search solver for finding solutions ({@link Solution}) to reduce
 * method cognitive complexity.
 * <p>
 * This solver exhaustively explores possible refactoring sequences using a
 * configured strategy (Longest vs Shortest sequence first).
 * </p>
 */
public class EnumerativeSearchSolver implements RefactoringSolver {

    private final EnumerativeSearchConfig config;
    private Solution bestSolution;

    public EnumerativeSearchSolver(EnumerativeSearchConfig config) {
        this.config = config;
    }

    @Override
    public Solution solve(SolverContext ctx, RefactoringCache cache, BufferedWriter writer) throws Exception {
        bestSolution = null;
        boolean optimal = false;

        long startTime = System.currentTimeMillis();

        // Initialize algorithm with AST and configured approach
        ExhaustiveEnumerationAlgorithm eea = new ExhaustiveEnumerationAlgorithm(
                cache, 
                ctx.ast, 
                this.config.getApproach()
        );

        try {
            eea.run(solution -> {
                // Wrap and evaluate the candidate solution
                Solution sol = new Solution(solution, ctx.compilationUnit, ctx.ast);
                sol.evaluate(cache);

                // Update best solution (minimize fitness)
                if (bestSolution == null || sol.getFitness() < bestSolution.getFitness()) {
                    bestSolution = sol;
                }
            }, this.config.getEvaluations());

        } catch (RuntimeException e) {
            // Catch early termination signal
            optimal = true;
            // Optional: Log optimal found
        }

        long runtime = System.currentTimeMillis() - startTime;

        // Write standardized CSV Results
        writeResultsToCsv(writer, ctx, bestSolution, optimal, runtime);

        return bestSolution;
    }

    /**
     * Helper to write the full set of results to the CSV buffer.
     */
    private void writeResultsToCsv(BufferedWriter bf, SolverContext ctx, Solution bestSolution, boolean optimal,
            long runtime) throws IOException {

        // Basic context info
        bf.append(ctx.algorithm).append(Constants.CSV_SEPARATOR);
        bf.append(ctx.project).append(Constants.CSV_SEPARATOR);
        bf.append(ctx.folder).append(Constants.CSV_SEPARATOR);
        bf.append(ctx.packageInProject).append(Constants.CSV_SEPARATOR);
        bf.append(ctx.classInPackage).append(Constants.CSV_SEPARATOR);
        bf.append(ctx.record.methodName).append(Constants.CSV_SEPARATOR);
        bf.append(String.valueOf(ctx.record.lineNumber)).append(Constants.CSV_SEPARATOR);
        bf.append(String.valueOf(ctx.record.complexity)).append(Constants.CSV_SEPARATOR);

        if (bestSolution != null) {
            // Solution metrics
            bf.append(bestSolution.toStringForFileFormat()).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getSize())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getReducedComplexity())).append(Constants.CSV_SEPARATOR);
            
            // Calculate achieved reduction
            int complexityDiff = ctx.record.complexity - bestSolution.getReducedComplexity();
            bf.append(String.valueOf(complexityDiff)).append(Constants.CSV_SEPARATOR);

            // Detailed refactoring statistics
            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getMinNumberOfExtractedLinesOfCode())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getMaxNumberOfExtractedLinesOfCode())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getMeanNumberOfExtractedLinesOfCode())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getTotalNumberOfExtractedLinesOfCode())).append(Constants.CSV_SEPARATOR);

            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getMinNumberOfParametersInExtractedMethods())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getMaxNumberOfParametersInExtractedMethods())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getMeanNumberOfParametersInExtractedMethods())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getTotalNumberOfParametersInExtractedMethods())).append(Constants.CSV_SEPARATOR);

            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getMinReductionOfCognitiveComplexity())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getMaxReductionOfCognitiveComplexity())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getMeanReductionOfCognitiveComplexity())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getExtractionMetricsStats().getTotalReductionOfCognitiveComplexity())).append(Constants.CSV_SEPARATOR);

        } else {
            // Placeholder for no solution
            bf.append("NO SOLUTION!;");
            // Append empty columns to align with the CSV header structure (15 empty slots for metrics above)
            for (int i = 0; i < 15; i++) {
                bf.append(Constants.CSV_SEPARATOR);
            }
        }

        // Final status columns
        bf.append(optimal ? "1" : "0").append(Constants.CSV_SEPARATOR);
        bf.append(String.valueOf(runtime)).append("\n");

        bf.flush();
    }
}