package com.mycompany.prova;

import java.io.File;
import java.sql.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;
import org.geotoolkit.geometry.GeneralDirectPosition;
import org.geotoolkit.referencing.CRS;
import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

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

class QuadNode<T> {
    public QuadNode() {
        
    }
    
    public void setUserData(T userData) {
        this.userData = userData;
    }
    
    public T getUserData() {
        return userData;
    }
    
    public void setChildren(int i, QuadNode node) {
        children[i] = node;
    }
    
    private T userData;
    public QuadNode children[] = new QuadNode[4];
}

class QuadTree extends DefaultTreeModel {
    public QuadTree(TreeNode root) {
        super(root);
    }
}

/**
 * Hello world!
 *
 */
public class App {
    private final static Envelope2D bound = new Envelope2D(new DirectPosition2D(12, 41.5), new DirectPosition2D(13, 42.5));
    private final static int MaxScale = 17;
    private static long count = 0;
    
    static double round(double val) {
        return Math.round(val*1e12)/1e12;
    }
    
    //12.460774, 41.901514 center
    public static void dividi(DirectPosition p1, DirectPosition p2, int scale) throws Exception {
//       if(scale > MaxScale || p2.getOrdinate(1)<bound.getMinY() || p2.getOrdinate(0) < bound.getMinX() || p1.getOrdinate(0) > bound.getMaxX() || p1.getOrdinate(1) > bound.getMaxY()) return;
        if(scale > MaxScale || !new Envelope2D(p1, p2).intersects(bound)) return;
        DirectPosition p1m = DefaultCRS.geographicToGeocentricTr.transform(p1, null);
        DirectPosition p2m = DefaultCRS.geographicToGeocentricTr.transform(p2, null);
        //Envelope2D r = new Envelope2D(p1, p2);
        DirectPosition m = DefaultCRS.geocentricToGeographicTr.transform(new DirectPosition2D((p1m.getOrdinate(0)+p2m.getOrdinate(0))/2., (p1m.getOrdinate(1)+p2m.getOrdinate(1))/2.), null);
        m.setOrdinate(0, round(m.getOrdinate(0)));
        m.setOrdinate(1, round(m.getOrdinate(1)));
        dividi(new DirectPosition2D(p1.getOrdinate(0), m.getOrdinate(1)), new DirectPosition2D(m.getOrdinate(0), p2.getOrdinate(1)), scale + 1);
        dividi(m, p2, scale + 1);
        dividi(p1, m, scale + 1);
        dividi(new DirectPosition2D(m.getOrdinate(0), p1.getOrdinate(1)), new DirectPosition2D(p2.getOrdinate(0), m.getOrdinate(1)), scale + 1);
        //DirectPosition m = 
       // System.out.println("scale="+scale +" " +new Envelope2D(p1, p2));
        count ++;
    }
    
    public static void main( String[] args ) throws Exception
    {
        double y = SphericalMercator.lat2y(10);
        double x = SphericalMercator.lon2x(-10);
        double lat = SphericalMercator.y2lat(y);
        double lon = SphericalMercator.x2lon(x);
       
       // CoordinateReferenceSystem targetCRS = DefaultGeocentricCRS.CARTESIAN;
        
        /*
         * From this point we can convert an arbitrary amount of coordinates using the
         * same MathTransform object. It could be in concurrent threads if we wish.
         *//*
        DirectPosition sourcePt = new GeneralDirectPosition(
                -10//27 + (59 + 17.0 / 60) / 60,   // 27°59'17"N
               , 10);// 86 + (55 + 31.0 / 60) / 60,0);  // 86°55'31"E
        DirectPosition targetPt = tr.transform(sourcePt, null);
        System.out.println("Source point: " + sourcePt);
        System.out.println("Target point: " + targetPt);
        System.out.println(x + " "+ y);
        System.out.println(lon + " "+ lat);
        File f = new File("prova");
        if(!f.exists())
        System.out.println( "Hello World!" );
        
        System.out.println(((DefaultMutableTreeNode)m.getRoot()).getUserObject());
        /*
        sourcePt.setOrdinate(0, MinLongitude);
        sourcePt.setOrdinate(1, -10);
        targetPt = tr.transform(sourcePt, null);
        System.out.println("Source point: " + sourcePt);
        System.out.println("Target point: " + targetPt);*/
        
        //dividi(new DirectPosition2D(MinLongitude, MinLatitude), new DirectPosition2D(MaxLongitude, MaxLatitude), 0);
        /*Class.forName("org.postgresql.Driver").newInstance();
        Connection conn = DriverManager.getConnection("jdbc:postgresql://192.168.128.128:5432/routing", "postgres", "");
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT count(*) as size FROM zone");
        while(rs.next())
            System.out.println(rs.getInt("size"));
        rs.close();
        st.close();
        conn.close();*/
        DirectPosition2D p1 = new DirectPosition2D(DefaultCRS.geographicCRS, 12.42, 41.8445);
        int scale = 6;
        
        int tile[] = DefaultCRS.pointToTileXY(p1, scale);
        int tileX = tile[0];
        int tileY = tile[1];
        System.out.println(tileX + " " + tileY);
        //System.out.println(p2m);
        System.out.println(count);
        /*
        DefaultMutableTreeNode g = new DefaultMutableTreeNode();
        g.setUserObject("root");
        DefaultTreeModel m = new DefaultTreeModel(g);
        *//*
        QuadTree tree = new QuadTree();
        tree.root = new QuadNode<Long>();
        tree.root.children[0] = new QuadNode<Long>();
        tree.root.children[0] = new QuadNode<Long>();
        tree.root.children[0] = new QuadNode<Long>();
        tree.root.children[0] = new QuadNode<Long>();*/
        System.out.println(QuadKey.fromTileXY(tileX, tileY, scale));
        
        tile = QuadKey.toTileXY(QuadKey.fromTileXY(tileX, tileY, scale));
        System.out.println(tile[0] + " " + tile[1]);
    }
    
}
