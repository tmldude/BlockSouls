"""3D gun item models (cuboids) and their 32x32 material atlases. Run through art.py.

Model frame: the barrel points north (-z), the top is +y, the gun's right side is +x. One unit = one pixel of a
16-unit block. Each gun texture is a 4x4 grid of 8x8 material tiles; faces sample a whole tile, so a tile is a
material swatch (bevel lit top-left, per STYLE.md), not a UV unwrap.

Display transforms are derived, not hand-tuned: the grip is placed where a vanilla sword's handle sits in each
hand pose, the barrel points where the camera / body faces, and the GUI view is fitted to the model's bounds.
"""

import json
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw

import artlib as A

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "guns/src/client/resources/assets/blocksouls_guns")

# ------------------------------------------------------------------------------------------------ material tiles


def tile(kind, r, base=None, seed="t"):
    rng = random.Random(seed)
    base = len(r) // 2 if base is None else base
    img = Image.new("RGBA", (8, 8))

    def c(i):
        return r[max(0, min(len(r) - 1, i))]

    for y in range(8):
        for x in range(8):
            i = base + (1 if x == 0 or y == 0 else 0) - (1 if x == 7 or y == 7 else 0)
            if kind in ("plate", "rust", "scuffed") and rng.random() < 0.18:
                i += rng.choice((-1, 1))
            col = c(i)
            if kind == "rust" and rng.random() < 0.35:
                col = A.RUST[rng.randint(2, 4)]
            if kind == "scuffed" and rng.random() < 0.12:
                col = r[min(len(r) - 1, base + 2)]  # bright scratches
            if kind == "grip" and (x + y) % 2 == 0 and 0 < x < 7 and 0 < y < 7:
                col = c(base - 1)
            if kind == "wood":
                col = c(base + (1 if (y * 3 + (x // 3)) % 5 == 0 else 0) - (1 if (y + x // 4) % 4 == 3 else 0))
            if kind == "tape":
                col = c(base + (1 if (x + 2 * y) % 5 == 0 else 0) - (1 if y % 3 == 2 else 0))
            if kind == "vent" and 2 <= y <= 5 and x % 2 == 1 and 0 < x < 7:
                col = A.POLYMER[0]
            if kind == "flute" and x % 3 == 1:
                col = c(base - 2)
            if kind == "bore":
                d = max(abs(x - 3.5), abs(y - 3.5))
                col = c(base) if d > 2.5 else (A.POLYMER[0] if d < 1.6 else c(base - 2))
            if kind == "lens":
                col = A.lerp(A.GLASS[4], A.GLASS[1], (x + y) / 14)
                if (x, y) in ((2, 2), (3, 2), (2, 3)):
                    col = A.GLASS[5]
            if kind == "glow":
                col = c(base)
            img.putpixel((x, y), col)
    return img


# --------------------------------------------------------------------------------------------------- gun designs
# Each part: (name, from, to, tile, {opts}) with opts: rot=(axis, angle, origin), faces={dir: tile}.

GUNS = {
    # Cheap and taped-together: rusty slide, cracked polymer frame, duct tape on the grip, a bent-wire rear sight.
    "starter_pistol": {
        "gui_tilt": 0,
        "tiles": {"slide": ("rust", A.SCRAP, 4), "frame": ("plate", A.POLYMER, 3), "bore": ("bore", A.SCRAP, 3),
                  "grip": ("grip", A.POLYMER, 3), "tape": ("tape", A.TAPE, 3), "wire": ("plate", A.BRASS, 3),
                  "dark": ("plate", A.POLYMER, 1), "orange": ("plate", A.EMBER, 3)},
        "grip": (8, 5.5, 10.5),
        "parts": [
            ("slide", (7, 9, 3), (9, 11, 11), "slide", {"faces": {"north": "bore"}}),
            ("frame", (7.1, 8, 4), (8.9, 9, 11), "frame", {}),
            ("muzzle", (7.5, 9.5, 2.5), (8.5, 10.5, 3), "bore", {}),
            ("grip", (7.1, 3, 9.2), (8.9, 8, 11.6), "grip", {"rot": ("x", -20, (8, 8, 10.5))}),
            ("tape", (6.9, 4.5, 9.0), (9.1, 6.2, 11.8), "tape", {"rot": ("x", -20, (8, 8, 10.5))}),
            ("guard", (7.6, 6.6, 6), (8.4, 7, 9), "dark", {}),
            ("guard_front", (7.6, 7, 6), (8.4, 8, 6.4), "dark", {}),
            ("trigger", (7.75, 7, 7.8), (8.25, 8, 8.3), "dark", {}),
            ("front_sight", (7.75, 11, 3.5), (8.25, 11.5, 4), "orange", {}),
            ("rear_sight", (7.4, 11, 10), (8.6, 11.8, 10.3), "wire", {}),
        ],
        "first_scale": 0.85, "third_scale": 0.9,
    },
    # Heavy revolver-type: long top-ribbed barrel, fluted cylinder, wood grip, brass front blade.
    "hand_cannon": {
        "gui_tilt": 8,
        "tiles": {"barrel": ("plate", A.GUNMETAL, 3), "bore": ("bore", A.GUNMETAL, 3),
                  "cyl": ("flute", A.GUNMETAL, 5), "frame": ("plate", A.GUNMETAL, 2), "wood": ("wood", A.WOOD, 3),
                  "rib": ("plate", A.GUNMETAL, 5), "brass": ("plate", A.BRASS, 4), "dark": ("plate", A.POLYMER, 1)},
        "grip": (8, 5.5, 13),
        "parts": [
            ("barrel", (7, 9, 0), (9, 11, 7.5), "barrel", {"faces": {"north": "bore"}}),
            ("rib", (7.4, 11, 0), (8.6, 11.6, 7.5), "rib", {}),
            ("ejector", (7.4, 8.2, 1.5), (8.6, 9, 7.5), "frame", {}),
            ("cylinder", (6.5, 7.8, 7.5), (9.5, 11.4, 11), "cyl", {"faces": {"north": "frame", "south": "frame"}}),
            ("frame", (7, 7.5, 11), (9, 11.6, 12.8), "frame", {}),
            ("topstrap", (7.2, 11.2, 7.3), (8.8, 11.8, 11.3), "frame", {}),
            ("hammer", (7.5, 11.4, 12.4), (8.5, 12.6, 13.4), "dark", {"rot": ("x", 20, (8, 11.5, 12.8))}),
            ("grip", (7.1, 2.6, 11.4), (8.9, 8.6, 14.2), "wood", {"rot": ("x", -25, (8, 8, 12.5))}),
            ("guard", (7.6, 6.2, 8.5), (8.4, 6.6, 11.6), "dark", {}),
            ("guard_front", (7.6, 6.6, 8.5), (8.4, 7.8, 8.9), "dark", {}),
            ("trigger", (7.75, 6.6, 10.2), (8.25, 7.8, 10.7), "dark", {}),
            ("front_sight", (7.75, 11.6, 0.4), (8.25, 12.6, 1.6), "brass", {}),
        ],
        "first_scale": 0.85, "third_scale": 0.85,
    },
    # Polymer-and-steel automatic: vented handguard, curved mag, holo sight with a green dot, rubber buttpad.
    "auto_rifle": {
        "gui_tilt": 30,
        "tiles": {"barrel": ("plate", A.GUNMETAL, 3), "bore": ("bore", A.GUNMETAL, 3), "guard": ("vent", A.POLYMER, 3),
                  "recv": ("plate", A.GUNMETAL, 4), "rail": ("flute", A.GUNMETAL, 3), "poly": ("grip", A.POLYMER, 3),
                  "stock": ("plate", A.POLYMER, 3), "lens": ("lens", A.GLASS), "dot": ("glow", A.TRITIUM, 3),
                  "pad": ("plate", A.RUBBER, 2), "mag": ("plate", A.GUNMETAL, 2), "dark": ("plate", A.POLYMER, 1)},
        "grip": (8, 6, 12),
        "parts": [
            ("brake", (7.25, 9.25, -7), (8.75, 10.75, -5.5), "barrel", {"faces": {"north": "bore"}}),
            ("barrel", (7.5, 9.5, -5.5), (8.5, 10.5, 0), "barrel", {}),
            ("guard", (7, 8.5, 0), (9, 11.5, 6), "guard", {}),
            ("receiver", (7, 8, 6), (9, 11.5, 13), "recv", {}),
            ("rail", (7.5, 11.5, 1), (8.5, 12, 12.5), "rail", {}),
            ("sight", (7.25, 12, 8), (8.75, 13.6, 10.8), "dark", {"faces": {"north": "lens", "south": "lens"}}),
            ("dot", (7.9, 12.6, 9.2), (8.1, 12.8, 9.4), "dot", {}),
            ("mag", (7.25, 3.6, 7.2), (8.75, 8, 9.6), "mag", {"rot": ("x", 15, (8, 8, 8.4))}),
            ("grip", (7.25, 4, 11.2), (8.75, 8, 13), "poly", {"rot": ("x", -20, (8, 8, 12))}),
            ("trigger", (7.75, 7, 10), (8.25, 8, 10.5), "dark", {}),
            ("stock", (7.25, 7.6, 13), (8.75, 11, 19), "stock", {}),
            ("cheek", (7.4, 11, 14), (8.6, 11.6, 18), "stock", {}),
            ("pad", (7.1, 7.4, 19), (8.9, 11.2, 19.6), "pad", {}),
        ],
        "first_scale": 0.75, "third_scale": 0.8,
    },
    # Scavenged pump: wood pump and stock (one stock crack taped), under-tube, red shells on a side saddle.
    "shotgun": {
        "gui_tilt": 30,
        "tiles": {"barrel": ("plate", A.GUNMETAL, 3), "bore": ("bore", A.GUNMETAL, 4), "tube": ("plate", A.GUNMETAL, 2),
                  "pump": ("wood", A.WOOD, 3), "recv": ("scuffed", A.GUNMETAL, 3), "stock": ("wood", A.WOOD, 2),
                  "tape": ("tape", A.TAPE, 3), "shell": ("plate", A.PAINT_RED, 3), "brass": ("plate", A.BRASS, 4),
                  "dark": ("plate", A.POLYMER, 1)},
        "grip": (8, 6.5, 13.5),
        "parts": [
            ("barrel", (7.25, 9.5, -4), (8.75, 11, 8), "barrel", {"faces": {"north": "bore"}}),
            ("tube", (7.4, 8.3, -2.5), (8.6, 9.5, 8), "tube", {}),
            ("pump", (7, 7.8, -1), (9, 9.9, 4), "pump", {}),
            ("receiver", (7, 8, 8), (9, 11.4, 13), "recv", {}),
            ("bead", (7.75, 11, -3.6), (8.25, 11.5, -3.1), "brass", {}),
            ("saddle", (9, 9, 9), (9.4, 10.6, 12.4), "dark", {}),
            ("shell1", (9.1, 9.2, 9.2), (9.6, 10.4, 10), "shell", {"faces": {"east": "brass"}}),
            ("shell2", (9.1, 9.2, 10.4), (9.6, 10.4, 11.2), "shell", {"faces": {"east": "brass"}}),
            ("shell3", (9.1, 9.2, 11.6), (9.6, 10.4, 12.4), "shell", {"faces": {"east": "brass"}}),
            ("guard", (7.6, 6.8, 9.5), (8.4, 7.2, 12.6), "dark", {}),
            ("trigger", (7.75, 7.2, 11), (8.25, 8, 11.5), "dark", {}),
            ("wrist", (7.25, 6, 12.6), (8.75, 10.4, 15), "stock", {"rot": ("x", -20, (8, 9, 13))}),
            ("stock", (7.25, 6.6, 14.6), (8.75, 10.2, 21), "stock", {"rot": ("x", -6, (8, 9, 15))}),
            ("tape", (7.1, 6.4, 16.4), (8.9, 10.4, 17.6), "tape", {"rot": ("x", -6, (8, 9, 15))}),
        ],
        "first_scale": 0.7, "third_scale": 0.75,
    },
    # Long bolt-action: bull barrel, folded bipod, long scope with glass both ends, polymer chassis.
    "sniper_rifle": {
        "gui_tilt": 35,
        "tiles": {"barrel": ("plate", A.GUNMETAL, 3), "bore": ("bore", A.GUNMETAL, 3), "chassis": ("plate", A.WOOD, 2),
                  "recv": ("plate", A.GUNMETAL, 4), "scope": ("plate", A.POLYMER, 2), "bell": ("plate", A.POLYMER, 3),
                  "lens": ("lens", A.GLASS), "stock": ("grip", A.POLYMER, 3), "pad": ("plate", A.RUBBER, 2),
                  "bolt": ("plate", A.GUNMETAL, 6), "dark": ("plate", A.POLYMER, 1), "bipod": ("plate", A.GUNMETAL, 2)},
        "grip": (8, 6.5, 13),
        "parts": [
            ("barrel", (7.5, 9.5, -10), (8.5, 10.5, 2), "barrel", {"faces": {"north": "bore"}}),
            ("brake", (7.3, 9.3, -10.6), (8.7, 10.7, -9.4), "bipod", {"faces": {"north": "bore"}}),
            ("bipod", (7.6, 8.7, -3), (8.4, 9.3, 2.5), "bipod", {}),
            ("chassis", (7, 8, 2), (9, 10.6, 12), "chassis", {}),
            ("receiver", (7.25, 10, 5), (8.75, 11.5, 12), "recv", {}),
            ("scope", (7.4, 12, 4.5), (8.6, 13.2, 10.5), "scope", {}),
            ("bell_front", (7.1, 11.7, 2.8), (8.9, 13.5, 4.8), "bell", {"faces": {"north": "lens"}}),
            ("bell_rear", (7.2, 11.8, 10.3), (8.8, 13.4, 12), "bell", {"faces": {"south": "lens"}}),
            ("knob", (8.6, 12.2, 7), (9.2, 12.9, 7.8), "bell", {}),
            ("mount1", (7.75, 11.5, 5.5), (8.25, 12, 6.5), "dark", {}),
            ("mount2", (7.75, 11.5, 9), (8.25, 12, 10)),
            ("bolt", (8.75, 10.6, 10.6), (10.2, 11.1, 11.1), "bolt", {}),
            ("bolt_knob", (9.9, 10.4, 10.4), (10.6, 11.3, 11.3), "bolt", {}),
            ("mag", (7.5, 6.8, 7), (8.5, 8, 9.5), "dark", {}),
            ("trigger", (7.75, 7, 10.5), (8.25, 8, 11)),
            ("grip", (7.25, 4.6, 12), (8.75, 8.4, 14), "stock", {"rot": ("x", -22, (8, 8.4, 13))}),
            ("stock", (7.25, 6.4, 13.6), (8.75, 10.6, 22), "stock", {}),
            ("cheek", (7.4, 10.6, 15), (8.6, 11.4, 20), "stock", {}),
            ("pad", (7.1, 6.2, 22), (8.9, 10.8, 22.8), "pad", {}),
        ],
        "first_scale": 0.65, "third_scale": 0.72,
    },
    # ---- Exotics: the archetype's silhouette with its own trim so it reads as "that gun, but special" at a glance.
    # Stray Verdict: the hand cannon in blued steel with a brass cylinder and a bone grip.
    "stray_verdict": {
        "gui_tilt": 8,
        "tiles": {"barrel": ("plate", A.STEEL_BLUE, 2), "bore": ("bore", A.STEEL_BLUE, 2),
                  "cyl": ("flute", A.BRASS, 4), "frame": ("plate", A.STEEL_BLUE, 2), "wood": ("plate", A.BONE, 3),
                  "rib": ("plate", A.BRASS, 4), "brass": ("glow", A.GOLD, 5), "dark": ("plate", A.POLYMER, 1)},
        "grip": (8, 5.5, 13),
        "parts": [
            ("barrel", (7, 9, -1), (9, 11, 7.5), "barrel", {"faces": {"north": "bore"}}),
            ("rib", (7.4, 11, -1), (8.6, 11.6, 7.5), "rib", {}),
            ("ejector", (7.4, 8.2, 1.5), (8.6, 9, 7.5), "frame", {}),
            ("cylinder", (6.5, 7.8, 7.5), (9.5, 11.4, 11), "cyl", {"faces": {"north": "frame", "south": "frame"}}),
            ("frame", (7, 7.5, 11), (9, 11.6, 12.8), "frame", {}),
            ("topstrap", (7.2, 11.2, 7.3), (8.8, 11.8, 11.3), "frame", {}),
            ("hammer", (7.5, 11.4, 12.4), (8.5, 12.6, 13.4), "dark", {"rot": ("x", 20, (8, 11.5, 12.8))}),
            ("grip", (7.1, 2.6, 11.4), (8.9, 8.6, 14.2), "wood", {"rot": ("x", -25, (8, 8, 12.5))}),
            ("guard", (7.6, 6.2, 8.5), (8.4, 6.6, 11.6), "dark", {}),
            ("guard_front", (7.6, 6.6, 8.5), (8.4, 7.8, 8.9), "dark", {}),
            ("trigger", (7.75, 6.6, 10.2), (8.25, 7.8, 10.7), "dark", {}),
            ("front_sight", (7.75, 11.6, -0.6), (8.25, 12.6, 0.6), "brass", {}),
        ],
        "first_scale": 0.85, "third_scale": 0.85,
    },
    # Lantern Eye: the sniper with a glowing gold lens, a pale chassis and a lantern hung under the scope.
    "lantern_eye": {
        "gui_tilt": 35,
        "tiles": {"barrel": ("plate", A.GUNMETAL, 2), "bore": ("bore", A.GUNMETAL, 3), "chassis": ("plate", A.PALE, 2),
                  "recv": ("plate", A.GUNMETAL, 3), "scope": ("plate", A.BRASS, 3), "bell": ("plate", A.BRASS, 4),
                  "lens": ("glow", A.GOLD, 5), "stock": ("grip", A.PALE, 1), "pad": ("plate", A.RUBBER, 2),
                  "bolt": ("plate", A.BRASS, 5), "dark": ("plate", A.POLYMER, 1), "bipod": ("plate", A.GUNMETAL, 2)},
        "grip": (8, 6.5, 13),
        "parts": [
            ("barrel", (7.5, 9.5, -10), (8.5, 10.5, 2), "barrel", {"faces": {"north": "bore"}}),
            ("brake", (7.3, 9.3, -10.6), (8.7, 10.7, -9.4), "bipod", {"faces": {"north": "bore"}}),
            ("bipod", (7.6, 8.7, -3), (8.4, 9.3, 2.5), "bipod", {}),
            ("chassis", (7, 8, 2), (9, 10.6, 12), "chassis", {}),
            ("receiver", (7.25, 10, 5), (8.75, 11.5, 12), "recv", {}),
            ("scope", (7.4, 12, 4.5), (8.6, 13.2, 10.5), "scope", {}),
            ("bell_front", (7.1, 11.7, 2.8), (8.9, 13.5, 4.8), "bell", {"faces": {"north": "lens"}}),
            ("bell_rear", (7.2, 11.8, 10.3), (8.8, 13.4, 12), "bell", {"faces": {"south": "lens"}}),
            ("lantern", (7.6, 7.2, 3.2), (8.4, 8.0, 4.0), "lens", {}),
            ("knob", (8.6, 12.2, 7), (9.2, 12.9, 7.8), "bell", {}),
            ("mount1", (7.75, 11.5, 5.5), (8.25, 12, 6.5), "dark", {}),
            ("mount2", (7.75, 11.5, 9), (8.25, 12, 10)),
            ("bolt", (8.75, 10.6, 10.6), (10.2, 11.1, 11.1), "bolt", {}),
            ("bolt_knob", (9.9, 10.4, 10.4), (10.6, 11.3, 11.3), "bolt", {}),
            ("mag", (7.5, 6.8, 7), (8.5, 8, 9.5), "dark", {}),
            ("trigger", (7.75, 7, 10.5), (8.25, 8, 11)),
            ("grip", (7.25, 4.6, 12), (8.75, 8.4, 14), "stock", {"rot": ("x", -22, (8, 8.4, 13))}),
            ("stock", (7.25, 6.4, 13.6), (8.75, 10.6, 22), "stock", {}),
            ("cheek", (7.4, 10.6, 15), (8.6, 11.4, 20), "stock", {}),
            ("pad", (7.1, 6.2, 22), (8.9, 10.8, 22.8), "pad", {}),
        ],
        "first_scale": 0.65, "third_scale": 0.72,
    },
    # Backdraft: the shotgun with a scorched barrel, hazard-striped pump and a vented muzzle brake.
    "backdraft": {
        "gui_tilt": 30,
        "tiles": {"barrel": ("scuffed", A.EMBER, 1), "bore": ("bore", A.GUNMETAL, 4), "tube": ("plate", A.GUNMETAL, 2),
                  "pump": ("plate", A.HAZARD, 3), "recv": ("scuffed", A.GUNMETAL, 3), "stock": ("plate", A.RUBBER, 2),
                  "vent": ("vent", A.EMBER, 2), "shell": ("plate", A.PAINT_RED, 3), "brass": ("plate", A.BRASS, 4),
                  "dark": ("plate", A.POLYMER, 1)},
        "grip": (8, 6.5, 13.5),
        "parts": [
            ("brake", (7, 9.25, -5.5), (9, 11.25, -4), "vent", {"faces": {"north": "bore"}}),
            ("barrel", (7.25, 9.5, -4), (8.75, 11, 8), "barrel", {"faces": {"north": "bore"}}),
            ("tube", (7.4, 8.3, -2.5), (8.6, 9.5, 8), "tube", {}),
            ("pump", (7, 7.8, -1), (9, 9.9, 4), "pump", {}),
            ("receiver", (7, 8, 8), (9, 11.4, 13), "recv", {}),
            ("bead", (7.75, 11, -3.6), (8.25, 11.5, -3.1), "brass", {}),
            ("saddle", (9, 9, 9), (9.4, 10.6, 12.4), "dark", {}),
            ("shell1", (9.1, 9.2, 9.2), (9.6, 10.4, 10), "shell", {"faces": {"east": "brass"}}),
            ("shell2", (9.1, 9.2, 10.4), (9.6, 10.4, 11.2), "shell", {"faces": {"east": "brass"}}),
            ("shell3", (9.1, 9.2, 11.6), (9.6, 10.4, 12.4), "shell", {"faces": {"east": "brass"}}),
            ("guard", (7.6, 6.8, 9.5), (8.4, 7.2, 12.6), "dark", {}),
            ("trigger", (7.75, 7.2, 11), (8.25, 8, 11.5), "dark", {}),
            ("wrist", (7.25, 6, 12.6), (8.75, 10.4, 15), "stock", {"rot": ("x", -20, (8, 9, 13))}),
            ("stock", (7.25, 6.6, 14.6), (8.75, 10.2, 21), "stock", {"rot": ("x", -6, (8, 9, 15))}),
        ],
        "first_scale": 0.7, "third_scale": 0.75,
    },
    # Kindling Engine: the auto rifle with an ember-glow handguard, red-hot barrel and a heat gauge on the receiver.
    "kindling_engine": {
        "gui_tilt": 30,
        "tiles": {"barrel": ("scuffed", A.EMBER, 2), "bore": ("bore", A.GUNMETAL, 3), "guard": ("vent", A.RUST, 3),
                  "recv": ("plate", A.GUNMETAL, 4), "rail": ("flute", A.GUNMETAL, 3), "poly": ("grip", A.POLYMER, 3),
                  "stock": ("plate", A.POLYMER, 3), "lens": ("lens", A.GLASS), "dot": ("glow", A.EMBER, 4),
                  "pad": ("plate", A.RUBBER, 2), "mag": ("plate", A.RUST, 2), "dark": ("plate", A.POLYMER, 1),
                  "gauge": ("glow", A.EMBER, 3)},
        "grip": (8, 6, 12),
        "parts": [
            ("brake", (7.25, 9.25, -7), (8.75, 10.75, -5.5), "barrel", {"faces": {"north": "bore"}}),
            ("barrel", (7.5, 9.5, -5.5), (8.5, 10.5, 0), "barrel", {}),
            ("guard", (7, 8.5, 0), (9, 11.5, 6), "guard", {}),
            ("receiver", (7, 8, 6), (9, 11.5, 13), "recv", {}),
            ("gauge", (9, 9, 9.5), (9.3, 10.5, 11.5), "gauge", {}),
            ("rail", (7.5, 11.5, 1), (8.5, 12, 12.5), "rail", {}),
            ("sight", (7.25, 12, 8), (8.75, 13.6, 10.8), "dark", {"faces": {"north": "lens", "south": "lens"}}),
            ("dot", (7.9, 12.6, 9.2), (8.1, 12.8, 9.4), "dot", {}),
            ("mag", (7.25, 3.6, 7.2), (8.75, 8, 9.6), "mag", {"rot": ("x", 15, (8, 8, 8.4))}),
            ("grip", (7.25, 4, 11.2), (8.75, 8, 13), "poly", {"rot": ("x", -20, (8, 8, 12))}),
            ("trigger", (7.75, 7, 10), (8.25, 8, 10.5), "dark", {}),
            ("stock", (7.25, 7.6, 13), (8.75, 11, 19), "stock", {}),
            ("cheek", (7.4, 11, 14), (8.6, 11.6, 18), "stock", {}),
            ("pad", (7.1, 7.4, 19), (8.9, 11.2, 19.6), "pad", {}),
        ],
        "first_scale": 0.75, "third_scale": 0.8,
    },
    # Tether Lance: a shoulder launcher — a fat tube with a web-spool canister underneath and a pale rocket in the mouth.
    "tether_lance": {
        "gui_tilt": 30,
        "tiles": {"tube": ("plate", A.GUNMETAL, 3), "bore": ("bore", A.GUNMETAL, 2), "band": ("plate", A.HAZARD, 3),
                  "spool": ("plate", A.BONE, 3), "silk": ("tape", A.BONE, 4), "rocket": ("plate", A.PALE, 3),
                  "tip": ("plate", A.PAINT_RED, 3), "grip": ("grip", A.POLYMER, 3), "dark": ("plate", A.POLYMER, 1),
                  "pad": ("plate", A.RUBBER, 2)},
        "grip": (8, 6, 11),
        "parts": [
            ("tube", (6.5, 8.5, -6), (9.5, 11.5, 14), "tube", {"faces": {"north": "bore", "south": "bore"}}),
            ("band_front", (6.3, 8.3, -5), (9.7, 11.7, -3.5), "band", {}),
            ("band_rear", (6.3, 8.3, 11), (9.7, 11.7, 12.5), "band", {}),
            ("rocket", (7.3, 9.3, -8), (8.7, 10.7, -5.5), "rocket", {}),
            ("tip", (7.6, 9.6, -9), (8.4, 10.4, -8), "tip", {}),
            ("spool", (7, 6.5, 2), (9, 8.5, 7), "spool", {}),
            ("silk", (7.2, 6.7, 3), (8.8, 8.3, 6), "silk", {}),
            ("sight", (7.75, 11.5, 2), (8.25, 12.6, 2.5), "dark", {}),
            ("rear_sight", (7.4, 11.5, 9), (8.6, 12.4, 9.4), "dark", {}),
            ("grip", (7.25, 4.5, 9.5), (8.75, 8.5, 11.5), "grip", {"rot": ("x", -15, (8, 8.5, 10.5))}),
            ("trigger", (7.75, 7.5, 8.3), (8.25, 8.5, 8.8), "dark", {}),
            ("foregrip", (7.4, 6.5, -1), (8.6, 8.5, 0.5), "grip", {}),
            ("pad", (6.8, 8.3, 14), (9.2, 11.7, 14.6), "pad", {}),
        ],
        "first_scale": 0.65, "third_scale": 0.7,
    },
}

FACES = ("north", "south", "east", "west", "up", "down")


def parts(gun):
    for p in GUNS[gun]["parts"]:
        if len(p) == 3:  # (name, from, to): default dark tile
            p = p + ("dark", {})
        yield p


def atlas(gun):
    spec = GUNS[gun]["tiles"]
    names = list(spec)
    assert len(names) <= 16, gun
    img = Image.new("RGBA", (32, 32), A.TRANSPARENT)
    for k, name in enumerate(names):
        kind, r, *base = spec[name]
        img.paste(tile(kind, r, base[0] if base else None, f"{gun}:{name}"), ((k % 4) * 8, (k // 4) * 8))
    return img, {n: k for k, n in enumerate(names)}


def uv(k):
    col, row = k % 4, k // 4
    return [col * 4, row * 4, col * 4 + 4, row * 4 + 4]


def elements(gun, index):
    out = []
    for name, frm, to, t, opts in parts(gun):
        faces = {}
        for f in FACES:
            tn = opts.get("faces", {}).get(f, t)
            faces[f] = {"uv": uv(index[tn]), "texture": "#gun"}
        el = {"name": name, "from": list(frm), "to": list(to), "faces": faces}
        if "rot" in opts:
            axis, angle, origin = opts["rot"]
            el["rotation"] = {"axis": axis, "angle": angle, "origin": list(origin)}
        out.append(el)
    return out


# ------------------------------------------------------------------------------------------------- display math

D = math.radians


def rx(a):
    a = D(a)
    return np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])


def ry(a):
    a = D(a)
    return np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])


def rz(a):
    a = D(a)
    return np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])


def rot_xyz(r):
    """Same convention as ItemTransform: Quaternionf.rotationXYZ = Rx * Ry * Rz."""
    return rx(r[0]) @ ry(r[1]) @ rz(r[2])


def apply(tf, p):
    """ItemTransform.apply on a model point p (pixels): translate(t) rotate scale translate(-0.5)."""
    rot, trans, scale = tf
    q = (np.asarray(p, float) / 16 - 0.5) * scale
    return rot_xyz(rot) @ q + np.asarray(trans) / 16


# Vanilla item/handheld (the sword pose) and where its handle sits in the 16x16 texture: pixel (2.5, 2.5) of the
# south face. A gun's grip goes exactly there, so it sits in the hand the way a sword's hilt does.
HANDHELD = {
    "thirdperson_righthand": ([0, -90, 55], [0, 4.0, 0.5], 0.85),
    "firstperson_righthand": ([0, -90, 25], [1.13, 3.2, 1.13], 0.68),
}
SWORD_HANDLE = (2.5, 2.5, 8)


def hand_transform(slot, rotation, scale, grip, nudge=(0, 0, 0)):
    vr, vt, vs = HANDHELD[slot]
    target = apply((vr, vt, vs), SWORD_HANDLE)
    q = (np.asarray(grip, float) / 16 - 0.5) * scale
    t = (target - rot_xyz(rotation) @ q) * 16 + np.asarray(nudge)
    return rotation, t, scale


def bounds(gun):
    pts = []
    for name, frm, to, t, opts in parts(gun):
        for x in (frm[0], to[0]):
            for y in (frm[1], to[1]):
                for z in (frm[2], to[2]):
                    p = np.array([x, y, z], float)
                    if "rot" in opts:
                        axis, angle, origin = opts["rot"]
                        m = {"x": rx, "y": ry, "z": rz}[axis](angle)
                        p = m @ (p - origin) + origin
                    pts.append(p)
    pts = np.array(pts)
    return pts.min(0), pts.max(0)


def fit(gun, rotation, size):
    """Scale + translation so the rotated model's screen-space bounds fill `size` px, centred."""
    lo, hi = bounds(gun)
    corners = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    proj = np.array([rot_xyz(rotation) @ (c / 16 - 0.5) for c in corners])
    ext = (proj.max(0) - proj.min(0))[:2]
    scale = min(size / 16 / ext[0], size / 16 / ext[1], 1.6)
    centre = (proj.max(0) + proj.min(0)) / 2 * scale
    return rotation, -centre * 16, scale


def euler_xyz(m):
    """Inverse of rot_xyz: angles (deg) with Rx(a) Ry(b) Rz(c) == m."""
    b = math.asin(max(-1.0, min(1.0, m[0][2])))
    a = math.atan2(-m[1][2], m[2][2])
    c = math.atan2(-m[0][1], m[0][0])
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def gui_rot(tilt):
    """GUI pose: profile with the muzzle to the right, top tipped toward the viewer a little, then the whole
    gun turned `tilt` degrees on screen (long guns go diagonal like vanilla tools so they fill the slot)."""
    return euler_xyz(rz(tilt) @ rx(14) @ ry(-100))


def r3(v):
    return [round(float(x), 3) for x in v]


def displays(gun):
    g = GUNS[gun]
    grip = g["grip"]
    fs, ts = g["first_scale"], g["third_scale"]
    out = {}
    # Third person: barrel forward, top up, from the arm-down hand frame (ItemInHandLayer: Rx(-90) Ry(180)).
    r, t, s = hand_transform("thirdperson_righthand", [90, 0, 0], ts, grip)
    out["thirdperson_righthand"] = {"rotation": r3(r), "translation": r3(t), "scale": r3([s] * 3)}
    # First person: camera looks down -z, so no yaw keeps the barrel forward; a little inward yaw toward the crosshair.
    r, t, s = hand_transform("firstperson_righthand", [0, 4, 0], fs, grip, nudge=(-1.0, 0.5, 0))
    out["firstperson_righthand"] = {"rotation": r3(r), "translation": r3(t), "scale": r3([s] * 3)}
    for side in ("thirdperson", "firstperson"):
        out[f"{side}_lefthand"] = dict(out[f"{side}_righthand"])  # ItemTransform mirrors these itself
    r, t, s = fit(gun, gui_rot(g.get("gui_tilt", 0)), 15.5)
    out["gui"] = {"rotation": r3(r), "translation": r3(t), "scale": r3([s] * 3)}
    r, t, s = fit(gun, [0, -90, 0], 8.0)
    out["ground"] = {"rotation": r3(r), "translation": r3(t), "scale": r3([s] * 3)}
    # Item frames look at the model from -z (vanilla generated uses fixed = [0, 180, 0]); same profile as the GUI.
    r, t, s = fit(gun, euler_xyz(ry(180) @ rot_xyz(gui_rot(g.get("gui_tilt", 0)))), 14.0)
    out["fixed"] = {"rotation": r3(r), "translation": r3(t), "scale": r3([s] * 3)}
    r, t, s = fit(gun, [0, -90, 0], 10.0)
    out["head"] = {"rotation": r3(r), "translation": r3(t + np.array([0, 10, 0])), "scale": r3([s] * 3)}
    return out


def model(gun, index):
    return {
        "gui_light": "front",
        "textures": {"gun": f"blocksouls_guns:item/{gun}", "particle": f"blocksouls_guns:item/{gun}"},
        "elements": elements(gun, index),
        "display": displays(gun),
    }


# ------------------------------------------------------------------------------------------ preview (GUI) render

CORNERS = {  # face -> corner order TL, TR, BR, BL seen from outside, as (x, y, z) picks of (0=from, 1=to)
    "north": [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    "south": [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    "east": [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    "west": [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    "up": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    "down": [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def render_model(mdl, tex, transform, px=256):
    """Orthographic render of a cuboid item model the way the GUI draws it (camera at +z, y up)."""
    rot, trans, scale = transform
    R = rot_xyz(rot)
    light = np.array([-0.3, 0.6, 1.0])
    light /= np.linalg.norm(light)
    quads = []
    tw, th = tex.size
    for el in mdl["elements"]:
        frm, to = np.array(el["from"], float), np.array(el["to"], float)
        er = np.eye(3)
        origin = np.zeros(3)
        if "rotation" in el:
            rr = el["rotation"]
            er = {"x": rx, "y": ry, "z": rz}[rr["axis"]](rr["angle"])
            origin = np.array(rr["origin"], float)
        for face, spec in el["faces"].items():
            cs = []
            for pick in CORNERS[face]:
                p = np.array([to[i] if pick[i] else frm[i] for i in range(3)])
                p = er @ (p - origin) + origin
                cs.append(R @ ((p / 16 - 0.5) * scale) + np.asarray(trans) / 16)
            n = R @ er @ np.array(NORMALS[face], float)
            if n[2] <= 1e-6:
                continue  # back-facing
            shade = 0.55 + 0.45 * max(0.0, float(n @ light))
            u0, v0, u1, v1 = [c * tw / 16 for c in spec["uv"]]
            quads.append((sum(c[2] for c in cs) / 4, cs, (u0, v0, u1, v1), shade))
    quads.sort(key=lambda q: q[0])
    img = Image.new("RGBA", (px, px), A.TRANSPARENT)
    dr = ImageDraw.Draw(img)

    def scr(p):
        return ((p[0] + 0.5) * px, (0.5 - p[1]) * px)

    for _, cs, (u0, v0, u1, v1), shade in quads:
        nu, nv = max(1, round(abs(u1 - u0))), max(1, round(abs(v1 - v0)))
        tl, tr, br, bl = [np.array(c) for c in cs]
        for j in range(nv):
            for i in range(nu):
                col = tex.getpixel((min(tw - 1, int(min(u0, u1) + i)), min(th - 1, int(min(v0, v1) + j))))
                if col[3] == 0:
                    continue

                def at(a, b):
                    top = tl + (tr - tl) * a
                    bot = bl + (br - bl) * a
                    return scr(top + (bot - top) * b)

                a0, a1, b0, b1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv
                poly = [at(a0, b0), at(a1, b0), at(a1, b1), at(a0, b1)]
                c = tuple(int(col[k] * shade) for k in range(3)) + (255,)
                dr.polygon(poly, fill=c, outline=c)
    return img


# --------------------------------------------------------------------------------------------------------- build

PREVIEWS = {}


def build():
    out = {}
    for gun in GUNS:
        tex, index = atlas(gun)
        mdl = model(gun, index)
        out[f"{ASSETS}/textures/item/{gun}.png"] = tex
        path = f"{ASSETS}/models/item/{gun}.json"
        with open(path, "w") as f:
            json.dump(mdl, f, indent=2)
            f.write("\n")
        gui = mdl["display"]["gui"]
        PREVIEWS[gun] = render_model(mdl, tex, (gui["rotation"], gui["translation"], gui["scale"][0]))
    return out
