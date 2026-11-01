"""
The six fishing rods as 3D item models, built in a running Blockbench (MCP plugin) and painted at two texels per unit.

A rod lies on the diagonal of the 16 grid exactly like vanilla's sprite (butt bottom left, tip top right, line down
the right), so minecraft:item/handheld_rod's display transforms hold it the same way. Its parts are long boxes turned
45 degrees about z, one per stretch of the shaft, plus small axis-aligned blocks (reel, flame, fin, hook).
The cast model is the same model without the hanging line and hook ("hang_*" elements).

    python3 rods3d.py [id ...]       (no ids: all six, then the sheet)

Writes models/<id>[_cast].json, textures/<id>[_cast].png, bbmodel/<id>.bbmodel, renders/<id>[_cast|_held].png,
items/<id>.json and ../rods/sheet.png.
"""
import base64
import json
import math
import os
import shutil
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "lootbox"))
sys.path.insert(0, os.path.join(HERE, "..", "fish"))
import rods_paint as rp  # noqa: E402  (patches paint)
import paint  # noqa: E402
import build  # noqa: E402
import lootbox  # noqa: E402  (camera shots)
from models import box  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

NS = "rainsfishing"
SIDES = build.SIDES
D = (math.sqrt(0.5), math.sqrt(0.5))
N = (-D[1], D[0])  # perpendicular, towards the upper left
P0 = (0.5, 0.5)
Z = 8.0
TIP = 19.2  # the shaft ends here (s along the rod), decorations may reach on

TIERS = {"reed": ("Reed Rod", "#bfbab0"), "reef": ("Reef Rod", "#5cdc82"), "pearl": ("Pearl Rod", "#6eafff"),
         "ember": ("Ember Rod", "#be82ff"), "leviathan": ("Leviathan Rod", "#ffbe46"),
         "void": ("Void Rod", "#7ff6ff")}

# vanilla's: handheld_rod for the hands, item/generated for the rest; gui is ours (a turned 3D view)
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 55], "translation": [0, 4, 2.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, -90, -55], "translation": [0, 4, 2.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, 90, 25], "translation": [0, 1.6, 0.8], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, -90, -25], "translation": [0, 1.6, 0.8], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [18, -34, 0], "translation": [0, 0.3, 0], "scale": [1.02, 1.02, 1.02]},
}
VIEW = {"az": 34, "el": 18}


def r3(v):
    return round(v, 3)


# ------------------------------------------------------------------ geometry

def at(s, off=0.0):
    return (P0[0] + D[0] * s + N[0] * off, P0[1] + D[1] * s + N[1] * off)


def seg(name, s0, s1, ty, tz, material, off=0.0, zoff=0.0):
    """A box lying along the rod from s0 to s1, ty across in the plane, tz deep, off to the upper left of the axis."""
    length = s1 - s0
    cx, cy = at((s0 + s1) / 2, off)
    z = Z + zoff
    return box(name, [r3(cx - length / 2), r3(cy - ty / 2), r3(z - tz / 2)],
               [r3(cx + length / 2), r3(cy + ty / 2), r3(z + tz / 2)], material, None,
               {"angle": 45, "axis": "z", "origin": [r3(cx), r3(cy), r3(z)]})


def ab(name, x0, y0, z0, x1, y1, z1, material):
    return box(name, [r3(x0), r3(y0), r3(z0)], [r3(x1), r3(y1), r3(z1)], material)


def disc(name, cx, cy, r, z0, z1, material, k=0.62):
    """A round-ish disc in the x-y plane: a plus of two boxes."""
    return [ab(name, cx - r, cy - r * k, z0, cx + r, cy + r * k, z1, material),
            ab(name + "_b", cx - r * k, cy - r, z0, cx + r * k, cy + r, z1, material)]


def blob(name, cx, cy, cz, r, material, k=0.62):
    """A chunky ball: three boxes crossed."""
    return [ab(name, cx - r, cy - r * k, cz - r * k, cx + r, cy + r * k, cz + r * k, material),
            ab(name + "_b", cx - r * k, cy - r, cz - r * k, cx + r * k, cy + r, cz + r * k, material),
            ab(name + "_c", cx - r * k, cy - r * k, cz - r, cx + r * k, cy + r * k, cz + r, material)]


def thick_at(s):
    return 1.25 if s < 11.2 else (1.0 if s < 15.2 else 0.8)


def rod_parts(rid, o):
    """o: the rod's look (material names, flags); returns the parts of the whole rod."""
    m = lambda key: rid + "_" + key  # noqa: E731
    parts = []
    add = parts.append
    # ---- the butt: a gem
    cx, cy = at(0.3)
    parts += blob("gem", cx, cy, Z, o.get("gem_r", 1.45), m("gem"))
    for extra in o.get("butt", []):
        parts += extra(rid)
    # ---- handle with ferrules
    add(seg("handle", 0.9, 7.0, 1.8, 1.8, m("handle")))
    add(seg("ferrule_butt", 0.8, 1.5, 2.3, 2.3, m("fit")))
    add(seg("ferrule_top", 6.4, 7.2, 2.3, 2.3, m("fit")))
    for i, s in enumerate(o.get("grip_rings", [])):
        add(seg("grip_ring%d" % i, s, s + 0.45, 2.1, 2.1, m("ring")))
    # ---- shaft in three tapering stretches
    add(seg("shaft_a", 7.0, 11.2, 1.25, 1.25, m("shaft")))
    add(seg("shaft_b", 11.2, 15.2, 1.0, 1.0, m("shaft")))
    add(seg("shaft_c", 15.2, TIP, 0.8, 0.8, m("shaft")))
    for i, s in enumerate(o["collars"]):
        t = thick_at(s) + o.get("collar_extra", 0.5)
        add(seg("collar%d" % i, s - 0.3, s + 0.3, t, t, m(o.get("collar_mat", "fit"))))
    for i, s in enumerate(o.get("bands", [])):
        t = thick_at(s) + 0.3
        add(seg("band%d" % i, s, s + o.get("band_len", 1.2), t, t, m("band")))
    # ---- guides (a band round the shaft and an eyelet below it) and the line along the rod
    for i, s in enumerate((10.0, 13.2, 16.4, 18.8)):
        t = thick_at(s)
        add(seg("guide%d" % i, s - 0.3, s + 0.3, t + 0.45, t + 0.45, m("guide")))
        add(seg("eyelet%d" % i, s - 0.25, s + 0.25, 0.9, 1.2, m("eyelet"), off=-(t / 2 + 0.5)))
    add(seg("line", 8.4, 19.7, 0.45, 0.5, m("line"), off=-1.1))
    # ---- the reel, hanging below the shaft just above the handle
    rcx, rcy = at(8.2, -2.8)
    add(seg("reel_foot", 7.7, 8.6, 2.0, 1.2, m("fit"), off=-1.3))
    rr = o.get("reel_r", 1.35)
    parts += disc("spool", rcx, rcy, rr, Z - 0.9, Z + 1.2, m("spool"))
    fr = rr + 0.3
    parts += disc("flange_b", rcx, rcy, fr, Z - 1.3, Z - 0.9, m("reel"))
    add(ab("hub", rcx - 0.4, rcy - 0.4, Z + 1.2, rcx + 0.4, rcy + 0.4, Z + 1.5, m("hub")))
    add(ab("hub_b", rcx - 0.3, rcy - 0.3, Z - 1.55, rcx + 0.3, rcy + 0.3, Z - 1.3, m("hub")))
    add(ab("crank", rcx, rcy - 0.2, Z + 1.5, rcx + 1.3, rcy + 0.2, Z + 1.9, m("reel")))
    add(ab("knob", rcx + 1.05, rcy - 0.3, Z + 1.9, rcx + 1.6, rcy + 0.3, Z + 2.5, m("knob")))
    # ---- tip and the rod's own ornaments
    for extra in o.get("extras", []):
        parts += extra(rid)
    # ---- the hanging line and the hook (not in the cast look)
    lx, ly = at(19.7, -1.1)
    add(ab("hang_line", lx - 0.25, 4.3, Z - 0.25, lx + 0.25, ly, Z + 0.25, m("line")))
    add(ab("hang_hook_bar", lx - 1.7, 3.7, Z - 0.25, lx + 0.25, 4.3, Z + 0.25, "hook"))
    add(ab("hang_hook_barb", lx - 1.7, 4.3, Z - 0.25, lx - 1.2, 5.3, Z + 0.25, "hook"))
    return parts


# ------------------------------------------------------------------ the six looks

def M(rid, key, colour, kind, **opts):
    rp.mat(rid + "_" + key, colour, kind, **opts)


def tip_cap(colour_key, length=1.1, thick=0.8, s0=TIP):
    def make(rid):
        return [seg("tip", s0, s0 + length, thick, thick, rid + "_" + colour_key)]
    return make


def reed():
    M("reed", "shaft", "#cdb560", "bamboo", colour2="#a8924a")
    M("reed", "node", "#8f7a3a", "bamboo")
    M("reed", "handle", "#a3743f", "speckle")
    M("reed", "fit", "#8c949e", "metal")
    M("reed", "ring", "#d8c49a", "cloth")
    M("reed", "guide", "#6e757d", "metal")
    M("reed", "eyelet", "#aab2bb", "metal")
    M("reed", "line", "#f2f2f2", "cloth")
    M("reed", "spool", "#f2f2f2", "wrap")
    M("reed", "reel", "#8c949e", "metal")
    M("reed", "hub", "#5a6068", "metal")
    M("reed", "knob", "#6b4a2a", "wood")
    M("reed", "gem", "#a9a7a2", "gem")
    M("reed", "tipcap", "#8f7a3a", "bamboo")
    M("reed", "band", "#8f7a3a", "bamboo")

    def nodes(rid):
        return [seg("node%d" % i, s - 0.3, s + 0.3, thick_at(s) + 0.5, thick_at(s) + 0.5, rid + "_node")
                for i, s in enumerate((9.2, 11.6, 14.0, 16.4, 18.4))]
    return {"collars": [], "grip_rings": [2.4, 3.4, 5.0, 5.8], "gem_r": 1.25, "extras": [nodes, tip_cap("tipcap")]}


def reef():
    M("reef", "shaft", "#46b9a8", "prism", colour2="#8fe8d8")
    M("reef", "handle", "#2d6a5e", "wrap")
    M("reef", "fit", "#c87a3a", "verdigris", colour2="#4fb08c", seed=3)
    M("reef", "ring", "#c87a3a", "verdigris", colour2="#4fb08c", seed=5)
    M("reef", "guide", "#c87a3a", "verdigris", colour2="#4fb08c", seed=9)
    M("reef", "eyelet", "#e0925a", "metal")
    M("reef", "line", "#f2f2f2", "cloth")
    M("reef", "spool", "#f2f2f2", "wrap")
    M("reef", "reel", "#c87a3a", "verdigris", colour2="#4fb08c", seed=7)
    M("reef", "hub", "#4fb08c", "metal")
    M("reef", "knob", "#e8587a", "speckle")
    M("reef", "gem", "#2fd062", "gem")
    M("reef", "coral", "#e8587a", "speckle")
    M("reef", "coral_b", "#f08a50", "speckle")
    M("reef", "coral_c", "#b46ad8", "speckle")
    M("reef", "band", "#3f8a6c", "wrap")
    M("reef", "tipcap", "#c87a3a", "verdigris", colour2="#4fb08c", seed=11)

    def coral(rid):
        out = []
        # a cluster growing from the shaft's upper side above the reel, and a sprig on the handle
        for i, (s0, s1, top, tz, c) in enumerate(((8.4, 9.2, 2.0, 0.8, "coral"), (9.2, 10.0, 3.0, 0.9, "coral_b"),
                                                  (10.0, 10.8, 2.3, 0.8, "coral"), (10.8, 11.4, 1.7, 0.7, "coral_c"))):
            lo = thick_at(s0) / 2 - 0.1
            out.append(seg("coral%d" % i, s0, s1, top - lo, tz, rid + "_" + c, off=(top + lo) / 2))
        for i, (s0, s1, top, tz, c) in enumerate(((3.6, 4.4, 2.2, 0.8, "coral_b"), (4.4, 5.2, 3.0, 0.9, "coral"),
                                                  (5.2, 5.9, 2.0, 0.8, "coral_c"))):
            out.append(seg("sprig%d" % i, s0, s1, top - 0.9, tz, rid + "_" + c, off=(top + 0.9) / 2))
        return out
    return {"collars": [11.2, 15.2], "gem_r": 1.4, "butt": [], "extras": [coral, tip_cap("tipcap")],
            "bands": [7.5]} | {"band_len": 0.8}


def pearl():
    M("pearl", "shaft", "#eef2f8", "pearly", colour2="#c9d8f5", seed=2)
    M("pearl", "handle", "#2f4f9a", "wrap")
    M("pearl", "fit", "#c3ccd6", "metal")
    M("pearl", "ring", "#dfe9ff", "cloth")
    M("pearl", "band", "#2f4f9a", "wrap")
    M("pearl", "guide", "#c3ccd6", "metal")
    M("pearl", "eyelet", "#e6edf5", "metal")
    M("pearl", "line", "#dfe9ff", "cloth")
    M("pearl", "spool", "#8fb0ff", "wrap")
    M("pearl", "reel", "#c3ccd6", "metal")
    M("pearl", "hub", "#6eafff", "gem")
    M("pearl", "knob", "#6eafff", "gem")
    M("pearl", "gem", "#f2f6ff", "gem")
    M("pearl", "bead", "#f2f6ff", "gem")
    M("pearl", "tipcap", "#c3ccd6", "metal")

    def bead(rid):
        cx, cy = at(20.3)
        return [seg("tip", TIP, TIP + 0.9, 0.9, 0.9, rid + "_tipcap")] + blob("tip_pearl", cx, cy, Z, 0.8, rid + "_bead")
    return {"collars": [7.7, 11.2, 15.2], "bands": [8.3, 12.2], "band_len": 1.0, "gem_r": 1.85, "extras": [bead],
            "grip_rings": [2.5, 4.6]}


def ember():
    M("ember", "shaft", "#f0a030", "blaze", colour2="#ffe070")
    M("ember", "handle", "#5a2630", "brick")
    M("ember", "fit", "#3a343c", "metal")
    M("ember", "ring", "#3a343c", "metal")
    M("ember", "guide", "#3a343c", "metal")
    M("ember", "eyelet", "#ff7a1a", "glow")
    M("ember", "line", "#ffcf90", "cloth")
    M("ember", "spool", "#ff5a10", "magma", colour2="#2a0e0a", seed=4)
    M("ember", "reel", "#3a343c", "metal")
    M("ember", "hub", "#ffd060", "glow")
    M("ember", "knob", "#ff7a1a", "glow")
    M("ember", "gem", "#be82ff", "gem")
    M("ember", "flame", "#ff6a14", "flame")
    M("ember", "flame_in", "#ffd24a", "flame")
    M("ember", "tipcap", "#3a343c", "metal")

    def flame(rid):
        tx, ty = at(TIP + 0.7)
        return [seg("tip", TIP, TIP + 0.9, 1.2, 1.2, rid + "_tipcap"),
                ab("flame", tx - 1.0, ty - 0.2, Z - 0.9, tx + 1.0, ty + 1.8, Z + 0.9, rid + "_flame"),
                ab("flame_in", tx - 0.5, ty + 0.4, Z - 0.5, tx + 0.5, ty + 2.2, Z + 0.5, rid + "_flame_in"),
                ab("flame_top", tx - 0.3, ty + 1.8, Z - 0.3, tx + 0.3, ty + 3.0, Z + 0.3, rid + "_flame"),
                ab("flame_l", tx - 1.7, ty + 0.4, Z - 0.5, tx - 1.0, ty + 1.7, Z + 0.5, rid + "_flame"),
                ab("flame_r", tx + 1.0, ty + 0.2, Z - 0.5, tx + 1.6, ty + 1.2, Z + 0.5, rid + "_flame_in")]
    return {"collars": [7.7, 11.2, 15.2], "collar_extra": 0.6, "gem_r": 1.45, "extras": [flame],
            "grip_rings": [3.0, 4.4]}


def leviathan():
    M("leviathan", "shaft", "#e2b13c", "metal")
    M("leviathan", "handle", "#8a3a22", "scale", colour2="#b0522e", seed=1)
    M("leviathan", "fit", "#f0c650", "metal")
    M("leviathan", "ring", "#f0c650", "metal")
    M("leviathan", "band", "#6c2b1a", "wrap")
    M("leviathan", "guide", "#c8962a", "metal")
    M("leviathan", "eyelet", "#f0c650", "metal")
    M("leviathan", "line", "#fff0c0", "cloth")
    M("leviathan", "spool", "#ff8a1a", "wrap")
    M("leviathan", "reel", "#f0c650", "metal")
    M("leviathan", "hub", "#ff8a1a", "gem")
    M("leviathan", "knob", "#ff8a1a", "gem")
    M("leviathan", "gem", "#ff8a1a", "gem")
    M("leviathan", "fin", "#e2b13c", "scale", colour2="#ff9a2a", seed=6)
    M("leviathan", "fin_edge", "#c8962a", "metal")
    M("leviathan", "tipcap", "#f0c650", "metal")

    def fin(rid):
        out = []
        steps = ((7.4, 8.4, 1.9), (8.4, 9.4, 2.7), (9.4, 10.4, 3.4), (10.4, 11.4, 3.0), (11.4, 12.4, 2.3),
                 (12.4, 13.4, 1.6))
        for i, (s0, s1, top) in enumerate(steps):
            lo = thick_at(s0) / 2 - 0.1
            out.append(seg("fin%d" % i, s0, s1, top - lo, 0.9, rid + "_fin", off=(top + lo) / 2))
        return out

    def spike(rid):
        tx, ty = at(TIP + 0.9)
        return [seg("tip", TIP, TIP + 1.5, 0.9, 0.9, rid + "_tipcap")] + blob("tip_gem", tx, ty, Z, 0.7, rid + "_gem")
    return {"collars": [11.2, 15.2], "bands": [7.9, 12.0, 16.0], "band_len": 1.6, "reel_r": 1.65, "gem_r": 1.6,
            "extras": [fin, spike], "grip_rings": [3.0]}


def void():
    M("void", "shaft", "#241a44", "stars", colour2="#4a3a86")
    M("void", "handle", "#1a1230", "wrap")
    M("void", "fit", "#2a2050", "metal")
    M("void", "ring", "#7ff6ff", "glow")
    M("void", "band", "#7ff6ff", "glow")
    M("void", "guide", "#2a2050", "metal")
    M("void", "eyelet", "#7ff6ff", "glow")
    M("void", "line", "#7ff6ff", "glow")
    M("void", "spool", "#7ff6ff", "glow")
    M("void", "reel", "#2a2050", "metal")
    M("void", "hub", "#f2feff", "glow")
    M("void", "knob", "#7ff6ff", "glow")
    M("void", "gem", "#3df2a8", "gem")
    M("void", "pupil", "#0a0614", "stars")
    M("void", "endrod", "#f4ffff", "glow")
    M("void", "tipglow", "#7ff6ff", "glow")
    M("void", "shard", "#7ff6ff", "gem")
    M("void", "shard_dark", "#4a2a9a", "gem")

    def eye(rid):
        cx, cy = at(0.3)
        return [ab("pupil", cx - 0.45, cy - 0.45, Z + 1.0, cx + 0.45, cy + 0.45, Z + 1.35, rid + "_pupil"),
                ab("pupil_b", cx - 0.45, cy - 0.45, Z - 1.35, cx + 0.45, cy + 0.45, Z - 1.0, rid + "_pupil")]

    def tip(rid):
        tx, ty = at(TIP + 1.4)
        out = [seg("tip", TIP, TIP + 2.2, 1.0, 1.0, rid + "_endrod"),
               seg("tip_stripe", TIP + 0.5, TIP + 0.8, 1.3, 1.3, rid + "_tipglow"),
               seg("tip_stripe2", TIP + 1.5, TIP + 1.8, 1.3, 1.3, rid + "_tipglow")]
        ex, ey = at(TIP + 2.6)
        out += blob("tip_point", ex, ey, Z, 0.5, rid + "_tipglow")
        return out

    def shards(rid):
        out = []
        for i, (s, off, zo, size, c) in enumerate(((13.5, 3.1, 1.6, 0.8, "shard"), (16.4, 2.7, -1.5, 0.7, "shard_dark"),
                                                   (10.5, 3.4, -1.2, 0.6, "shard_dark"), (17.8, 3.4, 1.0, 0.5, "shard"))):
            cx, cy = at(s, off)
            out.append(ab("shard%d" % i, cx - size / 2, cy - size, Z + zo - size / 2, cx + size / 2, cy + size,
                          Z + zo + size / 2, rid + "_" + c))
        return out
    return {"collars": [7.7, 11.2, 15.2], "collar_mat": "ring", "collar_extra": 0.4, "gem_r": 1.55, "butt": [eye],
            "extras": [tip, shards], "grip_rings": [2.2, 3.4, 4.6], "reel_r": 1.5}


LOOKS = {"reed": reed, "reef": reef, "pearl": pearl, "ember": ember, "leviathan": leviathan, "void": void}


# ------------------------------------------------------------------ build

def camera(view, dist=44):
    az, el = math.radians(view["az"]), math.radians(view["el"])
    target = (0, 8, 0)
    pos = (target[0] - dist * math.sin(az) * math.cos(el), target[1] + dist * math.sin(el),
           dist * math.cos(az) * math.cos(el))
    return pos, target


def shot(path, view, size=420, zoom=0.55, dist=44):
    """An orthographic shot, like the game draws an item."""
    pos, target = camera(view, dist)
    views = json.dumps(build.bb.call("tools/call", {"name": "list_views", "arguments": {}}))
    if "lootbox" not in views:
        build.bb.call("tools/call", {"name": "create_offscreen_view",
                                     "arguments": {"id": "lootbox", "width": size, "height": size, "antialias": True}})
    for message in build.bb.call("tools/call", {"name": "set_camera_angle", "arguments": {
            "view": "lootbox", "position": list(pos), "target": list(target), "projection": "orthographic",
            "zoom": zoom}}):
        for part in message.get("result", {}).get("content", []):
            if part.get("type") == "image":
                with open(path, "wb") as out:
                    out.write(base64.b64decode(part["data"]))
                return path
        if message.get("result", {}).get("isError") or "error" in message:
            raise RuntimeError(json.dumps(message)[:600])
    raise RuntimeError("no image from Blockbench")


def js(code):
    return build.eval_js(code)


def make(rid):
    rp.paint.MATERIALS["hook"] = {"colour": "#b8bec8", "kind": "metal"}
    opts = LOOKS[rid]()
    parts = rod_parts(rid, opts)
    faces = build.faces_of(parts)
    image, spots, size = paint.paint(faces)
    elements, index = [], 0
    for part in parts:
        uv = {}
        for side in SIDES:
            face = faces[index]
            x, y = spots[index]
            uv[side] = [x, y, x + face.w, y + face.h]
            index += 1
        rotation, origin = [0, 0, 0], [8, 8, 8]
        if part["rotation"]:
            rotation["xyz".index(part["rotation"]["axis"])] = part["rotation"]["angle"]
            origin = part["rotation"]["origin"]
        elements.append({"name": part["name"], "from": part["from"], "to": part["to"], "uv": uv,
                         "rotation": rotation, "origin": origin})
    spec = {"name": rid, "size": size, "texture": build.data_url(image), "elements": elements, "display": DISPLAY}
    built = build.eval_js(build.BUILD.replace("SPEC", json.dumps(spec)))
    for folder in ("bbmodel", "models", "renders", "textures", "items"):
        os.makedirs(os.path.join(HERE, folder), exist_ok=True)
    with open(os.path.join(HERE, "bbmodel", rid + ".bbmodel"), "w") as handle:
        handle.write(built["project"])
    image.save(os.path.join(HERE, "textures", rid + ".png"))
    shutil.copyfile(os.path.join(HERE, "textures", rid + ".png"), os.path.join(HERE, "textures", rid + "_cast.png"))
    texture = NS + ":item/rod/" + rid
    for suffix, keep in (("", lambda n: True), ("_cast", lambda n: not n.startswith("hang_"))):
        out = {key: value for key, value in built["model"].items() if key != "elements"}
        out["textures"] = {"0": texture, "particle": texture}
        out["elements"] = [e for e in built["model"]["elements"] if keep(e.get("name", ""))]
        for element in out["elements"]:
            for face in element["faces"].values():
                face["texture"] = "#0"
        with open(os.path.join(HERE, "models", rid + suffix + ".json"), "w") as handle:
            json.dump(out, handle, indent=1)
    with open(os.path.join(HERE, "items", rid + ".json"), "w") as handle:
        json.dump({"model": {"type": "minecraft:condition", "property": "minecraft:fishing_rod/cast",
                             "on_false": {"type": "minecraft:model", "model": NS + ":item/rod/" + rid},
                             "on_true": {"type": "minecraft:model", "model": NS + ":item/rod/" + rid + "_cast"}}},
                  handle, indent=1)
    render(rid, spec)
    return len(parts), size


def render(rid, spec):
    js("(() => { Modes.options.edit.select(); return 1; })()")
    time.sleep(0.9)
    renders = os.path.join(HERE, "renders")
    shot(os.path.join(renders, rid + ".png"), VIEW)
    # held: the display rotation inside the hand's own turn, seen from the front right like a third-person player
    js("""(() => {
      const inner = new Group({name: 'display', origin: [8, 8, 8], rotation: [0, 90, 55]}).init();
      const outer = new Group({name: 'hand', origin: [8, 8, 8], rotation: [-90, 180, 0]}).init();
      Cube.all.forEach(c => c.addTo(inner));
      inner.addTo(outer);
      Canvas.updateAll();
      return 1;
    })()""")
    time.sleep(0.4)
    shot(os.path.join(renders, rid + "_held.png"), {"az": -50, "el": 15}, dist=48)
    cast = dict(spec, elements=[e for e in spec["elements"] if not e["name"].startswith("hang_")])
    build.eval_js(build.BUILD.replace("SPEC", json.dumps(cast)))
    js("(() => { Modes.options.edit.select(); return 1; })()")
    time.sleep(0.9)
    shot(os.path.join(renders, rid + "_cast.png"), VIEW)


# ------------------------------------------------------------------ sheet

def sheet():
    from sheets import font, crop, BG
    title, tiny = font(22), font(12)
    cell_w, cell_h, per_row = 660, 250, 3
    ids = list(TIERS)
    rows = [ids[i:i + per_row] for i in range(0, len(ids), per_row)]
    out = Image.new("RGB", (per_row * cell_w + 40, 20 + len(rows) * (cell_h + 70) + 10), BG)
    draw = ImageDraw.Draw(out)
    y = 20
    for row in rows:
        for n, rid in enumerate(row):
            x = 20 + n * cell_w
            name, colour = TIERS[rid]
            draw.text((x + 10, y), name, font=title, fill=colour)
            draw.line((x + 10, y + 30, x + cell_w - 20, y + 30), fill=colour, width=2)
            for k, (suffix, caption) in enumerate((("", "with line"), ("_cast", "cast"), ("_held", "held"))):
                img = crop(Image.open(os.path.join(HERE, "renders", rid + suffix + ".png")).convert("RGBA"))
                scale = min(205 / img.width, 215 / img.height, 2.2)
                img = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.LANCZOS)
                cx = x + k * 215 + 107
                out.paste(img, (cx - img.width // 2, y + 38 + (215 - img.height) // 2), img)
                w = draw.textlength(caption, font=tiny)
                draw.text((cx - w / 2, y + 38 + 220), caption, font=tiny, fill=(120, 124, 134))
        y += cell_h + 70
    out.save(os.path.join(HERE, "sheet.png"))


def main():
    wanted = sys.argv[1:]
    for rid in TIERS:
        if wanted and rid not in wanted:
            continue
        print(rid, make(rid), "parts/atlas", flush=True)
    if not wanted:
        sheet()


if __name__ == "__main__":
    main()
