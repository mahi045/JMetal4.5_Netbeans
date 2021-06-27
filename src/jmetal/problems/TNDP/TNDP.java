/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jmetal.problems.TNDP;
import java.io.*;
import jmetal.util.Configuration;
import jmetal.core.Problem;
import jmetal.core.Solution;
import jmetal.util.JMException;
import grph.Grph;
import grph.in_memory.InMemoryGrph;
import grph.io.EdgeListReader;
import grph.properties.NumericalProperty;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Collections;
import com.google.common.collect.Sets;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import jmetal.core.Variable;
import jmetal.encodings.solutionType.RouteSetSolutionType;
import jmetal.encodings.variable.Route;
import jmetal.encodings.variable.RouteSet;
import jmetal.util.PseudoRandom;
import jmetal.util.DPQ;
import javafx.util.Pair;

/**
 *
 * @author MAN
 */
public class TNDP extends Problem
{

    private int numOfRoutes;
    private HashMap<Integer, int[]> demand;
    private double[][] time;
    private int[][] demandBusStopWise;
    private double[] centroid_distance;
    private int[] zone_ref;
    HashMap<Integer, ArrayList<Integer>> zoneStopMapping = new HashMap<Integer, ArrayList<Integer>>();
    HashMap<Integer, Integer> zoneIndexMapping = new HashMap<Integer, Integer>();
    HashMap<Integer, Integer> bug_fix = new HashMap<Integer, Integer>();
    private double totalDemand;
    public Grph g;
    private NumericalProperty EdgeWeight;
    public Instance ins;
    public int[] shelters = new int[] {381, 382};
    public HashMap<Integer, int []> shelter_immediate_node = new HashMap<Integer, int []>() {{
        put(381, new int [] {169, 218});
        put(382, new int [] {199, 299});    
    }};
    public int [] immediate_node = new int [] {169, 218, 199, 299};
    public HashMap<String, ArrayList<Integer>> all_shortest_paths = new HashMap<String, ArrayList<Integer>>();
    private int fleetSize = 322;
    private double velocity = 8.333;  // it is 30km/hr
    private int called = 0;
    private DPQ graphHelper;
    HashSet<Integer> sharedStops = new HashSet<Integer>();
    HashMap<Integer, ArrayList<Pair<Integer, Integer>>> sharedStopsStatistics = new HashMap<Integer, ArrayList<Pair<Integer, Integer>>>();
    HashMap<Integer, Integer> computedPracticalDelay = new HashMap<Integer, Integer>();
    public TNDP(int _numOfRoutes, Instance _ins) throws Exception
    {
        g = new InMemoryGrph();
        numOfRoutes = _numOfRoutes;
        ins = _ins;
        numberOfVariables_ = 1;
        numberOfObjectives_ = 5;
        numberOfConstraints_ = 2;
        problemName_ = ins.getName() + "-" +_numOfRoutes;
        demand = new HashMap<Integer, int[]>();
        time = new double[ins.getNumOfVertices()][ins.getNumOfVertices()];
        demandBusStopWise = new int[ins.getNumOfVertices()][shelters.length];
        centroid_distance = new double[ins.getNumOfVertices()];
        zone_ref = new int[ins.getNumOfVertices()];
        solutionType_ = new RouteSetSolutionType(this);
        readFromFile(ins.getTimeFile(), time);
        totalDemand = readDemandFromFile(ins.getDemandFile(), demand);
        fixZones(ins.getZoneListFile(), centroid_distance);
        InputStream fml = new FileInputStream(ins.getEdgeListFile());
        EdgeWeight = new NumericalProperty(null, 8, 0);
        EdgeListReader.alterGraph(g, fml, false, false, null);
        graphHelper = new DPQ(g, time);
        // prepareAllShortestPaths();
    }

    public static class OBJECTIVES
    {

        public static final int IVTT = 0, CD = 1, RL = 2, UP = 3, DO = 4;
    }

    @Override
    public void evaluate(Solution solution) throws JMException
    {
        Variable[] var = solution.getDecisionVariables();
        RouteSet rs = (RouteSet) var[0];
        int Vertices = ins.getNumOfVertices();
        double[][] routeDemand = new double[rs.size()][];
        int[] routeMLS = new int[rs.size()];
        double[] theoreticalTime = new double[rs.size()];
        double[] practicalTime = new double[rs.size()];
        computedPracticalDelay.clear();
        ArrayList[][] allPath = new ArrayList[zoneIndexMapping.size()+1][shelters.length];
        HashMap[][] allPathClass = new HashMap[zoneIndexMapping.size()+1][shelters.length];
        // HashMap[][] allPathGroup = new HashMap[Vertices][Vertices];
        int[][] edgeUsage = new int[Vertices][Vertices];
        double[][] edgeFreqSum = new double[Vertices][Vertices];

        double totalRL = 0;
        for (int k = 0; k < rs.size(); k++)
        {
            routeDemand[k] = new double[rs.getRoute(k).size()];
        }
        for (int k = 0; k < ins.getNumOfVertices(); k++)
        {
            demandBusStopWise[k][0] = 0;
            demandBusStopWise[k][1] = 0;
        }
        setSharedStops(rs);
        for (Map.Entry<Integer, ArrayList<Integer>> entry : zoneStopMapping.entrySet())
        {
            for (int j = 0; j < shelters.length; j++)
            {
                ArrayList<Path> paths = generateAllPath(entry.getValue(), shelters[j], rs);
                HashMap<Integer, ArrayList<Path>> pathClass = new HashMap<>();
                // HashMap<String, ArrayList<Path>> pathGroup = new HashMap<>();
//                screenPath(paths, rs);
//                classifyPaths(paths, pathClass/*, pathGroup*/);
                 if (paths.isEmpty()) //unsatisfied
                 {
                     paths = null;
                     pathClass = null;
                     rs.d[1] += demand.get(entry.getKey())[j];
                 } 
                 else
                 {
                     screenPath(paths, rs);
                     classifyPaths(paths, pathClass/*, pathGroup*/);
                     rs.d[paths.get(0).getNumOfSegment() - 1] += demand.get(entry.getKey())[j]; //d0 => 1 segement
                 }
                allPath[getIndex(entry.getKey())][j] = paths;
                allPathClass[getIndex(entry.getKey())][j] = pathClass;
                // allPathGroup[i][j] = pathGroup;

            }
        }
        // do
        // {
            for (int k = 0; k < rs.size(); k++)
            {
                Arrays.fill(routeDemand[k], 0);
            }
            for (Map.Entry<Integer, ArrayList<Integer>> entry : zoneStopMapping.entrySet())
            {
                for (int j = 0; j < shelters.length; j++)
                {
                    if (allPath[getIndex(entry.getKey())][j] != null) {
                        splitDemand(allPath[getIndex(entry.getKey())][j], allPathClass[getIndex(entry.getKey())][j]);
                        assignDemand(allPath[getIndex(entry.getKey())][j], demand.get(entry.getKey())[j], rs, routeDemand);
                    }
                }
            }
        // } while (!findMLS(rs, routeDemand));
        int totalMLS = 0;
        for (int i = 0; i < rs.size(); i++)
        {
            double MLSDemand = routeDemand[i][1];
            int MLS = 1;
            for (int j = 2; j < routeDemand[i].length; j++)
            {
                assert routeDemand[i][j] >= routeDemand[i][j-1]; 
                if (routeDemand[i][j] > MLSDemand)
                {
                    MLS = j;
                    MLSDemand = routeDemand[i][j];
                }
                    
            }
            assert routeDemand[i][routeDemand[i].length - 1] == MLSDemand;
            routeMLS[i] = (int) Math.ceil(MLSDemand);
            totalMLS += (int) Math.ceil(MLSDemand);
            rs.getRoute(i).fleet = 1;
        }
        ArrayList<Integer> random_number_list = new ArrayList<Integer>();
        for (int i = 0; i < fleetSize - rs.size(); i++) {
            random_number_list.add(PseudoRandom.nextInt(totalMLS));
        }
        Collections.sort(random_number_list);
        int r_index = 0;
        int r_index_new = 0;
        int cum_demand = routeMLS[r_index];
        for (Integer e: random_number_list) {
            if (cum_demand < e) {
                while(cum_demand < e) {
                    cum_demand += routeMLS[++r_index];
                }
                    
            }
            r_index_new = r_index;
            // check whether the route need more bus or not
            while(rs.getRoute(r_index_new).fleet * rs.getRoute(r_index_new).getCapacity() >= routeMLS[r_index_new]) {
                r_index_new++;
                if (r_index_new >= rs.size()) {
                    r_index_new = 0;
                }
            }
            rs.getRoute(r_index_new).fleet++;
        }
        // this code is for testing
        int totalfleet = 0;
        for (int i = 0; i < rs.size(); i++)
        {
            totalfleet += rs.getRoute(i).fleet;
        }
        if (totalfleet != fleetSize) {
            System.out.println(totalfleet + " " + fleetSize);
            System.out.println("Accidentally, fleetSize is incorrect");
            throw new Error("Accidentally, fleetSize is incorrect");
        }
        double evacuationTime = Double.MIN_VALUE;
        int tripRequired = 0;
        double RL, timeRequired, del;
        int first_stoping_time = 0, stoping_time, one_way_time = 0;
        for (int k = 0; k < rs.size(); k++)
        {
            tripRequired = (int) Math.ceil((double) routeMLS[k]/rs.getRoute(k).getCapacity());
            rs.getRoute(k).tripRequired = tripRequired;
            assert rs.getRoute(k).tripRequired * rs.getRoute(k).getCapacity() >= routeMLS[k];
            RL = rs.getRoute(k).calculateRouteLength_RoundTrip_edgeOverlap(time, edgeUsage, edgeFreqSum);
            totalRL += RL;
            del = (2 * RL) / (velocity * rs.getRoute(k).fleet);
            rs.getRoute(k).del = del;
            one_way_time = 0;
            for (int kk = 0; kk < rs.getRoute(k).nodeList.size(); kk++) {
                // this loop will compute the time for going one pass
                int to = rs.getRoute(k).nodeList.get(kk);
                if (kk == 0) {
                    one_way_time = 0;
                    continue;
                }
                else 
                {
                    int from = rs.getRoute(k).nodeList.get(kk - 1);
                    if (time[to][from] == Double.MAX_VALUE) {
                        one_way_time += 100.0;
                    }
                    else {
                        one_way_time += time[to][from];
                    }
                }
            }
            for (int kk = 0; kk < rs.getRoute(k).nodeList.size(); kk++) {
                int to = rs.getRoute(k).nodeList.get(kk);
                if (kk == 0) {
                    first_stoping_time = 0;
                }
                else 
                {
                    int from = rs.getRoute(k).nodeList.get(kk - 1);
                    if (time[to][from] == Double.MAX_VALUE) {
                        first_stoping_time += 100.0;
                    }
                    else {
                        first_stoping_time += time[to][from];
                    }
                }
                if (sharedStops.contains(rs.getRoute(k).nodeList.get(kk))) {
                    stoping_time = first_stoping_time;
//                    for (int tt = 0; tt < tripRequired; tt++) {
//                        sharedStopsStatistics.get(to)
//                                .add(new Pair<>(k, stoping_time));
//                        // this .add consider that the bus stop at here when it returns back
//                        sharedStopsStatistics.get(to)
//                                .add(new Pair<>(k, stoping_time + 2 * one_way_time - 2 * stoping_time));
//                        stoping_time += del;
//                    }
                }
            }
//            timeRequired = ((tripRequired / rs.getRoute(k).fleet) * (2 * RL) / (velocity))
//                + ((tripRequired % rs.getRoute(k).fleet) * (RL) / (velocity)) 
//                + ((tripRequired % rs.getRoute(k).fleet - 1) * del);
            timeRequired = (tripRequired - 1) * del + (RL) / (velocity);
            if (timeRequired <= 0) {
                throw new Error("Accidentally, evacuation time is invalid");
            }
            theoreticalTime[k] = (int) timeRequired;
            // evacuationTime = Math.max(evacuationTime, timeRequired);
        }
        // Let's compute the practical evacuation time
        int delay;
        
        for (Integer stop: sharedStops) {
            if (sharedStopsStatistics.get(stop).size() > 1) {
                Collections.sort(sharedStopsStatistics.get(stop), new Comparator<Pair<Integer, Integer>>() {
                    @Override
                    public int compare(final Pair<Integer, Integer> o1, final Pair<Integer, Integer> o2) {
                        return o1.getValue() - o2.getValue();
                    }
                });
            }
        }
        ArrayList<Integer> temp_prac_time = new ArrayList<Integer>(); 
        Integer k_value;
        for (int k = 0; k < rs.size(); k++)
        {
            temp_prac_time.clear();
            for (int kk = 0; kk < rs.getRoute(k).nodeList.size(); kk++) { 
                int stop = rs.getRoute(k).nodeList.get(kk);
                if (sharedStops.contains(stop) && sharedStopsStatistics.get(stop).size() > 1)
                {
                    if (!computedPracticalDelay.containsKey(stop)) {
                        computedPracticalDelay.put
                            (stop, computePracticalOverhead(sharedStopsStatistics.get(stop), 2));
//                        if (immediate_node[0] == stop || immediate_node[1] == stop
//                           || immediate_node[2] == stop || immediate_node[3] == stop) {
//                            System.out.println("Immediate Node: " + Integer.toString(stop) 
//                            + " has delay: " + Integer.toString(computedPracticalDelay.get(stop)));
//                        }
                    }
                    temp_prac_time.add(computedPracticalDelay.get(stop));
                    // practicalTime[k] += computedPracticalDelay.get(stop);
                }
            }
//            System.out.println("Theoretical Time: " + Double.toString(theoreticalTime[k]) +
//                    "Practical Time: " + Double.toString(practicalTime[k]));
            k_value = (int) Math.ceil((double) rs.getRoute(k).nodeList.size() / 10); // top-10% of practical evacuation time
            practicalTime[k] = 0;
            Collections.sort(temp_prac_time, Collections.reverseOrder());
//            for (int kk = 0; kk < k_value; kk++) {
//                practicalTime[k] += temp_prac_time.get(kk);
//            }
            evacuationTime = Math.max(evacuationTime, theoreticalTime[k] + practicalTime[k]);
        }
        double prac_delay = 0.0;
        for (int k = 0; k < rs.size(); k++)
        {
            prac_delay += practicalTime[k];
        }
        
        solution.setPracticalDelay(prac_delay);
        
        for (int i = 0; i < edgeFreqSum.length; i++)
        {
            for (int j = i + 1; j < edgeFreqSum.length; j++)
            {
                if (time[i][j] != Integer.MAX_VALUE)
                {
                    edgeFreqSum[i][j] = (edgeFreqSum[j][i] *= time[i][j]);
                }
            }

        }
        
        for (int k = 0; k < rs.size(); k++)
        {
            rs.getRoute(k).calculateCongestionFactor(edgeFreqSum);
        }
        solution.setObjective(OBJECTIVES.RL, totalRL);
        solution.setObjective(OBJECTIVES.DO, calculateObjectiveDO(edgeUsage, rs));        
        
        rs.d[0] = rs.d[0] / totalDemand; //direct
        rs.d[1] = rs.d[1] / totalDemand; // unsatisfied
        // rs.d[2] = rs.d[2] / totalDemand; // unsatisfied
        // solution.setObjective(OBJECTIVES.TP, rs.d[1]);
        solution.setObjective(OBJECTIVES.UP, rs.d[1]);
        // double totalFS = 0;
        // for (int k = 0; k < rs.size(); k++)
        // {
        //     totalFS += rs.getRoute(k).calculateFleetSize();
        // }
        // solution.setObjective(OBJECTIVES.FS, totalFS);
        solution.setObjective(OBJECTIVES.CD, calculateObjectiveCD(rs));
        solution.setObjective(OBJECTIVES.IVTT, evacuationTime);
        // solution.setObjective(OBJECTIVES.WT, calculateObjectiveWT(allPath, rs));
        // ++called;
        // if (called % 5000 == 0){
        //     System.out.println("It is called: "+ called +" times.");
        // }
        if (solution.simulator_) {
            prepare_bus_stops_file(solution.dirname_, solution.suffix_);
            try {
                prepare_bus_edges_file(time, solution.dirname_, solution.suffix_);
                prepare_bus_fleet_file(rs, solution.dirname_, solution.suffix_);
            } catch (Exception ex) {
                Logger.getLogger(TNDP.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private ArrayList<Path> generateAllPath(ArrayList<Integer> l, int d, RouteSet rs)
    {
        ArrayList<Path> paths = new ArrayList<>();
        ArrayList<Set<Integer>> routeNodeSet = new ArrayList<>();
        
        for (int i = 0; i < rs.size(); i++)
        {
            routeNodeSet.add(new HashSet<Integer>(rs.getRouteArrayList(i)));
            // if (routeNodeSet.get(i).contains(s))
            // {
            //     routesHavingS.add(i);
            // }
            // if (routeNodeSet.get(i).contains(d))
            // {
            //     routesHavingD.add(i);
            // }
            for (Integer s: l) {
                if (routeNodeSet.get(i).contains(s) && routeNodeSet.get(i).contains(d)) {
                    Path p = new Path(s, d);
                    p.addSegment(i, s, d);
                    paths.add(p);
                    p.setName("Path from " + s + " to " + d + ": " + "(R" + i + "," + s + "," + d + ")");
                }
            }
        }

        
        // if (commonRoutes.size() > 0) //direct path using one route
        // {
        //     for (int i : commonRoutes)
        //     {
        //         Path p = new Path(s, d);
        //         p.addSegment(i, s, d);
        //         paths.add(p);
        //         p.setName("Path from " + s + " to " + d + ": " + "(R" + i + "," + s + "," + d + ")");
        //     }
        // } 
        // else
        // {
        //     for (int ro : routesHavingS)
        //     {
        //         for (int rd : routesHavingD)
        //         {
        //             Set<Integer> commonNodes = intersect(routeNodeSet.get(ro), routeNodeSet.get(rd));
        //             if (commonNodes.size() > 0)
        //             {
        //                 for (int i : commonNodes)
        //                 {
        //                     Path p = new Path(s, d);
        //                     p.addSegment(ro, s, i);
        //                     p.addSegment(rd, i, d);
        //                     paths.add(p);
        //                     p.setName("Path from " + s + " to " + d + ": " + "(R" + ro + "," + s + "," + i + ")" + "(R" + rd + "," + i + "," + d + ")");
        //                     break;
        //                 }
        //             }
        //         }
        //     }
        // }

        return paths;
    }

    private static void classifyPaths(ArrayList<Path> paths, HashMap<Integer, ArrayList<Path>> pathClass)
    {

        // boolean transfer = paths.get(0).needTransfer();
        for (Path p : paths)
        {
            if (pathClass.containsKey(p.getRouteOfSeg(0)))
            {
                ArrayList<Path> pList = pathClass.get(p.getRouteOfSeg(0));
                pList.add(p);
            } else
            {
                ArrayList<Path> pList = new ArrayList<>();
                pList.add(p);
                pathClass.put(p.getRouteOfSeg(0), pList);
            }
            // if (transfer)
            // {
            //     String groupKey = p.getRouteAndEndOfSeg(0);
            //     if (pathGroup.containsKey(groupKey))
            //     {
            //         ArrayList<Path> pList = pathGroup.get(groupKey);
            //         pList.add(p);
            //     } else
            //     {
            //         ArrayList<Path> pList = new ArrayList<>();
            //         pList.add(p);
            //         pathGroup.put(groupKey, pList);
            //     }
            // }
        }
    }

    private void screenPath(ArrayList<Path> paths, RouteSet rs)
    {
        double minTime = Double.MAX_VALUE;
        for (Path p : paths)
        {
            double totalTime = 0;
            
            for (Path.Segment seg : p.segList)
            {
                Route r = rs.getRoute(seg.routeId);
                int si = r.nodeList.indexOf(seg.startNode);
                int ei = r.nodeList.indexOf(seg.endNode);
                if (si > ei)
                {
                    int t = si;
                    si = ei;
                    ei = t;
                }
                for (si++; si <= ei; si++)
                {
                    if (time[r.nodeList.get(si)][r.nodeList.get(si - 1)] == Double.MAX_VALUE) {
                        totalTime += 100.0;
                        continue;
                    }
                    totalTime += time[r.nodeList.get(si)][r.nodeList.get(si - 1)];
                }
            }
            p.setTotalInVehicleTime(totalTime);
            if (minTime > totalTime)
            {
                minTime = totalTime;
            }
        }
        for (int i = 0; i < paths.size(); i++)
        {
            Path p = paths.get(i);
            //double diff = (p.totalInVehicleTime - minTime) / minTime; 
            boolean reject = (p.totalInVehicleTime > minTime * (1 + Path.timeDiffThreshold));
            if (reject)
            {
                paths.remove(i);
            }
        }
    }
    
    private void calculateCongestedTravelTime(ArrayList<Path> paths, RouteSet rs)
    {
        for (Path p : paths)
        {
            double pathCongestedTravelTime = 0; 
            for (Path.Segment seg : p.segList)
            {
                double segTotalTime = 0;
                Route r = rs.getRoute(seg.routeId);
                int si = r.nodeList.indexOf(seg.startNode);
                int ei = r.nodeList.indexOf(seg.endNode);
                if (si > ei)
                {
                    int t = si;
                    si = ei;
                    ei = t;
                }
                for (si++; si <= ei; si++)
                {
                    segTotalTime += time[r.nodeList.get(si)][r.nodeList.get(si - 1)];
                }
                pathCongestedTravelTime =+ segTotalTime*r.getCongestionFactor();
            }
            p.setTotalInVehicleTimeCongested(pathCongestedTravelTime);
        }
    }

    public void route_destination_check(ArrayList<Route> routeSet, String s) {
        for (Route r: routeSet) {
            if (!isShelter(r.nodeList.get(r.nodeList.size() - 1))) {
                System.out.println("Last node is not shelter ... "+ s);
                System.exit(0);
            }
            Set<Integer> set = new HashSet<Integer>(r.nodeList);
            if (set.size() < r.nodeList.size()) {   
                System.out.println("There are duplicate elements in route");
            }
        }
    }

    private void splitDemand(ArrayList<Path> paths, HashMap<Integer, ArrayList<Path>> pathClass/*,HashMap<String, ArrayList<Path>> pathGroup, RouteSet rs*/)
    {
        // boolean transfer = paths.get(0).needTransfer();;
        double sumTotalTravelTimeINV = 0.0;
        for (int routeId : pathClass.keySet())
        {
            for (Path p : pathClass.get(routeId))
            {
                assert p.totalInVehicleTime > 0.0;
                sumTotalTravelTimeINV += (1.0 * pathClass.get(routeId).size() / p.totalInVehicleTime);
            }
        }
        assert sumTotalTravelTimeINV > 0.0;
        
        for (int routeId : pathClass.keySet())
        {
            double totalTravelTimeINV;
            //int totalPath = pathClass.get(routeId).size();
            for (Path p : pathClass.get(routeId))
            {
                totalTravelTimeINV = 1.0 / p.totalInVehicleTime;
                p.demandPerc = totalTravelTimeINV / (sumTotalTravelTimeINV);
            }
        }
        // if (transfer)
        // {
        //     for (Map.Entry<String, ArrayList<Path>> entry : pathGroup.entrySet())
        //     {
        //         ArrayList<Path> pList = entry.getValue();
        //         double sumFreq = 0;
        //         double sumDemand = 0;
        //         for (Path p : pList)
        //         {
        //             sumDemand += p.demandPerc;
        //             sumFreq += rs.getRoute(p.getRouteOfSeg(1)).frequency;
        //         }
        //         for (Path p : pList)
        //         {
        //             double routeFreq = rs.getRoute(p.getRouteOfSeg(1)).frequency;
        //             p.demandPerc = 1.0 * sumDemand * routeFreq / sumFreq;
        //         }
        //     }
        // }

    }

    private void assignDemand(ArrayList<Path> paths, int demand, RouteSet rs, double routeDemand[][])
    {
        int remaining_demand = demand;
        for (Path p : paths)
        {
            double allocatedDem = demand * p.demandPerc;
            for (Path.Segment seg : p.segList)
            {
                Route r = rs.getRoute(seg.routeId);
                int si = r.nodeList.indexOf(seg.startNode);
                int ei = r.nodeList.indexOf(seg.endNode);
                assert(isShelter(seg.endNode));
                demandBusStopWise[seg.startNode][getShelterIndex(seg.endNode)] += (int) Math.floor(allocatedDem);
                remaining_demand -= (int) Math.floor(allocatedDem);
                if (si > ei)
                {
                    int t = si;
                    si = ei;
                    ei = t;
                }
                for (si++; si <= ei; si++)
                {
                    routeDemand[seg.routeId][si] += allocatedDem;
                }
            }
        }
        if (remaining_demand > 0) {
            demandBusStopWise[paths.get(0).segList.get(0).startNode][getShelterIndex(paths.get(0).segList.get(0).endNode)] += remaining_demand;
        }
    }

    private static boolean findMLS(RouteSet rs, double[][] routeDemand)
    {
        boolean converged = true;
        for (int i = 0; i < rs.size(); i++)
        {
            double MLSDemand = routeDemand[i][1];
            int MLS = 1;
            for (int j = 2; j < routeDemand[i].length; j++)
            {
                if (routeDemand[i][j] > MLSDemand)
                {
                    MLS = j;
                    MLSDemand = routeDemand[i][j];
                }

            }
            converged = converged && rs.getRoute(i).reviseFrequency(MLSDemand, MLS);

        }
        return converged;
    }

    public Set<Integer> intersect(Set<Integer> set1, Set<Integer> set2)
    {
        Set<Integer> a;
        Set<Integer> b;
        Set<Integer> res = new HashSet<Integer>();
        if (set1.size() <= set2.size())
        {
            a = set1;
            b = set2;
        } else
        {
            a = set2;
            b = set1;
        }
        for (Integer e : a)
        {
            if (b.contains(e))
            {
                res.add(e);
            }
        }
        return res;
    }

    // private double calculateObjectiveIVTT(ArrayList<Path>[][] allPath)
    // {
    //     double total = 0;
    //     int vertices = ins.getNumOfVertices();
    //     for (int i = 0; i < vertices; i++)
    //     {
    //         for (int j = i + 1; j < vertices; j++)
    //         {
    //             if (allPath[i][j] != null)
    //             {
    //                 for (Path p : allPath[i][j])
    //                 {
    //                     total += p.demandPerc * demand[i][j] * p.totalInVehicleTime;
    //                 }

    //             }

    //         }
    //     }
    //     return total;
    // }
    
    private double calculateObjectiveIVTT(ArrayList<Path>[][] allPath, RouteSet rs) //ITS Revision: Consider congestion
    {
        double total = 0;
        int vertices = ins.getNumOfVertices();
        for (Map.Entry<Integer, ArrayList<Integer>> entry : zoneStopMapping.entrySet())
        {
            for (int j = 0; j < shelters.length; j++)
            {
                if (allPath[getIndex(entry.getKey())][j] != null)
                {
                    // if (allPath[entry.getKey()][j].get(0).getNumOfSegment() == 1) //direct path with no transfer, uses only one route
                    // {
                        for (Path p : allPath[getIndex(entry.getKey())][j])
                        {   
                            Route r = rs.getRoute(p.getRouteOfSeg(0));
                            total += p.demandPerc * demand.get(entry.getKey())[j] * p.totalInVehicleTime * r.getCongestionFactor(); //ITS Revision: Consider congestion
                        }                         
                    // }
                    // else
                    // {
                    //     calculateCongestedTravelTime(allPath[i][j], rs); 
                    //     for (Path p : allPath[i][j])
                    //     {                       
                    //         total += p.demandPerc * demand.get(entry.getKey())[j] * p.totalInVehicleTimeCongested; //ITS Revision: Consider congestion
                    //     }                        
                    // }


                }

            }
        }
        return total;
    }

    private double calculateObjectiveWT(ArrayList[][] allPath, RouteSet rs)
    {
        double totalWT = 0;
        int vertices = ins.getNumOfVertices();
        for (Map.Entry<Integer, ArrayList<Integer>> entry : zoneStopMapping.entrySet())
        {
            for (int j = 0; j < shelters.length; j++)
            {
                if (allPath[getIndex(entry.getKey())][j] != null)
                {
                    for (Path p : (ArrayList<Path>) allPath[getIndex(entry.getKey())][j]) //for each path
                    {
                        double pathWT = 0;
                        for (int k = 0; k < p.segList.size(); k++)
                        {
                            Route r = rs.getRoute(p.getRouteOfSeg(k));
                            pathWT += r.calculateWaitingTime() * r.getCongestionFactor(); //ITS Revision: Consider congestion
                        }
                        totalWT += p.demandPerc * demand.get(entry.getKey())[j] * pathWT;
                    }

                }

            }
        }
        return totalWT;
    }
    private double calculateObjectiveCD(RouteSet rs) {
        double centroid_distance = 0;
        for (int i = 0; i < rs.size(); i++) {
            Route r = rs.getRoute(i);
            for (Integer s: r.nodeList) {
                if (!isShelter(s)) {
                    centroid_distance += this.centroid_distance[s];
                }
            }
        }
        return centroid_distance;
    }


    private double calculateObjectiveDO(int[][] edgeUsage, RouteSet rs)
    {
        double totalDO = 0;
        for (Route r : rs.routeSet)
        {
            double routeDO = 0;
            for (int i = 1; i < r.nodeList.size(); i++) {
                int v0 = r.nodeList.get(i), v1 = r.nodeList.get(i - 1);
                if (edgeUsage[v0][v1] > 1) {
                    // routeDO += time[v0][v1];
                    if (time[v0][v1] == Double.MAX_VALUE) {
                        routeDO += 100.0;
                    } else {
                        routeDO += time[v0][v1];
                    }
                }
            }
            totalDO += routeDO / r.getLength();
        }

        return totalDO;
    }

    @Override
    public void evaluateConstraints(Solution solution) throws JMException
    {
        RouteSet rs = (RouteSet) (solution.getDecisionVariables())[0];
        //rs.lengthCheck((TNDP) solution.getProblem());
        //rs.ConnectednessCheck((TNDP) solution.getProblem());
        if (rs.getOverallConstraintViolation() != 0)
        {
            solution.setOverallConstraintViolation(rs.getOverallConstraintViolation());
        }
    }

    public int getNumberOfRoutes()
    {
        return numOfRoutes;
    }

    // public int getDemand(int i, int j)
    // {
    //     return demand[i][j];
    // }

    public double getTime(int i, int j)
    {
        return time[i][j];
    }

    public boolean isShelterOrImmediateNode(int stop) {
        for (int i = 0; i < shelters.length; i++ ){
            if (shelters[i] == stop)
                return true;
        }
        for (int i = 0; i < immediate_node.length; i++ ){
            if (immediate_node[i] == stop)
                return true; 
        }
        return false;
    }
    
    public boolean isShelter(int stop) {
        for (int i = 0; i < shelters.length; i++ ){
            if (shelters[i] == stop)
                return true;
        }
        return false;
    }
    
    public int getShelterIndex(int stop) {
        int index = -1;
        for (int i = 0; i < shelters.length; i++ ){
            if (shelters[i] == stop) {
                index = i;
                break;
            }
        }
        return index;
    }
    
    public boolean isImmediateNode(int stop) {
        for (int i = 0; i < immediate_node.length; i++ ){
            if (immediate_node[i] == stop)
                return true; 
        }
        return false;
    }
    
    public String convertToString3(int depot, int in_node, int shelter) {
        return String.valueOf(depot) + "->" + String.valueOf(in_node) + "->" + String.valueOf(shelter);
    } 
    
    public void prepareAllShortestPaths() {
        int paths[][] = new int[immediate_node.length][]; 
        ArrayList<Integer> p = new ArrayList<Integer>();
        for (int i = 0; i < ins.getNumOfVertices(); i++) {
            if (isShelterOrImmediateNode(i))
                continue;
            for (Map.Entry<Integer, int []> entry: shelter_immediate_node.entrySet()) {
                // System.out.println("Key: "+ entry.getKey());
                for (int j = 0; j < shelter_immediate_node.get(entry.getKey()).length; j++ ){
                    graphHelper.set_forbidden_nodes(immediate_node, shelter_immediate_node.get(entry.getKey())[j]);
                    p = graphHelper.dijkstra(i, shelter_immediate_node.get(entry.getKey())[j]);
                    p.add(entry.getKey());
                    all_shortest_paths.put(convertToString3(i, shelter_immediate_node.get(entry.getKey())[j],
                        entry.getKey()), p);
                }
            }
        }
        
    }
    
    public ArrayList<Integer> removeForbiddenNodes(ArrayList<Integer> adjNodes) {
        adjNodes.removeIf(e -> isShelterOrImmediateNode(e));
//        for (Integer e: adjNodes) {
//            for (int i = 0; i < shelters.length; i++ ){
//                if (shelters[i] == e) {
//                    adjNodes.remove(new Integer(e));
//                    break;
//                }  
//            }
//            for (int i = 0; i < immediate_node.length; i++ ){
//                if (immediate_node[i] == e) {
//                    adjNodes.remove(new Integer(e));
//                    break;
//                } 
//            }
//        }
        return adjNodes;
    }

    public int getNumberofShelters() {
        return shelters.length;
    }
    public int getNumberofZones() {
        return zoneStopMapping.size();
    }
    public ArrayList<Integer> getAllStops(int zoneId) {
        return zoneStopMapping.get(zoneId);
    }
    public Set<Integer> getAllZones() {
        return zoneStopMapping.keySet();
    }
    public int getZone(int stop) {
        return zone_ref[stop];
    }
    public int getIndex(int zone) {
        return zoneIndexMapping.get(zone);
    }
    public void bugFixing() {
        for (Map.Entry<Integer, ArrayList<Integer>> entry : zoneStopMapping.entrySet()) {
            if (bug_fix.get(entry.getKey()) != zoneStopMapping.get(entry.getKey()).size()) {
                System.out.println(zoneStopMapping.get(entry.getKey()));
                System.out.println(entry.getKey());
                java.lang.System.exit(0);
            }
        }
    }
    public ArrayList<Integer> uncoveredNodes(Set<Integer> coveredNodes) {
        HashMap<Integer, ArrayList<Integer>> isZoneCovered = new HashMap<Integer, ArrayList<Integer>>();
        for (Map.Entry<Integer, ArrayList<Integer>> entry : zoneStopMapping.entrySet()) {
            isZoneCovered.put(entry.getKey(), new ArrayList<Integer>(entry.getValue()));
        }
        int zoneId;
        for (Integer s: coveredNodes){
            if (isShelter(s)) 
                continue;
            zoneId = zone_ref[s];
            isZoneCovered.get(zoneId).clear();
        }
        // bugFixing();
        ArrayList<Integer> uncovered = new ArrayList<Integer>();
        for (Map.Entry<Integer, ArrayList<Integer>> entry : isZoneCovered.entrySet()) {
            if (!entry.getValue().isEmpty()){
                uncovered.addAll(entry.getValue());
            }
        }
        
        return uncovered;
    }
    public Boolean isAllZoneCovered(Set<Integer> coveredNodes, 
        HashMap<Integer, ArrayList<Integer>> zoneNeedAttention, ArrayList<Route> routeSet) {
        
        zoneNeedAttention.clear();
        for (int i = 0; i < shelters.length; i++) {
            zoneNeedAttention.put(shelters[i], new ArrayList<Integer>());
        }
        for (Map.Entry<Integer, ArrayList<Integer>> entry : zoneStopMapping.entrySet()) {
            for (int i = 0; i < shelters.length; i++) {
                zoneNeedAttention.get(shelters[i]).add(entry.getKey());
            }
            for (Route r: routeSet) {
                for (Integer s: r.shelterList) {
                    Set<Integer> set1 = new HashSet<Integer>(entry.getValue());
                    Set<Integer> set2 = new HashSet<Integer>(r.nodeList);
                    if (Sets.intersection(set1, set2).size() > 0) {
                        zoneNeedAttention.get(s).remove(new Integer(entry.getKey()));
                    }
                }
            }
        } 
        if (!uncoveredNodes(coveredNodes).isEmpty())
            return false;
        for (Map.Entry<Integer, ArrayList<Integer>> entry : zoneNeedAttention.entrySet()) {
            if (entry.getValue().size() > 0) {
                return false;
            }
        }
        return true;
    }
    public double[] setFitness(ArrayList<Integer> neighbours, Set<Integer> chosenNodes){
        double fit[] = new double[neighbours.size()];
        double total = 0, size = 0;
        int notReachedPerZone = 0;
        HashMap<Integer, Double> zones = new HashMap<Integer, Double>(); 
        for (Integer s: neighbours) {
            zones.put(s, 0.0);
        }
        for (Map.Entry<Integer, Double> entry : zones.entrySet()) {
            notReachedPerZone = 0;
            if (!isShelter(entry.getKey()))
            {
                for (Integer s: zoneStopMapping.get(zone_ref[entry.getKey()])) {
                    if (!chosenNodes.contains(s)){
                        notReachedPerZone++;
                    }
                }
                size = zoneStopMapping.get(zone_ref[entry.getKey()]).size();
                zones.put(entry.getKey(), (double) (1.0 * notReachedPerZone / size));
            }
            else {
                zones.put(entry.getKey(), PseudoRandom.randDouble());
            }
        }
        for (int k = 0; k < neighbours.size(); k++)
        {
            fit[k] = zones.get(neighbours.get(k));
        }
        return fit;
    }
    public int[] determineRoute(int source, int sink) {
        double fit[] = new double[immediate_node.length / shelters.length];
        int paths[][] = new int[immediate_node.length / shelters.length][]; 
        double total = 0.0;
        int index = 0;
        ArrayList<Integer> p = new ArrayList<Integer>();
        for (Map.Entry<Integer, int []> entry: shelter_immediate_node.entrySet()) {
            if (entry.getKey() != sink) continue;
            for (int i = 0; i < shelter_immediate_node.get(entry.getKey()).length; i++ ){
                if (g.containsAPath(source, shelter_immediate_node.get(entry.getKey())[i])){
                    // paths[i] = g.getShortestPath(source, shelters[i], EdgeWeight).toVertexArray();
                    graphHelper.set_forbidden_nodes(immediate_node, shelter_immediate_node.get(entry.getKey())[i]);
                    p = graphHelper.dijkstra(source, shelter_immediate_node.get(entry.getKey())[i]);
                    p.add(entry.getKey());
//                    p = all_shortest_paths.
//                            get(convertToString3(source, shelter_immediate_node.get(entry.getKey())[i], entry.getKey()));
                    paths[index] = p.stream().mapToInt(j -> j).toArray();
                    fit[index] = (double) (1.0 / paths[index].length);                 
                    total += fit[index];
                }
                else {
                    fit[index] = 0.0;
                }
                index++;
            // System.out.println(fit[i]);
            }
        }
        
        
        return paths[PseudoRandom.roulette_wheel(fit, 0)];
    }
    public boolean nodeCanBeDeleted(RouteSet rs, int node) 
    {
        if (isShelter(node)) {
            return false;
        }
        int zone = zone_ref[node];
        ArrayList<Integer> temp = new ArrayList<Integer>(zoneStopMapping.get(zone));
        temp.remove(new Integer(node));
        if (temp.isEmpty())
            return false;
        HashMap<Integer, Boolean> isReached = new HashMap<Integer, Boolean>();
        for (int i = 0; i < shelters.length; i++) {
            isReached.put(shelters[i], false);
        }
        Set<Integer> set1 = new HashSet<Integer>(temp);
        for (int i = 0; i < rs.size(); i++)
        {
            Route r = rs.getRoute(i);
            Set<Integer> set2 = new HashSet<Integer>(r.nodeList);
            if (!Collections.disjoint(set1, set2)) {
                for (Integer s: r.shelterList) {
                    isReached.replace(s, true);
                }
            }
        }
        for (Integer s: isReached.keySet()) {
            if (!isReached.get(s)) {
                return false;                    
            }
        }
        return true;
    }
    public void setSharedStops(RouteSet routeSet) 
    {
        sharedStops.clear();
        ArrayList<Integer> tempList = new ArrayList<Integer>();
        for (int i = 0; i < routeSet.size(); i++) {
            for (int j = i + 1; j < routeSet.size(); j++) {
                tempList.clear();
                tempList.addAll(routeSet.getRoute(i).nodeList);
                tempList.removeAll(routeSet.getRoute(i).shelterList);  // removing shelters
                tempList.retainAll(routeSet.getRoute(i).nodeList);   // keeping common stops
                if (!tempList.isEmpty()) {
                    sharedStops.addAll(tempList);
                }
            }
        }
        // System.out.println("The number of shared bus stops is " + Integer.toString(sharedStops.size()));
        sharedStopsStatistics.clear();
        for (Integer stop : sharedStops) {
            sharedStopsStatistics.put(stop, new ArrayList<Pair<Integer, Integer>>());
        }
    }
    public int computePracticalOverhead(ArrayList<Pair<Integer, Integer>> stopping_time, int Road_Width) {
        // System.out.println("Length of array: " + Integer.toString(stopping_time.size()));
        int opening_time = stopping_time.get(0).getValue();
        int available_slots = Road_Width;
        int Stand_time = 50;
        Queue<Integer> queue = new LinkedList<>();
        
        int delay = 0;  // we are doing all this to compute it.
        for (Pair <Integer, Integer> time: stopping_time) {
            int t_i = time.getValue();
            if (queue.size() < Road_Width) {
                // has empty slot
                queue.add(t_i);
            }
            else {
                int head = queue.poll();
                if (head + Stand_time <= time.getValue()) {
                    // no delay
                    delay += 0;
                }
                else {
                    delay += (head + Stand_time - t_i);
                }
                queue.add(t_i);
            }
            while(queue.peek() + Stand_time <= t_i) {
                queue.remove();
            }
            if (queue.size() > Road_Width) {
                throw new Error("Queue exceeds the size of Road_Width");
            }
        }
        return delay;
    }
    private int readDemandFromFile(String fileName, HashMap<Integer, int[]> data) throws Exception
    {
        int sum = 0;
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        int zone=0, index, d;
        String line;
        while((line = reader.readLine()) != null){
            index = 0;
            // System.out.println(line);
            for (String stop : line.split(" ")) {
                if (index == 0) {
                    zone = Integer.parseInt(stop);
                    demand.put(zone, new int[3]);
                    zoneIndexMapping.put(zone, zoneIndexMapping.size());
                }
                else {
                    d = Integer.parseInt(stop);
                    demand.get(zone)[index - 1] = d;
                    sum += d;
                }
                index++;
            }
        }
        reader.close();
        return sum;
    }
    private double readFromFile(String fileName, double[][] data) throws Exception
    {

        double sum = 0;
        Scanner sc = new Scanner(new FileInputStream(fileName));

        for (int i = 0; i < data.length; i++)
        {
            for (int j = 0; j < data.length; j++)
            {
                String s = sc.next();
                if (s.equals("-"))
                {
                    data[i][j] = Double.MAX_VALUE;
                } else
                {
                    data[i][j] =  Double.parseDouble(s);
                    sum += data[i][j];
                }
            }
        }

        sc.close();
        return sum / 2;
    }
    private void fixZones(String fileName, double[] centroid_distance) throws Exception
    {
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        int index, zone = 0;
        int stopId = 0;
        String line ;
        while((line = reader.readLine()) != null){
            index = 0;
            for (String stop : line.split(" ")) {
                if (index == 0) {
                    zone = Integer.parseInt(stop);
                    zoneStopMapping.put(zone, new ArrayList<Integer>());
                }
                else {
                    stopId = Integer.parseInt(stop);
                    zoneStopMapping.get(zone).add(stopId);
                    centroid_distance[stopId] = PseudoRandom.randDouble(0.1, 100.0);
                    zone_ref[stopId] = zone;
                }
                index++;
            }
            bug_fix.put(zone, index - 1);
        }
        reader.close();
        return;
    }
    
    public void prepare_bus_stops_file(String file_path, String suffix) {
        try {
            FileOutputStream fos = new FileOutputStream(file_path + "Bus_Stops" + suffix + ".txt");
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter bw = new BufferedWriter(osw);
            String line = "";
            for (int v = 0; v < ins.getNumOfVertices(); v++) {
                line = Integer.toString(v) + " " + Integer.toString(demandBusStopWise[v][0]) +  " " + Integer.toString(demandBusStopWise[v][1]);
                bw.write(line);
                bw.newLine();
            }
            bw.close();
        }
        catch (IOException e) {
            Configuration.logger_.severe("Error acceding to the file");
            e.printStackTrace();
        }
    }
    private void prepare_bus_edges_file(double[][] data, String file_path, String suffix) throws Exception
    {
        try {
            FileOutputStream fos = new FileOutputStream(file_path + "Bus_Edge" + suffix + ".txt");
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter bw = new BufferedWriter(osw);
            String line = "";
            for (int i = 0; i < data.length; i++)
            {
                for (int j = i + 1; j < data.length; j++)
                {
                    if (data[i][j] == Double.MAX_VALUE && data[j][i] == Double.MAX_VALUE) 
                    {
                        continue;
                    }
                    if (data[i][j] != Double.MAX_VALUE && data[i][j] == data[j][i])
                    {
                        line = Integer.toString(i) + " " + Integer.toString(j) + " " + Double.toString(data[i][j]) + " 2";
                    }
                    else {
                        line = Integer.toString(i) + " " + Integer.toString(j) + " " + Double.toString(Math.min(data[i][j], data[j][i])) + " 1";
                    }
                    bw.write(line);
                    bw.newLine();
                }
            }
            bw.close();
        }
        catch (IOException e) {
            Configuration.logger_.severe("Error acceding to the file");
            e.printStackTrace();
        }
    }
    private void prepare_bus_fleet_file(RouteSet rs, String file_path, String suffix) throws Exception
    {
        try {
            FileOutputStream fos = new FileOutputStream(file_path + "Bus_Fleet" + suffix + ".txt");
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter bw = new BufferedWriter(osw);
            String line = "";
            String fleet_line = "";
            String route_line = "";
            String trip_line = "";
            int fleet = 1;
            int index = 1;
            double interval;
            for (int i = 0; i < rs.size(); i++)
            {
                route_line = "";
                interval = 0.0;
                for (int j = 0; j < rs.getRoute(i).nodeList.size(); j++) {
                    if (j != rs.getRoute(i).nodeList.size() - 1) {
                        route_line += (Integer.toString(rs.getRoute(i).nodeList.get(j)) + " ");
                    }
                    else {
                        route_line += (Integer.toString(rs.getRoute(i).nodeList.get(j)));
                    } 
                }
                for (int j = 0; j < rs.getRoute(i).fleet; j++) {
                    fleet = rs.getRoute(i).tripRequired / rs.getRoute(i).fleet;
                    if (j < rs.getRoute(i).tripRequired % rs.getRoute(i).fleet) {
                        fleet++;
                    }
                    fleet_line = Integer.toString(fleet);
                    line = "Fleet " + Integer.toString(index++) + " on " + Integer.toString(i) + "'th route";
                    bw.write(line);
                    bw.newLine();
                    bw.write(route_line);
                    bw.newLine();
                    bw.write(Double.toString(interval));
                    bw.newLine();
                    interval += rs.getRoute(i).del;
                    bw.write(fleet_line);
                    bw.newLine();
                }
            }
            bw.close();
        }
        catch (IOException e) {
            Configuration.logger_.severe("Error acceding to the file");
            e.printStackTrace();
        }
    }
    public double[][] getTime()
    {
        return time;
    }

}
