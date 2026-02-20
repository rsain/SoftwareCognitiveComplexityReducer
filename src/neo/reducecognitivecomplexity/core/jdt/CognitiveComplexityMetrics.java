package neo.reducecognitivecomplexity.core.jdt;

/**
 * An immutable value object holding the detailed breakdown of Cognitive
 * Complexity for a specific block of code. *
 * <p>
 * Cognitive Complexity is composed of two main parts:
 * </p>
 * <ul>
 * <li><b>Inherent:</b> The base cost of a structure (e.g., loops,
 * conditionals).</li>
 * <li><b>Nesting:</b> The penalty added for the depth of that structure.</li>
 * </ul>
 */
public class CognitiveComplexityMetrics {

	// =========================================================================
	// FIELDS
	// =========================================================================

	/** The sum of the base costs (without nesting penalties) of the code block. */
	private final int accumulatedInherentCognitiveComplexity;

	/** The sum of the nesting penalties of the code block. */
	private final int accumulatedNestingCognitiveComplexity;

	/**
	 * * The total complexity this code would have if it were the body of a new
	 * method. (Typically Inherent + Nesting, where Nesting is calculated starting
	 * from level 0).
	 */
	private final int cognitiveComplexityWhenExtractedAsNewMethod;

	/**
	 * The amount of complexity removed from the original method by extracting this
	 * block.
	 */
	private final int cognitiveComplexityReduction;

	// Metadata
	private final int numberOfNestingContributors;
	private final int nestingLevel;

	// =========================================================================
	// CONSTRUCTOR
	// =========================================================================

	public CognitiveComplexityMetrics(int accumulatedInherentCognitiveComplexity,
			int accumulatedNestingCognitiveComplexity, int cognitiveComplexityWhenExtractedAsNewMethod,
			int cognitiveComplexityReduction, int numberOfNestingContributors, int nestingLevel) {

		this.accumulatedInherentCognitiveComplexity = accumulatedInherentCognitiveComplexity;
		this.accumulatedNestingCognitiveComplexity = accumulatedNestingCognitiveComplexity;
		this.cognitiveComplexityWhenExtractedAsNewMethod = cognitiveComplexityWhenExtractedAsNewMethod;
		this.cognitiveComplexityReduction = cognitiveComplexityReduction;
		this.numberOfNestingContributors = numberOfNestingContributors;
		this.nestingLevel = nestingLevel;
	}

	// =========================================================================
	// GETTERS
	// =========================================================================

	public int getAccumulatedInherentCognitiveComplexity() {
		return accumulatedInherentCognitiveComplexity;
	}

	public int getAccumulatedNestingCognitiveComplexity() {
		return accumulatedNestingCognitiveComplexity;
	}

	public int getCognitiveComplexityWhenExtractedAsNewMethod() {
		return cognitiveComplexityWhenExtractedAsNewMethod;
	}

	public int getCognitiveComplexityReduction() {
		return cognitiveComplexityReduction;
	}

	public int getNumberOfNestingContributors() {
		return numberOfNestingContributors;
	}

	public int getNestingLevel() {
		return nestingLevel;
	}

	// =========================================================================
	// TOSTRING
	// =========================================================================

	@Override
	public String toString() {
		return "ComplexityMetrics [" + "Extracted=" + cognitiveComplexityWhenExtractedAsNewMethod + ", Reduction="
				+ cognitiveComplexityReduction + ", Inherent=" + accumulatedInherentCognitiveComplexity + ", Nesting="
				+ accumulatedNestingCognitiveComplexity + ", Level=" + nestingLevel + "]";
	}
}