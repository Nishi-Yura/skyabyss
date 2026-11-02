"""影の綴じ手 (shadow_stitcher) 低ポリ ラフ 3 案。新月の谷の暗がりに住む細い人影 (docs/design/46 の 4.3)。
光の中では見えず、暗がりから長い腕を伸ばして引き寄せる。星灯りの中に引き出すと姿を見せ、斬れる。
体は影 (藍の黒) で、どの案も星の光の「縫い目」(綴じ糸) が走る: 暗がりで唯一見える所で、近づいたことが分かる。
A 縫い合わせた影 (人より細く背の高い人影。胸と腕を白く光る綴じ糸が十字に縫う。指が長い針)
B 布の影 (頭巾を被った裾の長い布の影。脚はなく裾が床を引きずる。縫い目の仮面の顔)
C 裂けた帯の影 (胴が何本もの影の帯に裂け、帯の端を綴じ糸が留める。頭は空ろな輪で、輪の中に星の目)
骨: root > body > head / arm_l / arm_r / leg_l / leg_r。身長 2.4 ブロック (38 単位)、腕は膝まで届く。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 18, 0)), "head": ("body", (0, 33, 0)),
         "arm_l": ("body", (-4.2, 30, 0)), "arm_r": ("body", (4.2, 30, 0)), "leg_l": ("body", (-1.8, 18, 0)), "leg_r": ("body", (1.8, 18, 0))}
PAL = make_pal({
    "shadow": (SHADOW, 0.5, dict(crack=0.04, noise=0.18)),
    "shade2": (INDIGO_F, 0.5, dict(crack=0.06, noise=0.2)),
    "hollow": (SHADOW, 0.4, dict(crack=0.0, noise=0.04, min=0)),
    "thread": (STAR, 0.85, dict(crack=0.0, noise=0.04, min=1)),
}, glow=["thread"])


def stitch(F, bone, pts, r=0.22, name="st"):
    F += part(name, tube(bone, "thread", pts, ra=r, sides=4, hint=(0, 0, 1)))


def long_arm(F, side, m="shadow", needles=True):
    b = "arm_l" if side < 0 else "arm_r"
    x = side * 4.2
    F += part(f"up{side}", tube(b, m, [(x, 30, 0), (x + side * 1.0, 22, 0.4)], ra=[1.0, 0.8], sides=6, hint=(1, 0, 0)))
    F += part(f"fo{side}", tube(b, m, [(x + side * 1.0, 22, 0.4), (x + side * 1.4, 13, -0.6)], ra=[0.8, 0.55], sides=6, hint=(1, 0, 0)))
    F += part(f"hand{side}", ellipsoid(b, m, (x + side * 1.4, 12.2, -0.8), (0.75, 1.0, 0.6), segs=6, rings=2))
    for j in range(4):
        dx = (j - 1.5) * 0.35
        F += part(f"fing{side}{j}", spike(b, "thread" if needles else m, (x + side * 1.4 + dx, 11.4, -0.9), (dx * 0.2, -1, -0.15), 3.6 if needles else 2.4, 0.2, sides=4, rings=2))


def build_a(k):
    """A 縫い合わせた影: 細い胴 (輪を積む)、長い腕と脚。胸と腕を光る綴じ糸が十字に縫う。指は長い針。"""
    F = []
    rings = [ring(17.6, 2.0, -1.3, 1.3, n=9), ring(21.0, 1.7, -1.2, 1.2, n=9), ring(25.0, 2.6, -1.6, 1.5, n=9),
             ring(29.0, 3.2, -1.7, 1.6, n=9), ring(31.4, 1.6, -1.1, 1.1, n=9), ring(32.6, 0.9, -0.8, 0.8, n=9)]
    F += part("torso", loft("body", "shadow", rings, caps=("shadow", "shadow")))
    F += part("head", ellipsoid("head", "shadow", (0, 35.0, -0.2), (1.7, 2.6, 1.8), segs=9, rings=4))
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"leg{s}", tube(b, "shadow", [(s * 1.8, 18, 0), (s * 2.0, 9, 0.6), (s * 1.8, 1.2, -0.4)], ra=[1.0, 0.75, 0.5], sides=6, hint=(1, 0, 0)))
        F += part(f"foot{s}", ellipsoid(b, "shadow", (s * 1.8, 0.6, -1.2), (0.7, 0.5, 1.6), segs=6, rings=2))
    long_arm(F, -1)
    long_arm(F, 1)
    # 綴じ糸: 胸の十字、腕の縫い目、口を縫う糸
    for i in range(4):
        y = 22.0 + i * 2.2
        stitch(F, "body", [(-1.4, y, -1.75), (1.4, y + 1.2, -1.75)], name=f"x1{i}")
        stitch(F, "body", [(1.4, y, -1.75), (-1.4, y + 1.2, -1.75)], name=f"x2{i}")
    for s, b in ((-1, "arm_l"), (1, "arm_r")):
        stitch(F, b, [(s * 4.6, 27, -0.85), (s * 5.2, 16, -0.9)], r=0.18, name=f"arm{s}")
    for j in range(3):
        stitch(F, "head", [((j - 1) * 0.5, 33.6, -1.95), ((j - 1) * 0.5, 34.4, -1.95)], r=0.14, name=f"mouth{j}")
    return F, [eye_at(-0.7, 35.6, -1.95, 0.3), eye_at(0.7, 35.6, -1.95, 0.3)]


def build_b(k):
    """B 布の影: 頭巾を被った裾の長い布。脚はなく、裾が床を引きずる。縫い目の仮面の顔、袖から長い腕。"""
    F = []
    rings = [ring(0.4, 5.4, -4.6, 4.8, n=12), ring(6.0, 4.4, -3.6, 3.8, n=12), ring(14.0, 3.4, -2.6, 2.8, n=12),
             ring(22.0, 3.2, -2.2, 2.4, n=12), ring(28.6, 3.6, -2.0, 2.2, n=12), ring(31.6, 2.0, -1.4, 1.6, n=12)]
    F += part("robe", loft("body", "shade2", rings, caps=("shadow", "shade2")))
    for i in range(6):
        a = i / 6 * 2 * math.pi
        F += part(f"tatter{i}", spike("body", "shadow", (math.cos(a) * 4.8, 1.2, math.sin(a) * 4.2), (math.cos(a) * 0.4, -1, math.sin(a) * 0.4), 2.2, 0.8, sides=4, rings=1))
    hood = [ring(31.0, 2.6, -2.4, 2.6, n=10), ring(34.0, 2.8, -2.2, 2.8, n=10), ring(37.0, 2.0, -1.0, 2.2, n=10), ring(38.6, 0.8, 0.2, 1.2, n=10)]
    F += part("hood", loft("head", "shade2", hood, caps=("shadow", "shade2")))
    F += part("mask", ellipsoid("head", "hollow", (0, 34.2, -2.2), (1.7, 2.1, 0.6), segs=8, rings=3))
    stitch(F, "head", [(0, 36.0, -2.75), (0, 32.4, -2.75)], r=0.16, name="mseam")
    for j in range(4):
        y = 32.8 + j * 0.9
        stitch(F, "head", [(-0.5, y, -2.75), (0.5, y, -2.75)], r=0.13, name=f"mx{j}")
    for i in range(5):
        y = 6.0 + i * 4.6
        stitch(F, "body", [(0, y, -4.4 + i * 0.4), (0, y + 3.6, -4.0 + i * 0.4)], r=0.2, name=f"front{i}")
    long_arm(F, -1, "shadow")
    long_arm(F, 1, "shadow")
    return F, [eye_at(-0.65, 35.0, -2.75, 0.28), eye_at(0.65, 35.0, -2.75, 0.28)]


def build_c(k):
    """C 裂けた帯の影: 胴が何本もの影の帯に裂けて揺れる。帯の端を綴じ糸が留める。頭は空ろな輪、輪の中に星の目。"""
    F = []
    F += part("core", ellipsoid("body", "shadow", (0, 27.0, 0), (3.0, 3.6, 1.8), segs=9, rings=4))
    for i in range(7):
        a = (i - 3) * 0.28
        x0 = math.sin(a) * 2.2
        pts = [(x0, 25, -0.3), (x0 * 1.6, 17, 0.4 + 0.2 * i), (x0 * 2.0 + 0.4 * (i % 2), 8, 0.0), (x0 * 2.2, 2.0, -0.4)]
        F += part(f"band{i}", tube("body", "shadow" if i % 2 else "shade2", pts, ra=[0.9, 0.7, 0.5, 0.25], sides=5, hint=(0, 0, 1)))
        stitch(F, "body", [(x0 * 1.6 - 0.3, 17, 0.4 + 0.2 * i - 0.6), (x0 * 1.6 + 0.3, 17.6, 0.4 + 0.2 * i - 0.6)], r=0.16, name=f"pin{i}")
    # 頭: 空ろな輪
    for i in range(10):
        a0, a1 = i / 10 * 2 * math.pi, (i + 1) / 10 * 2 * math.pi
        F += part(f"halo{i}", tube("head", "shadow", [(math.cos(a0) * 2.2, 35.6 + math.sin(a0) * 2.2, 0), (math.cos(a1) * 2.2, 35.6 + math.sin(a1) * 2.2, 0)],
                                   ra=0.55, sides=5, hint=(0, 0, 1)))
    F += part("neck", tube("head", "shadow", [(0, 30.0, 0), (0, 33.4, 0)], ra=[1.0, 0.6], sides=6, hint=(1, 0, 0)))
    F += part("eyeorb", ellipsoid("head", "thread", (0, 35.6, 0), (0.7, 0.7, 0.7), segs=6, rings=3))
    long_arm(F, -1, "shade2")
    long_arm(F, 1, "shade2")
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "reach": {"arm_r": (-75, 0, 10), "arm_l": (-20, 0, -15), "body": (-10, 0, 0), "head": (-12, 0, 0)},
}

SPEC = dict(
    id="shadow_stitcher", short="影の綴じ手", kind="新月の谷の敵 3案 (46 の 4.3)",
    title="影の綴じ手 (shadow_stitcher) — 暗がりから長い腕で引き寄せる細い人影・低ポリ + ドット絵 3 案",
    sub=["光の中では見えない (暗がりでは星の光の縫い目だけが見える)。暗がりから長い腕を伸ばして引き寄せ、星灯りの中に引き出すと姿を見せて斬れる。",
         "身長 2.4 ブロック。二段目は腕を伸ばして掴みにくる姿 (この敵の決め絵)。"],
    labels=["A 縫い合わせた影", "B 布の影", "C 裂けた帯の影"],
    rows=[("前 3/4", "idle", 212, 12, 300, (0, 19, 0), 7.0),
          ("横", "idle", 270, 4, 300, (0, 19, 0), 7.0),
          ("腕を伸ばす", "reach", 220, 8, 300, (0, 19, 0), 7.0),
          ("後ろ 3/4", "idle", 32, 12, 300, (0, 19, 0), 7.0)],
    options={
        "a": dict(name="A 縫い合わせた影", desc="人より細く背の高い人影。胸と腕を星の光の綴じ糸が十字に縫い、口も縫われている。指は長い光る針。"),
        "b": dict(name="B 布の影", desc="頭巾と裾の長い布の影。脚がなく裾を引きずる。縫い目の仮面。暗がりでは仮面の縫い目と袖の針だけが浮かぶ。"),
        "c": dict(name="C 裂けた帯の影", desc="胴が影の帯に裂けて揺れ、帯の端を綴じ糸が留める。頭は空ろな輪で、中に星の目が一つ。いちばん「人でない」。")},
    extra="月の言葉 (影の藍の黒・星の光の綴じ糸)。骨 7 本 (root > body > head / 腕 2 / 脚 2)。光るのは綴じ糸だけ。",
    budget="中の敵 600〜1000", atlas_h=150, pw=420,
    later="二段目 = 綴じ糸が増え、腕が三本になる。三段目 = 背から影の帯が広がり、針の指が倍の長さ。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
