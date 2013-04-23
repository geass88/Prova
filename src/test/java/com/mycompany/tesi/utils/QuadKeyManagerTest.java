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
import junit.framework.TestCase;

/**
 *
 * @author Tommaso
 */
public class QuadKeyManagerTest extends TestCase {
    
    public QuadKeyManagerTest(String testName) {
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
     * Test of fromTileXY method, of class QuadKeyManager.
     */
    public void testFromTileXY() {
        System.out.println("fromTileXY");
        TileXY tile = new TileXY(10, 20);
        int scale = 10;
        String expResult = "0000012120";
        String result = QuadKeyManager.fromTileXY(tile, scale);
        assertEquals(expResult, result);
        
        tile = new TileXY(1000, 2020);
        scale = 15;
        expResult = "000013333302100";
        result = QuadKeyManager.fromTileXY(tile, scale);
        System.out.println(result);
        assertEquals(expResult, result);
    }

    /**
     * Test of toTileXY method, of class QuadKeyManager.
     */
    public void testToTileXY() throws Exception {
        System.out.println("toTileXY");
        String quadKey = "0000012120";
        TileXY expResult = new TileXY(10, 20);
        TileXY result = QuadKeyManager.toTileXY(quadKey);
        assertEquals(expResult.getX(), result.getX());
        assertEquals(expResult.getY(), result.getY());
        
        quadKey = "000013333302100";
        expResult = new TileXY(1000, 2020);
        result = QuadKeyManager.toTileXY(quadKey);
        assertEquals(expResult.getX(), result.getX());
        assertEquals(expResult.getY(), result.getY());
    }
}
