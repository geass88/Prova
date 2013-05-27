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
import com.mycompany.tesi.beans.TileXY;
import com.mycompany.tesi.beans.TileXYRectangle;
import com.mycompany.tesi.estimators.FastSpeedEstimator;
import com.mycompany.tesi.estimators.ISpeedEstimator;
import com.mycompany.tesi.utils.TileSystem;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;
import java.util.List;
import java.util.logging.Logger;
import org.geotoolkit.geometry.Envelope2D;

/**
 *
 * @author Tommaso
 */
public class RecursiveObstacleCreator extends ObstacleCreator {
    
    private TileXYRectangle limitRect;
    private final int scale;
    private double bestQ = 0.;
    private TileXYRectangle bestObstacle;
    private double alphaObstacle;
    private double maxArea;
    private final static Logger logger = Logger.getLogger(ObstacleCreatorNew.class.getName());
    
    public RecursiveObstacleCreator(final TileSystem tileSystem, final ISpeedEstimator estimator, final double maxAlpha, final int maxRectArea, final String dbName, final int scale) {
        super(tileSystem, estimator, maxAlpha, maxRectArea);
        this.scale = scale;
        Envelope2D limit = Main.getBound(dbName);
        Point lc = geometryFactory.createPoint(new Coordinate(limit.getLowerCorner().getX(), limit.getLowerCorner().getY()));
        Point uc = geometryFactory.createPoint(new Coordinate(limit.getUpperCorner().getX(), limit.getUpperCorner().getY()));
        limitRect = findRect(lc, uc, scale, true);
        maxArea = (limitRect.getWidth() + 1) * (limitRect.getHeight() + 1);
        this.estimator = new FastSpeedEstimator(tileSystem, limitRect, scale);
    }
    
    protected void growRecursive(final TileXY seed, double maxAlpha){
        growRecursive(seed.getX(), seed.getY(), seed.getX(), seed.getY(), maxAlpha);
    }
    
    protected void growRecursive(final TileXYRectangle seed, double maxAlpha){
        growRecursive(seed.getLowerCorner().getX(), seed.getLowerCorner().getY(), seed.getUpperCorner().getX(), seed.getUpperCorner().getY(), maxAlpha);
    }
    
    protected boolean evaluate(TileXYRectangle rect, double newMaxAlpha) {
        double speed = this.estimator.estimateSpeed(rect, scale);
        double alpha = speed / 130.;
        if(alpha < newMaxAlpha) {
            double alphaInv = alpha == 0? 130: 1/alpha;
            int W = rect.getWidth() + 1, H = rect.getHeight() + 1;
            double quality = W*H/maxArea + alphaInv/1.3; // ok * (W*H);
            //double quality = W * H * alphaInv;
            //double quality = quality(outerRect, obstacle, scale);
            if(quality > bestQ) {
                bestObstacle = rect;
                bestQ = quality;
                alphaObstacle = alpha;
            }
            return true;
        }
        return false;
    }
    /*
    protected Double evaluate(TileXYRectangle partialRect, TileXYRectangle rect, double newMaxAlpha, double oldMaxSpeed) {
        double speed = Math.max(oldMaxSpeed, this.estimator.estimateSpeed(partialRect, scale));
        double alpha = speed / 130.;
        if(alpha < newMaxAlpha) {
            double alphaInv = alpha == 0? 130: 1/alpha;
            int W = rect.getWidth() + 1, H = rect.getHeight() + 1;
            double quality = W*H/maxArea + alphaInv/1.3; // ok * (W*H);
            //double quality = W * H * alphaInv;
            //double quality = quality(outerRect, obstacle, scale);
            if(quality > bestQ) {
                bestObstacle = rect;
                bestQ = quality;
                alphaObstacle = alpha;
            }
            return speed;
        }
        return null;
    }
    protected void growRecursiveNew(final TileXY seed, double maxAlpha){
        growRecursiveNew(seed.getX(), seed.getY(), seed.getX(), seed.getY(), maxAlpha,
                    this.estimator.estimateSpeed(new TileXYRectangle(seed.getX(), seed.getY(), 0, 0), scale));
    }
    protected void growRecursiveNew(int l_sx, int l_dw, int l_dx, int l_up, final double newMaxAlpha, double oldSpeed) {
        //System.out.println(l_sx + " "+ l_dx + " " + l_up + " " + l_dw);
        l_sx --;
        if(l_sx >= this.limitRect.getLowerCorner().getX()) {
            TileXYRectangle rect = new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw);
            Double speed;
            if((speed=evaluate(new TileXYRectangle(l_sx, l_dw, 1, l_up - l_dw), rect, newMaxAlpha, oldSpeed)) != null)
                growRecursiveNew(l_sx, l_dw, l_dx, l_up, newMaxAlpha, speed);
        }
        l_sx ++;
        
        l_dx ++;
        if(l_dx <= this.limitRect.getUpperCorner().getX()) {
            TileXYRectangle rect = new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw);
            Double speed;
            if((speed=evaluate(new TileXYRectangle(l_dx-1, l_dw, 1, l_up - l_dw), rect, newMaxAlpha, oldSpeed)) != null)
                growRecursiveNew(l_sx, l_dw, l_dx, l_up, newMaxAlpha, speed);
        }        
        l_dx --;
        
        l_up ++;
        if(l_up <= this.limitRect.getUpperCorner().getY()) {
            TileXYRectangle rect = new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw);
            Double speed;
            if((speed=evaluate(new TileXYRectangle(l_sx, l_up-1, l_dx - l_sx, 1), rect, newMaxAlpha, oldSpeed)) != null)
                growRecursiveNew(l_sx, l_dw, l_dx, l_up, newMaxAlpha, speed);
        }
        l_up --;
        
        l_dw --;
        if(l_dw >= this.limitRect.getLowerCorner().getY()) {
            TileXYRectangle rect = new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw);
            Double speed;
            if((speed=evaluate(new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, 1), rect, newMaxAlpha, oldSpeed)) != null)
                growRecursiveNew(l_sx, l_dw, l_dx, l_up, newMaxAlpha, speed);
        }
        l_dw ++;
    }
    */
    protected void growRecursive(int l_sx, int l_dw, int l_dx, int l_up, final double newMaxAlpha) {
        //System.out.println(l_sx + " "+ l_dx + " " + l_up + " " + l_dw);
        l_sx --;
        if(l_sx >= this.limitRect.getLowerCorner().getX()) {
            TileXYRectangle rect = new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw);
            if(evaluate(rect, newMaxAlpha))
                growRecursive(l_sx, l_dw, l_dx, l_up, newMaxAlpha);
        }
        l_sx ++;
        
        l_dx ++;
        if(l_dx <= this.limitRect.getUpperCorner().getX()) {
            TileXYRectangle rect = new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw);
            if(evaluate(rect, newMaxAlpha))
                growRecursive(l_sx, l_dw, l_dx, l_up, newMaxAlpha);
        }        
        l_dx --;
        
        l_up ++;
        if(l_up <= this.limitRect.getUpperCorner().getY()) {
            TileXYRectangle rect = new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw);
            if(evaluate(rect, newMaxAlpha))
                growRecursive(l_sx, l_dw, l_dx, l_up, newMaxAlpha);
        }
        l_up --;
        
        l_dw --;
        if(l_dw >= this.limitRect.getLowerCorner().getY()) {
            TileXYRectangle rect = new TileXYRectangle(l_sx, l_dw, l_dx - l_sx, l_up - l_dw);
            if(evaluate(rect, newMaxAlpha))
                growRecursive(l_sx, l_dw, l_dx, l_up, newMaxAlpha);
        }
        l_dw ++;
    }
    
    @Override
    public Obstacle getObstacle(final Point start, final Point end, int scale) {
        scale = this.scale;
        //TileXYRectangle outerRect = limitRect; //findRect(start, end, scale, true);
        TileXYRectangle innerRect = findRect(start, end, scale, false);
        if(innerRect == null) return null;
        //ISpeedEstimator localEstimator = this.estimator == null? new FastSpeedEstimator(tileSystem, outerRect, scale): this.estimator;
        //long time1 = System.nanoTime();
        List<TileXY> seeds = listSeeds(innerRect, start, end, scale);
        //long time2 = System.nanoTime();
        //System.out.println((time2-time1)*1e-6);
        //Set<TileXYRectangle> obstacles = new TreeSet<>();
        /*for(TileXY seed: seeds) {
            obstacles.addAll(buildRect(innerRect, seed));
        }*/
        //limitRect = innerRect;
        bestQ = 0.;
        bestObstacle = null;
        for(TileXY obs: seeds)
            growRecursive(obs, maxAlpha);
        return new Obstacle(bestObstacle, alphaObstacle, scale);
    }
    
    @Override
    public Obstacle grow(final Obstacle seedObstacle, final double newMaxAlpha) {
        bestQ = 0.;
        bestObstacle = null;
        growRecursive(seedObstacle.getRect(), newMaxAlpha);
        return new Obstacle(bestObstacle, alphaObstacle, scale);
    }
            
}
