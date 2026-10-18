"""語り部 (storyteller) の共通の形づくり。作図空間は前 = -Z、x は geo と反転 (書き出しで flipxz)。
骨 11 本 (root > body > head > hair / hair2、body > shawl / armL / armR / legL / legR / cord) は今の geo と同じ。
  head  = 顔 (肌・鼻)、hair = 白い眉・房、hair2 = 頭巾 (外殻・縁・先)、shawl = 肩掛け、body = 長衣・帯
cfg の辞書で 3 案の違いを決める。"""
from __future__ import annotations

from kit2 import *  # noqa

SKIN = [(120, 70, 60), (170, 108, 86), (212, 148, 112), (236, 176, 138), (250, 202, 164)]
WHITE = [(120, 126, 150), (172, 178, 200), (210, 216, 232), (238, 242, 250), (255, 255, 255)]
BOOT = [(22, 16, 14), (44, 32, 26), (72, 54, 40), (104, 80, 58)]
BRASS = [(66, 40, 12), (116, 76, 22), (172, 122, 40), (222, 176, 78), (252, 228, 142)]


def ring8(y, hw, zf, zb, ch, zc=0.0, cx=0.0):
    """面取りした箱の断面 (前の面が平ら)。ch = 角の面取り。"""
    pts = [(-(hw - ch), zf), (hw - ch, zf), (hw, zf + ch), (hw, zb - ch), (hw - ch, zb), (-(hw - ch), zb), (-hw, zb - ch),
           (-hw, zf + ch)]
    return [np.array([cx + x, y, zc + z]) for x, z in pts]


def interp_rings(spec, y):
    """spec = [(y, rx, zf, zb, zc)] の y での補間。"""
    st = sorted(spec)
    y = min(max(y, st[0][0]), st[-1][0])
    for a, b in zip(st[:-1], st[1:]):
        if a[0] <= y <= b[0]:
            t = (y - a[0]) / max(b[0] - a[0], 1e-9)
            return [a[i] * (1 - t) + b[i] * t for i in range(1, 5)]
    return list(st[-1][1:])


def side_of(bone):
    return 1.0 if pv(bone)[0] >= 0 else -1.0


def robe(cfg):
    F = []
    spec = cfg["robe"]
    rings = [ringp(y, rx, zf, zb, zc) for (y, rx, zf, zb, zc) in spec]
    F += rloft("body", "robe", rings, caps=(False, "robe"), mats=cfg.get("robe_mats"))
    # 帯
    by = cfg.get("belt_y", 9.6)
    r0 = interp_rings(spec, by)
    r1 = interp_rings(spec, by + 1.3)
    d = cfg.get("belt_d", 0.4)
    F += rloft("body", "belt", [ringp(by, r0[0] + d, r0[1] - d, r0[2] + d, r0[3]),
                                ringp(by + 1.3, r1[0] + d, r1[1] - d, r1[2] + d, r1[3])], caps=(False, False))
    return F


def shawl(cfg):
    F = []
    rings = [ringp(y, rx, zf, zb, zc) for (y, rx, zf, zb, zc) in cfg["shawl"]]
    F += rloft("shawl", "shawl", rings, caps=(False, "shawl"), mats=cfg.get("shawl_mats"))
    return F


def hood(cfg):
    """頭巾の外殻 (hair2) と、顔の開口の縁 (太い筒)。"""
    F = []
    rings = [ring8(y, hw, zf, zb, ch, zc) for (y, hw, zf, zb, ch, zc) in cfg["hood"]]
    F += rloft("hair2", "hood", rings, caps=(False, "hood"))
    half = cfg["rim"]  # [(x, y, z, r)] 左下から上へ。x >= 0 の側 (右半分は鏡)
    pts = [np.array([x, y, z]) for x, y, z, r in half]
    rr = [r for x, y, z, r in half]
    pts_l = [np.array([-p[0], p[1], p[2]]) for p in pts]
    path = pts + pts_l[::-1][1:]
    rad = rr + rr[::-1][1:]
    F += tube("hair2", "rim", path, rad, sides=cfg.get("rim_sides", 5), hint=(0, 0, 1), phase=0.0, caps=(False, False))
    return F


def face(cfg):
    F = []
    rings = [ringp(y, rx, zf, zb, -3.0, n=12, phase=15.0) for (y, rx, zf, zb) in cfg["face"]]
    F += rloft("head", "skin", rings, caps=(False, False))
    # 鼻
    nz = cfg.get("nose", (0.0, 20.5, -5.3, 1.0, 0.65))
    F += spike("head", "skin", (nz[0], nz[1], nz[2] + 0.5), (0, -0.4, -1), nz[3], nz[4], sides=4, rings=1,
               hint=(0, 1, 0), phase=45.0)
    return F


def brows_tufts(cfg):
    F = []
    for s in (1, -1):
        # 眉 (白い細い楔。内側が高く外側が下がる = やさしい)
        bx0, bx1, by0, by1, bz = cfg.get("brow", (0.6, 3.3, 23.0, 22.65, -5.5))
        bm = cfg.get("brow_mid", 0.35)
        path = [np.array([s * bx0, by0, bz]), np.array([s * (bx0 + bx1) * 0.5, (by0 + by1) / 2 + bm, bz + 0.1]),
                np.array([s * bx1, by1, bz + 0.5])]
        F += tube("hair", "white", path, [0.30, cfg.get("brow_r", 0.5), 0.0], sides=4, hint=(0, 0, 1), phase=45.0)
        # 縁の房 (白い髪が開口の横から垂れる)
        for (y, ln, rr_) in cfg.get("tufts", [(21.0, 3.0, 0.62), (19.6, 3.4, 0.6), (18.4, 2.2, 0.5)]):
            x = cfg.get("tuft_x", 4.0)
            F += spike("hair", "white", (s * x, y, cfg.get("tuft_z", -5.0)), (s * 0.1, -1, -0.12), ln, rr_,
                       sides=3, rings=1, hint=(0, 0, 1), phase=0.0, bend=(s * 0.3, 0, -0.2))
    return F


def arms(cfg):
    F = []
    for nm in ("armL", "armR"):
        s = side_of(nm)
        sh = cfg.get("sleeve", [2.0, 2.1, 1.8])
        pts = [np.array([s * 4.9, 16.3, -0.3]), np.array([s * 5.7, 13.4, -0.9]), np.array([s * 3.2, 11.8, -3.6])]
        F += tube(nm, "sleeve", pts, sh, sides=6, hint=(0, 1, 0), phase=30.0, caps=(False, False))
        # 袖口
        cuf = cfg.get("cuff", (1.95, 2.0))
        c0 = pts[2] + np.array([-s * 0.1, 0.0, -0.3])
        c1 = pts[2] + (np.array([-s * 1.3, 0.0, -1.1]) if True else 0)
        F += tube(nm, "cuff", [pts[2], c1], [cuf[0], cuf[1]], sides=6, hint=(0, 1, 0), phase=30.0, caps=(False, False))
        # 手 (組んだ手)
        hand_c = pts[2] + np.array([-s * 2.0, 0.1, -1.6])
        F += ellipsoid(nm, "skin", hand_c, (1.35, 0.95, 1.2), segs=6, rings=2, rot=rot_y(s * 20))
    return F


def legs(cfg):
    F = []
    for nm in ("legL", "legR"):
        s = side_of(nm)
        F += fbox(nm, "boot", (s * 1.6, 1.1, -2.2), (2.8, 2.2, 4.4), taper=(0.85, 0.8), cut=0.35, caps=(False, True))
    return F


def cord_parts(cfg):
    F = []
    s = side_of("cord")
    r = interp_rings(cfg["robe"], 9.9)
    zfront = r[3] + r[1] - cfg.get("belt_d", 0.4) - 0.2
    path = [np.array([s * 2.2, 9.6, zfront]), np.array([s * 2.6, 6.8, zfront - 0.3]),
            np.array([s * 2.4, 4.4, zfront - 0.5])]
    F += tube("cord", "cordm", path, [0.55, 0.55, 0.0], sides=4, hint=(1, 0, 0), phase=45.0)
    F += ellipsoid("cord", "cordm", np.array([s * 2.2, 9.9, zfront]), (1.0, 0.9, 0.8), segs=5, rings=1)
    return F


def front_z(spec, y, x, off=0.0):
    """長衣の八角形の断面の前側の面の上の、高さ y・横 x での z (off だけ前へ)。"""
    rx, zf, zb, zc = interp_rings(spec, y)
    ax = abs(x)
    x0, x1 = 0.3827 * rx, 0.9239 * rx
    z0, z1 = zc + 0.9239 * zf, zc + 0.3827 * zf
    if ax <= x0:
        z = z0
    elif ax >= x1:
        z = z1
    else:
        t = (ax - x0) / (x1 - x0)
        z = z0 * (1 - t) + z1 * t
    return z - off


def bell(bone, mat, top, k=1.0, clapper="cordm", n=6):
    """小さな鐘 (上が細く、裾が広がる)。top = 吊るす点。"""
    top = np.asarray(top, float)
    rings = [ngon(top + np.array([0, -0.2 * k, 0]), 0.55 * k, n, (1, 0, 0), (0, 0, 1), 30),
             ngon(top + np.array([0, -1.0 * k, 0]), 1.05 * k, n, (1, 0, 0), (0, 0, 1), 30),
             ngon(top + np.array([0, -1.8 * k, 0]), 1.45 * k, n, (1, 0, 0), (0, 0, 1), 30)]
    F = rloft(bone, mat, rings, caps=(mat, False))
    F += ellipsoid(bone, clapper, top + np.array([0, -2.0 * k, 0]), (0.5 * k, 0.45 * k, 0.5 * k), segs=4, rings=1)
    return F


def scallop_hem(cfg, bone="body", mat="hem", depth=1.3, out=0.5):
    """長衣の裾の八角形の下に、とがった波形の縁 (辺ごとに下がる三角形)。"""
    y, rx, zf, zb, zc = cfg["robe"][0]
    ring = ringp(y, rx, zf, zb, zc)
    F = []
    for k in range(len(ring)):
        p, q = ring[k], ring[(k + 1) % len(ring)]
        m = (p + q) / 2
        c = np.array([0, y, zc])
        o = nrm(np.array([m[0], 0, m[2] - zc])) * out
        tip = m + o + np.array([0, -depth, 0])
        F += poly(bone, mat, [p, q, tip], np.array([0, y - 3, zc]))
    return F


def base_body(cfg):
    F = []
    F += robe(cfg)
    F += shawl(cfg)
    F += hood(cfg)
    F += face(cfg)
    F += brows_tufts(cfg)
    F += arms(cfg)
    F += legs(cfg)
    F += cord_parts(cfg)
    return F


# ====================================================================== 顔の絵

DARKE = np.array([(10, 8, 16), (28, 22, 34), (60, 40, 46)], float)
PUP = np.array([(150, 224, 240), (222, 252, 255)], float)


PCOL = {"D": DARKE[0], "d": DARKE[2], "M": np.array((168, 76, 74), float), "m": np.array((200, 112, 104), float),
        "w": np.array(SKIN[1], float), "k": np.array((84, 50, 50), float)}


def face_paint(c, col, glow, sp):
    """顔の前の面 (肌) に、2 画素/単位の手描きのドットで目・口・頬・しわを置く (x を左右対称、y は段)。
    パターンは文字列の行 (上から)。D 暗い縁 / d やわらかい縁 / P 光る瞳 / H 瞳の照り / M 口 / m 口の赤み / w しわ。"""
    P, N, bay, owned = c["P"], c["N"], c["bay"], c["owned"]
    ax = np.abs(P[..., 0])
    sel = owned & (c["bones"] == "head") & (c["mats"] == "skin") & (N[..., 2] < -0.30)
    tx = np.floor(ax * 2.0 + 1e-6).astype(int)
    ty = np.floor(P[..., 1] * 2.0 + 1e-6).astype(int)
    if sp.get("blush"):
        bx, by, ba, bb, amt = sp["blush"]
        r = np.sqrt(((ax - bx) / ba) ** 2 + ((P[..., 1] - by) / bb) ** 2)
        m = sel & (r < 1.0)
        tint = np.array(sp.get("blush_col", (236, 120, 112)), float)
        col = np.where(m[..., None], col * (1 - amt) + tint * amt, col)

    def put(pat, ax0, ty0):
        nonlocal col, glow
        for r_, row in enumerate(pat):
            for k, ch in enumerate(row):
                if ch == ".":
                    continue
                m = sel & (tx == ax0 + k) & (ty == ty0 - r_)
                if ch in "PH":
                    cc = PUP[1] if ch == "H" else PUP[0]
                    col = np.where(m[..., None], cc, col)
                    glow = glow | m
                else:
                    col = np.where(m[..., None], PCOL[ch], col)

    for (pat, ax0, ty0) in sp["pats"]:
        put(pat, ax0, ty0)
    return col, glow
