package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

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
