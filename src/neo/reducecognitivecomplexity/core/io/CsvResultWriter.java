package neo.reducecognitivecomplexity.core.io;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.jdt.CompilationUnitPathExtractor;
import neo.reducecognitivecomplexity.core.jdt.Utils;
import neo.reducecognitivecomplexity.core.jdt.JavaMethodProcessor.MethodComplexityRecord;

/**
 * Handles the output of analysis results to a CSV file.
 * <p>
 * This class is responsible for creating a summary record for every method
 * analyzed. It records the method's location, its cognitive complexity score,
 * the estimated effort (technical debt) required to fix it, and the time taken
 * to generate the refactoring cache.
 * </p>
 * <p>
 * Implements {@link AutoCloseable} to ensure the file stream is released
 * correctly when used in a try-with-resources block.
 * </p>
 */
public class CsvResultWriter implements AutoCloseable {

	private static final Logger LOGGER = Logger.getLogger(CsvResultWriter.class.getName());
	private PrintWriter csvWriter;

	/**
	 * Initializes the writer and prepares the CSV file.
	 * <p>
	 * This constructor attempts to create a new file at the specified path. If
	 * successful, it immediately writes the CSV header row.
	 * </p>
	 * * @param filePath the absolute path where the CSV file should be created. If
	 * the file exists, it will be overwritten.
	 */
	public CsvResultWriter(String filePath) {
		try {
			csvWriter = new PrintWriter(new FileWriter(filePath));
			// Write the header row
			csvWriter.println(
					"project,sourceFolder,package,class,method,lineNumber,complexity,effort,msecForRefactoringCache");
		} catch (IOException e) {
			LOGGER.severe("Failed to initialize CSV: " + e.getMessage());
		}
	}

	/**
	 * Writes a single method's analysis result to the CSV file.
	 * <p>
	 * This method also calculates the <b>remediation effort</b> (Technical Debt)
	 * required to refactor the method, based on the constants defined in
	 * {@link Constants}.
	 * </p>
	 *
	 * @param components object containing the path details (project, package, file)
	 *                   of the method
	 * @param r          the complexity record containing the method name, line
	 *                   number, and complexity score
	 * @param runtime    the time (in milliseconds) it took to generate the
	 *                   refactoring cache for this method
	 */
	public void writeResult(CompilationUnitPathExtractor.PathComponents components, MethodComplexityRecord r,
			long runtime) {
		if (csvWriter == null)
			return;

		// Calculate Technical Debt (Effort)
		int effort = Utils.computeEffort(r.complexity, Constants.COGNITIVE_COMPLEXITY_THRESHOLD,
				Constants.INITIAL_EFFORT_IN_MINUTES_FOR_CC_ISSUE,
				Constants.LINEAR_EFFORT_INCREMENT_IN_MINUTES_FOR_CC_ISSUE);

		// Write the row
		csvWriter.printf("%s,%s,%s,%s,%s,%d,%d,%d,%d\n", components.getProjectName(), components.getSourceFolder(),
				components.getPackageDirectory(), components.getFileName(), r.methodName, r.lineNumber, r.complexity,
				effort, runtime);

		csvWriter.flush();
	}

	/**
	 * Closes the underlying file stream.
	 * <p>
	 * This method is called automatically when exiting a try-with-resources block.
	 * </p>
	 */
	@Override
	public void close() {
		if (csvWriter != null)
			csvWriter.close();
	}
}