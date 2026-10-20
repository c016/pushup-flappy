"""Generates the 8-bit style sound effects as small WAV files (standard library only).

Run with: python3 tools/sound_fx.py. Writes sfx_*.wav into app/src/main/res/raw.
"""
import math
import os
import random
import struct
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res', 'raw')


def square(phase):
    return 1.0 if (phase % 1.0) < 0.5 else -1.0


def render(duration, sample):
    """sample(t, progress) -> value in [-1, 1]."""
    n = int(duration * RATE)
    return [sample(i / RATE, i / n) for i in range(n)]


def sweep(f0, f1, duration, volume=0.5, wave_fn=square):
    phase = 0.0
    out = []
    for i in range(int(duration * RATE)):
        p = i / (duration * RATE)
        phase += (f0 + (f1 - f0) * p) / RATE
        out.append(wave_fn(phase) * volume * (1 - p) ** 0.5)
    return out


def noise(duration, volume=0.6, decay=3.0, smooth=0.0):
    rnd = random.Random(1)
    out, last = [], 0.0
    for i in range(int(duration * RATE)):
        p = i / (duration * RATE)
        last = last * smooth + rnd.uniform(-1, 1) * (1 - smooth)
        out.append(last * volume * math.exp(-decay * p))
    return out


def notes(freqs, each, volume=0.4):
    out = []
    for f in freqs:
        out += sweep(f, f, each, volume)
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(n)]


def save(samples, name):
    with wave.open(os.path.join(OUT, name + '.wav'), 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, s)) * 32000)) for s in samples))


if __name__ == '__main__':
    save(sweep(1400, 600, 0.06, 0.25), 'sfx_shot')
    save(noise(0.25, 0.7, 4), 'sfx_explode')
    save(mix(noise(0.9, 0.9, 3, smooth=0.6), sweep(120, 40, 0.9, 0.4)), 'sfx_big_explode')
    save(notes([660, 880, 1320], 0.04), 'sfx_capsule')
    save(sweep(300, 1500, 0.35, 0.4), 'sfx_powerup')
    save(mix(noise(0.6, 0.7, 2.5, smooth=0.3), sweep(500, 60, 0.6, 0.4)), 'sfx_hit')
    save(notes([880, 660] * 3, 0.16, 0.35), 'sfx_warning')
    save(sweep(500, 900, 0.08, 0.3), 'sfx_flap')
    save(notes([988, 1319], 0.07, 0.3), 'sfx_point')
