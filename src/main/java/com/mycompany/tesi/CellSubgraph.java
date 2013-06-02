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
package com.mycompany.tesi;

import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeIterator;
import static com.mycompany.tesi.SubgraphTask.MAX_SPEED;
import com.mycompany.tesi.beans.BoundaryNode;
import com.mycompany.tesi.beans.Metrics;
import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.hooks.MyCarFlagEncoder;
import com.mycompany.tesi.hooks.RawEncoder;
import com.mycompany.tesi.hooks.TimeCalculation;
import com.mycompany.tesi.utils.GraphHelper;
import com.mycompany.tesi.utils.TileSystem;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tommaso
 */
public class CellSubgraph {
    
    private final static String dbName = Main.DBS[0];
    private final static int scale = 13;
    //private final static String qkey = "031313131133"; 
    private final static String qkey ="0313131311310";// "1202020202003";//level 13
    private static TileSystem tileSystem;
    private static GeometryFactory geometryFactory = new GeometryFactory();
    private final WKTReader reader = new WKTReader();
    public static final String sql1 = "SELECT ways.gid, ways.source, ways.target, ways.freeflow_speed, ways.length, ways.reverse_cost<>1000000 AS bothdir, ways.km*1000 AS distance, ways.x1, ways.y1, ways.x2, ways.y2, st_astext(ways.the_geom) AS geometry " + //st_contains(shape, the_geom) AS contained
            "FROM ways JOIN ways_tiles ON gid = ways_id JOIN tiles ON tiles_qkey = qkey WHERE qkey = ? ORDER BY gid";
    
    
    private PreparedStatement st1, st2;
    private Set<Integer> innerEdges = new TreeSet<>();
    
    public CellSubgraph() {
        try(Connection conn = Main.getConnection(dbName)) {
            st1 = conn.prepareStatement(sql1);
            SubgraphTask.Cell cell = buildSubgraph(qkey, false);
            String sql = "INSERT INTO cell_ways SELECT * FROM ways WHERE gid=ANY(?);";
            try(PreparedStatement st = conn.prepareStatement(sql)) {
                st.setArray(1, conn.createArrayOf("int", innerEdges.toArray(new Integer[innerEdges.size()])));
                st.executeUpdate();
                
            }
        } catch(Exception e) {
            System.err.println(e);
        }
    }
    
    public static void main(String[] args) {
        System.out.println("not implemented");
        System.exit(0);
        tileSystem = Main.getTileSystem(dbName);        
        SubgraphTask.Cell cell;
        try {
            //cell = new CellSubgraph().buildSubgraph(qkey, false);
            SubgraphTask task = new SubgraphTask(tileSystem, dbName, scale);
            cell = task.getSubgraph(qkey, true);
            
            System.out.println(cell.boundaryNodes.size());
        } catch (Exception ex) {
            Logger.getLogger(CellSubgraph.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    
    
    /**
     * Build the interior cell subgraph for the given quadkey
     * @param qkey
     * @param withGeometry - add the way geometry
     * @return
     * @throws Exception 
     */
    public SubgraphTask.Cell buildSubgraph(String qkey, boolean withGeometry) throws Exception {
        Tile tile = tileSystem.getTile(qkey);
        Polygon rect = tile.getPolygon();
        Coordinate[] coordinates = { 
            new Coordinate(tile.getRect().getMinX(), tile.getRect().getMinY()),
            new Coordinate(tile.getRect().getMaxX(), tile.getRect().getMinY()),
            new Coordinate(tile.getRect().getMaxX(), tile.getRect().getMaxY())
        };
        LineString line = geometryFactory.createLineString(coordinates);
        Set<BoundaryNode> boundaryNodes = new TreeSet<>();
        GraphStorage graph = new GraphBuilder().create();
        graph.combinedEncoder(RawEncoder.COMBINED_ENCODER);
        RawEncoder vehicle = new MyCarFlagEncoder(MAX_SPEED);
        Map<Integer, Integer> nodes = new HashMap<>(); // graph to subgraph nodes
        int count = 0;
        st1.clearParameters();
        st1.setString(1, qkey);
        ResultSet rs;
        rs = st1.executeQuery();
        while(rs.next()) { // for each way in a tile
            Integer s = nodes.get(rs.getInt("source"));
            if(s == null) {
                nodes.put(rs.getInt("source"), s = count); 
                graph.setNode(s, rs.getDouble("y1"), rs.getDouble("x1"));
                count ++;
            }
            Integer t = nodes.get(rs.getInt("target"));
            if(t == null) {
                nodes.put(rs.getInt("target"), t = count); 
                graph.setNode(t, rs.getDouble("y2"), rs.getDouble("x2"));
                count ++;
            }
            /* Map<String, Object> p = new HashMap<>();
            p.put("caroneway", !rs2.getBoolean("bothdir"));
            p.put("car", rs2.getInt("freeflow_speed"));
            int flags = new AcceptWay(true, true, true).toFlags(p); */
            int flags = vehicle.flags(rs.getDouble("freeflow_speed"), rs.getBoolean("bothdir"));
            Point p1 = geometryFactory.createPoint(new Coordinate(rs.getDouble("x1"), rs.getDouble("y1")));
            Point p2 = geometryFactory.createPoint(new Coordinate(rs.getDouble("x2"), rs.getDouble("y2")));
            /*if(rs2.getBoolean("contained")) { 
                boolean cond1 = line.contains(p1);
                if(cond1 != line.contains(p2)) // cut edge!
                    if(cond1) // p2 is the boundary node
                        boundaryNodes.add(new BoundaryNode(t, rs2.getInt("target"), p2));
                    else // p1 is the boundary node
                        boundaryNodes.add(new BoundaryNode(s, rs2.getInt("source"), p1));
                else { // inner edge
                    if(!cond1) 
                        graph.edge(s, t, rs2.getDouble("distance"), flags);
                }
            } else { 
                boolean cond1 = rect.contains(p1);
                boolean cond2 = rect.contains(p2);
                if(cond1 && cond2)// inner edge
                    graph.edge(s, t, rs2.getDouble("distance"), flags);
                else if(cond1 && !line.contains(p1)) // cut edge
                    boundaryNodes.add(new BoundaryNode(s, rs2.getInt("source"), p1));
                else if(cond2 && !line.contains(p2))
                    boundaryNodes.add(new BoundaryNode(t, rs2.getInt("target"), p2));
                // else ; // not a cut edge
            }*/
            
            // without the contained field (BUGFIX: postgis precision problem)
            boolean lcp1 = line.contains(p1);
            boolean lcp2 = line.contains(p2);
            boolean rcp1 = rect.contains(p1);
            boolean rcp2 = rect.contains(p2);
            if(rcp1 && rcp2) {
                if(lcp1 != lcp2) {
                    //cutEdges.add(rs.getInt("gid"));
                    if(lcp1) 
                        boundaryNodes.add(new BoundaryNode(t, rs.getInt("target"), p2)); // cut
                    else 
                        boundaryNodes.add(new BoundaryNode(s, rs.getInt("source"), p1)); // cut 
                } else {
                    if(! lcp1) {// == and not on line
                        innerEdges.add(rs.getInt("gid"));
                        EdgeIterator edge = graph.edge(s, t, rs.getDouble("distance"), flags); //inner 
                        if(withGeometry) {
                            String wkt = rs.getString("geometry");
                            if(wkt != null) {
                                Geometry geometry = reader.read(wkt);
                                edge.wayGeometry(GraphHelper.getPillars(geometry));
                            }
                        }
                    }
                }
            } else {
                if(rcp1 && !lcp1) {
                    boundaryNodes.add(new BoundaryNode(s, rs.getInt("source"), p1)); // cut
                    //cutEdges.add(rs.getInt("gid"));
                }
                if(rcp2 && !lcp2) {
                    boundaryNodes.add(new BoundaryNode(t, rs.getInt("target"), p2)); // cut 
                    //cutEdges.add(rs.getInt("gid"));
                }
            }
        }
        rs.close();
        return null;//new SubgraphTask.Cell(graph, boundaryNodes, vehicle, nodes);
    }
    /*
    private void computeCliqueParallel(SubgraphTask.Cell cell) throws Exception {
        BoundaryNode[] nodesArray = cell.boundaryNodes.toArray(new BoundaryNode[cell.boundaryNodes.size()]);
        final int POOL_SIZE = 5;
        
        class Task implements Runnable {

            double max_speed = 0.;
            boolean exterior;
            SubgraphTask.Cell cell;
            BoundaryNode[] nodesArray;
            int tid;

            public Task(boolean exterior, SubgraphTask.Cell cell, BoundaryNode[] nodesArray, int tid) {
                this.exterior = exterior;
                this.cell = cell;
                this.nodesArray = nodesArray;
                this.tid = tid;
            }
            
            @Override
            public void run() {
                final Graph graph = cell.graph;
                final RawEncoder vehicle = cell.encoder;
                try {
                    int counter = 0;
                    int N = nodesArray.length;
                    int count = N*(N-1)/2;
                    int step = count / POOL_SIZE;
                    int start = tid * step;
                    int end = tid == POOL_SIZE-1? count: (tid+1)*step;
                    for(int i = 0; i < N; i ++) {
                        for(int j = i+1; j < N; j ++, counter++) {
                            if(counter < start) continue;
                            if(counter >= end) return;
                            //if(i == j) continue;
                            RoutingAlgorithm algo = new AlgorithmPreparation(vehicle).graph(graph).createAlgo();
                            Path path = algo.calcPath(nodesArray[i].getNodeId(), nodesArray[j].getNodeId());
                            Metrics m = null, rm = null;
                            if(path.found()) {
                                m = new Metrics(path.distance(), new TimeCalculation(vehicle).calcTime(path));
                                double speed = m.getDistance()*3.6/m.getTime();
                                if(speed > max_speed) max_speed = speed;
                            }
                            RoutingAlgorithm ralgo = new AlgorithmPreparation(vehicle).graph(graph).createAlgo();
                            Path rpath = ralgo.calcPath(nodesArray[j].getNodeId(), nodesArray[i].getNodeId());
                            if(rpath.found()) {
                                rm = new Metrics(rpath.distance(), new TimeCalculation(vehicle).calcTime(rpath));
                                double speed = rm.getDistance()*3.6/rm.getTime();
                                if(speed > max_speed) max_speed = speed;
                            }

                            if(! exterior) { // store the clique edge
                                if(m != null && rm != null && m.compareTo(rm) == 0)
                                    storeOverlayEdge(nodesArray[i], nodesArray[j], m, true);
                                else {
                                    if(m != null)
                                        storeOverlayEdge(nodesArray[i], nodesArray[j], m, false);
                                    if(rm != null)
                                        storeOverlayEdge(nodesArray[j], nodesArray[i], rm, false);
                                }
                            }
                        }
                    }
                } catch(SQLException e) {
                    System.err.println(e);
                }
            }
            
        }
        
        ThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(POOL_SIZE);
        Task[] list = new Task[POOL_SIZE];
        for(int i = 0; i < POOL_SIZE; i ++) {
            list[i] = new Task(exterior, cell, nodesArray, i);
            pool.execute(list[i]);
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.DAYS);
        if(exterior) { // store the interior speed
            double max_speed = 0.;
            for(Task item: list)
                if(item.max_speed > max_speed)
                    max_speed = item.max_speed;
            
        }
    }*/
    
}
