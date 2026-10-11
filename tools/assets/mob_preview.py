#!/usr/bin/env python3
"""Renders the mob models with their textures, the way the game builds them, into preview sheets.

    python3 tools/assets/mob_preview.py                  # every mob -> tools/assets/previews/<mob>.png
    python3 tools/assets/mob_preview.py brute --hitbox   # one mob, with its hitbox outlined

A small software rasterizer: cubes and UVs are built exactly like `ModelPart.Cube` (same vertex order, same UV
rectangles, same mirroring), parts are posed like `ModelPart.translateAndRotate` (translate, then rotationZYX,
then scale), and the result is drawn like `LivingEntityRenderer` (flip x/y, feet on the ground). The `_glow`
layer is drawn full-bright on top. The poses (walk, telegraphs) are approximations of the Java `setupAnim`s,
good enough to judge silhouettes and how far a windup reads.
"""

import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mob_models import MODELS  # noqa: E402

TEXTURES = os.path.join(os.path.dirname(__file__), "..", "..", "mobs", "src", "main", "resources", "assets",
                        "blocksouls_mobs", "textures", "entity")
OUT = os.path.join(os.path.dirname(__file__), "previews")

# Renderer scale and hitbox (blocks) per mob, as in BlockSoulsMobsClient / MobEntities.
SCALE = {"brute": 1.15, "mutant_brute": 1.35}
HITBOX = {"shambler": (0.6, 1.95), "crawler": (0.9, 0.8), "screamer": (0.5, 2.0), "bloater": (1.2, 1.7),
          "brute": (1.3, 2.6), "mutant_brute": (1.5, 3.0), "infected_piglin": (0.6, 1.95), "ash_hound": (0.7, 0.75)}


def rot_zyx(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def affine(m3=None, t=(0, 0, 0)):
    m = np.eye(4)
    if m3 is not None:
        m[:3, :3] = m3
    m[:3, 3] = t
    return m


def cube_quads(c, tw, th):
    """(4 corner positions in pixels, 4 uvs in texels) per face, ported from ModelPart.Cube."""
    w, h, d = c.size
    min_x, min_y, min_z = (o - c.grow for o in c.origin)
    max_x, max_y, max_z = c.origin[0] + w + c.grow, c.origin[1] + h + c.grow, c.origin[2] + d + c.grow
    if c.mirror:
        min_x, max_x = max_x, min_x
    t0, t1, t2, t3 = (min_x, min_y, min_z), (max_x, min_y, min_z), (max_x, max_y, min_z), (min_x, max_y, min_z)
    l0, l1, l2, l3 = (min_x, min_y, max_z), (max_x, min_y, max_z), (max_x, max_y, max_z), (min_x, max_y, max_z)
    u, v = c.u, c.v
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    faces = [
        ([l1, l0, t0, t1], u1, v0, u2, v1),   # DOWN (visual top)
        ([t2, t3, l3, l2], u2, v1, u22, v0),  # UP (visual bottom)
        ([t0, l0, l3, t3], u0, v1, u1, v2),   # WEST
        ([t1, t0, t3, t2], u1, v1, u2, v2),   # NORTH (front)
        ([l1, t1, t2, l2], u2, v1, u3, v2),   # EAST
        ([l0, l1, l2, l3], u3, v1, u4, v2),   # SOUTH
    ]
    out = []
    for verts, a0, b0, a1, b1 in faces:
        uvs = [(a1, b0), (a0, b0), (a0, b1), (a1, b1)]
        if c.mirror:
            verts, uvs = verts[::-1], uvs[::-1]
        out.append((np.array(verts, float), np.array(uvs, float)))
    return out


def pose_matrices(model, pose):
    """World-from-part matrices (pixels), applying `pose` = {part: dict(rot=(dx,dy,dz), pos=(dx,dy,dz), scale=s)}."""
    mats = {"root": np.eye(4)}
    for p in model.parts:
        extra = pose.get(p.name, {})
        rx, ry, rz = (a + b for a, b in zip(p.rot, extra.get("rot", (0, 0, 0))))
        x, y, z = (a + b for a, b in zip(p.pivot, extra.get("pos", (0, 0, 0))))
        s = extra.get("scale", (1, 1, 1))
        m = mats[p.parent] @ affine(rot_zyx(rx, ry, rz), (x, y, z)) @ np.diag([s[0], s[1], s[2], 1])
        mats[p.name] = m
    return mats


def load(name):
    path = os.path.join(TEXTURES, name + ".png")
    return np.asarray(Image.open(path).convert("RGBA")).astype(float) / 255 if os.path.exists(path) else None


def render(name, pose=None, yaw=-35, pitch=18, size=(260, 300), ppb=None, hitbox=False, ground=True):
    """Orthographic render. yaw/pitch in degrees (0 = looking at the mob's face)."""
    model = MODELS[name]
    tex, glow = load(name), load(name + "_glow")
    tw, th = model.tex
    pose = pose or {}
    scale = SCALE.get(name, 1.0)
    mats = pose_matrices(model, pose)
    # Model pixels -> world blocks: LivingEntityRenderer's scale(-1,-1,1) then translate(0,-1.501,0), with the
    # mob's yaw 180 (facing +z). Net: x stays, y and z flip; feet (y=24) land on 0.
    to_world = affine(np.diag([1 / 16, -1 / 16, -1 / 16]) * scale, (0, 1.5 * scale, 0))
    cam = rot_zyx(math.radians(pitch), 0, 0) @ rot_zyx(0, math.radians(yaw), 0)
    W, H = size
    if ppb is None:
        ppb = min(W, H) / (max(HITBOX[name][1], 1.5) * 1.45 + 0.4)
    # Screen: x right, y down; camera looks along -z of the world after `cam` (mob faces +z = toward camera).
    cy = H * 0.86

    def project(pw):
        pc = pw @ cam.T
        return np.stack([W / 2 + pc[:, 0] * ppb, cy - pc[:, 1] * ppb, pc[:, 2]], axis=1)

    color = np.zeros((H, W, 3))
    color[:] = (0.13, 0.14, 0.17)
    depth = np.full((H, W), -1e9)
    light = np.array([0.35, 0.85, 0.55])
    light /= np.linalg.norm(light)

    if ground:
        # A floor grid, one cell per block, so heights read.
        img = Image.fromarray((color * 255).astype(np.uint8))
        dr = ImageDraw.Draw(img)
        for i in range(-3, 4):
            for a, b in (((i, 0, -3), (i, 0, 3)), ((-3, 0, i), (3, 0, i))):
                pts = project(np.array([a, b], float))
                dr.line([(pts[0, 0], pts[0, 1]), (pts[1, 0], pts[1, 1])], fill=(48, 52, 60), width=1)
        color = np.asarray(img).astype(float) / 255

    tris = []
    for p in model.parts:
        m = to_world @ mats[p.name]
        for c in p.cubes:
            for verts, uvs in cube_quads(c, tw, th):
                pw = (np.c_[verts, np.ones(4)] @ m.T)[:, :3]
                tris.append((pw, uvs))

    def raster(layer, fullbright):
        nonlocal color
        for pw, uvs in tris:
            n = np.cross(pw[1] - pw[0], pw[2] - pw[0])
            if np.linalg.norm(n) < 1e-9:
                n = np.cross(pw[2] - pw[0], pw[3] - pw[0])
            if np.linalg.norm(n) < 1e-9:
                continue
            n /= np.linalg.norm(n)
            shade = 1.0 if fullbright else 0.45 + 0.55 * abs(float(n @ light))
            sp = project(pw)
            for tri in ((0, 1, 2), (0, 2, 3)):
                a, b, c = sp[list(tri)]
                ua, ub, uc = uvs[list(tri)]
                x0, x1 = int(max(0, math.floor(min(a[0], b[0], c[0])))), int(min(W - 1, math.ceil(max(a[0], b[0], c[0]))))
                y0, y1 = int(max(0, math.floor(min(a[1], b[1], c[1])))), int(min(H - 1, math.ceil(max(a[1], b[1], c[1]))))
                if x1 < x0 or y1 < y0:
                    continue
                den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
                if abs(den) < 1e-9:
                    continue
                xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
                w0 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / den
                w1 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / den
                w2 = 1 - w0 - w1
                inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
                if not inside.any():
                    continue
                z = w0 * a[2] + w1 * b[2] + w2 * c[2]
                u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
                v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
                ui = np.clip(np.floor(u).astype(int), 0, tw - 1)
                vi = np.clip(np.floor(v).astype(int), 0, th - 1)
                texel = layer[vi * layer.shape[0] // th, ui * layer.shape[1] // tw]
                sub_depth = depth[y0:y1 + 1, x0:x1 + 1]
                if fullbright:
                    ok = inside & (texel[..., 3] > 0.1) & (z >= sub_depth - 0.02)
                    sub = color[y0:y1 + 1, x0:x1 + 1]
                    sub[ok] = np.clip(sub[ok] + texel[..., :3][ok] * texel[..., 3][ok][:, None], 0, 1)
                else:
                    ok = inside & (texel[..., 3] > 0.1) & (z > sub_depth)
                    color[y0:y1 + 1, x0:x1 + 1][ok] = texel[..., :3][ok] * shade
                    sub_depth[ok] = z[ok]

    if tex is None:
        tex = np.ones((th, tw, 4)) * np.array([1, 0, 1, 1])
    raster(tex, False)
    if glow is not None:
        raster(glow, True)

    img = Image.fromarray((np.clip(color, 0, 1) * 255).astype(np.uint8))
    if hitbox:
        bw, bh = HITBOX[name]
        dr = ImageDraw.Draw(img)
        corners = np.array([[x, y, z] for x in (-bw / 2, bw / 2) for y in (0, bh) for z in (-bw / 2, bw / 2)])
        sp = project(corners)
        for i in range(8):
            for j in range(i + 1, 8):
                if bin(i ^ j).count("1") == 1:
                    dr.line([tuple(sp[i, :2]), tuple(sp[j, :2])], fill=(255, 255, 255), width=1)
    return img


# --- Poses (approximate setupAnim) ---------------------------------------------------------------------------------

def walk(name, t):
    """A mid-stride pose; t in radians of the gait cycle."""
    s = math.sin(t)
    if name in ("shambler", "screamer", "infected_piglin"):
        pose = {"right_leg": {"rot": (s * 0.6, 0, 0)}, "left_leg": {"rot": (-s * 0.6, 0, 0)}}
        if name == "screamer":
            pose.update({"right_arm": {"rot": (-s * 0.4, 0, 0)}, "left_arm": {"rot": (s * 0.4, 0, 0)}})
        if name == "infected_piglin":
            pose.update({"right_arm": {"rot": (-s * 0.5, 0, 0)}, "left_arm": {"rot": (s * 0.5, 0, 0)}})
        return pose
    if name in ("brute", "mutant_brute"):
        return {"right_leg": {"rot": (s * 0.5, 0, 0)}, "left_leg": {"rot": (-s * 0.5, 0, 0)},
                "right_arm": {"rot": (-s * 0.4, 0, 0)}, "left_arm": {"rot": (s * 0.4, 0, 0)}}
    if name == "bloater":
        return {"right_leg": {"rot": (s * 0.6, 0, 0)}, "left_leg": {"rot": (-s * 0.6, 0, 0)}, "belly": {"rot": (0, 0, s * 0.06)}}
    if name == "ash_hound":
        return {"right_front_leg": {"rot": (s * 0.8, 0, 0)}, "left_front_leg": {"rot": (s * 0.6, 0, 0)},
                "right_hind_leg": {"rot": (-s * 0.7, 0, 0)}, "left_hind_leg": {"rot": (-s * 0.5, 0, 0)},
                "body": {"rot": (math.cos(t) * 0.06, 0, 0)}}
    if name == "crawler":
        pose = {}
        for i, limb in enumerate(("right_front", "left_front", "right_mid", "left_mid", "right_hind", "left_hind")):
            ph = s if i in (0, 3, 4) else -s
            flip = -1 if limb.startswith("left") else 1
            pose[limb] = {"rot": (0, flip * ph * 0.4, 0)}
            pose[limb + "_upper"] = {"rot": (0, 0, -flip * max(0, ph) * 0.45)}
        return pose
    return {}


def telegraph(name):
    """The attack windup at full extension."""
    if name == "shambler":
        return {"right_arm": {"rot": (-2.3, 0, 0)}, "right_forearm": {"rot": (-0.5, 0, 0)}, "left_arm": {"rot": (-0.7, 0, 0)},
                "body": {"rot": (-0.2, 0, 0)}, "jaw": {"rot": (0.4, 0, 0)}}
    if name == "crawler":
        # As CrawlerModel when airborne: front limbs thrown forward and up, hind limbs trailing, mouth wide.
        pose = {"head": {"rot": (-0.35, 0, 0)}, "jaw": {"rot": (0.6, 0, 0)}}
        for side, sx in (("right", -1), ("left", 1)):
            pose[f"{side}_front"] = {"rot": (0, -sx * 0.4, 0)}
            pose[f"{side}_front_upper"] = {"rot": (0, 0, sx * 0.45)}
            pose[f"{side}_front_lower"] = {"rot": (0, 0, -sx * 0.5)}
            pose[f"{side}_hind"] = {"rot": (0, sx * 0.35, 0)}
        return pose
    if name == "screamer":
        return {"jaw": {"rot": (1.3, 0, 0)}, "head": {"rot": (-0.35, 0, 0)}, "body": {"rot": (-0.25, 0, 0)},
                "right_arm": {"rot": (-0.3, 0, 0.75)}, "left_arm": {"rot": (-0.3, 0, -0.75)},
                "right_forearm": {"rot": (-0.9, 0, 0)}, "left_forearm": {"rot": (-0.9, 0, 0)}}
    if name == "bloater":
        pose = {"belly": {"scale": (1.32, 1.22, 1.32)}, "head": {"pos": (0, -3, 0)},
                "right_arm": {"rot": (0, 0, 0.8)}, "left_arm": {"rot": (0, 0, -0.8)}}
        for sac in ("sac_front", "sac_low", "sac_right", "sac_left", "sac_top", "sac_back"):
            pose[sac] = {"scale": (1.35, 1.35, 1.35)}
        return pose
    if name in ("brute", "mutant_brute"):
        # BruteModel at full windup: fists overhead, body reared back, legs planted wide.
        return {"right_arm": {"rot": (-2.58, 0, -0.47)}, "left_arm": {"rot": (-2.58, 0, 0.47)},
                "right_forearm": {"rot": (-0.37, 0, 0)}, "left_forearm": {"rot": (-0.37, 0, 0)},
                "body": {"rot": (-0.5, 0, 0)}, "head": {"rot": (0.35, 0, 0)},
                "right_leg": {"rot": (0, 0, 0.15)}, "left_leg": {"rot": (0, 0, -0.15)}}
    if name == "infected_piglin":
        return {"right_arm": {"rot": (-2.6, 0, 0)}, "left_arm": {"rot": (-1.2, 0, 0)}}
    if name == "ash_hound":
        return {"body": {"rot": (-0.2, 0, 0)}, "jaw": {"rot": (0.6, 0, 0)}, "head": {"rot": (-0.2, 0, 0)},
                "right_front_leg": {"rot": (-1.1, 0, 0)}, "left_front_leg": {"rot": (-1.0, 0, 0)},
                "right_hind_leg": {"rot": (0.6, 0, 0)}, "left_hind_leg": {"rot": (0.7, 0, 0)}, "tail": {"rot": (-0.4, 0, 0)}}
    return {}


TITLES = {"shambler": "Shambler", "crawler": "Crawler", "screamer": "Screamer", "bloater": "Bloater", "brute": "Brute",
          "mutant_brute": "Mutant Brute", "infected_piglin": "Infected Piglin", "ash_hound": "Ash Hound"}
TELEGRAPH = {"shambler": "swing", "crawler": "pounce", "screamer": "scream", "bloater": "swell (burst telegraph)",
             "brute": "slam windup", "mutant_brute": "leap / slam windup", "infected_piglin": "swing", "ash_hound": "pounce"}


def sheet(name, hitbox=False):
    """Front 3/4, side, back 3/4 and walk at rest; the telegraph pose; a 20-block-away thumbnail."""
    views = [
        (render(name, yaw=-35, hitbox=hitbox), "front"),
        (render(name, yaw=-90), "side"),
        (render(name, yaw=145), "back"),
        (render(name, walk(name, 1.2), yaw=-60), "walk"),
        (render(name, telegraph(name), yaw=-40), TELEGRAPH[name]),
    ]
    # How it reads from 20 blocks away: tiny, then blown up with nearest-neighbour.
    far = render(name, telegraph(name), yaw=-40, size=(260, 300), ppb=18, ground=False)
    far = far.crop((130 - 26, 258 - 70, 130 + 26, 258 + 6)).resize((104, 152), Image.NEAREST)
    pad, label = 8, 18
    w = sum(v[0].width for v in views) + far.width + pad * (len(views) + 2)
    h = views[0][0].height + label + pad * 2 + 22
    out = Image.new("RGB", (w, h), (24, 25, 30))
    dr = ImageDraw.Draw(out)
    font = ImageFont.load_default()
    dr.text((pad, 6), TITLES[name], fill=(235, 235, 235), font=font)
    x = pad
    for img, text in views:
        out.paste(img, (x, 22))
        dr.text((x + 4, 22 + img.height + 3), text, fill=(170, 170, 170), font=font)
        x += img.width + pad
    out.paste(far, (x, 22))
    dr.text((x + 2, 22 + far.height + 3), "at ~20 blocks", fill=(170, 170, 170), font=font)
    return out


def main(argv):
    os.makedirs(OUT, exist_ok=True)
    names = [a for a in argv if not a.startswith("--")] or list(MODELS)
    for name in names:
        sheet(name, hitbox="--hitbox" in argv).save(os.path.join(OUT, name + ".png"))
        print("wrote", os.path.join(OUT, name + ".png"))


if __name__ == "__main__":
    main(sys.argv[1:])
