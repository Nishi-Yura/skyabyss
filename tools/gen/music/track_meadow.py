# -*- coding: utf-8 -*-
"""都のまわりの野 (meadow、土地 0)。ヘ長調 4/4、104。旅立ちの曲: ホルンの旋律、ギターの分散和音、ピチカートの低音、軽いタンバリン。
形: A (ホルン) 16 → B (フルートと弦) 8 → A' (ホルン + フルートの 8 度上、太鼓) 16 → 戻り 2 (Bb | C7、拾い上げで A の頭へ)
"""
from arrange import HAND_TO, Song

BPM, BAR = 104, 4
A, B, A2, TURN, BARS = 0, 16, 24, 40, 42

A_CH = "F | C | Dm | Bb | F | C | Bb | C | F | C | Dm | Bb | Gm | Dm | Bb C | F"
B_CH = "Dm | Bb | F | C | Dm | Bb | Gm | C"
TURN_CH = "Bb | C7"
A_MEL = ("F4:1 A4:1 C5:1.5 A4:.5 | G4:2 E4:1 C4:1 | D4:1 F4:1 A4:1.5 G4:.5 | F4:3 r:1 | "
         "F4:1 A4:1 C5:1.5 D5:.5 | E5:2 D5:1 C5:1 | D5:1.5 C5:.5 Bb4:1 A4:1 | G4:3 r:1 | "
         "F4:1 A4:1 C5:1.5 A4:.5 | G4:2 E4:1 C4:1 | D4:1 F4:1 A4:1 D5:1 | D5:2 C5:1 Bb4:1 | "
         "Bb4:1.5 A4:.5 G4:1 D5:1 | A4:2 F4:1 D4:1 | F4:1 G4:1 Bb4:1 Bb4:1 | A4:2 r:2")
B_MEL = ("A5:2 F5:1 D5:1 | F5:1.5 D5:.5 Bb4:2 | C5:1 F5:1 A5:1 G5:1 | G5:3 E5:1 | "
         "F5:1 A5:1 D6:1.5 C6:.5 | Bb5:2 F5:2 | G5:1 Bb5:1 D6:1 C6:1 | Bb5:1 A5:1 G5:2")
TURN_MEL = "r:2 D5:1 C5:1 | Bb4:1 A4:1 G4:1 E4:1"

CHORD_PLAN = [(A_CH, A), (B_CH, B), (A_CH, A2), (TURN_CH, TURN)]
MELODY_GROUPS = ("melody", "flute")
LEVELS = {"melody": -6, "flute": -9, "guitar": -12, "pizz": -12, "pad": -15, "harp": -13, "perc": -19, "tamb": -24,
          "timp": -18}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=11)
    arp = [0, 1, 2, 3, 4, 3, 2, 1]
    # A
    s.line("horn", A_MEL, A, "melody", pan=-0.1, vel=0.72)
    s.arp("guitar", A_CH, A, "guitar", arp, step=0.5, vel=0.6, center=57, pan=-0.35, wet=0.25)
    s.bassline("pizz", A_CH, A, "pizz", ((0, "R", 0.5), (2, "5", 0.5)), vel=0.75)
    s.pad("strings", A_CH, A, "pad", vel=0.35)
    s.perc("tamb", A, 16, [(1, 0.35), (3, 0.3)], "tamb", pan=0.4)
    s.perc("hand", A, 16, [(0, 0.5), (2.5, 0.3)], "perc", pan=-0.2, bend=HAND_TO["A"])
    s.gliss("harp", (A + 16) * BAR - 1.5, "F4", "F", 10)
    # B
    s.line("flute", B_MEL, B, "flute", pan=0.15, vel=0.75)
    s.line("strings_fast", B_MEL, B, "pad", pan=-0.2, vel=0.45, octave=-1)
    s.arp("harp", B_CH, B, "harp", [0, 2, 3, 4], step=1.0, vel=0.55, center=62, pan=0.35, wet=0.4, ring=2.2)
    s.bassline("pizz", B_CH, B, "pizz", ((0, "R", 1.0),), vel=0.7)
    s.pad("strings", B_CH, B, "pad", vel=0.5)
    s.perc(("timpani", 41), B + 7, 1, [(2, 0.4), (2.5, 0.45), (3, 0.5), (3.5, 0.6)], "timp")
    # A'
    s.line("horn", A_MEL, A2, "melody", pan=-0.1, vel=0.8)
    s.line("flute", A_MEL, A2, "flute", pan=0.2, vel=0.6, octave=1)
    s.arp("guitar", A_CH, A2, "guitar", arp, step=0.5, vel=0.65, center=57, pan=-0.35, wet=0.25)
    s.bassline("pizz", A_CH, A2, "pizz", ((0, "R", 0.5), (2, "5", 0.5)), vel=0.8)
    s.pad("strings", A_CH, A2, "pad", vel=0.5)
    s.perc("tamb", A2, 16, [(1, 0.4), (3, 0.35), (3.5, 0.2)], "tamb", pan=0.4)
    s.perc("hand", A2, 16, [(0, 0.6), (1.5, 0.3), (2.5, 0.35)], "perc", pan=-0.2, bend=HAND_TO["A"])
    # 戻り
    s.line("horn", TURN_MEL, TURN, "melody", pan=-0.1, vel=0.75)
    s.arp("guitar", TURN_CH, TURN, "guitar", arp, step=0.5, vel=0.62, center=57, pan=-0.35, wet=0.25)
    s.bassline("pizz", TURN_CH, TURN, "pizz", ((0, "R", 0.5), (2, "5", 0.5)), vel=0.75)
    s.pad("strings", TURN_CH, TURN, "pad", vel=0.42)
    s.perc("hand", TURN, 2, [(0, 0.55), (2, 0.4), (3, 0.45), (3.5, 0.5)], "perc", pan=-0.2, bend=HAND_TO["A"])
    s.gliss("harp", BARS * BAR - 1.5, "C4", "F", 10)
    return s.render(LEVELS, mute)
