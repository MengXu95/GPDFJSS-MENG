package mengxu.ruleanalysis.forManualRule;

import ec.Fitness;
import ec.gp.GPNode;
import ec.multiobjective.MultiObjectiveFitness;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.operation.basic.*;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.rule.workcenter.basic.NIQ;
import yimei.jss.rule.workcenter.basic.SBT;
import yimei.jss.rule.workcenter.basic.SRT;
import yimei.jss.rule.workcenter.basic.WIQ;
import yimei.jss.ruleanalysis.*;
import yimei.jss.simulation.event.AbstractEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ManualRuleTest {

	public static final long simSeed = 968356;
//	public static final long simSeed = 1;

	protected String trainPath; //the directory of training things
//	    protected RuleType ruleType;
	protected AbstractRule[] sequencingRule;
	protected AbstractRule[] routingRule;
	protected int numRuns;
	protected String testScenario;
	protected String testSetName;
	protected List<Objective> objectives; // The objectives to test.
	protected int numTrees;

	public ManualRuleTest(String trainPath, AbstractRule[] sequencingRule,
						  AbstractRule[] routingRule, int numRuns,
						  String testScenario, String testSetName,
						  List<Objective> objectives, int numTrees) {
		this.trainPath = trainPath;
		this.sequencingRule = sequencingRule;
		this.routingRule = routingRule;
		this.numRuns = numRuns;
		this.testScenario = testScenario;
		this.testSetName = testSetName;
		this.objectives = objectives;
		this.numTrees = numTrees;
	}

	public ManualRuleTest(String trainPath, AbstractRule[] sequencingRule,
						  AbstractRule[] routingRule, int numRuns,
						  String testScenario, String testSetName, int numTreess) {
		this(trainPath, sequencingRule, routingRule, numRuns, testScenario, testSetName, new ArrayList<>(), numTreess);
	}

	public String getTrainPath() {
	        return trainPath;
	    }

	public AbstractRule[] getSequencingRule() {
		return sequencingRule;
	}

	public AbstractRule[] getRoutingRule() {
		return routingRule;
	}

	//	    public RuleType getRuleType() {
//	        return ruleType;
//	    }

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

	public void writeToCSV() {
		SchedulingSet testSet = generateTestSet();
		File targetPath = new File(trainPath + "test"); //create a folder named "test" in trainPath
		if (!targetPath.exists()) {
			targetPath.mkdirs();
		}

		File csvFile = new File(targetPath + "/" + testSetName + "_" + this.objectives.get(0) + ".csv"); //create a .csv to save the test result

		List<Double> testResults = new ArrayList<>();
		List<AbstractRule[]> sequencingAndRoutingcombinations = new ArrayList<>();

		//for test: which machines are choosen by routing rule, CCGP. Scenario: run1, rule in generaiton 51 numRuns
		for (int i = 0; i < numRuns; i++) {
			System.out.println("Run "+ i);

			long start = System.currentTimeMillis();


			 for (int s = 0; s < this.sequencingRule.length; s++) {
				 for (int r = 0; r < this.routingRule.length; r++) {
					 AbstractRule[] generationalRules = new AbstractRule[2];
					 Fitness fitness = new MultiObjectiveFitness();
					 generationalRules[0] = this.sequencingRule[s];
					 generationalRules[1] = this.routingRule[r];

					 if (numTrees == 2) {
						 generationalRules[0].calcFitness(  //in calcFitness(), it will check which one is routing/sequencing rule
								 fitness, null,
								 testSet, generationalRules[1], objectives);
					 }

					 System.out.println("Sequencing rule: " + generationalRules[0].getName() +
							 "Routing rule: " + generationalRules[1].getName() +
							 ": test fitness = " +
							 fitness.fitness());

					 sequencingAndRoutingcombinations.add(generationalRules);
					 testResults.add(fitness.fitness());
				 }
			 }

			long finish = System.currentTimeMillis();
			long duration = (finish - start)/1000;
			System.out.println("Duration = " + duration + " s.");
		}

		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile.getAbsoluteFile()));

			writer.write("Run,SequencingRule,RoutingRule,TestFitness");

//			writer.write("Run,Generation,SeqRuleSize,SeqRuleUniqueTerminals,RoutRuleSize," +
//					"RoutRuleUniqueTerminals,Obj,TrainFitness,TestFitness,TrainTime");

			writer.newLine();


			for (int i = 0; i < numRuns; i++) {
				for (int j = 0; j < sequencingAndRoutingcombinations.size(); j++) { //use rules in each generation for testing
					String sequencingRule = sequencingAndRoutingcombinations.get(j)[0].getName();
					String routingRule = sequencingAndRoutingcombinations.get(j)[1].getName();
					double fitness = testResults.get(j);
					if (objectives.size() == 1) {
						writer.write(i + "," + sequencingRule + "," +
								routingRule + "," +
								fitness);
						writer.newLine();
					}
					else {
						System.out.println("Error here, there are more than one objectives!");
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

		String trainPath = "/Users/mengxu/Desktop/PhD_research/MO/ManualRule/";

//		AbstractRule FCFS = new FCFS(yimei.jss.rule.RuleType.SEQUENCING);
		AbstractRule SLACK = new Slack(yimei.jss.rule.RuleType.SEQUENCING);
		AbstractRule SPT = new SPT(yimei.jss.rule.RuleType.SEQUENCING);
		AbstractRule NPT = new NPT(yimei.jss.rule.RuleType.SEQUENCING);
		AbstractRule LWKR = new LWKR(yimei.jss.rule.RuleType.SEQUENCING);
//		AbstractRule[] sequencingRule = new AbstractRule[]{FCFS, SPT, NPT, LWKR};
		AbstractRule[] sequencingRule = new AbstractRule[]{SLACK};

		AbstractRule NIQ = new NIQ(yimei.jss.rule.RuleType.ROUTING);
		AbstractRule WIQ = new WIQ(yimei.jss.rule.RuleType.ROUTING);
		AbstractRule SRT = new SRT(yimei.jss.rule.RuleType.ROUTING);
		AbstractRule SBT = new SBT(yimei.jss.rule.RuleType.ROUTING);
//		AbstractRule[] routingRule = new AbstractRule[]{NIQ, WIQ, SRT, SBT};
		AbstractRule[] routingRule = new AbstractRule[]{WIQ};

		int numRuns = 1;
		String testScenario = "dynamic-job-shop"; //dynamic
		String testSetName = "missing-0.85-1.5"; //missing-0.85-4.0
		int numTrees = 2;
		int numObjectives = 1;
		String[] objectives = new String[]{"mean-flowtime"};

		//RuleTest ruleTest = new RuleTest(trainPath, ruleType, numRuns, testScenario, testSetName, numTrees);
		//modified by fzhang  24.5.2018  use multipleTreeRuleTest
		ManualRuleTest multipletreeruleTest = new ManualRuleTest(trainPath, sequencingRule, routingRule, numRuns, testScenario, testSetName, numTrees);

		for (int i = 0; i < numObjectives; i++) {
			multipletreeruleTest.addObjective(objectives[i]);
		}

		multipletreeruleTest.writeToCSV();
	}
}
