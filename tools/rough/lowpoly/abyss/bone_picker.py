"""骨拾い (bone_picker) 低ポリ ラフ 3 案。骨の層の小さな這うもの。骨を拾って背負い、群れで床をさらう。
A 骨の山 (丸い骨の体に拾った骨の束が乗る) / B 頭蓋の甲 (頭蓋の殻と洞の目、骨の細い脚) / C 肋の箱 (方解石の体に小さな肋の箱を背負う)
骨: root > body > head / leg_l1 leg_l2 leg_r1 leg_r2。群れ (6〜10 匹) なので三角形は 200〜300。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

LEGS = [("l", -1, 1), ("l", -1, 2), ("r", 1, 1), ("r", 1, 2)]
LEG_Z = {1: -0.9, 2: 1.2}
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 2.6, 0)), "head": ("body", (0, 2.2, -2.9))}
for nm, s, i in LEGS:
    BONES[f"leg_{nm}{i}"] = ("body", (s * 1.7, 1.6, LEG_Z[i]))
PAL = make_pal({
    "bone": (BONEC, 0.62, dict(crack=0.16, noise=0.24)),
    "calc": (CALC, 0.6, dict(crack=0.14, noise=0.2)),
    "ash": (ASHY, 0.55, dict(crack=0.2, noise=0.26)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "glow": ([(150, 46, 16), (210, 84, 24), (255, 146, 40), (255, 210, 96)], 0.7, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["glow"])


def legs(F, mat_="ash", r=0.42):
    for nm, s, i in LEGS:
        b = f"leg_{nm}{i}"
        zz = LEG_Z[i]
        F += part(b, tube(b, mat_, [(s * 1.7, 1.6, zz), (s * 2.7, 0.9, zz + 0.5), (s * 2.9, 0.0, zz + 0.9)], ra=[r, r * 0.8, r * 0.5], sides=5, hint=(0, 1, 0)))


def build_a(k):
    """A 骨の山: 丸い骨の体に拾った骨の束 (三本) を背に乗せる。束の根元は体に沈める。"""
    F = []
    F += part("body", ellipsoid("body", "bone", (0, 2.6, 0.3), (2.6, 2.0, 3.1), segs=10, rings=5))
    for i, (x, z) in enumerate(((-1.0, -0.6), (0.9, 0.2), (0.0, 1.4))):
        F += part(f"bone{i}", tube("body", "calc", [(x, 3.0, z), (x + 0.5, 4.5, z + 0.5), (x + 1.2, 5.6 - 0.3 * i, z + 1.0)], ra=[0.5, 0.42, 0.24], sides=6, hint=(0, 1, 0)))
    F += part("head", ellipsoid("head", "bone", (0, 2.2, -3.4), (1.5, 1.2, 1.3), segs=8, rings=4))
    for s in (-1, 1):
        F += part(f"palp{s}", spike("head", "calc", (s * 0.8, 1.6, -4.4), (s * 0.4, -0.3, -1), 1.2, 0.22, sides=4, rings=2))
    legs(F)
    return F, [eye_at(-0.6, 2.4, -4.5, 0.4), eye_at(0.6, 2.4, -4.5, 0.4)]


def build_b(k):
    """B 頭蓋の甲: 頭蓋の殻 (丸い骨の甲)。洞の目と、甲の下から出る細い骨の脚。体は小さい。"""
    F = []
    R = [ring(1.0, 1.9, -2.6, 2.6, n=10), ring(2.8, 2.8, -2.9, 2.9, n=10), ring(4.2, 2.2, -1.8, 1.8, n=10), ring(4.9, 1.2, -0.8, 0.8, n=10)]
    F += part("shell", loft("body", "bone", R, caps=("bone", "bone")))
    F += part("ridge", tube("body", "calc", [(0, 5.0, -2.0), (0, 5.4, 0.0), (0, 5.0, 2.0)], ra=[0.28, 0.4, 0.2], sides=4, hint=(1, 0, 0)))
    F += part("head", ellipsoid("head", "bone", (0, 2.4, -3.2), (1.3, 1.0, 1.2), segs=8, rings=4))
    F += part("brow", ellipsoid("head", "calc", (0, 3.1, -3.8), (1.2, 0.4, 0.5), segs=6, rings=2))
    legs(F, mat_="bone", r=0.38)
    return F, [eye_at(-0.55, 2.4, -4.1, 0.38), eye_at(0.55, 2.4, -4.1, 0.38)]


def build_c(k):
    """C 肋の箱: 方解石の体に小さな肋の箱を背負う (弧の肋三本が体の上で止まる)。"""
    F = []
    F += part("body", ellipsoid("body", "calc", (0, 2.6, 0.3), (2.5, 1.9, 3.0), segs=10, rings=5))
    for i, z in enumerate((-1.2, 0.2, 1.6)):
        F += part(f"rib{i}", tube("body", "bone", [(-2.0, 3.2, z), (0.0, 5.0, z), (2.0, 3.2, z)], ra=0.4, sides=5, hint=(0, 0, 1)))
    F += part("spine", tube("body", "bone", [(0, 1.8, -1.6), (0, 5.0, 0.2), (0, 5.0, 2.0)], ra=[0.4, 0.4, 0.3], sides=5, hint=(1, 0, 0)))
    F += part("head", ellipsoid("head", "calc", (0, 2.2, -3.4), (1.4, 1.2, 1.2), segs=8, rings=4))
    F += part("snout", spike("head", "bone", (0, 1.8, -4.2), (0, -0.3, -1), 1.3, 0.3, sides=4, rings=2))
    legs(F, mat_="calc", r=0.4)
    return F, [eye_at(-0.55, 2.4, -4.2, 0.36), eye_at(0.55, 2.4, -4.2, 0.36)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "carry": {"body": (6, 0, 0), "leg_l1": (0, 25, 0), "leg_r1": (0, 25, 0), "leg_l2": (0, -20, 0), "leg_r2": (0, -20, 0)},
}

SPEC = dict(
    id="bone_picker", short="骨拾い", kind="骨の層の敵 3案 (A3)",
    title="骨拾い (bone_picker) — 骨を拾って背負う小さな這うもの・低ポリ + ドット絵 3 案",
    sub=["骨の層の小さな敵。群れ (6〜10 匹) で床をさらい、骨を拾って背に積む。拾い終えた一匹は骨を揺らして音を立てる。",
         "叩けば一撃で、死ぬと背の骨が床に散る (拾い直せる)。このシートは群れの一匹を大きく寄せて描いた (ruler は人の目盛り 1.8 ブロック)。"],
    labels=["A 骨の山", "B 頭蓋の甲", "C 肋の箱"],
    rows=[("前 3/4", "idle", 212, 18, 300, (0, 2.8, 0), 26.0),
          ("横", "idle", 270, 6, 300, (0, 2.8, 0), 26.0),
          ("上から (群れで見る面)", "idle", 200, 60, 300, (0, 2.8, 0), 26.0),
          ("拾って歩く", "carry", 230, 14, 300, (0, 2.8, 0), 26.0),
          ("後ろ 3/4", "idle", 32, 16, 300, (0, 2.8, 0), 26.0)],
    options={
        "a": dict(name="A 骨の山", desc="丸い骨の体に拾った骨の束を三本背負う。歩くたびに束が揺れ、骨が鳴る。群れの中で一番「拾っている」姿が伝わる。"),
        "b": dict(name="B 頭蓋の甲", desc="頭蓋の殻を被った形。甲の縁に骨の稜、洞の目。遠目には頭蓋が歩く形で、群れの見分けがつきやすい。"),
        "c": dict(name="C 肋の箱", desc="方解石の体に小さな肋の箱を背負う。箱の中は空で、拾ったものを入れる形が見える。群れが一斉にさらう絵が描きやすい。")},
    extra="骨の言葉 (骨・方解石・灰)。骨 7 本 (root > body > head / leg_l1 leg_l2 leg_r1 leg_r2)。群れなので一匹は小さい (幅 0.5 ブロック)。",
    budget="群れの小物 200〜300 (一匹)", atlas_h=140, pw=420,
    later="歪み = 背の骨が増え、骨の束が体を覆う (洞の目が大きく)。崩れ = 骨が抜けて体の殻が割れ、脚だけが残る。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
