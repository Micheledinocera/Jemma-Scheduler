package it.ismb.jemma.scheduler.util;

public class Time
{	
	private int hours;
	private int minutes;
	private int AM_PM;
	
	/** Class costructor
	 * 
	 * @param hours an integer that indicates the hours
	 * @param minutes an integer that indicates the minutes
	 * @param AM_PM an integer that indicates the AM_PM: AM=0 e PM=1
	 */
	
	public Time(int hours,int minutes,int AM_PM)
	{
		this.hours=0;
		this.AM_PM=AM_PM;
		this.minutes=minutes;
		while(this.minutes>=60){this.minutes -= 60;this.hours++;}
		if(this.minutes<0) {this.minutes+=60;this.hours--;}
		if(hours>=12)this.AM_PM=1;
		if(AM_PM==1 && hours<12) this.hours=hours+12;
		else this.hours=hours;
		if(AM_PM==1 && this.hours>=24) this.hours=hours;
	}
	
	//print on the console
	
	public void stampaOrario()				
	{
		System.out.print(this.hours+":");
		if(this.minutes>=0 && this.minutes<=9){System.out.print("0");}
		System.out.print(this.minutes);
	}
	
	// functions for Times comparison
	
	public boolean equalsTime(Time T)
	{
		if(this.hours==T.getHours() && this.minutes==T.getMinutes() && this.AM_PM==T.getAM_PM()) return true;
		return false;
	}
	
	public boolean lessTime(Time T)
	{
		if(this.hours<T.getHours() || (this.hours==T.getHours() && this.minutes<T.getMinutes())) return true;
		return false;
	}
	
	public boolean lessorequalsTime(Time T)
	{
		if(this.hours<T.getHours() || (this.hours==T.getHours() && this.minutes<=T.getMinutes())) return true;
		return false;
	}
	
	// getters and setters
	
	public int getHours(){return this.hours;}
	
	public int getMinutes(){return this.minutes;}

	public int getAM_PM(){return this.AM_PM;}
	
	public void setHours(int ore){this.hours = ore;}

	public void setMinutes(int minuti)
	{
		this.minutes=minuti;
		while(this.minutes>=60){this.minutes -= 60;this.hours++;}
		if(this.minutes<0) {this.minutes+=60;this.hours--;}
	}
	
	public void setAM_PM(int AM_PM){this.AM_PM = AM_PM;}
}
