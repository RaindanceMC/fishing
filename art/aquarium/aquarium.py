"""
The aquarium: a one-block glass tank with a dark wooden frame, a sand floor, water, a sprig of kelp and a pebble.
Written straight as an item model (no Blockbench needed), worn by the display a placed tank stands in and by the item.

  aquarium.json  (rainsfishing:item/aquarium)   aquarium.png  (32x32 atlas)
  joined/aquarium_<mask>.json   the look of a placed block joined to neighbours (north 1, east 2, south 4, west 8),
                                so tanks side by side read as one

    python3 aquarium.py
"""
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "rainsfishing"
SIZE = 32
rng = random.Random(11)

# atlas regions, in texels: name -> (x, y, w, h)
REGIONS = {"frame": (0, 0, 8, 8), "sand": (8, 0, 8, 8), "kelp": (16, 0, 8, 8), "pebble": (24, 0, 8, 8),
           "water": (0, 8, 16, 16), "glass": (16, 8, 16, 16)}


def paint():
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = image.load()

    def fill(name, colour):
        x0, y0, w, h = REGIONS[name]
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                px[x, y] = colour(x - x0, y - y0, w, h)

    def jitter(base, amount):
        # one shift for all three channels: lighter or darker, never a different hue
        shift = rng.randint(-amount, amount)
        return tuple(max(0, min(255, c + shift)) for c in base)

    fill("frame", lambda x, y, w, h: jitter((58, 40, 28), 8) + (255,))
    fill("sand", lambda x, y, w, h: jitter((219, 203, 150), 14) + (255,))
    fill("kelp", lambda x, y, w, h: jitter((58, 128, 52) if (x + y) % 3 else (40, 96, 38), 8) + (255,))
    fill("pebble", lambda x, y, w, h: jitter((128, 132, 138), 12) + (255,))
    # water and glass are even all over: joined tanks put block after block of them side by side, and any pattern
    # in them shows as a seam every block
    fill("water", lambda x, y, w, h: (52, 128, 196, 96))
    fill("glass", lambda x, y, w, h: (210, 235, 255, 30))
    return image


def uv(name, side_w=None, side_h=None):
    x0, y0, w, h = REGIONS[name]
    k = 16 / SIZE
    return [x0 * k, y0 * k, (x0 + (side_w or w)) * k, (y0 + (side_h or h)) * k]


def cube(name, frm, to, material, sides=None):
    faces = {}
    for side in ("north", "east", "south", "west", "up", "down"):
        if sides is not None and side not in sides:
            continue
        faces[side] = {"uv": uv(material), "texture": "#0"}
    return {"name": name, "from": frm, "to": to, "faces": faces}


N, E, S, W = 1, 2, 4, 8


def elements(mask=0):
    """One block of a tank, joined to its neighbours on the sides in mask: no glass, frame or water wall there."""
    def has(side):
        return bool(mask & side)

    # how far floor, water and rims reach along x and z: to the block's edge where a neighbour carries on
    x0, x1 = (0 if has(W) else 1), (16 if has(E) else 15)
    z0, z1 = (0 if has(N) else 1), (16 if has(S) else 15)
    y0, y1 = 1, 15
    e = []
    # a corner post stands only where neither side next to it goes on
    for name, (x, z), sides in (("nw", (0, 0), (N, W)), ("ne", (15, 0), (N, E)), ("sw", (0, 15), (S, W)),
                                ("se", (15, 15), (S, E))):
        if not any(has(side) for side in sides):
            e.append(cube("post_" + name, [x, 0, z], [x + 1, 16, z + 1], "frame"))
    for y in (0, 15):
        if not has(N):
            e.append(cube("rim_n%d" % y, [x0, y, 0], [x1, y + 1, 1], "frame"))
        if not has(S):
            e.append(cube("rim_s%d" % y, [x0, y, 15], [x1, y + 1, 16], "frame"))
        if not has(W):
            e.append(cube("rim_w%d" % y, [0, y, z0], [1, y + 1, z1], "frame"))
        if not has(E):
            e.append(cube("rim_e%d" % y, [15, y, z0], [16, y + 1, z1], "frame"))
    water_faces = [face for face, side in (("north", N), ("south", S), ("west", W), ("east", E)) if not has(side)]
    e.append(cube("floor", [x0, 0, z0], [x1, 1, z1], "frame"))
    e.append(cube("sand", [x0, 1, z0], [x1, 2.5, z1], "sand", tuple(["up"] + water_faces)))
    # the decoration moves with the mask, so a long tank is not the same block over and over
    if mask % 3 == 0:
        e.append(cube("kelp1", [3, 2.5, 11], [4, 9, 12], "kelp"))
        e.append(cube("kelp2", [4, 6, 11], [5, 11, 12], "kelp"))
    if mask % 3 == 1:
        e.append(cube("kelp3", [12, 2.5, 4], [13, 7, 5], "kelp"))
    if mask % 4 == 0:
        e.append(cube("pebble", [10, 2.5, 10], [12.5, 3.5, 12], "pebble"))
    elif mask % 4 == 2:
        e.append(cube("pebble", [4, 2.5, 5], [6, 3.5, 6.5], "pebble"))
    e.append(cube("water", [x0, 2.5, z0], [x1, 13.5, z1], "water", tuple(water_faces + ["up"])))
    # glass panes just inside the frame, each a single face outward, only on open sides
    if not has(N):
        e.append(cube("glass_n", [x0, y0, 0.5], [x1, y1, 0.5], "glass", ("north",)))
    if not has(S):
        e.append(cube("glass_s", [x0, y0, 15.5], [x1, y1, 15.5], "glass", ("south",)))
    if not has(W):
        e.append(cube("glass_w", [0.5, y0, z0], [0.5, y1, z1], "glass", ("west",)))
    if not has(E):
        e.append(cube("glass_e", [15.5, y0, z0], [15.5, y1, z1], "glass", ("east",)))
    return e


def main():
    texture = NS + ":item/aquarium"
    model = {
        "textures": {"0": texture, "particle": texture},
        "elements": elements(0),
        "display": {
            "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
            "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }
    with open(os.path.join(HERE, "aquarium.json"), "w") as out:
        json.dump(model, out, indent=1)
    # a placed block wears one of 16 looks, by the sides it is joined on (TankRule.look)
    os.makedirs(os.path.join(HERE, "joined"), exist_ok=True)
    for mask in range(16):
        joined = {"textures": model["textures"], "elements": elements(mask)}
        with open(os.path.join(HERE, "joined", "aquarium_%d.json" % mask), "w") as out:
            json.dump(joined, out, separators=(",", ":"))
    paint().save(os.path.join(HERE, "aquarium.png"))
    print(len(model["elements"]), "elements, 16 joined looks")


if __name__ == "__main__":
    main()
