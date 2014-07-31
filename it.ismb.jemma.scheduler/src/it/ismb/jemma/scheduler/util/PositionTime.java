package it.ismb.jemma.scheduler.util;

public class PositionTime
{	
	private int posDisp;
	private int pos;
	private Time start;
	private Time stop;

	/** Class costructor
	 *  
	 * @param posDisp the position of the appliance
	 * @param pos the position of the start and stop
	 * @param start start time
	 * @param stop end time
	 */
	
	public PositionTime(int posDisp, int pos,Time start,Time stop)
	{
		this.posDisp=posDisp;
		this.pos=pos;
		this.start=start;
		this.stop=stop;
	}
	
	// getters and setters
	
	public int getPosDisp() {return posDisp;}

	public void setPosDisp(int posDisp) {this.posDisp = posDisp;}

	public int getPos() {return pos;}

	public void setPos(int pos) {this.pos = pos;}
	
	public Time getStart() {return start;}

	public void setStart(Time start) {this.start = start;}

	public Time getStop() {return stop;}

	public void setStop(Time stop) {this.stop = stop;}

	public void stampaPos(){System.out.print(pos);}
}
