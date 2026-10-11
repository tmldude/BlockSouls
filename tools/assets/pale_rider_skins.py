"""Pale Rider + Wraith Steed placeholder art (called from boss_skins.write_all).

Box lists mirror bosses/src/client/.../palerider/PaleRiderModel.java (128x128, box = (u, v, w, h, d)); the Wraith
Steed uses the same layout, so its texture is the horse half of the rider's.
"""

import hashlib
import random

from PIL import Image

from boss_skins import faces, fill, paint_box
from gen import PALETTES, noise_tile, rgb, shaped

HORSE_BOXES = {
    "horse_body": (0, 0, 10, 10, 24),
    "neck": (68, 0, 5, 13, 6),
    "horse_head": (90, 0, 6, 7, 10),
    "leg": (68, 20, 3, 14, 3),
    "tail": (80, 20, 3, 14, 3),
}
RIDER_BOXES = {
    "rider_body": (0, 34, 8, 12, 5),
    "rider_head": (26, 34, 8, 8, 8),
    "arm": (58, 34, 3, 12, 3),
    "cloak": (70, 40, 10, 14, 1),
    "skirt": (92, 20, 9, 10, 6),
    "lance": (0, 60, 2, 2, 40),
    "tip": (90, 60, 3, 3, 4),
}
# Ghost-pale coat and a darker, ragged cloak (both drawn translucent in game).
PALE = PALETTES["pale"]
CLOAK = ["#0d1018", "#1c2230", "#2a3346", "#3d4a63", "#55647f"]


def rng_for(name):
    return random.Random(int(hashlib.sha256(name.encode()).hexdigest(), 16))


def _horse(img, rng):
    for name, box in HORSE_BOXES.items():
        paint_box(img, rng, box, PALE)
    # Wispy mane down the back of the neck, tail fading toward the tip.
    nx0, ny0, nx1, ny1 = faces(HORSE_BOXES["neck"])["back"]
    for y in range(ny0, ny1):
        for x in range(nx0, nx1):
            if rng.random() < 0.6:
                img.putpixel((x, y), rgb(PALE[4]) + (255,))
    for kind, (x0, y0, x1, y1) in faces(HORSE_BOXES["tail"]).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                t = (y - y0) / max(1, y1 - y0)
                img.putpixel((x, y), rgb(PALE[4] if rng.random() > t else PALE[3]) + (int(255 * (1 - 0.6 * t)),))
    # Dark hollow eyes on the sides of the head (the glow layer lights them).
    for kind in ("right", "left"):
        x0, y0, x1, y1 = faces(HORSE_BOXES["horse_head"])[kind]
        fill(img, (x0 + 1, y0 + 1, x0 + 3, y0 + 3), rgb(PALE[0]) + (255,))
    # Hooves.
    lx0, ly0, lx1, ly1 = faces(HORSE_BOXES["leg"])["front"]
    for kind in ("front", "back", "left", "right"):
        x0, y0, x1, y1 = faces(HORSE_BOXES["leg"])[kind]
        fill(img, (x0, y1 - 2, x1, y1), rgb(PALE[1]) + (255,))


def pale_rider():
    rng = rng_for("pale_rider")
    img = noise_tile(rng, PALE, 128, clumpiness=0.55)
    _horse(img, rng)
    cloth = noise_tile(rng, CLOAK, 128, clumpiness=0.5)
    for name in ("rider_body", "rider_head", "arm", "cloak", "skirt"):
        for rect in faces(RIDER_BOXES[name]).values():
            img.paste(cloth.crop(rect), rect[:2])
        paint_box(img, rng, RIDER_BOXES[name], CLOAK)
    # The hood's face is a void.
    hx0, hy0, hx1, hy1 = faces(RIDER_BOXES["rider_head"])["front"]
    fill(img, (hx0 + 1, hy0 + 2, hx1 - 1, hy1), rgb(CLOAK[0]) + (255,))
    # Ragged hems: the cloak and robe fray to nothing at the bottom.
    for box in ("cloak", "skirt"):
        for kind, (x0, y0, x1, y1) in faces(RIDER_BOXES[box]).items():
            if kind in ("top", "bottom"):
                continue
            for x in range(x0, x1):
                for y in range(y1 - rng.randint(1, 4), y1):
                    img.putpixel((x, y), (0, 0, 0, 0))
    # Lance: pale bone shaft, a bright head.
    for kind, (x0, y0, x1, y1) in faces(RIDER_BOXES["lance"]).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                img.putpixel((x, y), rgb(PALE[4] if rng.random() < 0.6 else PALE[3]) + (255,))
    for kind, rect in faces(RIDER_BOXES["tip"]).items():
        fill(img, rect, rgb(PALE[4]) + (255,))
    return img


def pale_rider_glow():
    """Eyes in the hood and the horse's skull, and the lance head."""
    rng = rng_for("pale_rider_glow")
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    _glow_horse(img)
    hx0, hy0, hx1, hy1 = faces(RIDER_BOXES["rider_head"])["front"]
    for x in (hx0 + 2, hx1 - 3):
        img.putpixel((x, hy0 + 4), rgb(PALE[4]) + (255,))
        img.putpixel((x, hy0 + 5), rgb(PALE[3]) + (255,))
    for kind, (x0, y0, x1, y1) in faces(RIDER_BOXES["tip"]).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                if rng.random() < 0.7:
                    img.putpixel((x, y), rgb(PALE[4]) + (255,))
    return img


def _glow_horse(img):
    for kind in ("right", "left"):
        x0, y0, x1, y1 = faces(HORSE_BOXES["horse_head"])[kind]
        img.putpixel((x0 + 1, y0 + 1), rgb(PALE[4]) + (255,))
        img.putpixel((x0 + 2, y0 + 2), rgb(PALE[3]) + (255,))


def wraith_steed():
    img = pale_rider()
    # Only the horse regions are used by the steed's model; clear the rest so the file is honest.
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for box in HORSE_BOXES.values():
        for rect in faces(box).values():
            out.paste(img.crop(rect), rect[:2])
    return out


def wraith_steed_glow():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    _glow_horse(img)
    return img


WHISTLE = [
    "................",
    "................",
    "..........+##...",
    ".........+####..",
    "........+##-##..",
    ".......+###-#...",
    "......+###......",
    ".....+###.......",
    "....+###........",
    "...+###.........",
    "..+##-#.........",
    "..###-#.........",
    "...###..........",
    "................",
    "................",
    "................",
]


def whistle():
    """A curved bone whistle."""
    rng = rng_for("wraith_steed_whistle")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(WHISTLE):
        for x, ch in enumerate(row):
            if ch == "#":
                tone = PALE[3] if rng.random() < 0.8 else PALE[2]
            elif ch == "+":
                tone = PALE[4]
            elif ch == "-":
                tone = PALE[0]
            else:
                continue
            img.putpixel((x, y), rgb(tone) + (255,))
    return img


def write_all(save):
    save(pale_rider(), "entity", "pale_rider.png")
    save(pale_rider_glow(), "entity", "pale_rider_glow.png")
    save(wraith_steed(), "entity", "wraith_steed.png")
    save(wraith_steed_glow(), "entity", "wraith_steed_glow.png")
    save(shaped(rng_for("pale_rider_spawn_egg"), PALE, "item"), "item", "pale_rider_spawn_egg.png")
    save(whistle(), "item", "wraith_steed_whistle.png")
