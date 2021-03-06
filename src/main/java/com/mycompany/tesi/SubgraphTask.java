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
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.Path.EdgeVisitor;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import com.mycompany.tesi.beans.BoundaryNode;
import com.mycompany.tesi.beans.Metrics;
import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.hooks.MyCarFlagEncoder;
import com.mycompany.tesi.hooks.FastestCalc;
import com.mycompany.tesi.hooks.RawEncoder;
import com.mycompany.tesi.hooks.TimeCalculation;
import com.mycompany.tesi.utils.GraphHelper;
import com.mycompany.tesi.utils.QuadKeyManager;
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
    private final static Logger logger = Logger.getLogger(SubgraphTask.class.getName());
    private final TileSystem tileSystem;
    private final int scale;
    private String dbName;
    
    //public static final int MAX_SPEED = 130;
    public static final String sql1 = "SELECT ways.gid, ways.source, ways.target, ways.freeflow_speed, ways.length, ways.reverse_cost<>1000000 AS bothdir, ways.km*1000 AS distance, ways.x1, ways.y1, ways.x2, ways.y2, st_astext(ways.the_geom) AS geometry " + //st_contains(shape, the_geom) AS contained
            "FROM ways JOIN ways_tiles ON gid = ways_id JOIN tiles ON tiles_qkey = qkey WHERE qkey = ? ORDER BY gid";
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
        this(tileSystem, dbName, scale);
        this.qkeys = qkeys;
    }
    
    private PreparedStatement st1, st2, st3;
    private boolean overlayGen = false;

    public boolean isOverlayGen() {
        return overlayGen;
    }

    public void setOverlayGen(boolean overlayGen) {
        this.overlayGen = overlayGen;
    }
    
    @Override
    public void run() {
        if(overlayGen)
            run2();
        else 
            run1();
    }
    
    // compute only the cell max speed
    public void run1() {
        Connection conn = Main.getConnection(this.dbName);
        try {
            st1 = conn.prepareStatement(sql1); 
            st2 = conn.prepareStatement(String.format(sql2, this.scale));
            st3 = conn.prepareStatement(sql3);
            logger.log(Level.INFO, "Thread run ...");
            
            for(String qkey: qkeys) {
            //    computeCliqueParallel(qkey, false); // compute and store the clique // UNLOCK ME FOR OVERLAY GENERATION
                computeCliqueParallel(qkey, true); // compute and store the cell max speed using the porcupine
            }
            //st2.executeBatch(); // store the clique edges // UNLOCK ME FOR OVERLAY GENERATION
            // unlock also cut-edges
            
            st3.executeBatch(); // update the cell max speed
        } catch(Exception e) {
            logger.log(Level.SEVERE, null, e);
        } finally {
            try {
                st1.close();
                st2.close();
                st3.close();
                conn.close();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }
        
    // compute only the overlay graph
    public void run2() {
        Connection conn = Main.getConnection(this.dbName);
        try {
            st1 = conn.prepareStatement(sql1); 
            st2 = conn.prepareStatement(String.format(sql2, this.scale));
            st3 = conn.prepareStatement(sql3);
            logger.log(Level.INFO, "Thread run ...");
            
            for(String qkey: qkeys) {
                computeCliqueParallel(qkey, false); // compute and store the clique // UNLOCK ME FOR OVERLAY GENERATION
                // computeCliqueParallel(qkey, true); // compute and store the cell max speed using the porcupine
            }
            st2.executeBatch(); // store the clique edges // UNLOCK ME FOR OVERLAY GENERATION
            // unlock also cut-edges
            
            // st3.executeBatch(); // update the cell max speed
        } catch(Exception e) {
            logger.log(Level.SEVERE, null, e);
        } finally {
            try {
                st1.close();
                st2.close();
                st3.close();
                conn.close();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    public Set<Integer> getCutEdges() {
        return cutEdges;
    }
    
    public PointList pathUnpacking(final Graph graph, final Path path, final String sqkey, final String eqkey) {
        Connection conn = Main.getConnection(this.dbName);
        try {
            st1 = conn.prepareStatement(sql1); 
            final PointList roadPoints = new PointList();
            //final TIntList nodes = new TIntArrayList();
            
            path.forEveryEdge(new EdgeVisitor() {
                @Override
                public void next(EdgeIterator iter) {
                    double baseLat = graph.getLatitude(iter.baseNode());
                    double baseLon = graph.getLongitude(iter.baseNode());
                    double adjLat = graph.getLatitude(iter.adjNode());
                    double adjLon = graph.getLongitude(iter.adjNode());

                    try {
                        String baseQkey = QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(baseLon, baseLat, scale), scale);
                        String adjQkey = QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(adjLon, adjLat, scale), scale);
                        if(baseQkey.equals(adjQkey)) {
                            if(baseQkey.equals(sqkey) || baseQkey.equals(eqkey)) {
                                roadPoints.add(adjLat, adjLon);
                                PointList pillarNodes = iter.wayGeometry();
                                pillarNodes.reverse();
                                for(int i = 0; i < pillarNodes.size(); i ++)
                                    roadPoints.add(pillarNodes.latitude(i), pillarNodes.longitude(i));
                                //nodes.add(iter.adjNode());
                            } else {
                                Cell cell = buildSubgraph(baseQkey, true);
                                RoutingAlgorithm algo = new AlgorithmPreparation(cell.encoder).graph(cell.graph).createAlgo();
                                Path path1 = algo.calcPath(cell.graph2subgraph.get(iter.adjNode()), cell.graph2subgraph.get(iter.baseNode()));
                                if(path1.found()) {
                                    PointList list = path1.calcPoints();
                                    for(int j = 0; j < list.size()-1; j ++)
                                        roadPoints.add(list.latitude(j), list.longitude(j));
                                    /*
                                    Map<Integer, Integer> inverse = new HashMap<>();
                                    for(Integer key: cell.graph2subgraph.keySet()) {
                                        int value = cell.graph2subgraph.get(key);
                                        inverse.put(value, key);
                                    }
                                    TIntList nodes1 = path1.calcNodes();
                                    for(int h = 0; h < nodes1.size()-1; h++)
                                        nodes.add(inverse.get(nodes1.get(h)));*/
                                }
                            }
                        } else {
                            roadPoints.add(adjLat, adjLon);
                            PointList pillarNodes = iter.wayGeometry();
                            pillarNodes.reverse();
                            for(int i = 0; i < pillarNodes.size(); i ++)
                                roadPoints.add(pillarNodes.latitude(i), pillarNodes.longitude(i));
                            //nodes.add(iter.adjNode());
                        }
                    } catch (Exception ex) {
                        Logger.getLogger(SubgraphTask.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
            PointList points = path.calcPoints();
            roadPoints.add(points.latitude(points.size()-1), points.longitude(points.size()-1));
            // nodes.add(<last_node>);
            //System.out.println(nodes);
            /*
            for(int i = 0; i < points.size(); i ++) {
                String qkey = QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(points.longitude(i), points.latitude(i), scale), scale);
                if(qkey.equals(sqkey) || qkey.equals(eqkey)) {
                    roadPoints.add(points.latitude(i), points.longitude(i));
                    System.out.print(nodes.get(i)+", ");
                } 
                else if(qkey.equals(prev)) {
                    Cell cell = buildSubgraph(qkey, true);
                    RoutingAlgorithm algo = new AlgorithmPreparation(cell.encoder).graph(cell.graph).createAlgo();
                    Path path1 = algo.calcPath(cell.graph2subgraph.get(nodes.get(i-1)), cell.graph2subgraph.get(nodes.get(i)));
                    if(path1.found()) {
                        PointList list = path1.calcPoints();
                        for(int j = 1; j < list.size(); j ++)
                            roadPoints.add(list.latitude(j), list.longitude(j));
                        Map<Integer, Integer> inverse = new HashMap<>();
                        for(Integer key: cell.graph2subgraph.keySet()) {
                            int value = cell.graph2subgraph.get(key);
                            inverse.put(value, key);
                        }
                        TIntList nodes1 = path1.calcNodes();
                        for(int h = 1; h<nodes1.size(); h++)
                            System.out.print(inverse.get(nodes1.get(h))+", ");
                    }
                } else {
                    prev = qkey;
                    System.out.print(nodes.get(i)+", ");
                    roadPoints.add(points.latitude(i), points.longitude(i));
                }
            }*/
            return roadPoints;
        } catch(Exception e) {
            logger.log(Level.SEVERE, null, e);
        } finally {
            try {
                st1.close();
                conn.close();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }
    
    /**
     * 
     * @param qkey - identify uniquely a cell
     * @param exterior - specify to use the cell porcupine instead of the interior cell graph
     * @return 
     */
    public Cell getSubgraph(String qkey, boolean exterior) {
        Connection conn = Main.getConnection(this.dbName);
        try {
            st1 = conn.prepareStatement(sql1); 
            return exterior? buildExteriorSubgraph(qkey): buildSubgraph(qkey, true);
        } catch(Exception e) {
            logger.log(Level.SEVERE, null, e);
        } finally {
            try {
                st1.close();
                conn.close();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }
    
    public Map<String, Cell> getSubgraphs(boolean exterior) {
        if(qkeys == null || qkeys.isEmpty()) return null;
        Map<String, Cell> map = new HashMap<>();
        Connection conn = Main.getConnection(this.dbName);
        try {
            st1 = conn.prepareStatement(sql1); 
            for(String qkey: qkeys) 
                map.put(qkey, exterior? buildExteriorSubgraph(qkey): buildSubgraph(qkey, true));
        } catch(Exception e) {
            logger.log(Level.SEVERE, null, e);
        } finally {
            try {
                st1.close();
                conn.close();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        return map;
    }
    
    /**
     * Build the exterior cell subgraph for the given quadkey (porcupine = interior subgraph + cut-edges)
     * @param qkey
     * @return
     * @throws Exception 
     */
    private Cell buildExteriorSubgraph(String qkey/*, boolean withGeometry*/) throws Exception {
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
        RawEncoder vehicle = new MyCarFlagEncoder(Main.getMaxSpeed(dbName));
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
            
            int flags = vehicle.flags(rs.getDouble("freeflow_speed"), rs.getBoolean("bothdir"));
            Point p1 = geometryFactory.createPoint(new Coordinate(rs.getDouble("x1"), rs.getDouble("y1")));
            Point p2 = geometryFactory.createPoint(new Coordinate(rs.getDouble("x2"), rs.getDouble("y2")));
            
            // without the contained field (BUGFIX: postgis precision problem)
            boolean lcp1 = line.contains(p1);
            boolean lcp2 = line.contains(p2);
            boolean rcp1 = rect.contains(p1);
            boolean rcp2 = rect.contains(p2);
            if(rcp1 && rcp2) {
                if(lcp1 != lcp2) {
                    // cutEdges.add(rs.getInt("gid"));
                    if(lcp1) {
                        boundaryNodes.add(new BoundaryNode(s, rs.getInt("source"), p1)); // cut
                        /*EdgeIterator edge = */ graph.edge(s, t, rs.getDouble("distance"), flags); 
                    } else {
                        boundaryNodes.add(new BoundaryNode(t, rs.getInt("target"), p2)); // cut 
                        /*EdgeIterator edge = */ graph.edge(s, t, rs.getDouble("distance"), flags); 
                    }
                } else {
                    if(! lcp1) {// == and not on line
                        /*EdgeIterator edge = */ graph.edge(s, t, rs.getDouble("distance"), flags); //inner 
                        /*if(withGeometry) {
                            String wkt = rs.getString("geometry");
                            if(wkt != null) {
                                Geometry geometry = reader.read(wkt);
                                edge.wayGeometry(GraphHelper.getPillars(geometry));
                            }
                        }*/
                    }
                }
            } else {
                if(rcp1 && !lcp1) {
                    boundaryNodes.add(new BoundaryNode(t, rs.getInt("target"), p2)); // cut
                    /*EdgeIterator edge = */ graph.edge(s, t, rs.getDouble("distance"), flags);
                    // cutEdges.add(rs.getInt("gid"));
                }
                if(rcp2 && !lcp2) {
                    boundaryNodes.add(new BoundaryNode(s, rs.getInt("source"), p1)); // cut
                    /*EdgeIterator edge = */ graph.edge(s, t, rs.getDouble("distance"), flags);
                    // cutEdges.add(rs.getInt("gid"));
                }
            }
        }
        rs.close();
        return new Cell(graph, boundaryNodes, vehicle, nodes);
    }
    
    /**
     * Build the interior cell subgraph for the given quadkey
     * @param qkey
     * @param withGeometry - add the way geometry
     * @return
     * @throws Exception 
     */
    private Cell buildSubgraph(String qkey, boolean withGeometry) throws Exception {
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
        RawEncoder vehicle = new MyCarFlagEncoder(Main.getMaxSpeed(dbName));
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
                    cutEdges.add(rs.getInt("gid"));
                    if(lcp1) 
                        boundaryNodes.add(new BoundaryNode(t, rs.getInt("target"), p2)); // cut
                    else 
                        boundaryNodes.add(new BoundaryNode(s, rs.getInt("source"), p1)); // cut 
                } else {
                    if(! lcp1) {// == and not on line
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
                    cutEdges.add(rs.getInt("gid"));
                }
                if(rcp2 && !lcp2) {
                    boundaryNodes.add(new BoundaryNode(t, rs.getInt("target"), p2)); // cut 
                    cutEdges.add(rs.getInt("gid"));
                }
            }
        }
        rs.close();
        return new Cell(graph, boundaryNodes, vehicle, nodes);
    }
    
    /**
     * Compute the clique between all the boundary nodes of a cell
     * @param qkey - identify uniquely a cell
     * @param exterior - specify to use the cell porcupine instead of the interior cell graph
     * @throws Exception 
     */
    private void computeClique(String qkey, boolean exterior) throws Exception {
        Cell cell = exterior? buildExteriorSubgraph(qkey): buildSubgraph(qkey, false);
        Graph graph = cell.graph;
        RawEncoder vehicle = cell.encoder;
        
        double max_speed = 0.;
        BoundaryNode[] nodesArray = cell.boundaryNodes.toArray(new BoundaryNode[cell.boundaryNodes.size()]);
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
        if(exterior) { // store the interior speed
            st3.clearParameters();
            if(Double.isInfinite(max_speed) || Double.isNaN(max_speed))
                st3.setNull(1, Types.DOUBLE);
            else
                st3.setDouble(1, max_speed);
            st3.setString(2, qkey);
            //st3.executeUpdate();
            st3.addBatch();
        }
    }
    
    private void computeCliqueParallel(final String qkey, final boolean exterior) throws Exception {
        Cell cell = exterior? buildExteriorSubgraph(qkey): buildSubgraph(qkey, false);
        BoundaryNode[] nodesArray = cell.boundaryNodes.toArray(new BoundaryNode[cell.boundaryNodes.size()]);
        final int POOL_SIZE = 5;
        
        class Task implements Runnable {

            double max_speed = 0.;
            boolean exterior;
            Cell cell;
            BoundaryNode[] nodesArray;
            int tid;

            public Task(boolean exterior, Cell cell, BoundaryNode[] nodesArray, int tid) {
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
            st3.clearParameters();
            if(Double.isInfinite(max_speed) || Double.isNaN(max_speed))
                st3.setNull(1, Types.DOUBLE);
            else
                st3.setDouble(1, max_speed);
            st3.setString(2, qkey);
            //st3.executeUpdate();
            st3.addBatch();
        }
    }
    
    private synchronized void storeOverlayEdge(BoundaryNode source, BoundaryNode target, Metrics metrics, boolean bothDir) throws SQLException {
        st2.clearParameters();
        st2.setInt(1, source.getRoadNodeId());
        st2.setInt(2, target.getRoadNodeId());
        st2.setDouble(3, metrics.getDistance()/1000.);
        double speed = metrics.getDistance()*3.6/metrics.getTime();
        if(Double.isInfinite(speed) || Double.isNaN(speed)) speed = 0.;
        st2.setDouble(4, speed);
        st2.setDouble(5, metrics.getTime()/3600.);
        st2.setDouble(6, (bothDir? metrics.getTime()/3600.: 1000000));
        st2.setDouble(7, source.getPoint().getX());
        st2.setDouble(8, source.getPoint().getY());
        st2.setDouble(9, target.getPoint().getX());
        st2.setDouble(10, target.getPoint().getY());
        st2.addBatch();
        //st2.executeUpdate();
    }
    
    public class Cell {
        public final Graph graph;
        public final Set<BoundaryNode> boundaryNodes;
        public final RawEncoder encoder;
        public final Map<Integer, Integer> graph2subgraph;

        public Cell(final Graph graph, final Set<BoundaryNode> boundaryNodes, final RawEncoder encoder, final Map<Integer, Integer> graph2subgraph) {
            this.graph = graph;
            this.boundaryNodes = boundaryNodes;
            this.encoder = encoder;
            this.graph2subgraph = graph2subgraph;
        }
    }
}
class AlgorithmPreparation extends NoOpAlgorithmPreparation {

    private final RawEncoder vehicle;
    
    public AlgorithmPreparation(RawEncoder vehicle) {
        this.vehicle = vehicle;
    }
    
    @Override
    public RoutingAlgorithm createAlgo() {
        return new DijkstraBidirectionRef(_graph, vehicle).type(new FastestCalc(vehicle));
    }
    
}

class TasksHelper implements Runnable {
    public static final String sql1 = "SELECT DISTINCT tiles_qkey FROM ways_tiles WHERE length(tiles_qkey)=? ORDER BY tiles_qkey";
    public static final String sql2 = "SELECT my_add_cut_edges(?, ?);";
    public static final Integer POOL_SIZE = 4;
    private final TileSystem tileSystem;
    private final int scale;
    private String dbName;
    private final ThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(POOL_SIZE);
    private final static Logger logger = Logger.getLogger(TasksHelper.class.getName());
    private List<String> list;
    private boolean overlayGen = false;

    public boolean isOverlayGen() {
        return overlayGen;
    }

    public void setOverlayGen(boolean overlayGen) {
        this.overlayGen = overlayGen;
    }
    
    public TasksHelper(final TileSystem tileSystem, final String dbName, final int scale) {
        this.tileSystem = tileSystem;
        this.scale = scale;
        this.dbName = dbName;
    }
    
    public TasksHelper(final TileSystem tileSystem, final String dbName, final int scale, List<String> list) {
        this.tileSystem = tileSystem;
        this.scale = scale;
        this.dbName = dbName;
        this.list = list;
    }

    @Override
    public void run() {
        try {
            int amount;
            if(list == null) {
                list = new LinkedList<>();
                try(Connection conn = Main.getConnection(this.dbName); 
                        PreparedStatement st = conn.prepareStatement(sql1)) {
                    st.setInt(1, scale);
                    
                    try (ResultSet rs1 = st.executeQuery()) {
                        while(rs1.next()) { // for each tiles
                            list.add(rs1.getString(1));
                        }
                    }
                }
                amount = list.size() / POOL_SIZE;
            } else {
                amount = scale>6? 1<<(scale-7): 1;
            }
            if(amount == 0) amount = 1;
            logger.log(Level.INFO, String.format("Computing cliques for %s and scale=%d", dbName, scale));
            int start;
            List<SubgraphTask> tasks = new LinkedList<>();
            for(start = 0; start+amount < list.size(); start += amount) {
                SubgraphTask task = new SubgraphTask(tileSystem, dbName, scale, list.subList(start, start+amount));
                task.setOverlayGen(overlayGen);
                tasks.add(task);
                pool.execute(task);
            }
            SubgraphTask task = new SubgraphTask(tileSystem, dbName, scale, list.subList(start, list.size()));
            task.setOverlayGen(overlayGen);
            pool.execute(task);
            tasks.add(task);
            pool.shutdown();
            pool.awaitTermination(1l, TimeUnit.DAYS);
            if(overlayGen) {
                logger.log(Level.INFO, String.format("Adding cut-edges for %s and scale=%d", dbName, scale));
                Set<Integer> cutEdges = new TreeSet<>();
                for(SubgraphTask t: tasks) {
                    cutEdges.addAll(t.getCutEdges());
                }
                
                try (Connection conn = Main.getConnection(dbName);
                        PreparedStatement st = conn.prepareStatement(sql2)) {
                    st.setInt(1, scale);
                    st.setArray(2, conn.createArrayOf("integer", cutEdges.toArray()));
                    st.executeQuery();
                }
            }
        } catch (SQLException | InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

}
