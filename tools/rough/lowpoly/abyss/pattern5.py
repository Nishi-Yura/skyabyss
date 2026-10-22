"""深淵の敵のラフの表面の模様 (アトラスの画素を 3D の位置 P から描き直す)。ck5 が読み込む。

材質名ごとに模様を一つ選ぶ (DEFAULT_PATTERNS):
  grain    骨の木目 (縦の筋と細い割れ)           bone / calc / tuff
  crystal  塩の結晶 (細胞ごとの明るい面と縁の影) salt / saltc
  verdigris 酸化した銅の緑青の斑                  oxcu
  streak   雷の白い筋 (光る筋は発光マスクに入る)  light
  feather  羽の筋 (羽枝)                          feather
  scorch   煤の流れと熾の筋 (熾は発光)            soot / ash
"""
import numpy as np

import ck as _ck
from ck import hash3, vnoise

DEFAULT_PATTERNS = {"bone": "grain", "calc": "grain", "tuff": "grain", "ash": "scorch", "soot": "scorch",
                    "salt": "crystal", "saltc": "crystal", "oxcu": "verdigris", "light": "streak", "feather": "feather"}
GREEN_RAMP = [(30, 78, 70), (48, 112, 98), (72, 146, 124), (104, 176, 150)]


def pattern_overlay(atlas, glow, faces, P, M, pal):
    pats = pal.get("patterns", DEFAULT_PATTERNS)
    fi = np.where(M >= 0, M, 0)
    mats = np.array([f.mat for f in faces])[fi]
    atlas = atlas.copy()
    glow = glow.copy()
    for name, kind in pats.items():
        sel = (mats == name) & (M >= 0)
        sp = pal["mats"].get(name)
        if not sel.any() or sp is None:
            continue
        ramp = np.array(sp["ramp"], float)
        nr = len(ramp)
        c = None
        if kind == "grain":
            v = 0.5 + 0.5 * np.sin(P[..., 1] * 2.6 + 4.0 * vnoise(P * 0.5 + 5, 33))
            idx = np.clip(np.floor(v * nr * 0.8 + 0.1 * nr), 0, nr - 1).astype(int)
            c = ramp[idx]
            crack = vnoise(P * 1.3 + 11, 35) > 0.8
            c = np.where(crack[..., None], ramp[0], c)
        elif kind == "crystal":
            cells = np.floor(P * 0.7)
            h = hash3(cells, 9)
            fr = P * 0.7 - cells
            edge = np.minimum(fr, 1 - fr).min(-1)
            base = np.floor(np.clip(0.5 + 0.35 * (h - 0.5) * 2, 0, 0.999) * nr).astype(int)
            idx = np.where(h > 0.86, nr - 1, base)
            idx = np.where(edge < 0.06, max(nr // 2 - 1, 0), idx)
            c = ramp[np.clip(idx, 0, nr - 1)]
        elif kind == "verdigris":
            b = vnoise(P * 0.95 + 7, 41)
            g = np.array(GREEN_RAMP, float)
            gi = np.clip(np.floor((b - 0.45) * 9), 0, len(g) - 1).astype(int)
            idx = np.clip(np.floor(b * nr), 0, nr - 1).astype(int)
            c = np.where((b > 0.55)[..., None], g[gi], ramp[idx])
        elif kind == "streak":
            v = np.sin(P[..., 1] * 7.0 + 4 * vnoise(P * 0.6 + 2, 8))
            lit_line = v > 0.82
            c = np.where(lit_line[..., None], ramp[-1], ramp[np.clip(np.floor((v + 1) * 0.5 * nr), 0, nr - 1).astype(int)])
            glow = glow | (lit_line & sel)
        elif kind == "feather":
            v = 0.5 + 0.5 * np.sin(P[..., 2] * 10.0 + 2.2 * vnoise(P * 0.5, 12))
            idx = np.clip(np.floor(v * nr), 0, nr - 1).astype(int)
            c = ramp[idx]
        elif kind == "scorch":
            v = vnoise(P * np.array([0.5, 2.6, 0.5]) + 3, 14)
            idx = np.clip(np.floor(v * nr), 0, nr - 1).astype(int)
            c = ramp[idx]
            ember = v > 0.8
            c = np.where(ember[..., None], ramp[-1], c)
            glow = glow | (ember & sel & (name in ("soot", "ash")))
        if c is None:
            continue
        atlas[sel, :3] = np.clip(c[sel], 0, 255).astype(np.uint8)
        atlas[sel, 3] = 255
    return atlas, glow


_paint_prev = _ck.paint


def _paint_pattern(faces, size, P, N, M, pal, eyes=()):
    atlas, glow = _paint_prev(faces, size, P, N, M, pal, eyes)
    return pattern_overlay(atlas, glow, faces, P, M, pal)


_ck.paint = _paint_pattern
