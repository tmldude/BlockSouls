#!/usr/bin/env python3
"""Synthesizes every gun sound and writes them as .ogg, plus the guns module's sounds.json.

    python3 tools/assets/gun_sounds.py      # needs numpy and ffmpeg with libvorbis

No samples. A shot is layered: a bright supersonic crack (a few ms of high noise), the muzzle blast (band-passed
noise with a fast decay, driven for punch), a pitched low thump for weight, the action's metallic clack, and a short
filtered tail standing in for the room. Each weapon gets its own balance of those layers so they read apart in a
fight. Helpers live in synth.py. Output: guns/src/client/resources/assets/blocksouls_guns/sounds/gun/...
"""

import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import (SR, Gen, at, band, bell, clicks, curve, decay, drive, emit, env, metal, mix,  # noqa: E402
                   noise_hit, pad_to, resonances, reverb, saw, sine, sweep_band, t_axis, thump, whoosh)

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "guns", "src", "client", "resources", "assets", "blocksouls_guns")


def shot(g, dur, crack, blast_lo, blast_hi, blast_tau, boom_hi, boom_lo, boom_tau, mech, tail_tau, tail_hi, punch=2.0,
         layers=(0.5, 1.0, 0.8, 0.25, 0.35)):
    """One gunshot. layers = gains of (crack, blast, boom, mechanism, tail)."""
    n = int(dur * SR)
    c = band(g.noise(n), 3000, 14000) * decay(dur, crack)
    blast = noise_hit(g, dur, blast_lo, blast_hi, blast_tau)
    boom = thump(dur, boom_hi, boom_lo, boom_tau)
    clack = pad_to(metal(g, 0.12, mech, 0.012) + noise_hit(g, 0.12, 2000, 8000, 0.004), dur)
    tail = band(g.noise(n), 150, tail_hi) * env(n, shape=[(0, 0), (0.02, 1), (1, 0)]) * decay(dur, tail_tau)
    body = mix((c, layers[0]), (blast, layers[1]), (boom, layers[2]), (clack, layers[3]), (tail, layers[4]))
    return drive(body / (np.max(np.abs(body)) + 1e-9), punch)


def sounds():
    S = {}

    def make(key, count, fn):
        S[key] = [fn(Gen(f"gun/{key}{i}"), i) for i in range(count)]

    # Starter pistol: light and snappy, a thin crack with little weight behind it.
    make("starter_pistol/fire", 3, lambda g, i: shot(g, 0.45, 0.003, 400 + 40 * i, 5000, 0.03, 220, 90, 0.04, 3300 + 150 * i,
                                                     0.09, 3500, punch=2.2, layers=(0.55, 1.0, 0.45, 0.3, 0.25)))
    # Hand cannon: the big revolver. A deep boom, a ringing cylinder and a long roll off the walls.
    make("hand_cannon/fire", 3, lambda g, i: reverb(g, shot(g, 1.0, 0.004, 150, 3200, 0.08, 140 - 8 * i, 42, 0.13, 2100 + 90 * i,
                                                            0.28, 2400, punch=3.0, layers=(0.45, 1.0, 1.0, 0.35, 0.4)),
                                                    0.9, 0.18, damp=2500))
    # Auto rifle: tight and short so a stream of them doesn't smear.
    make("auto_rifle/fire", 3, lambda g, i: shot(g, 0.32, 0.0025, 300 + 30 * i, 6000, 0.022, 180 + 10 * i, 70, 0.035,
                                                 4200 + 200 * i, 0.06, 4000, punch=2.5, layers=(0.6, 1.0, 0.6, 0.2, 0.2)))

    # Shotgun: a wide, low blast with a spray of pellet hiss, then the pump racking.
    def shotgun(g, i):
        d = 1.1
        blast = shot(g, d, 0.005, 90, 2600, 0.11, 110, 36, 0.16, 1800, 0.3, 2000, punch=3.2, layers=(0.4, 1.0, 1.0, 0.0, 0.45))
        spray = band(g.noise(int(d * SR)), 2500, 9000) * decay(d, 0.06)
        rack = at(mix((pad_to(metal(g, 0.08, 1600, 0.01), 0.3), 1.0),
                      (at(metal(g, 0.1, 1250, 0.014), 0.13, 0.3), 1.0),
                      (at(noise_hit(g, 0.15, 800, 5000, 0.03), 0.02, 0.3), 0.5)), 0.6 + 0.03 * i, d)
        return reverb(g, mix((blast, 1.0), (spray, 0.25), (rack, 0.25)), 0.8, 0.15, damp=2200)
    make("shotgun/fire", 2, shotgun)

    # Sniper rifle: a vicious supersonic snap, a heavy boom, and an echo rolling away down the valley.
    def sniper(g, i):
        d = 1.9
        n = int(d * SR)
        body = shot(g, d, 0.0015, 200, 7000, 0.05, 130, 38, 0.12, 2600, 0.5, 1800, punch=3.0, layers=(1.0, 1.0, 0.9, 0.2, 0.25))
        echo = band(g.noise(n), 120, 900) * env(n, shape=[(0, 0), (0.12, 0), (0.25, 1), (1, 0)]) * (0.6 + 0.4 * g.smooth_noise(n, 5))
        bolt = at(mix((pad_to(metal(g, 0.1, 1400, 0.012), 0.35), 1.0), (at(metal(g, 0.1, 1900, 0.012), 0.22, 0.35), 0.8)),
                  1.2 + 0.05 * i, d)
        return reverb(g, mix((body, 1.0), (echo, 0.2), (bolt, 0.15)), 1.2, 0.2, damp=1800)
    make("sniper_rifle/fire", 2, sniper)

    # Dry click of a hammer on an empty chamber.
    make("empty", 2, lambda g, i: mix((metal(g, 0.15, 3200 + 300 * i, 0.008, gains=(1, 0.5, 0.3, 0.2)), 1.0),
                                      (noise_hit(g, 0.15, 3000, 9000, 0.002), 0.6)))

    # Reload: the magazine drops free (a click and a slide out); then it seats (a clack) and the slide racks.
    def reload_start(g, i):
        d = 0.45
        click = pad_to(metal(g, 0.08, 2800 + 200 * i, 0.007) + noise_hit(g, 0.08, 2000, 8000, 0.003), d)
        slide = at(whoosh(g, 0.22, [(0, 1500), (1, 900)], [(0, 6000), (1, 3500)]), 0.06, d)
        rattle = at(clicks(g, 0.2, 60, 2000, 7000, decay=0.002) * decay(0.2, 0.06), 0.1, d)
        return mix((click, 1.0), (slide, 0.35), (rattle, 0.3))
    make("reload_start", 2, reload_start)

    def reload_end(g, i):
        d = 0.55
        seat = pad_to(mix((thump(0.12, 300, 140, 0.02), 0.7), (metal(g, 0.12, 1500 + 100 * i, 0.012), 0.8),
                          (noise_hit(g, 0.12, 600, 6000, 0.008), 0.6)), d)
        back = at(metal(g, 0.1, 2300, 0.01) + noise_hit(g, 0.1, 1500, 7000, 0.004), 0.22, d)
        home = at(mix((metal(g, 0.15, 1700 + 80 * i, 0.016), 1.0), (noise_hit(g, 0.15, 800, 6000, 0.006), 0.8)), 0.33, d)
        return mix((seat, 1.0), (back, 0.6), (home, 0.9))
    make("reload_end", 2, reload_end)

    # Ricochet: a ping off stone and the bullet whining away.
    def ricochet(g, i):
        d = 0.6
        n = int(d * SR)
        f = curve([(0, 3400 + 300 * i), (1, 1300 + 200 * i)], n) * (1 + 0.02 * np.sin(2 * np.pi * 30 * t_axis(d)[:n]))
        whine = sine(f, n) * env(n, shape=[(0, 0), (0.03, 1), (1, 0)])
        return mix((whine, 0.8), (noise_hit(g, d, 1500, 9000, 0.01), 0.7), (metal(g, d, 2600, 0.03), 0.3))
    make("ricochet", 3, ricochet)

    # Explosive Rounds: a small, sharp pop with a puff of debris.
    def explosive(g, i):
        d = 0.8
        n = int(d * SR)
        return drive(mix((thump(d, 120, 38, 0.1), 1.0), (noise_hit(g, d, 80, 3500, 0.12), 0.9),
                         (clicks(g, d, 50, 1000, 6000, decay=0.003) * env(n, shape=[(0, 0), (0.05, 1), (1, 0)]), 0.3)), 2.0)
    make("explosive_round", 2, explosive)

    # Ammo pickup: a handful of cartridges clinking together.
    def pickup(g, i):
        d = 0.35
        out = np.zeros(int(d * SR))
        for k, t0 in enumerate((0.0, 0.05 + 0.01 * i, 0.11, 0.16 - 0.02 * i)):
            out += at(metal(g, 0.15, 2500 + 700 * g.rng.random(), 0.02, gains=(1, 0.5, 0.3, 0.2)) * (1 - 0.15 * k), t0, d)
        return out
    make("ammo_pickup", 2, pickup)

    # Precision hit: a clean, bright tick only the shooter hears, on top of the hit marker.
    make("headshot", 1, lambda g, i: mix((bell(g, 0.18, 2600, 0.04, bright=0.3), 1.0), (noise_hit(g, 0.18, 4000, 12000, 0.002), 0.4)))

    # Outlaw: the cylinder spins up (accelerating ratchet) and snaps shut.
    def outlaw(g, i):
        d = 0.6
        out = np.zeros(int(d * SR))
        t0, gap = 0.0, 0.07
        while t0 < 0.42:
            out += at(metal(g, 0.04, 3600, 0.004, gains=(1, 0.4, 0.2, 0.1)), t0, d)
            t0 += gap
            gap = max(0.022, gap * 0.82)
        out += at(mix((metal(g, 0.18, 1900, 0.025), 1.0), (bell(g, 0.18, 1800, 0.05, bright=0.6), 0.6)), 0.45, d)
        return out
    make("perk/outlaw", 1, outlaw)

    # Rampage: a short, angry rising growl of detuned saws (the code raises the pitch with each stack).
    def rampage(g, i):
        d = 0.55
        n = int(d * SR)
        f = curve([(0, 70), (0.7, 110), (1, 105)], n)
        tone = sum(saw(f * r, n, k) for k, r in enumerate((1.0, 1.007, 1.5, 2.01)))
        tone = band(tone, 60, 2500) * env(n, shape=[(0, 0), (0.15, 1), (0.8, 0.9), (1, 0)])
        return drive(mix((tone, 1.0), (thump(d, 140, 50, 0.08), 0.6)), 3.0)
    make("perk/rampage", 1, rampage)

    # Vorpal: a thin blade-like hiss slicing down into a deep gong.
    def vorpal(g, i):
        d = 1.0
        slice_ = whoosh(g, 0.25, [(0, 5000), (1, 1500)], [(0, 12000), (1, 5000)], ((0, 0), (0.2, 1), (1, 0)))
        gong = at(metal(g, 0.85, 180, 0.35, ratios=(1.0, 2.0, 2.98, 4.1), gains=(1, 0.5, 0.35, 0.2)), 0.15, d)
        return reverb(g, mix((pad_to(slice_, d), 0.6), (gong, 1.0)), 0.6, 0.2)
    make("perk/vorpal", 1, vorpal)

    # Kill confirm: a short two-note tick only the shooter hears, lower than the headshot tick.
    make("kill_confirm", 1, lambda g, i: mix((at(bell(g, 0.14, 1320, 0.03, bright=0.3), 0.0, 0.3), 1.0),
                                             (at(bell(g, 0.2, 880, 0.05, bright=0.3), 0.07, 0.3), 0.9),
                                             (noise_hit(g, 0.3, 3000, 10000, 0.002), 0.3)))

    # Kindling Engine venting: a hiss of steam with a metallic ping as the receiver cools.
    def vent(g, i):
        d = 1.2
        n = int(d * SR)
        hiss = band(g.noise(n), 1500, 9000) * env(n, shape=[(0, 0), (0.03, 1), (0.6, 0.5), (1, 0)])
        return mix((hiss, 1.0), (at(metal(g, 0.4, 2100, 0.05), 0.1, d), 0.5), (at(metal(g, 0.4, 1700, 0.05), 0.5, d), 0.4))
    make("exotic/vent", 1, vent)

    # Tether Lance: the rocket bursts (a blast) and the web snaps taut (a twang).
    def tether(g, i):
        d = 1.0
        blast = drive(mix((thump(d, 150, 40, 0.1), 1.0), (noise_hit(g, d, 80, 4000, 0.1), 0.9)), 2.0)
        n = int(d * SR)
        twang = sine(curve([(0, 700), (0.2, 350), (1, 300)], n), n) * env(n, shape=[(0, 0), (0.1, 0), (0.12, 1), (1, 0)])
        return reverb(g, mix((blast, 1.0), (twang, 0.5)), 0.7, 0.15)
    make("exotic/tether", 1, tether)

    # Lantern Eye: a glassy chime as targets light up.
    make("exotic/mark", 1, lambda g, i: reverb(g, mix((bell(g, 0.6, 1760, 0.15, bright=0.6), 1.0),
                                                       (at(bell(g, 0.5, 2640, 0.12, bright=0.6), 0.1, 0.7), 0.7)), 0.6, 0.3))
    # Rare drop: two rising bell notes, so a good gun on the ground is heard before it's seen.
    def drop_rare(g, i):
        d = 1.0
        notes = mix((pad_to(bell(g, 0.6, 880, 0.18, bright=0.5), d), 1.0), (at(bell(g, 0.7, 1320, 0.22, bright=0.5), 0.12, d), 0.9))
        return reverb(g, notes, 0.6, 0.25)
    make("drop/rare", 1, drop_rare)

    # Legendary drop: a bright three-note arpeggio over a low swell and a shimmer of high noise.
    def drop_legendary(g, i):
        d = 1.6
        n = int(d * SR)
        arp = sum(at(bell(g, 0.9, f, 0.3, bright=0.7), t0, d) * gain
                  for f, t0, gain in ((660, 0.0, 0.9), (990, 0.11, 0.9), (1320, 0.22, 1.0)))
        swell = sine(110, n) * env(n, shape=[(0, 0), (0.15, 1), (1, 0)]) * 0.35
        shimmer = band(g.noise(n), 6000, 14000) * env(n, shape=[(0, 0), (0.2, 1), (1, 0)]) * 0.08
        return reverb(g, mix((arp, 1.0), (swell, 1.0), (shimmer, 1.0)), 0.8, 0.3)
    make("drop/legendary", 1, drop_legendary)
    return S


# Event id -> (sound key, subtitle). The existing events keep their Stage 1 subtitle keys.
SUB = "subtitles.blocksouls_guns."
EVENTS = {
    "gun.starter_pistol.fire": ("starter_pistol/fire", SUB + "gun.fire"),
    "gun.hand_cannon.fire": ("hand_cannon/fire", SUB + "gun.fire"),
    "gun.auto_rifle.fire": ("auto_rifle/fire", SUB + "gun.fire"),
    "gun.shotgun.fire": ("shotgun/fire", SUB + "gun.fire"),
    "gun.sniper_rifle.fire": ("sniper_rifle/fire", SUB + "gun.fire"),
    "gun.empty": ("empty", SUB + "gun.empty"),
    "gun.reload_start": ("reload_start", SUB + "gun.reload"),
    "gun.reload_end": ("reload_end", SUB + "gun.reload"),
    "gun.ricochet": ("ricochet", SUB + "gun.ricochet"),
    "gun.explosive_round": ("explosive_round", SUB + "gun.explosive_round"),
    "gun.ammo_pickup": ("ammo_pickup", SUB + "gun.ammo_pickup"),
    "gun.headshot": ("headshot", None),
    "gun.perk.outlaw": ("perk/outlaw", SUB + "gun.perk.outlaw"),
    "gun.perk.rampage": ("perk/rampage", SUB + "gun.perk.rampage"),
    "gun.perk.vorpal": ("perk/vorpal", SUB + "gun.perk.vorpal"),
    "gun.kill_confirm": ("kill_confirm", None),
    "gun.exotic.vent": ("exotic/vent", SUB + "gun.exotic.vent"),
    "gun.exotic.tether": ("exotic/tether", SUB + "gun.exotic.tether"),
    "gun.exotic.mark": ("exotic/mark", SUB + "gun.exotic.mark"),
    "gun.drop.rare": ("drop/rare", SUB + "gun.drop"),
    "gun.drop.legendary": ("drop/legendary", SUB + "gun.drop"),
}
# Shots are the loudest thing a player makes (the heavy guns most of all); handling sounds sit well under them.
PEAKS = {"starter_pistol/fire": 0.8, "auto_rifle/fire": 0.82, "hand_cannon/fire": 0.92, "shotgun/fire": 0.92,
         "sniper_rifle/fire": 0.92, "explosive_round": 0.85, "ricochet": 0.45, "empty": 0.6, "reload_start": 0.6, "reload_end": 0.65,
         "ammo_pickup": 0.5, "headshot": 0.55, "outlaw": 0.7, "rampage": 0.7, "vorpal": 0.75, "kill_confirm": 0.5,
         "vent": 0.7, "tether": 0.85, "mark": 0.6, "drop/rare": 0.6, "drop/legendary": 0.7}


def main():
    S = sounds()
    count = emit(ASSETS, "blocksouls_guns", "gun", S, EVENTS, lambda key: PEAKS.get(key) or PEAKS[key.split("/")[-1]])
    print(f"{count} sounds for {len(EVENTS)} events")


if __name__ == "__main__":
    main()
