package mengxu.ruleanalysis.ParetoSetLearning;

import ec.EvolutionState;
import ec.Fitness;
import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.ensemble.EnsembleRule;
import mengxu.algorithm.multiobjective.ParetoSetLearning.GPRuleEvolutionStatePSL;
import mengxu.algorithm.multiobjective.ParetoSetLearning.PSLInitializer;
import mengxu.ruleanalysis.NSGAII.NSGAIIMultipleTreeResultFileReader;
import mengxu.ruleanalysis.NSGAII.NSGAIIMultipleTreeTestResult;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ParetoSetLearningMultiTreeRuleTestMeng {

	 public static final long simSeed = 968356;

		protected String trainPath; //the directory of training things
	    protected RuleTypeV2 ruleType;
	    protected int numRuns;
	    protected String testScenario;
	    protected String testSetName;
	    protected List<Objective> objectives; // The objectives to test.
	    protected int numTrees;

		protected int numPreference;
		protected double[][] preferences;

	    public ParetoSetLearningMultiTreeRuleTestMeng(String trainPath, RuleTypeV2 ruleType, int numRuns,
                                                      String testScenario, String testSetName,
                                                      List<Objective> objectives, int numTrees,
                                                      int numPreference) {
	        this.trainPath = trainPath;
	        this.ruleType = ruleType;
	        this.numRuns = numRuns;
	        this.testScenario = testScenario;
	        this.testSetName = testSetName;
	        this.objectives = objectives;
	        this.numTrees = numTrees;
			this.numPreference = numPreference;
	    }

	   //fzhang 17.11.2018 test for multi-objectives
	    public ParetoSetLearningMultiTreeRuleTestMeng(String trainPath, RuleTypeV2 ruleType, int numRuns,
                                                      String testScenario, String testSetName, int numTreess,
                                                      int numPreference) {
	        this(trainPath, ruleType, numRuns, testScenario, testSetName, new ArrayList<>(), numTreess, numPreference);
	    }

		public void generatePreferences(int numPreference){
			//Strategy 1: w1 + w2 must = 1
			int numObjectives = this.objectives.size();
			this.preferences = new double[numPreference][numObjectives];
			for (int i = 0; i < numPreference; i++) {
				if (numObjectives == 2) {
					double[] weightVector = new double[2];
					weightVector[0] = i / (double) (numPreference-1);
					weightVector[1] = (numPreference -1 - i) / (double) (numPreference-1);
					this.preferences[i] = weightVector;
				} else {
					throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
				}
			}

			//Strategy 2: w1 + w2 must = 1 by mengxu 2024.5.28
//			int numObjectives = this.objectives.size();
//			this.preferences = new double[numPreference][numObjectives];
//			int num = numPreference/2;
//			for (int i = 0; i < num; i++) {
//				if (numObjectives == 2) {
//					double[] weightVector = new double[2];
//					weightVector[0] = i / (double) (num-1);
//					weightVector[1] = (num -1 - i) / (double) (num-1);
//					this.preferences[i] = weightVector;
//				} else {
//					throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
//				}
//			}
//			Random rand = new Random(0);
//			for (int i = num; i < numPreference; i++) {
//				if (numObjectives == 2) {
//					double[] weightVector = new double[2];
//					weightVector[0] = rand.nextDouble();
//					weightVector[1] = rand.nextDouble();
//					this.preferences[i] = weightVector;
//				} else {
//					throw new RuntimeException("Unsupported number of objectives. Should be 2 or 3.");
//				}
//			}
		}

	    public String getTrainPath() {
	        return trainPath;
	    }

	    public RuleTypeV2 getRuleType() {
	        return ruleType;
	    }

	    public int getNumRuns() {
	        return numRuns;
	    }

	    public int getnumTrees() { return numTrees; }

	    public String getTestScenario() {
	        return testScenario;
	    }

	    public List<Objective> getObjectives() {
	        return objectives;
	    }

	    public void setObjectives(List<Objective> objectives) {
			this.objectives = objectives;
		}

		public void addObjective(Objective objective) {
			this.objectives.add(objective);
		}

		public void addObjective(String objective) {
			addObjective(Objective.get(objective));
		}

		//generate testset using simseed, replications
		public SchedulingSet generateTestSet() {
	        return SchedulingSet.generateSet(simSeed, testScenario,
	                testSetName, objectives, 30);
	    }

		public void writeToCSV(int topN) {
	        SchedulingSet testSet = generateTestSet();

	        File targetPath = new File(trainPath + "test"); //create a folder named "test" in trainPath
	        if (!targetPath.exists()) {
	            targetPath.mkdirs();
	        }

	        //modified by mengxu 2021.08.05
			if(topN>1){
				writeParetoFrontTopNToCSV(targetPath, testSet, topN);
			}
			else{
				writeParetoFrontToCSV(targetPath, testSet);
			}

//	        writeGenerationToCSV(targetPath, testSet);

	    }

	public void writeEachRunToCSV(int run, int topN, boolean ensemble) {
		this.generatePreferences(this.numPreference);
		SchedulingSet testSet = generateTestSet();

		File targetPath = new File(trainPath + "test"); //create a folder named "test" in trainPath
		if (!targetPath.exists()) {
			targetPath.mkdirs();
		}

		//modified by mengxu 2021.08.05
		if(topN>1 && ensemble){
			writeEachRunParetoFrontTopNEnsembleToCSV(targetPath, testSet, run, topN);
		}
		else if(topN>1){
			writeEachRunParetoFrontTopNToCSV(targetPath, testSet, run, topN);
		}
		else{
			writeEachRunParetoFrontToCSV(targetPath, testSet, run);
		}


//		//modified by mengxu 2021.08.05
//		writeParetoFrontToCSV(targetPath, testSet);
//	        writeGenerationToCSV(targetPath, testSet);

	}

	    public void writeParetoFrontToCSV(File targetPath, SchedulingSet testSet){
			File csvFile = new File(targetPath + "/" + testSetName + "-ParetoFront.csv"); //create a .csv to save the test result

			List<ParetoSetLearningParetoFrontTestResult> testParetoFrontResults = new ArrayList<>();

			//for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns
			for (int i = 0; i < numRuns; i++) {
				System.out.println("Run "+ i);
				//for (int i = 0; i < 1; i++) {
				File sourceFile = new File(trainPath + "job." + i + ".out.stat");  //this file keeps the rule
				//modified by mengxu 2022.08.11
				ParetoSetLearningParetoFrontTestResult result = ParetoSetLearningMultipleTreeTestResult.readParetoFrontTestResultFromFile(sourceFile, ruleType, numTrees, this.preferences.length);

				//modified by mengxu 2021.07.29
//				TestResult result = NSGAIIMultipleTreeTestResult.readParetoFrontFromFile(sourceFile, ruleType, numTrees);

				long start = System.currentTimeMillis();


				for (int j = 0; j < result.getGenerationalParetoFrontRules().size(); j++) {
					System.out.println("Pareto front: "+ (j*5-1) + "=========================");

					List<GPRule[]> generationalParetoFrontRules = result.getGenerationalParetoFrontRules(j);
//					AbstractRule[] generationalRules = result.getGenerationalRules(j);

					for(int p = 0; p < generationalParetoFrontRules.size(); p++){
						GPRule[] generationalRules = generationalParetoFrontRules.get(p);
						if (numTrees == 2) {
							generationalRules[0].calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
									result.getGenerationalTestParetoFrontFitness(j).get(p), null,
									testSet, generationalRules[1], objectives);
							//MultiObjecitve and KozaFitness extend from fitness(only return the largest fitness)
							//when we want to output more than one objective, first should be casted to multiobjective
//	                    System.out.println(((MultiObjectiveFitness)result.getGenerationalTestFitness(j)).objectives[0]);
//	                    System.out.println(((MultiObjectiveFitness)result.getGenerationalTestFitness(j)).objectives[1]);
						}
					}


				}

				long finish = System.currentTimeMillis();
				long duration = (finish - start)/1000;
				System.out.println("Duration = " + duration + " s.");

				testParetoFrontResults.add(result);
			}

			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
				if(objectives.size() == 1) {
					writer.write("Run,ParetoFromGeneration,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
							"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness");
					writer.newLine();
				}
				else {
					writer.write("Run,ParetoFromGeneration,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
							"RoutRuleUniqueTerminals,Obj,");
					for (int k = 0; k < objectives.size(); k++) {
						if(k == objectives.size()-1){
							writer.write("TrainFitness"+ k+ "," +
									"TestFitness"+ k);
						}else{
							writer.write("TrainFitness"+ k+ "," +
									"TestFitness"+ k + ",");
						}
					}
					writer.newLine();
				}

				//for (int i = 0; i < 1; i++) {
				for (int i = 0; i < numRuns; i++) {
					ParetoSetLearningParetoFrontTestResult result = testParetoFrontResults.get(i);

					//for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
					for (int j = 0; j < result.getGenerationalParetoFrontRules().size(); j++) { //use rules in each generation for testing
						for(int p=0; p<result.getGenerationalParetoFrontRules().get(j).size(); p++){
							MultiObjectiveFitness trainFit =
									(MultiObjectiveFitness)result.getGenerationalTrainParetoFrontFitness(j).get(p);
							MultiObjectiveFitness testFit =
									(MultiObjectiveFitness)result.getGenerationalTestParetoFrontFitness(j).get(p);
							GPRule[] rules = (GPRule[]) result.getGenerationalParetoFrontRules(j).get(p);
							GPRule seqRule = null;
							GPRule routRule = null;
							if (numTrees == 2) {
								if (rules[0].getType() == yimei.jss.rule.RuleType.SEQUENCING) {
									seqRule = rules[0];
									routRule = rules[1];
								} else {
									seqRule = rules[1];
									routRule = rules[0];
								}
							} else {
								seqRule = rules[0];
							}


							UniqueTerminalsGatherer gatherer = new UniqueTerminalsGatherer();
							int numUniqueTerminalsSeq = seqRule.getGPTree().child.numNodes(gatherer);
							int seqRuleSize = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);

							int numUniqueTerminalsRout = 0;
							int routRuleSize = 0;
							if (numTrees == 2) {
								gatherer = new UniqueTerminalsGatherer();
								numUniqueTerminalsRout = routRule.getGPTree().child.numNodes(gatherer);
								routRuleSize = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
							}

							if(objectives.size() == 1) {
								if(j == result.getGenerationalParetoFrontRules().size()-1){
									writer.write(i + "," + (j*5-1) + "," +
											seqRuleSize + "," +
											numUniqueTerminalsSeq + "," +
											routRuleSize +"," +
											numUniqueTerminalsRout +",0," +
											trainFit.fitness() + "," +
											testFit.fitness());
								}
								else{
									writer.write(i + "," + j*5 + "," +
											seqRuleSize + "," +
											numUniqueTerminalsSeq + "," +
											routRuleSize +"," +
											numUniqueTerminalsRout +",0," +
											trainFit.fitness() + "," +
											testFit.fitness());
								}
								writer.newLine();
							}
							else {
								if(j == result.getGenerationalParetoFrontRules().size()-1){
									writer.write(i + "," + (j*5-1) + "," +
											seqRuleSize + "," +
											numUniqueTerminalsSeq + "," +
											routRuleSize +"," +
											numUniqueTerminalsRout + "," +
											objectives.size() + ",");
								}
								else{
									writer.write(i + "," + j*5 + "," +
											seqRuleSize + "," +
											numUniqueTerminalsSeq + "," +
											routRuleSize +"," +
											numUniqueTerminalsRout + "," +
											objectives.size() + ",");
								}

								for (int k = 0; k < objectives.size(); k++) {
									if(k == objectives.size()-1){
										writer.write(trainFit.getObjective(k) + "," +
												testFit.getObjective(k));
									}else{
										writer.write(trainFit.getObjective(k) + "," +
												testFit.getObjective(k) + ",");
									}
								}
								writer.newLine();
							}
						}
					}
				}
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	public void writeParetoFrontTopNToCSV(File targetPath, SchedulingSet testSet, int topN){
		File csvFile = new File(targetPath + "/" + testSetName + "-ParetoFront.csv"); //create a .csv to save the test result

		List<ParetoSetLearningParetoFrontTestResult> testParetoFrontResults = new ArrayList<>();

		//for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns
		for (int i = 0; i < numRuns; i++) {
			System.out.println("Run "+ i);
			//for (int i = 0; i < 1; i++) {
			File sourceFile = new File(trainPath + "job." + i + ".out.stat");  //this file keeps the rule
			//modified by mengxu 2022.08.11
			ParetoSetLearningParetoFrontTestResult result = ParetoSetLearningMultipleTreeTestResult.readParetoFrontTestResultTopNFromFile(sourceFile, ruleType, numTrees, this.preferences.length, topN);

			//modified by mengxu 2021.07.29
//				TestResult result = NSGAIIMultipleTreeTestResult.readParetoFrontFromFile(sourceFile, ruleType, numTrees);

			long start = System.currentTimeMillis();


			for (int j = 0; j < result.getGenerationalParetoFrontRules().size(); j++) {
				System.out.println("Pareto front: "+ (j*5) + "=========================");

				List<GPRule[]> generationalParetoFrontRules = result.getGenerationalParetoFrontRules(j);
//					AbstractRule[] generationalRules = result.getGenerationalRules(j);

				for(int p = 0; p < generationalParetoFrontRules.size(); p++){
					GPRule[] generationalRules = generationalParetoFrontRules.get(p);
					if (numTrees == 2) {
						generationalRules[0].calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
								result.getGenerationalTestParetoFrontFitness(j).get(p), null,
								testSet, generationalRules[1], objectives);
					}
				}


			}

			long finish = System.currentTimeMillis();
			long duration = (finish - start)/1000;
			System.out.println("Duration = " + duration + " s.");

			testParetoFrontResults.add(result);
		}

		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
			if(objectives.size() == 1) {
				writer.write("Run,ParetoFromGeneration,ind,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness");
				writer.newLine();
			}
			else {
				writer.write("Run,ParetoFromGeneration,ind,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,");
				for (int k = 0; k < objectives.size(); k++) {
					if(k == objectives.size()-1){
						writer.write("TrainFitness"+ k+ "," +
								"TestFitness"+ k);
					}else{
						writer.write("TrainFitness"+ k+ "," +
								"TestFitness"+ k + ",");
					}
				}
				writer.newLine();
			}

			//for (int i = 0; i < 1; i++) {
			for (int i = 0; i < numRuns; i++) {
				ParetoSetLearningParetoFrontTestResult result = testParetoFrontResults.get(i);

				//for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
				for (int j = 0; j < result.getGenerationalParetoFrontRules().size(); j++) { //use rules in each generation for testing
					int gen = (j/(topN*numPreference))*5;
					int index = (j/numPreference)%topN;
					for(int p=0; p<result.getGenerationalParetoFrontRules().get(j).size(); p++){
						MultiObjectiveFitness trainFit =
								(MultiObjectiveFitness)result.getGenerationalTrainParetoFrontFitness(j).get(p);
						MultiObjectiveFitness testFit =
								(MultiObjectiveFitness)result.getGenerationalTestParetoFrontFitness(j).get(p);
						GPRule[] rules = (GPRule[]) result.getGenerationalParetoFrontRules(j).get(p);
						GPRule seqRule = null;
						GPRule routRule = null;
						if (numTrees == 2) {
							if (rules[0].getType() == yimei.jss.rule.RuleType.SEQUENCING) {
								seqRule = rules[0];
								routRule = rules[1];
							} else {
								seqRule = rules[1];
								routRule = rules[0];
							}
						} else {
							seqRule = rules[0];
						}

						UniqueTerminalsGatherer gatherer = new UniqueTerminalsGatherer();
						int numUniqueTerminalsSeq = seqRule.getGPTree().child.numNodes(gatherer);
						int seqRuleSize = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);

						int numUniqueTerminalsRout = 0;
						int routRuleSize = 0;
						if (numTrees == 2) {
							gatherer = new UniqueTerminalsGatherer();
							numUniqueTerminalsRout = routRule.getGPTree().child.numNodes(gatherer);
							routRuleSize = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
						}

						if(objectives.size() == 1) {
							if(j == result.getGenerationalParetoFrontRules().size()-1){
								writer.write(i + "," + (gen-1) + "," +
										index + "," +
										seqRuleSize + "," +
										numUniqueTerminalsSeq + "," +
										routRuleSize +"," +
										numUniqueTerminalsRout +",0," +
										trainFit.fitness() + "," +
										testFit.fitness());
							}
							else{
								writer.write(i + "," + gen + "," +
										index + "," +
										seqRuleSize + "," +
										numUniqueTerminalsSeq + "," +
										routRuleSize +"," +
										numUniqueTerminalsRout +",0," +
										trainFit.fitness() + "," +
										testFit.fitness());
							}
							writer.newLine();
						}
						else {
							if(j == result.getGenerationalParetoFrontRules().size()-1){
								writer.write(i + "," + (gen-1) + "," +
										index + "," +
										seqRuleSize + "," +
										numUniqueTerminalsSeq + "," +
										routRuleSize +"," +
										numUniqueTerminalsRout + "," +
										objectives.size() + ",");
							}
							else{
								writer.write(i + "," + gen + "," +
										index + "," +
										seqRuleSize + "," +
										numUniqueTerminalsSeq + "," +
										routRuleSize +"," +
										numUniqueTerminalsRout + "," +
										objectives.size() + ",");
							}

							for (int k = 0; k < objectives.size(); k++) {
								if(k == objectives.size()-1){
									writer.write(trainFit.getObjective(k) + "," +
											testFit.getObjective(k));
								}else{
									writer.write(trainFit.getObjective(k) + "," +
											testFit.getObjective(k) + ",");
								}
							}
							writer.newLine();
						}
					}
				}
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void writeEachRunParetoFrontToCSV(File targetPath, SchedulingSet testSet, int run){
		File csvFile = new File(targetPath + "/" + testSetName + "-" + run + "-ParetoFront.csv"); //create a .csv to save the test result

		List<ParetoSetLearningParetoFrontTestResult> testParetoFrontResults = new ArrayList<>();

		//for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns

		System.out.println("Run "+ run);
		//for (int i = 0; i < 1; i++) {
		File sourceFile = new File(trainPath + "job." + run + ".out.stat");  //this file keeps the rule
		//modified by mengxu 2022.08.11
		ParetoSetLearningParetoFrontTestResult result = ParetoSetLearningMultipleTreeTestResult.readParetoFrontTestResultFromFile(sourceFile, ruleType, numTrees, this.preferences.length);

		//modified by mengxu 2021.07.29
//				TestResult result = NSGAIIMultipleTreeTestResult.readParetoFrontFromFile(sourceFile, ruleType, numTrees);

		long start = System.currentTimeMillis();


		for (int j = 0; j < result.getGenerationalParetoFrontRules().size(); j++) {
			System.out.println("Pareto front: "+ j + "=========================");

			List<GPRule[]> generationalParetoFrontRules = result.getGenerationalParetoFrontRules(j);

			for(int p = 0; p < generationalParetoFrontRules.size(); p++){
				GPRule[] generationalRules = generationalParetoFrontRules.get(p);
				EvolutionState state = new GPRuleEvolutionStatePSL();
				((GPRuleEvolutionStatePSL)state).evaluatePreferenceIndex = p;
				state.initializer = new PSLInitializer();
				((GPRuleEvolutionStatePSL)state).terminalNormalisation = 2; //todo: need to change when test
				((PSLInitializer)state.initializer).weights = this.preferences;
				if (numTrees == 2) {
					generationalRules[0].calcFitnessWithPreference(  //in calcFitness(), it will check which one is routing/sequencing rule
							result.getGenerationalTestParetoFrontFitness(j).get(p), state,
							testSet, generationalRules[1], objectives, p);
				}
//				if (numTrees == 2) {
//					generationalRules[0].calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
//							result.getGenerationalTestParetoFrontFitness(j).get(p), null,
//							testSet, generationalRules[1], objectives);
//				}
			}


		}

		long finish = System.currentTimeMillis();
		long duration = (finish - start)/1000;
		System.out.println("Duration = " + duration + " s.");

		testParetoFrontResults.add(result);

		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
			if(objectives.size() == 1) {
				writer.write("Run,ParetoFromGeneration,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness");
				writer.newLine();
			}
			else {
				writer.write("Run,ParetoFromGeneration,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,");
				for (int k = 0; k < objectives.size(); k++) {
					if(k == objectives.size()-1){
						writer.write("TrainFitness"+ k+ "," +
								"TestFitness"+ k);
					}else{
						writer.write("TrainFitness"+ k+ "," +
								"TestFitness"+ k + ",");
					}
				}
				writer.newLine();
			}

			ParetoSetLearningParetoFrontTestResult eachResult = testParetoFrontResults.get(0);

			//for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
			for (int j = 0; j < eachResult.getGenerationalParetoFrontRules().size(); j++) { //use rules in each generation for testing
				for(int p=0; p<eachResult.getGenerationalParetoFrontRules().get(j).size(); p++){
					MultiObjectiveFitness trainFit =
							(MultiObjectiveFitness)eachResult.getGenerationalTrainParetoFrontFitness(j).get(p);
					MultiObjectiveFitness testFit =
							(MultiObjectiveFitness)eachResult.getGenerationalTestParetoFrontFitness(j).get(p);
					GPRule[] rules = (GPRule[]) eachResult.getGenerationalParetoFrontRules(j).get(p);
					GPRule seqRule = null;
					GPRule routRule = null;
					if (numTrees == 2) {
						if (rules[0].getType() == yimei.jss.rule.RuleType.SEQUENCING) {
							seqRule = rules[0];
							routRule = rules[1];
						} else {
							seqRule = rules[1];
							routRule = rules[0];
						}
					} else {
						seqRule = rules[0];
					}


					UniqueTerminalsGatherer gatherer = new UniqueTerminalsGatherer();
					int numUniqueTerminalsSeq = seqRule.getGPTree().child.numNodes(gatherer);
					int seqRuleSize = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);

					int numUniqueTerminalsRout = 0;
					int routRuleSize = 0;
					if (numTrees == 2) {
						gatherer = new UniqueTerminalsGatherer();
						numUniqueTerminalsRout = routRule.getGPTree().child.numNodes(gatherer);
						routRuleSize = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
					}

					if(objectives.size() == 1) {
						if(j == result.getGenerationalParetoFrontRules().size()-1){
							writer.write(run + "," + (j*5-1) + "," +
									seqRuleSize + "," +
									numUniqueTerminalsSeq + "," +
									routRuleSize +"," +
									numUniqueTerminalsRout +",0," +
									trainFit.fitness() + "," +
									testFit.fitness());
						}
						else{
							writer.write(run + "," + j*5 + "," +
									seqRuleSize + "," +
									numUniqueTerminalsSeq + "," +
									routRuleSize +"," +
									numUniqueTerminalsRout +",0," +
									trainFit.fitness() + "," +
									testFit.fitness());
						}
						writer.newLine();
					}
					else {
						if(j == result.getGenerationalParetoFrontRules().size()-1){
							writer.write(run + "," + (j*5-1) + "," +
									seqRuleSize + "," +
									numUniqueTerminalsSeq + "," +
									routRuleSize +"," +
									numUniqueTerminalsRout + "," +
									objectives.size() + ",");
						}
						else{
							writer.write(run + "," + j*5 + "," +
									seqRuleSize + "," +
									numUniqueTerminalsSeq + "," +
									routRuleSize +"," +
									numUniqueTerminalsRout + "," +
									objectives.size() + ",");
						}

						for (int k = 0; k < objectives.size(); k++) {
							if(k == objectives.size()-1){
								writer.write(trainFit.getObjective(k) + "," +
										testFit.getObjective(k));
							}else{
								writer.write(trainFit.getObjective(k) + "," +
										testFit.getObjective(k) + ",");
							}
						}
						writer.newLine();
					}
				}
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void writeEachRunParetoFrontTopNToCSV(File targetPath, SchedulingSet testSet, int run, int topN){
		File csvFile = new File(targetPath + "/" + testSetName + "-" + run + "-ParetoFront.csv"); //create a .csv to save the test result

		List<ParetoSetLearningParetoFrontTestResult> testParetoFrontResults = new ArrayList<>();

		//for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns

		System.out.println("Run "+ run);
		//for (int i = 0; i < 1; i++) {
		File sourceFile = new File(trainPath + "job." + run + ".out.stat");  //this file keeps the rule
		//modified by mengxu 2022.08.11
		ParetoSetLearningParetoFrontTestResult result = ParetoSetLearningMultipleTreeTestResult.readParetoFrontTestResultTopNFromFile(sourceFile, ruleType, numTrees, this.preferences.length, topN);

		//modified by mengxu 2021.07.29
//				TestResult result = NSGAIIMultipleTreeTestResult.readParetoFrontFromFile(sourceFile, ruleType, numTrees);

		long start = System.currentTimeMillis();


		for (int j = 0; j < result.getGenerationalParetoFrontRules().size(); j++) {
			System.out.println("Pareto front: "+ j*10 + "=========================");

			List<GPRule[]> generationalParetoFrontRules = result.getGenerationalParetoFrontRules(j);

			for(int p = 0; p < generationalParetoFrontRules.size(); p++){
				int indexPreference = p%numPreference;
				GPRule[] generationalRules = generationalParetoFrontRules.get(p);
				EvolutionState state = new GPRuleEvolutionStatePSL();
				((GPRuleEvolutionStatePSL)state).evaluatePreferenceIndex = indexPreference;
				state.initializer = new PSLInitializer();
				((GPRuleEvolutionStatePSL)state).terminalNormalisation = 2; //todo: need to change when test
				((PSLInitializer)state.initializer).weights = this.preferences;
				if (numTrees == 2) {
					generationalRules[0].calcFitnessWithPreference(  //in calcFitness(), it will check which one is routing/sequencing rule
							result.getGenerationalTestParetoFrontFitness(j).get(p), state,
							testSet, generationalRules[1], objectives, indexPreference);
				}
//				if (numTrees == 2) {
//					generationalRules[0].calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
//							result.getGenerationalTestParetoFrontFitness(j).get(p), null,
//							testSet, generationalRules[1], objectives);
//				}
			}


		}

		long finish = System.currentTimeMillis();
		long duration = (finish - start)/1000;
		System.out.println("Duration = " + duration + " s.");

		testParetoFrontResults.add(result);

		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
			if(objectives.size() == 1) {
				writer.write("Run,ParetoFromGeneration,ind,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness");
				writer.newLine();
			}
			else {
				writer.write("Run,ParetoFromGeneration,ind,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,");
				for (int k = 0; k < objectives.size(); k++) {
					if(k == objectives.size()-1){
						writer.write("TrainFitness"+ k+ "," +
								"TestFitness"+ k);
					}else{
						writer.write("TrainFitness"+ k+ "," +
								"TestFitness"+ k + ",");
					}
				}
				writer.newLine();
			}

			ParetoSetLearningParetoFrontTestResult eachResult = testParetoFrontResults.get(0);

			//for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
			for (int j = 0; j < eachResult.getGenerationalParetoFrontRules().size(); j++) { //use rules in each generation for testing
				int gen = j*5;
				for(int p=0; p<eachResult.getGenerationalParetoFrontRules().get(j).size(); p++){
					int index = p/numPreference;
					MultiObjectiveFitness trainFit =
							(MultiObjectiveFitness)eachResult.getGenerationalTrainParetoFrontFitness(j).get(p);
					MultiObjectiveFitness testFit =
							(MultiObjectiveFitness)eachResult.getGenerationalTestParetoFrontFitness(j).get(p);
					GPRule[] rules = (GPRule[]) eachResult.getGenerationalParetoFrontRules(j).get(p);
					GPRule seqRule = null;
					GPRule routRule = null;
					if (numTrees == 2) {
						if (rules[0].getType() == yimei.jss.rule.RuleType.SEQUENCING) {
							seqRule = rules[0];
							routRule = rules[1];
						} else {
							seqRule = rules[1];
							routRule = rules[0];
						}
					} else {
						seqRule = rules[0];
					}


					UniqueTerminalsGatherer gatherer = new UniqueTerminalsGatherer();
					int numUniqueTerminalsSeq = seqRule.getGPTree().child.numNodes(gatherer);
					int seqRuleSize = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);

					int numUniqueTerminalsRout = 0;
					int routRuleSize = 0;
					if (numTrees == 2) {
						gatherer = new UniqueTerminalsGatherer();
						numUniqueTerminalsRout = routRule.getGPTree().child.numNodes(gatherer);
						routRuleSize = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
					}

					if(objectives.size() == 1) {
						if(j == result.getGenerationalParetoFrontRules().size()-1){
							writer.write(run + "," + (gen-1) + "," +
									index + "," +
									seqRuleSize + "," +
									numUniqueTerminalsSeq + "," +
									routRuleSize +"," +
									numUniqueTerminalsRout +",0," +
									trainFit.fitness() + "," +
									testFit.fitness());
						}
						else{
							writer.write(run + "," + gen + "," +
									index + "," +
									seqRuleSize + "," +
									numUniqueTerminalsSeq + "," +
									routRuleSize +"," +
									numUniqueTerminalsRout +",0," +
									trainFit.fitness() + "," +
									testFit.fitness());
						}
						writer.newLine();
					}
					else {
						if(j == result.getGenerationalParetoFrontRules().size()-1){
							writer.write(run + "," + (gen-1) + "," +
									index + "," +
									seqRuleSize + "," +
									numUniqueTerminalsSeq + "," +
									routRuleSize +"," +
									numUniqueTerminalsRout + "," +
									objectives.size() + ",");
						}
						else{
							writer.write(run + "," + gen + "," +
									index + "," +
									seqRuleSize + "," +
									numUniqueTerminalsSeq + "," +
									routRuleSize +"," +
									numUniqueTerminalsRout + "," +
									objectives.size() + ",");
						}

						for (int k = 0; k < objectives.size(); k++) {
							if(k == objectives.size()-1){
								writer.write(trainFit.getObjective(k) + "," +
										testFit.getObjective(k));
							}else{
								writer.write(trainFit.getObjective(k) + "," +
										testFit.getObjective(k) + ",");
							}
						}
						writer.newLine();
					}
				}
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void writeEachRunParetoFrontTopNEnsembleToCSV(File targetPath, SchedulingSet testSet, int run, int topN){
		File csvFile = new File(targetPath + "/" + testSetName + "-" + run + "-ParetoFront.csv"); //create a .csv to save the test result

		List<ParetoSetLearningParetoFrontTestResult> testParetoFrontResults = new ArrayList<>();

		//for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns

		System.out.println("Run "+ run);
		//for (int i = 0; i < 1; i++) {
		File sourceFile = new File(trainPath + "job." + run + ".out.stat");  //this file keeps the rule
		//modified by mengxu 2022.08.11
//		ParetoSetLearningParetoFrontTestResult result = ParetoSetLearningMultipleTreeTestResult.readParetoFrontTestResultTopNFromFile(sourceFile, ruleType, numTrees, this.preferences.length, topN);
		ParetoSetLearningParetoFrontTestResult ensembleResult = ParetoSetLearningMultipleTreeTestResult.readParetoFrontTestResultTopNEnsembleFromFile(sourceFile, ruleType, numTrees, this.preferences.length, topN);
		//modified by mengxu 2021.07.29
//				TestResult result = NSGAIIMultipleTreeTestResult.readParetoFrontFromFile(sourceFile, ruleType, numTrees);

		long start = System.currentTimeMillis();


		for (int j = 0; j < ensembleResult.getGenerationalEnsembleRules().size(); j++) {
			System.out.println("Pareto front: "+ j*10 + "=========================");

			List<EnsembleRule> generationalEnsembleRules = ensembleResult.getGenerationalEnsembleRules().get(j);
//			List<GPRule[]> generationalParetoFrontRules = result.getGenerationalParetoFrontRules(j);

			for(int p = 0; p < generationalEnsembleRules.size(); p++){
				int indexPreference = p%numPreference;
				EnsembleRule ensembleRule = generationalEnsembleRules.get(p);
				EvolutionState state = new GPRuleEvolutionStatePSL();
				((GPRuleEvolutionStatePSL)state).evaluatePreferenceIndex = indexPreference;
				state.initializer = new PSLInitializer();
				((GPRuleEvolutionStatePSL)state).terminalNormalisation = 2; //todo: need to change when test
				((PSLInitializer)state.initializer).weights = this.preferences;
				//todo: revise from here!!! 2024.6.24
				testSet.getSimulations().get(0).useMultiCaseEnsemble = false;
				testSet.getSimulations().get(0).useMultiSubpopMultiCaseEnsemble = false;
				testSet.getSimulations().get(0).useVotingEnsemble = true;
				testSet.getSimulations().get(0).useLS = false;
				ensembleRule.calcFitnessByVotingEnsembleWithPreference(  //this is to get the final fitness by the voting of ensemble
						ensembleResult.getGenerationalEnsembleTestFitnesses().get(j).get(p), state,
						testSet, objectives, indexPreference);
//				ensembleRule.calcFitnessByVotingEnsemble(  //this is to get the final fitness by the voting of ensemble
//						ensembleResult.getGenerationalEnsembleTestFitnesses().get(j).get(p), state,
//						testSet, objectives);
				double[] testEnsembleContribution = ensembleRule.getEnsembleContribution();
				for(int c=0; c< testEnsembleContribution.length; c++){
					ensembleResult.getGenerationalTestEnsembleContribution().get(j).get(p).add(testEnsembleContribution[c]);
				}
			}


		}

		long finish = System.currentTimeMillis();
		long duration = (finish - start)/1000;
		System.out.println("Duration = " + duration + " s.");

		testParetoFrontResults.add(ensembleResult);

		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
			if(objectives.size() == 1) {
				writer.write("Run,ParetoFromGeneration,ind,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness");
				writer.newLine();
			}
			else {
				writer.write("Run,ParetoFromGeneration,ind,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,TestEnsembleContribution,");
				for (int k = 0; k < objectives.size(); k++) {
					if(k == objectives.size()-1){
						writer.write("TrainFitness"+ k+ "," +
								"TestFitness"+ k);
					}else{
						writer.write("TrainFitness"+ k+ "," +
								"TestFitness"+ k + ",");
					}
				}
				writer.newLine();
			}

			ParetoSetLearningParetoFrontTestResult eachResult = testParetoFrontResults.get(0);

			//for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
			for (int j = 0; j < eachResult.getGenerationalEnsembleRules().size(); j++) { //use rules in each generation for testing
				int gen = j*5;
				for(int p=0; p<eachResult.getGenerationalEnsembleRules().get(j).size(); p++){
					int index = p/numPreference;
					List<Fitness> trainFit = eachResult.getGenerationalEnsembleTrainFitnesses().get(j).get(p);
					MultiObjectiveFitness testFit =
							(MultiObjectiveFitness)eachResult.getGenerationalEnsembleTestFitnesses().get(j).get(p);
					List<Double> testEnsembleContribution = eachResult.getGenerationalTestEnsembleContribution().get(j).get(p);
//					MultiObjectiveFitness trainFit =
//							(MultiObjectiveFitness)eachResult.getGenerationalTrainParetoFrontFitness(j).get(p);
//					MultiObjectiveFitness testFit =
//							(MultiObjectiveFitness)eachResult.getGenerationalTestParetoFrontFitness(j).get(p);
//
					EnsembleRule ensembleRule = eachResult.getGenerationalEnsembleRules().get(j).get(p);
					double meanNumUniqueTerminalsSeq = 0;
					double meanSeqRuleSize = 0;
					double meanNumUniqueTerminalsRout = 0;
					double meanRoutRuleSize = 0;
					for(int c=0; c<ensembleRule.getEnsembleSequencingRule().size(); c++){
						GPRule seqRule = ensembleRule.getEnsembleSequencingRule().get(c);
						GPRule routRule = ensembleRule.getEnsembleRoutingRule().get(c);

						UniqueTerminalsGatherer gatherer = new UniqueTerminalsGatherer();
						int numUniqueTerminalsSeq = seqRule.getGPTree().child.numNodes(gatherer);
						int seqRuleSize = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);

						int numUniqueTerminalsRout = 0;
						int routRuleSize = 0;
						if (numTrees == 2) {
							gatherer = new UniqueTerminalsGatherer();
							numUniqueTerminalsRout = routRule.getGPTree().child.numNodes(gatherer);
							routRuleSize = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
						}

						meanNumUniqueTerminalsSeq = meanNumUniqueTerminalsSeq + numUniqueTerminalsSeq;
						meanSeqRuleSize = meanSeqRuleSize + seqRuleSize;
						meanNumUniqueTerminalsRout = meanNumUniqueTerminalsRout + numUniqueTerminalsRout;
						meanRoutRuleSize = meanRoutRuleSize + routRuleSize;
					}



					for(int c=0; c<testEnsembleContribution.size(); c++){
						if(objectives.size() == 1) {
							if(j == ensembleResult.getGenerationalEnsembleRules().size()-1){
								writer.write(run + "," + (gen-1) + "," +
										index + "," +
										meanSeqRuleSize + "," +
										meanNumUniqueTerminalsSeq + "," +
										meanRoutRuleSize +"," +
										meanNumUniqueTerminalsRout +",0," +
										testEnsembleContribution.get(c) + "," +
										trainFit.get(c).fitness() + "," +
										testFit.fitness());
							}
							else{
								writer.write(run + "," + gen + "," +
										index + "," +
										meanSeqRuleSize + "," +
										meanNumUniqueTerminalsSeq + "," +
										meanRoutRuleSize +"," +
										meanNumUniqueTerminalsRout +",0," +
										testEnsembleContribution.get(c) + "," +
										trainFit.get(c).fitness() + "," +
										testFit.fitness());
							}
							writer.newLine();
						}
						else {
							if(j == ensembleResult.getGenerationalEnsembleRules().size()-1){
								writer.write(run + "," + (gen-1) + "," +
										index + "," +
										meanSeqRuleSize + "," +
										meanNumUniqueTerminalsSeq + "," +
										meanRoutRuleSize +"," +
										meanNumUniqueTerminalsRout + "," +
										objectives.size() + "," +
										testEnsembleContribution.get(c) + ",");
							}
							else{
								writer.write(run + "," + gen + "," +
										index + "," +
										meanSeqRuleSize + "," +
										meanNumUniqueTerminalsSeq + "," +
										meanRoutRuleSize +"," +
										meanNumUniqueTerminalsRout + "," +
										objectives.size() + "," +
										testEnsembleContribution.get(c) + ",");
							}

							for (int k = 0; k < objectives.size(); k++) {
								if(k == objectives.size()-1){
									writer.write(((MultiObjectiveFitness)trainFit.get(c)).getObjective(k) + "," +
											testFit.getObjective(k));
								}else{
									writer.write(((MultiObjectiveFitness)trainFit.get(c)).getObjective(k) + "," +
											testFit.getObjective(k) + ",");
								}
							}
							writer.newLine();
						}
					}
				}
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void writeGenerationToCSV(File targetPath, SchedulingSet testSet){
		File csvFile = new File(targetPath + "/" + testSetName + "-Generation.csv"); //create a .csv to save the test result

		List<TestResult> testResults = new ArrayList<>();

		//for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns
		for (int i = 0; i < numRuns; i++) {
			System.out.println("Run "+ i);
			//for (int i = 0; i < 1; i++) {
			File sourceFile = new File(trainPath + "job." + i + ".out.stat");  //this file keeps the rule
			//modified by mengxu 2021.07.29
			TestResult result = NSGAIIMultipleTreeTestResult.readTrainResultFromFile(sourceFile, ruleType, numTrees);
			//original
//				TestResult result = NSGAIIMultipleTreeTestResult.readFromFile(sourceFile, ruleType, numTrees);

			//Didn't bother saving time files
			//for multi-objective, there are some error
			File timeFile = new File(trainPath + "job." + i + ".time.csv");
			result.setGenerationalTimeStat(NSGAIIMultipleTreeResultFileReader.readTimeFromFile(timeFile));

			File diversityFile = new File(trainPath + "job." + i + ".diversities.csv");
			result.setGenerationalGenotypeDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,1));
			result.setGenerationalPhenotypeDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,2));
			result.setGenerationalEntropyDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,3));
			result.setGenerationalPseudoIsomorphsDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,4));
			result.setGenerationalEditOneDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,5));
			result.setGenerationalEditTwoDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,6));
			result.setGenerationalPCDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,7));
			result.setGenerationalParentSelectionDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,8));


			//24.8.2018 fzhang read badrun in CSV
//	            File badrunsFile = new File(trainPath + "job." + i + ".BadRun.csv");
//	            result.setGenerationalBadRunStat(MultipleTreeResultFileReader.readBadRunFromFile(badrunsFile));

			long start = System.currentTimeMillis();

//	            result.validate(objectives);

			//for (int j = 42; j < result.getGenerationalRules().size(); j++) {
//	            System.out.println(result.getGenerationalRules().size());  // 1
			for (int j = 0; j < result.getGenerationalRules().size(); j++) {
				AbstractRule[] generationalRules = result.getGenerationalRules(j);
				if (numTrees == 2) {
					generationalRules[0].calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
							result.getGenerationalTestFitness(j), null,
							testSet, generationalRules[1], objectives);
					//MultiObjecitve and KozaFitness extend from fitness(only return the largest fitness)
					//when we want to output more than one objective, first should be casted to multiobjective
//	                    System.out.println(((MultiObjectiveFitness)result.getGenerationalTestFitness(j)).objectives[0]);
//	                    System.out.println(((MultiObjectiveFitness)result.getGenerationalTestFitness(j)).objectives[1]);
				}
				//generationalRules[1] is routing rule

				//modified by mengxu 2021.07.29
//	                System.out.println("Rule " + j + ": objecitve 1 = " +
//	                		((MultiObjectiveFitness)result.getGenerationalTestFitness(j)).objectives[0] +
//	                        ", objecitve 2 = "+ ((MultiObjectiveFitness)result.getGenerationalTestFitness(j)).objectives[1]);
				//fitness(): return the max value
			}

			long finish = System.currentTimeMillis();
			long duration = (finish - start)/1000;
			System.out.println("Duration = " + duration + " s.");

			testResults.add(result);
		}

		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
			if(objectives.size() == 1) {
				writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness,TrainTime," +
						"GenotypeDiversity, PhenotypeDiversity, EntropyDiversity, PseudoIsomorphsDiversity," +
						"EditOneDiversity, EditTwoDiversity, PCDiversity, ParentSelectionDiversity");
				writer.newLine();
			}
			else {
				writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,");
				for (int k = 0; k < objectives.size(); k++) {
					writer.write("TrainFitness"+ k+ "," +
							"TestFitness"+ k + ",");
				}
				writer.write("TrainTime," +
						"GenotypeDiversity, PhenotypeDiversity, EntropyDiversity, PseudoIsomorphsDiversity," +
						"EditOneDiversity, EditTwoDiversity, PCDiversity, ParentSelectionDiversity");
//	        		writer.write("Run,Front,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
//	  	                    "RoutRuleUniqueTerminals,Obj,TrainFitness1,TestFitness1,TrainFitness2,TestFitness2");
				writer.newLine();
			}

			//for (int i = 0; i < 1; i++) {
			for (int i = 0; i < numRuns; i++) {
				TestResult result = testResults.get(i);

				//for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
				for (int j = 0; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing

					MultiObjectiveFitness trainFit =
							(MultiObjectiveFitness)result.getGenerationalTrainFitness(j);
					MultiObjectiveFitness testFit =
							(MultiObjectiveFitness)result.getGenerationalTestFitness(j);
					GPRule[] rules = (GPRule[]) result.getGenerationalRules(j);
					GPRule seqRule = null;
					GPRule routRule = null;
					if (numTrees == 2) {
						if (rules[0].getType() == yimei.jss.rule.RuleType.SEQUENCING) {
							seqRule = rules[0];
							routRule = rules[1];
						} else {
							seqRule = rules[1];
							routRule = rules[0];
						}
					} else {
						seqRule = rules[0];
					}


					UniqueTerminalsGatherer gatherer = new UniqueTerminalsGatherer();
					int numUniqueTerminalsSeq = seqRule.getGPTree().child.numNodes(gatherer);
					int seqRuleSize = seqRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);

					int numUniqueTerminalsRout = 0;
					int routRuleSize = 0;
					if (numTrees == 2) {
						gatherer = new UniqueTerminalsGatherer();
						numUniqueTerminalsRout = routRule.getGPTree().child.numNodes(gatherer);
						routRuleSize = routRule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL);
					}

					if(objectives.size() == 1) {
						writer.write(i + "," + j + "," +
								seqRuleSize + "," +
								numUniqueTerminalsSeq + "," +
								routRuleSize +"," +
								numUniqueTerminalsRout +",0," +
								trainFit.fitness() + "," +
								testFit.fitness()+ ","+
								result.getGenerationalTime(j)+ ","+
								result.getGenerationalGenotypeDiversityStat(j)+ ","+
								result.getGenerationalPhenotypeDiversityStat(j)+ ","+
								result.getGenerationalEntropyDiversityStat(j)+ ","+
								result.getGenerationalPseudoIsomorphsDiversityStat(j)+ ","+
								result.getGenerationalEditOneDiversityStat(j)+ ","+
								result.getGenerationalEditTwoDiversityStat(j)+ ","+
								result.getGenerationalPCDiversityStat(j)+ ","+
								result.getGenerationalParentSelectionDiversityStat(j));
//		            writer.write(i + "," + j + "," +
//                            seqRuleSize + "," +
//                            numUniqueTerminalsSeq + "," +
//                            routRuleSize +"," +
//                            numUniqueTerminalsRout +",1," +
//                            trainFit.fitness() + "," +
//                            testFit.fitness()+ ","+
//                            result.getGenerationalTime(j));
						writer.newLine();
					}
					else {
//	  	            writer.write(i + "," + j + "," +
//                  		seqRuleSize + "," +
//                  		numUniqueTerminalsSeq + "," +
//                        routRuleSize +"," +
//                        numUniqueTerminalsRout +",2");
//
//                    for (int k = 0; k < objectives.size(); k++) {
//                         writer.write( "," +
//                              trainFit.getObjective(k) + "," +
//                              testFit.getObjective(k));
						writer.write(i + "," + j + "," +
								seqRuleSize + "," +
								numUniqueTerminalsSeq + "," +
								routRuleSize +"," +
								numUniqueTerminalsRout + "," +
								objectives.size() + ",");

						for (int k = 0; k < objectives.size(); k++) {
							writer.write(trainFit.getObjective(k) + "," +
									testFit.getObjective(k) + ",");
						}
						writer.write(result.getGenerationalTime(j)+ ","+
								result.getGenerationalGenotypeDiversityStat(j)+ ","+
								result.getGenerationalPhenotypeDiversityStat(j)+ ","+
								result.getGenerationalEntropyDiversityStat(j)+ ","+
								result.getGenerationalPseudoIsomorphsDiversityStat(j)+ ","+
								result.getGenerationalEditOneDiversityStat(j)+ ","+
								result.getGenerationalEditTwoDiversityStat(j)+ ","+
								result.getGenerationalPCDiversityStat(j)+ ","+
								result.getGenerationalParentSelectionDiversityStat(j));
						writer.newLine();
					}
				}
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	    /**
	     * Call this main method with several parameters
	     *
	     * /Users/dyska/Desktop/Uni/COMP489/GPJSS/grid_results/dynamic/raw/coevolution-fixed/0.85-max-flowtime/
	     * simple-rule
	     * 30
		 * 1 //this represents the number of run, from 1 to 30 add by mengxu
	     * dynamic-job-shop
	     * missing-0.85-4.0
	     * 2
	     * 1
	     * max-flowtime
	     */
		public static void main(String[] args) {
			int idx = 0;
			String trainPath = args[idx];
	        idx ++;
	        RuleTypeV2 ruleType = RuleTypeV2.get(args[idx]);
			idx ++;
	        int numRuns = Integer.valueOf(args[idx]); //30
	        idx ++;
			int curRun = Integer.valueOf(args[idx]); //30
			idx ++;
	        String testScenario = args[idx]; //dynamic
	        idx ++;
	        String testSetName = args[idx]; //missing-0.85-4.0
	        idx ++;
	        int numTrees = Integer.valueOf(args[idx]); //2
	        idx ++;
			int numObjectives = Integer.valueOf(args[idx]); //1
			idx ++;
			int numPreferences = Integer.valueOf(args[idx]); //50
			idx ++;

			//RuleTest ruleTest = new RuleTest(trainPath, ruleType, numRuns, testScenario, testSetName, numTrees);
			//modified by fzhang  24.5.2018  use multipleTreeRuleTest
			ParetoSetLearningMultiTreeRuleTestMeng multipletreeruleTest = new ParetoSetLearningMultiTreeRuleTestMeng(trainPath, ruleType, numRuns, testScenario, testSetName, numTrees, numPreferences);

			for (int i = 0; i < numObjectives; i++) {
				multipletreeruleTest.addObjective(args[idx]);
				idx ++;
			}

			int topN = 5; // add by mengxu 2024.5.21

//			multipletreeruleTest.writeEachRunToCSV(curRun-1, topN);
			multipletreeruleTest.writeEachRunToCSV(curRun-1, topN,true);

			//original
//			multipletreeruleTest.writeToCSV();
		}
}
