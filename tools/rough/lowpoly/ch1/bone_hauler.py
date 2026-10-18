"""骨負い (bone_hauler) 低ポリ ラフ 3 案。苔の生えた四つ足の獣。背に骨の籠を負う。非敵対、HP 16。
人を見ると逃げ、骨の集め場 (bone_cache) へ道を案内する。逃げる姿 (run) が一番大事な読み。
A 箱籠 (低く太い体。骨の角張った籠を背に載せる) / B 円錐の籠 (高い脚。細い円錐の籠に骨の輪) / C 両脇の袋 (丸い体。脇に袋が二つ垂れ、苔のたてがみ)
"""
import math

from ck import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 8.0, 0)),
         "head": ("body", (0, 9.0, -9.0)), "ear_l": ("head", (-1.6, 11.0, -10.2)), "ear_r": ("head", (1.6, 11.0, -10.2)),
         "basket": ("body", (0, 11.0, 0)), "tail": ("body", (0, 9.0, 9.0)),
         "leg_fl": ("body", (-3.0, 7.0, -6.0)), "leg_fr": ("body", (3.0, 7.0, -6.0)),
         "leg_bl": ("body", (-3.0, 7.0, 6.0)), "leg_br": ("body", (3.0, 7.0, 6.0))}

MOSSY = [(30, 24, 20), (58, 48, 36), (92, 78, 52), (126, 108, 68)]
HIDE = [(34, 26, 24), (66, 52, 44), (100, 84, 68), (134, 118, 96)]
PAL = {"mats": {
    "hide": mat(HIDE, 0.6, crack=0.0, noise=0.25, stain=dict(ramp=MOSS, thr=0.82, up=0.4, conc=0.3)),
    "moss": mat(MOSS, 0.6, crack=0.0, noise=0.3),
    "bone": mat(BONE, 0.7, crack=0.05),
    "dark": mat(DARK, 0.5, crack=0.0, min=0),
    "flesh": mat([(40, 34, 36), (80, 66, 60), (118, 98, 84), (152, 130, 108)], 0.62, crack=0.0, noise=0.2),
}, "skull_bone": "head", "face_mat": "flesh"}


def eye_at(x, y, z, r=0.9):
    return dict(c=V(x, y, z), rx=r, ry=r * 0.6, pr=(0.55, 0.38), tilt=0.15, zr=2.2)


def legs(F, hip_y, foot_len, thick):
    """四本の脚。骨は hip (pivot)。足の先は地面 (y=0)。"""
    for nm, s, z in (("fl", -1, -6.0), ("fr", 1, -6.0), ("bl", -1, 6.0), ("br", 1, 6.0)):
        b = "leg_" + nm
        F += part(b, tube(b, "hide", [(s * 3.2, hip_y, z), (s * 3.4, hip_y * 0.5, z + 0.2), (s * 3.5, 0.0, z + 0.4)],
                          ra=[thick, thick * 0.85, thick * 0.8], sides=5, hint=(1, 0, 0)))
        F += part(b + "f", ellipsoid(b, "dark", (s * 3.5, 0.6, z + 0.9), (1.1, 0.6, 1.5), segs=5, rings=1))


def ring_loop(cx, cy, cz, r, n=8):
    """円 (y 軸まわり) の輪。閉じた tube の経路。"""
    pts = [V(cx + r * math.sin(2 * math.pi * k / n), cy, cz - r * math.cos(2 * math.pi * k / n)) for k in range(n)]
    return pts + [pts[0]]


def build_a(k):
    """A 箱籠: 低く太い体。骨の角張った籠 (八角の輪を三段) を背に。上は開いて暗い口、骨が突き出す。"""
    F, eyes = [], []
    F += part("body", ellipsoid("body", "hide", (0, 8.0, 0), (4.6, 4.0, 9.0), segs=9, rings=4))
    F += part("head", ellipsoid("head", "hide", (0, 8.6, -11.4), (3.0, 2.8, 3.2), segs=8, rings=3))
    F += part("snout", ellipsoid("head", "flesh", (0, 7.6, -14.4), (1.7, 1.4, 1.6), segs=6, rings=2))
    for s in (-1, 1):
        F += part("ear_l" if s < 0 else "ear_r", spike("ear_l" if s < 0 else "ear_r", "hide", (s * 1.6, 11.0, -10.2),
                  (s * 0.5, 1, -0.2), 2.6, 0.9, sides=4, rings=2, hint=(1, 0, 0)))
    F += part("basket", loft("basket", "bone", [octa(11.4, 4.2, 5.0, 1.0), octa(13.6, 4.7, 5.5, 1.0), octa(16.0, 4.5, 5.2, 1.0)],
                             caps=(False, "dark")))
    F += part("rim", tube("basket", "bone", octa(16.0, 4.5, 5.2, 1.0) + [octa(16.0, 4.5, 5.2, 1.0)[0]], ra=0.4, sides=3))
    for x, z in ((-2.4, -2.0), (2.4, 1.6), (0.6, -3.6)):
        F += part(f"stick{x}{z}", spike("basket", "bone", (x, 15.6, z), (x * 0.1, 1, z * 0.1), 3.0, 0.5, sides=3, rings=2, hint=(1, 0, 0)))
    F += part("tail", tube("tail", "hide", [(0, 8.6, 8.6), (0, 9.4, 11.0), (0, 10.0, 12.6)], ra=[1.0, 0.7, 0.2], sides=5, hint=(1, 0, 0)))
    eyes = [eye_at(-2.4, 9.4, -13.2, 0.7), eye_at(2.4, 9.4, -13.2, 0.7)]
    legs(F, 7.0, 7.0, 1.4)
    return F, eyes


def octa(y, hx, hz, c=1.0):
    return [V(hx - c, y, -hz), V(hx, y, -hz + c), V(hx, y, hz - c), V(hx - c, y, hz),
            V(-hx + c, y, hz), V(-hx, y, hz - c), V(-hx, y, -hz + c), V(-hx + c, y, -hz)]


def build_b(k):
    """B 円錐の籠: 脚が高い細身の獣。細い円錐の籠を背に、骨の輪を二つ。頂に骨が立つ。首が長く頭が上がる。"""
    F, eyes = [], []
    F += part("body", ellipsoid("body", "hide", (0, 9.0, 0), (4.0, 4.2, 8.0), segs=9, rings=4))
    F += part("neck", tube("head", "hide", [(0, 9.4, -6.0), (0, 10.6, -9.0), (0, 11.0, -10.0)], ra=[2.1, 1.9, 1.9], sides=6, hint=(1, 0, 0)))
    F += part("head", ellipsoid("head", "hide", (0, 11.0, -11.6), (2.6, 2.6, 3.0), segs=8, rings=3))
    F += part("snout", ellipsoid("head", "flesh", (0, 10.0, -14.4), (1.3, 1.1, 1.2), segs=6, rings=2))
    for s in (-1, 1):
        F += part("ear_l" if s < 0 else "ear_r", spike("ear_l" if s < 0 else "ear_r", "hide", (s * 1.3, 13.2, -11.0),
                  (s * 0.3, 1, -0.2), 3.0, 0.7, sides=4, rings=2, hint=(1, 0, 0)))
    r0 = [11.5, 4.0], [14.6, 3.0], [19.0, 1.6]
    F += part("basket", loft("basket", "bone", [ring(y, r, r, r, 0, 8) for y, r in (r0[0], r0[1], r0[2])], caps=(False, "dark")))
    F += part("hoop1", tube("basket", "bone", ring_loop(0, 12.6, 0, 3.9, 8), ra=0.45, sides=3))
    F += part("hoop2", tube("basket", "bone", ring_loop(0, 16.8, 0, 2.4, 8), ra=0.4, sides=3))
    F += part("top", spike("basket", "bone", (0, 18.6, 0), (0, 1, 0), 3.4, 0.6, sides=3, rings=2, hint=(1, 0, 0)))
    F += part("tail", tube("tail", "hide", [(0, 9.4, 8.6), (0, 10.2, 11.0), (0, 10.6, 13.0)], ra=[1.0, 0.7, 0.2], sides=5, hint=(1, 0, 0)))
    eyes = [eye_at(-2.3, 11.6, -12.6, 0.7), eye_at(2.3, 11.6, -12.6, 0.7)]
    legs(F, 9.0, 9.0, 1.2)
    return F, eyes


def build_c(k):
    """C 両脇の袋: 丸く横に広い体。脇に骨の輪を巻いた袋が二つ垂れ、背と首に苔のたてがみ。短く太い脚。"""
    F, eyes = [], []
    F += part("body", ellipsoid("body", "hide", (0, 8.0, 0), (5.4, 4.4, 7.6), segs=8, rings=3))
    F += part("head", ellipsoid("head", "hide", (0, 8.8, -10.4), (3.0, 2.9, 3.1), segs=8, rings=3))
    F += part("snout", ellipsoid("head", "flesh", (0, 8.0, -13.4), (1.6, 1.3, 1.4), segs=6, rings=2))
    for s in (-1, 1):
        F += part("ear_l" if s < 0 else "ear_r", spike("ear_l" if s < 0 else "ear_r", "hide", (s * 2.2, 11.4, -9.8),
                  (s * 0.8, 0.6, -0.3), 2.6, 0.9, sides=4, rings=2, hint=(1, 0, 0)))
    F += part("mane", ellipsoid("body", "moss", (0, 11.6, -2.6), (3.6, 1.9, 4.6), segs=7, rings=2))
    for s in (-1, 1):
        cx = s * 6.8
        F += part(f"bag{s}", ellipsoid("body", "bone", (cx, 7.6, 0.5), (2.3, 3.4, 4.6), segs=6, rings=2))
        F += part(f"bagring{s}", tube("body", "bone", ring_loop(cx, 7.6, 0.5, 3.9, 8), ra=0.45, sides=3))
        F += part(f"bagtop{s}", spike("body", "bone", (cx, 10.4, 0.5), (s * 0.2, 1, 0), 2.6, 0.5, sides=3, rings=2, hint=(1, 0, 0)))
    F += part("tail", tube("tail", "hide", [(0, 8.8, 8.2), (0, 9.2, 10.6), (0, 9.6, 12.4)], ra=[1.0, 0.7, 0.2], sides=5, hint=(1, 0, 0)))
    eyes = [eye_at(-2.4, 9.4, -12.6, 0.7), eye_at(2.4, 9.4, -12.6, 0.7)]
    legs(F, 7.0, 6.0, 1.6)
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


SW = {"leg_fl": (30, 0, 0), "leg_fr": (30, 0, 0), "leg_bl": (-30, 0, 0), "leg_br": (-30, 0, 0)}
SW2 = {"leg_fl": (-30, 0, 0), "leg_fr": (-30, 0, 0), "leg_bl": (30, 0, 0), "leg_br": (30, 0, 0)}
POSES = {
    "idle": {"head": (-16, 0, 0)},
    "run": dict(SW, body=(3, 0, 0), head=(6, 0, 0), basket=(4, 0, 0), tail=(-18, 0, 0)),
    "run2": dict(SW2, body=(3, 0, 0), head=(6, 0, 0), basket=(-3, 0, 0), tail=(-10, 0, 0)),
}

SPEC = dict(
    id="bone_hauler", short="骨負い", kind="中立の獣 3案 (非敵対・逃走)",
    title="骨負い (bone_hauler) — 背の骨籠・低ポリ 3 案",
    sub=["苔の生えた四つ足。背に骨の籠を負う。人を見ると逃げ、骨の集め場へ道を案内する。HP 16 (非敵対)。",
         "逃げる姿 (run) が読みの中心。左の黄の目盛りは人 (1.8 ブロック)。全身の長さ 約 24 単位。"],
    labels=["A 箱籠", "B 円錐の籠", "C 両脇の袋"],
    rows=[("横 (待機: 頭を下げて草を食む)", "idle", 270, 6, 400, (0, 9, 0), 16.0),
          ("前 3/4 (待機)", "idle", 212, 14, 400, (0, 9, 0), 16.0),
          ("逃げる 1 (横)", "run", 270, 6, 400, (0, 9, 0), 16.0),
          ("逃げる 1 (前 3/4)", "run", 212, 14, 400, (0, 9, 0), 16.0),
          ("逃げる 2 (横)", "run2", 270, 6, 400, (0, 9, 0), 16.0)],
    options={
        "a": dict(name="A 箱籠", desc="低く太い体。骨の角張った箱籠を背に載せ、上の口から骨が突き出す。頭は前へ低く、耳は骨の角。逃げるとき籠が前後に揺れて、重い荷が跳ねるのが見える。苔は体の上と箱籠の縁。"),
        "b": dict(name="B 円錐の籠", desc="脚が高く、細い首の先に頭が上がる。細い円錐の籠に骨の輪が二つ。頂に一本の骨が立つ。逃げる姿は縦に伸びて、遠くからでも背の高い影で読める。他の二案より一番細身。"),
        "c": dict(name="C 両脇の袋", desc="丸く横に広い体。脇に骨の輪を巻いた袋が二つ垂れ、背と首に苔のたてがみ。脚は短く太い。逃げると袋が揺れる。横の幅が一番広く、三案の中で一番どっしりした影。")},
    extra="一章の言葉 (苔・骨の籠・琥珀の目・革の灰茶)。骨 11 本 (root > body > head / ear×2 / basket / tail / leg×4)。脚は hip を骨の pivot にして前後に振る (run / run2)。籠は body の子の骨なので、体の動きと一緒に振れる。",
    budget="地上の小物 300〜500", atlas_h=300)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
