"""Overview sheets: fish_sheet.png (renders by tier) and dish_sheet.png (sprites x8)."""
import json
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
TIERS = ["common", "uncommon", "rare", "epic", "legendary", "mythic"]
TIER_COLOURS = {"common": "#b8bcc4", "uncommon": "#6fd16f", "rare": "#5aa0ff", "epic": "#c070ff",
                "legendary": "#ffb030", "mythic": "#ff5a8a"}
BG = (22, 24, 30)


def font(size):
    for path in ("/usr/share/fonts/TTF/DejaVuSans.ttf", "/usr/share/fonts/noto/NotoSans-Regular.ttf",
                 "/usr/share/fonts/liberation/LiberationSans-Regular.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def crop(image):
    box = image.getbbox()
    return image.crop(box) if box else image


def sheet(entries, loader, cell, per_row, path, scale_fit):
    """entries: dicts with id/name/tier. loader(entry) -> RGBA image already at its display size."""
    title, small = font(26), font(15)
    rows = []
    for tier in TIERS:
        group = [e for e in entries if e["tier"] == tier]
        for i in range(0, len(group), per_row):
            rows.append((tier, group[i:i + per_row], i == 0))
    label_h = 44
    width = per_row * cell[0] + 40
    height = 20 + sum(cell[1] + label_h + (38 if first else 0) for _, _, first in rows) + 20
    out = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(out)
    y = 20
    for tier, group, first in rows:
        if first:
            draw.text((20, y), tier.upper(), font=title, fill=TIER_COLOURS[tier])
            draw.line((20, y + 32, width - 20, y + 32), fill=TIER_COLOURS[tier], width=2)
            y += 38
        for n, entry in enumerate(group):
            x = 20 + n * cell[0]
            img = loader(entry)
            out.paste(img, (x + (cell[0] - img.width) // 2, y + (cell[1] - img.height) // 2), img)
            name = entry["name"]
            w = draw.textlength(name, font=small)
            draw.text((x + (cell[0] - w) / 2, y + cell[1] + 4), name, font=small, fill=(225, 228, 235))
            t = entry["id"] if scale_fit else tier
            w = draw.textlength(tier, font=small)
            draw.text((x + (cell[0] - w) / 2, y + cell[1] + 22), tier, font=small, fill=TIER_COLOURS[tier])
        y += cell[1] + label_h
    out.save(path)


def fish_sheet(catalog):
    def load(entry):
        img = crop(Image.open(os.path.join(HERE, "renders", entry["id"] + ".png")).convert("RGBA"))
        scale = min(236 / img.width, 190 / img.height, 1.0)
        return img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))), Image.LANCZOS)
    sheet(catalog, load, (250, 200), 7, os.path.join(HERE, "fish_sheet.png"), True)


def dish_sheet(dishes):
    def load(entry):
        img = Image.open(os.path.join(HERE, "dishes", entry["id"] + ".png")).convert("RGBA")
        return img.resize((img.width * 8, img.height * 8), Image.NEAREST)
    sheet(dishes, load, (170, 140), 6, os.path.join(HERE, "dish_sheet.png"), False)


if __name__ == "__main__":
    cat = json.load(open(os.path.join(HERE, "catalog.json")))
    fish_sheet(cat["fish"])
