import hashlib
import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


ROOT = Path(__file__).resolve().parent
GREEN = "#008040"
GROUPS = ["R1", "R2", "R3", "R4", "R5", "H1", "H2", "H3"]
plt.rcParams.update({"font.family": "serif", "font.serif": ["Times New Roman", "DejaVu Serif"],
                     "font.size": 10, "text.color": GREEN, "axes.labelcolor": GREEN,
                     "xtick.color": GREEN, "ytick.color": GREEN, "axes.edgecolor": "#BDC9C2", "pdf.fonttype": 42})


def main():
    manifest = pd.read_csv(ROOT / "input-manifest.csv")
    params = manifest[(manifest.Algorithm == "full") & manifest.Artifact.str.endswith("effective.params")]
    params = params.drop_duplicates(["Group", "Seed", "Artifact"])
    assert len(params) == 240
    records = []
    sources = {}
    for entry in params.itertuples():
        path = Path(entry.Artifact)
        assert hashlib.sha256(path.read_bytes()).hexdigest() == entry.SHA256
        diagnostic = path.parent / f"job.{entry.Seed}.transferContribution.csv"
        sources[str(diagnostic)] = hashlib.sha256(diagnostic.read_bytes()).hexdigest()
        rows = pd.read_csv(diagnostic)
        assert set(rows.Gen.astype(int)) == set(range(15, 50))
        assert (rows.SurvivedCount <= rows.TransferCount).all()
        assert np.allclose(rows.TotalTransferProbabilityAfterUpdate + rows.NoTransferProbabilityAfterUpdate, 1)
        assert not rows.duplicated(["Gen", "ReceivingTask", "ContributingTask", "PreferenceRegion"]).any()
        receiver_probabilities = rows.drop_duplicates(["Gen", "ReceivingTask", "PreferenceRegion"])
        probability = receiver_probabilities.groupby("Gen").TotalTransferProbabilityAfterUpdate.mean()
        counts = rows.groupby("Gen")[["TransferCount", "SurvivedCount"]].sum()
        for generation in probability.index:
            records.append({"Group": entry.Group, "Seed": entry.Seed, "Generation": int(generation),
                            "MeanTaskRegionProbability": probability.loc[generation],
                            "GeneratedTransferChildren": int(counts.loc[generation, "TransferCount"]),
                            "ValidSurvivors": int(counts.loc[generation, "SurvivedCount"])})
    values = pd.DataFrame(records)
    assert len(values) == 240 * 35
    values.to_csv(ROOT / "training-transfer-per-run.csv", index=False)
    figure, axes = plt.subplots(2, 4, figsize=(12, 5.2), sharex=True, sharey=True)
    group_summary = []
    for group, axis in zip(GROUPS, axes.flat):
        frame = values[values.Group == group]
        summary = frame.groupby("Generation").MeanTaskRegionProbability.agg(["mean", "std"])
        task_count = 3 if group in ("R4", "H3") else 2
        fixed_rate = 0.8 / (task_count - 1)
        axis.plot(summary.index, summary["mean"], color=GREEN, linewidth=1.8, label="Full: task-region mean")
        axis.fill_between(summary.index, summary["mean"] - summary["std"], summary["mean"] + summary["std"],
                          color=GREEN, alpha=0.14)
        axis.axhline(fixed_rate, color="#F97316", linestyle="--", linewidth=1.2, label="Fixed mating probability")
        axis.axhline(0, color="#2563EB", linestyle=":", linewidth=1.2, label="No transfer")
        axis.set_title(group)
        axis.set_ylim(-0.02, 0.88)
        axis.set_xlabel("Generation")
        if group in ("R1", "R5"):
            axis.set_ylabel("Cross-task mating probability")
        axis.spines[["top", "right"]].set_visible(False)
        axis.grid(color="#E4EBE6", linewidth=0.6)
        final = frame[frame.Generation == 49]
        group_summary.append({"Group": group, "Runs": 30, "FixedProbability": fixed_rate,
                              "InitialFullProbability": float(summary.loc[15, "mean"]),
                              "FinalFullProbability": float(final.MeanTaskRegionProbability.mean()),
                              "FinalFullProbabilityStd": float(final.MeanTaskRegionProbability.std()),
                              "GeneratedChildren": int(frame.GeneratedTransferChildren.sum()),
                              "ValidSurvivors": int(frame.ValidSurvivors.sum())})
    figure.legend(*axes.flat[0].get_legend_handles_labels(), loc="lower center", ncol=3, frameon=False,
                  bbox_to_anchor=(0.52, 0.012))
    figure.tight_layout(rect=(0, 0.09, 1, 1))
    figure.savefig(ROOT / "training-transfer-probabilities.pdf", bbox_inches="tight", pad_inches=0.09)
    figure.savefig(ROOT / "training-transfer-probabilities.png", bbox_inches="tight", pad_inches=0.09, dpi=210)
    plt.close(figure)
    pd.DataFrame(group_summary).to_csv(ROOT / "training-transfer-summary.csv", index=False)
    (ROOT / "training-transfer-provenance.json").write_text(json.dumps({
        "sources": sources,
        "definition": "Unweighted mean over receiving task and preference-region entries; not the empirical mating frequency",
        "dispersion": "Sample standard deviation of 30 run-level means",
        "group_summary": group_summary,
    }, indent=2), encoding="utf-8")
    print(pd.DataFrame(group_summary).to_string(index=False))
    print("Validated 240 latest Full training diagnostics; raw training data unchanged.")


if __name__ == "__main__":
    main()