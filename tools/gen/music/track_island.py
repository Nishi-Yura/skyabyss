# -*- coding: utf-8 -*-
"""はじまりの島 (island、土地 1)。ト長調 6/8 (8 分音符 = 192)。朝の目覚め: ティンホイッスル、ギター、ハープ、薄い弦、振り物。
形: A 8 (笛) → A2 8 (笛 + ハープ) → B 8 (フルートと弦。短調の陰り) → A2' 8 (笛 + クラリネットの合わせ) → 戻り 2 (C | D)
拍は 8 分音符で数える (一小節 6)
"""
from arrange import HAND_TO, Song

BPM, BAR = 192, 6
A, A2, B, A3, TURN, BARS = 0, 8, 16, 24, 32, 34

A_CH = "G | Em | C | D | G | Em | Am | D"
A2_CH = "G | Bm | C | G/B | Am | D/F# | Em C | D"
B_CH = "Em | C | G | D | Em | C | Am | D7"
TURN_CH = "C | D"
A_MEL = ("D5:3 B4:2 G4:1 | E5:3 D5:2 B4:1 | C5:2 E5:1 G5:3 | F#5:4 E5:1 D5:1 | "
         "D5:3 B4:2 D5:1 | G5:3 E5:2 B4:1 | A4:2 C5:1 E5:2 D5:1 | F#4:3 A4:3")
A2_MEL = ("G4:2 B4:1 D5:3 | D5:2 F#5:1 B5:3 | A5:2 G5:1 E5:3 | D5:3 B4:3 | "
          "C5:2 E5:1 A5:3 | F#5:2 E5:1 D5:3 | E5:3 G5:3 | A5:3 F#5:3")
B_MEL = ("B5:4 A5:1 G5:1 | E5:3 G5:3 | D5:2 E5:1 D5:2 B4:1 | A4:6 | "
         "E5:2 F#5:1 G5:3 | G5:2 A5:1 G5:2 E5:1 | C5:2 E5:1 A5:3 | F#5:3 C5:2 A4:1")
TURN_MEL = "E5:2 D5:1 C5:3 | A4:3 B4:1 C5:2"

CHORD_PLAN = [(A_CH, A), (A2_CH, A2), (B_CH, B), (A2_CH, A3), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "second")
LEVELS = {"melody": -7, "second": -12, "guitar": -11, "harp": -13, "pad": -16, "bass": -13, "shaker": -25, "hand": -20}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=21)
    gp = [0, 2, 3, 4, 3, 2]
    for prog, bar0, mel, role, oct_, vel in ((A_CH, A, A_MEL, "whistle", 0, 0.7), (A2_CH, A2, A2_MEL, "whistle", 0, 0.72),
                                            (B_CH, B, B_MEL, "flute", 0, 0.72), (A2_CH, A3, A2_MEL, "whistle", 0, 0.76),
                                            (TURN_CH, TURN, TURN_MEL, "whistle", 0, 0.68)):
        s.line(role, mel, bar0, "melody", pan=0.1, vel=vel, octave=oct_)
        s.arp("guitar", prog, bar0, "guitar", gp, step=1, vel=0.55, center=59, pan=-0.35, wet=0.25)
        s.bassline("bass", prog, bar0, "bass", ((0, "R", 0.5), (3, "5", 0.5)), vel=0.55, low=(40, 52))
        s.pad("strings", prog, bar0, "pad", vel=0.3 if bar0 < B else 0.45, center=60)
    s.arp("harp", A2_CH, A2, "harp", [None, None, None, 4, 3, 2], step=1, vel=0.45, center=67, pan=0.4, wet=0.45, ring=2)
    s.arp("harp", B_CH, B, "harp", [0, 2, 4, 5, 4, 2], step=1, vel=0.5, center=64, count=5, pan=0.4, wet=0.45, ring=2)
    s.line("strings_fast", B_MEL, B, "second", pan=-0.2, vel=0.45, octave=-1)
    s.line("clarinet", A2_MEL, A3, "second", pan=-0.25, vel=0.5, octave=-1)
    s.perc("shaker", A2, 24, [(0, 0.35), (2, 0.2), (3, 0.3), (5, 0.2)], "shaker", pan=0.45)
    s.perc("hand", B, 16, [(0, 0.45), (3, 0.3)], "hand", pan=-0.2, bend=HAND_TO["G"])
    s.gliss("harp", BARS * BAR - 2.5, "D4", "G", 9, beats=2.5)
    return s.render(LEVELS, mute)
