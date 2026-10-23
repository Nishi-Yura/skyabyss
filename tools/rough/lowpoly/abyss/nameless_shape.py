"""名のない形 (nameless_shape) 低ポリ ラフ 3 案。深み (クリア後) の一番抽象的な敵。顔も手も持たない。見ると名前が出てこない形。
A 折れた殻 (黒い破片が中心の虚を囲んで回る。破片の面が鏡のように反る) / B 輪の結び (輪が一つ、ねじれて自分をくぐる。息をするように縮む) / C 垂れた布 (床へ垂れた布の塊。たたまれた一か所に細い切れ目の目)
骨: root > core (A: shard_* を別骨にせず core に沈める) / ring (B) / cloth (C)。大きさは人の 1.5 倍を目安。三角形は 400〜600 が目安。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck_ab import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "core": ("root", (0, 12.0, 0)), "ring": ("root", (0, 14.0, 0)),
         "cloth": ("root", (0, 4.0, 0)), "shard": ("core", (0, 12.0, 0)), "hem": ("root", (0, 0, 0))}
PAL = {"mats": {
    "void": mat(VOID, 0.45, crack=0.0, noise=0.12),
    "slate": mat(SLATE, 0.5, crack=0.0, noise=0.14),
    "bone": mat(BONEW, 0.55, crack=0.0, noise=0.14),
    "teal": mat(TEAL, 0.6, crack=0.0, noise=0.12),
}, "glow_mats": ["teal"]}


def shard(bone, m, c, d, L, r, sides=5):
    """中心 c から d の向きへ伸びる尖った破片 (根元に板、先は一点)。"""
    return cone(bone, m, c, d, L, r, sides=sides, rings=3)


def build_a(k):
    """A 折れた殻: 虚の黒の中心の周りに、石板の破片が六つ、反った方向へ向いて回る。中心は見えない。"""
    F = []
    F += part("core", ellipsoid("core", "void", (0, 12.0, 0), (3.2, 4.2, 3.2), segs=10, rings=4))
    dirs = [(1, 0.4, 0.1), (-0.6, 0.9, 0.5), (0.2, -0.5, 1.0), (-1.0, 0.1, -0.4), (0.5, 1.0, -0.6), (-0.3, -0.9, -0.3)]
    for i, (x, y, z) in enumerate(dirs):
        m = "slate" if i % 2 == 0 else "bone"
        c = (x * 2.2, 12.0 + y * 2.8, z * 2.2)
        # 欠片は輪の骨 (ring) に載せて、核のまわりを回す (2026-10-23: 前は核に沈めていて、待機でも撃つ時も動かなかった)
        F += part(f"shard{i}", shard("ring", m, c, (x, y, z), 5.0, 1.8, sides=6))
    F += part("eye", ellipsoid("core", "teal", (0, 12.0, -2.9), (0.7, 0.7, 0.4), segs=6, rings=2))
    return F, [eye(0, 12.0, -3.5, 0.35)]


def knot_rings(M=28, N=10, scale=1.5, cy=14.0):
    """三葉結び (トレフォイル) に沿って、断面の輪を並べる。輪は接線に垂直。最後の輪は最初と同じ (閉じた輪)。"""
    P = []
    for j in range(M + 1):
        t = 2 * math.pi * (j % M) / M
        P.append(V_(math.sin(t) + 2 * math.sin(2 * t), math.cos(t) - 2 * math.cos(2 * t), -math.sin(3 * t)) * scale + V_(0, cy, 0))
    rings = []
    for j in range(M + 1):
        jj = j % M
        T = P[(jj + 1) % M] - P[(jj - 1) % M]
        T = T / np.linalg.norm(T)
        up = V_(0, 0, 1.0) if abs(T[1]) > 0.9 else V_(0, 1.0, 0)
        Nn = np.cross(T, up)
        Nn = Nn / np.linalg.norm(Nn)
        B = np.cross(T, Nn)
        rr = 0.95 + 0.25 * math.sin(3 * 2 * math.pi * jj / M)
        pts = []
        for i in range(N):
            a = 2 * math.pi * i / N
            pts.append(P[jj] + rr * (math.cos(a) * Nn + math.sin(a) * B))
        rings.append(pts)
    return rings


def build_b(k):
    """B 輪の結び: 三葉結びの太い紐が一本、自分をくぐる。息をするように太さが変わる。顔はなく、目もない。"""
    F = []
    rings = knot_rings()
    faces = []
    for j in range(len(rings) - 1):
        faces += loft("ring", "void", [rings[j], rings[j + 1]], caps=(False, False))
    F += part("ring", faces)
    return F, []


def build_c(k):
    """C 垂れた布: 床へ垂れた布の塊。たたまれた一か所に細い切れ目の目。布の端は床に広がる。"""
    F = []
    R = [ring(0.6, 5.0, -4.0, 4.0, n=16, cx=0), ring(3.6, 4.4, -3.4, 3.4, n=16), ring(8.0, 3.4, -2.6, 2.6, n=16),
         ring(12.0, 2.6, -2.2, 2.2, n=16), ring(15.5, 2.4, -2.0, 2.0, n=16)]
    F += part("cloth", loft("cloth", "slate", R, caps=("void", "void")))
    F += part("fold", tube("cloth", "void", [(-2.4, 9.0, -2.4), (0.0, 6.0, -3.6), (2.4, 9.0, -2.4)], ra=[0.8, 0.9, 0.8], sides=6, caps=(True, True)))
    F += part("slit", ellipsoid("cloth", "teal", (0, 15.0, -2.0), (1.0, 0.18, 0.2), segs=5, rings=1))
    F += part("hem_l", tube("hem", "void", [(-4.6, 0.6, 1.6), (-6.6, 0.5, 3.6)], ra=[1.5, 1.1], sides=6, caps=(True, True)))
    F += part("hem_r", tube("hem", "void", [(4.6, 0.6, 1.4), (6.8, 0.5, 3.4)], ra=[1.5, 1.1], sides=6, caps=(True, True)))
    return F, [eye(-0.5, 15.0, -2.3, 0.2), eye(0.5, 15.0, -2.3, 0.2)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {"idle": {}, "turn": {"core": (0, 40, 0), "ring": (0, -30, 0), "cloth": (0, 20, 0)}}

SPEC = dict(
    id="nameless_shape", short="名のない形", kind="深み専用の敵 3案 (抽象)",
    title="名のない形 (nameless_shape) — 名前の出ない形。深みの一番抽象的な敵・低ポリ 3 案",
    sub=["深み (クリア後) の抽象的な敵。顔も手もなく、見ると名前が出てこない。近づくと体の向きがこちらへ回る。",
         "左の目盛りは人 (1.8 ブロック)。虚の黒・石板の青灰・骨の白だけで描く。"],
    labels=["A 折れた殻", "B 輪の結び", "C 垂れた布"],
    poses=POSES,
    options={
        "a": dict(name="A 折れた殻", desc="黒い中心を、反った石板と骨の破片が六つ囲んで回る。破片の向きが少しずつずれて、見るたびに形が違って見える。中心の目は一つだけ。"),
        "b": dict(name="B 輪の結び", desc="太い輪が一つ、ねじれて自分をくぐる。息をするように輪の太さが変わり、中央の穴の奥に青緑の光が一つ。輪の向きはどの角度から見ても終わりが見えない。"),
        "c": dict(name="C 垂れた布", desc="床へ垂れた布の塊。たたまれた一か所に細い切れ目の目、布の端は床に広がる。人のような大きさだが、人の形には見えない。"),
    },
    extra="深み専用の抽象的な敵。骨 5 本 (root > core / ring / cloth、core > shard)。顔・手・足を持たない。",
    budget="人型に準じる 400〜600",
    pw=380, atlas_h=170,
)
ROWS = [("前 3/4", "idle", 212, 18, 260, (0, 10, 0), 12.0, None),
        ("横", "idle", 270, 6, 260, (0, 10, 0), 12.0, None),
        ("上から", "idle", 200, 60, 260, (0, 10, 0), 12.0, None),
        ("向きが回る", "turn", 212, 18, 260, (0, 10, 0), 12.0, None)]
SPEC["rows"] = ROWS

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, ROWS, hint=128)
