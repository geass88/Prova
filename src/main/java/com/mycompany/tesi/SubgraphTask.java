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

import com.mycompany.tesi.utils.TileSystem;
import com.graphhopper.routing.DijkstraBidirection;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.PointList;
import com.mycompany.tesi.beans.BoundaryNode;
import com.mycompany.tesi.beans.Metrics;
import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.hooks.MyCarFlagEncoder;
import com.mycompany.tesi.hooks.FastestCalc;
import com.mycompany.tesi.hooks.TimeCalculation;
import com.mycompany.tesi.utils.QuadKeyManager;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;
import gnu.trove.list.TIntList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
public class SubgraphTask implements Runnable {

    private final WKTReader reader = new WKTReader();
    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final TileSystem tileSystem;
    private final int scale;
    private String dbName;
    
    public static final int maxSpeed = 130;
    public static final String sql1 = "SELECT ways.gid, ways.source, ways.target, ways.freeflow_speed, ways.length, ways.reverse_cost<>1000000 AS bothdir, ways.km*1000 AS distance, ways.x1, ways.y1, ways.x2, ways.y2, st_astext(ways.the_geom) AS geometry " + //st_contains(shape, the_geom) AS contained
            "FROM ways JOIN ways_tiles ON gid = ways_id JOIN tiles ON tiles_qkey = qkey WHERE qkey = ?";
    public static final String sql2 = "INSERT INTO \"overlay_%d\"(source, target, km, freeflow_speed, length, reverse_cost, x1, y1, x2, y2) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
    public static final String sql3 = "UPDATE tiles SET max_speed = ? WHERE qkey = ?";
    
    private Set<Integer> cutEdges = new TreeSet<>();
    private List<String> qkeys;
    
    public SubgraphTask(final TileSystem tileSystem, final String dbName, final int scale) {
        this.tileSystem = tileSystem;
        this.scale = scale;
        this.dbName = dbName;
    }

    public SubgraphTask(final TileSystem tileSystem, final String dbName, final int scale, List<String> qkeys) {
        this.tileSystem = tileSystem;
        this.scale = scale;
        this.dbName = dbName;
        this.qkeys = qkeys;
    }
    
    private PreparedStatement st1, st2, st3;
    
    @Override
    public void run() {
        Connection conn = Main.getConnection(this.dbName);
        try {
            st1 = conn.prepareStatement(sql1); 
            st2 = conn.prepareStatement(String.format(sql2, this.scale));
            st3 = conn.prepareStatement(sql3);
            System.out.println("Thread run ...");
            /*
            int count = 0;
            try (ResultSet rs1 = st1.executeQuery()) {
                while(rs1.next()) { // for each tiles
                    computeClique(rs1.getString(1));
                    //conn1.commit();
                    if(++ count % 500 == 0) {
                        System.exit(0);
                        st3.executeBatch();
                        st4.executeBatch();
                        count = 0;
                    }
                }
            }
            st3.executeBatch();
            st4.executeBatch();*/
            for(String q: qkeys)
                computeClique(q);
            st2.executeBatch();
            st3.executeBatch();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                //st1.close();
                st1.close();
                st2.close();
                st3.close();
                conn.close();
                //conn1.close();
            } catch (SQLException ex) {
                Logger.getLogger(SubgraphTask.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public Set<Integer> getCutEdges() {
        return cutEdges;
    }
    
    public void pathUnpacking(Path path) {
        Connection conn = Main.getConnection(this.dbName);
        try {
            st1 = conn.prepareStatement(sql1); 
            PointList points = path.calcPoints();
            System.out.println(QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(points.longitude(0), points.latitude(0), 15), 15));
            System.out.println(QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(points.longitude(1), points.latitude(1), 15), 15));
            System.out.println(QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(points.longitude(2), points.latitude(2), 15), 15));
            System.out.println(QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(points.longitude(3), points.latitude(3), 15), 15));
            Subgraph subgraph = buildSubgraph(QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(points.longitude(2), points.latitude(2), 15), 15));
            RoutingAlgorithm algo = new AlgorithmPreparation(subgraph.encoder).graph(subgraph.graph).createAlgo();
            TIntList list = path.calcNodes();
            Path path1 = algo.calcPath(subgraph.graph2subgraph.get(list.get(2)), subgraph.graph2subgraph.get(list.get(3)));
            if(path1.found()) {
                System.out.println(path1.calcNodes());
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                st1.close();
                conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(SubgraphTask.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public Subgraph getSubgraph(String qkey) throws Exception {
        Connection conn = Main.getConnection(this.dbName);
        try {
            st1 = conn.prepareStatement(sql1); 
            return buildSubgraph(qkey);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                st1.close();
                conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(SubgraphTask.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }
    
    public Subgraph buildSubgraph(String qkey) throws Exception {
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
        graph.combinedEncoder(MyCarFlagEncoder.COMBINED_ENCODER);
        MyCarFlagEncoder vehicle = new MyCarFlagEncoder(maxSpeed);
        Map<Integer, Integer> nodes = new HashMap<>(); // graph to subgraph nodes
        int count = 0;
        st1.clearParameters();
        st1.setString(1, qkey);
        ResultSet rs2 = st1.executeQuery();
        while(rs2.next()) { // for each way in a tile
            Integer s = nodes.get(rs2.getInt("source"));
            if(s == null) {
                nodes.put(rs2.getInt("source"), s = count); 
                graph.setNode(s, rs2.getDouble("y1"), rs2.getDouble("x1"));
                count ++;
            }
            Integer t = nodes.get(rs2.getInt("target"));
            if(t == null) {
                nodes.put(rs2.getInt("target"), t = count); 
                graph.setNode(t, rs2.getDouble("y2"), rs2.getDouble("x2"));
                count ++;
            }
            /* Map<String, Object> p = new HashMap<>();
            p.put("caroneway", !rs2.getBoolean("bothdir"));
            p.put("car", rs2.getInt("freeflow_speed"));
            int flags = new AcceptWay(true, true, true).toFlags(p); */
            int flags = vehicle.flags(rs2.getDouble("freeflow_speed"), rs2.getBoolean("bothdir"));
            Point p1 = geometryFactory.createPoint(new Coordinate(rs2.getDouble("x1"), rs2.getDouble("y1")));
            Point p2 = geometryFactory.createPoint(new Coordinate(rs2.getDouble("x2"), rs2.getDouble("y2")));
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
            
            // without contained
            boolean lcp1 = line.contains(p1);
            boolean lcp2 = line.contains(p2);
            boolean rcp1 = rect.contains(p1);
            boolean rcp2 = rect.contains(p2);
            if(rcp1 && rcp2) {
                if(lcp1 != lcp2) {
                    cutEdges.add(rs2.getInt("gid"));
                    if(lcp1) 
                        boundaryNodes.add(new BoundaryNode(t, rs2.getInt("target"), p2)); // cut
                    else 
                        boundaryNodes.add(new BoundaryNode(s, rs2.getInt("source"), p1)); // cut 
                } else {
                    if(! lcp1) // == and not on line
                        graph.edge(s, t, rs2.getDouble("distance"), flags);//inner
                }
            } else {
                if(rcp1 && !lcp1) {
                    boundaryNodes.add(new BoundaryNode(s, rs2.getInt("source"), p1)); // cut
                    cutEdges.add(rs2.getInt("gid"));
                }
                if(rcp2 && !lcp2) {
                    boundaryNodes.add(new BoundaryNode(t, rs2.getInt("target"), p2)); // cut 
                    cutEdges.add(rs2.getInt("gid"));
                }
            }
            //
            //System.out.println(graph.nodes());
        }
        rs2.close();
        return new Subgraph(graph, boundaryNodes, vehicle, nodes);
    }
    
    private void computeClique(String qkey) throws Exception {
        Subgraph subgraph = buildSubgraph(qkey);
        Graph graph = subgraph.graph;
        MyCarFlagEncoder vehicle = subgraph.encoder;
        
        double max_speed = 0.;
        BoundaryNode[] nodesArray = subgraph.boundaryNodes.toArray(new BoundaryNode[subgraph.boundaryNodes.size()]);
        //Metrics clique[][] = new Metrics[boundaryNodes.size()][boundaryNodes.size()];
        for(int i = 0; i < nodesArray.length; i ++) {
            for(int j = i+1; j < nodesArray.length; j ++) {
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
                if(rpath.found())
                    rm = new Metrics(rpath.distance(), new TimeCalculation(vehicle).calcTime(rpath));
                
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
        st3.clearParameters();
        st3.setDouble(1, max_speed);
        st3.setString(2, qkey);
        //st3.executeUpdate();
        st3.addBatch();
    }
    
    private void storeOverlayEdge(BoundaryNode source, BoundaryNode target, Metrics metrics, boolean bothDir) throws SQLException {
        st2.clearParameters();
        st2.setInt(1, source.getRoadNodeId());
        st2.setInt(2, target.getRoadNodeId());
        st2.setDouble(3, metrics.getDistance()/1000.);
        st2.setDouble(4, metrics.getDistance()*3.6/metrics.getTime());
        st2.setDouble(5, metrics.getTime());
        st2.setDouble(6, (bothDir? metrics.getTime(): 1000000));
        st2.setDouble(7, source.getPoint().getX());
        st2.setDouble(8, source.getPoint().getY());
        st2.setDouble(9, target.getPoint().getX());
        st2.setDouble(10, target.getPoint().getY());
        st2.addBatch();
        //st2.executeUpdate();
    }
    
    public class Subgraph {
        public final Graph graph;
        public final Set<BoundaryNode> boundaryNodes;
        public final MyCarFlagEncoder encoder;
        public final Map<Integer, Integer> graph2subgraph;

        public Subgraph(Graph graph, Set<BoundaryNode> boundaryNodes, MyCarFlagEncoder encoder, Map<Integer, Integer> graph2subgraph) {
            this.graph = graph;
            this.boundaryNodes = boundaryNodes;
            this.encoder = encoder;
            this.graph2subgraph = graph2subgraph;
        }
    }
}
class AlgorithmPreparation extends NoOpAlgorithmPreparation {

    private final MyCarFlagEncoder vehicle;
    
    public AlgorithmPreparation(MyCarFlagEncoder vehicle) {
        this.vehicle = vehicle;
    }
    
    @Override
    public RoutingAlgorithm createAlgo() {
        return new DijkstraBidirection(_graph, vehicle).type(new FastestCalc(vehicle));
    }
    
}

class TasksHelper implements Runnable {
    public static final String sql1 = "SELECT DISTINCT tiles_qkey FROM ways_tiles WHERE length(tiles_qkey)=? ORDER BY tiles_qkey";
    public static final String sql2 = "SELECT my_add_cut_edges(?, ?);";
    private final TileSystem tileSystem;
    private final int scale;
    private String dbName;
    private static final ThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(8);

    public TasksHelper(final TileSystem tileSystem, final String dbName, final int scale) {
        this.tileSystem = tileSystem;
        this.scale = scale;
        this.dbName = dbName;
    }

    @Override
    public void run() {
        Connection conn = Main.getConnection(this.dbName);
        try {
            PreparedStatement st1;
            st1 = conn.prepareStatement(sql1);
            st1.setInt(1, scale);
            System.out.println(String.format("Computing cliques for %s and scale=%d", dbName, scale));
            List<String> list = new LinkedList<>();
            try (ResultSet rs1 = st1.executeQuery()) {
                while(rs1.next()) { // for each tiles
                    list.add(rs1.getString(1));
                }
            }
            st1.close();

            int amount = scale>6? 1<<(scale-7): 1;
            int start;
            List<SubgraphTask> tasks = new LinkedList<>();
            for(start = 0; start+amount < list.size(); start += amount) {
                SubgraphTask task = new SubgraphTask(tileSystem, dbName, scale, list.subList(start, start+amount));
                tasks.add(task);
                pool.execute(task);
            }
            SubgraphTask task = new SubgraphTask(tileSystem, dbName, scale, list.subList(start, list.size()));
            pool.execute(task);
            tasks.add(task);
            pool.shutdown();
            pool.awaitTermination(1l, TimeUnit.DAYS);
            System.out.println(String.format("Adding cut-edges for %s and scale=%d", dbName, scale));
            Set<Integer> cutEdges = new TreeSet<>();
            for(SubgraphTask t: tasks) {
                cutEdges.addAll(t.getCutEdges());
            }
            try (PreparedStatement st2 = conn.prepareStatement(sql2)) {
                st2.setInt(1, scale);
                st2.setArray(2, conn.createArrayOf("integer", cutEdges.toArray()));
                st2.executeQuery();
            }
        } catch (Exception ex) {
            Logger.getLogger(SubgraphTask.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(TasksHelper.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}