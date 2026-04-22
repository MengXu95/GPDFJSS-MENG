package mengxu.ruleanalysis.furtherAnalysis;

import yimei.jss.jobshop.Objective;
import yimei.jss.ruleanalysis.ResultFileReader;
import yimei.jss.ruleanalysis.RuleTypeV2;
import yimei.jss.ruleanalysis.TestResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TrainDiversityLocalExplore {

	 public static final long simSeed = 968356;

		protected String trainPath; //the directory of training things
	    protected RuleTypeV2 ruleType;
	    protected int numRuns;
	    protected String testScenario;
	    protected String testSetName;
	    protected List<Objective> objectives; // The objectives to test.
	    protected int numTrees;

	    public TrainDiversityLocalExplore(String trainPath, RuleTypeV2 ruleType, int numRuns,
                                          String testScenario, String testSetName,
                                          List<Objective> objectives, int numTrees) {
	        this.trainPath = trainPath;
	        this.ruleType = ruleType;
	        this.numRuns = numRuns;
	        this.testScenario = testScenario;
	        this.testSetName = testSetName;
	        this.objectives = objectives;
	        this.numTrees = numTrees;
	    }

	    public TrainDiversityLocalExplore(String trainPath, RuleTypeV2 ruleType, int numRuns,
                                          String testScenario, String testSetName, int numTreess) {
	        this(trainPath, ruleType, numRuns, testScenario, testSetName, new ArrayList<>(), numTreess);
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
//		public SchedulingSet generateTestSet() {
//	        return SchedulingSet.generateSet(simSeed, testScenario,
//	                testSetName, objectives, 50);
//	    }

		public void writeToCSV() {
//	        SchedulingSet testSet = generateTestSet();
	        File targetPath = new File(trainPath + "test"); //create a folder named "test" in trainPath
	        if (!targetPath.exists()) {
	            targetPath.mkdirs();
	        }

	        File csvDiversityFile = new File(targetPath + "/" + testSetName + "-diversities.csv"); //create a .csv to save the test result

			File csvSeqAndRoutExploredFile = new File(targetPath + "/" + testSetName + "-SeqAndRoutExplored.csv"); //create a .csv to save the test result

			File csvNumOffByLocalAndExploreFile = new File(targetPath + "/" + testSetName + "-NumOffByLocalAndExplore.csv"); //create a .csv to save the test result

	        List<TestResult> testResults = new ArrayList<>();

	        //for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns
	        for (int i = 0; i < numRuns; i++) {
	        	System.out.println("Run "+ i);
	        //for (int i = 0; i < 1; i++) {
	            File sourceFile = new File(trainPath + "job." + i + ".out.stat");  //this file keeps the rule

//	            TestResult result = MultipleTreeTestResult.readFromFile(sourceFile, ruleType, numTrees);
				TestResult result = new TestResult();

	            //Didn't bother saving time files
//	            File timeFile = new File(trainPath + "job." + i + ".time.csv");
//	            result.setGenerationalTimeStat(MultipleTreeResultFileReader.readTimeFromFile(timeFile));

				//Didn't bother saving time files
				File diversityFile = new File(trainPath + "job." + i + ".diversities.csv");
				result.setGenerationalGenotypeDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,1));
				result.setGenerationalPhenotypeDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,2));
				result.setGenerationalEntropyDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,3));
				result.setGenerationalPseudoIsomorphsDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,4));
				result.setGenerationalEditOneDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,5));
				result.setGenerationalEditTwoDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,6));
				result.setGenerationalPCDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,7));
//				result.setGenerationalParentSelectionDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,8));

				//add by mengxu 2023.06.29
				//Didn't bother saving time files
				File SeqAndRoutExploredFile = new File(trainPath + "job." + i + ".SeqAndRoutExplored.csv");
				result.setSequencingExploredStat(ResultFileReader.readSeqAndRoutExploredFromFile(SeqAndRoutExploredFile,1));
				result.setRoutingExploredStat(ResultFileReader.readSeqAndRoutExploredFromFile(SeqAndRoutExploredFile,2));

				//add by mengxu 2023.06.29
				//Didn't bother saving time files
				File NumOffByLocalAndExploreFile = new File(trainPath + "job." + i + ".NumOffByLocalAndExplore.csv");
				result.setNumOffByLocalStat(ResultFileReader.readNumOffByLocalAndExploreFromFile(NumOffByLocalAndExploreFile,1));
				result.setNumOffByExploreStat(ResultFileReader.readNumOffByLocalAndExploreFromFile(NumOffByLocalAndExploreFile,2));



				//24.8.2018 fzhang read badrun in CSV
	      /*      File badrunsFile = new File(trainPath + "job." + i + ".BadRun.csv");
	            result.setGenerationalBadRunStat(MultipleTreeResultFileReader.readBadRunFromFile(badrunsFile));*/

	            long start = System.currentTimeMillis();


	            long finish = System.currentTimeMillis();
	            long duration = (finish - start)/1000;
	            System.out.println("Duration = " + duration + " s.");

	            testResults.add(result);
	        }

	        try {
	            BufferedWriter writer = new BufferedWriter(new FileWriter(csvDiversityFile.getAbsoluteFile()));
				writer.write("Run,Generation," +
						"GenotypeDiversity, PhenotypeDiversity, EntropyDiversity, PseudoIsomorphsDiversity," +
						"EditOneDiversity, EditTwoDiversity, PCDiversity");
	            writer.newLine();

	            //for (int i = 0; i < 1; i++) {
	            for (int i = 0; i < numRuns; i++) {
	                TestResult result = testResults.get(i);

	                //for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
	                for (int j = 0; j < 51; j++) { //use rules in each generation for testing
	                    if (objectives.size() == 1 || objectives.size() == 2) {
	                        writer.write(i + "," + j + "," +
									result.getGenerationalGenotypeDiversityStat(j)+ ","+
									result.getGenerationalPhenotypeDiversityStat(j)+ ","+
									result.getGenerationalEntropyDiversityStat(j)+ ","+
									result.getGenerationalPseudoIsomorphsDiversityStat(j)+ ","+
									result.getGenerationalEditOneDiversityStat(j)+ ","+
									result.getGenerationalEditTwoDiversityStat(j)+ ","+
									result.getGenerationalPCDiversityStat(j));
	                        writer.newLine();
	                    }
	                }
	            }
	            writer.close();
	        } catch (IOException e) {
	            e.printStackTrace();
	        }


			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(csvSeqAndRoutExploredFile.getAbsoluteFile()));
				writer.write("Run,Generation," +
						"sequencing, routing");
				writer.newLine();

				//for (int i = 0; i < 1; i++) {
				for (int i = 0; i < numRuns; i++) {
					TestResult result = testResults.get(i);

					//for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
					for (int j = 0; j < 51; j++) { //use rules in each generation for testing
						if (objectives.size() == 1 || objectives.size() == 2) {
							writer.write(i + "," + j + "," +
									result.getGenerationalSequencingExploredStat(j)+ ","+
									result.getGenerationalRoutingExploredStat(j));
							writer.newLine();
						}
					}
				}
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				BufferedWriter writer = new BufferedWriter(new FileWriter(csvNumOffByLocalAndExploreFile.getAbsoluteFile()));
				writer.write("Run,Generation," +
						"byLocal, byExplore");
				writer.newLine();

				//for (int i = 0; i < 1; i++) {
				for (int i = 0; i < numRuns; i++) {
					TestResult result = testResults.get(i);

					//for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
					for (int j = 0; j < 51; j++) { //use rules in each generation for testing
						if (objectives.size() == 1 || objectives.size() == 2) {
							writer.write(i + "," + j + "," +
									result.getGenerationalNumOffByLocalStat(j)+ ","+
									result.getGenerationalNumOffByExploreStat(j));
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
			TrainDiversityLocalExplore multipletreeruleTest = new TrainDiversityLocalExplore(trainPath, ruleType, numRuns, testScenario, testSetName, numTrees);

			for (int i = 0; i < numObjectives; i++) {
				multipletreeruleTest.addObjective(args[idx]);
				idx ++;
			}

			multipletreeruleTest.writeToCSV();
		}
}
