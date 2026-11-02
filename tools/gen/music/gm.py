# -*- coding: utf-8 -*-
"""合成の楽器を、音源 (GeneralUser GS。sf2.py) の生の楽器に差し替える。

use_soundfont() を呼ぶと synth の楽器の関数 (flute・lute・harp・strings …) が音源の楽器になる。
曲の楽譜はそのまま。楽器の選び (bank, program) と音程の補い (セント) はこの表で決める。
"""
import numpy as np

import sf2
import synth as S

# 役 → (bank, program, 音程の補い セント)。補いは check.py の測りで決めた (録音そのものの高さのずれ)
TABLE = {
    "flute": (0, 73, 0),        # Flute
    "lute": (0, 24, 0),         # Nylon Guitar (リュートの代わり)
    "harp": (0, 46, 0),         # Orchestral Harp
    "strings": (0, 49, 0),      # Slow Strings (和音の敷き)
    "strings_fast": (0, 48, 0),  # Fast Strings (旋律を弾く弦)
    "choir": (0, 52, -20),      # Concert Choir (録音が 20 セントほど高い)
    "bell": (8, 14, 0),         # Church Bells
    "celesta": (0, 8, 0),       # Celeste
    "bass": (0, 32, 0),         # Acoustic Bass
    "pizz": (0, 45, 0),         # Pizzicato Strings
    "timpani": (0, 47, 0),
    "taiko": (0, 116, 0),
    "tambourine": (12, 119, 0),
    "kit": (120, 48, 0),        # Orchestral Kit (打楽器は鍵盤の番号で選ぶ)
}


def midi_vel(vel):
    return int(np.clip(40 + 87 * vel, 1, 127))


def play(role, m, dur, vel, **kw):
    b, p, tune = TABLE[role]
    return sf2.bank().note(b, p, int(m), dur, midi_vel(vel), pitch_bend_cents=tune + kw.get("bend", 0.0))


def use_soundfont():
    # 合成の楽器も残す (重ねるときに使う。例: 鐘の低い響き)
    if not S.SYNTH:
        S.SYNTH.update(S.INSTRUMENTS)
    S.flute = lambda m, dur, vel=0.8, **kw: play("flute", m, dur, vel)
    S.lute = lambda m, dur, vel=0.8, **kw: play("lute", m, dur, vel)
    S.harp = lambda m, dur, vel=0.8, **kw: play("harp", m, dur, vel)
    # 立ち上がりの速い弦 (旋律) と遅い弦 (和音の敷き) を分ける
    S.strings = lambda m, dur, vel=0.7, attack=0.35, release=0.7, bright=1.0: play(
        "strings_fast" if attack < 0.2 else "strings", m, dur, vel)
    S.choir = lambda m, dur, vel=0.6, **kw: play("choir", m, dur, vel)
    S.bell = lambda m, dur, vel=0.8, length=6.0: play("bell", m, max(dur, 1.0), vel)
    S.celesta = lambda m, dur, vel=0.7, **kw: play("celesta", m, dur, vel)
    S.bass = lambda m, dur, vel=0.8, **kw: play("bass", m, dur, vel)
    S.drum = lambda m, dur, vel=0.7, **kw: play("kit", 36, 0.3, vel * 0.8)    # 演奏会の大太鼓 (弱く)
    S.shaker = lambda m, dur, vel=0.5, **kw: play("tambourine", 66, 0.2, vel)
    S.taiko = lambda m, dur, vel=0.8, **kw: play("taiko", 48, 0.5, vel)
    S.INSTRUMENTS.update(flute=S.flute, lute=S.lute, harp=S.harp, strings=S.strings, choir=S.choir, bell=S.bell,
                         celesta=S.celesta, bass=S.bass, drum=S.drum, shaker=S.shaker, taiko=S.taiko)
