"""一章の素材 4 つの 32x32 アイコン 3 案 (2026-10-28 のクオリティ点検。仮の絵 (右上の紫の三角) のままだった)。
骨片 (bone_chip)・野兎の針 (hare_quill)・岩殻の欠片 (rock_shell_scrap)・岩殻の糸 (rock_shell_thread)。描き方は icons_phase2.Icon。案ごとに形を変える。

実行: python tools/rough/icons_ch1_materials.py
出力: docs/review/roughs_ch1_materials/<品>_<案>.png と compare_0.png
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from icons_phase2 import Icon, E, P, R, L, sheet, CORD  # noqa: E402
import icons_phase2  # noqa: E402

OUT = Path(__file__).resolve().parents[2] / "docs/review/roughs_ch1_materials"
icons_phase2.OUT = OUT
BONE = (226, 214, 186)
BONE_D = (176, 162, 132)
QUILL = (214, 196, 160)
QUILL_TIP = (92, 70, 52)
SHELL = (104, 112, 124)
SHELL_L = (150, 160, 172)
AMBER = (226, 150, 60)
THREAD = (176, 186, 196)


def bone_chip(k):
    ic = Icon()
    if k == 0:   # 割れた骨のかけら 2 つ (断面の髄)
        ic.put(ic.mask(P([(4, 20), (14, 10), (20, 14), (11, 26)])), BONE)
        ic.put(ic.mask(P([(15, 24), (25, 12), (29, 17), (21, 28)])), BONE_D)
        ic.px([(12, 15), (13, 16), (22, 18)], (150, 110, 90))
        d = "割れた骨のかけら 2 つ (髄の点)"
    elif k == 1:   # 紐で括った骨片の束
        for x, y0, y1 in ((10, 6, 27), (15, 4, 28), (20, 7, 26), (24, 9, 25)):
            ic.put(ic.mask(L([(x, y0), (x + 2, y1)], 2)), BONE)
        ic.put(ic.mask(L([(7, 18), (27, 16)], 2)), CORD, shade=False)
        d = "紐で括った骨片の束"
    else:   # 削った骨の小札 (穴あき)
        ic.put(ic.mask(P([(9, 4), (23, 4), (26, 16), (16, 29), (6, 16)])), BONE)
        ic.put(ic.mask(E((14, 8, 18, 12))), (60, 52, 44), shade=False)
        ic.put(ic.mask(L([(10, 18), (22, 18)], 1)), BONE_D, shade=False)
        d = "削った骨の小札 (紐の穴)"
    return ic.done(), d


def hare_quill(k):
    ic = Icon()
    if k == 0:   # 一本の長い針 (先が黒い)
        ic.put(ic.mask(L([(6, 27), (26, 5)], 2)), QUILL)
        ic.put(ic.mask(L([(22, 9), (27, 4)], 2)), QUILL_TIP, shade=False)
        d = "一本の長い針 (先が黒い)"
    elif k == 1:   # 扇に広げた三本
        for a, b in (((8, 28), (12, 5)), ((10, 28), (22, 7)), ((12, 28), (29, 15))):
            ic.put(ic.mask(L([a, b], 1)), QUILL)
            ic.px([b], QUILL_TIP)
        ic.put(ic.mask(E((6, 25, 14, 31))), (150, 120, 90))
        d = "根元を括って扇に広げた三本"
    else:   # 毛のついた皮の切れから生えた針
        ic.put(ic.mask(E((4, 16, 28, 30))), (160, 126, 90))
        for x in (8, 12, 16, 20, 24):
            ic.put(ic.mask(L([(x, 20), (x + 3, 4 + (x % 3) * 2)], 1)), QUILL)
            ic.px([(x + 3, 4 + (x % 3) * 2)], QUILL_TIP)
        d = "皮の切れから生えた針"
    return ic.done(), d


def rock_shell_scrap(k):
    ic = Icon()
    if k == 0:   # 層の見える殻の板 (割れ口が明るい)
        ic.put(ic.mask(P([(4, 14), (16, 5), (28, 10), (26, 24), (10, 28)])), SHELL)
        for y in (12, 17, 22):
            ic.put(ic.mask(L([(8, y + 2), (26, y - 1)], 1)), SHELL_L, shade=False)
        d = "層の見える殻の板"
    elif k == 1:   # 反った殻のかけら (内は真珠色)
        ic.put(ic.mask(P([(3, 22), (9, 9), (22, 4), (29, 12), (21, 18), (10, 27)])), SHELL)
        ic.put(ic.mask(P([(10, 21), (13, 12), (22, 8), (19, 15)])), (206, 210, 216), shade=False)
        d = "反った殻のかけら (内は真珠色)"
    else:   # 苔のついた殻の鱗 3 枚
        for cx, cy in ((11, 20), (19, 13), (21, 23)):
            ic.put(ic.mask(E((cx - 7, cy - 6, cx + 7, cy + 6))), SHELL)
            ic.px([(cx - 2, cy - 3), (cx - 1, cy - 3)], (110, 150, 90))
        d = "苔のついた殻の鱗 3 枚"
    return ic.done(), d


def rock_shell_thread(k):
    ic = Icon()
    if k == 0:   # 殻の繊維の糸の輪 (琥珀の留め)
        for r in (11, 8):
            ic.put(ic.mask(lambda d, r=r: d.ellipse((16 - r, 16 - r, 16 + r, 16 + r), outline=1, width=2)), THREAD, shade=False)
        ic.put(ic.mask(E((20, 22, 26, 28))), AMBER)
        d = "糸の輪 (琥珀の留め)"
    elif k == 1:   # 骨の芯に巻いた糸
        ic.put(ic.mask(L([(7, 26), (25, 6)], 3)), BONE)
        ic.put(ic.mask(P([(10, 17), (17, 10), (23, 16), (16, 23)])), THREAD)
        for i in range(4):
            ic.put(ic.mask(L([(11 + i * 3, 19 - i * 3), (15 + i * 3, 23 - i * 3)], 1)), (130, 140, 150), shade=False)
        d = "骨の芯に巻いた糸"
    else:   # 殻のかけらから引き出した糸
        ic.put(ic.mask(P([(4, 24), (10, 14), (18, 16), (14, 28)])), SHELL)
        ic.put(ic.mask(L([(14, 18), (20, 12), (24, 16), (28, 6)], 1)), THREAD, shade=False)
        d = "殻のかけらから引き出した糸"
    return ic.done(), d


ITEMS = [("bone_chip", "骨片", bone_chip), ("hare_quill", "野兎の針", hare_quill),
         ("rock_shell_scrap", "岩殻の欠片", rock_shell_scrap), ("rock_shell_thread", "岩殻の糸", rock_shell_thread)]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for iid, jp, fn in ITEMS:
        opts = []
        for k in range(3):
            img, d = fn(k)
            img.save(OUT / f"{iid}_{'abc'[k]}.png")
            opts.append((img, d))
        rows.append((jp, opts))
    sheet(rows, "compare_0.png")
    print("ok", len(rows))


if __name__ == "__main__":
    main()
