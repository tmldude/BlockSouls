#!/usr/bin/env python3
"""Placeholder art for the Riftwarden, the floor-6 final boss (bosses module).

    python3 tools/assets/riftwarden.py && python3 tools/assets/boss_damage.py

- entity/riftwarden.png: 128x128 box-UV skin. The box list mirrors RiftwardenModel.createBodyLayer
  (bosses/src/client/.../riftwarden/); change both together.
- entity/riftwarden_glow.png: always lit (eye slit, horn tips, the floating core).
- entity/riftwarden_rift_glow.png: phase 2+ (transformed), rift seams opening across the body.
- item/riftwarden_spawn_egg.png: 16x16 icon.

Kept in its own file so parallel boss sessions don't collide. Deterministic: same pixels each run.
"""

import hashlib
import os
import random
import sys

from PIL import Image

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from boss_skins import faces, fill, paint_box  # noqa: E402
from gen import noise_tile, rgb, shaped  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(ROOT, "bosses/src/main/resources/assets/blocksouls_bosses/textures")

# Obsidian-violet shell, teal rift light.
SHELL = ["#0b0712", "#1a1026", "#2a1a3d", "#3d2758", "#5a3c80"]
RIFT = ["#1d6b66", "#2fb3a8", "#4dffe1", "#b8fff3", "#f2fffd"]
VIOLET = ["#3a1a66", "#6a2fb3", "#9b4dff", "#c79bff", "#efe0ff"]

# (u, v, w, h, d) for each cube in RiftwardenModel.
BOXES = {
    "torso": (0, 0, 16, 22, 10),
    "head": (60, 0, 10, 10, 10),
    "horn": (104, 0, 2, 8, 2),
    "core": (104, 16, 6, 6, 6),
    "arm": (0, 36, 6, 24, 6),
    "blade": (28, 36, 2, 4, 18),
    "leg": (76, 26, 6, 22, 7),
    "robe": (0, 70, 18, 14, 11),
}


def rng_for(name):
    return random.Random(int(hashlib.sha256(name.encode()).hexdigest(), 16))


def skin():
    rng = rng_for("riftwarden")
    img = noise_tile(rng, SHELL, 128, clumpiness=0.5)
    for box in BOXES.values():
        paint_box(img, rng, box, SHELL)
    # Head front: a dark mask with an eye slit (lit by the glow layer).
    hx0, hy0, hx1, hy1 = faces(BOXES["head"])["front"]
    fill(img, (hx0 + 1, hy0 + 3, hx1 - 1, hy0 + 5), rgb("#05030a") + (255,))
    # Core and blade: violet crystal.
    for name in ("core", "blade"):
        for rect in faces(BOXES[name]).values():
            x0, y0, x1, y1 = rect
            for y in range(y0, y1):
                for x in range(x0, x1):
                    img.putpixel((x, y), rgb(rng.choice(VIOLET[1:4])) + (255,))
    # Robe hem in violet.
    x0, y0, x1, y1 = faces(BOXES["robe"])["front"]
    for x in range(x0, x1):
        img.putpixel((x, y1 - 1), rgb(VIOLET[2]) + (255,))
    return img


def glow():
    rng = rng_for("riftwarden_glow")
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    hx0, hy0, hx1, _ = faces(BOXES["head"])["front"]
    fill(img, (hx0 + 2, hy0 + 3, hx1 - 2, hy0 + 5), rgb(RIFT[3]) + (255,))
    for rect in faces(BOXES["core"]).values():
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                if rng.random() < 0.7:
                    img.putpixel((x, y), rgb(rng.choice(RIFT[2:])) + (255,))
    # Horn tips.
    x0, y0, x1, _ = faces(BOXES["horn"])["front"]
    fill(img, (x0, y0, x1, y0 + 2), rgb(VIOLET[3]) + (255,))
    return img


def rift_glow():
    """Phase 2+: rift seams split the shell, teal light behind them."""
    rng = rng_for("riftwarden_rift_glow")
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    for name in ("torso", "arm", "leg", "robe"):
        for kind in ("front", "back", "left", "right"):
            x0, y0, x1, y1 = faces(BOXES[name])[kind]
            for _ in range(2 if name == "torso" else 1):
                x, y = rng.randrange(x0, x1), y0
                while y < y1:
                    if x0 <= x < x1:
                        img.putpixel((x, y), rgb(rng.choice(RIFT[1:4])) + (255,))
                    x += rng.choice((-1, 0, 0, 1))
                    y += 1
    return img


def save(img, *path):
    out = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("wrote", os.path.relpath(out, ROOT))


def main():
    save(skin(), "entity", "riftwarden.png")
    save(glow(), "entity", "riftwarden_glow.png")
    save(rift_glow(), "entity", "riftwarden_rift_glow.png")
    save(shaped(random.Random("riftwarden_spawn_egg"), VIOLET, "item"), "item", "riftwarden_spawn_egg.png")


if __name__ == "__main__":
    main()
