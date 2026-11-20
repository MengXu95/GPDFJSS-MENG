import argparse
import hashlib
import itertools
import json
import re
import runpy
from pathlib import Path

import numpy as np
import pandas as pd
from scipy.spatial import cKDTree
from scipy.stats import rankdata


ROOT = Path(__file__).resolve().parent
PAPER = ROOT.parent.parent
PREVIOUS = ROOT.parent / "latest-20261106"
WORKSPACE = next(parent for parent in PAPER.parents if (parent / "GPDFJSS-MENG.iml").exists())
DATA = WORKSPACE / "src" / "mengxu" / "ruleanalysis" / "MPSLGP"
GROUPS = {"R1": [1, 2], "R2": [3, 4], "R3": [6, 7], "R4": [1, 3, 5],
          "R5": [8, 9], "H1": [1, 10], "H2": [5, 6], "H3": [1, 5, 8]}
METHODS = ["NSGPII", "PSLGP", "MPSLGP"]
METRICS = ["HV", "IGD", "GD"]
ALPHA = .05
SHARED = runpy.run_path(str(ROOT.parent / "latest-20261104" / "analyze_pslgp49.py"))
NATIVE = runpy.run_path(str(PREVIOUS / "analyze_three_generation49.py"))
STATISTICS = runpy.run_path(str(ROOT.parent / "latest-20261103" / "build_result_figures.py"))
digest = SHARED["digest"]
properties = SHARED["properties"]


def frame(path):
    return pd.read_csv(path, float_precision="round_trip")


def elapsed(directory, seed, remember):
    path = directory / f"job.{seed}.time.csv"
    cumulative = directory / f"job.{seed}.timeSumGen.csv"
    remember(path)
    remember(cumulative)
    times, totals = frame(path), frame(cumulative)
    assert np.array_equal(times.Gen, np.arange(50)), path
    assert np.array_equal(totals.Gen, times.Gen), cumulative
    assert np.isfinite(times.Time).all() and (times.Time >= 0).all(), path
    assert np.allclose(times.Time.cumsum(), totals.timeSumGen, rtol=0, atol=1e-8), path
    seconds = float(times.Time.sum())
    assert seconds > 0
    return seconds


def training_records(text, heading):
    final = text[text.rfind("Generation: 49"):]
    assert heading in final
    final = final[final.rfind(heading) + len(heading):]
    return re.findall(r"Evaluated: true\s*\nFitness: \[([^\]]+)\]\s*\n(?:Rank: [^\n]+\nSparsity: [^\n]+\n)?Tree 0:\s*\n([^\n]+)"
                      r"\s*\nTree 1:\s*\n([^\n]+)", final)


def load_inputs():
    sources, records, training = {}, [], {}

    def remember(path, expected=None):
        path = Path(path)
        key = str(path)
        if key not in sources:
            sources[key] = digest(path)
        if expected is not None:
            assert sources[key] == expected, path
        return sources[key]

    for path in [Path(__file__), ROOT.parent / "latest-20261104" / "analyze_pslgp49.py",
                 PREVIOUS / "analyze_three_generation49.py",
                 ROOT.parent / "latest-20261103" / "build_result_figures.py",
                 PREVIOUS / "per-run.csv", PREVIOUS / "input-manifest.csv"]:
        remember(path)
    previous = frame(PREVIOUS / "per-run.csv")
    expected = frame(PREVIOUS / "input-manifest.csv").set_index("Path").SHA256.to_dict()
    full = previous[previous.Method == "Full"]
    assert len(full) == 540
    for entry in full.itertuples():
        path = Path(entry.Source)
        remember(path, expected[str(path)])
        testing = path.parent
        marker_path = testing.parent / "completed.properties"
        remember(marker_path)
        marker = properties(marker_path)
        assert marker["status"] == "COMPLETED" and marker["attempt"] == testing.name
        for name in ["test.properties", "selected-heuristic.properties"]:
            remember(testing / name, marker["sha256." + name])
        assert digest(path) == marker["sha256.preference-results.csv"]
        spec = properties(testing / "test.properties")
        assert digest(testing / "test.properties") == marker["configuration.sha256"]
        assert spec["algorithm"] == "full" and spec["indicator"] == "IGD"
        assert spec["generation"] == "49" and spec["training.generations"] == "50"
        assert spec["preferences"] == "200" and spec["replications"] == "30"
        assert spec["test.seed"] == "968356" and spec["smoke"] == "false"
        assert spec["training.population.total"] == "1000" and spec["training.preference.runtime.version"] == "2"
        assert spec["scenario"] == entry.Group and spec["benchmark.scenario"] == str(entry.Scenario)
        assert spec["task"] == str(entry.Task) and spec["run"] == str(entry.Seed)
        assert GROUPS[entry.Group][entry.Task] == entry.Scenario
        for key in ["source.stats", "source.params"]:
            remember(Path(spec[key]), spec[key + ".sha256"])
        selected = properties(testing / "selected-heuristic.properties")
        assert selected["selection"].startswith("First training-ranked individual")
        assert selected["generation"] == "49"
        assert selected["sequencing.expression"] == spec["sequencing.expression"]
        assert selected["routing.expression"] == spec["routing.expression"]
        train_directory = Path(spec["source.params"]).parent
        key = ("MPSLGP", entry.Group, -1, entry.Seed)
        if key not in training:
            train_marker_path = train_directory.parent / "completed.properties"
            remember(train_marker_path)
            train_marker = properties(train_marker_path)
            assert train_marker["status"] == "COMPLETED" and train_marker["attempt"] == train_directory.name
            assert train_marker["configuration.sha256"] == spec["source.configuration.sha256"]
            assert train_marker["sha256.effective.params"] == spec["source.params.sha256"]
            assert train_marker["sha256." + Path(spec["source.stats"]).name] == spec["source.stats.sha256"]
            seconds = elapsed(train_directory, entry.Seed, remember)
            assert seconds <= float(train_marker["duration.seconds"]) + 2
            training[key] = seconds
        data = frame(path)
        assert len(data) == 200 and np.array_equal(data.PreferenceIndex, np.arange(200))
        assert np.allclose(data.Weight0, np.arange(200) / 199, rtol=0, atol=1e-12)
        assert np.allclose(data.Weight1, np.arange(199, -1, -1) / 199, rtol=0, atol=1e-12)
        records.append(make_record("MPSLGP", entry.Group, entry.Task, entry.Scenario, entry.Seed, data, path))

    for method in METHODS[:2]:
        for group, scenarios in GROUPS.items():
            for task, scenario in enumerate(scenarios):
                for seed in range(30):
                    directory = DATA / "same-population-budget" / method / "matched" / group / f"task{task}-S{scenario}" / f"run{seed:02d}"
                    train, test = directory / "training", directory / "test-generation49"
                    for folder, names in [(train, ["experiment.properties", "effective.params", "completed.properties",
                                                   "evaluation-count.properties"]),
                                          (test, ["test.properties", "completed.properties"])]:
                        for name in names:
                            remember(folder / name)
                    identity, params = properties(train / "experiment.properties"), properties(train / "effective.params")
                    train_marker = properties(train / "completed.properties")
                    spec, marker = properties(test / "test.properties"), properties(test / "completed.properties")
                    fingerprint = hashlib.sha256((NATIVE["serialize"](identity) + NATIVE["serialize"](params)).encode("utf-8")).hexdigest()
                    assert train_marker["status"] == marker["status"] == "COMPLETED", directory
                    assert train_marker["configuration.sha256"] == marker["configuration.sha256"] == fingerprint, directory
                    population = 1000 // len(scenarios) + int((task - seed % len(scenarios)) % len(scenarios) < 1000 % len(scenarios))
                    for values in [identity, spec]:
                        assert values["budget"] == "matched" and values["group"] == group
                        assert values["task"] == str(task) and values["scenario"] == str(scenario) and values["seed"] == str(seed)
                        assert values["population"] == str(population) and values["group.population.total"] == "1000"
                        assert values["generations"] == "50" and values["smoke"] == "false"
                        assert values["test.replications"] == "30" and values["test.seed"] == "968356"
                    assert identity["method"] == method and spec["method"] == ("PSLGP_I" if method == "PSLGP" else method)
                    assert train_marker["actual.final.generation"] == marker["actual.generation"] == spec["actual.generation"] == "49"
                    assert params["generations"] == "50" and params["pop.subpop.0.size"] == str(population)
                    assert params["seed.0"] == str(seed) and params["quit-on-run-complete"] == "false"
                    assert params["evalthreads"] == params["breedthreads"] == "1"
                    model = "eval.problem.eval-model"
                    assert params[model + ".sim-seed"] == "0" and params[model + ".rotate-sim-seed"] == "true"
                    assert params[model + ".sim-models.0.num-jobs"] == "5000"
                    assert params[model + ".sim-models.0.warmup-jobs"] == "1000"
                    assert params[model + ".sim-models.0.replications"] == "1"
                    remember(Path(identity["template"]), identity["template.sha256"])
                    remember(train / "effective.params", train_marker["params.sha256"])
                    remember(train / "evaluation-count.properties", train_marker["counts.sha256"])
                    counts = properties(train / "evaluation-count.properties")
                    assert counts["actual.generation"] == "49"
                    assert counts["individual.evaluation.calls"] == train_marker["individual.evaluation.calls"]
                    assert int(counts["individual.evaluation.calls"]) > 0
                    stats = train / "out.stat"
                    remember(stats, train_marker["stats.sha256"])
                    assert spec["source.stats.sha256"] == digest(stats) and Path(spec["source.stats"]) == stats
                    text = stats.read_text(encoding="utf-8")
                    assert [int(value) for value in re.findall(r"(?m)^Generation: (\d+)\s*$", text)] == list(range(50))
                    path = test / "test-results.csv"
                    remember(path, marker["csv.sha256"])
                    data = frame(path)
                    assert set(data.Run) == {seed}
                    if method == "PSLGP":
                        assert params["compare-criteria"] == "IGD" and params["terminal-normalisation"] == "2"
                        assert params["terminals-from"] == "relative_with_preference"
                        assert spec["selection"] == "first recorded Top N individual"
                        assert spec["preferences"] == "200" and spec["replications"] == "30"
                        assert spec["generation"] == "49" and spec["run"] == str(seed)
                        assert spec["source.params.sha256"] == digest(train / "effective.params")
                        assert Path(spec["source.params"]) == train / "effective.params"
                        remember(test / "test.properties", marker["spec.sha256"])
                        remember(test / "test-outcomes.csv", marker["details.sha256"])
                        detail = frame(test / "test-outcomes.csv")
                        assert len(data) == len(detail) == 200
                        assert np.array_equal(detail.PreferenceIndex, np.arange(200)) and set(detail.CandidateIndex) == {0}
                        assert np.allclose(detail.Weight0, np.arange(200) / 199, rtol=0, atol=1e-12)
                        assert np.allclose(detail.Weight1, np.arange(199, -1, -1) / 199, rtol=0, atol=1e-12)
                        assert set(data.ParetoFromGeneration) == {49} and set(data.ind) == {0}
                        selected = training_records(text, "Top N individuals:")[0]
                        assert selected[1].strip() == spec["rule.0.sequencing"] and selected[2].strip() == spec["rule.0.routing"]
                        for objective in range(2):
                            assert np.array_equal(data[f"TestFitness{objective}"], detail[f"TestFitness{objective}"])
                            assert np.all(data[f"TrainFitness{objective}"] == float(spec[f"rule.0.fitness.{objective}"]))
                            assert float(selected[0].split()[objective]) == float(spec[f"rule.0.fitness.{objective}"])
                        assert int(marker["rules.tested"]) == int(train_marker["final.front.size"]) == int(spec["rule.count"]) == 1
                    else:
                        assert params["breed"] == "mengxu.algorithm.multiobjective.NSGPII.NSGA2BreederNoEnvironmentalSelection"
                        assert params["eval"] == "mengxu.algorithm.multiobjective.NSGPII.NSGA2EvaluatorNoEnvironmentalSelection"
                        assert params["terminals-from"] == "relative"
                        selected = training_records(text, "Pareto Front of Subpopulation 0")
                        assert len(data) == len(selected) == int(marker["rules.tested"]) == int(spec["rule.count"]) == int(train_marker["final.front.size"]), (
                            directory, len(data), len(selected), marker["rules.tested"], spec["rule.count"], train_marker["final.front.size"])
                        assert set(data.Generation) == {49} and np.array_equal(data.CandidateIndex, np.arange(len(data)))
                        assert np.array_equal(data[["TrainFitness0", "TrainFitness1"]].to_numpy(),
                                              np.array([[float(value) for value in item[0].split()] for item in selected]))
                    seconds = elapsed(train, seed, remember)
                    assert seconds <= float(train_marker["training.wall.seconds"]) + 2, directory
                    training[(method, group, task, seed)] = seconds
                    records.append(make_record(method, group, task, scenario, seed, data, path))
    assert len(records) == 1620 and len(training) == 1320
    return records, training, sources


def make_record(method, group, task, scenario, seed, data, source):
    points = data[["TestFitness0", "TestFitness1"]].to_numpy()
    valid = np.isfinite(points).all(axis=1) & (points >= 0).all(axis=1)
    assert valid.any(), f"All-invalid run must not be silently excluded: {source}"
    return {"Method": method, "Group": group, "Task": task, "Scenario": scenario, "Seed": seed,
            "ActualGeneration": 49, "InputRows": len(points), "InvalidRows": int((~valid).sum()),
            "Source": str(source), "Points": points[valid]}


def self_test():
    for scenarios in GROUPS.values():
        tasks = len(scenarios)
        for seed in range(30):
            assert sum(1000 // tasks + int((task - seed % tasks) % tasks < 1000 % tasks)
                       for task in range(tasks)) == 1000
    for metadata in ["", "Rank: 0\nSparsity: Infinity\n"]:
        text = "Generation: 49\nPareto Front of Subpopulation 0\nEvaluated: true\nFitness: [2.0 3.0]\n" + metadata + "Tree 0:\n PT\nTree 1:\n NIQ\n"
        assert training_records(text, "Pareto Front of Subpopulation 0") == [("2.0 3.0", " PT", " NIQ")]
    reference = {"Minimum": [0, 0], "Maximum": [1, 1], "Front": [[.2, .8], [.8, .2]]}
    points = np.array([[.2, .8], [.8, .2]])
    assert abs(SHARED["indicators"](points, reference)["HV"] - .45 / 1.21) < 1e-12
    assert SHARED["indicators"](points, reference)["IGD"] == NATIVE["generational_distance"](points, reference) == 0
    duplicates = np.vstack([points, [.5, .9], [.5, .9]])
    assert abs(NATIVE["generational_distance"](duplicates, reference) - np.sqrt(.1) / 2) < 1e-12
    for gains in [np.array([0., 0.]), np.array([1., 1., -2., 0.]), np.array([1., 2., 3., -4.])]:
        nonzero = gains[gains != 0]
        ranks = rankdata(np.abs(nonzero))
        observed = min(ranks[nonzero > 0].sum(), ranks[nonzero < 0].sum())
        sums = [float(np.dot(signs, ranks)) for signs in itertools.product([0, 1], repeat=len(ranks))]
        expected = min(1., 2 * sum(value <= observed for value in sums) / len(sums))
        assert STATISTICS["exact_signed_rank"](gains) == expected


def analyze():
    self_test()
    records, training, sources = load_inputs()
    references = {}
    for scenario in range(1, 11):
        points = np.concatenate([entry["Points"] for entry in records if entry["Scenario"] == scenario])
        minimum, maximum = points.min(axis=0), points.max(axis=0)
        assert (maximum > minimum).all()
        front = SHARED["nondominated"]((points - minimum) / (maximum - minimum))
        references[scenario] = {"Minimum": minimum.tolist(), "Maximum": maximum.tolist(), "Front": front.tolist()}
        pd.DataFrame(front, columns=["NormalizedObjective0", "NormalizedObjective1"]).to_csv(ROOT / f"scenario-{scenario}-reference.csv", index=False)
    runs = pd.DataFrame([{key: value for key, value in entry.items() if key != "Points"}
                         | SHARED["indicators"](entry["Points"], references[entry["Scenario"]])
                         | {"GD": NATIVE["generational_distance"](entry["Points"], references[entry["Scenario"]])}
                        for entry in records])
    assert not runs.duplicated(["Method", "Group", "Task", "Seed"]).any()
    assert np.isfinite(runs[METRICS]).all().all() and (runs[METRICS] >= 0).all().all() and (runs.HV <= 1).all()
    means = runs.groupby(["Method", "Group", "Task", "Scenario"], sort=False).agg(
        Runs=("Seed", "size"), HVMean=("HV", "mean"), HVStd=("HV", "std"), IGDMean=("IGD", "mean"),
        IGDStd=("IGD", "std"), GDMean=("GD", "mean"), GDStd=("GD", "std"),
        InputRows=("InputRows", "sum"), InvalidRows=("InvalidRows", "sum")).reset_index()
    assert len(means) == 54 and (means.Runs == 30).all()
    rows, tests = [], []
    for group, scenarios in GROUPS.items():
        for task, scenario in enumerate(scenarios):
            cells = [group, str(scenario)]
            target = means[(means.Group == group) & (means.Task == task)].set_index("Method").loc[METHODS]
            selected = runs[(runs.Group == group) & (runs.Task == task)]
            full = selected[selected.Method == "MPSLGP"].set_index("Seed").sort_index()
            assert list(full.index) == list(range(30))
            for metric in METRICS:
                symbols = {}
                for baseline in METHODS[:2]:
                    control = selected[selected.Method == baseline].set_index("Seed").sort_index()
                    assert list(control.index) == list(range(30))
                    gains = (full[metric] - control[metric]).to_numpy() * (1 if metric == "HV" else -1)
                    nonzero = gains[gains != 0]
                    ranks = rankdata(np.abs(nonzero))
                    positive, negative = float(ranks[nonzero > 0].sum()), float(ranks[nonzero < 0].sum())
                    probability = STATISTICS["exact_signed_rank"](gains)
                    symbol = "=" if probability > ALPHA or positive == negative else r"\uparrow" if positive > negative else r"\downarrow"
                    symbols[baseline] = symbol
                    tests.append({"Group": group, "Task": task, "Scenario": scenario, "Metric": metric, "Baseline": baseline,
                                  "Runs": 30, "NonzeroPairs": len(nonzero), "FullMean": full[metric].mean(),
                                  "BaselineMean": control[metric].mean(), "MeanGain": gains.mean(),
                                  "MedianPairedGain": float(np.median(gains)), "PositiveRankSum": positive,
                                  "NegativeRankSum": negative, "PValue": probability, "Alpha": ALPHA,
                                  "Correction": "none", "Symbol": symbol})
                values = target[metric + "Mean"]
                best = values.max() if metric == "HV" else values.min()
                for method in METHODS:
                    mean, deviation = target.loc[method, metric + "Mean"], target.loc[method, metric + "Std"]
                    cell = f"{mean:.3f}({deviation:.3f})"
                    if mean == best:
                        cell = r"\textbf{" + cell + "}"
                    if method in symbols:
                        cell += "$(" + symbols[method] + ")$"
                    cells.append(cell)
            rows.append(" & ".join(cells) + r" \\")
    tests = pd.DataFrame(tests)
    assert len(tests) == 108
    wins = []
    for baseline in METHODS[:2]:
        for metric in METRICS:
            subset = tests[(tests.Baseline == baseline) & (tests.Metric == metric)]
            wins.append({"Baseline": baseline, "Metric": metric, "BetterMeans": int((subset.MeanGain > 0).sum()),
                         "SignificantlyBetter": int((subset.Symbol == r"\uparrow").sum()),
                         "SignificantlyWorse": int((subset.Symbol == r"\downarrow").sum()),
                         "Nonsignificant": int((subset.Symbol == "=").sum())})
    wins = pd.DataFrame(wins)
    grouped = pd.DataFrame([{"Method": method, "Group": group, "Seed": seed,
                            "Minutes": sum(training[(method, group, task, seed)] for task in
                                           ([-1] if method == "MPSLGP" else range(len(scenarios)))) / 60}
                           for method in METHODS for group, scenarios in GROUPS.items() for seed in range(30)])
    times = grouped.groupby(["Method", "Group"], sort=False).agg(
        Runs=("Seed", "size"), MeanMinutes=("Minutes", "mean"), SDMinutes=("Minutes", "std")).reset_index()
    for name, result in [("per-run", runs), ("mean-std", means), ("paired-tests", tests), ("comparison-summary", wins),
                         ("training-grouped-per-seed", grouped), ("training-mean-std", times)]:
        result.to_csv(ROOT / f"{name}.csv", index=False)
    (ROOT / "table-rows.tex").write_text("\\newcommand{\\SameBudgetQualityRows}{%\n" + "\n".join(rows) + "\n}\n", encoding="utf-8")
    protocol = {"Methods": METHODS, "Groups": GROUPS, "TrainingSeeds": list(range(30)), "ActualGeneration": 49,
                "Runs": runs.groupby("Method").size().to_dict(), "GroupPopulation": 1000,
                "Normalization": "Per-scenario min/max over valid outcomes of ONLY these matched-budget inputs, all groups and seeds",
                "ReferenceFront": "Distinct normalized nondominated union of those same inputs",
                "Metrics": {"HV": "HV of distinct nondominated run outcomes at (1.1,1.1), divided by 1.21",
                            "IGD": "Mean Euclidean distance from reference points to nearest valid normalized run outcome",
                            "GD": "Mean Euclidean distance from ALL valid normalized run outcomes, with multiplicity, to reference front"},
                "PairedTest": "Exact two-sided Wilcoxon signed-rank; exact zeros omitted; absolute midranks for ties",
                "Alpha": ALPHA, "Correction": "none", "Pairing": "Training seed within each group/task/scenario; not preferences or front members",
                "Symbols": "At baselines: uparrow=MPSLGP significantly better, downarrow=MPSLGP significantly worse, equals=nonsignificant; direction from rank sums",
                "InputRows": runs.groupby("Method").InputRows.sum().to_dict(),
                "InvalidRows": runs.groupby("Method").InvalidRows.sum().to_dict(),
                "InvalidPolicy": "Exclude nonfinite/negative objective pairs only; retain all 30 seeds; refuse all-invalid runs",
                "Selection": {"MPSLGP": "First final task-ranked Top-N heuristic, reused raw held-out outcomes",
                              "PSLGP": "First final training-ranked Top-N heuristic; 200 held-out preferences",
                              "NSGPII": "All final population Pareto-front heuristics, no external archive"},
                "TrainingTime": "Sum of logged generation 0--49 elapsed seconds; joint MPSLGP once per group/seed; independent task times summed, not parallel batch makespan",
                "Limitations": "Population allocation and generations matched, not all simulation calls or wall time; native methods differ beyond transfer; unadjusted tests lack family-wise error control; reference family differs from Table VI",
                "References": references}
    (ROOT / "protocol.json").write_text(json.dumps(protocol, indent=2) + "\n", encoding="utf-8")
    for path, checksum in sources.items():
        assert digest(Path(path)) == checksum, path
    pd.DataFrame([{"Path": path, "SHA256": checksum} for path, checksum in sorted(sources.items())]).to_csv(ROOT / "input-manifest.csv", index=False)
    print(wins.to_string(index=False))
    print("Invalid rows:", protocol["InvalidRows"])
    print(times.to_string(index=False))
    print("PASS: 1,080 matched baseline training/test pairs and 540 reused MPSLGP task tests; 54 summaries; 108 paired comparisons.")


def validate():
    import pymupdf
    from scipy.spatial.distance import cdist
    from scipy.stats import wilcoxon

    self_test()
    manifest = frame(ROOT / "input-manifest.csv")
    for entry in manifest.itertuples():
        assert digest(Path(entry.Path)) == entry.SHA256, entry.Path
    protocol = json.loads((ROOT / "protocol.json").read_text(encoding="utf-8"))
    runs, means, tests = (frame(ROOT / f"{name}.csv") for name in ["per-run", "mean-std", "paired-tests"])
    assert len(runs) == 1620 and len(means) == 54 and len(tests) == 108
    pools = {scenario: [] for scenario in range(1, 11)}
    for entry in runs.itertuples():
        raw = frame(Path(entry.Source))[["TestFitness0", "TestFitness1"]].to_numpy()
        valid = np.isfinite(raw).all(axis=1) & (raw >= 0).all(axis=1)
        assert len(raw) == entry.InputRows and int((~valid).sum()) == entry.InvalidRows and valid.any()
        points = raw[valid]
        pools[entry.Scenario].append(points)
        reference = protocol["References"][str(entry.Scenario)]
        normalized = (points - reference["Minimum"]) / (np.asarray(reference["Maximum"]) - reference["Minimum"])
        distances = cdist(normalized, np.asarray(reference["Front"]))
        thresholds = np.unique(np.r_[normalized[:, 0], 1.1])
        hv = sum((right - left) * (1.1 - normalized[normalized[:, 0] <= left, 1].min())
                 for left, right in zip(thresholds[:-1], thresholds[1:])) / 1.21
        assert np.allclose([entry.HV, entry.IGD, entry.GD],
                           [hv, distances.min(axis=0).mean(), distances.min(axis=1).mean()], rtol=0, atol=1e-12), entry.Source
    for scenario, arrays in pools.items():
        points = np.concatenate(arrays)
        reference = protocol["References"][str(scenario)]
        assert np.array_equal(points.min(axis=0), reference["Minimum"])
        assert np.array_equal(points.max(axis=0), reference["Maximum"])
        normalized = (points - reference["Minimum"]) / (np.asarray(reference["Maximum"]) - reference["Minimum"])
        front = SHARED["nondominated"](normalized)
        assert np.array_equal(front, reference["Front"])
        assert np.array_equal(front, frame(ROOT / f"scenario-{scenario}-reference.csv").to_numpy())
    for entry in means.itertuples():
        selected = runs[(runs.Method == entry.Method) & (runs.Group == entry.Group) & (runs.Task == entry.Task)]
        assert set(selected.Seed) == set(range(30)) and len(selected) == entry.Runs == 30
        for metric in METRICS:
            assert abs(float(selected[metric].mean()) - getattr(entry, metric + "Mean")) < 1e-12
            assert abs(float(np.std(selected[metric].to_numpy(), ddof=1)) - getattr(entry, metric + "Std")) < 1e-12
    scipy_checks = 0
    for entry in tests.itertuples():
        selected = runs[(runs.Group == entry.Group) & (runs.Task == entry.Task)]
        full = selected[selected.Method == "MPSLGP"].set_index("Seed").sort_index()[entry.Metric].to_numpy()
        baseline = selected[selected.Method == entry.Baseline].set_index("Seed").sort_index()[entry.Metric].to_numpy()
        gains = (full - baseline) * (1 if entry.Metric == "HV" else -1)
        nonzero = gains[gains != 0]
        if len(nonzero) and len(np.unique(np.abs(nonzero))) == len(nonzero):
            assert abs(wilcoxon(nonzero, alternative="two-sided", method="exact").pvalue - entry.PValue) < 1e-14
            scipy_checks += 1
        ranks = rankdata(np.abs(nonzero))
        positive, negative = ranks[nonzero > 0].sum(), ranks[nonzero < 0].sum()
        assert positive == entry.PositiveRankSum and negative == entry.NegativeRankSum
        expected = "=" if entry.PValue > ALPHA or positive == negative else r"\uparrow" if positive > negative else r"\downarrow"
        assert entry.Symbol == expected and entry.Runs == 30 and entry.Correction == "none"
        assert abs(entry.MeanGain - gains.mean()) < 1e-12
    assert scipy_checks > 0
    rows = (ROOT / "table-rows.tex").read_text(encoding="utf-8").splitlines()[1:-1]
    assert len(rows) == 18
    for row, (group, scenario) in zip(rows, [(group, scenario) for group, values in GROUPS.items() for scenario in values]):
        cells = row.removesuffix(r" \\").split(" & ")
        assert cells[:2] == [group, str(scenario)] and len(cells) == 11
        selected = means[(means.Group == group) & (means.Scenario == scenario)].set_index("Method")
        for index, metric in enumerate(METRICS):
            best = selected[metric + "Mean"].max() if metric == "HV" else selected[metric + "Mean"].min()
            for offset, method in enumerate(METHODS):
                mean, deviation = selected.loc[method, metric + "Mean"], selected.loc[method, metric + "Std"]
                expected = f"{mean:.3f}({deviation:.3f})"
                if mean == best:
                    expected = r"\textbf{" + expected + "}"
                if method != "MPSLGP":
                    symbol = tests[(tests.Group == group) & (tests.Scenario == scenario) & (tests.Metric == metric)
                                   & (tests.Baseline == method)].Symbol.item()
                    expected += "$(" + symbol + ")$"
                assert cells[2 + 3 * index + offset] == expected, (group, scenario, metric, method)
    times, grouped = frame(ROOT / "training-mean-std.csv"), frame(ROOT / "training-grouped-per-seed.csv")
    assert len(times) == 24 and len(grouped) == 720 and (times.Runs == 30).all()
    for entry in times.itertuples():
        values = grouped[(grouped.Method == entry.Method) & (grouped.Group == entry.Group)].Minutes.to_numpy()
        assert len(values) == 30 and abs(values.mean() - entry.MeanMinutes) < 1e-12
        assert abs(values.std(ddof=1) - entry.SDMinutes) < 1e-12
    text = (PAPER / "mpslgp-method.tex").read_text(encoding="utf-8")
    section = text.split(r"\label{sec:matched-population-budget}", 1)[1].split(r"\subsection", 1)[0]
    assert "pending" not in section and r"\input{results/same-population-budget-20261120/table.tex}" in section
    summary = frame(ROOT / "comparison-summary.csv")
    for entry in summary.itertuples():
        counts = f"{entry.SignificantlyBetter}/{entry.SignificantlyWorse}/{entry.Nonsignificant}"
        assert counts in section
    assert protocol["InvalidRows"] == runs.groupby("Method").InvalidRows.sum().to_dict()
    aux = (PAPER / "mpslgp-method.aux").read_text(encoding="utf-8")
    log = (PAPER / "mpslgp-method.log").read_text(encoding="utf-8")
    assert not re.search(r"Overfull|undefined references|LaTeX Warning|^!", log, re.MULTILINE), "Inspect the final LaTeX log"
    table_match = re.search(r"\\newlabel\{tab:same-budget-quality\}\{\{([^}]+)\}\{(\d+)\}", aux)
    assert table_match is not None
    page_number = int(table_match.group(2))
    with pymupdf.open(PAPER / "mpslgp-method.pdf") as document:
        page = document[page_number - 1]
        table_text = re.sub(r"\s+", "", page.get_text())
        assert f"TABLE{table_match.group(1)}" in table_text.upper() and "MATCHEDGROUP-LEVELPOPULATIONBUDGETS" in table_text.upper()
        for entry in means.itertuples():
            for metric in METRICS:
                assert f"{getattr(entry, metric + 'Mean'):.3f}({getattr(entry, metric + 'Std'):.3f})" in table_text
        for block in page.get_text("blocks"):
            assert block[0] >= -1 and block[1] >= -1 and block[2] <= page.rect.width + 1 and block[3] <= page.rect.height + 1
    print(f"PASS: {len(manifest)} source hashes; 4,860 independent metric checks; 54 sample-SD summaries;"
          f" {scipy_checks} SciPy exact-test cross-checks; 162 table cells, arrows and bold;"
          f" 720 group timing records; compiled Table {table_match.group(1)} on PDF page {page_number}.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--self-test", action="store_true")
    mode.add_argument("--validate", action="store_true")
    arguments = parser.parse_args()
    if arguments.self_test:
        self_test()
        print("PASS: shared HV/IGD/GD conventions, GD multiplicities and exact signed-rank inference.")
    elif arguments.validate:
        validate()
    else:
        analyze()
