package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

/**
 * Used in phase 3, not useful other than that.
 * @author Shlomi
 *
 */
public class FragmentIdMsg extends Message {
	private final int fragmentId;
	
	public FragmentIdMsg(int fragmentId) {
		this.fragmentId = fragmentId;
	}
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	public int getFragmentId() {
		return fragmentId;
	}
	
	@Override
	public String toString() {
		return "FragmentIdMsg("+fragmentId+")";
	}
}
