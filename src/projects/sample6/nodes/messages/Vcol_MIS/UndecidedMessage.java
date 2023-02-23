package projects.sample6.nodes.messages.Vcol_MIS;

import sinalgo.nodes.messages.Message;

public class UndecidedMessage extends Message {

	@Override
	public Message clone() {
		return this; // read-only policy 
	}
	
	@Override
	public String toString() {
		return "Undecided";
	}

}
