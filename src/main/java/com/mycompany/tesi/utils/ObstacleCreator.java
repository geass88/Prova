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
package com.mycompany.tesi.utils;

import com.graphhopper.util.shapes.GHPlace;
import com.mycompany.tesi.Main;
import com.mycompany.tesi.beans.Obstacle;
import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.beans.TileXY;
import com.mycompany.tesi.beans.TileXYRectangle;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;

/**
 *
 * @author Tommaso
 */
public class ObstacleCreator {
    
    private final TileSystem tileSystem;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final static Logger logger = Logger.getLogger(ObstacleCreator.class.getName());
    private  SpeedEstimator estimator;
    public final static Integer maxRectArea = 100;
    
    //public ObstacleCreator() {}
    
    public ObstacleCreator(final TileSystem tileSystem) {
        this(tileSystem, false);
    }
    
    public ObstacleCreator(final TileSystem tileSystem, boolean climb) {
        this.tileSystem = tileSystem;
        this.estimator = climb? new ClimberEstimator(tileSystem): new RawEstimator(tileSystem);
    }
    
    private TileXYRectangle findRect(final Point start, final Point end, final int scale, boolean outer) {
        try {
            TileXY startTileXY = tileSystem.pointToTileXY(start.getX(), start.getY(), scale);
            TileXY endTileXY = tileSystem.pointToTileXY(end.getX(), end.getY(), scale);
            TileXYRectangle rect = new TileXYRectangle(startTileXY, endTileXY);
            if(outer) return rect;
            
            int stepX = Math.abs(startTileXY.getX() - endTileXY.getX()) > 1 ? 1: 0; 
            int stepY = Math.abs(startTileXY.getY() - endTileXY.getY()) > 1 ? 1: 0;
            if(stepX == 0 && stepY == 0) return null;
            TileXY corner = rect.getLowerCorner();
            corner.setX(corner.getX()+stepX);
            corner.setY(corner.getY()+stepY);
            corner = rect.getUpperCorner();
            corner.setX(corner.getX()-stepX);
            corner.setY(corner.getY()-stepY);
            return rect;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private List<TileXY> listSeeds(final TileXYRectangle rect, final Point start, final Point end, final int scale) {
        try {
            List<TileXY> list = new LinkedList<>();
            LineString segment = geometryFactory.createLineString(new Coordinate[] { start.getCoordinate(), end.getCoordinate() });
            
            for(int i = rect.getLowerCorner().getX(); i <= rect.getUpperCorner().getX(); i ++)
                for(int j = rect.getLowerCorner().getY(); j <= rect.getUpperCorner().getY(); j ++) {
                    Polygon polygon = tileSystem.getTile(i, j, scale).getPolygon();
                    if(segment.intersects(polygon))
                        list.add(new TileXY(i, j));
                }
            return list;
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    /**
     * Find all the rectangles within rect
     * @param rect
     * @return 
     */
    private List<TileXYRectangle> buildRect(final TileXYRectangle rect) {// list_zone
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
     * Find all the rectangles within rect that contains obs
     * @param rect
     * @param obs
     * @return 
     */
    private List<TileXYRectangle> buildRect(final TileXYRectangle rect, final TileXY obs) {
        /*int N = rect.getWidth() + 1, 
            M = rect.getHeight() + 1;
        */
        List<TileXYRectangle> list = new LinkedList<>();
        //int threshold = N*M/2;
        for(int l_sx = rect.getLowerCorner().getX(); l_sx <= obs.getX(); l_sx ++)
            for(int l_dx = rect.getUpperCorner().getX(); l_dx >= obs.getX(); l_dx --)
                for(int l_up = rect.getUpperCorner().getY(); l_up >= obs.getY(); l_up --)
                    for(int l_dw = rect.getLowerCorner().getY(); l_dw <= obs.getY(); l_dw ++)
                        list.add(new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw));
        return list;
    }
    
    //old
    private double quality(final TileXYRectangle rect, final TileXYRectangle obstacle, final int scale) {
        double insideSpeed = 0.;
        double outsideSpeed = 0.;
        for(int i = rect.getLowerCorner().getX(); i <= rect.getUpperCorner().getX(); i ++)
            for(int j = rect.getLowerCorner().getY(); j <= rect.getUpperCorner().getY(); j ++) {
                Tile tile = tileSystem.getTile(i, j, scale);
                //if(tile == null) continue;
                double maxSpeed = tile==null || tile.getUserObject()==null? 0: (double) tile.getUserObject();
                if(obstacle.getLowerCorner().getX()<=i && i<=obstacle.getUpperCorner().getX() && 
                        obstacle.getLowerCorner().getY()<=j && j<= obstacle.getUpperCorner().getY()) { // (i, j) in {rect intersection obs}
                    if(maxSpeed > insideSpeed)
                        insideSpeed = maxSpeed;
                } else {
                    if(maxSpeed > outsideSpeed)
                        outsideSpeed = maxSpeed;
                }
            }
        //System.out.println(insideSpeed);
        //System.out.println(outsideSpeed);
        int W = obstacle.getWidth() + 1, 
            H = obstacle.getHeight() + 1;
        double ok = insideSpeed/outsideSpeed < 0.7? 1:0;
        return ok * W * H;
        //return insideSpeed/outsideSpeed; //alpha
    }
    //old
    /*private double quality(final TileXYRectangle obstacle, final int scale, double outsideSpeed) {
        double insideSpeed = precise? estimateSpeed1(obstacle, scale): estimateSpeed(obstacle, scale);
        
        int W = obstacle.getWidth() + 1, 
            H = obstacle.getHeight() + 1;
        double ok = insideSpeed/outsideSpeed < 0.7? 1: 0;
        return ok * W * H;
    }*/
    
    public Obstacle getObstacle(final Point start, final Point end, final int scale) {
        TileXYRectangle outerRect = findRect(start, end, scale, true);
        TileXYRectangle innerRect = findRect(start, end, scale, false);
        if(innerRect == null) return null;
        List<TileXY> seeds = listSeeds(innerRect, start, end, scale);
        Set<TileXYRectangle> obstacles = new TreeSet<>();
        //long time1 = System.nanoTime();
        for(TileXY seed: seeds) {
            obstacles.addAll(buildRect(innerRect, seed));
        }
        //long time2 = System.nanoTime();
        //System.out.println((time2-time1)*1e-6);
        //this.estimator = new FastEstimator(tileSystem, outerRect, scale);
        TileXYRectangle bestObstacle = null;
        double bestQ = 0., alphaObstacle = 0.;
        double outsideSpeed = 130.;//estimator.estimateSpeed(outerRect, scale);
        double maxArea = (outerRect.getWidth()+1)*(outerRect.getHeight()+1)/100.;
        
        for(TileXYRectangle obstacle: obstacles) {
            double insideSpeed = estimator.estimateSpeed(obstacle, scale);
            int W = obstacle.getWidth() + 1, H = obstacle.getHeight() + 1;
            double alpha = insideSpeed/outsideSpeed;
            double ok = alpha < 0.7? 1: 0;
            double alphaInv = alpha == 0? 130: 1/alpha;
            double quality = ok * (W*H/maxArea + alphaInv/1.3); // ok * (W*H);
            //double quality = quality(outerRect, obstacle, scale);
            if(quality > bestQ) {
                bestObstacle = obstacle;
                bestQ = quality;
                alphaObstacle = alpha;
            }
        }
        //quality1(bestObstacle, scale, outsideSpeed, true);
        if(bestObstacle == null)
            return null;
        Tile lowerTile = tileSystem.getTile(bestObstacle.getLowerCorner(), scale);
        Tile upperTile = tileSystem.getTile(bestObstacle.getUpperCorner(), scale);
        /*System.out.println("best "+bestObstacle);
        System.out.println("best "+bestObstacle.getLowerCorner());
        System.out.println("best "+bestObstacle.getUpperCorner());
        
        System.out.println(lowerTile.getRect().getLowerCorner().getX());
        System.out.println(lowerTile.getRect().getLowerCorner().getY());
        System.out.println(lowerTile.getRect().getUpperCorner().getX());
        System.out.println(lowerTile.getRect().getUpperCorner().getY());
        System.out.println(upperTile.getRect().getLowerCorner().getX());
        System.out.println(upperTile.getRect().getLowerCorner().getY());
        System.out.println(upperTile.getRect().getUpperCorner().getX());
        System.out.println(upperTile.getRect().getUpperCorner().getY());
        */
        Envelope2D envelope = new Envelope2D(new DirectPosition2D(lowerTile.getRect().getLowerCorner().x, upperTile.getRect().getLowerCorner().y),
                new DirectPosition2D(upperTile.getRect().getUpperCorner().x, lowerTile.getRect().getUpperCorner().y));
        return new Obstacle(envelope, alphaObstacle, scale);
    }
    
    public Obstacle getObstacleParallel(final Point start, final Point end, final int scale) {
        final TileXYRectangle outerRect = findRect(start, end, scale, true);
        final TileXYRectangle innerRect = findRect(start, end, scale, false);
        if(innerRect == null) return null;
        final List<TileXY> seeds = listSeeds(innerRect, start, end, scale);
        final Set<TileXYRectangle> obstacles = new TreeSet<>();
        long time1 = System.nanoTime();
        for(TileXY seed: seeds) {
            obstacles.addAll(buildRect(innerRect, seed));
        }
        long time2 = System.nanoTime();
        System.out.println((time2-time1)*1e-6);
        System.out.println(obstacles.size());
        final int POOL_SIZE=50;
        
        this.estimator = new FastEstimator(tileSystem, outerRect, scale);
        class Res implements Comparable<Res> {
            double quality;
            TileXYRectangle bestObstacle;
            double alphaObstacle;

            public Res(double quality, TileXYRectangle bestObstacle, double alphaObstacle) {
                this.quality = quality;
                this.bestObstacle = bestObstacle;
                this.alphaObstacle = alphaObstacle;                
            }
            @Override
            public int compareTo(Res o) {
                if(quality == o.quality) return 0;
                return quality > o.quality? 1: -1;
            }
            
        };
        final java.util.concurrent.PriorityBlockingQueue<Res> q = new java.util.concurrent.PriorityBlockingQueue<>();
        class Parallel implements Runnable {
            final int id;
            
            public Parallel(int id) {
                this.id = id;
            }
            
            @Override
            public void run() {
                /*Set<TileXYRectangle> obstacles = new TreeSet<>();
                int div = seeds.size() / POOL_SIZE;
                int res = seeds.size() % POOL_SIZE;
                int start = id * div + (id<res? id: res);
                int end = start + div + (id<res? 1: 0);
                for(int i = start; i < end; i++) {
                    obstacles.addAll(buildRect(innerRect, seeds.get(i)));
                }*/
                int div = obstacles.size() / POOL_SIZE;
                int res = obstacles.size() % POOL_SIZE;
                int start = id * div + (id<res? id: res);
                int end = start + div + (id<res? 1: 0);
                
                double outsideSpeed = 130.;//estimator.estimateSpeed(outerRect, scale);
                double maxArea = (outerRect.getWidth()+1)*(outerRect.getHeight()+1)/100.;
                
                TileXYRectangle bestObstacle = null;
                double alphaObstacle = 0.;
                double bestQ = 0.;
                TileXYRectangle[] obs = obstacles.toArray(new TileXYRectangle[obstacles.size()]);
                for(int i = start; i < end; i ++) {
                    TileXYRectangle obstacle = obs[i];
                    double insideSpeed = estimator.estimateSpeed(obstacle, scale);
                    int W = obstacle.getWidth() + 1, H = obstacle.getHeight() + 1;
                    double alpha = insideSpeed/outsideSpeed;
                    double ok = alpha < 0.7? 1: 0;
                    double alphaInv = alpha == 0? 130: 1/alpha;
                    double quality = ok * (W*H/maxArea + alphaInv/1.3); // ok * (W*H);
                    //double quality = quality(outerRect, obstacle, scale);
                    if(quality > bestQ) {
                        bestObstacle = obstacle;
                        bestQ = quality;
                        alphaObstacle = alpha;
                    }
                }
                q.add(new Res(bestQ, bestObstacle, alphaObstacle));
            }
        }
        
        ThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(POOL_SIZE);
        
        for(int i = 0; i< POOL_SIZE; i++)
            pool.execute(new Parallel(i));
        pool.shutdown();
        try {
            pool.awaitTermination(1, TimeUnit.DAYS);
        } catch (InterruptedException ex) {
            Logger.getLogger(ObstacleCreator.class.getName()).log(Level.SEVERE, null, ex);
        }
        Res o = q.peek();
        
        //quality1(bestObstacle, scale, outsideSpeed, true);
        if(o.bestObstacle == null)
            return null;
        Tile lowerTile = tileSystem.getTile(o.bestObstacle.getLowerCorner(), scale);
        Tile upperTile = tileSystem.getTile(o.bestObstacle.getUpperCorner(), scale);
        
        Envelope2D envelope = new Envelope2D(new DirectPosition2D(lowerTile.getRect().getLowerCorner().x, upperTile.getRect().getLowerCorner().y),
                new DirectPosition2D(upperTile.getRect().getUpperCorner().x, lowerTile.getRect().getUpperCorner().y));
        return new Obstacle(envelope, o.alphaObstacle, scale);
    }
    public Obstacle getObstacleParallel(final GHPlace start, final GHPlace end, final int scale) {
        Point startPoint = geometryFactory.createPoint(new Coordinate(start.lon, start.lat));
        Point endPoint = geometryFactory.createPoint(new Coordinate(end.lon, end.lat));
        return getObstacleParallel(startPoint, endPoint, scale);
    }
    
    public Obstacle getObstacle(final DirectPosition2D start, final DirectPosition2D end, final int scale) {
        Point startPoint = geometryFactory.createPoint(new Coordinate(start.getX(), start.getY()));
        Point endPoint = geometryFactory.createPoint(new Coordinate(end.getX(), end.getY()));
        return getObstacle(startPoint, endPoint, scale);
    }
    
    public Obstacle getObstacle(final GHPlace start, final GHPlace end, final int scale) {
        Point startPoint = geometryFactory.createPoint(new Coordinate(start.lon, start.lat));
        Point endPoint = geometryFactory.createPoint(new Coordinate(end.lon, end.lat));
        return getObstacle(startPoint, endPoint, scale);
    }
    
    public Obstacle getObstacle(final Point start, final Point end) {
        int scale = findHeuristicScale(start, end);
        return getObstacle(start, end, scale);
    }
    
    public Obstacle getObstacle(final DirectPosition2D start, final DirectPosition2D end) {
        Point startPoint = geometryFactory.createPoint(new Coordinate(start.getX(), start.getY()));
        Point endPoint = geometryFactory.createPoint(new Coordinate(end.getX(), end.getY()));
        return getObstacle(startPoint, endPoint);
    }
    
    public Obstacle getObstacle(final GHPlace start, final GHPlace end) {
        Point startPoint = geometryFactory.createPoint(new Coordinate(start.lon, start.lat));
        Point endPoint = geometryFactory.createPoint(new Coordinate(end.lon, end.lat));
        return getObstacle(startPoint, endPoint);
    }
    
    public int findHeuristicScale(final Point start, final Point end) {
        int scale;
        for(scale = Main.MAX_SCALE; scale >= Main.MIN_SCALE; scale --) {
            TileXYRectangle outerRect = findRect(start, end, scale, true);
            int W = outerRect.getWidth()+1;
            int H = outerRect.getHeight()+1;
            if(W*H <= maxRectArea) break;
        }
        if(scale < Main.MIN_SCALE) scale = Main.MIN_SCALE;
        return scale;
    }
    
    public static void main(String []args) throws Exception {
        //03131313111232021
        //03131313111232211
        //point=51.51769,-0.128467&point=51.514885,-0.122437
        //51.512749,-0.132136&point=51.516514,-0.12321
        TileSystem tileSystem = Main.getFullTileSystem("berlin_routing");
        //lon1=-0.132136&lat1=51.512749&lon2=-0.12321&lat2=51.516514
        ObstacleCreator obstacleCreator = new ObstacleCreator(tileSystem, false);
        //13.3068932;52.4289273
        //13.3294221;52.4325648
        //52.418335,13.259125&point=52.578854,13.510437
        GHPlace start = new GHPlace(52.418335, 13.259125);
        GHPlace end = new GHPlace(52.578854,13.510437);
        /*Point startP = obstacleCreator.geometryFactory.createPoint(new Coordinate(start.lon, start.lat));
        Point endP = obstacleCreator.geometryFactory.createPoint(new Coordinate(end.lon, end.lat));
        int scale = obstacleCreator.findHeuristicScale(startP, endP);
        System.out.println("scale="+(scale));*/
        long time1 = System.nanoTime();
        Obstacle obstacle = obstacleCreator.getObstacle(start, end,15);
        long time2 = System.nanoTime();
        System.out.println(obstacle.getRect());
        System.out.println(obstacle.getAlpha());
        System.out.println((time2-time1)/1e6 + " ms");
    }
}

// Speed Estimation Strategy
interface SpeedEstimator {
    public abstract Double estimateSpeed(final TileXYRectangle obstacle, final int scale);
}

class RawEstimator implements SpeedEstimator {
    private final TileSystem tileSystem;

    public RawEstimator(final TileSystem tileSystem) {
        this.tileSystem = tileSystem;
    }
    
    @Override
    public Double estimateSpeed(final TileXYRectangle rect, final int scale) {
        double speed = 0.;
        for(int i = rect.getLowerCorner().getX(); i <= rect.getUpperCorner().getX(); i ++)
            for(int j = rect.getLowerCorner().getY(); j <= rect.getUpperCorner().getY(); j ++) {
                Tile tile = tileSystem.getTile(i, j, scale);
                //if(tile == null) continue;
                double maxSpeed = tile==null || tile.getUserObject()==null? 0: (double) tile.getUserObject();
                if(maxSpeed > speed)
                    speed = maxSpeed;
            }
        return speed;
    }
}

class FastEstimator implements SpeedEstimator {
    private final TileXYRectangle outerRect;
    private final double[][] speedMatrix;
    
    public FastEstimator(final TileSystem tileSystem, final TileXYRectangle outerRect, final int scale) {
        this.outerRect = outerRect;
        this.speedMatrix = new double[outerRect.getWidth()+1][outerRect.getHeight()+1];
        for(int i = outerRect.getLowerCorner().getX(); i <= outerRect.getUpperCorner().getX(); i ++)
            for(int j = outerRect.getLowerCorner().getY(); j <= outerRect.getUpperCorner().getY(); j ++) {
                Tile tile = tileSystem.getTile(i, j, scale);
                speedMatrix[i - outerRect.getLowerCorner().getX()][j - outerRect.getLowerCorner().getY()] = 
                        tile==null || tile.getUserObject()==null? 0: (double) tile.getUserObject(); 
            } 
    }
    
    @Override
    public Double estimateSpeed(final TileXYRectangle rect, final int scale) {
        double speed = 0.;
        for(int i = rect.getLowerCorner().getX(); i <= rect.getUpperCorner().getX(); i ++)
            for(int j = rect.getLowerCorner().getY(); j <= rect.getUpperCorner().getY(); j ++) {
                double maxSpeed = speedMatrix[i - outerRect.getLowerCorner().getX()][j - outerRect.getLowerCorner().getY()];
                if(maxSpeed > speed)
                    speed = maxSpeed;
            }
        return speed;
    }
}

// a speed estimator which pay attention to the opportunity of using upper level rectangle to increase speed
class ClimberEstimator implements SpeedEstimator {
    private final TileSystem tileSystem;
    
    public ClimberEstimator(final TileSystem tileSystem) {
        this.tileSystem = tileSystem;
    }
    
    @Override
    public Double estimateSpeed(final TileXYRectangle obstacle, final int scale) {
        double speed = 0.;
        int lx = obstacle.getLowerCorner().getX();
        int ly = obstacle.getLowerCorner().getY();
        int ux = obstacle.getUpperCorner().getX();
        int uy = obstacle.getUpperCorner().getY();
        int w = ux-lx+1;
        BitSet bitSet = new BitSet(w*(uy-ly+1));
        
        for(int i = lx; i <= ux; i ++)
            for(int j = ly; j <= uy; j ++) {
                if(bitSet.get((i-lx)*w+j-ly)) continue;
                
                int s = 0;
                for(int k = i, l = j; k % 2 == 0 && l % 2 == 0 && i+(2<<s) <= ux+1 && j+(2<<s) <= uy+1; l /= 2, k /= 2) s ++;
                if(scale - s < Main.MIN_SCALE) s = scale - Main.MIN_SCALE;
                int pow = 1 << s;
                for(int k = 0; k < pow; k ++)
                    for(int l = 0; l < pow; l ++)
                        bitSet.set((k+i-lx)*w+l+j-ly);
                Tile tile = tileSystem.getTile(i/pow, j/pow, scale-s);
                
                //if(tile == null) continue;
                double maxSpeed = tile==null || tile.getUserObject()==null? 0: (double) tile.getUserObject();
                if(maxSpeed > speed)
                    speed = maxSpeed;
                //if(print)
                    //System.out.println("rect: " + i/pow + " "+ j/pow +" "+ (scale-s));
            }
        return speed;
    }    
}