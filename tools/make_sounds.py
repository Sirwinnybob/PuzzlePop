#!/usr/bin/env python3
"""Synthesize PuzzlePop's sound effects (standard library only).

Usage: python3 -I tools/make_sounds.py [output-dir]   (default: app/src/main/res/raw)

Writes 44.1 kHz mono 16-bit WAVs, each normalised to a -6 dBFS peak:
  snap.wav    90 ms soft "pop" (a piece locks into place)
  join.wav    70 ms soft click (two pieces join)
  finish.wav  1.2 s rising arpeggio (puzzle complete, "Simple" celebration)
  fanfare.wav 3.5 s trumpet fanfare (puzzle complete, "Big party" celebration): drum roll,
              "ta-ta-ta TAAA, ta-ta-ta TAAAA" on brass, a cymbal crash on the big chord, then two
              party-horn toots. Its note times are mirrored in app/.../play/Celebration.kt (FANFARE_*) so the trumpets on screen toot in time.
  tray.wav    50 ms soft, low click (a piece goes into the tray), quieter than the others
  hint.wav    0.5 s soft two-note chime (the helper shows a piece and its spot)
The output is deterministic, so re-running produces identical files.
"""
import math
import random
import struct
import sys
import wave
from pathlib import Path

RATE = 44100
PEAK = 10 ** (-6 / 20)  # -6 dBFS


def samples(duration_s):
    return range(int(RATE * duration_s))


def fade_edges(buf, fade_in_s=0.002, fade_out_s=0.006):
    """Short linear fades so no sound starts or ends with a click."""
    n = len(buf)
    fi = max(1, int(RATE * fade_in_s))
    fo = max(1, int(RATE * fade_out_s))
    for i in range(min(fi, n)):
        buf[i] *= i / fi
    for i in range(min(fo, n)):
        buf[n - 1 - i] *= i / fo
    return buf


def pop():
    """A bubbly pop: a sine whose pitch drops quickly from 880 Hz to 330 Hz, decaying fast."""
    dur = 0.090
    buf = []
    phase = 0.0
    for i in samples(dur):
        t = i / RATE
        f = 330 + 550 * math.exp(-t / 0.018)
        phase += 2 * math.pi * f / RATE
        env = math.exp(-t / 0.028)
        buf.append(env * (math.sin(phase) + 0.25 * math.sin(2 * phase)))
    return fade_edges(buf)


def click():
    """A gentle wooden tick: a short 1.3 kHz tone with a soft overtone and a very fast decay."""
    dur = 0.070
    buf = []
    for i in samples(dur):
        t = i / RATE
        env = math.exp(-t / 0.012)
        buf.append(env * (math.sin(2 * math.pi * 1300 * t) + 0.3 * math.sin(2 * math.pi * 2600 * t)))
    return fade_edges(buf)


def tray_click():
    """A soft, low tick, like a piece dropped into a felt-lined box: 700 Hz, very fast decay."""
    dur = 0.050
    buf = []
    for i in samples(dur):
        t = i / RATE
        env = math.exp(-t / 0.008)
        buf.append(env * (math.sin(2 * math.pi * 700 * t) + 0.2 * math.sin(2 * math.pi * 1400 * t)))
    return fade_edges(buf)


# --- The fanfare ------------------------------------------------------------------------------------
# Times in seconds. Keep in step with FANFARE_NOTES_S / FANFARE_BIG_S / FANFARE_HORNS_S in Celebration.kt.
FANFARE_LEN = 3.5
ROLL_END = 0.55
G4, C5, E5, G5 = 392.00, 523.25, 659.25, 783.99
# (start, duration, [frequencies played together]) - pickups in unison, the long notes harmonised.
FANFARE = [
    (0.55, 0.11, [G4]), (0.68, 0.11, [G4]), (0.81, 0.11, [G4]),
    (0.94, 0.44, [C5, G4]),
    (1.42, 0.11, [G4]), (1.55, 0.11, [C5]), (1.68, 0.11, [E5]),
    (1.81, 0.95, [G5, E5, C5]),
]
BIG_HITS = [0.94, 1.81]
HORNS = [(2.86, 330.0), (3.06, 440.0)]


def brass(buf, start, dur, f0, amp=1.0, rng=None):
    """A trumpet-ish note: harmonics that get brighter as it gets louder, a scooped attack with a little
    breath, and vibrato that creeps in on long notes."""
    s0 = int(start * RATE)
    n = int((dur + 0.06) * RATE)
    phase = 0.0
    harmonics = [k for k in range(1, 15) if k * f0 * 1.02 < RATE / 2.2]
    for i in range(n):
        if s0 + i >= len(buf):
            break
        t = i / RATE
        # Fast rise with a small "blat" overshoot, then a gentle swell on long notes; release after dur.
        env = (1 - math.exp(-t / 0.010)) * (1 + 0.35 * math.exp(-t / 0.05))
        if dur > 0.3:
            env *= 0.85 + 0.15 * min(1.0, t / dur)
        if t > dur:
            env *= max(0.0, 1 - (t - dur) / 0.06)
        vib = 0.006 * min(1.0, max(0.0, (t - 0.22) / 0.3)) * math.sin(2 * math.pi * 5.5 * t)
        f = f0 * (1 - 0.035 * math.exp(-t / 0.018)) * (1 + vib)
        phase += 2 * math.pi * f / RATE
        bright = min(1.25, env)
        v = 0.0
        for k in harmonics:
            v += math.sin(k * phase) * (bright ** ((k - 1) * 0.45)) / (k ** 1.05)
        if rng is not None and t < 0.04:
            v += (rng.random() * 2 - 1) * 0.25 * (1 - t / 0.04)
        buf[s0 + i] += amp * env * v


def snare_roll(buf, rng, end):
    """A crescendo snare roll: rapid soft noise strokes from 0 to [end]."""
    stroke = 1 / 30
    k = 0
    while k * stroke < end:
        s0 = int(k * stroke * RATE)
        gain = 0.25 + 0.75 * (k * stroke / end) ** 1.5
        prev = 0.0
        for i in range(int(0.05 * RATE)):
            if s0 + i >= len(buf):
                break
            t = i / RATE
            noise = rng.random() * 2 - 1
            hp = noise - 0.6 * prev  # tilt toward the bright, rattly end
            prev = noise
            buf[s0 + i] += 1.8 * gain * math.exp(-t / 0.014) * hp
        k += 1


def timpani(buf, start, f0=130.81, amp=1.4):
    s0 = int(start * RATE)
    phase = 0.0
    for i in range(int(0.9 * RATE)):
        if s0 + i >= len(buf):
            break
        t = i / RATE
        f = f0 * (1 + 0.12 * math.exp(-t / 0.03))
        phase += 2 * math.pi * f / RATE
        env = min(1.0, t / 0.003) * math.exp(-t / 0.28)
        buf[s0 + i] += amp * env * (math.sin(phase) + 0.4 * math.sin(1.5 * phase) + 0.2 * math.sin(2 * phase))


def cymbal(buf, start, rng, amp=0.55):
    s0 = int(start * RATE)
    prev = 0.0
    for i in range(int(1.6 * RATE)):
        if s0 + i >= len(buf):
            break
        t = i / RATE
        noise = rng.random() * 2 - 1
        hp = noise - prev  # high-pass: splashy, not hissy-dull
        prev = noise
        env = min(1.0, t / 0.002) * (0.6 * math.exp(-t / 0.08) + 0.4 * math.exp(-t / 0.6))
        buf[s0 + i] += amp * env * hp


def party_horn(buf, start, f0, dur=0.32, amp=1.4):
    """A party blower: a buzzy sawtooth that bends up, wobbles, and drops off at the end."""
    s0 = int(start * RATE)
    phase = 0.0
    for i in range(int(dur * RATE)):
        if s0 + i >= len(buf):
            break
        t = i / RATE
        bend = 0.75 + 0.25 * (1 - math.exp(-t / 0.04)) - 0.12 * max(0.0, (t - dur * 0.7) / (dur * 0.3))
        f = f0 * bend * (1 + 0.02 * math.sin(2 * math.pi * 9 * t))
        phase += f / RATE
        saw = 2 * (phase % 1.0) - 1
        buzz = 0.75 + 0.25 * math.sin(2 * math.pi * 31 * t)
        env = min(1.0, t / 0.015) * min(1.0, (dur - t) / 0.04)
        buf[s0 + i] += amp * env * buzz * saw


def fanfare():
    rng = random.Random(7)  # deterministic noise
    buf = [0.0] * int(RATE * FANFARE_LEN)
    snare_roll(buf, rng, ROLL_END)
    for start, dur, freqs in FANFARE:
        for idx, f in enumerate(freqs):
            brass(buf, start, dur, f, amp=1.0 if idx == 0 else 0.7, rng=rng)
    for t in BIG_HITS:
        timpani(buf, t)
    cymbal(buf, BIG_HITS[-1], rng)
    for t, f in HORNS:
        party_horn(buf, t, f)
    return fade_edges(buf, fade_out_s=0.08)


def arpeggio():
    """C major rising arpeggio (C5 E5 G5 C6) with soft bell-like notes; the top note rings longest."""
    dur = 1.2
    notes = [(0.00, 523.25), (0.16, 659.25), (0.32, 783.99), (0.48, 1046.50)]
    n = int(RATE * dur)
    buf = [0.0] * n
    for idx, (start, f) in enumerate(notes):
        decay = 0.55 if idx == len(notes) - 1 else 0.22
        s0 = int(start * RATE)
        for i in range(s0, n):
            t = (i - s0) / RATE
            attack = min(1.0, t / 0.008)
            env = attack * math.exp(-t / decay)
            buf[i] += env * (math.sin(2 * math.pi * f * t) + 0.2 * math.sin(4 * math.pi * f * t))
    return fade_edges(buf, fade_out_s=0.05)


def chime():
    """A soft two-note bell (E5 then B5, a rising fifth), gentle attack, the second note rings out."""
    dur = 0.55
    notes = [(0.00, 659.25, 0.12), (0.17, 987.77, 0.20)]
    n = int(RATE * dur)
    buf = [0.0] * n
    for start, f, decay in notes:
        s0 = int(start * RATE)
        for i in range(s0, n):
            t = (i - s0) / RATE
            env = min(1.0, t / 0.012) * math.exp(-t / decay)
            buf[i] += env * (math.sin(2 * math.pi * f * t) + 0.15 * math.sin(4 * math.pi * f * t))
    return fade_edges(buf, fade_out_s=0.04)


def write(path, buf, peak_db=-6):
    peak = max(abs(v) for v in buf) or 1.0
    gain = 10 ** (peak_db / 20) / peak
    frames = b"".join(struct.pack("<h", int(round(v * gain * 32767))) for v in buf)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(frames)
    print(f"wrote {path} ({len(buf) / RATE * 1000:.0f} ms)")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "app/src/main/res/raw"
    out.mkdir(parents=True, exist_ok=True)
    write(out / "snap.wav", pop())
    write(out / "join.wav", click())
    write(out / "finish.wav", arpeggio())
    write(out / "fanfare.wav", fanfare())
    write(out / "tray.wav", tray_click(), peak_db=-12)
    write(out / "hint.wav", chime())


if __name__ == "__main__":
    main()
