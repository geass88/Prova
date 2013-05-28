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

import com.mycompany.tesi.utils.TileSystem;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tommaso
 */
public class CongestTile {
    
    private final static String dbName = "england_routing";
    private final static String[] qkeys = { 
        "031313131011", "031313131100", "031313131101", "031313131110", "031313131111", "120202020000", "120202020001"
        //"031313131011", "031313131100", "031313131101", "031313131110", "031313131111", "120202020000", "031313131013", "031313131102", "031313131103", "031313131112", "031313131113", "120202020002", "031313131031", "031313131120", "031313131121", "031313131130", "031313131131", "120202020020", "031313131033", "031313131122", "031313131123", "031313131132", "031313131133", "120202020022", "031313131211", "031313131300", "031313131301", "031313131310", "031313131311", "120202020200", "120202020001", "120202020003", "120202020021", "120202020023", "120202020201" 
    };
    private final static int maxSpeed = 20;
    private final static int scale = 12;
    private final static String fileName = "restore_2013_5_27_18_45_33.sql";
    private final static boolean CONGEST = true;
    private final static Logger logger = Logger.getLogger(CongestTile.class.getName());
    
    public static void main(String[] args) throws Exception {
        if(CONGEST) congest();
        else restore();
    }
    
    private static void congest() throws Exception {
        try(Connection conn = Main.getConnection(dbName)) {
            System.out.println("Preparing ...");
            List<Integer> waysIds = new LinkedList<>();
            List<Integer> oldSpeeds = new LinkedList<>();
            String sql = "SELECT DISTINCT ways_id, freeflow_speed FROM ways_tiles JOIN ways ON gid=ways_id WHERE tiles_qkey=ANY(?) AND freeflow_speed > ?;";
            try(PreparedStatement pst = conn.prepareStatement(sql)) {
                pst.setArray(1, conn.createArrayOf("varchar", qkeys));
                pst.setInt(2, maxSpeed);
                ResultSet rs; rs = pst.executeQuery();
                while(rs.next()) {
                    waysIds.add(rs.getInt(1));
                    oldSpeeds.add(rs.getInt(2));
                }
                rs.close();
            }
            List<String> updatableQkeys = new LinkedList<>();
            //sql = "SELECT DISTINCT tiles_qkey FROM ways_tiles WHERE ways_id=ANY(?);";
            sql = "SELECT DISTINCT tiles_qkey FROM ways_tiles WHERE ways_id=ANY(?) AND length(tiles_qkey)=?;";
            try(PreparedStatement pst = conn.prepareStatement(sql)) {
                pst.setArray(1, conn.createArrayOf("int", waysIds.toArray(new Integer[waysIds.size()])));
                pst.setInt(2, scale);
                ResultSet rs; rs = pst.executeQuery();
                while(rs.next()) {
                    updatableQkeys.add(rs.getString(1));
                }
                rs.close();                
            }
            
            System.out.println("Saving restore information ...");
            Calendar c = Calendar.getInstance();
            String date = String.format("%d_%d_%d_%d_%d_%d", c.get(Calendar.YEAR), c.get(Calendar.MONTH)+1, 
                    c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
            PrintStream fout; fout = new PrintStream(String.format("restore_%s.sql", date));
            PrintStream fout1; fout1 = new PrintStream(String.format("apply_%s.sql", date));
            sql = "UPDATE ways SET freeflow_speed = %d WHERE gid=%d;";
            for(int i = 0; i < waysIds.size(); i ++) {
                fout.println(String.format(sql, oldSpeeds.get(i), waysIds.get(i)));
                fout1.println(String.format(sql, maxSpeed, waysIds.get(i)));
            }
            fout.println("qkey");
            for(String qkey: updatableQkeys) 
                fout.println(qkey);
            fout.close();
            fout1.close();
            /*
            System.out.println("Congesting ...");
            sql = "UPDATE ways SET freeflow_speed = ? WHERE gid=ANY(?);";
            try(PreparedStatement pst = conn.prepareStatement(sql)) {
                pst.setInt(1, maxSpeed);
                pst.setArray(2, conn.createArrayOf("int", waysIds.toArray(new Integer[waysIds.size()])));
                pst.executeUpdate();
            }
            update(updatableQkeys);*/
        } catch(SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
        }        
    }
    
    private static void update(List<String> updatableQkeys) {
        TileSystem tileSystem = Main.getTileSystem(dbName);
        SubgraphTask task = new SubgraphTask(tileSystem, dbName, scale, updatableQkeys);
        ThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1);
        pool.execute(task);
        pool.shutdown();
    }
    
    private static void restore() throws Exception {
        File file = new File(fileName);
        //List<String> updatableQkeys;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String s;
            try(Connection conn = Main.getConnection(dbName); Statement st = conn.createStatement()) {
                while((s=reader.readLine())!=null) {
          //          if("qkey".equals(s)) break;
                    st.addBatch(s);
                }
                st.executeBatch();
            }
            /*updatableQkeys = new LinkedList<>();
            while((s=reader.readLine())!=null) {
                updatableQkeys.add(s);
            }*/
        }
        //update(updatableQkeys);
    }
}
