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
package com.mycompany.tesi.obstacles;

import com.mycompany.tesi.Main;
import com.mycompany.tesi.beans.Obstacle;
import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.beans.TileXY;
import com.mycompany.tesi.beans.TileXYRectangle;
import com.mycompany.tesi.estimators.FastSpeedEstimator;
import com.mycompany.tesi.estimators.ISpeedEstimator;
import com.mycompany.tesi.utils.TileSystem;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import org.geotoolkit.geometry.Envelope2D;

/**
 *
 * @author Tommaso
 */
public class ObstacleCreatorNew extends ObstacleCreator {
    
    private TileXYRectangle limitRect;
    private final int scale;
    private final static Logger logger = Logger.getLogger(ObstacleCreatorNew.class.getName());
        
    public ObstacleCreatorNew(int maxSpeed, final TileSystem tileSystem, final ISpeedEstimator estimator, final double maxAlpha, final int maxRectArea, final String dbName, final int scale) {
        super(maxSpeed, tileSystem, estimator, maxAlpha, maxRectArea);
        this.scale = scale;
        Envelope2D limit = Main.getBound(dbName);
        Point lc = geometryFactory.createPoint(new Coordinate(limit.getLowerCorner().getX(), limit.getLowerCorner().getY()));
        Point uc = geometryFactory.createPoint(new Coordinate(limit.getUpperCorner().getX(), limit.getUpperCorner().getY()));
        limitRect = findRect(lc, uc, scale, true);
        this.estimator = new FastSpeedEstimator(tileSystem, limitRect, scale);
    }
    
    @Override
    public Obstacle getObstacle(final Point start, final Point end) {
        return getObstacle(start, end, scale);
    }            
    
    @Override
    public Obstacle getObstacle(final Point start, final Point end, int scale) {
        scale = this.scale;
        TileXYRectangle outerRect = limitRect; //findRect(start, end, scale, true);
        TileXYRectangle innerRect = findRect(start, end, scale, true);
        if(innerRect == null) return null;
        ISpeedEstimator localEstimator = this.estimator == null? new FastSpeedEstimator(tileSystem, outerRect, scale): this.estimator;
        //long time1 = System.nanoTime();
        List<TileXY> seeds = listSeeds(innerRect, start, end, scale);
        //long time2 = System.nanoTime();
        //System.out.println((time2-time1)*1e-6);
        Set<TileXYRectangle> obstacles = new TreeSet<>();
        /*for(TileXY seed: seeds) {
            obstacles.addAll(buildRect(innerRect, seed));
        }*/
                
        TileXYRectangle bestObstacle = null;
        double bestQ = 0., alphaObstacle = 0.;
        double outsideSpeed = MAX_SPEED;//estimator.estimateSpeed(outerRect, scale);
        double maxArea = (outerRect.getWidth()+1)*(outerRect.getHeight()+1)/100.;
        /*Geometry startPoint = null;
        try {
            startPoint = JTS.transform(start, DefaultCRS.geographicToProjectedTr);
        } catch (MismatchedDimensionException | TransformException ex) {
            logger.log(Level.SEVERE, null, ex);
            return null;
        }*/
        for(TileXY obs: seeds)
            for(int l_sx = obs.getX(); l_sx >= limitRect.getLowerCorner().getX(); l_sx --)
                for(int l_dx = obs.getX(); l_dx <= limitRect.getUpperCorner().getX(); l_dx ++)
                    for(int l_up = obs.getY(); l_up <= limitRect.getUpperCorner().getY(); l_up ++)
                        for(int l_dw = obs.getY(); l_dw >= limitRect.getLowerCorner().getY(); l_dw --) {
                            TileXYRectangle obstacle = new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw);
                            if(obstacles.contains(obstacle)) continue;
                            obstacles.add(obstacle);
                            double insideSpeed = localEstimator.estimateSpeed(obstacle, scale);
                            double alpha = insideSpeed/outsideSpeed;
                            //double ok = alpha < maxAlpha? 1: 0;
                            if(alpha >= maxAlpha) continue;
                            double alphaInv = alpha == 0? MAX_SPEED: 1/alpha;
                            int W = obstacle.getWidth() + 1, H = obstacle.getHeight() + 1;
                            /*double distance=0.;
                            try {
                                distance = startPoint.distance(JTS.transform(extractGeometricRect(obstacle, scale), DefaultCRS.geographicToProjectedTr));
                            } catch (MismatchedDimensionException | TransformException ex) {
                                logger.log(Level.SEVERE, null, ex);
                            }
                            if(distance == 0) distance = 1.;
                            double quality = W*H/maxArea + alphaInv/1.3 +100./distance;*/ 
                            double quality = W*H/maxArea + alphaInv/1.3; // ok * (W*H);
                            //double quality = W * H * alphaInv;
                            //double quality = quality(outerRect, obstacle, scale);
                            if(quality > bestQ) {
                                bestObstacle = obstacle;
                                bestQ = quality;
                                alphaObstacle = alpha;
                                //System.out.println(distance);
                            }
                        }
        //quality1(bestObstacle, scale, outsideSpeed, true);
                
        return new Obstacle(bestObstacle, alphaObstacle, scale);
    }
        
    @Override
    public Obstacle grow(final Obstacle seedObstacle, final double newMaxAlpha) {
        return grow(seedObstacle, limitRect, newMaxAlpha);
    }
    
    @Override
    public Obstacle grow(final Obstacle seedObstacle, final TileXYRectangle limit, final double newMaxAlpha) {
        final TileXYRectangle seed = seedObstacle.getRect();
        int scale = seedObstacle.getGrainScale();
        
        TileXYRectangle bestObstacle = null;
        double bestQ = 0., alphaObstacle = 0.;
        double outsideSpeed = MAX_SPEED; //estimator.estimateSpeed(outerRect, scale);
        double maxArea = (limit.getWidth()+1)*(limit.getHeight()+1)/100.;
        for(int l_sx = seed.getLowerCorner().getX(); l_sx >= limit.getLowerCorner().getX(); l_sx --)
                for(int l_dx = seed.getUpperCorner().getX(); l_dx <= limit.getUpperCorner().getX(); l_dx ++)
                    for(int l_up = seed.getUpperCorner().getY(); l_up <= limit.getUpperCorner().getY(); l_up ++)
                        for(int l_dw = seed.getLowerCorner().getY(); l_dw >= limit.getLowerCorner().getY(); l_dw --) {
                            TileXYRectangle obstacle = new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw);
                            double insideSpeed = this.estimator.estimateSpeed(obstacle, scale);
                            double alpha = insideSpeed/outsideSpeed;
                            //double ok = alpha < newMaxAlpha? 1: 0;
                            if(alpha >= newMaxAlpha) continue;
                            double alphaInv = alpha == 0? MAX_SPEED: 1/alpha;
                            int W = obstacle.getWidth() + 1, H = obstacle.getHeight() + 1;
                            double quality = W*H/maxArea + alphaInv/1.3; // ok * (W*H);
                            //double quality = W * H * alphaInv;
                            //double quality = quality(outerRect, obstacle, scale);
                            if(quality > bestQ) {
                                bestObstacle = obstacle;
                                bestQ = quality;
                                alphaObstacle = alpha;
                            }
                        }
        return new Obstacle(bestObstacle, alphaObstacle, scale);
    }
    
    public static void main(String []args) {
        int scale=14;
        double alpha = 0.4;
        List<Obstacle> set = new LinkedList<>();
        Set<TileXYRectangle> set1 = new TreeSet<>();
        ObstacleCreatorNew creator = null;
        try {
            String db = Main.DBS[0];
            TileSystem system = Main.getFullTileSystem(db);
            creator = new ObstacleCreatorNew(Main.getMaxSpeed(db), system, null, alpha, 100, Main.DBS[0], scale);
            for(Tile t : system.visit(scale)) {
                if(t == null || t.getUserObject() == null) continue;
                double vR = (Double)t.getUserObject();
                TileXY seed = system.pointToTileXY((t.getRect().getUpperCorner().getX() + t.getRect().getLowerCorner().getX())/2., (t.getRect().getUpperCorner().getY() + t.getRect().getLowerCorner().getY())/2., scale);
                if(vR/130. <= alpha) {
                    TileXYRectangle seedR=new TileXYRectangle(seed.getX(), seed.getY(), 0,0);
                    Obstacle o = creator.grow(new Obstacle(seedR, vR/130., scale), alpha);
                    if(o!=null && o.getRect()!=null && !set1.contains(o.getRect())) {
                        set1.add(o.getRect());
                        set.add(o);
                    }
                    if(o!=null && o.getRect()==null && !set1.contains(seedR)) {
                        set1.add(seedR);
                        set.add(new Obstacle(seedR, vR/130., scale));
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        try(Connection conn = Main.getConnection(Main.DBS[0]); Statement s = conn.createStatement(); PreparedStatement st = conn.prepareStatement("insert into ostacoli(x1,y1,x2,y2,alpha,scale_grain) values(?, ?, ?, ?,?,?)")) {
            s.executeUpdate("truncate table ostacoli;");
            for(Obstacle o: set) {
                st.clearParameters();
                Envelope2D p = creator.extractEnvelope(o);
                st.setDouble(1, p.getLowerCorner().getX());
                st.setDouble(2, p.getLowerCorner().getY());
                st.setDouble(3, p.getUpperCorner().getX());
                st.setDouble(4, p.getUpperCorner().getY());
                st.setDouble(5, o.getAlpha());
                st.setInt(6, o.getGrainScale());
                st.executeUpdate();
                System.out.println(o.getRect());
                
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
