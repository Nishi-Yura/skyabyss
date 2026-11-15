# -*- coding: utf-8 -*-
"""動きの小さい心臓の技に、体全体の「溜め → 放つ → 戻る」の身ぶりを足す (2026-11-15 の声「残りの小さい技にも動きを足して」)。
hearts_amplify.py で 3 倍にしても小さかった技は、heart の骨が当たりの直前に少し震えるだけだった。
当たりの時刻 = heart の位置の震えがいちばん大きい鍵 (震えの終わり。技の予告と合っている) として、そのまわりに heart の大きさと向きの鍵を足す:
  当たりの 0.9 秒前から溜め (縮んで低く・後ろへ傾く) → 当たりで放つ (膨らむ・前へ傾く) → 少し行き過ぎて → 戻る。
heart の骨にすでに大きさ・向きの動きがある技は、その向きの鍵は足さない (もとの動きを壊さない)。
二度かけないよう、かけたら tools/gen/anim/hearts_gestured.json に記す。

    python -X utf8 -I tools/gen/anim/hearts_gesture.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
A = ROOT / "src/main/resources/assets/skyabyss/animations/entity"
MARK = Path(__file__).resolve().parent / "hearts_gestured.json"
# 心臓: (技, 傾く軸 0=x 1=y 2=z, 溜めの縮み, 放つ膨らみ)
MOVES = {
    "tide_heart": (["ebb", "pearl_rain", "moon_wave", "flood", "shell_strike", "breath"], 2, 0.9, 1.16),
    "storm_heart": (["web", "feather", "chain", "flash", "charge", "summon"], 0, 0.88, 1.18),
    "ember_heart": (["veil", "floor", "breath", "orb", "embers"], 0, 0.9, 1.15),
}
TILT = 9.0


def vec(v):
    if isinstance(v, dict):
        v = v.get("vector", v.get("post", v.get("pre")))
    if isinstance(v, (int, float)):
        return [float(v)] * 3
    return [float(x) if isinstance(x, (int, float)) else 0.0 for x in v]


def hit_time(heart, length):
    pos = heart.get("position") if heart else None
    if isinstance(pos, dict) and pos:
        best, bt = -1.0, length * 0.5
        for t, v in pos.items():
            m = max(abs(a) for a in vec(v))
            if m > best:
                best, bt = m, float(t)
        if best > 0:
            return bt
    return length * 0.5


def key(t):
    return "%.4f" % max(0.0, t)


def main():
    done = json.loads(MARK.read_text(encoding="utf-8")) if MARK.exists() else {}
    for h, (moves, axis, squash, swell) in MOVES.items():
        if h in done:
            print(h, "already gestured")
            continue
        path = A / ("%s.animation.json" % h)
        d = json.loads(path.read_text(encoding="utf-8"))
        added = []
        for mv in moves:
            anim = d["animations"].get("animation.%s.%s" % (h, mv))
            if not anim:
                continue
            L = float(anim.get("animation_length", 2.0))
            bones = anim.setdefault("bones", {})
            heart = bones.setdefault("heart", {})
            ti = min(hit_time(heart, L), L - 0.2)
            t0 = max(0.0, ti - 0.9)
            tw = max(t0 + 0.05, ti - 0.12)
            t1 = min(L - 0.1, ti + 0.14)
            t2 = min(L, ti + 0.5)
            if "scale" not in heart:
                lo = [1 + (1 - squash) * 0.5, squash, 1 + (1 - squash) * 0.5]
                heart["scale"] = {
                    key(0): {"vector": [1.0, 1.0, 1.0], "easing": "linear"},
                    key(t0): {"vector": [1.0, 1.0, 1.0], "easing": "linear"},
                    key(tw): {"vector": [round(x, 4) for x in lo], "easing": "easeInQuad"},
                    key(ti): {"vector": [swell, swell, swell], "easing": "easeOutBack"},
                    key(t1): {"vector": [0.97, 0.97, 0.97], "easing": "easeInOutSine"},
                    key(t2): {"vector": [1.0, 1.0, 1.0], "easing": "easeOutSine"},
                    key(L): {"vector": [1.0, 1.0, 1.0], "easing": "linear"},
                }
            if "rotation" not in heart:
                def r(a):
                    out = [0.0, 0.0, 0.0]
                    out[axis] = a
                    return out
                heart["rotation"] = {
                    key(0): {"vector": r(0.0), "easing": "linear"},
                    key(t0): {"vector": r(0.0), "easing": "linear"},
                    key(tw): {"vector": r(-TILT), "easing": "easeInQuad"},
                    key(ti): {"vector": r(TILT * 1.2), "easing": "easeOutBack"},
                    key(t1): {"vector": r(-TILT * 0.3), "easing": "easeInOutSine"},
                    key(t2): {"vector": r(0.0), "easing": "easeOutSine"},
                    key(L): {"vector": r(0.0), "easing": "linear"},
                }
            added.append("%s@%.2f" % (mv, ti))
        path.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
        done[h] = added
        print(h, added)
    MARK.write_text(json.dumps(done, ensure_ascii=False, indent=1), encoding="utf-8")


main()
