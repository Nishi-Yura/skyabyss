# -*- coding: utf-8 -*-
"""巡礼者の羽 (pilgrim_plume) 低ポリ 一品一案。約束の島の主 (docs/design/50 の 4.2.4)。
雲上の巡礼者の片方の翼だけが、先触れとして降りてくる (巡礼者の全身は最後まで見えない)。翼を広げて高さ 6 ブロック。
付け根に金の輪と朝の光の珠 (先触れの印)。前縁は金の骨、白い大きな風切羽が扇に垂れ、二列の雨覆が重なる。羽の先に朝の光。
浮かんだまま、羽ばたき (前へ扇の風)・羽の雨・先触れの印 (一人へ落ちる)・審きの光を使う。
骨: root > body > arm > hand / halo。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mk import *  # noqa

# 翼の付け根 (body) は少し右下、前縁は左上へ弧を描いて上がる
J = (8.0, 30.0, 0.0)
W = (-14.0, 66.0, 1.0)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", J), "arm": ("body", J), "hand": ("arm", W), "halo": ("body", J)}
PAL = make_pal({
    "white": (CLOUDW, 0.62, dict(crack=0.0, noise=0.12)),
    "mist": (CLOUDW, 0.48, dict(crack=0.0, noise=0.2)),
    "pale": (CLOUDW, 0.38, dict(crack=0.0, noise=0.22)),
    "gold": (GOLDW, 0.7, dict(crack=0.02, noise=0.1)),
    "dawn": (DAWN, 0.85, dict(crack=0.0, noise=0.05, min=1)),
}, glow=["dawn"])


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def feather(F, name, bone, root, direction, ln, w, mat, tip=True, z=0.0):
    """平たい大きな羽 (翼の面 = xy)。root から direction へ ln、幅 w"""
    dx, dy = direction
    n = math.hypot(dx, dy)
    dx, dy = dx / n, dy / n
    pts = [(root[0], root[1], root[2] + z), (root[0] + dx * ln * 0.5, root[1] + dy * ln * 0.5, root[2] + z + 0.3),
           (root[0] + dx * ln, root[1] + dy * ln, root[2] + z + 0.6)]
    F += part(name, tube(bone, mat, pts, ra=0.4, rb=[w * 0.42, w * 0.55, w * 0.2], sides=4, hint=(0, 0, 1)))
    # 軸は根の近くだけ (淡い金、細く。先まで通すと櫛に見える)
    F += part(name + "s", tube(bone, "gold", [lerp(pts[0], pts[1], -0.05), lerp(pts[0], pts[1], 0.7)], ra=0.5, rb=0.28, sides=4, hint=(0, 0, 1)))
    if tip:
        F += part(name + "t", ellipsoid(bone, "dawn", lerp(pts[1], pts[2], 0.94), (0.8, 0.8, 0.5), segs=5, rings=2))


def build(k):
    F = []
    # 前縁の骨 (金。付け根 → 手首 → 翼の先)
    TIP = (-40.0, 82.0, 2.0)
    arm = [J, (2.0, 46.0, 0.4), (-6.0, 58.0, 0.8), W]
    F += part("arm", tube("arm", "gold", arm, ra=[2.6, 2.2, 1.9, 1.7], rb=[2.0, 1.8, 1.6, 1.4], sides=6, hint=(0, 0, 1)))
    hand = [W, (-24.0, 74.0, 1.4), (-33.0, 79.0, 1.8), TIP]
    F += part("hand", tube("hand", "gold", hand, ra=[1.7, 1.4, 1.0, 0.4], rb=[1.4, 1.1, 0.8, 0.3], sides=6, hint=(0, 0, 1)))
    # 初列風切 (手から下へ扇に垂れる、長い。広く重ねる。後ろの羽ほど奥)
    for i in range(9):
        t = i / 8
        root = lerp(W, TIP, 0.06 + 0.88 * t)
        ang = math.radians(-126 + 44 * t)
        ln = 36.0 - 9.0 * abs(t - 0.4) * 2
        feather(F, f"p{i}", "hand", root, (math.cos(ang), math.sin(ang)), ln, 10.5 - 2.5 * t, "white" if i % 2 == 0 else "mist", z=1.0 + i * 0.35)
    # 次列風切 (腕から下へ。短め)
    for i in range(7):
        t = i / 6
        root = lerp(J, W, 0.12 + 0.84 * t)
        ang = math.radians(-96 - 24 * t)
        ln = 22.0 + 7.0 * t
        feather(F, f"s{i}", "arm", root, (math.cos(ang), math.sin(ang)), ln, 9.5, "white" if i % 2 else "mist", tip=(i % 2 == 0), z=0.8 + i * 0.3)
    # 雨覆 (二列。前縁の下に重なる短い羽。風切羽より前)
    for row, (off, ln, mat, w) in enumerate(((2.0, 9.0, "pale", 7.0), (6.0, 12.0, "white", 8.0))):
        for i in range(9):
            t = i / 8
            base = lerp(J, W, t / 0.7) if t < 0.7 else lerp(W, TIP, (t - 0.7) / 0.3 * 0.7)
            ang = math.radians(-100 - 24 * t)
            root = (base[0] + math.cos(ang) * off * 0.5, base[1] + math.sin(ang) * off * 0.5, base[2])
            feather(F, f"c{row}{i}", "arm" if t < 0.7 else "hand", root, (math.cos(ang), math.sin(ang)), ln, w, mat, tip=False, z=-1.4 + row * 0.7)
    # 前縁を覆う小羽の列 (骨の前に、鱗のように重なる)
    for i in range(10):
        t = i / 9
        p = lerp(J, W, t / 0.6) if t < 0.6 else lerp(W, TIP, (t - 0.6) / 0.4 * 0.85)
        F += part(f"lead{i}", ellipsoid("arm" if t < 0.6 else "hand", "white", (p[0] - 0.4, p[1] - 1.4, p[2] - 2.0), (3.0 - 1.4 * t, 2.0, 0.8), segs=6, rings=2))
    # 付け根の金の輪と朝の光の珠 (先触れの印)
    R = 7.5
    ring_pts = [(J[0] + math.cos(a) * R, J[1] + math.sin(a) * R, J[2] - 1.0) for a in [math.radians(i * 15) for i in range(25)]]
    F += part("halo", tube("halo", "gold", ring_pts, ra=0.6, rb=0.9, sides=4, hint=(0, 0, 1)))
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        F += part(f"ray{i}", spike("halo", "gold", (J[0] + math.cos(a) * (R + 0.6), J[1] + math.sin(a) * (R + 0.6), J[2] - 1.0),
                                    (math.cos(a), math.sin(a), 0), 3.2 if i % 2 else 2.0, 0.5, sides=4, rings=2))
    F += part("orb", ellipsoid("body", "dawn", (J[0], J[1], J[2] - 1.2), (3.0, 3.0, 2.6), segs=10, rings=4))
    F += part("socket", ellipsoid("body", "gold", (J[0] + 0.6, J[1] - 0.4, J[2] + 1.0), (3.6, 3.6, 2.4), segs=8, rings=3))
    return F, []


POSES = {
    "idle": {},
    "beat": {"arm": (-50, 0, 0), "hand": (-30, 0, 0)},
    "judge": {"arm": (0, 0, -35), "hand": (0, 0, -25), "body": (-20, 0, 0)},
}

SPEC = dict(
    id="pilgrim_plume", short="巡礼者の羽", kind="約束の島の主 一品一案 (50 の 4.2.4)",
    title="巡礼者の羽 (pilgrim_plume) — 先触れに降りてくる片方の翼・低ポリ + ドット絵 一案",
    sub=["浮かんだまま、羽ばたき (前へ扇の風)・羽の雨・先触れの印・審きの光。巡礼者の全身は見えない。",
         "金の前縁の骨、白い風切羽の扇、二列の雨覆。付け根に金の輪と朝の光の珠。高さ 6 ブロック。"],
    labels=["巡礼者の羽"],
    rows=[("前", "idle", 180, 4, 520, (-12, 52, 0), 5.6),
          ("前 3/4", "idle", 212, 8, 520, (-12, 52, 0), 5.6),
          ("羽ばたき", "beat", 240, 6, 520, (-12, 52, 0), 5.6),
          ("後ろ 3/4", "idle", 32, 10, 520, (-12, 52, 0), 5.6)],
    options={"a": dict(name="片方の翼", desc="金の骨と白い風切羽の大きな片翼。付け根に金の輪と朝の光の珠。")},
    extra="雲海の言葉 (雲の白・淡い金・朝の光)。骨 5 本。光るのは珠と羽の先。",
    budget="主 1500〜3000", pw=440,
    later="二段目 = 羽の先の光が強まる。三段目 = 輪が二重に。",
)

if __name__ == "__main__":
    run_one(SPEC, build, PAL, BONES, POSES, hint=256)
