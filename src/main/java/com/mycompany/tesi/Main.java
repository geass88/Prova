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

import com.mycompany.tesi.beans.DBData;
import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.utils.TileSystem;
import com.mycompany.tesi.beans.Pair;
import com.mycompany.tesi.utils.ConnectionPool;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import java.io.File;
import java.io.FileInputStream;
import java.sql.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;

/**
 * Preprocessing: creazione e salvataggio tiles, creazione overlay_graph.
 * @author Tommaso
 */
public class Main {
    
    public static final String[] DBS;// = { "berlin_routing", "hamburg_routing", "london_routing" };
    public static final String JDBC_URI;
    public static final String JDBC_USERNAME;
    public static final String JDBC_PASSWORD;
    public static final Integer MIN_SCALE;
    public static final Integer MAX_SCALE;
    public static final Integer POOL_SIZE;
    public static final Integer MAX_ACTIVE_DATASOURCE_CONNECTIONS;
    private final static ThreadPoolExecutor pool;
    private final static Logger logger = Logger.getLogger(Main.class.getName());
    public final static Properties PROPERTIES = new Properties();
    public static boolean TEST = false;
    private final static boolean GOAL_TILES;
    
    private final static Map<String, DBData> DBDataMap = new HashMap<>();
    
    static {        
        try {
            File file = new File("D:\\Workspace\\Netbeans\\Tesi\\config.xml");
            if(file.exists())
                PROPERTIES.loadFromXML(new FileInputStream(file));
            else 
                PROPERTIES.loadFromXML(new FileInputStream("F:\\Tommaso\\Workspace\\Tesi\\config.xml"));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Cannot found the config file!", ex);
            System.exit(1);
        }
        GOAL_TILES = Boolean.parseBoolean(PROPERTIES.getProperty("goal_tiles", "true"));
        pool = new ScheduledThreadPoolExecutor(POOL_SIZE=(GOAL_TILES? 6: 3));
        JDBC_URI = PROPERTIES.getProperty("jdbc_uri", "jdbc:postgresql://localhost:5432/");
        JDBC_USERNAME = PROPERTIES.getProperty("jdbc_username", "postgres");
        JDBC_PASSWORD = PROPERTIES.getProperty("jdbc_password", "postgres");
        MAX_ACTIVE_DATASOURCE_CONNECTIONS = Integer.valueOf(PROPERTIES.getProperty("jdbc_max_active", "10"));
        DBS = PROPERTIES.getProperty("jdbc_databases", "routing").split(" ");
        MIN_SCALE = Integer.valueOf(PROPERTIES.getProperty("min_scale", "13"));
        MAX_SCALE = Integer.valueOf(PROPERTIES.getProperty("max_scale", "17"));
        
        for(String db: DBS) {
            //DATASOURCES.put(db, new ConnectionPool(JDBC_URI, JDBC_USERNAME, JDBC_PASSWORD, db, MAX_ACTIVE_DATASOURCE_CONNECTIONS));
            DBData dbData = new DBData();
            ConnectionPool cp = new ConnectionPool(JDBC_URI, JDBC_USERNAME, JDBC_PASSWORD, db, MAX_ACTIVE_DATASOURCE_CONNECTIONS);
            try(Connection conn = cp.getDataSource().getConnection();
                    Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery("select MAX(freeflow_speed) as max_speed from ways;")
                    ) {
                if(rs.next())
                    dbData.setMaxSpeed(rs.getInt("max_speed"));
            } catch(SQLException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            dbData.setConnectionPool(cp);
            DBDataMap.put(db, dbData);
        }
        
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                for(DBData dbData: DBDataMap.values())
                    dbData.getConnectionPool().close();
            }
        }));
    }
    
    public static Connection getConnection(final String db) {
        try {
            if(TEST)
                return DriverManager.getConnection(JDBC_URI + db, JDBC_USERNAME, JDBC_PASSWORD);
            else
                return DBDataMap.get(db).getConnectionPool().getDataSource().getConnection();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static synchronized TileSystem getTileSystem(final String db) {
        if(DBDataMap.get(db).getTileSystem() == null)
            try(Connection conn = Main.getConnection(db)) {
                TileSystem tileSystem = new TileSystem(Main.getBound(conn), MAX_SCALE);
                tileSystem.computeTree();
                DBDataMap.get(db).setTileSystem(tileSystem);
            } catch(SQLException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        return DBDataMap.get(db).getTileSystem();
    }
    
    public static int getMaxSpeed(final String db) {
        return DBDataMap.get(db).getMaxSpeed();
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println(GOAL_TILES? "Creating tiles ...": "Creating overlays ...");
        System.out.println("Sure?");
        System.in.read();
        for(String dbName: DBS) {
            logger.log(Level.INFO, "Processing db {0} ...", dbName);
            if(GOAL_TILES)
                createTiles(dbName);
            else
                threadedSubgraph(dbName);
        }
        pool.shutdown();
        if(GOAL_TILES) { // calcola la velocità massima di ciascun tile senza usare gli overlay (max delle strade che intersecano il tile!)
            try {
                pool.awaitTermination(1, TimeUnit.DAYS);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            for(String dbName: DBS)
                doSpeed(dbName);
        }
        /*pool.awaitTermination(1l, TimeUnit.DAYS);
        System.out.println("Exiting ...");
        for(ConnectionPool ds: DATASOURCES.values())
            ds.close();*/
    }
    
    public static Envelope2D getBound(final Connection conn) throws SQLException {
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
    
    public static Envelope2D getBound(final String dbName) {
        try (Connection conn = getConnection(dbName)) {
            return getBound(conn);
        } catch(SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }
    /*
    public static Envelope2D getBound(final Connection conn) {
        return new Envelope2D(new DirectPosition2D(-0.565796, 51.246444), new DirectPosition2D(0.302124, 51.718521));
    }
    public static Envelope2D getBound(final String dbName) {
        return new Envelope2D(new DirectPosition2D(-0.565796, 51.246444), new DirectPosition2D(0.302124, 51.718521));
    }
    */   
    public static void createTiles(final String dbName) throws SQLException {
        List<Pair<String, Polygon>> tiles = new LinkedList<>();
        List<Pair<Integer, Geometry>> ways = new LinkedList<>();
        try (Connection conn = getConnection(dbName);
            Statement st = conn.createStatement();
            PreparedStatement pst = conn.prepareStatement("INSERT INTO tiles(qkey, lon1, lat1, lon2, lat2, shape) VALUES(?, ?, ?, ?, ?, ST_SetSRID(ST_MakeBox2D(ST_Point(?, ?), ST_Point(?, ?)), 4326));")) {
            TileSystem tileSystem = getTileSystem(dbName);
            Enumeration e = tileSystem.getTreeEnumeration();
            st.execute("TRUNCATE TABLE tiles; TRUNCATE TABLE ways_tiles;");
            
            logger.log(Level.INFO, "Saving tiles ...");
            while(e.hasMoreElements()) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
                Tile tile = (Tile) node.getUserObject();
                if(tile == null || node.isRoot()) continue;
                TreeNode[] path = node.getPath();
                String qkey = "";
                for(int i = 1; i < path.length; i ++)
                    qkey += path[i-1].getIndex(path[i]);

                if(qkey.length() >= MIN_SCALE && qkey.length() <= MAX_SCALE)
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
                //if(qkey.length() > 13)
                pst.addBatch();
            }
            pst.executeBatch();
            //st.execute("DELETE FROM tiles WHERE qkey='';"); // removing the root node
            logger.log(Level.INFO, "Binding ways-tiles ...");
            
            WKTReader reader = new WKTReader();
            
            try(ResultSet rs = st.executeQuery("SELECT gid, st_astext(the_geom) AS geometry FROM ways;")) {
                while(rs.next())
                    ways.add(new Pair(rs.getInt("gid"), reader.read(rs.getString("geometry"))));
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
                System.exit(0);
            }
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
                    logger.log(Level.SEVERE, null, ex);
                }
            }

        }

        int start, amount = tiles.size() / POOL_SIZE;
        if(amount == 0) amount = 1;
        for(start = 0; start+amount < tiles.size(); start += amount) {
            Task task = new Task(dbName, tiles.subList(start, start+amount), ways);
            pool.execute(task);
        }
        Task task = new Task(dbName, tiles.subList(start, tiles.size()), ways);
        pool.execute(task);
        
        //pool.shutdown();
        /*try {
            pool.awaitTermination(1, TimeUnit.DAYS);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        doSpeed(dbName);*/
    }
    
    /**
     * Determina la velocità max all'interno di ciascun tile come max delle strade che lo intersecano
     */
    public static void doSpeed(final String dbName) {
        try (Connection conn = getConnection(dbName);
            Statement st = conn.createStatement(); PreparedStatement pst = conn.prepareStatement("update tiles set max_speed=? where qkey=?")) {
                ResultSet rs; rs = st.executeQuery("select max(freeflow_speed), tiles_qkey from ways_tiles join ways on gid = ways_id group by tiles_qkey;");
            while(rs.next()) {
                pst.clearParameters();
                pst.setDouble(1, rs.getInt(1));
                pst.setString(2, rs.getString(2));
                pst.addBatch();
            }
            rs.close();
            pst.executeBatch();
        } catch(SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
     
    /*
    public static void createTiles1(final String dbName) throws SQLException {
        List<Pair<String, Polygon>> tiles = new LinkedList<>();
        List<Pair<Integer, Geometry>> ways = new LinkedList<>();
        List<Integer> speeds = new ArrayList<>();
        try (Connection conn = getConnection(dbName);
            Statement st = conn.createStatement();
            PreparedStatement pst = conn.prepareStatement("INSERT INTO tiles(qkey, lon1, lat1, lon2, lat2, shape) VALUES(?, ?, ?, ?, ?, ST_SetSRID(ST_MakeBox2D(ST_Point(?, ?), ST_Point(?, ?)), 4326));")) {
            TileSystem tileSystem = getTileSystem(dbName);
            Enumeration e = tileSystem.getTreeEnumeration();
            st.execute("TRUNCATE TABLE tiles; TRUNCATE TABLE ways_tiles;");
            
            logger.log(Level.INFO, "Saving tiles ...");
            while(e.hasMoreElements()) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
                Tile tile = (Tile) node.getUserObject();
                if(tile == null || node.isRoot()) continue;
                TreeNode[] path = node.getPath();
                String qkey = "";
                for(int i = 1; i < path.length; i ++)
                    qkey += path[i-1].getIndex(path[i]);

                if(qkey.length() >= MIN_SCALE && qkey.length() <= MAX_SCALE)
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
                //if(qkey.length() > 13)
                pst.addBatch();
            }
            pst.executeBatch();
            //st.execute("DELETE FROM tiles WHERE qkey='';"); // removing the root node
            logger.log(Level.INFO, "Binding ways-tiles ...");
            
            WKTReader reader = new WKTReader();
            
            try(ResultSet rs = st.executeQuery("SELECT gid, st_astext(the_geom) AS geometry, freeflow_speed FROM ways;")) {
                while(rs.next()) {
                    ways.add(new Pair(rs.getInt("gid"), reader.read(rs.getString("geometry"))));
                    speeds.add(rs.getInt("freeflow_speed"));
                }
            } catch (ParseException ex) {
                logger.log(Level.SEVERE, null, ex);
                System.exit(0);
            }
        }
        
        class Task implements Runnable {

            private final List<Pair<String, Polygon>> tiles;
            private final List<Pair<Integer, Geometry>> ways;
            private final Integer[] speeds;
            private final String dbName;

            public Task(String dbName, List<Pair<String, Polygon>> tiles, List<Pair<Integer, Geometry>> ways, Integer[] speeds) {
                this.dbName = dbName;
                this.tiles = tiles;
                this.ways = ways;
                this.speeds = speeds;
            }

            @Override
            public void run() {
                try(Connection conn = Main.getConnection(dbName); 
                    PreparedStatement st = conn.prepareStatement("INSERT INTO ways_tiles(tiles_qkey, ways_id) VALUES(?, ?);");
                        PreparedStatement st1 = conn.prepareStatement("UPDATE tiles SET max_speed = ? WHERE qkey = ?;")
                        ) {
                    for(Pair<String, Polygon> tile: tiles) {
                        int maxSpeed = 0;
                        for(int i = 0; i < ways.size(); i ++) {
                            Pair<Integer, Geometry> way = ways.get(i);
                            if(tile.getValue().intersects(way.getValue())) {
                                st.clearParameters();
                                st.setString(1, tile.getKey());
                                st.setInt(2, way.getKey());
                                st.addBatch();
                                if(speeds[i] > maxSpeed) maxSpeed = speeds[i];
                            }
                        }
                        st1.clearParameters();
                        st1.setInt(1, maxSpeed);
                        st1.setString(2, tile.getKey());
                        st1.addBatch();
                    }
                    st.executeBatch();
                    st1.executeBatch();
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }

        }

        int start, amount = tiles.size() / POOL_SIZE;
        if(amount == 0) amount = 1;
        for(start = 0; start+amount < tiles.size(); start += amount) {
            Task task = new Task(dbName, tiles.subList(start, start+amount), ways, speeds.toArray(new Integer[speeds.size()]));
            pool.execute(task);
        }
        Task task = new Task(dbName, tiles.subList(start, tiles.size()), ways, speeds.toArray(new Integer[speeds.size()]));
        pool.execute(task);

        //st.execute("SELECT my_ways_tiles_fill();"); // call it to fill the relation between tiles and ways
    }
      */
    public static void threadedSubgraph(final String db) throws Exception {
        TileSystem tileSystem = getTileSystem(db);
        
        for(int i = MIN_SCALE; i <= MAX_SCALE; i ++)
            pool.execute(new TasksHelper(tileSystem, db, i));//new SubgraphTask(tileSystem, db, i));
    }
    
    /**
     * Get a TileSystem with the max_speed tile info
     * @param db
     * @return
     * @throws SQLException 
     */
    public static TileSystem getFullTileSystem(final String db) throws SQLException {
        try(Connection conn = getConnection(db)) {
            Envelope2D bound = getBound(conn);
            TileSystem tileSystem = new TileSystem(bound, MAX_SCALE);
            tileSystem.computeTree();
            try (Statement st = conn.createStatement(); 
                    ResultSet rs = st.executeQuery("SELECT qkey, max_speed FROM tiles WHERE max_speed IS NOT NULL")) {
                while(rs.next()) {
                    Tile tile = tileSystem.getTile(rs.getString("qkey"));
                    double maxSpeed = rs.getDouble("max_speed");
                    if(tile != null)
                        tile.setUserObject(maxSpeed);
                }
            }
            return tileSystem;
        }
    }
}
