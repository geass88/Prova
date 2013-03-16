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
package com.mycompany.prova;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;
import org.opengis.geometry.DirectPosition;

/**
 *
 * @author Tommaso
 */
public class TilesCalculator {
    
    private final Envelope2D bound;
    private TreeModel tree;
    public final int maxDepth;    

    public TilesCalculator(final Envelope2D bound, final int maxDepth) {
        this.bound = bound;
        this.maxDepth = maxDepth;
        this.tree = null;
    }
    
    public void computeTree(DirectPosition p1, DirectPosition p2) throws Exception {
        this.tree = new DefaultTreeModel(compute(p1, p2, 0));
    }
    
    public Envelope2D getTile(String key) {
        TreeNode node = (TreeNode) tree.getRoot();
        for(int i = 0; i < key.length(); i++)
            node = node.getChildAt(key.charAt(i) - '0');
        
        return (Envelope2D) ((DefaultMutableTreeNode) node).getUserObject();
    }
    
    public Envelope2D getTile(TileXY t, int scale) {
        String key = QuadKeyManager.fromTileXY(t, scale);
        return getTile(key);
    }
    
    public Envelope2D getTile(int x, int y, int scale) {
        return getTile(new TileXY(x, y), scale);
    }
    
    private MutableTreeNode compute(DirectPosition p1, DirectPosition p2, int scale) throws Exception {
        if(scale > maxDepth) return null;
        Envelope2D rect = new Envelope2D(p1, p2);
        if(!rect.intersects(bound)) return new DefaultMutableTreeNode(null); // if(p2.getOrdinate(1)<bound.getMinY() || p2.getOrdinate(0) < bound.getMinX() || p1.getOrdinate(0) > bound.getMaxX() || p1.getOrdinate(1) > bound.getMaxY())
        
        DirectPosition p1m = DefaultCRS.geographicToProjectedTr.transform(p1, null);
        DirectPosition p2m = DefaultCRS.geographicToProjectedTr.transform(p2, null);
        
        DirectPosition m = DefaultCRS.projectedToGeographicTr.transform(new DirectPosition2D((p1m.getOrdinate(0)+p2m.getOrdinate(0))/2., (p1m.getOrdinate(1)+p2m.getOrdinate(1))/2.), null);
        m.setOrdinate(0, round(m.getOrdinate(0)));
        m.setOrdinate(1, round(m.getOrdinate(1)));
        MutableTreeNode parent = new DefaultMutableTreeNode(rect);
        DirectPosition[] dots = {
            new DirectPosition2D(p1.getOrdinate(0), m.getOrdinate(1)), new DirectPosition2D(m.getOrdinate(0), p2.getOrdinate(1)),
            m, p2,
            p1, m,
            new DirectPosition2D(m.getOrdinate(0), p1.getOrdinate(1)), new DirectPosition2D(p2.getOrdinate(0), m.getOrdinate(1))
        };
        for(int i = 0; i < 4; i ++) {
            MutableTreeNode child = compute(dots[i*2], dots[i*2+1], scale + 1);
            if(child != null) parent.insert(child, i);        
        }
        return parent;
    }
    
    
    
    private static double round(double val) {
        return Math.round(val*1e12)/1e12;
    }
}
