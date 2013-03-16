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
package com.mycompany.prova;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;
import org.geotoolkit.referencing.CRS;
import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

/**
 *
 * @author Tommaso
 */
public class DefaultCRS {
    
    public final static CoordinateReferenceSystem geographicCRS = DefaultGeographicCRS.WGS84;
    public final static CoordinateReferenceSystem projectedCRS;
    public final static MathTransform geographicToProjectedTr;
    public final static MathTransform projectedToGeographicTr;
    public final static Envelope2D geographicRect;
    public final static Envelope2D projectedRect;
    
    public final static double MinLatitude = -85.05112878;
    public final static double MaxLatitude = 85.05112878;
    public final static double MinLongitude = -180.;
    public final static double MaxLongitude = 180.;
    
    static {
        CoordinateReferenceSystem crs = null;
        MathTransform tr = null, rtr = null;
        DirectPosition projectedSW = null, projectedNE = null;
        geographicRect = new Envelope2D(new DirectPosition2D(geographicCRS, MinLongitude, MinLatitude), new DirectPosition2D(geographicCRS, MaxLongitude, MaxLatitude));
        try {
            crs = CRS.decode("EPSG:3857"); // Universal Transverse Mercator
            tr = CRS.findMathTransform(geographicCRS, crs);
            rtr = CRS.findMathTransform(crs, geographicCRS); //geographicToGeocentricTr.inverse();
            projectedSW = tr.transform(geographicRect.getLowerCorner(), null);
            projectedNE = tr.transform(geographicRect.getUpperCorner(), null);
        } catch (Exception ex) {
            Logger.getLogger(DefaultCRS.class.getName()).log(Level.SEVERE, null, ex);
        }
        projectedCRS = crs;
        geographicToProjectedTr = tr;
        projectedToGeographicTr = rtr;
        projectedRect = new Envelope2D(projectedSW, projectedNE);
        /*
         * From this point we can convert an arbitrary amount of coordinates using the
         * same MathTransform object. It could be in concurrent threads if we wish.
         */
    }
    
    /**
     * Find the tile that contains a given point
     * @param geographicPoint
     * @param scale
     * @return the tile that contains the point at the specified scale (null if the point isn't in the geographicRect)
     * @throws MismatchedDimensionException
     * @throws TransformException 
     */
    public static TileXY pointToTileXY(final DirectPosition2D geographicPoint, final int scale) throws MismatchedDimensionException, TransformException {
        if(geographicPoint == null || scale < 0 || !geographicRect.contains(geographicPoint)) return null;
        
        DirectPosition projectedPoint = geographicToProjectedTr.transform(geographicPoint, null);
        
        double value = 1 << scale;
        int tileY = (int) Math.floor((projectedPoint.getOrdinate(0) - projectedRect.getLowerCorner().getOrdinate(0)) / projectedRect.getWidth() * value);
        int tileX = (int) Math.floor((projectedRect.getUpperCorner().getOrdinate(1) - projectedPoint.getOrdinate(1)) / projectedRect.getHeight() * value);
        
        return new TileXY(tileX, tileY);
    }
    
}
