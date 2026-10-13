#!/usr/bin/env python3
"""Ability icons: one 16x16 GUI sprite per ability for the abilities HUD.

    python3 tools/assets/ability_icons.py [--sheet docs/art/ability-icons-sheet.png]

Each ability gets a slot frame (tactical = steel blue, signature = gold, ultimate = void purple) around a 12x12
glyph from GLYPHS (ASCII rows, auto-shaded per STYLE.md, sel-out outlined). Sprites go to
`abilities/src/client/resources/assets/blocksouls_abilities/textures/gui/sprites/ability/<id>.png`, drawn with
`blitSprite("blocksouls_abilities:ability/<id>")`. A new ability: add a glyph and re-run; an ability without one
gets a generic rune and a warning so the build never ships a missing sprite.
"""

import os
import sys

from PIL import Image

import artlib as A

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "abilities/src/client/resources/assets/blocksouls_abilities/textures/gui/sprites/ability")

M = A.Mat
SILK = A.ramp("#5e6670", "#8a939c", "#b4bcc4", "#d8dee4", "#f4f7fa")
BLOOD = A.ramp("#2a0306", "#4d070d", "#7a0d16", "#a8161f", "#d02a2e", "#ef5a52")
WATER = A.ramp("#06253a", "#0c4a6e", "#1b7aa6", "#3fa9d6", "#8fdcf5", "#dff7ff")
LEAF = A.ramp("#0c2410", "#164a1c", "#24702a", "#38963a", "#5cb84e", "#94dc78")
INK = A.rgb("#0b0c10")
WHITE = A.rgb("#f4f7fa")
SPARK = A.rgb("#fff6a8")

SLOTS = {
    "tactical": (A.STEEL_BLUE, A.rgb("#08101f"), A.rgb("#16284a")),
    "signature": (A.GOLD, A.rgb("#1c0f02"), A.rgb("#3e2408")),
    "ultimate": (A.VOID, A.rgb("#0e0619"), A.rgb("#2a1450")),
}

ABILITIES = {
    "frag": "tactical", "web_bomb": "tactical", "fire_wall": "tactical", "healing_dart": "tactical",
    "ground_pound": "signature", "web_pull": "signature", "ember_wave": "signature", "geyser": "signature",
    "taunt_slam": "signature", "fan_the_hammer": "signature", "overclock": "signature", "transfusion": "signature",
    "storm_barrage": "ultimate", "melee_frenzy": "ultimate", "party_overshield": "ultimate",
}

# ---- Glyphs: 12x12, '.' transparent. (rows, mats, fixed) ----
GLYPHS = {
    # A pineapple grenade with a pin.
    "frag": ([
        "....ppp.....",
        "....p.p.....",
        "...mmmmm....",
        "..mgmgmgm...",
        ".mmgmgmgmm..",
        ".mgmgmgmgm..",
        ".mmgmgmgmm..",
        ".mgmgmgmgm..",
        "..mmgmgmm...",
        "...mmmmm....",
        "............",
        "............",
    ], {"m": M(A.GUNMETAL, 3, 0.1), "g": M(A.GUNMETAL, 2), "p": M(A.BRASS, 4)}, {}),
    # A web with a sticky core.
    "web_bomb": ([
        "s....s....s.",
        ".s...s...s..",
        "..s..s..s...",
        "...sssss....",
        "....scs.....",
        "sssscccssss.",
        "....scs.....",
        "...sssss....",
        "..s..s..s...",
        ".s...s...s..",
        "s....s....s.",
        "............",
    ], {"s": M(SILK, 2, 0.1), "c": M(A.BONE, 4)}, {}),
    # A row of flames on a bar.
    "fire_wall": ([
        "............",
        "...f....f...",
        "..ff.f..ff..",
        ".fff.ff.fff.",
        ".fyf.fy.fyf.",
        ".fyyffyyfyf.",
        "fyyyfyyyyyyf",
        "fywyfywyywyf",
        ".ywy.yww.wy.",
        "..y...y..y..",
        "bbbbbbbbbbbb",
        "............",
    ], {"f": M(A.EMBER, 2, 0.1), "y": M(A.EMBER, 3), "w": M(A.EMBER, 5), "b": M(A.POLYMER, 2)}, {}),
    # A dart with a cross on its vial.
    "healing_dart": ([
        "........hh..",
        ".......hhh..",
        "......vvh...",
        ".....vrvv...",
        "....vrrrv...",
        "...vvvrv....",
        "..mmvvv.....",
        ".mmm........",
        "nmm.........",
        "n...........",
        "............",
        "............",
    ], {"v": M(A.GLASS, 3), "r": M(BLOOD, 4), "m": M(A.GUNMETAL, 4), "n": M(A.GUNMETAL, 2), "h": M(A.PAINT_RED, 3)}, {}),
    # A boot slamming down with a shockwave.
    "ground_pound": ([
        "....bbbb....",
        "....bbbb....",
        "....bbbb....",
        "...bbbbbb...",
        "..bbbbbbbb..",
        "............",
        ".w........w.",
        "ww.wwwwww.ww",
        "w..w....w..w",
        "...w....w...",
        "............",
        "............",
    ], {"b": M(A.RUBBER, 3, 0.1), "w": M(A.PALE, 3)}, {}),
    # A web line with a hooked end.
    "web_pull": ([
        "h...........",
        "hh..........",
        ".hh.........",
        "..ss........",
        "...ss.......",
        "....ss......",
        ".....ss.....",
        "......ss....",
        ".......ss...",
        "........ss..",
        ".........ss.",
        "..........ss",
    ], {"h": M(A.GUNMETAL, 5), "s": M(SILK, 3)}, {}),
    # A wave of embers rolling right.
    "ember_wave": ([
        "..........f.",
        "........fff.",
        "......ffyff.",
        "....ffyyyff.",
        "..ffyywwyff.",
        "ffyywwwwyff.",
        "fyywwwwwyf..",
        ".fyywwyyf...",
        "..ffyyff....",
        "....fff.....",
        "......f.....",
        "............",
    ], {"f": M(A.EMBER, 2, 0.15), "y": M(A.EMBER, 3), "w": M(A.EMBER, 5)}, {}),
    # A water column bursting up.
    "geyser": ([
        ".....ww.....",
        "....wwww....",
        "...wwwwww...",
        "..ww.ww.ww..",
        ".w..wwww..w.",
        "....wwww....",
        "....wwww....",
        "....wwww....",
        "...wwwwww...",
        "..wwwwwwww..",
        ".wwwwwwwwww.",
        "............",
    ], {"w": M(WATER, 3, 0.15)}, {}),
    # A shield with a shout mark.
    "taunt_slam": ([
        ".ssssssssss.",
        ".smmmmmmmms.",
        ".smmm!!mmms.",
        ".smmm!!mmms.",
        ".smmm!!mmms.",
        ".smmm!!mmms.",
        "..smmmmmms..",
        "..smm!!mms..",
        "...smmmms...",
        "....smms....",
        ".....ss.....",
        "............",
    ], {"s": M(A.GUNMETAL, 5), "m": M(A.GUNMETAL, 3, 0.1)}, {"!": A.rgb("#ffd84a")}),
    # A revolver with six spent rounds.
    "fan_the_hammer": ([
        "c.c.c.c.c.c.",
        "............",
        "bbbbbbbbbb..",
        "bbbbbbbbbbb.",
        "....ffff....",
        "....ff......",
        "....gg......",
        "....gg......",
        ".....g......",
        "............",
        "............",
        "............",
    ], {"c": M(A.BRASS, 4), "b": M(A.GUNMETAL, 3, 0.1), "f": M(A.GUNMETAL, 2), "g": M(A.WOOD, 3)}, {}),
    # A gear with a spark.
    "overclock": ([
        "....g..g....",
        "...gggggg...",
        "..gg....gg..",
        "gggg.ss.gggg",
        ".gg.s..s.gg.",
        ".gg.s..s.gg.",
        "gggg.ss.gggg",
        "..gg....gg..",
        "...gggggg...",
        "....g..g....",
        "..........k.",
        ".........kk.",
    ], {"g": M(A.GUNMETAL, 4, 0.1), "s": M(A.POLYMER, 2)}, {"k": SPARK}),
    # Two drops joined by a flow.
    "transfusion": ([
        "..r.........",
        ".rrr........",
        ".rrr........",
        "..r.........",
        "...rr.......",
        "....rrr.....",
        "......rrr...",
        ".......rr...",
        ".........r..",
        "........rrr.",
        "........rrr.",
        ".........r..",
    ], {"r": M(BLOOD, 3, 0.1)}, {}),
    # A cloud with three bolts.
    "storm_barrage": ([
        "...cccccc...",
        "..cccccccc..",
        ".cccccccccc.",
        ".cccccccccc.",
        "..cccccccc..",
        "..b...b...b.",
        ".bb..bb..bb.",
        ".b...b...b..",
        "bb..bb..bb..",
        "b...b...b...",
        "............",
        "............",
    ], {"c": M(A.STORM, 1, 0.1)}, {"b": A.rgb("#fff1b0")}),
    # Two crossed fists / sweeping slashes.
    "melee_frenzy": ([
        "w.........w.",
        ".w.......w..",
        "..w.....w...",
        "...w...w....",
        "....w.w.....",
        ".....w......",
        "....w.w.....",
        "...w...w....",
        "..w.....w...",
        ".w.......w..",
        "w.........w.",
        "............",
    ], {"w": M(A.PALE, 4)}, {}),
    # A big shield over three small ones.
    "party_overshield": ([
        "..vvvvvvvv..",
        ".vvvvvvvvvv.",
        ".vvvvvvvvvv.",
        "..vvvvvvvv..",
        "...vvvvvv...",
        "....vvvv....",
        ".....vv.....",
        "............",
        "ss..ss..ss..",
        "ss..ss..ss..",
        ".s...s...s..",
        "............",
    ], {"v": M(A.VOID, 4, 0.1), "s": M(A.VOID, 3)}, {}),
    None: ([
        "....rrrr....",
        "...rr..rr...",
        "...r....r...",
        ".......rr...",
        "......rr....",
        ".....rr.....",
        ".....r......",
        "............",
        ".....r......",
        ".....r......",
        "............",
        "............",
    ], {"r": M(A.BONE, 3)}, {}),
}


def glyph(name):
    if name not in GLYPHS:
        print(f"warning: no glyph for ability '{name}', using the generic rune", file=sys.stderr)
        name = None
    rows, mats, fixed = GLYPHS[name]
    return A.paint(rows, mats, fixed, f"ability_{name}")


def icon(name, slot):
    frame, bg_dark, bg_light = SLOTS[slot]
    img = Image.new("RGBA", (16, 16), A.TRANSPARENT)
    for y in range(1, 15):
        for x in range(1, 15):
            img.putpixel((x, y), A.lerp(bg_light, bg_dark, (x + y - 2) / 26))
    n = len(frame)
    for i in range(1, 15):
        img.putpixel((i, 0), frame[n - 2])
        img.putpixel((0, i), frame[n - 2])
        img.putpixel((i, 15), frame[2])
        img.putpixel((15, i), frame[2])
    img.putpixel((1, 1), frame[n - 3])
    img.putpixel((14, 14), frame[1])
    if slot == "ultimate":
        for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
            img.putpixel((x, y), frame[n - 1])
    g = Image.new("RGBA", (14, 14), A.TRANSPARENT)
    g.alpha_composite(glyph(name), (1, 1))
    img.alpha_composite(A.outline(g, factor=0.3), (1, 1))
    return img


def main(argv):
    os.makedirs(ASSETS, exist_ok=True)
    images = []
    for name, slot in ABILITIES.items():
        img = icon(name, slot)
        img.save(os.path.join(ASSETS, name + ".png"))
        images.append((name, img))
    print(f"{len(images)} ability icons -> {os.path.relpath(ASSETS, ROOT)}")
    if "--sheet" in argv:
        path = argv[argv.index("--sheet") + 1]
        cell, cols = 72, 8
        rows = (len(images) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cell, rows * cell), A.rgb("#202226"))
        for i, (name, img) in enumerate(images):
            sheet.alpha_composite(img.resize((64, 64), Image.NEAREST), ((i % cols) * cell + 4, (i // cols) * cell + 4))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        sheet.save(path)
        print(f"sheet -> {path}")


if __name__ == "__main__":
    main(sys.argv[1:])
