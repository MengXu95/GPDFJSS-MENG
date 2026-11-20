import hashlib
import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.colors import LinearSegmentedColormap, TwoSlopeNorm
from matplotlib.lines import Line2D
import numpy as np
import pandas as pd
from scipy.stats import rankdata


ROOT = Path(__file__).resolve().parent
PAPER = ROOT.parent.parent
GREEN = "#008040"
BLUE = "#2563EB"
ORANGE = "#F97316"
GRAY = "#64748B"
METHODS = ["full", "no-transfer", "fixed-transfer"]
LABELS = {"full": "Full MPSLGP", "no-transfer": "No transfer", "fixed-transfer": "Fixed transfer"}
COLORS = {"full": GREEN, "no-transfer": BLUE, "fixed-transfer": ORANGE}
GROUPS = ["R1", "R2", "R3", "R4", "R5", "H1", "H2", "H3"]
METRICS = ["HV", "IGD", "GD"]
plt.rcParams.update({
    "font.family": "serif", "font.serif": ["Times New Roman", "DejaVu Serif"],
    "font.size": 10, "axes.labelsize": 11, "axes.titlesize": 12,
    "text.color": GREEN, "axes.labelcolor": GREEN, "axes.edgecolor": "#BDC9C2",
    "xtick.color": GREEN, "ytick.color": GREEN, "pdf.fonttype": 42,
    "ps.fonttype": 42, "savefig.facecolor": "white",
})


def file_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_properties(path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    return values


def exact_signed_rank(gains):
    gains = gains[gains != 0]
    if len(gains) == 0:
        return 1.0
    ranks = np.rint(rankdata(np.abs(gains)) * 2).astype(int)
    counts = np.zeros(int(ranks.sum()) + 1, dtype=np.int64)
    counts[0] = 1
    for rank in ranks:
        counts[rank:] += counts[:-rank].copy()
    positive = int(ranks[gains > 0].sum())
    tail = min(positive, int(ranks.sum()) - positive)
    return min(1.0, 2 * int(counts[:tail + 1].sum()) / 2 ** len(gains))


def holm(pvalues):
    order = np.argsort(pvalues, kind="stable")
    adjusted = np.minimum(1, np.maximum.accumulate(np.asarray(pvalues)[order] * np.arange(len(order), 0, -1)))
    result = np.empty_like(adjusted)
    result[order] = adjusted
    return result


def neat(axis):
    axis.spines[["top", "right"]].set_visible(False)
    axis.grid(axis="x", color="#E4EBE6", linewidth=0.6)
    axis.set_axisbelow(True)


def save(figure, name):
    figure.savefig(ROOT / f"{name}.pdf", bbox_inches="tight", pad_inches=0.09)
    figure.savefig(ROOT / f"{name}.png", bbox_inches="tight", pad_inches=0.09, dpi=210)
    plt.close(figure)


def nondominated(points):
    points = np.unique(points, axis=0)
    ordered = points[np.lexsort((points[:, 1], points[:, 0]))]
    best = np.inf
    frontier = []
    for point in ordered:
        if point[1] < best:
            frontier.append(point)
            best = point[1]
    return np.asarray(frontier)


def main():
    audit = read_properties(ROOT / "completed.properties")
    assert audit["status"] == "COMPLETED"
    for key, value in audit.items():
        if key.startswith("sha256."):
            assert file_hash(ROOT / key[len("sha256."):]) == value
    protocol = read_properties(ROOT / "protocol.properties")
    assert protocol["preference.runtime.version"] == "2"
    samples = pd.read_csv(ROOT / "per-run.csv")
    means = pd.read_csv(ROOT / "mean-std.csv")
    tests = pd.read_csv(ROOT / "paired-tests.csv")
    gains = pd.read_csv(ROOT / "paired-gains.csv")
    published = pd.read_csv(PAPER / "published-pslgp-context.csv")
    full_means = pd.read_csv(ROOT / "full-summary" / "mean-std.csv")
    full_validity = pd.read_csv(ROOT / "full-summary" / "validity-summary.csv")
    manifest = pd.read_csv(ROOT / "input-manifest.csv")
    assert len(samples) == 1620 and len(means) == 54 and len(tests) == 108
    assert len(full_means) == 18 and len(published) == 30
    assert not samples.duplicated(["Algorithm", "Group", "Task", "Seed"]).any()
    assert np.isfinite(samples[["HVJoint", "IGDJoint", "GDJoint"]]).all().all()
    for _, subset in samples.groupby(["Algorithm", "Group", "Task"]):
        assert set(subset.Seed) == set(range(30))
        assert (subset.ValidPreferences + subset.InvalidPreferences == 200).all()
    verified_p = []
    for row in tests.itertuples():
        subset = gains[(gains.Group == row.Group) & (gains.Task == row.Task)
                       & (gains.Comparator == row.Comparator) & (gains.Metric == row.Metric)]
        assert len(subset) == 30
        probability = exact_signed_rank(subset.Gain.to_numpy())
        assert abs(probability - row.PValue) < 1e-12
        verified_p.append(probability)
    assert np.allclose(holm(verified_p), tests.HolmPValue, atol=1e-12)
    ordered = full_means.assign(GroupOrder=full_means.Group.map(GROUPS.index)).sort_values(["GroupOrder", "Task"])
    targets = list(zip(ordered.Group, ordered.Task, ordered.Scenario))
    target_labels = [f"{group} / S{scenario}" for group, _, scenario in targets]
    ypositions = np.arange(18)

    comparison_rows = []
    figure, axes = plt.subplots(1, 3, figsize=(12, 7.1), sharey=True)
    for metric, axis in zip(METRICS, axes):
        local = ordered[f"{metric}LocalMean"].to_numpy()
        deviations = ordered[f"{metric}LocalStd"].to_numpy()
        background = published[published.Metric == metric].set_index("Scenario")
        baseline = np.array([background.loc[scenario, "PSLGP-I-Mean"] for _, _, scenario in targets])
        baseline_sd = np.array([background.loc[scenario, "PSLGP-I-Std"] for _, _, scenario in targets])
        axis.errorbar(local, ypositions - 0.13, xerr=deviations, fmt="o", color=GREEN,
                      markersize=4.5, elinewidth=1.05, capsize=2, label="Full (latest; local references)")
        axis.errorbar(baseline, ypositions + 0.13, xerr=baseline_sd, fmt="^", color=GRAY,
                      markersize=4.0, elinewidth=0.8, capsize=2, label="PSLGP-I (published)")
        axis.set_title(metric + (" (higher is better)" if metric == "HV" else " (lower is better)"))
        axis.set_xlabel("Mean and sample standard deviation")
        axis.set_xlim(left=0)
        if metric == "HV":
            axis.set_xlim(0.45, 1.02)
        axis.set_yticks(ypositions, target_labels)
        axis.invert_yaxis()
        neat(axis)
        for index, (group, task, scenario) in enumerate(targets):
            comparison_rows.append({"Group": group, "Task": task, "Scenario": scenario, "Metric": metric,
                                    "FullMean": local[index], "FullStd": deviations[index],
                                    "PublishedPSLGPMean": baseline[index], "PublishedPSLGPStd": baseline_sd[index],
                                    "Protocol": "Different reference data; descriptive context, not a common-reference test"})
    figure.legend(*axes[0].get_legend_handles_labels(), loc="lower center", ncol=2, frameon=False,
                  bbox_to_anchor=(0.53, 0.02))
    figure.text(0.53, 0.006, "Different normalization/reference data: numerical comparisons are contextual, not statistical wins.",
                ha="center", fontsize=9)
    figure.tight_layout(rect=(0, 0.09, 1, 1))
    save(figure, "main-full-vs-pslgp")
    pd.DataFrame(comparison_rows).to_csv(ROOT / "main-comparison-data.csv", index=False)

    figure, axes = plt.subplots(1, 3, figsize=(12, 7.1), sharey=True)
    for metric, axis in zip(METRICS, axes):
        for offset, method in zip((-0.22, 0, 0.22), METHODS):
            rows = [means[(means.Algorithm == method) & (means.Group == group) & (means.Task == task)].iloc[0]
                    for group, task, _ in targets]
            axis.errorbar([row[f"{metric}JointMean"] for row in rows], ypositions + offset,
                          xerr=[row[f"{metric}JointStd"] for row in rows], fmt="o", markersize=3.6,
                          elinewidth=0.75, capsize=1.5, color=COLORS[method], label=LABELS[method])
        axis.set_title(metric + " (joint references)")
        axis.set_xlabel("Mean and sample standard deviation")
        axis.set_yticks(ypositions, target_labels)
        if metric == "HV":
            axis.set_xlim(0.70, 1.015)
        else:
            axis.set_xlim(left=0)
        axis.invert_yaxis()
        neat(axis)
    figure.legend(*axes[0].get_legend_handles_labels(), loc="lower center", ncol=3, frameon=False,
                  bbox_to_anchor=(0.53, 0.025))
    figure.text(0.53, 0.008, "All 30 seeds retained; control metrics are conditional on valid preferences. Holm family: 108 tests.",
                ha="center", fontsize=9)
    figure.tight_layout(rect=(0, 0.09, 1, 1))
    save(figure, "ablation-joint-quality")

    figure, axes = plt.subplots(1, 2, figsize=(10.6, 6.9), sharey=True)
    effect_rows = []
    for control, axis in zip(METHODS[1:], axes):
        effects = np.empty((18, 3))
        for target_index, (group, task, scenario) in enumerate(targets):
            for metric_index, metric in enumerate(METRICS):
                row = tests[(tests.Group == group) & (tests.Task == task) & (tests.Comparator == control)
                            & (tests.Metric == metric + "Joint")].iloc[0]
                effect = row.MeanGain / row.ControlMean * 100
                effects[target_index, metric_index] = effect
                effect_rows.append({"Group": group, "Task": task, "Scenario": scenario, "Control": control,
                                    "Metric": metric, "RelativeMeanGainPercent": effect,
                                    "PValue": row.PValue, "HolmPValue": row.HolmPValue})
        palette = LinearSegmentedColormap.from_list("readable_gain", ["#F3D7C3", "#F8FAF9", "#A4DAD0"])
        axis.imshow(effects, cmap=palette, norm=TwoSlopeNorm(vmin=-85, vcenter=0, vmax=85), aspect="auto")
        for row, column in np.ndindex(effects.shape):
            formatted_gain = "0.0%" if abs(effects[row, column]) < 0.05 else f"{effects[row, column]:+.1f}%"
            axis.text(column, row, formatted_gain, ha="center", va="center", fontsize=9.8, color=GREEN, weight="bold")
        axis.set_xticks(range(3), METRICS)
        axis.set_yticks(range(18), target_labels)
        axis.set_title("Full vs " + LABELS[control].lower())
        axis.tick_params(length=0)
        for spine in axis.spines.values():
            spine.set_visible(False)
    figure.text(0.53, 0.015, "Positive = Full better. Relative changes in means, not significance: no comparison survives Holm correction.",
                ha="center", fontsize=9)
    figure.tight_layout(rect=(0, 0.045, 1, 1))
    save(figure, "ablation-effect-map")
    pd.DataFrame(effect_rows).to_csv(ROOT / "effect-map-data.csv", index=False)

    validity_rows = []
    for method in METHODS:
        rows = samples[samples.Algorithm == method]
        validity_rows.append({"Algorithm": method, "TrainingRuns": 240, "TaskRuns": len(rows),
                              "PreferenceRows": len(rows) * 200, "InvalidPreferences": int(rows.InvalidPreferences.sum()),
                              "RunsWithInvalid": int((rows.InvalidPreferences > 0).sum()),
                              "SingleOutcomeRuns": int((rows.UniqueOutcomes == 1).sum()),
                              "MeanDistinctOutcomes": float(rows.UniqueOutcomes.mean()),
                              "MeanNondominatedOutcomes": float(rows.NondominatedOutcomes.mean())})
    validity = pd.DataFrame(validity_rows)
    validity.to_csv(ROOT / "method-validity-response.csv", index=False)
    figure, axes = plt.subplots(1, 3, figsize=(12, 3.2))
    for axis, field, title, denominator in zip(axes,
             ["InvalidPreferences", "SingleOutcomeRuns", "MeanNondominatedOutcomes"],
             ["Invalid preference outcomes", "Single-outcome heuristics", "Within-heuristic front size"],
             [108000, 540, None]):
        values = validity[field].to_numpy()
        bars = axis.bar(range(3), values, color=[COLORS[method] for method in METHODS], width=0.58)
        for bar, value in zip(bars, values):
            annotation = f"{int(value)}\n({value / denominator * 100:.2f}%)" if denominator else f"{value:.2f}"
            axis.annotate(annotation, (bar.get_x() + bar.get_width() / 2, bar.get_height()), xytext=(0, 5),
                          textcoords="offset points", ha="center", va="bottom", fontsize=10, color=GREEN)
        axis.set_xticks(range(3), ["Full", "No transfer", "Fixed transfer"])
        axis.set_title(title)
        axis.set_ylim(0, max(values) * 1.42)
        axis.set_ylabel("Count" if denominator else "Mean nondominated outcomes")
        neat(axis)
        axis.grid(False)
    figure.tight_layout()
    save(figure, "validity-and-response")

    training = samples[["Algorithm", "Group", "Seed", "TrainingSeconds"]].drop_duplicates()
    assert len(training) == 720
    assert not training.duplicated(["Algorithm", "Group", "Seed"]).any()
    runtime = training.groupby(["Algorithm", "Group"]).TrainingSeconds.agg(["mean", "std"]).reset_index()
    runtime[["mean", "std"]] /= 60
    runtime.to_csv(ROOT / "group-training-minutes.csv", index=False)
    samples.assign(TotalNodes=samples.SeqNodes + samples.RoutingNodes).groupby("Algorithm").TotalNodes.agg(["mean", "std"]).to_csv(ROOT / "rule-complexity.csv")
    figure, axes = plt.subplots(1, 2, figsize=(12, 3.8))
    for method in METHODS:
        frame = runtime[runtime.Algorithm == method].set_index("Group").loc[GROUPS]
        axes[0].errorbar(range(8), frame["mean"], yerr=frame["std"], fmt="o-", color=COLORS[method],
                         capsize=2, markersize=4, linewidth=1.2, label=LABELS[method])
        node_groups = samples[samples.Algorithm == method].assign(Nodes=lambda value: value.SeqNodes + value.RoutingNodes)
        node_summary = node_groups.groupby("Group").Nodes.agg(["mean", "std"]).loc[GROUPS]
        axes[1].plot(range(8), node_summary["mean"], "o-", color=COLORS[method], markersize=4,
                     linewidth=1.2, label=LABELS[method])
    axes[0].set_title("Whole-group training time")
    axes[0].set_ylabel("Minutes: mean and sample standard deviation")
    axes[1].set_title("Tested dual-tree heuristic size")
    axes[1].set_ylabel("Mean total GP nodes")
    for axis in axes:
        axis.set_xticks(range(8), GROUPS)
        neat(axis)
    figure.legend(*axes[0].get_legend_handles_labels(), loc="lower center", ncol=3, frameon=False,
                  bbox_to_anchor=(0.53, -0.015))
    figure.tight_layout(rect=(0, 0.08, 1, 1))
    save(figure, "runtime-and-complexity")

    point_manifest = manifest[manifest.Artifact.str.endswith("preference-results.csv")].copy()
    assert len(point_manifest) == 1620
    raw = {}
    for row in point_manifest.itertuples():
        path = Path(row.Artifact)
        assert file_hash(path) == row.SHA256
        frame = pd.read_csv(path)
        assert len(frame) == 200 and np.array_equal(frame.PreferenceIndex, np.arange(200))
        assert np.allclose(frame.Weight0, np.arange(200) / 199)
        points = frame[["TestFitness0", "TestFitness1"]].to_numpy()
        valid = (points >= 0).all(axis=1) & np.isfinite(points).all(axis=1)
        sample = samples[(samples.Algorithm == row.Algorithm) & (samples.Group == row.Group)
                         & (samples.Task == row.Task) & (samples.Seed == row.Seed)].iloc[0]
        assert int((~valid).sum()) == sample.InvalidPreferences
        raw[(row.Algorithm, row.Group, row.Task, row.Seed)] = points[valid]
    rank_frames = []
    for group in ("R4", "H3"):
        group_rows = samples[(samples.Algorithm == "full") & (samples.Group == group)].copy()
        group_rows["Rank"] = group_rows.groupby("Task").IGDJoint.rank(method="average")
        rank_frames.append(group_rows.groupby("Seed").Rank.mean())
    joint_rank = pd.concat(rank_frames, axis=1).mean(axis=1).sort_values(kind="stable")
    selected_seed = int(joint_rank.index[14])
    pd.DataFrame({"Seed": joint_rank.index, "MeanTaskRank": joint_rank.to_numpy()}).to_csv(ROOT / "representative-seed-ranking.csv", index=False)
    figure, axes = plt.subplots(2, 3, figsize=(12, 6.1))
    representative_rows = []
    full_protocol = read_properties(ROOT / "full-summary" / "protocol.properties")
    for group_index, group in enumerate(("R4", "H3")):
        group_targets = [(target, scenario) for target_group, target, scenario in targets if target_group == group]
        for task, scenario in group_targets:
            axis = axes[group_index, task]
            points = raw[("full", group, task, selected_seed)]
            reference_min = np.array(json.loads(full_protocol[f"scenario.{scenario}.min"].replace("\\ ", " ")))
            reference_max = np.array(json.loads(full_protocol[f"scenario.{scenario}.max"].replace("\\ ", " ")))
            normalized = (points - reference_min) / (reference_max - reference_min)
            axis.plot(np.arange(200) / 199, normalized[:, 0], color=BLUE, linewidth=1.6, label="Objective 1")
            axis.plot(np.arange(200) / 199, normalized[:, 1], color=ORANGE, linewidth=1.6, label="Objective 2")
            axis.set_title(f"{group} / S{scenario}, seed {selected_seed}")
            axis.set_xlabel("Input preference weight w1")
            axis.set_ylabel("Normalized mean objective")
            neat(axis)
            for index, point in enumerate(points):
                representative_rows.append({"Group": group, "Task": task, "Scenario": scenario,
                                            "Seed": selected_seed, "Weight0": index / 199,
                                            "Objective0": point[0], "Objective1": point[1]})
    figure.legend(*axes[0, 0].get_legend_handles_labels(), loc="lower center", ncol=2, frameon=False,
                  bbox_to_anchor=(0.53, 0.0))
    figure.tight_layout(rect=(0, 0.055, 1, 1))
    save(figure, "latest-preference-response")
    pd.DataFrame(representative_rows).to_csv(ROOT / "representative-preference-data.csv", index=False)

    figure, axes = plt.subplots(1, 2, figsize=(12, 4.2))
    eaf_rows = []
    for scenario, axis in zip((1, 5), axes):
        fronts = {}
        for group in ("R4", "H3"):
            task = next(task for target_group, task, target_scenario in targets
                        if target_group == group and target_scenario == scenario)
            fronts[group] = [nondominated(raw[("full", group, task, seed)]) for seed in range(30)]
        thresholds = np.unique(np.concatenate([front[:, 0] for group_fronts in fronts.values() for front in group_fronts]))
        for group, ink in (("R4", BLUE), ("H3", ORANGE)):
            attained = np.array([[np.min(front[front[:, 0] <= threshold, 1], initial=np.inf)
                                 for threshold in thresholds] for front in fronts[group]])
            sorted_values = np.sort(attained, axis=0)
            levels = [sorted_values[int(np.ceil(30 * probability)) - 1] for probability in (0.25, 0.5, 0.75)]
            for probability, values in zip((0.25, 0.5, 0.75), levels):
                for threshold, value in zip(thresholds, values):
                    if np.isfinite(value):
                        eaf_rows.append({"Group": group, "Scenario": scenario, "Probability": probability,
                                         "Objective0Threshold": threshold, "Objective1Boundary": value})
            axis.step(thresholds, np.where(np.isfinite(levels[1]), levels[1], np.nan), where="post",
                      color=ink, linewidth=1.5, label=group)
            valid_band = np.isfinite(levels[0]) & np.isfinite(levels[2])
            axis.fill_between(thresholds, np.where(valid_band, levels[0], np.nan),
                              np.where(valid_band, levels[2], np.nan), step="post", color=ink, alpha=0.15)
        axis.set_title(f"Scenario {scenario}: co-trained Full heuristics")
        axis.set_xlabel("Maximum flow time (raw)")
        axis.set_ylabel("Maximum weighted tardiness (raw)")
        axis.legend(frameon=False)
        neat(axis)
    figure.text(0.53, 0.01, "Median empirical attainment; shading: 25%-75%. One fixed heuristic front per task and seed; no outlier removal.",
                ha="center", fontsize=9)
    figure.tight_layout(rect=(0, 0.045, 1, 1))
    save(figure, "latest-empirical-attainment")
    pd.DataFrame(eaf_rows).to_csv(ROOT / "empirical-attainment-data.csv", index=False)

    report = {"snapshot": "latest-20261103", "runtime_version": 2, "training_runs": 720,
              "task_tests": 1620, "tested_preferences": 324000, "paired_tests": 108,
              "minimum_raw_p": float(tests.PValue.min()), "minimum_holm_p": float(tests.HolmPValue.min()),
              "significant_tests": int((tests.HolmPValue <= 0.05).sum()), "representative_seed": selected_seed,
              "validity": validity_rows, "main_context_counts": {}, "ablation_mean_counts": {},
              "runtime_minutes": runtime.to_dict(orient="records")}
    comparison = pd.DataFrame(comparison_rows)
    for metric in METRICS:
        rows = comparison[comparison.Metric == metric]
        better = rows.FullMean > rows.PublishedPSLGPMean if metric == "HV" else rows.FullMean < rows.PublishedPSLGPMean
        report["main_context_counts"][metric] = int(better.sum())
    for control in METHODS[1:]:
        report["ablation_mean_counts"][control] = {}
        for metric in METRICS:
            rows = tests[(tests.Comparator == control) & (tests.Metric == metric + "Joint")]
            report["ablation_mean_counts"][control][metric] = int((rows.MeanGain > 0).sum())
    (ROOT / "analysis-summary.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    all_full = samples[samples.Algorithm == "full"]
    response_summary = all_full.groupby(["Group", "Task", "Scenario"]).agg(
        DistinctMean=("UniqueOutcomes", "mean"), NondominatedMean=("NondominatedOutcomes", "mean"),
        SingleOutcomes=("UniqueOutcomes", lambda values: int((values == 1).sum())))
    response_summary.to_csv(ROOT / "full-response-summary.csv")
    narrative = [
        "# Latest MPSLGP results (2026-11-03)",
        "\nAll methods use completed preference-runtime-v2 experiments. Raw experiments are read-only.",
        f"\nAudit: 720 training runs; 1,620 task tests; 324,000 preference outcomes; 108 paired tests.",
        "\n## Full versus published PSLGP-I",
        "\nMain figure uses newly recomputed Full-only descriptive references. Published PSLGP-I values retain their original references; no cross-paper significance or common-reference superiority is claimed.",
        f"\nNumerically favorable Full rows (HV/IGD/GD): {report['main_context_counts']}. These are not statistical wins.",
        "\n## Transfer ablations",
        f"\nFull has better mean HV/IGD/GD than no transfer on {report['ablation_mean_counts']['no-transfer']} targets, and versus fixed transfer on {report['ablation_mean_counts']['fixed-transfer']} targets.",
        f"\nNo comparison survives the 108-test Holm family: minimum unadjusted p={tests.PValue.min():.8f}; adjusted p={tests.HolmPValue.min():.8f}.",
        "\nQuality indicators are conditional on valid preferences for controls. Every seed is retained. Invalid outcomes are reported separately, not silently dropped.",
        "\n```csv\n" + validity.to_csv(index=False) + "```",
        f"\nRepresentative preference-response seed: {selected_seed}, selected by the lower median of equal-group mean task IGD ranks for R4/H3; no cherry-picked optimum.",
        "\nRuntime is whole-group wall time under concurrent jobs, counted once per method/group/seed; not a hardware-independent speedup.",
        "\nShared normalization changes absolute values: Full-only and three-method scores must not be mixed. Raw empirical attainment avoids these normalization bounds.",
    ]
    (ROOT / "analysis.md").write_text("\n".join(narrative), encoding="utf-8")
    sources = [ROOT / "per-run.csv", ROOT / "mean-std.csv", ROOT / "paired-tests.csv",
               ROOT / "paired-gains.csv", ROOT / "protocol.properties", ROOT / "input-manifest.csv",
               ROOT / "full-summary" / "mean-std.csv", PAPER / "published-pslgp-context.csv", Path(__file__)]
    provenance = {"source_sha256": {str(path): file_hash(path) for path in sources},
                  "generated_sha256": {path.name: file_hash(path) for path in sorted(ROOT.iterdir())
                                       if path.is_file() and path.suffix in (".pdf", ".png", ".json", ".md")}}
    (ROOT / "figure-provenance.json").write_text(json.dumps(provenance, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "runtime_minutes"}, indent=2))
    print("Verified Java metrics snapshot and independently reproduced all 108 exact signed-rank/Holm tests.")
    print("Generated seven green-text PDF/PNG figures and source-linked analysis tables.")


if __name__ == "__main__":
    main()