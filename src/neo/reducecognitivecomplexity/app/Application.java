package neo.reducecognitivecomplexity.app;

import java.util.logging.Logger;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

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
	 * sets up the environment, processes the input configuration, and triggers the
	 * {@link MethodRefactoringPipeline}.
	 * </p>
	 *
	 * @param context the application context, containing arguments and launch info
	 * @return {@link IApplication#EXIT_OK} if execution finishes successfully, or
	 *         an error code otherwise
	 * @throws Exception if a critical error occurs during execution
	 */
	@Override
	public Object start(IApplicationContext context) throws Exception {
		// Setup
		Utils.initializeEnvironment();
		Utils.disableAutoBuild();

		// Config
		// Retrieve arguments passed via the Eclipse Application launch config
		String[] args = (String[]) context.getArguments().get("application.args");

		// Parse arguments using our updated Config logic
		Config config = Config.parse(args);

		if (!config.isValid()) {
			LOGGER.severe(Constants.MESSAGE_WHEN_WRONG_ARGS);
			// In Eclipse applications, returning an Integer usually signals the exit code
			return -1;
		}

		// Execution
		runProjectAnalysis(config);

		return IApplication.EXIT_OK;
	}

	/**
	 * Orchestrates the analysis process for the specified project.
	 * <p>
	 * This method:
	 * <ul>
	 * <li>Initializes the {@link CsvResultWriter} for output.</li>
	 * <li>Scans the project for methods exceeding the complexity threshold.</li>
	 * <li>Feeds qualifying methods into the {@link MethodRefactoringPipeline}.</li>
	 * </ul>
	 * </p>
	 *
	 * @param config the configuration object containing the project name and solver
	 *               settings
	 */
	private void runProjectAnalysis(Config config) {
		JavaMethodProcessor jmp = new JavaMethodProcessor();

		// Use try-with-resources to ensure CSV is closed automatically
		// NOTE: CsvResultWriter must implement AutoCloseable for this to work
		try (CsvResultWriter resultWriter = new CsvResultWriter(
				Constants.OUTPUT_FOLDER + config.getProjectName() + ".csv")) {

			// Initialize the pipeline with dependencies
			// The pipeline will handle graph generation internally based on config
			MethodRefactoringPipeline pipeline = new MethodRefactoringPipeline(config, resultWriter);

			// Get methods from the target project
			var result = jmp.processProject(config.getProjectName());

			// Process methods that exceed the complexity threshold
			if (result != null && result.unitComplexities != null) {
				result.unitComplexities.forEach((unit, records) -> {
					records.stream().filter(r -> r.complexity > Constants.COGNITIVE_COMPLEXITY_THRESHOLD)
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