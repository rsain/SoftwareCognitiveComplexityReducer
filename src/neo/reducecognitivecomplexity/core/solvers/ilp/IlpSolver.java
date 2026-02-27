package neo.reducecognitivecomplexity.core.solvers.ilp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.logging.Logger;

import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.Solution;
import neo.reducecognitivecomplexity.core.graphs.ExtractionVertex;
import neo.reducecognitivecomplexity.core.graphs.GraphBundle;
import neo.reducecognitivecomplexity.core.graphs.GraphService;
import neo.reducecognitivecomplexity.core.jdt.Utils;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCache;
import neo.reducecognitivecomplexity.core.solvers.RefactoringSolver;
import neo.reducecognitivecomplexity.core.solvers.SolverContext;
import neo.reducecognitivecomplexity.core.solvers.config.IlpConfig;

import org.jgrapht.graph.DefaultWeightedEdge;

import ilog.cplex.CpxException;
import ilog.cplex.IloCplex;

/**
 * Implementation of {@link RefactoringSolver} using Integer Linear Programming (ILP).
 * <p>
 * This solver models the refactoring problem as an optimization problem on a graph
 * (conflict graph, extractins graph) and uses the CPLEX engine to find the
 * mathematically optimal set of refactorings.
 * </p>
 */
public class IlpSolver implements RefactoringSolver {

    private static final Logger LOGGER = Logger.getLogger(IlpSolver.class.getName());
    private final IlpConfig config;

    public IlpSolver(IlpConfig config) {
        this.config = config;
    }

    @Override
    public String getExtraCsvHeaders() {
        return "modelStatus;numberSolutions;";
    }

    @Override
    public Solution solve(SolverContext ctx, RefactoringCache cache, BufferedWriter writer) throws Exception {
        long startTime = System.currentTimeMillis();
        Solution bestSolution = null;
        boolean isOptimal = false;
        int numberOfSolutions = 0;
        int numberOfOptimalSolutions = 0;
        String modelStatus = "UNKNOWN";

        GraphBundle graphs = ctx.getGraphs();
        boolean localBuild = false;

        // If the pipeline didn't provide graphs, build them locally (and mark for cleanup)
        if (graphs == null) {
            graphs = GraphService.buildGraphs(cache, ctx.ast);
            localBuild = true;
        }

        Model<ExtractionVertex, DefaultWeightedEdge> m = null;

        try {
            m = new Model<>(
                    graphs.conflicts,
                    graphs.noConflicts,
                    graphs.full
            );

            // MUTE CPLEX to avoid console spam!
            m.cplex.setOut(null);
            m.cplex.setWarning(null);

            // Configure CPLEX parameters for numerical stability and timeouts
            m.cplex.setParam(IloCplex.DoubleParam.EpInt, 1E-9); // Integrality tolerance
            m.cplex.setParam(IloCplex.DoubleParam.EpGap, 1E-9); // Relative MIP gap tolerance
            m.cplex.setParam(IloCplex.DoubleParam.EpOpt, 1E-9); // Optimality tolerance
            
            // Limit time
            m.cplex.setParam(IloCplex.DoubleParam.TimeLimit, config.getTimeLimit());
            
            // Limit working memory
            m.cplex.setParam(IloCplex.Param.WorkMem, config.getWorkingMemory());
            
            // Stop populating after finding 10 optimal solutions
            m.cplex.setParam(IloCplex.IntParam.PopulateLim, 10);
            
            // Export the mathematical model for debugging purposes
            String lpFileName = Constants.OUTPUT_FOLDER 
                    + ctx.generatePrefixForSolverFileNames() + ".lp";
            m.cplex.exportModel(lpFileName);
            
            // Execute the solver
            m.cplex.populate();

            // Retrieve solution metadata
            numberOfSolutions = m.cplex.getSolnPoolNsolns();
            modelStatus = m.cplex.getStatus().toString();
            isOptimal = m.cplex.getStatus().equals(ilog.cplex.IloCplex.Status.Optimal);

            numberOfOptimalSolutions = m.showSolutionPopOptima();

            if (numberOfOptimalSolutions > 0) {
                LOGGER.info("NUMBER OF OPTIMAL SOLUTIONS: " + numberOfOptimalSolutions);

                // Retrieve the best solution (index 0) from the pool
                bestSolution = m.getSolution(
                        0, 
                        Utils.getICompilationUnit(ctx.ast), 
                        ctx.compilationUnit, 
                        ctx.ast
                );

                // Calculate metrics against the cache
                bestSolution.evaluate(cache);

                // Log verbose details
                LOGGER.info(bestSolution.toStringVerbose());
            } else {
                LOGGER.warning("NO SOLUTION FOUND for " + ctx.generatePrefixForSolverFileNames());
            }

        } catch (CpxException ex) {
            LOGGER.warning("CPLEX Exception for " + ctx.generatePrefixForSolverFileNames() + ": " + ex.getMessage());
            modelStatus = neo.reducecognitivecomplexity.core.io.CsvResultWriter.escapeCSV("CPLEX Error: " + ex.getMessage());
        } catch (Exception ex) {
            LOGGER.warning("General Exception for " + ctx.generatePrefixForSolverFileNames() + ": " + ex.getMessage());
            modelStatus = neo.reducecognitivecomplexity.core.io.CsvResultWriter.escapeCSV("Exception: " + ex.getMessage());
        } finally {
            if (m != null && m.cplex != null) {
                m.cplex.end(); // Wipes the native C++ memory cleanly.
            }

            // If we built the graphs locally, we own them and must clear them.
            if (localBuild && graphs != null) {
                graphs.clear();
            }
        }

        long runtime = System.currentTimeMillis() - startTime;

        // Write results to CSV
        writeResultsToCsv(writer, ctx, bestSolution, isOptimal, modelStatus, numberOfOptimalSolutions, runtime);

        return bestSolution;
    }

    private void writeResultsToCsv(BufferedWriter bf, SolverContext ctx, Solution bestSolution,
                                   boolean isOptimal, String modelStatus, long numberOfOptimalSolutions, 
                                   long runtime) throws IOException {

        // Contextual Information
        bf.append(ctx.algorithm).append(Constants.CSV_SEPARATOR);
        bf.append(ctx.project).append(Constants.CSV_SEPARATOR);
        bf.append(ctx.folder).append(Constants.CSV_SEPARATOR);
        bf.append(ctx.packageInProject).append(Constants.CSV_SEPARATOR);
        bf.append(ctx.classInPackage).append(Constants.CSV_SEPARATOR);
        bf.append(ctx.record.methodName).append(Constants.CSV_SEPARATOR);
        bf.append(String.valueOf(ctx.record.lineNumber)).append(Constants.CSV_SEPARATOR);
        bf.append(String.valueOf(ctx.record.complexity)).append(Constants.CSV_SEPARATOR);

        // Solution Metrics
        if (bestSolution != null) {
            bf.append(bestSolution.toStringForFileFormat()).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getSize())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(bestSolution.getReducedComplexity())).append(Constants.CSV_SEPARATOR);
            
            int complexityReduction = ctx.record.complexity - bestSolution.getReducedComplexity();
            bf.append(String.valueOf(complexityReduction)).append(Constants.CSV_SEPARATOR);

            // Detailed Statistics
            var stats = bestSolution.getExtractionMetricsStats();
            bf.append(String.valueOf(stats.getMinNumberOfExtractedLinesOfCode())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(stats.getMaxNumberOfExtractedLinesOfCode())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(stats.getMeanNumberOfExtractedLinesOfCode())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(stats.getTotalNumberOfExtractedLinesOfCode())).append(Constants.CSV_SEPARATOR);
            
            bf.append(String.valueOf(stats.getMinNumberOfParametersInExtractedMethods())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(stats.getMaxNumberOfParametersInExtractedMethods())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(stats.getMeanNumberOfParametersInExtractedMethods())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(stats.getTotalNumberOfParametersInExtractedMethods())).append(Constants.CSV_SEPARATOR);
            
            bf.append(String.valueOf(stats.getMinReductionOfCognitiveComplexity())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(stats.getMaxReductionOfCognitiveComplexity())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(stats.getMeanReductionOfCognitiveComplexity())).append(Constants.CSV_SEPARATOR);
            bf.append(String.valueOf(stats.getTotalReductionOfCognitiveComplexity())).append(Constants.CSV_SEPARATOR);
        } else {
            // Placeholder for no solution
            bf.append("NO SOLUTION!;");
            // Fill empty columns to match header structure
            for (int i = 0; i < 15; i++) {
                bf.append(Constants.CSV_SEPARATOR);
            }
        }

        // Solver Specific Metadata
        bf.append(String.valueOf(isOptimal)).append(Constants.CSV_SEPARATOR);
        bf.append(modelStatus).append(Constants.CSV_SEPARATOR);
        bf.append(String.valueOf(numberOfOptimalSolutions)).append(Constants.CSV_SEPARATOR);
        bf.append(String.valueOf(runtime)).append("\n");

        bf.flush();
    }
}