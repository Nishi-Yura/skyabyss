#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""終章「雲海」の島の敵と主の素材を入れる (docs/design/50 の 4.2)。月の敵 (tools/gen/ch6/gen_moon_mobs.py) と同じ流れ・同じ動きの部品。

  PYTHONIOENCODING=utf-8 python -X utf8 tools/gen/finale/gen_cloud_mobs.py [名前...]   (名前を並べるとその敵だけ)

入力: docs/review/cloud_roughs/<id>/a/<id>_a_{mesh.json,atlas.png,glowmask.png}   (ラフの作り方は tools/rough/lowpoly/cloud/。一品一案)
出力 (assets/skyabyss/ の下):
  lowpoly/<id>.json, textures/lowpoly/<id>(.png|_glowmask.png), geo/entity/<id>_lowpoly(_debug).geo.json   (tools/install/install_lowpoly.py)
  textures/lowpoly/<id>_s1(...), <id>_s2(...)   二段目・三段目の姿 (同じ骨・同じ UV。朝の光の割れ目が広がる)
  geo/entity/<id>.geo.json + textures/entity/<id>.png   LowPoly.enabled = false のときの箱の代用
  animations/entity/<id>.animation.json, models/item/<id>_spawn_egg.json
  assets/skyabyss_finale/sounds.json            音の出来事 (既存の音の別名。字幕は lang)

島 1「鐘の島」(2026-10-30): 鐘の亡者・霞鳥・最後の鐘守。島 2「灯の島」(2026-10-30): 網打ち・灯蛾・灯守の目。
回転の約束は gen_moon_mobs と同じ (kf の x が負 = 前へ傾く)。
"""
import importlib.util
import json
import os
import random
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
_spec = importlib.util.spec_from_file_location("gen_moon_mobs", os.path.join(ROOT, "tools", "gen", "ch6", "gen_moon_mobs.py"))
M = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(M)
rot, S, loop, once, kf, bone, wjson, ev = M.rot, M.S, M.loop, M.once, M.kf, M.bone, M.wjson, M.ev
hurt_bones, death_bones, strip_empty_bones = M.hurt_bones, M.death_bones, M.strip_empty_bones

A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
AF = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_finale")
ROUGH = os.path.join(ROOT, "docs", "review", "cloud_roughs")

CHOICE = {"bell_mourner": "a", "mist_bird": "a", "last_bellkeeper": "a", "net_caster": "a", "lamp_moth": "a", "lamp_eye": "a"}


# ---------------------------------------------------------------- 鐘の亡者 (よろよろ歩く・鐘を振り上げて鳴らす)
def bell_mourner():
    p = "animation.bell_mourner."
    idle = {"body": bone([S(0, 2, 40), "0", S(90, 2, 30)]), "head": bone([S(90, 4, 30), S(0, 10, 20), "0"]),
            "bell": bone([S(0, 3, 50), "0", S(90, 3, 40)])}
    walk = {"leg_l": bone([S(0, 20, 200), "0", "0"]), "leg_r": bone([S(180, 20, 200), "0", "0"]),
            "body": bone(["0", "0", S(0, 4, 200)], [0, S(0, 0.4, 400), 0]), "bell": bone([S(90, 6, 200), "0", "0"]),
            "head": bone([S(0, 4, 200), "0", "0"])}
    # 鳴らす (1.6): 1.0 まで鐘を頭上へ振り上げ (予告)、1.1 で振り下ろして鳴らす
    ring = {"arm_l": bone(kf({0: (0, 0, 0), 1.0: (-120, 0, -10), 1.15: (-40, 0, -5), 1.6: (0, 0, 0)})),
            "arm_r": bone(kf({0: (0, 0, 0), 1.0: (-120, 0, 10), 1.15: (-40, 0, 5), 1.6: (0, 0, 0)})),
            "bell": bone(kf({0: (0, 0, 0), 1.0: (-20, 0, 0), 1.15: (25, 0, 0), 1.3: (-10, 0, 0), 1.6: (0, 0, 0)}),
                         kf({0: (0, 0, 0), 1.0: (0, 7, 2), 1.15: (0, 2, 0), 1.6: (0, 0, 0)}, False)),
            "body": bone(kf({0: (0, 0, 0), 1.0: (-8, 0, 0), 1.15: (10, 0, 0), 1.6: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 1.0: (-15, 0, 0), 1.15: (5, 0, 0), 1.6: (0, 0, 0)}))}
    # 打つ (0.8): 鐘で殴る
    strike = {"arm_l": bone(kf({0: (0, 0, 0), 0.35: (-60, 0, 0), 0.5: (20, 0, 0), 0.8: (0, 0, 0)})),
              "arm_r": bone(kf({0: (0, 0, 0), 0.35: (-60, 0, 0), 0.5: (20, 0, 0), 0.8: (0, 0, 0)})),
              "bell": bone(kf({0: (0, 0, 0), 0.35: (-30, 0, 0), 0.5: (30, 0, 0), 0.8: (0, 0, 0)})),
              "body": bone(kf({0: (0, 0, 0), 0.35: (-6, 0, 0), 0.5: (14, 0, 0), 0.8: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.6: (30, 0, 0), 1.2: (85, 0, 5)}), kf({0: (0, 0, 0), 1.2: (0, -8, 0)}, False)),
             "bell": bone(kf({0: (0, 0, 0), 0.6: (40, 0, 0)}), kf({0: (0, 0, 0), 0.6: (0, -3, -2)}, False))}
    return {p + "idle": loop(6.0, idle), p + "walk": loop(1.8, walk), p + "ring": once(1.6, ring), p + "strike": once(0.8, strike),
            p + "hurt": once(0.22, hurt_bones()), p + "death": once(1.2, death, True)}


# ---------------------------------------------------------------- 霞鳥 (舞う・急降下)
def mist_bird():
    p = "animation.mist_bird."
    idle = {"wing_l": bone(["0", "0", S(0, 16, 180)]), "wing_r": bone(["0", "0", S(180, 16, 180)]),
            "tail": bone([S(0, 8, 90), S(90, 12, 70), "0"]), "body": bone([S(0, 3, 90), "0", "0"], [0, S(0, 0.6, 90), 0]),
            "head": bone([S(90, 4, 60), S(0, 8, 40), "0"])}
    walk = {"wing_l": bone(["0", "0", S(0, 26, 300)]), "wing_r": bone(["0", "0", S(180, 26, 300)]),
            "tail": bone([S(0, 10, 150), "0", "0"]), "body": bone([S(0, 4, 300), "0", "0"])}
    # 急降下 (1.4): 0.8 まで翼を広げて構え (予告)、そこから畳んで突っ込む
    dive = {"wing_l": bone(kf({0: (0, 0, 0), 0.8: (0, 0, -30), 0.9: (0, -40, 45), 1.3: (0, -40, 45), 1.4: (0, 0, 0)})),
            "wing_r": bone(kf({0: (0, 0, 0), 0.8: (0, 0, 30), 0.9: (0, 40, -45), 1.3: (0, 40, -45), 1.4: (0, 0, 0)})),
            "body": bone(kf({0: (0, 0, 0), 0.8: (15, 0, 0), 0.9: (-35, 0, 0), 1.3: (-35, 0, 0), 1.4: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.8: (-15, 0, 0), 0.9: (10, 0, 0), 1.4: (0, 0, 0)})),
            "tail": bone(kf({0: (0, 0, 0), 0.8: (-10, 0, 0), 0.9: (15, 0, 0), 1.4: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.6: (60, 0, 120), 1.0: (90, 0, 180)}), kf({0: (0, 0, 0), 1.0: (0, -14, 0)}, False)),
             "wing_l": bone(kf({0: (0, 0, 0), 0.6: (0, 0, 50)})), "wing_r": bone(kf({0: (0, 0, 0), 0.6: (0, 0, -50)}))}
    return {p + "idle": loop(2.0, idle), p + "walk": loop(1.2, walk), p + "dive": once(1.4, dive), p + "hurt": once(0.22, hurt_bones()),
            p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 最後の鐘守 (主)
def last_bellkeeper():
    p = "animation.last_bellkeeper."
    idle = {"body": bone([S(0, 1.5, 30), "0", "0"], [0, S(0, 0.5, 30), 0]), "head": bone([S(90, 3, 25), S(0, 6, 18), "0"]),
            "bell": bone([S(0, 2, 35), "0", S(90, 2, 28)]), "arm_l": bone([S(0, 3, 35), "0", "0"]), "staff": bone([S(90, 2, 30), "0", "0"])}
    walk = {"leg_l": bone([S(0, 16, 160), "0", "0"]), "leg_r": bone([S(180, 16, 160), "0", "0"]),
            "body": bone(["0", "0", S(0, 2.5, 160)], [0, S(0, 0.6, 320), 0]), "bell": bone([S(90, 4, 160), "0", S(0, 3, 160)]),
            "arm_l": bone([S(0, 10, 160), "0", "0"])}
    # 槌の振り下ろし (2.0): 1.3 まで頭上に振りかぶり (床の輪の予告)、1.45 で叩きつける
    slam = {"arm_r": bone(kf({0: (0, 0, 0), 1.3: (-160, 0, 10), 1.45: (-30, 0, 5), 1.8: (-30, 0, 5), 2.0: (0, 0, 0)})),
            "staff": bone(kf({0: (0, 0, 0), 1.3: (170, 0, 0), 1.45: (95, 0, 0), 1.8: (95, 0, 0), 2.0: (0, 0, 0)})),
            "body": bone(kf({0: (0, 0, 0), 1.3: (-10, 0, 0), 1.45: (14, 0, 0), 1.8: (14, 0, 0), 2.0: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 1.3: (-10, 0, 0), 1.45: (10, 0, 0), 2.0: (0, 0, 0)})),
            "arm_l": bone(kf({0: (0, 0, 0), 1.3: (-30, 0, -20), 1.45: (10, 0, -10), 2.0: (0, 0, 0)}))}
    # 鐘の波 (2.2): 1.4 まで身を沈めて背の鐘をためる (予告)、1.5 で伸び上がって鐘を鳴らす (輪の波)
    wave = {"body": bone(kf({0: (0, 0, 0), 1.4: (18, 0, 0), 1.5: (-12, 0, 0), 1.9: (-12, 0, 0), 2.2: (0, 0, 0)}),
                         kf({0: (0, 0, 0), 1.4: (0, -3, 0), 1.5: (0, 1, 0), 2.2: (0, 0, 0)}, False)),
            "bell": bone(kf({0: (0, 0, 0), 1.4: (-25, 0, 0), 1.5: (20, 0, 0), 1.7: (-12, 0, 0), 1.9: (6, 0, 0), 2.2: (0, 0, 0)})),
            "arm_l": bone(kf({0: (0, 0, 0), 1.4: (-10, 0, 20), 1.5: (-20, 0, -60), 1.9: (-20, 0, -60), 2.2: (0, 0, 0)})),
            "arm_r": bone(kf({0: (0, 0, 0), 1.4: (-10, 0, -20), 1.5: (-20, 0, 50), 1.9: (-20, 0, 50), 2.2: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 1.4: (20, 0, 0), 1.5: (-20, 0, 0), 2.2: (0, 0, 0)}))}
    # 無音 (2.4): 左手を面にかざし (1.2 まで)、そのまま保って鐘雲をほどく
    silence = {"arm_l": bone(kf({0: (0, 0, 0), 1.2: (-95, 20, 25), 2.0: (-95, 20, 25), 2.4: (0, 0, 0)})),
               "head": bone(kf({0: (0, 0, 0), 1.2: (12, 0, 0), 2.0: (12, 0, 0), 2.4: (0, 0, 0)})),
               "body": bone(kf({0: (0, 0, 0), 1.2: (6, 0, 0), 2.0: (6, 0, 0), 2.4: (0, 0, 0)})),
               "bell": bone(kf({0: (0, 0, 0), 1.2: (0, 0, 0), 2.0: (0, 0, 0)}))}
    # 呼ぶ (2.0): 槌を高く掲げる (二段目に亡者を呼ぶ)
    call = {"arm_r": bone(kf({0: (0, 0, 0), 0.6: (-170, 0, 5), 1.6: (-170, 0, 5), 2.0: (0, 0, 0)})),
            "staff": bone(kf({0: (0, 0, 0), 0.6: (180, 0, 0), 1.6: (180, 0, 0), 2.0: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.6: (-25, 0, 0), 1.6: (-25, 0, 0), 2.0: (0, 0, 0)})),
            "bell": bone(kf({0: (0, 0, 0), 0.8: (15, 0, 0), 1.0: (-15, 0, 0), 1.2: (10, 0, 0), 1.4: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 1.0: (12, 0, 0), 2.4: (20, 0, 0)}), kf({0: (0, 0, 0), 1.0: (0, -10, 0), 2.4: (0, -14, 0)}, False)),
             "head": bone(kf({0: (0, 0, 0), 1.0: (30, 0, 0)})),
             "arm_r": bone(kf({0: (0, 0, 0), 1.0: (-20, 0, 10)})), "staff": bone(kf({0: (0, 0, 0), 1.0: (40, 0, 0)})),
             "bell": bone(kf({0: (0, 0, 0), 1.0: (-15, 0, 0), 2.4: (-10, 0, 0)}))}
    return {p + "idle": loop(8.0, idle), p + "walk": loop(2.4, walk), p + "slam": once(2.0, slam), p + "wave": once(2.2, wave),
            p + "silence": once(2.4, silence), p + "call": once(2.0, call), p + "hurt": once(0.22, hurt_bones()),
            p + "death": once(2.4, death, True)}


# ---------------------------------------------------------------- 網打ち (歩く・網を振り回して投げる)
def net_caster():
    p = "animation.net_caster."
    idle = {"body": bone([S(0, 2, 40), "0", S(90, 2, 30)]), "head": bone([S(90, 4, 30), S(0, 8, 20), "0"]),
            "net": bone([S(0, 4, 60), "0", S(90, 3, 50)])}
    walk = {"leg_l": bone([S(0, 22, 220), "0", "0"]), "leg_r": bone([S(180, 22, 220), "0", "0"]),
            "body": bone(["0", "0", S(0, 3, 220)], [0, S(0, 0.4, 440), 0]), "net": bone([S(90, 8, 220), "0", "0"])}
    # 投げる (1.4): 0.8 まで網を頭上で振り回す (予告)、0.9 で前へ投げる
    cast = {"arm_l": bone(kf({0: (0, 0, 0), 0.3: (-150, 0, -15), 0.55: (-150, 30, -15), 0.8: (-150, -30, -15), 0.9: (-60, 0, -5), 1.4: (0, 0, 0)})),
            "arm_r": bone(kf({0: (0, 0, 0), 0.3: (-150, 0, 15), 0.55: (-150, 30, 15), 0.8: (-150, -30, 15), 0.9: (-60, 0, 5), 1.4: (0, 0, 0)})),
            "net": bone(kf({0: (0, 0, 0), 0.3: (-40, 0, 0), 0.55: (-40, 120, 0), 0.8: (-40, 240, 0), 0.9: (-80, 360, 0), 1.0: (-90, 360, 0), 1.4: (0, 360, 0)}),
                        kf({0: (0, 0, 0), 0.8: (0, 4, 0), 0.9: (0, 2, -6), 1.1: (0, 0, -4), 1.4: (0, 0, 0)}, False)),
            "body": bone(kf({0: (0, 0, 0), 0.8: (-8, 0, 0), 0.9: (14, 0, 0), 1.4: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.8: (-12, 0, 0), 0.9: (6, 0, 0), 1.4: (0, 0, 0)}))}
    strike = {"arm_l": bone(kf({0: (0, 0, 0), 0.35: (-70, 0, 0), 0.5: (10, 0, 0), 0.8: (0, 0, 0)})),
              "arm_r": bone(kf({0: (0, 0, 0), 0.35: (-70, 0, 0), 0.5: (10, 0, 0), 0.8: (0, 0, 0)})),
              "body": bone(kf({0: (0, 0, 0), 0.35: (-6, 0, 0), 0.5: (12, 0, 0), 0.8: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.6: (30, 0, 0), 1.2: (85, 0, -5)}), kf({0: (0, 0, 0), 1.2: (0, -8, 0)}, False)),
             "net": bone(kf({0: (0, 0, 0), 0.6: (30, 0, 0)}))}
    return {p + "idle": loop(6.0, idle), p + "walk": loop(1.6, walk), p + "cast": once(1.4, cast), p + "strike": once(0.8, strike),
            p + "hurt": once(0.22, hurt_bones()), p + "death": once(1.2, death, True)}


# ---------------------------------------------------------------- 灯蛾 (舞う・強く光って触れる)
def lamp_moth():
    p = "animation.lamp_moth."
    idle = {"wing_l": bone(["0", "0", S(0, 30, 420)]), "wing_r": bone(["0", "0", S(180, 30, 420)]),
            "body": bone([S(0, 6, 120), S(90, 8, 90), "0"], [0, S(0, 0.8, 120), 0]), "head": bone([S(90, 6, 80), "0", "0"])}
    walk = {"wing_l": bone(["0", "0", S(0, 38, 560)]), "wing_r": bone(["0", "0", S(180, 38, 560)]), "body": bone([S(0, 8, 280), "0", "0"])}
    # 光る (1.2): 翅を大きく開いて震わせる (予告)、1.0 で前へ突っ込む
    flap_l = {0: (0, 0, 0), 1.0: (0, -30, -20), 1.2: (0, 0, 0)}
    flap_r = {0: (0, 0, 0), 1.0: (0, 30, 20), 1.2: (0, 0, 0)}
    for i in range(8):
        t = 0.2 + i * 0.1
        flap_l[t] = (0, 0, 40 if i % 2 == 0 else 30)
        flap_r[t] = (0, 0, -40 if i % 2 == 0 else -30)
    flare = {"wing_l": bone(kf(dict(sorted(flap_l.items())))), "wing_r": bone(kf(dict(sorted(flap_r.items())))),
             "body": bone(kf({0: (0, 0, 0), 0.2: (15, 0, 0), 1.0: (-25, 0, 0), 1.2: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.6: (60, 0, 160), 1.0: (90, 0, 200)}), kf({0: (0, 0, 0), 1.0: (0, -10, 0)}, False)),
             "wing_l": bone(kf({0: (0, 0, 0), 0.5: (0, 0, 60)})), "wing_r": bone(kf({0: (0, 0, 0), 0.5: (0, 0, -60)}))}
    return {p + "idle": loop(1.0, idle), p + "walk": loop(1.0, walk), p + "flare": once(1.2, flare), p + "hurt": once(0.22, hurt_bones()),
            p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 灯守の目 (主)
def lamp_eye():
    p = "animation.lamp_eye."
    idle = {"cage": bone([S(0, 3, 40), "0", S(90, 3, 30)], [0, S(0, 1.0, 40), 0]), "eye": bone([S(90, 6, 30), S(0, 10, 22), "0"])}
    walk = {"cage": bone([S(0, 4, 80), "0", S(90, 4, 60)], [0, S(0, 1.0, 80), 0])}
    # 凝視 (1.5 保つ): 目が見開き (前へ寄る)、鎧戸が少し開く
    stare = {"eye": dict(bone(kf({0: (0, 0, 0), 0.4: (6, 0, 0)}), kf({0: (0, 0, 0), 0.4: (0, 0, -1.2)}, False)),
                         scale=M.scale({0: (1, 1, 1), 0.4: (1.08, 1.08, 1.08), 1.4: (1.12, 1.12, 1.12), 1.5: (1, 1, 1)})),
             "shutter_l": bone(kf({0: (0, 0, 0), 0.4: (0, 18, 0), 1.5: (0, 18, 0)})), "shutter_r": bone(kf({0: (0, 0, 0), 0.4: (0, -18, 0), 1.5: (0, -18, 0)})),
             "cage": bone(kf({0: (0, 0, 0), 0.4: (-6, 0, 0), 1.5: (-6, 0, 0)}))}
    # 目を閉じる (3.0): 鎧戸が前へ回って目を隠し、籠が少し沈む
    closed = {"shutter_l": bone(kf({0: (0, 0, 0), 0.3: (0, -100, 0), 2.7: (0, -100, 0), 3.0: (0, 0, 0)})),
              "shutter_r": bone(kf({0: (0, 0, 0), 0.3: (0, 100, 0), 2.7: (0, 100, 0), 3.0: (0, 0, 0)})),
              "cage": bone(kf({0: (0, 0, 0), 0.3: (8, 0, 0), 2.7: (8, 0, 0), 3.0: (0, 0, 0)}),
                           kf({0: (0, 0, 0), 0.3: (0, -3, 0), 2.7: (0, -3, 0), 3.0: (0, 0, 0)}, False))}
    # 灯の雨 (1.2): 目を上へ向けて光を撒く
    rain = {"eye": bone(kf({0: (0, 0, 0), 0.4: (-40, 0, 0), 1.0: (-40, 0, 0), 1.2: (0, 0, 0)})),
            "cage": bone(kf({0: (0, 0, 0), 0.4: (0, 0, 0), 0.6: (0, 0, 6), 0.8: (0, 0, -6), 1.0: (0, 0, 0)}))}
    # 呼ぶ (2.0): 籠が揺れ、目が四方を見る
    call = {"eye": bone(kf({0: (0, 0, 0), 0.4: (0, 60, 0), 0.8: (0, -60, 0), 1.2: (-20, 0, 0), 2.0: (0, 0, 0)})),
            "cage": bone(kf({0: (0, 0, 0), 0.4: (0, 0, 10), 0.8: (0, 0, -10), 1.2: (0, 0, 0)}))}
    death = {"cage": bone(kf({0: (0, 0, 0), 1.0: (20, 0, 15), 2.4: (60, 0, 40)}), kf({0: (0, 0, 0), 1.0: (0, -6, 0), 2.4: (0, -24, 0)}, False)),
             "shutter_l": bone(kf({0: (0, 0, 0), 0.6: (0, -100, 0)})), "shutter_r": bone(kf({0: (0, 0, 0), 0.6: (0, 100, 0)}))}
    return {p + "idle": loop(8.0, idle), p + "walk": loop(4.0, walk), p + "stare": once(1.5, stare, True), p + "closed": once(3.0, closed),
            p + "rain": once(1.2, rain), p + "call": once(2.0, call), p + "hurt": once(0.22, hurt_bones("cage")), p + "death": once(2.4, death, True)}


ANIMS = {"bell_mourner": bell_mourner, "mist_bird": mist_bird, "last_bellkeeper": last_bellkeeper,
         "net_caster": net_caster, "lamp_moth": lamp_moth, "lamp_eye": lamp_eye}


# ---------------------------------------------------------------- 素材を入れる
def stage_atlases(mob, atlas, glow, tex):
    """二段目・三段目の絵: 体 (光らない画素) に朝の光の割れ目を足していく。割れ目の画素は光る"""
    from PIL import Image
    base = Image.open(atlas).convert("RGBA")
    gm = Image.open(glow).convert("L") if os.path.exists(glow) else Image.new("L", base.size, 0)
    W, H = base.size
    px = base.load()
    rng = random.Random(sum(map(ord, mob)))
    for tag, n in (("s1", 1), ("s2", 3)):
        im = base.copy()
        g = gm.copy()
        ip, gp = im.load(), g.load()
        for _ in range(int(W * H / 900) * n):
            x, y = rng.randrange(W), rng.randrange(H)
            if px[x, y][3] < 200 or gm.getpixel((x, y)) > 0:
                continue
            for _ in range(rng.randint(3, 7)):
                if 0 <= x < W and 0 <= y < H and px[x, y][3] >= 200:
                    ip[x, y] = (255, 228, 170, 255)
                    gp[x, y] = 255
                x += rng.choice((-1, 0, 1))
                y += rng.choice((-1, 0, 1))
        im.save(os.path.join(tex, "%s_%s.png" % (mob, tag)))
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
        subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "install", "install_lowpoly.py"), mob, "--mesh", mesh, "--atlas", atlas,
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


# ---------------------------------------------------------------- 音 (既存の音の別名。namespace skyabyss_finale)
SOUNDS = {
    # 島 1「鐘の島」
    "mourner_idle": [ev("entity.allay.ambient_without_item", 0.4, 0.4)],
    "mourner_tell": [ev("block.bell.resonate", 1.4, 0.7)],
    "mourner_ring": [ev("block.bell.use", 0.5, 1.2)],
    "mourner_strike": [ev("block.anvil.land", 1.4, 0.6)],
    "bird_cry": [ev("entity.parrot.ambient", 0.6, 0.6)],
    "bird_tell": [ev("entity.phantom.flap", 1.2, 0.9)],
    "bird_dive": [ev("entity.phantom.swoop", 1.2, 1.0)],
    "keeper_slam_tell": [ev("block.bell.resonate", 0.7, 1.2)],
    "keeper_slam": [ev("entity.warden.attack_impact", 0.7, 1.3)],
    "keeper_wave_tell": [ev("block.respawn_anchor.charge", 0.7, 1.0)],
    "keeper_wave": [ev("block.bell.use", 0.4, 1.6)],
    "keeper_silence": [ev("block.sculk_shrieker.shriek", 1.6, 0.6)],
    "keeper_call": [ev("block.bell.resonate", 0.5, 1.4)],
    "keeper_roar": [ev("block.bell.use", 0.3, 1.8)],
    # 島 2「灯の島」
    "beam_caught": [ev("block.beacon.power_select", 0.7, 0.8)],
    "lamp_lit": [ev("block.respawn_anchor.charge", 1.6, 0.9)],
    "lamp_out": [ev("block.respawn_anchor.deplete", 1.4, 0.6)],
    "caster_tell": [ev("entity.fishing_bobber.throw", 0.6, 1.0)],
    "caster_cast": [ev("entity.fishing_bobber.splash", 0.7, 1.2)],
    "caster_idle": [ev("entity.drowned.ambient", 0.6, 0.5)],
    "moth_flare": [ev("block.amethyst_block.chime", 1.8, 1.0)],
    "moth_mark": [ev("block.beacon.activate", 1.8, 0.8)],
    "eye_stare": [ev("entity.elder_guardian.curse", 1.4, 0.9)],
    "eye_beam": [ev("entity.guardian.attack", 0.8, 1.4)],
    "eye_close": [ev("block.iron_door.close", 0.6, 1.2)],
    "eye_rain_tell": [ev("block.beacon.power_select", 1.6, 1.0)],
    "eye_rain": [ev("entity.firework_rocket.blast", 1.2, 0.9)],
    "eye_call": [ev("block.respawn_anchor.set_spawn", 1.2, 1.2)],
    "eye_roar": [ev("entity.elder_guardian.ambient", 0.6, 1.6)],
}


def sounds():
    wjson(os.path.join(AF, "sounds.json"), {k: {"subtitle": "subtitles.skyabyss_finale." + k, "sounds": v} for k, v in SOUNDS.items()})


def main():
    only = set(sys.argv[1:])
    install(only)
    sounds()
    print("done")


if __name__ == "__main__":
    main()
