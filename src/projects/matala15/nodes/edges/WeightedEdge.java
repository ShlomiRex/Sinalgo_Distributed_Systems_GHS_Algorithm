package projects.matala15.nodes.edges;

import java.awt.Graphics;

import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Position;
import sinalgo.nodes.edges.BidirectionalEdge;

public class WeightedEdge extends BidirectionalEdge {
	
	private int weight;
	private boolean isDraw = false; 
	
	public void setWeight(int weight) {
		this.weight = weight;
	}
	
	public long getWeight() {
		return weight;
	}
	
	public void setIsDraw(boolean value) {
		this.isDraw = value;
	}
	
	@Override
	public void draw(Graphics g, PositionTransformation pt) {
		super.draw(g, pt);
		
		if (weight == 0 || isDraw == false)
			return;
		
		// Draw text weight on the edge
		Position start = this.startNode.getPosition();
		Position end = this.endNode.getPosition();
		
		pt.translateToGUIPosition(start);
		int fromX = pt.guiX, fromY = pt.guiY;
		pt.translateToGUIPosition(end);
		int toX = pt.guiX, toY = pt.guiY;
		
		int x = (int) ((toX + fromX)/2);
		int y = (int) ((toY + fromY)/2);
		
		g.drawString(""+this.weight, x, y);
	}
	
	@Override
	public String toString() {
		return "WeightedEdge("+weight+", " + isDraw + ")";
	}
}
