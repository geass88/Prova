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
import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.CombinedEncoder;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.VehicleEncoder;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import static com.mycompany.tesi.Main.getPillars;
import com.mycompany.tesi.hooks.CarFlagEncoder;
import com.mycompany.tesi.hooks.FastestCalc;
import com.mycompany.tesi.hooks.TimeCalculation;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
    
    private static final int maxSpeed = 130;
    private static final String sql1 = "SELECT DISTINCT tiles_qkey FROM ways_tiles WHERE length(tiles_qkey)=? ORDER BY tiles_qkey";
    private static final String sql2 = "SELECT ways.gid, ways.source, ways.target, ways.freeflow_speed, ways.length, ways.reverse_cost<>1000000 AS bothdir, ways.km*1000 AS distance, ways.x1, ways.y1, ways.x2, ways.y2, st_astext(ways.the_geom) AS geometry, st_contains(shape, the_geom) AS contained " +
            "FROM ways JOIN ways_tiles ON gid = ways_id JOIN tiles ON tiles_qkey = qkey WHERE qkey = ?";
    private static final String sql3 = "INSERT INTO \"overlay_17\"(source, target, km, freeflow_speed, length, reverse_cost, x1, y1, x2, y2) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
    private static final String sql4 = "SELECT my_add_cut_edges(?);";
    
    public SubgraphTask(final TileSystem tileSystem, final String dbName, final int scale) {
        this.tileSystem = tileSystem;
        this.scale = scale;
        this.dbName = dbName;
    }
    
    private PreparedStatement st1, st2, st3;
    
    @Override
    public void run() {
        Connection conn = Main.getConnection(this.dbName);
        Connection conn1 = Main.getConnection(this.dbName);
        try {
            conn1.setAutoCommit(false);
            st1 = conn.prepareStatement(sql1); 
            st2 = conn.prepareStatement(sql2); 
            st3 = conn1.prepareStatement(sql3);
            st1.setInt(1, scale);
            System.out.println("Computing cliques ...");
            try (ResultSet rs1 = st1.executeQuery()) {
                while(rs1.next()) { // for each tiles
                    helper(rs1.getString(1));
                    conn1.commit();
                }
            }
            System.out.println("Adding cut-edges ...");
            try (PreparedStatement st = conn.prepareStatement(sql4)) {
                st.setInt(1, scale);
                st.executeQuery();
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                st1.close();
                st2.close();
                st3.close();
                conn.close();
                conn1.close();
            } catch (SQLException ex) {
                Logger.getLogger(SubgraphTask.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
        
    public void helper(String qkey) throws Exception {
        Polygon rect = tileSystem.getTile(qkey).getPolygon();
        Set<BoundaryNode> boundaryNodes = new TreeSet<>();
        GraphStorage graph = new GraphBuilder().create();
        graph.combinedEncoder(COMBINED_ENCODER);
        CarFlagEncoder vehicle = new CarFlagEncoder(maxSpeed);
        Map<Integer, Integer> nodes = new HashMap<>();// graph to subgraph nodes
        int count = 0;
        st2.clearParameters();
        st2.setString(1, qkey);
        ResultSet rs2 = st2.executeQuery();
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
            /*
            Map<String, Object> p = new HashMap<>();
            p.put("caroneway", !rs2.getBoolean("bothdir"));
            p.put("car", rs2.getInt("freeflow_speed"));
            int flags = new AcceptWay(true, true, true).toFlags(p);
            */
            int flags = vehicle.flags(rs2.getDouble("freeflow_speed"), rs2.getBoolean("bothdir"));
            if(rs2.getBoolean("contained")) { // inner edge
                graph.edge(s, t, rs2.getDouble("distance"), flags);
            } else { // possible cut edge
                Point p1 = geometryFactory.createPoint(new Coordinate(rs2.getDouble("x1"), rs2.getDouble("y1")));
                Point p2 = geometryFactory.createPoint(new Coordinate(rs2.getDouble("x2"), rs2.getDouble("y2")));
                if(rect.contains(p1))
                    boundaryNodes.add(new BoundaryNode(s, rs2.getInt("source"), p1));
                else if(rect.contains(p2))
                    boundaryNodes.add(new BoundaryNode(t, rs2.getInt("target"), p2));
                // else ; // not a cut edge
            }
            //System.out.println(graph.nodes());
        }
        rs2.close();
        /*
        System.out.println(qkey + " " + graph.nodes() + " " + boundaryNodes.size() + " " + nodes.size());*/
        //double min_time = Double.MAX_VALUE;
        BoundaryNode[] nodesArray = boundaryNodes.toArray(new BoundaryNode[boundaryNodes.size()]);
        //Metrics clique[][] = new Metrics[boundaryNodes.size()][boundaryNodes.size()];
        for(int i = 0; i < nodesArray.length; i ++) {
            for(int j = i+1; j < nodesArray.length; j ++) {
                //if(i == j) continue;
                RoutingAlgorithm algo = new AlgorithmPreparation(vehicle).graph(graph).createAlgo();
                Path path = algo.calcPath(nodesArray[i].getNodeId(), nodesArray[j].getNodeId());
                Metrics m = null, rm = null;
                if(path.found())
                   m = new Metrics(path.distance(), new TimeCalculation(vehicle).calcTime(path));
                RoutingAlgorithm ralgo = new AlgorithmPreparation(vehicle).graph(graph).createAlgo();
                Path rpath = ralgo.calcPath(nodesArray[j].getNodeId(), nodesArray[i].getNodeId());
                if(rpath.found())
                    rm = new Metrics(rpath.distance(), new TimeCalculation(vehicle).calcTime(rpath));
                
                if(m != null && rm != null && m.compareTo(rm) == 0){
                    save(nodesArray[i], nodesArray[j], m, true);
                }
                else {
                    if(m != null)
                        save(nodesArray[i], nodesArray[j], m, false);
                    if(rm != null)
                        save(nodesArray[j], nodesArray[i], rm, false);
                }
            }
        }
        //
    }
    private void save(BoundaryNode source, BoundaryNode target, Metrics metrics, boolean bothDir) throws SQLException {
        st3.clearParameters();
        st3.setInt(1, source.getRoadNodeId());
        st3.setInt(2, target.getRoadNodeId());
        st3.setDouble(3, metrics.getDistance()/1000.);
        st3.setDouble(4, metrics.getDistance()*3.6/metrics.getTime());
        st3.setDouble(5, metrics.getTime());
        st3.setDouble(6, (bothDir? metrics.getTime(): 1000000));
        st3.setDouble(7, source.getPoint().getX());
        st3.setDouble(8, source.getPoint().getY());
        st3.setDouble(9, target.getPoint().getX());
        st3.setDouble(10, target.getPoint().getY());
        st3.executeUpdate();
    }
    
    public final static CombinedEncoder COMBINED_ENCODER = new CombinedEncoder() {
        @Override
        public int swapDirection(int flags) {
            if((flags & 3) == 3) 
                return flags;
            return flags ^ 3;
        }
    };
}


class BoundaryNode implements Comparable<BoundaryNode> {
    
    private int nodeId;
    private int roadNodeId;
    private Point point;

    public BoundaryNode(int nodeId, int roadNodeId, Point point) {
        this.nodeId = nodeId;
        this.point = point;
        this.roadNodeId = roadNodeId;
    }

    public int getRoadNodeId() {
        return roadNodeId;
    }

    public void setRoadNodeId(int roadNodeId) {
        this.roadNodeId = roadNodeId;
    }
    
    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }
    
    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }
    
    @Override
    public int compareTo(BoundaryNode o) {
        return (this.nodeId == o.nodeId? 0: 1);
    }
    
}

class AlgorithmPreparation extends NoOpAlgorithmPreparation {

    private CarFlagEncoder vehicle;
    
    public AlgorithmPreparation(CarFlagEncoder vehicle) {
        this.vehicle = vehicle;
    }
    
    @Override
    public RoutingAlgorithm createAlgo() {
        return new DijkstraBidirection(_graph, vehicle).type(new FastestCalc(vehicle));
    }
    
}

class Metrics implements Comparable<Metrics> {
    
    private double time;
    private double distance;

    public Metrics() {}
    
    public Metrics(double distance, double time) {
        this.time = time;
        this.distance = distance;
    }

    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    @Override
    public int compareTo(Metrics o) {
        return ((o != null && o.getTime() == time && o.getDistance() == distance)? 0: 1);
    }
}