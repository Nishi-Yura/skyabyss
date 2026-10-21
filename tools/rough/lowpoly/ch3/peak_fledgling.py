"""峰の雛 (peak_fledgling) 低ポリ ラフ 3 案。急降下の予習。HP 14、急降下 5。
岩棚の巣から飛び立ち、狙った人へ一直線に急降下 (光る線 0.8 秒。羽守の突撃の縮小版)。外すと岩に当たってよろめく。低く飛ぶ。
A 灰毛の雛 (丸い灰の綿羽・短い翼・銅の嘴) / B 鷲の子 (白青の羽の細身・銅の冠羽、羽守の兜の写し) / C 焦げ羽の雛 (黒く焦げた羽に青い焦げ筋・琥珀の目)
骨: root > body > head / wing_l / wing_r / tail / leg_l / leg_r。急降下 = 体を前へ倒し翼を後ろへたたむ。よろめく = 頭を振り翼を垂らす。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck3 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 5.0, 0)), "head": ("body", (0, 8.0, -2.4)),
         "wing_l": ("body", (-2.6, 7.0, -0.6)), "wing_r": ("body", (2.6, 7.0, -0.6)), "tail": ("body", (0, 5.6, 3.0)),
         "leg_l": ("body", (-1.1, 3.6, 0.2)), "leg_r": ("body", (1.1, 3.6, 0.2))}


def pal(body, wing, beak="beak", glow=()):
    return {"mats": {
        "down": mat(DOWN, 0.62, crack=0.0, noise=0.2, facevar=0.08),
        "grey": mat(ROCK, 0.66, crack=0.0, noise=0.22, facevar=0.08),
        "feather": mat(FEATHER, 0.62, crack=0.0, noise=0.12, stripe=3, axis="v"),
        "feathl": mat(FEATHER, 0.74, crack=0.0, noise=0.1),
        "char": mat(CHAR, 0.6, crack=0.0, noise=0.2, facevar=0.06),
        "scorch": mat(BOLT, 0.7, crack=0.0, min=1),
        "beak": mat(COPPER, 0.66, crack=0.0, noise=0.08),
        "claw": mat(CHAR, 0.5, crack=0.0),
        "eyeg": mat(AMBER, 0.78, crack=0.0, min=2),
        "eyeb": mat(BOLT, 0.78, crack=0.0, min=2),
        "dark": mat(DEEP, 0.5, crack=0.0, min=0),
    }, "skull_bone": "none", "face_mat": "none", "glow_mats": list(glow)}


PALS = {"a": pal("grey", "grey", glow=["eyeb"]), "b": pal("feather", "feather", glow=["eyeb"]), "c": pal("char", "char", glow=["scorch", "eyeg"])}


def bird(F, body_mat, belly_mat, wing_mat, head_mat, plump=1.0, wing_len=6.0, eye_mat="eyeb"):
    F += part("torso", ellipsoid("body", body_mat, (0, 6.0, 0.4), (2.8 * plump, 2.6 * plump, 3.4), segs=7, rings=2))
    F += part("belly", ellipsoid("body", belly_mat, (0, 5.4, -0.8), (2.2 * plump, 2.0 * plump, 2.4), segs=6, rings=2))
    F += part("headb", ellipsoid("head", head_mat, (0, 9.0, -2.8), (2.1, 2.0, 2.1), segs=7, rings=2))
    F += part("beak", spike("head", "beak", (0, 8.8, -4.4), (0, -0.35, -1), 2.2, 0.7, sides=4, rings=1, bend=(0, -0.5, 0)))
    for s in (-1, 1):
        F += part(f"eye{s}", ellipsoid("head", eye_mat, (s * 1.55, 9.5, -3.9), (0.42, 0.5, 0.3), segs=4, rings=1))
    for s, b in ((-1, "wing_l"), (1, "wing_r")):
        # 翼: 平たい閉じた楕円体を 2 枚重ねずに並べる (根元は体に沈める)
        F += part(f"arm{s}", ellipsoid(b, wing_mat, (s * (2.4 + wing_len * 0.3), 7.0, 0.2), (wing_len * 0.38, 0.55, 2.0), segs=6, rings=1))
        F += part(f"prim{s}", ellipsoid(b, wing_mat, (s * (2.4 + wing_len * 0.68), 6.8, 0.9), (wing_len * 0.3, 0.45, 1.4), segs=5, rings=1))
    F += part("tailf", ellipsoid("tail", wing_mat, (0, 5.8, 4.6), (1.6, 0.45, 2.2), segs=5, rings=1))
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"shin{s}", tube(b, "claw", [(s * 1.1, 4.2, 0.2), (s * 1.2, 2.0, 0.0), (s * 1.2, 0.4, -0.2)], ra=[0.45, 0.32, 0.3], sides=3, caps=(False, True)))
        F += part(f"toe{s}", spike(b, "claw", (s * 1.2, 0.4, -0.2), (0, -0.05, -1), 1.4, 0.3, sides=3, rings=1))


def build_a(k):
    """A 灰毛の雛: 丸い灰の綿羽の雛。短い翼、銅の嘴、青白い目。峰の岩に紛れる色。"""
    F = []
    bird(F, "grey", "down", "grey", "grey", plump=1.15, wing_len=5.2)
    # 頭の綿毛の房 (寝ぐせ)
    for i, (x, dz) in enumerate(((-0.5, 0.2), (0.4, -0.2), (0.0, 0.6))):
        F += part(f"fluff{i}", spike("head", "down", (x, 10.6, -2.8 + dz), (x * 0.5, 1, 0.3), 1.4, 0.4, sides=3, rings=1))
    return F, []


def build_b(k):
    """B 鷲の子: 白青の羽の細身の若鳥。頭に銅の冠羽 (羽守の兜の避雷針の写し)。翼が長い。"""
    F = []
    bird(F, "feather", "feathl", "feather", "feathl", plump=0.9, wing_len=7.2)
    for i, (x, ln) in enumerate(((0, 2.6), (-0.7, 1.8), (0.7, 1.8))):
        F += part(f"crest{i}", spike("head", "beak", (x, 10.4, -2.2), (x * 0.3, 1, 0.8), ln, 0.38, sides=3, rings=1))
    return F, []


def build_c(k):
    """C 焦げ羽の雛: 雷に焼かれた黒い羽に、青白い焦げ筋が光る。琥珀の目。焼けた見張り塔の雛。"""
    F = []
    bird(F, "char", "char", "char", "char", plump=1.0, wing_len=6.2, eye_mat="eyeg")
    for s, b in ((-1, "wing_l"), (1, "wing_r")):
        F += part(f"streak{s}", tube(b, "scorch", [(s * 3.0, 7.5, -0.4), (s * 5.0, 7.45, 0.3), (s * 7.6, 7.2, 1.0)], ra=[0.18, 0.22, 0.12], sides=3))
    F += part("streakh", tube("head", "scorch", [(0, 10.9, -3.6), (0, 11.0, -2.6), (0, 10.6, -1.2)], ra=[0.16, 0.2, 0.14], sides=3))
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "flap": {"wing_l": (0, 0, -40), "wing_r": (0, 0, 40), "leg_l": (30, 0, 0), "leg_r": (30, 0, 0)},
    "dive": {"body": (-40, 0, 0), "head": (20, 0, 0), "wing_l": (0, 40, -12), "wing_r": (0, -40, 12), "tail": (14, 0, 0), "leg_l": (50, 0, 0), "leg_r": (50, 0, 0)},
    "stagger": {"body": (10, 0, 14), "head": (-10, 20, 16), "wing_l": (0, 0, 30), "wing_r": (0, 0, -24)},
}

SPEC = dict(
    id="peak_fledgling", short="峰の雛", kind="地上の敵 3案 (P16)",
    title="峰の雛 (peak_fledgling) — 岩棚の巣の雛鳥・低ポリ + ドット絵 3 案",
    sub=["三章の地上の敵 (峰・焼けた見張り塔の入口の間)。HP 14、急降下 5。岩棚の巣から飛び立ち、狙った人へ一直線に急降下 (地面に光る線 0.8 秒)。",
         "外すと岩に当たってよろめく (狙い目)。羽守の突撃の縮小版。全長 約 0.6 ブロック。左の黄の目盛りは人 (1.8 ブロック)。"],
    labels=["A 灰毛の雛", "B 鷲の子", "C 焦げ羽の雛"],
    rows=[("前 3/4 (止まった姿)", "idle", 212, 14, 300, (0, 6, 0), 16.0),
          ("横", "idle", 270, 4, 300, (0, 6, 0), 16.0),
          ("飛ぶ (羽ばたき)", "flap", 200, 20, 300, (0, 7, 0), 14.0),
          ("急降下 (翼をたたみ前へ倒れる)", "dive", 250, 10, 300, (0, 6, 0), 15.0),
          ("よろめく (外して岩に当たった)", "stagger", 200, 12, 300, (0, 6, 0), 15.0)],
    options={
        "a": dict(name="A 灰毛の雛", desc="丸い灰の綿羽の雛。短い翼と銅の嘴、青白い目、頭に寝ぐせの綿毛。峰の岩に紛れ、巣立ったばかりの不器用さが急降下の外しに合う。いちばん丸く、背中の羽守とは似ない。"),
        "b": dict(name="B 鷲の子", desc="白青の羽の細身の若鳥。頭の銅の冠羽が羽守の兜の避雷針に似て、「羽守の突撃の予習」がいちばん見た目で伝わる。翼が長く、急降下の線が鋭く見える。"),
        "c": dict(name="C 焦げ羽の雛", desc="雷に焼かれた黒い羽に、翼と頭の青白い焦げ筋が光る。琥珀の目。焼けた見張り塔の住み手らしさが強く、暗い塔の中でも筋の光で居場所が分かる。")},
    extra="三章の言葉 (峰の灰・白青の羽・銅・焦げ・雷の青白)。骨 8 本。翼は閉じた平たい楕円体 2 枚を根元で体に沈める。急降下は体の前倒しと翼のたたみ、よろめきは頭の振り。",
    budget="地上の小物 300〜500", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PALS, BONES, POSES)
