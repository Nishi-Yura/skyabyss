#!/usr/bin/env python3
"""六章 区画 1「新月の谷」・区画 2「上弦の崖」の敵 3 種ずつと主の素材を入れる (docs/design/46 の 4.3・4.3.1・4.4・4.4.1)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/ch6/gen_moon_mobs.py [名前...]   (名前を並べるとその敵だけ)

入力: docs/review/moon_roughs/<id>/<案>/<id>_<案>_{mesh.json,atlas.png,glowmask.png}   (ラフの作り方は tools/rough/lowpoly/moon/)
出力 (assets/skyabyss/ の下):
  lowpoly/<id>.json, textures/lowpoly/<id>(.png|_glowmask.png), geo/entity/<id>_lowpoly(_debug).geo.json   (tools/install/install_lowpoly.py)
  textures/lowpoly/<id>_s1(.png|_glowmask.png), <id>_s2(...)   二段目・三段目の姿 (同じ骨・同じ UV。星の光の割れ目が広がる)
  geo/entity/<id>.geo.json + textures/entity/<id>.png         LowPoly.enabled = false のときの箱の代用
  animations/entity/<id>.animation.json
  models/item/<id>_spawn_egg.json
  assets/skyabyss_moon/sounds.json                            音の出来事 (既存の音の別名。字幕は lang)

選んだ案 (2026-10-28): 砂這い A 甲殻の掘り手・殻背の兎 B 欠けた三日月・影の綴じ手 C 裂けた帯・影抱き B 影の母。
  区画 2: 月跳ね A 岩の跳び虫・押し石 A 目のある輪石・崖這い B 岩背負いの蟹・月秤 A 天秤を担ぐ巨人。
回転の約束: kf の値で書き、ファイルへは (-x, -y, z)。kf の x が負 = 前へ傾く (垂れた腕なら前へ上がる、前を向いた顎なら先が上がる。
  深淵の前の私の影の「手を上げる」= -110 と同じ)。bone([...]) に直接書く文字の式はファイルの値なので逆 (正 = 前へ傾く)。位置はそのまま (y が上)。
"""
import json
import os
import random
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
AM = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_moon")
ROUGH = os.path.join(ROOT, "docs", "review", "moon_roughs")

CHOICE = {"dust_crawler": "a", "shell_hare": "b", "shadow_stitcher": "c", "shadow_embracer": "b",
          "moon_leaper": "a", "push_stone": "a", "cliff_creeper": "b", "moon_scale": "a"}


def wjson(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")


# ---------------------------------------------------------------- 動きの部品 (tools/gen/abyss/gen_abyss_mobs_b.py と同じ約束)
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
    out = {}
    for i, b in enumerate(names):
        side = -1 if "_l" in b else 1
        ph = 0 if (i % 2 == 0) == (side < 0) else 180
        out[b] = bone([S(ph, amp, speed), "0", "0"], [0, "math.abs(%s)" % S(ph, lift, speed), 0])
    return out


# ---------------------------------------------------------------- 砂這い (潜る・跳び出す・噛む)
CRAWL_LEGS = ["leg_l1", "leg_r1", "leg_l2", "leg_r2", "leg_l3", "leg_r3"]


def dust_crawler():
    p = "animation.dust_crawler."
    idle = {"head": bone(["0", S(0, 6, 80), "0"]), "jaw": bone([S(0, 4, 160), "0", "0"]), "tail": bone(["0", S(90, 8, 80), "0"])}
    for i, b in enumerate(CRAWL_LEGS):
        idle[b] = bone(["0", "0", S(i * 60, 3, 120)])
    walk = {}
    for i, b in enumerate(CRAWL_LEGS):
        ph = (i // 2) * 120 + (0 if "_l" in b else 180)
        walk[b] = bone(["0", S(ph, 18, 720), S(ph + 90, 8, 720)])
    walk["body"] = bone(["0", "0", S(0, 2, 720)])
    walk["tail"] = bone(["0", S(0, 10, 360), "0"])
    # 潜る: 頭から砂へ突っ込む (潜ったあとの沈みは描く側が体ごと下げる。MoonMobRenderers)
    burrow = {"body": bone(kf({0: (0, 0, 0), 0.25: (25, 0, 0), 0.5: (0, 0, 0)}), kf({0: (0, 5.5, 0), 0.25: (0, 3.0, 0), 0.5: (0, 0, 0)}, False))}
    # 跳び出す: 砂の下から前上へ (体を起こし、顎を開く)
    leap = {"body": bone(kf({0: (0, 0, 0), 0.15: (30, 0, 0), 0.45: (-10, 0, 0), 0.6: (0, 0, 0)}),
                         kf({0: (0, 0, 0), 0.15: (0, 1.0, 0), 0.6: (0, 0, 0)}, False)),
            "jaw": bone(kf({0: (0, 0, 0), 0.15: (35, 0, 0), 0.35: (-5, 0, 0), 0.6: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.15: (15, 0, 0), 0.6: (0, 0, 0)}))}
    for b in ("leg_l1", "leg_r1"):
        leap[b] = bone(kf({0: (0, 0, 0), 0.15: (40, 0, 0), 0.6: (0, 0, 0)}))
    bite = {"jaw": bone(kf({0: (0, 0, 0), 0.12: (35, 0, 0), 0.24: (-6, 0, 0), 0.4: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.12: (10, 0, 0), 0.24: (-8, 0, 0), 0.4: (0, 0, 0)})),
            "body": bone(None, kf({0: (0, 0, 0), 0.24: (0, 0, -1.5), 0.4: (0, 0, 0)}, False))}
    return {p + "idle": loop(2.0, idle), p + "walk": loop(0.5, walk), p + "burrow": once(0.5, burrow),
            p + "leap": once(0.6, leap), p + "attack": once(0.4, bite), p + "hurt": once(0.22, hurt_bones()),
            p + "death": once(0.8, death_bones(180, -0.5), True)}


# ---------------------------------------------------------------- 殻背の兎 (跳ぶ・宙で裏返って殻で落ちる)
def shell_hare():
    p = "animation.shell_hare."
    idle = {"ear_l": bone([S(0, 6, 140), "0", S(40, 5, 90)]), "ear_r": bone([S(180, 6, 140), "0", S(220, 5, 90)]),
            "head": bone([S(0, 3, 70), S(60, 6, 50), "0"]), "body": bone(None, [0, "math.abs(%s)*0.3" % S(0, 1, 90), 0])}
    hop = {"body": bone([S(0, 10, 540), "0", "0"], [0, "math.abs(%s)" % S(0, 3.0, 540), 0]),
           "leg_l": bone([S(180, 40, 540), "0", "0"]), "leg_r": bone([S(180, 40, 540), "0", "0"]),
           "arm_l": bone([S(0, 30, 540), "0", "0"]), "arm_r": bone([S(0, 30, 540), "0", "0"]),
           "ear_l": bone([S(90, 14, 540), "0", "0"]), "ear_r": bone([S(90, 14, 540), "0", "0"])}
    # 跳び上がり (0〜0.5 構え・跳ぶ)、宙で裏返る (0.5〜0.9)、落ちて押し潰す (描く側が影の円の予告)
    slam = {"body": bone(kf({0: (0, 0, 0), 0.3: (-15, 0, 0), 0.5: (20, 0, 0), 0.9: (180, 0, 0), 1.3: (180, 0, 0), 1.6: (360, 0, 0)}),
                         kf({0: (0, 0, 0), 0.3: (0, -1.0, 0), 0.9: (0, 6.0, 0), 1.3: (0, 6.0, 0), 1.6: (0, 0, 0)}, False)),
            "leg_l": bone(kf({0: (0, 0, 0), 0.3: (-40, 0, 0), 0.5: (60, 0, 0), 1.6: (0, 0, 0)})),
            "leg_r": bone(kf({0: (0, 0, 0), 0.3: (-40, 0, 0), 0.5: (60, 0, 0), 1.6: (0, 0, 0)})),
            "ear_l": bone(kf({0: (0, 0, 0), 0.5: (-60, 0, 0), 1.6: (0, 0, 0)})), "ear_r": bone(kf({0: (0, 0, 0), 0.5: (-60, 0, 0), 1.6: (0, 0, 0)}))}
    kick = {"leg_l": bone(kf({0: (0, 0, 0), 0.1: (-50, 0, 0), 0.25: (40, 0, 0), 0.4: (0, 0, 0)})),
            "leg_r": bone(kf({0: (0, 0, 0), 0.1: (-50, 0, 0), 0.25: (40, 0, 0), 0.4: (0, 0, 0)})),
            "body": bone(kf({0: (0, 0, 0), 0.1: (10, 0, 0), 0.25: (-15, 0, 0), 0.4: (0, 0, 0)}))}
    return {p + "idle": loop(3.0, idle), p + "walk": loop(0.667, hop), p + "slam": once(1.6, slam), p + "attack": once(0.4, kick),
            p + "hurt": once(0.22, hurt_bones()), p + "death": once(0.7, death_bones(140, -0.3), True)}


# ---------------------------------------------------------------- 影の綴じ手 (帯が揺れる・長い腕で掴んで引く)
def shadow_stitcher():
    p = "animation.shadow_stitcher."
    idle = {"body": bone(["0", "0", S(0, 4, 60)], [0, S(0, 0.4, 60), 0]), "head": bone([S(0, 4, 45), S(90, 10, 45), "0"]),
            "arm_l": bone([S(0, 6, 60), "0", S(30, 4, 45)]), "arm_r": bone([S(180, 6, 60), "0", S(210, 4, 45)])}
    glide = {"body": bone(["8", "0", S(0, 4, 200)], [0, S(0, 0.5, 200), 0]), "arm_l": bone(["-12", "0", S(0, 6, 200)]),
             "arm_r": bone(["-12", "0", S(180, 6, 200)]), "head": bone(["-6", "0", "0"])}
    # 掴む: 右腕を前へ長く伸ばし (0.5 の予告)、掴んで引き戻す
    reach = {"arm_r": bone(kf({0: (0, 0, 0), 0.5: (-95, 0, -8), 0.7: (-100, 0, -8), 1.1: (-20, 0, 0), 1.3: (0, 0, 0)}),
                           kf({0: (0, 0, 0), 0.5: (0, 0, -3.0), 0.7: (0, 0, -5.0), 1.1: (0, 0, 0)}, False)),
             "body": bone(kf({0: (0, 0, 0), 0.5: (-15, 0, 0), 1.1: (6, 0, 0), 1.3: (0, 0, 0)})),
             "head": bone(kf({0: (0, 0, 0), 0.5: (8, 0, 0), 1.3: (0, 0, 0)}))}
    slash = {"arm_l": bone(kf({0: (0, 0, 0), 0.15: (-110, 0, 20), 0.35: (-20, 0, -30), 0.5: (0, 0, 0)})),
             "body": bone(kf({0: (0, 0, 0), 0.15: (0, 15, 0), 0.35: (0, -15, 0), 0.5: (0, 0, 0)}))}
    # 星灯りに引き出された: 帯が縮み、身を竦める
    exposed = {"body": bone(kf({0: (0, 0, 0), 0.15: (15, 0, 0), 0.6: (5, 0, 0)}), kf({0: (0, 0, 0), 0.15: (0, -1.5, 0), 0.6: (0, -0.5, 0)}, False)),
               "arm_l": bone(kf({0: (0, 0, 0), 0.15: (-70, 0, 35)})), "arm_r": bone(kf({0: (0, 0, 0), 0.15: (-70, 0, -35)})),
               "head": bone(kf({0: (0, 0, 0), 0.15: (-20, 0, 0)}))}
    return {p + "idle": loop(4.0, idle), p + "walk": loop(1.8, glide), p + "reach": once(1.3, reach), p + "attack": once(0.5, slash),
            p + "exposed": once(0.6, exposed, True), p + "hurt": once(0.22, hurt_bones()), p + "death": once(1.0, death_bones(90, -2.5), True)}


# ---------------------------------------------------------------- 影抱き (影の母。珠を抱く四本の腕・影に潜る・掴む・投げる・波)
ARMS4 = ("arm_l", "arm_r", "arm_l2", "arm_r2")


def shadow_embracer():
    p = "animation.shadow_embracer."
    idle = {"body": bone(["0", "0", S(0, 2, 40)], [0, S(0, 0.6, 40), 0]), "head": bone([S(0, 3, 30), S(90, 6, 30), "0"])}
    for i, b in enumerate(ARMS4):
        idle[b] = bone([S(i * 90, 5, 50), "0", S(i * 90 + 45, 3, 50)])
    glide = {"body": bone(["6", "0", S(0, 3, 120)], [0, S(0, 0.6, 120), 0]), "head": bone(["-6", "0", "0"])}
    for i, b in enumerate(ARMS4):
        glide[b] = bone([S(i * 90, 8, 120), "0", "0"])
    # 影に潜る / 出る (床の影へ沈む。描く側が影を走らせる)
    sink = {"body": bone(kf({0: (0, 0, 0), 0.3: (-10, 0, 0), 0.9: (0, 0, 0)}), kf({0: (0, 0, 0), 0.9: (0, -60, 0)}, False))}
    rise = {"body": bone(kf({0: (0, 0, 0), 0.6: (-12, 0, 0), 0.9: (0, 0, 0)}), kf({0: (0, -60, 0), 0.6: (0, 2, 0), 0.9: (0, 0, 0)}, False))}
    for b in ARMS4:
        rise[b] = bone(kf({0: (0, 0, 0), 0.6: (-90, 0, 0), 0.9: (0, 0, 0)}))
    # 掴む: 上の二本を前へ伸ばす (0.7 の予告)、閉じる
    grab = {"arm_l": bone(kf({0: (0, 0, 0), 0.7: (-85, 0, 25), 0.9: (-95, 0, 5), 1.3: (0, 0, 0)}),
                          kf({0: (0, 0, 0), 0.7: (0, 0, -6), 0.9: (0, 0, -9), 1.3: (0, 0, 0)}, False)),
            "arm_r": bone(kf({0: (0, 0, 0), 0.7: (-85, 0, -25), 0.9: (-95, 0, -5), 1.3: (0, 0, 0)}),
                          kf({0: (0, 0, 0), 0.7: (0, 0, -6), 0.9: (0, 0, -9), 1.3: (0, 0, 0)}, False)),
            "body": bone(kf({0: (0, 0, 0), 0.7: (-18, 0, 0), 1.3: (0, 0, 0)}))}
    # 影を投げる: 下の右腕を振りかぶって投げる
    throw = {"arm_r2": bone(kf({0: (0, 0, 0), 0.5: (60, 0, -40), 0.7: (-110, 0, 10), 1.0: (0, 0, 0)})),
             "body": bone(kf({0: (0, 0, 0), 0.5: (0, -20, 0), 0.7: (0, 15, 0), 1.0: (0, 0, 0)}))}
    # 影の波: 四本を広げて掲げ (1.0 の予告)、床へ叩きつける
    wave = {"body": bone(kf({0: (0, 0, 0), 1.0: (15, 0, 0), 1.2: (-20, 0, 0), 1.7: (0, 0, 0)}),
                         kf({0: (0, 0, 0), 1.0: (0, 3, 0), 1.2: (0, -2, 0), 1.7: (0, 0, 0)}, False)),
            "head": bone(kf({0: (0, 0, 0), 1.0: (20, 0, 0), 1.2: (-15, 0, 0), 1.7: (0, 0, 0)}))}
    for b, side in (("arm_l", -1), ("arm_r", 1), ("arm_l2", -1), ("arm_r2", 1)):
        wave[b] = bone(kf({0: (0, 0, 0), 1.0: (-150, 0, side * -50), 1.2: (-40, 0, side * -20), 1.7: (0, 0, 0)}))
    # 星灯りで固まる: 身を反らせて止まる (斬れる間)
    frozen = {"body": bone(kf({0: (0, 0, 0), 0.2: (12, 0, 0)})), "head": bone(kf({0: (0, 0, 0), 0.2: (25, 0, 0)}))}
    for b, side in (("arm_l", -1), ("arm_r", 1), ("arm_l2", -1), ("arm_r2", 1)):
        frozen[b] = bone(kf({0: (0, 0, 0), 0.2: (-40, 0, side * -60)}))
    roar = {"body": bone(kf({0: (0, 0, 0), 0.4: (15, 0, 0), 1.6: (15, 0, 0), 2.0: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.4: (30, 0, 0), 1.6: (30, 0, 0), 2.0: (0, 0, 0)}))}
    for b, side in (("arm_l", -1), ("arm_r", 1), ("arm_l2", -1), ("arm_r2", 1)):
        roar[b] = bone(kf({0: (0, 0, 0), 0.4: (-70, 0, side * -70), 1.6: (-70, 0, side * -70), 2.0: (0, 0, 0)}))
    death = {"body": bone(kf({0: (0, 0, 0), 0.8: (20, 0, 0), 2.0: (-10, 0, 0)}), kf({0: (0, 0, 0), 0.8: (0, 1, 0), 2.0: (0, -40, 0)}, False)),
             "head": bone(kf({0: (0, 0, 0), 0.8: (40, 0, 0), 2.0: (10, 0, 0)}))}
    for b, side in (("arm_l", -1), ("arm_r", 1), ("arm_l2", -1), ("arm_r2", 1)):
        death[b] = bone(kf({0: (0, 0, 0), 0.8: (-120, 0, side * -40), 2.0: (0, 0, 0)}))
    return {p + "idle": loop(5.0, idle), p + "walk": loop(3.0, glide), p + "sink": once(0.9, sink, True), p + "rise": once(0.9, rise),
            p + "grab": once(1.3, grab), p + "throw": once(1.0, throw), p + "wave": once(1.7, wave), p + "frozen": once(0.2, frozen, True),
            p + "roar": once(2.0, roar), p + "hurt": once(0.22, hurt_bones()), p + "death": once(2.0, death, True)}


def scale(frames):
    return {"%.2f" % t: list(v) for t, v in frames.items()}


# ---------------------------------------------------------------- 月跳ね (岩の跳び虫。長い後ろ脚で跳びかかる・鎌の前腕で斬る)
def moon_leaper():
    p = "animation.moon_leaper."
    idle = {"head": bone([S(0, 4, 70), S(90, 8, 50), "0"]), "arm_l": bone([S(0, 5, 90), "0", "0"]), "arm_r": bone([S(180, 5, 90), "0", "0"]),
            "body": bone(None, [0, "math.abs(%s)*0.4" % S(0, 1, 80), 0])}
    walk = {"leg_l": bone([S(0, 22, 720), "0", "0"]), "leg_r": bone([S(180, 22, 720), "0", "0"]),
            "arm_l": bone([S(180, 14, 720), "0", "0"]), "arm_r": bone([S(0, 14, 720), "0", "0"]),
            "body": bone(["0", "0", S(0, 3, 720)], [0, "math.abs(%s)" % S(0, 0.8, 720), 0])}
    # 跳びかかる (2.0 秒): 屈む → 跳ぶ → 宙で脚を畳み鎌を下へ構える → 落ちて着く
    leap = {"body": bone(kf({0: (0, 0, 0), 0.2: (12, 0, 0), 0.3: (-25, 0, 0), 0.9: (20, 0, 0), 1.1: (35, 0, 0), 1.4: (0, 0, 0), 2.0: (0, 0, 0)}),
                         kf({0: (0, 0, 0), 0.2: (0, -2.0, 0), 0.3: (0, 1.0, 0), 1.4: (0, 0, 0)}, False)),
            "leg_l": bone(kf({0: (0, 0, 0), 0.2: (40, 0, 0), 0.3: (-55, 0, 0), 0.9: (60, 0, 0), 1.3: (20, 0, 0), 1.6: (0, 0, 0)})),
            "leg_r": bone(kf({0: (0, 0, 0), 0.2: (40, 0, 0), 0.3: (-55, 0, 0), 0.9: (60, 0, 0), 1.3: (20, 0, 0), 1.6: (0, 0, 0)})),
            "arm_l": bone(kf({0: (0, 0, 0), 0.3: (-60, 0, 10), 0.9: (-110, 0, 15), 1.3: (30, 0, 0), 1.7: (0, 0, 0)})),
            "arm_r": bone(kf({0: (0, 0, 0), 0.3: (-60, 0, -10), 0.9: (-110, 0, -15), 1.3: (30, 0, 0), 1.7: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.3: (-15, 0, 0), 1.1: (20, 0, 0), 1.6: (0, 0, 0)}))}
    # 斬る: 右の鎌を振りかぶって下ろし、左が追う
    slash = {"arm_r": bone(kf({0: (0, 0, 0), 0.15: (-110, 0, 20), 0.28: (35, 0, -10), 0.5: (0, 0, 0)})),
             "arm_l": bone(kf({0: (0, 0, 0), 0.2: (-90, 0, -15), 0.35: (30, 0, 10), 0.55: (0, 0, 0)})),
             "body": bone(kf({0: (0, 0, 0), 0.15: (0, 15, 0), 0.3: (10, -15, 0), 0.55: (0, 0, 0)}))}
    return {p + "idle": loop(2.5, idle), p + "walk": loop(0.5, walk), p + "leap": once(2.0, leap), p + "attack": once(0.55, slash),
            p + "hurt": once(0.22, hurt_bones()), p + "death": once(0.8, death_bones(170, -0.5), True)}


# ---------------------------------------------------------------- 押し石 (目のある輪石。輪がいつも回り、溜めると速く、押すと輪が膨らむ)
def push_stone():
    p = "animation.push_stone."
    idle = {"ring": bone(["0", "0", "query.anim_time * 40"]), "core": bone([S(0, 6, 60), S(90, 10, 40), "0"], [0, S(0, 0.5, 70), 0])}
    walk = {"ring": bone(["0", "0", "query.anim_time * 90"]), "core": bone([S(0, 6, 90), "0", "0"])}
    charge = {"ring": bone(["0", "0", "query.anim_time * 720"]), "core": dict(bone(kf({0: (0, 0, 0), 1.0: (0, 0, 0)})),
                                                                             scale=scale({0: (1, 1, 1), 0.8: (1.25, 1.25, 1.25), 1.0: (1.3, 1.3, 1.3)}))}
    push = {"ring": dict(bone(kf({0: (0, 0, 0), 0.4: (0, 0, 120)})), scale=scale({0: (1, 1, 1), 0.12: (1.45, 1.45, 1.45), 0.4: (1, 1, 1)})),
            "core": dict(bone(kf({0: (0, 0, 0), 0.12: (0, 0, 0)})), scale=scale({0: (1.3, 1.3, 1.3), 0.12: (0.8, 0.8, 0.8), 0.4: (1, 1, 1)}))}
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.06: (0, 0, 12), 0.22: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.6: (80, 0, 30), 1.0: (90, 0, 40)}), kf({0: (0, 0, 0), 0.6: (0, -6, 0), 1.0: (0, -9, 0)}, False))}
    return {p + "idle": loop(9.0, idle), p + "walk": loop(4.0, walk), p + "charge": once(1.0, charge, True), p + "push": once(0.4, push),
            p + "hurt": once(0.22, hurt), p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 崖這い (岩背負いの蟹。背の岩を鋏で外して投げる・挟む)
def cliff_creeper():
    p = "animation.cliff_creeper."
    idle = {"arm_l": bone(["0", S(0, 6, 60), "0"]), "arm_r": bone(["0", S(180, 6, 60), "0"]), "head": bone([S(0, 3, 50), "0", "0"]),
            "body": bone(None, [0, "math.abs(%s)*0.3" % S(0, 1, 60), 0])}
    walk = {"leg_l": bone([S(0, 25, 600), "0", S(90, 8, 600)]), "leg_r": bone([S(180, 25, 600), "0", S(270, 8, 600)]),
            "arm_l": bone([S(180, 10, 600), "0", "0"]), "arm_r": bone([S(0, 10, 600), "0", "0"]), "body": bone(["0", "0", S(0, 4, 600)])}
    # 岩落とし: 鋏を背へ回して岩を持ち上げ (0.3)、前へ放る (0.45)。そのあと背の岩は描かない (描く側)
    throw = {"arm_l": bone(kf({0: (0, 0, 0), 0.3: (-150, 0, 20), 0.45: (-40, 0, 10), 0.8: (0, 0, 0)})),
             "arm_r": bone(kf({0: (0, 0, 0), 0.3: (-150, 0, -20), 0.45: (-40, 0, -10), 0.8: (0, 0, 0)})),
             "rock": bone(kf({0: (0, 0, 0), 0.3: (-20, 0, 0), 0.45: (-60, 0, 0)}), kf({0: (0, 0, 0), 0.3: (0, 3.5, 1.0), 0.45: (0, 5.0, -6.0)}, False)),
             "body": bone(kf({0: (0, 0, 0), 0.3: (15, 0, 0), 0.45: (-10, 0, 0), 0.8: (0, 0, 0)}))}
    pinch = {"arm_r": bone(kf({0: (0, 0, 0), 0.15: (-35, 25, 0), 0.28: (-20, -10, 0), 0.45: (0, 0, 0)})),
             "arm_l": bone(kf({0: (0, 0, 0), 0.15: (-35, -25, 0), 0.28: (-20, 10, 0), 0.45: (0, 0, 0)})),
             "body": bone(kf({0: (0, 0, 0), 0.15: (-8, 0, 0), 0.45: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.5: (0, 0, 170), 0.9: (0, 0, 180)}), kf({0: (0, 0, 0), 0.5: (0, 3, 0), 0.9: (0, 2, 0)}, False))}
    return {p + "idle": loop(3.0, idle), p + "walk": loop(0.6, walk), p + "throw": once(0.8, throw), p + "attack": once(0.45, pinch),
            p + "hurt": once(0.22, hurt_bones()), p + "death": once(0.9, death, True)}


# ---------------------------------------------------------------- 月秤 (天秤を担ぐ巨人。錘を投げる・傾ける・叩き割る・薙ぐ・崩れる)
def moon_scale():
    p = "animation.moon_scale."
    idle = {"body": bone([S(0, 1.5, 30), "0", "0"], [0, S(0, 0.4, 30), 0]), "beam": bone(["0", "0", S(0, 3, 25)]),
            "head": bone([S(90, 3, 20), S(0, 8, 15), "0"])}
    walk = {"leg_l": bone([S(0, 18, 240), "0", "0"]), "leg_r": bone([S(180, 18, 240), "0", "0"]),
            "body": bone(["0", "0", S(0, 3, 240)], [0, "math.abs(%s)" % S(0, 0.8, 240), 0]), "beam": bone(["0", "0", S(90, 5, 240)])}
    # 錘を投げる (1.3): 右の皿の側を振り上げて、前へ放る
    throw = {"beam": bone(kf({0: (0, 0, 0), 0.5: (0, 0, 18), 0.8: (0, 0, -10), 1.3: (0, 0, 0)})),
             "arm_r": bone(kf({0: (0, 0, 0), 0.5: (-40, 0, -20), 0.8: (30, 0, 0), 1.3: (0, 0, 0)})),
             "body": bone(kf({0: (0, 0, 0), 0.5: (0, -20, 0), 0.8: (8, 15, 0), 1.3: (0, 0, 0)}))}
    # 傾ける (5.0): 棹を担ぎ上げて (1.5) 一方へ傾け、保つ (4.5 まで)
    tilt = {"beam": bone(kf({0: (0, 0, 0), 1.5: (0, 0, 28), 4.5: (0, 0, 28), 5.0: (0, 0, 0)}),
                         kf({0: (0, 0, 0), 1.0: (0, 3, 0), 1.5: (0, 2, 0), 4.5: (0, 2, 0), 5.0: (0, 0, 0)}, False)),
            "body": bone(kf({0: (0, 0, 0), 1.5: (8, 0, 10), 4.5: (8, 0, 10), 5.0: (0, 0, 0)})),
            "arm_l": bone(kf({0: (0, 0, 0), 1.5: (-30, 0, 20), 4.5: (-30, 0, 20), 5.0: (0, 0, 0)})),
            "arm_r": bone(kf({0: (0, 0, 0), 1.5: (-10, 0, -10), 4.5: (-10, 0, -10), 5.0: (0, 0, 0)})),
            "leg_l": bone(kf({0: (0, 0, 0), 1.5: (15, 0, 0), 4.5: (15, 0, 0), 5.0: (0, 0, 0)}))}
    # 叩き割り (2.2): 屈む → 跳ぶ → 叩きつける
    slam = {"body": bone(kf({0: (0, 0, 0), 0.4: (15, 0, 0), 0.6: (-15, 0, 0), 1.4: (-10, 0, 0), 1.5: (25, 0, 0), 2.2: (0, 0, 0)}),
                         kf({0: (0, 0, 0), 0.4: (0, -4, 0), 0.6: (0, 2, 0), 1.5: (0, -3, 0), 2.2: (0, 0, 0)}, False)),
            "beam": bone(kf({0: (0, 0, 0), 0.6: (-40, 0, 0), 1.4: (-60, 0, 0), 1.5: (30, 0, 0), 2.2: (0, 0, 0)})),
            "leg_l": bone(kf({0: (0, 0, 0), 0.4: (35, 0, 0), 0.6: (-20, 0, 0), 1.5: (30, 0, 0), 2.2: (0, 0, 0)})),
            "leg_r": bone(kf({0: (0, 0, 0), 0.4: (35, 0, 0), 0.6: (-20, 0, 0), 1.5: (30, 0, 0), 2.2: (0, 0, 0)}))}
    # 薙ぎ払い (1.4): 棹ごと体を引いて (0.8) 前へ振り抜く
    sweep = {"body": bone(kf({0: (0, 0, 0), 0.8: (0, -55, 0), 1.0: (0, 65, 0), 1.4: (0, 0, 0)})),
             "beam": bone(kf({0: (0, 0, 0), 0.8: (0, 0, -12), 1.0: (0, 0, 10), 1.4: (0, 0, 0)}))}
    # 崩れる (5.0): 膝をつき、前へ傾いて棹が落ちる。保って (4.5)、立ち直る
    topple = {"body": bone(kf({0: (0, 0, 0), 0.5: (30, 0, 0), 4.5: (30, 0, 0), 5.0: (0, 0, 0)}),
                           kf({0: (0, 0, 0), 0.5: (0, -7, 0), 4.5: (0, -7, 0), 5.0: (0, 0, 0)}, False)),
              "leg_l": bone(kf({0: (0, 0, 0), 0.5: (-70, 0, 0), 4.5: (-70, 0, 0), 5.0: (0, 0, 0)})),
              "leg_r": bone(kf({0: (0, 0, 0), 0.5: (-20, 0, 0), 4.5: (-20, 0, 0), 5.0: (0, 0, 0)})),
              "beam": bone(kf({0: (0, 0, 0), 0.5: (20, 0, -30), 4.5: (20, 0, -30), 5.0: (0, 0, 0)})),
              "head": bone(kf({0: (0, 0, 0), 0.5: (25, 0, 0), 4.5: (25, 0, 0), 5.0: (0, 0, 0)}))}
    roar = {"beam": bone(kf({0: (0, 0, 0), 0.5: (0, 0, 0), 1.6: (0, 0, 0), 2.0: (0, 0, 0)}), kf({0: (0, 0, 0), 0.5: (0, 6, 0), 1.6: (0, 6, 0), 2.0: (0, 0, 0)}, False)),
            "arm_l": bone(kf({0: (0, 0, 0), 0.5: (-60, 0, 25), 1.6: (-60, 0, 25), 2.0: (0, 0, 0)})),
            "arm_r": bone(kf({0: (0, 0, 0), 0.5: (-60, 0, -25), 1.6: (-60, 0, -25), 2.0: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.5: (-30, 0, 0), 1.6: (-30, 0, 0), 2.0: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.8: (-20, 0, 10), 2.0: (-80, 0, 15)}), kf({0: (0, 0, 0), 0.8: (0, -2, 0), 2.0: (0, -18, 0)}, False)),
             "beam": bone(kf({0: (0, 0, 0), 0.8: (0, 0, 30), 2.0: (40, 0, 60)}))}
    return {p + "idle": loop(6.0, idle), p + "walk": loop(1.5, walk), p + "throw": once(1.3, throw), p + "tilt": once(5.0, tilt),
            p + "slam": once(2.2, slam), p + "sweep": once(1.4, sweep), p + "topple": once(5.0, topple), p + "roar": once(2.0, roar),
            p + "hurt": once(0.22, hurt_bones()), p + "death": once(2.0, death, True)}


ANIMS = {"dust_crawler": dust_crawler, "shell_hare": shell_hare, "shadow_stitcher": shadow_stitcher, "shadow_embracer": shadow_embracer,
         "moon_leaper": moon_leaper, "push_stone": push_stone, "cliff_creeper": cliff_creeper, "moon_scale": moon_scale}


# ---------------------------------------------------------------- 素材を入れる
def strip_empty_bones(src, dst):
    """3 案で骨の表を共有するので、選んだ案に無い (面の無い、子も無い) 骨を落とす"""
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


def stage_atlases(mob, atlas, glow, tex):
    """二段目・三段目の絵: 体 (光らない画素) に星の光の割れ目を足していく。割れ目の画素は光る (発光マスクにも足す)"""
    from PIL import Image
    base = Image.open(atlas).convert("RGBA")
    gm = Image.open(glow).convert("L") if os.path.exists(glow) else Image.new("L", base.size, 0)
    W, H = base.size
    px = base.load()
    rng = random.Random(hash(mob) & 0xffff)
    for tag, n in (("s1", 1), ("s2", 3)):
        im = base.copy()
        g = gm.copy()
        ip, gp = im.load(), g.load()
        cracks = int(W * H / 900) * n
        for _ in range(cracks):
            x, y = rng.randrange(W), rng.randrange(H)
            if px[x, y][3] < 200 or gm.getpixel((x, y)) > 0:
                continue
            for _ in range(rng.randint(3, 7)):
                if 0 <= x < W and 0 <= y < H and px[x, y][3] >= 200:
                    ip[x, y] = (200, 228, 255, 255)
                    gp[x, y] = 255
                x += rng.choice((-1, 0, 1))
                y += rng.choice((-1, 0, 1))
        im.save(os.path.join(tex, "%s_%s.png" % (mob, tag)))
        # 光る画素の色は絵の色 (install_lowpoly と同じ: 発光マスクの白 = 光る)
        g.save(os.path.join(tex, "%s_%s_glowmask.png" % (mob, tag)))


def install(only=()):
    for mob, opt in CHOICE.items():
        if only and mob not in only:
            continue
        stem = "%s_%s" % (mob, opt)
        d = os.path.join(ROUGH, mob, opt)
        mesh = strip_empty_bones(os.path.join(d, stem + "_mesh.json"), os.path.join(d, stem + "_mesh_used.json"))
        atlas = os.path.join(d, stem + "_atlas.png")
        glow = os.path.join(d, stem + "_glowmask.png")
        subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "install/install_lowpoly.py"), mob, "--mesh", mesh, "--atlas", atlas,
                               "--glow", glow], cwd=ROOT)
        os.remove(mesh)
        tex = os.path.join(A, "textures", "lowpoly")
        stage_atlases(mob, atlas, glow, tex)
        ent = os.path.join(A, "textures", "entity")
        os.makedirs(ent, exist_ok=True)
        shutil.copyfile(os.path.join(A, "geo", "entity", mob + "_lowpoly_debug.geo.json"), os.path.join(A, "geo", "entity", mob + ".geo.json"))
        shutil.copyfile(os.path.join(tex, mob + ".png"), os.path.join(ent, mob + ".png"))
        gm = os.path.join(tex, mob + "_glowmask.png")
        if os.path.exists(gm):
            shutil.copyfile(gm, os.path.join(ent, mob + "_glowmask.png"))
        wjson(os.path.join(A, "models", "item", mob + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
        wjson(os.path.join(A, "animations", "entity", mob + ".animation.json"), {"format_version": "1.8.0", "animations": ANIMS[mob]()})


# ---------------------------------------------------------------- 音 (既存の音の別名。namespace skyabyss_moon)
def ev(name, pitch, vol):
    return {"name": name if ":" in name else "minecraft:" + name, "type": "event", "pitch": pitch, "volume": vol}


SOUNDS = {
    "crawler_dig": [ev("block.sand.break", 0.6, 0.6)],
    "crawler_burst": [ev("block.suspicious_sand.break", 0.6, 1.0)],
    "crawler_bite": [ev("entity.silverfish.hurt", 0.6, 0.9)],
    "hare_hop": [ev("entity.rabbit.jump", 0.7, 0.7)],
    "hare_slam": [ev("block.basalt.break", 0.5, 1.2)],
    "hare_idle": [ev("entity.rabbit.ambient", 0.6, 0.5)],
    "stitcher_whisper": [ev("entity.allay.ambient_without_item", 0.4, 0.5)],
    "stitcher_reach": [ev("entity.phantom.swoop", 0.6, 0.9)],
    "stitcher_exposed": [ev("block.amethyst_cluster.break", 0.7, 1.0)],
    "embracer_hum": [ev("block.respawn_anchor.ambient", 0.5, 0.8)],
    "embracer_sink": [ev("block.sculk.spread", 0.5, 1.2)],
    "embracer_grab": [ev("entity.warden.attack_impact", 0.7, 1.0)],
    "embracer_throw": [ev("entity.wither.shoot", 0.6, 0.8)],
    "embracer_wave": [ev("entity.warden.sonic_charge", 0.7, 1.0)],
    "embracer_frozen": [ev("block.glass.break", 0.6, 1.2)],
    "embracer_roar": [ev("entity.warden.roar", 0.8, 1.2)],
    "star_lamp_place": [ev("block.amethyst_block.chime", 1.4, 1.0)],
    "star_lamp_fade": [ev("block.amethyst_block.resonate", 0.8, 0.7)],
    "shadow_bridge_fade": [ev("block.sculk_vein.break", 0.7, 0.8)],
    "mirror_turn": [ev("block.grindstone.use", 1.4, 0.7)],
    "mirror_align": [ev("block.beacon.activate", 1.6, 0.9)],
    # 区画 2「上弦の崖」
    "leaper_jump": [ev("entity.goat.long_jump", 0.8, 0.9)],
    "leaper_land": [ev("block.basalt.break", 0.6, 1.1)],
    "stone_charge": [ev("block.respawn_anchor.charge", 1.4, 0.8)],
    "stone_push": [ev("entity.breeze.wind_burst", 0.7, 1.2)],
    "creeper_lift": [ev("block.stone.break", 0.6, 0.8)],
    "creeper_rock": [ev("block.deepslate.break", 0.5, 1.3)],
    "scale_creak": [ev("block.chain.step", 0.5, 0.7)],
    "scale_throw": [ev("entity.iron_golem.attack", 0.6, 1.0)],
    "scale_weight": [ev("block.anvil.land", 0.6, 0.9)],
    "scale_tilt": [ev("block.grindstone.use", 0.5, 1.0)],
    "scale_slam": [ev("entity.warden.attack_impact", 0.6, 1.2)],
    "scale_sweep": [ev("entity.player.attack.sweep", 0.6, 1.0)],
    "scale_topple": [ev("block.anvil.destroy", 0.6, 1.0)],
    "scale_roar": [ev("entity.ravager.roar", 0.6, 1.2)],
    "ledge_shake": [ev("block.suspicious_gravel.break", 0.8, 0.7)],
    "ledge_fall": [ev("block.tuff.break", 0.7, 1.0)],
    "ledge_back": [ev("block.tuff.place", 1.2, 0.6)],
    "plate_move": [ev("block.piston.contract", 0.6, 0.7)],
    "weight_set": [ev("block.anvil.place", 1.4, 0.6)],
    "light_body": [ev("entity.breeze.jump", 1.4, 0.8)],
}


def sounds():
    wjson(os.path.join(AM, "sounds.json"), {k: {"subtitle": "subtitles.skyabyss_moon." + k, "sounds": v} for k, v in SOUNDS.items()})


def main():
    only = set(sys.argv[1:])
    install(only)
    sounds()
    print("done")


if __name__ == "__main__":
    main()
