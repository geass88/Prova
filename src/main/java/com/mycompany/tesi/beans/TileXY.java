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

/**
 *
 * @author Tommaso
 */
public class TileXY implements Serializable, Comparable<TileXY> {
 
    private int x;
    private int y;
    
    public TileXY() {
        this.x = this.y = 0;
    }
    
    public TileXY(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
    
    @Override
    public String toString() {
        return String.format("(%d, %d)", x, y);
    }

    @Override
    public int compareTo(final TileXY o) {
        return (o!=null && o.x == x && o.y == y? 0: 1);
    }
    
}
