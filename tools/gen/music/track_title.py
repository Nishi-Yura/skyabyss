# -*- coding: utf-8 -*-
"""タイトル (title)「天と淵のあいだ」。ニ短調 4/4、76。主題: 天へ上がる (D → A → D → F) と、淵へ下りる (A → G → F → E → D)。
ハープとチェレスタの前奏、ホルンの独奏、弦と合唱、全部での盛り上がり、静かな戻り。
形: 前 2 (ハープ・チェレスタ) → A 8 (ホルン、低い 8 度) → A 8 (弦 + 合唱の敷き) → B 8 (全部) → A 8 (チェレスタとウーで静かに)
  → 終わり 2 (Gm | A。ハープの下り) → 頭の前奏へ
"""
from arrange import Song

BPM, BAR = 76, 4
INTRO, A1, A2, B, A3, TAIL, BARS = 0, 2, 10, 18, 26, 34, 36

INTRO_CH = "Dm | Bb"
A_CH = "Dm | Bb | F | C | Dm | Bb | Gm | A"
B_CH = "Bb | C | Dm | Dm/C | Bb | F/A | Gm | A"
TAIL_CH = "Gm | A"
RISE = "D5:1 A5:1 D6:2 | F6:2 D6:1 Bb5:1"
A_MEL = ("D4:1 A4:1 D5:2 | F5:2 D5:1 Bb4:1 | C5:1 F5:1 A5:2 | G5:3 E5:1 | "
         "A5:1.5 G5:.5 F5:1 E5:1 | D5:2 F5:2 | G5:1 Bb5:1 A5:1 G5:1 | E5:2 C#5:2")
B_MEL = ("D5:2 F5:2 | E5:2 G5:2 | A5:3 G5:.5 F5:.5 | F5:2 D5:2 | D5:1 F5:1 Bb5:2 | A5:2 F5:2 | G5:2 Bb4:2 | A4:2 C#5:1 E5:1")
FALL = "A5:1 G5:1 F5:1 E5:1 | E5:2 C#5:2"

CHORD_PLAN = [(INTRO_CH, INTRO), (A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TAIL_CH, TAIL)]
MELODY_GROUPS = ("melody", "second", "motif")
LEVELS = {"melody": -6, "second": -10, "motif": -11, "harp": -12, "pad": -14, "choir": -15, "bass": -14, "timp": -18,
          "cymbal": -23}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=161)
    secs = ((INTRO_CH, INTRO), (A_CH, A1), (A_CH, A2), (B_CH, B), (A_CH, A3), (TAIL_CH, TAIL))
    for prog, bar0 in secs:
        s.arp("harp", prog, bar0, "harp", [0, 2, 3, 4, 5, 4, 3, 2], step=0.5, vel=0.5 if bar0 != B else 0.55, center=62,
              count=5, low=(38, 50), pan=0.3, wet=0.5, ring=2.4)
        s.bassline("contrabass", prog, bar0, "bass", ((0, "R", 1.0),), vel=0.45 if bar0 in (INTRO, A1, TAIL) else 0.6,
                   low=(33, 45))
        if bar0 in (A2, B, A3):
            s.pad("strings", prog, bar0, "pad", vel=0.4 if bar0 != B else 0.55, center=60)
        if bar0 in (A2, B):
            s.pad("choir", prog, bar0, "choir", vel=0.45 if bar0 == A2 else 0.6, center=62)
        if bar0 == B:
            s.timp(prog, bar0, [(0, 0.45)], "timp")
            s.perc("cymbal", bar0, 1, [(0, 0.35)], "cymbal", pan=0.3, wet=0.6)
        if bar0 == A3:
            s.pad("oohs", prog, bar0, "choir", vel=0.4, center=62)
    s.line("celesta", RISE, INTRO, "motif", pan=-0.2, vel=0.6)
    s.line("horn", A_MEL, A1, "melody", pan=-0.1, vel=0.75, octave=-1)
    s.line("strings_fast", A_MEL, A2, "melody", pan=0.1, vel=0.72)
    s.line("horn", A_MEL, A2, "second", pan=-0.2, vel=0.5, octave=-1)
    s.line("strings_fast", B_MEL, B, "melody", pan=0.1, vel=0.8)
    s.line("horn", B_MEL, B, "second", pan=-0.2, vel=0.62, octave=-1)
    s.line("celesta", A_MEL, A3, "melody", pan=0.2, vel=0.62)
    s.line("flute", A_MEL, A3, "second", pan=-0.2, vel=0.45)
    s.line("celesta", FALL, TAIL, "motif", pan=-0.2, vel=0.6)
    s.line("horn", FALL, TAIL, "second", pan=0.1, vel=0.5, octave=-1)
    s.gliss("harp", BARS * BAR - 2, "D6", "D", 12, beats=2, down=True, mode=(0, 2, 3, 5, 7, 8, 10))
    return s.render(LEVELS, mute, reverb=3.6, damp=0.5)
