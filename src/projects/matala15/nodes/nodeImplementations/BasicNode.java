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
import java.util.Stack;

import javax.swing.JOptionPane;

import projects.matala15.CustomGlobal;
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
		PHASE_FOUR,
		PHASE_FIVE,
		PHASE_SIX,
		PHASE_SEVEN, 
		PHASE_EIGHT,
		PHASE_FINISHED
	}
	private List<Message> convergecast_buffer = new ArrayList<>(); // Holds list of convergecast messages. Used in phase 6, where leader waits for all convergecast messages in Big-O(N) (it fills up in multiple rounds).
	private boolean isPhase7NewLeader = false; // When in phase 7, a node can become a new leader. But we must wait untill phase 6 completes, and only then on phase 7 we send convergecast to switch mst direction.
	private int numOfNodesInFragment = 1; // Number of nodes in MST (in total). Used in phase 8 to check if the algorithm is finished. All nodes hold this.
	private List<Pair<BasicNode, Message>> messages_buffer = new ArrayList<>();
	private int phase1LeaderId = -1;
	private boolean isGHSFinished = false; // If this node finished running GHS this is true.
	
	/* POST GHS ALGORITHM */
	
	private boolean isClient = false; // A node becomes a client for 1 round, when it got a request from user to send message to server. It then resets back to false. Its a trigger for next round.
	private Message clientMessage = null; // When isClient triggered, this message gets sent to server. It then resets back to null after sending it.
	
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
		logger.logln("Node "+ID+" searched for MWOE and got: "+mwoe);
		return mwoe;
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
			FragmentConvergecastMsg fragmentConvergecastMsg = new FragmentConvergecastMsg(originalSenderId, msg);
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
				logger.logln("Node " + ID + " got convergecast message from: "+sender.ID+", unwrapping");
				FragmentConvergecastMsg fragmentConvergecastMsg = (FragmentConvergecastMsg) m;

				if (mst_parent != null) {
					// Continue to convergecast, intermediate node
					// NOTE: I don't have to check if multiple MWOE, and converge only the minimum, its not a REQUIREMENT, just a suggestion in the assignment.
					
					// If string message, then deal with it in diffirent place, not here.
					if ((fragmentConvergecastMsg.getMessage() instanceof StringMsg) == false) {
						//TODO: Remove comments
//						StringMsg stringMsg = (StringMsg) fragmentConvergecastMsg.getMessage();
//						stringMsg.addIntermediateNode(ID);
//						convergecast(fragmentConvergecastMsg.getOriginalSenderId(), stringMsg);
						
						// Not string message
						convergecast(fragmentConvergecastMsg.getOriginalSenderId(), fragmentConvergecastMsg.getMessage());
					}
					m = fragmentConvergecastMsg.getMessage();
				} else {
					// Fragment leader, got the message!
					Message msg = fragmentConvergecastMsg.getMessage();
					
					logger.logln("Node "+ID+" (fragment leader) saves convergecast message in buffer: "+msg);
					// Unwrap convergecast message and add it to buffer
					convergecast_buffer.add(msg);
					m = msg;
				}
			}
			
			logger.logln("Node "+ID+" got message from node "+sender.ID+": "+m);
			messages_buffer.add(new Pair<BasicNode, Message>(sender, m));
		}
	}

	@Override
	public void init() {
		
	}

	@Override
	public void neighborhoodChange() {

	}

	private void preStepPhase1() {
		// Start phase 1 (takes 1 round exactly)
		logger.logln("Node "+ID+" starts phase 1: find and broadcast MWOE");
		currPhase = AlgorithmPhases.PHASE_ONE;
					
		// Get MWOE from different fragment (can be null)
		mwoe = getMWOE(true);
		
		// Broadcast MWOE to local neighbours (not fragment)
		Message message = new MWOEMsg(mwoe.getWeight());
		broadcast(message);
	}
	
	private void preStepPhase2() {
		// Start phase 2 (takes N rounds - because of broadcast)
		logger.logln("Node "+ID+" starts phase 2: determine leader and broadcast leader ID");
		currPhase = AlgorithmPhases.PHASE_TWO;
		
		// Aggregate all messages from phase 1, and then determine leader in postStep
	}
	
	private void postStepPhase2() {
		// Select fragment leader. If my MWOE is equal to MWOE of different node, we both chosen the same edge.
		for (Pair<BasicNode, Message> p : messages_buffer) {
			BasicNode sender = p.getA();
			Message m = p.getB();
			
			// The first round of phase 2
			if (m instanceof MWOEMsg) {
				MWOEMsg msg = (MWOEMsg) m;
				if (mwoe != null && mwoe.getWeight() == msg.getWeight()) {
					// Both nodes chosen the same edge to be MWOE. Only one becomes leader, by higher ID
					phase1LeaderId = Math.max(ID, sender.ID);
					logger.logln("Node "+ID+" chosen node "+phase1LeaderId+" as phase 1 fragment leader");
					
					// Combine fragments and broadcast chosen leader, new fragmentId to current fragment
					combineFragments(sender);
					break;
				}
			} else if (m instanceof CombineFragmentMsg) {
				// Non-leader nodes in fragment will update their local variables as a result of the combined fragments.
				CombineFragmentMsg msg = (CombineFragmentMsg) m;
				
				logger.logln("Node "+ID+" changes fragmentLeaderId from: "+fragmentLeaderId+" to: "+
						msg.getNewLeaderId()+" and fragmentId from: "+fragmentId+" to: "+msg.getNewFragmentId()+
						" and nodes in fragment from: "+numOfNodesInFragment+" to: "+msg.getTotalNodesInFragment());

				fragmentId = msg.getNewFragmentId();
				fragmentLeaderId = msg.getNewLeaderId();
				numOfNodesInFragment = msg.getTotalNodesInFragment();
			} else {
				throw new RuntimeException("Unexpected message type: "+m);
			}
		}
	}
	
	private void preStepPhase3() {
		// Start phase 3 (takes 1 round exactly)
		logger.logln("Node "+ID+" starts phase 3: broadcast fragmentId");
		currPhase = AlgorithmPhases.PHASE_THREE;
		broadcast(new FragmentIdMsg(fragmentId));
	}
	
	private void preStepPhase4() {
		// Start phase 4 (takes 1 round exactly)
		logger.logln("Node "+ID+" starts phase 4: find MWOE");
		currPhase = AlgorithmPhases.PHASE_FOUR;
		
		// Get MWOE from different fragment (can be null)
		mwoe = getMWOE(true);
	}
	
	private void postStepPhase4() {
		for (Pair<BasicNode, Message> p : messages_buffer) {
			BasicNode sender = p.getA();
			Message m = p.getB();
			
			if (m instanceof FragmentIdMsg) {
				FragmentIdMsg msg = (FragmentIdMsg) m;
				
				// Update local neighbors fragments
				for(BasicNode n : neighbors) {
					if (n.ID == sender.ID) {
						n.fragmentId = msg.getFragmentId();
					}
				}
			} else {
				throw new RuntimeException("Unexpected message type: "+m);
			}
		}
	}
	
	private void preStepPhase5() {
		// Start phase 5 (takes N rounds for convergecast, professor said its ok in the forum)
		logger.logln("Node "+ID+" starts phase 5: convergecast MWOE to fragment leader");
		currPhase = AlgorithmPhases.PHASE_FIVE;

		if (mwoe != null) {
			MWOEMsg msg = new MWOEMsg(mwoe.getWeight());
			convergecast(ID, msg);
		}
	}
	
	private void preStepPhase6() {
		// Start phase 6
		logger.logln("Node "+ID+" starts phase 6: leader finds fragment MWOE and broadcasts it");
		currPhase = AlgorithmPhases.PHASE_SIX;
		
		// Only leader can start the phase
		if (ID == fragmentLeaderId) {
			// Find fragment MWOE by given convergecast messages of MWOE
			if (convergecast_buffer.size() != 0) {
				long[] all_mwoe = new long[convergecast_buffer.size()];
				
				for(int i = 0; i < convergecast_buffer.size(); i++) {
					Message m = convergecast_buffer.get(i);
					if (m instanceof MWOEMsg) {
						MWOEMsg msg = (MWOEMsg) m;
						all_mwoe[i] = msg.getWeight();
					} else {
						throw new RuntimeException("Convergecast buffer contains non-MWOE message: " + m);
					}
				}
				
				// Sort
				Arrays.sort(all_mwoe);
				long global_mwoe = all_mwoe[0];
				logger.logln("Node "+ID+" (fragment "+fragmentId+" leader) found a fragment MWOE from its convergecast buffer: "+convertToNiceWeight(global_mwoe));

				convergecast_buffer.clear(); // Clear the convergecast buffer after use
				
				// Broadcast to fragment
				MWOEMsg mwoeMsg = new MWOEMsg(global_mwoe);
				broadcastFragment(mwoeMsg);
				
				// send(mwoeMsg, this); // We can't send message to ourselves, so we must check if this node is the current MWOE
				// Check if node has connection with same weight, if so, this node becomes the new fragment leader
				if (mwoe != null && mwoe.getWeight() == global_mwoe) {
					logger.logln("Node "+ID+" is located on fragment MWOE edge: "+mwoe+", this node becomes new leader in next phase (phase 7)");
					isPhase7NewLeader = true;
				}
				
				// Wait O(N) rounds for broadcast (the professor said its ok in the forum)
			} else {
				logger.logln("Node "+ID+" has empty convergecast buffer, skipping");
			}
		}
	}
	
	private void postStepPhase6() {
		// If regular node
		if (ID != fragmentLeaderId) {
			// This node is not leader, check if it has any messages, like fragment MWOE to process
			for (Pair<BasicNode, Message> p : messages_buffer) {
				Message m = p.getB();
				
				MWOEMsg msg = (MWOEMsg) m;
				
				if (m instanceof MWOEMsg) {
					// Maybe this node is on the fragment MWOE edge? Check it
					if (mwoe != null && mwoe.getWeight() == msg.getWeight()) {
						logger.logln("Node "+ID+" is located on fragment MWOE edge: "+mwoe+", this node becomes new leader in next phase (phase 7)");
						isPhase7NewLeader = true;
						break;
					}
				} else {
					throw new RuntimeException("Unexpected message type: "+m);
				}
			}
		}
	}
	
	private void preStepPhase7() {
		// Start phase 7
		logger.logln("Node "+ID+" starts phase 7: find new leader & switch edge directions to new leader");
		currPhase = AlgorithmPhases.PHASE_SEVEN;
		
		if (isPhase7NewLeader) {
			logger.logln("Node "+ID+" becomes the new leader in fragment "+fragmentId);
			
			// There exist only 1 (or 0) such node per fragment. We want to switch direction from old leader to new leader in MST.
			NewLeaderSwitchMSTDirectionMSsg newLeaderSwitchMSTDirectionMSsg = new 
					NewLeaderSwitchMSTDirectionMSsg(ID);
			
			// Send convergecast
			convergecast(ID, newLeaderSwitchMSTDirectionMSsg);
			
			String mst_parent_id = null;
			if (mst_parent != null)
				mst_parent_id = ""+mst_parent.ID;
			logger.logln("Node "+ID+" switches MST parent from: "+mst_parent_id+" to: null (because this node becomes the new leader)");
			
			// Remove direction to old parent, because this node becomes new leader
			removeDirectionToMSTParent();
			
			// Become leader
			fragmentLeaderId = ID;
			mst_parent = null;
		}
		// Wait additional O(N) because of convergecast
	}
	
	private void postStepPhase7() {
		if (messages_buffer.size() == 0)
			return;
		
		if (messages_buffer.size() > 1)
			throw new RuntimeException("Expected to only have 1 message in round 7");

		BasicNode sender = messages_buffer.get(0).getA();
		Message m = messages_buffer.get(0).getB();
		
		NewLeaderSwitchMSTDirectionMSsg msg = (NewLeaderSwitchMSTDirectionMSsg) m;

		logger.logln("Node "+ID+" switches MST parent from: "+mst_parent+" to: "+sender.ID);
		replaceMSTParentDirection(sender); // Its important to first remove old connection and only then update mst parent (my mistake)
		mst_parent = sender;
		
		convergecast_buffer.clear(); // Only the old leader will update its buffer, but we clear anyway.
			
		// If old leader, switch to new leader and become regular node
		if (fragmentLeaderId == ID) {
			fragmentLeaderId = msg.getNewLeaderId();
		}

	}
	
	private void preStepPhase8() {
		// Start phase 8 (First round checks for ConnectFragmentMsg. Only then, we do combine fragments, which takes O(N), in total O(N+1))
		logger.logln("Node "+ID+" starts phase 8: The new leader requests to connect to fragment MWOE");
		currPhase = AlgorithmPhases.PHASE_EIGHT;
		
		if (isPhase7NewLeader) {
			// Send connect request to the MWOE fragment
			ConnectFragmentsMsg connectFragmentsMsg = new ConnectFragmentsMsg();
			send(connectFragmentsMsg, mwoe.endNode);
		}
	}
	
	private void postStepPhase8() {
		for (Pair<BasicNode, Message> p : messages_buffer) {
			BasicNode sender = p.getA();
			Message m = p.getB();
			
			if (m instanceof ConnectFragmentsMsg) {
				// Only connect nodes that agree both on same MWOE, AND if a non-leader node receives connect message, don't connect! Non-leaders can't connect
				if (mwoe.endNode.ID == sender.ID && isPhase7NewLeader) {
					combineFragments((BasicNode) mwoe.endNode);
					break; // There exist only 1 MWOE (or 0)
				}
			} else if (m instanceof CombineFragmentMsg) {
				CombineFragmentMsg msg = (CombineFragmentMsg) m;
				fragmentId = msg.getNewFragmentId();
				fragmentLeaderId = msg.getNewLeaderId();
				numOfNodesInFragment = msg.getTotalNodesInFragment();
			}
		}
	}
	
	private void preStepPhase9(int N) {
		// After phase 8 finishes
		logger.logln("Node "+ID+" finished running phase 8");
				
		// Clear local variables
		convergecast_buffer.clear();
		messages_buffer.clear();
		isPhase7NewLeader = false;
		phase1LeaderId = -1;

		// Check if all nodes are in the same fragment, if so we finished running the algorithm
		if (numOfNodesInFragment == N) {
			logger.logln("Node "+ID+" finished running GHS algorithm");
			isGHSFinished = true;	
			//NUM_NODES_TERMINATED_SIMULATION += 1; //TODO: Remove
			currPhase = AlgorithmPhases.PHASE_FINISHED;
			if (isServer) {
				// Begin to switch direction to server
				logger.logln("Server (node "+ID+") will now convergecast to MST leader, and server becomes new MST leader");
				NewLeaderSwitchMSTDirectionMSsg msg = new NewLeaderSwitchMSTDirectionMSsg(ID);
				convergecast(ID, msg);
				
				// Remove old connection to MST parent, because this is server, its new leader
				removeDirectionToMSTParent();
				fragmentLeaderId = ID;
				mst_parent = null;
			}
		} else {
			logger.logln("Node "+ID+" starts the cycle again");
			roundNum = N + 3 + (-1); // Start phase 4 again. I start with negative 1 because of postStep which increments by 1
		}
	}
	
	private void postStepPhase9() {
		if (messages_buffer.size() > 1)
			throw new RuntimeException("After finishing GHS, we expect to have a zero or one messages, to switch to server as new MST leader, or client/server messages");
		
		if (messages_buffer.size() == 1) {
			BasicNode sender = messages_buffer.get(0).getA();
			Message m = messages_buffer.get(0).getB();
			
			if (m instanceof NewLeaderSwitchMSTDirectionMSsg) {
				// Switch direction to server
				NewLeaderSwitchMSTDirectionMSsg msg = (NewLeaderSwitchMSTDirectionMSsg) m;
				logger.logln("Node "+ID+" switches MST parent from: "+mst_parent+" to: "+sender.ID);
				replaceMSTParentDirection(sender); // Its important to first remove old connection and only then update mst parent (my mistake)
				mst_parent = sender;
				fragmentLeaderId = msg.getNewLeaderId();
			} else {
				// Handle client requests, server responses
				logger.logln("Node "+ID+" got message of type: StringMsg");
				StringMsg msg = (StringMsg) m;
				
				if (isServer) {
					// Server
					logger.logln("Server got the message: " + msg);
					
					// Create response
					String responseStr = "Your message: \""+msg.getMessage()+"\" is received successfully, and can now be processed";
					StringMsg responseMessage = new StringMsg(responseStr, ID, false); // Destination is NOT server, but client
					
					// Fill response path with the request path
					for (int id : msg.getIntermediateNodes())
						responseMessage.addIntermediateNode(id);
					
					// Send back to client
					send(responseMessage, sender);
				} else {
					// Client
					logger.logln("Node "+ID+" will send the server response back to client");

					// If destination is server, we can safely convergecast 
					if (msg.isDestinationIsServer()) {
						msg.addIntermediateNode(ID);
						convergecast(msg.getOriginalSenderId(), msg);	
					} else {
						// Else, destination is client, send in opposite path of convergecast
						StringMsg serverResponse = new StringMsg(msg.getMessage(), msg.getOriginalSenderId(), false);
						for (int id : msg.getIntermediateNodes()) {
							serverResponse.addIntermediateNode(id);
						}
						
						// Get last ID and remove from list
						Stack<Integer> path = serverResponse.getIntermediateNodes();
						int lastId = path.pop();
						if (lastId == ID) {
							if (path.isEmpty() == false) {
								lastId = path.peek(); // Don't pop, since we want intermediate nodes to 'pop' their own ID.								
							} else {
								// We reached the client!
								logger.logln("Client (node "+ID+") got the server response: "+msg.getMessage());
								return;
							}
						}
						else {
							throw new RuntimeException("The last id in path should be this node's ID");
						}
						
						// Send to neighbor with ID just like last ID in message path
						boolean foundPath = false;
						for (BasicNode n : neighbors) {
							if (n.ID == lastId) {
								send(serverResponse, n);
								foundPath = true;
								break;
							}
						}
						
						if (foundPath == false)
							throw new RuntimeException("Couldn't find the path back to client, path: "+path);
					}
				}
			}
		}
	}
	
	private void preStepAfterGHSFinished() {
		if (isClient) {
			logger.logln("Node "+ID+" (client) begins sending the message to server");
			convergecast(ID, clientMessage);
			
			// Reset the trigger
			isClient = false;
			// Reset the message we don't need it anymore
			clientMessage = null;
		}
	}
	
	private void postStepGHSFinished() {
		logger.logln("postStepGHSFinished");
	}
	
	@Override
	public void preStep() {
		int N = Tools.getNodeList().size(); // Number of nodes (N)
		
		if (roundNum == 0) {
			preStepPhase1();
		} else if (roundNum == 1) {
			preStepPhase2();
		} else if (roundNum == N + 2) {
			preStepPhase3();
		} else if (roundNum == N + 3) {
			preStepPhase4();
		} else if (roundNum == N + 4) {
			preStepPhase5();
		} else if (roundNum == N*2 + 4) {
			preStepPhase6();
		} else if (roundNum == N*3 + 4) {
			preStepPhase7();
		} else if(roundNum == N*4 + 4) {
			preStepPhase8();
		} else if(roundNum == N*5 + 5) {
			preStepPhase9(N);
		} else if (isGHSFinished) {
			preStepAfterGHSFinished();	
		}
	}

	@Override
	public void postStep() {
		if (currPhase == AlgorithmPhases.PHASE_TWO) {
			postStepPhase2();
		} else if (currPhase == AlgorithmPhases.PHASE_FOUR) {
			postStepPhase4();
		} else if (currPhase == AlgorithmPhases.PHASE_SIX) {
			postStepPhase6();
		} else if (currPhase == AlgorithmPhases.PHASE_SEVEN) {
			postStepPhase7();
		} else if (currPhase == AlgorithmPhases.PHASE_EIGHT) {
			postStepPhase8();
		} else if (currPhase == AlgorithmPhases.PHASE_FINISHED) {
			postStepPhase9();
		} else if (isGHSFinished) {
			postStepGHSFinished();
		}
		
		roundNum += 1;
		messages_buffer.clear();
	}
	
	/**
	 * Draw fragment ID above the node
	 * Returns the width, height according to zoom factor, font size and more.
	 */
	private void drawFragmentId(Graphics g, PositionTransformation pt, int fontSize, int w, int h) {
		g.setColor(Color.MAGENTA);
		String fragmentIdString = ""+fragmentId;
		
		int yOffset = (int) (fontSize * pt.getZoomFactor()) * -2;
		g.drawString(fragmentIdString, pt.guiX - w/2, pt.guiY + h/2 + yOffset);
	}
	
	/**
	 * Draw indicator below the node that this node is a server
	 */
	private void drawAsServerNode(Graphics g, PositionTransformation pt, int width, int height) {
		int d = Math.max(width, height);
		
		// Fill color
		g.setColor(Color.YELLOW);
		g.fillRect(pt.guiX - width, pt.guiY + height, d, d);
		
		// Draw border
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
		
		// Source taken from 'Node.drawNodeAsDiskWithText()'
		// Get width, height of font by using zoom factor and length of string (fragmentId)
		String fragmentIdString = ""+fragmentId;
		Font font = new Font(null, 0, (int) (fontSize * pt.getZoomFactor())); 
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics(font); 
		int h = (int) Math.ceil(fm.getHeight());
		int w = (int) Math.ceil(fm.stringWidth(fragmentIdString));
		
		// If node is server, add indicator (yellow rectangle) below the node
		if (isServer)
			drawAsServerNode(g, pt, w, h);
		
		// If finished running, no need to show fragment ID or anything below this line
		if (CustomGlobal.IS_SIMULATION_TERMINATED)
			return;
		
		// Draw fragment ID above the node
		drawFragmentId(g, pt, fontSize, w, h);
		
		// Draw R letter if this node is phase7 leader, indicating this node is located on the fragment MWOE
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
		int w = (int) Math.ceil(fm.stringWidth("r"));
		
		int yOffset = (int) (fontSize * pt.getZoomFactor()) * -3;
		g.drawString("r", pt.guiX - w/2, pt.guiY - h/2 - yOffset);
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
		builder.append(", Fragment Leader ID: "+fragmentLeaderId);
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
		logger.logln("Client "+ID+" is sending message to server: \""+str+"\"");
		logger.logln("The message will be sent next round\n\n");
		
		StringMsg msg = new StringMsg(str, ID, true);
		msg.addIntermediateNode(ID);
		
		// Setup triggers for next round
		isClient = true;
		clientMessage = msg;
	}
	
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
		
		logger.logln("Node "+other.ID+" has "+other.getNumberOfFragmentChildren()+" nodes in fragment");
		
		// Whoever ID is higher becomes the leader
		// NOTE: Order of setting variables and sending message is important in this function (after broadcasting we can change local fragmentId and leaderId)
		if (ID > other.ID) {
			// This node will become the new leader. This node also has higher ID.
			logger.logln("Node "+ID+" becomes the new leader of combined fragments");

			// The node with lower ID already combined number of nodes in fragment of this node. 
			// To get original value, we decrement our nodes to get the true number of nodes in other fragment.
			numOfNodesInFragment += (other.getNumberOfFragmentChildren() - numOfNodesInFragment);
			
			// Tell current fragment of combining fragments
			CombineFragmentMsg combineFragmentMsg = new CombineFragmentMsg(ID, fragmentId, numOfNodesInFragment);
			broadcastFragment(combineFragmentMsg);
			
			fragmentLeaderId = ID;
			mst_parent = null;
			//fragmentId = fragmentId; // The current fragment ID is used for combining fragments
			
			// This node is the new leader, so it removes old MST parent direction
			removeDirectionToMSTParent();
		} else {
			// The other node will become child of the new leader. This node also has lower ID.
			logger.logln("Node "+ID+" becomes child of chosen leader "+other.ID);
			
			numOfNodesInFragment += other.getNumberOfFragmentChildren();
			
			// Broadcast that this current fragment will be changed become of combining the fragments BEFORE CHANGING CURRENT FRAGMENT ID.
			CombineFragmentMsg combineFragmentMsg = new CombineFragmentMsg(other.ID, other.fragmentId, numOfNodesInFragment);
			broadcastFragment(combineFragmentMsg);
			
			fragmentLeaderId = other.ID;
			mst_parent = other;
			fragmentId = other.fragmentId;

			// Remove old MST parent and set direction to new MST parent
			replaceMSTParentDirection(other);
		}
		phase1LeaderId = -1; // Clear
	}
	
	public int getNumberOfFragmentChildren() {
		return numOfNodesInFragment;
	}
	
	private String convertToNiceWeight(long weight) {
		String nice_weight = String.format("%,d", weight);
		return "\""+nice_weight+"\"";
	}
	
	/**
	 * If this node has MST parent, it removes the direction to this parent.
	 * Used when a node becomes new leader.
	 */
	private void removeDirectionToMSTParent() {
		if (mst_parent != null) {
			WeightedEdge parentEdge = getEdgeTo(mst_parent.ID);
			parentEdge.setDirection(null);
		}
	}
	
	
	/**
	 * Remove old MST parent and set direction to new MST parent
	 * @param newMSTParent
	 */
	private void replaceMSTParentDirection(BasicNode newMSTParent) {
		removeDirectionToMSTParent();
		WeightedEdge edgeToNewMSTParent = getEdgeTo(newMSTParent.ID);
		edgeToNewMSTParent.setDirection(newMSTParent);
	}
}
