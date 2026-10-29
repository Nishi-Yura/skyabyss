# -*- coding: utf-8 -*-
"""ボス (boss)。ニ短調 4/4、152。追い立てる戦い: 弦の 8 分の刻み、金管の強音、ホルンの旋律、和太鼓・スネア・ティンパニ、
合唱の強音。
形: 前 2 (刻みと和太鼓) → A 8 (ホルン) → A 8 (+ 合唱・金管) → B 8 (バイオリン) → B 8 (+ ホルンの 8 度下) → A 8 (全部)
  → 戻り 2 (Bb | A、太鼓のたたみかけで頭へ)
"""
from arrange import TAIKO_TO, Song

BPM, BAR = 152, 4
INTRO, A1, A2, B1, B2, A3, TURN, BARS = 0, 2, 10, 18, 26, 34, 42, 44

INTRO_CH = "Dm | Dm"
A_CH = "Dm | Bb | C | A | Dm | Bb | Gm | A"
B_CH = "Gm | Dm | Bb | A | Gm | Dm | Bb C | A"
TURN_CH = "Bb | A"
A_MEL = ("D5:1.5 A4:.5 D5:1 F5:1 | F5:1.5 E5:.5 D5:2 | E5:1 G5:1 E5:1 C5:1 | C#5:2 E5:2 | "
         "D5:1.5 A4:.5 D5:1 F5:1 | D5:1 F5:1 Bb5:2 | G5:1.5 F5:.5 D5:2 | E5:2 C#5:1 A4:1")
B_MEL = ("Bb4:2 D5:2 | F5:2 A5:2 | F5:1.5 D5:.5 Bb4:2 | C#5:2 E5:1 A5:1 | "
         "G5:2 Bb5:2 | A5:2 F5:1 D5:1 | D5:2 E5:2 | C#5:4")

CHORD_PLAN = [(INTRO_CH, INTRO), (A_CH, A1), (A_CH, A2), (B_CH, B1), (B_CH, B2), (A_CH, A3), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "second")
LEVELS = {"melody": -6, "second": -10, "ostinato": -11, "stab": -12, "choir": -14, "bass": -12, "taiko": -12, "snare": -17,
          "timp": -15, "cymbal": -20}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=121)
    ost = [0, 0, 1, 0, 2, 0, 1, 0]
    secs = ((INTRO_CH, INTRO, 2), (A_CH, A1, 8), (A_CH, A2, 8), (B_CH, B1, 8), (B_CH, B2, 8), (A_CH, A3, 8), (TURN_CH, TURN, 2))
    for prog, bar0, n in secs:
        s.arp("cello", prog, bar0, "ostinato", ost, step=0.5, vel=0.62, center=50, count=3, low=(38, 50), pan=-0.2,
              wet=0.2, ring=0.85)
        s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 0.5), (2, "R", 0.5)), vel=0.65, low=(33, 45))
        s.perc("taiko", bar0, n, [(0, 0.7), (1.5, 0.4), (2, 0.55), (3, 0.45), (3.5, 0.4)], "taiko", pan=-0.1, wet=0.3, bend=TAIKO_TO["D"])
        if bar0 != INTRO:
            s.perc("snare", bar0, n, [(1, 0.35), (3, 0.4)], "snare", pan=0.2)
            # 金管の強音 (拍の頭と 3 拍目)
            s.pad("brass", prog, bar0, "stab", vel=0.6, center=57, count=3, overlap=0.18)
        if bar0 in (A2, B2, A3):
            s.pad("choir", prog, bar0, "choir", vel=0.55, center=60, count=4)
            s.perc("crash", bar0, 1, [(0, 0.5)], "cymbal", pan=0.3, wet=0.5)
    s.line("horn", A_MEL, A1, "melody", pan=-0.1, vel=0.8)
    s.line("horn", A_MEL, A2, "melody", pan=-0.1, vel=0.85)
    s.line("trumpet", A_MEL, A2, "second", pan=0.2, vel=0.55)
    s.line("violin", B_MEL, B1, "melody", pan=0.1, vel=0.8)
    s.line("violin", B_MEL, B2, "melody", pan=0.1, vel=0.85)
    s.line("horn", B_MEL, B2, "second", pan=-0.2, vel=0.6, octave=-1)
    s.line("horn", A_MEL, A3, "melody", pan=-0.1, vel=0.88)
    s.line("violin", A_MEL, A3, "second", pan=0.2, vel=0.6, octave=1)
    s.perc(("timpani", 50), TURN, 2, [(0, 0.5), (1, 0.4), (2, 0.5), (2.5, 0.45), (3, 0.55), (3.5, 0.6)], "timp")
    return s.render(LEVELS, mute, reverb=2.4, damp=0.6)
