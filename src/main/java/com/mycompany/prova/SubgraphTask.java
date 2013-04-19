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
package com.mycompany.prova;

import com.mycompany.prova.utils.TileSystem;
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
import static com.mycompany.prova.Main.getPillars;
import com.mycompany.prova.hooks.CarFlagEncoder;
import com.mycompany.prova.hooks.FastestCalc;
import com.mycompany.prova.hooks.TimeCalculation;
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
    private static final String sql3 = "INSERT INTO \"overlay\"(source, target, km, freeflow_speed, length, x1, y1, x2, y2) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?);";
    
    public SubgraphTask(final TileSystem tileSystem, final String dbName, final int scale) {
        this.tileSystem = tileSystem;
        this.scale = scale;
        this.dbName = dbName;
    }
    
    private PreparedStatement st1, st2, st3;
    
    @Override
    public void run() {
        Connection conn = Main.getConnection(this.dbName);
        try {
            st1 = conn.prepareStatement(sql1); 
            st2 = conn.prepareStatement(sql2); 
            st3 = conn.prepareStatement(sql3);
            st1.setInt(1, scale);
            try (ResultSet rs1 = st1.executeQuery()) {
                while(rs1.next()) { // for each tiles
                    helper(rs1.getString(1));
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                st1.close();
                st2.close();
                st3.close();
                conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(SubgraphTask.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public void helper(String qkey) throws Exception {
        Polygon rect = tileSystem.getTile(qkey).getPolygon();
        LineString ring = rect.getExteriorRing();
        Set<BoundaryNode> boundaryNodes = new TreeSet<>();
        GraphStorage graph = new GraphBuilder().create();
        graph.combinedEncoder(new CombinedEncoder() {
            @Override
            public int swapDirection(int flags) {
                if((flags & 3) == 3) 
                    return flags;
                return flags ^ 3;
            }
        });
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
            } else { // cut edge
                Point p1 = geometryFactory.createPoint(new Coordinate(rs2.getDouble("x1"), rs2.getDouble("y1")));
                Point p2 = geometryFactory.createPoint(new Coordinate(rs2.getDouble("x2"), rs2.getDouble("y2")));
                if(rect.contains(p1))
                    boundaryNodes.add(new BoundaryNode(s, p1));
                else 
                    boundaryNodes.add(new BoundaryNode(t, p2));
                /*
                Geometry intersection = ring.intersection(geometry);
                Map<String, Integer> index = new HashMap<>();
                index.put(rs2.getDouble("y1") + " " + rs2.getDouble("x1"), s);
                index.put(rs2.getDouble("y2") + " " + rs2.getDouble("x2"), t);
                for(Coordinate c: intersection.getCoordinates()) {
                    String key = c.getOrdinate(1) + " " + c.getOrdinate(0);
                    Integer val = index.get(key);
                    if(val == null) {
                        boundaryNodes.add(new BoundaryNode(count, rs2.getInt("gid"), c));
                        index.put(key, count);
                        graph.setNode(count, c.getOrdinate(1), c.getOrdinate(0));
                        count ++;
                    } else // the intersection is in s or t
                        boundaryNodes.add(new BoundaryNode(val, rs2.getInt("gid"), c));
                }
                intersection = rect.intersection(geometry);
                double factor = rs2.getDouble("distance") / geometry.getLength();
                for(int i = 0; i < intersection.getNumGeometries(); i ++) {
                    Geometry g = intersection.getGeometryN(i);
                    if(g.getNumPoints() > 1) {
                        Coordinate dot1 = g.getCoordinates()[0];
                        Integer value1 = index.get(dot1.getOrdinate(1) + " " + dot1.getOrdinate(0));
                        Coordinate dot2 = g.getCoordinates()[g.getNumPoints()-1];
                        Integer value2 = index.get(dot2.getOrdinate(1) + " " + dot2.getOrdinate(0));
                        if(value1 == null || value2 == null)
                            System.err.println("PROBLEM in: " + dot1 + " " +dot2);
                        graph.edge(value1, value2, factor*g.getLength(), flags).wayGeometry(getPillars(g));
                    } // else {} // nothing to do
                }*/
            }
            //System.out.println(graph.nodes());
        }
        rs2.close();
        /*if("12021023231120".equals(qkey)) {
                    System.out.println("doit");
                    EdgeIterator o = graph.getEdges(17);
                    while(o.next())
                        if(o.adjNode() == 43)
                            System.out.println("flags : " +(o.flags()^3)+" "+o.flags());
                }
        System.out.println(qkey + " " + graph.nodes() + " " + boundaryNodes.size() + " " + nodes.size());*/
        //double min_time = Double.MAX_VALUE;
        /*
        for(BoundaryNode i: boundaryNodes) {
            for(BoundaryNode j: boundaryNodes) {
                int s = i.getNodeId();
                int t = j.getNodeId();
                if(s == t) continue;
                RoutingAlgorithm algo = new AlgorithmPreparation(vehicle).graph(graph).createAlgo();
                Path path = null;
                path = algo.calcPath(s, t);
                /*}catch(Exception e){
                    AllEdgesIterator it = graph.getAllEdges();
                    while(it.next())
                        System.out.println(it.baseNode()+" " +it.adjNode()+" " +(it.flags()>>>3)+ " " + it.flags()+ " "+(it.flags()&3));
                    
                    System.exit(0);
                }*//*
                if(path.found()) { // the path exists
                   // System.out.println("found path between " + s + " and "+ t);
                    double distance = path.distance();
                    double time = new TimeCalculation(path, vehicle).calcTime();
                    
                    String key1 = i.getWayId() + " " + graph.getLatitude(s) + " "+ graph.getLongitude(s);
                    String key2 = j.getWayId() + " " + graph.getLatitude(t) + " "+ graph.getLongitude(t);
                    Integer s_id = overlayIndex.get(key1);
                    Integer t_id = overlayIndex.get(key1);
                    if(s_id == null) {
                        overlayIndex.put(key1, s_id=overlayId);
                        overlayId ++;
                    }
                    if(t_id == null) {
                        overlayIndex.put(key2, t_id=overlayId);
                        overlayId ++;
                    }
                    /*
                    st3.clearParameters();
                    st3.setInt(1, s_id);
                    st3.setInt(2, t_id);
                    st3.setDouble(3, distance/1000.);
                    st3.setDouble(4, distance*3.6/time);
                    st3.setDouble(5, time);
                    st3.setDouble(6, i.getCoordinate().getOrdinate(0));
                    st3.setDouble(7, i.getCoordinate().getOrdinate(1));
                    st3.setDouble(8, j.getCoordinate().getOrdinate(0));
                    st3.setDouble(9, j.getCoordinate().getOrdinate(1));
                    st3.executeUpdate();*/
                    /*if(time < min_time)
                        min_time = time;*//*
                }
            }
            //System.out.println("["+graph.getLongitude(i)+", "+graph.getLatitude(i)+"],");
        }*/
        
        //
    }
    
}


class BoundaryNode implements Comparable<BoundaryNode> {
    
    private int nodeId;
    private Point point;

    public BoundaryNode(int nodeId, Point point) {
        this.nodeId = nodeId;
        this.point = point;
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
        return new DijkstraSimple(_graph, vehicle).type(new FastestCalc(vehicle));
    }
    
}