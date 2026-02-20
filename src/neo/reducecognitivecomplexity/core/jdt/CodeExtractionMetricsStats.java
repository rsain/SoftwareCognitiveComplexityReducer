package neo.reducecognitivecomplexity.core.jdt;

/**
 * Computes and holds statistical data (min, max, mean, total) for a collection
 * of {@link CodeExtractionMetrics}.
 * <p>
 * This class is immutable. If the input array is empty, all statistics default
 * to 0.
 * </p>
 */
public class CodeExtractionMetricsStats {

	// =========================================================================
	// METRICS: EXTRACTED LINES OF CODE
	// =========================================================================
	private final int minNumberOfExtractedLinesOfCode;
	private final int maxNumberOfExtractedLinesOfCode;
	private final double meanNumberOfExtractedLinesOfCode;
	private final int totalNumberOfExtractedLinesOfCode;

	// =========================================================================
	// METRICS: PARAMETERS
	// =========================================================================
	private final int minNumberOfParametersInExtractedMethods;
	private final int maxNumberOfParametersInExtractedMethods;
	private final double meanNumberOfParametersInExtractedMethods;
	private final int totalNumberOfParametersInExtractedMethods;

	// =========================================================================
	// METRICS: COGNITIVE COMPLEXITY REDUCTION
	// =========================================================================
	private final int minReductionOfCognitiveComplexity;
	private final int maxReductionOfCognitiveComplexity;
	private final double meanReductionOfCognitiveComplexity;
	private final int totalReductionOfCognitiveComplexity;

	/**
	 * Computes statistics from the provided array of metrics. * @param
	 * extractionMetrics the array of metrics to analyze.
	 */
	public CodeExtractionMetricsStats(CodeExtractionMetrics[] extractionMetrics) {

		// 1. Handle edge case: Empty or Null input
		if (extractionMetrics == null || extractionMetrics.length == 0) {
			this.minNumberOfExtractedLinesOfCode = 0;
			this.maxNumberOfExtractedLinesOfCode = 0;
			this.meanNumberOfExtractedLinesOfCode = 0.0;
			this.totalNumberOfExtractedLinesOfCode = 0;

			this.minNumberOfParametersInExtractedMethods = 0;
			this.maxNumberOfParametersInExtractedMethods = 0;
			this.meanNumberOfParametersInExtractedMethods = 0.0;
			this.totalNumberOfParametersInExtractedMethods = 0;

			this.minReductionOfCognitiveComplexity = 0;
			this.maxReductionOfCognitiveComplexity = 0;
			this.meanReductionOfCognitiveComplexity = 0.0;
			this.totalReductionOfCognitiveComplexity = 0;
			return;
		}

		// 2. Initialize accumulators
		int localMinLines = Integer.MAX_VALUE;
		int localMaxLines = Integer.MIN_VALUE;
		int localTotalLines = 0;

		int localMinParams = Integer.MAX_VALUE;
		int localMaxParams = Integer.MIN_VALUE;
		int localTotalParams = 0;

		int localMinReduction = Integer.MAX_VALUE;
		int localMaxReduction = Integer.MIN_VALUE;
		int localTotalReduction = 0;

		// 3. Single pass loop for performance
		for (CodeExtractionMetrics m : extractionMetrics) {
			// Lines
			int lines = m.getNumberOfExtractedLinesOfCode();
			if (lines < localMinLines)
				localMinLines = lines;
			if (lines > localMaxLines)
				localMaxLines = lines;
			localTotalLines += lines;

			// Parameters
			int params = m.getNumberOfParametersInExtractedMethod();
			if (params < localMinParams)
				localMinParams = params;
			if (params > localMaxParams)
				localMaxParams = params;
			localTotalParams += params;

			// Complexity Reduction
			int reduction = m.getReductionOfCognitiveComplexity();
			if (reduction < localMinReduction)
				localMinReduction = reduction;
			if (reduction > localMaxReduction)
				localMaxReduction = reduction;
			localTotalReduction += reduction;
		}

		// 4. Final assignment
		this.minNumberOfExtractedLinesOfCode = localMinLines;
		this.maxNumberOfExtractedLinesOfCode = localMaxLines;
		this.totalNumberOfExtractedLinesOfCode = localTotalLines;
		this.meanNumberOfExtractedLinesOfCode = (double) localTotalLines / extractionMetrics.length;

		this.minNumberOfParametersInExtractedMethods = localMinParams;
		this.maxNumberOfParametersInExtractedMethods = localMaxParams;
		this.totalNumberOfParametersInExtractedMethods = localTotalParams;
		this.meanNumberOfParametersInExtractedMethods = (double) localTotalParams / extractionMetrics.length;

		this.minReductionOfCognitiveComplexity = localMinReduction;
		this.maxReductionOfCognitiveComplexity = localMaxReduction;
		this.totalReductionOfCognitiveComplexity = localTotalReduction;
		this.meanReductionOfCognitiveComplexity = (double) localTotalReduction / extractionMetrics.length;
	}

	// =========================================================================
	// GETTERS
	// =========================================================================

	public int getMinNumberOfExtractedLinesOfCode() {
		return minNumberOfExtractedLinesOfCode;
	}

	public int getMaxNumberOfExtractedLinesOfCode() {
		return maxNumberOfExtractedLinesOfCode;
	}

	public int getTotalNumberOfExtractedLinesOfCode() {
		return totalNumberOfExtractedLinesOfCode;
	}

	public double getMeanNumberOfExtractedLinesOfCode() {
		return meanNumberOfExtractedLinesOfCode;
	}

	public int getMinNumberOfParametersInExtractedMethods() {
		return minNumberOfParametersInExtractedMethods;
	}

	public int getMaxNumberOfParametersInExtractedMethods() {
		return maxNumberOfParametersInExtractedMethods;
	}

	public int getTotalNumberOfParametersInExtractedMethods() {
		return totalNumberOfParametersInExtractedMethods;
	}

	public double getMeanNumberOfParametersInExtractedMethods() {
		return meanNumberOfParametersInExtractedMethods;
	}

	public int getMinReductionOfCognitiveComplexity() {
		return minReductionOfCognitiveComplexity;
	}

	public int getMaxReductionOfCognitiveComplexity() {
		return maxReductionOfCognitiveComplexity;
	}

	public int getTotalReductionOfCognitiveComplexity() {
		return totalReductionOfCognitiveComplexity;
	}

	public double getMeanReductionOfCognitiveComplexity() {
		return meanReductionOfCognitiveComplexity;
	}
}