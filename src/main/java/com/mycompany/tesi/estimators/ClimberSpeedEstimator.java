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

import com.mycompany.tesi.Main;
import com.mycompany.tesi.beans.Tile;
import com.mycompany.tesi.beans.TileXYRectangle;
import com.mycompany.tesi.utils.TileSystem;
import java.util.BitSet;

/**
 * A speed estimator which pay attention to the opportunity of using upper level rectangle to increase speed
 * @author Tommaso
 */
public class ClimberSpeedEstimator implements ISpeedEstimator {
    private final TileSystem tileSystem;
    
    public ClimberSpeedEstimator(final TileSystem tileSystem) {
        this.tileSystem = tileSystem;
    }
    
    @Override
    public Double estimateSpeed(final TileXYRectangle obstacle, final int scale) {
        double speed = 0.;
        int lx = obstacle.getLowerCorner().getX();
        int ly = obstacle.getLowerCorner().getY();
        int ux = obstacle.getUpperCorner().getX();
        int uy = obstacle.getUpperCorner().getY();
        int w = ux-lx+1;
        BitSet bitSet = new BitSet(w*(uy-ly+1));
        
        for(int i = lx; i <= ux; i ++)
            for(int j = ly; j <= uy; j ++) {
                if(bitSet.get((i-lx)*w+j-ly)) continue;
                
                int s = 0;
                for(int k = i, l = j; k % 2 == 0 && l % 2 == 0 && i+(2<<s) <= ux+1 && j+(2<<s) <= uy+1; l /= 2, k /= 2) s ++;
                if(scale - s < Main.MIN_SCALE) s = scale - Main.MIN_SCALE;
                int pow = 1 << s;
                for(int k = 0; k < pow; k ++)
                    for(int l = 0; l < pow; l ++)
                        bitSet.set((k+i-lx)*w+l+j-ly);
                Tile tile = tileSystem.getTile(i/pow, j/pow, scale-s);
                
                //if(tile == null) continue;
                double maxSpeed = tile==null || tile.getUserObject()==null? 0: (double) tile.getUserObject();
                if(maxSpeed > speed)
                    speed = maxSpeed;
                //if(print)
                    //System.out.println("rect: " + i/pow + " "+ j/pow +" "+ (scale-s));
            }
        return speed;
    }    
}