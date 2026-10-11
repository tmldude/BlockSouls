"""Box-UV skins for the boss models (used by bosses.py). Box lists mirror the Java LayerDefinitions."""

import hashlib
import random

from PIL import Image

from gen import PALETTES, noise_tile, rgb

# Furnace Knight (bosses/src/client/.../furnace/FurnaceKnightModel.java), 128x128. Box = (u, v, w, h, d).
FK_BOXES = {
    "torso": (0, 0, 18, 22, 11),
    "skirt": (0, 34, 19, 7, 12),
    "head": (60, 0, 12, 12, 12),
    "crest": (110, 0, 4, 5, 4),
    "visor": (104, 44, 9, 6, 1),
    "arm": (0, 54, 7, 22, 8),
    "pauldron": (32, 54, 10, 7, 11),
    "grip": (104, 26, 2, 2, 6),
    "guard": (104, 36, 2, 4, 2),
    "blade": (0, 88, 1, 10, 28),
    "leg": (76, 26, 6, 20, 7),
}
DARK_STEEL = ["#0e0f12", "#1c1e23", "#2c2f36", "#454953", "#6a6f7c"]


def faces(box):
    u, v, w, h, d = box
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def fill(img, rect, color):
    x0, y0, x1, y1 = rect
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < img.width and 0 <= y < img.height:
                img.putpixel((x, y), color)


def paint_box(img, rng, box, pal):
    for kind, (x0, y0, x1, y1) in faces(box).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                if not (0 <= x < img.width and 0 <= y < img.height):
                    continue
                c = img.getpixel((x, y))
                if kind == "top" and rng.random() < 0.5:
                    c = rgb(pal[3]) + (255,)
                elif kind == "bottom":
                    c = rgb(pal[0]) + (255,)
                # Plate seams every 4-5 px.
                if kind in ("front", "back", "left", "right") and (y - y0) % 5 == 4:
                    c = rgb(pal[0]) + (255,)
                if (x == x0 or x == x1 - 1) and kind in ("front", "back", "left", "right"):
                    c = rgb(pal[1]) + (255,)
                img.putpixel((x, y), c)


def furnace_knight():
    rng = random.Random(int(hashlib.sha256(b"furnace_knight").hexdigest(), 16))
    ember = PALETTES["ember"]
    img = noise_tile(rng, DARK_STEEL, 128, clumpiness=0.5)
    for name, box in FK_BOXES.items():
        paint_box(img, rng, box, DARK_STEEL)
    # Blade: brighter steel with a dark spine.
    for kind, rect in faces(FK_BOXES["blade"]).items():
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                tone = "#7c818c" if rng.random() < 0.7 else "#9aa0ab"
                if kind in ("right", "left") and y == y0:
                    tone = "#2c2f36"
                img.putpixel((x, y), rgb(tone) + (255,))
    # Grip: wrapped leather.
    fill(img, (104, 26, 120, 34), rgb("#3a2414") + (255,))
    # Furnace helm front: a dark iron plate with a soot-black grate opening (the glow layer lights it).
    hx0, hy0, hx1, hy1 = faces(FK_BOXES["head"])["front"]
    fill(img, (hx0 + 2, hy0 + 3, hx1 - 2, hy1 - 3), rgb("#120806") + (255,))
    # Visor grate bars.
    vx0, vy0, vx1, vy1 = faces(FK_BOXES["visor"])["front"]
    for x in range(vx0, vx1):
        for y in range(vy0, vy1):
            img.putpixel((x, y), rgb("#2c2f36" if x % 2 == 0 else "#120806") + (255,))
    # Heat-tinted rims on pauldrons and skirt.
    for name in ("pauldron", "skirt"):
        x0, y0, x1, y1 = faces(FK_BOXES[name])["front"]
        for x in range(x0, x1):
            img.putpixel((x, y1 - 1), rgb(ember[1]) + (255,))
    return img


def furnace_knight_glow():
    rng = random.Random(int(hashlib.sha256(b"furnace_knight_glow").hexdigest(), 16))
    ember = PALETTES["ember"]
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    # Fire behind the grate: the gaps between the visor bars and the opening around it.
    vx0, vy0, vx1, vy1 = faces(FK_BOXES["visor"])["front"]
    for x in range(vx0, vx1):
        for y in range(vy0, vy1):
            if x % 2 == 1:
                img.putpixel((x, y), rgb(ember[4] if rng.random() < 0.5 else ember[3]) + (255,))
    hx0, hy0, hx1, hy1 = faces(FK_BOXES["head"])["front"]
    for x in range(hx0 + 2, hx1 - 2):
        for y in range(hy0 + 3, hy1 - 3):
            if rng.random() < 0.55:
                img.putpixel((x, y), rgb(rng.choice(ember[2:])) + (255,))
    # Ember cracks across the chest plate.
    tx0, ty0, tx1, ty1 = faces(FK_BOXES["torso"])["front"]
    for _ in range(5):
        x, y = rng.randrange(tx0 + 1, tx1 - 1), rng.randrange(ty0 + 2, ty1 - 2)
        for i in range(rng.randint(3, 6)):
            if tx0 <= x < tx1 and ty0 <= y < ty1:
                img.putpixel((x, y), rgb(rng.choice(ember[2:4])) + (255,))
            x += rng.choice((-1, 0, 1))
            y += 1
    return img


def furnace_knight_blade_glow():
    """Phase 2: the cleaver's edge burns."""
    rng = random.Random(int(hashlib.sha256(b"furnace_knight_blade_glow").hexdigest(), 16))
    ember = PALETTES["ember"]
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    f = faces(FK_BOXES["blade"])
    for kind in ("right", "left"):
        x0, y0, x1, y1 = f[kind]
        for x in range(x0, x1):
            for y in range(y1 - 4, y1):
                if y >= y1 - 2 or rng.random() < 0.4:
                    img.putpixel((x, y), rgb(ember[4] if y == y1 - 1 else rng.choice(ember[2:4])) + (255,))
    fill(img, f["bottom"], rgb(ember[4]) + (255,))
    return img


def write_all(save):
    save(furnace_knight(), "entity", "furnace_knight.png")
    save(furnace_knight_glow(), "entity", "furnace_knight_glow.png")
    save(furnace_knight_blade_glow(), "entity", "furnace_knight_blade_glow.png")
