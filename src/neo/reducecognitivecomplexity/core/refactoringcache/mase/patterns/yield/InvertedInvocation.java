package neo.reducecognitivecomplexity.core.refactoringcache.mase.patterns.yield;

/**
 * This class allows one to invert the usual flow of method calls.
 * It is constructed based on an operation, which takes a Yield object
 * to invert the flow.
 * @author Francisco Chicano
 * @date 14/01/2012
 *
 * @param <A>
 * @param <B>
 */

public class InvertedInvocation<A,B>
{
	
	private enum State {CALLER, CALLEE, END};
	private static class YieldImpl<A, B> implements Yield<A, B>
	{
		private A argument;
		private B result;
		private State st;
		private YieldException exc;
		
		@Override
		public B yield(A a)
		{
			synchronized (this)
			{
				argument = a;
				if (st != null)
				{
					throw new YieldException("yield should be called just once");
				}
				notify();
				try
				{
					wait();
				}
				catch (InterruptedException e)
				{
					
				}
				
				return result;
			}
		}
	}

	private Operation<A,B> op;
	private YieldImpl<A,B> yi;
	public InvertedInvocation(Operation<A,B> op)
	{
		this.op=op;
		yi =  new YieldImpl<A,B>();
	}
	
	public A getArgument(){
		synchronized (yi)
		{
			if (yi.st != null && yi.st != State.CALLEE)
			{
				throw new YieldException("getArgument called twice");
			}
			
			Thread th = new Thread ()
			{
				public void run ()
				{
					try
					{
						op.run(yi);
					}
					catch (YieldException e)
					{
						yi.exc = e;
					}
					synchronized(yi)
					{
						yi.notify();
					}
				}
			};
			th.start();
			try
			{
				yi.wait();
			}
			catch (InterruptedException e)
			{
			}
			
			yi.st = State.CALLER;
			
			return yi.argument;
		}
	}
	
	public void setReturn(B b)
	{
		synchronized(yi)
		{
			if (yi.st != State.CALLER)
			{
				throw new YieldException("setReturn must be called after getArgument()");
			}
			yi.result = b;
			yi.notify();
			try
			{
				yi.st = State.CALLEE;
				yi.wait();
				yi.st = State.END;
			}
			catch (InterruptedException e)
			{
				
			}
			
			if (yi.exc != null)
			{
				throw yi.exc;
			}
		}
	}

}
