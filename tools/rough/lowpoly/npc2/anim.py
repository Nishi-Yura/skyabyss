"""NPC v2 の動き (idle / walk / talk)。人の持ち方 (cast の anim) ごとに組む。GeckoLib の animation.json (molang の式)。

骨: body (上下・ひねり)、armL/R (肩)、foreL/R (肘)、legL/R、hem (裾の揺れ)、hairB (後ろ髪の揺れ)。頭の向きは描画のコード (相手を見る・うなずく)。
向き: 係の人と同じ (root は回さない)。腕の X の正 = 前へ上げる。
型:
  free       両手が空く。話すとき右手で手振り (肘を曲げて手のひらを見せる)、左手が少し添える
  hold_l     左手に持ち物。左腕は揺れを小さく、話すときは右手で手振り
  hold_r     右手に持ち物 (杖・道具)。右腕は小さく、話すときは左手で手振り
  hold2      両手で抱える。腕は抱えたまま、話すときは体と肩でうなずく
  shoulder_r 右肩にかつぐ。右腕は止め、左手で手振り
  ground_r   右手で地に立てた三脚を支える。右腕は止め、左手 (画板) を少し上げて見せる
"""
from __future__ import annotations

import json


def s(expr):
    return str(expr)


def sin(f, a, ph=0.0, base=0.0):
    """base + sin(t * f + ph) * a (f は 1 秒あたりの度)。"""
    b = f"{base} + " if base else ""
    p = f" + {ph}" if ph else ""
    return f"{b}math.sin(query.anim_time * {f}{p}) * {a}"


def rot(x="0", y="0", z="0"):
    return {"rotation": [s(x), s(y), s(z)]}


def keys(frames):
    """{時刻: [x, y, z]} のキーフレーム。GeckoLib は線形でつなぐので、間を滑らかな曲線 (catmull-rom) で 0.1 秒ごとに埋める。"""
    import numpy as np
    T = [t for t, _ in frames]
    P = [np.array(v, float) for _, v in frames]
    out = {}
    for i in range(len(frames) - 1):
        p0, p1, p2, p3 = P[max(i - 1, 0)], P[i], P[i + 1], P[min(i + 2, len(P) - 1)]
        n = max(1, int(round((T[i + 1] - T[i]) / 0.1)))
        for k in range(n):
            u = k / n
            v = 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3)
            out[f"{T[i] + (T[i + 1] - T[i]) * u:.2f}"] = [round(float(x), 2) for x in v]
    out[f"{T[-1]:.2f}"] = [float(x) for x in P[-1]]
    return out


def idle(kind):
    f = 90  # 4 秒で一回り
    B = {
        "body": {"position": ["0", sin(f, 0.18), "0"], "rotation": [sin(f, 0.5), "0", sin(f / 2, 0.6)]},
        "hem": rot(sin(f, 1.2, 60)),
        "hairB": rot(sin(f, 2.0, 120)),
    }
    free_arm = lambda ph: rot(sin(f, 1.6, ph), "0", sin(f, 0.8, ph))  # noqa: E731
    still = lambda ph: rot(sin(f, 0.6, ph))  # noqa: E731
    B["armR"] = still(90) if kind in ("hold_r", "hold2", "shoulder_r", "ground_r") else free_arm(90)
    B["armL"] = still(30) if kind in ("hold_l", "hold2") else free_arm(30)
    B["foreR"] = rot(sin(f, 1.2, 140, 4.0)) if kind in ("free", "hold_l") else rot(sin(f, 0.5, 140))
    B["foreL"] = rot(sin(f, 1.2, 80, 4.0)) if kind in ("free", "hold_r", "shoulder_r") else rot(sin(f, 0.5, 80))
    return {"loop": True, "animation_length": 4.0, "bones": B}


def walk(kind):
    f = 360
    B = {
        "legL": rot(sin(f, 22)),
        "legR": rot(sin(f, -22)),
        "body": {"position": ["0", f"math.abs(math.sin(query.anim_time * {f})) * 0.45", "0"], "rotation": ["2", sin(f, 2.0), "0"]},
        "hem": rot(sin(f * 2, 2.5, 30, 2.0)),
        "hairB": rot(sin(f * 2, 3.0, 60, 4.0)),
    }
    swing = lambda a, base=0.0: rot(sin(f, a, 0, base))  # noqa: E731
    if kind in ("free", "hold_l"):
        B["armR"] = swing(16)
        B["foreR"] = rot(sin(f, 8, 30, 12))
    else:
        B["armR"] = swing(4) if kind in ("hold_r", "ground_r") else rot(sin(f * 2, 1.0))
        B["foreR"] = rot("0")
    if kind in ("free", "hold_r", "shoulder_r", "ground_r"):
        B["armL"] = swing(-16)
        B["foreL"] = rot(sin(f, -8, 30, 12))
    else:
        B["armL"] = rot(sin(f * 2, 1.0))
        B["foreL"] = rot("0")
    return {"loop": True, "animation_length": 1.0, "bones": B}


def gesture(side_bone, fore_bone, sign=1):
    """話すときの手振り (3 秒): 上げる → 手のひらを見せて二度振る → 少し下げてもう一度 → 戻す。"""
    up = keys([(0.0, (6, 0, 0)), (0.35, (34, 0, 6 * sign)), (0.9, (30, 0, 10 * sign)), (1.4, (36, 0, 4 * sign)), (2.0, (24, 0, 8 * sign)),
               (2.5, (30, 0, 6 * sign)), (3.0, (6, 0, 0))])
    fo = keys([(0.0, (8, 0, 0)), (0.35, (52, 0, 0)), (0.7, (40, 0, -8 * sign)), (1.05, (54, 0, 6 * sign)), (1.4, (44, 0, -6 * sign)),
               (2.0, (36, 0, 0)), (2.5, (48, 0, 4 * sign)), (3.0, (8, 0, 0))])
    return {side_bone: {"rotation": up}, fore_bone: {"rotation": fo}}


def talk(kind):
    f = 120
    B = {
        "body": {"position": ["0", sin(f, 0.15), "0"], "rotation": [sin(f, 1.4, 0, 1.5), sin(f / 2, 1.5), "0"]},
        "hem": rot(sin(f, 1.0, 40)),
        "hairB": rot(sin(f, 1.6, 90)),
    }
    if kind in ("free", "hold_l"):
        B.update(gesture("armR", "foreR", 1))
        if kind == "free":
            B["armL"] = rot(sin(f, 2.0, 90, 8))
            B["foreL"] = rot(sin(f, 3.0, 120, 14))
        else:
            B["armL"] = rot(sin(f, 1.0, 90))
            B["foreL"] = rot(sin(f, 1.0, 120))
    elif kind in ("hold_r", "shoulder_r", "ground_r"):
        B.update(gesture("armL", "foreL", -1))
        B["armR"] = rot(sin(f, 1.0, 90))
        B["foreR"] = rot(sin(f, 0.8, 120))
        if kind == "ground_r":
            B["armL"] = {"rotation": keys([(0.0, (0, 0, 0)), (0.6, (14, 0, 0)), (1.5, (10, 0, 0)), (2.2, (16, 0, 0)), (3.0, (0, 0, 0))])}
            B["foreL"] = {"rotation": keys([(0.0, (0, 0, 0)), (0.6, (12, 0, 0)), (1.5, (8, 0, 0)), (2.2, (14, 0, 0)), (3.0, (0, 0, 0))])}
    else:  # hold2: 抱えたまま、肩ごと小さく上下させて話す
        B["armR"] = rot(sin(f, 3.0, 0, 2))
        B["armL"] = rot(sin(f, 3.0, 0, 2))
        B["foreR"] = rot(sin(f, 2.0, 30))
        B["foreL"] = rot(sin(f, 2.0, 30))
        B["body"]["rotation"] = [sin(f, 2.4, 0, 2.5), sin(f / 2, 2.0), "0"]
    return {"loop": True, "animation_length": 3.0, "bones": B}


def animation_file(prefix, kind):
    """prefix = animation.<実体の名前> (attendant / town_guest)。"""
    return {"format_version": "1.8.0", "animations": {
        f"{prefix}.idle": idle(kind), f"{prefix}.walk": walk(kind), f"{prefix}.talk": talk(kind)}}


def sample(kind, name, t):
    """見本の絵用: 時刻 t の骨の回転 (度) を式とキーフレームから求める (molang の sin とキーの線形補間だけ)。"""
    import math
    clip = {"idle": idle, "walk": walk, "talk": talk}[name](kind)
    out = {}
    for bone, ch in clip["bones"].items():
        r = ch.get("rotation")
        if r is None:
            continue
        if isinstance(r, dict):
            ts = sorted(r, key=float)
            tt = t % clip["animation_length"]
            v = None
            for a, b in zip(ts[:-1], ts[1:]):
                if float(a) <= tt <= float(b):
                    u = (tt - float(a)) / max(float(b) - float(a), 1e-9)
                    va, vb = r[a], r[b]
                    v = [va[i] * (1 - u) + vb[i] * u for i in range(3)]
            out[bone] = tuple(v or r[ts[-1]])
        else:
            vals = []
            for e in r:
                e = e.replace("math.sin", "SIN").replace("math.abs", "abs").replace("query.anim_time", str(t))
                vals.append(eval(e, {"SIN": lambda d: math.sin(math.radians(d)), "abs": abs}))
            out[bone] = tuple(vals)
    return out


if __name__ == "__main__":
    print(json.dumps(animation_file("animation.attendant", "free"), indent=1)[:2000])
