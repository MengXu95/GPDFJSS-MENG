package mengxu.algorithm.KNNsurrogate;

import ec.Individual;
import ec.Population;
import ec.util.Checkpoint;
import ec.util.Parameter;
import org.apache.commons.lang3.ArrayUtils;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.helper.PopulationUtils;
import yimei.jss.niching.surrogateClearingMultitreeEvaluatorV1;
import yimei.jss.niching.surrogateClearingMultitreeEvaluatorV2;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.util.ArrayList;
import java.util.List;

//fzhang 2019.9.11 change the steps of evolutionary process --- intermediate population
public class KNNsurrogateGPRuleEvolutionState extends GPRuleEvolutionState {
    public static final String P_REPLICATIONS = "num-Rep";
    public int numRep;

    public static final String P_ELITES = "num-elites";
    public int numElites;

    public int evolve() {
        if (generation > 0)
            output.message("Generation " + generation);

        statistics.preEvaluationStatistics(this);

        evaluator.evaluatePopulation(this);  //here, after this we evaluate the population
        //((MultiPopCoevolutionaryClearingEvaluator)evaluator).setClear(false);

        statistics.postEvaluationStatistics(this);

        // SHOULD WE QUIT?
        if (evaluator.runComplete(this) && quitOnRunComplete)
        {
            output.message("Found Ideal Individual");
            return R_SUCCESS;
        }
        // SHOULD WE QUIT?
        if (generation == numGenerations-1)
        {
            generation++; // in this way, the last generation value will be printed properly.  fzhang 28.3.2018
            return R_FAILURE;
        }

        // PRE-BREEDING EXCHANGING
        statistics.prePreBreedingExchangeStatistics(this);
        population = exchanger.preBreedingExchangePopulation(this);  /** Simply returns state.population. */
        statistics.postPreBreedingExchangeStatistics(this);

        String exchangerWantsToShutdown = exchanger.runComplete(this);  /** Always returns null */
        if (exchangerWantsToShutdown!=null)
        {
            output.message(exchangerWantsToShutdown);
            return R_SUCCESS;
        }

        // BREEDING
        statistics.preBreedingStatistics(this);
        population = preselection();//actually, breed intermediate population and then resize the population to the normal size.  pre-selection

        //population = breeder.breedPopulation(this); //!!!!!!   return newpop;  if it is NSGA-II, the population here is 2N

        // POST-BREEDING EXCHANGING
        statistics.postBreedingStatistics(this);   //position 1  here, a new pop has been generated.

        // POST-BREEDING EXCHANGING
        statistics.prePostBreedingExchangeStatistics(this);
        population = exchanger.postBreedingExchangePopulation(this);   /** Simply returns state.population. */
        statistics.postPostBreedingExchangeStatistics(this);  //position 2

        // Generate new instances if needed
        RuleOptimizationProblem problem = (RuleOptimizationProblem)evaluator.p_problem;
        if (problem.getEvaluationModel().isRotatable()) {
            problem.rotateEvaluationModel();
        }

        // INCREMENT GENERATION AND CHECKPOINT
        generation++;
        if (checkpoint && generation%checkpointModulo == 0)
        {
            output.message("Checkpointing");
            statistics.preCheckpointStatistics(this);
            Checkpoint.setCheckpoint(this);
            statistics.postCheckpointStatistics(this);
        }

        return R_NOTDONE;
    }

    public Population preselection(){

        //ensure the elites can be saved in next generation
        ArrayList<Individual[]> elites = new ArrayList<Individual[]>(this.population.subpops.length);

        //2021.4.15 fzhang since this is multi-objective, we can not use sort to rank the individuals directly.
        //in fact, the individuals has been ranked during the evaluation process with buildArchive function. We do not need to sort any more.
//        PopulationUtils.sort(this.population);

        numElites = this.parameters.getIntWithDefault(new Parameter(P_ELITES), null, 1);
        for(int pop = 0; pop < this.population.subpops.length; pop++){
            List<Individual> tempElites = new ArrayList<>();
            for(int e = 0; e < numElites; e++){
                tempElites.add(this.population.subpops[pop].individuals[e]);
                }
            if(elites.size() == 0 || elites.size() == 1 || elites.size() == 2){
                elites.add(pop, tempElites.toArray(new Individual[tempElites.size()]));
            }
            else{
                Individual[] combineElites = ArrayUtils.addAll(elites.get(pop), tempElites.toArray(new Individual[tempElites.size()]));
                elites.set(pop, combineElites);
            }
        }

        Population newPop = (Population) this.population.emptyClone();//save the population with k*populationsize individuals
        Population tempNewPop; //save the population with populationsize individuals for combining them together to newPop
        numRep = this.parameters.getIntWithDefault(new Parameter(P_REPLICATIONS), null, 1);
        for(int i = 0; i < numRep; i++){
            tempNewPop = breeder.breedPopulation(this); //breed subpops one by one
            for(int sub = 0; sub < this.population.subpops.length; sub++){
                //combinedInds = new Individual[subpopsLength];
                //System.arraycopy(tempNewPop.subpops[sub].individuals, 0, combinedInds, 0, tempNewPop.subpops[sub].individuals.length);
                if (i == 0){
                    newPop.subpops[sub].individuals = tempNewPop.subpops[sub].individuals;
                }
                else{
                    newPop.subpops[sub].individuals = ArrayUtils.addAll(newPop.subpops[sub].individuals, tempNewPop.subpops[sub].individuals);
                }
            }
        }

        population = newPop;

        //evaluate the population based on surrogate model
        evaluator.evaluatePopulation(this);

        //2021.4.15 fzhang since this is multi-objective, we can not use sort to rank the individuals directly.
        //PopulationUtils.sort(population);
        //as a baseline, we need to use dominace relation to get the ranks of individuals. And fill the new population from rank 0 to rank **
        //1. evaluate the population based on NSGA2Evaluator, but do not need to re-evaluate all the n*popsize inds, we already have the estimated fitness
        // and the individuals are sorted based on the dominace relation. This is done by the evaluator of NSGA-II automatically
//        ((surrogateClearingMultitreeEvaluatorV1)evaluator).evaluatePopulation(this); //two functions here, 1. sort the individual, 2. only save original popsize inds
        //this is expected not good enough, since the inds in KNN are more likely non-dominated, which is hard to distinguish the quality of inds in intermediate pop.

//        ((surrogateClearingMultitreeEvaluatorV2)evaluator).evaluatePopulation(this, true);
        ((surrogateClearingMultitreeEvaluatorV2ForSingleObjective)evaluator).evaluatePopulation(this, true);

        //as proposed algorithms, the individuals in the KNN have ranks (sorting), we just need the sorting values to rank the individuals in the intermediate pop


//        PopulationUtils.sort(this.population); //add by mengxu to sort individuals 2022.03.17

        //fzhang 2021.2.15 may need to change to fill the population one by one, since there is no sorting anymore.
//        for(int sub = 0; sub < this.population.subpops.length; sub++){
//            population.subpops[sub].resize(population.subpops[sub].individuals.length/numRep);
//        }

        for(int sub = 0; sub < this.population.subpops.length; sub++){
            int e = 0;
                for(int replace = population.subpops[sub].individuals.length -1; replace >= population.subpops[sub].individuals.length - numElites; replace--){
                    population.subpops[sub].individuals[replace] = elites.get(sub)[e];
                    e++;
            }
        }
        return population;
    }
}