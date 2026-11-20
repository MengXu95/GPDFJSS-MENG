import argparse
import json
import re
import runpy
from pathlib import Path

import javaproperties
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "two-methods"
PAPER = ROOT.parent.parent
METHODS = ["Full", "PSLGP"]
METRICS = ["HV", "IGD"]
GROUPS = ["R1", "R2", "R3", "R4", "R5", "H1", "H2", "H3"]
PROTECTED = ["analyze_pslgp49.py", "main-table.tex", "table-rows.tex", "per-run.csv", "mean-std.csv",
             "protocol.json", "input-manifest.csv", "descriptive-comparisons.csv", "favourable-means.csv"]
TABLE = r"""\begin{table*}[t]
\centering
\color{resultrevision}
\caption{\resultrevision{Two-method HV and IGD comparison: mean(sample standard deviation) over 30 seeds per group/scenario, shown to three decimal places. Only Full MPSLGP and retested PSLGP-I contribute to the scenario-specific normalisation bounds and empirical reference fronts; NSGPII and MOGP/D outcomes are excluded from this calculation. Both methods use actual generation 49 and one fixed dual-tree heuristic across 200 preferences. MPSLGP selects task-specific Top-N rank 0; PSLGP uses its recorded Best Individual. Higher HV and lower IGD are better; bold marks the best unrounded mean, not statistical significance. Two invalid PSLGP preferences are excluded conditionally; all seeds remain. This is not a population-budget- or selection-matched experiment.}}
\label{tab:latest-two-method-quality}
\footnotesize
\setlength{\tabcolsep}{6pt}
\input{results/latest-20261104/two-methods/table-rows.tex}
\begin{tabular}{@{}lc*{4}{c}@{}}
\toprule
 & & \multicolumn{2}{c}{HV $\uparrow$} & \multicolumn{2}{c}{IGD $\downarrow$} \\
\cmidrule(lr){3-4}\cmidrule(lr){5-6}
Group & Scenario & Full MPSLGP & PSLGP & Full MPSLGP & PSLGP \\
\midrule
\MPSLGPPSLGPOnlyRows
\bottomrule
\end{tabular}
\end{table*}
"""


def build():
    shared = runpy.run_path(str(ROOT / "analyze_pslgp49.py"))
    digest, nondominated, indicators = shared["digest"], shared["nondominated"], shared["indicators"]
    protected = {name: digest(ROOT / name) for name in PROTECTED}
    expected_hashes = pd.read_csv(ROOT / "input-manifest.csv").set_index("Path").SHA256.to_dict()
    original = pd.read_csv(ROOT / "per-run.csv", float_precision="round_trip")
    selected = original[original.Method.isin(METHODS)]
    assert len(selected) == 840 and set(selected.Method) == set(METHODS) and set(selected.ActualGeneration) == {49}
    sources, records = {}, []

    def remember(path):
        value = digest(path)
        assert value == expected_hashes[str(path)], path
        sources[str(path)] = value
        return value

    for entry in selected.to_dict("records"):
        path = Path(entry["Source"])
        checksum = remember(path)
        frame = pd.read_csv(path, float_precision="round_trip")
        assert len(frame) == entry["InputRows"] == 200
        if entry["Method"] == "Full":
            assert np.array_equal(frame.PreferenceIndex, np.arange(200))
            assert np.allclose(frame.Weight0, np.arange(200) / 199, rtol=0, atol=1e-12)
            assert np.allclose(frame.Weight1, np.arange(199, -1, -1) / 199, rtol=0, atol=1e-12)
        else:
            with (path.parent / "test.properties").open("rb") as stream: spec = javaproperties.load(stream)
            with (path.parent / "completed.properties").open("rb") as stream: marker = javaproperties.load(stream)
            assert marker["status"] == "COMPLETED" and marker["actual.generation"] == spec["generation"] == "49"
            assert checksum == marker["csv.sha256"] and shared["configuration_hash"](spec) == marker["configuration.sha256"]
            assert spec["method"] == "PSLGP_I" and spec["smoke"] == "false" and spec["rule.count"] == "1"
            assert spec["selection"] == "recorded Best Individual"
            assert spec["preferences"] == "200" and spec["replications"] == "30" and spec["test.seed"] == "968356"
            assert spec["run"] == str(entry["Seed"]) and spec["scenario"] == str(entry["Scenario"])
            for name in ["test.properties", "completed.properties", "test-outcomes.csv"]: remember(path.parent / name)
            for key in ["source.stats", "source.params"]: assert remember(Path(spec[key])) == spec[key + ".sha256"]
            details = pd.read_csv(path.parent / "test-outcomes.csv", float_precision="round_trip")
            assert len(details) == 200 and np.array_equal(details.PreferenceIndex, np.arange(200))
            assert np.allclose(details.Weight0, np.arange(200) / 199, rtol=0, atol=1e-12)
            assert np.allclose(details.Weight1, np.arange(199, -1, -1) / 199, rtol=0, atol=1e-12)
            assert set(frame.Run) == {entry["Seed"]} and set(frame.ParetoFromGeneration) == {49} and set(frame.ind) == {0}
            for objective in range(2): assert np.array_equal(frame[f"TestFitness{objective}"], details[f"TestFitness{objective}"])
        points = frame[["TestFitness0", "TestFitness1"]].to_numpy()
        valid = np.isfinite(points).all(axis=1) & (points >= 0).all(axis=1)
        assert valid.any() and int((~valid).sum()) == entry["InvalidRows"]
        records.append({key: value for key, value in entry.items() if key not in METRICS} | {"Points": points[valid]})
    OUTPUT.mkdir(exist_ok=True)
    pools = {}
    for scenario in range(1, 11):
        points = np.concatenate([entry["Points"] for entry in records if entry["Scenario"] == scenario])
        minimum, maximum = points.min(axis=0), points.max(axis=0)
        assert (maximum > minimum).all()
        front = nondominated((points - minimum) / (maximum - minimum))
        pools[scenario] = {"Minimum": minimum.tolist(), "Maximum": maximum.tolist(), "Front": front.tolist()}
        pd.DataFrame(front, columns=["NormalizedObjective0", "NormalizedObjective1"]).to_csv(OUTPUT / f"scenario-{scenario}-reference.csv", index=False)
    rows = [{key: value for key, value in entry.items() if key != "Points"} | indicators(entry["Points"], pools[entry["Scenario"]]) for entry in records]
    runs = pd.DataFrame(rows)
    assert not runs.duplicated(["Method", "Group", "Task", "Scenario", "Seed"]).any()
    means = runs.groupby(["Method", "Group", "Task", "Scenario"], sort=False).agg(
        Runs=("Seed", "size"), HVMean=("HV", "mean"), HVStd=("HV", "std"), IGDMean=("IGD", "mean"), IGDStd=("IGD", "std"),
        InputRows=("InputRows", "sum"), InvalidRows=("InvalidRows", "sum")).reset_index()
    assert len(means) == 28 and (means.Runs == 30).all()
    assert np.isfinite(runs[METRICS]).all().all() and (runs[METRICS] >= 0).all().all()
    runs.to_csv(OUTPUT / "per-run.csv", index=False); means.to_csv(OUTPUT / "mean-std.csv", index=False)
    full = means[means.Method == "Full"].assign(Order=lambda frame: frame.Group.map(GROUPS.index)).sort_values(["Order", "Task"])
    table_rows, comparisons = [], []
    for entry in full.itertuples():
        displayed = [entry.Group, str(entry.Scenario)]
        for metric in METRICS:
            target = means[(means.Scenario == entry.Scenario) & ((means.Group == entry.Group) | (means.Group == "Independent"))].set_index("Method").loc[METHODS]
            values = target[metric + "Mean"].to_numpy()
            best = max(values) if metric == "HV" else min(values)
            for value in target.itertuples():
                mean, deviation = getattr(value, metric + "Mean"), getattr(value, metric + "Std")
                cell = f"{mean:.3f}({deviation:.3f})"
                displayed.append(chr(92) + "textbf{" + cell + "}" if mean == best else cell)
            comparisons.append({"Group": entry.Group, "Scenario": entry.Scenario, "Metric": metric,
                                "FullMean": values[0], "PSLGPMean": values[1],
                                "FullMeanBetter": bool(values[0] > values[1] if metric == "HV" else values[0] < values[1])})
        table_rows.append(" & ".join(displayed) + " " + chr(92) * 2)
    (OUTPUT / "table-rows.tex").write_text(chr(92) + "newcommand{" + chr(92) + "MPSLGPPSLGPOnlyRows}{%\n" + "\n".join(table_rows) + "\n}\n", encoding="utf-8")
    (OUTPUT / "table.tex").write_text(TABLE, encoding="utf-8")
    comparisons = pd.DataFrame(comparisons); comparisons.to_csv(OUTPUT / "descriptive-comparisons.csv", index=False)
    wins = comparisons.groupby("Metric").FullMeanBetter.sum().to_dict()
    old_protocol = json.loads((ROOT / "protocol.json").read_text())
    changed = [scenario for scenario in pools if not np.allclose(pools[scenario]["Minimum"], old_protocol["References"][str(scenario)]["Minimum"], rtol=0, atol=1e-9)
               or not np.allclose(pools[scenario]["Maximum"], old_protocol["References"][str(scenario)]["Maximum"], rtol=0, atol=1e-9)]
    protocol = {"Methods": METHODS, "ActualGenerations": {method: 49 for method in METHODS}, "Metrics": METRICS,
                "RunCounts": runs.groupby("Method").size().to_dict(), "Normalization": "Per-scenario min/max pooled ONLY over Full MPSLGP and PSLGP-I, all their groups and seeds",
                "ReferenceFront": "Distinct nondominated union of valid normalized outcomes from ONLY the two compared methods",
                "HV": old_protocol["HV"], "IGD": old_protocol["IGD"], "InvalidRows": runs.groupby("Method").InvalidRows.sum().to_dict(),
                "FullFavourableMeans": wins, "ChangedBoundsScenarios": changed, "References": pools, "TableVIProtectedSHA256": protected,
                "Limitations": "Generation49 for both, but task Top-N vs recorded Best Individual selection and shared vs per-task1000 populations remain different; no significance claim"}
    (OUTPUT / "protocol.json").write_text(json.dumps(protocol, indent=2), encoding="utf-8")
    pd.DataFrame([{"Path": path, "SHA256": checksum} for path, checksum in sources.items()]).to_csv(OUTPUT / "input-manifest.csv", index=False)
    report = "\n".join(["# MPSLGP versus Retested PSLGP-I", "", protocol["Normalization"], "", protocol["Limitations"], "", means.to_markdown(index=False),
                       "", "Full favourable target means: " + str(wins), "Changed bounds versus four-method table: " + str(changed)])
    (OUTPUT / "analysis.md").write_text(report, encoding="utf-8")
    assert all(digest(Path(path)) == checksum for path, checksum in sources.items())
    assert all(digest(ROOT / name) == checksum for name, checksum in protected.items())
    print("Full favourable means:", wins, "out of18 for each metric")
    print("Changed bounds:", changed)
    print("PASS: only840 generation49 runs from Full/PSLGP; two-method references recomputed; TableVI unchanged.")


def validate_pdf():
    import pymupdf
    shared = runpy.run_path(str(ROOT / "analyze_pslgp49.py")); digest = shared["digest"]
    protocol = json.loads((OUTPUT / "protocol.json").read_text())
    assert all(digest(ROOT / name) == checksum for name, checksum in protocol["TableVIProtectedSHA256"].items())
    for entry in pd.read_csv(OUTPUT / "input-manifest.csv").itertuples(): assert digest(Path(entry.Path)) == entry.SHA256
    runs = pd.read_csv(OUTPUT / "per-run.csv", float_precision="round_trip")
    means = pd.read_csv(OUTPUT / "mean-std.csv", float_precision="round_trip")
    assert len(runs) == 840 and len(means) == 28 and set(runs.Method) == set(METHODS)
    for entry in means.itertuples():
        samples = runs[(runs.Method == entry.Method) & (runs.Group == entry.Group) & (runs.Task == entry.Task) & (runs.Scenario == entry.Scenario)]
        assert len(samples) == 30 and set(samples.Seed) == set(range(30))
        for metric in METRICS:
            assert np.isclose(samples[metric].mean(), getattr(entry, metric + "Mean"), rtol=0, atol=1e-12)
            assert np.isclose(samples[metric].std(ddof=1), getattr(entry, metric + "Std"), rtol=0, atol=1e-12)
    lines = [line for line in (OUTPUT / "table-rows.tex").read_text().splitlines() if " & " in line]
    assert len(lines) == 18
    checked = 0
    with pymupdf.open(PAPER / "mpslgp-method.pdf") as document:
        text = " ".join(" ".join(page.get_text().split()) for page in document)
        pages = []
        for line in lines:
            cells = line[:-2].strip().split(" & "); assert len(cells) == 6
            for metric_index, metric in enumerate(METRICS):
                target = means[(means.Scenario == int(cells[1])) & ((means.Group == cells[0]) | (means.Group == "Independent"))].set_index("Method").loc[METHODS]
                values = target[metric + "Mean"].to_numpy(); best = max(values) if metric == "HV" else min(values)
                for method_index, entry in enumerate(target.itertuples()):
                    actual = cells[2 + metric_index * 2 + method_index]
                    clean = re.sub(r"\\textbf\{(.*?)\}", r"\1", actual)
                    expected = f"{getattr(entry, metric+'Mean'):.3f}({getattr(entry, metric+'Std'):.3f})"
                    assert clean == expected and clean in text
                    assert actual.startswith(chr(92) + "textbf{") == (getattr(entry, metric + "Mean") == best)
                    checked += 1
        for index, page in enumerate(document):
            if "Two-method HV and IGD comparison:" in " ".join(page.get_text().split()):
                pages.append(index + 1)
                spans = [span for block in page.get_text("dict")["blocks"] if "lines" in block for line in block["lines"] for span in line["spans"]]
                assert all(span["color"] == 0x008040 for span in spans if re.search(r"\d\.\d{3}\(", span["text"]))
                page.get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5)).save(OUTPUT / f"table-page-{index+1:02d}.png")
        assert pages and checked == 72
    assert re.search(r"\\newlabel\{tab:latest-main-quality\}\{\{VI\}", (PAPER / "mpslgp-method.aux").read_text())
    report = {"TableVIUnchanged": True, "NewTableCellsVerified": checked, "TablePages": pages,
              "Generation49Runs": 840, "Methods": METHODS, "FullFavourableMeans": protocol["FullFavourableMeans"],
              "RawSourceFilesVerified": len(pd.read_csv(OUTPUT / "input-manifest.csv")), "PDFSHA256": digest(PAPER / "mpslgp-method.pdf")}
    (OUTPUT / "validation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2)); print("PASS: all72 new cells, correct bold means, green PDF, sampleSD, source protection and TableVI unchanged.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(); parser.add_argument("--validate-pdf", action="store_true")
    options = parser.parse_args()
    validate_pdf() if options.validate_pdf else build()