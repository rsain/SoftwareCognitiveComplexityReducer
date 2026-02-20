package neo.reducecognitivecomplexity.core.graphs;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.SimpleGraph;

/**
 * A container for the graph representations of the possible extractions.
 * <p>
 * This class holds different "views" of the refactoring opportunities found in
 * a method.
 * </p>
 * <ul>
 * <li><b>Conflict Graph:</b> Models mutually exclusive code extractions (e.g.,
 * overlapping code).</li>
 * <li><b>No-Conflict Graph:</b> Models compatible code extractions.</li>
 * <li><b>Full Graph:</b> A comprehensive view combining all relationships
 * (optional).</li>
 * </ul>
 */
public class GraphBundle {

	/**
	 * The Conflict Graph (Undirected).
	 * <p>
	 * <b>Vertices:</b> Represent potential method extractions
	 * ({@link ExtractionVertex}).<br>
	 * <b>Edges:</b> Represent a "conflict" between two extractions. An edge exists
	 * if two extractions cannot coexist (e.g., they attempt to extract overlapping
	 * lines of code).
	 * </p>
	 * <p>
	 * Solvers use this to find the "Maximum Independent Set" (the largest set of
	 * non-conflicting extractions).
	 * </p>
	 */
	public SimpleGraph<ExtractionVertex, DefaultEdge> conflicts = new SimpleGraph<>(DefaultEdge.class);

	/**
	 * The No-Conflict / Dependency Graph (Directed & Weighted).
	 * <p>
	 * <b>Vertices:</b> Represent potential method extractions.<br>
	 * <b>Edges:</b> Represent valid transitions between extractions that do
	 * <i>not</i> conflict.
	 * </p>
	 */
	public SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> noConflicts = new SimpleDirectedWeightedGraph<>(
			DefaultWeightedEdge.class);

	/**
	 * The Full Graph (Directed & Weighted).
	 * <p>
	 * This graph contains the complete set of relationships, potentially including
	 * both conflict and dependency edges, depending on the specific graph building
	 * strategy used. It may be null if the strategy only requires separate
	 * conflict/no-conflict views.
	 * </p>
	 */
	public SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> full = null;

	/**
	 * Clears all graph data.
	 * <p>
	 * Removes all vertices and edges from the contained graphs, resetting them for
	 * reuse or garbage collection.
	 * </p>
	 */
	public void clear() {
		if (full != null)
			Utils.clear(full);
		if (noConflicts != null)
			Utils.clear(noConflicts);
		if (conflicts != null)
			Utils.clear(conflicts);
	}
}