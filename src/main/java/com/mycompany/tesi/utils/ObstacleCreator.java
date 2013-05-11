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
import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.beans.TileXY;
import com.mycompany.tesi.beans.TileXYRectangle;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
    private GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final static Logger logger = Logger.getLogger(ObstacleCreator.class.getName());
    
    //public ObstacleCreator() {}
    
    public ObstacleCreator(final TileSystem tileSystem) {
        this.tileSystem = tileSystem;
    }
    
    private TileXYRectangle findRect(final Point start, final Point end, final int scale, boolean outer) {
        try {
            TileXY startTileXY = tileSystem.pointToTileXY(start.getX(), start.getY(), scale);
            TileXY endTileXY = tileSystem.pointToTileXY(end.getX(), end.getY(), scale);
            TileXYRectangle rect = new TileXYRectangle(startTileXY, endTileXY);
            if(outer) return rect;
            int stepX = startTileXY.getX() == endTileXY.getX()? 0: 1;
            int stepY = startTileXY.getY() == endTileXY.getY()? 0: 1;
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
    
    private double quality(final TileXYRectangle rect, final TileXYRectangle obstacle, final int scale) {
        int N = rect.getWidth() + 1, 
            M = rect.getHeight() + 1;
        
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
        double ok = outsideSpeed/insideSpeed > 0.5 ?1:0;
        return ok + N*M;
    }
    
    public Envelope2D getObstacle(final Point start, final Point end, final int scale) {
        TileXYRectangle outerRect = findRect(start, end, scale, true);
        TileXYRectangle innerRect = findRect(start, end, scale, false);
        List<TileXY> seeds = listSeeds(innerRect, start, end, scale);
        Set<TileXYRectangle> obstacles = new TreeSet<>();
        for(TileXY seed: seeds) {
            obstacles.addAll(buildRect(innerRect, seed));
        }
        TileXYRectangle bestObstacle = null;
        double bestQ = 0;
        for(TileXYRectangle obstacle: obstacles) {
            double quality = quality(outerRect, obstacle, scale);
            if(quality > bestQ) {
                bestObstacle = obstacle;
                bestQ = quality;
            }
        }
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
        return envelope;
    }
    
    public Envelope2D getObstacle(final DirectPosition2D start, final DirectPosition2D end, final int scale) {
        return getObstacle(geometryFactory.createPoint(new Coordinate(start.getX(), start.getY())), 
                geometryFactory.createPoint(new Coordinate(end.getX(), end.getY())), scale);
    }
    
    public Envelope2D getObstacle(final GHPlace start, final GHPlace end, final int scale) {
        return getObstacle(geometryFactory.createPoint(new Coordinate(start.lon, start.lat)), 
                geometryFactory.createPoint(new Coordinate(end.lon, end.lat)), scale);
    }
    
    public static void main(String []args) throws Exception {
        //03131313111232021
        //03131313111232211
        //point=51.51769,-0.128467&point=51.514885,-0.122437
        //51.512749,-0.132136&point=51.516514,-0.12321
        TileSystem tileSystem = Main.loadTilesInfo("london_routing");
        //lon1=-0.132136&lat1=51.512749&lon2=-0.12321&lat2=51.516514
        ObstacleCreator obstacleCreator = new ObstacleCreator(tileSystem);
        System.out.println(obstacleCreator.getObstacle(new GHPlace(51.512749,-0.132136), new GHPlace(51.516514,-0.12321), 17));
    }
}
