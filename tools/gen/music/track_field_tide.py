# -*- coding: utf-8 -*-
"""潮殻の浜 (field_tide、土地 3)。変ロ長調 6/8 (8 分音符 = 168)。寄せては返す波: ハープの分散和音、フルートの旋律、
クラリネットの合わせ、弦のうねり、シンバルの柔らかいロール。
形: A 8 (フルート) → A 8 (+ クラリネット) → B 8 (弦とフルート) → A 8 (全部) → 戻り 2 (Eb | F)
"""
from arrange import Song

BPM, BAR = 168, 6
A1, A2, B, A3, TURN, BARS = 0, 8, 16, 24, 32, 34

A_CH = "Bb | F/A | Gm | Eb | Bb | F | Eb | F"
B_CH = "Gm | Eb | Bb | F | Gm | Eb | Cm | F"
TURN_CH = "Eb | F"
A_MEL = ("F5:3 D5:2 Bb4:1 | C5:3 A4:3 | Bb4:2 D5:1 G5:3 | G5:4 F5:1 Eb5:1 | "
         "D5:3 F5:2 Bb5:1 | A5:3 F5:2 C5:1 | G5:2 F5:1 Eb5:3 | F5:6")
A_LOW = ("D5:3 Bb4:2 F4:1 | A4:3 F4:3 | G4:2 Bb4:1 D5:3 | Eb5:4 D5:1 C5:1 | "
         "Bb4:3 D5:2 F5:1 | F5:3 C5:2 A4:1 | Eb5:2 D5:1 Bb4:3 | C5:6")
B_MEL = ("G5:3 Bb5:3 | Bb5:2 G5:1 Eb5:3 | F5:3 D5:3 | C5:2 D5:1 F5:3 | "
         "D5:2 G5:1 Bb5:3 | Bb5:3 G5:3 | Eb5:2 G5:1 C6:3 | A5:6")
TURN_MEL = "G5:3 Eb5:3 | C5:3 Eb5:3"

CHORD_PLAN = [(A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "second")
LEVELS = {"melody": -7, "second": -11, "harp": -11, "pad": -15, "bass": -14, "cymbal": -24}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=81)
    wave = [0, 2, 3, 4, 5, 4]
    for prog, bar0 in ((A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)):
        s.arp("harp", prog, bar0, "harp", wave, step=1, vel=0.5, center=62, count=5, low=(43, 55), pan=0.3, wet=0.45, ring=2.4)
        s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 1.0),), vel=0.5, low=(34, 46))
        s.pad("strings", prog, bar0, "pad", vel=0.28 if bar0 == A1 else 0.45, center=60, overlap=1.1)
    s.line("flute", A_MEL, A1, "melody", pan=0.1, vel=0.72)
    s.line("flute", A_MEL, A2, "melody", pan=0.1, vel=0.72)
    s.line("clarinet", A_LOW, A2, "second", pan=-0.2, vel=0.55)
    s.line("strings_fast", B_MEL, B, "melody", pan=-0.1, vel=0.7)
    s.line("flute", "|".join(B_MEL.split("|")[4:]), B + 4, "second", pan=0.25, vel=0.55)
    s.line("flute", A_MEL, A3, "melody", pan=0.1, vel=0.78)
    s.line("clarinet", A_LOW, A3, "second", pan=-0.2, vel=0.58)
    s.line("flute", TURN_MEL, TURN, "melody", pan=0.1, vel=0.7)
    # 波の寄せ: 8 小節ごとに柔らかいシンバル
    for bar0 in (A2, B, A3):
        s.perc("cymbal", bar0, 1, [(0, 0.3)], "cymbal", pan=0.4, wet=0.6)
    return s.render(LEVELS, mute, reverb=3.2)
