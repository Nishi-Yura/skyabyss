"""鯨・潮殻の武器共通の小道具 (光る線の作り方)。各 item_*.py が使う。"""
from __future__ import annotations

import numpy as np

from paint import line_mask, edge_mask, spot_mask


def blade_inlay(mats, cxf, rare, legend, y0, y1, half=0.5, dash=(2.4, 1.3), extra_spots=()):
    """刃の中心線の光る線。rare / legend は (y の始め, 終わり)、p5 は破線、p10 は端まで通す。
    extra_spots = [(mats, center, r)] は +10 と伝説で光る点 (飾りの光)。"""
    def fn(env, kind):
        if kind == "rare":
            return line_mask(env, mats, cxf, rare[0], rare[1], half)
        if kind == "legend":
            m = line_mask(env, mats, cxf, legend[0], legend[1], half)
            for ms, c, r in extra_spots:
                m = m | spot_mask(env, ms, c, r)
            return m
        if kind == "p5":
            return line_mask(env, mats, cxf, y0, y1 - 1.0, half, dash=dash)
        if kind == "p10":
            m = line_mask(env, mats, cxf, y0, y1, half + 0.1)
            for ms, c, r in extra_spots:
                m = m | spot_mask(env, ms, c, r * 0.8)
            return m
        return env["lit"] < -9
    return fn


def cx_interp(ys, xs):
    return lambda y: np.interp(y, ys, xs)
