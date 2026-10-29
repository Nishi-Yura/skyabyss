# -*- coding: utf-8 -*-
"""風待の里 (town_storm)。ホ短調の五音音階 (E G A B D) 4/4、84。高地の風の民の里: 尺八、琴、弦の薄い敷き、風鈴、遠い和太鼓。
形: A 8 (尺八 + 琴の分散和音) → A 8 (+ 弦・和太鼓) → B 8 (琴が歌い、尺八が長く支える) → A 8 → 終わり 2 (琴だけ、Em | D → 頭へ)
"""
from arrange import TAIKO_TO, Song

BPM, BAR = 84, 4
A1, A2, B, A3, TAIL, BARS = 0, 8, 16, 24, 32, 34

A_CH = "Em | D | C | Em | Em | Am | C D | Em"
B_CH = "Am | Em | C | D | Am | Em | D | Em"
TAIL_CH = "Em | D"
A_MEL = ("E5:2 G5:1 A5:1 | A5:2 D5:2 | G5:3 E5:1 | E5:4 | "
         "B4:1 D5:1 E5:2 | E5:1 G5:1 A5:2 | G5:2 A5:1 D5:1 | B4:2 E5:2")
B_MEL = ("A5:2 B5:1 A5:1 | G5:2 E5:2 | E5:1 G5:1 E5:2 | D5:3 A4:1 | "
         "C5:1 E5:1 A5:2 | B5:2 G5:2 | A5:2 F#5:1 D5:1 | E5:4")
# B で尺八が長く支える音 (和音の音)
B_HOLD = "E5:4 | B4:4 | G4:4 | A4:4 | E5:4 | B4:4 | A4:4 | B4:4"

CHORD_PLAN = [(A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TAIL_CH, TAIL)]
MELODY_GROUPS = ("melody", "koto_lead", "hold")
LEVELS = {"melody": -7, "koto_lead": -8, "hold": -15, "koto": -12, "pad": -17, "bass": -15, "taiko": -18, "chimes": -24}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=51)
    kp = [0, 2, 3, 4, 3, 2, 4, 3]
    for prog, bar0, n in ((A_CH, A1, 8), (A_CH, A2, 8), (B_CH, B, 8), (A_CH, A3, 8), (TAIL_CH, TAIL, 2)):
        s.arp("koto", prog, bar0, "koto", kp, step=0.5, vel=0.55, center=69, low=(52, 64), pan=-0.3, wet=0.35, ring=2.0)
        s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 1.0),), vel=0.5, low=(36, 48))
        if bar0 != A1:
            s.pad("strings", prog, bar0, "pad", vel=0.35, center=59, count=3)
        # 風鈴 (トライアングルとチャイム) をまばらに
        s.perc("chimes", bar0, n, [(0.0, 0.25)], "chimes", pan=0.5, wet=0.6)
    s.line("shakuhachi", A_MEL, A1, "melody", pan=0.1, vel=0.72, legato=0.98)
    s.line("shakuhachi", A_MEL, A2, "melody", pan=0.1, vel=0.75, legato=0.98)
    s.line("koto", B_MEL, B, "koto_lead", pan=0.2, vel=0.7)
    s.line("shakuhachi", B_HOLD, B, "hold", pan=0.1, vel=0.5, legato=1.0)
    s.line("shakuhachi", A_MEL, A3, "melody", pan=0.1, vel=0.78, legato=0.98)
    s.perc("taiko", A2, 8, [(0, 0.4)], "taiko", pan=-0.1, wet=0.4, bend=TAIKO_TO["E"])
    s.perc("taiko", A3, 8, [(0, 0.45), (2.5, 0.3)], "taiko", pan=-0.1, wet=0.4, bend=TAIKO_TO["E"])
    return s.render(LEVELS, mute, reverb=3.4)
