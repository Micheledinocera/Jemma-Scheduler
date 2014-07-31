package it.ismb.jemma.scheduler.util;

public class Appliance 
{
	private String description;
	private boolean readonly;
	
	/** Class costructor
	 * 
	 * @param description the name of the appliance
	 * @param readonly a parameter that indicates if the appliance support the PowerProfile cluster
	 */
	
	public Appliance(String description, boolean readonly)
	{
		this.description=description;
		this.readonly=readonly;
	}
	
	// getters and setters
	
	public String getDescription() {return description;}

	public void setDescription(String description) {this.description = description;}

	public boolean isReadonly() {return readonly;}

	public void setReadonly(boolean readonly) {this.readonly = readonly;}
}
