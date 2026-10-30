# -*- coding: utf-8 -*-
"""羽の番人 (plume_warden) 低ポリ 一品一案。約束の島の敵 (docs/design/50 の 4.2.4)。
巡礼者の先触れに仕える番兵の抜け殻。背の高い (2.3 ブロック) 淡い金の鎧に、肩から脛まで白い羽の外套。
兜は顔のない丸い面に横の切れ目 (朝の光)、頂きに白い羽の飾り。左腕に三日月の盾、右手に長い槍。
槍を下げて構え (地面に光の矢印の予告)、まっすぐ突っ込んで人を雲の縁へ押し出す。
骨: root > body > head / arm_l > shield / arm_r > lance / mantle / leg_l / leg_r。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 17, 0)), "head": ("body", (0, 30, -0.4)),
         "arm_l": ("body", (-5.2, 28, 0)), "arm_r": ("body", (5.2, 28, 0)), "shield": ("arm_l", (-7.0, 19.5, -2.4)),
         "lance": ("arm_r", (6.6, 18.6, -2.6)), "mantle": ("body", (0, 29, 2.4)),
         "leg_l": ("body", (-2.0, 17, 0)), "leg_r": ("body", (2.0, 17, 0))}
PAL = make_pal({
    "gold": (GOLDW, 0.66, dict(crack=0.04, noise=0.14)),
    "plate": (GOLDW, 0.52, dict(crack=0.06, noise=0.18)),
    "white": (CLOUDW, 0.6, dict(crack=0.0, noise=0.12)),
    "mist": (CLOUDW, 0.46, dict(crack=0.0, noise=0.2)),
    "under": (ROBE, 0.5, dict(crack=0.02, noise=0.16)),
    "wood": (ROPE, 0.55, dict(crack=0.04, noise=0.16)),
    "hollow": (HOLLOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["dawn"])


def build(k):
    F = []
    # 胴: 下着 (暗い布) の上に淡い金の胸当てと腰の板
    torso = [ring(15.5, 3.0, -2.3, 2.3, n=10), ring(20.0, 3.2, -2.4, 2.3, n=10), ring(25.0, 4.0, -2.8, 2.4, n=10, zc=-0.2),
             ring(29.0, 4.6, -2.8, 2.4, n=10, zc=-0.3), ring(31.0, 2.4, -1.6, 1.6, n=10)]
    F += part("torso", loft("body", "under", torso, caps=("under", "under")))
    cuirass = [ring(21.0, 3.5, -2.9, 2.6, n=10), ring(25.0, 4.3, -3.2, 2.7, n=10, zc=-0.2), ring(29.2, 4.9, -3.1, 2.7, n=10, zc=-0.3),
               ring(30.6, 3.6, -2.4, 2.2, n=10)]
    F += part("cuirass", loft("body", "gold", cuirass, caps=("gold", "gold")))
    F += part("sun", ellipsoid("body", "dawn", (0, 26.2, -3.3), (0.9, 0.9, 0.3), segs=6, rings=2))
    # 腰の板 (前と横に垂れる)
    for i, a in enumerate((-50, -15, 15, 50)):
        r = math.radians(a)
        x, z = math.sin(r) * 3.2, -math.cos(r) * 2.8
        F += part(f"tasset{i}", tube("body", "plate", [(x, 20.0, z), (x * 1.15, 15.0, z * 1.15)], ra=0.35, rb=[1.4, 1.6], sides=4,
                                     hint=(math.sin(r), 0, -math.cos(r)), caps=(True, True)))
    # 脚 (脛当て)
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"thigh{s}", tube(b, "under", [(s * 2.0, 17, 0), (s * 2.2, 9.0, 0)], ra=[1.5, 1.2], sides=6, hint=(1, 0, 0)))
        F += part(f"greave{s}", tube(b, "plate", [(s * 2.2, 9.6, -0.2), (s * 2.3, 1.6, -0.2)], ra=[1.35, 1.2], sides=6, hint=(1, 0, 0), caps=(True, True)))
        F += part(f"knee{s}", ellipsoid(b, "gold", (s * 2.2, 9.4, -1.2), (1.1, 1.0, 0.8), segs=6, rings=2))
        F += part(f"boot{s}", ellipsoid(b, "gold", (s * 2.3, 0.9, -0.8), (1.3, 0.9, 2.0), segs=6, rings=2))
    # 肩当てと腕
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        F += part(f"pauld{s}", ellipsoid(b, "gold", (s * 5.4, 28.6, 0), (2.2, 1.7, 2.4), segs=8, rings=3))
        F += part(f"up{s}", tube(b, "under", [(s * 5.4, 27.4, 0), (s * 6.4, 22.4, -0.8)], ra=[1.2, 1.0], sides=6, hint=(1, 0, 0)))
        F += part(f"vam{s}", tube(b, "plate", [(s * 6.4, 23.0, -0.8), (s * 6.8, 18.8, -2.4)], ra=[1.15, 1.0], sides=6, hint=(1, 0, 0), caps=(True, True)))
        F += part(f"hand{s}", ellipsoid(b, "gold", (s * 6.8, 18.6, -2.6), (1.0, 1.1, 1.0), segs=6, rings=2))
    # 兜: 丸い面 (顔は無い)、横の切れ目に朝の光、頂きの羽の飾り
    F += part("neck", tube("head", "under", [(0, 30.2, -0.3), (0, 32.0, -0.4)], ra=1.2, sides=6, hint=(1, 0, 0)))
    helm = [ring(31.4, 1.9, -2.0, 1.9, n=12), ring(33.4, 2.4, -2.5, 2.3, n=12), ring(35.8, 2.3, -2.4, 2.2, n=12), ring(37.6, 1.6, -1.6, 1.6, n=12),
            ring(38.4, 0.5, -0.5, 0.5, n=12)]
    F += part("helm", loft("head", "gold", helm, caps=("gold", "gold")))
    F += part("slit", ellipsoid("head", "hollow", (0, 34.6, -2.45), (1.6, 0.36, 0.3), segs=8, rings=2))
    F += part("slitl", ellipsoid("head", "dawn", (0, 34.6, -2.6), (1.2, 0.18, 0.2), segs=6, rings=2))
    for i, (dx, dz, ln) in enumerate(((0, 0.6, 8.0), (-0.6, 1.4, 6.6), (0.6, 1.4, 6.6))):
        F += part(f"crest{i}", tube("head", "white" if i == 0 else "mist", [(dx, 37.6, dz), (dx * 1.5, 41.0, dz + 2.4), (dx * 2, 42.2, dz + ln * 0.75)],
                                    ra=0.3, rb=[0.9, 1.2, 0.3], sides=4, hint=(1, 0, 0)))
    # 白い羽の外套 (肩から脛まで。羽を重ねた形)
    for i in range(7):
        a = math.radians(-70 + i * 23.3)
        x0, z0 = math.sin(a) * 4.4, math.cos(a) * 2.6 + 0.6
        ln = 17.0 - abs(i - 3) * 1.6
        F += part(f"fea{i}", tube("mantle", "white" if i % 2 == 0 else "mist",
                                  [(x0, 29.6, z0), (x0 * 1.1, 29.6 - ln * 0.5, z0 + 1.2), (x0 * 1.15, 29.6 - ln, z0 + 1.6)],
                                  ra=0.35, rb=[2.0, 2.2, 0.8], sides=4, hint=(math.sin(a), 0, math.cos(a))))
    F += part("collar", tube("mantle", "white", [(-4.6, 29.8, 0.4), (-2.4, 30.8, 2.4), (0, 31.0, 2.9), (2.4, 30.8, 2.4), (4.6, 29.8, 0.4)],
                             ra=0.9, rb=1.4, sides=5, hint=(0, 1, 0), caps=(True, True)))
    # 三日月の盾 (左腕。金の縁、白い面)
    R = 6.4
    arc = [(-8.0, 19.5 + math.sin(t) * R, -2.6 - math.cos(t) * R * 0.25) for t in [math.radians(-80 + i * 20) for i in range(9)]]
    F += part("shield", tube("shield", "white", arc, ra=0.5, rb=[0.6, 1.6, 2.4, 2.8, 2.9, 2.8, 2.4, 1.6, 0.6], sides=4, hint=(1, 0, 0)))
    F += part("srim", tube("shield", "gold", [(p[0] - 0.4, p[1], p[2]) for p in arc], ra=0.55, rb=0.6, sides=4, hint=(1, 0, 0)))
    F += part("sboss", ellipsoid("shield", "dawn", (-8.6, 19.5, -2.0), (0.4, 1.0, 1.0), segs=6, rings=2))
    # 長い槍 (右手。柄は木と金の巻き、穂先は細い葉の形)
    F += part("shaft", tube("lance", "wood", [(6.6, 10.0, -2.6), (6.6, 40.0, -2.6)], ra=0.45, sides=6, hint=(1, 0, 0), caps=(True, True)))
    for y in (17.0, 20.4, 30.0):
        F += part(f"wrap{y}", tube("lance", "gold", [(6.6, y, -2.6), (6.6, y + 1.0, -2.6)], ra=0.62, sides=6, hint=(1, 0, 0), caps=(True, True)))
    F += part("head", spike("lance", "gold", (6.6, 40.0, -2.6), (0, 1, 0), 7.0, 1.1, sides=4, rings=3, hint=(0, 0, 1)))
    F += part("pennon", tube("lance", "white", [(6.6, 37.4, -2.6), (6.6, 36.6, -0.4), (6.6, 35.6, 2.2)], ra=0.12, rb=[1.6, 1.4, 0.3], sides=4, hint=(1, 0, 0)))
    return F, []


POSES = {
    "idle": {},
    "charge": {"arm_r": (-80, 0, 0), "lance": (0, 0, 0), "body": (-18, 0, 0), "arm_l": (-40, 0, 10), "leg_l": (30, 0, 0), "leg_r": (-30, 0, 0)},
}

SPEC = dict(
    id="plume_warden", short="羽の番人", kind="約束の島の敵 一品一案 (50 の 4.2.4)",
    title="羽の番人 (plume_warden) — 三日月の盾と長い槍の番兵・低ポリ + ドット絵 一案",
    sub=["槍を下げて構え (地面に光の矢印 1 秒)、まっすぐ突っ込んで人を押し出す。雲の縁から落とされる。",
         "淡い金の鎧と白い羽の外套。顔のない兜の切れ目に朝の光、頂きに羽の飾り。左に三日月の盾、右に長い槍。身長 2.3 ブロック。"],
    labels=["羽の番人"],
    rows=[("前 3/4", "idle", 212, 8, 360, (0, 21, 0), 6.6),
          ("突き", "charge", 240, 6, 360, (0, 21, 0), 6.6),
          ("横", "idle", 270, 4, 360, (0, 21, 0), 6.6),
          ("後ろ 3/4", "idle", 32, 10, 360, (0, 21, 0), 6.6)],
    options={"a": dict(name="三日月の盾の番兵", desc="淡い金の鎧と白い羽の外套、顔のない兜、三日月の盾と長い槍。")},
    extra="雲海の言葉 (淡い金・雲の白・朝の光)。骨 10 本。光るのは兜の切れ目・胸の日輪・盾の芯。",
    budget="敵 900〜1600", pw=420,
    later="二段目 = 外套の羽が金に縁取られる。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
