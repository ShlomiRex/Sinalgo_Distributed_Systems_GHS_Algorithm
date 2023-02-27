package projects.matala15.nodes.nodeImplementations;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

import projects.matala15.Pair;
import projects.matala15.nodes.edges.WeightedEdge;
import projects.matala15.nodes.messages.MWOEMessage;
import sinalgo.configuration.WrongConfigurationException;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.Node.NodePopupMethod;
import sinalgo.nodes.edges.Edge;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import sinalgo.tools.logging.Logging;

/**
 * An internal node (or leaf node) of the tree. 
 */
public class BasicNode extends Node {
	
	Logging logger = Logging.getLogger();
	
	List<BasicNode> neighbors = new ArrayList<>(); // List of neighbor nodes.
	boolean isServer = false; // Only 1 node in the graph is server, and is chosen at round=0 only.
	WeightedEdge mwoe = null; // Current Minimum Weight Outgoing Edge
	BasicNode mstParent = null; // Current Minimum Spanning Tree parent
	int fragmentId = ID; // The fragment identifier (for GUI so its easier to debug)
	int fragmentLeaderId = ID; // The fragment leader (id)
	
	public void addNighbor(BasicNode other) {
		neighbors.add(other);
	}
	
	public List<BasicNode> getNeighbors() {
		return neighbors;
	}
	
	public boolean isConnectedTo(BasicNode other) {
		return neighbors.contains(other);
	}
	
	/**
	 * Minimum Weight Outgoing Edge
	 */
	private WeightedEdge getMWOE() {
		WeightedEdge mwoe = null;
		for(Edge e : outgoingConnections) {
			WeightedEdge edge = (WeightedEdge) e;
			long weight = edge.getWeight();
			
			// Update new MWOE
			if (mwoe == null || weight < mwoe.getWeight()) {
				mwoe = edge;
			}
		}
		return mwoe;
	}
	
	public int getFragmentId() {
		return fragmentId;
	}
	
	@Override
	public void checkRequirements() throws WrongConfigurationException {
	}

	@Override
	public void handleMessages(Inbox inbox) {
		while(inbox.hasNext()) {
			Message m = inbox.next();
			BasicNode sender = (BasicNode) inbox.getSender();
			if (m instanceof MWOEMessage) {
				MWOEMessage msg = (MWOEMessage) m;
				logger.logln(this.ID + " got message: " + msg + " from: " + sender.ID);
				if (mwoe.getWeight() == msg.weight) {
					// Both nodes chosen the same edge to be MWOE
					// Only one becomes leader, by higher ID
					if (ID > sender.ID) {
						fragmentLeaderId = ID;
					} else {
						fragmentLeaderId = sender.ID;
						fragmentId = sender.getFragmentId();
					}
				}
			}
			
		}
	}

	@Override
	public void init() {
		
	}

	@Override
	public void neighborhoodChange() {

	}

	@Override
	public void preStep() {
		// Get MWOE
		mwoe = getMWOE();
		
		// Broadcast
		Message message = new MWOEMessage(mwoe.getWeight());
		broadcast(message);
		
		// Set MST parent
		logger.logln("Node " + mwoe.endNode.ID + " becomes parent of node " + ID);
		mstParent = (BasicNode) mwoe.endNode;
		mwoe.setIsDrawDirected(true);
	}

	@Override
	public void postStep() {
	}
	
	@Override
	public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
		if (isServer) {
			setColor(Color.YELLOW);
		}
		
		int fontSize = 22;
		this.drawNodeAsDiskWithText(g, pt, highlight, ""+ID, fontSize, Color.WHITE);
		
		// Draw fragment ID above the node
		String fragmentIdString = ""+fragmentId;
		
		// Source taken from 'Node.drawNodeAsDiskWithText()'
		Font font = new Font(null, 0, (int) (fontSize * pt.getZoomFactor())); 
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics(font); 
		int h = (int) Math.ceil(fm.getHeight());
		int w = (int) Math.ceil(fm.stringWidth(fragmentIdString));
		
		g.setColor(Color.RED);
		int yOffset = (int) (fontSize * pt.getZoomFactor()) * -2;
		g.drawString(fragmentIdString, pt.guiX - w/2, pt.guiY + h/2 + yOffset);
	}
	
	@Override
	public String toString() {
		if (mwoe == null) {
			return "BasicNode("+this.ID+", Fragment: " + fragmentId + ")";	
		} else {
			return "BasicNode("+this.ID+", Fragment: " + fragmentId + ", MWOE:" + mwoe + ")";	
		}
	}
	
	@NodePopupMethod(menuText="Select as a server")
	public void myPopupMethod() {
		isServer = true;
		logger.logln("Setting node " + this + " as server");
	}
}
