package projects.matala12.nodes.nodeImplementations;

import java.awt.Color;
import java.awt.Graphics;

import projects.sample6.nodes.messages.MarkMessage;

import sinalgo.configuration.WrongConfigurationException;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;

/**
 * A node on the bottom of the tree
 */

// NOTE: I leave this class because its needed in CustomGlobal.java. I don't have time to modify "build_tree" function to only use TreeNode.
public class LeafNode extends TreeNode {

	// A counter that may be reset by the user
	public static int smallIdCounter = 0;
	public int smallID;
	
	public LeafNode() {
		smallID = ++smallIdCounter;
		this.six_vcol_color = smallID;
	}
	
	@Override
	public void checkRequirements() throws WrongConfigurationException {
	}

	@Override
	public void init() {
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
	
//	public void draw(Graphics g, PositionTransformation pt, boolean highlight){
//		super.drawNodeAsDiskWithText(g, pt, highlight, Integer.toString(this.smallID), 15, Color.YELLOW);
//	}
	
//	public String toString() {
//		return smallID + " (" + ID + ")";
//	}

}
