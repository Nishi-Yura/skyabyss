"""地下のダンジョンの装飾品 4 品の形のラフ (docs/design/55 の 2 章「初回の主: そのダンジョンだけの装飾品 1 つ」)。品ごとに三案。
材質の言葉はその土地の巨神と同じ (鯨骨の坑 = 岩鯨、潮の洞 = 潮殻、雷晶の空洞 = 雷翼、熔けた坑道 = 余燼)。主の形 (00 の 364〜367) から取る。

    python -X utf8 -I tools/rough/lowpoly/accessories/dungeon.py   → docs/review/roughs_dungeon_acc/<品>.png と sheet.png
"""
from __future__ import annotations

import sys

from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from PIL import ImageDraw  # noqa: E402

from common import *  # noqa: F401,F403
import sheet as SH

OUT_DIR = ROOT / "docs/review/roughs_dungeon_acc"


def cord(c, R=1.3, ax="z"):
    return torus("cord", c, R, 0.32, 0.32, n=6, k=3, ax=ax)


def bent(mat, pts, ra, sides=4):
    return curve(mat, pts, ra, [r * 1.05 for r in ra], sides=sides, caps=(True, True))


# ====================================================================== 骨掘りの顎飾り (鯨骨の坑。大百足の大顎)
def jaw_a(r):
    # 骨の円盤に、左右から交わる二本の大顎。真ん中に橙の目
    f = hexdisc("guard", (0, 0, 0), 3.6, 1.6, sides=8, phase=22.5)
    f += ell("gem", (0, 0.2, -0.9), (1.3, 1.3, 0.7), segs=6, rings=1)
    for s in (1, -1):
        f += bent("main", [(s * 3.0, 3.2, 0), (s * 5.2, 0.2, 0), (s * 3.4, -4.0, 0), (-s * 0.4, -5.6, 0)], [1.0, 1.0, 0.7, 0.2])
    f += cord((0, 4.8, 0))
    return f


def jaw_b(r):
    # 大百足の節を三つつないだ短い鎖。節ごとに骨の甲板と小さな脚
    f = []
    for k in range(3):
        y = 3.2 - k * 3.2
        f += ell("guard", (0, y, 0), (2.2 - 0.3 * k, 1.5, 1.6), segs=6, rings=1)
        f += ell("main", (0, y + 0.8, 0.2), (2.8 - 0.3 * k, 0.6, 1.4), segs=6, rings=1)
        for s in (1, -1):
            f += bent("acc", [(s * 1.8, y - 0.2, 0), (s * 3.4, y - 1.0, -0.2), (s * 3.6, y - 2.2, -0.2)], [0.35, 0.3, 0.1])
    f += bent("main", [(0.8, -5.2, 0), (2.0, -7.6, 0), (0.6, -8.8, 0)], [0.6, 0.5, 0.15])
    f += bent("main", [(-0.8, -5.2, 0), (-2.0, -7.6, 0), (-0.6, -8.8, 0)], [0.6, 0.5, 0.15])
    f += cord((0, 6.0, 0))
    return f


def jaw_c(r):
    # 鶴嘴の形の大顎一本 (反った骨の刃)。根に橙の目の玉と骨の留め具
    f = bent("main", [(-5.0, 1.0, 0), (-1.6, 3.4, 0), (2.8, 2.6, 0), (5.6, -1.6, 0)], [0.5, 1.2, 1.0, 0.2])
    f += block("guard", (-0.6, 1.6, 0), (2.4, 3.4, 2.2), taper=(0.7, 0.8))
    f += ell("gem", (-0.6, 0.6, -1.2), (1.1, 1.1, 0.6), segs=6, rings=1)
    f += bent("acc", [(-0.6, -0.2, 0), (-0.4, -3.4, 0), (-0.8, -6.0, 0)], [0.5, 0.4, 0.15])
    f += cord((-0.6, 4.8, 0))
    return f


# ====================================================================== 殻負いの螺貝 (潮の洞。大蛸の殻)
def shell_a(r):
    # 珊瑚の枝が生えた小さな巻貝。殻の口から桃の真珠がのぞく
    f = []
    for k in range(5):
        t = k / 4
        f += ell("main", (0.4 * k, 1.6 + 1.6 * k, 0), (3.4 - 2.2 * t, 1.6 - 0.8 * t, 3.0 - 1.8 * t), segs=8, rings=1)
    f += ell("gem", (-0.2, 0.4, -1.8), (1.2, 1.2, 0.9), segs=6, rings=1)
    f += bent("acc", [(2.4, 2.6, 0), (4.4, 4.6, 0), (4.2, 6.6, 0)], [0.6, 0.45, 0.15])
    f += bent("acc", [(-2.2, 2.2, 0), (-3.8, 3.8, 0)], [0.5, 0.15])
    f += cord((0, 9.4, 0))
    return f


def shell_b(r):
    # 真珠を抱いて巻く一本の蛸の腕 (吸盤の並び)
    f = ell("gem", (0, 0, 0), (2.4, 2.4, 2.4), segs=8, rings=2)
    pts = []
    for k in range(9):
        a = math.radians(-40 + k * 50)
        rr = 3.4 - k * 0.12
        pts.append((math.cos(a) * rr, math.sin(a) * rr - k * 0.4, -0.2 + 0.1 * k))
    f += curve("main", pts, [1.2 - k * 0.11 for k in range(9)], [1.2 - k * 0.11 for k in range(9)], sides=5, caps=(True, True))
    for k in range(1, 8, 2):
        f += ell("acc", (pts[k][0] * 1.08, pts[k][1] * 1.08, pts[k][2] - 0.8), (0.4, 0.4, 0.3), segs=5, rings=1)
    f += cord((0, 5.0, 0))
    return f


def shell_c(r):
    # フジツボに覆われた殻の灯籠 (殻の窓から桃の光)
    f = prism("main", (0, -3.6, 0), (0, 3.0, 0), 3.2, 2.0, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += ell("gem", (0, -0.2, -2.6), (1.4, 1.8, 0.6), segs=6, rings=1)
    for k in range(6):
        a = math.radians(30 + k * 60)
        f += lpkit.spike("w", "acc", (math.cos(a) * 3.0, 1.8 - (k % 2) * 2.6, math.sin(a) * 3.0), (math.cos(a), 0.4, math.sin(a)), 1.2, 0.6, sides=4, rings=1)
    f += prism("trim", (0, 3.0, 0), (0, 4.0, 0), 2.2, 1.2, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += cord((0, 5.4, 0))
    return f


# ====================================================================== 背晶の欠片 (雷晶の空洞。大蜥蜴の背の結晶)
def cry_a(r):
    # 銅の台に、雷の結晶の房 (真ん中が高い三本)
    f = prism("guard", (0, -4.6, 0), (0, -2.6, 0), 3.0, 3.4, sides=6, caps=(True, True), hint=(1, 0, 0))
    for x, h, w in ((0, 7.6, 1.4), (-1.8, 5.2, 1.0), (1.8, 5.6, 1.0)):
        f += lpkit.spike("w", "gem", (x, -2.8, 0), (x * 0.12, 1, 0), h, w, sides=6, rings=1)
    f += cord((0, -6.0, 0))
    return f


def cry_b(r):
    # 紺の鱗の一枚に、根もとから一本の結晶が生える
    f = hexdisc("main", (0, -1.0, 0), 3.8, 1.2, sides=6, phase=30)
    f += lpkit.spike("w", "gem", (0, 0.6, -0.4), (0.15, 1, -0.2), 7.0, 1.3, sides=6, rings=1)
    f += block("trim", (0, -4.4, 0), (3.6, 0.9, 1.6))
    f += cord((0, -5.6, 0))
    return f


def cry_c(r):
    # 二本の角の枠のあいだに、浮かぶ菱形の結晶
    f = []
    for s in (1, -1):
        f += bent("main", [(s * 1.0, -4.0, 0), (s * 3.6, -0.6, 0), (s * 3.0, 3.6, 0), (s * 1.2, 6.0, 0)], [0.8, 0.8, 0.5, 0.15])
    f += lpkit.spike("w", "gem", (0, 0.6, 0), (0, 1, 0), 3.6, 1.6, sides=4, rings=1)
    f += lpkit.spike("w", "gem", (0, 0.6, 0), (0, -1, 0), 3.0, 1.6, sides=4, rings=1)
    f += block("guard", (0, -4.6, 0), (3.0, 1.2, 1.8))
    f += cord((0, -6.0, 0))
    return f


# ====================================================================== 炉の熾 (熔けた坑道。巨人の背の炉)
def forge_a(r):
    # 煉瓦の小さな炉 (火の口が光る) と煙突。鎖で吊る
    f = block("main", (0, -0.6, 0), (5.0, 4.6, 4.0), taper=(0.9, 0.9))
    f += ell("gem", (0, -1.4, -2.1), (1.4, 1.0, 0.4), segs=6, rings=1)
    f += prism("guard", (1.4, 1.6, 0.6), (1.6, 5.2, 0.8), 0.8, 0.7, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += block("trim", (0, 1.8, 0), (5.4, 0.7, 4.4))
    f += cord((0, 6.4, 0))
    return f


def forge_b(r):
    # 鎚の頭の形の黒い玄武岩。割れ目が熔けて光る
    f = block("main", (0, 0.4, 0), (7.4, 3.6, 3.6), taper=(1.0, 1.0))
    f += block("gem", (0, 0.4, -1.85), (5.6, 0.5, 0.2))
    f += prism("guard", (0, -1.4, 0), (0, -6.4, 0), 0.7, 0.6, sides=6, caps=(True, True), hint=(1, 0, 0))
    f += cord((0, 3.4, 0))
    return f


def forge_c(r):
    # 玄武岩の輪の真ん中に、熾の玉
    f = torus("main", (0, 0, 0), 3.6, 0.9, 0.9, n=8, k=4, ax="z")
    f += ell("gem", (0, 0, 0), (2.0, 2.0, 1.4), segs=6, rings=2)
    for k in range(4):
        a = math.radians(45 + k * 90)
        f += block("trim", (math.cos(a) * 3.6, math.sin(a) * 3.6, 0), (1.2, 1.2, 2.4))
    f += cord((0, 5.0, 0))
    return f


ITEMS = {
    "bone_pit": ("骨掘りの顎飾り（鯨骨の坑）", "whale", [jaw_a, jaw_b, jaw_c],
                 ["骨の円盤と交わる二本の大顎", "大百足の節の短い鎖", "鶴嘴の形の大顎一本"]),
    "tide_cave": ("殻負いの螺貝（潮の洞）", "tide", [shell_a, shell_b, shell_c],
                  ["珊瑚の生えた巻貝と真珠", "真珠を抱いて巻く蛸の腕", "フジツボの殻の灯籠"]),
    "storm_hollow": ("背晶の欠片（雷晶の空洞）", "thunder", [cry_a, cry_b, cry_c],
                     ["銅の台に雷の結晶の房", "紺の鱗から生える結晶", "角の枠に浮かぶ菱形の結晶"]),
    "molten_mine": ("炉の熾（熔けた坑道）", "ember", [forge_a, forge_b, forge_c],
                    ["火の口の光る小さな炉", "割れ目の光る鎚の頭", "玄武岩の輪と熾の玉"]),
}
TW, TH = 210, 240


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    rows = []
    for key, (title, titan, fns, caps) in ITEMS.items():
        cols = []
        for i, fn in enumerate(fns):
            r = make(fn, titan, "legend")
            cols.append((r, caps[i]))
            print(key, "ABC"[i], r["tris"], flush=True)
        W = 24 + 3 * (2 * (TW + 4) + 14)
        im = Image.new("RGB", (W, 110 + TH + 40), SH.PLAT)
        dr = ImageDraw.Draw(im)
        SH.label(dr, (16, 12), f"{title} — 三案のラフ", 24, (255, 255, 255))
        SH.label(dr, (16, 48), "左 = 持ち物欄 (正面)、右 = 斜め。伝説の階級の姿 (縁が光る)。材質の言葉はその土地の巨神の装備と同じ", 14, (200, 205, 215))
        for i, (r, cap) in enumerate(cols):
            x = 16 + i * (2 * (TW + 4) + 14)
            SH.label(dr, (x, 76), f"{'ABC'[i]}  {cap}", 16, (150, 232, 255))
            im.paste(SH.tile_img(r, titan, 180, 0, 0.0, 8.5, (TW, TH)), (x, 104))
            im.paste(SH.tile_img(r, titan, 215, 22, 0.0, 8.5, (TW, TH)), (x + TW + 4, 104))
            SH.label(dr, (x, 104 + TH + 6), f"{r['tris']} 面", 13, (170, 175, 185))
            one = Image.new("RGB", (2 * TW + 4, TH), SH.PLAT)
            one.paste(SH.tile_img(r, titan, 180, 0, 0.0, 8.5, (TW, TH)), (0, 0))
            one.paste(SH.tile_img(r, titan, 215, 22, 0.0, 8.5, (TW, TH)), (TW + 4, 0))
            one.save(OUT_DIR / f"{key}_{'abc'[i]}.png")
        im.save(OUT_DIR / f"{key}.png")
        rows.append(im)
    H = sum(im.height for im in rows)
    big = Image.new("RGB", (max(im.width for im in rows), H), SH.PLAT)
    y = 0
    for im in rows:
        big.paste(im, (0, y))
        y += im.height
    big.save(OUT_DIR / "sheet.png")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
