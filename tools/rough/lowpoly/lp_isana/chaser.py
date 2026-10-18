"""イサナ用の共通部品: 頭・帽子・胴・腕・脚・荷と顔の絵。作図空間は前 = -Z、x は geo の x の符号反転 (armR = +x)。
各案 (opt_a/b/c) は cfg を渡してこの部品で組み、足りない物だけ自分で足す。"""
from __future__ import annotations

from kit2 import *  # noqa

XA = np.array([1.0, 0, 0])
ZA = np.array([0, 0, 1.0])


def oc(y, w, d, cx=0.0, cz=0.0, cut=0.35, dy=None):
    """面取りした矩形の輪 (8 点、順に前左・前右・右前・右後・後右・後左・左後・左前)。dy は点ごとの高さのずれ。"""
    r = rrect((cx, y, cz), XA, ZA, w, d, cut)
    if dy is not None:
        r = [p + np.array([0, dy[i], 0]) for i, p in enumerate(r)]
    return r


def hexring(c, rx, rz, n=6, ph=0.0, y=None):
    out = []
    for k in range(n):
        a = math.radians(ph + 360.0 * k / n)
        out.append(np.array([c[0] + rx * math.sin(a), c[1] if y is None else y, c[2] + rz * math.cos(a)]))
    return out


def rim_grid(bone, mat, r_in, r_out, ref):
    """同数の輪 2 つの間を板でつなぐ (ref は外向き判定の基準点)。"""
    n = len(r_in)
    rows = [list(r_in) + [r_in[0]], list(r_out) + [r_out[0]]]
    return grid(bone, mat, rows, np.asarray(ref, float))


# ====================================================================== 頭

def head(cfg):
    F = []
    cz = cfg.get("head_cz", -0.3)
    hw = cfg.get("head_w", 7.5)
    jaw = cfg.get("jaw_w", 4.6)
    rings = [oc(19.0, jaw, 5.0, 0, cz - 0.5, 0.5),
             oc(20.7, hw - 0.6, 7.0, 0, cz - 0.1, 0.4),
             oc(22.6, hw, 7.6, 0, cz, 0.38),
             oc(24.4, hw - 0.2, 7.4, 0, cz + 0.1, 0.38),
             oc(25.6, hw - 1.8, 6.0, 0, cz + 0.2, 0.45)]
    F += rloft("head", "skin", rings, caps=(False, "skin"))
    # 鼻 (三角錐)
    F += spike("head", "skin", (0, cfg.get("nose_y", 21.4), cz - 3.6), (0, -0.28, -1), cfg.get("nose_len", 1.0),
               cfg.get("nose_r", 0.8), sides=3, rings=1, hint=(0, 1, 0), phase=90.0)
    # 耳 (小さな板)
    if cfg.get("ears", True):
        for s_ in (1, -1):
            F += spike("head", "skin", (s_ * (hw / 2 - 0.1), 22.0, cz + 0.4), (s_, 0.1, 0.0), 1.1, 1.0, sides=3, rings=1,
                       hint=(0, 0, 1), phase=0.0)
    # 首
    F += tube("body", "skin", [np.array([0, 18.0, cz + 0.2]), np.array([0, 19.8, cz + 0.2])], 1.8, 1.7, sides=6,
              phase=30.0, caps=(False, False))
    return F


def hair(cfg):
    F = []
    cz = cfg.get("head_cz", -0.3)
    hw = cfg.get("head_w", 7.5)
    L = cfg.get("hair_len", 3.0)
    for s in (1, -1):   # もみあげ
        F += spike("hair", "hair", (s * (hw / 2 - 0.1), 23.2, cz - 2.0), (s * 0.15, -1, 0.0), 2.6, 0.9, sides=3, rings=1,
                   hint=(1, 0, 0), phase=0.0)
    # 後ろ髪 (襟足へ流れる)
    F += rloft("hair", "hair", [oc(24.4, hw - 0.2 + 0.5, 7.4 + 0.5, 0, cz + 0.6, 0.38),
                                oc(22.0, hw + 0.3, 6.4, 0, cz + 1.0, 0.38),
                                oc(24.4 - L - 3.4, hw - 2.0, 4.6, 0, cz + 1.3, 0.4)], caps=(False, False))
    return F


# ====================================================================== 帽子

def hat(cfg):
    F = []
    cz = cfg.get("head_cz", -0.3)
    by = cfg.get("brim_y", 24.7)
    bw, bd = cfg.get("brim_w", 13.2), cfg.get("brim_d", 13.4)
    droop = cfg.get("droop", 0.9)
    ch = cfg.get("crown_h", 4.0)
    cw = cfg.get("crown_w", 7.8)
    dy = [-droop, -droop, -droop * 0.2, -droop * 0.2 + cfg.get("side_up", 0.5), -droop * 0.15, -droop * 0.15,
          cfg.get("side_up", 0.5) - droop * 0.2, -droop * 0.2]
    # つば: 上面・縁・裏
    r_in = oc(by + 0.3, cw + 0.2, cw + 0.2, 0, cz + 0.3, 0.38)
    r_ot = oc(by + 0.2, bw, bd, 0, cz + 0.3, 0.42, dy)
    r_ob = oc(by - 0.35, bw, bd, 0, cz + 0.3, 0.42, dy)
    r_ib = oc(by - 0.45, cw + 0.1, cw + 0.1, 0, cz + 0.3, 0.38)
    c0 = np.array([0, by, cz + 0.3])
    F += rim_grid("hat", "hat", r_in, r_ot, c0 - np.array([0, 6.0, 0]))
    F += rim_grid("hat", "hat_rim", r_ot, r_ob, c0)
    F += rim_grid("hat", "hat_dk", r_ob, r_ib, c0 + np.array([0, 6.0, 0]))
    # 山
    top = by + 0.3 + ch
    topdy = [-0.9, -0.9, -0.3, 0.1, 0.2, 0.2, 0.1, -0.3] if cfg.get("dent", True) else [0] * 8
    crown = [oc(by + 0.2, cw, cw, 0, cz + 0.3, 0.38),
             oc(by + 0.2 + ch * 0.5, cw - 0.4, cw - 0.4, 0, cz + 0.3, 0.38),
             oc(top, cw - cfg.get("crown_taper", 1.7), cw - cfg.get("crown_taper", 1.7) + 0.2, 0, cz + 0.3, 0.45, topdy)]
    F += rloft("hat", "hat", crown, caps=(False, "hat"))
    # 帯
    bandm = cfg.get("band_mat", "band")
    F += rloft("hat", bandm, [oc(by + 0.35, cw + 0.15, cw + 0.15, 0, cz + 0.3, 0.38),
                              oc(by + 1.5, cw - 0.05, cw - 0.05, 0, cz + 0.3, 0.38)], caps=(False, False))
    return F


# ====================================================================== 胴

TORSO_FRONT = {12.0: -2.8, 15.5: -3.0, 18.8: -2.7}


def torso(cfg):
    F = []
    cw = cfg.get("chest_w", 9.0)
    sw = cfg.get("shoulder_w", 9.6)
    ww = cfg.get("waist_w", 8.0)
    rings = [oc(12.0, ww, 5.4, 0, -0.1, 0.38), oc(15.5, cw, 6.0, 0, 0.0, 0.38), oc(18.8, sw, 5.6, 0, 0.1, 0.42),
             oc(20.3, 6.2, 4.6, 0, 0.1, 0.45)]
    F += rloft("body", "coat", rings, caps=(False, "coat"))
    return F


def skirt(cfg):
    """胴の下の外套の裾。hem_y: 裾の高さ、hem_w/hem_d: 裾の幅・奥行。jag で裾をぎざぎざに。"""
    hy = cfg.get("hem_y", 6.2)
    hw, hd = cfg.get("hem_w", 10.2), cfg.get("hem_d", 7.4)
    jag = cfg.get("jag", None)
    mid_y = (12.4 + hy) / 2 + cfg.get("skirt_mid_dy", 0.4)
    rings = [oc(12.4, cfg.get("waist_w", 8.0) + 0.5, 5.8, 0, -0.1, 0.38),
             oc(mid_y, (cfg.get("waist_w", 8.0) + hw) / 2 + 0.3, (5.8 + hd) / 2, 0, 0.0, 0.38),
             oc(hy, hw, hd, 0, 0.1, 0.38, jag)]
    return rloft("body", cfg.get("skirt_mat", "coat"), rings, caps=(False, False),
                 mats=[cfg.get("skirt_mat", "coat"), cfg.get("skirt_mat", "coat"), cfg.get("skirt_mat", "coat")])


def belt(cfg, y0=11.3, y1=12.7, mat="belt"):
    ww = cfg.get("waist_w", 8.0)
    F = rloft("body", mat, [oc(y0, ww + 1.2, 6.5, 0, -0.1, 0.38), oc(y1, ww + 1.2, 6.5, 0, -0.1, 0.38)],
              caps=(False, False))
    F += fbox("body", "metal", (0, (y0 + y1) / 2, -3.55), (1.9, 1.7, 0.6), cut=0.25)
    return F


# ====================================================================== 腕・脚

def arm(bone, s, cfg):
    F = []
    x0 = s * cfg.get("arm_x", 5.6)
    r0, r1 = cfg.get("sleeve_r", 1.65), cfg.get("sleeve_rw", 1.45)
    path = [np.array([x0, 19.6, 0.0]), np.array([x0 + s * 0.15, 13.2, 0.05]), np.array([x0 + s * 0.1, 7.2, -0.05])]
    F += tube(bone, cfg.get("sleeve_mat", "coat"), path, [r0, r0 * 0.94, r1], sides=6, hint=(1, 0, 0), phase=0.0,
              caps=(True, False))
    # 袖口
    F += rloft(bone, cfg.get("cuff_mat", "cuff"), [oc(7.7, r1 * 2 + 0.7, r1 * 2 + 0.7, x0 + s * 0.1, -0.05, 0.4),
                                                    oc(6.4, r1 * 2 + 0.5, r1 * 2 + 0.5, x0 + s * 0.1, -0.05, 0.4)],
               caps=(False, False))
    # 手
    F += rloft(bone, cfg.get("hand_mat", "skin"), [oc(6.4, 2.6, 2.6, x0 + s * 0.1, -0.1, 0.45),
                                                    oc(4.6, 2.8, 2.7, x0 + s * 0.15, -0.1, 0.45)], caps=(False, "skin"))
    return F


def leg(bone, s, cfg):
    F = []
    x0 = s * 1.8
    tr = cfg.get("trouser_mat", "trousers")
    F += tube(bone, tr, [np.array([x0, 8.6, 0.0]), np.array([x0, 5.0, -0.1]), np.array([x0, 3.4, -0.2])],
              [1.75, 1.5, 1.6], sides=6, hint=(1, 0, 0), caps=(False, False))
    bt = cfg.get("boot_top", 4.4)
    rings = [oc(bt + 0.4, 3.9, 3.8, x0, -0.2, 0.4), oc(2.3, 3.4, 4.0, x0, -0.5, 0.4), oc(0.0, 3.6, 5.8, x0, -1.4, 0.5)]
    F += rloft(bone, "boot", rings, caps=(False, "sole"))
    return F


# ====================================================================== 荷

def pack(cfg):
    F = []
    pw = cfg.get("pack_w", 8.8)
    ph = cfg.get("pack_h", 10.4)
    py = cfg.get("pack_y", 15.0)
    pz = cfg.get("pack_z", 5.1)
    F += fbox("pack", "pack", (0, py, pz), (pw, ph, 3.2), taper=(0.92, 0.9), cut=0.3)
    if cfg.get("lid", False):
        F += fbox("pack", "pack_dk", (0, py + ph / 2 - 1.8, pz + 0.2), (pw + 0.3, 3.0, 3.6), cut=0.3, taper=(0.9, 0.9))
    if cfg.get("bedroll", True):
        top = py + ph / 2 + 0.6
        F += hpipe("pack", "roll", (-pw / 2 + 0.2, top, pz), (pw / 2 - 0.2, top, pz), 1.5, 1.5, n=6, rot=0.0,
                   caps=(True, True), cap_mat="roll_end")
        F += hpipe("pack", "roll", (-pw / 2 - 0.2, py - ph / 2 - 0.4, pz + 0.3), (pw / 2 + 0.2, py - ph / 2 - 0.4, pz + 0.3),
                   1.2, 1.2, n=6, rot=0.0, caps=(True, True), cap_mat="roll_end")
    return F


def tail(x, y_top, y_bot, z, w, notch=1.0, depth=0.5, mat="scarf", bone="body"):
    pts = [np.array([x - w / 2, y_top, z]), np.array([x + w / 2, y_top, z]), np.array([x + w / 2 + 0.1, y_bot, z - 0.35]),
           np.array([x, y_bot + notch, z - 0.4]), np.array([x - w / 2 - 0.1, y_bot, z - 0.35])]
    return extrude(bone, mat, pts, (0, 0, -depth), caps=(True, True))


def straps(cfg, mat="strap", xs=(2.6, -2.6)):
    """胴の前を斜めに通る荷のひも (外套の上に薄い板)。"""
    F = []
    for x in xs:
        pts = [np.array([x - 0.55, 19.4, -2.75]), np.array([x + 0.55, 19.4, -2.75]), np.array([x + 0.65, 13.4, -3.2]),
               np.array([x - 0.65, 13.4, -3.2])]
        F += extrude("body", mat, pts, (0, 0, -0.35), caps=(True, True))
    return F


# ====================================================================== 顔の絵

def face_painter(cfg):
    """頭の皮膚の面のうち正面を向く面に、3D 位置から目・眉・鼻の陰・口・髭・帽子の影を描く。"""
    ey = cfg.get("eye_y", 22.4)
    ex = cfg.get("eye_x", 1.95)
    ew = cfg.get("eye_w", 1.0)          # 目の半幅
    eh = cfg.get("eye_h", 0.42)         # 目の半高 (小さいほど細い)
    st = cfg.get("stubble", 0.4)
    sc = cfg.get("stubble_col", (74, 52, 44))
    brow_t = cfg.get("brow_t", 0.42)
    tilt = cfg.get("brow_tilt", 0.16)
    brow_c = cfg.get("brow_col", (44, 28, 22))
    iris = cfg.get("iris", (28, 34, 44))
    mouth_w = cfg.get("mouth_w", 1.5)
    smile = cfg.get("smile", -0.1)
    scar = cfg.get("scar", False)
    shade_y = cfg.get("hat_shade_y", 23.5)

    def extra(c, col, glow):
        P, N, bay, owned = c["P"], c["N"], c["bay"], c["owned"]
        bones, mats = c["bones"], c["mats"]
        base = owned & (bones == "head") & (mats == "skin")
        front = base & (N[..., 2] < -0.35)
        x, y = P[..., 0], P[..., 1]
        ax = np.abs(x)
        col = col.copy()
        # 帽子の影 (額)
        for k, (yy, dk) in enumerate(((shade_y, 0.86), (shade_y + 0.8, 0.76))):
            sel = base & (y > yy + 0.25 * bay)
            col = np.where(sel[..., None], col * dk, col)
        # 頬のあたりの血色
        cheek = front & (ax > 1.9) & (ax < 3.3) & (y > ey - 2.7) & (y < ey - 1.3)
        col = np.where(cheek[..., None], col * np.array([1.04, 0.95, 0.94]), col)
        if cfg.get("freckle", 0):
            fk = front & (ax > 1.2) & (ax < 3.2) & (y > ey - 2.6) & (y < ey - 0.9) &                 (hash3(P * 3.3, 9) > 1 - cfg["freckle"])
            col = np.where(fk[..., None], np.array((158, 98, 70), float), col)
        if cfg.get("age_lines", False):
            ln = front & (ax > 0.9) & (ax < 2.6) & (np.abs(y - (ey - 2.3 - 0.2 * ax)) < 0.2)
            col = np.where(ln[..., None], col * 0.78, col)
            fr = base & (y > ey + 2.1) & (y < ey + 2.5) & (ax < 2.4)
            col = np.where(fr[..., None], col * 0.82, col)
        # 鼻の下の影
        nose = front & (ax < 0.55 + 0.2 * bay) & (y > ey - 2.35) & (y < ey - 1.55)
        col = np.where(nose[..., None], col * 0.7, col)
        # 目 (白目 + 黒目 + 上まぶた + 下の隈) と眉
        for s in (1, -1):
            dx = x * s - ex
            side = front & (x * s > 0)
            dyy = y - ey - tilt * 0.5 * dx
            lid = side & (np.abs(dx) < ew + 0.25) & (dyy > eh) & (dyy < eh + 0.4)
            col = np.where(lid[..., None], col * 0.72, col)
            bag = side & (np.abs(dx) < ew - 0.1) & (dyy < -eh) & (dyy > -eh - 0.45)
            col = np.where(bag[..., None], col * 0.84, col)
            eye = side & (np.abs(dx) < ew) & (np.abs(dyy) < eh)
            sclera = np.where((dx < 0)[..., None], np.array([206, 208, 200]), np.array([170, 172, 166]))
            col = np.where(eye[..., None], sclera, col)
            ir = eye & (np.abs(dx) < ew * 0.42)
            col = np.where(ir[..., None], np.array(iris), col)
            yc = ey + cfg.get("brow_dy", 1.15) + tilt * (ax - ex)
            brow = side & (np.abs(ax - ex - cfg.get("brow_dx", 0.1)) < ew + 0.4) & (np.abs(y - yc) < brow_t)
            col = np.where(brow[..., None], np.array(brow_c) + 6 * bay[..., None], col)
        # 口
        my = ey - cfg.get("mouth_dy", 3.55)
        mouth = front & (ax < mouth_w) & (np.abs(y - my - smile * (ax ** 2) * 0.35) < 0.26)
        col = np.where(mouth[..., None], np.array(cfg.get("mouth_col", (118, 62, 54))), col)
        under = front & (ax < mouth_w * 0.6) & (np.abs(y - (my - 0.55)) < 0.2)
        col = np.where(under[..., None], col * 1.1, col)
        # 髭
        sel = front & (y < ey - 2.0) & ~mouth
        if cfg.get("stubble_mouth_gap", True):
            sel &= ~((ax < mouth_w * 0.9) & (np.abs(y - my) < 0.5))
        n = hash3(P * 2.7, 3)
        dens = st * np.clip((ey - 1.6 - y) / 2.2, 0.35, 1.0)
        sel &= (n + 0.25 * bay > 1 - dens)
        col = np.where(sel[..., None], np.array(sc, float), col)
        # 顎の下・横の面にもまばらに
        side_sel = base & ~front & (N[..., 1] < 0.4) & (y < ey - 1.0) & (hash3(P * 2.1, 5) + 0.25 * bay > 1 - st * 0.6)
        col = np.where(side_sel[..., None], np.array(sc, float), col)
        if scar:
            d = seg_dist(x, y, (-3.4, ey + 0.2), (-2.1, ey - 2.4))
            sl = front & (d < 0.22) & (x < 0)
            col = np.where(sl[..., None], np.array((196, 148, 128), float), col)
        return col, glow

    return extra
