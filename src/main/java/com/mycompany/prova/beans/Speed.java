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
package com.mycompany.prova.beans;

/**
 *
 * @author Tommaso
 */
public class Speed {
    
    public final static byte BOTH = 3;
    public final static byte FORWARD = 1;
    public final static byte BACKWARD = 2;
    private byte dir;
    private double value;

    public Speed(byte dir, double value) {
        this.dir = dir;
        this.value = value;
    }

    public byte getDir() {
        return dir;
    }

    public void setDir(byte dir) {
        this.dir = dir;
    }
    
    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }
    
}
