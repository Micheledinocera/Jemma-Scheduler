package it.ismb.jemma.scheduler.web;

import it.ismb.jemma.scheduler.util.Appliance;
import it.ismb.jemma.scheduler.util.AppliancesSimpleService;
import it.ismb.jemma.scheduler.util.Time;
import it.ismb.jemma.scheduler.util.PositionTime;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.energy_home.jemma.ah.hac.IAppliance;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

import com.google.gson.Gson;

public class WebComponent extends HttpServlet
{
	private static final long serialVersionUID = 8730527558957861771L;
	
	private HttpService httpService;
	@SuppressWarnings("unused")
	private ComponentContext context;
	private AppliancesSimpleService appliancesSimpleService;
	private Gson gson=new Gson();
	
	protected void activate(ComponentContext context)
	{
		try 
		{
			httpService.registerResources("/fullcalendar", "/web/fullcalendar", null);
			this.httpService.registerServlet("/calendar", this, null, null);
		} 
		catch (NamespaceException e) {e.printStackTrace();}
		catch (ServletException e) {e.printStackTrace();}
	}
	
	// Get
	
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException 
	{
		if(req.getPathInfo().endsWith("/getEvents"))			// return the installed appliances list  						
		{
			resp.setContentType("application/json");
			
			ArrayList<IAppliance> v1= (ArrayList<IAppliance>) appliancesSimpleService.getAppliances();
			PrintWriter writer=resp.getWriter();
			
			ArrayList<Appliance> v2= new ArrayList<Appliance>();
			
			for(int i=0;i<v1.size();i++)
			{
				v2.add(new Appliance(v1.get(i).getConfiguration().get("ah.app.name").toString(), appliancesSimpleService.getReadonly(i)));
			}
			writer.write(gson.toJson(v2));
			
		}else if(req.getPathInfo().endsWith("/refresh"))		// makes a refresh to check if the appliances are turned On/off
		{
			resp.setContentType("application/json");
			appliancesSimpleService.refresh();
			
		}else if(req.getPathInfo().endsWith("/getPVForecast"))	// return the list of the PV forecast		
		{
			resp.setContentType("application/json");
			ArrayList<Double> PV=appliancesSimpleService.getPVForecast();
			PrintWriter writer=resp.getWriter();
			writer.write(gson.toJson(PV));
		}
	}
	
	// Post
	
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException 
	{
		if(req.getPathInfo().endsWith("/setTime"))										// take the start and stop time of an appliance from the GUI and make a copy in the java file	
		{	
			resp.setContentType("application/json");
			
			PositionTime o=gson.fromJson(req.getReader(), PositionTime.class);
			appliancesSimpleService.addTime(o.getPosDisp(), o.getStart(), o.getStop());
			
		}else if(req.getPathInfo().endsWith("/unSetTime"))								// delete the start and the stop of the selected appliance
		{	
			resp.setContentType("application/json");
			
			int[] o=gson.fromJson(req.getReader(), int[].class);
			
			appliancesSimpleService.removeTime(o[0], o[1]);
			
		}else if(req.getPathInfo().endsWith("/changeTime"))								// change the start and the stop of the selected appliance
		{	
			resp.setContentType("application/json");
			
			PositionTime o=gson.fromJson(req.getReader(), PositionTime.class);
			
			appliancesSimpleService.changeTime(o.getPosDisp(), o.getPos(), o.getStart(), o.getStop());
			
		}else if(req.getPathInfo().endsWith("/getTime"))								// is the same of setTime but for the appliances that supports the PowerProfile cluster
		{	
			resp.setContentType("application/json");
			
			PositionTime po=gson.fromJson(req.getReader(), PositionTime.class);
			Time[] o = appliancesSimpleService.getTime(po.getPosDisp());
			
			appliancesSimpleService.addTime(po.getPosDisp(), o[0], o[1]);
			
			PrintWriter writer=resp.getWriter();
			writer.write(gson.toJson(o));
			
		}else if(req.getPathInfo().endsWith("/getPower"))								// take the power forecast from the selected appliance 
		{	
			resp.setContentType("application/json");
			
			int pos=gson.fromJson(req.getReader(), int.class);
			
			double pow=appliancesSimpleService.getPower(pos);
			
			PrintWriter writer=resp.getWriter();
			writer.write(gson.toJson(pow));
			
		}else if(req.getPathInfo().endsWith("/getEnergyRemote"))						// check if the appliance as turned On the Energy Remote control
		{
			resp.setContentType("application/json");
			int pos=gson.fromJson(req.getReader(), int.class);
			
			boolean flag=appliancesSimpleService.GetEnergyRemote(pos);
			
			PrintWriter writer=resp.getWriter();
			writer.write(gson.toJson(flag));
			
		}else if(req.getPathInfo().endsWith("/setSchedulerCheapest"))					// set the cheapest schedule on the appliance
		{
			resp.setContentType("application/json");
			int pos=gson.fromJson(req.getReader(), int.class);
			
			appliancesSimpleService.SetScheduleCheapest(pos);
			
		}else if(req.getPathInfo().endsWith("/setSchedulerGreenest"))					// set the greenest schedule on the appliance
		{
			resp.setContentType("application/json");
			int pos=gson.fromJson(req.getReader(), int.class);
			
			appliancesSimpleService.SetScheduleGreenest(pos);
			
		}else if(req.getPathInfo().endsWith("/accensione"))								// check the appliances in order to control the start and stop times
		{	
			resp.setContentType("application/json");
			appliancesSimpleService.checkOn();
			
		}else if(req.getPathInfo().endsWith("/getConsume"))								// take the power consume from the selected appliance 
		{
			resp.setContentType("application/json");
			int pos=gson.fromJson(req.getReader(), int.class);
			double consume=0.0;
			boolean flag=appliancesSimpleService.isOn(pos);
			
			if(flag)
			{
				consume=appliancesSimpleService.getSummation(pos);
			};
			
			PrintWriter writer=resp.getWriter();
			writer.write(gson.toJson(consume));
			
		}else if(req.getPathInfo().endsWith("/ExecOn"))									// turn On the selected appliance
		{
			resp.setContentType("application/json");
			int pos=gson.fromJson(req.getReader(), int.class);
			
			appliancesSimpleService.ExecOn(pos);
				
		}else if(req.getPathInfo().endsWith("/ExecOff"))								// turn off the selected appliance
		{
			resp.setContentType("application/json");
			int pos=gson.fromJson(req.getReader(), int.class);
			
			appliancesSimpleService.ExecOff(pos);
			
		}else if(req.getPathInfo().endsWith("/isOn"))									// check if the selected appliance is turned off or on
		{
			resp.setContentType("application/json");
			int pos=gson.fromJson(req.getReader(), int.class);
			
			boolean flag=appliancesSimpleService.isOn(pos);
			
			PrintWriter writer=resp.getWriter();
			writer.write(gson.toJson(flag));
			
		}else if(req.getPathInfo().endsWith("/getReadonly"))							// check if the selected appliance support the powerprofile cluster
		{
			resp.setContentType("application/json");
			
			List<IAppliance> v= appliancesSimpleService.getAppliances();
			
			ArrayList<Boolean> flags = new ArrayList<Boolean>();
			
			while(flags.size()<v.size())
			{
				flags.add(false);
			}
			
			for(int i=0;i<v.size();i++)
			{
				flags.set(i,appliancesSimpleService.getReadonly(i));
			}
			
			PrintWriter writer=resp.getWriter();
			writer.write(gson.toJson(flags));
		}else if(req.getPathInfo().endsWith("/execOverloadWarning"))					// force the selected appliance to make an overload Warning
		{
			resp.setContentType("application/json");
			int pos=gson.fromJson(req.getReader(), int.class);
			
			appliancesSimpleService.ExecOverloadWarningStart(pos);
			
			try {Thread.sleep(2000);} 
			catch (InterruptedException e) {e.printStackTrace();}
			
			appliancesSimpleService.ExecOverloadWarningStop(pos);
			
		}
	}
	
	// Bind/Unbind
	
	public void bindHttpService(HttpService httpService){this.httpService=httpService;}
	
	public void unbindHttpService(HttpService httpService){this.httpService=null;}
	
	public void bindAppliancesSimpleService(AppliancesSimpleService appliancesSimpleService){this.appliancesSimpleService=appliancesSimpleService;}
	
	public void unbindAppliancesSimpleService(AppliancesSimpleService appliancesSimpleService){this.appliancesSimpleService=null;}
}
