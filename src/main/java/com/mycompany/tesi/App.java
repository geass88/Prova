/*
 FILE DI SOLE PROVE: inutile!!
 
 */

package com.mycompany.tesi;

import com.graphhopper.routing.AStar;
import com.mycompany.tesi.utils.DefaultCRS;
import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.beans.TileXY;
import com.mycompany.tesi.beans.TileXYRectangle;
import com.mycompany.tesi.utils.TileSystem;
import com.mycompany.tesi.utils.QuadKeyManager;
import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.DijkstraBidirection;
import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.Path.EdgeVisitor;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.AcceptWay;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.CombinedEncoder;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.routing.util.ShortestCalc;
import com.graphhopper.routing.util.VehicleEncoder;
import com.graphhopper.routing.util.WeightCalculation;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.storage.index.Location2IDIndex;
import com.graphhopper.storage.index.Location2IDQuadtree;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;
import static com.mycompany.tesi.TasksHelper.POOL_SIZE;
import com.mycompany.tesi.hooks.FastestCalc;
import com.mycompany.tesi.hooks.MyCarFlagEncoder;
import com.mycompany.tesi.hooks.TimeCalculation;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.io.WKTReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;
import org.geotoolkit.referencing.CRS;
import org.geotoolkit.referencing.crs.DefaultImageCRS;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;


/*
class SphericalMercator {
    private final static double R = 6378137.;
    
    public static double y2lat(double aY) {
        return Math.toDegrees(2.* Math.atan(Math.exp(aY/R)) - Math.PI/2.);
    }

    public static double lat2y(double aLat) {
        return Math.log(Math.tan(Math.PI/4.+Math.toRadians(aLat)/2.))*R;
    }
    
    public static double x2lon(double aX) {
        return Math.toDegrees(aX/R);
    }

    public static double lon2x(double aLon) {
        return Math.toRadians(aLon)*R;
    }
}
*/
class Tasks2 implements Runnable {

    @Override
    public void run() {
        System.out.println("task2");
    }
    
}


class Tasks1 implements Runnable {
    ThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(5);
    
    @Override
    public void run() {
        System.out.println("task1");
        pool.execute(new Tasks2());
        pool.execute(new Tasks2());
        pool.execute(new Tasks2());
        pool.execute(new Tasks2());
        pool.execute(new Tasks2());
        pool.execute(new Tasks2());
        pool.shutdown();
        try {
            pool.awaitTermination(1, TimeUnit.DAYS);
        } catch (InterruptedException ex) {
            Logger.getLogger(Tasks1.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}

public class App {
    
    private final static Envelope2D bound = new Envelope2D(new DirectPosition2D(12, 41.5), new DirectPosition2D(13, 42.5));
    static ThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(2);
    public static void main(String[] args) throws Exception {
        /*pool.execute(new Tasks1());
        pool.execute(new Tasks1());
        pool.execute(new Tasks1());
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.DAYS);
        System.out.println("exit main");
        if(1==1)return ;
        /*
        double y = SphericalMercator.lat2y(10);
        double x = SphericalMercator.lon2x(-10);
        double lat = SphericalMercator.y2lat(y);
        double lon = SphericalMercator.x2lon(x);
       */
       // CoordinateReferenceSystem targetCRS = DefaultGeocentricCRS.CARTESIAN;
        pool.shutdown();
        TileSystem calc1 = new TileSystem(bound, 17);
        calc1.computeTree();
        CoordinateReferenceSystem crs = CRS.decode("EPSG:26986");
        MathTransform tr  = CRS.findMathTransform(DefaultCRS.geographicCRS, crs);
        List<Tile> l = calc1.visit(17);
        
        for(Tile t: l)
            System.out.println(t.getRect().getLowerCorner().
                    distance(t.getRect().getUpperCorner()));
        //new DistanceCalc().calcDist(t.getRect().getMinY(), t.getRect().getMinX(), t.getRect().getMaxY(), t.getRect().getMaxX()));
        //System.out.println(new DistanceCalc().calcDist(51.515579784224, -0.087890625, 51.522416083001, -0.076904296875));
        if(1==1) return;
        DirectPosition2D p1 = new DirectPosition2D(DefaultCRS.geographicCRS, 12.42, 41.8445);
        int scale = 11;
        TileSystem calc = new TileSystem(bound, 17);
        calc.computeTree();
        System.out.println("VISIT "+calc.visit(9).size());
        //TileSystem calc = new TileSystem(bound, 16);
        //calc.computeTree(new Envelope2D(new DirectPosition2D(geographicCRS, 0, 0), new DirectPosition2D(geographicCRS, MaxLongitude, MaxLatitude)));
        
        TileXY t = calc.pointToTileXY(p1, scale);
        System.out.println(t.getX() + " " + t.getY());
        System.out.println(calc.getTile(t.getX(), t.getY(), scale).getRect());
        System.out.println("back "+calc.pointToTileXY(calc.getTile(t.getX(), t.getY(), scale).getRect().getLowerCorner(), scale));
        System.out.println(QuadKeyManager.fromTileXY(new TileXY(t.getX(), t.getY()), scale));
        
        t = QuadKeyManager.toTileXY(QuadKeyManager.fromTileXY(new TileXY(t.getX(), t.getY()), scale));
        System.out.println(t.getX() + " " + t.getY());
        
        TileXY obs = new TileXY(2,0);
        TileXY corner1 = new TileXY(0,1);
        TileXY corner2 = new TileXY(5,0);
        TileXYRectangle rect = new TileXYRectangle(corner2, corner1);
        
        /*
        int count = 0;
        for(TileXYRectangle item: buildRect1(rect, obs)) {
            System.out.println(item); count++;
        }       
        System.out.println(count);
        */
        //maxSpeed(rect, new TileXYRectangle(new TileXY(2,1), new TileXY(4,0)));
        
        Geometry g1 = new WKTReader().read("LINESTRING (0 0, 10 10, 20 21)");
        System.out.println("Geometry 1: " + g1);
        
        // create a geometry by specifying the coordinates directly
        Coordinate[] coordinates = new Coordinate[]{new Coordinate(0, 0),
          new Coordinate(10, 10), new Coordinate(20, 20)};
        // use the default factory, which gives full double-precision
        Geometry g2 = new GeometryFactory().createLineString(coordinates);
        System.out.println("Geometry 2: " + g2);

        // compute the intersection of the two geometries
        Geometry g3 = g1.intersection(g2);
        System.out.println("G1 intersection G2: " + g3);
        /*
        GraphHopper hopper = new GraphHopper().graphHopperLocation("");
        //hopper.contractionHierarchies(true);
        hopper.forServer();                
        hopper.load("F:/Tommaso/VM/Shared/berlin-latest.osm");
        Graph g = hopper.getGraph();
        AllEdgesIterator i = g.getAllEdges();
        WKTReader r = new WKTReader();
        Envelope2D bound1= new Envelope2D(new DirectPosition2D(13.062824973378143, 52.3279473705876), new DirectPosition2D(13.763971934407323, 52.67961645900346));
        
        TileSystem calc1 = new TileSystem(bound1, 17);
        calc1.computeTree();
        
        List<Tile> lista = calc1.visit(15);
        while(i.next()) {
            String s = "LINESTRING(" + g.getLongitude(i.baseNode()) + " " + g.getLatitude(i.baseNode()) + ", ";
            
            for(Double[] d: i.wayGeometry().toGeoJson())
                s += d[0] + " " + d[1] + ", ";
            
            s += g.getLongitude(i.adjNode()) + " " + g.getLatitude(i.adjNode()) + ")";
            g1 = r.read(s);
            for(Tile tile: lista) {
                Geometry geo = org.geotoolkit.geometry.jts.JTS.toGeometry(tile.getRect());
                if(g1.intersects(geo))
                    ;//System.out.println("trovato");
            }
        }*/
        
        
        GraphStorage graph = new GraphBuilder().create();
        graph.combinedEncoder(MyCarFlagEncoder.COMBINED_ENCODER);
        GraphStorage graph1 = new GraphBuilder().create();
        graph.combinedEncoder(MyCarFlagEncoder.COMBINED_ENCODER);
        final VehicleEncoder enc = AcceptWay.parse("CAR").firstEncoder();
        final MyCarFlagEncoder vehicle = new MyCarFlagEncoder(130);
        final MyCarFlagEncoder vehicle1 = new MyCarFlagEncoder(130);
        //graph.combinedEncoder(vehicle.COMBINED_ENCODER);
        graph.setNode(1, 0, 0);
        graph.setNode(2, 0, 0);
        graph.setNode(3, 0,0);
        System.out.println("lon "+graph.getLongitude(1));
        PointList pillar = new PointList();
        pillar.add(1.5d, 1.2d);
                pillar.add(1.7d, 1.5d);
        /*graph.edge(2,3, 100, vehicle.flags(50., true));
        graph.edge(3,1, 200, vehicle.flags(10.1, false));
        graph.edge(1,2, 100, vehicle.flags(50., true));//.wayGeometry(pillar);
        graph.edge(1,3, 800, vehicle.flags(50., false));//.wayGeometry(pillar);*/
        graph.edge(2,3, 100, vehicle.flags(50, true));
        graph.edge(2,3, 200, vehicle.flags(50, true));
        graph.edge(3,1, 200, vehicle.flags(10, false));
        graph.edge(1,2, 100, vehicle.flags(50, false)).wayGeometry(pillar);
        //graph.edge(1,3, 800, enc.flags(50, false));//.wayGeometry(pillar);
        Map<Integer, Integer> ids=new HashMap<>();
        ids.put(1, 30);
        ids.put(3, 10);
        ids.put(2, 20);
        for(Integer i: ids.keySet())
            graph1.setNode(ids.get(i), graph.getLatitude(i), graph.getLongitude(i));
        AllEdgesIterator i = graph.getAllEdges();
        while(i.next()) {
            PointList g =i.wayGeometry();
            if(vehicle.isForward(i.flags()))
                graph1.edge(ids.get(i.baseNode()), ids.get(i.adjNode()), i.distance(), vehicle1.flags(vehicle.getSpeedHooked(i.flags()), vehicle.isBackward(i.flags()))).wayGeometry(g);
            else {
                g.reverse();
                graph1.edge(ids.get(i.adjNode()), ids.get(i.baseNode()), i.distance(), vehicle1.flags(vehicle.getSpeedHooked(i.flags()), vehicle.isForward(i.flags()))).wayGeometry(g);
            }
        }
        for(int d=0; d<=graph.nodes(); d++)
        System.out.println("node "+graph.getLatitude(d)+" " + graph.getLongitude(d));
        i = graph.getAllEdges();
        while(i.next())
            System.out.println("arc "+i.baseNode()+" "+i.adjNode() + " " +i.wayGeometry() +" " +vehicle.isForward(i.flags())+ " " + vehicle.isBackward(i.flags()));

        
         for(int d=0; d<=graph1.nodes(); d++)
        System.out.println("node "+graph1.getLatitude(d)+" " + graph1.getLongitude(d));
        i = graph1.getAllEdges();
        while(i.next())
            System.out.println("arc "+i.baseNode()+" "+i.adjNode() + " " +i.wayGeometry() +" " +vehicle1.isForward(i.flags())+ " " + vehicle1.isBackward(i.flags()));

        AlgorithmPreparation op= new NoOpAlgorithmPreparation() {
            @Override public RoutingAlgorithm createAlgo() {                
                return new AStarBidirection(_graph, vehicle).type(new FastestCalc(vehicle));
            }
        }.graph(graph);
                
        Path path = op.createAlgo().calcPath(1,3);
        
        path.forEveryEdge(new EdgeVisitor() {

            @Override
            public void next(EdgeIterator iter) {
                System.out.println(iter.adjNode() + " " +iter.baseNode() +" " +iter.wayGeometry());
            }
        });
        
        System.out.println(path.toDetailsString());
        System.out.println(path.calcPoints());
        System.out.println(path.distance());
        TimeCalculation path1 = new TimeCalculation(vehicle);
        //System.out.println("time: "+path.time() + " " + path1.calcTime(path));
        
        
        /*
        
        GraphStorage graph = new GraphBuilder().create();
        Map<String, Integer> nodi = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/routing", "postgres", "postgres"); 
                Statement st = conn.createStatement(); 
                ResultSet rs = st.executeQuery("select distinct * from ((select x1, y1 from berlin_2po_4pgr) union (select x2, y2 from berlin_2po_4pgr)) f")) {
            int co=0;
            while(rs.next()) {
                graph.setNode(co, rs.getDouble(1), rs.getDouble(2));
                nodi.put(rs.getDouble(1) +" "+ rs.getDouble(2), co);
                co++;
            }
        }
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/routing", "postgres", "postgres"); 
                Statement st = conn.createStatement(); 
                ResultSet rs = st.executeQuery("select x1, y1, x2, y2, km, reverse_cost<>1000000 from berlin_2po_4pgr")) {
            
            while(rs.next()) {
                graph.edge(nodi.get(rs.getDouble(1) +" "+ rs.getDouble(2)), nodi.get(rs.getDouble(3) +" "+ rs.getDouble(4)),
                        rs.getDouble(5), rs.getBoolean(6));
            }
        }
        
        AlgorithmPreparation op= new NoOpAlgorithmPreparation() {
            @Override public RoutingAlgorithm createAlgo() {
                return new AStarBidirection(_graph, new CarFlagEncoder()).type(new ShortestCalc());
            }
        }.graph(graph);
        System.out.println(op.createAlgo().calcPath(1,200 ).toDetailsString());
        
        /*
         * implements Comparable<Tile>
            @Override
            public int compareTo(Tile o) {
                return this.rect.equals(o.rect)? 0:1;
            }
        Set<Tile> set = new TreeSet<>();
        set.add(new Tile(new Envelope2D(DefaultCRS.projectedCRS,1,1,1,1)));
        set.add(new Tile(new Envelope2D()));
        System.out.println(set.size());
        * */
        /*
         * caricamento dei Tile da db postgres
        TreeModel tree = new DefaultTreeModel(new DefaultMutableTreeNode(new Tile(DefaultCRS.geographicRect)));        
        Class.forName("org.postgresql.Driver").newInstance();
        List<Tile> list;
        java.util.Queue<Integer> scales;
        try (Connection conn = DriverManager.getConnection("jdbc:postgresql://192.168.128.128:5432/routing", "postgres", ""); 
                Statement st = conn.createStatement(); 
                ResultSet rs = st.executeQuery("SELECT lon1, lat1, lon2, lat2, scale FROM zone")) {
            list = new LinkedList<>();
            scales = new LinkedList<>();
            while(rs.next()) {
                list.add(new Tile(new Envelope2D(null, rs.getDouble(1),rs.getDouble(2),rs.getDouble(3),rs.getDouble(4))));
                scales.add(rs.getInt(5));
            }
        }
        loadTiles(tree,list,scales);*/
    }
    
    
    
    static void loadTiles(TreeModel tree, List<Tile> tiles, java.util.Queue<Integer> scale) {
        TileSystem calc = new TileSystem(bound, 17);
        calc.computeTree();
        try {
            for(Tile tile: tiles) {
                String key = QuadKeyManager.fromTileXY(calc.pointToTileXY(tile.getRect().getLowerCorner(), scale.element()), scale.poll());            
                MutableTreeNode node = (MutableTreeNode) tree.getRoot();
                for(int i = 0; i < key.length(); i ++) {
                    int j = key.charAt(i) - '0';
                    for(int k = node.getChildCount(); k <= j; k ++)
                        node.insert(new DefaultMutableTreeNode(null), k);
                    node = (MutableTreeNode)node.getChildAt(j);
                }
                ((DefaultMutableTreeNode)node).setUserObject(tile);
            }
        } catch (MismatchedDimensionException | TransformException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
