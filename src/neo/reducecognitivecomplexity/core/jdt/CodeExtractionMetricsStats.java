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
	/**
	 * The per-extraction values that fed into the LOC statistics above, in the
	 * same order as the input array, one entry per extraction. Exposed (via
	 * {@link #getExtractedLinesOfCodeList()}) as a pipe-separated string so
	 * that, e.g., a CSV column can carry every individual extraction's LOC
	 * value rather than only the aggregated min/max/mean/total.
	 */
	private final int[] extractedLinesOfCodeValues;

	// =========================================================================
	// METRICS: PARAMETERS
	// =========================================================================
	private final int minNumberOfParametersInExtractedMethods;
	private final int maxNumberOfParametersInExtractedMethods;
	private final double meanNumberOfParametersInExtractedMethods;
	private final int totalNumberOfParametersInExtractedMethods;
	/**
	 * The per-extraction values that fed into the parameter-count statistics
	 * above, in the same order as the input array, one entry per extraction.
	 * Exposed (via {@link #getParametersInExtractedMethodsList()}) as a
	 * pipe-separated string.
	 */
	private final int[] parametersInExtractedMethodsValues;

	// =========================================================================
	// METRICS: COGNITIVE COMPLEXITY REDUCTION
	// =========================================================================
	private final int minReductionOfCognitiveComplexity;
	private final int maxReductionOfCognitiveComplexity;
	private final double meanReductionOfCognitiveComplexity;
	private final int totalReductionOfCognitiveComplexity;
	/**
	 * The per-extraction values that fed into the CC-reduction statistics
	 * above, in the same order as the input array, one entry per extraction.
	 * Exposed (via {@link #getReductionOfCognitiveComplexityList()}) as a
	 * pipe-separated string.
	 */
	private final int[] reductionOfCognitiveComplexityValues;

	// =========================================================================
	// METRICS: NESTED-IN-ANONYMOUS/LOCAL-CLASS EXTRACTIONS
	// =========================================================================
	/**
	 * True if AT LEAST ONE extraction in this collection has its code living
	 * inside a method that is itself a member of an anonymous class, or
	 * declared within a local class.
	 */
	private final boolean hasExtractionsNestedInAnonymousOrLocalClass;

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
			this.extractedLinesOfCodeValues = new int[0];

			this.minNumberOfParametersInExtractedMethods = 0;
			this.maxNumberOfParametersInExtractedMethods = 0;
			this.meanNumberOfParametersInExtractedMethods = 0.0;
			this.totalNumberOfParametersInExtractedMethods = 0;
			this.parametersInExtractedMethodsValues = new int[0];

			this.minReductionOfCognitiveComplexity = 0;
			this.maxReductionOfCognitiveComplexity = 0;
			this.meanReductionOfCognitiveComplexity = 0.0;
			this.totalReductionOfCognitiveComplexity = 0;
			this.reductionOfCognitiveComplexityValues = new int[0];

			this.hasExtractionsNestedInAnonymousOrLocalClass = false;
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

		boolean localHasNested = false;

		int[] localLinesValues = new int[extractionMetrics.length];
		int[] localParamsValues = new int[extractionMetrics.length];
		int[] localReductionValues = new int[extractionMetrics.length];

		// 3. Single pass loop for performance
		for (int i = 0; i < extractionMetrics.length; i++) {
			CodeExtractionMetrics m = extractionMetrics[i];

			// Lines
			int lines = m.getNumberOfExtractedLinesOfCode();
			if (lines < localMinLines)
				localMinLines = lines;
			if (lines > localMaxLines)
				localMaxLines = lines;
			localTotalLines += lines;
			localLinesValues[i] = lines;

			// Parameters
			int params = m.getNumberOfParametersInExtractedMethod();
			if (params < localMinParams)
				localMinParams = params;
			if (params > localMaxParams)
				localMaxParams = params;
			localTotalParams += params;
			localParamsValues[i] = params;

			// Complexity Reduction
			int reduction = m.getReductionOfCognitiveComplexity();
			if (reduction < localMinReduction)
				localMinReduction = reduction;
			if (reduction > localMaxReduction)
				localMaxReduction = reduction;
			localTotalReduction += reduction;
			localReductionValues[i] = reduction;

			// Nested in anonymous/local class
			if (m.isNestedInAnonymousOrLocalClass()) {
				localHasNested = true;
			}
		}

		// 4. Final assignment
		this.minNumberOfExtractedLinesOfCode = localMinLines;
		this.maxNumberOfExtractedLinesOfCode = localMaxLines;
		this.totalNumberOfExtractedLinesOfCode = localTotalLines;
		this.meanNumberOfExtractedLinesOfCode = (double) localTotalLines / extractionMetrics.length;
		this.extractedLinesOfCodeValues = localLinesValues;

		this.minNumberOfParametersInExtractedMethods = localMinParams;
		this.maxNumberOfParametersInExtractedMethods = localMaxParams;
		this.totalNumberOfParametersInExtractedMethods = localTotalParams;
		this.meanNumberOfParametersInExtractedMethods = (double) localTotalParams / extractionMetrics.length;
		this.parametersInExtractedMethodsValues = localParamsValues;

		this.minReductionOfCognitiveComplexity = localMinReduction;
		this.maxReductionOfCognitiveComplexity = localMaxReduction;
		this.totalReductionOfCognitiveComplexity = localTotalReduction;
		this.meanReductionOfCognitiveComplexity = (double) localTotalReduction / extractionMetrics.length;
		this.reductionOfCognitiveComplexityValues = localReductionValues;

		this.hasExtractionsNestedInAnonymousOrLocalClass = localHasNested;
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

	/**
	 * @return the per-extraction LOC values, in input order, joined with
	 *         {@code |} (e.g. {@code "4|7|2"}). Empty string if there were no
	 *         extractions.
	 */
	public String getExtractedLinesOfCodeList() {
		return joinAsPipeSeparatedList(extractedLinesOfCodeValues);
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

	/**
	 * @return the per-extraction parameter-count values, in input order,
	 *         joined with {@code |} (e.g. {@code "0|2|1"}). Empty string if
	 *         there were no extractions.
	 */
	public String getParametersInExtractedMethodsList() {
		return joinAsPipeSeparatedList(parametersInExtractedMethodsValues);
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

	/**
	 * @return the per-extraction CC-reduction values, in input order, joined
	 *         with {@code |} (e.g. {@code "3|1|5"}). Empty string if there
	 *         were no extractions.
	 */
	public String getReductionOfCognitiveComplexityList() {
		return joinAsPipeSeparatedList(reductionOfCognitiveComplexityValues);
	}

	public boolean hasExtractionsNestedInAnonymousOrLocalClass() {
		return hasExtractionsNestedInAnonymousOrLocalClass;
	}

	/**
	 * Joins an array of ints into a single pipe-separated ({@code |}) string,
	 * e.g. {@code [4, 7, 2] -> "4|7|2"}. Chosen over standard CSV quoting so
	 * that a single CSV cell can hold the whole list without needing nested
	 * quote-escaping, while still being trivial to split back into individual
	 * numeric values for downstream computation.
	 *
	 * @param values the values to join, in order.
	 * @return the pipe-separated string, or {@code ""} if {@code values} is
	 *         empty.
	 */
	private static String joinAsPipeSeparatedList(int[] values) {
		if (values == null || values.length == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				sb.append('|');
			}
			sb.append(values[i]);
		}
		return sb.toString();
	}
}