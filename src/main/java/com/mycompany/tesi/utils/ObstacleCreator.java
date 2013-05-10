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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotoolkit.geometry.DirectPosition2D;

/**
 *
 * @author Tommaso
 */
public class ObstacleCreator {
    
    private TileSystem tileSystem;
    private GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final static Logger logger = Logger.getLogger(ObstacleCreator.class.getName());
    
    
    public ObstacleCreator() {}
    
    
    
    
    public List<TileXY> listSeeds(final Point start, final Point end, final int scale) {
        try {
            TileXY startTileXY = tileSystem.pointToTileXY(start.getX(), start.getY(), scale);
            TileXY endTileXY = tileSystem.pointToTileXY(end.getX(), end.getY(), scale);
            TileXYRectangle rect = new TileXYRectangle(startTileXY, endTileXY);
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
    
    public List<TileXY> listSeeds(final DirectPosition2D start, final DirectPosition2D end, final int scale) {
        return listSeeds(geometryFactory.createPoint(new Coordinate(start.getX(), start.getY())), 
                geometryFactory.createPoint(new Coordinate(end.getX(), end.getY())), scale);
    }
    
}
