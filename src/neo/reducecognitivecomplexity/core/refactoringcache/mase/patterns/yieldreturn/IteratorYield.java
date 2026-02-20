package neo.reducecognitivecomplexity.core.refactoringcache.mase.patterns.yieldreturn;

import java.util.Iterator;
import java.util.NoSuchElementException;

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

public class IteratorYield<T> implements Iterator<T>
{
	private static class YieldImpl<T> implements Yield<T>
	{
		private T argument;
		private boolean arg;
		private boolean end;
		private RuntimeException exc;
		
		public synchronized void Break()
		{
			arg = false;
			end = true;
			notify();
			try
			{
				wait();
			}
			catch (InterruptedException e)
			{
				
			}
		}
		
		@Override
		public synchronized void Return(T obj)
		{
			arg = true;
			argument = obj;
			end = false;
			notify();
			try
			{
				wait();
			}
			catch (InterruptedException e)
			{
				
			}
		}
	}
	
	private Operation<T> op;
	private YieldImpl<T> yi;
	private Thread th;
	private IteratorYield(Operation<T> op)
	{
		this.op=op;
		yi =  new YieldImpl<T>();
	}
	
	public T next()
	{
		synchronized(yi)
		{
			while (true)
			{
				
				if (yi.arg)
				{
					yi.arg=false;
					return yi.argument;
				}
				else if (yi.end)
				{
					if (th != null)
					{
						th.stop();
					}
					th=null;
					throw new NoSuchElementException();
				}
				else
				{
					advanceThread();
					if (yi.exc != null)
					{
						throw yi.exc;
					}
				}
			}
		}
	}
	
	private void advanceThread()
	{
		if (th == null)
		{
			th = new Thread(){
				public void run ()
				{
					try
					{
						op.run(yi);
						yi.Break();
					}
					catch (RuntimeException e)
					{
						synchronized(yi)
						{
							yi.exc = e;
							yi.notify();
						}
					}
					
				}
			};
			th.start();
		}
		yi.notify();
		try
		{
			yi.wait();
		} catch (InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public boolean hasNext()
	{
		synchronized(yi)
		{
			while (true)
			{
				if (yi.arg)
				{
					return true;
				}
				else if (yi.end)
				{
					if (th != null)
					{
						th.stop();
					}
					th=null;
					return false;
				}
				else
				{
					advanceThread();
					if (yi.exc != null)
					{
						throw yi.exc;
					}
				}
			}
		}
	}

	@Override
	public void remove()
	{
		throw new UnsupportedOperationException();
		
	}
	
	public static <T> Iterator<T> getIterator(Operation<T> op)
	{
		return new IteratorYield<T>(op);
	}
	
	public static <T> Iterable<T> getIterable(final Operation<T> op)
	{
		return new Iterable<T>(){
			@Override
			public Iterator<T> iterator()
			{
				return IteratorYield.getIterator(op);
			}};
	}
}
