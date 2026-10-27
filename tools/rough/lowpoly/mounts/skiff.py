"""小舟 (skyabyss:skiff。市の道具屋で売る乗れる舟) ラフ 3 案。いまはバニラの舟の形に mod の絵。
A 平底の渡し舟 (板張り、四角い艫、二枚の腰板、真鍮の櫂受け、青い帯) / B 鯨骨と皮の舟 (骨の肋に皮を張り、縄で綴じる)
C 帆柱の小舟 (重ね張りの板、短い帆柱と畳んだ帆、舳先に真鍮の鳥)
大きさはバニラの舟と同じ (長さ 28・幅 14・座る高さ 6、1 単位 = 1/16 ブロック)。前 = -Z。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mt_common import *  # noqa

WOOD = [(70, 46, 28), (108, 74, 44), (146, 104, 64), (184, 140, 92)]
WOODD = [(40, 26, 18), (66, 44, 28), (94, 64, 40), (124, 88, 56)]
WOODL = [(120, 96, 64), (162, 132, 90), (196, 168, 120), (224, 200, 152)]
ROPE = [(96, 80, 52), (138, 118, 80), (176, 156, 108), (206, 190, 140)]
BONEC = [(150, 140, 118), (192, 182, 160), (222, 214, 194), (240, 236, 222)]
BLUE = [(30, 52, 86), (46, 78, 122), (70, 108, 156), (110, 146, 190)]
SAIL = [(150, 140, 116), (190, 180, 154), (220, 212, 188), (238, 232, 212)]

L = 14.0  # 半分の長さ


def hull(m, half_w, depth, sheer=2.2, flat=0.0, transom=0.0, top=6.0, n=13):
    """船体: 輪切り (U 字を上で閉じた 7 角形) を前から後ろへつなぐ。flat = 平底の幅の割合、transom = 艫の幅 (0 なら尖る)"""
    rings = []
    for i in range(n):
        z = -L + 2 * L * i / (n - 1)
        u = z / L
        bow = max(0.0, -u)
        w = half_w * (1 - bow ** 2.2) if u < 0 else half_w * (1 - (1 - transom / half_w) * u ** 2.6) if transom else half_w * (1 - u ** 2.2)
        w = max(w, 0.35)
        d = depth * (1 - 0.45 * abs(u) ** 2)
        t = top + sheer * abs(u) ** 2
        f = flat
        pts = [(-w, t, z), (-w * 0.96, t - d * 0.45, z), (-w * (0.55 + 0.4 * f), t - d * 0.88, z), (0.0, t - d, z),
               (w * (0.55 + 0.4 * f), t - d * 0.88, z), (w * 0.96, t - d * 0.45, z), (w, t, z)]
        rings.append(pts)
    return loft("body", m, rings, caps=(True, True)), rings


def rim(rings, m, r=0.55, lift=0.3):
    """舷縁 (両側の縁を通る管)"""
    F = []
    for side in (0, 6):
        pts = [(p[side][0], p[side][1] + lift, p[side][2]) for p in rings]
        F += T(m, pts, [r] * len(pts), sides=4)
    return F


def thwart(rings, z, m, h=0.0):
    """腰板 (横に渡す板)"""
    i = min(range(len(rings)), key=lambda k: abs(rings[k][0][2] - z))
    w = abs(rings[i][0][0]) * 0.92
    y = rings[i][0][1] - 1.0 + h
    return T(m, [(-w, y, z), (w, y, z)], [0.9, 0.9], sides=4)


def deck(rings, m, inset=0.6, drop=0.45):
    """床の板: 船体の上の面 (閉じた所) に、少し沈めた板を前後に 3 枚"""
    F = []
    for k in (-1, 0, 1):
        pts = []
        for p in rings[1:-1]:
            w = abs(p[0][0]) - inset
            if w < 1.0:
                continue
            pts.append((k * w * 0.6, p[0][1] - drop, p[0][2]))
        if len(pts) > 2:
            F += T(m, pts, [0.95] * len(pts), sides=4)
    return F


def a(k):
    F = []
    hf, rings = hull("wood", 7.0, 5.2, sheer=2.0, flat=0.7, transom=4.6)
    F += part("hull", hf)
    F += part("rim", rim(rings, "woodd"))
    # 青い帯 (舷縁の下)
    for side in (0, 6):
        pts = [(p[side][0] * 0.99, p[side][1] - 1.2, p[side][2]) for p in rings[1:-1]]
        F += part(f"stripe{side}", T("blue", pts, [0.42] * len(pts), sides=4))
    F += part("deck", deck(rings, "woodl"))
    for z in (-4.0, 5.0):
        F += part(f"thwart{z}", thwart(rings, z, "woodd", 0.9))
    # 艫の板と櫂受け
    F += part("transom", Bl("woodd", (0, rings[-1][0][1] - 1.6, L + 0.2), 2.0, segs=6, rings=2))
    for s in (-1, 1):
        F += part(f"lock{s}", C("brass", (s * 6.7, rings[6][0][1] + 0.4, 0.5), (0, 1, 0), 1.6, 0.45, sides=4, rings=2))
        F += part(f"oar{s}", T("woodl", [(s * 6.9, rings[6][0][1] + 1.2, -2), (s * 8.4, rings[6][0][1] + 0.6, 6), (s * 9.6, rings[6][0][1] - 0.6, 12)], [0.45, 0.45, 0.45], sides=4))
        F += part(f"blade{s}", ER("woodl", (s * 9.8, rings[6][0][1] - 0.8, 13.4), (0.4, 0.7, 2.2), segs=6, rings=2))
    F += part("prow", C("woodd", (0, rings[0][0][1] - 0.6, -L), (0, 0.5, -1), 2.4, 0.7, sides=4, rings=2))
    F += rider((0, 7.4, 3.2), 0.0)
    return F, []


def b(k):
    F = []
    hf, rings = hull("hided", 6.6, 5.6, sheer=2.8, flat=0.25)
    F += part("hull", hf)
    F += part("rim", rim(rings, "bonec", r=0.6, lift=0.35))
    # 骨の肋 (外へ少し出る輪) と縄の綴じ
    for i in range(2, len(rings) - 2, 2):
        p = rings[i]
        arc = [(q[0] * 1.03, q[1], q[2]) for q in p]
        F += part(f"rib{i}", T("bonec", arc, [0.42] * len(arc), sides=4))
        F += part(f"lash{i}", Bl("rope", (p[0][0] * 1.02, p[0][1] + 0.1, p[0][2]), 0.7, segs=5, rings=2))
        F += part(f"lashr{i}", Bl("rope", (p[6][0] * 1.02, p[6][1] + 0.1, p[6][2]), 0.7, segs=5, rings=2))
    F += part("deck", deck(rings, "hide", inset=0.9))
    for z in (-3.0, 5.5):
        F += part(f"thwart{z}", thwart(rings, z, "bonec", 0.9))
    # 舳先と艫の高く反った骨
    F += part("stem", T("bonec", [(0, rings[0][0][1] - 1.5, -L + 0.2), (0, rings[0][0][1] + 1.8, -L - 1.2), (0, rings[0][0][1] + 3.2, -L - 0.4)], [0.8, 0.6, 0.45], sides=4))
    F += part("sternp", T("bonec", [(0, rings[-1][0][1] - 1.5, L - 0.2), (0, rings[-1][0][1] + 1.6, L + 1.0)], [0.8, 0.5], sides=4))
    F += part("coil", E("rope", (2.8, rings[8][0][1] - 0.2, 8.4), (1.6, 0.6, 1.6), segs=8, rings=2))
    F += rider((0, 7.4, 3.2), 0.0)
    return F, []


def c(k):
    F = []
    hf, rings = hull("wood", 7.0, 5.6, sheer=2.6, flat=0.4)
    F += part("hull", hf)
    F += part("rim", rim(rings, "woodd"))
    # 重ね張りの板の段 (横の筋が三本)
    for j, (frac, dy) in enumerate(((0.97, -1.6), (0.9, -3.0), (0.74, -4.2))):
        for side, sgn in ((0, -1), (6, 1)):
            pts = [(p[side][0] * frac + sgn * 0.15, p[side][1] + dy * (1 - 0.3 * abs(p[0][2] / L)), p[side][2]) for p in rings[1:-1]]
            F += part(f"strake{j}{side}", T("woodd", pts, [0.3] * len(pts), sides=4))
    # 赤い帯
    for side in (0, 6):
        pts = [(p[side][0] * 1.0, p[side][1] - 0.7, p[side][2]) for p in rings[1:-1]]
        F += part(f"stripe{side}", T("clothr", pts, [0.35] * len(pts), sides=4))
    F += part("deck", deck(rings, "woodl"))
    F += part("thwart", thwart(rings, -3.5, "woodd", 0.9))
    # 帆柱・畳んだ帆と帆桁
    y0 = rings[5][0][1] - 1.5
    F += part("mast", T("woodd", [(0, y0, -3.5), (0, y0 + 16, -3.5)], [0.6, 0.45], sides=5))
    F += part("boom", T("woodd", [(0, y0 + 4, -3.5), (0, y0 + 4.4, 7.5)], [0.4, 0.4], sides=4))
    F += part("sail", T("sail", [(0, y0 + 4.9, -2.8), (0, y0 + 5.1, 7.0)], [0.95, 0.8], sides=6))
    F += part("ties", T("rope", [(0, y0 + 4.9, 1.0), (0, y0 + 4.9, 1.6)], [1.05, 1.05], sides=6))
    F += part("flag", ER("clothr", (0, y0 + 15.4, -2.6), (0.15, 0.9, 1.2), segs=4, rings=2))
    # 舳先の真鍮の鳥
    F += part("prow", T("woodd", [(0, rings[0][0][1] - 1.4, -L + 0.3), (0, rings[0][0][1] + 1.2, -L - 1.4)], [0.8, 0.6], sides=4))
    F += part("bird", ER("brass", (0, rings[0][0][1] + 1.8, -L - 1.8), (0.6, 0.9, 1.4), segs=6, rings=2))
    F += part("beak", C("brass", (0, rings[0][0][1] + 2.0, -L - 3.0), (0, 0, -1), 1.0, 0.3, sides=4, rings=2))
    F += rider((0, 7.4, 3.2), 0.0)
    return F, []


def build(k):
    return {"a": a, "b": b, "c": c}[k](k)


SPEC = dict(
    id="skiff", short="小舟", kind="乗り物 3案 (市の道具屋)",
    title="小舟 — 市の道具屋の乗れる舟 (いまはバニラの舟の形)",
    sub=["大きさはバニラの舟と同じ (長さ 1.75 ブロック)。左の黄色い目盛りが人 (1.8 ブロック)。", "一番下の行は、人が座った姿。"],
    options={
        "a": dict(name="A 平底の渡し舟", desc="板張りの平底、四角い艫。二枚の腰板と真鍮の櫂受け、青い帯。町の渡しの舟。"),
        "b": dict(name="B 鯨骨と皮の舟", desc="骨の肋に皮を張って縄で綴じた舟。舳先と艫の骨が高く反る。鯨追いの町の舟。"),
        "c": dict(name="C 帆柱の小舟", desc="重ね張りの板に赤い帯。短い帆柱と畳んだ帆、舳先に真鍮の鳥。"),
    },
    pw=380, rows=rows_for((0, 6, 0), 6.0),
)

PAL = PALM({"wood": mat(WOOD, 0.62, crack=0.0, noise=0.2), "woodd": mat(WOODD, 0.6, crack=0.0, noise=0.2), "woodl": mat(WOODL, 0.62, crack=0.0, noise=0.18),
            "rope": mat(ROPE, 0.62, crack=0.0, noise=0.2), "bonec": mat(BONEC, 0.64, crack=0.08, noise=0.18), "blue": mat(BLUE, 0.62, crack=0.0, noise=0.12),
            "sail": mat(SAIL, 0.64, crack=0.0, noise=0.12)})

if __name__ == "__main__":
    run(SPEC, build, PAL, hint=128, pupil=None)
