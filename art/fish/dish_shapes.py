"""
The dishes' shapes: DISHES[id] = {"items": [...], "view": {...}}. An item is a layer of a character grid
(L(...): y range, rows, top-left corner x/z, legend char -> material or (material, {side: material})) or one box (B(...)).
Colours and patterns live in the material definitions below (cooked colour, raw_ overrides for the raw texture).
"""
from dish_paint import mat, hard


# ------------------------------------------------------------------ grid helpers

def L(y0, y1, rows, at, legend, cell=1):
    return {"y": (y0, y1), "rows": rows, "at": at, "legend": legend, "cell": cell}


def B(x0, y0, z0, x1, y1, z1, material, sides=None, name="x"):
    return {"box": (x0, y0, z0, x1, y1, z1), "mat": material, "sides": sides, "name": name}


def rect(h, w, r0=0, c0=0):
    return {(r0 + r, c0 + c) for r in range(h) for c in range(w)}


def octa(h, w, cut, r0=0, c0=0):
    cells = rect(h, w, r0, c0)
    for i in range(cut):
        k = cut - i
        for c in list(range(k)) + list(range(w - k, w)):
            cells.discard((r0 + i, c0 + c))
            cells.discard((r0 + h - 1 - i, c0 + c))
    return cells


def shrink(cells, times=1):
    for _ in range(times):
        cells = {(r, c) for r, c in cells if all(n in cells for n in ((r - 1, c), (r + 1, c), (r, c - 1), (r, c + 1)))}
    return cells


def rows_of(size, *layers):
    """size = (h, w); layers = (cells, char) painted in order."""
    h, w = size
    grid = {}
    for cells, ch in layers:
        for cell in cells:
            grid[cell] = ch
    return ["".join(grid.get((r, c), ".") for c in range(w)) for r in range(h)]


def shift(cells, dr, dc):
    return {(r + dr, c + dc) for r, c in cells}


# ------------------------------------------------------------------ shared pieces

def bowl(body, rim, liquid, n=14, cx=8, cz=8, foot=None, gem=None, gem_side=None, h=2):
    """A voxel bowl, n wide (even), flared to a rim, liquid 0.5 under the rim top. Returns (items, top_y)."""
    at = (cx - n / 2, cz - n / 2)
    size = (n, n)
    items = []
    for y0, y1, wd in ((0, 1, n - 8), (1, 2, n - 6), (2, 3, n - 4), (3, 3 + h, n - 2)):
        o = (n - wd) // 2
        items.append(L(y0, y1, rows_of(size, (octa(wd, wd, 1 if wd <= 6 else 2, o, o), "b")), at,
                       {"b": foot if (y0 == 0 and foot) else body}))
    outer = octa(n, n, 3 if n >= 14 else 2)
    items.append(L(3 + h, 4 + h, rows_of(size, (outer - shrink(outer), "R")), at, {"R": rim}))
    inner = shrink(outer)
    rs, cs = [r for r, _ in inner], [c for _, c in inner]
    items.append(B(at[0] + min(cs), 3 + h, at[1] + min(rs), at[0] + max(cs) + 1, 3.5 + h, at[1] + max(rs) + 1, liquid,
                   name="liquid"))
    if gem:
        items.append(B(cx - 1, 3, cz + n / 2 - 1, cx + 1, 3 + h, cz + n / 2 - 0.5, gem, gem_side, name="gem"))
    return items, 3.5 + h


def plate(mat_top, mat_rim, n_h=12, n_w=16, cx=8, cz=8, under=None):
    """A flat plate (octagonal), food sits at y=2. Returns items."""
    at = (cx - n_w / 2, cz - n_h / 2)
    size = (n_h, n_w)
    cut = 3 if min(n_h, n_w) >= 12 else 2
    outer = octa(n_h, n_w, cut)
    return [
        L(0, 1, rows_of(size, (octa(n_h - 4, n_w - 4, 1, 2, 2), "b")), at, {"b": under or mat_top}),
        L(1, 2, rows_of(size, (outer, "b")), at, {"b": mat_top}),
        L(2, 2.5, rows_of(size, (outer - shrink(outer), "R")), at, {"R": mat_rim}),
    ]


# ------------------------------------------------------------------ materials

wood = hard("wood", "wood", "#8a5a30")
wood_dark = hard("wood_dark", "wood", "#5b3a1e")
wood_light = hard("wood_light", "wood", "#b98b52", raw="#b98b52")
gold = hard("gold", "metal", "#e2b13c")
gold_dark = hard("gold_dark", "metal", "#b98a22")
silver = hard("silver", "metal", "#c3ccd6")

broth_fisher = mat("broth_fisher", "#c98d3f", "liquid", level=3.5, specks=[("#f2cf86", 0.02)],
                   blobs=[(["#e8803a", "#e8803a", "#f0a050"], 1.3), (["#efdca0", "#d9c37a"], 1.1),
                          (["#f0e6dc", "#e6c8c4"], 1.0), (["#5f9a45"], 0.5)],
                   raw_colour="#d8c9a8", raw_specks=[("#e8e0c8", 0.02)],
                   raw_blobs=[(["#e8a090", "#e8a090", "#f0b8a8"], 1.5), (["#ece0b4"], 1.2), (["#e9dcc8"], 1.0)])
fish_chunk = mat("fish_chunk", "#f0e2d0", "flake", raw_colour="#f4b8a8", raw_pattern="marble")
fish_chunk_side = mat("fish_chunk_side", "#d9a35e", "crumb", raw_colour="#f0b4a0", raw_pattern="dough")
carrot = mat("carrot", "#e8802a", "speck", raw_colour="#e8a060")
potato = mat("potato", "#e6cf86", "speck", raw_colour="#ece3b8")
bowl_wood = mat("bowl_wood", "#8a5a30", "speck", shade=1.0)
bowl_wood_rim = mat("bowl_wood_rim", "#a8743e", "speck")


# shared sushi / plate materials
rice = mat("rice", "#f4f1e8", "grain", raw_colour="#d9d6cb")
nori = mat("nori", "#1f3a2a", "sheen", raw_colour="#2a4a33")
kelp = mat("kelp", "#2f4d2d", "sheen", raw_colour="#3c5d38")
wasabi = mat("wasabi", "#86bb3e", "speck", raw_colour="#9cc860")
ginger = mat("ginger", "#f1b5ae", "flat", raw_colour="#f4c8c0")
board = hard("board", "wood", "#b98b52")
board_edge = hard("board_edge", "wood", "#8a5f33")
lemon = mat("lemon", "#f2d33c", "speck", raw_colour="#f2d33c")
lemon_pith = mat("lemon_pith", "#f7efc0", "flat")
herb = mat("herb", "#4d9a3c", "speck", raw_colour="#6eaa58")
cream = mat("cream", "#f4f0e2", "speck", raw_colour="#ece6d4")
plate_white = mat("plate_white", "#ebe7dc", "ceramic")
plate_rim = mat("plate_rim", "#d6d2c6", "ceramic", bevel=False)
black = mat("black", "#14161c", "flat")


def maki_piece(x, z, outer, rice_m, fill, fill2=None, raise_fill=0.5, y0=1, h=3.5, nori_sides=None):
    """An upright cut roll, 6x6: nori ring, rice ring, 2x2 filling (3 fill + 1 fill2)."""
    cells = octa(6, 6, 1)
    f = shrink(cells, 2)
    items = [L(y0, y0 + h, rows_of((6, 6), (cells, "n"), (shrink(cells), "r")), (x, z), {"n": outer, "r": rice_m})]
    layout = [((2, 2), "f"), ((2, 3), "f"), ((3, 2), "g" if fill2 else "f"), ((3, 3), "f")]
    rows = rows_of((6, 6), *[({c}, ch) for c, ch in layout])
    items.append(L(y0, y0 + h + raise_fill, rows, (x, z), {"f": fill, "g": fill2 or fill}))
    return items


def nigiri(x, z, fish_top, fish_side, rice_m=None, band=None, y0=1, w=6, d=4, topping=None):
    """Rice block w x d with a slice draped over and a band round the middle."""
    rice_m = rice_m or rice
    items = [L(y0, y0 + 2.5, rows_of((d, w), (octa(d, w, 1), "r")), (x, z), {"r": rice_m}),
             B(x - 0.5, y0 + 2.5, z - 0.5, x + w + 0.5, y0 + 3.5, z + d + 0.5, fish_top,
               {s: fish_side for s in ("north", "south", "east", "west")}, name="slice")]
    if band:
        items.append(B(x + w / 2 - 1, y0 + 0.5, z - 1, x + w / 2 + 1, y0 + 4, z + d + 1, band, name="band"))
    if topping:
        items.append(topping)
    return items


# ------------------------------------------------------------------ the dishes
DISHES = {}

# ---- fish skewer
sk_stick = hard("sk_stick", "wood", "#b98b52")
sk_fish = mat("sk_fish", "#c98a42", "grill", raw_colour="#f1b4a2", raw_pattern="marble")
sk_flesh = mat("sk_flesh", "#f1e6d2", "flake", raw_colour="#f6c2b4", raw_pattern="marble")
sk_red = mat("sk_red", "#cf3b2c", "speck", raw_colour="#d9604e")
sk_onion = mat("sk_onion", "#d9a6c4", "stripe", raw_colour="#e6c6d8")
sk_green = mat("sk_green", "#5fa23c", "speck", raw_colour="#79b658")


def fish_chunk_box(x0, x1, y0=6, y1=10):
    return B(x0, y0, y0, x1, y1, y1, sk_fish, {"west": sk_flesh, "east": sk_flesh}, name="fish")


DISHES["fish_skewer"] = {"items": [
    B(0, 7.5, 7.5, 16, 8.5, 8.5, sk_stick, name="stick"),
    fish_chunk_box(2, 5),
    B(5, 6.5, 6.5, 7, 9.5, 9.5, sk_red, name="pepper"),
    fish_chunk_box(7, 10),
    B(10, 6.5, 6.5, 12, 9.5, 9.5, sk_onion, name="onion"),
    fish_chunk_box(12, 15),
], "view": {"el": 25, "az": 15, "roll": 40}}

# ---- herring sandwich
hs_crust = mat("hs_crust", "#c68a45", "crumb", raw_colour="#ead9b4", raw_pattern="dough")
hs_crumb = mat("hs_crumb", "#f1dfae", "crumb", raw_colour="#f1e6c6", raw_pattern="dough")
hs_lettuce = mat("hs_lettuce", "#7fc04a", "stripe", raw_colour="#9ccf72")
hs_herring = mat("hs_herring", "#8aa3bc", "scale2", raw_colour="#b3c4d3")
hs_herring_side = mat("hs_herring_side", "#e7d6c2", "flake", raw_colour="#f3b9ae", raw_pattern="marble")
hs_tomato = mat("hs_tomato", "#d6402e", "speck", raw_colour="#e07a68", specks=[("#f4d28a", 0.05)])
rag = octa(14, 14, 3)
for cell in ((0, 5), (0, 8), (13, 4), (13, 9), (5, 0), (9, 0), (4, 13), (8, 13), (11, 13)):
    rag.discard(cell)
DISHES["herring_sandwich"] = {"items": [
    L(0, 2, rows_of((12, 12), (octa(12, 12, 1), "b")), (2, 2), {"b": (hs_crust, {"up": hs_crumb})}),
    L(2, 2.5, rows_of((14, 14), (rag, "l")), (1, 1), {"l": hs_lettuce}),
    L(2.5, 3.5, rows_of((14, 14), (rect(4, 10, 2, 2), "h"), (rect(4, 10, 8, 4), "h")), (1, 1),
      {"h": (hs_herring, {"north": hs_herring_side, "south": hs_herring_side, "east": hs_herring_side,
                          "west": hs_herring_side})}),
    L(3.5, 4, rows_of((14, 14), (octa(4, 4, 1, 3, 9), "t"), (octa(4, 4, 1, 9, 2), "t")), (1, 1), {"t": hs_tomato}),
    L(4, 6, rows_of((12, 12), (octa(12, 12, 1), "b")), (2, 2), {"b": hs_crust}),
    L(6, 7, rows_of((10, 10), (octa(10, 10, 2), "b")), (3, 3), {"b": hs_crust}),
    B(8, 6, 8, 8.5, 12, 8.5, sk_stick, name="pick"),
    B(8.5, 10, 8, 10.5, 12, 8.5, sk_red, name="flag"),
], "view": {"el": 28, "az": 25}}

# ---- herring nigiri
hn_top = mat("hn_top", "#8fa9c0", "scale2", raw_colour="#b4c6d6")
hn_side = mat("hn_side", "#efe0cf", "flake", raw_colour="#f3b9ae", raw_pattern="marble")
DISHES["herring_nigiri"] = {"items": [
    L(0, 1, rows_of((10, 16), (octa(10, 16, 1), "w")), (0, 3), {"w": (board, {"east": board_edge, "west": board_edge,
                                                                         "north": board_edge, "south": board_edge})}),
    *nigiri(1.5, 4.5, hn_top, hn_side, band=kelp),
    *nigiri(8.5, 7, hn_top, hn_side, band=kelp),
    B(12.5, 1, 4, 14.5, 2.5, 6, wasabi, name="wasabi"),
    B(2, 1, 10.5, 4, 1.5, 12, ginger, name="ginger"),
    B(4, 1, 10.5, 6, 1.5, 12, ginger, name="ginger2"),
], "view": {"el": 38, "az": 22}}

# ---- fish 'n' chips
fc_paper = mat("fc_paper", "#e2d9ba", "paper", ink="#8a867a")
fc_chip = mat("fc_chip", "#e8bf55", "crumb", raw_colour="#ebe0a6", raw_pattern="dough")
fc_chip_end = mat("fc_chip_end", "#f4e2a0", "speck", raw_colour="#f0eac0")
fc_batter = mat("fc_batter", "#d79a38", "crumb", raw_colour="#e6c9a0", raw_pattern="dough",
                specks=[("#a8641c", 0.04)], raw_specks=[("#f0b4a4", 0.04)])
fc_batter_top = mat("fc_batter_top", "#f1e2c2", "flake", raw_colour="#f6c4b6", raw_pattern="marble")
cone_n = 10
cone = [
    L(0, 2, rows_of((10, 10), (octa(4, 4, 1, 3, 3), "p")), (3, 3), {"p": fc_paper}),
    L(2, 4, rows_of((10, 10), (octa(6, 6, 1, 2, 2), "p")), (3, 3), {"p": fc_paper}),
    L(4, 6, rows_of((10, 10), (octa(8, 8, 2, 1, 1), "p")), (3, 3), {"p": fc_paper}),
    L(6, 8, rows_of((10, 10), (octa(10, 10, 2), "p")), (3, 3), {"p": fc_paper}),
    L(8, 9, rows_of((10, 10), (octa(10, 10, 2), "p"), (shrink(octa(10, 10, 2)), "c")), (3, 3),
      {"p": fc_paper, "c": fc_chip}),
]
chips = [(4.5, 4.5, 4), (5.5, 7, 5), (6.5, 5.5, 3), (7.5, 9, 4.5), (9, 5, 3.5), (9.5, 7, 5), (8.5, 10, 4),
         (10.5, 9, 3), (5, 10, 3.5), (7, 4, 4.5), (11, 6, 4)]
for n, (cx, cz, h) in enumerate(chips):
    cone.append(B(cx, 9, cz, cx + 1, 9 + h, cz + 1, fc_chip, {"up": fc_chip_end}, name="chip%d" % n))
DISHES["fish_and_chips"] = {"items": cone + [
    B(4.5, 9, 6, 8.5, 14, 8, fc_batter, {"up": fc_batter_top}, name="fish1"),
    B(7.5, 9, 7.5, 11.5, 13, 9.5, fc_batter, {"up": fc_batter_top}, name="fish2"),
    B(10.5, 9, 10, 12.5, 10.5, 12, lemon, name="lemon"),
], "view": {"el": 24, "az": 20, "fov": 34}}

# ---- fisherman's stew
b_items, top = bowl(bowl_wood, bowl_wood_rim, broth_fisher, h=3)
DISHES["fishermans_stew"] = {"items": b_items + [
    B(4.5, top, 5, 6.5, top + 1, 7, carrot, name="c1"),
    B(8, top, 4.5, 10, top + 0.5, 6, carrot, name="c2"),
    B(6, top, 8.5, 8, top + 1, 10.5, potato, name="p1"),
    B(9.5, top, 9, 11.5, top + 0.5, 11, potato, name="p2"),
    B(8.5, top, 6.5, 10.5, top + 1, 8.5, fish_chunk, {s: fish_chunk_side for s in ("east", "west", "north", "south")},
      name="f1"),
    B(4.5, top, 9, 6.5, top + 0.5, 11, fish_chunk, name="f2"),
    B(7, top, 6, 8, top + 0.5, 7, herb, name="h1"),
], "view": {"el": 32, "az": 25}}

# ---- fish cakes
ck_top = mat("ck_top", "#d8963e", "crumb", raw_colour="#e8d2b4", raw_pattern="dough",
             specks=[("#9e5a1c", 0.04)], raw_specks=[("#f0a898", 0.05)])
ck_side = mat("ck_side", "#c98532", "crumb", raw_colour="#e2c8a4", raw_pattern="dough",
              blobs=[(["#f2e0c0", "#f0c8a0"], 1.2)], raw_blobs=[(["#f0a898", "#f4b8aa"], 1.6)])
ck_sauce = mat("ck_sauce", "#f1ecdc", "speck", raw_colour="#f1ecdc", specks=[("#6ba84c", 0.08)])
pl = plate(plate_white, plate_rim, 12, 15)
DISHES["fish_cakes"] = {"items": pl + [
    L(2, 4, rows_of((8, 8), (octa(8, 8, 2), "k")), (4, 4), {"k": (ck_side, {"up": ck_top})}),
    L(4, 6, rows_of((8, 8), (octa(8, 8, 2), "k")), (5, 3), {"k": (ck_side, {"up": ck_top})}),
    L(6, 8, rows_of((8, 8), (octa(8, 8, 2), "k")), (3.5 + 0.5, 4.5 + 0.5), {"k": (ck_side, {"up": ck_top})}),
    B(6, 8, 6, 9, 9, 9, ck_sauce, name="sauce"),
    B(7, 9, 7, 8, 9.5, 8.5, herb, name="dill"),
    B(11.5, 2, 9.5, 14.5, 4, 11.5, lemon, {"up": lemon_pith}, name="lemon"),
], "view": {"el": 34, "az": 25}}

# ---- herb-baked trout
tr_skin = mat("tr_skin", "#b8924a", "scale2", raw_colour="#9fb1ba", specks=[("#4f8f3a", 0.1), ("#e8d89a", 0.02)],
              raw_specks=[("#3a4a52", 0.05)])
tr_belly = mat("tr_belly", "#ead9a8", "scale2", raw_colour="#d6dee2")
tr_back = mat("tr_back", "#8a6a30", "scale2", raw_colour="#6f8794", specks=[("#4f8f3a", 0.12)], raw_specks=[("#2a3a42", 0.06)])
tr_head = mat("tr_head", "#a07e3c", "scale2", raw_colour="#8aa0ab")
tr_fin = mat("tr_fin", "#8a6a30", "stripe", raw_colour="#7f939e")
tr_platter = mat("tr_platter", "#ece8dc", "ceramic")
tr_rim = mat("tr_rim", "#7fae6a", "ceramic", bevel=False)
tr_potato = mat("tr_potato", "#d9a94a", "crumb", raw_colour="#ece2b0", raw_pattern="dough")
tr_grid = ["....BBBBBBB....",
           "..HHBBBBBBBBB.F",
           ".HHHBBBBBBBBBFF",
           "HHHHBBBBBBBBBF.",
           ".HHHBBBBBBBBBFF",
           "..HHBBBBBBBBB.F",
           "....BBBBBBB...."]
tr_cells = {(r, c): ch for r, row in enumerate(tr_grid) for c, ch in enumerate(row) if ch != "."}
tr_body = {k for k, v in tr_cells.items() if v in "BH"}
tr_upper = shrink(tr_body)
tr_tail = {k for k, v in tr_cells.items() if v == "F"}
tr_main = {k for k, v in tr_cells.items() if v in "BH"}
tr_head_cells = {k for k, v in tr_cells.items() if v == "H"}
DISHES["herb_trout"] = {"items": plate(tr_platter, tr_rim, 14, 16) + [
    L(2, 4, rows_of((7, 15), (tr_main - tr_head_cells, "B"), (tr_head_cells, "H")), (0.5, 4.5),
      {"B": (tr_skin, {"north": tr_belly, "south": tr_belly}), "H": (tr_head, {"north": tr_belly, "south": tr_belly})}),
    L(2, 3, rows_of((7, 15), (tr_tail, "F")), (0.5, 4.5), {"F": tr_fin}),
    L(4, 5.5, rows_of((7, 15), (tr_upper - tr_head_cells, "U"), (tr_upper & tr_head_cells, "H")), (0.5, 4.5),
      {"U": (tr_back, {"north": tr_skin, "south": tr_skin}), "H": tr_head}),
    L(5.5, 6.5, rows_of((7, 15), ({(3, c) for c in range(5, 12)}, "S")), (0.5, 4.5), {"S": tr_fin}),
    L(6.5, 7.5, rows_of((7, 15), ({(3, c) for c in range(5, 9)}, "S")), (0.5, 4.5), {"S": tr_fin}),
    B(2, 3, 5, 3, 4, 5.5, black, name="eye1"), B(2, 3, 11, 3, 4, 11.5, black, name="eye2"),
    B(8, 2, 3, 13, 2.5, 4, herb, name="fern"), B(9, 2.5, 3.5, 10, 3, 4, herb, name="fern2"),
    B(11, 2.5, 3.5, 12, 3, 4, herb, name="fern3"),
    B(3, 2, 12, 5, 3.5, 14, tr_potato, name="pot1"), B(6, 2, 12.5, 8, 3.5, 14, tr_potato, name="pot2"),
    B(9, 2, 12, 11, 3, 14, tr_potato, name="pot3"),
    B(12, 2, 11.5, 14.5, 2.5, 14, lemon, {"north": lemon_pith, "south": lemon_pith, "east": lemon_pith, "west": lemon_pith}, name="lemon"),
], "view": {"el": 32, "az": 22}}

# ---- bog stew
bg_bowl = mat("bg_bowl", "#7a4a34", "ceramic", band=("#4f6b3a", 0.2, 0.55))
bg_rim = mat("bg_rim", "#5d3826", "ceramic", bevel=False)
bg_broth = mat("bg_broth", "#5d5a2c", "liquid", level=3.2, raw_colour="#8d8e70",
               blobs=[(["#8a7a3a", "#a89a50"], 1.5), (["#3f5a22", "#4a6a28"], 1.2), (["#c8c280"], 0.7)],
               raw_blobs=[(["#b0b090", "#c0c0a0"], 1.5), (["#8a9a70"], 1.0)])
bg_cap = mat("bg_cap", "#7a4a26", "speck", raw_colour="#a07a58", specks=[("#c8985a", 0.06)])
bg_stem = mat("bg_stem", "#e8d8b8", "stripe", raw_colour="#efe3c8")
bg_cat = mat("bg_cat", "#4a4540", "speck", raw_colour="#6a6a68")
bg_cat_top = mat("bg_cat_top", "#e6dcc8", "flake", raw_colour="#f1b7ac", raw_pattern="marble")
b_items, top = bowl(bg_bowl, bg_rim, bg_broth, h=3)
DISHES["bog_stew"] = {"items": b_items + [
    B(5.5, top, 5.5, 6.5, top + 1, 6.5, bg_stem, name="s1"), B(4.5, top + 1, 4.5, 7.5, top + 1.5, 7.5, bg_cap, name="cap1"),
    B(9.5, top, 9, 10.5, top + 0.5, 10, bg_stem, name="s2"), B(8.5, top + 0.5, 8, 11.5, top + 1, 11, bg_cap, name="cap2"),
    B(8, top, 5, 10, top + 1, 7, bg_cat, {"up": bg_cat_top}, name="cat1"),
    B(4.5, top, 9, 6.5, top + 0.5, 11, bg_cat, {"up": bg_cat_top}, name="cat2"),
    B(7, top, 8, 8, top + 1.5, 9, herb, name="reed"),
], "view": {"el": 32, "az": 25}}

# ---- frost chowder
fr_bowl = mat("fr_bowl", "#e6eef4", "ceramic", band=("#5a9ad6", 0.25, 0.6))
fr_rim = mat("fr_rim", "#b8d4ea", "ceramic", bevel=False)
fr_soup = mat("fr_soup", "#ebe4d2", "liquid", level=4.0, raw_colour="#e0e6e4",
              blobs=[(["#ffffff", "#e8eef0"], 2.2), (["#e3d29a", "#d4bf80"], 2.0), (["#4f9a4f", "#3f8a44"], 1.2),
                     (["#bfe6f5", "#8fd0ea"], 1.0), (["#c8a8a0"], 0.5)],
              raw_blobs=[(["#f4b8aa", "#f1c8c0"], 1.6), (["#ece0b0"], 1.0), (["#d8ecf4"], 0.6)])
fr_cracker = mat("fr_cracker", "#d9b878", "speck", raw_colour="#e8d8b0")
fr_ice = mat("fr_ice", "#bfeaf8", "glow", raw_colour="#cfe8f0", sparkle="#e8fbff", raw_sparkle=None)
b_items, top = bowl(fr_bowl, fr_rim, fr_soup)
DISHES["frost_chowder"] = {"items": b_items + [
    B(4.5, top, 5, 6.5, top + 0.5, 7, fr_cracker, name="cr1"),
    B(9, top, 9.5, 11, top + 0.5, 11.5, fr_cracker, name="cr2"),
    B(8, top, 5.5, 9, top + 2.5, 6.5, fr_ice, name="ice1"),
    B(9, top, 6, 10, top + 1.5, 7, fr_ice, name="ice2"),
    B(7, top, 7.5, 8, top + 1, 8.5, fr_ice, name="ice3"),
    B(5.5, top, 9, 7.5, top + 0.5, 10.5, fish_chunk, name="cod"),
], "view": {"el": 32, "az": 25}}

# ---- wrasse maki
mk_mat = mat("mk_mat", "#d8c58a", "mat", raw_colour="#d8c58a")
mk_wrasse = mat("mk_wrasse", "#e8607a", "speck", raw_colour="#f0a0b0")
mk_cucumber = mat("mk_cucumber", "#6ab04c", "speck", raw_colour="#86c06a")
mk_edge = hard("mk_edge", "wood", "#a88a52")
DISHES["wrasse_maki"] = {"items": [
    L(0, 1, rows_of((14, 16), (octa(14, 16, 2), "m")), (0, 1), {"m": (mk_mat, {"north": mk_edge, "south": mk_edge,
                                                                           "east": mk_edge, "west": mk_edge})}),
    *maki_piece(1.5, 2, nori, rice, mk_wrasse, mk_cucumber),
    *maki_piece(8.5, 2, nori, rice, mk_wrasse, mk_cucumber),
    *maki_piece(5, 8.5, nori, rice, mk_wrasse, mk_cucumber),
], "view": {"el": 40, "az": 22}}

# ---- coral sushi
hard("sapphire", "gem", "#2d6bdc")
hard("ruby", "gem", "#cf1f3a")
hard("amethyst", "gem", "#9f6ae6")
hard("emerald", "gem", "#2faa5a")
cs_plate = mat("cs_plate", "#f0a590", "ceramic")
cs_rim = mat("cs_rim", "#e0705a", "ceramic", bevel=False)
cs_fish = mat("cs_fish", "#f08a68", "marble", raw_colour="#f6b9a8", shade=1.0)
cs_fish_side = mat("cs_fish_side", "#f4c4a6", "flake", raw_colour="#f8d0c4", raw_pattern="marble")
cs_fill = mat("cs_fill", "#f26a7c", "marble", raw_colour="#f4a0ac")
cs_fill2 = mat("cs_fill2", "#f5b94a", "speck", raw_colour="#f4d68a")
coral_a = mat("coral_a", "#e8588a", "speck", raw_colour="#e8588a")
coral_b = mat("coral_b", "#f08a3a", "speck", raw_colour="#f08a3a")
pearl = mat("pearl", "#f4f0f4", "glow", raw_colour="#e8e4ea", sparkle="#ffffff", raw_sparkle=None)
DISHES["coral_sushi"] = {"items": plate(cs_plate, cs_rim, 14, 16) + [
    *maki_piece(2, 2.5, nori, rice, cs_fill, cs_fill2, y0=2),
    *maki_piece(2, 8.5, nori, rice, cs_fill, cs_fill2, y0=2),
    *nigiri(9, 3, cs_fish, cs_fish_side, band=kelp, y0=2),
    *nigiri(9, 9.5, cs_fish, cs_fish_side, band=kelp, y0=2),
    B(10.5, 2, 7.5, 11.5, 5, 8.5, coral_a, name="co1"), B(12, 2, 7.5, 13, 4, 8.5, coral_b, name="co2"),
    B(13.5, 2, 7.5, 14.5, 5.5, 8.5, coral_a, name="co3"), B(10.5, 5, 7.5, 11.5, 6, 8.5, coral_b, name="co4"),
    B(12, 4, 7.5, 13, 4.5, 8.5, pearl, name="pearl"),
], "view": {"el": 40, "az": 22}}

# ---- ember skewer
em_rod = mat("em_rod", "#e8a02a", "stripe", raw_colour="#c8903a", specks=[("#ffd860", 0.06)], raw_specks=[])
em_char = mat("em_char", "#3d2519", "grill", raw_colour="#f1b4a2", raw_pattern="marble",
              specks=[("#ff6a1c", 0.06), ("#ffb030", 0.02)], raw_specks=[])
em_flesh = mat("em_flesh", "#e98a52", "flake", raw_colour="#f6c2b4", raw_pattern="marble")
em_pepper = mat("em_pepper", "#b32a1c", "speck", raw_colour="#d9604e", specks=[("#ff7a28", 0.05)], raw_specks=[])
em_flame = mat("em_flame", "#ffb52e", "glow", raw_invisible=True)
em_flame2 = mat("em_flame2", "#ff5a1c", "glow", raw_invisible=True)


def ember_chunk(x0, x1):
    return B(x0, 6, 6, x1, 10, 10, em_char, {"west": em_flesh, "east": em_flesh}, name="perch")


DISHES["ember_skewer"] = {"items": [
    B(0, 7.5, 7.5, 16, 8.5, 8.5, em_rod, name="rod"),
    ember_chunk(2, 5), B(5, 6.5, 6.5, 7, 9.5, 9.5, em_pepper, name="pepper"),
    ember_chunk(7, 10), B(10, 6.5, 6.5, 12, 9.5, 9.5, em_pepper, name="pepper2"), ember_chunk(12, 15),
    B(3, 10, 7.5, 4, 11.5, 8.5, em_flame, name="f1"), B(4, 10, 8, 4.5, 11, 8.5, em_flame2, name="f1b"),
    B(2.5, 10, 8, 3, 10.5, 9, em_flame2, name="f1c"),
    B(8, 10, 7, 9, 12, 8, em_flame, name="f2"), B(8, 10, 8, 9.5, 11, 9, em_flame2, name="f2b"),
    B(7.5, 10, 7, 8, 11, 7.5, em_flame2, name="f2c"),
    B(13, 10, 7.5, 14, 11, 8.5, em_flame, name="f3"), B(12.5, 10, 8.5, 13, 10.5, 9, em_flame2, name="f3b"),
], "view": {"el": 25, "az": 15, "roll": 40}}

# ---- glow broth
gb_bowl = mat("gb_bowl", "#2f8a8a", "ceramic", band=("#7fd0c8", 0.2, 0.45))
gb_rim = hard("gb_rim", "metal", "#c3ccd6")
gb_foot = hard("gb_foot", "metal", "#a8b4c0")
gb_broth = mat("gb_broth", "#8fe0a8", "liquid", level=4.6, raw_colour="#9aa89c", sparkle="#e8ffd0", raw_sparkle=None,
               blobs=[(["#ffb43a", "#ffc860"], 1.2), (["#c8ffd8", "#6af0d0"], 1.4), (["#3a9a8a"], 0.8)],
               raw_blobs=[(["#b8a078"], 1.0), (["#c4d0c4"], 1.2), (["#7aa098"], 0.8)])
gb_berry = mat("gb_berry", "#ffa628", "glow", raw_colour="#c88a4a", sparkle="#fff0a0", raw_sparkle=None)
gb_goby = mat("gb_goby", "#4fd0b8", "scale2", raw_colour="#7a9a94")
gb_goby_top = mat("gb_goby_top", "#d6fff0", "flake", raw_colour="#f1b7ac", raw_pattern="marble")
b_items, top = bowl(gb_bowl, gb_rim, gb_broth, foot=gb_foot)
DISHES["glow_broth"] = {"items": b_items + [
    B(4.5, top, 5.5, 6, top + 1, 7, gb_berry, name="b1"), B(8.5, top, 4.5, 10, top + 0.5, 6, gb_berry, name="b2"),
    B(7, top, 9, 8.5, top + 1, 10.5, gb_berry, name="b3"), B(10, top, 8, 11, top + 0.5, 9, gb_berry, name="b4"),
    B(6.5, top, 6.5, 8.5, top + 1, 8.5, gb_goby, {"up": gb_goby_top}, name="g1"),
    B(9, top, 9.5, 11, top + 0.5, 11.5, gb_goby, {"up": gb_goby_top}, name="g2"),
], "view": {"el": 32, "az": 25}}

# ---- abyssal broth
ab_bowl = mat("ab_bowl", "#2c4a8c", "ceramic", band=("#5a86c8", 0.2, 0.45))
ab_rim = hard("ab_rim", "metal", "#e2b13c")
ab_foot = hard("ab_foot", "metal", "#b98a22")
ab_broth = mat("ab_broth", "#123a5e", "liquid", level=2.6, raw_colour="#2a3a52",
               specks=[("#5be7ff", 0.012)], raw_specks=[],
               blobs=[(["#3ad0f0", "#7af0ff"], 0.9), (["#2a3a7a"], 1.2)], raw_blobs=[(["#4a5a72"], 1.2)])
ab_dark = mat("ab_dark", "#262040", "speck", raw_colour="#4a4a5a")
ab_dark_top = mat("ab_dark_top", "#d8d0e8", "flake", raw_colour="#f1b7ac", raw_pattern="marble")
ab_shell = mat("ab_shell", "#f1dcc8", "stripe", raw_colour="#e4d2c0", specks=[("#e8a0a8", 0.08)])
ab_lure = mat("ab_lure", "#5be7ff", "glow", raw_colour="#6a8a96", sparkle="#e8ffff", raw_sparkle=None)
b_items, top = bowl(ab_bowl, ab_rim, ab_broth, foot=ab_foot)
DISHES["abyssal_broth"] = {"items": b_items + [
    B(5, top, 5, 8, top + 1, 8, ab_shell, name="shell"), B(5.5, top + 1, 5.5, 7.5, top + 2, 7.5, ab_shell, name="shell2"),
    B(6, top + 2, 6, 7, top + 2.5, 7, ab_shell, name="shell3"),
    B(9, top, 9, 10, top + 3, 10, ab_dark, name="stalk"), B(9.5, top + 3, 8.5, 10.5, top + 3.5, 9.5, ab_dark, name="stalk2"),
    B(10, top + 3, 8.5, 11, top + 4, 9.5, ab_lure, name="lure"),
    B(8.5, top, 5.5, 10.5, top + 1, 7.5, ab_dark, {"up": ab_dark_top}, name="p1"),
    B(5, top, 9.5, 7, top + 0.5, 11.5, ab_dark, {"up": ab_dark_top}, name="p2"),
], "view": {"el": 32, "az": 25}}

# ---- void sashimi
vs_plate = mat("vs_plate", "#2a1a3d", "ceramic")
vs_rim = mat("vs_rim", "#8a5ad0", "ceramic", bevel=False)
vs_flesh = mat("vs_flesh", "#3a2a70", "marble", raw_colour="#2a2650", specks=[("#5ff0ff", 0.05)], raw_specks=[("#3a6a8a", 0.03)],
               shade=0.9)
vs_front = mat("vs_front", "#7a62b8", "flake", raw_colour="#4a4a8a", raw_pattern="marble", specks=[("#5ff0ff", 0.05)], raw_specks=[])
vs_edge = mat("vs_edge", "#1e1840", "speck", raw_colour="#1c1a38", specks=[("#5ff0ff", 0.04)], raw_specks=[])
vs_daikon = mat("vs_daikon", "#e8f2f4", "stripe", raw_colour="#e8f2f4")
vs_chorus = mat("vs_chorus", "#b78ad0", "speck", raw_colour="#c8a8d6", specks=[("#f0d0ff", 0.05)], raw_specks=[])
vs_chorus2 = mat("vs_chorus2", "#8a5ab0", "speck", raw_colour="#a888c0")
vs_star = mat("vs_star", "#aef6ff", "glow", raw_invisible=True)
sl = []
for i in range(5):
    sl.append(B(4, 6 - i, 3.5 + i * 2, 12, 7 - i, 7.5 + i * 2, vs_flesh,
                {"north": vs_edge, "south": vs_front, "east": vs_edge, "west": vs_edge}, name="slice%d" % i))
DISHES["void_sashimi"] = {"items": plate(vs_plate, vs_rim, 14, 16) + [
    B(4.5, 2, 3, 11.5, 6, 6.5, vs_daikon, name="daikon"), *sl,
    B(12.5, 2, 9, 14.5, 4, 11, vs_chorus, name="ch1"), B(13, 4, 9.5, 14, 5, 10.5, vs_chorus2, name="ch2"),
    B(2, 2, 9, 3.5, 3.5, 10.5, vs_chorus, name="ch3"),
    B(7.5, 7, 6, 8.5, 8, 7, vs_star, name="star1"), B(5, 6.5, 8.5, 5.5, 7, 9, vs_star, name="star2"),
    B(10.5, 7.5, 9, 11.5, 8, 9.5, vs_star, name="star3"),
], "view": {"el": 38, "az": 22}}

# ---- blazefin roll
bz_plate = mat("bz_plate", "#2b2b32", "ceramic")
bz_rim = mat("bz_rim", "#e8601c", "ceramic", bevel=False)
bz_roe = mat("bz_roe", "#ff7a2a", "speck", raw_colour="#f0a070", specks=[("#ffd060", 0.08), ("#c83c10", 0.06)],
             raw_specks=[("#f4c8a0", 0.05)])
bz_fill = mat("bz_fill", "#f0501c", "marble", raw_colour="#f4a890")
bz_fill2 = mat("bz_fill2", "#ffc43a", "speck", raw_colour="#f4dc90")
bz_flame = mat("bz_flame", "#ffb52e", "glow", raw_invisible=True)
bz_flame2 = mat("bz_flame2", "#ff4a14", "glow", raw_invisible=True)
DISHES["blazefin_roll"] = {"items": plate(bz_plate, bz_rim, 14, 16) + [
    *maki_piece(1.5, 2, bz_roe, rice, bz_fill, bz_fill2, y0=2, h=3.5, raise_fill=1),
    *maki_piece(8.5, 2, bz_roe, rice, bz_fill, bz_fill2, y0=2, h=3.5, raise_fill=1),
    *maki_piece(5, 8.5, bz_roe, rice, bz_fill, bz_fill2, y0=2, h=3.5, raise_fill=1),
    B(4, 6.5, 4, 5, 8.5, 5, bz_flame, name="f1"), B(4.5, 6.5, 5, 5.5, 7.5, 6, bz_flame2, name="f1b"),
    B(11, 6.5, 4, 12, 9, 5, bz_flame, name="f2"), B(10.5, 6.5, 5, 11.5, 8, 6, bz_flame2, name="f2b"),
    B(7.5, 6.5, 10.5, 8.5, 8.5, 11.5, bz_flame, name="f3"), B(8.5, 6.5, 11, 9.5, 7.5, 12, bz_flame2, name="f3b"),
], "view": {"el": 40, "az": 22}}

# ---- golden fish pie
gp_dish = mat("gp_dish", "#f1ece0", "ceramic")
gp_gold = hard("gp_gold", "metal", "#e2b13c")
gp_crust = mat("gp_crust", "#d9a04a", "crumb", raw_colour="#ecdcb6", raw_pattern="dough",
               specks=[("#ffe066", 0.05), ("#a8641c", 0.03)], raw_specks=[("#e8d0a0", 0.03)])
gp_crust_side = mat("gp_crust_side", "#c98a38", "crumb", raw_colour="#e6d2a8", raw_pattern="dough")
gp_strip = mat("gp_strip", "#f0bc58", "crumb", raw_colour="#f1e4c0", raw_pattern="dough",
               specks=[("#fff0a0", 0.06), ("#b8761c", 0.03)], raw_specks=[])
gp_fill = mat("gp_fill", "#c8a070", "speck", raw_colour="#e0a8a0", specks=[("#6aa04a", 0.1), ("#f4e0a0", 0.06)],
              raw_specks=[("#6aa04a", 0.08)])
gp_fin = mat("gp_fin", "#ffd24a", "scale2", raw_colour="#c8b890")
pie_oct = octa(10, 10, 2)
pie_rows = lambda idx: rows_of((10, 10), ({(r, c) for r, c in pie_oct if idx(r, c)}, "s"))
DISHES["golden_fish_pie"] = {"items": [
    L(0, 1, rows_of((14, 14), (octa(10, 10, 2, 2, 2), "d")), (1, 1), {"d": gp_dish}),
    L(1, 2.5, rows_of((14, 14), (octa(14, 14, 3), "d"), (octa(14, 14, 3) - shrink(octa(14, 14, 3)), "g")), (1, 1),
      {"d": gp_dish, "g": gp_gold}),
    L(2.5, 4.5, rows_of((12, 12), (octa(12, 12, 2), "k")), (2, 2), {"k": (gp_crust_side, {"up": gp_crust})}),
    L(4.5, 5, rows_of((10, 10), (pie_oct, "f")), (3, 3), {"f": gp_fill}),
    L(5, 5.5, pie_rows(lambda r, c: r % 2 == 1), (3, 3), {"s": gp_strip}),
    L(5.5, 6, pie_rows(lambda r, c: c % 2 == 1), (3, 3), {"s": gp_strip}),
    L(6, 7, ["..GGGG.F", ".GGGGGFF", "GGGGGGGF", ".GGGGGFF", "..GGGG.F"], (4, 5.5),
      {"G": gp_fin, "F": gp_fin}),
    B(4.5, 7, 7, 5.5, 7.5, 8, black, name="eye"),
], "view": {"el": 38, "az": 25}}

# ---- starlight soup
sl_body = hard("sl_body", "metal", "#e2b13c")
sl_rim = hard("sl_rim", "metal", "#f0c85a")
sl_foot = hard("sl_foot", "metal", "#b98a22")
sl_soup = mat("sl_soup", "#25307a", "liquid", level=2.8, raw_colour="#4a5278",
              specks=[("#fff2a8", 0.03), ("#ffffff", 0.01)], raw_specks=[],
              blobs=[(["#6a5ad0", "#4a7ae0"], 1.4), (["#fff2a8"], 0.5)], raw_blobs=[(["#6a6a98"], 1.2)])
sl_star = mat("sl_star", "#ffe27a", "glow", raw_colour="#d8c890", sparkle="#ffffff", raw_sparkle=None)
sl_star2 = mat("sl_star2", "#ffffff", "glow", raw_colour="#e8e0c8")
sl_float = mat("sl_float", "#fff0a0", "glow", raw_invisible=True)
sl_ray = mat("sl_ray", "#1c1c58", "speck", raw_colour="#4a4a6a", specks=[("#ffffff", 0.04)], raw_specks=[])
sl_ray_top = mat("sl_ray_top", "#e8e0f4", "flake", raw_colour="#f1b7ac", raw_pattern="marble")
b_items, top = bowl(sl_body, sl_rim, sl_soup, foot=sl_foot, gem="sapphire", h=3)
DISHES["starlight_soup"] = {"items": b_items + [
    B(6, top, 6, 7, top + 1.5, 7, sl_star, name="s0"), B(5, top, 6, 6, top + 1, 7, sl_star, name="s1"),
    B(7, top, 6, 8, top + 1, 7, sl_star, name="s2"), B(6, top, 5, 7, top + 1, 6, sl_star, name="s3"),
    B(6, top, 7, 7, top + 1, 8, sl_star, name="s4"),
    B(10, top, 8.5, 11, top + 1, 9.5, sl_star2, name="t0"), B(9.5, top, 9, 10, top + 0.5, 9.5, sl_star2, name="t1"),
    B(11, top, 9, 11.5, top + 0.5, 9.5, sl_star2, name="t2"),
    B(8.5, top, 5, 10.5, top + 1, 7, sl_ray, {"up": sl_ray_top}, name="ray"),
    B(5.5, top, 9, 7.5, top + 0.5, 11, sl_ray, {"up": sl_ray_top}, name="ray2"),
    B(11, top + 3, 4, 12, top + 4, 5, sl_float, name="fs0"), B(10, top + 3, 4, 11, top + 4, 5, sl_float, name="fs1"),
    B(12, top + 3, 4, 13, top + 4, 5, sl_float, name="fs2"), B(11, top + 4, 4, 12, top + 5, 5, sl_float, name="fs3"),
    B(11, top + 2, 4, 12, top + 3, 5, sl_float, name="fs4"),
    B(3, top + 2, 6, 4, top + 2.5, 7, sl_float, name="fs5"),
], "view": {"el": 32, "az": 25}}

# ---- crowned marlin nigiri
mn_tray = mat("mn_tray", "#1a1a24", "ceramic")
mn_rim = hard("mn_rim", "metal", "#e2b13c")
mn_top = mat("mn_top", "#c4293c", "marble", raw_colour="#d86a74", shade=1.0)
mn_side = mat("mn_side", "#8a1a2c", "flake", raw_colour="#e8a0a8", raw_pattern="marble")
mn_nugget = hard("mn_nugget", "metal", "#ffd24a")
mn_crown = hard("mn_crown", "metal", "#e2b13c")
DISHES["marlin_nigiri"] = {"items": plate(mn_tray, mn_rim, 14, 16) + [
    *nigiri(1.5, 8.5, mn_top, mn_side, band=kelp, y0=2),
    *nigiri(8.5, 8.5, mn_top, mn_side, band=kelp, y0=2),
    B(3.5, 5.5, 9.5, 5.5, 6.5, 11, mn_nugget, name="nug1"), B(10.5, 5.5, 9.5, 12.5, 6.5, 11, mn_nugget, name="nug2"),
    B(4.5, 2, 3, 11.5, 3.5, 6, mn_crown, name="crown_base"),
    B(4.5, 3.5, 3, 6, 5.5, 4, mn_crown, name="cp1"), B(7, 3.5, 3, 9, 6.5, 4, mn_crown, name="cp2"),
    B(10, 3.5, 3, 11.5, 5.5, 4, mn_crown, name="cp3"),
    B(7.5, 2.5, 6, 8.5, 3.5, 6.5, "sapphire", name="gem_c"), B(5, 2.5, 6, 5.5, 3.5, 6.5, "ruby", name="gem_l"),
    B(10.5, 2.5, 6, 11, 3.5, 6.5, "ruby", name="gem_r"),
], "view": {"el": 40, "az": 22}}

# ---- leviathan feast
lf_plate = mat("lf_plate", "#efe9dc", "ceramic")
lf_rim = hard("lf_rim", "metal", "#e2b13c")
lf_skin = mat("lf_skin", "#2f8a9a", "scale2", raw_colour="#6f8f9a", specks=[("#ffd24a", 0.07), ("#aef6ff", 0.02)],
              raw_specks=[("#3a5a66", 0.04)])
lf_belly = mat("lf_belly", "#f0d88a", "scale2", raw_colour="#c4d2d8")
lf_back = mat("lf_back", "#1f6a82", "scale2", raw_colour="#56707e", specks=[("#ffd24a", 0.08)], raw_specks=[])
lf_head = mat("lf_head", "#2a7a8c", "scale2", raw_colour="#62808c", specks=[("#ffd24a", 0.06)], raw_specks=[])
lf_fin = hard("lf_fin", "metal", "#e2b13c")
lf_fin_raw = mat("lf_fin_raw", "#d9a52e", "stripe", raw_colour="#7a8e98")
lf_tail = mat("lf_tail", "#d9a52e", "stripe", raw_colour="#7a8e98", specks=[("#fff0a0", 0.05)], raw_specks=[])
lf_eye = mat("lf_eye", "#aef6ff", "glow", raw_colour="#6a8a96")
lf_carrot = mat("lf_carrot", "#ffb02e", "speck", raw_colour="#e8a060", specks=[("#fff0a0", 0.08)], raw_specks=[])
lf_leaf = mat("lf_leaf", "#4ea83c", "speck", raw_colour="#6ab058")
lf_sauce = mat("lf_sauce", "#9a2a5a", "liquid", level=3.4, raw_colour="#9a7a8a", sparkle="#ffd0e0", raw_sparkle=None)
lf_grid = ["....BBBBBBB....",
           "..HHBBBBBBBBB.F",
           ".HHHBBBBBBBBBFF",
           "HHHHBBBBBBBBBF.",
           ".HHHBBBBBBBBBFF",
           "..HHBBBBBBBBB.F",
           "....BBBBBBB...."]
lf_cells = {(r, c): ch for r, row in enumerate(lf_grid) for c, ch in enumerate(row) if ch != "."}
lf_body = {k for k, v in lf_cells.items() if v in "BH"}
lf_up = shrink(lf_body)
lf_h = {k for k, v in lf_cells.items() if v == "H"}
lf_f = {k for k, v in lf_cells.items() if v == "F"}
DISHES["leviathan_feast"] = {"items": plate(lf_plate, lf_rim, 15, 16) + [
    L(2, 4, rows_of((7, 15), (lf_body - lf_h, "B"), (lf_h, "H")), (0.5, 4),
      {"B": (lf_skin, {"north": lf_belly, "south": lf_belly}), "H": (lf_head, {"north": lf_belly, "south": lf_belly})}),
    L(2, 3.5, rows_of((7, 15), (lf_f, "F")), (0.5, 4), {"F": lf_tail}),
    L(4, 5.5, rows_of((7, 15), (lf_up - lf_h, "U"), (lf_up & lf_h, "H")), (0.5, 4),
      {"U": (lf_back, {"north": lf_skin, "south": lf_skin}), "H": lf_head}),
    L(5.5, 6.5, rows_of((7, 15), ({(3, c) for c in range(5, 12)}, "S")), (0.5, 4), {"S": lf_fin_raw}),
    L(6.5, 8, rows_of((7, 15), ({(3, c) for c in range(5, 9)}, "S")), (0.5, 4), {"S": lf_fin_raw}),
    L(6.5, 7.5, rows_of((7, 15), ({(3, c) for c in range(9, 12)}, "S")), (0.5, 4), {"S": lf_fin_raw}),
    B(2, 3.5, 4.5, 3, 4.5, 5, lf_eye, name="eye1"), B(2, 3.5, 11, 3, 4.5, 11.5, lf_eye, name="eye2"),
    B(2.5, 2, 12.5, 7.5, 3.5, 14, lf_carrot, name="carrot1"), B(8.5, 2, 12, 13.5, 3.5, 13.5, lf_carrot, name="carrot2"),
    B(1.5, 2.5, 12.5, 2.5, 3.5, 14, lf_leaf, name="leaf1"), B(13.5, 2.5, 12, 14.5, 3.5, 13.5, lf_leaf, name="leaf2"),
    B(11.5, 2, 2.5, 14.5, 2.5, 5, lf_sauce, name="sauce"),
    B(1.5, 2, 2.5, 4.5, 3, 4, lemon, {"north": lemon_pith, "south": lemon_pith, "east": lemon_pith, "west": lemon_pith}, name="lemon"),
    B(7.5, 2.5, 1, 8.5, 3.5, 2, "ruby", name="g_n"), B(7.5, 2.5, 14, 8.5, 3.5, 15, "sapphire", name="g_s"),
    B(0.5, 2.5, 7.5, 1.5, 3.5, 8.5, "emerald", name="g_w"), B(14.5, 2.5, 7.5, 15.5, 3.5, 8.5, "amethyst", name="g_e"),
], "view": {"el": 40, "az": 24}}

# ---- astral sushi
as_plate = hard("as_plate", "metal", "#d9a52e")
as_rim = hard("as_rim", "metal", "#f0c85a")
as_void = mat("as_void", "#1c1034", "void", raw_colour="#2a2640", specks=[("#c8f4ff", 0.05), ("#8a5ad0", 0.03)],
              raw_specks=[("#8a8aa0", 0.02)])
as_rice = mat("as_rice", "#cfe8f5", "grain", raw_colour="#d9d6cb")
as_fish = mat("as_fish", "#4fe0e8", "marble", raw_colour="#9ab8c0", sparkle="#e8ffff", raw_sparkle=None, shade=1.0)
as_fish_top = mat("as_fish_top", "#5ff0f0", "marble", raw_colour="#a8c4cc", shade=1.0, sparkle="#e8ffff", raw_sparkle=None)
as_fish_side = mat("as_fish_side", "#2aa0b8", "flake", raw_colour="#8aa8b2", raw_pattern="marble")
as_star = mat("as_star", "#aef6ff", "glow", raw_invisible=True)
as_ink = mat("as_ink", "#3a1a70", "glow", raw_colour="#4a4a68", sparkle="#c8f4ff", raw_sparkle=None)
DISHES["astral_sushi"] = {"items": plate(as_plate, as_rim, 14, 16) + [
    *nigiri(1.5, 3, as_fish_top, as_fish_side, band=as_void, y0=2),
    *nigiri(8.5, 3.5, as_fish_top, as_fish_side, band=as_void, y0=2),
    *maki_piece(1.5, 8.5, as_void, as_rice, as_fish, as_fish_top, y0=2, raise_fill=1),
    *maki_piece(8.5, 8.5, as_void, as_rice, as_fish, as_fish_top, y0=2, raise_fill=1),
    B(7, 2, 7.5, 9, 3, 8.5, as_ink, name="ink"),
    B(7.5, 8, 8, 8.5, 9, 9, as_star, name="st1"), B(3.5, 7.5, 7.5, 4, 8, 8, as_star, name="st2"),
    B(11.5, 8, 7.5, 12.5, 9, 8.5, as_star, name="st3"), B(2, 9, 12, 2.5, 9.5, 12.5, as_star, name="st4"),
    B(14, 8, 12, 14.5, 8.5, 12.5, as_star, name="st5"),
    B(0.5, 2.5, 1.5, 1.5, 3.5, 2.5, "amethyst", name="ga"), B(14.5, 2.5, 1.5, 15.5, 3.5, 2.5, "amethyst", name="gb"),
], "view": {"el": 40, "az": 22}}
