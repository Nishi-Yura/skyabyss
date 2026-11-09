"""崩れた見張りの砦の主 (fort_warlord)。岩鯨の土地の奥のダンジョン「崩れた見張りの砦」(地上。野盗の巣を登る) の主 (issue #19 の ②)。Lv 15 前後。
砦の頂で待つ野盗の頭。技 (予告つき): 大薙ぎ (0.9 秒、前の広い扇)・跳び叩き (1.2 秒、狙った人の足もとの円 3)・手下を呼ぶ角笛 (体力 60% と 30%)。
大きさは人の 1.4 倍。骨: 人の骨 (root > body > head / arm_l / arm_r > weapon / offhand / leg_l / leg_r)。
A 骨鎧の野盗王: 岩鯨の骨を打ち付けた鎧、骨の兜、片刃の大鉈
B 肋骨の盾の大男: 背の高い大男が岩鯨の肋骨を盾にし、丸太の大槌
C 鉄仮面の弩手長: 鉄の仮面と厚い外套、重い連弩 (遠くから撃つ主。寄ると短剣)
"""
import math

from lf import *  # noqa: F401,F403
from lf import box, run, part, mat, eye_at, storm, biped, grow, HUMAN_BONES, rows_std

storm(False)
K = 1.4
BONES = grow([], HUMAN_BONES, K)
BONE = [(110, 100, 84), (150, 140, 120), (190, 180, 158), (220, 212, 192)]
PAL = {"mats": {"bone": mat(BONE, 0.65, noise=0.15, crack=0.1), "leather": mat([(46, 30, 22), (76, 50, 34), (108, 74, 50), (140, 104, 72)], 0.55, noise=0.15),
                "cloth": mat([(60, 44, 36), (92, 68, 50), (124, 96, 70)], 0.55, noise=0.2), "iron": mat([(40, 40, 46), (66, 66, 74), (98, 98, 106), (136, 136, 144)], 0.6, noise=0.1),
                "skin": mat([(90, 60, 44), (130, 92, 66), (168, 126, 94)], 0.55), "wood": mat([(46, 30, 18), (70, 46, 28), (98, 66, 40)], 0.55, stripe=3),
                "dark": mat([(14, 12, 12), (28, 24, 24), (44, 40, 38)], 0.5), "ember": mat(EMBER, 0.8, min=1)},
       "skull_bone": "head", "face_mat": "skin", "glow_mats": ["ember"]}


def build_a(k):
    F = []
    biped(F, "leather", "cloth", bulk=1.15)
    F += part("chest_bone", box("body", "bone", (0, 18.6, -2.8), (7.4, 6.0, 1.2), taper=(0.9, 1.0)))
    for sx in (1, -1):
        F += part(f"pauldron{sx}", ellipsoid("body", "bone", (sx * 5.8, 22.4, 0), (2.6, 1.8, 2.6), segs=7, rings=2))
        F += part(f"spike{sx}", spike("body", "bone", (sx * 6.6, 23.6, 0), (sx * 0.6, 1, 0), 3.2, 0.8, sides=4, rings=1))
    F += part("skull", ellipsoid("head", "skin", (0, 26.6, 0), (2.2, 2.4, 2.2), segs=8, rings=3))
    F += part("helm", ellipsoid("head", "bone", (0, 27.8, 0.2), (2.6, 1.8, 2.6), segs=8, rings=2))
    F += part("helm_ridge", box("head", "bone", (0, 29.6, 0.2), (0.8, 1.4, 4.8), taper=(0.6, 0.8)))
    F += part("beard", ellipsoid("head", "cloth", (0, 24.8, -1.6), (1.6, 1.4, 1.0), segs=6, rings=2))
    F += part("blade", box("weapon", "iron", (-6.2, 3.6, -0.6), (1.0, 12.0, 4.0), taper=(1.0, 1.4)))
    F += part("hilt", tube("weapon", "wood", [(-6.2, 11.6, -0.5), (-6.2, 9.4, -0.5)], ra=[0.7, 0.7], sides=5, hint=(1, 0, 0)))
    F += part("belt", tube("body", "leather", [(-4.2, 13.6, 0), (0, 13.8, -2.9), (4.2, 13.6, 0)], ra=[0.6, 0.6, 0.6], sides=4, hint=(0, 1, 0)))
    grow(F, {}, K)
    return F, [eye_at(-0.9 * K, 26.8 * K, -2.0 * K), eye_at(0.9 * K, 26.8 * K, -2.0 * K)]


def build_b(k):
    F = []
    biped(F, "cloth", "leather", bulk=1.35)
    F += part("skull", ellipsoid("head", "skin", (0, 26.2, -0.2), (2.0, 2.2, 2.1), segs=8, rings=3))
    F += part("hair", ellipsoid("head", "dark", (0, 27.6, 0.8), (2.2, 1.4, 2.0), segs=7, rings=2))
    F += part("jaw", box("head", "skin", (0, 24.8, -1.4), (2.8, 1.4, 1.6), taper=(0.8, 0.8)))
    # 岩鯨の肋骨の盾 (左手)
    for i in range(4):
        F += part(f"rib{i}", tube("offhand", "bone", [(7.2, 18.0 - i * 3.0, -3.0), (8.4, 15.8 - i * 3.0, -4.4), (7.4, 13.4 - i * 3.0, -5.4)], ra=[0.8, 0.9, 0.6], sides=5, hint=(1, 0, 0)))
    F += part("shield_strap", box("offhand", "leather", (7.4, 11.0, -2.2), (1.0, 12.0, 1.2)))
    # 丸太の大槌 (右手)
    F += part("haft", tube("weapon", "wood", [(-6.6, 14.0, -0.5), (-6.6, 2.0, -0.5)], ra=[0.7, 0.7], sides=5, hint=(1, 0, 0)))
    F += part("head_log", tube("weapon", "wood", [(-6.6, 2.0, -3.6), (-6.6, 2.0, 3.0)], ra=[2.2, 2.2], sides=7, hint=(0, 1, 0)))
    for z in (-2.4, 1.8):
        F += part(f"band{z}", tube("weapon", "iron", [(-6.6, 2.0, z - 0.4), (-6.6, 2.0, z + 0.4)], ra=[2.4, 2.4], sides=7, hint=(0, 1, 0)))
    grow(F, {}, K * 1.1)
    s = K * 1.1
    return F, [eye_at(-0.9 * s, 26.6 * s, -1.9 * s), eye_at(0.9 * s, 26.6 * s, -1.9 * s)]


def build_c(k):
    F = []
    biped(F, "cloth", "leather", bulk=1.1)
    F += part("cloak", ellipsoid("body", "cloth", (0, 17.0, 2.0), (5.4, 7.6, 2.6), segs=9, rings=3))
    F += part("collar", ellipsoid("body", "leather", (0, 22.0, 0.4), (4.6, 1.6, 3.2), segs=8, rings=1))
    F += part("skull", ellipsoid("head", "skin", (0, 26.4, 0), (2.1, 2.3, 2.1), segs=8, rings=3))
    F += part("mask", box("head", "iron", (0, 26.4, -2.0), (4.0, 4.4, 0.8), taper=(0.85, 0.9)))
    F += part("visor", box("head", "ember", (0, 27.0, -2.5), (2.6, 0.5, 0.3)))
    F += part("hood", ellipsoid("head", "cloth", (0, 27.8, 0.6), (2.8, 2.2, 2.6), segs=8, rings=2))
    # 重い連弩 (両手で前に構える)
    F += part("stock", box("weapon", "wood", (-4.0, 12.4, -4.6), (1.6, 1.6, 9.0)))
    F += part("bow", tube("weapon", "iron", [(-10.0, 12.6, -8.0), (-4.0, 13.2, -8.8), (2.0, 12.6, -8.0)], ra=[0.5, 0.6, 0.5], sides=4, hint=(0, 1, 0)))
    F += part("magazine", box("weapon", "wood", (-4.0, 14.6, -4.0), (2.0, 2.4, 3.2)))
    F += part("bolt", box("weapon", "ember", (-4.0, 13.4, -9.4), (0.4, 0.4, 2.6)))
    grow(F, {}, K)
    return F, []


def detail(F):
    # 細部: 腰の頭骨の飾りと袋・鉈の刃の欠け・骨鎧の鋲・兜の角・肩布
    for i, x in enumerate((-2.6, 2.8)):
        F += part(f"skull{i}", ellipsoid("body", "bone", (x, 12.6, -2.9), (0.8, 0.9, 0.7), segs=6, rings=2))
        F += part(f"socket{i}", box("body", "dark", (x, 12.8, -3.6), (0.8, 0.3, 0.2)))
    F += part("pouch2", box("body", "leather", (-3.4, 12.2, -1.4), (1.8, 2.0, 1.4), taper=(0.9, 0.8)))
    for i, (x, y) in enumerate(((-2.2, 20.6), (2.2, 20.6), (-2.2, 16.6), (2.2, 16.6))):
        F += part(f"rivet{i}", ellipsoid("body", "iron", (x, y, -3.5), (0.35, 0.35, 0.25), segs=4, rings=1))
    for s in (1, -1):
        F += part(f"helmhorn{s}", spike("head", "bone", (s * 2.2, 28.4, 0.2), (s * 0.8, 0.8, 0.2), 2.4, 0.5, sides=4, rings=2, bend=(0, 0.6, 0)))
        F += part(f"mantle{s}", box("body", "cloth", (s * 5.4, 19.6, 1.0), (2.6, 3.4, 0.4), taper=(0.8, 1.0)))
    for i, y in enumerate((1.0, 4.0, 7.0)):
        F += part(f"nick{i}", box("weapon", "dark", (-6.2, y, -2.6), (1.1, 0.6, 0.6)))
    return F


def build(k):
    F, eyes = {"a": build_a, "b": build_b, "c": build_c}[k](k)
    if k == "a":
        extra = detail([])
        grow(extra, {}, K)
        F += extra
    return F, eyes


POSES = {"idle": {},
         "swing": {"body": (0, 30, 0), "arm_r": (-110, 0, -40), "arm_l": (20, 0, 20)},
         "leap": {"body": (-20, 0, 0), "arm_r": (-160, 0, 0), "arm_l": (-160, 0, 0), "leg_l": (-40, 0, 0), "leg_r": (30, 0, 0)}}

SPEC = dict(
    id="fort_warlord", short="砦の主", kind="奥のダンジョンの主 3案", role="主", land="岩鯨",
    title="崩れた見張りの砦の主 (fort_warlord) — 岩鯨の奥のダンジョン・低ポリ 3 案",
    sub=["地上の砦 (野盗の巣) を登った頂で待つ野盗の頭。大薙ぎ (0.9 秒、前の広い扇)・跳び叩き (1.2 秒、足もとの円 3)・手下を呼ぶ角笛 (体力 60% と 30%)。",
         "大きさは人の 1.4 倍。主の見た目は心臓のあとの主と同じく、一目で『頭』と分かるもの。"],
    labels=["A 骨鎧の野盗王", "B 肋骨の盾の大男", "C 鉄仮面の弩手長"],
    rows=rows_std(20, 6.5, h=420, extra=(("大薙ぎの予告", "swing"), ("跳び叩きの予告", "leap"))),
    options={
        "a": dict(name="A 骨鎧の野盗王", desc="岩鯨の骨を打ち付けた鎧と骨の兜、片刃の大鉈。岩鯨の土地の野盗が巨神の骨で身を固めた、土地の物語に一番合う形。"),
        "b": dict(name="B 肋骨の盾の大男", desc="背の高い大男が岩鯨の肋骨を盾にし、鉄の輪の丸太の大槌を振る。盾で前を受けるので、回り込みを覚えさせる主。"),
        "c": dict(name="C 鉄仮面の弩手長", desc="鉄の仮面と厚い外套、重い連弩。遠くから撃つ主で、寄ると短剣に持ち替える。砦の高さ (登る作り) を活かせる。")},
    extra="人の骨 9 本。", budget="主 600〜1200", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
