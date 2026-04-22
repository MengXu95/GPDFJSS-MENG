package mengxu.algorithm.onlinecurves;

import ec.multiobjective.MultiObjectiveFitness;
import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.rule.AbstractRule;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.rule.operation.weighted.WSPT;
import yimei.jss.rule.workcenter.basic.WIQ;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;

import java.util.ArrayList;
import java.util.List;

import static yimei.jss.jobshop.Objective.*;

public class getCurveTest {

    public static void runDynamicSimulation(Objective o, double utilLevel,
                                            AbstractRule sequencingRule, AbstractRule routingRule) {
        MultiObjectiveFitness fitness = new MultiObjectiveFitness();
        fitness.objectives = new double[1];
        fitness.maxObjective = new double[1];
        fitness.maxObjective[0] = 1.0;
        fitness.minObjective = new double[1];
        fitness.maximize = new boolean[1];

        List<Objective> objectives = new ArrayList<>();
        objectives.add(o);
        int seed = 11;
        SchedulingSet set = createSchedulingSet(seed,o,utilLevel);

        int numRuns = 1;
        double[] results = new double[numRuns];
        for (int i = 0; i < numRuns; ++i) {
            sequencingRule.calcFitness(fitness, null, set, routingRule, objectives);
            results[i] = fitness.fitness();
//            set.getSimulations().get(0).writeCurvesToFile(o,utilLevel,i);
            set.rotateSeed(objectives);
        }

        String resultString = "";
        for (int i = 0; i < numRuns; ++i) {
            resultString += results[i] +",";
        }
        System.out.println(resultString.substring(0,resultString.length()-1));

        //System.out.println("Fitness averaged across "+numRuns+ " runs: "+(fitnessSum/numRuns));
        System.out.println();
    }

    public static SchedulingSet createSchedulingSet(int seed, Objective objective, double utilLevel) {
        List<Simulation> trainSimulations = new ArrayList<>();
        List<Integer> replications = new ArrayList<>();
        List<Objective> objectives = new ArrayList<>();
        objectives.add(objective);

        int numMachines = 10;
        // Number of jobs
        int numJobs = 5000;
        // Number of warmup jobs
        int warmupJobs = 1000;
        // Min number of operations
        int minNumOperations = 1;
        // Max number of operations
        int maxNumOperations = numMachines;
        // Due date factor
        double dueDateFactor = 1.5;
        // Number of replications
        int rep = 1;
        //Seed
        Simulation simulation = new DynamicSimulation(seed,
                null, null, numMachines, numJobs, warmupJobs,
                minNumOperations, maxNumOperations,
                utilLevel, dueDateFactor, false);

        trainSimulations.add(simulation);
        replications.add(new Integer(rep));

        return new SchedulingSet(trainSimulations, replications, objectives);
    }

    public static void main(String[] args){
        AbstractRule sequencingRule = new WSPT(RuleType.SEQUENCING);
        AbstractRule routingRule = new WIQ(RuleType.ROUTING);

//        GPRule sequencingRule = GPRule.readFromLispExpression(RuleType.SEQUENCING, "(Max (+ WKR (Max (+ (Min (/ NOR (* W PT)) (- (+ NIQ NOR) (+ WIQ PT))) (+ (+ W (Max NOR PT)) (/ (Min MWT NPT) (- NIQ OWT)))) (+ (+ (Max (Min WIQ WKR) (Max NPT WIQ)) (Min (Max WKR NPT) (* OWT PT))) (- (- (/ MWT NIQ) (+ WKR WIQ)) (- (Max NOR MWT) (* NIQ PT)))))) (/ (Max NPT TIS) (+ (* (/ (/ (* W WIQ) (Max NOR MWT)) OWT) (* (* (+ OWT W) (Max WIQ PT)) (/ (+ OWT MWT) (* PT TIS)))) (- (Max (/ (/ OWT WKR) (Max NOR NIQ)) (* (- TIS NOR) (/ NIQ W))) (* (Max (Min NIQ WIQ) (* W WKR)) MWT)))))");
//        GPRule routingRule = GPRule.readFromLispExpression(RuleType.ROUTING, "(+ (+ (Min (+ NIQ WIQ) (Max NIQ WKR)) (/ (Min (- W WKR) (+ (- (/ PT WKR) (Min TIS OWT)) (/ (Max WIQ WKR) (* NIQ PT)))) (- (* (* MWT TIS) (- MWT OWT)) (Max NOR (Min NIQ TIS))))) (- (- WKR MWT) (- (Min (Min W MWT) (+ NIQ NPT)) NOR)))");
//        Objective[] objectives = new Objective[]{MEAN_FLOWTIME, MAX_FLOWTIME, MEAN_WEIGHTED_FLOWTIME};
//        Double[] utilLevels = new Double[]{0.85, 0.95};

        Objective[] objectives = new Objective[]{MEAN_FLOWTIME};
        Double[] utilLevels = new Double[]{0.85, 0.95};

        for (int i = 0; i < 2; ++i) {
            double utilLevel = utilLevels[i];
            for (int j = 0; j < 1; ++j) {
                Objective o = objectives[j];
                System.out.println("Objective: "+o.toString()+", Utilisation level: "+utilLevel);
                System.out.println("Sequencing rule: "+sequencingRule.getName());
                System.out.println("Routing rule: "+routingRule.getName());
                runDynamicSimulation(o, utilLevel, sequencingRule, routingRule);
            }
        }
    }
}
