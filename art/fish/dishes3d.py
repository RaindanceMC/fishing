"""
Builds a 3D item model for every dish in catalog.json (cooked + raw texture) in a running Blockbench (MCP plugin),
paints it at two texels per unit, renders it and writes (under dishes/):

  models/<id>.json  <id>.png  models/raw_<id>.json  raw_<id>.png  bbmodel/<id>.bbmodel
  renders/<id>.png  renders/raw_<id>.png      and ../dish_sheet.png (every dish by tier, cooked | raw)

    python3 dishes3d.py [id ...]      (no ids: every dish, then the sheet)

The shapes (dish_shapes.py) are layers of character grids, one cell = one unit; equal neighbours merge into boxes.
Faces that sit inside the model (touching another box on all sides) are dropped from the model and painted 1 texel.
"""
import json
import math
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "lootbox"))
import dish_paint  # noqa: E402  (also patches paint)
import paint  # noqa: E402
import build  # noqa: E402
import lootbox  # noqa: E402  (camera shots)
import dish_shapes  # noqa: E402
from models import box  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

OUT = os.path.join(HERE, "dishes")
NS = "rainsfishing"
TIERS = ["common", "uncommon", "rare", "epic", "legendary", "mythic"]
SIDES = build.SIDES
NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0),
          "down": (0, -1, 0)}


def r2(v):
    return round(v * 2) / 2


# ------------------------------------------------------------------ layers -> boxes

def merge_cells(cells):
    """cells: set of (row, col) -> rectangles (r0, r1, c0, c1), horizontal runs then equal runs stacked."""
    runs = {}
    for r in sorted({r for r, _ in cells}):
        cols = sorted(c for rr, c in cells if rr == r)
        start = prev = cols[0]
        for c in cols[1:] + [None]:
            if c is None or c != prev + 1:
                runs.setdefault((start, prev), []).append(r)
                start = c
            prev = c if c is not None else prev
    rects = []
    for (c0, c1), rows in runs.items():
        rows.sort()
        start = prev = rows[0]
        for r in rows[1:] + [None]:
            if r is None or r != prev + 1:
                rects.append((start, prev, c0, c1))
                start = r
            prev = r if r is not None else prev
    return sorted(rects)


def parts_of(items):
    parts = []
    for n, item in enumerate(items):
        if "box" in item:
            a, b = item["box"][:3], item["box"][3:]
            parts.append(dict(box("b%d_%s" % (n, item.get("name", "x")), [r2(v) for v in a], [r2(v) for v in b],
                                  item["mat"], item.get("sides")), hide=set()))
            continue
        y0, y1 = item["y"]
        cell = item.get("cell", 1)
        ax, az = item["at"]
        legend = item["legend"]
        groups = {}
        for r, row in enumerate(item["rows"]):
            for c, ch in enumerate(row):
                if ch != ".":
                    groups.setdefault(ch, set()).add((r, c))
        for ch, cells in sorted(groups.items()):
            entry = legend[ch]
            material, sides = (entry, None) if isinstance(entry, str) else entry
            for r0, r1, c0, c1 in merge_cells(cells):
                name = "L%d_%s_%d_%d" % (n, ch, r0, c0)
                parts.append(dict(box(name, [r2(ax + c0 * cell), r2(y0), r2(az + r0 * cell)],
                                      [r2(ax + (c1 + 1) * cell), r2(y1), r2(az + (r1 + 1) * cell)], material, sides),
                                  hide=set()))
    return parts


def voxels(part):
    f, t = part["from"], part["to"]
    rng = [range(int(round(f[i] * 2)), int(round(t[i] * 2))) for i in range(3)]
    return {(x, y, z) for x in rng[0] for y in rng[1] for z in rng[2]}


def cull(parts):
    solid = set()
    for part in parts:
        solid |= voxels(part)
    for part in parts:
        f, t = [int(round(v * 2)) for v in part["from"]], [int(round(v * 2)) for v in part["to"]]
        for side, (nx, ny, nz) in NORMAL.items():
            axis = 0 if nx else (1 if ny else 2)
            sign = nx + ny + nz
            plane = t[axis] if sign > 0 else f[axis] - 1
            ranges = [range(f[i], t[i]) for i in range(3)]
            ranges[axis] = [plane]
            if all((x, y, z) in solid for x in ranges[0] for y in ranges[1] for z in ranges[2]):
                part["hide"].add(side)


def faces_of(parts):
    faces = []
    for part in parts:
        dx, dy, dz = (part["to"][i] - part["from"][i] for i in range(3))
        size = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz),
                "down": (dx, dz)}
        for side in SIDES:
            w, h = size[side]
            if side in part["hide"]:
                w = h = 0.5
            face = paint.Face(part["sides"].get(side, part["material"]),
                              max(1, round(w * paint.DENSITY)), max(1, round(h * paint.DENSITY)), side)
            f, t = part["from"], part["to"]
            if side in ("north", "south"):
                face.wu, face.wv = f[0], -t[1]
            elif side in ("east", "west"):
                face.wu, face.wv = f[2], -t[1]
            else:
                face.wu, face.wv = f[0], f[2]
            faces.append(face)
    return faces


# ------------------------------------------------------------------ display

def rot_xyz(v, rx, ry, rz):
    """JOML rotationXYZ: X first, then Y, then Z."""
    x, y, z = v
    a = math.radians(rx)
    y, z = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
    a = math.radians(ry)
    x, z = x * math.cos(a) + z * math.sin(a), -x * math.sin(a) + z * math.cos(a)
    a = math.radians(rz)
    x, y = x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a)
    return x, y, z


def fit(parts, rotation, size=15.0):
    """Scale and translation that put the rotated model centred in the 16 px gui slot."""
    xs, ys = [], []
    for part in parts:
        f, t = part["from"], part["to"]
        for cx in (f[0], t[0]):
            for cy in (f[1], t[1]):
                for cz in (f[2], t[2]):
                    x, y, _ = rot_xyz((cx - 8, cy - 8, cz - 8), *rotation)
                    xs.append(x)
                    ys.append(y)
    w, h = max(xs) - min(xs), max(ys) - min(ys)
    scale = max(0.4, min(size / max(w, h, 1), 1.4))
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    return round(scale, 3), [round(-cx * scale, 2), round(-cy * scale, 2), 0]


def display_of(parts, view):
    gui_rot = [view.get("el", 30), -view.get("az", 25), view.get("roll", 0)]
    scale, trans = fit(parts, gui_rot)
    held = view.get("held", 0.5)
    hand = {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [held, held, held]}
    display = {
        "gui": {"rotation": gui_rot, "translation": trans, "scale": [scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [view.get("fixed_el", 0), 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "thirdperson_righthand": dict(hand),
        "thirdperson_lefthand": dict(hand),
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13],
                                  "scale": [held + 0.15] * 3},
        "firstperson_lefthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13],
                                 "scale": [held + 0.15] * 3},
    }
    if view.get("roll"):
        display["thirdperson_righthand"]["rotation"] = [0, -90, 45]
        display["thirdperson_lefthand"]["rotation"] = [0, 90, -45]
        display["thirdperson_righthand"]["translation"] = [0, 4, 1]
        display["thirdperson_lefthand"]["translation"] = [0, 4, 1]
    return display


# ------------------------------------------------------------------ Blockbench

def write_model(path, model, hidden, texture):
    out = dict(model)
    out["textures"] = {"0": texture, "particle": texture}
    for element in out.get("elements", []):
        for side in hidden.get(element.get("name"), ()):
            element.get("faces", {}).pop(side, None)
        for face in element.get("faces", {}).values():
            face["texture"] = "#0"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        json.dump(out, handle, indent=1)


def camera(view):
    az, el, dist = math.radians(view.get("az", 25)), math.radians(view.get("el", 30)), view.get("dist", 40)
    target = (0, 8, 0)
    pos = (target[0] - dist * math.sin(az) * math.cos(el), target[1] + dist * math.sin(el),
           dist * math.cos(az) * math.cos(el))
    return pos, target


def make(entry):
    did = entry["id"]
    shape = dish_shapes.DISHES[did]
    view = shape.get("view", {})
    parts = parts_of(shape["items"])
    cull(parts)
    lo = [min(p["from"][i] for p in parts) for i in range(3)]
    hi = [max(p["to"][i] for p in parts) for i in range(3)]
    shift = [8 - (lo[i] + hi[i]) / 2 for i in range(3)]
    for part in parts:
        part["from"] = [round(part["from"][i] + shift[i], 3) for i in range(3)]
        part["to"] = [round(part["to"][i] + shift[i], 3) for i in range(3)]
    faces = faces_of(parts)
    image, spots, size = paint.paint(faces)
    raw_image = paint.paint(faces, colours=dish_paint.raw_colours())[0]
    elements, index = [], 0
    for part in parts:
        uv = {}
        for side in SIDES:
            face = faces[index]
            x, y = spots[index]
            uv[side] = [x, y, x + face.w, y + face.h]
            index += 1
        elements.append({"name": part["name"], "from": part["from"], "to": part["to"], "uv": uv,
                         "rotation": [0, 0, 0], "origin": [8, 8, 8]})
    built = build.eval_js(build.BUILD.replace("SPEC", json.dumps({
        "name": did, "size": size, "texture": build.data_url(image), "elements": elements,
        "display": display_of(parts, view)})))
    for folder in ("bbmodel", "models", "renders"):
        os.makedirs(os.path.join(OUT, folder), exist_ok=True)
    with open(os.path.join(OUT, "bbmodel", did + ".bbmodel"), "w") as handle:
        handle.write(built["project"])
    image.save(os.path.join(OUT, did + ".png"))
    raw_image.save(os.path.join(OUT, "raw_" + did + ".png"))
    hidden = {p["name"]: p["hide"] for p in parts}
    write_model(os.path.join(OUT, "models", did + ".json"), built["model"], hidden, NS + ":item/dish/" + did)
    with open(os.path.join(OUT, "models", "raw_" + did + ".json"), "w") as handle:
        json.dump({"parent": NS + ":item/dish/" + did,
                   "textures": {"0": NS + ":item/dish/raw_" + did, "particle": NS + ":item/dish/raw_" + did}},
                  handle, indent=1)
    render(did, view, image, raw_image, size)
    return len(parts), size


def render(did, view, image, raw_image, size):
    build.eval_js("(() => { Modes.options.edit.select(); return 1; })()")
    roll = view.get("roll", 0)
    if roll:
        build.eval_js("(() => { Cube.all.forEach(c => { c.rotation = [0, 0, %s]; c.origin = [8, 8, 8]; }); "
                      "Canvas.updateAll(); return 1; })()" % roll)
    pos, target = camera(view)
    for name, img in ((did, image), ("raw_" + did, raw_image)):
        if name != did:
            build.eval_js("""(() => { const t = Texture.all[0]; t.fromDataURL('%s'.split('_').join('/')); return 1; })()"""
                          % build.data_url(img))
        time.sleep(0.9)
        lootbox.shot(os.path.join(OUT, "renders", name + ".png"), pos, target, fov=view.get("fov", 32), size=420)


# ------------------------------------------------------------------ sheet

def sheet(catalog):
    from sheets import TIER_COLOURS, BG, font, crop
    title, small, tiny = font(26), font(15), font(12)
    cell_w, cell_h, per_row = 400, 215, 4
    rows = []
    for tier in TIERS:
        group = [e for e in catalog if e["tier"] == tier]
        for i in range(0, len(group), per_row):
            rows.append((tier, group[i:i + per_row], i == 0))
    width = per_row * cell_w + 40
    height = 20 + sum(cell_h + 30 + (38 if first else 0) for _, _, first in rows) + 20
    out = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(out)
    y = 20
    for tier, group, first in rows:
        if first:
            draw.text((20, y), tier.upper(), font=title, fill=TIER_COLOURS[tier])
            draw.line((20, y + 32, width - 20, y + 32), fill=TIER_COLOURS[tier], width=2)
            y += 38
        for n, entry in enumerate(group):
            x = 20 + n * cell_w
            for k, prefix in enumerate(("", "raw_")):
                img = crop(Image.open(os.path.join(OUT, "renders", prefix + entry["id"] + ".png")).convert("RGBA"))
                scale = min(190 / img.width, 175 / img.height, 1.6)
                img = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.LANCZOS)
                out.paste(img, (x + k * 200 + (200 - img.width) // 2, y + (cell_h - 20 - img.height) // 2), img)
            name = entry["name"]
            w = draw.textlength(name, font=small)
            draw.text((x + (cell_w - w) / 2, y + cell_h - 6), name, font=small, fill=(225, 228, 235))
            for k, caption in enumerate(("cooked", "raw")):
                w = draw.textlength(caption, font=tiny)
                draw.text((x + k * 200 + (200 - w) / 2, y + cell_h + 14), caption, font=tiny, fill=(120, 124, 134))
        y += cell_h + 30
    out.save(os.path.join(HERE, "dish_sheet.png"))


def main():
    catalog = json.load(open(os.path.join(HERE, "catalog.json")))["dishes"]
    wanted = sys.argv[1:]
    for entry in catalog:
        if wanted and entry["id"] not in wanted:
            continue
        print(entry["id"], make(entry), "boxes/atlas", flush=True)
    if not wanted:
        sheet(catalog)


if __name__ == "__main__":
    main()
