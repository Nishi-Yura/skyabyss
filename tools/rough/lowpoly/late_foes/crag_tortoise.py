"""峰の甲羅 (crag_tortoise)。嵐の高地の敵 (issue #20)。役: 守りを固める。Lv 21〜24。
ゆっくり歩き、人が近いと甲羅に籠る (予告 0.8 秒: 首を引く。籠っているあいだ前からの傷 −70%、後ろと横からはふつう)。
籠ったあと 1.0 秒の予告 (甲羅が光る) で前へ体当たり (帯 5、ひっくり返す = 短い打ち上げ)。後ろへ回り込む敵。
骨: root > body > head / leg_fl / leg_fr / leg_bl / leg_br / tail。
A 峰の甲羅亀: 甲羅が小さな雪の峰。岩の段、頂に青白い水晶
B 雷鉄の鎧亀: 銅の板を打った甲羅、背に避雷の銅の棒、板の継ぎ目が光る
C 氷の殻の大団子虫: 氷の板 7 枚の殻の大きな虫。丸まると玉の盾
"""
import math

from lf import *  # noqa: F401,F403
from lf import box, run, part, mat, eye_at, rows_std, storm, lumps

storm(True)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 7.0, 0)), "head": ("body", (0, 7.0, -8.0)),
         "leg_fl": ("body", (5.0, 6.0, -5.0)), "leg_fr": ("body", (-5.0, 6.0, -5.0)), "leg_bl": ("body", (5.0, 6.0, 5.0)),
         "leg_br": ("body", (-5.0, 6.0, 5.0)), "tail": ("body", (0, 6.0, 8.0))}
COPPER = [(70, 40, 24), (120, 70, 40), (170, 108, 64), (210, 150, 96)]
PAL = {"mats": {"crag": mat(CRAG, 0.6, noise=0.22), "snow": mat(SNOW, 0.72, noise=0.08), "volt": mat(VOLT, 0.78, min=1),
                "skin": mat([(50, 54, 50), (78, 84, 76), (108, 114, 104)], 0.55, noise=0.15), "copper": mat(COPPER, 0.62, noise=0.1),
                "ice": mat([(120, 160, 200), (160, 198, 230), (200, 228, 250)], 0.7, noise=0.08), "dark": mat([(14, 14, 20), (28, 28, 36), (44, 44, 54)], 0.5)},
       "skull_bone": "head", "face_mat": "skin", "glow_mats": ["volt"]}


def legs4(F, m, thick=1.6):
    for b, sx, sz in (("leg_fl", 1, -1), ("leg_fr", -1, -1), ("leg_bl", 1, 1), ("leg_br", -1, 1)):
        F += part(b, tube(b, m, [(sx * 5.0, 6.4, sz * 5.0), (sx * 6.4, 3.6, sz * 5.6), (sx * 6.4, 0.6, sz * 5.6)], ra=[thick, thick * 0.95, thick * 1.05], sides=6, hint=(1, 0, 0)))
        F += part(b + "_foot", ellipsoid(b, "dark", (sx * 6.4, 0.6, sz * 5.6 - 0.6), (thick * 1.1, 0.7, thick * 1.2), segs=6, rings=1))


def head(F, m="skin", beak=True):
    F += part("neck", tube("head", m, [(0, 7.0, -6.0), (0, 7.6, -9.0)], ra=[2.0, 1.7], sides=6, hint=(1, 0, 0)))
    F += part("skull", ellipsoid("head", m, (0, 8.4, -10.6), (2.0, 1.8, 2.4), segs=8, rings=2))
    if beak:
        F += part("beak", box("head", "dark", (0, 7.8, -13.0), (1.8, 1.4, 1.4), taper=(0.6, 0.3)))


def build_a(k):
    """A 峰の甲羅亀"""
    F = []
    F += part("plastron", ellipsoid("body", "skin", (0, 5.0, 0), (6.0, 1.6, 7.6), segs=10, rings=1))
    # 甲羅 = 三段の岩の段 + 雪の頂
    for i, (y, rx, rz) in enumerate(((7.4, 7.0, 8.4), (10.4, 5.4, 6.4), (12.8, 3.6, 4.4))):
        F += part(f"tier{i}", ellipsoid("body", "crag", (0, y, 0.3 * i), (rx, 2.4, rz), segs=9, rings=2))
    F += part("cap", ellipsoid("body", "snow", (0, 14.4, 0.8), (3.0, 1.6, 3.4), segs=8, rings=2))
    for i, (x, z, ln) in enumerate(((0, 1.0, 4.0), (1.4, 2.2, 2.8), (-1.2, -0.2, 2.6))):
        F += part(f"crystal{i}", spike("body", "volt", (x, 14.8, z), (x * 0.2, 1, 0.1), ln, 0.8, sides=4, rings=1))
    F += lumps("body", "crag", [((4.6, 9.2, -4.2), 1.2), ((-5.0, 8.8, 3.0), 1.3), ((3.2, 11.6, 3.8), 1.0)])
    head(F)
    legs4(F, "skin")
    F += part("tail", spike("tail", "skin", (0, 6.0, 7.6), (0, -0.3, 1), 3.0, 1.0, sides=5, rings=1))
    return F, [eye_at(-1.6, 9.0, -11.6), eye_at(1.6, 9.0, -11.6)]


def build_b(k):
    """B 雷鉄の鎧亀"""
    F = []
    F += part("plastron", ellipsoid("body", "skin", (0, 5.0, 0), (6.0, 1.6, 7.6), segs=10, rings=1))
    F += part("shell", ellipsoid("body", "crag", (0, 9.0, 0), (7.2, 5.2, 8.6), segs=10, rings=3))
    # 銅の板 (前・横・背) と光る継ぎ目
    F += part("plate_top", box("body", "copper", (0, 14.0, 0.4), (6.0, 0.8, 7.0), taper=(0.8, 0.8)))
    for sx in (1, -1):
        F += part(f"plate_side{sx}", box("body", "copper", (sx * 6.6, 9.4, 0), (0.9, 4.0, 10.0)))
        F += part(f"seam{sx}", box("body", "volt", (sx * 4.6, 13.0, 0), (0.6, 0.6, 9.0)))
    F += part("plate_front", box("body", "copper", (0, 10.0, -7.8), (9.0, 4.4, 0.9), taper=(0.85, 1.0)))
    for i in range(4):
        F += part(f"rivet{i}", ellipsoid("body", "dark", ((i - 1.5) * 2.2, 11.4, -8.4), (0.4, 0.4, 0.3), segs=4, rings=1))
    # 避雷の銅の棒
    F += part("rod", tube("body", "copper", [(0, 14.0, 2.0), (0, 21.0, 2.6)], ra=[0.6, 0.4], sides=5, hint=(1, 0, 0)))
    F += part("rod_tip", ellipsoid("body", "volt", (0, 21.4, 2.6), (0.9, 0.9, 0.9), segs=6, rings=1))
    head(F)
    F += part("helm", box("head", "copper", (0, 10.0, -10.6), (4.0, 0.8, 3.6), taper=(0.8, 0.8)))
    legs4(F, "skin", 1.8)
    F += part("tail", spike("tail", "skin", (0, 6.0, 7.6), (0, -0.3, 1), 3.0, 1.0, sides=5, rings=1))
    return F, [eye_at(-1.6, 9.0, -11.6), eye_at(1.6, 9.0, -11.6)]


def build_c(k):
    """C 氷の殻の大団子虫"""
    F = []
    F += part("belly", ellipsoid("body", "dark", (0, 4.6, 0), (5.4, 2.0, 8.2), segs=10, rings=1))
    for i in range(7):
        t = i / 6
        z = -7.0 + 14.0 * t
        k_ = 1 - 0.28 * abs(t - 0.5) * 2
        F += part(f"plate{i}", ellipsoid("body", "ice", (0, 7.0 + 1.8 * k_, z), (6.6 * k_, 4.8 * k_, 1.6), segs=10, rings=2))
        F += part(f"rim{i}", ellipsoid("body", "volt", (0, 7.0 + 1.8 * k_ + 4.6 * k_, z + 0.8), (1.6 * k_, 0.35, 0.5), segs=5, rings=1))
    F += part("skull", ellipsoid("head", "crag", (0, 6.8, -9.0), (3.4, 2.2, 1.8), segs=8, rings=2))
    for sx in (1, -1):
        F += part(f"ant{sx}", tube("head", "crag", [(sx * 1.4, 7.6, -10.4), (sx * 3.0, 9.4, -13.0), (sx * 4.4, 9.0, -14.6)], ra=[0.35, 0.25, 0.05], sides=4, hint=(0, 1, 0)))
    for b, sx, sz in (("leg_fl", 1, -1), ("leg_fr", -1, -1), ("leg_bl", 1, 1), ("leg_br", -1, 1)):
        for j, dz in enumerate((-1.2, 1.2)):
            F += part(f"{b}{j}", tube(b, "dark", [(sx * 4.4, 5.0, sz * 4.6 + dz), (sx * 5.8, 2.4, sz * 4.8 + dz), (sx * 5.6, 0.2, sz * 4.8 + dz)],
                                       ra=[0.55, 0.45, 0.3], sides=4, hint=(1, 0, 0)))
    F += part("tail", box("tail", "ice", (0, 6.0, 8.6), (5.0, 3.0, 1.2), taper=(0.7, 0.6)))
    return F, [eye_at(-1.8, 7.2, -10.5, 0.6), eye_at(1.8, 7.2, -10.5, 0.6)]


def detail(F):
    # 細部: 段の縁の雪の帯・小さな水晶の群れ・脚の鱗・爪・首のしわ
    for i, (y, r) in enumerate(((9.6, 6.6), (12.6, 5.0))):
        for k in range(10):
            a = math.radians(k * 36 + i * 18)
            F += part(f"snow{i}{k}", ellipsoid("body", "snow", (r * math.cos(a), y, 0.3 * i + r * 1.18 * math.sin(a)), (1.0, 0.35, 0.9), segs=5, rings=1))
    for i, (x, z) in enumerate(((3.4, -2.6), (-3.6, 2.2), (2.6, 4.0))):
        F += part(f"cry{i}", spike("body", "volt", (x, 10.6, z), (x * 0.1, 1, z * 0.1), 1.8, 0.45, sides=4, rings=1))
    for b, sx, sz in (("leg_fl", 1, -1), ("leg_fr", -1, -1), ("leg_bl", 1, 1), ("leg_br", -1, 1)):
        for j in range(3):
            F += part(f"{b}_nail{j}", spike(b, "dark", (sx * 6.4 + (j - 1) * 0.6, 0.5, sz * 5.6 - 1.4), (0, -0.2, -1), 0.8, 0.25, sides=3, rings=1))
        F += part(f"{b}_scale", ellipsoid(b, "crag", (sx * 6.2, 3.4, sz * 5.4), (1.0, 0.6, 1.0), segs=5, rings=1))
    for i in range(3):
        F += part(f"neckfold{i}", tube("head", "skin", [(-1.8, 7.0, -6.6 - i * 0.8), (0, 6.2, -6.8 - i * 0.8), (1.8, 7.0, -6.6 - i * 0.8)], ra=[0.25, 0.3, 0.25], sides=4, hint=(0, 0, 1)))
    return F


def build(k):
    F, eyes = {"a": build_a, "b": build_b, "c": build_c}[k](k)
    if k == "a":
        F = detail(F)
    return F, eyes


POSES = {
    "idle": {},
    "guard": {"head": (0, 0, 0), "body": (6, 0, 0), "leg_fl": (0, 0, 30), "leg_fr": (0, 0, -30), "leg_bl": (0, 0, 30), "leg_br": (0, 0, -30)},
    "ram": {"body": (-10, 0, 0), "head": (-20, 0, 0), "leg_fl": (-30, 0, 0), "leg_fr": (-30, 0, 0), "leg_bl": (40, 0, 0), "leg_br": (40, 0, 0)},
}

SPEC = dict(
    id="crag_tortoise", short="峰の甲羅", kind="嵐の敵 3案 (守りを固める)", role="守りを固める", land="嵐",
    title="峰の甲羅 (crag_tortoise) — 嵐の高地・守りを固める・低ポリ 3 案",
    sub=["人が近いと甲羅に籠る (予告 0.8 秒: 首を引く。籠っているあいだ前からの傷 −70%)。籠ったあと 1.0 秒の予告 (甲羅が光る) で前へ体当たり (帯 5)。",
         "正面から叩き続けると削れない。横・後ろへ回り込む・崩しゲージを使う、を覚えさせる役。"],
    labels=["A 峰の甲羅亀", "B 雷鉄の鎧亀", "C 氷の殻の大団子虫"],
    rows=rows_std(8, 10.0, h=320, extra=(("籠る (前からの傷 −70%)", "guard"), ("体当たり", "ram"))),
    options={
        "a": dict(name="A 峰の甲羅亀", desc="甲羅が三段の岩の峰で、頂に雪と青白い水晶。嵐の高地の山がそのまま歩いている見た目。籠ると小さな峰に見える。"),
        "b": dict(name="B 雷鉄の鎧亀", desc="銅の板を打った甲羅、背に避雷の銅の棒、板の継ぎ目が光る。人が鎧を着せたような硬さが一目で分かる。体当たりの前に棒へ雷が落ちる演出が作れる。"),
        "c": dict(name="C 氷の殻の大団子虫", desc="氷の板 7 枚の殻の大きな虫。籠ると丸まって玉の盾になる。灰ひきずり (四章) と形が近いので、土地ごとの違いは色だけになる。")},
    extra="骨 8 本 (root > body > head / leg_fl / leg_fr / leg_bl / leg_br / tail)。",
    budget="地上の敵 400〜700", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
