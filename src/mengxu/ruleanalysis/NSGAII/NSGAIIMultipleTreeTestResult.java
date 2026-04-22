package mengxu.ruleanalysis.NSGAII;

import yimei.jss.ruleanalysis.RuleTypeV2;
import yimei.jss.ruleanalysis.TestResult;

import java.io.File;

public class NSGAIIMultipleTreeTestResult extends TestResult {
	public static TestResult readFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by fzhang 24.5.2018   for multiple trees of one individual
		return NSGAIIMultipleTreeResultFileReader.readTestResultFromFile(file, ruleType, ruleType.isMultiobjective(),
				numTrees);
	}

	public static TestResult readTrainResultFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by fzhang 24.5.2018   for multiple trees of one individual
		return NSGAIIMultipleTreeResultFileReader.readNSGAIITrainResultFromFile(file, ruleType, true,
				numTrees);
	}

	//modified by mengxu 2021.08.05
	public static TestResult readParetoFrontFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by fzhang 24.5.2018   for multiple trees of one individual
		return NSGAIIMultipleTreeResultFileReader.readNSGAIIParetoFrontFromFile(file, ruleType, true,
				numTrees);
	}

	public static ParetoFrontTestResult readParetoFrontTestResultFromFile(File file, RuleTypeV2 ruleType, int numTrees){
		return mengxu.ruleanalysis.NSGAII.NSGAIIMultipleTreeResultFileReader.readNSGAIIParetoFrontForGenerationFromFile(file, ruleType, true,
				numTrees);
	}
}
