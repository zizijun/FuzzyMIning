/*
 * Copyright (c) 2007 Christian W. Guenther (christian@deckfour.org)
 * 
 * LICENSE:
 * 
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 * 
 * EXEMPTION:
 * 
 * License to link and use is also granted to open source programs which are not
 * licensed under the terms of the GPL, given that they satisfy one or more of
 * the following conditions: 1) Explicit license is granted to the ProM and
 * ProMimport programs for usage, linking, and derivative work. 2) Carte blance
 * license is granted to all programs developed at Eindhoven Technical
 * University, The Netherlands, or under the umbrella of STW Technology
 * Foundation, The Netherlands. For further exemptions not covered by the above
 * conditions, please contact the author of this code.
 */
package org.processmining.models.graphbased.directed.fuzzymodel.transform;

import java.util.ArrayList;
import java.util.Set;

import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.graphbased.directed.fuzzymodel.FMClusterNode;
import org.processmining.models.graphbased.directed.fuzzymodel.FMNode;
import org.processmining.models.graphbased.directed.fuzzymodel.MutableFuzzyGraph;

/**
 * @author christian
 * @author Jiafei Li
 */
public class SimpleTransformer extends FuzzyGraphTransformer {

	protected double threshold;
	protected MutableFuzzyGraph graph;

	public SimpleTransformer(PluginContext context) {
		super(context, "Simple transformer");
		threshold = 1.0;
	}

	public void setThreshold(double aThreshold) {
		threshold = aThreshold;
	}

	public double getThreshold() {
		return threshold;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.processmining.mining.fuzzymining.graph.transform.FuzzyGraphTransformer
	 * #transform(org.processmining.mining.fuzzymining.graph.FuzzyGraph)
	 */
	public void transform(MutableFuzzyGraph graph) {
		this.graph = graph;
		// clean up edges
		cleanUpEdges(threshold * 0.1, 1.0);// threshold * 0.3);
		// determine simplification victims
		ArrayList<FMNode> simplificationVictims = new ArrayList<FMNode>();
		FMNode n;
		for (int i = 0; i < graph.getNumberOfInitialNodes(); i++) {
			n = graph.getPrimitiveNode(i);
			if (n.getSignificance() < threshold) {
				simplificationVictims.add(n);
			}
		}
		// create clusters
		int clusterIndex = graph.getNumberOfInitialNodes() + 1;
		while (simplificationVictims.size() > 0) {
			FMClusterNode cluster = new FMClusterNode(graph, clusterIndex, "");
			graph.addClusterNode(cluster);
			clusterIndex++;
			boolean nodeAdded = true;
			while (nodeAdded == true) {
				nodeAdded = false;
				ArrayList<FMNode> clustered = new ArrayList<FMNode>();
				for (FMNode node : simplificationVictims) {
					if (shouldMergeWith(node, cluster) == true) {
						cluster.add(node);
						graph.setNodeAliasMapping(node.getIndex(), cluster);
						clustered.add(node);
						nodeAdded = true;
					}
				}
				simplificationVictims.removeAll(clustered);
			}
		}
		// merge clusters
		mergeAllClusters();
		// remove unary clusters
		for (int i = graph.getClusterNodes().size() - 1; i >= 0; i--) {
			FMClusterNode cluster = graph.getClusterNodes().get(i);
			if (cluster.getPrimitives().size() == 1) {
				// unary cluster; remove
				for (int k = 0; k < graph.getNumberOfInitialNodes(); k++) {
					FMNode mapping = graph.getNodeMappedTo(k);
					if ((mapping != null) && mapping.equals(cluster)) {
						graph.setNodeAliasMapping(k, null);
					}
				}
				graph.removeClusterNode(cluster);
			}
		}
		// clean up edges
		cleanUpEdges(threshold, threshold);
	}

	protected boolean shouldMergeWith(FMNode node, FMClusterNode cluster) {
		if (cluster.getPrimitives().size() == 0) {
			return true; // always add first node
		}
		if ((cluster.isDirectlyConnectedTo(node) == true) && (cluster.contains(node) == false)) {
			double ownSignificance = 0.0;
			double otherSignificance = 0.0;
			double ownCorrelation = 0.0;
			double otherCorrelation = 0.0;
			// aggregate correlation and significance of edges between the node in
			// question and connected nodes, separately for edges to/from cluster and
			// to/from other nodes.
			for (int i = 0; i < graph.getNumberOfInitialNodes(); i++) {
				if (i == node.getIndex()) {
					continue; // ignore self-references
				}
				if (cluster.contains(graph.getPrimitiveNode(i))) {
					ownSignificance += graph.getBinarySignificance(node.getIndex(), i);
					ownSignificance += graph.getBinarySignificance(i, node.getIndex());
					ownCorrelation += graph.getBinaryCorrelation(node.getIndex(), i);
					ownCorrelation += graph.getBinaryCorrelation(i, node.getIndex());
				} else {
					otherSignificance += graph.getBinarySignificance(node.getIndex(), i);
					otherSignificance += graph.getBinarySignificance(i, node.getIndex());
					otherCorrelation += graph.getBinaryCorrelation(node.getIndex(), i);
					otherCorrelation += graph.getBinaryCorrelation(i, node.getIndex());
				}
			}
			// make a decision
			return ((ownCorrelation > otherCorrelation) || (ownSignificance > otherSignificance));
		} else {
			// no connection - no merge.
			return false;
		}
	}

	protected boolean shouldMerge(FMClusterNode first, FMClusterNode second) {
		if (first.equals(second)) {
			return false;
		} else if (first.directlyFollows(second) && second.directlyFollows(first)) {
			return true; // connected in both directions: merge
		} else {
			Set<FMNode> preFirst = first.getPredecessors();
			Set<FMNode> preSecond = second.getPredecessors();
			if (doSignificantlyOverlap(preFirst, preSecond)) {
				return true;
			}
			/*
			 * HashSet<Node> preBoth = new HashSet<Node>();
			 * preBoth.addAll(preFirst); preBoth.retainAll(preSecond);
			 * if((preBoth.size() * 2) > (preFirst.size() + preSecond.size())) {
			 * return true; }
			 */
			Set<FMNode> postFirst = first.getSuccessors();
			Set<FMNode> postSecond = second.getSuccessors();
			return doSignificantlyOverlap(postFirst, postSecond);
			/*
			 * HashSet<Node> postBoth = new HashSet<Node>();
			 * postBoth.addAll(postFirst); postBoth.retainAll(postSecond);
			 * if((postBoth.size() * 2) > (postFirst.size() +
			 * postSecond.size())) { return true; } else { return false; }
			 */
		}
	}

	protected boolean doSignificantlyOverlap(Set<FMNode> first, Set<FMNode> second) {
		int limit = first.size() + second.size();
		int match = 0;
		for (FMNode node : first) {
			if (second.contains(node)) {
				match += 2;
				if (match > limit) {
					return true;
				}
			}
		}
		return false;
	}

	protected FMClusterNode mergeClusters(FMClusterNode first, FMClusterNode second) {
		for (FMNode node : second.getPrimitives()) {
			first.add(node);
			graph.setNodeAliasMapping(node.getIndex(), first);
		}
		graph.removeClusterNode(second);
		return first;
	}

	protected boolean mergeAllPossibleInto(FMClusterNode cluster) {
		ArrayList<FMClusterNode> victims = new ArrayList<FMClusterNode>(graph.getClusterNodes());
		boolean success = false;
		for (FMClusterNode other : victims) {
			if (shouldMerge(cluster, other) == true) {
				mergeClusters(cluster, other);
				success = true;
			}
		}
		return success;
	}

	protected void mergeAllClusters() {
		boolean keepOn = true;
		while (keepOn == true) {
			keepOn = false;
			for (FMClusterNode cluster : graph.getClusterNodes()) {
				if (mergeAllPossibleInto(cluster) == true) {
					keepOn = true;
					break;
				}
			}
		}
	}

	protected void cleanUpEdges(double significanceThreshold, double correlationThreshold) {
		for (int x = 0; x < graph.getNumberOfInitialNodes(); x++) {
			for (int y = 0; y < graph.getNumberOfInitialNodes(); y++) {
				if ((graph.getNodeMappedTo(x) == null)
						|| (graph.getNodeMappedTo(y) == null)
						|| ((graph.getBinarySignificance(x, y) < significanceThreshold) && (graph.getBinaryCorrelation(
								x, y) < correlationThreshold))) {
					graph.setBinarySignificance(x, y, 0.0);
					graph.setBinaryCorrelation(x, y, 0.0);
				}
			}
		}
	}

}
