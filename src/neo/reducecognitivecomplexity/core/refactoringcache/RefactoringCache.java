package neo.reducecognitivecomplexity.core.refactoringcache;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.ltk.core.refactoring.Change;
import org.jgrapht.alg.TransitiveReduction;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.graph.SimpleGraph;

import neo.reducecognitivecomplexity.core.ExtractionTextRange;
import neo.reducecognitivecomplexity.core.Sequence;
import neo.reducecognitivecomplexity.core.graphs.ExtractionVertex;
import neo.reducecognitivecomplexity.core.jdt.CodeExtractionMetrics;

/**
 * Models a cache of refactoring opportunities found during the search.
 * <p>
 * This cache speeds up the search for code extraction sequences. When a code
 * extraction ({@link Sequence}) is evaluated, this cache is queried. If there
 * is a hit, metrics are retrieved directly. Otherwise, the oracle evaluates the
 * feasibility, and the result is stored here.
 * </p>
 */
public class RefactoringCache {

	private final CompilationUnit compilationUnit;
	private final MethodDeclaration methodDeclaration;

	/**
	 * Map storing metrics for specific code offsets (start, end).
	 */
	public Map<ExtractionTextRange, CodeExtractionMetrics> cache;

	public RefactoringCache(CompilationUnit compilationUnit, MethodDeclaration methodDeclaration) {
		this.compilationUnit = compilationUnit;
		this.methodDeclaration = methodDeclaration;
		this.cache = new HashMap<>();
	}

	public RefactoringCache(String path, String fileName, CompilationUnit compilationUnit) throws IOException {
		this(path, fileName, compilationUnit, null);
	}

	/**
	 * Initialize the refactoring cache from a CSV file.
	 * <p>
	 * Assumes the CSV header: "Offset_A, Offset_B, feasibility, reason,
	 * #parameters, extractedLOC, reductionCC, extractedMethodCC,
	 * accumulatedInherentComponent, accumulatedNestingComponent,
	 * numberNestingContributors, nesting, runtime"
	 * </p>
	 * 
	 * @param path              Directory path of the CSV file.
	 * @param fileName          Name of the CSV file.
	 * @param compilationUnit   The AST CompilationUnit.
	 * @param methodDeclaration The MethodDeclaration (can be null).
	 * @throws IOException If file reading fails.
	 */
	public RefactoringCache(String path, String fileName, CompilationUnit compilationUnit,
			MethodDeclaration methodDeclaration) throws IOException {
		this.compilationUnit = compilationUnit;
		this.methodDeclaration = methodDeclaration;
		this.cache = new HashMap<>();

		try (BufferedReader br = new BufferedReader(new FileReader(path + fileName))) {
			String line = br.readLine(); // Skip CSV header

			while ((line = br.readLine()) != null) {
				String[] tokens = line.split(",");
				// Parse feasibility (1 = true, 0 = false)
				boolean isFeasible = Integer.parseInt(tokens[2].trim()) == 1;

				CodeExtractionMetrics metrics = new CodeExtractionMetrics(isFeasible, tokens[3], // reason
						false, // isVariableDeclaration (defaulted false in original)
						Integer.parseInt(tokens[5].trim()), // extractedLOC
						Integer.parseInt(tokens[4].trim()), // parameters
						new ArrayList<Change>(), new ArrayList<Change>(), Integer.parseInt(tokens[6].trim()), // reductionCC
						Integer.parseInt(tokens[8].trim()), // accumulatedInherent
						Integer.parseInt(tokens[9].trim()), // accumulatedNesting
						Integer.parseInt(tokens[10].trim()), // numberNestingContributors
						Integer.parseInt(tokens[11].trim()), // nesting
						Long.parseLong(tokens[12].trim()) // runtime
				);

				ExtractionTextRange pair = new ExtractionTextRange(Integer.parseInt(tokens[0].trim()), Integer.parseInt(tokens[1].trim()));
				this.cache.put(pair, metrics);
			}
		}
	}

	/**
	 * Get metrics for a specific sequence of code.
	 *
	 * @param sequence The sequence to evaluate.
	 * @return A copy of the Metrics if found/evaluated, or null if the sequence is
	 *         empty.
	 */
	public CodeExtractionMetrics getMetrics(Sequence sequence) {
		if (sequence.getSiblingNodes().isEmpty()) {
			return null;
		}

		ExtractionTextRange key = sequence.getTextRange();
		CodeExtractionMetrics result = cache.get(key);

		if (result == null) {
			// Miss: Evaluate and update cache
			result = sequence.evaluate();
			cache.put(key, result);
		}

		// Return a copy to prevent external modification of the cached value
		return new CodeExtractionMetrics(result);
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		long countFeasible = cache.values().stream().filter(CodeExtractionMetrics::isFeasible).count();
		long countUnfeasible = cache.size() - countFeasible;

		result.append("Elements in the cache = ").append(cache.size()).append(" (feasible: ").append(countFeasible)
				.append(", unfeasible: ").append(countUnfeasible).append(")").append(System.lineSeparator());

		return result.toString();
	}

	/**
	 * Write the refactoring cache to a CSV file.
	 *
	 * @param path     Directory path.
	 * @param fileName File name.
	 * @throws IOException If writing fails.
	 */
	public void writeToCSV(String path, String fileName) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path + fileName, false))) {
			writer.write(
					"A, B, feasible, reason, parameters, extractedLOC, reductionCC, extractedMethodCC, accumulatedInherentComponent, accumulatedNestingComponent, numberNestingContributors, nesting, runtime");
			writer.newLine();

			for (Entry<ExtractionTextRange, CodeExtractionMetrics> entry : cache.entrySet()) {
				CodeExtractionMetrics metrics = entry.getValue();
				String reason = metrics.getReason();

				if (!metrics.isFeasible()) {
					reason = reason.replaceAll(System.lineSeparator(), " ");
				}

				StringBuilder line = new StringBuilder();
				line.append(entry.getKey().getStart()).append(", ").append(entry.getKey().getEnd()).append(", ")
						.append(metrics.isFeasible() ? "1" : "0").append(", ").append("\"").append(reason)
						.append("\", ").append(metrics.getNumberOfParametersInExtractedMethod()).append(", ")
						.append(metrics.getNumberOfExtractedLinesOfCode()).append(", ")
						.append(metrics.getReductionOfCognitiveComplexity()).append(", ")
						.append(metrics.getCognitiveComplexityOfNewExtractedMethod()).append(", ")
						.append(metrics.getAccumulatedInherentComponent()).append(", ")
						.append(metrics.getAccumulatedNestingComponent()).append(", ")
						.append(metrics.getNumberNestingContributors()).append(", ").append(metrics.getNesting())
						.append(", ").append(metrics.getRuntime());

				writer.write(line.toString());
				writer.newLine();
			}
		}
	}

	/**
	 * Generate directed weighted graphs associated to the refactoring cache.
	 * <p>
	 * Populates two graphs: 1. A dependency graph of feasible refactorings (based
	 * on containment). 2. A conflict graph where refactorings overlap.
	 * </p>
	 *
	 * @param graphWithoutConflicts Target graph to store valid inclusions (no
	 *                              conflicts).
	 * @param conflictsGraph        Target graph to store conflicts.
	 * @return Directed weighted graph including conflicts (edges with weight 0).
	 */
	public SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> getGraphOfFeasibleRefactorings(
			SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> graphWithoutConflicts,
			SimpleGraph<ExtractionVertex, DefaultEdge> conflictsGraph) {

		neo.reducecognitivecomplexity.core.graphs.Utils.clear(graphWithoutConflicts);
		neo.reducecognitivecomplexity.core.graphs.Utils.clear(conflictsGraph);

		Map<ExtractionTextRange, CodeExtractionMetrics> feasibleRefactorings = Utils.filterByValue(cache,
				CodeExtractionMetrics::isFeasible);
		List<ExtractionTextRange> offsetPairs = new ArrayList<>(feasibleRefactorings.keySet());

		SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> result = new SimpleDirectedWeightedGraph<>(
				DefaultWeightedEdge.class);

		// Add vertices and edges based on spatial relationships (containment vs
		// overlap)
		for (int i = 0; i < offsetPairs.size(); i++) {
			ExtractionTextRange p = offsetPairs.get(i);
			ExtractionVertex vertexP = createVertex(p, feasibleRefactorings.get(p));
			result.addVertex(vertexP);

			for (int j = i + 1; j < offsetPairs.size(); j++) {
				ExtractionTextRange q = offsetPairs.get(j);
				ExtractionVertex vertexQ = createVertex(q, feasibleRefactorings.get(q));
				result.addVertex(vertexQ);

				if (ExtractionTextRange.isContained(q, p)) {
					// q is inside p -> Edge from Q to P
					DefaultWeightedEdge edge = result.addEdge(vertexQ, vertexP);
					if (edge != null)
						result.setEdgeWeight(edge, 1);
				} else if (ExtractionTextRange.isContained(p, q)) {
					// p is inside q -> Edge from P to Q
					DefaultWeightedEdge edge = result.addEdge(vertexP, vertexQ);
					if (edge != null)
						result.setEdgeWeight(edge, 1);
				} else if (ExtractionTextRange.overlapping(p, q)) {
					// Partial overlap -> Conflict
					conflictsGraph.addVertex(vertexP);
					conflictsGraph.addVertex(vertexQ);
					conflictsGraph.addEdge(vertexQ, vertexP);
				}
			}
		}

		// Perform Transitive Reduction to simplify the dependency graph
		// This removes redundant edges (e.g., if A->B and B->C, remove A->C)

		TransitiveReduction.INSTANCE.reduce(result);

		// Copy the clean dependency graph
		neo.reducecognitivecomplexity.core.graphs.Utils.copy(result, graphWithoutConflicts);

		// Add conflict edges back into the result (with weight 0)
		for (DefaultEdge e : conflictsGraph.edgeSet()) {
			ExtractionVertex source = conflictsGraph.getEdgeSource(e);
			ExtractionVertex target = conflictsGraph.getEdgeTarget(e);

			// Add edge in both directions for conflicts
			DefaultWeightedEdge we1 = result.addEdge(source, target);
			if (we1 != null)
				result.setEdgeWeight(we1, 0);

			DefaultWeightedEdge we2 = result.addEdge(target, source);
			if (we2 != null)
				result.setEdgeWeight(we2, 0);
		}

		return result;
	}

	/**
	 * Generate a directed weighted graph for the refactoring cache (Simplified
	 * version). This version does not output the conflict graph separately.
	 *
	 * @param root The root node (usually the method declaration).
	 * @return Directed weighted graph.
	 */
	public SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> getGraphOfFeasibleRefactorings(
			ExtractionVertex root) {
		Map<ExtractionTextRange, CodeExtractionMetrics> feasibleRefactorings = Utils.filterByValue(cache,
				CodeExtractionMetrics::isFeasible);
		List<ExtractionTextRange> offsetPairs = new ArrayList<>(feasibleRefactorings.keySet());

		SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> result = new SimpleDirectedWeightedGraph<>(
				DefaultWeightedEdge.class);

		// Build Graph
		for (int i = 0; i < offsetPairs.size(); i++) {
			ExtractionTextRange p = offsetPairs.get(i);
			ExtractionVertex vertexP = createVertex(p, feasibleRefactorings.get(p));
			result.addVertex(vertexP);

			for (int j = i + 1; j < offsetPairs.size(); j++) {
				ExtractionTextRange q = offsetPairs.get(j);
				ExtractionVertex vertexQ = createVertex(q, feasibleRefactorings.get(q));
				result.addVertex(vertexQ);

				if (ExtractionTextRange.isContained(q, p)) {
					DefaultWeightedEdge edge = result.addEdge(vertexQ, vertexP);
					if (edge != null)
						result.setEdgeWeight(edge, 1);
				} else if (ExtractionTextRange.isContained(p, q)) {
					DefaultWeightedEdge edge = result.addEdge(vertexP, vertexQ);
					if (edge != null)
						result.setEdgeWeight(edge, 1);
				}
			}
		}

		// Attach Root (Method Declaration)
		if (root != null && result.addVertex(root)) {
			for (ExtractionVertex v : result.vertexSet()) {
				if (!v.equals(root) && result.outDegreeOf(v) == 0) {
					DefaultWeightedEdge edge = result.addEdge(v, root);
					if (edge != null)
						result.setEdgeWeight(edge, 1);
				}
			}
		}

		TransitiveReduction.INSTANCE.reduce(result);
		return result;
	}

	/**
	 * Reduces the graph by removing redundant refactorings that do not offer better
	 * cognitive complexity reduction than their sub-components.
	 */
	public SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> reduceGraphOfFeasibleRefactorings(
			ExtractionVertex vertexForMethod, int methodCognitiveComplexity,
			SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> graphWithoutConflicts,
			SimpleGraph<ExtractionVertex, DefaultEdge> conflictsGraph) {

		SimpleDirectedWeightedGraph<ExtractionVertex, DefaultWeightedEdge> result = new SimpleDirectedWeightedGraph<>(
				DefaultWeightedEdge.class);
		SimpleGraph<ExtractionVertex, DefaultEdge> conflictsReducedGraph = new SimpleGraph<>(DefaultEdge.class);

		neo.reducecognitivecomplexity.core.graphs.Utils.copy(graphWithoutConflicts, result);
		neo.reducecognitivecomplexity.core.graphs.Utils.copy(conflictsGraph, conflictsReducedGraph);

		// Pruning logic: If a child node has the same CC reduction as the parent, the
		// parent is redundant.
		for (ExtractionVertex currentVertex : graphWithoutConflicts.vertexSet()) {
			if (!currentVertex.equals(vertexForMethod)) {
				List<ExtractionVertex> children = neo.reducecognitivecomplexity.core.graphs.Utils
						.immediatePreviousVertices(result, currentVertex);

				for (ExtractionVertex child : children) {
					if (currentVertex.getReductionOfCognitiveComplexity() == child
							.getReductionOfCognitiveComplexity()) {
						neo.reducecognitivecomplexity.core.graphs.Utils.remove(result, currentVertex);
						conflictsReducedGraph.removeVertex(currentVertex);
						break;
					}
				}
			}
		}

		TransitiveReduction.INSTANCE.reduce(result);

		// Reset and update the input graphs with the reduced versions
		neo.reducecognitivecomplexity.core.graphs.Utils.clear(graphWithoutConflicts);
		neo.reducecognitivecomplexity.core.graphs.Utils.clear(conflictsGraph);
		neo.reducecognitivecomplexity.core.graphs.Utils.copy(result, graphWithoutConflicts);
		neo.reducecognitivecomplexity.core.graphs.Utils.copy(conflictsReducedGraph, conflictsGraph);

		// Re-add conflict edges
		for (DefaultEdge e : conflictsReducedGraph.edgeSet()) {
			ExtractionVertex source = conflictsReducedGraph.getEdgeSource(e);
			ExtractionVertex target = conflictsReducedGraph.getEdgeTarget(e);

			DefaultWeightedEdge we1 = result.addEdge(source, target);
			if (we1 != null)
				result.setEdgeWeight(we1, 0);

			DefaultWeightedEdge we2 = result.addEdge(target, source);
			if (we2 != null)
				result.setEdgeWeight(we2, 0);
		}

		return result;
	}

	/**
	 * Reduce the refactoring cache by removing overlapped extractions that have
	 * identical complexity reduction.
	 *
	 * @return A new RefactoringCache containing only the reduced set.
	 */
	public RefactoringCache reduce() {
		RefactoringCache result = new RefactoringCache(this.compilationUnit, methodDeclaration);

		Map<ExtractionTextRange, CodeExtractionMetrics> feasibleRefactorings = Utils.filterByValue(cache,
				CodeExtractionMetrics::isFeasible);
		List<ExtractionTextRange> offsetPairs = new ArrayList<>(feasibleRefactorings.keySet());

		// Initialize result with all feasible refactorings
		result.cache.putAll(feasibleRefactorings);

		for (int i = 0; i < offsetPairs.size(); i++) {
			ExtractionTextRange p = offsetPairs.get(i);
			CodeExtractionMetrics metricsP = cache.get(p);

			for (int j = 0; j < offsetPairs.size(); j++) {
				if (i == j)
					continue;

				ExtractionTextRange q = offsetPairs.get(j);
				CodeExtractionMetrics metricsQ = cache.get(q);

				// If both refactorings yield the same reduction, remove the one that
				// contains/is contained by the other
				// This logic seems to prioritize keeping the *smaller* or *larger* scope
				// depending on loop order,
				// effectively deduping identical benefits.
				if (metricsP.getReductionOfCognitiveComplexity() == metricsQ.getReductionOfCognitiveComplexity()) {
					if (ExtractionTextRange.isContained(q, p)) {
						result.cache.remove(p);
					} else if (ExtractionTextRange.isContained(p, q)) {
						result.cache.remove(q);
					}
				}
			}
		}

		return result;
	}

	public CompilationUnit getCompilationUnit() {
		return this.compilationUnit;
	}

	public MethodDeclaration getMethodDeclaration() {
		return this.methodDeclaration;
	}

	// Helper to create vertex to avoid code duplication
	private ExtractionVertex createVertex(ExtractionTextRange p, CodeExtractionMetrics metrics) {
		return new ExtractionVertex(p.getStart(), p.getEnd(), metrics.getReductionOfCognitiveComplexity(),
				metrics.getAccumulatedInherentComponent(), metrics.getAccumulatedNestingComponent(),
				metrics.getNumberNestingContributors(), metrics.getNesting());
	}
	
	/**
	 * Locates the MethodDeclaration by finding the earliest cached extraction
	 * and traversing up the AST.
	 */
	public MethodDeclaration findEnclosingMethodDeclaration() {
	    if (cache == null || cache.isEmpty()) {
	        return null;
	    }

	    // 1. Find the key (ExtractionTextRange) with the minimum start position
	    ExtractionTextRange firstRange = cache.keySet().stream()
	            .min((r1, r2) -> Integer.compare(r1.getStart(), r2.getStart()))
	            .orElse(null);

	    if (firstRange == null) {
	        return null;
	    }

	    // 2. Find the ASTNode at that exact position
	    NodeFinder finder = new NodeFinder(compilationUnit, firstRange.getStart(), 0);
	    ASTNode node = finder.getCoveringNode();

	    // 3. Traverse up the AST to find the enclosing MethodDeclaration
	    return neo.reducecognitivecomplexity.core.jdt.Utils.getMethodDeclaration(node);
	}
}