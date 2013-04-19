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
package com.mycompany.prova.hooks;

import com.graphhopper.routing.util.VehicleEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Tommaso
 */
public class CarFlagEncoder implements VehicleEncoder {

    public final static byte FORWARD = 1;
    public final static byte BACKWARD = 2;
    public final static byte BOTH = 3;
    
    // km/h
    private List<Double> speeds = new ArrayList<>();
    /*public final CombinedEncoder COMBINED_ENCODER = new CombinedEncoder() {
        @Override
        public int swapDirection(int flags) {
            /*byte dir = speeds.get(flags).getDir();
            if(dir != Speed.BOTH)
                speeds.get(flags).setDir((byte)(dir ^ Speed.BOTH));
            return flags;
        }
    };*/
    private int maxSpeed;

    public CarFlagEncoder(int maxSpeed) {
        this.maxSpeed = maxSpeed;
    }
    
    public int flags(double speed, boolean bothDir) {
        byte dir = bothDir? BOTH: FORWARD;
        speeds.add(speed);
        return ((speeds.size() - 1) << 2) | dir;
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
        throw new UnsupportedOperationException("Not supported yet."); //required for CH
    }

    @Override
    public int getSpeed(int flags) {
        return (int) Math.round(speeds.get(flags >>> 2));
    }
    
    public double getSpeedHooked(int flags) {
        return speeds.get(flags >>> 2);
    }

    @Override
    public int flags(int speed, boolean bothDir) {
        throw new UnsupportedOperationException("Not supported yet."); //
        //return flags(speed*1., bothDir);
    }
    
}
