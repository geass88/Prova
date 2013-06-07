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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tommaso
 */
public class OstacoliManuali {
 
    private static final String start[] = { "122011003112123",  "122011003112211", "122011003112300", "122011003112303" };
    private static final String end[] = { "122011003113000", "122011003112123", "122011003112121", "122011003112312"  } ;
    private static final String db = Main.DBS[0];
    private static final Logger logger = Logger.getLogger(OstacoliManuali.class.getName());

    public void obstacles() {
        try (Connection conn = Main.getConnection(db);
                Statement s = conn.createStatement();
                PreparedStatement st = conn.prepareStatement("INSERT INTO ostacoli(x1, y1, x2, y2, alpha, scale_grain, etime) VALUES(?, ?, ?, ?, ?, ?, ?);");
                PreparedStatement st1 = conn.prepareStatement("SELECT lon1, lat1 FROM tiles WHERE qkey=?");
                PreparedStatement st2 = conn.prepareStatement("SELECT lon2, lat2 FROM tiles WHERE qkey=?");
                ){
            s.executeUpdate("TRUNCATE TABLE ostacoli;");
            for(int i = 0;  i< start.length; i++) {
                st.clearParameters();
                st1.clearParameters();
                st2.clearParameters();
                
                st1.setString(1, start[i]);
                st2.setString(1, end[i]);
                ResultSet rs1 = st1.executeQuery();
                ResultSet rs2 = st2.executeQuery();
                if(rs1.next() && rs2.next()) {
                    st.setDouble(1, rs1.getDouble(1));
                    st.setDouble(2, rs1.getDouble(2));
                    st.setDouble(3, rs2.getDouble(1));
                    st.setDouble(4, rs2.getDouble(2));
                    st.setDouble(5, 0.5555);
                    st.setInt(6, 15);
                    st.setInt(7, 0);
                }
                st.executeUpdate();
            }
        } catch(SQLException e) {
            System.err.println(e);
        }
    }
    
    public void pair() {
        Set<String> qkeys = new HashSet<>();
        for(int i = 0; i < start.length; i++)
            qkeys.addAll(Arrays.asList(CellOverlay.listQkeys(start[i], end[i])));
        System.out.println("selected cells number: "+qkeys.size());
        List<Integer> nodes = P2PQueryInTilesGenerator.helper(qkeys.toArray(new String[qkeys.size()]));
        int POINTS_COUNT = 200;
        try (Connection conn = Main.getConnection(db); Statement stm = conn.createStatement()) {
            stm.executeUpdate("TRUNCATE TABLE pair;");
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
            }
        } catch(SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    
    public static void main(String[] args) {
        //new OstacoliLecce().obstacles();
        new OstacoliManuali().pair();
    }
    
}
