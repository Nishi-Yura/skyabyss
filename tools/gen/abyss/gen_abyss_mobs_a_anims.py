#!/usr/bin/env python3
"""深淵の敵 A の 9 種のアニメ (abyss_<id>_s1.animation.json) を書く。三段とも骨の名前・付け根は同じなので一つのファイルを共有する。

  python -I tools/gen/abyss/gen_abyss_mobs_a_anims.py

前は idle / walk の空の枠だけで、どの種も動かなかった (docs/review/mob_quality_2026-10-23.md)。ここで全種に
待機・歩き (飛ぶ種は羽ばたき)・攻撃・被弾・倒れるを付け、予告のある種は予告の長さに合わせた構えを付ける:
  骨笛 call 1.5 秒 (WARN_CALL 30)、塩の抜け殻 crack 1.5 秒 (WARN_CRACK 30) と wake、潮だまりの手 grasp 1.5 秒で閉じる (WARN_RING 30)、
  避雷の像 charge 1.5 秒で振り下ろす (WARN_RING 30)、焦げた鳥の影 shadow 1.2 秒 (WARN_SHADOW 24) と dive 2 秒 (急降下 40 tick)、
  白い貝守は殻の開け閉め (open / shut の繰り返し。AbyssMob の shell の制御が OPEN で選ぶ)。
向き: 下の関数は「垂れた部品 (腕・脚) の x の + が前へ振る、立つ部品 (胴・頭) と前へ伸びた部品 (鳥の胴・首) の x の - が前へ倒れる」で書き、
書き出すときに x を反転する (flip_x)。ゲームの GeckoLib では、ファイルの x の + で立つ部品が前へ倒れる (像が倒れる向きで確かめた。
docs/review/mob_quality_v2/)。
"""
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "animations", "entity")


def kf(points):
    """{時間: [x, y, z]} → GeckoLib のキー (時間は秒)。"""
    return {("%g" % t): {"vector": [round(v, 2) for v in vec]} for t, vec in sorted(points.items())}


def sin(amp, period, phase=0.0, base=0.0):
    """ループ用の正弦の式 (anim_time は秒)。"""
    s = "%g * math.sin(%g * query.anim_time + %g)" % (amp, 360.0 / period, phase)
    return s if base == 0 else "%g + %s" % (base, s)


def loop(length, bones):
    return {"loop": True, "animation_length": length, "bones": bones}


def once(length, bones, hold=False):
    # 倒れる動きの「終わった姿で止める」は GuardMob の thenPlayAndHold が受け持つ (hold は印だけ)
    return {"loop": False, "animation_length": length, "bones": bones}


def rot(**axes):
    return {"rotation": [axes.get("x", "0"), axes.get("y", "0"), axes.get("z", "0")]}


def rp(r=None, p=None):
    d = {}
    if r:
        d["rotation"] = kf(r)
    if p:
        d["position"] = kf(p)
    return d


def shake(t0, t1, step, amp, axis=2, base=(0, 0, 0)):
    """t0〜t1 の細かい震え (交互に ±amp)。"""
    out = {}
    t = t0
    s = 1
    while t <= t1 + 1e-6:
        v = list(base)
        v[axis] += amp * s
        out[round(t, 3)] = v
        s = -s
        t += step
    return out


Z = [0, 0, 0]


# ---------------------------------------------------------------- 人型 (肋の番・塩の抜け殻・避雷の像) の共通
def human_walk(period=1.0, arm=18, leg=24, shield_arm=None):
    b = {
        "leg_l": rot(x=sin(leg, period)),
        "leg_r": rot(x=sin(-leg, period)),
        "arm_r": rot(x=sin(-arm, period), z="-3"),
        "body": {"rotation": ["-3", sin(4, period), "0"],
                 "position": ["0", "0.45 * math.abs(math.sin(%g * query.anim_time))" % (360.0 / period), "0"]},
        "head": rot(x="2", y=sin(-4, period)),
    }
    b["arm_l"] = rot(x=shield_arm, z="3") if shield_arm else rot(x=sin(arm, period), z="3")
    return b


def human_idle(period=3.0, shield_arm=None, slump=0.0):
    b = {
        "body": {"rotation": [sin(1.5, period, 0, -slump), "0", "0"], "position": ["0", sin(0.25, period), "0"]},
        "head": rot(x=sin(-2.5, period, 40, slump * 0.6), y=sin(6, period * 2)),
        "arm_r": rot(x=sin(3, period, 90, slump * 0.5), z=sin(2, period, 0, -4)),
        "leg_l": rot(x="0"), "leg_r": rot(x="0"),
    }
    b["arm_l"] = rot(x=shield_arm, z="4") if shield_arm else rot(x=sin(-3, period, 90, slump * 0.5), z=sin(-2, period, 0, 4))
    return b


def human_hurt():
    return once(0.35, {
        "body": rp({0: Z, 0.08: [10, 0, 5], 0.35: Z}, {0: Z, 0.08: [0, 0, 1.2], 0.35: Z}),
        "head": rp({0: Z, 0.08: [16, 0, -8], 0.35: Z}),
        "arm_l": rp({0: Z, 0.08: [-12, 0, -10], 0.35: Z}),
        "arm_r": rp({0: Z, 0.08: [-12, 0, 10], 0.35: Z}),
    })


def human_death(length=1.4):
    """膝から崩れ、前へ倒れる (胴は前へ = x の -)。"""
    return once(length, {
        "body": rp({0: Z, 0.25: [8, 0, -4], 0.55: [-20, 0, -6], 1.0: [-82, 0, -8], length: [-86, 0, -8]},
                   {0: Z, 0.25: [0, -1.5, 0], 0.55: [0, -3.5, 0], 1.0: [0, -9.5, 0], length: [0, -10, 0]}),
        "head": rp({0: Z, 0.25: [18, 0, 6], 0.55: [-10, 0, 10], length: [-14, 12, 14]}),
        # 膝が抜けて (脚が胴より遅れて) 前へ倒れる。最後は脚も胴とそろって伸びる
        "leg_l": rp({0: Z, 0.25: [-14, 0, 0], 0.55: [14, 0, -4], 1.0: [10, 0, -4], length: [6, 0, -4]}),
        "leg_r": rp({0: Z, 0.25: [-6, 0, 0], 0.55: [24, 0, 4], 1.0: [16, 0, 4], length: [10, 0, 4]}),
        "arm_l": rp({0: Z, 0.25: [-20, 0, -14], 0.55: [30, 0, -18], 1.0: [150, 0, -20], length: [160, 0, -20]}),
        "arm_r": rp({0: Z, 0.25: [-20, 0, 14], 0.55: [40, 0, 16], 1.0: [150, 0, 22], length: [158, 0, 22]}),
    }, hold=True)


# ---------------------------------------------------------------- 肋の番: 盾を前に構えて歩き、盾の陰から右手で突く
SHIELD_X = "26"


def rib_guard():
    return {
        "idle": loop(3.0, human_idle(3.0, shield_arm=sin(2, 3.0, 0, 26))),
        "walk": loop(1.0, human_walk(1.0, arm=14, leg=22, shield_arm=sin(3, 1.0, 0, 28))),
        "attack": once(0.8, {
            # 0〜0.12 引き (盾を寄せ、右手を後ろへ) → 0.2 突き → 0.45 戻り
            "arm_r": rp({0: Z, 0.12: [-46, 10, 8], 0.22: [92, -8, -4], 0.4: [86, -8, -4], 0.8: Z}),
            "arm_l": rp({0: [26, 0, 4], 0.12: [40, 18, 6], 0.22: [30, 6, 4], 0.8: [26, 0, 4]}),
            "body": rp({0: Z, 0.12: [6, 16, 0], 0.22: [-12, -14, 0], 0.4: [-10, -12, 0], 0.8: Z},
                       {0: Z, 0.12: [0, 0, 0.8], 0.22: [0, -0.4, -2.2], 0.4: [0, -0.4, -2], 0.8: Z}),
            "head": rp({0: Z, 0.12: [4, -10, 0], 0.22: [-6, 10, 0], 0.8: Z}),
            "leg_l": rp({0: Z, 0.22: [24, 0, 0], 0.4: [22, 0, 0], 0.8: Z}),
            "leg_r": rp({0: Z, 0.22: [-18, 0, 0], 0.4: [-16, 0, 0], 0.8: Z}),
        }),
        "hurt": human_hurt(),
        "death": human_death(),
    }


# ---------------------------------------------------------------- 塩の抜け殻: 塩に固まって俯いた姿で待つ。ひびが入って震え、起きて殴る
def salt_husk():
    return {
        # 固まった姿 (俯いて腕を垂らす)。息はほとんど無い
        "idle": loop(4.0, human_idle(4.0, slump=18)),
        "walk": loop(1.2, {
            **human_walk(1.2, arm=10, leg=20),
            "body": {"rotation": ["-14", sin(6, 1.2), sin(3, 2.4)],
                     "position": ["0", "0.5 * math.abs(math.sin(300 * query.anim_time))", "0"]},
            "head": rot(x="10", y=sin(-6, 1.2), z=sin(4, 2.4)),
            "arm_l": rot(x=sin(14, 1.2, 0, 14), z="6"), "arm_r": rot(x=sin(-14, 1.2, 0, 14), z="-6"),
        }),
        # 予告 1.5 秒: 全身が細かく震え、腕が少しずつ持ち上がる (ひびの粒と同じ長さ)
        "crack": once(1.5, {
            "body": rp({**shake(0.0, 1.4, 0.1, 2.0, 2, (-18, 0, 0)), 1.5: [-8, 0, 0]}),
            "head": rp({0: [-10, 0, 0], 0.6: [-6, 8, 0], 1.0: [-2, -8, 0], 1.5: [8, 0, 0]}),
            "arm_l": rp({0: [9, 0, 4], 1.5: [40, 0, -14]}),
            "arm_r": rp({0: [9, 0, -4], 1.5: [40, 0, 14]}),
        }),
        # 割れて起きる (0.5 秒): 塩を振り落とすように反る
        "wake": once(0.6, {
            "body": rp({0: [-8, 0, 0], 0.15: [14, 0, 0], 0.6: Z}, {0: Z, 0.15: [0, 0.8, 0], 0.6: Z}),
            "head": rp({0: [8, 0, 0], 0.15: [24, 0, 0], 0.6: Z}),
            "arm_l": rp({0: [40, 0, -14], 0.15: [-30, 0, -40], 0.6: Z}),
            "arm_r": rp({0: [40, 0, 14], 0.15: [-30, 0, 40], 0.6: Z}),
        }),
        # 両腕の振り下ろし
        "attack": once(0.9, {
            "arm_l": rp({0: Z, 0.14: [168, 0, 10], 0.3: [40, 0, 4], 0.5: [36, 0, 4], 0.9: Z}),
            "arm_r": rp({0: Z, 0.14: [168, 0, -10], 0.3: [40, 0, -4], 0.5: [36, 0, -4], 0.9: Z}),
            "body": rp({0: Z, 0.14: [10, 0, 0], 0.3: [-22, 0, 0], 0.5: [-20, 0, 0], 0.9: Z},
                       {0: Z, 0.3: [0, -0.8, -1.2], 0.5: [0, -0.8, -1.2], 0.9: Z}),
            "head": rp({0: Z, 0.14: [14, 0, 0], 0.3: [-8, 0, 0], 0.9: Z}),
        }),
        "hurt": human_hurt(),
        # 塩の塊に戻って崩れる (沈みながら前へ)
        "death": once(1.4, {
            "body": rp({0: Z, 0.3: [-10, 0, 6], 0.8: [-40, 0, 10], 1.4: [-62, 0, 12]}, {0: Z, 0.3: [0, -2, 0], 0.8: [0, -7, 0], 1.4: [0, -10, -1]}),
            "head": rp({0: Z, 0.3: [-20, 0, 0], 1.4: [-30, 14, 18]}),
            "leg_l": rp({0: Z, 0.3: [14, 0, -6], 0.8: [40, 0, -10], 1.4: [56, 0, -12]}),
            "leg_r": rp({0: Z, 0.3: [6, 0, 6], 0.8: [34, 0, 10], 1.4: [50, 0, 12]}),
            "arm_l": rp({0: Z, 0.3: [20, 0, -10], 1.4: [70, 0, -30]}),
            "arm_r": rp({0: Z, 0.3: [20, 0, 10], 1.4: [64, 0, 34]}),
        }, hold=True),
    }


# ---------------------------------------------------------------- 避雷の像: 動かない像。雷を集めて両腕を掲げ、輪の終わりで振り下ろす
def lightning_idol():
    return {
        "idle": loop(4.0, {
            "body": {"rotation": [sin(0.8, 4.0), "0", "0"], "position": ["0", sin(0.12, 4.0), "0"]},
            "head": rot(x=sin(-2, 4.0, 60), y=sin(12, 8.0)),
            "arm_l": rot(x=sin(2, 4.0, 90), z=sin(-1.5, 4.0, 0, 3)),
            "arm_r": rot(x=sin(2, 4.0, 90), z=sin(1.5, 4.0, 0, -3)),
        }),
        "walk": loop(1.4, human_walk(1.4, arm=8, leg=14)),
        # 予告 1.5 秒: 両腕を頭の上へ (0〜0.9)、震えながら溜め (0.9〜1.45)、1.5 で振り下ろして指す、1.9 戻る
        "charge": once(1.9, {
            "arm_l": rp({0: Z, 0.5: [150, 0, 22], 0.9: [168, 0, 16], **shake(0.95, 1.4, 0.075, 3, 2, (168, 0, 16)),
                         1.5: [70, 0, 6], 1.62: [62, 0, 6], 1.9: Z}),
            "arm_r": rp({0: Z, 0.5: [150, 0, -22], 0.9: [168, 0, -16], **shake(0.95, 1.4, 0.075, 3, 2, (168, 0, -16)),
                         1.5: [70, 0, -6], 1.62: [62, 0, -6], 1.9: Z}),
            "body": rp({0: Z, 0.9: [8, 0, 0], 1.45: [10, 0, 0], 1.5: [-14, 0, 0], 1.62: [-12, 0, 0], 1.9: Z},
                       {0: Z, 0.9: [0, 0.6, 0], 1.45: [0, 0.8, 0], 1.5: [0, -0.6, -0.8], 1.9: Z}),
            "head": rp({0: Z, 0.9: [22, 0, 0], 1.45: [26, 0, 0], 1.5: [-12, 0, 0], 1.9: Z}),
        }),
        "attack": once(0.7, {
            "arm_r": rp({0: Z, 0.15: [-30, 0, 0], 0.3: [80, 0, 0], 0.7: Z}),
            "body": rp({0: Z, 0.15: [4, 10, 0], 0.3: [-8, -8, 0], 0.7: Z}),
        }),
        # 石なので小さく揺れるだけ
        "hurt": once(0.3, {
            "body": rp({0: Z, 0.06: [3, 0, 2], 0.14: [-2, 0, -1.5], 0.3: Z}),
            "head": rp({0: Z, 0.06: [6, 0, -4], 0.3: Z}),
        }),
        # 割れて前へ倒れる (膝は曲がらない像の倒れ方)
        "death": once(1.6, {
            "body": rp({0: Z, 0.3: [2, 0, 3], 0.5: [-4, 0, 2], 1.2: [-84, 0, 4], 1.35: [-80, 0, 4], 1.6: [-88, 0, 4]},
                       {0: Z, 0.5: [0, 0, 0], 1.2: [0, -9.5, 0], 1.6: [0, -10, 0]}),
            "head": rp({0: Z, 1.2: [-6, 0, 0], 1.35: [10, 10, 16], 1.6: [6, 14, 18]}),
            "arm_l": rp({0: Z, 1.2: [10, 0, -8], 1.35: [40, 0, -30], 1.6: [44, 0, -34]}),
            "arm_r": rp({0: Z, 1.2: [10, 0, 8], 1.35: [36, 0, 26], 1.6: [40, 0, 30]}),
            "leg_l": rp({0: Z, 1.6: Z}),
            "leg_r": rp({0: Z, 1.6: Z}),
        }, hold=True),
    }


# ---------------------------------------------------------------- 骨拾い: 骨を漁る小さな四つ足。斜めの組で脚を出して走る
def bone_picker():
    P = 0.42
    legs = {}
    for name, ph, side in (("leg_l1", 0, -1), ("leg_r2", 0, 1), ("leg_r1", 180, 1), ("leg_l2", 180, -1)):
        legs[name] = rot(x=sin(32, P, ph), y=sin(14 * side, P, ph + 90), z=sin(10 * side, P, ph + 90, 0))
    return {
        "idle": loop(2.4, {
            "body": {"rotation": [sin(2, 2.4), sin(3, 4.8), "0"], "position": ["0", sin(0.15, 2.4), "0"]},
            # 骨をつつく首の動き (時々、素早く 2 回)
            "head": rot(x="math.max(0, 14 * math.sin(150 * query.anim_time) - 6) * 1.6", y=sin(10, 4.8, 30)),
            "leg_l1": rot(z=sin(-3, 2.4)), "leg_r1": rot(z=sin(3, 2.4)),
        }),
        "walk": loop(P, {
            **legs,
            "body": {"rotation": [sin(3, P / 2), sin(4, P), sin(3, P)], "position": ["0", "0.35 * math.abs(math.sin(%g * query.anim_time))" % (360 / P), "0"]},
            "head": rot(x=sin(-5, P / 2, 60), y=sin(-5, P)),
        }),
        # 噛みつき: 0.1 の引き → 0.2 で前へ跳ねて首を叩き下ろす
        "attack": once(0.6, {
            "body": rp({0: Z, 0.1: [12, 0, 0], 0.2: [-14, 0, 0], 0.35: [-10, 0, 0], 0.6: Z}, {0: Z, 0.1: [0, 0.4, 1.2], 0.2: [0, 0.2, -2.6], 0.35: [0, 0, -2.2], 0.6: Z}),
            "head": rp({0: Z, 0.1: [30, 0, 0], 0.2: [-34, 0, 0], 0.3: [-26, 0, 0], 0.6: Z}),
            "leg_l1": rp({0: Z, 0.1: [-30, 0, -10], 0.2: [30, 0, -8], 0.6: Z}),
            "leg_r1": rp({0: Z, 0.1: [-30, 0, 10], 0.2: [30, 0, 8], 0.6: Z}),
            "leg_l2": rp({0: Z, 0.2: [-28, 0, 0], 0.6: Z}),
            "leg_r2": rp({0: Z, 0.2: [-28, 0, 0], 0.6: Z}),
        }),
        "hurt": once(0.3, {
            "body": rp({0: Z, 0.07: [14, 0, 8], 0.3: Z}, {0: Z, 0.07: [0, 0.6, 1], 0.3: Z}),
            "head": rp({0: Z, 0.07: [22, 0, 0], 0.3: Z}),
        }),
        # ひっくり返って脚を縮める
        "death": once(1.0, {
            # 横へ転がって脚を縮める (裏返しは付け根の高さで浮いて見えたので、横倒しで止める)
            "body": rp({0: Z, 0.2: [0, 0, 30], 0.45: [0, 0, 92], 0.55: [0, 0, 80], 0.7: [0, 0, 88], 1.0: [0, 0, 86]},
                       {0: Z, 0.2: [0, 0.6, 0], 0.45: [0, 0, 0], 1.0: [0, -0.4, 0]}),
            "leg_l1": rp({0: Z, 0.5: [30, 0, 40], 0.8: [20, 0, 50], 1.0: [24, 0, 46]}),
            "leg_l2": rp({0: Z, 0.5: [-30, 0, 40], 0.8: [-20, 0, 50], 1.0: [-24, 0, 46]}),
            "leg_r1": rp({0: Z, 0.5: [30, 0, -40], 0.8: [20, 0, -50], 1.0: [24, 0, -46]}),
            "leg_r2": rp({0: Z, 0.5: [-30, 0, -40], 0.8: [-20, 0, -50], 1.0: [-24, 0, -46]}),
            "head": rp({0: Z, 0.5: [30, 0, 0], 1.0: [36, 0, 0]}),
        }, hold=True),
    }


# ---------------------------------------------------------------- 骨笛: 浮かぶ頭骨。予告 1.5 秒で顎を大きく開けて鳴らす
def bone_flute():
    return {
        "idle": loop(4.0, {"head": {"position": ["0", sin(0.8, 2.0), "0"], "rotation": [sin(4, 2.0, 90), sin(8, 4.0), sin(5, 4.0, 45)]},
                            "jaw": rot(x=sin(5, 2.0, 0, 6)), "tail": rot(x=sin(10, 2.0, 120), y=sin(18, 4.0, 60))}),
        "walk": loop(2.0, {"head": {"position": ["0", sin(0.6, 1.0), "0"], "rotation": [sin(6, 1.0, 90, -6), sin(6, 2.0), sin(8, 2.0, 45)]},
                           "jaw": rot(x=sin(6, 1.0, 0, 6)), "tail": rot(x=sin(16, 1.0, 120, 10), y=sin(24, 2.0, 60))}),
        # 予告 (1.5 秒): 頭を反らせて顎を開き、3 度鳴らす。最後に顎を大きく開いて止め、閉じる
        "call": once(1.7, {
            "head": rp({0: Z, 0.3: [26, 0, 0], **shake(0.4, 1.3, 0.1, 5, 1, (26, 0, 0)), 1.45: [32, 0, 0], 1.55: [-10, 0, 0], 1.7: Z},
                       {0: Z, 0.3: [0, 0.6, 0], 1.45: [0, 1.2, 0], 1.55: [0, -0.4, 0], 1.7: Z}),
            "jaw": rp({0: Z, 0.3: [40, 0, 0], 0.45: [16, 0, 0], 0.65: [44, 0, 0], 0.8: [16, 0, 0], 1.0: [46, 0, 0], 1.15: [18, 0, 0],
                       1.45: [58, 0, 0], 1.55: [8, 0, 0], 1.7: [6, 0, 0]}),
            "tail": rp({0: Z, 0.3: [-30, 0, 0], **shake(0.4, 1.4, 0.1, 18, 1, (-30, 0, 0)), 1.7: Z}),
        }),
        "hurt": once(0.3, {"head": rp({0: Z, 0.06: [-16, 10, 12], 0.3: Z}, {0: Z, 0.06: [0, 0.5, 1.2], 0.3: Z}),
                            "jaw": rp({0: Z, 0.06: [30, 0, 0], 0.3: [6, 0, 0]})}),
        # 鳴き終わったように顎が外れて、回りながら落ちる
        "death": once(1.2, {
            "head": rp({0: Z, 0.2: [-20, 0, 10], 0.8: [40, 90, 70], 1.2: [60, 120, 90]}, {0: Z, 0.2: [0, 1.2, 0], 0.8: [0, -4, 0], 1.2: [0, -5.5, 0]}),
            "jaw": rp({0: Z, 0.2: [50, 0, 0], 1.2: [62, 0, 14]}),
            "tail": rp({0: Z, 0.5: [40, 30, 0], 1.2: [60, 40, 0]}),
        }, hold=True),
    }


# ---------------------------------------------------------------- 潮だまりの手: 溜まりから伸びた四本の腕。予告の輪で開き、1.5 秒で閉じて掴む
ARMS = {"a1": (-2.4, 0.0), "a2": (2.0, 0.8), "a3": (-0.8, -2.0), "a4": (2.8, -1.6)}


def outward(name, deg):
    """溜まりの中心から外へ倒す回転 (x と z に分ける)。"""
    x, z = ARMS[name]
    l = math.hypot(x, z) or 1
    # z が前 (-) の腕は前へ倒れる (x の +)、x が左 (-) の腕は z の - で外へ
    return [-z / l * deg, 0, -x / l * deg]


def tide_hands():
    idle = {}
    walk = {}
    for i, n in enumerate(ARMS):
        ph = i * 83
        idle[n] = rot(x=sin(9, 3.0, ph), y=sin(14, 6.0, ph), z=sin(7, 3.0, ph + 60))
        walk[n] = rot(x=sin(12, 1.5, ph), y=sin(16, 3.0, ph), z=sin(10, 1.5, ph + 60))
    idle["pool"] = {"position": ["0", sin(0.15, 3.0), "0"]}
    grasp = {}
    snap = {}
    for i, n in enumerate(ARMS):
        o = outward(n, 34)
        inn = outward(n, -38)
        tremble = {round(0.75 + k * 0.09, 3): [o[0] + (2.5 if k % 2 else -2.5), 0, o[2] + (2.5 if k % 2 else -2.5)] for k in range(7)}
        grasp[n] = rp({0: Z, 0.6: o, **tremble, 1.4: o, 1.5: inn, 1.62: [v * 0.9 for v in inn], 2.0: Z},
                      {0: Z, 0.6: [0, 1.4, 0], 1.4: [0, 1.6, 0], 1.5: [0, -0.4, 0], 2.0: Z})
        snap[n] = rp({0: Z, 0.1: inn, 0.5: Z})
    grasp["pool"] = rp(None, {0: Z, 1.45: [0, 0.2, 0], 1.5: [0, -0.5, 0], 2.0: Z})
    hurt = {n: rp({0: Z, 0.07: outward(n, 20), 0.3: Z}) for n in ARMS}
    death = {n: rp({0: Z, 0.3: outward(n, 30), 1.0: outward(n, 70)}, {0: Z, 0.3: [0, 0.5, 0], 1.0: [0, -7.5, 0], 1.3: [0, -8.5, 0]}) for n in ARMS}
    death["pool"] = rp(None, {0: Z, 1.3: [0, -1.4, 0]})
    return {
        "idle": loop(6.0, idle),
        "walk": loop(3.0, walk),
        "grasp": once(2.0, grasp),
        "attack": once(0.5, snap),
        "hurt": once(0.3, hurt),
        "death": once(1.3, death, hold=True),
    }


# ---------------------------------------------------------------- 白い貝守: 殻にこもる貝。開いている間だけ身と鋏が出る
def white_shell_keeper():
    # 閉じる: 殻が地面まで下りて身にかぶさる (殻を下げ、子の身は同じだけ上げて元の高さに残す)。鋏は殻の下へたたむ
    shut = {"shell": rp({0: [0, 0, 0], 1.0: [0, 0, 0]}, {0: [0, -2.0, 0], 1.0: [0, -2.0, 0]}),
            "body": rp({0: [0, 0, 0], 1.0: [0, 0, 0]}, {0: [0, 1.6, 0.6], 1.0: [0, 1.6, 0.6]}),
            "claw_l": rp({0: [0, 50, 0], 1.0: [0, 50, 0]}, {0: [0.6, 0, 2.4], 1.0: [0.6, 0, 2.4]}),
            "claw_r": rp({0: [0, -50, 0], 1.0: [0, -50, 0]}, {0: [-0.6, 0, 2.4], 1.0: [-0.6, 0, 2.4]})}
    opened = {"body": {"rotation": [sin(3, 1.6), sin(5, 3.2), "0"], "position": ["0", sin(0.25, 1.6, 0, 0.3), "0"]},
              "claw_l": rot(x=sin(10, 0.8, 0, -8), y=sin(8, 1.6, 0, -6), z=sin(4, 0.8)),
              "claw_r": rot(x=sin(10, 0.8, 180, -8), y=sin(8, 1.6, 180, 6), z=sin(-4, 0.8))}
    return {
        "idle": loop(3.0, {"shell": {"rotation": [sin(1.5, 3.0), "0", sin(1.5, 6.0)], "position": ["0", sin(0.12, 3.0), "0"]}}),
        "walk": loop(0.8, {"shell": {"rotation": [sin(2, 0.4), sin(4, 0.8), sin(3, 0.8)], "position": ["0", "0.3 * math.abs(math.sin(450 * query.anim_time))", "0"]}}),
        "shut": loop(1.0, shut),
        "open": loop(1.6, opened),
        "attack": once(0.55, {
            "claw_l": rp({0: Z, 0.12: [-40, -20, 0], 0.22: [20, 18, 0], 0.55: Z}),
            "claw_r": rp({0: Z, 0.12: [-40, 20, 0], 0.22: [20, -18, 0], 0.55: Z}),
            "body": rp({0: Z, 0.12: [10, 0, 0], 0.22: [-10, 0, 0], 0.55: Z}, {0: Z, 0.22: [0, 0, -1.4], 0.55: Z}),
        }),
        "hurt": once(0.3, {"shell": rp({0: Z, 0.07: [8, 0, 6], 0.3: Z}, {0: Z, 0.07: [0, -0.4, 0], 0.3: Z})}),
        "death": once(1.2, {
            # 殻ごと前へ傾いて沈み、鋏がだらりと伸びる
            "shell": rp({0: Z, 0.3: [-6, 0, 10], 0.7: [12, 0, 18], 0.85: [9, 0, 16], 1.2: [12, 0, 20]}, {0: Z, 0.3: [0, 0.4, 0], 0.7: [0, -1.2, 0], 1.2: [0, -1.5, 0]}),
            "claw_l": rp({0: Z, 0.7: [40, 0, 30], 1.2: [50, 0, 40]}),
            "claw_r": rp({0: Z, 0.7: [40, 0, -30], 1.2: [50, 0, -40]}),
        }, hold=True),
    }


# ---------------------------------------------------------------- 帯電の羽虫: 速い羽ばたき。体当たりで腹を前へ曲げる
def charged_moth():
    F = 0.2
    fly = {"wing_l": rot(z=sin(-48, F, 0, -8), y=sin(10, F, 90)), "wing_r": rot(z=sin(48, F, 0, 8), y=sin(-10, F, 90)),
           "body": {"rotation": [sin(4, F * 4, 0, -4), "0", sin(4, F * 8)], "position": ["0", sin(0.4, F * 4), "0"]},
           "abd": rot(x=sin(8, F * 4, 90, 6)), "head": rot(x=sin(4, F * 4, 30))}
    return {
        "idle": loop(F * 8, fly),
        "walk": loop(F * 8, {**fly, "body": {"rotation": [sin(4, F * 4, 0, -14), "0", sin(6, F * 8)], "position": ["0", sin(0.4, F * 4), "0"]}}),
        "attack": once(0.45, {
            "body": rp({0: Z, 0.1: [16, 0, 0], 0.2: [-26, 0, 0], 0.45: Z}, {0: Z, 0.1: [0, 0.4, 1.2], 0.2: [0, -0.2, -2.4], 0.45: Z}),
            "abd": rp({0: Z, 0.1: [-30, 0, 0], 0.2: [60, 0, 0], 0.32: [54, 0, 0], 0.45: Z}),
            "wing_l": rp({0: Z, 0.2: [0, -30, -70], 0.45: Z}), "wing_r": rp({0: Z, 0.2: [0, 30, 70], 0.45: Z}),
        }),
        "hurt": once(0.25, {"body": rp({0: Z, 0.05: [20, 0, 24], 0.25: Z}), "abd": rp({0: Z, 0.05: [-20, 0, 0], 0.25: Z})}),
        "death": once(1.0, {
            "body": rp({0: Z, 0.3: [30, 120, 40], 0.7: [70, 300, 80], 1.0: [80, 360, 90]}, {0: Z, 0.3: [0, -0.8, 0], 1.0: [0, -2.0, 0]}),
            "wing_l": rp({0: Z, 0.2: [0, 0, 60], 1.0: [0, 20, 80]}), "wing_r": rp({0: Z, 0.2: [0, 0, -60], 1.0: [0, -20, -80]}),
            "abd": rp({0: Z, 1.0: [40, 0, 0]}),
        }, hold=True),
    }


# ---------------------------------------------------------------- 焦げた鳥の影: ゆっくり羽ばたいて上で待つ。影を落とし (1.2 秒)、翼をたたんで急降下して掴む
def scorched_bird():
    P = 1.1
    # 左の翼は肘で折れて垂れている (メッシュの形)。羽ばたくのは右だけで、左は引きつるように小さく動く
    glide = {"wing_l": rot(z=sin(-5, P / 2, 30), x=sin(3, P, 90)), "wing_r": rot(z=sin(28, P, 0, 4), x=sin(4, P, 90)),
             "body": {"rotation": [sin(3, P, 180), "0", sin(4, P * 2)], "position": ["0", sin(0.7, P, 180), "0"]},
             "head": rot(x=sin(-5, P, 200), y=sin(14, P * 3)), "tail": rot(x=sin(6, P, 60), y=sin(8, P * 2)),
             "leg_l": rot(x="30"), "leg_r": rot(x="30")}
    return {
        "idle": loop(P * 6, glide),
        "walk": loop(P * 6, glide),
        # 影を落とす予告 (1.2 秒 = WARN_SHADOW 24): 翼を高く広げて首を下へ、獲物を見下ろす
        "shadow": once(1.3, {
            "wing_l": rp({0: Z, 0.4: [0, 0, -14], 0.8: [0, 0, -10], 1.2: [0, 0, -16], 1.3: [0, 0, -12]}),
            "wing_r": rp({0: Z, 0.4: [0, 0, 50], 0.8: [0, 0, 44], 1.2: [0, 0, 56], 1.3: [0, 0, 40]}),
            "head": rp({0: Z, 0.4: [-40, 0, 0], 0.7: [-36, 12, 0], 1.0: [-36, -12, 0], 1.2: [-44, 0, 0], 1.3: [-40, 0, 0]}),
            "body": rp({0: Z, 0.4: [-14, 0, 0], 1.2: [-20, 0, 0], 1.3: [-24, 0, 0]}, {0: Z, 0.4: [0, 0.6, 0], 1.3: [0, 0.8, 0]}),
            "tail": rp({0: Z, 0.4: [20, 0, 0], 1.3: [26, 0, 0]}),
            "leg_l": rp({0: [30, 0, 0], 1.3: [-30, 0, 0]}), "leg_r": rp({0: [30, 0, 0], 1.3: [-30, 0, 0]}),
        }),
        # 急降下 (2 秒 = 40 tick): 翼を後ろへたたみ、頭から落ちる。最後に脚を前へ出す
        "dive": once(2.0, {
            "wing_l": rp({0: [0, 0, -40], 0.2: [0, -60, 30], 1.6: [0, -64, 34], 2.0: Z}),
            "wing_r": rp({0: [0, 0, 40], 0.2: [0, 60, -30], 1.6: [0, 64, -34], 2.0: Z}),
            "body": rp({0: [-24, 0, 0], 0.2: [-46, 0, 0], **shake(0.3, 1.5, 0.1, 2, 2, (-46, 0, 0)), 1.6: [-30, 0, 0], 2.0: Z}),
            "head": rp({0: [-40, 0, 0], 0.2: [10, 0, 0], 1.6: [10, 0, 0], 2.0: Z}),
            "tail": rp({0: [26, 0, 0], 0.2: [10, 0, 0], 2.0: Z}),
            "leg_l": rp({0: [-30, 0, 0], 0.2: [40, 0, 0], 1.2: [40, 0, 0], 1.5: [-60, 0, 0], 2.0: [30, 0, 0]}),
            "leg_r": rp({0: [-30, 0, 0], 0.2: [40, 0, 0], 1.2: [40, 0, 0], 1.5: [-60, 0, 0], 2.0: [30, 0, 0]}),
        }),
        "attack": once(0.5, {
            "leg_l": rp({0: [30, 0, 0], 0.1: [-70, 0, 0], 0.3: [-50, 0, 0], 0.5: [30, 0, 0]}),
            "leg_r": rp({0: [30, 0, 0], 0.1: [-70, 0, 0], 0.3: [-50, 0, 0], 0.5: [30, 0, 0]}),
            "wing_l": rp({0: Z, 0.1: [0, 0, -60], 0.5: Z}), "wing_r": rp({0: Z, 0.1: [0, 0, 60], 0.5: Z}),
            "head": rp({0: Z, 0.1: [-30, 0, 0], 0.5: Z}),
        }),
        "hurt": once(0.3, {"body": rp({0: Z, 0.07: [14, 0, 18], 0.3: Z}), "head": rp({0: Z, 0.07: [24, 0, 0], 0.3: Z}),
                           "wing_l": rp({0: Z, 0.07: [0, 0, 30], 0.3: Z}), "wing_r": rp({0: Z, 0.07: [0, 0, -30], 0.3: Z})}),
        # 翼が煤になって崩れ、きりもみで落ちる
        "death": once(1.4, {
            "body": rp({0: Z, 0.3: [-20, 0, 30], 1.0: [-70, 0, 200], 1.4: [-80, 0, 260]}, {0: Z, 0.3: [0, 0.5, 0], 1.0: [0, -2.5, 0], 1.4: [0, -3.5, 0]}),
            "wing_l": rp({0: Z, 0.3: [0, 0, -60], 1.4: [0, 30, 40]}), "wing_r": rp({0: Z, 0.3: [0, 0, 70], 1.4: [0, -30, -30]}),
            "head": rp({0: Z, 0.3: [30, 0, 0], 1.4: [40, 0, 20]}),
            "tail": rp({0: Z, 1.4: [-30, 20, 0]}),
        }, hold=True),
    }


def _interp(keys, t):
    """{"秒": {"vector": [...]}} の線形補間 (x だけ)。"""
    ts = sorted((float(k), v["vector"][0]) for k, v in keys.items())
    if t <= ts[0][0]:
        return ts[0][1]
    for (t0, v0), (t1, v1) in zip(ts, ts[1:]):
        if t0 <= t <= t1:
            return v0 + (v1 - v0) * (t - t0) / ((t1 - t0) or 1)
    return ts[-1][1]


def counter_legs(anims, skip=("death",)):
    """脚は胴の子なので、胴を前後に傾けると脚も一緒に倒れて、像ごと板のように傾く。
    倒れる動き以外は、脚の x 回転から胴の x 回転を引いて、脚が地面に立ったままになるようにする。"""
    for name, a in anims.items():
        if name in skip:
            continue
        bones = a["bones"]
        body = bones.get("body", {}).get("rotation")
        if body is None:
            continue
        for leg in ("leg_l", "leg_r"):
            lr = bones.setdefault(leg, {}).get("rotation")
            if isinstance(body, list):
                bx = body[0]
                lx = lr[0] if isinstance(lr, list) else "0"
                rest = lr[1:] if isinstance(lr, list) else ["0", "0"]
                bones[leg]["rotation"] = ["(%s) - (%s)" % (lx, bx)] + list(rest)
            else:
                times = sorted(set(float(k) for k in body) | (set(float(k) for k in lr) if isinstance(lr, dict) else set()))
                out = {}
                for t in times:
                    lv = [0, 0, 0]
                    if isinstance(lr, dict):
                        lv = [_interp(lr, t), 0, 0]
                        # y, z は元の近いキーのまま
                        near = min(lr, key=lambda k: abs(float(k) - t))
                        lv[1], lv[2] = lr[near]["vector"][1], lr[near]["vector"][2]
                    out[t] = [lv[0] - _interp(body, t), lv[1], lv[2]]
                bones[leg]["rotation"] = kf(out)
    return anims


KINDS = {
    "bone_picker": bone_picker, "rib_guard": rib_guard, "bone_flute": bone_flute,
    "salt_husk": salt_husk, "tide_hands": tide_hands, "white_shell_keeper": white_shell_keeper,
    "charged_moth": charged_moth, "lightning_idol": lightning_idol, "scorched_bird": scorched_bird,
}


HUMANS = ("rib_guard", "salt_husk", "lightning_idol")
# 潮だまりの手の回転は outward() (溜まりの中心から外へ) で作ってあり、ゲームで外へ開くのを確かめた向きなので反転しない
NO_FLIP = ("tide_hands",)


def flip_x(anims):
    """回転の x を反転する (上の向きの約束 → ゲームの向き)。"""
    for a in anims.values():
        for b in a["bones"].values():
            r = b.get("rotation")
            if isinstance(r, list):
                x = r[0]
                b["rotation"] = [("-(%s)" % x) if isinstance(x, str) and x not in ("0", "") else (-x if not isinstance(x, str) else x)] + r[1:]
            elif isinstance(r, dict):
                for k in r.values():
                    k["vector"][0] = -k["vector"][0] if k["vector"][0] else 0
    return anims


def write_all():
    os.makedirs(OUT, exist_ok=True)
    for kind, fn in KINDS.items():
        made = fn()
        if kind in HUMANS:
            counter_legs(made)
        if kind not in NO_FLIP:
            flip_x(made)
        anims = {"animation.%s.%s" % (kind, k): v for k, v in made.items()}
        doc = {"format_version": "1.8.0", "animations": anims}
        p = os.path.join(OUT, "abyss_%s_s1.animation.json" % kind)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            json.dump(doc, f, indent=1, ensure_ascii=False)
            f.write("\n")
        print(p, len(anims))


if __name__ == "__main__":
    write_all()
