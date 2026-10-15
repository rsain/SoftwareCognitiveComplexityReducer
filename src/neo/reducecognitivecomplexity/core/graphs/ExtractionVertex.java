package neo.reducecognitivecomplexity.core.graphs;

import java.util.Objects;

/**
 * Represents a node in the refactoring graph.
 * <p>
 * Each vertex corresponds to a specific block of code (defined by start/end offsets)
 * that is a candidate for extraction into a separate method. It holds the cognitive
 * complexity metrics that would result if this specific block were extracted.
 * </p>
 */
public class ExtractionVertex implements Comparable<ExtractionVertex> {

	private final int initialOffset;
	private final int endOffset;
	
	// Complexity metrics
	private final int reductionOfCognitiveComplexity; 
	private final int accumulatedInherentComponent; 
	private final int accumulatedNestingComponent; 
	private final int numberNestingContributors; 
	private final int nesting; // Nesting level (0-based)

	/**
	 * True if the code this vertex represents lives inside a method that is
	 * itself a member of an anonymous class, or declared within a local class
	 * (a class declared inside a method/block body). See
	 * {@link neo.reducecognitivecomplexity.core.jdt.Utils#isEscapingExtraction(ASTNode)}
	 * and the detailed explanation in the javadoc of
	 * {@link neo.reducecognitivecomplexity.core.jdt.Utils#computeMetricsIfExtracted(ASTNode)}: in that case, JDT
	 * places the newly extracted method as a SIBLING within that same
	 * anonymous/local class rather than as an independent member of the
	 * enclosing (true) class, so {@code reductionOfCognitiveComplexity} above
	 * only reflects the LOCAL nesting reduction, not a full removal.
	 */
	private final boolean nestedInAnonymousOrLocalClass;

	/**
	 * True if this vertex's own root node is the {@code elseStatement} of an
	 * external {@code IfStatement} not part of this same extraction.
	 */
	private final boolean chainingContinuationOfExternalParent;

	// Temporal values used during solver execution
	private Integer temporalAccumulatedInherentComponent;
	private Integer temporalAccumulatedNestingComponent;
	private Integer temporalNumberNestingContributors;

	public ExtractionVertex(int initialOffset, int endOffset, int reductionOfCognitiveComplexity, 
			int accumulatedInherentComponent, int accumulatedNestingComponent, 
			int numberNestingContributors, int nesting, boolean nestedInAnonymousOrLocalClass,
			boolean chainingContinuationOfExternalParent) {
		this.initialOffset = initialOffset;
		this.endOffset = endOffset;
		this.reductionOfCognitiveComplexity = reductionOfCognitiveComplexity;
		this.accumulatedInherentComponent = accumulatedInherentComponent;
		this.accumulatedNestingComponent = accumulatedNestingComponent;
		this.numberNestingContributors = numberNestingContributors;
		this.nesting = nesting;
		this.nestedInAnonymousOrLocalClass = nestedInAnonymousOrLocalClass;
		this.chainingContinuationOfExternalParent = chainingContinuationOfExternalParent;
	}

	// =========================================================================
	// GETTERS
	// =========================================================================

	public int getInitialOffset() {
		return initialOffset;
	}

	public int getEndOffset() {
		return endOffset;
	}

	public int getReductionOfCognitiveComplexity() {
		return this.reductionOfCognitiveComplexity;
	}

	public int getNesting() {
		return nesting;
	}

	public boolean isNestedInAnonymousOrLocalClass() {
		return nestedInAnonymousOrLocalClass;
	}

	public boolean isChainingContinuationOfExternalParent() {
		return chainingContinuationOfExternalParent;
	}

	/**
	 * Returns the total complexity cost of this vertex if it is extracted.
	 */
	public int getComplexityWhenExtracted() {
		return getAccumulatedInherentComponent() + getAccumulatedNestingComponent();
	}

	public int getAccumulatedInherentComponent() {
		if (temporalAccumulatedInherentComponent != null) {
			return temporalAccumulatedInherentComponent;
		}
		return accumulatedInherentComponent;
	}

	public int getAccumulatedNestingComponent() {
		if (temporalAccumulatedNestingComponent != null) {
			return temporalAccumulatedNestingComponent;
		}
		return accumulatedNestingComponent;
	}

	public int getNumberNestingContributors() {
		if (temporalNumberNestingContributors != null) {
			return temporalNumberNestingContributors;
		}
		return numberNestingContributors;
	}

	// =========================================================================
	// TEMPORAL STATE MANAGEMENT
	// =========================================================================

	public void setTemporalAccumulatedInherentComponent(Integer val) {
		this.temporalAccumulatedInherentComponent = val;
	}

	public void setTemporalAccumulatedNestingComponent(Integer val) {
		this.temporalAccumulatedNestingComponent = val;
	}

	public void setTemporalNumberNestingContributors(Integer val) {
		this.temporalNumberNestingContributors = val;
	}

	public void removeTemporalInformation() {
		this.temporalAccumulatedInherentComponent = null;
		this.temporalAccumulatedNestingComponent = null;
		this.temporalNumberNestingContributors = null;
	}

	// =========================================================================
	// LOGIC & COMPARISON
	// =========================================================================

	/**
	 * Checks if the given vertex {@code v} is structurally contained within this vertex.
	 * <p>
	 * This is true if {@code v} starts after (or at) this start, and ends before (or at) this end.
	 * </p>
	 */
	public boolean contains(ExtractionVertex v) {
		return (v.initialOffset >= this.initialOffset) && (v.endOffset <= this.endOffset);
	}

	@Override
	public int compareTo(ExtractionVertex other) {
		// 1. Compare by Start Offset (ascending)
		int startComparison = Integer.compare(this.initialOffset, other.initialOffset);
		if (startComparison != 0) {
			return startComparison;
		}
		// 2. Compare by End Offset (ascending)
		return Integer.compare(this.endOffset, other.endOffset);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		
		ExtractionVertex other = (ExtractionVertex) obj;
		return this.initialOffset == other.initialOffset && 
			   this.endOffset == other.endOffset;
	}

	@Override
	public int hashCode() {
		return Objects.hash(initialOffset, endOffset);
	}

	@Override
	public String toString() {
		return String.format("[%d, %d] (%d, %d, %d, %d, %d, nested=%b, chainCont=%b)",
				initialOffset, endOffset, 
				reductionOfCognitiveComplexity,
				accumulatedInherentComponent,
				accumulatedNestingComponent,
				numberNestingContributors,
				nesting,
				nestedInAnonymousOrLocalClass,
				chainingContinuationOfExternalParent);
	}
}