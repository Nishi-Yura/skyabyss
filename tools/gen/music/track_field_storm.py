# -*- coding: utf-8 -*-
"""雷翼の高地 (field_storm、土地 4)。ロ短調 4/4、126。嵐の緊張: トレモロの弦、和太鼓とティンパニ、尺八とフルートの重ね、
ホルンの短い強音。
形: A 8 (トレモロ + 尺八/フルート) → A 8 (+ ホルン・和太鼓) → B 8 (ホルンが歌う) → A 8 (全部) → 戻り 2 (Em | F#)
"""
from arrange import TAIKO_TO, Song

BPM, BAR = 126, 4
A1, A2, B, A3, TURN, BARS = 0, 8, 16, 24, 32, 34

A_CH = "Bm | G | A | F# | Bm | G | Em | F#"
B_CH = "G | A | Bm | Bm | G | A | F#sus4 F# | F#"
TURN_CH = "Em | F#"
A_MEL = ("F#5:2 B5:1 A5:1 | G5:1.5 F#5:.5 D5:2 | E5:1 F#5:1 A5:1 E5:1 | F#5:2 C#5:2 | "
         "B4:1 D5:1 F#5:2 | G5:2 B5:1 G5:1 | E5:1 G5:1 B5:1.5 A5:.5 | A#5:2 F#5:2")
B_MEL = ("D5:2 B4:2 | C#5:2 E5:2 | F#5:3 E5:.5 D5:.5 | D5:2 B4:2 | "
         "G5:2 D5:1 B4:1 | A4:1 C#5:1 E5:2 | B4:2 A#4:2 | C#5:4")
TURN_MEL = "G5:2 E5:2 | C#5:2 E5:1 F#5:1"

CHORD_PLAN = [(A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "second")
LEVELS = {"melody": -6, "second": -10, "tremolo": -12, "bass": -12, "taiko": -14, "timp": -16, "stab": -14, "cymbal": -22}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=91)
    for prog, bar0, n in ((A_CH, A1, 8), (A_CH, A2, 8), (B_CH, B, 8), (A_CH, A3, 8), (TURN_CH, TURN, 2)):
        s.pad("tremolo", prog, bar0, "tremolo", vel=0.5 if bar0 == A1 else 0.62, center=59, count=4)
        s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 0.5), (2, "R", 0.25), (3, "R", 0.25)), vel=0.6, low=(35, 47))
        s.timp(prog, bar0, [(0, 0.5), (3.5, 0.35)], "timp", wet=0.4)
        if bar0 != A1:
            s.perc("taiko", bar0, n, [(0, 0.6), (1.5, 0.35), (2, 0.45), (3.5, 0.4)], "taiko", pan=-0.1, wet=0.35, bend=TAIKO_TO["F#"])
            # ホルンの短い強音 (和音を拍の頭に)
            s.pad("brass", prog, bar0, "stab", vel=0.55, center=57, count=3, overlap=0.25)
    s.line("shakuhachi", A_MEL, A1, "melody", pan=0.1, vel=0.75)
    s.line("flute", A_MEL, A1, "second", pan=-0.15, vel=0.5)
    s.line("shakuhachi", A_MEL, A2, "melody", pan=0.1, vel=0.78)
    s.line("flute", A_MEL, A2, "second", pan=-0.15, vel=0.55)
    s.line("horn", B_MEL, B, "melody", pan=-0.1, vel=0.8)
    s.line("strings_fast", B_MEL, B, "second", pan=0.2, vel=0.5, octave=1)
    s.line("shakuhachi", A_MEL, A3, "melody", pan=0.1, vel=0.82)
    s.line("horn", A_MEL, A3, "second", pan=-0.2, vel=0.55, octave=-1)
    s.line("shakuhachi", TURN_MEL, TURN, "melody", pan=0.1, vel=0.75)
    for bar0 in (A2, A3):
        s.perc("crash", bar0, 1, [(0, 0.4)], "cymbal", pan=0.3, wet=0.5)
    return s.render(LEVELS, mute, reverb=3.0)
