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
import com.graphhopper.storage.Graph;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;
import com.mycompany.tesi.SubgraphTask.Cell;
import com.mycompany.tesi.hooks.FastestCalc;
import com.mycompany.tesi.hooks.MyCarFlagEncoder;
import com.mycompany.tesi.hooks.RawEncoder;
import com.mycompany.tesi.utils.GraphHelper;
import com.mycompany.tesi.utils.QuadKeyManager;
import com.mycompany.tesi.utils.TileSystem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tommaso
 */
public class OverlayTest extends TestCase {
    
    private GHPlace[] fromNodes;// = { new GHPlace(52.4059488, 13.2831624) };
    private GHPlace[] toNodes;// = { new GHPlace(52.5663245, 13.5318755) };
    private final static String dbName = "london_routing";
    private final static int POINTS_COUNT = 20;
    private TileSystem tileSystem;
    
    private List<Integer> ids;
    
    public OverlayTest(String testName) {
        super(testName);
    }
    
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        Main.TEST = true;
        Connection conn = Main.getConnection(dbName);
        Statement st = conn.createStatement();
        
        /*List<Integer> a = new ArrayList<>();
        ResultSet rs = st.executeQuery("select distinct source from (select source from overlay_13 union select target from overlay_13) n");
        while(rs.next())
            a.add(rs.getInt(1));
        rs.close();
        int maxId = a.size()-1;
        */
        ResultSet rs = st.executeQuery("SELECT max(source), max(target) from ways");
        rs.next();
        int maxId = Math.max(rs.getInt(1), rs.getInt(2));
        rs.close();
        ids = new ArrayList<>(POINTS_COUNT*2);
        for(int i = 0; i < POINTS_COUNT*2; i++) {
            int value = (int)Math.round(Math.random()*maxId);
            if(ids.contains(value))
                i--;
            else
                ids.add(value);
                //ids.add(a.get(value));
        }
        st.close();
        //ids.set(0, 17553); ids.set(1, 8403);
        
        //ids.set(0, 19474); ids.set(1, 32874);//berlin
        //ids.set(0, 10258); ids.set(1, 21855);//hamburg
        fromNodes = new GHPlace[POINTS_COUNT];
        toNodes = new GHPlace[POINTS_COUNT];
        PreparedStatement pst = conn.prepareStatement("select y1, x1 from (select y1, x1, source from ways union select y2, x2, target from ways) t where source=?");
        for(int i = 0; i < POINTS_COUNT*2; i++) {
            pst.clearParameters();
            pst.setInt(1, ids.get(i));
            rs = pst.executeQuery();
            rs.next();
            if(i < POINTS_COUNT)
                fromNodes[i] = new GHPlace(rs.getDouble(1), rs.getDouble(2));
            else
                toNodes[i-POINTS_COUNT] = new GHPlace(rs.getDouble(1), rs.getDouble(2));
            rs.close();
        }
        pst.close();
        conn.close();
        tileSystem = Main.getTileSystem(dbName);
    }
    
    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }
    
    
    
    private PointList getPath2(final Graph graph, final RawEncoder vehicle, final GHPlace from, final GHPlace to) throws Exception {
        /*GraphStorage graph = new GraphBuilder().create();
        graph.combinedEncoder(RawEncoder.COMBINED_ENCODER);
        RawEncoder vehicle = new MyCarFlagEncoder(130);
        
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
                edge.wayGeometry(GraphHelper.getPillars(geometry));
            }
            rs.close();
        }*/
        
        GraphHopperAPI instance = new GraphHopper(graph).forDesktop();
        long time = System.nanoTime();
        GHResponse ph = instance.route(new GHRequest(from, to).algorithm("dijkstrabi").type(new FastestCalc(vehicle)).vehicle(vehicle));//52.406608,13.286591&point=52.568004,13.53241
        time = System.nanoTime() - time;
        if(!ph.found()) return null;
        
        System.out.println("road graph: "+time/1e9);
        /*
        System.out.println(ph.distance());
        System.out.println(ph.points().size());*/
       // System.out.println(ph.path.calcNodes());
        // System.out.println(new TimeCalculation(vehicle).calcTime(ph.path));
        return ph.points();
    }
    
    private PointList getPath1(Graph graph, final RawEncoder vehicle, final GHPlace from, final GHPlace to, int scale) throws Exception {
        /*GraphStorage graph = new GraphBuilder().create();
        graph.combinedEncoder(RawEncoder.COMBINED_ENCODER);
        RawEncoder vehicle = new MyCarFlagEncoder(130);
        try(Connection conn = Main.getConnection(dbName); 
            Statement st = conn.createStatement()) {
            ResultSet rs;
            String table = "overlay_" + scale;
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
        }*/
        SubgraphTask task = new SubgraphTask(tileSystem, dbName, scale);
        /*
        GraphStorage s = new GraphBuilder().create();
        s.combinedEncoder(MyCarFlagEncoder.COMBINED_ENCODER);
        Graph g = graph.copyTo(s);
        */
        
        String start_qkey = QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(from.lon, from.lat, scale), scale);
        Cell startCell = task.getSubgraph(start_qkey);
        String end_qkey = QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(to.lon, to.lat, scale), scale);
        Cell endCell = task.getSubgraph(end_qkey);
        graph = GraphHelper.cloneGraph(graph, startCell, endCell);
        GraphHelper.union(graph, startCell, vehicle);
        if(!end_qkey.equals(start_qkey))
            GraphHelper.union(graph, endCell, vehicle);
        
        //System.out.println(start_qkey + " " + end_qkey);
        /*System.out.println(QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(graph.getLongitude(13465), graph.getLatitude(13465), scale), scale));
        
        System.out.println(QuadKeyManager.fromTileXY(tileSystem.pointToTileXY(graph.getLongitude(14483), graph.getLatitude(14483), scale), scale));
        */
        GraphHopperAPI instance = new GraphHopper(graph).forDesktop();
        long time = System.nanoTime();
        /*EdgeIterator i = graph.getEdges(21024);
        while(i.next())
            //    i.distance(100);
            System.out.println("SYS "+scale+i.adjNode() +" " + i.baseNode() + " " +i.distance() +" "+vehicle.getSpeedHooked(i.flags())+" "+ vehicle.isForward(i.flags()) + " "+ vehicle.isBackward(i.flags()) );
        */
        GHResponse ph = instance.route(new GHRequest(from, to).algorithm("dijkstrabi").type(new FastestCalc(vehicle)).vehicle(vehicle));
        if(!ph.found()) return null;
        /*time = System.nanoTime() - time;
        assertTrue(ph.found());
        System.out.println("overlay: "+time/1e9);*/
        //System.out.println(ph.distance());
        /*
        for(Integer n :ph.path.calcNodes().toArray())
            System.out.print(graph.getLongitude(n) + " "+graph.getLatitude(n)+ " ");
        System.out.println();
        //System.out.println(ph.path.calcPoints());*/
        //time = System.nanoTime();
        PointList roadPoints = task.pathUnpacking(graph, ph.path, start_qkey, end_qkey);
        time = System.nanoTime() - time;
      //  System.out.println(ph.path.calcNodes());
        System.out.println("overlay: "+time/1e9);
        //System.out.println(roadPoints.size());
        //System.out.println("TIME: " + new TimeCalculation(vehicle).calcTime(ph.path));
        return roadPoints;
    }
    
    @Test
    public void testPath() throws Exception {
        PointList[] expected = new PointList[POINTS_COUNT]; 
        {
            RawEncoder vehicleRoad = new MyCarFlagEncoder(130);
            Graph roadGraph = GraphHelper.readGraph(dbName, "ways", vehicleRoad);        
            for(int i = 0; i < POINTS_COUNT; i++)
                expected[i] = getPath2(roadGraph, vehicleRoad, fromNodes[i], toNodes[i]);
        }
        for(int j = Main.MIN_SCALE; j <= Main.MAX_SCALE; j ++) {
            RawEncoder vehicle = new MyCarFlagEncoder(130);
            Graph graph = GraphHelper.readGraph(dbName, "overlay_" + j, vehicle);
            for(int i = 0; i < POINTS_COUNT; i++) {
                System.out.println("FROM: "+ids.get(i) + " TO: " + ids.get(i+POINTS_COUNT) + " scale="+j);
                assertEquals(expected[i], getPath1(graph, vehicle, fromNodes[i], toNodes[i], j));
            }
        }
    }
}
