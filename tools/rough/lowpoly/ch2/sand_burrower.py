"""砂潜り (sand_burrower) 低ポリ ラフ 3 案。地面の下の敵。HP 16、飛びかかり 4。
干潮の砂に潜む。人が真上を歩くと砂が小さく盛り上がり (予告)、飛び出して噛む。満潮では出ない。
A 砂鰻 (細長い砂色の鰻。珊瑚色の顎) / B 砂蟹 (丸い砂の甲羅・羽の触角・前の鋏) / C 星見魚 (平たい頭が上を向き、目と口が空を見る)
骨: root > body > head > jaw / tail / fin_l / fin_r (蟹では fin = 鋏、tail = 腹の板)。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck2 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 3.0, 0)), "head": ("body", (0, 3.4, -5.5)),
         "jaw": ("head", (0, 2.6, -7.5)), "tail": ("body", (0, 3.0, 6.0)),
         "fin_l": ("body", (-4.0, 2.6, -2.0)), "fin_r": ("body", (4.0, 2.6, -2.0))}
PAL = {"mats": {
    "skin": mat(SAND, 0.62, crack=0.0, noise=0.25, dots=0.1),
    "belly": mat(SALT, 0.7, crack=0.0, noise=0.1),
    "coral": mat(CORAL, 0.66, crack=0.0, noise=0.12),
    "teal": mat(TEAL, 0.6, crack=0.0, noise=0.2),
    "shell": mat(SAND, 0.66, crack=0.12, noise=0.2, stain=dict(ramp=TEAL, thr=0.95, up=0.2, conc=0.3)),
    "tooth": mat(SALT, 0.8, crack=0.0),
    "mouth": mat(DEEP, 0.5, crack=0.0, min=0),
}, "skull_bone": "head", "face_mat": "skin"}


def eye_at(x, y, z, r=0.9, zr=2.0):
    return dict(c=V(x, y, z), rx=r, ry=r * 0.7, pr=(0.5, 0.4), tilt=0.1, zr=zr)


def build_a(k):
    """A 砂鰻: 砂色の細長い体 (背に暗い斑)、珊瑚色の顎と白い牙。潜るときは尾から。"""
    F = []
    R = [ring(3.0, 2.2, -2.4, 2.4, zc=-4.0, n=6), ring(3.0, 2.6, -3.0, 3.0, zc=0.0, n=6), ring(3.0, 2.2, -2.6, 2.6, zc=4.0, n=6)]
    # 胴: 前後に伸びた筒 (z 方向の筒を tube で)
    F += part("body", tube("body", "skin", [(0, 3.0, -5.0), (0, 3.2, -1.0), (0, 3.0, 3.0), (0, 2.8, 6.4)], ra=[2.2, 2.6, 2.4, 1.9], rb=[2.0, 2.4, 2.2, 1.7], sides=6, hint=(1, 0, 0), caps=(False, False)))
    F += part("tail", tube("tail", "skin", [(0, 2.8, 6.0), (0, 2.6, 10.0), (0, 2.2, 14.0), (0, 1.8, 17.5)], ra=[1.9, 1.5, 1.0, 0.1], rb=[1.7, 1.3, 0.9, 0.1], sides=6, hint=(1, 0, 0)))
    F += part("finback", spike("tail", "coral", (0, 3.8, 11.0), (0, 0.6, 1), 5.0, 0.7, sides=3, rings=1))
    F += part("head", ellipsoid("head", "skin", (0, 3.6, -7.0), (2.6, 2.2, 3.2), segs=6, rings=2))
    F += part("jaw", ellipsoid("jaw", "coral", (0, 1.9, -8.8), (2.0, 0.8, 2.6), segs=6, rings=1))
    for s in (-1, 1):
        F += part(f"fang{s}", spike("jaw", "tooth", (s * 1.0, 2.3, -10.6), (s * 0.1, 1, -0.2), 1.6, 0.4, sides=3, rings=1))
        F += part(f"fin{s}", spike(f"fin_{'l' if s < 0 else 'r'}", "coral", (s * 2.4, 2.6, -2.0), (s, -0.3, 0.6), 3.4, 1.0, sides=3, rings=1))
    eyes = [eye_at(-1.8, 4.6, -8.6), eye_at(1.8, 4.6, -8.6)]
    return F, eyes


def build_b(k):
    """B 砂蟹: 丸い砂の甲羅 (前が低い卵形)、羽のような触角、前の短い鋏。"""
    F = []
    R = [ring(1.0, 4.6, -6.0, 5.0, n=8), ring(3.2, 5.4, -7.0, 6.0, n=8), ring(5.4, 4.2, -5.4, 4.8, n=8), ring(6.6, 1.8, -2.2, 2.2, n=8)]
    F += part("carapace", loft("body", "shell", R, caps=("belly", "shell")))
    F += part("stripe", ellipsoid("body", "teal", (0, 6.1, 0.0), (1.2, 0.6, 4.2), segs=4, rings=1))
    F += part("tail", spike("tail", "shell", (0, 1.6, 5.4), (0, -0.3, 1), 3.4, 2.2, sides=4, rings=1))
    F += part("head", ellipsoid("head", "skin", (0, 3.0, -7.0), (2.4, 1.4, 1.6), segs=6, rings=1))
    F += part("jaw", ellipsoid("jaw", "coral", (0, 2.0, -8.0), (1.4, 0.6, 0.9), segs=5, rings=1))
    for s in (-1, 1):
        F += part(f"ant{s}", tube("head", "coral", [(s * 0.8, 3.8, -7.6), (s * 1.8, 6.6, -9.6), (s * 2.4, 9.0, -10.4)], ra=[0.4, 0.35, 0.1], sides=3))
        for j, t in enumerate((0.4, 0.7)):
            F += part(f"feather{s}{j}", spike("head", "coral", (s * (0.8 + 1.6 * t), 3.8 + 5.2 * t, -7.6 - 2.8 * t), (s * 1, 0.2, 0.3), 1.6, 0.25, sides=3, rings=1))
        b = f"fin_{'l' if s < 0 else 'r'}"
        F += part(f"claw{s}", tube(b, "coral", [(s * 3.6, 2.4, -3.0), (s * 4.8, 2.0, -6.0), (s * 4.2, 1.6, -8.6)], ra=[0.9, 0.8, 0.7], sides=4))
        F += part(f"pincer{s}", spike(b, "tooth", (s * 4.2, 1.6, -8.6), (-s * 0.2, 0, -1), 1.8, 0.6, sides=3, rings=1))
        for j, z in enumerate((-2.0, 1.0, 3.6)):
            F += part(f"leg{s}{j}", tube("body", "skin", [(s * 4.6, 1.6, z), (s * 6.4, 1.0, z + 0.6), (s * 7.2, 0.0, z + 1.2)], ra=[0.6, 0.5, 0.2], sides=3))
    eyes = [eye_at(-1.2, 3.3, -8.4, 0.7), eye_at(1.2, 3.3, -8.4, 0.7)]
    return F, eyes


def build_c(k):
    """C 星見魚: 平たく広い頭が上を向き、目と大きな口が空を見る。口の縁に珊瑚の房。胴は細く尾へ。"""
    F = []
    R = [ring(0.8, 4.6, -4.2, 3.6, zc=-6.0, n=8), ring(3.0, 5.2, -4.8, 4.0, zc=-6.0, n=8), ring(4.6, 4.0, -3.8, 3.2, zc=-6.0, n=8)]
    F += part("head", loft("head", "skin", R, caps=("belly", "skin")))
    # 上を向いた口 (頭の上の前の縁に、暗い口と白い牙の列)
    F += part("mouth", ellipsoid("jaw", "mouth", (0, 4.5, -8.8), (3.0, 0.4, 1.0), segs=6, rings=1))
    F += part("lip", tube("jaw", "coral", [(-3.2, 4.4, -8.6), (-1.6, 4.9, -9.8), (0, 5.0, -10.2), (1.6, 4.9, -9.8), (3.2, 4.4, -8.6)], ra=0.45, sides=3))
    for i in range(5):
        x = -2.4 + 1.2 * i
        F += part(f"fringe{i}", spike("jaw", "coral", (x, 4.9, -9.6 - 0.3 * (1 - abs(x) / 3)), (x * 0.1, 1, -0.4), 1.4, 0.3, sides=3, rings=1))
    F += part("body", tube("body", "skin", [(0, 2.6, -2.4), (0, 2.6, 1.0), (0, 2.4, 5.0)], ra=[3.4, 2.8, 1.9], rb=[2.2, 1.9, 1.4], sides=6, hint=(1, 0, 0)))
    F += part("tail", tube("tail", "skin", [(0, 2.4, 5.0), (0, 2.2, 8.0), (0, 2.0, 10.0)], ra=[1.9, 1.0, 0.4], rb=[1.4, 0.8, 0.3], sides=5, hint=(1, 0, 0)))
    F += part("tailfin", spike("tail", "teal", (0, 2.0, 9.6), (0, 0, 1), 3.4, 2.4, sides=4, rings=1, rb=0.3))
    for s in (-1, 1):
        b = f"fin_{'l' if s < 0 else 'r'}"
        F += part(f"fin{s}", spike(b, "teal", (s * 3.8, 2.2, -2.6), (s, -0.1, 0.5), 4.4, 1.8, sides=4, rings=1, rb=0.4))
    eyes = [eye_at(-1.9, 4.6, -5.0, 0.8, 1.0), eye_at(1.9, 4.6, -5.0, 0.8, 1.0)]
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "leap": {"body": (-28, 0, 0), "head": (-10, 0, 0), "jaw": (30, 0, 0), "tail": (24, 0, 0), "fin_l": (0, 0, 30), "fin_r": (0, 0, -30)},
}

SPEC = dict(
    id="sand_burrower", short="砂潜り", kind="地上の敵 3案 (P12)",
    title="砂潜り (sand_burrower) — 砂の下の待ち伏せ・低ポリ + ドット絵 3 案",
    sub=["二章の地上の敵。干潮の砂に潜み、人が真上を歩くと砂が盛り上がる (予告)。飛び出して噛む。満潮では出ない。HP 16、飛びかかり 4。",
         "潜っている間は体が砂の下 (見えない)。予告は砂の盛り上がりと粒。左の黄の目盛りは人 (1.8 ブロック)。"],
    labels=["A 砂鰻", "B 砂蟹", "C 星見魚"],
    rows=[("前 3/4", "idle", 212, 16, 300, (0, 4, 0), 13.0),
          ("横", "idle", 270, 6, 300, (0, 4, 0), 13.0),
          ("上から (砂から出たとき最初に見える面)", "idle", 200, 60, 300, (0, 4, 0), 13.0),
          ("飛びかかり (口を開けて前へ反る)", "leap", 230, 10, 340, (0, 6, -2), 12.0),
          ("後ろ 3/4", "idle", 32, 16, 300, (0, 4, 0), 13.0)],
    options={
        "a": dict(name="A 砂鰻", desc="砂色の細長い鰻。背の暗い斑、珊瑚色の顎と白い牙、尾に珊瑚色の背びれ。飛び出す軌跡が長く、跳ねる動きが一番読める。尾から砂へ潜る。"),
        "b": dict(name="B 砂蟹", desc="丸い砂の甲羅に青緑の筋、羽のような触角、前の短い鋏。甲羅が砂から少し出ても砂の盛り上がりに見える。噛むより挟む見た目。横歩きで戻る。"),
        "c": dict(name="C 星見魚", desc="平たく広い頭が上を向き、目と大きな口が空を見る (本物の星見魚は砂に潜って真上の獲物を待つ)。口の縁に珊瑚の房。真上を歩くと食われる、という読みが形そのもの。")},
    extra="二章の言葉 (砂・珊瑚・青緑・白)。骨 7 本 (root > body > head > jaw / tail / fin_l / fin_r)。潜る・出るは体ごと上下 (アニメの position) で、潜っている間は描かない。",
    budget="地上の小物 300〜500", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
