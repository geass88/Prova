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

import com.graphhopper.routing.util.VehicleEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Tommaso
 */
public class MyCarFlagEncoder extends RawEncoder implements VehicleEncoder {

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

    public MyCarFlagEncoder(int maxSpeed) {
        super(maxSpeed);
    }
    
    @Override
    public int flags(double speed, boolean bothDir) {
        byte dir = bothDir? BOTH: FORWARD;
        speeds.add(speed);
        return ((speeds.size() - 1) << 2) | dir;
    }
    /*
    @Override
    public int getSpeed(int flags) {
        return (int) Math.round(getSpeedHooked(flags));
    }*/
    
    @Override
    public double getSpeedHooked(int flags) {
        return speeds.get(flags >>> 2);
    }
}
