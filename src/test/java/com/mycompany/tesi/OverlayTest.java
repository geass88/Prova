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
import com.mycompany.tesi.SubgraphTask.Subgraph;
import com.mycompany.tesi.hooks.FastestCalc;
import com.mycompany.tesi.hooks.MyCarFlagEncoder;
import com.mycompany.tesi.hooks.RawEncoder;
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
                /*Geometry geometry = reader.read(rs.getString(6));
                int pillarNumber = geometry.getNumPoints() - 2;
                if(pillarNumber > 0) {
                    PointList pillarNodes = new PointList(pillarNumber);
                    for(int i = 1; i <= pillarNumber; i ++) {
                        pillarNodes.add(geometry.getCoordinates()[i].getOrdinate(1), geometry.getCoordinates()[i].getOrdinate(0));
                        //System.out.println("lat=" + g.getCoordinates()[i].getOrdinate(1) +" lon = "+ g.getCoordinates()[i].getOrdinate(0));
                    }
                    edge.wayGeometry(pillarNodes);
                }*/
            }
            rs.close();
        }
        
        GraphHopperAPI instance = new GraphHopper(graph).forDesktop();
        long time = System.nanoTime();
        GHResponse ph = instance.route(new GHRequest(52.4059488,13.2831624, 52.5663245,13.5318755).algorithm("dijkstrabi").type(new FastestCalc(vehicle)).vehicle(vehicle));//52.406608,13.286591&point=52.568004,13.53241
        time = System.nanoTime() - time;
        assertTrue(ph.found());
        System.out.println("road graph: "+time/1e9);
        System.out.println(ph.distance());
        //System.out.println(ph.path.calcPoints());
        System.out.println(ph.path.calcNodes());
        
    }
    
    @Test
    public void testPath1() throws Exception {
        GraphStorage graph = new GraphBuilder().create();
        graph.combinedEncoder(RawEncoder.COMBINED_ENCODER);
        RawEncoder vehicle = new RawEncoder(130);
        
        String dbName = "berlin_routing";
        try(Connection conn = Main.getConnection(dbName); 
            Statement st = conn.createStatement()) {
            ResultSet rs;
            String table = "overlay_15";
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
        SubgraphTask task = new SubgraphTask(tileSystem, dbName, 15);
        String qkey = QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(13.2831624, 52.4059488, 15), 15);
        Subgraph start = task.getSubgraph(qkey);
        qkey = QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(13.5318755, 52.5663245, 15), 15);
        Subgraph end = task.getSubgraph(qkey);
        
        for(Integer node: start.graph2subgraph.keySet())
            graph.setNode(node, start.graph.getLatitude(start.graph2subgraph.get(node)), start.graph.getLongitude(start.graph2subgraph.get(node)));
        
        for(Integer node: end.graph2subgraph.keySet())
            graph.setNode(node, end.graph.getLatitude(end.graph2subgraph.get(node)), end.graph.getLongitude(end.graph2subgraph.get(node)));
        
        Map<Integer, Integer> sinverse = new HashMap<>();
        for(Integer node: start.graph2subgraph.keySet())
            sinverse.put(start.graph2subgraph.get(node), node);
        
        Map<Integer, Integer> einverse = new HashMap<>();
        for(Integer node: end.graph2subgraph.keySet())
            einverse.put(end.graph2subgraph.get(node), node);
        
        AllEdgesIterator i = start.graph.getAllEdges();
        while(i.next())
            if(start.encoder.isForward(i.flags()))
                graph.edge(sinverse.get(i.baseNode()), sinverse.get(i.adjNode()), i.distance(), vehicle.flags(start.encoder.getSpeedHooked(i.flags()), start.encoder.isBackward(i.flags())));
            else
                graph.edge(sinverse.get(i.adjNode()), sinverse.get(i.baseNode()), i.distance(), vehicle.flags(start.encoder.getSpeedHooked(i.flags()), start.encoder.isForward(i.flags())));
        i = end.graph.getAllEdges();
        while(i.next())
            if(end.encoder.isForward(i.flags()))
                graph.edge(einverse.get(i.baseNode()), einverse.get(i.adjNode()), i.distance(), vehicle.flags(end.encoder.getSpeedHooked(i.flags()), end.encoder.isBackward(i.flags())));
            else
                graph.edge(einverse.get(i.adjNode()), einverse.get(i.baseNode()), i.distance(), vehicle.flags(end.encoder.getSpeedHooked(i.flags()), end.encoder.isForward(i.flags())));
       /* GraphStorage s = new GraphBuilder().create();
        s.combinedEncoder(MyCarFlagEncoder.COMBINED_ENCODER);
        Graph g = graph.copyTo(s);*/
        GraphHopperAPI instance = new GraphHopper(graph).forDesktop();
        long time = System.nanoTime();
        GHResponse ph = instance.route(new GHRequest(52.4059488,13.2831624, 52.5663245,13.5318755).algorithm("dijkstrabi").type(new FastestCalc(vehicle)).vehicle(vehicle));//52.406608,13.286591&point=52.568004,13.53241
        time = System.nanoTime() - time;
        assertTrue(ph.found());
        System.out.println("overlay: "+time/1e9);
        System.out.println(ph.distance());
        /*
        for(Integer n :ph.path.calcNodes().toArray())
            System.out.print(graph.getLongitude(n) + " "+graph.getLatitude(n)+ " ");
        System.out.println();
        //System.out.println(ph.path.calcPoints());*/
        
        task.pathUnpacking(ph.path);
    }
}
