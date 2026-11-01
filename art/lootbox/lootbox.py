"""
The fishing lootboxes: one sea chest in five tiers, a full block in size, so it works as an item and as a placed
block (an item display). Each tier is told apart by colour, by its metalwork and by what has grown on it:

  driftwood (common)   bleached planks, rope instead of iron, barnacles and kelp
  reef (uncommon)      oak and verdigris copper, coral growing from the lid
  pearl (rare)         deep-blue planks, silver, a scallop shell on top, pearl studs
  abyss (epic)         near-black wood, dark iron, amethyst crystals breaking out, a glowing rune
  leviathan (legendary) red-brown wood under gold, fins on its flanks, a crown on the lid, a sun-orange gem
  void (mythic, best)  starry black, end-rod white corners, an ender eye for a lock, a glowing rift down the front
                       and shards of the void hovering over the lid

Writes per tier: <tier>.bbmodel, models/<tier>.json (closed), <tier>_base.json and <tier>_lid.json (for the opening
animation, the lid hinges at [8, 9, 13]), textures/<tier>.png, and renders under renders/.

Needs Blockbench running with its MCP plugin (client: ~/.claude/tools/bbmcp/bb.py) and the adventurers' item
pipeline (RaindanceMC/dnd/dnd-module/art/items (checked out next to this repository)).

    python3 lootbox.py [tier ...]
"""
import base64
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "..", "dnd", "dnd-module", "art", "items"))
import build  # noqa: E402
import paint  # noqa: E402
from models import box  # noqa: E402

bb = build.bb
HINGE = (8, 9, 13)

BLOCK_DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}

TIERS = {
    "driftwood": {"wood": ("#8a7558", "wood"), "wood_lid": ("#98825f", "wood"), "band": ("#b89a64", "cloth"),
                  "post": None, "lock": ("#7d7a72", "metal"), "gem": ("#9aa3a8", "metal"),
                  "glow": ("#cfd6d8", "smooth"), "inside": ("#2b2620", "smooth")},
    "reef": {"wood": ("#7a5230", "wood"), "wood_lid": ("#86603a", "wood"), "band": ("#4fa784", "metal"),
             "post": ("#3d8a6c", "metal"), "lock": ("#4fa784", "metal"), "gem": ("#2fd062", "gem"),
             "glow": ("#5cf08a", "smooth"), "inside": ("#1f2a1f", "smooth")},
    "pearl": {"wood": ("#2f4f86", "wood"), "wood_lid": ("#38609c", "wood"), "band": ("#c3ccd6", "metal"),
              "post": ("#aab4bf", "metal"), "lock": ("#d6dde6", "metal"), "gem": ("#eef2f8", "gem"),
              "glow": ("#7fc4ff", "smooth"), "inside": ("#141c2e", "smooth")},
    "abyss": {"wood": ("#2c1a3a", "wood"), "wood_lid": ("#352046", "wood"), "band": ("#4a4258", "metal"),
              "post": ("#3a3346", "metal"), "lock": ("#5a5070", "metal"), "gem": ("#b072ff", "gem"),
              "glow": ("#c38cff", "smooth"), "inside": ("#12081c", "smooth")},
    "leviathan": {"wood": ("#5e2416", "wood"), "wood_lid": ("#6c2b1a", "wood"), "band": ("#e2b13c", "metal"),
                  "post": ("#c8962a", "metal"), "lock": ("#f0c650", "metal"), "gem": ("#ff8a1a", "gem"),
                  "glow": ("#ffc24a", "smooth"), "inside": ("#2a1206", "smooth")},
    "void": {"wood": ("#1c1536", "void"), "wood_lid": ("#211a3e", "void"), "band": ("#1d1a2e", "metal"),
             "post": ("#2e2650", "metal"), "lock": ("#2a2440", "metal"), "gem": ("#1d5e4a", "smooth"),
             "glow": ("#7ff6ff", "smooth"), "inside": ("#020106", "smooth")},
}


def materials(tier):
    for slot, spec in TIERS[tier].items():
        if spec:
            paint.MATERIALS[tier + "_" + slot] = {"colour": spec[0], "kind": spec[1]}
    glow, dark = paint.hexrgb(TIERS[tier]["glow"][0]), paint.hexrgb(TIERS[tier]["inside"][0])
    floor = tuple(round(d + (g - d) * 0.28) for g, d in zip(glow, dark))
    paint.MATERIALS[tier + "_floor"] = {"colour": "#%02x%02x%02x" % floor, "kind": "smooth"}
    extra = {"barnacle": ("#cfc9b8", "speckle"), "kelp": ("#3f7a3a", "leather"), "coral": ("#ff6f7d", "smooth"),
             "coral_b": ("#ffa04d", "smooth"), "shell": ("#f1d9c8", "smooth"), "shell_rib": ("#d9a98f", "smooth"),
             "pearl": ("#f3f5fa", "gem"), "crystal": ("#a66bff", "gem"), "crystal_dark": ("#6a35c0", "gem"),
             "rune_glow": ("#b98bff", "rune"), "fin": ("#e2b13c", "metal"), "fin_dark": ("#a87818", "metal"),
             "rift": ("#7ff6ff", "smooth"), "rift_core": ("#f2feff", "smooth"), "shard": ("#1a1430", "void"),
             "shard_edge": ("#5fe6ff", "smooth"), "eye_iris": ("#5fd3a4", "smooth"),
             "eye_inner": ("#a6f0c9", "smooth"), "eye_pupil": ("#04100c", "smooth"),
             "gem_void_rim": ("#1d5e4a", "smooth")}
    for name, (colour, kind) in extra.items():
        paint.MATERIALS[name] = {"colour": colour, "kind": kind}


def chest(tier):
    m = lambda slot: tier + "_" + slot  # noqa: E731
    has_posts = TIERS[tier]["post"] is not None
    band = m("band")
    base, lid = [], []
    # feet and body
    for x, z in ((1.2, 2.7), (12.8, 2.7), (1.2, 11.3), (12.8, 11.3)):
        base.append(box("foot", (x, 0, z), (x + 2, 1, z + 2), m("post") if has_posts else band))
    # hollow: a floor and four walls, dark inside, so the open chest reads empty and deep, waiting for what comes
    # out of it (or goes in); only a faint shimmer of the tier's colour lies on the floor
    base.append(box("floor", (1.5, 1, 3), (14.5, 2, 13), m("wood"), {"up": m("floor")}))
    base.append(box("wall_front", (1.5, 2, 3), (14.5, 8.4, 4), m("wood"), {"south": m("inside"), "up": m("wood")}))
    base.append(box("wall_back", (1.5, 2, 12), (14.5, 8.4, 13), m("wood"), {"north": m("inside"), "up": m("wood")}))
    base.append(box("wall_left", (1.5, 2, 4), (2.5, 8.4, 12), m("wood"), {"east": m("inside"), "up": m("wood")}))
    base.append(box("wall_right", (13.5, 2, 4), (14.5, 8.4, 12), m("wood"), {"west": m("inside"), "up": m("wood")}))
    base.append(box("rim_low", (1.2, 1, 2.7), (14.8, 2, 13.3), band))
    for name, start, end, side in (("rim_front", (1.2, 8.2, 2.7), (14.8, 9, 4.0), "south"),
                                   ("rim_back", (1.2, 8.2, 12.0), (14.8, 9, 13.3), "north"),
                                   ("rim_left", (1.2, 8.2, 4.0), (2.5, 9, 12.0), "east"),
                                   ("rim_right", (13.5, 8.2, 4.0), (14.8, 9, 12.0), "west")):
        base.append(box(name, start, end, band, {side: m("inside")}))
    for x in (4, 10.5):
        base.append(box("strap", (x, 2, 2.6), (x + 1.5, 8.2, 3.0), band))
        base.append(box("strap", (x, 2, 13.0), (x + 1.5, 8.2, 13.4), band))
    if has_posts:
        for x, z in ((1, 2.5), (13.5, 2.5), (1, 12), (13.5, 12)):
            base.append(box("post", (x, 1, z), (x + 1.5, 9, z + 1.5), m("post")))
    base.append(box("lock_plate", (6.5, 5, 2.4), (9.5, 8.2, 3), m("lock")))
    # the lid, rounded by two steps
    lid.append(box("lid_body", (1.5, 9, 3), (14.5, 12, 13), m("wood_lid"), {"down": m("inside")}))
    lid.append(box("lid_arch", (1.5, 12, 4), (14.5, 13.2, 12), m("wood_lid")))
    lid.append(box("lid_crown", (1.5, 13.2, 5.5), (14.5, 13.9, 10.5), m("wood_lid")))
    lid.append(box("lid_rim", (1.2, 9, 2.7), (14.8, 9.8, 13.3), band, {"down": m("inside")}))
    for x in (4, 10.5):
        lid.append(box("lid_strap", (x, 9.8, 2.6), (x + 1.5, 12.2, 13.4), band))
        lid.append(box("lid_strap_arch", (x, 12.2, 3.6), (x + 1.5, 13.4, 12.4), band))
        lid.append(box("lid_strap_crown", (x, 13.4, 5.1), (x + 1.5, 14.1, 10.9), band))
    if has_posts:
        for x, z in ((1, 2.5), (13.5, 2.5), (1, 12), (13.5, 12)):
            lid.append(box("lid_post", (x, 9, z), (x + 1.5, 11.6, z + 1.5), m("post")))
    lid.append(box("lid_latch", (6.8, 7.4, 2.1), (9.2, 11, 2.7), m("lock")))
    gem_size = 1.0 if tier == "leviathan" else 0.8
    if tier != "void":
        lid.append(box("lid_gem", (8 - gem_size, 9.2 - gem_size, 1.8), (8 + gem_size, 9.2 + gem_size, 2.2), m("gem")))
    extras_base, extras_lid = EXTRAS[tier]()
    return base + extras_base, lid + extras_lid


def driftwood():
    base = [box("barnacle", (2.0, 2.4, 2.5), (3.0, 3.2, 3.0), "barnacle"),
            box("barnacle", (2.8, 2.1, 2.6), (3.5, 2.7, 3.0), "barnacle"),
            box("barnacle", (12.6, 3.5, 2.6), (13.4, 4.2, 3.0), "barnacle"),
            box("barnacle", (14.5, 2.6, 9.0), (15.0, 3.6, 10.2), "barnacle"),
            box("kelp", (14.5, 1, 5.2), (14.9, 7.5, 5.8), "kelp"),
            box("kelp", (14.5, 3, 6.4), (14.9, 8.6, 7.0), "kelp")]
    lid = [box("lid_barnacle", (12.4, 12.9, 4.6), (13.6, 13.6, 5.6), "barnacle"),
           box("lid_barnacle", (2.6, 13.5, 8.2), (3.4, 14.2, 9.0), "barnacle"),
           box("lid_kelp", (13.6, 12.0, 5.2), (14.9, 12.4, 7.0), "kelp"),
           box("lid_kelp", (14.5, 9.6, 5.2), (14.9, 12.2, 5.8), "kelp")]
    return base, lid


def reef():
    base = [box("coral", (13.6, 1, 4.6), (14.4, 4.0, 5.4), "coral_b"),
            box("coral", (13.9, 3.2, 4.0), (14.6, 3.8, 6.0), "coral_b")]
    lid = []
    for name, (x, z, h, mat) in {"a": (3.0, 7.0, 3.2, "coral"), "b": (4.4, 8.0, 2.2, "coral_b"),
                                 "c": (2.4, 9.0, 1.6, "coral")}.items():
        lid.append(box("lid_coral_" + name, (x, 13.6, z), (x + 0.8, 13.6 + h, z + 0.8), mat))
    lid.append(box("lid_coral_arm", (2.0, 15.2, 7.1), (3.2, 15.9, 7.7), "coral"))
    lid.append(box("lid_coral_arm", (3.6, 16.0, 7.1), (4.6, 16.6, 7.7), "coral"))
    lid.append(box("lid_coral_tip", (4.1, 15.4, 8.1), (5.5, 16.0, 8.7), "coral_b"))
    return base, lid


def pearl():
    base = [box("pearl", (x, 4.6, z), (x + 0.7, 5.3, z + 0.7), "pearl")
            for x, z in ((1.4, 2.1), (13.9, 2.1))]
    lid = [box("lid_shell", (6.3, 13.9, 6.4), (9.7, 14.6, 9.6), "shell", {"up": "shell_rib"}),
           box("lid_shell_top", (6.8, 14.6, 6.9), (9.2, 15.2, 9.3), "shell", {"up": "shell_rib"}),
           box("lid_shell_hinge", (7.4, 13.9, 9.6), (8.6, 14.4, 10.3), "shell_rib"),
           box("lid_pearl", (7.55, 15.2, 7.6), (8.45, 15.9, 8.5), "pearl")]
    for x, z in ((1.4, 2.1), (13.9, 2.1), (1.4, 13.2), (13.9, 13.2)):
        lid.append(box("lid_pearl_stud", (x, 11.7, z), (x + 0.7, 12.4, z + 0.7), "pearl"))
    return base, lid


def abyss():
    base = [box("rune", (6.8, 2.6, 2.5), (9.2, 4.4, 2.6), "rune_glow"),
            box("crystal", (12.4, 1, 1.6), (13.6, 4.4, 2.8), "crystal", None,
                {"angle": 22.5, "axis": "z", "origin": [13, 1, 2.2]}),
            box("crystal", (13.6, 1, 2.0), (14.4, 3.0, 2.8), "crystal_dark", None,
                {"angle": -22.5, "axis": "x", "origin": [14, 1, 2.4]}),
            box("crystal", (1.2, 1, 12.6), (2.4, 5.2, 13.8), "crystal", None,
                {"angle": -22.5, "axis": "z", "origin": [1.8, 1, 13.2]})]
    # a cluster bursting out of the lid: one tall spire, three leaning out of it
    lid = [box("lid_crystal", (7.0, 13.2, 7.0), (9.0, 18.6, 9.0), "crystal"),
           box("lid_crystal", (9.2, 13.2, 7.2), (10.8, 16.8, 8.8), "crystal_dark", None,
               {"angle": -22.5, "axis": "z", "origin": [10, 13.4, 8]}),
           box("lid_crystal", (5.2, 13.2, 7.4), (6.6, 16.2, 8.8), "crystal", None,
               {"angle": 22.5, "axis": "z", "origin": [5.9, 13.4, 8.1]}),
           box("lid_crystal", (7.4, 13.2, 5.0), (8.8, 15.6, 6.4), "crystal_dark", None,
               {"angle": -22.5, "axis": "x", "origin": [8.1, 13.4, 5.7]}),
           box("lid_crystal", (12.0, 12.6, 10.6), (13.2, 15.0, 11.8), "crystal", None,
               {"angle": 22.5, "axis": "x", "origin": [12.6, 12.8, 11.2]})]
    return base, lid


def leviathan():
    base = []
    # swept fins on both flanks: slats that start low at the front and sweep up and back
    for x0, x1 in ((0.2, 1.2), (14.8, 15.8)):
        for n, (y, z0, z1) in enumerate(((2.6, 4.2, 10.4), (3.6, 5.2, 11.4), (4.6, 6.4, 12.4), (5.6, 7.8, 13.2),
                                         (6.6, 9.4, 13.8))):
            base.append(box("fin", (x0, y, z0), (x1, y + 1.0, z1), "fin" if n % 2 == 0 else "fin_dark"))
    base.append(box("emblem", (7.0, 2.6, 2.5), (9.0, 4.4, 2.7), "fin"))
    lid = []
    for x, h in ((4.2, 1.6), (7.3, 2.4), (10.4, 1.6)):
        lid.append(box("lid_spike", (x, 13.9, 7.4), (x + 1.4, 13.9 + h, 8.6), "fin", {"up": "fin_dark"}))
    lid.append(box("lid_crown_band", (3.6, 13.9, 7.2), (12.4, 14.4, 8.8), "fin_dark"))
    return base, lid


def void():
    # a rift torn diagonally across the front under the lock, white-hot at its core
    base = []
    for n, (x0, y0, x1, y1) in enumerate(((5.6, 2.2, 7.0, 3.2), (6.6, 3.0, 8.4, 3.9), (8.0, 3.7, 9.4, 4.6),
                                          (9.0, 4.4, 10.4, 5.2))):
        base.append(box("rift", (x0, y0, 2.45), (x1, y1, 2.6), "rift"))
        base.append(box("rift_core", (x0 + 0.3, y0 + 0.3, 2.4), (x1 - 0.3, y1 - 0.3, 2.45), "rift_core"))
    # thin end-rod light running up every corner
    for x, z in ((0.85, 2.35), (14.65, 2.35), (0.85, 13.15), (14.65, 13.15)):
        base.append(box("end_rod", (x, 1, z), (x + 0.5, 9, z + 0.5), "rift_core"))
    # shards of the void hovering over the lid, edged in gateway light; they ride with the lid and fly when it opens
    lid = []
    for n, (x, y, z, w, h, axis, angle) in enumerate(((7.0, 15.6, 7.0, 2.0, 2.6, "y", 45), (3.4, 15.0, 5.0, 1.2, 1.6, "z", 22.5),
                                                      (11.6, 15.4, 9.6, 1.2, 1.8, "x", -22.5),
                                                      (10.4, 17.4, 5.6, 0.8, 1.0, "z", -22.5))):
        rotation = {"angle": angle, "axis": axis, "origin": [x + w / 2, y + h / 2, z + w / 2]}
        lid.append(box("lid_shard_%d" % n, (x, y, z), (x + w, y + h, z + w), "shard",
                       {"up": "shard_edge", "down": "shard_edge"}, rotation))
    lid.append(box("lid_seam", (1.5, 13.9, 7.6), (14.5, 14.05, 8.4), "rift"))
    # the lock is an ender eye, rounded with plus shapes: dark green rim, green iris, a pale ring, a black slit
    def plus(name, r, z0, z1, material, cx=8, cy=9.2):
        short = r * 0.6
        return [box(name, (cx - r, cy - short, z0), (cx + r, cy + short, z1), material),
                box(name + "_b", (cx - short, cy - r, z0 - 0.01), (cx + short, cy + r, z1 - 0.01), material)]
    lid += plus("lid_eye_rim", 1.25, 1.75, 2.1, "gem_void_rim")
    lid += plus("lid_eye_iris", 0.95, 1.6, 1.75, "eye_iris")
    lid += plus("lid_eye_inner", 0.6, 1.5, 1.6, "eye_inner")
    lid.append(box("lid_eye_pupil", (7.86, 8.5, 1.4), (8.14, 9.9, 1.5), "eye_pupil"))
    for x, z in ((0.85, 2.35), (14.65, 2.35), (0.85, 13.15), (14.65, 13.15)):
        lid.append(box("lid_end_rod", (x, 9, z), (x + 0.5, 11.6, z + 0.5), "rift_core"))
    return base, lid


EXTRAS = {"driftwood": driftwood, "reef": reef, "pearl": pearl, "abyss": abyss, "leviathan": leviathan, "void": void}

LID_POSE = """
(() => {
  const angle = ANGLE, lift = LIFT, shake = SHAKE;
  const hinge = new THREE.Vector3(8, 9, 13);
  const q = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1, 0, 0), angle * Math.PI / 180);
  let n = 0;
  for (const cube of Cube.all) {
    const mesh = cube.mesh;
    if (!mesh.userData.home) {
      mesh.userData.home = {p: mesh.position.clone(), q: mesh.quaternion.clone()};
    }
    const home = mesh.userData.home;
    mesh.position.copy(home.p);
    mesh.quaternion.copy(home.q);
    if (cube.name.startsWith('lid')) {
      const offset = home.p.clone().sub(hinge).applyQuaternion(q);
      mesh.position.copy(hinge).add(offset);
      mesh.quaternion.copy(q).multiply(home.q);
      n++;
    }
    mesh.position.y += lift;
    mesh.position.x += shake;
  }
  return n;
})()
"""


def pose(angle=0.0, lift=0.0, shake=0.0):
    return build.eval_js(LID_POSE.replace("ANGLE", str(angle)).replace("LIFT", str(lift))
                         .replace("SHAKE", str(shake)))


def shot(path, position, target=(0, 7, 0), fov=34, size=560):
    views = json.dumps(bb.call("tools/call", {"name": "list_views", "arguments": {}}))
    if "lootbox" not in views:
        bb.call("tools/call", {"name": "create_offscreen_view",
                               "arguments": {"id": "lootbox", "width": size, "height": size, "antialias": True}})
    for message in bb.call("tools/call", {"name": "set_camera_angle", "arguments": {
            "view": "lootbox", "position": list(position), "target": list(target), "projection": "perspective",
            "fov": fov}}):
        for part in message.get("result", {}).get("content", []):
            if part.get("type") == "image":
                with open(path, "wb") as out:
                    out.write(base64.b64decode(part["data"]))
                return path
        if message.get("result", {}).get("isError") or "error" in message:
            raise RuntimeError(json.dumps(message)[:600])
    raise RuntimeError("no image from Blockbench")


def write_model(model, path, keep=lambda name: True, tier=""):
    texture = "rainsfishing:block/lootbox/" + tier
    out = {key: value for key, value in model.items() if key != "elements"}
    out["textures"] = {"0": texture, "particle": texture}
    out["elements"] = [e for e in model.get("elements", []) if keep(e.get("name", ""))]
    for element in out["elements"]:
        for face in element.get("faces", {}).values():
            face["texture"] = "#0"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        json.dump(out, handle, indent=1)


def make(tier):
    materials(tier)
    base, lid = chest(tier)
    parts = base + lid
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
        rotation, origin = [0, 0, 0], [8, 8, 8]
        if part["rotation"]:
            rotation["xyz".index(part["rotation"]["axis"])] = part["rotation"]["angle"]
            origin = part["rotation"]["origin"]
        elements.append({"name": part["name"], "from": part["from"], "to": part["to"], "uv": uv,
                         "rotation": rotation, "origin": origin})
    built = build.eval_js(build.BUILD.replace("SPEC", json.dumps({
        "name": "lootbox_" + tier, "size": size, "texture": build.data_url(image), "elements": elements,
        "display": BLOCK_DISPLAY})))
    with open(os.path.join(HERE, tier + ".bbmodel"), "w") as handle:
        handle.write(built["project"])
    os.makedirs(os.path.join(HERE, "textures"), exist_ok=True)
    image.save(os.path.join(HERE, "textures", tier + ".png"))
    models = os.path.join(HERE, "models")
    write_model(built["model"], os.path.join(models, tier + ".json"), tier=tier)
    write_model(built["model"], os.path.join(models, tier + "_base.json"), lambda n: not n.startswith("lid"), tier)
    write_model(built["model"], os.path.join(models, tier + "_lid.json"), lambda n: n.startswith("lid"), tier)
    return len(parts)


def renders(tier):
    folder = os.path.join(HERE, "renders", tier)
    os.makedirs(folder, exist_ok=True)
    build.eval_js("(() => { Modes.options.edit.select(); return 1; })()")
    pose(0)
    shot(os.path.join(folder, "closed.png"), (25, 19, -31))
    shot(os.path.join(folder, "back.png"), (-25, 19, 31))
    pose(105)
    shot(os.path.join(folder, "open.png"), (27, 26, -34), (0, 9, 0))
    frames = []
    # the opening as the server will play it: rise, shake harder and harder, burst open, settle
    timeline = [(0, 0, 0)] * 2 + [(0, l, 0) for l in (0.5, 1.0, 1.5, 2.0)]
    timeline += [(0, 2.0, s) for s in (0.3, -0.3, 0.5, -0.5, 0.7, -0.7, 0.9, -0.9)]
    timeline += [(a, 2.0, 0) for a in (25, 60, 95, 120, 112, 105)] + [(105, 2.0, 0)] * 6
    for n, (angle, lift, shake) in enumerate(timeline):
        pose(angle, lift, shake)
        frames.append(shot(os.path.join(folder, "f%02d.png" % n), (28, 24, -36), (0, 10, 0), size=360))
    pose(0)
    return frames


def main():
    wanted = sys.argv[1:] or list(TIERS)
    for tier in wanted:
        print(tier, make(tier), "parts")
        renders(tier)


if __name__ == "__main__":
    main()
