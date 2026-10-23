#!/usr/bin/env python3
"""五章の深淵の敵 9 体 (B 組: 灰の層 3・深み 2・深淵だけ 4) の素材を入れる (docs/design/36 の 4・4.1、A3)。

  python -I tools/abyss/paint_abyss_b.py      (先に絵を描く: docs/review/abyss_painted/)
  PYTHONIOENCODING=utf-8 python -I tools/gen/abyss/gen_abyss_mobs_b.py [名前...]   (名前を並べるとその敵だけ)

入力: docs/review/abyss_roughs/<id>/<案>/<id>_<案>_mesh.json   (メッシュ。絵は docs/review/abyss_painted/)
出力 (assets/skyabyss/ の下):
  lowpoly/<id>.json, textures/lowpoly/<id>(.png|_glowmask.png), geo/entity/<id>_lowpoly(_debug).geo.json   (tools/install/install_lowpoly.py)
  textures/lowpoly/<id>_s1(.png|_glowmask.png), <id>_s2(...)        歪み・崩れの絵 (同じ骨・同じ UV)
  geo/entity/<id>.geo.json + textures/entity/<id>.png              LowPoly.enabled = false のときの箱の代用
  animations/entity/<id>.animation.json
  textures/item/<品>.png・models/item/<品>.json・models/item/<id>_spawn_egg.json
  assets/skyabyss_abyss_b/sounds.json                              音の出来事 (既存の音の別名。字幕は lang)
  lang: assets/skyabyss/lang/ja_jp.json・en_us.json の abyss_b の鍵 (lang_abyss_b の一覧)
"""
import copy
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
AB = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_abyss_b")
ROUGH = os.path.join(ROOT, "docs", "review", "abyss_roughs")
PAINT = os.path.join(ROOT, "docs", "review", "abyss_painted")

# 選んだ案 (docs/design/36 の 4.1。2026-10-22 の選定)
CHOICE = {
    "ash_trail": "c", "ember_keeper": "c", "soot_child": "a",
    "past_shadow": "c", "nameless_shape": "a",
    "lamp_eater": "a", "ceiling_walker": "a", "heartbeat_mimic": "b", "rope_hanger": "b",
}


def wjson(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")


# ---------------------------------------------------------------- 動きの部品 (tools/gen/ch4/gen_ch4_ground_mobs.py と同じ約束)
# 回転は数学の右手系 (前 -Z、x が + = 前が上がる) で書き、ファイルへは (-x, -y, z)。位置はそのまま (y が上)
def neg(expr):
    return "-(%s)" % expr


def rot(x=0, y=0, z=0):
    return [-x if x else 0, -y if y else 0, z]


def S(phase, amp, speed=360):
    return "math.sin(query.anim_time * %d + %d) * %s" % (speed, phase, amp)


def C(phase, amp, speed=360):
    return "math.cos(query.anim_time * %d + %d) * %s" % (speed, phase, amp)


def loop(length, bones):
    return {"loop": True, "animation_length": length, "bones": bones}


def once(length, bones, hold=False):
    d = {"animation_length": length, "bones": bones}
    if hold:
        d["loop"] = "hold_on_last_frame"
    return d


def kf(frames, rotation=True):
    return {"%.2f" % t: (rot(*v) if rotation else list(v)) for t, v in frames.items()}


def bone(rotation=None, position=None):
    d = {}
    if rotation:
        d["rotation"] = rotation
    if position:
        d["position"] = position
    return d


def hurt_bones(bodyname="body"):
    return {bodyname: bone(kf({0: (0, 0, 0), 0.06: (0, 0, 10), 0.22: (0, 0, 0)}))}


def death_bones(spin=160, sink=2.0, bodyname="body"):
    return {bodyname: bone(kf({0: (0, 0, 0), 0.5: (0, 0, spin), 1.0: (0, 0, spin + 10)}),
                           kf({0: (0, 0, 0), 0.5: (0, sink, 0), 1.0: (0, sink * 1.2, 0)}, False))}


def legs_walk(names, speed, amp, lift=6):
    """脚 (名前の列) を交互に振る。左右の名前は _l / _r で決める"""
    out = {}
    for i, b in enumerate(names):
        side = -1 if "_l" in b else 1
        ph = 0 if (i % 2 == 0) == (side < 0) else 180
        out[b] = bone([S(ph, amp, speed), "0", "0"], [0, "math.abs(%s)" % S(ph, lift, speed), 0])
    return out


# ---------------------------------------------------------------- 灰の層: 灰の這い跡 (見えない。足跡 prints は床に残る)
def ash_trail():
    p = "animation.ash_trail."
    idle = {"head": bone(["0", S(0, 10, 90), "0"]), "arm_l": bone([S(40, 6, 120), "0", "0"]), "arm_r": bone([S(220, 6, 120), "0", "0"])}
    walk = legs_walk(["leg_l1", "leg_r1", "leg_l2", "leg_r2"], 600, 26, 4)
    walk["body"] = bone(["0", "0", S(0, 3, 600)], [0, "math.abs(%s)*0.4" % S(0, 1, 600), 0])
    walk["arm_l"] = bone([S(0, 20, 600), "0", "0"])
    walk["arm_r"] = bone([S(180, 20, 600), "0", "0"])
    reveal = {"head": bone(kf({0: (0, 0, 0), 0.15: (-25, 0, 0), 0.5: (-10, 0, 0)})),
              "body": bone(kf({0: (0, 0, 0), 0.15: (-8, 0, 0), 0.5: (-4, 0, 0)})),
              "arm_l": bone(kf({0: (0, 0, 0), 0.15: (-60, 0, 0)})), "arm_r": bone(kf({0: (0, 0, 0), 0.15: (-60, 0, 0)}))}
    attack = {"body": bone(kf({0: (0, 0, 0), 0.12: (-30, 0, 0), 0.3: (0, 0, 0)}),
                           kf({0: (0, 0, 0), 0.12: (0, 0, -2), 0.3: (0, 0, 0)}, False)),
              "arm_l": bone(kf({0: (0, 0, 0), 0.12: (-90, 0, 0), 0.3: (0, 0, 0)})),
              "arm_r": bone(kf({0: (0, 0, 0), 0.12: (-90, 0, 0), 0.3: (0, 0, 0)}))}
    return {p + "idle": loop(2.5, idle), p + "walk": loop(0.6, walk), p + "reveal": once(0.5, reveal, True),
            p + "attack": once(0.3, attack), p + "hurt": once(0.22, hurt_bones()), p + "death": once(0.6, death_bones(120, 0.5), True)}


# ---------------------------------------------------------------- 前の私の影 (濡れた影。人の動きを遅れてなぞる。揺れ・鏡の振り)
def past_shadow():
    p = "animation.past_shadow."
    idle = {"body": bone(["0", "0", S(0, 3, 70)], [0, S(0, 0.3, 70), 0]), "head": bone(["0", S(120, 8, 70), "0"]),
            "arm_l": bone([S(0, 8, 70), "0", "0"]), "arm_r": bone([S(180, 8, 70), "0", "0"])}
    walk = legs_walk(["leg_l", "leg_r"], 360, 22, 3)
    walk["arm_l"] = bone([S(0, 18, 360), "0", "0"])
    walk["arm_r"] = bone([S(180, 18, 360), "0", "0"])
    # 鏡: 人が手を振ったあと、同じ形を遅れてなぞる (手を上げて下ろす)
    mirror = {"arm_r": bone(kf({0: (0, 0, 0), 0.25: (-110, 0, -10), 0.6: (-110, 0, -10), 1.0: (0, 0, 0)})),
              "head": bone(kf({0: (0, 0, 0), 0.4: (10, 0, 0), 1.0: (0, 0, 0)}))}
    return {p + "idle": loop(3.5, idle), p + "walk": loop(0.6, walk), p + "mirror": once(1.0, mirror),
            p + "hurt": once(0.25, hurt_bones()), p + "death": once(1.2, death_bones(110, 0.5), True)}


# ---------------------------------------------------------------- 名のない形 (核のまわりに欠片が回る。遅い)
def nameless_shape():
    p = "animation.nameless_shape."
    idle = {"ring": bone(["0", "query.anim_time * 90", "0"]), "core": bone(["0", "0", S(0, 4, 90)], [0, S(0, 0.35, 90), 0]),
            "cloth": bone([S(0, 4, 120), "0", "0"]), "hem": bone([S(90, 3, 120), "0", "0"])}
    walk = {"ring": bone(["0", "query.anim_time * 180", "0"]), "core": bone(["0", "0", S(0, 6, 180)]),
            "cloth": bone([S(0, 8, 180), "0", "0"])}
    # 撃つ: 欠片の輪 (ring。A 案の欠片はここに載る) が止まって縮み、核が前へ傾いた瞬間に輪が速く回って開く
    shoot = {"ring": bone(kf({0: (0, 0, 0), 0.3: (8, 30, 0), 0.5: (-14, 200, 0), 0.9: (0, 360, 0)}),
                          kf({0: (0, 0, 0), 0.3: (0, -0.6, 0), 0.5: (0, 0.6, 0), 0.9: (0, 0, 0)}, False)),
             "core": bone(kf({0: (0, 0, 0), 0.25: (12, 0, 0), 0.5: (0, 0, 0)}))}
    return {p + "idle": loop(3.0, idle), p + "walk": loop(1.0, walk), p + "shoot": once(0.9, shoot),
            p + "hurt": once(0.25, hurt_bones("core")), p + "death": once(1.5, death_bones(60, 1.0, "core"), True)}


# ---------------------------------------------------------------- 灯喰い (羽が震える。顎を開いて灯りを食う。暗がりでは速い)
def lamp_eater():
    p = "animation.lamp_eater."
    idle = {"wing_l": bone([0, 0, S(0, 14, 140)]), "wing_r": bone([0, 0, S(0, -14, 140)]),
            "body": bone(["0", "0", S(0, 2, 140)], [0, S(0, 0.3, 140), 0]), "jaw": bone([S(0, 4, 140), "0", "0"])}
    walk = {"wing_l": bone([0, 0, S(0, 30, 540)]), "wing_r": bone([0, 0, S(0, -30, 540)]),
            "body": bone(["0", "0", S(0, 4, 540)], [0, S(0, 0.6, 540), 0]), "jaw": bone([S(0, 6, 540), "0", "0"])}
    snuff = {"jaw": bone(kf({0: (0, 0, 0), 0.2: (-40, 0, 0), 0.6: (-40, 0, 0), 1.0: (0, 0, 0)})),
             "wing_l": bone(kf({0: (0, 0, 0), 0.2: (0, 0, 30), 1.0: (0, 0, 0)})),
             "wing_r": bone(kf({0: (0, 0, 0), 0.2: (0, 0, -30), 1.0: (0, 0, 0)}))}
    return {p + "idle": loop(2.5, idle), p + "walk": loop(0.5, walk), p + "snuff": once(1.0, snuff),
            p + "hurt": once(0.25, hurt_bones()), p + "death": once(0.9, death_bones(120, 0.8), True)}


# ---------------------------------------------------------------- 逆さ歩き (天井を歩く。見られている間は固まる。落ちるとき腕を広げる)
def ceiling_walker():
    p = "animation.ceiling_walker."
    idle = {"head": bone(["0", S(0, 8, 80), "0"]), "arm_l": bone([S(0, 5, 80), "0", "0"]), "arm_r": bone([S(180, 5, 80), "0", "0"])}
    walk = legs_walk(["leg_l", "leg_r"], 420, 22, 3)
    walk["arm_l"] = bone([S(0, 14, 420), "0", "0"])
    walk["arm_r"] = bone([S(180, 14, 420), "0", "0"])
    # 落ちる: 腕と脚を広げて、落ちる (着いたとき体を丸める)
    drop = {"arm_l": bone(kf({0: (0, 0, 0), 0.3: (-150, 0, -40)})), "arm_r": bone(kf({0: (0, 0, 0), 0.3: (-150, 0, 40)})),
            "leg_l": bone(kf({0: (0, 0, 0), 0.3: (30, 0, 0)})), "leg_r": bone(kf({0: (0, 0, 0), 0.3: (30, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.3: (-30, 0, 0)}))}
    # 見られている間は指先だけ震える (止まっているのが見える)
    freeze = {"arm_l": bone([S(0, 3, 300), "0", "0"]), "arm_r": bone([S(180, 3, 300), "0", "0"])}
    return {p + "idle": loop(2.0, idle), p + "walk": loop(0.8, walk), p + "freeze": loop(1.0, freeze),
            p + "drop": once(0.5, drop, True), p + "hurt": once(0.25, hurt_bones()), p + "death": once(0.9, death_bones(180, 0.6), True)}


# ---------------------------------------------------------------- 縄の人 (縄から垂れる。揺れる。引くときは体を後ろへ反らす)
def rope_hanger():
    p = "animation.rope_hanger."
    idle = {"figure": bone(["0", "0", S(0, 3, 90)]), "head": bone([S(60, 6, 90), "0", "0"]),
            "arm_l": bone([S(0, 10, 90), "0", "0"]), "arm_r": bone([S(180, 10, 90), "0", "0"]),
            "leg_l": bone([S(0, 4, 90), "0", "0"]), "leg_r": bone([S(180, 4, 90), "0", "0"])}
    pull = {"figure": bone(kf({0: (0, 0, 0), 0.4: (-22, 0, 0), 1.0: (-22, 0, 0), 1.4: (0, 0, 0)})),
            "arm_l": bone(kf({0: (0, 0, 0), 0.4: (-70, 0, -30), 1.0: (-70, 0, -30), 1.4: (0, 0, 0)})),
            "arm_r": bone(kf({0: (0, 0, 0), 0.4: (-70, 0, 30), 1.0: (-70, 0, 30), 1.4: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.4: (-16, 0, 0), 1.4: (0, 0, 0)}))}
    return {p + "idle": loop(3.0, idle), p + "pull": once(1.4, pull), p + "hurt": once(0.25, hurt_bones("figure")),
            p + "death": once(1.2, death_bones(120, 2.0, "figure"), True)}


# ---------------------------------------------------------------- 2026-10-23 作り直した 3 種 (燠の番人・煤の子・鼓動もどき) の動き
# 向き (rot の数学の右手系): x の + = 垂れた部品 (腕・脚・鎖) が前へ振れる・立つ部品 (胴・頭) が後ろへ反る・前へ伸びた部品 (花弁の上) が上がる。
# z の + = +x の側の垂れた部品が外へ上がる (-x の側は内へ)。scale は [x, y, z] の倍率。
def sc(frames):
    return {"%.2f" % t: list(v) for t, v in frames.items()}


def ember_keeper():
    """燠の番人 (灰の僧): 裾と足・袖と前腕・頭巾の尾二節・鎖と灯し。予告 telegraph 0.8 秒 (TELL_TICKS 16) で灯しを掲げて右手を開き、
    heat で両腕を払って裾が広がる。歩くと足が裾の下から交互に出て、裾・頭巾の尾・灯しが遅れて揺れる。"""
    p = "animation.ember_keeper."
    idle = {"body": bone(["0", "0", S(0, 1.5, 120)], [0, S(0, 0.15, 120), 0]),
            "hem": bone([S(40, 1.2, 120), "0", S(180, 1.0, 120)]),
            "head": bone([S(60, 3, 72), S(90, 7, 60), "0"]),
            "hood_t1": bone([S(30, 5, 120), "0", S(120, 3, 72)]), "hood_t2": bone([S(80, 8, 120), "0", S(170, 5, 72)]),
            "arm_l": bone([S(0, 3, 120), "0", "0"]), "forearm_l": bone([S(30, 3, 120), "0", "0"]),
            "chain": bone([S(60, 5, 120), "0", S(150, 4, 90)]), "lamp": bone([S(100, 4, 120), S(0, 20, 45), S(190, 3, 90)]),
            "arm_r": bone([S(180, 3, 120), "0", "0"]), "forearm_r": bone([S(210, 4, 120), "0", "0"])}
    walk = legs_walk(["leg_l", "leg_r"], 360, 24, 1.2)
    walk["body"] = bone(["4", S(0, 3, 360), S(90, 2, 360)], [0, "math.abs(%s)*0.35" % S(0, 1, 360), 0])
    walk["hem"] = bone([S(90, 5, 360), "0", S(0, 3, 360)])
    walk["head"] = bone(["-3", S(180, 3, 360), "0"])
    walk["hood_t1"] = bone([S(60, 8, 360), "0", S(150, 4, 360)])
    walk["hood_t2"] = bone([S(120, 12, 360), "0", S(210, 6, 360)])
    walk["arm_l"] = bone([S(180, 8, 360), "0", "0"])
    walk["forearm_l"] = bone(["-10", "0", "0"])
    walk["chain"] = bone([S(30, 14, 360), "0", S(120, 6, 360)])
    walk["lamp"] = bone([S(80, 10, 360), "0", S(160, 6, 360)])
    walk["arm_r"] = bone([S(0, 18, 360), "0", "0"])
    walk["forearm_r"] = bone([S(30, 10, 360), "0", "0"])
    # 予告 (0.8 秒): 0〜0.5 で前へかがみ、左の灯しを前へ掲げ (鎖は垂れたまま)、右手を前へ開く。0.5〜0.8 は熱で震える
    def tremble(base, amp, axis=2):
        out = {0.5: base}
        for i, t in enumerate((0.56, 0.62, 0.68, 0.74, 0.8)):
            v = list(base)
            v[axis] += amp if i % 2 == 0 else -amp
            out[t] = tuple(v)
        return out
    telegraph = {"body": bone(kf({0: (0, 0, 0), 0.5: (-14, 0, 0), **{t: v for t, v in tremble((-14, 0, 0), 1.2).items() if t > 0.5}})),
                 "hem": bone(kf({0: (0, 0, 0), 0.5: (10, 0, 0)})),
                 "head": bone(kf({0: (0, 0, 0), 0.5: (6, 0, 0)})),
                 "hood_t1": bone(kf({0: (0, 0, 0), 0.5: (14, 0, 0), 0.8: (20, 0, 0)})), "hood_t2": bone(kf({0: (0, 0, 0), 0.5: (18, 0, 0), 0.8: (26, 0, 0)})),
                 "arm_l": bone(kf({0: (0, 0, 0), 0.5: (74, 0, -12), 0.8: (80, 0, -12)})), "forearm_l": bone(kf({0: (0, 0, 0), 0.5: (34, 0, 0)})),
                 "chain": bone(kf({0: (0, 0, 0), 0.5: (-98, 0, 10), 0.65: (-112, 0, 6), 0.8: (-104, 0, 12)})),
                 "lamp": bone(kf({0: (0, 0, 0), 0.5: (0, 90, 0), 0.8: (0, 160, 0)}), None),
                 "arm_r": bone(kf({0: (0, 0, 0), 0.5: (58, 0, -14), **{t: v for t, v in tremble((58, 0, -14), 3).items() if t > 0.5}})),
                 "forearm_r": bone(kf({0: (0, 0, 0), 0.5: (30, 0, 0)}))}
    telegraph["lamp"]["scale"] = sc({0: (1, 1, 1), 0.5: (1.15, 1.15, 1.15), 0.65: (1.05, 1.05, 1.05), 0.8: (1.2, 1.2, 1.2)})
    # 熱の輪 (0.6 秒): 0.1 で両腕を外へ払い、胴は起きて反る。裾が一度広がって戻る
    heat = {"body": bone(kf({0: (-14, 0, 0), 0.1: (8, 0, 0), 0.35: (4, 0, 0), 0.6: (0, 0, 0)})),
            "hem": bone(kf({0: (10, 0, 0), 0.1: (-6, 0, 0), 0.6: (0, 0, 0)})),
            "head": bone(kf({0: (6, 0, 0), 0.1: (12, 0, 0), 0.6: (0, 0, 0)})),
            "hood_t1": bone(kf({0: (20, 0, 0), 0.1: (40, 0, 0), 0.6: (0, 0, 0)})), "hood_t2": bone(kf({0: (26, 0, 0), 0.15: (55, 0, 0), 0.6: (0, 0, 0)})),
            "arm_l": bone(kf({0: (80, 0, -12), 0.1: (40, 0, 70), 0.35: (35, 0, 62), 0.6: (0, 0, 0)})),
            "forearm_l": bone(kf({0: (34, 0, 0), 0.1: (10, 0, 0), 0.6: (0, 0, 0)})),
            "chain": bone(kf({0: (-104, 0, 12), 0.1: (-60, 0, -70), 0.3: (-20, 0, -40), 0.6: (0, 0, 0)})),
            "lamp": bone(kf({0: (0, 160, 0), 0.3: (0, 260, 0), 0.6: (0, 360, 0)})),
            "arm_r": bone(kf({0: (58, 0, -14), 0.1: (40, 0, -70), 0.35: (35, 0, -62), 0.6: (0, 0, 0)})),
            "forearm_r": bone(kf({0: (30, 0, 0), 0.1: (10, 0, 0), 0.6: (0, 0, 0)}))}
    heat["hem"]["scale"] = sc({0: (1, 1, 1), 0.1: (1.22, 0.96, 1.22), 0.35: (1.08, 1, 1.08), 0.6: (1, 1, 1)})
    heat["lamp"]["scale"] = sc({0: (1.2, 1.2, 1.2), 0.1: (1.4, 1.4, 1.4), 0.6: (1, 1, 1)})
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.06: (10, 0, 4), 0.25: (0, 0, 0)})), "head": bone(kf({0: (0, 0, 0), 0.06: (12, 0, 0), 0.25: (0, 0, 0)})),
            "chain": bone(kf({0: (0, 0, 0), 0.08: (-30, 0, 10), 0.25: (0, 0, 0)})), "hem": bone(kf({0: (0, 0, 0), 0.06: (-5, 0, 0), 0.25: (0, 0, 0)}))}
    # 倒れる (1.4 秒): 膝が抜けて沈み (0.4)、前へ崩れ伏す (1.0)。裾はしぼみ、灯しは床へ落ちて転がる
    death = {"body": bone(kf({0: (0, 0, 0), 0.4: (-12, 0, 4), 1.0: (-82, 0, 8), 1.4: (-86, 0, 8)}),
                          kf({0: (0, 0, 0), 0.4: (0, -1.6, 0), 1.0: (0, -9.5, -1.0), 1.4: (0, -9.8, -1.0)}, False)),
             "hem": bone(kf({0: (0, 0, 0), 0.4: (14, 0, 0), 1.0: (30, 0, 0)})),
             "head": bone(kf({0: (0, 0, 0), 0.4: (-14, 0, 0), 1.0: (10, 0, 10)})),
             "hood_t1": bone(kf({0: (0, 0, 0), 1.0: (30, 0, 0)})), "hood_t2": bone(kf({0: (0, 0, 0), 1.0: (40, 0, 0)})),
             "arm_l": bone(kf({0: (0, 0, 0), 0.4: (20, 0, 10), 1.0: (150, 0, 20)})), "arm_r": bone(kf({0: (0, 0, 0), 0.4: (20, 0, -10), 1.0: (150, 0, -20)})),
             "chain": bone(kf({0: (0, 0, 0), 0.4: (-20, 0, 0), 1.0: (-150, 0, 0), 1.4: (-160, 0, 0)})),
             "lamp": bone(kf({0: (0, 0, 0), 1.0: (60, 40, 30), 1.4: (80, 60, 40)})),
             "leg_l": bone(kf({0: (0, 0, 0), 0.4: (-30, 0, 0), 1.0: (-80, 0, 0)}), kf({0: (0, 0, 0), 0.4: (0, -1.5, 0), 1.0: (0, -8.0, -2.0)}, False)),
             "leg_r": bone(kf({0: (0, 0, 0), 0.4: (-30, 0, 0), 1.0: (-80, 0, 0)}), kf({0: (0, 0, 0), 0.4: (0, -1.5, 0), 1.0: (0, -8.0, -2.0)}, False))}
    death["hem"]["scale"] = sc({0: (1, 1, 1), 0.4: (1.05, 0.9, 1.05), 1.0: (1.12, 0.62, 1.12)})
    return {p + "idle": loop(3.0, idle), p + "walk": loop(1.0, walk), p + "telegraph": once(0.8, telegraph, True),
            p + "heat": once(0.6, heat), p + "hurt": once(0.25, hurt), p + "death": once(1.4, death, True)}


def soot_child():
    """煤の子 (煤の団子): 腹・頭・顎・短い腕と足・煙の尾二節。噛む attack は予告 0.2 秒 (Tell の 4 tick): 0.1 で頭を反らして口を開け、
    0.2 で前へ飛びつきながら口を閉じる。歩きは左右に揺れる小走りで、足が交互に出る。"""
    p = "animation.soot_child."
    idle = {"body": bone(["0", "0", S(0, 3, 180)], [0, "math.abs(%s)*0.25" % S(0, 1, 180), 0]),
            "head": bone([S(60, 4, 180), S(30, 14, 90), "0"]), "jaw": bone([S(0, 4, 270), "0", "0"]),
            "arm_l": bone([S(0, 8, 180), "0", S(90, 5, 180)]), "arm_r": bone([S(180, 8, 180), "0", S(270, 5, 180)]),
            "wisp": bone([S(40, 8, 180), "0", S(130, 6, 120)]), "wisp_2": bone([S(100, 14, 180), "0", S(190, 10, 120)])}
    walk = legs_walk(["leg_l", "leg_r"], 900, 36, 0.6)
    walk["body"] = bone(["6", "0", S(0, 9, 900)], [0, "math.abs(%s)*0.5" % S(0, 1, 900), 0])
    walk["head"] = bone(["4", "0", S(180, 5, 900)])
    walk["arm_l"] = bone([S(180, 34, 900), "0", "12"])
    walk["arm_r"] = bone([S(0, 34, 900), "0", "-12"])
    walk["wisp"] = bone(["-20", "0", S(90, 10, 900)])
    walk["wisp_2"] = bone(["-24", "0", S(180, 14, 900)])
    attack = {"body": bone(kf({0: (0, 0, 0), 0.1: (10, 0, 0), 0.2: (-28, 0, 0), 0.3: (-18, 0, 0), 0.45: (0, 0, 0)}),
                           kf({0: (0, 0, 0), 0.1: (0, -0.3, 0.4), 0.2: (0, 0.6, -1.4), 0.3: (0, 0.2, -1.0), 0.45: (0, 0, 0)}, False)),
              "head": bone(kf({0: (0, 0, 0), 0.1: (22, 0, 0), 0.2: (-10, 0, 0), 0.45: (0, 0, 0)})),
              "jaw": bone(kf({0: (0, 0, 0), 0.1: (-42, 0, 0), 0.17: (-42, 0, 0), 0.21: (4, 0, 0), 0.45: (0, 0, 0)})),
              "arm_l": bone(kf({0: (0, 0, 0), 0.1: (40, 0, 30), 0.2: (95, 0, -10), 0.45: (0, 0, 0)})),
              "arm_r": bone(kf({0: (0, 0, 0), 0.1: (40, 0, -30), 0.2: (95, 0, 10), 0.45: (0, 0, 0)})),
              "wisp": bone(kf({0: (0, 0, 0), 0.2: (-35, 0, 0), 0.45: (0, 0, 0)})), "wisp_2": bone(kf({0: (0, 0, 0), 0.25: (-40, 0, 0), 0.45: (0, 0, 0)}))}
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.05: (16, 0, 8), 0.2: (0, 0, 0)}), kf({0: (0, 0, 0), 0.05: (0, 0.2, 0.5), 0.2: (0, 0, 0)}, False)),
            "head": bone(kf({0: (0, 0, 0), 0.05: (14, 0, 0), 0.2: (0, 0, 0)})), "jaw": bone(kf({0: (0, 0, 0), 0.05: (-25, 0, 0), 0.2: (0, 0, 0)}))}
    hurt["body"]["scale"] = sc({0: (1, 1, 1), 0.05: (1.12, 0.86, 1.12), 0.2: (1, 1, 1)})
    # 倒れる (0.8 秒): 横へ転がって (0.35)、煤の山にしぼむ (0.8)。口は開いたまま、目の熾が下を向く
    death = {"body": bone(kf({0: (0, 0, 0), 0.35: (10, 0, 80), 0.8: (6, 0, 92)}), kf({0: (0, 0, 0), 0.35: (0, -0.5, 0), 0.8: (0, -1.0, 0)}, False)),
             "head": bone(kf({0: (0, 0, 0), 0.35: (20, 0, 0), 0.8: (30, 0, 0)})), "jaw": bone(kf({0: (0, 0, 0), 0.35: (-36, 0, 0)})),
             "arm_l": bone(kf({0: (0, 0, 0), 0.35: (0, 0, 60), 0.8: (0, 0, 40)})), "arm_r": bone(kf({0: (0, 0, 0), 0.35: (0, 0, -60), 0.8: (0, 0, -40)})),
             "leg_l": bone(kf({0: (0, 0, 0), 0.35: (-50, 0, 0)})), "leg_r": bone(kf({0: (0, 0, 0), 0.35: (-30, 0, 20)})),
             "wisp": bone(kf({0: (0, 0, 0), 0.8: (-60, 0, 0)})), "wisp_2": bone(kf({0: (0, 0, 0), 0.8: (-50, 0, 0)}))}
    death["body"]["scale"] = sc({0: (1, 1, 1), 0.35: (1, 1, 1), 0.8: (1.15, 0.7, 1.15)})
    return {p + "idle": loop(2.0, idle), p + "walk": loop(0.4, walk), p + "attack": once(0.45, attack),
            p + "hurt": once(0.2, hurt), p + "death": once(0.8, death, True)}


def heartbeat_mimic():
    """鼓動もどき (膜の袋): 三つの膨らみが「どくん・どくん」と二拍ずつ打ち、口の四枚の花弁がわずかに息をする。根は床で時々ひきつる。
    呼ぶ lure (2 秒ごと): 二拍に合わせて花弁が開き、奥の青緑の芯が脈を打つ。噛む bite は予告 0.4 秒 (Tell の 8 tick): 0〜0.35 で花弁が大きく開き
    袋が息を吸ってふくらみ、0.4 で前へ跳ねて花弁を閉じる。倒れるは膨らみがしぼみ、袋がつぶれて花弁が垂れる。"""
    p = "animation.heartbeat_mimic."

    def beat(speed, amp, phase=0):
        # 二拍 (どくん・どくん): |sin| の山を二つずつ。式は 0〜amp
        return "math.pow(math.abs(math.sin(query.anim_time * %d + %d)), 6) * %s" % (speed, phase, amp)

    def scale_beat(speed, amp, phase=0):
        e = "1 + %s" % beat(speed, amp, phase)
        return [e, e, e]

    idle = {"body": {"scale": ["1 + %s" % beat(120, 0.03), "1 - %s" % beat(120, 0.02), "1 + %s" % beat(120, 0.03)]},
            "bulge_a": {"scale": scale_beat(120, 0.22, 0)}, "bulge_b": {"scale": scale_beat(120, 0.2, 25)},
            "bulge_c": {"scale": scale_beat(120, 0.2, 50)}, "core": {"scale": scale_beat(120, 0.25, 10)},
            "petal_t": bone([S(0, 4, 60), "0", "0"]), "petal_b": bone([S(0, -4, 60), "0", "0"]),
            "petal_l": bone(["0", S(0, -4, 60), "0"]), "petal_r": bone(["0", S(0, 4, 60), "0"]),
            "root_fl": bone(["0", "0", "math.pow(math.abs(math.sin(query.anim_time * 47)), 12) * 6"]),
            "root_fr": bone(["0", "0", "-math.pow(math.abs(math.sin(query.anim_time * 53 + 30)), 12) * 6"]),
            "root_bl": bone(["math.pow(math.abs(math.sin(query.anim_time * 41 + 60)), 12) * -5", "0", "0"]),
            "root_br": bone(["math.pow(math.abs(math.sin(query.anim_time * 59 + 90)), 12) * -5", "0", "0"])}
    # 呼ぶ (1.0 秒): 0.1 と 0.35 の二拍。花弁が開いて芯が光る
    two = {0: 1.0, 0.1: 1.35, 0.22: 1.0, 0.35: 1.28, 0.5: 1.0, 1.0: 1.0}
    lure = {"bulge_a": {"scale": sc({t: (v, v, v) for t, v in two.items()})},
            "bulge_b": {"scale": sc({t + (0.04 if 0 < t < 1 else 0): (v, v, v) for t, v in two.items()})},
            "bulge_c": {"scale": sc({t + (0.08 if 0 < t < 1 else 0): (v, v, v) for t, v in two.items()})},
            "core": {"scale": sc({0: (1, 1, 1), 0.1: (1.5, 1.5, 1.5), 0.25: (1.1, 1.1, 1.1), 0.35: (1.45, 1.45, 1.45), 0.6: (1, 1, 1)})},
            "body": {"scale": sc({0: (1, 1, 1), 0.1: (1.06, 0.97, 1.06), 0.22: (1, 1, 1), 0.35: (1.05, 0.98, 1.05), 0.5: (1, 1, 1)})},
            "petal_t": bone(kf({0: (0, 0, 0), 0.1: (24, 0, 0), 0.6: (18, 0, 0), 1.0: (0, 0, 0)})),
            "petal_b": bone(kf({0: (0, 0, 0), 0.1: (-24, 0, 0), 0.6: (-18, 0, 0), 1.0: (0, 0, 0)})),
            "petal_l": bone(kf({0: (0, 0, 0), 0.1: (0, -24, 0), 0.6: (0, -18, 0), 1.0: (0, 0, 0)})),
            "petal_r": bone(kf({0: (0, 0, 0), 0.1: (0, 24, 0), 0.6: (0, 18, 0), 1.0: (0, 0, 0)}))}
    # 噛む (0.8 秒、当たりは 0.4): 開く → 閉じて前へ跳ねる → 戻る
    op = 62
    bite = {"petal_t": bone(kf({0: (0, 0, 0), 0.3: (op, 0, 0), 0.37: (op + 6, 0, 0), 0.43: (-8, 0, 0), 0.55: (-4, 0, 0), 0.8: (0, 0, 0)})),
            "petal_b": bone(kf({0: (0, 0, 0), 0.3: (-op, 0, 0), 0.37: (-op - 6, 0, 0), 0.43: (8, 0, 0), 0.55: (4, 0, 0), 0.8: (0, 0, 0)})),
            "petal_l": bone(kf({0: (0, 0, 0), 0.3: (0, -op, 0), 0.37: (0, -op - 6, 0), 0.43: (0, 8, 0), 0.55: (0, 4, 0), 0.8: (0, 0, 0)})),
            "petal_r": bone(kf({0: (0, 0, 0), 0.3: (0, op, 0), 0.37: (0, op + 6, 0), 0.43: (0, -8, 0), 0.55: (0, -4, 0), 0.8: (0, 0, 0)})),
            "body": bone(kf({0: (0, 0, 0), 0.3: (6, 0, 0), 0.4: (-16, 0, 0), 0.55: (-12, 0, 0), 0.8: (0, 0, 0)}),
                         kf({0: (0, 0, 0), 0.3: (0, 0.3, 0.6), 0.4: (0, -0.2, -1.8), 0.55: (0, 0, -1.2), 0.8: (0, 0, 0)}, False)),
            "core": bone(None, kf({0: (0, 0, 0), 0.3: (0, 0, -0.5), 0.4: (0, 0, 0), 0.8: (0, 0, 0)}, False)),
            "root_fl": bone(kf({0: (0, 0, 0), 0.3: (0, 0, 8), 0.4: (0, 0, -6), 0.8: (0, 0, 0)})),
            "root_fr": bone(kf({0: (0, 0, 0), 0.3: (0, 0, -8), 0.4: (0, 0, 6), 0.8: (0, 0, 0)})),
            "root_bl": bone(kf({0: (0, 0, 0), 0.4: (-10, 0, 0), 0.8: (0, 0, 0)})), "root_br": bone(kf({0: (0, 0, 0), 0.4: (-10, 0, 0), 0.8: (0, 0, 0)}))}
    bite["body"]["scale"] = sc({0: (1, 1, 1), 0.3: (1.1, 1.08, 1.1), 0.4: (0.94, 0.96, 1.06), 0.55: (1, 1, 1)})
    bite["core"]["scale"] = sc({0: (1, 1, 1), 0.3: (1.5, 1.5, 1.5), 0.4: (1, 1, 1)})
    hurt = {"body": {"scale": sc({0: (1, 1, 1), 0.06: (1.1, 0.86, 1.1), 0.25: (1, 1, 1)})},
            "petal_t": bone(kf({0: (0, 0, 0), 0.06: (-10, 0, 0), 0.25: (0, 0, 0)})), "petal_b": bone(kf({0: (0, 0, 0), 0.06: (10, 0, 0), 0.25: (0, 0, 0)})),
            "bulge_a": {"scale": sc({0: (1, 1, 1), 0.06: (0.8, 0.8, 0.8), 0.25: (1, 1, 1)})},
            "bulge_b": {"scale": sc({0: (1, 1, 1), 0.06: (0.8, 0.8, 0.8), 0.25: (1, 1, 1)})}}
    death = {"body": bone(kf({0: (0, 0, 0), 0.5: (-8, 0, 6), 1.4: (-14, 0, 10)}), kf({0: (0, 0, 0), 0.5: (0, -0.8, 0), 1.4: (0, -2.4, 0)}, False)),
             "bulge_a": {"scale": sc({0: (1, 1, 1), 0.2: (1.3, 1.3, 1.3), 0.8: (0.35, 0.35, 0.35)})},
             "bulge_b": {"scale": sc({0: (1, 1, 1), 0.3: (1.25, 1.25, 1.25), 0.9: (0.35, 0.35, 0.35)})},
             "bulge_c": {"scale": sc({0: (1, 1, 1), 0.4: (1.2, 1.2, 1.2), 1.0: (0.35, 0.35, 0.35)})},
             "core": {"scale": sc({0: (1, 1, 1), 0.6: (0.3, 0.3, 0.3)})},
             "petal_t": bone(kf({0: (0, 0, 0), 0.6: (-20, 0, 0), 1.4: (-55, 0, 0)})), "petal_b": bone(kf({0: (0, 0, 0), 0.6: (-30, 0, 0), 1.4: (-40, 0, 0)})),
             "petal_l": bone(kf({0: (0, 0, 0), 1.4: (-30, -40, 0)})), "petal_r": bone(kf({0: (0, 0, 0), 1.4: (-30, 40, 0)})),
             "root_fl": bone(kf({0: (0, 0, 0), 1.4: (0, 0, 22)})), "root_fr": bone(kf({0: (0, 0, 0), 1.4: (0, 0, -22)})),
             "root_bl": bone(kf({0: (0, 0, 0), 1.4: (-20, 0, 14)})), "root_br": bone(kf({0: (0, 0, 0), 1.4: (-20, 0, -14)}))}
    death["body"]["scale"] = sc({0: (1, 1, 1), 0.5: (1.04, 0.92, 1.04), 1.4: (1.2, 0.5, 1.2)})
    return {p + "idle": loop(3.0, idle), p + "lure": once(1.0, lure), p + "bite": once(0.8, bite),
            p + "hurt": once(0.25, hurt), p + "death": once(1.4, death, True)}


ANIMS = {"ash_trail": ash_trail, "ember_keeper": ember_keeper, "soot_child": soot_child, "past_shadow": past_shadow,
         "nameless_shape": nameless_shape, "lamp_eater": lamp_eater, "ceiling_walker": ceiling_walker,
         "heartbeat_mimic": heartbeat_mimic, "rope_hanger": rope_hanger}


# ---------------------------------------------------------------- 素材を入れる
# 2026-10-23 に作り直した 3 種。ラフは 3 案で骨の表を共有するので、選んだ案に無い (面の無い、子も無い) 骨を落として入れる
REBUILT = ("ember_keeper", "soot_child", "heartbeat_mimic")


def strip_empty_bones(src, dst):
    m = json.load(open(src, encoding="utf-8"))
    bones = m["bones"]
    while True:
        parents = {b.get("parent") for b in bones.values()}
        drop = [n for n, b in bones.items() if n != "root" and not b["triangles"] and n not in parents]
        if not drop:
            break
        for n in drop:
            del bones[n]
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(m, f, ensure_ascii=False)
    return dst


def install(only=()):
    for mob, opt in CHOICE.items():
        if only and mob not in only:
            continue
        stem = "%s_%s" % (mob, opt)
        mesh = os.path.join(ROUGH, mob, opt, stem + "_mesh.json")
        if mob in REBUILT:
            mesh = strip_empty_bones(mesh, os.path.join(ROUGH, mob, opt, stem + "_mesh_used.json"))
            old_geo = os.path.join(A, "geo", "entity", mob + ".geo.json")
            if os.path.exists(old_geo):
                os.remove(old_geo)
        base = os.path.join(PAINT, mob + "_base_atlas.png")
        gmask = os.path.join(PAINT, mob + "_base_glowmask.png")
        subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "install/install_lowpoly.py"), mob, "--mesh", mesh, "--atlas", base,
                               "--glow", gmask], cwd=ROOT)
        if mob in REBUILT:
            os.remove(mesh)   # 骨を落とした一時の写し
        tex = os.path.join(A, "textures", "lowpoly")
        for tag in ("s1", "s2"):
            shutil.copyfile(os.path.join(PAINT, "%s_%s_atlas.png" % (mob, tag)), os.path.join(tex, "%s_%s.png" % (mob, tag)))
            g = os.path.join(PAINT, "%s_%s_glowmask.png" % (mob, tag))
            gd = os.path.join(tex, "%s_%s_glowmask.png" % (mob, tag))
            if os.path.exists(gd):
                os.remove(gd)
            shutil.copyfile(g, gd)
        # 箱の代用 (LowPoly.enabled = false)。発光マスクも同じく入れる
        ent = os.path.join(A, "textures", "entity")
        os.makedirs(ent, exist_ok=True)
        shutil.copyfile(os.path.join(A, "geo", "entity", mob + "_lowpoly_debug.geo.json"), os.path.join(A, "geo", "entity", mob + ".geo.json"))
        shutil.copyfile(os.path.join(tex, mob + ".png"), os.path.join(ent, mob + ".png"))
        gm = os.path.join(tex, mob + "_glowmask.png")
        if os.path.exists(gm):
            shutil.copyfile(gm, os.path.join(ent, mob + "_glowmask.png"))
        wjson(os.path.join(A, "models", "item", mob + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
        wjson(os.path.join(A, "animations", "entity", mob + ".animation.json"), {"format_version": "1.8.0", "animations": ANIMS[mob]()})


# ---------------------------------------------------------------- 音 (既存の音の別名。namespace skyabyss_abyss_b)
def ev(name, pitch, vol):
    return {"name": name if ":" in name else "minecraft:" + name, "type": "event", "pitch": pitch, "volume": vol}


SOUNDS = {
    "ash_trail_step": [ev("block.gravel.step", 0.7, 0.35)],
    "ash_trail_reveal": [ev("entity.warden.nearby_close", 1.6, 0.7)],
    "ash_trail_attack": [ev("block.sand.break", 0.6, 0.9)],
    "ember_keeper_idle": [ev("block.fire.ambient", 0.7, 0.5)],
    "ember_keeper_telegraph": [ev("block.fire.extinguish", 0.6, 0.9)],
    "ember_keeper_heat": [ev("entity.blaze.shoot", 0.6, 1.1)],
    "soot_child_idle": [ev("entity.silverfish.ambient", 0.6, 0.5)],
    "soot_child_bite": [ev("entity.silverfish.hurt", 1.2, 0.6)],
    "past_shadow_echo": [ev("entity.phantom.ambient", 0.8, 0.9)],
    "past_shadow_mirror": [ev("entity.enderman.stare", 0.8, 0.8)],
    "nameless_shape_hum": [ev("block.beacon.ambient", 0.6, 0.6)],
    "nameless_shape_shoot": [ev("block.amethyst_block.chime", 0.6, 0.9)],
    "lamp_eater_snuff": [ev("block.candle.extinguish", 0.8, 1.0)],
    "lamp_eater_hiss": [ev("entity.silverfish.hurt", 0.7, 0.8)],
    "ceiling_creak": [ev("block.chain.step", 0.6, 1.0)],
    "ceiling_drop": [ev("entity.zombie.attack_wooden_door", 1.4, 1.0)],
    "heartbeat_lure": [ev("entity.warden.heartbeat", 1.0, 1.0)],
    "heartbeat_bite": [ev("entity.zombie.attack_iron_door", 0.8, 1.0)],
    "rope_creak": [ev("block.chain.place", 0.5, 1.0)],
    "rope_pull": [ev("entity.lead_knot.place", 0.6, 1.0)],
}


def sounds():
    wjson(os.path.join(AB, "sounds.json"), {k: {"subtitle": "subtitles.skyabyss_abyss_b." + k, "sounds": v} for k, v in SOUNDS.items()})


# ---------------------------------------------------------------- 品の絵 (32x32。手描きではなく、形を決めて描く。深淵の材料 5 つ)
def icons():
    from PIL import Image, ImageDraw

    def canvas():
        return Image.new("RGBA", (32, 32), (0, 0, 0, 0))

    out = {}
    # 灯の灰: 灰の山 (粒の濃淡) と、消えた灯りの赤い残り火
    im = canvas()
    d = ImageDraw.Draw(im)
    d.ellipse((4, 14, 27, 28), fill=(96, 92, 90, 255))
    d.ellipse((7, 12, 24, 24), fill=(132, 126, 122, 255))
    for x, y in ((10, 18), (14, 21), (19, 16), (22, 22), (12, 14), (17, 24)):
        d.point((x, y), fill=(190, 184, 178, 255))
    d.ellipse((13, 7, 18, 12), fill=(255, 140, 48, 255))
    out["lamp_ash"] = im
    # 煤の団子: 黒い丸 (ふくらみの陰影)
    im = canvas()
    d = ImageDraw.Draw(im)
    d.ellipse((5, 6, 26, 27), fill=(30, 27, 26, 255))
    d.ellipse((8, 8, 20, 18), fill=(62, 56, 52, 255))
    d.point((11, 11), fill=(120, 112, 106, 255))
    out["soot_clump"] = im
    # 縄の結び目: 茶色の輪を二つ重ねる
    im = canvas()
    d = ImageDraw.Draw(im)
    d.ellipse((4, 8, 20, 24), outline=(112, 84, 52, 255), width=3)
    d.ellipse((12, 6, 28, 22), outline=(150, 116, 74, 255), width=3)
    d.ellipse((12, 13, 18, 19), fill=(84, 62, 38, 255))
    out["rope_knot"] = im
    # 影の欠片: 青灰の薄い破片 (縁は明るく)
    im = canvas()
    d = ImageDraw.Draw(im)
    d.polygon([(16, 3), (27, 16), (19, 29), (7, 22), (6, 9)], fill=(72, 94, 108, 200), outline=(170, 200, 216, 255))
    d.polygon([(16, 8), (22, 16), (17, 23), (11, 18)], fill=(110, 138, 152, 220))
    out["echo_shard"] = im
    # 鼓動の膜: 淡い赤の袋と、細い血管のような筋
    im = canvas()
    d = ImageDraw.Draw(im)
    d.ellipse((5, 5, 27, 27), fill=(178, 96, 104, 255), outline=(96, 50, 56, 255))
    d.line((9, 16, 16, 12, 23, 16), fill=(120, 60, 66, 255), width=1)
    d.line((16, 12, 16, 22), fill=(120, 60, 66, 255), width=1)
    d.ellipse((11, 10, 15, 14), fill=(230, 170, 176, 255))
    out["pulse_membrane"] = im
    return out


def item_textures():
    for name, im in icons().items():
        p = os.path.join(A, "textures", "item", name + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p)
        wjson(os.path.join(A, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/" + name}})


def main():
    only = set(sys.argv[1:])   # 名前を並べると、その敵の素材だけ入れ直す (音・品の絵は毎回書く)
    install(only)
    sounds()
    item_textures()
    print("done")


if __name__ == "__main__":
    main()
