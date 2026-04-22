package mengxu.ruleanalysis.MultitaskLearning;

import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
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

public class MultiTaskMultipleTreeRuleTestSingleRun {

	 public static final long simSeed = 968356;

		protected String trainPath; //the directory of training things
	    protected RuleTypeV2 ruleType;
	    protected int indexOfRun;
	    protected String testScenario;
	    protected List<Objective> objectives; // The objectives to test.
	    protected int numTrees;
		protected int numTasks;
		protected ArrayList<String> testSetNames;

	    public MultiTaskMultipleTreeRuleTestSingleRun(String trainPath, RuleTypeV2 ruleType, int indexOfRun,
													  String testScenario, int numTasks, ArrayList<String> testSetNames,
													  List<Objective> objectives, int numTrees) {
	        this.trainPath = trainPath;
	        this.ruleType = ruleType;
	        this.indexOfRun = indexOfRun;
	        this.testScenario = testScenario;
	        this.testSetNames = testSetNames;
	        this.objectives = objectives;
	        this.numTrees = numTrees;
			this.numTasks = numTasks;
	    }

	    public MultiTaskMultipleTreeRuleTestSingleRun(String trainPath, RuleTypeV2 ruleType, int indexofRun, String testScenario,
													  int numTasks, ArrayList<String> testSetNames, int numTreess) {
	        this(trainPath, ruleType, indexofRun, testScenario, numTasks, testSetNames, new ArrayList<>(), numTreess);
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
		public SchedulingSet generateTestSet(String testSetName) {
	        return SchedulingSet.generateSet(simSeed, testScenario,
	                testSetName, objectives, 50);
	    }

		public void writeToCSV() {
			System.out.println("Run "+ indexOfRun);
			for(int task=0; task< testSetNames.size(); task++){
				System.out.println("Task "+ task);
				String testSetName = testSetNames.get(task);
				SchedulingSet testSet = generateTestSet(testSetName);
				File targetPath = new File(trainPath + "test"); //create a folder named "test" in trainPath
				if (!targetPath.exists()) {
					targetPath.mkdirs();
				}
				File csvFile = new File(targetPath + "/" + testSetName + "_" + indexOfRun + ".csv"); //create a .csv to save the test result

				List<TestResult> testResults = new ArrayList<>();

				File sourceFile = new File(trainPath + "job." + indexOfRun + ".out.stat");  //this file keeps the rule
				TestResult result = MultipleTreeTestResult.readSubPopFromFile(sourceFile, ruleType, numTrees, task);

				//Didn't bother saving time files
				File timeFile = new File(trainPath + "job." + indexOfRun + ".time.csv");
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


				long start = System.currentTimeMillis();

				for (int j = 0; j < result.getGenerationalRules().size(); j++) {
					AbstractRule[] generationalRules = result.getGenerationalRules(j);
					if (numTrees == 2) {
						generationalRules[0].calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
								result.getGenerationalTestFitness(j), null,
								testSet, generationalRules[1], objectives);
					}

					System.out.println("Generation " + j + ": test fitness = " +
							result.getGenerationalTestFitness(j).fitness());
				}

				long finish = System.currentTimeMillis();
				long duration = (finish - start)/1000;
				System.out.println("Duration = " + duration + " s.");

				testResults.add(result);

				try {
					BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));

					writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
							"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness,TrainTime");
					writer.newLine();

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

						if (objectives.size() == 1) {
							writer.write(indexOfRun + "," + j + "," +
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
							for (int k = 0; k < objectives.size(); k++) {
								writer.write(k + "," +
										trainFit.getObjective(k) + "," +
										testFit.getObjective(k) + ",");
							}
							writer.newLine();
						}
					}
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
	    }


	    /**
	     * Call this main method with several parameters
	     *
	     * /Users/dyska/Desktop/Uni/COMP489/GPJSS/grid_results/dynamic/raw/coevolution-fixed/0.85-max-flowtime/
	     * simple-rule
	     * 30  //indexofRun
	     * dynamic-job-shop
		 * 3 how many tasks
	     * missing-0.75-1.5 //parameter for each task
		 * missing-0.85-1.5 //parameter for each task
		 * missing-0.95-1.5 //parameter for each task
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
			int numTasks = Integer.valueOf(args[idx]); //3
			ArrayList<String> testSetNames = new ArrayList<>();
			for(int i=0; i<numTasks; i++){
				idx ++;
				String testSetName = args[idx]; //missing-0.75-1.5
				testSetNames.add(testSetName);
			}
	        idx ++;
	        int numTrees = Integer.valueOf(args[idx]); //2
	        idx ++;
			int numObjectives = Integer.valueOf(args[idx]); //1
			idx ++;

			//RuleTest ruleTest = new RuleTest(trainPath, ruleType, numRuns, testScenario, testSetName, numTrees);
			//modified by fzhang  24.5.2018  use multipleTreeRuleTest
			MultiTaskMultipleTreeRuleTestSingleRun multipletreeruleTest = new MultiTaskMultipleTreeRuleTestSingleRun(trainPath, ruleType, indexofRun, testScenario,
					                                                                               numTasks, testSetNames, numTrees);

			for (int i = 0; i < numObjectives; i++) {
				multipletreeruleTest.addObjective(args[idx]);
				idx ++;
			}

			multipletreeruleTest.writeToCSV();
		}
}
