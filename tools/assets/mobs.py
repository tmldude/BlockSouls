#!/usr/bin/env python3
"""Placeholder skins for the mobs module's custom (box-UV) models.

    python3 tools/assets/mobs.py            # writes every mob texture + spawn egg into mobs/src/main/resources

Each model's boxes are listed below as (u, v, w, h, d) texture offsets and sizes, mirroring the Java
LayerDefinitions in mobs/src/client/.../model/. Every face of every box is painted (no transparent holes),
tops lighter and bottoms darker so the shapes read, and eyes go on the head box's front face. Glow textures
(full-bright layers) are transparent except for veins / eyes. Same input -> same image. The art-mobs session
replaces these at the same paths.
"""

import hashlib
import os
import random

from PIL import Image

from gen import PALETTES, noise_tile, rgb

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "mobs", "src", "main", "resources", "assets", "blocksouls_mobs", "textures")

# name: (size, palette, head box, other boxes, eye color or None for palette brightest)
# Box = (u, v, w, h, d).
MOBS = {
    "shambler": ((64, 64), "rot", (0, 0, 8, 8, 8),
                 [(16, 16, 8, 12, 4), (40, 16, 4, 15, 4), (32, 48, 4, 12, 4), (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)], "#d8e070"),
    "crawler": ((64, 64), "ash", (0, 20, 7, 6, 6),
                [(0, 0, 8, 6, 14), (44, 0, 4, 2, 6), (26, 20, 5, 2, 3), (0, 32, 2, 13, 2)], "#e0e8a0"),
    "screamer": ((64, 64), "bone", (0, 0, 6, 6, 6),
                 [(24, 0, 5, 2, 5), (16, 16, 6, 13, 3), (40, 16, 2, 17, 2), (48, 16, 2, 17, 2), (0, 16, 3, 15, 3), (0, 34, 3, 15, 3)],
                 "#ff5a5a"),
    "bloater": ((128, 64), "rot", (72, 0, 6, 5, 6),
                [(0, 0, 18, 18, 18), (0, 36, 5, 6, 6), (22, 36, 6, 6, 4), (42, 36, 7, 7, 4), (72, 12, 4, 4, 4), (72, 22, 3, 8, 3)],
                "#f0e060"),
    "brute": ((128, 64), "flesh", (60, 0, 6, 6, 6),
              [(0, 0, 18, 18, 12), (60, 26, 12, 6, 10), (0, 30, 7, 24, 8), (84, 0, 9, 7, 10), (30, 30, 7, 14, 7)], "#ffd27a"),
    "mutant_brute": ((128, 64), "rad", (60, 0, 6, 6, 6),
                     [(0, 0, 18, 18, 12), (60, 26, 12, 6, 10), (0, 30, 7, 24, 8), (84, 0, 9, 7, 10), (30, 30, 7, 14, 7),
                      (96, 26, 2, 6, 2), (104, 26, 2, 4, 2), (96, 36, 7, 6, 7)], "#c8ff6e"),
    "infected_piglin": ((64, 64), "blight", (0, 0, 10, 8, 8),
                        [(31, 1, 4, 4, 1), (39, 6, 1, 5, 4), (51, 6, 1, 5, 4), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (0, 16, 4, 12, 4),
                         (44, 32, 4, 3, 5), (44, 40, 3, 2, 3), (0, 32, 6, 6, 3), (18, 32, 4, 4, 2), (30, 40, 3, 5, 4)], "#ffd84a"),
    "ash_hound": ((64, 32), "ash", (0, 19, 5, 5, 5),
                  [(0, 0, 5, 5, 14), (38, 0, 2, 2, 9), (20, 19, 3, 3, 4), (34, 19, 2, 3, 1), (40, 19, 2, 9, 2), (48, 0, 2, 9, 2)], "#ff8a1a"),
}

# Mobs with a full-bright layer: veins (mutant) or just eyes (hound).
GLOW = {"mutant_brute": "veins", "ash_hound": "eyes"}


def faces(box):
    """(x0, y0, x1, y1, kind) for each face of a box-UV cube: top, bottom, then the four sides."""
    u, v, w, h, d = box
    return [
        (u + d, v, u + d + w, v + d, "top"),
        (u + d + w, v, u + d + 2 * w, v + d, "bottom"),
        (u, v + d, u + d, v + d + h, "side"),
        (u + d, v + d, u + d + w, v + d + h, "front"),
        (u + d + w, v + d, u + 2 * d + w, v + d + h, "side"),
        (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h, "side"),
    ]


def put(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def eyes(img, head, color):
    u, v, w, h, d = head
    fx, fy = u + d, v + d
    row = fy + max(1, h // 3)
    inset = max(1, w // 4)
    for x in (fx + inset, fx + w - 1 - inset):
        put(img, x, row, color)
        if w >= 8:
            put(img, x, row + 1, color)


def skin(name):
    (width, height), palette_name, head, boxes, eye = MOBS[name]
    pal = PALETTES[palette_name]
    rng = random.Random(int(hashlib.sha256(name.encode()).hexdigest(), 16))
    img = noise_tile(rng, pal, max(width, height), clumpiness=0.45).crop((0, 0, width, height))
    for box in [head] + boxes:
        for x0, y0, x1, y1, kind in faces(box):
            for y in range(y0, y1):
                for x in range(x0, x1):
                    if not (0 <= x < width and 0 <= y < height):
                        continue
                    if kind == "top" and rng.random() < 0.6:
                        put(img, x, y, rgb(pal[3]) + (255,))
                    elif kind == "bottom" and rng.random() < 0.6:
                        put(img, x, y, rgb(pal[0]) + (255,))
                    elif (y == y1 - 1) and kind in ("front", "side") and rng.random() < 0.5:
                        put(img, x, y, rgb(pal[1]) + (255,))
        # Sores / wounds on the bigger faces.
        u, v, w, h, d = box
        if w * h >= 48:
            for _ in range(rng.randint(1, 3)):
                x, y = u + d + rng.randrange(w), v + d + rng.randrange(h)
                for i in range(rng.randint(2, 4)):
                    put(img, x + rng.choice((-1, 0, 1)), y + i, rgb(pal[0]) + (255,))
    # Dark mouth under the eyes.
    u, v, w, h, d = head
    mouth_y = v + d + h - 2
    for x in range(u + d + 1, u + d + w - 1):
        if rng.random() < 0.7:
            put(img, x, mouth_y, rgb(pal[0]) + (255,))
    eyes(img, head, rgb(eye or pal[4]) + (255,))
    return img


def glow(name):
    (width, height), palette_name, head, boxes, eye = MOBS[name]
    rng = random.Random(int(hashlib.sha256((name + "_glow").encode()).hexdigest(), 16))
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    if GLOW[name] == "veins":
        bright = [rgb(c) + (255,) for c in ("#7fd33a", "#c8ff6e")]
        for box in boxes:
            u, v, w, h, d = box
            for x0, y0, x1, y1, kind in faces(box):
                if kind == "bottom" or (x1 - x0) * (y1 - y0) < 12:
                    continue
                # A branching vein crawling across the face.
                x, y = rng.randrange(x0, x1), y0
                while y < y1:
                    put(img, x, y, rng.choice(bright))
                    x = min(x1 - 1, max(x0, x + rng.choice((-1, 0, 0, 1))))
                    y += 1
    eyes(img, head, rgb(eye) + (255,))
    return img


EGG = [
    "................", "......####......", ".....######.....", "....########....",
    "...##########...", "...##########...", "..############..", "..############..",
    "..############..", "..############..", "..############..", "...##########...",
    "...##########...", "....########....", "......####......", "................",
]


def spawn_egg(name):
    _, palette_name, _, _, eye = MOBS[name]
    pal = PALETTES[palette_name]
    rng = random.Random(int(hashlib.sha256((name + "_egg").encode()).hexdigest(), 16))
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    spot = rgb(eye or pal[4]) + (255,)
    for y, row in enumerate(EGG):
        for x, ch in enumerate(row):
            if ch != "#":
                continue
            edge = row[x - 1] != "#" or x + 1 >= 16 or row[x + 1] != "#" or EGG[y - 1][x] != "#" or EGG[y + 1][x] != "#"
            if edge:
                color = rgb(pal[0]) + (255,)
            elif rng.random() < 0.18:
                color = spot
            else:
                color = rgb(pal[rng.choice((1, 2, 2, 3))]) + (255,)
            img.putpixel((x, y), color)
    # Highlight.
    img.putpixel((6, 3), rgb(pal[4]) + (255,))
    img.putpixel((5, 4), rgb(pal[4]) + (255,))
    return img


def main():
    entity = os.path.join(ROOT, "entity")
    item = os.path.join(ROOT, "item")
    os.makedirs(entity, exist_ok=True)
    os.makedirs(item, exist_ok=True)
    for name in MOBS:
        skin(name).save(os.path.join(entity, name + ".png"))
        if name in GLOW:
            glow(name).save(os.path.join(entity, name + "_glow.png"))
        spawn_egg(name).save(os.path.join(item, name + "_spawn_egg.png"))


if __name__ == "__main__":
    main()
