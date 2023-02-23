package projects.matala15.nodes.nodeImplementations;

import java.util.ArrayList;
import java.util.List;

import sinalgo.configuration.WrongConfigurationException;
import sinalgo.nodes.Node;
import sinalgo.nodes.messages.Inbox;
import sinalgo.tools.logging.Logging;

/**
 * An internal node (or leaf node) of the tree. 
 */
public class BasicNode extends Node {
	
	List<BasicNode> neighbors = new ArrayList<>();
	Logging logger = Logging.getLogger();
	
	public void addNighbor(BasicNode other) {
		neighbors.add(other);
	}
	
	public List<BasicNode> getNeighbors() {
		return neighbors;
	}
	
	public boolean isConnectedTo(BasicNode other) {
		return neighbors.contains(other);
	}
	
	@Override
	public void checkRequirements() throws WrongConfigurationException {
	}

	@Override
	public void handleMessages(Inbox inbox) {			
		
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
	
//	@Override
//	public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
//		// overwrite the draw method to change how the GUI represents this node
//		super.drawNodeAsDiskWithText(g, pt, highlight, Integer.toString(this.nodeColor), 12, Color.CYAN);
//	}
}
