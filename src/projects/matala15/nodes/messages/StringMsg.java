package projects.matala15.nodes.messages;

import java.util.ArrayList;
import java.util.List;

import sinalgo.nodes.messages.Message;

public class StringMsg extends Message {
	
	private final String msg;
	private final List<Integer> intermediate_nodes = new ArrayList<>();
	
	public StringMsg(String msg) {
		this.msg = msg;
	}
	
	public String getMessage() {
		return msg;
	}
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	public void addIntermediateNode(int id) {
		intermediate_nodes.add(id);
	}
	
	@Override
	public String toString() {
		return "StringMsg(\""+msg+"\")";
	}
}
