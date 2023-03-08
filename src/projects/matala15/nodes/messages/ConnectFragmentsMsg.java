package projects.matala15.nodes.messages;

import sinalgo.nodes.messages.Message;

/**
 * Message is used to notify node X that node Y tries to connect to it (and basically combine both fragments).
 * @author Shlomi Domnenko
 *
 */
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
