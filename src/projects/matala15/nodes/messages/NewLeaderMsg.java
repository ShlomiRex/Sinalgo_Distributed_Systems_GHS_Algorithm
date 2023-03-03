package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

public class NewLeaderMsg extends Message {

	private final int new_leader_id;
	
	public NewLeaderMsg(int id) {
		new_leader_id = id;
	}
	
	public int getNewLeaderId() {
		return new_leader_id;
	}
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	@Override
	public String toString() {
		return "NewLeaderMsg("+new_leader_id+")";
	}
}
