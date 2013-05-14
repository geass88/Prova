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

import com.graphhopper.util.shapes.GHPlace;
import com.mycompany.tesi.beans.Obstacle;
import com.mycompany.tesi.utils.ObstacleCreator;
import com.mycompany.tesi.utils.TileSystem;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tommaso
 */
public class ObstacleBuilder {
    
    private static final Logger logger = Logger.getLogger(ObstacleBuilder.class.getName());
    
    public static void main(String[] args) throws Exception {
        final String[] FILES = { "BerlinSourceTarget", "HamburgSourceTarget", "London_Source_Target" };
        final String table = "ways";
        for(int i = 0; i < Main.DBS.length; i ++) {
            String db = Main.DBS[i];
            Map<Integer, GHPlace> nodes = new HashMap<>();
            try(Connection conn = Main.getConnection(db);
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select * from ((select distinct source, y1, x1 from " + table + ") union (select distinct target, y2, x2 from " + table + ")) nodes order by source")) {
                while(rs.next())
                    nodes.put(rs.getInt(1), new GHPlace(rs.getDouble(2), rs.getDouble(3)));
            }
            
            TileSystem tileSystem = Main.getFullTileSystem(db);
            ObstacleCreator[] creators = { new ObstacleCreator(tileSystem, true), new ObstacleCreator(tileSystem, false) };
            File file = new File("data/" + FILES[i]);
            if(!file.exists()) {
                logger.log(Level.SEVERE, "The file {0} doesn't exists", FILES[i]);
                System.exit(1);
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(file));
                    Connection conn = Main.getConnection(db); 
                    PreparedStatement st = conn.prepareStatement("INSERT INTO obstacles(id, source, target, obst_id, x1, y1, x2, y2, alpha, scale_grain, obst_type, etime) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);")) {
                String s; reader.readLine(); // skip the first row
                int serial = 1;
                int obst_id = 1;
                while((s=reader.readLine()) != null) {
                    String[] tokens = s.split(",");
                    Integer source = Integer.valueOf(tokens[0]);
                    Integer target = Integer.valueOf(tokens[1]);
                    GHPlace start = nodes.get(source);
                    GHPlace end = nodes.get(target);
                    for(int j = 0; j < creators.length; j ++) {
                        long time1 = System.nanoTime();
                        Obstacle obstacle = creators[j].getObstacle(start, end);
                        long time2 = System.nanoTime();
                        if(obstacle != null) {
                            st.clearParameters();
                            st.setInt(1, serial ++);
                            st.setInt(2, source);
                            st.setInt(3, target);
                            st.setInt(4, obst_id);
                            st.setDouble(5, obstacle.getRect().getLowerCorner().x);
                            st.setDouble(6, obstacle.getRect().getLowerCorner().y);
                            st.setDouble(7, obstacle.getRect().getUpperCorner().x);
                            st.setDouble(8, obstacle.getRect().getUpperCorner().y);
                            st.setDouble(9, obstacle.getAlpha());
                            st.setInt(10, obstacle.getGrainScale());
                            st.setInt(11, j);
                            st.setLong(12, (time2-time1)/1000);
                            st.addBatch();
                        }
                    }
                    obst_id ++;
                }
                st.executeBatch();
            }
        }
    }
    
}
