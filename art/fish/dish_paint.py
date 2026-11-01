"""
Food painter for the 3D dishes: one generic material kind ("d_tex") with patterns (rice grains, flaky fish, crumb,
glazed ceramic, broth with floating bits, newspaper print ...). Everything that is not food (wood, gold, gems)
goes to the shared paint.py kinds. Each material can carry raw_<option> overrides for the raw texture.
"""
import math
import random
import sys
import os

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "..", "dnd", "dnd-module", "art", "items"))
import paint  # noqa: E402

paint.DENSITY = 2
MATS = {}
LIGHT = {"up": 1.0, "north": 0.25, "west": 0.4, "south": 0.1, "east": -0.15, "down": -0.6}


def mat(name, colour, pattern="flat", **opts):
    spec = {"kind": "d_tex", "colour": colour, "pattern": pattern}
    spec.update(opts)
    MATS[name] = spec
    paint.MATERIALS[name] = spec
    return name


def hard(name, kind, colour, raw=None):
    spec = {"kind": kind, "colour": colour}
    if raw:
        spec["raw_colour"] = raw
    MATS[name] = spec
    paint.MATERIALS[name] = spec
    return name


def raw_colours():
    out = {"_raw": True}
    for name, spec in MATS.items():
        if "raw_colour" in spec:
            out[name] = spec["raw_colour"]
    return out


def hx(text):
    return paint.hexrgb(text)


def shade(rgb, f):
    return tuple(max(0, min(255, int(c * f))) for c in rgb)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def hash2(x, y, seed=0):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def paint_d(pixels, x0, y0, face, rng, spec, colours):
    raw = bool(colours and colours.get("_raw"))

    def o(key, default=None):
        if raw and ("raw_" + key) in spec:
            return spec["raw_" + key]
        return spec.get(key, default)

    base = colours.get(face.material, spec["colour"]) if raw else spec["colour"]
    tones = paint.ramp(base)
    n_tones = len(tones)
    pattern = o("pattern", "flat")
    w, h, side = face.w, face.h, face.side
    lit = LIGHT[side]
    top = side == "up"
    seed = spec.get("seed", 3)
    grid = [[None] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            t = 3.0 + lit * o("shade", 1.1)
            n = rng.uniform(-0.5, 0.5)
            if pattern == "flat":
                t += n * 0.6
            elif pattern == "speck":
                t += n * 1.7 - (1.4 if rng.random() < 0.1 else 0)
            elif pattern == "crumb":
                t += (hash2(x // 2, y // 2, seed) - 0.5) * 1.6 + n * 0.9
                r = rng.random()
                if r < 0.07:
                    t -= 1.3
                elif r < 0.12:
                    t += 1.1
            elif pattern == "dough":
                t += (hash2(x // 3, y // 3, seed) - 0.5) * 0.9 + n * 0.5
            elif pattern == "grain":
                gx = (x + ((y // 2) % 2) * 2) % 4
                if gx == 3:
                    t -= 1.5
                else:
                    t += 0.7 + (0.5 if gx == 0 else 0)
                if y % 2 == 1:
                    t -= 0.5
                t += n * 0.4
            elif pattern == "flake":
                s = (x + y + seed % 3) % 5
                if s == 0:
                    t = 4.8 + lit * 0.8 + n * 0.3
                else:
                    t += (-0.4 if s == 1 else 0) + n * 0.5
            elif pattern == "marble":
                s = (x * 2 + y * 3 + seed) % 9
                t += (1.8 if s < 2 else 0) + n * 0.5
            elif pattern == "grill":
                t += n * 0.6 - (1.8 if (x - y) % 8 in (0, 1) else 0)
            elif pattern == "scale2":
                gx = (x + (y // 2) % 2 * 2) % 4
                t += (-0.9 if gx == 0 else 0.3) + (-0.6 if y % 2 == 1 else 0.3) + n * 0.4
            elif pattern == "scale":
                t += (0.5 if (x + y) % 2 == 0 else -0.35) + n * 0.5
            elif pattern == "sheen":
                t += math.sin(y * 1.3 + x * 0.15) * 0.35 + n * 0.5
            elif pattern == "stripe":
                t += (0.7 if (x // 2) % 2 == 0 else -0.5) + n * 0.4
            elif pattern == "mat":
                t += (-1.3 if x % 4 == 0 else (0.5 if x % 4 == 1 else 0)) + n * 0.4
                if y % 6 == 3 and x % 4 != 0:
                    t -= 0.8
            elif pattern == "ceramic":
                t += n * 0.25
                if not top and side != "down":
                    t += 0.7 - (y / max(1, h - 1)) * 1.3
                    if y == 1 and w > 4:
                        t += 1.2
                    if x == 2 and 2 < y < h - 2:
                        t += 0.9
            elif pattern == "liquid":
                t = o("level", 3.4) + math.sin(x * 0.9 + y * 0.45) * 0.3 + n * 0.45
                if not top:
                    t -= 0.5
            elif pattern == "paper":
                t += n * 0.5 + 0.6
            elif pattern == "void":
                t = 0.7 + n * 0.8 + (hash2(x // 2, y // 2, seed) - 0.5) * 1.0
            elif pattern == "glow":
                t = 4.4 + n * 1.0 + math.sin(x * 0.9 + y * 0.7) * 0.5
            else:
                raise ValueError(pattern)
            band = o("band")
            if band and not top and side != "down" and pattern == "ceramic":
                colour, f0, f1 = band
                if h * f0 <= y < h * f1 + 0.001:
                    grid[y][x] = ("c", hx(colour), t)
                    continue
            if o("bevel", True) and w > 3 and h > 3 and pattern not in ("liquid", "paper"):
                if x == 0 or y == 0:
                    t += 0.7
                if x == w - 1 or y == h - 1:
                    t -= 0.9
            grid[y][x] = ("t", None, t)

    # print on paper, in world coordinates so it runs on across boxes: text lines, headlines, a photo block
    if pattern == "paper" and w > 2:
        ink = hx(o("ink", "#4a4a4a"))
        ox, oy = int(round(getattr(face, "wu", 0) * 2)), int(round(getattr(face, "wv", 0) * 2))
        for y in range(h):
            for x in range(w):
                gx, gy = x + ox, y + oy
                if hash2(gx // 14, gy // 12, 5) > 0.9 and (gx % 14) not in (0, 13) and (gy % 12) not in (0, 11):
                    grid[y][x] = ("c", mix(ink, (214, 208, 190), 0.55 + 0.1 * ((gx // 2 + gy // 2) % 2)), 0)
                elif gy % 3 == 1 and (gx % 7) < 5 and hash2(gx // 7, gy // 3, 2) > 0.22:
                    grid[y][x] = ("c", ink, 0)

    for spec_blob in o("blobs", []) or []:
        colours_b, density = spec_blob
        count = int(w * h * density / 100.0 + rng.random())
        for _ in range(count):
            bw, bh = rng.choice(((2, 2), (3, 2), (2, 1), (2, 2), (1, 1)))
            bx, by = rng.randint(0, max(0, w - bw)), rng.randint(0, max(0, h - bh))
            c = hx(rng.choice(colours_b))
            for yy in range(bh):
                for xx in range(bw):
                    if by + yy < h and bx + xx < w:
                        f = 1.25 if (xx == 0 and yy == 0) else (0.72 if (xx == bw - 1 and yy == bh - 1 and bw * bh > 2) else 1.0)
                        grid[by + yy][bx + xx] = ("c", shade(c, f), 0)
    for colour, prob in o("specks", []) or []:
        c = hx(colour)
        for y in range(h):
            for x in range(w):
                if rng.random() < prob:
                    grid[y][x] = ("c", c, 0)
    sparkle = o("sparkle")
    if sparkle and w > 4 and h > 4:
        c = hx(sparkle)
        for _ in range(int(w * h / 90.0 + rng.random())):
            sx, sy = rng.randint(1, w - 2), rng.randint(1, h - 2)
            grid[sy][sx] = ("c", (255, 255, 255), 0)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                grid[sy + dy][sx + dx] = ("c", c, 0)

    for y in range(h):
        for x in range(w):
            kind, colour, t = grid[y][x]
            alpha = 0 if o("invisible") else 255
            if kind == "c":
                pixels[x0 + x, y0 + y] = colour + (alpha,)
            else:
                pixels[x0 + x, y0 + y] = tones[max(0, min(n_tones - 1, int(round(t))))] + (alpha,)


_original = paint.paint_face


def patched(pixels, x0, y0, face, rng, colours=None, frame=0):
    spec = paint.MATERIALS[face.material]
    if spec["kind"] == "d_tex":
        return paint_d(pixels, x0, y0, face, rng, spec, colours)
    return _original(pixels, x0, y0, face, rng, colours, frame)


paint.paint_face = patched
