package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

public class MWOEMessage extends Message {

	public final long weight;
	
	public MWOEMessage(long weight) {
		this.weight = weight;
	}
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	@Override
	public String toString() {
		String nice_weight = String.format("%,d", weight);
		return "MWOEMessage("+nice_weight+")";
	}

}
