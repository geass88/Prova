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

import com.mycompany.tesi.utils.ConnectionPool;
import com.mycompany.tesi.utils.TileSystem;
import java.io.Serializable;

/**
 *
 * @author Tommaso
 */
public class DBData implements Serializable {
    
    private TileSystem tileSystem;
    private ConnectionPool connectionPool;
    private int maxSpeed;
    public final static int DEFAULT_SPEED = 130;
    
    public DBData() {
        this.tileSystem = null;
        this.maxSpeed = DEFAULT_SPEED;
        this.connectionPool = null;
    }

    public DBData(TileSystem tileSystem, ConnectionPool dataSource, int maxSpeed) {
        this.tileSystem = tileSystem;
        this.connectionPool = dataSource;
        this.maxSpeed = maxSpeed;
    }
    
    public TileSystem getTileSystem() {
        return tileSystem;
    }

    public void setTileSystem(TileSystem tileSystem) {
        this.tileSystem = tileSystem;
    }

    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    public void setConnectionPool(ConnectionPool dataSource) {
        this.connectionPool = dataSource;
    }

    public int getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(int maxSpeed) {
        this.maxSpeed = maxSpeed;
    }   
}
