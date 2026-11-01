"""スキン「魔女」(服だけ。ゴシックの魔女の服と帽子、浮かぶ髑髏が三つ回る)。

python tools/rough/skinbox/skins/witch.py [install]   → docs/review/skins/full/witch/ に見本の絵と mesh.json・atlas.png (install で mod の中へ)
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from kit import Model, export, sheet, install, ROOT  # noqa: E402
from paint import C, mix, blank, out, cloth, skin, hair, lace, clear, sprite, stamp, circle_alpha, edge, shade_rows, rnd  # noqa: E402

ID = "witch"

# ---------------------------------------------------------------- 色
BLACK = dict(base="#2b2534", light="#463d54", dark="#16121c", line="#0e0b12")
PURPLE = dict(base="#6c2f90", light="#9a5cc4", dark="#43195f")
CRIMSON = dict(base="#a3143c", light="#d93a62", dark="#650a24")
SILVER = dict(base="#c9cdd9", light="#f4f6fb", dark="#7e8497")
BONE = dict(base="#ece2cc", light="#fff8ea", dark="#b5a78c")
GLOW = "#c45cff"
GLOW2 = "#f2d2ff"


# ---------------------------------------------------------------- 布の模様
def pleats(img, face, W, H, box, every=5):
    for x in range(0, W, every):
        img[:, x, :3] = mix(img[:, x, :3], C(BLACK["line"]), 0.55)
        if x + 1 < W:
            img[:, x + 1, :3] = mix(img[:, x + 1, :3], C(BLACK["light"]), 0.35)
    return img


def skirt_panel(outer):
    lining = cloth(PURPLE["dark"], PURPLE["base"], "#2a0f3c", edges=False)

    def over(img, face, W, H, box):
        pleats(img, face, W, H, box)
        # 裾: 紫の帯と紅の細い線、いちばん下はレースの波
        band = max(3, H // 7)
        img[H - band - 3:H - 3, :, :3] = shade_rows(band, C(PURPLE["light"]), C(PURPLE["base"]), C(PURPLE["dark"]))[:, None, :]
        img[H - band - 4, :, :3] = C(CRIMSON["base"])
        img[H - 3:H, :, :3] = C("#ece6f1")
        img[H - 3:H, :, :3] = mix(img[H - 3:H, :, :3], C("#c4bad0"), np.linspace(0, 0.5, 3)[:, None, None].repeat(W, 1))
        for x in range(W):
            if x % 4 in (0, 3):
                img[H - 1, x, 3] = 0
        for x in range(0, W, 4):
            img[H - 2, x, 3] = 0
        return img
    outer_p = cloth(BLACK["base"], BLACK["light"], BLACK["dark"], BLACK["line"], over=over)

    def p(face, W, H, box):
        if face == outer:
            return outer_p(face, W, H, box)
        if face in "nsew":
            return lining(face, W, H, box)
        return cloth(BLACK["dark"], edges=False)(face, W, H, box)
    return p


def petticoat_panel(outer):
    l = lace("#efe9f3", "#c3b8d0")

    def p(face, W, H, box):
        if face in "ud":
            return clear()(face, W, H, box)
        return l(face, W, H, box)
    return p


def corset(face, W, H, box):
    img = cloth(BLACK["base"], BLACK["light"], BLACK["dark"], BLACK["line"])(face, W, H, box).astype(float)
    if face in "ns":
        cx = W // 2
        pw = W // 4
        img[:, cx - pw:cx + pw, :3] = shade_rows(H, C(CRIMSON["light"]), C(CRIMSON["base"]), C(CRIMSON["dark"]))[:, None, :]
        # 骨の線
        for x in (cx - pw - 1, cx + pw):
            img[:, x, :3] = C(PURPLE["base"])
        if face == "n":
            # 前の編み上げ (×)
            for y in range(1, H - 1, 3):
                img[y, cx - 2, :3] = C("#111111")
                img[y, cx + 1, :3] = C("#111111")
                img[y + 1, cx - 1, :3] = C("#111111")
                img[y + 1, cx, :3] = C("#111111")
                img[y, cx - 3, :3] = C(SILVER["base"])
                img[y, cx + 2, :3] = C(SILVER["base"])
    elif face in "ew":
        for x in range(1, W, 3):
            img[:, x, :3] = mix(img[:, x, :3], C(PURPLE["dark"]), 0.5)
    return out(img)


def bodice(face, W, H, box):
    img = cloth(BLACK["base"], BLACK["light"], BLACK["dark"], BLACK["line"])(face, W, H, box).astype(float)
    if face == "n":
        # 胸の縁取り (紫) と銀のボタン
        img[0:2, :, :3] = C(PURPLE["base"])
        for x in (3, W - 4):
            img[:, x, :3] = C(PURPLE["dark"])
        img[H - 2:, :, :3] = C(PURPLE["base"])
    elif face == "s":
        img[H - 2:, :, :3] = C(PURPLE["base"])
        # 背中の編み上げ
        cx = W // 2
        for y in range(2, H - 3, 3):
            img[y, cx - 1, :3] = C(CRIMSON["base"])
            img[y + 1, cx, :3] = C(CRIMSON["base"])
    elif face in "ew":
        img[H - 2:, :, :3] = C(PURPLE["base"])
    return out(img)


def jabot(face, W, H, box):
    """胸元のひだ飾り: 白いレースが三段、下の縁は波。"""
    if face != "n":
        return lace()(face, W, H, box)
    img = blank(W, H, C("#efe9f3"))
    tiers = 3
    th = H / tiers
    for t in range(tiers):
        y0, y1 = int(t * th), int((t + 1) * th)
        img[y0:y1, :, :3] = shade_rows(y1 - y0, C("#ffffff"), C("#ece5f1"), C("#b9adc8"), 0.2, 0.55)[:, None, :]
        for x in range(W):
            if x % 3 == 0:
                img[y0:y1, x, :3] = mix(img[y0:y1, x, :3], C("#b9adc8"), 0.5)
    for x in range(W):
        if x % 3 == 1:
            img[H - 1, x, 3] = 0
    img[:, 0, 3] = 0
    img[:, W - 1, 3] = 0
    img[0:2, 0, 3] = 255
    return out(img)


def gem(col, light, dark):
    def p(face, W, H, box):
        img = blank(W, H, C(col))
        if face in "nsewu" and W >= 2 and H >= 2:
            img[0, 0, :3] = C(light)
            img[H - 1, :, :3] = C(dark)
            img[:, W - 1, :3] = mix(img[:, W - 1, :3], C(dark), 0.6)
        g = np.ones((H, W), bool)
        return out(img), g
    return p


def buckle(face, W, H, box):
    img = cloth(**{"base": CRIMSON["base"], "light": CRIMSON["light"], "dark": CRIMSON["dark"]})(face, W, H, box).astype(float)
    if face == "n":
        cx, cy = W // 2, H // 2
        for y in range(max(0, cy - 2), min(H, cy + 3)):
            for x in range(cx - 3, cx + 3):
                if y in (cy - 2, cy + 2) or x in (cx - 3, cx + 2):
                    img[y, x, :3] = C(SILVER["light"] if y == cy - 2 else SILVER["base"])
    return out(img)


def boot(face, W, H, box):
    img = cloth("#1f1a24", "#3b3346", "#0f0c13", "#08060a")(face, W, H, box).astype(float)
    if face in "nsew":
        # 銀の留め金の付いた紫の帯を二本
        for yy in (int(H * 0.3), int(H * 0.66)):
            img[yy, :, :3] = C("#4a3f58")
            img[yy + 1, :, :3] = C("#0c0a10")
            if face in "ew":
                img[yy - 1:yy + 2, W // 2 - 1:W // 2 + 1, :3] = C(SILVER["base"])
                img[yy - 1, W // 2 - 1, :3] = C(SILVER["light"])
        if face == "n":
            # 前の紐
            cx = W // 2
            for y in range(1, H - 1, 2):
                img[y, cx - 1:cx + 1, :3] = C("#5a4d68")
    return out(img)


def stocking(face, W, H, box):
    img = cloth("#201b28", "#3a3148", "#110e16", "#0a080d")(face, W, H, box).astype(float)
    if face in "nsew":
        # 薄い光沢の筋
        img[:, W // 3, :3] = mix(img[:, W // 3, :3], C("#5b4e70"), 0.6)
        if face in "ns":
            # 小さな十字の刺繍
            cx = W // 2
            y = H // 2
            for d in (-1, 0, 1):
                img[y + d, cx, :3] = C(PURPLE["light"])
                img[y, cx + d, :3] = C(PURPLE["light"])
    return out(img)


def sole(face, W, H, box):
    return cloth("#120f15", "#2c2632", "#08070a", edges=False)(face, W, H, box)


def puff(face, W, H, box):
    img = cloth(BLACK["base"], "#4a4058", BLACK["dark"], BLACK["line"])(face, W, H, box).astype(float)
    if face in "nsew":
        for x in range(1, W, 4):
            img[:, x, :3] = mix(img[:, x, :3], C(PURPLE["base"]), 0.55)
            if x + 1 < W:
                img[:, x + 1, :3] = mix(img[:, x + 1, :3], C(PURPLE["light"]), 0.25)
        # 丸み: 上の角を明るく
        img[1:3, :, :3] = mix(img[1:3, :, :3], C("#5d5270"), 0.5)
    return out(img)


def cuff(face, W, H, box):
    img = cloth(BLACK["base"], BLACK["light"], BLACK["dark"], BLACK["line"])(face, W, H, box).astype(float)
    if face in "nsew":
        img[H - 2:, :, :3] = C(PURPLE["base"])
        img[H - 3, :, :3] = C(CRIMSON["base"])
    if face == "d":
        img[..., :3] = C(PURPLE["dark"])
        img[1:-1, 1:-1, :3] = C("#2a0f3c")
    return out(img)


def hat(face, W, H, box):
    img = blank(W, H, C("#30273b"))
    if face in "nsew":
        img[..., :3] = shade_rows(H, C("#3a3047"), C("#30273b"), C("#272030"), 0.4, 0.6)[:, None, :]
    elif face == "u":
        # 段の上の面は明るく照らされるので、そのぶん暗く塗って段を目立たせない
        img[..., :3] = C("#30273b") * 0.8
    return out(img)


def brim(face, W, H, box):
    if face not in "ud":
        return clear()(face, W, H, box)
    col = C("#2a2133") if face == "u" else C("#43195f")
    img = blank(W, H, col)
    m = circle_alpha(W, H)
    rim = circle_alpha(W, H, 0.9)
    yy, xx = np.mgrid[0:H, 0:W]
    dx = (xx + 0.5) / W * 2 - 1
    dy = (yy + 0.5) / H * 2 - 1
    r = np.sqrt(dx * dx + dy * dy)
    if face == "u":
        img[..., :3] = mix(C("#3e3350"), C("#1c1624"), np.clip(r, 0, 1)[..., None])
        # 同心のしわ
        ring = (np.abs((r * 9) % 1 - 0.5) < 0.06) & (r > 0.5)
        img[ring, :3] = C("#4d4060")
    else:
        img[..., :3] = mix(C("#5a2a7c"), C("#2a0f3c"), np.clip(r, 0, 1)[..., None])
    img[rim, :3] = C(PURPLE["base"]) if face == "u" else C(PURPLE["light"])
    img[~m, 3] = 0
    return out(img)


def skull(face, W, H, box):
    """髑髏の頭。前の面に目のくぼみ (光る)。"""
    b = C(BONE["base"])
    img = cloth(BONE["base"], BONE["light"], BONE["dark"], "#8d7f66")(face, W, H, box).astype(float)
    glow = np.zeros((H, W), bool)
    if face == "n":
        cx = W // 2
        img[0:max(1, H // 2), cx - 1:cx + 1, :3] = C("#3a2a44")
        img[H - 1, :, :3] = C(BONE["dark"])
    elif face == "u":
        # 頭のひび
        img[H // 3, W // 2:W // 2 + 2, :3] = C("#8d7f66")
        img[H // 3 + 1, W // 2 + 1, :3] = C("#8d7f66")
    return out(img), glow


def jaw(face, W, H, box):
    img = cloth(BONE["base"], BONE["light"], BONE["dark"], "#8d7f66")(face, W, H, box).astype(float)
    if face == "n":
        for x in range(1, W - 1):
            img[0:max(1, H - 1), x, :3] = C(BONE["light"]) if x % 2 else C("#7d705a")
    return out(img)


def ribbon(face, W, H, box):
    return cloth(CRIMSON["base"], CRIMSON["light"], CRIMSON["dark"])(face, W, H, box)


def ribbon_tail(face, W, H, box):
    img = cloth(CRIMSON["base"], CRIMSON["light"], CRIMSON["dark"])(face, W, H, box).astype(float)
    if face in "nsew" and W >= 3:
        # 下の端を燕尾に切る
        for k in range(min(3, H)):
            for x in range(W):
                if abs(x - (W - 1) / 2) < (3 - k) * W / 6:
                    img[H - 1 - k, x, 3] = 0
    return out(img)


def moon(face, W, H, box):
    """三日月の飾り (横から見える面だけ。丸から丸をずらして抜く)。"""
    img = blank(W, H, C("#e9e4ff"))
    if face in "ew":
        yy, xx = np.mgrid[0:H, 0:W]
        u = (xx + 0.5) / W * 2 - 1
        v = (yy + 0.5) / H * 2 - 1
        a = u * u + v * v <= 1.0
        b = (u - 0.45) ** 2 + (v + 0.15) ** 2 <= 0.72
        m = a & ~b
        img[..., :3] = mix(C("#ffffff"), C("#a99ee0"), np.clip((v + 1) / 2, 0, 1)[..., None])
        img[~m, 3] = 0
        if face == "e":
            # 向こう側の面は裏から見ると左右が逆になるので、映して手前の面と重ねる
            return out(img[:, ::-1]), m[:, ::-1]
        return out(img), m
    if face in "nsud":
        return out(blank(W, H)), None
    return out(img), None


def collar_flap(outer):
    """白い襟 (角の丸い布、縁に黒と紫の線、下の縁はレースの波)。"""
    def p(face, W, H, box):
        if face != outer:
            return out(blank(W, H, C("#2a2133")))
        img = blank(W, H, C("#f6f1f8"))
        img[..., :3] = shade_rows(H, C("#ffffff"), C("#f2ecf5"), C("#cfc4da"), 0.25, 0.6)[:, None, :]
        img[H - 3:H - 1, :, :3] = C("#2a2133")
        img[H - 4, :, :3] = C(PURPLE["base"])
        for y in range(2, H - 5, 3):
            for x in range((y // 3) % 2 * 2 + 1, W - 1, 4):
                img[y, x, :3] = C("#cbbfd8")
        for x in range(W):
            if x % 4 in (0, 3):
                img[H - 1, x, 3] = 0
        for (cy, cx) in ((H - 1, 0), (H - 1, 1), (H - 2, 0), (H - 1, W - 1), (H - 1, W - 2), (H - 2, W - 1)):
            img[cy, cx, 3] = 0
        return out(img)
    return p


def skull_top(face, W, H, box):
    img = cloth(BONE["base"], BONE["light"], BONE["dark"], "#8d7f66")(face, W, H, box).astype(float)
    glow = np.zeros((H, W), bool)
    if face == "n":
        # 下の半分に二つの目のくぼみ (光る紫)
        ew = max(2, W // 3)
        for x0 in (1, W - 1 - ew):
            for y in range(H // 2 - 1, H):
                for x in range(x0, x0 + ew):
                    core = (y >= H // 2) and (x0 < x < x0 + ew - 1)
                    img[y, x, :3] = C(GLOW2) if core and y == H - 2 else C(GLOW)
                    glow[y, x] = True
        img[0, :, :3] = C(BONE["light"])
    elif face in "ew":
        img[H // 2:, W // 3:W // 3 + 2, :3] = mix(img[H // 2:, W // 3:W // 3 + 2, :3], C(BONE["dark"]), 0.7)
    elif face == "u":
        img[H // 3, W // 2:W // 2 + 3, :3] = C("#8d7f66")
        img[H // 3 + 1, W // 2 + 2, :3] = C("#8d7f66")
        img[H // 3 - 1, W // 2 - 1, :3] = C("#8d7f66")
    return out(img), glow


# ---------------------------------------------------------------- 組み立て
def witch_hat(m, y0, base, brim_w, side_x):
    """帽子 (広いつば、16 段の円すいが後ろへ折れて垂れる。上の段は揺れる) と、横の紅のリボン・三日月。"""
    import math
    TILT = 6
    m.box("head", (-brim_w / 2, y0 - 0.2, -brim_w / 2), (brim_w, 0.4, brim_w), brim, rot=(TILT, 0, 0), pivot=(0, y0, 0))
    m.box("head", (-(base + 0.25) / 2, y0, -(base + 0.25) / 2), (base + 0.25, 1.25, base + 0.25), buckle, rot=(TILT, 0, 0), pivot=(0, y0, 0))
    pos = np.array([0.0, y0, 0.0])
    n = 16
    ds = 10.5 / n
    parent = "head"
    for i in range(n):
        s = i / (n - 1)
        ang = TILT + 125 * s ** 2.2
        w = (base - 0.9) * (1 - s) ** 1.1 + 0.9
        bn = parent
        if i == 8:
            bn = m.bone("cloth_hat1", "head", tuple(pos))
            parent = bn
        elif i == 13:
            bn = m.bone("cloth_hat2", "cloth_hat1", tuple(pos))
            parent = bn
        m.box(bn, (pos[0] - w / 2, pos[1], pos[2] - w / 2), (w, ds * 1.35, w), hat, rot=(ang, 0, 0), pivot=tuple(pos))
        a = math.radians(ang)
        pos = pos + np.array([0, math.cos(a), math.sin(a)]) * ds
    x = side_x
    m.box("head", (x, y0 + 0.2, -1.0), (0.7, 1.4, 1.4), ribbon)
    m.box("head", (x - 0.25, y0 + 0.1, -3.6), (0.6, 2.0, 2.6), ribbon, rot=(22, 0, 0), pivot=(x, y0 + 0.9, -1.0))
    m.box("head", (x - 0.25, y0 + 0.1, 0.4), (0.6, 2.0, 2.6), ribbon, rot=(-22, 0, 0), pivot=(x, y0 + 0.9, 0.4))
    m.bone("cloth_charm", "head", (x - 0.1, y0 + 0.2, -0.3))
    m.box("cloth_charm", (x - 0.15, y0 - 2.2, -0.35), (0.12, 2.4, 0.12), cloth(SILVER["dark"], edges=False), pivot=(x - 0.1, y0 + 0.2, -0.3))
    m.box("cloth_charm", (x - 0.25, y0 - 4.4, -1.3), (0.3, 2.4, 2.2), moon, pivot=(x - 0.1, y0 + 0.2, -0.3))


def skulls(m):
    """浮かぶ髑髏 (三つ。肩のまわりを回り、ひとつずつ上下する)。"""
    m.bone("orbit_skulls", None, (0, 21.0, 0))
    for i, (cx, cy, cz) in enumerate(((-9.6, 25.0, -1.5), (9.2, 19.5, 2.0), (1.5, 28.5, 9.0))):
        bn = m.bone(f"float_skull{i + 1}", "orbit_skulls", (cx, cy, cz))
        m.box(bn, (cx - 1.35, cy - 0.5, cz - 1.3), (2.7, 2.0, 2.7), skull_top)
        m.box(bn, (cx - 1.05, cy - 1.25, cz - 1.45), (2.1, 1.1, 2.2), skull)
        m.box(bn, (cx - 0.8, cy - 1.85, cz - 1.3), (1.6, 0.65, 1.6), jaw)


def skirt(m, Y0, L, hw, hd, tilt=17):
    """スカート (外の黒い布 8 枚と、下の白いレース 8 枚)。付け根は腰。hw / hd = 腰の幅・奥行きの半分。"""
    for bn, outer, z, sign in (("skirt_f", "n", -hd, 1), ("skirt_b", "s", hd, -1)):
        piv = (0, Y0, z)
        m.bone(bn, "body", piv)
        m.box(bn, (-hw, Y0 - L, z - 0.175), (hw * 2, L, 0.35), skirt_panel(outer), rot=(tilt * sign, 0, 0), pivot=piv)
        m.box(bn, (-hw + 0.2, Y0 - L - 0.9, z - 0.175 + 0.25 * sign), (hw * 2 - 0.4, L + 0.6, 0.35), petticoat_panel(outer),
              rot=(tilt * 0.8 * sign, 0, 0), pivot=piv)
    sx = hw + 0.2
    m.pair_bone("skirt_R", "body", (-sx, Y0, 0))
    m.pair("skirt_R", (-sx - 0.35, Y0 - L, -hd), (0.35, L, hd * 2), skirt_panel("w"), rot=(0, 0, -tilt), pivot=(-sx, Y0, 0))
    m.pair("skirt_R", (-sx - 0.1, Y0 - L - 0.9, -hd + 0.2), (0.35, L + 0.6, hd * 2 - 0.4), petticoat_panel("w"), rot=(0, 0, -tilt * 0.77), pivot=(-sx, Y0, 0))
    cw = min(hw, hd) * 1.6
    for nm, (px, pz), yw in (("skirt_fR", (-hw + 0.25, -hd + 0.35), 45), ("skirt_bR", (-hw + 0.25, hd - 0.35), 135)):
        m.pair_bone(nm, "body", (px, Y0, pz))
        m.pair(nm, (px - cw / 2, Y0 - L, pz - 0.17), (cw, L, 0.35), skirt_panel("n"), rot=(tilt, 0, 0), pivot=(px, Y0, pz), yaw=yw)
        m.pair(nm, (px - cw / 2 + 0.3, Y0 - L - 0.9, pz + 0.1), (cw - 0.6, L + 0.6, 0.35), petticoat_panel("n"), rot=(tilt * 0.77, 0, 0),
               pivot=(px, Y0, pz), yaw=yw)


def build_outfit():
    """服だけ: 着ている人のバニラの体 (自分のスキンの顔・髪・手) の上に重ねる。大きさは Steve の体 (重ね着の層 +0.25 より外)。"""
    m = Model("skin_full/" + ID, outfit=True)
    black = cloth(**BLACK)
    # ---- 脚 (ニーハイの靴下とレース、厚底の編み上げブーツ)。脚 = x -3.9..0.1, z ±2
    m.pair("legR", (-4.3, 4.8, -2.35), (4.6, 5.9, 4.7), stocking)
    m.pair("legR", (-4.45, 10.3, -2.5), (4.9, 1.0, 5.0), lace("#efe9f3", "#c3b8d0"), hide=("u", "d"))
    m.pair("legR", (-4.4, 1.2, -2.45), (4.8, 4.0, 4.9), boot)
    m.pair("legR", (-4.5, 0.45, -3.3), (5.0, 1.6, 5.75), boot)
    m.pair("legR", (-4.6, 0.0, -3.4), (5.2, 0.5, 5.95), sole)
    # ---- 体 (x ±4, z ±2)
    m.box("body", (-4.3, 12.3, -2.35), (8.6, 5.6, 4.7), corset)
    m.box("body", (-4.4, 17.7, -2.45), (8.8, 6.45, 4.9), bodice)
    m.box("body", (-1.5, 18.2, -2.85), (3.0, 4.9, 0.45), jabot)
    m.box("body", (-0.65, 22.4, -3.1), (1.3, 1.2, 0.55), ribbon)
    m.pair("body", (-2.8, 22.0, -2.95), (2.15, 1.7, 0.4), ribbon, rot=(0, 0, -12), pivot=(-0.65, 22.8, -2.9))
    m.pair("body", (-1.2, 19.6, -3.0), (0.75, 2.8, 0.25), ribbon_tail, rot=(0, 0, -10), pivot=(-0.65, 22.4, -2.9))
    m.pair("body", (-4.4, 20.9, -2.75), (3.6, 3.0, 0.28), collar_flap("n"), rot=(0, 0, -6), pivot=(-0.6, 23.9, -2.7))
    m.pair("body", (-4.65, 21.4, -2.5), (0.28, 2.6, 5.0), collar_flap("w"))
    m.box("body", (-4.5, 21.2, 2.5), (9.0, 2.8, 0.28), collar_flap("s"))
    m.box("body", (-0.9, 14.8, 2.4), (1.8, 1.8, 0.8), ribbon)
    m.pair("body", (-4.6, 14.3, 2.5), (3.8, 2.8, 0.7), ribbon, rot=(0, 0, 12), pivot=(-0.9, 15.7, 2.85))
    m.pair_bone("ribbon_R", "body", (-0.7, 15.3, 3.0))
    m.pair("ribbon_R", (-2.0, 6.8, 2.7), (1.4, 8.5, 0.3), ribbon_tail, rot=(0, 0, -8), pivot=(-0.7, 15.3, 3.0))
    skirt(m, 12.9, 8.0, 4.35, 2.4)
    # ---- 腕 (腕 = x -8..-4, y 12..24, z ±2)。ふくらんだ肩・袖・広がる袖口。手 (下の 1.5) は自分のスキンが見える
    m.pair("armR", (-8.55, 19.4, -2.55), (5.1, 5.0, 5.1), puff)
    m.pair("armR", (-8.6, 19.0, -2.6), (5.2, 0.55, 5.2), ribbon)
    m.pair("armR", (-8.35, 16.4, -2.35), (4.7, 2.7, 4.7), black)
    m.pair("armR", (-8.7, 14.2, -2.7), (5.4, 2.6, 5.4), cuff)
    m.pair("armR", (-8.85, 13.45, -2.85), (5.7, 0.85, 5.7), lace("#efe9f3", "#c3b8d0"), hide=("u", "d"))
    # ---- 帽子 (頭 = 8、重ね着の層で 9)
    witch_hat(m, 32.75, 9.1, 17.6, -5.0)
    skulls(m)
    return m


if __name__ == "__main__":
    model = build_outfit()
    dst = ROOT / "docs/review/skins/full" / ID
    data, atlas, glow, faces = export(model, dst, "skin_full/" + ID)
    sheet(model, dst / "preview.png", views=((15, 10), (150, 10), (-70, 8)), scale=20, size=(620, 960))
    print(ID, data["triangle_count"], "triangles", data["texture_size"], "atlas")
    if "install" in sys.argv:
        install(model, ID, dst)
        print("installed")
