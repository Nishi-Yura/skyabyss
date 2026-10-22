"""帯電の羽虫 (charged_moth) 低ポリ ラフ 3 案。雷の層の光る群れ (8〜12 匹)。人に寄り、触れると小さく痺れる。
A 細い羽虫 (細い胴と薄い羽、腹の先が光る) / B 甲虫の羽虫 (硬い甲の背に光る筋、羽は畳む) / C 蝶の羽虫 (大きい薄羽、羽の縁が光る)
骨: root > body > head / wing_l / wing_r / abd。群れなので一匹は小さく、三角形は 200〜300。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 2.2, 0)), "head": ("body", (0, 2.3, -2.6)),
         "wing_l": ("body", (-0.8, 2.8, -0.4)), "wing_r": ("body", (0.8, 2.8, -0.4)), "abd": ("body", (0, 1.9, 1.6))}
PAL = make_pal({
    "tuff": (TUFF, 0.62, dict(crack=0.14, noise=0.22)),
    "oxcu": (OXCU, 0.55, dict(crack=0.12, noise=0.2)),
    "light": (LIGHT, 0.85, dict(crack=0.0, noise=0.03, min=2)),
    "wing": ([(120, 128, 136), (168, 176, 186), (204, 212, 220), (236, 240, 244)], 0.7, dict(crack=0.1, noise=0.16)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
}, glow=["light"])


def wing_plane(F, bone, root, tip, chord, mat_="wing"):
    """羽の一枚: 根から先へ伸びる薄い筒 (厚さ 0.14、幅は中ほどで広い)。円盤にしない。"""
    root, tip = np.array(root, float), np.array(tip, float)
    mid = root + (tip - root) * 0.45
    F += part(bone + "_plane", tube(bone, mat_, [root, mid, tip], ra=[0.14, 0.14, 0.05], rb=[chord * 0.5, chord, chord * 0.3],
                                    sides=6, hint=(0, 1, 0)))
    return F


def build_a(k):
    """A 細い羽虫: 細い胴と薄い羽。腹の先の白い雷の玉が群れの目印 (近いと光が強い)。"""
    F = []
    F += part("thorax", ellipsoid("body", "tuff", (0, 2.2, -0.6), (1.0, 0.9, 1.4), segs=10, rings=5))
    F += part("abdomen", tube("abd", "tuff", [(0, 0, 0), (0, -0.1, 1.6), (0, 0.1, 3.2)], ra=[0.8, 0.6, 0.4], sides=7, hint=(1, 0, 0)))
    F += part("tip", ellipsoid("abd", "light", (0, 0.1, 3.8), (0.55, 0.55, 0.55), segs=6, rings=3))
    F += part("head", ellipsoid("head", "tuff", (0, 2.3, -2.5), (0.75, 0.6, 0.6), segs=7, rings=3))
    for s, nm in ((-1, "l"), (1, "r")):
        wing_plane(F, f"wing_{nm}", (s * 0.6, 2.8, -0.6), (s * 3.4, 3.0, -0.2), 1.6)
        wing_plane(F, f"wing_{nm}", (s * 0.6, 2.8, 0.3), (s * 2.6, 2.8, 1.4), 1.2)
        F += part(f"antenna_{nm}", tube("head", "oxcu", [(s * 0.3, 0.3, -0.6), (s * 1.0, 1.6, -1.0)], ra=[0.14, 0.08], sides=4, hint=(0, 0, 1)))
    return F, [eye_at(-0.36, 2.4, -3.0, 0.26), eye_at(0.36, 2.4, -3.0, 0.26)]


def build_b(k):
    """B 甲虫の羽虫: 硬い甲の背に光る筋。羽は甲の下に畳み、甲の縁から少しだけのぞく。"""
    F = []
    F += part("shell", loft("body", "oxcu", [ring(1.3, 1.4, -1.8, 1.8, n=10), ring(2.4, 1.8, -2.2, 2.2, n=10), ring(3.0, 1.3, -1.6, 1.6, n=10)], caps=("oxcu", "oxcu")))
    F += part("seam", tube("body", "light", [(0, 3.8, -1.6), (0, 3.9, 0.0), (0, 3.8, 1.6)], ra=[0.2, 0.28, 0.2], sides=4, hint=(1, 0, 0)))
    F += part("head", ellipsoid("head", "tuff", (0, 2.2, -2.6), (0.8, 0.6, 0.7), segs=7, rings=3))
    F += part("mand_l", spike("head", "tuff", (-0.4, 1.8, -3.3), (-0.2, -0.2, -1), 1.0, 0.2, sides=4, rings=2))
    F += part("mand_r", spike("head", "tuff", (0.4, 1.8, -3.3), (0.2, -0.2, -1), 1.0, 0.2, sides=4, rings=2))
    for s, nm in ((-1, "l"), (1, "r")):
        wing_plane(F, f"wing_{nm}", (s * 0.5, 2.9, -0.6), (s * 2.0, 2.9, 0.2), 1.0)
    return F, [eye_at(-0.4, 2.4, -3.2, 0.24), eye_at(0.4, 2.4, -3.2, 0.24)]


def build_c(k):
    """C 蝶の羽虫: 大きい薄羽。羽の縁が光る線になり、羽ばたくと光の軌跡が残る。"""
    F = []
    F += part("body", ellipsoid("body", "tuff", (0, 2.2, 0.0), (0.8, 0.8, 1.8), segs=8, rings=4))
    F += part("head", ellipsoid("head", "tuff", (0, 2.3, -2.2), (0.7, 0.65, 0.7), segs=7, rings=3))
    for s, nm in ((-1, "l"), (1, "r")):
        wing_plane(F, f"wing_{nm}", (s * 0.6, 2.9, -1.2), (s * 3.2, 3.0, -1.0), 1.8)
        wing_plane(F, f"wing_{nm}", (s * 0.6, 2.9, 0.8), (s * 3.0, 3.0, 1.8), 1.5)
        F += part(f"edge_{nm}", tube(f"wing_{nm}", "light", [(s * 0.6, 3.1, -2.4), (s * 3.6, 3.1, -1.2), (s * 4.2, 3.1, 1.6)], ra=0.2, sides=4, hint=(0, 1, 0)))
    return F, [eye_at(-0.3, 2.4, -2.6, 0.24), eye_at(0.3, 2.4, -2.6, 0.24)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "flap": {"wing_l": (0, 0, 40), "wing_r": (0, 0, -40)},
}

SPEC = dict(
    id="charged_moth", short="帯電の羽虫", kind="雷の層の敵 3案 (A3)",
    title="帯電の羽虫 (charged_moth) — 光る群れの虫・低ポリ + ドット絵 3 案",
    sub=["雷の層の群れ (8〜12 匹)。人に寄って光り、触れると小さく痺れる。一匹は弱いが、群れで寄ると痺れが重なる。",
         "このシートは一匹を大きく寄せて描いた (ruler は人の目盛り 1.8 ブロック)。光る所は白い雷。"],
    labels=["A 細い羽虫", "B 甲虫の羽虫", "C 蝶の羽虫"],
    rows=[("前 3/4", "idle", 212, 18, 300, (0, 3.0, 0), 22.0),
          ("横", "idle", 270, 6, 300, (0, 3.0, 0), 22.0),
          ("上から (群れで見る面)", "idle", 200, 60, 300, (0, 3.0, 0), 22.0),
          ("羽ばたく", "flap", 230, 14, 300, (0, 3.0, 0), 22.0),
          ("後ろ 3/4", "idle", 32, 16, 300, (0, 3.0, 0), 22.0)],
    options={
        "a": dict(name="A 細い羽虫", desc="細い胴と薄い羽。腹の先の白い雷の玉が群れの目印になる。小さく、群れが一斉に寄る姿が一番出る。"),
        "b": dict(name="B 甲虫の羽虫", desc="酸化した銅の硬い甲に白い雷の筋。羽は畳んで甲の下。打つと甲が鳴るので、触れて痺れる形がはっきり読める。"),
        "c": dict(name="C 蝶の羽虫", desc="大きい薄羽で、縁が白い雷の線になる。羽ばたくと光の軌跡が残り、群れの動きが遠くから追える。ただし一番大きく、数が多いと重い。")},
    extra="雷の言葉 (凝灰岩・酸化した銅・白い雷の光・薄羽)。骨 6 本 (root > body > head / wing_l wing_r / abd)。群れなので一匹は小さい。",
    budget="群れの小物 200〜300 (一匹)", atlas_h=140, pw=420,
    later="歪み = 光の玉が二つに増え、羽の縁が焦げて黒い。崩れ = 羽が半分抜け、光だけが浮く (光は見えるが体が見えない)。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
