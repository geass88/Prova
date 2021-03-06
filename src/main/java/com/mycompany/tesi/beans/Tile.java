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

import com.vividsolutions.jts.geom.Polygon;
import java.io.Serializable;
import org.geotoolkit.geometry.Envelope2D;
import org.geotoolkit.geometry.jts.JTS;

/**
 *
 * @author Tommaso
 */
public class Tile implements Serializable {
    
    private Envelope2D rect;
    private Object userObject;
    private Polygon polygon;
    
    public Tile(final Envelope2D rect) {
        this.rect = rect;
        this.polygon = JTS.toGeometry(rect);
    }

    public Envelope2D getRect() {
        return rect;
    }

    public void setRect(Envelope2D rect) {
        this.rect = rect;
    }
    
    public Polygon getPolygon() {
        //return JTS.toGeometry(rect);
        return polygon;
    }

    public Object getUserObject() {
        return userObject;
    }

    public void setUserObject(Object userObject) {
        this.userObject = userObject;
    }
    
}
