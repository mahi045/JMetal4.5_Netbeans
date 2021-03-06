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
import jmetal.metaheuristics.spea2.SPEA2;
import jmetal.operators.crossover.RouteSetCrossover;
import jmetal.operators.mutation.RouteSetAddDelRand;
import jmetal.operators.mutation.RouteSetAddDelMutation;
import jmetal.operators.mutation.RouteSetAddDelTELRand;
import jmetal.operators.mutation.RouteSetAddDelTEORand;
import jmetal.operators.mutation.RouteSetCombinedGuidedMutation;
import jmetal.operators.mutation.RouteSetCombinedRandomMutation;
import jmetal.operators.selection.RandomSelection;
import jmetal.operators.selection.SelectionFactory;
import jmetal.util.JMException;

/**
 *
 * @author MAN
 */
public class SPEA2_Settings extends Settings
{

    public int populationSize_;
    public int maxGenerations_;
    public String mutationName_;
    public String SelectionName_;
    public double mutationProbability_;
    public double crossoverProbability_;
    public double addProbability_;
    public int archiveSize_;
    public int tSize_;

    private HashMap<String, Operator> ListOfMutAndSel = new HashMap<>();

    public SPEA2_Settings(String ins)
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
        maxGenerations_ = 500;
        mutationProbability_ = 1.0;
        crossoverProbability_ = 1.0;
        mutationName_ = "RouteSetAddDelMutation";
        SelectionName_ = "RandomSelection";
        addProbability_ = 0.5;
        populationSize_ = 112;
        archiveSize_ = 112;
        tSize_ = 40;
    } // SPEA2_Settings

    @Override
    public Algorithm configure() throws JMException
    {
        Algorithm algorithm; // The algorithm to use
        Operator crossover; // Crossover operator
        Operator mutation; // Mutation operator
        Operator selection;

        HashMap parameters; // Operator parameters

        algorithm = new SPEA2(problem_);

        algorithm.setInputParameter("populationSize", populationSize_);
        algorithm.setInputParameter("archiveSize", archiveSize_);
        algorithm.setInputParameter("maxEvaluations", maxGenerations_*populationSize_+populationSize_);

        parameters = new HashMap();
        parameters.put("probability", crossoverProbability_);
        crossover = new RouteSetCrossover(parameters);

//        parameters = new HashMap();
//        parameters.put("probability", mutationProbability_);
//        parameters.put("addProbability", addProbability_);
//        mutation = new RouteSetAddDelMutation(parameters);
        mutation = ListOfMutAndSel.get(mutationName_);

        // Selection operator 
        parameters = null;
        selection = SelectionFactory.getSelectionOperator("BinaryTournament", parameters);

        // Add the operators to the algorithm
        algorithm.addOperator("crossover", crossover);
        algorithm.addOperator("mutation", mutation);
        algorithm.addOperator("selection", selection);

        return algorithm;
    }

}
