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
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AcceptWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.FastestCalc;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.VehicleEncoder;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import static com.mycompany.prova.Main.getPillars;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
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
    private final TileSystem tileSystem;
    private final int scale;
    private String dbName;
    
    private static String sql1 = "SELECT DISTINCT tiles_qkey FROM ways_tiles WHERE length(tiles_qkey)=? ORDER BY tiles_qkey";
    private static String sql2 = "SELECT ways.source, ways.target, ways.freeflow_speed, ways.length, ways.reverse_cost=1000000 AS oneway, ways.km*1000 AS distance, ways.x1, ways.y1, ways.x2, ways.y2, st_astext(ways.the_geom) AS geometry, st_contains(shape, the_geom) AS contained " +
            "FROM ways JOIN ways_tiles ON gid = ways_id JOIN tiles ON tiles_qkey = qkey WHERE qkey = ?";
        
    public SubgraphTask(final TileSystem tileSystem, final String dbName, final int scale) {
        this.tileSystem = tileSystem;
        this.scale = scale;
        this.dbName = dbName;
    }
    
    @Override
    public void run() {
        Connection conn = Main.getConnection(this.dbName);
        try (PreparedStatement st1 = conn.prepareStatement(sql1);
            PreparedStatement st2 = conn.prepareStatement(sql2)) {
            st1.setInt(1, scale);
            ResultSet rs1 = st1.executeQuery();
            while(rs1.next()) { // for each tiles
                String qkey = rs1.getString(1);
                Polygon rect = tileSystem.getTile(qkey).getPolygon();
                LineString ring = rect.getExteriorRing();
                Set<Integer> boundaryNodes = new TreeSet<>();
                GraphStorage graph = new GraphBuilder().create();
                Map<Integer, Integer> nodes = new HashMap<>();
                int count = 0;
                //System.out.println(g.getExteriorRing().toText());
                
                st2.setString(1, qkey);
                ResultSet rs2 = st2.executeQuery();
                while(rs2.next()) { // for each way in a tile
                    Geometry geometry = reader.read(rs2.getString("geometry"));
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
                    Map<String, Object> p = new HashMap<>();
                    p.put("caroneway", rs2.getBoolean("oneway"));
                    p.put("car", rs2.getInt("freeflow_speed"));
                    int flags = new AcceptWay(true, true, true).toFlags(p);
                    
                    if(rs2.getBoolean("contained")) {                        
                        EdgeIterator edge = graph.edge(s, t, rs2.getDouble("distance"), flags);
                        PointList pillarNodes = getPillars(geometry);
                        if(pillarNodes != null) 
                            edge.wayGeometry(pillarNodes);
                    } else {
                        Geometry intersection = ring.intersection(geometry);
                        Map<String, Integer> index = new HashMap<>();
                        index.put(rs2.getDouble("y1") + " " + rs2.getDouble("x1"), s);
                        index.put(rs2.getDouble("y2") + " " + rs2.getDouble("x2"), t);
                        for(Coordinate c: intersection.getCoordinates()) {
                            String key = c.getOrdinate(1) + " " + c.getOrdinate(0);
                            Integer val = index.get(key);
                            if(val == null) {
                                boundaryNodes.add(count);
                                index.put(key, count);
                                graph.setNode(count, c.getOrdinate(1), c.getOrdinate(0));
                                count ++;
                            } else // the intersection is in s or t
                                boundaryNodes.add(val);
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
                        }
                    }
                    //System.out.println(graph.nodes());
                }
                rs2.close();
                st2.clearParameters();
                
                double min_time = Double.MAX_VALUE;
                for(Integer i: boundaryNodes) {
                    for(Integer j: boundaryNodes) {
                        if(i == j) continue;
                        RoutingAlgorithm algo = new NoOpAlgorithmPreparation() {
                            @Override public RoutingAlgorithm createAlgo() {
                                VehicleEncoder vehicle = new CarFlagEncoder();
                                return new DijkstraBidirection(_graph, vehicle).type(new FastestCalc(vehicle));
                            }
                        }.graph(graph).createAlgo();
                        Path path = algo.calcPath(i,j);
                        if(path.found()) { // the path exists
                            double distance = path.distance();
                            double time = path.time();
                            if(time < min_time)
                                min_time = time;
                        }
                    }
                    //System.out.println("["+graph.getLongitude(i)+", "+graph.getLatitude(i)+"],");
                }
            }
            rs1.close();
        } catch(Exception e) {
            System.err.println(e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(SubgraphTask.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
}
