package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

public class ConnectFragmentsMsg extends Message {
	
	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	@Override
	public String toString() {
		return "ConnectFragmentsMsg";
	}
}
