"""Boon icons: one 16x16 GUI sprite per boon plus the Wildcard card back.

    python3 tools/assets/boon_icons.py [--sheet docs/art/boon-icons-sheet.png]

Every boon in `data/*/boons/*.json` gets a tier frame (common = scrap steel, rare = steel blue, legendary = gold
with corner studs) around a 12x12 glyph from GLYPHS, and combos (boons with "requires") get a magenta diamond in the
top-right corner. The script reads tier and combo from the JSON, writes the sprite to
`assets/<ns>/textures/gui/sprites/boon/<name>.png` and points the JSON's "icon" at `<ns>:boon/<name>`.

A new boon: add its JSON, add a glyph below (ASCII rows, auto-shaded per STYLE.md), re-run. A boon without a glyph
gets a generic rune and a warning, so the build never ships a missing sprite.
"""

import glob
import json
import os
import sys

from PIL import Image, ImageDraw

import artlib as A

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA = os.path.join(ROOT, "game/src/main/resources/data")
ASSETS = os.path.join(ROOT, "game/src/main/resources/assets")

M = A.Mat
LEAF = A.ramp("#0c2410", "#164a1c", "#24702a", "#38963a", "#5cb84e", "#94dc78")
SLIME = A.ramp("#123316", "#1f5a24", "#358a38", "#56b84f", "#86dc78", "#c4f5b4")
BLOOD = A.ramp("#2a0306", "#4d070d", "#7a0d16", "#a8161f", "#d02a2e", "#ef5a52")
SKIN = A.ramp("#3d2416", "#66402a", "#8f5e3e", "#b67d55", "#d6a074", "#f0c79e")
SILK = A.ramp("#5e6670", "#8a939c", "#b4bcc4", "#d8dee4", "#f4f7fa")
MOON = A.ramp("#5e4a12", "#9a7d24", "#d2b44a", "#f2df8a", "#fff6cc")

WHITE = A.rgb("#f4f7fa")
INK = A.rgb("#0b0c10")
GLOW = A.rgb("#a6ff8a")
WIND = A.rgb("#cfe9ff")
COMBO = A.rgb("#e36bff")
COMBO_LIGHT = A.rgb("#ffd6ff")

# Tier frames: (frame ramp, background dark, background light). Must read apart at 1x (STYLE.md: "Read at 1x").
TIERS = {
    "common": (A.SCRAP, A.rgb("#16171a"), A.rgb("#2a2c31")),
    "rare": (A.STEEL_BLUE, A.rgb("#08101f"), A.rgb("#16284a")),
    "legendary": (A.GOLD, A.rgb("#1c0f02"), A.rgb("#3e2408")),
    "wildcard": (A.VOID, A.rgb("#0e0619"), A.rgb("#2a1450")),
}

# ---- Glyphs: 12x12, '.' transparent. (rows, mats, fixed) ----
GLYPHS = {
    "swift_feet": ([
        "............",
        ".......LLL..",
        ".......LLL..",
        ".ss....LLL..",
        ".......LLL..",
        "..ss...LLLL.",
        ".......LLLLL",
        ".sss..LLLLLL",
        "......LLLLLL",
        ".ss..LLLLLLL",
        ".....SSSSSSS",
        "............",
    ], {"L": M(A.WOOD, 4), "S": M(A.RUBBER, 3)}, {"s": WIND}),
    "spring_legs": ([
        ".....aa.....",
        "....aaaa....",
        "...aaaaaa...",
        ".....aa.....",
        "..cccccccc..",
        ".........c..",
        "..cccccccc..",
        "..c.........",
        "..cccccccc..",
        ".........c..",
        "..cccccccc..",
        "............",
    ], {"c": M(A.GUNMETAL, 6)}, {"a": GLOW}),
    "iron_skin": ([
        "............",
        ".iiiiiiiiii.",
        ".iiiiiiiiii.",
        ".iirr..rrii.",
        ".irrrrrrrri.",
        ".irrrrrrrri.",
        "..irrrrrri..",
        "..iirrrrii..",
        "...iirrii...",
        "....iiii....",
        ".....ii.....",
        "............",
    ], {"i": M(A.SCRAP, 4), "r": M(BLOOD, 4)}, {}),
    "deep_lungs": ([
        "............",
        ".......bbb..",
        "......b...b.",
        "......b.h.b.",
        "......b...b.",
        ".bbb...bbb..",
        "b.h.b.......",
        "b...b..bb...",
        "b...b.b.hb..",
        ".bbb..b..b..",
        ".......bb...",
        "............",
    ], {}, {"b": A.GLASS[4], "h": WHITE}),
    "night_eyes": ([
        "............",
        "............",
        "...wwwwww...",
        "..wwwggwww..",
        ".wwwggggwww.",
        "wwwggppggwww",
        "wwwggppggwww",
        ".wwwggggwww.",
        "..wwwggwww..",
        "...wwwwww...",
        "............",
        "............",
    ], {"w": M(A.BONE, 4), "g": M(A.TRITIUM, 2)}, {"p": INK}),
    "quick_hands": ([
        "............",
        "..b...b...b.",
        ".bbb.bbb.bbb",
        ".bbb.bbb.bbb",
        ".ccc.ccc.ccc",
        ".ccc.ccc.ccc",
        ".ccc.ccc.ccc",
        ".ccc.ccc.ccc",
        ".ccc.ccc.ccc",
        ".ddd.ddd.ddd",
        "............",
        "sssss.sss.ss",
    ], {"b": M(A.RUST, 5), "c": M(A.BRASS, 4), "d": M(A.BRASS, 2, edges=False)}, {"s": WIND}),
    "steady_climber": ([
        ".r...aa...r.",
        ".r..aaaa..r.",
        ".rraaaaaarr.",
        ".r...aa...r.",
        ".r...aa...r.",
        ".rrrraarrrr.",
        ".r...aa...r.",
        ".r........r.",
        ".rrrrrrrrrr.",
        ".r........r.",
        ".r........r.",
        ".rrrrrrrrrr.",
    ], {"r": M(A.WOOD, 4, edges=False)}, {"a": GLOW}),
    "four_leaf": ([
        "............",
        ".lll....lll.",
        "lllll..lllll",
        "lllll..lllll",
        ".llllllllll.",
        "...llllll...",
        "...llllll...",
        ".llllllllll.",
        "lllll..lllll",
        "lllll..lllll",
        ".lll.ss.lll.",
        "......ss....",
    ], {"l": M(LEAF, 3), "s": M(LEAF, 1, edges=False)}, {}),
    "brawler": ([
        "............",
        "...ffffff...",
        "..ffffffff..",
        "..fddddddf..",
        "..ffffffff..",
        "..fddddddf..",
        "..ffffffff..",
        "..fddddddft.",
        ".tttfffffft.",
        ".ttttttttt..",
        "...wwwwww...",
        "...wwwwww...",
    ], {"f": M(SKIN, 3), "t": M(SKIN, 2), "w": M(A.TAPE, 2)}, {"d": SKIN[1]}),
    "fire_ward": ([
        "............",
        ".ssssssssss.",
        ".s....f...s.",
        ".s...ff...s.",
        ".s...fff..s.",
        ".s..fffff.s.",
        ".s.ffyyfffs.",
        ".s.ffyyyffs.",
        "..s.fyyyfs..",
        "...s.fffs...",
        "....ssss....",
        "............",
    ], {"s": M(A.STEEL_BLUE, 4, edges=False), "f": M(A.EMBER, 3)}, {"y": A.EMBER[5]}),
    "featherfall": ([
        "..........ff",
        "........ffff",
        ".......fffff",
        "......ffffff",
        ".....ffffff.",
        "....fffffff.",
        "...fffffff..",
        "...ffffff...",
        "..fqffff....",
        "..qfff......",
        ".q..........",
        "q...........",
    ], {"f": M(SILK, 3)}, {"q": A.WOOD[2]}),
    "long_thread": ([
        "............",
        ".wwwwwwww...",
        "..tttttt....",
        "..tttttt....",
        "..tttttt....",
        "..tttttt....",
        ".wwwwwwww...",
        ".....l......",
        "......l.....",
        ".......l....",
        "........l...",
        ".........lll",
    ], {"w": M(A.WOOD, 4), "t": M(SILK, 3)}, {"l": WHITE}),
    "second_wind": ([
        "............",
        "........ww..",
        ".......w..w.",
        "..........w.",
        "wwwwwwwwww..",
        "............",
        "wwwwwwwwwww.",
        "..........w.",
        ".......w..w.",
        "........ww..",
        "............",
        "..wwwwwww...",
    ], {}, {"w": WIND}),
    "hollow_bones": ([
        ".....aa.....",
        "....aaaa....",
        "...aa..aa...",
        ".....aa.....",
        "....aaaa....",
        "...aa..aa...",
        "............",
        ".bb......bb.",
        "bbbbbbbbbbbb",
        "bbbbbbbbbbbb",
        ".bb......bb.",
        "............",
    ], {"b": M(A.BONE, 4)}, {"a": GLOW}),
    "slippery": ([
        ".....s......",
        ".....ss.....",
        "....sss.....",
        "....ssss....",
        "...ssssss...",
        "..ssssssss..",
        "..sshhssss..",
        "..shhsssss..",
        "..ssssssss..",
        "...ssssss...",
        "....ssss....",
        "............",
    ], {"s": M(SLIME, 3)}, {"h": SLIME[5]}),
    "quickpick": ([
        "...hhhhhh...",
        ".hhhhhhhhhh.",
        "hh...ww...hh",
        "h....ww....h",
        ".....ww.....",
        ".....ww.....",
        ".....ww.....",
        ".....ww.....",
        ".....ww.....",
        ".....ww.....",
        ".....ww.....",
        ".....ww.....",
    ], {"h": M(A.GUNMETAL, 5), "w": M(A.WOOD, 4)}, {}),
    "mending_blood": ([
        ".....rr.....",
        ".....rr.....",
        "....rrrr....",
        "...rrrrrr...",
        "...rrrrrr...",
        "..rrrwwrrr..",
        "..rrrwwrrr..",
        "..rwwwwwwr..",
        "..rwwwwwwr..",
        "..rrrwwrrr..",
        "...rrwwrr...",
        "....rrrr....",
    ], {"r": M(BLOOD, 3)}, {"w": A.rgb("#ffe0e0")}),
    "wings": ([
        "............",
        "w..........w",
        "ww........ww",
        "www......www",
        "wwww....wwww",
        "wwwww..wwwww",
        ".wwwwwwwwww.",
        "..wwwwwwww..",
        "..www..www..",
        "..ww....ww..",
        "..w......w..",
        "............",
    ], {"w": M(A.PALE, 3)}, {}),
    "sky_rider": ([
        "............",
        ".....gg.....",
        "....gggg....",
        "...gggggg...",
        "..gggggggg..",
        ".gggggggggg.",
        "gggg.gg.gggg",
        "gg...gg...gg",
        ".....gg.....",
        "..cccccccc..",
        ".cccccccccc.",
        "..cccccccc..",
    ], {"g": M(A.VOID, 4), "c": M(SILK, 3)}, {}),
    "juggernaut": ([
        "...hhhhhh...",
        "..hhhhhhhh..",
        ".hhhhhhhhhh.",
        ".hhhhhhhhhh.",
        ".hhhhhhhhhh.",
        ".hvvvvvvvvh.",
        ".hhhhhvhhhh.",
        ".hhhhhvhhhh.",
        ".hhhvhvhvhh.",
        ".hhhhhhhhhh.",
        "..hhhhhhhh..",
        "............",
    ], {"h": M(A.GUNMETAL, 5)}, {"v": INK}),
    "moon_boots": ([
        ".mmm........",
        "mm..........",
        "m......bbbb.",
        "m......bbbb.",
        "mm.....bbbb.",
        ".mmm...bbbb.",
        ".......bbbb.",
        "......bbbbb.",
        "....bbbbbbb.",
        "...bbbbbbbb.",
        "...ssssssss.",
        "............",
    ], {"m": M(MOON, 3), "b": M(A.PALE, 3), "s": M(A.RUBBER, 3)}, {}),
    "wildcard": ([
        "............",
        "....qqqq....",
        "...qq..qq...",
        "...qq..qq...",
        ".......qq...",
        "......qq....",
        ".....qq.....",
        ".....qq.....",
        "............",
        ".....qq.....",
        ".....qq.....",
        "............",
    ], {}, {"q": A.rgb("#ece0ff")}),
    # Generic rune for boons nobody drew yet.
    None: ([
        "............",
        ".....rr.....",
        "....r..r....",
        "...r....r...",
        "..r..rr..r..",
        ".r..r..r..r.",
        ".r..r..r..r.",
        "..r..rr..r..",
        "...r....r...",
        "....r..r....",
        ".....rr.....",
        "............",
    ], {}, {"r": A.rgb("#d0d4dc")}),
}


def afterimage():
    """Three chevrons fading in from the left: speed with an echo."""
    img = Image.new("RGBA", (12, 12), A.TRANSPARENT)
    base = A.rgb("#9ff3ff")
    for k, alpha in enumerate((90, 170, 255)):
        x0 = k * 3
        for y in range(9):
            dx = 4 - abs(y - 4)
            for x in (x0 + dx, x0 + dx + 1):
                if x < 12:
                    img.putpixel((x, y + 1), (base[0], base[1], base[2], alpha))
    return img


def glyph(name, seed):
    if name == "afterimage":
        return afterimage()
    if name not in GLYPHS:
        print(f"warning: no glyph for boon '{name}', using the generic rune", file=sys.stderr)
        name = None
    rows, mats, fixed = GLYPHS[name]
    return A.paint(rows, mats, fixed, seed)


def icon(name, tier, combo=False):
    frame, bg_dark, bg_light = TIERS[tier]
    img = Image.new("RGBA", (16, 16), A.TRANSPARENT)
    # Background: lit from the top-left, a soft diagonal gradient.
    for y in range(1, 15):
        for x in range(1, 15):
            img.putpixel((x, y), A.lerp(bg_light, bg_dark, (x + y - 2) / 26))
    # Frame: rounded corners, top/left lit, bottom/right shadowed.
    n = len(frame)
    for i in range(1, 15):
        img.putpixel((i, 0), frame[n - 2])
        img.putpixel((0, i), frame[n - 2])
        img.putpixel((i, 15), frame[2])
        img.putpixel((15, i), frame[2])
    img.putpixel((1, 1), frame[n - 3])
    img.putpixel((14, 14), frame[1])
    if tier == "legendary":
        for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
            img.putpixel((x, y), frame[n - 1])
    # The glyph, sel-out outlined, in the 14x14 interior.
    g = Image.new("RGBA", (14, 14), A.TRANSPARENT)
    g.alpha_composite(glyph(name, f"boon_{name}"), (1, 1))
    img.alpha_composite(A.outline(g, factor=0.3), (1, 1))
    if combo:
        for x, y in ((13, 0), (12, 1), (14, 1), (11, 2), (15, 2), (12, 3), (14, 3), (13, 4)):
            img.putpixel((x, y), COMBO)
        for x, y in ((13, 1), (12, 2), (13, 2), (14, 2), (13, 3)):
            img.putpixel((x, y), COMBO_LIGHT)
    return img


def boons():
    """(namespace, name, path, json) for every boon in the game module's data."""
    for path in sorted(glob.glob(os.path.join(DATA, "*", "boons", "*.json"))):
        ns = path.split(os.sep)[-3]
        with open(path) as f:
            yield ns, os.path.splitext(os.path.basename(path))[0], path, json.load(f)


def sprite_path(ns, name):
    return os.path.join(ASSETS, ns, "textures/gui/sprites/boon", name + ".png")


def main(argv):
    made = {}
    for ns, name, path, data in boons():
        img = icon(name, data["tier"], bool(data.get("requires")))
        A.save(img, sprite_path(ns, name))
        made[name] = img
        sprite = f"{ns}:boon/{name}"
        if data.get("icon") != sprite:
            data["icon"] = sprite
            with open(path, "w") as f:
                json.dump(data, f, indent=2)
                f.write("\n")
    wildcard = icon("wildcard", "wildcard")
    A.save(wildcard, sprite_path("blocksouls_game", "wildcard"))
    made["wildcard"] = wildcard
    print(f"painted {len(made)} boon icons")
    if "--sheet" in argv:
        sheet(made, argv[argv.index("--sheet") + 1])


def sheet(images, path, cell=72, cols=8):
    label = 12
    names = sorted(images)
    rows = (len(names) + cols - 1) // cols
    out = Image.new("RGBA", (cols * cell, rows * (cell + label)), (34, 32, 38, 255))
    dr = ImageDraw.Draw(out)
    for i, name in enumerate(names):
        x, y = (i % cols) * cell, (i // cols) * (cell + label)
        big = images[name].resize((64, 64), Image.NEAREST)
        out.alpha_composite(big, (x + 4, y + 4))
        dr.text((x + 3, y + cell), name[:12], fill=(220, 214, 200, 255))
    A.save(out, os.path.join(ROOT, path))


if __name__ == "__main__":
    main(sys.argv[1:])
