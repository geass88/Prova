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
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tommaso
 */
public class OstacoliManuali {
 
    private static final String start[];
    private static final String end[];
    private static final String obstacles[] = {
        //"12023222112103", "12023222111220", "12023222112131", "12023222113102", "12023222113200", "12023222113013", "12023222112122", "12023222112121", "12023222113202", "12023222111223", "12023222113100", "12023222113101", "12023222111231", "12023222111320", "12023222111212", "12023222111213", "12023222111201", "12023222111210", "12023222110301", "12023222110310" // roma
        //"12020213220302", "12020213220312", "12020213220332", "12020213220331", "12020213222013", "12020213220322", "12020213222120", "12020213222110", "12020213222301", "12020213222113", "12020213223202", "12020213223023", "12020213222302", "12020213222310" // brussel
        /*"12022001101202", "12022001101122", "12022001101230", "12022001101031", "12022001103013", "12022001103101", "12022001101330", "12022001101311", "12022001102113", "12022001103001", "12022001102113", "12022001101222" //parigi
        
        ,"12022001100313", "12022001101100", "12022001102130", "12022001101023" // 90km/h parigi*/
        "033111012101310", "033111010332221", "033111012101313", "033111010332200", "033111010323320", "033111010332202", "033111012110120", "033111012110111", "033111010332231", "033111010332302", "033111012111023", "033111012111130", "033111012111122", "033111012111112", "033111012103003", "033111012101232", "033111012101232", "033111012101231", "033111012101231", "033111012101302" // madrid
        
    };
    private static final String db = Main.DBS[0];
    private static final Logger logger = Logger.getLogger(OstacoliManuali.class.getName());

    static {
        start = new String[obstacles.length/2];
        end = new String[obstacles.length/2];
        for(int i = 0; i < obstacles.length; i ++) {
            if(i % 2 == 0)
                start[i/2] = obstacles[i];
            else 
                end[i/2] = obstacles[i];
        }
    }
    
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
                    st.setDouble(5, 50./120.);
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
        int POINTS_COUNT = 1000;
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
    
    public void pair1() {
        try (Connection conn = Main.getConnection(db); Statement stm = conn.createStatement()) {
                stm.executeUpdate("TRUNCATE TABLE pair;");
        } catch (Exception e) {e.printStackTrace();}
        for(int j = 0; j < start.length; j ++) {
            Set<String> qkeys = new HashSet<>();
            qkeys.addAll(Arrays.asList(CellOverlay.listQkeys(start[j], end[j])));
            System.out.println("selected cells number: "+qkeys.size());
            List<Integer> nodes = P2PQueryInTilesGenerator.helper(qkeys.toArray(new String[qkeys.size()]));
            int POINTS_COUNT = 1000/start.length;
            try (Connection conn = Main.getConnection(db);) {
                //stm.executeUpdate("TRUNCATE TABLE pair;");
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
    }
    
    
    public static void main(String[] args) {
        //new OstacoliManuali().obstacles();
        new OstacoliManuali().pair();
    }
    
}
