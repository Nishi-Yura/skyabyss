#!/usr/bin/env python3
"""嵐と灰の土地に足した敵 6 種 (issue #20。2026-11-09 の頁「嵐と灰の新しい敵」の選定) を、選んだラフから入れる。

  python -X utf8 -I tools/gen/foes/gen_late_foes.py

入力: docs/review/roughs_late_foes/<id>/<案>/<id>_<案>_{mesh.json,atlas.png,glowmask.png}   (ラフは tools/rough/lowpoly/late_foes/)
出力 (assets/skyabyss/ の下): lowpoly/<id>.json・textures/lowpoly/<id>(_glowmask).png・geo/entity/<id>_lowpoly(_debug).geo.json (tools/install/install_lowpoly.py)、
  geo/entity/<id>.geo.json + textures/entity/<id>.png (LowPoly.enabled = false のときの箱の代用)、animations/entity/<id>.animation.json、
  models/item/<id>_spawn_egg.json
動きの名前は Java (entity/foe/<型>) と同じ: idle・walk・hurt・death と技の名前。
回転の約束: ラフの姿勢 = 数学の右手系 (前 -Z。x が + = 前が上がる) で書き、ファイルへは (-x, -y, z) (tools/gen/ch4/gen_ch4_ground_mobs.py と同じ)。
"""
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
R = "docs/review/roughs_late_foes"
CHOICE = {"bolt_slinger": "a", "storm_jelly": "a", "crag_tortoise": "a", "ember_spitter": "a", "ash_whirl": "c", "basalt_scorpion": "a"}


def wjson(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")


def neg(e):
    return "-(%s)" % e


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
    return {"%.2f" % t: (rot(*v) if rotation else list(v)) for t, v in frames.items()}


def bone(rotation=None, position=None):
    d = {}
    if rotation:
        d["rotation"] = rotation
    if position:
        d["position"] = position
    return d


def hurt_death(p, body, fall=90, axis="z"):
    hurt = {body: bone(kf({0: (0, 0, 0), 0.08: (-10, 0, 0), 0.3: (0, 0, 0)}))}
    v = (0, 0, fall) if axis == "z" else (fall, 0, 0)
    death = {body: bone(kf({0: (0, 0, 0), 0.6: v, 1.0: v}), kf({0: (0, 0, 0), 0.6: (0, -1, 0), 1.0: (0, -1.5, 0)}, False))}
    return {p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 雷石投げ (二足。投げ・跳び退き)
def slinger():
    p = "animation.bolt_slinger."
    idle = {"body": bone(position=["0", S(0, 0.15, 120), "0"]), "head": bone(["0", S(0, 6, 90), "0"]), "arm_r": bone(mrot(S(0, 4, 120)))}
    walk = {"leg_l": bone(mrot(S(0, 26, 540))), "leg_r": bone(mrot(S(180, 26, 540))), "arm_l": bone(mrot(S(180, 18, 540))),
            "arm_r": bone(mrot(S(0, 14, 540))), "body": bone(position=["0", "math.abs(" + S(0, 0.25, 540) + ")", "0"])}
    throw = {"body": bone(kf({0: (0, 0, 0), 0.6: (0, 25, 0), 0.9: (-8, -20, 0), 1.2: (0, 0, 0)})),
             "arm_r": bone(kf({0: (0, 0, 0), 0.6: (150, 0, 20), 0.85: (-60, 0, 0), 1.2: (0, 0, 0)})),
             "arm_l": bone(kf({0: (0, 0, 0), 0.6: (-40, 0, -20), 1.2: (0, 0, 0)}))}
    hop = {"body": bone(kf({0: (0, 0, 0), 0.15: (14, 0, 0), 0.5: (0, 0, 0)}), kf({0: (0, 0, 0), 0.2: (0, 3, 2), 0.5: (0, 0, 0)}, False)),
           "leg_l": bone(kf({0: (0, 0, 0), 0.2: (30, 0, 0), 0.5: (0, 0, 0)})), "leg_r": bone(kf({0: (0, 0, 0), 0.2: (-20, 0, 0), 0.5: (0, 0, 0)}))}
    out = {p + "idle": loop(3.0, idle), p + "walk": loop(2 / 3, walk), p + "throw": once(1.2, throw), p + "hop": once(0.5, hop)}
    out.update(hurt_death(p, "body", 90, "x"))
    return out


# ---------------------------------------------------------------- 雷雲くらげ (漂う。縮む・放電)
def jelly():
    p = "animation.storm_jelly."
    T = ["t%d" % i for i in range(6)]
    idle = {"bell": bone(position=["0", S(0, 0.6, 90), "0"])}
    for i, t in enumerate(T):
        idle[t] = bone(mrot(S(i * 60, 8, 120)))
    walk = dict(idle)
    charge = {"bell": bone(kf({0: (0, 0, 0), 1.2: (0, 0, 0)}), kf({0: (0, 0, 0), 1.2: (0, -1.2, 0)}, False))}
    for i, t in enumerate(T):
        s = 1 if i < 3 else -1
        charge[t] = bone(kf({0: (0, 0, 0), 1.0: (s * 40, 0, 0), 1.2: (s * 45, 0, 0)}))
    burst = {"bell": bone(position=kf({0: (0, -1.2, 0), 0.15: (0, 0.8, 0), 0.6: (0, 0, 0)}, False))}
    for i, t in enumerate(T):
        s = 1 if i < 3 else -1
        burst[t] = bone(kf({0: (s * 45, 0, 0), 0.15: (-s * 55, 0, 0), 0.6: (0, 0, 0)}))
    hurt = {"bell": bone(kf({0: (0, 0, 0), 0.1: (12, 0, 0), 0.3: (0, 0, 0)}))}
    death = {"bell": bone(kf({0: (0, 0, 0), 1.0: (0, 0, 40)}), kf({0: (0, 0, 0), 1.0: (0, -20, 0)}, False))}
    return {p + "idle": loop(4.0, idle), p + "walk": loop(4.0, walk), p + "charge": once(1.2, charge, True), p + "burst": once(0.6, burst),
            p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


LEGS4 = ["leg_fl", "leg_fr", "leg_bl", "leg_br"]


def legs4_walk(speed, amp):
    ph = {"leg_fl": 0, "leg_br": 0, "leg_fr": 180, "leg_bl": 180}
    return {b: bone(mrot(S(ph[b], amp, speed))) for b in LEGS4}


# ---------------------------------------------------------------- 峰の甲羅 (籠る・体当たり)
def tortoise():
    p = "animation.crag_tortoise."
    idle = {"head": bone(["0", S(0, 8, 60), "0"])}
    walk = legs4_walk(360, 16)
    walk["body"] = bone(["0", "0", S(0, 2, 360)])
    guard = {"head": bone(kf({0: (0, 0, 0), 0.6: (-10, 0, 0)}), kf({0: (0, 0, 0), 0.6: (0, -1, 3)}, False)), "body": bone(kf({0: (0, 0, 0), 0.6: (6, 0, 0)}))}
    for b in LEGS4:
        guard[b] = bone(kf({0: (0, 0, 0), 0.6: (0, 0, 30 if b.endswith("l") else -30)}))
    ram = {"body": bone(kf({0: (6, 0, 0), 0.4: (-10, 0, 0), 1.0: (0, 0, 0)})), "head": bone(kf({0: (-10, 0, 0), 0.4: (-20, 0, 0), 1.0: (0, 0, 0)}))}
    for b in LEGS4:
        ram[b] = bone(kf({0: (0, 0, 0), 0.4: (-30 if "f" in b[4] else 40, 0, 0), 1.0: (0, 0, 0)}))
    out = {p + "idle": loop(5.0, idle), p + "walk": loop(1.0, walk), p + "guard": once(0.6, guard, True), p + "ram": once(1.0, ram)}
    out.update(hurt_death(p, "body", 100))
    return out


# ---------------------------------------------------------------- 熾吐き (膨らむ・吐く・灰を吹く)
def spitter():
    p = "animation.ember_spitter."
    idle = {"body": bone(position=["0", S(0, 0.2, 120), "0"]), "head": bone(["0", S(0, 5, 60), "0"])}
    walk = legs4_walk(540, 20)
    walk["body"] = bone(position=["0", "math.abs(" + S(0, 0.4, 540) + ")", "0"])
    spit = {"head": bone(kf({0: (0, 0, 0), 0.8: (22, 0, 0), 1.0: (30, 0, 0), 1.3: (0, 0, 0)})),
            "jaw": bone(kf({0: (0, 0, 0), 0.8: (0, 0, 0), 0.95: (-30, 0, 0), 1.3: (0, 0, 0)})),
            "body": bone(kf({0: (0, 0, 0), 0.8: (-8, 0, 0), 1.3: (0, 0, 0)}))}
    puff = {"head": bone(kf({0: (0, 0, 0), 0.3: (-10, 0, 0), 0.6: (0, 0, 0)})), "jaw": bone(kf({0: (0, 0, 0), 0.3: (-25, 0, 0), 0.6: (0, 0, 0)}))}
    out = {p + "idle": loop(3.0, idle), p + "walk": loop(2 / 3, walk), p + "spit": once(1.3, spit), p + "puff": once(0.6, puff)}
    out.update(hurt_death(p, "body", 160))
    return out


# ---------------------------------------------------------------- 灰の渦 (壺。回る・吹き上げ)
def whirl():
    p = "animation.ash_whirl."
    idle = {"spin": bone(position=["0", S(0, 0.4, 90), "0"]), "ring1": bone(["0", "query.anim_time * 90", "0"]), "ring2": bone(["0", "-query.anim_time * 120", "0"])}
    walk = idle
    spin = {"ring1": bone(["0", "query.anim_time * 600", "0"]), "ring2": bone(["0", "-query.anim_time * 800", "0"]), "ring0": bone(["0", "query.anim_time * 300", "0"]),
            "spin": bone(position=["0", "math.min(query.anim_time, 1.2) * 1.0", "0"])}
    burst = {"ring2": bone(kf({0: (0, 0, 0), 0.2: (16, 0, 0), 0.8: (0, 0, 0)})), "mask": bone(kf({0: (0, 0, 0), 0.2: (-14, 0, 0), 0.8: (0, 0, 0)})),
             "spin": bone(position=kf({0: (0, 1.2, 0), 0.4: (0, -1.5, 0), 0.8: (0, -1.5, 0)}, False))}
    out = {p + "idle": loop(4.0, idle), p + "walk": loop(4.0, walk), p + "spin": loop(1.2, spin), p + "burst": once(0.8, burst, True)}
    out.update(hurt_death(p, "spin", 80))
    return out


LEGS6 = ["leg_l1", "leg_l2", "leg_l3", "leg_r1", "leg_r2", "leg_r3"]


# ---------------------------------------------------------------- 黒石の甲殻 (構える・突く)
def scorpion():
    p = "animation.basalt_scorpion."
    idle = {"tail": bone(mrot(S(0, 4, 90))), "claw_l": bone(["0", S(0, 4, 60), "0"]), "claw_r": bone(["0", S(180, 4, 60), "0"])}
    walk = {}
    for i, b in enumerate(LEGS6):
        ph = 0 if (i % 2 == 0) else 180
        walk[b] = bone(["0", S(ph, 18, 720), "0"])
    walk["tail"] = bone(mrot(S(0, 5, 360)))
    guard = {"claw_l": bone(kf({0: (0, 0, 0), 0.5: (0, 30, 0)})), "claw_r": bone(kf({0: (0, 0, 0), 0.5: (0, -30, 0)})),
             "head": bone(kf({0: (0, 0, 0), 0.5: (8, 0, 0)}))}
    strike = {"tail": bone(kf({0: (0, 0, 0), 0.75: (12, 0, 0), 0.95: (-35, 0, 0), 1.4: (0, 0, 0)})),
              "body": bone(kf({0: (0, 0, 0), 0.75: (4, 0, 0), 0.95: (-8, 0, 0), 1.4: (0, 0, 0)})),
              "claw_l": bone(rot(0, 30, 0)), "claw_r": bone(rot(0, -30, 0))}
    out = {p + "idle": loop(3.0, idle), p + "walk": loop(0.5, walk), p + "guard": once(0.5, guard, True), p + "strike": once(1.4, strike)}
    out.update(hurt_death(p, "body", 170))
    return out


ANIMS = {"bolt_slinger": slinger, "storm_jelly": jelly, "crag_tortoise": tortoise, "ember_spitter": spitter, "ash_whirl": whirl, "basalt_scorpion": scorpion}


def install():
    for mob, k in CHOICE.items():
        d = "%s/%s/%s/%s_%s_" % (R, mob, k, mob, k)
        cmd = [sys.executable, os.path.join(ROOT, "tools", "install", "install_lowpoly.py"), mob, "--mesh", d + "mesh.json", "--atlas", d + "atlas.png",
               "--glow", d + "glowmask.png"]
        subprocess.check_call(cmd, cwd=ROOT)
        os.makedirs(os.path.join(A, "textures", "entity"), exist_ok=True)
        shutil.copyfile(os.path.join(A, "geo", "entity", mob + "_lowpoly_debug.geo.json"), os.path.join(A, "geo", "entity", mob + ".geo.json"))
        shutil.copyfile(os.path.join(A, "textures", "lowpoly", mob + ".png"), os.path.join(A, "textures", "entity", mob + ".png"))
        wjson(os.path.join(A, "models", "item", mob + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
        wjson(os.path.join(A, "animations", "entity", mob + ".animation.json"), {"format_version": "1.8.0", "animations": ANIMS[mob]()})


if __name__ == "__main__":
    install()
    print("done")
