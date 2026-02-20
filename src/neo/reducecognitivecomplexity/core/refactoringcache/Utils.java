package neo.reducecognitivecomplexity.core.refactoringcache;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * General utility methods for search algorithms and collection manipulation.
 */
public class Utils {

	/**
	 * Adapts an {@link Iterable} of type S to an {@link Iterable} of type T by
	 * applying a mapping function to each element.
	 * <p>
	 * This is a lazy operation; the function is applied only when {@code next()} is
	 * called on the iterator.
	 * </p>
	 *
	 * @param <S> The source type.
	 * @param <T> The target type.
	 * @param it  The source iterable.
	 * @param fn  The function to map elements from S to T.
	 * @return An iterable producing elements of type T.
	 */
	public static <S, T> Iterable<T> adapt(Iterable<S> it, Function<S, T> fn) {
		return () -> new Iterator<T>() {
			private final Iterator<S> original = it.iterator();

			@Override
			public boolean hasNext() {
				return original.hasNext();
			}

			@Override
			public T next() {
				return fn.apply(original.next());
			}
		};
	}

	/**
	 * Filters a Map based on the values using a predicate.
	 *
	 * @param <K>       The key type.
	 * @param <V>       The value type.
	 * @param map       The map to filter.
	 * @param predicate The condition to test against the map values.
	 * @return A new Map containing only entries where the value satisfies the
	 *         predicate.
	 */
	public static <K, V> Map<K, V> filterByValue(Map<K, V> map, Predicate<V> predicate) {
		return map.entrySet().stream().filter(entry -> predicate.test(entry.getValue()))
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
	}
}