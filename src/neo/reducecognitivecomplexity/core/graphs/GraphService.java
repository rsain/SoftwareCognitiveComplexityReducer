package neo.reducecognitivecomplexity.core.graphs;

import java.io.IOException;
import java.util.logging.Logger;

import org.eclipse.jdt.core.dom.MethodDeclaration;

import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCache;
import neo.reducecognitivecomplexity.core.solvers.SolverContext;

/**
 * Service class responsible for building and exporting graph representations of
 * refactoring opportunities.
 * <p>
 * This class acts as a bridge between the {@link RefactoringCache} (which holds
 * the raw data) and the {@link GraphBundle} (which holds the structural graph
 * views). It also handles the serialization of these graphs to DOT format for
 * external visualization.
 * </p>
 */
public class GraphService {

	private static final Logger LOGGER = Logger.getLogger(GraphService.class.getName());

	/**
	 * Builds the complete set of graphs (Conflict, No-Conflict, Full) from the
	 * refactoring cache. * @param cache the cache containing valid refactoring
	 * opportunities
	 * 
	 * @param ast the AST node of the method being analyzed (used to identify the
	 *            root)
	 * @return a populated {@link GraphBundle} containing the generated graphs
	 */
	public static GraphBundle buildGraphs(RefactoringCache cache, MethodDeclaration ast) {
		LOGGER.info("Building dependency and conflict graphs...");

		// Identify the root vertex corresponding to the method body
		ExtractionVertex root = Utils.getRootForGraphAssociatedToMethodBody(ast);
		LOGGER.info("Graph root identified: " + root.toString());

		GraphBundle bundle = new GraphBundle();

		// Populate the graphs via the cache's logic.
		// Note: 'noConflicts' and 'conflicts' are initialized empty in the bundle
		// and passed by reference to be populated.
		bundle.full = cache.getGraphOfFeasibleRefactorings(bundle.noConflicts, bundle.conflicts);

		return bundle;
	}

	/**
	 * Exports the generated graphs to DOT files in the output directory.
	 * <p>
	 * This generates three files:
	 * <ul>
	 * <li><b>Full Graph:</b> All edges and vertices.</li>
	 * <li><b>No-Conflict Graph:</b> Dependency edges only.</li>
	 * <li><b>Conflict Graph:</b> Conflict edges only.</li>
	 * </ul>
	 * </p>
	 *
	 * @param bundle the bundle containing the graphs to export
	 * @param ctx    the current solver context (used for naming the output files)
	 * @throws IOException if writing to the file system fails
	 */
	public static void exportGraphs(GraphBundle bundle, SolverContext ctx) throws IOException {
		// Generate a unique file prefix based on the project/class/method
		String fileName = ctx.generatePrefixForSolverFileNames();

		LOGGER.info("Exporting graphs to " + Constants.OUTPUT_FOLDER);

		// Export Full Graph
		Utils.renderGraphInDotFormatInFile(bundle.full, Constants.OUTPUT_FOLDER,
				fileName + Constants.FILE_EXTENSION_FOR_FULL_GRAPH);

		// Export No-Conflict (Dependency) Graph
		Utils.renderGraphInDotFormatInFile(bundle.noConflicts, Constants.OUTPUT_FOLDER,
				fileName + Constants.FILE_EXTENSION_FOR_GRAPH_WITHOUT_CONFLICT);

		// Export Conflict Graph
		Utils.renderConflictGraphInDotFormatInFile(bundle.conflicts, Constants.OUTPUT_FOLDER,
				fileName + Constants.FILE_EXTENSION_FOR_CONFLICT_GRAPH);
	}
}