# -*- coding: utf-8 -*-
"""月 (moon)。ホ・リディア (E F# G# A# B C# D#) 3/4、66。浮遊感: オルゴールの旋律、合唱 (ウー) の敷き、ハープの分散和音、
チェレスタ、グロッケンのきらめき、柔らかい低音。リディアの #4 (A#) が月の明るい不思議さ。
形: A 8 (オルゴール + 合唱) → A 8 (+ ハープ・チェレスタ) → B 8 (合唱が歌い、グロッケンがきらめく) → A 8 (全部) → 戻り 2 (F#/E | B)
"""
from arrange import Song

BPM, BAR = 66, 3
A1, A2, B, A3, TURN, BARS = 0, 8, 16, 24, 32, 34

A_CH = "E | F#/E | G#m | F# | E | F#/E | C#m7 | B"
B_CH = "C#m | G#m | E | F# | C#m | G#m | E | B"
TURN_CH = "F#/E | B"
A_MEL = ("B5:2 G#5:1 | A#5:2 C#6:1 | B5:1 D#6:1 B5:1 | A#5:3 | E6:2 D#6:1 | C#6:2 A#5:1 | B5:1 G#5:1 E5:1 | F#5:3")
B_MEL = ("E6:2 C#6:1 | D#6:2 B5:1 | G#5:1 B5:1 E6:1 | C#6:3 | G#5:2 E5:1 | B5:2 G#5:1 | A#5:1 B5:1 G#5:1 | F#5:3")
TURN_MEL = "A#5:3 | B5:2 D#5:1"

CHORD_PLAN = [(A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "second", "sparkle")
LEVELS = {"melody": -8, "second": -12, "sparkle": -20, "pad": -13, "harp": -13, "bass": -17}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=151)
    for prog, bar0 in ((A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)):
        s.pad("oohs", prog, bar0, "pad", vel=0.45, center=64, count=4, overlap=1.1)
        s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 1.0),), vel=0.4, low=(36, 48))
        if bar0 != A1:
            s.arp("harp", prog, bar0, "harp", [0, 2, 3, 4, 5, 4], step=0.5, vel=0.45, center=66, count=5, low=(40, 52), pan=0.35,
                  wet=0.55, ring=2.5)
    s.line("musicbox", A_MEL, A1, "melody", pan=0.15, vel=0.7)
    s.line("musicbox", A_MEL, A2, "melody", pan=0.15, vel=0.72)
    s.line("celesta", A_MEL, A2, "second", pan=-0.25, vel=0.5, octave=-1)
    s.line("oohs", B_MEL, B, "melody", pan=0.0, vel=0.62, octave=-1, legato=1.0)
    s.line("glock", B_MEL, B, "sparkle", pan=0.35, vel=0.45)
    s.line("musicbox", A_MEL, A3, "melody", pan=0.15, vel=0.75)
    s.line("celesta", A_MEL, A3, "second", pan=-0.25, vel=0.55, octave=-1)
    s.line("musicbox", TURN_MEL, TURN, "melody", pan=0.15, vel=0.68)
    return s.render(LEVELS, mute, reverb=4.2, damp=0.45)
