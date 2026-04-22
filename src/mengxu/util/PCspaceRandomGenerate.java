package mengxu.util;

import ec.EvolutionState;
import ec.Individual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PCspaceRandomGenerate {

    public int numSequencingDecisions;

    public int numRoutingDecisions;

    public int sampleSizeSequencing;

    public int sampleSizeRouting;

    public int numSequencingExplored;

    public int numRoutingExplored;

    public int decisionMin;

    public int decisionMax;
    public List<double[]> sequencingPCMap;
    public List<double[]> sequencingPCInCurrentGeneration;
    public List<List<Double>> sequencingPCFitness;

    public List<double[]> routingPCMap;
    public List<double[]> routingPCInCurrentGeneration;
    public List<List<Double>> routingPCFitness;

    public double sequencingMapCoverage;

    public double routingMapCoverage;

    //added by mengxu 2023.06.22
    public List<double[]> sequencingPCRegionForLocalSearch;

    public List<double[]> routingPCRegionForLocalSearch;


    public List<double[]> sequencingPCRegionForExplore;

    public List<double[]> routingPCRegionForExplore;

    public double probabilityForChange = 0.2; //add by mengxu 2023.07.06

    public boolean recordFitnessNormalisation; //added by mengxu 2023.07.11
    public boolean zScoreNormalisation;

    public PCspaceRandomGenerate(int numSequencingDecisions, int numRoutingDecisions, int sampleSize){
        this.numSequencingDecisions = numSequencingDecisions;
        this.numRoutingDecisions = numRoutingDecisions;
        this.sampleSizeSequencing = sampleSize;
        this.sampleSizeRouting = sampleSize;
        this.decisionMin = 1;
        this.decisionMax = 7;
        this.sequencingPCMap = new ArrayList<>();
        this.sequencingPCFitness = new ArrayList<>();
        this.routingPCMap = new ArrayList<>();
        this.routingPCFitness = new ArrayList<>();
        // added by mengxu 2023.06.22
        this.sequencingPCRegionForLocalSearch = new ArrayList<>();
        this.routingPCRegionForLocalSearch = new ArrayList<>();
        this.sequencingPCRegionForExplore = new ArrayList<>();
        this.routingPCRegionForExplore = new ArrayList<>();
        //added by mengxu 2023.06.28
        this.numSequencingExplored = 0;
        this.numRoutingExplored = 0;
        this.sequencingPCInCurrentGeneration = new ArrayList<>();
        this.routingPCInCurrentGeneration = new ArrayList<>();
        this.recordFitnessNormalisation = false; //added by mengxu 2023.07.11
        this.zScoreNormalisation = false;
    }

    public boolean isRecordFitnessNormalisation() {
        return recordFitnessNormalisation;
    }

    public void setRecordFitnessNormalisation(boolean recordFitnessNormalisation) {
        this.recordFitnessNormalisation = recordFitnessNormalisation;
    }

    public boolean iszScoreNormalisation() {
        return zScoreNormalisation;
    }

    public void setzScoreNormalisation(boolean zScoreNormalisation) {
        this.zScoreNormalisation = zScoreNormalisation;
    }

    public int getNumSequencingExplored() {
        this.numSequencingExplored = 0;
        for(int i=0; i<this.sequencingPCFitness.size(); i++){
            List<Double> fitList = this.sequencingPCFitness.get(i);
            if(fitList.size()>0){
                this.numSequencingExplored++;
            }
        }
        return this.numSequencingExplored;
    }

    public int getNumRoutingExplored() {
        this.numRoutingExplored = 0;
        for(int i=0; i<this.routingPCFitness.size(); i++){
            List<Double> fitList = this.routingPCFitness.get(i);
            if(fitList.size()>0){
                this.numRoutingExplored++;
            }
        }
        return this.numRoutingExplored;
    }

    //todo: need to implement the function/strategy of:
    //      1) explore the less frequently explore or unexplored region in the map of sequencing and routing, respectively
    //      2) find and precise explore (local search) the region that support good fitness


    //todo:
    public boolean localSearchAcceptSequencing(double[] indSequencingPC, double acceptDis){
        boolean accept = false;
        //step 0: find the good PC of good performance individuals based on the sequencing and routing map
        //step 1: calculate the distance between indPC and the good PC
        //step 2: only when the distance is within a threshold, we accept the ind, this is the local search
//        if(this.sequencingPCRegionForLocalSearch.size() == 0){
//            System.out.println("Local search guide sequencing PC can not be empty! Error here");
//        }
//        int key = inSequencingMap(indSequencingPC);
//        if(key == -1){
//            List<Double> fitnesses = new ArrayList<>();
//            this.sequencingPCMap.add(indSequencingPC);//update the sequencing map
//            this.sequencingPCFitness.add(fitnesses);
//            this.sampleSizeSequencing = this.sampleSizeSequencing + 1;
//        }

        for(int i=0; i<this.sequencingPCRegionForLocalSearch.size(); i++){
            double[] guidePC = this.sequencingPCRegionForLocalSearch.get(i);
            double dis = distance(guidePC,indSequencingPC);
            if(dis <= acceptDis){
                accept = true;
                return accept;
            }
        }
        return accept;
    }

    public boolean localSearchAcceptRouting(double[] indRoutingPC, double acceptDis){
        boolean accept = false;
        //step 0: find the good PC of good performance individuals based on the sequencing and routing map
        //step 1: calculate the distance between indPC and the good PC
        //step 2: only when the distance is within a threshold, we accept the ind, this is the local search
//        if(this.routingPCRegionForLocalSearch.size() == 0){
//            System.out.println("Local search guide routing PC can not be empty! Error here");
//        }
//        int key = inRoutingMap(indRoutingPC);
//        if(key == -1){
//            List<Double> fitnesses = new ArrayList<>();
//            this.routingPCMap.add(indRoutingPC);//update the sequencing map
//            this.routingPCFitness.add(fitnesses);
//            this.sampleSizeRouting = this.sampleSizeRouting + 1;
//        }
        for(int i=0; i<this.routingPCRegionForLocalSearch.size(); i++){
            double[] guidePC = this.routingPCRegionForLocalSearch.get(i);
            double dis = distance(guidePC,indRoutingPC);
            if(dis <= acceptDis){
                accept = true;
                return accept;
            }
        }
        return accept;
    }

    public double distance(double[] firstPC, double[] secondPC){
        double sum = 0;
        for(int i=0; i<firstPC.length; i++){
            sum += Math.pow((firstPC[i]-secondPC[i]),2);
        }
        double dis = Math.sqrt(sum);
        return dis;
    }


    public void updateSequencingPCsForLocalSearch(double frequencyLimit, double fitnessLimit, boolean clear){
        if(clear){//the clear must be true
            this.sequencingPCRegionForLocalSearch.clear();
        }
        for(int i=0; i<this.sequencingPCMap.size(); i++){
            List<Double> fitList = this.sequencingPCFitness.get(i);
            double median = calculateMedian(fitList);

            boolean satisfyFitnessLimit = false;

            //--------strategy 2---------------------
            if(median < fitnessLimit){
                satisfyFitnessLimit = true;
            }
            //---------------------------------------

//            //--------strategy 1---------------------
//            for(int j=0; j<fitList.size(); j++){
//                double fit = fitList.get(j);
//                if(fit < fitnessLimit){
//                    satisfyFitnessLimit = true;
//                }
//            }
//            //---------------------------------------

            if(fitList.size()>frequencyLimit && satisfyFitnessLimit){
                this.sequencingPCRegionForLocalSearch.add(this.sequencingPCMap.get(i));
            }
        }
    }

    public void updateRoutingPCsForLocalSearch(double frequencyLimit, double fitnessLimit, boolean clear){
        if(clear){
            this.routingPCRegionForLocalSearch.clear();
        }
        for(int i=0; i<this.routingPCMap.size(); i++){
            List<Double> fitList = this.routingPCFitness.get(i);
            boolean satisfyFitnessLimit = false;
            double median = calculateMedian(fitList);

            //--------strategy 2---------------------
            if(median < fitnessLimit){
                satisfyFitnessLimit = true;
            }
            //---------------------------------------

//            //--------strategy 1---------------------
//            for(int j=0; j<fitList.size(); j++){
//                double fit = fitList.get(j);
//                if(fit < fitnessLimit){
//                    satisfyFitnessLimit = true;
//                }
//            }
//            //---------------------------------------
            if(fitList.size()>frequencyLimit && satisfyFitnessLimit){
                this.routingPCRegionForLocalSearch.add(this.routingPCMap.get(i));
            }
        }
    }

    //todo:
//    public boolean explore(double[] indPC){
//        boolean accept = false;
//        //step 0: find the unexplored and want to explore PC based on the sequencing and routing map
//
//        //step 1: calculate the distance between indPC and the unexplored PC
//
//        //step 2: only when the distance is within a threshold, we accept the ind, this is the explore process
//        return accept;
//    }

    public boolean exploreAcceptSequencing(double[] indSequencingPC, double acceptDis){
        boolean accept = false;
        //step 0: find the good PC of good performance individuals based on the sequencing and routing map
        //step 1: calculate the distance between indPC and the good PC
        //step 2: only when the distance is within a threshold, we accept the ind, this is the local search
//        if(this.sequencingPCRegionForExplore.size() == 0){
//            System.out.println("Exploration guide sequencing PC can not be empty! Error here");
//        }
//        int key = inSequencingMap(indSequencingPC);
//        if(key == -1){
//            List<Double> fitnesses = new ArrayList<>();
//            this.sequencingPCMap.add(indSequencingPC);//update the sequencing map
//            this.sequencingPCFitness.add(fitnesses);
//            this.sampleSizeSequencing = this.sampleSizeSequencing + 1;
//        }

        for(int i=0; i<this.sequencingPCRegionForExplore.size(); i++){
            double[] guidePC = this.sequencingPCRegionForExplore.get(i);
            double dis = distance(guidePC,indSequencingPC);
            if(dis <= acceptDis){
                accept = true;
                this.sequencingPCRegionForExplore.remove(i);//added bymengxu 2023.06.24
                return accept;
            }
        }
        return accept;
    }

    public boolean exploreAcceptRouting(double[] indRoutingPC, double acceptDis){
        boolean accept = false;
        //step 0: find the good PC of good performance individuals based on the sequencing and routing map
        //step 1: calculate the distance between indPC and the good PC
        //step 2: only when the distance is within a threshold, we accept the ind, this is the local search
//        if(this.routingPCRegionForExplore.size() == 0){
//            System.out.println("Exploration guide routing PC can not be empty! Error here");
//        }
//        int key = inRoutingMap(indRoutingPC);
//        if(key == -1){
//            List<Double> fitnesses = new ArrayList<>();
//            this.routingPCMap.add(indRoutingPC);//update the sequencing map
//            this.routingPCFitness.add(fitnesses);
//            this.sampleSizeRouting = this.sampleSizeRouting + 1;
//        }

        for(int i=0; i<this.routingPCRegionForExplore.size(); i++){
            double[] guidePC = this.routingPCRegionForExplore.get(i);
            double dis = distance(guidePC,indRoutingPC);
            if(dis <= acceptDis){
                accept = true;
                this.routingPCRegionForExplore.remove(i);//added bymengxu 2023.06.24
                return accept;
            }
        }
        return accept;
    }

    //add by mengxu 2023.07.07
    public boolean exploreAcceptSequencingNonDuplicatedOffspringInMap(double[] indSequencingPC, double acceptDis){
        boolean accept = false;
        //step 0: find the good PC of good performance individuals based on the sequencing and routing map
        //step 1: calculate the distance between indPC and the good PC
        //step 2: only when the distance is within a threshold, we accept the ind, this is the local search
        int key = inSequencingMapNonExplored(indSequencingPC);
        if(key == -1){
            accept = true;
            return accept;
        }
        return accept;
    }

    public boolean exploreAcceptRoutingNonDuplicatedOffspringInMap(double[] indRoutingPC, double acceptDis){
        boolean accept = false;
        //step 0: find the good PC of good performance individuals based on the sequencing and routing map
        //step 1: calculate the distance between indPC and the good PC
        //step 2: only when the distance is within a threshold, we accept the ind, this is the local search
        int key = inRoutingMapNonExplored(indRoutingPC);
        if(key == -1){
            accept = true;
            return accept;
        }
        return accept;
    }

    //add by mengxu 2023.07.07
    public boolean exploreAcceptSequencingNonDuplicatedOffspringInCurrentGeneration(double[] indSequencingPC, double acceptDis){
        boolean accept = false;
        //step 0: find the good PC of good performance individuals based on the sequencing and routing map
        //step 1: calculate the distance between indPC and the good PC
        //step 2: only when the distance is within a threshold, we accept the ind, this is the local search
        int key = inSequencingMapNonExploredInCurrentGeneration(indSequencingPC);
        if(key == -1){
            accept = true;
            return accept;
        }
        return accept;
    }

    public boolean exploreAcceptRoutingNonDuplicatedOffspringInCurrentGeneration(double[] indRoutingPC, double acceptDis){
        boolean accept = false;
        //step 0: find the good PC of good performance individuals based on the sequencing and routing map
        //step 1: calculate the distance between indPC and the good PC
        //step 2: only when the distance is within a threshold, we accept the ind, this is the local search
        int key = inRoutingMapNonExploredInCurrentGeneration(indRoutingPC);
        if(key == -1){
            accept = true;
            return accept;
        }
        return accept;
    }


    public void clearSequencingPCInCurrentGeneration(){
        this.sequencingPCInCurrentGeneration.clear();
    }

    public void clearRoutingPCInCurrentGeneration(){
        this.routingPCInCurrentGeneration.clear();
    }

    public void updateSequencingPCInCurrentGeneration(double[] offspringSequencingPC){
        this.sequencingPCInCurrentGeneration.add(offspringSequencingPC);
    }

    public void updateRoutingPCInCurrentGeneration(double[] offspringRoutingPC){
        this.routingPCInCurrentGeneration.add(offspringRoutingPC);
    }

    public void updateSequencingPCsForExplore(int frequencyLimit, boolean clear){
        if(clear){
            this.sequencingPCRegionForExplore.clear();
        }
        for(int i=0; i<this.sequencingPCMap.size(); i++){
            List<Double> fitList = this.sequencingPCFitness.get(i);
            if(fitList.size() <= frequencyLimit){
                this.sequencingPCRegionForExplore.add(this.sequencingPCMap.get(i));
            }
        }
    }

    public void updateRoutingPCsForExplore(int frequencyLimit, boolean clear){
        if(clear){
            this.routingPCRegionForExplore.clear();
        }
        for(int i=0; i<this.routingPCMap.size(); i++){
            List<Double> fitList = this.routingPCFitness.get(i);
            if(fitList.size() <= frequencyLimit){
                this.routingPCRegionForExplore.add(this.routingPCMap.get(i));
            }
        }
    }

    public void generatePCMap(EvolutionState state){
        getSequencingPCMap(state);
        getRoutingPCMap(state);

    }

    private void getSequencingPCMap(EvolutionState state){
        int i=0;
        int trys = 0;
        int tryLimits = this.sampleSizeSequencing * 3;
        while(i<this.sampleSizeSequencing && trys<tryLimits){
            double[] sequencingPC = new double[this.numSequencingDecisions];
            for(int j=0; j<this.numSequencingDecisions; j++){
                double decision = (double)(state.random[0].nextInt(this.decisionMax) + 1);
                sequencingPC[j] = decision;
            }
            if(inSequencingMap(sequencingPC) == -1) {
                this.sequencingPCMap.add(sequencingPC);
                List<Double> fitList = new ArrayList<>();
                this.sequencingPCFitness.add(fitList);
                i++;
            }
            trys++;
        }
    }

    private void getRoutingPCMap(EvolutionState state){
        int i=0;
        int trys = 0;
        int tryLimits = this.sampleSizeRouting * 3;
        while(i<this.sampleSizeRouting && trys<tryLimits){
            double[] routingPC = new double[this.numRoutingDecisions];
            for(int j=0; j<this.numRoutingDecisions; j++){
                double decision = (double)(state.random[0].nextInt(this.decisionMax) + 1);
                routingPC[j] = decision;
            }
            if(inRoutingMap(routingPC) == -1){
                this.routingPCMap.add(routingPC);
                List<Double> fitList = new ArrayList<>();
                this.routingPCFitness.add(fitList);
                i++;
            }
            trys++;
        }
    }

    //add by mengxu 2023.07.06
    public void expandSequencingPCMap(EvolutionState state, int expandNumber){
        int i=0;
        int trys = 0;
        int tryLimits = expandNumber * 3;
        while(i<expandNumber && trys<tryLimits){
            double[] sequencingPC = new double[this.numSequencingDecisions];
            int rand_index = state.random[0].nextInt(this.sequencingPCMap.size());
            double[] rand_decision = this.sequencingPCMap.get(rand_index);

            for(int j=0; j<this.numSequencingDecisions; j++){
                double rand_pro = state.random[0].nextDouble();
                if(rand_pro < this.probabilityForChange){
                    double decision = (double)(state.random[0].nextInt(this.decisionMax) + 1);
                    sequencingPC[j] = decision;
                }
                else{
                    sequencingPC[j] = rand_decision[j];
                }
            }
            if(inSequencingMap(sequencingPC) == -1) {
                this.sequencingPCMap.add(sequencingPC);
                List<Double> fitList = new ArrayList<>();
                this.sequencingPCFitness.add(fitList);
                i++;
            }
            trys++;
        }
    }

    //add by mengxu 2023.07.06
    public void expandRoutingPCMap(EvolutionState state, int expandNumber){
        int i=0;
        int trys = 0;
        int tryLimits = expandNumber * 3;
        while(i<expandNumber && trys<tryLimits){
            double[] routingPC = new double[this.numRoutingDecisions];
            int rand_index = state.random[0].nextInt(this.routingPCMap.size());
            double[] rand_decision = this.routingPCMap.get(rand_index);

            for(int j=0; j<this.numRoutingDecisions; j++){
                double rand_pro = state.random[0].nextDouble();
                if(rand_pro < this.probabilityForChange){
                    double decision = (double)(state.random[0].nextInt(this.decisionMax) + 1);
                    routingPC[j] = decision;
                }
                else{
                    routingPC[j] = rand_decision[j];
                }
            }
            if(inRoutingMap(routingPC) == -1) {
                this.routingPCMap.add(routingPC);
                List<Double> fitList = new ArrayList<>();
                this.routingPCFitness.add(fitList);
                i++;
            }
            trys++;
        }
    }

    public void updateSequencingMap(double[][] popSequencingPCMap, double[] fit){
        double numInMap = 0;
        double numNotInMap = 0;
        for(int i=0; i<popSequencingPCMap.length; i++){
            double[] decisionB = popSequencingPCMap[i];
            int key = inSequencingMap(decisionB);
            if(key != -1){
                numInMap = numInMap + 1;
                this.sequencingPCFitness.get(key).add(fit[i]);
            }
            else{
                numNotInMap = numNotInMap + 1;
                List<Double> fitnesses = new ArrayList<>();
                fitnesses.add(fit[i]);
                this.sequencingPCMap.add(decisionB);//update the sequencing map
                this.sequencingPCFitness.add(fitnesses);
                this.sampleSizeSequencing = this.sampleSizeSequencing + 1;
            }
        }
        this.sequencingMapCoverage = numInMap/((double)this.sampleSizeSequencing);
    }

    public void updateSequencingMapWithNormalisedFitness(double[][] popSequencingPCMap, double[] fit, double lowBoundFit, double upBoundFit){
        double numInMap = 0;
        double numNotInMap = 0;
        for(int i=0; i<popSequencingPCMap.length; i++){
            double[] decisionB = popSequencingPCMap[i];
            int key = inSequencingMap(decisionB);
            if(key != -1){
                numInMap = numInMap + 1;
                double normalisedFit = (fit[i] - lowBoundFit)/(upBoundFit - lowBoundFit);
                this.sequencingPCFitness.get(key).add(normalisedFit);
            }
            else{
                numNotInMap = numNotInMap + 1;
                List<Double> fitnesses = new ArrayList<>();
                double normalisedFit = (fit[i] - lowBoundFit)/(upBoundFit - lowBoundFit);
                fitnesses.add(normalisedFit);
                this.sequencingPCMap.add(decisionB);//update the sequencing map
                this.sequencingPCFitness.add(fitnesses);
                this.sampleSizeSequencing = this.sampleSizeSequencing + 1;
            }
        }
        this.sequencingMapCoverage = numInMap/((double)this.sampleSizeSequencing);
    }

    public int inSequencingMap(double[] sequencingDecision){
        int inMap = -1;
        for(int i=0; i<this.sequencingPCMap.size(); i++){
            double[] decisionA = this.sequencingPCMap.get(i);
            if(equal(decisionA, sequencingDecision)){
                inMap = i;
                return inMap;
            }
        }
        return inMap;
    }

    //add by mengxu 2023.07.07
    public int inSequencingMapNonExplored(double[] sequencingDecision){
        int inMap = -1;
        for(int i=0; i<this.sequencingPCMap.size(); i++){
            List<Double> fitList = this.sequencingPCFitness.get(i);
            if(!fitList.isEmpty()){
                double[] decisionA = this.sequencingPCMap.get(i);
                if(equal(decisionA, sequencingDecision)){
                    inMap = i;
                    return inMap;
                }
            }
        }
        return inMap;
    }

    public int inRoutingMapNonExplored(double[] routingDecision){
        int inMap = -1;
        for(int i=0; i<this.routingPCMap.size(); i++){
            List<Double> fitList = this.routingPCFitness.get(i);
            if(!fitList.isEmpty()){
                double[] decisionA = this.routingPCMap.get(i);
                if(equal(decisionA, routingDecision)){
                    inMap = i;
                    return inMap;
                }
            }
        }
        return inMap;
    }

    public int inSequencingMapNonExploredInCurrentGeneration(double[] sequencingDecision){
        int inMap = -1;
        for(int i=0; i<this.sequencingPCInCurrentGeneration.size(); i++){
            double[] decisionA = this.sequencingPCInCurrentGeneration.get(i);
            if(equal(decisionA, sequencingDecision)){
                inMap = i;
                return inMap;
            }
        }
        return inMap;
    }

    public int inRoutingMapNonExploredInCurrentGeneration(double[] routingDecision){
        int inMap = -1;
        for(int i=0; i<this.routingPCInCurrentGeneration.size(); i++){
            double[] decisionA = this.routingPCInCurrentGeneration.get(i);
            if(equal(decisionA, routingDecision)){
                inMap = i;
                return inMap;
            }
        }
        return inMap;
    }

    public void updateRoutingMap(double[][] popRoutingPCMap, double[] fit){
        double numInMap = 0;
        double numNotInMap = 0;
        for(int i=0; i<popRoutingPCMap.length; i++){
            double[] decisionB = popRoutingPCMap[i];
            int key = inRoutingMap(decisionB);
            if(key != -1){
                numInMap = numInMap + 1;
                this.routingPCFitness.get(key).add(fit[i]);
            }
            else{
                numNotInMap = numNotInMap + 1;
                List<Double> fitnesses = new ArrayList<>();
                fitnesses.add(fit[i]);
                this.routingPCMap.add(decisionB);//update the sequencing map
                this.routingPCFitness.add(fitnesses);
                this.sampleSizeRouting = this.sampleSizeRouting + 1;
            }
        }
        this.routingMapCoverage = numInMap/((double)this.sampleSizeRouting);
    }

    public void updateRoutingMapWithNormalisedFitness(double[][] popRoutingPCMap, double[] fit, double lowBoundFit, double upBoundFit){
        double numInMap = 0;
        double numNotInMap = 0;
        for(int i=0; i<popRoutingPCMap.length; i++){
            double[] decisionB = popRoutingPCMap[i];
            int key = inRoutingMap(decisionB);
            if(key != -1){
                numInMap = numInMap + 1;
                double normalisedFit = (fit[i] - lowBoundFit)/(upBoundFit - lowBoundFit);
                this.routingPCFitness.get(key).add(normalisedFit);
            }
            else{
                numNotInMap = numNotInMap + 1;
                List<Double> fitnesses = new ArrayList<>();
                double normalisedFit = (fit[i] - lowBoundFit)/(upBoundFit - lowBoundFit);
                fitnesses.add(normalisedFit);
                this.routingPCMap.add(decisionB);//update the sequencing map
                this.routingPCFitness.add(fitnesses);
                this.sampleSizeRouting = this.sampleSizeRouting + 1;
            }
        }
        this.routingMapCoverage = numInMap/((double)this.sampleSizeRouting);
    }

    public int inRoutingMap(double[] routingDecision){
        int inMap = -1;
        for(int i=0; i<this.routingPCMap.size(); i++){
            double[] decisionA = this.routingPCMap.get(i);
            if(equal(decisionA, routingDecision)){
                inMap = i;
                return inMap;
            }
        }
        return inMap;
    }

    public boolean equal(double[] decisionA, double[] decisionB){
        boolean equal = true;
        for(int i=0; i<decisionA.length; i++){
            if(decisionA[i] != decisionB[i]){
//                System.out.println("Not equal test here!");
                return false;
            }
        }
        return equal;
    }

    //added by mengxu 2023.06.22, print the Map
    public void printSequencingPCMap(){
        System.out.println();
        for(int i=0; i<this.sequencingPCMap.size(); i++){
            List<Double> fitList = this.sequencingPCFitness.get(i);
            if(fitList.size()>0){
                double[] decision = this.sequencingPCMap.get(i);
                System.out.print("{");
                printDecision(decision);
                System.out.print(": ");
                printFitList(fitList);
                System.out.println("}");
            }
            else{
                double[] decision = this.sequencingPCMap.get(i);
                System.out.print("{");
                printDecision(decision);
                System.out.println("}");
            }
        }
    }

    public double calculateSequencingFrequencyForLocalSearch(boolean usingEpsilon){
        if(usingEpsilon){
            double maxFrequency = 0;
            List<Double> allSequencingFrequencies = new ArrayList<>();
            for(int i=0; i<this.sequencingPCFitness.size(); i++){
                List<Double> fitList = this.sequencingPCFitness.get(i);
                if(fitList.size() > 0){
                    allSequencingFrequencies.add((double)fitList.size());
                    if((double)fitList.size() > maxFrequency){
                        maxFrequency = (double)fitList.size();
                    }
                }
            }
            double epsilon = calculateEpsilon(allSequencingFrequencies);
            return maxFrequency - epsilon;
        }
        else{
            //using median
            double maxFrequency = 0;
            List<Double> allSequencingFrequencies = new ArrayList<>();
            for(int i=0; i<this.sequencingPCFitness.size(); i++){
                List<Double> fitList = this.sequencingPCFitness.get(i);
                if(fitList.size() > 0){
                    allSequencingFrequencies.add((double)fitList.size());
                    if((double)fitList.size() > maxFrequency){
                        maxFrequency = (double)fitList.size();
                    }
                }
            }
            double median = calculateMedian(allSequencingFrequencies);
            return median;
        }
    }

    public double calculateRoutingFrequencyForLocalSearch(boolean usingEpsilon){
        if(usingEpsilon){
            double maxFrequency = 0;
            List<Double> allRoutingFrequencies = new ArrayList<>();
            for(int i=0; i<this.routingPCFitness.size(); i++){
                List<Double> fitList = this.routingPCFitness.get(i);
                if(fitList.size() > 0){
                    allRoutingFrequencies.add((double)fitList.size());
                    if((double)fitList.size() > maxFrequency){
                        maxFrequency = (double)fitList.size();
                    }
                }
            }
            double epsilon = calculateEpsilon(allRoutingFrequencies);
            return maxFrequency - epsilon;
        }
        else{
            double maxFrequency = 0;
            List<Double> allRoutingFrequencies = new ArrayList<>();
            for(int i=0; i<this.routingPCFitness.size(); i++){
                List<Double> fitList = this.routingPCFitness.get(i);
                if(fitList.size() > 0){
                    allRoutingFrequencies.add((double)fitList.size());
                    if((double)fitList.size() > maxFrequency){
                        maxFrequency = (double)fitList.size();
                    }
                }
            }
            double median = calculateMedian(allRoutingFrequencies);
            return median;
        }
    }

    public double calculateEpsilon(List<Double> allValues){
        allValues.sort(Double::compareTo);

        double median;
        if(allValues.size() % 2 == 0){
            int indexMedian = allValues.size() / 2;
            median = (allValues.get(indexMedian-1) + allValues.get(indexMedian))/2;
        }
        else{
            int indexMedian = allValues.size() / 2;
            median = allValues.get(indexMedian);
        }
        for(int ref2=0; ref2<allValues.size(); ref2++){
            allValues.set(ref2, Math.abs(allValues.get(ref2) - median));
        }
        allValues.sort(Double::compareTo);
        if(allValues.size() % 2 == 0){
            int indexMedian = allValues.size() / 2;
            median = (allValues.get(indexMedian-1) + allValues.get(indexMedian))/2;
        }
        else{
            int indexMedian = allValues.size() / 2;
            median = allValues.get(indexMedian);
        }
        return median;
    }

    public double calculateMedian(List<Double> allValues){
        allValues.sort(Double::compareTo);

        double median;
        if(allValues.size() % 2 == 0){
            int indexMedian = allValues.size() / 2;
            median = (allValues.get(indexMedian-1) + allValues.get(indexMedian))/2;
        }
        else{
            int indexMedian = allValues.size() / 2;
            median = allValues.get(indexMedian);
        }
        return median;
    }

    //added by mengxu 2023.06.22, print the Map
    public void printRoutingPCMap(){
        System.out.println();
        for(int i=0; i<this.routingPCMap.size(); i++){
            List<Double> fitList = this.routingPCFitness.get(i);
            if(fitList.size()>0){
                double[] decision = this.routingPCMap.get(i);
                System.out.print("{");
                printDecision(decision);
                System.out.print(": ");
                printFitList(fitList);
                System.out.println("}");
            }
            else{
                double[] decision = this.routingPCMap.get(i);
                System.out.print("{");
                printDecision(decision);
                System.out.println("}");
            }
        }
    }

    public void printDecision(double[] decision){
        System.out.print("[");
        for(int i=0; i<decision.length-1; i++){
            System.out.print(decision[i] + ", ");
        }
        System.out.print(decision[decision.length-1] + "]");
    }

    public void printFitList(List<Double> fitList){
        System.out.print("[");
        for(int i=0; i<fitList.size()-1; i++){
            System.out.print(fitList.get(i) + ", ");
        }
        System.out.print(fitList.get(fitList.size()-1) + "]");
    }
}
