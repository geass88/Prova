package com.mycompany.tesi;

import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedList;
import java.util.List;

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

/**
 *
 * @author Tommaso
 */
public class P2PQueryGenerator {
    
    private final static int POINTS_COUNT = 50;
    
    public static void main(String[] args) throws Exception {
        String[] qkey_ll = { "0313131330" }; //"03131313213", "03131313212", "03131313230", "03131313231" };
        String[] qkey_ur = { "1202020023" }; //"1202020030" };
        String[] qkey_ul = { "0313131123" }; //"0313131121"};
        String[] qkey_lr = { "1202020221" }; //"12020202302", "12020202303", "12020202320", "12020202321" };
        
        List<Integer> ll = helper(qkey_ll);
        List<Integer> ur = helper(qkey_ur);
        List<Integer> ul = helper(qkey_ul);
        List<Integer> lr = helper(qkey_lr);
        
        if(ll.size() < POINTS_COUNT || ur.size() < POINTS_COUNT || ul.size() < POINTS_COUNT || lr.size() < POINTS_COUNT) {
            System.out.println("Problem!");
            System.exit(1);
        }
        
        for(List<Integer> list : new List[]{ll, ur, ul, lr})
            while(list.size() > POINTS_COUNT) {
                int value = (int)Math.round(Math.random()*(list.size()-1));
                list.remove(value);
            }
        
        File file = new File("data/EnglandSourceTarget");
        try (PrintStream fout = new PrintStream(file)) {
            fout.println("source,target");
            int i;
            for(i = 0; i < POINTS_COUNT/2; i++)
                fout.println(ll.get(i) + ","+ ur.get(i));
            for(; i < POINTS_COUNT; i ++)
                fout.println(ur.get(i) + ","+ ll.get(i));
            for(i = 0; i < POINTS_COUNT/2; i++)
                fout.println(ul.get(i) + ","+ lr.get(i));
            for(; i < POINTS_COUNT; i ++)
                fout.println(lr.get(i) + ","+ ul.get(i));
        }
    }
    
    private static List<Integer> helper(String[] qkeys) throws Exception {
        List<Integer> list = new LinkedList<>();
        try(Connection conn = Main.getConnection("england_routing");
                PreparedStatement st = conn.prepareStatement("select distinct source from ways, tiles where qkey = ANY(?) and " +
                "st_contains(shape, st_setsrid(st_point(x1, y1), 4326))")) {
            st.setArray(1, conn.createArrayOf("varchar", qkeys));
            ResultSet rs;
            rs = st.executeQuery();
            while(rs.next()) 
                list.add(rs.getInt(1));
            rs.close();
            return list;
        }
    }
}
