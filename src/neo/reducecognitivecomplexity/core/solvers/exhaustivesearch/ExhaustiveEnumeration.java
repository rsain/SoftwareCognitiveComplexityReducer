package neo.reducecognitivecomplexity.core.solvers.exhaustivesearch;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A generic utility for performing an exhaustive search (Cartesian product) over a list of iterables,
 * with support for pruning invalid branches early.
 * <p>
 * This class implements an iterative backtracking algorithm. It constructs combinations by picking
 * one element from each iterable in the provided list. The {@code validity} predicate is checked
 * at every step of the construction, allowing the algorithm to skip entire branches of the search
 * tree if a partial combination is already invalid.
 * </p>
 *
 * @param <T> The type of elements being enumerated.
 */
public class ExhaustiveEnumeration<T> {

    private final List<Iterable<T>> elementsToIterate;
    private final Predicate<Stack<T>> validity;

    // State variables for the iterative backtracking
    private Stack<Iterator<T>> iterators;
    private Stack<T> currentElement;

    /**
     * Constructs a new enumerator.
     *
     * @param elementsToIterate A list of data sources. The algorithm will attempt to pick one item
     * from the first iterable, then one from the second, etc.
     * @param validity          A predicate to test partial or complete solutions. If this returns
     * {@code false}, the current branch is pruned immediately.
     */
    public ExhaustiveEnumeration(List<Iterable<T>> elementsToIterate, Predicate<Stack<T>> validity) {
        this.elementsToIterate = elementsToIterate;
        this.validity = validity;
    }

    /**
     * Executes the exhaustive search.
     *
     * @param consumer    The action to perform on each valid complete combination found.
     * <p><b>Note:</b> The {@code Stack} passed to the consumer is reused/mutable.
     * If the consumer needs to persist the data, it must create a copy.</p>
     * @param maxElements The maximum number of valid solutions to find before stopping.
     */
    public void run(Consumer<Stack<T>> consumer, long maxElements) {
        this.iterators = new Stack<>();
        this.currentElement = new Stack<>();
        long count = 0;

        if (maxElements <= 0) {
            return;
        }

        // Initialize the first level
        if (thereAreMoreIteratorsToAdd()) {
            addNewIterator();
        }

        // Iterative backtracking loop
        while (!iterators.isEmpty()) {
            if (iterators.peek().hasNext()) {
                // Advance the current level to the next element
                iterateOverTopIterator();

                // Check if the current partial solution is valid
                if (validity.test(currentElement)) {
                    if (thereAreMoreIteratorsToAdd()) {
                        // Go deeper: Add iterator for the next level
                        addNewIterator();
                    } else {
                        // Leaf node: We have a complete, valid solution
                        consumer.accept(currentElement);
                        count++;
                        
                        if (count >= maxElements) {
                            return;
                        }
                    }
                }
                // If not valid, the loop continues, effectively pruning this branch
                // and trying the next element at the current level.
            } else {
                // Current iterator exhausted: Backtrack to previous level
                removeTopIterator();
            }
        }
    }

    /**
     * Calculates the total size of the search space (Cartesian product).
     * <p>
     * This iterates through all source iterables to count their sizes.
     * </p>
     *
     * @return The theoretical maximum number of combinations (ignoring validity checks).
     */
    public BigInteger count() {
        BigInteger result = BigInteger.ONE;

        for (Iterable<T> iterable : elementsToIterate) {
            long number = 0;
            for (T ignored : iterable) {
                number++;
            }
            
            if (number == 0) {
                return BigInteger.ZERO;
            }
            
            result = result.multiply(BigInteger.valueOf(number));
        }

        return result;
    }

    // --- Helper Methods ---

    /**
     * Backtracks: Removes the iterator and element of the deepest level.
     */
    protected void removeTopIterator() {
        if (!iterators.isEmpty()) iterators.pop();
        if (!currentElement.isEmpty()) currentElement.pop();
    }

    /**
     * Checks if we have not yet reached the depth equal to the number of input iterables.
     */
    protected boolean thereAreMoreIteratorsToAdd() {
        return iterators.size() < elementsToIterate.size();
    }

    /**
     * Updates the current element at the top of the stack with the next value
     * from the current iterator.
     */
    protected void iterateOverTopIterator() {
        // Remove the placeholder or previous value
        currentElement.pop();
        // Push the new value
        currentElement.push(iterators.peek().next());
    }

    /**
     * Descends into the recursion: Gets the iterator for the next level
     * and pushes a placeholder (null) onto the element stack.
     */
    protected void addNewIterator() {
        // Get the iterator corresponding to the current depth
        iterators.push(elementsToIterate.get(iterators.size()).iterator());
        // Push placeholder to maintain stack alignment before first next() is called
        currentElement.push(null);
    }
}