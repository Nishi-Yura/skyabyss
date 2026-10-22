"""焦げた鳥の影 (scorched_bird) 低ポリ ラフ 3 案。雷の層の空の敵。天井の闇から降りて、上から掴みに来る。
A 広げた翼 (両翼を広げ、風切り羽を階段のように並べる。扇の尾) / B 折れた翼 (左翼が肘で折れて垂れ、羽が割れて下がる。右翼は半分) / C 長い尾の鳥 (翼は畳み、長い尾の先に白い雷)
骨: root > body > head / wing_l / wing_r / tail / leg_l / leg_r。すべて作図空間の絶対座標。羽は薄い筒 (厚さ 0.12〜0.14、幅は羽の根元が広い) で、羽の根は翼の骨か胴に沈める。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 4.6, 0)), "head": ("body", (0, 6.6, -2.6)),
         "wing_l": ("body", (-1.2, 5.6, -0.8)), "wing_r": ("body", (1.2, 5.6, -0.8)),
         "tail": ("body", (0, 4.2, 2.2)), "leg_l": ("body", (-0.7, 3.4, -0.2)), "leg_r": ("body", (0.7, 3.4, -0.2))}
PAL = make_pal({
    "soot": (ASHY, 0.55, dict(crack=0.18, noise=0.24)),
    "feather": ([(60, 56, 56), (100, 94, 92), (140, 132, 126), (182, 174, 166), (214, 206, 196)], 0.62, dict(crack=0.0, noise=0.12)),
    "tuff": (TUFF, 0.62, dict(crack=0.14, noise=0.22)),
    "oxcu": (OXCU, 0.55, dict(crack=0.12, noise=0.2)),
    "light": (LIGHT, 0.85, dict(crack=0.0, noise=0.03, min=2)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
}, glow=["light"])


def feather(F, bone, root, tip, chord=0.8, thick=0.13, mat_="feather", bend=0.0):
    """羽一枚: 根から先へ伸びる薄い筒。根は広く、先はとがる (rb = 羽の幅、ra = 厚さ)。"""
    root, tip = np.array(root, float), np.array(tip, float)
    mid = (root + tip) / 2 + np.array([0.0, bend, 0.0])
    F += part(bone + "_f", tube(bone, mat_, [root, mid, tip], ra=[thick, thick, 0.04], rb=[chord * 0.7, chord, chord * 0.2],
                                sides=6, hint=(0, 1, 0)))
    return F


def shaft(F, bone, pts, r=0.36, mat_="tuff"):
    F += part(bone + "_shaft", tube(bone, mat_, pts, ra=[r, r * 0.8, r * 0.55, r * 0.4][:len(pts)], sides=5, hint=(0, 1, 0)))
    return F


def body(F):
    F += part("body", ellipsoid("body", "soot", (0, 4.6, 0.2), (1.7, 1.6, 2.8), segs=10, rings=5))
    F += part("breast", ellipsoid("body", "soot", (0, 4.9, -1.3), (1.4, 1.4, 1.3), segs=8, rings=4))
    F += part("neck", tube("body", "soot", [(0, 5.6, -1.8), (0, 6.4, -2.5)], ra=0.85, sides=6, hint=(1, 0, 0)))
    F += part("head", ellipsoid("head", "soot", (0, 6.9, -3.0), (1.05, 1.0, 1.1), segs=8, rings=4))
    F += part("beak", spike("head", "tuff", (0, 6.8, -4.0), (0, -0.2, -1), 1.5, 0.42, sides=4, rings=2))
    F += part("beak_low", spike("head", "tuff", (0, 6.4, -3.9), (0, -0.1, -1), 1.0, 0.25, sides=4, rings=2))
    F += part("tail_base", tube("body", "soot", [(0, 4.2, 2.0), (0, 3.9, 4.2)], ra=0.9, sides=6, hint=(1, 0, 0)))
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"leg_{nm}", tube(f"leg_{nm}", "tuff", [(s * 0.7, 3.6, -0.2), (s * 0.8, 1.6, -0.4), (s * 0.8, 0.9, -0.6)], ra=0.32, sides=5, hint=(1, 0, 0)))
        for i, dx in enumerate((-0.3, 0.0, 0.3)):
            F += part(f"talon_{nm}{i}", spike(f"leg_{nm}", "oxcu", (s * 0.8, 0.9, -0.6), (s * 0.8 + dx, -0.4, -1.0), 1.0, 0.16, sides=4, rings=2))
    return F


def tail_fan(F, length=8.0):
    """尾の扇: 五枚の尾羽が後ろへ広がる。根は尾の付け根 (胴の中) に沈める。"""
    for i, x in enumerate((-2.2, -1.1, 0.0, 1.1, 2.2)):
        zt = 3.8 + length * 0.12 + 4.2 - abs(x) * 0.35
        F = feather(F, "tail", (0, 3.9, 3.8), (x * 0.9, 3.6, zt), chord=0.9, thick=0.13)
    return F


def wing_spread(F, s, scale=1.0, n_prim=5):
    """広げた翼: 翼の骨 (肩・肘・手首) と、風切り羽 (根が階段になり、後ろの縁がのこぎり) と、雨覆い。scale で長さを変える。"""
    bn = "wing_l" if s < 0 else "wing_r"
    S, E, W = (s * 1.2, 5.6, -0.8), (s * 4.4 * scale, 5.9, -0.6), (s * 8.2 * scale, 5.8, -0.2)
    shaft(F, bn, [S, E, W], r=0.38)
    for i in range(n_prim):
        root = (s * (8.0 - 0.45 * i) * scale, 5.8, -0.3 + 0.35 * i)
        tip = (s * (13.6 - 0.55 * i) * scale, 5.6, 0.3 + 1.1 * i)
        feather(F, bn, root, tip, chord=0.75, thick=0.12, bend=0.05)
    for j in range(4):
        root = (s * (5.6 - 0.8 * j) * scale, 5.9, -0.5 + 0.2 * j)
        tip = (s * (9.0 - 0.8 * j) * scale, 5.7, 1.6 + 0.9 * j)
        feather(F, bn, root, tip, chord=0.95, thick=0.14)
    for k in range(4):
        root = (s * (7.0 - 1.0 * k) * scale, 5.9, -0.7 + 0.25 * k)
        tip = (s * (7.8 - 1.0 * k) * scale, 5.7, 0.3 + 0.55 * k)
        feather(F, bn, root, tip, chord=0.7, thick=0.12)
    return F


def build_a(k):
    """A 広げた翼: 両翼を広げ、風切り羽を階段のように並べる。扇の尾。上から見た影が一番大きい。"""
    F = body([])
    wing_spread(F, -1)
    wing_spread(F, 1)
    tail_fan(F)
    return F, [eye_at(-0.5, 7.0, -3.9, 0.28), eye_at(0.5, 7.0, -3.9, 0.28)]


def build_b(k):
    """B 折れた翼: 左翼が肘で折れて垂れ、羽が割れて下がる。右翼は半分だけ広げる。割れた骨の先が一本のぞく。"""
    F = body([])
    s = -1
    S, E, K, W = (s * 1.2, 5.6, -0.8), (s * 3.4, 5.9, -0.6), (s * 4.6, 3.0, -0.2), (s * 4.2, 0.9, 0.4)
    shaft(F, "wing_l", [S, E, K, W], r=0.36)
    for i in range(3):
        root = (s * (4.4 + 0.25 * i), 3.2 - 0.6 * i, -0.2 + 0.4 * i)
        tip = (s * (6.2 + 0.7 * i), 0.4 + 0.2 * i, 0.9 + 0.7 * i)
        feather(F, "wing_l", root, tip, chord=0.7, thick=0.12, bend=-0.3)
    F += part("splinter", spike("wing_l", "tuff", (s * 4.6, 3.0, -0.2), (s * 0.5, 0.4, 0.0), 1.8, 0.2, sides=4, rings=2))
    wing_spread(F, 1, scale=0.72, n_prim=4)
    tail_fan(F, length=6.0)
    return F, [eye_at(-0.5, 7.0, -3.9, 0.28), eye_at(0.5, 7.0, -3.9, 0.28)]


def build_c(k):
    """C 長い尾の鳥: 翼は畳んで胴の横に寄せる。細い尾が長く伸び、先の尾羽が白い雷の玉のように光る。"""
    F = body([])
    for s in (-1, 1):
        bn = "wing_l" if s < 0 else "wing_r"
        S, E, W = (s * 1.2, 5.6, -0.8), (s * 2.2, 5.6, 0.6), (s * 2.4, 4.9, 3.2)
        shaft(F, bn, [S, E, W], r=0.3)
        for i in range(4):
            root = (s * 2.2, 5.0 - 0.1 * i, 2.8 + 0.3 * i)
            tip = (s * 1.4, 4.6, 6.0 + 0.4 * i)
            feather(F, bn, root, tip, chord=0.6, thick=0.1)
    F += part("tail_shaft", tube("tail", "soot", [(0, 4.0, 2.6), (0, 3.7, 5.4), (0, 3.4, 8.0)], ra=[0.6, 0.42, 0.18], sides=6, hint=(1, 0, 0)))
    F += part("tail_tip", ellipsoid("tail", "light", (0, 3.4, 8.4), (0.5, 0.5, 0.5), segs=6, rings=3))
    for x in (-0.8, 0.8):
        feather(F, "tail", (0, 3.6, 7.0), (x, 3.4, 9.2), chord=0.5, thick=0.08)
    return F, [eye_at(-0.5, 7.0, -3.9, 0.28), eye_at(0.5, 7.0, -3.9, 0.28)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "dive": {"body": (40, 0, 0), "head": (30, 0, 0), "wing_l": (0, 0, 35), "wing_r": (0, 0, -35), "tail": (-30, 0, 0)},
}

SPEC = dict(
    id="scorched_bird", short="焦げた鳥の影", kind="雷の層の敵 3案 (A3)",
    title="焦げた鳥の影 (scorched_bird) — 天井の闇から降りる空の敵・羽のある鳥・低ポリ + ドット絵 3 案",
    sub=["雷の層の空の敵。天井の闇から影だけで降りてくる (上を見ないと気づかない)。急降下して掴み、そのまま上へ戻る。",
         "翼を広げた形は幅が広いので、三案とも同じ縮尺で描いた (高さは約 40%、幅は枠に収まる)。影は灯りでは消えず、光の術の輪郭で見える。"],
    labels=["A 広げた翼", "B 折れた翼", "C 長い尾の鳥"],
    rows=[("前 3/4 (翼を広げる)", "idle", 212, 12, 300, (0, 4.6, 0), 12.0),
          ("横", "idle", 270, 4, 300, (0, 4.6, 0), 12.0),
          ("上から (影の形)", "idle", 200, 60, 300, (0, 4.6, 0), 12.0),
          ("急降下", "dive", 230, 10, 300, (0, 4.6, 0), 12.0),
          ("後ろ 3/4", "idle", 32, 12, 300, (0, 4.6, 0), 12.0)],
    options={
        "a": dict(name="A 広げた翼", desc="両翼を広げて風切り羽を階段のように並べ、扇の尾。上から見た影が一番大きく、天井の闇に紛れて見つけにくい。"),
        "b": dict(name="B 折れた翼", desc="左翼が肘で折れて垂れ、羽が割れて下がる。割れた骨の先が一本のぞく。右翼は半分だけ。飛べない影に見え、動きの乱れで隙が分かる。"),
        "c": dict(name="C 長い尾の鳥", desc="翼は畳んで胴の横に寄せ、細い尾が長く伸びて先に白い雷の玉。尾で上から打つ形で、攻撃の距離が一番読める。")},
    extra="雷の言葉 (煤・羽の灰・凝灰岩・酸化した銅・白い雷の光)。骨 8 本 (root > body > head / wing_l wing_r / tail / leg_l leg_r)。羽は薄い筒の羽根、根は翼の骨か胴に沈める。",
    budget="空の中型 300〜450 (羽の数で増える)", atlas_h=150, pw=420,
    later="歪み = 羽に穴が増え、尾の先の光が二つになる。崩れ = 翼が取れて胴だけが落ち、落ちた所で煤の跡が残る。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
