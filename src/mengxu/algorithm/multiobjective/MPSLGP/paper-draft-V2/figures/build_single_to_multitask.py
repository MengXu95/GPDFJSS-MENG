"""Build an editable single-task to multitask DFJSS problem illustration."""

import hashlib
import json
import runpy
from pathlib import Path
from zipfile import ZipFile

from lxml import etree
from pptx import Presentation
from pptx.enum.shapes import MSO_AUTO_SHAPE_TYPE
from pptx.util import Inches


ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "DFJSS_Single_to_Multitask.pptx"
SOURCE = ROOT.parent / "mpslgp-method.tex"
BLUE = "2563EB"
BLUE_LIGHT = "DBEAFE"
ORANGE = "F97316"
ORANGE_LIGHT = "FFEDD5"
GRAY = "64748B"
GRAY_LIGHT = "F3F4F6"
INK = "111827"
LAMBDA = "\u03bb"
SUB1 = "\u2081"
SUB2 = "\u2082"
SUBT = "\u209c"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build():
    source_hash = digest(SOURCE)
    existing = ROOT / "MPSLGP_Editable_Figures.pptx"
    existing_hash = digest(existing)
    helpers = runpy.run_path(str(ROOT / "build_mpslgp_figures.py"))
    label, form, line, circle = [helpers[name] for name in ["label", "form", "line", "circle"]]
    deck = Presentation()
    deck.slide_width = Inches(16)
    deck.slide_height = Inches(7.7)
    slide = deck.slides.add_slide(deck.slide_layouts[6])
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = helpers["rgb"]("FFFFFF")

    def text(name, content, left, top, width, height, size=18, colour=INK, bold=False, math=False):
        return label(slide, name, left, top, width, height, content, size, colour, bold,
                     font="Cambria Math" if math else helpers["FONT"])

    def box(name, content, left, top, width, height, colour=BLUE, pale=BLUE_LIGHT, size=18):
        shape = form(slide, name, left, top, width, height, pale, colour,
                     kind=MSO_AUTO_SHAPE_TYPE.ROUNDED_RECTANGLE, weight=1.2)
        text(name + " label", content, left + .06, top + .03, width - .12, height - .06, size, colour)
        return shape

    def arrow(name, start, end, colour=INK, dashed=False, reverse=False):
        return line(slide, name, start, end, colour, 1.5, True, dashed,
                    source=None, target=None)

    def mini_tree(name, left, top, colour):
        line(slide, name + " left branch", (left + .21, top + .12), (left + .07, top + .39), colour, 1.05, False)
        line(slide, name + " right branch", (left + .21, top + .12), (left + .35, top + .39), colour, 1.05, False)
        for suffix, horizontal, vertical in [("root", .21, .12), ("left", .07, .39), ("right", .35, .39)]:
            circle(slide, name + " " + suffix, (left + horizontal, top + vertical), .055, "FFFFFF", colour)

    def heuristic(name, symbol, left, top, width, height, colour, pale):
        form(slide, name + " frame", left, top, width, height, "FFFFFF", colour, weight=1.5)
        text(name + " function", symbol, left + .05, top + .05, width - .10, .36, 20, colour, True, math=True)
        baseline = top + .48
        mini_tree(name + " sequencing tree", left + width * .23 - .21, baseline, colour)
        mini_tree(name + " routing tree", left + width * .74 - .21, baseline, colour)
        text(name + " sequencing", "Sequencing", left + .08, top + height - .32, width * .48 - .10, .25, 13, INK)
        text(name + " routing", "Routing", left + width * .52, top + height - .32, width * .45 - .06, .25, 13, INK)

    def tradeoffs(name, left, top, width, height, colour, suffix=""):
        start = (left + .32, top + height - .25)
        arrow(name + " horizontal axis", start, (left + width - .12, start[1]), INK)
        arrow(name + " vertical axis", start, (start[0], top + .08), INK)
        text(name + " objective 1", "f" + suffix + SUB1 if suffix else "f" + SUB1,
             left + width - .55, top + height - .27, .50, .26, 13, INK, math=True)
        text(name + " objective 2", "f" + suffix + SUB2 if suffix else "f" + SUB2,
             left - .02, top - .03, .57, .26, 13, INK, math=True)
        points = [(left + .50, top + .25), (left + .66, top + .49),
                  (left + .89, top + .64), (left + 1.18, top + .75), (left + 1.50, top + .80)]
        for index, (first, second) in enumerate(zip(points, points[1:])):
            line(slide, name + f" tradeoff segment {index}", first, second, colour, 1.7, False)
        for index, point in enumerate(points):
            circle(slide, name + f" preference point {index}", point, .045, colour, colour)

    text("single heading", "(a) Single-task MO-DFJSS", .30, .20, 5.65, .48, 24, BLUE, True)
    text("multi heading", "(b) Multi-task MO-DFJSS", 7.03, .20, 8.55, .48, 24, ORANGE, True)
    line(slide, "single heading rule", (.38, .82), (6.02, .82), BLUE, 1.3, False)
    line(slide, "multi heading rule", (7.14, .82), (15.56, .82), ORANGE, 1.3, False)

    box("single task", "One environment: D, (f" + SUB1 + ", f" + SUB2 + ")", .78, 1.08, 4.85, .65, BLUE, BLUE_LIGHT, 21)
    text("dynamic arrivals", "Dynamic arrivals  /  routing  /  machine queues", .56, 1.87, 5.27, .35, 16, GRAY)
    box("single state", "Shop state s", .77, 2.45, 2.05, .58, GRAY, GRAY_LIGHT, 18)
    box("single preferences", "Preferences " + LAMBDA, 3.30, 2.45, 2.34, .58, ORANGE, ORANGE_LIGHT, 18)
    arrow("state input", (1.80, 3.03), (2.53, 3.55), BLUE)
    arrow("preference input", (4.47, 3.03), (3.77, 3.55), ORANGE)
    heuristic("single heuristic", "h(s, " + LAMBDA + ")", 1.27, 3.55, 3.87, 1.42, BLUE, BLUE_LIGHT)
    arrow("single response", (3.20, 4.97), (3.20, 5.42), BLUE)
    tradeoffs("single front", 2.11, 5.50, 2.02, 1.34, BLUE)
    text("single outcome description", "One heuristic; different trade-offs", .67, 7.05, 5.10, .42, 19, BLUE, True)

    arrow("extension", (6.13, 3.89), (6.91, 3.89), ORANGE)
    text("extension label", "Across\ntasks", 6.05, 2.95, .95, .67, 16, GRAY)

    form(slide, "joint learning region", 9.83, 1.13, 3.16, 5.83, "FFFFFF", ORANGE, dashed=True, weight=1.4)
    text("joint learning heading", "Joint GP learning", 9.93, 1.22, 2.96, .38, 21, ORANGE, True)
    text("task column heading", "DFJSS tasks", 7.11, 1.10, 2.44, .42, 17, GRAY, True)
    text("front column heading", "Local objectives", 13.22, 1.10, 2.45, .42, 17, GRAY, True)

    rows = [("1", SUB1, BLUE, BLUE_LIGHT), ("2", SUB2, ORANGE, ORANGE_LIGHT), ("t", SUBT, GRAY, GRAY_LIGHT)]
    for index, (task, suffix, colour, pale) in enumerate(rows):
        top = 1.87 + index * 1.67
        task_symbol = "Task " + task + "\nD" + suffix + ", (f" + suffix + SUB1 + ", f" + suffix + SUB2 + ")"
        box("task " + task, task_symbol, 7.16, top + .16, 2.34, .83, colour, pale, 18)
        arrow("task " + task + " evaluation", (9.50, top + .57), (10.02, top + .57), colour)
        heuristic("heuristic " + task, "h" + suffix + "(s, " + LAMBDA + ")", 10.03, top, 2.76, 1.24, colour, pale)
        arrow("task " + task + " outcomes", (12.79, top + .57), (13.35, top + .57), colour)
        tradeoffs("task " + task + " front", 13.36, top - .03, 2.13, 1.32, colour, suffix)
    arrow("sharing down first", (11.24, 3.16), (11.24, 3.48), ORANGE)
    arrow("sharing up first", (11.56, 3.48), (11.56, 3.16), BLUE)
    arrow("sharing down second", (11.24, 4.83), (11.24, 5.15), ORANGE)
    arrow("sharing up second", (11.56, 5.15), (11.56, 4.83), GRAY)
    text("shared structures", "Exchange rule structures", 9.91, 6.57, 2.99, .28, 14, GRAY)
    text("task distinctions", "Tasks differ in load, workload or objective pair", 7.13, 7.06, 8.50, .42, 18, GRAY)

    notes = slide.notes_slide.notes_text_frame
    notes.text = (
        "Based on the current Section II.A and II.B of mpslgp-method.tex. "
        "D is one task distribution; D_t and (f_t,1,f_t,2) define each multitask problem. "
        "h_t is a task-specific sequencing-routing tree pair conditioned on shop state and preferences. "
        "The curves are conceptual illustrations of preference-induced trade-offs, not experimental fronts. "
        "Task1, task2 and taskt illustrate members of a general task collection; exchange links are schematic, not an adjacency restriction. "
        "Joint learning exchanges structures while preserving each task's evaluation context. "
        "Every visible element is a native editable PowerPoint shape, connector or text box."
    )
    deck.core_properties.title = "From Single-Task to Multi-Task Multiobjective DFJSS"
    deck.core_properties.subject = "Editable problem illustration based on Sections II.A and II.B"
    deck.save(OUTPUT)
    ns = {"a": "http://schemas.openxmlformats.org/drawingml/2006/main", "p": "http://schemas.openxmlformats.org/presentationml/2006/main"}
    with ZipFile(OUTPUT) as archive:
        xml = etree.fromstring(archive.read("ppt/slides/slide1.xml"))
        assert not xml.xpath("//p:pic", namespaces=ns)
        assert not [name for name in archive.namelist() if name.startswith("ppt/media/")]
        text_nodes = xml.xpath("//a:t/text()", namespaces=ns)
        assert any("h" + SUB1 in text for text in text_nodes) and any("h" + SUBT in text for text in text_nodes)
        assert len(deck.slides) == 1
    assert digest(existing) == existing_hash and digest(SOURCE) == source_hash
    report = {"Slides": 1, "EditableShapes": len(slide.shapes), "TextElements": len(text_nodes), "EmbeddedImages": 0,
              "SourceSectionIIABSHA256": source_hash, "ExistingFigureDeckSHA256": existing_hash,
              "Palette": {"Blue": BLUE, "Orange": ORANGE, "Gray": GRAY},
              "PPTXSHA256": digest(OUTPUT)}
    (ROOT / "DFJSS_Single_to_Multitask-validation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    print("PASS: native editable PPT diagram generated; no image objects; existing manuscript and figure deck unchanged.")


if __name__ == "__main__":
    build()