"""
The big bobber floating over a fishing spot, in the vanilla bobber's spirit: a chunky voxel float, red above and white
below, three steps each way, a dark keel and a J hook underneath, painted at one texel per unit like vanilla textures. The plugin
shows it on an item display scaled up ~2.6x, bobbing and turning slowly like a dropped item.

One model; each kind of spot has its own atlas (same layout, other colours), so a variant is just parent + texture:

  bobber.bbmodel, models/bobber.json (+ bobber_<kind>.json), textures/bobber_<kind>.png, renders/bobber_*.png

    python3 bobber.py
"""
import json
import math
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

paint.DENSITY = 1  # vanilla's density: one texel per model unit
NS = "rainsfishing"
# per kind of spot: (cap, body)
KINDS = {
    "river": ("#d03b34", "#efece4"),
    "ocean": ("#2f7fd6", "#efece4"),
    "warm_ocean": ("#ff7a3c", "#f3ece0"),
    "swamp": ("#6a9a2c", "#e6e2ce"),
    "frozen": ("#8fd3ff", "#ffffff"),
    "cave": ("#e2c13c", "#ede6d4"),
    "void": ("#7ff6ff", "#5a46a0"),
}


def materials(kind="river"):
    cap, body = KINDS[kind]
    paint.MATERIALS["bobber_cap"] = {"colour": cap, "kind": "speckle"}
    paint.MATERIALS["bobber_body"] = {"colour": body, "kind": "speckle"}
    paint.MATERIALS["bobber_keel"] = {"colour": "#3a3530", "kind": "speckle"}
    paint.MATERIALS["bobber_hook"] = {"colour": "#25262b", "kind": "speckle"}


def colours(kind):
    cap, body = KINDS[kind]
    return {"bobber_cap": cap, "bobber_body": body}


def bobber():
    layers = [  # (y0, y1, half width, material), bottom to top
        (0, 1, 1, "bobber_keel"),
        (1, 2, 2, "bobber_body"),
        (2, 4, 3, "bobber_body"),
        (4, 6, 3, "bobber_cap"),
        (6, 7, 2, "bobber_cap"),
        (7, 8, 1, "bobber_cap"),
    ]
    parts = [box("layer_%d" % n, (8 - w, y0, 8 - w), (8 + w, y1, 8 + w), material)
             for n, (y0, y1, w, material) in enumerate(layers)]
    # the hook hanging below, a J like the vanilla sprite's: shaft, bend to the left, barb pointing up
    parts.append(box("hook_shaft", (7.5, -4, 7.5), (8.5, 0, 8.5), "bobber_hook"))
    parts.append(box("hook_bend", (6.5, -5, 7.5), (8.5, -4, 8.5), "bobber_hook"))
    parts.append(box("hook_barb", (5.5, -4, 7.5), (6.5, -3, 8.5), "bobber_hook"))
    return parts


SPIN = """
(() => {
  const angle = ANGLE, lift = LIFT;
  const axis = new THREE.Vector3(8, 0, 8);
  const q = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), angle * Math.PI / 180);
  for (const cube of Cube.all) {
    const mesh = cube.mesh;
    if (!mesh.userData.home) {
      mesh.userData.home = {p: mesh.position.clone(), q: mesh.quaternion.clone()};
    }
    const home = mesh.userData.home;
    const offset = home.p.clone().sub(axis);
    offset.y = 0;
    offset.applyQuaternion(q);
    mesh.position.set(axis.x + offset.x, home.p.y + lift, axis.z + offset.z);
    mesh.quaternion.copy(q).multiply(home.q);
  }
  return Cube.all.length;
})()
"""


def spin(angle, lift):
    build.eval_js(SPIN.replace("ANGLE", str(angle)).replace("LIFT", str(lift)))


def write_model(model, kind=None):
    folder = os.path.join(HERE, "models")
    os.makedirs(folder, exist_ok=True)
    if kind:
        out = {"parent": NS + ":item/fishing_spot/bobber",
               "textures": {"0": NS + ":item/fishing_spot/bobber_" + kind,
                            "particle": NS + ":item/fishing_spot/bobber_" + kind}}
        name = "bobber_" + kind
    else:
        out = dict(model)
        texture = NS + ":item/fishing_spot/bobber_river"
        out["textures"] = {"0": texture, "particle": texture}
        for element in out.get("elements", []):
            for face in element.get("faces", {}).values():
                face["texture"] = "#0"
        name = "bobber"
    with open(os.path.join(folder, name + ".json"), "w") as handle:
        json.dump(out, handle, indent=1)


def main():
    materials()
    parts = bobber()
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
        "name": "bobber", "size": size, "texture": build.data_url(image), "elements": elements,
        "display": {"fixed": {"scale": [1, 1, 1]}, "head": {"scale": [1, 1, 1]},
                    "gui": {"rotation": [20, 30, 0], "scale": [0.9, 0.9, 0.9]},
                    "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]}}})))
    with open(os.path.join(HERE, "bobber.bbmodel"), "w") as handle:
        handle.write(built["project"])
    write_model(built["model"])
    os.makedirs(os.path.join(HERE, "textures"), exist_ok=True)
    os.makedirs(os.path.join(HERE, "renders"), exist_ok=True)
    build.eval_js("(() => { Modes.options.edit.select(); return 1; })()")
    tex = None
    for kind in KINDS:
        atlas = paint.paint(faces, colours=colours(kind))[0]
        atlas.save(os.path.join(HERE, "textures", "bobber_" + kind + ".png"))
        write_model(None, kind)
        # swap the texture in Blockbench to render this kind
        build.eval_js("(() => { Texture.all[0].fromDataURL('ATLAS'.split('_').join('/')); return 1; })()"
                      .replace("ATLAS", build.data_url(atlas)))
        time.sleep(0.8)  # the texture loads asynchronously
        spin(-56, 0)
        lootbox.shot(os.path.join(HERE, "renders", "bobber_" + kind + ".png"), (15, 7, -22), (0, 1.5, 0), size=360)
        if kind in ("ocean", "void"):
            # the float as the server plays it: up and down like a dropped item, turning slowly
            for f in range(16):
                spin(f * 22.5, math.sin(f / 16 * math.tau) * 0.45)
                lootbox.shot(os.path.join(HERE, "renders", "bobber_%s_f%02d.png" % (kind, f)), (13, 5.5, -19.5),
                             (0, 1.5, 0), size=300)
    spin(0, 0)
    print(len(parts), "parts,", len(KINDS), "kinds")


if __name__ == "__main__":
    main()
