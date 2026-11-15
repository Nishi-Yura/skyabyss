# -*- coding: utf-8 -*-
"""岩鯨の心臓の技の「heart」の骨の動きを大きくする (2026-11-15 の声「大きくして」)。
第一段・第二段で殻を heart の子にした (tools/install/whale_heart_shell_parent.py) あと、突き・連射・光線などは大きさが 5〜10% 膨らむだけで、
遠目には動いて見えなかった。heart の骨の、もとの姿 (大きさ 1・位置 0・向き 0) からのずれを技ごとの倍率で広げる。時刻は変えない (技の予告と合ったまま)。
二度かけないよう、かけたら tools/gen/anim/whale_heart_amplified.json に印を残す (アニメを書き直したら、その印を消してから流し直す)。
heart_moves.py を流し直したら (アニメが書き直される) これも流し直す。

    python -X utf8 -I tools/gen/anim/whale_heart_amplify.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PATH = ROOT / "src/main/resources/assets/skyabyss/animations/entity/stone_whale_heart.animation.json"
MARK = Path(__file__).resolve().parent / "whale_heart_amplified.json"
# 技: (大きさ, 位置, 向き) の倍率
GAIN = {
    "idle": (1.6, 1.6, 1.4),
    "walk": (1.6, 1.6, 1.4),
    "thrust": (2.6, 2.2, 1.8),
    "volley": (2.4, 2.0, 1.8),
    "beam": (2.6, 2.0, 1.8),
    "sweep": (2.0, 2.0, 1.8),
    "leap": (1.6, 1.6, 1.5),
    "roar_1": (1.8, 1.8, 1.6),
    "roar_2": (1.8, 1.8, 1.6),
    "chain": (2.0, 2.0, 1.8),
    "shell_drop": (2.0, 2.0, 1.8),
}
BASE = {"scale": 1.0, "position": 0.0, "rotation": 0.0}
WHICH = {"scale": 0, "position": 1, "rotation": 2}


def widen(v, base, k):
    if isinstance(v, (int, float)):
        return base + (v - base) * k
    return [round(base + (x - base) * k, 4) for x in v]


def main():
    d = json.loads(PATH.read_text(encoding="utf-8"))
    if MARK.exists():
        print("already amplified")
        return
    n = 0
    for name, anim in d["animations"].items():
        move = name.split(".")[-1]
        if move not in GAIN:
            continue
        bone = anim.get("bones", {}).get("heart")
        if not bone:
            continue
        for ch, keys in bone.items():
            if ch not in BASE or not isinstance(keys, dict):
                continue
            k = GAIN[move][WHICH[ch]]
            for t, v in keys.items():
                if isinstance(v, dict) and "vector" in v:
                    v["vector"] = widen(v["vector"], BASE[ch], k)
                elif isinstance(v, dict):
                    for side in ("pre", "post"):
                        if side in v:
                            v[side] = widen(v[side], BASE[ch], k)
                else:
                    keys[t] = widen(v, BASE[ch], k)
                n += 1
    MARK.write_text(json.dumps({"amplified": sorted(GAIN)}, ensure_ascii=False), encoding="utf-8")
    PATH.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
    print("widened", n, "keys")


main()
