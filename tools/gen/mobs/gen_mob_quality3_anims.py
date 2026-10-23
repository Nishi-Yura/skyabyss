#!/usr/bin/env python3
"""敵の見直し (三回目、docs/review/mob_quality_2026-10-23.md の 17・18・21) で足す動き。既存のアニメのファイルに足すだけ (ほかは触らない)。

  python -I tools/gen/mobs/gen_mob_quality3_anims.py

  水路の顎 waterway_maw: 被弾 hurt (体が跳ねて節が波打つ・顎が開く) と倒れる death (腹を上にして回り、沈みながら丸まる)。
                         GuardMob ではないので WaterwayMaw.java が自分で流す。倒れるアニメがあるので LowPolyGeoRenderer はバニラの横倒しをしない
  熾り蟲 cinder_mite:    攻撃 attack。触れて傷 (CinderMite.aiStep の doHurtTarget、1 秒に一度) と同じ tick に流す。当たりは即時なので
                         0.06 秒で頭を噛み下ろし、胴が前へ跳ねる (溜めは付けない。当たりの瞬間と動きの山をそろえる)
  骨負い bone_hauler:    攻撃はしない (追いかけっこの獣)。代わりに、人を見て逃げ出す瞬間 (FleeGoal.start) に「驚く」startle
                         (前脚を上げて立ち上がり、耳が立って籠が揺れ、走り出す)
向き (ゲームで確かめた値。tools/gen/mobs/gen_guard_hurt_death.py と同じ): ファイルの x の + で、立つ部品は前へ倒れ、垂れた部品 (脚) は後ろへ振れ、
前へ伸びた部品 (頭) は先が下がる。z は長い軸のまわりの回転 (魚の節は z の 180 で腹が上)。
WaterwayMaw・CinderMite・BoneHauler の生成スクリプト (gen_mobs.py・gen_ch4_ground_mobs.py) でアニメを書き出し直したら、これをもう一度流す。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
ANIM = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "animations", "entity")
Z = [0, 0, 0]


def kf(points):
    return {"%g" % t: {"vector": [round(x, 2) for x in v]} for t, v in sorted(points.items())}


def b(r=None, p=None, s=None):
    d = {}
    if r:
        d["rotation"] = kf(r)
    if p:
        d["position"] = kf(p)
    if s:
        d["scale"] = kf(s)
    return d


def maw():
    """水路の顎: 頭 (seg0 の子) から尾 (seg5 > tail) へ節がつながる長い魚。"""
    segs = ["seg1", "seg2", "seg3", "seg4", "seg5", "tail"]
    hurt = {"seg0": b({0: Z, 0.06: [-6, 10, 4], 0.35: Z}, {0: Z, 0.06: [0, 0.8, 1.2], 0.35: Z}),
            "head": b({0: Z, 0.06: [-10, 0, 0], 0.35: Z}),
            "jaw": b({0: Z, 0.06: [-24, 0, 0], 0.2: [-10, 0, 0], 0.35: Z}),
            "lure": b({0: Z, 0.06: [-30, 0, 12], 0.18: [18, 0, -8], 0.35: Z})}
    for i, n in enumerate(segs):
        a = (-1) ** i * (8 + 2 * i)
        hurt[n] = b({0: Z, 0.06 + 0.02 * i: [0, a, 0], 0.18 + 0.02 * i: [0, -a * 0.5, 0], 0.35: Z})
    death = {"seg0": b({0: Z, 0.3: [-10, 20, 40], 0.9: [6, 30, 165], 1.6: [10, 34, 178]},
                       {0: Z, 0.3: [0, 1.0, 0], 0.9: [0, -2.0, 0], 1.6: [0, -5.0, 0]}),
             "head": b({0: Z, 0.3: [-16, 0, 0], 1.6: [8, 0, 0]}),
             "jaw": b({0: Z, 0.3: [-34, 0, 0], 0.9: [-26, 0, 0], 1.6: [-20, 0, 0]}),
             "lure": b({0: Z, 0.3: [-40, 0, 0], 0.9: [50, 0, 20], 1.6: [70, 0, 24]})}
    for i, n in enumerate(segs):
        death[n] = b({0: Z, 0.3: [0, (-1) ** i * 18, 0], 0.9: [6, 14 + 2 * i, 0], 1.6: [8, 12 + 3 * i, 0]})
    return {"hurt": (0.35, hurt, False), "death": (1.6, death, True)}


def mite():
    """熾り蟲: 胴 (body) > 頭・尾・六本の脚。当たりは流し始めの tick。"""
    atk = {"body": b({0: Z, 0.06: [12, 0, 0], 0.14: [8, 0, 0], 0.3: Z}, {0: Z, 0.06: [0, 0.2, -0.9], 0.14: [0, 0.1, -0.6], 0.3: Z}),
           "head": b({0: [-14, 0, 0], 0.06: [24, 0, 0], 0.14: [18, 0, 0], 0.3: Z}),
           "tail": b({0: Z, 0.06: [26, 0, 0], 0.3: Z}),
           "leg_l1": b({0: Z, 0.06: [0, -20, -18], 0.3: Z}), "leg_r1": b({0: Z, 0.06: [0, 20, 18], 0.3: Z}),
           "leg_l3": b({0: Z, 0.06: [0, 14, 8], 0.3: Z}), "leg_r3": b({0: Z, 0.06: [0, -14, -8], 0.3: Z})}
    return {"attack": (0.3, atk, False)}


def hauler():
    """骨負い: 胴 > 頭 (耳)・籠・尾・四本の脚。驚いて前脚を上げ、着いて走り出す。"""
    st = {"body": b({0: Z, 0.15: [-16, 0, 0], 0.3: [5, 0, 0], 0.5: Z}, {0: Z, 0.15: [0, 1.2, 0.6], 0.3: [0, -0.3, -0.4], 0.5: Z}),
          "head": b({0: Z, 0.15: [-24, 0, 0], 0.3: [6, 0, 0], 0.5: Z}),
          "ear_l": b({0: Z, 0.1: [-30, 0, -10], 0.5: Z}), "ear_r": b({0: Z, 0.1: [-30, 0, 10], 0.5: Z}),
          "basket": b({0: Z, 0.15: [8, 0, 0], 0.3: [-10, 0, 3], 0.42: [4, 0, -2], 0.5: Z}),
          "tail": b({0: Z, 0.15: [30, 0, 0], 0.5: Z}),
          "leg_fl": b({0: Z, 0.15: [-40, 0, 0], 0.3: [8, 0, 0], 0.5: Z}), "leg_fr": b({0: Z, 0.15: [-34, 0, 0], 0.3: [6, 0, 0], 0.5: Z}),
          "leg_bl": b({0: Z, 0.15: [14, 0, 0], 0.5: Z}), "leg_br": b({0: Z, 0.15: [14, 0, 0], 0.5: Z})}
    return {"startle": (0.5, st, False)}


MOBS = {"waterway_maw": maw, "cinder_mite": mite, "bone_hauler": hauler}


def main():
    for mob, fn in MOBS.items():
        p = os.path.join(ANIM, mob + ".animation.json")
        raw = open(p, "rb").read().decode("utf-8")
        nl = "\r\n" if "\r\n" in raw else "\n"
        doc = json.loads(raw)
        a = doc["animations"]
        for name, (length, bones, hold) in fn().items():
            e = {"loop": "hold_on_last_frame" if hold else False, "animation_length": length, "bones": bones}
            a["animation.%s.%s" % (mob, name)] = e
        text = json.dumps(doc, ensure_ascii=False, indent=2) + "\n"
        open(p, "wb").write(text.replace("\n", nl).encode("utf-8"))
        print(mob, sorted(k.rsplit(".", 1)[1] for k in a))


if __name__ == "__main__":
    main()
