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
         "rib0": ("body", (-2.6, 6.0, 0)), "rib1": ("body", (2.6, 6.0, 0)), "rib2": ("body", (-1.6, 9.5, -1.0)), "rib3": ("body", (1.6, 9.5, -1.0)),
         # 2026-10-23 (B を作り直し): 口の四枚の花弁 (petal_*) と床に根を張る四本の根 (root_*)、膜の下の拍の芯 (core)
         "petal_t": ("body", (0, 6.6, -4.6)), "petal_b": ("body", (0, 3.8, -4.6)), "petal_l": ("body", (1.4, 5.2, -4.6)), "petal_r": ("body", (-1.4, 5.2, -4.6)),
         "root_fl": ("body", (2.4, 2.6, -1.8)), "root_fr": ("body", (-2.4, 2.6, -1.8)), "root_bl": ("body", (2.4, 2.6, 2.0)), "root_br": ("body", (-2.4, 2.6, 2.0)),
         "core": ("body", (0, 5.2, -4.9))}
PAL = {"mats": {
    "flesh": mat([(26, 20, 26), (48, 38, 46), (78, 62, 70), (112, 92, 100)], 0.55, crack=0.0, noise=0.16),
    "flesh_d": mat([(16, 12, 18), (30, 24, 32), (52, 44, 54)], 0.5, crack=0.0, noise=0.12),
    "slate": mat(SLATE, 0.5, crack=0.0, noise=0.12),
    "void": mat(VOID, 0.5, crack=0.0, noise=0.1),
    "bone": mat(BONEW, 0.6, crack=0.0, noise=0.12),
    "teal": mat(TEAL, 0.7, crack=0.0, noise=0.08, min=2),
    "vein": mat([(12, 8, 14), (24, 16, 26), (40, 28, 40)], 0.5, crack=0.0, noise=0.1),
    "lip": mat([(40, 26, 36), (70, 48, 60), (104, 76, 86), (134, 104, 110)], 0.55, crack=0.0, noise=0.14),
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
    """B 膜の袋 (2026-10-23 作り直し。前は袋と三つの膨らみ・細い筒の口だけで、肋の骨が空、黒い塊に見えた):
    膜の袋は少しゆがんだ形で、表に暗い筋 (膜の脈) が走り、背に石板の青灰の板が三枚貼り付く (冷たい石に寄生した肉)。
    三つの膨らみ (bulge_a/b/c) は脈ごと膨らむ。前の口は短い漏斗で、四枚の花弁 (petal_t/b/l/r) が閉じている。
    呼ぶ時は花弁が少し開いて奥の青緑 (喉) が脈を打ち、噛む時は花弁が大きく開いてから閉じる。
    袋の下から四本の根 (root_*) が床へ伸びて体を留める (動かない敵なので、根で「張り付いている」と読める)。"""
    F = []

    def lump_deform(p):
        x, y, z = p
        return np.array([x * (1.0 + 0.06 * math.sin(2.1 * y)), y * (1.0 - 0.05 * math.cos(1.7 * x)), z * (1.0 + 0.05 * math.sin(1.3 * x + y))])

    F += part("body", ellipsoid("body", "flesh", (0, 6.0, 0.2), (4.2, 4.3, 4.0), segs=14, rings=6, deform=lump_deform))
    # 膜の脈 (表に浮く暗い筋)。袋の表面に半ば沈める
    for j, (a0, a1, y0, y1) in enumerate(((-150, -100, 3.0, 9.4), (-30, 20, 2.8, 9.6), (60, 120, 3.2, 9.0), (160, 210, 3.0, 8.8))):
        pts = []
        for t in range(5):
            f = t / 4
            ang = math.radians(a0 + (a1 - a0) * f)
            y = y0 + (y1 - y0) * f
            ry = max(0.15, 1 - ((y - 6.0) / 4.4) ** 2) ** 0.5
            pts.append((4.15 * ry * math.sin(ang), y, 0.2 - 3.95 * ry * math.cos(ang)))
        F += part(f"vein{j}", tube("body", "vein", pts, ra=[0.22, 0.3, 0.3, 0.26, 0.18], sides=4, caps=(True, True)))
    # 背の石板 (冷たい石に貼り付いた面)
    for j, (c, r_, rot_) in enumerate((((-1.6, 8.6, 3.2), (1.8, 1.3, 0.5), (-30, -25, 10)), ((1.9, 7.2, 3.4), (1.6, 1.4, 0.5), (-10, 30, -12)),
                                        ((0.0, 4.6, 3.8), (2.0, 1.1, 0.5), (8, 0, 4)))):
        F += part(f"slab{j}", ellipsoid("body", "slate", c, r_, segs=6, rings=2, rot=rot_xyz(*rot_)))
    # 拍の膨らみ (脈ごと)
    for b_, c, r_ in (("bulge_a", (-3.1, 7.0, -0.2), 1.75), ("bulge_b", (3.1, 7.0, -0.2), 1.75), ("bulge_c", (0, 10.0, 1.0), 1.6)):
        F += part(b_, ellipsoid(b_, "lip", c, (r_, r_, r_), segs=9, rings=3))
        cx, cy, cz = c
        F += part(b_ + "_v", tube(b_, "vein", [(cx - 0.9, cy + 0.9, cz - 0.9), (cx, cy + 0.3, cz - r_ * 0.98), (cx + 0.9, cy - 0.7, cz - 0.9)],
                                  ra=[0.16, 0.22, 0.16], sides=4, caps=(True, True)))
    # 喉の拍の芯 (花弁が開くと見える青緑。光らないが冷たく明るい)
    F += part("core", ellipsoid("core", "teal", (0, 5.2, -4.95), (0.8, 0.8, 0.3), segs=8, rings=2))
    # 口の漏斗 (袋から前へ短く出る)
    F += part("funnel", tube("body", "flesh_d", [(0, 5.2, -2.8), (0, 5.2, -4.0), (0, 5.2, -4.7)], ra=[2.0, 1.85, 1.7], sides=10, hint=(0, 1, 0), caps=(True, True)))
    F += part("throat", ellipsoid("body", "void", (0, 5.2, -4.75), (1.45, 1.45, 0.35), segs=8, rings=2))
    # 四枚の花弁 (閉じた姿で口を覆う。先は内へ曲がる)
    for b_, (ux, uy) in (("petal_t", (0, 1)), ("petal_b", (0, -1)), ("petal_l", (1, 0)), ("petal_r", (-1, 0))):
        base = np.array([ux * 1.4, 5.2 + uy * 1.4, -4.6])
        mid = np.array([ux * 1.0, 5.2 + uy * 1.0, -5.5])
        tip = np.array([ux * 0.25, 5.2 + uy * 0.25, -5.75])
        F += part(b_, tube(b_, "lip", [base, mid, tip], ra=[0.75, 0.6, 0.0], rb=[1.25, 0.95, 0.0], sides=5,
                           hint=(uy, -ux, 0) if ux == 0 else (0, 1, 0), caps=(True, False)))
        F += part(b_ + "_tooth", cone(b_, "bone", (mid[0] * 0.7, 5.2 + (mid[1] - 5.2) * 0.7, -5.5), (-ux, -uy, -0.3), 0.7, 0.18, sides=3, rings=1))
    # 根 (袋の下から床へ。先は床に沈む)
    for b_, sx, sz in (("root_fl", 1, -1), ("root_fr", -1, -1), ("root_bl", 1, 1), ("root_br", -1, 1)):
        p0 = (sx * 2.2, 2.9, sz * 1.8 + 0.2)
        p1 = (sx * 3.9, 1.4, sz * 3.1 + 0.2)
        p2 = (sx * 5.0, 0.1, sz * 3.8 + 0.2)
        F += part(b_, tube(b_, "flesh_d", [p0, p1, p2], ra=[0.85, 0.55, 0.3], sides=5, caps=(True, True)))
        F += part(b_ + "_tip", ellipsoid(b_, "flesh", (sx * 5.1, 0.15, sz * 3.9 + 0.2), (0.55, 0.25, 0.55), segs=6, rings=1))
    F += part("eye_l", ellipsoid("body", "teal", (-1.7, 8.4, -2.85), (0.38, 0.38, 0.3), segs=6, rings=2))
    F += part("eye_r", ellipsoid("body", "teal", (1.7, 8.4, -2.85), (0.38, 0.38, 0.3), segs=6, rings=2))
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
         "open": {"jaw": (-38, 0, 0), "petal_t": (-50, 0, 0), "petal_b": (50, 0, 0), "petal_l": (0, -50, 0), "petal_r": (0, 50, 0)}}

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
    run(SPEC, build, PAL, BONES, ROWS, hint=192)
