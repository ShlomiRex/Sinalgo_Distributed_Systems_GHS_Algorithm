package projects.matala12.nodes.messages;

import sinalgo.nodes.messages.Message;

/**
 * A message sent to children that should be marked.
 */
public class MarkMessage extends Message {

	@Override
	public Message clone() {
		return this; // read-only policy 
	}

}
