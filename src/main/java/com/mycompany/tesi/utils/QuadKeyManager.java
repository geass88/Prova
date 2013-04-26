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

/**
 *
 * @author Tommaso
 */
public class QuadKeyManager {
    
    public static String fromTileXY(final TileXY tile, final int scale) {
        if(tile == null || scale < 0) return null;
        StringBuilder quadKey = new StringBuilder();
        for (int i = scale; i > 0; i--) {
            char digit = '0';
            int mask = 1 << (i - 1);
            if ((tile.getX() & mask) != 0)
                digit += 2;

            if ((tile.getY() & mask) != 0)
                digit ++;
            quadKey.append(digit);
        }
        return quadKey.toString();   
    }
    
    public static TileXY toTileXY(final String quadKey) throws Exception {
        if(quadKey == null) return null;
        int tileX = 0, tileY = 0;
        int scale = quadKey.length();
        for (int i = scale; i > 0; i--) {
            int mask = 1 << (i - 1);
            switch (quadKey.charAt(scale - i)) {
                case '0':
                    break;

                case '1':
                    tileY |= mask;
                    break;

                case '2':
                    tileX |= mask;
                    break;

                case '3':
                    tileX |= mask;
                    tileY |= mask;
                    break;

                default:
                    throw new Exception("Invalid QuadKey digit sequence.");
            }
        }
        return new TileXY(tileX, tileY);
    }
}
