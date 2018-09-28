# Solving Transit Network Design Problem Using Many-Objective Evolutionary Approach
This is a JAVA Netbeans project based on JMetal 4.5 (http://jmetal.sourceforge.net/) where we added necessary codes to solve transit network design problem (TNDP) by applying many-objective evolutionary algorithms (MaOEAs). In addition, here we keep the datasets and statistical results used for our research works. Below we describe different components of this repository.

## Source code
All the codes are organized within the directiry `src/jmetal/`. Here we describe the codes that we added for solving TNDP.

### Solution enconding
We encoded a transit network using the following classes kept within `src/jmetal/encodings/variable/`:
* Route.java 
* RouteSet.java 

### Random initialization
We implemented this as a method named `generateRouteSet()` inside the class `RouteSet`.

### Crossover Operator
We implemented this using `src/jmetal/operators/crossover/RouteSetCrossover.java`.

### Mutation Operators
We implemented four mutation operators inside `src/jmetal/operators/mutation/` listed below:
* RouteSetAddlMutation.java  (AddNodes)
* RouteSetDelMutation.java  (DeleteNodes)
* RouteSetTELMutation.java  (ShortenRoutes)
* RouteSetTEOMutation.java  (ReduceOverlap)

### Mutation Schemes
We implemented five mutation schemes inside `src/jmetal/operators/mutation/` listed below:
* RouteSetAddDelRand.java  (Basic Scheme)
* RouteSetAddDelTELRand.java  (Random Scheme I)
* RouteSetAddDelTEORand.java  (Random Scheme I)
* RouteSetCombinedRandomMutation.java  (Random Scheme I)
* RouteSetCombinedGuidedMutation.java  (Guided Scheme)

### Evolutionary algorithms
We adapted four evolutionary algorithms to solve TNDP as follows:
* jmetal/metaheuristics/spea2/SPEA2.java
* jmetal/problems/TNDP/MOEAD.java
* jmetal/metaheuristics/nsgaIII/NSGAIII.java
* jmetal/metaheuristics/thetadea/ThetaDEA.java


### Experiement
We experiement with four algorithms varying differetn parameters we implemented four classes inside `jmetal/problems/TNDP/` as follows:
* TNDPExpSPEA2.java
* TNDPExpMOEAD.java
* TNDPExpNSGAIII.java
* TNDPExpThetaDEA.java

## Datasets
We keep the datasets in the directory `IO`. There are currently four datasets listed below:
* Mandl 
* Mumford0 
* Mumford2
* Mumford3

New datasets can be added very easily at any time.

## Statistical results
We keep all the statistical results obtain throughout our research in the directory `Experiment`. The results include the following things:
* Pareto fronts calculated for different datasets
* Sets of solutions generated by different algorithms
* HV, GAS and GDS obtained by different algorithms, genetic operators

## Requirements
To use our framework the following software packages are required:
* [Java SE Development Kit 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html?ssSourceSiteId=otnes)
* [Apache Ant](https://ant.apache.org/)
* [Git](https://git-scm.com/)

## Downloading and compiling
To download our framework just clone the Git repository hosted in GitHub:
```
git clone https://github.com/ali-nayeem/JMetal4.5_Netbeans.git
```
Once cloned, you can compile the software and generate a jar file with the following commands:
```
ant
ant jar
```

## Running code
To execute a class named `TNDPExpThetaDEA`, just run this command:

```
java -cp Jama-1.0.2.jar:grph-1.6.29-big.jar:dist/JMetal4.5.jar jmetal.problems.TNDP.TNDPExpThetaDEA
```
