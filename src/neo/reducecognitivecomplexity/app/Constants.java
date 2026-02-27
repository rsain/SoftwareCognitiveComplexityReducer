package neo.reducecognitivecomplexity.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Defines the global constants, configuration settings, and property keys used
 * throughout the application.
 * <p>
 * This class serves as a central repository for:
 * <ul>
 * <li>Environment configurations loaded dynamically from {@code config.properties}.</li>
 * <li>Metric thresholds and limits (e.g., complexity thresholds).</li>
 * <li>File extensions, output paths, and serialization formats.</li>
 * <li>Keys used to store and retrieve attributes from AST or Graph nodes.</li>
 * <li>User-facing messages and error strings.</li>
 * </ul>
 * </p>
 */
public class Constants {
	private static final Logger LOGGER = Logger.getLogger(Constants.class.getName());

	// =========================================================================
	// METRICS & THRESHOLDS (Dynamically Loaded)
	// =========================================================================

	/**
	 * The threshold value for Cognitive Complexity.
	 * <p>
	 * Methods with a complexity score higher than this value are considered candidates
	 * for refactoring. This value is loaded from the {@code complexity.threshold} 
	 * property in {@code config.properties}. If omitted or unreadable, it defaults 
	 * to 15 (matching SonarQube's standard).
	 * </p>
	 */
	public static final int COGNITIVE_COMPLEXITY_THRESHOLD;
	
	/**
	 * The maximum working RAM memory for solvers.
	 * <p>
	 * This value is loaded from the {@code working.memory} 
	 * property in {@code config.properties}. If omitted or unreadable, it defaults 
	 * to 2048 megabytes.
	 * </p>
	 */
	public static final int WORKING_MEMORY;
	
	/**
	 * The maximum running time in seconds for solvers.
	 * <p>
	 * This value is loaded from the {@code time.limit} 
	 * property in {@code config.properties}. If omitted or unreadable, it defaults 
	 * to 300 seconds.
	 * </p>
	 */
	public static final int TIME_LIMIT;

	/**
	 * The estimated initial setup time (in minutes) required to address a Cognitive
	 * Complexity issue manually.
	 */
	public static final int INITIAL_EFFORT_IN_MINUTES_FOR_CC_ISSUE = 5;

	/**
	 * The estimated additional time (in minutes) required per unit of complexity to
	 * fix the issue manually.
	 */
	public static final int LINEAR_EFFORT_INCREMENT_IN_MINUTES_FOR_CC_ISSUE = 1;

	// =========================================================================
	// AST & GRAPH NODE PROPERTIES
	// =========================================================================

	/**
	 * Property key for the total complexity score added by a specific node.
	 * <p>
	 * Value = {@code Inherent Complexity} + {@code Nesting Penalty}.
	 * </p>
	 */
	public static final String CONTRIBUTION_TO_COGNITIVE_COMPLEXITY = "contributionToCognitiveComplexity";

	/**
	 * Property key for the sum of {@link #CONTRIBUTION_TO_COGNITIVE_COMPLEXITY} for
	 * a node AND all its descendants.
	 */
	public static final String ACCUMULATED_CONTRIBUTION_TO_COGNITIVE_COMPLEXITY = "accumulatedContributionToCognitiveComplexity";

	/**
	 * Property key for the inherent cognitive complexity of a node (e.g., an 'if'
	 * typically costs 1).
	 */
	public static final String INHERENT_COGNITIVE_COMPLEXITY = "inherentCognitiveComplexity";

	/**
	 * Property key for the penalty paid by a node due to its nesting level (depth).
	 */
	public static final String NESTING_COGNITIVE_COMPLEXITY = "nestingCognitiveComplexity";

	/**
	 * Property key for the nesting depth of a node relative to the method start.
	 */
	public static final String NESTING_LEVEL = "nestingLevel";

	/**
	 * Property key for the sum of {@link #INHERENT_COGNITIVE_COMPLEXITY} for this
	 * node AND all its descendants.
	 */
	public static final String ACCUMULATED_INHERENT_COGNITIVE_COMPLEXITY = "accumulatedInherentCognitiveComplexity";

	/**
	 * Property key for the sum of nesting penalties paid by this node AND all its
	 * descendants.
	 */
	public static final String ACCUMULATED_NESTING_COGNITIVE_COMPLEXITY = "accumulatedNestingCognitiveComplexity";

	/**
	 * Property key for the count of structural nodes (if, for, catch) inside a
	 * block that paid a nesting penalty.
	 */
	public static final String NUMBER_OF_NESTING_CONTRIBUTORS = "numberOfNestingContributors";

	/**
	 * Property key for the cognitive complexity of nested code. Useful for
	 * container nodes like TryStatements.
	 */
	public static final String COGNITIVE_COMPLEXITY_OF_NESTED_CODE = "cognitiveComplexityOfNestedCode";

	// =========================================================================
	// FILES, PATHS & OUTPUT
	// =========================================================================

	/**
	 * The absolute path to the directory where experimental data and results are
	 * output.
	 * <p>
	 * This value is loaded dynamically from the {@code output.folder} property in 
	 * {@code config.properties}. If the configuration is missing, it safely defaults 
	 * to an {@code output/} directory inside the current working directory.
	 * </p>
	 */
	public static final String OUTPUT_FOLDER;

	/**
	 * Extension for files storing the final refactoring solution (refactored code).
	 */
	public static final String FILE_EXTENSION_FOR_SOLUTION = ".solution";

	/** Extension for general result files (CSV format). */
	public static final String FILE_EXTENSION_FOR_RESULTS = ".results";

	/** Extension for the refactoring cache (CSV format). */
	public static final String FILE_EXTENSION_FOR_REFACTORING_CACHE = ".csv";

	/** Extension for the full extraction graph export (DOT format). */
	public static final String FILE_EXTENSION_FOR_FULL_GRAPH = ".dot";

	/** Extension for the conflict graph export (DOT format). */
	public static final String FILE_EXTENSION_FOR_CONFLICT_GRAPH = ".conflicts.dot";

	/** Extension for the graph without conflicts export (DOT format). */
	public static final String FILE_EXTENSION_FOR_GRAPH_WITHOUT_CONFLICT = ".no-conflicts.dot";

	/**
	 * The separator character used for CSV file generation.
	 */
	public static final String CSV_SEPARATOR = ";";

	// =========================================================================
	// MESSAGES
	// =========================================================================

	/**
	 * Error message displayed when the command-line arguments are invalid.
	 */
	public static final String MESSAGE_WHEN_WRONG_ARGS = "Invalid Arguments. Usage: [ProjectName] [Solver (optional)] [GenerateGraphs (optional: true/false)]";
	
	// =========================================================================
	// STATIC INITIALIZATION
	// =========================================================================
	
	static {
		Properties props = new Properties();

		// Define a safe fallback default (e.g., an 'output' folder in the current directory)
		String defaultOutput = System.getProperty("user.dir") + File.separator + "output" + File.separator;
		String loadedOutputFolder = defaultOutput;

		// Try to load the config.properties file from the project root
		File configFile = new File("config.properties");
		if (configFile.exists()) {
			try (InputStream input = new FileInputStream(configFile)) {
				props.load(input);

				// Read the property, fallback to default if the key is missing
				String configuredPath = props.getProperty("output.folder", defaultOutput);

				// Ensure it ends with a separator for safety when concatenating later
				if (!configuredPath.endsWith(File.separator) && !configuredPath.endsWith("/")) {
					configuredPath += File.separator;
				}
				loadedOutputFolder = configuredPath;

			} catch (IOException ex) {
				LOGGER.warning("Could not read config.properties. Using default paths.");
			}
		} else {
			LOGGER.info("No config.properties found. Using default paths. You can copy config.template.properties to create one.");
		}

		// Assign the loaded (or default) value to the final variable
		OUTPUT_FOLDER = loadedOutputFolder;

		// Ensure the output directory exists and log its absolute path
		File outDir = new File(OUTPUT_FOLDER);
		if (!outDir.exists()) {
			outDir.mkdirs();
		}
		
		// Print the exact location to the console so the user always knows where it is
		LOGGER.info("Output folder resolved to: " + outDir.getAbsolutePath());

		// Load the default cognitive complexity threshold, defaulting to 15 if missing or unreadable
		int threshold = 15;
		try {
			threshold = Integer.parseInt(props.getProperty("complexity.threshold", "15"));
		} catch (NumberFormatException e) {
			LOGGER.severe("Invalid cognitive complexity threshold in config. Using default: 15");
		}
		COGNITIVE_COMPLEXITY_THRESHOLD = threshold;
		
		// Parse the working memory in megabytes, defaulting to 2048 MB (2 GB) if not found
	    int workingMemory = 2028;
	    try {
	    	Integer.parseInt(props.getProperty("working.memory", "2048"));
	    } catch (NumberFormatException e) {
	    	LOGGER.severe("Invalid working memory value in config. Using default: 2048");
		}
		WORKING_MEMORY = workingMemory;
		
		// Parse the time limit in seconds, defaulting to 300 seconds if not found
	    int timeLimit = 300;
	    try {
	    	Integer.parseInt(props.getProperty("time.limit", "300"));
	    } catch (NumberFormatException e) {
			LOGGER.severe("Invalid time limit value in config. Using default: 300");
		}
		TIME_LIMIT = timeLimit;
	    
	}
}