#!/usr/bin/env python3
"""一章の地上の敵 4 種 (岩殻虫 A・棘うさぎ A・骨負い A・老いた歩哨 A) を、選ばれたラフから入れる (docs/design/21 の 3.1、P7)。

  python tools/gen_ch1_ground_mobs.py

入力: docs/review/ch1_roughs/<id>/a/<id>_a_{mesh.json,atlas.png,glowmask.png}  (選ばれた A 案。ラフの作り方は tools/rough/lowpoly/ch1/)
出力 (すべて src/main/resources/assets/skyabyss/ の下):
  lowpoly/<id>.json, textures/lowpoly/<id>(.png|_glowmask.png), geo/entity/<id>_lowpoly(.geo|_debug.geo).json   (tools/install_lowpoly.py)
  geo/entity/<id>.geo.json + textures/entity/<id>.png   LowPoly.enabled = false のときの箱の代用 (デバッグ箱)
  animations/entity/<id>.animation.json                 このファイルで作る (回転は「ラフの回転 = 数学の向き」で書き、GeckoLib の符号 (x, y を反転) に直して出す)
  sounds.json の行 (既存の音源に高さを付けた別名) と字幕、lang、歩哨の割符の絵

回転の約束 (ラフの姿勢表 = 数学の右手系。前は -Z): x が + = 頭・前が上がる / 垂れた腕と脚の先が前へ。ファイルへは (-x, -y, z)。
骨と pivot の確認 (ラフの bbox との突き合わせ): 脚の pivot は付け根、耳・棘は根もと、盾は左前腕の子。直す所は無かった (体の中心 pivot は
立ち上がり・倒れで位置の補正を足して合わせる)。
"""
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
R = os.path.join(ROOT, "docs", "review", "ch1_roughs")
MOBS = ["rock_shell_beetle", "spine_hare", "bone_hauler", "elder_sentry"]


def wjson(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


# ---------------------------------------------------------------- 動き
def neg(expr):
    return "-(%s)" % expr


def rot(x=0, y=0, z=0):
    """数学の向き (x, y, z 度) -> ファイルの値"""
    return [-x if x else 0, -y if y else 0, z]


def mrot(x="0", y="0", z="0"):
    """molang の式 (数学の向き) -> ファイルの式"""
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
    """{時刻: 値} -> キーフレーム。rotation は数学の向きで渡す"""
    out = {}
    for t, v in frames.items():
        out["%.2f" % t] = rot(*v) if rotation else list(v)
    return out


def bone(rotation=None, position=None):
    d = {}
    if rotation:
        d["rotation"] = rotation
    if position:
        d["position"] = position
    return d


def beetle():
    legs_a, legs_b = ["leg_fl", "leg_mr", "leg_bl"], ["leg_fr", "leg_ml", "leg_br"]
    idle = {"body": bone(position=["0", S(0, 0.25, 120), "0"]),
            "head": bone(mrot(S(90, 3, 120))),
            "shield": bone(mrot(S(0, 0.8, 120)))}
    walk = {"body": bone(["0", "0", S(0, 1.5)], ["0", "math.abs(" + S(0, 0.5) + ")", "0"]),
            "head": bone(mrot(S(180, 3)))}
    for b in legs_a:
        walk[b] = bone(mrot(S(0, 24)))
    for b in legs_b:
        walk[b] = bone(mrot(S(180, 24)))
    # 立ち上がり (0.8 秒 = 予告) -> 体当たり (0.8〜1.05) -> 戻る。体を後ろへ傾けるので、後ろ脚が地面へ沈む分を position で持ち上げる
    charge = {
        "body": bone(kf({0: (0, 0, 0), 0.3: (22, 0, 0), 0.8: (36, 0, 0), 0.95: (-9, 0, 0), 1.2: (-3, 0, 0), 1.6: (0, 0, 0)}),
                     kf({0: (0, 0, 0), 0.3: (0, 1.2, 0), 0.8: (0, 2.6, 0), 0.95: (0, 0.5, 0), 1.6: (0, 0, 0)}, False)),
        "head": bone(kf({0: (0, 0, 0), 0.8: (-14, 0, 0), 0.95: (10, 0, 0), 1.6: (0, 0, 0)})),
        "shield": bone(kf({0: (0, 0, 0), 0.8: (10, 0, 0), 0.95: (-4, 0, 0), 1.6: (0, 0, 0)})),
        "leg_fl": bone(kf({0: (0, 0, 0), 0.8: (34, 0, 0), 0.95: (-26, 0, 0), 1.6: (0, 0, 0)})),
        "leg_fr": bone(kf({0: (0, 0, 0), 0.8: (34, 0, 0), 0.95: (-26, 0, 0), 1.6: (0, 0, 0)})),
        "leg_bl": bone(kf({0: (0, 0, 0), 0.8: (-14, 0, 0), 0.95: (-30, 0, 0), 1.6: (0, 0, 0)})),
        "leg_br": bone(kf({0: (0, 0, 0), 0.8: (-14, 0, 0), 0.95: (-30, 0, 0), 1.6: (0, 0, 0)})),
    }
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (-7, 0, 0), 0.3: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.08: (12, 0, 0), 0.3: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.6: (0, 0, 180)}),
                          kf({0: (0, 0, 0), 0.3: (0, 12, 0), 0.7: (0, 8.6, 0), 1.0: (0, 8.6, 0)}, False))}
    # 腹を上にひっくり返り、脚が縮む
    for b in legs_a + legs_b:
        death[b] = bone(kf({0: (0, 0, 0), 0.5: (-50, 0, 0), 1.0: (-70, 0, 0)}))
    death["head"] = bone(kf({0: (0, 0, 0), 0.6: (20, 0, 0)}))
    return {"animation.rock_shell_beetle.idle": loop(3.0, idle), "animation.rock_shell_beetle.walk": loop(1.0, walk),
            "animation.rock_shell_beetle.charge": once(1.6, charge), "animation.rock_shell_beetle.hurt": once(0.3, hurt),
            "animation.rock_shell_beetle.death": once(1.0, death, True)}


SPINES = ["spine%d" % i for i in range(5)]


def hare():
    laid = 60  # 寝た棘 (背の後ろへ倒れる角度)
    laid_rot = mrot(str(laid))
    idle = {"body": bone(position=["0", S(0, 0.2, 120), "0"]),
            "head": bone(mrot(S(90, 3, 120))),
            "ear_l": bone(mrot(S(0, 4, 120))), "ear_r": bone(mrot(S(120, 4, 120)))}
    walk = {"body": bone(mrot(S(0, 6)), ["0", "math.abs(" + S(0, 1.6) + ")", "0"]),
            "head": bone(mrot(S(180, 4))),
            "fore_l": bone(mrot(S(0, 32))), "fore_r": bone(mrot(S(0, 32))),
            "hind_l": bone(mrot(S(180, 38))), "hind_r": bone(mrot(S(180, 38))),
            "tail": bone(mrot(S(90, 8))),
            "ear_l": bone(mrot(S(0, 10) + " + 12")), "ear_r": bone(mrot(S(0, 10) + " + 12"))}
    for b in SPINES:
        idle[b] = bone(laid_rot)
        walk[b] = bone(laid_rot)
    # 予告 0.8 秒: しゃがんで (体の前を下げ、前脚を引く) 棘が立つ。0.8 で撃ち、棘が寝て、後ろ脚で跳ねて離れる
    shoot = {
        "body": bone(kf({0: (0, 0, 0), 0.7: (-14, 0, 0), 0.8: (-14, 0, 0), 0.95: (12, 0, 0), 1.25: (4, 0, 0), 1.6: (0, 0, 0)}),
                     kf({0: (0, 0, 0), 0.7: (0, -1.2, 0), 0.8: (0, -1.2, 0), 0.95: (0, 1.5, 0), 1.25: (0, 0, 0)}, False)),
        "head": bone(kf({0: (0, 0, 0), 0.7: (-22, 0, 0), 0.8: (-22, 0, 0), 0.95: (8, 0, 0), 1.6: (0, 0, 0)})),
        "fore_l": bone(kf({0: (0, 0, 0), 0.7: (-34, 0, 0), 0.8: (-34, 0, 0), 0.95: (-25, 0, 0), 1.6: (0, 0, 0)})),
        "fore_r": bone(kf({0: (0, 0, 0), 0.7: (-34, 0, 0), 0.8: (-34, 0, 0), 0.95: (-25, 0, 0), 1.6: (0, 0, 0)})),
        "hind_l": bone(kf({0: (0, 0, 0), 0.7: (-30, 0, 0), 0.8: (-30, 0, 0), 0.95: (-35, 0, 0), 1.25: (10, 0, 0), 1.6: (0, 0, 0)})),
        "hind_r": bone(kf({0: (0, 0, 0), 0.7: (-30, 0, 0), 0.8: (-30, 0, 0), 0.95: (-35, 0, 0), 1.25: (10, 0, 0), 1.6: (0, 0, 0)})),
        "ear_l": bone(kf({0: (0, 0, 0), 0.5: (60, 0, 0), 1.2: (60, 0, 0), 1.6: (0, 0, 0)})),
        "ear_r": bone(kf({0: (0, 0, 0), 0.5: (60, 0, 0), 1.2: (60, 0, 0), 1.6: (0, 0, 0)})),
    }
    # 棘: 寝た (60) -> 体の傾き (-14) を打ち消して真上 (14) へ 0.7 秒で立つ -> 撃った瞬間 (0.8〜0.9) に寝る
    for i, b in enumerate(SPINES):
        t = i * 0.03
        shoot[b] = bone(kf({0: (laid, 0, 0), 0.3 + t: (laid, 0, 0), 0.7: (14, 0, 0), 0.8: (14, 0, 0), 0.92: (laid, 0, 0), 1.6: (laid, 0, 0)}))
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (-8, 0, 0), 0.3: (0, 0, 0)})),
            "ear_l": bone(kf({0: (0, 0, 0), 0.08: (40, 0, 0), 0.3: (0, 0, 0)})),
            "ear_r": bone(kf({0: (0, 0, 0), 0.08: (40, 0, 0), 0.3: (0, 0, 0)}))}
    for b in SPINES:
        hurt[b] = bone(laid_rot)
    # 倒れる: 横倒しになり (体の中心 pivot のまま 90 度転がるので、半幅ぶん下げる)、脚が開く
    death = {"body": bone(kf({0: (0, 0, 0), 0.45: (0, 0, 90)}), kf({0: (0, 0, 0), 0.45: (0, -3.0, 0)}, False)),
             "hind_l": bone(kf({0: (0, 0, 0), 0.45: (-40, 0, 0)})), "hind_r": bone(kf({0: (0, 0, 0), 0.45: (-40, 0, 0)})),
             "fore_l": bone(kf({0: (0, 0, 0), 0.45: (30, 0, 0)})), "fore_r": bone(kf({0: (0, 0, 0), 0.45: (30, 0, 0)})),
             "ear_l": bone(kf({0: (0, 0, 0), 0.45: (70, 0, 0)})), "ear_r": bone(kf({0: (0, 0, 0), 0.45: (70, 0, 0)}))}
    for b in SPINES:
        death[b] = bone(laid_rot)
    return {"animation.spine_hare.idle": loop(3.0, idle), "animation.spine_hare.walk": loop(0.6, walk),
            "animation.spine_hare.shoot": once(1.6, shoot), "animation.spine_hare.hurt": once(0.3, hurt),
            "animation.spine_hare.death": once(0.6, death, True)}


def hauler():
    legs_a, legs_b = ["leg_fl", "leg_br"], ["leg_fr", "leg_bl"]
    # 待機: 頭を下げて草を食む (ラフの idle = 頭 -16)
    idle = {"head": bone(mrot(S(0, 4, 120) + " - 16")), "body": bone(position=["0", S(0, 0.2, 120), "0"]),
            "basket": bone(mrot(S(60, 1.2, 120))), "tail": bone(mrot(S(0, 6, 120))),
            "ear_l": bone(mrot(S(0, 5, 120))), "ear_r": bone(mrot(S(150, 5, 120)))}
    walk = {"body": bone(mrot(S(0, 3)), ["0", "math.abs(" + S(0, 0.7) + ")", "0"]),
            "head": bone(mrot(S(180, 4) + " + 4")),
            "basket": bone(mrot(S(90, 4))), "tail": bone(mrot(S(90, 10) + " - 10"))}
    for b in legs_a:
        walk[b] = bone(mrot(S(0, 30)))
    for b in legs_b:
        walk[b] = bone(mrot(S(180, 30)))
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (-6, 0, 0), 0.3: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.08: (14, 0, 0), 0.3: (0, 0, 0)})),
            "basket": bone(kf({0: (0, 0, 0), 0.08: (8, 0, 0), 0.3: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.5: (0, 0, 90)}), kf({0: (0, 0, 0), 0.5: (0, -4.2, 0)}, False)),
             "head": bone(kf({0: (0, 0, 0), 0.5: (20, 0, 0)})),
             "leg_fl": bone(kf({0: (0, 0, 0), 0.5: (30, 0, 0)})), "leg_fr": bone(kf({0: (0, 0, 0), 0.5: (30, 0, 0)})),
             "leg_bl": bone(kf({0: (0, 0, 0), 0.5: (-30, 0, 0)})), "leg_br": bone(kf({0: (0, 0, 0), 0.5: (-30, 0, 0)}))}
    return {"animation.bone_hauler.idle": loop(3.0, idle), "animation.bone_hauler.walk": loop(0.6, walk),
            "animation.bone_hauler.hurt": once(0.3, hurt), "animation.bone_hauler.death": once(0.6, death, True)}


def sentry():
    # 骨の番兵 (bone_sentinel.animation.json) の待機・歩きを土台に、背が高いぶんゆっくり
    idle = {"body": bone(mrot("0"), ["0", S(0, 0.3, 120), "0"]),
            "head": bone(["0", "-(%s)" % S(60, 3, 120), "0"]),
            "left_arm": bone([neg(S(40, 2.5, 120)), "0", "-3"]), "right_arm": bone([neg(S(-40, 2.5, 120)), "0", "3"])}
    walk = {"leg_l": bone(mrot(S(0, 28))), "leg_r": bone(mrot(S(0, -28))),
            "left_arm": bone(mrot(S(0, -20))), "right_arm": bone(mrot(S(0, 20))),
            "left_forearm": bone(mrot("8 + " + S(90, -8))), "right_forearm": bone(mrot("8 + " + S(90, 8))),
            "body": bone(mrot("-4"), ["0", "math.abs(" + S(0, 0.8) + ")", "0"]),
            "head": bone(["0", neg(S(180, 3)), "0"])}
    # 振り下ろし (予告 0.8 秒): 右腕を頭の上へ振りかぶり、0.8 で打ち下ろす。当たる範囲は前の扇
    attack = {"right_arm": bone(kf({0: (0, 0, 0), 0.7: (150, 0, 0), 0.8: (150, 0, 0), 0.92: (-20, 0, 0), 1.25: (-20, 0, 0), 1.5: (0, 0, 0)})),
              "right_forearm": bone(kf({0: (0, 0, 0), 0.7: (-30, 0, 0), 0.92: (-10, 0, 0), 1.5: (0, 0, 0)})),
              "body": bone(kf({0: (0, 0, 0), 0.7: (8, 0, 0), 0.92: (-14, 0, 0), 1.25: (-10, 0, 0), 1.5: (0, 0, 0)})),
              "head": bone(kf({0: (0, 0, 0), 0.7: (6, 0, 0), 0.92: (-8, 0, 0), 1.5: (0, 0, 0)}))}
    # 盾の突進 (予告 1.2 秒 = 床の線): 体を沈めて前へ傾け、盾を前に低く構える。1.2 で突っ込み、止まって戻る
    pose = {"body": (-26, 0, 0), "head": (-8, 0, 0), "left_arm": (62, 0, 0), "left_forearm": (0, 90, 0),
            "right_arm": (40, 0, 0), "right_forearm": (-30, 0, 0), "leg_l": (30, 0, 0), "leg_r": (-14, 0, 0)}
    rush = {k: (v[0] * 1.3, v[1], v[2]) if k in ("body",) else v for k, v in pose.items()}
    charge = {}
    for b, v in pose.items():
        r = rush[b]
        charge[b] = bone(kf({0: (0, 0, 0), 0.6: v, 1.2: v, 1.35: r, 1.7: r, 2.1: (0, 0, 0)}))
    charge["body"]["position"] = kf({0: (0, 0, 0), 0.6: (0, -1.5, 0), 1.7: (0, -1.5, 0), 2.1: (0, 0, 0)}, False)
    # 骨笛 (予告 0.8 秒、笛を吹く): 頭を反らし、右腕を曲げて笛を口へ
    whistle = {"head": bone(kf({0: (0, 0, 0), 0.5: (14, 0, 0), 1.3: (14, 0, 0), 1.8: (0, 0, 0)})),
               "right_arm": bone(kf({0: (0, 0, 0), 0.5: (180, -40, 10), 1.3: (180, -40, 10), 1.8: (0, 0, 0)})),
               "right_forearm": bone(kf({0: (0, 0, 0), 0.5: (-150, 0, 0), 1.3: (-150, 0, 0), 1.8: (0, 0, 0)})),
               "body": bone(kf({0: (0, 0, 0), 0.5: (-6, 0, 0), 1.3: (-6, 0, 0), 1.8: (0, 0, 0)}))}
    # 足踏みの輪 (予告 1.2 秒): 片脚を高く上げ、拳を頭上へ。1.2 で踏み下ろす
    stomp = {"body": bone(kf({0: (0, 0, 0), 1.0: (4, 0, 0), 1.2: (-10, 0, 0), 1.6: (-4, 0, 0), 2.0: (0, 0, 0)}),
                          kf({0: (0, 0, 0), 1.0: (0, 0, 0), 1.2: (0, -2.4, 0), 1.6: (0, -1, 0), 2.0: (0, 0, 0)}, False)),
             "leg_r": bone(kf({0: (0, 0, 0), 1.0: (80, 0, 0), 1.2: (0, 0, 0), 2.0: (0, 0, 0)})),
             "right_arm": bone(kf({0: (0, 0, 0), 1.0: (170, 0, 0), 1.2: (30, 0, 0), 1.6: (10, 0, 0), 2.0: (0, 0, 0)})),
             "left_arm": bone(kf({0: (0, 0, 0), 1.0: (-8, 0, 0), 1.2: (20, 0, 0), 2.0: (0, 0, 0)}))}
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (-6, 0, 0), 0.3: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.08: (12, 0, 0), 0.3: (0, 0, 0)}))}
    # 倒れる: 膝をつき、前へ崩れる
    death = {"body": bone(kf({0: (0, 0, 0), 0.5: (-20, 0, 0), 1.0: (-85, 0, 0)}), kf({0: (0, 0, 0), 0.5: (0, -5, 0), 1.0: (0, -9.5, 0)}, False)),
             "head": bone(kf({0: (0, 0, 0), 0.6: (-25, 0, 0), 1.0: (-15, 0, 0)})),
             "leg_l": bone(kf({0: (0, 0, 0), 0.5: (50, 0, 0), 1.0: (20, 0, 0)})), "leg_r": bone(kf({0: (0, 0, 0), 0.5: (30, 0, 0), 1.0: (10, 0, 0)})),
             "left_arm": bone(kf({0: (0, 0, 0), 0.6: (40, 0, 0), 1.0: (70, 0, 0)})), "right_arm": bone(kf({0: (0, 0, 0), 0.6: (40, 0, 0), 1.0: (70, 0, 0)}))}
    p = "animation.elder_sentry."
    return {p + "idle": loop(3.0, idle), p + "walk": loop(1.0, walk), p + "attack": once(1.5, attack), p + "charge": once(2.1, charge),
            p + "whistle": once(1.8, whistle), p + "stomp": once(2.0, stomp), p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 音 (既存の音源に高さを付けた別名。音源そのものの合成は別の手当て)
M = "skyabyss:mobs/"
SOUNDS = {
    "rock_shell_beetle_ambient": [(M + "moss_crawler_ambient", 0.55, 0.7)],
    "rock_shell_beetle_hurt": [(M + "bone_sentinel_guard", 0.8, 0.9)],
    "rock_shell_beetle_death": [(M + "moss_crawler_death", 0.6, 1.0), ("minecraft:block/stone/break1", 0.7, 0.8)],
    "rock_shell_beetle_step": [("minecraft:block/stone/step1", 1.4, 0.35), ("minecraft:block/stone/step2", 1.5, 0.35)],
    "rock_shell_beetle_charge": [(M + "captain_erupt_charge", 1.6, 0.8)],
    "spine_hare_ambient": [(M + "rib_archer_ambient", 1.7, 0.4)],
    "spine_hare_hurt": [(M + "rib_archer_hurt_1", 1.8, 0.7), (M + "rib_archer_hurt_2", 1.8, 0.7)],
    "spine_hare_death": [(M + "rib_archer_death", 1.8, 0.7)],
    "spine_hare_step": [("minecraft:block/grass/step1", 1.6, 0.4), ("minecraft:block/grass/step2", 1.6, 0.4)],
    "spine_hare_shoot": [(M + "rib_archer_shoot", 1.5, 0.9)],
    "spine_hare_raise": [(M + "stone_heart_spike_charge", 1.9, 0.5)],
    "bone_hauler_ambient": [(M + "moss_crawler_ambient", 0.7, 0.6)],
    "bone_hauler_hurt": [(M + "moss_crawler_hurt", 0.75, 0.9)],
    "bone_hauler_death": [(M + "moss_crawler_death", 0.7, 0.9)],
    "bone_hauler_step": [("minecraft:block/gravel/step1", 1.0, 0.4), ("minecraft:block/gravel/step2", 1.0, 0.4)],
    "elder_sentry_ambient": [(M + "bone_sentinel_ambient", 0.7, 0.8)],
    "elder_sentry_hurt": [(M + "bone_sentinel_hurt_1", 0.75, 1.0), (M + "bone_sentinel_hurt_2", 0.75, 1.0)],
    "elder_sentry_death": [(M + "bone_sentinel_death", 0.7, 1.0)],
    "elder_sentry_step": [(M + "bone_sentinel_step_1", 0.7, 0.6), (M + "bone_sentinel_step_2", 0.7, 0.6)],
    "elder_sentry_guard": [(M + "bone_sentinel_guard", 0.7, 1.0)],
    "elder_sentry_swing": [(M + "captain_leap_roar", 1.6, 0.6)],
    "elder_sentry_charge": [(M + "captain_erupt_charge", 1.0, 1.4)],
    "elder_sentry_horn": [(M + "captain_roar", 1.7, 0.9)],
    "elder_sentry_stomp": [(M + "stone_heart_slam", 1.2, 1.2)],
}
SUBTITLES = {
    "rock_shell_beetle_ambient": ("Shell scrapes", "殻がこすれる"), "rock_shell_beetle_hurt": ("Shell cracks", "殻が鳴る"),
    "rock_shell_beetle_death": ("Rock shell beetle falls", "岩殻虫が倒れる"), "rock_shell_beetle_step": ("Scuttling", "足早に這う"),
    "rock_shell_beetle_charge": ("Rock shell beetle rears", "岩殻虫が立ち上がる"),
    "spine_hare_ambient": ("Hare rustles", "うさぎが身じろぐ"), "spine_hare_hurt": ("Spine hare squeals", "棘うさぎが鳴く"),
    "spine_hare_death": ("Spine hare falls", "棘うさぎが倒れる"), "spine_hare_step": ("Hopping", "跳ねる"),
    "spine_hare_shoot": ("Spine flies", "棘が飛ぶ"), "spine_hare_raise": ("Spines rise", "棘が立つ"),
    "bone_hauler_ambient": ("Hauler grunts", "骨負いが鳴く"), "bone_hauler_hurt": ("Hauler cries out", "骨負いが悲鳴をあげる"),
    "bone_hauler_death": ("Hauler falls", "骨負いが倒れる"), "bone_hauler_step": ("Heavy steps", "重い足音"),
    "elder_sentry_ambient": ("Old bones creak", "古い骨がきしむ"), "elder_sentry_hurt": ("Elder sentry grunts", "老いた歩哨がうめく"),
    "elder_sentry_death": ("Elder sentry falls", "老いた歩哨が倒れる"), "elder_sentry_step": ("Heavy footsteps", "重い足音"),
    "elder_sentry_guard": ("Shield blocks", "盾が受ける"), "elder_sentry_swing": ("Elder sentry swings", "老いた歩哨が振りかぶる"),
    "elder_sentry_charge": ("Elder sentry rushes", "老いた歩哨が突っ込む"), "elder_sentry_horn": ("Bone horn sounds", "骨笛が鳴る"),
    "elder_sentry_stomp": ("Ground shakes", "地面が揺れる"),
}
LANG = {
    "entity.skyabyss.rock_shell_beetle": ("Rock Shell Beetle", "岩殻虫"),
    "entity.skyabyss.spine_hare": ("Spine Hare", "棘うさぎ"),
    "entity.skyabyss.bone_hauler": ("Bone Hauler", "骨負い"),
    "entity.skyabyss.elder_sentry": ("Elder Sentry", "老いた歩哨"),
    "item.skyabyss.rock_shell_beetle_spawn_egg": ("Rock Shell Beetle Spawn Egg", "岩殻虫のスポーンエッグ"),
    "item.skyabyss.spine_hare_spawn_egg": ("Spine Hare Spawn Egg", "棘うさぎのスポーンエッグ"),
    "item.skyabyss.bone_hauler_spawn_egg": ("Bone Hauler Spawn Egg", "骨負いのスポーンエッグ"),
    "item.skyabyss.elder_sentry_spawn_egg": ("Elder Sentry Spawn Egg", "老いた歩哨のスポーンエッグ"),
    "item.skyabyss.sentry_tally": ("Sentry's Tally", "歩哨の割符"),
    "message.skyabyss.ch1.sentry.tally": ("In the old sentry's grip was a notched bone tally.", "老いた歩哨の手に、刻み目のある骨の割符が握られていた。"),
    "item.skyabyss.sentry_tally.desc": ("A notched bone tally. The old sentry carried it for someone who never came to claim it.",
                                        "刻み目のある骨の割符。老いた歩哨が、取りに来なかった誰かのために持っていた。"),
}


def sounds():
    p = os.path.join(A, "sounds.json")
    data = json.load(open(p, encoding="utf-8"))
    for key, entries in SOUNDS.items():
        data[key] = {"subtitle": "subtitles.skyabyss." + key,
                     "sounds": [{"name": n, "pitch": round(pitch, 3), "volume": vol} for n, pitch, vol in entries]}
    wjson(p, data)
    for i, lang in enumerate(("en_us", "ja_jp")):
        lp = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_mobsounds", "lang", lang + ".json")
        d = json.load(open(lp, encoding="utf-8"))
        for key, texts in SUBTITLES.items():
            d["subtitles.skyabyss." + key] = texts[i]
        wjson(lp, d)


def lang():
    for i, lang_ in enumerate(("en_us", "ja_jp")):
        lp = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_ch1", "lang", lang_ + ".json")
        d = json.load(open(lp, encoding="utf-8"))
        for key, texts in LANG.items():
            d[key] = texts[i]
        wjson(lp, d)


def tally_icon():
    """歩哨の割符: 刻み目のある骨の板 (32x32)。ラフではなく仮の絵 (一章の言葉: 骨・琥珀・灰青)"""
    from PIL import Image
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    bone, shade, dark, amber, grey = (0xD8, 0xCD, 0xB2, 255), (0xB0, 0xA4, 0x88, 255), (0x6E, 0x62, 0x4C, 255), (0xE0, 0x9A, 0x30, 255), (0x5A, 0x60, 0x70, 255)
    for y in range(6, 27):
        for x in range(9, 24):
            edge = x in (9, 23) or y in (6, 26)
            px[x, y] = dark if edge else (shade if x > 19 else bone)
    for n, y in enumerate(range(9, 24, 3)):  # 刻み目
        for x in range(12, 12 + (11 if n % 2 == 0 else 7)):
            px[x, y] = dark
    for x in range(14, 18):  # 琥珀のひび
        for y in range(21, 24):
            px[x, y] = amber
    for y in range(3, 7):  # 吊るす紐の穴
        px[16, y] = grey
        px[17, y] = grey
    out = os.path.join(A, "textures", "item", "sentry_tally.png")
    im.save(out)
    wjson(os.path.join(A, "models", "item", "sentry_tally.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/sentry_tally"}})
    for mob in MOBS:
        wjson(os.path.join(A, "models", "item", mob + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def install():
    for mob in MOBS:
        d = os.path.join(R, mob, "a", mob + "_a_")
        subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "install_lowpoly.py"), mob, "--mesh", d + "mesh.json", "--atlas", d + "atlas.png",
                               "--glow", d + "glowmask.png"], cwd=ROOT)
        # LowPoly.enabled = false のときの代用 (デバッグ箱 + アトラスそのまま)
        shutil.copyfile(os.path.join(A, "geo", "entity", mob + "_lowpoly_debug.geo.json"), os.path.join(A, "geo", "entity", mob + ".geo.json"))
        os.makedirs(os.path.join(A, "textures", "entity"), exist_ok=True)
        shutil.copyfile(os.path.join(A, "textures", "lowpoly", mob + ".png"), os.path.join(A, "textures", "entity", mob + ".png"))
        gm = os.path.join(A, "textures", "lowpoly", mob + "_glowmask.png")
        if os.path.exists(gm):
            shutil.copyfile(gm, os.path.join(A, "textures", "entity", mob + "_glowmask.png"))


def main():
    install()
    anims = {}
    for mob, fn in (("rock_shell_beetle", beetle), ("spine_hare", hare), ("bone_hauler", hauler), ("elder_sentry", sentry)):
        wjson(os.path.join(A, "animations", "entity", mob + ".animation.json"), {"format_version": "1.8.0", "animations": fn()})
    sounds()
    lang()
    tally_icon()
    print("done")


if __name__ == "__main__":
    main()
