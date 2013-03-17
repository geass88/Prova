package com.mycompany.prova;

import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;
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
    
    public static void main( String[] args ) throws Exception {
        /*
        double y = SphericalMercator.lat2y(10);
        double x = SphericalMercator.lon2x(-10);
        double lat = SphericalMercator.y2lat(y);
        double lon = SphericalMercator.x2lon(x);
       */
       // CoordinateReferenceSystem targetCRS = DefaultGeocentricCRS.CARTESIAN;
        
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
        int scale = 11;
        TilesCalculator calc = new TilesCalculator(bound, 17);
        calc.computeTree();
        
        TileXY t = calc.pointToTileXY(p1, scale);
        System.out.println(t.getX() + " " + t.getY());
        System.out.println(calc.getTile(t.getX(), t.getY(), scale).getRect());
        
        System.out.println(QuadKeyManager.fromTileXY(new TileXY(t.getX(), t.getY()), scale));
        
        t = QuadKeyManager.toTileXY(QuadKeyManager.fromTileXY(new TileXY(t.getX(), t.getY()), scale));
        System.out.println(t.getX() + " " + t.getY());
    }
    
}
