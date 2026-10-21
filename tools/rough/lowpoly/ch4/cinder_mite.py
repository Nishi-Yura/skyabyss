"""熾り蟲 (cinder_mite) 低ポリ ラフ 3 案。熱に寄る群れの予習。HP 6、触れて 3。3〜5 匹の群れで、温石を持つ人・火・温かい岩へ寄る。叩けば一撃。
A 熾の粒虫 (丸い炭の背に熾のひび、短い 6 脚) / B 火打ちの甲虫 (平たい黒石の甲、前に火打ちの大顎、背に熾の筋) / C 灰の蚤 (灰色の細い体、太い後ろ脚、熾の尾の玉)
骨: root > body > head / tail / leg_l1..3 / leg_r1..3。歩く = 脚を交互に振る。寄る (熱へ駆ける) = 体を低くして速く。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck4 import *  # noqa

LEGS = [("l", -1, 1), ("l", -1, 2), ("l", -1, 3), ("r", 1, 1), ("r", 1, 2), ("r", 1, 3)]
LEG_Z = {1: -1.6, 2: 0.0, 3: 1.6}
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 1.6, 0)), "head": ("body", (0, 1.8, -2.4)), "tail": ("body", (0, 1.8, 2.2))}
for nm, s, i in LEGS:
    BONES[f"leg_{nm}{i}"] = ("body", (s * 1.6, 1.4, LEG_Z[i]))
PAL = {"mats": {
    "char": mat(CHAR, 0.55, crack=0.0, noise=0.18),
    "ash": mat(ASH, 0.6, crack=0.06, noise=0.2),
    "basalt": mat(BASALT, 0.58, crack=0.1, noise=0.16),
    "ember": mat(EMBER, 0.72, crack=0.0, noise=0.08, min=1),
    "leg": mat(SOOT, 0.5, crack=0.0, noise=0.1),
}, "skull_bone": "none", "face_mat": "none", "glow_mats": ["ember"]}


def eye_at(x, y, z, r=0.35):
    return dict(c=V(x, y, z), rx=r, ry=r, pr=(0.5, 0.5), tilt=0.0, zr=0.6)


def legs(F, mat_="leg", spread=1.3, drop=1.4, r=0.22, back=0.0):
    for nm, s, i in LEGS:
        b = f"leg_{nm}{i}"
        x0, y0, z0 = s * 1.4, 1.4, LEG_Z[i]
        zz = z0 + (i - 2) * 0.6 + (back if i == 3 else 0.0)
        F += part(b, tube(b, mat_, [(x0, y0, z0), (s * (1.4 + spread), y0 + 0.5, zz), (s * (1.6 + spread * 1.3), y0 - drop, zz + (i - 2) * 0.3)],
                          ra=[r, r * 0.9, 0.06], sides=3, hint=(0, 1, 0)))


def build_a(k):
    """A 熾の粒虫: 丸い炭の背 (ひびに熾がのぞく)、小さな頭と 6 本の短い脚。火の粉が一粒、歩いているように見える。"""
    F = []
    F += part("back", ellipsoid("body", "char", (0, 2.0, 0.2), (1.9, 1.4, 2.3), segs=8, rings=2))
    # 熾のひび (背に沈めた細い管 3 本)
    for i, x in enumerate((-0.8, 0.0, 0.8)):
        F += part(f"crack{i}", tube("body", "ember", [(x * 0.6, 3.25, -1.2), (x, 3.35, 0.2), (x * 0.7, 3.0, 1.6)], ra=[0.12, 0.2, 0.12], sides=3))
    F += part("head", ellipsoid("head", "basalt", (0, 1.8, -2.5), (1.0, 0.8, 0.8), segs=6, rings=1))
    for s in (-1, 1):
        F += part(f"palp{s}", spike("head", "leg", (s * 0.5, 1.6, -3.1), (s * 0.3, -0.2, -1), 0.9, 0.16, sides=3, rings=1))
    F += part("tail", ellipsoid("tail", "ember", (0, 1.8, 2.5), (0.7, 0.6, 0.5), segs=5, rings=1))
    legs(F)
    return F, [eye_at(-0.45, 2.1, -3.1), eye_at(0.45, 2.1, -3.1)]


def build_b(k):
    """B 火打ちの甲虫: 平たい黒石の甲と、前に突き出た火打ちの大顎 (打ち合わせると火の粉)。背の中央に熾の筋。"""
    F = []
    R = [ring(1.0, 1.8, -2.0, 2.0, n=8), ring(1.9, 2.1, -2.4, 2.4, n=8), ring(2.6, 1.3, -1.6, 1.6, n=8)]
    F += part("shell", loft("body", "basalt", R, caps=("char", "basalt")))
    F += part("seam", tube("body", "ember", [(0, 2.62, -1.4), (0, 2.75, 0.0), (0, 2.62, 1.4)], ra=[0.12, 0.2, 0.12], sides=3))
    F += part("head", ellipsoid("head", "char", (0, 1.7, -2.5), (1.1, 0.6, 0.7), segs=6, rings=1))
    for s in (-1, 1):
        F += part(f"mand{s}", tube("head", "ash", [(s * 0.6, 1.6, -2.9), (s * 0.9, 1.6, -3.8), (s * 0.3, 1.6, -4.4)], ra=[0.28, 0.22, 0.05], sides=3, hint=(0, 1, 0)))
        F += part(f"spark{s}", ellipsoid("head", "ember", (s * 0.35, 1.6, -4.3), (0.16, 0.16, 0.16), segs=4, rings=1))
    F += part("tail", spike("tail", "basalt", (0, 1.7, 2.2), (0, -0.2, 1), 1.0, 0.5, sides=4, rings=1))
    legs(F, spread=1.1, drop=1.3)
    return F, [eye_at(-0.55, 1.95, -2.95, 0.3), eye_at(0.55, 1.95, -2.95, 0.3)]


def build_c(k):
    """C 灰の蚤: 灰色の細い体、太い後ろ脚 (跳ぶ予感)、尾の先に熾の玉。小さく、速そうに見える。"""
    F = []
    F += part("thorax", ellipsoid("body", "ash", (0, 2.2, -0.6), (1.2, 1.3, 1.5), segs=6, rings=2))
    F += part("abdomen", ellipsoid("body", "ash", (0, 2.0, 1.2), (1.4, 1.2, 1.6), segs=6, rings=2))
    F += part("band", ellipsoid("body", "char", (0, 2.0, 0.35), (1.25, 1.15, 0.35), segs=6, rings=1))
    F += part("head", ellipsoid("head", "char", (0, 1.9, -2.4), (0.8, 0.8, 0.9), segs=6, rings=1))
    F += part("snout", spike("head", "leg", (0, 1.6, -3.1), (0, -0.4, -1), 1.0, 0.2, sides=3, rings=1))
    F += part("tail", ellipsoid("tail", "ember", (0, 2.2, 3.0), (0.6, 0.6, 0.6), segs=5, rings=1))
    legs(F, spread=1.2, drop=1.5, back=0.6)
    # 太い後ろ脚の腿 (3 本目の脚の付け根に沈める)
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"thigh{nm}", ellipsoid(f"leg_{nm}3", "ash", (s * 2.2, 2.4, 2.0), (0.55, 0.9, 0.7), segs=5, rings=1))
    return F, [eye_at(-0.45, 2.2, -3.0, 0.3), eye_at(0.45, 2.2, -3.0, 0.3)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "rush": {"body": (8, 0, 0), "leg_l1": (0, 25, 0), "leg_r1": (0, 25, 0), "leg_l3": (0, -25, 0), "leg_r3": (0, -25, 0), "leg_l2": (0, -20, 0), "leg_r2": (0, -20, 0)},
}

SPEC = dict(
    id="cinder_mite", short="熾り蟲", kind="地上の敵 3案 (P20)",
    title="熾り蟲 (cinder_mite) — 熱に寄る小さな群れ・低ポリ + ドット絵 3 案",
    sub=["四章の地上の敵。3〜5 匹の群れで、温石を持つ人・火・温かい岩へ寄る (背中の火の粉蛾の「いちばん熱いものへ寄る」の予習)。HP 6、触れて 3。",
         "叩けば一撃で、死ぬと小さな火の粉。温石を台に置くと、群れはそちらへ寄る。左の黄の目盛りは人 (1.8 ブロック)。"],
    labels=["A 熾の粒虫", "B 火打ちの甲虫", "C 灰の蚤"],
    rows=[("前 3/4", "idle", 212, 18, 260, (0, 2, 0), 30.0),
          ("横", "idle", 270, 6, 260, (0, 2, 0), 30.0),
          ("上から (群れで見る面)", "idle", 200, 60, 260, (0, 2, 0), 30.0),
          ("寄る (熱へ駆ける。体を低く)", "rush", 230, 14, 260, (0, 2, 0), 30.0),
          ("後ろ 3/4", "idle", 32, 16, 260, (0, 2, 0), 30.0)],
    options={
        "a": dict(name="A 熾の粒虫", desc="丸い炭の背に熾のひびが三筋、短い 6 本の脚。遠目には転がる燠そのもので、群れが床を這うと火の粉が歩いているように見える。光る所が背なので上から読みやすい。"),
        "b": dict(name="B 火打ちの甲虫", desc="平たい黒石の甲と、前に突き出た火打ちの大顎 (先に火の粉の粒)。背の中央に熾の筋。玄武岩の床に紛れ、動くと顎の火の粉で見つかる。触れて 3 の痛さが一番伝わる形。"),
        "c": dict(name="C 灰の蚤", desc="灰色の細い体と太い後ろ脚、尾の先に熾の玉。灰の地の色に溶け、尾の玉だけが光る。小さく速そうで、群れが一斉に向きを変える姿が分かりやすい。")},
    extra="四章の言葉 (灰・炭・黒石・熾の橙)。骨 10 本 (root > body > head / tail / leg_l1..3 / leg_r1..3)。湧くのは 3〜5 匹の群れなので、一匹は小さく (幅 0.4 ブロック) 三角形は少なめ。",
    budget="地上の小物 300〜500 (群れなので 300 以下)", atlas_h=140, pw=420)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=64)
