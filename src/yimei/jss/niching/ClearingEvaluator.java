package yimei.jss.niching;

import ec.EvolutionState;
import ec.simple.SimpleEvaluator;
import ec.util.Parameter;
import mengxu.algorithm.MAPElites.CVTMAPElites.GPRuleEvolutionStateCVTMAPElites;
import mengxu.algorithm.MAPElites.CVTMAPElites.MultiCaseBehaviour.GPRuleEvolutionStateCVTMultiCaseMAPElites;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.ruleoptimisation.MultipleTreeRuleOptimizationProblem;
import yimei.jss.simulation.Simulation;

/**
 * The evaluator with population clearing.
 * The evaluator is used for niching.
 *
 * Created by YiMei on 3/10/16.
 */
public class ClearingEvaluator extends SimpleEvaluator {

    public static final String P_RADIUS = "radius";
    public static final String P_CAPACITY = "capacity";

    protected boolean clear = true;

    protected double radius;
    protected int capacity;

    public static PhenoCharacterisation[] phenoCharacterisation;

    public double getRadius() {
        return radius;
    }

    public int getCapacity() {
        return capacity;
    }

    public PhenoCharacterisation[] getPhenoCharacterisation() {
        return phenoCharacterisation;
    }

    public PhenoCharacterisation getPhenoCharacterisation(int index) {
        return phenoCharacterisation[index];
    }

    public void setup(final EvolutionState state, final Parameter base) {
        super.setup(state, base);

        radius = state.parameters.getDoubleWithDefault(
                base.push(P_RADIUS), null, 0.0);
        capacity = state.parameters.getIntWithDefault(
                base.push(P_CAPACITY), null, 1);
        String filePath = state.parameters.getString(new Parameter("filePath"), null);
        //It's a little tricky to know whether we have 1 or 2 populations here, so we will assume
        //2 for the purpose of the phenoCharacterisation, and ignore the second object if only
        //1 is used
        phenoCharacterisation = new PhenoCharacterisation[2];
        if (filePath == null) {
            //dynamic simulation
            //modified by mengxu 2023.03.31
            Simulation simulation = ((MultipleRuleEvaluationModel)((MultipleTreeRuleOptimizationProblem)state.evaluator.p_problem).getEvaluationModel()).getSchedulingSet().getSimulations().get(0);;
            phenoCharacterisation[0] =
                    SequencingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
            phenoCharacterisation[1] =
                    RoutingPhenoCharacterisation.defaultPhenoCharacterisation(simulation);
        } else {
            //static simulation
            phenoCharacterisation[0] =
                    SequencingPhenoCharacterisation.defaultPhenoCharacterisation(filePath);
            phenoCharacterisation[1] =
                    RoutingPhenoCharacterisation.defaultPhenoCharacterisation(filePath);
        }
    }

    @Override
    public void evaluatePopulation(final EvolutionState state) {
        super.evaluatePopulation(state);

        for(int i=0; i<state.population.subpops.length; i++){
            state.totalEvaluationTime += state.population.subpops[i].individuals.length;
        }
        
      //fzhang  2018.10.9  this is for single population, no business with Co-evolution.
        if(clear) {
//        	System.out.print("clearingEvalutor");
//            Clearing.clearPopulation(state, radius, capacity,
//                    phenoCharacterisation);
            if(state instanceof GPRuleEvolutionStateCVTMAPElites){//modified by mengxu 2023.10.11
                multitreeClearingFirst.clearPopulation(state, radius, capacity,
                        ((GPRuleEvolutionStateCVTMAPElites) state).phenoCharacterisation);
            }
            else if(state instanceof GPRuleEvolutionStateCVTMultiCaseMAPElites){//modified by mengxu 2023.10.11
                multitreeClearingFirst.clearPopulation(state, radius, capacity,
                        ((GPRuleEvolutionStateCVTMultiCaseMAPElites) state).phenoCharacterisation);
            }
            else{
                multitreeClearingFirst.clearPopulation(state, radius, capacity,
                        phenoCharacterisation);
            }
        }
    }

    public void setClear(boolean clear) {
        this.clear = clear;
    }
}
