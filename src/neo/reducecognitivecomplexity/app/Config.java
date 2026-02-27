package neo.reducecognitivecomplexity.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the runtime configuration settings for the application.
 * <p>
 * This class encapsulates the input parameters provided by the user, such as
 * the target project name, the selected refactoring solver strategy, output
 * preferences, and batch CSV processing targets. It acts as an immutable data 
 * carrier and provides a factory method to parse and validate raw command-line arguments.
 * </p>
 */
public class Config {
	private final String projectName;
	private final String solver;
	private final boolean generateGraphs;
	private final boolean valid;
	private final String csvFilePath;

	/**
	 * Constructs a new Config instance.
	 *
	 * @param projectName    the name of the Eclipse project to analyze (null if in CSV mode)
	 * @param solver         the identifier of the solver, or {@code null} if no solver is selected
	 * @param generateGraphs {@code true} to export .dot graph files for visualization
	 * @param valid          {@code true} if the configuration is valid and ready for use
	 * @param csvFilePath    the path to the CSV file containing target methods to analyze (null if full project mode)
	 */
	public Config(String projectName, String solver, boolean generateGraphs, boolean valid, String csvFilePath) {
		this.projectName = projectName;
		this.solver = solver;
		this.generateGraphs = generateGraphs;
		this.valid = valid;
		this.csvFilePath = csvFilePath;
	}

	/**
	 * Returns the name of the project targeted for full analysis.
	 *
	 * @return the project name string, or {@code null} if running in Batch CSV mode
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
	 *
	 * @return {@code true} if graph generation is enabled
	 */
	public boolean isGenerateGraphs() {
		return generateGraphs;
	}

	/**
	 * Checks if this configuration is valid.
	 *
	 * @return {@code true} if the arguments were parsed successfully, {@code false} otherwise
	 */
	public boolean isValid() {
		return valid;
	}

	/**
	 * Returns the file path for the batch CSV containing target methods.
	 *
	 * @return the absolute or relative path to the CSV file, or {@code null} if not provided
	 */
	public String getCsvFilePath() {
		return csvFilePath;
	}

	/**
	 * Parses the command-line arguments to create a {@link Config} object.
	 * <p>
	 * Supported execution modes:
	 * <ul>
	 * <li><b>Full Project Mode:</b> {@code [projectName] [solver?] [generateGraphs?]} <br>
	 * Example: {@code "MyProject" "ILP" "true"}</li>
	 * <li><b>Batch CSV Mode:</b> {@code -batch [path] [solver?] [generateGraphs?]} <br>
	 * Example: {@code -batch "target_methods.csv" "ILP" "false"}</li>
	 * </ul>
	 * </p>
	 *
	 * @param args the array of command-line arguments
	 * @return a {@link Config} instance representing the parsed settings
	 */
	public static Config parse(String[] args) {
		String parsedCsvPath = null;
		List<String> positionalArgs = new ArrayList<>();

		// Extract flags and separate positional arguments
		for (int i = 0; i < args.length; i++) {
			if ("-batch".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
				parsedCsvPath = args[i + 1];
				i++; // Skip the path value
			} else {
				positionalArgs.add(args[i]);
			}
		}

		// Handle Batch CSV Mode
		if (parsedCsvPath != null) {
			String solv = null;
			boolean genGraphs = false;

			if (positionalArgs.size() >= 1) {
				solv = parseSolverArg(positionalArgs.get(0));
			}
			if (positionalArgs.size() >= 2) {
				genGraphs = Boolean.parseBoolean(positionalArgs.get(1));
			}
			// In CSV mode, project name is null because the CSV defines the projects
			return new Config(null, solv, genGraphs, true, parsedCsvPath);
		}

		// Handle Full Project Scan Mode
		if (positionalArgs.size() == 1) {
			return new Config(positionalArgs.get(0), null, false, true, null);
		} else if (positionalArgs.size() == 2) {
			return new Config(positionalArgs.get(0), parseSolverArg(positionalArgs.get(1)), false, true, null);
		} else if (positionalArgs.size() >= 3) {
			return new Config(positionalArgs.get(0), parseSolverArg(positionalArgs.get(1)),
					Boolean.parseBoolean(positionalArgs.get(2)), true, null);
		}

		// Invalid configuration (empty arguments)
		return new Config(null, null, false, false, null);
	}

	/**
	 * Helper method to safely parse the solver argument.
	 * Treats "none" or "null" (case-insensitive) as a null solver.
	 */
	private static String parseSolverArg(String solverArg) {
		if ("none".equalsIgnoreCase(solverArg) || "null".equalsIgnoreCase(solverArg)) {
			return null;
		}
		return solverArg;
	}
}