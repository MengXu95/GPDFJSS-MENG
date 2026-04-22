package mengxu.ruleanalysis.NSGAII;

import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.RuleTypeV2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Add by mengxu to store all the Pareto fronts of not only the final generation, but inter generation
 * 2022.08.11
 */
public class ParetoFrontTestResult {

	private List<List<GPRule[]>> generationalParetoFrontRules;
	private List<List<Fitness>> generationalTrainParetoFrontFitnesses;  //generational things is a list to contain informaiton related
	private List<List<Fitness>> generationalValidationParetoFrontFitnesses;
	private List<List<Fitness>> generationalTestParetoFrontFitnesses;

	//selected subset individuals based on validation, implemengted by meng xu 2022.08.12
	private List<GPRule[]> subsetGPRulesSelectedByValidation;
	//selected subset individuals' fitness based on validation, implemengted by meng xu 2022.08.12
	private List<Fitness> subsetFitnessesSelectedByValidation;

	private List<Fitness> subsetTestFitnessesSelectedByValidation;
	private List<GPRule[]> bestParetoFrontRules;
	private List<Fitness> bestTrainingParetoFrontFitness;
	private List<Fitness> bestValidationParetoFrontFitness;
	private List<Fitness> bestTestParetoFrontFitness;
	private DescriptiveStatistics generationalTimeStat;
	//fzhang 24.8.2018 read badrun into CSV
	private DescriptiveStatistics generationalBadRunStat;
	private DescriptiveStatistics generationalGenotypeDiversityStat;
	private DescriptiveStatistics generationalPhenotypeDiversityStat;
	private DescriptiveStatistics generationalEntropyDiversityStat;
	private DescriptiveStatistics generationalPseudoIsomorphsDiversityStat;
	private DescriptiveStatistics generationalEditOneDiversityStat;
	private DescriptiveStatistics generationalEditTwoDiversityStat;
	private DescriptiveStatistics generationalPCDiversityStat;
	private DescriptiveStatistics generationalParentSelectionDiversityStat;

	//fzhang 31.5.2019 read average rule size into CSV
	private DescriptiveStatistics generationalAveRoutingRuleSizeStat;
	private DescriptiveStatistics generationalAveSequencingRuleSizeStat;
	private DescriptiveStatistics generationalAveRuleSizeStat;

	//mengxu 2022.01.19 read combination number
	private DescriptiveStatistics generationalCombinationNumberStat;
	private DescriptiveStatistics generationalXoverProbabilityStat;


	//fzhang 2019.1.14 read training fitness into CSV
	private DescriptiveStatistics generationalTrainingFitnessStat0;
	private DescriptiveStatistics generationalTrainingFitnessStat1;

	public static final long validationSimSeed = 483561;

	public ParetoFrontTestResult() {
		generationalParetoFrontRules = new ArrayList<>();
		generationalTrainParetoFrontFitnesses = new ArrayList<>();
		generationalValidationParetoFrontFitnesses = new ArrayList<>();
		generationalTestParetoFrontFitnesses = new ArrayList<>();
		subsetGPRulesSelectedByValidation = new ArrayList<>();
		subsetFitnessesSelectedByValidation = new ArrayList<>();
		subsetTestFitnessesSelectedByValidation = new ArrayList<>();
	}

	public List<List<GPRule[]>> getGenerationalParetoFrontRules() {
		return generationalParetoFrontRules;
	}

	public List<GPRule[]> getGenerationalParetoFrontRulesFromFinalPop() {
		return generationalParetoFrontRules.get(generationalParetoFrontRules.size()-1);
	}

	public List<GPRule[]> getGenerationalParetoFrontRules(int idx) {
		return generationalParetoFrontRules.get(idx);
	}

	public List<List<Fitness>> getGenerationalTrainParetoFrontFitnesses() {
		return generationalTrainParetoFrontFitnesses;
	}

	public List<Fitness> getGenerationalTrainParetoFrontFitness(int idx) {
		return generationalTrainParetoFrontFitnesses.get(idx);
	}

	public List<List<Fitness>> getGenerationalValidationParetoFrontFitnesses() {
		return generationalValidationParetoFrontFitnesses;
	}

	public List<Fitness> getGenerationalValidationParetoFrontFitness(int idx) {
		return generationalValidationParetoFrontFitnesses.get(idx);
	}

	public List<List<Fitness>> getGenerationalTestParetoFrontFitnesses() {
		return generationalTestParetoFrontFitnesses;
	}

	public List<Fitness> getGenerationalTestParetoFrontFitness(int idx) {
		return generationalTestParetoFrontFitnesses.get(idx);
	}

	public List<GPRule[]> getBestParetoFrontRules() {
		return bestParetoFrontRules;
	}

	public List<Fitness> getBestTrainingParetoFrontFitness() {
		return bestTrainingParetoFrontFitness;
	}

	public List<Fitness> getBestValidationParetoFrontFitness() {
		return bestValidationParetoFrontFitness;
	}

	public List<Fitness> getBestTestParetoFrontFitness() {
		return bestTestParetoFrontFitness;
	}

	public DescriptiveStatistics getGenerationalTimeStat() {
		return generationalTimeStat;
	}

	public double getGenerationalTime(int gen) {
		return generationalTimeStat.getElement(gen);
	}

	//fzhang 24.8.2018 get the badrun into CSV
	public DescriptiveStatistics getGenerationalBadRunStat() {
		return generationalBadRunStat;
	}

	public double getGenerationalBadRun(int gen) {
		return generationalBadRunStat.getElement(gen);
	}

	//fzhang 2019.6.1 get the average routing rule size into CSV
	//===========================start========================
	public DescriptiveStatistics getGenerationalAveRoutingRuleSizeStatStat() {
		return generationalAveRoutingRuleSizeStat;
	}
	public double getGenerationalAveRoutingRuleSizeStatStat(int gen) {
		return generationalAveRoutingRuleSizeStat.getElement(gen);
	}
    //======================================end================================

	//fzhang 2019.6.1 get the average routing rule size into CSV
	//===================================start==================================
	public DescriptiveStatistics getGenerationalAveSequencingRuleSizeStatStat() { return generationalAveSequencingRuleSizeStat;	}

	public double getGenerationalAveSequencingRuleSizeStatStat(int gen) {
		return generationalAveSequencingRuleSizeStat.getElement(gen);
	}
	//====================================end===================================

	//fzhang 2019.6.1 get the average rule size into CSV
	//===================================start==================================
	public DescriptiveStatistics getGenerationalAveRuleSizeStatStat() { return generationalAveRuleSizeStat;}

	public double getGenerationalAveRuleSizeStatStat(int gen) {
		return generationalAveRuleSizeStat.getElement(gen);
	}
	//====================================end===================================

	// fzhang 2019.1.14 get the training fitness into CSV
	public DescriptiveStatistics getGenerationalTrainingFitnessStat0() {
		return generationalTrainingFitnessStat0;
	}

	public double getGenerationalTrainingFitness0(int gen) {
		return generationalTrainingFitnessStat0.getElement(gen);
	}
	
	public DescriptiveStatistics getGenerationalTrainingFitnessStat1() {
		return generationalTrainingFitnessStat1;
	}

	public double getGenerationalTrainingFitness1(int gen) {
		return generationalTrainingFitnessStat1.getElement(gen);
	}
//=========================================================================
	public void setGenerationalParetoFrontRules(List<List<GPRule[]>> generationalParetoFrontRules) {
		this.generationalParetoFrontRules = generationalParetoFrontRules;
	}

	public void addGenerationalParetoFrontRules(List<GPRule[]> ParetoFrontRules) {
		this.generationalParetoFrontRules.add(ParetoFrontRules);
	}

	public void setGenerationalTrainParetoFrontFitnesses(List<List<Fitness>> generationalTrainParetoFrontFitnesses) {
		this.generationalTrainParetoFrontFitnesses = generationalTrainParetoFrontFitnesses;
	}

	public void addGenerationalTrainParetoFrontFitness(List<Fitness> f) {
		this.generationalTrainParetoFrontFitnesses.add(f);
	}

	public void setGenerationalValidationParetoFrontFitness(List<List<Fitness>> generationalValidationParetoFrontFitnesses) {
		this.generationalValidationParetoFrontFitnesses = generationalValidationParetoFrontFitnesses;
	}

	public void addGenerationalValidationParetoFrontFitnesses(List<Fitness> f) {
		this.generationalValidationParetoFrontFitnesses.add(f);
	}

	public void setGenerationalTestParetoFrontFitnesses(List<List<Fitness>> generationalTestParetoFrontFitnesses) {
		this.generationalTestParetoFrontFitnesses = generationalTestParetoFrontFitnesses;
	}

	public void addGenerationalTestParetoFrontFitnesses(List<Fitness> f) {
		this.generationalTestParetoFrontFitnesses.add(f);
	}

	public void setBestParetoFrontRules(List<GPRule[]> bestParetoFrontRules) {
		this.bestParetoFrontRules = bestParetoFrontRules;
	}

	public void setBestTrainingParetoFrontFitness(List<Fitness> bestTrainingParetoFrontFitness) {
		this.bestTrainingParetoFrontFitness = bestTrainingParetoFrontFitness;
	}

	public void setBestValidationParetoFrontFitness(List<Fitness> bestValidationParetoFrontFitnesses) {
		this.bestValidationParetoFrontFitness = bestValidationParetoFrontFitnesses;
	}

	public void setBestTestParetoFrontFitness(List<Fitness> bestTestParetoFrontFitnesses) {
		this.bestTestParetoFrontFitness = bestTestParetoFrontFitnesses;
	}

	public void setGenerationalTimeStat(DescriptiveStatistics generationalTimeStat) {
		this.generationalTimeStat = generationalTimeStat;
	}


	//2021.04.15 mengxu read diversities into CSV-------------------------------------------
	public void setCombinationNumberStat(DescriptiveStatistics generationalCombinationNumberStat) {
		this.generationalCombinationNumberStat = generationalCombinationNumberStat;
	}

	//----------------------------------------------------------

	public double getGenerationalCombinationNumberStat(int gen) {
		return generationalCombinationNumberStat.getElement(gen);
	}

	public double getGenerationalXoverProbabilityStat(int gen) {
		return generationalXoverProbabilityStat.getElement(gen);
	}

	public void setGenerationalCombinationNumberStat(DescriptiveStatistics generationalCombinationNumberStat) {
		this.generationalCombinationNumberStat = generationalCombinationNumberStat;
	}

	public void setGenerationalXoverProbabilityStat(DescriptiveStatistics generationalXoverProbabilityStat) {
		this.generationalXoverProbabilityStat = generationalXoverProbabilityStat;
	}

	//2021.04.15 mengxu read diversities into CSV-------------------------------------------
	public void setGenerationalGenotypeDiversityStat(DescriptiveStatistics generationalGenotypeDiversityStat) {
		this.generationalGenotypeDiversityStat = generationalGenotypeDiversityStat;
	}

	public void setGenerationalPhenotypeDiversityStat(DescriptiveStatistics generationalPhenotypeDiversityStat) {
		this.generationalPhenotypeDiversityStat = generationalPhenotypeDiversityStat;
	}

	public void setGenerationalEntropyDiversityStat(DescriptiveStatistics generationalEntropyDiversityStat) {
		this.generationalEntropyDiversityStat = generationalEntropyDiversityStat;
	}

	public void setGenerationalPseudoIsomorphsDiversityStat(DescriptiveStatistics generationalPseudoIsomorphsDiversityStat) {
		this.generationalPseudoIsomorphsDiversityStat = generationalPseudoIsomorphsDiversityStat;
	}

	public void setGenerationalEditOneDiversityStat(DescriptiveStatistics generationalEditOneDiversityStat) {
		this.generationalEditOneDiversityStat = generationalEditOneDiversityStat;
	}

	public void setGenerationalEditTwoDiversityStat(DescriptiveStatistics generationalEditTwoDiversityStat) {
		this.generationalEditTwoDiversityStat = generationalEditTwoDiversityStat;
	}

	public void setGenerationalPCDiversityStat(DescriptiveStatistics generationalPCDiversityStat) {
		this.generationalPCDiversityStat = generationalPCDiversityStat;
	}
	public void setGenerationalParentSelectionDiversityStat(DescriptiveStatistics generationalParentSelectionDiversityStat) {
		this.generationalParentSelectionDiversityStat = generationalParentSelectionDiversityStat;
	}

	public double getGenerationalParentSelectionDiversityStat(int gen) {
		return generationalParentSelectionDiversityStat.getElement(gen);
	}

	public double getGenerationalGenotypeDiversityStat(int gen) {
		return generationalGenotypeDiversityStat.getElement(gen);
	}

	public double getGenerationalPhenotypeDiversityStat(int gen) {
		return generationalPhenotypeDiversityStat.getElement(gen);
	}

	public double getGenerationalEntropyDiversityStat(int gen) {
		return generationalEntropyDiversityStat.getElement(gen);
	}

	public double getGenerationalPseudoIsomorphsDiversityStat(int gen) {
		return generationalPseudoIsomorphsDiversityStat.getElement(gen);
	}

	public double getGenerationalEditOneDiversityStat(int gen) {
		return generationalEditOneDiversityStat.getElement(gen);
	}

	public double getGenerationalEditTwoDiversityStat(int gen) {
		return generationalEditTwoDiversityStat.getElement(gen);
	}

	public double getGenerationalPCDiversityStat(int gen) {
		return generationalPCDiversityStat.getElement(gen);
	}

	//add by meng xu 2022.08.12
	public List<GPRule[]> getSubsetGPRulesSelectedByValidation() {
		return subsetGPRulesSelectedByValidation;
	}

	//add by meng xu 2022.08.12
	public List<Fitness> getSubsetFitnessesSelectedByValidation() {
		return subsetFitnessesSelectedByValidation;
	}

	//add by meng xu 2022.08.12
	public List<Fitness> getSubsetTestFitnessesSelectedByValidation() {
		return subsetTestFitnessesSelectedByValidation;
	}

	//----------------------------------------------------------------------

	//31.5.2019 fzhang read average routing rule size into CSV
	public void setGenerationalAveRoutingRuleSizeStat(DescriptiveStatistics generationalAveRoutingRuleSizeStat) {
		this.generationalAveRoutingRuleSizeStat = generationalAveRoutingRuleSizeStat;
	}

	//31.5.2019 fzhang read average sequencing rule size into CSV
	public void setGenerationalAveSequencingRuleSizeStat(DescriptiveStatistics generationalAveSequencingRuleSizeStat) {
		this.generationalAveSequencingRuleSizeStat = generationalAveSequencingRuleSizeStat;
	}

	//5.6.2019 fzhang read average rule size into CSV
	public void setGenerationalAveRuleSizeStat(DescriptiveStatistics generationalAveRuleSizeStat) {
		this.generationalAveRuleSizeStat = generationalAveRuleSizeStat;
	}

	//=======================================================================================
	//24.8.2018 fzhang read badrun into CSV
	public void setGenerationalBadRunStat(DescriptiveStatistics generationalBadRunStat) {
		this.generationalBadRunStat = generationalBadRunStat;
	}
	//======================================================================================
	
	// =======================================================================================
	// 24.8.2018 fzhang read trainingfitness into CSV
	public void setGenerationalTrainingFitnessStat0(DescriptiveStatistics generationalTrainingFitnessStat0) {
		this.generationalTrainingFitnessStat0 = generationalTrainingFitnessStat0;
	}
	
	public void setGenerationalTrainingFitnessStat1(DescriptiveStatistics generationalTrainingFitnessStat1) {
		this.generationalTrainingFitnessStat1 = generationalTrainingFitnessStat1;
	}
	// ======================================================================================
	
	public static ParetoFrontTestResult readFromFile(File file, RuleTypeV2 ruleType, int numPopulations) {
		return NSGAIIMultipleTreeResultFileReader.readNSGAIIParetoFrontForGenerationFromFile(file, ruleType, ruleType.isMultiobjective(), numPopulations);
	}
	public SchedulingSet generateValidationSet(long simSeed,
											   String scenario,
											   String setName,
											   List<Objective> objectives,
											   int replications) {
		return SchedulingSet.generateSet(simSeed, scenario,
				setName, objectives, replications);//todo:need modified to 50/30
	}


	//implemented by mengxu 2022.08.12 to select a subset of individuals for test
	public List<List> validateAndSubsetSelection(int num,
												 long simSeed,
												 String scenario,
												 String setName,
												 List<Objective> objectives,
												 int replications) {
		SchedulingSet validationSet =
				generateValidationSet(simSeed, scenario,
						setName, objectives, replications);

		bestValidationParetoFrontFitness = new ArrayList<>();

		bestParetoFrontRules = new ArrayList<>();//used to store the Pareto front (subset selection individuals) from all the generation

		List<GPRule[]> allGPRules = new ArrayList<>();
		List<Fitness> allFitness = new ArrayList<>();

//		generationalValidationParetoFrontFitnesses.add(bestValidationParetoFrontFitness);

		for(int j = 0; j < generationalParetoFrontRules.size(); j++) {
			List<Fitness> validationParetoFrontFitness = new ArrayList<>();
			List<GPRule[]> generationalParetoFrontForGenRules = generationalParetoFrontRules.get(j);
			for(int p = 0; p < generationalParetoFrontForGenRules.size(); p++){
				GPRule[] generationalRules = generationalParetoFrontForGenRules.get(p);
				MultiObjectiveFitness validationFitness = new MultiObjectiveFitness();
				if (generationalRules.length == 2) {
					generationalRules[0].calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
							validationFitness, null,
							validationSet, generationalRules[1], objectives);
					validationParetoFrontFitness.add(validationFitness);
				}
				allGPRules.add(generationalRules);
				allFitness.add(validationFitness);
			}
			generationalValidationParetoFrontFitnesses.add(validationParetoFrontFitness);
		}

		//build the Pareto front of the individuals from all generation based on the validation set
		MultiObjectiveFitness typicalFitness = (MultiObjectiveFitness) allFitness.get(0);
		// build front
		List<ArrayList> frontWithFrontFitness = buildParetoFront(allGPRules, allFitness, null, null,null, null);

		// sort by objective[0]
		Object[] sortedFront = frontWithFrontFitness.get(0).toArray();
		Object[] sortedFrontFitness = frontWithFrontFitness.get(1).toArray();
		Object[] sortedNonFront = frontWithFrontFitness.get(2).toArray();
		Object[] sortedNonFrontFitness = frontWithFrontFitness.get(3).toArray();

		//front sorting implemented by mengxu 2022.08.12
		for(int i=0; i< sortedFront.length-1; i++){
			for(int j=i+1; j<sortedFront.length; j++){
				if(((MultiObjectiveFitness)sortedFrontFitness[i]).getObjective(0) > ((MultiObjectiveFitness)sortedFrontFitness[j]).getObjective(0)){
					Object midFront = sortedFront[i];
					Object midFrontFitness = sortedFrontFitness[i];
					sortedFront[i] = sortedFront[j];
					sortedFrontFitness[i] = sortedFrontFitness[j];
					sortedFront[j] = midFront;
					sortedFrontFitness[j] = midFrontFitness;
				}
			}
		}

		List<GPRule[]> subsetGPRules = new ArrayList<>();
		List<Fitness> subsetFitness = new ArrayList<>();
		List<Fitness> subsetTestFitness = new ArrayList<>();

		int currentNum = 0;
		while(currentNum < num && currentNum < sortedFront.length + sortedNonFront.length){
			while(currentNum < sortedFront.length){
				subsetGPRules.add((GPRule[])(sortedFront[currentNum]));
				subsetFitness.add((Fitness)(sortedFrontFitness[currentNum]));
				subsetTestFitness.add((Fitness)((Fitness)(sortedFrontFitness[currentNum])).clone());
				currentNum++;
			}
			while(currentNum-sortedFront.length < sortedNonFront.length){
				subsetGPRules.add((GPRule[])(sortedNonFront[currentNum-sortedFront.length]));
				subsetFitness.add((Fitness)(sortedNonFrontFitness[currentNum-sortedFront.length]));
				subsetTestFitness.add((Fitness)((Fitness)(sortedNonFrontFitness[currentNum-sortedFront.length])).clone());
				currentNum++;
			}
		}

		List<List> subsetGPRulesWithFitness = new ArrayList<>();
		subsetGPRulesWithFitness.add(subsetGPRules);
		subsetGPRulesWithFitness.add(subsetFitness);

		this.subsetGPRulesSelectedByValidation = subsetGPRules;
		this.subsetFitnessesSelectedByValidation = subsetFitness;
		this.subsetTestFitnessesSelectedByValidation = subsetTestFitness;
		return subsetGPRulesWithFitness;
	}


	//add by mengxu 2022.08.12 used to build Pareto front based on Fangfang's code
	public List<ArrayList> buildParetoFront(List<GPRule[]> inds, List<Fitness> allFitness,
									  ArrayList front, ArrayList frontFitness,
									  ArrayList nonFront, ArrayList nonFrontFitness)
	{
		if(front == null){
			front = new ArrayList();
			frontFitness = new ArrayList();
			nonFront = new ArrayList();
			nonFrontFitness = new ArrayList();
		}

		// put the first guy in the front
		front.add(inds.get(0));
		frontFitness.add(allFitness.get(0));

		// iterate over all the remaining individuals
		for (int i = 1; i < inds.size(); i++)
		{
			GPRule[] ind = inds.get(i);
			Fitness indFitness = allFitness.get(i);

			boolean noOneWasBetter = true;
			int frontSize = front.size();

			// iterate over the entire front
			for (int j = 0; j < frontSize; j++)
			{
				GPRule[] frontmember = (GPRule[])front.get(j);
				Fitness frontmemberFitness = (Fitness)frontFitness.get(j);

				// if the front member is better than the individual, dump the individual and go to the next one
				if (paretoDominates(((MultiObjectiveFitness) (frontmemberFitness)),(MultiObjectiveFitness) (indFitness)))
				{
					if(nonFront != null){
						nonFront.add(ind);
						nonFrontFitness.add(indFitness);
					}
					noOneWasBetter = false;
					break;  // failed.  He's not in the front
				}
				// if the individual was better than the front member, dump the front member.  But look over the
				// other front members (don't break) because others might be dominated by the individual as well.
				else if (paretoDominates(((MultiObjectiveFitness) (indFitness)), (MultiObjectiveFitness) (frontmemberFitness)))
				{
					yank(j, front, frontFitness);
					// a front member is dominated by the new individual.  Replace him
					frontSize--; // member got removed
					j--;  // because there's another guy we now need to consider in his place
					if (nonFront != null){
						nonFront.add(frontmember);
						nonFrontFitness.add(frontmemberFitness);
					}
				}
			}
			if (noOneWasBetter){
				front.add(ind);
				frontFitness.add(indFitness);
			}
		}
		List<ArrayList> frontWithFrontFitness = new ArrayList<>();
		frontWithFrontFitness.add(front);
		frontWithFrontFitness.add(frontFitness);
		frontWithFrontFitness.add(nonFront);
		frontWithFrontFitness.add(nonFrontFitness);

		return frontWithFrontFitness;
	}

	public void yank(int val, ArrayList front, ArrayList frontFitness)
	{
		int size = front.size();
		front.set(val, front.get(size - 1));
		front.remove(size - 1);
		frontFitness.set(val, frontFitness.get(size - 1));
		frontFitness.remove(size - 1);
	}

	public boolean paretoDominates(MultiObjectiveFitness current, MultiObjectiveFitness other)
	{
		boolean abeatsb = false;

		if (current.objectives.length != other.objectives.length)
			throw new RuntimeException("Attempt made to compare two multiobjective fitnesses; but they have different numbers of objectives.");

		for (int x = 0; x < current.objectives.length; x++)
		{
			if (current.objectives[x] < other.objectives[x]) //objective[0] and objective[1]: [2514.5953504074932, 498.03951619189513]
				abeatsb = true;
			else if (current.objectives[x] > other.objectives[x])
				return false;
		}

		return abeatsb;
	}

}
