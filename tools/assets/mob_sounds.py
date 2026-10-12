#!/usr/bin/env python3
"""Synthesizes every mob sound from scratch and writes them as .ogg, plus sounds.json.

    python3 tools/assets/mob_sounds.py      # needs numpy and ffmpeg with libvorbis

No samples: voices are a glottal pulse train (with jitter, roughness and a growling subharmonic) shaped by vowel
formants that move over time (STFT filtering), mixed with filtered noise for breath, hiss and splatter, and
decaying resonances for impacts (the helpers live in synth.py). A little reverb places them in the world. Same
script, same samples (the random generators are seeded per sound).
Output: mobs/src/main/resources/assets/blocksouls_mobs/sounds/mob/...
"""

import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import (SR, Gen, band, bubbles, clicks, curve, drive, env, finish, groan, growl, mix,  # noqa: E402
                   resonances, reverb, shriek, stft_filter, sweep_band, t_axis, thump, voice, vowel_path, write_ogg)

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "mobs", "src", "main", "resources", "assets", "blocksouls_mobs")


def sounds():
    """name -> list of (variant samples). Names map to blocksouls_mobs:mob/<mob>/<sound><n>."""
    S = {}

    def make(key, count, fn):
        S[key] = [fn(Gen(f"{key}{i}"), i) for i in range(count)]

    # Shambler: low wet groans, a grunt when hit, a long gurgling collapse.
    make("shambler/ambient", 3, lambda g, i: reverb(g, groan(g, 1.3 + 0.3 * i, [(0, 78 + 6 * i), (0.4, 92), (1, 64)],
                                                                [(0, "uh"), (0.5, "o"), (1, "u")], gurgle=0.5), 0.5, 0.18))
    make("shambler/hurt", 2, lambda g, i: groan(g, 0.35, [(0, 120 + 15 * i), (0.3, 160), (1, 110)], [(0, "a"), (1, "uh")],
                                                rough=0.5, shape=[(0, 0), (0.1, 1), (1, 0)]))
    make("shambler/death", 1, lambda g, i: reverb(g, groan(g, 1.8, [(0, 115), (0.3, 95), (1, 42)],
                                                            [(0, "a"), (0.4, "o"), (1, "u")], gurgle=1.0, rough=0.5,
                                                            shape=[(0, 0), (0.05, 1), (0.7, 0.7), (1, 0)]), 0.7, 0.2))

    # Crawler: chittering clicks over a hissing breath; a thin shriek; a whoosh when it leaps.
    def crawler_ambient(g, i):
        d = 0.9 + 0.2 * i
        n = int(d * SR)
        breath = band(g.noise(n), 1500, 6000) * env(n, shape=[(0, 0), (0.3, 1), (0.6, 0.4), (1, 0)])
        return mix((clicks(g, d, 28 + 6 * i), 1.0), (breath, 0.35),
                   (groan(g, d, [(0, 210), (1, 180)], [(0, "i"), (1, "e")], scale=1.2, rough=0.6, sub=0), 0.25))
    make("crawler/ambient", 3, crawler_ambient)
    make("crawler/hurt", 2, lambda g, i: shriek(g, 0.28, [(0, 520 + 60 * i), (0.4, 700), (1, 480)], [(0, "e"), (1, "i")],
                                                shape=[(0, 0), (0.08, 1), (1, 0)]))
    make("crawler/death", 1, lambda g, i: mix((shriek(g, 0.8, [(0, 620), (0.3, 540), (1, 220)], [(0, "i"), (0.5, "e"), (1, "uh")],
                                                       shape=[(0, 0), (0.05, 1), (1, 0)]), 1.0),
                                               (clicks(g, 0.8, 18) * env(int(0.8 * SR), shape=[(0, 1), (1, 0)]), 0.5)))
    make("crawler/leap", 2, lambda g, i: sweep_band(g.noise(int(0.4 * SR)), [(0, 700), (1, 2600 + 300 * i)],
                                                    [(0, 2000), (1, 7000)], 0.4) * env(int(0.4 * SR), shape=[(0, 0), (0.25, 1), (1, 0)]))

    # Screamer: a rattling wheeze at rest; the scream is two detuned, distorted voices tearing from "aa" to "ee".
    def wheeze(g, i):
        d = 1.4
        n = int(d * SR)
        air = band(g.noise(n), 600 + 200 * i, 4500) * env(n, shape=[(0, 0), (0.35, 1), (0.5, 0.3), (0.85, 0.8), (1, 0)])
        rattle = voice(g, curve([(0, 38), (1, 30)], n), rough=1.0, breath=0) * env(n, shape=[(0, 0), (0.4, 0.6), (0.5, 0), (1, 0)])
        whine = groan(g, d, [(0, 420), (1, 380)], [(0, "i"), (1, "i")], scale=1.2, rough=0.2, sub=0) * \
            env(n, shape=[(0, 0), (0.6, 0), (0.8, 1), (1, 0)])
        return reverb(g, mix((air, 1.0), (band(rattle, 100, 1500), 0.4), (whine, 0.25)), 0.5, 0.2)
    make("screamer/ambient", 2, wheeze)
    make("screamer/scream", 2, lambda g, i: reverb(g, shriek(g, 1.1, [(0, 380 + 40 * i), (0.12, 760), (0.6, 840), (1, 600)],
                                                                [(0, "a"), (0.3, "ae"), (0.7, "e"), (1, "i")], scale=1.1,
                                                                rough=0.45, dist=4.0, shape=[(0, 0), (0.08, 1), (0.8, 0.9), (1, 0)]),
                                                     1.0, 0.3))
    make("screamer/hurt", 2, lambda g, i: shriek(g, 0.3, [(0, 680 + 50 * i), (1, 520)], [(0, "a"), (1, "e")], dist=3,
                                                 shape=[(0, 0), (0.06, 1), (1, 0)]))
    make("screamer/death", 1, lambda g, i: reverb(g, shriek(g, 1.4, [(0, 820), (0.3, 640), (1, 160)],
                                                             [(0, "i"), (0.5, "a"), (1, "u")], dist=3,
                                                             shape=[(0, 0), (0.04, 1), (0.6, 0.6), (1, 0)]), 0.9, 0.3))

    # Bloater: gurgling bubbles and a wet moan; a rising pressurized hiss; the burst is a thump, a tearing splash
    # and spatter raining down.
    make("bloater/ambient", 2, lambda g, i: mix((bubbles(g, 1.3, 16, 90, 260), 1.0),
                                                (groan(g, 1.3, [(0, 70), (1, 60)], [(0, "o"), (1, "u")], scale=0.7, gurgle=1.2), 0.6)))

    def hiss(g, i):
        d = 1.6
        n = int(d * SR)
        h = sweep_band(g.noise(n), [(0, 900), (1, 3200)], [(0, 3000), (1, 9000)], d) * env(n, shape=[(0, 0), (0.15, 0.5), (0.9, 1), (1, 0)])
        creak = band(voice(g, curve([(0, 30), (1, 70)], n), rough=1.0, breath=0), 200, 1800) * env(n, shape=[(0, 0), (0.4, 0.3), (1, 0.8)])
        return mix((h, 1.0), (creak, 0.35), (bubbles(g, d, 25, 150, 400), 0.4))
    make("bloater/hiss", 1, hiss)

    def burst(g, i):
        d = 1.2
        n = int(d * SR)
        boom = thump(d, 160, 45, 0.18)
        tear = band(g.noise(n), 150, 3500) * np.exp(-t_axis(d) / 0.08)
        spatter = bubbles(g, d, 70, 300, 1400) * env(n, shape=[(0, 0), (0.08, 1), (1, 0)])
        drops = clicks(g, d, 40, 800, 4000, decay=0.01) * env(n, shape=[(0, 0), (0.15, 1), (1, 0)])
        return reverb(g, mix((boom, 1.0), (tear, 0.8), (spatter, 0.45), (drops, 0.3)), 0.8, 0.22)
    make("bloater/burst", 2, burst)
    make("bloater/hurt", 2, lambda g, i: mix((bubbles(g, 0.35, 60, 120, 500), 0.8),
                                             (groan(g, 0.35, [(0, 95), (1, 80)], [(0, "uh"), (1, "o")], scale=0.75, gurgle=1,
                                                    shape=[(0, 0), (0.1, 1), (1, 0)]), 1.0)))
    make("bloater/death", 1, lambda g, i: mix((groan(g, 1.2, [(0, 90), (1, 40)], [(0, "o"), (1, "u")], scale=0.7, gurgle=1.5,
                                                     shape=[(0, 0), (0.05, 1), (1, 0)]), 1.0),
                                              (bubbles(g, 1.2, 30, 70, 200), 0.6)))

    # Brute: a chesty growl and snort; the windup is a rising roar; the slam is a ground-shaking impact.
    def snort(g, n):
        s = band(g.noise(int(0.18 * SR)), 300, 2500) * env(int(0.18 * SR), shape=[(0, 0), (0.1, 1), (1, 0)])
        out = np.zeros(n)
        out[-len(s) - int(0.1 * SR):-int(0.1 * SR)] += s
        return out
    make("brute/ambient", 2, lambda g, i: reverb(g, mix((growl(g, 1.6, [(0, 48), (0.5, 56), (1, 44)], scale=0.55), 1.0),
                                                        (snort(g, int(1.6 * SR)), 0.5 if i == 0 else 0.0)), 0.6, 0.15))
    make("brute/windup", 1, lambda g, i: reverb(g, drive(growl(g, 1.05, [(0, 55), (0.8, 120), (1, 115)], scale=0.6, sub=0.4,
                                                               vowels=((0, "uh"), (0.6, "a"), (1, "a")),
                                                               shape=[(0, 0), (0.6, 0.8), (0.95, 1), (1, 0)]) * 1.5, 2.0), 0.7, 0.2))

    def slam(g, i):
        d = 1.5
        n = int(d * SR)
        boom = thump(d, 110, 32, 0.35)
        crunch = band(g.noise(n), 200, 5000) * np.exp(-t_axis(d) / 0.05)
        rubble = clicks(g, d, 45, 400, 3000, decay=0.012) * env(n, shape=[(0, 0), (0.05, 1), (0.5, 0.3), (1, 0)])
        rumble = band(g.noise(n), 25, 120) * env(n, shape=[(0, 0), (0.03, 1), (1, 0)])
        return reverb(g, mix((boom, 1.0), (crunch, 0.6), (rubble, 0.35), (rumble, 0.6)), 1.0, 0.2)
    make("brute/slam", 2, slam)
    make("brute/hurt", 2, lambda g, i: growl(g, 0.45, [(0, 70 + 8 * i), (0.3, 90), (1, 60)], scale=0.6, am=12,
                                             vowels=((0, "a"), (1, "uh")), shape=[(0, 0), (0.1, 1), (1, 0)]))
    make("brute/death", 1, lambda g, i: reverb(g, mix((growl(g, 2.0, [(0, 90), (0.4, 70), (1, 30)], scale=0.55,
                                                             vowels=((0, "a"), (0.5, "o"), (1, "u")),
                                                             shape=[(0, 0), (0.05, 1), (0.7, 0.6), (1, 0)]), 1.0),
                                                       (np.concatenate([np.zeros(int(1.5 * SR)), thump(0.6, 90, 35, 0.15)]), 0.7)),
                                                   0.8, 0.2))

    # Mutant Brute: the brute's voice, rougher and doubled, with a radiation crackle under everything.
    def crackle(g, d, rate=14):
        return clicks(g, d, rate, 2500, 9000, decay=0.0008, jitter=1.9)
    make("mutant_brute/ambient", 2, lambda g, i: reverb(g, mix((growl(g, 1.8, [(0, 42), (0.5, 50), (1, 40)], scale=0.5, rough=0.8), 1.0),
                                                               (crackle(g, 1.8), 0.35)), 0.7, 0.15))
    make("mutant_brute/roar", 1, lambda g, i: reverb(g, mix((drive(growl(g, 1.6, [(0, 60), (0.3, 130), (0.8, 120), (1, 70)], scale=0.55,
                                                                         sub=0.8, rough=0.8, vowels=((0, "o"), (0.3, "a"), (1, "a")),
                                                                         shape=[(0, 0), (0.15, 1), (0.8, 0.9), (1, 0)]) * 2, 2.5), 1.0),
                                                            (shriek(g, 1.6, [(0, 240), (0.3, 300), (1, 200)], [(0, "a"), (1, "a")], scale=0.8,
                                                                    shape=[(0, 0), (0.2, 1), (0.8, 0.8), (1, 0)]), 0.35),
                                                            (crackle(g, 1.6, 30), 0.3)), 1.1, 0.25))
    make("mutant_brute/throw", 1, lambda g, i: mix((growl(g, 0.6, [(0, 80), (0.3, 110), (1, 70)], scale=0.6, am=10,
                                                          vowels=((0, "uh"), (1, "a")), shape=[(0, 0), (0.15, 1), (1, 0)]), 1.0),
                                                   (sweep_band(g.noise(int(0.6 * SR)), [(0, 300), (1, 1500)], [(0, 1500), (1, 5000)], 0.6) *
                                                    env(int(0.6 * SR), shape=[(0, 0), (0.4, 0), (0.6, 1), (1, 0)]), 0.6)))
    make("mutant_brute/hurt", 2, lambda g, i: mix((growl(g, 0.5, [(0, 65 + 8 * i), (0.3, 85), (1, 55)], scale=0.55, am=14,
                                                         vowels=((0, "a"), (1, "uh")), shape=[(0, 0), (0.1, 1), (1, 0)]), 1.0),
                                                  (crackle(g, 0.5, 40), 0.4)))
    make("mutant_brute/death", 1, lambda g, i: reverb(g, mix((growl(g, 2.4, [(0, 100), (0.4, 70), (1, 25)], scale=0.5, rough=0.9,
                                                                    vowels=((0, "a"), (0.5, "o"), (1, "u")),
                                                                    shape=[(0, 0), (0.05, 1), (0.7, 0.6), (1, 0)]), 1.0),
                                                             (crackle(g, 2.4, 25) * env(int(2.4 * SR), shape=[(0, 0.3), (0.6, 1), (1, 0)]), 0.5)),
                                                         0.9, 0.2))

    # Infected Piglin: sick, nasal grunts that catch wetly in the throat; a squeal; a gurgling death.
    def grunts(g, i):
        d = 1.0 + 0.2 * i
        n = int(d * SR)
        f0 = 150 + 20 * i
        y = groan(g, d, [(0, f0), (1, f0 * 0.9)], [(0, "uh"), (0.5, "o"), (1, "uh")], scale=0.95, rough=0.6, sub=0.2,
                  gurgle=0.8)
        pulses = np.zeros(n)
        for k, start in enumerate((0.0, 0.3, 0.55) if i == 0 else (0.0, 0.4)):
            a, b = int(start * SR), int((start + 0.22) * SR)
            pulses[a:min(b, n)] = np.sin(np.linspace(0, np.pi, min(b, n) - a)) * (1 - 0.15 * k)
        return y * pulses
    make("infected_piglin/ambient", 2, grunts)
    make("infected_piglin/hurt", 2, lambda g, i: shriek(g, 0.35, [(0, 420 + 40 * i), (0.3, 560), (1, 380)], [(0, "e"), (1, "a")],
                                                        scale=0.95, dist=2.0, shape=[(0, 0), (0.08, 1), (1, 0)]))
    make("infected_piglin/death", 1, lambda g, i: mix((shriek(g, 1.1, [(0, 520), (0.3, 420), (1, 130)], [(0, "e"), (0.5, "a"), (1, "u")],
                                                              scale=0.95, dist=2.0, shape=[(0, 0), (0.05, 1), (0.7, 0.5), (1, 0)]), 1.0),
                                                      (bubbles(g, 1.1, 25, 100, 300), 0.5)))

    # Ash Hound: a low rumbling growl over crackling embers; a sharp yelp; a whine that burns out.
    def embers(g, d, rate=22):
        return clicks(g, d, rate, 1200, 7000, decay=0.0015, jitter=1.9)
    make("ash_hound/ambient", 2, lambda g, i: mix((growl(g, 1.3, [(0, 85 + 10 * i), (0.5, 100), (1, 80)], scale=0.75, am=28,
                                                         vowels=((0, "u"), (1, "o"))), 1.0), (embers(g, 1.3), 0.4)))
    make("ash_hound/hurt", 2, lambda g, i: mix((shriek(g, 0.28, [(0, 760 + 60 * i), (1, 520)], [(0, "a"), (1, "e")], scale=0.9,
                                                       dist=1.6, shape=[(0, 0), (0.05, 1), (1, 0)]), 1.0), (embers(g, 0.28, 50), 0.3)))
    make("ash_hound/death", 1, lambda g, i: mix((shriek(g, 1.2, [(0, 700), (0.3, 620), (1, 260)], [(0, "i"), (0.5, "u"), (1, "u")],
                                                        scale=0.9, dist=1.4, shape=[(0, 0), (0.05, 1), (0.6, 0.5), (1, 0)]), 1.0),
                                                (embers(g, 1.2, 35) * env(int(1.2 * SR), shape=[(0, 0.4), (0.5, 1), (1, 0)]), 0.6)))

    # Dread howl: far-off and lonely, a gliding "oo" with vibrato, dulled by distance and smeared by echo.
    def howl(g, i):
        d = 3.2
        n = int(d * SR)
        f0 = curve([(0, 260 + 30 * i), (0.25, 430 + 20 * i), (0.7, 400), (1, 250)], n)
        f0 *= 1 + 0.012 * np.sin(2 * np.pi * 5.2 * t_axis(d)[:n])
        src = voice(g, f0, rough=0.08, breath=0.05)
        y = stft_filter(src, vowel_path([(0, "u"), (0.3, "o"), (1, "u")], scale=1.0, tilt=1.4, dur=d))
        y *= env(n, shape=[(0, 0), (0.15, 1), (0.7, 0.85), (1, 0)])
        return band(reverb(g, y, 2.2, 0.55, damp=1800), 120, 2200)
    make("dread/howl", 2, howl)

    # Something heavy hitting a barricade: a wooden bang with ringing planks and a splintering crack.
    def bang(g, i):
        d = 0.45
        n = int(d * SR)
        hit = band(g.noise(n), 80, 4000) * np.exp(-t_axis(d) / 0.02)
        body = resonances(g, d, [(170 + 20 * i, 1.0), (390, 0.6), (820 + 40 * i, 0.4), (1500, 0.2)], 0.09)
        splinter = clicks(g, d, 90, 1500, 6000, decay=0.003) * np.exp(-t_axis(d)[:n] / 0.08)
        return mix((hit, 0.8), (body, 1.0), (splinter, 0.35))
    make("wave_mob/break_hit", 2, bang)
    return S


# Event id -> sound key. Subtitles keep the Stage 1 keys.
EVENTS = {
    "entity.shambler.ambient": "shambler/ambient", "entity.shambler.hurt": "shambler/hurt", "entity.shambler.death": "shambler/death",
    "entity.crawler.ambient": "crawler/ambient", "entity.crawler.hurt": "crawler/hurt", "entity.crawler.death": "crawler/death",
    "entity.crawler.leap": "crawler/leap",
    "entity.screamer.ambient": "screamer/ambient", "entity.screamer.scream": "screamer/scream", "entity.screamer.hurt": "screamer/hurt",
    "entity.screamer.death": "screamer/death",
    "entity.bloater.ambient": "bloater/ambient", "entity.bloater.hiss": "bloater/hiss", "entity.bloater.burst": "bloater/burst",
    "entity.bloater.hurt": "bloater/hurt", "entity.bloater.death": "bloater/death",
    "entity.brute.ambient": "brute/ambient", "entity.brute.slam": "brute/slam", "entity.brute.windup": "brute/windup",
    "entity.brute.hurt": "brute/hurt", "entity.brute.death": "brute/death",
    "entity.mutant_brute.ambient": "mutant_brute/ambient", "entity.mutant_brute.roar": "mutant_brute/roar",
    "entity.mutant_brute.throw": "mutant_brute/throw", "entity.mutant_brute.hurt": "mutant_brute/hurt",
    "entity.mutant_brute.death": "mutant_brute/death",
    "entity.infected_piglin.ambient": "infected_piglin/ambient", "entity.infected_piglin.hurt": "infected_piglin/hurt",
    "entity.infected_piglin.death": "infected_piglin/death",
    "entity.ash_hound.ambient": "ash_hound/ambient", "entity.ash_hound.hurt": "ash_hound/hurt", "entity.ash_hound.death": "ash_hound/death",
    "ambient.dread.howl": "dread/howl",
    "entity.wave_mob.break_hit": "wave_mob/break_hit",
}
# Per-sound peak level: ambient chatter sits under combat sounds; telegraphs and impacts are loud.
PEAK = {"ambient": 0.6, "hurt": 0.75, "death": 0.8, "scream": 0.95, "slam": 0.95, "burst": 0.95, "roar": 0.95,
        "windup": 0.9, "hiss": 0.8, "howl": 0.85, "leap": 0.7, "throw": 0.8, "break_hit": 0.85}


# Telegraphs and far-off cues carry further than the vanilla 16 blocks, so a horde's tells read from a distance.
RANGE = {"scream": 32, "roar": 32, "slam": 24, "windup": 24, "burst": 24, "howl": 32}


def main():
    S = sounds()
    out_dir = os.path.join(ASSETS, "sounds", "mob")
    for key, variants in S.items():
        peak = PEAK[key.split("/")[1]]
        for i, x in enumerate(variants):
            write_ogg(finish(x, peak), os.path.join(out_dir, f"{key}{i + 1}.ogg"))
    sounds_json = {}
    for event, key in EVENTS.items():
        sounds_json[event] = {
            "subtitle": f"subtitles.blocksouls_mobs.{event}",
            "sounds": [f"blocksouls_mobs:mob/{key}{i + 1}" for i in range(len(S[key]))],
        }
        reach = RANGE.get(key.split("/")[1])
        if reach:
            sounds_json[event]["sounds"] = [{"name": n, "attenuation_distance": reach} for n in sounds_json[event]["sounds"]]
    with open(os.path.join(ASSETS, "sounds.json"), "w") as f:
        json.dump(sounds_json, f, indent=2)
        f.write("\n")
    print(f"{sum(len(v) for v in S.values())} sounds for {len(EVENTS)} events")


if __name__ == "__main__":
    main()
