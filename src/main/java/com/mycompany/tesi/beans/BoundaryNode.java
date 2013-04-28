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

import com.vividsolutions.jts.geom.Point;
import java.io.Serializable;

/**
 *
 * @author Tommaso
 */
public class BoundaryNode implements Comparable<BoundaryNode>, Serializable {
    
    private int nodeId;
    private int roadNodeId;
    private Point point;

    public BoundaryNode() {}
    
    public BoundaryNode(int nodeId, int roadNodeId, Point point) {
        this.nodeId = nodeId;
        this.point = point;
        this.roadNodeId = roadNodeId;
    }

    public int getRoadNodeId() {
        return roadNodeId;
    }

    public void setRoadNodeId(int roadNodeId) {
        this.roadNodeId = roadNodeId;
    }
    
    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }
    
    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }
    
    @Override
    public int compareTo(BoundaryNode o) {
        return (o!=null && this.roadNodeId == o.roadNodeId? 0: 1);
    }
    
}
