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

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AcceptWay;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;
import com.mycompany.tesi.SubgraphTask.Cell;
import com.mycompany.tesi.hooks.FastestCalc;
import com.mycompany.tesi.hooks.MyCarFlagEncoder;
import com.mycompany.tesi.hooks.RawEncoder;
import com.mycompany.tesi.hooks.TimeCalculation;
import com.mycompany.tesi.utils.QuadKeyManager;
import com.mycompany.tesi.utils.TileSystem;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;
import gnu.trove.list.TIntList;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author Tommaso
 */
public class OverlayTest extends TestCase {
    
    private int[] fromNodes = { 26736 };
    private int[] toNodes = { 9595 };
    
    public OverlayTest(String testName) {
        super(testName);
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }
    
    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }
    
    @Test
    public void testPath() throws Exception {
        GraphStorage graph = new GraphBuilder().create();
        graph.combinedEncoder(RawEncoder.COMBINED_ENCODER);
        RawEncoder vehicle = new RawEncoder(130);
        WKTReader reader = new WKTReader();
        String dbName = "berlin_routing";
        try(Connection conn = Main.getConnection(dbName); 
            Statement st = conn.createStatement()) {
            ResultSet rs;
            String table = "ways";
            rs = st.executeQuery("select * from ((select distinct source, y1, x1 from " + table + ") union (select distinct target, y2, x2 from " + table + ")) nodes order by source");
            while(rs.next())
                graph.setNode(rs.getInt(1), rs.getDouble(2), rs.getDouble(3));
            rs.close();
            rs = st.executeQuery("select source, target, km*1000, reverse_cost<>1000000, freeflow_speed, st_astext(the_geom) from " + table);//st_astext(the_geom)
            while(rs.next()) {
                EdgeIterator edge = graph.edge(rs.getInt(1), rs.getInt(2), rs.getDouble(3), vehicle.flags(rs.getDouble(5), rs.getBoolean(4)));
                Geometry geometry = reader.read(rs.getString(6));
                PointList pillarNodes = Main.getPillars(geometry);
                if(pillarNodes != null)
                    edge.wayGeometry(pillarNodes);
            }
            rs.close();
        }
        
        GraphHopperAPI instance = new GraphHopper(graph).forDesktop();
        long time = System.nanoTime();
        GHResponse ph = instance.route(new GHRequest(graph.getLatitude(fromNodes[0]), graph.getLongitude(fromNodes[0]), graph.getLatitude(toNodes[0]), graph.getLongitude(toNodes[0])).algorithm("dijkstrabi").type(new FastestCalc(vehicle)).vehicle(vehicle));//52.406608,13.286591&point=52.568004,13.53241
        time = System.nanoTime() - time;
        assertTrue(ph.found());
        System.out.println("road graph: "+time/1e9);
        System.out.println(ph.distance());
        System.out.println(ph.path.calcPoints());
        System.out.println(ph.path.calcNodes());
        System.out.println(new TimeCalculation(vehicle).calcTime(ph.path));
    }
    
    private void union(final Graph graph, final Cell cell, final RawEncoder vehicle) {
        Map<Integer, Integer> inverse = new HashMap<>();
        for(Integer key: cell.graph2subgraph.keySet()) {
            int value = cell.graph2subgraph.get(key);
            graph.setNode(key, cell.graph.getLatitude(value), cell.graph.getLongitude(value));
            inverse.put(value, key);
        }
        
        AllEdgesIterator iterator = cell.graph.getAllEdges();
        while(iterator.next())
            if(cell.encoder.isForward(iterator.flags()))
                graph.edge(inverse.get(iterator.baseNode()), inverse.get(iterator.adjNode()), iterator.distance(), vehicle.flags(cell.encoder.getSpeedHooked(iterator.flags()), cell.encoder.isBackward(iterator.flags())));
            else
                graph.edge(inverse.get(iterator.adjNode()), inverse.get(iterator.baseNode()), iterator.distance(), vehicle.flags(cell.encoder.getSpeedHooked(iterator.flags()), cell.encoder.isForward(iterator.flags())));
        
    }
    
    @Test
    public void testPath1() throws Exception {
        int scale = 15;
        GraphStorage graph = new GraphBuilder().create();
        graph.combinedEncoder(RawEncoder.COMBINED_ENCODER);
        RawEncoder vehicle = new RawEncoder(130);
        
        String dbName = "berlin_routing";
        try(Connection conn = Main.getConnection(dbName); 
            Statement st = conn.createStatement()) {
            ResultSet rs;
            String table = "overlay_" + scale;
            rs = st.executeQuery("select * from ((select distinct source, y1, x1 from " + table + ") union (select distinct target, y2, x2 from " + table + ")) nodes order by source");
            while(rs.next())
                graph.setNode(rs.getInt(1), rs.getDouble(2), rs.getDouble(3));
            rs.close();
            rs = st.executeQuery("select source, target, km*1000, reverse_cost<>1000000, freeflow_speed from " + table);//st_astext(the_geom)
            while(rs.next()) {
                graph.edge(rs.getInt(1), rs.getInt(2), rs.getDouble(3), vehicle.flags(rs.getDouble(5), rs.getBoolean(4)));
            }
            rs.close();
        }
        TileSystem tileSystem = new TileSystem(Main.getBound(Main.getConnection(dbName)), Main.MAX_SCALE);
        tileSystem.computeTree();
        SubgraphTask task = new SubgraphTask(tileSystem, dbName, scale);
        /*
        GraphStorage s = new GraphBuilder().create();
        s.combinedEncoder(MyCarFlagEncoder.COMBINED_ENCODER);
        Graph g = graph.copyTo(s);
        */
        String start_qkey = QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(graph.getLongitude(fromNodes[0]), graph.getLatitude(fromNodes[0]), scale), scale);
        Cell startCell = task.getSubgraph(start_qkey);
        String end_qkey = QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(graph.getLongitude(toNodes[0]), graph.getLatitude(toNodes[0]), scale), scale);
        Cell endCell = task.getSubgraph(end_qkey);
        union(graph, startCell, vehicle);
        union(graph, endCell, vehicle);
        
        GraphHopperAPI instance = new GraphHopper(graph).forDesktop();
        long time = System.nanoTime();
        GHResponse ph = instance.route(new GHRequest(graph.getLatitude(fromNodes[0]), graph.getLongitude(fromNodes[0]), graph.getLatitude(toNodes[0]), graph.getLongitude(toNodes[0])).algorithm("dijkstrabi").type(new FastestCalc(vehicle)).vehicle(vehicle));//52.406608,13.286591&point=52.568004,13.53241
        time = System.nanoTime() - time;
        assertTrue(ph.found());
        System.out.println("overlay: "+time/1e9);
        System.out.println(ph.distance());
        /*
        for(Integer n :ph.path.calcNodes().toArray())
            System.out.print(graph.getLongitude(n) + " "+graph.getLatitude(n)+ " ");
        System.out.println();
        //System.out.println(ph.path.calcPoints());*/
        System.out.println(ph.path.calcNodes());
        PointList roadPoints = task.pathUnpacking(ph.path, start_qkey, end_qkey);
        System.out.println(roadPoints.size());
        System.out.println(roadPoints);
        System.out.println("TIME: " + new TimeCalculation(vehicle).calcTime(ph.path));
    }
}
