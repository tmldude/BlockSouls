"""Boss v2 presentation overlays, painted over each boss's existing skin (run after the skin scripts):

- ``<boss>_damaged1/2``: scratches and chips, drawn as a cutout layer below 66% / 33% health.
- ``<boss>_cracks<phase>``: emissive cracks in the boss's theme colour; each phase's set contains the last one's and
  spreads further (BossRenderer.cracks).
- ``<boss>_broken_<part>``: the breakable part's UVs charred and split (BossRenderer.brokenPart).

Everything is seeded by file name, so re-running gives the same PNGs. Box lists mirror the Java LayerDefinitions.

    python3 tools/assets/boss_damage.py
"""

import hashlib
import os
import random

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TEX = os.path.join(ROOT, "bosses", "src", "main", "resources", "assets", "blocksouls_bosses", "textures", "entity")

# name -> (phases, crack colour (hot core, cool edge), breakable parts {part: [(u, v, w, h, d), ...]})
BOSSES = {
    "furnace_knight": (2, ("#ffd27a", "#ff6a1a"), {}),
    "stormplate_knight": (2, ("#e8f8ff", "#58b8ff"), {
        # StormplateKnightModel left_arm pauldron (its own UVs: stormplate.py "pauldron_l").
        "pauldron": [(80, 100, 8, 6, 9)],
    }),
    "pale_rider": (2, ("#e6fff9", "#5ee6d6"), {}),
    "ashwyrm": (3, ("#fff0a0", "#ff7a1a"), {
        # AshwyrmModel tail, tail2, tail3 and the tail fin.
        "tail": [(70, 48, 8, 8, 12), (110, 48, 6, 6, 10), (142, 48, 4, 4, 10), (102, 70, 1, 4, 8)],
    }),
    "final_dragon": (2, ("#f4e0ff", "#a24bff"), {}),
}


def rgb(hex_color):
    hex_color = hex_color.lstrip("#")
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4))


def rng_for(name):
    return random.Random(int(hashlib.sha256(name.encode()).hexdigest()[:16], 16))


def box_rect(box):
    u, v, w, h, d = box
    return u, v, u + 2 * (d + w), v + d + h


def opaque(skin):
    alpha = skin.getchannel("A")
    w, h = skin.size
    return {(x, y) for y in range(h) for x in range(w) if alpha.getpixel((x, y)) > 0}


def darken(color, f):
    return tuple(int(c * f) for c in color[:3]) + (255,)


def walk(rng, start, steps, solid, branch=0.12):
    """A jagged crack: a random walk across opaque pixels, sometimes forking."""
    out = []
    heads = [(start, rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)]))]
    while heads and len(out) < steps:
        (x, y), (dx, dy) = heads.pop()
        for _ in range(rng.randint(3, 9)):
            if rng.random() < 0.35:
                dx, dy = rng.choice([(dx, dy), (dy, dx), (-dy, -dx)])
            nx, ny = x + dx, y + dy
            if (nx, ny) not in solid:
                break
            x, y = nx, ny
            out.append((x, y))
            if rng.random() < branch:
                heads.append(((x, y), (dy, dx)))
        heads.append(((x, y), (dx, dy)))
        if rng.random() < 0.15:
            break
    return out


def damaged(skin, name, level):
    rng = rng_for(f"{name}_damaged{level}")
    solid = opaque(skin)
    cells = sorted(solid)
    out = Image.new("RGBA", skin.size, (0, 0, 0, 0))
    px = out.load()
    src = skin.load()
    # Scratches: short dark strokes with a light lip on one side.
    for _ in range(len(cells) // (90 if level == 1 else 35)):
        x, y = rng.choice(cells)
        dx, dy = rng.choice([(1, 0), (1, 1), (0, 1), (1, -1)])
        for i in range(rng.randint(2, 5 if level == 1 else 7)):
            p, lip = (x + dx * i, y + dy * i), (x + dx * i, y + dy * i - 1)
            if p in solid:
                px[p] = darken(src[p], 0.35)
                if lip in solid and px[lip][3] == 0:
                    px[lip] = tuple(min(255, int(c * 1.35) + 12) for c in src[lip][:3]) + (255,)
    # Chips (level 2): small dark gouges.
    if level == 2:
        for _ in range(len(cells) // 120):
            x, y = rng.choice(cells)
            for p in ((x, y), (x + 1, y), (x, y + 1)):
                if p in solid:
                    px[p] = darken(src[p], 0.2)
    return out


def cracks(skin, name, phases, colors):
    """One image per phase 1..phases-1; each contains the previous one's cracks plus new ones."""
    rng = rng_for(f"{name}_cracks")
    solid = opaque(skin)
    cells = sorted(solid)
    hot, cool = rgb(colors[0]), rgb(colors[1])
    images = []
    lit = []
    for phase in range(1, phases):
        for _ in range(max(6, len(cells) // (700 if phase == 1 else 450))):
            path = walk(rng, rng.choice(cells), rng.randint(10, 26), solid)
            lit.extend(path)
        out = Image.new("RGBA", skin.size, (0, 0, 0, 0))
        px = out.load()
        for x, y in lit:
            px[x, y] = cool + (255,)
        # Hot cores where cracks run longest.
        for i, (x, y) in enumerate(lit):
            if i % 3 == 1:
                px[x, y] = hot + (255,)
        images.append(out)
    return images


def broken(skin, name, boxes):
    rng = rng_for(f"{name}_broken")
    solid = opaque(skin)
    out = Image.new("RGBA", skin.size, (0, 0, 0, 0))
    px = out.load()
    src = skin.load()
    region = set()
    for box in boxes:
        x0, y0, x1, y1 = box_rect(box)
        region |= {(x, y) for y in range(y0, y1) for x in range(x0, x1) if (x, y) in solid}
    # Charred: everything darkened, mottled.
    for x, y in region:
        px[x, y] = darken(src[x, y], rng.choice((0.25, 0.32, 0.4)))
    # Split: deep black fractures through it.
    cells = sorted(region)
    for _ in range(max(3, len(cells) // 60)):
        for x, y in walk(rng, rng.choice(cells), rng.randint(6, 16), region, branch=0.25):
            px[x, y] = (10, 8, 8, 255)
    return out


def save(img, file):
    out = os.path.join(TEX, file + ".png")
    img.save(out)
    print("wrote", os.path.relpath(out, ROOT))


def main():
    for name, (phases, colors, parts) in BOSSES.items():
        skin = Image.open(os.path.join(TEX, name + ".png")).convert("RGBA")
        for level in (1, 2):
            save(damaged(skin, name, level), f"{name}_damaged{level}")
        for phase, img in enumerate(cracks(skin, name, phases, colors), start=1):
            save(img, f"{name}_cracks{phase}")
        for part, boxes in parts.items():
            save(broken(skin, name, boxes), f"{name}_broken_{part}")


if __name__ == "__main__":
    main()
