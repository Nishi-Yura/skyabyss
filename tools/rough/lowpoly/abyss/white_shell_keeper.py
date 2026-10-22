"""白い貝守 (white_shell_keeper) 低ポリ ラフ 3 案。塩の層の硬い敵。殻にこもって待ち、殻の口が開いたときだけ打てる。
A 巻貝 (螺旋の殻を背負い、殻の口から鋏が出る) / B 二枚貝 (二枚の殻を蝶番で開き、中に身がのぞく) / C 亀の甲 (丸い甲に四本の脚と頭)
骨: root > shell / body / claw_l / claw_r。殻が閉じているときは身を殻の中に沈める。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "shell": ("root", (0, 2.0, 0.0)), "body": ("shell", (0, 2.0, -0.6)),
         "claw_l": ("body", (-2.6, 1.4, -2.6)), "claw_r": ("body", (2.6, 1.4, -2.6))}
PAL = make_pal({
    "salt": (SALT, 0.7, dict(crack=0.16, noise=0.2)),
    "saltc": (SALTC, 0.6, dict(crack=0.2, noise=0.24)),
    "sea": (SEA, 0.55, dict(crack=0.12, noise=0.2)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "glow": ([(70, 120, 130), (120, 176, 180), (180, 220, 220), (230, 250, 248)], 0.8, dict(crack=0.0, noise=0.04, min=2)),
}, glow=["glow"])


def circle(y, rad, cz=0.0, n=14):
    return [np.array([rad * math.cos(2 * math.pi * k / n), y, cz + rad * math.sin(2 * math.pi * k / n)]) for k in range(n)]


def build_a(k):
    """A 巻貝: 円錐の殻 (螺旋の帯を二本重ねる) を背負う。殻の口から鋏が二本、身は殻の中。"""
    F = []
    F += part("shell", loft("shell", "salt", [circle(2.5, 4.2, 0.0), circle(4.8, 3.6, 0.5), circle(6.8, 2.4, 0.9), circle(8.4, 1.2, 1.1)], caps=("saltc", "saltc")))
    F += part("whorl", tube("shell", "saltc", [(-2.0, 3.6, 1.8), (1.4, 5.0, 2.0), (2.8, 6.0, 0.2)], ra=0.5, sides=6, hint=(0, 1, 0)))
    F += part("body", ellipsoid("body", "salt", (0, 1.6, -1.0), (2.6, 2.2, 2.4), segs=10, rings=4))
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"claw_{nm}", tube(f"claw_{nm}", "salt", [(s * 2.6, 1.4, -2.6), (s * 3.6, 0.8, -4.2), (s * 4.2, 1.4, -6.0)], ra=[0.6, 0.5, 0.2], sides=5, hint=(0, 1, 0)))
        F += part(f"claw_tip_{nm}", spike(f"claw_{nm}", "saltc", (s * 4.2, 1.4, -6.0), (s * 0.4, 0.0, -1), 1.6, 0.3, sides=4, rings=2))
    return F, [eye_at(-0.7, 2.2, -3.4, 0.3), eye_at(0.7, 2.2, -3.4, 0.3)]


def build_b(k):
    """B 二枚貝: 二枚の殻 (楕円体の半分ずつ) を背の蝶番で開く。開いた口に身が見える。"""
    F = []
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"half_{nm}", ellipsoid("shell", "salt", (s * 2.9, 3.4, 0.0), (3.0, 3.6, 4.2), segs=14, rings=6))
    F += part("hinge", tube("shell", "saltc", [(0, 0.8, -3.6), (0, 4.2, 0.0), (0, 0.8, 3.6)], ra=0.5, sides=5, hint=(1, 0, 0)))
    F += part("body", ellipsoid("body", "sea", (0, 2.6, -0.4), (2.4, 1.8, 2.2), segs=9, rings=3))
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"claw_{nm}", spike(f"claw_{nm}", "salt", (s * 2.6, 1.4, -2.6), (s * 0.5, 0.0, -1.0), 2.6, 0.45, sides=5, rings=2))
    return F, [eye_at(-0.7, 2.8, -2.8, 0.3), eye_at(0.7, 2.8, -2.8, 0.3)]


def build_c(k):
    """C 亀の甲: 丸い甲 (輪を重ねた丸い甲羅)。甲の下から四本の脚と小さな頭。殻を閉じると甲だけになる。"""
    F = []
    F += part("shell", loft("shell", "salt", [circle(1.0, 5.0, 0.0, 16), circle(3.4, 6.0, 0.0, 16), circle(5.6, 4.6, 0.0, 16), circle(6.8, 2.4, 0.0, 16)], caps=("saltc", "saltc")))
    F += part("seam", tube("shell", "saltc", [(-5.6, 2.8, 0), (0, 6.8, 0), (5.6, 2.8, 0)], ra=0.35, sides=4, hint=(0, 0, 1)))
    F += part("body", ellipsoid("body", "salt", (0, 1.6, -0.6), (2.2, 1.6, 2.2), segs=8, rings=3))
    F += part("head", ellipsoid("body", "saltc", (0, 1.8, -4.0), (1.0, 0.9, 1.2), segs=7, rings=3))
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"claw_{nm}", tube(f"claw_{nm}", "saltc", [(s * 2.6, 1.4, -2.6), (s * 3.4, -0.2, -3.8), (s * 3.6, -0.4, -5.2)], ra=[0.5, 0.4, 0.2], sides=5, hint=(0, 1, 0)))
    return F, [eye_at(-0.5, 2.0, -4.6, 0.25), eye_at(0.5, 2.0, -4.6, 0.25)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "open": {"body": (-15, 0, 0), "claw_l": (-20, 0, 0), "claw_r": (-20, 0, 0)},
}

SPEC = dict(
    id="white_shell_keeper", short="白い貝守", kind="塩の層の敵 3案 (A3)",
    title="白い貝守 (white_shell_keeper) — 殻にこもる塩の貝・硬い・低ポリ + ドット絵 3 案",
    sub=["塩の層の硬い敵。普段は殻にこもって動かず、近づくと殻の口が開く。口が開いている間だけ打てる (閉じている間は硬い)。",
         "このシートは一体を大きく寄せて描いた (ruler は人の目盛り 1.8 ブロック)。殻の縁は白く、遠目には塩の岩に見える。"],
    labels=["A 巻貝", "B 二枚貝", "C 亀の甲"],
    rows=[("前 3/4 (殻にこもる)", "idle", 212, 16, 300, (0, 4.6, 0), 19.0),
          ("横", "idle", 270, 6, 300, (0, 4.6, 0), 19.0),
          ("上から", "idle", 200, 60, 300, (0, 4.6, 0), 19.0),
          ("口が開く (打てる間)", "open", 230, 12, 300, (0, 4.6, 0), 19.0),
          ("後ろ 3/4", "idle", 32, 16, 300, (0, 4.6, 0), 19.0)],
    options={
        "a": dict(name="A 巻貝", desc="円錐の殻を背負い、殻の口から鋏が二本出る。殻の帯が一番「貝」として読める。口が開くと鋏の根が見える。"),
        "b": dict(name="B 二枚貝", desc="二枚の殻を背の蝶番で開く。開いた口の中に身が見え、打てる所がはっきりする。閉じると岩の塊。"),
        "c": dict(name="C 亀の甲", desc="丸い甲の下から四本の脚と小さな頭。殻を閉じると甲だけになり、一番低く、転がっていそうな形。")},
    extra="塩の言葉 (白・塩の皮・海の緑・洞の黒)。骨 5 本 (root > shell / body / claw_l claw_r)。殻の中に身を沈める。",
    budget="硬い中型 300〜450", atlas_h=170, pw=420,
    later="歪み = 殻の螺旋が欠け、中の身が見える (白が褪せて海の緑)。崩れ = 殻が割れて二枚になり、身が剥き出しで動く。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
