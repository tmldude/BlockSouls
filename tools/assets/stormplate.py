#!/usr/bin/env python3
"""Art for the Stormplate Knight and its dragonscale armor (bosses module).

    python3 tools/assets/stormplate.py        # writes into bosses/src/main/resources/assets/blocksouls_bosses/textures

- entity/stormplate_knight.png: 128x128 box-UV skin. The box list mirrors StormplateKnightModel.createBodyLayer
  (bosses/src/client/.../stormplate/); change both together.
- entity/stormplate_knight_glow.png: always lit (visor, spearhead edge, gold seams).
- entity/stormplate_knight_storm_glow.png: phase 2, lightning crawling over the plate.
- item/stormplate_knight_spawn_egg.png, item/dragonscale_<piece>.png (16x16 icons, STYLE.md rules).
- entity/equipment/humanoid[_leggings]/dragonscale.png: the worn armor (64x32 humanoid layout).

Kept in its own file (not boss_skins.py) so parallel boss sessions don't collide. Deterministic: same pixels each run.
"""

import os
import random
import sys

from PIL import Image

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import artlib as A  # noqa: E402
from gen import PALETTES, shaped  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(ROOT, "bosses/src/main/resources/assets/blocksouls_bosses/textures")

# Dragon scale: deep storm blue, dark -> light. Gold trim from artlib.GOLD; lightning from STEEL_BLUE's top end.
SCALE = A.ramp("#080e1a", "#0f1828", "#16243d", "#1e3152", "#294270", "#36578f", "#4b74b3", "#7097d1")
CLOTH = A.ramp("#07090f", "#0d111c", "#141a2a", "#1c2438", "#262f47")
SHAFT = A.ramp("#120d0a", "#1f1611", "#2e2119", "#3f2e22", "#52402f")
STEEL = A.ramp("#2a2f38", "#454b57", "#6a7280", "#99a2b0", "#c9d0da", "#eef2f7")
GOLD = A.GOLD
GLOW = A.ramp("#d9b44a", "#f2d77a", "#fff1b0", "#fffbe8")
BOLT = A.ramp("#3f73bf", "#6c9fe0", "#a8cbf7", "#e2f0ff")

# (u, v, w, h, d) for each cube in StormplateKnightModel.
BOXES = {
    "torso": (0, 0, 16, 20, 10),
    "breastplate": (0, 30, 17, 9, 11),
    "skirt": (0, 50, 17, 8, 11),
    "helm": (56, 0, 10, 10, 10),
    "crest": (96, 0, 2, 5, 10),
    "fin": (96, 16, 2, 6, 4),
    "cape": (56, 20, 14, 28, 1),
    "arm": (0, 70, 6, 20, 6),
    "pauldron": (24, 70, 8, 6, 9),
    # The left pauldron has its own UVs: it is the breakable one (boss_damage.py paints its broken overlay).
    "pauldron_l": (80, 100, 8, 6, 9),
    "shaft": (0, 96, 1, 1, 26),
    "guard": (76, 85, 2, 6, 1),
    "spearhead": (58, 85, 1, 4, 8),
    "leg": (86, 26, 6, 22, 7),
}


def faces(box):
    """name -> (x0, y0, x1, y1) of each face of a box-UV cube."""
    u, v, w, h, d = box
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def rng_for(name):
    return random.Random(name)


def scale_px(x, y, base, rng, ramp=SCALE, cell_w=4, cell_h=3):
    """Overlapping scales: rows of `cell_w`-wide scales, every other row offset by half; each scale lit at the top and
    shadowed along its lower rim, so the surface reads as layered plates even at a distance."""
    row = y // cell_h
    cx = (x + (cell_w // 2 if row % 2 else 0)) % cell_w
    cy = y % cell_h
    i = base
    if cy == 0:
        i += 1
    if cy == cell_h - 1 or cx == cell_w - 1:
        i -= 1
    if cy == cell_h - 1 and cx in (0, cell_w - 1):
        i -= 1
    if rng.random() < 0.12:
        i += rng.choice((-1, 1))
    return ramp[max(0, min(len(ramp) - 1, i))]


def paint_box(img, rng, box, painter):
    for face, (x0, y0, x1, y1) in faces(box).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                if 0 <= x < img.width and 0 <= y < img.height:
                    c = painter(face, x - x0, y - y0, x1 - x0, y1 - y0)
                    if c is not None:
                        img.putpixel((x, y), c)


def scaled(rng, base=4, rim=None):
    """A painter: dragon scale on the sides, darker underneath, optional gold rim on the bottom row of the sides."""
    def p(face, x, y, w, h):
        if face == "bottom":
            return SCALE[1]
        if face == "top":
            return SCALE[base + (1 if rng.random() < 0.4 else 0)]
        if rim is not None and y == h - 1:
            return GOLD[rim + (1 if x % 3 == 0 else 0)]
        return scale_px(x, y, base, rng)
    return p


def knight():
    rng = rng_for("stormplate_knight")
    img = Image.new("RGBA", (128, 128), A.TRANSPARENT)
    # Torso: dark mail under the plate (mostly hidden behind the breastplate and skirt).
    paint_box(img, rng, BOXES["torso"], lambda f, x, y, w, h: CLOTH[2 + (1 if (x + y) % 2 == 0 else 0)] if f != "bottom" else CLOTH[1])
    paint_box(img, rng, BOXES["torso"], lambda f, x, y, w, h: scale_px(x, y, 3, rng) if f in ("front", "back") and y < 10 else None)
    # Breastplate: big scales with a gold collar and a gold sternum ridge.
    def breast(f, x, y, w, h):
        if f == "top":
            return GOLD[3] if (x + y) % 3 else GOLD[4]
        if f == "bottom":
            return SCALE[1]
        if y == 0:
            return GOLD[4] if x % 2 else GOLD[3]
        if f == "front" and x in (w // 2 - 1, w // 2) and y < h - 1:
            return GOLD[3] if x == w // 2 - 1 else GOLD[2]
        if y == h - 1:
            return GOLD[2]
        return scale_px(x, y, 5, rng, cell_w=5, cell_h=3)
    paint_box(img, rng, BOXES["breastplate"], breast)
    # Skirt: vertical plates (tassets) with gold edging.
    def skirt(f, x, y, w, h):
        if f in ("top", "bottom"):
            return SCALE[1]
        if y == h - 1:
            return GOLD[3] if x % 4 else GOLD[2]
        if x % 4 == 3:
            return SCALE[1]
        return scale_px(x, y, 4, rng, cell_w=4, cell_h=2)
    paint_box(img, rng, BOXES["skirt"], skirt)
    # Helm: smooth plate, a dark T-shaped visor, gold brow.
    def helm(f, x, y, w, h):
        if f == "bottom":
            return SCALE[0]
        if f == "top":
            return SCALE[5] if (x + y) % 4 else SCALE[6]
        if f == "front":
            if y == 3 and 1 <= x <= w - 2:
                return GOLD[4] if x % 2 else GOLD[3]
            if y == 5 and 1 <= x <= w - 2:
                return SCALE[0]
            if 6 <= y <= 8 and w // 2 - 1 <= x <= w // 2:
                return SCALE[0]
            if y == h - 1:
                return GOLD[2]
        i = 5 + (1 if y < 2 else 0) - (1 if y >= h - 2 else 0) - (1 if x in (0, w - 1) else 0)
        if rng.random() < 0.1:
            i += rng.choice((-1, 1))
        return SCALE[i]
    paint_box(img, rng, BOXES["helm"], helm)
    paint_box(img, rng, BOXES["crest"], lambda f, x, y, w, h: GOLD[4 if y < 2 else 3] if f != "bottom" else GOLD[1])
    paint_box(img, rng, BOXES["fin"], lambda f, x, y, w, h: GOLD[3] if y == 0 or x == 0 else scale_px(x, y, 5, rng))
    # Cape: dark navy cloth, faded gold hem, torn into ragged tails at the bottom (cutout = holes).
    tears = [rng.randint(0, 6) for _ in range(32)]
    def cape(f, x, y, w, h):
        if f in ("front", "back", "right", "left"):
            col = x if f in ("front", "back") else 0
            if y >= h - tears[col % len(tears)]:
                return A.TRANSPARENT
            if f == "back" and rng.random() < 0.02:
                return A.TRANSPARENT
            if y == h - tears[col % len(tears)] - 1:
                return GOLD[1]
            return CLOTH[2 + (1 if (x * 3 + y) % 7 == 0 else 0) - (1 if y > h * 0.6 and rng.random() < 0.3 else 0)]
        return CLOTH[1]
    paint_box(img, rng, BOXES["cape"], cape)
    paint_box(img, rng, BOXES["arm"], scaled(rng, 4, rim=2))
    # Pauldrons: three overlapping lames with gold rims.
    def pauldron(f, x, y, w, h):
        if f == "bottom":
            return SCALE[1]
        if f == "top":
            return SCALE[6] if (x + y) % 3 else GOLD[3]
        if y % 2 == 1:
            return GOLD[3] if f == "front" or x % 2 else GOLD[2]
        return SCALE[5 - y // 2 + (1 if x == 0 else 0)]
    paint_box(img, rng, BOXES["pauldron"], pauldron)
    paint_box(img, rng, BOXES["pauldron_l"], pauldron)
    # Legs: scaled thighs, plate greaves from the knee down.
    def leg(f, x, y, w, h):
        if f == "bottom":
            return STEEL[0]
        if f == "top":
            return SCALE[3]
        if y == 10:
            return GOLD[3]
        if y > 10:
            i = 5 - (1 if x in (0, w - 1) else 0) - (1 if y >= h - 2 else 0)
            return SCALE[i] if (y - 11) % 4 else SCALE[i - 2]
        return scale_px(x, y, 4, rng)
    paint_box(img, rng, BOXES["leg"], leg)
    # Spear: dark wood shaft with gold bands; gold guard; a bright steel head with a gold ridge.
    paint_box(img, rng, BOXES["shaft"], lambda f, x, y, w, h: GOLD[3] if (f in ("top", "bottom") and x % 9 == 0) or
              (f not in ("top", "bottom") and x % 9 == 0) else SHAFT[2 + (x + y) % 2])
    paint_box(img, rng, BOXES["guard"], lambda f, x, y, w, h: GOLD[4] if y in (0, h - 1) else GOLD[3])
    def head(f, x, y, w, h):
        if f in ("right", "left"):
            # Side faces carry the blade's shape: wide at the base (x small = point end), ridge in the middle.
            if y in (1, 2):
                return GOLD[4] if f == "right" else GOLD[3]
            return STEEL[4] if y == 0 else STEEL[3]
        return STEEL[5] if f == "top" else STEEL[2]
    paint_box(img, rng, BOXES["spearhead"], head)
    return img


def knight_glow():
    rng = rng_for("stormplate_knight_glow")
    img = Image.new("RGBA", (128, 128), A.TRANSPARENT)
    hx0, hy0, hx1, hy1 = faces(BOXES["helm"])["front"]
    # The visor slit burns pale gold.
    for x in range(hx0 + 2, hx1 - 2):
        img.putpixel((x, hy0 + 5), GLOW[2] if x % 3 else GLOW[3])
    # Spearhead edges.
    for face in ("right", "left"):
        x0, y0, x1, y1 = faces(BOXES["spearhead"])[face]
        for x in range(x0, x1):
            img.putpixel((x, y0), GLOW[1])
            img.putpixel((x, y1 - 1), GLOW[1])
    x0, y0, x1, y1 = faces(BOXES["spearhead"])["front"]
    for y in range(y0, y1):
        img.putpixel((x0, y), GLOW[3])
    # A faint gold seam glint down the sternum and on the crest.
    bx0, by0, bx1, by1 = faces(BOXES["breastplate"])["front"]
    mid = (bx0 + bx1) // 2
    for y in range(by0 + 1, by1 - 1):
        if rng.random() < 0.6:
            img.putpixel((mid - 1, y), GLOW[0])
    return img


def knight_storm_glow():
    """Phase 2: forks of lightning crawling across the plate."""
    rng = rng_for("stormplate_knight_storm_glow")
    img = Image.new("RGBA", (128, 128), A.TRANSPARENT)

    def fork(x0, y0, x1, y1, n):
        for _ in range(n):
            x, y = rng.randrange(x0, x1), rng.randrange(y0, y1)
            for _ in range(rng.randint(4, 9)):
                if x0 <= x < x1 and y0 <= y < y1:
                    img.putpixel((x, y), BOLT[rng.choice((1, 2, 2, 3))])
                x += rng.choice((-1, 0, 1))
                y += 1

    for name, count in (("breastplate", 6), ("arm", 3), ("leg", 3), ("pauldron", 2), ("skirt", 3), ("helm", 2)):
        for face in ("front", "back", "left", "right"):
            fork(*faces(BOXES[name])[face], count if face == "front" else max(1, count // 2))
    # The whole spearhead crackles.
    for face, (x0, y0, x1, y1) in faces(BOXES["spearhead"]).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                if rng.random() < 0.5:
                    img.putpixel((x, y), BOLT[rng.choice((2, 3))])
    return img


# ------------------------------------------------------------------------------------------------- armor icons

M = A.Mat
ICON_MATS = {"m": M(SCALE, 5, 0.15), "n": M(SCALE, 3, 0.1), "g": M(GOLD, 3), "c": M(CLOTH, 2)}
ICON_FIX = {"e": GLOW[2], "x": SCALE[7], "v": SCALE[0]}

ICONS = {
    "helmet": [
        "................",
        ".......gg.......",
        "......gmmg......",
        "....mmmgmmmm....",
        "...mmxmgmmmmm...",
        "..gmmmmgmmmmmg..",
        "..gnnnnnnnnnng..",
        "..mmggggggggmm..",
        "..mmveeeeeevmm..",
        "..mmmmmvvmmmmm..",
        "..nmmm.vv.mmmn..",
        "..nnm......mnn..",
        "................",
        "................",
        "................",
        "................",
    ],
    "chestplate": [
        "................",
        ".gmmg......gmmg.",
        ".gmmmg....gmmmg.",
        "..mmmmggggmmmm..",
        "..nnnnngnnnnnn..",
        "...mxmmgmmmmm...",
        "...nnnngnnnnn...",
        "...mmmmgmmmmm...",
        "...nnnngnnnnn...",
        "...mmmmgmmmmm...",
        "...nnnngnnnnn...",
        "...gggggggggg...",
        "................",
        "................",
        "................",
        "................",
    ],
    "leggings": [
        "................",
        "...gggggggggg...",
        "...mmmmmmmmmm...",
        "...nnnnnnnnnn...",
        "...mxmm..mmmm...",
        "...nnnn..nnnn...",
        "...mmmm..mmmm...",
        "...gggg..gggg...",
        "...mmmm..mmmm...",
        "...nnnn..nnnn...",
        "...mmmm..mmmm...",
        "...nnnn..nnnn...",
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
        "...ggg....ggg...",
        "...mmm....mmm...",
        "..mmmm...mmmm...",
        ".mmmmm..mmmmmm..",
        ".gnnnng.gnnnnng.",
        "................",
        "................",
        "................",
        "................",
    ],
}


# ------------------------------------------------------------------------------------------------ armor layers

HUMANOID = {
    "head": (0, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "arm": (40, 16, 4, 12, 4),
    "leg": (0, 16, 4, 12, 4),
}


def box_faces(u, v, w, h, d):
    return [
        (u + d, v, u + d + w, v + d, "top"),
        (u + d + w, v, u + d + 2 * w, v + d, "bottom"),
        (u, v + d, u + d, v + d + h, "side"),
        (u + d, v + d, u + d + w, v + d + h, "front"),
        (u + d + w, v + d, u + 2 * d + w, v + d + h, "side"),
        (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h, "side"),
    ]


def armor_layer(legs):
    img = Image.new("RGBA", (64, 32), A.TRANSPARENT)
    rng = random.Random(f"dragonscale_{legs}")

    def box(part, keep=None, base=5):
        u, v, w, h, d = HUMANOID[part]
        for (x0, y0, x1, y1, face) in box_faces(u, v, w, h, d):
            for y in range(y0, y1):
                for x in range(x0, x1):
                    fx, fy = x - x0, y - y0
                    if keep and not keep(face, fx, fy):
                        continue
                    if face == "top":
                        c = SCALE[base + (1 if rng.random() < 0.3 else 0)]
                    elif face == "bottom":
                        c = SCALE[2]
                    else:
                        c = scale_px(fx, fy, base, rng, cell_w=3, cell_h=2)
                    img.putpixel((x, y), c)

    def hline(part, row, faces_from=2, color=GOLD[3]):
        u, v, w, h, d = HUMANOID[part]
        for (x0, y0, x1, y1, face) in box_faces(u, v, w, h, d)[faces_from:]:
            for x in range(x0, x1):
                img.putpixel((x, y0 + row), color if x % 3 else GOLD[4])

    if legs:
        box("body", keep=lambda f, x, y: f in ("top", "bottom") or y >= 7)
        box("leg", keep=lambda f, x, y: f == "top" or (f != "bottom" and y < 9))
        hline("body", 7)
        hline("leg", 4, color=GOLD[2])
        return img

    def helmet_keep(face, x, y):
        if face == "front":
            if y == 4 and 1 <= x <= 6:
                return False  # visor slit
            if 5 <= y and 3 <= x <= 4:
                return True  # nasal guard
            return y < 6
        if face == "side":
            return y < 7
        return face == "top"

    box("head", keep=helmet_keep, base=5)
    u, v, w, h, d = HUMANOID["head"]
    hx0, hy0 = u + d, v + d
    for x in range(0, 8):
        img.putpixel((hx0 + x, hy0 + 3), GOLD[4] if x % 2 else GOLD[3])
    # Gold crest down the middle of the top.
    for y in range(0, 8):
        img.putpixel((u + d + 3, v + y), GOLD[3])
        img.putpixel((u + d + 4, v + y), GOLD[4])
    box("body")
    bu, bv, bw, bh, bd = HUMANOID["body"]
    bx0, by0 = bu + bd, bv + bd
    for y in range(0, bh - 1):
        img.putpixel((bx0 + 3, by0 + y), GOLD[3])
        img.putpixel((bx0 + 4, by0 + y), GOLD[2])
    hline("body", 0)
    box("arm", keep=lambda f, x, y: f == "top" or (f != "bottom" and y < 6))
    hline("arm", 5)
    box("leg", keep=lambda f, x, y: f == "bottom" or (f != "top" and y >= 7))
    hline("leg", 7)
    return img


# --------------------------------------------------------------------------------------------------------- main


def save(img, *path):
    out = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("wrote", os.path.relpath(out, ROOT))


def main():
    save(knight(), "entity", "stormplate_knight.png")
    save(knight_glow(), "entity", "stormplate_knight_glow.png")
    save(knight_storm_glow(), "entity", "stormplate_knight_storm_glow.png")
    save(shaped(random.Random("stormplate_knight_spawn_egg"), PALETTES["storm"], "item"), "item", "stormplate_knight_spawn_egg.png")
    for piece, rows in ICONS.items():
        save(A.sprite(rows, ICON_MATS, ICON_FIX, f"dragonscale_{piece}"), "item", f"dragonscale_{piece}.png")
    save(armor_layer(False), "entity", "equipment", "humanoid", "dragonscale.png")
    save(armor_layer(True), "entity", "equipment", "humanoid_leggings", "dragonscale.png")


if __name__ == "__main__":
    main()
