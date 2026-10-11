#!/usr/bin/env python3
"""Composes and renders one looping track per field boss and one for the final raid, as stereo .ogg.

    python3 tools/assets/music.py           # needs numpy and ffmpeg with libvorbis

Procedural and simple on purpose: a chord progression on a pad, a bass, drums and one or two voices that fit the
boss, with a melody that wanders the scale (seeded, so the same script writes the same track). Each track is a
whole number of bars; the reverb tail past the end is folded back onto the start, so the loop has no seam.
sounds.json (written by boss_sounds.py) streams them at volume 0.6 so they sit under the fight's sound effects.
Output: bosses/src/main/resources/assets/blocksouls_bosses/sounds/music/<track>.ogg
"""

import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import (SR, Gen, band, bell, choir, clicks, curve, env, level, lowpass, metal, noise_hit, saw,  # noqa: E402
                   sine, t_axis, thump, write_ogg)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "bosses", "src", "main", "resources", "assets", "blocksouls_bosses", "sounds", "music")
TAIL = 4.0  # seconds of reverb and ringing folded back onto the start


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


class Track:
    def __init__(self, name, bpm, beats_per_bar, bars):
        self.g = Gen("music/" + name)
        self.beat = 60.0 / bpm
        self.bar = self.beat * beats_per_bar
        self.length = self.bar * bars
        self.bars = bars
        n = int((self.length + TAIL) * SR)
        self.dry = np.zeros((n, 2))
        self.wet = np.zeros((n, 2))

    def add(self, x, t, gain=1.0, pan=0.0, send=0.3):
        """Places mono x at t seconds: pan -1 (left) .. 1 (right), send = how much goes to the reverb."""
        i = int(t * SR)
        m = max(0, min(len(x), len(self.dry) - i))
        if m == 0:
            return
        lr = np.array([np.sqrt((1 - pan) / 2), np.sqrt((1 + pan) / 2)]) * gain
        part = x[:m, None] * lr
        self.dry[i:i + m] += part * (1 - send)
        self.wet[i:i + m] += part * send

    def render(self, room=2.2, damp=3500):
        g = self.g
        out = self.dry.copy()
        nir = int(room * SR)
        for ch in range(2):
            ir = g.noise(nir) * np.exp(-np.linspace(0, 7, nir))
            ir = band(ir, 100, damp)
            ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
            size = len(self.wet) + nir
            y = np.fft.irfft(np.fft.rfft(self.wet[:, ch], size) * np.fft.rfft(ir, size), size)[:len(self.wet)]
            out[:, ch] += y * 0.8
        n = int(self.length * SR)
        looped = out[:n].copy()
        tail = out[n:]
        looped[:len(tail)] += tail[:n]
        return looped


# --- Instruments -------------------------------------------------------------------------------------------------

def pad(g, notes, dur, cutoff=1200, detune=0.006, attack=0.25, release=0.3, wave="saw"):
    n = int(dur * SR)
    out = np.zeros(n)
    for m in notes:
        for d in (-detune, 0, detune):
            f = hz(m) * (1 + d) * (1 + 0.002 * g.smooth_noise(n, 0.5))
            out += saw(f, n, g.rng.random()) if wave == "saw" else sine(f, n, g.rng.random() * 6)
    out = lowpass(out, cutoff)
    return out * env(n, attack * dur, release * dur) / len(notes)


def organ(g, notes, dur, attack=0.2, release=0.3):
    n = int(dur * SR)
    out = np.zeros(n)
    for m in notes:
        for h, a in ((1, 1.0), (2, 0.5), (3, 0.3), (4, 0.2), (6, 0.1)):
            out += a * sine(hz(m) * h * (1 + 0.001 * g.rng.normal()), n, g.rng.random() * 6)
    return out * env(n, attack * dur, release * dur) / len(notes)


def bass(g, m, dur, cutoff=500, pluck=0.25):
    n = int(dur * SR)
    f = hz(m)
    x = 0.7 * sine(f, n) + 0.4 * lowpass(saw(f, n), cutoff)
    return x * np.exp(-t_axis(dur)[:n] / max(0.05, pluck)) * env(n, 0.005, 0.03)


def brass(g, m, dur, cutoff=1400, bite=1.0):
    n = int(dur * SR)
    f = hz(m) * (1 + 0.004 * np.sin(2 * np.pi * 5 * t_axis(dur)[:n]) * np.minimum(1, t_axis(dur)[:n] / 0.4))
    x = saw(f, n) + 0.5 * saw(f * 1.004, n, 0.3)
    swell = curve([(0, 0.3), (0.15, 1), (1, 0.8)], n)
    return lowpass(x, cutoff) * swell ** bite * env(n, 0.03, 0.12)


def lead(g, m, dur, vibrato=0.006):
    n = int(dur * SR)
    t = t_axis(dur)[:n]
    f = hz(m) * (1 + vibrato * np.sin(2 * np.pi * 5.2 * t) * np.minimum(1, t / 0.3))
    x = sine(f, n) + 0.2 * sine(3 * f, n) + 0.08 * sine(5 * f, n)
    return x * env(n, 0.06, 0.3 * dur)


def drum(g, size=1.0, dur=0.9):
    return thump(dur, 110 / size, 40 / size, 0.22 * size) + 0.25 * noise_hit(g, dur, 80, 1500 / size, 0.04)


def snare(g, dur=0.3):
    return 0.6 * thump(dur, 260, 160, 0.03) + noise_hit(g, dur, 1200, 8000, 0.07)


def anvil(g, dur=0.8):
    return metal(g, dur, 980, 0.25, ratios=(1.0, 2.67, 4.1, 5.9), gains=(1, 0.6, 0.4, 0.25)) + noise_hit(g, dur, 2000, 9000, 0.004)


def hoofbeat(g):
    return 0.8 * thump(0.3, 240, 120, 0.04) + 0.4 * metal(g, 0.3, 330, 0.05, ratios=(1, 2.1, 3.3, 4.6)) + 0.4 * noise_hit(g, 0.3, 700, 5000, 0.005)


def melody(g, scale, start, steps, lo, hi):
    """A random walk on scale degrees (mostly steps, the odd leap), as midi notes."""
    deg, out = start, []
    for _ in range(steps):
        deg += int(g.rng.choice([-2, -1, -1, 0, 1, 1, 2, 3, -3]))
        deg = max(lo, min(hi, deg))
        octave, k = divmod(deg, len(scale))
        out.append(scale[k] + 12 * octave)
    return out


MINOR = [0, 2, 3, 5, 7, 8, 10]


def chord(root, kind="m"):
    return [root, root + (3 if kind == "m" else 4), root + 7]


# --- Tracks -----------------------------------------------------------------------------------------------------

def furnace_knight():
    """D minor, 84 bpm. A forge: anvil strikes, taiko-like drums, a smouldering pad and a horn line."""
    tr = Track("furnace_knight", 84, 4, 32)
    g = tr.g
    prog = [(50, "m"), (46, "M"), (43, "m"), (45, "M")]  # Dm Bb Gm A
    scale = [62 + s for s in MINOR]
    for bar in range(tr.bars):
        t = bar * tr.bar
        root, kind = prog[(bar // 2) % 4]
        if bar % 2 == 0:
            tr.add(pad(g, chord(root, kind) + [root + 12], tr.bar * 2, cutoff=900), t, 0.35, 0.0, 0.5)
            tr.add(band(g.noise(int(tr.bar * 2 * SR)), 100, 700) * env(int(tr.bar * 2 * SR), shape=[(0, 0), (0.5, 1), (1, 0)]), t, 0.06, -0.3, 0.6)
        for b, gain in ((0, 1.0), (1.5, 0.6), (2, 0.85), (3.5, 0.5)):
            tr.add(drum(g, 1.2), t + b * tr.beat, 0.55 * gain, 0.0, 0.25)
        if bar >= 4:
            for b in (0, 2):
                tr.add(anvil(g), t + b * tr.beat, 0.12, 0.35, 0.4)
        for b in range(8):
            tr.add(bass(g, root - 12, tr.beat * 0.5, 400, 0.18), t + b * tr.beat * 0.5, 0.35 if b % 2 == 0 else 0.2, 0.0, 0.1)
    notes = melody(g, scale, 3, 32, 0, 9)
    for k, m in enumerate(notes):
        bar = 16 + k // 2
        if k % 4 != 3:
            tr.add(brass(g, m, tr.beat * 2 * 0.95, 1300), bar * tr.bar + (k % 2) * 2 * tr.beat, 0.22, -0.2, 0.45)
    return tr.render(2.4, 3000)


def pale_rider():
    """A minor in 6/8. A ghost choir over a galloping hollow pulse, wind, and a thin whistle."""
    tr = Track("pale_rider", 240, 6, 60)  # the beat is an eighth note
    g = tr.g
    prog = [(57, "m"), (53, "M"), (50, "m"), (52, "M")]  # Am F Dm E
    for bar in range(tr.bars):
        t = bar * tr.bar
        root, kind = prog[(bar // 4) % 4]
        if bar % 4 == 0:
            d = tr.bar * 4
            voices = choir(g, d, [(0, hz(root - 12)), (1, hz(root - 12))], [(0, "u"), (0.5, "o"), (1, "u")], voices=3, spread=0.01, tilt=1.4)
            for m in chord(root, kind)[1:]:
                voices += choir(g, d, [(0, hz(m)), (1, hz(m))], [(0, "u"), (1, "o")], voices=2, spread=0.01, tilt=1.4)
            tr.add(voices * env(len(voices), 0.25 * d, 0.3 * d), t, 0.3, 0.0, 0.7)
            tr.add(band(g.noise(int(d * SR)), 300, 1500) * (0.5 + 0.5 * g.smooth_noise(int(d * SR), 0.6)), t, 0.07, 0.5, 0.5)
            tr.add(bass(g, root - 24, d * 0.9, 300, 1.5), t, 0.3, 0.0, 0.2)
        if bar >= 4:
            for e, gain in ((0, 1.0), (2, 0.6), (3, 0.9), (5, 0.6)):
                tr.add(hoofbeat(g), t + e * tr.beat, 0.3 * gain, -0.25 if e % 2 else 0.25, 0.45)
        if bar % 8 == 7:
            tr.add(drum(g, 1.5, 1.5), t + 3 * tr.beat, 0.4, 0.0, 0.5)
    scale = [69 + s for s in MINOR]
    notes = melody(g, scale, 4, 24, 0, 10)
    for k, m in enumerate(notes):
        bar = 28 + k
        if k % 3 != 2 and bar < tr.bars:
            tr.add(lead(g, m + 12, tr.beat * 5, 0.01), bar * tr.bar, 0.07, 0.3, 0.75)
    return tr.render(3.0, 2500)


def ashwyrm():
    """C minor, 96 bpm. Huge drums, low brass stabs, fire in the background, a rising horn theme."""
    tr = Track("ashwyrm", 96, 4, 36)
    g = tr.g
    prog = [(48, "m"), (44, "M"), (41, "m"), (43, "M")]  # Cm Ab Fm G
    scale = [60 + s for s in MINOR]
    stabs = [0, 0.75, 1.5, 2.5, 3.0]
    for bar in range(tr.bars):
        t = bar * tr.bar
        root, kind = prog[(bar // 2) % 4]
        if bar % 2 == 0:
            tr.add(pad(g, chord(root, kind), tr.bar * 2, cutoff=700), t, 0.25, 0.0, 0.5)
            d = tr.bar * 2
            n = int(d * SR)
            burn = band(g.noise(n), 150, 1500) * (0.6 + 0.4 * g.smooth_noise(n, 2)) + 0.5 * clicks(g, d, 15, 2000, 8000, decay=0.001, jitter=1.9)
            tr.add(burn, t, 0.04, -0.4, 0.3)
        for b, gain in ((0, 1.0), (0.75, 0.5), (2, 0.9), (2.5, 0.5), (3.5, 0.7)):
            tr.add(drum(g, 1.5, 1.2), t + b * tr.beat, 0.6 * gain, 0.0, 0.3)
        if bar >= 2:
            for b in stabs:
                tr.add(brass(g, root - 12, tr.beat * 0.45, 900, 0.5), t + b * tr.beat, 0.22, -0.15, 0.3)
                tr.add(brass(g, root - 5, tr.beat * 0.45, 900, 0.5), t + b * tr.beat, 0.12, 0.15, 0.3)
        if bar % 4 == 3:
            tr.add(snare(g) * 0.5, t + 3.5 * tr.beat, 0.25, 0.2, 0.4)
            tr.add(snare(g) * 0.5, t + 3.75 * tr.beat, 0.3, 0.2, 0.4)
    notes = melody(g, scale, 4, 36, 2, 11)
    for k, m in enumerate(notes):
        bar = 16 + k // 2
        if bar < tr.bars and k % 4 != 3:
            tr.add(brass(g, m, tr.beat * 2 * 0.9, 1800), bar * tr.bar + (k % 2) * 2 * tr.beat, 0.2, 0.1, 0.5)
    return tr.render(2.6, 3000)


def stormplate_knight():
    """E minor, 112 bpm. Driving eighth-note bass, steel hits on the backbeat, thunder rolling under."""
    tr = Track("stormplate_knight", 112, 4, 42)
    g = tr.g
    prog = [(52, "m"), (48, "M"), (50, "M"), (47, "m")]  # Em C D Bm
    scale = [64 + s for s in MINOR]
    for bar in range(tr.bars):
        t = bar * tr.bar
        root, kind = prog[(bar // 2) % 4]
        if bar % 2 == 0:
            tr.add(pad(g, chord(root, kind) + [root + 12], tr.bar * 2, cutoff=1500), t, 0.22, 0.0, 0.5)
        for b in range(8):
            tr.add(bass(g, root - 12 if b % 4 != 3 else root - 5, tr.beat * 0.5, 700, 0.12), t + b * tr.beat * 0.5, 0.35, 0.0, 0.1)
        for b in (0, 2):
            tr.add(drum(g, 1.0, 0.6), t + b * tr.beat, 0.5, 0.0, 0.2)
        if bar >= 2:
            for b in (1, 3):
                tr.add(metal(g, 0.7, 760, 0.3) + noise_hit(g, 0.7, 2000, 9000, 0.05), t + b * tr.beat, 0.14, 0.3, 0.4)
        if bar % 8 == 6:
            d = 5.0
            n = int(d * SR)
            roll = band(g.noise(n), 30, 300) * env(n, shape=[(0, 0), (0.05, 1), (0.4, 0.6), (1, 0)]) * (0.6 + 0.4 * g.smooth_noise(n, 3))
            tr.add(roll, t + tr.beat, 0.35, -0.3, 0.5)
    notes = melody(g, scale, 4, 96, 0, 12)
    for k, m in enumerate(notes):
        bar = 18 + k // 4
        if bar < tr.bars:
            tr.add(lead(g, m, tr.beat * 0.9, 0.003), bar * tr.bar + (k % 4) * tr.beat, 0.1, 0.35 if k % 2 else -0.35, 0.45)
    return tr.render(2.0, 4000)


def final_raid():
    """B minor, 60 bpm. A void drone and an organ, crystal bells arpeggiating, a slow, enormous drum, then a choir."""
    tr = Track("final_raid", 60, 4, 24)
    g = tr.g
    prog = [(47, "m"), (43, "M"), (40, "m"), (42, "M")]  # Bm G Em F#
    n = int(tr.length * SR)
    t = t_axis(tr.length)[:n]
    drone = sum(np.sin(2 * np.pi * f * t) for f in (hz(35), hz(35) + 0.5, hz(42) * 1.001)) * 0.3
    tr.add(drone + 0.3 * band(g.noise(n), 40, 300) * (0.5 + 0.5 * g.smooth_noise(n, 0.3)), 0.0, 0.5, 0.0, 0.3)
    for bar in range(tr.bars):
        t0 = bar * tr.bar
        root, kind = prog[bar % 4]
        tr.add(organ(g, chord(root, kind) + [root + 12], tr.bar), t0, 0.2, 0.0, 0.55)
        if bar >= 4:
            tr.add(drum(g, 2.0, 2.0), t0, 0.6, 0.0, 0.4)
            tr.add(drum(g, 2.0, 2.0), t0 + 2.5 * tr.beat, 0.35, 0.0, 0.4)
        if bar >= 8:
            arp = [root + 12, root + 15 + (1 if kind == "M" else 0), root + 19, root + 24]
            for k in range(8):
                m = arp[k % 4] + (12 if k >= 4 else 0)
                tr.add(bell(g, 1.5, hz(m), 0.6, 0.5), t0 + k * tr.beat * 0.5, 0.08, -0.5 + k / 7, 0.6)
        if bar >= 16 and bar % 2 == 0:
            d = tr.bar * 2
            voices = sum(choir(g, d, [(0, hz(m)), (1, hz(m))], [(0, "o"), (0.5, "a"), (1, "o")], voices=2, spread=0.01, tilt=1.1)
                         for m in chord(root, kind))
            tr.add(voices * env(len(voices), 0.3 * d, 0.3 * d), t0, 0.12, 0.0, 0.7)
    return tr.render(3.5, 3000)


TRACKS = {"furnace_knight": furnace_knight, "pale_rider": pale_rider, "ashwyrm": ashwyrm, "stormplate_knight": stormplate_knight,
          "final_raid": final_raid}


def main(names=None):
    for name in names or TRACKS:
        x = TRACKS[name]()
        x = level(x, 0.8, loop=True)
        write_ogg(x, os.path.join(OUT, name + ".ogg"), quality=3)
        print(f"{name}: {len(x) / SR:.1f} s")


if __name__ == "__main__":
    main(sys.argv[1:])
