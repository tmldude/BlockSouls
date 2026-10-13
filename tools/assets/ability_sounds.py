#!/usr/bin/env python3
"""Synthesizes every ability sound and writes them as .ogg, plus the abilities module's sounds.json.

    python3 tools/assets/ability_sounds.py      # needs numpy and ffmpeg with libvorbis

No samples. Cast cues are short UI-ish tones per slot (a click, a chord, a swell), the effects are layered from
synth.py's thumps, noise hits, metal, bells, fire and thunder. Output:
abilities/src/client/resources/assets/blocksouls_abilities/sounds/ability/...
"""

import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import (SR, Gen, at, band, bell, clicks, curve, decay, drive, emit, env, fire, impact, metal, mix,  # noqa: E402
                   noise_hit, pad_to, reverb, rumble, saw, sine, thump, thunder, whoosh)

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "abilities", "src", "client", "resources", "assets", "blocksouls_abilities")


def tone(f_path, dur, shape, wave=sine):
    n = int(dur * SR)
    return wave(curve(f_path, n), n) * env(n, shape=shape)


def sounds():
    S = {}

    def make(key, count, fn):
        S[key] = [fn(Gen(f"ability/{key}{i}"), i) for i in range(count)]

    # Cast cues: tactical = a short two-note click, signature = a rising three-note chord, ultimate = a deep swell.
    make("cast/tactical", 2, lambda g, i: mix((tone([(0, 880 + 40 * i), (1, 1320)], 0.14, [(0, 0), (0.1, 1), (1, 0)]), 0.6),
                                              (metal(g, 0.12, 2600, 0.01, gains=(1, 0.4, 0.2, 0.1)), 0.5)))

    def chord(g, i):
        d = 0.5
        out = np.zeros(int(d * SR))
        for k, f in enumerate((523, 659, 784)):
            out += at(tone([(0, f * (1 + 0.004 * i)), (1, f * 1.01)], 0.3, [(0, 0), (0.1, 1), (1, 0)], wave=lambda fr, n: saw(fr, n, 0)), 0.08 * k, d)
        return reverb(g, band(out, 200, 6000), 0.5, 0.2)
    make("cast/signature", 2, chord)

    def swell(g, i):
        d = 1.2
        n = int(d * SR)
        low = sine(curve([(0, 55), (0.6, 110), (1, 110)], n), n) * env(n, shape=[(0, 0), (0.5, 1), (0.8, 0.8), (1, 0)])
        shimmer = band(g.noise(n), 2000, 9000) * env(n, shape=[(0, 0), (0.6, 0.4), (0.7, 1), (1, 0)])
        return drive(mix((low, 1.0), (shimmer, 0.35), (bell(g, d, 1760, 0.3, bright=0.5), 0.3)), 1.8)
    make("cast/ultimate", 1, swell)

    # Denied: a dull double tap. Ready: a bright rising chime.
    make("denied", 1, lambda g, i: mix((at(thump(0.08, 220, 140, 0.02), 0.0, 0.2), 1.0), (at(thump(0.08, 200, 120, 0.02), 0.09, 0.2), 0.9)))
    make("ready", 1, lambda g, i: reverb(g, mix((at(bell(g, 0.5, 1320, 0.12, bright=0.6), 0.0, 0.8), 1.0),
                                                (at(bell(g, 0.6, 1980, 0.15, bright=0.6), 0.14, 0.8), 0.8),
                                                (at(bell(g, 0.7, 2640, 0.2, bright=0.5), 0.28, 0.8), 0.7)), 0.6, 0.25))

    # Throw: a short whoosh with a pin click.
    make("throw", 2, lambda g, i: mix((metal(g, 0.08, 3000 + 200 * i, 0.006, gains=(1, 0.4, 0.2, 0.1)), 0.5),
                                      (at(whoosh(g, 0.3, [(0, 400), (1, 900)], [(0, 2500), (1, 5000)]), 0.04, 0.4), 1.0)))
    # Frag: a hard blast with debris.
    make("frag", 2, lambda g, i: reverb(g, drive(mix((impact(g, 1.2, 120 - 10 * i, 30, 0.35, 0.5), 1.0),
                                                     (noise_hit(g, 1.2, 60, 4000, 0.15), 0.8)), 2.5), 0.9, 0.2, damp=2000))
    # Web: a snap and a stretchy twang.
    make("web", 2, lambda g, i: mix((noise_hit(g, 0.4, 1500, 8000, 0.01), 0.7),
                                    (tone([(0, 600 + 80 * i), (0.3, 300), (1, 240)], 0.4, [(0, 0), (0.05, 1), (1, 0)], wave=lambda f, n: saw(f, n, 0)), 0.6)))
    # Fire: a roaring burst.
    make("fire", 2, lambda g, i: mix((fire(g, 1.0, rate=34 + 4 * i, roar=1.2), 1.0), (noise_hit(g, 1.0, 100, 3000, 0.12), 0.5)))
    # Heal: a soft glassy hum with a pulse.
    make("heal", 1, lambda g, i: reverb(g, mix((tone([(0, 660), (1, 990)], 0.6, [(0, 0), (0.3, 1), (1, 0)]), 0.6),
                                               (bell(g, 0.6, 1320, 0.25, bright=0.3), 0.6)), 0.6, 0.3))
    # Slam: a ground-shaking thud with rubble.
    make("slam", 2, lambda g, i: reverb(g, drive(mix((impact(g, 1.0, 90 - 8 * i, 24, 0.3, 0.4), 1.0), (rumble(g, 1.0, 20, 120), 0.6)), 2.2), 0.8, 0.2, damp=1500))
    # Geyser: a rushing rise of water noise.
    make("geyser", 1, lambda g, i: mix((whoosh(g, 1.1, [(0, 200), (1, 1200)], [(0, 1500), (1, 7000)], ((0, 0), (0.3, 1), (1, 0))), 1.0),
                                       (clicks(g, 1.1, 90, 1500, 6000, decay=0.003) * decay(1.1, 0.4), 0.4)))
    # Fan the hammer: six quick cracks.
    def fan(g, i):
        d = 0.9
        out = np.zeros(int(d * SR))
        for k in range(6):
            crack = mix((noise_hit(g, 0.2, 300, 6000, 0.03), 1.0), (thump(0.2, 180, 60, 0.05), 0.8), (metal(g, 0.1, 2200, 0.01), 0.3))
            out += at(drive(crack, 2.5), 0.1 * k + 0.01 * (k % 2), d)
        return reverb(g, out, 0.7, 0.15, damp=2500)
    make("fan", 1, fan)
    # Zap: a crackling electric snap.
    make("zap", 2, lambda g, i: mix((clicks(g, 0.25, 400 + 100 * i, 2000, 9000, decay=0.0015) * decay(0.25, 0.08), 1.0),
                                    (tone([(0, 2400), (1, 600)], 0.25, [(0, 0), (0.02, 1), (1, 0)], wave=lambda f, n: saw(f, n, 0)), 0.3)))
    # Drain: a sucking downward sweep with a heartbeat.
    make("drain", 1, lambda g, i: mix((whoosh(g, 0.9, [(0, 1800), (1, 150)], [(0, 7000), (1, 900)]), 0.8),
                                      (at(thump(0.3, 90, 40, 0.1), 0.35, 0.9), 0.9), (at(thump(0.3, 90, 40, 0.1), 0.6, 0.9), 0.8)))
    # Storm: thunder, varied.
    make("storm", 3, lambda g, i: thunder(g, 1.6 + 0.2 * i, crack=0.8 + 0.3 * i))
    # Frenzy: a sharp swing whoosh.
    make("frenzy", 2, lambda g, i: mix((whoosh(g, 0.35, [(0, 300), (1, 1500)], [(0, 3000), (1, 9000)], ((0, 0), (0.4, 1), (1, 0))), 1.0),
                                       (noise_hit(g, 0.35, 1000, 6000, 0.02), 0.4 + 0.2 * i)))
    # Shield: a glassy rising shimmer locking in.
    make("shield", 1, lambda g, i: reverb(g, mix((tone([(0, 330), (1, 660)], 0.7, [(0, 0), (0.6, 1), (1, 0)]), 0.5),
                                                 (at(bell(g, 0.5, 1760, 0.2, bright=0.6), 0.45, 0.9), 0.8),
                                                 (band(g.noise(int(0.9 * SR)), 3000, 10000) * env(int(0.9 * SR), shape=[(0, 0), (0.5, 0.5), (1, 0)]), 0.2)),
                                             0.6, 0.3))
    return S


SUB = "subtitles.blocksouls_abilities."
EVENTS = {
    "ability.cast.tactical": ("cast/tactical", SUB + "ability.cast"),
    "ability.cast.signature": ("cast/signature", SUB + "ability.cast"),
    "ability.cast.ultimate": ("cast/ultimate", SUB + "ability.cast_ultimate"),
    "ability.denied": ("denied", SUB + "ability.denied"),
    "ability.ready": ("ready", SUB + "ability.ready"),
    "ability.throw": ("throw", SUB + "ability.throw"),
    "ability.frag": ("frag", SUB + "ability.frag"),
    "ability.web": ("web", SUB + "ability.web"),
    "ability.fire": ("fire", SUB + "ability.fire"),
    "ability.heal": ("heal", SUB + "ability.heal"),
    "ability.slam": ("slam", SUB + "ability.slam"),
    "ability.geyser": ("geyser", SUB + "ability.geyser"),
    "ability.fan": ("fan", SUB + "ability.fan"),
    "ability.zap": ("zap", SUB + "ability.zap"),
    "ability.drain": ("drain", SUB + "ability.drain"),
    "ability.storm": ("storm", SUB + "ability.storm"),
    "ability.frenzy": ("frenzy", SUB + "ability.frenzy"),
    "ability.shield": ("shield", SUB + "ability.shield"),
}
PEAKS = {"tactical": 0.5, "signature": 0.6, "ultimate": 0.8, "denied": 0.45, "ready": 0.6, "throw": 0.5, "frag": 0.92, "web": 0.6,
         "fire": 0.8, "heal": 0.55, "slam": 0.9, "geyser": 0.75, "fan": 0.88, "zap": 0.6, "drain": 0.65, "storm": 0.92, "frenzy": 0.7,
         "shield": 0.6}


def main():
    S = sounds()
    count = emit(ASSETS, "blocksouls_abilities", "ability", S, EVENTS, lambda key: PEAKS[key.split("/")[-1]])
    print(f"{count} sounds for {len(EVENTS)} events")


if __name__ == "__main__":
    main()
