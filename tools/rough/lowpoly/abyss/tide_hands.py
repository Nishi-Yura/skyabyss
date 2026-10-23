"""潮だまりの手 (tide_hands) 低ポリ ラフ 3 案。塩の層の床から伸びる手。踏むと足を掴んで止める (足止め)。
A 五指の手 (床の平たい溜まりから四本の腕、先に五指) / B 貝殻の手 (腕に貝の板の籠手、指は短い) / C 海藻の手 (細く長い指、海藻の紐が垂れる)
骨: root > pool / a1 a2 a3 a4 (腕は床の溜まりに沈める)。動くと四本が順に床から出る。
"""
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

ARMS = [("a1", -2.4, 0.0, -1), ("a2", 2.0, 0.8, 1), ("a3", -0.8, -2.0, 1), ("a4", 2.8, -1.6, -1)]
BONES = {"root": (None, (0, 0, 0)), "pool": ("root", (0, 0.0, 0))}
for nm, x, z, ang in ARMS:
    BONES[nm] = ("pool", (x, 0.0, z))
PAL = make_pal({
    "salt": (SALT, 0.7, dict(crack=0.18, noise=0.22)),
    "saltc": (SALTC, 0.6, dict(crack=0.22, noise=0.24)),
    "sea": (SEA, 0.55, dict(crack=0.14, noise=0.22)),
    "oxcu": (OXCU, 0.55, dict(crack=0.12, noise=0.2)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "glow": ([(70, 120, 130), (120, 176, 180), (180, 220, 220), (230, 250, 248)], 0.8, dict(crack=0.0, noise=0.04, min=2)),
}, glow=["glow"])


def pool(F, m="sea"):
    F += part("pool", ellipsoid("pool", m, (0, -0.5, 0), (5.0, 1.1, 4.4), segs=12, rings=4))


def arm(F, nm, x, z, ang, m, fingers=5, glove=None, flen=2.4, thin=False):
    """一本の腕: 溜まりの中の点 (x, z) から立ち上がり、先に手と指。座標は作図空間の絶対値 (pivot = (x,0,z))。"""
    def P(dx, dy, dz):
        return (x + dx, dy, z + dz)
    r = 0.85 if not thin else 0.55
    F += part(nm, tube(nm, m, [P(0, 0.0, 0), P(0.5 * ang, 3.6, 0.2), P(0.9 * ang, 6.6, 0.4)], ra=[r, r * 0.92, r * 0.78], sides=7, hint=(1, 0, 0)))
    F += part(nm + "_palm", ellipsoid(nm, m, P(0.9 * ang, 7.2, 0.4), (r * 1.8, 1.0 if not thin else 0.7, 1.4), segs=8, rings=3))
    for i in range(fingers):
        t = (i - (fingers - 1) / 2) * 0.55
        F += part(f"{nm}_f{i}", spike(nm, m, P(0.9 * ang + t * 0.8, 7.2, 0.4 + t * 0.5), (t * 0.4, 0.25, -1.0), flen * (0.8 if i in (0, fingers - 1) else 1.0), 0.28 if not thin else 0.18, sides=4, rings=2))
    if glove:
        def circle(y, rad):
            return [P(rad * math.cos(a), y, rad * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 9)[:-1]]
        F += part(nm + "_glove", loft(nm, glove, [circle(3.6, r + 0.2), circle(5.2, r + 0.4), circle(6.4, r + 0.3)], caps=(glove, glove)))


def build_a(k):
    """A 五指の手: 床の平たい溜まりから四本の腕、先に五指。指は長さをそろえた尖り。"""
    F = []
    pool(F)
    for nm, x, z, ang in ARMS:
        arm(F, nm, x, z, ang, "salt", fingers=5)
    return F, []


def build_b(k):
    """B 貝殻の手 (2026-10-23 作り直し): 溜まりの縁に塩の殻がこびりつき、四本の腕が外へ反ってから肘で内へ折れ、
    貝の板を三枚重ねた籠手の先で、節のある四指と親指が溜まりの真ん中へ鉤のように曲がる。腕ごとに高さと反りを変える。"""
    F = []
    pool(F, "sea")
    # 溜まりの縁の塩の殻 (縁に沈めた小さな塊。間隔と大きさを揃えない)
    for i in range(9):
        a = 2 * math.pi * (i + 0.37 * (i % 3)) / 9
        rr = 0.55 + 0.25 * ((i * 7) % 3) / 2
        F += part(f"rim{i}", ellipsoid("pool", "saltc" if i % 2 else "salt", (4.5 * math.cos(a), 0.15, 3.9 * math.sin(a)),
                                       (rr * 1.3, rr * 0.7, rr), segs=5, rings=1))
    # 溜まりの水面 (少し暗い内側の円盤)
    F += part("water", ellipsoid("pool", "hollow", (0, 0.35, 0), (3.6, 0.25, 3.1), segs=8, rings=1))
    H = {"a1": 8.2, "a2": 7.4, "a3": 8.8, "a4": 6.8}
    for nm, x, z, ang in ARMS:
        arm_b(F, nm, x, z, H[nm])
    return F, []


def arm_b(F, nm, x, z, h):
    """B の腕一本。根元 (x,0,z) から外へ反って立ち上がり、肘で内へ折れ、手首から先は溜まりの中心へ向く。"""
    c = np.array([0.0, 0.0, 0.0])
    base = np.array([x, 0.0, z])
    out = base - c
    out[1] = 0
    out = out / (np.linalg.norm(out) or 1)
    inn = -out
    up = np.array([0.0, 1.0, 0.0])
    side = np.cross(up, out)

    def P(o, y, s=0.0):
        return tuple(base + out * o + up * y + side * s)

    elbow = P(1.5, h * 0.5)
    wrist = P(1.15, h * 0.86)
    path = [P(0, -0.6), P(0.8, h * 0.22), elbow, P(1.35, h * 0.7), wrist]
    F += part(nm, tube(nm, "salt", path, ra=[0.95, 0.82, 0.74, 0.66, 0.58], sides=7, hint=tuple(side), caps=(True, True)))
    # 肘の節 (外へ少し張る)
    F += part(nm + "_elbow", ellipsoid(nm, "saltc", elbow, (0.82, 0.8, 0.82), segs=6, rings=1))
    # 貝の板の籠手: 手首へ向かって開く三枚 (それぞれ前の板にかぶさる)
    w = np.array(wrist)
    e = np.array(elbow)
    ax = (w - e) / np.linalg.norm(w - e)
    a1 = np.cross(ax, side)
    a1 = a1 / np.linalg.norm(a1)
    a2 = np.cross(ax, a1)
    for j, (t, r0, r1, ln) in enumerate(((0.1, 0.78, 1.05, 0.95), (0.5, 0.72, 1.1, 0.9))):
        c0 = e + (w - e) * t
        rings = []
        for tt, rr in ((0.0, r0), (0.55, (r0 + r1) / 2), (1.0, r1)):
            cc = c0 + ax * ln * tt
            rings.append([tuple(cc + rr * (math.cos(th) * a1 + math.sin(th) * a2) * (1.0 if k % 2 == 0 else 0.86))
                          for k, th in enumerate(np.linspace(0, 2 * math.pi, 8)[:-1])])
        F += part(f"{nm}_shell{j}", loft(nm, "saltc", rings, caps=("saltc", "saltc")))
    # 手のひら: 中心へ向けた平たい塊
    palm_c = w + ax * 0.75 + inn * 0.15
    F += part(nm + "_palm", ellipsoid(nm, "salt", tuple(palm_c), (1.0, 0.6, 1.0), segs=6, rings=2))
    # 指: 根元 → 第二関節 → 第三関節 → 先 (中心へ鉤に曲がって下を向く)。四指 + 親指
    for i, sp in enumerate((-0.75, -0.25, 0.25, 0.75)):
        root = palm_c + side * sp + inn * 0.55 + up * 0.25
        ln = 1.0 if i in (1, 2) else 0.82
        k1 = root + inn * ln * 0.45 + up * ln * 0.95
        k2 = k1 + inn * ln * 0.6 + up * ln * 0.45
        tip = k2 + inn * ln * 0.55 - up * ln * 0.3
        F += part(f"{nm}_f{i}", tube(nm, "salt", [tuple(root), tuple(k1), tuple(k2), tuple(tip)], ra=[0.27, 0.24, 0.2, 0.0],
                                     sides=4, hint=tuple(side), caps=(True, False)))
        F += part(f"{nm}_k{i}", ellipsoid(nm, "saltc", tuple(k1), (0.3, 0.3, 0.3), segs=4, rings=1))
    th = palm_c - side * 1.05 + inn * 0.1
    t1 = th + inn * 0.6 - side * 0.3 + up * 0.55
    t2 = t1 + inn * 0.55 + up * 0.1
    F += part(f"{nm}_thumb", tube(nm, "salt", [tuple(th), tuple(t1), tuple(t2)], ra=[0.3, 0.24, 0.0], sides=5, hint=tuple(up), caps=(True, False)))


def build_c(k):
    """C 海藻の手: 細く長い指。海藻の紐が手首から垂れ、溜まりの緑と揃う。"""
    F = []
    pool(F, "sea")
    for nm, x, z, ang in ARMS:
        arm(F, nm, x, z, ang, "oxcu", fingers=4, flen=3.2, thin=True)
        F += part(nm + "_weed", tube(nm, "sea", [(x + 0.9 * ang, 6.2, z + 0.4), (x + 0.9 * ang + 1.0, 3.8, z + 0.6), (x + 0.9 * ang + 1.4, 1.2, z + 0.8)], ra=[0.2, 0.26, 0.06], sides=4, hint=(0, 0, 1)))
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "grab": {"a1": (0, 0, -12), "a2": (0, 0, 14), "a3": (0, 0, -10), "a4": (0, 0, 12)},
}

SPEC = dict(
    id="tide_hands", short="潮だまりの手", kind="塩の層の敵 3案 (A3)",
    title="潮だまりの手 (tide_hands) — 床から伸びる手・足止め・低ポリ + ドット絵 3 案",
    sub=["塩の層の足止め。潮だまりの床から手が伸び、踏んだ人の足を掴む。掴まれると数秒動けない (一本ずつ剥がす)。",
         "床の溜まりは見えるが、手は踏んでから出る。このシートは四本の手を大きく寄せて描いた (ruler は人の目盛り 1.8 ブロック)。"],
    labels=["A 五指の手", "B 貝殻の手", "C 海藻の手"],
    rows=[("前 3/4", "idle", 212, 22, 300, (0, 5.0, 0), 17.0),
          ("横", "idle", 270, 8, 300, (0, 5.0, 0), 17.0),
          ("上から (溜まりの広さ)", "idle", 200, 62, 300, (0, 5.0, 0), 17.0),
          ("掴む (足を捕まえる)", "grab", 230, 16, 300, (0, 5.0, 0), 17.0),
          ("後ろ 3/4", "idle", 32, 20, 300, (0, 5.0, 0), 17.0)],
    options={
        "a": dict(name="A 五指の手", desc="床の溜まりから四本の腕。先に五指の塩の手。指は長さをそろえた尖り。遠目にも手と分かり、掴む形が一番読める。"),
        "b": dict(name="B 貝殻の手", desc="腕に貝の板の籠手。指は短く、手首まで貝に覆われる。掴むよりも押さえる形で、重い足止めに合う。"),
        "c": dict(name="C 海藻の手", desc="細く長い指の手に海藻の紐が垂れる。溜まりの緑と揃い、水の中から伸びる感じが一番出る。ただし細く、群れにすると見えにくい。")},
    extra="塩の言葉 (白・塩の皮・海の緑・酸化した銅)。骨 5 本 (root > pool / a1..a4)。腕は溜まり (床の楕円) に沈める。手と指は腕の子。",
    budget="地上の中型 300〜450 (四本の腕)", atlas_h=140, pw=420,
    later="歪み = 指が増えて関節が二つに曲がる・海藻が濃くなる。崩れ = 腕が抜けて溜まりだけが残り、溜まりの緑が濃く光る。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
