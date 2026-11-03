"""
The fishing skill tree's looks: brass portholes for the nodes, fishing line between them (Core's tree.py writes the
font rainsfishing:tree from them), and a painting per branch behind the page (font rainsfishing:tree_scene, U+E000 +
the branch's place in FishingPack.SCENES). preview.py lays a page out the way the game does, to look at.

    python3 skilltree_art.py      (pack.py runs it too)
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CORE_ART = os.path.join(HERE, "..", "..", "..", "..", "RainsCore", "art", "menus")
sys.path.insert(0, CORE_ART)

from canvas import (Canvas, W, chest_well, inventory_well, hotbar_well, label_y, height, mix, wood, well,  # noqa: E402
                    vignette, rivet, shade)
import tree as core_tree  # noqa: E402

PACK = os.path.join(HERE, "..", "..", "fishing-module", "src", "main", "resources", "de", "raindancer", "modules",
                    "fishing", "pack")
NS = "rainsfishing"
ASSETS = os.path.join(PACK, "assets", NS)
ROWS = 6
SCENES = ["tidecaller", "treasure", "collector", "ember", "monger"]


# ------------------------------------------------------------------------------------------------ frames and lines

BRASS = (214, 168, 72)
BRASS_DARK = (118, 78, 26)
BRASS_LIGHT = (255, 232, 160)


class FishingLook(core_tree.Style):
    """Brass portholes and fishing line."""

    def line(self, along, across, lit):
        d = abs(across)
        # a little twist along the line, like braided line
        twist = 0.5 + 0.5 * math.sin(along * 1.6)
        if lit:
            if d <= 0.8:
                return mix((210, 255, 250), (255, 255, 255), twist), 255
            if d <= 1.6:
                return mix((60, 214, 210), (120, 240, 230), twist), 255
            if d <= 4.2:
                return (90, 230, 220), int(120 * (1 - (d - 1.6) / 2.6) ** 1.5)
            return None
        if d <= 0.7:
            return mix((150, 160, 168), (196, 204, 210), twist), 235
        if d <= 1.5:
            return (40, 44, 52), 200
        return None

    def frame(self, state, x, y):
        r = math.hypot(x, y)
        a = math.atan2(y, x)
        ring_outer, ring_inner = 11.2, 8.4
        if state == "locked":
            face, dark, light = (104, 104, 110), (48, 48, 54), (150, 150, 158)
            glass = (14, 20, 28)
        elif state == "barred":
            face, dark, light = (128, 76, 52), (64, 34, 24), (170, 110, 80)
            glass = (30, 14, 14)
        elif state == "maxed":
            face, dark, light = (246, 200, 90), (150, 96, 26), (255, 246, 200)
            glass = (30, 96, 104)
        elif state == "owned":
            face, dark, light = BRASS, BRASS_DARK, BRASS_LIGHT
            glass = (20, 70, 84)
        else:  # open
            face, dark, light = BRASS, BRASS_DARK, BRASS_LIGHT
            glass = (14, 44, 60)
        # the glow of a node that can be taken, and the rays of a finished one
        if state == "open" and ring_outer < r <= 12.9:
            return (110, 250, 240), int(170 * (1 - (r - ring_outer) / 1.7))
        if state == "maxed" and ring_outer < r <= 12.9:
            ray = max(0.0, math.cos(a * 6)) ** 6
            if ray > 0.25:
                return (255, 236, 150), int(255 * ray)
            return None
        if ring_inner < r <= ring_outer:
            # lit from the top left, a groove in the middle of the ring
            lit = 0.5 - 0.5 * (x + y) / max(1.0, r) / 1.25
            colour = mix(dark, light, lit)
            if abs(r - (ring_inner + ring_outer) / 2) < 0.45:
                colour = mix(colour, dark, 0.45)
            colour = mix(face, colour, 0.75)
            # four bolts
            for k in range(4):
                bx, by = math.cos(math.pi / 4 + k * math.pi / 2) * 9.8, math.sin(math.pi / 4 + k * math.pi / 2) * 9.8
                bd = math.hypot(x - bx, y - by)
                if bd < 1.25:
                    colour = light if (x - bx) + (y - by) < 0 else dark
            if state == "barred" and (abs(math.cos(a * 3)) > 0.97):
                colour = mix(colour, (40, 20, 14), 0.6)
            return colour, 255
        if r <= ring_inner:
            # the glass: water behind it, a highlight across the top left
            depth = r / ring_inner
            colour = mix(glass, shade(glass, 0.55), depth * 0.8)
            if -6.5 < x + y * 0.6 < -4.5 and r < ring_inner - 1:
                colour = mix(colour, (220, 250, 255), 0.22)
            if state == "barred" and abs(x - y) <= 1.0:
                return (196, 64, 52), 255
            if state == "locked":
                return colour, 210
            return colour, 235
        return None


# ------------------------------------------------------------------------------------------------ the paintings

def noise(rng, x, y, s):
    return (math.sin(x * 0.29 + y * 0.13) * 0.5 + math.sin(x * 0.071 - y * 0.19 + 1.3) * 0.5) * s \
        + rng.uniform(-1, 1) * s * 0.6


def chrome_and_inventory(c, rng, plank, plank_dark, plank_light, well_inner):
    """The bottom: a plank bar for the chrome row, the player's inventory on wet planks, and its label's patch."""
    h = height(ROWS)
    y0 = 17 + 18 * 5 - 2
    wood(c, 0, y0, W, h, plank, plank_dark, plank_light, seed=rng.randint(0, 999), plank=10)
    vignette(c, 0, y0, W, h, (10, 8, 6), strength=0.35, reach=8)
    for col in range(9):
        x, y = chest_well(5, col)
        well(c, x, y, well_inner, plank_dark, plank_light, depth=0.4)
    # "Inventory" is written in the game's dark grey: a pale strip of rope-wrapped board under it
    ly = label_y(ROWS)
    for y in range(ly - 2, ly + 9):
        for x in range(5, 70):
            c.put(x, y, mix((214, 196, 156), (188, 168, 126), (y - ly + 2) / 11 + rng.uniform(-0.04, 0.04)))
    c.hline(5, 70, ly - 3, plank_dark)
    c.hline(5, 70, ly + 9, plank_dark)
    for x in (5, 69):
        c.vline(x, ly - 2, ly + 9, plank_dark)
    for row in range(3):
        for col in range(9):
            x, y = inventory_well(ROWS, row, col)
            well(c, x, y, well_inner, plank_dark, plank_light, depth=0.4)
    for col in range(9):
        x, y = hotbar_well(ROWS, col)
        well(c, x, y, well_inner, plank_dark, plank_light, depth=0.4)
    c.round_corners(3)


def title_band(c, top, bottom, edge):
    """The strip the title is written on: dark enough for light ink."""
    for y in range(0, 16):
        for x in range(W):
            c.put(x, y, mix(top, bottom, y / 15))
    c.hline(0, W, 15, edge)
    c.hline(0, W, 16, shade(edge, 0.6), 160)


def field(c, top=17, bottom=17 + 18 * 5 - 2):
    return top, bottom


def scene_tidecaller(seed=1):
    """The open sea at dusk: a deep gradient, rolling swells, a moon low over the water."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    t0, t1 = field(c)
    deep, mid, sky = (8, 30, 56), (18, 72, 108), (60, 120, 150)
    for y in range(t0, t1):
        for x in range(W):
            v = (y - t0) / (t1 - t0)
            swell = math.sin(x * 0.11 + y * 0.42 + math.sin(x * 0.03) * 3) * 0.5 + 0.5
            colour = mix(mix(sky, mid, min(1, v * 2.2)), deep, max(0, v * 1.3 - 0.3))
            colour = mix(colour, (120, 190, 210), (swell ** 10) * 0.18 * (1 - v))
            colour = mix(colour, (0, 0, 0), noise(rng, x, y, 0.02) + 0.02)
            c.put(x, y, colour)
    # a moon and its path on the water
    mx, my = 142, 30
    for y in range(t0, t1):
        for x in range(W):
            d = math.hypot(x - mx, y - my)
            if d < 7:
                c.put(x, y, mix((250, 246, 220), (220, 214, 180), d / 7))
            elif d < 16:
                c.tint(x, y, (200, 220, 230), 0.18 * (1 - (d - 7) / 9))
            if abs(x - mx) < 6 + (y - my) * 0.25 and y > my + 10 and math.sin(y * 1.7 + x * 0.3) > 0.55:
                c.tint(x, y, (240, 240, 210), 0.22 * (1 - (y - my) / 90))
    # foam crests
    for _ in range(26):
        x0, y0 = rng.randint(0, W), rng.randint(t0 + 20, t1 - 4)
        for i in range(rng.randint(5, 12)):
            c.tint(x0 + i, y0 + int(math.sin(i * 0.7) * 1.2), (200, 236, 240), 0.45)
    vignette(c, 0, t0, W, t1, (2, 8, 18), strength=0.5, reach=18)
    title_band(c, (12, 26, 40), (20, 44, 62), (6, 14, 24))
    chrome_and_inventory(c, rng, (104, 82, 58), (54, 40, 28), (150, 122, 88), (36, 30, 26))
    return c


def scene_treasure(seed=2):
    """An old sea chart: stained parchment, dotted routes, a compass rose and an X."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    t0, t1 = field(c)
    paper, paper_dark, ink = (214, 190, 140), (168, 136, 88), (92, 60, 32)
    for y in range(t0, t1):
        for x in range(W):
            stain = noise(rng, x, y, 0.08) + 0.12
            c.put(x, y, mix(paper, paper_dark, stain))
    # coastlines
    for k in range(3):
        cx, cy, rad = rng.randint(10, 166), rng.randint(t0 + 10, t1 - 10), rng.randint(12, 22)
        for y in range(t0, t1):
            for x in range(W):
                a = math.atan2(y - cy, x - cx)
                edge = rad * (1 + 0.25 * math.sin(a * 5 + k) + 0.12 * math.sin(a * 11))
                d = math.hypot(x - cx, y - cy)
                if d < edge:
                    c.tint(x, y, (150, 160, 96), 0.35)
                if abs(d - edge) < 0.6:
                    c.tint(x, y, ink, 0.6)
    # a compass rose
    rx, ry = 150, t1 - 22
    for y in range(t0, t1):
        for x in range(W):
            d = math.hypot(x - rx, y - ry)
            a = math.atan2(y - ry, x - rx)
            if d < 16 * (0.3 + 0.7 * abs(math.cos(a * 2)) ** 8):
                c.tint(x, y, ink, 0.35)
            if abs(d - 13) < 0.5:
                c.tint(x, y, ink, 0.5)
    # dotted routes and an X
    for _ in range(3):
        x, y = rng.uniform(0, W), rng.uniform(t0, t1)
        ang = rng.uniform(0, 6.28)
        for i in range(60):
            ang += rng.uniform(-0.25, 0.25)
            x += math.cos(ang) * 2.4
            y += math.sin(ang) * 2.4
            if i % 2 == 0:
                c.tint(int(x), int(y), (150, 40, 30), 0.6)
    for i in range(-3, 4):
        c.put(30 + i, t1 - 16 + i, (160, 30, 26))
        c.put(30 + i, t1 - 16 - i, (160, 30, 26))
    vignette(c, 0, t0, W, t1, (70, 40, 14), strength=0.6, reach=14)
    title_band(c, (64, 40, 22), (84, 56, 30), (36, 22, 12))
    chrome_and_inventory(c, rng, (110, 76, 46), (58, 38, 22), (156, 116, 74), (40, 28, 20))
    return c


def scene_collector(seed=3):
    """A reef seen through the water: blue-green light, kelp, coral, sand and bubbles."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    t0, t1 = field(c)
    for y in range(t0, t1):
        for x in range(W):
            v = (y - t0) / (t1 - t0)
            colour = mix((40, 150, 160), (12, 64, 86), v)
            ray = max(0.0, math.sin(x * 0.09 - y * 0.05 + 1.0)) ** 14
            colour = mix(colour, (170, 240, 230), ray * 0.25 * (1 - v))
            c.put(x, y, mix(colour, (0, 0, 0), noise(rng, x, y, 0.02) + 0.02))
    # sand along the bottom, coral and kelp standing on it
    for x in range(W):
        top = t1 - 9 + int(math.sin(x * 0.12) * 2 + math.sin(x * 0.31) * 1)
        for y in range(top, t1):
            c.put(x, y, mix((196, 176, 120), (150, 130, 84), (y - top) / 9 + rng.uniform(-0.05, 0.05)))
    for k in range(9):
        bx = rng.randint(4, W - 4)
        colour = rng.choice([(230, 100, 120), (250, 160, 70), (180, 90, 200), (90, 200, 160)])
        for i in range(rng.randint(12, 30)):
            sway = math.sin(i * 0.35 + k) * 2.2
            y = t1 - 9 - i
            c.tint(int(bx + sway), y, colour, 0.6)
            c.tint(int(bx + sway) + 1, y, shade(colour, 0.7), 0.45)
    for k in range(6):
        bx = rng.randint(0, W)
        for i in range(60):
            sway = math.sin(i * 0.12 + k * 2) * 4
            c.tint(int(bx + sway), t1 - 8 - i, (40, 110, 60), 0.5)
    for _ in range(40):
        x, y = rng.randint(0, W - 1), rng.randint(t0, t1 - 12)
        c.tint(x, y, (220, 250, 255), 0.5)
        c.tint(x + 1, y - 1, (255, 255, 255), 0.25)
    vignette(c, 0, t0, W, t1, (2, 20, 30), strength=0.55, reach=16)
    title_band(c, (8, 48, 58), (14, 70, 80), (4, 24, 30))
    chrome_and_inventory(c, rng, (96, 84, 66), (48, 40, 30), (144, 128, 100), (30, 30, 30))
    return c


def scene_ember(seed=4):
    """Lava on the left, the void on the right, meeting in a ragged seam."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    t0, t1 = field(c)
    stars = {(rng.randint(0, W - 1), rng.randint(t0, t1 - 1)) for _ in range(70)}
    for y in range(t0, t1):
        for x in range(W):
            seam = W * 0.5 + (y - t0) * 0.35 - 20 + math.sin(y * 0.2) * 6 + math.sin(y * 0.07) * 8
            if x < seam:
                crack = abs(math.sin(x * 0.21 + math.sin(y * 0.17) * 2.4) * math.cos(y * 0.19 + x * 0.05))
                colour = mix((60, 14, 6), (230, 90, 20), crack ** 3 * 0.9)
                colour = mix(colour, (255, 200, 80), max(0, crack - 0.92) * 8)
                glow = max(0.0, 1 - (seam - x) / 30)
                colour = mix(colour, (120, 30, 60), glow * 0.35)
            else:
                v = (x - seam) / max(1, W - seam)
                colour = mix((40, 10, 52), (6, 2, 14), min(1, v * 1.4))
                if (x, y) in stars:
                    colour = (230, 210, 255) if rng.random() < 0.5 else (170, 140, 230)
                edge = max(0.0, 1 - (x - seam) / 10)
                colour = mix(colour, (180, 60, 200), edge * 0.4)
            c.put(x, y, colour)
    vignette(c, 0, t0, W, t1, (6, 2, 6), strength=0.55, reach=16)
    title_band(c, (34, 10, 14), (48, 14, 34), (14, 4, 8))
    chrome_and_inventory(c, rng, (64, 52, 56), (30, 22, 26), (110, 92, 100), (24, 18, 22))
    return c


def scene_monger(seed=5):
    """The fish market on the pier: boards, a striped awning, lantern light, crates of fish."""
    c = Canvas(W, height(ROWS), seed)
    rng = random.Random(seed)
    t0, t1 = field(c)
    wood(c, 0, t0, W, t1, (120, 88, 56), (66, 46, 28), (170, 132, 90), seed=seed, plank=13, vertical=True)
    # an awning along the top, red and cream
    for y in range(t0, t0 + 9):
        for x in range(W):
            stripe = (x // 11) % 2
            colour = (176, 48, 42) if stripe else (232, 220, 190)
            scallop = y - t0 > 6 + math.cos((x % 11) / 11 * 6.28) * 1.5
            if not scallop:
                c.put(x, y, shade(colour, 0.85 + 0.15 * (y - t0) / 9))
    # lantern glow
    for lx in (30, 146):
        for y in range(t0, t1):
            for x in range(W):
                d = math.hypot(x - lx, y - (t0 + 16))
                if d < 40:
                    c.tint(x, y, (255, 200, 110), 0.28 * (1 - d / 40) ** 2)
    vignette(c, 0, t0, W, t1, (20, 12, 6), strength=0.55, reach=16)
    title_band(c, (54, 30, 18), (78, 44, 26), (28, 16, 8))
    chrome_and_inventory(c, rng, (92, 66, 42), (48, 34, 20), (140, 106, 70), (34, 26, 18))
    return c


PAINTERS = [scene_tidecaller, scene_treasure, scene_collector, scene_ember, scene_monger]


def write_scenes():
    out = os.path.join(ASSETS, "textures", "font", "tree_scene")
    os.makedirs(out, exist_ok=True)
    providers = []
    files = []
    for index, (name, paint) in enumerate(zip(SCENES, PAINTERS)):
        c = paint()
        c.save(os.path.join(out, name + ".png"))
        files.append("assets/%s/textures/font/tree_scene/%s.png" % (NS, name))
        providers.append({"type": "bitmap", "file": "%s:font/tree_scene/%s.png" % (NS, name),
                          "ascent": 13, "height": height(ROWS), "chars": [chr(0xE000 + index)]})
    providers.append({"type": "space", "advances": {"": -8, "": -169}})
    with open(os.path.join(ASSETS, "font", "tree_scene.json"), "w") as f:
        json.dump({"providers": providers}, f, indent=1)
    files.append("assets/%s/font/tree_scene.json" % NS)
    return files


def paint():
    """Writes the frames, lines and paintings into the pack; returns the files written."""
    os.makedirs(os.path.join(ASSETS, "font"), exist_ok=True)
    files, images = core_tree.write(ASSETS, NS, "tree", FishingLook())
    core_tree.sheet(images, "/tmp/tree-fishing.png")
    return files + write_scenes()


def main():
    files = paint()
    listing = os.path.join(PACK, "files.txt")
    known = [line.strip() for line in open(listing) if line.strip()]
    known = [f for f in known if "/font/tree" not in f and "font/tree_scene" not in f] + files
    with open(listing, "w") as out:
        out.write("\n".join(known) + "\n")
    print(len(files), "files")


if __name__ == "__main__":
    main()
