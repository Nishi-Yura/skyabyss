# -*- coding: utf-8 -*-
"""霞鳥 (mist_bird) 低ポリ 一品一案。鐘の島の敵 (docs/design/50 の 4.2.1)。
雲から生まれる白い鳥。翼を広げて 2.2 ブロック。体は白い霞の羽、くちばしと足は淡い金、目は朝の光。尾は長く、霞のようにたなびく。
上を回り、狙った人へ急降下して押し戻す (落ちる所に影の円の予告)。
骨: root > body > head / wing_l / wing_r / tail。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 8, 0)), "head": ("body", (0, 9.4, -4.2)),
         "wing_l": ("body", (-1.6, 9.6, -0.6)), "wing_r": ("body", (1.6, 9.6, -0.6)), "tail": ("body", (0, 8.4, 3.6))}
PAL = make_pal({
    "white": (CLOUDW, 0.6, dict(crack=0.0, noise=0.12)),
    "mist": (CLOUDW, 0.5, dict(crack=0.0, noise=0.22)),
    "gold": (GOLDW, 0.7, dict(crack=0.02, noise=0.1)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
}, glow=["dawn"])


def wing(F, side):
    b = "wing_l" if side < 0 else "wing_r"
    # 腕の骨に沿った前縁と、後ろへ流れる羽の列 (平たい筒)
    F += part(f"lead{side}", tube(b, "white", [(side * 1.6, 9.6, -0.6), (side * 6.0, 10.4, -0.2), (side * 11.0, 10.0, 1.0), (side * 15.5, 9.4, 2.6)],
                                  ra=[0.45, 0.4, 0.3, 0.12], rb=[1.0, 0.8, 0.6, 0.25], sides=4, hint=(0, 1, 0)))
    # 羽: 幅の広い平たい羽を少しずつ重ねる (翼の面が途切れない)
    for i in range(9):
        t = i / 8
        x0 = side * (2.2 + 12.6 * t)
        z0 = -0.4 + 2.6 * t
        ln = 5.2 - 2.0 * t
        F += part(f"fea{side}{i}", tube(b, "mist" if i % 2 else "white",
                                        [(x0, 9.8 - 0.4 * t, z0), (x0 + side * 0.5, 9.6 - 0.4 * t, z0 + ln * 0.6), (x0 + side * 0.9, 9.4 - 0.4 * t, z0 + ln)],
                                        ra=[0.2, 0.16, 0.08], rb=[1.3, 1.1, 0.45], sides=4, hint=(0, 1, 0)))


def build(k):
    F = []
    F += part("chest", ellipsoid("body", "white", (0, 8.6, -1.2), (2.0, 1.9, 3.4), segs=10, rings=4))
    F += part("back", ellipsoid("body", "mist", (0, 9.4, 1.6), (1.6, 1.2, 2.6), segs=8, rings=3))
    F += part("neck", tube("head", "white", [(0, 9.0, -3.4), (0, 9.6, -4.6)], ra=[1.2, 1.0], sides=6, hint=(1, 0, 0)))
    F += part("head", ellipsoid("head", "white", (0, 9.8, -5.4), (1.15, 1.1, 1.4), segs=8, rings=3))
    F += part("beak", spike("head", "gold", (0, 9.6, -6.6), (0, -0.15, -1), 1.8, 0.45, sides=4, rings=2))
    for s in (-1, 1):
        F += part(f"eye{s}", ellipsoid("head", "dawn", (s * 0.85, 10.0, -5.9), (0.22, 0.22, 0.2), segs=5, rings=2))
        F += part(f"foot{s}", tube("body", "gold", [(s * 0.8, 7.0, 0.4), (s * 0.9, 6.0, 1.6)], ra=[0.25, 0.15], sides=4, hint=(1, 0, 0)))
    wing(F, -1)
    wing(F, 1)
    # 尾: 長い霞の羽 3 本 (先が細く、少し下がる)
    for i, dx in enumerate((-0.9, 0.0, 0.9)):
        F += part(f"tail{i}", tube("tail", "mist" if i != 1 else "white", [(dx * 0.6, 8.6, 3.4), (dx * 1.4, 8.0, 7.0), (dx * 2.2, 7.4, 10.6)],
                                   ra=[0.2, 0.14, 0.05], rb=[0.8, 0.6, 0.2], sides=4, hint=(0, 1, 0)))
    return F, []


POSES = {
    "idle": {},
    "dive": {"wing_l": (0, 0, 45), "wing_r": (0, 0, -45), "body": (-35, 0, 0), "head": (10, 0, 0), "tail": (15, 0, 0)},
}

SPEC = dict(
    id="mist_bird", short="霞鳥", kind="鐘の島の敵 一品一案 (50 の 4.2.1)",
    title="霞鳥 (mist_bird) — 雲から生まれる白い鳥・低ポリ + ドット絵 一案",
    sub=["上を回り、狙った人へ急降下して押し戻す (落ちる所に影の円 0.8 秒)。雲の縁から落とされる。",
         "白い霞の羽、淡い金のくちばしと足、朝の光の目。翼を広げて 2.2 ブロック。"],
    labels=["霞鳥"],
    rows=[("上から", "idle", 200, 45, 300, (0, 9, 2), 7.0),
          ("前 3/4", "idle", 212, 10, 300, (0, 9, 2), 7.0),
          ("急降下", "dive", 230, 6, 300, (0, 9, 2), 7.0),
          ("横", "idle", 270, 4, 300, (0, 9, 2), 7.0)],
    options={"a": dict(name="白い霞の鳥", desc="白い羽の鳥。翼の後ろの羽と長い尾が霞のようにたなびく。くちばしと足は淡い金、目は朝の光。")},
    extra="雲海の言葉 (雲の白・淡い金・朝の光)。骨 6 本。",
    budget="小さな敵 400〜800", pw=420,
    later="二段目 = 尾が伸び、羽の縁が金に。三段目 = 群れの頭 (二回り大きい)。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
