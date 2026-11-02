# -*- coding: utf-8 -*-
"""鐘の都の曲「鐘の都」(3/4、D 長調)。ループで流す。

主題は都の鐘 (F#-E-D-A / D-F#-E-A) から生まれる。町の笛の旋律の頭が鐘の四つの音と同じ。
ループの本体: A (笛) 16 小節 → B (弦が歌い、笛が鳥のように合いの手) 16 → A' (笛 + チェレスタ、太鼓と振り物) 16
  → 戻り 2 小節 (G | Asus4 A)。A' の終わりは D に落ち着かず笛が上へ抜け、戻りでは伴奏を続けたまま太鼓が盛り上げ、
  A→B と同じハープの駆け上がりで A の頭へ入る (つなぎ目が「やり直し」でなく曲の続きに聞こえるように)。
  はみ出た余韻と残響は頭へ折り返して足す (Mix.render の loop)。終わりから頭へ途切れずにつながる。
使い方: python tools/gen/music/track_capital.py <ループ.ogg>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np  # noqa: E402

import gm  # noqa: E402
import synth as S  # noqa: E402
from score import bass_note, humanize, melody, pitch, progression, voicing  # noqa: E402

BPM = 104
BAR = 3
SEC = 60.0 / BPM


def sec(beat):
    return beat * SEC


A_CH = "D | A/C# | Bm | G | D/F# | G | Em7 | A7 | D | A/C# | Bm | F#m | G | D/F# | Em7 A7 | D"
B_CH = "Bm | G | D | A | Bm | G | Em | F#7 | G | A | F#m | Bm | G | D/F# | Em7 | Asus4 A"
TURN_CH = "G | Asus4 A"

A_MEL = ("F#5:2 E5:.5 D5:.5 | E5:1 A4:2 | B4:1 C#5:1 D5:1 | D5:2 B4:1 | A4:1 D5:1 F#5:1 | G5:2 F#5:.5 E5:.5 | "
         "E5:1 D5:1 B4:1 | C#5:2 A4:1 | F#5:2 E5:.5 D5:.5 | E5:1 A5:2 | F#5:1 E5:1 D5:1 | C#5:2 A4:1 | "
         "B4:1 D5:1 G5:1 | F#5:1.5 E5:.5 D5:1 | E5:1 D5:.5 E5:.5 C#5:1 | D5:3")
B_MEL = ("F#5:2 D5:1 | G5:2 D5:1 | F#5:1.5 E5:.5 D5:1 | E5:3 | D5:1 E5:1 F#5:1 | G5:1.5 A5:.5 G5:1 | "
         "F#5:1 E5:1 D5:1 | C#5:2 A#4:1 | B4:1 D5:1 G5:1 | A5:2 G5:.5 F#5:.5 | F#5:1.5 E5:.5 C#5:1 | D5:2 B4:1 | "
         "B4:1 D5:1 G5:1 | A5:1.5 F#5:.5 D5:1 | G5:1 F#5:1 E5:1 | E5:3")
B_BIRD = ("r:3 | r:2 B5:.5 A5:.5 | F#5:3 | r:3 | r:3 | r:2 D6:.5 B5:.5 | G5:3 | r:2 A#5:1 | B5:3 | r:2 C#6:.5 D6:.5 | "
          "C#6:3 | r:2 B5:1 | r:3 | r:2 A5:.5 G5:.5 | E5:3 | r:3")
# A' の終わりは D に落ち着かず、上へ抜ける (曲がここで終わったように聞こえないように)
A2_END = "E5:1 D5:.5 E5:.5 C#5:1 | D5:1.5 F#5:.5 A5:1"
# 戻り: 高い所から下りて、拾い上げで A の頭 (F#5) へ
TURN_MEL = "B5:2 A5:.5 G5:.5 | E5:1.5 r:.5 C#5:.5 E5:.5"

# 小節の位置 (ループの本体)
A, B, A2, TURN = 0, 16, 32, 48
LOOP_BARS = 50

# 検め (check.py) が使う: 和音の並びと始まりの小節
CHORD_PLAN = [(A_CH, A), (B_CH, B), (A_CH, A2), (TURN_CH, TURN)]


def play_melody(mix, notes, inst, group, pan, wet, vel=0.75, octave=0, legato=0.96, **kw):
    for at, length, p, tie in notes:
        if p is None:
            continue
        dur = sec(length) * (1.04 if tie else legato)
        v = vel * (1.0 + 0.06 * min(length, 2) - 0.04) * np.random.default_rng(int(at * 100)).uniform(0.94, 1.04)
        y = S.INSTRUMENTS[inst](p + 12 * octave, dur, v, **kw)
        mix.add(y, humanize(sec(at), 0.006), pan, 1.0, wet, group)


def pad(mix, prog, vel, group="pad", center=57):
    for at, length, name in prog:
        vs = voicing(name, center, 4)
        for i, p in enumerate(vs):
            y = S.strings(p, sec(length) + 0.05, vel, attack=0.4, release=0.9)
            mix.add(y, sec(at), -0.45 + 0.3 * i, 1.0, 0.5, group)


def bassline(mix, prog, vel=0.8):
    for at, length, name in prog:
        y = S.bass(bass_note(name), sec(length) * 0.95, vel)
        mix.add(y, humanize(sec(at), 0.004), 0.0, 1.0, 0.08, "bass")


def lute_arp(mix, prog, vel=0.7):
    rng = np.random.default_rng(11)
    for at, length, name in prog:
        v = voicing(name, 57, 3)
        low = bass_note(name, 45, 57)
        pat = [low, v[0], v[1], v[2], v[1], v[0]]
        steps = int(round(length * 2))
        for k in range(steps):
            p = pat[k % len(pat)]
            vv = vel * (1.0 if k == 0 else 0.62 + 0.1 * rng.random())
            y = S.lute(p, sec(0.5) * 1.6, vv)
            mix.add(y, humanize(sec(at + k * 0.5), 0.007), -0.35, 1.0, 0.25, "lute")


def harp_arp(mix, prog, vel=0.6):
    for at, length, name in prog:
        v = voicing(name, 62, 4)
        low = bass_note(name, 43, 55)
        seq = [low, v[0], v[1], v[2], v[3]]
        steps = int(round(length))
        for k in range(steps):
            idx = [0, 2, 4][k % 3] if steps >= 3 else [0, 3][k % 2]
            y = S.harp(seq[idx], sec(1) * 2, vel * (1.0 if k == 0 else 0.75))
            mix.add(y, humanize(sec(at + k), 0.006), 0.35, 1.0, 0.4, "harp")


def gliss(mix, at_beat, start, steps, beats=1.5, vel=0.5):
    scale = [0, 2, 4, 5, 7, 9, 11]  # D 長調の並び (D から)
    base = pitch(start)
    d = pitch("D4") % 12
    notes = []
    p = base
    while len(notes) < steps:
        if (p - d) % 12 in scale:
            notes.append(p)
        p += 1
    for i, q in enumerate(notes):
        y = S.harp(q, 1.2, vel * (0.7 + 0.3 * i / steps))
        mix.add(y, sec(at_beat + beats * i / steps), 0.35, 1.0, 0.45, "harp")


def drums(mix, bar0, bars, pattern):
    """pattern: [(拍, 大きさ)] を毎小節。振り物は 8 分ごと"""
    for b in range(bars):
        for beat, v, kind in pattern:
            inst = S.drum if kind == "drum" else S.shaker
            y = inst(0, 0.2, v)
            group = "drum" if kind == "drum" else "shaker"
            mix.add(y, humanize(sec((bar0 + b) * BAR + beat), 0.005), -0.1 if kind == "drum" else 0.45, 1.0,
                    0.2, group)


def body(mix, off):
    """ループの本体を off 小節目から置く"""
    # A: 笛・リュート・低音・軽い太鼓
    a_prog = progression(A_CH, BAR, off + A)
    play_melody(mix, melody(A_MEL, BAR, off + A), "flute", "melody", 0.1, 0.35)
    lute_arp(mix, a_prog)
    bassline(mix, a_prog, 0.7)
    pad(mix, a_prog, 0.28)
    drums(mix, off + A, 16, [(0, 0.5, "drum"), (2, 0.28, "drum")])
    gliss(mix, (off + A + 16) * BAR - 1.5, "D4", 10)

    # B: 弦が歌い、笛は合いの手。ハープ
    b_prog = progression(B_CH, BAR, off + B)
    b_notes = melody(B_MEL, BAR, off + B)
    play_melody(mix, b_notes, "strings", "smel", -0.15, 0.4, vel=0.75, legato=1.0, attack=0.12, release=0.5)
    play_melody(mix, b_notes, "strings", "smel", 0.15, 0.4, vel=0.5, octave=-1, legato=1.0, attack=0.15, release=0.5)
    play_melody(mix, melody(B_BIRD, BAR, off + B), "flute", "bird", 0.3, 0.45, vel=0.6)
    harp_arp(mix, b_prog)
    bassline(mix, b_prog, 0.65)
    pad(mix, b_prog, 0.4)
    drums(mix, off + B, 16, [(0, 0.35, "drum")])
    gliss(mix, (off + B + 16) * BAR - 1.5, "A4", 9)

    # A': 笛 + チェレスタの重ね、太鼓と振り物
    a2_prog = progression(A_CH, BAR, off + A2)
    a2_text = A_MEL.rsplit("|", 2)[0] + "| " + A2_END
    a2_notes = melody(a2_text, BAR, off + A2)
    play_melody(mix, a2_notes, "flute", "melody", 0.1, 0.35, vel=0.8)
    play_melody(mix, a2_notes, "celesta", "celesta", 0.3, 0.45, vel=0.6, octave=1)
    lute_arp(mix, a2_prog, 0.75)
    bassline(mix, a2_prog, 0.75)
    pad(mix, a2_prog, 0.45)
    drums(mix, off + A2, 16, [(0, 0.6, "drum"), (1, 0.22, "drum"), (2, 0.35, "drum")]
          + [(1, 0.3, "shaker"), (2, 0.22, "shaker")])

    # 戻り: 伴奏は続けたまま、太鼓が盛り上げ、ハープの駆け上がりで頭へ
    t_prog = progression(TURN_CH, BAR, off + TURN)
    play_melody(mix, melody(TURN_MEL, BAR, off + TURN), "flute", "melody", 0.1, 0.35, vel=0.78)
    lute_arp(mix, t_prog, 0.72)
    bassline(mix, t_prog, 0.72)
    pad(mix, t_prog, 0.42)
    drums(mix, off + TURN, 1, [(0, 0.55, "drum"), (2, 0.32, "drum")])
    drums(mix, off + TURN + 1, 1, [(0, 0.5, "drum"), (1.5, 0.32, "drum"), (2, 0.42, "drum"), (2.5, 0.5, "drum")])
    gliss(mix, (off + TURN + 2) * BAR - 1.5, "D4", 10)


LEVELS = {"melody": -6, "smel": -7, "bird": -12, "celesta": -17, "lute": -11, "harp": -12, "bass": -11,
          "pad": -15, "drum": -16, "shaker": -27}


def build(mute=(), live=True):
    """ループの 1 周。1 周ぶん置き、はみ出た余韻と残響を頭へ折り返す。mute: 鳴らさない組 (検めで伴奏だけを聴くとき)。
    live: 音源の生の楽器で鳴らす (False なら合成の楽器)"""
    if live:
        gm.use_soundfont()
    loop = int(round(sec(LOOP_BARS * BAR) * S.SR))
    mix = S.Mix(loop / S.SR + 8.0)
    body(mix, 0)
    levels = dict(LEVELS)
    for g in mute:
        levels[g] = -200
    return mix.render(S.make_ir(2.8, damp=0.55), levels, fade_in=0.0, fade_out=0.0, loop=loop)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "capital.ogg"
    y = build()
    S.write_ogg(out, y, S.QUALITY)
    print("wrote", out, "%.2f s" % (len(y) / S.SR), "peak %.2f" % np.max(np.abs(y)))
