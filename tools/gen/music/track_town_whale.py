# -*- coding: utf-8 -*-
"""鯨見の町 (town_whale)。イ・ドリア 6/8 (8 分音符 = 204)。鯨追いの丘の町の陽気なジグ:
ティンホイッスル、マンドリン、フィドル (バイオリン)、手太鼓 (バウロンの代わり)、アコーディオンの敷き。
形: A 8 (笛) → A 8 (笛 + フィドルの合わせ) → B 8 (フィドル) → A 8 (全部) → 戻り 2 (G | Em)
"""
from arrange import HAND_TO, Song

BPM, BAR = 204, 6
A1, A2, B, A3, TURN, BARS = 0, 8, 16, 24, 32, 34

A_CH = "Am | G | D | Am | Am | G | Em | Am"
B_CH = "C | G | D | Am | C | G | Em D | Am"
TURN_CH = "G | Em"
A_MEL = ("A4:2 C5:1 E5:2 C5:1 | D5:2 B4:1 G4:2 B4:1 | A4:2 F#4:1 D5:2 F#5:1 | E5:3 A4:3 | "
         "A4:2 C5:1 E5:2 A4:1 | G5:2 D5:1 B4:2 D5:1 | E5:2 G5:1 E5:2 B4:1 | A4:6")
B_MEL = ("G4:2 C5:1 E5:2 G5:1 | D5:2 G5:1 B4:2 D5:1 | F#5:2 E5:1 D5:2 A4:1 | C5:3 E5:3 | "
         "E5:2 G5:1 E5:2 C5:1 | D5:2 B4:1 G4:2 B4:1 | E5:3 F#5:3 | E5:3 A4:3")
TURN_MEL = "D5:2 B4:1 G4:3 | E5:2 G5:1 B4:3"

CHORD_PLAN = [(A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "fiddle")
LEVELS = {"melody": -7, "fiddle": -9, "mandolin": -11, "accordion": -17, "bass": -13, "hand": -16, "tamb": -24}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=31)
    strum = [0, 2, 3, 1, 3, 2]
    for prog, bar0 in ((A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)):
        s.arp("mandolin", prog, bar0, "mandolin", strum, step=1, vel=0.6, center=64, pan=-0.35, wet=0.22, ring=1.3, low=(52, 64))
        s.bassline("bass", prog, bar0, "bass", ((0, "R", 0.5), (3, "5", 0.5)), vel=0.6, low=(40, 52))
        s.pad("accordion", prog, bar0, "accordion", vel=0.35, center=57, count=3)
        n = 2 if bar0 == TURN else 8
        s.perc("hand", bar0, n, [(0, 0.6), (2, 0.3), (3, 0.45), (5, 0.3)], "hand", pan=-0.15, bend=HAND_TO["A"])
    s.line("whistle", A_MEL, A1, "melody", pan=0.15, vel=0.72, octave=1)
    s.line("whistle", A_MEL, A2, "melody", pan=0.15, vel=0.72, octave=1)
    s.line("violin", A_MEL, A2, "fiddle", pan=-0.2, vel=0.55)
    s.line("violin", B_MEL, B, "fiddle", pan=-0.2, vel=0.72)
    s.line("whistle", A_MEL, A3, "melody", pan=0.15, vel=0.78, octave=1)
    s.line("violin", A_MEL, A3, "fiddle", pan=-0.2, vel=0.6)
    s.line("whistle", TURN_MEL, TURN, "melody", pan=0.15, vel=0.7, octave=1)
    s.perc("tamb", A3, 8, [(0, 0.3), (3, 0.3)], "tamb", pan=0.45)
    return s.render(LEVELS, mute)
