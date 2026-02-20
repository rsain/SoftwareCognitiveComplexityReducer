package neo.reducecognitivecomplexity.core.refactoringcache;

import neo.reducecognitivecomplexity.core.refactoringcache.ConsecutiveSequenceIterator.SentenceSequenceInfo;

/**
 * Explorer that iterates over all potentially valid sentence sequences that
 * contribute to cognitive complexity.
 * <p>
 * Unlike the iterator/supplier, this class is typically used to eagerly
 * validate or "warm up" a cache by invoking
 * {@link SentenceSequenceInfo#validSequence(int, int)} on all relevant ranges.
 * </p>
 */
public class ValidSentenceSequencesExplorer {

	private int[] nextWithCC;
	private int lastWithCC;
	private final SentenceSequenceInfo sequence;

	public ValidSentenceSequencesExplorer(SentenceSequenceInfo sequence) {
		this.sequence = sequence;
	}

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
	}

	/**
	 * Triggers validation for all sequences that contain at least one
	 * complexity-contributing statement. This effectively "visits" every valid
	 * range [i, j] that matters for complexity reduction.
	 */
	public void exploreSequence() {
		initializeDataStructures();
		int last = sequence.numberOfSentences();

		// Iterate through all start points 'i' that precede or are complexity
		// contributors
		for (int i = 1; i <= lastWithCC; i++) {

			// Iterate through end points 'j' backwards from the end.
			// We stop at nextWithCC[i] because any sequence ending *before* the next
			// complex node would effectively not capture the complexity we are trying to
			// isolate.
			for (int j = last; j >= nextWithCC[i]; j--) {
				sequence.validSequence(i, j);
			}
		}
	}
}