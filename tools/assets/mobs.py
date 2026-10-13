#!/usr/bin/env python3
"""Paints the mobs module's textures: every mob skin, its emissive `_glow` layer, and the spawn eggs.

    python3 tools/assets/mobs.py        # writes into mobs/src/main/resources/assets/blocksouls_mobs/textures

Hand-directed pixel art, painted against the UV layout in `mob_models.py` (so editing a model and re-running both
scripts keeps everything aligned). Each mob gets its own color ramps and a painter that places features on named
cube faces: eyes, mouths, ribs, wounds, torn cloth, veins. Face coordinates are as seen from outside the mob with
its top up; on the front face x = 0 is the mob's right side (the viewer's left). Glow layers are transparent except
for what should shine in the dark. Deterministic: same script, same pixels.
"""

import hashlib
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mob_models import MODELS  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "mobs", "src", "main", "resources", "assets",
                    "blocksouls_mobs", "textures")

BAYER = np.array([[0, 2], [3, 1]]) / 4 - 0.375


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*colors):
    return [hexrgb(c) for c in colors]


class Face:
    def __init__(self, x, y, w, h):
        self.x, self.y, self.w, self.h = x, y, w, h


class Canvas:
    """A texture plus the model's UV layout, with face-local drawing helpers."""

    def __init__(self, name, transparent=False):
        self.model = MODELS[name]
        tw, th = self.model.tex
        self.a = np.zeros((th, tw, 4), np.uint8)
        self.rng = np.random.default_rng(int(hashlib.sha256((name + str(transparent)).encode()).hexdigest()[:12], 16))
        self.transparent = transparent

    def face(self, cube_id, kind):
        c = self.model.cube(cube_id)
        w, h, d = (int(round(n)) for n in c.size)
        u, v = c.u, c.v
        return Face(*{
            "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
        }[kind])

    def faces(self, cube_id, kinds=("top", "bottom", "right", "front", "left", "back")):
        return [self.face(cube_id, k) for k in kinds]

    def px(self, f, x, y, color, alpha=255):
        if 0 <= x < f.w and 0 <= y < f.h:
            self.a[f.y + y, f.x + x] = (*color[:3], alpha)

    def rect(self, f, x, y, w, h, color, alpha=255):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.px(f, xx, yy, color, alpha)

    def get(self, f, x, y):
        return self.a[f.y + y, f.x + x]

    def noise(self, h, w, cell=3):
        gh, gw = h // cell + 2, w // cell + 2
        g = self.rng.random((gh, gw))
        ys, xs = np.arange(h) / cell, np.arange(w) / cell
        y0, x0 = ys.astype(int), xs.astype(int)
        fy, fx = (ys - y0)[:, None], (xs - x0)[None, :]
        a = g[y0][:, x0] * (1 - fx) + g[y0][:, x0 + 1] * fx
        b = g[y0 + 1][:, x0] * (1 - fx) + g[y0 + 1][:, x0 + 1] * fx
        return a * (1 - fy) + b * fy

    def skin(self, cube_ids, colors, base=0.5, amp=0.35, cell=3, grain=0.12):
        """Base coat: clumped noise mapped onto a dark-to-light ramp, lit from above, dithered."""
        for cid in cube_ids:
            for kind in ("top", "bottom", "right", "front", "left", "back"):
                f = self.face(cid, kind)
                if f.w <= 0 or f.h <= 0:
                    continue
                val = base + (self.noise(f.h, f.w, cell) - 0.5) * amp * 2 + (self.rng.random((f.h, f.w)) - 0.5) * grain
                if kind == "top":
                    val += 0.14
                elif kind == "bottom":
                    val -= 0.28
                else:
                    val += np.linspace(0.1, -0.14, f.h)[:, None] if f.h > 1 else 0
                    val[-1:, :] -= 0.08
                yy, xx = np.mgrid[0:f.h, 0:f.w]
                val += BAYER[yy % 2, xx % 2] * (1.0 / len(colors))
                idx = np.clip((val * len(colors)).astype(int), 0, len(colors) - 1)
                pal = np.array(colors, np.uint8)
                self.a[f.y:f.y + f.h, f.x:f.x + f.w, :3] = pal[idx]
                self.a[f.y:f.y + f.h, f.x:f.x + f.w, 3] = 255

    def scatter(self, cube_ids, colors, density, kinds=("front", "right", "left", "back", "top")):
        for cid in cube_ids:
            for k in kinds:
                f = self.face(cid, k)
                for _ in range(int(f.w * f.h * density + self.rng.random())):
                    self.px(f, int(self.rng.integers(f.w)), int(self.rng.integers(f.h)), colors[int(self.rng.integers(len(colors)))])

    def wound(self, f, x, y, w, h, core, rim, wet=None):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                edge = yy in (y, y + h - 1) or xx in (x, x + w - 1)
                if edge and self.rng.random() < 0.35:
                    continue
                self.px(f, xx, yy, rim if edge else core)
        if wet:
            self.px(f, x + w // 2, y + h // 2 - (h > 2), wet)

    def vein(self, f, x, y, length, color, wander=0.35, down=True, alpha=255):
        for _ in range(length):
            self.px(f, x, y, color, alpha)
            if self.rng.random() < wander:
                x += 1 if self.rng.random() < 0.5 else -1
            else:
                y += 1 if down else -1
            x = max(0, min(f.w - 1, x))

    def band(self, cube_id, y, color, h=1, chance=1.0):
        for k in ("right", "front", "left", "back"):
            f = self.face(cube_id, k)
            for yy in range(y, y + h):
                for xx in range(f.w):
                    if self.rng.random() < chance:
                        self.px(f, xx, yy, color)

    def save(self, path):
        Image.fromarray(self.a, "RGBA").save(path)


def eye(c, f, x, y, color, socket, glint=None):
    c.rect(f, x - 1, y - 1, 3, 3, socket) if socket else None
    c.px(f, x, y, color)
    if glint:
        c.px(f, x + 1, y, glint)


def teeth(c, f, y, x0, x1, tooth, gap, every=2):
    for x in range(x0, x1):
        c.px(f, x, y, tooth if (x - x0) % every == 0 or c.rng.random() < 0.25 else gap)


def torn_edge(c, f, color=None, depth=3):
    """Ragged bottom edge on cloth: knock out pixels from the bottom up."""
    for x in range(f.w):
        cut = int(c.rng.integers(0, depth + 1))
        for y in range(f.h - cut, f.h):
            c.px(f, x, y, color or (0, 0, 0), 0 if color is None else 255)


def holes(c, f, n, maxw=3, maxh=3):
    for _ in range(n):
        w, h = int(c.rng.integers(1, maxw + 1)), int(c.rng.integers(1, maxh + 1))
        x, y = int(c.rng.integers(0, max(1, f.w - w + 1))), int(c.rng.integers(0, max(1, f.h - h + 1)))
        c.rect(f, x, y, w, h, (0, 0, 0), 0)


# --- Ramps --------------------------------------------------------------------------------------------------------

ROT = ramp("#232b1d", "#38432c", "#4f5a3c", "#66714b", "#7f8a5e", "#9aa275")
BRUISE = ramp("#2a1f2a", "#45304a", "#5e4256")
BLOOD = hexrgb("#4a0f10"), hexrgb("#7a1a17"), hexrgb("#a8322a")
BONE = ramp("#6b6252", "#a39880", "#d1c7ad", "#ede5cf")
CLOTH = ramp("#1c232c", "#2b3642", "#3c4a58", "#516272")
DENIM = ramp("#1a1d24", "#262c38", "#343d4d", "#45506a")
PALE = ramp("#3b3c40", "#58585a", "#77746f", "#958f86", "#b3ab9d", "#cfc6b5")
GAUNT = ramp("#3a3a40", "#56565c", "#75747a", "#97959a", "#b9b6b8", "#dad6d2")
BILE = ramp("#2c3214", "#45501d", "#606d25", "#7d8a33", "#9ca847", "#bcc566")
PUS = ramp("#6f6a1e", "#9a9128", "#c4b83a", "#e3d86a")
MEAT = ramp("#2b1714", "#4a2620", "#6a372c", "#8a4a3a", "#a8614c", "#c47f66")
SCAR = ramp("#5c3a35", "#7e554a")
RUST = ramp("#3b1d10", "#6e3518", "#9c4f22", "#c8743a")
SICK = ramp("#1c1f18", "#2f3426", "#454b35", "#5e6242", "#7a7b52", "#959466")
PIG = ramp("#5a2e2c", "#86453f", "#b06257", "#cf8174", "#e7a596")
BLIGHT = ramp("#2a0a14", "#5c1328", "#8f2340", "#c2456a", "#e88aa6")
LEATHER = ramp("#2a1a10", "#3f2817", "#5a3a20")
GOLD = hexrgb("#d9a520"), hexrgb("#a87612"), hexrgb("#f2d36b")
ASH = ramp("#141313", "#211f1e", "#302c2a", "#423d3a", "#58524d", "#706862")
CINDER = ramp("#6e6661", "#958a83", "#b9b0a8")
EMBER = hexrgb("#ff7a1a"), hexrgb("#ffb347"), hexrgb("#c23c0c"), hexrgb("#ffe08a")
RAD = hexrgb("#7fd33a"), hexrgb("#c8ff6e"), hexrgb("#4fae24"), hexrgb("#efffb8")
BLACK = hexrgb("#0b0a0a")
VOMIT = ramp("#262a17", "#3b4222", "#535c30", "#6b7542", "#868f58", "#a3aa74")
SCRAP = ramp("#1e1f22", "#33363a", "#4b4f53", "#666a6c", "#858785", "#a8a7a0")


# --- Painters -----------------------------------------------------------------------------------------------------

def shambler():
    c = Canvas("shambler")
    c.skin(["skull", "jaw", "torso", "right_upper", "right_forearm", "right_hand", "left_arm", "right_leg", "left_leg"], ROT)
    c.scatter(["torso", "skull", "left_arm", "left_leg"], BRUISE, 0.05)
    # Face: sunken sockets, pinprick eyes, a nose hole, scalp torn open to the bone on top.
    f = c.face("skull", "front")
    for x, y in ((1, 3), (5, 3)):
        c.rect(f, x, y, 2, 2, ROT[0])
    c.px(f, 2, 4, hexrgb("#d8e070"))
    c.px(f, 5, 4, hexrgb("#a6ad55"))
    c.px(f, 3, 5, BLACK)
    c.px(f, 4, 5, ROT[1])
    teeth(c, f, 6, 1, 7, BONE[2], ROT[0])
    top = c.face("skull", "top")
    c.wound(top, 3, 2, 4, 3, BONE[2], BLOOD[1])
    c.px(top, 4, 3, BONE[3])
    for k in ("right", "left", "back", "top"):
        g = c.face("skull", k)
        for _ in range(4):  # Hair clinging in clumps.
            x = int(c.rng.integers(g.w))
            c.vein(g, x, 0, int(c.rng.integers(1, 3)), hexrgb("#1a1612"), wander=0.5)
    # Hanging jaw: teeth along the top front, dark gums inside.
    j = c.face("jaw", "top")
    c.rect(j, 0, 0, j.w, j.h - 1, hexrgb("#3a1414"))
    teeth(c, j, j.h - 1, 0, j.w, BONE[2], hexrgb("#3a1414"))
    c.px(c.face("jaw", "front"), 2, 0, BLOOD[0])
    # Torso under the shirt: ribs show through the tears.
    t = c.face("torso", "front")
    for y in range(3, 9, 2):
        c.rect(t, 1, y, 6, 1, ROT[4])
        c.rect(t, 1, y + 1, 6, 1, ROT[1])
    c.wound(t, 4, 8, 3, 3, BLOOD[0], BLOOD[1], BLOOD[2])
    # The shirt: faded work shirt, torn and stained, mostly missing on the right side.
    shirt = Canvas("shambler")
    shirt.skin(["shirt"], CLOTH, amp=0.25, cell=2)
    for k in ("front", "back", "right", "left"):
        sf = c.face("shirt", k)
        holes(shirt, sf, 3 if k in ("front", "back") else 1)
        torn_edge(shirt, sf, depth=4)
        for _ in range(3):
            shirt.px(sf, int(c.rng.integers(sf.w)), int(c.rng.integers(sf.h)), hexrgb("#3b1a16"))
    sf = c.face("shirt", "front")
    shirt.rect(sf, 0, 0, 3, 5, (0, 0, 0), 0)  # ripped open at the collar
    shirt.rect(sf, 3, 0, 1, 12, hexrgb("#222a33"))  # button placket
    for y in (2, 5, 8):
        shirt.px(sf, 3, y, BONE[1])
    shirt.rect(c.face("shirt", "top"), 0, 0, 8, 4, (0, 0, 0), 0)
    shirt.rect(c.face("shirt", "bottom"), 0, 0, 8, 4, (0, 0, 0), 0)
    copy_cubes(c, shirt, ["shirt"])
    # Trousers on the right leg only; the left leg is bare to the bone at the shin.
    pants = Canvas("shambler")
    pants.skin(["right_pants"], DENIM, amp=0.25, cell=2)
    for k in ("front", "back", "right", "left"):
        pf = c.face("right_pants", k)
        holes(pants, pf, 1, 2, 2)
        torn_edge(pants, pf, depth=5)
    pants.rect(c.face("right_pants", "top"), 0, 0, 4, 4, (0, 0, 0), 0)
    pants.rect(c.face("right_pants", "bottom"), 0, 0, 4, 4, (0, 0, 0), 0)
    copy_cubes(c, pants, ["right_pants"])
    lf = c.face("left_leg", "front")
    c.rect(lf, 1, 6, 1, 4, BONE[2])
    c.wound(lf, 0, 5, 3, 6, BLOOD[0], BLOOD[1])
    c.rect(lf, 1, 6, 1, 4, BONE[2])
    # Dragging hand: knuckles worn raw, black nails.
    for k in ("front", "bottom"):
        hf = c.face("right_hand", k)
        c.rect(hf, 0, hf.h - 1, hf.w, 1, BLOOD[0])
        for x in range(0, hf.w, 2):
            c.px(hf, x, hf.h - 1, BLACK)
    c.rect(c.face("right_forearm", "front"), 0, 3, 3, 1, ROT[0])
    c.wound(c.face("right_upper", "right"), 1, 2, 2, 3, BLOOD[0], BLOOD[1])
    claw(c, "left_arm")
    c.save(path("shambler"))


def claw(c, cid):
    f = c.face(cid, "bottom")
    c.rect(f, 0, 0, f.w, f.h, hexrgb("#2a2420"))
    for k in ("front", "right", "left", "back"):
        g = c.face(cid, k)
        for x in range(g.w):
            c.px(g, x, g.h - 1, hexrgb("#1c1712") if x % 2 == 0 else hexrgb("#3a322a"))


def copy_cubes(dst, src, cube_ids):
    """Copy the given cubes' texture area (overlay layers painted on their own canvas)."""
    for cid in cube_ids:
        for f in dst.faces(cid):
            dst.a[f.y:f.y + f.h, f.x:f.x + f.w] = src.a[f.y:f.y + f.h, f.x:f.x + f.w]


def crawler():
    c = Canvas("crawler")
    limbs = [f"{s}_{p}_{seg}" for s in ("right", "left") for p in ("front", "mid", "hind") for seg in ("upper", "lower")]
    c.skin(["torso", "hips", "skull", "jaw"], PALE)
    c.skin(limbs, PALE, base=0.42)
    c.skin(["spine1", "spine2", "spine3"], BONE, base=0.6, amp=0.2)
    c.scatter(["torso", "hips"], BRUISE, 0.06)
    # Back: the skin split along the spine, ribs pressing through the flanks.
    top = c.face("torso", "top")
    for y in range(top.h):
        c.px(top, 3, y, hexrgb("#4a1a1c"))
        c.px(top, 4, y, BLOOD[1] if y % 3 else BONE[1])
    for k in ("right", "left"):
        f = c.face("torso", k)
        for x in range(1, f.w - 1, 2):
            c.rect(f, x, 1, 1, 3, PALE[5])
            c.rect(f, x + 1, 1, 1, 3, PALE[1])
    c.wound(c.face("torso", "bottom"), 2, 3, 4, 5, BLOOD[0], BLOOD[1], BLOOD[2])
    # Face: a cluster of small yellow eyes, a split lower face full of teeth.
    f = c.face("skull", "front")
    c.rect(f, 0, 0, f.w, 3, PALE[1])
    for x, y in ((1, 1), (4, 1), (2, 0), (3, 2)):
        c.px(f, x, y, hexrgb("#e8ec8a"))
    c.px(f, 0, 2, BLACK)
    c.px(f, 5, 2, BLACK)
    teeth(c, f, 4, 0, 6, BONE[3], hexrgb("#2a0e10"))
    j = c.face("jaw", "top")
    c.rect(j, 0, 0, j.w, j.h, hexrgb("#3a1012"))
    teeth(c, j, j.h - 1, 0, j.w, BONE[3], hexrgb("#3a1012"))
    teeth(c, j, j.h - 2, 0, j.w, hexrgb("#3a1012"), BONE[2], every=3)
    # Limbs: blackened joints and claws at the ground end.
    for cid in limbs:
        if cid.endswith("lower"):
            claw(c, cid)
            for k in ("front", "right", "left", "back"):
                g = c.face(cid, k)
                c.rect(g, 0, 0, g.w, 1, BRUISE[0])
        else:
            g = c.face(cid, "top")
            c.rect(g, 0, 0, g.w, g.h, PALE[2])
    c.save(path("crawler"))


def screamer():
    c = Canvas("screamer")
    body = ["pelvis", "spine", "ribs", "neck", "skull", "jaw", "chin", "right_leg", "left_leg", "right_knee", "left_knee",
            "right_upper", "right_forearm", "left_upper", "left_forearm"]
    c.skin(body, GAUNT, base=0.55)
    c.skin(["teeth"], BONE, base=0.75, amp=0.1)
    fingers = ["right_finger1", "right_finger2", "left_finger1", "left_finger2"]
    c.skin(fingers, GAUNT, base=0.35)
    for cid in fingers:
        claw(c, cid)
    # Ribcage front and back: bars of bone over dark hollows.
    for k in ("front", "back"):
        f = c.face("ribs", k)
        c.rect(f, 0, 0, f.w, f.h, hexrgb("#2b2329"))
        for y in range(0, f.h - 1, 2):
            c.rect(f, 0, y, f.w, 1, GAUNT[4])
            c.px(f, 0, y, GAUNT[2])
            c.px(f, f.w - 1, y, GAUNT[2])
        c.rect(f, f.w // 2, 0, 1, f.h, GAUNT[5] if k == "back" else GAUNT[3])
    for k in ("right", "left"):
        f = c.face("ribs", k)
        for y in range(0, f.h - 1, 2):
            c.rect(f, 0, y + 1, f.w, 1, GAUNT[1])
    for k in ("front", "back", "right", "left"):
        f = c.face("spine", k)
        for y in range(f.h):
            c.px(f, f.w // 2, y, GAUNT[5] if y % 2 else GAUNT[2])
    # Skull: huge black sockets with red pinpoints, nose slit, skin stretched to grey bone.
    f = c.face("skull", "front")
    c.rect(f, 0, 1, 2, 2, BLACK)
    c.rect(f, 4, 1, 2, 2, BLACK)
    c.px(f, 1, 2, hexrgb("#ff4a3a"))
    c.px(f, 4, 2, hexrgb("#ff4a3a"))
    c.px(f, 2, 3, GAUNT[0])
    c.px(f, 3, 3, GAUNT[0])
    c.rect(f, 0, 4, f.w, 1, GAUNT[1])
    for k in ("right", "left", "back", "top"):
        g = c.face("skull", k)
        c.vein(g, int(c.rng.integers(g.w)), 0, 4, GAUNT[1])
    # Upper teeth row, and the jaw: a long tongue-less maw, red throat, fangs along the rim.
    tf = c.face("teeth", "front")
    teeth(c, tf, 0, 0, tf.w, BONE[3], hexrgb("#3a0d10"))
    j = c.face("jaw", "top")
    c.rect(j, 0, 0, j.w, j.h, hexrgb("#5a1418"))
    c.rect(j, 1, 0, j.w - 2, j.h - 2, hexrgb("#2a070a"))
    teeth(c, j, j.h - 1, 0, j.w, BONE[3], hexrgb("#5a1418"))
    for y in range(j.h):
        c.px(j, 0, y, BONE[2] if y % 2 else hexrgb("#5a1418"))
        c.px(j, j.w - 1, y, BONE[2] if y % 2 == 0 else hexrgb("#5a1418"))
    c.rect(c.face("chin", "top"), 0, 0, 4, 2, hexrgb("#2a070a"))
    cf = c.face("chin", "front")
    c.rect(cf, 0, cf.h - 1, cf.w, 1, GAUNT[0])
    # Knobbly knees and joints darker.
    for cid in ("right_knee", "left_knee"):
        c.skin([cid], GAUNT, base=0.72, amp=0.1)
    c.save(path("screamer"))


def bloater():
    c = Canvas("bloater")
    sacs = ["sac_front", "sac_low", "sac_right", "sac_left", "sac_top", "sac_back"]
    c.skin(["belly", "girth", "head", "right_leg", "left_leg", "right_arm", "left_arm"], BILE, base=0.5, cell=4)
    # Stretched skin: purple veins crawling over the belly, a split seam leaking down the front.
    for cid, k in [("belly", k) for k in ("front", "right", "left", "back", "top")] + [("girth", k) for k in ("right", "left", "back", "top")]:
        f = c.face(cid, k)
        for _ in range(5):
            c.vein(f, int(c.rng.integers(f.w)), int(c.rng.integers(f.h // 2)), int(c.rng.integers(6, 14)),
                   hexrgb("#4d2a44"), wander=0.45)
        c.scatter([cid], [BILE[5], BILE[4]], 0.01, (k,))
    f = c.face("girth", "front")
    for y in range(1, 11):
        x = 9 + (1 if y % 4 == 0 else 0)
        c.px(f, x, y, hexrgb("#1c1a0a"))
        c.px(f, x + 1, y, PUS[1])
        if y % 3 == 0:
            c.px(f, x - 1, y, BONE[1])
            c.px(f, x + 2, y, BONE[1])
    c.vein(f, 10, 11, 1, PUS[2], wander=0.1)
    c.wound(f, 14, 6, 3, 3, hexrgb("#3a2a0a"), PUS[1])
    c.wound(c.face("belly", "bottom"), 5, 6, 6, 5, hexrgb("#2a2a10"), BILE[1])
    # Pustules: glossy, yellow, highlighted, dark at the base.
    for s in sacs:
        c.skin([s], PUS, base=0.55, amp=0.2, cell=2)
        for k in ("front", "right", "left", "back", "top"):
            g = c.face(s, k)
            c.px(g, 1, 1, PUS[3])
            c.px(g, 2, 1, hexrgb("#fff6b0"))
            c.vein(g, g.w - 2, g.h - 1, 3, hexrgb("#6e5a1a"), down=False)
    # Head: tiny, sunk into the fat, beady eyes and a drooling slot of a mouth.
    f = c.face("head", "front")
    c.px(f, 1, 1, BLACK)
    c.px(f, 4, 1, BLACK)
    c.px(f, 1, 2, hexrgb("#f0e060"))
    c.px(f, 4, 2, hexrgb("#f0e060"))
    c.rect(f, 1, 3, 4, 1, hexrgb("#2a1010"))
    c.px(f, 3, 4, PUS[2])
    for cid in ("right_arm", "left_arm"):
        claw(c, cid)
    c.save(path("bloater"))
    # Glow: the pustules shine a sick yellow-green through the night.
    g = Canvas("bloater", transparent=True)
    for s in sacs:
        for k in ("front", "right", "left", "back", "top"):
            f = g.face(s, k)
            for y in range(f.h):
                for x in range(f.w):
                    if g.rng.random() < 0.25:
                        g.px(f, x, y, hexrgb("#4a5410"))
            g.px(f, 1, 1, hexrgb("#9aa830"))
    g.save(path("bloater_glow"))


def brute_base(c, colors, eye_color):
    parts = ["waist", "chest", "hump", "head", "underbite", "right_shoulder", "right_upper", "right_forearm", "right_fist",
             "left_shoulder", "left_upper", "left_forearm", "left_fist", "right_leg", "left_leg"]
    parts = [p for p in parts if not (p.startswith("left") and c.model.cube(p).uv)]
    c.skin(parts, colors, base=0.52, cell=4)
    # Muscle striations on the arms and chest.
    for cid in ("right_upper", "right_forearm", "left_upper", "left_forearm", "right_leg", "left_leg"):
        if c.model.cube(cid).uv:
            continue
        for k in ("front", "right", "left", "back"):
            f = c.face(cid, k)
            for x in range(1, f.w, 3):
                c.vein(f, x, 0, f.h, colors[1], wander=0.15)
    f = c.face("chest", "front")
    c.rect(f, 8, 1, 2, 10, colors[1])  # sternum groove
    for y in (3, 4):
        c.rect(f, 2, y, 5, 1, colors[1])
        c.rect(f, 11, y, 5, 1, colors[1])
    c.wound(f, 2, 7, 4, 3, BLOOD[0], SCAR[1])
    for y in range(6, 11):
        c.px(f, 13 + (y % 2), y, SCAR[1])  # old stitched scar
        c.px(f, 12 + (y % 2) * 2, y, SCAR[0])
    # Hump: a ridge of bony knobs.
    t = c.face("hump", "top")
    for x in range(1, t.w - 1, 3):
        c.rect(t, x, 3, 2, 2, BONE[1])
        c.px(t, x, 3, BONE[2])
    # Fists: raw knuckles, bone showing through on the front.
    for cid in ("right_fist", "left_fist"):
        if c.model.cube(cid).uv:
            continue
        f = c.face(cid, "front")
        for x in range(0, f.w, 2):
            c.rect(f, x, f.h - 3, 1, 2, BONE[2])
            c.px(f, x + 1, f.h - 3, colors[0])
        c.rect(c.face(cid, "bottom"), 0, 0, 8, 8, colors[0])
    # Rusted shackle still locked around the right wrist.
    c.band("right_forearm", 6, RUST[1], 2)
    c.band("right_forearm", 6, RUST[3], 1, chance=0.4)
    c.band("right_forearm", 8, hexrgb("#24140a"), 1)
    # Tiny head: deep-set eyes under a heavy brow, tusks jutting up from the underbite.
    f = c.face("head", "front")
    c.rect(f, 0, 0, 5, 2, colors[1])
    c.px(f, 1, 2, eye_color)
    c.px(f, 3, 2, eye_color)
    c.px(f, 2, 3, colors[0])
    u = c.face("underbite", "front")
    c.rect(u, 0, 0, u.w, 1, colors[0])
    c.px(u, 0, 0, BONE[3])
    c.px(u, u.w - 1, 0, BONE[3])
    teeth(c, u, 0, 1, u.w - 1, BONE[2], colors[0], every=2)
    ut = c.face("underbite", "top")
    c.rect(ut, 0, 0, ut.w, ut.h, hexrgb("#3a1412"))
    c.px(ut, 0, ut.h - 1, BONE[3])
    c.px(ut, ut.w - 1, ut.h - 1, BONE[3])


def brute():
    c = Canvas("brute")
    brute_base(c, MEAT, hexrgb("#ffd27a"))
    c.save(path("brute"))


def mutant_brute():
    c = Canvas("mutant_brute")
    brute_base(c, SICK, hexrgb("#d8ff7a"))
    spines = ["spine1", "spine2", "spine3", "spine4"]
    c.skin(spines + ["nub1", "nub2"], ramp("#5a6b3a", "#8a9a5a", "#c2cc8a", "#e2e8b8"), base=0.6, amp=0.2)
    c.skin(["growth"], ramp("#2c3a18", "#3f5a1e", "#5a7a28", "#7da236"), base=0.5, cell=2)
    g = c.face("growth", "front")
    for _ in range(4):
        c.wound(g, int(c.rng.integers(0, 5)), int(c.rng.integers(0, 4)), 2, 2, hexrgb("#1a2408"), hexrgb("#3f5a1e"))
    # Burn-like radiation blotches.
    c.scatter(["chest", "right_shoulder", "left_upper", "waist"], ramp("#2a2e1a", "#6a7a3a"), 0.05)
    c.save(path("mutant_brute"))
    # Glow: rad-green veins branching over the body and arms, eyes, and the tumor/nubs/spine tips lit from within.
    gl = Canvas("mutant_brute", transparent=True)
    for cid in ("chest", "waist", "right_shoulder", "right_upper", "right_forearm", "left_shoulder", "left_upper",
                "left_forearm", "right_leg", "left_leg", "hump"):
        for k in ("front", "right", "left", "back"):
            f = gl.face(cid, k)
            if f.w * f.h < 12:
                continue
            for _ in range(1 + f.w * f.h // 110):
                x, y = int(gl.rng.integers(f.w)), 0 if gl.rng.random() < 0.6 else int(gl.rng.integers(f.h))
                gl.vein(f, x, y, int(gl.rng.integers(f.h // 2, f.h + 1)), RAD[0], wander=0.4)
                gl.px(f, x, y, RAD[1])
    f = gl.face("head", "front")
    gl.px(f, 1, 2, RAD[3])
    gl.px(f, 3, 2, RAD[3])
    for k in ("front", "right", "left", "back", "top"):
        f = gl.face("growth", k)
        for y in range(f.h):
            for x in range(f.w):
                if gl.rng.random() < 0.3:
                    gl.px(f, x, y, RAD[2])
        gl.px(f, f.w // 2, f.h // 2, RAD[1])
        for cid in ("nub1", "nub2"):
            gl.rect(gl.face(cid, k), 0, 0, 3, 2, RAD[1] if k == "top" else RAD[0])
        for cid in ("spine1", "spine2", "spine3", "spine4"):
            sf = gl.face(cid, k)
            gl.rect(sf, 0, 0, sf.w, 1 if k != "top" else sf.h, RAD[1])
    gl.save(path("mutant_brute_glow"))


def infected_piglin():
    c = Canvas("infected_piglin")
    c.skin(["head", "snout", "right_ear", "body", "right_arm", "left_arm", "right_leg", "left_leg"], PIG, base=0.52)
    growths = ["eye_growth", "left_ear", "head_growth1", "head_growth2", "back_growth1", "back_growth2", "arm_growth",
               "knee_growth"]
    c.skin(growths, BLIGHT, base=0.55, cell=2)
    for cid in growths:
        for k in ("front", "right", "left", "back", "top"):
            g = c.face(cid, k)
            c.px(g, int(c.rng.integers(g.w)), int(c.rng.integers(g.h)), BLIGHT[4])
            c.px(g, int(c.rng.integers(g.w)), int(c.rng.integers(g.h)), BLIGHT[0])
    c.skin(["tusk_right", "tusk_left"], BONE, base=0.7, amp=0.1)
    # Face: one squinting yellow eye; the other side swallowed by the growth. Snout with nostrils.
    f = c.face("head", "front")
    c.rect(f, 1, 2, 3, 1, PIG[1])
    c.rect(f, 1, 3, 2, 1, hexrgb("#f0eadc"))
    c.px(f, 2, 3, hexrgb("#b88a10"))
    c.px(f, 6, 3, BLIGHT[1])
    c.vein(f, 5, 4, 4, BLIGHT[2], wander=0.6)
    c.vein(f, 7, 0, 3, BLIGHT[2], wander=0.5)
    s = c.face("snout", "front")
    c.px(s, 1, 2, PIG[0])
    c.px(s, 2, 2, PIG[0])
    c.rect(s, 0, 0, 4, 1, PIG[4])
    # Infection spreading down the left side of the body: veins and blisters.
    for cid in ("body", "left_arm", "left_leg"):
        for k in ("front", "left", "back"):
            g = c.face(cid, k)
            for _ in range(2):
                c.vein(g, g.w - 1 - int(c.rng.integers(2)), int(c.rng.integers(3)), int(c.rng.integers(4, 9)), BLIGHT[2])
    c.scatter(["left_arm"], [BLIGHT[3], BLIGHT[1]], 0.15)
    # Leather belt with a tarnished gold buckle, gold cuff on the right arm.
    c.band("body", 8, LEATHER[1], 2)
    c.band("body", 8, LEATHER[2], 1, chance=0.5)
    bf = c.face("body", "front")
    c.rect(bf, 3, 8, 2, 2, GOLD[1])
    c.px(bf, 3, 8, GOLD[2])
    c.band("right_arm", 7, GOLD[1], 1)
    c.band("right_arm", 8, GOLD[0], 1, chance=0.6)
    c.band("right_leg", 11, LEATHER[0], 1)
    c.band("left_leg", 11, LEATHER[0], 1)
    # Hat and jacket: fleshy overgrowth on the outer layers, transparent where the pig still shows.
    over = Canvas("infected_piglin")
    over.skin(["hat", "jacket"], BLIGHT, base=0.5, cell=2)
    for cid, keep in (("hat", {"top": 0.45, "left": 0.5, "front": 0.04, "back": 0.3, "right": 0.08, "bottom": 0}),
                      ("jacket", {"top": 0, "left": 0.35, "front": 0.1, "back": 0.35, "right": 0.03, "bottom": 0})):
        for k, share in keep.items():
            f = over.face(cid, k)
            field = over.noise(f.h, f.w, 2)
            # Grows from the mob's left (x = w-1 on the front, x = 0 on the back) and the top.
            xs = np.linspace(0, 1, f.w)[None, :] if k in ("front", "top") else np.linspace(1, 0, f.w)[None, :]
            bias = xs * 0.35 if k in ("front", "top", "back") else 0
            mask = field + bias > 1 - share
            region = over.a[f.y:f.y + f.h, f.x:f.x + f.w]
            region[~mask] = 0
            for y in range(f.h):
                for x in range(f.w):
                    if mask[y, x] and over.rng.random() < 0.08:
                        over.px(f, x, y, BLIGHT[4])
    copy_cubes(c, over, ["hat", "jacket"])
    c.save(path("infected_piglin"))


def ash_hound():
    c = Canvas("ash_hound")
    body = ["chest", "waist", "skull", "snout", "jaw", "right_ear", "left_ear", "right_front_leg", "left_front_leg",
            "right_thigh", "right_shin", "left_thigh", "left_shin", "tail", "tail_tip"]
    c.skin(body, ASH, base=0.45, cell=2)
    # Ash flakes: pale, peeling scale-like patches over the fur.
    c.scatter(body, CINDER, 0.09)
    flakes = ["flake1", "flake2", "flake3", "flake4"]
    c.skin(flakes, CINDER, base=0.45, amp=0.2)
    for cid in flakes:
        for k in ("right", "left"):
            f = c.face(cid, k)
            c.rect(f, 0, f.h - 1, f.w, 1, ASH[2])
    # Ribs showing on the lean chest.
    for k in ("right", "left"):
        f = c.face("chest", k)
        for x in range(1, f.w, 2):
            c.rect(f, x, 2, 1, 3, ASH[4])
    # Face: ember eyes in black sockets, scorched snout, mouth glowing inside.
    f = c.face("skull", "front")
    c.rect(f, 0, 1, 2, 2, BLACK)
    c.rect(f, 3, 1, 2, 2, BLACK)
    c.px(f, 1, 2, EMBER[0])
    c.px(f, 3, 2, EMBER[0])
    s = c.face("snout", "front")
    c.rect(s, 0, 0, s.w, s.h, ASH[0])
    c.px(s, 0, 1, ASH[3])
    c.px(s, 2, 1, ASH[3])
    for k in ("bottom", "right", "left"):
        g = c.face("snout", k)
        c.rect(g, 0, g.h - 1, g.w, 1, hexrgb("#3a0e04"))
    j = c.face("jaw", "top")
    c.rect(j, 0, 0, j.w, j.h, hexrgb("#4a1004"))
    teeth(c, j, j.h - 1, 0, j.w, BONE[2], hexrgb("#4a1004"), every=2)
    for y in range(0, j.h, 2):
        c.px(j, 0, y, BONE[2])
        c.px(j, j.w - 1, y, BONE[2])
    for cid in ("right_front_leg", "left_front_leg", "right_shin", "left_shin"):
        claw(c, cid)
    c.save(path("ash_hound"))
    # Glow: ember eyes, the inside of the mouth, cracks along the flanks and spine where it still burns.
    g = Canvas("ash_hound", transparent=True)
    f = g.face("skull", "front")
    g.px(f, 1, 2, EMBER[3])
    g.px(f, 3, 2, EMBER[3])
    g.px(f, 0, 2, EMBER[2])
    g.px(f, 4, 2, EMBER[2])
    j = g.face("jaw", "top")
    g.rect(j, 1, 0, j.w - 2, j.h - 1, EMBER[2])
    g.rect(j, 1, 1, 1, j.h - 3, EMBER[0])
    for k in ("bottom", "right", "left"):
        sf = g.face("snout", k)
        g.rect(sf, 0, sf.h - 1, sf.w, 1, EMBER[2])
    for cid in ("chest", "waist", "right_thigh", "left_thigh"):
        for k in ("right", "left", "top", "front"):
            sf = g.face(cid, k)
            if sf.w * sf.h < 8:
                continue
            for _ in range(sf.w * sf.h // 30):
                g.vein(sf, int(g.rng.integers(sf.w)), int(g.rng.integers(sf.h)), int(g.rng.integers(2, 5)),
                       EMBER[2] if g.rng.random() < 0.6 else EMBER[0], wander=0.6)
    for cid in ("flake1", "flake2", "flake3", "flake4", "tail_tip"):
        for k in ("right", "left", "front", "back", "top"):
            sf = g.face(cid, k)
            g.rect(sf, 0, 0, sf.w, 1, EMBER[0])
    g.save(path("ash_hound_glow"))


def spitter():
    c = Canvas("spitter")
    c.skin(["torso", "skull", "jaw", "right_arm", "right_leg"], VOMIT, base=0.5)
    c.skin(["throat", "back_sac1", "back_sac2"], BILE, base=0.62, amp=0.2, cell=2)
    c.scatter(["torso", "skull", "right_arm"], BRUISE, 0.04)
    # Sacs: stretched thin, glossy highlight, green veins crawling over them.
    for cid in ("throat", "back_sac1", "back_sac2"):
        for k in ("front", "right", "left", "back", "top", "bottom"):
            f = c.face(cid, k)
            c.px(f, 1, 1, BILE[5])
            c.vein(f, f.w - 1, 0, max(2, f.h), hexrgb("#2f3a12"), wander=0.5)
    # Face: milky eyes, a nose slit, lips eaten back from the teeth by acid.
    f = c.face("skull", "front")
    for x in (1, 5):
        c.px(f, x, 3, hexrgb("#d8e8a0"))
        c.px(f, x, 2, VOMIT[0])
    c.px(f, 3, 4, VOMIT[0])
    teeth(c, f, 6, 1, 6, BONE[2], hexrgb("#1f2a0c"))
    j = c.face("jaw", "top")
    c.rect(j, 0, 0, j.w, j.h, hexrgb("#2a3a0e"))
    teeth(c, j, 0, 0, j.w, BONE[2], hexrgb("#2a3a0e"))
    # Bile stains dribbling down the chest from the mouth.
    f = c.face("torso", "front")
    for x in (2, 3, 5):
        c.vein(f, x, 0, int(c.rng.integers(4, 9)), BILE[3], wander=0.2)
    torn_edge(c, c.face("torso", "front"), VOMIT[0], depth=1)
    c.save(path("spitter"))
    # Glow: the bile in the sacs and dribbling from the mouth.
    g = Canvas("spitter", transparent=True)
    for cid in ("throat", "back_sac1", "back_sac2"):
        for k in ("front", "right", "left", "back", "bottom"):
            f = g.face(cid, k)
            for y in range(f.h):
                for x in range(f.w):
                    if g.rng.random() < 0.3:
                        g.px(f, x, y, hexrgb("#4f6a12"))
            g.px(f, 1, 1, hexrgb("#b6e04a"))
    g.save(path("spitter_glow"))


def bulwark():
    c = Canvas("bulwark")
    c.skin(["waist", "chest", "head", "right_shoulder", "right_arm", "right_fist", "right_leg"], MEAT, base=0.42, cell=4)
    c.skin(["helm"], SCRAP, base=0.5, amp=0.25)
    c.skin(["slab", "boss"], SCRAP, base=0.5, amp=0.3, cell=4)
    c.skin(["spike1", "spike2", "spike3", "spike4"], BONE, base=0.6, amp=0.15)
    # Shield: plates of scrap riveted together, rust bleeding from the seams, gouged by old blows.
    f = c.face("slab", "front")
    for y in (7, 15):
        c.rect(f, 0, y, f.w, 1, SCRAP[0])
        for x in range(1, f.w, 3):
            c.px(f, x, y - 1, SCRAP[4])
    c.rect(f, 9, 0, 1, f.h, SCRAP[0])
    for _ in range(6):
        c.vein(f, int(c.rng.integers(f.w)), int(c.rng.choice([8, 16])), int(c.rng.integers(2, 6)), RUST[2], wander=0.2)
    for _ in range(5):
        x, y = int(c.rng.integers(1, f.w - 4)), int(c.rng.integers(1, f.h - 2))
        for i in range(3):
            c.px(f, x + i, y + (i % 2), SCRAP[5])
    c.scatter(["slab", "boss", "helm"], RUST, 0.06)
    b = c.face("boss", "front")
    c.rect(b, 1, 1, b.w - 2, b.h - 2, SCRAP[1])
    c.rect(b, 2, 2, b.w - 4, b.h - 4, SCRAP[4])
    # Helm: an eye slit with two dull orange points behind it.
    h = c.face("helm", "front")
    c.rect(h, 0, 2, h.w, 1, BLACK)
    c.px(h, 1, 2, hexrgb("#ff9a3a"))
    c.px(h, 4, 2, hexrgb("#ff9a3a"))
    # Leather straps across the chest, a shackle on the fist.
    for k in ("front", "back"):
        f = c.face("chest", k)
        for i in range(f.w):
            c.px(f, i, (i * 10) // f.w, LEATHER[1])
            c.px(f, i, (i * 10) // f.w + 1, LEATHER[0])
    c.band("right_arm", 8, RUST[1], 2)
    c.band("right_arm", 8, RUST[3], 1, chance=0.4)
    c.wound(c.face("chest", "front"), 10, 6, 3, 3, BLOOD[0], SCAR[1])
    c.save(path("bulwark"))


def affix_overlays():
    """Affix tints: tileable 64x64 emissive patterns drawn over a mob's whole model (any UV layout).

    Mostly transparent so the mob's own skin reads through; the pattern says what the affix does."""
    os.makedirs(os.path.join(ROOT, "entity", "affix"), exist_ok=True)
    size = 64
    for name, (colors, density, alpha) in AFFIX_TINTS.items():
        rng = np.random.default_rng(int(hashlib.sha256(("affix_" + name).encode()).hexdigest()[:12], 16))
        a = np.zeros((size, size, 4), np.uint8)
        a[:, :, :3] = colors[0]
        a[:, :, 3] = alpha
        if name == "burning":
            # Flame tongues licking upward.
            for _ in range(int(size * density)):
                x, y = int(rng.integers(size)), int(rng.integers(size))
                for i in range(int(rng.integers(2, 6))):
                    a[(y - i) % size, (x + int(rng.integers(-1, 2))) % size] = (*colors[1 + (i > 1)], 200)
        elif name == "frosted":
            # Six-armed ice crystals.
            for _ in range(int(size * density)):
                x, y = int(rng.integers(size)), int(rng.integers(size))
                for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1)):
                    a[(y + dy) % size, (x + dx) % size] = (*colors[1 if (dx or dy) else 2], 210)
        elif name == "volatile":
            # Glowing cracks, as if the skin is about to split.
            for _ in range(int(size * density)):
                x, y = int(rng.integers(size)), int(rng.integers(size))
                for i in range(int(rng.integers(4, 9))):
                    a[y % size, x % size] = (*colors[1 if i % 3 else 2], 220)
                    x += int(rng.integers(-1, 2))
                    y += 1
        elif name == "magnetic":
            # Arcs: zigzag lightning lines.
            for _ in range(int(size * density)):
                x, y = int(rng.integers(size)), int(rng.integers(size))
                for i in range(int(rng.integers(4, 8))):
                    a[y % size, x % size] = (*colors[1 if i % 2 else 2], 220)
                    x += 1
                    y += 1 if i % 2 else -1
        Image.fromarray(a, "RGBA").save(os.path.join(ROOT, "entity", "affix", name + ".png"))


# Base tint, two pattern colours, pattern count per row of texture, base alpha.
AFFIX_TINTS = {
    "burning": ((hexrgb("#ff5a10"), hexrgb("#ff9a2a"), hexrgb("#ffe08a")), 1.2, 46),
    "frosted": ((hexrgb("#7ac8ff"), hexrgb("#c8ecff"), hexrgb("#ffffff")), 0.8, 52),
    "volatile": ((hexrgb("#ff2a2a"), hexrgb("#ff6a3a"), hexrgb("#fff0a0")), 0.6, 40),
    "magnetic": ((hexrgb("#6a5aff"), hexrgb("#9ab4ff"), hexrgb("#e0f0ff")), 0.6, 44),
}


# --- Spawn eggs ---------------------------------------------------------------------------------------------------

EGG = [
    "................", "......####......", ".....######.....", "....########....",
    "...##########...", "...##########...", "..############..", "..############..",
    "..############..", "..############..", "..############..", "...##########...",
    "...##########...", "....########....", "......####......", "................",
]
EGGS = {
    "shambler": (ROT, CLOTH[2], hexrgb("#d8e070")),
    "crawler": (PALE, BRUISE[1], hexrgb("#e8ec8a")),
    "screamer": (GAUNT, hexrgb("#5a1418"), hexrgb("#ff4a3a")),
    "bloater": (BILE, PUS[2], PUS[3]),
    "brute": (MEAT, RUST[2], hexrgb("#ffd27a")),
    "mutant_brute": (SICK, RAD[0], RAD[1]),
    "infected_piglin": (PIG, BLIGHT[2], GOLD[0]),
    "ash_hound": (ASH, CINDER[1], EMBER[0]),
    "spitter": (VOMIT, BILE[4], hexrgb("#b6e04a")),
    "bulwark": (SCRAP, RUST[2], hexrgb("#ff9a3a")),
}


def spawn_egg(name):
    colors, spot, accent = EGGS[name]
    rng = np.random.default_rng(int(hashlib.sha256((name + "_egg").encode()).hexdigest()[:12], 16))
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(EGG):
        for x, ch in enumerate(row):
            if ch != "#":
                continue
            edge = row[x - 1] != "#" or x + 1 >= 16 or row[x + 1] != "#" or EGG[y - 1][x] != "#" or EGG[y + 1][x] != "#"
            shade = 1 + (1 if x < 8 else 0) + (1 if y < 7 else 0)
            if edge:
                color = colors[0]
            elif rng.random() < 0.2:
                color = spot
            else:
                color = colors[min(len(colors) - 1, shade + int(rng.integers(0, 2)))]
            img.putpixel((x, y), tuple(color) + (255,))
    for x, y in ((6, 9), (9, 7), (8, 11)):
        img.putpixel((x, y), tuple(accent) + (255,))
    img.putpixel((6, 3), tuple(colors[-1]) + (255,))
    img.putpixel((5, 4), tuple(colors[-1]) + (255,))
    return img


def path(name):
    return os.path.join(ROOT, "entity", name + ".png")


PAINTERS = {"shambler": shambler, "crawler": crawler, "screamer": screamer, "bloater": bloater, "brute": brute,
            "mutant_brute": mutant_brute, "infected_piglin": infected_piglin, "ash_hound": ash_hound,
            "spitter": spitter, "bulwark": bulwark}


def main():
    os.makedirs(os.path.join(ROOT, "entity"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "item"), exist_ok=True)
    for name, paint in PAINTERS.items():
        paint()
        spawn_egg(name).save(os.path.join(ROOT, "item", name + "_spawn_egg.png"))
    affix_overlays()


if __name__ == "__main__":
    main()
