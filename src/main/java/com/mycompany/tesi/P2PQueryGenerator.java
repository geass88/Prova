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
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tommaso
 */
public class P2PQueryGenerator {
 
    private static final String db = Main.DBS[0];
    private static final String table = "overlay_14";
    private final static Logger logger = Logger.getLogger(P2PQueryGenerator.class.getName());
    
    public static void main(String[] args) {
        final List<Integer> nodes = new ArrayList<>();
        Connection conn = Main.getConnection(db); 
        try(Statement st = conn.createStatement()) {
            st.executeUpdate("TRUNCATE TABLE pair;");
            ResultSet rs; rs = st.executeQuery("SELECT DISTINCT source FROM " + table + " UNION SELECT DISTINCT target FROM " + table + ";");
            while(rs.next())
                nodes.add(rs.getInt(1));
            rs.close();
        } catch(SQLException e) {
            e.printStackTrace();
        }
       // RawEncoder vehicle = new MyCarFlagEncoder(SubgraphTask.MAX_SPEED);
        //Graph graph = GraphHelper.readGraph(db, "overlay_12", vehicle);
        
        int POINTS_COUNT = 1000;
        for(int i = 0; i < POINTS_COUNT; ) {
            int source = (int)Math.round(Math.random()*(nodes.size()-1));
            int target = (int)Math.round(Math.random()*(nodes.size()-1));
            
            if(source == target) continue;
            
            try(PreparedStatement st = conn.prepareStatement("INSERT INTO pair(source, target) VALUES(?, ?);")) {
                st.setInt(1, nodes.get(source));
                st.setInt(2, nodes.get(target));
                st.executeUpdate();
                /*RoutingAlgorithm algo = new AlgorithmPreparation(vehicle).graph(graph).createAlgo();
                Path path = algo.calcPath(source, target);
                if(! path.found()) {
                    PreparedStatement st1 = conn.prepareStatement("DELETE FROM pair WHERE source = ? and target = ?");
                    st1.setInt(1, source);
                    st1.setInt(2, target);
                    st1.executeUpdate();
                    st1.close();
                    continue;
                }*/
                i ++;
            } catch(SQLException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            
                //ids.add(a.get(value));
        }
        try {
            conn.close();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
}
