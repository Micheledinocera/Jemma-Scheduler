package it.ismb.jemma.scheduler.util;

import java.util.ArrayList;
import java.util.List;

import org.energy_home.jemma.ah.hac.IAppliance;

public interface AppliancesSimpleService 
{
	// Appliances
	
	/** This function return the list of appliances
	 * 
	 * @return the list of the installed appliances in the web console
	 */
	
	public List<IAppliance> getAppliances();
	
	//Time
	
	/** This function return the Time of start and stop setted by an appliance that support the power profile server
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @return the time of start and stop (a list of 2 elements of type Time)
	 */
	
	public Time[] getTime(int posDisp);
	
	/** This function add a start and a stop time to the appliance in the position posDisp, 
	 * these times are stored in the global variable dispTime in AppliancesSimpleServiceImpl
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @param ST a Time that indicates the start time of the appliance
	 * @param ET a Time that indicates the end time of the appliance
	 */
	
	public void addTime(int posDisp, Time ST, Time ET);
	
	/** This function remove the start and stop time to the appliance in the position posDisp, 
	 *  these times are removed from the array dispTime[posDisp] at the position 2*pos and 2*pos+1
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @param pos an integer that indicates the times position
	 */
	
	public void removeTime(int posDisp, int pos);
	
	/** This function change the start and stop time to the appliance in the position posDisp, 
	 *  these times are changes from the array dispTime[posDisp] at the position 2*pos and 2*pos+1
	 *  and are replaced with the times ST (Start Time) and ET (End Time) 
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @param pos an integer that indicates the times position
	 * @param ST a Time that indicates the start time of the appliance
	 * @param ET a Time that indicates the end time of the appliance
	 */
	
	public void changeTime(int posDisp, int pos, Time ST, Time ET);
	
	// On/off
	
	/** This function check if the appliance in the position posDisp is turned on
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @return true if the appliance is turned on, false otherwise
	 */
	
	public boolean isOn(int posDisp);
	
	/** This function initialize some global arrays and then call the function checkOn */
	
	public void refresh();
	
	/** This function check if the appliance in the position posDisp supports the Power Profile cluster
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @return true if the appliance supports the Power Profile cluster, false otherwise
	 */
	
	public boolean getReadonly(int posDisp);
	
	/** This function check all the appliances start and stop times and turn them on or off if they match the right time */
	
	public void checkOn();
	
	/** This function turn on the appliance in the position posDisp
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void ExecOn(int posDisp);
	
	/** This function turn off the appliance in the position posDisp
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void ExecOff(int posDisp);
	
	/** This function force the appliance in the position posDisp to start an Overload Warning
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void ExecOverloadWarningStart(int posDisp);
	
	/** This function force the appliance in the position posDisp to stop an Overload Warning
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void ExecOverloadWarningStop(int posDisp);
	
	//Consume
	
	/** This function give the power consumption of the appliance in the position posDisp,
	 *  the appliance must support the On/off cluster
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @return a double with that indicates the appliance power consumption
	 */
	
	public double getSummation(int posDisp);
	
	/** This function give the power consumption of the appliance in the position posDisp,
	 *  the appliance must support the PowerProfile cluster
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @return a double with that indicates the appliance power consumption
	 */
	
	public double getPower(int posDisp);
	
	/** This function give the instant consume of the appliance in the position posDisp,
	 *  the appliance must support the On/off cluster
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @return a double with that indicates the appliance instant consume
	 */
	
	public double getInstantConsume(int posDisp); // not used
	
	//Scheduling
	
	/** This function check if the appliance has turned on the Energy Remote control
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 * @return true if the appliance has the Energy Remote control, false otherwise
	 */
	
	public boolean GetEnergyRemote(int posDisp);
	
	/** This function set the cheapest schedule on the appliance
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void SetScheduleCheapest(int posDisp); // not used
	
	/** This function set the greenest schedule on the appliance
	 * 
	 * @param posDisp an integer that indicates the appliance position
	 */
	
	public void SetScheduleGreenest(int posDisp); // not used
	
	//PV Forecast
	
	/** This function returns an array with the values of the PhotoVoltaic forecast in kWh
	 * 
	 * @return An array with the values of the PV forecast
	 */
	
	public ArrayList<Double> getPVForecast();
	
}
