"""棘うさぎ (spine_hare) 低ポリ ラフ 3 案。夜の草原の野うさぎ。HP 12。アーチャー型の予習。
背の骨の棘は寝ている (通常)。予告 0.5 秒で、しゃがんで棘を立てる (立った棘が予告そのもの)。
一本をまっすぐ前へ撃って、後ろ脚で跳ねて逃げる。背の棘は一本ずつ別の骨 (spine0〜4) で、予告の回転だけで立つ。
A 長耳 (細身で背の弧が高い。長い耳が立つ) / B 毛玉 (丸い体。耳は短く寝て、扇形の棘の冠) / C 細長い (長い首と低い横の線。耳は寝て、長い尾)
"""
import math

from ck import *  # noqa

SPINE_Z = [-4.6, -2.3, 0.0, 2.3, 4.6]


def back_y(z, cy=6.5, ry=3.8, rz=6.5):
    """背の表面の高さ (胴の楕円体の上端)。棘の根元を置く高さ。"""
    return cy + ry * math.sqrt(max(0.0, 1.0 - (z / rz) ** 2))


BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 6.5, 0)), "head": ("body", (0, 8.0, -6.0)),
         "ear_l": ("head", (-1.1, 10.4, -6.4)), "ear_r": ("head", (1.1, 10.4, -6.4)),
         "fore_l": ("body", (-2.2, 5.6, -4.0)), "fore_r": ("body", (2.2, 5.6, -4.0)),
         "hind_l": ("body", (-3.0, 5.2, 3.5)), "hind_r": ("body", (3.0, 5.2, 3.5)),
         "tail": ("body", (0, 7.0, 6.6))}
for _i, _z in enumerate(SPINE_Z):
    BONES["spine%d" % _i] = ("body", (0.0, back_y(_z) - 0.4, _z))

FUR = [(34, 30, 30), (66, 58, 52), (100, 90, 78), (136, 124, 106), (168, 156, 134)]
PAL = {"mats": {
    "fur": mat(FUR, 0.6, crack=0.0, noise=0.25),
    "flesh": mat([(40, 34, 36), (80, 66, 60), (118, 98, 84), (152, 130, 108)], 0.62, crack=0.0, noise=0.2),
    "bone": mat(BONE, 0.7, crack=0.05),
    "dark": mat(DARK, 0.5, crack=0.0, min=0),
}, "skull_bone": "head", "face_mat": "flesh"}


def eye_at(x, y, z, r=0.9):
    return dict(c=V(x, y, z), rx=r, ry=r * 0.6, pr=(0.55, 0.38), tilt=0.15, zr=2.2)


def spines(F, lens, rs, dirs=None):
    """背の棘 5 本。各棘は根元が骨の pivot (寝た棘は pose で後ろへ倒す)。"""
    for i, z in enumerate(SPINE_Z):
        b = "spine%d" % i
        base = (0.0, back_y(z) - 0.4, z)
        d = (0, 1, 0) if dirs is None else dirs[i]
        F += part(b, spike(b, "bone", base, d, lens[i], rs, sides=3, rings=2, hint=(1, 0, 0)))


def build_a(k):
    """A 長耳: 細身の体。長い耳が立って、背の弧が高い。尻の腿は大きく、跳ねる脚が長い。"""
    F, eyes = [], []
    F += part("body", ellipsoid("body", "fur", (0, 6.5, 0), (4.2, 3.8, 6.5), segs=9, rings=4))
    F += part("head", ellipsoid("head", "fur", (0, 8.2, -6.6), (2.7, 2.6, 2.7), segs=8, rings=3))
    F += part("muzzle", ellipsoid("head", "flesh", (0, 7.2, -9.2), (1.4, 1.1, 1.2), segs=5, rings=1))
    for s in (-1, 1):
        # 葉の形の耳: 幅 (ra) は z 方向 (横から見える面)、厚み (rb) は x 方向。根元は細く、中ほどで広く、先は尖る。
        F += part("ear_l" if s < 0 else "ear_r", tube("ear_l" if s < 0 else "ear_r", "fur",
                  [(s * 1.0, 10.0, -6.6), (s * 1.3, 13.6, -6.4), (s * 1.7, 17.4, -5.9), (s * 2.0, 20.2, -5.2)],
                  ra=[0.7, 1.35, 1.0, 0.1], rb=[0.22, 0.34, 0.24, 0.04], sides=6, hint=(0, 0, 1)))
        F += part(f"thigh{s}", ellipsoid(f"hind_{'l' if s < 0 else 'r'}", "fur", (s * 3.0, 6.0, 3.6), (2.3, 3.0, 3.3), segs=6, rings=2))
        F += part(f"hindf{s}", tube(f"hind_{'l' if s < 0 else 'r'}", "flesh",
                  [(s * 2.6, 3.4, 2.5), (s * 2.7, 1.0, 5.0), (s * 2.8, 0.5, 8.6)], ra=[1.0, 0.8, 0.7], sides=4, hint=(1, 0, 0)))
        F += part(f"fore{s}", tube(f"fore_{'l' if s < 0 else 'r'}", "fur",
                  [(s * 2.2, 5.6, -4.0), (s * 2.4, 2.5, -4.3), (s * 2.5, 0.0, -4.6)], ra=[0.9, 0.7, 0.7], sides=4, hint=(1, 0, 0)))
    F += part("tail", ellipsoid("tail", "flesh", (0, 7.0, 6.6), (1.6, 1.6, 1.6), segs=7, rings=2))
    spines(F, [4.5, 5.5, 6.5, 5.5, 4.5], 0.8)
    eyes = [eye_at(-2.35, 8.5, -7.6, 0.7), eye_at(2.35, 8.5, -7.6, 0.7)]
    return F, eyes


def build_b(k):
    """B 毛玉: 丸い体。耳は短く寝る。扇形の棘の冠 (外の棘ほど横へ開く)。脚は短くずんぐり。"""
    F, eyes = [], []
    F += part("body", ellipsoid("body", "fur", (0, 6.8, 0), (5.0, 4.6, 5.0), segs=10, rings=4))
    F += part("head", ellipsoid("head", "fur", (0, 8.4, -5.4), (3.0, 2.8, 2.8), segs=8, rings=3))
    F += part("muzzle", ellipsoid("head", "flesh", (0, 7.6, -7.9), (1.1, 1.0, 1.0), segs=5, rings=1))
    for s in (-1, 1):
        F += part("ear_l" if s < 0 else "ear_r", tube("ear_l" if s < 0 else "ear_r", "fur",
                  [(s * 1.2, 10.4, -5.0), (s * 1.8, 11.0, -3.4), (s * 2.4, 10.0, -1.6)], ra=[0.9, 0.7, 0.2], sides=4, hint=(0, 1, 0)))
        F += part(f"thigh{s}", ellipsoid(f"hind_{'l' if s < 0 else 'r'}", "fur", (s * 3.6, 5.4, 2.6), (2.6, 2.6, 2.8), segs=6, rings=2))
        F += part(f"hindf{s}", tube(f"hind_{'l' if s < 0 else 'r'}", "flesh",
                  [(s * 3.0, 2.6, 2.6), (s * 3.0, 0.8, 4.0), (s * 3.0, 0.5, 5.6)], ra=[1.0, 0.8, 0.7], sides=4, hint=(1, 0, 0)))
        F += part(f"fore{s}", tube(f"fore_{'l' if s < 0 else 'r'}", "fur",
                  [(s * 2.6, 5.0, -3.2), (s * 2.8, 2.2, -3.6), (s * 2.9, 0.0, -3.8)], ra=[1.0, 0.8, 0.8], sides=4, hint=(1, 0, 0)))
    F += part("tail", ellipsoid("tail", "flesh", (0, 7.2, 6.4), (2.0, 2.0, 2.0), segs=7, rings=2))
    fan = [(-0.7, 1, 0), (-0.35, 1, 0), (0, 1, 0), (0.35, 1, 0), (0.7, 1, 0)]
    spines(F, [4.0, 5.5, 6.5, 5.5, 4.0], 0.9, dirs=fan)
    eyes = [eye_at(-2.6, 8.9, -6.2, 0.7), eye_at(2.6, 8.9, -6.2, 0.7)]
    return F, eyes


def build_c(k):
    """C 細長い: 低く長い胴。長い首の先に小さい頭。耳は首に寝る。後ろへ細い長い尾。脚は長く細い。"""
    F, eyes = [], []
    F += part("body", ellipsoid("body", "fur", (0, 6.4, 0), (3.3, 3.2, 8.0), segs=9, rings=4))
    F += part("neck", tube("head", "fur", [(0, 6.0, -5.5), (0, 7.0, -7.6), (0, 7.8, -8.6)], ra=[2.1, 1.9, 1.9], sides=6, hint=(1, 0, 0)))
    F += part("head", ellipsoid("head", "fur", (0, 8.6, -9.6), (2.3, 2.2, 2.6), segs=6, rings=2))
    F += part("muzzle", ellipsoid("head", "flesh", (0, 8.0, -12.0), (1.1, 0.9, 1.0), segs=7, rings=2))
    for s in (-1, 1):
        F += part("ear_l" if s < 0 else "ear_r", tube("ear_l" if s < 0 else "ear_r", "fur",
                  [(s * 1.0, 10.2, -8.0), (s * 1.4, 10.4, -6.4), (s * 1.6, 9.2, -4.6)], ra=[0.7, 0.5, 0.2], sides=6, hint=(1, 0, 0)))
        F += part(f"hind{s}", tube(f"hind_{'l' if s < 0 else 'r'}", "flesh",
                  [(s * 2.6, 5.6, 3.6), (s * 3.0, 1.8, 6.0), (s * 2.9, 0.0, 3.4)], ra=[1.0, 0.7, 0.6], sides=6, hint=(1, 0, 0)))
        F += part(f"fore{s}", tube(f"fore_{'l' if s < 0 else 'r'}", "fur",
                  [(s * 2.2, 5.6, -4.4), (s * 2.5, 2.2, -4.8), (s * 2.6, 0.0, -5.0)], ra=[0.7, 0.6, 0.6], sides=6, hint=(1, 0, 0)))
    F += part("tail", tube("tail", "fur", [(0, 6.8, 7.6), (0, 7.4, 11.0), (0, 8.0, 14.6)], ra=[1.2, 0.8, 0.2], sides=6, hint=(1, 0, 0)))
    spines(F, [3.5, 4.5, 5.0, 4.5, 3.5], 0.75)
    eyes = [eye_at(-2.0, 9.2, -10.0, 0.7), eye_at(2.0, 9.2, -10.0, 0.7)]
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


LAID = 60   # 寝た棘 (背の後ろへ倒れる角度)
SP = {"spine%d" % i: (LAID, 0, 0) for i in range(len(SPINE_Z))}
SP_UP = {"spine%d" % i: (0, 0, 0) for i in range(len(SPINE_Z))}

POSES = {
    "idle": dict(SP),
    # 予告: しゃがむ (胴の前を下げ、前脚を曲げる) と同時に棘が立つ
    # 体の前を深く下げ、尻を上げる。棘は体の傾きを打ち消して真上へ立てる (own +14 + 体 -14 = 0)。耳は後ろへ倒す。
    "telegraph": dict(SP_UP, body=(-14, 0, 0), head=(-22, 0, 0), fore_l=(-34, 0, 0), fore_r=(-34, 0, 0),
                      hind_l=(-30, 0, 0), hind_r=(-30, 0, 0), ear_l=(60, 0, 0), ear_r=(60, 0, 0),
                      **{"spine%d" % i: (14, 0, 0) for i in range(len(SPINE_Z))}),
    # 撃って跳ねる: 胴を前へ上げ、後ろ脚を蹴り出す。棘は寝ている
    "shoot": dict(SP, body=(12, 0, 0), head=(8, 0, 0), fore_l=(-25, 0, 0), fore_r=(-25, 0, 0),
                  hind_l=(-35, 0, 0), hind_r=(-35, 0, 0)),
}

SPEC = dict(
    id="spine_hare", short="棘うさぎ", kind="地上の敵 3案 (P7・アーチャー予習)",
    title="棘うさぎ (spine_hare) — 背の棘の予告・低ポリ 3 案",
    sub=["夜の草原の野うさぎ。背の骨の棘は寝ていて、予告 (0.5 秒) でしゃがんで立つ。立った棘が予告の絵そのもの。",
         "一本をまっすぐ前へ撃って、後ろ脚で跳ねて逃げる。HP 12。全身の幅 約 22 単位。左の黄の目盛りは人 (1.8 ブロック)。"],
    labels=["A 長耳", "B 毛玉", "C 細長い"],
    rows=[("前から (棘は寝ている)", "idle", 180, 8, 430, (0, 9, 0), 18.0),
          ("横 (寝た棘の列)", "idle", 270, 6, 430, (0, 9, 0), 18.0),
          ("前 3/4", "idle", 212, 14, 430, (0, 9, 0), 18.0),
          ("予告: しゃがんで棘を立てる (横)", "telegraph", 270, 6, 470, (0, 11, 0), 18.0),
          ("予告 (前 3/4)", "telegraph", 212, 14, 470, (0, 11, 0), 18.0),
          ("撃って跳ねる (横)", "shoot", 270, 6, 430, (0, 9, 0), 18.0)],
    options={
        "a": dict(name="A 長耳", desc="細身の体に長い耳が立つ、いちばん兎らしい形。背の弧は高く、寝た棘は背の線に沿う。予告では長い耳が後ろへ倒れ、しゃがんだ体から棘が立つ。跳ねる脚が長く、後ろ姿で一番細く見える。"),
        "b": dict(name="B 毛玉", desc="丸い体に短い耳が寝る、ずんぐりした形。背の棘は扇形の冠 (外ほど横へ開く)。予告では冠が一気に立ち、丸い体が縮むので「膨らんで棘を立てる」が読める。脚は短く、横から見ると背が一番高い。"),
        "c": dict(name="C 細長い", desc="低く長い胴と長い首、先に小さい頭。耳は首に寝て、尾は後ろへ細く長く伸びる。寝た棘は細い背の線になり、横のシルエットは横長。予告では首が下がり、背の棘だけが上へ立つ。")},
    extra="一章の言葉 (灰の毛・骨の棘・琥珀の目)。骨 16 本 (root > body > head / ear×2 / fore×2 / hind×2 / tail / spine0〜4)。棘は根元が骨の pivot なので、寝る・立つは同じ骨の回転だけ。撃つ棘そのものは投射物 (モデルに入れない)。",
    budget="地上の小物 300〜500", atlas_h=300)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
