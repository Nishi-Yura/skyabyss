# -*- coding: utf-8 -*-
"""余燼の荒野 (field_ember、土地 5)。ニのフリギア属音階 (D Eb F# G A Bb C) 4/4、88。灰と熱の荒れ地:
低い弦の持続音 (D)、シタールの動機、イングリッシュホルンの旋律、和太鼓の鼓動、振り物。
形: 前 2 (持続音とシタール) → A 8 (イングリッシュホルン) → A 8 (+ 和太鼓・弦) → B 8 (オーボエ) → A 8 (全部) → 終わり 2 (Eb | D)
"""
from arrange import TAIKO_TO, Song

BPM, BAR = 88, 4
INTRO, A1, A2, B, A3, TAIL, BARS = 0, 2, 10, 18, 26, 34, 36

INTRO_CH = "D | D"
A_CH = "D | Eb | D | Cm | D | Eb | Gm | D"
B_CH = "Gm | Cm | Eb | D | Gm | Cm | Eb | D"
TAIL_CH = "Eb | D"
A_MEL = ("A4:2 Bb4:1 A4:1 | G4:1.5 F#4:.5 Eb4:2 | F#4:1 A4:1 D5:2 | Eb5:2 D5:1 C5:1 | "
         "D5:1 Eb5:1 F#5:2 | G5:2 F#5:1 Eb5:1 | D5:2 Bb4:1 G4:1 | A4:4")
B_MEL = ("G4:1 Bb4:1 D5:2 | C5:1.5 Eb5:.5 G5:2 | Bb4:2 Eb5:2 | F#5:3 A5:1 | "
         "G5:2 D5:2 | Eb5:1 D5:1 C5:2 | Bb4:2 G4:2 | A4:2 F#4:1 Eb4:1")
# シタールの動機 (D の持続の上で)
SITAR = "D5:.5 Eb5:.5 F#5:.5 Eb5:.5 D5:1 A4:1 | D5:.5 Eb5:.5 F#5:.5 G5:.5 F#5:1 Eb5:1"

CHORD_PLAN = [(INTRO_CH, INTRO), (A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TAIL_CH, TAIL)]
MELODY_GROUPS = ("melody", "sitar")
LEVELS = {"melody": -7, "sitar": -11, "drone": -20, "pad": -17, "taiko": -14, "shaker": -24, "bass": -15}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=101)
    # 低い D の持続音 (曲の全部で。和音の根音が替わっても D は鳴り続ける)
    for bar0 in range(0, BARS, 2):
        s.hit("cello", 38, bar0 * BAR, 0.5, "drone", pan=-0.2, wet=0.4, dur=BAR * 2 * 1.05)
        s.hit("contrabass", 26, bar0 * BAR, 0.45, "drone", pan=0.2, wet=0.3, dur=BAR * 2 * 1.05)
    for prog, bar0, n in ((INTRO_CH, INTRO, 2), (A_CH, A1, 8), (A_CH, A2, 8), (B_CH, B, 8), (A_CH, A3, 8), (TAIL_CH, TAIL, 2)):
        if bar0 in (A1, TAIL):
            s.pad("strings", prog, bar0, "pad", vel=0.35, center=60, count=3)
        if bar0 not in (INTRO, A1, TAIL):
            s.pad("strings", prog, bar0, "pad", vel=0.4, center=60, count=3)
            s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 1.0),), vel=0.45, low=(38, 50))
        if bar0 not in (INTRO, A1):
            s.perc("taiko", bar0, n, [(0, 0.6), (0.75, 0.3), (2, 0.45)], "taiko", pan=-0.1, wet=0.35, bend=TAIKO_TO["D"])
        s.perc("shaker", bar0, n, [(1, 0.25), (1.5, 0.15), (3, 0.25), (3.5, 0.15)], "shaker", pan=0.45)
    s.line("sitar", SITAR, INTRO, "sitar", pan=0.3, vel=0.6)
    s.line("sitar", SITAR, TAIL, "sitar", pan=0.3, vel=0.6)
    s.line("english_horn", A_MEL, A1, "melody", pan=0.0, vel=0.72, legato=0.97)
    s.line("english_horn", A_MEL, A2, "melody", pan=0.0, vel=0.75, legato=0.97)
    s.line("sitar", A_MEL, A2, "sitar", pan=0.3, vel=0.45, octave=1)
    s.line("oboe", B_MEL, B, "melody", pan=0.1, vel=0.72, legato=0.97)
    s.line("english_horn", A_MEL, A3, "melody", pan=0.0, vel=0.8, legato=0.97)
    s.line("sitar", A_MEL, A3, "sitar", pan=0.3, vel=0.5, octave=1)
    return s.render(LEVELS, mute, reverb=3.0)
