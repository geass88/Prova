package com.mycompany.prova;

import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;
import org.geotoolkit.referencing.crs.DefaultImageCRS;
import org.opengis.geometry.MismatchedDimensionException;
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

public class App {
    private final static Envelope2D bound = new Envelope2D(new DirectPosition2D(12, 41.5), new DirectPosition2D(13, 42.5));
    
    public static void main(String[] args) throws Exception {
        /*
        double y = SphericalMercator.lat2y(10);
        double x = SphericalMercator.lon2x(-10);
        double lat = SphericalMercator.y2lat(y);
        double lon = SphericalMercator.x2lon(x);
       */
       // CoordinateReferenceSystem targetCRS = DefaultGeocentricCRS.CARTESIAN;
        
        DirectPosition2D p1 = new DirectPosition2D(DefaultCRS.geographicCRS, 12.42, 41.8445);
        int scale = 11;
        TileSystem calc = new TileSystem(bound, 17);
        calc.computeTree();
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
        maxSpeed(rect, new TileXYRectangle(new TileXY(2,1), new TileXY(4,0)));
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
    
    /**
     * Find all the rectangles between tile1 and tile2
     * @param tile1
     * @param tile2
     * @return 
     */
    static List<TileXYRectangle> buildRect(TileXYRectangle rect) {// list_zone
        int N = rect.getWidth() + 1, 
            M = rect.getHeight() + 1;
        
        List<TileXYRectangle> list = new LinkedList<>();
        int threshold = (N*M)/2;
        for(int x = N; x > 0; x --)
            for(int y = M; y > 0; y --)// x*y>threshold
                for(int i = 0; i < N+1-x; i ++)
                    for(int j = 0; j < M+1-y; j ++)
                        list.add(new TileXYRectangle(rect.getLowerCorner().getX()+i, rect.getLowerCorner().getY()+j, x-1, y-1));
        return list;
    }
    
    /**
     * Find all the rectangles between tile1 and tile2 that contains obs
     * @param tile1
     * @param tile2
     * @param obs
     * @return 
     */
    static List<TileXYRectangle> buildRect1(TileXYRectangle rect, TileXY obs) {
        int N = rect.getWidth() + 1, 
            M = rect.getHeight() + 1;
        
        List<TileXYRectangle> list = new LinkedList<>();
        int threshold = N*M/2;
        for(int l_sx = rect.getLowerCorner().getX(); l_sx <= obs.getX(); l_sx ++)
            for(int l_dx = rect.getUpperCorner().getX(); l_dx >= obs.getX(); l_dx --)
                for(int l_up = rect.getUpperCorner().getY(); l_up >= obs.getY(); l_up --)
                    for(int l_dw = rect.getLowerCorner().getY(); l_dw <= obs.getY(); l_dw ++)
                        list.add(new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw));
        return list;
    }
    
    static int maxSpeed(TileXYRectangle rect, TileXYRectangle obs) {
        int N = rect.getWidth() + 1, 
            M = rect.getHeight() + 1;
        
        double inside_speed = 0.;
        double outside_speed = 0.;
        for(int i = rect.getLowerCorner().getX(); i <= rect.getUpperCorner().getX(); i ++)
            for(int j = rect.getLowerCorner().getY(); j <= rect.getUpperCorner().getY(); j ++)
                if(obs.getLowerCorner().getX()<=i && i<=obs.getUpperCorner().getX() && 
                        obs.getLowerCorner().getY()<=j && j<= obs.getUpperCorner().getY()) { // (i, j) in {rect intersection obs}
                    inside_speed ++;
                } else {
                    outside_speed ++;
                }
        System.out.println(inside_speed);
        System.out.println(outside_speed);
        return 0;
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
