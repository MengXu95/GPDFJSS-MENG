package mengxu.ruleanalysis.furtherAnalysis;

import yimei.jss.jobshop.Objective;
import yimei.jss.ruleanalysis.MultipleTreeTestResult;
import yimei.jss.ruleanalysis.ResultFileReader;
import yimei.jss.ruleanalysis.RuleTypeV2;
import yimei.jss.ruleanalysis.TestResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TrainDiversityAndCombinationAndXoverPro {

	 public static final long simSeed = 968356;

		protected String trainPath; //the directory of training things
	    protected RuleTypeV2 ruleType;
	    protected int numRuns;
	    protected String testScenario;
	    protected String testSetName;
	    protected List<Objective> objectives; // The objectives to test.
	    protected int numTrees;

	    public TrainDiversityAndCombinationAndXoverPro(String trainPath, RuleTypeV2 ruleType, int numRuns,
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

	    public TrainDiversityAndCombinationAndXoverPro(String trainPath, RuleTypeV2 ruleType, int numRuns,
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

	        File csvFile = new File(targetPath + "/" + testSetName + "-diversities.csv");//create a .csv to save the test result
			File csvCombinationFile = new File(targetPath + "/" + testSetName + "-combinationnumber.csv");
			File csvXoverFile = new File(targetPath + "/" + testSetName + "-xoverprobability.csv");


			List<TestResult> testResults = new ArrayList<>();

	        //for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns
	        for (int i = 0; i < numRuns; i++) {
//	        	System.out.println("Run "+ i);
	        //for (int i = 0; i < 1; i++) {
	            File sourceFile = new File(trainPath + "job." + i + ".out.stat");  //this file keeps the rule
	            TestResult result = MultipleTreeTestResult.readFromFile(sourceFile, ruleType, numTrees);

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
				result.setGenerationalParentSelectionDiversityStat(ResultFileReader.readDiversitiesFromFile(diversityFile,8));

				//add by mengxu 2022.01.19
				File combinationFile = new File(trainPath + "job." + i + ".combinationnumber.csv");
				result.setGenerationalCombinationNumberStat(ResultFileReader.readCombinationNumberFromFile(combinationFile));

				//add by mengxu 2022.01.19
				File xoverFile = new File(trainPath + "job." + i + ".xoverprobability.csv");
				result.setGenerationalXoverProbabilityStat(ResultFileReader.readXoverProbabilityFromFile(xoverFile));



				//24.8.2018 fzhang read badrun in CSV
	      /*      File badrunsFile = new File(trainPath + "job." + i + ".BadRun.csv");
	            result.setGenerationalBadRunStat(MultipleTreeResultFileReader.readBadRunFromFile(badrunsFile));*/

	            long start = System.currentTimeMillis();


	            long finish = System.currentTimeMillis();
	            long duration = (finish - start)/1000;
//	            System.out.println("Duration = " + duration + " s.");

	            testResults.add(result);
	        }

	        try {
	            BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));
	            /*writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
	                    "RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness, TrainTime, BadRun");*/
	            
//	            writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
//	                    "RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness,TrainTime");
				writer.write("Run,Generation," +
						"GenotypeDiversity, PhenotypeDiversity, EntropyDiversity, PseudoIsomorphsDiversity," +
						"EditOneDiversity, EditTwoDiversity, PCDiversity, ParentSelectionDiversity");
	            writer.newLine();

	            //for (int i = 0; i < 1; i++) {
	            for (int i = 0; i < numRuns; i++) {
	                TestResult result = testResults.get(i);

	                //for (int j = 42; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
	                for (int j = 0; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing
	                    
	                    if (objectives.size() == 1) {
	                        writer.write(i + "," + j + "," +
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

			try {
				BufferedWriter writerCombination = new BufferedWriter(new FileWriter(csvCombinationFile.getAbsoluteFile()));

				writerCombination.write("Run,Generation,combination");
				writerCombination.newLine();

				for (int i = 0; i < numRuns; i++) {
					TestResult result = testResults.get(i);

					for (int j = 0; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing

						if (objectives.size() == 1) {
							writerCombination.write(i + "," + j + "," +
									result.getGenerationalCombinationNumberStat(j));
							writerCombination.newLine();
						}
					}
				}
				writerCombination.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				BufferedWriter writerXoverPro = new BufferedWriter(new FileWriter(csvXoverFile.getAbsoluteFile()));

				writerXoverPro.write("Run,Generation,xoverPro");
				writerXoverPro.newLine();

				for (int i = 0; i < numRuns; i++) {
					TestResult result = testResults.get(i);

					for (int j = 0; j < result.getGenerationalRules().size(); j++) { //use rules in each generation for testing

						if (objectives.size() == 1) {
							writerXoverPro.write(i + "," + j + "," +
									result.getGenerationalXoverProbabilityStat(j));
							writerXoverPro.newLine();
						}
					}
				}
				writerXoverPro.close();
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
//			int idx = 0;
//			String trainPath = args[idx];
//	        idx ++;
//	        RuleType ruleType = RuleType.get(args[idx]);
//			idx ++;
//	        int numRuns = Integer.valueOf(args[idx]); //30
//	        idx ++;
//	        String testScenario = args[idx]; //dynamic
//	        idx ++;
//	        String testSetName = args[idx]; //missing-0.85-4.0
//	        idx ++;
//	        int numTrees = Integer.valueOf(args[idx]); //2
//	        idx ++;
//			int numObjectives = Integer.valueOf(args[idx]); //1
//			idx ++;

//			TrainDiversityAndCombinationAndXoverPro multipletreeruleTest = new TrainDiversityAndCombinationAndXoverPro(trainPath, ruleType, numRuns, testScenario, testSetName, numTrees);
//
//			for (int i = 0; i < numObjectives; i++) {
//				multipletreeruleTest.addObjective(args[idx]);
//				idx ++;
//			}
//
//			multipletreeruleTest.writeToCSV();


			//----by mengxu 2024.6.2-----
//			/home/xume/Desktop/PaperGPDPSBR/GPDPS2/multipletreegp-dynamic/max-weighted-tardiness-0.85-1.5/
//					simple-rule
//			30
//			dynamic-job-shop
//			missing-0.85-1.5
//			2
//			1
//			max-flowtime

			String workdir = "/Users/mengxu/Desktop/PhD research/2024 PhD paper/PaperGPDPSBR/";
			String[] algorithms = {"TourBase"};
//			String[] algorithms = {"epi-0","epi-0.05", "epi-0.15", "epi-0.2", "epi-0.25", "epi-0.3",
//					               "xover-0.005","xover-0.01","xover-0.015", "xover-0.025", "xover-0.03",
//					               "GPDPS1","GPDPS2", "GPDPS", "GPDPSs", "GPDPSo"};
			String testSetName = null;
			String[] objectives = {"max-flowtime-0.85-1.5","max-flowtime-0.95-1.5",
									"mean-flowtime-0.85-1.5","mean-flowtime-0.95-1.5",
									"max-weighted-tardiness-0.85-1.5","max-weighted-tardiness-0.95-1.5"};
			RuleTypeV2 ruleType = RuleTypeV2.get("simple-rule");
			int numRuns = 30;
			String testScenario = "dynamic-job-shop";
			int numTrees = 2;
			int numObjectives = 1;

			for(int i=0; i< algorithms.length; i++){
				System.out.println(algorithms[i] + ":");
				for(int j=0; j<objectives.length; j++){
					String trainPath = workdir + algorithms[i] + "/multipletreegp-dynamic/" + objectives[j] + "/";

					TrainDiversityAndCombinationAndXoverPro multipletreeruleTest = new TrainDiversityAndCombinationAndXoverPro(trainPath, ruleType, numRuns, testScenario, testSetName, numTrees);

					multipletreeruleTest.addObjective("null");
					multipletreeruleTest.writeToCSV();
				}
			}


		}
}
