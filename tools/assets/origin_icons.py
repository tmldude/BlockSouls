"""Origin icons: one 16x16 GUI sprite per origin plus the Origin Sigil item texture.

    python3 tools/assets/origin_icons.py [--sheet docs/art/origin-icons-sheet.png]

Every origin in `data/*/origins/*.json` gets a round sigil disc (dark stone, a ring in the origin's accent) around a
12x12 glyph from GLYPHS, written to `assets/<ns>/textures/gui/sprites/origin/<name>.png` (main resources, so the
server can check the sprite exists). The sigil item is painted to the client resources. Same rules as STYLE.md:
light from the top-left, sel-out outlines, ramps not tints. An origin without a glyph gets a generic rune.
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
CLIENT_ASSETS = os.path.join(ROOT, "game/src/client/resources/assets")

M = A.Mat
BLOOD = A.ramp("#2a0306", "#4d070d", "#7a0d16", "#a8161f", "#d02a2e", "#ef5a52")
SEA = A.ramp("#06202c", "#0d4152", "#176a80", "#2a9bb1", "#5fd0dc", "#c3f3f5")
SKY = A.ramp("#2b3a52", "#4f6a8e", "#7f9dc2", "#b7cde6", "#eef5ff")
LEAF = A.ramp("#0c2410", "#164a1c", "#24702a", "#38963a", "#5cb84e", "#94dc78")
WHITE = A.rgb("#f4f7fa")
WIND = A.rgb("#cfe9ff")

# Accent ring per origin (the disc is the same for all so the row reads as one set).
ACCENTS = {
    "drifter": WIND, "webslinger": A.PALE[3], "tidecaller": SEA[4], "ashborn": A.EMBER[3], "zephyr": SKY[3],
    "juggernaut": A.SCRAP[5], "gunslinger": A.BRASS[4], "tinkerer": A.HAZARD[3], "bloodbound": BLOOD[4], "gambler": A.VOID[5],
}

# ---- Glyphs: 12x12, '.' transparent. (rows, mats, fixed) ----
GLYPHS = {
    "drifter": ([  # two stacked chevrons: the jump chain
        "............",
        ".....ww.....",
        "....wwww....",
        "...ww..ww...",
        "..ww....ww..",
        "............",
        ".....ww.....",
        "....wwww....",
        "...ww..ww...",
        "..ww....ww..",
        "............",
        "............",
    ], {}, {"w": WIND}),
    "webslinger": ([  # a web strand with a knot, swinging
        "s...........",
        ".s..........",
        "..s.........",
        "...s........",
        "....s.......",
        ".....s......",
        "......ss....",
        ".......sss..",
        "........kkk.",
        "........kkk.",
        ".........k..",
        "............",
    ], {"k": M(A.PALE, 2)}, {"s": A.PALE[4]}),
    "tidecaller": ([  # waves
        "............",
        "............",
        ".ww..ww..ww.",
        "w..ww..ww..w",
        "............",
        "..ww..ww..w.",
        ".w..ww..ww..",
        "............",
        ".ww..ww..ww.",
        "w..ww..ww..w",
        "............",
        "............",
    ], {}, {"w": SEA[4]}),
    "ashborn": ([  # a flame
        ".....f......",
        "....ff......",
        "....fff.....",
        "...ffff..f..",
        "...fffff.f..",
        "..ffoffffff.",
        "..ffooofff..",
        "..fooooofff.",
        "..fooyoooff.",
        "...ooyyoof..",
        "....oyyoo...",
        ".....ooo....",
    ], {"f": M(A.EMBER, 2), "o": M(A.EMBER, 3), "y": M(A.EMBER, 4, edges=False)}, {}),
    "zephyr": ([  # a wing
        "............",
        "..........w.",
        ".......wwww.",
        ".....wwwwww.",
        "...wwwwwwww.",
        "..wwwwwwwww.",
        ".wwwwwww.ww.",
        ".wwwww...w..",
        "..www.......",
        "...w........",
        "............",
        "............",
    ], {"w": M(SKY, 2)}, {}),
    "juggernaut": ([  # a heavy shield
        "............",
        ".ssssssssss.",
        ".sppppppppss",
        ".sppppppppss",
        ".sppppppppss",
        ".sppppppppss",
        "..spppppps..",
        "..spppppps..",
        "...sppppss..",
        "....spps....",
        ".....ss.....",
        "............",
    ], {"s": M(A.SCRAP, 2), "p": M(A.SCRAP, 4)}, {}),
    "gunslinger": ([  # a revolver cylinder seen from the side, with a cartridge
        "............",
        "..gggggg....",
        ".ggggggggg..",
        ".gg.gggg.gg.",
        ".gggggggggg.",
        ".gg.gggg.gg.",
        ".ggggggggg..",
        "..gggggg....",
        "............",
        "....bbbbbb..",
        "....bbbbbbb.",
        "............",
    ], {"g": M(A.GUNMETAL, 4), "b": M(A.BRASS, 3)}, {}),
    "tinkerer": ([  # a wrench
        "..hh........",
        ".hhhh.......",
        ".h..hh......",
        ".h...hh.....",
        "..hh..hh....",
        "...hhh.hh...",
        "....hhh.hh..",
        ".....hhh.hh.",
        "......hhh.h.",
        ".......hhhh.",
        "........hh..",
        "............",
    ], {"h": M(A.HAZARD, 3)}, {}),
    "bloodbound": ([  # a drop
        ".....b......",
        ".....b......",
        "....bbb.....",
        "....bbb.....",
        "...bbbbb....",
        "...bbbbb....",
        "..bbbbbbb...",
        "..bbhbbbb...",
        "..bbhbbbb...",
        "...bbbbb....",
        "....bbb.....",
        "............",
    ], {"b": M(BLOOD, 3)}, {"h": BLOOD[5]}),
    "gambler": ([  # a card with a pip
        "............",
        "..cccccccc..",
        "..cwwwwwwc..",
        "..cwwpwwwc..",
        "..cwwwwwwc..",
        "..cwwwwwwc..",
        "..cwwwwwwc..",
        "..cwwwpwwc..",
        "..cwwwwwwc..",
        "..cccccccc..",
        "............",
        "............",
    ], {"c": M(A.VOID, 3), "w": M(A.BONE, 4, edges=False)}, {"p": A.VOID[2]}),
    None: ([  # generic rune
        "............",
        "....rrrr....",
        "...r....r...",
        "...r.rr.r...",
        "...r.rr.r...",
        "...r....r...",
        "....rrrr....",
        ".....rr.....",
        ".....rr.....",
        "....rrrr....",
        "............",
        "............",
    ], {"r": M(A.BONE, 3)}, {}),
}

SIGIL = [  # 16x16 item: a bone disc with a carved rune, lit top-left, gold inlay
    "................",
    ".....dddddd.....",
    "...ddbbbbbbdd...",
    "..dbbbbbbbbbbd..",
    ".dbbbbggggbbbbd.",
    ".dbbbgbbbbgbbbd.",
    "dbbbbgbggbgbbbbd",
    "dbbbbgbggbgbbbbd",
    "dbbbbgbbbbgbbbbd",
    "dbbbbbggggbbbbbd",
    ".dbbbbbggbbbbbd.",
    ".dbbbbbggbbbbbd.",
    "..dbbbbggbbbbd..",
    "...ddbbbbbbdd...",
    ".....dddddd.....",
    "................",
]


def glyph(name):
    if name not in GLYPHS:
        print(f"warning: no glyph for origin '{name}', using the generic rune", file=sys.stderr)
        name = None
    rows, mats, fixed = GLYPHS[name]
    return A.paint(rows, mats, fixed, f"origin_{name}")


def icon(name):
    """A round dark disc with the origin's accent ring and its glyph."""
    accent = ACCENTS.get(name, A.BONE[3])
    img = Image.new("RGBA", (16, 16), A.TRANSPARENT)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 7.6:
                if d >= 6.6:
                    # The ring: lit top-left, shadowed bottom-right.
                    lit = (x + y) < 15
                    img.putpixel((x, y), A.lerp(accent, A.rgb("#000000"), 0.15 if lit else 0.45))
                else:
                    img.putpixel((x, y), A.lerp(A.DEEPSLATE[3], A.DEEPSLATE[0], (x + y) / 30))
    g = Image.new("RGBA", (14, 14), A.TRANSPARENT)
    g.alpha_composite(glyph(name), (1, 1))
    img.alpha_composite(A.outline(g, factor=0.3), (1, 1))
    return img


def sigil():
    mats = {"b": M(A.BONE, 3, 0.25), "d": M(A.BONE, 1, edges=False), "g": M(A.GOLD, 3, edges=False)}
    return A.sprite(SIGIL, mats, {}, "origin_sigil")


def origins():
    for path in sorted(glob.glob(os.path.join(DATA, "*", "origins", "*.json"))):
        ns = path.split(os.sep)[-3]
        with open(path) as f:
            yield ns, os.path.splitext(os.path.basename(path))[0], json.load(f)


def main(argv):
    made = {}
    for ns, name, data in origins():
        img = icon(name)
        A.save(img, os.path.join(ASSETS, ns, "textures/gui/sprites/origin", name + ".png"))
        made[name] = img
        expected = f"{ns}:origin/{name}"
        if data.get("icon") != expected:
            print(f"warning: {name}.json icon is {data.get('icon')!r}, sprite painted as {expected}", file=sys.stderr)
    item = sigil()
    A.save(item, os.path.join(CLIENT_ASSETS, "blocksouls_game", "textures/item/origin_sigil.png"))
    made["origin_sigil"] = item
    print(f"painted {len(made) - 1} origin icons + the sigil")
    if "--sheet" in argv:
        sheet(made, argv[argv.index("--sheet") + 1])


def sheet(images, path, cell=72, cols=6):
    label = 12
    names = sorted(images)
    rows = (len(names) + cols - 1) // cols
    out = Image.new("RGBA", (cols * cell, rows * (cell + label)), (34, 32, 38, 255))
    dr = ImageDraw.Draw(out)
    for i, name in enumerate(names):
        x = (i % cols) * cell
        y = (i // cols) * (cell + label)
        out.alpha_composite(images[name].resize((cell - 8, cell - 8), Image.NEAREST), (x + 4, y + 4))
        dr.text((x + 2, y + cell - 2), name[:12], fill=(220, 220, 220, 255))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path)


if __name__ == "__main__":
    main(sys.argv[1:])
