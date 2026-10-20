#!/usr/bin/env python3
"""遺物ブロックの全様式を並べて撮る台本 tools/shots/relic_blocks.txt を書く。
使い方: python tools/gen_relic_shots.py → ./gradlew runShots --no-daemon -Pscript=tools/shots/relic_blocks.txt → python -I tools/relic_blocks_sheet.py
平らな世界の暗い床に、種類ごとに 4 巨神 (鯨・潮・嵐・余燼) を左から並べ、状態の違い (手前・奥) を二列に置く。"""
from pathlib import Path

OUT = Path(__file__).resolve().parent / "shots/relic_blocks.txt"
Y = -60
TITANS = ["whale", "tide", "storm", "ember"]
X0, STEP = 0, 3
# 種類: (名前, 手前の行の状態, 奥の行の状態, 向きのプロパティ)
ROWS = [
    ("relic_cache", "facing=south,open=false", "facing=south,open=true"),
    ("rest_hearth", "lit=true", "lit=false"),
    ("relic_lever", "face=floor,facing=south,powered=false", "face=floor,facing=south,powered=true"),
    ("relic_button", "face=floor,facing=south,powered=false", "face=floor,facing=south,powered=true"),
    ("relic_plate", "powered=false", "powered=true"),
    ("relic_bell", "facing=north,ring=false", "facing=north,ring=true"),
    ("tablet", "facing=south,standing=true", "facing=south,standing=false"),
    ("trial_seal", "", ""),
]


def main():
    L = ["# 遺物ブロックの全様式 (自動生成: tools/gen_relic_shots.py)", "world relic_show flat", "size 1280 720", "timeout 600", "render 8", "clouds off",
         "cmd time set noon", "cmd weather clear", "cmd gamemode spectator", "hud off", "cmd gamerule doDaylightCycle false", "camera 0.5 -60 0.5 0 0", "waitchunks", "wait 1500", "cmd effect give @p minecraft:night_vision infinite 0 true", "fov 62"]
    for i, (name, a, b) in enumerate(ROWS):
        z = i * 10
        cx = X0 + 1.5 * STEP + 0.5
        # 先にそこへ行って読み込ませてから置く
        small = name in ("relic_lever", "relic_button", "relic_plate")
        L.append(f"camera {cx} {Y + (1.2 if small else 1.9)} {z + (8.4 if small else 8.2)} 180 {34 if small else 21}")
        L.append("fov 56" if small else "fov 58")
        L.append("waitchunks")
        L.append(f"cmd fill {X0 - 6} {Y - 1} {z - 3} {X0 + 16} {Y - 1} {z + 8} minecraft:polished_deepslate")
        L.append(f"cmd fill {X0 - 6} {Y} {z - 3} {X0 + 16} {Y + 6} {z + 8} minecraft:air")
        for j, t in enumerate(TITANS):
            x = X0 + j * STEP
            sa = (a + "," if a else "") + f"titan={t}"
            sb = (b + "," if b else "") + f"titan={t}"
            if name == "trial_seal":
                L.append(f"cmd setblock {x} {Y} {z} skyabyss:{name}[titan={t}]")
                L.append(f"cmd setblock {x} {Y} {z + 3} skyabyss:{name}[titan={t}]")
            elif name == "tablet":
                L.append(f"cmd setblock {x} {Y} {z + 1} minecraft:stone_bricks")
                L.append(f"cmd setblock {x} {Y} {z + 5} skyabyss:{name}[{sa}]")
                L.append(f"cmd setblock {x} {Y} {z + 2} skyabyss:{name}[{sb}]")
            else:
                L.append(f"cmd setblock {x} {Y} {z + 3} skyabyss:{name}[{sa}]")
                L.append(f"cmd setblock {x} {Y} {z} skyabyss:{name}[{sb}]")
        L.append("wait 30")
        L.append("chat clear")
        L.append("wait 5")
        L.append(f"shot relic_{name}")
    L.append("quit")
    OUT.write_text("\n".join(L) + "\n", encoding="utf-8")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
