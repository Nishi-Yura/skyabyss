"""沈んだ神殿の主 (temple_keeper)。潮殻の土地の奥のダンジョン「沈んだ神殿」(潮が満ち引きする半地下) の主 (issue #19 の ②)。Lv 21 前後。
満ち潮の段と引き潮の段を切り替える。技: 潮の槍の突き (0.8 秒、前の細い帯 6)・渦 (1.4 秒、足もとの円 4 が三つ)・満ち潮の呼び (床が水に沈む、高い台へ逃げる)。
骨: root > body > head / arm_l / arm_r > weapon / offhand / tail (A・C は尾、B は殻の口)。
A 珊瑚に呑まれた祭司: 長衣の祭司の亡骸に珊瑚と貝が生え、三叉の槍。下は水に溶ける長い裾
B 巻貝の神像: 大きな巻貝の殻から石の神像の上体が出る。殻の口から潮が渦巻く
C 潮の大鰻: とぐろを巻いた大鰻。頭に祭司の冠、背に海晶の鰭
"""
import math

from lf import *  # noqa: F401,F403
from lf import box, run, part, mat, eye_at, storm, grow, rows_std

storm(True)
K = 1.5
BONES = grow([], {"root": (None, (0, 0, 0)), "body": ("root", (0, 10.0, 0)), "head": ("body", (0, 24.0, 0)),
                  "arm_l": ("body", (5.0, 21.0, 0)), "arm_r": ("body", (-5.0, 21.0, 0)), "weapon": ("arm_r", (-6.0, 12.0, -1.0)),
                  "offhand": ("arm_l", (6.0, 12.0, -1.0)), "tail": ("body", (0, 6.0, 2.0))}, K)
SEA = [(22, 50, 58), (34, 76, 84), (52, 108, 112), (80, 140, 138)]
CORAL = [(120, 44, 52), (170, 70, 72), (214, 112, 100), (240, 160, 140)]
SHELL = [(110, 80, 60), (150, 112, 84), (190, 150, 112), (220, 186, 146)]
PAL = {"mats": {"robe": mat([(30, 46, 54), (46, 68, 78), (66, 94, 104), (92, 124, 132)], 0.58, noise=0.2), "coral": mat(CORAL, 0.65, noise=0.15),
                "shell": mat(SHELL, 0.62, stripe=3), "stone": mat([(70, 76, 80), (100, 108, 110), (132, 140, 140), (164, 172, 170)], 0.6, noise=0.15, crack=0.1),
                "sea": mat(SEA, 0.62, noise=0.18), "glow": mat([(40, 150, 160), (90, 210, 210), (170, 245, 240)], 0.78, min=1),
                "bone": mat([(150, 140, 120), (190, 180, 158), (220, 212, 192)], 0.65), "gold": mat([(110, 80, 30), (170, 130, 50), (220, 180, 90)], 0.7)},
       "skull_bone": "head", "face_mat": "bone", "glow_mats": ["glow"]}


def build_a(k):
    F = []
    F += part("robe", tube("body", "robe", [(0, 0.4, 0), (0, 8.0, 0), (0, 16.0, 0), (0, 22.0, 0)], ra=[5.4, 4.2, 3.8, 3.4], sides=9, hint=(1, 0, 0)))
    F += part("hem", tube("tail", "glow", [(0, 0.2, 0), (0, 1.6, 0)], ra=[5.8, 5.4], sides=9, hint=(1, 0, 0)))
    F += part("skull", ellipsoid("head", "bone", (0, 26.0, -0.4), (2.2, 2.4, 2.2), segs=8, rings=3))
    F += part("hood", ellipsoid("head", "robe", (0, 26.6, 0.8), (2.8, 2.8, 2.8), segs=8, rings=2))
    for i, (p, d, ln) in enumerate((((2.0, 28.4, 0.4), (0.4, 1, 0), 3.0), ((-1.8, 27.8, 1.2), (-0.5, 1, 0.2), 2.4), ((3.4, 22.0, -1.6), (1, 0.6, -0.4), 2.6),
                                   ((-3.0, 17.0, -2.8), (-0.6, 0.3, -1), 2.4), ((2.6, 10.0, -3.6), (0.5, 0.2, -1), 2.0))):
        F += part(f"coral{i}", spike("body" if i > 1 else "head", "coral", p, d, ln, 0.7, sides=4, rings=2, bend=(0, 0.6, 0)))
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(f"{b}_sleeve", tube(b, "robe", [(sx * 4.4, 21.4, 0), (sx * 6.0, 16.0, -0.6), (sx * 6.2, 12.6, -1.0)], ra=[1.4, 1.6, 1.8], sides=6, hint=(0, 0, 1)))
        F += part(f"{b}_hand", ellipsoid(b, "bone", (sx * 6.2, 11.6, -1.0), (0.9, 1.1, 0.9), segs=6, rings=2))
    F += part("shaft", tube("weapon", "gold", [(-6.2, 2.0, -1.0), (-6.2, 26.0, -1.0)], ra=[0.45, 0.45], sides=5, hint=(1, 0, 0)))
    for dx in (-1.4, 0, 1.4):
        F += part(f"tine{dx}", spike("weapon", "glow", (-6.2 + dx, 26.0, -1.0), (dx * 0.2, 1, 0), 3.0, 0.4, sides=4, rings=1))
    F += part("tine_bar", box("weapon", "gold", (-6.2, 26.0, -1.0), (3.4, 0.6, 0.6)))
    grow(F, {}, K)
    return F, [eye_at(-0.8 * K, 26.4 * K, -2.4 * K, 0.5 * K), eye_at(0.8 * K, 26.4 * K, -2.4 * K, 0.5 * K)]


def build_b(k):
    F = []
    # 巻貝の殻 (下)。口は前
    for i in range(5):
        r = 7.0 - i * 1.3
        F += part(f"whorl{i}", ellipsoid("tail", "shell", (0.6 * i, 5.0 + i * 1.6, 2.4 + i * 1.2), (r, r * 0.65, r), segs=10, rings=2))
    F += part("lip", tube("tail", "coral", [(0, 5.0, -4.6), (0, 5.0, -5.6)], ra=[4.4, 4.0], sides=9, hint=(0, 1, 0)))
    F += part("whirl", ellipsoid("tail", "glow", (0, 5.0, -5.8), (3.2, 3.0, 0.6), segs=8, rings=2))
    # 石の神像の上体
    F += part("torso", ellipsoid("body", "stone", (0, 15.0, -1.0), (4.2, 5.4, 2.8), segs=8, rings=3))
    F += part("skull", ellipsoid("head", "stone", (0, 22.6, -1.0), (2.4, 2.8, 2.4), segs=8, rings=3))
    F += part("crown", tube("head", "gold", [(0, 24.6, -1.0), (0, 26.2, -1.0)], ra=[2.6, 3.0], sides=8, hint=(1, 0, 0)))
    F += part("face", box("head", "glow", (0, 22.6, -3.4), (2.4, 0.4, 0.3)))
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        F += part(f"{b}_arm", tube(b, "stone", [(sx * 4.2, 18.6, -1.0), (sx * 7.0, 14.6, -2.0), (sx * 6.4, 10.6, -4.0)], ra=[1.6, 1.4, 1.3], sides=6, hint=(0, 0, 1)))
        F += part(f"{b}_hand", ellipsoid(b, "stone", (sx * 6.2, 9.8, -4.6), (1.5, 1.5, 1.5), segs=6, rings=2))
    F += part("orb", ellipsoid("weapon", "glow", (-6.2, 11.4, -6.2), (1.2, 1.2, 1.2), segs=6, rings=2))
    grow(F, {}, K)
    return F, []


def build_c(k):
    F = []
    # とぐろ (三つの輪) から首が立ち上がる
    pts = []
    for i in range(28):
        t = i / 27
        a = t * 2.6 * math.pi
        r = 7.0 - 3.2 * t
        pts.append((r * math.cos(a), 2.2 + t * 6.0, r * math.sin(a) + 2.0))
    pts += [(0.6, 12.0, 1.0), (0.2, 18.0, -0.6), (0, 22.0, -2.6)]
    ras = [2.4 - 0.6 * (i / len(pts)) for i in range(len(pts))]
    F += part("coil", tube("tail", "sea", pts, ra=ras, sides=7, hint=(0, 1, 0)))
    F += part("head", ellipsoid("head", "sea", (0, 23.6, -4.0), (2.4, 2.0, 3.4), segs=8, rings=2))
    F += part("jaw", box("head", "bone", (0, 22.4, -6.4), (2.4, 0.8, 2.4), taper=(0.7, 0.6)))
    F += part("crown", tube("head", "gold", [(0, 25.2, -3.4), (0, 26.8, -3.0)], ra=[1.6, 2.0], sides=7, hint=(1, 0, 0)))
    for i in range(6):
        p = pts[16 + i * 2]
        F += part(f"fin{i}", spike("tail", "glow", (p[0], p[1] + 1.9, p[2]), (0, 1, 0), 3.4, 0.9, sides=4, rings=1))
    grow(F, {}, K)
    return F, [eye_at(-1.6 * K, 24.2 * K, -5.6 * K, 0.6 * K), eye_at(1.6 * K, 24.2 * K, -5.6 * K, 0.6 * K)]


def detail(F):
    # 細部: 長衣の裾の藤壺・胸の貝の首飾り・袖の破れの房・珊瑚の小枝・頭巾の貝
    for i in range(8):
        a = math.radians(i * 45 + 10)
        F += part(f"barnacle{i}", ellipsoid("body", "shell", (4.8 * math.cos(a), 2.4 + (i % 3) * 1.2, 4.8 * math.sin(a)), (0.6, 0.5, 0.6), segs=5, rings=1))
    for i in range(5):
        a = math.radians(200 + i * 35)
        F += part(f"neckshell{i}", ellipsoid("body", "shell", (2.4 * math.cos(a), 21.4 - abs(i - 2) * 0.5, -2.6 + 0.4 * math.sin(a)), (0.45, 0.45, 0.3), segs=5, rings=1))
    for sx, b in ((1, "arm_l"), (-1, "arm_r")):
        for j in range(3):
            F += part(f"{b}_rag{j}", spike(b, "robe", (sx * (5.6 + j * 0.3), 12.8, -0.6 + j * 0.6), (0, -1, 0), 1.8, 0.4, sides=3, rings=1))
    for i, (p, d) in enumerate((((-2.6, 18.0, -2.8), (-0.4, 1, -0.6)), ((2.2, 8.0, -4.2), (0.6, 0.4, -1)), ((0, 25.0, 2.2), (0, 1, 0.5)))):
        F += part(f"twig{i}", spike("body" if i < 2 else "head", "coral", p, d, 1.8, 0.4, sides=4, rings=2, bend=(0, 0.4, 0)))
    return F


def build(k):
    F, eyes = {"a": build_a, "b": build_b, "c": build_c}[k](k)
    if k == "a":
        extra = detail([])
        grow(extra, {}, K)
        F += extra
    return F, eyes


POSES = {"idle": {}, "thrust": {"body": (-10, 0, 0), "arm_r": (-80, 0, 0), "head": (-10, 0, 0)},
         "call": {"arm_l": (-160, 0, 20), "arm_r": (-160, 0, -20), "head": (20, 0, 0)}}

SPEC = dict(
    id="temple_keeper", short="神殿の主", kind="奥のダンジョンの主 3案", role="主", land="潮",
    title="沈んだ神殿の主 (temple_keeper) — 潮の奥のダンジョン・低ポリ 3 案",
    sub=["満ち潮と引き潮の段を切り替える主。潮の槍の突き (0.8 秒、前の帯 6)・渦 (1.4 秒、円 4 が三つ)・満ち潮の呼び (床が沈み、高い台へ逃げる)。",
         "大きさは人の 1.5 倍。"],
    labels=["A 珊瑚に呑まれた祭司", "B 巻貝の神像", "C 潮の大鰻"],
    rows=rows_std(20, 6.0, h=420, extra=(("突きの予告", "thrust"), ("満ち潮の呼び", "call"))),
    options={
        "a": dict(name="A 珊瑚に呑まれた祭司", desc="長衣の祭司の亡骸に珊瑚と貝が生え、三叉の槍を持つ。裾は光る潮に溶ける。神殿の『昔の祭司』の物語に一番合う。"),
        "b": dict(name="B 巻貝の神像", desc="大きな巻貝の殻から石の神像の上体が出る。殻の口に潮の渦。動かない台座型で、満ち引きの床の仕掛けを主役にできる。"),
        "c": dict(name="C 潮の大鰻", desc="とぐろを巻いた大鰻、頭に祭司の冠、背に光る鰭。満ち潮で泳ぎ回る (水の中を速く動く) 主にできる。")},
    extra="骨 8 本。", budget="主 600〜1200", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
