#!/usr/bin/env python3
"""Placeholder texture generator: deterministic, in-theme pixel art so no session waits on real art.

    python3 tools/assets/gen.py <kind> <palette> <out.png> [--seed NAME]

kinds:    block ore ingot item gun helmet chestplate leggings boots skin
palettes: see PALETTES below (one per theme: scrap, rust, blight, rad, ember, pale, storm, ash, bone, steel, wood)

The same arguments always produce the same image. The seed defaults to the output file name, so two items in
the same palette still look different. Real art replaces these later; keep file paths stable.
"""

import argparse
import hashlib
import random
import sys

from PIL import Image

# Dark to light. Each theme reads at a glance: rust = scavenged, blight = Nether infection, rad = radiation, ...
PALETTES = {
    "scrap": ["#2b2b2e", "#4a4a50", "#6b6b72", "#8f8f96", "#b8b8bd"],
    "rust": ["#3b1d10", "#6e3518", "#9c4f22", "#c8743a", "#e3a266"],
    "blight": ["#2a0a14", "#5c1328", "#8f2340", "#c2456a", "#e88aa6"],
    "rad": ["#0f2410", "#1f4d1a", "#3f8f2a", "#7fd33a", "#c8ff6e"],
    "ember": ["#2a0d03", "#6b2207", "#b8420c", "#f07a1a", "#ffc25a"],
    "pale": ["#1c2230", "#3d4a63", "#6f82a3", "#a9bbd6", "#e8f0ff"],
    "storm": ["#101a2e", "#23395e", "#3e6aa8", "#d9b44a", "#fff1b0"],
    "ash": ["#161616", "#2e2b2a", "#4c4643", "#6e6661", "#958a83"],
    "bone": ["#3a342a", "#6b6252", "#a39880", "#d1c7ad", "#f2ecdc"],
    "steel": ["#1d2329", "#38444f", "#5a6b7a", "#8a9cad", "#c5d3df"],
    "wood": ["#2e1c0e", "#4f3219", "#73502b", "#987040", "#bf9660"],
}

# 16x16 silhouettes. '#' = body, '+' = highlight, '-' = shadow, '.' = transparent.
SHAPES = {
    "ingot": [
        "................", "................", "................", "................",
        "................", "....++++++++....", "...+########-...", "..+##########-..",
        ".+############-.", ".##############-", ".-------------- ", "................",
        "................", "................", "................", "................",
    ],
    "item": [
        "................", "......++++......", "....++####-.....", "...+#######-....",
        "..+#########-...", "..##########-...", ".+###########-..", ".############-..",
        ".############-..", "..##########-...", "..-#########-...", "...-#######-....",
        "....--####-.....", "......----......", "................", "................",
    ],
    "gun": [
        "................", "................", "................", "................",
        "................", "...++++++++++++.", "..+############-", ".+#############-",
        ".#######--------", ".#####-.........", ".####-..........", ".####-..........",
        ".####-..........", "..--............", "................", "................",
    ],
    "helmet": [
        "................", "................", "....++++++++....", "...+########-...",
        "..+##########-..", "..############..", "..####----####..", "..###-....-###..",
        "..###-....-###..", "..--........--..", "................", "................",
        "................", "................", "................", "................",
    ],
    "chestplate": [
        "................", "..+++......+++..", "..###-....-###..", "..####++++####..",
        "..############..", "...##########...", "...##########...", "...##########...",
        "...##########...", "...##########...", "...##########...", "...----------...",
        "................", "................", "................", "................",
    ],
    "leggings": [
        "................", "...++++++++++...", "...##########...", "...##########...",
        "...####--####...", "...####..####...", "...####..####...", "...####..####...",
        "...####..####...", "...####..####...", "...----..----...", "................",
        "................", "................", "................", "................",
    ],
    "boots": [
        "................", "................", "................", "................",
        "................", "................", "...+++....+++...", "...###....###...",
        "...###....###...", "...###....###...", "..####...####...", ".#####..#####...",
        ".-----..-----...", "................", "................", "................",
    ],
}


def rgb(hex_color):
    hex_color = hex_color.lstrip("#")
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4))


def noise_tile(rng, palette, size, clumpiness=0.35):
    """Value-noise-ish tile using the palette's middle tones, with clumps so it doesn't look like static."""
    mids = palette[1:4]
    grid = [[rng.randrange(len(mids)) for _ in range(size)] for _ in range(size)]
    for _ in range(2):
        for y in range(size):
            for x in range(size):
                if rng.random() < clumpiness:
                    nx, ny = (x + rng.choice((-1, 0, 1))) % size, (y + rng.choice((-1, 0, 1))) % size
                    grid[y][x] = grid[ny][nx]
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), rgb(mids[grid[y][x]]) + (255,))
    return img


def block(rng, palette):
    img = noise_tile(rng, palette, 16)
    # Occasional dark cracks and light specks read as worn, scavenged material.
    for _ in range(rng.randint(2, 4)):
        x, y = rng.randrange(16), rng.randrange(16)
        for _ in range(rng.randint(2, 5)):
            img.putpixel((x % 16, y % 16), rgb(palette[0]) + (255,))
            x += rng.choice((-1, 0, 1))
            y += 1
    for _ in range(rng.randint(3, 6)):
        img.putpixel((rng.randrange(16), rng.randrange(16)), rgb(palette[4]) + (255,))
    return img


def ore(rng, palette):
    img = noise_tile(rng, PALETTES["ash"], 16, clumpiness=0.5)
    for _ in range(rng.randint(3, 5)):
        cx, cy = rng.randrange(2, 14), rng.randrange(2, 14)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
            if rng.random() < 0.75:
                shade = palette[4] if (dx, dy) == (0, 0) else palette[rng.choice((2, 3))]
                img.putpixel(((cx + dx) % 16, (cy + dy) % 16), rgb(shade) + (255,))
    return img


def shaped(rng, palette, shape_name):
    shape = SHAPES[shape_name]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(shape):
        for x, ch in enumerate(row[:16]):
            if ch == "#":
                tone = palette[2] if rng.random() < 0.8 else palette[rng.choice((1, 3))]
            elif ch == "+":
                tone = palette[4]
            elif ch == "-":
                tone = palette[0]
            else:
                continue
            img.putpixel((x, y), rgb(tone) + (255,))
    return img


# 64x64 humanoid skin layout (vanilla zombie/player UVs): rectangles for each body part's faces.
SKIN_PARTS = [
    (0, 0, 32, 16),   # head
    (16, 16, 40, 32),  # body
    (40, 16, 56, 32),  # right arm
    (0, 16, 16, 32),   # right leg
    (32, 48, 48, 64),  # left arm
    (16, 48, 32, 64),  # left leg
]


def skin(rng, palette):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    tile = noise_tile(rng, palette, 64, clumpiness=0.45)
    for x0, y0, x1, y1 in SKIN_PARTS:
        img.paste(tile.crop((x0, y0, x1, y1)), (x0, y0))
    # Glowing eyes on the head's front face (8..16, 8..16), so every mob reads as hostile.
    eye = rgb(palette[4]) + (255,)
    for x in (10, 13):
        img.putpixel((x, 12), eye)
    # Darker "wound" streaks on the body front.
    for _ in range(3):
        x, y = rng.randrange(20, 28), rng.randrange(20, 30)
        for i in range(3):
            img.putpixel((x, min(31, y + i)), rgb(palette[0]) + (255,))
    return img


KINDS = {
    "block": block,
    "ore": ore,
    "skin": skin,
    **{name: (lambda shape: lambda rng, palette: shaped(rng, palette, shape))(name) for name in SHAPES},
}


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("kind", choices=sorted(KINDS))
    parser.add_argument("palette", choices=sorted(PALETTES))
    parser.add_argument("out")
    parser.add_argument("--seed", help="defaults to the output file name")
    args = parser.parse_args(argv)

    seed = args.seed or args.out.rsplit("/", 1)[-1]
    rng = random.Random(int(hashlib.sha256(seed.encode()).hexdigest(), 16))
    palette = PALETTES[args.palette]
    KINDS[args.kind](rng, palette).save(args.out)


if __name__ == "__main__":
    main(sys.argv[1:])
