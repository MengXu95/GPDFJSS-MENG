package mengxu.ruleanalysis.NSGAII;

import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.ResultFileReader;
import yimei.jss.ruleanalysis.RuleTypeV2;
import yimei.jss.ruleanalysis.TestResult;
import yimei.util.lisp.LispSimplifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class NSGAIIMultipleTreeResultFileReader extends ResultFileReader {

	public static TestResult readFeatureContributionFromFile(File file,
													RuleTypeV2 ruleType,
													boolean isMultiObjective,
													int numPopulations) {
		TestResult result = new TestResult();

		String line;
		Fitness[] fitnesses = new Fitness[numPopulations];

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			while (!(line = br.readLine()).equals("Best Individual of Run:")) {
				if (line.startsWith("Generation")) {
					br.readLine(); //Best individual:

					GPRule[] rules = new GPRule[numPopulations];
					GPRule[] collaborators = new GPRule[numPopulations];
					for (int i = 0; i < numPopulations; ++i) {
						br.readLine(); //Subpopulation i:
						br.readLine(); //Evaluated: true
						line = br.readLine(); //this will be either a fitness or collaborator rule
						if (numPopulations == 2) {
							//collaborator rule

							//fzhang 2019.1.30 if use simplified version, there are some error in some tests
							//line = LispSimplifier.simplifyExpression(line);

							if (i == 0) {
								collaborators[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);
							} else {
								collaborators[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
							}

							line = br.readLine(); //read in fitness on following line
						}
						fitnesses[i] = readFitnessFromLine(line, isMultiObjective);

						if (numPopulations == 2) {
							br.readLine(); //Collaborator 1 or 0:
						}

//                        br.readLine(); //Tree 0: //add by mengxu
//                        br.readLine(); //Tree 0: //add by mengxu
//
//                        br.readLine(); //Tree 0: //add by mengxu
//                        br.readLine(); //Tree 0: //add by mengxu

						br.readLine(); //Tree 0:
						String expression = br.readLine();

						//fzhang 2019.1.30 if use simplified version, there are some error in some tests
						//expression = LispSimplifier.simplifyExpression(expression);

						if (i == 0) {
							//subpop 0 is sequencing rules
							rules[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING,expression);
						} else {
							//subpop 1 is routing rules
							rules[i] = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING,expression);
						}
					}

					Fitness fitness = fitnesses[0];
					GPRule[] bestRules = rules; //will just be single rule for 1 subpop

					if (numPopulations == 2) {
						//need to decide which subpop yielded better fitnesses
						if (fitness.fitness() < fitnesses[1].fitness()) {
							//subpop 0 was best
							bestRules[0] = rules[0];  //sequencing rule
							bestRules[1] = collaborators[0]; //routing rule
						} else {
							//subpop 1 was best
							fitness = fitnesses[1];
							bestRules[0] = rules[1];  //routing rule
							bestRules[1] = collaborators[1]; //sequencing rule
						}
					}
					result.setBestRules(bestRules);
					result.setBestTrainingFitness(fitness);

					result.addGenerationalRules(bestRules);
					result.addGenerationalTrainFitness(fitness);
					result.addGenerationalValidationFitnesses((Fitness) fitness.clone());
					result.addGenerationalTestFitnesses((Fitness) fitness.clone());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}


	public static TestResult readNSGAIIParetoFrontFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                           int numTrees) {
		TestResult result = new TestResult();

		String line;
		Fitness fitnesses;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			//read the file until arriveing the position 'Pareto Front of Subpopulation 0'
			line = br.readLine();
			while(!(line.equals("Pareto Front of Subpopulation 0"))) {
				line = br.readLine();
			}
			//the output here is line = Pareto Front of Subpopulation 0
			line = br.readLine();
	
			while(line != null) {
				if (line.startsWith("Evaluated: true")) {
					GPRule sequencingRule;
					GPRule routingRule;

					line = br.readLine(); // read in fitness on following line
					fitnesses = readFitnessFromLine(line, isMultiObjective);

					//fzhang  2018.11.4   for NSGA-II
//	                //mengxu need to hide for MOEAD
//					br.readLine(); //Rank: 0
//					br.readLine(); //Sparsity: Infinity
					
					br.readLine(); // tree 0
					line = br.readLine(); // this is a sequencing rule

					// sequencing rule
					line = LispSimplifier.simplifyExpression(line);
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
					result.addGenerationalValidationFitnesses((Fitness) fitness.clone());
					result.addGenerationalTestFitnesses((Fitness) fitness.clone());
					line = br.readLine();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	public static TestResult readNSGAIITrainResultFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                           int numTrees) {
		TestResult result = new TestResult();

		String line;
		Fitness fitnesses;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			while (!(line = br.readLine()).equals(" PARETO FRONTS")) {

				if (line.startsWith("Generation")) {
					br.readLine(); // Best individual:

					GPRule sequencingRule;
					GPRule routingRule;

					br.readLine(); // Subpopulation 0:
					br.readLine(); // Evaluated: true

					line = br.readLine(); // read in fitness on following line
					fitnesses = readFitnessFromLine(line, isMultiObjective);

					//fzhang  2018.11.4   for NSGA-II
					br.readLine(); //Rank: 0
					br.readLine(); //Sparsity: Infinity

					br.readLine(); // tree 0
					line = br.readLine(); // this is a sequencing rule

					// sequencing rule
//					line = LispSimplifier.simplifyExpression(line);
					sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);

					// routing rule
					br.readLine();
					line = br.readLine();
//					line = LispSimplifier.simplifyExpression(line);
					routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

					Fitness fitness = fitnesses;
					GPRule[] bestRules = new GPRule[numTrees];

					bestRules[0] = sequencingRule; // sequencing rule
					bestRules[1] = routingRule; // routing rule

					result.setBestRules(bestRules);
					result.setBestTrainingFitness(fitness);

					result.addGenerationalRules(bestRules);
					result.addGenerationalTrainFitness(fitness);
//					result.addGenerationalValidationFitnesses((Fitness) fitness.clone());
					result.addGenerationalTestFitnesses((Fitness) fitness.clone());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	//add by mengxu 2022.08.11 to get test result for not only the final generation
	public static ParetoFrontTestResult readNSGAIIParetoFrontForGenerationFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                                                   int numTrees) {
		ParetoFrontTestResult result = new ParetoFrontTestResult();

		String line;
		Fitness fitnesses;

		int currentGeneration = 0;//modified by mengxu 2022.08.11

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			//read the file until arriveing the position 'Pareto Front of Subpopulation 0'
			line = br.readLine();
			while(line != null){
//			while(currentGeneration != 101){//the 101 is suitable for 500 generations and each 5 get a Pareto front
				while(!(line.equals("Pareto Front of Subpopulation 0"))) {
					line = br.readLine();
				}
				//the output here is line = Pareto Front of Subpopulation 0
				line = br.readLine();
				List<GPRule[]> bestParetoFrontRules = new ArrayList<>();
				List<Fitness> bestTrainingParetoFrontFitness = new ArrayList<>();
				List<Fitness> bestValidationParetoFrontFitnesses = new ArrayList<>();
				List<Fitness> bestTestParetoFrontFitnesses = new ArrayList<>();


				while(!Objects.equals(line, "") && line != null) {
					if (line.startsWith("Evaluated: true")) {
						GPRule sequencingRule;
						GPRule routingRule;

						line = br.readLine(); // read in fitness on following line
						fitnesses = readFitnessFromLine(line, isMultiObjective);

						//fzhang  2018.11.4   for NSGA-II and SPEA2
//	                    //mengxu need to hide for MOEAD
//						br.readLine(); //Rank: 0
//						br.readLine(); //Sparsity: Infinity

						br.readLine(); // tree 0
						line = br.readLine(); // this is a sequencing rule

						// sequencing rule
						line = LispSimplifier.simplifyExpression(line);
						sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);

						// routing rule
						br.readLine();
						line = br.readLine();
						routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

						Fitness fitness = fitnesses;
						GPRule[] bestRules = new GPRule[numTrees];

						bestRules[0] = sequencingRule; // sequencing rule
						bestRules[1] = routingRule; // routing rule

						bestParetoFrontRules.add(bestRules);
						bestTrainingParetoFrontFitness.add(fitness);
						bestValidationParetoFrontFitnesses.add((Fitness) fitness.clone());
						bestTestParetoFrontFitnesses.add((Fitness) fitness.clone());
						line = br.readLine();
					}
				}
				result.setBestParetoFrontRules(bestParetoFrontRules);
				result.setBestTrainingParetoFrontFitness(bestTrainingParetoFrontFitness);

				result.addGenerationalParetoFrontRules(bestParetoFrontRules);
				result.addGenerationalTrainParetoFrontFitness(bestTrainingParetoFrontFitness);
				result.addGenerationalValidationParetoFrontFitnesses(bestValidationParetoFrontFitnesses);
				result.addGenerationalTestParetoFrontFitnesses(bestTestParetoFrontFitnesses);
//
				currentGeneration++;
			}



//			//read the file until arriveing the position 'Pareto Front of Subpopulation 0'
//			line = br.readLine();
//			while(!(line.equals("Pareto Front of Subpopulation 0")) && currentGeneration != 499) {
//				line = br.readLine();
//				List<GPRule[]> bestParetoFrontRules = new ArrayList<>();
//				List<Fitness> bestTrainingParetoFrontFitness = new ArrayList<>();
//				List<Fitness> bestValidationParetoFrontFitnesses = new ArrayList<>();
//				List<Fitness> bestTestParetoFrontFitnesses = new ArrayList<>();
//				if (line.startsWith("Evaluated: true")) {
//					GPRule sequencingRule;
//					GPRule routingRule;
//
//					line = br.readLine(); // read in fitness on following line
//					fitnesses = readFitnessFromLine(line, isMultiObjective);
//
//					//fzhang  2018.11.4   for NSGA-II
////	                //mengxu need to hide for MOEAD
//					br.readLine(); //Rank: 0
//					br.readLine(); //Sparsity: Infinity
//
//					br.readLine(); // tree 0
//					line = br.readLine(); // this is a sequencing rule
//
//					// sequencing rule
//					line = LispSimplifier.simplifyExpression(line);
//					sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);
//
//					// routing rule
//					br.readLine();
//					line = br.readLine();
//					routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);
//
//					Fitness fitness = fitnesses;
//					GPRule[] bestRules = new GPRule[numTrees];
//
//					bestRules[0] = sequencingRule; // sequencing rule
//					bestRules[1] = routingRule; // routing rule
//
//					bestParetoFrontRules.add(bestRules);
//					bestTrainingParetoFrontFitness.add(fitness);
//					bestValidationParetoFrontFitnesses.add((Fitness) fitness.clone());
//					bestTestParetoFrontFitnesses.add((Fitness) fitness.clone());
//					line = br.readLine();
//				}
//				result.setBestParetoFrontRules(bestParetoFrontRules);
//				result.setBestTrainingParetoFrontFitness(bestTrainingParetoFrontFitness);
//
//				result.addGenerationalParetoFrontRules(bestParetoFrontRules);
//				result.addGenerationalTrainParetoFrontFitness(bestTrainingParetoFrontFitness);
//				result.addGenerationalValidationParetoFrontFitnesses(bestValidationParetoFrontFitnesses);
//				result.addGenerationalTestParetoFrontFitnesses(bestTestParetoFrontFitnesses);
//
//				currentGeneration++;
//			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}
	
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

	private static Fitness readFitnessFromLine(String line, boolean isMultiobjective) {
		if (isMultiobjective) {
			// TODO read multi-objective fitness line
			/*String[] spaceSegments = line.split("\\s+");//\\s��ʾ   �ո�,�س�,���еȿհ׷�, +�ű�ʾһ����������˼
			String[] equation = spaceSegments[1].split("=");
			double fitness = Double.valueOf(equation[1]);
			KozaFitness f = new KozaFitness();
			f.setStandardizedFitness(null, fitness);*/
			
			//save objective from training fzhang 18.11.2018
			/*String[] spaceSegments = line.split("\\s+");//\\s��ʾ   �ո�,�س�,���еȿհ׷�, +�ű�ʾһ����������˼
			String[] equation1 = spaceSegments[1].split("\\[|\\]");
			String[] equation2 = spaceSegments[2].split("\\[|\\]");
			double fitness1 = Double.valueOf(equation1[1]);
			double fitness2 = Double.valueOf(equation2[0]);
			MultiObjectiveFitness f = new MultiObjectiveFitness();
			f.objectives = new double[2];
			f.objectives[0] = fitness1;
			f.objectives[1] = fitness2;

			return f;*/
			return parseFitness(line);
			 
		} else {
			String[] spaceSegments = line.split("\\s+"); // . �� | �� * ��ת���ַ�������ü� \\��
			String[] fitVec = spaceSegments[1].split("\\[|\\]");//����ָ����������� | ��Ϊ���ַ���
			double fitness = Double.valueOf(fitVec[1]);
			MultiObjectiveFitness f = new MultiObjectiveFitness();
			f.objectives = new double[1];
			f.objectives[0] = fitness;

			return f;
		}
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
    
}
