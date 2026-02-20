package neo.reducecognitivecomplexity.core.solvers.config;

/**
 * Marker interface for solver configuration objects.
 * <p>
 * Implementing classes (e.g., {@link IlpConfig},
 * {@link EnumerativeSearchConfig}) carry algorithm-specific parameters such as
 * timeouts, search limits, or heuristic strategies.
 * </p>
 */
public interface SolverConfig {
	// Currently empty, serves as a common type for all solver configurations.
}