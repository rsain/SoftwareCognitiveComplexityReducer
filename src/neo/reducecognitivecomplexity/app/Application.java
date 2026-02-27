package neo.reducecognitivecomplexity.app;

import java.util.logging.Logger;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import neo.reducecognitivecomplexity.core.BatchCsvProcessor;
import neo.reducecognitivecomplexity.core.MethodRefactoringPipeline;
import neo.reducecognitivecomplexity.core.eclipse.Utils;
import neo.reducecognitivecomplexity.core.io.CsvResultWriter;
import neo.reducecognitivecomplexity.core.jdt.JavaMethodProcessor;

/**
 * The main entry point for the "Reduce Cognitive Complexity" Eclipse
 * application.
 * <p>
 * This class implements the {@link IApplication} interface, allowing the plugin
 * to be run as a standalone Eclipse Application (headless or UI-based) rather
 * than just contributing to an existing workbench.
 * </p>
 * <p>
 * Its primary responsibilities are:
 * <ol>
 * <li>Parsing command-line arguments via {@link Config}.</li>
 * <li>Initializing the Eclipse workspace environment (disabling
 * auto-build).</li>
 * <li>Orchestrating the analysis and refactoring pipeline.</li>
 * </ol>
 * </p>
 */
public class Application implements IApplication {

	private static final Logger LOGGER = Logger.getLogger(Application.class.getName());

	/**
	 * Starts the application.
	 * <p>
	 * This method is the equivalent of a standard Java {@code main()} method. It
	 * sets up the environment, processes the input configuration, and triggers 
	 * either the targeted {@link BatchCsvProcessor} or a full project analysis.
	 * </p>
	 *
	 * @param context the application context, containing arguments and launch info
	 * @return {@link IApplication#EXIT_OK} if execution finishes successfully, or
	 * an error code otherwise
	 * @throws Exception if a critical error occurs during execution
	 */
	@Override
	public Object start(IApplicationContext context) throws Exception {
		// Setup Environment
		Utils.initializeEnvironment();
		Utils.initializeWorkspace(); // Ensures JDT UI prefs and workspace are fully loaded
		Utils.disableAutoBuild();    // Prevents performance hits from automatic recompilation

		// Retrieve & Parse Configuration
		String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		Config config = Config.parse(args);

		if (!config.isValid()) {
			LOGGER.severe(Constants.MESSAGE_WHEN_WRONG_ARGS);
			return -1; // Return error code
		}

		// Initialize Central Results Writer
		// Note: The file name relies on the project name or a default "batch_results.csv"
		String resultsFileName = config.getProjectName() != null ? 
				config.getProjectName() + "_results.csv" : "batch_results.csv";
		String outputCsvPath = Constants.OUTPUT_FOLDER + resultsFileName;
		
		// Try-with-resources ensures the writer is safely closed when the app finishes
		try (CsvResultWriter resultWriter = new CsvResultWriter(outputCsvPath)) {
			
			// Execution Branching
			if (config.getCsvFilePath() != null && !config.getCsvFilePath().isEmpty()) {
				// --- MODE BATCH CSV SCAN ---
				LOGGER.info("Starting Batch CSV Processing Mode...");
				BatchCsvProcessor processor = new BatchCsvProcessor(config, resultWriter);
				processor.processCsv(config.getCsvFilePath());
			} else {
				// --- MODE FULL PROJECT SCAN ---
				LOGGER.info("Starting Full Project Analysis Mode...");
				runProjectAnalysis(config, resultWriter); 
			}

		} catch (Exception e) {
			LOGGER.severe("A critical error occurred during execution: " + e.getMessage());
			e.printStackTrace();
			return -1;
		}

		LOGGER.info("Application finished successfully.");
		return IApplication.EXIT_OK;
	}

	/**
	 * Orchestrates the full-project analysis process for the specified project.
	 * <p>
	 * This method:
	 * <ul>
	 * <li>Scans the project for methods exceeding the configured complexity threshold.</li>
	 * <li>Feeds qualifying methods into the {@link MethodRefactoringPipeline}.</li>
	 * </ul>
	 * </p>
	 *
	 * @param config       the configuration object containing the project name and solver settings
	 * @param resultWriter the shared writer to output analysis metrics and refactoring results
	 */
	private void runProjectAnalysis(Config config, CsvResultWriter resultWriter) {
		JavaMethodProcessor jmp = new JavaMethodProcessor();

		try {
			// Initialize the pipeline with dependencies
			// The pipeline will handle graph generation internally based on config
			MethodRefactoringPipeline pipeline = new MethodRefactoringPipeline(config, resultWriter);

			// Get methods from the target project
			var result = jmp.processProject(config.getProjectName());

			// Process methods that exceed the complexity threshold
			if (result != null && result.unitComplexities != null) {
				result.unitComplexities.forEach((unit, records) -> {
					records.stream()
					        .filter(r -> r.complexity > Constants.COGNITIVE_COMPLEXITY_THRESHOLD)
							.forEach(record -> pipeline.process(unit, record));
				});
			}

		} catch (Exception e) {
			LOGGER.severe("Analysis failed: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Stops the application.
	 * <p>
	 * This method is called by the platform to force the application to exit. Since
	 * this is a batch-processing application, no specific stop logic is required.
	 * </p>
	 */
	@Override
	public void stop() {
		// No cleanup required for forced stop
	}

}