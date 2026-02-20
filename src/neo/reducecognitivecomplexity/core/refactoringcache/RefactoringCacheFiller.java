package neo.reducecognitivecomplexity.core.refactoringcache;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.EmptyStatement;

import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.Sequence;
import neo.reducecognitivecomplexity.core.jdt.CodeExtractionMetrics;
import neo.reducecognitivecomplexity.core.refactoringcache.ConsecutiveSequenceIterator.SentenceSequenceInfo;

/**
 * Helper class responsible for populating the {@link RefactoringCache}.
 * <p>
 * This class orchestrates the exhaustive enumeration of all possible valid
 * refactoring sequences within a given method to "warm up" the cache.
 * </p>
 */
public class RefactoringCacheFiller {

	/**
	 * Explores all valid subsequences within a specific list of sibling statements.
	 *
	 * @param sentences        The sequence of sibling nodes to explore.
	 * @param refactoringCache The cache to populate.
	 */
	private static void exploreSentenceSequence(Sequence sentences, RefactoringCache refactoringCache) {
		new ValidSentenceSequencesExplorer(new SentenceSequenceInfo() {

			@Override
			public int numberOfSentences() {
				return sentences.getSiblingNodes().size();
			}

			@Override
			public int cognitiveComplexityOfSentence(int sentence) {
				ASTNode node = sentences.getSiblingNodes().get(sentence - 1);

				// 1. Try to get the accumulated complexity (direct contribution)
				Integer complexity = (Integer) node
						.getProperty(Constants.ACCUMULATED_CONTRIBUTION_TO_COGNITIVE_COMPLEXITY);

				// 2. If null, it might be a container (e.g., TryStatement) containing complex
				// code
				if (complexity == null) {
					complexity = (Integer) node.getProperty(Constants.COGNITIVE_COMPLEXITY_OF_NESTED_CODE);
				}

				return (complexity != null) ? complexity : 0;
			}

			@Override
			public boolean validSequence(int from, int to) {
				// Empty statements (semicolons) are not valid extraction boundaries
				if (isEmptyStatement(from) || isEmptyStatement(to)) {
					return false;
				}

				// Create a temporary Sequence object for the range [from, to]
				Sequence subSequence = new Sequence(refactoringCache.getCompilationUnit(),
						sentences.getSiblingNodes().subList(from - 1, to));

				// Check feasibility (this call populates the cache internally)
				CodeExtractionMetrics cem = refactoringCache.getMetrics(subSequence);
				return cem.isFeasible();
			}

			private boolean isEmptyStatement(int sentence) {
				ASTNode node = sentences.getSiblingNodes().get(sentence - 1);
				return (node instanceof EmptyStatement);
			}

		}).exploreSequence();
	}

	/**
	 * Entry point to run the exhaustive enumeration algorithm on a specific method.
	 * <p>
	 * It first identifies all "flat" sequences of statements (blocks, switch
	 * groups) and then explores every valid subsequence within them.
	 * </p>
	 *
	 * @param refactoringCache The cache to fill.
	 * @param method           The ASTNode representing the method to analyze.
	 */
	public static void exhaustiveEnumerationAlgorithm(RefactoringCache refactoringCache, ASTNode method) {
		// 1. Visit the method to find all linear sequences of statements (e.g. inside
		// blocks)
		SentencesSelectorVisitor sentencesSelectorVisitor = new SentencesSelectorVisitor(
				refactoringCache.getCompilationUnit());
		method.accept(sentencesSelectorVisitor);

		// 2. For every linear sequence found, explore all combinations
		sentencesSelectorVisitor.getSentencesToIterate()
				.forEach(sequence -> exploreSentenceSequence(sequence, refactoringCache));
	}
}