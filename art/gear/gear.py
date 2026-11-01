"""
Fishing gear as voxel item models: the five baits and the fishing net. Each is a handful of boxes, every material a
small shaded swatch in one atlas per item (a net's mesh is a cut-out grid). `--render` photographs them in the running
Blockbench (the shared client in ~/.claude/tools/bbmcp) and writes sheet.png.

    python3 gear.py [--render]
"""
import base64
import io
import json
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SW = 8  # swatch size in pixels; an atlas is 8 swatches across


def shade(rgb, f):
    return tuple(max(0, min(255, int(c * f))) for c in rgb)


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def swatch(kind, colour, seed):
    rng = random.Random(seed)
    base = hexrgb(colour)
    img = Image.new("RGBA", (SW, SW), (0, 0, 0, 0))
    px = img.load()
    for y in range(SW):
        for x in range(SW):
            f = 1.0 + rng.uniform(-0.07, 0.07)
            if kind == "mesh":
                if x % 2 == 0 or y % 2 == 0:
                    px[x, y] = shade(base, f - (0.15 if (x + y) % 4 == 0 else 0)) + (255,)
                continue
            if kind == "tint":
                px[x, y] = shade(base, f) + (255,)
                continue
            if kind == "metal":
                f += 0.18 if (x + y) % 7 == 0 else 0
            elif kind == "glow":
                f = 1.05 + rng.uniform(-0.03, 0.08)
            elif kind == "wood":
                f += 0.12 if y % 3 == 0 else -0.04
            elif kind == "rope":
                f += 0.15 if (x + y) % 3 == 0 else -0.08
            elif kind == "glass":
                # like vanilla glass: a clear pane in a light frame, one glint — fully clear, never half see-through
                edge = x == 0 or y == 0 or x == SW - 1 or y == SW - 1
                glint = (x == 2 and 2 <= y <= 4) or (x == 3 and y == 2)
                if edge or glint:
                    px[x, y] = shade(base, f + (0.2 if glint else 0)) + (255,)
                continue
            px[x, y] = shade(base, f) + (255,)
    return img


def model(parts, materials, display):
    """parts: (name, from, to, material); materials: name -> (kind, colour)."""
    names = list(materials)
    cols = 8
    rows = (len(names) + cols - 1) // cols
    atlas = Image.new("RGBA", (cols * SW, max(1, rows) * SW), (0, 0, 0, 0))
    where = {}
    for n, name in enumerate(names):
        kind, colour = materials[name]
        x, y = (n % cols) * SW, (n // cols) * SW
        atlas.paste(swatch(kind, colour, n * 7 + 3), (x, y))
        where[name] = (x, y)
    size = max(atlas.width, atlas.height)
    square = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    square.paste(atlas, (0, 0))
    scale = 16.0 / size
    elements = []
    for name, start, end, mat in parts:
        x, y = where[mat]
        uv = [round(x * scale + 0.1, 3), round(y * scale + 0.1, 3), round((x + SW) * scale - 0.1, 3),
              round((y + SW) * scale - 0.1, 3)]
        faces = {side: {"uv": uv, "texture": "#0"} for side in ("north", "south", "east", "west", "up", "down")}
        elements.append({"name": name, "from": [round(v, 3) for v in start], "to": [round(v, 3) for v in end],
                         "faces": faces})
    return square, {"texture_size": [size, size], "elements": elements, "display": display}


DISPLAY = {
    "gui": {"rotation": [25, -35, 0], "scale": [1.0, 1.0, 1.0]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "scale": [0.9, 0.9, 0.9]},
    "head": {"translation": [0, 13, 0]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [1, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [1, 2, 0], "scale": [0.5, 0.5, 0.5]},
}


def can_with_worms():
    m = {"tin": ("metal", "#9aa3ad"), "tin_dark": ("metal", "#6b737c"), "rim": ("metal", "#c4cbd3"),
         "soil": ("wood", "#5a3e26"), "worm": ("glow", "#e88a9a"), "worm_dark": ("glow", "#c86a7c"),
         "label": ("rope", "#d8b04a")}
    p = [("can_n", (4, 0, 4), (12, 7, 5), "tin"), ("can_s", (4, 0, 11), (12, 7, 12), "tin"),
         ("can_w", (4, 0, 5), (5, 7, 11), "tin_dark"), ("can_e", (11, 0, 5), (12, 7, 11), "tin_dark"),
         ("bottom", (5, 0, 5), (11, 1, 11), "tin_dark"), ("soil", (5, 1, 5), (11, 6, 11), "soil"),
         ("rim_n", (3.6, 7, 3.6), (12.4, 7.6, 4.6), "rim"), ("rim_s", (3.6, 7, 11.4), (12.4, 7.6, 12.4), "rim"),
         ("rim_w", (3.6, 7, 4.6), (4.6, 7.6, 11.4), "rim"), ("rim_e", (11.4, 7, 4.6), (12.4, 7.6, 11.4), "rim"),
         ("label", (4, 2, 3.8), (12, 5, 4), "label"),
         ("worm1a", (6, 6, 6), (7, 9, 7), "worm"), ("worm1b", (6, 9, 6), (8, 10, 7), "worm"),
         ("worm1c", (7.5, 8, 6), (8.5, 10, 7), "worm_dark"),
         ("worm2a", (9, 6, 8), (10, 10, 9), "worm_dark"), ("worm2b", (9, 10, 8), (10, 11, 10), "worm"),
         ("worm3a", (7, 6, 9), (8, 8, 10), "worm"), ("worm3b", (5.5, 7.5, 9), (7, 8.5, 10), "worm")]
    return p, m


def jar(glass, liquid, cork, blobs):
    m = {"glass": ("glass", glass), "liquid": ("tint", liquid), "cork": ("wood", cork), "blob": ("glow", blobs),
         "base": ("glass", glass)}
    p = [("liquid", (5, 1, 5), (11, 5, 11), "liquid"),
         ("glass_n", (4.5, 0, 4.5), (11.5, 10, 5), "glass"), ("glass_s", (4.5, 0, 11), (11.5, 10, 11.5), "glass"),
         ("glass_w", (4.5, 0, 5), (5, 10, 11), "glass"), ("glass_e", (11, 0, 5), (11.5, 10, 11), "glass"),
         ("bottom", (5, 0, 5), (11, 1, 11), "base"),
         ("neck", (6, 10, 6), (10, 11, 10), "glass"), ("cork", (6.3, 11, 6.3), (9.7, 13, 9.7), "cork"),
         ("blob1", (5.2, 5, 5.2), (7.6, 7.4, 7.6), "blob"), ("blob2", (8.4, 5.4, 5.4), (10.6, 7.6, 7.6), "blob"),
         ("blob3", (5.6, 7.4, 8.4), (7.8, 9.6, 10.6), "blob"), ("blob4", (8.6, 2, 8.6), (10.6, 4, 10.6), "blob"),
         ("blob5", (8.2, 8, 8.2), (9.8, 9.6, 9.8), "blob")]
    return p, m


def spinner():
    m = {"gold": ("metal", "#f0c650"), "gold_dark": ("metal", "#c8962a"), "bead": ("glow", "#e83a4a"),
         "wire": ("metal", "#c4cbd3"), "hook": ("metal", "#9aa3ad"), "feather": ("rope", "#f6e9b8")}
    p = [("ring", (7.5, 14, 7.5), (8.5, 15.5, 8.5), "wire"), ("wire", (7.75, 11, 7.75), (8.25, 14, 8.25), "wire"),
         ("bead", (7.2, 10, 7.2), (8.8, 11.4, 8.8), "bead"),
         ("blade", (6, 6, 7.6), (10, 10, 8.4), "gold"), ("blade_tip", (7, 5, 7.6), (9, 6, 8.4), "gold_dark"),
         ("body", (7.2, 3, 7.2), (8.8, 6, 8.8), "gold_dark"),
         ("feather1", (6.6, 1, 7.4), (7.6, 3, 8.6), "feather"), ("feather2", (8.4, 1, 7.4), (9.4, 3, 8.6), "feather"),
         ("hook_a", (7.75, 0, 7.75), (8.25, 3, 8.25), "hook"), ("hook_b", (8.25, 0, 7.75), (10, 0.5, 8.25), "hook"),
         ("hook_c", (9.5, 0.5, 7.75), (10, 2, 8.25), "hook")]
    return p, m


def abyss_lure():
    m = {"ink": ("glass", "#1a1f3a"), "ink_core": ("glow", "#121630"), "light": ("glow", "#e8ffb0"),
         "spot": ("glow", "#3ad8c8"), "stalk": ("metal", "#2a3a60"), "hook": ("metal", "#9aa3ad")}
    p = [("ball", (5, 2, 5), (11, 8, 11), "ink_core"), ("ball_top", (6, 8, 6), (10, 9, 10), "ink_core"),
         ("ball_bot", (6, 1, 6), (10, 2, 10), "ink_core"),
         ("spot1", (4.8, 5, 6.5), (5, 6, 7.5), "spot"), ("spot2", (11, 3.5, 8), (11.2, 4.5, 9), "spot"),
         ("spot3", (7, 6.5, 4.8), (8, 7.5, 5), "spot"), ("spot4", (8.5, 3, 11), (9.5, 4, 11.2), "spot"),
         ("stalk", (7.75, 9, 7.75), (8.25, 13, 8.25), "stalk"), ("stalk_b", (8.25, 12.5, 7.75), (10.5, 13, 8.25), "stalk"),
         ("light", (9.8, 10.6, 7.2), (11.4, 12.2, 8.8), "light"),
         ("hook_a", (7.75, -1, 7.75), (8.25, 1, 8.25), "hook"), ("hook_b", (8.25, -1, 7.75), (10, -0.5, 8.25), "hook")]
    return p, m


def starlure():
    m = {"star": ("glow", "#ffe88a"), "star_core": ("glow", "#fff6c0"), "chain": ("metal", "#c8d0ff"),
         "hook": ("metal", "#9aa3ad"), "spark": ("glow", "#8aa8ff")}
    p = [("core", (6.5, 5, 7.25), (9.5, 8, 8.75), "star_core"),
         ("arm_u", (7.25, 8, 7.4), (8.75, 10.5, 8.6), "star"), ("arm_d", (7.25, 2.5, 7.4), (8.75, 5, 8.6), "star"),
         ("arm_l", (4, 5.75, 7.4), (6.5, 7.25, 8.6), "star"), ("arm_r", (9.5, 5.75, 7.4), (12, 7.25, 8.6), "star"),
         ("chain1", (7.6, 10.5, 7.6), (8.4, 11.5, 8.4), "chain"), ("chain2", (7.75, 11.5, 7.75), (8.25, 13, 8.25), "chain"),
         ("chain3", (7.5, 13, 7.5), (8.5, 14, 8.5), "chain"),
         ("hook_a", (7.75, 0.5, 7.75), (8.25, 2.5, 8.25), "hook"), ("hook_b", (8.25, 0.5, 7.75), (9.8, 1, 8.25), "hook"),
         ("spark1", (4.5, 9, 7.75), (5.3, 9.8, 8.25), "spark"), ("spark2", (10.8, 3, 7.75), (11.6, 3.8, 8.25), "spark")]
    return p, m


def net():
    m = {"mesh": ("mesh", "#d8c49a"), "rope": ("rope", "#c9a46a"), "knot": ("rope", "#a8844a"),
         "fish": ("metal", "#9fc4dc"), "fish_b": ("metal", "#e8a060"), "fin": ("glow", "#5a88a8")}
    # a sagging bag: narrow at the bottom, full in the middle, drawn tight at the neck
    layers = ((0, 1.5, 5.5, 10.5), (1.5, 3.5, 4.5, 11.5), (3.5, 8, 4, 12), (8, 10, 4.5, 11.5), (10, 11, 6, 10))
    p = []
    for n, (y0, y1, lo, hi) in enumerate(layers):
        p += [("mesh%d_n" % n, (lo, y0, lo), (hi, y1, lo + 0.3), "mesh"),
              ("mesh%d_s" % n, (lo, y0, hi - 0.3), (hi, y1, hi), "mesh"),
              ("mesh%d_w" % n, (lo, y0, lo + 0.3), (lo + 0.3, y1, hi - 0.3), "mesh"),
              ("mesh%d_e" % n, (hi - 0.3, y0, lo + 0.3), (hi, y1, hi - 0.3), "mesh")]
    p += [("mesh_b", (5.5, 0, 5.5), (10.5, 0.3, 10.5), "mesh"),
          ("neck", (6.6, 11, 6.6), (9.4, 12, 9.4), "rope"), ("tie", (6.2, 11.3, 6.2), (9.8, 11.8, 9.8), "knot"),
          ("loop_l", (7, 12, 7.6), (7.6, 15, 8.4), "rope"), ("loop_r", (8.4, 12, 7.6), (9, 15, 8.4), "rope"),
          ("loop_top", (7, 15, 7.6), (9, 15.6, 8.4), "rope"),
          ("fish1", (5, 3, 6), (11, 5, 8), "fish"), ("fish1_tail", (10.5, 4.5, 6.5), (12.2, 6.5, 7.5), "fin"),
          ("fish2", (5.5, 5.5, 8), (10.5, 7.3, 10), "fish_b"), ("fish2_tail", (3.8, 6.3, 8.5), (5.5, 8.3, 9.5), "fin"),
          ("fish3", (6.5, 8, 6.5), (9.5, 10.5, 8.5), "fish"), ("fish3_tail", (7.5, 10.5, 7), (8.5, 12.8, 8), "fin")]
    return p, m


ITEMS = {"bait/wriggler": can_with_worms, "bait/shimmer": lambda: jar("#cfe8ff", "#3a7aa8", "#8a6a44", "#a8fbff"),
         "bait/gilded": spinner, "bait/abyss": abyss_lure, "bait/starlure": starlure, "net": net}


def write():
    out = {}
    for key, make in ITEMS.items():
        parts, materials = make()
        atlas, built = model(parts, materials, DISPLAY)
        name = key.replace("/", "_")
        os.makedirs(os.path.join(HERE, "textures"), exist_ok=True)
        os.makedirs(os.path.join(HERE, "models"), exist_ok=True)
        atlas.save(os.path.join(HERE, "textures", name + ".png"))
        built["textures"] = {"0": "rainsfishing:item/gear/" + name, "particle": "rainsfishing:item/gear/" + name}
        with open(os.path.join(HERE, "models", name + ".json"), "w") as handle:
            json.dump(built, handle, indent=1)
        out[key] = (atlas, built)
    return out


def render(built):
    sys.path.insert(0, os.path.expanduser("~/.claude/tools/bbmcp"))
    import bb
    shots = []
    for key, (atlas, model_json) in built.items():
        buffer = io.BytesIO()
        atlas.save(buffer, "PNG")
        # split at every slash: the eval tool refuses code containing a double slash, which base64 can hold
        url = ("data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode()).replace("/", "/'+'")
        spec = dict(model_json)
        bb.eval_js("""(() => {
          newProject(Formats.java_block);
          Codecs.java_block.parse(MODEL, '');
          const tex = new Texture({name: 'gear'}).fromDataURL('PICTURE').add(false);
          Cube.all.forEach(c => { for (const f in c.faces) c.faces[f].texture = tex.uuid; });
          Canvas.updateAll();
          return Cube.all.length;
        })()""".replace("MODEL", json.dumps(spec)).replace("PICTURE", url))
        views = json.dumps(bb.call("tools/call", {"name": "list_views", "arguments": {}}))
        if "gear" not in views:
            bb.call("tools/call", {"name": "create_offscreen_view",
                                   "arguments": {"id": "gear", "width": 360, "height": 360, "antialias": True}})
        path = os.path.join(HERE, "render_" + key.replace("/", "_") + ".png")
        for message in bb.call("tools/call", {"name": "set_camera_angle", "arguments": {
                "view": "gear", "position": [-16, 18, -22], "target": [0, 7, 0], "projection": "perspective",
                "fov": 40}}):
            for part in message.get("result", {}).get("content", []):
                if part.get("type") == "image":
                    with open(path, "wb") as handle:
                        handle.write(base64.b64decode(part["data"]))
        shots.append((key, path))
    sheet = Image.new("RGBA", (360 * len(shots), 360), (30, 32, 40, 255))
    for n, (key, path) in enumerate(shots):
        img = Image.open(path).convert("RGBA")
        sheet.alpha_composite(img, (n * 360, 0))
    sheet.save(os.path.join(HERE, "sheet.png"))


if __name__ == "__main__":
    built = write()
    if "--render" in sys.argv:
        render(built)
    print("written", len(built))
