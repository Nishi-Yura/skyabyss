# -*- coding: utf-8 -*-
"""心臓の後のボスを「B 切り絵の悪夢」の見た目にする (2026-11-08 の声「Bの感じめっちゃ好き。心臓の後のボスたちの見た目をすべてこういうのにして」。
ラフは docs/review/roughs_boss_style/boss_style.png の B)。形と骨と動きはそのまま、低ポリのアトラスを塗り直す:
  - 黒の紙: 明るさを三段に寄せる (暗い所は墨、中ほどは墨より少し明るい灰、明るい所は生成り)
  - 白い縁: 明るさの段の境と、塗られた所の縁に生成りの線 (切り抜いた紙の縁)
  - 桃色: 光る所 (光の型 glowmask) は桃色に光る
体のまわりに浮かぶ切り絵の菱形は、ゲームの描き手 (client/entity/PaperCutLayer) が足す。
元の絵は tools/sources/paper_bosses/<名前>.png に残す (二度目からは、そこから塗り直す)。

    python -I -X utf8 tools/gen/mobs/paper_bosses.py [名前 ...]
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
LP = ROOT / "src/main/resources/assets/skyabyss/textures/lowpoly"
SRC = ROOT / "tools/sources/paper_bosses"

# 終章・六章・五章のボスと主 (四つの心臓のあと)
# 淵の主 (lord_*) と月の主 (moon_core ほか) は、2026-11-08 の選定でボスごとの見せ方に替えた (tools/gen/mobs/boss_looks_paint.py)。ここでは塗らない
# 2026-11-08 で、心臓の後のボスはどれもボスごとの見せ方に替えた (終章の主たちは tools/gen/mobs/boss_looks_paint.py の雲の喪章)。
# 切り絵で塗るボスは今はない (この道具は元の絵 tools/sources/paper_bosses/ の置き場として残す)
BOSSES = []
# 心臓の番人 (heart_warden) は 2026-11-08 に「錆の門番 B 鍵持ちの騎士」で作り直した (tools/rough/lowpoly/abyss/warden_knight.py)
# 深みの王 (king_*) は 2026-11-08 に「喪服の王」の形で作り直した (tools/rough/lowpoly/abyss/king_mourning.py)。色は形の材質のまま

INK = np.array([14, 12, 18], float)
INK2 = np.array([44, 40, 50], float)
PAPER = np.array([232, 224, 206], float)
PINK = np.array([246, 110, 168], float)
PINK_HI = np.array([255, 196, 220], float)


def files_of(name):
    out = []
    for p in sorted(LP.glob(name + "*.png")):
        stem = p.stem
        if stem.endswith("_glowmask") or "_glow" in stem:
            continue
        rest = stem[len(name):]
        if rest == "" or rest.startswith("_s") or rest.startswith("_c"):
            out.append(p)
    return out


def paint(img: Image.Image, glow: Image.Image | None) -> Image.Image:
    a = np.asarray(img.convert("RGBA")).astype(float)
    rgb, alpha = a[..., :3], a[..., 3]
    lum = (rgb @ np.array([0.299, 0.587, 0.114])) / 255.0
    solid = alpha > 0
    # 三段に寄せる (場所ごとの明るさの幅で決める: その絵の明るさの 35% と 80% の分位)
    if solid.any():
        lo, hi = np.percentile(lum[solid], [35, 82])
    else:
        lo, hi = 0.3, 0.7
    band = np.where(lum < lo, 0, np.where(lum < hi, 1, 2))
    out = np.zeros_like(rgb)
    out[band == 0] = INK
    out[band == 1] = INK2
    out[band == 2] = PAPER
    # 段の境と、塗られた所の縁に生成りの線 (1 画素)
    edge = np.zeros(band.shape, bool)
    for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
        nb = np.roll(np.roll(band, dy, 0), dx, 1)
        na = np.roll(np.roll(solid, dy, 0), dx, 1)
        edge |= solid & ((nb != band) & (band == 0) & (nb == 2))
        edge |= solid & ~na
    out[edge] = PAPER
    # 光る所は桃色
    if glow is not None:
        g = np.asarray(glow.convert("L").resize(img.size)).astype(float) / 255.0
        m = g > 0.05
        out[m] = PINK * (1 - g[m, None] * 0.5) + PINK_HI * (g[m, None] * 0.5)
    res = np.concatenate([np.clip(out, 0, 255), alpha[..., None]], axis=2).astype(np.uint8)
    return Image.fromarray(res, "RGBA")


def run(name):
    files = files_of(name)
    if not files:
        print(name, "no textures")
        return
    SRC.mkdir(parents=True, exist_ok=True)
    for p in files:
        keep = SRC / p.name
        if not keep.exists():
            shutil.copyfile(p, keep)
        glow_path = LP / (p.stem + "_glowmask.png")
        if not glow_path.exists():
            glow_path = LP / (name + "_glowmask.png")
        glow = Image.open(glow_path) if glow_path.exists() else None
        paint(Image.open(keep), glow).save(p)
    print(name, len(files), "textures")


if __name__ == "__main__":
    for n in (sys.argv[1:] or BOSSES):
        run(n)
