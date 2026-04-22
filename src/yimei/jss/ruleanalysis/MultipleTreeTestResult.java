package yimei.jss.ruleanalysis;

import java.io.File;

public class MultipleTreeTestResult extends TestResult{
	public static TestResult readFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by fzhang 24.5.2018   for multiple trees of one individual
		return MultipleTreeResultFileReader.readTestResultFromFile(file, ruleType, ruleType.isMultiobjective(),
				numTrees);
	}

	public static TestResult readSubPopFromFile(File file, RuleTypeV2 ruleType, int numTrees, int subpop) {

		// modified by fzhang 24.5.2018   for multiple trees of one individual
		return MultipleTreeResultFileReader.readSubPopTestResultFromFile(file, ruleType, ruleType.isMultiobjective(),
				numTrees, subpop);
	}

	public static TestResult readEnsembleContributionFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by mengxu 2023.02.28   for multiple trees of one individual
		return MultipleTreeResultFileReader.readEnsembleContributionTestResultFromFile(file, ruleType, ruleType.isMultiobjective(),
				numTrees);
	}

	public static TestResult readMAPElitesFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by mengxu 2023.02.28   for multiple trees of one individual
		return MultipleTreeResultFileReader.readMAPElitesTestResultFromFile(file, ruleType, ruleType.isMultiobjective(),
				numTrees);
	}

	public static TestResult readEnsembleContributionAndSpreadFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by mengxu 2023.02.28   for multiple trees of one individual
		return MultipleTreeResultFileReader.readEnsembleContributionAndSpreadTestResultFromFile(file, ruleType, ruleType.isMultiobjective(),
				numTrees);
	}

	public static TestResult readMOFromFile(File file, RuleTypeV2 ruleType, int numTrees) {

		// modified by fzhang 24.5.2018   for multiple trees of one individual
		return MultipleTreeResultFileReader.readTestResultFromFile(file, ruleType, true,
				numTrees);
	}

	public static TestResult readEnsembleFromFile(File file, RuleTypeV2 ruleType, int numTrees){
		return MultipleTreeResultFileReader.readEnsembleTestResultFromFile(file, ruleType, ruleType.isMultiobjective(),
				numTrees);
	}
}
