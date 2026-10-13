"""Class icons: one 16x16 GUI sprite per class plus the Class Sigil item texture.

    python3 tools/assets/class_icons.py [--sheet docs/art/class-icons-sheet.png]

Every class in `data/*/classes/*.json` gets a round sigil disc (dark stone, a ring in the class's accent) around a
12x12 glyph from GLYPHS, written to `assets/<ns>/textures/gui/sprites/class/<name>.png` (main resources, so the
server can check the sprite exists). The sigil item is painted to the client resources. Same rules as STYLE.md:
light from the top-left, sel-out outlines, ramps not tints. A class without a glyph gets a generic rune.
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

# Accent ring per class (the disc is the same for all so the row reads as one set).
ACCENTS = {"warden": A.SCRAP[5], "striker": BLOOD[4], "arcanist": A.VOID[5], "ranger": LEAF[4]}

# ---- Glyphs: 12x12, '.' transparent. (rows, mats, fixed) ----
GLYPHS = {
    "warden": ([  # a heavy shield
        "............",
        ".ssssssssss.",
        ".sppppppppss",
        ".sppppppppss",
        ".sppggggppss",
        ".sppppppppss",
        "..spppppps..",
        "..spppppps..",
        "...sppppss..",
        "....spps....",
        ".....ss.....",
        "............",
    ][:12], {"s": M(A.SCRAP, 2), "p": M(A.SCRAP, 4), "g": M(A.GOLD, 3, edges=False)}, {}),
    "striker": ([  # a blade with a blood drop
        "..........b.",
        ".........bb.",
        "........bb..",
        ".......bb...",
        "......bb....",
        ".....bb.....",
        "..h.bb......",
        "..hhb.......",
        "..hhh.......",
        ".h..h.......",
        "....r.......",
        "....r.......",
    ], {"b": M(A.GUNMETAL, 5), "h": M(A.WOOD, 3)}, {"r": BLOOD[4]}),
    "arcanist": ([  # an orb with a spark
        "............",
        "....oooo....",
        "...oooooo...",
        "..oooowooo..",
        "..ooowwooo..",
        "..oooowooo..",
        "..oooooooo..",
        "...oooooo...",
        "....oooo....",
        ".....hh.....",
        "....hhhh....",
        "............",
    ], {"o": M(A.VOID, 3), "h": M(A.BONE, 3)}, {"w": A.VOID[6]}),
    "ranger": ([  # an arrow in flight
        "............",
        "..........a.",
        ".........aa.",
        "........aaa.",
        ".......a.aa.",
        "......a.....",
        ".....a......",
        "....a.......",
        "...a........",
        "..ff........",
        ".fff........",
        "............",
    ], {"a": M(LEAF, 3), "f": M(A.BONE, 3)}, {}),
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
        print(f"warning: no glyph for class '{name}', using the generic rune", file=sys.stderr)
        name = None
    rows, mats, fixed = GLYPHS[name]
    return A.paint(rows, mats, fixed, f"class_{name}")


def icon(name):
    """A round dark disc with the class's accent ring and its glyph."""
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
    return A.sprite(SIGIL, mats, {}, "class_sigil")


def classes():
    for path in sorted(glob.glob(os.path.join(DATA, "*", "classes", "*.json"))):
        ns = path.split(os.sep)[-3]
        with open(path) as f:
            yield ns, os.path.splitext(os.path.basename(path))[0], json.load(f)


def main(argv):
    made = {}
    for ns, name, data in classes():
        img = icon(name)
        A.save(img, os.path.join(ASSETS, ns, "textures/gui/sprites/class", name + ".png"))
        made[name] = img
        expected = f"{ns}:class/{name}"
        if data.get("icon") != expected:
            print(f"warning: {name}.json icon is {data.get('icon')!r}, sprite painted as {expected}", file=sys.stderr)
    item = sigil()
    A.save(item, os.path.join(CLIENT_ASSETS, "blocksouls_game", "textures/item/class_sigil.png"))
    made["class_sigil"] = item
    print(f"painted {len(made) - 1} class icons + the sigil")
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
