import argparse
import hashlib
import json
import re
import runpy
from pathlib import Path

import javaproperties
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parent
OLD = ROOT.parent / "latest-20261103"
OUTPUT = ROOT / "ablation-full-no-transfer"
PAPER = ROOT.parent.parent
METHODS = ["no-transfer", "Full"]
CONTROLS = METHODS[:1]
METRICS = ["HV", "IGD", "GD"]
GROUPS = ["R1", "R2", "R3", "R4", "R5", "H1", "H2", "H3"]
MAIN_FILES = ["main-table.tex", "table-rows.tex", "per-run.csv", "mean-std.csv", "paired-tests.csv", "protocol.json"]
TABLE = r"""\begin{table*}[t]
\centering
\color{resultrevision}
\caption{\resultrevision{Transfer-ablation HV, IGD and GD results: mean(sample standard deviation) over 30 paired training seeds per group/scenario, shown to three decimal places. Full and no-transfer share scenario bounds and empirical reference fronts pooled only over these two profiles. At each no-transfer entry, $(\uparrow)$, $(\downarrow)$ and $(=)$ mean Full is significantly better, worse or not significantly different under an exact two-sided paired Wilcoxon signed-rank test at $\alpha=0.05$, without multiplicity correction. Direction follows paired signed-rank sums; bold marks the best unrounded mean separately. Higher HV and lower IGD/GD are better. Invalid no-transfer preferences are excluded conditionally; all seeds remain. These scores use a different reference family from Table~\ref{tab:latest-main-quality}.}}
\label{tab:latest-ablation-quality}
\footnotesize
\setlength{\tabcolsep}{2pt}
\input{results/latest-20261106/ablation-full-no-transfer/table-rows.tex}
\begin{tabular}{@{}lc*{6}{c}@{}}
\toprule
 & & \multicolumn{2}{c}{HV $\uparrow$} & \multicolumn{2}{c}{IGD $\downarrow$} & \multicolumn{2}{c}{GD $\downarrow$} \\
\cmidrule(lr){3-4}\cmidrule(lr){5-6}\cmidrule(lr){7-8}
Group & Scenario & No-transfer & Full & No-transfer & Full & No-transfer & Full \\
\midrule
\UnifiedAblationRows
\bottomrule
\end{tabular}
\end{table*}
"""


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def properties(path):
    with path.open("rb") as stream:
        return javaproperties.load(stream)


def plot_diagnostics(runs, sources, raw_records):
    import matplotlib.pyplot as plt

    colours = {"Full": "#008040", "no-transfer": "#2563EB"}
    order = ["Full", "no-transfer"]
    labels = ["Full", "No-transfer"]

    def save(figure, name):
        figure.savefig(OUTPUT / f"{name}.pdf", bbox_inches="tight", pad_inches=.09)
        figure.savefig(OUTPUT / f"{name}.png", bbox_inches="tight", pad_inches=.09, dpi=210)
        plt.close(figure)

    validity_rows = []
    for method in order:
        selected = runs[runs.Method == method]
        validity_rows.append({"Method": method, "TaskRuns": len(selected), "PreferenceRows": len(selected) * 200,
                              "InvalidPreferences": int(selected.InvalidPreferences.sum()),
                              "RunsWithInvalid": int((selected.InvalidPreferences > 0).sum()),
                              "SingleOutcomeRuns": int((selected.UniqueOutcomes == 1).sum()),
                              "MeanDistinctOutcomes": float(selected.UniqueOutcomes.mean()),
                              "MeanNondominatedOutcomes": float(selected.NondominatedOutcomes.mean())})
    validity = pd.DataFrame(validity_rows)
    validity.to_csv(OUTPUT / "method-validity-response.csv", index=False)
    figure, axes = plt.subplots(1, 3, figsize=(13, 3.6))
    for axis, field, title, denominator in zip(axes,
            ["InvalidPreferences", "SingleOutcomeRuns", "MeanNondominatedOutcomes"],
            ["Invalid preference outcomes", "Single-outcome heuristics", "Within-heuristic front size"], [108000, 540, None]):
        values = validity[field].to_numpy()
        bars = axis.bar(range(2), values, color=[colours[method] for method in order], width=.55)
        for bar, value in zip(bars, values):
            annotation = f"{int(value)}\n({value / denominator * 100:.2f}%)" if denominator else f"{value:.2f}"
            axis.annotate(annotation, (bar.get_x() + bar.get_width() / 2, bar.get_height()), xytext=(0, 5),
                          textcoords="offset points", ha="center", va="bottom", fontsize=11, color="#008040")
        axis.set_xticks(range(2), labels)
        axis.set_title(title)
        axis.set_ylim(0, max(values) * 1.42)
        axis.set_ylabel("Count" if denominator else "Mean nondominated outcomes")
        axis.spines[["top", "right"]].set_visible(False)
    figure.tight_layout()
    save(figure, "validity-and-response")

    training = runs[["Method", "Group", "Seed", "TrainingSeconds"]].drop_duplicates()
    assert len(training) == 480 and not training.duplicated(["Method", "Group", "Seed"]).any()
    runtime = training.groupby(["Method", "Group"]).TrainingSeconds.agg(["mean", "std"]).reset_index()
    runtime[["mean", "std"]] /= 60
    runtime.to_csv(OUTPUT / "group-training-minutes.csv", index=False)
    nodes = runs.assign(TotalNodes=runs.SeqNodes + runs.RoutingNodes)
    nodes.groupby("Method").TotalNodes.agg(["mean", "std"]).to_csv(OUTPUT / "rule-complexity.csv")
    figure, axes = plt.subplots(1, 2, figsize=(13, 4.1))
    for method, label in zip(order, labels):
        selected = runtime[runtime.Method == method].set_index("Group").loc[GROUPS]
        axes[0].errorbar(range(8), selected["mean"], yerr=selected["std"], fmt="o-", color=colours[method],
                         capsize=2, markersize=4, linewidth=1.2, label=label)
        summary = nodes[nodes.Method == method].groupby("Group").TotalNodes.mean().loc[GROUPS]
        axes[1].plot(range(8), summary, "o-", color=colours[method], markersize=4, linewidth=1.2, label=label)
    axes[0].set_title("Whole-group training time")
    axes[0].set_ylabel("Minutes: mean and sample standard deviation")
    axes[1].set_title("Tested dual-tree heuristic size")
    axes[1].set_ylabel("Mean total GP nodes")
    for axis in axes:
        axis.set_xticks(range(8), GROUPS)
        axis.spines[["top", "right"]].set_visible(False)
        axis.grid(axis="x", color="#E4EBE6", linewidth=.6)
    figure.legend(*axes[0].get_legend_handles_labels(), loc="lower center", ncol=2, frameon=False, bbox_to_anchor=(.53, -.015))
    figure.tight_layout(rect=(0, .08, 1, 1))
    save(figure, "runtime-and-complexity")

    path = OLD / "training-transfer-per-run.csv"
    provenance = OLD / "training-transfer-provenance.json"
    sources[str(path)] = digest(path)
    sources[str(provenance)] = digest(provenance)
    for filename, checksum in json.loads(provenance.read_text())["sources"].items():
        assert digest(Path(filename)) == checksum
        sources[filename] = checksum
    values = pd.read_csv(path, float_precision="round_trip")
    assert len(values) == 240 * 35 and not values.duplicated(["Group", "Seed", "Generation"]).any()
    values.to_csv(OUTPUT / "training-transfer-per-run.csv", index=False)
    figure, axes = plt.subplots(2, 4, figsize=(13, 5.6), sharex=True, sharey=True)
    summary_rows = []
    for group, axis in zip(GROUPS, axes.flat):
        selected = values[values.Group == group]
        summary = selected.groupby("Generation").MeanTaskRegionProbability.agg(["mean", "std", "size"])
        assert set(summary.index) == set(range(15, 50)) and (summary["size"] == 30).all()
        axis.plot(summary.index, summary["mean"], color=colours["Full"], linewidth=1.8, label="Full: task-region mean")
        axis.fill_between(summary.index, summary["mean"] - summary["std"], summary["mean"] + summary["std"],
                          color=colours["Full"], alpha=.14)
        axis.axhline(0, color=colours["no-transfer"], linestyle=":", linewidth=1.2, label="No-transfer")
        axis.set_title(group)
        axis.set_ylim(-.02, .43)
        axis.set_xlabel("Generation")
        if group in ("R1", "R5"): axis.set_ylabel("Cross-task mating probability")
        axis.spines[["top", "right"]].set_visible(False)
        axis.grid(color="#E4EBE6", linewidth=.6)
        summary_rows.append({"Group": group, "Runs": 30, "InitialFullProbability": float(summary.loc[15, "mean"]),
                             "FinalFullProbability": float(summary.loc[49, "mean"]),
                             "FinalFullProbabilityStd": float(summary.loc[49, "std"]), "NoTransferProbability": 0})
    figure.legend(*axes.flat[0].get_legend_handles_labels(), loc="lower center", ncol=2, frameon=False, bbox_to_anchor=(.52, .012))
    figure.tight_layout(rect=(0, .09, 1, 1))
    save(figure, "training-transfer-probabilities")
    pd.DataFrame(summary_rows).to_csv(OUTPUT / "training-transfer-summary.csv", index=False)

    rank_frames = []
    for group in ("R4", "H3"):
        selected = runs[(runs.Method == "Full") & (runs.Group == group)].copy()
        selected["Rank"] = selected.groupby("Task").IGD.rank(method="average")
        rank_frames.append(selected.groupby("Seed").Rank.mean())
    ranking = pd.concat(rank_frames, axis=1).mean(axis=1).sort_values(kind="stable")
    seed = int(ranking.index[14])
    pd.DataFrame({"Seed": ranking.index, "MeanTaskRank": ranking.to_numpy()}).to_csv(OUTPUT / "representative-seed-ranking.csv", index=False)
    path = OLD / "full-summary/protocol.properties"
    sources[str(path)] = digest(path)
    protocol = properties(path)
    figure, axes = plt.subplots(2, 3, figsize=(13, 6.4))
    representative = []
    for group_index, group in enumerate(("R4", "H3")):
        selected = [entry for entry in raw_records if entry["Algorithm"] == "full" and entry["Group"] == group and entry["Seed"] == seed]
        assert len(selected) == 3
        for entry in selected:
            axis = axes[group_index, entry["Task"]]
            points = entry["Points"]
            assert len(points) == 200
            minimum = np.asarray(json.loads(protocol[f"scenario.{entry['Scenario']}.min"]))
            maximum = np.asarray(json.loads(protocol[f"scenario.{entry['Scenario']}.max"]))
            normalized = (points - minimum) / (maximum - minimum)
            axis.plot(np.arange(200) / 199, normalized[:, 0], color="#2563EB", linewidth=1.6, label="Objective 1")
            axis.plot(np.arange(200) / 199, normalized[:, 1], color="#F97316", linewidth=1.6, label="Objective 2")
            axis.set_title(f"{group} / S{entry['Scenario']}, seed {seed}")
            axis.set_xlabel("Input preference weight w1")
            axis.set_ylabel("Normalized mean objective")
            axis.spines[["top", "right"]].set_visible(False)
            axis.grid(axis="x", color="#E4EBE6", linewidth=.6)
            for index, point in enumerate(points):
                representative.append({"Group": group, "Task": entry["Task"], "Scenario": entry["Scenario"], "Seed": seed,
                                       "Weight0": index / 199, "Objective0": point[0], "Objective1": point[1]})
    figure.legend(*axes[0, 0].get_legend_handles_labels(), loc="lower center", ncol=2, frameon=False, bbox_to_anchor=(.53, 0))
    figure.tight_layout(rect=(0, .055, 1, 1))
    save(figure, "latest-preference-response")
    pd.DataFrame(representative).to_csv(OUTPUT / "representative-preference-data.csv", index=False)
    print(f"Representative Full seed from two-profile IGD ranks: {seed}")


def build():
    shared = runpy.run_path(str(ROOT.parent / "latest-20261104/analyze_pslgp49.py"))
    main = runpy.run_path(str(ROOT / "analyze_three_generation49.py"))
    statistics = runpy.run_path(str(OLD / "build_result_figures.py"))
    plot = runpy.run_path(str(ROOT / "plot_table_vi.py"))["plot_comparison"]
    protected_main = {name: digest(ROOT / name) for name in MAIN_FILES}
    protected_previous = {str(path): digest(path) for path in (ROOT / "ablation").rglob("*") if path.is_file()}
    completed = properties(OLD / "completed.properties")
    protected_old = {str(OLD / key[len("sha256."):]): value for key, value in completed.items() if key.startswith("sha256.")}
    assert all(digest(Path(path)) == checksum for path, checksum in protected_old.items())
    samples = pd.read_csv(OLD / "per-run.csv", float_precision="round_trip")
    samples = samples[samples.Algorithm.isin(["full", "no-transfer"])].copy()
    manifest = pd.read_csv(OLD / "input-manifest.csv")
    inputs = manifest[manifest.Artifact.str.endswith("preference-results.csv") & manifest.Algorithm.isin(["full", "no-transfer"])]
    assert len(samples) == len(inputs) == 1080
    assert set(samples.Algorithm) == {"full", "no-transfer"}
    sources = protected_old.copy()
    records = []
    for entry in inputs.itertuples():
        path = Path(entry.Artifact)
        assert digest(path) == entry.SHA256
        sources[str(path)] = entry.SHA256
        frame = pd.read_csv(path, float_precision="round_trip")
        assert len(frame) == 200 and np.array_equal(frame.PreferenceIndex, np.arange(200))
        assert np.allclose(frame.Weight0, np.arange(200) / 199, rtol=0, atol=1e-12)
        assert np.allclose(frame.Weight1, np.arange(199, -1, -1) / 199, rtol=0, atol=1e-12)
        points = frame[["TestFitness0", "TestFitness1"]].to_numpy()
        valid = np.isfinite(points).all(axis=1) & (points >= 0).all(axis=1)
        assert valid.any()
        row = samples[(samples.Algorithm == entry.Algorithm) & (samples.Group == entry.Group)
                      & (samples.Task == entry.Task) & (samples.Seed == entry.Seed)]
        assert len(row) == 1 and int(row.iloc[0].Scenario) == entry.Scenario
        assert row.iloc[0].ValidPreferences == int(valid.sum()) and row.iloc[0].InvalidPreferences == int((~valid).sum())
        records.append({"Algorithm": entry.Algorithm, "Group": entry.Group, "Task": entry.Task,
                        "Scenario": entry.Scenario, "Seed": entry.Seed, "Source": str(path), "Points": points[valid]})
    pools = {}
    for scenario in range(1, 11):
        points = np.concatenate([entry["Points"] for entry in records if entry["Scenario"] == scenario])
        minimum, maximum = points.min(axis=0), points.max(axis=0)
        front = shared["nondominated"]((points - minimum) / (maximum - minimum))
        front = front[np.lexsort((front[:, 1], front[:, 0]))]
        pools[scenario] = {"Minimum": minimum.tolist(), "Maximum": maximum.tolist(), "Front": front.tolist()}
    runs = samples.rename(columns={"Algorithm": "Method", **{metric + "Joint": metric for metric in METRICS}}).copy()
    runs["Method"] = runs.Method.replace({"full": "Full"})
    runs["ActualGeneration"] = 49
    for entry in records:
        method = "Full" if entry["Algorithm"] == "full" else entry["Algorithm"]
        selected = ((runs.Method == method) & (runs.Group == entry["Group"])
                    & (runs.Task == entry["Task"]) & (runs.Seed == entry["Seed"]))
        assert selected.sum() == 1
        reference = pools[entry["Scenario"]]
        scores = shared["indicators"](entry["Points"], reference) | {"GD": main["generational_distance"](entry["Points"], reference)}
        for metric, value in scores.items(): runs.loc[selected, metric] = value
    assert not runs.duplicated(["Method", "Group", "Task", "Scenario", "Seed"]).any()
    means = runs.groupby(["Method", "Group", "Task", "Scenario"], sort=False).agg(
        Runs=("Seed", "size"), HVMean=("HV", "mean"), HVStd=("HV", "std"), IGDMean=("IGD", "mean"), IGDStd=("IGD", "std"),
        GDMean=("GD", "mean"), GDStd=("GD", "std"), InvalidPreferences=("InvalidPreferences", "sum")).reset_index()
    assert len(means) == 36 and (means.Runs == 30).all()
    OUTPUT.mkdir(exist_ok=True)
    for scenario, reference in pools.items():
        pd.DataFrame(reference["Front"], columns=["Objective0", "Objective1"]).to_csv(OUTPUT / f"scenario-{scenario}-reference.csv", index=False)
    runs.to_csv(OUTPUT / "per-run.csv", index=False); means.to_csv(OUTPUT / "mean-std.csv", index=False)
    targets = means[means.Method == "Full"].assign(Order=lambda frame: frame.Group.map(GROUPS.index)).sort_values(["Order", "Task"])
    rows, comparisons, tests = [], [], []
    for entry in targets.itertuples():
        cells = [entry.Group, str(entry.Scenario)]
        for metric in METRICS:
            target = means[(means.Group == entry.Group) & (means.Task == entry.Task)].set_index("Method").loc[METHODS]
            values = target[metric + "Mean"].to_numpy(); best = max(values) if metric == "HV" else min(values)
            selected = runs[(runs.Method == "Full") & (runs.Group == entry.Group) & (runs.Task == entry.Task)].set_index("Seed").sort_index()
            assert list(selected.index) == list(range(30))
            symbols = {}
            for control in CONTROLS:
                baseline = runs[(runs.Method == control) & (runs.Group == entry.Group) & (runs.Task == entry.Task)].set_index("Seed").sort_index()
                assert list(baseline.index) == list(range(30))
                gains = (selected[metric] - baseline[metric]).to_numpy() * (1 if metric == "HV" else -1)
                ranks = statistics["rankdata"](np.abs(gains[gains != 0])); nonzero = gains[gains != 0]
                positive, negative = float(ranks[nonzero > 0].sum()), float(ranks[nonzero < 0].sum())
                probability = statistics["exact_signed_rank"](gains)
                symbol = "=" if probability > .05 else r"\uparrow" if positive > negative else r"\downarrow" if positive < negative else "="
                symbols[control] = symbol
                mean = float(target.loc["Full", metric + "Mean"]); baseline_mean = float(target.loc[control, metric + "Mean"])
                result = {"Group": entry.Group, "Task": entry.Task, "Scenario": entry.Scenario, "Metric": metric, "Control": control,
                          "FullMean": mean, "ControlMean": baseline_mean, "FullMeanBetter": bool(mean > baseline_mean if metric == "HV" else mean < baseline_mean)}
                comparisons.append(result)
                tests.append(result | {"Runs": 30, "PValue": probability, "Alpha": .05, "Correction": "none",
                                       "PositiveRankSum": positive, "NegativeRankSum": negative, "Symbol": symbol,
                                       "Significant": probability <= .05})
            for row in target.itertuples():
                mean, deviation = getattr(row, metric + "Mean"), getattr(row, metric + "Std")
                cell = f"{mean:.3f}({deviation:.3f})"
                if mean == best: cell = chr(92) + "textbf{" + cell + "}"
                if row.Index in CONTROLS: cell += "$(" + symbols[row.Index] + ")$"
                cells.append(cell)
        rows.append(" & ".join(cells) + " " + chr(92) * 2)
    (OUTPUT / "table-rows.tex").write_text(chr(92) + "newcommand{" + chr(92) + "UnifiedAblationRows}{%\n" + "\n".join(rows) + "\n}\n", encoding="utf-8")
    (OUTPUT / "table.tex").write_text(TABLE, encoding="utf-8")
    comparisons = pd.DataFrame(comparisons); comparisons.to_csv(OUTPUT / "descriptive-comparisons.csv", index=False)
    tests = pd.DataFrame(tests); assert len(tests) == 54
    tests.to_csv(OUTPUT / "paired-tests.csv", index=False)
    favourable = comparisons.groupby(["Control", "Metric"]).FullMeanBetter.sum().unstack()
    symbols = tests.groupby(["Control", "Metric", "Symbol"]).size()
    plot_diagnostics(runs, sources, records)
    meta = {"Methods": METHODS, "Metrics": METRICS, "Runs": 1080, "Targets": 18, "ActualGeneration": 49,
            "Normalization": "Common per-scenario min/max/reference front over Full/no-transfer ONLY; separate from main-experiment and historical three-profile references",
            "MetricsRecomputedFromRaw": True, "References": pools,
            "Test": "Exact two-sided paired Wilcoxon signed-rank;zero differences omitted;midranks;raw alpha0.05;no multiple-comparison correction",
            "SymbolDirection": "uparrow=Full significantly better than the indicated control;downarrow=worse;equals=no significant difference",
            "ProtectedMainSHA256": protected_main, "ProtectedOriginalAnalysisSHA256": protected_old,
            "ProtectedPreviousAblationSHA256": protected_previous}
    (OUTPUT / "protocol.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    pd.DataFrame([{"Path": path, "SHA256": checksum} for path, checksum in sources.items()]).to_csv(OUTPUT / "input-manifest.csv", index=False)
    (OUTPUT / "analysis.md").write_text("\n".join(["# Unified Transfer-Ablation Analysis", "", meta["Normalization"], "", meta["Test"],
        "", "Arrows refer to Full relative to each control; no family-wise significance or equivalence is established.",
        "", favourable.to_markdown(), "", symbols.to_markdown(), "", tests.to_markdown(index=False)]), encoding="utf-8")
    plot(root=OUTPUT, methods=[("no-transfer", "No-transfer", "#2563EB", "s"),
                               ("Full", "Full", "#008040", "o")], expected_means=36, expected_runs=1080,
         stem="ablation-quality", protected_names=["table.tex", "table-rows.tex", "per-run.csv", "mean-std.csv", "paired-tests.csv", "protocol.json"])
    assert all(digest(Path(path)) == checksum for path, checksum in sources.items())
    assert all(digest(Path(path)) == checksum for path, checksum in protected_previous.items())
    assert all(digest(ROOT / name) == checksum for name, checksum in protected_main.items())
    print("Mean-favourable Full targets:"); print(favourable.to_string())
    print("Unadjusted Wilcoxon signs:"); print(symbols.to_string())
    print("PASS:1080raw runs recomputed with Full/no-transfer-only references;36mean rows;54raw Wilcoxon tests;108table/plotvalues;main and historical analyses unchanged.")


def validate_pdf():
    import pymupdf
    meta = json.loads((OUTPUT / "protocol.json").read_text())
    for entry in pd.read_csv(OUTPUT / "input-manifest.csv").itertuples(): assert digest(Path(entry.Path)) == entry.SHA256
    assert all(digest(Path(path)) == checksum for path, checksum in meta["ProtectedPreviousAblationSHA256"].items())
    assert all(digest(ROOT / name) == checksum for name, checksum in meta["ProtectedMainSHA256"].items())
    means = pd.read_csv(OUTPUT / "mean-std.csv", float_precision="round_trip")
    tests = pd.read_csv(OUTPUT / "paired-tests.csv", float_precision="round_trip")
    points = pd.read_csv(OUTPUT / "ablation-quality-data.csv", float_precision="round_trip")
    assert len(tests) == 54 and len(points) == 108 and set(tests.Correction) == {"none"}
    assert set(means.Method) == set(METHODS) and set(tests.Control) == {"no-transfer"} and set(points.Method) == {"No-transfer", "Full"}
    rows = [line for line in (OUTPUT / "table-rows.tex").read_text().splitlines() if " & " in line]
    assert len(rows) == 18
    checked = 0
    with pymupdf.open(PAPER / "mpslgp-method.pdf") as document:
        text = " ".join(" ".join(page.get_text().split()) for page in document)
        assert not re.search(r"fixed[\s-]*transfer", text, re.IGNORECASE)
        for line in rows:
            cells = line[:-2].strip().split(" & "); assert len(cells) == 8
            for metric_index, metric in enumerate(METRICS):
                target = means[(means.Group == cells[0]) & (means.Scenario == int(cells[1]))].set_index("Method").loc[METHODS]
                values = target[metric + "Mean"].to_numpy(); best = max(values) if metric == "HV" else min(values)
                for method_index, entry in enumerate(target.itertuples()):
                    actual = cells[2 + metric_index * len(METHODS) + method_index]
                    clean = re.sub(r"\$\((?:\\uparrow|\\downarrow|=)\)\$$", "", actual)
                    clean = re.sub(r"\\textbf\{(.*?)\}", r"\1", clean)
                    expected = f"{getattr(entry, metric+'Mean'):.3f}({getattr(entry, metric+'Std'):.3f})"
                    assert clean == expected and clean in text
                    assert actual.startswith(chr(92) + "textbf{") == (getattr(entry, metric + "Mean") == best)
                    if entry.Index in CONTROLS:
                        test = tests[(tests.Group == cells[0]) & (tests.Scenario == int(cells[1])) & (tests.Metric == metric) & (tests.Control == entry.Index)].iloc[0]
                        assert actual.endswith("$(" + test.Symbol + ")$")
                    checked += 1
        pages = []
        for index, page in enumerate(document):
            if "Transfer-ablation HV, IGD and GD results:" in " ".join(page.get_text().split()):
                pages.append(index + 1)
                page.get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5)).save(OUTPUT / f"table-page-{index+1:02d}.png")
        assert pages and checked == 108
    aux = (PAPER / "mpslgp-method.aux").read_text()
    assert "fig:latest-ablation-effects" not in aux
    report = {"AblationCellsVerified": checked, "AblationPlotPoints": 108, "RawWilcoxonComparisons": 54,
              "OnlyFullAndNoTransfer": True, "PreviousAblationPreserved": True,
              "TablePages": pages, "OriginalEffectMapRemoved": True, "MainResultsUnchanged": True,
              "SourceFilesVerified": len(pd.read_csv(OUTPUT / "input-manifest.csv")), "PDFSHA256": digest(PAPER / "mpslgp-method.pdf")}
    (OUTPUT / "validation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2)); print("PASS:108ablation tablecells/54marks,108plotpoints,onlyFull/no-transfer,main/sourceinputsunchanged.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(); parser.add_argument("--validate-pdf", action="store_true")
    options = parser.parse_args()
    validate_pdf() if options.validate_pdf else build()