"""雷雲くらげ (storm_jelly)。嵐の高地の敵 (issue #20)。役: まわりに効く。Lv 20〜24。
地面から 2〜3 ブロック浮いて漂い、人に寄ってくる。予告 1.2 秒 (体が縮んで光が強まり、足もとに半径 5 の円) のあと放電: 円の中に傷 + 痺れ (0.6 秒の足止め)。
放電のあと 3 秒は光が消えて柔らかい (受ける傷 +30%)。骨: root > bell > core / t0..t5 (垂れる触手)。
A 雲のくらげ: こぶの重なった雷雲の傘、中に光る芯、光の糸の触手
B 雷の目玉の雲: 丸い雲の塊に大きな一つ目、避雷針のような棘が四方へ
C 帯電した浮き魚: 膨らんだ灰青の魚、稲妻形の鰭、背に青白い棘
"""
import math

from lf import *  # noqa: F401,F403
from lf import box, run, part, mat, eye_at, rows_std, storm

storm(True)
BONES = {"root": (None, (0, 0, 0)), "bell": ("root", (0, 40.0, 0)), "core": ("bell", (0, 40.0, 0))}
for i in range(6):
    a = math.radians(i * 60 + 30)
    BONES[f"t{i}"] = ("bell", (3.2 * math.cos(a), 36.0, 3.2 * math.sin(a)))
PAL = {"mats": {"cloud": mat(CLOUD, 0.62, noise=0.22), "snow": mat(SNOW, 0.7, noise=0.1), "volt": mat(VOLT, 0.78, min=1),
                "crag": mat(CRAG, 0.6, noise=0.2), "dark": mat([(14, 14, 20), (28, 28, 36), (44, 44, 54)], 0.5),
                "fish": mat([(54, 66, 86), (80, 96, 120), (112, 130, 156), (150, 166, 190)], 0.6, noise=0.12)},
       "skull_bone": "bell", "face_mat": "cloud", "glow_mats": ["volt"]}


def tentacles(F, mat_="volt", n=6, length=12.0, r=0.45):
    for i in range(n):
        a = math.radians(i * 60 + 30)
        x, z = 3.2 * math.cos(a), 3.2 * math.sin(a)
        pts = [(x, 37.0, z), (x * 1.2 + 0.6 * math.sin(a * 3), 37.0 - length * 0.35, z * 1.2), (x * 0.9, 37.0 - length * 0.7, z * 0.9 + 0.6),
               (x * 1.1, 37.0 - length, z * 1.1)]
        F += part(f"t{i}", tube(f"t{i}", mat_, pts, ra=[r, r * 0.8, r * 0.6, 0.05], sides=4, hint=(0, 0, 1)))


def build_a(k):
    """A 雲のくらげ"""
    F = []
    for i, (c, rr) in enumerate((((0, 42.0, 0), (6.0, 3.6, 6.0)), ((3.2, 44.2, 1.0), (3.2, 2.6, 3.2)), ((-3.0, 44.0, -0.6), (3.4, 2.8, 3.2)),
                                 ((0.4, 45.4, -2.4), (3.0, 2.4, 2.8)), ((0, 39.6, 0), (5.0, 1.6, 5.0)))):
        F += part(f"puff{i}", ellipsoid("bell", "cloud" if i < 4 else "snow", c, rr, segs=9, rings=3 if i == 0 else 2))
    F += part("core", ellipsoid("core", "volt", (0, 38.4, 0), (2.6, 1.8, 2.6), segs=8, rings=2))
    tentacles(F)
    return F, []


def build_b(k):
    """B 雷の目玉の雲"""
    F = []
    F += part("ball", ellipsoid("bell", "cloud", (0, 41.0, 0), (6.4, 5.6, 6.4), segs=10, rings=4))
    for i, (c, rr) in enumerate((((4.0, 44.6, 2.0), (3.0, 2.4, 3.0)), ((-4.2, 44.0, 1.4), (3.0, 2.6, 3.0)), ((0, 46.0, 2.6), (3.2, 2.2, 3.0)))):
        F += part(f"lump{i}", ellipsoid("bell", "cloud", c, rr, segs=8, rings=2))
    # 大きな一つ目 (白目と光る瞳)
    F += part("eyeball", ellipsoid("core", "snow", (0, 41.4, -5.2), (3.0, 3.0, 1.6), segs=8, rings=3))
    F += part("pupil", ellipsoid("core", "volt", (0, 41.4, -6.5), (1.3, 1.6, 0.5), segs=6, rings=2))
    F += part("lid", ellipsoid("bell", "dark", (0, 44.0, -4.6), (3.6, 1.0, 1.8), segs=8, rings=1))
    # 避雷針の棘
    for i, (d, ln) in enumerate((((1, 0.6, 0), 5.0), ((-1, 0.6, 0), 5.0), ((0, 1, 0.2), 6.0), ((0.5, 0.4, 0.8), 4.0), ((-0.5, 0.4, 0.8), 4.0))):
        n = np.array(d, float) / np.linalg.norm(d)
        base = np.array([0, 41.0, 0]) + n * np.array([5.4, 4.8, 5.4])
        F += part(f"rod{i}", spike("bell", "crag", base, d, ln, 0.6, sides=4, rings=2))
        F += part(f"rodtip{i}", ellipsoid("bell", "volt", base + n * ln * 0.95, (0.5, 0.5, 0.5), segs=5, rings=1))
    tentacles(F, "volt", length=6.0, r=0.35)
    return F, []


def build_c(k):
    """C 帯電した浮き魚"""
    F = []
    F += part("body", ellipsoid("bell", "fish", (0, 41.0, 0), (5.0, 5.4, 6.4), segs=10, rings=4))
    F += part("belly", ellipsoid("bell", "snow", (0, 37.6, -0.6), (3.6, 2.2, 4.8), segs=8, rings=2))
    F += part("mouth", ellipsoid("bell", "dark", (0, 40.0, -6.2), (1.6, 1.0, 0.8), segs=6, rings=1))
    # 稲妻形の鰭 (左右と尾)
    for sx in (1, -1):
        F += part(f"fin{sx}", tube("bell", "volt", [(sx * 4.6, 41.0, -1.0), (sx * 7.4, 42.6, 0.4), (sx * 6.6, 40.6, 1.0), (sx * 8.8, 41.4, 2.6)],
                                    ra=[0.9, 0.6, 0.5, 0.05], sides=4, hint=(0, 1, 0)))
    F += part("tail", tube("bell", "fish", [(0, 41.0, 5.6), (0, 41.6, 8.4)], ra=[2.2, 1.0], sides=6, hint=(1, 0, 0)))
    F += part("tailfin", tube("bell", "volt", [(0, 41.6, 8.2), (0, 44.4, 10.2), (0, 41.4, 9.8), (0, 38.6, 10.4)], ra=[0.8, 0.5, 0.5, 0.05], sides=4, hint=(1, 0, 0)))
    # 背の棘
    for i in range(7):
        a = math.radians(-60 + i * 20)
        base = (5.0 * math.sin(a) * 0.9, 41.0 + 5.2 * math.cos(a) * 0.95, 1.0 - i * 0.2)
        F += part(f"spine{i}", spike("bell", "volt", base, (math.sin(a), math.cos(a), 0.2), 2.6, 0.45, sides=4, rings=1))
    return F, [eye_at(-3.4, 42.2, -4.4, 0.8), eye_at(3.4, 42.2, -4.4, 0.8)]


def detail(F):
    # 細部: 傘の縁の房 (下向きの小さな雲の舌)・触手の光る節・傘の上の小さな稲妻
    for i in range(14):
        a = math.radians(i * 360 / 14)
        F += part(f"frill{i}", spike("bell", "snow", (5.4 * math.cos(a), 39.6, 5.4 * math.sin(a)), (0.2 * math.cos(a), -1, 0.2 * math.sin(a)), 1.6, 0.6, sides=4, rings=1))
    for i in range(6):
        a = math.radians(i * 60 + 30)
        for j, y in enumerate((33.0, 29.0)):
            F += part(f"bead{i}{j}", ellipsoid(f"t{i}", "volt", (3.4 * math.cos(a), y, 3.4 * math.sin(a)), (0.45, 0.45, 0.45), segs=5, rings=1))
    for i, (x, z) in enumerate(((2.0, 1.0), (-1.6, -1.2), (0.4, 2.2))):
        F += part(f"zap{i}", tube("bell", "volt", [(x, 46.0, z), (x + 0.6, 47.2, z), (x - 0.2, 47.8, z), (x + 0.4, 49.0, z)], ra=[0.25, 0.22, 0.2, 0.05], sides=4, hint=(1, 0, 0)))
    return F


def build(k):
    F, eyes = {"a": build_a, "b": build_b, "c": build_c}[k](k)
    if k == "a":
        F = detail(F)
    return F, eyes


POSES = {
    "idle": {},
    "charge": {"bell": (0, 0, 0), "t0": (40, 0, 0), "t1": (40, 0, 0), "t2": (40, 0, 0), "t3": (-40, 0, 0), "t4": (-40, 0, 0), "t5": (-40, 0, 0)},
    "burst": {"t0": (-50, 0, 0), "t1": (-50, 0, 0), "t2": (-50, 0, 0), "t3": (50, 0, 0), "t4": (50, 0, 0), "t5": (50, 0, 0)},
}

SPEC = dict(
    id="storm_jelly", short="雷雲くらげ", kind="嵐の敵 3案 (まわりに効く)", role="まわりに効く", land="嵐",
    title="雷雲くらげ (storm_jelly) — 嵐の高地・まわりに効く・低ポリ 3 案",
    sub=["地面から 2〜3 ブロック浮いて漂う。予告 1.2 秒 (縮んで光が強まり、足もとに半径 5 の円) のあと放電: 傷 + 0.6 秒の足止め。",
         "放電のあと 3 秒は光が消えて柔らかい (受ける傷 +30%)。近寄って叩くか、離れて待つかを選ばせる役。"],
    labels=["A 雲のくらげ", "B 雷の目玉の雲", "C 帯電した浮き魚"],
    rows=rows_std(36, 9.0, h=380, extra=(("予告: 縮む", "charge"), ("放電", "burst"))),
    options={
        "a": dict(name="A 雲のくらげ", desc="こぶの重なった雷雲の傘の下に光る芯、光の糸の触手 6 本。放電の前に糸が縮んで芯が明るくなるので、予告が一番読みやすい。"),
        "b": dict(name="B 雷の目玉の雲", desc="丸い雲の塊に大きな一つ目、避雷針のような棘が四方へ。目が人を追い、放電のときに瞳が光る。少し怖い見た目。"),
        "c": dict(name="C 帯電した浮き魚", desc="膨らんだ灰青の魚が宙に浮く。稲妻形の鰭と背の青白い棘、腹の白。放電の前に膨らむ (フグの見立て)。嵐の生き物らしさは一番弱い。")},
    extra="骨 9 本 (root > bell > core / t0..t5)。B・C は触手の骨が短い / 無い (同じ骨の並びで動かせる)。",
    budget="地上の敵 400〜700", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
