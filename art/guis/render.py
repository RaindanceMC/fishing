"""
Draws dumped RainsFishing windows (from e2e's FishingScreensScenarioTest, -Dfishing.guis=<dir>) the way the vanilla
client shows them, with every button's tooltip beside the window. Needs the client's textures unpacked to /tmp/mcassets.

    python3 render.py <dumped-json-dir> <png-out-dir>
"""
import json, os, sys
from PIL import Image, ImageDraw

A = "/tmp/mcassets/assets/minecraft/textures"
ART = "/home/tom/Projekte/SEProjects/MC-Plugins/RaindanceMC/fishing/art"
CAT = json.load(open(ART + "/fish/catalog.json"))
FISH = {f["name"]: f["id"] for f in CAT["fish"]}
S = 3  # scale

font = Image.open(A + "/font/ascii.png").convert("RGBA")
widths = {}
for code in range(256):
    gx, gy = (code % 16) * 8, (code // 16) * 8
    w = 0
    for x in range(8):
        if any(font.getpixel((gx + x, gy + y))[3] > 0 for y in range(8)):
            w = x + 1
    widths[code] = w if code != 32 else 3


def clean(text):
    return (text or "").replace("—", "-").replace("»", ">").replace("’", "'").replace("·", "-")


def text_width(text):
    return sum(widths.get(ord(c), 5) + 1 for c in clean(text) if ord(c) < 256)


def draw_text(img, x, y, text, colour, shadow=True):
    for c in clean(text):
        code = ord(c)
        if code >= 256:
            continue
        gx, gy = (code % 16) * 8, (code // 16) * 8
        glyph = font.crop((gx, gy, gx + 8, gy + 8))
        alpha = glyph.split()[3]
        if shadow:
            sc = tuple(v // 4 for v in colour)
            img.paste(Image.new("RGBA", (8, 8), sc + (255,)), (x + 1, y + 1), alpha)
        img.paste(Image.new("RGBA", (8, 8), colour + (255,)), (x, y), alpha)
        x += widths.get(code, 5) + 1
    return x


def chest_icon():
    tex = Image.open(A + "/entity/chest/normal.png").convert("RGBA")
    icon = Image.new("RGBA", (16, 16))
    icon.paste(tex.crop((14, 14, 28, 19)).resize((14, 5), Image.NEAREST), (1, 1))
    icon.paste(tex.crop((14, 33, 28, 43)).resize((14, 10), Image.NEAREST), (1, 6))
    icon.paste(tex.crop((1, 1, 3, 5)), (7, 4))
    return icon


def icon(item):
    material = item["material"].lower()
    name = item.get("name") or ""
    if name in FISH and os.path.exists(f"{ART}/fish/renders/{FISH[name]}.png"):
        im = Image.open(f"{ART}/fish/renders/{FISH[name]}.png").convert("RGBA")
        bbox = im.getbbox()
        im = im.crop(bbox)
        im.thumbnail((16, 16), Image.LANCZOS)
        out = Image.new("RGBA", (16, 16))
        out.alpha_composite(im, ((16 - im.width) // 2, (16 - im.height) // 2))
        return out
    if material == "chest":
        return chest_icon()
    for path in (f"{A}/item/{material}.png", f"{A}/item/{material}_00.png", f"{A}/block/{material}.png",
                 f"{A}/block/{material.replace('_pane', '')}.png"):
        if os.path.exists(path):
            im = Image.open(path).convert("RGBA")
            return im.crop((0, 0, 16, 16))
    return None


def tooltip(name, lore):
    lines = [(name or " ", (255, 255, 255))] + [(l, (170, 170, 170)) for l in lore]
    w = max(text_width(t) for t, _ in lines) + 8
    h = 4 + 10 + 2 + 10 * (len(lines) - 1) + 4
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((1, 0, w - 2, h - 1), fill=(16, 0, 16, 240))
    d.rectangle((0, 1, w - 1, h - 2), fill=(16, 0, 16, 240))
    d.rectangle((1, 1, w - 2, h - 2), outline=(80, 0, 255, 120))
    d.rectangle((2, 2, w - 3, h - 3), outline=(40, 0, 127, 120))
    y = 4
    for n, (t, c) in enumerate(lines):
        draw_text(img, 4, y, t, c)
        y += 12 if n == 0 else 10
    return img


def render(path):
    d = json.load(open(path))
    rows = d["size"] // 9
    bg = Image.open(A + "/gui/container/generic_54.png").convert("RGBA")
    top = bg.crop((0, 0, 176, 17 + rows * 18))
    bottom = bg.crop((0, 125, 176, 222))
    win = Image.new("RGBA", (176, top.height + bottom.height))
    win.paste(top, (0, 0))
    win.paste(bottom, (0, top.height))
    draw_text(win, 8, 6, d["title"], (64, 64, 64), shadow=False)
    draw_text(win, 8, top.height + 2, "Inventory", (64, 64, 64), shadow=False)
    shown = []
    for slot, item in d["slots"].items():
        slot = int(slot)
        x, y = 8 + (slot % 9) * 18, 18 + (slot // 9) * 18
        ic = icon(item)
        if ic is not None:
            win.alpha_composite(ic, (x, y))
        if item["amount"] > 1:
            draw_text(win, x + 17 - text_width(str(item["amount"])), y + 9, str(item["amount"]), (255, 255, 255))
        if "STAINED_GLASS_PANE" not in item["material"] and (item.get("name") or item.get("lore")):
            shown.append((slot, item))
    tips = [tooltip(item["name"], item["lore"]) for _, item in shown]
    gap = 6
    limit = max(win.height, 330)
    columns, current, used = [], [], 0
    for t in tips:
        if current and used + t.height > limit:
            columns.append(current)
            current, used = [], 0
        current.append(t)
        used += t.height + gap
    if current:
        columns.append(current)
    widths_ = [max(t.width for t in col) for col in columns]
    height = max([win.height] + [sum(t.height + gap for t in col) for col in columns])
    canvas = Image.new("RGBA", (176 + 18 + sum(w + 10 for w in widths_) + 6, height + 12), (30, 33, 40, 255))
    canvas.alpha_composite(win, (6, 6))
    x = 176 + 18
    for col, w in zip(columns, widths_):
        y = 6
        for t in col:
            canvas.alpha_composite(t, (x, y))
            y += t.height + gap
        x += w + 10
    out = canvas.resize((canvas.width * S, canvas.height * S), Image.NEAREST)
    target = os.path.join(sys.argv[2], os.path.basename(path).replace(".json", ".png"))
    out.convert("RGB").save(target)
    print(target)


if __name__ == "__main__":
    os.makedirs(sys.argv[2], exist_ok=True)
    for name in sorted(os.listdir(sys.argv[1])):
        if name.endswith(".json") and name[0].isdigit() and "item" not in name:
            render(os.path.join(sys.argv[1], name))
