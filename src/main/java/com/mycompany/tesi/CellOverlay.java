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
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import java.sql.*;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import org.geotoolkit.geometry.Envelope2D;

/**
 *
 * @author Tommaso
 */
public class CellOverlay {
    
    private final static String dbName = Main.DBS[0];
    public static String[] QKEYS = { 
    //"0313131311121", "0313131311130", "0313131311131", "0313131311123", "0313131311132", "0313131311133", "0313131311301", "0313131311310", "0313131311311"    
        //"0313131311310", "0313131311311" 
        "031313131011", "031313131100", "031313131101", "031313131110", "031313131111", "120202020000", "031313131013", "031313131102", "031313131103", "031313131112", "031313131113", "120202020002", "031313131031", "031313131120", "031313131121", "031313131130", "031313131131", "120202020020", "031313131033", "031313131122", "031313131123", "031313131132", "031313131133", "120202020022", "031313131211", "031313131300", "031313131301", "031313131310", "031313131311", "120202020200", "120202020001", "120202020003", "120202020021", "120202020023", "120202020201" , // box grande
        "031313113233", "031313113322", "031313113323", "031313113332", "031313113333", "120202002222", "120202002223"// aggiunta sopra
    
    };
    private static int scale = 12;
    private static final Logger logger = Logger.getLogger(CellOverlay.class.getName());
    
    public CellOverlay() {}
    
    public void doAll() {
        TileSystem tileSystem = Main.getTileSystem(dbName);
        
        String sql = "INSERT INTO ostacoli(x1, y1, x2, y2, alpha, scale_grain, etime) VALUES(?, ?, ?, ?, ?, ?, ?)";
        try(Connection conn = Main.getConnection(dbName);
                Statement st = conn.createStatement();
                PreparedStatement pst = conn.prepareStatement(sql)) {
            st.executeUpdate("TRUNCATE TABLE boundary_nodes; TRUNCATE TABLE ostacoli; TRUNCATE TABLE overlay_" + scale);
            //cell = new CellSubgraph().buildSubgraph(qkey, false);
            
            TasksHelper task = new TasksHelper(tileSystem, dbName, scale, Arrays.asList(QKEYS));
            task.setOverlayGen(true);
            task.run();
            SubgraphTask t = new SubgraphTask(tileSystem, dbName, scale);
            Set<BoundaryNode> nodes = new TreeSet<>(); 
            Geometry p = tileSystem.getTile(QKEYS[0]).getPolygon();
            for(int i = 1; i < QKEYS.length; i ++) {
                Tile tile = tileSystem.getTile(QKEYS[i]);
                p = p.union(tile.getPolygon());
            }
            for(String qkey: QKEYS) {
                Tile tile = tileSystem.getTile(qkey);
                SubgraphTask.Cell cell = t.getSubgraph(qkey, true);                
                for(BoundaryNode node : cell.boundaryNodes.toArray(new BoundaryNode[cell.boundaryNodes.size()])) {
                    if(!node.getPoint().intersects(p))
                        nodes.add(node);
                }
                SubgraphTask.Cell cell1 = t.getSubgraph(qkey, false);
                double max_speed = computeCliqueParallel(cell1);
                Envelope2D rect = tile.getRect();
                
                ResultSet rs = st.executeQuery("SELECT MAX(freeflow_speed) FROM overlay_"+scale+ ";");
                if(!rs.next()) {
                    System.err.println("ERRORE!");
                    System.exit(0);
                }
                double vG = rs.getDouble(1);
                pst.clearParameters();
                pst.setDouble(1, rect.getLowerCorner().getX());
                pst.setDouble(2, rect.getLowerCorner().getY());
                pst.setDouble(3, rect.getUpperCorner().getX());
                pst.setDouble(4, rect.getUpperCorner().getY());
                pst.setDouble(5, max_speed/vG);
                pst.setInt(6, scale);
                pst.setDouble(7, 0);
                pst.executeUpdate();
            }
            try(PreparedStatement pst1 = conn.prepareStatement("INSERT INTO boundary_nodes(id, lon, lat) VALUES(?, ?, ?);")) {
                BoundaryNode[] boundaryNodes = nodes.toArray(new BoundaryNode[nodes.size()]);
                for(BoundaryNode node: boundaryNodes) {
                    pst1.clearParameters();
                    pst1.setInt(1, node.getRoadNodeId());
                    pst1.setDouble(2, node.getPoint().getX());
                    pst1.setDouble(3, node.getPoint().getY());
                    pst1.executeUpdate();
                }
            }
            
           // System.out.println(cell.boundaryNodes.size());
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }
/*
    private void sublevel() {        
        TileSystem system = Main.getTileSystem(Main.DBS[0]);
        GeometryFactory factory = new GeometryFactory();
        Coordinate[] coordinates = { 
            new Coordinate(-0.4855275, 51.3321433),
            new Coordinate(-0.4855275, 51.6904132),
            new Coordinate(0.190887, 51.6904132),
            new Coordinate(0.190887, 51.3321433),
            new Coordinate(-0.4855275, 51.3321433)
        };
        //[-0.4855275,51.3321433,0.190887,51.6904132]
        Geometry g = factory.createPolygon(coordinates);
        List<String> qkeys = new LinkedList<>();
        Enumeration e = system.getTreeEnumeration();
        while(e.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
            Tile t = ((Tile)node.getUserObject());
            if(t == null) continue;
            Geometry p = t.getPolygon();
            TreeNode[] path = node.getPath();
            String qkey = "";
            for(int i = 1; i < path.length; i ++)
                qkey += path[i-1].getIndex(path[i]);
            if(qkey.length() == scale && g.intersects(p))
                qkeys.add(qkey);
        }
        QKEYS = qkeys.toArray(new String[qkeys.size()]);
    }*/
    
    private void sublevel() {
        QKEYS = new String[]{
            "03131313110311", "03131313111200", "03131313111201", "03131313111210", "03131313111211", "03131313111300", "03131313111301", "03131313111303", "03131313111302", "03131313111213", "03131313111212", "03131313111203", "03131313111202", "03131313110313", "03131313110331", "03131313111220", "03131313111221", "03131313111230", "03131313111231", "03131313111320", "03131313111321", "03131313111323", "03131313111322", "03131313111233", "03131313111232", "03131313111223", "03131313111222", "03131313110333", "03131313112111", "03131313113000", "03131313113001", "03131313113010", "03131313113011", "03131313113100", "03131313113101", "03131313113103", "03131313113102", "03131313113013", "03131313113012", "03131313113003", "03131313113002", "03131313112113"
        };
    }
    
    
    public static void main(String[] args) {
        CellOverlay instance = new CellOverlay();
        scale = 14;  instance.sublevel();
        instance.doAll();

        //System.out.println(qkeys.size());
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
        if(Double.isInfinite(max_speed) || Double.isNaN(max_speed))
            return 0.;
        return max_speed;
    }    
}
