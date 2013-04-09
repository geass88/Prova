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

import java.sql.*;
import java.util.Enumeration;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;

/**
 * Preprocessing
 * @author Tommaso
 */
public class Main {
    
    public static final String JDBC_URI = "jdbc:postgresql://192.168.128.128:5432/";
    public static final Integer MAX_SCALE = 17;
    
    
    public static void main(String[] args) throws SQLException {
        String dbs[] = { "berlin_routing", "hamburg_routing", "london_routing"};
        
        for(String dbName: dbs) {
            try (Connection conn = DriverManager.getConnection(JDBC_URI + dbName, "postgres", "postgres"); 
                Statement st = conn.createStatement();
                PreparedStatement pst = conn.prepareStatement("INSERT INTO tiles(qkey, lon1, lat1, lon2, lat2, shape) VALUES(?, ?, ?, ?, ?, ST_SetSRID(ST_MakeBox2D(ST_Point(?, ?), ST_Point(?, ?)), 4326));")) {
                ResultSet rs;
                rs = st.executeQuery("SELECT MIN(x1), MIN(x2), MIN(y1), MIN(y2), MAX(x1), MAX(x2), MAX(y1), MAX(y2) FROM ways");
                rs.next();
                Envelope2D bound = new Envelope2D(
                    new DirectPosition2D(Math.min(rs.getDouble(1), rs.getDouble(2)), Math.min(rs.getDouble(3), rs.getDouble(4))), 
                    new DirectPosition2D(Math.max(rs.getDouble(5), rs.getDouble(6)), Math.max(rs.getDouble(7), rs.getDouble(8)))
                );
                rs.close();

                TileSystem calc = new TileSystem(bound, MAX_SCALE);
                calc.computeTree();
                Enumeration e = calc.getTreeEnumeration();
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
                // st.execute("SELECT my_ways_tiles_fill();"); // call it to fill the relation between tiles and ways
            }
        }
    }
    
}
