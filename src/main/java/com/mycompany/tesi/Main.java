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

import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.utils.TileSystem;
import com.mycompany.tesi.beans.Pair;
import com.mycompany.tesi.utils.ConnectionPool;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import java.sql.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;

/**
 * Preprocessing: creazione e salvataggio tiles.
 * @author Tommaso
 */
public class Main {
    
    public static final String[] DBS = { "berlin_routing", "hamburg_routing", "london_routing" };
    public static final Integer MIN_SCALE = 13;
    public static final Integer MAX_SCALE = 17;
    public static final Integer POOL_SIZE = 3;
    public static final Integer MAX_ACTIVE_DATASOURCE_CONNECTIONS = 10;
    private final static Map<String, ConnectionPool> DATASOURCES = new HashMap<>();
    private static final ThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(POOL_SIZE);
    public static boolean TEST = false;
    
    static {
        for(String db: DBS)
            DATASOURCES.put(db, new ConnectionPool(db, MAX_ACTIVE_DATASOURCE_CONNECTIONS));
        
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                for(ConnectionPool ds: DATASOURCES.values())
                    ds.close();
            }
        }));
    }
    
    public static Connection getConnection(final String db) {
        try {
            if(TEST)
                return DriverManager.getConnection(ConnectionPool.JDBC_URI + db, "postgres", "postgres");
            else
                return DATASOURCES.get(db).getDataSource().getConnection();
        } catch (SQLException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("Sure?");
        System.in.read();
        for(String dbName: DBS) {
            System.out.println("Processing db " + dbName + " ...");
            //create_tiles(dbName);
            threadedSubgraph(dbName);
        }
        pool.shutdown();
        /*pool.awaitTermination(1l, TimeUnit.DAYS);
        System.out.println("Exiting ...");
        for(ConnectionPool ds: DATASOURCES.values())
            ds.close();*/
    }
    
    public static Envelope2D getBound(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); 
            ResultSet rs = st.executeQuery("SELECT MIN(x1), MIN(x2), MIN(y1), MIN(y2), MAX(x1), MAX(x2), MAX(y1), MAX(y2) FROM ways")) {
            rs.next();
            Envelope2D bound = new Envelope2D(
                new DirectPosition2D(Math.min(rs.getDouble(1), rs.getDouble(2)), Math.min(rs.getDouble(3), rs.getDouble(4))), 
                new DirectPosition2D(Math.max(rs.getDouble(5), rs.getDouble(6)), Math.max(rs.getDouble(7), rs.getDouble(8)))
            );
            return bound;
        }
    }
    
    public static void create_tiles(String dbName) throws SQLException {
        try (Connection conn = getConnection(dbName);
            Statement st = conn.createStatement();
            PreparedStatement pst = conn.prepareStatement("INSERT INTO tiles(qkey, lon1, lat1, lon2, lat2, shape) VALUES(?, ?, ?, ?, ?, ST_SetSRID(ST_MakeBox2D(ST_Point(?, ?), ST_Point(?, ?)), 4326));")) {
            Envelope2D bound = getBound(conn);
            TileSystem tileSystem = new TileSystem(bound, MAX_SCALE);
            tileSystem.computeTree();
            Enumeration e = tileSystem.getTreeEnumeration();
            st.execute("TRUNCATE TABLE tiles; TRUNCATE TABLE ways_tiles;");
            List<Pair<String, Polygon>> tiles = new LinkedList<>();
            System.out.println("Saving tiles ...");
            while(e.hasMoreElements()) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
                Tile tile = (Tile) node.getUserObject();
                if(tile == null || node.isRoot()) continue;
                TreeNode[] path = node.getPath();
                String qkey = "";
                for(int i = 1; i < path.length; i ++)
                    qkey += path[i-1].getIndex(path[i]);

                tiles.add(new Pair(qkey, tile.getPolygon()));
                pst.clearParameters();
                pst.setString(1, qkey);
                pst.setDouble(2, tile.getRect().getMinX());
                pst.setDouble(3, tile.getRect().getMinY());
                pst.setDouble(4, tile.getRect().getMaxX());
                pst.setDouble(5, tile.getRect().getMaxY());
                pst.setDouble(6, tile.getRect().getMinX());
                pst.setDouble(7, tile.getRect().getMinY());
                pst.setDouble(8, tile.getRect().getMaxX());
                pst.setDouble(9, tile.getRect().getMaxY());
                pst.addBatch();
            }
            pst.executeBatch();
            //st.execute("DELETE FROM tiles WHERE qkey='';"); // removing the root node
            System.out.println("Binding ways-tiles ...");
            
            List<Pair<Integer, Geometry>> ways = new LinkedList<>();
            WKTReader reader = new WKTReader();
            ResultSet rs;
            rs = st.executeQuery("SELECT gid, st_astext(the_geom) AS geometry FROM ways;");
            try {
                while(rs.next())
                    ways.add(new Pair(rs.getInt("gid"), reader.read(rs.getString("geometry"))));
            } catch (ParseException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(0);
            } finally {
                rs.close();
            }
            
            class Task implements Runnable {

                private final List<Pair<String, Polygon>> tiles;
                private final List<Pair<Integer, Geometry>> ways;
                private final String dbName;
                
                public Task(String dbName, List<Pair<String, Polygon>> tiles, List<Pair<Integer, Geometry>> ways) {
                    this.dbName = dbName;
                    this.tiles = tiles;
                    this.ways = ways;
                }
                
                @Override
                public void run() {
                    try(Connection conn = Main.getConnection(dbName); 
                        PreparedStatement st = conn.prepareStatement("INSERT INTO ways_tiles(tiles_qkey, ways_id) VALUES(?, ?);")) {
                        
                        for(Pair<String, Polygon> tile: tiles) 
                            for(Pair<Integer, Geometry> way: ways)
                                if(tile.getValue().intersects(way.getValue())) {
                                    st.clearParameters();
                                    st.setString(1, tile.getKey());
                                    st.setInt(2, way.getKey());
                                    st.addBatch();
                                }
                        st.executeBatch();
                    } catch (SQLException ex) {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                
            }
            
            int start, amount = 1000;
            for(start = 0; start+amount < tiles.size(); start += amount) {
                Task task = new Task(dbName, tiles.subList(start, start+amount), ways);
                pool.execute(task);
            }
            Task task = new Task(dbName, tiles.subList(start, tiles.size()), ways);
            pool.execute(task);
            
            //st.execute("SELECT my_ways_tiles_fill();"); // call it to fill the relation between tiles and ways
        }
    }
      
    public static void threadedSubgraph(String db) throws Exception {
        Envelope2D bound;
        try (Connection conn = getConnection(db)) {
            bound = getBound(conn);
        }
        TileSystem tileSystem = new TileSystem(bound, MAX_SCALE);
        tileSystem.computeTree();
        
        for(int i = MIN_SCALE; i <= MAX_SCALE; i ++)
            pool.execute(new TasksHelper(tileSystem, db, i));//new SubgraphTask(tileSystem, db, i));
    }
    
    // unused
    static void loadTiles(Connection conn) throws SQLException {
        Envelope2D bound = getBound(conn);
        TileSystem tileSystem = new TileSystem(bound, MAX_SCALE);
        tileSystem.computeTree();
        boolean ok = true;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT qkey FROM tiles")) {
            while(rs.next()) {
                Tile tile = tileSystem.getTile(rs.getString("qkey"));
                
                ok &= tile != null;
            }
        }
        System.out.println(ok);
    }
}

/*
--select distinct tiles_qkey from ways_tiles where length(tiles_qkey)>16 order by tiles_qkey
   SELECT st_astext(st_intersection(st_setsrid(st_makeline(array[
       st_point(lon1,lat1), st_point(lon2,lat1), st_point(lon2,lat2), st_point(lon1,lat2), st_point(lon1,lat1)
   ]), 4326), the_geom)), *
   FROM ways JOIN ways_tiles ON gid=ways_id JOIN tiles ON tiles_qkey=qkey 
   WHERE tiles_qkey='120210232303' and not st_contains(shape, the_geom)

*/
/*
    public static void subgraph(Connection conn) throws Exception {
        Envelope2D bound = getBound(conn);
        TileSystem tileSystem = new TileSystem(bound, MAX_SCALE);
        tileSystem.computeTree();
        WKTReader reader = new WKTReader();
        
        //GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        String sql1 = "SELECT DISTINCT tiles_qkey FROM ways_tiles WHERE length(tiles_qkey)=17 ORDER BY tiles_qkey";
        String sql2 = "SELECT ways.source, ways.target, ways.freeflow_speed, ways.length, ways.reverse_cost=1000000 AS oneway, ways.km*1000 AS distance, ways.x1, ways.y1, ways.x2, ways.y2, st_astext(ways.the_geom) AS geometry, st_contains(shape, the_geom) AS contained " +
            "FROM ways JOIN ways_tiles ON gid = ways_id JOIN tiles ON tiles_qkey = qkey WHERE qkey = ?";
        
        try (Statement st1 = conn.createStatement(); ResultSet rs1 = st1.executeQuery(sql1);
            PreparedStatement st2 = conn.prepareStatement(sql2)) {
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
        }
    }
    */