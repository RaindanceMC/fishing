"""Approval sheet (closed row, open row, tier labels) and one opening GIF per tier from the renders."""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
TIERS = [("driftwood", "Driftwood", "Common", (190, 186, 176)), ("reef", "Reef", "Uncommon", (92, 220, 130)),
         ("pearl", "Pearl", "Rare", (110, 175, 255)), ("abyss", "Abyss", "Epic", (190, 130, 255)),
         ("leviathan", "Leviathan", "Legendary", (255, 190, 70)),
         ("void", "Void", "Mythic", (127, 246, 255))]


def font(size):
    for path in ("/usr/share/fonts/TTF/DejaVuSans-Bold.ttf", "/usr/share/fonts/noto/NotoSans-Bold.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def sheet():
    cell = 400
    out = Image.new("RGBA", (cell * len(TIERS), cell * 2 + 90), (28, 31, 38, 255))
    draw = ImageDraw.Draw(out)
    for i, (tier, name, rarity, colour) in enumerate(TIERS):
        folder = os.path.join(HERE, "renders", tier)
        for row, shot in enumerate(("closed", "open")):
            image = Image.open(os.path.join(folder, shot + ".png")).convert("RGBA").resize((cell, cell))
            out.alpha_composite(image, (i * cell, 90 + row * cell))
        draw.rectangle((i * cell + 20, 76, (i + 1) * cell - 20, 82), fill=colour)
        draw.text((i * cell + cell // 2, 22), name, fill=colour, font=font(30), anchor="mt")
        draw.text((i * cell + cell // 2, 56), rarity, fill=(200, 204, 212), font=font(17), anchor="mt")
    out.save(os.path.join(HERE, "lootbox_tiers.png"))


def gifs():
    for tier, *_ in TIERS:
        folder = os.path.join(HERE, "renders", tier)
        names = sorted(n for n in os.listdir(folder) if n.startswith("f") and n.endswith(".png"))
        frames = []
        for n in names:
            frame = Image.new("RGBA", (360, 360), (28, 31, 38, 255))
            frame.alpha_composite(Image.open(os.path.join(folder, n)).convert("RGBA").resize((360, 360)))
            frames.append(frame.convert("P", palette=Image.ADAPTIVE))
        durations = [120] * len(frames)
        durations[-1] = 900
        frames[0].save(os.path.join(HERE, "opening_" + tier + ".gif"), save_all=True, append_images=frames[1:],
                       duration=durations, loop=0)


if __name__ == "__main__":
    sheet()
    gifs()
