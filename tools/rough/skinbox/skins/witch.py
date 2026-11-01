"""体ごとのスキン「魔女」(ゴシックの魔女。浮かぶ髑髏が三つ回る)。

python tools/rough/skinbox/skins/witch.py [outfit|full] [install]
  outfit = 服だけ (自分のスキンの上に重ねる)、full = 見た目ごと替える。docs/review/skins/full/witch_<種類>/ に見本の絵と mesh.json・atlas.png
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
HAIR = dict(base="#2d2443", dark="#151022", light="#4d3d6c", hi="#a48fd6")
BLACK = dict(base="#2b2534", light="#463d54", dark="#16121c", line="#0e0b12")
PURPLE = dict(base="#6c2f90", light="#9a5cc4", dark="#43195f")
CRIMSON = dict(base="#a3143c", light="#d93a62", dark="#650a24")
SILVER = dict(base="#c9cdd9", light="#f4f6fb", dark="#7e8497")
BONE = dict(base="#ece2cc", light="#fff8ea", dark="#b5a78c")
GLOW = "#c45cff"
GLOW2 = "#f2d2ff"


# ---------------------------------------------------------------- 顔 (頭の前、36x36)
# 目 (画面の左の目。外の端 = 左)。11x14。上は太いまつげと外へ跳ねる線、瞳は上が暗く下が明るい、ハイライトは左上の大と右下の小
EYE = [
    "KK..KKKK...",
    ".KKKKKKKKK.",
    "..KKKKKKKKK",
    ".KDDDDDDDKK",
    ".DDHHDDDDDK",
    ".DHHHDDPDD.",
    ".DHHPPPPDM.",
    ".DMPPPPPMM.",
    ".MMPPPPPMM.",
    ".MMMPPPhML.",
    ".LMMMMMMLL.",
    ".LLGGLLGLL.",
    "..LGGGGGL..",
    "......kkk..",
]
EYE_PAL = {"K": "#1c1024", "k": "#7a4458", "D": "#4c0b2a", "M": "#a8183f", "L": "#e8486a", "G": "#ff9fb2", "P": "#2a0612", "H": "#ffffff", "h": "#ffd6de"}


def face_paint(face, W, H, box):
    if face != "n":
        return skin()(face, W, H, box)
    img = skin()(face, W, H, box).astype(float)
    eye = sprite(EYE, EYE_PAL)
    ey = 17
    stamp(img, eye, 4, ey)
    # もう片方は映すが、ハイライトは同じ左上に戻す
    e2 = eye[:, ::-1].copy()
    hl = np.array([[c in "Hh" for c in r] for r in EYE])
    hl_m = hl[:, ::-1]
    e2[hl_m, :3] = C(EYE_PAL["D"])
    e2[hl, :3] = eye[hl, :3]
    stamp(img, e2, 21, ey)
    # 頬の赤み (小さく、目の外の下)
    for (cx, cy) in ((4, 31), (28, 31)):
        for dx in range(4):
            img[cy, cx + dx, :3] = mix(img[cy, cx + dx, :3], C("#f09aa6"), 0.6)
        img[cy, cx + 1, :3] = C("#e47d92")
        img[cy, cx + 3, :3] = C("#e47d92")
    # 鼻と口 (小さく笑う)
    img[30, 18, :3] = C("#e7b8ab")
    for (y, x) in ((32, 16), (33, 17), (33, 18), (32, 19)):
        img[y, x, :3] = C("#8c3448")
    return out(img)


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


def collar(face, W, H, box):
    img = cloth(BLACK["base"], BLACK["light"], BLACK["dark"], BLACK["line"])(face, W, H, box).astype(float)
    if face in "nsew":
        img[0, :, :3] = C(PURPLE["light"])
        img[1, :, :3] = C(PURPLE["base"])
    return out(img)


def back_hair(face, W, H, box):
    img = hair(**HAIR, jag=0.12, jag_w=(3, 6), seed=7)(face, W, H, box)
    if face in "ns":
        # 真ん中を逆 V に割って、二つの房に
        cx = W / 2
        for y in range(H):
            t = (y - H * 0.62) / (H * 0.38)
            if t > 0:
                half = t * W * 0.18
                img[y, int(cx - half):int(np.ceil(cx + half)), 3] = 0
    return img


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


def collar_lace(face, W, H, box):
    """丸襟の花びら: 白いレース、外の縁は黒い線と波、角は丸く。"""
    if face not in "ud":
        return out(blank(W, H, C("#d9d0e2")))
    img = blank(W, H, C("#f3eef6") if face == "u" else C("#c9bfd6"))
    yy, xx = np.mgrid[0:H, 0:W]
    if face == "u":
        img[..., :3] = mix(C("#ffffff"), C("#d6cce0"), ((yy + 0.5) / H)[..., None] * 0.7)
        # 外の縁 (絵の上 = 体から遠い側) に黒い線
        img[1:3, :, :3] = C("#2a2133")
        img[3, :, :3] = C(PURPLE["base"])
        for y in range(5, H - 1, 3):
            for x in range((y // 3) % 2 * 2, W, 4):
                img[y, x, :3] = C("#c3b8d0")
    for x in range(W):
        if x % 4 in (0, 3):
            img[0, x, 3] = 0
    for (cy, cx) in ((0, 0), (0, W - 1), (1, 0), (1, W - 1), (0, 1), (0, W - 2)):
        img[cy, cx, 3] = 0
    return out(img)


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


# 小さな目 (画面の左の目。外の端 = 左)。7x6。上のまぶたの線、瞳は上が暗く下が明るい、ハイライト 1 画素
EYE_S = [
    ".KKKKKK",
    "KKDDDDK",
    "KDPPHDK",
    "KMPPMMK",
    ".LMMLL.",
    "..kkk..",
]


def small_face(face, W, H, box):
    if face != "n":
        return skin()(face, W, H, box)
    img = skin()(face, W, H, box).astype(float)
    eye = sprite(EYE_S, EYE_PAL)
    ey = 18
    stamp(img, eye, 5, ey)
    e2 = eye[:, ::-1].copy()
    hl = np.array([[c in "Hh" for c in r] for r in EYE_S])
    e2[hl[:, ::-1], :3] = C(EYE_PAL["P"])
    e2[hl, :3] = eye[hl, :3]
    stamp(img, e2, 20, ey)
    # 薄い頬と小さな口 (片方に白い牙)
    for x in (5, 6, 25, 26):
        img[ey + 7, x, :3] = mix(img[ey + 7, x, :3], C("#f2a0ad"), 0.5)
    img[ey + 9, 15, :3] = C("#8c3448")
    img[ey + 9, 16, :3] = C("#8c3448")
    img[ey + 10, 16, :3] = C("#ffffff")
    return out(img)


def build_full():
    """見た目ごと替える: 頭はバニラと同じ 8、顔は小さな目を低く。体は細身。"""
    m = Model("skin_full/" + ID)
    black = cloth(**BLACK)
    sk = skin()
    m.pair("legR", (-3.4, 10.4, -1.4), (2.8, 1.8, 2.8), sk, hide=("u",))
    m.pair("legR", (-3.45, 4.6, -1.45), (2.9, 5.9, 2.9), stocking)
    m.pair("legR", (-3.6, 9.9, -1.6), (3.2, 1.0, 3.2), lace("#efe9f3", "#c3b8d0"), hide=("u", "d"))
    m.pair("legR", (-3.62, 1.2, -1.62), (3.24, 3.8, 3.24), boot)
    m.pair("legR", (-3.7, 0.55, -2.7), (3.4, 1.6, 4.5), boot)
    m.pair("legR", (-3.78, 0.0, -2.78), (3.56, 0.6, 4.66), sole)
    m.box("body", (-3.0, 12.4, -1.7), (6.0, 5.8, 3.4), corset)
    m.box("body", (-3.3, 18.0, -1.9), (6.6, 5.5, 3.8), bodice)
    m.box("body", (-0.9, 23.3, -0.9), (1.8, 1.3, 1.8), sk)
    m.box("body", (-1.05, 23.6, -1.05), (2.1, 0.55, 2.1), ribbon)
    m.box("body", (-1.3, 18.4, -2.3), (2.6, 4.6, 0.45), jabot)
    m.box("body", (-0.6, 22.2, -2.6), (1.2, 1.1, 0.5), ribbon)
    m.pair("body", (-2.6, 21.8, -2.45), (2.0, 1.6, 0.4), ribbon, rot=(0, 0, -12), pivot=(-0.6, 22.6, -2.4))
    m.pair("body", (-1.1, 19.6, -2.5), (0.7, 2.6, 0.25), ribbon_tail, rot=(0, 0, -10), pivot=(-0.6, 22.2, -2.4))
    m.pair("body", (-3.35, 21.0, -2.2), (3.05, 2.7, 0.28), collar_flap("n"), rot=(0, 0, -6), pivot=(-0.3, 23.5, -2.1))
    m.pair("body", (-3.55, 21.5, -2.0), (0.28, 2.2, 4.0), collar_flap("w"))
    m.box("body", (-3.4, 21.3, 1.95), (6.8, 2.4, 0.28), collar_flap("s"))
    m.box("body", (-0.8, 15.0, 1.7), (1.6, 1.6, 0.8), ribbon)
    m.pair("body", (-4.0, 14.5, 1.8), (3.4, 2.6, 0.7), ribbon, rot=(0, 0, 12), pivot=(-0.8, 15.8, 2.1))
    m.pair_bone("ribbon_R", "body", (-0.6, 15.4, 2.3))
    m.pair("ribbon_R", (-1.8, 7.4, 2.0), (1.3, 8.0, 0.3), ribbon_tail, rot=(0, 0, -8), pivot=(-0.6, 15.4, 2.3))
    skirt(m, 13.6, 8.0, 3.0, 1.9)
    m.pair("armR", (-6.45, 15.6, -1.05), (2.1, 4.6, 2.1), black)
    m.pair("armR", (-6.95, 19.8, -1.5), (3.0, 3.8, 3.0), puff)
    m.pair("armR", (-7.0, 19.4, -1.55), (3.1, 0.55, 3.1), ribbon)
    m.pair("armR", (-7.1, 13.0, -1.7), (3.4, 2.9, 3.4), cuff)
    m.pair("armR", (-7.25, 12.3, -1.85), (3.7, 0.85, 3.7), lace("#efe9f3", "#c3b8d0"), hide=("u", "d"))
    m.pair("armR", (-6.2, 10.9, -0.8), (1.6, 2.4, 1.6), sk)
    # ---- 頭 (バニラと同じ 8。顔は小さな目を低く、前髪が額を深く覆う)
    m.box("head", (-4.0, 24.0, -4.0), (8.0, 8.0, 8.0), small_face)
    m.box("head", (-4.5, 31.2, -4.5), (9.0, 1.1, 9.1), hair(**HAIR))
    m.box("head", (-4.5, 24.4, 3.85), (9.0, 7.9, 0.9), hair(**HAIR, jag=0.12))
    m.pair("head", (-4.65, 24.8, -4.2), (0.75, 7.2, 8.4), hair(**HAIR, jag=0.22, jag_w=(4, 7)))
    m.box("head", (-4.45, 27.5, -4.7), (8.9, 4.2, 0.65), hair(**HAIR, jag=0.55, jag_w=(5, 8), seed=11), hide=("s", "u"))
    m.pair_bone("hair_lock_R", "head", (-3.9, 29.5, -3.0))
    m.pair("hair_lock_R", (-4.8, 18.8, -3.9), (1.6, 10.7, 1.6), hair(**HAIR, jag=0.08), rot=(0, 0, -3), pivot=(-3.9, 29.5, -3.0))
    m.bone("hair_back", "body", (0, 26.5, 4.7))
    m.box("hair_back", (-4.4, 12.8, 4.3), (8.8, 14.0, 1.0), back_hair, pivot=(0, 26.5, 4.7))
    m.box("hair_back", (-3.8, 15.5, 5.15), (7.6, 10.8, 0.6), hair(**HAIR, jag=0.3, jag_w=(4, 7), seed=3), pivot=(0, 26.5, 4.7), hide=("n",))
    m.pair_bone("hair_backside_R", "body", (-4.0, 26.5, 3.6))
    m.pair("hair_backside_R", (-5.0, 15.0, 2.9), (1.6, 11.0, 1.6), hair(**HAIR, jag=0.12), rot=(0, 0, -5), pivot=(-4.0, 26.5, 3.6))
    witch_hat(m, 31.8, 8.4, 16.4, -4.6)
    skulls(m)
    return m


if __name__ == "__main__":
    from kit import render
    kinds = [a for a in sys.argv[1:] if a in ("outfit", "full")] or ["outfit", "full"]
    for kind in kinds:
        model = build_outfit() if kind == "outfit" else build_full()
        dst = ROOT / "docs/review/skins/full" / f"{ID}_{kind}"
        data, atlas, glow, faces = export(model, dst, "skin_full/" + ID)
        sheet(model, dst / "preview.png", views=((15, 10), (150, 10), (-70, 8)), scale=20, size=(620, 960))
        if kind == "full":
            render([f for f in faces if f.bone == "head"], 0, 0, 60, (700, 700), center=(0, 28.5, 0)).save(dst / "preview_face.png")
        print(ID, kind, data["triangle_count"], "triangles", data["texture_size"], "atlas")
        if "install" in sys.argv:
            install(model, ID, dst)
            print("installed", kind)
