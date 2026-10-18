"""C 案: 鐘守の語り部 — 鐘のそばに立つ、神秘的で儀式の人。尖頭アーチの細長い頭巾 (縁に銀青の刺繍)、
長く細い白い眉と房、強めに淡く光る目。長衣は鐘の形に裾が広がり、波形の縁と鐘の刺繍の帯。
胸に真鍮の鐘の飾り、縄の帯の端にも鐘。袖も鐘形。やさしい口もと。"""
from __future__ import annotations

from char import *  # noqa

NAME = "C 鐘守の語り部"
HINT = 128

NAVY = [(8, 14, 44), (16, 26, 76), (26, 42, 108), (40, 62, 140), (58, 84, 168)]
NAVY2 = [(12, 20, 58), (22, 36, 92), (34, 54, 124), (50, 76, 156), (74, 104, 186)]
TRIM = [(46, 66, 112), (84, 112, 158), (132, 162, 204), (188, 212, 240), (232, 244, 255)]
ROBE = [(44, 48, 60), (72, 78, 94), (102, 110, 128), (134, 142, 160), (166, 174, 190)]
HEM = [(30, 34, 46), (50, 56, 72), (74, 82, 100), (100, 110, 130)]
BAND = [(18, 28, 64), (30, 46, 92), (46, 68, 124), (70, 98, 160)]
BELT = [(50, 34, 20), (92, 66, 38), (136, 100, 58), (180, 140, 86), (220, 184, 120)]
SK = [(110, 76, 80), (160, 116, 112), (204, 160, 146), (230, 190, 170), (246, 214, 194)]
THREAD = np.array([(150, 190, 232), (214, 236, 255)], float)

PAL = dict(
    mats={
        "robe": dict(ramp=ROBE, base=0.58, lit=0.30, noise=0.08, facevar=0.05, crack=0, min=0, streak=0.12, ao=0.10),
        "hem": dict(ramp=HEM, base=0.60, lit=0.30, noise=0.10, facevar=0.05, crack=0, min=0, streak=0.1),
        "band": dict(ramp=BAND, base=0.58, lit=0.30, noise=0.04, facevar=0.02, crack=0, min=0, streak=0.0),
        "belt": dict(ramp=BELT, base=0.55, lit=0.30, noise=0.08, crack=0, min=0, stripe=2, axis="u"),
        "cordm": dict(ramp=BELT, base=0.58, lit=0.30, noise=0.08, crack=0, min=0, facevar=0.05),
        "shawl": dict(ramp=NAVY2, base=0.55, lit=0.32, noise=0.08, facevar=0.05, crack=0, min=0, stripe=4, axis="v"),
        "hood": dict(ramp=NAVY, base=0.55, lit=0.34, noise=0.08, facevar=0.06, crack=0, min=0, streak=0.10),
        "rim": dict(ramp=TRIM, base=0.50, lit=0.34, noise=0.05, facevar=0.04, crack=0, min=0, stripe=3, axis="u"),
        "skin": dict(ramp=SK, base=0.88, lit=0.06, noise=0.0, facevar=0.0, crack=0, min=1, streak=0.0, ao=0.04),
        "white": dict(ramp=WHITE, base=0.66, lit=0.30, noise=0.04, facevar=0.03, crack=0, min=1, streak=0.0),
        "sleeve": dict(ramp=ROBE, base=0.58, lit=0.30, noise=0.08, facevar=0.05, crack=0, min=0, streak=0.10),
        "cuff": dict(ramp=BAND, base=0.58, lit=0.30, noise=0.04, crack=0, min=0, stripe=2, axis="v"),
        "boot": dict(ramp=BOOT, base=0.55, lit=0.30, noise=0.1, crack=0, min=0),
        "brass": dict(ramp=BRASS, base=0.60, lit=0.34, noise=0.04, facevar=0.02, crack=0, min=0),
    },
    glow_mats=[],
)

CFG = dict(
    robe=[(1.9, 7.2, -5.4, 5.2, 0), (4.0, 6.0, -4.8, 4.6, 0), (8.5, 4.5, -4.2, 3.9, 0), (11.8, 4.0, -3.9, 3.8, 0),
          (13.8, 4.5, -4.0, 4.5, 0)],
    robe_mats=["band", "robe", "robe", "robe"],
    shawl=[(12.6, 5.0, -4.4, 3.8, 0), (15.0, 5.5, -4.6, 4.2, 0), (17.0, 4.6, -3.9, 3.7, 0), (18.4, 2.9, -2.5, 2.7, -0.2)],
    shawl_mats=["shawl", "shawl", "shawl"],
    hood=[(17.0, 3.2, -3.8, 3.8, 1.2, 0.0), (18.6, 4.2, -4.3, 4.6, 1.5, 0), (21.0, 4.5, -4.3, 5.0, 1.6, 0),
          (23.8, 4.0, -4.1, 4.8, 1.5, 0.3), (26.2, 2.8, -3.3, 3.8, 1.2, 0.8), (28.4, 1.5, -2.0, 2.5, 0.7, 1.6),
          (31.8, 0.3, -0.4, 0.8, 0.2, 3.2)],
    rim=[(2.0, 17.3, -4.5, 0.9), (3.3, 18.9, -4.9, 1.0), (3.9, 21.0, -5.0, 1.05), (3.5, 23.6, -4.8, 1.0),
         (2.3, 25.6, -4.5, 0.95), (1.0, 26.9, -4.2, 0.9), (0.0, 27.5, -4.0, 0.9)],
    face=[(17.7, 1.3, -1.3, 1.0), (18.9, 2.3, -1.9, 1.4), (20.4, 2.9, -2.3, 1.6), (22.3, 3.0, -2.2, 1.6),
          (24.0, 2.5, -1.7, 1.4), (25.2, 1.6, -1.2, 1.0)],
    brow=(0.6, 3.4, 23.2, 22.5, -5.5), brow_mid=0.4, brow_r=0.45,
    tufts=[(21.0, 3.8, 0.5), (19.6, 4.4, 0.5), (18.4, 3.0, 0.45)], tuft_x=3.9,
    sleeve=[2.0, 2.4, 2.8], cuff=(2.9, 3.2),
)

FACE = dict(pats=[
    ([".DD.", "DHPD", "dPPd"], 2, 43),
    (["..m", "MM."], 0, 38),
], blush=None)


def build():
    F = base_body(CFG)
    F += scallop_hem(CFG, "body", "band", depth=1.4, out=0.4)
    # 胸の鐘 (肩掛けの前に吊るす。細い鎖)
    zc = front_z(CFG["shawl"], 15.0, 0.0, 0.5)
    F += tube("shawl", "brass", [np.array([-2.0, 17.6, zc + 0.6]), np.array([0, 16.2, zc - 0.1]),
                                  np.array([2.0, 17.6, zc + 0.6])], [0.3, 0.3, 0.3], sides=3, hint=(0, 0, 1))
    F += bell("shawl", "brass", (0.0, 16.2, zc - 0.2), 1.25, clapper="brass")
    # 縄の端の鐘
    s = side_of("cord")
    F += bell("cord", "brass", (s * 2.4, 4.5, front_z(CFG["robe"], 4.5, s * 2.4, 0.5)), 0.8, clapper="brass", n=5)
    return dict(body=F, eyes=[], extra=extra)


def extra(c, col, glow):
    col, glow = face_paint(c, col, glow, FACE)
    P, N, bay, owned = c["P"], c["N"], c["bay"], c["owned"]
    mats = c["mats"]
    # 長衣の裾の帯: 鐘形の刺繍 (逆 V の糸) を一周
    ang = np.arctan2(P[..., 0], -(P[..., 2]))
    ph = np.floor(ang / (2 * np.pi) * 80.0).astype(int) % 8
    row = np.floor((P[..., 1] - 1.9) * 2.0).astype(int)
    d = np.abs(ph - 3.5)
    on = owned & (mats == "band") & (N[..., 1] > -0.5) & (np.abs(np.round(d) - (3 - np.clip(row, 0, 3)) * 1.0) < 0.5) & \
        (row >= 0) & (row <= 3) & (d < 4)
    col = np.where(on[..., None], THREAD[0], col)
    lip = owned & (mats == "band") & (row == 3) & (d >= 3)
    col = np.where(lip[..., None], THREAD[1], col)
    # 頭巾の縁の刺繍: 細い糸の点線
    rim = owned & (mats == "rim") & (np.floor(P[..., 1] * 2) % 4 == 0) & (np.floor(P[..., 0] * 2 + P[..., 2]) % 2 == 0)
    col = np.where(rim[..., None], THREAD[0], col)
    # 肩掛けの縁取り
    sh = owned & (mats == "shawl") & (np.floor(P[..., 1] * 2) % 5 == 0)
    col = np.where(sh[..., None], np.array(BAND[3], float), col)
    return col, glow
