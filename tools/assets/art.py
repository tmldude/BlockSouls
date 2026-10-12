#!/usr/bin/env python3
"""Hand-directed item, block, armor and vehicle art for guns, world, vehicles and game (STYLE.md).

    python3 tools/assets/art.py          # repaint everything in place
    python3 tools/assets/art.py --sheet docs/art/items-sheet.png

Deterministic: same script, same pixels. Gun 3D models and their textures live in guns.py (run by this script).
"""

import argparse
import math
import os
import random
import sys

from PIL import Image

sys.dont_write_bytecode = True  # keep tools/assets free of __pycache__
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from artlib import *  # noqa: E402,F401,F403
import artlib as A  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
WORLD = os.path.join(ROOT, "world/src/client/resources/assets/blocksouls_world/textures")
GUNS = os.path.join(ROOT, "guns/src/client/resources/assets/blocksouls_guns/textures")
VEH = os.path.join(ROOT, "vehicles/src/client/resources/assets/blocksouls_vehicles/textures")
GAME = os.path.join(ROOT, "game/src/client/resources/assets/blocksouls_game/textures")

OUT = {}  # path -> Image, filled by the painters, written by main()


def emit(path, img):
    OUT[path] = img


M = A.Mat

# ============================================================================================ items: materials


def item_scrap():
    rows = [
        "................",
        "................",
        "...........bb...",
        ".........mmmm...",
        ".......mmmmmmm..",
        ".....mmmmmrrmm..",
        "....mmmmmmrrmm..",
        "...mmmommmmrm...",
        "..nnmmmmmmmmm...",
        "..nnnnmmmmmmm...",
        "..rnnnnnnnmm....",
        "..rrnnnnnnn.....",
        "...rnnnnxn......",
        "....nnnnn.......",
        "................",
        "................",
    ]
    return A.sprite(rows, {"m": M(A.SCRAP, 4, 0.15), "n": M(A.SCRAP, 2, 0.15), "r": M(A.RUST, 3, 0.3),
                           "b": M(A.RUST, 4)},
                    {"o": A.SCRAP[0], "x": A.SCRAP[6]}, "scrap")


def item_raw_gunmetal():
    rows = [
        "................",
        "................",
        "................",
        "......gggg......",
        "....ggggggss....",
        "...gggwgggggs...",
        "..ggggggggggg...",
        "..sgggggggggggg.",
        "..ssggggggwgggg.",
        "...ggggggggggg..",
        "...gggggggsgg...",
        "....ggggggss....",
        "......ggg.......",
        "................",
        "................",
        "................",
    ]
    return A.sprite(rows, {"g": M(A.GUNMETAL, 3, 0.45), "s": M(A.STONE, 2, 0.3)},
                    {"w": A.GUNMETAL[7]}, "raw_gunmetal")


def item_gunmetal_ingot():
    rows = [
        "................",
        "................",
        "................",
        "................",
        "......TTTTTTTTT.",
        "....TwTTTTTTTTS.",
        "..TTTTTTTTTTTSS.",
        ".FFFFFFFFFFFSSS.",
        ".FFFnFnFFFFFSS..",
        ".FFFFnFFFFFFS...",
        ".FFFFFFFFFFF....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ]
    return A.sprite(rows, {"T": M(A.GUNMETAL, 5), "F": M(A.GUNMETAL, 4), "S": M(A.GUNMETAL, 2)},
                    {"w": A.GUNMETAL[7], "n": A.GUNMETAL[2]}, "ingot")


def item_gunmetal_nugget():
    rows = [
        "................",
        "................",
        "................",
        "................",
        "................",
        "........ggg.....",
        ".......gwggg....",
        "...ggg.ggggg....",
        "..gwggg.ggg.....",
        "..ggggg..hhh....",
        "...ggg..hwhhh...",
        "........hhhhh...",
        ".........hhh....",
        "................",
        "................",
        "................",
    ]
    return A.sprite(rows, {"g": M(A.GUNMETAL, 4), "h": M(A.GUNMETAL, 3)}, {"w": A.GUNMETAL[7]}, "nugget")


def item_blight_core():
    rows = [
        "................",
        "..t.........t...",
        "...t...tt..t....",
        "....tffffft.....",
        "...ffffffffff...",
        "..fffcccccfff...",
        "..ffcc1111ccff..",
        ".tffc112211cff..",
        "..ffc122221cfft.",
        "..ffc112211cff..",
        "..ffcc1111ccff..",
        "...fffcccccff...",
        "...tffffffft....",
        "..t...tfft..t...",
        ".......t........",
        "................",
    ]
    glow = A.BLIGHT_GLOW
    return A.sprite(rows, {"f": M(A.BLIGHT, 3, 0.25), "t": M(A.BLIGHT, 2, edges=False), "c": M(A.BLIGHT, 5, edges=False)},
                    {"1": glow[1], "2": glow[3]}, "core")


# ================================================================================================= armor icons

SCRAP_ARMOR = {"m": M(A.SCRAP, 4, 0.12), "n": M(A.SCRAP, 3, 0.12), "r": M(A.RUST, 3, 0.35), "l": M(A.WOOD, 3),
               "b": M(A.RUST, 5, edges=False)}
SCRAP_FIX = {"o": A.SCRAP[0], "x": A.SCRAP[6], "v": A.POLYMER[0]}
BLIGHT_ARMOR = {"m": M(A.GUNMETAL, 2, 0.1), "n": M(A.GUNMETAL, 1, 0.1), "f": M(A.BLIGHT, 3, 0.3),
                "r": M(A.BLIGHT, 4, 0.2), "b": M(A.BONE, 3)}
BLIGHT_FIX = {"o": A.BLIGHT_GLOW[0], "x": A.BLIGHT_GLOW[2], "v": A.BLIGHT[0]}

ARMOR_ROWS = {
    "scrap_plate": {
        "helmet": [
            "................",
            "................",
            "......mmmm......",
            "....mmmmmmmm....",
            "...mmxmmmmmrm...",
            "..mmmmmmmmmmrr..",
            "..nnnbnnnnbnnn..",
            "..mmvvvvvvvvmm..",
            "..mmmm.mm.mmmm..",
            "..rmm......mmm..",
            "..rrm......mmr..",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        "chestplate": [
            "................",
            "..mmm......mmm..",
            "..mxmm....mmmm..",
            "..mmmmllllmmmm..",
            "..nnnnnlnnnnnn..",
            "...mbmmmlmmbm...",
            "...nnnnnnlnnn...",
            "...mmmmrmmlmm...",
            "...nnnnrrnnln...",
            "...mmmmmrmmmm...",
            "...nnnnnnnnnn...",
            "...mmbmmmmbmm...",
            "................",
            "................",
            "................",
            "................",
        ],
        "leggings": [
            "................",
            "...lllllllllll..",
            "...mmmmbmmmmmm..",
            "...nnnnnnnnnnn..",
            "...mmmmm.mmmmm..",
            "...mxmm...mmmm..",
            "...nnnn...nnnr..",
            "...mmmm...mmrr..",
            "...mmbm...mbmr..",
            "...nnnn...nnnn..",
            "...mmmm...mmmm..",
            "...rmmm...mmmm..",
            "................",
            "................",
            "................",
            "................",
        ],
        "boots": [
            "................",
            "................",
            "................",
            "................",
            "................",
            "...mmm....mmm...",
            "...mxm....mmm...",
            "...nnn....nnn...",
            "...mbm....mbm...",
            "..mmmm...mmmm...",
            ".mmmmm..mmmmmr..",
            ".llllll.lllllll.",
            "................",
            "................",
            "................",
            "................",
        ],
    },
    "blight_forged": {
        "helmet": [
            "................",
            "....b......b....",
            "....bmmmmmmb....",
            "...mmmmffmmmm...",
            "..mmmfmmmmmfmm..",
            "..mmffmmmmmmfm..",
            "..mnnnnnnnnnnm..",
            "..mmvovvvvovmm..",
            "..mmmmm..mmmmm..",
            "..fmm......mmf..",
            "..ff........ff..",
            "................",
            "................",
            "................",
            "................",
            "................",
        ],
        "chestplate": [
            "................",
            ".bmm.......mmb..",
            "..mmmm....mmmm..",
            "..mmmmffffmmmm..",
            "..nnnffooffnnn..",
            "...mmmfxofmmm...",
            "...nnnffffnnn...",
            "...mmfmmmmfmm...",
            "...nnfnnnnfnn...",
            "...mmmmmmmmmm...",
            "...nnnnnnnnnn...",
            "...ffmmmmmmff...",
            "................",
            "................",
            "................",
            "................",
        ],
        "leggings": [
            "................",
            "...nnnnnnnnnnn..",
            "...mmmmfffmmmm..",
            "...mmmmmommmmm..",
            "...mmmmm.mmmmm..",
            "...mfmm...mmfm..",
            "...nfnn...nnfn..",
            "...mmmm...mmmm..",
            "...mmmm...mmmm..",
            "...nnnn...nnnn..",
            "...fmmm...mmmf..",
            "...ffmm...mmff..",
            "................",
            "................",
            "................",
            "................",
        ],
        "boots": [
            "................",
            "................",
            "................",
            "................",
            "................",
            "...mmm....mmm...",
            "...mfm....mfm...",
            "...nnn....nnn...",
            "...mom....mom...",
            "..mmmm...mmmm...",
            ".fmmmm..fmmmmm..",
            ".bnnnnn.bnnnnnn.",
            "................",
            "................",
            "................",
            "................",
        ],
    },
}


def armor_icons():
    for mat, pieces in ARMOR_ROWS.items():
        mats, fix = (SCRAP_ARMOR, SCRAP_FIX) if mat == "scrap_plate" else (BLIGHT_ARMOR, BLIGHT_FIX)
        for piece, rows in pieces.items():
            emit(f"{WORLD}/item/{mat}_{piece}.png", A.sprite(rows, mats, fix, f"{mat}_{piece}"))


# ========================================================================================== ammo and gun parts


def item_primary_ammo():
    rows = [
        "................",
        "................",
        "....c...........",
        "...ccc...c......",
        "...ccc..ccc.....",
        "...bbb..ccc..c..",
        "...bbb..bbb.ccc.",
        "...bbb..bbb.ccc.",
        "...bbb..bbb.bbb.",
        "...bbb..bbb.bbb.",
        "...bbb..bbb.bbb.",
        "...rrr..bbb.bbb.",
        "........rrr.bbb.",
        ".............rrr",
        "................",
        "................",
    ]
    rows = [r[:16] for r in rows]
    return A.sprite(rows, {"b": M(A.BRASS, 3), "c": M(A.RUST, 4), "r": M(A.BRASS, 2)}, {}, "primary_ammo")


def item_special_ammo():
    rows = [
        "................",
        "......vv........",
        ".....vvvv.......",
        ".....vwvv.......",
        ".....pppp..vv...",
        ".....gggg.vvvv..",
        ".....gggg.vwvv..",
        ".....pppp.pppp..",
        ".....gggg.gggg..",
        ".....gggg.gggg..",
        ".....gggg.pppp..",
        ".....bbbb.gggg..",
        ".....bbbb.gggg..",
        "..........bbbb..",
        "..........bbbb..",
        "................",
    ]
    return A.sprite(rows, {"v": M(A.VOID, 4), "p": M(A.VOID, 3, edges=False), "g": M(A.GUNMETAL, 3), "b": M(A.BRASS, 3)},
                    {"w": A.VOID[6]}, "special_ammo")


def item_gun_barrel():
    rows = [
        "................",
        "............mmm.",
        "...........mmomm",
        "..........mmmmm.",
        ".........ssss...",
        "........sxsxs...",
        ".......ssssss...",
        "......sxsxss....",
        ".....ssssss.....",
        "....bbbbbs......",
        "...bbbbbb.......",
        "..bbbbbb........",
        "..bbbbb.........",
        "..bbbb..........",
        "................",
        "................",
    ]
    return A.sprite(rows, {"m": M(A.GUNMETAL, 4), "s": M(A.GUNMETAL, 3), "b": M(A.GUNMETAL, 4)},
                    {"o": A.GUNMETAL[0], "x": A.GUNMETAL[1]}, "gun_barrel")


def item_gun_receiver():
    rows = [
        "................",
        "................",
        "................",
        "..rrrrrrrrrrrr..",
        "..r.r.r.r.r.r...",
        ".mmmmmmmmmmmmmm.",
        ".mmmmmmmmmmmmmm.",
        ".mmmmpppppmmmmm.",
        ".mmmmpooopmmmmm.",
        ".mmmmpppppmmmmm.",
        ".nnnnnnnnnnnnnn.",
        ".nnn.ddd...nnnn.",
        "......dd........",
        "................",
        "................",
        "................",
    ]
    return A.sprite(rows, {"r": M(A.GUNMETAL, 2, edges=False), "m": M(A.GUNMETAL, 4, 0.08), "n": M(A.GUNMETAL, 2),
                           "p": M(A.GUNMETAL, 2), "d": M(A.GUNMETAL, 3)},
                    {"o": A.BRASS[4]}, "gun_receiver")


def item_gun_stock():
    rows = [
        "................",
        "................",
        "................",
        "................",
        ".mmmm...........",
        ".mmmwwwwwwww....",
        ".mmmwwwwwwwwwww.",
        "..mmwwwwwwwwwwww",
        "...mwwwwwwwwwww.",
        "....wwwwwwww....",
        "....wwwwww......",
        ".....wwww.......",
        "......ww........",
        "................",
        "................",
        "................",
    ]
    rows = [r[:15] + "." for r in rows]
    return A.sprite(rows, {"m": M(A.GUNMETAL, 3), "w": M(A.WOOD, 3, 0.2)}, {}, "gun_stock")


# ===================================================================================================== vehicles


def item_wheel():
    img = Image.new("RGBA", (16, 16), A.TRANSPARENT)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            ang = math.atan2(y - cy, x - cx)
            light = -(x - cx + y - cy) / 10  # top-left lit
            if d <= 7.0:
                if d > 4.6:  # tyre with tread notches
                    i = 2 + (1 if light > 0.3 else 0) - (1 if light < -0.4 else 0)
                    if d > 6.2 and int((ang + math.pi) / (2 * math.pi) * 16) % 2 == 0:
                        i -= 1
                    c = A.RUBBER[max(0, i)]
                elif d > 3.6:  # rim
                    c = A.SCRAP[4 + (1 if light > 0.2 else 0) - (1 if light < -0.3 else 0)]
                elif d > 1.4:  # hub with rust
                    c = A.RUST[3] if (x + 2 * y) % 5 == 0 else A.SCRAP[3]
                else:
                    c = A.SCRAP[1]
                img.putpixel((x, y), c)
    for bx, by in ((7, 5), (10, 7), (8, 10), (5, 8)):
        img.putpixel((bx, by), A.SCRAP[6])
    return A.outline(img)


def item_engine():
    rows = [
        "................",
        "......pp........",
        "......pp........",
        "...ffffffff.....",
        "...nnnnnnnn.....",
        "...ffffffff.....",
        "...nnnnnnnn..ee.",
        "..mmmmmmmmmmmee.",
        "..mmrmmmmmmmme..",
        "..mmmmmmxmmmm...",
        "..mmmmmmmrrmm...",
        "..mmmmmmmmrmm...",
        "..nnnnnnnnnnn...",
        "...bb.....bb....",
        "................",
        "................",
    ]
    return A.sprite(rows, {"f": M(A.SCRAP, 4), "n": M(A.SCRAP, 2, edges=False), "m": M(A.SCRAP, 3, 0.15),
                           "r": M(A.RUST, 3, 0.3), "p": M(A.RUST, 4), "e": M(A.GUNMETAL, 2), "b": M(A.RUBBER, 2)},
                    {"x": A.HAZARD[3]}, "engine")


def kart_textures():
    """Box-UV texture for the kart (KartModel, 128x64) plus a rusted, burnt wreck variant."""
    boxes = {  # name: (u, v, w, h, d, painter)
        "chassis": (0, 0, 20, 3, 32, "body"),
        "bumper": (0, 36, 16, 4, 3, "bumper"),
        "seat_base": (72, 36, 7, 2, 7, "seat"),
        "seat_back": (100, 36, 7, 7, 1, "seat"),
        "roll_post": (24, 48, 1, 12, 1, "bar"),
        "roll_top": (30, 48, 20, 1, 1, "bar"),
        "steering": (72, 48, 4, 4, 1, "rubber"),
        "engine": (40, 36, 10, 6, 5, "engine"),
        "exhaust": (84, 48, 2, 8, 2, "exhaust"),
        "wheel": (0, 48, 3, 7, 7, "wheel"),
    }
    for wreck in (False, True):
        rng = random.Random("kart_wreck" if wreck else "kart")
        img = Image.new("RGBA", (128, 64), A.TRANSPARENT)
        body = A.HAZARD if not wreck else A.RUST
        for name, (u, v, w, h, d, kind) in boxes.items():
            for (x0, y0, x1, y1, face) in box_faces(u, v, w, h, d):
                for y in range(y0, y1):
                    for x in range(x0, x1):
                        fx, fy = x - x0, y - y0
                        fw, fh = x1 - x0, y1 - y0
                        c = kart_px(kind, face, fx, fy, fw, fh, rng, body, wreck)
                        img.putpixel((x, y), c)
        emit(f"{VEH}/entity/{'kart_wreck' if wreck else 'kart'}.png", img)


def box_faces(u, v, w, h, d):
    return [
        (u + d, v, u + d + w, v + d, "top"),
        (u + d + w, v, u + d + 2 * w, v + d, "bottom"),
        (u, v + d, u + d, v + d + h, "side"),
        (u + d, v + d, u + d + w, v + d + h, "front"),
        (u + d + w, v + d, u + 2 * d + w, v + d + h, "side"),
        (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h, "side"),
    ]


def kart_px(kind, face, x, y, w, h, rng, body, wreck):
    edge = x == 0 or y == 0
    far = x == w - 1 or y == h - 1
    n = rng.random()

    def shade(r, base):
        i = base + (1 if edge else 0) - (1 if far else 0)
        if n < 0.12:
            i += rng.choice((-1, 1))
        return r[max(0, min(len(r) - 1, i))]

    def grime(c):
        if wreck and n > 0.55:
            c = A.lerp(c, A.RUST[1] if n > 0.8 else A.POLYMER[1], 0.55)
        return c

    if kind == "body":
        if face == "top":
            # floor deck: checker-plate with a hazard stripe at the nose
            if y < 3:
                c = (A.HAZARD[3] if (x // 2 + y) % 2 == 0 else A.POLYMER[1]) if not wreck else A.RUST[2 + (x // 2 + y) % 2]
            else:
                c = A.SCRAP[3] if (x + y) % 4 else A.SCRAP[5]
            return grime(c)
        if face == "bottom":
            return grime(A.SCRAP[1])
        c = shade(body, 3)
        if y == 1 and x % 6 == 2:
            c = A.SCRAP[5]  # rivets
        return grime(c)
    if kind == "bumper":
        c = (A.HAZARD[3] if ((x + y) // 2) % 2 == 0 else A.POLYMER[1]) if face == "front" else shade(A.SCRAP, 3)
        return grime(c)
    if kind == "seat":
        c = shade(A.POLYMER, 3)
        if face in ("top", "front") and (x == w // 2):
            c = A.POLYMER[2]  # stitch
        if wreck and n > 0.7:
            c = A.TAPE[1]  # torn foam
        return grime(c)
    if kind == "bar":
        return grime(shade(A.GUNMETAL, 4))
    if kind == "rubber":
        return shade(A.RUBBER, 2)
    if kind == "engine":
        if face in ("side", "front") and y % 2 == 0:
            return grime(A.SCRAP[2])  # cooling fins
        c = shade(A.SCRAP, 4)
        if face == "top" and 2 <= x <= w - 3 and 1 <= y <= h - 2:
            c = A.RUST[3] if (x + y) % 3 else A.RUST[4]
        return grime(c)
    if kind == "exhaust":
        if face == "top":
            return A.POLYMER[0]
        return grime(shade(A.RUST, 3) if y > 1 else A.POLYMER[2])
    if kind == "wheel":
        if face == "side" and w == 7:  # hub faces (the 7x7 ones)
            d = math.hypot(x - 3, y - 3)
            if d < 1.2:
                return A.SCRAP[1]
            if d < 2.6:
                return A.SCRAP[4] if not wreck else A.RUST[3]
            return A.RUBBER[2 if (x + y) % 2 else 1]
        return A.RUBBER[1 + (y % 2)]
    return (255, 0, 255, 255)


# ======================================================================================================== relics

RELIC_ROWS = [
    "................",
    ".......cc.......",
    "......c..c......",
    ".......cc.......",
    ".......ff.......",
    "......ffff......",
    ".....ffggff.....",
    "....ffgwgggf....",
    "...ffgggggggf...",
    "....ffgggggf....",
    ".....ffgggf.....",
    "......ffgf......",
    ".......ff.......",
    "................",
    "................",
    "................",
]


def relics():
    tiers = {
        "common": ({"f": M(A.BONE, 3, 0.2), "g": M(A.SCRAP, 3), "c": M(A.SCRAP, 4, edges=False)}, A.SCRAP[6]),
        "rare": ({"f": M(A.GUNMETAL, 4), "g": M(A.STEEL_BLUE, 4), "c": M(A.GUNMETAL, 5, edges=False)}, A.STEEL_BLUE[6]),
        "legendary": ({"f": M(A.GOLD, 3), "g": M(A.EMBER, 3), "c": M(A.GOLD, 4, edges=False)}, A.GOLD[6]),
    }
    for tier, (mats, glint) in tiers.items():
        rows = list(RELIC_ROWS)
        if tier == "legendary":  # radiating sparks: the only tier that "shines"
            rows[2] = "..s...c..c...s.."
            rows[8] = ".s.ffgggggggf.s."
        mats = dict(mats)
        emit(f"{GAME}/item/relic_{tier}.png", A.sprite(rows, mats, {"w": glint, "s": A.GOLD[5]}, f"relic_{tier}"))


def item_soul():
    """A pale blue wisp with a bright core: the run's currency."""
    rows = [
        "................",
        "................",
        "......ss........",
        ".....sSSs.......",
        "....sSWWSs......",
        "....sSWWSSs.....",
        "....sSSSSSs.....",
        ".....sSSSSs.....",
        "......sSSSs.....",
        ".......sSs......",
        "........ss......",
        ".........s......",
        "..........s.....",
        "................",
        "................",
        "................",
    ]
    soul = A.ramp("#1d3a5c", "#2f6fa8", "#5fb0e8", "#b9e6ff")
    return A.sprite(rows, {"s": M(soul, 1), "S": M(soul, 2)}, {"W": A.rgb("#eaf8ff")}, "soul")


def item_reroll_charm():
    """A gunmetal ring threaded with souls."""
    rows = [
        "................",
        ".....gggggg.....",
        "....gg....gg....",
        "...gg..ss..gg...",
        "...g..sSSs..g...",
        "..gg..sSSs..gg..",
        "..g....ss....g..",
        "..g..........g..",
        "..gg........gg..",
        "...g..ss.ss.g...",
        "...gg.SS.SSgg...",
        "....gg....gg....",
        ".....gggggg.....",
        "................",
        "................",
        "................",
    ]
    soul = A.ramp("#1d3a5c", "#2f6fa8", "#5fb0e8", "#b9e6ff")
    return A.sprite(rows, {"g": M(A.GUNMETAL, 5), "s": M(soul, 1), "S": M(soul, 3)}, {}, "reroll_charm")


def item_trinket_case():
    """A bone-and-brass case with a gold clasp: a pick-1-of-3 trinket offer."""
    rows = [
        "................",
        "................",
        "...bbbbbbbbbb...",
        "..bBBBBBBBBBBb..",
        "..bBkkkkkkkkBb..",
        "..bBkkkkkkkkBb..",
        "..bbbbbggbbbbb..",
        "..bBBBBggBBBBb..",
        "..bBBBBBBBBBBb..",
        "..bBBBBBBBBBBb..",
        "..bBBBBBBBBBBb..",
        "...bbbbbbbbbb...",
        "................",
        "................",
        "................",
        "................",
    ]
    return A.sprite(rows, {"b": M(A.BRASS, 2), "B": M(A.BONE, 4), "k": M(A.BONE, 2), "g": M(A.GOLD, 4)}, {}, "trinket_case")


# ========================================================================================================= blocks


def stone_base(seed, r):
    img = A.noise_tile(16, 16, r, seed, base=3, p=0.55)
    rng = random.Random(seed + "c")
    for _ in range(5):  # soft darker strata blotches
        x, y = rng.randrange(16), rng.randrange(16)
        for dx in range(rng.randint(2, 4)):
            A.put(img, x + dx, y, r[2])
            if rng.random() < 0.5:
                A.put(img, x + dx, y + 1, r[2])
    for _ in range(4):  # light chips
        x, y = rng.randrange(16), rng.randrange(16)
        A.put(img, x, y, r[5 if len(r) > 5 else 4])
    return img


# Ore clusters: a few lumps with a cyan-steel glint, outlined dark. (dx, dy) cells.
ORE_LUMPS = [
    (3, 3, [(0, 0), (1, 0), (0, 1), (1, 1), (2, 1), (1, 2)]),
    (10, 2, [(0, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]),
    (11, 8, [(0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (1, 2), (2, 2)]),
    (3, 10, [(0, 0), (1, 0), (0, 1), (1, 1), (-1, 1), (0, 2)]),
    (7, 13, [(0, 0), (1, 0), (2, 0), (1, 1)]),
    (7, 6, [(0, 0), (0, 1), (1, 1)]),
]


def ore(base_ramp, seed, deep=False):
    img = stone_base(seed, base_ramp)
    if deep:  # horizontal cleavage lines
        for y in (4, 9, 14):
            for x in range(16):
                if (x * 7 + y) % 5:
                    A.put(img, x, y, base_ramp[1])
    lump = [A.GUNMETAL[1], A.GUNMETAL[4], A.GUNMETAL[5], A.GUNMETAL[6], A.rgb("#b9dbe6")]
    for cx, cy, cells in ORE_LUMPS:
        A.blob(img, cx, cy, cells, lump, outline_idx=0)
        A.put(img, cx, cy, A.GUNMETAL[7])  # glint top-left
    return img


def raw_gunmetal_block():
    img = A.noise_tile(16, 16, A.GUNMETAL, "rawblk", base=2, p=0.6)
    rng = random.Random("rawblk2")
    # big lumps with dark cracks between
    for _ in range(9):
        x, y = rng.randrange(16), rng.randrange(16)
        cells = [(0, 0), (1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (2, 2)][: rng.randint(4, 7)]
        A.blob(img, x, y, cells, [A.GUNMETAL[1], A.GUNMETAL[3], A.GUNMETAL[4], A.GUNMETAL[5], A.GUNMETAL[6]], outline_idx=0)
    for _ in range(6):
        A.put(img, rng.randrange(16), rng.randrange(16), A.STONE[2])
    return img


def gunmetal_block():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            # two machined plates (8 px tall) with a bevel, brushed horizontal streaks
            py = y % 8
            i = 4
            if py == 0 or x == 0:
                i = 6
            elif py == 7 or x == 15:
                i = 2
            elif (x * 3 + y * 7) % 11 == 0:
                i = 5
            img.putpixel((x, y), A.GUNMETAL[i])
    for x, y in ((2, 2), (13, 2), (2, 10), (13, 10)):  # rivets
        img.putpixel((x, y), A.GUNMETAL[7])
        img.putpixel((x + 1, y + 1), A.GUNMETAL[1])
    for x in range(5, 11):  # stamped serial bar
        img.putpixel((x, 12), A.GUNMETAL[3])
    return img


def scrap_pile():
    """Top/side texture for the low pile model: sides sample rows 9-15, top samples the whole square."""
    rng = random.Random("scrap_pile")
    img = A.noise_tile(16, 16, A.SCRAP, "pile", base=2, p=0.7)
    pieces = [  # (x, y, w, h, ramp)
        (1, 1, 6, 4, A.RUST), (8, 1, 6, 3, A.SCRAP), (3, 5, 4, 5, A.SCRAP), (8, 5, 7, 3, A.RUST),
        (1, 10, 5, 3, A.GUNMETAL), (7, 9, 4, 5, A.RUST), (11, 10, 4, 4, A.SCRAP), (2, 13, 5, 3, A.SCRAP),
    ]
    for x0, y0, w, h, r in pieces:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                i = 4 if (y == y0 or x == x0) else (2 if (y == y0 + h - 1 or x == x0 + w - 1) else 3)
                if rng.random() < 0.15:
                    i -= 1
                A.put(img, x, y, r[i])
        A.put(img, x0 + 1, y0 + 1, A.SCRAP[6])  # bolt / glint
    for x, y in ((6, 4), (12, 8), (4, 12)):  # bits of yellow hazard paint
        A.put(img, x, y, A.HAZARD[3])
    return img


NETHER_FRAMES = 8


def blighted_netherrack():
    """Netherrack crust overgrown with flesh; the veins swell and glow on a 8-frame loop (mcmeta interpolates)."""
    base = A.noise_tile(16, 16, A.NETHER, "bnr", base=3, p=0.6)
    for y in range(16):  # netherrack-style crevices
        for x in range(16):
            if (x * 7 + y * 13) % 17 in (0, 1) and (x + y) % 3:
                base.putpixel((x, y), A.NETHER[1])
    rng = random.Random("bnr2")
    flesh = set()
    for cx, cy, n in ((3, 3, 14), (11, 6, 10), (6, 12, 16), (14, 13, 8), (0, 9, 7)):  # fleshy growths, wrapping
        cells = [(cx, cy)]
        while len(cells) < n:  # random-walk accretion gives lumpy, non-round patches
            x, y = rng.choice(cells)
            dx, dy = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
            p = ((x + dx) % 16, (y + dy) % 16)
            if p not in cells:
                cells.append(p)
        flesh.update(cells)
    veins = set()
    for _ in range(4):  # wandering vein paths
        x, y = rng.randrange(16), rng.randrange(16)
        for _ in range(14):
            veins.add((x % 16, y % 16))
            x += rng.choice((-1, 0, 1, 1))
            y += rng.choice((-1, 0, 1))
    frames = Image.new("RGBA", (16, 16 * NETHER_FRAMES))
    for f in range(NETHER_FRAMES):
        pulse = (1 - math.cos(2 * math.pi * f / NETHER_FRAMES)) / 2  # 0..1..0
        img = base.copy()
        for (x, y) in flesh:
            n = (x * 5 + y * 3) % 4
            i = 3 + (1 if n == 0 else 0) - (1 if n == 3 else 0)
            img.putpixel((x, y), A.BLIGHT[i])
        for (x, y) in flesh:  # flesh rim against rock
            for dx, dy in ((0, 1), (1, 0)):
                p = ((x + dx) % 16, (y + dy) % 16)
                if p not in flesh:
                    img.putpixel(p, A.BLIGHT[1])
        for (x, y) in veins:
            c = A.lerp(A.BLIGHT[4], A.BLIGHT_GLOW[1], pulse) if (x, y) in flesh else A.lerp(A.BLIGHT[2], A.BLIGHT[4], pulse)
            img.putpixel((x, y), c)
        frames.paste(img, (0, 16 * f))
    return frames


def blight_growth():
    """Cross plant: fleshy stalks with swollen glowing bulbs."""
    rows = [
        "................",
        "...o........o...",
        "..ooo......ooo..",
        "...s...o....s...",
        "...s..ooo...s...",
        "...ss..s...ss...",
        "....s..s..ss....",
        "....s..s..s.....",
        "..o.ss.s.ss.o...",
        ".ooo.s.s.s.ooo..",
        "..s..sss.s..s...",
        "..ss..sssssss...",
        "...sss.sss.ss...",
        "....ssssssss....",
        "...ttttttttttt..",
        "..tttttttttttttt",
    ]
    rows = [r[:16] for r in rows]
    img = A.paint(rows, {"s": M(A.BLIGHT, 3), "t": M(A.BLIGHT, 2, 0.3)},
                  {"o": A.BLIGHT_GLOW[1]}, "growth")
    for x, y in ((3, 1), (12, 1), (7, 3), (2, 8), (12, 8)):
        img.putpixel((x, y), A.BLIGHT_GLOW[3])
    return img


def blight_veins():
    """Glow-lichen-style overlay: branching veins on a transparent face."""
    img = Image.new("RGBA", (16, 16), A.TRANSPARENT)
    rng = random.Random("veins")
    paths = []
    for sx, sy in ((0, 3), (15, 11), (6, 15), (9, 0)):
        x, y = sx, sy
        p = []
        for _ in range(12):
            p.append((x, y))
            x += rng.choice((-1, 0, 1)) if sx in (6, 9) else (1 if sx == 0 else -1)
            y += rng.choice((-1, 0, 1)) if sx not in (6, 9) else (1 if sy == 0 else -1)
            x, y = max(0, min(15, x)), max(0, min(15, y))
        paths.append(p)
    for p in paths:
        for i, (x, y) in enumerate(p):
            img.putpixel((x, y), A.BLIGHT[4] if i % 3 else A.BLIGHT[5])
            if i % 4 == 0:
                for dx, dy in ((0, 1), (1, 0)):
                    if 0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] == 0:
                        img.putpixel((x + dx, y + dy), A.BLIGHT[2])
    for x, y in ((4, 4), (11, 10), (7, 12), (9, 3)):  # nodes
        img.putpixel((x, y), A.BLIGHT_GLOW[1])
    return img


HEART_FRAMES = 8


def blight_heart():
    """Base (flesh shell) is static; the overlay (glowing core, rendered with light_emission) beats."""
    base = A.noise_tile(16, 16, A.BLIGHT, "heart", base=2, p=0.6)
    for y in range(16):  # sinew lines in the shell
        for x in range(16):
            if (x + 2 * y) % 7 == 0 or (2 * x - y) % 9 == 0:
                base.putpixel((x, y), A.BLIGHT[1])
            if (x in (0, 15) or y in (0, 15)) and (x * 3 + y) % 4 == 0:
                base.putpixel((x, y), A.BLIGHT[0])
    glow = Image.new("RGBA", (16, 16 * HEART_FRAMES), A.TRANSPARENT)
    for f in range(HEART_FRAMES):
        # a double "lub-dub" beat: two peaks then rest
        t = f / HEART_FRAMES
        beat = max(math.exp(-((t - 0.1) ** 2) / 0.004), 0.7 * math.exp(-((t - 0.3) ** 2) / 0.004))
        radius = 3.0 + 2.0 * beat
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                tendril = abs(abs(x - 7.5) - abs(y - 7.5)) < 1.1 and d < 7.6
                if d < radius:
                    k = d / radius
                    c = A.BLIGHT_GLOW[3] if k < 0.35 else (A.BLIGHT_GLOW[2] if k < 0.65 else A.BLIGHT_GLOW[1])
                elif d < radius + 1:
                    c = A.BLIGHT_GLOW[0]
                elif tendril:
                    c = A.lerp(A.BLIGHT[4], A.BLIGHT_GLOW[0], beat)
                else:
                    continue
                glow.putpixel((x, 16 * f + y), c)
    return base, glow


def blocks():
    emit(f"{WORLD}/block/gunmetal_ore.png", ore(A.STONE, "gunmetal_ore"))
    emit(f"{WORLD}/block/deepslate_gunmetal_ore.png", ore(A.DEEPSLATE, "deepslate_gunmetal_ore", deep=True))
    emit(f"{WORLD}/block/raw_gunmetal_block.png", raw_gunmetal_block())
    emit(f"{WORLD}/block/gunmetal_block.png", gunmetal_block())
    emit(f"{WORLD}/block/scrap_pile.png", scrap_pile())
    emit(f"{WORLD}/block/blighted_netherrack.png", blighted_netherrack())
    emit(f"{WORLD}/block/blight_growth.png", blight_growth())
    emit(f"{WORLD}/block/blight_veins.png", blight_veins())
    base, glow = blight_heart()
    emit(f"{WORLD}/block/blight_heart.png", base)
    emit(f"{WORLD}/block/blight_heart_glow.png", glow)


# ================================================================================================ armor layers


def armor_layers():
    """Equipment textures (64x32 humanoid layout): helmet/chest/arms/boots on `humanoid`, legs on `humanoid_leggings`."""
    for mat in ("scrap_plate", "blight_forged"):
        emit(f"{WORLD}/entity/equipment/humanoid/{mat}.png", armor_layer(mat, legs=False))
        emit(f"{WORLD}/entity/equipment/humanoid_leggings/{mat}.png", armor_layer(mat, legs=True))


# Humanoid boxes (u, v, w, h, d) and which piece they belong to.
HUMANOID = {
    "head": (0, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "arm": (40, 16, 4, 12, 4),
    "leg": (0, 16, 4, 12, 4),
}


def armor_layer(mat, legs):
    img = Image.new("RGBA", (64, 32), A.TRANSPARENT)
    rng = random.Random(f"{mat}_{legs}")
    scrap = mat == "scrap_plate"
    plate = A.SCRAP if scrap else A.GUNMETAL
    accent = A.RUST if scrap else A.BLIGHT
    trim = A.WOOD if scrap else A.BONE
    pbase = 4 if scrap else 2

    def px(part, face, x, y, w, h):
        edge_lit = x == 0 or y == 0
        edge_dark = x == w - 1 or y == h - 1
        i = pbase + (1 if edge_lit else 0) - (1 if edge_dark else 0)
        band = (y % 4 == 3) if part in ("body", "arm", "leg") else (y % 3 == 2)
        if band:
            i -= 1  # overlapping plate seams
        c = plate[max(0, min(len(plate) - 1, i))]
        r = rng.random()
        if scrap:
            if r < 0.14:
                c = accent[rng.randint(2, 4)]  # rust patches
            if band and x % 3 == 1:
                c = A.SCRAP[6]  # rivets on seams
        else:
            if r < 0.12:
                c = accent[rng.randint(2, 4)]  # flesh creeping between plates
            if band and (x + y) % 4 == 0:
                c = A.BLIGHT[4]
        return c

    def box(part, u, v, w, h, d, keep=None):
        for (x0, y0, x1, y1, face) in box_faces(u, v, w, h, d):
            for y in range(y0, y1):
                for x in range(x0, x1):
                    fx, fy = x - x0, y - y0
                    if keep and not keep(face, fx, fy, x1 - x0, y1 - y0):
                        continue
                    img.putpixel((x, y), px(part, face, fx, fy, x1 - x0, y1 - y0))

    if legs:
        box("body", *HUMANOID["body"], keep=lambda f, x, y, w, h: f in ("top", "bottom") or y >= 7)  # belt / hips
        box("leg", *HUMANOID["leg"], keep=lambda f, x, y, w, h: f in ("top",) or (f != "bottom" and y < 9))
        # belt strap
        u, v, w, h, d = HUMANOID["body"]
        for (x0, y0, x1, y1, face) in box_faces(u, v, w, h, d)[2:]:
            for x in range(x0, x1):
                img.putpixel((x, y0 + 7), trim[2])
                if x % 4 == 1:
                    img.putpixel((x, y0 + 7), trim[4])
        return img

    # helmet: skip the face's eye band so the visor reads as a slit
    def helmet_keep(face, x, y, w, h):
        if face == "front":
            if y == 4 and 1 <= x <= 6:
                return False  # visor slit (transparent)
            return y < 6
        if face == "side":
            return y < 7
        return face == "top"

    box("head", *HUMANOID["head"], keep=helmet_keep)
    u, v, w, h, d = HUMANOID["head"]
    hx0, hy0 = u + d, v + d  # front face origin: visor trim and (blight) glowing eyes
    for x in range(1, 7):
        img.putpixel((hx0 + x, hy0 + 5), trim[3])
        img.putpixel((hx0 + x, hy0 + 3), trim[2])
    if not scrap:
        img.putpixel((hx0 + 2, hy0 + 4), A.BLIGHT_GLOW[1])
        img.putpixel((hx0 + 5, hy0 + 4), A.BLIGHT_GLOW[1])
    box("body", *HUMANOID["body"])
    # chest centrepiece: scrap = hazard-painted plate, blight = exposed pulsing core
    bx0, by0 = 16 + 4, 16 + 4
    for y in range(2, 6):
        for x in range(2, 6):
            if scrap:
                c = A.HAZARD[3] if (x + y) % 2 == 0 else A.POLYMER[1]
            else:
                c = A.BLIGHT_GLOW[1] if 3 <= x <= 4 and 3 <= y <= 4 else A.BLIGHT[4]
            img.putpixel((bx0 + x, by0 + y), c)
    box("arm", *HUMANOID["arm"], keep=lambda f, x, y, w, h: f == "top" or (f != "bottom" and y < 6))
    box("leg", *HUMANOID["leg"], keep=lambda f, x, y, w, h: f == "bottom" or (f != "top" and y >= 7))
    # boot soles strap
    u, v, w, h, d = HUMANOID["leg"]
    for (x0, y0, x1, y1, face) in box_faces(u, v, w, h, d)[2:]:
        for x in range(x0, x1):
            img.putpixel((x, y1 - 1), trim[1])
    return img


# =========================================================================================================== main


def items():
    emit(f"{WORLD}/item/scrap.png", item_scrap())
    emit(f"{WORLD}/item/raw_gunmetal.png", item_raw_gunmetal())
    emit(f"{WORLD}/item/gunmetal_ingot.png", item_gunmetal_ingot())
    emit(f"{WORLD}/item/gunmetal_nugget.png", item_gunmetal_nugget())
    emit(f"{WORLD}/item/blight_core.png", item_blight_core())
    armor_icons()
    emit(f"{GUNS}/item/primary_ammo.png", item_primary_ammo())
    emit(f"{GUNS}/item/special_ammo.png", item_special_ammo())
    emit(f"{GUNS}/item/gun_barrel.png", item_gun_barrel())
    emit(f"{GUNS}/item/gun_receiver.png", item_gun_receiver())
    emit(f"{GUNS}/item/gun_stock.png", item_gun_stock())
    emit(f"{VEH}/item/wheel.png", item_wheel())
    emit(f"{VEH}/item/engine.png", item_engine())
    relics()
    emit(f"{GAME}/item/soul.png", item_soul())
    emit(f"{GAME}/item/reroll_charm.png", item_reroll_charm())
    emit(f"{GAME}/item/trinket_case.png", item_trinket_case())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", help="also write a contact sheet PNG here")
    args = ap.parse_args()
    items()
    blocks()
    armor_layers()
    kart_textures()
    import guns

    for path, img in guns.build().items():
        OUT[path] = img
    for path, img in OUT.items():
        A.save(img, path)
    print(f"wrote {len(OUT)} textures")
    if args.sheet:
        import sheet

        sheet.contact_sheet(OUT, args.sheet)
        print(f"sheet: {args.sheet}")


if __name__ == "__main__":
    main()
