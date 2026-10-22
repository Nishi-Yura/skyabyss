#!/usr/bin/env python3
"""二章の地上の敵 4 種 (潮だまり貝・砂潜り・宿借り・潮の番人) を、選んだラフから入れる (docs/design/21 の 4.1、P12)。

  python -I tools/gen/ch2/gen_ch2_ground_mobs.py

入力: docs/review/ch2_roughs/<id>/<案>/<id>_<案>_{mesh.json,atlas.png,glowmask.png}  (ラフの作り方は tools/rough/lowpoly/ch2/)
      潮の番人は銛の別メッシュ <id>_<案>_harpoon_{mesh.json,atlas.png} も (受け口 socket_main、名前 shore_harpoon)
出力 (すべて src/main/resources/assets/skyabyss/ の下):
  lowpoly/<id>.json, textures/lowpoly/<id>(.png|_glowmask.png), geo/entity/<id>_lowpoly(.geo|_debug.geo).json   (tools/install/install_lowpoly.py)
  geo/entity/<id>.geo.json + textures/entity/<id>.png   LowPoly.enabled = false のときの箱の代用 (デバッグ箱)
  animations/entity/<id>.animation.json                 このファイルで作る
  sounds.json の行 (既存の音の出来事 (type event) に高さを付けた別名)、字幕と名前は assets/skyabyss_ch2mob/lang/
  models/item/<id>_spawn_egg.json

案は CHOICE で選ぶ (仮の選定: 貝 A・砂潜り C・宿借り B・番人 A。ユーザーの選定で変われば、ここを変えて流し直す)。
回転の約束: 貝・砂潜り・宿借りはラフの姿勢表 = 数学の右手系 (前 -Z。x が + = 前が上がる) で書き、ファイルへは (-x, -y, z)。
番人は珊瑚憑きと同じ骨 (root が Y 180 度) で、値は GeckoLib の値そのまま (珊瑚憑きの animation と同じ書き方)。
"""
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
R = os.path.join(ROOT, "docs", "review", "ch2_roughs")
CHOICE = {"tidepool_clam": "a", "sand_burrower": "c", "drift_hermit": "b", "shore_warden": "a"}


def wjson(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")


# ---------------------------------------------------------------- 動きの部品 (tools/gen/ch1/gen_ch1_ground_mobs.py と同じ)
def neg(expr):
    return "-(%s)" % expr


def rot(x=0, y=0, z=0):
    return [-x if x else 0, -y if y else 0, z]


def mrot(x="0", y="0", z="0"):
    return [neg(x) if x != "0" else "0", neg(y) if y != "0" else "0", z]


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


def gkf(frames):
    """GeckoLib の値そのまま (番人)"""
    return {"%.2f" % t: list(v) for t, v in frames.items()}


def bone(rotation=None, position=None):
    d = {}
    if rotation:
        d["rotation"] = rotation
    if position:
        d["position"] = position
    return d


# ---------------------------------------------------------------- 潮だまり貝
OPEN = 58


def clam():
    p = "animation.tidepool_clam."
    closed = {"lid": bone(mrot(S(0, 1.2, 90) + " + 1.2"))}
    # 開く (0.8 秒 = 予告): はじめは少しずつ、最後に大きく開く。開いたまま止まる
    opening = {"lid": bone(kf({0: (0, 0, 0), 0.3: (10, 0, 0), 0.6: (34, 0, 0), 0.8: (OPEN, 0, 0)})),
               "body": bone(kf({0: (0, 0, 0), 0.2: (0, 0, 2), 0.4: (0, 0, -2), 0.6: (0, 0, 2), 0.8: (0, 0, 0)}))}
    open_hold = {"lid": bone(mrot(S(0, 2, 120) + " + %d" % OPEN))}
    # 挟む: 一気に閉じ (0.12)、閉じたまま (〜0.5)、また開く (0.9)
    pinch = {"lid": bone(kf({0: (OPEN, 0, 0), 0.12: (-2, 0, 0), 0.2: (0, 0, 0), 0.5: (0, 0, 0), 0.9: (OPEN, 0, 0)})),
             "body": bone(position=kf({0: (0, 0, 0), 0.12: (0, 1.2, 0), 0.3: (0, 0, 0)}, False))}
    close = {"lid": bone(kf({0: (OPEN, 0, 0), 0.4: (0, 0, 0)}))}
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (0, 0, 6), 0.18: (0, 0, -4), 0.3: (0, 0, 0)}))}
    death = {"lid": bone(kf({0: (0, 0, 0), 0.5: (80, 0, 0), 1.0: (74, 0, 0)})),
             "body": bone(kf({0: (0, 0, 0), 0.6: (0, 0, 12), 1.0: (0, 0, 10)}), kf({0: (0, 0, 0), 1.0: (0, -0.8, 0)}, False))}
    return {p + "idle": loop(4.0, closed), p + "walk": loop(4.0, closed), p + "open": once(0.8, opening, True),
            p + "open_hold": loop(3.0, open_hold), p + "pinch": once(0.9, pinch, True), p + "close": once(0.4, close, True),
            p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 砂潜り (星見魚)
def burrower():
    p = "animation.sand_burrower."
    idle = {"fin_l": bone(["0", "0", S(0, 8, 180)]), "fin_r": bone(["0", "0", neg(S(0, 8, 180))]),
            "jaw": bone(mrot(S(90, 3, 90) + " + 3")), "tail": bone(["0", S(0, 6, 120), "0"])}
    walk = {"body": bone(["0", S(0, 8, 540), "0"], ["0", "math.abs(" + S(0, 0.4, 540) + ")", "0"]),
            "tail": bone(["0", S(180, 22, 540), "0"]),
            "fin_l": bone(["0", S(0, 20, 540), S(90, 12, 540)]), "fin_r": bone(["0", S(0, 20, 540), neg(S(90, 12, 540))])}
    # 潜っている間 (描かないが、出る瞬間の始まりの姿): 体ごと砂の下
    buried = {"body": bone(position=["0", "-9", "0"])}
    # 飛び出す (予告のあと): 砂の下から斜め上へ跳ね、口を開けて落ちる
    leap = {"body": bone(kf({0: (35, 0, 0), 0.2: (-18, 0, 0), 0.45: (-6, 0, 0), 0.6: (0, 0, 0)}),
                         kf({0: (0, -9, 0), 0.15: (0, 5, -2), 0.35: (0, 2.5, -1), 0.6: (0, 0, 0)}, False)),
            "jaw": bone(kf({0: (0, 0, 0), 0.1: (40, 0, 0), 0.3: (-4, 0, 0), 0.6: (0, 0, 0)})),
            "tail": bone(kf({0: (0, 0, 0), 0.2: (30, 0, 0), 0.6: (0, 0, 0)})),
            "fin_l": bone(kf({0: (0, 0, 0), 0.2: (0, 0, -40), 0.6: (0, 0, 0)})), "fin_r": bone(kf({0: (0, 0, 0), 0.2: (0, 0, 40), 0.6: (0, 0, 0)}))}
    # 飛びかかり (予告 0.8 秒 = 床の帯): 身を沈めて尾を上げ、口を少しずつ開く。0.8 で前へ跳ねて噛む
    bite = {"body": bone(kf({0: (0, 0, 0), 0.7: (-8, 0, 0), 0.8: (-8, 0, 0), 0.9: (20, 0, 0), 1.05: (0, 0, 0), 1.3: (0, 0, 0)}),
                         kf({0: (0, 0, 0), 0.7: (0, -1.0, 1.0), 0.8: (0, -1.0, 1.0), 0.92: (0, 3.5, -5), 1.1: (0, 0, -2), 1.3: (0, 0, 0)}, False)),
            "jaw": bone(kf({0: (0, 0, 0), 0.7: (26, 0, 0), 0.8: (34, 0, 0), 0.92: (-4, 0, 0), 1.3: (0, 0, 0)})),
            "tail": bone(kf({0: (0, 0, 0), 0.7: (24, 0, 0), 0.9: (-20, 0, 0), 1.3: (0, 0, 0)}))}
    burrow = {"body": bone(kf({0: (0, 0, 0), 0.3: (-25, 0, 0), 0.8: (-30, 0, 0)}), kf({0: (0, 0, 0), 0.3: (0, -2, 0), 0.8: (0, -9, 0)}, False)),
              "tail": bone(["0", S(0, 30, 900), "0"])}
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (8, 0, 6), 0.3: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.5: (0, 0, 160), 1.0: (0, 0, 180)}), kf({0: (0, 0, 0), 0.3: (0, 3, 0), 1.0: (0, 4.0, 0)}, False)),
             "jaw": bone(kf({0: (0, 0, 0), 0.5: (30, 0, 0)}))}
    return {p + "idle": loop(3.0, idle), p + "walk": loop(1.0, walk), p + "buried": loop(1.0, buried), p + "leap": once(0.6, leap),
            p + "bite": once(1.3, bite), p + "burrow": once(0.8, burrow, True), p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 宿借り
LEGS = ["leg_l1", "leg_l2", "leg_r1", "leg_r2"]


def hermit():
    p = "animation.drift_hermit."
    idle = {"claw_l": bone(mrot(S(0, 4, 120))), "claw_r": bone(mrot(S(90, 6, 120))), "head": bone(["0", S(0, 6, 90), "0"])}
    walk = {"body": bone(position=["0", "math.abs(" + S(0, 0.3, 720) + ")", "0"]), "shell": bone(["0", "0", S(0, 3, 360)])}
    for i, b in enumerate(LEGS):
        side = -1 if "_l" in b else 1
        ph = 0 if i in (0, 3) else 180
        walk[b] = bone(["0", S(ph, 20, 720), "%d * math.abs(%s)" % (side * 1, S(ph, 14, 720))])
    walk["claw_l"] = bone(mrot(S(0, 6, 720)))
    walk["claw_r"] = bone(mrot(S(180, 6, 720)))
    # 殻に潜る: 頭・鋏・脚を殻の中へ引き、殻ごと地面に下ろす
    hidden_pose = {"body": bone(position=["0", "-1.6", "0"]), "head": bone(mrot("10"), ["0", "-0.6", "3.2"]),
                   "claw_l": bone(mrot("-20"), ["0", "-0.4", "3.0"]), "claw_r": bone(mrot("-20"), ["0", "-0.4", "3.0"])}
    for b in LEGS:
        side = -1 if "_l" in b else 1
        hidden_pose[b] = bone(["0", "0", str(side * -60)], ["0", "0.6", "1.6"])
    hide = {"body": bone(position=kf({0: (0, 0, 0), 0.2: (0, -1.6, 0)}, False)),
            "head": bone(kf({0: (0, 0, 0), 0.2: (10, 0, 0)}), kf({0: (0, 0, 0), 0.2: (0, -0.6, 3.2)}, False)),
            "claw_l": bone(kf({0: (0, 0, 0), 0.2: (-20, 0, 0)}), kf({0: (0, 0, 0), 0.2: (0, -0.4, 3.0)}, False)),
            "claw_r": bone(kf({0: (0, 0, 0), 0.2: (-20, 0, 0)}), kf({0: (0, 0, 0), 0.2: (0, -0.4, 3.0)}, False))}
    for b in LEGS:
        side = -1 if "_l" in b else 1
        hide[b] = bone({"0.00": [0, 0, 0], "0.20": [0, 0, side * -60]}, kf({0: (0, 0, 0), 0.2: (0, 0.6, 1.6)}, False))
    # 吐く: 殻から体を起こし、鋏を上げて口から拾い物を出す
    spit = {"body": bone(kf({0: (0, 0, 0), 0.25: (16, 0, 0), 0.6: (12, 0, 0), 0.9: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.25: (20, 0, 0), 0.35: (-6, 0, 0), 0.9: (0, 0, 0)})),
            "claw_l": bone(kf({0: (0, 0, 0), 0.25: (40, 0, 0), 0.9: (0, 0, 0)})), "claw_r": bone(kf({0: (0, 0, 0), 0.25: (40, 0, 0), 0.9: (0, 0, 0)}))}
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (-8, 0, 4), 0.3: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.5: (0, 0, 150), 1.0: (0, 0, 170)}), kf({0: (0, 0, 0), 0.5: (0, 5, 0), 1.0: (0, 6.5, 0)}, False))}
    for b in LEGS:
        death[b] = bone(kf({0: (0, 0, 0), 0.6: (0, 0, 30 if "_l" in b else -30)}))
    return {p + "idle": loop(3.0, idle), p + "walk": loop(0.5, walk), p + "hide": once(0.2, hide, True), p + "hidden": loop(1.0, hidden_pose),
            p + "spit": once(0.9, spit), p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 潮の番人 (珊瑚憑きの骨。値は GeckoLib のまま)
sys.path.insert(0, os.path.join(ROOT, "tools", "rough", "lowpoly", "ch2"))


def warden():
    p = "animation.shore_warden."
    src = json.load(open(os.path.join(A, "animations", "entity", "coral_thrall.animation.json"), encoding="utf-8"))["animations"]
    out = {}
    for k in ("idle", "walk", "hurt", "death"):
        out[p + k] = src["animation.coral_thrall." + k]
    # ラフの姿勢 (tools/rough/lowpoly/ch2/shore_warden.py) と同じ値
    STANCE = {"arm_r": (-34.0, 0.0, 0.0), "forearm_r": (40.0, 0.0, 0.0), "body": (-10.0, -18.0, 0.0), "head": (8.0, 14.0, 0.0), "arm_l": (24.0, 0.0, 12.0),
              "leg_r": (14.0, 0.0, 0.0), "leg_l": (-12.0, 0.0, 0.0)}
    THRUST = {"arm_r": (52.0, 0.0, 0.0), "forearm_r": (-62.0, 0.0, 0.0), "body": (18.0, 12.0, 0.0), "head": (-10.0, -8.0, 0.0), "arm_l": (-24.0, 0.0, 10.0),
              "leg_r": (-18.0, 0.0, 0.0), "leg_l": (16.0, 0.0, 0.0)}
    CAST = {"arm_r": (-160.0, 0.0, -8.0), "forearm_r": (-40.0, 0.0, 0.0), "body": (-8.0, 0.0, 0.0), "head": (14.0, 0.0, 0.0), "arm_l": (-150.0, 0.0, 14.0),
            "leg_r": (0.0, 0.0, 0.0), "leg_l": (0.0, 0.0, 0.0)}
    SLAM = {"arm_r": (-70.0, 0.0, 0.0), "forearm_r": (-70.0, 0.0, 0.0), "body": (20.0, 0.0, 0.0), "head": (-6.0, 0.0, 0.0), "arm_l": (-50.0, 0.0, 10.0),
            "leg_r": (-20.0, 0.0, 0.0), "leg_l": (10.0, 0.0, 0.0)}
    Z = (0.0, 0.0, 0.0)
    # 突き: 構え 0〜0.3 で入り 1.1 まで保つ (予告 1.1 秒 = 床の帯)。1.1〜1.2 で突き、1.45 まで伸ばし、1.9 で戻る
    thrust = {}
    for b in STANCE:
        thrust[b] = {"rotation": gkf({0: Z, 0.3: STANCE[b], 1.0: STANCE[b], 1.1: tuple(v * 1.1 for v in STANCE[b]), 1.2: THRUST[b], 1.45: THRUST[b], 1.9: Z})}
    thrust["body"]["position"] = gkf({0: Z, 0.3: (0, 0, -1.2), 1.1: (0, 0, -1.4), 1.2: (0, -0.6, 3.0), 1.45: (0, -0.6, 3.0), 1.9: Z})
    # 満ち潮の輪: 銛を頭上へ (0〜0.5)、1.2 まで掲げて保つ (予告 1.2 秒 = 床の青い輪)。1.2〜1.3 で石突を床へ、1.7 まで、2.1 で戻る
    ring = {}
    for b in CAST:
        ring[b] = {"rotation": gkf({0: Z, 0.5: CAST[b], 1.15: CAST[b], 1.3: SLAM[b], 1.7: SLAM[b], 2.1: Z})}
    ring["body"]["position"] = gkf({0: Z, 0.5: (0, 0.6, 0), 1.15: (0, 0.6, 0), 1.3: (0, -1.6, 0), 1.7: (0, -1.6, 0), 2.1: Z})
    out[p + "thrust"] = {"animation_length": 1.9, "bones": thrust}
    out[p + "ring"] = {"animation_length": 2.1, "bones": ring}
    return out


# ---------------------------------------------------------------- 音 (既存の音の出来事の別名。type event で高さと大きさを掛ける)
def ev(name, pitch, vol):
    return {"name": name if ":" in name else "skyabyss:" + name, "type": "event", "pitch": pitch, "volume": vol}


SOUNDS = {
    "tidepool_clam_ambient": [ev("bubble_pop", 0.7, 0.35)],
    "tidepool_clam_open": [ev("reef_nest_open", 1.2, 1.0)],
    "tidepool_clam_snap": [ev("reef_nest_close", 1.5, 1.0)],
    "tidepool_clam_close": [ev("reef_nest_close", 1.1, 0.6)],
    "tidepool_clam_hurt": [ev("reef_scuttler_hurt", 0.8, 0.8)],
    "tidepool_clam_death": [ev("reef_nest_break", 1.3, 0.9)],
    "sand_burrower_ambient": [ev("tide_beckoner_breath", 1.8, 0.3)],
    "sand_burrower_bulge": [ev("tide_beckoner_burrow", 1.6, 0.9)],
    "sand_burrower_emerge": [ev("tide_beckoner_emerge", 1.7, 1.0)],
    "sand_burrower_bite": [ev("minecraft:entity.fox.bite", 0.7, 1.0)],
    "sand_burrower_hurt": [ev("reef_scuttler_hurt", 1.3, 0.8)],
    "sand_burrower_death": [ev("reef_scuttler_death", 1.2, 0.8)],
    "sand_burrower_step": [ev("minecraft:block.sand.step", 1.4, 0.3)],
    "drift_hermit_ambient": [ev("reef_scuttler_ambient", 1.5, 0.45)],
    "drift_hermit_hurt": [ev("reef_scuttler_hurt", 1.6, 0.7)],
    "drift_hermit_death": [ev("reef_scuttler_death", 1.6, 0.7)],
    "drift_hermit_step": [ev("reef_scuttler_step", 1.4, 0.25)],
    "drift_hermit_hide": [ev("reef_nest_close", 1.8, 0.6)],
    "drift_hermit_spit": [ev("bubble_pop", 0.9, 0.8)],
    "shore_warden_ambient": [ev("coral_thrall_ambient", 0.75, 1.0)],
    "shore_warden_hurt": [ev("coral_thrall_hurt", 0.8, 1.0)],
    "shore_warden_death": [ev("coral_thrall_death", 0.7, 1.0)],
    "shore_warden_step": [ev("coral_thrall_step", 0.75, 0.6)],
    "shore_warden_stance": [ev("tide_beckoner_raise", 1.1, 1.2)],
    "shore_warden_thrust": [ev("coral_thrall_thrust", 0.8, 1.4)],
    "shore_warden_ring": [ev("tide_heart_flood", 1.3, 1.0)],
    "shore_warden_flood": [ev("tide_heart_wave", 1.4, 1.2)],
}
SUBTITLES = {
    "tidepool_clam_ambient": ("Clam bubbles", "貝が泡を吐く"), "tidepool_clam_open": ("Clam opens", "貝が殻を開く"),
    "tidepool_clam_snap": ("Clam snaps shut", "貝が挟む"), "tidepool_clam_close": ("Clam closes", "貝が閉じる"),
    "tidepool_clam_hurt": ("Shell cracks", "殻が鳴る"), "tidepool_clam_death": ("Clam breaks", "貝が割れる"),
    "sand_burrower_ambient": ("Sand stirs", "砂がうごめく"), "sand_burrower_bulge": ("Sand bulges", "砂が盛り上がる"),
    "sand_burrower_emerge": ("Burrower bursts out", "砂潜りが飛び出す"), "sand_burrower_bite": ("Burrower bites", "砂潜りが噛む"),
    "sand_burrower_hurt": ("Burrower hisses", "砂潜りがうめく"), "sand_burrower_death": ("Burrower dies", "砂潜りが倒れる"),
    "sand_burrower_step": ("Sand shifts", "砂が擦れる"),
    "drift_hermit_ambient": ("Hermit clicks", "宿借りが鳴る"), "drift_hermit_hurt": ("Hermit squeaks", "宿借りが鳴く"),
    "drift_hermit_death": ("Hermit dies", "宿借りが倒れる"), "drift_hermit_step": ("Tiny steps", "小さな足音"),
    "drift_hermit_hide": ("Hermit hides", "宿借りが殻に潜る"), "drift_hermit_spit": ("Hermit spits something out", "宿借りが何かを吐く"),
    "shore_warden_ambient": ("Coral creaks", "珊瑚がきしむ"), "shore_warden_hurt": ("Shore warden groans", "潮の番人がうめく"),
    "shore_warden_death": ("Shore warden falls", "潮の番人が倒れる"), "shore_warden_step": ("Wet heavy steps", "濡れた重い足音"),
    "shore_warden_stance": ("Harpoon drawn back", "銛を引く"), "shore_warden_thrust": ("Harpoon thrusts", "銛が突き出る"),
    "shore_warden_ring": ("Tide ring rises", "満ち潮の輪が光る"), "shore_warden_flood": ("Water rushes in", "水が押し寄せる"),
}
LANG = {
    "entity.skyabyss.tidepool_clam": ("Tidepool Clam", "潮だまり貝"),
    "entity.skyabyss.sand_burrower": ("Sand Burrower", "砂潜り"),
    "entity.skyabyss.drift_hermit": ("Drift Hermit", "宿借り"),
    "entity.skyabyss.shore_warden": ("Shore Warden", "潮の番人"),
    "item.skyabyss.tidepool_clam_spawn_egg": ("Tidepool Clam Spawn Egg", "潮だまり貝のスポーンエッグ"),
    "item.skyabyss.sand_burrower_spawn_egg": ("Sand Burrower Spawn Egg", "砂潜りのスポーンエッグ"),
    "item.skyabyss.drift_hermit_spawn_egg": ("Drift Hermit Spawn Egg", "宿借りのスポーンエッグ"),
    "item.skyabyss.shore_warden_spawn_egg": ("Shore Warden Spawn Egg", "潮の番人のスポーンエッグ"),
}


NL = chr(10)


def sounds():
    p = os.path.join(A, "sounds.json")
    # ほかの作業とぶつからないよう、ファイルの他の行は触らず、末尾に一つの塊として足す (流し直しは塊を置き換える)
    text = open(p, encoding="utf-8").read()
    data = json.loads(text)
    for key in SOUNDS:
        if key in data:
            start = text.index('"%s"' % key)
            text = text[:text.rfind(",", 0, start)] + NL + "}" + NL   # 前の流しの塊から後ろを捨てる
            break
    body = text.rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip()
    block = ("," + NL).join("  %s: %s" % (json.dumps(k), json.dumps({"subtitle": "subtitles.skyabyss." + k, "sounds": v}, ensure_ascii=False))
                            for k, v in SOUNDS.items())
    out = body + "," + NL + block + NL + "}" + NL
    json.loads(out)
    with open(p, "w", encoding="utf-8", newline="") as f:
        f.write(out)


def lang():
    for i, lang_ in enumerate(("en_us", "ja_jp")):
        d = {}
        for key, texts in LANG.items():
            d[key] = texts[i]
        for key, texts in SUBTITLES.items():
            d["subtitles.skyabyss." + key] = texts[i]
        wjson(os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_ch2mob", "lang", lang_ + ".json"), d)


def install():
    for mob, k in CHOICE.items():
        # 相対パス (--gear は ":" で区切るので、ドライブ名の付いた絶対パスは渡せない)
        d = "docs/review/ch2_roughs/%s/%s/%s_%s_" % (mob, k, mob, k)
        cmd = [sys.executable, os.path.join(ROOT, "tools", "install/install_lowpoly.py"), mob, "--mesh", d + "mesh.json", "--atlas", d + "atlas.png",
               "--glow", d + "glowmask.png"]
        if mob == "shore_warden":
            cmd += ["--gear", d + "harpoon_mesh.json:" + d + "harpoon_atlas.png:shore_harpoon:socket_main"]
        subprocess.check_call(cmd, cwd=ROOT)
        shutil.copyfile(os.path.join(A, "geo", "entity", mob + "_lowpoly_debug.geo.json"), os.path.join(A, "geo", "entity", mob + ".geo.json"))
        os.makedirs(os.path.join(A, "textures", "entity"), exist_ok=True)
        shutil.copyfile(os.path.join(A, "textures", "lowpoly", mob + ".png"), os.path.join(A, "textures", "entity", mob + ".png"))
        gm = os.path.join(A, "textures", "lowpoly", mob + "_glowmask.png")
        if os.path.exists(gm):
            shutil.copyfile(gm, os.path.join(A, "textures", "entity", mob + "_glowmask.png"))
        wjson(os.path.join(A, "models", "item", mob + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def main():
    install()
    for mob, fn in (("tidepool_clam", clam), ("sand_burrower", burrower), ("drift_hermit", hermit), ("shore_warden", warden)):
        wjson(os.path.join(A, "animations", "entity", mob + ".animation.json"), {"format_version": "1.8.0", "animations": fn()})
    sounds()
    lang()
    print("done")


if __name__ == "__main__":
    main()
