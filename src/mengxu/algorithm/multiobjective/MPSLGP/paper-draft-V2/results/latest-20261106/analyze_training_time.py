import argparse
import hashlib
import json
import re
from pathlib import Path

import javaproperties
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parent
OLD = ROOT.parent / "latest-20261103"
OUTPUT = ROOT / "training-time"
PAPER = ROOT.parent.parent
METHODS = ["NSGPII", "PSLGP", "MPSLGP"]
GROUPS = ["R1", "R2", "R3", "R4", "R5", "H1", "H2", "H3"]
REMOVED = ["fig:latest-ablation-quality", "fig:latest-transfer-training", "fig:latest-validity-response", "fig:latest-cost"]
TABLE = r"""\begin{table}[t]
\centering
\color{resultrevision}
\caption{\textcolor{red}{Recorded training time for the same task groups: mean(standard deviation), in minutes, calculated over all 30 runs. Times cover logged generations~0--49. MPSLGP is counted once per group; PSLGP and NSGPII sum the independent task times for the same seed.}}
\label{tab:latest-training-time}
\footnotesize
\setlength{\tabcolsep}{3pt}
\input{results/latest-20261106/training-time/table-rows.tex}
\begin{tabular}{@{}lc*{3}{c}@{}}
\toprule
Group & Scenarios & NSGPII & PSLGP & MPSLGP \\
\midrule
\TrainingTimeRows
\bottomrule
\end{tabular}
\end{table}
"""


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def properties(path):
    with path.open("rb") as stream:
        return javaproperties.load(stream)


def build():
    OUTPUT.mkdir(exist_ok=True)
    protected = {str(directory / name): digest(directory / name)
                 for directory in [ROOT, ROOT / "ablation-full-no-transfer"]
                 for name in ["per-run.csv", "mean-std.csv", "paired-tests.csv", "table-rows.tex", "protocol.json"]}
    sources = {}

    def remember(path, checksum=None):
        value = digest(path)
        if checksum is not None: assert value == checksum, path
        sources[str(path)] = value

    def elapsed(directory, seed, final_generation):
        path = directory / f"job.{seed}.time.csv"
        cumulative = directory / f"job.{seed}.timeSumGen.csv"
        remember(path); remember(cumulative)
        times = pd.read_csv(path, float_precision="round_trip").sort_values("Gen")
        totals = pd.read_csv(cumulative, float_precision="round_trip").sort_values("Gen")
        assert np.array_equal(times.Gen, np.arange(final_generation + 1)), path
        assert np.array_equal(totals.Gen, times.Gen), cumulative
        assert np.isfinite(times.Time).all() and (times.Time >= 0).all()
        assert np.allclose(times.Time.cumsum(), totals.timeSumGen, rtol=0, atol=1e-8), path
        selected = times[times.Gen <= 49]
        assert len(selected) == 50
        seconds = float(selected.Time.sum())
        assert seconds > 0 and abs(seconds - float(totals[totals.Gen == 49].timeSumGen.iloc[0])) < 1e-8
        return seconds, float(times.Time.sum()), str(path)

    main_path = ROOT / "per-run.csv"
    original_path = OLD / "input-manifest.csv"
    remember(main_path); remember(original_path)
    main = pd.read_csv(main_path, float_precision="round_trip")
    manifest = pd.read_csv(original_path)
    targets = main[main.Method == "Full"][["Group", "Task", "Scenario"]].drop_duplicates()
    assert len(targets) == 18
    mapping = {group: targets[targets.Group == group].sort_values("Task").Scenario.astype(int).tolist() for group in GROUPS}
    params = manifest[(manifest.Algorithm == "full") & manifest.Artifact.str.endswith("effective.params")].drop_duplicates(["Group", "Seed", "Artifact"])
    assert len(params) == 240
    records = []
    for entry in params.itertuples():
        path = Path(entry.Artifact)
        marker_path = path.parent.parent / "completed.properties"
        remember(path, entry.SHA256); remember(marker_path)
        marker, config = properties(marker_path), properties(path)
        assert marker["status"] == "COMPLETED" and marker["attempt"] == path.parent.name
        assert marker["sha256.effective.params"] == entry.SHA256 and config["generations"] == "50"
        seconds, total, source = elapsed(path.parent, entry.Seed, 49)
        wall = float(marker["duration.seconds"])
        assert seconds <= wall + 2, source
        records.append({"Method": "MPSLGP", "Group": entry.Group, "Scenario": -1, "Seed": entry.Seed,
                        "Seconds": seconds, "FullLoggedSeconds": total, "SourceFinalGeneration": 49,
                        "CompletionWallSeconds": wall, "Source": source})
    for entry in main[main.Method.isin(["PSLGP", "NSGPII"])].itertuples():
        source = Path(entry.Source)
        if entry.Method == "PSLGP":
            spec_path = source.parent / "test.properties"
            remember(spec_path)
            spec = properties(spec_path)
            assert spec["generation"] == "49" and int(spec["run"]) == entry.Seed and int(spec["scenario"]) == entry.Scenario
            stats = Path(spec["source.stats"])
            remember(stats, spec["source.stats.sha256"])
            config_path = Path(spec["source.params"])
            remember(config_path, spec["source.params.sha256"])
            assert properties(config_path)["generations"] == "51"
            seconds, total, filename = elapsed(stats.parent, entry.Seed, 50)
            wall = None
            final_generation = 50
        else:
            directory = source.parent.parent / "training"
            marker_path = directory / "completed.properties"
            config_path = directory / "effective.params"
            remember(marker_path); remember(config_path)
            marker, config = properties(marker_path), properties(config_path)
            assert marker["status"] == "COMPLETED" and marker["actual.final.generation"] == "49"
            assert digest(config_path) == marker["params.sha256"] and config["generations"] == "50"
            seconds, total, filename = elapsed(directory, entry.Seed, 49)
            wall = float(marker["training.wall.seconds"])
            assert seconds <= wall + 2, filename
            final_generation = 49
        records.append({"Method": entry.Method, "Group": "Independent", "Scenario": entry.Scenario, "Seed": entry.Seed,
                        "Seconds": seconds, "FullLoggedSeconds": total, "SourceFinalGeneration": final_generation,
                        "CompletionWallSeconds": wall, "Source": filename})
    runs = pd.DataFrame(records)
    assert len(runs) == 840 and set(runs.Method) == set(METHODS)
    assert not runs.duplicated(["Method", "Group", "Scenario", "Seed"]).any()
    runs.to_csv(OUTPUT / "per-run.csv", index=False)
    grouped = []
    for group, scenarios in mapping.items():
        for seed in range(30):
            for method in METHODS:
                selected = runs[(runs.Method == method) & (runs.Seed == seed)]
                if method == "MPSLGP": selected = selected[selected.Group == group]
                else: selected = selected[selected.Scenario.isin(scenarios)]
                assert len(selected) == (1 if method == "MPSLGP" else len(scenarios))
                grouped.append({"Method": method, "Group": group, "Seed": seed, "Tasks": len(scenarios),
                                "ConstituentRuns": len(selected), "Seconds": float(selected.Seconds.sum()),
                                "Minutes": float(selected.Seconds.sum()) / 60})
    grouped = pd.DataFrame(grouped)
    assert len(grouped) == 720
    grouped.to_csv(OUTPUT / "grouped-per-seed.csv", index=False)
    means = grouped.groupby(["Method", "Group"], sort=False).agg(
        Runs=("Seed", "size"), Tasks=("Tasks", "first"), MeanMinutes=("Minutes", "mean"), SDMinutes=("Minutes", "std")).reset_index()
    assert len(means) == 24 and (means.Runs == 30).all()
    means.to_csv(OUTPUT / "mean-std.csv", index=False)
    rows = []
    for group, scenarios in mapping.items():
        cells = [group, ", ".join(map(str, scenarios))]
        selected = means[means.Group == group].set_index("Method").loc[METHODS]
        for entry in selected.itertuples(): cells.append(f"{entry.MeanMinutes:.2f}({entry.SDMinutes:.2f})")
        rows.append(" & ".join(cells) + " " + chr(92) * 2)
    (OUTPUT / "table-rows.tex").write_text(chr(92) + "newcommand{" + chr(92) + "TrainingTimeRows}{%\n" + "\n".join(rows) + "\n}\n", encoding="utf-8")
    (OUTPUT / "table.tex").write_text(TABLE, encoding="utf-8")
    meta = {"Methods": METHODS, "TrainingRuns": 840, "MPSLGPRuns": 240, "IndependentRunsPerMethod": 300,
            "Groups": mapping, "IncludedGenerations": list(range(50)), "Unit": "minutes",
            "Definition": "Sum of logged per-generation elapsed seconds for 0..49, divided by60; independently validated against timeSumGen.csv",
            "GroupCost": "MPSLGP joint run counted once;independent task times summed for each group and seed;not parallel batch completion time",
            "Caveats": "Different hardware/concurrency and operators/population allocations;not a controlled speedup or equal-quality comparison;timing windows omit some setup/finalization",
            "ProtectedQualitySHA256": protected}
    (OUTPUT / "protocol.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    pd.DataFrame([{"Path": path, "SHA256": checksum} for path, checksum in sources.items()]).to_csv(OUTPUT / "input-manifest.csv", index=False)
    (OUTPUT / "analysis.md").write_text("# Three-Method Training Time\n\n" + meta["Definition"] + "\n\n" + meta["GroupCost"] + "\n\n" + meta["Caveats"] + "\n\n" + means.to_markdown(index=False), encoding="utf-8")
    assert all(digest(Path(path)) == checksum for path, checksum in sources.items())
    assert all(digest(Path(path)) == checksum for path, checksum in protected.items())
    print(means.pivot(index="Group", columns="Method", values="MeanMinutes").loc[GROUPS].to_string())
    print("PASS:840training timing logs/cumulative files;720group-seed costs;24mean/SDrows;PSLGPgeneration50excluded;quality data unchanged.")


def validate_pdf():
    import pymupdf

    meta = json.loads((OUTPUT / "protocol.json").read_text())
    for entry in pd.read_csv(OUTPUT / "input-manifest.csv").itertuples(): assert digest(Path(entry.Path)) == entry.SHA256
    assert all(digest(Path(path)) == checksum for path, checksum in meta["ProtectedQualitySHA256"].items())
    aux = (PAPER / "mpslgp-method.aux").read_text()
    assert all(label not in aux for label in REMOVED)
    log = (PAPER / "mpslgp-method.log").read_text(errors="replace")
    assert not re.search(r"Overfull|undefined references|LaTeX Error", log)
    means = pd.read_csv(OUTPUT / "mean-std.csv", float_precision="round_trip")
    pages = []
    with pymupdf.open(PAPER / "mpslgp-method.pdf") as document:
        for index, page in enumerate(document):
            text = " ".join(page.get_text().split())
            if "Recorded training time for the same task groups:" in text:
                pages.append(index + 1)
                for entry in means.itertuples(): assert f"{entry.MeanMinutes:.2f}({entry.SDMinutes:.2f})" in text
                page.get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5)).save(OUTPUT / f"table-page-{index+1:02d}.png")
        assert pages
        pdf_pages = len(document)
    report = {"TrainingTimeCells": 24, "TrainingRuns": 840, "TablePages": pages, "PDFPages": pdf_pages,
              "RemovedFigureLabels": REMOVED, "QualityDataUnchanged": True, "SourcesVerified": len(pd.read_csv(OUTPUT / "input-manifest.csv")),
              "PDFSHA256": digest(PAPER / "mpslgp-method.pdf")}
    (OUTPUT / "validation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2)); print("PASS:24trainingtime cells;fourfiguresremoved;sourcehashes/qualitydata/LaTeXchecks.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--validate-pdf", action="store_true")
    options = parser.parse_args()
    validate_pdf() if options.validate_pdf else build()