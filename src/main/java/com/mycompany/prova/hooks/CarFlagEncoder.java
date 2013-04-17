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

import com.graphhopper.routing.util.CombinedEncoder;
import com.graphhopper.routing.util.VehicleEncoder;
import com.mycompany.prova.beans.Speed;
import java.util.ArrayList;

/**
 *
 * @author Tommaso
 */
public class CarFlagEncoder implements VehicleEncoder {

    // km/h
    private ArrayList<Speed> speeds = new ArrayList<>();
    public final CombinedEncoder COMBINED_ENCODER = new CombinedEncoder() {
        @Override
        public int swapDirection(int flags) {
            byte dir = speeds.get(flags).getDir();
            if(dir != Speed.BOTH)
                speeds.get(flags).setDir((byte)(dir ^ Speed.BOTH));
            return flags;
        }
    };
    
    @Override
    public int flags(int speed, boolean bothDir) {
        speeds.add(new Speed(bothDir? Speed.BOTH: Speed.FORWARD, speed));
        return speeds.size() - 1;
    }
        
    @Override
    public boolean isForward(int flags) {
        return (speeds.get(flags).getDir() & Speed.FORWARD) != 0;
    }

    @Override
    public boolean isBackward(int flags) {
        return (speeds.get(flags).getDir() & Speed.BACKWARD) != 0;
    }

    @Override
    public int getMaxSpeed() {
        return 130;
    }

    @Override
    public boolean canBeOverwritten(int flags1, int flags2) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getSpeed(int flags) {
        return (int) Math.round(speeds.get(flags).getValue());
    }
    
    public double getSpeedHooked(int flags) {
        return speeds.get(flags).getValue();
    }
    
}
