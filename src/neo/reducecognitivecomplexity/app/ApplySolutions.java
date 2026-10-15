package neo.reducecognitivecomplexity.app;

import java.io.File;
import java.util.logging.Logger;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import neo.reducecognitivecomplexity.core.SavedSolutionProcessor;
import neo.reducecognitivecomplexity.core.eclipse.Utils;

/**
 * Main entry point for the Eclipse Headless Application mode.
 * <p>
 * Applies a batch of saved refactoring solution files ({@code .result}) to
 * target projects in the current workspace.
 * </p>
 * <p>
 * <b>Required Arguments:</b>
 * <ol>
 * <li>Path to the folder containing {@code .result} files.</li>
 * </ol>
 * </p>
 */
public class ApplySolutions implements IApplication {

	private static final Logger LOGGER = Logger.getLogger(ApplySolutions.class.getName());
	private static final int EXPECTED_ARGS = 1;
	private static final Integer EXIT_ERROR = Integer.valueOf(-1);

	@Override
	public Object start(IApplicationContext context) {
		try {
			// Setup workspace environment
			LOGGER.info("Initializing Eclipse workspace environment...");
			Utils.initializeEnvironment();
			Utils.initializeWorkspace();
			Utils.disableAutoBuild();

			// Retrieve and validate command line arguments
			String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
			if (!validateArguments(args)) {
				return EXIT_ERROR;
			}

			String resultsFolderPath = args[0];

			// Execute solution processor
			LOGGER.info("Starting Saved Solutions Refactoring Application Mode...");
			SavedSolutionProcessor processor = new SavedSolutionProcessor();
			processor.applySavedSolutions(resultsFolderPath);

			LOGGER.info("Application finished successfully.");
			return IApplication.EXIT_OK;

		} catch (Exception e) {
			LOGGER.severe("Fatal error during solution refactoring execution: " + e.getMessage());
			e.printStackTrace();
			return EXIT_ERROR;
		}
	}

	/**
	 * Validates that arguments are non-null, correct in count, and point to an
	 * existing directory.
	 */
	private boolean validateArguments(String[] args) {
		if (args == null || args.length != EXPECTED_ARGS) {
			LOGGER.severe(
					"Invalid parameters. Exactly 1 parameter is required: path to the folder containing '.result' files.");
			return false;
		}

		File resultsFolder = new File(args[0]);
		if (!resultsFolder.exists() || !resultsFolder.isDirectory()) {
			LOGGER.severe("Target path does not exist or is not a valid directory: " + args[0]);
			return false;
		}

		return true;
	}

	@Override
	public void stop() {
		// No cleanup required for forced stop
	}
}