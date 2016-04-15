package cdm2scn;
import org.pi4soa.cdl.*;
import org.pi4soa.cdl.Package;

import java.io.*;

import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.EList;
import java.util.Iterator;

/* 本程序的目的是读取一个.cdm编排文件，并自动生成所有可能的.scn场景文件。
 * 主要的做法是，把给定的编排扫描一遍，输出一个可能的场景；
 * 然后根据上次扫描时存储下来的一些信息，再扫描一遍编排并输出下一种可能的场景；
 * 如此循环，最终就会输出此编排的所有可能场景。
 */
public class CDM2SCN
{
	private int MAX_LOOP_TIMES = 5;
	
	private int branchCount; // 用来记录一条场景路线中，经过的分支点数量
	private ActIterator[] iterStack = new ActIterator[100]; // 记录在每个分支点做的选择
	
	// 因为执行Finalize时要先判断在此之前它是否被成功Perform过
	// 用来记录一条场景路线中，成功完成的Perform数量以及所有被成功Perform过的Choreography名字
	private int performCount;
	private String[] performList = new String[20];
	
	// stopGettingActivities若为真，则停止读取activities，并退出到外层重新调用getOneScn
	private boolean stopGettingActivities;
	// goToExceptionHandler若为真，表示当前场景路线中出现异常，需要寻找对应的ExceptionHandler
	private boolean goToExceptionHandler;
	private String exceptionType;
	
	/* 下面三个数据成员是为了记录场景中的messageLink
	 * messageLink数组中的数据是两两配对的，也即：
	 * source=//@scenarioObject.messageLink[0] target=//@scenarioObject.messageLink[1]
	 * source=//@scenarioObject.messageLink[2] target=//@scenarioObject.messageLink[3]
	 * ...以此类推
	 */
	private int scenarioObjectCount;
	private int messageCount;
	private int[] messageLink = new int[200];
	
	// 记录cdm文件的路径和名称，并且赋予它一个默认路径
	public String cdmPath = "C:/CDM/";
	public String cdmName = "BuyerSeller.cdm";
	
	public CDM2SCN()
	{
		initialize();
	}
	
	public void initialize()
	{
		branchCount = 0;
		performCount = 0;
		stopGettingActivities = false;
		goToExceptionHandler = false;
		scenarioObjectCount = 0;
		messageCount = 0;
	}
	
	public void setPath(String path) throws IOException
	{
		// 把文件路径分割成路径和文件名两部分，分别存入cdmPath和cdmName
		int index1=path.lastIndexOf("/"); // 路径可能使用的分隔符是/或者\
		int index2=path.lastIndexOf("\\");
		if(index1 > index2)
		{
			cdmPath = path.substring(0, 1+index1);
			cdmName = path.substring(1+index1);
		}
		else if(index2 > index1)
		{
			cdmPath = path.substring(0, 1+index2);
			cdmName = path.substring(1+index2);
		}
		else // 如果输入的路径中不含有任何/或者\，则使用默认路径
		{
			System.out.println("Use default path: "+cdmPath+cdmName);
		}
	}
	
	// getOneScn方法可以读出编排中可能的一条场景路线
	public boolean getOneScn(Choreography choreography) throws Exception
	{
		performCount = 0;
		EList<Activity> actList = choreography.getActivities(); // 读取Activity的列表
		Iterator<Activity> iterAct = actList.iterator();
		int passChoice = 0; // 沿着编排树进行扫描，这个变量用来记录经过的Choice节点数量
		while (iterAct.hasNext())
		{
			passChoice += this.getOneAct(iterAct.next(), passChoice); // 对编排的所有活动调用getOneAct方法
			if (stopGettingActivities == true) // 说明此次找到的不是合法的场景，需要重新调用getOneScn寻找新的场景
			{
				stopGettingActivities = false;
				if (branchCount == 0) // 所有的场景路径已经找完了
					return false;
				else
					return this.getOneScn(choreography);
			}
			if (goToExceptionHandler == true) // 如果出现异常
			{
				ExceptionHandler exceptionHandler = choreography.getExceptionHandler();
				if (exceptionHandler != null)
				{
					goToExceptionHandler = false;
					EList<ExceptionWorkUnit> EWUList = exceptionHandler.getExceptionWorkUnits();
					Iterator<ExceptionWorkUnit> iterEWU = EWUList.iterator();
					while (iterEWU.hasNext()) // 遍历ExceptionWorkUnit，看看有没有对应于当前exceptionType的异常处理
					{
						ExceptionWorkUnit ewu = iterEWU.next();
						if (ewu.getExceptionType().equals(exceptionType)) // 找到了对应的异常处理
						{
							EList<Activity> excActList = ewu.getActivities();
							Iterator<Activity> iterExcAct = excActList.iterator();
							while (iterExcAct.hasNext())
							{
								passChoice += this.getOneAct(iterExcAct.next(), passChoice);
							}
						}
					}
				}
				else
				{
					goToExceptionHandler = false;
					break;
				}
			}
		}
		return true;
	}
	
	// getOneAct方法读取指定的一个activity
	// iterStack[]中下标为0~(passChoice-1)的元素被pass，从passChoice号元素开始读取
	// 该方法返回的值是在这个activity中遇到的的分支点数量
	private int getOneAct(Activity act, int passChoice) throws IOException
	{
		//System.out.println(act.getName()+"  "+act.getDescription());
		int passChoiceNew = passChoice;
		if (act instanceof Interaction) // 分情况，如果这个Activity是Interaction：
		{
			EList<ExchangeDetails> detailList = ((Interaction)act).getExchangeDetails();
			Iterator<ExchangeDetails> iterDetail = detailList.iterator();
			while (iterDetail.hasNext())
			{
				ExchangeDetails detail = iterDetail.next();
				if (detail.isFault())
				{
					goToExceptionHandler = true;
					exceptionType = detail.getReceiveCauseException();
					exceptionType = exceptionType.substring(exceptionType.indexOf(':')+1);
				}
			}
		}
		if (act instanceof Choice) // 如果这个Activity是Choice：
		{
			EList<Activity> branchList = ((Choice) act).getActivities();
			Iterator<Activity> iterBranch = branchList.iterator();
			if (passChoiceNew >= branchCount) // 以前没走到过这个分支点
			{
				Activity a = iterBranch.next();
				iterStack[passChoiceNew] = new ActIterator(a, iterBranch);
				branchCount++;
				passChoiceNew++;
				passChoiceNew += this.getOneAct(iterStack[passChoiceNew-1].getActivity(), passChoiceNew);
				if (stopGettingActivities == true)
				{
					return passChoiceNew - passChoice;
				}
				if (goToExceptionHandler == true)
				{
					return passChoiceNew - passChoice;
				}
			}
			else if (passChoiceNew == branchCount - 1) // 这是以前走过分支点中的最后一个
			{
				if (iterStack[passChoiceNew].iterHasNext())
				{
					iterStack[passChoiceNew].iterNext();
					passChoiceNew++;
					passChoiceNew += this.getOneAct(iterStack[passChoiceNew-1].getActivity(), passChoiceNew);
					if (stopGettingActivities == true)
					{
						return passChoiceNew - passChoice;
					}
					if (goToExceptionHandler == true)
					{
						return passChoiceNew - passChoice;
					}
				}
				else
				{
					branchCount--;
					stopGettingActivities = true;
					return -1;
				}
			}
			else  // 这是以前走过的分支点，且不是最后一个
			{
				passChoiceNew++;
				passChoiceNew += this.getOneAct(iterStack[passChoiceNew-1].getActivity(), passChoiceNew);
				if (stopGettingActivities == true)
				{
					return passChoiceNew - passChoice;
				}
				if (goToExceptionHandler == true)
				{
					return passChoiceNew - passChoice;
				}
			}
		}
		if (act instanceof Sequence) // 如果这个Activity是Sequence：
		{
			EList<Activity> actList = ((Sequence) act).getActivities();
			Iterator<Activity> iterAct = actList.iterator();
			while (iterAct.hasNext())
			{
				passChoiceNew += this.getOneAct(iterAct.next(), passChoiceNew);
				if (stopGettingActivities == true)
				{
					return passChoiceNew - passChoice;
				}
				if (goToExceptionHandler == true)
				{
					return passChoiceNew - passChoice;
				}
			}
		}
		if (act instanceof Conditional) // 如果这个Activity是Conditional：
		{
			EList<Activity> actList = ((Conditional) act).getActivities();
			Iterator<Activity> iterAct = actList.iterator();
			if (passChoiceNew >= branchCount) // 以前没走到过这个分支点
			{
				Activity a = iterAct.next();
				iterStack[passChoiceNew] = new ActIterator(a, iterAct, 0);
				branchCount++;
				passChoiceNew++;
				if (stopGettingActivities == true)
				{
					return passChoiceNew - passChoice;
				}
				if (goToExceptionHandler == true)
				{
					return passChoiceNew - passChoice;
				}
			}
			else if (passChoiceNew == branchCount - 1) // 这是以前走过分支点中的最后一个
			{
				if (iterStack[passChoiceNew].getDescription()==0)
				{
					iterStack[passChoiceNew].setDescription(1);
					passChoiceNew++;
					while (iterAct.hasNext())
					{
						passChoiceNew += this.getOneAct(iterAct.next(), passChoiceNew);
						if (stopGettingActivities == true)
						{
							return passChoiceNew - passChoice;
						}
						if (goToExceptionHandler == true)
						{
							return passChoiceNew - passChoice;
						}
					}
				}
				else
				{
					branchCount--;
					stopGettingActivities = true;
					return -1;
				}
			}
			else  // 这是以前走过的分支点，且不是最后一个
			{
				passChoiceNew++;
				if (iterStack[passChoiceNew].getDescription()==1)
				{
					while (iterAct.hasNext())
					{
						passChoiceNew += this.getOneAct(iterAct.next(), passChoiceNew);
						if (stopGettingActivities == true)
						{
							return passChoiceNew - passChoice;
						}
						if (goToExceptionHandler == true)
						{
							return passChoiceNew - passChoice;
						}
					}
				}
			}
		}
		if (act instanceof Parallel) // 如果这个Activity是Parallel：(Unfinished!!!)
		{
			EList<Activity> actList = ((Parallel) act).getActivities();
			Iterator<Activity> iterAct = actList.iterator();
			while (iterAct.hasNext())
			{
				passChoiceNew += this.getOneAct(iterAct.next(), passChoiceNew);
				if (stopGettingActivities == true)
				{
					return passChoiceNew - passChoice;
				}
				if (goToExceptionHandler == true) // 这里还要修改!!!
				{
					return passChoiceNew - passChoice;
				}
			}
		}
		if (act instanceof While) // 如果这个Activity是While：
		{
			if (passChoiceNew >= branchCount) // 以前没有经过过这个循环活动
			{
				EList<Activity> actList = ((While) act).getActivities();
				Iterator<Activity> iterAct = actList.iterator();
				Activity a = iterAct.next();
				iterStack[passChoiceNew] = new ActIterator(a, iterAct, 0);
				branchCount++;
				passChoiceNew++;
				if (stopGettingActivities == true)
				{
					return passChoiceNew - passChoice;
				}
				if (goToExceptionHandler == true)
				{
					return passChoiceNew - passChoice;
				}
			}
			else if (passChoiceNew == branchCount - 1) // 这是以前经过的循环中的最后一个
			{
				int loopTimes = iterStack[passChoiceNew].getDescription();
				if (loopTimes < MAX_LOOP_TIMES)
				{
					loopTimes++;
					iterStack[passChoiceNew].setDescription(loopTimes);
					passChoiceNew++;
					for(;loopTimes>0;loopTimes--)
					{
						EList<Activity> actList = ((While) act).getActivities();
						Iterator<Activity> iterAct = actList.iterator();
						while (iterAct.hasNext())
						{
							passChoiceNew += this.getOneAct(iterAct.next(), passChoiceNew);
							if (stopGettingActivities == true)
							{
								return passChoiceNew - passChoice;
							}
							if (goToExceptionHandler == true)
							{
								return passChoiceNew - passChoice;
							}
						}
					}
				}
				else
				{
					branchCount--;
					stopGettingActivities = true;
					return -1;
				}
			}
			else  // 这是以前经过的循环活动，且不是最后一个
			{
				passChoiceNew++;
				int loopTimes = iterStack[passChoiceNew].getDescription();
				for(;loopTimes>0;loopTimes--)
				{
					EList<Activity> actList = ((While) act).getActivities();
					Iterator<Activity> iterAct = actList.iterator();
					while (iterAct.hasNext())
					{
						passChoiceNew += this.getOneAct(iterAct.next(), passChoiceNew);
						if (stopGettingActivities == true)
						{
							return passChoiceNew - passChoice;
						}
						if (goToExceptionHandler == true)
						{
							return passChoiceNew - passChoice;
						}
					}
				}
			}
		}
		if (act instanceof Perform) // 如果这个Activity是Perform：
		{
			Choreography choreo = ((Perform) act).getChoreography();
			
			EList<Activity> actList = choreo.getActivities();
			Iterator<Activity> iterAct = actList.iterator();
			while (iterAct.hasNext())
			{
				passChoiceNew += this.getOneAct(iterAct.next(), passChoiceNew);
				if (stopGettingActivities == true)
				{
					return passChoiceNew - passChoice;
				}
				if (goToExceptionHandler == true) // 如果出现异常
				{
					ExceptionHandler exceptionHandler = choreo.getExceptionHandler();
					if (exceptionHandler != null)
					{
						goToExceptionHandler = false;
						EList<ExceptionWorkUnit> EWUList = exceptionHandler.getExceptionWorkUnits();
						Iterator<ExceptionWorkUnit> iterEWU = EWUList.iterator();
						while (iterEWU.hasNext())
						{
							ExceptionWorkUnit ewu = iterEWU.next();
							if (ewu.getExceptionType().equals(exceptionType)) // 找到了对应的异常处理
							{
								EList<Activity> excActList = ewu.getActivities();
								Iterator<Activity> iterExcAct = excActList.iterator();
								while (iterExcAct.hasNext())
								{
									passChoiceNew += this.getOneAct(iterExcAct.next(), passChoiceNew);
								}
								return passChoiceNew - passChoice;
							}
						}
						goToExceptionHandler = true; // 不存在对应的异常处理，则把它置为true，退回上一层继续寻找ExceptionHandler
					}
					return passChoiceNew - passChoice; // 出现异常时，这个perform并没有成功完成，所以下面的两条语句不需要执行
				}
			}
			performList[performCount] = choreo.getName();
			performCount++;
		}
		if (act instanceof Finalize) // 如果这个Activity是Finalize：
		{
			String choreoName = ((Finalize) act).getChoreography().getName();
			for(int i = 0; i < performCount; i++)
			{
				if(choreoName.equals(performList[i]))
				{
					EList<Activity> actList = ((Finalize) act).getFinalizer().getActivities();
					Iterator<Activity> iterAct = actList.iterator();
					while (iterAct.hasNext())
					{
						passChoiceNew += this.getOneAct(iterAct.next(), passChoiceNew);
						if (stopGettingActivities == true)
						{
							return passChoiceNew - passChoice;
						}
						if (goToExceptionHandler == true) // 如果出现异常
						{
							ExceptionHandler exceptionHandler = ((Finalize) act).getChoreography().getExceptionHandler();
							if (exceptionHandler != null)
							{
								goToExceptionHandler = false;
								EList<ExceptionWorkUnit> EWUList = exceptionHandler.getExceptionWorkUnits();
								Iterator<ExceptionWorkUnit> iterEWU = EWUList.iterator();
								while (iterEWU.hasNext())
								{
									ExceptionWorkUnit ewu = iterEWU.next();
									if (ewu.getExceptionType().equals(exceptionType)) // 找到了对应的异常处理
									{
										EList<Activity> excActList = ewu.getActivities();
										Iterator<Activity> iterExcAct = excActList.iterator();
										while (iterExcAct.hasNext())
										{
											passChoiceNew += this.getOneAct(iterExcAct.next(), passChoiceNew);
										}
										return passChoiceNew - passChoice;
									}
								}
								goToExceptionHandler = true; // 不存在对应的异常处理，则把它置为true，退回上一层继续寻找ExceptionHandler
							}
							return passChoiceNew - passChoice;
						}
					}
					break;
				}
			}
		}
		return passChoiceNew - passChoice;
	}
	
	public void generateAllScn(String path) throws Exception
	{
		setPath(path);
		org.pi4soa.cdl.Package pack = CDLManager.load(cdmPath + cdmName); // 读入cdm文件中的package
		EList<Choreography> choreoList = pack.getChoreographies(); // 并把package中的所有choreography都存入一个表内
		Iterator<Choreography> iterChoreo = choreoList.iterator(); // 这个表可以由一个Iterator对象遍历
		
		while (iterChoreo.hasNext())
		{
			Choreography choreo = iterChoreo.next();
			if (choreo.getRoot()) // 只对root choreography执行以下操作，而不输出子编排：
			{
				generateAllScn(choreo);
			}
		}
	}
	
	public void generateAllScn(CDLType choreography) throws Exception
	{
		int scnNum = 1; // 这是输出的scn文件的编号
		initialize();
		while (getOneScn(choreography)) // getOneScn返回真，则说明成功地找到了一条场景路线
		{
			genOneScn(choreography, scnNum); // 输出该场景至编号为scnNum的.scn文件中
			if (branchCount == 0) break; // 如果这个编排本来就没有任何分支，则跳出while循环
			scnNum++;
		}
	}
	
	public void genOneScn(CDLType choreography, int scnNum) throws IOException
	{
		performCount = 0; // 把一些变量初始化
		scenarioObjectCount = 0;
		messageCount = 0;
		String name = cdmPath + choreography.getName() + scnNum + ".scn";
		FileOutputStream output = new FileOutputStream(name);
		System.out.print("Generating "+name+" ... ");
		printScenarioPreamble(choreography, output, scnNum);
		EList<Activity> actList;
		if (choreography instanceof Choreography)
		{
			actList = ((Choreography)choreography).getActivities();
		}
		else if (choreography instanceof Activity)
		{
			actList = ECollections.emptyEList();
			actList.move(0, (Activity)choreography);
		}
		Iterator<Activity> iterAct = actList.iterator();
		int passChoice = 0;
		while (iterAct.hasNext())
		{
			passChoice += this.printScenarioObjects(iterAct.next(), output, passChoice);
			if (goToExceptionHandler == true) // 如果出现异常
			{
				ExceptionHandler exceptionHandler = choreography.getExceptionHandler();
				if (exceptionHandler != null)
				{
					goToExceptionHandler = false;
					EList<ExceptionWorkUnit> EWUList = exceptionHandler.getExceptionWorkUnits();
					Iterator<ExceptionWorkUnit> iterEWU = EWUList.iterator();
					while (iterEWU.hasNext())
					{
						ExceptionWorkUnit ewu = iterEWU.next();
						if (ewu.getExceptionType().equals(exceptionType)) // 找到了对应的异常处理
						{
							EList<Activity> excActList = ewu.getActivities();
							Iterator<Activity> iterExcAct = excActList.iterator();
							while (iterExcAct.hasNext())
							{
								passChoice += this.printScenarioObjects(iterExcAct.next(), output, passChoice);
							}
						}
					}
				}
				else
				{
					goToExceptionHandler = false;
					break;
				}
			}
		}
		printMessageLinks(output);
		printParticipants(output);
		output.write("</scn:Scenario>\r\n".getBytes());
		System.out.println("Done!");
	}
	
	private void printScenarioPreamble(Choreography choreography, FileOutputStream output, int scnNum) throws IOException
	{
		output.write("<?xml version=\"1.0\" encoding=\"GBK\"?>\r\n".getBytes());
		output.write("<scn:Scenario xmi:version=\"2.0\" xmlns:xmi=\"http://www.omg.org/XMI\" ".getBytes());
		output.write("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ".getBytes());
		output.write("xmlns:scn=\"http://www.pi4soa.org/scenario\" ".getBytes());
		output.write(("name=\""+choreography.getName()+scnNum+"\" ").getBytes());
		output.write(("description=\""+choreography.getName()+scnNum+"\" ").getBytes());
		output.write("author=\"\" ".getBytes());
		output.write(("choreographyDescriptionURL=\""+cdmName+"\">\r\n").getBytes());
	}
	
	// printScenarioObjects方法读取指定的一个activity，并视情况将它输出到scn文件中
	// Choice[]中下标为0~(passChoice-1)的元素被pass，从passChoice号元素开始读取
	private int printScenarioObjects(Activity act, FileOutputStream output, int passChoice) throws IOException
	{
		int passChoiceNew = passChoice;
		if (act instanceof Choice) // 分情况，如果这个Activity是Choice：
		{
			passChoiceNew++;
			passChoiceNew += this.printScenarioObjects(iterStack[passChoiceNew-1].getActivity(), output, passChoiceNew);
			if (goToExceptionHandler == true)
			{
				return passChoiceNew - passChoice;
			}
		}
		if (act instanceof Sequence) // 如果这个Activity是Sequence：
		{
			EList<Activity> actList = ((Sequence) act).getActivities();
			Iterator<Activity> iterAct = actList.iterator();
			while (iterAct.hasNext())
			{
				passChoiceNew += this.printScenarioObjects(iterAct.next(), output, passChoiceNew);
				if (goToExceptionHandler == true)
				{
					return passChoiceNew - passChoice;
				}
			}
		}
		if (act instanceof Interaction) // 如果这个Activity是Interaction：
		{
			EList<ExchangeDetails> detailList = ((Interaction)act).getExchangeDetails();
			Iterator<ExchangeDetails> iterDetail = detailList.iterator();
			while (iterDetail.hasNext())
			{
				ExchangeDetails detail = iterDetail.next();
				output.write("  <scenarioObjects xsi:type=\"scn:MessageEvent\" ".getBytes());
				output.write("sessionId=\"s1\" ".getBytes());
				
				Package pack = CDLManager.load(cdmPath + cdmName);
				EList<ParticipantType> partiList = pack.getTypeDefinitions().getParticipantTypes();
				Iterator<ParticipantType> iterParti = partiList.iterator();
				int fromParticipantNum = -1;
				int toParticipantNum = -1;
				int PartiNum = 0;
				while (iterParti.hasNext())
				{
					EList<RoleType> roleList = iterParti.next().getRoleTypes();
					Iterator<RoleType> iterRole = roleList.iterator();
					while (iterRole.hasNext())
					{
						RoleType role = iterRole.next();
						if (role.getName().equals(((Interaction)act).getFromRoleType().getName()))
						{
							fromParticipantNum = PartiNum;
						}
						if (role.getName().equals(((Interaction)act).getToRoleType().getName()))
						{
							toParticipantNum = PartiNum;
						}
					}
					PartiNum++;
				}
				
				String messageType, faultName = "";
				if (detail.getType().getClassification() == 1)
					messageType=((InformationType) detail.getType()).getTypeName();
				else
					messageType=((ChannelType) detail.getType()).getReferenceToken().getInformationType().getTypeName();
				EList<NameSpace> nameSpaceList = pack.getTypeDefinitions().getNameSpaces();
				Iterator<NameSpace> iterNameSpace = nameSpaceList.iterator();
				
				while (iterNameSpace.hasNext())
				{
					NameSpace nameSpace = iterNameSpace.next();
					String prefix = nameSpace.getPrefix();
					if (messageType.substring(0, prefix.length()+1).equals(prefix+":"))
					{
						messageType = "{"+nameSpace.getURI()+"}"+messageType.substring(prefix.length()+1);
					}
					if (detail.isFault()&&detail.getFaultName().substring(0, prefix.length()+1).equals(prefix+":"))
					{
						faultName = detail.getFaultName();
						faultName = "{"+nameSpace.getURI()+"}"+faultName.substring(prefix.length()+1);
						goToExceptionHandler = true;
						exceptionType = detail.getReceiveCauseException();
						exceptionType = exceptionType.substring(exceptionType.indexOf(':')+1);
					}
				}
				
				if (detail.getAction().getValue()==1)
					output.write(("participant=\"//@participants."+fromParticipantNum+"\" ").getBytes());
				else
					output.write(("participant=\"//@participants."+toParticipantNum+"\" ").getBytes());
				
				output.write(("operationName=\""+((Interaction)act).getOperation()+"\" ").getBytes());
				
				if (detail.isFault())
				{
					output.write(("faultName=\""+faultName+"\" ").getBytes());
				}
				if (detail.getAction().getValue()!=1)
				{
					output.write("isRequest=\"false\" ".getBytes());
				}
				output.write(("value=\"&lt;"+detail.getName()+" id=&quot;1&quot; />\" ").getBytes());
				output.write("channelId=\"c1\" ".getBytes());
				output.write(("messageType=\""+messageType+"\"/>\r\n").getBytes());
				
				
				output.write("  <scenarioObjects xsi:type=\"scn:MessageEvent\" ".getBytes());
				output.write("sessionId=\"s1\" ".getBytes());
				
				if (detail.getAction().getValue()==1)
					output.write(("participant=\"//@participants."+toParticipantNum+"\" ").getBytes());
				else
					output.write(("participant=\"//@participants."+fromParticipantNum+"\" ").getBytes());
				
				output.write(("operationName=\""+((Interaction)act).getOperation()+"\" ").getBytes());
				if (detail.isFault())
				{
					output.write(("faultName=\""+faultName+"\" ").getBytes());
				}
				if (detail.getAction().getValue()!=1)
				{
					output.write("isRequest=\"false\" ".getBytes());
				}
				output.write(("value=\"&lt;"+detail.getName()+" id=&quot;1&quot; />\" ").getBytes());
				output.write("direction=\"receive\" ".getBytes());
				output.write("channelId=\"c1\" ".getBytes());
				output.write(("messageType=\""+messageType+"\"/>\r\n").getBytes());
				
				messageLink[2*messageCount] = scenarioObjectCount;
				scenarioObjectCount++;
				messageLink[2*messageCount+1] = scenarioObjectCount;
				scenarioObjectCount++;
				messageCount++;
			}
		}
		if (act instanceof Conditional) // 如果这个Activity是Conditional：
		{
			passChoiceNew++;
			if (iterStack[passChoiceNew].getDescription()==1)
			{
				EList<Activity> actList = ((While) act).getActivities();
				Iterator<Activity> iterAct = actList.iterator();
				while (iterAct.hasNext())
				{
					passChoiceNew += this.printScenarioObjects(iterAct.next(), output, passChoiceNew);
					if (goToExceptionHandler == true)
					{
						return passChoiceNew - passChoice;
					}
				}
			}
		}
		if (act instanceof Parallel) // 如果这个Activity是Parallel：(Unfinished!!!)
		{
			EList<Activity> actList = ((Parallel) act).getActivities();
			Iterator<Activity> iterAct = actList.iterator();
			while (iterAct.hasNext())
			{
				passChoiceNew += this.printScenarioObjects(iterAct.next(), output, passChoiceNew);
				if (goToExceptionHandler == true) // 需要进一步处理!!!
				{
					return passChoiceNew - passChoice;
				}
			}
		}
		if (act instanceof While) // 如果这个Activity是While：
		{
			passChoiceNew++;
			int loopTimes = iterStack[passChoiceNew].getDescription();
			for(;loopTimes>0;loopTimes--)
			{
				EList<Activity> actList = ((While) act).getActivities();
				Iterator<Activity> iterAct = actList.iterator();
				while (iterAct.hasNext())
				{
					passChoiceNew += this.printScenarioObjects(iterAct.next(), output, passChoiceNew);
					if (goToExceptionHandler == true)
					{
						return passChoiceNew - passChoice;
					}
				}
			}
		}
		if (act instanceof Perform) // 如果这个Activity是Perform：
		{
			Choreography choreo = ((Perform) act).getChoreography();
			
			EList<Activity> actList = choreo.getActivities();
			Iterator<Activity> iterAct = actList.iterator();
			while (iterAct.hasNext())
			{
				passChoiceNew += this.printScenarioObjects(iterAct.next(), output, passChoiceNew);
				if (goToExceptionHandler == true) // 如果出现异常
				{
					ExceptionHandler exceptionHandler = choreo.getExceptionHandler();
					if (exceptionHandler != null)
					{
						goToExceptionHandler = false;
						EList<ExceptionWorkUnit> EWUList = exceptionHandler.getExceptionWorkUnits();
						Iterator<ExceptionWorkUnit> iterEWU = EWUList.iterator();
						while (iterEWU.hasNext())
						{
							ExceptionWorkUnit ewu = iterEWU.next();
							if (ewu.getExceptionType().equals(exceptionType)) // 找到了对应的异常处理
							{
								EList<Activity> excActList = ewu.getActivities();
								Iterator<Activity> iterExcAct = excActList.iterator();
								while (iterExcAct.hasNext())
								{
									passChoiceNew += this.printScenarioObjects(iterExcAct.next(), output, passChoiceNew);
								}
								return passChoiceNew - passChoice;
							}
						}
						goToExceptionHandler = true; // 不存在对应的异常处理，则把它置为true，退回上一层继续寻找ExceptionHandler
					}
					return passChoiceNew - passChoice; // 出现异常时，这个perform并没有成功完成，所以下面的两条语句不需要执行
				}
			}
			performList[performCount] = choreo.getName();
			performCount++;
		}
		if (act instanceof Finalize) // 如果这个Activity是Finalize：
		{
			String choreoName = ((Finalize) act).getChoreography().getName();
			for(int i = 0; i < performCount; i++)
			{
				if(choreoName.equals(performList[i]))
				{
					EList<Activity> actList = ((Finalize) act).getFinalizer().getActivities();
					Iterator<Activity> iterAct = actList.iterator();
					while (iterAct.hasNext())
					{
						passChoiceNew += this.printScenarioObjects(iterAct.next(), output, passChoiceNew);
						if (goToExceptionHandler == true) // 如果出现异常
						{
							ExceptionHandler exceptionHandler = ((Finalize) act).getChoreography().getExceptionHandler();
							if (exceptionHandler != null)
							{
								goToExceptionHandler = false;
								EList<ExceptionWorkUnit> EWUList = exceptionHandler.getExceptionWorkUnits();
								Iterator<ExceptionWorkUnit> iterEWU = EWUList.iterator();
								while (iterEWU.hasNext())
								{
									ExceptionWorkUnit ewu = iterEWU.next();
									if (ewu.getExceptionType().equals(exceptionType)) // 找到了对应的异常处理
									{
										EList<Activity> excActList = ewu.getActivities();
										Iterator<Activity> iterExcAct = excActList.iterator();
										while (iterExcAct.hasNext())
										{
											passChoiceNew += this.printScenarioObjects(iterExcAct.next(), output, passChoiceNew);
										}
										return passChoiceNew - passChoice;
									}
								}
								goToExceptionHandler = true; // 不存在对应的异常处理，则把它置为true，退回上一层继续寻找ExceptionHandler
							}
							return passChoiceNew - passChoice;
						}
					}
					break;
				}
			}
		}
		return passChoiceNew - passChoice;
		
	}
	
	private void printMessageLinks(FileOutputStream output) throws IOException
	{
		for(int i = 0; i < messageCount; i++)
		{
			output.write("  <messageLinks ".getBytes());
			output.write(("source=\"//@scenarioObjects."+messageLink[2*i]+"\" ").getBytes());
			output.write(("target=\"//@scenarioObjects."+messageLink[2*i+1]+"\"/>\r\n").getBytes());
		}
	}
	
	private void printParticipants(FileOutputStream output) throws IOException
	{
		Package pack = CDLManager.load(cdmPath + cdmName);
		EList<ParticipantType> partiList = pack.getTypeDefinitions().getParticipantTypes();
		Iterator<ParticipantType> iterParti = partiList.iterator();
		while (iterParti.hasNext())
		{
			output.write(("  <participants type=\""+iterParti.next().getName()+"\"/>\r\n").getBytes());
		}
	}
	
	public int getMaxLoops() { return MAX_LOOP_TIMES; }
	public void setMaxLoops(int i) { MAX_LOOP_TIMES = i; }
}