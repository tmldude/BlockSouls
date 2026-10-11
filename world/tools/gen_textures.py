#!/usr/bin/env python3
"""Placeholder textures for world (see tools/assets/README.md). Deterministic; real art replaces them at the same paths.

    python3 world/tools/gen_textures.py
"""
import hashlib
import json
import os
import random
import subprocess
import sys

from PIL import Image, ImageEnhance

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.join(HERE, "..", "..")
GEN = os.path.join(REPO, "tools", "assets", "gen.py")
TEX = os.path.join(HERE, "..", "src", "client", "resources", "assets", "blocksouls_world", "textures")

sys.dont_write_bytecode = True  # keep tools/assets free of __pycache__
sys.path.insert(0, os.path.dirname(GEN))
import gen  # noqa: E402  (tools/assets/gen.py)


def out(rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def run(kind, palette, rel):
    subprocess.run([sys.executable, GEN, kind, palette, out(rel)], check=True)


def rng(seed):
    return random.Random(int(hashlib.sha256(seed.encode()).hexdigest(), 16))


def animate(base, rel, frames=8, frametime=3, low=0.8, high=1.15):
    """Stacks brightness-pulsed copies of a 16x16 tile into a vertical strip + .mcmeta (a slow heartbeat)."""
    import math
    strip = Image.new("RGBA", (16, 16 * frames))
    for i in range(frames):
        k = low + (high - low) * (0.5 - 0.5 * math.cos(2 * math.pi * i / frames))
        strip.paste(ImageEnhance.Brightness(base).enhance(k), (0, 16 * i))
    path = out(rel)
    strip.save(path)
    with open(path + ".mcmeta", "w") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, f, indent=2)
        f.write("\n")


P = gen.PALETTES

# Materials.
run("ore", "steel", "block/gunmetal_ore.png")
run("ore", "steel", "block/deepslate_gunmetal_ore.png")
run("block", "steel", "block/raw_gunmetal_block.png")
run("block", "steel", "block/gunmetal_block.png")
run("block", "rust", "block/scrap_pile.png")
run("item", "rust", "item/scrap.png")
run("item", "steel", "item/raw_gunmetal.png")
run("ingot", "steel", "item/gunmetal_ingot.png")
run("item", "steel", "item/gunmetal_nugget.png")
run("item", "blight", "item/blight_core.png")

# Deepslate variant: darken the stone so it reads as deeper rock.
p = out("block/deepslate_gunmetal_ore.png")
ImageEnhance.Brightness(Image.open(p)).enhance(0.7).save(p)

# Infected Nether: fleshy pulsing netherrack, a pulsing heart.
animate(gen.block(rng("blighted_netherrack"), P["blight"]), "block/blighted_netherrack.png")
heart = gen.block(rng("blight_heart"), P["blight"])
for x in range(5, 11):
    for y in range(5, 11):
        d = abs(x - 7.5) + abs(y - 7.5)
        heart.putpixel((x, y), gen.rgb(P["blight"][4] if d < 2 else P["ember"][3] if d < 3.5 else P["blight"][3]) + (255,))
animate(heart, "block/blight_heart.png", frametime=2, low=0.75, high=1.3)

# Growth: a few fleshy stalks with bulbs (cross model, transparent background).
r = rng("blight_growth")
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for sx in (3, 7, 11, 13):
    h = r.randint(6, 13)
    x = sx
    for y in range(15, 15 - h, -1):
        img.putpixel((x, y), gen.rgb(P["blight"][r.choice((1, 2))]) + (255,))
        if r.random() < 0.25:
            x = max(1, min(14, x + r.choice((-1, 1))))
    top = 15 - h
    for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0)):
        img.putpixel((max(0, min(15, x + dx)), max(0, top + dy)), gen.rgb(P["blight"][4]) + (255,))
img.save(out("block/blight_growth.png"))

# Veins: branching lines on a transparent face.
r = rng("blight_veins")
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for _ in range(4):
    x, y = r.randrange(16), r.randrange(16)
    for _ in range(14):
        img.putpixel((x, y), gen.rgb(P["blight"][r.choice((2, 3, 4))]) + (255,))
        x = (x + r.choice((-1, 0, 1))) % 16
        y = (y + r.choice((-1, 0, 1, 1))) % 16
img.save(out("block/blight_veins.png"))

# Armor: item icons and equipment layers.
for s, pal in (("scrap_plate", "rust"), ("blight_forged", "blight")):
    for piece in ("helmet", "chestplate", "leggings", "boots"):
        run(piece, pal, f"item/{s}_{piece}.png")
    for layer, size in (("humanoid", (64, 32)), ("humanoid_leggings", (64, 32))):
        tile = gen.noise_tile(rng(f"{s}_{layer}"), P[pal], 64, clumpiness=0.45).crop((0, 0) + size)
        # Rivets / veins so the two sets read differently from a distance.
        rr = rng(f"{s}_{layer}_detail")
        for _ in range(40):
            tile.putpixel((rr.randrange(size[0]), rr.randrange(size[1])), gen.rgb(P[pal][4]) + (255,))
        tile.save(out(f"entity/equipment/{layer}/{s}.png"))

print("textures written to", os.path.normpath(TEX))
