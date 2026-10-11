"""Shared synthesis helpers for BlockSouls' generated audio (mob_sounds.py, gun_sounds.py, boss_sounds.py, ...).

No samples: everything is built from oscillators, filtered noise, formant filters and decaying resonances. Each
sound seeds its own random generator from its name, so the same script always writes the same samples.
"""

import hashlib
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
# Formants (Hz) of a human-sized vocal tract; monsters scale them down (bigger) or up (smaller).
VOWELS = {
    "a": (730, 1090, 2440), "e": (530, 1840, 2480), "i": (270, 2290, 3010), "o": (570, 840, 2410),
    "u": (300, 870, 2240), "uh": (520, 1190, 2390), "ae": (660, 1720, 2410),
}


class Gen:
    def __init__(self, name):
        self.rng = np.random.default_rng(int(hashlib.sha256(name.encode()).hexdigest()[:12], 16))

    def smooth_noise(self, n, rate):
        """Band-limited random wiggle in [-1, 1], about `rate` changes per second."""
        pts = max(2, int(n / SR * rate) + 2)
        g = self.rng.uniform(-1, 1, pts)
        return np.interp(np.linspace(0, pts - 1, n), np.arange(pts), g)

    def noise(self, n):
        return self.rng.uniform(-1, 1, n)


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def curve(points, n):
    """Piecewise-linear curve through (fraction, value) points, sampled n times."""
    xs, ys = zip(*points)
    return np.interp(np.linspace(0, 1, n), xs, ys)


def env(n, attack=0.02, release=0.2, shape=None):
    """Attack/release envelope, or a custom piecewise shape [(fraction, level), ...]."""
    if shape is not None:
        return curve(shape, n)
    e = np.ones(n)
    a, r = max(1, int(attack * SR)), max(1, int(release * SR))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] *= np.linspace(1, 0, r) ** 1.5
    return e


def voice(g, f0, rough=0.2, sub=0.0, jitter=0.01, breath=0.08):
    """Glottal source following the f0 array (Hz)."""
    n = len(f0)
    f = f0 * (1 + jitter * g.smooth_noise(n, 30))
    phase = np.cumsum(f / SR)
    # A sawtooth softened at the edges: buzzy like a throat, not a synth.
    src = 2 * (phase % 1) - 1
    src = src - 0.5 * (2 * ((phase + 0.5) % 1) - 1) * 0.3
    if sub:
        src += sub * (2 * ((phase / 2) % 1) - 1)
    src *= 1 + rough * g.smooth_noise(n, 70)
    return src + breath * g.noise(n)


def formant_env(freqs, vowel, scale=1.0, bw=(90, 110, 160), gains=(1.0, 0.6, 0.35), tilt=0.0):
    f = np.array(VOWELS[vowel]) * scale if isinstance(vowel, str) else np.array(vowel)
    out = np.full_like(freqs, 0.02)
    for fi, b, a in zip(f, bw, gains):
        out += a * np.exp(-0.5 * ((freqs - fi) / (b * max(scale, 0.6))) ** 2)
    if tilt:
        out *= (1 + freqs / 1000) ** -tilt
    return out


def stft_filter(x, gain_at, n=1024, hop=256):
    """Filter x with a time-varying magnitude response gain_at(t_seconds, freqs) -> gains."""
    win = np.hanning(n)
    pad = np.concatenate([np.zeros(n), x, np.zeros(n)])
    out = np.zeros_like(pad)
    norm = np.zeros_like(pad)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    for start in range(0, len(pad) - n, hop):
        frame = pad[start:start + n] * win
        spec = np.fft.rfft(frame)
        t = max(0.0, (start + n / 2 - n) / SR)
        spec *= gain_at(t, freqs)
        out[start:start + n] += np.fft.irfft(spec, n) * win
        norm[start:start + n] += win ** 2
    out /= np.maximum(norm, 1e-6)
    return out[n:n + len(x)]


def vowel_path(path, scale=1.0, tilt=0.5, dur=1.0):
    """gain_at() morphing through [(fraction, vowel), ...] over dur seconds."""
    def gain_at(t, freqs):
        x = min(1.0, t / dur)
        for (x0, v0), (x1, v1) in zip(path, path[1:]):
            if x0 <= x <= x1:
                k = (x - x0) / max(1e-6, x1 - x0)
                f = (1 - k) * np.array(VOWELS[v0]) + k * np.array(VOWELS[v1])
                return formant_env(freqs, tuple(f * scale), scale=scale, tilt=tilt)
        return formant_env(freqs, path[-1][1], scale=scale, tilt=tilt)
    return gain_at


def band(x, lo, hi, soft=1.0):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    gain = 1 / (1 + (lo / np.maximum(f, 1)) ** (4 * soft)) / (1 + (f / hi) ** (4 * soft))
    return np.fft.irfft(spec * gain, len(x))


def sweep_band(x, lo_path, hi_path, dur):
    def gain_at(t, f):
        k = min(1.0, t / dur)
        lo = np.interp(k, *zip(*lo_path))
        hi = np.interp(k, *zip(*hi_path))
        return 1 / (1 + (lo / np.maximum(f, 1)) ** 4) / (1 + (f / hi) ** 4)
    return stft_filter(x, gain_at)


def reverb(g, x, size=0.6, wet=0.25, damp=3000):
    n = int(size * SR)
    ir = g.noise(n) * np.exp(-np.linspace(0, 6, n))
    ir = band(ir, 80, damp)
    ir[0] = 0
    y = np.fft.irfft(np.fft.rfft(np.concatenate([x, np.zeros(n)])) * np.fft.rfft(np.concatenate([ir, np.zeros(len(x))])),
                     len(x) + n)
    y /= np.max(np.abs(y)) + 1e-9
    dry = np.concatenate([x / (np.max(np.abs(x)) + 1e-9), np.zeros(n)])
    return dry * (1 - wet) + y * wet


def drive(x, amount):
    return np.tanh(x * amount) / np.tanh(amount)


def clicks(g, dur, rate, lo=1500, hi=5000, decay=0.004, jitter=1.0):
    n = int(dur * SR)
    out = np.zeros(n)
    t = 0.0
    while t < dur:
        i = int(t * SR)
        m = int(decay * 6 * SR)
        burst = g.noise(m) * np.exp(-np.arange(m) / (decay * SR))
        out[i:i + m] += burst[:max(0, min(m, n - i))] * (0.5 + 0.5 * g.rng.random())
        t += (1 / rate) * (1 + jitter * (g.rng.random() - 0.5))
    return band(out, lo, hi)


def thump(dur, f_start, f_end, decay):
    t = t_axis(dur)
    f = f_end + (f_start - f_end) * np.exp(-t * 18)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / decay)


def resonances(g, dur, modes, decay):
    t = t_axis(dur)
    out = np.zeros(len(t))
    for f, a in modes:
        out += a * np.sin(2 * np.pi * f * (1 + 0.002 * g.rng.random()) * t + g.rng.random() * 6) * np.exp(-t / (decay * (300 / f) ** 0.3))
    return out


def bubbles(g, dur, rate, f_lo, f_hi):
    n = int(dur * SR)
    out = np.zeros(n)
    t = 0.0
    while t < dur - 0.05:
        f = g.rng.uniform(f_lo, f_hi)
        m = int(SR * g.rng.uniform(0.03, 0.08))
        tt = np.arange(m) / SR
        # A bubble: a short sine whose pitch rises as it pops.
        blip = np.sin(2 * np.pi * np.cumsum(f * (1 + tt * 8)) / SR) * np.exp(-tt * 45)
        i = int(t * SR)
        out[i:i + m] += blip[:max(0, min(m, n - i))] * g.rng.uniform(0.4, 1)
        t += g.rng.exponential(1 / rate)
    return out


def mix(*parts):
    n = max(len(p) for p, _ in parts)
    out = np.zeros(n)
    for p, gain in parts:
        out[:len(p)] += p / (np.max(np.abs(p)) + 1e-9) * gain
    return out


def finish(x, peak=0.9):
    x = x - np.mean(x)
    fade = int(0.006 * SR)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    return x / (np.max(np.abs(x)) + 1e-9) * peak


# --- Recipes ------------------------------------------------------------------------------------------------------

def groan(g, dur, f0_path, vowels, scale=0.85, rough=0.35, sub=0.3, breath=0.12, tilt=0.6, shape=None, gurgle=0.0):
    n = int(dur * SR)
    f0 = curve(f0_path, n) * (1 + 0.03 * g.smooth_noise(n, 3))
    src = voice(g, f0, rough=rough, sub=sub, breath=breath)
    if gurgle:
        src *= 1 + gurgle * np.maximum(0, g.smooth_noise(n, 18))
    y = stft_filter(src, vowel_path(vowels, scale=scale, tilt=tilt, dur=dur))
    return y * env(n, 0.06, 0.25, shape)


def shriek(g, dur, f0_path, vowels, scale=1.15, rough=0.25, detune=1.012, dist=2.5, shape=None):
    n = int(dur * SR)
    f0 = curve(f0_path, n) * (1 + 0.04 * g.smooth_noise(n, 9))
    src = voice(g, f0, rough=rough, breath=0.25) + 0.7 * voice(g, f0 * detune, rough=rough, breath=0.1)
    y = stft_filter(src, vowel_path(vowels, scale=scale, tilt=0.2, dur=dur))
    return drive(y / (np.max(np.abs(y)) + 1e-9), dist) * env(n, 0.02, 0.15, shape)


def growl(g, dur, f0, scale=0.6, rough=0.6, sub=0.6, am=22, shape=None, vowels=(( 0, "o"), (1, "uh"))):
    n = int(dur * SR)
    f = curve(f0, n)
    src = voice(g, f, rough=rough, sub=sub, breath=0.15)
    src *= 1 + 0.6 * np.sin(2 * np.pi * am * t_axis(dur)[:n] + 3 * g.smooth_noise(n, 4))
    y = stft_filter(src, vowel_path(list(vowels), scale=scale, tilt=0.9, dur=dur))
    return y * env(n, 0.08, 0.3, shape)


def write_ogg(samples, path, quality=4):
    """Encodes samples as Vorbis: a 1-D array is mono (the game positions it), an (n, 2) array is stereo (music)."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    channels = 1 if samples.ndim == 1 else samples.shape[1]
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav = tmp.name
    try:
        with wave.open(wav, "wb") as w:
            w.setnchannels(channels)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        # -bitexact keeps the output identical run to run.
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", str(quality),
                        "-ac", str(channels), "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", path],
                       check=True)
    finally:
        os.unlink(wav)


# --- Shared by the gun, boss, vehicle and music generators -------------------------------------------------------

# Loudest allowed 100 ms RMS after normalizing. Vanilla thunder peaks near full scale and stays there for seconds;
# capping the sustained level keeps every BlockSouls sound below it even at the volumes the code plays them with.
MAX_RMS = 0.4


def level(x, peak, loop=False):
    """finish() to `peak`, then turn down anything whose loudest 100 ms is denser than MAX_RMS. A loop keeps its ends
    (fading them would put a dip at every repeat)."""
    x = finish(x, peak) if not loop else (x - np.mean(x, axis=0)) / (np.max(np.abs(x)) + 1e-9) * peak
    sq = (x if x.ndim == 1 else x.mean(axis=1)) ** 2
    w = min(len(sq), int(0.1 * SR))
    c = np.concatenate([[0.0], np.cumsum(sq)])
    rms = float(np.sqrt(np.max(c[w:] - c[:-w]) / w))
    return x * (MAX_RMS / rms) if rms > MAX_RMS else x


def loop_seam(x, fade):
    """Makes x loop without a click: its last `fade` seconds are cross-faded into its start, then dropped."""
    n = int(fade * SR)
    head, tail = x[:n].copy(), x[-n:]
    k = np.linspace(0, 1, n)
    if x.ndim == 2:
        k = k[:, None]
    out = x[:-n].copy()
    out[:n] = head * np.sqrt(k) + tail * np.sqrt(1 - k)
    return out


def pad_to(x, dur):
    n = int(dur * SR)
    return np.concatenate([x, np.zeros(max(0, n - len(x)))])[:n]


def at(x, start, total):
    """x placed `start` seconds into a silent buffer `total` seconds long."""
    out = np.zeros(int(total * SR))
    i = int(start * SR)
    m = max(0, min(len(x), len(out) - i))
    out[i:i + m] += x[:m]
    return out


def decay(dur, tau):
    return np.exp(-t_axis(dur) / tau)


def noise_hit(g, dur, lo, hi, tau):
    """A burst of band-limited noise with an exponential tail: the body of every gunshot, crunch and splash."""
    return band(g.noise(int(dur * SR)), lo, hi) * decay(dur, tau)


def metal(g, dur, base, tau, ratios=(1.0, 2.76, 5.40, 8.93), gains=(1.0, 0.6, 0.4, 0.25)):
    """Struck metal: inharmonic partials of a bar or plate, the higher ones dying first."""
    return resonances(g, dur, [(base * r, a) for r, a in zip(ratios, gains)], tau)


def bell(g, dur, f, tau, bright=0.5):
    """A glassy bell: partials near 1, 2.32, 4.25, 6.63 x f, with a slow shimmer."""
    t = t_axis(dur)
    out = np.zeros(len(t))
    for r, a in ((1.0, 1.0), (2.32, 0.5 * bright + 0.2), (4.25, 0.35 * bright), (6.63, 0.2 * bright), (2.0, 0.15)):
        out += a * np.sin(2 * np.pi * f * r * t + g.rng.random() * 6) * np.exp(-t / (tau / r ** 0.4))
    return out * (1 + 0.08 * np.sin(2 * np.pi * 5.5 * t))


def whoosh(g, dur, lo_path, hi_path, shape=((0, 0), (0.5, 1), (1, 0))):
    """Air moving: noise through a band that sweeps along the paths, swelled by `shape`."""
    n = int(dur * SR)
    return sweep_band(g.noise(n), lo_path, hi_path, dur) * env(n, shape=list(shape))


def fire(g, dur, rate=30, roar=1.0):
    """Burning: a roaring low band that surges, with crackles on top."""
    n = int(dur * SR)
    surge = 0.6 + 0.4 * g.smooth_noise(n, 6)
    body = band(g.noise(n), 120, 1800) * surge
    hiss = band(g.noise(n), 2500, 9000) * (0.5 + 0.5 * g.smooth_noise(n, 12))
    return mix((body, roar), (hiss, 0.25), (clicks(g, dur, rate, 1500, 8000, decay=0.0012, jitter=1.9), 0.45))


def rumble(g, dur, lo=25, hi=160, rate=3):
    n = int(dur * SR)
    return band(g.noise(n), lo, hi) * (0.6 + 0.4 * g.smooth_noise(n, rate))


def thunder(g, dur, crack=1.0):
    """A close lightning strike: a bright tearing crack, then a long rolling rumble."""
    n = int(dur * SR)
    tear = band(g.noise(n), 900, 12000) * decay(dur, 0.035)
    snap = clicks(g, min(dur, 0.25), 160, 2000, 10000, decay=0.002)
    roll = rumble(g, dur, 30, 400, 4) * env(n, shape=[(0, 0), (0.03, 1), (0.35, 0.7), (1, 0)])
    return mix((tear, crack), (pad_to(snap, dur), 0.5 * crack), (roll, 1.0), (thump(dur, 90, 30, 0.4), 0.6))


def impact(g, dur, f_hi=110, f_lo=32, tau=0.3, debris=0.35):
    """Something huge hitting the ground: a pitched thump, a crunch, rubble and a rumble."""
    n = int(dur * SR)
    return mix((thump(dur, f_hi, f_lo, tau), 1.0), (noise_hit(g, dur, 200, 5000, 0.05), 0.6),
               (clicks(g, dur, 45, 400, 3000, decay=0.012) * env(n, shape=[(0, 0), (0.05, 1), (0.5, 0.3), (1, 0)]), debris),
               (rumble(g, dur) * env(n, shape=[(0, 0), (0.03, 1), (1, 0)]), 0.6))


def wingbeat(g, dur, size=1.0):
    """One downstroke of a big wing: a swell of low air ending in a soft whomp."""
    n = int(dur * SR)
    air = band(g.noise(n), 60 / size, 900 / size) * env(n, shape=[(0, 0), (0.55, 1), (0.7, 0.4), (1, 0)])
    whomp = at(thump(dur * 0.5, 90 / size, 40 / size, 0.08), dur * 0.5, dur)
    flutter = band(g.noise(n), 400, 2500) * env(n, shape=[(0, 0), (0.5, 0), (0.6, 1), (1, 0)]) * \
        (0.5 + 0.5 * np.sin(2 * np.pi * 28 * t_axis(dur)[:n]))
    return mix((air, 1.0), (whomp, 0.8), (flutter, 0.15))


def choir(g, dur, f0_path, vowels, voices=4, spread=0.012, scale=1.0, tilt=1.0, vibrato=5.0):
    """Several breathy voices on the same vowel, slightly detuned: ghostly when reverbed."""
    n = int(dur * SR)
    out = np.zeros(n)
    for k in range(voices):
        f0 = curve(f0_path, n) * (1 + spread * (k - voices / 2) / voices) * \
            (1 + 0.01 * np.sin(2 * np.pi * (vibrato + 0.4 * k) * t_axis(dur)[:n] + k))
        out += voice(g, f0, rough=0.05, breath=0.2, jitter=0.004)
    return stft_filter(out, vowel_path(list(vowels), scale=scale, tilt=tilt, dur=dur))


def saw(f, n, phase=0.0):
    ph = np.cumsum(np.broadcast_to(f, (n,)) / SR) + phase
    return 2 * (ph % 1) - 1


def sine(f, n, phase=0.0):
    return np.sin(2 * np.pi * np.cumsum(np.broadcast_to(f, (n,)) / SR) + phase)


def lowpass(x, cutoff):
    return band(x, 1, cutoff)


def emit(assets, namespace, folder, sounds, events, peak, extra=None, quality=4, loops=()):
    """Writes every sound as assets/sounds/<folder>/<key><n>.ogg and the module's sounds.json.

    sounds: key -> list of variants; events: event id -> (key, subtitle key or None); peak(key) -> normalize level;
    extra(event, key) -> dict of extra per-sound fields (attenuation_distance, stream, volume) or None; loops: keys
    that play looped (their ends aren't faded).
    """
    for key, variants in sounds.items():
        for i, x in enumerate(variants):
            write_ogg(level(x, peak(key), key in loops), os.path.join(assets, "sounds", folder, f"{key}{i + 1}.ogg"), quality)
    out = {}
    for event, (key, subtitle) in events.items():
        names = [f"{namespace}:{folder}/{key}{i + 1}" for i in range(len(sounds[key]))]
        fields = (extra(event, key) if extra else None) or {}
        entry = {}
        if subtitle:
            entry["subtitle"] = subtitle
        entry["sounds"] = [dict(name=nm, **fields) if fields else nm for nm in names]
        out[event] = entry
    with open(os.path.join(assets, "sounds.json"), "w") as f:
        json.dump(out, f, indent=2)
        f.write("\n")
    return sum(len(v) for v in sounds.values())
