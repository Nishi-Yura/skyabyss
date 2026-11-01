"""スキン「魔女」(docs/design/55。顔は着ている人のスキン)。
紫の長い髪 (先細りの束)、黒いドレスに紅の胸当てと紫のスカート、白いふくらんだ袖と白い靴下、金の金具、折れた三角帽、回る髑髏と浮かぶ魔導書。
塗りは明・中・影・濃い影の 4 段 (paint.cel / body.HairCel)。形は大きく少なく、細かすぎる飾りは描かない。

python tools/rough/skinbox/skins/witch.py [install]   → docs/review/skins/full/witch/ に見本の絵と mesh.json・atlas.png (install で mod の中へ)
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from kit import Model, T  # noqa: E402
from paint import C, mix, blank, out, cel, band  # noqa: E402

ID = "witch"

# ---------------------------------------------------------------- 色 (4 段: 中・明・影・濃い影)
BLACK = cel("#2c2438", "#5a4a70", "#1c1626", "#0e0a14")
CRIMSON = cel("#c01e4a", "#ff6a88", "#801030", "#4a0818")
WHITE = cel("#f4eef6", "#ffffff", "#c8bed8", "#8e82a6")
GOLD = cel("#dcaa3c", "#fff0a0", "#9a6a1a", "#5a3a0a")
HAT = cel("#2e2440", "#5c4a7a", "#1c1528", "#0c0812", rim=False, ao=False, outline=False, noise=0.015)
BONE = cel("#ece2cc", "#fffaf0", "#b8a888", "#7a6a50")
GLOW = "#c45cff"
GLOW2 = "#f2d2ff"


# ---------------------------------------------------------------- 描き方
def skirt_panel(face, W, H, box):
    """スカートの一枚: 紫に、描いたひだ (細い影と光の線)、裾の紅の帯。"""
    def over(img, face, W, H, box):
        if face not in "nsew":
            return img
        # ひだ: 1.5px ごとに影の線と光の線を半画素ずつ
        step = max(3, int(1.5 * T))
        for x in range(0, W, step):
            img[:, x, :3] = mix(img[:, x, :3], C("#1e1034"), 0.5)
            if x + 1 < W:
                img[:, x + 1, :3] = mix(img[:, x + 1, :3], C("#9a72c8"), 0.3)
        hb = max(1, T // 2)
        band(img, H - 3 * hb, H - hb, "#c01e4a", hi="#ff6a88")
        band(img, H - hb, H, "#4a0818")
        return img
    return cel("#4c2e72", "#7a52a8", "#331e52", "#1e1034", over=over)(face, W, H, box)


def lace(face, W, H, box):
    """白いレース: 下の縁は波、少しの穴。"""
    if face in "ud":
        return out(blank(W, H))

    def over(img, face, W, H, box):
        # 下の縁の波 (半画素ごとに抜く)
        for x in range(W):
            if x % 3 == 0:
                img[H - 1, x, 3] = 0
        return img
    return cel("#f4eef6", "#ffffff", "#c8bed8", "#8e82a6", over=over, outline=False, ao=False)(face, W, H, box)


def collar(face, W, H, box):
    """白い丸襟: 縁に紅の線、角は丸く抜く。"""
    def over(img, face, W, H, box):
        if face in "nsew":
            band(img, H - 1, H, "#c01e4a")
            for (cy, cx) in ((H - 1, 0), (H - 1, W - 1)):
                img[cy, cx, 3] = 0
        return img
    return cel("#f4eef6", "#ffffff", "#c8bed8", "#8e82a6", over=over)(face, W, H, box)


def ribbon_tail(face, W, H, box):
    """リボンの垂れ: 先は燕尾に抜く。"""
    def over(img, face, W, H, box):
        if face in "nsew" and W >= 3:
            for k in range(min(T, H)):
                for x in range(W):
                    if abs(x - (W - 1) / 2) < (T - k) * W / (2 * T):
                        img[H - 1 - k, x, 3] = 0
        return img
    return cel("#c01e4a", "#ff6a88", "#801030", "#4a0818", over=over)(face, W, H, box)


def gem(face, W, H, box):
    img = blank(W, H, C("#d8306a"))
    if W >= 2 and H >= 2:
        img[: H // 2, : W // 2, :3] = C("#ff9ec0")
        img[H - 1, :, :3] = C("#6a0a30")
    return out(img), np.ones((H, W), bool)


def brim(face, W, H, box):
    """帽子のつば (上から見て丸)。上は黒紫で外へ明るい輪、下は紫。"""
    if face not in "ud":
        return out(blank(W, H))
    yy, xx = np.mgrid[0:H, 0:W]
    r = np.hypot((xx + 0.5) / W * 2 - 1, (yy + 0.5) / H * 2 - 1)
    img = blank(W, H, C("#2e2440"))
    if face == "u":
        img[..., :3] = np.where((r > 0.62)[..., None], C("#3e3256"), C("#2e2440"))
        img[(r > 0.9), :3] = C("#5c4a7a")
        img[(r > 0.84) & (r < 0.87), :3] = C("#1c1528")
    else:
        img[..., :3] = np.where((r > 0.7)[..., None], C("#6a3a9a"), C("#4c2e72"))
    img[r > 1.0, 3] = 0
    return out(img)


def moon(face, W, H, box):
    """三日月の飾り (横の面だけ)。"""
    img = blank(W, H, C("#e9e4ff"))
    if face in "ew":
        yy, xx = np.mgrid[0:H, 0:W]
        u = (xx + 0.5) / W * 2 - 1
        v = (yy + 0.5) / H * 2 - 1
        m = (u * u + v * v <= 1.0) & ~((u - 0.45) ** 2 + (v + 0.15) ** 2 <= 0.72)
        img[..., :3] = np.where((v < 0)[..., None], C("#ffffff"), C("#b8aee8"))
        img[~m, 3] = 0
        if face == "e":
            return out(img[:, ::-1]), m[:, ::-1]
        return out(img), m
    return out(blank(W, H)), None


def skull_face(face, W, H, box):
    """髑髏の顔の段: 前の面に光る目のくぼみと鼻。"""
    img = BONE(face, W, H, box).astype(float)
    glow = np.zeros((H, W), bool)
    if face == "n":
        ew = max(1, W // 3)
        for x0 in (max(0, W // 2 - ew - 1), W // 2 + 1):
            img[:H - 1 if H > 1 else H, x0:x0 + ew, :3] = C(GLOW)
            glow[:H - 1 if H > 1 else H, x0:x0 + ew] = True
        img[H - 1:, W // 2:W // 2 + 1, :3] = C("#3a2a44")
    return out(img), glow


def teeth(face, W, H, box):
    img = BONE(face, W, H, box).astype(float)
    if face == "n":
        for x in range(0, W, 2):
            img[:, x, :3] = C("#5a4c3a")
    return out(img)


def book_cover(face, W, H, box):
    """魔導書の表紙: 紫の革、金の角、光る紋。"""
    img = cel("#3a1650", "#6a3290", "#24103a", "#12061a")(face, W, H, box).astype(float)
    glow = np.zeros((H, W), bool)
    if face in "ew" and W >= 4 and H >= 4:
        for (y, x) in ((0, 0), (0, W - 1), (H - 1, 0), (H - 1, W - 1)):
            img[y:y + 1, x:x + 1, :3] = C("#dcaa3c")
        yy, xx = np.mgrid[0:H, 0:W]
        r = np.hypot(xx + 0.5 - W / 2, yy + 0.5 - H / 2)
        ring = (r < min(W, H) * 0.34) & (r > min(W, H) * 0.34 - 1)
        img[ring, :3] = C(GLOW)
        glow |= ring
        cx, cy = W // 2, H // 2
        img[cy, cx, :3] = C(GLOW2)
        glow[cy, cx] = True
    return out(img), glow


def pages(face, W, H, box):
    img = cel("#efe4c8", "#fff8e6", "#c9b88f", "#8a7a58")(face, W, H, box).astype(float)
    if face in "nsud":
        for y in range(0, H, 2):
            img[y, :, :3] = mix(img[y, :, :3], C("#bfae86"), 0.25)
    return out(img)


def cord(face, W, H, box):
    return out(blank(W, H, C("#14101a")))


# ---------------------------------------------------------------- 組み立ての部品
def bow(m, bone, cx, cy, z, size=1.0, back=False):
    """立体のリボン: 結び目 (真ん中に光る宝石)・左右の輪。z = 前の面 (back なら後ろの面)。"""
    s = size
    zf = z - 0.5 * s if not back else z
    m.box(bone, (cx - 0.45 * s, cy - 0.45 * s, zf), (0.9 * s, 0.9 * s, 0.5 * s), CRIMSON, rounded=False)
    for side in (-1, 1):
        x0 = cx + (0.35 * s if side > 0 else -2.05 * s)
        m.box(bone, (x0, cy - 0.15 * s, zf + 0.05), (1.7 * s, 0.75 * s, 0.4 * s), CRIMSON, rot=(0, 0, 16 * side), pivot=(cx, cy, z), rounded=False)
        m.box(bone, (x0, cy - 0.75 * s, zf + 0.05), (1.7 * s, 0.7 * s, 0.4 * s), CRIMSON, rot=(0, 0, -12 * side), pivot=(cx, cy, z), rounded=False)
    m.box(bone, (cx - 0.25 * s, cy - 0.25 * s, zf - 0.1 if not back else zf + 0.5 * s), (0.5 * s, 0.5 * s, 0.15), gem, rounded=False)


def witch_hat(m, y0=33.6, base_w=9.4, brim_w=17.0, side_x=-5.2):
    """帽子: 厚みのある二枚のつば、9 段の円すい (角は八角) が後ろへ折れて垂れる (上の段は揺れる)、紅の帯と金の留め金、横のリボンと三日月。"""
    TILT = 6
    rt = (TILT, 0, 0)
    pv = (0, y0, 0)
    m.box("head", (-brim_w / 2, y0 - 0.2, -brim_w / 2), (brim_w, 0.45, brim_w), brim, rot=rt, pivot=pv, rounded=False)
    m.box("head", (-brim_w / 2 + 0.4, y0 - 0.55, -brim_w / 2 + 0.4), (brim_w - 0.8, 0.4, brim_w - 0.8), brim, rot=rt, pivot=pv, rounded=False)
    m.box("head", (-(base_w + 0.3) / 2, y0, -(base_w + 0.3) / 2), (base_w + 0.3, 1.3, base_w + 0.3), CRIMSON, rot=rt, pivot=pv)
    bz = -(base_w + 0.3) / 2 - 0.25
    for (x, y, w, h) in ((-1.0, y0 + 0.05, 2.0, 0.3), (-1.0, y0 + 1.0, 2.0, 0.3), (-1.0, y0 + 0.05, 0.3, 1.25), (0.7, y0 + 0.05, 0.3, 1.25)):
        m.box("head", (x, y, bz), (w, h, 0.3), GOLD, rot=rt, pivot=pv, rounded=False)
    pos = np.array([0.0, y0, 0.0])
    n = 9
    ds = 10.5 / n
    parent = "head"
    for i in range(n):
        s = i / (n - 1)
        ang = TILT + 120 * s ** 2.0
        w = (base_w - 1.2) * (1 - s) ** 1.05 + 1.2
        bn = parent
        if i == 4:
            bn = m.bone("cloth_hat1", "head", tuple(pos))
            parent = bn
        elif i == 7:
            bn = m.bone("cloth_hat2", "cloth_hat1", tuple(pos))
            parent = bn
        m.box(bn, (pos[0] - w / 2, pos[1], pos[2] - w / 2), (w, ds * 1.45, w), HAT, rot=(ang, 0, 0), pivot=tuple(pos))
        a = math.radians(ang)
        pos = pos + np.array([0, math.cos(a), math.sin(a)]) * ds
    x = side_x
    bow(m, "head", x, y0 + 0.75, -0.3, size=0.8)
    m.bone("cloth_charm", "head", (x - 0.1, y0 + 0.2, 0.6))
    m.box("cloth_charm", (x - 0.15, y0 - 2.2, 0.55), (0.15, 2.4, 0.15), GOLD, pivot=(x - 0.1, y0 + 0.2, 0.6), rounded=False)
    m.box("cloth_charm", (x - 0.3, y0 - 4.6, -0.5), (0.35, 2.6, 2.4), moon, pivot=(x - 0.1, y0 + 0.2, 0.6), rounded=False)


def skull(m, bone, c):
    """髑髏: 頭の丸み、光る目の段、あごと歯。"""
    cx, cy, cz = c
    m.box(bone, (cx - 1.4, cy - 0.2, cz - 1.35), (2.8, 1.6, 2.8), BONE)
    m.box(bone, (cx - 1.0, cy + 1.3, cz - 1.0), (2.0, 0.5, 2.0), BONE, rounded=False)
    m.box(bone, (cx - 1.2, cy - 1.15, cz - 1.5), (2.4, 1.0, 2.4), skull_face, rounded=False)
    m.box(bone, (cx - 0.9, cy - 1.8, cz - 1.3), (1.8, 0.65, 1.7), BONE, rounded=False)
    m.box(bone, (cx - 0.8, cy - 1.35, cz - 1.55), (1.6, 0.3, 0.2), teeth, rounded=False)


# ---------------------------------------------------------------- 組み立て
def build():
    from body import base, HairCel, hair_mass, bangs2, side_hair2, back_hair2
    m = Model("skin_full/" + ID)
    base(m, own=True, under="#2c2438")
    # ---- 髪 (紫。かさ・前髪 5 束・横髪 2 束ずつ・後ろ髪 5 束。束は先細りのキューブの鎖)
    hp = HairCel("#7a4fb4", "#b48ee0", "#4f2f80", "#2c1a4c", "#ece0ff")
    hair_mass(m, hp, back_bottom=24.0, shingles=False)
    bangs2(m, hp)
    side_hair2(m, hp, length=13.5)
    back_hair2(m, hp, bottom=11.5)
    # ---- 胴: 黒い胴着、紅の胸当て (コルセットの前) と黒い紐・金の鳩目、帯と金の留め金
    m.box("body", (-3.6, 18.4, -2.1), (7.2, 5.1, 4.2), BLACK)
    m.box("body", (-3.25, 14.3, -1.95), (6.5, 4.2, 3.9), BLACK)
    m.box("body", (-1.5, 14.3, -2.2), (3.0, 4.3, 0.35), CRIMSON, rounded=False)
    for i in range(4):
        y = 14.9 + 0.95 * i
        for side in (-1, 1):
            m.box("body", (side * 0.95 - 0.17, y - 0.17, -2.3), (0.34, 0.34, 0.2), GOLD, rounded=False)
            m.box("body", (-0.95, y - 0.1, -2.33), (1.9, 0.2, 0.16), cord, rot=(0, 0, 26 * side), pivot=(0, y + 0.45, -2.25), rounded=False)
    m.box("body", (-3.45, 13.8, -2.15), (6.9, 0.65, 4.3), BLACK)
    for (x, y, w, h) in ((-0.7, 13.7, 1.4, 0.25), (-0.7, 14.35, 1.4, 0.25), (-0.7, 13.7, 0.25, 0.9), (0.45, 13.7, 0.25, 0.9)):
        m.box("body", (x, y, -2.4), (w, h, 0.25), GOLD, rounded=False)
    # 白い丸襟 (前の二枚と後ろ)、首の紅のリボンと宝石、胸の白いひだ飾りと紅のリボン
    for side in (-1, 1):
        x0 = -3.0 if side < 0 else 0.3
        m.box("body", (x0, 21.9, -2.45), (2.7, 1.55, 0.3), collar, rot=(0, 0, -7 * side), pivot=(0, 23.4, -2.3), rounded=False)
    m.box("body", (-3.0, 22.0, 1.95), (6.0, 1.6, 0.3), collar, rot=(16, 0, 0), pivot=(0, 23.5, 2.0), rounded=False)
    m.box("body", (-1.15, 23.5, -1.15), (2.3, 0.55, 2.3), CRIMSON, rounded=False)
    m.box("body", (-0.3, 23.45, -1.4), (0.6, 0.65, 0.25), gem, rounded=False)
    for k in range(3):
        m.box("body", (-0.9 - 0.15 * k, 21.2 - 1.15 * k, -2.4 - 0.12 * k), (1.8 + 0.3 * k, 1.3, 0.3), lace, rounded=False)
    bow(m, "body", 0, 22.3, -2.45, size=0.8)
    # 腰の後ろの大きなリボンと長い垂れ (揺れる)、左の腰の紅の飾り帯と垂れ
    bow(m, "body", 0, 15.3, 1.95, size=1.3, back=True)
    m.pair_bone("ribbon_back_R", "body", (-0.5, 15.0, 2.3))
    m.pair("ribbon_back_R", (-1.6, 6.8, 2.25), (1.2, 8.2, 0.25), ribbon_tail, rot=(0, 0, -7), pivot=(-0.5, 15.0, 2.3))
    bow(m, "body", 3.2, 14.1, -2.3, size=0.75)
    m.bone("ribbon_sash", "body", (3.2, 13.9, -2.4))
    m.box("ribbon_sash", (2.8, 6.2, -2.55), (0.9, 7.8, 0.2), ribbon_tail, rot=(-8, 0, 6), pivot=(3.2, 13.9, -2.4), rounded=False)
    m.box("ribbon_sash", (3.5, 7.6, -2.5), (0.75, 6.4, 0.2), ribbon_tail, rot=(-8, 0, 16), pivot=(3.2, 13.9, -2.4), rounded=False)
    # ---- スカート: 腰のまわりに大きな板 12 枚 (外へ開き、ひだは描く)、下に白いレースの輪。一枚ごとに骨 (脚で開き、歩くとなびく)
    Y0, L, R, n = 14.2, 6.8, 3.0, 12
    for k in range(n):
        a = 360.0 * k / n
        rr = R * (1 + 0.14 * abs(math.sin(math.radians(2 * a))))
        px, pz = -math.sin(math.radians(a)) * rr, -math.cos(math.radians(a)) * rr
        w = 2 * math.pi * rr / n * 1.32
        bn = m.bone(f"skirt_{k}", "body", (px, Y0, pz))
        m.box(bn, (px - w / 2, Y0 - L, pz - 0.2), (w, L, 0.4), skirt_panel, rot=(18, 0, 0), pivot=(px, Y0, pz), yaw=a, rounded=False)
        m.box(bn, (px - w / 2 + 0.1, Y0 - L - 0.85, pz + 0.05), (w - 0.2, L + 0.3, 0.3), lace, rot=(15, 0, 0), pivot=(px, Y0, pz), yaw=a, rounded=False)
    # ---- 腕: 白いふくらんだ肩 (斜めの板の輪)、黒い腕、広がる黒い袖口と白いレース
    m.pair("armR", (-6.8, 14.4, -1.3), (2.6, 4.6, 2.6), BLACK)
    for bone, cx in (("armR", -5.5), ("armL", 5.5)):
        m.box(bone, (cx - 1.85, 19.1, -1.85), (3.7, 3.1, 3.7), WHITE)
        m.ring(bone, (cx, 0), 20.6, 3.0, 1.6, 8, 0.5, WHITE, tilt=24, overlap=1.45, phase=22.5)
        m.ring(bone, (cx, 0), 19.0, 1.9, 2.8, 8, 0.5, WHITE, tilt=-34, overlap=1.4, phase=22.5)
        m.ring(bone, (cx, 0), 18.7, 0.5, 1.8, 8, 0.45, CRIMSON, tilt=0, overlap=1.3, phase=22.5)
        m.ring(bone, (cx, 0), 12.9, 2.6, 1.5, 10, 0.45, BLACK, tilt=20, overlap=1.4)
        m.ring(bone, (cx, 0), 12.0, 1.0, 2.4, 10, 0.35, lace, tilt=34, overlap=1.35, phase=18)
    # ---- 脚: 白い長い靴下と紅のガーター、黒い厚底の長靴 (紅の帯 2 本と金の留め金)
    m.pair("legR", (-3.55, 4.0, -1.75), (3.3, 7.9, 3.5), WHITE)
    m.pair("legR", (-3.65, 10.4, -1.85), (3.5, 0.55, 3.7), CRIMSON)
    m.pair("legR", (-3.9, 10.25, -0.4), (0.3, 0.85, 0.8), CRIMSON)
    m.pair("legR", (-3.7, 0.9, -1.9), (3.6, 3.8, 3.8), BLACK)
    m.pair("legR", (-3.85, 4.3, -2.05), (3.9, 0.9, 4.1), BLACK)
    for y in (1.7, 3.2):
        m.pair("legR", (-3.8, y, -2.0), (3.8, 0.5, 4.0), CRIMSON)
        m.pair("legR", (-4.05, y - 0.15, -0.45), (0.3, 0.8, 0.9), GOLD)
    m.pair("legR", (-3.7, 0.5, -3.1), (3.6, 1.5, 5.0), BLACK)
    m.pair("legR", (-3.85, 0.0, -3.45), (3.9, 0.6, 5.5), cel("#141018", "#2e2638", "#0a080c", "#000000"))
    # ---- 帽子
    witch_hat(m)
    # ---- 浮かぶもの: 髑髏 3 つ (回り、ひとつずつ上下)、魔導書 (横で浮いて揺れる)
    m.bone("orbit_skulls", None, (0, 21.0, 0))
    for i, c in enumerate(((-9.4, 25.0, -1.5), (9.0, 19.5, 2.0), (1.5, 28.5, 9.0))):
        bn = m.bone(f"float_skull{i + 1}", "orbit_skulls", c)
        skull(m, bn, c)
    bx, by, bz = 8.6, 15.5, -3.0
    m.bone("float_book", "body", (bx, by, bz))
    m.box("float_book", (bx - 0.6, by - 1.6, bz - 1.25), (1.2, 3.2, 2.5), pages, rot=(0, 30, 8), pivot=(bx, by, bz), rounded=False)
    for side in (-1, 1):
        m.box("float_book", (bx - 0.6 + (0.95 if side > 0 else -0.25), by - 1.75, bz - 1.35), (0.3, 3.5, 2.7), book_cover, rot=(0, 30, 8),
              pivot=(bx, by, bz), rounded=False)
    m.box("float_book", (bx - 0.75, by - 0.3, bz - 1.55), (1.5, 0.6, 0.3), GOLD, rot=(0, 30, 8), pivot=(bx, by, bz), rounded=False)
    return m


if __name__ == "__main__":
    from kit import main
    main(ID, build, sys.argv)
