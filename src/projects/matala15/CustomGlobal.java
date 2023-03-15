/*
 Copyright (c) 2007, Distributed Computing Group (DCG)
                    ETH Zurich
                    Switzerland
                    dcg.ethz.ch

 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 - Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 - Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the
   distribution.

 - Neither the name 'Sinalgo' nor the names of its contributors may be
   used to endorse or promote products derived from this software
   without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package projects.matala15;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import javax.swing.JOptionPane;

import projects.matala15.nodes.edges.WeightedEdge;
import projects.matala15.nodes.nodeImplementations.BasicNode;
import sinalgo.nodes.Node;
import sinalgo.nodes.Position;
import sinalgo.nodes.edges.Edge;
import sinalgo.runtime.AbstractCustomGlobal;
import sinalgo.runtime.Global;
import sinalgo.runtime.Runtime;
import sinalgo.tools.Tools;
import sinalgo.tools.logging.Logging;
import sinalgo.tools.statistics.Distribution;

/**
 * This class holds customized global state and methods for the framework. 
 * The only mandatory method to overwrite is 
 * <code>hasTerminated</code>
 * <br>
 * Optional methods to override are
 * <ul>
 * <li><code>customPaint</code></li>
 * <li><code>handleEmptyEventQueue</code></li>
 * <li><code>onExit</code></li>
 * <li><code>preRun</code></li>
 * <li><code>preRound</code></li>
 * <li><code>postRound</code></li>
 * <li><code>checkProjectRequirements</code></li>
 * </ul>
 * @see sinalgo.runtime.AbstractCustomGlobal for more details.
 * <br>
 * In addition, this class also provides the possibility to extend the framework with
 * custom methods that can be called either through the menu or via a button that is
 * added to the GUI. 
 */
public class CustomGlobal extends AbstractCustomGlobal{
	
	Logging logger = Logging.getLogger();
	Vector<BasicNode> graphNodes = new Vector<BasicNode>();
	
	Random random = Distribution.getRandom(); // Use configuration seed
	long randomSeed = Distribution.getSeed(); // Get seed from configuration
	
	// For generating nodes positions randomly on the surface (taken from defaultProject)
	projects.matala15.models.distributionModels.Random
		randomDistrubutionModel = new projects.matala15.models.distributionModels.Random(randomSeed);
	
	private int roundNum = 0;
	public static boolean IS_SIMULATION_TERMINATED = false;
	public static boolean IS_TOGGLE_DRAW_WEIGHTS = true;
	public static boolean IS_TOGGLE_DRAW_MST = true;
	public static boolean IS_TOGGLE_DRAW_MESSAGES_ON_EDGE = true;
	
	private long totalGraphWeight = 0; 	// Used in componenet 3
	private List<WeightedEdge> allEdges = new ArrayList<>();
	
	// Add random 7 edges to each node, by closest neighbors (I don't want messy graph, we can skip the distance check)
	private void addSevenEdgesPerNode() {
		logger.logln("Adding 7 edges...");
		long numTotalEdges = 0;
		for (BasicNode currentNode : graphNodes) {
			Position pos1 = currentNode.getPosition();
			List<Pair<Double, BasicNode>> distances = new ArrayList<>();
			
			// Iterate over all other nodes to check their distance
			for (BasicNode other : graphNodes) { 
				if (currentNode.equals(other))
					continue;
				Position pos2 = other.getPosition();
				double distance = pos1.distanceTo(pos2);
				distances.add(new Pair<Double, BasicNode>(distance, other));
			}
			
			// Sort the pairs, by distance
			distances.sort(new Comparator<Pair<Double, BasicNode>>() {
				@Override
				public int compare(Pair<Double, BasicNode> o1, Pair<Double, BasicNode> o2) {
					return (int) (o1.getA() - o2.getA());
				}
			});
			
			// Connect to closest neighbor that has less than 7 edges
			for (Pair<Double, BasicNode> pair : distances) {
				BasicNode other = pair.getB();
				if (currentNode.equals(other) || currentNode.isConnectedTo(other))
					continue;
				
				int numNeighbors = currentNode.getNeighbors().size();
				int otherNumNeighbors = other.getNeighbors().size();
				if (numNeighbors >= 7)
					break;
				if (otherNumNeighbors >= 7)
					continue;
				
				// Add edge (both are not connected[first 'if'], and their number of edges is OK)
				currentNode.addBidirectionalConnectionTo(other);
				
				//logger.logln("Node " + currentNode + " connects to " + other);
				currentNode.addNighbor(other);
				other.addNighbor(currentNode);
				numTotalEdges ++;
				
				//int curOutSize = currentNode.outgoingConnections.size();
				//int otherOutSize = other.outgoingConnections.size();
				//logger.logln("curOutSize, otherOutSize = " + curOutSize + ", " + otherOutSize);
				
				// Generate weight for the added edge
				int weight = random.nextInt(1_000_000_000) + 1;
				// Find the added edge
				for (Edge e : currentNode.outgoingConnections) {
					if (e.endNode.equals(other)) {
						WeightedEdge weightedEdge = (WeightedEdge) e;
						//logger.logln("Edge added: " + weightedEdge);
						weightedEdge.setWeight(weight);
						totalGraphWeight += weight;
						weightedEdge.setIsDrawWeight(true);
						allEdges.add(weightedEdge);
					}
				}
				// Find the second added edge (bidirectional = 2 edges)
				for (Edge e : other.outgoingConnections) {
					if (e.endNode.equals(currentNode)) {
						WeightedEdge weightedEdge = (WeightedEdge) e;
						weightedEdge.setWeight(weight); // We set the weight so both unidirectional edges have the same weight
						weightedEdge.setIsDrawWeight(false); // We only want to draw text once per edge
					}
				}
			}
		}
		logger.logln("Total number of edges: " + numTotalEdges);
	}

	@AbstractCustomGlobal.CustomButton(buttonText="Build custom graph", toolTipText="Builds a custom graph")
	public void buildCustomGraph() {
		// remove all nodes (if any)
		Runtime.clearAllNodes();
		graphNodes.clear();
		
		// Number of nodes to create
		int defaultNumOfNodes = 10; // TODO: Change to default higher number
		int numOfNodes;
		String strNumNodes = JOptionPane.showInputDialog(null, "How many nodes to generate? (default: " + defaultNumOfNodes + ")");
		try {
			numOfNodes = Integer.parseInt(strNumNodes);			
		} catch(NumberFormatException e) {
			numOfNodes = defaultNumOfNodes;
		}
		
		// Create nodes
		for (int i = 0; i < numOfNodes; i++) {
			BasicNode node = new BasicNode();
			node.setPosition(randomDistrubutionModel.getNextPosition());
			graphNodes.add(node);
		}		
		
		// Add edges
		addSevenEdgesPerNode();
		//Tools.reevaluateConnections();
		
		
		// Finalize (no idea why without this line, I don't see the nodes/edges)
		for (BasicNode node : graphNodes) {
			node.finishInitializationWithDefaultModels(true);
		}
		
		// Repaint the GUI as we have added some nodes
		Tools.repaintGUI();
	}

	@AbstractCustomGlobal.CustomButton(buttonText="Toggle weights", toolTipText="Toggle between drawing weights on edges or not")
	public void toggleDrawWeights() {
		IS_TOGGLE_DRAW_WEIGHTS = !IS_TOGGLE_DRAW_WEIGHTS;
		Tools.repaintGUI();
	}
	
	@AbstractCustomGlobal.CustomButton(buttonText="Toggle only MST edges", toolTipText="Toggle between drawing edge that is associated with MST (directed torwards any node)")
	public void toggleMST() {
		IS_TOGGLE_DRAW_MST = !IS_TOGGLE_DRAW_MST;
		Tools.repaintGUI();
	}
	
	@AbstractCustomGlobal.CustomButton(buttonText="Toggle messages", toolTipText="Toggle between drawing messages on edge or not")
	public void toggleDrawMessages() {
		IS_TOGGLE_DRAW_MESSAGES_ON_EDGE = !IS_TOGGLE_DRAW_MESSAGES_ON_EDGE;
		Tools.repaintGUI();
	}
	
	@Override
	public void preRun() {
		super.preRun();
		logger.logln("preRun");
		buildCustomGraph();
	}
	
	@Override
	public void preRound() {
		super.preRound();
		
		logger.logln("Round: "+roundNum+" preRound");
	}
	
	@Override
	public void postRound() {
		super.postRound();

		logger.logln("postRound\n\n");			
		roundNum ++;
	}
	
	@Override
	public boolean hasTerminated() {
		return false;
	}

	@AbstractCustomGlobal.CustomButton(buttonText="Run Component 3", toolTipText="Calculates graph weight, and MST weight")
	public void calculateWeightsOfComponent3() {
		logger.logln("Calculating componenet 3...");
		logger.logln("Total nodes in graph: "+graphNodes.size());
		logger.logln("Total graph weight: "+convertToNiceWeight(totalGraphWeight));
		
		// Calculate MST weight
		long mstGraphWeight = 0;
		
		// Get MST root
		int mstRootId = graphNodes.get(0).getFragmentLeaderId(); // After algo finishes, there exist only one fragment with one leader.
		BasicNode mstRoot = null;
		for (BasicNode n : graphNodes)
			if (n.ID == mstRootId)
				mstRoot = n;
		
		// Iterate over all children of root
		for (BasicNode n : mstRoot.getNeighbors()) {
			// If neighbor is also child in MST
			if (n.getMSTParentId() == mstRootId) {
				// Calculate weight of subtree of this child
				mstGraphWeight += n.getEdgeTo(mstRootId).getWeight();
				mstGraphWeight += calculateWeightOfSubtree(n);
			}
		}
		
		logger.logln("Total MST graph weight: " +convertToNiceWeight(mstGraphWeight));
	}
	
	private long calculateWeightOfSubtree(BasicNode root) {
		long subtreeWeight = 0;
		
		// Iterate over all children of root
		for (BasicNode n : root.getNeighbors()) {
			// If neighbor is also child in MST
			if (n.getMSTParentId() == root.ID) {
				// Calculate weight of subtree of this child
				subtreeWeight += n.getEdgeTo(root.ID).getWeight();
				subtreeWeight += calculateWeightOfSubtree(n);
			}
		}
		
		return subtreeWeight;
	}
	
	private String convertToNiceWeight(long weight) {
		String nice_weight = String.format("%,d", weight);
		return "\""+nice_weight+"\"";
	}
	
}
