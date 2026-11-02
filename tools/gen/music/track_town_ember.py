# -*- coding: utf-8 -*-
"""灰炉の村 (town_ember)。ニ短調 3/4、76。灰の荒野の生き残りの村。温かく哀しい: チェロ、イングリッシュホルン、オーボエ、
ギター、弦の敷き、遠い手太鼓。
形: A 8 (チェロ) → A 8 (チェロ + ギターの合いの手) → B 8 (イングリッシュホルンと弦) → A 8 (オーボエが 8 度上で歌い、チェロが支える)
  → 戻り 2 (Gm | A7、チェロの拾い上げで A の頭へ)
"""
from arrange import HAND_TO, Song

BPM, BAR = 76, 3
A1, A2, B, A3, TURN, BARS = 0, 8, 16, 24, 32, 34

A_CH = "Dm | Gm | C | F | Bb | Gm | A7 | Dm"
B_CH = "F | C | Dm | Bb | Gm | Dm | E7 | A"
TURN_CH = "Gm | A7"
A_MEL = ("A3:2 D4:1 | D4:1.5 C4:.5 Bb3:1 | C4:1 E4:1 G4:1 | F4:3 | "
         "F4:1 D4:1 Bb3:1 | G4:1.5 F4:.5 D4:1 | E4:1 C#4:1 A3:1 | D4:3")
B_MEL = ("A4:2 C5:1 | G4:2 E4:1 | F4:1 A4:1 D5:1 | D5:2 Bb4:1 | "
         "Bb4:1 A4:1 G4:1 | F4:1.5 E4:.5 D4:1 | G#4:2 B4:1 | A4:3")
TURN_MEL = "Bb3:1 C4:1 D4:1 | C#4:2 E4:1"

CHORD_PLAN = [(A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "second")
LEVELS = {"melody": -7, "second": -11, "guitar": -12, "pad": -15, "bass": -14, "hand": -21}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=61)
    gp = [0, 2, 3, 4, 3, 2]
    for prog, bar0 in ((A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)):
        s.arp("guitar", prog, bar0, "guitar", gp, step=0.5, vel=0.5, center=57, pan=-0.35, wet=0.3, ring=2.0)
        s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 1.0),), vel=0.5, low=(36, 48))
        s.pad("strings", prog, bar0, "pad", vel=0.28 if bar0 < B else 0.42, center=57)
        n = 2 if bar0 == TURN else 8
        if bar0 >= B:
            s.perc("hand", bar0, n, [(0, 0.35)], "hand", pan=-0.2, wet=0.4, bend=HAND_TO["A"])
    s.line("cello", A_MEL, A1, "melody", pan=-0.1, vel=0.72, legato=0.98)
    s.line("cello", A_MEL, A2, "melody", pan=-0.1, vel=0.72, legato=0.98)
    s.line("english_horn", B_MEL, B, "melody", pan=0.1, vel=0.72, legato=0.97)
    s.line("oboe", A_MEL, A3, "melody", pan=0.1, vel=0.72, octave=1, legato=0.97)
    s.line("cello", A_MEL, A3, "second", pan=-0.15, vel=0.55, legato=0.98)
    s.line("cello", TURN_MEL, TURN, "melody", pan=-0.1, vel=0.7, legato=0.98)
    return s.render(LEVELS, mute, reverb=3.0)
