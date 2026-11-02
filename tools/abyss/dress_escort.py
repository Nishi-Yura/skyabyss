"""深淵の護りの下り道 (塩・灰。印 esc_<層>_<n> が道の芯) に、坑の支え木の枠を入れる (2026-10-28 夜の見直し)。

下り道は真っ直ぐの細い坑で、壁が平らなまま続いていた。探掘隊が掘り進んだ坑らしく、道ぞい 7 マスごとに
支え木の枠 (両の壁に柱、天井に梁) を置く。形は変えない: 柱と梁は今の壁・天井のブロックを置き換えるだけで、通る所 (空気) には何も置かない。
  - 置き換えるのは層の岩だけ (灯り・鎖・格子・仕掛けのブロックは触らない)
  - 仕掛けの箱 (<名>_a / <名>_b の印の間: 脆い床・井戸・迷路・封 …) と、待ち場・縄張りの印のまわり 3 は避ける
  - 塩は唐檜 (淡い塩に濃い木)、灰は紅樹 (燠の色)。光るブロックは使わない
二度は入れない (設計図の dress に escort_frames があればやめる)。建て直しには AbyssBuild.VERSION を上げる。

  python -I tools/abyss/dress_escort.py
"""
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen", "world"))
import town_outskirts as T  # noqa: E402  (設計図の読み書き)

PATH = os.path.join(T.DIR, "abyss_layers.json")
TAG = "escort_frames"
STEP = 7
WOOD = {"salt": ("minecraft:stripped_spruce_log", "minecraft:spruce_log"),
        "ash": ("minecraft:stripped_mangrove_log", "minecraft:mangrove_log")}
ROCK = {"salt": {"minecraft:calcite", "minecraft:white_terracotta", "minecraft:snow_block", "minecraft:diorite", "minecraft:smooth_quartz",
                 "minecraft:amethyst_block", "minecraft:pink_terracotta", "minecraft:smooth_sandstone"},
        "ash": {"minecraft:blackstone", "minecraft:basalt[axis=y]", "minecraft:smooth_basalt", "minecraft:cobbled_deepslate", "minecraft:gravel",
                "minecraft:polished_basalt[axis=y]", "minecraft:magma_block", "minecraft:tuff"}}


def main():
    d, c = T.load(PATH)
    if TAG in d.get("dress", []):
        print("already done")
        return
    m = d["marks"]
    boxes = []
    for k in m:
        if k.endswith("_a") and k[:-2] + "_b" in m:
            a, b = m[k], m[k[:-2] + "_b"]
            boxes.append((min(a[0], b[0]) - 1, min(a[1], b[1]) - 1, min(a[2], b[2]) - 1, max(a[0], b[0]) + 1, max(a[1], b[1]) + 1, max(a[2], b[2]) + 1))
    keep = [v for k, v in m.items() if k.startswith(("escwait_", "wp_", "mark_", "socket_", "journal_", "frag_", "lamp_", "grave_", "carve_", "stake_", "anchor_"))]

    def in_box(x, y, z):
        return any(b[0] <= x <= b[3] and b[1] <= y <= b[4] and b[2] <= z <= b[5] for b in boxes)

    def near_keep(x, y, z):
        return any(abs(x - k[0]) <= 3 and abs(y - k[1]) <= 3 and abs(z - k[2]) <= 3 for k in keep)

    def air(x, y, z):
        s = c.get((x, y, z))
        return s is not None and (s.endswith(":air") or "cave_air" in s)

    placed = frames = 0
    for layer in ("salt", "ash"):
        n = 0
        while f"esc_{layer}_{n}" in m:
            n += 1
        post, beam = WOOD[layer]
        rock = ROCK[layer]
        for k in range(STEP, n - STEP, STEP):
            x0, y0, z0 = m[f"esc_{layer}_{k}"]
            ax, _, az = m[f"esc_{layer}_{k - 2}"]
            bx, _, bz = m[f"esc_{layer}_{k + 2}"]
            dx, dz = bx - ax, bz - az
            if dx == 0 and dz == 0:
                continue
            # 坑の向きを東西か南北に寄せる (斜めの坑は飛ばす)
            if abs(dx) > abs(dz) * 2:
                px, pz, along = 0, 1, "x"
            elif abs(dz) > abs(dx) * 2:
                px, pz, along = 1, 0, "z"
            else:
                continue
            if in_box(x0, y0, z0) or near_keep(x0, y0, z0) or not air(x0, y0, z0):
                continue
            # 天井の高さと両の壁
            h = 0
            while h < 8 and air(x0, y0 + h, z0):
                h += 1
            if h < 2 or h >= 8:
                continue
            sides = []
            for sgn in (-1, 1):
                w = 0
                while w < 4 and air(x0 + sgn * w * px, y0, z0 + sgn * w * pz):
                    w += 1
                if w >= 4:
                    break
                sides.append(sgn * w)
            if len(sides) < 2:
                continue
            cells = []
            for s in sides:
                for yy in range(y0, y0 + h):
                    cells.append(((x0 + s * px, yy, z0 + s * pz), post + "[axis=y]"))
            cross = "x" if along == "z" else "z"
            for w in range(sides[0], sides[1] + 1):
                cells.append(((x0 + w * px, y0 + h, z0 + w * pz), beam + f"[axis={cross}]"))
            # 枠の全部が層の岩の上に置けるときだけ (欠けた枠は置かない)
            if all(c.get(p) in rock and not in_box(*p) for p, _ in cells):
                for p, st in cells:
                    c[p] = st
                    placed += 1
                frames += 1
    d.setdefault("dress", []).append(TAG)
    T.save(PATH, d, c)
    print(f"escort frames: {frames} frames, {placed} blocks")


if __name__ == "__main__":
    main()
