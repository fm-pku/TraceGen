package cdm2scn;
import org.pi4soa.cdl.*;
import java.util.Iterator;

class ActIterator
{
	private Activity act;
	private Iterator<Activity> iterAct;
	private int des;
	
	public ActIterator(Activity activity, Iterator<Activity> iterator)
	{
		act = activity;
		iterAct = iterator;
	}
	
	public ActIterator(Activity activity, Iterator<Activity> iterator, int description)
	{
		act = activity;
		iterAct = iterator;
		des = description;
	}
	
	public Activity getActivity()
	{
		return act;
	}
	
	public boolean iterHasNext()
	{
		return iterAct.hasNext();
	}
	
	public void iterNext()
	{
		act = iterAct.next();
	}
	
	public void setDescription(int i)
	{
		des = i;
	}
	
	public int getDescription()
	{
		return des;
	}
}