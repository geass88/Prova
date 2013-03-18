package com.mycompany.prova;

import java.util.LinkedList;
import java.util.List;
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
          int count = 0;
        TileXY obs = new TileXY(2,2);
        for(Envelope2D item: buildRect1(new TileXY(0,0), new TileXY(4,4), obs)) {
            System.out.println(item); count++;
        }
        System.out.println(count);
        
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
    }
    
    static List<Envelope2D> buildRect(TileXY tile1, TileXY tile2) {
        int N = Math.abs(tile1.getX()-tile2.getX())+1, 
            M = Math.abs(tile1.getY()-tile2.getY())+1;
        TileXY lowerCorner = new TileXY(Math.min(tile1.getX(), tile2.getX()), Math.min(tile1.getY(), tile2.getY()));
        TileXY upperCorner = new TileXY(Math.max(tile1.getX(), tile2.getX()), Math.max(tile1.getY(), tile2.getY()));
        
        List<Envelope2D> list = new LinkedList<>();
        int threshold = (N*M)/2;
        for(int x = N; x > 0; x --)
            for(int y = M; y > 0; y --)// x*y>threshold
                for(int i = 0; i < N+1-x; i ++)
                    for(int j = 0; j < M+1-y; j ++)
                        list.add(new Envelope2D(DefaultCRS.projectedCRS, lowerCorner.getX()+i, lowerCorner.getY()+j, x-1, y-1));
        return list;
    }
    
    static List<Envelope2D> buildRect1(TileXY tile1, TileXY tile2, TileXY obs) {
        int N = Math.abs(tile1.getX()-tile2.getX())+1, 
            M = Math.abs(tile1.getY()-tile2.getY())+1;
        TileXY lowerCorner = new TileXY(Math.min(tile1.getX(), tile2.getX()), Math.min(tile1.getY(), tile2.getY()));
        TileXY upperCorner = new TileXY(Math.max(tile1.getX(), tile2.getX()), Math.max(tile1.getY(), tile2.getY()));
        List<Envelope2D> list = new LinkedList<>();
        int threshold = N*M/2;
        for(int l_sx = lowerCorner.getX()+1; l_sx <= obs.getX(); l_sx ++)
            for(int l_dx = upperCorner.getX()-1; l_dx >= obs.getX(); l_dx --)// x*y>threshold
                for(int l_up = upperCorner.getY()-1; l_up >= obs.getY(); l_up --)
                    for(int l_dw = lowerCorner.getY()+1; l_dw <= obs.getY(); l_dw ++)
                        list.add(new Envelope2D(DefaultCRS.projectedCRS, l_sx, l_dw, l_dx-l_sx, l_up-l_dw));
        return list;
    }
    
    
}
