/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jmetal.problems.TNDP;

import java.util.HashMap;
import jmetal.core.Algorithm;
import jmetal.core.Operator;
import jmetal.experiments.Settings;
import jmetal.metaheuristics.nsgaII.NSGAII;
import jmetal.operators.crossover.RouteSetCrossover;
import jmetal.operators.mutation.RouteSetAddDelMutation;
import jmetal.operators.mutation.RouteSetAddDelRand;
import jmetal.operators.mutation.RouteSetAddDelTELRand;
import jmetal.operators.mutation.RouteSetAddDelTEORand;
import jmetal.operators.mutation.RouteSetCombinedGuidedMutation;
import jmetal.operators.mutation.RouteSetCombinedRandomMutation;
import jmetal.operators.selection.RandomSelection;
import jmetal.qualityIndicator.Hypervolume;
import jmetal.util.JMException;

/**
 *
 * @author mahi
 */
public class NSGAII_Settings extends Settings {
    public int populationSize_;
    public int maxEvaluations_;
    public String mutationName_;
    public String SelectionName_;
    public double mutationProbability_;
    public double crossoverProbability_;
    public double addProbability_;
    public int tSize_;
    Hypervolume indicator_;

    private HashMap<String, Operator> ListOfMutAndSel = new HashMap<>();

    public NSGAII_Settings(String ins)
    {
        super(ins);

        //return c.newInstance();
        try
        {
            String[] probName = ins.split("-");
            Class instance = Class.forName("jmetal.problems.TNDP." + probName[0]);
            problem_ = new TNDP(Integer.parseInt(probName[1]), (Instance) instance.newInstance());
        } catch (Exception e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        ListOfMutAndSel.put("RouteSetAddDelMutation", new RouteSetAddDelMutation(null));
        ListOfMutAndSel.put("RouteSetCombinedGuidedMutation", new RouteSetCombinedGuidedMutation(null, problem_));
        ListOfMutAndSel.put("RouteSetCombinedRandomMutation", new RouteSetCombinedRandomMutation(null));
        ListOfMutAndSel.put("RouteSetAddDelRand", new RouteSetAddDelRand(null));
        ListOfMutAndSel.put("RouteSetAddDelTELRand", new RouteSetAddDelTELRand(null));
        ListOfMutAndSel.put("RouteSetAddDelTEORand", new RouteSetAddDelTEORand(null));
        ListOfMutAndSel.put("RandomSelection", new RandomSelection(null));
        ListOfMutAndSel.put("RetativeTournamentSelection", new RetativeTournamentSelection(null));
        // Default experiments.settings
        maxEvaluations_ = 500;
        mutationProbability_ = 1.0;
        crossoverProbability_ = 1.0;
        mutationName_ = "RouteSetAddDelMutation";
        SelectionName_ = "RandomSelection";
        addProbability_ = 0.5;
        tSize_ = 40;
        populationSize_ = 112;
        indicator_ = new Hypervolume();
    } // NSGAII_Settings

    @Override
    public Algorithm configure() throws JMException
    {
        Algorithm algorithm; // The algorithm to use
        Operator crossover; // Crossover operator
        Operator mutation; // Mutation operator
        Operator selection;

        HashMap parameters; // Operator parameters

        algorithm = new NSGAII(problem_);


        algorithm.setInputParameter("maxEvaluations", maxEvaluations_);
        algorithm.setInputParameter("populationSize", populationSize_);
        algorithm.setInputParameter("indicators", indicator_);

        parameters = new HashMap();
        parameters.put("probability", crossoverProbability_);
        crossover = new RouteSetCrossover(parameters);

//        parameters = new HashMap();
//        parameters.put("probability", mutationProbability_);
//        parameters.put("addProbability", addProbability_);
//        mutation = new RouteSetAddDelMutation(parameters);
        mutation = ListOfMutAndSel.get(mutationName_);
        if (SelectionName_.equals("RetativeTournamentSelection"))
        {
            parameters = new HashMap();
            parameters.put("tSize", tSize_);
            selection = new RetativeTournamentSelection(parameters);
        }
        else
        {
             selection = ListOfMutAndSel.get(SelectionName_);
        }
        
        // Add the operators to the algorithm
        algorithm.addOperator("crossover", crossover);
        algorithm.addOperator("mutation", mutation);
        algorithm.addOperator("selection", selection);

        return algorithm;
    }
}
