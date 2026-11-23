# -*- coding: utf-8 -*-
"""淵の王 5 体の技の動き (windup_<技> と strike_<技>) を、手で決めた姿と王ごとの癖から作る (2026-11-11 の声「予備動作 → 溜め → 一気に振る → 余韻」)。

技ごとに三つの姿を決める (王みな同じ骨組み: body・chest・head・arm_l/r・fore_l/r・leg_l/r・robe_f/b)。
  A 予備動作 (溜めと逆へ少し)、W 溜めきった姿、C 当たりの姿
溜め (2.0 秒。ゲームでは予告の長さに合わせて速さを変える):
  0 → A (ゆっくり) → W (大きく) → 溜めの終わりは W より少し深く (王の癖: 震え・軋み・息)
当たり (1.2 秒。BossLook.STRIKE_TICKS = 24。溜めから当たりへは混ぜずに切り替える GuardMob.bossState):
  W → C を一気に (2 tick ほど) → 当たりで止まる (ヒットストップ) → C の先まで流れる (余韻) → 戻る
重なり: 体の先ほど遅れて動く (胴 → 腕 → 前腕・頭)。遅れの大きさは王の癖。

王の癖:
  骨  獣: 頭が遅れて噛みつく (当たりで頭が前へ跳ねる)。余韻は重い
  塩  結晶: 溜めは段を付けて軋みながら (止まる・進むをくり返す)。当たりはぴたりと止まり、余韻はほぼない。最後に細かく震える
  雷  電気: 溜めの終わりは震え続け、当たりはいちばん速い。余韻にびくっと二度引きつる
  灰  炉: 予備動作で深く沈み、溜めは息をするように胸が上下。当たりは重く遅めで、体が沈んで二度弾む
  影  影: 関節が順に遅れてしなる (重なりが大きい)。余韻は大きく行き過ぎ、横へ揺れながら戻る

    python -X utf8 -I tools/gen/anim/king_moves.py
"""
from __future__ import annotations

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ANIM = ROOT / "src/main/resources/assets/skyabyss/animations/entity"

MOVES = ("slam", "charge", "summon", "bolts", "sweep")
BONES = ("body", "chest", "head", "arm_l", "arm_r", "fore_l", "fore_r", "leg_l", "leg_r", "robe_f", "robe_b")


def P(**kw):
    """姿: 骨名 → 回転 [x,y,z]。body_pos で体の位置。"""
    return kw


# ---------------------------------------------------------------- 技ごとの三つの姿 (左右の z は鏡)
# 腕の x の回り: + = 前・上へ (168 で真上)、- = 後ろへ。当たりの姿の腕は前へ振り抜く (2026-11-11 の点検で、突進・叩きつけ・投げの腕が後ろへ振れていたのを直した)
def arms(x, y, z, fx=0.0):
    return dict(arm_l=[x, y, -z], arm_r=[x, -y, z], fore_l=[fx, 0, 0], fore_r=[fx, 0, 0])


def legs(x, z=0.0):
    return dict(leg_l=[x, 0, -z], leg_r=[x, 0, z])


POSES = {
    # 両腕を振り上げ、地面へ叩きつける
    "slam": dict(
        A=P(chest=[10, 0, 0], head=[8, 0, 0], **arms(-28, 0, 6, 10), **legs(8, 2), body_pos=[0, -1.2, 0]),
        W=P(chest=[-24, 0, 0], head=[-18, 0, 0], **arms(168, 0, 12, 28), **legs(12, 6), robe_f=[-8, 0, 0], robe_b=[10, 0, 0], body_pos=[0, -1.0, 0.6]),
        C=P(chest=[42, 0, 0], head=[20, 0, 0], **arms(26, 0, 6, -12), **legs(32, 8), robe_f=[36, 0, 0], robe_b=[-36, 0, 0], body_pos=[0, -5.5, -2.2]),
    ),
    # 身を沈めて引き、前へ突っ込む
    "charge": dict(
        A=P(chest=[-8, 0, 0], head=[-6, 0, 0], **arms(20, 0, 10, 10), body_pos=[0, 0.6, 0.8]),
        W=P(chest=[18, 0, 0], head=[-10, 0, 0], **arms(-50, 0, 34, 40), leg_l=[-20, 0, -4], leg_r=[30, 0, 4], robe_f=[10, 0, 0], robe_b=[24, 0, 0],
            body_pos=[0, -3.0, 2.6]),
        C=P(chest=[34, 0, 0], head=[16, 0, 0], **arms(54, 0, 8, -10), leg_l=[-42, 0, -4], leg_r=[38, 0, 4], robe_f=[-20, 0, 0], robe_b=[44, 0, 0],
            body_pos=[0, -1.2, -7.0]),
    ),
    # 腕を胸の前で組んでから大きく広げて天を仰ぎ、地へ振り下ろして呼ぶ
    "summon": dict(
        A=P(chest=[14, 0, 0], head=[12, 0, 0], **arms(44, 0, -32, 60), body_pos=[0, -1.0, 0]),
        W=P(chest=[-22, 0, 0], head=[-30, 0, 0], **arms(128, 0, 58, 10), **legs(6, 8), robe_f=[-6, 0, -6], robe_b=[8, 0, 6], body_pos=[0, 1.2, 0]),
        C=P(chest=[28, 0, 0], head=[22, 0, 0], **arms(26, 0, 74, -10), **legs(26, 10), robe_f=[24, 0, 0], robe_b=[-24, 0, 0], body_pos=[0, -4.0, -0.8]),
    ),
    # 右腕を後ろへ大きく引き、身をひねって投げる
    "bolts": dict(
        A=P(chest=[6, -12, 0], head=[0, 8, 0], arm_l=[20, 0, -10], arm_r=[-30, 0, -14], fore_r=[30, 0, 0], body_pos=[0, -0.6, 0]),
        W=P(chest=[-14, 38, 0], head=[-6, -24, 0], arm_l=[-70, 0, -14], arm_r=[172, 0, 38], fore_l=[-10, 0, 0], fore_r=[48, 0, 0], leg_l=[-14, 0, 0],
            leg_r=[16, 0, 4], robe_f=[0, 0, 8], robe_b=[0, 0, 8], body_pos=[0, -1.2, 1.2]),
        C=P(chest=[22, -40, 0], head=[10, 12, 0], arm_l=[-34, 0, -30], arm_r=[64, 0, 4], fore_l=[20, 0, 0], fore_r=[-8, 0, 0], leg_l=[-26, 0, 0],
            leg_r=[24, 0, 4], robe_f=[0, 0, -18], robe_b=[0, 0, -18], body_pos=[0, -2.2, -2.6]),
    ),
    # 両腕を片側へ大きくひねり、反対へ薙ぎ払う
    "sweep": dict(
        A=P(chest=[4, -16, 0], arm_l=[30, 0, -30], arm_r=[30, 0, 20], body_pos=[-0.6, -0.4, 0]),
        W=P(chest=[-6, 62, 6], head=[0, -30, 0], arm_l=[70, 0, 18], arm_r=[86, 0, 68], fore_l=[20, 0, 0], fore_r=[34, 0, 0], **legs(10, 4),
            robe_f=[0, 0, 14], robe_b=[0, 0, 14], body_pos=[1.4, -1.6, 0]),
        C=P(chest=[10, -70, -8], head=[4, 26, 0], arm_l=[78, 0, -70], arm_r=[70, 0, -18], fore_l=[-14, 0, 0], fore_r=[-6, 0, 0], **legs(14, 6),
            robe_f=[0, 0, -30], robe_b=[0, 0, -30], body_pos=[-1.8, -2.0, -1.0]),
    ),
}

# ---------------------------------------------------------------- 王の癖
STYLES = {
    "bone": dict(amp=1.0, ta=0.32, tw=1.25, deep=1.10, snap=0.07, stop=0.10, follow=1.22, settle_over=-0.10, lag=0.035, tremble=0.0, steps=0,
                 breath=0.0, bounce=0, twitch=0, sway=0.0, head_bite=26.0),
    "salt": dict(amp=0.9, ta=0.28, tw=1.35, deep=1.06, snap=0.05, stop=0.16, follow=1.02, settle_over=0.0, lag=0.0, tremble=0.0, steps=5,
                 breath=0.0, bounce=0, twitch=0, sway=0.0, head_bite=0.0, shiver=1.6),
    "storm": dict(amp=1.0, ta=0.22, tw=1.05, deep=1.12, snap=0.04, stop=0.08, follow=1.18, settle_over=-0.06, lag=0.015, tremble=3.2, steps=0,
                  breath=0.0, bounce=0, twitch=2, sway=0.0, head_bite=0.0),
    "ash": dict(amp=1.08, ta=0.48, tw=1.4, deep=1.08, snap=0.10, stop=0.12, follow=1.12, settle_over=-0.04, lag=0.03, tremble=0.0, steps=0,
                breath=1.0, bounce=2, twitch=0, sway=0.0, head_bite=0.0),
    "shadow": dict(amp=1.05, ta=0.36, tw=1.2, deep=1.14, snap=0.07, stop=0.06, follow=1.38, settle_over=-0.16, lag=0.07, tremble=0.0, steps=0,
                   breath=0.0, bounce=0, twitch=0, sway=10.0, head_bite=0.0, head_tilt=22.0),
}

# 体の先ほど遅れる (重なり): 遅れの段
DEPTH = {"body": 0, "chest": 1, "leg_l": 1, "leg_r": 1, "robe_f": 2, "robe_b": 2, "arm_l": 2, "arm_r": 2, "head": 2, "fore_l": 3, "fore_r": 3}

EASE_IN = "easeInQuad"
EASE_OUT = "easeOutQuad"
EASE_IO = "easeInOutSine"


def val(pose, bone, key="rot"):
    if key == "pos":
        return list(pose.get("body_pos", [0, 0, 0])) if bone == "body" else [0, 0, 0]
    return list(pose.get(bone, [0, 0, 0]))


def mix(a, b, t):
    return [x + (y - x) * t for x, y in zip(a, b)]


def scl(a, k):
    return [x * k for x in a]


def r2(v):
    return [round(float(x), 2) for x in v]


def track(keys, length):
    """keys = [(time, vec, easing)] → GeckoLib の keyframes。時は 0..length に収め、同じ時は後を残す。"""
    out = {}
    for t, v, e in keys:
        t = min(max(0.0, t), length)
        out[f"{t:.4f}"] = {"vector": r2(v), "easing": e}
    return dict(sorted(out.items(), key=lambda kv: float(kv[0])))


def windup(king, move):
    st = STYLES[king]
    ps = POSES[move]
    L = 2.0
    bones = {}
    for bone in BONES + ("body_pos",):
        key = "pos" if bone == "body_pos" else "rot"
        bn = "body" if bone == "body_pos" else bone
        A = scl(val(ps["A"], bn, key), st["amp"])
        W = scl(val(ps["W"], bn, key), st["amp"])
        D = scl(W, st["deep"])
        if not any(A) and not any(W):
            continue
        lag = st["lag"] * DEPTH.get(bn, 0)
        keys = [(0.0, [0, 0, 0], EASE_IO), (st["ta"] + lag, A, EASE_IO)]
        if st["steps"]:
            # 段を付けて軋む: 進む (速く) と止まる をくり返す
            n = st["steps"]
            t0, t1 = st["ta"] + 0.1, st["tw"]
            for i in range(n):
                f = (i + 1) / n
                ta = t0 + (t1 - t0) * (i + 0.35) / n
                tb = t0 + (t1 - t0) * (i + 1) / n
                keys.append((ta, mix(A, W, (i + 0.85) / n), EASE_IN))
                keys.append((tb, mix(A, W, f), "linear"))
        else:
            keys.append((st["tw"] + lag, W, "easeOutCubic"))
        if st["tremble"] and key == "rot" and bn in ("chest", "arm_l", "arm_r", "head"):
            # 溜めの終わりの震え
            t = st["tw"] + 0.1
            k = 0
            while t < L - 0.06:
                f = (t - st["tw"]) / (L - st["tw"])
                j = st["tremble"] * (1 if k % 2 else -1) * (0.4 + 0.6 * f)
                keys.append((t, mix(W, D, f) if True else W, "linear"))
                keys[-1] = (t, [a + j * (0.6 if i == 1 else 1) for i, a in enumerate(mix(W, D, f))], "linear")
                t += 0.06
                k += 1
        if st["breath"] and bn == "chest":
            for i in range(2):
                t = st["tw"] + 0.1 + i * 0.25
                keys.append((t, [a - 5 for a in mix(W, D, 0.3 + 0.3 * i)], EASE_IO))
                keys.append((t + 0.12, mix(W, D, 0.45 + 0.3 * i), EASE_IO))
        if st["breath"] and key == "pos":
            for i in range(2):
                t = st["tw"] + 0.1 + i * 0.25
                keys.append((t, [D[0], D[1] + 0.8, D[2]], EASE_IO))
                keys.append((t + 0.12, D, EASE_IO))
        if st.get("head_tilt") and bn == "head":
            keys.append((st["tw"] + 0.2, [W[0], W[1], W[2] + st["head_tilt"]], EASE_IO))
            D = [D[0], D[1], D[2] + st["head_tilt"]]
        keys.append((L, D, EASE_IO))
        bones.setdefault(bn, {})["position" if key == "pos" else "rotation"] = track(keys, L)
    return {"loop": "hold_on_last_frame", "animation_length": L, "bones": bones}


def strike(king, move):
    st = STYLES[king]
    ps = POSES[move]
    L = 1.2
    bones = {}
    for bone in BONES + ("body_pos",):
        key = "pos" if bone == "body_pos" else "rot"
        bn = "body" if bone == "body_pos" else bone
        W = scl(val(ps["W"], bn, key), st["amp"])
        D = scl(W, st["deep"])
        C = scl(val(ps["C"], bn, key), st["amp"])
        if st.get("head_tilt") and bn == "head":
            D = [D[0], D[1], D[2] + st["head_tilt"]]
        if not any(W) and not any(C):
            continue
        lag = st["lag"] * DEPTH.get(bn, 0)
        snap = st["snap"] + lag
        Cx = scl(C, 1.12)
        if bn == "head" and st["head_bite"]:
            Cx = [Cx[0] + st["head_bite"], Cx[1], Cx[2]]
            snap += 0.04
        stop = snap + st["stop"]
        F = mix(C, scl(C, st["follow"]), 1.0)
        keys = [(0.0, D, EASE_IO), (snap, Cx, EASE_IN), (stop, mix(Cx, C, 0.25), "linear")]
        if bn == "head" and st["head_bite"]:
            # 噛みついて戻る
            keys.append((stop + 0.06, [C[0] - 6, C[1], C[2]], EASE_OUT))
        if st["bounce"] and key == "pos":
            keys.append((stop + 0.08, [C[0], C[1] + 1.6, C[2]], EASE_OUT))
            keys.append((stop + 0.18, [C[0], C[1] - 0.8, C[2]], EASE_IO))
            keys.append((stop + 0.26, [C[0], C[1] + 0.5, C[2]], EASE_IO))
        if st["twitch"] and key == "rot" and bn in ("chest", "arm_l", "arm_r", "head"):
            for i in range(st["twitch"]):
                t = stop + 0.08 + i * 0.12
                keys.append((t, [a + (6 if i % 2 == 0 else -5) for a in mix(C, F, 0.4 + 0.2 * i)], "linear"))
                keys.append((t + 0.04, mix(C, F, 0.45 + 0.2 * i), "linear"))
        if st.get("shiver") and key == "rot" and bn in ("chest", "head", "arm_l", "arm_r"):
            for i in range(4):
                keys.append((stop + 0.04 + i * 0.04, [a + st["shiver"] * (1 if i % 2 else -1) for a in C], "linear"))
        follow_t = max(stop + 0.22, 0.55 + lag)
        if st["bounce"] or st["twitch"]:
            follow_t = max(follow_t, stop + 0.36)
        keys.append((follow_t, F, EASE_OUT))
        if st["sway"] and key == "rot" and bn in ("chest", "head", "robe_f", "robe_b"):
            keys.append((follow_t + 0.18, [F[0] * 0.5, F[1] * 0.5, F[2] * 0.5 + st["sway"]], EASE_IO))
            keys.append((follow_t + 0.36, [F[0] * 0.2, F[1] * 0.2, F[2] * 0.2 - st["sway"] * 0.6], EASE_IO))
        elif st["settle_over"]:
            keys.append((follow_t + 0.2, mix(F, C, 0.5), EASE_IO))
            keys.append((min(L - 0.12, follow_t + 0.42), scl(C, st["settle_over"]), EASE_IO))
        keys.append((L, [0, 0, 0], EASE_IO))
        # 時の並びが崩れたら (遅れが大きい骨) 後ろを詰める
        bones.setdefault(bn, {})["position" if key == "pos" else "rotation"] = track(keys, L)
    return {"animation_length": L, "bones": bones}


def main():
    log = {}
    for king in STYLES:
        p = ANIM / f"king_{king}.animation.json"
        data = json.loads(p.read_text(encoding="utf-8"))
        an = data["animations"]
        for move in MOVES:
            an[f"animation.king_{king}.windup_{move}"] = windup(king, move)
            an[f"animation.king_{king}.strike_{move}"] = strike(king, move)
        p.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
        log[king] = [f"windup_{m}/strike_{m}" for m in MOVES]
        print(king, "ok")


if __name__ == "__main__":
    main()
