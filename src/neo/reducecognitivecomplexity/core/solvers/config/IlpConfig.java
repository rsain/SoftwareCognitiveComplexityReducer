package neo.reducecognitivecomplexity.core.solvers.config;

import neo.reducecognitivecomplexity.app.Constants;

/**
 * Configuration specific to the Integer Linear Programming (ILP) solver.
 * <p>
 * Controls parameters passed to the underlying optimization engine (e.g., CPLEX),
 * such as execution time and working memory limits.
 * </p>
 */
public class IlpConfig implements SolverConfig {

    /**
     * The maximum time limit (in seconds) allowed for the solver to run.
     */
    private final int timeLimit;
    
    /**
     * The maximum working RAM memory (in MB) allowed for the solver to use.
     */
    private final int workingMemory;

    /**
     * Default constructor.
     * Sets the time limit to 300 seconds.
     */
    public IlpConfig() {
        this(Constants.TIME_LIMIT, Constants.WORKING_MEMORY);
    }

    /**
     * Constructor with custom time limit.
     *
     * @param timeLimit The maximum execution time in seconds.
     */
    public IlpConfig(int timeLimit, int workingMemory) {
        this.timeLimit = timeLimit;
        this.workingMemory = workingMemory;
    }

    public int getTimeLimit() {
        return timeLimit;
    }
    
    public int getWorkingMemory() {
        return workingMemory;
    }
}