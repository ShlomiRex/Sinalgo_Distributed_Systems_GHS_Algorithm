package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

/**
 * Wrapper class for broadcasting any message across the fragment
 * @author Shlomi Domnenko
 *
 */
public class FragmentBroadcastMsg extends Message {
	
	private final int originalSenderId;
	private final int fragmentId;
	private final Message msg;
	private final int broadcastId;
	
	/**
	 * 
	 * @param originalSenderId The node that originally broadcasted
	 * @param fragmentId Only broadcast to nodes with this fragmentId
	 * @param msg What message to send
	 * @param broadcastId The broadcast id - used to prevent sending the same message over and over again.
	 */
	public FragmentBroadcastMsg(int originalSenderId, int fragmentId, Message msg, int broadcastId) {
		this.originalSenderId = originalSenderId;
		this.fragmentId = fragmentId;
		this.msg = msg;
		this.broadcastId = broadcastId;
	}
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	public int getFragmentId() {
		return fragmentId;
	}
	
	// Unwrap the message
	public Message getMessage() {
		return msg;
	}
	
	public int getOriginalSenderId() {
		return originalSenderId;
	}
	
	public int getBroadcastId() {
		return broadcastId;
	}
	
	@Override
	public String toString() {
		return "Broadcast(Fragment ID: "+fragmentId+", Message: "+msg+")";
	}
}
