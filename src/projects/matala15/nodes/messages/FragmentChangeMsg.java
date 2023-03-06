package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

/**
 * Used in phase 8, when combining 2 fragments, one of them should notify the entire fragment that they have new leader and new fragment id.
 * @author Shlomi
 *
 */
public class FragmentChangeMsg extends Message {
	private final int newLeaderId;
	private final int newFragmentId;
	
	public FragmentChangeMsg(int newLeaderId, int newFragmentId) {
		this.newLeaderId = newLeaderId;
		this.newFragmentId = newFragmentId;
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
	
	@Override
	public String toString() {
		return "FragmentChangeMsg(New Leader: "+newLeaderId+", New Fragment Id: "+newFragmentId+")";
	}
}
