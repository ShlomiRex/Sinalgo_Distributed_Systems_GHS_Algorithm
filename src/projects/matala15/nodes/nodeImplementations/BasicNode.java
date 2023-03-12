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

import javax.swing.JOptionPane;

import projects.matala15.Pair;
import projects.matala15.nodes.edges.WeightedEdge;
import projects.matala15.nodes.messages.CombineFragmentMsg;
import projects.matala15.nodes.messages.ConnectFragmentsMsg;
import projects.matala15.nodes.messages.FragmentBroadcastMsg;
import projects.matala15.nodes.messages.FragmentConvergecastMsg;
import projects.matala15.nodes.messages.FragmentIdMsg;
import projects.matala15.nodes.messages.MWOEMsg;
import projects.matala15.nodes.messages.NewLeaderSwitchMSTDirectionMSsg;
import projects.matala15.nodes.messages.StringMsg;
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
	private AlgorithmPhases currPhase = AlgorithmPhases.PHASE_ONE;
	private enum AlgorithmPhases {
		PHASE_ONE,
		PHASE_TWO,
		PHASE_THREE,
		PHASE_FOUR_FIVE,
		PHASE_SIX,
		PHASE_SEVEN, 
		PHASE_EIGHT
	}
	private List<Message> convergecast_buffer = new ArrayList<>(); // Holds list of convergecast messages. Used in phase 6, where leader waits for all convergecast messages in Big-O(N) (it fills up in multiple rounds).
	private boolean isPhase7NewLeader = false; // When in phase 7, a node can become a new leader. But we must wait untill phase 6 completes, and only then on phase 7 we send convergecast to switch mst direction.
	private boolean isFirstPhaseCycleFinished = false; // When a node doesn't finish a phase cycle (1-8) then this is false. When it finishes phase 8, its true. Its to indicate what to do in phase 2 (next cycle).
	private int numOfNodesInFragment = 1; // Number of nodes in MST (in total). Used in phase 8 to check if the algorithm is finished. All nodes hold this.
	public static int NUM_NODES_TERMINATED_SIMULATION = 0; // When node finishes to run, he increases this
	
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
		
		// Add to broadcast list
		if (broadcast_list.get(ID) == null) {
			broadcast_list.put(ID, new ArrayList<>());
		}
		broadcast_list.get(ID).add(broadcastId);
		
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
		logger.logln("Node "+ID+" sends convergecast message: " + msg);
		if (mst_parent != null) {
			FragmentConvergecastMsg fragmentConvergecastMsg = new FragmentConvergecastMsg(ID, msg);
			send(fragmentConvergecastMsg, mst_parent); // Non-leader node, converge to leader	
		}
		else {			
			logger.logln("Node "+ID+" (fragment "+fragmentId+" leader) appends its own convergecast_buffer the message");
			convergecast_buffer.add(msg);
		}		
	}
	
	@Override
	public void handleMessages(Inbox inbox) {
		while(inbox.hasNext()) {
			Message m = inbox.next();
			BasicNode sender = (BasicNode) inbox.getSender();
			
			StringBuilder builder = new StringBuilder();
			builder.append("Node "+ID+" finished handling message: ");
			
			// Unwrap broadcast message
			if (m instanceof FragmentBroadcastMsg) {
				logger.logln("Node "+ID+" got broadcast message, unwrapping");
				FragmentBroadcastMsg fragmentBroadcastMsg = (FragmentBroadcastMsg) m;
				logger.logln("Node "+ID+" got message: "+fragmentBroadcastMsg);

				// If got broadcast from different fragment, ignore
				// NOTE: This can only happen at phase eight, when 2 or more nodes from fragmentId=X both get a message of CombineFragmentMsg
				// I explained this at the document, basically 2 nodes from fragment X change at the same time to fragment Y AND both got a broadcast message
				// so one of them with lower ID broadcasts to the other node (if they have edge) a fragment broadcast with old fragmentId
				if (fragmentBroadcastMsg.getFragmentId() != fragmentId) {
//					throw new RuntimeException("ERROR: " + this + " got fragment broadcast message for "
//							+ "fragmentId="+fragmentBroadcastMsg.getFragmentId()+" which is not the intended target");
					// ignore
					logger.logln("Node "+ID+" received broadcast message from diffirent fragment, ignoring");
					continue;
				}
				else {
					// Unwrap the message and check its instance later
					m = fragmentBroadcastMsg.getMessage();
				}
				
				boolean alreadyBroadcasted = checkAlreadyBroadcasted(fragmentBroadcastMsg);
				if (alreadyBroadcasted) {
					logger.logln("Node "+ID+" already broadcasted the message, continuing");
					continue;
				}
				
				// Broadcast the message again
				rebroadcast(sender, fragmentBroadcastMsg);

				// Continue handling the wrapped message
			} 
			// Unwrap convergecast message
			else if (m instanceof FragmentConvergecastMsg) {
				logger.logln("Node " + ID + " got convergecast message, unwrapping");
				FragmentConvergecastMsg fragmentConvergecastMsg = (FragmentConvergecastMsg) m;
				logger.logln("Node "+ID+" got message: "+fragmentConvergecastMsg);

				if (mst_parent != null) {
					// Continue to convergecast, intermediate node
					// NOTE: I don't have to check if multiple MWOE, and converge only the minimum, its not a REQUIREMENT, just a suggestion in the assignment.
					
					// If string message, then add this intermediate node
					if (fragmentConvergecastMsg.getMessage() instanceof StringMsg) {
						StringMsg stringMsg = (StringMsg) fragmentConvergecastMsg.getMessage();
						stringMsg.addIntermediateNode(ID);
						convergecast(fragmentConvergecastMsg.getOriginalSenderId(), stringMsg);
					} else {
						// Not string message
						convergecast(fragmentConvergecastMsg.getOriginalSenderId(), fragmentConvergecastMsg.getMessage());	
					}
					continue;
				} else {
					// Fragment leader, got the message!
					Message msg = fragmentConvergecastMsg.getMessage();
					
					logger.logln("Node "+ID+" (fragment leader) saves convergecast message in buffer: "+msg);
					// Unwrap convergecast message and add it to buffer
					convergecast_buffer.add(msg);
					m = msg;
				}
			}
			
			// Handle all known messages (after unwrapping broadcast or convergecast message)
			if (m instanceof MWOEMsg) {
				MWOEMsg msg = (MWOEMsg) m;
				builder.append(msg);
				
				if (currPhase != AlgorithmPhases.PHASE_SIX) {
					// Can be phase 2 (after we got MWOE), in phase 1 we send the MWOE.
					
					if (mwoe != null && mwoe.getWeight() == msg.weight) {
						// Both nodes chosen the same edge to be MWOE. Only one becomes leader, by higher ID
						combineFragments(sender);
					}
				} else {
					// Phase 6
					// Check if node has connection with same weight, if so, this node becomes the new fragment leader
					if (mwoe.getWeight() == msg.weight) {
						logger.logln("Node "+ID+" is located on fragment MWOE edge: "+mwoe+", this node becomes new leader in next phase (phase 7)");
						isPhase7NewLeader = true;
					}
				}
			} else if (m instanceof FragmentIdMsg) {
				FragmentIdMsg msg = (FragmentIdMsg) m;
				builder.append(msg);
				
				// Update local neighbors fragments
				for(BasicNode n : neighbors) {
					if (n.ID == sender.ID) {
						n.fragmentId = msg.getFragmentId();
					}
				}
			} else if (m instanceof NewLeaderSwitchMSTDirectionMSsg) {
				NewLeaderSwitchMSTDirectionMSsg msg = (NewLeaderSwitchMSTDirectionMSsg) m;
				builder.append(msg);
				
				logger.logln("Node "+ID+" switches MST parent from: "+mst_parent+" to: "+sender.ID);
				mst_parent = sender;
				WeightedEdge edge = getEdgeTo(sender.ID);
				edge.setDirection(sender);
				convergecast_buffer.clear(); // Only the old leader will update its buffer, but we clear anyway.
					
				// If old leader, switch to new leader and become regular node
				if (fragmentLeaderId == ID) {
					fragmentLeaderId = msg.getNewLeaderId();
				}
			} else if (m instanceof ConnectFragmentsMsg) {
				ConnectFragmentsMsg msg = (ConnectFragmentsMsg) m;
				builder.append(msg);
				
				// Received connect request (only leaders can receive this). Will now combine fragments.
				combineFragments(sender);
			} else if (m instanceof CombineFragmentMsg) {
				CombineFragmentMsg msg = (CombineFragmentMsg) m;
				builder.append(msg);
				
				// Non-leader nodes in fragment will update their local variables as a result of the combined fragments.
				logger.logln("Node "+ID+" changes fragmentLeaderId from: "+fragmentLeaderId+" to: "+
						msg.getNewLeaderId()+" and fragmentId from: "+fragmentId+" to: "+msg.getNewFragmentId());
				
				// If old leader, switch direction
				if (ID == fragmentLeaderId) {
					if (sender.ID == msg.getNewLeaderId()) {
						mst_parent = sender;
						WeightedEdge edge = getEdgeTo(sender.ID);
						edge.setDirection(sender);
					}
				}
				
				fragmentId = msg.getNewFragmentId();
				fragmentLeaderId = msg.getNewLeaderId();
				numOfNodesInFragment = msg.getTotalNodesInFragment();
			}
			else if (m instanceof StringMsg) {
				StringMsg msg = (StringMsg) m;
				builder.append(msg);
				
				// If server
				if (fragmentLeaderId == ID) {
					logger.logln("Your message: [\""+msg.getMessage()+"\"] is received successfully, and can now be processed");
				} else {
					// Regular node, no need to convergecast, since we already dealt with this above.
				}
			}
			else {
				throw new RuntimeException("ERROR: Got invalid unhandled message: " + m);
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

	private void phase1() {
		// Start phase 1
		logger.logln("Node "+ID+" starts phase 1: broadcast MWOE to determine leader");
		currPhase = AlgorithmPhases.PHASE_ONE;
					
		// Get MWOE from different fragment (can be null)
		mwoe = getMWOE(true);
		
		// Broadcast MWOE
		Message message = new MWOEMsg(mwoe.getWeight());
		broadcast(message);
		
		// Takes 1 round exactly
	}
	
	private void phase2() {
		// Start phase 2
		logger.logln("Node "+ID+" starts phase 2: broadcast leader ID");
		currPhase = AlgorithmPhases.PHASE_TWO;
		
		// Takes N rounds (N + second phase [2])
	}
	
	private void phase3() {
		// Start phase 3
		logger.logln("Node "+ID+" starts phase 3: broadcast fragmentId");
		currPhase = AlgorithmPhases.PHASE_THREE;
		broadcast(new FragmentIdMsg(fragmentId));
	}
	
	private void phase45() {
		// Start phase 4 + 5 (immediately after finding MWOE (which takes O(1)) we convergecast)
		logger.logln("Node "+ID+" starts phase 4 and 5: find MWOE and convergecast to fragment leader");
		currPhase = AlgorithmPhases.PHASE_FOUR_FIVE;
		
		// Find MWOE
		mwoe = getMWOE(true);
		if (mwoe != null) {
			MWOEMsg msg = new MWOEMsg(mwoe.getWeight());
			convergecast(ID, msg);
		}
		// Wait O(N) rounds for convergecast (the professor said its ok in the forum)
	}
	
	private void phase6() {
		// Start phase 6
		logger.logln("Node "+ID+" starts phase 6: leader broadcasts MWOE");
		currPhase = AlgorithmPhases.PHASE_SIX;
		
		// Only leader can start the phase
		if (mst_parent == null) {
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
				logger.logln("Node "+ID+" (fragment "+fragmentId+" leader) found a global "
						+ "MWOE from its convergecast buffer: "+convertToNiceWeight(global_mwoe));
				
				// Clear the convergecast buffer after use
				convergecast_buffer.clear();
				
				
				
				// Broadcast to fragment
				MWOEMsg mwoeMsg = new MWOEMsg(global_mwoe);
				broadcastFragment(mwoeMsg);
				
				// send(mwoeMsg, this); // We can't send message to ourselves, so we must check if this node is the current MWOE
				// Check if node has connection with same weight, if so, this node becomes the new fragment leader
				if (mwoe.getWeight() == global_mwoe) {
					logger.logln("Node "+ID+" is located on fragment MWOE edge: "+mwoe+", this node becomes new leader in next phase (phase 7)");
					isPhase7NewLeader = true;
				}
				
				// Wait O(N) rounds for broadcast (the professor said its ok in the forum)
			} else {
				logger.logln("Node "+ID+" has empty convergecast buffer, skipping");
			}
		}
	}
	
	private void phase7() {
		// Start phase 7
		logger.logln("Node "+ID+" starts phase 7: find new leader & switch edge directions to new leader");
		currPhase = AlgorithmPhases.PHASE_SEVEN;
		if (isPhase7NewLeader) {
			logger.logln("Node "+ID+" becomes the new leader in fragment "+fragmentId+" due to phase 7");
			
			// There exist only 1 (or 0) such node per fragment. We want to switch direction from old leader to new leader in MST.
			NewLeaderSwitchMSTDirectionMSsg newLeaderSwitchMSTDirectionMSsg = new 
					NewLeaderSwitchMSTDirectionMSsg(ID);
			convergecast(ID, newLeaderSwitchMSTDirectionMSsg);
			
			String mst_parent_id = null;
			if (mst_parent != null)
				mst_parent_id = ""+mst_parent.ID;
			logger.logln("Node "+ID+" switches MST parent from: "+mst_parent_id+" to: null (because this node becomes the new leader)");
			
			// Remove direction to old parent, because this node becomes new leader
			if (mst_parent != null) {
				WeightedEdge mst_child = getEdgeTo(mst_parent.ID);
				mst_child.setDirection(null);
				mst_parent = null;	
			}
			
			// Become leader!
			fragmentLeaderId = ID;
		}
		// Wait additional O(N) because of convergecast
	}
	
	private void phase8() {
		// Start phase 8
		logger.logln("Node "+ID+" starts phase 8: The new leader requests to connect to fragment global MWOE");
		currPhase = AlgorithmPhases.PHASE_EIGHT;
		
		if (isPhase7NewLeader) {
			isPhase7NewLeader = false; // Phase 8 starts, we clear the old flags we used
			// Send connect request to the MWOE
			ConnectFragmentsMsg connectFragmentsMsg = new ConnectFragmentsMsg();
			send(connectFragmentsMsg, mwoe.endNode);
			
			// Connect fragments, broadcast the change to entire fragment, and change local variables
			combineFragments((BasicNode) mwoe.endNode);
		}
		// Wait additional O(N) because of combine fragments, the old leader will broadcast to his fragment and notify his nodes of the change
	}
	
	private void postPhase8(int N) {
		// After phase 8 finishes
		logger.logln("Node "+ID+" finished running phase 8");
		isFirstPhaseCycleFinished = true;
		
		roundNum = N + 3 + (-1); // Start phase 4 again. I start with negative 1 because of postStep which increments by 1
	}
	
	@Override
	public void preStep() {
		
		if (NUM_NODES_TERMINATED_SIMULATION == Tools.getNodeList().size())
			return;
		
		int N = Tools.getNodeList().size(); // Number of nodes (N)
		
		if (roundNum == 0) {
			phase1();
		} else if (roundNum == 1) {
			phase2();
		} else if (roundNum == N + 2) {
			phase3();
		} else if (roundNum == N + 3) {
			phase45();
		} else if (roundNum == N*2 + 3) {
			phase6();
		} else if (roundNum == N*3 + 3) {
			phase7();
		} else if(roundNum == N*4 + 3) {
			phase8();
		} else if(roundNum == N*5 + 3) {
			postPhase8(N);
		}
	}

	@Override
	public void postStep() {
		if (currPhase == AlgorithmPhases.PHASE_EIGHT) {
			if (numOfNodesInFragment == Tools.getNodeList().size()) {
				// We finished, all nodes in fragment!
				logger.logln("Node "+ID+" finished running the simulation");
				NUM_NODES_TERMINATED_SIMULATION += 1;
			}
		}
		
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
		
		// Draw R letter if this node is phase7 leader
		if (isPhase7NewLeader)
			drawPhase7NewLeader(g, pt, fontSize);
	}
	
	private void drawPhase7NewLeader(Graphics g, PositionTransformation pt, int fontSize) {
		g.setColor(Color.BLUE);
		
		// Source taken from 'Node.drawNodeAsDiskWithText()'
		Font font = new Font(null, 0, (int) (fontSize * pt.getZoomFactor())); 
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics(font); 
		int h = (int) Math.ceil(fm.getHeight());
		int w = (int) Math.ceil(fm.stringWidth("R"));
		
		int yOffset = (int) (fontSize * pt.getZoomFactor()) * -3;
		g.drawString("R", pt.guiX - w/2, pt.guiY - h/2 - yOffset);
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("BasicNode(ID: ").append(ID).append(", Fragment: ").append(fragmentId);
		
		if (isServer) {
			builder.append(", Server Node");
		} else {
			if (mwoe != null) {
				builder.append(", MWOE: ").append(convertToNiceWeight(mwoe.getWeight()));
			}
		}
		if (ID == fragmentLeaderId) {
			builder.append(", Fragment Leader");
		}
		if (mst_parent != null) {
			builder.append(", MST Parent: "+mst_parent.ID);
		}
		
		builder.append(", Number of fragment nodes: "+numOfNodesInFragment);
		builder.append(", Phase: "+currPhase.name());
		
		builder.append(")");
		return builder.toString();
	}
	
	@NodePopupMethod(menuText="Select as a server")
	public void selectAsServer() {
		isServer = true;
		logger.logln("Setting node " + this + " as server");
	}
	
	@NodePopupMethod(menuText="Send message to server")
	public void sendMessageAsClient() {
		String str = JOptionPane.showInputDialog(null, "Send a message to server:");
		logger.logln("Client "+ID+" is sending message to server: "+str);
		StringMsg msg = new StringMsg(str);
		convergecast(ID, msg);
	}
	
	/**
	 * Return an edge from current node to the given nodeId. If doesn't exist, return null.
	 * @param nodeId
	 * @return
	 */
	private WeightedEdge getEdgeTo(int nodeId) {
		for (Edge e : outgoingConnections) {
			WeightedEdge weightedEdge = (WeightedEdge) e;
			if (weightedEdge.endNode.ID == nodeId) {
				return weightedEdge;
			}
		}
		return null;
	}
	
	/**
	 * Connect this node to the other node, which combines the fragments and changes MST direction accordingly.
	 * @param other
	 */
	private void combineFragments(BasicNode other) {
		logger.logln("Node "+ID+" connects to fragment of node: "+other);
		
		WeightedEdge edgeToSender = getEdgeTo(other.ID);
		
		logger.logln("Other node (ID: "+other.ID+") number of nodes in fragment: "+other.getNumberOfFragmentChildren());
		
		// Whoever ID is higher becomes the leader
		// NOTE: Order of setting variables and sending message is important in this function
		if (ID > other.ID) {
			// This node will become the new leader
			logger.logln("Node "+ID+" becomes the new leader of combined fragments");

			// The node with lower ID already combined number of nodes in fragment of this node. To get original value, we decrement our nodes.
			numOfNodesInFragment += (other.getNumberOfFragmentChildren() - numOfNodesInFragment);
			
			CombineFragmentMsg combineFragmentMsg = new CombineFragmentMsg(ID, fragmentId, numOfNodesInFragment);
			broadcastFragment(combineFragmentMsg);
			
			// Remove direction to old leader
			WeightedEdge edgeToOldLeader = getEdgeTo(fragmentLeaderId);
			if (edgeToOldLeader != null) {
				edgeToOldLeader.setDirection(null);
			}
			
			fragmentLeaderId = ID;
			mst_parent = null;
			edgeToSender.setDirection(null);
		} else {
			// The other node will become the new leader
			logger.logln("Node "+other.ID+" becomes the new leader of combined fragments");
			
			numOfNodesInFragment += other.getNumberOfFragmentChildren();
			
			// Remove old parent from GUI (new leader edge is directed, this old edge is no longer directed)
			if (mst_parent != null) {
				WeightedEdge parentEdge = getEdgeTo(mst_parent.ID);
				parentEdge.setDirection(null);
			}
			
			// Broadcast that this current fragment will be changed become of combining the fragments
			CombineFragmentMsg combineFragmentMsg = new CombineFragmentMsg(other.ID, other.fragmentId, numOfNodesInFragment);
			broadcastFragment(combineFragmentMsg);

			// We broadcast first before we change our local fragment ID, leader ID because broadcastFragment will send to current fragmentId
			// so if we change fragmentId to new fragmentId, the broadcast will be sent to wrong sub-tree.
			
			fragmentLeaderId = other.ID;
			mst_parent = other;
			edgeToSender.setDirection(other);
			fragmentId = other.fragmentId;
		}
	}
	
	public int getNumberOfFragmentChildren() {
		return numOfNodesInFragment;
	}
	
	private String convertToNiceWeight(long weight) {
		String nice_weight = String.format("%,d", weight);
		return "\""+nice_weight+"\"";
	}
}
