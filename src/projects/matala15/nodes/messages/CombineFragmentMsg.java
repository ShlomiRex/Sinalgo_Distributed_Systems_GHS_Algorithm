package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

/**
 * This message is given to children of MST leader, so they update their own fragmentId, leaderId, and more.
 * @author Shlomi Domnenko
 *
 */
public class CombineFragmentMsg extends Message {
	private final int newLeaderId;
	private final int newFragmentId;
	private final int totalNodesInFragment;
	
	public CombineFragmentMsg(int newLeaderId, int newFragmentId, int totalNodesInFragment) {
		this.newLeaderId = newLeaderId;
		this.newFragmentId = newFragmentId;
		this.totalNodesInFragment = totalNodesInFragment;
	}
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	public int getNewLeaderId() {
		return newLeaderId;
	}
	
	public int getNewFragmentId() {
		return newFragmentId;
	}
	
	public int getTotalNodesInFragment() {
		return totalNodesInFragment;
	}
	
	@Override
	public String toString() {
		return "CombineFragmentMsg(New Leader: "+newLeaderId+", New Fragment Id: "+newFragmentId+", Nodes in fragment: "+totalNodesInFragment+")";
	}
}
