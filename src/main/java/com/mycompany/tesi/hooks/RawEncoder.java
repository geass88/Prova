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
package com.mycompany.tesi.hooks;

import com.graphhopper.routing.util.CombinedEncoder;
import com.graphhopper.routing.util.VehicleEncoder;

/**
 *
 * @author Tommaso
 */
public class RawEncoder implements VehicleEncoder {
    
    public final static byte FORWARD = 1;
    public final static byte BACKWARD = 2;
    public final static byte BOTH = 3;
    protected int maxSpeed;
    
    public RawEncoder(int maxSpeed) {
        this.maxSpeed = maxSpeed;
    }
    
    @Override
    public int flags(int speed, boolean bothDir) {
        return flags(speed*1.d, bothDir);
    }
    
    public int flags(double speed, boolean bothDir) {
        byte dir = bothDir? BOTH: FORWARD;
        int flags = (((int)(speed*1e6)) << 2) | dir;
        return flags;
    }

    // required for time calculation
    @Override
    public int getSpeed(int flags) {
        return (int) Math.round(getSpeedHooked(flags));//throw new UnsupportedOperationException("Not supported yet."); 
    }

    public double getSpeedHooked(int flags) {
        return (flags >>> 2) / 1e6;
    }
    
    @Override
    public boolean isForward(int flags) {
        return (flags & FORWARD) != 0;
    }

    @Override
    public boolean isBackward(int flags) {
        return (flags & BACKWARD) != 0;
    }

    @Override
    public int getMaxSpeed() {
        return maxSpeed;
    }

    @Override
    public boolean canBeOverwritten(int flags1, int flags2) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    public final static CombinedEncoder COMBINED_ENCODER = new CombinedEncoder() {
        @Override
        public int swapDirection(int flags) {
            if((flags & BOTH) == BOTH) 
                return flags;
            return flags ^ BOTH;
        }
    };
    
}
