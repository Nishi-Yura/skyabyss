# -*- coding: utf-8 -*-
"""ボスごとの見せ方 (2026-11-08 の選定。ラフは docs/review/roughs_boss_looks/、tools/rough/lowpoly/boss_looks/boss_looks.py)。
B 切り絵 (2026-11-08 に外した) を全員に揃えるのをやめ、ボスごとに題を変える。形と骨と動きはそのまま、低ポリのアトラスを塗り直す:
  淵の主 (lord_*)   : A 金継ぎの漆。黒漆の体、割れ目を継いだ金の筋が巨神の色に光る (灰=熾の橙、塩=潮の青緑、肋=骨の金白、嵐=紫)
  月の主 (moon_*)   : A 螺鈿の月。月の岩に、貝の内側のような虹の螺鈿。光る所は冷たい白青
  終章の主たち      : C 雲の喪章 (始祖 founder は形から作り直した。tools/rough/lowpoly/abyss/founder_pilgrim.py)。最後の鐘守・巡礼者 (目・手・月・羽)・
                      巻き戻す者・灯の目は形はそのまま、灰白の雲の体に、いちばん暗い所を黒いレース (織り目) に、光る所を白い光に塗る。
                      段の絵 (_s1・_s2) は雲が曇るように一段ずつ暗く
体のまわりの後光・漂うもの (輪・三日月・熾の粉・星屑) はゲームの描き手 (client/entity/PaperCutLayer の見せ方) が足す。
元の絵は tools/sources/paper_bosses/<名前>.png (切り絵に塗る前の絵) から塗る。光の型 (<名前>_glowmask.png) は、金の筋の分を足して書き直す。

    python -I -X utf8 tools/gen/mobs/boss_looks_paint.py [名前 ...]
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
LP = ROOT / "src/main/resources/assets/skyabyss/textures/lowpoly"
SRC = ROOT / "tools/sources/paper_bosses"
GLOW_SRC = ROOT / "tools/sources/boss_looks_glow"

INK = (12, 10, 16)
LORDS = {
    "lord_ash": (255, 150, 60),
    "lord_salt": (90, 230, 210),
    "lord_rib": (255, 226, 150),
    "lord_storm": (176, 136, 255),
}
MOONS = ["moon_core", "moon_scale", "full_keeper", "waning_blade", "shadow_embracer"]
CLOUDS = ["last_bellkeeper", "pilgrim_eye", "pilgrim_hand", "pilgrim_moon", "pilgrim_plume", "rewinder", "lamp_eye"]


def lum(a):
    return (a[..., :3] @ np.array([0.299, 0.587, 0.114])) / 255.0


def gradient_map(a, stops):
    L = lum(a)
    out = np.zeros(a.shape[:2] + (3,))
    pos = np.array([s[0] for s in stops])
    cols = np.array([s[1] for s in stops], float)
    for k in range(3):
        out[..., k] = np.interp(L, pos, cols[:, k])
    return out


def kintsugi(a, glow, color):
    solid = a[..., 3] > 0
    rgb = gradient_map(a, [(0, INK), (0.45, (44, 34, 38)), (1, (128, 104, 96))])
    L = lum(a)
    k = np.abs(np.roll(L, 1, 0) - np.roll(L, -1, 0)) + np.abs(np.roll(L, 1, 1) - np.roll(L, -1, 1))
    veins = (k > np.percentile(k[solid], 90)) & solid & (L < 0.6) if solid.any() else np.zeros(L.shape, bool)
    # 金の筋は芯を金、外を巨神の色 (光る)
    gold = np.array((255, 214, 120), float)
    rgb[veins] = gold * 0.45 + np.array(color, float) * 0.55
    rgb[glow] = color
    return rgb, glow | veins


def pearl(a, glow):
    rgb = gradient_map(a, [(0, (30, 34, 50)), (0.5, (150, 160, 182)), (1, (242, 246, 252))])
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    t = (xx / w * 3.0 + yy / h * 2.0) * math.pi
    tint = np.stack([np.sin(t) * 0.5 + 0.5, np.sin(t + 2.1) * 0.5 + 0.5, np.sin(t + 4.2) * 0.5 + 0.5], -1) * 60
    rgb = np.clip(rgb + tint - 30, 0, 255)
    rgb[glow] = (210, 240, 255)
    return rgb, glow


def cloud(a, glow, stage=0):
    L = lum(a)
    rgb = gradient_map(a, [(0, (98, 100, 116)), (0.35, (150, 154, 170)), (0.65, (200, 204, 216)), (1, (244, 244, 250))])
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    solid = a[..., 3] > 0
    dark = (L < np.percentile(L[solid], 34)) & solid if solid.any() else np.zeros(L.shape, bool)
    weave = ((xx + yy) % 3 == 0) | ((xx - yy) % 5 == 0)
    rgb[dark] = np.where(weave[dark][:, None], np.array((40, 36, 46), float), np.array((10, 8, 12), float))
    # 明るさの段の境に黒い細い線 (縫い目・縁取り。白一色で形が溶けないように)
    band = np.digitize(L, np.percentile(L[solid], [34, 60, 82])) if solid.any() else np.zeros(L.shape, int)
    edge = np.zeros(L.shape, bool)
    edge[:-1] |= band[:-1] != band[1:]
    edge[:, :-1] |= band[:, :-1] != band[:, 1:]
    rgb[edge & solid & ~dark] = (26, 22, 30)
    k = (1.0, 0.84, 0.66)[stage]
    rgb = rgb * k
    rgb[glow] = (232, 240, 255)
    return rgb, glow


def files_of(name):
    out = []
    for p in sorted(LP.glob(name + "*.png")):
        stem = p.stem
        if "_glow" in stem:
            continue
        rest = stem[len(name):]
        if rest == "" or rest.startswith("_s") or rest.startswith("_c"):
            out.append(p)
    return out


def run(name):
    files = files_of(name)
    if not files:
        print(name, "no textures")
        return
    GLOW_SRC.mkdir(parents=True, exist_ok=True)
    for p in files:
        src = SRC / p.name
        if not src.exists():
            print("  no source for", p.name, "(paint the paper version first)")
            continue
        a = np.asarray(Image.open(src).convert("RGBA")).astype(float)
        gp = LP / (p.stem + "_glowmask.png")
        keep = GLOW_SRC / gp.name
        if gp.exists() and not keep.exists():
            Image.open(gp).save(keep)
        gsrc = keep if keep.exists() else None
        if gsrc is not None:
            g = Image.open(gsrc).convert("RGBA")
            if g.size != (a.shape[1], a.shape[0]):
                g = g.resize((a.shape[1], a.shape[0]), Image.NEAREST)
            glow = np.asarray(g)[..., 3] > 40
        else:
            glow = np.zeros(a.shape[:2], bool)
        if name in LORDS:
            rgb, g2 = kintsugi(a, glow, LORDS[name])
        elif name in CLOUDS:
            stage = 2 if p.stem.endswith("_s2") else 1 if p.stem.endswith("_s1") else 0
            rgb, g2 = cloud(a, glow, stage)
        else:
            rgb, g2 = pearl(a, glow)
        out = np.concatenate([np.clip(rgb, 0, 255), a[..., 3:4]], axis=2).astype(np.uint8)
        Image.fromarray(out, "RGBA").save(p)
        if gsrc is not None or g2.any():
            gm = np.zeros(a.shape[:2] + (4,), np.uint8)
            gm[g2] = (255, 255, 255, 255)
            Image.fromarray(gm, "RGBA").save(gp)
    print(name, len(files), "textures")


if __name__ == "__main__":
    for n in (sys.argv[1:] or list(LORDS) + MOONS + CLOUDS):
        run(n)
