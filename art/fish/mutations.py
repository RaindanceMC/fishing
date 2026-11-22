"""
Paints the four rare variants of every fish from its finished texture (run fish.py first), no Blockbench needed:

  textures/<id>_<mutation>.png   models/<id>_<mutation>.json   (the fish's model as parent, with the new texture)
  mutation_sheet.png             one row per fish: plain, albino, melanistic, golden, prismatic

    python3 mutations.py

Albino: pale, pink-tinged, red eyes. Melanistic: near black with a blue sheen. Golden: a gold ramp on the fish's own
shading. Prismatic: its shading in rainbow bands along the body.
"""
import colorsys
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "rainsfishing"
MUTATIONS = ["albino", "melanistic", "golden", "prismatic"]


def lum(r, g, b):
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255


def regions(model, size):
    """Each texel the model paints -> (is a pupil, where along the body 0..1), read from its elements."""
    found = {}
    for element in model["elements"]:
        pupil = element["name"].startswith("E_")
        along = (element["from"][0] + element["to"][0]) / 32
        for face in element["faces"].values():
            x0, y0, x1, y1 = (v * size / 16 for v in face["uv"])
            for y in range(int(min(y0, y1)), int(max(y0, y1))):
                for x in range(int(min(x0, x1)), int(max(x0, x1))):
                    found[x, y] = (pupil, along)
    return found


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return tuple(int(a + (b - a) * f) for a, b in zip(c0, c1))
    return stops[-1][1]


GOLD = [(0.0, (74, 46, 8)), (0.35, (176, 118, 20)), (0.7, (255, 206, 72)), (1.0, (255, 246, 196))]
PALE = [(0.0, (196, 160, 168)), (0.5, (236, 214, 218)), (1.0, (255, 250, 250))]
DARK = [(0.0, (16, 16, 24)), (0.5, (48, 50, 72)), (1.0, (118, 124, 160))]


def paint(image, mutation, where):
    out = image.copy()
    pixels = out.load()
    width, height = out.size
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                continue
            pupil, along = where.get((x, y), (False, x / width))
            if pupil:
                pixels[x, y] = ((200, 36, 60, a) if mutation == "albino" else (r, g, b, a))
                continue
            t = lum(r, g, b)
            if mutation == "albino":
                c = ramp(PALE, 0.25 + t * 0.75)
            elif mutation == "melanistic":
                c = ramp(DARK, t)
            elif mutation == "golden":
                c = ramp(GOLD, 0.15 + t * 0.85)
            else:
                hue = (along * 1.6) % 1.0
                rr, gg, bb = colorsys.hls_to_rgb(hue, 0.3 + t * 0.55, 0.85)
                c = (int(rr * 255), int(gg * 255), int(bb * 255))
            pixels[x, y] = c + (a,)
    return out


def main():
    catalog = json.load(open(os.path.join(HERE, "catalog.json")))["fish"]
    rows = []
    for entry in catalog:
        fid = entry["id"]
        texture = Image.open(os.path.join(HERE, "textures", fid + ".png")).convert("RGBA")
        model = json.load(open(os.path.join(HERE, "models", fid + ".json")))
        # uv runs 0..16 over the whole texture, whatever texture_size the model names
        where = regions(model, texture.width)
        row = [texture]
        for mutation in MUTATIONS:
            painted = paint(texture, mutation, where)
            painted.save(os.path.join(HERE, "textures", "%s_%s.png" % (fid, mutation)))
            # the fish's own model as parent: only the texture differs, so the variant is a few lines
            name = NS + ":item/fish/%s_%s" % (fid, mutation)
            variant = {"parent": NS + ":item/fish/" + fid, "textures": {"0": name, "particle": name}}
            with open(os.path.join(HERE, "models", "%s_%s.json" % (fid, mutation)), "w") as handle:
                json.dump(variant, handle, indent=1)
            row.append(painted)
        rows.append(row)
    cell = max(max(t.size) for t in rows[0]) * 4
    sheet = Image.new("RGBA", (cell * 5, cell * len(rows)), (24, 26, 32, 255))
    for r, row in enumerate(rows):
        for c, texture in enumerate(row):
            big = texture.resize((texture.width * 4, texture.height * 4), Image.NEAREST)
            sheet.paste(big, (c * cell, r * cell), big)
    sheet.save(os.path.join(HERE, "mutation_sheet.png"))
    print(len(rows), "fish,", len(rows) * len(MUTATIONS), "variants")


if __name__ == "__main__":
    main()
