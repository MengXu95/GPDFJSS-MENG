package mengxu.ruleanalysis.MPSLGP;


import yimei.jss.ruleanalysis.RuleTypeV2;
import yimei.jss.ruleanalysis.TestResult;

import java.io.File;

public class ParetoSetLearningMultipleTreeTestResult extends TestResult {
	public static TestResult readFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by fzhang 24.5.2018   for multiple trees of one individual
		return ParetoSetLearningMultipleTreeResultFileReader.readTestResultFromFile(file, ruleType, ruleType.isMultiobjective(),
				numTrees);
	}

	public static TestResult readTrainResultFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by fzhang 24.5.2018   for multiple trees of one individual
		return ParetoSetLearningMultipleTreeResultFileReader.readParetoSetLearningTrainResultFromFile(file, ruleType, true,
				numTrees);
	}

	//modified by mengxu 2021.08.05
	public static TestResult readParetoFrontFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by fzhang 24.5.2018   for multiple trees of one individual
		return ParetoSetLearningMultipleTreeResultFileReader.readParetoSetLearningParetoFrontFromFile(file, ruleType, true,
				numTrees);
	}

	public static ParetoSetLearningParetoFrontTestResult readParetoFrontTestResultFromFile(File file, RuleTypeV2 ruleType, int numTrees, int numPreference){
		return ParetoSetLearningMultipleTreeResultFileReader.readParetoSetLearningParetoFrontForGenerationFromFile(file, ruleType, true,
				numTrees, numPreference);
	}

	public static ParetoSetLearningParetoFrontTestResult readParetoFrontTestResultTopNFromFile(File file, RuleTypeV2 ruleType, int numTrees, int numPreference, int topN){
		return ParetoSetLearningMultipleTreeResultFileReader.readParetoSetLearningParetoFrontForGenerationTopNFromFile(file, ruleType, true,
				numTrees, numPreference, topN);
	}

	public static ParetoSetLearningParetoFrontTestResult readParetoFrontTestResultTopNEnsembleFromFile(File file, RuleTypeV2 ruleType, int numTrees, int numPreference, int topN){
		return ParetoSetLearningMultipleTreeResultFileReader.readParetoSetLearningParetoFrontForGenerationTopNEnsembleFromFile(file, ruleType, true,
				numTrees, numPreference, topN);
	}

	public static ParetoSetLearningParetoFrontTestResult readParetoFrontFixedStructureTestResultFromFile(File file, RuleTypeV2 ruleType, int numTrees, int numPreference){
		return ParetoSetLearningMultipleTreeResultFileReader.readParetoSetLearningFixedStructureParetoFrontForGenerationFromFile(file, ruleType, true,
				numTrees, numPreference);
	}
}
