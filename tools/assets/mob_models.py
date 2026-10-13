#!/usr/bin/env python3
"""The mobs module's model geometry: the single source of truth for every mob's parts, cubes and UV layout.

    python3 tools/assets/mob_models.py     # rewrites mobs/src/client/.../model/MobGeometry.java

Each mob is a tree of parts (Minecraft model space: pixels, y points down, the feet stand on y = 24, the mob
faces -z, its right side is -x). Each part has a pivot, a rest rotation in radians, and cubes. UV offsets are
packed automatically, so cubes can be added or resized freely; `mobs.py` paints textures from the same layout
and `mob_preview.py` renders it, so the Java model, the texture and the screenshots can't drift apart.

Cube ids (the first argument of `C`) name the cube for the painter; they never reach Java.
"""

import os
from dataclasses import dataclass, field

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA_OUT = os.path.join(HERE, "..", "..", "mobs", "src", "client", "java", "io", "github", "tmldude", "blocksouls", "mobs",
                        "client", "model", "MobGeometry.java")


@dataclass
class C:
    """A cube: id, origin (x, y, z) relative to the part pivot, size (w, h, d), optional inflation and mirroring.

    `uv` names an earlier cube of the same size whose texture area this one reuses (mirrored limbs)."""
    id: str
    origin: tuple
    size: tuple
    grow: float = 0.0
    mirror: bool = False
    uv: str = None
    u: int = 0
    v: int = 0

    def footprint(self):
        w, h, d = self.size
        return 2 * (d + w), d + h


@dataclass
class P:
    name: str
    parent: str
    pivot: tuple = (0, 0, 0)
    rot: tuple = (0, 0, 0)
    cubes: list = field(default_factory=list)


@dataclass
class Model:
    name: str
    tex: tuple
    parts: list

    def part(self, name):
        return next(p for p in self.parts if p.name == name)

    def cube(self, cube_id):
        for p in self.parts:
            for c in p.cubes:
                if c.id == cube_id:
                    return c
        raise KeyError(cube_id)

    def cubes(self):
        return [c for p in self.parts for c in p.cubes]


def limb_pair(prefix, side_x, y, z, parent, upper_rot, lower_rot, upper, lower, lower_pivot, yaw):
    """A two-segment limb on each side, left mirroring right. Returns the parts, right side first."""
    parts = []
    for side, sx in (("right", -1), ("left", 1)):
        name = f"{side}_{prefix}"
        flip = -1 if side == "left" else 1
        parts.append(P(name, parent, (sx * side_x, y, z), (0, yaw * flip, 0)))
        parts.append(P(f"{name}_upper", name, (0, 0, 0), (upper_rot[0], upper_rot[1] * flip, upper_rot[2] * flip),
                       [C(f"{name}_upper", (upper[0][0], upper[0][1], upper[0][2]), upper[1], mirror=side == "left")]))
        parts.append(P(f"{name}_lower", f"{name}_upper", lower_pivot, (lower_rot[0], lower_rot[1] * flip, lower_rot[2] * flip),
                       [C(f"{name}_lower", (lower[0][0], lower[0][1], lower[0][2]), lower[1], mirror=side == "left")]))
    return parts


# --- The mobs -----------------------------------------------------------------------------------------------------

def shambler():
    return Model("shambler", (64, 64), [
        # Hips pivot: the hunch tips the whole upper body forward and a little toward the dragging arm.
        P("body", "root", (0, 12, 0), (0.42, 0, 0.07), [
            C("torso", (-4, -12, -2), (8, 12, 4)),
            C("shirt", (-4, -12, -2), (8, 12, 4), grow=0.3),
        ]),
        P("head", "body", (0, -12, -1), (-0.25, 0, 0.32), [C("skull", (-4, -7, -4), (8, 7, 8))]),
        P("jaw", "head", (0, 0, 2), (0.32, 0, 0), [C("jaw", (-3.5, -0.5, -6), (7, 2, 6))]),
        # The long right arm hangs straight down and its knuckles drag on the ground.
        P("right_arm", "body", (-5.5, -10, 0), (-0.32, 0, 0.12), [C("right_upper", (-2, -1.5, -2), (4, 10, 4))]),
        P("right_forearm", "right_arm", (0, 8, 0), (0.2, 0, -0.05), [C("right_forearm", (-1.5, 0, -1.5), (3, 9, 3))]),
        P("right_hand", "right_forearm", (0, 8.5, 0), (0.55, 0, 0), [C("right_hand", (-2, 0, -2), (4, 3, 4))]),
        # The withered left arm reaches for you.
        P("left_arm", "body", (5, -10, 0), (-1.2, 0, -0.1), [C("left_arm", (-1, -1.5, -1.5), (3, 12, 3))]),
        P("right_leg", "root", (-2, 12, 0), (0, 0, 0), [
            C("right_leg", (-2, 0, -2), (4, 12, 4)),
            C("right_pants", (-2, 0, -2), (4, 12, 4), grow=0.25),
        ]),
        P("left_leg", "root", (2, 12, 0), (0, 0, 0), [C("left_leg", (-1.5, 0, -1.5), (3, 12, 3))]),
    ])


def crawler():
    parts = [
        P("body", "root", (0, 15, 0), (0.06, 0, 0), [
            C("torso", (-4, -2.5, -7), (8, 5, 12)),
            C("hips", (-3, -2, 5), (6, 4, 4)),
            C("spine1", (-1, -4.5, -5), (2, 2, 3)),
            C("spine2", (-1, -4.5, -1), (2, 2, 3)),
            C("spine3", (-1, -4, 3), (2, 2, 3)),
        ]),
        P("head", "root", (0, 15, -7), (0.15, 0, 0), [C("skull", (-3, -3.5, -5), (6, 5, 5))]),
        P("jaw", "head", (0, 1, -1), (0.35, 0, 0), [C("jaw", (-2.5, 0, -4.5), (5, 2, 5))]),
    ]
    # Six limbs, elbows high like a spider's: the limb root yaws (fans the limb forward or back), the upper
    # segment tilts up and out, the lower one comes back down to the ground.
    for prefix, z, yaw in (("front", -5, -0.7), ("mid", 0, 0.05), ("hind", 5, 0.7)):
        thin = prefix == "mid"
        upper = ((-1, -7, -1), (2, 7, 2)) if not thin else ((-0.5, -6, -0.5), (1, 6, 1))
        lower = ((-1, 0, -1), (2, 15, 2)) if not thin else ((-0.5, 0, -0.5), (1, 14, 1))
        parts += limb_pair(prefix, 3.5, 14, z, "body", (0, 0, -0.85), (0, 0, 1.08), upper, lower,
                           (0, -7 if not thin else -6, 0), yaw)
    for p in parts:
        # Limbs hang off the body part, whose pivot is at y = 15: shift them into its space.
        if p.parent == "body" and p.name != "body":
            x, y, z = p.pivot
            p.pivot = (x, y - 15, z)
    return Model("crawler", (64, 64), parts)


def screamer():
    return Model("screamer", (64, 64), [
        P("right_leg", "root", (-1.8, 10, 0), (0, 0, 0), [
            C("right_leg", (-1, 0, -1), (2, 14, 2)),
            C("right_knee", (-1.5, 5, -1.7), (3, 2, 3)),
        ]),
        P("left_leg", "root", (1.8, 10, 0), (0, 0, 0), [
            C("left_leg", (-1, 0, -1), (2, 14, 2), mirror=True),
            C("left_knee", (-1.5, 5, -1.7), (3, 2, 3), mirror=True),
        ]),
        # Wasp waist: pelvis, a bare spine, then the ribcage.
        P("body", "root", (0, 10, 0), (0.16, 0, 0), [
            C("pelvis", (-3, -2, -1.5), (6, 2, 3)),
            C("spine", (-1, -5, -0.5), (2, 3, 2)),
            C("ribs", (-3.5, -12, -2), (7, 7, 4)),
            C("neck", (-1, -14, -1), (2, 2, 2)),
        ]),
        P("head", "body", (0, -13.5, -0.5), (0, 0, 0), [
            C("skull", (-3, -6, -3.5), (6, 5, 6)),
            C("teeth", (-2.5, -1, -3.5), (5, 1, 1)),
        ]),
        # The distended jaw: a long lower jaw with a hanging chin, hinged at the back of the skull.
        P("jaw", "head", (0, -1, 2), (0.12, 0, 0), [
            C("jaw", (-2.5, 0, -6), (5, 2, 7)),
            C("chin", (-2, 1.5, -6), (4, 4, 2)),
        ]),
        P("right_arm", "body", (-4.5, -11, 0), (0, 0, 0.1), [C("right_upper", (-1, -1, -1), (2, 10, 2))]),
        P("right_forearm", "right_arm", (0, 9, 0), (-0.1, 0, 0), [C("right_forearm", (-1, 0, -1), (2, 9, 2))]),
        P("right_hand", "right_forearm", (0, 8.5, 0), (0, 0, 0), [
            C("right_finger1", (-1, 0, -1), (1, 5, 1)),
            C("right_finger2", (0.2, 0, -0.6), (1, 4, 1)),
        ]),
        P("left_arm", "body", (4.5, -11, 0), (0, 0, -0.1), [C("left_upper", (-1, -1, -1), (2, 10, 2), mirror=True)]),
        P("left_forearm", "left_arm", (0, 9, 0), (-0.1, 0, 0), [C("left_forearm", (-1, 0, -1), (2, 9, 2), mirror=True)]),
        P("left_hand", "left_forearm", (0, 8.5, 0), (0, 0, 0), [
            C("left_finger1", (0, 0, -1), (1, 5, 1), mirror=True),
            C("left_finger2", (-1.2, 0, -0.6), (1, 4, 1), mirror=True),
        ]),
    ])


BLOATER_SACS = [
    # name, pivot on the belly, cube origin, size
    ("sac_front", (3, 1, -9), (-3, -3, -2), (6, 6, 4)),
    ("sac_low", (-4, 6, -9), (-2, -2, -1.5), (4, 4, 3)),
    ("sac_right", (-9, -1, 1), (-2, -3.5, -3.5), (4, 7, 7)),
    ("sac_left", (9, 4, -3), (-2, -2.5, -2.5), (4, 5, 5)),
    ("sac_top", (-3, -9, 2), (-3, -2, -3), (6, 4, 6)),
    ("sac_back", (2, -2, 9), (-4, -4, -2), (8, 8, 4)),
]


def bloater():
    parts = [
        # Pivot at the belly's center so it swells evenly.
        P("belly", "root", (0, 11, 0), (0, 0, 0), [
            C("belly", (-9, -9, -9), (18, 18, 18)),
            # A wider band around the middle rounds the sack out.
            C("girth", (-10, -6, -10), (20, 12, 20)),
        ]),
    ]
    for name, pivot, origin, size in BLOATER_SACS:
        parts.append(P(name, "belly", pivot, (0, 0, 0), [C(name, origin, size)]))
    parts += [
        P("head", "root", (0, 3, -4), (0, 0, 0), [C("head", (-3, -5, -3), (6, 5, 6))]),
        P("right_leg", "root", (-4.5, 19, 0), (0, 0, 0), [C("right_leg", (-2, 0, -2), (4, 5, 4))]),
        P("left_leg", "root", (4.5, 19, 0), (0, 0, 0), [C("left_leg", (-2, 0, -2), (4, 5, 4), mirror=True)]),
        P("right_arm", "root", (-9.5, 6, -3), (0, 0, 0.35), [C("right_arm", (-1.5, 0, -1.5), (3, 7, 3))]),
        P("left_arm", "root", (9.5, 6, -3), (0, 0, -0.35), [C("left_arm", (-1.5, 0, -1.5), (3, 7, 3), mirror=True)]),
    ]
    return Model("bloater", (128, 128), parts)


def brute_parts():
    return [
        P("right_leg", "root", (-4.5, 12, 1), (0, 0, 0), [C("right_leg", (-3.5, 0, -3.5), (7, 12, 7))]),
        P("left_leg", "root", (4.5, 12, 1), (0, 0, 0), [C("left_leg", (-3.5, 0, -3.5), (7, 12, 7), mirror=True, uv="right_leg")]),
        # Hunched: the chest leans over the hips, the head hangs low between the shoulders.
        P("body", "root", (0, 12, 1), (0.42, 0, 0), [
            C("waist", (-6, -6, -4.5), (12, 6, 9)),
            C("chest", (-9, -18, -6), (18, 12, 12)),
            C("hump", (-7, -21, -3), (14, 4, 9)),
        ]),
        P("head", "body", (0, -15, -6), (-0.42, 0, 0), [
            C("head", (-2.5, -4.5, -4), (5, 5, 5)),
            C("underbite", (-3, -0.5, -4.6), (6, 2, 4)),
        ]),
        P("right_arm", "body", (-11, -15, -1), (-0.62, 0, 0.12), [
            C("right_shoulder", (-5, -5, -5), (9, 8, 10)),
            C("right_upper", (-3, 2, -3), (6, 9, 6)),
        ]),
        P("right_forearm", "right_arm", (0, 10.5, 0), (0.12, 0, 0), [
            C("right_forearm", (-3.5, 0, -3.5), (7, 9, 7)),
            C("right_fist", (-4, 8, -4), (8, 6, 8)),
        ]),
        P("left_arm", "body", (11, -15, -1), (-0.62, 0, -0.12), [
            C("left_shoulder", (-4, -5, -5), (9, 8, 10), mirror=True, uv="right_shoulder"),
            C("left_upper", (-3, 2, -3), (6, 9, 6), mirror=True, uv="right_upper"),
        ]),
        P("left_forearm", "left_arm", (0, 10.5, 0), (0.12, 0, 0), [
            C("left_forearm", (-3.5, 0, -3.5), (7, 9, 7), mirror=True, uv="right_forearm"),
            C("left_fist", (-4, 8, -4), (8, 6, 8), mirror=True, uv="right_fist"),
        ]),
    ]


def brute():
    return Model("brute", (128, 64), brute_parts())


def mutant_brute():
    parts = brute_parts()
    # No shared UVs: the mutation is asymmetric, so each side gets its own veins.
    for p in parts:
        for c in p.cubes:
            c.uv = None
    parts += [
        # Bony spines erupting along the hump and back.
        P("spines", "body", (0, -19, 1), (0, 0, 0), [
            C("spine1", (-1, -6, -1), (2, 6, 2)),
            C("spine2", (-5, -4, 1), (2, 5, 2)),
            C("spine3", (3, -5, 0), (2, 6, 2)),
            C("spine4", (-1, -3, 5), (2, 4, 2)),
        ]),
        # A tumorous growth bursting from the right shoulder, and crystal-like nubs on the left forearm.
        P("growth", "right_arm", (-2, -5, 1), (0.3, 0.4, -0.2), [C("growth", (-4, -4, -4), (7, 6, 7))]),
        P("nubs", "left_forearm", (2.5, 3, 0), (0, 0, -0.3), [
            C("nub1", (0, -1, -2), (3, 2, 2)),
            C("nub2", (0, 2, 1), (2, 2, 2)),
        ]),
    ]
    return Model("mutant_brute", (128, 128), parts)


def infected_piglin():
    # HumanoidModel needs head (with a hat child), body, arms and legs at the vanilla pivots.
    return Model("infected_piglin", (128, 64), [
        P("head", "root", (0, 0, 0), (0, 0, 0), [
            C("head", (-5, -8, -4), (10, 8, 8)),
            C("snout", (-2, -4, -5), (4, 4, 1)),
            C("tusk_right", (-3, -2, -5), (1, 2, 1)),
            C("tusk_left", (2, -2, -5), (1, 2, 1)),
            # The growth swallowing the left eye and brow.
            C("eye_growth", (1, -8, -5), (4, 4, 3)),
        ]),
        P("hat", "head", (0, 0, 0), (0, 0, 0), [C("hat", (-5, -8, -4), (10, 8, 8), grow=0.4)]),
        P("right_ear", "head", (-4.5, -6, 0), (0, 0, 0.5236), [C("right_ear", (-1, 0, -2), (1, 5, 4))]),
        # The left ear has fused into a swollen lump.
        P("left_ear", "head", (4.5, -6, 0), (0, 0, -0.35), [C("left_ear", (0, -0.5, -2.5), (2, 4, 5))]),
        P("head_growth", "head", (-1, -8, -1), (0.2, 0, 0.3), [
            C("head_growth1", (-2, -3, -2), (4, 3, 5)),
            C("head_growth2", (1, -2, 0), (3, 2, 3)),
        ]),
        P("body", "root", (0, 0, 0), (0, 0, 0), [
            C("body", (-4, 0, -2), (8, 12, 4)),
            C("jacket", (-4, 0, -2), (8, 12, 4), grow=0.25),
        ]),
        P("back_growth", "body", (0, 4, 2), (0, 0, 0.15), [
            C("back_growth1", (-3, -3, 0), (6, 6, 3)),
            C("back_growth2", (0, 2, 1), (4, 4, 2)),
        ]),
        P("right_arm", "root", (-5, 2, 0), (0, 0, 0), [C("right_arm", (-3, -2, -2), (4, 12, 4))]),
        # The infected arm: swollen, the growth climbing it.
        P("left_arm", "root", (5, 2, 0), (0, 0, 0), [C("left_arm", (-1, -2, -2.5), (5, 12, 5))]),
        P("arm_growth", "left_arm", (2.5, 1, 0), (0, 0, 0), [C("arm_growth", (-0.5, -2, -2.5), (3, 5, 4))]),
        P("right_leg", "root", (-1.9, 12, 0), (0, 0, 0), [C("right_leg", (-2, 0, -2), (4, 12, 4))]),
        P("left_leg", "root", (1.9, 12, 0), (0, 0, 0), [
            C("left_leg", (-2, 0, -2), (4, 12, 4), mirror=True),
            C("knee_growth", (0, 4, -3), (3, 3, 2)),
        ]),
    ])


def ash_hound():
    return Model("ash_hound", (64, 64), [
        P("body", "root", (0, 12, 0), (0, 0, 0), [
            C("chest", (-3, -3, -5), (6, 7, 7)),
            C("waist", (-2, -2.5, 2), (4, 4, 7)),
            C("flake1", (-0.5, -5.5, -5), (1, 3, 3)),
            C("flake2", (-0.5, -5, -1.5), (1, 3, 3)),
            C("flake3", (-0.5, -4.5, 2), (1, 2, 3)),
            C("flake4", (-0.5, -4, 5), (1, 2, 3)),
        ]),
        P("head", "body", (0, -1.5, -5), (0.1, 0, 0), [
            C("skull", (-2.5, -3, -4), (5, 5, 4)),
            C("snout", (-1.5, -1, -8), (3, 2, 4)),
            C("right_ear", (-2.5, -5, -1), (1, 3, 2)),
            C("left_ear", (1.5, -5, -1), (1, 3, 2), mirror=True),
        ]),
        P("jaw", "head", (0, 1.5, -3), (0.12, 0, 0), [C("jaw", (-1.5, 0, -5), (3, 1, 5))]),
        P("right_front_leg", "root", (-2, 14, -3.5), (0, 0, 0), [C("right_front_leg", (-1, 0, -1), (2, 10, 2))]),
        P("left_front_leg", "root", (2, 14, -3.5), (0, 0, 0), [C("left_front_leg", (-1, 0, -1), (2, 10, 2), mirror=True)]),
        # Digitigrade hind legs: the thigh slopes forward, the shin back.
        P("right_hind_leg", "root", (-1.8, 13, 6), (-0.45, 0, 0), [C("right_thigh", (-1.5, 0, -2), (3, 6, 4))]),
        P("right_hind_shin", "right_hind_leg", (0, 5.5, 0), (0.85, 0, 0), [C("right_shin", (-1, 0, -1), (2, 6, 2))]),
        P("left_hind_leg", "root", (1.8, 13, 6), (-0.45, 0, 0), [C("left_thigh", (-1.5, 0, -2), (3, 6, 4), mirror=True)]),
        P("left_hind_shin", "left_hind_leg", (0, 5.5, 0), (0.85, 0, 0), [C("left_shin", (-1, 0, -1), (2, 6, 2), mirror=True)]),
        P("tail", "body", (0, -1, 8.5), (1.0, 0, 0), [C("tail", (-1, 0, -1), (2, 5, 2))]),
        P("tail_tip", "tail", (0, 4.5, 0), (0.35, 0, 0), [C("tail_tip", (-0.5, 0, -0.5), (1, 5, 1))]),
    ])


def spitter():
    return Model("spitter", (64, 64), [
        # Stooped forward over a throat swollen with bile; sacs bulge along its back.
        P("body", "root", (0, 12, 0), (0.22, 0, 0), [
            C("torso", (-4, -12, -2.5), (8, 12, 5)),
            C("back_sac1", (-3.5, -10, 2.5), (3, 3, 2)),
            C("back_sac2", (1, -6, 2.5), (2, 2, 2)),
        ]),
        P("head", "body", (0, -12, -1), (-0.22, 0, 0), [C("skull", (-3.5, -7, -4), (7, 7, 7))]),
        P("jaw", "head", (0, -1, -1), (0.2, 0, 0), [C("jaw", (-3, 0, -3.5), (6, 2, 4))]),
        # The throat sac hangs under the jaw and swells before every spit.
        P("throat", "head", (0, 0.5, -2), (0, 0, 0), [C("throat", (-3, 0, -2.5), (6, 4, 5))]),
        P("right_arm", "body", (-5.5, -11, 0), (0, 0, 0.08), [C("right_arm", (-1.5, -1, -1.5), (3, 13, 3))]),
        P("left_arm", "body", (5.5, -11, 0), (0, 0, -0.08), [C("left_arm", (-1.5, -1, -1.5), (3, 13, 3), mirror=True, uv="right_arm")]),
        P("right_leg", "root", (-2, 12, 0), (0, 0, 0), [C("right_leg", (-1.5, 0, -1.5), (3, 12, 3))]),
        P("left_leg", "root", (2, 12, 0), (0, 0, 0), [C("left_leg", (-1.5, 0, -1.5), (3, 12, 3), mirror=True, uv="right_leg")]),
    ])


def bulwark():
    return Model("bulwark", (128, 64), [
        P("right_leg", "root", (-4, 10, 0), (0, 0, 0), [C("right_leg", (-3, 0, -3), (6, 14, 6))]),
        P("left_leg", "root", (4, 10, 0), (0, 0, 0), [C("left_leg", (-3, 0, -3), (6, 14, 6), mirror=True, uv="right_leg")]),
        P("body", "root", (0, 10, 0), (0.12, 0, 0), [
            C("waist", (-6, -6, -4), (12, 6, 8)),
            C("chest", (-8, -17, -5), (16, 11, 10)),
        ]),
        P("head", "body", (0, -17, -2), (-0.12, 0, 0), [
            C("head", (-3, -6, -3.5), (6, 6, 6)),
            C("helm", (-3, -6, -3.5), (6, 6, 6), grow=0.5),
        ]),
        # The right arm swings a heavy fist; the left holds the slab up in front.
        P("right_arm", "body", (-10, -15, 0), (-0.2, 0, 0.1), [
            C("right_shoulder", (-3.5, -3, -3.5), (7, 6, 7)),
            C("right_arm", (-2.5, 3, -2.5), (5, 11, 5)),
            C("right_fist", (-3, 13, -3), (6, 5, 6)),
        ]),
        P("left_arm", "body", (9, -15, -1), (-1.2, -0.45, 0), [
            C("left_shoulder", (-3.5, -3, -3.5), (7, 6, 7), mirror=True, uv="right_shoulder"),
            C("left_arm", (-2.5, 3, -2.5), (5, 11, 5), mirror=True, uv="right_arm"),
        ]),
        # A slab of riveted scrap covering it from the eyes to the knees.
        P("shield", "body", (1, -9, -8), (-0.12, 0, 0), [
            C("slab", (-9, -7, -2), (18, 24, 3)),
            C("boss", (-3, -1, -3.5), (6, 6, 2)),
            C("spike1", (-7, -5, -4), (2, 2, 2)),
            C("spike2", (5, -5, -4), (2, 2, 2)),
            C("spike3", (-7, 13, -4), (2, 2, 2)),
            C("spike4", (5, 13, -4), (2, 2, 2)),
        ]),
    ])


MODELS = {m.name: m for m in (shambler(), crawler(), screamer(), bloater(), brute(), mutant_brute(), infected_piglin(),
                              ash_hound(), spitter(), bulwark())}


# --- UV packing ---------------------------------------------------------------------------------------------------

def pack(model):
    """Shelf-pack every cube's UV footprint into the texture, tallest first. Raises if it doesn't fit."""
    width, height = model.tex
    own = [c for c in model.cubes() if c.uv is None]
    order = sorted(own, key=lambda c: (-c.footprint()[1], -c.footprint()[0], c.id))
    # Skyline packing: heights[x] is the first free row in column x.
    heights = [0] * width
    for c in order:
        fw, fh = (int(-(-n // 1)) for n in c.footprint())
        best = None
        for x in range(0, width - fw + 1):
            top = max(heights[x:x + fw])
            if top + fh <= height and (best is None or top < best[1]):
                best = (x, top)
        if best is None:
            raise ValueError(f"{model.name}: no room for {c.id} ({fw}x{fh}) in {width}x{height}")
        c.u, c.v = best
        for x in range(best[0], best[0] + fw):
            heights[x] = best[1] + fh
    by_id = {c.id: c for c in model.cubes()}
    for c in model.cubes():
        if c.uv is not None:
            src = by_id[c.uv]
            assert src.size == c.size, (model.name, c.id, c.uv)
            c.u, c.v = src.u, src.v
    return model


for _m in MODELS.values():
    pack(_m)


# --- Java ---------------------------------------------------------------------------------------------------------

def num(x):
    x = round(float(x), 4)
    if x == int(x):
        return str(int(x))
    return f"{x}f"


def camel(name):
    head, *rest = name.split("_")
    return head + "".join(s.capitalize() for s in rest)


def java_method(model):
    has_children = {p.parent for p in model.parts}
    lines = [
        f"\t/** {model.name}: {model.tex[0]}×{model.tex[1]} texture. */",
        f"\tpublic static LayerDefinition {camel(model.name)}() {{",
        "\t\tMeshDefinition mesh = new MeshDefinition();",
        "\t\tPartDefinition root = mesh.getRoot();",
    ]
    for p in model.parts:
        builder = "CubeListBuilder.create()"
        mirror = False
        for c in p.cubes:
            if c.mirror != mirror:
                builder += ".mirror()" if c.mirror else ".mirror(false)"
                mirror = c.mirror
            args = ", ".join(num(n) for n in (*c.origin, *c.size))
            if c.grow:
                args += f", new CubeDeformation({num(c.grow)})"
            builder += f"\n\t\t\t\t.texOffs({c.u}, {c.v}).addBox({args})"
        x, y, z = p.pivot
        rx, ry, rz = p.rot
        if any((rx, ry, rz)):
            pose = f"PartPose.offsetAndRotation({', '.join(num(n) for n in (x, y, z, rx, ry, rz))})"
        elif any((x, y, z)):
            pose = f"PartPose.offset({', '.join(num(n) for n in (x, y, z))})"
        else:
            pose = "PartPose.ZERO"
        parent = "root" if p.parent == "root" else camel(p.parent)
        decl = f"PartDefinition {camel(p.name)} = " if p.name in has_children else ""
        lines.append(f"\t\t{decl}{parent}.addOrReplaceChild(\"{p.name}\", {builder},\n\t\t\t\t{pose});")
    lines.append(f"\t\treturn LayerDefinition.create(mesh, {model.tex[0]}, {model.tex[1]});")
    lines.append("\t}")
    return "\n".join(lines)


def java():
    methods = "\n\n".join(java_method(m) for m in MODELS.values())
    return f"""// Generated by tools/assets/mob_models.py. Edit the spec there and re-run it; don't edit this file by hand.
package io.github.tmldude.blocksouls.mobs.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Meshes and UV layouts of every mob model. The textures in {{@code assets/blocksouls_mobs/textures/entity}} are
 * painted against these same layouts by {{@code tools/assets/mobs.py}}.
 */
public final class MobGeometry {{
\tprivate MobGeometry() {{
\t}}

{methods}
}}
"""


def main():
    with open(JAVA_OUT, "w") as f:
        f.write(java())
    for m in MODELS.values():
        used = max(c.v + c.footprint()[1] for c in m.cubes())
        print(f"{m.name}: {len(m.parts)} parts, {len(m.cubes())} cubes, {m.tex[0]}x{m.tex[1]} (rows used: {used:.0f})")


if __name__ == "__main__":
    main()
