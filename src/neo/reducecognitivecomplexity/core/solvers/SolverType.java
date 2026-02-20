package neo.reducecognitivecomplexity.core.solvers;

import java.util.Arrays;

/**
 * Enumeration of available solvers for reducing cognitive complexity.
 * <p>
 * Defines the keys used in configuration files to select the desired solver.
 * </p>
 */
public enum SolverType {
    
    /** Integer Linear Programming solver (exact optimization). */
    ILP("ILP"),
    
    /** Exhaustive Search: Prioritizes longest sequences first (greedy heuristic). */
    ES_LONG_SEQUENCE_FIRST("ES-LSF"),
    
    /** Exhaustive Search: Prioritizes shortest sequences first. */
    ES_SHORT_SEQUENCE_FIRST("ES-SSF");

    private final String key;

    SolverType(String key) {
        this.key = key;
    }

    /**
     * Gets the string key associated with this solver type.
     * @return The key used in configuration.
     */
    public String getKey() {
        return key;
    }

    /**
     * Helper to find the corresponding Enum from a string key (case-insensitive).
     * <p>
     * Useful for parsing configuration files or CLI arguments.
     * </p>
     * * @param text The key to look up (e.g., "ILP", "ES-LSF").
     * @return The matching SolverType.
     * @throws IllegalArgumentException if the key is unknown.
     */
    public static SolverType fromKey(String text) {
        return Arrays.stream(values())
                .filter(type -> type.key.equalsIgnoreCase(text))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown solver type: " + text));
    }
}