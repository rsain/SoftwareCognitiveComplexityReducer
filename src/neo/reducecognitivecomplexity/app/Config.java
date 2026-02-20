package neo.reducecognitivecomplexity.app;

/**
 * Represents the runtime configuration settings for the application.
 * <p>
 * This class encapsulates the input parameters provided by the user, such as
 * the target project name, the selected refactoring solver strategy, and output
 * preferences. It acts as an immutable data carrier and provides a factory
 * method to parse and validate raw command-line arguments.
 * </p>
 */
public class Config {
	private final String projectName;
	private final String solver;
	private final boolean generateGraphs;
	private final boolean valid;

	/**
	 * Constructs a new Config instance.
	 *
	 * @param projectName    the name of the Eclipse project to analyze/refactor
	 * @param solver         the identifier of the solver, or {@code null} if no
	 *                       solver is selected
	 * @param generateGraphs {@code true} to export .dot graph files for
	 *                       visualization
	 * @param valid          {@code true} if the configuration is valid and ready
	 *                       for use
	 */
	public Config(String projectName, String solver, boolean generateGraphs, boolean valid) {
		this.projectName = projectName;
		this.solver = solver;
		this.generateGraphs = generateGraphs;
		this.valid = valid;
	}

	/**
	 * Returns the name of the project targeted for analysis.
	 *
	 * @return the project name string, or {@code null} if the config is invalid
	 */
	public String getProjectName() {
		return projectName;
	}

	/**
	 * Returns the name of the solver selected for execution.
	 * <p>
	 * If this returns {@code null}, the application will perform the analysis and
	 * cache generation (and optionally graph export) but will not attempt to solve
	 * the refactoring problem.
	 * </p>
	 *
	 * @return the solver identifier string, or {@code null} if none was specified
	 */
	public String getSolver() {
		return solver;
	}

	/**
	 * Indicates whether extraction graphs should be exported to disk.
	 * <p>
	 * If {@code true}, the application will generate .dot files (full, conflict,
	 * and no-conflict graphs) in the output directory. This is useful for debugging
	 * the refactoring cache or visualizing complex method structures.
	 * </p>
	 *
	 * @return {@code true} if graph generation is enabled
	 */
	public boolean isGenerateGraphs() {
		return generateGraphs;
	}

	/**
	 * Checks if this configuration is valid.
	 * <p>
	 * This flag indicates whether the arguments were parsed successfully. If
	 * {@code false}, the application should print usage instructions and exit.
	 * </p>
	 *
	 * @return {@code true} if the arguments were parsed successfully, {@code false}
	 *         otherwise
	 */
	public boolean isValid() {
		return valid;
	}

	/**
	 * Parses the command-line arguments to create a {@link Config} object.
	 * <p>
	 * Supported argument formats:
	 * <ul>
	 * <li><b>1 argument:</b> {@code [projectName]} <br>
	 * (Solver defaults to {@code null}, Graphs default to {@code false})</li>
	 * <li><b>2 arguments:</b> {@code [projectName] [solver]} <br>
	 * (Graphs default to {@code false})</li>
	 * <li><b>3 arguments:</b> {@code [projectName] [solver] [generateGraphs]} <br>
	 * (e.g., {@code "MyProject" "none" "true"} to skip solving but generate
	 * graphs)</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <b>Note:</b> If the solver argument is explicitly set to "none" or "null"
	 * (case-insensitive), it is interpreted as {@code null}, effectively skipping
	 * the solving phase.
	 * </p>
	 *
	 * @param args the array of command-line arguments
	 * @return a {@link Config} instance representing the parsed settings
	 */
	public static Config parse(String[] args) {
		if (args.length == 1) {
			// Case: Project only -> No solver, No graphs
			return new Config(args[0], null, false, true);
		} else if (args.length == 2) {
			// Case: Project + Solver -> No graphs
			return new Config(args[0], args[1], false, true);
		} else if (args.length == 3) {
			// Case: Full configuration
			String projectName = args[0];
			String solverArg = args[1];
			boolean graphs = Boolean.parseBoolean(args[2]);

			// Handle explicit "none" or "null" string as a null solver
			if ("none".equalsIgnoreCase(solverArg) || "null".equalsIgnoreCase(solverArg)) {
				solverArg = null;
			}

			return new Config(projectName, solverArg, graphs, true);
		}

		// Invalid number of arguments
		return new Config(null, null, false, false);
	}
}