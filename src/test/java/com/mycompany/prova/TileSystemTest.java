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

import junit.framework.TestCase;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;
import org.opengis.geometry.DirectPosition;

/**
 *
 * @author Tommaso
 */
public class TileSystemTest extends TestCase {
    
    private final static Envelope2D bound = new Envelope2D(new DirectPosition2D(12, 41.5), new DirectPosition2D(13, 42.5));
    
    public TileSystemTest(String testName) {
        super(testName);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of computeTree method, of class TilesCalculator.
     */
    public void testComputeTree_0args() {
        System.out.println("computeTree");
        TileSystem instance = new TileSystem(bound, 17);
        assertTrue(instance.computeTree());
    }

    /**
     * Test of computeTree method, of class TilesCalculator.
     */
    public void testComputeTree_DirectPosition_DirectPosition() {
        System.out.println("computeTree");
        DirectPosition p1 = DefaultCRS.geographicRect.getLowerCorner();
        DirectPosition p2 = DefaultCRS.geographicRect.getUpperCorner();
        TileSystem instance = new TileSystem(bound, 17);
        assertTrue(instance.computeTree(p1, p2));
    }

    /**
     * Test of getTile method, of class TilesCalculator.
     */
    public void testGetTile_String() {
        System.out.println("getTile");
        String key = "12023222112";
        TileSystem instance = new TileSystem(bound, 17);
        instance.computeTree();
        Envelope2D result = instance.getTile(key).getRect();
        assertNotNull(result);
    }

    /**
     * Test of getTile method, of class TilesCalculator.
     */
    public void testGetTile_TileXY_int() {
        System.out.println("getTile");
        TileXY t = new TileXY(761, 1094);
        int scale = 11;
        TileSystem instance = new TileSystem(bound, 17);
        instance.computeTree();
        Envelope2D result = instance.getTile(t, scale).getRect();
        assertNotNull(result);        
    }

    /**
     * Test of getTile method, of class TilesCalculator.
     */
    public void testGetTile_3args() {
        System.out.println("getTile");
        int x = 761;
        int y = 1094;
        int scale = 11;
        TileSystem instance = new TileSystem(bound, 17);
        instance.computeTree();
        Envelope2D result = instance.getTile(x, y, scale).getRect();
        assertNotNull(result);
    }

    /**
     * Test of pointToTileXY method, of class TilesCalculator.
     */
    public void testPointToTileXY() throws Exception {
        System.out.println("pointToTileXY");
        DirectPosition2D geographicPoint = new DirectPosition2D(DefaultCRS.geographicCRS, 12.42, 41.8445);
        int scale = 11;
        TileSystem instance = new TileSystem(bound, 17);
        instance.computeTree();
        TileXY expResult = new TileXY(761, 1094);
        TileXY result = instance.pointToTileXY(geographicPoint, scale);
        assertEquals(expResult.getX(), result.getX());
        assertEquals(expResult.getY(), result.getY());
    }
    
    /**
     * Test of pointToTileXY method, of class TilesCalculator.
     */
    public void testPointToTile() throws Exception {
        System.out.println("pointToTile");
        DirectPosition2D geographicPoint = new DirectPosition2D(DefaultCRS.geographicCRS, 12.42, 41.8445);
        int scale = 11;
        TileSystem instance = new TileSystem(bound, 17);
        instance.computeTree();
        TileXY tileXY = new TileXY(761, 1094);
        Tile expResult = instance.getTile(tileXY, scale);
        Tile result = instance.pointToTile(geographicPoint, scale);           
        assertEquals(expResult.getRect(), result.getRect());
    }
}
