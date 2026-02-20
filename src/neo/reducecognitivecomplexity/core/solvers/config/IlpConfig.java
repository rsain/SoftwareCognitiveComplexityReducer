package neo.reducecognitivecomplexity.core.solvers.config;

/**
 * Configuration specific to the Integer Linear Programming (ILP) solver.
 * <p>
 * Controls parameters passed to the underlying optimization engine (e.g., CPLEX),
 * such as execution time limits.
 * </p>
 */
public class IlpConfig implements SolverConfig {

    /**
     * The maximum time limit (in seconds) allowed for the solver to run.
     */
    private final int timeLimit;

    /**
     * Default constructor.
     * Sets the time limit to 300 seconds.
     */
    public IlpConfig() {
        this(300);
    }

    /**
     * Constructor with custom time limit.
     *
     * @param timeLimit The maximum execution time in seconds.
     */
    public IlpConfig(int timeLimit) {
        this.timeLimit = timeLimit;
    }

    public int getTimeLimit() {
        return timeLimit;
    }
}