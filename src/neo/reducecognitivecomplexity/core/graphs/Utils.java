package neo.reducecognitivecomplexity.core.graphs;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.GraphTests;
import org.jgrapht.alg.clique.DegeneracyBronKerboschCliqueFinder;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.generate.ComplementGraphGenerator;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.SimpleGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.ExportException;
import org.jgrapht.nio.dot.DOTExporter;

import neo.reducecognitivecomplexity.app.Constants;

/**
 * Utility class for graph processing and analysis.
 * <p>
 * This class provides helper methods to:
 * <ul>
 * <li>Render graphs to DOT format (string or file).</li>
 * <li>Calculate graph metrics (distance, nesting depth).</li>
 * <li>Manipulate graph structures (remove vertices, bridge edges).</li>
 * <li>Find graph properties (roots, leaves, maximal independent sets).</li>
 * </ul>
 * </p>
 */
public class Utils {

	// =========================================================================
	// EXPORT / RENDERING
	// =========================================================================

	/**
	 * Renders a weighted directed graph into a DOT format string.
	 *
	 * @param g the graph to render
	 * @return the DOT string representation
	 * @throws ExportException if the graph cannot be exported
	 */
	public static String renderGraphInDotFormat(SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> g)
			throws ExportException {
		DOTExporter<ExtractionVertex, DefaultWeightedEdge> exporter = new DOTExporter<>();

		// Vertex Label: toString() of the vertex
		exporter.setVertexAttributeProvider(v -> {
			Map<String, Attribute> map = new LinkedHashMap<>();
			map.put("label", DefaultAttribute.createAttribute(v.toString()));
			return map;
		});

		// Edge Label: Weight (Red if weight is 0/conflict)
		exporter.setEdgeAttributeProvider(e -> {
			Map<String, Attribute> map = new LinkedHashMap<>();
			double weight = g.getEdgeWeight(e);
			if (weight == 0) {
				map.put("label", DefaultAttribute.createAttribute((int) weight));
				map.put("color", DefaultAttribute.createAttribute("red"));
			}
			return map;
		});

		Writer writer = new StringWriter();
		exporter.exportGraph(g, writer);
		return writer.toString();
	}

	/**
	 * Renders a conflict graph (undirected/simple) into a DOT format string.
	 *
	 * @param g the graph to render
	 * @return the DOT string representation
	 * @throws ExportException if the graph cannot be exported
	 */
	public static String renderConflictGraphInDotFormat(Graph<ExtractionVertex, DefaultEdge> g) throws ExportException {
		DOTExporter<ExtractionVertex, DefaultEdge> exporter = new DOTExporter<>();

		exporter.setVertexAttributeProvider(v -> {
			Map<String, Attribute> map = new LinkedHashMap<>();
			map.put("label", DefaultAttribute.createAttribute(v.toString()));
			return map;
		});

		exporter.setEdgeAttributeProvider(e -> {
			Map<String, Attribute> map = new LinkedHashMap<>();
			map.put("color", DefaultAttribute.createAttribute("red"));
			return map;
		});

		Writer writer = new StringWriter();
		exporter.exportGraph(g, writer);
		return writer.toString();
	}

	/**
	 * Renders a weighted directed graph to a DOT file at the specified location.
	 *
	 * @param g        the graph to export
	 * @param path     the directory path (ending with separator)
	 * @param fileName the name of the file
	 * @throws IOException if writing to the file fails
	 */
	public static void renderGraphInDotFormatInFile(
			SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> g, String path, String fileName)
			throws IOException {
		String dotInfo = renderGraphInDotFormat(g);
		try (FileWriter fw = new FileWriter(path + fileName, false)) {
			fw.append(dotInfo);
		}
	}

	/**
	 * Renders a conflict graph to a DOT file at the specified location.
	 *
	 * @param g        the graph to export
	 * @param path     the directory path
	 * @param fileName the name of the file
	 * @throws IOException if writing to the file fails
	 */
	public static void renderConflictGraphInDotFormatInFile(Graph<ExtractionVertex, DefaultEdge> g, String path,
			String fileName) throws IOException {
		String dotInfo = renderConflictGraphInDotFormat(g);
		try (FileWriter fw = new FileWriter(path + fileName, false)) {
			fw.append(dotInfo);
		}
	}

	// =========================================================================
	// GRAPH ALGORITHMS & METRICS
	// =========================================================================

	/**
	 * Finds all maximal independent sets in the given graph.
	 * <p>
	 * This is equivalent to finding maximal cliques in the complement graph.
	 * </p>
	 *
	 * @param graph the input graph
	 * @param <V>   vertex type
	 * @param <E>   edge type
	 * @return an iterable of sets, where each set is a maximal independent set
	 */
	public static <V> Iterable<Set<V>> getMaximalIndependentSets(SimpleGraph<V, DefaultEdge> graph) {

		// Now this is valid because E is effectively DefaultEdge
		SimpleGraph<V, DefaultEdge> complementGraph = new SimpleGraph<>(DefaultEdge.class);

		// Generate the complement graph
		ComplementGraphGenerator<V, DefaultEdge> cgg = new ComplementGraphGenerator<>(graph);
		cgg.generateGraph(complementGraph);

		// Return iterable over maximal cliques of the complement
		return new DegeneracyBronKerboschCliqueFinder<>(complementGraph);
	}

	/**
	 * Calculates the shortest path between two vertices using Dijkstra's algorithm.
	 *
	 * @param graph   the graph
	 * @param vertexA starting vertex
	 * @param vertexB target vertex
	 * @param <V>     vertex type
	 * @param <E>     edge type
	 * @return the path, or null if no path exists
	 */
	public static <V, E> GraphPath<V, E> getShortestPathBetweenVertices(SimpleDirectedGraph<V, E> graph, V vertexA,
			V vertexB) {
		DijkstraShortestPath<V, E> dijkstra = new DijkstraShortestPath<>(graph);
		return dijkstra.getPath(vertexA, vertexB);
	}

	/**
	 * Calculates the distance (number of edges) between two vertices.
	 *
	 * @param graph   the graph
	 * @param vertexA starting vertex
	 * @param vertexB target vertex
	 * @param <V>     vertex type
	 * @param <E>     edge type
	 * @return the length of the path, or -1 if no path exists
	 */
	public static <V, E> int getDistanceBetweenVertices(SimpleDirectedGraph<V, E> graph, V vertexA, V vertexB) {
		GraphPath<V, E> result = getShortestPathBetweenVertices(graph, vertexA, vertexB);
		return (result == null) ? -1 : result.getLength();
	}

	/**
	 * Calculates the nesting distance between two vertices.
	 * <p>
	 * The nesting distance is defined as the difference in nesting levels between
	 * the two vertices, assuming a path exists.
	 * </p>
	 *
	 * @param graph   the graph
	 * @param vertexA starting vertex
	 * @param vertexB target vertex
	 * @param <E>     edge type
	 * @return the difference in nesting (A - B)
	 * @throws RuntimeException if no path exists
	 */
	public static <E> int getNestingDistanceBetweenVertices(SimpleDirectedGraph<ExtractionVertex, E> graph,
			ExtractionVertex vertexA, ExtractionVertex vertexB) {
		GraphPath<ExtractionVertex, E> result = getShortestPathBetweenVertices(graph, vertexA, vertexB);

		if (result == null) {
			throw new RuntimeException("No connection between vertices: " + vertexA + " -> " + vertexB);
		}
		return vertexA.getNesting() - vertexB.getNesting();
	}

	// =========================================================================
	// GRAPH MANIPULATION
	// =========================================================================

	/**
	 * Removes all edges from the graph.
	 */
	public static <V, E> void removeAllEdges(Graph<V, E> graph) {
		graph.removeAllEdges(new ArrayList<>(graph.edgeSet()));
	}

	/**
	 * Clears the graph (removes all vertices and edges).
	 */
	public static <V, E> void clear(Graph<V, E> graph) {
		// Removing vertices automatically removes associated edges in JGraphT
		graph.removeAllVertices(new ArrayList<>(graph.vertexSet()));
	}

	/**
	 * Copies all vertices and edges from source graph to target graph.
	 *
	 * @param source graph to copy from
	 * @param target graph to copy to
	 */
	public static <V, E> void copy(Graph<V, E> source, Graph<V, E> target) {
		for (V v : source.vertexSet()) {
			target.addVertex(v);
		}
		for (E e : source.edgeSet()) {
			target.addEdge(source.getEdgeSource(e), source.getEdgeTarget(e), e);
		}
	}

	/**
	 * Removes a vertex from the graph but maintains connectivity by bridging its
	 * predecessors to its successors.
	 * <p>
	 * For every Predecessor P and Successor S of Vertex V: Add edge P -> S. Then
	 * remove V.
	 * </p>
	 *
	 * @param g the graph
	 * @param v the vertex to remove
	 * @return true if the graph was modified
	 */
	public static <V, E> boolean remove(Graph<V, E> g, V v) {
		if (!g.containsVertex(v))
			return false;

		List<V> successors = immediateNextVertices(g, v);
		List<V> predecessors = immediatePreviousVertices(g, v);

		for (V pred : predecessors) {
			for (V succ : successors) {
				if (!pred.equals(succ)) {
					g.addEdge(pred, succ);
				}
			}
		}
		return g.removeVertex(v);
	}

	// =========================================================================
	// TOPOLOGY HELPERS
	// =========================================================================

	public static <V, E> List<V> leaves(Graph<V, E> g) {
		return g.vertexSet().stream().filter(v -> g.outDegreeOf(v) == 0) // Changed to outDegree (Sink)
				.collect(Collectors.toList());
	}

	public static <V, E> List<V> roots(Graph<V, E> g) {
		return g.vertexSet().stream().filter(v -> g.inDegreeOf(v) == 0) // Changed to inDegree (Source)
				.collect(Collectors.toList());
	}

	public static <V, E> boolean isLeaf(Graph<V, E> g, V v) {
		return g.outDegreeOf(v) == 0;
	}

	public static <V, E> boolean isRoot(Graph<V, E> g, V v) {
		return g.inDegreeOf(v) == 0;
	}

	/**
	 * Returns immediate predecessors (sources of incoming edges).
	 */
	public static <V, E> List<V> immediatePreviousVertices(Graph<V, E> g, V v) {
		List<V> result = new ArrayList<>();
		if (g.containsVertex(v)) {
			for (E e : g.incomingEdgesOf(v)) {
				result.add(g.getEdgeSource(e));
			}
		}
		return result;
	}

	/**
	 * Returns immediate successors (targets of outgoing edges).
	 */
	public static <V, E> List<V> immediateNextVertices(Graph<V, E> g, V v) {
		List<V> result = new ArrayList<>();
		if (g.containsVertex(v)) {
			for (E e : g.outgoingEdgesOf(v)) {
				result.add(g.getEdgeTarget(e));
			}
		}
		return result;
	}

	/**
	 * Recursively finds all ancestors of a vertex.
	 * <p>
	 * <b>Warning:</b> Assumes acyclic graph.
	 * </p>
	 */
	public static <V, E> List<V> previousVertices(Graph<V, E> g, V v) {
		if (!g.containsVertex(v))
			return new ArrayList<>();
		return previousVerticesRecursion(g, v, new ArrayList<>());
	}

	private static <V, E> List<V> previousVerticesRecursion(Graph<V, E> g, V v, List<V> explored) {
		for (V prev : immediatePreviousVertices(g, v)) {
			if (!explored.contains(prev)) {
				explored.add(prev);
				previousVerticesRecursion(g, prev, explored);
			}
		}
		return explored;
	}

	// =========================================================================
	// DOMAIN SPECIFIC: EXTRACTION VERTEX HELPERS
	// =========================================================================

	/**
	 * Creates an {@link ExtractionVertex} representing the entire method body.
	 *
	 * @param ast the MethodDeclaration AST node
	 * @return the root extraction vertex
	 */
	public static ExtractionVertex getRootForGraphAssociatedToMethodBody(ASTNode ast) {
		MethodDeclaration methodDecl = (MethodDeclaration) ast;
		Block methodBody = methodDecl.getBody();

		if (methodBody == null || methodBody.statements().isEmpty()) {
			// Handle empty methods gracefully if needed, or throw error
			// returning null or a zero-length vertex depending on requirements
			return null;
		}

		int startPos = ((Statement) methodBody.statements().get(0)).getStartPosition();
		Statement lastStmt = (Statement) methodBody.statements().get(methodBody.statements().size() - 1);
		int endPos = lastStmt.getStartPosition() + lastStmt.getLength();

		// Retrieve pre-calculated properties from AST
		int inherent = (int) ast.getProperty(Constants.ACCUMULATED_INHERENT_COGNITIVE_COMPLEXITY);
		int nesting = (int) ast.getProperty(Constants.ACCUMULATED_NESTING_COGNITIVE_COMPLEXITY);
		int contributors = (int) ast.getProperty(Constants.NUMBER_OF_NESTING_CONTRIBUTORS);

		return new ExtractionVertex(startPos, endPos, inherent + nesting, inherent, nesting, contributors, 0);
	}

	/**
	 * Returns a map of vertices to their sorted index.
	 */
	public static <E> Map<ExtractionVertex, Integer> mapVerticesToTheirLexicographicPosition(
			Graph<ExtractionVertex, E> g) {
		Map<ExtractionVertex, Integer> result = new HashMap<>();
		List<ExtractionVertex> vertices = getVerticesSortedByTheirLexicographicPosition(g);
		for (int i = 0; i < vertices.size(); i++) {
			result.put(vertices.get(i), i);
		}
		return result;
	}

	/**
	 * Returns vertices sorted by their natural order (offset), with the root (Sink)
	 * first.
	 */
	public static <E> List<ExtractionVertex> getVerticesSortedByTheirLexicographicPosition(
			Graph<ExtractionVertex, E> g) {

		List<ExtractionVertex> result = new ArrayList<>();
		List<ExtractionVertex> sortedList = new ArrayList<>();
		ExtractionVertex root = null;

		for (ExtractionVertex v : g.vertexSet()) {
			// In dependency graphs here, Root is defined as having no outgoing edges (The
			// "Whole")
			if (g.outDegreeOf(v) == 0) {
				if (root != null) {
					System.err.println("WARNING: Multiple roots found in graph. Using: " + root);
				}
				root = v;
			} else {
				sortedList.add(v);
			}
		}

		Collections.sort(sortedList);

		if (root != null) {
			result.add(root);
		}
		result.addAll(sortedList);

		return result;
	}

	/**
	 * Returns a list of vertices that exist structurally "between" two vertices. v2
	 * must be contained within v1.
	 */
	public static <E> List<ExtractionVertex> verticesBetweenTwoVertices(Graph<ExtractionVertex, E> g,
			ExtractionVertex v1, ExtractionVertex v2) {

		if (g.containsVertex(v1) && g.containsVertex(v2) && v2.isContained(v1)) {
			return verticesBetweenTwoVerticesRecursion(g, v1, v2, new ArrayList<>());
		}
		return new ArrayList<>();
	}

	private static <E> List<ExtractionVertex> verticesBetweenTwoVerticesRecursion(Graph<ExtractionVertex, E> g,
			ExtractionVertex v1, ExtractionVertex v2, List<ExtractionVertex> explored) {

		for (ExtractionVertex next : immediateNextVertices(g, v2)) {
			// Stop if we hit the outer boundary (v1)
			if (next.equals(v1))
				continue;

			// Ensure we are still inside v1 boundaries
			if (next.isContained(v1)) {
				if (!explored.contains(next)) {
					explored.add(next);
					verticesBetweenTwoVerticesRecursion(g, v1, next, explored);
				}
			}
		}
		return explored;
	}

	// =========================================================================
	// TREE UTILITIES
	// =========================================================================

	public static <V, E> boolean checkTree(Graph<V, E> graph) {
		boolean rootFound = false;
		for (V vertex : graph.vertexSet()) {
			if (graph.outDegreeOf(vertex) == 0) {
				if (rootFound)
					return false;
				rootFound = true;
			} else if (graph.outDegreeOf(vertex) > 1) {
				return false;
			}
		}
		// JGraphT requires Undirected view for isTree check
		return GraphTests.isTree(new AsUndirectedGraph<>(graph));
	}

	public static <V, E> V anyLeaf(Graph<V, E> tree) {
		Set<V> vertices = tree.vertexSet();
		if (vertices.isEmpty())
			throw new NoSuchElementException();

		V vertex = vertices.iterator().next();
		// Traverse up until we hit a node with no incoming edges (Leaf in this
		// dependency direction)
		while (tree.inDegreeOf(vertex) > 0) {
			E edge = tree.incomingEdgesOf(vertex).iterator().next();
			vertex = tree.getEdgeSource(edge);
		}
		return vertex;
	}

	public static <V, E> void removeSubTree(Graph<V, E> tree, V vertex) {
		Queue<V> toExplore = new LinkedList<>();
		List<V> toRemove = new ArrayList<>();

		toExplore.add(vertex);

		// BFS to find all ancestors (dependencies)
		while (!toExplore.isEmpty()) {
			V current = toExplore.poll();
			toRemove.add(current);

			// In dependency graphs, children depend on parent?
			// Based on `immediatePreviousVertices` usage in original code, it flows upwards
			for (V prev : immediatePreviousVertices(tree, current)) {
				if (!toRemove.contains(prev) && !toExplore.contains(prev)) {
					toExplore.add(prev);
				}
			}
		}
		tree.removeAllVertices(toRemove);
	}

	public static ExtractionVertex getParent(SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> tree,
			ExtractionVertex vertex) {
		if (tree.outDegreeOf(vertex) == 0)
			return null;

		DefaultWeightedEdge edge = tree.outgoingEdgesOf(vertex).iterator().next();
		return tree.getEdgeTarget(edge);
	}
}