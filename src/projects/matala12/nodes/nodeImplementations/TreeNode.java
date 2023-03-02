package projects.matala12.nodes.nodeImplementations;



import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;

import projects.sample6.CustomGlobal;
import projects.sample6.nodes.messages.SixVcol.ColorMessage;
import projects.sample6.nodes.messages.Vcol_MIS.DecidedMessage;
import projects.sample6.nodes.messages.Vcol_MIS.RoundMessage;
import projects.sample6.nodes.messages.Vcol_MIS.UndecidedMessage;
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
 * Note that the leaves are instances of LeafNode, a subclass of this class. 
 */
public class TreeNode extends Node {

	Logging logger = Logging.getLogger();
	
	public TreeNode parent = null; // the parent in the tree, null if this node is the root
	
	public int six_vcol_color = -1; // Possible values: 0..5. At the start, there is N colors. After the algo finishes, there are 6 colors maximum.
	private boolean flag_sixVcol_started = false; // Used only for the root to start the algorithm.
	private int round = 1;
	private boolean flag_sixVcol_finished = false; // Used locally for each node to change their draw function
	private int six_vcol_current_round_bits = -1;
	
	private int mis_round = 0;
	private boolean flag_mis_started = false;
	public static boolean FLAG_MIS_STARTED_GLOBAL = false; // Used for MyEdge to draw 2 strings on the same edge so they don't overlap.
	private MIS_State mis_state = MIS_State.NON_MIS;
	private boolean mis_decided = false;
	private ArrayList<Node> received = new ArrayList<>();
	private boolean is_have_neighbour_in_mis = false;
	
	private enum MIS_State {
		IN_MIS,
		NON_MIS
	}
	
	@Override
	public void checkRequirements() throws WrongConfigurationException {
	}

	@Override
	public void handleMessages(Inbox inbox) {
		if (flag_sixVcol_finished == false)
			six_vcol(inbox);
		else
			vcol_mis(inbox);
	}
	
	/** 
	 * This function sends a message to all the node's children.
	 * I didn't understand why 'broadcast(m);' doesn't work correctly. Apparently it sends to parent aswell (which I didn't take into account), which is not what I wanted.
	 * @param m
	 */
	private void broadcast_children(Message m) {
		for(Edge e : this.outgoingConnections) {
			// Note: .equals is comparing objects, I did '==' and spent 30 minutes on why it doesn't work! Because it compares values, not object hashes.
			TreeNode outgoing_node = (TreeNode)e.endNode;
			if (outgoing_node.parent == null) {
				// do nothing, root has no parent
			}
			else if (outgoing_node.parent.equals(this)) {
				// if 'this' is the parent of outgoing node, then outgoing node is the child of 'this'. Send
				logger.logln(this + " sends message: " + m + " to: " + e.endNode);
				send(m, e.endNode);
			}
		}
	}
	
	// Page 126, Algo 9.9 within the book (six_vcol)
	// Specifically, section 9.5.2: Six Coloring Algorithm
	private void six_vcol(Inbox inbox) {
		if (flag_sixVcol_started == false) {
			// "The algorithm starts by the root coloring itself with 0 and sending this color to its children."
			if (parent == null) {
				this.six_vcol_color = 0;
			}
			
			// All parents send their color to all their children (which gives log * n run-time, instead of height of tree, like regualr broadcast)
			flag_sixVcol_started = true;
			// Send color of parent to children
			Message m = new ColorMessage(this.six_vcol_color);
			broadcast_children(m);
			
			this.six_vcol_current_round_bits = CustomGlobal.SIX_VCOL_MAX_COLOR_BITS;
		}
		
		// The root will change his color in every round by 'randomizing' the k index (like random parent. But root has no parent so we do this manually).
		// We also don't want to send message at the last round, since the next round will receive the Color message, but this phase will stop.
		if (parent == null && flag_sixVcol_finished == false && round < CustomGlobal.SIX_VCOL_SIMULATION_ROUNDS) {
			// (root color + 1) Modulo (n)
			int root_parent_color = ((this.six_vcol_color + 1) % CustomGlobal.NUM_OF_NODES);
			Message m = new ColorMessage(root_parent_color);
			logger.logln(this + " sends message to self: " + m);
			sendDirect(m, this);
		}
		
		while(inbox.hasNext() && round <= CustomGlobal.SIX_VCOL_SIMULATION_ROUNDS) {
			Message m = inbox.next();
			
			// Received from parent
			if(m instanceof ColorMessage) {	
				logger.logln(this + " received message from parent: " + m.toString());
				/*
				 * "In every round thereafter, each node receives a color c_p from its parent 
				 * and finds the smallest index k that its current color c_i differs from c_p."
				 */
				int cp = ((ColorMessage) m).getSixVcolColor(); 	// parent color
				int ci = this.six_vcol_color;					// current color
				
				// Convert to binary
				String cp_binary = Integer.toBinaryString(cp);
				String ci_binary = Integer.toBinaryString(ci);
				
				// Zero-pad
				// Note: to activate assert, use '-ea' JVM flag.
				assert cp_binary.length() < this.six_vcol_current_round_bits;
				assert ci_binary.length() < this.six_vcol_current_round_bits;
				
				while(cp_binary.length() < six_vcol_current_round_bits)
					cp_binary = "0" + cp_binary;
				while(ci_binary.length() < six_vcol_current_round_bits)
					ci_binary = "0" + ci_binary;
				
				
				
				// Find smallest k-index 
				int k_index = -1;
				// We start from the start of the string (left-to-right), even though MSB (most significant bit) binary string is read from right-to-left.
				// Note: ci_binary and cp_binary lengths are the same, because of zero-pad. We could also do: i = Math.max(cp_binary.length(), ci_binary.length()
				for(int i = 0; i < ci_binary.length(); i++) {
					if (ci_binary.charAt(i) != cp_binary.charAt(i)) {
						k_index = i;
						break; // We must find the 'smallest' k_index, so we stop when we got the first result.
					}
				}
				
				assert k_index == -1; // At the beginning all nodes have different colors. Its impossible to not find any difference.
				
				/*	
				 * "It then assigns a new color to itself by a bit string representing k
				 * concatenated with the value of c_i in the kth position."
				 */
				String k_binary = Integer.toBinaryString(k_index);
				String new_color_binary = k_binary + ci_binary.charAt(k_index);
				int new_color = Integer.parseInt(new_color_binary, 2);
				logger.logln(this + " changed color from " + this.six_vcol_color +  " to " + new_color + " at round " + round);
				this.six_vcol_color = new_color;
				
				/*
				 * "This new value of c_i is sent to children to be received
				 * in the next round and used for the same computation in the next round."
				 */
				ColorMessage new_color_message = new ColorMessage(this.six_vcol_color);
				broadcast_children(new_color_message);
				
				/*
				 * "This process continues until each node has a color in the range {0,..,5}..."
				 */
				// Recalculate amount of bits for next round
				this.six_vcol_current_round_bits = 1 + (int)Math.ceil(Math.log(this.six_vcol_current_round_bits) / Math.log(2));
			}
		}
		if (flag_sixVcol_finished == false && round > CustomGlobal.SIX_VCOL_SIMULATION_ROUNDS) {
			flag_sixVcol_finished = true;
			logger.logln(this + " finished coloring.");
			Tools.repaintGUI();
		}
		round += 1;
	}

	// The Vcol_MIS algorithm
	private void vcol_mis(Inbox inbox) {
		if (flag_mis_started == false) {
			flag_mis_started = true;
			FLAG_MIS_STARTED_GLOBAL = true;
			logger.logln(this+ " started MIS algo");
			
			// Start by broadcasting the round message with the color after six_vcol algorithm.
			Message m = new RoundMessage(this.six_vcol_color);
			broadcast(m);
		} else {
			while(inbox.hasNext()) {
				Message m = inbox.next();
				
				if (m instanceof DecidedMessage) {
					is_have_neighbour_in_mis = true;
				}
			}
			
			logger.logln(this + " received: " + received);
			
			if (mis_decided == false && this.six_vcol_color == mis_round) {
				if (is_have_neighbour_in_mis == false) {
					Message m2 = new DecidedMessage();
					logger.logln(this + " broadcasts: " + m2);
					broadcast(m2);
					
					this.mis_state = MIS_State.IN_MIS;
					this.mis_decided = true;
					logger.logln(this + " changed MIS state to: INMIS");
				} else {
					Message m2 = new UndecidedMessage();
					this.mis_state = MIS_State.NON_MIS;
					this.mis_decided = true;
					broadcast(m2);
				}
			}
			mis_round++;
		}
	}
	
	@Override
	public void init() {
		// "Initially, all nodes have color represented by their identifiers" (for total of n colors)
		this.six_vcol_color = this.ID;
	}

	@Override
	public void neighborhoodChange() {
	}

	@Override
	public void preStep() {

	}

	@Override
	public void postStep() {

	}

	@Override
	public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
		String text = ""+six_vcol_color;
		int fontSize = 12;
		Color textColor;
		switch(this.six_vcol_color) {
			case 0:
				this.setColor(Color.YELLOW);
				textColor = Color.BLACK;
				break;
			case 1:
				this.setColor(Color.CYAN);
				textColor = Color.BLACK;
				break;
			case 2:
				this.setColor(Color.RED);
				textColor = Color.BLACK;
				break;
			case 3:
				this.setColor(Color.GRAY);
				textColor= Color.BLACK;
				break;
			case 4:
				this.setColor(Color.MAGENTA);
				textColor = Color.BLACK;
				break;
			case 5:
				this.setColor(Color.PINK);
				textColor = Color.BLACK;
				break;
			default:
				this.setColor(Color.BLACK);
				textColor = Color.YELLOW;
				break;
		}
		this.drawNodeAsDiskWithText(g, pt, highlight, text, fontSize, textColor);
		
		if (flag_mis_started == true) {
			// Can be looked into the source code for this.drawNodeAsDiskWithText
			// Basically took some functionality and apply to my code so it visually appealing
			
			String mis_text = "Undecided";
			Color c = g.getColor();

			if (this.mis_decided) {
				if (this.mis_state == MIS_State.IN_MIS) {
					g.setColor(Color.RED);
					mis_text = "INMIS";
				} else {
					g.setColor(Color.BLUE);
					mis_text = "NONMIS";
				}
			} else {
				g.setColor(Color.BLACK);
			}
			Font font = new Font(null, 0, (int) (fontSize * pt.getZoomFactor())); 
			FontMetrics fm = g.getFontMetrics(font); 
			int h = (int) Math.ceil(fm.getHeight());
			g.drawString(mis_text, pt.guiX, pt.guiY - (int)(h*0.5));
			g.setColor(c);
		}
	}

	@Override
	public String toString() {
		return "Node(ID="+this.ID+", Color="+this.six_vcol_color+")";
	}
	
//	@NodePopupMethod(menuText = "Color children") 
//	public void colorKids() {
//		MarkMessage msg = new MarkMessage();
//		MessageTimer timer = new MessageTimer(msg);
//		timer.startRelative(1, this);
//	}

	
	
}
