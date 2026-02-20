package neo.reducecognitivecomplexity.core.refactoringcache.mase.patterns.yield;

/**
 * An exception for the Yield pattern.
 * @author Francisco Chicano
 * @date 14/01/2012
 *
 */


public class YieldException extends RuntimeException
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public YieldException()
	{
		super();
	}
	
	public YieldException(String message)
	{
		super(message);
	}
}
