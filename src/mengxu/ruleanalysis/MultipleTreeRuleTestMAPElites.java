package mengxu.ruleanalysis;

import ec.Fitness;
import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.ensemble.EnsembleRule;
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

public class MultipleTreeRuleTestMAPElites {

	 public static final long simSeed = 968356;

		protected String trainPath; //the directory of training things
	    protected RuleTypeV2 ruleType;
	    protected int indexOfRun;
	    protected String testScenario;
	    protected String testSetName;
	    protected List<Objective> objectives; // The objectives to test.
	    protected int numTrees;

	    public MultipleTreeRuleTestMAPElites(String trainPath, RuleTypeV2 ruleType, int indexOfRun,
											 String testScenario, String testSetName,
											 List<Objective> objectives, int numTrees) {
	        this.trainPath = trainPath;
	        this.ruleType = ruleType;
	        this.indexOfRun = indexOfRun;
	        this.testScenario = testScenario;
	        this.testSetName = testSetName;
	        this.objectives = objectives;
	        this.numTrees = numTrees;
	    }

	    public MultipleTreeRuleTestMAPElites(String trainPath, RuleTypeV2 ruleType, int indexOfRun,
											 String testScenario, String testSetName, int numTreess) {
	        this(trainPath, ruleType, indexOfRun, testScenario, testSetName, new ArrayList<>(), numTreess);
	    }

	    public String getTrainPath() {
	        return trainPath;
	    }

	    public RuleTypeV2 getRuleType() {
	        return ruleType;
	    }

	    public int getIndexOfRun() {
	        return indexOfRun;
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
	                testSetName, objectives, 50);
	    }

		public void writeToCSV() {
	        SchedulingSet testSet = generateTestSet();
	        File targetPath = new File(trainPath + "test"); //create a folder named "test" in trainPath
	        if (!targetPath.exists()) {
	            targetPath.mkdirs();
	        }

	        File csvFile = new File(targetPath + "/" + testSetName + "_" + indexOfRun + ".csv"); //create a .csv to save the test result

//	        List<TestResult> testResults = new ArrayList<>();

	        //for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns
			int i = indexOfRun;
//	        for (int i = 0; i < numRuns; i++) {
	        	System.out.println("Run "+ i);
	        //for (int i = 0; i < 1; i++) {
	            File sourceFile = new File(trainPath + "job." + i + ".out.stat");  //this file keeps the rule
	            TestResult result = MultipleTreeTestResult.readMAPElitesFromFile(sourceFile, ruleType, numTrees);

	            //Didn't bother saving time files
	            File timeFile = new File(trainPath + "job." + i + ".time.csv");
	            result.setGenerationalTimeStat(MultipleTreeResultFileReader.readTimeFromFile(timeFile));

				//read average rule size in to .csv
				File aveDiversityFile = new File(trainPath + "job." + i + ".diversities.csv");
				result.setGenerationalGenotypeDiversityStat(MultipleTreeResultFileReader.readDiversitiesFromFile(aveDiversityFile, 1));
				result.setGenerationalPhenotypeDiversityStat(MultipleTreeResultFileReader.readDiversitiesFromFile(aveDiversityFile, 2));
				result.setGenerationalEntropyDiversityStat(MultipleTreeResultFileReader.readDiversitiesFromFile(aveDiversityFile, 3));
				result.setGenerationalPseudoIsomorphsDiversityStat(MultipleTreeResultFileReader.readDiversitiesFromFile(aveDiversityFile, 4));
				result.setGenerationalEditOneDiversityStat(MultipleTreeResultFileReader.readDiversitiesFromFile(aveDiversityFile, 5));
				result.setGenerationalEditTwoDiversityStat(MultipleTreeResultFileReader.readDiversitiesFromFile(aveDiversityFile, 6));
				result.setGenerationalPCDiversityStat(MultipleTreeResultFileReader.readDiversitiesFromFile(aveDiversityFile, 7));

				//24.8.2018 fzhang read badrun in CSV
	      /*      File badrunsFile = new File(trainPath + "job." + i + ".BadRun.csv");
	            result.setGenerationalBadRunStat(MultipleTreeResultFileReader.readBadRunFromFile(badrunsFile));*/

	            long start = System.currentTimeMillis();

//	            result.validate(objectives);

	            //for (int j = 42; j < result.getGenerationalRules().size(); j++) {
	             for (int j = 0; j < result.getGenerationalRules().size(); j++) {
					 if(result.getGenerationalRules(j) != null){// single individual win
						 AbstractRule[] generationalRules = result.getGenerationalRules(j);
						 if (numTrees == 2) {
							 //add by mengxu 2023.03.08
							 testSet.getSimulations().get(0).useMultiCaseEnsemble = false;
							 testSet.getSimulations().get(0).useMultiSubpopMultiCaseEnsemble = false;
							 testSet.getSimulations().get(0).useVotingEnsemble = false;
							 testSet.getSimulations().get(0).useLS = false;
							 generationalRules[0].calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
									 result.getGenerationalTestFitness(j), null,
									 testSet, generationalRules[1], objectives);
						 }

						 System.out.println("Generation " + j + ": test fitness = " +
								 result.getGenerationalTestFitness(j).fitness());
					 }
					 else{//ensemble win todo: need double check
//						 for (int j = 0; j < result.getGenerationalEnsembleRules().size(); j++) {
						 EnsembleRule generationalEnsembleRules = result.getGenerationalEnsembleRules().get(j);
						 if (numTrees == 2) {
							 //add by mengxu 2023.03.08
							 //following is to get the test objective value of each element in ensemble
							 for(int e=0; e<generationalEnsembleRules.getEnsembleSequencingRule().size(); e++){
								 GPRule sequencingRule = generationalEnsembleRules.getEnsembleSequencingRule().get(e);
								 GPRule routingRule = generationalEnsembleRules.getEnsembleRoutingRule().get(e);
								 //add by mengxu 2023.03.08
								 testSet.getSimulations().get(0).useMultiCaseEnsemble = false;
								 testSet.getSimulations().get(0).useMultiSubpopMultiCaseEnsemble = false;
								 testSet.getSimulations().get(0).useVotingEnsemble = false;
								 testSet.getSimulations().get(0).useLS = false;
								 sequencingRule.calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
										 result.getGenerationalEnsembleEachTestFitnessesGen(j).get(e), null,
										 testSet, routingRule, objectives);
								 //todo: during this process, also need to store the ensemble contribution of each element 2023.02.27
							 }
//							 testSet.getSimulations().get(0).useMultiCaseEnsemble = false;
//							 testSet.getSimulations().get(0).useMultiSubpopMultiCaseEnsemble = false;
//							 testSet.getSimulations().get(0).useVotingEnsemble = true;
//							 testSet.getSimulations().get(0).useLS = false;
//							 generationalEnsembleRules.calcFitnessByVotingEnsemble(  //this is to get the final fitness by the voting of ensemble
//									 result.getGenerationalEnsembleTestFitnesses().get(j), null,
//									 testSet, objectives);
//							 double[] testEnsembleContribution = generationalEnsembleRules.getEnsembleContribution();
//							 for(int c=0; c< testEnsembleContribution.length; c++){
//								 result.getGenerationalTestEnsembleContribution().get(j).add(testEnsembleContribution[c]);
//							 }

						 }

//						 System.out.println("Generation " + j + ": ensemble test fitness = " +
//								 result.getGenerationalEnsembleTestFitnesses().get(j).fitness());
					 }

	            }


	            long finish = System.currentTimeMillis();
	            long duration = (finish - start)/1000;
	            System.out.println("Duration = " + duration + " s.");


//				testResults.add(result);

//	        }

	        try {
	            BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
	            /*writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
	                    "RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness, TrainTime, BadRun");*/
	            
//	            writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
//	                    "RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness,EnsembleTestFitness, TrainTime," +
//						"GenotypeDiversity,PhenotypeDiversity,EntropyDiversity,PseudoIsomorphsDiversity," +
//						"EditOneDiversity,EditTwoDiversity,PCDiversity");

				writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
						"RoutRuleUniqueTerminals,Obj,IndividualWin,finalTrainFitness,TrainFitness,TestFitness,TrainTime," +
						"GenotypeDiversity,PhenotypeDiversity,EntropyDiversity,PseudoIsomorphsDiversity," +
						"EditOneDiversity,EditTwoDiversity,PCDiversity");
	            writer.newLine();

	            //for (int i = 0; i < 1; i++) {
//	            for (int i = 0; i < numRuns; i++) {
//	                TestResult result = testResults.get(i);

	                //for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
	                for (int j = 0; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
						if(result.getGenerationalRules(j) != null) {// single individual win

							MultiObjectiveFitness trainFit =
									(MultiObjectiveFitness) result.getGenerationalTrainFitness(j);
							MultiObjectiveFitness testFit =
									(MultiObjectiveFitness) result.getGenerationalTestFitness(j);

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

							double genotypeDiversity = -1;
							double phenotypeDiversity = -1;
							double entropyDiversity = -1;
							double pseudoIsomoDiversity = -1;
							double editOneDiversity = -1;
							double editTwoDiversity = -1;
							double PCDiversity = -1;
							//modified by mengxu 2021.04.16
							if(j < result.getGenotypeDiversityStat().getN()){
								genotypeDiversity = result.getGenerationalGenotypeDiversityStat(j);
								phenotypeDiversity = result.getGenerationalPhenotypeDiversityStat(j);
								entropyDiversity = result.getGenerationalEntropyDiversityStat(j);
								pseudoIsomoDiversity = result.getGenerationalPseudoIsomorphsDiversityStat(j);
								editOneDiversity = result.getGenerationalEditOneDiversityStat(j);
								editTwoDiversity = result.getGenerationalEditTwoDiversityStat(j);
								PCDiversity = result.getGenerationalPCDiversityStat(j);
							}
//							double genotypeDiversity = result.getGenerationalGenotypeDiversityStat(j);
//							double phenotypeDiversity = result.getGenerationalPhenotypeDiversityStat(j);
//							double entropyDiversity = result.getGenerationalEntropyDiversityStat(j);
//							double pseudoIsomoDiversity = result.getGenerationalPseudoIsomorphsDiversityStat(j);
//							double editOneDiversity = result.getGenerationalEditOneDiversityStat(j);
//							double editTwoDiversity = result.getGenerationalEditTwoDiversityStat(j);
//							double PCDiversity = result.getGenerationalPCDiversityStat(j);


							if (objectives.size() == 1) {
								writer.write(i + "," + j + "," +
										seqRuleSize + "," +
										numUniqueTerminalsSeq + "," +
										routRuleSize + "," +
										numUniqueTerminalsRout + ",0," +
										"True," +
										trainFit.fitness() + "," +
										trainFit.fitness() + "," +
										testFit.fitness() + "," +
										result.getGenerationalTime(j) + "," +
										genotypeDiversity + "," +
										phenotypeDiversity + "," +
										entropyDiversity + "," +
										pseudoIsomoDiversity + "," +
										editOneDiversity + "," +
										editTwoDiversity + "," +
										PCDiversity);
								writer.newLine();
							} else {
								System.out.println("Error here!");
								writer.newLine();
							}
						}
						else{//ensemble win

							MultiObjectiveFitness finalTrainFit =
									(MultiObjectiveFitness) result.getGenerationalTrainFitness(j);
//							MultiObjectiveFitness finalTestFit =
//									(MultiObjectiveFitness) result.getGenerationalEnsembleTestFitnessesGen(j);
							List<Fitness> eachTrainFit = result.getGenerationalEnsembleTrainFitnessesGen(j);
							List<Fitness> eachTestFit = result.getGenerationalEnsembleEachTestFitnessesGen(j);
//							List<Double> trainEnsembleContribution = result.getGenerationalTrainEnsembleContribution().get(j);
//							List<Double> testEnsembleContribution = result.getGenerationalTestEnsembleContribution().get(j);
							//todo: need to get the ensemble contribution of each element

							EnsembleRule ensembleRule = result.getGenerationalEnsembleRules().get(j);
							List<GPRule> sequencingRules = ensembleRule.getEnsembleSequencingRule();
							List<GPRule> routingRules = ensembleRule.getEnsembleRoutingRule();

							double genotypeDiversity = -1;
							double phenotypeDiversity = -1;
							double entropyDiversity = -1;
							double pseudoIsomoDiversity = -1;
							double editOneDiversity = -1;
							double editTwoDiversity = -1;
							double PCDiversity = -1;
							//modified by mengxu 2021.04.16
							if(j < result.getGenotypeDiversityStat().getN()){
								genotypeDiversity = result.getGenerationalGenotypeDiversityStat(j);
								phenotypeDiversity = result.getGenerationalPhenotypeDiversityStat(j);
								entropyDiversity = result.getGenerationalEntropyDiversityStat(j);
								pseudoIsomoDiversity = result.getGenerationalPseudoIsomorphsDiversityStat(j);
								editOneDiversity = result.getGenerationalEditOneDiversityStat(j);
								editTwoDiversity = result.getGenerationalEditTwoDiversityStat(j);
								PCDiversity = result.getGenerationalPCDiversityStat(j);
							}
							//modified by mengxu 2021.04.16
//							double genotypeDiversity = result.getGenerationalGenotypeDiversityStat(j);
//							double phenotypeDiversity = result.getGenerationalPhenotypeDiversityStat(j);
//							double entropyDiversity = result.getGenerationalEntropyDiversityStat(j);
//							double pseudoIsomoDiversity = result.getGenerationalPseudoIsomorphsDiversityStat(j);
//							double editOneDiversity = result.getGenerationalEditOneDiversityStat(j);
//							double editTwoDiversity = result.getGenerationalEditTwoDiversityStat(j);
//							double PCDiversity = result.getGenerationalPCDiversityStat(j);

							for(int e=0; e<sequencingRules.size(); e++){
								GPRule seqRule = null;
								GPRule routRule = null;
								if (numTrees == 2) {
									if (sequencingRules.get(e).getType() == yimei.jss.rule.RuleType.SEQUENCING) {
										seqRule = sequencingRules.get(e);
										routRule = routingRules.get(e);
									} else {
										System.out.println("error here!");
									}
								} else {
									System.out.println("error here!");
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

								if (objectives.size() == 1) {
									writer.write(i + "," + j + "," +
											seqRuleSize + "," +
											numUniqueTerminalsSeq + "," +
											routRuleSize + "," +
											numUniqueTerminalsRout + ",0," +
											"False," +
											finalTrainFit.fitness() + "," +
											eachTrainFit.get(e).fitness() + "," +
											eachTestFit.get(e).fitness() + "," +
											result.getGenerationalTime(j) + "," +
											genotypeDiversity + "," +
											phenotypeDiversity + "," +
											entropyDiversity + "," +
											pseudoIsomoDiversity + "," +
											editOneDiversity + "," +
											editTwoDiversity + "," +
											PCDiversity);
									writer.newLine();
								} else {
									System.out.println("Error here!");
									writer.newLine();
								}
							}
						}
	                }
//	            }
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
	        int indexofRun = Integer.valueOf(args[idx]); //30
	        idx ++;
	        String testScenario = args[idx]; //dynamic
	        idx ++;
	        String testSetName = args[idx]; //missing-0.85-4.0
	        idx ++;
	        int numTrees = Integer.valueOf(args[idx]); //2
	        idx ++;
			int numObjectives = Integer.valueOf(args[idx]); //1
			idx ++;

			//RuleTest ruleTest = new RuleTest(trainPath, ruleType, numRuns, testScenario, testSetName, numTrees);
			//modified by fzhang  24.5.2018  use multipleTreeRuleTest
			MultipleTreeRuleTestMAPElites multipletreeruleTest = new MultipleTreeRuleTestMAPElites(trainPath, ruleType, indexofRun, testScenario, testSetName, numTrees);

			for (int i = 0; i < numObjectives; i++) {
				multipletreeruleTest.addObjective(args[idx]);
				idx ++;
			}

			multipletreeruleTest.writeToCSV();
		}

	public static class MultipleTreeRuleTestSingleRun {

		 public static final long simSeed = 968356;

			protected String trainPath; //the directory of training things
			protected RuleTypeV2 ruleType;
	//	    protected int numRuns;
			protected String testScenario;
			protected String testSetName;
			protected List<Objective> objectives; // The objectives to test.
			protected int numTrees;

			protected int indexOfRun;

			public MultipleTreeRuleTestSingleRun(String trainPath, RuleTypeV2 ruleType, int indexOfRun,
												 String testScenario, String testSetName,
												 List<Objective> objectives, int numTrees) {
				this.trainPath = trainPath;
				this.ruleType = ruleType;
				this.indexOfRun = indexOfRun;
				this.testScenario = testScenario;
				this.testSetName = testSetName;
				this.objectives = objectives;
				this.numTrees = numTrees;
			}

			public MultipleTreeRuleTestSingleRun(String trainPath, RuleTypeV2 ruleType, int numRuns,
												 String testScenario, String testSetName, int numTreess) {
				this(trainPath, ruleType, numRuns, testScenario, testSetName, new ArrayList<>(), numTreess);
			}

			public String getTrainPath() {
				return trainPath;
			}

			public RuleTypeV2 getRuleType() {
				return ruleType;
			}

	//	    public int getNumRuns() {
	//	        return numRuns;
	//	    }

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
						testSetName, objectives, 50);
			}

			public void writeToCSV() {
				SchedulingSet testSet = generateTestSet();
				File targetPath = new File(trainPath + "test"); //create a folder named "test" in trainPath
				if (!targetPath.exists()) {
					targetPath.mkdirs();
				}

				File csvFile = new File(targetPath + "/" + testSetName + "_" + indexOfRun + ".csv"); //create a .csv to save the test result

				List<TestResult> testResults = new ArrayList<>();

				//for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns
				int i = indexOfRun;
	//	        for (int i = 0; i < numRuns; i++) {
					System.out.println("Run "+ i);
				//for (int i = 0; i < 1; i++) {
					File sourceFile = new File(trainPath + "job." + i + ".out.stat");  //this file keeps the rule
					TestResult result = MultipleTreeTestResult.readFromFile(sourceFile, ruleType, numTrees);

					//Didn't bother saving time files
					File timeFile = new File(trainPath + "job." + i + ".time.csv");
					result.setGenerationalTimeStat(MultipleTreeResultFileReader.readTimeFromFile(timeFile));

	//				//Didn't bother saving time files
	//				File diversityFile = new File(trainPath + "job." + i + ".diversities.csv");
	//				result.setGenerationalGenotypeDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,1));
	//				result.setGenerationalPhenotypeDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,2));
	//				result.setGenerationalEntropyDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,3));
	//				result.setGenerationalPseudoIsomorphsDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,4));
	//				result.setGenerationalEditOneDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,5));
	//				result.setGenerationalEditTwoDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,6));
	//				result.setGenerationalPCDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,7));
	//				result.setGenerationalParentSelectionDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,8));



					//24.8.2018 fzhang read badrun in CSV
			  /*      File badrunsFile = new File(trainPath + "job." + i + ".BadRun.csv");
					result.setGenerationalBadRunStat(MultipleTreeResultFileReader.readBadRunFromFile(badrunsFile));*/

					long start = System.currentTimeMillis();

	//	            result.validate(objectives);

					//for (int j = 42; j < result.getGenerationalRules().size(); j++) {
					 for (int j = 0; j < result.getGenerationalRules().size(); j++) {
						AbstractRule[] generationalRules = result.getGenerationalRules(j);
						if (numTrees == 2) {
							generationalRules[0].calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
									result.getGenerationalTestFitness(j), null,
									testSet, generationalRules[1], objectives);
						}
						//generationalRules[1] is routing rule

						System.out.println("Generation " + j + ": test fitness = " +
								result.getGenerationalTestFitness(j).fitness());
					}

					long finish = System.currentTimeMillis();
					long duration = (finish - start)/1000;
					System.out.println("Duration = " + duration + " s.");

					testResults.add(result);
	//	        }

				try {
					BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
					/*writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
							"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness, TrainTime, BadRun");*/

					writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
							"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness,TrainTime");
	//				writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
	//						"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness,TrainTime," +
	//						"GenotypeDiversity, PhenotypeDiversity, EntropyDiversity, PseudoIsomorphsDiversity," +
	//						"EditOneDiversity, EditTwoDiversity, PCDiversity, ParentSelectionDiversity");
					writer.newLine();

					//for (int i = 0; i < 1; i++) {
	//	            for (int i = 0; i < numRuns; i++) {
	//	                TestResult result = testResults.get(i);

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

						  /*  if (objectives.size() == 1) {
								writer.write(i + "," + j + "," +
										seqRuleSize + "," +
										numUniqueTerminalsSeq + "," +
										routRuleSize +"," +
										numUniqueTerminalsRout +",0," +
										trainFit.fitness() + "," +
										testFit.fitness()+ ","+
										result.getGenerationalTime(j)+ ","+
										result.getGenerationalBadRun(j));
								writer.newLine();
							}*/

							if (objectives.size() == 1) {
								writer.write(i + "," + j + "," +
										seqRuleSize + "," +
										numUniqueTerminalsSeq + "," +
										routRuleSize +"," +
										numUniqueTerminalsRout +",0," +
										trainFit.fitness() + "," +
										testFit.fitness()+ ","+
										result.getGenerationalTime(j));
	//									result.getGenerationalGenotypeDiversityStat(j)+ ","+
	//									result.getGenerationalPhenotypeDiversityStat(j)+ ","+
	//									result.getGenerationalEntropyDiversityStat(j)+ ","+
	//									result.getGenerationalPseudoIsomorphsDiversityStat(j)+ ","+
	//									result.getGenerationalEditOneDiversityStat(j)+ ","+
	//									result.getGenerationalEditTwoDiversityStat(j)+ ","+
	//									result.getGenerationalPCDiversityStat(j)+ ","+
	//									result.getGenerationalParentSelectionDiversityStat(j));
								writer.newLine();
							}
							else {
	//	                        writer.write(i + "," + j + "," +
	//	                                rule.getGPTree().child.numNodes(GPNode.NODESEARCH_ALL) + "," +
	//	                                numUniqueTerminals + ",");

								for (int k = 0; k < objectives.size(); k++) {
									writer.write(k + "," +
											trainFit.getObjective(k) + "," +
											testFit.getObjective(k) + ",");
								}
								writer.newLine();
							}
						}
	//	            }
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
				int indexofRun = Integer.valueOf(args[idx]); //30
				idx ++;
				String testScenario = args[idx]; //dynamic
				idx ++;
				String testSetName = args[idx]; //missing-0.85-4.0
				idx ++;
				int numTrees = Integer.valueOf(args[idx]); //2
				idx ++;
				int numObjectives = Integer.valueOf(args[idx]); //1
				idx ++;

				//RuleTest ruleTest = new RuleTest(trainPath, ruleType, numRuns, testScenario, testSetName, numTrees);
				//modified by fzhang  24.5.2018  use multipleTreeRuleTest
				MultipleTreeRuleTestSingleRun multipletreeruleTest = new MultipleTreeRuleTestSingleRun(trainPath, ruleType, indexofRun, testScenario, testSetName, numTrees);

				for (int i = 0; i < numObjectives; i++) {
					multipletreeruleTest.addObjective(args[idx]);
					idx ++;
				}

				multipletreeruleTest.writeToCSV();
			}
	}
}
