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
import com.graphhopper.util.PointList;
import com.mycompany.tesi.Main;
import com.mycompany.tesi.SubgraphTask.Cell;
import com.mycompany.tesi.beans.BoundaryNode;
import com.mycompany.tesi.hooks.RawEncoder;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;
import java.sql.*;
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
            int flags = iterator.flags();
            if(cell.encoder.isForward(flags)) {
                edge = graph.edge(inverse.get(iterator.baseNode()), inverse.get(iterator.adjNode()), iterator.distance(), vehicle.flags(cell.encoder.getSpeedHooked(flags), cell.encoder.isBackward(flags)));
                edge.wayGeometry(iterator.wayGeometry());
            }
            else {
                edge = graph.edge(inverse.get(iterator.adjNode()), inverse.get(iterator.baseNode()), iterator.distance(), vehicle.flags(cell.encoder.getSpeedHooked(flags), cell.encoder.isForward(flags)));
                PointList pillars = iterator.wayGeometry();
                pillars.reverse();
                edge.wayGeometry(pillars);
            }
        }
        
    }
    
    public static GraphStorage cloneGraph(final Graph g, final Cell start, final Cell end) {
        GraphStorage g1 = new GraphBuilder().create();
        g1.combinedEncoder(RawEncoder.COMBINED_ENCODER);
        
        for(int i = 0; i < g.nodes(); i++)
            g1.setNode(i, g.getLatitude(i), g.getLongitude(i));
        
        Set<Integer> markedNodes1 = new TreeSet<>();
        Set<Integer> markedNodes2 = new TreeSet<>();
        for(BoundaryNode node: start.boundaryNodes)
            markedNodes1.add(node.getRoadNodeId());
        
        for(BoundaryNode node: end.boundaryNodes)
            markedNodes2.add(node.getRoadNodeId());
        
        AllEdgesIterator i = g.getAllEdges();
        while(i.next()) {
            if((markedNodes1.contains(i.baseNode()) && markedNodes1.contains(i.adjNode())) || (markedNodes2.contains(i.baseNode()) && markedNodes2.contains(i.adjNode())))
                continue;
            g1.edge(i.baseNode(), i.adjNode(), i.distance(), i.flags()).wayGeometry(i.wayGeometry());
        }
        return g1;
    }
    
    public static PointList getPillars(Geometry g) {
        int pillarNumber = g.getNumPoints() - 2;
        if(pillarNumber > 0) {
            PointList pillarNodes = new PointList(pillarNumber);
            for(int i = 1; i <= pillarNumber; i ++) {
                pillarNodes.add(g.getCoordinates()[i].getOrdinate(1), g.getCoordinates()[i].getOrdinate(0));
                //System.out.println("lat=" + g.getCoordinates()[i].getOrdinate(1) +" lon = "+ g.getCoordinates()[i].getOrdinate(0));
            }
            return pillarNodes;
        }
        return null;
    }
    
    public static GraphStorage readGraph(String dbName, String table, RawEncoder vehicle) throws Exception {
        WKTReader reader = new WKTReader();
        GraphStorage graph = new GraphBuilder().create();
        graph.combinedEncoder(RawEncoder.COMBINED_ENCODER);
        //RawEncoder vehicle = new MyCarFlagEncoder(130);
        
        try(Connection conn = Main.getConnection(dbName); 
            Statement st = conn.createStatement()) {
            ResultSet rs;
            rs = st.executeQuery("select * from ((select distinct source, y1, x1 from " + table + ") union (select distinct target, y2, x2 from " + table + ")) nodes order by source");
            while(rs.next())
                graph.setNode(rs.getInt(1), rs.getDouble(2), rs.getDouble(3));
            rs.close();
            rs = st.executeQuery("select source, target, km*1000, reverse_cost<>1000000, freeflow_speed, st_astext(the_geom) from " + table);//st_astext(the_geom)
            while(rs.next()) {
                EdgeIterator edge = graph.edge(rs.getInt(1), rs.getInt(2), rs.getDouble(3), vehicle.flags(rs.getDouble(5), rs.getBoolean(4)));
                String wkt = rs.getString(6);
                if(wkt != null) {
                    Geometry geometry = reader.read(wkt);
                    edge.wayGeometry(GraphHelper.getPillars(geometry));
                }
            }
            rs.close();
        }
        return graph;
    }
}
