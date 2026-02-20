package neo.reducecognitivecomplexity.core.solvers.exhaustivesearch;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTNode;

import neo.reducecognitivecomplexity.core.Sequence;
import neo.reducecognitivecomplexity.core.refactoringcache.RefactoringCache;
import neo.reducecognitivecomplexity.core.refactoringcache.SentenceSequenceIterator;
import neo.reducecognitivecomplexity.core.refactoringcache.SentencesSelectorVisitor;

/**
 * Orchestrates the exhaustive search for refactoring opportunities within a method.
 * <p>
 * This class uses a {@link SentencesSelectorVisitor} to identify blocks of code (sentences)
 * that can be refactored, and then uses {@link ExhaustiveEnumeration} to generate
 * all valid combinations of these refactorings based on the selected {@link Approach}.
 * </p>
 */
public class ExhaustiveEnumerationAlgorithm {

    private final ASTNode method;
    private final RefactoringCache refactoringCache;
    private final SentencesSelectorVisitor sentencesSelectorVisitor;
    private final Approach approach;

    /**
     * Defines the strategy for iterating over possible refactoring sequences.
     */
    public enum Approach {
        /**
         * Prioritizes longer sequences of statements for extraction first.
         * Often yields larger refactorings early in the search.
         */
        LONG_SEQUENCE_FIRST, 
        
        /**
         * Prioritizes shorter sequences of statements first.
         * Useful for finding fine-grained refactorings.
         */
        SHORT_SEQUENCE_FIRST
    }

    /**
     * Constructs the algorithm instance.
     *
     * @param refactoringCache Cache containing valid refactorings and metrics.
     * @param method           The AST node of the method to analyze.
     * @param approach         The search strategy (heuristic) to apply.
     */
    public ExhaustiveEnumerationAlgorithm(RefactoringCache refactoringCache, ASTNode method, Approach approach) {
        this.refactoringCache = refactoringCache;
        this.method = method;
        this.approach = approach;
        
        // Initialize the visitor to identify refactorable blocks in the AST
        this.sentencesSelectorVisitor = new SentencesSelectorVisitor(refactoringCache.getCompilationUnit());
        this.method.accept(sentencesSelectorVisitor);
    }

    /**
     * Executes the search and streams valid solutions to the consumer.
     *
     * @param consumer    Accepts a complete list of refactoring Sequences representing a solution.
     * @param maxElements The maximum number of solutions to generate.
     */
    public void run(Consumer<List<Sequence>> consumer, long maxElements) {
        // 1. Prepare iterators for each block of code identified by the visitor
        List<Iterable<List<Sequence>>> elementsToIterate = sentencesSelectorVisitor.getSentencesToIterate().stream()
                .map(sequence -> new SentenceSequenceIterator(sequence, refactoringCache, approach))
                .collect(Collectors.toList());

        // 2. Initialize the generic exhaustive enumerator
        // The predicate is technically unused here (always true) because validity is handled 
        // implicitly by the iterators or the cache.
        ExhaustiveEnumeration<List<Sequence>> ee = new ExhaustiveEnumeration<>(elementsToIterate, t -> true);

        // 3. Run the search
        ee.run(stackOfSolutions -> {
            // Flatten the Stack<List<Sequence>> into a single List<Sequence>
            // Each element in the stack represents a selection for a specific code block.
            List<Sequence> result = new ArrayList<>();
            for (List<Sequence> partial : stackOfSolutions) {
                if (partial != null) {
                    result.addAll(partial);
                }
            }
            consumer.accept(result);
        }, maxElements);
    }

    /**
     * Calculates the total size of the search space.
     *
     * @return The number of possible combinations found by the visitor.
     */
    public BigInteger count() {
        List<Iterable<List<Sequence>>> elementsToIterate = sentencesSelectorVisitor.getSentencesToIterate().stream()
                .map(sequence -> new SentenceSequenceIterator(sequence, refactoringCache, approach))
                .collect(Collectors.toList());

        ExhaustiveEnumeration<List<Sequence>> ee = new ExhaustiveEnumeration<>(elementsToIterate, t -> true);
        return ee.count();
    }
}