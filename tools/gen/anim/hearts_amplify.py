# -*- coding: utf-8 -*-
"""潮・雷翼・燠の心臓の、動きの小さい技を大きくする (2026-11-15 の声「ほかの心臓の動きも同じように見直して」。岩鯨は whale_heart_amplify.py)。
技ごとに「見える動きの量」を測る: 体の三角形の割合で重みを付けた、骨 (親から受け継ぐぶんも) の大きさ・位置・向きのいちばん大きなずれ
(大きさ 1% = 1、位置 1/16 ブロック = 1、向き 2 度 = 1)。三つの段の平均が目標 (技 14、待ち 7) に届かない技は、
その技のすべての骨の小さな動き (向き 90 度・大きさ 40%・位置 16 までのもの。回り続ける動きは触らない) を、同じ倍率 (最大 3 倍) で広げる。
時刻は変えない (技の予告と合ったまま)。登場・倒れる・崩れ・被弾・殻の割れは触らない。
二度かけないよう、かけた心臓は tools/gen/anim/hearts_amplified.json に記す (アニメを書き直したら、その名前を消してから流し直す)。

    python -X utf8 -I tools/gen/anim/hearts_amplify.py           広げる
    python -X utf8 -I tools/gen/anim/hearts_amplify.py --dry     量と倍率を見るだけ
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
A = ROOT / "src/main/resources/assets/skyabyss"
MARK = Path(__file__).resolve().parent / "hearts_amplified.json"
HEARTS = ["tide_heart", "storm_heart", "ember_heart"]
SKIP = {"intro", "death", "stagger", "flinch", "crack", "reclose"}
TARGET_MOVE = 14.0
TARGET_IDLE = 7.0
MAX_GAIN = 3.0
BASE = {"scale": 1.0, "position": 0.0, "rotation": 0.0}
SMALL = {"scale": 0.4, "position": 16.0, "rotation": 90.0}


def vec(v):
    if isinstance(v, dict):
        v = v.get("vector", v.get("post", v.get("pre")))
    if isinstance(v, (int, float)):
        return [float(v)] * 3
    return [float(x) if isinstance(x, (int, float)) else 0.0 for x in v]


def keys_of(k):
    return k if isinstance(k, dict) else {"0": k}


def dev(ch, keys):
    m = 0.0
    for v in keys_of(keys).values():
        x = vec(v)
        if ch == "scale":
            m = max(m, max(abs(a - 1) for a in x) * 100)
        elif ch == "position":
            m = max(m, max(abs(a) for a in x))
        else:
            m = max(m, max(abs(a) for a in x) / 2)
    return m


def raw(ch, keys):
    m = 0.0
    for v in keys_of(keys).values():
        m = max(m, max(abs(a - BASE[ch]) for a in vec(v)))
    return m


def phase_parts(h, p):
    mesh = json.loads((A / ("lowpoly/%s_p%d.json" % (h, p))).read_text(encoding="utf-8"))
    geo = json.loads((A / ("geo/entity/%s_p%d_lowpoly.geo.json" % (h, p))).read_text(encoding="utf-8"))["minecraft:geometry"][0]["bones"]
    parent = {b["name"]: b.get("parent") for b in geo}
    tris = {k: v.get("triangle_count", 0) for k, v in mesh["bones"].items()}
    return parent, tris


def score(bones, parent, tris):
    total = sum(tris.values()) or 1
    s = 0.0
    for b, t in tris.items():
        if not t:
            continue
        d = 0.0
        c = b
        while c:
            for ch, keys in bones.get(c, {}).items():
                if ch in BASE:
                    d = max(d, dev(ch, keys))
            c = parent.get(c)
        s += d * t / total
    return s


def widen(v, base, k):
    if isinstance(v, (int, float)):
        return round(base + (v - base) * k, 4)
    return [round(base + (x - base) * k, 4) if isinstance(x, (int, float)) else x for x in v]


def main():
    dry = "--dry" in sys.argv
    done = json.loads(MARK.read_text(encoding="utf-8")) if MARK.exists() else {}
    for h in HEARTS:
        if h in done and not dry:
            print(h, "already amplified")
            continue
        path = A / ("animations/entity/%s.animation.json" % h)
        d = json.loads(path.read_text(encoding="utf-8"))
        phases = [phase_parts(h, p) for p in (1, 2, 3)]
        gains = {}
        for name, anim in d["animations"].items():
            move = name.split(".")[-1]
            if move in SKIP:
                continue
            bones = anim.get("bones", {})
            s = sum(score(bones, par, tr) for par, tr in phases) / len(phases)
            target = TARGET_IDLE if move in ("idle", "walk") else TARGET_MOVE
            k = min(MAX_GAIN, target / s) if s > 0.05 else 1.0
            if k <= 1.05:
                continue
            gains[move] = round(k, 2)
            if dry:
                continue
            for bone in bones.values():
                for ch, keys in bone.items():
                    if ch not in BASE or not isinstance(keys, (dict, list, int, float)) or raw(ch, keys) > SMALL[ch]:
                        continue
                    if not isinstance(keys, dict):
                        bone[ch] = widen(keys, BASE[ch], k)
                        continue
                    for t, v in keys.items():
                        if isinstance(v, dict) and "vector" in v:
                            v["vector"] = widen(v["vector"], BASE[ch], k)
                        elif isinstance(v, dict):
                            for side in ("pre", "post"):
                                if side in v:
                                    v[side] = widen(v[side], BASE[ch], k)
                        else:
                            keys[t] = widen(v, BASE[ch], k)
        print(h, "gains", gains)
        if not dry:
            path.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
            done[h] = gains
    if not dry:
        MARK.write_text(json.dumps(done, ensure_ascii=False, indent=1), encoding="utf-8")


main()
