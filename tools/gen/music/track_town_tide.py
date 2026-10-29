# -*- coding: utf-8 -*-
"""汐守の港 (town_tide)。ハ長調 6/8 (8 分音符 = 180)。浜の港の舟歌: アコーディオン、クラリネット、ギター、
ハープの波、ピチカートの低音、手太鼓。
形: A 8 (アコーディオン) → A 8 (+ クラリネットの 3 度下) → B 8 (クラリネットとフルート) → A 8 (全部) → 戻り 2 (F | G)
"""
from arrange import HAND_TO, Song

BPM, BAR = 180, 6
A1, A2, B, A3, TURN, BARS = 0, 8, 16, 24, 32, 34

A_CH = "C | Am | F | G | C | Am | Dm G | C"
B_CH = "F | C | G | Am | F | C | Dm | G"
TURN_CH = "F | G"
A_MEL = ("E5:3 G5:2 E5:1 | C5:3 A4:3 | A4:2 C5:1 F5:2 E5:1 | D5:6 | "
         "E5:2 F5:1 G5:3 | A5:2 G5:1 E5:3 | F5:3 D5:2 B4:1 | C5:6")
# 3 度下の合わせ (クラリネット)
A_LOW = ("C5:3 E5:2 C5:1 | A4:3 E4:3 | F4:2 A4:1 C5:2 C5:1 | B4:6 | "
         "C5:2 D5:1 E5:3 | E5:2 E5:1 C5:3 | D5:3 B4:2 G4:1 | E4:6")
B_MEL = ("A5:3 G5:2 F5:1 | E5:3 C5:3 | D5:2 E5:1 G5:3 | E5:6 | "
         "F5:2 A5:1 C6:3 | G5:3 E5:3 | F5:2 E5:1 D5:3 | D5:3 G4:3")
TURN_MEL = "A5:2 G5:1 F5:3 | D5:3 B4:2 D5:1"

CHORD_PLAN = [(A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "second")
LEVELS = {"melody": -7, "second": -11, "guitar": -12, "harp": -13, "pizz": -12, "pad": -17, "hand": -18, "tamb": -25}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=41)
    for prog, bar0 in ((A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)):
        s.arp("guitar", prog, bar0, "guitar", [0, 2, 3, 4, 3, 2], step=1, vel=0.55, center=60, pan=-0.35, wet=0.25)
        s.bassline("pizz", prog, bar0, "pizz", ((0, "R", 0.5), (3, "5", 0.5)), vel=0.7, low=(36, 48))
        s.pad("strings", prog, bar0, "pad", vel=0.3, center=60)
        n = 2 if bar0 == TURN else 8
        s.perc("hand", bar0, n, [(0, 0.5), (3, 0.35)], "hand", pan=-0.15, bend=HAND_TO["G"])
        # 波: ハープが上がって下りる
        s.arp("harp", prog, bar0, "harp", [1, 2, 3, 4, 3, 2], step=1, vel=0.42, center=67, pan=0.4, wet=0.5, ring=2.2)
    s.line("accordion", A_MEL, A1, "melody", pan=0.1, vel=0.7)
    s.line("accordion", A_MEL, A2, "melody", pan=0.1, vel=0.7)
    s.line("clarinet", A_LOW, A2, "second", pan=-0.2, vel=0.55)
    s.line("clarinet", B_MEL, B, "melody", pan=-0.1, vel=0.72)
    s.line("flute", "|".join(B_MEL.split("|")[4:]), B + 4, "second", pan=0.25, vel=0.5)
    s.line("accordion", A_MEL, A3, "melody", pan=0.1, vel=0.76)
    s.line("clarinet", A_LOW, A3, "second", pan=-0.2, vel=0.58)
    s.line("accordion", TURN_MEL, TURN, "melody", pan=0.1, vel=0.68)
    s.perc("tamb", A3, 8, [(0, 0.3), (3, 0.25)], "tamb", pan=0.45)
    return s.render(LEVELS, mute)
