"""Shared helpers for the hand-directed art scripts (art.py, guns.py). See STYLE.md for the rules they encode.

Sprites are ASCII grids. Each character is either a *material* (a colour ramp that gets auto-shaded with the
house light: top-left lit, bottom-right shadowed) or a *fixed* colour (eyes, glints, glow). After painting,
`outline` wraps the silhouette in a darkened copy of the neighbouring colour ("sel-out"), never pure black.
"""

import random

from PIL import Image

TRANSPARENT = (0, 0, 0, 0)


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def ramp(*hexes):
    return [rgb(h) for h in hexes]


# ---- Palettes (dark -> light). STYLE.md lists the same values; change both together. ----
GUNMETAL = ramp("#121519", "#1f242b", "#2f363f", "#434c57", "#5c6773", "#7d8995", "#a6b2bd", "#d5dde4")
SCRAP = ramp("#1b1c1f", "#2e3034", "#45474c", "#5f6268", "#7d8086", "#a0a3a8", "#c9cbce")
RUST = ramp("#261007", "#45200e", "#6b3216", "#934a20", "#ba6630", "#d98b4c", "#efb47c")
BLIGHT = ramp("#17040b", "#360a1a", "#5c122c", "#871d41", "#b12f59", "#d75280", "#f78db0")
BLIGHT_GLOW = ramp("#ff2e63", "#ff6b93", "#ffb3c9", "#fff0f4")
BRASS = ramp("#33210a", "#5e3e12", "#8f621e", "#bd8d30", "#dfb85a", "#f6e19a")
POLYMER = ramp("#0f0f10", "#1b1b1c", "#28272a", "#38363a", "#4c494e", "#646066")
WOOD = ramp("#271609", "#41260f", "#5e3918", "#7d4f24", "#9d6834", "#bf884b")
TAPE = ramp("#3f3e3a", "#5d5b55", "#7f7c74", "#a29e94", "#c4c0b4")
VOID = ramp("#160b29", "#2c1652", "#47257f", "#6a3cb0", "#9567d9", "#c4a3f2", "#ece0ff")
STONE = ramp("#353537", "#4b4b4e", "#5e5e61", "#727275", "#87878a", "#9d9d9f")
DEEPSLATE = ramp("#18181c", "#232328", "#2f2f35", "#3c3c43", "#4a4a52", "#5a5a63")
NETHER = ramp("#220909", "#3b1010", "#561716", "#701f1d", "#8b2a26", "#a43a33")
BONE = ramp("#3b352b", "#615847", "#8c8068", "#b5a98c", "#d8ceb2", "#f3ecd8")
STEEL_BLUE = ramp("#0e1a33", "#1a3160", "#285090", "#3f73bf", "#6c9fe0", "#a8cbf7", "#e2f0ff")
GOLD = ramp("#3a2304", "#6b420a", "#a26a12", "#d39a20", "#f2c845", "#ffe98f", "#fffbe0")
EMBER = ramp("#2a0d03", "#6b2207", "#b8420c", "#f07a1a", "#ffc25a", "#fff0c0")
PALE = ramp("#1c2230", "#3d4a63", "#6f82a3", "#a9bbd6", "#e8f0ff")
STORM = ramp("#101a2e", "#23395e", "#3e6aa8", "#d9b44a", "#fff1b0")
RUBBER = ramp("#0d0d0e", "#19191b", "#252528", "#333337", "#45454a")
PAINT_RED = ramp("#2c0707", "#561010", "#841b17", "#b02a20", "#d4463a")
HAZARD = ramp("#3d2c03", "#7a5807", "#b8860b", "#e6b422", "#ffd85a")
GLASS = ramp("#071820", "#0f3242", "#1d5a73", "#3a8fa8", "#7fd0e0", "#d8f6fb")
TRITIUM = ramp("#0b2a10", "#1e6b26", "#3fc24a", "#a6ff8a")


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def darken(c, f):
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), c[3])


class Mat:
    """An auto-shaded material: a ramp, its base index, and how much seeded noise to add."""

    def __init__(self, ramp_, base=None, noise=0.0, edges=True):
        self.ramp = ramp_
        self.base = len(ramp_) // 2 if base is None else base
        self.noise = noise
        self.edges = edges

    def at(self, i):
        return self.ramp[max(0, min(len(self.ramp) - 1, i))]


def paint(rows, mats, fixed=None, seed="x", size=None):
    """Paint an ASCII sprite. `mats`: char -> Mat (auto-shaded); `fixed`: char -> RGBA. '.' is transparent."""
    fixed = fixed or {}
    rng = random.Random(seed)
    h = len(rows)
    w = len(rows[0])
    assert all(len(r) == w for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", size or (w, h), TRANSPARENT)

    def ch(x, y):
        if 0 <= x < w and 0 <= y < h:
            return rows[y][x]
        return "."

    for y in range(h):
        for x in range(w):
            c = rows[y][x]
            if c == ".":
                continue
            if c in fixed:
                img.putpixel((x, y), fixed[c])
                continue
            m = mats[c]
            i = m.base
            if m.edges:
                lit = (ch(x, y - 1) != c) + (ch(x - 1, y) != c)
                dark = (ch(x, y + 1) != c) + (ch(x + 1, y) != c)
                i += lit - dark
            if m.noise and rng.random() < m.noise:
                i += rng.choice((-1, 1))
            img.putpixel((x, y), m.at(i))
    return img


def outline(img, factor=0.42, diagonal=False):
    """Sel-out: every transparent pixel touching the silhouette takes the darkened colour of its neighbour."""
    out = img.copy()
    w, h = img.size
    px = img.load()
    nbrs = [(0, -1), (-1, 0), (1, 0), (0, 1)]
    if diagonal:
        nbrs += [(-1, -1), (1, -1), (-1, 1), (1, 1)]
    for y in range(h):
        for x in range(w):
            if px[x, y][3]:
                continue
            best = None
            for dx, dy in nbrs:
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and px[nx, ny][3]:
                    c = px[nx, ny]
                    lum = c[0] + c[1] + c[2]
                    if best is None or lum < best[0]:
                        best = (lum, c)
            if best:
                c = darken(best[1], factor)
                out.putpixel((x, y), (c[0], c[1], c[2], 255))
    return out


def sprite(rows, mats, fixed=None, seed="x", diagonal=False):
    return outline(paint(rows, mats, fixed, seed), diagonal=diagonal)


def noise_tile(w, h, ramp_, seed, base=None, spread=1, p=0.5):
    """A tileable noisy fill around `base` (used under block details)."""
    rng = random.Random(seed)
    base = len(ramp_) // 2 if base is None else base
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            i = base
            if rng.random() < p:
                i += rng.randint(-spread, spread)
            img.putpixel((x, y), ramp_[max(0, min(len(ramp_) - 1, i))])
    return img


def blob(img, cx, cy, cells, ramp_, outline_idx=0, seed="b"):
    """Draw a small lumpy cluster (ore nugget) from a list of (dx, dy) cells, shaded top-left."""
    s = set(cells)
    for dx, dy in cells:
        x, y = cx + dx, cy + dy
        i = len(ramp_) // 2
        if (dx, dy - 1) not in s:
            i += 1
        if (dx - 1, dy) not in s:
            i += 1
        if (dx, dy + 1) not in s:
            i -= 1
        if (dx + 1, dy) not in s:
            i -= 1
        put(img, x, y, ramp_[max(1, min(len(ramp_) - 1, i))])
    for dx, dy in cells:
        for ox, oy in ((0, 1), (1, 0), (0, -1), (-1, 0)):
            if (dx + ox, dy + oy) not in s:
                put(img, cx + dx + ox, cy + dy + oy, ramp_[outline_idx], only_if_not=ramp_)


def put(img, x, y, c, only_if_not=None):
    w, h = img.size
    x %= w
    y %= h
    if only_if_not is not None and img.getpixel((x, y)) in only_if_not:
        return
    img.putpixel((x, y), c)


def save(img, path):
    import os

    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
