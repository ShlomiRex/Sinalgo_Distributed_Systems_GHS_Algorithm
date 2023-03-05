package projects.matala15.nodes.nodeImplementations;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import projects.matala15.Pair;
import projects.matala15.nodes.edges.WeightedEdge;
import projects.matala15.nodes.messages.FragmentBroadcastMsg;
import projects.matala15.nodes.messages.FragmentConvergecastMsg;
import projects.matala15.nodes.messages.FragmentIdMsg;
import projects.matala15.nodes.messages.MWOEMsg;
import projects.matala15.nodes.messages.NewLeaderMsg;
import sinalgo.configuration.WrongConfigurationException;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.edges.Edge;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import sinalgo.tools.Tools;
import sinalgo.tools.logging.Logging;

/**
 * An internal node (or leaf node) of the tree. 
 */
public class BasicNode extends Node {
	
	private Logging logger = Logging.getLogger();
	
	private List<BasicNode> neighbors = new ArrayList<>(); // List of neighbor nodes.
	private boolean isServer = false; // Only 1 node in the graph is server, and is chosen at round=0 only.
	private WeightedEdge mwoe = null; // Current Minimum Weight Outgoing Edge
	private int fragmentId = ID; // The fragment identifier the node is currently in
	private int fragmentLeaderId = ID; // The fragment leader id (if this node is fragment leader, then fragmentLeaderId = ID)
	private int roundNum = 0; // The round number (we are in synchronized model so its allowed)
	private int broadcastId = 0; // This is for stopping broadcasting to avoid loops. The pair 'ID' and 'broadcastId' both used to distinguish a unique broadcast.
	private Map<Integer, List<Integer>> broadcast_list = new HashMap<>(); // All the broadcast message this node has sent, used to stop the broadcast loop. The key is ID of originalSenderId. Value is list of broadcast IDs.
	private BasicNode mst_parent = null; // The constructed MST
	private AlgorithmPhases currPhase;
	private enum AlgorithmPhases {
		PHASE_ONE,
		PHASE_TWO,
		PHASE_THREE,
		PHASE_FOUR_FIVE,
		PHASE_SIX,
		PHASE_SEVEN
	}
	private List<Message> convergecast_buffer = new ArrayList<>(); // Holds list of convergecast messages. Used in phase 6, where leader waits for all convergecast messages in Big-O(N) (it fills up in multiple rounds).
	
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
	 * @param checkFromOtherFragment If true, this function checks that the endNode is from other fragment (not in same fragment)
	 * @return The weighted edge.
	 */
	private WeightedEdge getMWOE(boolean checkFromOtherFragment) {
		WeightedEdge mwoe = null;
		for(Edge e : outgoingConnections) {
			WeightedEdge edge = (WeightedEdge) e;
			long weight = edge.getWeight();
			
			// Update new MWOE
			if (checkFromOtherFragment) {
				int endNodeFragmentId = ((BasicNode) edge.endNode).fragmentId;
				if ( (mwoe == null || weight < mwoe.getWeight()) && endNodeFragmentId != fragmentId) {
					mwoe = edge;
				}
			} else {
				if (mwoe == null || weight < mwoe.getWeight()) {
					mwoe = edge;
				}
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

	/**
	 * Broadcast a message to only nodes that are in the current fragment
	 * Deals with loops
	 * NOTE: Only use this ONCE per broadcast. Intermediate node should NOT use this function to re-broadcast.
	 * @param msg The message to broadcast
	 */
	private void broadcastFragment(Message msg) {
		logger.logln("Node "+ID+" broadcasts to fragment: " + msg);
		
		// Wrap the intended message inside FragmentBroadcastMsg message
		FragmentBroadcastMsg broadcastMsg = new FragmentBroadcastMsg(ID, fragmentId, msg, broadcastId);
		
		for(BasicNode n : neighbors) {
			if (n.fragmentId == fragmentId) {
				send(broadcastMsg, n);
			}
		}
		broadcastId += 1;
	}
	
	/**
	 * Check if this node already broadcasted the message
	 * @param fragmentBroadcastMsg
	 * @return True if already broadcasted
	 */
	private boolean checkAlreadyBroadcasted(FragmentBroadcastMsg fragmentBroadcastMsg) {
		// Check if we already broadcasted this message
		List<Integer> originalSenderBroadcasts = broadcast_list.get(fragmentBroadcastMsg.getOriginalSenderId());
		
		// We can't find any broadcast messages from this original sender, it means that we did not broadcasted his message, yet
		if (originalSenderBroadcasts == null)
			return false;
		else {
			// Check if this message 'broadcastId' matches with the list of broadcast messages
			int currMsgBroadcastId = fragmentBroadcastMsg.getBroadcastId();
			for (int broadcastedId : originalSenderBroadcasts) {
				// If it exists, that means we already broadcasted it
				if (broadcastedId == currMsgBroadcastId) {
					return true;
				}
			}
			// This broadcastId doesn't exists in the record, so we didn't broadcast it yet
			return false;
		}
	}
	
	/**
	 * Rebroadcast a fragment broadcast message, update the local broadcast IDs
	 * Intermediate nodes should use this function, rather than the function 'broadcastFragment'
	 * @param sender The sender of this broadcast message (might be the original sender [if direct neighbor], might not [if intermediate node])
	 * @param fragmentBroadcastMsg
	 */
	private void rebroadcast(Node sender, FragmentBroadcastMsg fragmentBroadcastMsg) {
		for(BasicNode n : neighbors) {
			if (n.fragmentId == fragmentId) {
				// Don't send back to sender
				if (n.ID != sender.ID) {
					logger.logln("Node "+ID+" re-broadcasts: "+fragmentBroadcastMsg);
					send(fragmentBroadcastMsg, n);
				}
			}
		}
		// Update local broadcast IDs to avoid loops
		int originalSenderId = fragmentBroadcastMsg.getOriginalSenderId();
		int originalSenderBroadcastId = fragmentBroadcastMsg.getBroadcastId();
		
		// If this sender ID does not exist, create new list
		if (broadcast_list.get(originalSenderId) == null)
			broadcast_list.put(originalSenderId, new ArrayList<Integer>());
		
		// Add the broadcast id to this sender id
		broadcast_list.get(originalSenderId).add(originalSenderBroadcastId);
	}
	
	/**
	 * Do convergecast of any message, with intent to send the message to the fragment leader.
	 * @param msg Any message to convergecast
	 */
	private void convergecast(int originalSenderId, Message msg) {
		logger.logln("Node "+ID+" converges the message: " + msg);
		FragmentConvergecastMsg fragmentConvergecastMsg = new FragmentConvergecastMsg(ID, msg);
		send(fragmentConvergecastMsg, mst_parent);
	}
	
	@Override
	public void handleMessages(Inbox inbox) {
		while(inbox.hasNext()) {
			Message m = inbox.next();
			BasicNode sender = (BasicNode) inbox.getSender();
			
			StringBuilder builder = new StringBuilder();
			builder.append("Node " + this.ID + " got message: ");
			
			// Unwrap broadcast message
			if (m instanceof FragmentBroadcastMsg) {
				logger.logln("Node " + ID + " got FragmentBroadcastMsg, unwrapping");
				FragmentBroadcastMsg fragmentBroadcastMsg = (FragmentBroadcastMsg) m;

				// If some miracle the message was sent by different fragment, this should never happen (its allowed but i'm harsh on this assignment)
				if (fragmentBroadcastMsg.getFragmentId() != fragmentId) {
					throw new RuntimeException("ERROR: " + this + " got fragment broadcast message for "
							+ "fragmentId="+fragmentBroadcastMsg.getFragmentId()+" which is not the intended target");
				}
				else {
					// Unwrap the message and check its instance later
					m = fragmentBroadcastMsg.getMessage();
				}
				
				boolean alreadyBroadcasted = checkAlreadyBroadcasted(fragmentBroadcastMsg);
				if (alreadyBroadcasted)
					continue;
				
				// Broadcast the message
				rebroadcast(sender, fragmentBroadcastMsg);

				// Continue handling the wrapped message
			}
			
			// Handle all known messages
			if (m instanceof MWOEMsg) {
				MWOEMsg msg = (MWOEMsg) m;
				builder.append(msg);
				if (mwoe.getWeight() == msg.weight) {
					// Both nodes chosen the same edge to be MWOE. Only one becomes leader, by higher ID
					if (ID > sender.ID) {
						// This node becomes the fragment leader
						fragmentLeaderId = ID;
						Message new_leader_msg = new NewLeaderMsg(ID);
						broadcastFragment(new_leader_msg);
					} else {
						// The other node becomes the fragment leader
						fragmentLeaderId = sender.ID;
						fragmentId = sender.fragmentId;
						
						// Set MST parent and direction to construct MST
						mst_parent = (BasicNode) mwoe.endNode;
						mwoe.setIsDrawDirected(true);
					}
				}
			} else if (m instanceof NewLeaderMsg) {
				NewLeaderMsg msg = (NewLeaderMsg) m;
				builder.append(msg);

				// Change the current leader, locally
				fragmentLeaderId = msg.getNewLeaderId();
			} else if (m instanceof FragmentIdMsg) {
				FragmentIdMsg msg = (FragmentIdMsg) m;
				builder.append(msg);
				
				// Update local neighbors fragments
				for(BasicNode n : neighbors) {
					if (n.ID == sender.ID) {
						n.fragmentId = msg.getFragmentId();
					}
				}
				
				// Start phase 4
				// Get MWOE from different fragment (can be null!)
				long previous_mwoe_edge_id = mwoe.getID();
				mwoe = getMWOE(true);
				if (mwoe == null) {
					logger.logln("Node "+ID+" has no MWOE in diffirent fragment");
				}
				else if (previous_mwoe_edge_id != mwoe.getID()) {
					// That means we have new MWOE
					logger.logln("Node "+ID+" found a new MWOE: " + mwoe);
					
					MWOEMsg mwoeMsg = new MWOEMsg(mwoe.getWeight());
					
					// Convergecast to leader
					if (mst_parent != null) {
						// Non-leader node, converge to leader
						convergecast(ID, mwoeMsg);
					} else {
						// Leader node
						// Leader adds MWOE of its own to its own convergecast_buffer
						logger.logln("Fragment "+fragmentId+" leader (node "+ID+") appends its own MWOE to its convergecast_buffer");
						convergecast_buffer.add(mwoeMsg);
					}
				}
			}
			// Unwrap convergecast message
			else if (m instanceof FragmentConvergecastMsg) {
				logger.logln("Node " + ID + " got FragmentConvergecastMsg, unwrapping");
				FragmentConvergecastMsg fragmentConvergecastMsg = (FragmentConvergecastMsg) m;
				
				if (mst_parent != null) {
					// Continue to convergecast, intermediate node
					// NOTE: I don't have to check if multiple MWOE, and converge only the minimum, its not a REQUIREMENT, just a suggestion in the assignment.
					convergecast(fragmentConvergecastMsg.getOriginalSenderId(), fragmentConvergecastMsg.getMessage());
					continue;
				} else {
					// Fragment leader, got the message!
					logger.logln("Fragment "+fragmentId+" leader (node "+ID+") got convergecast message: "+fragmentConvergecastMsg);
					// Unwrap convergecast message and add it to buffer
					convergecast_buffer.add(fragmentConvergecastMsg.getMessage());
				}
			}
			else {
				throw new RuntimeException("ERROR: Got invalid message: " + m);
			}
			builder.append(" from node: " + sender.ID);
			logger.logln(builder.toString());
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
		int totalNodes = Tools.getNodeList().size(); // Big-O(N)
		
		if (roundNum == 0) {
			// Start phase 1
			logger.logln("Node "+ID+" starts phase 1: broadcast MWOE to determine leader");
			currPhase = AlgorithmPhases.PHASE_ONE;
						
			// Get MWOE from different fragment (can be null! But since its the first round its fine, all nodes are unique fragments)
			mwoe = getMWOE(true);
			
			// Broadcast MWOE - kickstarts the entire algorithm
			Message message = new MWOEMsg(mwoe.getWeight());
			broadcast(message);
		}
		else if (roundNum == 1) {
			// Start phase 2
			logger.logln("Node "+ID+" starts phase 2: broadcast leader ID");
			currPhase = AlgorithmPhases.PHASE_TWO;
		}
		// We take N time for second phase (N + second phase [2])
		else if (roundNum == totalNodes + 2) {
			// Start phase 3
			logger.logln("Node "+ID+" starts phase 3: broadcast fragmentId");
			currPhase = AlgorithmPhases.PHASE_THREE;
			broadcast(new FragmentIdMsg(fragmentId));
		}
		else if (roundNum == totalNodes + 3) {
			// Start phase 4 + 5 (immediately after finding MWOE (which takes O(1)) we convergecast)
			logger.logln("Node "+ID+" starts phase 4 and 5: find MWOE and convergecast to fragment leader");
			currPhase = AlgorithmPhases.PHASE_FOUR_FIVE;
			// Wait O(N) rounds for convergecast (the professor said its ok in the forum)
		} else if (roundNum == totalNodes*2 + 3) {
			// Start phase 6
			logger.logln("Node "+ID+" starts phase 6: leader broadcasts MWOE");
			currPhase = AlgorithmPhases.PHASE_SIX;
			
			// Get the global fragment MWOE
			if (convergecast_buffer.size() != 0) {
				long[] all_mwoe = new long[convergecast_buffer.size()];
				
				for(int i = 0; i < convergecast_buffer.size(); i++) {
					Message m = convergecast_buffer.get(i);
					if (m instanceof MWOEMsg) {
						MWOEMsg msg = (MWOEMsg) m;
						all_mwoe[i] = msg.weight;
					} else {
						throw new RuntimeException("Convergecast buffer contains non-MWOE message: " + m);
					}
				}
				// Sort
				Arrays.sort(all_mwoe);
				long global_mwoe = all_mwoe[0];
				logger.logln("Fragment "+fragmentId+" leader (node "+ID+") found a global MWOE: "+global_mwoe);
				
				// Clear the convergecast buffer after use
				convergecast_buffer.clear();
				
				// Broadcast to fragment
				MWOEMsg mwoeBroadcastMsg = new MWOEMsg(global_mwoe);
				broadcastFragment(mwoeBroadcastMsg);
				// Wait O(N) rounds for broadcast (the professor said its ok in the forum)
			} else {
				logger.logln("Node "+ID+" has empty convergecast buffer, skipping");
			}
		} else if (roundNum == totalNodes*3 + 3) {
			// Start phase 7
			logger.logln("Node "+ID+" starts phase 7:");
			currPhase = AlgorithmPhases.PHASE_SEVEN;
		}
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
				String nice_weight = String.format("%,d", mwoe.getWeight());
				builder.append(", MWOE: ").append(nice_weight);
			}
		}
		if (ID == fragmentLeaderId) {
			builder.append(", Fragment Leader");
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
