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

import com.graphhopper.routing.util.AcceptWay;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.WKTReader;
import java.sql.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;
import org.geotoolkit.geometry.jts.JTS;

/**
 * Preprocessing: creazione e salvataggio tiles.
 * @author Tommaso
 */
public class Main {
    
    public static final String JDBC_URI = "jdbc:postgresql://192.168.128.128:5432/";
    public static final String[] DBS = { "berlin_routing", "hamburg_routing", "london_routing"};
    public static final Integer MAX_SCALE = 17;
    
    
    public static void main(String[] args) throws Exception {
        for(String dbName: DBS) {
            System.out.println("Processing db " + dbName + " ...");
            try (Connection conn = DriverManager.getConnection(JDBC_URI + dbName, "postgres", "postgres")) {
                subgraph(conn);
            }
        }
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
    
    public static void create_tiles(Connection conn) throws SQLException {        
        try (Statement st = conn.createStatement();
            PreparedStatement pst = conn.prepareStatement("INSERT INTO tiles(qkey, lon1, lat1, lon2, lat2, shape) VALUES(?, ?, ?, ?, ?, ST_SetSRID(ST_MakeBox2D(ST_Point(?, ?), ST_Point(?, ?)), 4326));")) {
            Envelope2D bound = getBound(conn);
            TileSystem tileSystem = new TileSystem(bound, MAX_SCALE);
            tileSystem.computeTree();
            Enumeration e = tileSystem.getTreeEnumeration();
            st.execute("TRUNCATE TABLE tiles;");
            while(e.hasMoreElements()) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
                Tile tile = (Tile) node.getUserObject();
                if(tile == null) continue;
                TreeNode[] path = node.getPath();
                String qkey = "";
                for(int i = 1; i < path.length; i ++)
                    qkey += path[i-1].getIndex(path[i]);

                pst.setString(1, qkey);
                pst.setDouble(2, tile.getRect().getMinX());
                pst.setDouble(3, tile.getRect().getMinY());
                pst.setDouble(4, tile.getRect().getMaxX());
                pst.setDouble(5, tile.getRect().getMaxY());
                pst.setDouble(6, tile.getRect().getMinX());
                pst.setDouble(7, tile.getRect().getMinY());
                pst.setDouble(8, tile.getRect().getMaxX());
                pst.setDouble(9, tile.getRect().getMaxY());
                pst.executeUpdate();
                pst.clearParameters();
            }
            st.execute("DELETE FROM tiles WHERE qkey='';"); // removing the root node
            st.execute("SELECT my_ways_tiles_fill();"); // call it to fill the relation between tiles and ways
        }
    }
    
    public static void subgraph(Connection conn) throws Exception {
        Envelope2D bound = getBound(conn);
        TileSystem tileSystem = new TileSystem(bound, MAX_SCALE);
        tileSystem.computeTree();
        WKTReader reader = new WKTReader();
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        
        /*LineString l1=factory.createLineString(new Coordinate[]{new Coordinate(0,0),new Coordinate(0,50)});
        LineString l2=factory.createLineString(new Coordinate[]{new Coordinate(-10,10),new Coordinate(10,10)});
        org.geotoolkit.geometry.jts.JTS.convertToCRS(l2, DefaultCRS.projectedCRS);
        System.out.println(l1.intersection(l2).toText());*/
        String sql = "SELECT ways.source, ways.target, ways.freeflow_speed, ways.length, ways.reverse_cost=1000000 AS oneway, ways.km, ways.x1, ways.y1, ways.x2, ways.y2, st_astext(ways.the_geom) AS geometry, st_contains(shape, the_geom) AS contained " +
            "FROM ways JOIN ways_tiles ON gid = ways_id JOIN tiles ON tiles_qkey = qkey WHERE qkey = ?";
        try (Statement st1 = conn.createStatement(); 
            ResultSet rs1 = st1.executeQuery("SELECT DISTINCT tiles_qkey FROM ways_tiles WHERE length(tiles_qkey)>13 ORDER BY tiles_qkey");
            PreparedStatement st2 = conn.prepareStatement(sql)) {
            while(rs1.next()) {
                String qkey = rs1.getString(1);
                Polygon rect = tileSystem.getTile(qkey).getPolygon();
                LineString ring = rect.getExteriorRing();
                GraphStorage graph = new GraphBuilder().create();
                Map<Integer, Integer> nodes = new HashMap<>();
                int count = 0;
                //System.out.println(g.getExteriorRing().toText());
                
                st2.setString(1, qkey);
                ResultSet rs2 = st2.executeQuery();
                while(rs2.next()) {
                    if(rs2.getBoolean("contained")) {
                        Integer s = nodes.get(rs2.getInt("source"));
                        if(s == null) {
                            nodes.put(s=count, rs2.getInt("source")); 
                            graph.setNode(s, rs2.getDouble("y1"), rs2.getDouble("x1"));
                            count++;
                        }
                        Integer t = nodes.get(rs2.getInt("target"));
                        if(t == null) {
                            nodes.put(t=count, rs2.getInt("target")); 
                            graph.setNode(t, rs2.getDouble("y2"), rs2.getDouble("x2"));
                            count++;
                        }
                        Map<String, Object> p = new HashMap<>();
                        p.put("caroneway", rs2.getBoolean("oneway"));
                        p.put("car", rs2.getInt("freeflow_speed"));
                        int flags = new AcceptWay(true, true, true).toFlags(p);
                        EdgeIterator i = graph.edge(s, t, rs2.getDouble("km")*1000., flags);
                        Geometry geometry = reader.read(rs2.getString("geometry"));
                        PointList pillarNodes = getPillars(geometry);
                        if(pillarNodes != null) 
                            i.wayGeometry(pillarNodes);
                        
                    } else {

                    }
                }
                rs2.close();
                st2.clearParameters();
            }
        }
    }
    
    static PointList getPillars(Geometry g) {
        int pillarNumber = g.getNumPoints() - 2;
        if(pillarNumber > 0) {
            PointList pillarNodes = new PointList(pillarNumber);
            for(int i = 1; i <= pillarNumber; i ++) {
                pillarNodes.add(g.getCoordinates()[i].getOrdinate(1), g.getCoordinates()[i].getOrdinate(0));
                //System.out.println("lat=" + g.getCoordinates()[i].getOrdinate(1) +" lon = "+ g.getCoordinates()[i].getOrdinate(0));
            }
            return pillarNodes;
        }
        return null;
    }
    
    /*
     --select distinct tiles_qkey from ways_tiles where length(tiles_qkey)>16 order by tiles_qkey
        SELECT st_astext(st_intersection(st_setsrid(st_makeline(array[
            st_point(lon1,lat1), st_point(lon2,lat1), st_point(lon2,lat2), st_point(lon1,lat2), st_point(lon1,lat1)
        ]), 4326), the_geom)), *
        FROM ways JOIN ways_tiles ON gid=ways_id JOIN tiles ON tiles_qkey=qkey 
        WHERE tiles_qkey='120210232303' and not st_contains(shape, the_geom)
     
     */
    
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
