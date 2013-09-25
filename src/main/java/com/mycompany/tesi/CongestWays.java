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

import java.io.IOException;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tommaso
 */
public class CongestWays {
    
    //private static final int MAX_SPEED = 90;
    //private static final int MAX_SPEED = 70;
    private static final int MAX_SPEED = 60;
    private static final Logger logger = Logger.getLogger(CongestWays.class.getName());
    
    public static void main(String[] args) {
        CongestWays instance = new CongestWays();
        System.out.println("Have you restored the ways table?");
        try {
            System.in.read();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        //instance.backup();
        //instance.doAll();
        instance.doAll1();
    }
    /*
    private void backup() {
        try(Connection conn = Main.getConnection(Main.DBS[0]);
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT gid, freeflow_speed FROM ways;");
            PrintWriter fout = new PrintWriter("ways_restore.sql")
        ) {
            while(rs.next()) {
                String s = String.format("UPDATE ways SET freeflow_speed=%d WHERE gid=%d;", rs.getInt(1), rs.getInt(2));
                fout.println(s);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }*/
    
    private void doAll() {
        System.out.println("Congest speed ..." + MAX_SPEED);
        try(Connection conn = Main.getConnection(Main.DBS[0]);
            PreparedStatement pst = conn.prepareStatement("update ways set freeflow_speed=? where freeflow_speed>?")
        ) {
            pst.setInt(1, MAX_SPEED);
            pst.setInt(2, MAX_SPEED);
            pst.executeUpdate();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    private void doAll1() {
        System.out.println("Congest distance ..." + MAX_SPEED);
        try(Connection conn = Main.getConnection(Main.DBS[0]);
            PreparedStatement pst = conn.prepareStatement("update ways set km=1000 where freeflow_speed>?")
        ) {
            pst.setInt(1, MAX_SPEED);
            pst.executeUpdate();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}

