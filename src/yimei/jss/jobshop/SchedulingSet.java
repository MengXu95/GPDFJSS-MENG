package yimei.jss.jobshop;

import mengxu.complexsimulation.HeterogeneousSimulation;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import yimei.jss.rule.AbstractRule;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The set of scheduling problems. The set includes:
 *   1. A list of simulations.
 *   2. Number of replications for each simulation.
 *   3. The objective lower bound matrix: (i,j) - the lower bound of objective i in replication j.
 *
 * Created by YiMei on 28/09/16.
 */
public class SchedulingSet {

    private List<Simulation> simulations;
    private List<Integer> replications;
    private RealMatrix objectiveLowerBoundMtx;

    private List<RealMatrix> objectiveMultiCaseLowerBoundMtx;

    public SchedulingSet(List<Simulation> simulations,
                         List<Integer> replications,
                         List<Objective> objectives) {
        this.simulations = simulations;
        this.replications = replications;
        createObjectiveLowerBoundMatrix(objectives);
        
        //fzhang 2018.12.20 if we do not want use the lowerBounds, just comment this
        //lowerBoundsFromBenchmarkRule(objectives);
    }

    public List<Simulation> getSimulations() {
        return simulations;
    }

    public List<Integer> getReplications() {
        return replications;
    }

    public RealMatrix getObjectiveLowerBoundMtx() {
        return objectiveLowerBoundMtx;
    }

    public List<RealMatrix> getObjectiveMultiCaseLowerBoundMtx() {
        return objectiveMultiCaseLowerBoundMtx;
    }

    public double getObjectiveLowerBound(int row, int col) {
        return objectiveLowerBoundMtx.getEntry(row, col);
    }

    public void setReplications(List<Integer> replications) {
        this.replications = replications;
    }

    public void setRule(AbstractRule rule) {
        for (Simulation simulation : simulations) {
            simulation.setSequencingRule(rule);
        }
    }

    public void rotateSeed(List<Objective> objectives) {
        for (Simulation simulation : simulations) {
            simulation.rotateSeed();
        }

        //fzhang 2018.12.20  if we do not use lowerBounds, just comment it
        //lowerBoundsFromBenchmarkRule(objectives);
    }

    private void createObjectiveLowerBoundMatrix(List<Objective> objectives) {
        int rows = objectives.size();
        int cols = 0;
        for (int rep : replications) {
            cols += rep;
        }
        objectiveLowerBoundMtx = new Array2DRowRealMatrix(rows, cols);
    }

    private void createObjectiveMultiCaseLowerBoundMatrix(List<Objective> objectives, int numCase) {
        objectiveMultiCaseLowerBoundMtx = new ArrayList<>();
        for(int i=0; i<numCase; i++){
            int rows = objectives.size();
            int cols = 0;
            for (int rep : replications) {
                cols += rep;
            }
            objectiveMultiCaseLowerBoundMtx.add(new Array2DRowRealMatrix(rows, cols));
        }
    }

    public void lowerBoundsFromBenchmarkRule(List<Objective> objectives) {
        for (int i = 0; i < objectives.size(); i++) {
            Objective objective = objectives.get(i);
            AbstractRule benchmarkSeqRule = objective.benchmarkSequencingRule();
            AbstractRule benchmarkRoutingRule = objective.benchmarkRoutingRule();

            int col = 0;
            for (int j = 0; j < simulations.size(); j++) {
                Simulation simulation = simulations.get(j);
                simulation.setSequencingRule(benchmarkSeqRule);
                simulation.setRoutingRule(benchmarkRoutingRule);
                simulation.rerun(); //this will make sure benchmark rules affect everything

                double value = simulation.objectiveValue(objective);
                objectiveLowerBoundMtx.setEntry(i, col, value);
//                System.out.println("objective1LowerBound: "+ this.getObjectiveLowerBound(i, col));
                
                col ++;

                for (int k = 1; k < replications.get(j); k++) {
                    simulation.rerun();
                    value = simulation.objectiveValue(objective);
                    objectiveLowerBoundMtx.setEntry(i, col, value);
                    col ++;
                }
                simulation.reset();
            }

        }
    }

    //add by mengxu 2025.1.6
    public double lowerBoundsFromSetOfBenchmarkRule(List<Objective> objectives) {
        for (int i = 0; i < objectives.size(); i++) {
            Objective objective = objectives.get(i);
            ArrayList<AbstractRule> benchmarkSeqRules = objective.setOfBenchmarkSequencingRule();
//            AbstractRule benchmarkSeqRule = objective.benchmarkSequencingRule();
            AbstractRule benchmarkRoutingRule = objective.benchmarkRoutingRule();
            double[] benchmarkRuleFit = new double[benchmarkSeqRules.size()];

            for(int c=0; c<benchmarkSeqRules.size(); c++){
                AbstractRule benchmarkSeqRule = benchmarkSeqRules.get(c);
                int col = 0;
                for (int j = 0; j < simulations.size(); j++) {
                    Simulation simulation = simulations.get(j);
                    simulation.setSequencingRule(benchmarkSeqRule);
                    simulation.setRoutingRule(benchmarkRoutingRule);
                    simulation.rerun(); //this will make sure benchmark rules affect everything

                    double value = simulation.objectiveValue(objective);
                    objectiveLowerBoundMtx.setEntry(i, col, value);
//                System.out.println("objective1LowerBound: "+ this.getObjectiveLowerBound(i, col));

                    col ++;

                    for (int k = 1; k < replications.get(j); k++) {
                        simulation.rerun();
                        value = simulation.objectiveValue(objective);
                        objectiveLowerBoundMtx.setEntry(i, col, value);
                        col ++;
                    }
                    simulation.reset();
                }
                benchmarkRuleFit[c] = objectiveLowerBoundMtx.getEntry(i,0);
            }
            System.out.println("benchmarkRuleFit: ");
            for (double value : benchmarkRuleFit) {
                System.out.print(value + " ");
            }
            System.out.println();
            return Arrays.stream(benchmarkRuleFit).sum()/(benchmarkRuleFit.length);
        }
        return Double.MAX_VALUE;
    }

    //add by mengxu 2023.10.26
    public void multiCaselowerBoundsFromBenchmarkRule(List<Objective> objectives, int numCase) {
        createObjectiveMultiCaseLowerBoundMatrix(objectives,numCase);
        for (int i = 0; i < objectives.size(); i++) {
            Objective objective = objectives.get(i);
            AbstractRule benchmarkSeqRule = objective.benchmarkSequencingRule();
            AbstractRule benchmarkRoutingRule = objective.benchmarkRoutingRule();

            int col = 0;
            for (int j = 0; j < simulations.size(); j++) {
                Simulation simulation = simulations.get(j);
                simulation.setSequencingRule(benchmarkSeqRule);
                simulation.setRoutingRule(benchmarkRoutingRule);
                simulation.rerun(); //this will make sure benchmark rules affect everything

//                double value = simulation.objectiveValue(objective);
                double[] multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan
                for(int c=0; c<numCase; c++){
                    double value = multiCaseObjValue[c];
                    objectiveMultiCaseLowerBoundMtx.get(c).setEntry(i, col, value);
                }

//                System.out.println("objective1LowerBound: "+ this.getObjectiveLowerBound(i, col));

                col ++;

                for (int k = 1; k < replications.get(j); k++) {
                    simulation.rerun();
                    multiCaseObjValue = simulation.objectiveValueMultiCase(objectives.get(i),numCase); // this line: the value of makespan
                    for(int c=0; c<numCase; c++){
                        double value = multiCaseObjValue[c];
                        objectiveMultiCaseLowerBoundMtx.get(c).setEntry(i, col, value);
                    }
                    col ++;
                }
                simulation.reset();
            }

        }
    }

    public SchedulingSet surrogate(int numWorkCenters, int numJobsRecorded,
                                   int warmupJobs, List<Objective> objectives) {
        List<Simulation> surrogateSimulations = new ArrayList<>();
        List<Integer> surrogateReplications = new ArrayList<>();

        for (int i = 0; i < simulations.size(); i++) {
            surrogateSimulations.add(
                    simulations.get(i).surrogate(
                    numWorkCenters, numJobsRecorded, warmupJobs));
            surrogateReplications.add(1);
        }

        return new SchedulingSet(surrogateSimulations,
                surrogateReplications, objectives);
    }

    public SchedulingSet surrogateBusy(int numWorkCenters, int numJobsRecorded,
                                   int warmupJobs, List<Objective> objectives) {
        List<Simulation> surrogateSimulations = new ArrayList<>();
        List<Integer> surrogateReplications = new ArrayList<>();

        for (int i = 0; i < simulations.size(); i++) {
            surrogateSimulations.add(
                    simulations.get(i).surrogateBusy(
                            numWorkCenters, numJobsRecorded, warmupJobs));
            surrogateReplications.add(1);
        }

        return new SchedulingSet(surrogateSimulations,
                surrogateReplications, objectives);
    }

    public static SchedulingSet dynamicFullSet(long simSeed,
                                               double utilLevel,
                                               double dueDateFactor,
                                               List<Objective> objectives,
                                               int reps) {
        List<Simulation> simulations = new ArrayList<>();
        
        //original
      /*  simulations.add(
                DynamicSimulation.standardFull(simSeed, null, null, 10, 4000, 1000,
                        utilLevel, dueDateFactor));*/

        //modified by mengxu 2021.09.02 for heterogeneous simulation
        simulations.add(
                HeterogeneousSimulation.standardFull(simSeed, null, null, 10, 5000, 1000,
                        utilLevel, dueDateFactor));
        
//      //fzhang 2019.2.12 test should be also with 5000 jobs
//        simulations.add(
//                DynamicSimulation.standardFull(simSeed, null, null, 10, 5000, 1000,
//                        utilLevel, dueDateFactor));
        List<Integer> replications = new ArrayList<>();
        replications.add(reps);

        return new SchedulingSet(simulations, replications, objectives);
    }

    public static SchedulingSet dynamicMissingSet(long simSeed,
                                                  double utilLevel,
                                                  double dueDateFactor,
                                                  List<Objective> objectives,
                                                  int reps) {
        List<Simulation> simulations = new ArrayList<>();


        //modified by mengxu 2021.09.02 for heterogeneous simulation
//        simulations.add(
//                HeterogeneousSimulation.standardMissing(simSeed, null, null, 10, 5000, 1000,
//                        utilLevel, dueDateFactor));
        simulations.add(
                HeterogeneousSimulation.standardMissing(simSeed, null, null, 10, 5000, 1000,
                        utilLevel, dueDateFactor));
        
//        //fzhang 2019.2.12 test should be also with 5000 jobs
//        simulations.add(
//                DynamicSimulation.standardMissing(simSeed, null, null, 10, 5000, 1000,
//                        utilLevel, dueDateFactor));
        
        List<Integer> replications = new ArrayList<>();
        replications.add(reps);
        return new SchedulingSet(simulations, replications, objectives);
    }

    //modified by mengxu 2022.03.08
    public static SchedulingSet dynamicMissingSetDifferentRandomSeed(long simSeed,
                                                                     double utilLevel,
                                                                     double dueDateFactor,
                                                                     List<Objective> objectives,
                                                                     int reps,
                                                                     int numRandom) {
        List<Simulation> simulations = new ArrayList<>();
        //original
    /*    simulations.add(
                DynamicSimulation.standardMissing(simSeed, null, null, 10, 4000, 1000,
                        utilLevel, dueDateFactor));*/

        //modified by mengxu 2021.09.02 for heterogeneous simulation
        for(int i=0; i<numRandom; i++){
            simulations.add(
                    HeterogeneousSimulation.standardMissing(simSeed, null, null, 10, 5000, 1000,
                            utilLevel, dueDateFactor));
            simSeed = simSeed + 10000;
        }


//        //fzhang 2019.2.12 test should be also with 5000 jobs
//        simulations.add(
//                DynamicSimulation.standardMissing(simSeed, null, null, 10, 5000, 1000,
//                        utilLevel, dueDateFactor));

        List<Integer> replications = new ArrayList<>();
        for(int i=0; i<numRandom; i++) {
            replications.add(reps);
        }
        return new SchedulingSet(simulations, replications, objectives);
    }

    public static SchedulingSet generateSet(long simSeed,
                                            String scenario,
                                            String setName,
                                            List<Objective> objectives,
                                            int replications) {
        if (scenario.equals(Scenario.DYNAMIC_JOB_SHOP.getName())) {
            String[] parameters = setName.split("-");  //for example: missing-0.95-4.0      utilLevel = parameters[1] = 0.85
            double utilLevel = Double.valueOf(parameters[1]);
            double dueDateFactor = Double.valueOf(parameters[2]); //dueDateFactor = parameters[2] = 4

            if (parameters[0].equals("missing")) {
                return SchedulingSet.dynamicMissingSet(simSeed, utilLevel, dueDateFactor, objectives, replications);
            }
            else if (parameters[0].equals("full")) {
                return SchedulingSet.dynamicFullSet(simSeed, utilLevel, dueDateFactor, objectives, replications);
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }


    public static SchedulingSet generateSetDifferentRandomSeed(long simSeed,
                                                                String scenario,
                                                                String setName,
                                                                List<Objective> objectives,
                                                                int replications,
                                                               int numRandom) {
        if (scenario.equals(Scenario.DYNAMIC_JOB_SHOP.getName())) {
            String[] parameters = setName.split("-");  //for example: missing-0.95-4.0      utilLevel = parameters[1] = 0.85
            double utilLevel = Double.valueOf(parameters[1]);
            double dueDateFactor = Double.valueOf(parameters[2]); //dueDateFactor = parameters[2] = 4

            if (parameters[0].equals("missing")) {
                return SchedulingSet.dynamicMissingSetDifferentRandomSeed(simSeed, utilLevel, dueDateFactor, objectives, replications,numRandom);
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchedulingSet that = (SchedulingSet) o;

        if (!simulations.equals(that.simulations)) return false;
        if (!replications.equals(that.replications)) return false;
        return objectiveLowerBoundMtx.equals(that.objectiveLowerBoundMtx);
    }

    @Override
    public int hashCode() {
        int result = simulations.hashCode();
        result = 31 * result + replications.hashCode();
        result = 31 * result + objectiveLowerBoundMtx.hashCode();
        return result;
    }
}
