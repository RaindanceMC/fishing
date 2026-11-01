"""
A fishing spot: rings rippling out on the water; the hologram above it is plain text (Core's ui.hologram).

  textures/ripple.png (+ .mcmeta)  animated, greyscale on transparent: tinted per spot through custom_model_data
                                   colours, so one texture serves every spot tier
  models/ripple.json               one flat quad lying on the water (scale the item display to ~3 blocks)
  items/ripple.json                the item definition with the tint
  mockup_sea.gif, mockup_void.gif  how it reads in the world: on the sea, and in the End hanging in the void itself

    python3 spot.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
SIZE = 32
FRAMES = 16
RINGS = 3


def ripple_frame(f):
    """Rings born at the centre every FRAMES/RINGS frames, growing to the edge and fading as they go."""
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = image.load()
    centre = (SIZE - 1) / 2
    rmax = SIZE / 2 - 0.5
    radii = [((f / FRAMES + k / RINGS) % 1.0) * rmax for k in range(RINGS)]
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x - centre, y - centre)
            alpha = 0
            for r in radii:
                if r < 1.5:
                    continue
                fade = (1 - r / rmax) ** 0.8
                delta = d - r
                if -0.5 <= delta < 0.5:
                    alpha = max(alpha, 235 * fade)          # the crest
                elif -1.5 <= delta < -0.5:
                    alpha = max(alpha, 90 * fade)           # the trough behind it, softer
            if alpha:
                shade = 255 if alpha > 150 else 215
                px[x, y] = (shade, shade, shade, int(alpha))
    # bubbles rising where the fish stir: a few fixed spots near the centre, each popping in turn
    for n, (bx, by) in enumerate(((15, 14), (17, 16), (14, 17), (16, 15), (18, 14), (13, 15))):
        phase = (f + n * 5) % FRAMES
        if phase < 3:
            px[bx, by] = (255, 255, 255, 230 - phase * 60)
    return image


def ripple():
    strip = Image.new("RGBA", (SIZE, SIZE * FRAMES))
    frames = [ripple_frame(f) for f in range(FRAMES)]
    for f, frame in enumerate(frames):
        strip.paste(frame, (0, f * SIZE))
    os.makedirs(os.path.join(HERE, "textures"), exist_ok=True)
    strip.save(os.path.join(HERE, "textures", "ripple.png"))
    with open(os.path.join(HERE, "textures", "ripple.png.mcmeta"), "w") as out:
        json.dump({"animation": {"frametime": 6, "interpolate": True}}, out, indent=1)
    face = {"uv": [0, 0, 16, 16], "texture": "#0", "tintindex": 0}
    model = {"textures": {"0": "rainsfishing:block/fishing_spot/ripple", "particle": "rainsfishing:block/fishing_spot/ripple"},
             "elements": [{"name": "ripple", "from": [0, 0.05, 0], "to": [16, 0.05, 16], "shade": False,
                           "faces": {"up": face, "down": dict(face, uv=[0, 16, 16, 0])}}],
             "display": {"fixed": {"scale": [1, 1, 1]}, "head": {"scale": [1, 1, 1]}}}
    os.makedirs(os.path.join(HERE, "models"), exist_ok=True)
    with open(os.path.join(HERE, "models", "ripple.json"), "w") as out:
        json.dump(model, out, indent=1)
    item = {"model": {"type": "minecraft:model", "model": "rainsfishing:block/fishing_spot/ripple",
                      "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": 0xFFFFFF}]}}
    os.makedirs(os.path.join(HERE, "items"), exist_ok=True)
    with open(os.path.join(HERE, "items", "ripple.json"), "w") as out:
        json.dump(item, out, indent=1)
    return frames


# ---- the mockup -----------------------------------------------------------------------------------------------------

def water_tile(seed):
    rng = random.Random(seed)
    tile = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            wave = math.sin((x + y * 0.6 + seed) * 0.7) * 10
            v = rng.randint(-6, 6) + wave
            tile.putpixel((x, y), (int(38 + v * 0.4), int(84 + v), int(170 + v), 255))
    return tile


def font(size):
    for path in ("/usr/share/fonts/TTF/DejaVuSans-Bold.ttf", "/usr/share/fonts/noto/NotoSans-Bold.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def perspective_coeffs(src, dst):
    """Coefficients for Image.transform(PERSPECTIVE) mapping dst quad -> src quad."""
    import numpy
    rows = []
    for (x, y), (u, v) in zip(dst, src):
        rows.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        rows.append([0, 0, 0, x, y, 1, -v * x, -v * y])
    a = numpy.array(rows, dtype=float)
    b = numpy.array([c for p in src for c in p], dtype=float)
    return numpy.linalg.solve(a, b).tolist()


def end_stone_tile(seed):
    rng = random.Random(seed + 40)
    tile = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = rng.randint(-10, 10) - (14 if rng.random() < 0.08 else 0)
            tile.putpixel((x, y), (int(219 + v), int(222 + v), int(160 + v), 255))
    return tile


def mockup(frames, name, void=False, tint=(127, 246, 255), title="Fishing Spot", lines=(), bobber=None):
    """
    Water: the spot lies on the sea. Void: you stand on an End island's edge and the spot hangs in the nothing below
    it — no water, the line simply drops into the void.
    """
    blocks, px = 13, 16
    out_frames = []
    tiles = [end_stone_tile(s) if void else water_tile(s) for s in range(4)]
    width, height = 720, 480
    flat_size = blocks * px
    quad = [(-260, 250), (980, 250), (2200, 560), (-1480, 560)]
    coeffs = perspective_coeffs([(0, 0), (flat_size, 0), (flat_size, flat_size), (0, flat_size)], quad)
    stars = [(random.Random(n).randrange(width), random.Random(n * 3 + 1).randrange(height),
              random.Random(n * 7 + 2).random()) for n in range(140)]
    motes = [(random.Random(n + 500).uniform(260, 460), random.Random(n + 900).uniform(0, 1)) for n in range(14)]
    for f, ring in enumerate(frames):
        flat = Image.new("RGBA", (flat_size, flat_size))
        for by in range(blocks):
            for bx in range(blocks):
                if void and by < 10 - (1 if bx in (2, 3, 9) else 0):
                    continue  # the island stops here; beyond its edge there is nothing
                flat.paste(tiles[(bx * 3 + by + (0 if void else f // 4)) % 4], (bx * px, by * px))
        tinted = Image.new("RGBA", ring.size)
        for y in range(ring.height):
            for x in range(ring.width):
                r, g, b, a = ring.getpixel((x, y))
                tinted.putpixel((x, y), (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255, a))
        spot = tinted.resize((3 * px, 3 * px), Image.NEAREST)
        flat.alpha_composite(spot, (5 * px, 5 * px))
        scene = Image.new("RGBA", (width, height), (24, 30, 46, 255))
        sky = ImageDraw.Draw(scene)
        if void:
            for y in range(height):
                t = y / height
                sky.line([(0, y), (width, y)], fill=(int(14 + 10 * t), int(8 + 4 * t), int(26 + 14 * t), 255))
            for x, y, b in stars:
                if (f + int(b * 16)) % 16 < 13:
                    c = int(110 + b * 120)
                    sky.point((x, y), fill=(c, c, int(c * 0.9) + 20, 255))
        else:
            for y in range(260):
                t = y / 260
                sky.line([(0, y), (width, y)], fill=(int(70 + 60 * t), int(110 + 60 * t), int(170 + 50 * t), 255))
        warped = flat.transform((width, height), Image.PERSPECTIVE, coeffs, Image.NEAREST)
        scene.alpha_composite(warped)
        draw = ImageDraw.Draw(scene)
        if void:
            # reverse-portal motes drifting up out of the spot
            for x, phase in motes:
                t = (phase + f / len(frames)) % 1.0
                y = 330 - t * 120
                size = 4 if t < 0.6 else 2
                draw.rectangle((x, y, x + size, y + size), fill=(190, 120, 255, 255))
        if bobber:
            # the big bobber over the spot (rendered in Blockbench by bobber.py, bobbing and turning)
            path = os.path.join(HERE, "renders", "bobber_%s_f%02d.png" % (bobber, f % 16))
            if os.path.exists(path):
                float_image = Image.open(path).convert("RGBA").resize((150, 150), Image.LANCZOS)
                scene.alpha_composite(float_image, (width // 2 - 75, 206))
        bob = 0
        title_font, line_font = font(30), font(20)
        rows = [(title, title_font, tint)] + [(text, line_font, (225, 230, 236)) for text in lines]
        heights = [fnt.getbbox(t)[3] + 8 for t, fnt, _ in rows]
        widest = max(fnt.getlength(t) for t, fnt, _ in rows)
        top = 88
        left = width / 2 - widest / 2
        panel = Image.new("RGBA", (int(widest + 24), sum(heights) + 20), (0, 0, 0, 96))
        scene.alpha_composite(panel, (int(left - 12), top - 10))
        y = top
        for n, (text, fnt, colour) in enumerate(rows):
            x = width / 2 - fnt.getlength(text) / 2
            draw.text((x + 2, y + 2), text, font=fnt, fill=(40, 40, 40, 255))
            draw.text((x, y), text, font=fnt, fill=colour + (255,))
            y += heights[n]
        out_frames.append(scene.convert("RGB").convert("P", palette=Image.ADAPTIVE))
    out_frames[0].save(os.path.join(HERE, name + ".gif"), save_all=True, append_images=out_frames[1:],
                       duration=150, loop=0)


if __name__ == "__main__":
    frames = ripple()
    mockup(frames, "mockup_sea", tint=(150, 205, 255), lines=("Pearl Shoal", "Pearl Chest can bite here"),
           bobber="ocean")
    mockup(frames, "mockup_void", void=True, lines=("Void Shoal", "Void Chest can bite here"), bobber="void")
    print("ok")
