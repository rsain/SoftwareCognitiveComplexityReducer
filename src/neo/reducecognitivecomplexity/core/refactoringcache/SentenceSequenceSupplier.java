package neo.reducecognitivecomplexity.core.refactoringcache;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import neo.reducecognitivecomplexity.core.Sequence;
import neo.reducecognitivecomplexity.core.solvers.exhaustivesearch.ExhaustiveEnumerationAlgorithm.Approach;

/**
 * A Supplier that provides a random list of {@link Sequence} objects from the
 * available refactoring options.
 * <p>
 * This class uses a {@link SentenceSequenceIterator} to generate all possible
 * valid sequence combinations (options), caches them, and then allows random
 * access or retrieval by index.
 * </p>
 */
public class SentenceSequenceSupplier implements Supplier<List<Sequence>> {

	// TODO: Address potential memory issues if the number of options is very large.
	// Consider lazy loading or streaming if 'options' grows too big.

	private final SentenceSequenceIterator ssi;
	private final Random rnd;

	/**
	 * Cache for the computed list of sequence options. Initialized lazily in
	 * {@link #precomputeListOfSequences()}.
	 */
	private List<List<Sequence>> options;

	/**
	 * Constructs a supplier for sentence sequences.
	 *
	 * @param sentences        The base sequence of sentences (AST nodes) to
	 *                         process.
	 * @param refactoringCache The cache used to validate refactoring feasibility.
	 * @param approach         The strategy for enumeration (e.g., Longest First).
	 * @param seed             The seed for the random number generator to ensure
	 *                         reproducibility.
	 */
	public SentenceSequenceSupplier(Sequence sentences, RefactoringCache refactoringCache, Approach approach,
			long seed) {
		this.ssi = new SentenceSequenceIterator(sentences, refactoringCache, approach);
		this.rnd = new Random(seed);
	}

	/**
	 * Lazily populates the `options` list from the iterator. This method ensures
	 * the exhaustive search is performed only once.
	 */
	private void precomputeListOfSequences() {
		if (options == null) {
			options = new ArrayList<>();
			// Consume the iterator and cache all results
			ssi.forEach(l -> options.add(l));
		}
	}

	/**
	 * Returns a random list of sequences from the precomputed options. * @return A
	 * random {@code List<Sequence>}.
	 */
	@Override
	public List<Sequence> get() {
		precomputeListOfSequences();
		if (options.isEmpty()) {
			return new ArrayList<>(); // Or handle appropriately if no options exist
		}
		return options.get(rnd.nextInt(options.size()));
	}

	/**
	 * Returns the total number of valid sequence options discovered. * @return The
	 * count of options.
	 */
	public int numberOfOptions() {
		precomputeListOfSequences();
		return options.size();
	}

	/**
	 * Retrieves a specific option by index. * @param i The index of the option to
	 * retrieve.
	 * 
	 * @return The {@code List<Sequence>} at the specified index.
	 * @throws IndexOutOfBoundsException if the index is invalid.
	 */
	public List<Sequence> getOption(int i) {
		precomputeListOfSequences();
		return options.get(i);
	}
}