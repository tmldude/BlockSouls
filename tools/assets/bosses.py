#!/usr/bin/env python3
"""Placeholder art for the bosses module.

    python3 tools/assets/bosses.py          # writes into bosses/src/main/resources/assets/blocksouls_bosses/textures

- block/fog_wall.png: translucent drifting fog (the arena wall; translucency makes it render in the translucent layer).
- item/<boss>_spawn_egg.png: an egg in the boss's palette.
- entity/<boss>.png + <boss>_glow.png: box-UV skins for the boss models. Box lists mirror the Java LayerDefinitions
  in bosses/src/client/.../<boss>/; update both together.

Same input -> same image. Real art replaces these at the same paths.
"""

import hashlib
import os
import random

from PIL import Image

from gen import PALETTES, noise_tile, rgb, shaped

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "bosses", "src", "main", "resources", "assets", "blocksouls_bosses", "textures")


def rng_for(name):
    return random.Random(int(hashlib.sha256(name.encode()).hexdigest(), 16))


def fog_wall():
    rng = rng_for("fog_wall")
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            # Soft horizontal streaks, mostly see-through.
            band = 0.5 + 0.5 * ((y * 7 + x * 2 + rng.randrange(5)) % 9) / 8
            v = int(170 + 60 * band)
            a = int(70 + 70 * band * rng.random())
            img.putpixel((x, y), (v, v, min(255, v + 12), a))
    return img


def save(img, *path):
    out = os.path.join(ROOT, *path)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("wrote", os.path.relpath(out))


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


def main():
    save(fog_wall(), "block", "fog_wall.png")
    egg_rng = rng_for("furnace_knight_spawn_egg")
    save(shaped(egg_rng, PALETTES["ember"], "item"), "item", "furnace_knight_spawn_egg.png")
    try:
        import boss_skins
    except ImportError:
        return
    boss_skins.write_all(save)


if __name__ == "__main__":
    main()
