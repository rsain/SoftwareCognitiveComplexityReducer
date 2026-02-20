package neo.reducecognitivecomplexity.core.refactoringcache.mase.patterns.yieldreturn;

/**
 * This interface is used in the Operation to fill the iterator with objects.
 * @author Francisco Chicano
 * @date 14/01/2012
 *
 * @param <A>
 * @param <B>
 */

public interface Yield<T>
{
	public void Return(T obj);
	public void Break();
}
