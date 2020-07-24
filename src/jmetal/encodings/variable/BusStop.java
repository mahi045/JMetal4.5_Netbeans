/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jmetal.encodings.variable;

/**
 *
 * @author mahib
 */
public class BusStop {
    public int stopId;
    public boolean isInterval;
    
    public BusStop(int stopId){
        this.stopId = stopId;
        this.isInterval = false;
    }
    void makeStopage() {
        this.isInterval = true;
    }
}
