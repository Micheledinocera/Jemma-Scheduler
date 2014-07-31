package it.ismb.jemma.scheduler.test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

import it.ismb.jemma.scheduler.util.AppliancesSimpleService;
import it.ismb.jemma.scheduler.util.Time;

import org.energy_home.jemma.ah.cluster.zigbee.eh.SignalStateResponse;
import org.energy_home.jemma.ah.hac.IAppliance;
import org.energy_home.jemma.ah.hac.IEndPoint;
import org.energy_home.jemma.ah.hac.IServiceCluster;
import org.energy_home.jemma.ah.hac.lib.ext.IAppliancesProxy;
import org.energy_home.jemma.ah.hac.lib.ext.TextConverter;
import org.osgi.service.component.ComponentContext;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/* Global comments 
 * 	
 * Some functions are marked as "not used", it means that they can be used for some future implementations
 */

public class AppliancesSimpleServiceImpl implements AppliancesSimpleService 
{
	// Global variables
	
	ArrayList<Integer> posOn=new ArrayList<Integer>();							// A list where are stored at what time position is turned on the appliance, the indices match with the appliances list
	ArrayList<Double> powList=new ArrayList<Double>();							// A list where are stored the power consumption of the appliances, the indices match with the appliances list
	ComponentContext context;													// The current context
	IAppliancesProxy appliancesProxy;											// An instance of the interface appliancesProxy used for invoke clusters
	ArrayList<Double> forecastPV=getPVForecast();								// A list where are stored the power production forecast for the PhotoVoltaic
	List<IAppliance> appliances=new ArrayList<IAppliance>();					// A list where are stored the appliances
	ArrayList<ArrayList<Time>> dispTime= new ArrayList<ArrayList<Time>>();		// A matrix where are stored the appliances start and stop, 
																				// the coloumns match with the appliances list indices,
																				// even rows have appliance start times and odd rows have appliance end times
	
	// Activate
	
	public void activate(ComponentContext context){this.context=context;}

	// Appliances
	
	/** This function return the list of the installed appliances,
	 *  notice that is not a void function because I need to recall it from the interface AppliancesSimpleService 
	 * 
	 * @return the list of the installed appliances in the web console in the global variable appliances
	 */
	
	@SuppressWarnings("unchecked")
	public List<IAppliance> getAppliances()
	{
		List<IAppliance> appliances1=appliancesProxy.getAppliances();								// appliances1 is a local variable because appliances is global
		
		while(dispTime.size()<(appliances.size())){dispTime.add(new ArrayList<Time>());}			// for every appliance add a new List of start and stop times
		while(powList.size()<appliances.size()){powList.add(0.0);}									// for every appliance add a new spot to the powList
		
		for(int i=0;i<appliances.size();i++)														// updates the array appliances if some appliance is deleted
		{
			int flag=0;
			for(int j=0;j<appliances1.size();j++)
			{
				if(appliances1.get(j).getPid().equals(appliances.get(i).getPid())){break;}
				if(j==(appliances.size()-1)){flag=1;}
			}
			if(flag==1)
			{
				appliances.remove(i);
				dispTime.remove(i);
				powList.remove(i);
			}
		}
		
		for(int i=0;i<appliances1.size();i++)														// updates the array appliances if some appliance is installed
		{
			if(appliances1.get(i).getDescriptor().getFriendlyName().equals("Green@Home")){}								// don't put into appliances Green@home  
			else if(appliances1.get(i).getConfiguration().get("ah.app.name").toString().equals("Metering Device 1")){}	// don't put into appliances Smart Info
			else
			{
				int flag=1;
				for(int j=0;j<appliances.size();j++)
				{
					flag=0;
					if(appliances1.get(i).getPid().equals(appliances.get(j).getPid())){break;}
					if(j==(appliances.size()-1)){flag=1;}
				}
				if(flag==1){appliances.add(appliances1.get(i));}
			}
		}
		return appliances;
	}

	// Times
	
	/** Converts an integer into a bit array
	 * 
	 * @param i an integer
	 * @return return a list of integer used like bit i.e. 0 or 1
	 */
	
	public ArrayList<Integer> toBin(int i)
	{
		ArrayList<Integer> v=new ArrayList<Integer>();
		if(i==0)
		{
			for(int j=0;j<9;j++)	v.add(0);
		}
		else
		{
			int n=(int) Math.floor(Math.log(i)/Math.log(2));
			for(int j=0;j<=n;j++)
			{
				v.add(j,i%2);
				i=(int) Math.floor(i/2);
			}
		}
		return v;
	}
	
	/** Converts a bit array in a Time,
	 *  for more information about the time codification see ZigBee specification 
	 * 
	 * @param v an array of integers used like bits i.e. 1 or 0
	 * @return a Time
	 */
	
	private Time fromBinToOrario(ArrayList<Integer> v)
	{
		Time ora=new Time(0,0,0);
		Calendar now1 = Calendar.getInstance();
		Time now=new Time(now1.get(Calendar.HOUR),now1.get(Calendar.MINUTE),now1.get(Calendar.AM_PM));
		
		int minutes=0;
		int hours=0;
		
		for(int i=0;i<=5;i++){minutes+=Math.pow(2,i)*v.get(i);}							// the first 6 bits are the minutes
		
		for(int i=8;i<v.size();i++){hours+=Math.pow(2, i-8)*v.get(i);}					// the bits from 8 to the last are the hours
		
		if(v.get(6)==1)																	// the 6-th bit tell if the time is absolute or relative, 1=absolute and 0=relative
		{
			ora.setMinutes(minutes);
			ora.setHours(hours);
		}
		else
		{
			ora.setMinutes(minutes+now.getMinutes());
			ora.setHours(hours+now.getHours()+ora.getHours());
		}
		
		return ora;
	}

	/** This function invoke a cluster method that returns the start time of the appliance,
	 *  notice that only the appliances that support the ApplianceControlServer cluster can use this function 
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @return an integer that have to be decoded as a Time
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private int getStartTime(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception 
	{
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"getStartTime", new String[0], appliancesProxy.getRequestContext(true));
		
		int ST=(Integer)appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"getStartTime",
				objectParams);
		
		return ST;
	}
	
	/** This function invoke a cluster method that returns the end time of the appliance,
	 *  notice that only the appliances that support the ApplianceControlServer cluster can use this function 
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @return an integer that have to be decoded as a Time
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private int getEndTime(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception 
	{
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"getFinishTime", new String[0], appliancesProxy.getRequestContext(true));
		
		int ET=(Integer)appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"getFinishTime",
				objectParams);
		
		return ET;
	}
	
	/** This function add the start Time ST and the end Time ET at the list dispTime[posDisp]
	 *  
	 *  @param posDisp the appliance position
	 *  @param ST Start Time
	 *  @param ET End Time
	 */
	
	public void addTime(int posDisp, Time ST, Time ET)
	{
		getAppliances();
		
		while(dispTime.size()<(appliances.size())){dispTime.add(new ArrayList<Time>());}
		
		dispTime.get(posDisp).add(ST);
		dispTime.get(posDisp).add(ET);
	}
	
	/** This function remove the start Time and the end Time from the list dispTime[posDisp] at the positions 2*pos and 2*pos+1
	 *  
	 *  @param posDisp the appliance position
	 *  @param pos the Times position
	 */
	
	public void removeTime(int posDisp, int pos)
	{	
		dispTime.get(posDisp).remove((2*pos)+1);
		dispTime.get(posDisp).remove(2*pos);
	}
	
	/** This function change the start Time and the end Time from the list dispTime[posDisp] with the start Time ST and the end Time ET at the positions 2*pos and 2*pos+1
	 *  
	 *  @param posDisp the appliance position
	 *  @param pos the old Times position
	 *  @param ST the new Start Time
	 *  @param ET the new End Time
	 */
	
	public void changeTime(int posDisp, int pos, Time ST, Time ET)
	{
		getAppliances();
		
		while(dispTime.size()<(appliances.size())){dispTime.add(new ArrayList<Time>());}
		
		dispTime.get(posDisp).set(2*pos,ST);
		dispTime.get(posDisp).set((2*pos)+1,ET);
	}
	
	/** This function add a start Time and an end Time at the list dispTime[posDisp] taken them from the appliance,
	 *  notice that the function is similar to addTime but it works only with appliances that support the ApplianceControlServer cluster
	 *  
	 *  @param posDisp the appliance position
	 */
	
	public Time[] getTime(int posDisp)
	{
		Time[] Times={new Time(0,0,0),new Time(0,0,0)};
		
		getAppliances();
		IEndPoint[] eps= appliances.get(posDisp).getEndPoints();

		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.ApplianceControlServer"))			// call the functions getStartTime and getEndTime only if the cluster is the right one
				{
					try
					{
						Times[0]=fromBinToOrario(toBin(getStartTime(appliances.get(posDisp), ep, cluster)));
						Times[1]=fromBinToOrario(toBin(getEndTime(appliances.get(posDisp), ep, cluster)));
					}
				
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			}
		}
		return Times;
	}
	
	// Consumes
	
	/** This function invoke a cluster method that returns the instantaneous consume of the appliance as an integer,
	 *  notice that the result has to be formatted using the related multiplier and divisor
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @return an integer with the instant consume of the appliance
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private int getConsume(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception 
	{
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"getIstantaneousDemand", new String[0], appliancesProxy.getRequestContext(true));
		
		int consume=(Integer)appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"getIstantaneousDemand",
				objectParams);
		
		return consume;
	}
	
	/** This function invoke a cluster method that returns the multiplier for the appliance consume,
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @return an integer that indicates the multiplier
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private int getMultiplier(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception
	{
		
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"getMultiplier", new String[0], appliancesProxy.getRequestContext(true));
		
		int multiplier=(Integer)appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"getMultiplier",
				objectParams);
		
		return multiplier;
	}
	
	/** This function invoke a cluster method that returns the divisor for the appliance consume,
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @return an integer that indicates the divisor
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private int getDivisor(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception
	{
		
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"getDivisor", new String[0], appliancesProxy.getRequestContext(true));
		
		int divisor=(Integer)appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"getDivisor",
				objectParams);
		
		return divisor;
	}
	
	/** This function invoke a cluster method that returns the energy formatting for the appliance consume as an integer,
	 *  notice that the result has to be decoded as a bit array,
	 *  notice also that this function can be used only by appliances that support the PowerProfileSrver cluster
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @return an integer that indicates the energy formatting
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private int getEnergyFormatting(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception 
	{
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"getEnergyFormatting", new String[0] , appliancesProxy.getRequestContext(true));
		
		Object result=appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"getEnergyFormatting",
				objectParams);
		
		ArrayList<Integer> v=fromStringToArrayListInteger(TextConverter.getTextRepresentation(result));
		
		return v.get(0);
	}
	
	/** This function invoke a cluster method that returns the energy formatting for the appliance consume as an integer,
	 *  notice that the result has to be decoded as a bit array,
	 *  notice also that this function can be used only by appliances that support the SimpleMeteringServer cluster
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @return an integer that indicates the energy formatting
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private int getSummationFormatting(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception 
	{
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"getSummationFormatting", new String[0] , appliancesProxy.getRequestContext(true));
		
		Object result=appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"getSummationFormatting",
				objectParams);
		
		ArrayList<Integer> v=fromStringToArrayListInteger(TextConverter.getTextRepresentation(result));
		
		return v.get(0);
	}
	
	/** This function invoke a cluster method that returns the energy consumption of the appliance,
	 *  notice that the result has to be formatted using the related energy formatting
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @return an integer with the energy consumption of the appliance
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private double getCurrentSummationDelivered(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception 
	{
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"getCurrentSummationDelivered", new String[0] , appliancesProxy.getRequestContext(true));
		
		Object result=appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"getCurrentSummationDelivered",
				objectParams);
		
		ArrayList<Integer> v=fromStringToArrayListInteger(TextConverter.getTextRepresentation(result));
		
		return v.get(0);
	}
	
	/** This function converts an array of bits in the energy formatting
	 * 
	 * @param v an array of integers
	 * @return the number of digits at the left of the comma
	 */
	
	private int fromBinToEnergyFormatting(ArrayList<Integer> v)
	{
		int k=0;
		for(int i=0;i<3;i++){k+=Math.pow(2,i)*v.get(i);}
		
		return k;
	}
	
	/** This function take the unformatted energy as input and return the formatted energy as output,
	 * 	notice that are used different function depending on the supported cluster
	 * 
	 * @param energy the energy 
	 * @param pos the appliance position
	 * @return the energy formatted
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private double EnergyFormatted(double energy, int pos) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception
	{
		int format=0;
		
		IEndPoint[] eps=appliances.get(pos).getEndPoints();
		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.PowerProfileServer"))
				{
					try
					{
						format=fromBinToEnergyFormatting(toBin(getEnergyFormatting(appliances.get(pos), ep, cluster)));
					}
				
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}else if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.metering.SimpleMeteringServer"))
				{
					try
					{
						format=fromBinToEnergyFormatting(toBin(getSummationFormatting(appliances.get(pos), ep, cluster)));
					}
				
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			}
		}
		
		energy=energy*Math.pow(10,-1*format);
		
		return energy;
	}
	
	/** This function invoke a cluster method that returns an array with the energy consumption forecast for the setted program, one for every cicle,
	 *  notice that only the appliances that support the PowerProfileServer cluster can use this function
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @return an array with the energy consumption 
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private ArrayList<Integer> getPowerProfileNotification(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception
	{
		String[] stringa={"0"};
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"execPowerProfileRequest", stringa , appliancesProxy.getRequestContext(true));
		
		Object result=appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"execPowerProfileRequest",
				objectParams);
		
		ArrayList<Integer> v=fromStringToArrayListInteger(TextConverter.getTextRepresentation(result));
		
		return v;
	} 
	
	/** This function converts a string into an array of integers, 
	 *  		e.g. "e45|r5" ----> {45,5}
	 * 
	 * @param a a string
	 * @return an array of integers
	 */
	
	public ArrayList<Integer> fromStringToArrayListInteger(String a)
	{
		int flag=0;int n=a.length();int j=0;
		ArrayList<Integer> v=new ArrayList<Integer>();
		for(int i=0;i<n;i++)
		{
			try 
			{
				if(flag==0)
				{
					v.add(Integer.parseInt(a.substring(i, i+1)));
					j++;
					flag=1;
				}
				else
				{
					int k=j-1;
					v.set(k, v.get(k)*10+Integer.parseInt(a.substring(i, i+1)));
				}
				
			} 
			catch (NumberFormatException e){flag=0;}
		}
	return v;
	}
	
	/** This function returns the formatted energy consumption of the appliance in the position pos,
	 *  notice that this function works only on the appliances that support the SimpleMeteringServer cluster
	 * 
	 * @param pos the appliance position
	 * @return a double that indicates the energy consumption of the appliance
	 */
	
	public double getSummation(int pos)
	{
		double pow=0.0;
		
		IEndPoint[] eps=appliances.get(pos).getEndPoints();
		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.metering.SimpleMeteringServer"))
				{
					try
					{
						pow=getCurrentSummationDelivered(appliances.get(pos), ep, cluster);
						
						try
						{
							pow=EnergyFormatted(pow,pos);
						}
						
						catch (ClassNotFoundException e1){e1.printStackTrace();}
						catch (InstantiationException e1){e1.printStackTrace();}
						catch (IllegalAccessException e1){e1.printStackTrace();}
						catch (NoSuchFieldException e1){e1.printStackTrace();}
						catch (Exception e1){e1.printStackTrace();}
					}
					
							
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			}
		}
		
		return pow;
	}
	
	/** This function returns the formatted energy consumption of the appliance in the position pos,
	 *  notice that this function works only on the appliances that support the PowerProfileServer cluster
	 * 
	 * @param pos the appliance position
	 * @return a double that indicates the energy consumption of the appliance
	 */
	
	public double getPower(int pos)
	{
		double pow=0.0;
		ArrayList<Integer> PowerProfileNotification=new ArrayList<Integer>();
		IEndPoint[] eps=appliances.get(pos).getEndPoints();
		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.PowerProfileServer"))
				{
					try
					{
						PowerProfileNotification=getPowerProfileNotification(appliances.get(pos), ep, cluster);
						int n=PowerProfileNotification.get(2);
						for(int m=0;m<n;m++)
						{
							try
							{
								pow+=EnergyFormatted(PowerProfileNotification.get(m*6+7),pos);
							}
							
							catch (ClassNotFoundException e1){e1.printStackTrace();}
							catch (InstantiationException e1){e1.printStackTrace();}
							catch (IllegalAccessException e1){e1.printStackTrace();}
							catch (NoSuchFieldException e1){e1.printStackTrace();}
							catch (Exception e1){e1.printStackTrace();}
						}
					}
							
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			}
		}
		return pow;
	}
	
	/** This function returns the instant consumption of the appliance in the position pos,
	 *  notice that this function works only on the appliances that support the SimpleMeteringServer cluster
	 * 
	 * @param pos the appliance position
	 * @return a double that indicates the energy consumption of the appliance
	 */
	
	public double getInstantConsume(int pos)		// not used
	{
		double pow=0.0;
		IEndPoint[] eps=appliances.get(pos).getEndPoints();
		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.metering.SimpleMeteringServer"))
				{
					try
					{
						pow=getConsume(appliances.get(pos), ep, cluster);
						double multiplier=getMultiplier(appliances.get(pos), ep, cluster);
						double divisor=getDivisor(appliances.get(pos), ep, cluster);
						pow=pow*multiplier/divisor;
					}

					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			}
		}
		return pow;
	}
	
	// Scheduling
	
	/** This function check if the appliance has turned on the Energy Remote control,
	 *  notice that this function works only on the appliances that support the PowerProfileServer cluster
	 * 
	 * @param pos an integer that indicates the appliance position
	 * @return true if the appliance has the Energy Remote control, false otherwise
	 */
	
	public boolean GetEnergyRemote(int pos)
	{
		boolean flag=false;
		IEndPoint[] eps=appliances.get(pos).getEndPoints();
		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.PowerProfileServer"))
				{
					try
					{
						Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"getEnergyRemote", new String[0], appliancesProxy.getRequestContext(true));
						
						flag=(Boolean) appliancesProxy.invokeClusterMethod(appliances.get(pos).getPid(),
								ep.getId(), 
								cluster.getName(),
								"getEnergyRemote",
								objectParams);
						
						return flag;
					}
							
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			}
		}
		return flag;
	}
	
	/** This function set the cheapest schedule on the appliance
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void SetScheduleCheapest(int pos)		// not used
	{
		
		IEndPoint[] eps=appliances.get(pos).getEndPoints();
		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.PowerProfileServer"))
				{
					try
					{
						String[] stringa={"1"};
						Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"setScheduleMode", stringa , appliancesProxy.getRequestContext(true));
						
						appliancesProxy.invokeClusterMethod(appliances.get(pos).getPid(),
								ep.getId(), 
								cluster.getName(),
								"setScheduleMode",
								objectParams);
					}
							
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			}
		}	
	}
	
	/** This function set the greenest schedule on the appliance
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void SetScheduleGreenest(int pos)		// not used
	{
		
		IEndPoint[] eps=appliances.get(pos).getEndPoints();
		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.PowerProfileServer"))
				{
					try
					{
						String[] stringa={"3"};
						Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"setScheduleMode", stringa , appliancesProxy.getRequestContext(true));
						
						appliancesProxy.invokeClusterMethod(appliances.get(pos).getPid(),
								ep.getId(), 
								cluster.getName(),
								"setScheduleMode",
								objectParams);
					}
							
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			}
		}	
	}

	// PV forecast
	
	/** This function returns an array with the values of the PhotoVoltaic forecast in kWh and are stored in the global variable forecastPV,
	 *  notice that is not a void function because I need to recall it from the interface AppliancesSimpleService
	 * 
	 * @return An array with the values of the PV forecast
	 */
	
	public ArrayList<Double> getPVForecast()
	{
		ArrayList<Double> forecast = new ArrayList<Double>();
		try 
		{
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			Calendar cal = new GregorianCalendar();
			if(cal.get(Calendar.HOUR_OF_DAY)<11)
		        cal.add(Calendar.DAY_OF_MONTH, -1);
			String date = format.format(cal.getTime());
            // Create SOAP Connection
            SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
            SOAPConnection soapConnection = soapConnectionFactory.createConnection();
            // Send SOAP Message to SOAP Server
            String url = "http://ws.i-em.eu/v4/iem.asmx";
            SOAPMessage soapResponse = soapConnection.call(createSOAPRequest(date), url);
            // Process the SOAP Response
            double[] val = getValuesFromSOAPResponse(soapResponse);
            if(val!=null)
            {
            	if(val.length>0){forecast = new ArrayList<Double>();}
                for(int i=0; i< val.length; i++)
                {
                	if(Double.isNaN(val[i]))val[i] = 0;
                	forecast.add(val[i]);
                }
            }
            soapConnection.close();
           
        } catch (Exception e) {} 
		
		return forecast;
    }
	
	// On/Off
	
	/** This function invoke a cluster method that turn on the appliance,
	 *  notice that this function works only on the appliances that support the OnOffServer cluster
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private void execOn(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception 
	{
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"execOn", new String[0], appliancesProxy.getRequestContext(true));
		
		appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"execOn",
				objectParams);
	}
	
	/** This function invoke a cluster method that turn off the appliance,
	 *  notice that this function works only on the appliances that support the OnOffServer cluster
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private void execOff(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception 
	{
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"execOff", new String[0], appliancesProxy.getRequestContext(true));
		
		appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"execOff",
				objectParams);
	}

	/** This function invoke a cluster method that turn on the appliance,
	 *  notice that this function works only on the appliances that support the ApplianceControlServer cluster, 
	 *	also notice that the Energy Remote control must be activate
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	public void applianceStart(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception
	{
		String[] stringa={"1"};
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"execCommandExecution", stringa , appliancesProxy.getRequestContext(true));
		
		appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"execCommandExecution",
				objectParams);

	}
	
	/** This function invoke a cluster method that turn off the appliance,
	 *  notice that this function works only on the appliances that support the ApplianceControlServer cluster, 
	 *	also notice that the Energy Remote control must be activate
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	public void applianceStop(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception
	{
		String[] stringa={"2"};
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"execCommandExecution", stringa , appliancesProxy.getRequestContext(true));
		
		appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"execCommandExecution",
				objectParams);
	}
	
	/** This function invoke a cluster method that pause the appliance,
	 *  notice that this function works only on the appliances that support the ApplianceControlServer cluster, 
	 *	also notice that the Energy Remote control must be activate
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	public void appliancePause(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception
	{
		String[] stringa={"3"};
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"execCommandExecution", stringa , appliancesProxy.getRequestContext(true));
		
		appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"execCommandExecution",
				objectParams);

	}
	
	/** This function invoke a cluster method that force the appliance to start an Overload Warning
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private void execOverloadWarningStart(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception 
	{
		String[] stringa={"0"};
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"execOverloadWarning", stringa, appliancesProxy.getRequestContext(true));
		
		appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"execOverloadWarning",
				objectParams);
	}
	
	/** This function invoke a cluster method that force the appliance to stop an Overload Warning
	 * 
	 * @param appliance the appliance
	 * @param ep the endpoint
	 * @param cluster the cluster
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 * @throws Exception
	 */
	
	private void execOverloadWarningStop(IAppliance appliance, IEndPoint ep,IServiceCluster cluster) throws ClassNotFoundException,InstantiationException, IllegalAccessException,NoSuchFieldException, Exception 
	{
		String[] stringa={"2"};
		Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"execOverloadWarning", stringa, appliancesProxy.getRequestContext(true));
		
		appliancesProxy.invokeClusterMethod(appliance.getPid(),
				ep.getId(), 
				cluster.getName(),
				"execOverloadWarning",
				objectParams);
	}
	
	/** This function force the appliance in the position posDisp to start
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void ExecOn(int posDisp)
	{
		getAppliances();
		IEndPoint[] eps= appliances.get(posDisp).getEndPoints();

		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.general.OnOffServer"))
				{
					try{execOn(appliances.get(posDisp), ep, cluster);}
				
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}else if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.ApplianceControlServer"))
				{
					try{applianceStart(appliances.get(posDisp), ep, cluster);}
							
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			} 
		}
	}
	
	/** This function force the appliance in the position posDisp to stop
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void ExecOff(int posDisp)
	{
		getAppliances();
		IEndPoint[] eps= appliances.get(posDisp).getEndPoints();

		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.general.OnOffServer"))
				{
					try{execOff(appliances.get(posDisp), ep, cluster);}
				
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}else if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.ApplianceControlServer"))
				{
					try{applianceStop(appliances.get(posDisp), ep, cluster);}
							
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			}
		}
	}
	
	/** This function force the appliance in the position posDisp to start an Overload Warning,
	 *  notice that this function works only on appliances that support the ApplianceControlServer cluster
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void ExecOverloadWarningStart(int posDisp)
	{
		getAppliances();
		
		IEndPoint[] eps= appliances.get(posDisp).getEndPoints();

		for(int e=0;e<eps.length;e++)
		{
			
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.ApplianceControlServer"))
				{
					try{execOverloadWarningStart(appliances.get(posDisp), ep, cluster);}
				
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			} 
		}
	}
	
	/** This function force the appliance in the position posDisp to stop an Overload Warning,
	 *  notice that this function works only on appliances that support the ApplianceControlServer cluster
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void ExecOverloadWarningStop(int posDisp)
	{
		getAppliances();
		IEndPoint[] eps= appliances.get(posDisp).getEndPoints();

		for(int e=0;e<eps.length;e++)
		{
			
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.ApplianceControlServer"))
				{
					try{execOverloadWarningStop(appliances.get(posDisp), ep, cluster);}
				
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			} 
		}
	}
	
	/** This function check if the appliance in the position pos is turned on 
	 * 
	 * @param pos the appliance position
	 * @return true if the appliance is turned on, false otherwise
	 */
	
	public boolean isOn(int pos)
	{
		boolean flag=false;
		IEndPoint[] eps=appliances.get(pos).getEndPoints();
		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.general.OnOffServer"))
				{
					try
					{
						Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"getOnOff", new String[0], appliancesProxy.getRequestContext(true));
						
						flag=(Boolean)appliancesProxy.invokeClusterMethod(appliances.get(pos).getPid(),
								ep.getId(), 
								cluster.getName(),
								"getOnOff",
								objectParams);
					}
							
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}else if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.eh.ApplianceControlServer"))
				{
					try
					{
					Object[] objectParams = TextConverter.getObjectParameters(Class.forName(cluster.getName()),"execSignalState", new String[0], appliancesProxy.getRequestContext(true));
					
					SignalStateResponse stato= (SignalStateResponse) appliancesProxy.invokeClusterMethod(appliances.get(pos).getPid(),
							ep.getId(), 
							cluster.getName(),
							"execSignalState",
							objectParams);
					
					int statusAppliance=stato.ApplianceStatus;
					
					if(statusAppliance==4){flag=true;}
					else if(statusAppliance==3 || statusAppliance==6){flag=false;}
					}
					
					catch (ClassNotFoundException e1){e1.printStackTrace();}
					catch (InstantiationException e1){e1.printStackTrace();}
					catch (IllegalAccessException e1){e1.printStackTrace();}
					catch (NoSuchFieldException e1){e1.printStackTrace();}
					catch (Exception e1){e1.printStackTrace();}
				}
			}
		}
		
		return flag;
	}
	
	/** This function check all the appliances start and stop times and turn them on or off if they match the right time */
	
	public void checkOn()
	{
		getAppliances();
		
		ArrayList<ArrayList<Calendar>> calendars=new ArrayList<ArrayList<Calendar>>();
		
		while(dispTime.size()<appliances.size()){dispTime.add(new ArrayList<Time>());}
		
		while(calendars.size()<appliances.size()){calendars.add(new ArrayList<Calendar>());}
		
		Calendar cal=Calendar.getInstance();
		Time now=new Time(cal.get(Calendar.HOUR),cal.get(Calendar.MINUTE),cal.get(Calendar.AM_PM));
		
		for(int posDisp=0;posDisp<appliances.size();posDisp++)
		{
			while(calendars.get(posDisp).size()<dispTime.get(posDisp).size()){calendars.get(posDisp).add(Calendar.getInstance());}
			
			if(!isOn(posDisp)) // check if the appliance is turned on
			{
				System.out.println(dispTime.get(posDisp).size());
				for(int pos=0;pos<dispTime.get(posDisp).size()/2;pos++)
				{
					int durataStart=(dispTime.get(posDisp).get(2*pos).getHours()-now.getHours())*60+(dispTime.get(posDisp).get(2*pos).getMinutes()-now.getMinutes());
					calendars.get(posDisp).get(2*pos).add(Calendar.MINUTE,durataStart);
					int durataStop=(dispTime.get(posDisp).get((2*pos)+1).getHours()-now.getHours())*60+(dispTime.get(posDisp).get((2*pos)+1).getMinutes()-now.getMinutes());
					calendars.get(posDisp).get((2*pos)+1).add(Calendar.MINUTE,durataStop);
					
					if(durataStart<=0 && durataStop>0){ExecOn(posDisp);break;}
				}
			}
			else
			{
				boolean flag=false;
				for(int pos=0;pos<dispTime.get(posDisp).size()/2;pos++)
				{
					int durataStart=(dispTime.get(posDisp).get(2*pos).getHours()-now.getHours())*60+(dispTime.get(posDisp).get(2*pos).getMinutes()-now.getMinutes());
					calendars.get(posDisp).get(2*pos).add(Calendar.MINUTE,durataStart);
					int durataStop=(dispTime.get(posDisp).get((2*pos)+1).getHours()-now.getHours())*60+(dispTime.get(posDisp).get((2*pos)+1).getMinutes()-now.getMinutes());
					calendars.get(posDisp).get((2*pos)+1).add(Calendar.MINUTE,durataStop);
					
					if(durataStart<=0 && durataStop>0){flag=true;break;}
				}
				if(!flag){ExecOff(posDisp);}
			}
		}
	}

	/** This function basically calls checkOn() but is usefull in the javascript scheduler to re-initialize the page */
	
	public void refresh()
	{
		for(int j=0;j<dispTime.size();j++){if(!getReadonly(j))ExecOff(j);}
		
		dispTime= new ArrayList<ArrayList<Time>>();
		checkOn();
	}
	
	/** This function check if the appliance in the position posDisp supports the Power Profile cluster
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @return true if the appliance supports the Power Profile cluster, false otherwise
	 */
	
	public boolean getReadonly(int posDisp)
	{
		boolean readonly=true;
		
		IEndPoint[] eps= appliances.get(posDisp).getEndPoints();

		for(int e=0;e<eps.length;e++)
		{
			IEndPoint ep=eps[e];
			IServiceCluster[] clusters= ep.getServiceClusters();
			for(int c=0;c<clusters.length;c++)
			{
				IServiceCluster cluster=clusters[c];
				if(cluster.getName().equals("org.energy_home.jemma.ah.cluster.zigbee.general.OnOffServer"))
				{
					readonly=false;
				}
			}
		}
		
		return readonly;
	}

	// Bind/Unbind for the instance of the appliancesProxy interface
	
	public void bindIAppliancesProxy(IAppliancesProxy appliancesProxy){this.appliancesProxy=appliancesProxy;}

	public void unbindIAppliancesProxy(IAppliancesProxy appliancesProxy){this.appliancesProxy=null;}
	
	// SOAP for the PV forecast
	
	private static SOAPMessage createSOAPRequest(String date) throws Exception 
	{
		
		MessageFactory messageFactory = MessageFactory.newInstance();
	    SOAPMessage soapMessage = messageFactory.createMessage();
	    SOAPPart soapPart = soapMessage.getSOAPPart();

	    String serverURI = "http://ws.i-em.eu/v4/";

	    // SOAP Envelope
	    SOAPEnvelope envelope = soapPart.getEnvelope();
	    envelope.addNamespaceDeclaration("example", serverURI);

	    // SOAP Body
	    SOAPBody soapBody = envelope.getBody();
	    SOAPElement soapBodyElem = soapBody.addChildElement("Get72hPlantForecast", "example");
	    SOAPElement soapBodyElem1 = soapBodyElem.addChildElement("plantID", "example");
	    soapBodyElem1.addTextNode("telecom_02");
	    SOAPElement soapBodyElem2 = soapBodyElem.addChildElement("quantityID", "example");
	    soapBodyElem2.addTextNode("frc_pac");
	    SOAPElement soapBodyElem3 = soapBodyElem.addChildElement("timestamp", "example");
	    soapBodyElem3.addTextNode(date);
	    SOAPElement soapBodyElem4 = soapBodyElem.addChildElement("langID", "example");
	    soapBodyElem4.addTextNode("en");

	    MimeHeaders headers = soapMessage.getMimeHeaders();
	    headers.addHeader("SOAPAction", serverURI  + "Get72hPlantForecast");

	    soapMessage.saveChanges();

	    return soapMessage;
	 }
		
	private double[] getValuesFromSOAPResponse(SOAPMessage soapResponse) throws Exception 
	{
    	NodeList list = soapResponse.getSOAPPart().getElementsByTagName("values");
        if(list!=null)
        {
        	
        	list = list.item(0).getChildNodes();
        	double[] frc_values = new double[24];
            for(int i=0; i< 24; i++)
            {
            	Node node = list.item(i);
            	Node value = node.getLastChild();
            	frc_values[i] = Double.parseDouble(value.getTextContent());
            }
            return frc_values;
        } 
        else{return null;}
    }

}