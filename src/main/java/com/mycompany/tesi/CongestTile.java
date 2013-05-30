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
        "0313131311301", "0313131311310"
        //"03131313110120", "03131313110121", "03131313110130", "03131313110131", "03131313111020", "03131313111021", "03131313110122", "03131313110123", "03131313110132", "03131313110133", "03131313111022", "03131313111023", "03131313110300", "03131313110301", "03131313110310", "03131313110311", "03131313111200", "03131313111201", "03131313111203", "03131313111202", "03131313110313", "03131313110312", "03131313110303", "03131313110302", "03131313110320", "03131313110330", "03131313110321", "03131313110331", "03131313111220", "03131313111221", "03131313111223", "03131313111222", "03131313110333", "03131313110332", "03131313110323", "03131313110322", "03131313112100", "03131313112101", "03131313112110", "03131313112111", "03131313113000", "03131313113001"
        //"03131313110210", "03131313110211", "03131313110300", "03131313110212", "03131313110213", "03131313110302", "03131313110230", "03131313110231", "03131313110320"
        
        //"031313131011", "031313131100", "031313131101", "031313131110", "031313131111", "120202020000", "120202020001"
        
        
        
        //"031313113233", "031313113322", "031313113323", "031313113332", "031313113333", "120202002222", "120202002223", "031313131011", "031313131100", "031313131101", "031313131110", "031313131111", "120202020000", "120202020001", "031313131013", "031313131102", "031313131103", "031313131112", "031313131113", "120202020002", "120202020003", "031313131031", "031313131120", "031313131121", "031313131130", "031313131131", "120202020020", "120202020021"
        
        
        //"031313131011", "031313131100", "031313131101", "031313131110", "031313131111", "120202020000", "031313131013", "031313131102", "031313131103", "031313131112", "031313131113", "120202020002", "031313131031", "031313131120", "031313131121", "031313131130", "031313131131", "120202020020", "031313131033", "031313131122", "031313131123", "031313131132", "031313131133", "120202020022", "031313131211", "031313131300", "031313131301", "031313131310", "031313131311", "120202020200", "120202020001", "120202020003", "120202020021", "120202020023", "120202020201" , // box grande
        
        
        //"031313113233", "031313113322", "031313113323", "031313113332", "031313113333", "120202002222", "120202002223"// aggiunta sopra
        
        //"031313131213", "031313131302", "031313131303", "031313131312", "031313131313", "120202020202", "120202020203", "120202020221", "120202020220", "031313131331", "031313131330", "031313131321", "031313131320", "031313131231"//aggiunta sotto
    };
    private final static int maxSpeed = 30;
    private final static int scale = 13;
    private final static String fileName = "restore_2013_5_30_12_3_31.sql";
    private final static boolean CONGEST = true;
    private final static Logger logger = Logger.getLogger(CongestTile.class.getName());
    
    public static void main(String[] args) throws Exception {
        if(CONGEST) congest();
        else restore();
    }
    
    private static void congest() throws Exception {
        //System.out.println(qkeys.length);
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
            
            System.out.println("Congesting ...");
            sql = "UPDATE ways SET freeflow_speed = ? WHERE gid=ANY(?);";
            try(PreparedStatement pst = conn.prepareStatement(sql)) {
                pst.setInt(1, maxSpeed);
                pst.setArray(2, conn.createArrayOf("int", waysIds.toArray(new Integer[waysIds.size()])));
                pst.executeUpdate();
            }
            update(updatableQkeys);
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
        List<String> updatableQkeys;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String s;
            try(Connection conn = Main.getConnection(dbName); Statement st = conn.createStatement()) {
                while((s=reader.readLine())!=null) {
                    if("qkey".equals(s)) break;
                    st.addBatch(s);
                }
                st.executeBatch();
            }
            updatableQkeys = new LinkedList<>();
            while((s=reader.readLine())!=null) {
                updatableQkeys.add(s);
            }
        }
        update(updatableQkeys);
    }
}
