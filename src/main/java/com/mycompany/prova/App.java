package com.mycompany.prova;

import java.io.File;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
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
    private final static double MinLatitude = -85.05112878;
    private final static double MaxLatitude = 85.05112878;
    private final static double MinLongitude = -180;
    private final static double MaxLongitude = 180;
    
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
/**
 * Hello world!
 *
 */
public class App 
{
    
    public static void main( String[] args ) throws TransformException, NoSuchAuthorityCodeException, FactoryException
    {
        double y = SphericalMercator.lat2y(10);
        double x = SphericalMercator.lon2x(-10);
        double lat = SphericalMercator.y2lat(y);
        double lon = SphericalMercator.x2lon(x);
         
       // CoordinateReferenceSystem targetCRS = DefaultGeocentricCRS.CARTESIAN;
        CoordinateReferenceSystem sourceCRS =  DefaultGeographicCRS.WGS84;//This CRS is equivalent to EPSG:4326 except for axis order, since EPSG puts latitude before longitude 
        //CRS.decode("EPSG:4326");
        CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:3857");// Transverse Mercator
        MathTransform tr = CRS.findMathTransform(sourceCRS, targetCRS);
        /*
         * From this point we can convert an arbitrary amount of coordinates using the
         * same MathTransform object. It could be in concurrent threads if we wish.
         */
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
        DefaultMutableTreeNode g = new DefaultMutableTreeNode();
        g.setUserObject("root");
        DefaultTreeModel m = new DefaultTreeModel(g);
        System.out.println(((DefaultMutableTreeNode)m.getRoot()).getUserObject());
    }
}
