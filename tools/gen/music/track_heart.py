# -*- coding: utf-8 -*-
"""巨神の心臓 (heart、心臓と心臓の番人との戦い)。ヘ短調 4/4、138。最後の戦い: 合唱、全部の弦、ホルンとトロンボーン、
和太鼓・ティンパニ・シンバル。巨神の動機 = F → C → Db → C (短 6 度のため息)。
形: 前 2 (和太鼓と低い弦の動機) → A 8 (合唱) → A 8 (合唱 + ホルン) → B 8 (バイオリン) → C 8 (変イ長調へ持ち上がる)
  → 戻り 2 (Db | C、たたみかけて頭へ)
"""
from arrange import TAIKO_TO, Song

BPM, BAR = 138, 4
INTRO, A1, A2, B, C, TURN, BARS = 0, 2, 10, 18, 26, 34, 36

INTRO_CH = "Fm | Fm"
A_CH = "Fm | Db | Eb | C | Fm | Db | Bbm | C"
B_CH = "Db | Eb | Cm | Fm | Db | Eb | Bbm C | C"
C_CH = "Ab | Eb | Fm | Db | Ab | Eb | Bbm | C"
TURN_CH = "Db | C"
MOTIF = "F3:1 C4:1 Db4:1.5 C4:.5 | F3:1 C4:1 Db4:1.5 C4:.5"
A_MEL = ("F4:2 C5:2 | Db5:2 C5:2 | Bb4:2 G4:2 | C5:4 | F4:1 Ab4:1 C5:2 | F5:2 Db5:2 | Bb4:1 Db5:1 F5:2 | E5:2 C5:1 G4:1")
B_MEL = ("F5:2 Ab5:2 | G5:2 Eb5:2 | Eb5:1.5 D5:.5 C5:2 | Ab4:2 C5:2 | Db5:1 F5:1 Ab5:2 | Bb5:2 G5:2 | F5:2 E5:2 | G5:4")
C_MEL = ("C5:2 Eb5:2 | Eb5:2 Bb4:2 | Ab4:1 C5:1 F5:2 | F5:2 Ab5:2 | Ab5:2 Eb5:2 | G5:2 Bb5:2 | Bb5:2 Db6:2 | C6:2 G5:1 E5:1")

CHORD_PLAN = [(INTRO_CH, INTRO), (A_CH, A1), (A_CH, A2), (B_CH, B), (C_CH, C), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "second", "motif")
LEVELS = {"melody": -6, "second": -9, "motif": -11, "strings": -12, "brass": -12, "bass": -12, "taiko": -11, "timp": -14,
          "cymbal": -19}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=131)
    ost = [0, 1, 2, 1, 0, 1, 2, 3]
    secs = ((INTRO_CH, INTRO, 2), (A_CH, A1, 8), (A_CH, A2, 8), (B_CH, B, 8), (C_CH, C, 8), (TURN_CH, TURN, 2))
    for prog, bar0, n in secs:
        s.arp("cello", prog, bar0, "strings", ost, step=0.5, vel=0.6, center=53, count=3, low=(36, 48), pan=-0.2,
              wet=0.25, ring=0.9)
        s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 0.5), (2, "R", 0.5)), vel=0.65, low=(29, 41))
        s.perc("taiko", bar0, n, [(0, 0.75), (1, 0.4), (2, 0.6), (2.5, 0.4), (3, 0.5)], "taiko", pan=-0.1, wet=0.35, bend=TAIKO_TO["F"])
        s.timp(prog, bar0, [(0, 0.5), (2, 0.4)], "timp", wet=0.4)
        if bar0 != INTRO:
            s.pad("strings", prog, bar0, "strings", vel=0.5, center=62, count=4)
            s.pad("brass", prog, bar0, "brass", vel=0.55 if bar0 != A1 else 0.4, center=55, count=3, overlap=0.5)
        if bar0 in (A1, A2, B, C):
            s.perc("crash", bar0, 1, [(0, 0.5)], "cymbal", pan=0.3, wet=0.5)
    s.line("cello", MOTIF, INTRO, "motif", pan=-0.2, vel=0.8)
    s.line("contrabass", MOTIF, INTRO, "motif", pan=0.2, vel=0.7, octave=-1)
    s.line("choir", A_MEL, A1, "melody", pan=0.0, vel=0.75, legato=1.0)
    s.line("choir", A_MEL, A2, "melody", pan=0.0, vel=0.8, legato=1.0)
    s.line("horn", A_MEL, A2, "second", pan=-0.2, vel=0.65)
    s.line("violin", B_MEL, B, "melody", pan=0.1, vel=0.82)
    s.line("choir", B_MEL, B, "second", pan=0.0, vel=0.55, octave=-1, legato=1.0)
    s.line("violin", C_MEL, C, "melody", pan=0.1, vel=0.85)
    s.line("choir", C_MEL, C, "second", pan=0.0, vel=0.65, octave=-1, legato=1.0)
    s.line("trombone", "|".join(C_MEL.split("|")[4:]), C + 4, "second", pan=-0.25, vel=0.6, octave=-1)
    s.line("cello", MOTIF, TURN, "motif", pan=-0.2, vel=0.8)
    s.line("horn", MOTIF, TURN, "motif", pan=0.2, vel=0.7, octave=1)
    s.perc("snare", TURN + 1, 1, [(2, 0.4), (2.5, 0.45), (3, 0.5), (3.25, 0.5), (3.5, 0.6), (3.75, 0.65)], "taiko", pan=0.2)
    return s.render(LEVELS, mute, reverb=3.0, damp=0.55)
