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

import projects.matala15.nodes.edges.WeightedEdge;
import projects.matala15.nodes.nodeImplementations.BasicNode;
import sinalgo.models.ConnectivityModelHelper;
import sinalgo.nodes.Node.NodePopupMethod;
import sinalgo.nodes.Position;
import sinalgo.nodes.edges.Edge;
import sinalgo.runtime.AbstractCustomGlobal;
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
	Random random = Distribution.getRandom(); // Use configuration seed
	Vector<BasicNode> graphNodes = new Vector<BasicNode>();
	
	// Number of nodes to create
	int numOfNodes = 20;
	
	// I set seed so each time I click on the button, it generates exactly the same graph each time
	// We can later change this to not use seed, so its random. But for testing purposes I use seed.
	long randomSeed = 2023;
	
	// For generating nodes positions randomly on the surface (taken from defaultProject)
	projects.matala15.models.distributionModels.Random
		randomDistrubutionModel = new projects.matala15.models.distributionModels.Random(randomSeed);
	
	// For generating nodes edges randomly (taken from defaultProject)
	double rMax = 50;
	projects.matala15.models.connectivityModels.UDG 
		unitDiskConnectivity = new projects.matala15.models.connectivityModels.UDG(rMax);
	
	@Override
	public void preRun() {
		super.preRun();
	}
	
	public boolean hasTerminated() {
		return false;
	}

	@AbstractCustomGlobal.CustomButton(buttonText="Build Sample Graph", toolTipText="Builds a sample graph")
	public void buildSampleGraph() {
		// remove all nodes (if any)
		Runtime.clearAllNodes();
		graphNodes.clear();
				
		// Create nodes
		for (int i = 0; i < numOfNodes; i++) {
			BasicNode basicNode = new BasicNode();
			basicNode.setPosition(randomDistrubutionModel.getNextPosition());
			graphNodes.add(basicNode);
		}
		
		// Create connections randomly automatically
//		Tools.reevaluateConnections();
		
		long numTotalEdges = 0;
		
		// Add random 7 edges to each node, by closest neighbors (I don't want messy graph, we can skip the distance check)
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
						weightedEdge.setIsDraw(true);
					}
				}
				// Find the second added edge (bidirectional = 2 edges)
				for (Edge e : other.outgoingConnections) {
					if (e.endNode.equals(currentNode)) {
						WeightedEdge weightedEdge = (WeightedEdge) e;
						weightedEdge.setWeight(weight); // We set the weight so both unidirectional edges have the same weight
						weightedEdge.setIsDraw(false); // We only want to draw text once per edge
					}
				}
			}
		}
		
		logger.logln("Total number of edges: " + numTotalEdges);
			
		// Finalize (no idea why without this line, I don't see the nodes/edges)
		for (BasicNode node : graphNodes) {
//			logger.logln("Node: " + node);
//			for (Edge e : node.outgoingConnections) {
//				logger.logln("Edge: " + (WeightedEdge)e);	
//			}
			node.finishInitializationWithDefaultModels(true);
		}
		
		// Repaint the GUI as we have added some nodes
		Tools.repaintGUI();
	}
	
	//TODO: Ask user how many nodes
//	@AbstractCustomGlobal.CustomButton(buttonText="Build Custom Graph", toolTipText="Builds a custom graph")
//	public void buildCustomGraph() {
//		
//	}
	
	@NodePopupMethod(menuText="Multicast 2")
	public void myPopupMethod() {
		System.out.println("OK");
	}
}
