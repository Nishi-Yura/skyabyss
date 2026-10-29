# -*- coding: utf-8 -*-
"""岩鯨の丘陵 (field_whale、土地 2)。ホ短調 4/4、112。冒険の行進: 弦の 8 分の刻み、ホルンの旋律、ティンパニ、軽いスネア。
形: A 8 (刻み + ホルン) → A 8 (+ フルートの 8 度上・スネア) → B 8 (バイオリンが歌う) → A 8 (全部) → 戻り 2 (C | B7)
"""
from arrange import Song

BPM, BAR = 112, 4
A1, A2, B, A3, TURN, BARS = 0, 8, 16, 24, 32, 34

A_CH = "Em | C | G | D | Em | C | Am | B"
B_CH = "C | D | G | Em | C | Am | B7 | B7"
TURN_CH = "C | B7"
A_MEL = ("B4:2 E5:1 G5:1 | G5:2 E5:1.5 C5:.5 | D5:2 B4:1 G4:1 | A4:3 D5:1 | "
         "B4:2 E5:1 G5:1 | G5:1.5 A5:.5 G5:1 E5:1 | E5:2 C5:1 A4:1 | B4:2 D#5:1 F#5:1")
B_MEL = ("G5:2 E5:2 | F#5:2 D5:1 A4:1 | B4:1.5 D5:.5 G5:2 | E5:3 B4:1 | "
         "C5:1 E5:1 G5:1 C6:1 | A5:2 E5:1 C5:1 | D#5:1 F#5:1 A5:1 B5:1 | B5:2 A5:1 F#5:1")
TURN_MEL = "E5:2 G5:2 | F#5:2 D#5:2"

CHORD_PLAN = [(A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "flute")
LEVELS = {"melody": -6, "flute": -11, "ostinato": -12, "pad": -16, "bass": -12, "timp": -15, "snare": -21, "cymbal": -22}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=71)
    ost = [0, 1, 2, 1, 3, 1, 2, 1]
    for prog, bar0, n in ((A_CH, A1, 8), (A_CH, A2, 8), (B_CH, B, 8), (A_CH, A3, 8), (TURN_CH, TURN, 2)):
        s.arp("cello", prog, bar0, "ostinato", ost, step=0.5, vel=0.55, center=55, count=3, low=(40, 52), pan=-0.25,
              wet=0.25, ring=0.9)
        s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 0.5), (2, "R", 0.5)), vel=0.6, low=(36, 48))
        s.pad("strings", prog, bar0, "pad", vel=0.3 if bar0 == A1 else 0.45, center=60)
        s.timp(prog, bar0, [(0, 0.55), (2, 0.35)], "timp", wet=0.35)
        if bar0 != A1:
            s.perc("snare", bar0, n, [(1, 0.25), (3, 0.3), (3.5, 0.2)], "snare", pan=0.2)
    s.line("horn", A_MEL, A1, "melody", pan=-0.1, vel=0.75)
    s.line("horn", A_MEL, A2, "melody", pan=-0.1, vel=0.78)
    s.line("flute", A_MEL, A2, "flute", pan=0.25, vel=0.55, octave=1)
    s.line("violin", B_MEL, B, "melody", pan=0.1, vel=0.75)
    s.line("horn", "|".join(B_MEL.split("|")[4:]), B + 4, "flute", pan=-0.2, vel=0.5, octave=-1)
    s.line("horn", A_MEL, A3, "melody", pan=-0.1, vel=0.82)
    s.line("violin", A_MEL, A3, "flute", pan=0.25, vel=0.55, octave=1)
    s.line("horn", TURN_MEL, TURN, "melody", pan=-0.1, vel=0.75)
    s.perc("cymbal", A3, 1, [(0, 0.45)], "cymbal", pan=0.3, wet=0.5)
    s.perc(("timpani", 47), TURN + 1, 1, [(2, 0.4), (2.5, 0.45), (3, 0.5), (3.5, 0.6)], "timp")
    return s.render(LEVELS, mute)
