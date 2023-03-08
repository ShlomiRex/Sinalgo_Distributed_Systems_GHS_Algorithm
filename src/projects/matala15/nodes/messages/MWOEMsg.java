package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

/**
 * A node sends its MWOE edge. Used in multiple phases (1, 2, 6, and more)
 * @author Shlomi Domnenko
 *
 */
public class MWOEMsg extends Message {

	public final long weight;
	
	public MWOEMsg(long weight) {
		this.weight = weight;
	}
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	@Override
	public String toString() {
		String nice_weight = String.format("%,d", weight);
		return "MWOEMsg(\""+nice_weight+"\")";
	}

}
