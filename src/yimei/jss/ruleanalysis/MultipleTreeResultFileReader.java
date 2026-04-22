package yimei.jss.ruleanalysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import mengxu.algorithm.ensemble.EnsembleRule;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import yimei.jss.rule.operation.evolved.GPRule;

public class MultipleTreeResultFileReader extends ResultFileReader {

	public static TestResult readTestResultFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                    int numTrees) {
		TestResult result = new TestResult();

		String line;
		Fitness fitnesses;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			while (!(line = br.readLine()).equals("Best Individual of Run:")) {
//				while (!(line = br.readLine()).equals(" PARETO FRONTS")) {
				
				if (line.startsWith("Generation")) {
					br.readLine(); // Best individual:

					GPRule sequencingRule;
					GPRule routingRule;

					br.readLine(); // Subpopulation 0:
					br.readLine(); // Evaluated: true

					//this is suitable for single individual!
					line = br.readLine(); // read in fitness on following line
					fitnesses = readFitnessFromLine(line, isMultiObjective);
					br.readLine(); // tree 0
					line = br.readLine(); // this is a sequencing rule
					// sequencing rule
					sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
					// routing rule
					br.readLine();
					line = br.readLine();
					routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

					Fitness fitness = fitnesses;
					GPRule[] bestRules = new GPRule[numTrees];

					bestRules[0] = sequencingRule; // sequencing rule
					bestRules[1] = routingRule; // routing rule

					result.setBestRules(bestRules);
					result.setBestTrainingFitness(fitness);

					result.addGenerationalRules(bestRules);
					result.addGenerationalTrainFitness(fitness);
					result.addGenerationalTestFitnesses((Fitness) fitness.clone());

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	public static TestResult readSubPopTestResultFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
													int numTrees, int subpop) {
		TestResult result = new TestResult();

		String line;
		Fitness fitnesses;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			while (!(line = br.readLine()).equals("Best Individual of Run:")) {
//				while (!(line = br.readLine()).equals(" PARETO FRONTS")) {

				if (line.startsWith("Generation")) {
					br.readLine(); // Best individual:

					GPRule sequencingRule;
					GPRule routingRule;

					while (!(line = br.readLine()).equals("Subpopulation " + subpop + ":")){}
					//todo: need to double check this place

//					br.readLine(); // Subpopulation 0:
					br.readLine(); // Evaluated: true

					//this is suitable for single individual!
					line = br.readLine(); // read in fitness on following line
					fitnesses = readFitnessFromLine(line, isMultiObjective);
					br.readLine(); // tree 0
					line = br.readLine(); // this is a sequencing rule
					// sequencing rule
					sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
					// routing rule
					br.readLine();
					line = br.readLine();
					routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

					Fitness fitness = fitnesses;
					GPRule[] bestRules = new GPRule[numTrees];

					bestRules[0] = sequencingRule; // sequencing rule
					bestRules[1] = routingRule; // routing rule

					result.setBestRules(bestRules);
					result.setBestTrainingFitness(fitness);

					result.addGenerationalRules(bestRules);
					result.addGenerationalTrainFitness(fitness);
					result.addGenerationalTestFitnesses((Fitness) fitness.clone());

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	public static TestResult readEnsembleContributionTestResultFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                                        int numTrees) {
		TestResult result = new TestResult();

		String line;
		Fitness finalFitnesses;
		Fitness fitnesses;

		boolean finalGenerationFinish = false;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			while ((line = br.readLine()) != null) {
//			while (!(line = br.readLine()).equals("Best Individual of Run:")) {
//				while (!(line = br.readLine()).equals(" PARETO FRONTS")) {
//				if((line = br.readLine()).equals("Generation: 50")){
//					finalGenerationFinish = true;
//				}

				if (line.startsWith("Generation")) {
					br.readLine(); // Best individual:

					GPRule sequencingRule;
					GPRule routingRule;

					br.readLine(); // Subpopulation 0:

					line = br.readLine(); // read in fitness on following line
					boolean isIndividualWin = isIndividualWin(line);
					finalFitnesses = readfinalFitnessFromLine(line, isMultiObjective);

					if(isIndividualWin){
						br.readLine(); // Evaluated: true

						//this is suitable for single individual!
						line = br.readLine(); // read in fitness on following line
						fitnesses = readFitnessFromLine(line, isMultiObjective);
						br.readLine(); // tree 0
						line = br.readLine(); // this is a sequencing rule
						// sequencing rule
						sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
						// routing rule
						br.readLine();
						line = br.readLine();
						routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

						Fitness fitness = fitnesses;
						GPRule[] bestRules = new GPRule[numTrees];

						bestRules[0] = sequencingRule; // sequencing rule
						bestRules[1] = routingRule; // routing rule

						result.setBestRules(bestRules);
						result.setBestTrainingFitness(fitness);

						result.addGenerationalRules(bestRules);
						result.addGenerationalTrainFitness(fitness);
						result.addGenerationalTestFitnesses((Fitness) fitness.clone());
						//remember the generation
						result.addGenerationalEnsembleRules(null);
						result.addGenerationalEnsembleTrainFitness(null);
						result.addGenerationalTrainEnsembleContribution(null);
						result.addGenerationalEnsembleEachTestFitness(null);
						result.addGenerationalEnsembleTestFitnesses(null);
						result.addGenerationalTestEnsembleContribution(null);
					}
					else{
						//this is for ensemble fitness
						List<GPRule> ensembleSequencingRule = new ArrayList<>();
						List<GPRule> ensembleRoutingRule = new ArrayList<>();
						List<Fitness> ensembleTrainFitness = new ArrayList<>();
						List<Fitness> ensembleEachTestFitness = new ArrayList<>();
						List<Double> ensembleContribution = new ArrayList<>();
						Fitness ensembleTestFitness = (Fitness)finalFitnesses.clone();

						line = br.readLine(); // read ensemble contribution on following line
						ensembleContribution = readEnsembleContributionFromLine(line);
						List<Double> ensembleContributionTest = new ArrayList<>();

						int ensembleSize = ensembleContribution.size();
						for(int i=0; i<ensembleSize;i++){
							br.readLine(); // Evaluated: true

							line = br.readLine(); // read in fitness on following line
							fitnesses = readFitnessFromLine(line, isMultiObjective);

							br.readLine(); // tree 0
							line = br.readLine(); // this is a sequencing rule
							sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);

							// routing rule
							br.readLine(); // tree 1
							line = br.readLine();  // this is a routing rule
							routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

							Fitness trainFitness = fitnesses;

							ensembleSequencingRule.add(sequencingRule);
							ensembleRoutingRule.add(routingRule);

							ensembleTrainFitness.add(trainFitness);
							Fitness eachTestFitness = (Fitness)fitnesses.clone();
							ensembleEachTestFitness.add(eachTestFitness);

						}

						EnsembleRule ensembleRule = new EnsembleRule(ensembleSequencingRule, ensembleRoutingRule);
						result.setEnsembleRule(ensembleRule);
						result.addGenerationalEnsembleRules(ensembleRule);
						result.addGenerationalTrainFitness(finalFitnesses);
						result.addGenerationalEnsembleTrainFitness(ensembleTrainFitness);
						result.addGenerationalTrainEnsembleContribution(ensembleContribution);
						result.addGenerationalEnsembleEachTestFitness(ensembleEachTestFitness);
						result.addGenerationalEnsembleTestFitnesses(ensembleTestFitness);
						result.addGenerationalTestEnsembleContribution(ensembleContributionTest);

						//remember the generation
						result.addGenerationalRules(null);
//						result.addGenerationalTrainFitness(null);
						result.addGenerationalTestFitnesses(null);
					}

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	public static TestResult readMAPElitesTestResultFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                             int numTrees) {
		TestResult result = new TestResult();

		String line;
		Fitness finalFitnesses;
		Fitness fitnesses;

		boolean finalGenerationFinish = false;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			while ((line = br.readLine()) != null) {
//			while (!(line = br.readLine()).equals("Best Individual of Run:")) {
//				while (!(line = br.readLine()).equals(" PARETO FRONTS")) {
//				if((line = br.readLine()).equals("Generation: 50")){
//					finalGenerationFinish = true;
//				}

				if (line.startsWith("Generation")) {
					br.readLine(); // Best individual:

					GPRule sequencingRule;
					GPRule routingRule;

					br.readLine(); // Subpopulation 0:

					line = br.readLine(); // Individual win with Fitness:
					boolean isIndividualWin = isIndividualWin(line);
//					finalFitnesses = readfinalFitnessFromLine(line, isMultiObjective);

					if(isIndividualWin){
						br.readLine(); // Evaluated: true

						//this is suitable for single individual!
						line = br.readLine(); // read in fitness on following line
						fitnesses = readFitnessFromLine(line, isMultiObjective);
						br.readLine(); // tree 0
						line = br.readLine(); // this is a sequencing rule
						// sequencing rule
						sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
						// routing rule
						br.readLine();
						line = br.readLine();
						routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

						Fitness fitness = fitnesses;
						GPRule[] bestRules = new GPRule[numTrees];

						bestRules[0] = sequencingRule; // sequencing rule
						bestRules[1] = routingRule; // routing rule

						result.setBestRules(bestRules);
						result.setBestTrainingFitness(fitness);

						result.addGenerationalRules(bestRules);
						result.addGenerationalTrainFitness(fitness);
						result.addGenerationalTestFitnesses((Fitness) fitness.clone());
						//remember the generation
						result.addGenerationalEnsembleRules(null);
						result.addGenerationalEnsembleTrainFitness(null);
//						result.addGenerationalTrainEnsembleContribution(null);
						result.addGenerationalEnsembleEachTestFitness(null);
//						result.addGenerationalEnsembleTestFitnesses(null);
//						result.addGenerationalTestEnsembleContribution(null);
					}
					else{
						//this is for ensemble fitness
						List<GPRule> ensembleSequencingRule = new ArrayList<>();
						List<GPRule> ensembleRoutingRule = new ArrayList<>();
						List<Fitness> ensembleTrainFitness = new ArrayList<>();
						List<Fitness> ensembleEachTestFitness = new ArrayList<>();
//						List<Double> ensembleContribution = new ArrayList<>();
//						Fitness ensembleTestFitness = (Fitness)finalFitnesses.clone();

						finalFitnesses = readfinalFitnessFromLine(line, isMultiObjective);
						double[] objectives = ((MultiObjectiveFitness)finalFitnesses).getObjectives();
						int numObj = objectives.length;
						double[] averageobjectives = new double[numObj];
						for(int i=0; i<numObj; i++){
							averageobjectives[i] = 0;
						}
						line = br.readLine(); // Elites number:
						int elites_num = readElitesNumberFromLine(line);
//						List<Double> ensembleContributionTest = new ArrayList<>();

						for(int i=0; i<elites_num;i++){
							br.readLine(); // Evaluated: true

							line = br.readLine(); // read in fitness on following line
							fitnesses = readFitnessFromLine(line, isMultiObjective);

							br.readLine(); // tree 0
							line = br.readLine(); // this is a sequencing rule
							sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);

							// routing rule
							br.readLine(); // tree 1
							line = br.readLine();  // this is a routing rule
							routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

							Fitness trainFitness = fitnesses;

							ensembleSequencingRule.add(sequencingRule);
							ensembleRoutingRule.add(routingRule);

							ensembleTrainFitness.add(trainFitness);
							Fitness eachTestFitness = (Fitness)fitnesses.clone();
							ensembleEachTestFitness.add(eachTestFitness);

							for(int j=0; j<numObj; j++){
								averageobjectives[j] = averageobjectives[j] + ((MultiObjectiveFitness)trainFitness).getObjective(j);
							}

						}

						EnsembleRule ensembleRule = new EnsembleRule(ensembleSequencingRule, ensembleRoutingRule);
						result.setEnsembleRule(ensembleRule);
						result.addGenerationalEnsembleRules(ensembleRule);
						for(int j=0; j<numObj; j++){
							averageobjectives[j] = averageobjectives[j] / elites_num;
						}
						((MultiObjectiveFitness) finalFitnesses).setObjectives(null,averageobjectives);
						result.addGenerationalTrainFitness(finalFitnesses);
						result.addGenerationalEnsembleTrainFitness(ensembleTrainFitness);
//						result.addGenerationalTrainEnsembleContribution(ensembleContribution);
						result.addGenerationalEnsembleEachTestFitness(ensembleEachTestFitness);
//						result.addGenerationalEnsembleTestFitnesses(ensembleTestFitness);
//						result.addGenerationalTestEnsembleContribution(ensembleContributionTest);

						//remember the generation
						result.addGenerationalRules(null);
//						result.addGenerationalTrainFitness(null);
						result.addGenerationalTestFitnesses(null);
					}

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	public static TestResult readEnsembleContributionAndSpreadTestResultFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                                                 int numTrees) {
		TestResult result = new TestResult();

		String line;
		Fitness finalFitnesses;
		Fitness fitnesses;

		boolean finalGenerationFinish = false;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			while ((line = br.readLine()) != null) {
//			while (!(line = br.readLine()).equals("Best Individual of Run:")) {
//				while (!(line = br.readLine()).equals(" PARETO FRONTS")) {
//				if((line = br.readLine()).equals("Generation: 50")){
//					finalGenerationFinish = true;
//				}

				if (line.startsWith("Generation")) {
					br.readLine(); // Best individual:

					GPRule sequencingRule;
					GPRule routingRule;

					br.readLine(); // Subpopulation 0:

					line = br.readLine(); // read in fitness on following line
					boolean isIndividualWin = isIndividualWin(line);
					finalFitnesses = readfinalFitnessFromLine(line, isMultiObjective);

					if(isIndividualWin){
						br.readLine(); // Evaluated: true

						//this is suitable for single individual!
						line = br.readLine(); // read in fitness on following line
						fitnesses = readFitnessFromLine(line, isMultiObjective);
						br.readLine(); // tree 0
						line = br.readLine(); // this is a sequencing rule
						// sequencing rule
						sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
						// routing rule
						br.readLine();
						line = br.readLine();
						routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

						Fitness fitness = fitnesses;
						GPRule[] bestRules = new GPRule[numTrees];

						bestRules[0] = sequencingRule; // sequencing rule
						bestRules[1] = routingRule; // routing rule

						result.setBestRules(bestRules);
						result.setBestTrainingFitness(fitness);

						result.addGenerationalRules(bestRules);
						result.addGenerationalTrainFitness(fitness);
						result.addGenerationalTestFitnesses((Fitness) fitness.clone());
						//remember the generation
						result.addGenerationalEnsembleRules(null);
						result.addGenerationalEnsembleTrainFitness(null);
						result.addGenerationalTrainEnsembleContribution(null);
						result.addGenerationalEnsembleEachTestFitness(null);
						result.addGenerationalEnsembleTestFitnesses(null);
						result.addGenerationalTestEnsembleContribution(null);
						result.addGenerationalTestEnsembleSpread(null);
					}
					else{
						//this is for ensemble fitness
						List<GPRule> ensembleSequencingRule = new ArrayList<>();
						List<GPRule> ensembleRoutingRule = new ArrayList<>();
						List<Fitness> ensembleTrainFitness = new ArrayList<>();
						List<Fitness> ensembleEachTestFitness = new ArrayList<>();
						List<Double> ensembleContribution = new ArrayList<>();
						Fitness ensembleTestFitness = (Fitness)finalFitnesses.clone();

						line = br.readLine(); // read ensemble contribution on following line
						ensembleContribution = readEnsembleContributionFromLine(line);
						List<Double> ensembleContributionTest = new ArrayList<>();
						List<Double> ensembleSpreadTest = new ArrayList<>();

						int ensembleSize = ensembleContribution.size();
						for(int i=0; i<ensembleSize;i++){
							br.readLine(); // Evaluated: true

							line = br.readLine(); // read in fitness on following line
							fitnesses = readFitnessFromLine(line, isMultiObjective);

							br.readLine(); // tree 0
							line = br.readLine(); // this is a sequencing rule
							sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);

							// routing rule
							br.readLine(); // tree 1
							line = br.readLine();  // this is a routing rule
							routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

							Fitness trainFitness = fitnesses;

							ensembleSequencingRule.add(sequencingRule);
							ensembleRoutingRule.add(routingRule);

							ensembleTrainFitness.add(trainFitness);
							Fitness eachTestFitness = (Fitness)fitnesses.clone();
							ensembleEachTestFitness.add(eachTestFitness);

						}

						EnsembleRule ensembleRule = new EnsembleRule(ensembleSequencingRule, ensembleRoutingRule);
						result.setEnsembleRule(ensembleRule);
						result.addGenerationalEnsembleRules(ensembleRule);
						result.addGenerationalTrainFitness(finalFitnesses);
						result.addGenerationalEnsembleTrainFitness(ensembleTrainFitness);
						result.addGenerationalTrainEnsembleContribution(ensembleContribution);
						result.addGenerationalEnsembleEachTestFitness(ensembleEachTestFitness);
						result.addGenerationalEnsembleTestFitnesses(ensembleTestFitness);
						result.addGenerationalTestEnsembleContribution(ensembleContributionTest);
						result.addGenerationalTestEnsembleSpread(ensembleSpreadTest);

						//remember the generation
						result.addGenerationalRules(null);
//						result.addGenerationalTrainFitness(null);
						result.addGenerationalTestFitnesses(null);
					}

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}


	//modified by mengxu 2021.05.08 add ensemble result
	public static TestResult readEnsembleTestResultFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                            int numTrees) {
		TestResult result = new TestResult();

		String line;
		Fitness fitnesses;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			while (!(line = br.readLine()).equals("End")) {
//				while (!(line = br.readLine()).equals(" PARETO FRONTS")) {

				if (line.startsWith("Generation")) {
					br.readLine(); // Ensemble size: 5

					GPRule sequencingRule;
					GPRule routingRule;
					List<GPRule> ensembleSequencingRule = new ArrayList<>();
					List<GPRule> ensembleRoutingRule = new ArrayList<>();
					List<Fitness> ensembleTrainFitness = new ArrayList<>();
					List<Fitness> ensembleValidationFitness = new ArrayList<>();


					int ensembleSize = Integer.parseInt(br.readLine());
					for(int i=0; i<ensembleSize;i++){
						br.readLine(); // Member: 0

						line = br.readLine(); // read in fitness on following line
						fitnesses = readFitnessFromLine(line, isMultiObjective);

						br.readLine(); // tree 0
						line = br.readLine(); // this is a sequencing rule
						sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);

						// routing rule
						br.readLine(); // tree 1
						line = br.readLine();  // this is a routing rule
						routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

						Fitness trainFitness = fitnesses;
						Fitness validationFitness = (Fitness)fitnesses.clone();
						Fitness testFitness = (Fitness)fitnesses.clone();

						ensembleSequencingRule.add(sequencingRule);
						ensembleRoutingRule.add(routingRule);

						ensembleTrainFitness.add(trainFitness);
						ensembleValidationFitness.add(validationFitness);
						Fitness ensembleTestFitness = testFitness;
						result.addGenerationalEnsembleTestFitnesses(ensembleTestFitness);
					}

					EnsembleRule ensembleRule = new EnsembleRule(ensembleSequencingRule, ensembleRoutingRule);

					result.setEnsembleRule(ensembleRule);

					result.addGenerationalEnsembleRules(ensembleRule);
					result.addGenerationalEnsembleTrainFitness(ensembleTrainFitness);
//					result.addGenerationalEnsembleTestFitnesses(ensembleTestFitness);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	private static Fitness readFitnessFromLine(String line, boolean isMultiobjective) {
		if (isMultiobjective) {
			return parseFitness(line);//modified by mengxu 2021.05.31
			// TODO read multi-objective fitness line
//			String[] spaceSegments = line.split("\\s+");
//			String[] equation = spaceSegments[1].split("=");
//			double fitness = Double.valueOf(equation[1]);
//			KozaFitness f = new KozaFitness();
//			f.setStandardizedFitness(null, fitness);
//
//			return f;
		} else {
			String[] spaceSegments = line.split("\\s+");
			String[] fitVec = spaceSegments[1].split("\\[|\\]");
			double fitness = Double.valueOf(fitVec[1]);
			MultiObjectiveFitness f = new MultiObjectiveFitness();
			f.objectives = new double[1];
			f.objectives[0] = fitness;

			return f;
		}
	}

	//add by mengxu 2023.02.27
	private static Fitness readfinalFitnessFromLine(String line, boolean isMultiobjective) {
		if (isMultiobjective) {
			return parseFitness(line);//modified by mengxu 2021.05.31
			// TODO read multi-objective fitness line
//			String[] spaceSegments = line.split("\\s+");
//			String[] equation = spaceSegments[1].split("=");
//			double fitness = Double.valueOf(equation[1]);
//			KozaFitness f = new KozaFitness();
//			f.setStandardizedFitness(null, fitness);
//
//			return f;
		} else {
			String[] spaceSegments = line.split("\\s+");
			String[] fitVec = spaceSegments[spaceSegments.length-1].split("\\[|\\]");
			double fitness = Double.valueOf(fitVec[1]);
			MultiObjectiveFitness f = new MultiObjectiveFitness();
			f.objectives = new double[1];
			f.objectives[0] = fitness;

			return f;
		}
	}

	private static int readElitesNumberFromLine(String line) {
		String[] spaceSegments = line.split("\\s+");
		int elitesNumber = Integer.valueOf(spaceSegments[2]);

		return elitesNumber;
	}

	private static List<Double> readEnsembleContributionFromLine(String line) {
		String[] spaceSegments = line.split("\\s+");
		List<Double> ensembleContribution = new ArrayList<>();
		for(int i=2; i< spaceSegments.length; i++){
			String[] fitVec = spaceSegments[i].split("\\[|\\]|,");
			if(i==2){
				ensembleContribution.add(Double.valueOf(fitVec[1]));
			}
			else{
				ensembleContribution.add(Double.valueOf(fitVec[0]));
			}
		}

//		for(int i=0; i< ensembleContributionString.length; i++){
//			double contribution = Double.valueOf(ensembleContributionString[i]);
//			ensembleContribution.add(contribution);
//		}

		return ensembleContribution;
	}

	private static boolean isIndividualWin(String line) {
		String[] spaceSegments = line.split("\\s+");
		String[] fitVec = spaceSegments[spaceSegments.length-1].split("\\[|\\]");
		double fitness = Double.valueOf(fitVec[1]);
		MultiObjectiveFitness f = new MultiObjectiveFitness();
		f.objectives = new double[1];
		f.objectives[0] = fitness;

		if(Objects.equals(spaceSegments[0], "Individual")){
			return true;//modified by mengxu
		}
		return false;
	}

	   //24.8.2018  fzhang read badrun into CSV
    public static DescriptiveStatistics readBadRunFromFile(File file) {
        DescriptiveStatistics generationalBadRunStat = new DescriptiveStatistics();

        String line;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine();
            while(true) {
                line = br.readLine();

                if (line == null)
                    break;

                String[] commaSegments = line.split(",");
                generationalBadRunStat.addValue(Double.valueOf(commaSegments[1])); //read from excel, the first column is 0
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return generationalBadRunStat;
    }

    //modified by mengxu 2021.05.31
	private static MultiObjectiveFitness parseFitness(String line)
	{
		String[] spaceSegments = line.split("\\s+");//\\s��ʾ   �ո�,�س�,���еȿհ׷�, +�ű�ʾһ����������˼
		MultiObjectiveFitness f = new MultiObjectiveFitness();
		f.objectives = new double[spaceSegments.length - 1];
		for(int i = 1; i < spaceSegments.length; i++)
		{
			String[] equation = spaceSegments[i].split("\\[|\\]");
			double fitness = Double.valueOf(equation[i == 1 ? 1 : 0]);
			f.objectives[i-1] = fitness;
		}

		return f;
//		String[] equation1 = spaceSegments[1].split("\\[|\\]");
//		String[] equation2 = spaceSegments[2].split("\\[|\\]");
//		double fitness1 = Double.valueOf(equation1[1]);
//		double fitness2 = Double.valueOf(equation2[0]);
////		MultiObjectiveFitness f = new MultiObjectiveFitness();
////		f.objectives = new double[2];
//		f.objectives[0] = fitness1;
//		f.objectives[1] = fitness2;
	}
    
}
