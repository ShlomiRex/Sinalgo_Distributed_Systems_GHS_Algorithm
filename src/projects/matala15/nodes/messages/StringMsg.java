package projects.matala15.nodes.messages;

import java.util.Stack;

import sinalgo.nodes.messages.Message;

public class StringMsg extends Message {
	
	private final String msg;
	private final int originalSenderId;
	private final boolean isDestinationIsServer;
	private final Stack<Integer> intermediate_nodes = new Stack<>();
	
	public StringMsg(String msg, int originalSenderId, boolean isDestinationIsServer) {
		this.msg = msg;
		this.originalSenderId = originalSenderId;
		this.isDestinationIsServer = isDestinationIsServer;
	}
	
	public String getMessage() {
		return msg;
	}
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	public void addIntermediateNode(int id) {
		intermediate_nodes.push(id);
	}
	
	public Stack<Integer> getIntermediateNodes() {
		return intermediate_nodes;
	}
	
	public int getOriginalSenderId() {
		return originalSenderId;
	}
	
	public boolean isDestinationIsServer() {
		return isDestinationIsServer;
	}
	
	@Override
	public String toString() {
		return "StringMsg(\""+msg+"\", Path: "+intermediate_nodes+")";
	}
}
