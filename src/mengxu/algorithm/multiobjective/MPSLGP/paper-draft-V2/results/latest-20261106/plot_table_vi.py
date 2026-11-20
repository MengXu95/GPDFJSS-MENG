import hashlib
import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parent
METHODS = [("NSGPII", "NSGPII", "#2563EB", "s"),
           ("PSLGP", "PSLGP", "#F97316", "^"),
           ("Full", "MPSLGP", "#64748B", "o")]
GROUPS = ["R1", "R2", "R3", "R4", "R5", "H1", "H2", "H3"]
TEXT = "#000000"
PROTECTED = ["main-table.tex", "table-rows.tex", "mean-std.csv", "per-run.csv", "paired-tests.csv", "protocol.json"]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def plot_comparison(root=ROOT, methods=METHODS, expected_means=38, expected_runs=1140,
                    stem="table-vi-quality", protected_names=PROTECTED):
    protected = {name: digest(root / name) for name in protected_names}
    means = pd.read_csv(root / "mean-std.csv", float_precision="round_trip")
    runs = pd.read_csv(root / "per-run.csv", float_precision="round_trip")
    assert len(means) == expected_means and len(runs) == expected_runs
    assert set(runs.Method) == {method for method, _, _, _ in methods} and set(runs.ActualGeneration) == {49}
    targets = means[means.Method == "Full"].assign(Order=lambda frame: frame.Group.map(GROUPS.index)).sort_values(["Order", "Task"])
    assert len(targets) == 18
    plt.rcParams.update({
        "font.family": "serif", "font.serif": ["Times New Roman", "DejaVu Serif"],
        "font.size": 13, "axes.labelsize": 13, "axes.titlesize": 14,
        "text.color": TEXT, "axes.labelcolor": TEXT, "axes.edgecolor": TEXT,
        "xtick.color": TEXT, "ytick.color": TEXT, "pdf.fonttype": 42,
        "ps.fonttype": 42, "savefig.facecolor": "white",
    })
    figure, axes = plt.subplots(1, 3, figsize=(13, 7.3), sharey=True)
    positions = np.arange(18)
    labels = [f"{entry.Group} / S{entry.Scenario}" for entry in targets.itertuples()]
    points = []
    limits = {}
    for metric, axis in zip(("HV", "IGD", "GD"), axes):
        minimum, maximum = np.inf, -np.inf
        offsets = (-.23, 0, .23) if len(methods) == 3 else np.linspace(-.14, .14, len(methods))
        for offset, (method, label, colour, marker) in zip(offsets, methods):
            averages, deviations = [], []
            for entry in targets.itertuples():
                selected = means[(means.Method == method) & (means.Scenario == entry.Scenario)]
                samples = runs[(runs.Method == method) & (runs.Scenario == entry.Scenario)]
                if method == "Full" or set(selected.Group) != {"Independent"}:
                    selected = selected[(selected.Group == entry.Group) & (selected.Task == entry.Task)]
                    samples = samples[(samples.Group == entry.Group) & (samples.Task == entry.Task)]
                assert len(selected) == 1 and len(samples) == 30 and set(samples.Seed) == set(range(30))
                row = selected.iloc[0]
                mean, deviation = float(row[metric + "Mean"]), float(row[metric + "Std"])
                assert np.isclose(samples[metric].mean(), mean, rtol=0, atol=1e-12)
                assert np.isclose(samples[metric].std(ddof=1), deviation, rtol=0, atol=1e-12)
                averages.append(mean); deviations.append(deviation)
                points.append({"Group": entry.Group, "Task": entry.Task, "Scenario": entry.Scenario,
                               "Metric": metric, "Method": label, "Mean": mean, "SampleSD": deviation, "Runs": 30})
            axis.errorbar(averages, positions + offset, xerr=deviations, fmt=marker, markersize=4.0,
                          elinewidth=.85, capsize=1.8, color=colour, label=label, zorder=3)
            minimum = min(minimum, float(np.min(np.asarray(averages) - deviations)))
            maximum = max(maximum, float(np.max(np.asarray(averages) + deviations)))
        padding = max(.01, (maximum - minimum) * .06)
        left = min(0, minimum - padding) if metric != "HV" else minimum - padding
        right = maximum + padding
        axis.set_xlim(left, right)
        limits[metric] = [left, right]
        axis.set_title(metric + (" (higher is better)" if metric == "HV" else " (lower is better)"))
        axis.set_xlabel("Mean and standard deviation")
        axis.set_yticks(positions, labels)
        for spine in axis.spines.values():
            spine.set_visible(True)
            spine.set_color(TEXT)
            spine.set_linewidth(.7)
        axis.grid(axis="x", color="#E3E3E3", linewidth=.6)
        axis.set_axisbelow(True)
        for position in range(1, len(targets)):
            if targets.iloc[position].Group != targets.iloc[position - 1].Group:
                axis.axhline(position - .5, color="#E3E3E3", linewidth=.55, zorder=0)
    axes[0].invert_yaxis()
    figure.legend(*axes[0].get_legend_handles_labels(), loc="lower center", ncol=len(methods), frameon=False, bbox_to_anchor=(.53, .02))
    figure.tight_layout(rect=(0, .075, 1, 1))
    for suffix, options in [("pdf", {}), ("png", {"dpi": 210})]:
        figure.savefig(root / f"{stem}.{suffix}", bbox_inches="tight", pad_inches=.09, **options)
    plt.close(figure)
    points = pd.DataFrame(points)
    expected_points = len(targets) * 3 * len(methods)
    assert len(points) == expected_points
    for entry in points.itertuples():
        left, right = limits[entry.Metric]
        assert left <= entry.Mean - entry.SampleSD and entry.Mean + entry.SampleSD <= right
    data_name = "table-vi-figure-data.csv" if stem == "table-vi-quality" else stem + "-data.csv"
    points.to_csv(root / data_name, index=False)
    assert all(digest(root / name) == checksum for name, checksum in protected.items())
    report = {"PlotPoints": expected_points, "Targets": 18, "Metrics": ["HV", "IGD", "GD"],
              "LegendOrder": [label for _, label, _, _ in methods], "ErrorBars": "standard deviation over all30 training runs; n-1 denominator",
              "SeriesColours": {label: colour for _, label, colour, _ in methods}, "AxesAndTextColour": TEXT,
              "AxisLimits": limits, "TableVIProtectedSHA256": protected,
              "PDFSHA256": digest(root / f"{stem}.pdf"), "PNGSHA256": digest(root / f"{stem}.png")}
    validation_name = "table-vi-figure-validation.json" if stem == "table-vi-quality" else stem + "-validation.json"
    (root / validation_name).write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"PASS:{expected_points} plotted means/sampleSD match comparison table across18 targets and3metrics;no error-bar clipping;table and statistical results unchanged.")


if __name__ == "__main__":
    plot_comparison()