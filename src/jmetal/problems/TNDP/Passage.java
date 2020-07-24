/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jmetal.problems.TNDP;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

/**
 *
 * @author mahib
 */
public class Passage {
    public List<Integer> nodeList;
    public HashMap<Integer, ArrayList<Integer>> zoneList;
    public int shelter;
    public int depot;
    public int intermediateNode;
    public int length;
    public int index;

    public Passage(List<Integer> nodeList, HashMap<Integer, ArrayList<Integer>> zoneList, int index) {
        this.nodeList = new ArrayList<>();
        this.nodeList.clear();
        this.nodeList.addAll(nodeList);
        this.zoneList = new HashMap<Integer, ArrayList<Integer>>();
        this.zoneList.clear();
        this.zoneList.putAll(zoneList);;
        depot = this.nodeList.get(0);
        length = this.nodeList.size();
        shelter = this.nodeList.get(length - 1);
        intermediateNode = this.nodeList.get(length - 2);
        this.index = index;
    }
}
