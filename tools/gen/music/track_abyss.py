# -*- coding: utf-8 -*-
"""深淵 (abyss)。調のない持続音の曲 (60、4/4 で数える)。ホラーの環境の曲 (docs/design/36・01 の演出の原則: 音で脅かさない、引き算):
低い C の持続音に、増 4 度 (F#) がゆっくり出入りする。合唱 (ウー) の半音のかたまりが 4 小節ごとに入れ替わる。
高い所にまばらなチェレスタ、遠い心臓の音 (和太鼓の二打)、まれな管の鐘。急に大きな音は出さない (立ち上がりはどれも遅い)。
"""
import numpy as np

from arrange import Song

BPM, BAR = 60, 4
BARS = 24
# 合唱のかたまり (4 小節ずつ)
CLUSTERS = [(48, 49, 54), (47, 53, 54), (48, 51, 54, 55), (46, 52, 53), (48, 49, 55), (47, 50, 53, 54)]
SPARKS = [84, 85, 90, 91, 83, 88, 79, 86]  # C6 Db6 F#6 G6 B5 E6 G5 D6

LEVELS = {"drone": -13, "tritone": -18, "choir": -14, "high": -22, "celesta": -18, "heart": -20, "bell": -24}


def build(mute=()):
    s = Song(BPM, BAR, BARS, seed=141)
    rng = np.random.default_rng(7)
    # 低い C (2 小節ずつ重ねて途切れない持続音)
    for bar0 in range(0, BARS, 2):
        s.hit("contrabass", 36, bar0 * BAR, 0.5, "drone", pan=-0.15, wet=0.5, dur=BAR * 2 * 1.08)
        s.hit("cello", 48, bar0 * BAR, 0.35, "drone", pan=0.15, wet=0.5, dur=BAR * 2 * 1.08)
    # 増 4 度がゆっくり出入りする (4 小節おきに 4 小節)
    for bar0 in range(2, BARS, 8):
        s.hit("cello", 42, bar0 * BAR, 0.4, "tritone", pan=0.3, wet=0.6, dur=BAR * 4)
    # 合唱のかたまり
    for i, bar0 in enumerate(range(0, BARS, 4)):
        for j, key in enumerate(CLUSTERS[i % len(CLUSTERS)]):
            s.hit("oohs", key, bar0 * BAR + j * 0.7, 0.45, "choir", pan=-0.4 + 0.27 * j, wet=0.7, dur=BAR * 4 * 1.05)
    # 高い弦の擦れ (トレモロ) を 8 小節に一度
    for bar0 in range(4, BARS, 8):
        s.hit("tremolo", 90, bar0 * BAR, 0.3, "high", pan=0.5, wet=0.8, dur=BAR * 3)
        s.hit("tremolo", 91, bar0 * BAR + 1.5, 0.28, "high", pan=-0.5, wet=0.8, dur=BAR * 2.6)
    # まばらなチェレスタ (拍に乗らない)
    beat = 2.0
    while beat < BARS * BAR - 2:
        s.hit("celesta", SPARKS[int(rng.integers(0, len(SPARKS)))], beat, float(rng.uniform(0.3, 0.5)), "celesta",
              pan=float(rng.uniform(-0.6, 0.6)), wet=0.8, dur=1.0)
        beat += float(rng.uniform(2.5, 6.0))
    # 遠い心臓 (二打) を 2 小節ごと
    for bar0 in range(1, BARS, 2):
        s.hit("kit", 36, bar0 * BAR, 0.4, "heart", pan=0.0, wet=0.7, dur=0.5)
        s.hit("kit", 36, bar0 * BAR + 0.35, 0.3, "heart", pan=0.0, wet=0.7, dur=0.5)
    # まれな管の鐘
    for bar0 in (7, 19):
        s.hit("tubular", 61, bar0 * BAR + 2, 0.35, "bell", pan=0.4, wet=0.85, dur=3.0)
    return s.render(LEVELS, mute, reverb=4.8, damp=0.6, target_rms_db=-21.0)
