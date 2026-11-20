import hashlib
import html
import json
from pathlib import Path

import pandas as pd
import pymupdf


ROOT = Path(__file__).resolve().parent
PAPER = ROOT.parent.parent
GREEN = 0x008040
FIGURES = ["main-full-vs-pslgp", "ablation-joint-quality", "ablation-effect-map",
           "training-transfer-probabilities", "validity-and-response", "latest-preference-response",
           "latest-empirical-attainment", "runtime-and-complexity"]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def spans(page):
    return [span for block in page.get_text("dict")["blocks"] if "lines" in block
            for line in block["lines"] for span in line["spans"]]


def main():
    inputs = pd.read_csv(ROOT / "input-manifest.csv")
    assert (inputs.groupby("Artifact").SHA256.nunique() == 1).all()
    checked = {}
    for row in inputs.drop_duplicates("Artifact").itertuples():
        path = Path(row.Artifact)
        assert digest(path) == row.SHA256, path
        checked[str(path)] = row.SHA256
    transfer = json.loads((ROOT / "training-transfer-provenance.json").read_text(encoding="utf-8"))
    for path, expected in transfer["sources"].items():
        assert digest(Path(path)) == expected, path
        checked[path] = expected
    plot_spans = 0
    for name in FIGURES:
        with pymupdf.open(ROOT / f"{name}.pdf") as figure:
            assert len(figure) == 1
            text_spans = spans(figure[0])
            assert text_spans
            for span in text_spans:
                if span["text"].strip():
                    assert span["color"] == GREEN, (name, span["text"], span["color"])
                    assert pymupdf.Rect(span["bbox"]) in figure[0].rect, (name, span["bbox"])
            plot_spans += len(text_spans)
    source = (PAPER / "mpslgp-method.tex").read_text(encoding="utf-8")
    assert "results/latest-20261103/results-section.tex" in source
    assert "results/latest-20261103/comparison-design.tex" in source
    assert "results/full-all-groups" not in source
    assert "results/joint-r4-h3" not in source
    with pymupdf.open(PAPER / "mpslgp-method.pdf") as manuscript:
        text = "\n".join(page.get_text() for page in manuscript)
        for word in ["324,000", "192", "171", "Holm", "0.078797", "PSLGP-I"]:
            assert word in text, word
        required = ["The results below replace", "None of the 108 exact", "The latest completed study covers"]
        result_pages = []
        for index, page in enumerate(manuscript):
            for block in page.get_text("dict")["blocks"]:
                if "lines" not in block:
                    continue
                block_spans = [span for line in block["lines"] for span in line["spans"]]
                content = " ".join(" ".join(span["text"] for span in block_spans).split())
                for phrase in required[:]:
                    if phrase in content:
                        assert all(span["color"] == GREEN for span in block_spans if span["text"].strip()), phrase
                        required.remove(phrase)
            if any(span["color"] == GREEN for span in spans(page)):
                page.get_pixmap(matrix=pymupdf.Matrix(1.3, 1.3)).save(ROOT / f"paper-page-{index+1:02d}.png")
                result_pages.append(index + 1)
        assert not required, required
        manuscript_pages = len(manuscript)
    report = json.loads((ROOT / "analysis-summary.json").read_text(encoding="utf-8"))
    sections = [
        "<!doctype html><html lang='en'><head><meta charset='utf-8'><title>Latest MPSLGP results</title>",
        "<style>body{max-width:1180px;margin:32px auto;padding:0 24px;color:#008040;"
        "font:17px/1.6 Georgia,serif;background:white}h1,h2{line-height:1.25}"
        "img{display:block;width:100%;height:auto;margin:24px 0}table{border-collapse:collapse;width:100%}"
        "td,th{padding:8px;border-bottom:1px solid #d7e5db;text-align:left}pre{white-space:pre-wrap}"
        "a{color:inherit}</style></head><body><h1>Latest MPSLGP results</h1>",
        "<p>Verified preference-runtime-v2 snapshot, 3 November 2026. 720 training runs, "
        "1,620 task tests and 324,000 preference outcomes. Every seed is retained.</p>",
        "<h2>Main Comparison</h2><p>Full is compared with published PSLGP-I summaries. "
        "Their reference data differ, so this is context rather than a common-reference statistical ranking.</p>",
        "<img src='main-full-vs-pslgp.png' alt='Full and published PSLGP-I indicators'>",
        "<h2>Transfer Ablations</h2><p>Full has better mean HV/IGD/GD on 15/14/17 targets against "
        "no transfer and 7/7/9 against fixed transfer. No paired comparison survives Holm correction "
        "over 108 tests (minimum adjusted p = 0.078797).</p>",
        "<img src='ablation-joint-quality.png' alt='Three-method common-reference quality'>",
        "<img src='ablation-effect-map.png' alt='Relative mean differences, not significant wins'>",
        "<h2>Validity and Preference Response</h2><table><tr><th>Method</th><th>Invalid preferences</th>"
        "<th>Task runs with invalid preferences</th><th>Single-outcome heuristics</th></tr>",
    ]
    for row in report["validity"]:
        sections.append(f"<tr><td>{html.escape(row['Algorithm'])}</td>"
                        f"<td>{row['InvalidPreferences']} / 108000</td>"
                        f"<td>{row['RunsWithInvalid']} / 540</td>"
                        f"<td>{row['SingleOutcomeRuns']} / 540</td></tr>")
    sections.extend([
        "</table><p>Control quality metrics are conditional on valid preferences. Invalid outputs are "
        "reported separately, not silently discarded. Replication-level failures are not counted here.</p>",
        "<img src='validity-and-response.png' alt='Validity and responsiveness'>",
        "<img src='latest-preference-response.png' alt='Fixed-heuristic preference responses'>",
        "<h2>Training and Raw Attainment</h2><p>Training probabilities change modestly. Full and fixed "
        "transfer do not have matched realized mating frequencies. Timing reflects concurrent batch runs.</p>",
        "<img src='training-transfer-probabilities.png' alt='Training transfer probabilities'>",
        "<img src='latest-empirical-attainment.png' alt='Raw-objective empirical attainment'>",
        "<img src='runtime-and-complexity.png' alt='Training cost and rule complexity'>",
        "<p>Old results are preserved as archives but removed from the active manuscript. "
        "The current common-reference ablation scores must not be mixed with Full-only or published scores.</p>",
        "</body></html>",
    ])
    (ROOT / "analysis.html").write_text("\n".join(sections), encoding="utf-8")
    final = {"source_files_reverified": len(checked), "figure_green_text_spans": plot_spans,
             "manuscript_pages": manuscript_pages, "green_content_pages": result_pages,
             "old_result_references_in_active_manuscript": 0,
             "file_sha256": {str(path.relative_to(PAPER)): digest(path)
                             for path in ROOT.rglob("*") if path.is_file() and path.name != "validation.json"}}
    final["file_sha256"]["mpslgp-method.tex"] = digest(PAPER / "mpslgp-method.tex")
    final["file_sha256"]["mpslgp-method.pdf"] = digest(PAPER / "mpslgp-method.pdf")
    (ROOT / "validation.json").write_text(json.dumps(final, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in final.items() if key != "file_sha256"}, indent=2))
    print("PASS: source integrity, eight green-text figures, manuscript update and green result paragraphs.")


if __name__ == "__main__":
    main()