#!/usr/bin/env python3
"""Kart horn sound and fuel can texture for the kart-fuel-horn feature.

    python3 vehicles/tools/gen_fuel_horn.py     # needs numpy, Pillow, and ffmpeg with libvorbis

The horn is synthesized from scratch with tools/assets/mob_sounds.py's helpers: two detuned, slightly overdriven
pulse tones a major third apart (the classic two-note car horn) through a tinny resonant "trumpet" band, with a
blip at the start where the diaphragm kicks in. Seeded, so re-running gives the same file.
"""

import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "tools", "assets"))
import mob_sounds as ms  # noqa: E402
from gen import PALETTES, rgb  # noqa: E402

ASSETS = os.path.join(HERE, "..", "src", "client", "resources", "assets", "blocksouls_vehicles")
SR = ms.SR


def pulse(freq, n, duty=0.35, wobble=None):
    f = freq * (1 + (wobble if wobble is not None else 0))
    phase = np.cumsum(f / SR) % 1
    return np.where(phase < duty, 1.0, -1.0)


def horn():
    g = ms.Gen("kart.horn")
    dur = 0.55
    n = int(dur * SR)
    wob = 0.004 * g.smooth_noise(n, 8)
    tones = []
    for base in (392.0, 494.0):  # G4 + B4
        for detune in (0.997, 1.004):
            tones.append(pulse(np.full(n, base * detune), n, wobble=wob))
    x = sum(tones) / len(tones)
    # A tinny metal diaphragm: band-limited, with a resonant bump around 2.4 kHz.
    body = ms.band(x, 250, 3800)
    ring = ms.band(x, 2100, 2800, soft=2.0)
    x = ms.mix((body, 1.0), (ring, 0.35))
    x = ms.drive(x, 2.2)
    # Attack: a quick pitch-less click as the horn kicks in, then a short hold and release.
    kick = ms.band(g.noise(int(0.02 * SR)), 800, 5000) * np.linspace(1, 0, int(0.02 * SR))
    x[:len(kick)] += kick * 0.4
    x *= ms.env(n, attack=0.012, release=0.08)
    x = ms.reverb(g, x, size=0.35, wet=0.12, damp=4000)
    return ms.finish(x, 0.85)


def fuel_can():
    """16x16 jerry can: rust body, ember cap and stripe, scrap handle."""
    rust = [rgb(c) for c in PALETTES["rust"]]
    ember = [rgb(c) for c in PALETTES["ember"]]
    scrap = [rgb(c) for c in PALETTES["scrap"]]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, c):
        px[x, y] = (*c, 255)

    # Body x 3..12, y 4..14, with a light left edge and dark right/bottom edge.
    for y in range(4, 15):
        for x in range(3, 13):
            c = rust[2]
            if x == 3 or y == 4:
                c = rust[3]
            if x == 12 or y == 14:
                c = rust[1]
            put(x, y, c)
    # Embossed X on the side.
    for i in range(6):
        put(5 + i, 7 + i, rust[1])
        put(10 - i, 7 + i, rust[1])
    # Ember stripe.
    for x in range(4, 12):
        put(x, 6, ember[3])
    # Handle (scrap) across the top-left, spout + cap top-right.
    for x in range(4, 9):
        put(x, 1, scrap[3])
    for y in range(1, 4):
        put(4, y, scrap[2])
        put(8, y, scrap[2])
    put(10, 3, scrap[1])
    put(11, 3, scrap[1])
    put(10, 2, ember[2])
    put(11, 2, ember[4])
    put(11, 1, ember[3])
    return img


def main():
    ms.write_ogg(horn(), os.path.join(ASSETS, "sounds", "kart", "horn.ogg"))
    out = os.path.join(ASSETS, "textures", "item", "fuel_can.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    fuel_can().save(out)
    print("wrote horn.ogg and fuel_can.png")


if __name__ == "__main__":
    main()
