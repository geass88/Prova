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

import java.io.Serializable;
import org.geotoolkit.geometry.Envelope2D;

/**
 *
 * @author Tommaso
 */
public class Obstacle implements Serializable {
    
    //private Envelope2D rect;
    private Double alpha;
    private int grainScale;
    private TileXYRectangle rect;

    public Obstacle(TileXYRectangle rect, Double alpha, int grainScale) {
        this.rect = rect;
        this.alpha = alpha;
        this.grainScale = grainScale;
    }

    public int getGrainScale() {
        return grainScale;
    }

    public void setGrainScale(int grainScale) {
        this.grainScale = grainScale;
    }

    public TileXYRectangle getRect() {
        return rect;
    }

    public void setRect(TileXYRectangle rect) {
        this.rect = rect;
    }

    public Double getAlpha() {
        return alpha;
    }

    public void setAlpha(Double alpha) {
        this.alpha = alpha;
    } 
}
