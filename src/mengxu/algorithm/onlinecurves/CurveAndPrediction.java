package mengxu.algorithm.onlinecurves;

import yimei.jss.jobshop.Objective;
import yimei.jss.jobshop.SchedulingSet;
import yimei.jss.ruleevaluation.MultipleRuleEvaluationModel;
import yimei.jss.simulation.DynamicSimulation;
import yimei.jss.simulation.Simulation;

import java.util.ArrayList;
import java.util.List;

public class CurveAndPrediction {

    private List<List<Integer>> x_Axis_List;
    private List<List<Double>> y_Axis_List;

    public CurveAndPrediction(){
        x_Axis_List = new ArrayList<>();
        y_Axis_List = new ArrayList<>();
    }

    public void updateCurveXY(MultipleRuleEvaluationModel evaluationModel){
        List<Simulation> simulationList = evaluationModel.getSchedulingSet().getSimulations();
        List<Objective> objectiveList = evaluationModel.getObjectives();


        for(int i=0; i< simulationList.size(); i++){
            Simulation simulation = simulationList.get(i);
            List<Integer> x_Axis;
            List<Double> y_Axis;
            if(x_Axis_List.size()-1>=i){
                x_Axis = x_Axis_List.get(i);
                y_Axis = y_Axis_List.get(i);
            }
            else{
                x_Axis = new ArrayList<>();
                y_Axis = new ArrayList<>();
            }
            int x_Axis_Size = x_Axis.size();

            if(simulation.getNumJobsArrived() == x_Axis.get(x_Axis_Size-1)){
                //no new job arrive
                y_Axis.set(simulation.getNumJobsArrived(), simulation.getSystemState().getNumOfOperationInSystem());
            }
            else{
                x_Axis.add(simulation.getNumJobsArrived());
                y_Axis.add(simulation.getSystemState().getNumOfOperationInSystem());
            }
        }
    }

    public void updateCurveXY(Simulation simulation, Objective objective){
        List<Integer> x_Axis;
        List<Double> y_Axis;
        if(x_Axis_List.size() == 1){
            x_Axis = x_Axis_List.get(0);
            y_Axis = y_Axis_List.get(0);
        }
        else{
            x_Axis = new ArrayList<>();
            y_Axis = new ArrayList<>();
        }
        int x_Axis_Size = x_Axis.size();

        if(simulation.getNumJobsArrived() == x_Axis.get(x_Axis_Size-1)){
            //no new job arrive
            y_Axis.set(simulation.getNumJobsArrived(), simulation.getSystemState().getNumOfOperationInSystem());
        }
        else{
            x_Axis.add(simulation.getNumJobsArrived());
            y_Axis.add(simulation.getSystemState().getNumOfOperationInSystem());
        }
    }

    public void updateCurveXY(SchedulingSet set, Objective objective){
        List<Simulation> simulationList = set.getSimulations();

        for(int i=0; i< simulationList.size(); i++){
            Simulation simulation = simulationList.get(i);
            List<Integer> x_Axis;
            List<Double> y_Axis;
            if(x_Axis_List.size()-1>=i){
                x_Axis = x_Axis_List.get(i);
                y_Axis = y_Axis_List.get(i);
            }
            else{
                x_Axis = new ArrayList<>();
                y_Axis = new ArrayList<>();
            }
            int x_Axis_Size = x_Axis.size();

            if(simulation.getNumJobsArrived() == x_Axis.get(x_Axis_Size-1)){
                //no new job arrive
                y_Axis.set(simulation.getNumJobsArrived(), simulation.getSystemState().getNumOfOperationInSystem());
            }
            else{
                x_Axis.add(simulation.getNumJobsArrived());
                y_Axis.add(simulation.getSystemState().getNumOfOperationInSystem());
            }
        }
    }

    public List<List<Integer>> getAllCurveX(){
        return x_Axis_List;
    }

    public List<List<Double>> getAllCurveY(){
        return y_Axis_List;
    }

}
