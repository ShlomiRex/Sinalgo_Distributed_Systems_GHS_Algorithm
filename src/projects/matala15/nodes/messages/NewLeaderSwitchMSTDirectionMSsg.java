package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

/**
 * Used in phase 7 to switch direction of MST.
 * @author Shlomi Domnenko
 *
 */
public class NewLeaderSwitchMSTDirectionMSsg extends Message {
	private final int newLeaderId;
	
	public NewLeaderSwitchMSTDirectionMSsg(int newLeaderId) {
		this.newLeaderId = newLeaderId;
	}
	
	public int getNewLeaderId() {
		return newLeaderId;
	}
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	@Override
	public String toString() {
		return "NewLeaderSwitchMSTDirectionMSsg("+newLeaderId+")";
	}
}
