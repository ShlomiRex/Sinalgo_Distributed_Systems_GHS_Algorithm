package projects.matala15.nodes.nodeImplementations;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

import projects.matala15.Pair;
import projects.matala15.nodes.edges.WeightedEdge;
import projects.matala15.nodes.messages.ConnectMsg;
import projects.matala15.nodes.messages.MWOEMsg;
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
	
	private Logging logger = Logging.getLogger();
	
	private List<BasicNode> neighbors = new ArrayList<>(); // List of neighbor nodes.
	private boolean isServer = false; // Only 1 node in the graph is server, and is chosen at round=0 only.
	private WeightedEdge mwoe = null; // Current Minimum Weight Outgoing Edge
	private BasicNode mstParent = null; // Current Minimum Spanning Tree parent
	private int fragmentId = ID; // The fragment identifier (for GUI so its easier to debug)
	private int fragmentLeaderId = ID; // The fragment leader (id)
	private int roundNum = 0; // The round number (we are in synchronized model so its allowed)
	
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
	 * Find and return the Minimum Weight Outgoing Edge
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
			if (m instanceof MWOEMsg) {
				MWOEMsg msg = (MWOEMsg) m;
				logger.logln(this.ID + " got message: " + msg + " from: " + sender.ID);
				if (mwoe.getWeight() == msg.weight) {
					// Both nodes chosen the same edge to be MWOE
					// Only one becomes leader, by higher ID
					if (ID > sender.ID) {
						fragmentLeaderId = ID;
					} else {
						// Send connect message
						ConnectMsg connectMsg = new ConnectMsg();
						send(connectMsg, sender);
						
						// Combine fragments
						fragmentLeaderId = sender.ID;
						fragmentId = sender.getFragmentId();
					}
				}
			} else if (m instanceof ConnectMsg) {
				ConnectMsg msg = (ConnectMsg) m;
				logger.logln(this.ID + " got message: " + msg + " from: " + sender.ID);
			} else {
				logger.logln("ERROR: Got invalid message: " + m);
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
		if (roundNum == 0) {
			// Get MWOE
			mwoe = getMWOE();
			
			// Broadcast
			Message message = new MWOEMsg(mwoe.getWeight());
			broadcast(message);
		}

//		// Set MST parent
//		logger.logln("Node " + mwoe.endNode.ID + " becomes parent of node " + ID);
//		mstParent = (BasicNode) mwoe.endNode;
//		mwoe.setIsDrawDirected(true);
	}

	@Override
	public void postStep() {
		roundNum += 1;
	}
	
	/**
	 * Draw fragment ID above the node
	 * Returns the width, height according to zoom factor, font size and more.
	 */
	private Pair<Integer, Integer> drawFragmentId(Graphics g, PositionTransformation pt, int fontSize) {
		g.setColor(Color.MAGENTA);
		String fragmentIdString = ""+fragmentId;
		
		// Source taken from 'Node.drawNodeAsDiskWithText()'
		Font font = new Font(null, 0, (int) (fontSize * pt.getZoomFactor())); 
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics(font); 
		int h = (int) Math.ceil(fm.getHeight());
		int w = (int) Math.ceil(fm.stringWidth(fragmentIdString));
		
		int yOffset = (int) (fontSize * pt.getZoomFactor()) * -2;
		g.drawString(fragmentIdString, pt.guiX - w/2, pt.guiY + h/2 + yOffset);
		
		return new Pair<Integer, Integer>(w, h);
	}
	
	/**
	 * Draw indicator below the node that this node is a server
	 */
	private void drawAsServerNode(Graphics g, PositionTransformation pt, int width, int height) {
		int d = Math.max(width, height);
		
		g.setColor(Color.YELLOW);
		g.fillRect(pt.guiX - width, pt.guiY + height, d, d);
		
		g.setColor(Color.BLACK);
		g.drawRect(pt.guiX - width, pt.guiY + height, d, d);
	}
	
	@Override
	public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
		int fontSize = 22;
		
		// Highlight the node if its the leader in the fragment
		highlight = (fragmentLeaderId == ID);
		
		// Draw the node
		this.drawNodeAsDiskWithText(g, pt, highlight, ""+ID, fontSize, Color.WHITE);
		
		// Draw fragment ID above the node
		Pair<Integer, Integer> widthHeight = drawFragmentId(g, pt, fontSize);
		
		// If node is server, add indicator (yellow rectangle) below the node
		if (isServer)
			drawAsServerNode(g, pt, widthHeight.getA(), widthHeight.getB());
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("BasicNode(").append(this.ID).append(", Fragment: ").append(fragmentId);
		
		if (isServer) {
			builder.append(", Server Node");
		} else {
			if (mwoe != null) {
				builder.append(", MWOE: ").append(mwoe);
			}
		}
		builder.append(")");
		return builder.toString();
	}
	
	@NodePopupMethod(menuText="Select as a server")
	public void myPopupMethod() {
		isServer = true;
		logger.logln("Setting node " + this + " as server");
	}
}
