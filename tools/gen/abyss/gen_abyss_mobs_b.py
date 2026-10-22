#!/usr/bin/env python3
"""五章の深淵の敵 9 体 (B 組: 灰の層 3・深み 2・深淵だけ 4) の素材を入れる (docs/design/36 の 4・4.1、A3)。

  python -I tools/abyss/paint_abyss_b.py      (先に絵を描く: docs/review/abyss_painted/)
  PYTHONIOENCODING=utf-8 python -I tools/gen/abyss/gen_abyss_mobs_b.py

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


# ---------------------------------------------------------------- 燠の番人 (熱の気配。胸の燠が先に見える。前かがみ・手を上げる熱の輪)
def ember_keeper():
    p = "animation.ember_keeper."
    idle = {"body": bone(["0", "0", S(0, 2, 60)], [0, S(0, 0.2, 60), 0]), "head": bone(["0", S(90, 6, 60), "0"]),
            "arm_l": bone([S(0, 6, 60), "0", "0"]), "arm_r": bone([S(180, 6, 60), "0", "0"])}
    walk = legs_walk(["leg_l", "leg_r"], 360, 18, 3)
    walk["body"] = bone(["0", "0", S(0, 2, 360)])
    walk["arm_l"] = bone([S(0, 10, 360), "0", "0"])
    walk["arm_r"] = bone([S(180, 10, 360), "0", "0"])
    # 予告 (赤い輪の前触れ): 手を胸の前へ、体が少し前へ。熱の輪: 両腕を広げて前へ
    telegraph = {"arm_l": bone(kf({0: (0, 0, 0), 0.8: (-70, 0, -20)})), "arm_r": bone(kf({0: (0, 0, 0), 0.8: (-70, 0, 20)})),
                 "body": bone(kf({0: (0, 0, 0), 0.8: (-14, 0, 0)}))}
    heat = {"arm_l": bone(kf({0: (0, 0, 0), 0.1: (-120, 0, -30), 0.4: (-120, 0, -30), 0.6: (0, 0, 0)})),
            "arm_r": bone(kf({0: (0, 0, 0), 0.1: (-120, 0, 30), 0.4: (-120, 0, 30), 0.6: (0, 0, 0)})),
            "body": bone(kf({0: (0, 0, 0), 0.1: (-16, 0, 0), 0.6: (0, 0, 0)}))}
    return {p + "idle": loop(3.0, idle), p + "walk": loop(1.0, walk), p + "telegraph": once(0.8, telegraph, True),
            p + "heat": once(0.6, heat), p + "hurt": once(0.22, hurt_bones()), p + "death": once(0.9, death_bones(90, 1.0), True)}


# ---------------------------------------------------------------- 煤の子 (小さい。数で来る。跳ねる)
def soot_child():
    p = "animation.soot_child."
    idle = {"body": bone(["0", "0", S(0, 4, 120)], [0, S(0, 0.25, 120), 0]), "head": bone(["0", S(60, 12, 120), "0"])}
    walk = {"body": bone(["0", "0", S(0, 6, 480)], [0, "math.abs(%s)*0.6" % S(0, 1, 480), 0]),
            "arm_l": bone([S(0, 30, 480), "0", "0"]), "arm_r": bone([S(180, 30, 480), "0", "0"])}
    attack = {"body": bone(kf({0: (0, 0, 0), 0.1: (-35, 0, 0), 0.3: (0, 0, 0)})), "head": bone(kf({0: (0, 0, 0), 0.1: (-30, 0, 0), 0.3: (0, 0, 0)}))}
    return {p + "idle": loop(2.0, idle), p + "walk": loop(0.4, walk), p + "attack": once(0.3, attack),
            p + "hurt": once(0.2, hurt_bones()), p + "death": once(0.5, death_bones(170, 0.1), True)}


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
    shoot = {"shard": bone(kf({0: (0, 0, 0), 0.3: (0, 0, 0), 0.5: (-70, 0, 0), 0.9: (0, 0, 0)}),
                           kf({0: (0, 0, 0), 0.3: (0, 0, 0), 0.5: (0, 0, 0), 0.9: (0, 0, 0)}, False)),
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


# ---------------------------------------------------------------- 鼓動もどき (膜の袋。こぶが拍を打つ。噛むときは顎が開く)
def heartbeat_mimic():
    p = "animation.heartbeat_mimic."
    beat = lambda speed, amp: {"bulge_a": bone(["0", "0", "0"], [S(0, 0, speed), S(0, 0, speed), 0]),
                               "bulge_b": bone([0, 0, 0]), "bulge_c": bone([0, 0, 0])}
    idle = {"body": bone(["0", "0", S(0, 2, 120)], [0, "math.abs(%s)*0.5" % S(0, 1, 60), 0]),
            "bulge_a": bone(["0", "0", "0"], [0, "math.abs(%s)*0.8" % S(0, 1, 60), 0]),
            "bulge_b": bone(["0", "0", "0"], [0, "math.abs(%s)*0.6" % S(40, 1, 60), 0]),
            "bulge_c": bone(["0", "0", "0"], [0, "math.abs(%s)*0.6" % S(80, 1, 60), 0])}
    lure = {"body": bone(["0", "0", S(0, 3, 240)], [0, "math.abs(%s)*1.0" % S(0, 1, 240), 0]),
            "bulge_a": bone(["0", "0", "0"], [0, "math.abs(%s)*1.2" % S(0, 1, 240), 0])}
    bite = {"jaw": bone(kf({0: (0, 0, 0), 0.1: (-50, 0, 0), 0.25: (0, 0, 0)})), "body": bone(kf({0: (0, 0, 0), 0.1: (-10, 0, 0), 0.25: (0, 0, 0)}))}
    for i in range(4):
        bite["rib%d" % i] = bone(kf({0: (0, 0, 0), 0.1: (0, 0, 14 if i % 2 else -14), 0.25: (0, 0, 0)}))
    return {p + "idle": loop(3.0, idle), p + "lure": loop(1.0, lure), p + "bite": once(0.3, bite),
            p + "hurt": once(0.25, hurt_bones()), p + "death": once(1.0, death_bones(90, 0.4), True)}


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


ANIMS = {"ash_trail": ash_trail, "ember_keeper": ember_keeper, "soot_child": soot_child, "past_shadow": past_shadow,
         "nameless_shape": nameless_shape, "lamp_eater": lamp_eater, "ceiling_walker": ceiling_walker,
         "heartbeat_mimic": heartbeat_mimic, "rope_hanger": rope_hanger}


# ---------------------------------------------------------------- 素材を入れる
def install():
    for mob, opt in CHOICE.items():
        stem = "%s_%s" % (mob, opt)
        mesh = os.path.join(ROUGH, mob, opt, stem + "_mesh.json")
        base = os.path.join(PAINT, mob + "_base_atlas.png")
        gmask = os.path.join(PAINT, mob + "_base_glowmask.png")
        subprocess.check_call([sys.executable, os.path.join(ROOT, "tools", "install/install_lowpoly.py"), mob, "--mesh", mesh, "--atlas", base,
                               "--glow", gmask], cwd=ROOT)
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
    install()
    sounds()
    item_textures()
    print("done")


if __name__ == "__main__":
    main()
