/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jmetal.problems.TNDP;

/**
 *
 * @author laptop
 */
public class Halifax extends Instance
{
    
    Halifax()
    {
        numOfVertices = 383;
        dir = "IO/Halifax/";
        demandFile = "HalifaxZoneDemand.txt";
        timeFile = "HalifaxTime.txt";
        RouteFile = "HalifaxRoute.txt";
        EdgeListFile = "HalifaxEdgelist.txt";
        ZoneListFile = "HalifaxZone.txt";
        CentroidFile = "HalifaxZoneDistance.txt";
        minNode = 9;
        maxNode = 22;
        name = "Halifax";    
    }
}
