#!/usr/bin/env python3
"""Placeholder textures for the kart, the kart wreck and the part items, using tools/assets/gen.py's palettes.

    python3 vehicles/tools/gen_textures.py

Deterministic. The kart textures follow KartModel's UV layout (128x64): each box's region is painted in the
palette of what it is (tyres dark, metal scrap/rust, seats leather-ish). Real art replaces these at the same paths.
"""

import hashlib
import os
import random
import sys

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "tools", "assets"))
import gen  # noqa: E402

from PIL import Image  # noqa: E402

ASSETS = os.path.join(HERE, "..", "src", "client", "resources", "assets", "blocksouls_vehicles", "textures")


def box_uv(u, v, w, h, d):
    """Texture rectangle (x0, y0, x1, y1) a vanilla box UV with texOffs(u, v) and size w*h*d occupies."""
    return u, v, u + 2 * (d + w), v + d + h


# Regions of KartModel's boxes: (rect, part kind).
REGIONS = [
    (box_uv(0, 0, 20, 3, 32), "body"),
    (box_uv(0, 36, 16, 4, 3), "trim"),
    (box_uv(40, 36, 10, 6, 5), "engine"),
    (box_uv(72, 36, 7, 2, 7), "seat"),
    (box_uv(100, 36, 7, 7, 1), "seat"),
    (box_uv(0, 48, 3, 7, 7), "tyre"),
    (box_uv(24, 48, 1, 12, 1), "trim"),
    (box_uv(30, 48, 20, 1, 1), "trim"),
    (box_uv(72, 48, 4, 4, 1), "tyre"),
    (box_uv(84, 48, 2, 8, 2), "engine"),
]

KART_PALETTES = {"body": "scrap", "trim": "ember", "engine": "steel", "seat": "wood", "tyre": "ash"}
WRECK_PALETTES = {"body": "rust", "trim": "rust", "engine": "scrap", "seat": "bone", "tyre": "ash"}


def rng_for(name):
    return random.Random(int(hashlib.sha256(name.encode()).hexdigest(), 16))


def sheet(name, palettes, rust_specks):
    rng = rng_for(name)
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    tiles = {kind: gen.noise_tile(rng, gen.PALETTES[p], 128, clumpiness=0.45) for kind, p in palettes.items()}
    rust = gen.PALETTES["rust"]
    for (x0, y0, x1, y1), kind in REGIONS:
        img.paste(tiles[kind].crop((x0, y0, x1, y1)), (x0, y0))
        palette = gen.PALETTES[palettes[kind]]
        for _ in range((x1 - x0) * (y1 - y0) // 25):
            x, y = rng.randrange(x0, x1), rng.randrange(y0, y1)
            if kind == "tyre":
                img.putpixel((x, y), gen.rgb(palette[0]) + (255,))
            elif rng.random() < rust_specks:
                img.putpixel((x, y), gen.rgb(rust[rng.choice((1, 2, 3))]) + (255,))
            else:
                img.putpixel((x, y), gen.rgb(palette[4]) + (255,))
    return img


def wheel_item():
    rng = rng_for("wheel")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    tyre, hub = gen.PALETTES["ash"], gen.PALETTES["scrap"]
    for y in range(16):
        for x in range(16):
            r = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if r <= 7.5:
                if r > 4.5:
                    tone = tyre[0] if (x + y) % 3 == 0 else tyre[rng.choice((1, 2))]
                elif r > 1.5:
                    tone = hub[rng.choice((2, 3))] if r < 4 else hub[1]
                else:
                    tone = hub[4]
                img.putpixel((x, y), gen.rgb(tone) + (255,))
    return img


def engine_item():
    rng = rng_for("engine")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    steel, rust = gen.PALETTES["steel"], gen.PALETTES["rust"]
    for y in range(4, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (4, 13)
            tone = steel[0] if edge else steel[rng.choice((1, 2, 2, 3))]
            img.putpixel((x, y), gen.rgb(tone) + (255,))
    for x in (4, 7, 10):  # cylinder heads
        for y in (1, 2, 3):
            img.putpixel((x, y), gen.rgb(steel[3 if y == 1 else 2]) + (255,))
            img.putpixel((x + 1, y), gen.rgb(steel[1]) + (255,))
    for _ in range(8):
        img.putpixel((rng.randrange(3, 13), rng.randrange(5, 13)), gen.rgb(rust[rng.choice((2, 3))]) + (255,))
    for y in range(6, 12):  # exhaust pipe
        img.putpixel((14, y), gen.rgb(rust[2]) + (255,))
    return img


def main():
    os.makedirs(os.path.join(ASSETS, "entity"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "item"), exist_ok=True)
    sheet("kart", KART_PALETTES, 0.15).save(os.path.join(ASSETS, "entity", "kart.png"))
    sheet("kart_wreck", WRECK_PALETTES, 0.5).save(os.path.join(ASSETS, "entity", "kart_wreck.png"))
    wheel_item().save(os.path.join(ASSETS, "item", "wheel.png"))
    engine_item().save(os.path.join(ASSETS, "item", "engine.png"))


if __name__ == "__main__":
    main()
