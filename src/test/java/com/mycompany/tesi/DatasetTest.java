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

import com.graphhopper.storage.GraphStorage;
import com.mycompany.tesi.hooks.MyCarFlagEncoder;
import com.mycompany.tesi.hooks.RawEncoder;
import com.mycompany.tesi.utils.GraphHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    
    public DatasetTest(String name) {
        super(name);
    }
    
    @Before
    @Override
    public void setUp() {
        RawEncoder vehicle = new MyCarFlagEncoder(SubgraphTask.MAX_SPEED);
        graph = GraphHelper.readGraph("hamburg_routing", "ways", vehicle);
    }
    
    @After
    @Override
    public void tearDown() {
        graph.close();
    }
    
    @Test
    public void testDataset() throws Exception {
        File file = new File("HamburgWays");
        assertTrue(file.exists());
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String s; reader.readLine();
            while((s=reader.readLine()) != null) {
                String[] tokens = s.split(",");
                Integer id = Integer.valueOf(tokens[0]);
                double x = Double.valueOf(tokens[1]);
               // System.out.println(x);
                double y = Double.valueOf(tokens[2]);
                if(y != graph.getLatitude(id) || x!= graph.getLongitude(id))
                    count++;
            }
        }
        System.out.println(count);
    }
}