package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

/**
 * Wrapper class for broadcasting any message across the fragment
 * @author Shlomi Domnenko
 *
 */
public class FragmentConvergecastMsg extends Message {
	
	private final int originalSenderId;
	private final Message msg;
	
	/**
	 * 
	 * @param originalSenderId The node that originally broadcasted
	 * @param msg What message to send
	 */
	public FragmentConvergecastMsg(int originalSenderId, Message msg) {
		this.originalSenderId = originalSenderId;
		this.msg = msg;
	}
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	// Unwrap the message
	public Message getMessage() {
		return msg;
	}
	
	public int getOriginalSenderId() {
		return originalSenderId;
	}
	
	@Override
	public String toString() {
		return "Convergecast(Original Sender ID: "+originalSenderId+", Message: "+msg+")";
	}
}
