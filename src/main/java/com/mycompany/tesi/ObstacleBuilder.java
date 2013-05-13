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

import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.shapes.GHPlace;
import com.mycompany.tesi.beans.Obstacle;
import com.mycompany.tesi.hooks.MyCarFlagEncoder;
import com.mycompany.tesi.hooks.RawEncoder;
import com.mycompany.tesi.utils.GraphHelper;
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
import org.geotoolkit.geometry.Envelope2D;

/**
 *
 * @author Tommaso
 */
public class ObstacleBuilder {
    
    private static Logger logger = Logger.getLogger(ObstacleBuilder.class.getName());
    
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
            ObstacleCreator creator = new ObstacleCreator(tileSystem, false);
            File file = new File("data/" + FILES[i]);
            if(!file.exists()) {
                logger.log(Level.SEVERE, "The file {0} doesn't exists", FILES[i]);
                System.exit(1);
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(file));
                    Connection conn = Main.getConnection(db); 
                    PreparedStatement st = conn.prepareStatement("INSERT INTO table(id, x1, y1, x2, y2, alpha, type, time) VALUES(?, ?, ?, ?, ?, ?, ?, ?);")) {
                String s; reader.readLine();
                int serial = 1;
                while((s=reader.readLine()) != null) {
                    String[] tokens = s.split(",");
                    Integer source = Integer.valueOf(tokens[0]);
                    Integer target = Integer.valueOf(tokens[1]);
                    GHPlace start = nodes.get(source);
                    GHPlace end = nodes.get(target);
                    long time1 = System.nanoTime();
                    Obstacle obstacle = creator.getObstacle(start, end, 17);
                    long time2 = System.nanoTime();
                    if(obstacle != null) {
                        st.clearParameters();
                        st.setInt(1, serial);
                        st.setDouble(2, obstacle.getRect().getLowerCorner().x);
                        st.setDouble(3, obstacle.getRect().getLowerCorner().y);
                        st.setDouble(4, obstacle.getRect().getUpperCorner().x);
                        st.setDouble(5, obstacle.getRect().getUpperCorner().y);
                        st.setDouble(6, obstacle.getAlpha());
                        st.setInt(7, obstacle.getGrainScale());
                        st.setLong(8, (time2-time1)/1000);
                        st.addBatch();
                    }
                    serial ++;
                }
                st.executeBatch();
            }
        }
    }
    
}