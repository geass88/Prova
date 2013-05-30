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
import com.graphhopper.storage.Graph;
import com.mycompany.tesi.beans.BoundaryNode;
import com.mycompany.tesi.beans.Metrics;
import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.hooks.RawEncoder;
import com.mycompany.tesi.hooks.TimeCalculation;
import com.mycompany.tesi.utils.TileSystem;
import java.sql.*;
import java.util.Arrays;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotoolkit.geometry.Envelope2D;

/**
 *
 * @author Tommaso
 */
public class CellOverlay {
    
    private final static String dbName = Main.DBS[0];
    private final static String[] qkeys = { 
    "0313131311121", "0313131311130", "0313131311131", "0313131311123", "0313131311132", "0313131311133", "0313131311301", "0313131311310", "0313131311311"    
        //"0313131311310", "0313131311311" 
    
    };
    private int scale = 13;
    
    public CellOverlay() {}
    
    public void doAll() {
        TileSystem tileSystem = Main.getTileSystem(dbName);
        
        String sql = "INSERT INTO ostacoli(x1, y1, x2, y2, alpha, scale_grain, etime) VALUES(?, ?, ?, ?, ?, ?, ?)";
        try(Connection conn = Main.getConnection(dbName);
                Statement st = conn.createStatement();
                PreparedStatement pst = conn.prepareStatement(sql)) {
            st.executeUpdate("TRUNCATE TABLE ostacoli; TRUNCATE TABLE overlay_" + scale);
            //cell = new CellSubgraph().buildSubgraph(qkey, false);
            
            TasksHelper task = new TasksHelper(tileSystem, dbName, scale, Arrays.asList(qkeys));
            task.setOverlayGen(true);
            task.run();
            SubgraphTask t = new SubgraphTask(tileSystem, dbName, scale);
            //Set<Integer> nodes = new TreeSet<>(); 
            for(String qkey: qkeys) {
                Tile tile = tileSystem.getTile(qkey);
                /*SubgraphTask.Cell cell = t.getSubgraph(qkey, true);                
                for(BoundaryNode node : cell.boundaryNodes) {
                    if(!node.getPoint().intersects(tile.getPolygon()))
                        nodes.add(node.getRoadNodeId());
                    
                }*/
                SubgraphTask.Cell cell = t.getSubgraph(qkey, false);
                double max_speed = computeCliqueParallel(cell);
                Envelope2D rect = tile.getRect();
                
                ResultSet rs = st.executeQuery("SELECT MAX(freeflow_speed) FROM overlay_"+scale+ ";");
                if(!rs.next()) {
                    System.err.println("ERRORE!");
                    System.exit(0);
                }
                double vG = rs.getDouble(1);
                pst.setDouble(1, rect.getLowerCorner().getX());
                pst.setDouble(2, rect.getLowerCorner().getY());
                pst.setDouble(3, rect.getUpperCorner().getX());
                pst.setDouble(4, rect.getUpperCorner().getY());
                pst.setDouble(5, max_speed/vG);
                pst.setInt(6, scale);
                pst.setDouble(7, 0);
                pst.executeUpdate();
            }
            //cell = task.getSubgraph(qkey, true);
            
           // System.out.println(cell.boundaryNodes.size());
        } catch (Exception ex) {
            Logger.getLogger(CellSubgraph.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void main(String[] args) {
        CellOverlay i = new CellOverlay();
        i.doAll();
    }
        
    private double computeCliqueParallel(final SubgraphTask.Cell cell) throws Exception {
        BoundaryNode[] nodesArray = cell.boundaryNodes.toArray(new BoundaryNode[cell.boundaryNodes.size()]);
        final int POOL_SIZE = 5;
        
        class Task implements Runnable {

            double max_speed = 0.;
            SubgraphTask.Cell cell;
            BoundaryNode[] nodesArray;
            int tid;

            public Task(SubgraphTask.Cell cell, BoundaryNode[] nodesArray, int tid) {
                this.cell = cell;
                this.nodesArray = nodesArray;
                this.tid = tid;
            }
            
            @Override
            public void run() {
                final Graph graph = cell.graph;
                final RawEncoder vehicle = cell.encoder;
                
                int counter = 0;
                int N = nodesArray.length;
                int count = N*(N-1)/2;
                int step = count / POOL_SIZE;
                int start = tid * step;
                int end = tid == POOL_SIZE-1? count: (tid+1)*step;
                for(int i = 0; i < N; i ++) {
                    for(int j = i+1; j < N; j ++, counter++) {
                        if(counter < start) continue;
                        if(counter >= end) return;
                        //if(i == j) continue;
                        RoutingAlgorithm algo = new AlgorithmPreparation(vehicle).graph(graph).createAlgo();
                        Path path = algo.calcPath(nodesArray[i].getNodeId(), nodesArray[j].getNodeId());
                        Metrics m = null, rm = null;
                        if(path.found()) {
                            m = new Metrics(path.distance(), new TimeCalculation(vehicle).calcTime(path));
                            double speed = m.getDistance()*3.6/m.getTime();
                            if(speed > max_speed) max_speed = speed;
                        }
                        RoutingAlgorithm ralgo = new AlgorithmPreparation(vehicle).graph(graph).createAlgo();
                        Path rpath = ralgo.calcPath(nodesArray[j].getNodeId(), nodesArray[i].getNodeId());
                        if(rpath.found()) {
                            rm = new Metrics(rpath.distance(), new TimeCalculation(vehicle).calcTime(rpath));
                            double speed = rm.getDistance()*3.6/rm.getTime();
                            if(speed > max_speed) max_speed = speed;
                        }
                    }
                }
                
            }
            
        }
        
        ThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(POOL_SIZE);
        Task[] list = new Task[POOL_SIZE];
        for(int i = 0; i < POOL_SIZE; i ++) {
            list[i] = new Task(cell, nodesArray, i);
            pool.execute(list[i]);
        }
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.DAYS);
        
        double max_speed = 0.;
        for(Task item: list)
            if(item.max_speed > max_speed)
                max_speed = item.max_speed;

        return max_speed;
    }    
}
