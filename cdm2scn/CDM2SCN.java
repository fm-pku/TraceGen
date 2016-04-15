package cdm2scn;
import org.pi4soa.cdl.*;
import org.pi4soa.cdl.Package;

import java.io.*;

import org.eclipse.emf.common.util.ECollections;
import org.eclipse.emf.common.util.EList;
import java.util.Iterator;

/* �������Ŀ���Ƕ�ȡһ��.cdm�����ļ������Զ��������п��ܵ�.scn�����ļ���
 * ��Ҫ�������ǣ��Ѹ����ı���ɨ��һ�飬���һ�����ܵĳ�����
 * Ȼ������ϴ�ɨ��ʱ�洢������һЩ��Ϣ����ɨ��һ����Ų������һ�ֿ��ܵĳ�����
 * ���ѭ�������վͻ�����˱��ŵ����п��ܳ�����
 */
public class CDM2SCN
{
	private int MAX_LOOP_TIMES = 5;
	
	private int branchCount; // ������¼һ������·���У������ķ�֧������
	private ActIterator[] iterStack = new ActIterator[100]; // ��¼��ÿ����֧������ѡ��
	
	// ��Ϊִ��FinalizeʱҪ���ж��ڴ�֮ǰ���Ƿ񱻳ɹ�Perform��
	// ������¼һ������·���У��ɹ���ɵ�Perform�����Լ����б��ɹ�Perform����Choreography����
	private int performCount;
	private String[] performList = new String[20];
	
	// stopGettingActivities��Ϊ�棬��ֹͣ��ȡactivities�����˳���������µ���getOneScn
	private boolean stopGettingActivities;
	// goToExceptionHandler��Ϊ�棬��ʾ��ǰ����·���г����쳣����ҪѰ�Ҷ�Ӧ��ExceptionHandler
	private boolean goToExceptionHandler;
	private String exceptionType;
	
	/* �����������ݳ�Ա��Ϊ�˼�¼�����е�messageLink
	 * messageLink�����е�������������Եģ�Ҳ����
	 * source=//@scenarioObject.messageLink[0] target=//@scenarioObject.messageLink[1]
	 * source=//@scenarioObject.messageLink[2] target=//@scenarioObject.messageLink[3]
	 * ...�Դ�����
	 */
	private int scenarioObjectCount;
	private int messageCount;
	private int[] messageLink = new int[200];
	
	// ��¼cdm�ļ���·�������ƣ����Ҹ�����һ��Ĭ��·��
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
		// ���ļ�·���ָ��·�����ļ��������֣��ֱ����cdmPath��cdmName
		int index1=path.lastIndexOf("/"); // ·������ʹ�õķָ�����/����\
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
		else // ��������·���в������κ�/����\����ʹ��Ĭ��·��
		{
			System.out.println("Use default path: "+cdmPath+cdmName);
		}
	}
	
	// getOneScn�������Զ��������п��ܵ�һ������·��
	public boolean getOneScn(Choreography choreography) throws Exception
	{
		performCount = 0;
		EList<Activity> actList = choreography.getActivities(); // ��ȡActivity���б�
		Iterator<Activity> iterAct = actList.iterator();
		int passChoice = 0; // ���ű���������ɨ�裬�������������¼������Choice�ڵ�����
		while (iterAct.hasNext())
		{
			passChoice += this.getOneAct(iterAct.next(), passChoice); // �Ա��ŵ����л����getOneAct����
			if (stopGettingActivities == true) // ˵���˴��ҵ��Ĳ��ǺϷ��ĳ�������Ҫ���µ���getOneScnѰ���µĳ���
			{
				stopGettingActivities = false;
				if (branchCount == 0) // ���еĳ���·���Ѿ�������
					return false;
				else
					return this.getOneScn(choreography);
			}
			if (goToExceptionHandler == true) // ��������쳣
			{
				ExceptionHandler exceptionHandler = choreography.getExceptionHandler();
				if (exceptionHandler != null)
				{
					goToExceptionHandler = false;
					EList<ExceptionWorkUnit> EWUList = exceptionHandler.getExceptionWorkUnits();
					Iterator<ExceptionWorkUnit> iterEWU = EWUList.iterator();
					while (iterEWU.hasNext()) // ����ExceptionWorkUnit��������û�ж�Ӧ�ڵ�ǰexceptionType���쳣����
					{
						ExceptionWorkUnit ewu = iterEWU.next();
						if (ewu.getExceptionType().equals(exceptionType)) // �ҵ��˶�Ӧ���쳣����
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
	
	// getOneAct������ȡָ����һ��activity
	// iterStack[]���±�Ϊ0~(passChoice-1)��Ԫ�ر�pass����passChoice��Ԫ�ؿ�ʼ��ȡ
	// �÷������ص�ֵ�������activity�������ĵķ�֧������
	private int getOneAct(Activity act, int passChoice) throws IOException
	{
		//System.out.println(act.getName()+"  "+act.getDescription());
		int passChoiceNew = passChoice;
		if (act instanceof Interaction) // �������������Activity��Interaction��
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
		if (act instanceof Choice) // ������Activity��Choice��
		{
			EList<Activity> branchList = ((Choice) act).getActivities();
			Iterator<Activity> iterBranch = branchList.iterator();
			if (passChoiceNew >= branchCount) // ��ǰû�ߵ��������֧��
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
			else if (passChoiceNew == branchCount - 1) // ������ǰ�߹���֧���е����һ��
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
			else  // ������ǰ�߹��ķ�֧�㣬�Ҳ������һ��
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
		if (act instanceof Sequence) // ������Activity��Sequence��
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
		if (act instanceof Conditional) // ������Activity��Conditional��
		{
			EList<Activity> actList = ((Conditional) act).getActivities();
			Iterator<Activity> iterAct = actList.iterator();
			if (passChoiceNew >= branchCount) // ��ǰû�ߵ��������֧��
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
			else if (passChoiceNew == branchCount - 1) // ������ǰ�߹���֧���е����һ��
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
			else  // ������ǰ�߹��ķ�֧�㣬�Ҳ������һ��
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
		if (act instanceof Parallel) // ������Activity��Parallel��(Unfinished!!!)
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
				if (goToExceptionHandler == true) // ���ﻹҪ�޸�!!!
				{
					return passChoiceNew - passChoice;
				}
			}
		}
		if (act instanceof While) // ������Activity��While��
		{
			if (passChoiceNew >= branchCount) // ��ǰû�о��������ѭ���
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
			else if (passChoiceNew == branchCount - 1) // ������ǰ������ѭ���е����һ��
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
			else  // ������ǰ������ѭ������Ҳ������һ��
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
		if (act instanceof Perform) // ������Activity��Perform��
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
				if (goToExceptionHandler == true) // ��������쳣
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
							if (ewu.getExceptionType().equals(exceptionType)) // �ҵ��˶�Ӧ���쳣����
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
						goToExceptionHandler = true; // �����ڶ�Ӧ���쳣�����������Ϊtrue���˻���һ�����Ѱ��ExceptionHandler
					}
					return passChoiceNew - passChoice; // �����쳣ʱ�����perform��û�гɹ���ɣ����������������䲻��Ҫִ��
				}
			}
			performList[performCount] = choreo.getName();
			performCount++;
		}
		if (act instanceof Finalize) // ������Activity��Finalize��
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
						if (goToExceptionHandler == true) // ��������쳣
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
									if (ewu.getExceptionType().equals(exceptionType)) // �ҵ��˶�Ӧ���쳣����
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
								goToExceptionHandler = true; // �����ڶ�Ӧ���쳣�����������Ϊtrue���˻���һ�����Ѱ��ExceptionHandler
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
		org.pi4soa.cdl.Package pack = CDLManager.load(cdmPath + cdmName); // ����cdm�ļ��е�package
		EList<Choreography> choreoList = pack.getChoreographies(); // ����package�е�����choreography������һ������
		Iterator<Choreography> iterChoreo = choreoList.iterator(); // ����������һ��Iterator�������
		
		while (iterChoreo.hasNext())
		{
			Choreography choreo = iterChoreo.next();
			if (choreo.getRoot()) // ֻ��root choreographyִ�����²�������������ӱ��ţ�
			{
				generateAllScn(choreo);
			}
		}
	}
	
	public void generateAllScn(CDLType choreography) throws Exception
	{
		int scnNum = 1; // ���������scn�ļ��ı��
		initialize();
		while (getOneScn(choreography)) // getOneScn�����棬��˵���ɹ����ҵ���һ������·��
		{
			genOneScn(choreography, scnNum); // ����ó��������ΪscnNum��.scn�ļ���
			if (branchCount == 0) break; // ���������ű�����û���κη�֧��������whileѭ��
			scnNum++;
		}
	}
	
	public void genOneScn(CDLType choreography, int scnNum) throws IOException
	{
		performCount = 0; // ��һЩ������ʼ��
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
			if (goToExceptionHandler == true) // ��������쳣
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
						if (ewu.getExceptionType().equals(exceptionType)) // �ҵ��˶�Ӧ���쳣����
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
	
	// printScenarioObjects������ȡָ����һ��activity������������������scn�ļ���
	// Choice[]���±�Ϊ0~(passChoice-1)��Ԫ�ر�pass����passChoice��Ԫ�ؿ�ʼ��ȡ
	private int printScenarioObjects(Activity act, FileOutputStream output, int passChoice) throws IOException
	{
		int passChoiceNew = passChoice;
		if (act instanceof Choice) // �������������Activity��Choice��
		{
			passChoiceNew++;
			passChoiceNew += this.printScenarioObjects(iterStack[passChoiceNew-1].getActivity(), output, passChoiceNew);
			if (goToExceptionHandler == true)
			{
				return passChoiceNew - passChoice;
			}
		}
		if (act instanceof Sequence) // ������Activity��Sequence��
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
		if (act instanceof Interaction) // ������Activity��Interaction��
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
		if (act instanceof Conditional) // ������Activity��Conditional��
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
		if (act instanceof Parallel) // ������Activity��Parallel��(Unfinished!!!)
		{
			EList<Activity> actList = ((Parallel) act).getActivities();
			Iterator<Activity> iterAct = actList.iterator();
			while (iterAct.hasNext())
			{
				passChoiceNew += this.printScenarioObjects(iterAct.next(), output, passChoiceNew);
				if (goToExceptionHandler == true) // ��Ҫ��һ������!!!
				{
					return passChoiceNew - passChoice;
				}
			}
		}
		if (act instanceof While) // ������Activity��While��
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
		if (act instanceof Perform) // ������Activity��Perform��
		{
			Choreography choreo = ((Perform) act).getChoreography();
			
			EList<Activity> actList = choreo.getActivities();
			Iterator<Activity> iterAct = actList.iterator();
			while (iterAct.hasNext())
			{
				passChoiceNew += this.printScenarioObjects(iterAct.next(), output, passChoiceNew);
				if (goToExceptionHandler == true) // ��������쳣
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
							if (ewu.getExceptionType().equals(exceptionType)) // �ҵ��˶�Ӧ���쳣����
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
						goToExceptionHandler = true; // �����ڶ�Ӧ���쳣�����������Ϊtrue���˻���һ�����Ѱ��ExceptionHandler
					}
					return passChoiceNew - passChoice; // �����쳣ʱ�����perform��û�гɹ���ɣ����������������䲻��Ҫִ��
				}
			}
			performList[performCount] = choreo.getName();
			performCount++;
		}
		if (act instanceof Finalize) // ������Activity��Finalize��
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
						if (goToExceptionHandler == true) // ��������쳣
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
									if (ewu.getExceptionType().equals(exceptionType)) // �ҵ��˶�Ӧ���쳣����
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
								goToExceptionHandler = true; // �����ڶ�Ӧ���쳣�����������Ϊtrue���˻���һ�����Ѱ��ExceptionHandler
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