# -*- coding: utf-8 -*-
"""巨神の背 (back、試練の道)。ハ短調 4/4、96。謎めいた緊張: 低い弦のピチカートの刻み、合唱 (ウー) の敷き、
チェレスタの動機 (巨神の歌)、ティンパニの脈、遠いチャイム。
形: A 8 (刻み + チェレスタ) → A 8 (+ 合唱・ティンパニ) → B 8 (合唱が歌う) → A 8 (全部) → 戻り 2 (Ab | G)
"""
from arrange import Song

BPM, BAR = 96, 4
A1, A2, B, A3, TURN, BARS = 0, 8, 16, 24, 32, 34

A_CH = "Cm | Ab | Fm | G | Cm | Ab | Db | G"
B_CH = "Ab | Eb | Fm | Cm | Ab | Eb | Fm | G"
TURN_CH = "Ab | G"
A_MEL = ("G5:1 Eb5:1 C5:2 | C6:1 Ab5:1 Eb5:2 | F5:1 Ab5:1 C6:1 Ab5:1 | B4:2 D5:1 G5:1 | "
         "G5:1 Eb5:1 C5:2 | Ab5:2 G5:1 Eb5:1 | F5:1 Ab5:1 Db6:2 | B5:4")
B_MEL = ("C5:4 | Bb4:2 G4:2 | Ab4:2 C5:2 | G4:4 | Eb5:2 C5:2 | Eb5:2 Bb4:2 | F5:2 Ab5:2 | G5:2 F5:1 D5:1")
TURN_MEL = "Eb5:2 C5:2 | B4:2 D5:2"

CHORD_PLAN = [(A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "choir_mel")
LEVELS = {"melody": -9, "choir_mel": -8, "ostinato": -12, "choir": -15, "bass": -13, "timp": -16, "chimes": -24}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=111)
    ost = [0, None, 2, 0, 1, None, 2, 1]
    for prog, bar0, n in ((A_CH, A1, 8), (A_CH, A2, 8), (B_CH, B, 8), (A_CH, A3, 8), (TURN_CH, TURN, 2)):
        s.arp("pizz", prog, bar0, "ostinato", ost, step=0.5, vel=0.6, center=52, count=3, low=(36, 48), pan=-0.2, wet=0.3,
              ring=0.8)
        s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 1.0),), vel=0.45, low=(31, 43))
        if bar0 != A1:
            s.pad("oohs", prog, bar0, "choir", vel=0.45, center=60, count=4)
            s.timp(prog, bar0, [(0, 0.45), (2, 0.25)], "timp", wet=0.4)
        s.perc("chimes", bar0, n // 2 if n > 1 else 1, [(0, 0.2)], "chimes", pan=0.5, wet=0.7)
    s.line("celesta", A_MEL, A1, "melody", pan=0.3, vel=0.65)
    s.line("celesta", A_MEL, A2, "melody", pan=0.3, vel=0.68)
    s.line("oohs", B_MEL, B, "choir_mel", pan=0.0, vel=0.65, legato=1.0)
    s.line("celesta", A_MEL, A3, "melody", pan=0.3, vel=0.72)
    s.line("glock", A_MEL, A3, "melody", pan=-0.3, vel=0.4)
    s.line("oohs", TURN_MEL, TURN, "choir_mel", pan=0.0, vel=0.6, legato=1.0)
    s.perc(("timpani", 43), TURN + 1, 1, [(2, 0.35), (2.5, 0.4), (3, 0.45), (3.5, 0.55)], "timp")
    return s.render(LEVELS, mute, reverb=3.6, damp=0.5)
