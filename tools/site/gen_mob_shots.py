# -*- coding: utf-8 -*-
"""wiki の敵の姿の絵を撮る台本 (tools/shots/wiki_mobs.txt) を書く。

敵の並びと大きさは tools/sources/site/wiki.json (wikidump) から。wiki の「敵と主」に載る敵 (wiki.NOT_FOES を除く生き物) だけ。
ライムの幕の部屋の真ん中に、動かない敵を南向きに呼び、南東から斜めに撮る。体の高さ・幅でカメラの遠さを決める。
撮ったら `python -I tools/site/cut_mob_shots.py` で幕を抜く。

使い方: python -I tools/site/gen_mob_shots.py [敵の名前 ...] (名前を渡すとその敵だけ撮り直す台本) → bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/wiki_mobs.txt -PshotsName=wiki_mobs
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wiki import DUMP, NOT_FOES  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "tools", "shots", "wiki_mobs.txt")
FLOOR = -61  # 平らな世界の地面
FOV = 50
EYE = 1.62  # 立った人の目の高さ
# 模型が当たりの箱よりずっと大きい敵 (カメラが体の中に入る) の遠さの倍
FAR = {"skyabyss:rock_shell_beetle": 3.0}


def foes(dump):
    out = []
    for m in dump["mobs"]:
        pkg = ".".join(m.get("class", "").split(".")[3:-1])
        if m.get("living") and m.get("stats") and not any(pkg.startswith(p) for p in NOT_FOES):
            out.append(m)
    return out


def main():
    dump = json.load(open(DUMP, encoding="utf8"))
    mobs = foes(dump)
    if len(sys.argv) > 1:
        # 撮り直し: 名前を渡した敵だけ (台本は全部の形に戻すため、あとで引数なしでもう一度書く)
        mobs = [m for m in mobs if m["id"].split(":", 1)[1] in sys.argv[1:]]
    lines = [
        "# wiki の敵の姿 (tools/site/gen_mob_shots.py が書く。手で直さない)。ライムの幕の部屋で一体ずつ、南東から斜めに",
        "# bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/wiki_mobs.txt -PshotsName=wiki_mobs → python -I tools/site/cut_mob_shots.py",
        "world wiki_mobs flat", "size 720 720", "timeout 3600", "render 8", "clouds off", "fov %d" % FOV, "wait 80",
        "cmd skyabyss cinematic stop", "close", "cmd time set noon", "cmd weather clear", "cmd gamerule doDaylightCycle false",
        "cmd gamerule doMobSpawning false", "cmd gamemode spectator",
        # 幕: 床と、北・西・南・東の壁 (高さ 80)。カメラは部屋の中
        "cmd fill -60 %d -60 60 %d 60 minecraft:lime_concrete" % (FLOOR, FLOOR),
        "cmd fill -60 %d -60 60 %d -60 minecraft:lime_concrete" % (FLOOR + 1, FLOOR + 80),
        "cmd fill -60 %d -60 -60 %d 60 minecraft:lime_concrete" % (FLOOR + 1, FLOOR + 80),
        "cmd fill -60 %d 60 60 %d 60 minecraft:lime_concrete" % (FLOOR + 1, FLOOR + 80),
        "cmd fill 60 %d -60 60 %d 60 minecraft:lime_concrete" % (FLOOR + 1, FLOOR + 80),
        "camera 0 -50 30 180 10", "waitchunks", "hud off", "wait 40", "cmd skyabyss cinematic stop", "close",
    ]
    half = math.tan(math.radians(FOV / 2))
    for m in mobs:
        mid = m["id"]
        h = max(0.4, m.get("height", 1))
        w = max(0.4, m.get("width", 1))
        d = max(h / (1.4 * half), w / (1.1 * half), 2.6)
        if m["stats"].get("generic.max_health", 0) >= 150:
            d *= 1.5  # 主・中ボスの模型は当たりの箱より大きい
        d *= FAR.get(mid, 1.0)
        cx, cz = d * math.sin(math.radians(30)), d * math.cos(math.radians(30))
        # 敵は区画の真ん中 (0.5, 0.5) に立つ。camera は足もとの高さを取るので、目の高さ (EYE) を引く
        eye = FLOOR + 1 + h * 0.55 + d * 0.12
        cy = eye - EYE
        pitch = math.degrees(math.atan2(eye - (FLOOR + 1 + h * 0.5), d))
        yaw = math.degrees(math.atan2(cx, -cz)) % 360
        cx, cz = cx + 0.5, cz + 0.5
        name = mid.split(":", 1)[1]
        lines += [
            "", "# " + m["name"],
            # 前の敵はずっと下へ移してから消す (倒れたときの煙が次の絵に写らない)
            "cmd tp @e[type=!player] 0 -250 0", "cmd kill @e[type=!player]",
            "cmd summon %s 0.5 %d 0.5 {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,Silent:1b,NoGravity:1b,Rotation:[0f,0f]}" % (mid, FLOOR + 1),
            "camera %.2f %.2f %.2f %.1f %.1f" % (cx, cy, cz, yaw, pitch),
            "wait 30", "chat clear", "shot %s" % name,
        ]
    lines += ["cmd kill @e[type=!player]", "quit"]
    with open(OUT, "w", encoding="utf8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print("mobs:", len(mobs), "->", OUT)


if __name__ == "__main__":
    main()
