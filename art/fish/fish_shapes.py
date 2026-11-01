"""
Side profiles of every fish as character grids (head on the left, -X). One character is one model unit.

  B body  W belly  A accent (flush with the body)  K dark (barbels, stalks)  E pupil  R eye white / teeth
  F fin (1 thick)  G thick fin (2)  S spike / horn / crystal (accent, 2 thick)  Y gold (crown)
  Z glowing spot (flush with the body)  L glowing bulb (sticks out)

A shape is a dict: grid, depth (body thickness, int or list per column), plane "xy" (a fish seen from the side) or
"xz" (a ray, lying flat: rows run along z, thickness is y).
"""
import math


def G(*rows):
    width = max(len(r) for r in rows)
    return [list(r.ljust(width, ".")) for r in rows]


def put(g, r, c, ch):
    if 0 <= r < len(g) and 0 <= c < len(g[0]):
        g[r][c] = ch


def hrow(g, r, c0, c1, ch, only=None):
    for c in range(c0, c1 + 1):
        if 0 <= r < len(g) and 0 <= c < len(g[0]) and (only is None or g[r][c] in only):
            g[r][c] = ch


def spots(g, cells, ch="A"):
    for r, c in cells:
        put(g, r, c, ch)


def show(g):
    return "\n".join("".join(r) for r in g)


def wave(path, thick, head=3, fin=True):
    """A thin sinuous body along a top-row path: B rows over one W row, a fin cell above (and below); (grid, path)."""
    width = len(path)
    top = min(path) - 1
    path = [y - top for y in path]
    height = max(path) + thick + 1
    g = [["."] * width for _ in range(height)]
    for x, y in enumerate(path):
        for k in range(thick):
            g[y + k][x] = "W" if k == thick - 1 else "B"
        if fin and x >= head:
            g[y - 1][x] = "F"
            if x >= head + 2 and x < width - 2:
                g[y + thick][x] = "F"
    return g, path


# ------------------------------------------------------------------ templates

TROUT = ["....FFF.......",
         "..BBBBBBBB..FF",
         ".BEBBBBBBBBFF.",
         ".BBBBBBBBBBFF.",
         ".WWWWWWWWW..FF",
         "....FF........"]

HERRING = ["....FF........",
           "..BBBBBBBB.FFF",
           "BBEBBBBBBBBFF.",
           ".WWWWWWWWW.FFF",
           "....FF........"]

MACKEREL = ["....FFF.FF..F.",
            "..BBBBBBBBB.FF",
            ".BEBBBBBBBBBFF",
            ".BBBBBBBBBBBFF",
            ".WWWWWWWWWW.FF",
            "..WWWWWWWW.F.."]

WRASSE = ["....FFFFFFFF...",
          "...FFFFFFFFFF..",
          "..BBBBBBBBB..FF",
          ".BBBBBBBBBBBFFF",
          "BBEBBBBBBBBBFFF",
          ".WWWWWWWWWWBFFF",
          "..WWWWWWWWW.FFF",
          "....FFF....FFF."]

COD = ["...FFF.FFF....",
       "..BBBBBBBBB.FF",
       ".BBBBBBBBBBBFF",
       "BBEBBBBBBBBBFF",
       ".BBBBBBBBBBBFF",
       ".WWWWWWWWWWWFF",
       ".KWWWFFWWWW.FF"]

LONG = [".........FFF....",
        ".BBBBBBBBBBBB.FF",
        "BBEBBBBBBBBBBFFF",
        "BBBBBBBBBBBBBFF.",
        ".WWWWWWWWWWW.FFF",
        ".....FF...F....."]

DEEP = ["....FFFF......",
        "...FFFFFFF....",
        "..BBBBBBBB..FF",
        ".BBBBBBBBBBFFF",
        "BBEBBBBBBBBFF.",
        "BBBBBBBBBBBFF.",
        ".WWWWWWWWWWFFF",
        "..WWWWWWWW..FF",
        "....FFF......."]

STUBBY = ["....FF....",
          "..BBBBBB.F",
          ".BBBBBBBFF",
          "BRBBBBBBFF",
          "BBBBBBBBFF",
          ".WWWWWWW.F",
          "...FF....."]

CATFISH = ["......FF.F.....",
           "...BBBBBBBB...",
           "..BBBBBBBBBB.F",
           "KKBEBBBBBBBBFF",
           ".KBBBBBBBBBBFF",
           ".KWWWWWWWWWFFF",
           "K.WWWWWWW..FF.",
           "....FF........"]

MARLIN = ["......FFFF....FF",
          ".....FFFFFF...FF",
          ".....FFFFFFF.FF.",
          "....FFFFFFFFFFF.",
          "...BBBBBBBBBBFF.",
          "..BEBBBBBBBBBFF.",
          "KKKBBBBBBBBBBFF.",
          "...WWWWWWWWW.FF.",
          "....WWWWWWW...FF",
          ".......FF....FF."]

ANGLER = [".LL.............",
          ".LLK............",
          "..KKK...........",
          "...BBBBBB.......",
          "..BBBBBBBBB..F..",
          ".BBBBBBBBBBB.FF.",
          ".BRBBBBBBBBBBFFF",
          ".BEBBBBBBBBBBFF.",
          ".WRWRWWWWWWWFFF.",
          "..WWWWWWWWW..F..",
          "......FF........"]

RAY = ["................",   # xz plane: rows are z, the ray's tail runs to the right
       ".....BBBB.......",
       "....BBBBBB......",
       "...BBBBBBBB.....",
       "..BBBBBBBBBB....",
       ".BBBEBBEBBBBB...",
       "BBBBBBBBBBBBB.FF",
       "BBBBBBBBBBBBBFFF",
       "BBBBBBBBBBBBB.FF",
       ".BBBBBBBBBBBB...",
       "..BBBBBBBBBB....",
       "...BBBBBBBB.....",
       "....BBBBBB......",
       ".....BBBB.......",
       "................"]


def shaped(rows, depth=2, plane="xy", ends=(0, 0), dy=0):
    return {"grid": G(*rows), "depth": depth, "plane": plane, "ends": ends, "dy": dy}


# ------------------------------------------------------------------ the fish

def build_all():
    out = {}

    # ---- slim
    s = shaped(TROUT)
    spots(s["grid"], [(1, 4), (1, 7), (2, 5), (2, 8), (3, 6), (3, 9), (1, 9)])
    out["pebble_trout"] = s

    s = shaped(HERRING)
    hrow(s["grid"], 1, 3, 9, "A")
    out["silver_herring"] = s

    s = shaped(MACKEREL)
    for c in (3, 5, 7, 9):
        put(s["grid"], 1, c, "A")
        put(s["grid"], 2, c + 1, "A")
    out["storm_mackerel"] = s

    s = shaped(WRASSE, depth=2)
    hrow(s["grid"], 3, 2, 9, "A")
    put(s["grid"], 5, 11, "F")
    out["coral_wrasse"] = s

    s = shaped(TROUT)
    for c in range(3, 11):
        put(s["grid"], 3, c, "A" if c % 2 else "B")
    spots(s["grid"], [(2, 5), (2, 8)], "A")
    out["glassfin"] = s

    s = shaped(COD, depth=[2, 2, 4, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2])
    spots(s["grid"], [(1, 3), (2, 6), (1, 8), (2, 9), (3, 5), (3, 8)], "A")
    out["frost_cod"] = s

    s = shaped(TROUT)
    spots(s["grid"], [(1, 3), (1, 5), (1, 7), (2, 4), (2, 6), (2, 8), (3, 3), (3, 5), (3, 7), (3, 9)], "A")
    put(s["grid"], 5, 4, "S")
    put(s["grid"], 5, 5, "S")
    out["glacier_char"] = s

    s = shaped(["...ZFFFZ......ZF",
                "....FFFFFF...FFZ",
                "..BBBBBBBBB.FFF.",
                ".BEBBBBBBBBBFFF.",
                ".BBBBBBBBBBBFF..",
                ".WWWWWWWWWWFFFZ.",
                "..FFF.FFF.FFZ..."], depth=2)
    for c in (3, 5, 7, 9):
        put(s["grid"], 2, c, "Z")
    for c in (4, 6, 8, 10):
        put(s["grid"], 3, c, "Z")
    out["aurora_trout"] = s

    s = shaped(HERRING)
    put(s["grid"], 2, 2, "W")
    out["blind_cavefish"] = s

    # ---- long
    s = shaped(LONG, depth=2)
    spots(s["grid"], [(1, 3), (1, 6), (1, 9), (2, 4), (2, 8), (2, 11), (3, 5), (3, 10)], "A")
    out["reed_pike"] = s

    g = G("...........FF...",
          "..S.S.S.S.S.BFFF",
          ".BBBBBBBBBBBBFFF",
          "KBEBBBBBBBBBBFF.",
          "BBBBBBBBBBBBFF..",
          "K.WWWWWWWWWFFF..",
          "...W.WFF.WW.....")
    spots(g, [(2, 5), (2, 6), (3, 5), (4, 5), (4, 6)], "A")
    out["moon_sturgeon"] = {"grid": g, "depth": 2, "plane": "xy", "ends": (0, 0), "dy": 0}

    g = G(".........FFF....",
          "BBBBBBBBBBBBB.FF",
          "BKEBBBBBBBBBBFFF",
          "BBBBBBBBBBBBBFF.",
          ".WWWWWWWWWWW.FFF",
          ".....FF...F.....")
    # a long beak: the first columns narrow to a snout
    for r in (1, 3):
        for c in range(0, 4):
            put(g, r, c, "." if c < 3 else "B")
    for c in (3, 5, 7, 9, 11):
        put(g, 3, c, "Z")
    for c in (4, 6, 8, 10):
        put(g, 4, c, "Z")
    put(g, 0, 11, "Z")
    out["lantern_gar"] = {"grid": g, "depth": 2, "plane": "xy", "ends": (0, 0), "dy": 0}

    # ---- deep
    d_deep = [2, 2, 4, 4, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2]
    s = shaped(["....FFFFFFF...",
                "...FFFFFFFFF..",
                "..BBBBBBBBB.FF",
                ".BBBBBBBBBBFFF",
                "BBEBBBBBBBBFF.",
                "BBBBBBBBBBBFF.",
                ".WWWWWWWWWWFFF",
                "..WWWWWWWW..FF",
                "....FFF......."], depth=d_deep)
    spots(s["grid"], [(2, 4), (3, 6), (3, 3), (4, 5), (4, 8), (5, 4), (5, 7), (6, 6), (3, 9)], "A")
    put(s["grid"], 8, 9, "K")
    put(s["grid"], 7, 0, "K")
    out["mirror_carp"] = s

    s = shaped(["...F.F.F.F....",
                "...FFFFFFFF...",
                "..BBBBBBBBB.FF",
                ".BBBBBBBBBBFFF",
                "BBEBBBBBBBBFF.",
                "BBBBBBBBBBBFF.",
                ".WWWWWWWWWWFFF",
                "..WWWWWWWW..FF",
                "....FFF......."], depth=d_deep)
    spots(s["grid"], [(4, 6), (4, 7), (5, 6), (5, 7)], "R")
    out["pearl_snapper"] = s

    s = shaped(["....FFFFFF....",
                "...FFFFFFFFF..",
                "..BBBBBBBBB.FF",
                ".BBBBBBBBBBFFF",
                "BBEBBBBBBBBFF.",
                "BBBBBBBBBBBFF.",
                ".BBBBBBBBBBFFF",
                "..WWBBBBBB..FF",
                "....FFFFFF...."], depth=d_deep)
    for r in range(2, 8):
        put(s["grid"], r, 4, "A")
    put(s["grid"], 4, 11, "A")
    put(s["grid"], 5, 11, "A")
    out["sunfin_tang"] = s

    s = shaped(["...S.S.S.S.....",
                "...FFFFFFFFF..",
                "..BBBBBBBBB.FF",
                ".BBBBBBBBBBFFF",
                "BBEBBBBBBBBFF.",
                "BBBBBBBBBBBFF.",
                ".WWWWWWWWWWFFF",
                "..WWWWWWWW..FF",
                "....FFF......."], depth=d_deep)
    for r, c in ((2, 3), (2, 4), (3, 3), (3, 4), (3, 5), (3, 6), (4, 6), (4, 7), (5, 7), (5, 8), (5, 9), (6, 9), (6, 8)):
        put(s["grid"], r, c, "A")
    out["amethyst_koi"] = s

    s = shaped(["...F.F.F.F.F..",
                "..FFFFFFFFFF..",
                "..BBBBBBBBB.FF",
                ".BBBBBBBBBBFFF",
                "BBEBBBBBBBBFF.",
                "BBBBBBBBBBBFF.",
                ".WWWWWWWWWWFFF",
                "..WWWWWWWW..FF",
                "...FFFF.F.F..."], depth=d_deep)
    for c in (3, 6, 9):
        for r in range(2, 8):
            if s["grid"][r][c] in "BW":
                put(s["grid"], r, c, "A")
    out["ember_perch"] = s

    s = shaped(["....SS.S.S......",
                "...ZSFFFFFF..ZZ.",
                "..BBBBBBBB.FFZZ.",
                ".SBBBBBBBBBFFFZ.",
                "ZBEBBBBBBBBFFFZ.",
                "BBBBBBBBBBBFFFZ.",
                ".WWWWWWWWWWFFZ..",
                "..WWWWWWWW.FZ...",
                "...ZFFZFFZ......"], depth=[2, 2, 4, 4, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2])
    spots(s["grid"], [(3, 3), (3, 6), (4, 7), (5, 5), (5, 9), (2, 6), (2, 9), (4, 10)], "A")
    for c in (1, 3, 5, 7):
        put(s["grid"], 8, c + 3, "Z")
    out["phoenix_koi"] = s

    # ---- angler
    s = shaped(ANGLER, depth=[2, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2])
    spots(s["grid"], [(5, 8), (6, 6), (7, 9), (5, 4)], "A")
    out["abyssal_angler"] = s

    # ---- catfish
    s = shaped(CATFISH, depth=[2, 4, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2])
    spots(s["grid"], [(2, 5), (3, 8), (4, 6)], "A")
    out["bog_catfish"] = s

    # ---- eels
    g, path = wave([3, 3, 3, 2, 2, 1, 1, 1, 1, 2, 2, 3, 3, 3, 2, 2], 2, head=3)
    for x in (4, 7, 10, 13):
        put(g, path[x], x, "A")
    put(g, path[1], 1, "E")
    out["mire_eel"] = {"grid": g, "depth": 2, "plane": "xy", "ends": (0, 0), "dy": 0}

    g, path = wave([4, 4, 3, 3, 2, 1, 1, 1, 2, 3, 4, 4, 4, 3, 2, 2], 3, head=3)
    for x in range(3, 15, 2):
        put(g, path[x] - 1, x, "S")
        put(g, path[x] + 1, x, "Z")
    put(g, path[0] + 1, 0, "B")
    put(g, path[1], 1, "E")
    put(g, path[1] - 1, 1, "S")
    put(g, path[2] - 1, 2, "S")
    out["void_eel"] = {"grid": g, "depth": 2, "plane": "xy", "ends": (0, 0), "dy": 0}

    # ---- serpents
    g, path = wave([4, 4, 4, 3, 2, 1, 1, 1, 2, 3, 4, 5, 5, 5, 4, 3], 3, head=4, fin=False)
    for x in range(4, 15, 2):
        put(g, path[x] - 1, x, "S")
    put(g, path[0] - 1, 0, "S")
    put(g, path[1] - 1, 1, "S")
    put(g, path[1] - 2, 1, "S")
    put(g, path[2] - 1, 3, "S")
    put(g, path[1], 1, "E")
    put(g, path[2], 2, "R")
    for x in range(5, 14, 3):
        put(g, path[x] + 1, x, "A")
    put(g, path[15], 15, "F")
    put(g, path[15] + 1, 14, "F")
    out["leviathan_hatchling"] = {"grid": g, "depth": [3, 4, 4] + [3] * 13, "plane": "xy", "ends": (0, 0), "dy": 0}

    # ouroboros: a ring that bites its own tail
    n = 16
    g = [["."] * n for _ in range(n)]
    cx = cy = (n - 1) / 2
    for r in range(n):
        for c in range(n):
            dd = math.hypot(r - cy, c - cx)
            if 5.2 <= dd <= 7.4:
                g[r][c] = "W" if dd < 6.3 else "B"
    # spikes on the outside every 45 degrees, bright dots on the back
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        rr = round(cy + 8.2 * math.sin(a))
        cc = round(cx + 8.2 * math.cos(a))
        rr = max(0, min(n - 1, rr))
        cc = max(0, min(n - 1, cc))
        if g[rr][cc] == ".":
            g[rr][cc] = "S"
        r2 = round(cy + 6.6 * math.sin(a + math.pi / 8))
        c2 = round(cx + 6.6 * math.cos(a + math.pi / 8))
        if g[r2][c2] == "B":
            g[r2][c2] = "Z"
    # head at the left, tail tip meeting it above
    for r in range(5, 11):
        for c in range(0, 3):
            if g[r][c] == ".":
                g[r][c] = "B"
    for r in range(8, 11):
        for c in range(0, 3):
            g[r][c] = "W" if r == 10 else g[r][c]
    put(g, 7, 1, "E")
    put(g, 8, 1, "R")
    for r, c in ((4, 2), (4, 3), (3, 3)):
        put(g, r, c, "F")
    for r in (6, 9):
        put(g, r, 3, "R")
    out["ouroboros_eel"] = {"grid": g, "depth": 3, "plane": "xy", "ends": (0, 0), "dy": 0}

    # ---- stubby
    s = shaped(STUBBY, depth=[2, 4, 4, 4, 4, 4, 2, 2, 2, 2])
    spots(s["grid"], [(1, 4), (2, 3), (3, 5), (2, 6), (4, 4)], "Z")
    out["glow_goby"] = s

    s = shaped(["...S.S....",
                "..BSBBS..F",
                ".BBBBBBBFF",
                "BRBBBBBBFF",
                "BBBBBBBBFF",
                ".WWWWWWW.F",
                "...FF....."], depth=[2, 4, 4, 4, 4, 4, 2, 2, 2, 2])
    spots(s["grid"], [(2, 3), (3, 4), (4, 5), (2, 6)], "A")
    out["crystal_tetra"] = s

    s = shaped(["..S..S.S..",
                "..BSBBBBBF",
                ".BBBBBBBFF",
                "BRBBZZBBFF",
                "BBBZZZBBFF",
                ".WWWZWWW.F",
                "...SS....."], depth=[2, 4, 4, 4, 4, 4, 2, 2, 2, 2])
    out["shard_minnow"] = s

    # ---- rays (flat)
    g = G(*RAY)
    spots(g, [(4, 5), (6, 7), (8, 5), (7, 9), (9, 8), (5, 9)], "Z")
    hrow(g, 6, 13, 15, "S")
    put(g, 6, 13, "F")
    out["magma_ray"] = {"grid": g, "depth": [2, 2, 2, 4, 4, 4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 2], "plane": "xz",
                        "ends": (0, 0), "dy": 0}

    g = G(*["................",
            "....S.BBBB.S....",
            "...BBBBBBBBB....",
            "..BBBBBBBBBBB...",
            ".BBBBBBBBBBBBB..",
            "BBBBEBBBEBBBBBB.",
            "BBBBBBBBBBBBBBFF",
            "BBBBBBBBBBBBBFFF",
            "BBBBBBBBBBBBBBFF",
            ".BBBBBBBBBBBBB..",
            "..BBBBBBBBBBB...",
            "...BBBBBBBBB....",
            "....S.BBBB.S....",
            "................"])
    star = [(7, 6), (6, 6), (8, 6), (7, 5), (7, 7), (5, 8), (9, 8), (4, 11), (7, 10), (10, 11), (3, 4), (11, 4)]
    spots(g, star, "Z")
    for r, c in ((6, 14), (7, 15), (8, 14)):
        put(g, r, c, "Z")
    out["star_ray"] = {"grid": g, "depth": [2, 2, 2, 3, 3, 4, 4, 4, 4, 3, 3, 2, 2, 2, 2, 2], "plane": "xz",
                       "ends": (0, 0), "dy": 0}

    # ---- marlins
    g = G(*MARLIN)
    for c in range(4, 9):
        put(g, 3, c, "A") if c % 2 == 0 else None
    out["blazefin"] = {"grid": g, "depth": 2, "plane": "xy", "ends": (0, 0), "dy": 0}
    g = G(".......FFFF..FF.",
          "......FFFFFF.FF.",
          "...Y.Y.YFFFFFFF.",
          "...YYYYYFFFFFFF.",
          "...BBBBBBBBBBFF.",
          "..KBEBBBBBBBBFF.",
          "KKKBBBBBBBBBBFF.",
          "...WWWWWWWWW.FF.",
          "....WWWWWWW..FF.",
          "........FF.FF...")
    out["crowned_marlin"] = {"grid": g, "depth": 2, "plane": "xy", "ends": (0, 0), "dy": 0}

    # ---- leviathan: horned head, arched back with a spiked crest, a great fluke
    g = G("Z...Z...Z...Z..Z",
          "..S.S.......FFF.",
          "..SBS.S.S.S.FFFF",
          ".BBBBBBBBBBBBFFF",
          "BBEBBBBBBBBBBBFF",
          "BRRBBBBBBBBBBBFF",
          "BBBBBBBBBBBBBFFF",
          ".WWWWWWWWWWWWFFF",
          "..RWRWWWWWWWFFF.",
          "....GGG.GG..FF..",
          "....GG..........")
    spots(g, [(3, 4), (3, 7), (4, 8), (3, 10), (5, 6), (5, 10), (4, 5)], "Z")
    out["astral_leviathan"] = {"grid": g, "depth": 4, "plane": "xy", "ends": (0, 0), "dy": 0}
    for rid in ("magma_ray", "star_ray"):
        r = out[rid]
        g = r["grid"]
        keep = [row[:] for row in g]
        for i in range(len(g)):
            for j in range(len(g[0])):
                if g[i][j] in "BZ":
                    near = [(i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)]
                    if any(not (0 <= a < len(g) and 0 <= b < len(g[0])) or g[a][b] == "." for a, b in near):
                        keep[i][j] = "W" if rid == "magma_ray" else "F"
        r["grid"] = keep
        r["depth"] = [2, 2, 2, 2, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2]
    return out


if __name__ == "__main__":
    import sys
    allf = build_all()
    for k, v in allf.items():
        if len(sys.argv) > 1 and k not in sys.argv[1:]:
            continue
        print(k, len(v["grid"][0]), "x", len(v["grid"]))
        print(show(v["grid"]))
        print()
