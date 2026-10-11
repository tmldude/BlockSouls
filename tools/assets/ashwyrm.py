#!/usr/bin/env python3
"""Placeholder art for the Ashwyrm (bosses module).

    python3 tools/assets/ashwyrm.py

- entity/ashwyrm.png: box-UV skin, ash-grey scales with darker plates, pale belly, bone horns and claws.
- entity/ashwyrm_glow.png: ember cracks across the scales, eyes and the inside of the jaws (always lit).
- entity/ashwyrm_enraged_glow.png: phase 3, the cracks spread and the wing membranes smoulder.
- item/ashwyrm_spawn_egg.png, item/ashwyrm_scale.png, item/ember_barrel.png.

Box list mirrors bosses/src/client/.../ashwyrm/AshwyrmModel.java (texture 256x128); update both together.
Same input -> same image. Real art replaces these at the same paths. Kept out of boss_skins.py so the parallel boss
sessions don't edit the same file.
"""

import hashlib
import os
import random
import sys

from PIL import Image

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import artlib as A  # noqa: E402
from boss_skins import faces, fill  # noqa: E402
from bosses import save  # noqa: E402
from gen import PALETTES, rgb  # noqa: E402

W, H = 256, 128
# (u, v, w, h, d), as in AshwyrmModel.createBodyLayer.
BOXES = {
    "body": (0, 0, 20, 12, 36),
    "spike": (120, 70, 1, 4, 4),
    "neck": (0, 48, 8, 8, 10),
    "neck2": (36, 48, 7, 7, 10),
    "skull": (196, 0, 10, 7, 10),
    "snout": (196, 17, 8, 4, 7),
    "jaw": (196, 28, 8, 2, 9),
    "horn": (86, 70, 2, 2, 6),
    "front_leg": (170, 48, 6, 12, 6),
    "rear_leg": (194, 48, 7, 12, 8),
    "tail": (70, 48, 8, 8, 12),
    "tail2": (110, 48, 6, 6, 10),
    "tail3": (142, 48, 4, 4, 10),
    "fin": (102, 70, 1, 4, 8),
    "wing_bone": (0, 70, 20, 3, 3),
    "membrane": (112, 0, 20, 1, 22),
    "tip_bone": (46, 70, 18, 2, 2),
    "tip_membrane": (112, 23, 18, 1, 20),
}
ASH = PALETTES["ash"]
EMBER = PALETTES["ember"]
BELLY = ["#3a3633", "#5a534e", "#7a716a", "#9a8f86", "#b9ada2"]
BONE = PALETTES["bone"]
MEMBRANE = ["#1d1a19", "#2c2624", "#3d3431", "#4e4340", "#62554f"]


def rng_for(name):
    return random.Random(int(hashlib.sha256(name.encode()).hexdigest(), 16))


def px(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def scales(img, rng, rect, pal, belly=False):
    """Overlapping scale rows: each scale a lighter top edge fading into a dark rim."""
    x0, y0, x1, y1 = rect
    for y in range(y0, y1):
        row = (y - y0) // 2
        for x in range(x0, x1):
            k = (x - x0 + (row % 2) * 2) % 4
            shade = 3 if (y - y0) % 2 == 0 and k in (1, 2) else 2
            if k == 0:
                shade = 1
            if rng.random() < 0.12:
                shade += rng.choice((-1, 1))
            px(img, x, y, rgb((BELLY if belly else pal)[max(0, min(4, shade))]) + (255,))


def paint_scaled_box(img, rng, box, belly_face="bottom"):
    for kind, rect in faces(box).items():
        if kind == belly_face:
            scales(img, rng, rect, ASH, belly=True)
        elif kind == "top":
            scales(img, rng, rect, ASH)
            # A darker ridge down the spine.
            x0, y0, x1, y1 = rect
            mid = (x0 + x1) // 2
            for y in range(y0, y1):
                px(img, mid, y, rgb(ASH[0]) + (255,))
        else:
            scales(img, rng, rect, ASH)


def skin():
    rng = rng_for("ashwyrm")
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for name in ("body", "neck", "neck2", "skull", "snout", "tail", "tail2", "tail3", "front_leg", "rear_leg"):
        paint_scaled_box(img, rng, BOXES[name])
    # Jaw: scaled outside, dark mouth on top (the inside), bone teeth along the front edge.
    paint_scaled_box(img, rng, BOXES["jaw"], belly_face="bottom")
    f = faces(BOXES["jaw"])
    fill(img, f["top"], rgb("#1a0a06") + (255,))
    x0, y0, x1, y1 = f["top"]
    for x in range(x0, x1, 2):
        px(img, x, y0, rgb(BONE[4]) + (255,))
    # Teeth under the snout.
    x0, y0, x1, y1 = faces(BOXES["snout"])["bottom"]
    fill(img, (x0, y0, x1, y1), rgb("#1a0a06") + (255,))
    for x in range(x0, x1, 2):
        px(img, x, y1 - 1, rgb(BONE[4]) + (255,))
    # Eyes: dark sockets on the skull's sides (the glow layer lights them).
    for kind in ("left", "right"):
        x0, y0, x1, y1 = faces(BOXES["skull"])[kind]
        fill(img, (x0 + 1, y0 + 2, x0 + 4, y0 + 4), rgb("#0c0605") + (255,))
    # Bone: horns, spikes, fin, claws.
    for name in ("horn", "spike", "fin"):
        for kind, rect in faces(BOXES[name]).items():
            x0, y0, x1, y1 = rect
            for y in range(y0, y1):
                for x in range(x0, x1):
                    px(img, x, y, rgb(BONE[2] if (x + y) % 3 else BONE[3]) + (255,))
    for name in ("front_leg", "rear_leg"):
        for kind in ("front", "left", "right", "back"):
            x0, y0, x1, y1 = faces(BOXES[name])[kind]
            for x in range(x0, x1):
                px(img, x, y1 - 1, rgb(BONE[3] if x % 2 else BONE[1]) + (255,))
    # Wings: dark leathery membranes with lighter veins; bones in grey.
    for name in ("membrane", "tip_membrane"):
        for kind, rect in faces(BOXES[name]).items():
            x0, y0, x1, y1 = rect
            for y in range(y0, y1):
                for x in range(x0, x1):
                    tone = 1 + (rng.random() < 0.3)
                    if (x - x0) % 5 == 0 or (y - y0) % 7 == 0:
                        tone = 3
                    px(img, x, y, rgb(MEMBRANE[tone]) + (255,))
    for name in ("wing_bone", "tip_bone"):
        for kind, rect in faces(BOXES[name]).items():
            scales(img, rng, rect, ASH)
    return img


def cracks(img, rng, rect, n, length, colors):
    """Branching ember cracks wandering across a face."""
    x0, y0, x1, y1 = rect
    if x1 - x0 < 2 or y1 - y0 < 2:
        return
    for _ in range(n):
        x, y = rng.randrange(x0, x1), rng.randrange(y0, y1)
        for i in range(rng.randint(length // 2, length)):
            if x0 <= x < x1 and y0 <= y < y1:
                px(img, x, y, rgb(rng.choice(colors)) + (255,))
            x += rng.choice((-1, 0, 1, 1))
            y += rng.choice((-1, 0, 1))


def glow(enraged):
    rng = rng_for("ashwyrm_enraged_glow" if enraged else "ashwyrm_glow")
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    hot = EMBER[3:] if not enraged else EMBER[2:]
    scale = 2 if enraged else 1
    for name, per_face in (("body", 4), ("neck", 1), ("neck2", 1), ("tail", 1), ("tail2", 1), ("front_leg", 1), ("rear_leg", 1)):
        for kind, rect in faces(BOXES[name]).items():
            if kind == "bottom":
                continue
            cracks(img, rng, rect, per_face * scale, 8 if name == "body" else 5, hot)
    if not enraged:
        # Eyes and the furnace inside the jaws.
        for kind in ("left", "right"):
            x0, y0, x1, y1 = faces(BOXES["skull"])[kind]
            fill(img, (x0 + 1, y0 + 2, x0 + 4, y0 + 4), rgb(EMBER[4]) + (255,))
            px(img, x0 + 2, y0 + 2, rgb("#fff0c0") + (255,))
        x0, y0, x1, y1 = faces(BOXES["jaw"])["top"]
        for y in range(y0 + 1, y1):
            for x in range(x0 + 1, x1 - 1):
                if rng.random() < 0.6:
                    px(img, x, y, rgb(rng.choice(EMBER[2:])) + (255,))
    else:
        # Smouldering edges on the wing membranes.
        for name in ("membrane", "tip_membrane"):
            for kind in ("top", "bottom"):
                x0, y0, x1, y1 = faces(BOXES[name])[kind]
                for x in range(x0, x1):
                    for y in range(y1 - 3, y1):
                        if rng.random() < 0.5:
                            px(img, x, y, rgb(rng.choice(EMBER[2:4])) + (255,))
    return img


def item_scale():
    rows = [
        "................",
        "................",
        "......aaaa......",
        "....aaaaaaaa....",
        "...aaaaccaaaa...",
        "..aaaaaacaaaaa..",
        "..aaaaaacaaaaa..",
        "..aaaaacaaaaaa..",
        "..aaaaaacaaaaa..",
        "...aaaaaccaaa...",
        "...aaaaaacaaa...",
        "....aaaaacaa....",
        ".....aaaaaa.....",
        "......aaaa......",
        ".......aa.......",
        "................",
    ]
    ash = A.ramp(*["#161616", "#2e2b2a", "#4c4643", "#6e6661", "#958a83", "#b5aaa2"])
    return A.sprite(rows, {"a": A.Mat(ash, 3, 0.2)}, {"c": A.EMBER[4]}, "ashwyrm_scale")


def item_barrel():
    rows = [
        "................",
        "............eee.",
        "...........mmmme",
        "..........mmmmm.",
        ".........mcmcm..",
        "........mmmmm...",
        ".......mcmcm....",
        "......mmmmm.....",
        ".....mcmcm......",
        "....bbbbm.......",
        "...bbbbb........",
        "..bbbbbb........",
        "..bbbbb.........",
        "..bbbb..........",
        "................",
        "................",
    ]
    return A.sprite(rows, {"m": A.Mat(A.GUNMETAL, 3), "b": A.Mat(A.GUNMETAL, 2)},
                    {"c": A.EMBER[3], "e": A.EMBER[4]}, "ember_barrel")


def spawn_egg():
    rng = rng_for("ashwyrm_spawn_egg")
    rows = [
        "................",
        "......++++......",
        "....++####-.....",
        "...+#######-....",
        "..+#########-...",
        "..##########-...",
        ".+###########-..",
        ".############-..",
        ".############-..",
        "..##########-...",
        "..-#########-...",
        "...-#######-....",
        "....--####-.....",
        "......----......",
        "................",
        "................",
    ]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == ".":
                continue
            tone = {"+": 4, "#": 2, "-": 1}[c]
            color = rgb(ASH[tone])
            if c == "#" and rng.random() < 0.18:
                color = rgb(rng.choice(EMBER[2:4]))
            img.putpixel((x, y), color + (255,))
    return img


def main():
    save(skin(), "entity", "ashwyrm.png")
    save(glow(False), "entity", "ashwyrm_glow.png")
    save(glow(True), "entity", "ashwyrm_enraged_glow.png")
    save(spawn_egg(), "item", "ashwyrm_spawn_egg.png")
    save(item_scale(), "item", "ashwyrm_scale.png")
    save(item_barrel(), "item", "ember_barrel.png")


if __name__ == "__main__":
    main()
