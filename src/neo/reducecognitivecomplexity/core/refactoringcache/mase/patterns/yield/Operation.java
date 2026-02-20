package neo.reducecognitivecomplexity.core.refactoringcache.mase.patterns.yield;

/**
 * The operation represents a block of code that computes the value of A and
 * later it will receive the value of B
 * @author Francisco Chicano
 * @date 14/01/2012
 *
 * @param <A>
 * @param <B>
 */

public interface Operation<A,B>
{
	public void run(Yield<A,B> yield);
}
