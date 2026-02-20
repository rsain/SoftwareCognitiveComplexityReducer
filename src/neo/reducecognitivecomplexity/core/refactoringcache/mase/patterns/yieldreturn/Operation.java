package neo.reducecognitivecomplexity.core.refactoringcache.mase.patterns.yieldreturn;

/**
 * The operation represents a block of code that computes the 
 * elements of the iterator using the yield return (C#) pattern.
 * @author Francisco Chicano
 * @date 14/01/2012
 *
 * @param <A>
 * @param <B>
 */

public interface Operation<T>
{
	public void run(Yield<T> yield);
}
