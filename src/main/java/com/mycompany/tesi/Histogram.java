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

import com.mycompany.tesi.beans.StoreData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tommaso
 */
public class Histogram {
    
    private final String dbName;
    
    public Histogram(final String dbName) {
        this.dbName = dbName;
    }
    
    public StoreData[] createHistogram(String table, double min, double step, int count) {
        String sql = String.format("SELECT COUNT(*) AS frequency, floor((freeflow_speed-?)/?) AS index "
                + "FROM \"%s\" WHERE freeflow_speed >= ? GROUP BY index ORDER BY index", table);
        try(Connection conn = Main.getConnection(dbName);
            PreparedStatement st = conn.prepareStatement(sql)) {
            st.setDouble(1, min);
            st.setDouble(2, step);
            st.setDouble(3, min);
            ResultSet rs = st.executeQuery();
            StoreData[] data = new StoreData[count+1];
            double v = min;
            for(int i = 0; i < count; i ++)
                data[i] = new StoreData(0, v, v += step);
            data[count] = new StoreData(0, v, v);
            
            while(rs.next()) {
                int index = rs.getInt("index");
                if(index <= count)
                    data[index].setFrequency(rs.getInt("frequency"));
            }
            rs.close();
            return data;
        } catch(SQLException ex) {
            Logger.getLogger(Histogram.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static void main(String args[]) {
        Histogram h = new Histogram("london_routing");
        double min = 0;
        int count = 40;
        double step = 10;
        StoreData[] a = h.createHistogram("ways", min, step, count);
        for(StoreData i: a)
            System.out.println(i.getFrequency()+" ");
        for(int i = 0; i <= count; i ++) {
            System.out.println(String.format("[%.2f, %.2f)", a[i].getMin(), a[i].getMax()));
        }
        
    }
}
