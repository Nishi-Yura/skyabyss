"""四つの心臓 (岩鯨・潮殻・雷翼・余燼) の「登場 (intro)」と「倒れる (death)」のアニメーションを、各 animation.json に足す。

使い方 (リポジトリ直下で): python tools/install/install_heart_cinematics.py
入出力: src/main/resources/assets/skyabyss/animations/entity/{stone_whale_heart,tide_heart,storm_heart,ember_heart}.animation.json
  animation.<名前>.intro  (4.0 秒。長さは HeartBoss.INTRO_TICKS = 80 tick と合わせる)
  animation.<名前>.death  (5.0 秒。最後の姿で止まる。HeartBoss.DEATH_TICKS = 100 tick と合わせる)
既にある同じ名前のアニメーションは置き換える (何度流しても同じ結果)。ほかのアニメーションには触れない。
install_*_heart.py を書き出し直したときは、このスクリプトをもう一度流す (書き出し直すと消えるため)。

向きの変換はほかの心臓のスクリプトと同じ (ラフの空間で書き、install_heart_v4.track でゲームの向きに直す)。
段階 1 の模型の骨で「登場」を、全段階の骨の和集合で「倒れる」を書く (登場は段階 1 でしか流れない。倒れるは段階を選ばない)。

演出の方針 (材質の言葉に合わせる):
  岩鯨 = 石の殻が床から迫り上がり、割れて外へ剥がれ落ち、核だけが残って砕ける
  潮殻 = 巻貝がほどけて開き、真珠が浮かんで泡になる
  雷翼 = 雷が落ちて翼が開き、翼がだらりと落ちて核が過負荷で弾ける
  余燼 = 灰の塊が鼓動しながら熱を帯びて割れ、冷えて灰になる
"""
from __future__ import annotations

import json
import zlib
import math
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools/rough"))
import install_heart_v4 as H  # noqa: E402  (track, out_dir を使う)

ASSET = ROOT / "src/main/resources/assets/skyabyss/animations/entity"
INTRO_LEN = 4.0
DEATH_LEN = 5.0
DT = 0.1
Z3 = [0.0, 0.0, 0.0]
TINY = 0.01


def clamp01(x):
    return max(0.0, min(1.0, x))


def ease_out_cubic(x):
    return 1 - (1 - x) ** 3


def ease_in_quad(x):
    return x * x


def ease_in_out(x):
    return x * x * (3 - 2 * x)


def ease_out_back(x):
    c1 = 1.70158
    c3 = c1 + 1
    return 1 + c3 * (x - 1) ** 3 + c1 * (x - 1) ** 2


def seg(t, t0, t1):
    """t0 から t1 のあいだに 0 から 1 へ進む割合"""
    return clamp01((t - t0) / (t1 - t0))


def S(s):
    return [s, s, s]


def sample(f, length, dt=DT):
    n = int(round(length / dt))
    return [(round(k * dt, 3), f(k * dt)) for k in range(n + 1)]


def seedof(name):
    return zlib.crc32(name.encode())


def shake(t, amp, seed=0.0, freq=37.0):
    """ふるえの量 (x, z)"""
    return [amp * math.sin(t * freq + seed), 0.0, amp * math.cos(t * freq * 1.3 + seed * 2)]


def add(a, b):
    return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]


def put(bones, name, kind, f, length):
    bones.setdefault(name, {})[kind] = H.track(sample(f, length), kind)


def anim(length, bones, loop_hold=False):
    d = {"animation_length": length, "bones": bones}
    if loop_hold:
        d["loop"] = "hold_on_last_frame"
    return d


# ================================================================ 岩鯨の心臓
SW_ROOTS = ["seams", "crown", "base", "tendrils", "heart", "bowl", "chains", "core", "crack_3", "crack_7", "cracks", "heart_veins",
            "shard", "spikes"] + [f"plate_{i}" for i in range(8)]
SW_ALL = SW_ROOTS + [f"seam_{i}" for i in range(8)] + [f"crack_{i}" for i in (0, 1, 2)]
SW_PLATE_DIR = {f"plate_{i}": H.out_dir(i).tolist() for i in range(8)}
SW_CORE_GROUP = ("core", "heart", "heart_veins", "base", "tendrils", "cracks", "crack_3", "crack_7", "seams", "bowl")
SW_TOP_GROUP = ("crown", "spikes", "chains", "shard")


def stone_whale():
    bury = -34.0                                   # 模型の高さ 25 単位 + 床までの 1 ブロック (6.4 単位) より深く

    # ---- 登場: 床の下から石の殻が迫り上がり、板が音を立てて閉じる
    def rise(t, t0, t1):
        x = seg(t, t0, t1)
        return ease_out_back(x) if x < 1 else 1.0

    bones = {}
    for name in SW_ROOTS:
        if name.startswith("plate_"):
            i = int(name.split("_")[1])
            t0, t1 = 1.7 + 0.07 * i, 2.75 + 0.07 * i
        elif name in SW_CORE_GROUP:
            t0, t1 = 1.2, 2.5
        else:
            t0, t1 = 2.3, 3.4

        def pos(t, t0=t0, t1=t1, name=name):
            r = rise(t, t0, t1)
            rising = 0.0 < seg(t, t0, t1) < 1.0
            sh = shake(t, 0.35, seedof(name) % 7) if rising else Z3
            return [sh[0], bury * (1 - r), sh[2]]

        put(bones, name, "position", pos, INTRO_LEN)
    for name in [f"plate_{i}" for i in range(8)]:
        i = int(name.split("_")[1])
        t0 = 1.7 + 0.07 * i
        put(bones, name, "rotation",
            lambda t, t0=t0, i=i: [0, 0, 7 * math.sin(t * 19 + i) * (1 - seg(t, t0, t0 + 1.1)) * (1 if t > t0 else 0)], INTRO_LEN)
    # 殻が閉じ終わるときの脈
    put(bones, "heart", "scale", lambda t: S(1 + 0.16 * math.sin(math.pi * seg(t, 3.3, 3.9)) ** 2), INTRO_LEN)
    put(bones, "core", "scale", lambda t: S(1 + 0.6 * math.sin(math.pi * seg(t, 3.3, 4.0)) ** 2), INTRO_LEN)
    A_intro = anim(INTRO_LEN, bones)

    # ---- 倒れる: 殻が外へ剥がれて落ち、鎖が切れ、むき出しの核がふるえて砕ける
    bones = {}
    for name in SW_ALL:
        sh_amp = 0.5 if name in SW_ROOTS else 0.0
        if name.startswith("plate_"):
            i = int(name.split("_")[1])
            t0 = 0.7 + 0.12 * i
            d = SW_PLATE_DIR[name]

            def pos(t, t0=t0, d=d, name=name):
                x = seg(t, t0, t0 + 1.6)
                r = 30.0 * ease_in_quad(x)
                base = shake(t, 0.5 * (1 - x), seedof(name) % 7)
                return [base[0] + d[0] * r, -3.0 * x - 60.0 * x * x, base[2] + d[2] * r]

            put(bones, name, "position", pos, DEATH_LEN)
            put(bones, name, "rotation", lambda t, t0=t0: [0, 50 * ease_in_quad(seg(t, t0, t0 + 1.6)), 35 * ease_in_quad(seg(t, t0, t0 + 1.6))], DEATH_LEN)
            continue
        if name in ("seams", "cracks") or name.startswith("seam_") or name.startswith("crack_"):
            t0, dur = 1.4, 1.6
        elif name == "chains":
            t0, dur = 1.2, 1.8
        elif name in SW_TOP_GROUP or name == "bowl":
            t0, dur = 2.0, 1.6
        else:
            t0, dur = 99, 1

        def pos(t, t0=t0, dur=dur, name=name, amp=sh_amp):
            x = seg(t, t0, t0 + dur)
            sh = shake(t, amp * (1 - x), seedof(name) % 7) if t < t0 else Z3
            kick = 8.0 * math.sin(math.pi * seg(t, 1.2, 1.5)) if name == "chains" else 0.0
            return [sh[0], kick - 60.0 * x * x, sh[2]]

        put(bones, name, "position", pos, DEATH_LEN)
    # むき出しの核: ふるえ → 光の点滅 → 縮んで消える
    def core_pos(t):
        amp = 0.9 * seg(t, 0.0, 0.6) * (1 - 0.6 * seg(t, 3.0, 4.4)) * (1 - seg(t, 4.4, 4.9))
        return shake(t, amp, 1.3, 53.0)

    for name in ("heart", "core", "heart_veins", "base", "tendrils"):
        put(bones, name, "position", core_pos, DEATH_LEN)

    def collapse(t):
        return 1 - 0.99 * ease_in_quad(seg(t, 4.3, 4.9))

    put(bones, "core", "scale",
        lambda t: S((1 + 0.5 * seg(t, 0, 0.7) + 0.35 * math.sin(t * 22) * seg(t, 3.0, 4.3)) * collapse(t) * (1 + 1.4 * math.sin(math.pi * seg(t, 4.3, 4.6)))),
        DEATH_LEN)
    put(bones, "heart", "scale", lambda t: S((1 + 0.12 * math.sin(t * 9) * seg(t, 0.3, 3.0)) * collapse(t)), DEATH_LEN)
    put(bones, "heart_veins", "scale", lambda t: S((1 + 0.3 * seg(t, 0, 0.8) + 0.4 * math.sin(t * 17) * seg(t, 3.0, 4.3)) * collapse(t)), DEATH_LEN)
    for name in ("base", "tendrils"):
        put(bones, name, "scale", lambda t: S(collapse(t)), DEATH_LEN)
    A_death = anim(DEATH_LEN, bones, loop_hold=True)
    return A_intro, A_death


# ================================================================ 潮殻の心臓
TD_ALL = ["heart", "heart_vein", "vessel", "core_ring", "core_eye", "spiral", "aperture_lip", "barnacle", "frond", "chamber",
          "moon_crescent", "orbit", "shard", "water_drop"]


def tide_shell():
    # ---- 登場: 水の底から核が浮かび、巻貝がほどけて開き、真珠のまわりを水滴の輪が回りはじめる
    when = {  # 名前: (現れはじめ, 現れ終わり)
        "core_eye": (0.2, 1.0), "core_ring": (0.3, 1.2), "heart": (0.4, 1.4), "heart_vein": (0.5, 1.5),
        "vessel": (0.9, 2.3), "spiral": (1.0, 2.6), "aperture_lip": (1.9, 2.8), "barnacle": (2.0, 2.9), "frond": (2.0, 3.1),
        "chamber": (2.1, 3.0), "orbit": (2.4, 3.4), "water_drop": (2.5, 3.5), "moon_crescent": (2.6, 3.5), "shard": (2.7, 3.5)}
    bones = {}
    for name in TD_ALL:
        t0, t1 = when[name]

        def sc(t, t0=t0, t1=t1, name=name):
            x = seg(t, t0, t1)
            s = max(TINY, ease_out_back(x) if x < 1 else 1.0) if x > 0 else TINY
            if name == "heart" and t > 3.3:
                s *= 1 + 0.12 * math.sin(math.pi * seg(t, 3.3, 3.9)) ** 2
            if name == "core_eye" and t > 3.3:
                s *= 1 + 0.6 * math.sin(math.pi * seg(t, 3.3, 4.0)) ** 2
            return S(s)

        put(bones, name, "scale", sc, INTRO_LEN)
        put(bones, name, "position", lambda t: [0.0, -10.0 * (1 - ease_out_cubic(seg(t, 0.0, 2.6))), 0.0], INTRO_LEN)
    for name, turns in (("spiral", 1.5), ("aperture_lip", 1.5), ("barnacle", 1.5), ("frond", 1.5), ("vessel", 1.0)):
        t0, t1 = when[name]
        put(bones, name, "rotation", lambda t, t0=t0, t1=t1, turns=turns: [0, 360 * turns * (1 - ease_out_cubic(seg(t, t0, t1 + 0.4))), 0], INTRO_LEN)
    for name, sign in (("orbit", -1), ("water_drop", 1), ("moon_crescent", 1)):
        t0, t1 = when[name]
        put(bones, name, "rotation", lambda t, t0=t0, t1=t1, sign=sign: [0, sign * 540 * (1 - ease_out_cubic(seg(t, t0, t1 + 0.5))), 0], INTRO_LEN)
    A_intro = anim(INTRO_LEN, bones)

    # ---- 倒れる: 殻が震えて開き、真珠が浮かび上がって脈を弱め、泡になって弾ける
    bones = {}
    shell = ("vessel", "spiral", "aperture_lip", "barnacle", "frond", "chamber")
    pearl = ("heart", "heart_vein", "core_ring", "core_eye")
    ring = ("orbit", "water_drop", "moon_crescent", "shard")
    for name in shell:
        t_gone = {"aperture_lip": 1.6, "barnacle": 1.8, "frond": 2.0, "chamber": 3.0, "vessel": 2.6, "spiral": 2.8}[name]

        def sc(t, name=name, t_gone=t_gone):
            x = seg(t, t_gone - 0.8, t_gone)
            return S(max(TINY, (1.0 + 0.15 * seg(t, 0.8, t_gone - 0.2)) * (1 - x)))

        put(bones, name, "scale", sc, DEATH_LEN)

        def pos(t, name=name, t_gone=t_gone):
            sh = shake(t, 0.6 * seg(t, 0, 0.5) * (1 - seg(t, 0.9, 1.4)), seedof(name) % 5)
            out = 7.0 * ease_in_quad(seg(t, 0.8, t_gone))
            return [sh[0] + out, -2.0 * seg(t, 0.8, t_gone), sh[2]]

        put(bones, name, "position", pos, DEATH_LEN)
        put(bones, name, "rotation", lambda t, t_gone=t_gone: [0, 0, 28 * ease_in_quad(seg(t, 0.8, t_gone))], DEATH_LEN)
    for name in pearl:
        def pos(t):
            return [0.0, 14.0 * ease_out_cubic(seg(t, 1.4, 3.2)), 0.0]

        put(bones, name, "position", pos, DEATH_LEN)

        def sc(t, name=name):
            pulse = 1 + (0.22 if name == "heart" else 0.0) * math.sin(t * 11) * seg(t, 3.2, 4.4)
            go = 1 - 0.99 * ease_in_quad(seg(t, 4.3, 4.9))
            eye = 1.8 * math.sin(math.pi * seg(t, 0.0, 0.8)) if name == "core_eye" else 0.0
            flash = 1 + 1.2 * math.sin(math.pi * seg(t, 4.3, 4.6))
            return S(max(TINY, (1 + 0.25 * seg(t, 1.4, 3.2) + eye * 0.4) * pulse * go * flash))

        put(bones, name, "scale", sc, DEATH_LEN)
    put(bones, "core_ring", "rotation", lambda t: [0, 720 * ease_in_quad(seg(t, 1.4, 3.6)), 0], DEATH_LEN)
    for name, sign in (("orbit", -1), ("water_drop", 1), ("moon_crescent", 1), ("shard", -1)):
        put(bones, name, "rotation", lambda t, sign=sign: [0, sign * 1080 * ease_in_quad(seg(t, 0.4, 3.6)), 0], DEATH_LEN)
        put(bones, name, "scale", lambda t: S(max(TINY, (1 + 0.8 * seg(t, 0.8, 3.0)) * (1 - seg(t, 3.0, 3.8)))), DEATH_LEN)
        put(bones, name, "position", lambda t: [0.0, -10.0 * ease_in_quad(seg(t, 2.6, 3.8)), 0.0], DEATH_LEN)
    A_death = anim(DEATH_LEN, bones, loop_hold=True)
    return A_intro, A_death


# ================================================================ 雷翼の心臓
SG_ALL = ["heart", "heart_vein", "vessel", "core", "core_eye", "wing_l", "wing_r", "crest_l", "crest_r", "finger", "joint", "shed", "web"]
SG_WINGS = ("wing_l", "wing_r")


def wing_rot(side, v):
    return [v[0], v[1] * side, v[2] * side]


def storm_wing():
    # ---- 登場: 小さな火花が明滅し、雷が落ちた瞬間に心臓が現れ、畳んだ翼が開く
    strike = 1.6
    bones = {}
    for name in SG_ALL:
        if name in ("core", "core_eye"):
            def sc(t, name=name):
                if t < strike:
                    return S(0.18 + 0.32 * abs(math.sin(t * (9 + 6 * seg(t, 0, strike))))) if name == "core_eye" else S(0.1)
                x = seg(t, strike, strike + 0.5)
                return S(1.0 + 0.8 * (1 - x) + (0.5 * math.sin(math.pi * seg(t, 3.3, 4.0)) ** 2 if name == "core_eye" else 0.0))
        elif name in ("heart", "heart_vein", "vessel"):
            def sc(t, name=name):
                if t < strike:
                    return S(TINY)
                x = seg(t, strike, strike + 0.45)
                return S(1.0 + 0.25 * (1 - x) + (0.14 * math.sin(math.pi * seg(t, 3.3, 3.9)) ** 2 if name == "heart" else 0.0))
        else:
            t0 = {"wing_l": 1.7, "wing_r": 1.7, "crest_l": 2.2, "crest_r": 2.2, "finger": 2.3, "joint": 2.4, "shed": 2.5, "web": 2.6}[name]

            def sc(t, t0=t0):
                if t < strike:
                    return S(TINY)
                x = seg(t, t0, t0 + 0.9)
                return S(max(TINY, ease_out_back(x) if x < 1 else 1.0))

        put(bones, name, "scale", sc, INTRO_LEN)
        put(bones, name, "position", lambda t: [0.0, 12.0 * (1 - ease_in_quad(seg(t, strike - 0.2, strike + 0.6))) if t > strike - 0.2 else 12.0, 0.0], INTRO_LEN)
    for name, side in (("wing_l", 1), ("wing_r", -1)):
        put(bones, name, "rotation",
            lambda t, side=side: wing_rot(side, [0, 80 * (1 - ease_out_back(seg(t, 1.8, 3.0))) if t < 3.0 else 0, -85 * (1 - ease_out_back(seg(t, 1.8, 3.0))) if t < 3.0 else 0]),
            INTRO_LEN)
    for name, side in (("crest_l", 1), ("crest_r", -1)):
        put(bones, name, "rotation", lambda t, side=side: wing_rot(side, [0, 0, -60 * (1 - ease_out_back(seg(t, 2.2, 3.2))) if t < 3.2 else 0]), INTRO_LEN)
    A_intro = anim(INTRO_LEN, bones)

    # ---- 倒れる: 翼が痙攣してだらりと落ち、羽が散り、核が過負荷で膨らんで弾ける
    bones = {}
    for name in SG_ALL:
        go = lambda t: 1 - 0.99 * ease_in_quad(seg(t, 4.0, 4.6))
        if name in SG_WINGS or name.startswith("crest"):
            gone = 2.4 if name.startswith("wing") else 1.9
            put(bones, name, "scale", lambda t, gone=gone: S(max(TINY, 1 - 0.99 * ease_in_quad(seg(t, gone - 0.8, gone)))), DEATH_LEN)
            put(bones, name, "position", lambda t: [0.0, -10.0 * ease_in_quad(seg(t, 1.0, 2.6)), 0.0], DEATH_LEN)
        elif name in ("finger", "joint", "shed", "web"):
            put(bones, name, "scale", lambda t: S(max(TINY, 1 - 0.99 * ease_in_quad(seg(t, 1.4, 2.4)))), DEATH_LEN)
            put(bones, name, "position", lambda t: [0.0, -10.0 * ease_in_quad(seg(t, 1.2, 2.4)), 0.0], DEATH_LEN)
        elif name in ("core", "core_eye"):
            put(bones, name, "scale",
                lambda t: S(max(TINY, (1 + 1.2 * ease_in_quad(seg(t, 1.8, 3.9)) + 0.25 * math.sin(t * 30) * seg(t, 1.8, 3.9)
                                      + (1.8 * math.sin(math.pi * seg(t, 3.9, 4.2))) ) * go(t))), DEATH_LEN)
        else:
            put(bones, name, "scale", lambda t: S(max(TINY, (1 + 0.1 * math.sin(t * 25) * seg(t, 0, 3.9)) * go(t))), DEATH_LEN)
        if name in ("heart", "heart_vein", "vessel", "core", "core_eye"):
            put(bones, name, "position",
                lambda t, name=name: add(shake(t, 0.9 * seg(t, 0, 0.6) * (1 - seg(t, 3.9, 4.4)), seedof(name) % 5, 47.0),
                                          [0.0, -6.0 * ease_in_out(seg(t, 1.0, 4.4)), 0.0]), DEATH_LEN)
    for name, side in (("wing_l", 1), ("wing_r", -1)):
        def rot(t, side=side):
            flutter = 14 * math.sin(t * 31) * seg(t, 0, 0.3) * (1 - seg(t, 0.9, 1.1))
            limp = ease_in_out(seg(t, 1.0, 2.2))
            return wing_rot(side, [20 * limp, 0, -flutter + 70 * limp])

        put(bones, name, "rotation", rot, DEATH_LEN)
    for name, side in (("crest_l", 1), ("crest_r", -1)):
        put(bones, name, "rotation", lambda t, side=side: wing_rot(side, [0, 0, 55 * ease_in_out(seg(t, 1.0, 1.9))]), DEATH_LEN)
    A_death = anim(DEATH_LEN, bones, loop_hold=True)
    return A_intro, A_death


# ================================================================ 余燼の心臓
EM_ALL = ["heart", "heart_vein", "vessel", "core_ring", "core_eye", "crust_glow", "crust", "drips", "cinders", "ash_drips", "ash_shell",
          "drops", "heart_crust"]
EM_THUMPS = (0.6, 1.2, 1.7, 2.1, 2.4)


def thump(t, amp=1.0, width=0.28):
    """鼓動の山 (熱の灯るたびの膨らみ)"""
    v = 0.0
    for k, t0 in enumerate(EM_THUMPS):
        x = seg(t, t0, t0 + width)
        v = max(v, math.sin(math.pi * x) * (0.5 + 0.1 * k))
    return v * amp


def ember():
    # ---- 登場: 灰の塊が鼓動のたびに熱を帯び、最後に殻を割って熱を吐く
    bones = {}
    shell = ("ash_shell", "crust", "vessel")
    for name in EM_ALL:
        if name in shell:
            def sc(t, name=name):
                grow = 0.62 + 0.38 * ease_in_out(seg(t, 0.0, 2.4))
                burst = 0.3 * math.sin(math.pi * seg(t, 2.4, 3.1))
                return S(grow * (1 + burst + 0.1 * thump(t) * (1 if t < 2.6 else 0)))
        elif name == "crust_glow":
            def sc(t):
                level = sum(0.2 for t0 in EM_THUMPS if t >= t0)
                x = seg(t, 0.5, 3.4)
                return S(max(TINY, min(1.0, level) * (1 + 0.15 * thump(t)) if t < 3.1 else 1.0 + 0.15 * math.sin(math.pi * seg(t, 3.1, 3.8))))
        elif name in ("heart", "heart_vein", "core_ring", "core_eye", "heart_crust"):
            def sc(t, name=name):
                if t < 1.2:
                    return S(TINY)
                x = seg(t, 1.2, 2.6)
                s = max(TINY, ease_out_cubic(x) * 0.9) * (1 + 0.15 * thump(t))
                if t > 3.0:
                    s = s / 0.9 * (1 if name != "core_eye" else 1 + 0.6 * math.sin(math.pi * seg(t, 3.0, 4.0)) ** 2)
                    s *= 1 + (0.15 * math.sin(math.pi * seg(t, 3.0, 3.8)) ** 2 if name == "heart" else 0.0)
                return S(s)
        else:
            def sc(t):
                x = seg(t, 2.6, 3.5)
                return S(max(TINY, ease_out_back(x) if x < 1 else 1.0)) if t > 2.6 else S(TINY)

        put(bones, name, "scale", sc, INTRO_LEN)
        put(bones, name, "position", lambda t: [0.0, -6.0 * (1 - ease_out_cubic(seg(t, 0.0, 2.6))), 0.0], INTRO_LEN)
    for name in shell:
        put(bones, name, "position", lambda t: add([0.0, -6.0 * (1 - ease_out_cubic(seg(t, 0.0, 2.6))), 0.0], shake(t, 0.25 * thump(t), 2.0, 60.0)), INTRO_LEN)
    A_intro = anim(INTRO_LEN, bones)

    # ---- 倒れる: 鼓動がゆっくりになり、熱が引いて冷え、殻がひび割れて灰になって崩れる
    bones = {}
    for name in EM_ALL:
        go = lambda t: 1 - 0.99 * ease_in_quad(seg(t, 4.1, 4.8))
        if name == "crust_glow":
            put(bones, name, "scale", lambda t: S(max(TINY, (1 - 0.5 * seg(t, 0.0, 1.0) - 0.49 * seg(t, 1.0, 2.4)) * go(t)
                                                      * (1 + 0.5 * math.sin(math.pi * seg(t, 3.5, 3.9))))), DEATH_LEN)
        elif name in ("drips", "drops", "cinders", "ash_drips"):
            put(bones, name, "scale", lambda t: S(max(TINY, 1 - 0.99 * ease_in_quad(seg(t, 0.8, 2.0)))), DEATH_LEN)
            put(bones, name, "position", lambda t: [0.0, -8.0 * ease_in_quad(seg(t, 0.8, 2.0)), 0.0], DEATH_LEN)
        elif name in ("ash_shell", "crust", "vessel"):
            def sc(t):
                crack = 1 + 0.1 * seg(t, 1.8, 2.8) * (1 + 0.5 * math.sin(t * 40))
                sag = 1 - 0.55 * ease_in_quad(seg(t, 2.8, 4.1))
                return [crack * (1 + 0.25 * seg(t, 2.8, 4.1)), max(TINY, crack * sag * go(t)), crack * (1 + 0.25 * seg(t, 2.8, 4.1))]

            put(bones, name, "scale", sc, DEATH_LEN)
            put(bones, name, "position", lambda t: add(shake(t, 0.5 * seg(t, 1.8, 2.8) * (1 - seg(t, 2.8, 3.1)), 3.0, 55.0),
                                                       [0.0, -9.0 * ease_in_quad(seg(t, 2.8, 4.4)), 0.0]), DEATH_LEN)
        else:
            def sc(t, name=name):
                beat = 0.2 * (math.sin(math.pi * seg(t, 0.0, 0.45)) + 0.6 * math.sin(math.pi * seg(t, 0.9, 1.5))
                              + 0.4 * math.sin(math.pi * seg(t, 2.0, 2.8)) + 0.3 * math.sin(math.pi * seg(t, 3.5, 4.1)))
                dim = 1 - (0.6 * seg(t, 1.0, 2.4) if name == "core_eye" else 0.0)
                flash = 1 + 0.9 * math.sin(math.pi * seg(t, 3.7, 4.0)) if name in ("heart", "core_eye") else 1
                return S(max(TINY, (1 + beat) * dim * flash * go(t)))

            put(bones, name, "scale", sc, DEATH_LEN)
            put(bones, name, "position", lambda t: [0.0, -10.0 * ease_in_quad(seg(t, 3.0, 4.6)), 0.0], DEATH_LEN)
    A_death = anim(DEATH_LEN, bones, loop_hold=True)
    return A_intro, A_death


HEARTS = {"stone_whale_heart": stone_whale, "tide_heart": tide_shell, "storm_heart": storm_wing, "ember_heart": ember}


def main():
    for name, build in HEARTS.items():
        intro, death = build()
        path = ASSET / f"{name}.animation.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data["animations"][f"animation.{name}.intro"] = intro
        data["animations"][f"animation.{name}.death"] = death
        path.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        print(name, "intro/death written:", len(intro["bones"]), len(death["bones"]), "bones")


if __name__ == "__main__":
    main()
