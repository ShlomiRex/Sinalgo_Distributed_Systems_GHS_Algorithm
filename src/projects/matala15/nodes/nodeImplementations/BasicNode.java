// Author: Evyatar Bitton
// ID: 311432306

package projects.matala15.nodes.nodeImplementations;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import projects.defaultProject.nodes.timers.MessageTimer;
import projects.matala12.nodes.messages.*;
import projects.matala12.nodes.messages.DecideMISMessage.MISDecision;
import projects.sample6.nodes.messages.MarkMessage;
import sinalgo.configuration.WrongConfigurationException;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.Node.NodePopupMethod;
import sinalgo.nodes.edges.Edge;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;

/**
 * An internal node (or leaf node) of the tree. 
 */
public class BasicNode extends Node {
	/// Static members
	private static int FINAL_NUM_OF_COLORS = 8;
	
	/// Data Fields
	private static int MAX_BINARY_LENGTH;
	private static int ROUNDS_TO_FINISH_COLORING;
	
	public int nodeColor;
	public String nodeColorBinary;
	public ClientNode parent = null; // the parent in the tree, null if this node is the root

	private boolean isRoot = false;
	private boolean receivedRound = false;
	
	private int coloringRoundCounter;
	private int colorBinaryCurrentMaxLength;
	private boolean hasStartedColoring = false;
	private boolean isCurrentlyColoring = false;
	
	private int MISRoundCounter = 0;
	private boolean decidedMIS = false;
	private boolean neighborInMainMIS = false;
	private boolean neighborInSecondaryMIS = false;
	
	// Methods
	@Override
	public void checkRequirements() throws WrongConfigurationException {
	}

	@Override
	public void handleMessages(Inbox inbox) {			
		if (isCurrentlyColoring)
		{
			if (coloringRoundCounter <= BasicNode.ROUNDS_TO_FINISH_COLORING) {
				// Color the graph
				six_Vcol(inbox);		
				coloringRoundCounter++;	
			}
			else {
				// Finished coloring phase - Start MIS selection phase.
				isCurrentlyColoring = false;
			}			
		}
		else if (!decidedMIS)
		{
			// Create the main and secondary MIS from the graph
			VCol_MIS(inbox);
		}		
	}
	

	/**
	 * Algorithm 9.9 page 126 from the book (Six_Vcol)
	 * @param inbox Incoming messages
	 */
	private void six_Vcol(Inbox inbox) {
		while(inbox.hasNext()) {
			Message m = inbox.next();
			
			if(m instanceof RoundColorMessage) {
				
				if(receivedRound || parent != null && !inbox.getSender().equals(parent)) {
					continue;// only consider round messages once per round and from the parent
				}
				
				// Get color of parent. Note that root has no parent, and so its parent color doesn't matter
				String parentColorBinary;
				if (!isRoot) {
					parentColorBinary = ((RoundColorMessage) m).senderColorBinary;	
				}
				else {
					parentColorBinary = "0";
				}
				
				// Use the parent color to get the new node color
				this.nodeColor = createNewColorFromParent(this.nodeColorBinary, parentColorBinary);
				this.nodeColorBinary = Integer.toBinaryString(this.nodeColor);

				receivedRound = true;
			}
		}
	}

	// Modified Algorithm 10.5 from the book
	private void VCol_MIS(Inbox inbox) {
		while(inbox.hasNext()) {
			Message m = inbox.next();
			
			if(m instanceof RoundMISMessage) {

				if(receivedRound) {
					continue;// only consider round messages once per round
				}
				
				if (nodeColor == ((RoundMISMessage) m).roundNumber) {
					// Round to handle this color - attempt to enter a MIS depending on neighbors
					decidedMIS = true;
				}

				receivedRound = true;
			}
			else if (m instanceof DecideMISMessage) {
				MISDecision neighborDecision = ((DecideMISMessage) m).decision;
				
				if (neighborDecision == MISDecision.IN_MAIN_MIS) { 
					neighborInMainMIS = true;
				}
				else if (neighborDecision == MISDecision.IN_SECONDARY_MIS) {
					neighborInSecondaryMIS = true;
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
		// First round - start the coloring process concurrently at all the nodes 
		if (!this.hasStartedColoring) {
			
			// Set default data for the root node
			if (this.parent == null) {
				this.isRoot = true;
				this.nodeColor = 0;
			}
			else
			{
				// Set initial color value to a unique color that isn't part of colors[0-7]
				//this.nodeColor = super.ID + TreeNode.FINAL_NUM_OF_COLORS;
				this.nodeColor = super.ID;
			}
			
			// Get the initial binary value of nodeColor
			this.nodeColorBinary = Integer.toBinaryString(this.nodeColor);
			// Make sure it has the same number of bits as the biggest nodeColor
			colorBinaryCurrentMaxLength = BasicNode.MAX_BINARY_LENGTH;
			// TODO remove
			//while(nodeColorBinary.length()<TreeNode.MAX_BINARY_LENGTH) { 
			//	this.nodeColorBinary = "0" + this.nodeColorBinary;
			//}
			
			isCurrentlyColoring = true;
			hasStartedColoring = true;
			coloringRoundCounter = 0;
		}
	}

	@Override
	public void postStep() {
		// Send color to all neighbors during coloring phase
		if (isCurrentlyColoring) {
			for(Edge e : outgoingConnections) {
				RoundColorMessage colorUpdateMessage = new RoundColorMessage(this.nodeColorBinary);
				send(colorUpdateMessage, e.endNode);
			}
		}
		else if (MISRoundCounter <= FINAL_NUM_OF_COLORS)
		{
			if (decidedMIS) {
				makeMISDecision();
			}
			
			RoundMISMessage message = new RoundMISMessage(MISRoundCounter);
			sendDirect(message, this);
			MISRoundCounter++;
		}

		receivedRound = false;
	}
	
	private void makeMISDecision() {
		DecideMISMessage message;
		if (!neighborInMainMIS) {
			// No neighbor in the main MIS - enter it
			message = new DecideMISMessage(MISDecision.IN_MAIN_MIS);
			setColor(Color.RED);
		}
		else if (!neighborInSecondaryMIS) {
			// Neighbor in main MIS, but no neighbor in secondary MIS - enter it
			message = new DecideMISMessage(MISDecision.IN_SECONDARY_MIS);
			setColor(Color.YELLOW);
		}
		else {
			// Neighbors in both MIS - do not enter any MIS
			message = new DecideMISMessage(MISDecision.NOT_IN_MIS);
			setColor(Color.GREEN);
		}
	
		// Send the message to all neighbors
		for(Edge e : outgoingConnections) {
			send(message, e.endNode);
		}
	}

	@Override
	public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
		// overwrite the draw method to change how the GUI represents this node
		super.drawNodeAsDiskWithText(g, pt, highlight, Integer.toString(this.nodeColor), 12, Color.CYAN);
	}

	private int createNewColorFromParent(String selfColorBinary, String parentColorBinary)
	{		
		// Make sure all color numbers in a round use the same number of bits 
		while(selfColorBinary.length() < colorBinaryCurrentMaxLength) { 
			// Self color binary number is too short.	
			selfColorBinary = "0" + selfColorBinary;
		}
		while(parentColorBinary.length() <colorBinaryCurrentMaxLength) { 
			// Parent color binary number is too short.				
			parentColorBinary = "0" + parentColorBinary;
		}		
		
		// Update max color binary length for next round (number of possible colors)
		// Note that minimum length is at least 3
		colorBinaryCurrentMaxLength = (int)Math.ceil(Math.log(colorBinaryCurrentMaxLength) / Math.log(2)) + 1;
		if (colorBinaryCurrentMaxLength < 3) {
			colorBinaryCurrentMaxLength = 3;
		}
				
		int indexK = findIndexK(selfColorBinary, parentColorBinary);		

		// Create new color number by concating the binary value of indexK with the bit at orgColor[indexK]
		StringBuilder sb = new StringBuilder();
		sb.append(Integer.toBinaryString(indexK));
		sb.append(selfColorBinary.charAt(indexK));
		return Integer.parseInt(sb.toString(), 2);
	}

	private int findIndexK(String firstColorBinary, String secondColorBinary) {
		int indexK = 0;
		if(isRoot) {
			// Root does not have a parent number so it chooses a random k index
			indexK = (int )(Math.random() * (firstColorBinary.length() - 1));
		}		
		else {	
			// Find index of the first bit that is different between the first and the second colors
			for(int i=0; i < firstColorBinary.length(); i++) {
				if(firstColorBinary.charAt(i) != secondColorBinary.charAt(i)) {
					indexK=i;
					break;
				}				
			}
		}
		
		return indexK;
	}
	
	/**
	 * 
	 * @param maxN
	 */
	public static void setMaxN(int maxN)
	{
		BasicNode.ROUNDS_TO_FINISH_COLORING = logStar(maxN);
		
		// Number of bits in the maxColorValue = log_2 of it, rounded up
		int maxColorValue = maxN + BasicNode.FINAL_NUM_OF_COLORS;
		BasicNode.MAX_BINARY_LENGTH = (int)Math.ceil(Math.log(maxColorValue) / Math.log(2));
	}
		
	// Log Star per explanation in the guide book at page 96
	private static int logStar(int number) {
		if (number < 2) {
			return 0;
		}
		else {
			int numberLog2 = (int) (Math.log(number) / Math.log(2));
			return 1 + logStar(numberLog2);
		}
	}
}
