#!/usr/bin/env python3
"""Synthesizes the kart's sounds and writes them as .ogg, plus the vehicles module's sounds.json.

    python3 tools/assets/vehicle_sounds.py  # needs numpy and ffmpeg with libvorbis

No samples. The engine is a small two-stroke: a train of combustion pulses (each a short decaying burst through the
exhaust's resonances) with intake hiss and a little roughness, rendered as a seamless loop. KartEngineSound plays it
at pitch 0.5 when idling and up to 1.6 flat out, so the loop is tuned for pitch 1.0 = mid revs. Helpers live in
synth.py. Output: vehicles/src/client/resources/assets/blocksouls_vehicles/sounds/kart/...

The horn (kart.horn) belongs to the kart-fuel-horn session; this script leaves any entry it isn't writing alone.
"""

import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import (SR, Gen, at, band, clicks, curve, decay, drive, emit, env, impact, loop_seam, metal, mix,  # noqa: E402
                   noise_hit, pad_to, resonances, reverb, sine, sweep_band, t_axis, thump, whoosh)

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "vehicles", "src", "client", "resources", "assets", "blocksouls_vehicles")

ENGINE_HZ = 52  # firing rate at pitch 1.0: 26 Hz idle, ~83 Hz flat out


def engine(g, dur, hz, fade=0.25):
    """Seamless engine loop: whole numbers of firing cycles, the tail cross-faded into the head."""
    total = dur + fade
    n = int(total * SR)
    period = SR / hz
    pulse_len = int(period * 1.6)
    pulses = np.zeros(n + pulse_len)
    k = 0
    while k * period < n:
        i = int(k * period + g.rng.normal(0, period * 0.02))
        amp = 0.8 + 0.2 * g.rng.random() * (1.5 if k % 7 == 3 else 1)
        burst = g.noise(pulse_len) * np.exp(-np.arange(pulse_len) / (period * 0.25))
        pulses[max(0, i):max(0, i) + pulse_len] += burst * amp
        k += 1
    pulses = pulses[:n]
    # The exhaust: a pipe resonance and a boxy muffler body.
    exhaust = band(pulses, 60, 900) + 0.4 * band(pulses, 900, 2500)
    tone = mix((sine(hz, n), 1.0), (sine(hz * 2, n), 0.5), (sine(hz * 3, n), 0.25))
    intake = band(g.noise(n), 1500, 5000) * (0.6 + 0.4 * np.sin(2 * np.pi * hz * t_axis(total)[:n]))
    rattle = clicks(g, total, hz * 0.5, 2500, 7000, decay=0.001)
    x = mix((exhaust, 1.0), (tone, 0.35), (intake, 0.12), (rattle, 0.08))
    x = drive(x / (np.max(np.abs(x)) + 1e-9), 1.8)
    return loop_seam(x, fade)


def sounds():
    S = {}

    def make(key, count, fn):
        S[key] = [fn(Gen(f"kart/{key}{i}"), i) for i in range(count)]

    make("engine", 1, lambda g, i: engine(g, 2.0, ENGINE_HZ))

    # Hop: a springy suspension boing and a plastic thunk.
    def hop(g, i):
        d = 0.4
        n = int(d * SR)
        boing = sine(curve([(0, 160), (0.3, 300), (1, 260)], n), n) * (1 + 0.3 * np.sin(2 * np.pi * 22 * t_axis(d)[:n])) * decay(d, 0.12)
        return mix((boing, 0.6), (thump(d, 220, 90, 0.04), 1.0), (noise_hit(g, d, 400, 3000, 0.01), 0.3))
    make("hop", 2, hop)

    # Boost: an exhaust pop and a rising turbo whoosh (the code pitches it up with the drift tier).
    def boost(g, i):
        d = 0.9
        n = int(d * SR)
        pop = mix((thump(0.2, 180, 60, 0.03), 1.0), (noise_hit(g, 0.2, 200, 4000, 0.02), 0.8))
        whine = sine(curve([(0, 900), (1, 2400)], n), n) * env(n, shape=[(0, 0), (0.2, 1), (1, 0)])
        rush = whoosh(g, d, [(0, 300), (1, 1500)], [(0, 2000), (1, 8000)], ((0, 0), (0.2, 1), (1, 0)))
        return mix((pad_to(pop, d), 1.0), (whine, 0.25), (rush, 0.8))
    make("boost", 2, boost)

    # Skid: rubber squealing on the road.
    def skid(g, i):
        d = 0.9
        n = int(d * SR)
        f = (1700 + 150 * i) * (1 + 0.03 * g.smooth_noise(n, 25))
        squeal = sum(sine(f * h, n, h) / h for h in (1, 2.02, 3.05)) * (0.6 + 0.4 * np.abs(g.smooth_noise(n, 30)))
        scrub = band(g.noise(n), 800, 4000) * (0.6 + 0.4 * g.smooth_noise(n, 40))
        return mix((squeal, 0.6), (scrub, 1.0)) * env(n, shape=[(0, 0), (0.05, 1), (0.7, 0.8), (1, 0)])
    make("skid", 2, skid)

    # Ram / crash: a body-panel crunch, a clank, and loose bits rattling.
    def crash(g, i):
        d = 0.9
        n = int(d * SR)
        crunch = noise_hit(g, d, 200, 6000, 0.06)
        clank = metal(g, d, 330 + 60 * i, 0.12, ratios=(1.0, 2.4, 4.1, 6.7), gains=(1, 0.7, 0.4, 0.2))
        bits = clicks(g, d, 40, 1500, 6000, decay=0.004) * env(n, shape=[(0, 0), (0.05, 1), (1, 0)])
        return drive(mix((crunch, 1.0), (clank, 0.7), (thump(d, 160, 50, 0.06), 0.8), (bits, 0.3)), 2.0)
    make("ram", 3, crash)

    # Breakdown: a bang, a cough of the dying engine, and parts clattering to the ground.
    def breakdown(g, i):
        d = 2.2
        bang = mix((impact(g, 0.9, 140, 40, 0.15, 0.4), 1.0), (metal(g, 0.9, 280, 0.2), 0.6))
        cough = np.zeros(int(d * SR))
        for k, t0 in enumerate((0.55, 0.8, 1.15)):
            cough += at(mix((thump(0.2, 120, 50, 0.04), 1.0), (noise_hit(g, 0.2, 150, 2500, 0.03), 0.8)) * (1 - 0.25 * k), t0, d)
        clatter = np.zeros(int(d * SR))
        for k in range(10):
            clatter += at(metal(g, 0.2, 700 + 1500 * g.rng.random(), 0.03) * g.rng.uniform(0.3, 0.8), 0.3 + 1.6 * g.rng.random(), d)
        return reverb(g, mix((pad_to(bang, d), 1.0), (cough, 0.6), (clatter, 0.3)), 0.6, 0.15)
    make("break", 2, breakdown)

    # Repair: three hammer blows on sheet metal.
    def hammering(g, i):
        d = 1.1
        out = np.zeros(int(d * SR))
        for k, t0 in enumerate((0.0, 0.3, 0.62)):
            hit = mix((metal(g, 0.4, 520 + 40 * i + 25 * k, 0.09, ratios=(1.0, 2.3, 3.7, 5.9)), 1.0),
                      (noise_hit(g, 0.4, 800, 7000, 0.006), 0.7), (thump(0.4, 260, 120, 0.02), 0.4))
            out += at(hit * (1 - 0.1 * k), t0, d)
        return out
    make("repair", 2, hammering)

    # Part fitted: a ratchet tightening a bolt, then a solid clunk.
    def fit(g, i):
        d = 0.8
        out = np.zeros(int(d * SR))
        for k in range(6):
            out += at(metal(g, 0.04, 3100 + 100 * i, 0.004, gains=(1, 0.4, 0.2, 0.1)) * 0.6, 0.05 * k, d)
        out += at(mix((thump(0.3, 200, 80, 0.04), 1.0), (metal(g, 0.3, 420, 0.06), 0.8)), 0.38, d)
        return out
    make("fit_part", 2, fit)

    # Repaired: the engine catches (pulses speeding up into a short rev) with a cheerful clank.
    def repaired(g, i):
        d = 1.8
        n = int(d * SR)
        hz = curve([(0, 8), (0.35, 20), (0.6, 70), (1, 40)], n)
        out = np.zeros(n)
        t0 = 0.0
        while t0 < d - 0.1:
            idx = int(t0 * SR)
            out += at(noise_hit(g, 0.08, 60, 1500, 0.012), t0, d)
            t0 += 1 / hz[min(idx, n - 1)]
        rev = band(out, 50, 2000) * env(n, shape=[(0, 0.6), (0.6, 1), (1, 0)])
        clank = metal(g, d, 600, 0.15)
        return mix((rev, 1.0), (clank, 0.35))
    make("repaired", 1, repaired)
    return S


SUB = "subtitles.blocksouls_vehicles."
EVENTS = {
    "kart.engine": ("engine", SUB + "kart.engine"),
    "kart.hop": ("hop", SUB + "kart.hop"),
    "kart.boost": ("boost", SUB + "kart.boost"),
    "kart.skid": ("skid", SUB + "kart.skid"),
    "kart.ram": ("ram", SUB + "kart.ram"),
    "kart.break": ("break", SUB + "kart.break"),
    "kart.repair": ("repair", SUB + "kart.repair"),
    "wreck.fit_part": ("fit_part", SUB + "wreck.fit_part"),
    "wreck.repaired": ("repaired", SUB + "wreck.repaired"),
}
PEAKS = {"engine": 0.6, "hop": 0.6, "boost": 0.75, "skid": 0.55, "ram": 0.85, "break": 0.9, "repair": 0.75, "fit_part": 0.7,
         "repaired": 0.75}


def main():
    path = os.path.join(ASSETS, "sounds.json")
    with open(path) as f:
        before = json.load(f)
    count = emit(ASSETS, "blocksouls_vehicles", "kart", sounds(), EVENTS, PEAKS.get, loops=("engine",))
    # Keep events other sessions own (the horn) as they are.
    with open(path) as f:
        data = json.load(f)
    for event, entry in before.items():
        if event not in EVENTS:
            data[event] = entry
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print(f"{count} sounds for {len(EVENTS)} events")


if __name__ == "__main__":
    main()
