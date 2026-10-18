"""骨鳥 (bone_gull) 低ポリ ラフ 3 案。弱い飛ぶ敵 (HP 8)。1 体ずつ急降下する。
急降下の前に 0.5 秒の鳴き声の予告 (頭を上げ、くちばしを開き、翼を上げる)。壁をすり抜けない。
予告の姿 (telegraph): 頭を上げ、下あごを開いて鳴く、翼は上へ。急降下 (dive): 体を前へ倒し、翼を背へたたむ (翼は胴の後ろへ回る)。
三角形の目安: 300〜450。
A 骨の翼 (厚みのある翼の面 + 三本の風切りの骨 + 翼の中の骨の筋) / B 肋の翼 (翼は肋の弧だけ、向こうが見える) / C 鎌の翼 (後ろへ流れる細い翼と二股の尾、頭に骨の冠)
"""
import math
from ck import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 2.2, 0)),
         "head": ("body", (0, 3.4, -3.2)), "jaw": ("head", (0, 3.0, -5.0)),
         "wing_l": ("body", (-1.6, 3.0, -0.5)), "wing_r": ("body", (1.6, 3.0, -0.5)),
         "tail": ("body", (0, 2.2, 3.2))}
PAL = {"mats": {
    "stone": mat(STONE, 0.66, crack=0.22, stain=dict(ramp=MOSS, thr=0.93, up=0.3, conc=0.2)),
    "rim": mat(BROWN, 0.6, crack=0.1, noise=0.15),
    "bone": mat(BONE, 0.7, crack=0.05),
    "flesh": mat([(40, 34, 36), (80, 66, 60), (118, 98, 84), (152, 130, 108)], 0.62, crack=0.0, noise=0.2),
    "dark": mat(DARK, 0.5, crack=0.0, min=0),
    "moss": mat(MOSS, 0.6, crack=0.0, noise=0.25),
}, "skull_bone": "head", "face_mat": "rim"}


def eye_at(x, y, z, r=0.6):
    return dict(c=V(x, y, z), rx=r, ry=r * 0.7, pr=(0.55, 0.38), tilt=0.15, zr=2.2)


def airfoil(bone, m, side, root, length, sections, sweep=0.0):
    """翼の骨の方向 (x) に沿った厚みのある翼 (loft)。sections = [(s, 前の弦, 後の弦, 厚み)]、s は 0〜1。
    断面は (y, z) の楕円。sweep は先端を後ろ (+z) へ流す量。"""
    rings = []
    for s, cf, cb, th in sections:
        x = root[0] + side * length * s
        z0 = root[2] + sweep * s * s
        y0 = root[1] - 0.10 * length * s * s
        ring_ = []
        for a in np.linspace(0, 360, 7)[:-1]:
            r_ = math.radians(a)
            yy = y0 + th * math.cos(r_)
            zz = z0 + (cf * math.sin(r_) if math.sin(r_) < 0 else cb * math.sin(r_))
            ring_.append(V(x, yy, zz))
        rings.append(ring_)
    return loft(bone, m, rings, caps=(False, m))


def body_part(F, kind):
    if kind == "round":
        F += part("body", ellipsoid("body", "stone", (0, 2.4, 0.0), (2.9, 2.6, 4.2), segs=8, rings=3))
    else:
        F += part("body", ellipsoid("body", "stone", (0, 2.2, 0.0), (2.2, 2.0, 4.6), segs=8, rings=3))
    # 胸の骨の板と、胴の背の骨の肋 (二本)、腹の竜骨
    F += part("breast", ellipsoid("body", "bone", (0, 1.4, -2.2), (1.6, 1.0, 2.0), segs=5, rings=1))
    for x in (-0.9, 0.9):
        F += part("back_rib", tube("body", "bone", [(x, 4.3, -1.6), (x * 1.2, 4.5, 0.8), (x * 0.8, 3.9, 3.0)],
                                   ra=0.3, sides=4, hint=(0, 0, 1)))
    F += part("keel", spike("body", "rim", (0, 0.2, -1.0), (0, -1, -0.2), 1.6, 0.5, sides=3, rings=1))
    F += part("head", ellipsoid("head", "stone", (0, 3.6, -4.4), (1.9, 1.8, 2.0), segs=8, rings=3))
    # 頭の後ろの骨の冠 (二本の小さな棘)
    for x in (-0.9, 0.9):
        F += part("nape", spike("head", "bone", (x, 5.0, -3.4), (x * 0.3, 0.8, 0.5), 1.2, 0.3, sides=3, rings=1))
    F += part("brow", spike("head", "bone", (0, 5.1, -4.0), (0, 0.4, -1), 1.4, 0.45, sides=3, rings=1))
    F += part("beak", spike("head", "bone", (0, 3.4, -5.8), (0, -0.12, -1), 3.4, 0.9, sides=4, rings=2))
    F += part("jaw", spike("jaw", "bone", (0, 3.0, -5.0), (0, -0.3, -1), 2.6, 0.5, sides=3, rings=1))
    F += part("jaw_tooth", spike("jaw", "bone", (0, 3.0, -6.6), (0, -0.6, -0.2), 0.9, 0.25, sides=3, rings=1))
    F += part("tail_fan", tube("tail", "rim", [(-0.8, 2.2, 3.0), (0.0, 2.0, 5.8), (0.8, 2.2, 3.0)], ra=[0.9, 1.4, 0.9], rb=[0.4, 0.4, 0.4], sides=4, hint=(0, 1, 0)))
    eyes = [eye_at(-1.3, 4.1, -5.6), eye_at(1.3, 4.1, -5.6)]
    return eyes


def build_a(k):
    """A 骨の翼: 厚みのある翼の面。骨の腕と、先の三本の風切りの骨と、翼の中の骨の筋。"""
    F = []
    eyes = body_part(F, "round")
    for s in (-1, 1):
        nm = "wing_l" if s < 0 else "wing_r"
        F += part(nm, airfoil(nm, "bone", s, (1.6 * s, 3.0, -0.5), 9.0,
                              [(0.0, 2.6, 2.2, 0.7), (0.35, 2.4, 2.2, 0.6), (0.7, 1.6, 1.6, 0.5), (1.0, 0.5, 0.8, 0.3)]))
        # 翼の中の骨の筋 (前縁に沿う)
        F += part(nm + "_strut", tube(nm, "rim", [(1.6 * s + 1.0 * s, 3.4, -0.2), (1.6 * s + 5.0 * s, 3.2, -0.4),
                                                  (1.6 * s + 8.4 * s, 2.6, -0.6)], ra=[0.35, 0.3, 0.2], sides=4, hint=(0, 0, 1)))
        for j, (dz, ln) in enumerate(((-1.8, 2.4), (0.0, 2.9), (1.6, 2.0))):
            tip = np.array([1.6 * s + 9.0 * s, 3.0 - 0.9, -0.5 + dz * 0.6])
            base_ = np.array([1.6 * s + 6.6 * s, 3.0 - 0.6, -0.5 + dz * 0.2])
            F += part(nm + "_f", spike(nm, "rim", base_, tip - base_, ln, 0.45, sides=3, rings=1))
    return F, eyes


def build_b(k):
    """B 肋の翼: 翼の面はなく、四本の肋の弧。向こうが見え、鳥の体が小さく見える。"""
    F = []
    eyes = body_part(F, "slim")
    for s in (-1, 1):
        nm = "wing_l" if s < 0 else "wing_r"
        for j, (z, ln, rise) in enumerate(((-2.6, 8.0, 2.8), (-1.0, 9.0, 3.2), (0.6, 7.6, 2.6), (2.0, 5.6, 1.8))):
            path = [(1.2 * s, 3.2, z), (s * (1.6 + ln * 0.5), 3.2 + rise, z - 0.4),
                    (s * (1.6 + ln), 3.0 + rise * 0.1, z - 0.8)]
            F += part(nm, tube(nm, "bone" if j < 2 else "rim", path, ra=[0.65, 0.5, 0.2], sides=4, hint=(0, 0, 1)))
        # 肋の間をつなぐ細い横木 (中ほど)
        F += part(nm + "_cross", tube(nm, "rim", [(s * 4.0, 5.2, -2.6), (s * 4.5, 5.6, 2.0)], ra=0.3, sides=4, hint=(0, 1, 0)))
    # 翼の付け根の骨の板 (肩)
    for s in (-1, 1):
        F += part("shoulder", ellipsoid("body", "rim", (s * 1.8, 3.2, -0.9), (1.0, 0.9, 1.4), segs=4, rings=1))
    return F, eyes


def build_c(k):
    """C 鎌の翼: 後ろへ流れる細い翼、先は鎌のように反る。二股の尾。頭に骨の冠。"""
    F = []
    eyes = body_part(F, "slim")
    for s in (-1, 1):
        nm = "wing_l" if s < 0 else "wing_r"
        F += part(nm, airfoil(nm, "bone", s, (1.6 * s, 3.0, -0.5), 10.5,
                              [(0.0, 2.2, 2.4, 0.5), (0.4, 1.8, 2.4, 0.5), (0.75, 1.1, 2.0, 0.35), (1.0, 0.3, 1.2, 0.2)],
                              sweep=3.4))
        F += part(nm + "_hook", spike(nm, "rim", (s * 9.4, 2.5, 2.4), (s * 0.8, -0.2, 0.9), 2.2, 0.5, sides=3, rings=1))
        # 翼の後ろ縁の小さな骨の爪 (三本。根元は翼の面の上)
        for t in (0.25, 0.5, 0.75):
            base_ = (s * (1.6 + 10.5 * t), 3.0 - 0.1, -0.5 + 3.4 * t * t)
            F += part(nm + "_claw", spike(nm, "bone", base_, (0, 0.6, 1.0), 1.6, 0.35, sides=3, rings=1))
    F += part("crest", spike("head", "bone", (0, 5.1, -3.6), (0, 0.9, 0.4), 2.6, 0.7, sides=3, rings=1))
    F += part("crest2", spike("head", "bone", (0, 4.8, -2.4), (0, 0.6, 0.6), 1.8, 0.5, sides=3, rings=1))
    F += part("tail_fork", spike("tail", "rim", (-0.6, 2.0, 2.8), (-0.4, -0.1, 1), 4.2, 0.7, sides=3, rings=1))
    F += part("tail_fork2", spike("tail", "rim", (0.6, 2.0, 2.8), (0.4, -0.1, 1), 4.2, 0.7, sides=3, rings=1))
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


# 予告 (telegraph): 頭を上げ、下あごを開いて鳴く、翼は上へ。急降下 (dive): 体を前へ倒し、翼を背へたたむ
POSES = {
    "idle": {"wing_l": (0, 0, -8), "wing_r": (0, 0, 8)},
    "telegraph": {"head": (18, 0, 0), "jaw": (-32, 0, 0), "wing_l": (0, 0, -55), "wing_r": (0, 0, 55),
                  "body": (8, 0, 0)},
    "dive": {"body": (-55, 0, 0), "head": (10, 0, 0), "wing_l": (0, 70, 0), "wing_r": (0, -70, 0)},
}

SPEC = dict(
    id="bone_gull", short="骨鳥", kind="飛ぶ敵 3案 (P7)",
    title="骨鳥 (bone_gull) — 弱い飛ぶ敵・低ポリ + ドット絵 3 案",
    sub=["弱い飛ぶ敵。HP 8。1 体ずつ急降下する。急降下の前に 0.5 秒の鳴き声の予告 (頭を上げ、くちばしを開く)。",
         "左の黄の目盛りは人 (1.8 ブロック) の高さ。予告の姿と急降下の姿を、翼の開きに合わせて中央に置いた。"],
    labels=["A 骨の翼", "B 肋の翼", "C 鎌の翼"],
    rows=[("横 (翼を開いた滑空)", "idle", 270, 4, 320, (0, 4, 0), 24.0),
          ("前 3/4 (滑空)", "idle", 212, 14, 320, (0, 4, 0), 24.0),
          ("予告: 鳴く (横から)", "telegraph", 270, 4, 320, (0, 4, 0), 24.0),
          ("予告: 鳴く (前 3/4)", "telegraph", 212, 14, 320, (0, 4, 0), 24.0),
          ("急降下 (横から)", "dive", 270, 4, 320, (0, 4, 0), 22.0),
          ("急降下 (上から 3/4)", "dive", 212, 40, 320, (0, 4, 0), 22.0)],
    options={
        "a": dict(name="A 骨の翼", desc="短い胴に、厚みのある骨の翼。翼の中に骨の筋が通り、先に三本の風切りの骨が放射状に出る。胸の骨の板と背の肋、腹の竜骨で、骨の鳥らしい造り。鳴くと翼が上がり、急降下では翼が背へたたまれて、一本の矢の形になる。"),
        "b": dict(name="B 肋の翼", desc="翼の面はなく、肋の弧が四本並ぶ。向こうが見える骨の翼で、骨の鳥らしさが一番強い。横木で肋をつなぎ、くちばしが目立つ。翼が上がると弧が天を向いて、予告の形がはっきりする。"),
        "c": dict(name="C 鎌の翼", desc="細く長い翼が後ろへ流れ、先が鎌のように反る。尾は二股の骨。頭に二本の骨の冠が立つ。ほかの二案より横に細い輪郭で、急降下は翼が後ろへ引かれて細い矢に見える。"),
    },
    extra="一章の言葉 (灰青・骨・琥珀の目・茶の割れ目)。骨 7 本 (body / head / jaw / wing_l / wing_r / tail)。予告は頭を上げ、顎を開き、翼を上げる。急降下は体を前へ倒し、翼を背へ折りたたむ（翼の根の枢軸で後ろへ回す）。目安は 300〜450 三角形。",
    budget="飛ぶ敵 300〜450", atlas_h=260)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
