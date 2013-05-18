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
public class RawSpeedEstimator implements ISpeedEstimator {
    private final TileSystem tileSystem;

    public RawSpeedEstimator(final TileSystem tileSystem) {
        this.tileSystem = tileSystem;
    }
    
    @Override
    public Double estimateSpeed(final TileXYRectangle rect, final int scale) {
        double speed = 0.;
        for(int i = rect.getLowerCorner().getX(); i <= rect.getUpperCorner().getX(); i ++)
            for(int j = rect.getLowerCorner().getY(); j <= rect.getUpperCorner().getY(); j ++) {
                Tile tile = tileSystem.getTile(i, j, scale);
                //if(tile == null) continue;
                double maxSpeed = tile==null || tile.getUserObject()==null? 0: (double) tile.getUserObject();
                if(maxSpeed > speed)
                    speed = maxSpeed;
            }
        return speed;
    }
}
