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
package com.mycompany.tesi.estimators;

import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.beans.TileXYRectangle;
import com.mycompany.tesi.utils.TileSystem;

/**
 *
 * @author Tommaso
 */
public class FastSpeedEstimator implements ISpeedEstimator {
    private final TileXYRectangle outerRect;
    private final double[][] speedMatrix;
    
    public FastSpeedEstimator(final TileSystem tileSystem, final TileXYRectangle outerRect, final int scale) {
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
