import argparse
import hashlib
import json
import re
import runpy
import shutil
from pathlib import Path

import javaproperties
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parent
PAPER = ROOT.parent.parent
PREVIOUS = ROOT.parent / "latest-20261104"
WORKSPACE = next(parent for parent in PAPER.parents if (parent / "GPDFJSS-MENG.iml").exists())
NS_ROOT = WORKSPACE / "src/mengxu/ruleanalysis/MPSLGP/nsgpii-generation49/independent/Independent"
METHODS = ["NSGPII", "PSLGP", "Full"]
BASELINES = ["NSGPII", "PSLGP"]
ALPHA = .05
METRICS = ["HV", "IGD"]
GROUPS = ["R1", "R2", "R3", "R4", "R5", "H1", "H2", "H3"]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def properties(path):
    with path.open("rb") as stream:
        return javaproperties.load(stream)


def serialize(values):
    def escape(value):
        return "".join(f"\\u{ord(character):04x}" if ord(character) < 32 or ord(character) > 126
                       else "\\" + character if character in "\\ :=#!" else character for character in value)
    return "".join(escape(key) + "=" + escape(value) + "\n" for key, value in sorted(values.items()))


def build():
    archive = ROOT / "before-uncorrected-wilcoxon"
    for name in ["paired-tests.csv", "analysis.md", "protocol.json", "table-rows.tex", "main-table.tex", "analyze_three_generation49.py"]:
        path, backup = ROOT / name, archive / name
        if path.is_file() and not backup.exists():
            archive.mkdir(exist_ok=True)
            shutil.copy2(path, backup)
    shared = runpy.run_path(str(PREVIOUS / "analyze_pslgp49.py"))
    statistics = runpy.run_path(str(ROOT.parent / "latest-20261103/build_result_figures.py"))
    previous_files = {str(path): digest(path) for path in PREVIOUS.rglob("*") if path.is_file() and "__pycache__" not in path.parts}
    previous_runs = pd.read_csv(PREVIOUS / "per-run.csv", float_precision="round_trip")
    expected = pd.read_csv(PREVIOUS / "input-manifest.csv").set_index("Path").SHA256.to_dict()
    sources, records, ns_training = {}, [], []

    def remember(path, checksum=None):
        value = digest(path)
        if checksum is not None:
            assert value == checksum, path
        sources[str(path)] = value
        return value

    for entry in previous_runs[previous_runs.Method.isin(["Full", "PSLGP"])].to_dict("records"):
        path = Path(entry["Source"])
        remember(path, expected[str(path)])
        frame = pd.read_csv(path, float_precision="round_trip")
        assert len(frame) == entry["InputRows"] == 200 and entry["ActualGeneration"] == 49
        if entry["Method"] == "PSLGP":
            for name in ["test.properties", "completed.properties", "test-outcomes.csv"]:
                remember(path.parent / name, expected[str(path.parent / name)])
            spec, marker = properties(path.parent / "test.properties"), properties(path.parent / "completed.properties")
            assert marker["status"] == "COMPLETED" and marker["actual.generation"] == spec["generation"] == "49"
            assert shared["configuration_hash"](spec) == marker["configuration.sha256"] and digest(path) == marker["csv.sha256"]
            assert spec["preferences"] == "200" and spec["replications"] == "30" and spec["test.seed"] == "968356" and spec["smoke"] == "false"
            assert spec["selection"] == "recorded Best Individual" and spec["rule.count"] == "1"
            for key in ["source.stats", "source.params"]:
                remember(Path(spec[key]), spec[key + ".sha256"])
            details = pd.read_csv(path.parent / "test-outcomes.csv", float_precision="round_trip")
            assert len(details) == 200 and np.array_equal(details.PreferenceIndex, np.arange(200))
            assert np.allclose(details.Weight0, np.arange(200) / 199, rtol=0, atol=1e-12)
            assert np.allclose(details.Weight1, np.arange(199, -1, -1) / 199, rtol=0, atol=1e-12)
            for objective in range(2):
                assert np.array_equal(frame[f"TestFitness{objective}"], details[f"TestFitness{objective}"])
            assert set(frame.ParetoFromGeneration) == {49} and set(frame.Run) == {entry["Seed"]} and set(frame.ind) == {0}
        else:
            assert np.array_equal(frame.PreferenceIndex, np.arange(200))
            assert np.allclose(frame.Weight0, np.arange(200) / 199, rtol=0, atol=1e-12)
        points = frame[["TestFitness0", "TestFitness1"]].to_numpy()
        valid = np.isfinite(points).all(axis=1) & (points >= 0).all(axis=1)
        assert valid.any() and int((~valid).sum()) == entry["InvalidRows"]
        records.append({key: value for key, value in entry.items() if key not in METRICS} | {"Points": points[valid]})
    assert len(records) == 840
    for scenario in range(1, 11):
        for seed in range(30):
            directory = NS_ROOT / f"S{scenario}" / f"run{seed:02d}"
            training, testing = directory / "training", directory / "test-generation49"
            identity = properties(training / "experiment.properties")
            parameters = properties(training / "effective.params")
            train_marker = properties(training / "completed.properties")
            spec, marker = properties(testing / "test.properties"), properties(testing / "completed.properties")
            for name in ["experiment.properties", "effective.params", "completed.properties", "evaluation-count.properties"]:
                remember(training / name)
            for name in ["test.properties", "completed.properties"]:
                remember(testing / name)
            fingerprint = hashlib.sha256((serialize(identity) + serialize(parameters)).encode("utf-8")).hexdigest()
            assert train_marker["configuration.sha256"] == marker["configuration.sha256"] == fingerprint, directory
            assert train_marker["status"] == marker["status"] == "COMPLETED"
            assert train_marker["actual.final.generation"] == marker["actual.generation"] == spec["actual.generation"] == "49"
            assert identity["budget"] == spec["budget"] == "independent" and identity["method"] == spec["method"] == "NSGPII"
            assert identity["population"] == spec["population"] == parameters["pop.subpop.0.size"] == "1000"
            assert identity["generations"] == spec["generations"] == parameters["generations"] == "50"
            assert identity["scenario"] == spec["scenario"] == str(scenario) and identity["seed"] == spec["seed"] == parameters["seed.0"] == str(seed)
            assert identity["smoke"] == spec["smoke"] == "false" and spec["test.replications"] == "30" and spec["test.seed"] == "968356"
            assert parameters["breed"] == "mengxu.algorithm.multiobjective.NSGPII.NSGA2BreederNoEnvironmentalSelection"
            assert parameters["eval"] == "mengxu.algorithm.multiobjective.NSGPII.NSGA2EvaluatorNoEnvironmentalSelection"
            assert parameters["eval.problem.eval-model.sim-seed"] == "0" and parameters["eval.problem.eval-model.rotate-sim-seed"] == "true"
            assert parameters["eval.problem.eval-model.sim-models.0.num-jobs"] == "5000" and parameters["eval.problem.eval-model.sim-models.0.warmup-jobs"] == "1000"
            assert remember(training / "effective.params") == train_marker["params.sha256"]
            assert remember(training / "evaluation-count.properties") == train_marker["counts.sha256"]
            counts = properties(training / "evaluation-count.properties")
            assert counts["actual.generation"] == "49" and counts["individual.evaluation.calls"] == train_marker["individual.evaluation.calls"]
            assert int(counts["individual.evaluation.calls"]) > 0
            remember(Path(identity["template"]), identity["template.sha256"])
            stats = training / "out.stat"
            assert remember(stats) == train_marker["stats.sha256"] == spec["source.stats.sha256"]
            assert Path(spec["source.stats"]) == stats
            text = stats.read_text(encoding="utf-8")
            assert [int(value) for value in re.findall(r"(?m)^Generation: (\d+)\s*$", text)] == list(range(50))
            region = text[text.rfind("Generation: 49"):]
            final = region[region.rfind("Pareto Front of Subpopulation 0") + len("Pareto Front of Subpopulation 0"):]
            training_vectors = np.asarray([[float(value) for value in vector.split()] for vector in re.findall(r"(?m)^Fitness: \[([^\]]+)\]", final)])
            path = testing / "test-results.csv"
            remember(path, marker["csv.sha256"])
            frame = pd.read_csv(path, float_precision="round_trip")
            assert len(frame) == int(spec["rule.count"]) == int(marker["rules.tested"]) == int(train_marker["final.front.size"])
            assert set(frame.Generation) == {49} and set(frame.Run) == {seed} and np.array_equal(frame.CandidateIndex, np.arange(len(frame)))
            assert np.array_equal(frame[["TrainFitness0", "TrainFitness1"]].to_numpy(), training_vectors)
            assert len(re.findall(r"(?m)^Tree 0:", final)) == len(re.findall(r"(?m)^Tree 1:", final)) == len(frame)
            points = frame[["TestFitness0", "TestFitness1"]].to_numpy()
            valid = np.isfinite(points).all(axis=1) & (points >= 0).all(axis=1)
            assert valid.any(), f"All-invalid NSGPII test: S{scenario}/seed{seed}"
            records.append({"Method": "NSGPII", "Group": "Independent", "Task": -1, "Scenario": scenario, "Seed": seed,
                            "ActualGeneration": 49, "SnapshotIndex": 49, "InputRows": len(frame), "InvalidRows": int((~valid).sum()),
                            "Selection": "complete actual-generation49 final population Pareto front;1000 per task",
                            "Source": str(path), "Points": points[valid]})
            ns_training.append({"Scenario": scenario, "Seed": seed, "Population": 1000, "FinalGeneration": 49,
                                "IndividualEvaluationCalls": int(counts["individual.evaluation.calls"]),
                                "WallSeconds": float(train_marker["training.wall.seconds"]), "FrontSize": len(frame)})
    assert len(records) == 1140 and set(record["Method"] for record in records) == set(METHODS)
    pools = {}
    for scenario in range(1, 11):
        points = np.concatenate([record["Points"] for record in records if record["Scenario"] == scenario])
        minimum, maximum = points.min(axis=0), points.max(axis=0)
        assert (maximum > minimum).all()
        front = shared["nondominated"]((points - minimum) / (maximum - minimum))
        pools[scenario] = {"Minimum": minimum.tolist(), "Maximum": maximum.tolist(), "Front": front.tolist()}
        pd.DataFrame(front, columns=["NormalizedObjective0", "NormalizedObjective1"]).to_csv(ROOT / f"scenario-{scenario}-reference.csv", index=False)
    runs = pd.DataFrame([{key: value for key, value in record.items() if key != "Points"}
                         | shared["indicators"](record["Points"], pools[record["Scenario"]]) for record in records])
    assert not runs.duplicated(["Method", "Group", "Task", "Scenario", "Seed"]).any()
    assert np.isfinite(runs[METRICS]).all().all() and (runs[METRICS] >= 0).all().all()
    means = runs.groupby(["Method", "Group", "Task", "Scenario"], sort=False).agg(
        Runs=("Seed", "size"), HVMean=("HV", "mean"), HVStd=("HV", "std"), IGDMean=("IGD", "mean"), IGDStd=("IGD", "std"),
        InputRows=("InputRows", "sum"), InvalidRows=("InvalidRows", "sum")).reset_index()
    assert len(means) == 38 and (means.Runs == 30).all()
    runs.to_csv(ROOT / "per-run.csv", index=False); means.to_csv(ROOT / "mean-std.csv", index=False)
    pd.DataFrame(ns_training).to_csv(ROOT / "nsgpii-training-audit.csv", index=False)
    full = means[means.Method == "Full"].assign(Order=lambda frame: frame.Group.map(GROUPS.index)).sort_values(["Order", "Task"])
    rows, comparisons, tests = [], [], []
    for entry in full.itertuples():
        displayed = [entry.Group, str(entry.Scenario)]
        for metric in METRICS:
            target = means[(means.Scenario == entry.Scenario) & ((means.Group == entry.Group) | (means.Group == "Independent"))].set_index("Method").loc[METHODS]
            values = target[metric + "Mean"].to_numpy(); best = max(values) if metric == "HV" else min(values)
            mpslgp_mean = float(target.loc["Full", metric + "Mean"])
            selected = runs[(runs.Method == "Full") & (runs.Group == entry.Group) & (runs.Task == entry.Task)].set_index("Seed").sort_index()
            assert len(selected) == 30 and list(selected.index) == list(range(30))
            symbols = {}
            for baseline in BASELINES:
                value = float(target.loc[baseline, metric + "Mean"])
                control = runs[(runs.Method == baseline) & (runs.Scenario == entry.Scenario)].set_index("Seed").sort_index()
                assert len(control) == 30 and list(control.index) == list(range(30))
                gains = (selected[metric] - control[metric]).to_numpy() * (1 if metric == "HV" else -1)
                nonzero = gains[gains != 0]
                ranks = statistics["rankdata"](np.abs(nonzero))
                positive = float(ranks[nonzero > 0].sum())
                negative = float(ranks[nonzero < 0].sum())
                probability = statistics["exact_signed_rank"](gains)
                direction = "MPSLGP better" if positive > negative else "MPSLGP worse" if positive < negative else "Rank tie"
                symbol = "=" if probability > ALPHA else r"\uparrow" if positive > negative else r"\downarrow" if positive < negative else "="
                symbols[baseline] = symbol
                row = {"Group": entry.Group, "Scenario": entry.Scenario, "Metric": metric, "Baseline": baseline,
                       "FullMean": mpslgp_mean, "BaselineMean": value,
                       "FullMeanBetter": bool(mpslgp_mean > value if metric == "HV" else mpslgp_mean < value)}
                comparisons.append(row)
                tests.append(row | {"Runs": 30, "MedianPairedGain": float(np.median(gains)),
                                    "PositiveRankSum": positive, "NegativeRankSum": negative,
                                    "PValue": probability, "Alpha": ALPHA, "Correction": "none",
                                    "Direction": direction, "Significant": probability <= ALPHA, "Symbol": symbol})
            for point in target.itertuples():
                mean, deviation = getattr(point, metric + "Mean"), getattr(point, metric + "Std")
                cell = f"{mean:.3f}({deviation:.3f})"
                if mean == best: cell = chr(92) + "textbf{" + cell + "}"
                if point.Index in BASELINES: cell += "$(" + symbols[point.Index] + ")$"
                displayed.append(cell)
        rows.append(" & ".join(displayed) + " " + chr(92) * 2)
    (ROOT / "table-rows.tex").write_text(chr(92) + "newcommand{" + chr(92) + "ThreeGenerationFortyNineRows}{%\n" + "\n".join(rows) + "\n}\n", encoding="utf-8")
    comparison = pd.DataFrame(comparisons); comparison.to_csv(ROOT / "descriptive-comparisons.csv", index=False)
    tests = pd.DataFrame(tests); assert len(tests) == 72
    tests.to_csv(ROOT / "paired-tests.csv", index=False)
    favourable = comparison.groupby(["Baseline", "Metric"]).FullMeanBetter.sum().unstack()
    favourable.to_csv(ROOT / "favourable-means.csv")
    significant = tests[tests.Significant].groupby(["Baseline", "Metric", "Direction"]).size()
    summary = {"Methods": METHODS, "ActualGeneration": 49, "Runs": runs.groupby("Method").size().to_dict(),
               "Normalization": "Per-scenario min/max from only MPSLGP,PSLGP,NSGPII actual49 valid outcomes,all groups/seeds",
               "ReferenceFront": "Distinct normalized nondominated union of only these three methods",
               "Metrics": METRICS, "HV": "minimization;reference1.1/1.1;divide1.21", "IGD": "arithmetic mean Euclidean nearest-outcome distance",
               "InvalidRows": runs.groupby("Method").InvalidRows.sum().to_dict(), "PairedComparisons": 72,
               "Test": "Exact two-sided paired Wilcoxon signed-rank;zero differences omitted;midranks for ties",
               "Alpha": ALPHA, "Correction": "none", "DisplayOrder": ["NSGPII", "PSLGP", "MPSLGP"],
               "Symbols": "At each baseline entry:uparrow=MPSLGP significantly better,downarrow=MPSLGP significantly worse,equals=no significant difference;raw p<=0.05",
               "Pairing": "30 training run IDs per fixed group/scenario;preferences/front members are not independent replicates",
               "FullFavourableMeans": favourable.to_dict(), "References": pools, "PreviousAnalysisSHA256": previous_files,
               "Limitations": "Generation and test protocol aligned;not total population/evaluation-budget matched;conditioned single heuristics versus a multiheuristic front;TopN versus Best selection differs"}
    (ROOT / "protocol.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    for path, checksum in sources.items(): assert digest(Path(path)) == checksum
    assert all(digest(Path(path)) == checksum for path, checksum in previous_files.items())
    pd.DataFrame([{"Path": path, "SHA256": checksum} for path, checksum in sources.items()]).to_csv(ROOT / "input-manifest.csv", index=False)
    for name in ["mpslgp-method.tex", "mpslgp-method.pdf"]:
        backup = ROOT / ("before-nsgpii49-" + name)
        if not backup.exists(): shutil.copy2(PAPER / name, backup)
    display_means = means.assign(Method=means.Method.replace({"Full": "MPSLGP"}))
    report = "\n".join(["# Three-Method Actual-Generation-49 Comparison", "", summary["Normalization"], "", summary["Limitations"],
                       "", "Exact two-sided paired Wilcoxon signed-rank tests use alpha=0.05 without multiple-comparison correction. Symbols describe MPSLGP relative to each baseline, not the baseline relative to MPSLGP. Unadjusted tests do not provide family-wise error control.",
                       "", "## Mean and Sample SD", display_means.to_markdown(index=False), "", "## Favourable MPSLGP Means", favourable.to_markdown(),
                       "", "## Unadjusted Significant Paired Comparisons", significant.to_markdown() if len(significant) else "None",
                       "", "## Paired Tests", tests.to_markdown(index=False)])
    (ROOT / "analysis.md").write_text(report, encoding="utf-8")
    print("Favourable MPSLGP means:"); print(favourable.to_string())
    print("Unadjusted Wilcoxon comparisons at alpha0.05:"); print(significant.to_string())
    print("Invalid outcome rows:", summary["InvalidRows"])
    print("PASS: 300 new NSGPII training/test pairs verified;1140 actual49 runs;three-method refs and18-target table;72 unadjusted paired tests;prior data preserved.")


def validate_pdf():
    import pymupdf
    protocol = json.loads((ROOT / "protocol.json").read_text())
    for entry in pd.read_csv(ROOT / "input-manifest.csv").itertuples(): assert digest(Path(entry.Path)) == entry.SHA256
    assert all(digest(Path(path)) == checksum for path, checksum in protocol["PreviousAnalysisSHA256"].items())
    runs, means = pd.read_csv(ROOT / "per-run.csv", float_precision="round_trip"), pd.read_csv(ROOT / "mean-std.csv", float_precision="round_trip")
    assert len(runs) == 1140 and set(runs.Method) == set(METHODS) and set(runs.ActualGeneration) == {49}
    for entry in means.itertuples():
        subset = runs[(runs.Method == entry.Method) & (runs.Group == entry.Group) & (runs.Task == entry.Task) & (runs.Scenario == entry.Scenario)]
        assert len(subset) == 30 and set(subset.Seed) == set(range(30))
        for metric in METRICS:
            assert np.isclose(subset[metric].mean(), getattr(entry, metric + "Mean"), rtol=0, atol=1e-12)
            assert np.isclose(subset[metric].std(ddof=1), getattr(entry, metric + "Std"), rtol=0, atol=1e-12)
    checked = 0
    lines = [line for line in (ROOT / "table-rows.tex").read_text().splitlines() if " & " in line]
    assert len(lines) == 18
    with pymupdf.open(PAPER / "mpslgp-method.pdf") as document:
        text = " ".join(" ".join(page.get_text().split()) for page in document)
        assert re.search(r"MOGP\s*/?\s*D|MOGPD", text) is None
        pages = []
        for line in lines:
            cells = line[:-2].strip().split(" & "); assert len(cells) == 8
            for metric_index, metric in enumerate(METRICS):
                target = means[(means.Scenario == int(cells[1])) & ((means.Group == cells[0]) | (means.Group == "Independent"))].set_index("Method").loc[METHODS]
                values = target[metric + "Mean"].to_numpy(); best = max(values) if metric == "HV" else min(values)
                for method_index, entry in enumerate(target.itertuples()):
                    actual = cells[2 + metric_index * 3 + method_index]
                    base = re.sub(r"\$\((?:\\uparrow|\\downarrow|=)\)\$$", "", actual)
                    clean = re.sub(r"\\textbf\{(.*?)\}", r"\1", base)
                    assert clean == f"{getattr(entry, metric+'Mean'):.3f}({getattr(entry, metric+'Std'):.3f})" and clean in text
                    assert actual.startswith(chr(92) + "textbf{") == (getattr(entry, metric + "Mean") == best)
                    if entry.Index in BASELINES:
                        tests = pd.read_csv(ROOT / "paired-tests.csv", float_precision="round_trip")
                        test = tests[(tests.Group == cells[0]) & (tests.Scenario == int(cells[1])) & (tests.Metric == metric) & (tests.Baseline == entry.Index)].iloc[0]
                        assert actual.endswith("$(" + test.Symbol + ")$")
                    else:
                        assert actual == base
                    checked += 1
        for index, page in enumerate(document):
            if "Three-method generation-49 HV and IGD comparison:" in " ".join(page.get_text().split()):
                pages.append(index + 1)
                spans = [span for block in page.get_text("dict")["blocks"] if "lines" in block for line in block["lines"] for span in line["spans"]]
                assert all(span["color"] == 0x008040 for span in spans if re.search(r"\d\.\d{3}\(", span["text"]))
                page.get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5)).save(ROOT / f"table-page-{index+1:02d}.png")
        assert pages and checked == 108
        report = {"TableCellsVerified": checked, "TablePages": pages, "PDFPages": len(document),
                  "ActualGeneration": 49, "Methods": METHODS, "Runs": 1140, "NSGPIINewTests": 300,
                  "SourcesVerified": len(pd.read_csv(ROOT / "input-manifest.csv")), "PDFSHA256": digest(PAPER / "mpslgp-method.pdf")}
    (ROOT / "validation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2)); print("PASS: all108cells,three methodsactual49,noMOGPD,green/sampleSD andallsourcehashes verified.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(); parser.add_argument("--validate-pdf", action="store_true")
    options = parser.parse_args()
    validate_pdf() if options.validate_pdf else build()