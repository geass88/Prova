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

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import org.geotoolkit.geometry.DirectPosition2D;
import org.geotoolkit.geometry.Envelope2D;
import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.TransformException;

/**
 *
 * @author Tommaso
 */
public class TileSystem {
    
    public final Envelope2D bound;
    public final int maxDepth;
    private TreeModel tree;
    private Envelope2D projectedRootRect;
    
    public TileSystem(final Envelope2D bound, final int maxDepth) {
        this.bound = bound;
        this.maxDepth = maxDepth;
        this.tree = null;
        this.projectedRootRect = null;
    }
    
    public boolean computeTree() {
        return computeTree(DefaultCRS.geographicRect);
    }
    
    public boolean computeTree(final Envelope2D rect) {
        if(rect == null) return false;
        return computeTree(rect.getLowerCorner(), rect.getUpperCorner());
    }
    
    public boolean computeTree(final DirectPosition lowerCorner, final DirectPosition upperCorner) {
        try {
            this.tree = new DefaultTreeModel(compute(lowerCorner, upperCorner, 0));
            this.projectedRootRect = new Envelope2D(DefaultCRS.geographicToProjectedTr.transform(lowerCorner, null), 
                    DefaultCRS.geographicToProjectedTr.transform(upperCorner, null));            
        } catch (MismatchedDimensionException | TransformException ex) {
            Logger.getLogger(TileSystem.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }
    
    public Tile getTile(final String key) {
        if(key == null) return null;
        TreeNode node = (TreeNode) this.tree.getRoot();
        for(int i = 0; i < key.length(); i++)
            node = node.getChildAt(key.charAt(i) - '0');
        
        return (Tile) ((DefaultMutableTreeNode) node).getUserObject();
    }
    
    public Tile getTile(final TileXY t, final int scale) {
        if(t == null || scale < 0) return null;
        String key = QuadKeyManager.fromTileXY(t, scale);
        return getTile(key);
    }
    
    public Tile getTile(final int x, final int y, final int scale) {
        return getTile(new TileXY(x, y), scale);
    }
    
    /**
     * Find the coordinates of the tile that contains a given point
     * @param geographicPoint
     * @param scale
     * @return the tile that contains the point at the specified scale (null if the point isn't in the geographicRect)
     * @throws MismatchedDimensionException
     * @throws TransformException
     */
    public TileXY pointToTileXY(final DirectPosition2D geographicPoint, final int scale) throws MismatchedDimensionException, TransformException {
        if (geographicPoint == null || scale < 0) /*  || !this.bound.contains(geographicPoint) */
            return null;
        
        DirectPosition projectedPoint = DefaultCRS.geographicToProjectedTr.transform(geographicPoint, null);
        double value = 1 << scale;
        
        int tileY = (int) Math.floor((projectedPoint.getOrdinate(0) - projectedRootRect.getLowerCorner().getOrdinate(0)) / projectedRootRect.getWidth() * value);
        int tileX = (int) Math.floor((projectedRootRect.getUpperCorner().getOrdinate(1) - projectedPoint.getOrdinate(1)) / projectedRootRect.getHeight() * value);
        return new TileXY(tileX, tileY);
    }
    
    public Tile pointToTile(final DirectPosition2D geographicPoint, final int scale) throws Exception {
        return getTile(pointToTileXY(geographicPoint, scale), scale);
    }
    
    private MutableTreeNode compute(final DirectPosition lowerCorner, final DirectPosition upperCorner, final int scale) throws MismatchedDimensionException, TransformException {
        if(scale > maxDepth) return null;
        Envelope2D rect = new Envelope2D(lowerCorner, upperCorner);
        if(!rect.intersects(bound)) return new DefaultMutableTreeNode(null); // if(p2.getOrdinate(1)<bound.getMinY() || p2.getOrdinate(0) < bound.getMinX() || p1.getOrdinate(0) > bound.getMaxX() || p1.getOrdinate(1) > bound.getMaxY())
        
        DirectPosition p1m = DefaultCRS.geographicToProjectedTr.transform(lowerCorner, null);
        DirectPosition p2m = DefaultCRS.geographicToProjectedTr.transform(upperCorner, null);
        
        DirectPosition center = DefaultCRS.projectedToGeographicTr.transform(new DirectPosition2D((p1m.getOrdinate(0)+p2m.getOrdinate(0))/2., (p1m.getOrdinate(1)+p2m.getOrdinate(1))/2.), null);
        center.setOrdinate(0, round(center.getOrdinate(0)));
        center.setOrdinate(1, round(center.getOrdinate(1)));        
        DirectPosition[] dots = {
            new DirectPosition2D(lowerCorner.getOrdinate(0), center.getOrdinate(1)), new DirectPosition2D(center.getOrdinate(0), upperCorner.getOrdinate(1)),
            center, upperCorner,
            lowerCorner, center,
            new DirectPosition2D(center.getOrdinate(0), lowerCorner.getOrdinate(1)), new DirectPosition2D(upperCorner.getOrdinate(0), center.getOrdinate(1))
        };
        MutableTreeNode parent = new DefaultMutableTreeNode(new Tile(rect));
        for(int i = 0; i < 4; i ++) {
            MutableTreeNode child = compute(dots[i*2], dots[i*2+1], scale + 1);
            if(child != null) parent.insert(child, i);        
        }
        return parent;
    }
    
    private static double round(final double val) {
        return Math.round(val*1e12)/1e12;
    }
}
