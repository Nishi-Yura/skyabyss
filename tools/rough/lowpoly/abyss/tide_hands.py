"""潮だまりの手 (tide_hands) 低ポリ ラフ 3 案。塩の層の床から伸びる手。踏むと足を掴んで止める (足止め)。
A 五指の手 (床の平たい溜まりから四本の腕、先に五指) / B 貝殻の手 (腕に貝の板の籠手、指は短い) / C 海藻の手 (細く長い指、海藻の紐が垂れる)
骨: root > pool / arm1 arm2 arm3 arm4 (腕は床の溜まりに沈める)。動くと四本が順に床から出る。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

ARMS = [("a1", -2.2, 0.0, 0.2), ("a2", 1.8, 0.8, -0.6), ("a3", -0.6, -1.8, 1.0), ("a4", 2.6, -1.4, 1.4)]
BONES = {"root": (None, (0, 0, 0)), "pool": ("root", (0, 0.0, 0))}
for nm, x, z, ang in ARMS:
    BONES[nm] = ("pool", (x, 0.0, z))
PAL = make_pal({
    "salt": (SALT, 0.7, dict(crack=0.05, noise=0.1)),
    "saltc": (SALTC, 0.6, dict(crack=0.1, noise=0.16)),
    "sea": (SEA, 0.55, dict(crack=0.0, noise=0.1)),
    "oxcu": (OXCU, 0.55, dict(crack=0.0, noise=0.1)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "glow": ([(70, 120, 130), (120, 176, 180), (180, 220, 220), (230, 250, 248)], 0.8, dict(crack=0.0, noise=0.04, min=2)),
}, glow=["glow"])


def pool(F, m="sea"):
    F += part("pool", ellipsoid("pool", m, (0, -0.4, 0), (4.2, 0.9, 3.6), segs=8, rings=2))


def arm(F, nm, x, z, ang, m, fingers=5, glove=None, flen=2.0, thin=False):
    """一本の腕: 床の溜まりの中の点 (x, z) から立ち上がり、先に手と指。指の根は手に沈める。座標は作図空間 (腕の骨の pivot は (x,0,z))。"""
    def P(dx, dy, dz):
        return (x + dx, dy, z + dz)
    r = 0.55 if not thin else 0.35
    F += part(nm, tube(nm, m, [P(0, 0.0, 0), P(0.5 * ang, 3.4, 0.2), P(0.9 * ang, 6.2, 0.4)], ra=[r, r * 0.9, r * 0.7], sides=5, hint=(1, 0, 0)))
    F += part(nm + "_palm", ellipsoid(nm, m, P(0.9 * ang, 6.8, 0.4), (r * 1.7, 0.9 if not thin else 0.6, 1.2), segs=5, rings=1))
    for i in range(fingers):
        t = (i - (fingers - 1) / 2) * 0.45
        F += part(f"{nm}_f{i}", spike(nm, m, P(0.9 * ang + t * 0.7, 6.8, 0.4 + t * 0.4), (t * 0.4, 0.2, -1.0), flen * (0.8 if i in (0, fingers - 1) else 1.0), 0.2 if not thin else 0.13, sides=3, rings=1))
    if glove:
        def circle(y, rad):
            return [P(rad * math.cos(a), y, rad * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 7)[:-1]]
        F += part(nm + "_glove", loft(nm, glove, [circle(3.4, r + 0.1), circle(4.8, r + 0.25)], caps=(glove, glove)))


def build_a(k):
    """A 五指の手: 床の平たい溜まりから四本の腕、先に五指。指は長さをそろえた尖り。"""
    F = []
    pool(F)
    for nm, x, z, ang in ARMS:
        arm(F, nm, x, z, ang, "salt", fingers=5)
    return F, []


def build_b(k):
    """B 貝殻の手: 腕に貝の板の籠手 (手首まで)。指は短い。貝の縁が内側へ沈む。"""
    F = []
    pool(F, "sea")
    for nm, x, z, ang in ARMS:
        arm(F, nm, x, z, ang, "salt", fingers=3, glove="saltc", flen=1.2)
    return F, []


def build_c(k):
    """C 海藻の手: 細く長い指。海藻の紐が手首から垂れ、溜まりの緑と揃う。"""
    F = []
    pool(F, "sea")
    for nm, x, z, ang in ARMS:
        arm(F, nm, x, z, ang, "oxcu", fingers=4, flen=2.6, thin=True)
        F += part(nm + "_weed", tube(nm, "sea", [(x + 0.9 * ang, 6.2, z + 0.4), (x + 0.9 * ang + 0.8, 3.8, z + 0.6), (x + 0.9 * ang + 1.2, 1.4, z + 0.8)], ra=[0.14, 0.18, 0.04], sides=3, hint=(0, 0, 1)))
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "grab": {"a1": (0, 0, -12), "a2": (0, 0, 14), "a3": (0, 0, -10), "a4": (0, 0, 12), "pool": (0, 0, 0)},
}

SPEC = dict(
    id="tide_hands", short="潮だまりの手", kind="塩の層の敵 3案 (A3)",
    title="潮だまりの手 (tide_hands) — 床から伸びる手・足止め・低ポリ + ドット絵 3 案",
    sub=["塩の層の足止め。潮だまりの床から手が伸び、踏んだ人の足を掴む。掴まれると数秒動けない (一本ずつ剥がす)。",
         "床の溜まりは見えるが、手は踏んでから出る。左の赤の目盛りは人 (1.8 ブロック)。"],
    labels=["A 五指の手", "B 貝殻の手", "C 海藻の手"],
    rows=[("前 3/4", "idle", 212, 22, 260, (0, 5, 0), 26.0),
          ("横", "idle", 270, 8, 260, (0, 5, 0), 26.0),
          ("上から (溜まりの広さ)", "idle", 200, 62, 260, (0, 5, 0), 26.0),
          ("掴む (足を捕まえる)", "grab", 230, 16, 260, (0, 5, 0), 26.0),
          ("後ろ 3/4", "idle", 32, 20, 260, (0, 5, 0), 26.0)],
    options={
        "a": dict(name="A 五指の手", desc="床の溜まりから四本の腕。先に五指の塩の手。指は長さをそろえた尖り。遠目にも手と分かり、掴む形が一番読める。"),
        "b": dict(name="B 貝殻の手", desc="腕に貝の板の籠手。指は短く、手首まで貝に覆われる。掴むよりも押さえる形で、重い足止めに合う。"),
        "c": dict(name="C 海藻の手", desc="細く長い指の手に海藻の紐が垂れる。溜まりの緑と揃い、水の中から伸びる感じが一番出る。ただし一番細く、群れにすると見えにくい。")},
    extra="塩の言葉 (白・塩の皮・海の緑・酸化した銅)。骨 5 本 (root > pool / a1..a4)。腕は溜まり (床の楕円) に沈める。手と指は腕の子。",
    budget="地上の小物 300〜500 (四本の腕)", atlas_h=140, pw=420,
    later="歪み = 指が増えて関節が二つに曲がる・海藻が濃くなる。崩れ = 腕が抜けて溜まりだけが残り、溜まりの緑が濃く光る。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=64)
