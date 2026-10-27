#!/usr/bin/env python3
"""層の主の、決め技 (snuff・crush・split・roar) 以外の技の溜めと当たりの動きを、いまの animations/entity/lord_*.animation.json に足す。

    PYTHONIOENCODING=utf-8 python -I tools/gen/abyss/gen_lord_moves.py

主の技 (story/ch5/layers/LordMoves) は abyss/boss/BossLookHook で型 (slam・charge・bolts・sweep) に寄せて流れる。
その型の windup_<型> (2.0 秒、最後で止める。予告の長さに合わせて速さが変わる) と strike_<型> (0.8 秒) を主ごとに置く:
  肋の主   sweep (rib_sweep)・slam (bone_spikes・marrow_quake)・bolts (bone_rain)
  白殻の主 slam (shell_slam・salt_pillars)・bolts (brine_spray)・sweep (tide_ring)・charge (shell_roll)
  鼓動まね charge (jolt_line)・bolts (static_field・thunder_cross)・sweep (chain_burst)
  灯り喰い sweep (ember_claw)・bolts (ash_breath・cinder_rain)・slam (magma_burst)・charge (lunge)
当たりの動きは溜めの最後の姿から始める (溜めの最後の値 = 当たりの 0 秒の値)。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_abyss_mobs_b as G  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
ANIM = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "animations", "entity")


def R(frames):
    return G.kf(frames, True)


def T(frames):
    return G.kf(frames, False)


def sc(frames):
    return T({t: (v, v, v) if isinstance(v, (int, float)) else v for t, v in frames.items()})


def b(rot=None, pos=None, scale=None):
    d = {}
    if rot:
        d["rotation"] = R(rot)
    if pos:
        d["position"] = T(pos)
    if scale:
        d["scale"] = sc(scale)
    return d


def pair(windup, strike):
    """溜め (2.0 秒・最後で止める) と当たり (0.8 秒)。値は {骨: (回転, 位置, 大きさ)} の辞書で、溜めの終わり = 当たりの初め"""
    return G.once(2.0, windup, True), G.once(0.8, strike)


def wind(end, mid=None):
    """0 → (mid は 1.2 秒) → end (2.0 秒) の溜め"""
    f = {0: (0, 0, 0)}
    if mid:
        f[1.2] = mid
    f[2.0] = end
    return f


def hit(start, peak, over=None):
    """溜めの最後 → 当たりの瞬間 (0.12 秒) → 行き過ぎ (0.3 秒) → 戻る (0.8 秒)"""
    f = {0: start, 0.12: peak}
    if over:
        f[0.3] = over
    f[0.8] = (0, 0, 0)
    return f


# ---------------------------------------------------------------- 肋の主 (人の形・杖を右手に)
def lord_rib():
    out = {}
    # 払い: 胸を右へひねり杖を横へ引く → 左へ大きく薙ぐ
    w = {"chest": b(wind((6, -38, 0), (4, -30, 0))), "arm_r": b(wind((-30, 0, -70), (-24, 0, -60))), "fore_r": b(wind((20, 0, 0))),
         "arm_l": b(wind((0, 0, 30))), "leg_l": b(wind((14, 0, 0))), "leg_r": b(wind((-12, 0, 0))), "head": b(wind((4, -20, 0)))}
    s = {"chest": b(hit((6, -38, 0), (-8, 40, 0), (-8, 52, 0))), "arm_r": b(hit((-30, 0, -70), (-70, 70, -20), (-60, 90, -10))),
         "fore_r": b(hit((20, 0, 0), (0, 0, 0))), "arm_l": b(hit((0, 0, 30), (0, 0, -10))), "leg_l": b(hit((14, 0, 0), (-6, 0, 0))),
         "leg_r": b(hit((-12, 0, 0), (8, 0, 0))), "head": b(hit((4, -20, 0), (-6, 24, 0)))}
    out["sweep"] = pair(w, s)
    # 叩きつけ: 杖を両手で頭の上へ → 地へ突き立てる (体が沈む)
    w = {"arm_r": b(wind((-165, 0, 10), (-150, 0, 8))), "arm_l": b(wind((-150, 0, -10), (-140, 0, -8))), "fore_r": b(wind((-20, 0, 0))),
         "chest": b(wind((16, 0, 0)), scale={0: 1.0, 2.0: (1.08, 1.04, 1.1)}), "head": b(wind((-18, 0, 0))),
         "body": b(pos={0: (0, 0, 0), 2.0: (0, 1.5, 0)})}
    s = {"arm_r": b(hit((-165, 0, 10), (30, 0, 4), (38, 0, 4))), "arm_l": b(hit((-150, 0, -10), (24, 0, -4))), "fore_r": b(hit((-20, 0, 0), (10, 0, 0))),
         "chest": b(hit((16, 0, 0), (-30, 0, 0), (-34, 0, 0)), scale={0: (1.08, 1.04, 1.1), 0.12: (0.92, 0.96, 0.9), 0.8: 1.0}),
         "head": b(hit((-18, 0, 0), (16, 0, 0))), "body": b(pos={0: (0, 1.5, 0), 0.12: (0, -4, 0), 0.3: (0, -4.5, 0), 0.8: (0, 0, 0)})}
    out["slam"] = pair(w, s)
    # 骨の雨: 杖を高く掲げ、灯りを揺らして天を仰ぐ → 杖を振り下ろして降らせる
    w = {"arm_r": b(wind((-170, 0, -6))), "fore_r": b(wind((-10, 0, 0))), "head": b(wind((30, 0, 0), (26, 0, 0))), "jaw": b(wind((30, 0, 0))),
         "chest": b(wind((10, 0, 0)), scale={0: 1.0, 1.0: (1.06, 1.03, 1.06), 2.0: (1.1, 1.05, 1.1)}),
         "lamps": b({0: (0, 0, 0), 0.5: (-20, 0, 12), 1.0: (18, 0, -12), 1.5: (-20, 0, 12), 2.0: (16, 0, -10)})}
    s = {"arm_r": b(hit((-170, 0, -6), (-40, 0, 0))), "fore_r": b(hit((-10, 0, 0), (20, 0, 0))), "head": b(hit((30, 0, 0), (-10, 0, 0))),
         "jaw": b(hit((30, 0, 0), (44, 0, 0))), "chest": b(hit((10, 0, 0), (-14, 0, 0)), scale={0: (1.1, 1.05, 1.1), 0.12: 0.94, 0.8: 1.0}),
         "lamps": b(hit((16, 0, -10), (-30, 0, 20), (20, 0, -10)))}
    out["bolts"] = pair(w, s)
    return out


# ---------------------------------------------------------------- 白殻の主 (長い首・殻・目の茎・尾)
def lord_salt():
    out = {}
    # 叩きつけ: 首を高く持ち上げ殻を起こす → 頭から叩き落とす
    w = {"neck": b(wind((40, 0, 0), (34, 0, 0))), "head": b(wind((24, 0, 0))), "body": b(wind((10, 0, 0)), {0: (0, 0, 0), 2.0: (0, 2, 0)}),
         "shell": b(wind((-8, 0, 0))), "stalk_l": b(wind((-20, 0, 10))), "stalk_r": b(wind((-20, 0, -10))), "tail": b(wind((-14, 0, 0)))}
    s = {"neck": b(hit((40, 0, 0), (-54, 0, 0), (-58, 0, 0))), "head": b(hit((24, 0, 0), (-20, 0, 0))),
         "body": b(hit((10, 0, 0), (-12, 0, 0)), {0: (0, 2, 0), 0.12: (0, -2.5, -3), 0.3: (0, -3, -3), 0.8: (0, 0, 0)}),
         "shell": b(hit((-8, 0, 0), (6, 0, 0))), "stalk_l": b(hit((-20, 0, 10), (20, 0, 0))), "stalk_r": b(hit((-20, 0, -10), (20, 0, 0))),
         "tail": b(hit((-14, 0, 0), (16, 0, 0)))}
    out["slam"] = pair(w, s)
    # 塩の吐きかけ: 頭を反らして体を膨らませる → 首を突き出して吐く (体が縮む)
    w = {"neck": b(wind((24, 0, 0))), "head": b(wind((30, 0, 0))), "body": b(scale={0: 1.0, 1.2: (1.06, 1.08, 1.06), 2.0: (1.1, 1.12, 1.1)}),
         "stalk_l": b(wind((0, 0, 30))), "stalk_r": b(wind((0, 0, -30)))}
    s = {"neck": b(hit((24, 0, 0), (-30, 0, 0))), "head": b(hit((30, 0, 0), (-24, 0, 0), (-28, 0, 0))),
         "body": b(scale={0: (1.1, 1.12, 1.1), 0.12: (0.9, 0.9, 0.92), 0.4: (0.95, 0.95, 0.96), 0.8: 1.0}),
         "stalk_l": b(hit((0, 0, 30), (0, 0, -10))), "stalk_r": b(hit((0, 0, -30), (0, 0, 10)))}
    out["bolts"] = pair(w, s)
    # 潮の輪: 身を低くして殻をひねる → 殻ごと一回りする
    w = {"body": b(wind((0, -30, 0)), {0: (0, 0, 0), 2.0: (0, -2.5, 0)}), "shell": b(wind((0, -40, 0))), "neck": b(wind((-20, 0, 0))),
         "tail": b(wind((0, 40, 0)))}
    s = {"body": b({0: (0, -30, 0), 0.4: (0, 330, 0), 0.8: (0, 360, 0)}, {0: (0, -2.5, 0), 0.4: (0, -1, 0), 0.8: (0, 0, 0)}),
         "shell": b({0: (0, -40, 0), 0.3: (0, 20, 0), 0.8: (0, 0, 0)}), "neck": b(hit((-20, 0, 0), (-6, 0, 0))), "tail": b(hit((0, 40, 0), (0, -30, 0)))}
    out["sweep"] = pair(w, s)
    # 殻転がり: 首と目を殻へ引っ込め、後ろへ引く → 前へ転がり出る
    w = {"neck": b(wind((-60, 0, 0), (-50, 0, 0))), "head": b(wind((-30, 0, 0))), "stalk_l": b(wind((60, 0, 0))), "stalk_r": b(wind((60, 0, 0))),
         "body": b(wind((-14, 0, 0)), {0: (0, 0, 0), 2.0: (0, -1, 3)}), "tail": b(wind((40, 0, 0)))}
    s = {"body": b({0: (-14, 0, 0), 0.12: (60, 0, 0), 0.4: (120, 0, 0), 0.8: (0, 0, 0)}, {0: (0, -1, 3), 0.12: (0, 0, -6), 0.4: (0, 0, -8), 0.8: (0, 0, 0)}),
         "neck": b(hit((-60, 0, 0), (-60, 0, 0), (-40, 0, 0))), "head": b(hit((-30, 0, 0), (-30, 0, 0))), "stalk_l": b(hit((60, 0, 0), (60, 0, 0))),
         "stalk_r": b(hit((60, 0, 0), (60, 0, 0))), "tail": b(hit((40, 0, 0), (40, 0, 0)))}
    out["charge"] = pair(w, s)
    return out


# ---------------------------------------------------------------- 鼓動まねの主 (六本脚・心臓・顎・背の芽)
LEGS = [(-1, -1, 0), (1, -1, 1), (-1, 0, 2), (1, 0, 3), (-1, 1, 4), (1, 1, 5)]   # gen_abyss_boss_models.STORM_LEGS と同じ (横, 前後, 番号)


def lord_storm():
    out = {}
    # 雷の線: 身を沈めて後ろへ引く → 前へ突っ込む
    w = {"body": b(wind((14, 0, 0)), {0: (0, 0, 0), 2.0: (0, -2.5, 3)}), "jaw": b(wind((30, 0, 0))), "heart": b(scale={0: 1.0, 1.0: 1.15, 1.2: 1.0, 1.8: 1.2, 2.0: 1.2})}
    s = {"body": b(hit((14, 0, 0), (-16, 0, 0)), {0: (0, -2.5, 3), 0.12: (0, 0, -6), 0.3: (0, 0, -7), 0.8: (0, 0, 0)}), "jaw": b(hit((30, 0, 0), (50, 0, 0))),
         "heart": b(scale={0: 1.2, 0.12: 1.4, 0.3: 1.0, 0.8: 1.0})}
    for sx, fz, i in LEGS:
        w["leg%d" % i] = b(wind((fz * 18, 0, -sx * 20)))
        s["leg%d" % i] = b(hit((fz * 18, 0, -sx * 20), (-fz * 20, 0, sx * 6)))
    out["charge"] = pair(w, s)
    # 落ちる雷・十字: 背の芽を立てて光らせ、心臓が速く打つ → 芽を弾いて落とす
    w = {"buds": b(wind((-50, 0, 0), (-40, 0, 0)), scale={0: 1.0, 2.0: 1.5}), "body": b(wind((-10, 0, 0)), {0: (0, 0, 0), 2.0: (0, 2, 0)}),
         "heart": b(scale={0: 1.0, 0.3: 1.2, 0.5: 1.0, 0.8: 1.2, 1.0: 1.0, 1.3: 1.25, 1.5: 1.0, 1.7: 1.3, 2.0: 1.3}), "jaw": b(wind((20, 0, 0)))}
    s = {"buds": b(hit((-50, 0, 0), (30, 0, 0), (-10, 0, 0)), scale={0: 1.5, 0.12: 1.7, 0.4: 1.0, 0.8: 1.0}),
         "body": b(hit((-10, 0, 0), (8, 0, 0)), {0: (0, 2, 0), 0.12: (0, -1, 0), 0.8: (0, 0, 0)}), "heart": b(scale={0: 1.3, 0.12: 1.5, 0.3: 1.0, 0.8: 1.0}),
         "jaw": b(hit((20, 0, 0), (40, 0, 0)))}
    out["bolts"] = pair(w, s)
    # 鎖の弾け: 脚を広げて体をひねる → 一回りして弾ける
    w = {"body": b(wind((0, -40, 0)), {0: (0, 0, 0), 2.0: (0, -1.5, 0)}, {0: 1.0, 2.0: (0.92, 0.96, 0.92)}), "heart": b(scale={0: 1.0, 2.0: 1.25})}
    s = {"body": b({0: (0, -40, 0), 0.35: (0, 320, 0), 0.8: (0, 360, 0)}, {0: (0, -1.5, 0), 0.8: (0, 0, 0)}, {0: (0.92, 0.96, 0.92), 0.12: 1.16, 0.4: 1.0, 0.8: 1.0}),
         "heart": b(scale={0: 1.25, 0.12: 1.5, 0.3: 1.0, 0.8: 1.0})}
    for sx, fz, i in LEGS:
        w["leg%d" % i] = b(wind((0, 0, -sx * 26)))
        s["leg%d" % i] = b(hit((0, 0, -sx * 26), (0, 0, sx * 10)))
    out["sweep"] = pair(w, s)
    return out


# ---------------------------------------------------------------- 灯り喰いの主 (浮かぶ体・顎・灯り・左右の鉤)
def lord_ash():
    out = {}
    # 熾の爪: 体をひねって鉤を後ろへ → 前へ掻く
    w = {"body": b(wind((6, -30, 0))), "hooks_l": b(wind((0, -50, -30))), "hooks_r": b(wind((0, 50, 30))), "jaw": b(wind((20, 0, 0)))}
    s = {"body": b(hit((6, -30, 0), (-10, 30, 0), (-10, 40, 0))), "hooks_l": b(hit((0, -50, -30), (0, 40, 20))), "hooks_r": b(hit((0, 50, 30), (0, -40, -20))),
         "jaw": b(hit((20, 0, 0), (36, 0, 0)))}
    out["sweep"] = pair(w, s)
    # 灰の息・燠の雨: 浮き上がって口を大きく開け、灯りが燃え上がる → 吐き下ろす
    w = {"body": b(wind((14, 0, 0)), {0: (0, 0, 0), 2.0: (0, 4, 0)}, {0: 1.0, 2.0: 1.08}), "jaw": b(wind((60, 0, 0), (40, 0, 0))),
         "lamps": b({0: (0, 0, 0), 0.5: (-14, 0, 10), 1.0: (14, 0, -10), 1.5: (-18, 0, 12), 2.0: (-16, 0, 10)}),
         "hooks_l": b(wind((0, -20, -40))), "hooks_r": b(wind((0, 20, 40)))}
    s = {"body": b(hit((14, 0, 0), (-20, 0, 0)), {0: (0, 4, 0), 0.12: (0, 2, -3), 0.8: (0, 0, 0)}, {0: 1.08, 0.12: 0.94, 0.8: 1.0}),
         "jaw": b(hit((60, 0, 0), (70, 0, 0), (50, 0, 0))), "lamps": b(hit((-16, 0, 10), (30, 0, -20))),
         "hooks_l": b(hit((0, -20, -40), (0, 0, 0))), "hooks_r": b(hit((0, 20, 40), (0, 0, 0)))}
    out["bolts"] = pair(w, s)
    # 熔岩の破裂: 高く浮いて鉤を掲げる → 落ちて叩きつける (体がつぶれる)
    w = {"body": b(wind((-10, 0, 0)), {0: (0, 0, 0), 1.2: (0, 5, 0), 2.0: (0, 6, 0)}), "hooks_l": b(wind((-60, -20, -50))), "hooks_r": b(wind((-60, 20, 50))),
         "jaw": b(wind((30, 0, 0))), "lamps": b(wind((-30, 0, 0)))}
    s = {"body": b(hit((-10, 0, 0), (20, 0, 0)), {0: (0, 6, 0), 0.12: (0, -3, 0), 0.3: (0, -2, 0), 0.8: (0, 0, 0)}, {0: 1.0, 0.12: (1.2, 0.8, 1.2), 0.4: 1.0, 0.8: 1.0}),
         "hooks_l": b(hit((-60, -20, -50), (40, 0, -10))), "hooks_r": b(hit((-60, 20, 50), (40, 0, 10))), "jaw": b(hit((30, 0, 0), (50, 0, 0))),
         "lamps": b(hit((-30, 0, 0), (40, 0, 0), (-20, 0, 0)))}
    out["slam"] = pair(w, s)
    # 飛びかかり: 後ろへ反って鉤を引く → 前へ飛ぶ
    w = {"body": b(wind((22, 0, 0)), {0: (0, 0, 0), 2.0: (0, 1, 4)}), "hooks_l": b(wind((40, -20, -20))), "hooks_r": b(wind((40, 20, 20))), "jaw": b(wind((40, 0, 0)))}
    s = {"body": b(hit((22, 0, 0), (-30, 0, 0)), {0: (0, 1, 4), 0.12: (0, 0, -8), 0.3: (0, 0, -9), 0.8: (0, 0, 0)}),
         "hooks_l": b(hit((40, -20, -20), (-70, 10, 0))), "hooks_r": b(hit((40, 20, 20), (-70, -10, 0))), "jaw": b(hit((40, 0, 0), (64, 0, 0)))}
    out["charge"] = pair(w, s)
    return out


LORDS = {"lord_rib": lord_rib, "lord_salt": lord_salt, "lord_storm": lord_storm, "lord_ash": lord_ash}


def clean(bones):
    return {n: {k: v for k, v in bb.items() if v} for n, bb in bones.items() if any(bb.values())}


# 体全体が一つの骨で動く主 (鼓動まね・灯り喰い) は、同じ角度でも見た目の動きが小さい。回転と移動をこの倍に (大きさと一回りの回転は変えない)
AMP = {"lord_storm": 1.8, "lord_ash": 1.8}


def amp(anim, k):
    for bone in anim["bones"].values():
        for key in ("rotation", "position"):
            if key in bone:
                bone[key] = {t: [v * k if isinstance(v, (int, float)) and abs(v) < 200 else v for v in vals] for t, vals in bone[key].items()}


def main():
    n = 0
    for stem, fn in LORDS.items():
        path = os.path.join(ANIM, stem + ".animation.json")
        d = json.load(open(path, encoding="utf-8"))
        for move, (w, s) in fn().items():
            w["bones"] = clean(w["bones"])
            s["bones"] = clean(s["bones"])
            if stem in AMP:
                amp(w, AMP[stem])
                amp(s, AMP[stem])
            d["animations"]["animation.%s.windup_%s" % (stem, move)] = w
            d["animations"]["animation.%s.strike_%s" % (stem, move)] = s
            n += 1
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(d, f, ensure_ascii=False, indent=1)
            f.write("\n")
    print("added", n, "windup/strike pairs")


if __name__ == "__main__":
    main()
