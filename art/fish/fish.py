"""
Builds a 3D item model for every fish in catalog.json in a running Blockbench (MCP plugin), paints it at one texel
per unit, renders it, and writes (all under this folder):

  bbmodel/<id>.bbmodel   models/<id>.json (rainsfishing:item/fish/<id>)   textures/<id>.png   renders/<id>.png
  fish_sheet.png         every fish by tier

    python3 fish.py [id ...]      (no ids: every fish, then the sheet)

Shapes live in fish_shapes.py as character grids; each cell becomes a voxel box (equal neighbours merged).
"""
import colorsys
import json
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "..", "dnd", "dnd-module", "art", "items"))
sys.path.insert(0, os.path.join(HERE, "..", "lootbox"))
import build  # noqa: E402
import paint  # noqa: E402
from models import box  # noqa: E402
import lootbox  # noqa: E402  (camera shots)
import fish_shapes  # noqa: E402

paint.DENSITY = 1
NS = "rainsfishing"
TIERS = ["common", "uncommon", "rare", "epic", "legendary", "mythic"]

# char -> (material, half thickness: number | "b" body | "e" body + 0.5)
CELLS = {
    "B": ("body", "b"), "W": ("belly", "b"), "A": ("accent", "b"), "K": ("dark", "b"),
    "E": ("pupil", "e"), "R": ("white", "e"), "L": ("glow", 1.5), "Z": ("glow", "b"),
    "F": ("fin", 0.5), "G": ("fin", 1.0), "S": ("accent", 1.0), "Y": ("gold", 1.0),
}

DISPLAY = {
    "gui": {"rotation": [18, -28, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
FLAT_DISPLAY = dict(DISPLAY, gui={"rotation": [58, -22, 0], "translation": [0, -1, 0], "scale": [0.9, 0.9, 0.9]},
                    ground={"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
                    fixed={"rotation": [90, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]})


# ------------------------------------------------------------------ painting

_original_paint_face = paint.paint_face


def _paint_face(pixels, x0, y0, face, rng, colours=None, frame=0):
    spec = paint.MATERIALS[face.material]
    if spec["kind"] not in ("scales", "finray"):
        return _original_paint_face(pixels, x0, y0, face, rng, colours, frame)
    tones = paint.ramp(spec["colour"])
    light = {"up": 1.0, "north": 0.25, "west": 0.4, "south": 0.1, "east": -0.15, "down": -0.6}[face.side]
    for y in range(face.h):
        for x in range(face.w):
            t = 3 + light * 1.2 + rng.uniform(-0.25, 0.25)
            if spec["kind"] == "scales":
                t += 0.45 if (x + y) % 2 == 0 else -0.3
            else:  # fin rays
                t += 0.5 if x % 2 == 0 else -0.35
                t += 0.5 if y == 0 else 0
            if x == 0 or y == 0:
                t += 0.5
            if x == face.w - 1 or y == face.h - 1:
                t -= 0.7
            pixels[x0 + x, y0 + y] = tones[max(0, min(len(tones) - 1, int(round(t))))] + (255,)


paint._paint_face_patched = True
paint.paint_face = _paint_face


def shade(hexcolour, factor):
    r, g, b = (c / 255 for c in paint.hexrgb(hexcolour))
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    r, g, b = colorsys.hls_to_rgb(h, max(0, min(1, l * factor)), s)
    return "#%02x%02x%02x" % (int(r * 255), int(g * 255), int(b * 255))


def lift(hexcolour, amount):
    r, g, b = paint.hexrgb(hexcolour)
    return "#%02x%02x%02x" % tuple(int(c + (255 - c) * amount) for c in (r, g, b))


def register(colours, glow):
    body, belly, fin, accent = colours
    m = paint.MATERIALS
    m["fish_body"] = {"colour": body, "kind": "scales"}
    m["fish_belly"] = {"colour": belly, "kind": "scales"}
    m["fish_fin"] = {"colour": fin, "kind": "finray"}
    m["fish_accent"] = {"colour": accent, "kind": "scales"}
    m["fish_dark"] = {"colour": shade(fin, 0.55), "kind": "speckle"}
    m["fish_pupil"] = {"colour": "#101218", "kind": "smooth"}
    m["fish_white"] = {"colour": "#f1f1e8", "kind": "smooth"}
    m["fish_glow"] = {"colour": lift(accent, 0.18) if glow else accent, "kind": "speckle"}
    m["fish_gold"] = {"colour": "#e8b830", "kind": "scales"}


MAT = {"body": "fish_body", "belly": "fish_belly", "fin": "fish_fin", "accent": "fish_accent", "dark": "fish_dark",
       "pupil": "fish_pupil", "white": "fish_white", "glow": "fish_glow", "gold": "fish_gold"}


# ------------------------------------------------------------------ grid -> boxes

def half_of(spec, body_half):
    if spec == "b":
        return body_half
    if spec == "e":
        return body_half + 0.5
    return spec


def parts_of(shape):
    grid, depth, plane = shape["grid"], shape["depth"], shape["plane"]
    height, width = len(grid), len(grid[0])
    x0 = (16 - width) // 2
    ytop = 8 + height // 2 + shape.get("dy", 0)
    z0 = (16 - height) // 2

    def body_half(c):
        d = depth[c] if isinstance(depth, list) else depth
        return d / 2

    cells = {}
    for r in range(height):
        for c in range(width):
            ch = grid[r][c]
            if ch == ".":
                continue
            material, spec = CELLS[ch]
            cells.setdefault((ch, half_of(spec, body_half(c))), set()).add((r, c))
    parts = []
    for (ch, half), where in sorted(cells.items(), key=lambda kv: kv[0][0]):
        # horizontal runs, then equal runs stacked
        runs = {}
        for r in sorted({r for r, _ in where}):
            cols = sorted(c for rr, c in where if rr == r)
            start = prev = cols[0]
            for c in cols[1:] + [None]:
                if c is None or c != prev + 1:
                    runs.setdefault((start, prev), []).append(r)
                    start = c
                prev = c
        rects = []
        for (c0, c1), rows in runs.items():
            rows.sort()
            start = prev = rows[0]
            for r in rows[1:] + [None]:
                if r is None or r != prev + 1:
                    rects.append((start, prev, c0, c1))
                    start = r
                prev = r
        material = MAT[CELLS[ch][0]]
        for n, (r0, r1, c0, c1) in enumerate(sorted(rects)):
            name = "%s_%d_%d" % (ch, r0, c0)
            if plane == "xy":
                a = (x0 + c0, ytop - (r1 + 1), 8 - half)
                b = (x0 + c1 + 1, ytop - r0, 8 + half)
                parts.append(box(name, a, b, material))
            else:
                a = (x0 + c0, 8 - half, z0 + r0)
                b = (x0 + c1 + 1, 8 + half, z0 + r1 + 1)
                sides = {"down": MAT["belly"]} if ch in "BA" else None
                parts.append(box(name, a, b, material, sides))
    return parts


# ------------------------------------------------------------------ Blockbench

def write_model(fid, model):
    texture = NS + ":item/fish/" + fid
    out = dict(model)
    out["textures"] = {"0": texture, "particle": texture}
    for element in out.get("elements", []):
        for face in element.get("faces", {}).values():
            face["texture"] = "#0"
    os.makedirs(os.path.join(HERE, "models"), exist_ok=True)
    with open(os.path.join(HERE, "models", fid + ".json"), "w") as handle:
        json.dump(out, handle, indent=1)


def make(entry, shapes):
    fid = entry["id"]
    shape = shapes[fid]
    register(entry["colours"], bool(entry.get("glow")))
    parts = parts_of(shape)
    faces = build.faces_of(parts)
    image, spots, size = paint.paint(faces)
    elements, index = [], 0
    for part in parts:
        uv = {}
        for side in build.SIDES:
            face = faces[index]
            x, y = spots[index]
            uv[side] = [x, y, x + face.w, y + face.h]
            index += 1
        elements.append({"name": part["name"], "from": part["from"], "to": part["to"], "uv": uv,
                         "rotation": [0, 0, 0], "origin": [8, 8, 8]})
    flat = shape["plane"] == "xz"
    built = build.eval_js(build.BUILD.replace("SPEC", json.dumps({
        "name": fid, "size": size, "texture": build.data_url(image), "elements": elements,
        "display": FLAT_DISPLAY if flat else DISPLAY})))
    for folder in ("bbmodel", "textures", "renders"):
        os.makedirs(os.path.join(HERE, folder), exist_ok=True)
    with open(os.path.join(HERE, "bbmodel", fid + ".bbmodel"), "w") as handle:
        handle.write(built["project"])
    image.save(os.path.join(HERE, "textures", fid + ".png"))
    write_model(fid, built["model"])
    build.eval_js("(() => { Modes.options.edit.select(); return 1; })()")
    time.sleep(0.8)  # the texture loads asynchronously
    cam = ((-6, 19, 24), (0, 8, 0)) if flat else ((-10, 14, 27), (0, 8, 0))
    lootbox.shot(os.path.join(HERE, "renders", fid + ".png"), cam[0], cam[1], fov=36, size=420)
    return len(parts)


def main():
    catalog = json.load(open(os.path.join(HERE, "catalog.json")))["fish"]
    shapes = fish_shapes.build_all()
    wanted = sys.argv[1:]
    for entry in catalog:
        if wanted and entry["id"] not in wanted:
            continue
        print(entry["id"], make(entry, shapes), "boxes", flush=True)
    if not wanted:
        import sheets
        sheets.fish_sheet(catalog)


if __name__ == "__main__":
    main()
