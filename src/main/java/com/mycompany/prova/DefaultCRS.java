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
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

/**
 *
 * @author Tommaso
 */
public class DefaultCRS {
    
    public final static CoordinateReferenceSystem geographicCRS = DefaultGeographicCRS.WGS84;
    public final static CoordinateReferenceSystem geocentricCRS;
    public final static MathTransform geographicToGeocentricTr;
    public final static MathTransform geocentricToGeographicTr;
    public final static Envelope2D geographicRect;
    public final static Envelope2D geocentricRect;
    public final static double MinLatitude = -85.05112878;
    public final static double MaxLatitude = 85.05112878;
    public final static double MinLongitude = -180.;
    public final static double MaxLongitude = 180.;
    
    static {
        CoordinateReferenceSystem crs = null;
        MathTransform tr = null, rtr = null;
        DirectPosition geocentricSW = null, geocentricNE = null;
        geographicRect = new Envelope2D(new DirectPosition2D(geographicCRS, MinLongitude, MinLatitude), new DirectPosition2D(geographicCRS, MaxLongitude, MaxLatitude));
        try {
            crs = CRS.decode("EPSG:3857"); // Universal Transverse Mercator
            tr = CRS.findMathTransform(geographicCRS, crs);
            rtr = CRS.findMathTransform(crs, geographicCRS);//geographicToGeocentricTr.inverse();
            geocentricSW = tr.transform(geographicRect.getLowerCorner(), null);
            geocentricNE = tr.transform(geographicRect.getUpperCorner(), null);
        } catch (Exception ex) {
            Logger.getLogger(DefaultCRS.class.getName()).log(Level.SEVERE, null, ex);
        }
        geocentricCRS = crs;
        geographicToGeocentricTr = tr;
        geocentricToGeographicTr = rtr;
        geocentricRect = new Envelope2D(geocentricSW, geocentricNE);
    }
    
    public static int[] pointToTileXY(final DirectPosition2D geographicPoint, int scale) throws MismatchedDimensionException, TransformException {
        DirectPosition geocentricPoint = geographicToGeocentricTr.transform(geographicPoint, null);
        
        double value = 1 << scale;
        int tileY = (int)Math.floor((geocentricPoint.getOrdinate(0) - geocentricRect.getLowerCorner().getOrdinate(0)) / geocentricRect.getWidth() * value);
        int tileX = (int)Math.floor((geocentricRect.getUpperCorner().getOrdinate(1) - geocentricPoint.getOrdinate(1)) / geocentricRect.getHeight() * value);
        
        return new int[] { tileX, tileY };
    }
    
}
