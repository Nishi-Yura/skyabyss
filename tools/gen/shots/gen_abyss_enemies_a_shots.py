#!/usr/bin/env python3
"""深淵の敵 A (9 種 × 三段) の撮影台本 tools/shots/abyss_enemies_a.txt を書く (docs/design/36 の 4・4.2)。

  python -I tools/gen/shots/gen_abyss_enemies_a_shots.py

縦穴の真ん中の空 (半径 20 の内側) に、種ごとに一つの台を置く (9 つの格子)。段 1・2・3 は層の高さを下る:
  元の姿 = 元の層 (骨 y200・塩 y80・雷 y-40)、歪み = 一つ下、崩れ = 二つ下 (深み y-280 まで)。
台は段ごとに置き直す (同じ格子の上に、層の高さだけずらす)。明かりは台の角の海のランタン (見た目を見せるため)。
撮影は ./gradlew runShots --no-daemon -Pscript=tools/shots/abyss_enemies_a.txt -PshotsName=abyss_enemies_a
"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, "tools", "shots", "abyss_enemies_a.txt")

KINDS = ["bone_picker", "rib_guard", "bone_flute", "salt_husk", "tide_hands", "white_shell_keeper",
         "charged_moth", "lightning_idol", "scorched_bird"]
# 段 1〜3 の層の高さ (層 0 骨 1 塩 2 雷 3 灰 4 深み)
LAYER_Y = {0: 200, 1: 80, 2: -40, 3: -160, 4: -280}
NATIVE = {"bone_picker": 0, "rib_guard": 0, "bone_flute": 0, "salt_husk": 1, "tide_hands": 1,
          "white_shell_keeper": 1, "charged_moth": 2, "lightning_idol": 2, "scorched_bird": 2}
CELL = 12  # 台の間隔
GRID = {i: ((i % 3 - 1) * CELL, (i // 3 - 1) * CELL) for i in range(9)}


def platform(x, y, z):
    return [
        "cmd fill %d %d %d %d %d %d minecraft:polished_deepslate" % (x - 4, y - 1, z - 4, x + 4, y - 1, z + 4),
        "cmd setblock %d %d %d minecraft:sea_lantern" % (x - 3, y, z - 3),
        "cmd setblock %d %d %d minecraft:sea_lantern" % (x + 3, y, z + 3),
        "cmd setblock %d %d %d minecraft:sea_lantern" % (x - 1, y + 3, z),
        "cmd setblock %d %d %d minecraft:sea_lantern" % (x + 1, y + 2, z - 2),
    ]


def main():
    lines = [
        "# 深淵の敵 A の 9 種 × 三段 (docs/design/36 の 4・4.2)。tools/gen/shots/gen_abyss_enemies_a_shots.py が書く。",
        "# 縦穴の真ん中の台の上で、種ごと・段ごとに一枚 (正面寄り・斜め)。段 1 は元の層、段 2・3 は一つ・二つ下の高さ。",
        "world abyss_enemies_a flat",
        "size 1280 720",
        "timeout 2400",
        "render 12",
        "clouds off",
        "",
        "wait 80",
        "cmd skyabyss cinematic stop",
        "cmd gamemode spectator",
        "hud off",
        "cmd skyabyss abyss enter",
        "wait 40",
        "cmd skyabyss abyss build",
        "wait 900",
    ]
    for i, kind in enumerate(KINDS):
        gx, gz = GRID[i]
        for stage in (1, 2, 3):
            layer = NATIVE[kind] + stage - 1
            y = LAYER_Y[layer]
            cx, cz = gx, gz
            lines.append("")
            lines.append("# %s 段 %d (層 %d・y %d)" % (kind, stage, layer, y))
            lines += platform(cx, y, cz)
            lines.append("wait 10")
            # 台の上へ。湧く所は p.x+2.5 (前)。正面は約 1.5 ブロック、横は (cx-1, cz+2) から北を向く
            lines.append("cmd tp @s %d %d %d 270 4" % (cx - 2, y, cz))
            lines.append("cmd skyabyss abyss mob %s %d" % (kind, stage))
            lines.append("wait 10")
            # 撮影のあいだは歩かせない (AI を止める。種ごとの動きの tick は残る)
            lines.append("cmd data merge entity @e[type=skyabyss:%s,limit=1,sort=nearest] {NoAI:1b}" % kind)
            lines.append("wait 30")
            # 近寄る: 敵の今いる所から測る (湧いた所から動いていても、大きさが違っても同じ写り。2026-10-28)。正面は前 2.6、横は右 2.6
            sel = "@e[type=skyabyss:%s,limit=1,sort=nearest]" % kind
            lines.append("cmd execute at %s run tp @s ^ ^0.6 ^2.6 facing entity %s eyes" % (sel, sel))
            lines.append("wait 10")
            lines.append("shot %s_s%d_front" % (kind, stage))
            lines.append("cmd execute at %s run tp @s ^2.6 ^0.6 ^ facing entity %s eyes" % (sel, sel))
            lines.append("wait 10")
            lines.append("shot %s_s%d_side" % (kind, stage))
            lines.append("cmd kill @e[type=skyabyss:%s,distance=..30]" % kind)
            lines.append("wait 10")
    lines.append("")
    lines.append("wait 20")
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote", OUT, len(lines), "lines")


if __name__ == "__main__":
    main()
