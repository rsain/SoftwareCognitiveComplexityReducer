package neo.reducecognitivecomplexity.core.refactoringcache.mase.patterns.yield;

/**
 * This interface is used in the Operation to inform on 
 * the A value and get the B value.
 * @author Francisco Chicano
 * @date 14/01/2012
 *
 * @param <A>
 * @param <B>
 */

public interface Yield<A,B>
{
	public B yield(A a);
}
