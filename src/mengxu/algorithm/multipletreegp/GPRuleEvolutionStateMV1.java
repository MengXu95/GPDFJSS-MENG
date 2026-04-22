package mengxu.algorithm.multipletreegp;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.*;
import ec.simple.SimpleEvolutionState;
import ec.util.Checkpoint;
import ec.util.Parameter;
import mengxu.algorithm.diversitymeasure.*;
import mengxu.mcts.Mcts;
import yimei.jss.gp.GPRuleEvolutionState;
import yimei.jss.gp.function.*;
import yimei.jss.gp.terminal.AttributeGPNode;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.niching.RoutingPhenoCharacterisation;
import yimei.jss.niching.SequencingPhenoCharacterisation;
import yimei.jss.niching.phenotypicForSurrogateV1;
import yimei.jss.rule.AbstractRuleHelper;
import yimei.jss.ruleoptimisation.RuleOptimizationProblem;

import java.io.*;
import java.util.*;

/**
 * The evolution state of evolving dispatching rules with GP.
 *
 * @author mengxu (Nora)
 *
 */

public class GPRuleEvolutionStateMV1 extends GPRuleEvolutionState {

	/**
	 * Read the file to specify the terminals.
	 */

	public static final String P_FUNCTIONSET = "fset";
	public static final String P_MCTS_POLICY = "mcts-policy";

	public String mctsPolicy;


	ArrayList<ArrayList<Double>> storeGenDiversities = new ArrayList<>();

//	public Mcts[] mctses;

	@Override
	public void setup(EvolutionState state, Parameter base) {
		super.setup(this, base);
		this.mctsPolicy = state.parameters.getString(new Parameter(P_MCTS_POLICY), null);

		// Load my function set
//		String s = state.parameters.getString(base.push(P_FUNCTIONSET),null);
//		String s = "f0";
//		if (s==null)
//			state.output.fatal("No function set given for the GPTreeConstraints ");
//		GPFunctionSet functionset = GPFunctionSet.functionSetFor(s,state);


		List<GPNode> availTerminalGPNodes0 = new ArrayList<>(Arrays.asList(this.getTerminals(0)));
//		Collections.addAll(availGPNodes0, functionset.nodes[0]);
		List<GPNode> availFunctionGPNodes0 = new ArrayList<>();
		availFunctionGPNodes0.add(new Add());
		availFunctionGPNodes0.add(new Div());
		availFunctionGPNodes0.add(new Max());
		availFunctionGPNodes0.add(new Min());
		availFunctionGPNodes0.add(new Mul());
		availFunctionGPNodes0.add(new Sub());
		this.mctses[0] = new Mcts(availTerminalGPNodes0, availFunctionGPNodes0,false);


		List<GPNode> availTerminalGPNodes1 = new ArrayList<>(Arrays.asList(this.getTerminals(0)));
//		Collections.addAll(availGPNodes1, functionset.nodes[1]);
		List<GPNode> availFunctionGPNodes1 = new ArrayList<>();
		availFunctionGPNodes1.add(new Add());
		availFunctionGPNodes1.add(new Div());
		availFunctionGPNodes1.add(new Max());
		availFunctionGPNodes1.add(new Min());
		availFunctionGPNodes1.add(new Mul());
		availFunctionGPNodes1.add(new Sub());
		this.mctses[1] = new Mcts(availTerminalGPNodes1, availFunctionGPNodes1,false);



	}

	public Mcts[] getMctses() {
		return mctses;
	}

	@Override
	public int evolve() {
	    if (generation > 0)
	        output.message("Generation " + generation);

	    //System.out.println("generation "+generation);
	    // EVALUATION
	    statistics.preEvaluationStatistics(this);

	    evaluator.evaluatePopulation(this);  //// here, after this we evaluate the population
	    statistics.postEvaluationStatistics(this);

		//modified by mengxu. Measure diversity. 2021.04.15-------------------------
		Individual[] individuals = this.population.subpops[0].individuals;
		GenotypeDiversity genoD = new GenotypeDiversity();
		double genoDvalue = (double)genoD.genotypeDiversity(individuals) / individuals.length;
//		System.out.println("Genotype diversity: " + genoDvalue);
		PhenotypeDiversity phenoD = new PhenotypeDiversity();
		double phenoDvalue = (double)phenoD.phenotypeDiversity(individuals) / individuals.length;
//		System.out.println("Phenotype diversity: " + phenoDvalue);
		EntropyDiversity entroD = new EntropyDiversity();
		double entroDvalue = entroD.entropyDiversity(individuals);
//		System.out.println("Entropy diversity: " + entroDvalue);
		PseudoIsomorphsDiversity pseIsoD = new PseudoIsomorphsDiversity();
		double pseIsoDvalue = (double)pseIsoD.pseudoIsomorphsDiversity(individuals) / individuals.length;
//		System.out.println("Pseudo isomorphs diversity: " + pseIsoDvalue);
		EditDistanceDiversityV1 edit1D = new EditDistanceDiversityV1();
		double edit1Dvalue = edit1D.editDistanceDiversityV1(individuals, bestIndi(0));//todo: need modified.
//		System.out.println("Edit 1 diversity: " + edit1Dvalue);
		EditDistanceDiversityV2 edit2D = new EditDistanceDiversityV2();
		double edit2Dvalue = edit2D.editDistanceDiversityV2(individuals, bestIndi(0));//todo: need modified.
//		System.out.println("Edit 2 diversity: " + edit2Dvalue);


		//-------------------------------------------------------------------

		//calculate the pc of each individual in each subpop and do cluster--------------
		PhenoCharacterisation[] phenoCharacterisation = new PhenoCharacterisation[2];
		//dynamic simulation
		phenoCharacterisation[0] =
				SequencingPhenoCharacterisation.defaultPhenoCharacterisation();
		phenoCharacterisation[1] =
				RoutingPhenoCharacterisation.defaultPhenoCharacterisation();
		double[][][] indsCharListsMultiTree = phenotypicForSurrogateV1.phenotypicPopulationFixedDecisions(this, phenoCharacterisation, true); //3. calculate the phenotypic characteristic
		//get the fitness for training model
		double[][] fitnessesForModel = new double[this.population.subpops.length][this.population.subpops[0].individuals.length];
		for(int subpop = 0; subpop < this.population.subpops.length; subpop++) {
			for (int ind = 0; ind < this.population.subpops[subpop].individuals.length; ind++) {
				fitnessesForModel[subpop][ind] = this.population.subpops[subpop].individuals[ind].fitness.fitness();
			}
		}
		PhenotypicCharacteristicDiversity pcD = new PhenotypicCharacteristicDiversity();
		double pcDvalue = (double)pcD.phenotypicCharacteristicDiversity(indsCharListsMultiTree[0]) / individuals.length;
		System.out.println("PC diversity: " + pcDvalue);

		//modified by mengxu. save diversities.
		ArrayList<Double> diversities = new ArrayList<>();
		diversities.add((double)generation);
		diversities.add(genoDvalue);
		diversities.add(phenoDvalue);
		diversities.add(entroDvalue);
		diversities.add(pseIsoDvalue);
		diversities.add(edit1Dvalue);
		diversities.add(edit2Dvalue);
		diversities.add(pcDvalue);
		storeGenDiversities.add(diversities);



		//modified by mengxu 2021.04.16. update totalreward of MCTSTree
		if(this.mctsPolicy.equals("totalReward-div-visitCount-linked") ||
				this.mctsPolicy.equals("totalReward-div-visitCount")){
			for(int i=0; i<individuals.length; i++){
				Individual ind = individuals[i];
				GPTree gpTree0 = ((GPIndividual)ind).trees[0];
				GPTree gpTree1 = ((GPIndividual)ind).trees[1];
				this.mctses[0].frontPropagateTotalReward(gpTree0, ind.fitness.fitness());
				this.mctses[1].frontPropagateTotalReward(gpTree1, ind.fitness.fitness());
			}
		}
		else if(this.mctsPolicy.equals("num-infinity-reward") ||
				this.mctsPolicy.equals("mean-non-infinity-reward")){
			for(int i=0; i<individuals.length; i++){
				Individual ind = individuals[i];
				GPTree gpTree0 = ((GPIndividual)ind).trees[0];
				GPTree gpTree1 = ((GPIndividual)ind).trees[1];
				this.mctses[0].frontPropagateAllReward(this.generation, gpTree0, ind.fitness.fitness());
				this.mctses[1].frontPropagateAllReward(this.generation, gpTree1, ind.fitness.fitness());
			}
		}

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
			writeDiversityToFile();//modified by mengxu	2021.04.15
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
	        /*
	         * Don't really know what to return here.  The only place I could
	         * find where runComplete ever returns non-null is
	         * IslandExchange.  However, that can return non-null whether or
	         * not the ideal individual was found (for example, if there was
	         * a communication error with the server).
	         *
	         * Since the original version of this code didn't care, and the
	         * result was initialized to R_SUCCESS before the while loop, I'm
	         * just going to return R_SUCCESS here.
	         */

	        return R_SUCCESS;
	        }

	    // BREEDING
	    statistics.preBreedingStatistics(this);

	    population = breeder.breedPopulation(this); //!!!!!!   return newpop;  if it is NSGA-II, the population here is 2N

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

	//2021.2.15 modified by mengxu
	public void writeDiversityToFile(){
		File diversities = new File("job." + jobSeed + ".diversities.csv"); //successedTransfer[i][j]: task j makes a successful transfer for task i.
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(diversities));
			writer.write("Gen, Geno, Pheno, Entropy, PseIso, Edit 1, Edit 2, PC");
			writer.newLine();
			for (int ind = 0; ind < storeGenDiversities.size(); ind++) {
				ArrayList<Double> ref = storeGenDiversities.get(ind);
				writer.write(ref.get(0) + "," + ref.get(1) + "," + ref.get(2)
						+ "," + ref.get(3) + "," + ref.get(4) + "," + ref.get(5)
						+ "," + ref.get(6) + "," + ref.get(7));
				writer.newLine();
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
