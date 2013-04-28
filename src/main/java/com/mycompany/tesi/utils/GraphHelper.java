/*
 * Copyright 2013 Tommaso.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mycompany.tesi.utils;

import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeIterator;
import com.mycompany.tesi.SubgraphTask.Cell;
import com.mycompany.tesi.beans.BoundaryNode;
import com.mycompany.tesi.hooks.RawEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Tommaso
 */
public class GraphHelper {
    
    public static void union(final Graph graph, final Cell cell, final RawEncoder vehicle) {
        Map<Integer, Integer> inverse = new HashMap<>();
        for(Integer key: cell.graph2subgraph.keySet()) {
            int value = cell.graph2subgraph.get(key);
            graph.setNode(key, cell.graph.getLatitude(value), cell.graph.getLongitude(value));
            inverse.put(value, key);
        }
        
        AllEdgesIterator iterator = cell.graph.getAllEdges();
        while(iterator.next()) {
            EdgeIterator edge;
            if(cell.encoder.isForward(iterator.flags()))
                edge = graph.edge(inverse.get(iterator.baseNode()), inverse.get(iterator.adjNode()), iterator.distance(), vehicle.flags(cell.encoder.getSpeedHooked(iterator.flags()), cell.encoder.isBackward(iterator.flags())));
            else
                edge = graph.edge(inverse.get(iterator.adjNode()), inverse.get(iterator.baseNode()), iterator.distance(), vehicle.flags(cell.encoder.getSpeedHooked(iterator.flags()), cell.encoder.isForward(iterator.flags())));
            edge.wayGeometry(iterator.wayGeometry());
        }
        
    }
    
    public static GraphStorage cloneGraph(final Graph g, final BoundaryNode[] nodes) {
        GraphStorage g1 = new GraphBuilder().create();
        g1.combinedEncoder(RawEncoder.COMBINED_ENCODER);
        
        for(int i = 0; i < g.nodes(); i++)
            g1.setNode(i, g.getLatitude(i), g.getLongitude(i));
        
        Set<Integer> markedNodes = new TreeSet<>();
        for(int i = 0; i < nodes.length; i++)
            markedNodes.add(nodes[i].getRoadNodeId());
        AllEdgesIterator i = g.getAllEdges();
        while(i.next()) {
            if(markedNodes.contains(i.baseNode()) && markedNodes.contains(i.adjNode()))
                continue;
            g1.edge(i.baseNode(), i.adjNode(), i.distance(), i.flags()).wayGeometry(i.wayGeometry());
        }
        return g1;
    }
    
}
