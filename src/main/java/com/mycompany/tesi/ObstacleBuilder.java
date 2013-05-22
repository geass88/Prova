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
import com.mycompany.tesi.obstacles.ObstacleCreator;
import com.mycompany.tesi.utils.GraphHelper;
import com.mycompany.tesi.obstacles.ObstacleCreatorNew;
import com.mycompany.tesi.utils.TileSystem;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotoolkit.geometry.Envelope2D;

/**
 *
 * @author Tommaso
 */
public class ObstacleBuilder {
    
    private static final Logger logger = Logger.getLogger(ObstacleBuilder.class.getName());
    private static final ThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(5);
    //public static final String[] FILES = { "BerlinSourceTarget", "HamburgSourceTarget", "London_Source_Target" };
    public static final String[] FILES = { "EnglandSourceTarget" };
    public static final String TABLE = "ways";    
    
    public static void main(String[] args) throws Exception {        
        for(int i = 0; i < Main.DBS.length; i ++) {
            String db = Main.DBS[i];
            Map<Integer, GHPlace> nodes = GraphHelper.readNodes(db, TABLE);
            
            TileSystem tileSystem = Main.getFullTileSystem(db);
            File file = new File("data/" + FILES[i]);
            if(!file.exists()) {
                logger.log(Level.SEVERE, "The file {0} doesn't exists", FILES[i]);
                System.exit(1);
            }
            List<String> queries = new LinkedList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String s; reader.readLine(); // skip the first row
                int obst_id = 1;
                while((s=reader.readLine()) != null) {
                    queries.add(obst_id + "," + s); obst_id ++;
                }
            }
            int start;
            int amount = 100;
            for(start = 0; start+amount < queries.size(); start += amount) {
                Task task = new Task(db, nodes, queries.subList(start, start+amount), tileSystem);
                pool.execute(task);
            }
            pool.execute(new Task(db, nodes, queries.subList(start, queries.size()), tileSystem));
        }
        pool.shutdown();
    }
    
}

class Task implements Runnable {
    private final String db;
    private final Map<Integer, GHPlace> nodes;
    private final List<String> queries;
    private final TileSystem tileSystem;

    public Task(final String db, final Map<Integer, GHPlace> nodes, final List<String> queries, final TileSystem tileSystem) {
        this.queries = queries;
        this.nodes = nodes;
        this.db = db;
        this.tileSystem = tileSystem;
    }

    @Override
    public void run() {
        try (Connection conn = Main.getConnection(db); 
                PreparedStatement st = conn.prepareStatement("INSERT INTO obstacles(source, target, obst_id, x1, y1, x2, y2, alpha, scale_grain, obst_type, etime, max_area) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);")) {
            ObstacleCreator[] creators = { /*new ObstacleCreatorNew(tileSystem, true, .7, 100, 13),*/ new ObstacleCreatorNew(tileSystem, null, .7, 100, db, 12) };
            for(String s: queries) {
                String[] tokens = s.split(",");
                Integer obst_id = Integer.valueOf(tokens[0]);
                Integer source = Integer.valueOf(tokens[1]);
                Integer target = Integer.valueOf(tokens[2]);
                GHPlace start = nodes.get(source);
                GHPlace end = nodes.get(target);
                for(int j = 0; j < creators.length; j ++) {
                    long time1 = System.nanoTime();
                    Obstacle obstacle = creators[j].getObstacle(start, end);
                    long time2 = System.nanoTime();

                    st.clearParameters();
                    st.setInt(1, source);
                    st.setInt(2, target);
                    st.setInt(3, obst_id);
                    if(obstacle != null && obstacle.getRect() != null) {
                        Envelope2D rect = creators[j].extractEnvelope(obstacle);
                        st.setDouble(4, rect.getLowerCorner().x);
                        st.setDouble(5, rect.getLowerCorner().y);
                        st.setDouble(6, rect.getUpperCorner().x);
                        st.setDouble(7, rect.getUpperCorner().y);
                        st.setDouble(8, obstacle.getAlpha());
                        st.setInt(9, obstacle.getGrainScale());
                    } else {
                        st.setNull(4, Types.DOUBLE);
                        st.setNull(5, Types.DOUBLE);
                        st.setNull(6, Types.DOUBLE);
                        st.setNull(7, Types.DOUBLE);
                        st.setNull(8, Types.DOUBLE);
                        st.setNull(9, Types.INTEGER);
                    }
                    st.setInt(10, j);
                    st.setInt(11, (int)((time2-time1)/1000));
                    st.setInt(12, creators[j].getMaxRectArea());
                    st.addBatch();
                }
            }
            st.executeBatch();
        } catch (SQLException ex) {
            Logger.getLogger(Task.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
