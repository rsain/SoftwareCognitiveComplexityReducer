package neo.reducecognitivecomplexity.core.solvers.config;

import neo.reducecognitivecomplexity.core.solvers.exhaustivesearch.ExhaustiveEnumerationAlgorithm.Approach;

/**
 * Configuration specific to the Enumerative Search (Exhaustive) solver.
 * <p>
 * Controls the search strategy (e.g., Longest First vs Shortest First) and limits
 * the search space by defining a maximum number of evaluations.
 * </p>
 */
public class EnumerativeSearchConfig implements SolverConfig {

    private final Approach approach;
    
    /**
     * The maximum number of evaluations to run before stopping the search.
     */
    private final int evaluations;

    /**
     * Default constructor.
     * Sets evaluations to 10,000 and approach to {@code LONG_SEQUENCE_FIRST}.
     */
    public EnumerativeSearchConfig() {
        this(10000, Approach.LONG_SEQUENCE_FIRST);
    }

    /**
     * Constructor with custom settings.
     *
     * @param evaluations The maximum number of candidates to evaluate.
     * @param approach    The search strategy (heuristic) to use.
     */
    public EnumerativeSearchConfig(int evaluations, Approach approach) {
        this.evaluations = evaluations;
        this.approach = approach;
    }

    public int getEvaluations() {
        return evaluations;
    }

    public Approach getApproach() {
        return approach;
    }
}