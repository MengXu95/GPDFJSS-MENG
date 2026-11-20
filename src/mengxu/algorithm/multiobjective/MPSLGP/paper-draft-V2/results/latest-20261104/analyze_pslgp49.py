import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path

import javaproperties
import numpy as np
import pandas as pd
from scipy.spatial import cKDTree


ROOT = Path(__file__).resolve().parent
PAPER = ROOT.parent.parent
WORKSPACE = next(parent for parent in PAPER.parents if (parent / "GPDFJSS-MENG.iml").exists())
OLD = ROOT.parent / "latest-20261103" / "baseline-tables"
TESTS = WORKSPACE / "src/mengxu/ruleanalysis/MPSLGP/baseline-tests-generation49/PSLGP_I"
METHODS = ["Full", "PSLGP", "NSGPII", "MOGP/D"]
METRICS = ["HV", "IGD"]
GROUPS = ["R1", "R2", "R3", "R4", "R5", "H1", "H2", "H3"]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def properties(path):
    with path.open("rb") as stream:
        return javaproperties.load(stream)


def configuration_hash(values):
    def escape(value):
        return "".join(f"\\u{ord(character):04x}" if ord(character) < 32 or ord(character) > 126
                       else "\\" + character if character in "\\ :=#!" else character for character in value)
    text = "".join(escape(key) + "=" + escape(value) + "\n" for key, value in sorted(values.items()))
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def nondominated(points):
    values = np.unique(points, axis=0)
    values = values[np.lexsort((values[:, 1], values[:, 0]))]
    previous = np.r_[np.inf, np.minimum.accumulate(values[:, 1])[:-1]]
    return values[values[:, 1] < previous]


def indicators(points, reference):
    minimum = np.asarray(reference["Minimum"])
    maximum = np.asarray(reference["Maximum"])
    normalized = (points - minimum) / (maximum - minimum)
    front = nondominated(normalized)
    previous = np.minimum(1.1, np.r_[1.1, front[:-1, 1]])
    hv = np.sum(np.maximum(0, 1.1 - front[:, 0]) * np.maximum(0, previous - front[:, 1])) / 1.21
    igd = cKDTree(normalized).query(np.asarray(reference["Front"]))[0].mean()
    return {"HV": float(hv), "IGD": float(igd)}


def build():
    assert abs(indicators(np.array([[0.2, 0.8], [0.8, 0.2]]),
                          {"Minimum": [0, 0], "Maximum": [1, 1], "Front": [[0.2, 0.8], [0.8, 0.2]]})["HV"] - .45 / 1.21) < 1e-12
    assert abs(indicators(np.array([[0.2, 1.4], [0.4, 0.8]]),
                          {"Minimum": [0, 0], "Maximum": [1, 1], "Front": [[0.4, 0.8]]})["HV"] - .21 / 1.21) < 1e-12
    old_files = {path.name: digest(path) for path in OLD.iterdir() if path.is_file()}
    manifest = pd.read_csv(OLD / "table-input-manifest.csv").set_index("Path").SHA256.to_dict()
    old_runs = pd.read_csv(OLD / "main-per-run-common-reference.csv", float_precision="round_trip")
    old_protocol = json.loads((OLD / "table-protocol.json").read_text())
    sources = {}

    def remember(path):
        checksum = digest(path)
        sources[str(path)] = checksum
        return checksum

    for name in ["main-per-run-common-reference.csv", "main-mean-std-common-reference.csv", "table-protocol.json", "table-input-manifest.csv"]:
        remember(OLD / name)
    records = []
    for old in old_runs.to_dict("records"):
        record = {key: value for key, value in old.items() if key not in METRICS}
        if old["Method"] == "PSLGP":
            directory = TESTS / f"S{old['Scenario']}" / f"run{old['Seed']:02d}" / "generation49"
            spec_path = directory / "test.properties"
            marker_path = directory / "completed.properties"
            spec, marker = properties(spec_path), properties(marker_path)
            remember(spec_path); remember(marker_path)
            assert marker["status"] == "COMPLETED" and marker["actual.generation"] == "49", directory
            assert configuration_hash(spec) == marker["configuration.sha256"], directory
            assert spec["method"] == "PSLGP_I" and spec["generation"] == "49" and spec["smoke"] == "false"
            assert spec["run"] == str(old["Seed"]) and spec["scenario"] == str(old["Scenario"])
            assert spec["preferences"] == "200" and spec["replications"] == "30" and spec["test.seed"] == "968356"
            assert spec["rule.count"] == "1" and spec["selection"] == "recorded Best Individual"
            for key in ["source.stats", "source.params"]:
                assert remember(Path(spec[key])) == spec[key + ".sha256"], spec[key]
                assert "PSLIGD_BR" in Path(spec[key]).parts
            text = Path(spec["source.stats"]).read_text(encoding="utf-8-sig")
            block = re.search(r"(?ms)^Generation: 49\s*\n(.*?)(?=^Generation: 50\s*$)", text)
            assert block is not None
            best = re.search(r"(?ms)^Best Individual:\s*\nSubpopulation 0:\s*\nEvaluated: true\s*\nFitness: \[([^\]]+)\]\s*\nTree 0:\s*\n([^\n]+)\s*\nTree 1:\s*\n([^\n]+)", block.group(1))
            assert best is not None and best.group(2).strip() == spec["rule.0.sequencing"] and best.group(3).strip() == spec["rule.0.routing"]
            assert np.array_equal([float(value) for value in best.group(1).split()],
                                  [float(spec["rule.0.fitness.0"]), float(spec["rule.0.fitness.1"])])
            path = directory / spec["output.csv"]
            assert remember(path) == marker["csv.sha256"]
            details_path = directory / "test-outcomes.csv"
            remember(details_path)
            frame = pd.read_csv(path, float_precision="round_trip")
            details = pd.read_csv(details_path, float_precision="round_trip")
            assert len(frame) == len(details) == 200
            assert set(frame.Run) == {old["Seed"]} and set(frame.ParetoFromGeneration) == {49} and set(frame.ind) == {0}
            assert np.array_equal(details.PreferenceIndex, np.arange(200)) and set(details.CandidateIndex) == {0}
            assert np.allclose(details.Weight0, np.arange(200) / 199, rtol=0, atol=1e-12)
            assert np.allclose(details.Weight1, np.arange(199, -1, -1) / 199, rtol=0, atol=1e-12)
            for objective in range(2):
                assert np.array_equal(frame[f"TestFitness{objective}"], details[f"TestFitness{objective}"])
                assert np.all(frame[f"TrainFitness{objective}"] == float(spec[f"rule.0.fitness.{objective}"]))
            assert frame[["SeqRuleSize", "SeqRuleUniqueTerminals", "RoutRuleSize", "RoutRuleUniqueTerminals"]].drop_duplicates().shape[0] == 1
            record.update(Source=str(path), SnapshotIndex=49, ActualGeneration=49,
                          Selection="recorded generation-49 Best Individual; fixed dual-tree heuristic over200 preferences")
        else:
            path = Path(old["Source"])
            assert remember(path) == manifest[str(path)], path
            frame = pd.read_csv(path, float_precision="round_trip")
            if old["Method"] == "Full":
                assert len(frame) == 200 and np.array_equal(frame.PreferenceIndex, np.arange(200))
                assert np.allclose(frame.Weight0, np.arange(200) / 199)
                record["ActualGeneration"] = 49
            else:
                assert int(frame.ParetoFromGeneration.max()) == old["SnapshotIndex"] == 29
                frame = frame[frame.ParetoFromGeneration == old["SnapshotIndex"]]
                assert set(frame.Run) == {old["Seed"]} and "ind" not in frame
                record["ActualGeneration"] = 50
                record["Selection"] = "serialized final generation-50 population Pareto front; not external archive"
        points = frame[["TestFitness0", "TestFitness1"]].to_numpy()
        valid = np.isfinite(points).all(axis=1) & (points >= 0).all(axis=1)
        assert valid.any()
        record.update(InputRows=len(points), InvalidRows=int((~valid).sum()), Points=points[valid])
        records.append(record)
    assert len(records) == 1440 and sum(record["Method"] == "PSLGP" for record in records) == 300
    pools = {}
    for scenario in range(1, 11):
        points = np.concatenate([record["Points"] for record in records if record["Scenario"] == scenario])
        minimum, maximum = points.min(axis=0), points.max(axis=0)
        assert (maximum > minimum).all()
        front = nondominated((points - minimum) / (maximum - minimum))
        pools[scenario] = {"Minimum": minimum.tolist(), "Maximum": maximum.tolist(), "Front": front.tolist()}
        pd.DataFrame(front, columns=["NormalizedObjective0", "NormalizedObjective1"]).to_csv(ROOT / f"scenario-{scenario}-reference.csv", index=False)
    per_run = []
    fixed_reference = []
    for record in records:
        scores = indicators(record["Points"], pools[record["Scenario"]])
        assert all(np.isfinite(value) and value >= 0 for value in scores.values())
        per_run.append({key: value for key, value in record.items() if key != "Points"} | scores)
        if record["Method"] == "PSLGP":
            reference = old_protocol["References"][str(record["Scenario"])] | {
                "Front": pd.read_csv(OLD / f"scenario-{record['Scenario']}-reference.csv").to_numpy().tolist()}
            fixed_reference.append({"Scenario": record["Scenario"], "Seed": record["Seed"]} | indicators(record["Points"], reference))
    runs = pd.DataFrame(per_run)
    assert not runs.duplicated(["Method", "Group", "Task", "Scenario", "Seed"]).any()
    means = runs.groupby(["Method", "Group", "Task", "Scenario"], sort=False).agg(
        Runs=("Seed", "size"), HVMean=("HV", "mean"), HVStd=("HV", "std"), IGDMean=("IGD", "mean"), IGDStd=("IGD", "std"),
        InputRows=("InputRows", "sum"), InvalidRows=("InvalidRows", "sum")).reset_index()
    assert len(means) == 48 and (means.Runs == 30).all()
    runs.to_csv(ROOT / "per-run.csv", index=False); means.to_csv(ROOT / "mean-std.csv", index=False)
    pd.DataFrame(fixed_reference).to_csv(ROOT / "pslgp49-old-reference.csv", index=False)
    full = means[means.Method == "Full"].assign(Order=lambda frame: frame.Group.map(GROUPS.index)).sort_values(["Order", "Task"])
    rows, comparisons = [], []
    for entry in full.itertuples():
        displayed = [entry.Group, str(entry.Scenario)]
        for metric in METRICS:
            selected = means[(means.Scenario == entry.Scenario) & ((means.Group == entry.Group) | (means.Group == "Independent"))].set_index("Method").loc[METHODS]
            values = selected[metric + "Mean"].to_numpy()
            best_mean = max(values) if metric == "HV" else min(values)
            for point in selected.itertuples():
                mean, deviation = getattr(point, metric + "Mean"), getattr(point, metric + "Std")
                cell = f"{mean:.3f}({deviation:.3f})"
                displayed.append(chr(92) + "textbf{" + cell + "}" if mean == best_mean else cell)
            for baseline, value in zip(METHODS[1:], values[1:]):
                comparisons.append({"Group": entry.Group, "Scenario": entry.Scenario, "Metric": metric, "Baseline": baseline,
                                    "FullMean": values[0], "BaselineMean": value,
                                    "FullMeanBetter": bool(values[0] > value if metric == "HV" else values[0] < value)})
        rows.append(" & ".join(displayed) + " " + chr(92) * 2)
    (ROOT / "table-rows.tex").write_text(chr(92) + "newcommand{" + chr(92) + "PSLGPFortyNineRows}{%\n" + "\n".join(rows) + "\n}\n", encoding="utf-8")
    comparison = pd.DataFrame(comparisons)
    comparison.to_csv(ROOT / "descriptive-comparisons.csv", index=False)
    favourable = comparison.groupby(["Baseline", "Metric"]).FullMeanBetter.sum().unstack()
    favourable.to_csv(ROOT / "favourable-means.csv")
    protocol = {"Methods": METHODS, "Metrics": METRICS, "RunCounts": runs.groupby("Method").size().to_dict(),
                "ActualGenerations": {"Full": 49, "PSLGP": 49, "NSGPII": 50, "MOGP/D": 50},
                "PSLGPSelection": "saved generation49 Best Individual, not generation50 Top-N rank0",
                "Normalization": "Common per-scenario min/max pooled over all four methods, all groups and seeds",
                "ReferenceFront": "Unique nondominated union of normalized valid outcomes from the four methods",
                "HV": "2D minimization, reference(1.1,1.1), divided by1.21", "IGD": "Arithmetic mean Euclidean distance from reference front to nearest valid run outcome",
                "Display": "Mean(sampleSD),30 evolutionary seeds,three decimals; bold uses unrounded means",
                "InvalidRows": runs.groupby("Method").InvalidRows.sum().to_dict(),
                "ChangedBoundsScenarios": [scenario for scenario in range(1, 11) if not np.allclose(pools[scenario]["Minimum"], old_protocol["References"][str(scenario)]["Minimum"], rtol=0, atol=1e-9)
                                            or not np.allclose(pools[scenario]["Maximum"], old_protocol["References"][str(scenario)]["Maximum"], rtol=0, atol=1e-9)],
                "References": pools, "PreviousAnalysisSHA256": old_files,
                "Inference": "Descriptive; not matched-budget, not fully generation-matched, and not a weight-correction-only causal comparison"}
    (ROOT / "protocol.json").write_text(json.dumps(protocol, indent=2), encoding="utf-8")
    for path, checksum in sources.items():
        assert digest(Path(path)) == checksum, path
    assert all(digest(OLD / name) == checksum for name, checksum in old_files.items())
    pd.DataFrame([{"Path": path, "SHA256": checksum} for path, checksum in sources.items()]).to_csv(ROOT / "input-manifest.csv", index=False)
    for name in ["mpslgp-method.tex", "mpslgp-method.pdf"]:
        target = ROOT / ("before-pslgp49-" + name)
        if not target.exists(): shutil.copy2(PAPER / name, target)
    report = "\n".join(["# PSLGP Generation-49 Four-Method Comparison", "", "PSLGP generation49 uses the saved Best Individual, whereas its previous generation50 comparison used Top-N rank0. Both generation and selection policy changed; this is not a generation-only effect.",
                       "", "MPSLGP and PSLGP use generation49; NSGPII and MOGP/D remain generation50. All methods share new scenario references. Raw test files and previous analysis are unchanged.",
                       "", "## Means and Sample Standard Deviations", means.to_markdown(index=False), "", "## Favourable Full Means (Descriptive Only)", favourable.to_markdown()])
    (ROOT / "analysis.md").write_text(report, encoding="utf-8")
    print(favourable.to_string())
    print("Changed normalization bounds:", protocol["ChangedBoundsScenarios"])
    print("PSLGP invalid preferences:", protocol["InvalidRows"]["PSLGP"])
    print("PASS: 300 completed PSLGP49 tests verified;1440 four-method scores recalculated;18-row three-decimal table generated;previous analysis preserved.")


def validate_pdf():
    import pymupdf
    protocol = json.loads((ROOT / "protocol.json").read_text())
    for entry in pd.read_csv(ROOT / "input-manifest.csv").itertuples(): assert digest(Path(entry.Path)) == entry.SHA256
    assert all(digest(OLD / name) == checksum for name, checksum in protocol["PreviousAnalysisSHA256"].items())
    runs, means = pd.read_csv(ROOT / "per-run.csv"), pd.read_csv(ROOT / "mean-std.csv")
    for entry in means.itertuples():
        selected = runs[(runs.Method == entry.Method) & (runs.Group == entry.Group) & (runs.Task == entry.Task) & (runs.Scenario == entry.Scenario)]
        assert len(selected) == 30
        for metric in METRICS:
            assert np.isclose(selected[metric].mean(), getattr(entry, metric + "Mean"), rtol=0, atol=1e-12)
            assert np.isclose(selected[metric].std(ddof=1), getattr(entry, metric + "Std"), rtol=0, atol=1e-12)
    lines = [line for line in (ROOT / "table-rows.tex").read_text().splitlines() if " & " in line]
    assert len(lines) == 18
    checked = 0
    with pymupdf.open(PAPER / "mpslgp-method.pdf") as document:
        text = " ".join(" ".join(page.get_text().split()) for page in document)
        pages = []
        for line in lines:
            cells = line[:-2].strip().split(" & "); assert len(cells) == 10
            for index, (metric, method) in enumerate((metric, method) for metric in METRICS for method in METHODS):
                selected = means[(means.Method == method) & (means.Scenario == int(cells[1]))]
                if method == "Full": selected = selected[selected.Group == cells[0]]
                entry = selected.iloc[0]
                actual = re.sub(r"\\textbf\{(.*?)\}", r"\1", cells[index + 2])
                expected = f"{entry[metric+'Mean']:.3f}({entry[metric+'Std']:.3f})"
                assert actual == expected and actual in text
                checked += 1
        for index, page in enumerate(document):
            if "Four-method HV and IGD comparison with retested PSLGP generation 49" in " ".join(page.get_text().split()):
                pages.append(index + 1)
                spans = [span for block in page.get_text("dict")["blocks"] if "lines" in block for line in block["lines"] for span in line["spans"]]
                assert all(span["color"] == 0x008040 for span in spans if re.search(r"\d\.\d{3}\(", span["text"]))
                page.get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5)).save(ROOT / f"table-page-{index+1:02d}.png")
        assert pages and checked == 144
        report = {"TableCellsVerified": checked, "TablePages": pages, "PDFPages": len(document),
                  "PSLGPGeneration": 49, "PSLGPTests": 300, "PSLGPPreferences": 60000,
                  "RawSourceFilesVerified": len(pd.read_csv(ROOT / "input-manifest.csv")), "PDFSHA256": digest(PAPER / "mpslgp-method.pdf")}
    (ROOT / "validation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2)); print("PASS: all144 table cells, green text, sampleSD, original inputs and previous analysis verified.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(); parser.add_argument("--validate-pdf", action="store_true")
    options = parser.parse_args()
    validate_pdf() if options.validate_pdf else build()