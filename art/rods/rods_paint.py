"""
Rod materials on top of the shared item painter: kinds that the DnD painter does not have (wound cord, bamboo, scales,
starry void, magma, glow ...). Every kind returns a ramp position (0 dark .. 6 light) per texel or a fixed colour;
the frame (a light rim up and left, a dark one down and right) is added here. Along the rod = x of a face.
"""
import math
import zlib
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "..", "dnd", "dnd-module", "art", "items"))
import paint  # noqa: E402

paint.DENSITY = 2
LIGHT = {"up": 1.0, "north": 0.25, "west": 0.4, "south": 0.1, "east": -0.15, "down": -0.6}
CUSTOM = {"wrap", "bamboo", "prism", "brick", "blaze", "scale", "stars", "glow", "magma", "flame", "verdigris",
          "pearly", "shell"}


def mat(name, colour, kind, **opts):
    spec = {"colour": colour, "kind": kind}
    spec.update(opts)
    paint.MATERIALS[name] = spec
    return name


def h(x, y, seed=0):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def tone(spec, t, tones):
    return tones[max(0, min(len(tones) - 1, int(round(t))))]


def paint_custom(pixels, x0, y0, face, rng, spec, colours):
    kind = spec["kind"]
    tones = paint.ramp(spec["colour"])
    tones2 = paint.ramp(spec["colour2"]) if "colour2" in spec else tones
    w, hh = face.w, face.h
    light = LIGHT[face.side]
    seed = int(spec.get("seed", 0)) + zlib.crc32(face.material.encode()) % 97
    for y in range(hh):
        for x in range(w):
            noise = rng.uniform(-0.5, 0.5)
            t = 3 + light * 1.1
            use2 = False
            fixed = None
            bevel = True
            if kind == "wrap":
                t += (0.95 if (x + y * 2) % 4 < 2 else -0.75) + noise * 0.4
                if (x + y * 2) % 4 == 0:
                    t -= 0.5
            elif kind == "bamboo":
                t += (h(0, y, seed) - 0.5) * 1.6 + noise * 0.35
                if h(x // 5, y, seed + 3) < 0.18:
                    t -= 1.1
                if y == 0:
                    t += 0.9
            elif kind == "prism":
                cell = h(x // 3, y // 2, seed)
                t += (cell - 0.5) * 2.2 + noise * 0.3
                if x % 3 == 0 and w > 3:
                    t -= 0.7
                if cell > 0.86:
                    use2 = True
                    t = 4.5
            elif kind == "brick":
                row = y // 2
                off = 2 if row % 2 else 0
                if y % 2 == 1 and hh > 2:
                    t = 0.8
                elif (x + off) % 4 == 3 and w > 3:
                    t = 0.8
                else:
                    t += (h((x + off) // 4, row, seed) - 0.5) * 1.3 + noise * 0.3
                bevel = False
            elif kind == "blaze":
                band = (x // 3) % 2
                t += (0.9 if band else -0.4) + noise * 0.4
                if y == 0:
                    t += 0.8
                if h(x, y, seed) > 0.93:
                    use2 = True
                    t = 5.5
            elif kind == "scale":
                row = y // 2
                off = 1.5 if row % 2 else 0
                cx = (x + off) % 3
                edge = (y % 2 == 1) or cx < 1
                t += (-0.9 if edge else 0.9) + (h(int(x + off) // 3, row, seed) - 0.5) * 0.8
                if y % 2 == 0 and 1 <= cx < 2:
                    t += 0.5
            elif kind == "stars":
                t = 1.1 + light * 0.5 + noise * 0.4 + math.sin((x * 0.55 + y * 1.3) * 0.9) * 0.45
                r = h(x, y, seed)
                if r > 0.955:
                    fixed = (230, 250, 255) if r > 0.985 else (127, 246, 255)
                elif r < 0.05:
                    use2 = True
                    t = 3.2
                bevel = r < 0.5
            elif kind == "glow":
                t = 3.5 + noise * 0.3 + (1.3 if y == 0 or x == 0 else 0)
                if min(x, y, w - 1 - x, hh - 1 - y) == 0 and w > 2 and hh > 2:
                    t -= 0.9
                bevel = False
            elif kind == "magma":
                cell = h(x // 2, y // 2, seed)
                t = 3.6 + (cell - 0.5) * 3.0 + noise * 0.3
                if cell < 0.2:
                    use2 = True
                    t = 1.2
                bevel = False
            elif kind == "flame":
                t = 3.0 + (1.0 - y / max(1, hh - 1)) * 2.4 + noise * 0.5
                if h(x, y, seed) > 0.9:
                    t += 1.2
                bevel = False
            elif kind == "verdigris":
                patch = math.sin(x * 0.7 + seed) * math.cos(y * 1.3 + x * 0.2) + h(x, y, seed) * 0.9
                if patch > 0.35:
                    use2 = True
                    t += 0.2 + noise * 0.8
                else:
                    t += math.cos((y / max(1, hh - 1)) * math.pi) * 0.8 + noise * 0.35
                    if (x + y * 2) % max(4, w) == 1:
                        t += 1.2
            elif kind == "pearly":
                wave = math.sin((x * 0.45 + y * 1.4) + seed) * 0.8
                t = 4.0 + light * 0.6 + wave + noise * 0.3
                use2 = wave > 0.45
                if y == 0:
                    t += 0.9
            elif kind == "shell":
                cx, cy = (w - 1) / 2, (hh - 1) / 2
                ang = math.atan2(y - cy, x - cx)
                t += (0.7 if int(ang * 2.2 + 20) % 2 else -0.5) + noise * 0.3
            if bevel:
                if x == 0 or y == 0:
                    t += 0.8
                if x == w - 1 or y == hh - 1:
                    t -= 0.9
            colour = fixed or tone(spec, t, tones2 if use2 else tones)
            pixels[x0 + x, y0 + y] = tuple(colour) + (255,)


_original = paint.paint_face


def patched(pixels, x0, y0, face, rng, colours=None, frame=0):
    spec = paint.MATERIALS[face.material]
    if spec["kind"] in CUSTOM:
        return paint_custom(pixels, x0, y0, face, rng, spec, colours)
    return _original(pixels, x0, y0, face, rng, colours, frame)


paint.paint_face = patched
