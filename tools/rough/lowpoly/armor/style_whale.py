"""岩鯨の甲殻 (最終)。様式 mix: A の量塊 (大きな石の殻・二段の肩・骨の胸板) + C の使い込み (縄・護符・ぼろ布・苔・欠け) + 控えめな光。
「かご」(平らな格子の布に細い棒) をやめ、体は石の面で覆い、骨は面の板で付ける。光は兜の視き穴・胸の核・膝の核だけ (+ 階級の縁の筋)。
階級で輪郭が変わる:
  通常 = 全面の兜 + 骨の胸板 + 二段の肩 + 太い腿当て + 重い靴
  レア = 肩に牙・兜に側頭の牙・背に背びれ 3 枚・足首に骨の鰭
  伝説 = 兜に背びれの冠 (3 枚) と後ろへ流れる 2 本の角・両肩に大きな牙・背の肋の弧と長いぼろ布・腿の外の鰭
材質: main = 石 / sec = 骨 / edge = 明るい骨 / cloth = 暗い石の下着 / cord = 縄 / acc = 苔 / trim = 階級の縁 / gem = 光 / dark。"""
from __future__ import annotations

from akit import *  # noqa: F401,F403
from akit import V, bx, ext, spk, crv, rr, shell, rot_x, rot_y, rot_z, ARM, LEG, BOOT


def gem(b, c, size=(1.4, 0.8, 0.5)):
    return bx(b, "gem", c, size, taper=(0.8, 0.8), skip=("b",))


def rag(b, c, w, length, rot=None, mat="cloth", th=0.5):
    """ぼろ布: 先が裂けた垂れ。c = 吊り元の中心。"""
    pts = [(-w, 0), (w, 0), (w * 0.9, -length * 0.55), (w * 0.35, -length), (-w * 0.2, -length * 0.62), (-w * 0.9, -length * 0.85)]
    return ext(b, mat, pts, th, c=c, rot=rot)


def charm(b, x, y, z, drop=2.2):
    f = bx(b, "cord", (x, y - drop / 2, z), (0.5, drop, 0.5))
    f += bx(b, "edge", (x, y - drop - 0.6, z), (1.2, 1.6, 0.9), taper=(0.6, 0.7))
    return f


def helmet(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    b = "armorHead"
    f = []
    rings = [rr(23.8, 4.9, -4.9, 4.9, cut=1.3), rr(28.4, 5.1, -5.1, 5.1, cut=1.3), rr(31.6, 4.8, -4.8, 4.9, cut=1.5), rr(33.6, 3.4, -3.4, 3.8, cut=1.5)]
    f += shell(b, "main", rings, caps=(False, "main"))
    f += bx(b, "sec", (0, 30.2, -5.2), (10.6, 1.6, 0.8), skip=("b", "+z"))              # 骨の眉庇
    f += bx(b, "dark", (0, 27.9, -5.2), (7.6, 1.8, 0.5), skip=("b", "+z"))              # 視き穴
    f += bx(b, "gem", (0, 27.9, -5.5), (6.0, 0.55, 0.2), skip=("b", "+z"))
    f += bx(b, "main", (0, 25.0, -5.2), (7.0, 2.6, 0.8), skip=("b", "+z"), taper=(0.9, 0.9))   # 顎当て
    f += bx(b, "cord", (0, 29.0, 0), (10.4, 0.8, 10.4), skip=("b", "u"), taper=(1.0, 1.0))      # 額の縄の帯
    for s in (-1, 1):
        f += bx(b, "sec", (s * 5.3, 27.0, -0.4), (1.2, 6.6, 7.4), rot=rot_z(-s * 6), taper=(0.8, 0.9))   # 頬の骨板
    f += charm(b, 5.9, 27.4, -2.0, 2.0)
    # 背びれの冠: 前から後ろへ流れる板
    f += ext(b, "sec", [(-5.8, 0), (5.2, 0), (4.0, 2.6), (0.6, 4.8), (-3.4, 3.4)], 1.2, c=(0, 33.4, 0), rot=rot_y(90))
    if t >= 1:
        f += ext(b, "edge", [(-5.0, 0), (4.0, 0), (2.4, 1.6), (-2.0, 2.0)], 1.6, c=(0, 33.2, 0), rot=rot_y(90))
        for s in (-1, 1):
            f += spk(b, "edge", (s * 4.9, 31.6, -1.4), (s * 0.8, 0.8, -0.1), 3.6, 1.1, sides=4, rings=2)   # 側頭の牙
    if t == 2:
        # 背びれの冠が 3 枚に、後ろへ流れる角 2 本
        f += ext(b, "sec", [(-4.6, 0), (3.4, 0), (1.8, 4.6), (-1.0, 6.4), (-3.2, 3.8)], 1.1, c=(1.9, 34.6, 0), rot=rot_y(90))
        f += ext(b, "sec", [(-3.6, 0), (2.4, 0), (0.6, 3.6), (-2.2, 2.6)], 1.0, c=(-1.9, 34.6, 0), rot=rot_y(90))
        for s in (-1, 1):
            f += spk(b, "sec", (s * 3.4, 32.6, 2.0), (s * 0.6, 0.35, 1.0), 6.0, 1.5, sides=4, rings=3, bend=(s * 0.8, -1.4, 1.2))
    return {b: f}


def chest(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    B = "armorBody"
    f = []
    rings = [rr(12.2, 4.9, -3.1, 3.0, cut=0.9), rr(17.0, 5.1, -3.6, 3.3, cut=1.1), rr(22.6, 5.1, -3.5, 3.3, cut=1.1), rr(24.3, 4.4, -3.0, 3.0, cut=1.0)]
    f += shell(B, "main", rings)
    f += bx(B, "sec", (0, 19.2, -4.2), (7.8, 6.8, 1.0), taper=(0.92, 0.95), skip=("b", "+z"))   # 骨の胸板
    f += bx(B, "edge", (0, 19.2, -4.9), (4.4, 5.0, 0.6), taper=(0.9, 0.9), skip=("b", "+z"))
    f += gem(B, (0, 19.8, -5.3), (2.0, 2.0, 0.5))
    f += bx(B, "trim", (0, 13.0, -3.6), (9.8, 1.4, 1.0), skip=("b", "+z"))
    f += bx(B, "cord", (0, 15.4, -3.9), (9.4, 0.7, 0.8), skip=("b", "+z"))                       # 胸板の下の縄
    f += bx(B, "main", (0, 13.1, -4.3), (2.4, 1.8, 0.6), skip=("b", "+z"))
    f += bx(B, "main", (0, 23.4, 0), (9.8, 1.6, 6.6), taper=(0.9, 0.9))                          # 首当て
    # 背: 石の瘤
    f += bx(B, "main", (0, 19.0, 4.1), (7.8, 8.0, 2.0), taper=(0.92, 0.92), skip=("b", "-z"))
    f += bx(B, "cord", (0, 15.4, 3.9), (9.4, 0.7, 0.8), skip=("b", "-z"))
    f += bx(B, "sec", (0, 18.6, 5.3), (2.6, 7.6, 0.8), taper=(0.8, 0.9), skip=("b", "-z"))       # 背骨の板
    # 腰の左右にぼろ布
    for s in (-1, 1):
        f += rag(B, (s * 4.2, 13.0, -0.4), 1.8, 4.0 + (1.2 if s == 1 else 0), rot=rot_y(90))
    if t >= 1:
        for k, (y, w) in enumerate(((22.6, 4.0), (19.2, 3.6), (15.8, 3.0))):    # 背びれ 3 枚 (横から見て大きな板)
            f += ext(B, "sec", [(-w, 0), (w, 0), (w * 0.5, 5.4 - 0.6 * k), (-w * 0.2, 6.2 - 0.6 * k)], 1.2, c=(0, y, 5.0 + 0.2 * k), rot=rot_x(-28))
        f += charm(B, -4.4, 14.6, -3.6, 2.0)
    if t == 2:
        # 背の肋の弧と長いぼろ布
        for s in (-1, 1):
            for k, y in enumerate((21.2, 17.6)):
                f += crv(B, "edge", [(s * 1.0, y, 5.0), (s * 4.0, y + 0.8, 5.4), (s * 6.4, y - 0.8, 4.2), (s * 6.8, y - 3.6, 2.4)],
                         [0.9, 0.9, 0.7, 0.0], sides=4, hint=(0, 0, 1))
        f += rag(B, (0, 14.0, 5.0), 3.4, 9.5, rot=None, th=0.5)
    out = {B: f}
    for s in (-1, 1):
        ab = ARM[s]
        g = []
        g += bx(ab, "cloth", (s * 6.2, 18.0, 0), (4.6, 11.0, 4.8), taper=(0.97, 0.97), skip=("u",))
        g += bx(ab, "main", (s * 6.2, 14.6, 0), (5.2, 3.6, 5.4), taper=(0.95, 0.95))          # 籠手の石
        g += bx(ab, "sec", (s * 6.2, 13.1, 0), (5.5, 0.8, 5.7))
        g += bx(ab, "cord", (s * 6.2, 16.8, 0), (5.0, 0.7, 5.2), skip=("u",))                  # 二の腕の縄
        # 大きな二段の肩
        g += bx(ab, "main", (s * 8.0, 24.5, 0), (8.4, 2.0, 7.0), rot=rot_z(-s * 12), taper=(0.78, 0.85))
        g += bx(ab, "main", (s * 9.6, 22.0, 0), (7.0, 1.8, 6.0), rot=rot_z(-s * 22), taper=(0.78, 0.85))
        g += bx(ab, "sec", (s * 8.0, 25.7, 0), (7.6, 0.8, 6.4), rot=rot_z(-s * 12), taper=(0.78, 0.85))
        if t == 2:
            g += bx(ab, "main", (s * 11.0, 19.6, 0), (5.6, 1.6, 5.0), rot=rot_z(-s * 30), taper=(0.78, 0.85))   # 三段目
        if t >= 1 and (s == -1 or t == 2):   # 牙: レアは右肩だけ、伝説は両肩
            g += spk(ab, "sec", V(s * 10.0, 25.4, 0.0), V(s * 0.55, 1, 0.0), 6.4 if t == 1 else 8.0, 1.5 if t == 1 else 1.8,
                     sides=4, rings=3, bend=(s * 1.6, 0, 0))
        out[ab] = g
    return out


def legs(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    out = {}
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = []
        g += bx(lb, "cloth", (cx, 6.2, 0), (4.4, 11.4, 4.8), taper=(0.97, 0.97), skip=("b", "u"))
        g += bx(lb, "main", (cx, 9.6, -2.7), (4.2, 5.2, 1.0), taper=(0.95, 0.92), skip=("b", "+z"))     # 腿当て
        g += bx(lb, "main", (cx, 11.0, -2.9), (4.5, 2.6, 1.0), rot=rot_x(-6), taper=(0.95, 0.9))       # 草摺
        g += bx(lb, "cord", (cx, 8.0, 0), (4.8, 0.7, 5.0), skip=("b", "u"))                           # 腿の縄
        g += bx(lb, "sec", (cx, 6.6, -3.0), (4.2, 2.8, 1.4), taper=(0.8, 0.75))                       # 膝の骨
        g += spk(lb, "sec", V(cx, 6.6, -3.6), V(0, 0.15, -1), 2.6 if t < 2 else 4.2, 1.0, sides=4, rings=2)
        g += bx(lb, "main", (cx, 3.6, -2.7), (3.8, 4.4, 1.0), taper=(0.9, 0.9), skip=("b", "+z"))     # すね当て
        g += bx(lb, "trim", (cx, 11.8, 0), (4.8, 0.8, 5.1), skip=("b", "u"))
        if s == -1:
            g += gem(lb, (cx, 6.7, -3.9), (1.2, 1.0, 0.4))
        if t >= 1:
            g += bx(lb, "sec", (cx, 1.8, -2.5), (4.0, 0.8, 1.0), skip=("b", "+z"))              # すねの下の骨の縁
        if t == 2:   # 腿の外の鰭
            g += ext(lb, "sec", [(-3.2, 0), (2.6, 0), (1.0, 3.6), (-2.0, 2.4)], 1.0, c=(cx + s * 2.6, 9.6, 0.2), rot=rot_y(90) @ rot_z(-s * 18))
        out[lb] = g
    return out


def boots(r):
    t = {"common": 0, "rare": 1, "legend": 2}[r]
    out = {}
    for s in (-1, 1):
        bb = BOOT[s]
        cx = s * 2.15
        g = []
        g += bx(bb, "main", (cx, 2.2, 0), (4.8, 4.4, 5.0), taper=(0.95, 0.95), skip=("b",))
        g += bx(bb, "main", (cx, 1.2, -3.6), (4.4, 2.4, 2.6), taper=(0.8, 0.7), skip=("b",))     # 爪先
        g += bx(bb, "sec", (cx, 1.9, -4.8), (3.4, 1.2, 0.8), taper=(0.7, 0.7), skip=("b",))       # 爪先の骨の当て
        g += bx(bb, "sec", (cx, 4.2, 0), (5.0, 0.9, 5.2), skip=("b", "u"))                        # 足首の骨の輪
        g += bx(bb, "cord", (cx, 3.2, 0), (5.0, 0.6, 5.2), skip=("b", "u"))
        g += bx(bb, "trim", (cx, 0.5, 0), (5.1, 0.8, 5.4), skip=("u",))
        g += gem(bb, (cx + s * 2.55, 3.6, 0), (0.5, 1.0, 1.2))                                      # 足首の外の小さな核
        if t >= 1:
            g += ext(bb, "edge", [(-2.0, 0), (2.0, 0), (1.0, 2.4), (-1.4, 3.0)], 0.9, c=(cx + s * 2.7, 3.4, 0.4), rot=rot_y(90) @ rot_z(-s * 22))   # 足首の鰭
        if t == 2:
            g += spk(bb, "sec", V(cx, 1.2, 2.2), V(0, 0.25, 1), 3.6, 1.1, sides=4, rings=2)       # かかとの爪
            g += spk(bb, "edge", V(cx, 1.6, -4.8), V(0, 0.2, -1), 3.0, 1.0, sides=4, rings=2)     # 爪先の牙
        out[bb] = g
    return out


def build(T="whale", rar="common"):
    pieces = {"helmet": helmet(rar), "chestplate": chest(rar), "leggings": legs(rar), "boots": boots(rar)}
    return {k: [f for bone_faces in v.values() for f in bone_faces] for k, v in pieces.items()}
