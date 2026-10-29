# -*- coding: utf-8 -*-
"""楽譜の書き方。旋律は文字で書き、和音は名前で書く。

旋律: "F#5:2 E5:.5 D5:.5 | E5:1 A4:2 | r:3"
  - 音名 + 8 度 (C4 = 60)、":" のあとに拍の数。r は休み。"|" は小節の区切り (拍の数を検める)
  - 音名のあとの "~" は次の音へつなぐ (笛のレガート。少し重ねる)
和音: "D", "A/C#", "Bm", "Em7", "A7", "Gmaj7", "Asus4", "F#", "Dadd9"
"""
import re

import numpy as np

NOTE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def pitch(name):
    m = re.fullmatch(r"([A-G])([#b]?)(-?\d)", name)
    if not m:
        raise ValueError("bad note " + name)
    p = NOTE[m.group(1)] + (1 if m.group(2) == "#" else -1 if m.group(2) == "b" else 0)
    return 12 * (int(m.group(3)) + 1) + p


def melody(text, beats_per_bar, start_bar=0):
    """[(拍の位置, 拍の長さ, 音程 or None, つなぐか)] を返す。小節ごとの拍の数を検める"""
    out = []
    beat = start_bar * beats_per_bar
    for bi, bar in enumerate(text.split("|")):
        bar = bar.strip()
        if not bar:
            continue
        total = 0.0
        for tok in bar.split():
            name, length = tok.split(":")
            length = float(length)
            tie = name.endswith("~")
            name = name.rstrip("~")
            out.append((beat + total, length, None if name == "r" else pitch(name), tie))
            total += length
        if abs(total - beats_per_bar) > 1e-6:
            raise ValueError("bar %d has %.2f beats: %s" % (bi + 1, total, bar))
        beat += beats_per_bar
    return out


QUAL = {
    "": [0, 4, 7], "m": [0, 3, 7], "7": [0, 4, 7, 10], "m7": [0, 3, 7, 10], "maj7": [0, 4, 7, 11],
    "sus4": [0, 5, 7], "sus2": [0, 2, 7], "add9": [0, 4, 7, 14], "madd9": [0, 3, 7, 14], "dim": [0, 3, 6],
    "6": [0, 4, 7, 9], "m6": [0, 3, 7, 9], "5": [0, 7],
}


def chord(name):
    """(低音の音程の類 0〜11, 和音の音の類の並び)"""
    m = re.fullmatch(r"([A-G][#b]?)([a-z0-9]*)(?:/([A-G][#b]?))?", name)
    if not m:
        raise ValueError("bad chord " + name)
    root = pitch(m.group(1) + "4") % 12
    tones = [(root + i) % 12 for i in QUAL[m.group(2)]]
    bass_pc = pitch(m.group(3) + "4") % 12 if m.group(3) else root
    return bass_pc, tones


def nearest(pc, around):
    """音の類 pc で around にいちばん近い音程"""
    best = None
    for o in range(-2, 3):
        p = (around // 12 + o) * 12 + pc
        if best is None or abs(p - around) < abs(best - around):
            best = p
    return best


def voicing(name, center=57, count=4):
    """和音を center のまわりに count 音で積む (閉じた並び)"""
    _, tones = chord(name)
    notes = sorted({nearest(pc, center) for pc in tones})
    while len(notes) < count:
        notes.append(notes[len(notes) - len(tones)] + 12 if len(notes) >= len(tones) else notes[-1] + 12)
    return sorted(notes)[:count]


def bass_note(name, low=38, high=50):
    bpc, _ = chord(name)
    p = nearest(bpc, (low + high) // 2)
    while p < low:
        p += 12
    while p > high:
        p -= 12
    return p


def progression(text, beats_per_bar, start_bar=0):
    """"D | A/C# | Em A7 | ..." → [(拍の位置, 拍の長さ, 和音の名前)]。一小節に二つなら半分ずつ"""
    out = []
    beat = start_bar * beats_per_bar
    for bar in text.split("|"):
        names = bar.split()
        if not names:
            continue
        each = beats_per_bar / len(names)
        for i, n in enumerate(names):
            out.append((beat + i * each, each, n))
        beat += beats_per_bar
    return out


def humanize(at, amount=0.008, rng=np.random.default_rng(3)):
    return at + rng.normal(0, amount)
