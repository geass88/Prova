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

import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.storage.Graph;
import com.mycompany.tesi.beans.BoundaryNode;
import com.mycompany.tesi.beans.Metrics;
import com.mycompany.tesi.beans.Pair;
import com.mycompany.tesi.beans.StoreData;
import com.mycompany.tesi.hooks.RawEncoder;
import com.mycompany.tesi.hooks.TimeCalculation;
import com.mycompany.tesi.utils.TileSystem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
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
    
    public Pair<StoreData[], Double> createHistogram(String qkey, int scale, double min, double step, int count) {
        TileSystem tileSystem;
        try(Connection conn = Main.getConnection(dbName)) {
            tileSystem = new TileSystem(Main.getBound(conn), Main.MAX_SCALE);
            tileSystem.computeTree();
        } catch(SQLException ex) {
            Logger.getLogger(Histogram.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        SubgraphTask task = new SubgraphTask(tileSystem, dbName, scale);
        SubgraphTask.Cell cell = task.getSubgraph(qkey);

        StoreData[] data = new StoreData[count+1];
        double v = min;
        for(int i = 0; i < count; i ++)
            data[i] = new StoreData(0, v, v += step);
        data[count] = new StoreData(0, v, v);

        AllEdgesIterator i = cell.graph.getAllEdges();
        double max_speed = 0.;
        while(i.next()) {
            double speed = cell.encoder.getSpeedHooked(i.flags());
            int index = (int)Math.floor((speed - min) / step);
            if(index <= count)
                data[index].setFrequency(data[index].getFrequency()+1);
            if(speed > max_speed) max_speed = speed;
        }
        Pair<StoreData[], Double> pair = new Pair<>(data, max_speed);
        return pair;
    }
    
    public StoreData[] createCliqueHistogram(String qkey, int scale, double min, double step, int count) {
        TileSystem tileSystem;
        try(Connection conn = Main.getConnection(dbName)) {
            tileSystem = new TileSystem(Main.getBound(conn), Main.MAX_SCALE);
            tileSystem.computeTree();
        } catch(SQLException ex) {
            Logger.getLogger(Histogram.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        SubgraphTask task = new SubgraphTask(tileSystem, dbName, scale);
        SubgraphTask.Cell cell = task.getSubgraph(qkey);

        StoreData[] data = new StoreData[count+1];
        double v = min;
        for(int i = 0; i < count; i ++)
            data[i] = new StoreData(0, v, v += step);
        data[count] = new StoreData(0, v, v);
        
        Graph graph = cell.graph;
        RawEncoder vehicle = cell.encoder;
        
        BoundaryNode[] nodesArray = cell.boundaryNodes.toArray(new BoundaryNode[cell.boundaryNodes.size()]);
        for(int i = 0; i < nodesArray.length; i ++) {
            for(int j = i+1; j < nodesArray.length; j ++) {
                //if(i == j) continue;
                RoutingAlgorithm algo = new AlgorithmPreparation(vehicle).graph(graph).createAlgo();
                Path path = algo.calcPath(nodesArray[i].getNodeId(), nodesArray[j].getNodeId());
                Metrics m = null, rm = null;
                double speed = 0., rspeed = 0.;
                if(path.found()) {
                    m = new Metrics(path.distance(), new TimeCalculation(vehicle).calcTime(path));
                    speed = m.getDistance()*3.6/m.getTime();
                }
                RoutingAlgorithm ralgo = new AlgorithmPreparation(vehicle).graph(graph).createAlgo();
                Path rpath = ralgo.calcPath(nodesArray[j].getNodeId(), nodesArray[i].getNodeId());
                if(rpath.found()) {
                    rm = new Metrics(rpath.distance(), new TimeCalculation(vehicle).calcTime(rpath));
                    rspeed = rm.getDistance()*3.6/rm.getTime();
                }
                
                if(m != null && rm != null && m.compareTo(rm) == 0) {
                    int index = (int)Math.floor((speed - min) / step);
                    if(index <= count)
                        data[index].setFrequency(data[index].getFrequency()+1);  
                } else {
                    if(m != null) {
                        int index = (int)Math.floor((speed - min) / step);
                        if(index <= count)
                            data[index].setFrequency(data[index].getFrequency()+1);  
                    }
                    if(rm != null) {
                        int index = (int)Math.floor((rspeed - min) / step);
                        if(index <= count)
                            data[index].setFrequency(data[index].getFrequency()+1);  
                    }
                }
            }
        }
        return data;
    }
    
    public Map<String, Integer> getStatsTable(int scale) {
        String sql1 = "select count(distinct source) from (select source from %s union select target from %s) t";
        String sql2 = "select count(*) from %s;";
        String sql3 = "select count(distinct tiles_qkey) from ways_tiles where length(tiles_qkey)=%d";
        String sql4 = "select count(*) from %s where the_geom is not null;";
        
        try(Connection conn = Main.getConnection(dbName); 
                Statement st = conn.createStatement()) {
            Map<String, Integer> map = new HashMap<>();
            String table = scale == 0? "ways": ("overlay_"+scale);
            ResultSet rs = st.executeQuery(String.format(sql1, table, table));
            rs.next();
            map.put("num_nodes", rs.getInt(1));
            rs.close();
            rs = st.executeQuery(String.format(sql2, table));
            rs.next();
            map.put("num_edges", rs.getInt(1));
            rs.close();
            if(scale != 0) {
                rs = st.executeQuery(String.format(sql3, scale));
                rs.next();
                map.put("num_tiles", rs.getInt(1));
                rs.close();
            }
            rs = st.executeQuery(String.format(sql4, table));
            rs.next();
            map.put("num_cut_edges", rs.getInt(1));
            rs.close();         
            return map;
        } catch(SQLException ex) {
            Logger.getLogger(Histogram.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static void main(String args[]) {
        Histogram h = new Histogram("hamburg_routing");
        double min = 0;
        int count = 13;
        double step = 10;
        StoreData[] a = h.createHistogram("1202013120232", 13, min, step, count).getKey();
        
        for(int i = 0; i <= count; i ++) {
            System.out.println(String.format("[%.2f, %.2f) - %d", a[i].getMin(), a[i].getMax(), a[i].getFrequency()));
        }
        //System.out.println(h.getStatsTable(15));
    }
}
