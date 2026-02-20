package neo.reducecognitivecomplexity.core.solvers;

import java.io.BufferedWriter;

import neo.reducecognitivecomplexity.core.Solution;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCache;

/**
 * Common interface for all refactoring solvers.
 * <p>
 * Solvers are responsible for finding the optimal (or near-optimal) set of
 * refactorings to reduce cognitive complexity within the constraints provided
 * by the context.
 * </p>
 */
public interface RefactoringSolver {

	/**
	 * Returns a semicolon-separated string of additional CSV headers specific to
	 * this solver.
	 * <p>
	 * This allows specific solvers to log custom metrics (e.g.,
	 * "iterations;memory_usage") alongside the standard output.
	 * </p>
	 * 
	 * @return A string of headers, or an empty string if no extra data is logged.
	 */
	default String getExtraCsvHeaders() {
		return "";
	}

	/**
	 * Executes the specific solver to find a refactoring solution.
	 *
	 * @param ctx    The context containing method info, constraints, and
	 *               configuration.
	 * @param cache  The cache of feasible refactorings (containing metrics and
	 *               validity checks).
	 * @param writer A {@link BufferedWriter} for logging detailed execution steps
	 *               or debug info.
	 * @return The best {@link Solution} found, or {@code null} if no valid solution
	 *         exists.
	 * @throws Exception If the solving process encounters a critical error.
	 */
	Solution solve(SolverContext ctx, RefactoringCache cache, BufferedWriter writer) throws Exception;
}