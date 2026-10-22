#!/usr/bin/env python3
"""三章の地上の敵 4 種 (帯電毛玉・峰の雛・雷拾い・焼けた羽守) を、選んだラフから入れる (docs/design/21 の 5.1、P16)。

  python -I tools/gen/ch3/gen_ch3_ground_mobs.py

入力: docs/review/ch3_roughs/<id>/<案>/<id>_<案>_{mesh.json,atlas.png,glowmask.png}  (ラフの作り方は tools/rough/lowpoly/ch3/)
      焼けた羽守は鉤槍の別メッシュ <id>_<案>_spear_{mesh.json,atlas.png} も (受け口 socket_main、名前 scorched_glaive)
出力 (すべて src/main/resources/assets/skyabyss/ の下):
  lowpoly/<id>.json, textures/lowpoly/<id>(.png|_glowmask.png), geo/entity/<id>_lowpoly(.geo|_debug.geo).json   (tools/install/install_lowpoly.py)
  geo/entity/<id>.geo.json + textures/entity/<id>.png   LowPoly.enabled = false のときの箱の代用 (デバッグ箱)
  animations/entity/<id>.animation.json                 このファイルで作る (焼けた羽守は羽守の動きを写して技を足す)
  sounds.json の行 (既存の音の出来事 (type event) の別名。末尾の一つの塊)、字幕と名前は assets/skyabyss_ch3mob/lang/
  models/item/<id>_spawn_egg.json

案は CHOICE で選ぶ (選定: 毛玉 B・雛 B・雷拾い C・羽守 C。選び直すときはここを変えて流し直す)。
回転の約束: 小さな三体はラフの姿勢表 = 数学の右手系 (前 -Z。x が + = 前が上がる) で書き、ファイルへは (-x, -y, z)。
焼けた羽守は羽守と同じ骨 (root が Y 180 度) で、羽守の dive と同じ書き方 (ファイルへ (-x, y, -z))。
"""
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss")
CHOICE = {"static_puff": "b", "peak_fledgling": "b", "lightning_gleaner": "c", "scorched_warden": "c"}  # 選定 第 2 期 (2026-10-22)


def wjson(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
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
    out = {}
    for t, v in frames.items():
        out["%.2f" % t] = rot(*v) if rotation else list(v)
    return out


def raw(frames):
    return {"%.2f" % t: list(v) for t, v in frames.items()}


def bone(rotation=None, position=None, scale=None):
    d = {}
    if rotation:
        d["rotation"] = rotation
    if position:
        d["position"] = position
    if scale:
        d["scale"] = scale
    return d


# ---------------------------------------------------------------- 帯電毛玉
TUFT_OPEN = {"tuft_n": (-40, 0, 0), "tuft_s": (40, 0, 0), "tuft_e": (0, 0, -40), "tuft_w": (0, 0, 40)}


def puff():
    p = "animation.static_puff."
    idle = {"body": bone(["0", "query.anim_time * 30", "0"], ["0", S(0, 0.8, 120), "0"])}
    for i, b in enumerate(TUFT_OPEN):
        idle[b] = bone(mrot(S(i * 70, 3, 180)))
    # 帯電 (4 秒。予告): 毛の房が少しずつ逆立ち、体がふくらむ。最後の 0.6 秒は細かく震える
    charge = {"body": bone(None, None, raw({0: (1, 1, 1), 3.4: (1.22, 1.22, 1.22), 4.0: (1.3, 1.3, 1.3)}))}
    for b, v in TUFT_OPEN.items():
        charge[b] = bone(kf({0: (0, 0, 0), 2.0: tuple(x * 0.45 for x in v), 3.4: tuple(x * 0.9 for x in v), 3.7: tuple(x * 1.05 for x in v), 4.0: v}))
    charge["tuft_t"] = bone(None, None, raw({0: (1, 1, 1), 4.0: (1, 1.6, 1)}))
    discharge = {"body": bone(None, None, raw({0: (1.3, 1.3, 1.3), 0.1: (0.85, 0.85, 0.85), 0.3: (1.05, 1.05, 1.05), 0.5: (1, 1, 1)}))}
    for b, v in TUFT_OPEN.items():
        discharge[b] = bone(kf({0: v, 0.1: tuple(x * 1.3 for x in v), 0.5: (0, 0, 0)}))
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (10, 0, 8), 0.3: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 1.0: (0, 180, 0)}), kf({0: (0, 0, 0), 1.0: (0, -6, 0)}, False), raw({0: (1, 1, 1), 1.0: (0.4, 0.4, 0.4)}))}
    return {p + "idle": loop(12.0, idle), p + "walk": loop(12.0, idle), p + "charge": once(4.0, charge, True),
            p + "discharge": once(0.5, discharge), p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 峰の雛
FOLD_L = (0, 80, 10)
FOLD_R = (0, -80, -10)


def fledgling():
    p = "animation.peak_fledgling."
    idle = {"wing_l": bone(rot(*FOLD_L)), "wing_r": bone(rot(*FOLD_R)), "head": bone(mrot(S(0, 6, 120), S(90, 10, 60)))}
    walk = {"wing_l": bone(rot(*FOLD_L)), "wing_r": bone(rot(*FOLD_R)),
            "body": bone(None, ["0", "math.abs(" + S(0, 0.4, 720) + ")", "0"]),
            "leg_l": bone(mrot(S(0, 25, 720))), "leg_r": bone(mrot(S(180, 25, 720))), "head": bone(mrot(S(0, 6, 720)))}
    # 飛ぶ (上がる・狙う): 羽ばたき、脚をたたむ
    fly = {"wing_l": bone(["0", "0", "-(%s - 15)" % S(0, 40, 1080)]), "wing_r": bone(["0", "0", "%s - 15" % S(0, 40, 1080)]),
           "leg_l": bone(rot(40, 0, 0)), "leg_r": bone(rot(40, 0, 0)), "tail": bone(mrot(S(0, 8, 1080)))}
    dive = {"body": bone(kf({0: (0, 0, 0), 0.15: (-40, 0, 0)})), "head": bone(kf({0: (0, 0, 0), 0.15: (20, 0, 0)})),
            "wing_l": bone(kf({0: (0, 0, -30), 0.15: (0, 50, -10)})), "wing_r": bone(kf({0: (0, 0, 30), 0.15: (0, -50, 10)})),
            "leg_l": bone(rot(50, 0, 0)), "leg_r": bone(rot(50, 0, 0)), "tail": bone(rot(14, 0, 0))}
    stagger = {"body": bone(mrot("10", "0", S(0, 14, 360))), "head": bone(mrot("-10", S(0, 25, 540), S(90, 16, 540))),
               "wing_l": bone(rot(0, 40, 30)), "wing_r": bone(rot(0, -40, -30))}
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (12, 0, 6), 0.3: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.6: (0, 0, 80), 1.0: (0, 0, 90)}), kf({0: (0, 0, 0), 1.0: (0, -1.5, 0)}, False)),
             "wing_l": bone(kf({0: FOLD_L, 0.6: (0, 20, 40)})), "wing_r": bone(kf({0: FOLD_R, 0.6: (0, -20, -40)}))}
    return {p + "idle": loop(6.0, idle), p + "walk": loop(0.5, walk), p + "fly": loop(1 / 3, fly), p + "dive": once(0.15, dive, True),
            p + "stagger": loop(1.0, stagger), p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 雷拾い
def gleaner():
    p = "animation.lightning_gleaner."
    idle = {"neck": bone(mrot(S(0, 4, 90), S(90, 6, 45))), "tail": bone(mrot(S(0, 4, 120)))}
    walk = {"leg_l": bone(mrot(S(0, 22, 540))), "leg_r": bone(mrot(S(180, 22, 540))), "neck": bone(mrot(S(90, 8, 1080))),
            "body": bone(None, ["0", "math.abs(" + S(0, 0.5, 540) + ")", "0"])}
    peck = {"neck": bone(kf({0: (0, 0, 0), 0.3: (-60, 0, 0), 0.45: (-72, 0, 0), 0.6: (-60, 0, 0), 0.75: (-72, 0, 0), 1.2: (0, 0, 0)})),
            "head": bone(kf({0: (0, 0, 0), 0.3: (-30, 0, 0), 1.2: (0, 0, 0)})), "body": bone(kf({0: (0, 0, 0), 0.3: (-12, 0, 0), 1.2: (0, 0, 0)}))}
    flap = {"wing_l": bone(kf({0: (0, 0, 0), 0.15: (0, 0, -70), 0.3: (0, 0, -10), 0.45: (0, 0, -70), 0.6: (0, 0, 0)})),
            "wing_r": bone(kf({0: (0, 0, 0), 0.15: (0, 0, 70), 0.3: (0, 0, 10), 0.45: (0, 0, 70), 0.6: (0, 0, 0)}))}
    flee = {"leg_l": bone(mrot(S(0, 35, 1080))), "leg_r": bone(mrot(S(180, 35, 1080))), "neck": bone(rot(18, 0, 0)), "body": bone(rot(-10, 0, 0)),
            "wing_l": bone(["0", "0", "-(%s - 30)" % S(0, 30, 1080)]), "wing_r": bone(["0", "0", "%s - 30" % S(0, 30, 1080)])}
    packless = {"pack": bone(None, None, ["0", "0", "0"])}
    hurt = {"body": bone(kf({0: (0, 0, 0), 0.08: (8, 0, 6), 0.3: (0, 0, 0)}))}
    death = {"body": bone(kf({0: (0, 0, 0), 0.7: (0, 0, 85), 1.0: (0, 0, 90)}), kf({0: (0, 0, 0), 1.0: (0, -9, 0)}, False)),
             "neck": bone(kf({0: (0, 0, 0), 0.8: (-30, 0, 20)}))}
    return {p + "idle": loop(8.0, idle), p + "walk": loop(2 / 3, walk), p + "peck": once(1.2, peck), p + "flap": once(0.6, flap),
            p + "flee": loop(1 / 3, flee), p + "packless": loop(1.0, packless), p + "hurt": once(0.3, hurt), p + "death": once(1.0, death, True)}


# ---------------------------------------------------------------- 焼けた羽守 (羽守の動きを写し、雷の輪・外套の跳躍・柱の雷を受けた身震いを足す)
def grot(v):
    x, y, z = v
    return [-x, y, -z]


def warden():
    p = "animation.scorched_warden."
    src = json.load(open(os.path.join(A, "animations", "entity", "feather_warden.animation.json"), encoding="utf-8"))["animations"]
    out = {}
    for k, v in src.items():
        out[k.replace("feather_warden", "scorched_warden")] = v
    # ラフの姿勢 (tools/rough/lowpoly/ch3/scorched_warden.py の RING / LEAP) と同じ値
    RING = {"body": (-12, 0, 0), "arm_r": (-40, 0, 30), "arm_l": (-40, 0, -30), "cloak_l": (0, 0, -40), "cloak_r": (0, 0, 40),
            "leg_r": (20, 0, -8), "leg_l": (20, 0, 8), "socket_main": (-60, 0, 0)}
    LEAP = {"body": (10, 0, 0), "cloak_l": (0, 0, -95), "cloak_r": (0, 0, 95), "arm_r": (-150, 0, 0), "arm_l": (-30, 0, -40),
            "leg_r": (-30, 0, 0), "leg_l": (-20, 0, 0), "head": (-14, 0, 0)}
    Z = (0, 0, 0)
    ring = {}
    for b, v in RING.items():
        ring[b] = {"rotation": {"0.00": [0, 0, 0], "0.40": grot(v), "1.20": grot(tuple(x * 1.1 for x in v)), "1.40": grot(tuple(x * 0.6 for x in v)),
                                "1.70": grot(Z)}}
    ring["body"]["position"] = {"0.00": [0, 0, 0], "0.40": [0, -2.5, 0], "1.20": [0, -3.0, 0], "1.30": [0, -1.0, 0], "1.70": [0, 0, 0]}
    leap = {}
    for b, v in LEAP.items():
        leap[b] = {"rotation": {"0.00": [0, 0, 0], "0.25": grot(v), "0.95": grot(v), "1.20": grot(tuple(x * 0.2 for x in v))}}
    struck = {"body": {"rotation": {"0.00": [0, 0, 0], "0.08": [-14, 6, 0], "0.25": [6, -4, 0], "0.60": [0, 0, 0]}},
              "head": {"rotation": {"0.00": [0, 0, 0], "0.08": [20, 0, 0], "0.60": [0, 0, 0]}}}
    out[p + "ring"] = {"animation_length": 1.7, "loop": "hold_on_last_frame", "bones": ring}
    out[p + "leap"] = {"animation_length": 1.2, "loop": "hold_on_last_frame", "bones": leap}
    out[p + "struck"] = {"animation_length": 0.6, "bones": struck}
    return out


# ---------------------------------------------------------------- 音 (既存の音の出来事の別名)
def ev(name, pitch, vol):
    return {"name": name if ":" in name else "skyabyss:" + name, "type": "event", "pitch": pitch, "volume": vol}


SOUNDS = {
    "static_puff_ambient": [ev("static_moth_ambient", 1.6, 0.4)],
    "static_puff_charge": [ev("static_moth_charge", 1.3, 0.9)],
    "static_puff_discharge": [ev("static_moth_discharge", 1.4, 1.0)],
    "static_puff_hurt": [ev("minecraft:block.wool.hit", 1.2, 0.9)],
    "static_puff_death": [ev("static_moth_burst", 1.5, 0.8)],
    "peak_fledgling_ambient": [ev("minecraft:entity.parrot.ambient", 0.8, 0.6)],
    "peak_fledgling_cry": [ev("minecraft:entity.parrot.fly", 0.9, 1.0)],
    "peak_fledgling_dive": [ev("feather_warden_dive", 1.6, 1.0)],
    "peak_fledgling_thud": [ev("minecraft:block.stone.hit", 0.7, 1.2)],
    "peak_fledgling_hurt": [ev("minecraft:entity.parrot.hurt", 0.8, 0.9)],
    "peak_fledgling_death": [ev("minecraft:entity.parrot.death", 0.8, 0.9)],
    "lightning_gleaner_ambient": [ev("minecraft:entity.parrot.ambient", 0.6, 0.5)],
    "lightning_gleaner_flap": [ev("minecraft:entity.parrot.fly", 0.7, 1.0)],
    "lightning_gleaner_drop": [ev("minecraft:block.chain.fall", 1.4, 0.9)],
    "lightning_gleaner_hurt": [ev("minecraft:entity.parrot.hurt", 0.6, 0.9)],
    "lightning_gleaner_death": [ev("minecraft:entity.parrot.death", 0.6, 0.9)],
    "lightning_gleaner_step": [ev("minecraft:block.copper.step", 1.4, 0.3)],
    "scorched_warden_ambient": [ev("feather_warden_ambient", 0.8, 1.0)],
    "scorched_warden_hurt": [ev("feather_warden_hurt", 0.85, 1.0)],
    "scorched_warden_death": [ev("feather_warden_death", 0.75, 1.0)],
    "scorched_warden_ring": [ev("static_moth_charge", 0.7, 1.3)],
    "scorched_warden_ring_burst": [ev("minecraft:entity.lightning_bolt.impact", 1.2, 1.0)],
    "scorched_warden_leap": [ev("feather_warden_crouch", 0.9, 1.2)],
    "scorched_warden_land": [ev("feather_warden_land", 0.8, 1.3)],
    "scorched_warden_struck": [ev("minecraft:entity.lightning_bolt.thunder", 1.6, 0.8)],
}
SUBTITLES = {
    "static_puff_ambient": ("Static crackles", "静電がはぜる"), "static_puff_charge": ("Fluff bristles", "毛が逆立つ"),
    "static_puff_discharge": ("Static discharges", "毛玉が放電する"), "static_puff_hurt": ("Fluff tears", "綿がちぎれる"),
    "static_puff_death": ("Static puff bursts", "帯電毛玉が弾ける"),
    "peak_fledgling_ambient": ("Fledgling chirps", "雛が鳴く"), "peak_fledgling_cry": ("Fledgling takes off", "雛が飛び立つ"),
    "peak_fledgling_dive": ("Fledgling dives", "雛が急降下する"), "peak_fledgling_thud": ("Fledgling hits the rock", "雛が岩にぶつかる"),
    "peak_fledgling_hurt": ("Fledgling squawks", "雛が鳴く"), "peak_fledgling_death": ("Fledgling falls", "峰の雛が倒れる"),
    "lightning_gleaner_ambient": ("Gleaner croaks", "雷拾いが鳴く"), "lightning_gleaner_flap": ("Wings flap", "羽ばたく"),
    "lightning_gleaner_drop": ("Wire clatters", "銅線が落ちる"), "lightning_gleaner_hurt": ("Gleaner cries", "雷拾いが鳴く"),
    "lightning_gleaner_death": ("Gleaner falls", "雷拾いが倒れる"), "lightning_gleaner_step": ("Copper legs tap", "銅の脚が鳴る"),
    "scorched_warden_ambient": ("Burnt feathers rustle", "焼けた羽が擦れる"), "scorched_warden_hurt": ("Scorched warden groans", "焼けた羽守がうめく"),
    "scorched_warden_death": ("Scorched warden falls", "焼けた羽守が倒れる"), "scorched_warden_ring": ("Lightning gathers", "雷が足もとに集まる"),
    "scorched_warden_ring_burst": ("Lightning ring bursts", "雷の輪が弾ける"), "scorched_warden_leap": ("Cloak spreads", "外套が広がる"),
    "scorched_warden_land": ("Heavy landing", "重く降り立つ"), "scorched_warden_struck": ("The pillar strikes the warden", "柱の雷が羽守を打つ"),
}
LANG = {
    "entity.skyabyss.static_puff": ("Static Puff", "帯電毛玉"),
    "entity.skyabyss.peak_fledgling": ("Peak Fledgling", "峰の雛"),
    "entity.skyabyss.lightning_gleaner": ("Lightning Gleaner", "雷拾い"),
    "entity.skyabyss.scorched_warden": ("Scorched Warden", "焼けた羽守"),
    "item.skyabyss.static_puff_spawn_egg": ("Static Puff Spawn Egg", "帯電毛玉のスポーンエッグ"),
    "item.skyabyss.peak_fledgling_spawn_egg": ("Peak Fledgling Spawn Egg", "峰の雛のスポーンエッグ"),
    "item.skyabyss.lightning_gleaner_spawn_egg": ("Lightning Gleaner Spawn Egg", "雷拾いのスポーンエッグ"),
    "item.skyabyss.scorched_warden_spawn_egg": ("Scorched Warden Spawn Egg", "焼けた羽守のスポーンエッグ"),
}

NL = chr(10)


def sounds():
    p = os.path.join(A, "sounds.json")
    # ほかの作業とぶつからないよう、ファイルの他の行は触らず、末尾に一つの塊として足す (流し直しは塊を置き換える)
    text = open(p, encoding="utf-8").read()
    data = json.loads(text)
    first = next(iter(SOUNDS))
    if first in data:
        start = text.index('"%s"' % first)
        text = text[:text.rfind(",", 0, start)] + NL + "}" + NL
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
    for i, code in enumerate(("en_us", "ja_jp")):
        d = {}
        for key, texts in LANG.items():
            d[key] = texts[i]
        for key, texts in SUBTITLES.items():
            d["subtitles.skyabyss." + key] = texts[i]
        wjson(os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss_ch3mob", "lang", code + ".json"), d)


def install():
    for mob, k in CHOICE.items():
        d = "docs/review/ch3_roughs/%s/%s/%s_%s_" % (mob, k, mob, k)
        cmd = [sys.executable, os.path.join(ROOT, "tools", "install/install_lowpoly.py"), mob, "--mesh", d + "mesh.json", "--atlas", d + "atlas.png",
               "--glow", d + "glowmask.png"]
        if mob == "scorched_warden":
            cmd += ["--gear", d + "spear_mesh.json:" + d + "spear_atlas.png:scorched_glaive:socket_main"]
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
    for mob, fn in (("static_puff", puff), ("peak_fledgling", fledgling), ("lightning_gleaner", gleaner), ("scorched_warden", warden)):
        wjson(os.path.join(A, "animations", "entity", mob + ".animation.json"), {"format_version": "1.8.0", "animations": fn()})
    sounds()
    lang()
    print("done")


if __name__ == "__main__":
    main()
