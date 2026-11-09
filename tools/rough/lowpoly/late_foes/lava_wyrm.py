"""熔岩の管の坑の主 (lava_wyrm)。灰の土地の奥のダンジョン「熔岩の管の坑」(流れる熔岩と崩れる床) の主 (issue #19 の ②)。Lv 33 前後。
技: 噛みつき (0.7 秒、前の帯 5)・熔岩の吐き (1.3 秒、前の扇の床が燃える)・潜り (体力 60% と 30% で熔岩に潜り、床の三か所から順に飛び出す。光る円が予告)。
骨: root > body > neck > head > jaw / seg1..4 (後ろへ続く体) / arm_l / arm_r。
A 熔岩の大蛇: 黒石の鱗の大蛇。鱗の継ぎ目と喉が熾で光る。床の熔岩から上体を出す
B 熾の大百足: 黒石の殻の節が続く大百足。脚の先が熾、頭に大きな顎
C 炉の巨人: 冷えた熔岩の体に炉の口の胸。腕は鎖を巻いた鉄の槌 (主の中で唯一の人型)
"""
import math

from lf import *  # noqa: F401,F403
from lf import box, run, part, mat, eye_at, storm, grow, rows_std, lumps

storm(False)
K = 1.6
BONES = grow([], {"root": (None, (0, 0, 0)), "body": ("root", (0, 6.0, 0)), "neck": ("body", (0, 10.0, -4.0)), "head": ("neck", (0, 20.0, -8.0)),
                  "jaw": ("head", (0, 19.0, -11.0)), "seg1": ("body", (0, 4.0, 5.0)), "seg2": ("seg1", (0, 3.0, 10.0)), "seg3": ("seg2", (0, 3.0, 15.0)),
                  "seg4": ("seg3", (0, 3.0, 20.0)), "arm_l": ("body", (6.0, 16.0, -1.0)), "arm_r": ("body", (-6.0, 16.0, -1.0))}, K)
PAL = {"mats": {"obsid": mat(OBSID, 0.62, noise=0.12), "basalt": mat(BASALT, 0.58, noise=0.2, crack=0.1), "ember": mat(EMBER, 0.82, min=1),
                "rust": mat(RUST, 0.58, noise=0.15), "iron": mat([(40, 38, 40), (66, 62, 64), (98, 92, 92)], 0.6, noise=0.1), "char": mat(CHAR, 0.5, noise=0.2)},
       "skull_bone": "head", "face_mat": "obsid", "glow_mats": ["ember"]}


def build_a(k):
    F = []
    pts = [(0, 1.0, 22.0), (2.0, 1.4, 16.0), (-1.6, 2.0, 10.0), (0.6, 3.0, 4.0), (0, 6.0, -1.0), (0, 12.0, -5.0), (0, 18.0, -7.0)]
    bones_ = ["seg4", "seg3", "seg2", "seg1", "body", "neck", "neck"]
    for i in range(len(pts) - 1):
        F += part(f"body{i}", tube(bones_[i], "obsid", [pts[i], pts[i + 1]], ra=[1.6 + i * 0.35, 1.95 + i * 0.35], sides=8, hint=(1, 0, 0)))
        F += part(f"glow{i}", tube(bones_[i], "ember", [tuple(a + (b - a) * 0.45 for a, b in zip(pts[i], pts[i + 1])), tuple(a + (b - a) * 0.55 for a, b in zip(pts[i], pts[i + 1]))],
                                    ra=[1.75 + i * 0.35, 1.8 + i * 0.35], sides=8, hint=(1, 0, 0)))
    F += part("head", ellipsoid("head", "obsid", (0, 20.0, -9.4), (3.4, 2.6, 4.2), segs=9, rings=3))
    F += part("jaw", box("jaw", "basalt", (0, 18.0, -11.6), (4.4, 1.2, 4.8), taper=(0.8, 0.7)))
    F += part("throat", ellipsoid("neck", "ember", (0, 15.0, -8.2), (2.4, 2.8, 1.6), segs=7, rings=2))
    for sx in (1, -1):
        F += part(f"horn{sx}", spike("head", "basalt", (sx * 2.0, 22.0, -8.0), (sx * 0.5, 0.6, 1), 4.4, 0.9, sides=4, rings=2, bend=(0, 1.0, 0)))
    for i in range(5):
        F += part(f"crest{i}", spike("neck" if i < 2 else "body", "basalt", (0, 20.4 - i * 3.6, -5.2 + i * 1.6), (0, 1, 0.5), 3.0, 0.7, sides=4, rings=1))
    grow(F, {}, K)
    return F, [eye_at(-2.0 * K, 21.2 * K, -12.0 * K, 0.6 * K), eye_at(2.0 * K, 21.2 * K, -12.0 * K, 0.6 * K)]


def build_b(k):
    F = []
    segs = [("neck", (0, 9.0, -4.0)), ("body", (0, 6.0, 0.0)), ("seg1", (0, 4.6, 4.4)), ("seg2", (0, 3.8, 8.8)), ("seg3", (0, 3.4, 13.2)), ("seg4", (0, 3.0, 17.6))]
    for i, (b, c) in enumerate(segs):
        r = 3.6 - i * 0.3
        F += part(f"seg{i}", ellipsoid(b, "obsid", c, (r, r * 0.75, 2.6), segs=9, rings=2))
        F += part(f"segglow{i}", ellipsoid(b, "ember", (c[0], c[1] + r * 0.6, c[2] + 1.8), (r * 0.6, 0.35, 0.4), segs=6, rings=1))
        for sx in (1, -1):
            F += part(f"leg{i}{sx}", tube(b, "basalt", [(sx * r * 0.8, c[1] - 0.6, c[2]), (sx * (r + 2.4), c[1] + 0.6, c[2]), (sx * (r + 3.0), 0.2, c[2] - 0.4)],
                                           ra=[0.6, 0.45, 0.2], sides=4, hint=(1, 0, 0)))
            F += part(f"legtip{i}{sx}", ellipsoid(b, "ember", (sx * (r + 3.0), 0.3, c[2] - 0.4), (0.35, 0.35, 0.35), segs=4, rings=1))
    F += part("head", ellipsoid("head", "obsid", (0, 11.0, -8.2), (3.0, 2.2, 2.4), segs=8, rings=2))
    for sx in (1, -1):
        F += part(f"mand{sx}", tube("jaw", "basalt", [(sx * 1.8, 10.0, -9.8), (sx * 3.4, 9.4, -12.6), (sx * 1.0, 9.0, -14.0)], ra=[0.9, 0.6, 0.1], sides=4, hint=(0, 1, 0)))
        F += part(f"ant{sx}", tube("head", "char", [(sx * 1.2, 12.8, -9.6), (sx * 3.0, 16.6, -11.6), (sx * 5.0, 17.4, -13.4)], ra=[0.35, 0.25, 0.05], sides=4, hint=(0, 1, 0)))
    grow(F, {}, K)
    return F, [eye_at(-1.6 * K, 12.0 * K, -10.2 * K, 0.5 * K), eye_at(1.6 * K, 12.0 * K, -10.2 * K, 0.5 * K)]


def build_c(k):
    F = []
    F += part("torso", ellipsoid("body", "basalt", (0, 18.0, -1.0), (6.4, 6.6, 4.4), segs=9, rings=3))
    F += part("furnace", tube("body", "iron", [(0, 17.0, -4.4), (0, 17.0, -5.6)], ra=[3.0, 3.2], sides=8, hint=(0, 1, 0)))
    F += part("furnace_fire", ellipsoid("body", "ember", (0, 17.0, -5.4), (2.4, 2.4, 0.5), segs=8, rings=2))
    F += part("hips", ellipsoid("body", "basalt", (0, 11.0, 0), (5.0, 2.8, 3.4), segs=8, rings=2))
    F += part("head", ellipsoid("head", "basalt", (0, 25.6, -2.0), (2.6, 2.4, 2.4), segs=8, rings=2))
    F += part("chimney", tube("head", "iron", [(0, 27.6, -1.0), (0, 31.0, 0.0)], ra=[1.2, 1.0], sides=6, hint=(1, 0, 0)))
    F += part("eyes", box("head", "ember", (0, 25.8, -4.3), (2.8, 0.5, 0.3)))
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(f"{b}_up", tube(b, "basalt", [(sx * 6.0, 22.0, -1.0), (sx * 8.6, 15.0, -2.0)], ra=[2.4, 2.0], sides=7, hint=(0, 0, 1)))
        F += part(f"{b}_lo", tube(b, "basalt", [(sx * 8.6, 15.2, -2.0), (sx * 9.0, 8.0, -3.0)], ra=[2.0, 1.8], sides=7, hint=(0, 0, 1)))
        F += part(f"{b}_hammer", box(b, "iron", (sx * 9.0, 5.0, -3.0), (4.4, 4.4, 4.4)))
        F += part(f"{b}_chain", tube(b, "iron", [(sx * 9.2, 12.0, -3.0), (sx * 9.4, 9.0, -3.2)], ra=[2.1, 2.0], sides=6, hint=(0, 0, 1)))
    for sx, leg in ((1, "seg1"), (-1, "seg2")):
        F += part(f"leg{sx}", tube("body", "basalt", [(sx * 3.0, 10.0, 0), (sx * 3.4, 4.0, -0.4), (sx * 3.4, 1.0, 0)], ra=[2.4, 2.0, 2.0], sides=7, hint=(1, 0, 0)))
        F += part(f"foot{sx}", box("body", "char", (sx * 3.4, 0.6, -1.2), (3.6, 1.2, 5.0), taper=(0.9, 0.8)))
    F += lumps("body", "ember", [((4.0, 21.0, -3.0), 0.6), ((-4.4, 14.0, -2.4), 0.5), ((2.0, 11.0, -2.6), 0.5)])
    grow(F, {}, K)
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "breath": {"neck": (-20, 0, 0), "head": (-14, 0, 0), "jaw": (35, 0, 0), "arm_l": (-90, 0, 20), "arm_r": (-90, 0, -20)},
         "dive": {"body": (40, 0, 0), "neck": (40, 0, 0), "head": (20, 0, 0)}}

SPEC = dict(
    id="lava_wyrm", short="熔岩の管の主", kind="奥のダンジョンの主 3案", role="主", land="灰",
    title="熔岩の管の坑の主 (lava_wyrm) — 灰の奥のダンジョン・低ポリ 3 案",
    sub=["噛みつき (0.7 秒、前の帯 5)・熔岩の吐き (1.3 秒、前の扇の床が燃える)・潜り (体力 60% と 30% で熔岩に潜り、床の三か所から順に飛び出す。光る円が予告)。",
         "大きさは人の 1.6 倍 (A・B は長さ 4〜5 ブロック)。"],
    labels=["A 熔岩の大蛇", "B 熾の大百足", "C 炉の巨人"],
    rows=rows_std(14, 4.8, h=420, extra=(("吐きの予告", "breath"), ("潜り", "dive"))),
    options={
        "a": dict(name="A 熔岩の大蛇", desc="黒石の鱗の大蛇。鱗の継ぎ目と喉が熾で光る。床の熔岩から上体を出して吐き、潜っては飛び出す。熔岩の管の主として一番まっすぐ。"),
        "b": dict(name="B 熾の大百足", desc="黒石の殻の節が続く大百足。脚の先が熾、頭に大きな顎。床を這い回り、崩れる床と相性がよい。虫が苦手な人には怖い。"),
        "c": dict(name="C 炉の巨人", desc="冷えた熔岩の体に炉の口の胸、頭に煙突、腕は鎖を巻いた鉄の槌。灰の鍛え手 (中ボス) と系統が近い。潜りの技は合わないので床を叩いて熔岩を噴かせる形に替える。")},
    extra="骨 11 本。", budget="主 600〜1200", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
