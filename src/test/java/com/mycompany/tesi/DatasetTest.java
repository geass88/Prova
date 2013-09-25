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
package com.mycompany.tesi;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperAPI;
import com.graphhopper.storage.GraphStorage;
import com.graphhopper.util.shapes.GHPlace;
import com.mycompany.tesi.hooks.FastestCalc;
import com.mycompany.tesi.hooks.MyCarFlagEncoder;
import com.mycompany.tesi.hooks.RawEncoder;
import com.mycompany.tesi.utils.GraphHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tommaso
 */
public class DatasetTest extends TestCase {
    
    private GraphStorage graph;
    private RawEncoder vehicle;
    
    public DatasetTest(String name) {
        super(name);
    }
    
    @Before
    @Override
    public void setUp() {
        Main.TEST = true;
        String db = "berlin_routing";
        vehicle = new MyCarFlagEncoder(Main.getMaxSpeed(db));
        graph = GraphHelper.readGraph(db, "ways", vehicle);
    }
    
    @After
    @Override
    public void tearDown() {
        graph.close();
    }
    
    @Test
    public void testDataset() throws Exception {
        File file = new File("data/BerlinSourceTarget");
        assertTrue(file.exists());
        GraphHopperAPI instance = new GraphHopper(graph).forDesktop();
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String s; reader.readLine();
            while((s=reader.readLine()) != null) {
                String[] tokens = s.split(",");
                Integer source = Integer.valueOf(tokens[0]);
                Integer target = Integer.valueOf(tokens[1]);
                GHResponse ph = instance.route(new GHRequest(new GHPlace(graph.getLatitude(source), graph.getLongitude(source)), 
                        new GHPlace(graph.getLatitude(target), graph.getLongitude(target))).algorithm("dijkstrabi").type(new FastestCalc(vehicle)).vehicle(vehicle));
               // System.out.println(x);
                if(!ph.found())
                    ++count;
            }
        }
        System.out.println(count);
    }
}