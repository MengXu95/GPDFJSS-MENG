from collections import Counter
from hashlib import sha256
from pathlib import Path
from zipfile import ZipFile

from lxml import etree
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.dml import MSO_LINE_DASH_STYLE
from pptx.enum.shapes import MSO_AUTO_SHAPE_TYPE, MSO_CONNECTOR, MSO_SHAPE_TYPE
from pptx.enum.text import MSO_ANCHOR, MSO_AUTO_SIZE, PP_ALIGN
from pptx.oxml.xmlchemy import OxmlElement
from pptx.util import Inches, Pt


ROOT = Path(__file__).resolve().parent
REFERENCE = ROOT / "reference.pptx"
OUTPUT = ROOT / "MPSLGP_Editable_Figures.pptx"
REFERENCE_HASH = "5D9403D46014EAE6625672536C1A716E843F9FA8A4FDD1FD93AF40412BD43049"
FONT = "Comic Sans MS"
MATH = "Cambria Math"
INK = "111827"
BLUE = "2563EB"
BLUE_DARK = "1D4ED8"
BLUE_LIGHT = "DBEAFE"
BLUE_MID = "93C5FD"
ORANGE = "F97316"
ORANGE_DARK = "C2410C"
ORANGE_LIGHT = "FFEDD5"
ORANGE_MID = "FDBA74"
GRAY = "64748B"
GRAY_DARK = "334155"
GRAY_LIGHT = "F3F4F6"
GRAY_MID = "B4C7E7"
WHITE = "FFFFFF"
LAMBDA = "\u03bb"
MULTIPLY = "\u00d7"
RIGHT = "\u2192"
LEFT = "\u2190"
MINUS = "\u2212"
TAU = "\u03c4"
ETA = "\u03b7"
EPSILON = "\u03b5"
THETA = "\u03b8"
SIGMA = "\u03a3"
GE = "\u2265"
NE = "\u2260"
SUB = ("\u2080", "\u2081", "\u2082", "\u2083")


def rgb(value):
    return RGBColor.from_string(value)


def label(slide, name, left, top, width, height, content, size=16,
          ink=INK, bold=False, font=FONT, align=PP_ALIGN.CENTER):
    item = slide.shapes.add_textbox(Inches(left), Inches(top), Inches(width), Inches(height))
    item.name = name
    frame = item.text_frame
    frame.clear()
    frame.word_wrap = True
    frame.auto_size = MSO_AUTO_SIZE.NONE
    frame.margin_left = frame.margin_right = Inches(0.02)
    frame.margin_top = frame.margin_bottom = Inches(0.015)
    frame.vertical_anchor = MSO_ANCHOR.MIDDLE
    for index, line in enumerate(content.split("\n")):
        paragraph = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        paragraph.alignment = align
        paragraph.line_spacing = 1.02
        paragraph.space_before = paragraph.space_after = Pt(0)
        run = paragraph.add_run()
        run.text = line
        run.font.name = font
        run.font.size = Pt(size)
        run.font.bold = bold
        run.font.color.rgb = rgb(ink)
    return item


def form(slide, name, left, top, width, height, fill=WHITE, stroke=BLUE_MID,
         kind=MSO_AUTO_SHAPE_TYPE.ROUNDED_RECTANGLE, dashed=False, weight=1.1):
    item = slide.shapes.add_shape(kind, Inches(left), Inches(top), Inches(width), Inches(height))
    item.name = name
    if kind == MSO_AUTO_SHAPE_TYPE.ROUNDED_RECTANGLE:
        item.adjustments[0] = 0.045
    if fill is None:
        item.fill.background()
    else:
        item.fill.solid()
        item.fill.fore_color.rgb = rgb(fill)
    if stroke is None:
        item.line.fill.background()
    else:
        item.line.color.rgb = rgb(stroke)
        item.line.width = Pt(weight)
        if dashed:
            item.line.dash_style = MSO_LINE_DASH_STYLE.DASH
    return item


def block(slide, name, left, top, width, height, content, fill=BLUE_LIGHT,
          stroke=BLUE, ink=BLUE_DARK, size=16, bold=False,
          kind=MSO_AUTO_SHAPE_TYPE.ROUNDED_RECTANGLE):
    item = form(slide, name, left, top, width, height, fill, stroke, kind)
    label(slide, name + " text", left + 0.065, top + 0.025,
          width - 0.13, height - 0.05, content, size, ink, bold)
    return item


def panel(slide, name, left, top, width, height, title, accent=BLUE, size=18):
    pale = BLUE_LIGHT if accent == BLUE else ORANGE_LIGHT
    dark = BLUE_DARK if accent == BLUE else ORANGE_DARK
    stroke = BLUE_MID if accent == BLUE else ORANGE_MID
    item = form(slide, name, left, top, width, height, WHITE, stroke)
    block(slide, name + " header", left + 0.10, top + 0.10,
          width - 0.20, 0.52, title, pale, accent, dark, size, True)
    return item


def line(slide, name, start, end, ink=INK, width=1.4, arrow=True, dashed=False,
         source=None, target=None, source_site=3, target_site=1):
    item = slide.shapes.add_connector(MSO_CONNECTOR.STRAIGHT,
                                     Inches(start[0]), Inches(start[1]),
                                     Inches(end[0]), Inches(end[1]))
    item.name = name
    item.line.color.rgb = rgb(ink)
    item.line.width = Pt(width)
    if dashed:
        item.line.dash_style = MSO_LINE_DASH_STYLE.DASH
    if arrow:
        tip = OxmlElement("a:tailEnd")
        tip.set("type", "triangle")
        tip.set("w", "med")
        tip.set("len", "med")
        item._element.spPr.get_or_add_ln().append(tip)
    if source is not None:
        item.begin_connect(source, source_site)
    if target is not None:
        item.end_connect(target, target_site)
    return item


def route(slide, name, points, ink=INK, width=1.5, dashed=False, arrow=True):
    return [line(slide, name + f" segment {index}", start, end, ink, width,
                 arrow and index == len(points) - 2, dashed)
            for index, (start, end) in enumerate(zip(points, points[1:]))]


def circle(slide, name, center, radius, fill, stroke=None):
    return form(slide, name, center[0] - radius, center[1] - radius,
                radius * 2, radius * 2, fill, stroke, MSO_AUTO_SHAPE_TYPE.OVAL)


def group(slide, name, members):
    item = slide.shapes.add_group_shape(members)
    item.name = name
    return item


def miniature_tree(slide, name, left, top, width=0.55, height=0.55, accent=BLUE):
    root = (left + width / 2, top + 0.09)
    leaves = ((left + 0.09, top + height - 0.09),
              (left + width - 0.09, top + height - 0.09))
    members = [line(slide, name + f" edge {index}", root, leaf, GRAY, 0.85, False)
               for index, leaf in enumerate(leaves)]
    members.append(circle(slide, name + " root", root, 0.09, BLUE_LIGHT, accent))
    members.append(circle(slide, name + " state terminal", leaves[0], 0.075, GRAY_MID, accent))
    members.append(circle(slide, name + " preference terminal", leaves[1], 0.075, ORANGE_MID, ORANGE))
    return group(slide, name, members)


def individual(slide, name, left, top, width=1.30, height=0.86, task="1"):
    members = [form(slide, name + " border", left, top, width, height, WHITE, BLUE)]
    members.append(label(slide, name + " task index", left + 0.07, top + 0.015,
                         width - 0.14, 0.23, "z = " + task, 12, ORANGE_DARK, font=MATH))
    tree_width = (width - 0.22) / 2
    members.append(miniature_tree(slide, name + " sequencing", left + 0.07,
                                 top + 0.26, tree_width, height - 0.30))
    members.append(miniature_tree(slide, name + " routing", left + width / 2 + 0.02,
                                 top + 0.26, tree_width, height - 0.30))
    return group(slide, name, members)


def machine(slide, name, left, top, caption, scale=1.0, accent=BLUE):
    members = [form(slide, name + " body", left, top, 0.54 * scale, 0.34 * scale,
                    BLUE_LIGHT if accent == BLUE else GRAY_LIGHT, accent,
                    MSO_AUTO_SHAPE_TYPE.RECTANGLE)]
    members.append(form(slide, name + " base", left - 0.04 * scale,
                        top + 0.34 * scale, 0.62 * scale, 0.06 * scale,
                        accent, None, MSO_AUTO_SHAPE_TYPE.RECTANGLE))
    members.append(label(slide, name + " name", left, top, 0.54 * scale,
                         0.31 * scale, caption, 11.5 * scale, accent))
    return group(slide, name, members)


def utilities(slide, name, left, top, cell=0.46, donor_heading=False):
    if donor_heading:
        label(slide, name + " donor heading", left, top - 0.64,
              cell * 3, 0.31, "Donor task d", 12, GRAY)
    for column in range(3):
        label(slide, name + f" column {column}", left + column * cell,
              top - 0.30, cell, 0.28, str(column + 1), 13, GRAY_DARK, font=MATH)
    for row in range(3):
        label(slide, name + f" row {row}", left - 0.29, top + row * cell,
              0.25, cell, str(row + 1), 13, GRAY_DARK, font=MATH)
        for column in range(3):
            active = row == 0 and column != row
            fill = GRAY_LIGHT if row == column else ORANGE_LIGHT if active else BLUE_LIGHT
            stroke = ORANGE if active else GRAY
            form(slide, name + f" cell {row} {column}", left + column * cell,
                 top + row * cell, cell, cell, fill, stroke, MSO_AUTO_SHAPE_TYPE.RECTANGLE)
            value = MINUS if row == column else "U" + SUB[row + 1] + SUB[column + 1]
            label(slide, name + f" cell label {row} {column}", left + column * cell + 0.01,
                  top + row * cell + 0.01, cell - 0.02, cell - 0.02, value, 12,
                  ORANGE_DARK if active else GRAY_DARK, font=MATH)


def frontier(slide, name, left, top, width, height, compact=False):
    origin = (left + 0.25, top + height - 0.31)
    line(slide, name + " objective 1 axis", origin, (left + width - 0.13, origin[1]), GRAY_DARK, 1.2)
    line(slide, name + " objective 2 axis", origin, (origin[0], top + 0.08), GRAY_DARK, 1.2)
    label(slide, name + " objective 1 label", left + width - 0.88,
          origin[1] + 0.02, 0.77, 0.30, "Obj. 1", 12 if compact else 15, GRAY_DARK)
    label(slide, name + " objective 2 label", left + 0.33, top,
          0.80, 0.31, "Obj. 2", 12 if compact else 15, GRAY_DARK)
    coordinates = []
    for index in range(37):
        fraction = index / 36
        point_x = origin[0] + 0.30 + fraction * (width - 0.76)
        point_y = top + 0.46 + (1 - (1 - fraction) ** 2.4) * (height - 1.01)
        coordinates.append((point_x, point_y))
    builder = slide.shapes.build_freeform(Inches(coordinates[0][0]), Inches(coordinates[0][1]))
    builder.add_line_segments([(Inches(point_x), Inches(point_y))
                               for point_x, point_y in coordinates[1:]], close=False)
    curve = builder.convert_to_shape()
    curve.name = name + " editable schematic frontier"
    curve.fill.background()
    curve.line.color.rgb = rgb(GRAY_DARK)
    curve.line.width = Pt(1.8)
    for count, (index, ink) in enumerate(((5, BLUE), (17, GRAY), (29, ORANGE)), 1):
        point_x, point_y = coordinates[index]
        circle(slide, name + f" outcome {count}", (point_x, point_y), 0.065 if compact else 0.095, ink)
        label(slide, name + f" outcome label {count}", point_x + 0.08,
              point_y - 0.20, 0.46, 0.30, "P" + SUB[count], 12 if compact else 14.5, ink, font=MATH)


def new_slide(deck, title, subtitle):
    slide = deck.slides.add_slide(deck.slide_layouts[6])
    slide.background.fill.solid()
    slide.background.fill.fore_color.rgb = rgb(WHITE)
    label(slide, "Figure title", 0.45, 0.21, 17.1, 0.48,
          title, 24, bold=True, align=PP_ALIGN.LEFT)
    label(slide, "Figure subtitle", 0.47, 0.76, 17.0, 0.42,
          subtitle, 15, GRAY_DARK, align=PP_ALIGN.LEFT)
    return slide


def stage(slide, name, top, badge, title, accent=BLUE):
    block(slide, name + " badge", 0.45, top, 1.32, 0.43, badge,
          BLUE_LIGHT if accent == BLUE else ORANGE_LIGHT, accent,
          BLUE_DARK if accent == BLUE else ORANGE_DARK, 16, True)
    label(slide, name + " heading", 1.93, top - 0.005, 10.6, 0.43,
          title, 18, bold=True, align=PP_ALIGN.LEFT)


def draw_framework(deck):
    slide = new_slide(deck, "Overall framework of MPSLGP",
                      "Shared search across tasks; one preference-conditioned sequencing-routing heuristic per task")
    stage(slide, "PSLGP stage", 1.22, "PSLGP", "Task-local learning across objective preferences")
    label(slide, "Color key", 12.60, 1.19, 4.93, 0.43,
          "Blue: inherited core   Orange: extension", 12.5, GRAY_DARK)
    initial = panel(slide, "Population", 0.45, 1.82, 3.25, 3.65, "Initial population P0")
    preselection = panel(slide, "Preselection", 4.20, 1.82, 3.85, 3.65, "Brood preselection")
    evaluation = panel(slide, "Evaluation", 8.55, 1.82, 4.05, 3.65, "Task-specific evaluation")
    quality = panel(slide, "Cross-preference quality", 13.10, 1.82, 4.45, 3.65, "Across-preference quality")
    label(slide, "Population description", 0.64, 2.54, 2.86, 0.58,
          "N dual-tree individuals\none shared population", 15.5)
    for row in range(2):
        for column in range(2):
            individual(slide, f"Candidate {row} {column}", 0.72 + 1.39 * column,
                       3.23 + 0.98 * row, task=str((row * 2 + column) % 3 + 1))
    label(slide, "Initial values", 0.63, 5.14, 2.90, 0.28,
          "g = 0; U[r,d,k] = 0", 13, GRAY, font=MATH)
    label(slide, "Preselection conditions", 4.37, 2.51, 3.51, 0.51,
          "g > 0: candidate pool of 5N\ng = 0: keep the initial population", 13.5)
    for index in range(10):
        circle(slide, f"Brood point {index}", (4.68 + 0.30 * index, 3.21),
               0.072, ORANGE if index in (2, 7) else BLUE_MID, BLUE)
    block(slide, "Same-task KNN", 4.55, 3.49, 3.15, 0.51, "Same-task KNN estimates", size=16)
    line(slide, "Brood into surrogate", (6.125, 3.30), (6.125, 3.49), GRAY)
    label(slide, "Preselection clearing", 4.43, 4.12, 3.39, 0.36,
          "Clear within-task duplicates", 14.5)
    block(slide, "Balanced retention", 4.53, 4.61, 3.19, 0.56,
          f"Balanced retention {RIGHT} N", ORANGE_LIGHT, ORANGE, ORANGE_DARK, 15.5)
    block(slide, "Main preference", 8.82, 2.53, 3.51, 0.46,
          f"Main preference {LAMBDA}", size=16)
    for index in range(3):
        row_top = 3.21 + index * 0.56
        block(slide, f"Task {index + 1}", 8.82, row_top, 1.16, 0.41,
              f"Task {index + 1}", GRAY_LIGHT, GRAY, GRAY_DARK, 14)
        machine(slide, f"Simulator {index}", 10.23, row_top + 0.015, "sim", 0.80)
        line(slide, f"Simulator output {index}", (10.78, row_top + 0.20), (11.14, row_top + 0.20), GRAY)
        label(slide, f"Observed objectives {index}", 11.22, row_top - 0.02,
              1.13, 0.46, "(F1, F2)", 16.5, font=MATH)
    label(slide, "Task reference scales", 8.77, 4.99, 3.62, 0.33,
          "Task-local ranks and reference scales", 13, ORANGE_DARK)
    label(slide, "Auxiliary preferences", 13.29, 2.52, 4.08, 0.47,
          "Same-task KNN at auxiliary preferences", 14.5)
    miniature_tree(slide, "Fixed routing tree", 13.35, 3.24, 0.53, 0.63)
    miniature_tree(slide, "Fixed sequencing tree", 13.99, 3.24, 0.53, 0.63)
    label(slide, "One heuristic", 13.34, 4.00, 1.18, 0.33, "one h", 15, BLUE_DARK)
    line(slide, "Heuristic quality", (14.57, 3.62), (15.02, 3.62), BLUE)
    frontier(slide, "Preference response", 15.05, 3.03, 2.15, 1.55, True)
    label(slide, "Aggregated quality", 13.30, 4.66, 4.02, 0.66,
          "HV / IGD / GD + scalar score\nPreference diversity; cache samples", 13.5)
    for index, (source, target) in enumerate(((initial, preselection), (preselection, evaluation), (evaluation, quality))):
        line(slide, f"Learning flow {index}", (0, 0), (0, 0), source=source, target=target)
    stage(slide, "Transfer stage", 6.18, "MPSLGP", "Adaptive task transfer and offspring feedback", ORANGE)
    variation = panel(slide, "Variation", 0.45, 6.78, 3.25, 3.55, "Variation + inheritance", ORANGE, 16.2)
    gate = panel(slide, "Regional gate", 4.20, 6.78, 3.85, 3.55, "C1  Regional transfer gate", ORANGE, 16.5)
    feedback = panel(slide, "Actual-child feedback", 13.10, 6.78, 4.45, 3.55,
                     "C2  Actual-offspring feedback", ORANGE, 17)
    line(slide, "Quality into feedback", (0, 0), (0, 0), ORANGE, 1.9,
         source=quality, target=feedback, source_site=2, target_site=0)
    label(slide, "Variation operator", 0.63, 7.50, 2.87, 0.34, "One-tree crossover", 17)
    miniature_tree(slide, "Primary tree", 0.77, 8.00, 0.77, 0.62)
    miniature_tree(slide, "Donor tree", 2.47, 8.00, 0.77, 0.62, ORANGE)
    line(slide, "Subtree transfer", (1.64, 8.31), (2.34, 8.31), ORANGE, 2.0)
    label(slide, "Inheritance and events", 0.62, 8.79, 2.92, 0.65,
          "Node-based task inheritance\nUnique transfer event ID", 13.2, ORANGE_DARK)
    label(slide, "Brood composition", 0.62, 9.58, 2.92, 0.56,
          "Mutation + copy + elites\nNext brood pool: 5N", 13.5)
    label(slide, "Regional choice", 4.45, 7.50, 3.35, 0.34, "Next preference region k", 15.5)
    utilities(slide, "Utility matrix", 4.90, 8.20, 0.44)
    label(slide, "Regional utilities", 6.47, 8.16, 1.21, 0.69,
          "U[r,d,k]\nper region", 14.5, ORANGE_DARK, font=MATH)
    block(slide, "Local option", 6.37, 9.07, 1.40, 0.44,
          "No transfer", GRAY_LIGHT, GRAY, GRAY_DARK, 11.8)
    label(slide, "Gate output", 4.36, 9.76, 3.53, 0.37,
          "Local mate or sampled donor task", 14)
    line(slide, "Gate into variation", (0, 0), (0, 0), ORANGE, 1.9,
         source=gate, target=variation, source_site=1, target_site=3)
    label(slide, "Credit timing", 13.33, 7.54, 3.99, 0.58,
          "Track children generated in g - 1\nCount valid survivors in g", 15)
    block(slide, "Matched local baseline", 13.43, 8.24, 3.77, 0.73,
          "Same task + same generation\nBest valid non-transfer baseline", size=14)
    label(slide, "Reward evidence", 13.35, 9.15, 3.95, 0.40,
          f"Improvement + survival {MINUS} penalty", 14.7, ORANGE_DARK)
    label(slide, "Utility feedback", 13.41, 9.73, 3.84, 0.37,
          "Update U[r,d,k]; no extra simulation", 13.3)
    block(slide, "Termination", 9.31, 7.84, 2.48, 1.42,
          "Stop\ntraining?", GRAY_LIGHT, GRAY, GRAY_DARK, 17,
          kind=MSO_AUTO_SHAPE_TYPE.DIAMOND)
    line(slide, "Feedback into stop check", (13.10, 8.55), (11.79, 8.55), ORANGE, 1.9)
    line(slide, "No stop into gate", (9.31, 8.55), (8.05, 8.55), ORANGE, 1.9)
    label(slide, "No branch", 8.40, 8.15, 0.60, 0.32, "No", 15, ORANGE_DARK)
    label(slide, "Instance rotation", 8.67, 6.94, 3.76, 0.66,
          "Continue: rotate task instances\nand advance the main preference", 13.5, GRAY_DARK)
    block(slide, "Deployment goal", 8.64, 9.66, 3.88, 0.64,
          "One selected heuristic per task\nSame trees; new preference inputs", size=13.3, bold=True)
    line(slide, "Stop into output", (10.55, 9.26), (10.55, 9.66), GRAY)
    label(slide, "Yes branch", 10.83, 9.27, 0.55, 0.30, "Yes", 13, GRAY_DARK)
    route(slide, "Next-generation loop", ((0.45, 8.55), (0.19, 8.55), (0.19, 5.95),
                                         (6.125, 5.95), (6.125, 5.47)), ORANGE, 2.0)
    label(slide, "Generation loop label", 1.05, 5.56, 4.65, 0.33,
          f"Next brood pool Q (5N); g {LEFT} g + 1", 14, ORANGE_DARK, font=MATH)
    label(slide, "Task preservation", 0.48, 10.53, 17.0, 0.30,
          "C3: task labels, within-task evaluation and balanced retention preserve each task's search within the shared budget.",
          13.4, GRAY_DARK, align=PP_ALIGN.LEFT)
    slide.notes_slide.notes_text_frame.text = (
        "Based on GPRuleEvolutionStatePSL.evolve, the HV evaluator, PSLParentSelection, and PSLBreederKeepParetoFront. "
        "Initialization occurs once; subsequent iterations enter brood preselection directly. Main-preference simulation "
        "precedes cross-preference estimation and utility feedback. Post-evaluation clearing is optional. The region used "
        "during breeding belongs to the next evaluation generation. A fixed selected heuristic per task is the deployment "
        "goal. The diagrams use an illustrative task count, and the front is schematic, not a result. Palette and typography "
        "follow reference.pptx slides 1 and 2. All elements are native editable PowerPoint objects."
    )


def draw_transfer(deck):
    slide = new_slide(deck, "Preference-region-aware task knowledge transfer",
                      "Directed task-pair utilities guide mating; actual offspring outcomes provide the feedback")
    context = panel(slide, "Context", 0.45, 1.57, 5.15, 7.47,
                    "1  Select the transfer context", size=18)
    controller = panel(slide, "Controller", 6.20, 1.57, 5.15, 7.47,
                       "2  Gate, mate and inherit", ORANGE, 18)
    feedback = panel(slide, "Credit", 11.95, 1.57, 5.60, 7.47,
                     "3  Credit observed offspring", ORANGE, 18)
    individual(slide, "Primary heuristic", 0.91, 2.42, 1.75, 1.15, "r" + SUB[0])
    label(slide, "Primary source", 2.86, 2.57, 2.34, 0.77,
          "Primary task r" + SUB[0] + "\nPSLGP tournament", 15)
    label(slide, "Next preference context", 0.76, 3.86, 4.51, 0.64,
          f"Next evaluation preference {LAMBDA}\nLocate its preference region k", 15.5)
    for index in range(5):
        block(slide, f"Region {index + 1}", 0.97 + 0.82 * index, 4.69, 0.82, 0.47,
              str(index + 1), ORANGE_MID if index == 2 else BLUE_LIGHT,
              ORANGE if index == 2 else BLUE_MID,
              ORANGE_DARK if index == 2 else BLUE_DARK, 15,
              kind=MSO_AUTO_SHAPE_TYPE.RECTANGLE)
    label(slide, "Region order", 0.81, 5.22, 4.47, 0.33,
          "Obj. 2 emphasis                  Obj. 1 emphasis", 11.5, GRAY)
    label(slide, "Selected utility context", 0.77, 5.81, 4.52, 0.40,
          f"Read U[r{SUB[0]},d,k] in the selected region", 15.5, ORANGE_DARK)
    utilities(slide, "Directed utilities", 1.14, 6.77, 0.51, True)
    label(slide, "Matrix meanings", 3.08, 6.57, 2.14, 1.53,
          "Rows: receiving tasks\nColumns: donor tasks\nOne matrix per region", 14.1, GRAY_DARK)
    label(slide, "Asymmetry", 0.75, 8.51, 4.57, 0.30,
          f"Directed evidence: U[r,d,k] {NE} U[d,r,k] in general", 12.5, GRAY)

    block(slide, "Gating equations", 6.47, 2.43, 4.61, 1.22,
          f"p(d | r{SUB[0]},k) = B exp(U[r{SUB[0]},d,k]/{TAU}) / Z\n"
          f"Z = exp(U{SUB[0]}/{TAU}) + {SIGMA}j{NE}r{SUB[0]} exp(U[r{SUB[0]},j,k]/{TAU})\n"
          f"p(local) = 1 {MINUS} {SIGMA}d{NE}r{SUB[0]} p(d | r{SUB[0]},k)",
          ORANGE_LIGHT, ORANGE, ORANGE_DARK, 13.2)
    label(slide, "Budget and no-transfer reference", 6.59, 3.78, 4.38, 0.32,
          f"B: transfer budget; U{SUB[0]}: fixed local reference", 12.8, GRAY)
    block(slide, "Local mating", 6.46, 4.44, 2.15, 0.82,
          "Local mating\nsame-task parent", size=14.2)
    block(slide, "Transfer mating", 8.96, 4.44, 2.13, 0.82,
          "Transfer\nsample donor task", ORANGE_LIGHT, ORANGE, ORANGE_DARK, 14.2)
    route(slide, "Choose local source", ((6.47, 3.27), (6.33, 3.27),
                                        (6.33, 4.23), (7.535, 4.23), (7.535, 4.44)), BLUE)
    route(slide, "Choose donor source", ((11.08, 3.27), (11.22, 3.27),
                                        (11.22, 4.23), (10.02, 4.23), (10.02, 4.44)), ORANGE)
    route(slide, "Local into crossover", ((7.535, 5.26), (7.535, 5.44),
                                          (8.78, 5.44), (8.78, 6.10)), BLUE)
    route(slide, "Donor into crossover", ((10.02, 5.26), (10.02, 5.44),
                                          (8.78, 5.44)), ORANGE, arrow=False)
    label(slide, "Crossover operation", 6.47, 5.50, 2.12, 0.57,
          "One-tree\ncrossover", 14.5, bold=True)
    miniature_tree(slide, "Primary contributor", 6.77, 6.17, 0.82, 0.71)
    miniature_tree(slide, "Donor contributor", 9.89, 6.17, 0.82, 0.71, ORANGE)
    block(slide, "Crossover child", 8.13, 6.10, 1.30, 0.86, "child", size=17)
    line(slide, "Primary genetic contribution", (7.72, 6.53), (8.13, 6.53), BLUE)
    line(slide, "Donor genetic contribution", (9.82, 6.53), (9.43, 6.53), ORANGE)
    block(slide, "Inheritance decision", 6.48, 7.37, 4.60, 1.15,
          "Count both parents' nodes across both trees\n"
          f"c_d / (c_p + c_d) {GE} {THETA}: inherit donor task\n"
          "Otherwise keep the primary-parent task",
          ORANGE_LIGHT, ORANGE, ORANGE_DARK, 13.3)
    label(slide, "Credit follows task", 6.56, 8.58, 4.45, 0.30,
          "Credit follows the child's assigned task r", 13.3, GRAY)

    label(slide, "Transfer event criterion", 12.26, 2.42, 4.98, 0.65,
          "Record actual cross-task children only\nBoth parents must contribute nodes", 15)
    for index in range(3):
        block(slide, f"Event {index + 1}", 12.53 + 1.46 * index, 3.22, 1.15, 0.47,
              f"ID {index + 1}", ORANGE_LIGHT if index < 2 else GRAY_LIGHT,
              ORANGE if index < 2 else GRAY, ORANGE_DARK if index < 2 else GRAY_DARK, 14)
    label(slide, "Generated and survived cohorts", 12.27, 3.95, 4.97, 0.68,
          "Brood preselection + task simulation\nCount n generated and s valid survivors", 14.8)
    block(slide, "Non-transfer baseline", 12.25, 4.93, 2.25, 0.90,
          "b: best local score\nsame task and generation", size=12.8)
    block(slide, "Real offspring score", 14.78, 4.93, 2.47, 0.90,
          "f: child's real score\ncurrent main preference", ORANGE_LIGHT, ORANGE, ORANGE_DARK, 12.8)
    label(slide, "Relative improvement", 12.20, 6.12, 5.11, 0.43,
          f"I = clip((b {MINUS} f) / max(|b|, {EPSILON}), {MINUS}1, 1)", 15, GRAY_DARK, font=MATH)
    label(slide, "Group contribution", 12.17, 6.78, 5.15, 0.53,
          f"C = [wI {SIGMA}I + wS s {MINUS} wP(n {MINUS} s + n_non)] / n", 13.5, ORANGE_DARK, font=MATH)
    block(slide, "Updated utility", 12.25, 7.70, 5.0, 0.56,
          f"U[r,d,k] {LEFT} (1 {MINUS} {ETA})U[r,d,k] + {ETA}C", ORANGE_LIGHT, ORANGE, ORANGE_DARK, 15.2)
    label(slide, "Credit once", 12.27, 8.54, 4.97, 0.30,
          "One credit per event; no extra simulation", 13.8, GRAY)
    line(slide, "Context into controller", (0, 0), (0, 0), ORANGE, 1.9,
         source=context, target=controller)
    line(slide, "Controller into outcomes", (0, 0), (0, 0), ORANGE, 1.9,
         source=controller, target=feedback)
    route(slide, "Utility feedback", ((14.75, 9.04), (14.75, 9.59),
                                      (3.025, 9.59), (3.025, 9.04)), ORANGE, 2.2)
    label(slide, "Utility update route", 4.76, 9.84, 9.34, 0.36,
          "Update only the observed receiving-task / donor-task / region entry", 15.5, ORANGE_DARK)
    label(slide, "Attribution qualification", 0.49, 10.47, 17.0, 0.31,
          "The child's inherited task may differ from the primary parent's task. Transfer credit is observed evidence, not a guarantee of Pareto improvement.",
          12.4, GRAY_DARK, align=PP_ALIGN.LEFT)
    slide.notes_slide.notes_text_frame.text = (
        "Adaptive path grounded in donorTransferProbabilities, PSLParentSelection.produce, "
        "setOffspringTaskIndexByContribution, and updateAdaptiveTransferUtilitiesAfterEvaluation. "
        "The probability formula shows the zero minimum-donor-probability setting. B is the effective budget after "
        "optional donor-count scaling; U0 is fixed. Before transferStartGeneration, local mating is used. n includes "
        "discarded offspring; s counts valid, evaluated, uncleared survivors. Improvement uses real current-preference "
        "scalar objectives against the same-generation non-transfer baseline. n_non counts non-improving survivors. "
        "Without a valid baseline, improvement and n_non are omitted; survival and discarded-child penalty remain. "
        "Empty groups are not updated. Each child is identified separately and credited at most once. "
        "Node counts describe ancestry, not causal importance. Matrix entries are symbols, not experimental estimates."
    )


def expression_tree(slide, name, center, top, state_terminals):
    points = {
        "root": (center, top),
        "left": (center - 0.73, top + 0.96),
        "right": (center + 0.73, top + 0.96),
        "preference1": (center - 1.16, top + 1.99),
        "state1": (center - 0.37, top + 1.99),
        "preference2": (center + 0.37, top + 1.99),
        "state2": (center + 1.16, top + 1.99),
    }
    pairs = (("root", "left"), ("root", "right"), ("left", "preference1"),
             ("left", "state1"), ("right", "preference2"), ("right", "state2"))
    members = []
    edges = []
    for parent, child in pairs:
        edge = line(slide, name + " edge " + parent + " " + child,
                    points[parent], points[child], GRAY, 1.15, False)
        edges.append((edge, parent, child))
        members.append(edge)
    nodes = {}
    labels = (("root", "+"), ("left", MULTIPLY), ("right", MULTIPLY),
              ("preference1", "w" + SUB[1]), ("state1", state_terminals[0]),
              ("preference2", "w" + SUB[2]), ("state2", state_terminals[1]))
    for key, content in labels:
        is_preference = key.startswith("preference")
        is_state = key.startswith("state")
        fill = ORANGE_MID if is_preference else GRAY_LIGHT if is_state else BLUE_LIGHT
        stroke = ORANGE if is_preference else GRAY if is_state else BLUE
        radius = 0.30 if is_preference or is_state else 0.255
        node = circle(slide, name + " node " + key, points[key], radius, fill, stroke)
        nodes[key] = node
        members.append(node)
        font_size = 12.5 if is_state and len(content) > 2 else 14 if is_state else 18
        members.append(label(slide, name + " label " + key, points[key][0] - 0.28,
                             points[key][1] - 0.22, 0.56, 0.44, content, font_size,
                             ORANGE_DARK if is_preference else GRAY_DARK if is_state else BLUE_DARK,
                             font=FONT if is_state else MATH))
        if is_preference:
            members.append(label(slide, name + " preference name " + key, points[key][0] - 0.40,
                                 points[key][1] + 0.34, 0.80, 0.30,
                                 "Pone" if key == "preference1" else "Ptwo", 11.5, ORANGE_DARK))
    for edge, parent, child in edges:
        edge.begin_connect(nodes[parent], 2)
        edge.end_connect(nodes[child], 0)
    return group(slide, name, members)


def draw_representation(deck):
    slide = new_slide(deck, "Individual representation in MPSLGP",
                      "One task-labelled individual contains one preference-conditioned sequencing-routing heuristic")
    panel(slide, "Preference input", 0.45, 1.63, 4.05, 3.32, "Bi-objective preference", size=18)
    panel(slide, "State input", 0.45, 5.37, 4.05, 3.54, "Task t: current shop state", size=17)
    panel(slide, "Genotype", 5.26, 1.63, 7.27, 8.28, "One individual x", size=21)
    panel(slide, "Deployment", 13.29, 1.63, 4.26, 8.28, "Deploy the same heuristic", size=16.7)
    origin = (1.15, 4.21)
    line(slide, "Preference w1 axis", origin, (3.10, 4.21), GRAY, 1.2)
    line(slide, "Preference w2 axis", origin, (1.15, 2.56), GRAY, 1.2)
    line(slide, "Preference simplex", (1.15, 2.81), (2.87, 4.21), BLUE_MID, 1.6, False)
    for index, (fraction, ink) in enumerate(((0.80, BLUE), (0.50, GRAY), (0.20, ORANGE)), 1):
        point_x = 1.15 + 1.72 * fraction
        point_y = 2.81 + 1.40 * fraction
        circle(slide, f"Preference point {index}", (point_x, point_y), 0.077, ink)
        label(slide, f"Preference symbol {index}", point_x + 0.09, point_y - 0.34,
              0.61, 0.30, LAMBDA + SUB[index], 15, ink, font=MATH)
    label(slide, "w1 label", 2.48, 4.25, 0.68, 0.32, "w" + SUB[1], 16, GRAY_DARK, font=MATH)
    label(slide, "w2 label", 0.62, 2.52, 0.49, 0.32, "w" + SUB[2], 16, GRAY_DARK, font=MATH)
    label(slide, "Preference vector", 2.98, 2.71, 1.29, 1.20,
          f"{LAMBDA} = (w{SUB[1]},w{SUB[2]})\nw{SUB[1]} + w{SUB[2]} = 1", 15, font=MATH)
    label(slide, "Preference role", 0.71, 4.60, 3.54, 0.27,
          "User preference, not another GP tree", 13.1, BLUE_DARK)
    for index in range(3):
        machine(slide, f"Machine {index + 1}", 0.84 + 1.10 * index, 6.34,
                f"M{index + 1}", 1.25, GRAY if index == 1 else BLUE)
        for queue_index in range(index + 1):
            form(slide, f"Machine {index} queue {queue_index}", 0.91 + 1.10 * index,
                 7.02 + queue_index * 0.14, 0.52, 0.095,
                 ORANGE_LIGHT if index == 2 else BLUE_LIGHT,
                 ORANGE_MID if index == 2 else BLUE_MID, MSO_AUTO_SHAPE_TYPE.RECTANGLE, weight=0.7)
    label(slide, "State extraction", 0.72, 7.63, 3.51, 0.36,
          f"Extract state features {RIGHT} s", 15.5)
    for index, feature in enumerate(("NIQ", "WIQ", "PT", "SL")):
        block(slide, "Terminal " + feature, 0.72 + 0.89 * index, 8.17, 0.76, 0.44,
              feature, GRAY_LIGHT, GRAY, GRAY_DARK, 14)
    block(slide, "Function set", 0.45, 9.30, 4.05, 0.61,
          f"Functions: +  {MINUS}  {MULTIPLY}  /  min  max", size=15)
    block(slide, "Task metadata", 5.57, 2.40, 6.65, 0.63,
          "MPSLGP task label: z = t   (training metadata)", ORANGE_LIGHT, ORANGE, ORANGE_DARK, 16.1)
    label(slide, "Task metadata explanation", 5.72, 3.13, 6.35, 0.37,
          "Chooses the evaluation task, not the user's trade-off", 14, GRAY_DARK)
    label(slide, "Conditioned mapping", 5.53, 3.78, 6.72, 0.44,
          f"h(s, {LAMBDA}): the same two trees for all preferences", 18, BLUE_DARK, True, MATH)
    form(slide, "Inherited PSLGP representation", 5.56, 4.37, 6.66, 3.73,
         None, BLUE_MID, dashed=True)
    expression_tree(slide, "Routing GP tree", 7.16, 4.91, ("WIQ", "PT"))
    expression_tree(slide, "Sequencing GP tree", 10.66, 4.91, ("PT", "SL"))
    label(slide, "Routing tree role", 5.80, 7.58, 2.73, 0.35, "Routing rule", 18, BLUE_DARK, True)
    label(slide, "Sequencing tree role", 9.19, 7.58, 2.95, 0.35, "Sequencing rule", 18, BLUE_DARK, True)
    label(slide, "Routing expression", 5.73, 8.37, 2.91, 0.39,
          f"w{SUB[1]} {MULTIPLY} WIQ + w{SUB[2]} {MULTIPLY} PT", 16, GRAY_DARK, font=MATH)
    label(slide, "Sequencing expression", 9.16, 8.37, 2.94, 0.39,
          f"w{SUB[1]} {MULTIPLY} PT + w{SUB[2]} {MULTIPLY} SL", 16, GRAY_DARK, font=MATH)
    label(slide, "Tree symbol key", 5.62, 9.02, 6.50, 0.32,
          "Blue: functions   Gray: state terminals   Orange: preferences", 12.7, GRAY_DARK)
    label(slide, "Illustrative expressions", 5.65, 9.47, 6.44, 0.27,
          "Illustrative syntax; not a reported learned solution", 12, GRAY)
    label(slide, "Deployment fixed objects", 13.53, 2.48, 3.78, 0.44,
          "Hold task t and the trees fixed", 15.1, BLUE_DARK, True)
    preferences = (("(0.8, 0.2)", BLUE_LIGHT, BLUE, BLUE_DARK),
                   ("(0.5, 0.5)", GRAY_LIGHT, GRAY, GRAY_DARK),
                   ("(0.2, 0.8)", ORANGE_LIGHT, ORANGE, ORANGE_DARK))
    for index, (weights, fill, stroke, ink) in enumerate(preferences, 1):
        block(slide, f"Deployment preference {index}", 13.89, 3.18 + (index - 1) * 0.63,
              3.05, 0.47, f"{LAMBDA}{SUB[index]} = {weights}", fill, stroke, ink, 16.5)
    label(slide, "Output priorities", 13.53, 5.28, 3.77, 0.61,
          "Sequencing / routing priorities\nSchedule on task t", 15)
    line(slide, "Schedule yields outcomes", (15.42, 5.96), (15.42, 6.29), GRAY, 1.5)
    frontier(slide, "Induced Pareto approximation", 13.55, 6.49, 3.73, 2.64)
    label(slide, "Schematic outcomes", 13.49, 9.27, 3.84, 0.51,
          "Schematic outcomes; Pareto-front\napproximation is the learning goal", 12.4, GRAY)
    line(slide, "Preference into two trees", (4.50, 3.59), (5.26, 3.59), BLUE, 1.9)
    label(slide, "Preference edge", 4.51, 3.10, 0.72, 0.33,
          f"w{SUB[1]}, w{SUB[2]}", 13, ORANGE_DARK, font=MATH)
    line(slide, "State into two trees", (4.50, 7.14), (5.26, 7.14), BLUE, 1.9)
    label(slide, "State edge", 4.52, 6.71, 0.69, 0.34, "s", 18, BLUE_DARK, font=MATH)
    line(slide, "Same heuristic into deployment", (12.53, 5.89), (13.29, 5.89), BLUE, 1.9)
    route(slide, "Task annotation", ((12.22, 2.72), (12.88, 2.72),
                                    (12.88, 2.99), (13.89, 2.99)), ORANGE, 1.3, True)
    block(slide, "Deployment interpretation", 0.48, 10.35, 17.02, 0.46,
          f"One selected heuristic per task: changing {LAMBDA} changes decisions, not the tree pair.",
          BLUE_LIGHT, BLUE_MID, BLUE_DARK, 18, True)
    slide.notes_slide.notes_text_frame.text = (
        "Redrawn from reference.pptx slide 3, using the colors and typography of slides 1 and 2. "
        "A PSLGP individual represents one preference-conditioned scheduling heuristic with routing and sequencing "
        "trees. Pone/Ptwo are preference terminals, not selectors of other trees. MPSLGP adds task-index metadata "
        "for evaluation and inheritance, not a universal task-conditioning input. State includes candidate-specific "
        "operation and machine features. Tree outputs are priority scores; objective outcomes require scheduling "
        "or simulation. Toy expressions and outcome points are schematic, not results or guaranteed optimal solutions. "
        "Routing is shown left to follow the supplied figure; code stores sequencing then routing. "
        "Source: M. Xu et al., Pareto Set Learning Through Genetic Programming for Multiobjective Dynamic Scheduling, "
        "IEEE TEVC, DOI 10.1109/TEVC.2025.3568375. All trees, points, axes and labels are editable."
    )


def visit(shapes):
    for item in shapes:
        yield item
        if item.shape_type == MSO_SHAPE_TYPE.GROUP:
            yield from visit(item.shapes)


def remove_effects(deck):
    for slide in deck.slides:
        for item in visit(slide.shapes):
            for effect in item._element.xpath("./p:style/a:effectRef"):
                effect.set("idx", "0")
            properties = getattr(item._element, "spPr", None)
            if properties is not None:
                properties.append(OxmlElement("a:effectLst"))


def validate(path):
    deck = Presentation(path)
    assert len(deck.slides) == 3
    for index, slide in enumerate(deck.slides, 1):
        items = list(visit(slide.shapes))
        assert not any(item.shape_type == MSO_SHAPE_TYPE.PICTURE for item in items)
        for item in items:
            assert item.left >= -2 and item.top >= -2, item.name
            assert item.left + item.width <= deck.slide_width + 2, item.name
            assert item.top + item.height <= deck.slide_height + 2, item.name
        print(f"slide{index}: native_objects={len(items)}, pictures=0, types={dict(Counter(str(item.shape_type) for item in items))}")
    with ZipFile(path) as archive:
        namespace = {"a": "http://schemas.openxmlformats.org/drawingml/2006/main"}
        arrow_count = 0
        for index in range(1, 4):
            root = etree.fromstring(archive.read(f"ppt/slides/slide{index}.xml"))
            arrow_count += len(root.xpath('//a:tailEnd[@type="triangle"]', namespaces=namespace))
        assert arrow_count >= 30
        assert not any(name.startswith("ppt/media/") for name in archive.namelist())
        print(f"arrowheads={arrow_count}; embedded_media=0")
    assert sha256(REFERENCE.read_bytes()).hexdigest().upper() == REFERENCE_HASH
    print("reference=unchanged")


def main():
    assert sha256(REFERENCE.read_bytes()).hexdigest().upper() == REFERENCE_HASH
    deck = Presentation()
    deck.slide_width = Inches(18)
    deck.slide_height = Inches(11)
    deck.core_properties.title = "MPSLGP: Editable Framework and Individual Representation"
    deck.core_properties.subject = "Scientific figures with reference-matched blue, orange and gray palette"
    deck.core_properties.author = "MPSLGP Research"
    draw_framework(deck)
    draw_transfer(deck)
    draw_representation(deck)
    remove_effects(deck)
    deck.save(OUTPUT)
    validate(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()