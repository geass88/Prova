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

import com.graphhopper.routing.Path;
import com.graphhopper.util.EdgeIterator;

/**
 *
 * @author Tommaso
 */
public class TimeCalculation {
    
    private RawEncoder encoder;
    
    public TimeCalculation(RawEncoder encoder) {
        this.encoder = encoder;
    }
    
    double time;
    public double calcTime(Path path) {
        time = 0.;
        path.forEveryEdge(new Path.EdgeVisitor() {
            @Override
            public void next(EdgeIterator iter) {
                //System.out.println(iter.adjNode() + " " + iter.baseNode()+" " + iter.flags());
                //System.out.println(iter.distance());
                time += iter.distance()*3.6/encoder.getSpeedHooked(iter.flags());
            }
        });
        return time;
    }
    
}
