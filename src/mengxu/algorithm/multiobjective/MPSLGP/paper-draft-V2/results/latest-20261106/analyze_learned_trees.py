import argparse
import hashlib
import json
import runpy
from collections import Counter
from pathlib import Path

import javaproperties
import numpy as np
import pandas as pd
from pyparsing import nested_expr
import sympy as symbolic


ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "tree-case-study"
PAPER = ROOT.parent.parent
WORKSPACE = next(parent for parent in PAPER.parents if (parent / "GPDFJSS-MENG.iml").exists())
TESTS = WORKSPACE / "src/mengxu/ruleanalysis/MPSLGP/paper-tests-preference-v2/full/IGD/R4/run20"
OPERATORS = {"+", "-", "*", "/", "Min", "Max"}
MODULES = {
    "A": {"Expression": "(- (* NOR Ptwo) SL)", "Scenarios": [1, 3], "Colour": "#087F8C", "Label": "A: NOR*p2 - SL (shared S1/S3)"},
    "B": {"Expression": "(+ W Ptwo)", "Scenarios": [3, 5], "Colour": "#2563EB", "Label": "B: W + p2 (shared S3/S5)"},
    "U1": {"Expression": "(Max TIS (- WKR TRANT))", "Scenarios": [1], "Colour": "#B45309", "Label": "U1: Max(TIS, WR-TR) (S1 only)"},
    "U3": {"Expression": "(Min NIQ SL)", "Scenarios": [3], "Colour": "#A23B72", "Label": "U3: Min(NIQ, SL) (S3 only)"},
    "U5": {"Expression": "(+ (/ WKR Pone) (+ NIQ MWT))", "Scenarios": [5], "Colour": "#B24732", "Label": "U5: D(WR,p1) + NIQ + MWT (S5 only)"},
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def properties(path):
    with path.open("rb") as stream:
        return javaproperties.load(stream)


def freeze(value):
    if isinstance(value, str):
        assert value not in OPERATORS
        return value
    assert len(value) == 3 and value[0] in OPERATORS
    return (value[0], freeze(value[1]), freeze(value[2]))


def parse(expression):
    result = nested_expr().parse_string(expression, parse_all=True).as_list()
    assert len(result) == 1
    return freeze(result[0])


def prefix(tree):
    return tree if isinstance(tree, str) else "(" + tree[0] + " " + prefix(tree[1]) + " " + prefix(tree[2]) + ")"


def walk(tree, path="r"):
    yield path, tree
    if isinstance(tree, tuple):
        yield from walk(tree[1], path + ".0")
        yield from walk(tree[2], path + ".1")


def size(tree):
    return sum(1 for _ in walk(tree))


def evaluate(tree, values):
    if isinstance(tree, str):
        return values[tree]
    left = evaluate(tree[1], values)
    right = evaluate(tree[2], values)
    if tree[0] == "+": return left + right
    if tree[0] == "-": return left - right
    if tree[0] == "*": return left * right
    if tree[0] == "/": return 1.0 if right == 0 else left / right
    if tree[0] == "Min": return min(left, right)
    return max(left, right)


def verify_preferences(sequencing):
    names = sorted({node for entry in sequencing for _, node in walk(parse(entry["Expression"])) if isinstance(node, str)})
    symbols = {name: symbolic.Symbol(name, real=True) for name in names}
    divide = symbolic.Function("D")

    def convert(tree):
        if isinstance(tree, str): return symbols[tree]
        left, right = convert(tree[1]), convert(tree[2])
        return {"+": lambda: left + right, "-": lambda: left - right, "*": lambda: left * right,
                "/": lambda: divide(left, right), "Min": lambda: symbolic.Min(left, right),
                "Max": lambda: symbolic.Max(left, right)}[tree[0]]()

    work, transfer, slack, count, weight = [symbols[name] for name in ["WKR", "TRANT", "SL", "NOR", "W"]]
    queue, machine_wait, operation_wait, system_time, processing, due = [symbols[name] for name in ["NIQ", "MWT", "OWT", "TIS", "PT", "rDD"]]
    first, second = symbols["Pone"], symbols["Ptwo"]
    shared = symbolic.Min(queue, slack)
    constant = transfer - system_time + 3 * processing - work + count - due + 2 * slack + queue - operation_wait - symbolic.Max(work, transfer)
    multiplier = (divide(work, first) + queue + machine_wait) * divide(symbolic.Max(count, second), count * operation_wait)
    forms = {
        1: symbolic.Min(slack - count * second, transfer - work) - symbolic.Max(system_time, work - transfer) - symbolic.Min(second, slack),
        3: symbolic.Min(shared - work + weight + slack + (1 - count) * second,
                        weight * (shared - work + symbolic.Min(processing, work)) - shared),
        5: constant + first + (4 - weight) * second - second ** 2 + symbolic.Max(queue, second) - multiplier,
    }
    rank_form = constant - queue - weight * second - multiplier
    common_offset = queue + first + 4 * second - second ** 2 + symbolic.Max(queue, second)
    assert symbolic.simplify(forms[5] - rank_form - common_offset) == 0
    for entry in sequencing:
        expression = convert(parse(entry["Expression"]))
        assert symbolic.simplify(symbolic.expand(expression) - symbolic.expand(forms[entry["Scenario"]])) == 0
    random = np.random.default_rng(20261106)
    cases = 0
    for _ in range(100):
        values = {name: float(random.uniform(1, 200)) for name in names}
        values["NOR"] = float(random.integers(1, 11))
        values["NIQ"] = float(random.integers(2, 20))
        values["W"] = float(random.choice([1, 2, 4]))
        values["SL"] = float(random.uniform(-200, 200))
        values["rDD"] = values["SL"] + values["WKR"]
        values["OWT"] = float(random.choice([0, random.uniform(1, 200)]))
        for preference in [0, .001, .25, .5, .75, .999, 1]:
            values["Pone"], values["Ptwo"] = 100 * preference, 100 * (1 - preference)
            replacements = {symbols[name]: value for name, value in values.items()}
            for entry in sequencing:
                expected = forms[entry["Scenario"]].subs(replacements)
                for term in list(expected.atoms(symbolic.Function)):
                    if term.func == divide:
                        numerator, denominator = map(float, term.args)
                        expected = expected.subs(term, 1.0 if denominator == 0 else numerator / denominator)
                actual = evaluate(parse(entry["Expression"]), values)
                assert np.isclose(actual, float(expected), rtol=1e-10, atol=1e-7)
                cases += 1
    return {"SymbolicIdentitiesVerified": [1, 3, 5], "NumericIdentityCases": cases,
            "NumericCasesAreSyntheticAlgebraTests": True, "ProtectedDivision": "D(a,b)=1 if b=0,else a/b",
            "PreferenceTerminals": "Pone=100*w1;Ptwo=100*(1-w1)",
            "S5CommonPreferenceOffset": str(common_offset), "S5RankEquivalent": str(rank_form),
            "S5CommonContext": "Same work-centre queue:NIQ/MWT shared;all candidates receive the same preference",
            "Forms": {str(scenario): str(expression) for scenario, expression in forms.items()}}


def draw_trees(sequencing, sources):
    renderer = runpy.run_path(str(ROOT / "plot_learned_trees.py"))["plot_trees"]
    return renderer({"Trees": sequencing, "Modules": MODULES, "SourcesSHA256": sources}, output=OUTPUT)


def build():
    OUTPUT.mkdir(exist_ok=True)
    records, sources, responses = [], {}, []
    protected = {str(directory / name): digest(directory / name)
                 for directory in [ROOT, ROOT / "ablation-full-no-transfer", ROOT / "training-time"]
                 for name in ["per-run.csv", "mean-std.csv", "paired-tests.csv", "table-rows.tex", "protocol.json"] if (directory / name).is_file()}
    for task, scenario in enumerate([1, 3, 5]):
        path = TESTS / f"task{task:02d}/attempt-0001/selected-heuristic.properties"
        metadata = properties(path)
        sources[str(path)] = digest(path)
        stats = Path(metadata["source.stats"])
        assert digest(stats) == metadata["source.stats.sha256"]
        sources[str(stats)] = digest(stats)
        assert metadata["generation"] == "49" and metadata["run"] == "20" and metadata["scenario"] == "R4"
        assert int(metadata["task"]) == task and int(metadata["benchmark.scenario"]) == scenario
        spec_path = path.parent / "test.properties"
        spec = properties(spec_path)
        sources[str(spec_path)] = digest(spec_path)
        assert spec["terminal.normalisation"] == "2" and spec["training.preference.runtime.version"] == "2"
        assert spec["objective.0"] == "max-flowtime" and spec["objective.1"] == "max-weighted-tardiness"
        preference_path = path.parent / "preference-results.csv"
        sources[str(preference_path)] = digest(preference_path)
        preferences = pd.read_csv(preference_path, float_precision="round_trip")
        assert len(preferences) == 200 and np.array_equal(preferences.PreferenceIndex, np.arange(200))
        assert np.isfinite(preferences[["TestFitness0", "TestFitness1"]]).all().all() and (preferences[["TestFitness0", "TestFitness1"]] >= 0).all().all()
        outcomes = preferences[["TestFitness0", "TestFitness1"]].to_numpy()
        responses.append({"Scenario": scenario, "Seed": 20, "Preferences": 200,
                  "DistinctOutcomePairs": len(np.unique(outcomes, axis=0)),
                  "ConsecutiveOutcomeChanges": int(np.any(outcomes[1:] != outcomes[:-1], axis=1).sum()),
                  "Weight0AtFirstPreference": float(preferences.Weight0.iloc[0]),
                  "FirstFitness0": float(outcomes[0, 0]), "FirstFitness1": float(outcomes[0, 1]),
                  "LastFitness0": float(outcomes[-1, 0]), "LastFitness1": float(outcomes[-1, 1])})
        text = stats.read_text(encoding="utf-8")
        final = text[text.rfind("Generation: 49"):]
        for role in ["sequencing", "routing"]:
            expression = metadata[role + ".expression"]
            tree = parse(expression)
            assert parse(prefix(tree)) == tree and expression in final
            fragments = [(position, subtree) for position, subtree in walk(tree)]
            terminals = Counter(subtree for _, subtree in fragments if isinstance(subtree, str))
            records.append({"Task": task, "Scenario": scenario, "Role": role, "Nodes": size(tree),
                            "Expression": prefix(tree), "TerminalCounts": dict(terminals),
                            "Subtrees": [{"Path": position, "Expression": prefix(subtree), "Nodes": size(subtree)}
                                         for position, subtree in fragments if isinstance(subtree, tuple)]})
    sequencing = [entry for entry in records if entry["Role"] == "sequencing"]
    shared = []
    all_fragments = {fragment["Expression"] for entry in sequencing for fragment in entry["Subtrees"]}
    for expression in all_fragments:
        occurrences = [{"Scenario": entry["Scenario"], "Path": fragment["Path"]}
                       for entry in sequencing for fragment in entry["Subtrees"] if fragment["Expression"] == expression]
        if len({entry["Scenario"] for entry in occurrences}) >= 2:
            shared.append({"Expression": expression, "Nodes": size(parse(expression)), "Occurrences": occurrences})
    shared.sort(key=lambda entry: (-entry["Nodes"], entry["Expression"]))
    for label, module in MODULES.items():
        matches = [{"Scenario": entry["Scenario"], "Path": fragment["Path"]}
                   for entry in sequencing for fragment in entry["Subtrees"] if fragment["Expression"] == module["Expression"]]
        assert sorted({entry["Scenario"] for entry in matches}) == module["Scenarios"]
        module["Occurrences"] = matches
    for entry in sequencing:
        other = {fragment["Expression"] for candidate in sequencing if candidate["Scenario"] != entry["Scenario"] for fragment in candidate["Subtrees"]}
        private = {fragment["Expression"] for fragment in entry["Subtrees"] if fragment["Path"] != "r" and fragment["Expression"] not in other}
        entry["DistinctPrivateProperNonterminalSubtrees"] = len(private)
    diagnostics = stats.parent / "job.20.transferContribution.csv"
    sources[str(diagnostics)] = digest(diagnostics)
    transfer = pd.read_csv(diagnostics)
    assert set(transfer.Gen) == set(range(15, 50))
    assert (transfer.SurvivedCount <= transfer.TransferCount).all()
    task_pairs = transfer.groupby(["ReceivingTask", "ContributingTask"])[["TransferCount", "SurvivedCount"]].sum().reset_index()
    task_pairs.to_csv(OUTPUT / "transfer-pair-counts.csv", index=False)
    pd.DataFrame(responses).to_csv(OUTPUT / "preference-response-summary.csv", index=False)
    preferences = verify_preferences(sequencing)
    draw_trees(sequencing, sources)
    semantic_files = [WORKSPACE / "src/yimei/jss/gp/function/Div.java", WORKSPACE / "src/yimei/jss/gp/terminal/JobShopAttribute.java",
                      WORKSPACE / "src/yimei/jss/gp/terminal/AttributeGPNode.java", WORKSPACE / "src/yimei/jss/jobshop/OperationOption.java"]
    for path in semantic_files: sources[str(path)] = digest(path)
    report = {"Group": "R4", "Seed": 20, "Generation": 49, "Selection": "Previously fixed lower-median representative seed;final task-specific training rank0;not chosen by tree similarity",
              "Trees": records, "SharedSequencingSubtrees": shared, "Modules": MODULES,
              "PreferenceAnalysis": preferences, "TransferPairs": task_pairs.to_dict("records"),
              "RecordedDualTreeResponses": responses,
              "NoSubtreeGenealogyAvailable": True, "ProtectedAnalysisSHA256": protected, "SourcesSHA256": sources}
    (OUTPUT / "tree-audit.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    for entry in records:
        print(f"S{entry['Scenario']} {entry['Role']}: {entry['Nodes']}nodes;Pone={entry['TerminalCounts'].get('Pone',0)};Ptwo={entry['TerminalCounts'].get('Ptwo',0)}")
    print("Nonterminal sequencing subtrees shared across tasks:")
    print(json.dumps(shared, indent=2))
    print("Private proper nonterminal signatures:", [(entry["Scenario"], entry["DistinctPrivateProperNonterminalSubtrees"]) for entry in sequencing])
    print("Transfer pair counts:"); print(task_pairs.to_string(index=False))
    print("Recorded dual-tree response counts:"); print(pd.DataFrame(responses).to_string(index=False))
    print("Preference algebra:", json.dumps(preferences, indent=2))
    assert all(digest(Path(path)) == checksum for path, checksum in sources.items())
    assert all(digest(Path(path)) == checksum for path, checksum in protected.items())
    print("PASS: six source trees matched generation49;139sequencingnodes plotted;shared/private modules verified;three symbolic identities and2100numericcases;source/analysis unchanged.")


def validate_pdf():
    import pymupdf

    report = json.loads((OUTPUT / "tree-audit.json").read_text())
    for path, checksum in report["SourcesSHA256"].items(): assert digest(Path(path)) == checksum
    for path, checksum in report["ProtectedAnalysisSHA256"].items(): assert digest(Path(path)) == checksum
    nodes = pd.read_csv(OUTPUT / "tree-figure-nodes.csv", keep_default_na=False)
    assert len(nodes) == 139 and int(nodes.Preference.sum()) == 14
    for entry in report["Trees"]:
        if entry["Role"] != "sequencing": continue
        selected = nodes[nodes.Scenario == entry["Scenario"]]
        original = {path: subtree if isinstance(subtree, str) else subtree[0] for path, subtree in walk(parse(entry["Expression"]))}
        assert dict(zip(selected.Path, selected.Token)) == original
    figure = json.loads((OUTPUT / "figure-validation.json").read_text())
    assert figure["NodeLabelCollisions"] == 0 and digest(OUTPUT / "learned-sequencing-trees.pdf") == figure["PDFSHA256"]
    assert digest(OUTPUT / "learned-sequencing-trees.png") == figure["PNGSHA256"]
    assert report["PreferenceAnalysis"]["NumericIdentityCases"] == 2100
    aux = (PAPER / "mpslgp-method.aux").read_text()
    assert "fig:learned-sequencing-trees" in aux
    pages = []
    with pymupdf.open(PAPER / "mpslgp-method.pdf") as document:
        text = " ".join(" ".join(page.get_text().split()) for page in document)
        assert "Shared and Task-Specific Tree Structures" in text and "Task-Specific Effects of Preference Inputs" in text
        for index, page in enumerate(document):
            if "Generation-49 sequencing trees for R4" in " ".join(page.get_text().split()):
                pages.append(index + 1)
                page.get_pixmap(matrix=pymupdf.Matrix(1.7, 1.7)).save(OUTPUT / f"figure-page-{index+1:02d}.png")
        assert pages
    result = {"Scenarios": [1, 3, 5], "Seed": 20, "OriginalNodesVerified": 139, "PreferenceLeaves": 14,
              "MaximalSharedModules": ["A", "B"], "TaskPrivateModules": ["U1", "U3", "U5"],
              "SymbolicIdentitiesVerified": 3, "NumericAlgebraCases": 2100, "FigurePages": pages,
              "QualityAndTimingDataUnchanged": True, "SourcesVerified": len(report["SourcesSHA256"]),
              "PDFSHA256": digest(PAPER / "mpslgp-method.pdf")}
    (OUTPUT / "validation.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2)); print("PASS: two case-study subsections;139source-matchednodes;shared/private/preference annotations;protected inputs unchanged.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--validate-pdf", action="store_true")
    options = parser.parse_args()
    validate_pdf() if options.validate_pdf else build()