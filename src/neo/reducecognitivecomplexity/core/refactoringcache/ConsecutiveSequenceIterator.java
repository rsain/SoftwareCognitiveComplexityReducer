package neo.reducecognitivecomplexity.core.refactoringcache;

import java.util.Stack;

import neo.reducecognitivecomplexity.core.refactoringcache.mase.patterns.yieldreturn.IteratorYield;
import neo.reducecognitivecomplexity.core.refactoringcache.mase.patterns.yieldreturn.Yield;
import neo.reducecognitivecomplexity.core.solvers.exhaustivesearch.ExhaustiveEnumerationAlgorithm.Approach;

/**
 * Iterator that generates consecutive sequences of sentences for analysis.
 * <p>
 * This class uses a yield-return pattern to lazily generate combinations of
 * sentence ranges (start, end) that are valid and contribute to cognitive
 * complexity.
 * </p>
 */
public class ConsecutiveSequenceIterator {

	/**
	 * Interface to provide information about the sentences being analyzed.
	 */
	public interface SentenceSequenceInfo {
		/**
		 * @return The total number of sentences in the block.
		 */
		int numberOfSentences();

		/**
		 * @param sentence The sentence index (1-based).
		 * @return The cognitive complexity value of the specific sentence.
		 */
		int cognitiveComplexityOfSentence(int sentence);

		/**
		 * Checks if a sequence of sentences from {@code from} to {@code to} is valid
		 * for extraction (e.g., doesn't break control flow).
		 *
		 * @param from Start index.
		 * @param to   End index.
		 * @return true if the sequence is valid.
		 */
		boolean validSequence(int from, int to);
	}

	/**
	 * Array mapping a sentence index to the index of the <em>next</em> sentence
	 * that has non-zero cognitive complexity.
	 */
	public int[] nextWithCC;

	/**
	 * The index of the last sentence in the block that has non-zero cognitive
	 * complexity.
	 */
	public int lastWithCC;

	private final SentenceSequenceInfo sequence;
	private final Approach approach;

	/**
	 * Stack storing current pairs of (start, end) indices representing sequences.
	 */
	private Stack<Integer> los;

	/**
	 * Constructor.
	 *
	 * @param sequence Information provider for the sentences.
	 * @param approach The search strategy (e.g., LONG_SEQUENCE_FIRST).
	 */
	public ConsecutiveSequenceIterator(SentenceSequenceInfo sequence, Approach approach) {
		this.sequence = sequence;
		this.approach = approach;
	}

	/**
	 * Pre-calculates the `nextWithCC` array and identifying the last complex
	 * sentence to optimize the iteration process.
	 */
	private void initializeDataStructures() {
		int last = sequence.numberOfSentences();
		this.lastWithCC = 0;
		this.nextWithCC = new int[last + 2];

		// Sentinel value: last sentence + 1
		int lastCC = last + 1;
		this.nextWithCC[last + 1] = lastCC;

		// Iterate backwards to map each sentence to the next one that has complexity
		for (int i = last; i > 0; i--) {
			if (sequence.cognitiveComplexityOfSentence(i) > 0) {
				lastCC = i;
				if (this.lastWithCC == 0) {
					this.lastWithCC = i;
				}
			}
			this.nextWithCC[i] = lastCC;
		}

		this.los = new Stack<>();
	}

	/**
	 * Returns an Iterable that generates the sequences. * @return Iterable of
	 * Stacks, where each Stack contains pairs of integers (start, end).
	 */
	public Iterable<Stack<Integer>> getIterable() {
		// delegates to the private method utilizing the custom Yield pattern
		return IteratorYield.getIterable(this::generateElementsRecursive);
	}

	/**
	 * Entry point for the recursive generation.
	 */
	private void generateElementsRecursive(Yield<Stack<Integer>> yield) {
		initializeDataStructures();
		generateElementsRecursive(yield, 1);
	}

	/**
	 * Recursive core that explores valid sentence sequences.
	 *
	 * @param yield The yield context for returning values.
	 * @param first The starting sentence index for this recursion step.
	 */
	private void generateElementsRecursive(Yield<Stack<Integer>> yield, int first) {
		int last = sequence.numberOfSentences();

		// Yield the current state of sequences (even if empty or partial)
		yield.Return(los);

		// Iterate through potential start points that have complexity
		for (int i = first; i <= lastWithCC; i++) {
			int startIndex;
			int endIndex;

			// Determine loop bounds based on the chosen Approach
			if (approach.equals(Approach.LONG_SEQUENCE_FIRST)) {
				// Try longest possible sequences first
				startIndex = last;
				endIndex = nextWithCC[i];
			} else {
				// Try shortest sequences first (expanding outwards)
				startIndex = nextWithCC[i];
				endIndex = last;
			}

			for (int j = startIndex; j <= endIndex; j++) {
				if (sequence.validSequence(i, j)) {
					los.push(i);
					los.push(j);

					// Recurse to find the next sequence starting after j
					generateElementsRecursive(yield, j + 1);

					// Backtrack
					los.pop();
					los.pop();
				}
			}
		}
	}
}