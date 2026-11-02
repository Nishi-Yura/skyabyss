#!/usr/bin/env python3
"""大陸の新しい敵 8 体 (docs/design/44、一体一案で選ばれた形) をゲームへ入れる。

  python tools/gen/foes/gen_foes_one.py

入力: docs/review/foes_one/<id>/a/<id>_a_{mesh.json,atlas.png,glowmask.png}  (形の作り方は tools/rough/lowpoly/foes/)
出力 (src/main/resources/assets/ の下):
  skyabyss/lowpoly/<id>.json, skyabyss/textures/lowpoly/<id>(.png|_glowmask.png), skyabyss/geo/entity/<id>_lowpoly(.geo|_debug.geo).json
  skyabyss/geo/entity/<id>.geo.json + skyabyss/textures/entity/<id>.png   LowPoly.enabled = false のときの代用
  skyabyss/animations/entity/<id>.animation.json   このファイルで作る。予告・技の姿勢はラフの POSES をそのまま使う (見せた絵と同じ構え)
  skyabyss/sounds.json の行 (バニラ / 既存の音の別名) と字幕 (skyabyss_mobsounds)、名前 (skyabyss_foes)、スポーンエッグのモデル

回転の約束は一章と同じ (ラフの姿勢 = 数学の右手系、前は -Z。ファイルへは (-x, -y, z))。
"""
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
R = os.path.join(ROOT, "docs", "review", "foes_one")
sys.path.insert(0, os.path.join(ROOT, "tools", "rough", "lowpoly", "foes"))
MOBS = ["bandit", "bandit_archer", "bandit_chief", "bone_footman", "bone_bowman", "bone_standard", "grain_rat", "stray_wisp",
        "feral_dog", "moss_dead", "mist_angler", "thunder_wolf",
        "rock_boar", "tide_crab", "storm_ram", "ash_lizard"]


def wjson(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


# ---------------------------------------------------------------- 動きの部品 (一章の生成と同じ書き方)
def neg(expr):
    return "-(%s)" % expr


def rot(x=0, y=0, z=0):
    return [-x if x else 0, -y if y else 0, z]


def S(phase, amp, speed=360):
    return "math.sin(query.anim_time * %d + %d) * %s" % (speed, phase, amp)


def loop(length, bones):
    return {"loop": True, "animation_length": length, "bones": bones}


def once(length, bones, hold=False):
    d = {"animation_length": length, "bones": bones}
    if hold:
        d["loop"] = "hold_on_last_frame"
    return d


def kf(frames, rotation=True):
    out = {}
    for t, v in frames.items():
        out["%.2f" % t] = rot(*v) if rotation else list(v)
    return out


def bone(rotation=None, position=None, scale=None):
    d = {}
    if rotation:
        d["rotation"] = rotation
    if position:
        d["position"] = position
    if scale:
        d["scale"] = scale
    return d


def wobble(base, extra):
    """静止の角度 base (数学の向き) に、x へ molang の揺れ extra を足した回転 (ファイルの値)"""
    x, y, z = base
    return [neg("%s + %s" % (x, extra)), str(-y) if y else "0", str(z)]


def seq(frames):
    """[(時刻, 姿勢 dict)] -> 骨ごとのキーフレーム。姿勢に無い骨は 0"""
    names = sorted({b for _, p in frames for b in p})
    return {b: bone(kf({t: p.get(b, (0, 0, 0)) for t, p in frames})) for b in names}


def merge(*poses):
    out = {}
    for p in poses:
        out.update(p)
    return out


def human_set(prefix, poses, step=1.0, hurt_lean=6, fall=9.5):
    """人型の待機・歩き・のけぞり・倒れ。待機はラフの idle の構えに息を足す"""
    idle_p = poses["idle"]
    idle = {"body": bone(wobble(idle_p.get("body", (0, 0, 0)), S(0, 1.2, 120)), ["0", S(0, 0.3, 120), "0"]),
            "head": bone(wobble(idle_p.get("head", (0, 0, 0)), S(60, 2.5, 120)))}
    for b in ("left_arm", "right_arm", "left_forearm", "right_forearm"):
        if b in idle_p:
            idle[b] = bone(wobble(idle_p[b], S(40 if "left" in b else -40, 2.0, 120)))
    walk = {"leg_l": bone([neg(S(0, 30)), "0", "0"]), "leg_r": bone([neg(S(0, -30)), "0", "0"]),
            "body": bone(wobble(idle_p.get("body", (0, 0, 0)), "-2"), ["0", "math.abs(" + S(0, 0.7) + ")", "0"]),
            "head": bone(wobble(idle_p.get("head", (0, 0, 0)), S(180, 2)))}
    for b, sgn in (("left_arm", -1), ("right_arm", 1)):
        walk[b] = bone(wobble(idle_p.get(b, (0, 0, 0)), S(0, 18 * sgn)))
    for b in ("left_forearm", "right_forearm"):
        if b in idle_p:
            walk[b] = bone(wobble(idle_p[b], "0"))
    hurt = {"body": bone(kf({0: idle_p.get("body", (0, 0, 0)), 0.08: (-hurt_lean, 0, 0), 0.3: idle_p.get("body", (0, 0, 0))})),
            "head": bone(kf({0: idle_p.get("head", (0, 0, 0)), 0.08: (12, 0, 0), 0.3: idle_p.get("head", (0, 0, 0))}))}
    # 倒れる: 膝をつき、前へ崩れる
    death = {"body": bone(kf({0: (0, 0, 0), 0.5: (-20, 0, 0), 1.0: (-85, 0, 0)}), kf({0: (0, 0, 0), 0.5: (0, -fall / 2, 0), 1.0: (0, -fall, 0)}, False)),
             "head": bone(kf({0: (0, 0, 0), 0.6: (-25, 0, 0), 1.0: (-15, 0, 0)})),
             "leg_l": bone(kf({0: (0, 0, 0), 0.5: (50, 0, 0), 1.0: (20, 0, 0)})), "leg_r": bone(kf({0: (0, 0, 0), 0.5: (30, 0, 0), 1.0: (10, 0, 0)})),
             "left_arm": bone(kf({0: (0, 0, 0), 0.6: (40, 0, 0), 1.0: (70, 0, 0)})), "right_arm": bone(kf({0: (0, 0, 0), 0.6: (40, 0, 0), 1.0: (70, 0, 0)}))}
    p = "animation.%s." % prefix
    return {p + "idle": loop(3.0, idle), p + "walk": loop(step, walk), p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


def bones_collapse(prefix, anims):
    """骸の倒れ: 膝から崩れて骨の山になる (人の倒れより低く、早い)"""
    p = "animation.%s." % prefix
    anims[p + "death"] = once(0.8, {
        "body": bone(kf({0: (0, 0, 0), 0.3: (-30, 0, 8), 0.8: (-80, 0, 20)}), kf({0: (0, 0, 0), 0.3: (0, -4, 0), 0.8: (0, -10.5, 0)}, False)),
        "head": bone(kf({0: (0, 0, 0), 0.4: (-30, 20, 0), 0.8: (-50, 40, 10)})),
        "leg_l": bone(kf({0: (0, 0, 0), 0.3: (70, 0, 0), 0.8: (80, 0, -10)})), "leg_r": bone(kf({0: (0, 0, 0), 0.3: (60, 0, 0), 0.8: (85, 0, 10)})),
        "left_arm": bone(kf({0: (0, 0, 0), 0.5: (20, 0, 30), 0.8: (10, 0, 60)})), "right_arm": bone(kf({0: (0, 0, 0), 0.5: (20, 0, -30), 0.8: (10, 0, -60)}))}, True)
    # 旗のそばで起き上がる (0.9 秒): 崩れかけて、ぎくしゃくと立ち直る
    anims[p + "rise"] = once(0.9, {
        "body": bone(kf({0: (0, 0, 0), 0.2: (-40, 0, 10), 0.5: (-40, 0, 10), 0.9: (0, 0, 0)}), kf({0: (0, 0, 0), 0.2: (0, -6, 0), 0.5: (0, -6, 0), 0.9: (0, 0, 0)}, False)),
        "head": bone(kf({0: (0, 0, 0), 0.2: (-30, 30, 0), 0.6: (10, -10, 0), 0.9: (0, 0, 0)})),
        "leg_l": bone(kf({0: (0, 0, 0), 0.2: (60, 0, 0), 0.5: (60, 0, 0), 0.9: (0, 0, 0)})), "leg_r": bone(kf({0: (0, 0, 0), 0.2: (50, 0, 0), 0.5: (50, 0, 0), 0.9: (0, 0, 0)}))})


# ---------------------------------------------------------------- 8 体の動き
def bandit():
    import bandit as M
    P = M.POSES
    a = human_set("bandit", P, 0.8)
    # 斬り下ろし (予告 0.6 秒): 鉈を頭の後ろへ振りかぶる -> 0.72 で斜めに斬り下ろす
    a["animation.bandit.swing"] = once(1.4, seq([(0, P["idle"]), (0.5, P["telegraph"]), (0.6, P["telegraph"]), (0.72, P["attack"]), (1.0, P["attack"]), (1.4, P["idle"])]))
    return a


def bandit_archer():
    import bandit_archer as M
    P = M.POSES
    a = human_set("bandit_archer", P, 0.8)
    loose = merge(P["telegraph"], {"right_arm": (95, 0, 40), "right_forearm": (0, 30, 0), "body": (-4, 64, 0)})
    # 射る (予告 0.8 秒): 体を横へ開き、弓を前へ、弦を引く -> 0.8 で放つ -> 後ろへ跳ぶ
    a["animation.bandit_archer.shoot"] = once(1.5, seq([(0, P["idle"]), (0.55, P["telegraph"]), (0.8, P["telegraph"]), (0.86, loose), (1.1, loose), (1.5, P["idle"])]))
    return a


def bandit_chief():
    import bandit_chief as M
    P = M.POSES
    a = human_set("bandit_chief", P, 1.0, fall=11.5)
    through = {"body": (-6, -40, 0), "right_arm": (70, -50, -20), "right_forearm": (-10, 0, 0), "left_arm": (30, -20, 10), "leg_l": (16, 0, 0), "leg_r": (-12, 0, 0)}
    # 薙ぎ (予告 0.8 秒): 斧を後ろへ大きく引く -> 0.92 で前を薙ぐ
    a["animation.bandit_chief.sweep"] = once(1.7, seq([(0, P["idle"]), (0.65, P["telegraph"]), (0.8, P["telegraph"]), (0.95, through), (1.25, through), (1.7, P["idle"])]))
    # 叫ぶ: 斧を掲げて吠える (まわりの野盗が速くなる)
    a["animation.bandit_chief.shout"] = once(1.8, seq([(0, P["idle"]), (0.45, P["shout"]), (1.35, P["shout"]), (1.8, P["idle"])]))
    return a


def bone_footman():
    import bone_footman as M
    P = M.POSES
    a = human_set("bone_footman", P, 1.0)
    thrust = merge(P["idle"], {"body": (-16, 0, 0), "right_arm": (85, 0, 0), "right_forearm": (0, 0, 0), "leg_l": (-22, 0, 0), "leg_r": (24, 0, 0)})
    # 突き (予告 0.6 秒): 盾を構えたまま剣を引く -> 0.7 で短く突く
    a["animation.bone_footman.thrust"] = once(1.3, seq([(0, P["idle"]), (0.5, P["telegraph"]), (0.6, P["telegraph"]), (0.7, thrust), (0.95, thrust), (1.3, P["idle"])]))
    # 盾が受ける
    a["animation.bone_footman.block"] = once(0.35, seq([(0, P["idle"]), (0.08, merge(P["idle"], {"left_arm": (55, 30, 0), "body": (-10, 0, 0)})), (0.35, P["idle"])]))
    bones_collapse("bone_footman", a)
    return a


def bone_bowman():
    import bone_bowman as M
    P = M.POSES
    a = human_set("bone_bowman", P, 1.0)
    # 弧の矢 (予告 1.0 秒): 弓を斜め上へ向けて引く -> 0.8 で放つ
    up = merge(P["telegraph"], {"left_arm": (130, 0, -10), "right_arm": (125, 0, 20), "head": (20, -60, 0)})
    loose = merge(up, {"right_arm": (115, 0, 45), "right_forearm": (0, 30, 0)})
    a["animation.bone_bowman.shoot"] = once(1.4, seq([(0, P["idle"]), (0.6, up), (0.8, up), (0.86, loose), (1.05, loose), (1.4, P["idle"])]))
    # 弓で殴る (予告 0.5 秒): 弓を振りかぶって横に払い、下がる
    wind = {"body": (0, -35, 0), "left_arm": (100, 60, 0), "left_forearm": (-20, 0, 0), "right_arm": (20, 0, -10)}
    hit = {"body": (-6, 35, 0), "left_arm": (85, -50, 0), "left_forearm": (-10, 0, 0), "right_arm": (20, 0, -10), "leg_l": (-14, 0, 0), "leg_r": (14, 0, 0)}
    a["animation.bone_bowman.bash"] = once(1.1, seq([(0, P["idle"]), (0.4, wind), (0.5, wind), (0.6, hit), (0.8, hit), (1.1, P["idle"])]))
    bones_collapse("bone_bowman", a)
    return a


def bone_standard():
    import bone_standard as M
    P = M.POSES
    a = human_set("bone_standard", P, 1.15, fall=11)
    through = {"body": (-4, -45, 0), "right_arm": (80, -60, -20), "right_forearm": (-10, 0, 0), "left_arm": (20, 20, 20), "leg_l": (16, 0, 0), "leg_r": (-12, 0, 0)}
    # 薙ぎ (予告 0.7 秒)
    a["animation.bone_standard.sweep"] = once(1.6, seq([(0, P["idle"]), (0.55, P["telegraph"]), (0.7, P["telegraph"]), (0.84, through), (1.15, through), (1.6, P["idle"])]))
    # 旗を突き立てる (予告 1.2 秒): 両手を上げて旗竿を抜き上げ (背の旗が 6 上がる) -> 1.2 で地へ突き下ろす
    plant = seq([(0, P["idle"]), (0.8, P["plant"]), (1.1, P["plant"]), (1.25, merge(P["plant"], {"left_arm": (60, 0, -20), "right_arm": (60, 0, 20), "body": (-4, 0, 0)})),
                 (1.9, merge(P["plant"], {"left_arm": (60, 0, -20), "right_arm": (60, 0, 20), "body": (-4, 0, 0)})), (2.3, P["idle"])])
    plant["banner"] = bone(kf({0: (0, 0, 0), 0.8: (-8, 0, 0), 1.1: (-8, 0, 0), 1.25: (4, 0, 0), 2.3: (0, 0, 0)}),
                           kf({0: (0, 0, 0), 0.8: (0, 6, -1), 1.1: (0, 7, -1), 1.25: (0, -5, -1), 1.9: (0, -5, -1), 2.3: (0, 0, 0)}, False))
    a["animation.bone_standard.plant"] = once(2.3, plant)
    idle = a["animation.bone_standard.idle"]["bones"]
    idle["banner"] = bone([neg(S(0, 2, 90)), "0", S(90, 1.5, 70)])
    bones_collapse("bone_standard", a)
    return a


def grain_rat():
    import grain_rat as M
    P = M.POSES
    p = "animation.grain_rat."
    idle = {"body": bone(position=["0", S(0, 0.15, 160), "0"]),
            "head": bone([neg(S(0, 6, 300)), neg(S(90, 8, 140)), "0"]),  # 鼻先をひくつかせる
            "ear_l": bone([neg(S(0, 6, 200)), "0", "0"]), "ear_r": bone([neg(S(120, 6, 200)), "0", "0"]),
            "tail": bone([neg(S(0, 6, 90)), neg(S(60, 14, 90)), "0"])}
    walk = {"body": bone([neg(S(0, 6)), "0", "0"], ["0", "math.abs(" + S(0, 1.2) + ")", "0"]),
            "head": bone([neg(S(180, 6)), "0", "0"]),
            "fore_l": bone([neg(S(0, 40)), "0", "0"]), "fore_r": bone([neg(S(20, 40)), "0", "0"]),
            "hind_l": bone([neg(S(180, 46)), "0", "0"]), "hind_r": bone([neg(S(200, 46)), "0", "0"]),
            "tail": bone([neg(S(90, 10) + " - 6"), neg(S(0, 20)), "0"])}
    leap = {"body": (14, 0, 0), "head": (10, 0, 0), "fore_l": (-50, 0, 0), "fore_r": (-50, 0, 0), "hind_l": (50, 0, 0), "hind_r": (50, 0, 0), "tail": (-20, 0, 0)}
    bite = merge(leap, {"head": (-16, 0, 0), "body": (-4, 0, 0)})
    # 噛みつき (予告 0.3 秒): 身を縮める -> 跳ぶ -> 噛む -> 離れる
    act = seq([(0, {}), (0.25, P["telegraph"]), (0.3, P["telegraph"]), (0.4, leap), (0.5, bite), (0.65, bite), (0.9, {})])
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (-10, 0, 0), 0.3: (0, 0, 0)})), "ear_l": bone(kf({0: (0, 0, 0), 0.08: (-40, 0, 0), 0.3: (0, 0, 0)})),
            "ear_r": bone(kf({0: (0, 0, 0), 0.08: (-40, 0, 0), 0.3: (0, 0, 0)}))}
    # 倒れる: 横倒しになり、脚を縮める (胴の pivot は 4.6 の高さ、半幅 3.4 ぶん下げる)
    death = {"body": bone(kf({0: (0, 0, 0), 0.4: (0, 0, 90)}), kf({0: (0, 0, 0), 0.4: (0, -1.6, 0)}, False)),
             "fore_l": bone(kf({0: (0, 0, 0), 0.4: (40, 0, 0)})), "fore_r": bone(kf({0: (0, 0, 0), 0.4: (40, 0, 0)})),
             "hind_l": bone(kf({0: (0, 0, 0), 0.4: (-40, 0, 0)})), "hind_r": bone(kf({0: (0, 0, 0), 0.4: (-40, 0, 0)})),
             "tail": bone(kf({0: (0, 0, 0), 0.4: (0, 40, 0)}))}
    return {p + "idle": loop(2.0, idle), p + "walk": loop(0.4, walk), p + "bite": once(0.9, act), p + "hurt": once(0.3, hurt), p + "death": once(0.5, death, True)}


def stray_wisp():
    p = "animation.stray_wisp."
    # 浮いて揺れ、骨組みがゆっくり回り、霧の尾がなびき、光の粒がめぐる
    idle = {"core": bone(position=["0", S(0, 1.2, 90), "0"]),
            "cage": bone([neg(S(0, 6, 70)), "query.anim_time * 30", S(90, 5, 60)]),
            "tail": bone([neg(S(0, 10, 120)), "0", S(90, 8, 100)]),
            "mote_a": bone(["0", "query.anim_time * 120", "0"]), "mote_b": bone(["0", "-query.anim_time * 160", "0"])}
    move = {k: dict(v) for k, v in idle.items()}
    move["tail"] = bone([neg("25 + " + S(0, 10, 200)), "0", S(90, 6, 160)])
    # 明滅 (予告 0.6 秒): 芯が膨らむ -> 0.6 で光を吐いてしぼむ
    pulse = {"core": bone(None, kf({0: (0, 0, 0), 0.6: (0, 1.5, 0), 0.7: (0, -0.5, 0), 1.0: (0, 0, 0)}, False),
                          scale=kf({0: (1, 1, 1), 0.3: (1.15, 1.15, 1.15), 0.45: (1.05, 1.05, 1.05), 0.6: (1.45, 1.45, 1.45), 0.7: (0.8, 0.8, 0.8), 1.0: (1, 1, 1)}, False)),
             "cage": bone(kf({0: (0, 0, 0), 0.6: (0, 90, 0), 1.0: (0, 120, 0)})),
             "mote_a": bone(kf({0: (0, 0, 0), 0.6: (0, 360, 0), 1.0: (0, 400, 0)})), "mote_b": bone(kf({0: (0, 0, 0), 0.6: (0, -360, 0), 1.0: (0, -400, 0)}))}
    hurt = {"core": bone(kf({0: (0, 0, 0), 0.08: (0, 0, 20), 0.3: (0, 0, 0)}), scale=kf({0: (1, 1, 1), 0.08: (0.8, 0.8, 0.8), 0.3: (1, 1, 1)}, False))}
    # 消える: 芯がしぼみ、骨組みだけが落ちる
    death = {"core": bone(None, kf({0: (0, 0, 0), 0.6: (0, -6, 0)}, False), kf({0: (1, 1, 1), 0.2: (1.3, 1.3, 1.3), 0.6: (0.05, 0.05, 0.05)}, False)),
             "cage": bone(kf({0: (0, 0, 0), 0.6: (40, 90, 30)}))}
    return {p + "idle": loop(12.0, idle), p + "walk": loop(12.0, move), p + "pulse": once(1.0, pulse), p + "hurt": once(0.3, hurt), p + "death": once(0.6, death, True)}


# ---------------------------------------------------------------- 二組目 (野犬・苔の亡者・霧の漁り火・雷狼)
def pack_beast(prefix, P, step=0.5):
    """野犬・雷狼: 四つ足の待機・駆け足・跳びかかり・吠える・のけぞり・倒れ。予告の姿勢はラフの POSES (howl / telegraph)"""
    p = "animation.%s." % prefix
    idle_p = P["idle"]
    idle = {"body": bone(position=["0", S(0, 0.25, 120), "0"]),
            "head": bone(wobble(idle_p.get("head", (0, 0, 0)), S(0, 3, 100))),
            "jaw": bone([neg(S(0, 2, 200) + " - 2"), "0", "0"]),
            "tail": bone([neg(S(0, 4, 80) + " + %s" % idle_p.get("tail", (0, 0, 0))[0]), neg(S(90, 10, 80)), "0"]),
            "ear_l": bone([neg(S(0, 4, 90)), "0", "0"]), "ear_r": bone([neg(S(140, 4, 90)), "0", "0"])}
    walk = {"body": bone([neg(S(0, 4)), "0", "0"], ["0", "math.abs(" + S(0, 1.0) + ")", "0"]),
            "head": bone([neg(S(180, 5) + " - 6"), "0", "0"]),
            "fore_l": bone([neg(S(0, 34)), "0", "0"]), "fore_r": bone([neg(S(40, 34)), "0", "0"]),
            "hind_l": bone([neg(S(180, 34)), "0", "0"]), "hind_r": bone([neg(S(220, 34)), "0", "0"]),
            "tail": bone([neg(S(90, 8) + " - 4"), neg(S(0, 12)), "0"])}
    leap = {"body": (12, 0, 0), "head": (6, 0, 0), "jaw": (-30, 0, 0), "fore_l": (-60, 0, 0), "fore_r": (-60, 0, 0), "hind_l": (50, 0, 0), "hind_r": (50, 0, 0),
            "tail": (-10, 0, 0), "ear_l": (-30, 0, 0), "ear_r": (-30, 0, 0)}
    bite = merge(leap, {"head": (-14, 0, 0), "jaw": (0, 0, 0), "body": (-4, 0, 0)})
    # 跳びかかり (予告 0.4 秒): 身を沈める -> 跳ぶ -> 噛む
    act = seq([(0, idle_p), (0.32, P["telegraph"]), (0.4, P["telegraph"]), (0.5, leap), (0.62, bite), (0.8, bite), (1.1, idle_p)])
    # 吠える (予告 1.0〜1.2 秒): 天を仰いで吠え続け、戻る
    howl = seq([(0, idle_p), (0.4, P["howl"]), (1.2, P["howl"]), (1.5, idle_p)])
    j = P["howl"]["jaw"]
    howl["jaw"] = bone(kf({0: (0, 0, 0), 0.4: j, 0.6: (-18, 0, 0), 0.8: j, 1.0: (-20, 0, 0), 1.2: j, 1.5: (0, 0, 0)}))
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (-8, 0, 0), 0.3: (0, 0, 0)})), "head": bone(kf({0: (0, 0, 0), 0.08: (14, 0, 0), 0.3: (0, 0, 0)}))}
    # 倒れる: 横倒しになり、脚が開く
    death = {"body": bone(kf({0: (0, 0, 0), 0.45: (0, 0, 90)}), kf({0: (0, 0, 0), 0.45: (0, -4.5, 0)}, False)),
             "head": bone(kf({0: (0, 0, 0), 0.45: (-20, 0, 0)})),
             "fore_l": bone(kf({0: (0, 0, 0), 0.45: (30, 0, 0)})), "fore_r": bone(kf({0: (0, 0, 0), 0.45: (30, 0, 0)})),
             "hind_l": bone(kf({0: (0, 0, 0), 0.45: (-30, 0, 0)})), "hind_r": bone(kf({0: (0, 0, 0), 0.45: (-30, 0, 0)})),
             "tail": bone(kf({0: (0, 0, 0), 0.45: (-30, 30, 0)}))}
    return {p + "idle": loop(3.0, idle), p + "walk": loop(step, walk), p + "bite": once(1.1, act), p + "howl": once(1.5, howl),
            p + "hurt": once(0.3, hurt), p + "death": once(0.6, death, True)}


def feral_dog():
    import feral_dog as M
    return pack_beast("feral_dog", M.POSES, 0.45)


def thunder_wolf():
    import thunder_wolf as M
    return pack_beast("thunder_wolf", M.POSES, 0.5)


def moss_dead():
    import moss_dead as M
    P = M.POSES
    a = human_set("moss_dead", P, 1.4, fall=10.5)
    slam = merge(P["idle"], {"body": (-34, 10, 0), "right_arm": (20, 0, -4), "right_forearm": (-4, 0, 0), "leg_r": (24, 0, 0), "leg_l": (-6, 0, 0)})
    # 叩きつけ (予告 1.0 秒): 太い右腕を頭の上へ振りかぶる -> 1.0 で前へ叩きつける
    a["animation.moss_dead.slam"] = once(1.8, seq([(0, P["idle"]), (0.85, P["telegraph"]), (1.0, P["telegraph"]), (1.12, slam), (1.45, slam), (1.8, P["idle"])]))
    # 胞子 (予告 1.2 秒): 背を丸めて荷を突き出す -> 1.2 で撒く
    a["animation.moss_dead.spores"] = once(2.1, seq([(0, P["idle"]), (0.9, P["spores"]), (1.2, P["spores"]),
                                                      (1.3, merge(P["spores"], {"body": (-40, 0, 0), "pack": (6, 0, 0)})), (1.7, P["spores"]), (2.1, P["idle"])]))
    a["animation.moss_dead.idle"]["bones"]["pack"] = bone([neg(S(0, 1.5, 100)), "0", S(90, 1.5, 80)])
    return a


def mist_angler():
    import mist_angler as M
    P = M.POSES
    p = "animation.mist_angler."
    ip = P["idle"]
    # 脚がないので、体ごとゆらゆら浮き、霧の尾がなびき、提げた灯が揺れる
    idle = {"body": bone(wobble(ip["body"], S(0, 2, 90)), ["0", S(0, 0.8, 90), "0"]),
            "head": bone(wobble(ip["head"], S(60, 3, 70))),
            "right_arm": bone(wobble(ip["right_arm"], S(30, 2, 90))), "right_forearm": bone(wobble(ip["right_forearm"], "0")),
            "weapon": bone(wobble(ip["weapon"], S(0, 2, 90))),
            "lantern": bone([neg(S(200, 8, 90) + " + %s" % ip["lantern"][0]), "0", S(0, 6, 70)]),
            "left_arm": bone(wobble(ip["left_arm"], S(-40, 2, 90))), "left_forearm": bone(wobble(ip["left_forearm"], "0")),
            "tail": bone([neg(S(0, 8, 120) + " + %s" % ip["tail"][0]), "0", S(90, 6, 100)])}
    move = {k: dict(v) for k, v in idle.items()}
    move["tail"] = bone([neg("-30 + " + S(0, 10, 200)), "0", S(90, 6, 160)])
    move["body"] = bone(wobble((-18, 0, 0), S(0, 2, 160)), ["0", S(0, 0.6, 160), "0"])
    throw = {"body": (-12, -30, 0), "head": (0, 10, 0), "left_arm": (70, 30, -10), "left_forearm": (-6, 0, 0), "right_arm": (20, 0, -10), "weapon": (-30, 0, 0),
             "lantern": (14, 0, 0), "tail": (-20, 0, -10)}
    # 網を投げる (予告 1.0 秒): 左手の網を後ろへ振りかぶる -> 1.0 で投げる
    net = seq([(0, ip), (0.85, P["telegraph"]), (1.0, P["telegraph"]), (1.1, throw), (1.4, throw), (1.8, ip)])
    # 灯を揺らす (予告 0.8 秒): 竿を高く掲げて大きく振る
    up = merge(ip, {"right_arm": (120, 0, -10), "right_forearm": (-10, 0, 0), "weapon": (10, 0, 0), "lantern": (-50, 0, 0), "head": (20, 0, 0)})
    sway = seq([(0, ip), (0.4, up), (0.6, merge(up, {"lantern": (-20, 0, 30)})), (0.8, merge(up, {"lantern": (-60, 0, -30)})),
                (1.0, merge(up, {"lantern": (-40, 0, 20)})), (1.4, ip)])
    hurt = {"body": bone(kf({0: ip["body"], 0.08: (-20, 0, 0), 0.3: ip["body"]})), "head": bone(kf({0: ip["head"], 0.08: (18, 0, 0), 0.3: ip["head"]}))}
    # 消える: 霧へほどけて沈む
    death = {"body": bone(kf({0: (0, 0, 0), 0.8: (-40, 0, 20)}), kf({0: (0, 0, 0), 0.8: (0, -10, 0)}, False),
                          kf({0: (1, 1, 1), 0.5: (1.1, 0.9, 1.1), 0.8: (0.1, 0.1, 0.1)}, False))}
    return {p + "idle": loop(12.0, idle), p + "walk": loop(12.0, move), p + "net": once(1.8, net), p + "sway": once(1.4, sway),
            p + "hurt": once(0.3, hurt), p + "death": once(0.8, death, True)}


# ---------------------------------------------------------------- 名のある強敵 (岩背の大猪・潮招きの大蟹・雷角の大羊・灰被りの大蜥蜴)
def beast_base(prefix, P, step, legs=("fore_l", "fore_r", "hind_l", "hind_r"), swing=26, fall=8.0, splay=False):
    """四つ足の大物の待機・歩き・のけぞり・倒れ。splay = 脚が横へ張り出す (蜥蜴: 脚は縦軸のまわりに振る)"""
    p = "animation.%s." % prefix
    ip = P["idle"]
    idle = {"body": bone(wobble(ip.get("body", (0, 0, 0)), "0"), ["0", S(0, 0.3, 90), "0"])}
    for b in ("head", "tail", "tail2"):
        if b in ip or b == "head":
            base = ip.get(b, (0, 0, 0))
            idle[b] = bone([neg("%s + %s" % (base[0], S(0 if b == "head" else 90, 2.5, 80))), neg("%s + %s" % (base[1], S(40, 4 if b != "head" else 2, 60))), "0"])
    fl, fr, hl, hr = legs
    walk = {"body": bone(wobble(ip.get("body", (0, 0, 0)), S(0, 2)), ["0", "math.abs(" + S(0, 0.6) + ")", "0"]),
            "head": bone(wobble(ip.get("head", (0, 0, 0)), S(180, 3)))}
    for b, ph in ((fl, 0), (hr, 0), (fr, 180), (hl, 180)):
        walk[b] = bone(["0", neg(S(ph, swing)), "0"] if splay else [neg(S(ph, swing)), "0", "0"])
    if "tail" in BONES_OF.get(prefix, ()):
        walk["tail"] = bone(["0", neg(S(90, 14)), "0"])
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (-5, 0, 0), 0.3: (0, 0, 0)})), "head": bone(kf({0: (0, 0, 0), 0.08: (12, 0, 0), 0.3: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.7: (0, 0, 90)}), kf({0: (0, 0, 0), 0.7: (0, -fall, 0)}, False)),
             "head": bone(kf({0: (0, 0, 0), 0.7: (-20, 0, 0)}))}
    for b in legs:
        death[b] = bone(kf({0: (0, 0, 0), 0.7: (30 if b.startswith("fore") else -30, 0, 0)}))
    return {p + "idle": loop(4.0, idle), p + "walk": loop(step, walk), p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


BONES_OF = {"rock_boar": ("tail",), "storm_ram": ("tail",), "ash_lizard": ("tail", "tail2")}


def rock_boar():
    import rock_boar as M
    P = M.POSES
    a = beast_base("rock_boar", P, 0.9, fall=9.0)
    run = {"body": (-10, 0, 0), "head": (-30, 0, 0), "fore_l": (-40, 0, 0), "fore_r": (30, 0, 0), "hind_l": (30, 0, 0), "hind_r": (-40, 0, 0), "tail": (40, 0, 0)}
    run2 = merge(run, {"fore_l": (30, 0, 0), "fore_r": (-40, 0, 0), "hind_l": (-40, 0, 0), "hind_r": (30, 0, 0)})
    paw_up, paw_dn = P["telegraph"], merge(P["telegraph"], {"fore_r": (5, 0, 0)})
    # 突進 (予告 1.2 秒): 頭を下げて前脚で地を掻く (三度) -> 1.2 で駆ける
    a["animation.rock_boar.charge"] = once(2.1, seq([(0, P["idle"]), (0.3, paw_up), (0.5, paw_dn), (0.7, paw_up), (0.9, paw_dn), (1.1, paw_up), (1.2, run), (1.35, run2),
                                                      (1.5, run), (1.65, run2), (2.1, P["idle"])]))
    # 背を震わせる (予告 1.0 秒): 前を持ち上げて岩の背を揺すり、1.0 で前脚を叩きつける
    slam = {"body": (-6, 0, 0), "head": (-14, 0, 0), "fore_l": (20, 0, 0), "fore_r": (20, 0, 0)}
    q = seq([(0, P["idle"]), (0.7, P["quake"]), (0.8, merge(P["quake"], {"body": (16, 0, 4)})), (0.9, merge(P["quake"], {"body": (16, 0, -4)})), (1.0, P["quake"]),
             (1.1, slam), (1.4, slam), (1.8, P["idle"])])
    a["animation.rock_boar.quake"] = once(1.8, q)
    # 牙で薙ぐ (予告 0.6 秒): 頭を横へ引き、反対へ振り抜く
    a["animation.rock_boar.gore"] = once(1.1, seq([(0, P["idle"]), (0.5, {"head": (-24, 34, 0), "body": (0, 10, 0)}), (0.6, {"head": (-24, 34, 0), "body": (0, 10, 0)}),
                                                    (0.72, {"head": (10, -40, 0), "body": (0, -12, 0)}), (1.1, P["idle"])]))
    return a


def tide_crab():
    import tide_crab as M
    P = M.POSES
    p = "animation.tide_crab."
    ip = P["idle"]
    idle = {"body": bone(position=["0", S(0, 0.3, 80), "0"]), "claw_big": bone(wobble(ip["claw_big"], S(0, 3, 70))), "pincer": bone(wobble(ip["pincer"], S(60, 6, 120))),
            "claw_small": bone([neg(S(90, 8, 140)), "0", "0"]), "eye_l": bone([neg(S(0, 6, 90)), "0", "0"]), "eye_r": bone([neg(S(120, 6, 90)), "0", "0"])}
    walk = {"body": bone(["0", "0", S(0, 3)], ["0", "math.abs(" + S(0, 0.5) + ")", "0"]), "claw_big": bone(wobble(ip["claw_big"], S(0, 4))),
            "pincer": bone(wobble(ip["pincer"], "0"))}
    for b, ph, sx in (("leg_la", 0, 1), ("leg_rb", 0, -1), ("leg_lb", 180, 1), ("leg_ra", 180, -1)):
        walk[b] = bone(["0", neg(S(ph, 16)), "%d * (%s)" % (sx, S(ph + 90, 14))])
    slam = {"body": (-8, 0, 0), "claw_big": (-24, 0, -10), "pincer": (0, 0, 0), "claw_small": (-10, 0, 0)}
    # 大ばさみの叩きつけ (予告 1.0 秒)
    slam_a = seq([(0, ip), (0.85, P["telegraph"]), (1.0, P["telegraph"]), (1.1, slam), (1.5, slam), (2.0, ip)])
    # 泡 (予告 1.2 秒): 体を前へ傾けて、1.2 から泡を吹き続ける (はさみを打ち鳴らす)
    spray = seq([(0, ip), (0.9, P["spray"]), (1.2, P["spray"]), (1.35, merge(P["spray"], {"body": (-14, 0, 0)})), (1.5, P["spray"]), (1.65, merge(P["spray"], {"body": (-14, 0, 0)})),
                 (1.8, P["spray"]), (2.2, ip)])
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (6, 0, 0), 0.3: (0, 0, 0)})), "eye_l": bone(kf({0: (0, 0, 0), 0.08: (40, 0, 0), 0.3: (0, 0, 0)})),
            "eye_r": bone(kf({0: (0, 0, 0), 0.08: (40, 0, 0), 0.3: (0, 0, 0)}))}
    # 倒れる: 脚を縮めて腹を落とし、大ばさみが落ちる
    death = {"body": bone(kf({0: (0, 0, 0), 0.6: (6, 0, 8)}), kf({0: (0, 0, 0), 0.6: (0, -7, 0)}, False)), "claw_big": bone(kf({0: (0, 0, 0), 0.6: (-20, 0, -20)})),
             "eye_l": bone(kf({0: (0, 0, 0), 0.6: (60, 0, 0)})), "eye_r": bone(kf({0: (0, 0, 0), 0.6: (60, 0, 0)}))}
    for b, sx in (("leg_la", 1), ("leg_lb", 1), ("leg_ra", -1), ("leg_rb", -1)):
        death[b] = bone(kf({0: (0, 0, 0), 0.6: (0, 0, sx * 40)}))
    return {p + "idle": loop(6.0, idle), p + "walk": loop(0.8, walk), p + "slam": once(2.0, slam_a), p + "spray": once(2.2, spray),
            p + "hurt": once(0.3, hurt), p + "death": once(0.8, death, True)}


def storm_ram():
    import storm_ram as M
    P = M.POSES
    a = beast_base("storm_ram", P, 0.8, fall=10.0)
    run = {"body": (-8, 0, 0), "head": (-40, 0, 0), "fore_l": (-40, 0, 0), "fore_r": (30, 0, 0), "hind_l": (30, 0, 0), "hind_r": (-40, 0, 0)}
    run2 = merge(run, {"fore_l": (30, 0, 0), "fore_r": (-40, 0, 0), "hind_l": (-40, 0, 0), "hind_r": (30, 0, 0)})
    # 角の突進 (予告 1.2 秒): 頭を下げて角を前へ -> 1.2 で駆ける
    a["animation.storm_ram.charge"] = once(2.1, seq([(0, P["idle"]), (0.9, P["telegraph"]), (1.2, P["telegraph"]), (1.3, run), (1.45, run2), (1.6, run), (1.75, run2), (2.1, P["idle"])]))
    # 立ち上がって蹄を打ち下ろす (予告 1.2 秒)
    stomp = {"body": (-6, 0, 0), "head": (-20, 0, 0), "fore_l": (30, 0, 0), "fore_r": (30, 0, 0)}
    rear = seq([(0, P["idle"]), (0.9, P["rear"]), (1.2, P["rear"]), (1.32, stomp), (1.6, stomp), (2.0, P["idle"])])
    rear["body"]["position"] = kf({0: (0, 0, 0), 0.9: (0, 4, 4), 1.2: (0, 4, 4), 1.32: (0, 0, 0)}, False)
    a["animation.storm_ram.rear"] = once(2.0, rear)
    # 頭突き (予告 0.6 秒)
    a["animation.storm_ram.butt"] = once(1.0, seq([(0, P["idle"]), (0.5, {"head": (-40, 0, 0), "body": (-6, 0, 0)}), (0.6, {"head": (-40, 0, 0), "body": (-6, 0, 0)}),
                                                    (0.7, {"head": (14, 0, 0), "body": (4, 0, 0)}), (1.0, P["idle"])]))
    return a


def ash_lizard():
    import ash_lizard as M
    P = M.POSES
    a = beast_base("ash_lizard", P, 1.0, swing=22, fall=6.0, splay=True)
    a["animation.ash_lizard.walk"]["bones"]["tail"] = bone(["0", neg(S(90, 14)), "0"])
    a["animation.ash_lizard.walk"]["bones"]["tail2"] = bone(["0", neg(S(140, 18)), "0"])
    a["animation.ash_lizard.walk"]["bones"]["body"] = bone(["0", neg(S(0, 6)), "0"], ["0", S(0, 0.3, 720), "0"])
    breath = {"body": (8, 0, 0), "head": (6, 0, 0), "jaw": (-34, 0, 0), "fore_l": (-10, 0, 0), "fore_r": (-10, 0, 0)}
    # 吐く (予告 1.2 秒): 頭を持ち上げて口を開け -> 1.2 から吐き続け、首を左右に振る
    a["animation.ash_lizard.breath"] = once(2.4, seq([(0, P["idle"]), (1.0, P["telegraph"]), (1.2, P["telegraph"]), (1.3, breath), (1.6, merge(breath, {"head": (6, 14, 0)})),
                                                       (1.9, merge(breath, {"head": (6, -14, 0)})), (2.1, breath), (2.4, P["idle"])]))
    # 尾の薙ぎ (予告 0.8 秒): 体をひねって尾を振りかぶる -> 0.8 で反対へ振り抜く
    through = {"body": (0, -30, 0), "head": (0, -20, 0), "tail": (0, -60, 0), "tail2": (0, -50, 0)}
    a["animation.ash_lizard.sweep"] = once(1.6, seq([(0, P["idle"]), (0.65, P["coil"]), (0.8, P["coil"]), (0.95, through), (1.2, through), (1.6, P["idle"])]))
    # 噛みつき (予告 0.5 秒)
    a["animation.ash_lizard.bite"] = once(0.9, seq([(0, P["idle"]), (0.4, {"head": (12, 0, 0), "jaw": (-30, 0, 0), "body": (4, 0, 0)}),
                                                     (0.5, {"head": (12, 0, 0), "jaw": (-30, 0, 0), "body": (4, 0, 0)}), (0.6, {"head": (-12, 0, 0), "jaw": (0, 0, 0), "body": (-4, 0, 0)}),
                                                     (0.9, P["idle"])]))
    return a


# ---------------------------------------------------------------- 音 (既存の音源・バニラの音の別名。"type": "event" = その音の出来事をそのまま鳴らす)
M = "skyabyss:mobs/"


def ev(name, pitch, vol):
    return (name, pitch, vol, "event")


SOUNDS = {
    "bandit_ambient": [ev("minecraft:entity.pillager.ambient", 0.95, 0.7)],
    "bandit_hurt": [ev("minecraft:entity.pillager.hurt", 1.0, 1.0)],
    "bandit_death": [ev("minecraft:entity.pillager.death", 1.0, 1.0)],
    "bandit_swing": [ev("minecraft:entity.player.attack.sweep", 0.75, 0.9)],
    "bandit_archer_draw": [ev("minecraft:item.crossbow.loading_middle", 0.8, 0.9)],
    "bandit_archer_shoot": [ev("minecraft:entity.arrow.shoot", 0.9, 1.0)],
    "bandit_chief_ambient": [ev("minecraft:entity.vindicator.ambient", 0.8, 0.9)],
    "bandit_chief_hurt": [ev("minecraft:entity.vindicator.hurt", 0.8, 1.0)],
    "bandit_chief_death": [ev("minecraft:entity.vindicator.death", 0.75, 1.0)],
    "bandit_chief_shout": [ev("minecraft:entity.vindicator.celebrate", 0.7, 1.4)],
    "bone_soldier_ambient": [(M + "bone_sentinel_ambient", 1.15, 0.7)],
    "bone_soldier_hurt": [(M + "bone_sentinel_hurt_1", 1.15, 0.9), (M + "bone_sentinel_hurt_2", 1.15, 0.9)],
    "bone_soldier_death": [(M + "bone_sentinel_death", 1.1, 1.0)],
    "bone_soldier_step": [(M + "bone_sentinel_step_1", 1.2, 0.4), (M + "bone_sentinel_step_2", 1.2, 0.4)],
    "bone_soldier_block": [(M + "bone_sentinel_guard", 1.2, 0.9)],
    "bone_soldier_rise": [ev("minecraft:entity.skeleton_horse.ambient", 0.6, 0.9)],
    "bone_bowman_shoot": [(M + "rib_archer_shoot", 0.85, 1.0)],
    "bone_standard_plant": [(M + "stone_heart_slam", 1.4, 1.0)],
    "bone_standard_horn": [(M + "captain_roar", 1.3, 0.8)],
    "grain_rat_ambient": [ev("minecraft:entity.silverfish.ambient", 0.7, 0.6)],
    "grain_rat_hurt": [ev("minecraft:entity.silverfish.hurt", 0.75, 0.9)],
    "grain_rat_death": [ev("minecraft:entity.silverfish.death", 0.7, 0.9)],
    "grain_rat_bite": [ev("minecraft:entity.fox.bite", 1.3, 0.9)],
    "stray_wisp_ambient": [ev("minecraft:entity.allay.ambient_without_item", 0.55, 0.5)],
    "stray_wisp_hurt": [ev("minecraft:entity.allay.hurt", 0.6, 0.9)],
    "stray_wisp_death": [ev("minecraft:entity.allay.death", 0.5, 1.0)],
    "stray_wisp_pulse": [ev("minecraft:block.beacon.deactivate", 1.6, 0.9)],
    "feral_dog_ambient": [ev("minecraft:entity.wolf.growl", 1.1, 0.7)],
    "feral_dog_hurt": [ev("minecraft:entity.wolf.hurt", 1.0, 0.9)],
    "feral_dog_death": [ev("minecraft:entity.wolf.death", 1.0, 1.0)],
    "feral_dog_howl": [ev("minecraft:entity.wolf.howl", 1.1, 1.4)],
    "feral_dog_bite": [ev("minecraft:entity.fox.bite", 0.8, 1.0)],
    "moss_dead_ambient": [ev("minecraft:entity.zombie.ambient", 0.55, 0.8)],
    "moss_dead_hurt": [ev("minecraft:block.moss.break", 0.7, 1.0), ev("minecraft:entity.zombie.hurt", 0.6, 0.8)],
    "moss_dead_death": [ev("minecraft:entity.zombie.death", 0.5, 1.0)],
    "moss_dead_step": [ev("minecraft:block.moss.step", 0.7, 0.6)],
    "moss_dead_slam": [(M + "stone_heart_slam", 1.3, 1.0)],
    "moss_dead_spores": [ev("minecraft:block.sculk_sensor.clicking", 0.6, 1.2)],
    "mist_angler_ambient": [ev("minecraft:entity.drowned.ambient_water", 0.7, 0.7)],
    "mist_angler_hurt": [ev("minecraft:entity.drowned.hurt_water", 0.8, 1.0)],
    "mist_angler_death": [ev("minecraft:entity.drowned.death_water", 0.7, 1.0)],
    "mist_angler_net": [ev("minecraft:entity.fishing_bobber.throw", 0.6, 1.2)],
    "mist_angler_sway": [ev("minecraft:block.bell.resonate", 1.6, 0.6)],
    "thunder_wolf_ambient": [ev("minecraft:entity.wolf.growl", 0.75, 0.9)],
    "thunder_wolf_hurt": [ev("minecraft:entity.wolf.hurt", 0.75, 1.0)],
    "thunder_wolf_death": [ev("minecraft:entity.wolf.death", 0.7, 1.0)],
    "thunder_wolf_howl": [ev("minecraft:entity.wolf.howl", 0.8, 1.6)],
    "rock_boar_ambient": [ev("minecraft:entity.hoglin.ambient", 0.75, 1.0)],
    "rock_boar_hurt": [ev("minecraft:entity.hoglin.hurt", 0.75, 1.0)],
    "rock_boar_death": [ev("minecraft:entity.hoglin.death", 0.7, 1.2)],
    "rock_boar_step": [ev("minecraft:entity.hoglin.step", 0.7, 0.8)],
    "rock_boar_charge": [ev("minecraft:entity.hoglin.angry", 0.7, 1.6)],
    "rock_boar_quake": [(M + "stone_heart_slam", 0.9, 1.4)],
    "tide_crab_ambient": [ev("minecraft:entity.turtle.ambient_land", 0.5, 1.0)],
    "tide_crab_hurt": [ev("minecraft:entity.turtle.hurt", 0.6, 1.0)],
    "tide_crab_death": [ev("minecraft:entity.turtle.death", 0.55, 1.2)],
    "tide_crab_slam": [(M + "stone_heart_slam", 1.15, 1.3)],
    "tide_crab_spray": [ev("minecraft:block.bubble_column.upwards_inside", 0.9, 1.6)],
    "storm_ram_ambient": [ev("minecraft:entity.goat.screaming.ambient", 0.7, 1.0)],
    "storm_ram_hurt": [ev("minecraft:entity.goat.hurt", 0.7, 1.0)],
    "storm_ram_death": [ev("minecraft:entity.goat.death", 0.6, 1.2)],
    "storm_ram_charge": [ev("minecraft:entity.goat.prepare_ram", 0.7, 1.5)],
    "storm_ram_stomp": [ev("minecraft:entity.goat.ram_impact", 0.6, 1.5)],
    "ash_lizard_ambient": [ev("minecraft:entity.strider.ambient", 0.55, 1.0)],
    "ash_lizard_hurt": [ev("minecraft:entity.strider.hurt", 0.6, 1.0)],
    "ash_lizard_death": [ev("minecraft:entity.strider.death", 0.5, 1.2)],
    "ash_lizard_breath": [ev("minecraft:entity.blaze.shoot", 0.6, 1.4)],
    "ash_lizard_sweep": [ev("minecraft:entity.player.attack.sweep", 0.5, 1.4)],
}
SUBTITLES = {
    "bandit_ambient": ("Bandit mutters", "野盗がつぶやく"), "bandit_hurt": ("Bandit hurts", "野盗がうめく"), "bandit_death": ("Bandit falls", "野盗が倒れる"),
    "bandit_swing": ("Cleaver swings", "鉈が振られる"), "bandit_archer_draw": ("Bowstring creaks", "弦がきしむ"), "bandit_archer_shoot": ("Arrow flies", "矢が飛ぶ"),
    "bandit_chief_ambient": ("Bandit chief growls", "頭目がうなる"), "bandit_chief_hurt": ("Bandit chief grunts", "頭目がうめく"),
    "bandit_chief_death": ("Bandit chief falls", "頭目が倒れる"), "bandit_chief_shout": ("Bandit chief roars", "頭目が吠える"),
    "bone_soldier_ambient": ("Bones rattle", "骨が鳴る"), "bone_soldier_hurt": ("Bones crack", "骨がきしむ"), "bone_soldier_death": ("Bones collapse", "骸が崩れる"),
    "bone_soldier_step": ("Bony steps", "骨の足音"), "bone_soldier_block": ("Shield blocks", "盾が受ける"), "bone_soldier_rise": ("Bones rise again", "骸が起き上がる"),
    "bone_bowman_shoot": ("Longbow looses", "長弓が鳴る"), "bone_standard_plant": ("Banner strikes the ground", "旗が地を突く"),
    "bone_standard_horn": ("Old war cry", "古い鬨の声"),
    "grain_rat_ambient": ("Rat squeaks", "鼠が鳴く"), "grain_rat_hurt": ("Rat squeals", "鼠が悲鳴をあげる"), "grain_rat_death": ("Rat dies", "鼠が倒れる"),
    "grain_rat_bite": ("Rat bites", "鼠が噛む"),
    "stray_wisp_ambient": ("Wisp hums", "迷い灯がささやく"), "stray_wisp_hurt": ("Wisp flickers", "迷い灯がゆらぐ"), "stray_wisp_death": ("Wisp fades", "迷い灯が消える"),
    "stray_wisp_pulse": ("Wisp pulses", "迷い灯が明滅する"),
    "feral_dog_ambient": ("Feral dog growls", "野犬がうなる"), "feral_dog_hurt": ("Feral dog yelps", "野犬が鳴く"), "feral_dog_death": ("Feral dog dies", "野犬が倒れる"),
    "feral_dog_howl": ("Feral dog howls", "野犬が吠える"), "feral_dog_bite": ("Feral dog bites", "野犬が噛む"),
    "moss_dead_ambient": ("Moss dead groans", "苔の亡者がうめく"), "moss_dead_hurt": ("Moss tears", "苔がちぎれる"),
    "moss_dead_death": ("Moss dead collapses", "苔の亡者が崩れる"), "moss_dead_step": ("Heavy mossy steps", "苔の重い足音"),
    "moss_dead_slam": ("Moss dead slams", "苔の亡者が叩きつける"), "moss_dead_spores": ("Spores swell", "胞子が膨らむ"),
    "mist_angler_ambient": ("Water drips", "水がしたたる"), "mist_angler_hurt": ("Mist angler wails", "霧の漁り火が鳴く"),
    "mist_angler_death": ("Mist angler fades", "霧の漁り火が消える"), "mist_angler_net": ("Net is cast", "網が投げられる"),
    "mist_angler_sway": ("Lantern sways", "漁り火が揺れる"),
    "thunder_wolf_ambient": ("Thunder wolf growls", "雷狼がうなる"), "thunder_wolf_hurt": ("Thunder wolf yelps", "雷狼が鳴く"),
    "thunder_wolf_death": ("Thunder wolf dies", "雷狼が倒れる"), "thunder_wolf_howl": ("Thunder wolf howls", "雷狼が吠える"),
    "rock_boar_ambient": ("Great boar grunts", "大猪がうなる"), "rock_boar_hurt": ("Great boar squeals", "大猪が鳴く"), "rock_boar_death": ("Great boar falls", "大猪が倒れる"),
    "rock_boar_step": ("Heavy hooves", "重い蹄の音"), "rock_boar_charge": ("Great boar snorts", "大猪が鼻を鳴らす"), "rock_boar_quake": ("Rock plates shake", "岩の背が震える"),
    "tide_crab_ambient": ("Great crab clicks", "大蟹がはさみを鳴らす"), "tide_crab_hurt": ("Shell cracks", "甲羅がきしむ"), "tide_crab_death": ("Great crab falls", "大蟹が倒れる"),
    "tide_crab_slam": ("Great claw slams", "大ばさみが叩きつけられる"), "tide_crab_spray": ("Bubbles gush", "泡が噴き出す"),
    "storm_ram_ambient": ("Great ram bleats", "大羊が鳴く"), "storm_ram_hurt": ("Great ram cries", "大羊が叫ぶ"), "storm_ram_death": ("Great ram falls", "大羊が倒れる"),
    "storm_ram_charge": ("Great ram lowers its horns", "大羊が角を下げる"), "storm_ram_stomp": ("Hooves strike", "蹄が打ち下ろされる"),
    "ash_lizard_ambient": ("Great lizard hisses", "大蜥蜴が息を漏らす"), "ash_lizard_hurt": ("Great lizard shrieks", "大蜥蜴が叫ぶ"),
    "ash_lizard_death": ("Great lizard falls", "大蜥蜴が倒れる"), "ash_lizard_breath": ("Embers pour out", "熾火が吐かれる"), "ash_lizard_sweep": ("Tail sweeps", "尾が薙ぐ"),
}
NAMES = {
    "bandit": ("Bandit", "野盗", 0x5A4632, 0x8C2E22), "bandit_archer": ("Bandit Archer", "野盗の弓手", 0x6B7040, 0x5A4632),
    "bandit_chief": ("Bandit Chief", "野盗の頭目", 0x4A3A2C, 0xB0302A), "bone_footman": ("Bone Footman", "骸の歩兵", 0xD8CDB2, 0x7A5A3A),
    "bone_bowman": ("Bone Bowman", "骸の弓兵", 0xD8CDB2, 0x4A5A3A), "bone_standard": ("Bone Standard-Bearer", "骸の旗手", 0xD8CDB2, 0x2E3E6A),
    "grain_rat": ("Grain Rat", "穂かじり鼠", 0x8A6A40, 0xD8B470), "stray_wisp": ("Stray Wisp", "迷い灯", 0x9ACAEE, 0x2A2E3A),
    "feral_dog": ("Feral Dog", "野犬", 0x6E6050, 0x8A6A40), "moss_dead": ("Moss Dead", "苔の亡者", 0x3E5A2A, 0xA8F082),
    "mist_angler": ("Mist Angler", "霧の漁り火", 0x54523A, 0x78F0D2), "thunder_wolf": ("Thunder Wolf", "雷狼", 0x3E4862, 0x8CD2FF),
    "rock_boar": ("Rockback Boar", "岩背の大猪", 0x4A3C2E, 0x7A8696), "tide_crab": ("Tidecaller Crab", "潮招きの大蟹", 0x22424A, 0xBA4A26),
    "storm_ram": ("Thunderhorn Ram", "雷角の大羊", 0xC4C8D0, 0x2A2C34), "ash_lizard": ("Ashcloak Lizard", "灰被りの大蜥蜴", 0x1E1A18, 0xE05A30),
}


# 名のある強敵 (world/continent/NamedFoes) の名札と知らせ
NAMED_LANG = {
    "entity.skyabyss.named.island": ("Redsash, Chief of the Pass", "峠の頭目 赤帯"),
    "entity.skyabyss.named.meadow": ("The Last Standard-Bearer", "最後の旗手"),
    "message.skyabyss.named.appear": ("Something named stirs nearby: %s", "近くに名のある強敵が現れた: %s"),
    "message.skyabyss.named.down": ("%s has fallen. %s bell coins", "%s を倒した。鐘銭 %s 枚"),
}


def sounds():
    p = os.path.join(A, "sounds.json")
    data = json.load(open(p, encoding="utf-8"))
    for key, entries in SOUNDS.items():
        out = []
        for e in entries:
            s = {"name": e[0], "pitch": round(e[1], 3), "volume": e[2]}
            if len(e) > 3:
                s["type"] = e[3]
            out.append(s)
        data[key] = {"subtitle": "subtitles.skyabyss." + key, "sounds": out}
    wjson(p, data)
    for i, lang in enumerate(("en_us", "ja_jp")):
        lp = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_mobsounds", "lang", lang + ".json")
        d = json.load(open(lp, encoding="utf-8"))
        for key, texts in SUBTITLES.items():
            d["subtitles.skyabyss." + key] = texts[i]
        wjson(lp, d)


def lang():
    for i, lang_ in enumerate(("en_us", "ja_jp")):
        d = {}
        for mob, (en, ja, _, _) in NAMES.items():
            name = (en, ja)[i]
            d["entity.skyabyss." + mob] = name
            d["item.skyabyss." + mob + "_spawn_egg"] = name + " Spawn Egg" if i == 0 else name + "のスポーンエッグ"
        for key, texts in NAMED_LANG.items():
            d[key] = texts[i]
        wjson(os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_foes", "lang", lang_ + ".json"), d)
    for mob in MOBS:
        wjson(os.path.join(A, "models", "item", mob + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def install():
    for mob in MOBS:
        d = os.path.join(R, mob, "a", mob + "_a_")
        subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "install/install_lowpoly.py"), mob, "--mesh", d + "mesh.json", "--atlas", d + "atlas.png",
                               "--glow", d + "glowmask.png"], cwd=ROOT)
        shutil.copyfile(os.path.join(A, "geo", "entity", mob + "_lowpoly_debug.geo.json"), os.path.join(A, "geo", "entity", mob + ".geo.json"))
        os.makedirs(os.path.join(A, "textures", "entity"), exist_ok=True)
        shutil.copyfile(os.path.join(A, "textures", "lowpoly", mob + ".png"), os.path.join(A, "textures", "entity", mob + ".png"))
        gm = os.path.join(A, "textures", "lowpoly", mob + "_glowmask.png")
        if os.path.exists(gm):
            shutil.copyfile(gm, os.path.join(A, "textures", "entity", mob + "_glowmask.png"))


def main():
    if "--no-install" not in sys.argv:
        install()
    for mob in MOBS:
        wjson(os.path.join(A, "animations", "entity", mob + ".animation.json"), {"format_version": "1.8.0", "animations": globals()[mob]()})
    sounds()
    lang()
    print("done")


if __name__ == "__main__":
    main()
