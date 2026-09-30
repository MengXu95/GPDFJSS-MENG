package mengxu.ruleanalysis.MPSLGP;

import ec.Fitness;
import ec.multiobjective.MultiObjectiveFitness;
import mengxu.algorithm.ensemble.EnsembleRule;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import yimei.jss.gp.terminal.JobShopAttribute;
import yimei.jss.rule.RuleType;
import yimei.jss.rule.operation.evolved.GPRule;
import yimei.jss.ruleanalysis.ResultFileReader;
import yimei.jss.ruleanalysis.RuleTypeV2;
import yimei.jss.ruleanalysis.TestResult;
import yimei.util.lisp.LispSimplifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParetoSetLearningMultipleTreeResultFileReader extends ResultFileReader {

	/** The first training-ranked individual of one task, with Tree 0 sequencing and Tree 1 routing. */
	public static final class SelectedTaskHeuristic {
		private final int taskIndex;
		private final int generation;
		private final GPRule sequencingRule;
		private final GPRule routingRule;
		private final double[] trainingObjectives;

		private SelectedTaskHeuristic(int taskIndex, int generation, GPRule sequencingRule,
									 GPRule routingRule, double[] trainingObjectives) {
			this.taskIndex = taskIndex;
			this.generation = generation;
			this.sequencingRule = sequencingRule;
			this.routingRule = routingRule;
			this.trainingObjectives = trainingObjectives.clone();
		}

		public int getTaskIndex() { return taskIndex; }
		public int getGeneration() { return generation; }
		public GPRule getSequencingRule() { return sequencingRule; }
		public GPRule getRoutingRule() { return routingRule; }
		public double[] getTrainingObjectives() { return trainingObjectives.clone(); }
	}

	/**
	 * Reads rank zero in the final "Top N individuals by MPSLGP Task:" section.
	 * The writer's training ranking is authoritative; neither generation-best records,
	 * Pareto fronts nor test scores are candidates. Expressions are not simplified.
	 *
	 * All task blocks and their declared positive individual counts are validated, including
	 * nonselected individuals. Task labels must be unique and contiguous from zero, and must
	 * match the final generation's best-task labels when those are present. The section ends
	 * at the task Pareto-front heading or EOF after complete records.
	 *
	 * @throws IOException if the file cannot be read, the final generation differs from
	 *                     expectedGeneration, or the final selection section is missing,
	 *                     malformed, ambiguous or lacks the requested task
	 * @throws IllegalArgumentException if file is null or either index is negative
	 */
	public static SelectedTaskHeuristic readFinalTaskHeuristic(Path file, int taskIndex,
															 int expectedGeneration) throws IOException {
		if (file == null || taskIndex < 0 || expectedGeneration < 0) {
			throw new IllegalArgumentException("A file and nonnegative task/generation indices are required.");
		}
		return new FinalTaskHeuristicReader(file).read(taskIndex, expectedGeneration);
	}

	private static final class FinalTaskHeuristicReader {
		private static final String TOP = "Top N individuals by MPSLGP Task:";
		private static final String FRONTS = "PARETO FRONTS BY MPSLGP TASK";
		private static final Pattern GENERATION = Pattern.compile("Generation: ([0-9]+)");
		private static final Pattern TASK = Pattern.compile("MPSLGP Task ([0-9]+) Top ([0-9]+) individuals:");
		private static final Pattern BEST_TASK = Pattern.compile("MPSLGP Task ([0-9]+):(?: no individuals)?");
		private static final Pattern FITNESS = Pattern.compile("Fitness: \\[([^\\[\\]]+)\\]");
		private final Path file;
		private final List<String> lines;
		private int position;

		private FinalTaskHeuristicReader(Path file) throws IOException {
			this.file = file;
			this.lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		}

		private IOException invalid(String message) {
			return new IOException(file + ":" + position + ": " + message);
		}

		private int integer(String value) throws IOException {
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				throw new IOException(file + ":" + position + ": Integer out of range: " + value, e);
			}
		}

		private SelectedTaskHeuristic read(int requestedTask, int expectedGeneration) throws IOException {
			int generation = -1;
			int generationStart = -1;
			int topStart = -1;
			while (position < lines.size()) {
				String line = lines.get(position++).trim();
				if (line.startsWith("Generation")) {
					Matcher match = GENERATION.matcher(line);
					if (!match.matches()) {
						throw invalid("Malformed generation header.");
					}
					int nextGeneration = integer(match.group(1));
					if (nextGeneration <= generation) {
						throw invalid("Duplicate or out-of-order generation.");
					}
					generation = nextGeneration;
					generationStart = position;
					topStart = -1;
				} else if (line.startsWith("Top N individuals")) {
					if (!TOP.equals(line) || generation < 0 || topStart >= 0) {
						throw invalid("Malformed, misplaced or duplicate Top N section.");
					}
					topStart = position;
				}
			}
			if (generation != expectedGeneration) {
				throw invalid("Expected final generation " + expectedGeneration + ", found " + generation + ".");
			}
			if (topStart < 0) {
				throw invalid("Missing Top N section in final generation.");
			}

			Set<Integer> bestTasks = new HashSet<>();
			position = generationStart;
			while (position < topStart - 1) {
				String line = lines.get(position++).trim();
				if (line.startsWith("MPSLGP Task ")) {
					Matcher match = BEST_TASK.matcher(line);
					if (!match.matches() || !bestTasks.add(integer(match.group(1)))) {
						throw invalid("Malformed or duplicate final generation task label.");
					}
				} else if (line.startsWith("PARETO FRONTS")) {
					throw invalid("Final Top N section must precede Pareto fronts.");
				}
			}

			Set<Integer> tasks = new HashSet<>();
			SelectedTaskHeuristic selected = null;
			position = topStart;
			String line;
			while ((line = nextNonBlank()) != null && !FRONTS.equals(line)) {
				Matcher match = TASK.matcher(line);
				if (!match.matches()) {
					throw invalid("Expected a task Top N header; unexpected record or individual count mismatch.");
				}
				int task = integer(match.group(1));
				int count = integer(match.group(2));
				if (!tasks.add(task)) {
					throw invalid("Duplicate task block " + task + ".");
				}
				if (count <= 0) {
					throw invalid("Task " + task + " must have a positive Top N count.");
				}
				for (int rank = 0; rank < count; rank++) {
					expect("Evaluated: true");
					double[] objectives = objectives();
					expect("Tree 0:");
					GPRule sequencing = rule(RuleType.SEQUENCING);
					expect("Tree 1:");
					GPRule routing = rule(RuleType.ROUTING);
					if (task == requestedTask && rank == 0) {
						selected = new SelectedTaskHeuristic(task, generation, sequencing, routing, objectives);
					}
				}
			}
			for (int task = 0; task < tasks.size(); task++) {
				if (!tasks.contains(task)) {
					throw invalid("Task labels must be contiguous from zero.");
				}
			}
			if (!bestTasks.isEmpty() && !tasks.equals(bestTasks)) {
				throw invalid("Top N task labels do not match final generation task labels.");
			}
			if (selected == null) {
				throw invalid("Missing requested task " + requestedTask + " in final Top N section.");
			}
			while ((line = nextNonBlank()) != null) {
				if (line.startsWith("MPSLGP Task ")) {
					throw invalid("Misplaced task block after final Top N section.");
				}
			}
			return selected;
		}

		private String nextNonBlank() {
			while (position < lines.size()) {
				String line = lines.get(position++).trim();
				if (!line.isEmpty()) {
					return line;
				}
			}
			return null;
		}

		private void expect(String expected) throws IOException {
			String line = nextNonBlank();
			if (!expected.equals(line)) {
				throw invalid("Expected '" + expected + "', found " + (line == null ? "EOF" : "'" + line + "'") + ".");
			}
		}

		private double finiteDouble(String value) throws IOException {
			try {
				double number = Double.parseDouble(value);
				if (!Double.isFinite(number)) {
					throw invalid("Nonfinite number: " + value);
				}
				return number;
			} catch (NumberFormatException e) {
				throw new IOException(file + ":" + position + ": Invalid number: " + value, e);
			}
		}

		private double[] objectives() throws IOException {
			String line = nextNonBlank();
			Matcher match = FITNESS.matcher(line == null ? "" : line);
			if (!match.matches()) {
				throw invalid("Expected Fitness: [objective0 objective1].");
			}
			String[] values = match.group(1).trim().split("\\s+");
			if (values.length != 2) {
				throw invalid("Expected exactly two training objectives.");
			}
			return new double[]{finiteDouble(values[0]), finiteDouble(values[1])};
		}

		private GPRule rule(RuleType type) throws IOException {
			String expression = nextNonBlank();
			if (expression == null) {
				throw invalid("Missing " + type + " tree expression.");
			}
			String[] tokens = expression.replace("(", " ( ").replace(")", " ) ").trim().split("\\s+");
			int[] cursor = {0};
			String canonical = expression(tokens, cursor);
			if (cursor[0] != tokens.length) {
				throw invalid("Extra tokens after tree expression.");
			}
			// LispParser assumes valid arity and balanced parentheses and can hang otherwise.
			GPRule parsed = GPRule.readFromLispExpression(type, canonical);
			return new GPRule(type, parsed.getGPTree(), expression);
		}

		private String expression(String[] tokens, int[] cursor) throws IOException {
			if (cursor[0] == tokens.length) {
				throw invalid("Incomplete tree expression.");
			}
			String token = tokens[cursor[0]++];
			if ("(".equals(token)) {
				if (cursor[0] == tokens.length) {
					throw invalid("Missing tree function.");
				}
				String function = tokens[cursor[0]++];
				int arity;
				switch (function) {
					case "+": case "-": case "*": case "/": case "Max": case "Min":
						arity = 2;
						break;
					case "if":
						arity = 3;
						break;
					default:
						throw invalid("Unknown tree function: " + function);
				}
				StringBuilder result = new StringBuilder("(").append(function);
				for (int child = 0; child < arity; child++) {
					result.append(' ').append(expression(tokens, cursor));
				}
				if (cursor[0] == tokens.length || !")".equals(tokens[cursor[0]++])) {
					throw invalid("Wrong function arity or missing closing parenthesis.");
				}
				return result.append(')').toString();
			}
			if (JobShopAttribute.get(token) == null) {
				if (!NumberUtils.isNumber(token)) {
					throw invalid("Unknown tree terminal: " + token);
				}
				finiteDouble(token);
			}
			return token;
		}
	}

	private static class ParsedMPSLGPIndividual {
		private final GPRule[] rules;
		private final Fitness fitness;

		private ParsedMPSLGPIndividual(GPRule[] rules, Fitness fitness) {
			this.rules = rules;
			this.fitness = fitness;
		}
	}

	private static int parseGeneration(String line) {
		String[] splitLine = line.split(" ");
		return Integer.valueOf(splitLine[1]);
	}

	private static int parseMPSLGPTaskIndex(String line) {
		String marker = "MPSLGP Task ";
		int start = line.indexOf(marker);
		if (start < 0) {
			return -1;
		}
		start += marker.length();
		int end = start;
		while (end < line.length() && Character.isDigit(line.charAt(end))) {
			end++;
		}
		return Integer.valueOf(line.substring(start, end));
	}

	private static ParsedMPSLGPIndividual readMPSLGPIndividual(BufferedReader br,
													 boolean isMultiObjective,
													 int numTrees) throws IOException {
		String line = br.readLine();
		Fitness fitness = readFitnessFromLine(line, isMultiObjective);

		br.readLine();
		line = br.readLine();
		GPRule sequencingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING,
				LispSimplifier.simplifyExpression(line));

		GPRule routingRule = null;
		if (numTrees == 2) {
			br.readLine();
			line = br.readLine();
			routingRule = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);
		}

		GPRule[] rules = new GPRule[numTrees];
		rules[0] = sequencingRule;
		if (numTrees == 2) {
			rules[1] = routingRule;
		}
		return new ParsedMPSLGPIndividual(rules, fitness);
	}

	private static void addIndividualForAllPreferences(ParetoSetLearningParetoFrontTestResult result,
													 List<GPRule[]> rulesForGeneration,
													 List<Fitness> trainFitnessForGeneration,
													 List<Fitness> validationFitnessForGeneration,
													 List<Fitness> testFitnessForGeneration,
													 List<Integer> taskIndicesForGeneration,
													 ParsedMPSLGPIndividual individual,
													 int taskIndex,
													 int numPreference) {
		for (int preferenceIndex = 0; preferenceIndex < numPreference; preferenceIndex++) {
			rulesForGeneration.add(individual.rules);
			trainFitnessForGeneration.add(individual.fitness);
			validationFitnessForGeneration.add((Fitness) individual.fitness.clone());
			testFitnessForGeneration.add((Fitness) individual.fitness.clone());
			taskIndicesForGeneration.add(taskIndex);
		}
	}


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


	public static TestResult readParetoSetLearningParetoFrontFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
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
					br.readLine(); //Rank: 0
					br.readLine(); //Sparsity: Infinity
					
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

	public static TestResult readParetoSetLearningTrainResultFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
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
	public static ParetoSetLearningParetoFrontTestResult readParetoSetLearningParetoFrontForGenerationTopNFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                                                                                   int numTrees, int numPreference, int topN) {
		ParetoSetLearningParetoFrontTestResult result = new ParetoSetLearningParetoFrontTestResult();

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			int currentGeneration = -1;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("Generation:")) {
					currentGeneration = parseGeneration(line);
				}

				if (!line.equals("Top N individuals by MPSLGP Task:")) {
					continue;
				}

				List<GPRule[]> bestParetoFrontRules = new ArrayList<>();
				List<Fitness> bestTrainingParetoFrontFitness = new ArrayList<>();
				List<Fitness> bestValidationParetoFrontFitnesses = new ArrayList<>();
				List<Fitness> bestTestParetoFrontFitnesses = new ArrayList<>();
				List<Integer> taskIndices = new ArrayList<>();
				int currentTaskIndex = -1;
				int individualsReadForTask = 0;

				while ((line = br.readLine()) != null) {
					if (line.startsWith("Generation:") || line.startsWith(" PARETO FRONTS")) {
						break;
					}
					if (line.trim().isEmpty()) {
						continue;
					}
					if (line.startsWith("MPSLGP Task ") && line.contains("Top")) {
						currentTaskIndex = parseMPSLGPTaskIndex(line);
						individualsReadForTask = 0;
						continue;
					}
					if (line.startsWith("Evaluated: true") && currentTaskIndex >= 0 && individualsReadForTask < topN) {
						ParsedMPSLGPIndividual individual = readMPSLGPIndividual(br, isMultiObjective, numTrees);
						addIndividualForAllPreferences(result, bestParetoFrontRules, bestTrainingParetoFrontFitness,
								bestValidationParetoFrontFitnesses, bestTestParetoFrontFitnesses, taskIndices,
								individual, currentTaskIndex, numPreference);
						individualsReadForTask++;
					}
				}

				if (!bestParetoFrontRules.isEmpty()) {
					result.setBestParetoFrontRules(bestParetoFrontRules);
					result.setBestTrainingParetoFrontFitness(bestTrainingParetoFrontFitness);
					result.addGenerationalGenerationNumber(currentGeneration);
					result.addGenerationalTaskIndices(taskIndices);
					result.addGenerationalParetoFrontRules(bestParetoFrontRules);
					result.addGenerationalTrainParetoFrontFitness(bestTrainingParetoFrontFitness);
					result.addGenerationalValidationParetoFrontFitnesses(bestValidationParetoFrontFitnesses);
					result.addGenerationalTestParetoFrontFitnesses(bestTestParetoFrontFitnesses);
				}

				if (line == null) {
					break;
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	//add by mengxu 2022.08.11 to get test result for not only the final generation
	public static ParetoSetLearningParetoFrontTestResult readParetoSetLearningParetoFrontForGenerationTopNEnsembleFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                                                                                           int numTrees, int numPreference, int topN) {
		ParetoSetLearningParetoFrontTestResult result = new ParetoSetLearningParetoFrontTestResult();

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			int currentGeneration = -1;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("Generation:")) {
					currentGeneration = parseGeneration(line);
				}

				if (!line.equals("Top N individuals by MPSLGP Task:")) {
					continue;
				}

				List<EnsembleRule> preferenceEnsembleRule = new ArrayList<>();
				List<List<Double>> preferenceEnsembleContributionTest = new ArrayList<>();
				List<List<Fitness>> preferenceEnsembleTrainFitnessAll = new ArrayList<>();
				List<Fitness> preferenceEnsembleTestFitness = new ArrayList<>();
				List<Integer> taskIndices = new ArrayList<>();
				int currentTaskIndex = -1;
				int individualsReadForTask = 0;
				List<GPRule> ensembleSequencingRule = new ArrayList<>();
				List<GPRule> ensembleRoutingRule = new ArrayList<>();
				List<Fitness> preferenceEnsembleTrainFitness = new ArrayList<>();
				Fitness trainFitness = null;

				while ((line = br.readLine()) != null) {
					if (line.startsWith("Generation:") || line.startsWith(" PARETO FRONTS")) {
						break;
					}
					if (line.trim().isEmpty()) {
						continue;
					}
					if (line.startsWith("MPSLGP Task ") && line.contains("Top")) {
						currentTaskIndex = parseMPSLGPTaskIndex(line);
						individualsReadForTask = 0;
						ensembleSequencingRule = new ArrayList<>();
						ensembleRoutingRule = new ArrayList<>();
						preferenceEnsembleTrainFitness = new ArrayList<>();
						trainFitness = null;
						continue;
					}
					if (line.startsWith("Evaluated: true") && currentTaskIndex >= 0 && individualsReadForTask < topN) {
						ParsedMPSLGPIndividual individual = readMPSLGPIndividual(br, isMultiObjective, numTrees);
						ensembleSequencingRule.add(individual.rules[0]);
						if (numTrees == 2) {
							ensembleRoutingRule.add(individual.rules[1]);
						}
						trainFitness = (Fitness) individual.fitness.clone();
						preferenceEnsembleTrainFitness.add(trainFitness);
						individualsReadForTask++;

						if (individualsReadForTask == topN) {
							for (int preferenceIndex = 0; preferenceIndex < numPreference; preferenceIndex++) {
								preferenceEnsembleTrainFitnessAll.add(preferenceEnsembleTrainFitness);
								preferenceEnsembleTestFitness.add((Fitness) trainFitness.clone());
								preferenceEnsembleRule.add(new EnsembleRule(ensembleSequencingRule, ensembleRoutingRule));
								preferenceEnsembleContributionTest.add(new ArrayList<>());
								taskIndices.add(currentTaskIndex);
							}
						}
					}
				}

				if (!preferenceEnsembleRule.isEmpty()) {
					result.addGenerationalGenerationNumber(currentGeneration);
					result.addGenerationalTaskIndices(taskIndices);
					result.addGenerationalEnsembleRules(preferenceEnsembleRule);
					result.addGenerationalEnsembleTrainFitness(preferenceEnsembleTrainFitnessAll);
					result.addGenerationalEnsembleTestFitnesses(preferenceEnsembleTestFitness);
					result.addGenerationalTestEnsembleContribution(preferenceEnsembleContributionTest);
				}

				if (line == null) {
					break;
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}


	//add by mengxu 2022.08.11 to get test result for not only the final generation
	public static ParetoSetLearningParetoFrontTestResult readParetoSetLearningParetoFrontForGenerationFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                                                                               int numTrees, int numPreference) {
		ParetoSetLearningParetoFrontTestResult result = new ParetoSetLearningParetoFrontTestResult();

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			int currentGeneration = -1;
			while ((line = br.readLine()) != null) {
				if (!line.startsWith("Generation:")) {
					continue;
				}
				currentGeneration = parseGeneration(line);
				line = br.readLine();
				if (!"Best Individual by MPSLGP Task:".equals(line)) {
					continue;
				}

				List<GPRule[]> bestParetoFrontRules = new ArrayList<>();
				List<Fitness> bestTrainingParetoFrontFitness = new ArrayList<>();
				List<Fitness> bestValidationParetoFrontFitnesses = new ArrayList<>();
				List<Fitness> bestTestParetoFrontFitnesses = new ArrayList<>();
				List<Integer> taskIndices = new ArrayList<>();
				int currentTaskIndex = -1;

				while ((line = br.readLine()) != null) {
					if (line.startsWith("Top N individuals") || line.startsWith("Generation:")) {
						break;
					}
					if (line.trim().isEmpty()) {
						continue;
					}
					if (line.startsWith("MPSLGP Task ")) {
						currentTaskIndex = parseMPSLGPTaskIndex(line);
						continue;
					}
					if (line.startsWith("Evaluated: true") && currentTaskIndex >= 0) {
						ParsedMPSLGPIndividual individual = readMPSLGPIndividual(br, isMultiObjective, numTrees);
						addIndividualForAllPreferences(result, bestParetoFrontRules, bestTrainingParetoFrontFitness,
								bestValidationParetoFrontFitnesses, bestTestParetoFrontFitnesses, taskIndices,
								individual, currentTaskIndex, numPreference);
					}
				}

				if (!bestParetoFrontRules.isEmpty()) {
					result.setBestParetoFrontRules(bestParetoFrontRules);
					result.setBestTrainingParetoFrontFitness(bestTrainingParetoFrontFitness);
					result.addGenerationalGenerationNumber(currentGeneration);
					result.addGenerationalTaskIndices(taskIndices);
					result.addGenerationalParetoFrontRules(bestParetoFrontRules);
					result.addGenerationalTrainParetoFrontFitness(bestTrainingParetoFrontFitness);
					result.addGenerationalValidationParetoFrontFitnesses(bestValidationParetoFrontFitnesses);
					result.addGenerationalTestParetoFrontFitnesses(bestTestParetoFrontFitnesses);
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	public static ParetoSetLearningParetoFrontTestResult readParetoSetLearningFixedStructureParetoFrontForGenerationFromFile(File file, RuleTypeV2 ruleType, boolean isMultiObjective,
                                                                                                                             int numTrees, int numPreference) {
		ParetoSetLearningParetoFrontTestResult result = new ParetoSetLearningParetoFrontTestResult();

		String line;
		Fitness fitnesses;

//		int currentGeneration = 0;//modified by mengxu 2022.08.11
		int printGap = 10;

		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			//read the file until arriveing the position 'Pareto Front of Subpopulation 0'
			line = br.readLine();
			while(!(line = br.readLine()).equals("Generation: 100")){
				//the output here is line = Pareto Front of Subpopulation 0
//				line = br.readLine();
				if (line.startsWith("Generation")) {
					String[] splitLine = line.split(" ");
					int gen = Integer.valueOf(splitLine[1]);
					if(gen%printGap == 0){
						List<GPRule[]> bestParetoFrontRules = new ArrayList<>();
						List<Fitness> bestTrainingParetoFrontFitness = new ArrayList<>();
						List<Fitness> bestValidationParetoFrontFitnesses = new ArrayList<>();
						List<Fitness> bestTestParetoFrontFitnesses = new ArrayList<>();
						line = br.readLine(); //Best Individual:
						line = br.readLine(); //Subpopulation 0:
						line = br.readLine(); //Evaluated: true
						line = br.readLine();
						fitnesses = readFitnessFromLine(line, isMultiObjective);

						br.readLine(); // tree 0
						line = br.readLine(); // this is a sequencing rule

						GPRule sequencingRule_0;
						GPRule sequencingRule_1;
						GPRule routingRule_0;
						GPRule routingRule_1;

						// sequencing rule 0
						line = LispSimplifier.simplifyExpression(line);
						sequencingRule_0 = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);

						// sequencing rule 1
						br.readLine(); // tree 1
						line = br.readLine();
						sequencingRule_1 = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);

						// routing rule 0
						br.readLine(); // tree 2
						line = br.readLine();
						routingRule_0 = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

						// routing rule 1
						br.readLine(); // tree 3
						line = br.readLine();
						routingRule_1 = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

						Fitness fitness = fitnesses;
						GPRule[] bestRules = new GPRule[numTrees];

						for(int t=0; t<numPreference; t++){
							bestRules[0] = sequencingRule_0; // sequencing rule
							bestRules[1] = sequencingRule_1; // sequencing rule
							bestRules[2] = routingRule_0; // routing rule
							bestRules[3] = routingRule_1; // routing rule

							bestParetoFrontRules.add(bestRules);
							bestTrainingParetoFrontFitness.add(fitness);
							bestValidationParetoFrontFitnesses.add((Fitness) fitness.clone());
							bestTestParetoFrontFitnesses.add((Fitness) fitness.clone());
						}
						result.setBestParetoFrontRules(bestParetoFrontRules);
						result.setBestTrainingParetoFrontFitness(bestTrainingParetoFrontFitness);

						result.addGenerationalParetoFrontRules(bestParetoFrontRules);
						result.addGenerationalTrainParetoFrontFitness(bestTrainingParetoFrontFitness);
						result.addGenerationalValidationParetoFrontFitnesses(bestValidationParetoFrontFitnesses);
						result.addGenerationalTestParetoFrontFitnesses(bestTestParetoFrontFitnesses);
					}

				}
			}

			List<GPRule[]> bestParetoFrontRules = new ArrayList<>();
			List<Fitness> bestTrainingParetoFrontFitness = new ArrayList<>();
			List<Fitness> bestValidationParetoFrontFitnesses = new ArrayList<>();
			List<Fitness> bestTestParetoFrontFitnesses = new ArrayList<>();
			if (line.startsWith("Generation")) {
				String[] splitLine = line.split(" ");
				int gen = Integer.valueOf(splitLine[1]);
				if(gen%printGap == 0){
					line = br.readLine(); //Best Individual:
					line = br.readLine(); //Subpopulation 0:
					line = br.readLine(); //Evaluated: true
					line = br.readLine();
					fitnesses = readFitnessFromLine(line, isMultiObjective);

					br.readLine(); // tree 0
					line = br.readLine(); // this is a sequencing rule

					GPRule sequencingRule_0;
					GPRule sequencingRule_1;
					GPRule routingRule_0;
					GPRule routingRule_1;

					// sequencing rule 0
					line = LispSimplifier.simplifyExpression(line);
					sequencingRule_0 = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);

					// sequencing rule 1
					br.readLine(); // tree 1
					line = br.readLine();
					sequencingRule_1 = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.SEQUENCING, line);

					// routing rule 0
					br.readLine(); // tree 2
					line = br.readLine();
					routingRule_0 = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

					// routing rule 1
					br.readLine(); // tree 3
					line = br.readLine();
					routingRule_1 = GPRule.readFromLispExpression(yimei.jss.rule.RuleType.ROUTING, line);

					Fitness fitness = fitnesses;
					GPRule[] bestRules = new GPRule[numTrees];

					for(int t=0; t<numPreference; t++){
						bestRules[0] = sequencingRule_0; // sequencing rule
						bestRules[1] = sequencingRule_1; // sequencing rule
						bestRules[2] = routingRule_0; // routing rule
						bestRules[3] = routingRule_1; // routing rule

						bestParetoFrontRules.add(bestRules);
						bestTrainingParetoFrontFitness.add(fitness);
						bestValidationParetoFrontFitnesses.add((Fitness) fitness.clone());
						bestTestParetoFrontFitnesses.add((Fitness) fitness.clone());
					}

//							line = br.readLine();
				}
//					}
			}
			result.setBestParetoFrontRules(bestParetoFrontRules);
			result.setBestTrainingParetoFrontFitness(bestTrainingParetoFrontFitness);

			result.addGenerationalParetoFrontRules(bestParetoFrontRules);
			result.addGenerationalTrainParetoFrontFitness(bestTrainingParetoFrontFitness);
			result.addGenerationalValidationParetoFrontFitnesses(bestValidationParetoFrontFitnesses);
			result.addGenerationalTestParetoFrontFitnesses(bestTestParetoFrontFitnesses);

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
