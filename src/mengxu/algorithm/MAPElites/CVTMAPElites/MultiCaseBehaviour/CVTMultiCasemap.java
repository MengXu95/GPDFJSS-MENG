package mengxu.algorithm.MAPElites.CVTMAPElites.MultiCaseBehaviour;

import ec.EvolutionState;
import ec.Individual;
import ec.gp.GPIndividual;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans.Cluster;
import mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans.DistanceCompute;
import mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans.KMeansRun;
import mengxu.algorithm.MAPElites.CVTMAPElites.Kmeans.Point;
import yimei.jss.niching.PhenoCharacterisation;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CVTMultiCasemap {

    public int num_centroids;
    public int num_centroids_upper_bound;

    public int dimensionality;

    public int max_size;

    public ArrayList<Integer> num_accept_each_cell;

    public ArrayList<ArrayList<Individual>> inds_each_cell;

    public ArrayList<ArrayList<Double>> fitnesses_each_cell;

    public ArrayList<ArrayList<double[]>> MultiCase_each_cell;

    public ArrayList<ArrayList<Integer>> indsFromGen_each_cell;

    public boolean recordFitnessNormalisation;

    public boolean zScoreNormalisation;
    public Set<Cluster> CVTMap;

    private ArrayList<double[]> initialRandomMultiCase;
    ArrayList<Individual> initialRandomInds;
    ArrayList<Double> initialRandomFits;

    ArrayList<Integer> initialRandomIndsFromGen;

    double penalty;

    double max_caseFit=1;
    double min_caseFit=0;

    public String measurement_dis; // if false, means using Euclidean distance

    public CVTMultiCasemap(int num_centroids, int dimensionality, int max_size, double penalty, String measurement_dis){
        this.num_centroids = num_centroids;
        this.num_centroids_upper_bound = num_centroids;
        this.dimensionality = dimensionality;
        this.max_size = max_size;
        this.recordFitnessNormalisation = false;
        this.zScoreNormalisation = false;
        this.CVTMap = null;
        this.penalty = penalty;
        this.measurement_dis = measurement_dis;
        //an empty map
        this.initialRandomMultiCase = new ArrayList<>();
        this.initialRandomInds = new ArrayList<>();
        this.initialRandomFits = new ArrayList<>();
        this.initialRandomIndsFromGen = new ArrayList<>();
        this.num_accept_each_cell = new ArrayList<>();
        this.inds_each_cell = new ArrayList<>();
        this.fitnesses_each_cell = new ArrayList<>();
        this.MultiCase_each_cell = new ArrayList<>();
        this.indsFromGen_each_cell = new ArrayList<>();
        for(int i=0; i<this.num_centroids; i++){
            this.num_accept_each_cell.add(1);
            ArrayList<Individual> inds = new ArrayList<>();
            this.inds_each_cell.add(inds);
            ArrayList<Double> fitnesses = new ArrayList<>();
            this.fitnesses_each_cell.add(fitnesses);
            ArrayList<double[]> PCs = new ArrayList<>();
            this.MultiCase_each_cell.add(PCs);
            ArrayList<Integer> indsFromGen = new ArrayList<>();
            this.indsFromGen_each_cell.add(indsFromGen);
        }
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


    public void initialiseCVTMultiCaseMapDirectlyBasedOnRandomMultiCase(EvolutionState state){
        getRandomMultiCaseMap(state);
    }

//    public void initialiseCVTMultiCaseMapDirectlyBasedOnRandomMultiCaseBasedOnRandomGenotype(EvolutionState state, int number){
//        getRandomMultiCaseMapBasedOnRandomGenotype(state, number);
//    }

    public void poorCellsClear(double threshold){
        // 2023.08.11
        for(int i=0; i<this.fitnesses_each_cell.size(); i++){
            ArrayList<Double> fitEachCell = this.fitnesses_each_cell.get(i);
            for(int j=0; j<fitEachCell.size(); j++){
                if(this.fitnesses_each_cell.get(i).get(j) > threshold){ //todo: for this threshold, do we also need *penalty???
                    this.fitnesses_each_cell.get(i).remove(j);
                    this.indsFromGen_each_cell.get(i).remove(j);
                    this.inds_each_cell.get(i).remove(j);
                    this.MultiCase_each_cell.get(i).remove(j);
                    j--;
                }
            }
//            if(fitEachCell.size()==0){//todo: why use this???
//                this.fitnesses_each_cell.remove(i);
//                this.indsFromGen_each_cell.remove(i);
//                this.inds_each_cell.remove(i);
//                this.MultiCase_each_cell.remove(i);
//                this.num_accept_each_cell.remove(i);
//                i--;
//            }
        }

        //clear randomPCMAP
        for(int i=0; i<this.initialRandomFits.size(); i++){
            double fit = initialRandomFits.get(i);
//            if(fit >= Double.MAX_VALUE || fit >= Double.POSITIVE_INFINITY){
            if(fit > threshold){ //todo: for this threshold, do we also need *penalty???
                this.initialRandomFits.remove(i);
                this.initialRandomIndsFromGen.remove(i);
                this.initialRandomMultiCase.remove(i);
                this.initialRandomInds.remove(i);
                i--;
            }
        }

        this.num_centroids = this.fitnesses_each_cell.size();
    }

    private void getRandomMultiCaseMap(EvolutionState state){
        int i=0;
        int trys = 0;
        int tryLimits = this.num_centroids * 3;
//        ArrayList<double[]> randomPCs = new ArrayList<>();
//        ArrayList<Individual> randomInds = new ArrayList<>();
//        ArrayList<Double> randomFits = new ArrayList<>();
        while(i<this.num_centroids && trys<tryLimits){
            double[] randomPC = new double[this.dimensionality];
            for(int j=0; j<this.dimensionality; j++){
                double decision = (double)(state.random[0].nextDouble() + 1); //generate a double between 0 and 1
                randomPC[j] = decision;
            }
            if(inRandomPCsMap(randomPC) == -1 && inCellPCsMap(randomPC) == -1) {
                this.initialRandomMultiCase.add(randomPC);
                this.initialRandomInds.add(null);
                this.initialRandomFits.add(Double.MAX_VALUE);
                this.initialRandomIndsFromGen.add(-1);
                i++;
            }
            trys++;
        }
        //use randomPCs to generate a CVT map
        KMeansRun kRun =new KMeansRun(this.num_centroids, this.initialRandomMultiCase, this.initialRandomInds, this.initialRandomFits, this.initialRandomIndsFromGen, this.measurement_dis);

        Set<Cluster> clusterSet = kRun.run(state);
        this.CVTMap = clusterSet;
//        System.out.println("单次迭代运行次数："+kRun.getIterTimes());
//        for (Cluster cluster : clusterSet) {
//            System.out.println(cluster);
//        }
    }

//    private void getRandomMultiCaseMapBasedOnRandomGenotype(EvolutionState state, int number){
//        int i=0;
//        int trys = 0;
//        int tryLimits = number * 3;
//
//        while(i<number && trys<tryLimits){
//            Individual individual = state.population.subpops[0].species.newIndividual(state, 0);
//            double[] PC = calculatePC(state, individual);
//
//            if(inRandomPCsMap(PC) == -1 && inCellPCsMap(PC) == -1) {
//                this.initialRandomMultiCase.add(PC);
//                this.initialRandomInds.add(null);
//                this.initialRandomFits.add(Double.MAX_VALUE);
//                this.initialRandomIndsFromGen.add(-1);
//                i++;
//            }
//            trys++;
//        }
//        //use randomPCs to generate a CVT map
//        KMeansRun kRun =new KMeansRun(this.num_centroids, this.initialRandomMultiCase, this.initialRandomInds, this.initialRandomFits, this.initialRandomIndsFromGen);
//
//        Set<Cluster> clusterSet = kRun.run(this.use_weight_binary_dis);
//        this.CVTMap = clusterSet;
//
//    }

    public double[] calculatePC(EvolutionState state,Individual individual){
        PhenoCharacterisation[] pc = ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).phenoCharacterisation;
        RuleType[] ruleTypes = {RuleType.SEQUENCING, RuleType.ROUTING}; //ruleType is an array
        int[] charListSeq;
        int[] charListRout;
        int treeID = 0;
        PhenoCharacterisation phenoCharacterisation = pc[treeID];//fzhang 2018.10.02  define two phenotype characteristic---phenoCharacterisation
        RuleType ruleType = ruleTypes[treeID];  //ruleType is a rule type---ruleType[0] = SEQUENCING  ruleType[1] = ROUTING
        charListSeq = phenoCharacterisation.characterise(  //characterise: calculate the distance
                new GPRule(ruleType, ((GPIndividual) individual).trees[treeID]));
        treeID = 1;
        phenoCharacterisation = pc[treeID];
        ruleType = ruleTypes[treeID];
        charListRout = phenoCharacterisation.characterise(  //characterise: calculate the distance
                new GPRule(ruleType, ((GPIndividual) individual).trees[treeID]));

        double[] charListSeqRoutDouble = new double[charListSeq.length+charListRout.length];

        int i=0;
        for(i=0;i<charListSeq.length;i++){
            charListSeqRoutDouble[i] = (double)charListSeq[i];
            charListSeqRoutDouble[i+charListSeq.length] = (double)charListRout[i];
        }
        return charListSeqRoutDouble;
    }

    public void updateCVTMap(EvolutionState state, Individual[] individuals,
                             double[][] indsCharListsMultiTree,
                             double[] fitnessesForModel,
                             double threshold){ //todo: need double check 2023.09.25
//        this.poorCellsClear(threshold); //add by mengxu 2023.08.11
        int clearGap = ((GPRuleEvolutionStateCVTMultiCaseMAPElites)state).clearGap;
        if(state.generation>0 && state.generation%clearGap==0){
            this.poorCellsClear(threshold); //add by mengxu 2023.08.11
        }

        this.initialRandomMultiCase.clear();
        this.initialRandomInds.clear();
        this.initialRandomFits.clear();
        this.initialRandomIndsFromGen.clear();

        for(int i=0; i<this.fitnesses_each_cell.size(); i++){
            for(int j=0; j<this.fitnesses_each_cell.get(i).size(); j++){
                this.initialRandomFits.add(this.fitnesses_each_cell.get(i).get(j));
                this.initialRandomMultiCase.add(this.MultiCase_each_cell.get(i).get(j).clone());
                this.initialRandomInds.add((Individual)this.inds_each_cell.get(i).get(j).clone());
                this.initialRandomIndsFromGen.add(this.indsFromGen_each_cell.get(i).get(j));
            }
        }

        for(int i=0; i<indsCharListsMultiTree.length; i++){
            boolean badrun = false;
            if(fitnessesForModel[i] >= Double.MAX_VALUE || fitnessesForModel[i] >= Double.POSITIVE_INFINITY){
                badrun = true;
            }
            if(inRandomPCsMap(indsCharListsMultiTree[i]) < 0 && !badrun) {
//            if(inRandomPCsMap(indsCharListsMultiTree[i]) == -1 && inCellPCsMap(indsCharListsMultiTree[i]) == -1 && !badrun) {
//                double[] indsCharListsMultiTreeClone = new double[indsCharListsMultiTree[i].length];
//                for(int c=0; c<indsCharListsMultiTreeClone.length; c++){
//                    indsCharListsMultiTreeClone[c] = indsCharListsMultiTree[i][c];
//                }
//                this.initialRandomMultiCase.add(indsCharListsMultiTreeClone);
                this.initialRandomMultiCase.add(indsCharListsMultiTree[i].clone());
                this.initialRandomInds.add((Individual) individuals[i].clone());
                this.initialRandomFits.add(fitnessesForModel[i]);
                this.initialRandomIndsFromGen.add(state.generation);
            }
        }
        //add by mengxu 2023.08.14
        if(this.initialRandomMultiCase.size() >= this.num_centroids_upper_bound){
            this.num_centroids = this.num_centroids_upper_bound;
        }
        else{
            this.num_centroids = this.initialRandomMultiCase.size();
        }
        //use randomPCs to generate a CVT map
        KMeansRun kRun =new KMeansRun(this.num_centroids, this.initialRandomMultiCase, this.initialRandomInds, this.initialRandomFits, this.initialRandomIndsFromGen, this.measurement_dis);

        Set<Cluster> clusterSet = kRun.run(state);
        this.CVTMap = clusterSet;

        System.out.println("clusterSet size: " + clusterSet.size());

        //add by mengxu 2023.09.28
        this.reMatchingAfterKmeans();


        //update the stored individuals and relative information
//        for(int i=0; i<clusterSet.size(); i++){
//            this.num_accept_each_cell.add(1);
//            ArrayList<Individual> inds = new ArrayList<>();
//            this.inds_each_cell.add(inds);
//            ArrayList<Double> fitnesses = new ArrayList<>();
//            this.fitnesses_each_cell.add(fitnesses);
//            ArrayList<double[]> PCs = new ArrayList<>();
//            this.PCs_each_cell.add(PCs);
//            ArrayList<Integer> indsFromGen = new ArrayList<>();
//            this.indsFromGen_each_cell.add(indsFromGen);
//        }
    }

    public void reMatchingAfterKmeans(){//todo: error here!!! 2023.11.21 need revise!
        ArrayList<Double> all_old_fitnesses = new ArrayList();
        ArrayList<double[]> all_old_PCs = new ArrayList();
        ArrayList<Individual> all_old_inds = new ArrayList();
        ArrayList<Integer> all_old_indsFromGen = new ArrayList();

//        this.initialRandomMultiCase.clear();
//        this.initialRandomInds.clear();
//        this.initialRandomFits.clear();
//        this.initialRandomIndsFromGen.clear();

        for(int i=0; i<this.fitnesses_each_cell.size(); i++){
            for(int j=0; j<this.fitnesses_each_cell.get(i).size(); j++){

//                double[] indsCharListsMultiTreeClone = new double[this.MultiCase_each_cell.get(i).get(j).length];
//                for(int c=0; c<indsCharListsMultiTreeClone.length; c++){
//                    indsCharListsMultiTreeClone[j] = this.MultiCase_each_cell.get(i).get(j)[c];
//                }

                all_old_fitnesses.add(this.fitnesses_each_cell.get(i).get(j));
                all_old_PCs.add(this.MultiCase_each_cell.get(i).get(j).clone());
//                all_old_PCs.add(indsCharListsMultiTreeClone);
                all_old_inds.add((Individual)this.inds_each_cell.get(i).get(j).clone());
                all_old_indsFromGen.add(this.indsFromGen_each_cell.get(i).get(j));

//                this.initialRandomFits.add(this.fitnesses_each_cell.get(i).get(j));
//                this.initialRandomMultiCase.add(this.MultiCase_each_cell.get(i).get(j).clone());
//                this.initialRandomInds.add((Individual)this.inds_each_cell.get(i).get(j).clone());
//                this.initialRandomIndsFromGen.add(this.indsFromGen_each_cell.get(i).get(j));
            }
        }
//
        this.fitnesses_each_cell.clear();
        this.MultiCase_each_cell.clear();
        this.inds_each_cell.clear();
        this.indsFromGen_each_cell.clear();
        this.num_accept_each_cell.clear();
//
//        //add by mengxu 2023.10.11
        int numCluster = this.CVTMap.size();
        for(int i=0; i<numCluster; i++){
            ArrayList<Double> fitnesses = new ArrayList();
            this.fitnesses_each_cell.add(fitnesses);
            ArrayList<double[]> PCs = new ArrayList();
            this.MultiCase_each_cell.add(PCs);
            ArrayList<Individual> individuals = new ArrayList();
            this.inds_each_cell.add(individuals);
            ArrayList<Integer> indsFromGen = new ArrayList<>();
            this.indsFromGen_each_cell.add(indsFromGen);
            this.num_accept_each_cell.add(1);
        }
//
        int numberUpdate = 0;

        DistanceCompute distanceCompute = new DistanceCompute();
        for(int i=0; i<all_old_fitnesses.size(); i++){
            boolean badrun = false;
            if(all_old_fitnesses.get(i) >= Double.MAX_VALUE || all_old_fitnesses.get(i) >= Double.POSITIVE_INFINITY){
                badrun = true;
                System.out.println("Should not come here!");
            }
            if(!badrun){
                Point candidate = new Point(all_old_PCs.get(i), all_old_inds.get(i), all_old_fitnesses.get(i), all_old_indsFromGen.get(i));
                //keep or delete
                double min_dis = Double.MAX_VALUE;
                int clusterID = -1;
                for(Cluster cluster: this.CVTMap) {
                    Point center = cluster.getCenter();
                    double dis = 0;
                    if(Objects.equals(this.measurement_dis, "weight_binary_dis")){
                        dis = distanceCompute.getWeightBinaryDis(center, candidate);
                    }
                    else if(Objects.equals(this.measurement_dis, "euclidean_dis")){
                        dis = distanceCompute.getEuclideanDis(center, candidate);
                    }
                    else if(Objects.equals(this.measurement_dis, "correlation_dis")){
                        dis = distanceCompute.getCorrelationDis(center, candidate);
                    }

                    if(dis < min_dis){
                        clusterID = cluster.getId();
                        min_dis = dis;
                    }
                }

                if(clusterID < 0){
                    System.out.println("Error here!!!");
                }

                if(clusterID >= this.fitnesses_each_cell.size()){
                    System.out.println("Error here!!! clusterID should not >= this.fitnesses_each_cell.size()");
                    this.num_accept_each_cell.add(1);

                    candidate.setClusterId(clusterID);
                    ArrayList<Double> candidate_fit = new ArrayList<>();
                    candidate_fit.add(candidate.getFitness());
                    this.fitnesses_each_cell.add(candidate_fit);//todo: need double check 2023.10.11

                    ArrayList<Individual> inds = new ArrayList<>();
                    inds.add(candidate.getIndividual());
                    this.inds_each_cell.add(inds);

                    ArrayList<double[]> PCs = new ArrayList<>();
                    PCs.add(candidate.getlocalArray().clone());
                    this.MultiCase_each_cell.add(PCs);

                    ArrayList<Integer> indsFromGen = new ArrayList<>();
                    indsFromGen.add(candidate.getFromGen());
                    this.indsFromGen_each_cell.add(indsFromGen);

                    numberUpdate++;
                }
                else{
                    int num_thisCell = this.fitnesses_each_cell.get(clusterID).size();
                    if(num_thisCell < this.num_accept_each_cell.get(clusterID)){
                        candidate.setClusterId(clusterID);
                        this.fitnesses_each_cell.get(clusterID).add(candidate.getFitness());
                        this.MultiCase_each_cell.get(clusterID).add(candidate.getlocalArray().clone());
                        this.inds_each_cell.get(clusterID).add(candidate.getIndividual());
                        this.indsFromGen_each_cell.get(clusterID).add(candidate.getFromGen());
                        numberUpdate++;
                    }
                    else{
//                        System.out.println("repit!");
                        double worstFitInCell = Double.NEGATIVE_INFINITY;
                        int worstIndex = -1;
                        for(int j=0; j<this.fitnesses_each_cell.get(clusterID).size(); j++){
                            double refFit = this.fitnesses_each_cell.get(clusterID).get(j);
                            if(refFit > worstFitInCell){
                                worstFitInCell = refFit;
                                worstIndex = j;
                            }
                        }
                        int gapGen = candidate.getFromGen() - this.indsFromGen_each_cell.get(clusterID).get(worstIndex);
                        worstFitInCell = worstFitInCell * Math.pow(this.penalty,gapGen);//add by mengxu 2023.07.26
                        if(candidate.getFitness() <= worstFitInCell){
                            this.fitnesses_each_cell.get(clusterID).set(worstIndex, candidate.getFitness());
                            this.MultiCase_each_cell.get(clusterID).set(worstIndex, candidate.getlocalArray().clone());
                            this.inds_each_cell.get(clusterID).set(worstIndex, candidate.getIndividual());
                            this.indsFromGen_each_cell.get(clusterID).set(worstIndex, candidate.getFromGen());
                            numberUpdate++;
                        }
                    }
                }
//                else{
//                    System.out.println("Error in reMatchingAfterKmeans!");
//                    System.out.println("clusterID: " + clusterID);
//                    System.out.println("candidate.getFitness(): " + candidate.getFitness());
//                    System.out.println("min_dis: " + min_dis);
//                }
            }
        }
        System.out.println("Number update in CVTMAP when rematching: " + numberUpdate);
    }

//    public void reMatchingAfterKmeans(){//todo: error here!!! 2023.11.21 need revise!
//        ArrayList<Double> all_old_fitnesses = new ArrayList();
//        ArrayList<double[]> all_old_PCs = new ArrayList();
//        ArrayList<Individual> all_old_inds = new ArrayList();
//        ArrayList<Integer> all_old_indsFromGen = new ArrayList();
//
//        this.initialRandomMultiCase.clear();
//        this.initialRandomInds.clear();
//        this.initialRandomFits.clear();
//        this.initialRandomIndsFromGen.clear();
//
//        for(int i=0; i<this.fitnesses_each_cell.size(); i++){
//            for(int j=0; j<this.fitnesses_each_cell.get(i).size(); j++){
//
////                double[] indsCharListsMultiTreeClone = new double[this.MultiCase_each_cell.get(i).get(j).length];
////                for(int c=0; c<indsCharListsMultiTreeClone.length; c++){
////                    indsCharListsMultiTreeClone[j] = this.MultiCase_each_cell.get(i).get(j)[c];
////                }
//
//                all_old_fitnesses.add(this.fitnesses_each_cell.get(i).get(j));
//                all_old_PCs.add(this.MultiCase_each_cell.get(i).get(j).clone());
////                all_old_PCs.add(indsCharListsMultiTreeClone);
//                all_old_inds.add((Individual)this.inds_each_cell.get(i).get(j).clone());
//                all_old_indsFromGen.add(this.indsFromGen_each_cell.get(i).get(j));
//
//                this.initialRandomFits.add(this.fitnesses_each_cell.get(i).get(j));
//                this.initialRandomMultiCase.add(this.MultiCase_each_cell.get(i).get(j).clone());
//                this.initialRandomInds.add((Individual)this.inds_each_cell.get(i).get(j).clone());
//                this.initialRandomIndsFromGen.add(this.indsFromGen_each_cell.get(i).get(j));
//            }
//        }
//
//        this.fitnesses_each_cell.clear();
//        this.MultiCase_each_cell.clear();
//        this.inds_each_cell.clear();
//        this.indsFromGen_each_cell.clear();
//        this.num_accept_each_cell.clear();
//
//        //add by mengxu 2023.10.11
//        int numCluster = this.CVTMap.size();
//        for(int i=0; i<numCluster; i++){
//            ArrayList<Double> fitnesses = new ArrayList();
//            this.fitnesses_each_cell.add(fitnesses);
//            ArrayList<double[]> PCs = new ArrayList();
//            this.MultiCase_each_cell.add(PCs);
//            ArrayList<Individual> individuals = new ArrayList();
//            this.inds_each_cell.add(individuals);
//            ArrayList<Integer> indsFromGen = new ArrayList<>();
//            this.indsFromGen_each_cell.add(indsFromGen);
//            this.num_accept_each_cell.add(1);
//        }
//
//        int numberUpdate = 0;
//
//        DistanceCompute distanceCompute = new DistanceCompute();
//        for(int i=0; i<all_old_fitnesses.size(); i++){
//            boolean badrun = false;
//            if(all_old_fitnesses.get(i) >= Double.MAX_VALUE || all_old_fitnesses.get(i) >= Double.POSITIVE_INFINITY){
//                badrun = true;
//            }
//            if(!badrun){
//                Point candidate = new Point(all_old_PCs.get(i), all_old_inds.get(i), all_old_fitnesses.get(i), all_old_indsFromGen.get(i));
//                //keep or delete
//                double min_dis = Double.MAX_VALUE;
//                int clusterID = -1;
//                for(Cluster cluster: this.CVTMap) {
//                    Point center = cluster.getCenter();
//                    double dis = 0;
//                    if(Objects.equals(this.measurement_dis, "weight_binary_dis")){
//                        dis = distanceCompute.getWeightBinaryDis(center, candidate);
//                    }
//                    else if(Objects.equals(this.measurement_dis, "euclidean_dis")){
//                        dis = distanceCompute.getEuclideanDis(center, candidate);
//                    }
//                    else if(Objects.equals(this.measurement_dis, "correlation_dis")){
//                        dis = distanceCompute.getCorrelationDis(center, candidate);
//                    }
//
//                    if(dis < min_dis){
//                        clusterID = cluster.getId();
//                        min_dis = dis;
//                    }
//                }
//
//                if(clusterID < 0){
//                    System.out.println("Error here!!!");
//                }
//
//                if(clusterID >= this.fitnesses_each_cell.size()){
//                    System.out.println("Error here!!! clusterID should not >= this.fitnesses_each_cell.size()");
//                    this.num_accept_each_cell.add(1);
//
//                    candidate.setClusterId(clusterID);
//                    ArrayList<Double> candidate_fit = new ArrayList<>();
//                    candidate_fit.add(candidate.getFitness());
//                    this.fitnesses_each_cell.add(candidate_fit);//todo: need double check 2023.10.11
//
//                    ArrayList<Individual> inds = new ArrayList<>();
//                    inds.add(candidate.getIndividual());
//                    this.inds_each_cell.add(inds);
//
//                    ArrayList<double[]> PCs = new ArrayList<>();
//                    PCs.add(candidate.getlocalArray().clone());
//                    this.MultiCase_each_cell.add(PCs);
//
//                    ArrayList<Integer> indsFromGen = new ArrayList<>();
//                    indsFromGen.add(candidate.getFromGen());
//                    this.indsFromGen_each_cell.add(indsFromGen);
//
//                    numberUpdate++;
//                }
//                else{
//                    int num_thisCell = this.fitnesses_each_cell.get(clusterID).size();
//                    if(num_thisCell < this.num_accept_each_cell.get(clusterID)){
//                        candidate.setClusterId(clusterID);
//                        this.fitnesses_each_cell.get(clusterID).add(candidate.getFitness());
//                        this.MultiCase_each_cell.get(clusterID).add(candidate.getlocalArray().clone());
//                        this.inds_each_cell.get(clusterID).add(candidate.getIndividual());
//                        this.indsFromGen_each_cell.get(clusterID).add(candidate.getFromGen());
//                        numberUpdate++;
//                    }
//                    else{
////                        System.out.println("repit!");
//                        double worstFitInCell = Double.NEGATIVE_INFINITY;
//                        int worstIndex = -1;
//                        for(int j=0; j<this.fitnesses_each_cell.get(clusterID).size(); j++){
//                            double refFit = this.fitnesses_each_cell.get(clusterID).get(j);
//                            if(refFit > worstFitInCell){
//                                worstFitInCell = refFit;
//                                worstIndex = j;
//                            }
//                        }
//                        int gapGen = candidate.getFromGen() - this.indsFromGen_each_cell.get(clusterID).get(worstIndex);
//                        worstFitInCell = worstFitInCell * Math.pow(this.penalty,gapGen);//add by mengxu 2023.07.26
//                        if(candidate.getFitness() <= worstFitInCell){
//                            this.fitnesses_each_cell.get(clusterID).set(worstIndex, candidate.getFitness());
//                            this.MultiCase_each_cell.get(clusterID).set(worstIndex, candidate.getlocalArray().clone());
//                            this.inds_each_cell.get(clusterID).set(worstIndex, candidate.getIndividual());
//                            this.indsFromGen_each_cell.get(clusterID).set(worstIndex, candidate.getFromGen());
//                            numberUpdate++;
//                        }
//                    }
//                }
////                else{
////                    System.out.println("Error in reMatchingAfterKmeans!");
////                    System.out.println("clusterID: " + clusterID);
////                    System.out.println("candidate.getFitness(): " + candidate.getFitness());
////                    System.out.println("min_dis: " + min_dis);
////                }
//            }
//        }
//        System.out.println("Number update in CVTMAP when rematching: " + numberUpdate);
//    }

    public void updateCVTMapIndividualsWithIndividual(EvolutionState state, Individual individual,
                                                      double[] indsCharListsMultiTree,
                                                      double fitnessesForModel){
//        this.updateCVTMap(state, individuals, indsCharListsMultiTree, fitnessesForModel, threshold);

        int numberUpdate = 0;

        DistanceCompute distanceCompute = new DistanceCompute();
        boolean badrun = false;
        if(fitnessesForModel >= Double.MAX_VALUE || fitnessesForModel >= Double.POSITIVE_INFINITY){
            badrun = true;
        }
        if(!badrun){
            Point candidate = new Point(indsCharListsMultiTree, individual, fitnessesForModel, state.generation);
            //keep or delete
            double min_dis = Double.MAX_VALUE;
            int clusterID = -1;
            for(Cluster cluster: this.CVTMap) {
                Point center = cluster.getCenter();
                double dis = 0;
                if(Objects.equals(this.measurement_dis, "weight_binary_dis")){
                    dis = distanceCompute.getWeightBinaryDis(center, candidate);
                }
                else if(Objects.equals(this.measurement_dis, "euclidean_dis")){
                    dis = distanceCompute.getEuclideanDis(center, candidate);
                }
                else if(Objects.equals(this.measurement_dis, "correlation_dis")){
                    dis = distanceCompute.getCorrelationDis(center, candidate);
                }

                if(dis < min_dis){
                    clusterID = cluster.getId();
                    min_dis = dis;
                }
            }

            if(clusterID < 0){
                System.out.println("Error here!!!");
            }

            if(clusterID >= this.fitnesses_each_cell.size()){//todo: need double check, what meaning this represents?????? by mengxu 2023.09.25

                this.num_accept_each_cell.add(1);

                candidate.setClusterId(clusterID);
                ArrayList<Double> candidate_fit = new ArrayList<>();
                candidate_fit.add(candidate.getFitness());
                this.fitnesses_each_cell.add(candidate_fit);

                ArrayList<Individual> inds = new ArrayList<>();
                inds.add(candidate.getIndividual());
                this.inds_each_cell.add(inds);

                ArrayList<double[]> PCs = new ArrayList<>();
                PCs.add(candidate.getlocalArray().clone());
                this.MultiCase_each_cell.add(PCs);

                ArrayList<Integer> indsFromGen = new ArrayList<>();
                indsFromGen.add(candidate.getFromGen());
                this.indsFromGen_each_cell.add(indsFromGen);

                numberUpdate++;
            }
            else{
                int num_thisCell = this.fitnesses_each_cell.get(clusterID).size();
                if(num_thisCell < this.num_accept_each_cell.get(clusterID)){
                    candidate.setClusterId(clusterID);
                    this.fitnesses_each_cell.get(clusterID).add(candidate.getFitness());
                    this.MultiCase_each_cell.get(clusterID).add(candidate.getlocalArray().clone());
                    this.inds_each_cell.get(clusterID).add(candidate.getIndividual());
                    this.indsFromGen_each_cell.get(clusterID).add(candidate.getFromGen());
                    numberUpdate++;
                }
                else{
                    double worstFitInCell = Double.NEGATIVE_INFINITY;
                    int worstIndex = -1;
                    for(int j=0; j<this.fitnesses_each_cell.get(clusterID).size(); j++){
                        double refFit = this.fitnesses_each_cell.get(clusterID).get(j);
                        if(refFit > worstFitInCell){
                            worstFitInCell = refFit;
                            worstIndex = j;
                        }
                    }
                    int gapGen = state.generation - this.indsFromGen_each_cell.get(clusterID).get(worstIndex);
                    worstFitInCell = worstFitInCell * Math.pow(this.penalty,gapGen);//add by mengxu 2023.07.26
                    if(candidate.getFitness() <= worstFitInCell){
                        this.fitnesses_each_cell.get(clusterID).set(worstIndex, candidate.getFitness());
                        this.MultiCase_each_cell.get(clusterID).set(worstIndex, candidate.getlocalArray().clone());
                        this.inds_each_cell.get(clusterID).set(worstIndex, candidate.getIndividual());
                        this.indsFromGen_each_cell.get(clusterID).set(worstIndex, candidate.getFromGen());
                        numberUpdate++;
                    }
                }
            }
        }

//        System.out.println("Number update in CVTMAP: " + numberUpdate);
    }

    public void updateCVTMapIndividualsWithPopulation(EvolutionState state, Individual[] individuals,
                                                      double[][] indsCharListsMultiTree,
                                                      double[] fitnessesForModel,
                                                      double threshold){
        this.updateCVTMap(state, individuals, indsCharListsMultiTree, fitnessesForModel, threshold);

        int numberUpdate = 0;

        DistanceCompute distanceCompute = new DistanceCompute();
        for(int i=0; i<individuals.length; i++){
            boolean badrun = false;
            if(fitnessesForModel[i] >= Double.MAX_VALUE || fitnessesForModel[i] >= Double.POSITIVE_INFINITY){
                badrun = true;
            }
            if(!badrun){
                Point candidate = new Point(indsCharListsMultiTree[i], individuals[i], fitnessesForModel[i], state.generation);
                //keep or delete
                double min_dis = Double.MAX_VALUE;
                int clusterID = -1;
                for(Cluster cluster: this.CVTMap) {
                    Point center = cluster.getCenter();
                    double dis = 0;
                    if(Objects.equals(this.measurement_dis, "weight_binary_dis")){
                        dis = distanceCompute.getWeightBinaryDis(center, candidate);
                    }
                    else if(Objects.equals(this.measurement_dis, "euclidean_dis")){
                        dis = distanceCompute.getEuclideanDis(center, candidate);
                    }
                    else if(Objects.equals(this.measurement_dis, "correlation_dis")){
                        dis = distanceCompute.getCorrelationDis(center, candidate);
                    }
                    if(dis < min_dis){
                        clusterID = cluster.getId();
                        min_dis = dis;
                    }
                }

                if(clusterID >= this.fitnesses_each_cell.size()){
                    System.out.println("Error here!!! clusterID should not >= this.fitnesses_each_cell.size()");
                    this.num_accept_each_cell.add(1);

                    candidate.setClusterId(clusterID);
                    ArrayList<Double> candidate_fit = new ArrayList<>();
                    candidate_fit.add(candidate.getFitness());
                    this.fitnesses_each_cell.add(candidate_fit);

                    ArrayList<Individual> inds = new ArrayList<>();
                    inds.add(candidate.getIndividual());
                    this.inds_each_cell.add(inds);

                    ArrayList<double[]> PCs = new ArrayList<>();
                    PCs.add(candidate.getlocalArray().clone());
                    this.MultiCase_each_cell.add(PCs);

                    ArrayList<Integer> indsFromGen = new ArrayList<>();
                    indsFromGen.add(candidate.getFromGen());
                    this.indsFromGen_each_cell.add(indsFromGen);

                    numberUpdate++;
                }
                else{
                    //todo: need to update all the elites in the CVT-MAP before this after Kmeans-RUn as we add new PCs to generate the CVT-MAP
                    int num_thisCell = this.fitnesses_each_cell.get(clusterID).size();
                    if(num_thisCell < this.num_accept_each_cell.get(clusterID)){
                        candidate.setClusterId(clusterID);
                        this.fitnesses_each_cell.get(clusterID).add(candidate.getFitness());
                        this.MultiCase_each_cell.get(clusterID).add(candidate.getlocalArray().clone());
                        this.inds_each_cell.get(clusterID).add(candidate.getIndividual());
                        this.indsFromGen_each_cell.get(clusterID).add(candidate.getFromGen());
                        numberUpdate++;
                    }
                    else{
                        double worstFitInCell = Double.NEGATIVE_INFINITY;
                        int worstIndex = -1;
                        for(int j=0; j<this.fitnesses_each_cell.get(clusterID).size(); j++){
                            double refFit = this.fitnesses_each_cell.get(clusterID).get(j);
                            if(refFit > worstFitInCell){
                                worstFitInCell = refFit;
                                worstIndex = j;
                            }
                        }
                        int gapGen = state.generation - this.indsFromGen_each_cell.get(clusterID).get(worstIndex);
                        worstFitInCell = worstFitInCell * Math.pow(this.penalty,gapGen);//add by mengxu 2023.07.26
                        if(candidate.getFitness() < worstFitInCell){
                            this.fitnesses_each_cell.get(clusterID).set(worstIndex, candidate.getFitness());
                            this.MultiCase_each_cell.get(clusterID).set(worstIndex, candidate.getlocalArray().clone());
                            this.inds_each_cell.get(clusterID).set(worstIndex, candidate.getIndividual());
                            this.indsFromGen_each_cell.get(clusterID).set(worstIndex, candidate.getFromGen());
                            numberUpdate++;
                        }
                    }
                }
            }
        }

        System.out.println("Number update in CVTMAP: " + numberUpdate);
    }


    public int getNumberIndividualsInCVTMap(){
        int number = 0;
        for(int i=0; i<this.fitnesses_each_cell.size(); i++){
            number = number + this.fitnesses_each_cell.get(i).size();
        }
        return number;
    }

    public ArrayList<Individual> getAllIndividualsInCVTMap(EvolutionState state){
        ArrayList<Individual> individuals = new ArrayList<>();
        for(int i=0; i<this.inds_each_cell.size(); i++){
            ArrayList<Individual> indsRef = this.inds_each_cell.get(i);
            for(int j=0; j<indsRef.size(); j++){
                Individual ind = (Individual)indsRef.get(j).clone();
                double ind_old_fit = this.fitnesses_each_cell.get(i).get(j);
                int gapGen = state.generation - this.indsFromGen_each_cell.get(i).get(j);
                double penaltyFit = ind_old_fit * Math.pow(this.penalty,gapGen);//add by mengxu 2023.07.26;
                double[] objectives = new double[1];
                objectives[0] = penaltyFit;
                ((MultiObjectiveFitness)ind.fitness).setObjectives(state,objectives);
                //todo: need modify here 2023.11.03
//                double[][] ind_multi_case_old_fit = ((OneInstanceMultiCaseMultiObjectiveFitnessMAPElites)ind.fitness).multiInstanceMultiObjectiveFitness;
//                double[][] ind_multi_case_old_fit_penalty = new double[ind_multi_case_old_fit.length][ind_multi_case_old_fit[0].length];
//                for(int c=0; c<ind_multi_case_old_fit.length; c++){
//                    int gapGen = state.generation - this.indsFromGen_each_cell.get(i).get(j);
//                    double penaltyFit = ind_multi_case_old_fit[c][0] * Math.pow(this.penalty,gapGen);//add by mengxu 2023.07.26;
//                    ind_multi_case_old_fit_penalty[c][0] = penaltyFit;
//                }
//                ((OneInstanceMultiCaseMultiObjectiveFitnessMAPElites)ind.fitness).setMultiInstanceFitness(state,ind_multi_case_old_fit_penalty);
//                System.out.println("Penalty fitness: " + ind.fitness.fitness());
                individuals.add(ind);
            }
//            individuals.addAll(indsRef);
        }
        return individuals;
    }

    public ArrayList<double[]> getAllIndividualsPCInCVTMap(EvolutionState state){
        ArrayList<double[]> PCs = new ArrayList<>();
        for(int i=0; i<this.MultiCase_each_cell.size(); i++){
            ArrayList<double[]> PCsRef = this.MultiCase_each_cell.get(i);
            for(int j=0; j<PCsRef.size(); j++){
                double[] PC = PCsRef.get(j).clone();
                PCs.add(PC);
            }
        }
        return PCs;
    }

    public ArrayList<double[]> getAllIndividualsFitnessInCVTMap(EvolutionState state){
        ArrayList<double[]> fits = new ArrayList<>();
        for(int i=0; i<this.inds_each_cell.size(); i++){
            ArrayList<Double> indsRef = this.fitnesses_each_cell.get(i);
            for(int j=0; j<indsRef.size(); j++){
                int gapGen = state.generation - this.indsFromGen_each_cell.get(i).get(j);
                double refFit = indsRef.get(j) * Math.pow(this.penalty,gapGen);//add by mengxu 2023.07.26;
                double[] objectives = new double[1];
                objectives[0] = refFit;
                fits.add(objectives);
            }
        }
        return fits;
    }

    public int inRandomPCsMap(double[] randomPC){
        int inMap = -1;
        for(int i=0; i<this.initialRandomMultiCase.size(); i++){
            double[] decisionA = this.initialRandomMultiCase.get(i);
            if(equal(decisionA, randomPC)){
                inMap = i;
                return inMap;
            }
        }
        return inMap;
    }

    public int inCellPCsMap(double[] randomPC){
        int inMap = -1;
        for(int i=0; i<this.MultiCase_each_cell.size(); i++){
            ArrayList<double[]> PCs = this.MultiCase_each_cell.get(i);
            for(int j=0; j<PCs.size(); j++){
                double[] decisionA = PCs.get(j);
                if(equal(decisionA, randomPC)){
                    inMap = i;
                    return inMap;
                }
            }
        }
        return inMap;
    }


    public boolean equal(double[] decisionA, double[] decisionB){
        boolean equal = true;
        for(int i=0; i<decisionA.length; i++){
            if(Double.compare(decisionA[i], decisionB[i]) != 0){
//                System.out.println("Not equal test here!");
                return false;
            }
        }
        return equal;
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
