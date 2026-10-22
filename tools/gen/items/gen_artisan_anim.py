#!/usr/bin/env python3
"""旅の職人の動き (idle / walk / talk) を書く。語り部・イサナと同じ書き方 (GeckoLib の式)。

  python tools/gen/items/gen_artisan_anim.py   -> assets/skyabyss/animations/entity/artisan.animation.json
頭は NpcRenderer が相手の方へ向ける (アニメでは動かさない)。脚は外套の裾が 6 ほどまで降りているので、振りは 18 度までにしている。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "animations", "entity", "artisan.animation.json")


def rot(x, y="0", z="0"):
    return {"rotation": [x, y, z]}


anim = {"format_version": "1.8.0", "animations": {
    # 待つ: 息で胴が上下し、右手が時々道具の柄を直す
    "animation.artisan.idle": {"loop": True, "animation_length": 5.0, "bones": {
        "body": {"position": ["0", "math.sin(query.anim_time * 72) * 0.2", "0"], "rotation": ["math.sin(query.anim_time * 72) * 0.6", "0", "0"]},
        "armL": {"rotation": ["math.sin(query.anim_time * 72 + 30) * 1.5", "0", "math.sin(query.anim_time * 72) * 0.8"]},
        "armR": {"rotation": ["math.sin(query.anim_time * 72 + 90) * 1.5 + math.max(0, math.sin(query.anim_time * 144)) * 3", "0", "0"]}}},
    "animation.artisan.walk": {"loop": True, "animation_length": 1.0, "bones": {
        "legL": rot("math.sin(query.anim_time * 360) * 18"),
        "legR": rot("-math.sin(query.anim_time * 360) * 18"),
        "armL": rot("-math.sin(query.anim_time * 360) * 12"),
        "armR": rot("math.sin(query.anim_time * 360) * 12"),
        "body": {"position": ["0", "math.abs(math.sin(query.anim_time * 360)) * 0.4", "0"], "rotation": ["0", "math.sin(query.anim_time * 360) * 1.5", "0"]}}},
    # 話す: 右手が前で身ぶりをし、胴が小さく前後する
    "animation.artisan.talk": {"loop": True, "animation_length": 3.0, "bones": {
        "armR": rot("32 + math.sin(query.anim_time * 120) * 7", "0", "math.sin(query.anim_time * 120 + 40) * 3"),
        "armL": rot("8 + math.sin(query.anim_time * 120 + 90) * 3"),
        "body": {"position": ["0", "math.sin(query.anim_time * 120) * 0.15", "0"], "rotation": ["math.sin(query.anim_time * 120) * 1.2", "0", "0"]}}},
}}

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    json.dump(anim, f, indent=1)
    f.write("\n")
print("wrote", OUT)
