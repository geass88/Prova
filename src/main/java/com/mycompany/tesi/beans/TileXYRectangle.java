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
package com.mycompany.tesi.beans;

/**
 *
 * @author Tommaso
 */
public class TileXYRectangle {
    
    private TileXY lowerCorner;
    private TileXY upperCorner;

    public TileXYRectangle(int cornerX, int cornerY, int width, int height) {
        int upperCornerX = cornerX + width, upperCornerY = cornerY + height;
        this.lowerCorner = new TileXY(Math.min(cornerX, upperCornerX), Math.min(cornerY, upperCornerY));
        this.upperCorner = new TileXY(Math.max(cornerX, upperCornerX), Math.max(cornerY, upperCornerY));
    }
    
    public TileXYRectangle(final TileXY corner1, final TileXY corner2) {
        this.lowerCorner = new TileXY(Math.min(corner1.getX(), corner2.getX()), Math.min(corner1.getY(), corner2.getY()));
        this.upperCorner = new TileXY(Math.max(corner1.getX(), corner2.getX()), Math.max(corner1.getY(), corner2.getY()));
    }

    public int getWidth() {
        return Math.abs(upperCorner.getX() - lowerCorner.getX());
    }
    
    public int getHeight() {
        return Math.abs(upperCorner.getY() - lowerCorner.getY());
    }
    
    public TileXY getLowerCorner() {
        return lowerCorner;
    }

    public void setLowerCorner(final TileXY lowerCorner) {
        this.lowerCorner = lowerCorner;
    }

    public TileXY getUpperCorner() {
        return upperCorner;
    }

    public void setUpperCorner(final TileXY upperCorner) {
        this.upperCorner = upperCorner;
    }
    
    @Override
    public String toString() {
        return String.format("RECT(%d %d, %d %d)", this.lowerCorner.getX(), 
            this.lowerCorner.getY(), this.upperCorner.getX(), this.upperCorner.getY());
    }
    
}
