package projects.matala12.nodes.messages.Vcol_MIS;

import sinalgo.nodes.messages.Message;

public class RoundMessage extends Message {
	private int col = 0;
	
	public RoundMessage(int col) {
		this.col = col;
	}
	
	public int getColor() {
		return this.col;
	}

	@Override
	public Message clone() {
		return this; // read-only policy 
	}

	@Override
	public String toString() {
		return "Round(" + this.col+")";
	}
	
}
