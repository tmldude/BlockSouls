#!/usr/bin/env python3
"""Synthesizes every boss sound effect and writes them as .ogg, plus the bosses module's sounds.json (music included:
its entries point at the tracks music.py writes).

    python3 tools/assets/boss_sounds.py     # needs numpy and ffmpeg with libvorbis
    python3 tools/assets/music.py           # the boss tracks (sounds.json already names them)

No samples. Each boss has its own palette so it can be told apart with eyes closed:
- Furnace Knight: iron (inharmonic clanks), bellows (slow breathing noise), fire (roar and crackle).
- Pale Rider: hollow hoofbeats (a wooden-box thump), wind, and a spectral choir smeared by long reverb.
- Ashwyrm: huge wings (low air swells), a sub-heavy driven roar, fire breath.
- Stormplate Knight: thunder (crack and roll), steel (bright, long-ringing metal), electric crackle.
- Hollow Sovereign (the final raid): void (deep beating drones), cracking crystal (glassy bells and shatters), and
  Sovereign's End, a swell that rises over its length (the code replays it every 2 s, higher each time).
Helpers live in synth.py. Output: bosses/src/main/resources/assets/blocksouls_bosses/sounds/boss/...
"""

import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import (SR, Gen, at, band, bell, choir, clicks, curve, decay, drive, emit, env, fire, growl, impact, metal,  # noqa: E402
                   mix, noise_hit, pad_to, reverb, rumble, saw, shriek, sine, sweep_band, t_axis, thump, thunder,
                   voice, whoosh, wingbeat)

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "bosses", "src", "main", "resources", "assets", "blocksouls_bosses")


def sh(*pts):
    return list(pts)


# --- Palettes ------------------------------------------------------------------------------------------------------

def iron(g, dur, base=420, tau=0.12):
    """A heavy iron clank: a dull hit and low, quickly damped partials."""
    return mix((metal(g, dur, base, tau, ratios=(1.0, 2.31, 3.9, 6.1), gains=(1, 0.7, 0.45, 0.2)), 1.0),
               (noise_hit(g, dur, 300, 4000, 0.015), 0.6))


def steel(g, dur, base=760, tau=0.5):
    """Bright steel: higher partials that ring on."""
    return mix((metal(g, dur, base, tau, ratios=(1.0, 2.76, 5.4, 8.93, 13.3)[:4], gains=(1, 0.75, 0.5, 0.3)), 1.0),
               (noise_hit(g, dur, 1500, 9000, 0.006), 0.5))


def bellows(g, dur, rate=0.8):
    """Slow mechanical breathing: low noise swelling in and out."""
    n = int(dur * SR)
    breath = 0.5 - 0.5 * np.cos(2 * np.pi * rate * t_axis(dur)[:n])
    return band(g.noise(n), 80, 900) * breath ** 1.5


def crackle(g, dur, rate=40, lo=2500, hi=10000):
    return clicks(g, dur, rate, lo, hi, decay=0.0007, jitter=1.9)


def hoof(g, size=1.0):
    """One hollow hoofbeat: a hard tap on a resonant, empty box."""
    d = 0.35
    return mix((thump(d, 260 / size, 120 / size, 0.05), 1.0),
               (metal(g, d, 340 / size, 0.06, ratios=(1.0, 2.1, 3.3, 4.6), gains=(1, 0.5, 0.25, 0.1)), 0.5),
               (noise_hit(g, d, 600, 5000, 0.006), 0.6))


def gallop(g, dur, beats, start=0.0, size=1.0, gain=1.0):
    """Hoofbeats in the three-beat gallop rhythm, `beats` strides per second."""
    out = np.zeros(int(dur * SR))
    t0 = start
    while t0 < dur - 0.3:
        for k, off in enumerate((0.0, 0.11, 0.2)):
            out += at(hoof(g, size) * (0.75 + 0.25 * g.rng.random()) * (1 if k == 2 else 0.8), t0 + off, dur)
        t0 += 1 / beats
    return out * gain


def wind(g, dur, lo=300, hi=1600, rate=1.5):
    n = int(dur * SR)
    centre = 0.5 + 0.5 * g.smooth_noise(n, rate)
    lo_path = [(k / 8, lo * (0.7 + 0.6 * centre[min(n - 1, int(k / 8 * n))])) for k in range(9)]
    hi_path = [(k / 8, hi * (0.7 + 0.6 * centre[min(n - 1, int(k / 8 * n))])) for k in range(9)]
    return sweep_band(g.noise(n), lo_path, hi_path, dur) * (0.6 + 0.4 * centre)


def ghost(g, dur, f0_path, vowels=((0, "u"), (1, "o")), voices=4, size=1.2, wet=0.5):
    """The spectral voice: a detuned breathy choir in a long, dark hall."""
    n = int(dur * SR)
    y = choir(g, dur, f0_path, vowels, voices=voices, spread=0.02, scale=1.0, tilt=1.2)
    return reverb(g, y * env(n, 0.15, 0.4), size, wet, damp=2200)


def roar(g, dur, f0, scale=0.5, sub=0.8, rough=0.8, heat=2.5, vowels=((0, "o"), (0.3, "a"), (1, "a")), shape=None):
    shape = shape or sh((0, 0), (0.12, 1), (0.8, 0.9), (1, 0))
    return drive(growl(g, dur, f0, scale=scale, sub=sub, rough=rough, vowels=vowels, shape=shape) * 2, heat)


def zap(g, dur, rate=80):
    """Electric arcing: dense crackle with a buzzing 120 Hz edge."""
    n = int(dur * SR)
    buzz = band(saw(120 * (1 + 0.05 * g.smooth_noise(n, 20)), n), 200, 6000) * (0.5 + 0.5 * np.abs(g.smooth_noise(n, 40)))
    return mix((crackle(g, dur, rate, 1500, 12000), 1.0), (buzz, 0.5))


def void(g, dur, f=46, beat=0.7):
    """The Sovereign's void: a deep cluster of beating sines with a hollow, breathing noise floor."""
    n = int(dur * SR)
    t = t_axis(dur)[:n]
    tone = sum(np.sin(2 * np.pi * f * r * t + k) for k, r in enumerate((1.0, 1.0 + beat / f, 1.5, 2.003, 3.01)))
    air = band(g.noise(n), 60, 500) * (0.5 + 0.5 * g.smooth_noise(n, 1.5))
    return mix((tone, 1.0), (air, 0.5))


def glass(g, dur, f=1800, count=40, spread=2.5):
    """Shattering crystal: a sharp crack, then many small bright shards falling."""
    out = noise_hit(g, dur, 2000, 14000, 0.02) * 0.8
    for k in range(count):
        t0 = abs(g.rng.normal(0, 0.15)) + 0.01
        out += at(bell(g, 0.4, f * (1 + spread * g.rng.random()), 0.06, bright=0.9) * g.rng.uniform(0.2, 0.6) *
                  np.exp(-t0 * 3), t0, dur)
    return out


def swing(g, dur, lo=200, hi=1800, heavy=1.0):
    """A big weapon cutting the air."""
    return whoosh(g, dur, [(0, lo * 0.6), (0.5, lo * 1.4), (1, lo)], [(0, hi * 0.6), (0.5, hi * 1.5), (1, hi * 0.8)],
                  ((0, 0), (0.45, 1), (0.6, 0.6), (1, 0))) * heavy


def rise(g, dur, f0, f1, harmonics=(1, 2, 3), noise=0.4, lo=300, hi=3000):
    """A swell rising in pitch: a charge or a cast building."""
    n = int(dur * SR)
    f = curve([(0, f0), (1, f1)], n)
    tone = sum(sine(f * h, n, h) / h for h in harmonics)
    hiss = sweep_band(g.noise(n), [(0, lo), (1, lo * 3)], [(0, hi), (1, hi * 3)], dur)
    return mix((tone, 1.0), (hiss, noise)) * env(n, shape=sh((0, 0), (0.85, 1), (1, 0)))


# --- Sounds --------------------------------------------------------------------------------------------------------

def sounds():
    S = {}

    def make(key, count, fn):
        S[key] = [fn(Gen(f"boss/{key}{i}"), i) for i in range(count)]

    # Arena
    def fog_close(g, i):
        d = 1.6
        n = int(d * SR)
        swirl = whoosh(g, d, [(0, 1500), (1, 200)], [(0, 6000), (1, 900)], ((0, 0), (0.6, 1), (1, 0)))
        boom = at(thump(1.0, 70, 30, 0.35), 0.9, d)
        shimmer = sum(sine(curve([(0, 900 * r), (1, 600 * r)], n), n) for r in (1, 1.26, 1.5)) * env(n, shape=sh((0, 0), (0.7, 0.6), (1, 0)))
        return reverb(g, mix((swirl, 1.0), (boom, 0.9), (shimmer, 0.12)), 1.4, 0.35)
    make("arena/fog_close", 1, fog_close)

    def fog_open(g, i):
        d = 2.0
        n = int(d * SR)
        breath = whoosh(g, d, [(0, 200), (1, 1800)], [(0, 900), (1, 8000)], ((0, 0), (0.15, 1), (1, 0)))
        shimmer = sum(sine(curve([(0, 500 * r), (1, 900 * r)], n), n) for r in (1, 1.26, 1.5)) * env(n, shape=sh((0, 0), (0.2, 0.8), (1, 0)))
        return reverb(g, mix((breath, 1.0), (shimmer, 0.25)), 1.6, 0.4)
    make("arena/fog_open", 1, fog_open)

    # Lock-on: a soft, low UI tick (only the player who locks hears it).
    make("arena/lock_on", 1, lambda g, i: mix((metal(g, 0.15, 1100, 0.025, gains=(1, 0.3, 0.1, 0.05)), 1.0),
                                              (sine(curve([(0, 600), (1, 900)], int(0.15 * SR)), int(0.15 * SR)) * decay(0.15, 0.04), 0.5)))

    # Furnace Knight: iron, bellows, fire.
    make("furnace_knight/ambient", 2, lambda g, i: mix((bellows(g, 2.2, 0.7 + 0.1 * i), 1.0), (fire(g, 2.2, 20), 0.35),
                                                       (at(iron(g, 0.4, 300 + 40 * i, 0.08), 1.4, 2.2), 0.25)))
    make("furnace_knight/hurt", 2, lambda g, i: mix((iron(g, 0.6, 380 + 50 * i, 0.15), 1.0),
                                                    (growl(g, 0.6, sh((0, 80 + 10 * i), (0.3, 105), (1, 70)), scale=0.55, am=14,
                                                           vowels=((0, "a"), (1, "uh")), shape=sh((0, 0), (0.1, 1), (1, 0))), 0.6)))

    def fk_death(g, i):
        d = 3.2
        n = int(d * SR)
        voice_ = growl(g, 2.2, sh((0, 95), (0.4, 70), (1, 30)), scale=0.5, vowels=((0, "a"), (0.5, "o"), (1, "u")),
                       shape=sh((0, 0), (0.05, 1), (0.7, 0.6), (1, 0)))
        crash = at(mix((impact(g, 1.2, 100, 30, 0.3), 1.0), (iron(g, 1.2, 260, 0.3), 0.8)), 1.8, d)
        hiss = fire(g, d, 15) * env(n, shape=sh((0, 0.6), (0.6, 0.4), (1, 0)))
        return reverb(g, mix((pad_to(voice_, d), 1.0), (crash, 0.9), (hiss, 0.35)), 1.0, 0.2)
    make("furnace_knight/death", 1, fk_death)
    make("furnace_knight/step", 3, lambda g, i: mix((thump(0.45, 120 - 10 * i, 45, 0.07), 1.0), (iron(g, 0.45, 220 + 30 * i, 0.06), 0.5)))
    make("furnace_knight/roar", 1, lambda g, i: reverb(g, mix((roar(g, 2.0, sh((0, 60), (0.3, 120), (0.8, 110), (1, 70)), scale=0.5), 1.0),
                                                              (fire(g, 2.0, 35) * env(int(2.0 * SR), shape=sh((0, 0), (0.2, 1), (1, 0))), 0.5)),
                                                          1.1, 0.25))
    make("furnace_knight/windup", 2, lambda g, i: mix((bellows(g, 1.2, 1.5) * env(int(1.2 * SR), shape=sh((0, 0.3), (0.9, 1), (1, 0))), 1.0),
                                                      (fire(g, 1.2, 50 + 10 * i) * env(int(1.2 * SR), shape=sh((0, 0.1), (0.95, 1), (1, 0))), 0.8)))
    make("furnace_knight/slam", 2, lambda g, i: reverb(g, mix((impact(g, 1.6, 115 - 10 * i, 32, 0.35), 1.0), (iron(g, 1.6, 300, 0.2), 0.6),
                                                              (fire(g, 1.6, 60) * decay(1.6, 0.4), 0.5)), 1.0, 0.2))
    make("furnace_knight/swing", 2, lambda g, i: swing(g, 0.7, 150 + 20 * i, 1400))
    make("furnace_knight/breath", 2, lambda g, i: mix((fire(g, 1.4, 45, roar=1.3) * env(int(1.4 * SR), shape=sh((0, 0), (0.1, 1), (0.8, 0.9), (1, 0))), 1.0),
                                                      (whoosh(g, 1.4, [(0, 300), (1, 200)], [(0, 3000), (1, 2000)], ((0, 0), (0.1, 1), (1, 0))), 0.6)))

    def fk_charge(g, i):
        d = 1.6
        out = np.zeros(int(d * SR))
        for k in range(5):
            out += at(mix((thump(0.3, 130, 50, 0.05), 1.0), (iron(g, 0.3, 260 + 20 * k, 0.05), 0.5)), 0.05 + 0.28 * k, d)
        return mix((out, 1.0), (bellows(g, d, 2.0), 0.5), (fire(g, d, 30), 0.3))
    make("furnace_knight/charge", 1, fk_charge)
    make("furnace_knight/pillar", 2, lambda g, i: mix((whoosh(g, 1.3, [(0, 100), (0.3, 400), (1, 300)], [(0, 900), (0.3, 5000), (1, 3000)],
                                                             ((0, 0), (0.15, 1), (1, 0))), 1.0),
                                                      (fire(g, 1.3, 70) * decay(1.3, 0.5), 0.8), (thump(1.3, 100 + 10 * i, 40, 0.1), 0.6)))

    # Pale Rider: hollow hooves, wind, the spectral.
    make("pale_rider/ambient", 2, lambda g, i: mix((ghost(g, 2.6, sh((0, 150 + 15 * i), (0.5, 170), (1, 140))), 1.0),
                                                   (wind(g, 2.6), 0.4)))
    make("pale_rider/hurt", 2, lambda g, i: mix((hoof(g, 0.8), 0.8),
                                                (reverb(g, shriek(g, 0.4, sh((0, 520 + 40 * i), (1, 400)), [(0, "o"), (1, "u")], scale=1.0,
                                                                  dist=1.5, shape=sh((0, 0), (0.1, 1), (1, 0))), 0.8, 0.4), 0.8)))
    make("pale_rider/death", 1, lambda g, i: mix((ghost(g, 3.5, sh((0, 420), (0.3, 360), (1, 90)), ((0, "o"), (0.5, "u"), (1, "u")), 6, 1.6, 0.6), 1.0),
                                                 (wind(g, 3.5) * env(int(3.5 * SR), shape=sh((0, 0), (0.2, 1), (1, 0))), 0.5)))
    make("pale_rider/hooves", 3, lambda g, i: gallop(g, 1.1, 1.8 + 0.1 * i, 0.02 * i))
    make("pale_rider/scream", 2, lambda g, i: reverb(g, shriek(g, 1.3, sh((0, 300 + 30 * i), (0.15, 620), (0.6, 560), (1, 300)),
                                                               [(0, "a"), (0.4, "e"), (1, "o")], scale=1.0, rough=0.4, dist=2.0,
                                                               shape=sh((0, 0), (0.1, 1), (0.7, 0.8), (1, 0))), 1.6, 0.5, damp=2500))
    make("pale_rider/windup", 2, lambda g, i: mix((wind(g, 1.3, 400, 2400, 3) * env(int(1.3 * SR), shape=sh((0, 0.2), (0.9, 1), (1, 0))), 1.0),
                                                  (ghost(g, 1.3, sh((0, 180 + 20 * i), (1, 300)), ((0, "u"), (1, "a")), 3, 0.8, 0.3), 0.7)))
    make("pale_rider/charge", 1, lambda g, i: mix((gallop(g, 1.8, 2.6), 1.0),
                                                  (whoosh(g, 1.8, [(0, 300), (1, 800)], [(0, 1500), (1, 5000)], ((0, 0), (0.7, 1), (1, 0))), 0.6)))
    make("pale_rider/swing", 2, lambda g, i: reverb(g, swing(g, 0.6, 300 + 50 * i, 2600), 0.9, 0.35))
    make("pale_rider/stomp", 2, lambda g, i: reverb(g, mix((hoof(g, 0.55), 1.0), (impact(g, 1.0, 140, 45, 0.2, 0.2), 0.7)), 1.3, 0.35))
    make("pale_rider/call", 1, lambda g, i: mix((ghost(g, 2.4, sh((0, 110), (0.3, 165), (1, 165)), ((0, "u"), (0.4, "o"), (1, "a")), 6, 1.8, 0.55), 1.0),
                                                (wind(g, 2.4, 200, 900), 0.4)))
    make("pale_rider/throw", 2, lambda g, i: reverb(g, mix((swing(g, 0.5, 600, 4000), 1.0),
                                                           (sine(curve([(0, 1800 + 100 * i), (1, 900)], int(0.5 * SR)), int(0.5 * SR)) *
                                                            env(int(0.5 * SR), shape=sh((0, 0), (0.3, 1), (1, 0))), 0.25)), 0.9, 0.3))

    def blink(g, i):
        d = 0.9
        n = int(d * SR)
        suck = whoosh(g, d, [(0, 3000), (1, 300)], [(0, 9000), (1, 1500)], ((0, 0), (0.8, 1), (0.85, 0), (1, 0)))
        sparkle = sum(sine(curve([(0, 2000 * r), (1, 700 * r)], n), n) for r in (1, 1.33)) * env(n, shape=sh((0, 0), (0.75, 1), (1, 0)))
        return reverb(g, mix((suck, 1.0), (sparkle, 0.15)), 1.0, 0.45)
    make("pale_rider/blink", 2, blink)
    make("pale_rider/whistle", 1, lambda g, i: reverb(g, mix((sine(curve([(0, 1300), (0.35, 1900), (0.6, 1750), (1, 1500)], int(1.1 * SR)) *
                                                                   (1 + 0.006 * np.sin(2 * np.pi * 6 * t_axis(1.1)[:int(1.1 * SR)])), int(1.1 * SR)), 1.0),
                                                              (band(g.noise(int(1.1 * SR)), 1200, 3500), 0.25)) *
                                                          env(int(1.1 * SR), shape=sh((0, 0), (0.1, 1), (0.8, 0.8), (1, 0))), 1.5, 0.45))
    make("pale_rider/steed_fade", 1, lambda g, i: mix((ghost(g, 1.6, sh((0, 300), (1, 120)), ((0, "o"), (1, "u")), 3, 1.4, 0.6), 0.8),
                                                      (whoosh(g, 1.6, [(0, 2000), (1, 300)], [(0, 7000), (1, 1200)], ((0, 0), (0.2, 1), (1, 0))), 0.6)))

    # Ashwyrm: huge wings, fire breath, a roar you feel.
    make("ashwyrm/ambient", 2, lambda g, i: mix((growl(g, 2.6, sh((0, 38 + 4 * i), (0.5, 46), (1, 36)), scale=0.4, rough=0.7, am=9,
                                                       vowels=((0, "u"), (1, "o"))), 1.0), (fire(g, 2.6, 8, 0.3), 0.15)))
    make("ashwyrm/hurt", 2, lambda g, i: roar(g, 0.8, sh((0, 90 + 10 * i), (0.3, 130), (1, 80)), scale=0.45, heat=2.0,
                                               shape=sh((0, 0), (0.08, 1), (1, 0))))
    make("ashwyrm/death", 1, lambda g, i: reverb(g, mix((roar(g, 3.4, sh((0, 120), (0.3, 100), (1, 25)), scale=0.42, heat=2.0,
                                                             vowels=((0, "a"), (0.5, "o"), (1, "u")), shape=sh((0, 0), (0.05, 1), (0.7, 0.6), (1, 0))), 1.0),
                                                        (at(impact(g, 1.4, 80, 25, 0.5), 2.6, 4.0), 1.0)), 1.6, 0.25))
    make("ashwyrm/step", 3, lambda g, i: mix((thump(0.7, 80 - 5 * i, 28, 0.12), 1.0), (rumble(g, 0.7) * decay(0.7, 0.2), 0.5),
                                             (noise_hit(g, 0.7, 150, 2000, 0.03), 0.3)))
    make("ashwyrm/roar", 2, lambda g, i: reverb(g, mix((roar(g, 2.6, sh((0, 50 + 5 * i), (0.25, 105), (0.8, 95), (1, 55)), scale=0.4, heat=3.0), 1.0),
                                                       (shriek(g, 2.6, sh((0, 200), (0.25, 280), (1, 180)), [(0, "a"), (1, "o")], scale=0.7,
                                                               shape=sh((0, 0), (0.2, 1), (0.8, 0.7), (1, 0))), 0.25),
                                                       (rumble(g, 2.6, 20, 90) * env(int(2.6 * SR), shape=sh((0, 0), (0.2, 1), (1, 0))), 0.6)),
                                                   1.6, 0.25))

    def bite(g, i):
        d = 0.8
        snap = at(mix((noise_hit(g, 0.3, 500, 6000, 0.01), 1.0), (thump(0.3, 200, 70, 0.04), 0.8), (iron(g, 0.3, 180, 0.03), 0.4)), 0.25, d)
        snarl = growl(g, d, sh((0, 70), (0.3, 110), (1, 60)), scale=0.45, am=18, vowels=((0, "a"), (1, "uh")),
                      shape=sh((0, 0), (0.25, 1), (0.35, 0.3), (1, 0)))
        return mix((snap, 1.0), (snarl, 0.7))
    make("ashwyrm/bite", 2, bite)
    make("ashwyrm/tail", 2, lambda g, i: mix((swing(g, 1.0, 80, 900, 1.0), 1.0), (at(thump(0.5, 90, 35, 0.1), 0.55, 1.0), 0.5)))
    make("ashwyrm/breath", 2, lambda g, i: mix((fire(g, 2.4, 60, roar=1.5) * env(int(2.4 * SR), shape=sh((0, 0), (0.08, 1), (0.85, 0.9), (1, 0))), 1.0),
                                               (growl(g, 2.4, sh((0, 55), (1, 50)), scale=0.4, am=30,
                                                      shape=sh((0, 0), (0.1, 1), (0.85, 0.8), (1, 0))), 0.45),
                                               (whoosh(g, 2.4, [(0, 150), (1, 150)], [(0, 2500), (1, 2500)], ((0, 0), (0.08, 1), (0.85, 1), (1, 0))), 0.5)))
    make("ashwyrm/flap", 3, lambda g, i: wingbeat(g, 0.9 + 0.1 * i, 1.6))
    make("ashwyrm/fireball", 2, lambda g, i: mix((whoosh(g, 0.9, [(0, 200), (1, 800)], [(0, 1500), (1, 6000)], ((0, 0), (0.15, 1), (1, 0))), 1.0),
                                                 (fire(g, 0.9, 50) * decay(0.9, 0.3), 0.7), (thump(0.9, 160, 60, 0.06), 0.5)))
    make("ashwyrm/impact", 2, lambda g, i: reverb(g, mix((impact(g, 1.4, 100, 32, 0.3), 1.0), (fire(g, 1.4, 70) * decay(1.4, 0.35), 0.6)), 1.0, 0.2))
    make("ashwyrm/land", 1, lambda g, i: reverb(g, mix((impact(g, 2.2, 70, 22, 0.6, 0.5), 1.0), (wingbeat(g, 0.8, 1.8), 0.4)), 1.4, 0.2))

    def meteor(g, i):
        d = 2.2
        n = int(d * SR)
        fall = whoosh(g, d, [(0, 1500), (1, 150)], [(0, 6000), (1, 1200)], ((0, 0), (0.85, 1), (0.9, 0.2), (1, 0)))
        burn = fire(g, d, 40) * env(n, shape=sh((0, 0), (0.85, 1), (0.9, 0.3), (1, 0)))
        boom = at(impact(g, 0.8, 90, 30, 0.25), 1.85, d)
        return mix((fall, 1.0), (burn, 0.5), (boom, 1.0))
    make("ashwyrm/meteor", 2, meteor)

    # Stormplate Knight: thunder and steel.
    make("stormplate_knight/ambient", 2, lambda g, i: mix((zap(g, 2.0, 12 + 4 * i) * (0.4 + 0.6 * np.abs(g.smooth_noise(int(2.0 * SR), 2))), 0.5),
                                                          (sine(100 + 0 * i, int(2.0 * SR)) * 0.3 + sine(200, int(2.0 * SR)) * 0.2, 0.5),
                                                          (at(steel(g, 0.8, 600 + 50 * i, 0.3), 1.0, 2.0), 0.3)))
    make("stormplate_knight/hurt", 2, lambda g, i: mix((steel(g, 0.7, 700 + 80 * i, 0.25), 1.0), (crackle(g, 0.7, 60) * decay(0.7, 0.2), 0.4)))
    make("stormplate_knight/death", 1, lambda g, i: reverb(g, mix((thunder(g, 3.5), 1.0), (at(steel(g, 1.5, 500, 0.6), 0.6, 3.5), 0.7),
                                                                  (at(impact(g, 1.5, 100, 30, 0.3), 1.8, 3.5), 0.8)), 1.4, 0.2))
    make("stormplate_knight/step", 3, lambda g, i: mix((thump(0.5, 130 - 10 * i, 50, 0.06), 1.0), (steel(g, 0.5, 820 + 60 * i, 0.12), 0.35)))
    make("stormplate_knight/roar", 1, lambda g, i: reverb(g, mix((roar(g, 2.0, sh((0, 70), (0.3, 140), (0.8, 125), (1, 80)), scale=0.55), 1.0),
                                                                 (zap(g, 2.0, 120) * env(int(2.0 * SR), shape=sh((0, 0), (0.2, 1), (1, 0))), 0.4),
                                                                 (steel(g, 2.0, 500, 0.8), 0.2)), 1.2, 0.25))
    make("stormplate_knight/windup", 2, lambda g, i: mix((rise(g, 1.2, 90 + 20 * i, 360, (1, 2, 3, 5), 0.3, 800, 4000), 1.0),
                                                         (zap(g, 1.2, 60) * env(int(1.2 * SR), shape=sh((0, 0), (0.9, 1), (1, 0))), 0.6)))
    make("stormplate_knight/thrust", 2, lambda g, i: mix((swing(g, 0.4, 900 + 100 * i, 6000), 1.0), (at(steel(g, 0.5, 1100, 0.2), 0.15, 0.6), 0.4)))
    make("stormplate_knight/swing", 2, lambda g, i: mix((swing(g, 0.7, 250 + 30 * i, 2200), 1.0), (crackle(g, 0.7, 50), 0.25)))
    make("stormplate_knight/charge", 1, lambda g, i: mix((zap(g, 1.6, 150), 1.0),
                                                         (rise(g, 1.6, 60, 240, (1, 2, 3), 0.5, 300, 3000), 0.7)))
    make("stormplate_knight/thunder", 3, lambda g, i: thunder(g, 2.8 + 0.3 * i, 1.0 - 0.15 * i))
    make("stormplate_knight/throw", 2, lambda g, i: mix((swing(g, 0.7, 700, 5000), 1.0), (zap(g, 0.7, 120) * env(int(0.7 * SR), shape=sh((0, 0), (0.3, 1), (1, 0))), 0.5)))
    make("stormplate_knight/slam", 2, lambda g, i: reverb(g, mix((impact(g, 1.8, 110, 30, 0.35), 1.0), (thunder(g, 1.8, 0.7), 0.6),
                                                                 (steel(g, 1.8, 450 + 40 * i, 0.4), 0.4)), 1.0, 0.2))
    make("stormplate_knight/puddle", 2, lambda g, i: mix((zap(g, 0.5, 200), 1.0), (noise_hit(g, 0.5, 2000, 10000, 0.03), 0.6)) * decay(0.5, 0.18))

    # Hollow Sovereign (the final raid): void, cracking crystal.
    make("hollow_sovereign/ambient", 2, lambda g, i: reverb(g, mix((void(g, 2.8, 40 + 3 * i), 0.6),
                                                                   (growl(g, 2.8, sh((0, 34), (0.5, 40), (1, 32)), scale=0.38, rough=0.5, am=6,
                                                                          vowels=((0, "u"), (1, "o"))), 1.0)), 1.6, 0.3))
    make("hollow_sovereign/hurt", 2, lambda g, i: mix((glass(g, 0.7, 1400 + 200 * i, 10), 0.6),
                                                      (roar(g, 0.7, sh((0, 80), (0.3, 110), (1, 70)), scale=0.42, heat=2.0, shape=sh((0, 0), (0.08, 1), (1, 0))), 1.0)))

    def sov_death(g, i):
        d = 6.0
        n = int(d * SR)
        cry = roar(g, 3.5, sh((0, 110), (0.3, 90), (1, 20)), scale=0.4, heat=2.0, vowels=((0, "a"), (0.5, "o"), (1, "u")),
                   shape=sh((0, 0), (0.05, 1), (0.7, 0.6), (1, 0)))
        drone = void(g, d, 44) * env(n, shape=sh((0, 0), (0.1, 1), (1, 0)))
        shatter = at(glass(g, 2.5, 900, 80, 4), 2.2, d)
        return reverb(g, mix((pad_to(cry, d), 1.0), (drone, 0.5), (shatter, 0.7)), 2.2, 0.35, damp=2400)
    make("hollow_sovereign/death", 1, sov_death)
    make("hollow_sovereign/flap", 3, lambda g, i: reverb(g, wingbeat(g, 1.1 + 0.1 * i, 2.0), 1.4, 0.25))
    make("hollow_sovereign/roar", 2, lambda g, i: reverb(g, mix((roar(g, 2.8, sh((0, 45 + 5 * i), (0.25, 95), (0.8, 85), (1, 45)), scale=0.38, heat=2.5), 1.0),
                                                                (void(g, 2.8, 41) * env(int(2.8 * SR), shape=sh((0, 0), (0.2, 1), (1, 0))), 0.4)),
                                                            2.0, 0.35))
    make("hollow_sovereign/screech", 1, lambda g, i: reverb(g, shriek(g, 1.2, sh((0, 250), (0.7, 700), (1, 760)), [(0, "o"), (0.6, "a"), (1, "e")],
                                                                      scale=0.9, rough=0.4, dist=3.0, shape=sh((0, 0), (0.85, 1), (1, 0))), 1.4, 0.35))
    make("hollow_sovereign/breath", 2, lambda g, i: mix((whoosh(g, 2.2, [(0, 80), (1, 120)], [(0, 1200), (1, 1600)], ((0, 0), (0.1, 1), (0.85, 1), (1, 0))), 1.0),
                                                        (void(g, 2.2, 52) * env(int(2.2 * SR), shape=sh((0, 0), (0.1, 1), (0.85, 1), (1, 0))), 0.6),
                                                        (crackle(g, 2.2, 30, 3000, 12000), 0.25)))
    make("hollow_sovereign/gust", 2, lambda g, i: whoosh(g, 1.4, [(0, 150), (0.3, 500), (1, 200)], [(0, 1500), (0.3, 6000), (1, 1500)],
                                                         ((0, 0), (0.25, 1), (1, 0))))
    make("hollow_sovereign/tail", 2, lambda g, i: swing(g, 1.0, 90, 1000))
    make("hollow_sovereign/orb", 1, lambda g, i: mix((void(g, 1.4, 110, 3), 0.7), (bell(g, 1.4, 880, 0.8, 0.3), 0.5)) *
         env(int(1.4 * SR), shape=sh((0, 0), (0.15, 1), (0.8, 0.8), (1, 0))))
    make("hollow_sovereign/orb_pop", 2, lambda g, i: mix((glass(g, 0.6, 2200 + 300 * i, 12, 1.5), 1.0), (thump(0.6, 300, 100, 0.03), 0.5)))
    make("hollow_sovereign/meteor", 2, lambda g, i: reverb(g, mix((impact(g, 1.6, 90, 28, 0.35), 1.0), (glass(g, 1.6, 1000, 20), 0.4),
                                                                  (void(g, 1.6, 50) * decay(1.6, 0.5), 0.4)), 1.2, 0.25))
    make("hollow_sovereign/land", 1, lambda g, i: reverb(g, mix((impact(g, 2.4, 65, 20, 0.7, 0.5), 1.0), (glass(g, 2.4, 900, 30, 3), 0.3)), 1.6, 0.25))
    make("hollow_sovereign/deflect", 2, lambda g, i: mix((bell(g, 0.6, 1500 + 200 * i, 0.15, 0.8), 1.0), (noise_hit(g, 0.6, 3000, 12000, 0.004), 0.6)))
    make("hollow_sovereign/enrage", 1, lambda g, i: reverb(g, mix((rise(g, 2.6, 40, 120, (1, 1.5, 2, 3), 0.5, 100, 1500), 1.0),
                                                                  (roar(g, 2.6, sh((0, 40), (0.7, 120), (1, 110)), scale=0.4, heat=3.0,
                                                                        shape=sh((0, 0), (0.6, 0.6), (0.9, 1), (1, 0))), 0.9)), 2.0, 0.3))
    make("hollow_sovereign/shield_break", 1, lambda g, i: reverb(g, mix((glass(g, 2.2, 700, 90, 4), 1.0), (thump(2.2, 140, 40, 0.2), 0.6),
                                                                        (void(g, 2.2, 60) * decay(2.2, 0.6), 0.4)), 1.4, 0.3))

    def cast(g, i):
        # Sovereign's End: a dark swell that climbs and brightens; replayed every 2 s at a rising pitch, the layers
        # overlap into one long, ever-higher build.
        d = 2.6
        n = int(d * SR)
        drone = void(g, d, 55, 1.5) * env(n, shape=sh((0, 0), (0.4, 1), (0.8, 0.9), (1, 0)))
        climb = rise(g, d, 220, 330, (1, 1.5, 2), 0.3, 400, 3000)
        chorus = choir(g, d, sh((0, 110), (1, 165)), ((0, "u"), (1, "a")), voices=4, spread=0.015, scale=0.9) * \
            env(n, shape=sh((0, 0), (0.6, 1), (1, 0)))
        return reverb(g, mix((drone, 0.8), (climb, 0.5), (chorus, 0.6)), 1.6, 0.35)
    make("hollow_sovereign/cast", 1, cast)

    # The raid's crystals and plates. The chime is a clean tone: the code sets its pitch from the note's position.
    make("raid/crystal_chime", 1, lambda g, i: reverb(g, bell(g, 2.0, 660, 0.9, 0.4), 1.4, 0.3))
    make("raid/crystal_shatter", 2, lambda g, i: reverb(g, glass(g, 1.6, 1200 + 200 * i, 60, 3), 1.0, 0.25))
    make("raid/crystal_wrong", 1, lambda g, i: mix((band(saw(curve([(0, 92), (1, 80)], int(0.9 * SR)), int(0.9 * SR)) +
                                                         saw(97, int(0.9 * SR)), 60, 1600), 1.0), (void(g, 0.9, 46), 0.6)) *
         env(int(0.9 * SR), shape=sh((0, 0), (0.05, 1), (0.7, 0.8), (1, 0))))

    def reform(g, i):
        d = 1.6
        n = int(d * SR)
        notes = np.zeros(n)
        for k, f in enumerate((440, 554, 659, 880)):
            notes += at(bell(g, 0.8, f, 0.4, 0.4), 0.25 * k, d)
        rush = whoosh(g, d, [(0, 300), (1, 3000)], [(0, 2000), (1, 10000)], ((0, 0), (0.9, 1), (1, 0)))
        return reverb(g, mix((rush, 0.6), (notes, 1.0)), 1.2, 0.3)
    make("raid/crystal_reform", 1, reform)
    make("raid/plate_hum", 1, lambda g, i: mix((sum(sine(f, int(1.4 * SR)) / k for k, f in enumerate((220, 330, 440.7), 1)), 1.0),
                                               (band(g.noise(int(1.4 * SR)), 200, 900), 0.15)) *
         env(int(1.4 * SR), shape=sh((0, 0), (0.2, 1), (0.8, 1), (1, 0))))
    make("raid/plate_held", 1, lambda g, i: reverb(g, mix((at(bell(g, 1.0, 523, 0.5, 0.4), 0, 1.2), 1.0), (at(bell(g, 1.0, 784, 0.5, 0.4), 0.12, 1.2), 0.8)), 1.0, 0.25))

    def chains(g, i):
        d = 1.4
        out = np.zeros(int(d * SR))
        for k in range(28):
            t0 = 0.05 + 1.1 * (k / 28) + 0.03 * g.rng.normal()
            out += at(metal(g, 0.12, 1400 + 1600 * g.rng.random(), 0.02, gains=(1, 0.5, 0.3, 0.15)) * g.rng.uniform(0.3, 1), max(0, t0), d)
        return mix((out, 1.0), (thump(d, 90, 40, 0.15), 0.4))
    make("raid/chains", 1, chains)
    return S


# Event id -> (sound key, subtitle). Every existing event keeps its subtitle key.
def _events():
    ev = {"block.fog_wall.close": "arena/fog_close", "block.fog_wall.open": "arena/fog_open", "ui.lock_on": "arena/lock_on"}
    per_boss = {
        "furnace_knight": ["ambient", "hurt", "death", "step", "roar", "windup", "slam", "swing", "breath", "charge", "pillar"],
        "pale_rider": ["ambient", "hurt", "death", "hooves", "scream", "windup", "charge", "swing", "stomp", "call", "throw", "blink"],
        "ashwyrm": ["ambient", "hurt", "death", "step", "roar", "bite", "tail", "breath", "flap", "fireball", "impact", "land", "meteor"],
        "stormplate_knight": ["ambient", "hurt", "death", "step", "roar", "windup", "thrust", "swing", "charge", "thunder", "throw",
                              "slam", "puddle"],
    }
    for boss, names in per_boss.items():
        for name in names:
            ev[f"entity.{boss}.{name}"] = f"{boss}/{name}"
    ev["item.wraith_steed_whistle.use"] = "pale_rider/whistle"
    ev["entity.wraith_steed.fade"] = "pale_rider/steed_fade"
    # The final raid's dragon keeps its Stage 2b event ids (entity.final_dragon.*); its sounds are the Sovereign's.
    for name in ["ambient", "hurt", "death", "flap", "roar", "screech", "breath", "gust", "tail", "orb", "orb_pop", "meteor", "land",
                 "deflect", "enrage", "shield_break", "cast"]:
        ev[f"entity.final_dragon.{name}"] = f"hollow_sovereign/{name}"
    for name in ["chime", "shatter", "wrong", "reform"]:
        ev[f"entity.raid_crystal.{name}"] = f"raid/crystal_{name}"
    ev["block.raid_plate.hum"] = "raid/plate_hum"
    ev["block.raid_plate.held"] = "raid/plate_held"
    ev["block.raid_plate.chains"] = "raid/chains"
    return {e: (k, None if e == "ui.lock_on" else f"subtitles.blocksouls_bosses.{e}") for e, k in ev.items()}


EVENTS = _events()
# Music: one looping track per boss, written by music.py, streamed from disk.
MUSIC = {"music.furnace_knight": "furnace_knight", "music.pale_rider": "pale_rider", "music.ashwyrm": "ashwyrm",
         "music.stormplate_knight": "stormplate_knight", "music.final_dragon": "final_raid"}

# Peaks by kind: telegraphs and big impacts loudest, idle sounds and steps quieter.
PEAKS = {"ambient": 0.6, "step": 0.6, "hooves": 0.7, "hurt": 0.75, "death": 0.9, "lock_on": 0.45, "plate_hum": 0.55, "orb": 0.7,
         "crystal_chime": 0.7, "plate_held": 0.7}


def peak(key):
    return PEAKS.get(key.split("/")[-1], 0.9)


def main():
    import json
    S = sounds()
    count = emit(ASSETS, "blocksouls_bosses", "boss", S, EVENTS, peak)
    path = os.path.join(ASSETS, "sounds.json")
    with open(path) as f:
        data = json.load(f)
    for event, track in MUSIC.items():
        data[event] = {"sounds": [{"name": f"blocksouls_bosses:music/{track}", "stream": True, "volume": 0.6}]}
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print(f"{count} sounds for {len(EVENTS)} events, plus {len(MUSIC)} music events")


if __name__ == "__main__":
    main()
