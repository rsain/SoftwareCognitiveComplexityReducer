package neo.reducecognitivecomplexity.core;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.jdt.CodeExtractionMetrics;
import neo.reducecognitivecomplexity.core.jdt.CognitiveComplexityMetrics;
import neo.reducecognitivecomplexity.core.jdt.Utils;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCache;

/**
 * Represents a contiguous list of sibling AST nodes (typically Statements) 
 * that form a candidate for extraction into a separate method.
 */
public class Sequence {

    /**
     * The CompilationUnit this sequence belongs to.
     */
    private final CompilationUnit compilationUnit;

    /**
     * The list of sibling nodes (e.g., statements in a block) included in this sequence.
     */
    private List<ASTNode> siblingNodes;

    /**
     * Creates an empty Sequence for a given compilation unit.
     * * @param compilationUnit The owner CU.
     */
    public Sequence(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
        this.siblingNodes = new ArrayList<>();
    }

    /**
     * Creates a Sequence from a specific list of sibling nodes.
     * * @param compilationUnit The owner CU.
     * @param siblingNodes    The nodes to include.
     */
    public Sequence(CompilationUnit compilationUnit, List<ASTNode> siblingNodes) {
        this.compilationUnit = compilationUnit;
        this.siblingNodes = new ArrayList<>(siblingNodes);
    }

    /**
     * Creates a Sequence defined by a text range.
     * <p>
     * Uses the JDT NodeFinder to locate the nodes covered by the given range.
     * </p>
     * * @param compilationUnit The owner CU.
     * @param range           The start/end offsets defining the sequence.
     */
    public Sequence(CompilationUnit compilationUnit, ExtractionTextRange range) {
        this.compilationUnit = compilationUnit;
        // Use the NodeFinder visitor to resolve the AST nodes within the range
        this.siblingNodes = new Utils.NodeFinderVisitorForGivenSelection(
                compilationUnit.getRoot(),
                range.getStart(),
                range.getEnd() - range.getStart()
        ).getNodes();
    }

    // -------------------------------------------------------------------------
    // Metrics & Evaluation
    // -------------------------------------------------------------------------

    /**
     * Simulates the extraction of this sequence to compute exact metrics.
     * <p>
     * <b>Note:</b> This operation is computationally expensive as it may involve
     * checking preconditions and simulating AST rewrites. Prefer {@link #evaluate(RefactoringCache)}
     * if a cache is available.
     * </p>
     *
     * @return The metrics describing the result of extracting this sequence, 
     * or {@code null} if the sequence is empty.
     */
    public CodeExtractionMetrics evaluate() {
        if (siblingNodes.isEmpty()) {
            return null; // Cannot compute metrics for an empty sequence
        }

        ASTNode startNode = Utils.getStatementOrParent(siblingNodes.get(0));
        ASTNode endNode = Utils.getStatementOrParent(siblingNodes.get(siblingNodes.size() - 1));

        // 1. Base extraction check (Eclipse JDT / Preconditions)
        // This Utils method instantiates the CodeExtractionMetrics object for us
        CodeExtractionMetrics result = Utils.checkCodeExtractionBetweenTwoNodes(compilationUnit, startNode, endNode);

        // 2. Compute Cognitive Complexity reductions manually
        int totalReduction = 0;
        int accInherent = 0;
        int accNesting = 0;
        int nestingContributors = 0;
        int maxNestingLevel = 0;

        for (ASTNode node : this.siblingNodes) {
            CognitiveComplexityMetrics metrics = Utils.computeMetricsIfExtracted(node);
            
            totalReduction += metrics.getCognitiveComplexityReduction();
            accInherent += metrics.getAccumulatedInherentCognitiveComplexity();
            accNesting += metrics.getAccumulatedNestingCognitiveComplexity();
            nestingContributors += metrics.getNumberOfNestingContributors();
            
            if (metrics.getNestingLevel() > maxNestingLevel) {
                maxNestingLevel = metrics.getNestingLevel();
            }
        }

        // Update the result object with the accumulated values
        result.setReductionOfCognitiveComplexity(result.getReductionOfCognitiveComplexity() + totalReduction);
        result.setAccumulatedInherentComponent(result.getAccumulatedInherentComponent() + accInherent);
        result.setAccumulatedNestingComponent(result.getAccumulatedNestingComponent() + accNesting);
        result.setNumberNestingContributors(result.getNumberNestingContributors() + nestingContributors);
        result.setNesting(maxNestingLevel);

        return result;
    }

    /**
     * Retrieves metrics for this sequence from the provided {@link RefactoringCache}.
     * <p>
     * This is the preferred method for performance during solver execution.
     * </p>
     * * @param cache The cache containing pre-computed metrics.
     * @return The cached metrics.
     */
    public CodeExtractionMetrics evaluate(RefactoringCache cache) {
        return cache.getMetrics(this);
    }

    /**
     * Performs the actual extraction of this sequence into a new method.
     * * @param unit                The compilation unit.
     * @param extractedMethodName The name for the new method.
     * @return The result metrics of the applied refactoring.
     */
    public CodeExtractionMetrics extract(CompilationUnit unit, String extractedMethodName) {
        if (siblingNodes.isEmpty()) {
            return null;
        }

        ASTNode startNode = Utils.getStatementOrParent(siblingNodes.get(0));
        ASTNode endNode = Utils.getStatementOrParent(siblingNodes.get(siblingNodes.size() - 1));

        return Utils.extractCodeBetweenTwoNodes(unit, startNode, endNode, extractedMethodName, false);
    }

    // -------------------------------------------------------------------------
    // Property Accessors
    // -------------------------------------------------------------------------

    /**
     * Calculates the text range (Start Offset, End Offset) covered by this sequence.
     * * @return The {@link ExtractionTextRange} or null if the sequence is empty.
     */
    public ExtractionTextRange getTextRange() {
        if (siblingNodes.isEmpty()) {
            return null;
        }

        ASTNode first = siblingNodes.get(0);
        ASTNode last = siblingNodes.get(siblingNodes.size() - 1);

        // Ensure we cover the full statement of the last node
        ASTNode endNodeStatement = Utils.getStatementOrParent(last);
        
        int start = first.getStartPosition();
        int end = endNodeStatement.getStartPosition() + endNodeStatement.getLength();

        return new ExtractionTextRange(start, end);
    }

    public List<ASTNode> getSiblingNodes() {
        return siblingNodes;
    }

    public void setSiblingNodes(List<ASTNode> siblingNodes) {
        this.siblingNodes = siblingNodes;
    }

    /**
     * Gets the MethodDeclaration that contains this sequence.
     */
    public MethodDeclaration getMethodDeclaration() {
        if (siblingNodes.isEmpty()) {
            return null;
        }
        ASTNode parent = Utils.getMethodDeclaration(siblingNodes.get(0));
        return (parent instanceof MethodDeclaration) ? (MethodDeclaration) parent : null;
    }

    public boolean contains(ASTNode node) {
        return siblingNodes.contains(node);
    }

    // -------------------------------------------------------------------------
    // Complexity Metrics Helpers
    // -------------------------------------------------------------------------

    public int getNesting() {
        if (siblingNodes.isEmpty()) {
            return -1;
        }
        return Utils.computeNesting(null, siblingNodes.get(0));
    }

    public int getAccumulatedCognitiveComplexity() {
        return siblingNodes.stream()
                .mapToInt(node -> Utils.getIntegerPropertyOfNode(node, Constants.ACCUMULATED_CONTRIBUTION_TO_COGNITIVE_COMPLEXITY))
                .sum();
    }

    public int getComplexityWhenExtracted() {
        return getAccumulatedInherentComponent() + getAccumulatedNestingComponent();
    }

    public int getAccumulatedInherentComponent() {
        return siblingNodes.stream()
                .mapToInt(node -> Utils.getIntegerPropertyOfNode(node, Constants.ACCUMULATED_INHERENT_COGNITIVE_COMPLEXITY))
                .sum();
    }

    public int getAccumulatedNestingComponent() {
        return siblingNodes.stream()
                .mapToInt(node -> Utils.getIntegerPropertyOfNode(node, Constants.ACCUMULATED_NESTING_COGNITIVE_COMPLEXITY))
                .sum();
    }

    public int getNumberNestingContributors() {
        return siblingNodes.stream()
                .mapToInt(node -> Utils.getIntegerPropertyOfNode(node, Constants.NUMBER_OF_NESTING_CONTRIBUTORS))
                .sum();
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Creates a deep copy of the sequence structure (not the AST nodes themselves).
     */
    public Sequence copy() {
        return new Sequence(this.compilationUnit, this.siblingNodes);
    }

    @Override
    public String toString() {
        if (siblingNodes.isEmpty()) {
            return "[]";
        }
        String positions = siblingNodes.stream()
                .map(node -> String.valueOf(node.getStartPosition()))
                .collect(Collectors.joining(", "));
        return "[" + positions + "]";
    }
    
    /**
     * Returns a string representation containing the actual source code of the 
     * nodes in this sequence.
     * <p>
     * This is useful for debugging or logging to see exactly what code statements 
     * are contained in the sequence.
     * </p>
     * * @return A string formatted as {@code "[sourceCodeNode1,sourceCodeNode2,...]"}
     */
    public String toSourceCodeString() {
        if (siblingNodes.isEmpty()) {
            return "[]";
        }

        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < siblingNodes.size() - 1; i++) {
            result.append(siblingNodes.get(i)).append(",");
        }
        result.append(siblingNodes.get(siblingNodes.size() - 1)).append("]");

        return result.toString();
    }
}