"""
The aquarium: a one-block glass tank with a dark wooden frame, a sand floor, water, a sprig of kelp and a pebble.
Written straight as an item model (no Blockbench needed), worn by the display a placed tank stands in and by the item.

  aquarium.json  (rainsfishing:item/aquarium)   aquarium.png  (32x32 atlas)

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
        return tuple(max(0, min(255, c + rng.randint(-amount, amount))) for c in base)

    fill("frame", lambda x, y, w, h: jitter((58, 40, 28), 8) + (255,))
    fill("sand", lambda x, y, w, h: jitter((219, 203, 150), 14) + (255,))
    fill("kelp", lambda x, y, w, h: jitter((58, 128, 52) if (x + y) % 3 else (40, 96, 38), 8) + (255,))
    fill("pebble", lambda x, y, w, h: jitter((128, 132, 138), 12) + (255,))
    fill("water", lambda x, y, w, h: jitter((52, 128, 196), 6) + (96 + (8 if (x * 7 + y * 3) % 5 == 0 else 0),))
    # glass: nearly clear, a lighter rim and a diagonal glint
    fill("glass", lambda x, y, w, h: (220, 240, 255, 150) if x in (0, w - 1) or y in (0, h - 1)
         else (235, 248, 255, 70) if abs(x - y) <= 1 and 3 < x < 9 else (210, 235, 255, 26))
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


def elements():
    e = []
    # frame: four posts and the top and bottom rims
    for i, (x, z) in enumerate(((0, 0), (15, 0), (0, 15), (15, 15))):
        e.append(cube("post%d" % i, [x, 0, z], [x + 1, 16, z + 1], "frame"))
    for y in (0, 15):
        e.append(cube("rim_n%d" % y, [1, y, 0], [15, y + 1, 1], "frame"))
        e.append(cube("rim_s%d" % y, [1, y, 15], [15, y + 1, 16], "frame"))
        e.append(cube("rim_w%d" % y, [0, y, 1], [1, y + 1, 15], "frame"))
        e.append(cube("rim_e%d" % y, [15, y, 1], [16, y + 1, 15], "frame"))
    e.append(cube("floor", [1, 0, 1], [15, 1, 15], "frame"))
    e.append(cube("sand", [1, 1, 1], [15, 2.5, 15], "sand", ("up", "north", "south", "east", "west")))
    e.append(cube("kelp1", [3, 2.5, 11], [4, 9, 12], "kelp"))
    e.append(cube("kelp2", [4, 6, 11], [5, 11, 12], "kelp"))
    e.append(cube("kelp3", [12, 2.5, 4], [13, 7, 5], "kelp"))
    e.append(cube("pebble", [10, 2.5, 10], [12.5, 3.5, 12], "pebble"))
    e.append(cube("water", [1, 2.5, 1], [15, 13.5, 15], "water", ("up", "north", "south", "east", "west")))
    # glass panes just inside the frame, each a single face outward
    e.append(cube("glass_n", [1, 1, 0.5], [15, 15, 0.5], "glass", ("north",)))
    e.append(cube("glass_s", [1, 1, 15.5], [15, 15, 15.5], "glass", ("south",)))
    e.append(cube("glass_w", [0.5, 1, 1], [0.5, 15, 15], "glass", ("west",)))
    e.append(cube("glass_e", [15.5, 1, 1], [15.5, 15, 15], "glass", ("east",)))
    return e


def main():
    texture = NS + ":item/aquarium"
    model = {
        "textures": {"0": texture, "particle": texture},
        "elements": elements(),
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
    paint().save(os.path.join(HERE, "aquarium.png"))
    print(len(model["elements"]), "elements")


if __name__ == "__main__":
    main()
