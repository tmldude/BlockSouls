#!/usr/bin/env python3
"""Store-page art for BlockSouls (Modrinth icon and gallery image), original pixel art.

    python3 tools/assets/promo.py            # writes docs/art/promo/icon.png and gallery.png

The gallery scene: an armored knight on a grass block at night, a horde closing in from both sides, title top-left.
The icon: a chibi version of the same knight on a block. Everything is drawn here from rectangles and polygons
(no third-party art), small, then scaled up with nearest-neighbour so it stays crisp pixel art.
"""
import math
import os
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "art", "promo")


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def rect(d, x, y, w, h, c):
    if w > 0 and h > 0:
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=c)


def outlined(layer, color=hexc("#07060b"), width=1):
    """Puts a dark outline around everything opaque on the layer."""
    alpha = layer.split()[3].point(lambda a: 255 if a > 0 else 0)
    grown = alpha
    for _ in range(width):
        grown = grown.filter(ImageFilter.MaxFilter(3))
    ring = ImageChops.subtract(grown, alpha)
    out = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    out.paste(Image.new("RGBA", layer.size, color), (0, 0), ring)
    out.alpha_composite(layer)
    return out


def glow(base, points, color, radius, strength):
    """Additive soft glow around points (eyes, moon, embers)."""
    g = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    for (x, y, r) in points:
        d.ellipse([x - r, y - r, x + r, y + r], fill=color)
    g = g.filter(ImageFilter.GaussianBlur(radius))
    a = g.split()[3].point(lambda v: int(min(255, v * strength)))
    g.putalpha(a)
    base.alpha_composite(g)


# --- palettes --------------------------------------------------------------------------------------------------
STEEL = [hexc(h) for h in ("#1b1f27", "#2c323d", "#434b58", "#5f6977", "#8994a2", "#bcc6d2")]
CLOAK = [hexc(h) for h in ("#1e0709", "#3a0d12", "#5a161c", "#7a2228")]
BRASS = [hexc(h) for h in ("#4a3010", "#8f621e", "#d3a447", "#f6e19a")]
EMBER = hexc("#ffb347")
EMBER_HOT = hexc("#fff0c0")
LEATHER = hexc("#3b2618")
SKIN = [hexc(h) for h in ("#1d3320", "#2e4f2f", "#3f6b3c", "#5a8a4f")]
SHIRT = [hexc(h) for h in ("#14303f", "#1f4a5e", "#2d6680")]
PANTS = [hexc(h) for h in ("#1a1633", "#29224d", "#3a3168")]
ZEYE = hexc("#ff3b2f")
GRASS = [hexc(h) for h in ("#1f3a1b", "#2c5226", "#3a6a30", "#4f8740")]
DIRT = [hexc(h) for h in ("#2a1a10", "#3d2717", "#553621", "#6b4529")]


# --- the knight ------------------------------------------------------------------------------------------------
def knight(cx, feet, helm=(10, 10), torso=(12, 12), legs=10, cloak_flare=3, sword=True, seed=1):
    """A front-facing knight as its own outlined layer; returns (layer, eye points)."""
    rnd = random.Random(seed)
    W, H = 320, 220
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    hw, hh = helm
    tw, th = torso
    torso_top = feet - legs - th
    helm_top = torso_top - hh + 1

    # Cloak behind: shoulders to just above the feet, flaring out, tattered hem.
    top_half = tw // 2 + 3
    bottom = feet - 1
    for y in range(torso_top + 1, bottom + 1):
        t = (y - torso_top) / max(1, bottom - torso_top)
        half = int(top_half + cloak_flare * t)
        for x in range(cx - half, cx + half):
            if y > bottom - 3 and rnd.random() < 0.35 * (y - (bottom - 3)):
                continue  # ragged hem
            shade = CLOAK[1] if x < cx - half + 2 else CLOAK[0] if x > cx + half - 3 else CLOAK[2]
            if rnd.random() < 0.06:
                shade = CLOAK[3]
            layer.putpixel((x, y), shade)

    # Legs: greaves and dark boots.
    lw = tw // 2 - 1
    for lx in (cx - tw // 2 + 1, cx + 1):
        rect(d, lx, feet - legs, lw - 1, legs, STEEL[3])
        rect(d, lx, feet - legs, 1, legs, STEEL[4])
        rect(d, lx + lw - 2, feet - legs, 1, legs, STEEL[2])
        rect(d, lx, feet - legs + legs // 2 - 1, lw - 1, 1, STEEL[2])  # knee plate edge
        rect(d, lx, feet - 2, lw, 2, STEEL[1])

    # Torso: plate with a highlight edge, a dark-red tabard down the middle, a belt.
    tx = cx - tw // 2
    rect(d, tx, torso_top, tw, th, STEEL[3])
    rect(d, tx, torso_top, 2, th, STEEL[4])
    rect(d, tx + tw - 2, torso_top, 2, th, STEEL[2])
    rect(d, tx + 2, torso_top + 1, tw - 4, 1, STEEL[5])
    tab_w = max(2, tw // 3)
    rect(d, cx - tab_w // 2, torso_top + 3, tab_w, th - 3 + legs // 2, CLOAK[2])
    rect(d, cx - tab_w // 2, torso_top + 3, 1, th - 3 + legs // 2, CLOAK[3])
    rect(d, tx, torso_top + th - 3, tw, 2, LEATHER)
    rect(d, cx - 1, torso_top + th - 3, 2, 2, BRASS[2])

    # Pauldrons.
    pw = max(3, tw // 3)
    for px, light in ((tx - pw + 2, True), (tx + tw - 2, False)):
        rect(d, px, torso_top - 1, pw, 4, STEEL[3])
        rect(d, px, torso_top - 1, pw, 1, STEEL[5] if light else STEEL[4])
        rect(d, px, torso_top + 2, pw, 1, STEEL[2])

    eyes = []
    # Sword planted point-down in front, both gauntlets on the crossguard.
    if sword:
        grip_y = torso_top + th - 5
        rect(d, cx - 1, grip_y + 3, 2, feet - grip_y + 1, STEEL[5])  # blade (sinks into the block)
        rect(d, cx, grip_y + 3, 1, feet - grip_y + 1, STEEL[4])
        rect(d, cx - 4, grip_y + 2, 8, 1, BRASS[2])  # crossguard
        rect(d, cx - 4, grip_y + 2, 1, 1, BRASS[3])
        rect(d, cx - 1, grip_y - 1, 2, 3, LEATHER)  # grip
        rect(d, cx - 1, grip_y - 3, 2, 2, BRASS[2])  # pommel
        # Arms down from the pauldrons, forearms in to the grip.
        for ax, fx, fw in ((tx - 2, tx + 1, cx - 1 - (tx + 1)), (tx + tw - 1, cx + 1, tx + tw - 1 - (cx + 1))):
            rect(d, ax, torso_top + 3, 3, grip_y - torso_top - 3, STEEL[2])
            rect(d, ax, torso_top + 3, 1, grip_y - torso_top - 3, STEEL[3])
            rect(d, fx, grip_y - 1, fw, 3, STEEL[3])
            rect(d, fx, grip_y - 1, fw, 1, STEEL[4])
        rect(d, cx - 3, grip_y - 1, 2, 3, STEEL[1])  # gauntlets
        rect(d, cx + 1, grip_y - 1, 2, 3, STEEL[1])

    # Gorget and helm: a bucket helm with a T visor and ember eyes.
    rect(d, cx - hw // 2 + 1, torso_top - 1, hw - 2, 2, STEEL[1])
    hx = cx - hw // 2
    rect(d, hx, helm_top, hw, hh, STEEL[3])
    rect(d, hx, helm_top, 2, hh, STEEL[4])
    rect(d, hx + hw - 2, helm_top, 2, hh, STEEL[2])
    rect(d, hx + 1, helm_top, hw - 2, 1, STEEL[5])
    layer.putpixel((hx, helm_top), (0, 0, 0, 0))
    layer.putpixel((hx + hw - 1, helm_top), (0, 0, 0, 0))
    slit_y = helm_top + hh // 2 - 1
    rect(d, hx + 1, slit_y, hw - 2, 1, hexc("#050407"))
    rect(d, cx - 1, slit_y, 2, hh // 2 - 1, hexc("#050407"))
    for ex in (cx - hw // 4 - 1, cx + hw // 4):
        layer.putpixel((ex, slit_y), EMBER)
        eyes.append((ex, slit_y))
    # Rivets along the brow.
    for rx in range(hx + 2, hx + hw - 2, 3):
        layer.putpixel((rx, helm_top + 2), STEEL[5])
    return outlined(layer), eyes


# --- zombies ---------------------------------------------------------------------------------------------------
def zombie(facing_right=True, seed=0, reach=True):
    """A side-view zombie (32 px tall, arms out toward its target) on its own layer; returns (layer, eye)."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (24, 34), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0 = 6
    # Back leg, front leg (a shambling step).
    step = rnd.choice((0, 1, 2))
    rect(d, x0 + 1 - step, 22, 4, 11, PANTS[0])
    rect(d, x0 + 1 + step, 22, 4, 11, PANTS[1])
    rect(d, x0 + 1 + step, 31, 4, 2, PANTS[0])
    # Torso, torn shirt with skin showing through.
    rect(d, x0, 10, 6, 12, SHIRT[1])
    rect(d, x0, 10, 1, 12, SHIRT[2])
    for _ in range(4):
        rect(d, x0 + rnd.randrange(0, 5), 12 + rnd.randrange(0, 9), 1, rnd.choice((1, 2)), SKIN[2])
    rect(d, x0, 20 + rnd.randrange(0, 2), 6, 1, SHIRT[0])
    # Head, slightly hunched forward.
    hx = x0 + 1
    rect(d, hx, 2, 8, 8, SKIN[2])
    rect(d, hx, 2, 8, 1, SKIN[3])
    rect(d, hx, 8, 8, 2, SKIN[1])
    rect(d, hx + 1, 2, 3, 2, SKIN[1])  # a patch of rot
    eye = (hx + 6, 5)
    img.putpixel(eye, ZEYE)
    rect(d, hx + 5, 7, 3, 1, hexc("#0b0f0b"))  # mouth
    # Arms reaching forward at shoulder height.
    if reach:
        rect(d, x0 + 3, 11, 13, 3, SHIRT[2])
        rect(d, x0 + 12, 11, 4, 3, SKIN[2])
        rect(d, x0 + 12, 11, 4, 1, SKIN[3])
        droop = rnd.choice((0, 1))
        rect(d, x0 + 4, 14 + droop, 11, 2, SHIRT[1])
        rect(d, x0 + 12, 14 + droop, 3, 2, SKIN[1])
    layer = outlined(img)
    if not facing_right:
        layer = layer.transpose(Image.FLIP_LEFT_RIGHT)
        eye = (layer.width - 1 - eye[0] - 1, eye[1])
    else:
        eye = (eye[0] + 1, eye[1])
    return layer, eye


# --- the block -------------------------------------------------------------------------------------------------
def iso_block(img, cx, top_y, edge, seed=3):
    """An isometric grass block: top diamond (grass), left and right faces (dirt with a grass fringe)."""
    rnd = random.Random(seed)
    half_w = edge * 2 // 2 * 1  # top face is 2*edge wide, edge tall
    w = edge * 2
    h = edge
    texel = max(2, edge // 8)

    def tex(ramp, u, v, key):
        r = random.Random(hash((key, u // texel, v // texel, seed)))
        return ramp[r.choice((0, 1, 1, 2, 2, 2, 3))]

    px = img.load()
    for y in range(top_y, top_y + h + edge + 1):
        for x in range(cx - edge, cx + edge):
            dx = x - cx + 0.5
            dy = y - top_y + 0.5
            if dy <= h and abs(dx) / edge + abs(dy - h / 2) / (h / 2) <= 1.0:
                u = int(dx / 2 + dy)
                v = int(-dx / 2 + dy)
                c = tex(GRASS, u, v, "top")
                c = mix(c, hexc("#a9bbd6"), 0.12)  # moonlight on the top face
            elif dy > h / 2:
                left = dx < 0
                edge_y = h - abs(dx) / 2  # where this column's side face starts (the diamond's lower edge)
                depth = dy - edge_y
                if depth < 0 or depth > edge:
                    continue
                col = int(abs(dx))
                if depth < max(2, edge // 6) + (col * 7 % 3 == 0):
                    c = tex(GRASS, col, int(depth), "fringe")
                else:
                    c = tex(DIRT, col, int(depth), "dirt")
                c = mix(c, hexc("#000000"), 0.15 if left else 0.45)
            else:
                continue
            if 0 <= x < img.width and 0 <= y < img.height:
                px[x, y] = c
    return top_y + h // 2  # feet height for something standing in the middle of the top


# --- pixel font for the title ----------------------------------------------------------------------------------
FONT = {
    "B": ["1110", "1001", "1001", "1110", "1001", "1001", "1110"],
    "L": ["1000", "1000", "1000", "1000", "1000", "1000", "1111"],
    "O": ["0110", "1001", "1001", "1001", "1001", "1001", "0110"],
    "C": ["0111", "1000", "1000", "1000", "1000", "1000", "0111"],
    "K": ["1001", "1010", "1100", "1100", "1010", "1001", "1001"],
    "S": ["0111", "1000", "1000", "0110", "0001", "0001", "1110"],
    "U": ["1001", "1001", "1001", "1001", "1001", "1001", "0110"],
}


def title(img, text, x, y, scale):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    top = hexc("#ffe29a")
    bottom = hexc("#d9531a")
    cx = x
    for ch in text:
        rows = FONT[ch]
        for r, row in enumerate(rows):
            c = mix(top, bottom, r / (len(rows) - 1))
            for col, bit in enumerate(row):
                if bit == "1":
                    rect(d, cx + col * scale, y + r * scale, scale, scale, c)
        cx += (len(rows[0]) + 1) * scale
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    shadow.paste(Image.new("RGBA", img.size, hexc("#000000", 170)), (2, 2), layer.split()[3])
    img.alpha_composite(shadow)
    img.alpha_composite(outlined(layer, hexc("#1a0803")))
    return cx


# --- scenes ----------------------------------------------------------------------------------------------------
def sky(img, top, bottom, seed):
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    for y in range(img.height):
        d.line([(0, y), (img.width, y)], fill=mix(top, bottom, y / img.height))
    for _ in range(img.width * img.height // 260):
        x, y = rnd.randrange(img.width), rnd.randrange(int(img.height * 0.6))
        b = rnd.choice((90, 140, 200, 255))
        img.putpixel((x, y), (220, 225, 255, b))


def hills(img, base_y, amp, color, seed, step=6):
    rnd = random.Random(seed)
    pts = [(0, img.height)]
    y = base_y
    for x in range(0, img.width + step, step):
        y = max(base_y - amp, min(base_y + amp // 2, y + rnd.randint(-3, 3)))
        pts.append((x, y))
    pts.append((img.width, img.height))
    ImageDraw.Draw(img).polygon(pts, fill=color)


def fog(img, y0, y1, color, max_alpha):
    f = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(f)
    for y in range(y0, y1):
        t = 1 - abs((y - (y0 + y1) / 2) / ((y1 - y0) / 2))
        d.line([(0, y), (img.width, y)], fill=color[:3] + (int(max_alpha * t),))
    img.alpha_composite(f)


def gallery():
    W, H = 320, 180
    img = Image.new("RGBA", (W, H))
    sky(img, hexc("#07060f"), hexc("#2a1d3a"), seed=7)
    # Moon with a halo.
    glow(img, [(258, 40, 16)], hexc("#c9d4ff", 160), 10, 1.0)
    d = ImageDraw.Draw(img)
    d.ellipse([244, 26, 272, 54], fill=hexc("#e8eeff"))
    for (mx, my, r) in ((252, 34, 3), (262, 44, 4), (255, 47, 2)):
        d.ellipse([mx - r, my - r, mx + r, my + r], fill=hexc("#c3cbe6"))
    hills(img, 118, 14, hexc("#161427"), seed=11, step=8)
    fog(img, 104, 132, hexc("#5b4f7a"), 90)
    hills(img, 132, 8, hexc("#0f1418"), seed=12, step=5)
    # Ground: dark grass, a little texture.
    rnd = random.Random(5)
    d = ImageDraw.Draw(img)
    rect(d, 0, 140, W, H - 140, hexc("#0d140d"))
    for _ in range(900):
        x, y = rnd.randrange(W), rnd.randrange(140, H)
        img.putpixel((x, y), rnd.choice(GRASS[:2]))

    # Far zombies: small, fogged silhouettes.
    for i, (x, feet, right) in enumerate([(40, 138, True), (76, 136, True), (230, 136, False), (276, 139, False),
                                          (12, 141, True), (300, 142, False)]):
        z, _ = zombie(right, seed=40 + i)
        z = z.resize((z.width * 2 // 3, z.height * 2 // 3), Image.NEAREST)
        dark = Image.new("RGBA", z.size, hexc("#151225"))
        dark.putalpha(z.split()[3].point(lambda a: int(a * 0.85)))
        img.alpha_composite(dark, (x - z.width // 2, feet - z.height))

    # The block and the knight.
    feet = iso_block(img, 160, 106, 32)
    k, eyes = knight(160, feet + 2, helm=(14, 13), torso=(18, 16), legs=15, cloak_flare=5, seed=2)
    img.alpha_composite(k.crop((0, 0, W, H)))
    glow(img, [(x, y, 1) for x, y in eyes], hexc("#ff8a1e", 255), 2, 2.2)
    for x, y in eyes:
        img.putpixel((x, y), EMBER_HOT)

    # Embers rising off the knight.
    rnd = random.Random(9)
    pts = []
    for _ in range(18):
        x = 160 + rnd.randint(-28, 28)
        y = feet - rnd.randint(10, 70)
        pts.append((x, y, 0))
        img.putpixel((x, y), rnd.choice((EMBER, hexc("#ff7a1a"))))
    glow(img, pts, hexc("#ff7a1a", 200), 2, 1.2)

    # Near zombies: closing in from both sides.
    eye_pts = []
    for i, (x, feet_z, right) in enumerate([(92, 160, True), (60, 170, True), (122, 178, True),
                                            (232, 158, False), (262, 172, False), (200, 181, False)]):
        z, e = zombie(right, seed=i)
        k_ = 1.0 + (feet_z - 156) / 40
        z = z.resize((int(z.width * k_), int(z.height * k_)),
                     Image.NEAREST)
        f = z.width / 24
        ox, oy = x - z.width // 2, feet_z - z.height
        img.alpha_composite(z, (ox, oy))
        eye_pts.append((ox + int(e[0] * f), oy + int(e[1] * f), 1))
    glow(img, eye_pts, hexc("#ff2a1a", 255), 2, 1.8)

    title(img, "BLOCKSOULS", 10, 10, 2)

    # Vignette.
    v = Image.new("L", (W, H), 0)
    dv = ImageDraw.Draw(v)
    for i in range(40):
        dv.rectangle([i, i, W - 1 - i, H - 1 - i], outline=int(150 * (1 - i / 40) ** 2))
    shade = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    shade.putalpha(v)
    img.alpha_composite(shade)
    return img.resize((W * 6, H * 6), Image.NEAREST)


def icon():
    S = 64
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bg = Image.new("RGBA", (S, S))
    sky(bg, hexc("#120d24"), hexc("#3a2752"), seed=3)
    glow(bg, [(48, 14, 6)], hexc("#c9d4ff", 150), 4, 1.0)
    ImageDraw.Draw(bg).ellipse([43, 9, 53, 19], fill=hexc("#e8eeff"))
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=12, fill=255)
    img.paste(bg, (0, 0), mask)
    feet = iso_block(img, 32, 37, 13, seed=8)
    # Chibi: a big helm on a small body.
    layer, eyes = knight(32, feet + 1, helm=(20, 17), torso=(14, 8), legs=5, cloak_flare=2, sword=False, seed=4)
    img.alpha_composite(layer.crop((0, 0, S, S)))
    glow(img, [(x, y, 1) for x, y in eyes], hexc("#ff8a1e", 255), 2, 2.4)
    for x, y in eyes:
        img.putpixel((x, y), EMBER_HOT)
        img.putpixel((x + 1, y), EMBER)
    img.putalpha(ImageChops.multiply(img.split()[3], mask))
    return img.resize((S * 8, S * 8), Image.NEAREST)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    gallery().save(os.path.join(OUT, "gallery.png"))
    icon().save(os.path.join(OUT, "icon.png"))
    print("wrote", os.path.normpath(OUT))
