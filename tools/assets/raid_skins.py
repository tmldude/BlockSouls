#!/usr/bin/env python3
"""Placeholder art for the final raid (bosses module).

    python3 tools/assets/raid_skins.py

- entity/final_dragon.png: box-UV skin for FinalDragonModel (256x128): void-black hide, bone ridge, horns, claws and
  wing bones, dusky membranes.
- entity/final_dragon_glow.png: violet cracks, eyes, membrane veins (always lit).
- entity/final_dragon_enraged_glow.png: P3 / soft enrage: white-hot cracks spreading over everything.
- entity/raid_crystal.png: vanilla End crystal UV layout (64x32), bone-white glass around a violet core.
- item/final_dragon_spawn_egg.png, item/final_dragon_trophy.png (the Hollow Crown).

Box list mirrors bosses/src/client/.../raid/FinalDragonModel.java; update both together. Same input -> same image.
"""

import hashlib
import os
import random

from PIL import Image

from boss_skins import faces, fill
from gen import rgb, shaped

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "bosses", "src", "main", "resources", "assets", "blocksouls_bosses", "textures")

# (u, v, w, h, d) for every box in FinalDragonModel.
BOXES = {
    "body": (0, 0, 14, 10, 30),
    "spine": (88, 0, 2, 3, 30),
    "neck": (152, 0, 6, 6, 8),
    "head": (180, 0, 10, 8, 14),
    "jaw": (180, 22, 8, 3, 12),
    "horn": (228, 0, 2, 7, 2),
    "big_horn": (236, 0, 2, 10, 2),
    "wing_arm": (0, 40, 36, 3, 4),
    "wing_bone": (0, 47, 34, 2, 2),
    "membrane": (0, 52, 36, 0, 26),
    "membrane_tip": (0, 78, 34, 0, 26),
    "tail": (124, 52, 5, 5, 10),
    "tail_blade": (124, 67, 1, 6, 10),
    "leg": (154, 52, 4, 7, 4),
    "shin": (170, 52, 3, 7, 3),
    "foot": (182, 52, 5, 2, 6),
    "spike": (204, 52, 3, 3, 6),
}
VOID_HIDE = ["#07060a", "#0e0b14", "#16111f", "#1f182b", "#2a2139"]
BONE = ["#3a342a", "#6b6252", "#a39880", "#d1c7ad", "#f2ecdc"]
MEMBRANE = ["#0b0812", "#140f1f", "#1d1530", "#271c40"]
VIOLET = ["#3b0f6e", "#6a2bc4", "#9b4dff", "#c9a2ff", "#f2ecff"]
BONE_PARTS = ("spine", "horn", "big_horn", "wing_arm", "wing_bone", "tail_blade", "foot", "spike")


def rng_for(name):
    return random.Random(int(hashlib.sha256(name.encode()).hexdigest(), 16))


def save(img, *path):
    out = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("wrote", os.path.relpath(out))


def rects(name):
    return faces(BOXES[name])


def paint_hide(img, rng, name, pal):
    for kind, (x0, y0, x1, y1) in rects(name).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                tone = pal[rng.choice((0, 1, 1, 2, 2, 3))]
                if kind == "top" and rng.random() < 0.3:
                    tone = pal[min(len(pal) - 1, 3)]
                if kind == "bottom":
                    tone = pal[0]
                # Gaunt: rib shadows across the flanks every 4 px.
                if name == "body" and kind in ("right", "left") and (x - x0) % 4 == 0:
                    tone = pal[0]
                img.putpixel((x, y), rgb(tone) + (255,))


def paint_bone(img, rng, name):
    for kind, (x0, y0, x1, y1) in rects(name).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                tone = BONE[rng.choice((2, 3, 3, 4))]
                if kind == "bottom" or (y - y0) % 5 == 4:
                    tone = BONE[1]
                img.putpixel((x, y), rgb(tone) + (255,))


def paint_membrane(img, rng, name):
    x0, y0, x1, y1 = rects(name)["top"]
    bx0, by0, bx1, by1 = rects(name)["bottom"]
    for (a0, b0, a1, b1) in ((x0, y0, x1, y1), (bx0, by0, bx1, by1)):
        for y in range(b0, b1):
            for x in range(a0, a1):
                tone = MEMBRANE[rng.choice((1, 2, 2, 3))]
                # Ragged trailing edge: holes torn in the back third.
                if y > b0 + (b1 - b0) * 0.75 and rng.random() < 0.25 + (y - b0) / (b1 - b0) * 0.3:
                    img.putpixel((x, y), (0, 0, 0, 0))
                    continue
                img.putpixel((x, y), rgb(tone) + (255,))
        # Darker finger-bones fanning back.
        for f in range(4):
            fx = a0 + (a1 - a0) * (f + 1) // 5
            for y in range(b0, b1 - 2):
                x = fx + (y - b0) // 4
                if a0 <= x < a1:
                    img.putpixel((x, y), rgb(BONE[1]) + (255,))


def final_dragon():
    rng = rng_for("final_dragon")
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    for name in BOXES:
        if name.startswith("membrane"):
            paint_membrane(img, rng, name)
        elif name in BONE_PARTS:
            paint_bone(img, rng, name)
        else:
            paint_hide(img, rng, name, VOID_HIDE)
    # Bone plates on the skull's brow and the jaw's teeth line.
    hx0, hy0, hx1, hy1 = rects("head")["top"]
    fill(img, (hx0 + 2, hy0, hx1 - 2, hy0 + 4), rgb(BONE[3]) + (255,))
    jx0, jy0, jx1, jy1 = rects("jaw")["front"]
    for x in range(jx0, jx1):
        if x % 2 == 0:
            img.putpixel((x, jy0), rgb(BONE[4]) + (255,))
    # Dark eye sockets (the glow lights them).
    fx0, fy0, fx1, fy1 = rects("head")["front"]
    for ex in (fx0 + 1, fx1 - 3):
        fill(img, (ex, fy0 + 2, ex + 2, fy0 + 4), rgb("#000000") + (255,))
    return img


def crack(img, rng, rect, colors, count, length):
    x0, y0, x1, y1 = rect
    if x1 - x0 < 2 or y1 - y0 < 2:
        return
    for _ in range(count):
        x, y = rng.randrange(x0, x1), rng.randrange(y0, y1)
        for _ in range(rng.randint(length // 2, length)):
            if x0 <= x < x1 and y0 <= y < y1:
                img.putpixel((x, y), rgb(rng.choice(colors)) + (255,))
            x += rng.choice((-1, 0, 1))
            y += rng.choice((0, 1, 1))


def final_dragon_glow():
    rng = rng_for("final_dragon_glow")
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    # Eyes.
    fx0, fy0, fx1, fy1 = rects("head")["front"]
    for ex in (fx0 + 1, fx1 - 3):
        fill(img, (ex, fy0 + 2, ex + 2, fy0 + 3), rgb(VIOLET[4]) + (255,))
        fill(img, (ex, fy0 + 3, ex + 2, fy0 + 4), rgb(VIOLET[2]) + (255,))
    # Violet cracks along the flanks, back, neck and tail.
    for name, n in (("body", 9), ("neck", 2), ("tail", 2), ("head", 2)):
        for kind in ("top", "right", "left"):
            crack(img, rng, rects(name)[kind], VIOLET[1:4], n, 7)
    # Throat glow inside the jaw.
    jx0, jy0, jx1, jy1 = rects("jaw")["top"]
    fill(img, (jx0 + 1, jy0 + 1, jx1 - 1, jy1 - 1), rgb(VIOLET[2]) + (255,))
    # Faint veins in the membranes.
    for name in ("membrane", "membrane_tip"):
        x0, y0, x1, y1 = rects(name)["top"]
        crack(img, rng, (x0, y0, x1, y0 + (y1 - y0) * 3 // 4), VIOLET[0:2], 5, 12)
    # The crown's tips.
    for name in ("horn", "big_horn"):
        x0, y0, x1, y1 = rects(name)["top"]
        fill(img, (x0, y0, x1, y1), rgb(VIOLET[3]) + (255,))
    return img


def final_dragon_enraged_glow():
    rng = rng_for("final_dragon_enraged_glow")
    img = final_dragon_glow()
    hot = ["#c9a2ff", "#e6d4ff", "#ffffff"]
    for name, n in (("body", 18), ("neck", 5), ("tail", 4), ("head", 4), ("jaw", 2), ("leg", 2), ("shin", 2)):
        for kind in ("top", "right", "left", "front", "back"):
            crack(img, rng, rects(name)[kind], hot, n, 6)
    for name in ("wing_arm", "wing_bone"):
        for kind in ("top", "front", "back"):
            crack(img, rng, rects(name)[kind], VIOLET[2:], 6, 10)
    return img


def raid_crystal():
    """Vanilla End crystal layout: glass cube 8x8x8 at (0,0), core cube at (32,0), base 12x4x12 at (0,16)."""
    rng = rng_for("raid_crystal")
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for (x0, y0, x1, y1) in faces((0, 0, 8, 8, 8)).values():
        for y in range(y0, y1):
            for x in range(x0, x1):
                edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
                if edge:
                    img.putpixel((x, y), rgb(BONE[4]) + (230,))
                elif rng.random() < 0.15:
                    img.putpixel((x, y), rgb(BONE[3]) + (110,))
    for (x0, y0, x1, y1) in faces((32, 0, 8, 8, 8)).values():
        for y in range(y0, y1):
            for x in range(x0, x1):
                img.putpixel((x, y), rgb(rng.choice(VIOLET[1:4])) + (255,))
    for (x0, y0, x1, y1) in faces((0, 16, 12, 4, 12)).values():
        for y in range(y0, y1):
            for x in range(x0, x1):
                img.putpixel((x, y), rgb(rng.choice(BONE[0:2])) + (255,))
    return img


def trophy():
    """The Hollow Crown: a ring of bone horns around a violet glint."""
    rows = [
        "................",
        "................",
        "..#....#....#...",
        "..#...###...#...",
        "..##..#v#..##...",
        "..###.###.###...",
        "..###########...",
        "..##vv###vv##...",
        "..###########...",
        "..+++++++++++...",
        "...---------....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    colors = {"#": BONE[3], "+": BONE[4], "-": BONE[1], "v": VIOLET[2]}
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in colors:
                img.putpixel((x, y + 2), rgb(colors[c]) + (255,))
    return img


def main():
    save(final_dragon(), "entity", "final_dragon.png")
    save(final_dragon_glow(), "entity", "final_dragon_glow.png")
    save(final_dragon_enraged_glow(), "entity", "final_dragon_enraged_glow.png")
    save(raid_crystal(), "entity", "raid_crystal.png")
    save(shaped(rng_for("final_dragon_spawn_egg"), ["#07060a", "#1f182b", "#6a2bc4", "#9b4dff", "#d1c7ad"], "item"), "item",
         "final_dragon_spawn_egg.png")
    save(trophy(), "item", "final_dragon_trophy.png")


if __name__ == "__main__":
    main()
