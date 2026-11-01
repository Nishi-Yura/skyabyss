"""スキン「魔女」(docs/design/55。顔は着ている人のスキン。黒紫の長い髪、ゴシックのドレス、折れた三角帽、回る髑髏と浮かぶ魔導書)。

python tools/rough/skinbox/skins/witch.py [install]   → docs/review/skins/full/witch/ に見本の絵と mesh.json・atlas.png (install で mod の中へ)
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from kit import Model  # noqa: E402
from paint import metal, C, mix, blank, out, cloth, skin, hair, lace, clear, sprite, stamp, circle_alpha, edge, shade_rows, rnd  # noqa: E402

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
def strip_panel(outer, lining=True):
    """プリーツの一枚 (細い帯)。外の面は黒に紫の裾の帯とレースの波、内は紫の裏地。線の描き込みは要らない (段が本当にある)。"""
    lin = cloth(PURPLE["dark"], PURPLE["base"], "#2a0f3c", edges=False)

    def over(img, face, W, H, box):
        band_h = max(3, H // 8)
        img[H - band_h - 4:H - 4, :, :3] = shade_rows(band_h, C(PURPLE["light"]), C(PURPLE["base"]), C(PURPLE["dark"]))[:, None, :]
        img[H - band_h - 5, :, :3] = C(CRIMSON["base"])
        img[H - 4:H, :, :3] = C("#ece6f1")
        for x in range(W):
            if x % 6 in (0, 5):
                img[H - 1, x, 3] = 0
            if x % 6 == 0:
                img[H - 2, x, 3] = 0
        return img
    outer_p = cloth(BLACK["base"], BLACK["light"], BLACK["dark"], BLACK["line"], over=over)

    def p(face, W, H, box):
        if face == outer:
            return outer_p(face, W, H, box)
        if face in "nsew":
            return lin(face, W, H, box) if lining else outer_p(outer, W, H, box)
        return cloth(BLACK["dark"], edges=False)(face, W, H, box)
    return p


def lace_strip(face, W, H, box):
    """下のレース (白、穴の模様、下の縁は波)。"""
    if face in "ud":
        return out(blank(W, H))
    img = blank(W, H, C("#f0eaf4"))
    img[..., :3] = shade_rows(H, C("#ffffff"), C("#ece5f1"), C("#bfb3cc"), 0.2, 0.55)[:, None, :]
    for y in range(2, H - 4, 4):
        for x in range((y // 4) % 2 * 3, W, 6):
            img[y:y + 2, x:x + 2, :3] = C("#c9bed6")
    for x in range(W):
        if x % 6 in (0, 1, 5):
            img[H - 1, x, 3] = 0
        if x % 6 == 0:
            img[H - 2:H, x, 3] = 0
    return out(img)


def corset_plain(face, W, H, box):
    """コルセット: 前の紅の帯 (編み上げは立体の紐で上に重ねる)、骨の線、縁は紫。"""
    img = cloth(BLACK["base"], BLACK["light"], BLACK["dark"], BLACK["line"])(face, W, H, box).astype(float)
    if face in "ns":
        cx, pw = W // 2, W // 5
        img[:, cx - pw:cx + pw, :3] = shade_rows(H, C(CRIMSON["light"]), C(CRIMSON["base"]), C(CRIMSON["dark"]))[:, None, :]
        for x in (cx - pw - 1, cx + pw):
            img[:, x, :3] = C(PURPLE["base"])
        for x in (W // 6, W - W // 6):
            img[:, x, :3] = mix(img[:, x, :3], C(PURPLE["dark"]), 0.7)
    img[0, :, :3] = C(PURPLE["light"])
    img[-1, :, :3] = C(PURPLE["dark"])
    return out(img)


def silver(face, W, H, box):
    return metal(SILVER["base"], SILVER["light"], SILVER["dark"])(face, W, H, box)


def cord(face, W, H, box):
    return cloth("#141018", "#3a3048", "#08060a", edges=False)(face, W, H, box)


def book_cover(face, W, H, box):
    """魔導書の表紙: 紫の革、銀の角金具、光る紋。"""
    img = cloth("#3a1650", "#5c2a78", "#1e0a2a", "#12061a")(face, W, H, box).astype(float)
    glow = np.zeros((H, W), bool)
    if face in "ew" and W >= 8 and H >= 8:
        for (y, x) in ((0, 0), (0, W - 3), (H - 3, 0), (H - 3, W - 3)):
            img[y:y + 3, x:x + 3, :3] = C(SILVER["base"])
        yy, xx = np.mgrid[0:H, 0:W]
        r = np.hypot(xx + 0.5 - W / 2, yy + 0.5 - H / 2)
        ring = (r < min(W, H) * 0.3) & (r > min(W, H) * 0.3 - 1.2)
        img[ring, :3] = C(GLOW)
        glow |= ring
        cx, cy = W // 2, H // 2
        for k in range(-2, 3):
            img[cy + k, cx, :3] = C(GLOW2)
            img[cy, cx + k, :3] = C(GLOW2)
            glow[cy + k, cx] = glow[cy, cx + k] = True
    return out(img), glow


def pages(face, W, H, box):
    img = cloth("#efe4c8", "#fff8e6", "#c9b88f", edges=False)(face, W, H, box).astype(float)
    if face in "nsud":
        for y in range(0, H, 2):
            img[y, :, :3] = mix(img[y, :, :3], C("#bfae86"), 0.4)
    return out(img)


def teeth(face, W, H, box):
    img = cloth(BONE["base"], BONE["light"], BONE["dark"], edges=False)(face, W, H, box).astype(float)
    if face == "n":
        for x in range(0, W, 3):
            img[:, x, :3] = C("#6a5c48")
    return out(img)


# ---------------------------------------------------------------- 組み立て (素体の上に重ねる。docs/design/55)
def witch_hat(m, y0=32.75, base_w=8.8, brim_w=16.6, side_x=-4.95):
    """帽子: 二枚重ねの広いつば、16 段の円すいが後ろへ折れて垂れる (上の段は揺れる)、紅の帯と立体の銀の留め金、横のリボンと三日月。"""
    TILT = 6
    rt = (TILT, 0, 0)
    pv = (0, y0, 0)
    m.box("head", (-brim_w / 2, y0 - 0.2, -brim_w / 2), (brim_w, 0.35, brim_w), brim, rot=rt, pivot=pv)
    m.box("head", (-brim_w / 2 + 0.3, y0 - 0.45, -brim_w / 2 + 0.3), (brim_w - 0.6, 0.25, brim_w - 0.6), brim, rot=rt, pivot=pv)
    m.box("head", (-(base_w + 0.3) / 2, y0, -(base_w + 0.3) / 2), (base_w + 0.3, 1.15, base_w + 0.3), ribbon, rot=rt, pivot=pv)
    # 留め金 (枠 4 本と爪)
    bz = -(base_w + 0.3) / 2 - 0.2
    for (x, y, w, h) in ((-0.8, y0 + 0.05, 1.6, 0.25), (-0.8, y0 + 0.9, 1.6, 0.25), (-0.8, y0 + 0.05, 0.25, 1.1), (0.55, y0 + 0.05, 0.25, 1.1), (-0.1, y0 + 0.3, 0.2, 0.6)):
        m.box("head", (x, y, bz), (w, h, 0.25), silver, rot=rt, pivot=pv)
    pos = np.array([0.0, y0, 0.0])
    n = 16
    ds = 10.5 / n
    parent = "head"
    for i in range(n):
        s = i / (n - 1)
        ang = TILT + 125 * s ** 2.2
        w = (base_w - 0.9) * (1 - s) ** 1.1 + 0.9
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
    m.box("head", (x, y0 + 0.15, -0.9), (0.7, 1.3, 1.3), ribbon)
    m.box("head", (x - 0.2, y0 + 0.05, -3.4), (0.55, 1.9, 2.5), ribbon, rot=(22, 0, 0), pivot=(x, y0 + 0.8, -0.9))
    m.box("head", (x - 0.2, y0 + 0.05, 0.3), (0.55, 1.9, 2.5), ribbon, rot=(-22, 0, 0), pivot=(x, y0 + 0.8, 0.3))
    m.bone("cloth_charm", "head", (x - 0.1, y0 + 0.2, -0.3))
    m.box("cloth_charm", (x - 0.15, y0 - 2.2, -0.35), (0.12, 2.4, 0.12), silver, pivot=(x - 0.1, y0 + 0.2, -0.3))
    m.box("cloth_charm", (x - 0.25, y0 - 4.4, -1.3), (0.3, 2.4, 2.2), moon, pivot=(x - 0.1, y0 + 0.2, -0.3))


def pleated_skirt(m, Y0=14.2, L=8.2, rx=3.55, rz=2.25, tilt=16):
    """プリーツのスカート: 腰のまわりに 8 枚 (前・後ろ・横・斜め 4)、一枚は細い帯 4 本を 0.35 ずつ前後にずらした本当のひだ。下に白いレースの輪。"""
    panels = [  # 骨, 付け根 (x, z), 外の向きの yaw (前 = 0), 幅
        ("skirt_f", (0, -rz), 0, 4.2), ("skirt_b", (0, rz), 180, 4.2),
        ("skirt_R", (-rx, 0), 90, 3.4), ("skirt_L", (rx, 0), -90, 3.4),
        ("skirt_fR", (-rx * 0.78, -rz * 0.78), 45, 3.0), ("skirt_fL", (rx * 0.78, -rz * 0.78), -45, 3.0),
        ("skirt_bR", (-rx * 0.78, rz * 0.78), 135, 3.0), ("skirt_bL", (rx * 0.78, rz * 0.78), -135, 3.0),
    ]
    for bn, (px, pz), yw, w in panels:
        m.bone(bn, "body", (px, Y0, pz))
        n = 4
        sw = w / n
        for k in range(n):
            off = 0.35 if k % 2 else 0.0
            x0 = px - w / 2 + sw * k
            m.box(bn, (x0 - 0.05, Y0 - L, pz - 0.15 - off), (sw + 0.1, L, 0.3), strip_panel("n"), rot=(tilt, 0, 0), pivot=(px, Y0, pz), yaw=yw)
        # 下のレース (少し内、少し長く、ひだなし)
        m.box(bn, (px - w / 2 + 0.2, Y0 - L - 0.8, pz + 0.2), (w - 0.4, L + 0.4, 0.25), lace_strip, rot=(tilt * 0.8, 0, 0), pivot=(px, Y0, pz), yaw=yw)


def bow(m, bone, cx, cy, z, size=1.0, tails=None, tail_bone=None, flip_z=False):
    """立体のリボン: 結び目・左右の輪 (上下 2 枚と端)・垂れ。z = 前の面 (flip_z なら後ろ向き)。"""
    s = size
    d = -1 if not flip_z else 1
    zz = z if not flip_z else z
    m.box(bone, (cx - 0.45 * s, cy - 0.4 * s, zz - (0.5 * s if not flip_z else 0)), (0.9 * s, 0.8 * s, 0.5 * s), ribbon)
    for side in (-1, 1):
        x_end = cx + side * 2.0 * s
        for (dy, r) in ((0.25, 14), (-0.25, -14)):
            x0 = min(cx + side * 0.4 * s, x_end)
            m.box(bone, (x0, cy + dy * s - 0.25 * s, zz - (0.4 * s if not flip_z else 0)), (1.6 * s, 0.5 * s, 0.4 * s), ribbon,
                  rot=(0, 0, r * side), pivot=(cx, cy, zz))
        m.box(bone, (x_end - 0.2 * s if side > 0 else x_end - 0.2 * s, cy - 0.45 * s, zz - (0.4 * s if not flip_z else 0)), (0.4 * s, 0.9 * s, 0.4 * s), ribbon)
    if tails:
        L = tails
        tb = tail_bone or bone
        for side in (-1, 1):
            m.box(tb, (cx + side * 0.35 * s - 0.35 * s, cy - L, zz - (0.2 if not flip_z else 0)), (0.7 * s, L, 0.2), ribbon_tail,
                  rot=(0, 0, -10 * side), pivot=(cx, cy, zz))


def skull3(m, bone, c):
    """髑髏 (細かく): 頭の丸み 2 段、顔 (光る目のくぼみ)、頬、あご、歯。"""
    cx, cy, cz = c
    m.box(bone, (cx - 1.35, cy - 0.3, cz - 1.3), (2.7, 1.5, 2.7), cloth(BONE["base"], BONE["light"], BONE["dark"], "#8d7f66"))
    m.box(bone, (cx - 1.0, cy + 1.15, cz - 1.0), (2.0, 0.45, 2.0), cloth(BONE["base"], BONE["light"], BONE["dark"], "#8d7f66"))
    m.box(bone, (cx - 1.15, cy - 1.1, cz - 1.45), (2.3, 0.9, 2.3), skull_top)
    m.box(bone, (cx - 0.85, cy - 1.75, cz - 1.25), (1.7, 0.6, 1.6), jaw)
    m.box(bone, (cx - 0.75, cy - 1.3, cz - 1.5), (1.5, 0.3, 0.2), teeth)


def build():
    from body import base, HairPaint, hair_cap, bangs, side_locks, back_hair
    m = Model("skin_full/" + ID)
    base(m, own=True, under=BLACK["base"])
    # ---- 髪 (黒紫の長い髪。前髪 3 層、横髪 2 段、背中まで 3 層)
    hp = HairPaint("#30274a", "#140f22", "#54457a", "#b9a6ee", seed=7)
    hair_cap(m, hp, back_bottom=24.5)
    bangs(m, hp, length=4.4)
    side_locks(m, hp, length=9.5)
    back_hair(m, hp, bottom=12.5)
    # ---- 胴: 胴着、コルセット (立体の編み上げと銀の鳩目)、細い帯と留め金
    m.box("body", (-3.6, 18.2, -2.1), (7.2, 5.3, 4.2), bodice)
    m.box("body", (-3.2, 14.4, -1.95), (6.4, 4.0, 3.9), corset_plain)
    for i in range(5):
        y = 14.85 + 0.7 * i
        for side in (-1, 1):
            m.box("body", (side * 0.55 - 0.12, y - 0.12, -2.12), (0.24, 0.24, 0.2), silver)
            m.box("body", (-0.62, y - 0.08, -2.15), (1.24, 0.16, 0.14), cord, rot=(0, 0, 30 * side), pivot=(0, y, -2.08))
    m.box("body", (-3.4, 14.0, -2.12), (6.8, 0.55, 4.24), cloth(BLACK["dark"], BLACK["light"], "#08060a", edges=False))
    for (x, y, w, h) in ((-0.55, 13.92, 1.1, 0.18), (-0.55, 14.47, 1.1, 0.18), (-0.55, 13.92, 0.18, 0.73), (0.37, 13.92, 0.18, 0.73)):
        m.box("body", (x, y, -2.3), (w, h, 0.2), silver)
    # 襟 (白い丸襟の二枚と後ろ)、首のリボンと宝石、胸のひだ飾り 3 段、胸の結び目
    for side in (-1, 1):
        x0 = -2.9 if side < 0 else 0.3
        m.box("body", (x0, 22.05, -2.42), (2.6, 1.35, 0.28), collar_flap("n"), rot=(0, 0, -7 * side), pivot=(0, 23.4, -2.3))
        m.box("body", (x0 + 0.3, 21.75, -2.42), (2.0, 0.35, 0.28), collar_flap("n"), rot=(0, 0, -7 * side), pivot=(0, 23.4, -2.3))
    m.box("body", (-2.9, 22.1, 1.95), (5.8, 1.5, 0.3), collar_flap("s"), rot=(16, 0, 0), pivot=(0, 23.5, 2.0))
    m.box("body", (-1.15, 23.55, -1.15), (2.3, 0.5, 2.3), ribbon)
    m.box("body", (-0.3, 23.45, -1.38), (0.6, 0.65, 0.25), gem("#c0306a", "#ff9ec0", "#6a0a30"))
    for k in range(3):
        m.box("body", (-0.95 - 0.12 * k, 21.4 - 1.2 * k, -2.4 - 0.12 * k), (1.9 + 0.24 * k, 1.35, 0.3), jabot)
    bow(m, "body", 0, 22.2, -2.45, size=0.75)
    m.pair_bone("ribbon_chest_R", "body", (-0.3, 21.9, -2.7))
    m.pair("ribbon_chest_R", (-0.95, 19.4, -2.7), (0.55, 2.5, 0.18), ribbon_tail, rot=(0, 0, -12), pivot=(-0.3, 21.9, -2.7))
    # 腰の後ろの大きなリボンと長い垂れ (揺れる)
    bow(m, "body", 0, 15.2, 1.95, size=1.25, flip_z=True)
    m.pair_bone("ribbon_back_R", "body", (-0.5, 15.0, 2.3))
    m.pair("ribbon_back_R", (-1.6, 6.6, 2.25), (1.1, 8.4, 0.22), ribbon_tail, rot=(0, 0, -7), pivot=(-0.5, 15.0, 2.3))
    pleated_skirt(m)
    # ---- 腕: 細い袖、三段でふくらむ肩、広がる袖口と紫の裏地、立体のレースの縁
    m.pair("armR", (-6.95, 17.3, -1.45), (2.9, 5.0, 2.9), cloth(**BLACK))
    m.pair("armR", (-7.25, 21.4, -1.75), (3.5, 1.9, 3.5), puff)
    m.pair("armR", (-7.45, 19.95, -1.95), (3.9, 1.5, 3.9), puff)
    m.pair("armR", (-7.15, 19.15, -1.65), (3.3, 0.85, 3.3), puff)
    m.pair("armR", (-7.2, 18.85, -1.7), (3.4, 0.35, 3.4), ribbon)
    m.pair("armR", (-6.85, 15.1, -1.35), (2.7, 2.3, 2.7), cloth(**BLACK))
    m.pair("armR", (-7.2, 13.4, -1.7), (3.4, 1.8, 3.4), cuff)
    m.pair("armR", (-7.55, 12.65, -2.05), (4.1, 0.85, 4.1), cuff)
    m.pair("armR", (-7.65, 12.0, -2.15), (4.3, 0.7, 4.3), lace_strip, hide=("u", "d"))
    # ---- 脚: ニーハイ (上にレースの縁と紅の小さなリボン)、厚底の編み上げ長靴 (帯 2 本と銀の留め金、前の紐)
    m.pair("legR", (-3.55, 6.4, -1.75), (3.3, 4.9, 3.5), stocking)
    m.pair("legR", (-3.72, 10.7, -1.92), (3.64, 0.75, 3.84), lace_strip, hide=("u", "d"))
    m.pair("legR", (-3.85, 10.6, -0.35), (0.25, 0.6, 0.7), ribbon)
    m.pair("legR", (-3.65, 1.2, -1.85), (3.5, 5.3, 3.7), boot)
    m.pair("legR", (-3.8, 6.2, -2.0), (3.8, 0.5, 4.0), cloth("#141018", "#3a3048", "#08060a"))
    for y in (2.3, 4.4):
        m.pair("legR", (-3.75, y, -1.95), (3.7, 0.45, 3.9), cloth(PURPLE["dark"], PURPLE["base"], "#2a0f3c"))
        m.pair("legR", (-3.95, y - 0.1, -0.35), (0.25, 0.65, 0.7), silver)
    for i in range(5):
        y = 1.6 + 0.9 * i
        m.pair("legR", (-2.65, y, -1.98), (1.1, 0.14, 0.14), cord, rot=(0, 0, 25 if i % 2 else -25), pivot=(-2.1, y, -1.9))
    m.pair("legR", (-3.65, 0.55, -3.0), (3.5, 1.4, 4.8), boot)
    m.pair("legR", (-3.4, 0.55, -3.35), (3.0, 1.05, 0.4), boot)
    m.pair("legR", (-3.8, 0.0, -3.45), (3.8, 0.6, 5.4), sole)
    # ---- 帽子
    witch_hat(m)
    # ---- 浮かぶもの: 髑髏 3 つ (回り、ひとつずつ上下)、魔導書 (横で浮いて揺れる)
    m.bone("orbit_skulls", None, (0, 21.0, 0))
    for i, c in enumerate(((-9.4, 25.0, -1.5), (9.0, 19.5, 2.0), (1.5, 28.5, 9.0))):
        bn = m.bone(f"float_skull{i + 1}", "orbit_skulls", c)
        skull3(m, bn, c)
    bx, by, bz = 8.6, 15.5, -3.0
    m.bone("float_book", "body", (bx, by, bz))
    m.box("float_book", (bx - 0.6, by - 1.6, bz - 1.25), (1.2, 3.2, 2.5), pages, rot=(0, 30, 8), pivot=(bx, by, bz))
    for side in (-1, 1):
        m.box("float_book", (bx - 0.6 + (0.95 if side > 0 else -0.25), by - 1.75, bz - 1.35), (0.3, 3.5, 2.7), book_cover, rot=(0, 30, 8), pivot=(bx, by, bz))
    m.box("float_book", (bx - 0.75, by - 0.3, bz - 1.55), (1.5, 0.6, 0.3), silver, rot=(0, 30, 8), pivot=(bx, by, bz))
    return m


if __name__ == "__main__":
    from kit import main
    main(ID, build, sys.argv)
