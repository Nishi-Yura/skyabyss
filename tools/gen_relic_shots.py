#!/usr/bin/env python3
"""遺物ブロックの全様式を並べて撮る台本 tools/shots/relic_blocks.txt を書く。
使い方: python tools/gen_relic_shots.py → ./gradlew runShots --no-daemon -Pscript=tools/shots/relic_blocks.txt → python -I tools/relic_blocks_sheet.py
平らな世界の暗い床に、種類ごとに 4 巨神 (鯨・潮・嵐・余燼) を左から並べ、状態の違い (手前・奥) を二列に置く。"""
from pathlib import Path

OUT = Path(__file__).resolve().parent / "shots/relic_blocks.txt"
Y = -60
TITANS = ["whale", "tide", "storm", "ember"]
X0, STEP = 100, 3
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
         "cmd time set 13200", "cmd weather clear", "cmd gamemode spectator", "hud off", "cmd gamerule doDaylightCycle false"]
    # 床
    L.append(f"cmd fill {X0 - 8} {Y - 1} -4 {X0 + 20} {Y - 1} {len(ROWS) * 10 + 4} minecraft:polished_deepslate")
    for i, (name, a, b) in enumerate(ROWS):
        z = i * 10
        for j, t in enumerate(TITANS):
            x = X0 + j * STEP
            sa = (a + "," if a else "") + f"titan={t}"
            sb = (b + "," if b else "") + f"titan={t}"
            if name == "trial_seal":
                L.append(f"cmd setblock {x} {Y} {z} skyabyss:{name}[titan={t}]")
                L.append(f"cmd setblock {x} {Y} {z + 3} skyabyss:{name}[titan={t}]")
                continue
            if name == "tablet":
                L.append(f"cmd setblock {x} {Y} {z + 3} minecraft:stone_bricks")
                L.append(f"cmd setblock {x} {Y} {z + 4} minecraft:stone_bricks")
                L.append(f"cmd setblock {x} {Y} {z} skyabyss:{name}[{sa}]")
                L.append(f"cmd setblock {x} {Y} {z + 4} skyabyss:{name}[{sb}]")
                continue
            L.append(f"cmd setblock {x} {Y} {z} skyabyss:{name}[{sa}]")
            L.append(f"cmd setblock {x} {Y} {z + 3} skyabyss:{name}[{sb}]")
    for i, (name, a, b) in enumerate(ROWS):
        z = i * 10
        cx = X0 + 1.5 * STEP + 0.5
        L.append(f"camera {cx} {Y + 3.6} {z + 8.5} 180 30")
        L.append("waitchunks")
        L.append("wait 20")
        L.append(f"shot relic_{name}")
    L.append("quit")
    OUT.write_text("\n".join(L) + "\n", encoding="utf-8")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
