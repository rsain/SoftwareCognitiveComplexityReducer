package neo.reducecognitivecomplexity.core;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.jdt.CodeExtractionMetrics;
import neo.reducecognitivecomplexity.core.jdt.CodeExtractionMetricsStats;
import neo.reducecognitivecomplexity.core.jdt.Utils;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCache;

/**
 * A Solution represents a candidate refactoring plan for a method.
 * <p>
 * It consists of a list of {@link Sequence}s, each representing a block of code 
 * to be extracted into a separate method. The class manages the evaluation (fitness),
 * feasibility, and application of these refactorings.
 * </p>
 */
public class Solution {
  
    /**
     * List of code sequences to extract. 
     * Ordered by their position in the source code (top to bottom).
     */
    private List<Sequence> sequenceList;
    
    /**
     * The CompilationUnit this Solution belongs to.
     */
    private CompilationUnit compilationUnit;

    /**
     * The {@link MethodDeclaration} node of the method being refactored.
     */
    private ASTNode method;

    /**
     * Name of the method being refactored.
     */
    private String methodName;

    /**
     * True if all code extractions in the list are valid and applicable.
     */
    private boolean feasible = false;

    /**
     * Quality of the Solution (lower is better, but code initializes to Double.MAX_VALUE).
     */
    private double fitness;

    /**
     * Initial cognitive complexity of the method before refactoring.
     */
    private int initialComplexity;
    
    /**
     * Total reduction in cognitive complexity if the Solution is applied.
     */
    private int reducedComplexity;

    /**
     * Aggregated metrics of the code extractions associated with this Solution.
     */
    private CodeExtractionMetricsStats extractionMetricsStats = null;

    /**
     * Create a Solution from a given list of Sequences.
     *
     * @param sequenceList          The list of sequences to extract.
     * @param compilationUnit       The CompilationUnit containing the code.
     * @param methodDeclarationNode The MethodDeclaration node.
     */
    public Solution(List<Sequence> sequenceList, CompilationUnit compilationUnit, ASTNode methodDeclarationNode) {
        this.sequenceList = sequenceList;
        this.sortSequencesForRefactoringApplication();
        this.compilationUnit = compilationUnit;
        this.method = methodDeclarationNode;
        this.methodName = ((MethodDeclaration) this.method).getName().toString();
        this.initialComplexity = Utils.getIntegerPropertyOfNode(method, Constants.ACCUMULATED_CONTRIBUTION_TO_COGNITIVE_COMPLEXITY);
        this.fitness = Double.MAX_VALUE;
    }

    /**
     * Create an empty Solution for a given CompilationUnit.
     *
     * @param compilationUnit       The CompilationUnit.
     * @param methodDeclarationNode The MethodDeclaration node.
     */
    public Solution(CompilationUnit compilationUnit, ASTNode methodDeclarationNode) {
        this(new ArrayList<>(), compilationUnit, methodDeclarationNode);
    }
    
    /**
     * Import a Solution from a string representation.
     *
     * @param compilationUnit           The CompilationUnit.
     * @param solutionString            String representation of offsets (e.g., "[[8520], [10105 10147]]").
     * @param methodCognitiveComplexity The known complexity of the method.
     */
    public Solution(CompilationUnit compilationUnit, String solutionString, int methodCognitiveComplexity) {
        this.sequenceList = new ArrayList<>();
        this.compilationUnit = compilationUnit;

        Pattern p = Pattern.compile("\\[([0-9,\\s]+)\\]");
        Matcher m = p.matcher(solutionString);

        while (m.find()) {
            String value = m.group(1); 
            String[] offsets = value.split("[,\\s]+");

            List<ASTNode> nodes = new ArrayList<>();
            for (String offsetStr : offsets) {
                if (offsetStr.isEmpty()) continue;
                int offset = Integer.parseInt(offsetStr.trim());
                NodeFinder finder = new NodeFinder(compilationUnit, offset, 0);
                ASTNode node = finder.getCoveringNode();
                if (node != null) {
                    nodes.add(node);
                }
            }
            if (!nodes.isEmpty()) {
                this.sequenceList.add(new Sequence(compilationUnit, nodes));
            }
        }

        if (!sequenceList.isEmpty()) {
            this.method = sequenceList.get(0).getMethodDeclaration();
            this.methodName = ((MethodDeclaration) this.method).getName().toString();
        }
        this.initialComplexity = methodCognitiveComplexity;
        this.fitness = Double.MAX_VALUE;
        this.sortSequencesForRefactoringApplication();
    }

    /**
     * Copy Constructor.
     *
     * @param other The solution to copy.
     */
    public Solution(Solution other) {
        this.compilationUnit = other.compilationUnit;
        this.method = other.method;
        this.methodName = ((MethodDeclaration) other.method).getName().toString();
        this.sequenceList = new ArrayList<>();
        for (Sequence s : other.sequenceList) {
            this.sequenceList.add(s.copy());
        }
        this.fitness = other.fitness;
        this.feasible = other.feasible;
        this.initialComplexity = other.initialComplexity;
        this.reducedComplexity = other.reducedComplexity;
        this.extractionMetricsStats = other.extractionMetricsStats;
    }
    
    /**
     * Compute the aggregated metrics of the solution.
     * Orchestrates evaluation, hierarchy resolution, metrics adjustment, and fitness penalty.
     */
    public CodeExtractionMetrics evaluate(RefactoringCache rf) {
        List<CodeExtractionMetrics> metricsList = evaluateAllSequences(rf);

        // Fast-fail if any sequence is not feasible
        Optional<CodeExtractionMetrics> unfeasibleMetrics = metricsList.stream()
                .filter(m -> !m.isFeasible())
                .findFirst();

        if (unfeasibleMetrics.isPresent()) {
            resetInfeasibleState();
            return unfeasibleMetrics.get();
        }

        applyNestingAdjustments(metricsList);
        return aggregateAndCalculateFitness(metricsList);
    }

    // -------------------------------------------------------------------------
    // Helper Methods (Delegated Responsibilities)
    // -------------------------------------------------------------------------

    /**
     * Sorts the sequence list in strict execution order for applying the refactoring.
     * Children always precede parents and independent sequences are ordered bottom-to-top.
     */
    public void sortSequencesForRefactoringApplication() {
        if (this.sequenceList == null || this.sequenceList.isEmpty()) return;

        this.sequenceList.sort((seqA, seqB) -> {
            ExtractionTextRange rA = seqA.getTextRange();
            ExtractionTextRange rB = seqB.getTextRange();
            return ExtractionTextRange.compareExecutionOrder(rA, rB);
        });
    }
    
    private List<CodeExtractionMetrics> evaluateAllSequences(RefactoringCache rf) {
        return sequenceList.stream()
                .map(seq -> seq.evaluate(rf))
                .collect(Collectors.toList());
    }

    private void applyNestingAdjustments(List<CodeExtractionMetrics> metricsList) {
        // Snapshot raw reductions before modifying parents
        int[] rawReductions = metricsList.stream()
                .mapToInt(CodeExtractionMetrics::getReductionOfCognitiveComplexity)
                .toArray();

        for (int childIndex = 0; childIndex < sequenceList.size(); childIndex++) {
            int parentIndex = findDirectParentIndex(childIndex);
            if (parentIndex != -1) {
                Sequence parentSequence = sequenceList.get(parentIndex);

                // Only subtract the child's contribution when the PARENT's own
                // reduction was computed by SUMMING the full contribution of
                // every complexity node in its subtree, since
                // that is the only case where a nested child sequence's
                // contribution risks being counted twice (once for the child
                // itself, once again as part of the parent's own subtree sum).
                //
                // When the parent's reduction was instead computed with the
                // "nested" (shift-based) formula (because the parent's own
                // extraction site lives inside an anonymous/local class), it
                // is a uniform per-subtree value, NOT a sum of each
                // descendant's own contribution, so it never double-counts
                // anything to begin with.
                if (parentSequenceUsesFullContributionSum(parentSequence)) {
                    subtractChildMetricsFromParent(
                        metricsList.get(parentIndex), 
                        metricsList.get(childIndex), 
                        rawReductions[childIndex]
                    );
                }
            }
        }
    }

    /**
     * Whether {@code sequence}'s own {@code reductionOfCognitiveComplexity}
     * was computed by summing the full contribution of every complexity node
     * in its subtree (the "escaping" case), as opposed to the "nested"
     * (shift-based) case - see
     * {@link Utils#computeMetricsIfExtracted(ASTNode)} and
     * {@link Utils#isEscapingExtraction(ASTNode)}.
     */
    private boolean parentSequenceUsesFullContributionSum(Sequence sequence) {
        List<ASTNode> nodes = sequence.getSiblingNodes();
        if (nodes == null || nodes.isEmpty()) {
            return true; // Defensive default: preserve the previous (subtracting) behavior.
        }
        ASTNode root = Utils.getStatementOrParent(nodes.get(0));
        return Utils.isEscapingExtraction(root);
    }

    private int findDirectParentIndex(int childIndex) {
        ExtractionTextRange childRange = sequenceList.get(childIndex).getTextRange();

        return IntStream.range(0, sequenceList.size())
                .filter(j -> j != childIndex)
                .filter(j -> childRange.isContainedIn(sequenceList.get(j).getTextRange()))
                .reduce((bestParent, candidate) -> 
                    sequenceList.get(candidate).getTextRange().isContainedIn(sequenceList.get(bestParent).getTextRange()) 
                        ? candidate 
                        : bestParent
                )
                .orElse(-1);
    }

    private void subtractChildMetricsFromParent(CodeExtractionMetrics parent, CodeExtractionMetrics child, int childRawReduction) {
        parent.setReductionOfCognitiveComplexity(parent.getReductionOfCognitiveComplexity() - childRawReduction);
        parent.setAccumulatedInherentComponent(parent.getAccumulatedInherentComponent() - child.getAccumulatedInherentComponent());
        parent.setNumberNestingContributors(parent.getNumberNestingContributors() - child.getNumberNestingContributors());

        int levelDifference = child.getNesting() - parent.getNesting();
        int childContributionToParentNesting = child.getAccumulatedNestingComponent()
                + child.getNumberNestingContributors() * levelDifference;
        parent.setAccumulatedNestingComponent(parent.getAccumulatedNestingComponent() - childContributionToParentNesting);
    }

    private CodeExtractionMetrics aggregateAndCalculateFitness(List<CodeExtractionMetrics> metricsList) {
        CodeExtractionMetrics aggregated = new CodeExtractionMetrics(true, "", false, 0, 0, new ArrayList<>(), new ArrayList<>(), 0);
        
        this.reducedComplexity = 0;
        this.fitness = sequenceList.size();

        for (CodeExtractionMetrics metric : metricsList) {
            this.reducedComplexity += metric.getReductionOfCognitiveComplexity();
            aggregated.joinMetrics(metric);

            int complexity = metric.getCognitiveComplexityOfNewExtractedMethod();
            if (complexity > Constants.COGNITIVE_COMPLEXITY_THRESHOLD) {
                this.fitness += (complexity - Constants.COGNITIVE_COMPLEXITY_THRESHOLD) * 10;
            }
        }

        int finalMethodComplexity = this.initialComplexity - this.reducedComplexity;
        if (finalMethodComplexity > Constants.COGNITIVE_COMPLEXITY_THRESHOLD) {
            this.fitness += (finalMethodComplexity - Constants.COGNITIVE_COMPLEXITY_THRESHOLD) * 10;
        }

        this.extractionMetricsStats = new CodeExtractionMetricsStats(metricsList.toArray(new CodeExtractionMetrics[0]));
        this.feasible = aggregated.isFeasible();

        return aggregated;
    }

    private void resetInfeasibleState() {
        this.fitness = Double.MAX_VALUE;
        this.feasible = false;
        this.reducedComplexity = 0;
    }

    // Setters for execution lifecycle manipulation
    public void updateCompilationUnitState(CompilationUnit cu, ASTNode refreshedMethodNode) {
        this.compilationUnit = cu;
        this.method = refreshedMethodNode;
    }
    
    public CompilationUnit getCompilationUnit() { return compilationUnit; }
    
    public boolean isFeasible() {
        return feasible;
    }

    public void removeSequence(int i) {
        this.sequenceList.remove(i);
    }

    public List<Sequence> getSequenceList() {
        return this.sequenceList;
    }

    public Sequence getSequence(int i) {
        return this.sequenceList.get(i);
    }

    public int getSize() {
        return this.sequenceList.size();
    }

    public String getMethodName() {
        return this.methodName;
    }

    public MethodDeclaration getMethodDeclaration() {
        return (MethodDeclaration) method;
    }

    public boolean contains(ASTNode node) {
        return sequenceList.stream().anyMatch(s -> s.contains(node));
    }
    

    @Override
    public String toString() {
        return "Solution [methodName=" + methodName + ", sequenceList=" + sequenceList + ", isFeasible=" + feasible
                + ", fitness=" + fitness + ", initialComplexity=" + initialComplexity +", reducedComplexity=" + reducedComplexity + "]";
    }

    public String toStringVerbose() {
        StringBuilder result = new StringBuilder();

        result.append("Solution [methodName=").append(methodName)
              .append(", sequenceList=").append(sequenceList)
              .append(", isFeasible=").append(feasible)
              .append(", fitness=").append(fitness)
              .append(", initialComplexity=").append(initialComplexity)
              .append(", reducedComplexity=").append(reducedComplexity).append("]\n");

        if (this.compilationUnit != null && this.compilationUnit.getJavaElement() != null) {
             result.append("COMPILATION UNIT ").append(this.compilationUnit.getJavaElement().getPath()).append("\n");
        }
        result.append("Printing sequence list (AST nodes) [").append(sequenceList.size()).append(" code extraction(s)] ...\n");

        int count = 0;
        for (Sequence seq : sequenceList) {
            result.append("EXTRACTION ").append(count + 1).append("\n");
            result.append("Nodes starting position: ").append(seq.toString()).append("\n");
            result.append("Offset: ").append(seq.getTextRange()).append("\n");

            List<ASTNode> nodes = seq.getSiblingNodes();
            boolean nested = !nodes.isEmpty()
                    && !Utils.isEscapingExtraction(Utils.getStatementOrParent(nodes.get(0)));
            result.append("Nested in anonymous/local class: ").append(nested).append("\n");

            result.append("Code: ").append(seq.toSourceCodeString()).append("\n");
            count++;
        }

        return result.toString();
    }

    public String toStringForFileFormat() {
        return this.sequenceList.toString();
    }

    public double getFitness() {
        return fitness;
    }

    public int getReducedComplexity() {
        return reducedComplexity;
    }

    public int getInitialComplexity() {
        return initialComplexity;
    }
    
    public CodeExtractionMetricsStats getExtractionMetricsStats() {
        return extractionMetricsStats;
    }

    /**
     * True if AT LEAST ONE extraction in this solution has its code living
     * inside a method that is itself a member of an anonymous class, or
     * declared within a local class - see
     * {@code CodeExtractionMetrics#isNestedInAnonymousOrLocalClass()}. A flag
     * rather than a count: the exact number of affected extractions matters
     * less than knowing whether the reported reduction could be understated
     * for this specific case. Convenience delegate over
     * {@link #getExtractionMetricsStats()}.
     */
    public boolean hasExtractionsNestedInAnonymousOrLocalClass() {
        return extractionMetricsStats != null
                && extractionMetricsStats.hasExtractionsNestedInAnonymousOrLocalClass();
    }

    public void writeInFile(String absolutePathToFile) {
        try (PrintWriter pw = new PrintWriter(absolutePathToFile)) {
            pw.write(this.toStringVerbose());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Return a list of text ranges for the sequences in this solution.
     */
    public List<ExtractionTextRange> getRanges() {
        return sequenceList.stream()
                .map(Sequence::getTextRange)
                .collect(Collectors.toList());
    }
}