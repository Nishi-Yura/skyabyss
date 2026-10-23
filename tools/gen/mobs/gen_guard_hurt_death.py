#!/usr/bin/env python3
"""鯨の背の番兵たち (骨の番兵・骨棘の射手・老番兵・番兵長・骨の砲手・骨鳥) に被弾 (hurt) と倒れる (death) の動きを足す。

  python -I tools/gen/mobs/gen_guard_hurt_death.py

前はどれも被弾・倒れるのアニメが無く、倒れるときはバニラの横倒し (90 度) だけだった (docs/review/mob_quality_2026-10-23.md)。
既存のアニメのファイルを読み、hurt と death だけを足す (ほかのアニメは触らない)。流すのは GuardMob.autoHurtDeath()
(各モブの Java で true)。倒れるアニメがあるので LowPolyGeoRenderer はバニラの横倒しをしない。
向き (ゲームで確かめた値): ファイルの x の + で、立つ部品 (胴・頭) は前へ倒れ、垂れた部品 (腕・脚) は後ろへ振れ、
前へ伸びた部品 (鳥の胴・砲身) は先が下がる。骨棘の射手は root が y に 180 度回っているので、x と z を反転して書く。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
ANIM = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "animations", "entity")
Z = [0, 0, 0]


def kf(points, flip=False):
    out = {}
    for t, v in sorted(points.items()):
        v = list(v)
        if flip:
            v = [-v[0], v[1], -v[2]]
        out["%g" % t] = {"vector": [round(x, 2) for x in v]}
    return out


def b(r=None, p=None, flip=False):
    d = {}
    if r:
        d["rotation"] = kf(r, flip)
    if p:
        d["position"] = kf({t: ([-v[0], v[1], -v[2]] if flip else v) for t, v in p.items()})
    return d


def humanoid(names, scale=1.0, flip=False, length=1.3):
    """人型 (脚は胴の子)。被弾: 胴が後ろへ揺れる。倒れる: 膝が抜けて沈み、前へ倒れて伏せる (脚は胴とそろって後ろへ伸びる)。"""
    body, head, arm_l, fore_l, arm_r, fore_r, leg_l, leg_r = names
    s = scale
    hurt = {
        body: b({0: Z, 0.07: [-9, 0, 5], 0.3: Z}, {0: Z, 0.07: [0, 0, 0.8 * s], 0.3: Z}, flip),
        head: b({0: Z, 0.07: [-14, 0, -7], 0.3: Z}, flip=flip),
        arm_l: b({0: Z, 0.07: [-14, 0, -8], 0.3: Z}, flip=flip),
        arm_r: b({0: Z, 0.07: [-14, 0, 8], 0.3: Z}, flip=flip),
    }
    L = length
    death = {
        body: b({0: Z, 0.2: [-8, 0, 3], 0.5: [12, 0, -4], 0.95: [80, 0, -6], L: [86, 0, -6]},
                {0: Z, 0.2: [0, 0.3 * s, 0], 0.5: [0, -3 * s, 0], 0.95: [0, -9.5 * s, 0], L: [0, -10 * s, 0]}, flip),
        head: b({0: Z, 0.2: [-18, 0, 0], 0.5: [10, 0, 8], L: [24, 14, 16]}, flip=flip),
        leg_l: b({0: Z, 0.5: [-38, 0, -4], 0.95: [-8, 0, -6], L: [-6, 0, -6]}, flip=flip),
        leg_r: b({0: Z, 0.5: [-22, 0, 4], 0.95: [-4, 0, 6], L: [-2, 0, 6]}, flip=flip),
        arm_l: b({0: Z, 0.2: [-20, 0, -12], 0.95: [-60, 0, -26], L: [-64, 0, -30]}, flip=flip),
        arm_r: b({0: Z, 0.2: [-20, 0, 12], 0.95: [-56, 0, 30], L: [-60, 0, 34]}, flip=flip),
    }
    if fore_l:
        death[fore_l] = b({0: Z, 0.95: [-20, 0, 0], L: [-24, 0, 0]}, flip=flip)
    if fore_r:
        death[fore_r] = b({0: Z, 0.95: [-30, 0, 0], L: [-34, 0, 0]}, flip=flip)
    return hurt, death, L


def captain():
    """番兵長 (中ボス。脚は胴の子でない)。倒れる: 頭を反らせて吠えるように 0.4 秒、膝をつき、前へ崩れる (2 秒)。"""
    hurt = {"body": b({0: Z, 0.08: [-6, 0, 4], 0.35: Z}, {0: Z, 0.08: [0, 0, 1], 0.35: Z}),
            "head": b({0: Z, 0.08: [-12, 0, -6], 0.35: Z}),
            "armR": b({0: Z, 0.08: [-10, 0, 6], 0.35: Z}), "armL": b({0: Z, 0.08: [-10, 0, -6], 0.35: Z})}
    death = {
        "head": b({0: Z, 0.4: [-28, 0, 0], 0.9: [8, 0, 6], 1.6: [20, 10, 14], 2.0: [22, 12, 16]}),
        "body": b({0: Z, 0.4: [-10, 0, 0], 0.9: [14, 0, -3], 1.6: [78, 0, -6], 2.0: [82, 0, -6]},
                  {0: Z, 0.4: [0, 0.6, 0], 0.9: [0, -6, 0], 1.6: [0, -14, -6], 2.0: [0, -15, -6]}),
        "legL": b({0: Z, 0.9: [-62, 0, -6], 1.6: [-84, 0, -8], 2.0: [-86, 0, -8]}, {0: Z, 0.9: [0, -5, 0], 1.6: [0, -9, 0], 2.0: [0, -9.5, 0]}),
        "legR": b({0: Z, 0.9: [-70, 0, 6], 1.6: [-86, 0, 8], 2.0: [-88, 0, 8]}, {0: Z, 0.9: [0, -5, 0], 1.6: [0, -9, 0], 2.0: [0, -9.5, 0]}),
        "armR": b({0: Z, 0.4: [-40, 0, 30], 0.9: [-10, 0, 10], 1.6: [-70, 0, 24], 2.0: [-74, 0, 26]}),
        "armL": b({0: Z, 0.4: [-40, 0, -30], 0.9: [-10, 0, -10], 1.6: [-64, 0, -28], 2.0: [-68, 0, -30]}),
        "forearmR": b({0: Z, 0.4: [-50, 0, 0], 1.6: [-20, 0, 0], 2.0: [-22, 0, 0]}),
        "forearmL": b({0: Z, 0.4: [-50, 0, 0], 1.6: [-24, 0, 0], 2.0: [-26, 0, 0]}),
    }
    return hurt, death, 2.0


def mortar():
    """骨の砲台: 被弾で砲身が跳ね、倒れるときは砲身が前へ垂れ、盾が前へ倒れ、胴が傾いて沈む。"""
    hurt = {"cannon": b({0: Z, 0.07: [-10, 0, 4], 0.3: Z}), "body": b({0: Z, 0.07: [0, 0, 3], 0.3: Z}, {0: Z, 0.07: [0, -0.3, 0], 0.3: Z}),
            "head": b({0: Z, 0.07: [-16, 0, 0], 0.3: Z})}
    death = {"cannon": b({0: Z, 0.3: [-14, 0, 0], 0.8: [52, 0, 10], 1.2: [56, 0, 12]}),
             "shield": b({0: Z, 0.4: [10, 0, 0], 0.9: [76, 0, 4], 1.2: [80, 0, 4]}),
             "head": b({0: Z, 0.3: [-20, 0, 0], 1.2: [30, 0, 24]}),
             "body": b({0: Z, 0.5: [0, 0, 6], 1.2: [4, 0, 10]}, {0: Z, 0.5: [0, -0.6, 0], 1.2: [0, -1.6, 0]})}
    return hurt, death, 1.2


def gull():
    """骨の鴎: 被弾で体がよじれ、倒れるときは翼をたたみ、きりもみで頭から落ちる。"""
    hurt = {"body": b({0: Z, 0.06: [-10, 0, 20], 0.3: Z}), "head": b({0: Z, 0.06: [-16, 0, 0], 0.3: Z}),
            "wing_l": b({0: Z, 0.06: [0, 0, 24], 0.3: Z}), "wing_r": b({0: Z, 0.06: [0, 0, -24], 0.3: Z})}
    death = {"body": b({0: Z, 0.25: [-16, 0, 40], 0.7: [36, 0, 150], 1.0: [40, 0, 176]}, {0: Z, 0.25: [0, 0.5, 0], 1.0: [0, -1.6, 0]}),
             "wing_l": b({0: Z, 0.25: [0, 0, -50], 1.0: [0, 30, 70]}), "wing_r": b({0: Z, 0.25: [0, 0, 50], 1.0: [0, -30, -70]}),
             "head": b({0: Z, 0.25: [-20, 0, 0], 1.0: [24, 0, 10]}), "jaw": b({0: Z, 0.25: [26, 0, 0], 1.0: [30, 0, 0]}),
             "tail": b({0: Z, 1.0: [-20, 0, 0]})}
    return hurt, death, 1.0


MOBS = {
    "bone_sentinel": lambda: humanoid(("body", "head", "left_arm", "left_forearm", "right_arm", "right_forearm", "leg_l", "leg_r")),
    "elder_sentinel": lambda: humanoid(("body", "head", "left_arm", "left_forearm", "right_arm", "right_forearm", "leg_l", "leg_r"), scale=1.3, length=1.5),
    "rib_archer": lambda: humanoid(("spine", "skull", "arm_l", "forearm_l", "arm_r", "forearm_r", "leg_l", "leg_r"), flip=True),
    "whalebone_captain": captain,
    "bone_mortar": mortar,
    "bone_gull": gull,
}


def main():
    for mob, fn in MOBS.items():
        p = os.path.join(ANIM, mob + ".animation.json")
        raw = open(p, "rb").read().decode("utf-8")
        nl = "\r\n" if "\r\n" in raw else "\n"
        doc = json.loads(raw)
        hurt, death, L = fn()
        a = doc["animations"]
        a["animation.%s.hurt" % mob] = {"loop": False, "animation_length": 0.35, "bones": hurt}
        a["animation.%s.death" % mob] = {"loop": False, "animation_length": L, "bones": death}
        text = json.dumps(doc, ensure_ascii=False, indent=2) + "\n"
        open(p, "wb").write(text.replace("\n", nl).encode("utf-8"))
        print(mob, len(a))


if __name__ == "__main__":
    main()
