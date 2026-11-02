#!/usr/bin/env python3
"""四章の地上の敵 4 種 (熾り蟲・灰の屑・灰ひきずり・古い灰の番人) と小物 5 つを、選んだラフから入れる (docs/design/21 の 6.1、P19・P20)。

  PYTHONIOENCODING=utf-8 python -I tools/gen/ch4/gen_ch4_ground_mobs.py

入力: docs/review/ch4_roughs/<id>/<案>/<id>_<案>_{mesh.json,atlas.png,glowmask.png}   (ラフの作り方は tools/rough/lowpoly/ch4/)
      灰の番人の変種 (屑・古い灰の番人) は燃え上がった姿のアトラス <id>_<案>_{atlas_flared,glowmask_flared}.png と道具 <id>_<案>_poker_*
      小物は docs/review/ch4_roughs/items/icons/<品>_<案>_32.png
出力 (assets/skyabyss/ の下):
  lowpoly/<id>.json, textures/lowpoly/<id>(.png|_glowmask.png|_flared.png), geo/entity/<id>_lowpoly(_debug).geo.json   (tools/install/install_lowpoly.py)
  geo/entity/<id>.geo.json + textures/entity/<id>.png   LowPoly.enabled = false のときの箱の代用
  animations/entity/<id>.animation.json                 熾り蟲・灰ひきずりはこのファイルで作る。古い灰の番人は灰の番人のアニメに「亀裂」「熱の波」を足す。屑は灰の番人のアニメをそのまま使う
  textures/item/<品>.png・models/item/<品>.json・models/item/<id>_spawn_egg.json
  assets/skyabyss_ch4/sounds.json                        音の出来事 (skyabyss_ch4 の名前空間。既存の音の別名。字幕の文は lang)
  tools/sources/<品>/   選ばなかった案の絵

案は CHOICE で選ぶ (2026-10-22 のユーザーの選定: 熾り蟲 A・灰の屑 C・灰ひきずり A・古い灰の番人 A、小物は煤鉄 A・灰麻 A・灰の護り糸 B・煤の砥石 C・灰の冷やし水 A)。
回転の約束: 熾り蟲・灰ひきずりはラフの姿勢表 = 数学の右手系 (前 -Z。x が + = 前が上がる) で書き、ファイルへは (-x, -y, z)。
"""
import copy
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
A4 = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_ch4")
R = "docs/review/ch4_roughs"
CHOICE = {"cinder_mite": "a", "ash_husk": "c", "ash_dragger": "a", "elder_thrall": "a"}
ITEM_CHOICE = {"soot_iron": "a", "ash_hemp": "a", "ash_ward_thread": "b", "soot_whetstone": "c", "ash_cooler": "a"}
THRALLS = ("ash_husk", "elder_thrall")


def wjson(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")


# ---------------------------------------------------------------- 動きの部品 (tools/gen/ch2/gen_ch2_ground_mobs.py と同じ)
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
    return {"%.2f" % t: (rot(*v) if rotation else list(v)) for t, v in frames.items()}


def bone(rotation=None, position=None):
    d = {}
    if rotation:
        d["rotation"] = rotation
    if position:
        d["position"] = position
    return d


LEGS6 = ["leg_l1", "leg_l2", "leg_l3", "leg_r1", "leg_r2", "leg_r3"]


def legs_walk(speed, amp=22, lift=10):
    out = {}
    for i, b in enumerate(LEGS6):
        side = -1 if "_l" in b else 1
        ph = 0 if (i % 2 == 0) == (side < 0) else 180
        out[b] = bone(["0", S(ph, amp, speed), "%d * math.abs(%s)" % (side, S(ph, lift, speed))])
    return out


# ---------------------------------------------------------------- 熾り蟲
def mite():
    p = "animation.cinder_mite."
    idle = {"head": bone(["0", S(0, 8, 120), "0"]), "tail": bone(mrot(S(90, 6, 200)))}
    for b in LEGS6:
        idle[b] = bone(["0", "0", S(hash(b) % 180, 3, 240)])
    walk = legs_walk(1440, 24, 10)
    walk["body"] = bone(position=["0", "math.abs(" + S(0, 0.15, 1440) + ")", "0"])
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.06: (0, 0, 14), 0.2: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.3: (0, 0, 170), 0.6: (0, 0, 180)}), kf({0: (0, 0, 0), 0.3: (0, 1.5, 0), 0.6: (0, 1.0, 0)}, False))}
    for b in LEGS6:
        death[b] = bone(kf({0: (0, 0, 0), 0.4: (0, 0, 40 if "_l" in b else -40)}))
    return {p + "idle": loop(2.0, idle), p + "walk": loop(0.25, walk), p + "hurt": once(0.2, hurt), p + "death": once(0.6, death, True)}


# ---------------------------------------------------------------- 灰ひきずり
def dragger():
    p = "animation.ash_dragger."
    idle = {"head": bone(["0", S(0, 6, 90), "0"]), "tail": bone(["0", S(180, 4, 90), "0"])}
    walk = legs_walk(720, 18, 8)
    walk["body"] = bone(["0", S(0, 3, 720), "0"])
    walk["tail"] = bone(["0", S(90, 8, 360), "0"])
    curled = {"head": bone(rot(80, 0, 0)), "tail": bone(rot(-80, 0, 0))}
    for b in LEGS6:
        curled[b] = bone(rot(0, 0, 60 if "_l" in b else -60))
    curl = {"head": bone(kf({0: (0, 0, 0), 0.3: (80, 0, 0)})), "tail": bone(kf({0: (0, 0, 0), 0.3: (-80, 0, 0)}))}
    for b in LEGS6:
        curl[b] = bone(kf({0: (0, 0, 0), 0.3: (0, 0, 60 if "_l" in b else -60)}))
    rolling = dict(curled)
    rolling["roll"] = bone([neg("query.anim_time * 720"), "0", "0"])
    uncurl = {"head": bone(kf({0: (80, 0, 0), 0.4: (0, 0, 0)})), "tail": bone(kf({0: (-80, 0, 0), 0.4: (0, 0, 0)}))}
    for b in LEGS6:
        uncurl[b] = bone(kf({0: (0, 0, 60 if "_l" in b else -60), 0.4: (0, 0, 0)}))
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (0, 0, 8), 0.3: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.5: (0, 0, 160), 1.0: (0, 0, 180)}), kf({0: (0, 0, 0), 0.5: (0, 3, 0), 1.0: (0, 4.5, 0)}, False))}
    return {p + "idle": loop(3.0, idle), p + "walk": loop(0.5, walk), p + "curl": once(0.3, curl, True), p + "curled": loop(1.0, curled),
            p + "rolling": loop(0.5, rolling), p + "uncurl": once(0.4, uncurl), p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 古い灰の番人 (灰の番人のアニメ + 亀裂 = 火掻きを床へ打ちつける、熱の波 = 殻が割れる)
def elder():
    src = json.load(open(os.path.join(A, "animations", "entity", "ash_thrall.animation.json"), encoding="utf-8"))["animations"]
    out = {}
    for k, v in src.items():
        out[k.replace("animation.ash_thrall.", "animation.elder_thrall.")] = copy.deepcopy(v)
    out["animation.elder_thrall.crack"] = copy.deepcopy(src["animation.ash_thrall.attack"])
    out["animation.elder_thrall.wave"] = copy.deepcopy(src["animation.ash_thrall.shell_break"])
    return out


# ---------------------------------------------------------------- 音 (skyabyss_ch4 の名前空間。既存の音の別名)
def ev(name, pitch, vol):
    return {"name": name if ":" in name else "skyabyss:" + name, "type": "event", "pitch": pitch, "volume": vol}


SOUNDS = {
    "cinder_mite_ambient": [ev("ember_moth_flutter", 1.8, 0.4)],
    "cinder_mite_death": [ev("ember_moth_death", 1.6, 0.6)],
    "ash_dragger_curl": [ev("cinder_roller_curl", 1.4, 0.8)],
    "ash_dragger_roll": [ev("cinder_roller_roll", 1.5, 0.6)],
    "elder_thrall_crack": [ev("ember_crust_crack", 0.7, 1.4)],
    "elder_thrall_burst": [ev("ember_vent_burst", 0.9, 1.4)],
    "elder_thrall_wave": [ev("ember_heart_gale", 1.2, 1.2)],
    "elder_thrall_rake": [ev("ash_smith_inhale", 1.3, 1.0)],
}


def sounds():
    wjson(os.path.join(A4, "sounds.json"), {k: {"subtitle": "subtitles.skyabyss_ch4." + k, "sounds": v} for k, v in SOUNDS.items()})


def install():
    for mob, k in CHOICE.items():
        d = "%s/%s/%s/%s_%s_" % (R, mob, k, mob, k)
        cmd = [sys.executable, os.path.join(ROOT, "tools", "install/install_lowpoly.py"), mob, "--mesh", d + "mesh.json", "--atlas", d + "atlas.png",
               "--glow", d + "glowmask.png"]
        if mob in THRALLS:
            # 骨は灰の番人のもの (geo/entity/ash_thrall.geo.json)。道具は別メッシュ (受け口 socket_main)
            gear = "ash_husk_poker" if mob == "ash_husk" else "elder_rake"
            cmd += ["--geo-name", "ash_thrall", "--gear", d + "poker_mesh.json:" + d + "poker_atlas.png:" + gear + ":socket_main"]
        # --geo-name は灰の番人の骨の geo (ash_thrall_lowpoly*.geo.json) を書き直すので、灰の番人のものを元に戻す (骨は同じ。箱の代用の箱だけ違う)
        keep = {}
        if mob in THRALLS:
            for n in ("ash_thrall_lowpoly.geo.json", "ash_thrall_lowpoly_debug.geo.json"):
                keep[n] = open(os.path.join(A, "geo", "entity", n), "rb").read()
        subprocess.check_call(cmd, cwd=ROOT)
        for n, data in keep.items():
            open(os.path.join(A, "geo", "entity", n), "wb").write(data)
        os.makedirs(os.path.join(A, "textures", "entity"), exist_ok=True)
        if mob in THRALLS:
            # 燃え上がった姿 (同じ UV の別アトラス)。箱の代用は灰の番人の geo と絵
            shutil.copyfile(os.path.join(ROOT, d + "atlas_flared.png"), os.path.join(A, "textures", "lowpoly", mob + "_flared.png"))
            gf = os.path.join(ROOT, d + "glowmask_flared.png")
            if os.path.exists(gf):
                shutil.copyfile(gf, os.path.join(A, "textures", "lowpoly", mob + "_flared_glowmask.png"))
            g = json.load(open(os.path.join(ROOT, "tools", "sources", "geo_cube", "geo", "entity", "ash_thrall.geo.json"), encoding="utf-8"))
            g["minecraft:geometry"][0]["description"]["identifier"] = "geometry." + mob
            wjson(os.path.join(A, "geo", "entity", mob + ".geo.json"), g, indent=None)
            for suffix in ("", "_glowmask", "_flared", "_flared_glowmask"):
                src = os.path.join(A, "textures", "entity", "ash_thrall" + suffix + ".png")
                if os.path.exists(src):
                    shutil.copyfile(src, os.path.join(A, "textures", "entity", mob + suffix + ".png"))
        else:
            shutil.copyfile(os.path.join(A, "geo", "entity", mob + "_lowpoly_debug.geo.json"), os.path.join(A, "geo", "entity", mob + ".geo.json"))
            shutil.copyfile(os.path.join(A, "textures", "lowpoly", mob + ".png"), os.path.join(A, "textures", "entity", mob + ".png"))
            gm = os.path.join(A, "textures", "lowpoly", mob + "_glowmask.png")
            if os.path.exists(gm):
                shutil.copyfile(gm, os.path.join(A, "textures", "entity", mob + "_glowmask.png"))
        wjson(os.path.join(A, "models", "item", mob + "_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})


def items():
    icons = os.path.join(ROOT, R, "items", "icons")
    for item, k in ITEM_CHOICE.items():
        shutil.copyfile(os.path.join(icons, "%s_%s_32.png" % (item, k)), os.path.join(A, "textures", "item", item + ".png"))
        wjson(os.path.join(A, "models", "item", item + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/" + item}})
        src = os.path.join(ROOT, "tools", "sources", item)
        os.makedirs(src, exist_ok=True)
        for o in "abc":
            shutil.copyfile(os.path.join(icons, "%s_%s_32.png" % (item, o)), os.path.join(src, "%s_%s_32.png" % (item, o)))


def main():
    install()
    for mob, fn in (("cinder_mite", mite), ("ash_dragger", dragger), ("elder_thrall", elder)):
        wjson(os.path.join(A, "animations", "entity", mob + ".animation.json"), {"format_version": "1.8.0", "animations": fn()})
    sounds()
    items()
    print("done")


if __name__ == "__main__":
    main()
