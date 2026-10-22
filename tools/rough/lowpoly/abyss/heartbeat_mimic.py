"""鼓動もどき (heartbeat_mimic) 低ポリ ラフ 3 案。深淵だけの敵 (どの層にも出る)。溜まりの間の鼓動の音をまねる肉の塊。近づくと口を開く。
グロにしない: 血の色を出さず、暗い赤紫と石板の青灰で「肉に似た冷たいもの」にする。
A 肉の塊と一つの口 (丸い塊、前面に下唇が開く小さな口。口の中は虚の黒) / B 膜の袋 (膜の袋に三つの膨らみが拍を打つ。前の口は細い筒) / C 骨を抱えた肉 (骨の白の肋が塊から突き出し、その間から口が開く)
骨: root > body > jaw ; body > bulge_a / bulge_b / bulge_c (B のみ) ; body > rib0..rib3 (C のみ)。
人の 0.8 倍から 1 倍。三角形は 200〜400 が目安。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 6.0, 0)), "jaw": ("body", (0, 5.2, -3.4)),
         "bulge_a": ("body", (-3.0, 7.0, 0)), "bulge_b": ("body", (3.0, 7.0, 0)), "bulge_c": ("body", (0, 10.5, 1.0)),
         "rib0": ("body", (-2.6, 6.0, 0)), "rib1": ("body", (2.6, 6.0, 0)), "rib2": ("body", (-1.6, 9.5, -1.0)), "rib3": ("body", (1.6, 9.5, -1.0))}
PAL = {"mats": {
    "flesh": mat([(26, 20, 26), (48, 38, 46), (78, 62, 70), (112, 92, 100)], 0.55, crack=0.0, noise=0.16),
    "flesh_d": mat([(16, 12, 18), (30, 24, 32), (52, 44, 54)], 0.5, crack=0.0, noise=0.12),
    "slate": mat(SLATE, 0.5, crack=0.0, noise=0.12),
    "void": mat(VOID, 0.5, crack=0.0, noise=0.1),
    "bone": mat(BONEW, 0.6, crack=0.0, noise=0.12),
    "teal": mat(TEAL, 0.7, crack=0.0, noise=0.08, min=2),
}, "glow_mats": []}


def build_a(k):
    """A 肉の塊と一つの口: 丸い塊。前面の下唇 (ふたを開ける形) の奥は虚の黒。鼓動の音に合わせて塊が膨らむ。"""
    F = []
    F += part("body", ellipsoid("body", "flesh", (0, 6.0, 0), (4.0, 3.6, 3.8), segs=14, rings=6))
    F += part("mouth", ellipsoid("body", "void", (0, 5.0, -3.6), (2.0, 0.9, 0.5), segs=8, rings=2))
    F += part("lip", ellipsoid("jaw", "flesh_d", (0, 4.3, -3.6), (2.2, 0.5, 0.6), segs=8, rings=1))
    F += part("eye_l", ellipsoid("body", "teal", (-1.8, 8.0, -3.4), (0.35, 0.35, 0.2), segs=4, rings=1))
    F += part("eye_r", ellipsoid("body", "teal", (1.8, 8.0, -3.4), (0.35, 0.35, 0.2), segs=4, rings=1))
    return F, []


def build_b(k):
    """B 膜の袋: 膜の袋に三つの膨らみ。拍は一つずつずれて打つ。前の口は細い筒で、開くと中が見える。"""
    F = []
    F += part("body", ellipsoid("body", "flesh_d", (0, 6.0, 0), (4.2, 4.6, 4.0), segs=14, rings=6))
    F += part("bulge_a", ellipsoid("bulge_a", "flesh", (-3.0, 7.0, 0), (1.6, 1.6, 1.6), segs=7, rings=2))
    F += part("bulge_b", ellipsoid("bulge_b", "flesh", (3.0, 7.0, 0), (1.6, 1.6, 1.6), segs=7, rings=2))
    F += part("bulge_c", ellipsoid("bulge_c", "flesh", (0, 10.5, 1.0), (1.5, 1.5, 1.5), segs=7, rings=2))
    F += part("mouth", tube("jaw", "void", [(0, 5.2, -3.6), (0, 5.2, -4.4), (0, 5.0, -5.0)], ra=[1.2, 1.0, 0.9], sides=7, caps=(True, True)))
    F += part("lip", tube("jaw", "flesh", [(0, 5.2, -3.8), (0, 3.6, -4.2), (0, 2.6, -3.9)], ra=[0.4, 0.45, 0.2], sides=5, caps=(True, True)))
    return F, []


def build_c(k):
    """C 骨を抱えた肉: 塊の背から白い肋が四本突き出る。肋の間の前側に口。肋は塊の中へ沈める。"""
    F = []
    F += part("body", ellipsoid("body", "flesh", (0, 6.0, 0), (3.8, 3.4, 3.6), segs=14, rings=6))
    for i, (b, p0, p1) in enumerate((("rib0", (-2.6, 6.0, 0), (-5.6, 9.0, -1.4)),
                                     ("rib1", (2.6, 6.0, 0), (5.6, 9.0, -1.4)),
                                     ("rib2", (-1.6, 9.5, -1.0), (-3.4, 13.4, -2.2)),
                                     ("rib3", (1.6, 9.5, -1.0), (3.4, 13.4, -2.2)))):
        F += part(f"rib{i}", tube(b, "bone", [p0, p1], ra=[0.95, 0.65], sides=8, caps=(True, True)))
    F += part("mouth", ellipsoid("jaw", "void", (0, 5.0, -3.4), (1.4, 1.0, 0.6), segs=7, rings=2))
    F += part("lip", ellipsoid("jaw", "bone", (0, 3.8, -3.6), (1.6, 0.35, 0.5), segs=7, rings=1))
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "beat": {"bulge_a": (0, 0, 12), "bulge_b": (0, 0, -12), "bulge_c": (-10, 0, 0)},
         "open": {"jaw": (-38, 0, 0)}}

SPEC = dict(
    id="heartbeat_mimic", short="鼓動もどき", kind="深淵だけの敵 3案 (肉の塊)",
    title="鼓動もどき (heartbeat_mimic) — 鼓動をまねる肉の塊・低ポリ 3 案",
    sub=["深淵だけ (どの層にも出る)。溜まりの間の鼓動の音をまねる。血の色は出さず、暗い赤紫と石板の青灰だけ。",
         "左の目盛りは人 (1.8 ブロック)。「開く」行は近づいた時の口の姿。"],
    labels=["A 肉の塊と一つの口", "B 膜の袋", "C 骨を抱えた肉"],
    poses=POSES,
    options={
        "a": dict(name="A 肉の塊と一つの口", desc="丸い塊に前の下唇だけが開く。口の奥は虚の黒、目は青緑の点が二つ。鼓動の音に合わせて塊が膨らむ。一番小さく、足元の塊として見落としやすい。"),
        "b": dict(name="B 膜の袋", desc="膜の袋に三つの膨らみ。拍は一つずつずれて打ち、膨らみの動きで鼓動の音と合っているのが分かる。前の口は細い筒で、近づくと開いて中が見える。"),
        "c": dict(name="C 骨を抱えた肉", desc="塊の背から骨の白の肋が四本突き出る。肋の間の前側に口。骨の白で、肉の塊だと分かる前に骨の形で目を引く。見ても怖さが残るよう、血の色は使わない。"),
    },
    extra="深淵だけの敵。骨 10 本 (root > body > jaw、body > bulge_a / bulge_b / bulge_c (B)、body > rib0..rib3 (C))。鍵は口と鼓動 (拍の動き)。",
    budget="単体の敵 200〜400",
    pw=380, atlas_h=170,
)
ROWS = [("前 3/4", "idle", 212, 18, 260, (0, 6, 0), 19.0, None),
        ("横", "idle", 270, 6, 260, (0, 6, 0), 19.0, None),
        ("拍 (鼓動の音で膨らむ)", "beat", 212, 18, 260, (0, 6, 0), 19.0, None),
        ("近づいた時 (口が開く)", "open", 212, 18, 260, (0, 6, 0), 19.0, None)]
SPEC["rows"] = ROWS

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, ROWS, hint=96)
