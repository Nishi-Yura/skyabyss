"""巨神の防具 4 組の一品一案 (2026-10-26 の声「色違いの手抜きはやめて」「巨神の武器装備はどれも同じくらいの豪華さ」)。
今入っている「巨神シリーズ S2」(4 巨神で同じ骨組み) をやめ、巨神ごとに別の形の「最終」案 (style_whale / style_tide / style_storm / style_ember) を土台にする。
豪華さをそろえる: 潮殻 (伝説 ~2,000 面) に合わせて、岩鯨と余燼に部品を足す (通常 ~1,200・伝説 ~1,800)。雷翼は少し足す。
  岩鯨 = 両肩の藤壺の群れ・縄の帯と骨の護符・苔の房 / レアから背の尾鰭の外套 / 伝説で尾鰭が大きく、肩の牙に骨の環
  余燼 = 腰の前の岩の草摺 (両脚)・大きな左肩の溶岩の筋・背の熱の排気の鰭 / レアから膝の割れ目 / 伝説で背の鰭が倍、尾の先に熾火
  雷翼 = 胸の銅の留め輪と羽の房 / 伝説で腕の外の長い風切羽
"""
from __future__ import annotations

import math

from akit import *  # noqa: F401,F403
from akit import V, bx, ext, spk, crv, rr, shell, rot_x, rot_y, rot_z, ARM, LEG, BOOT, feather
import style_whale
import style_tide
import style_storm
import style_ember

BASE = dict(whale=style_whale, tide=style_tide, thunder=style_storm, ember=style_ember)
TIER = {"common": 0, "rare": 1, "legend": 2}


def barnacle_cluster(b, c, n, spread, r=0.5, h=0.9, d=(0, 1, 0)):
    out = []
    for i in range(n):
        a = 2.39996 * i
        rr_ = spread * math.sqrt((i + 0.5) / n)
        p = V(c[0] + math.cos(a) * rr_, c[1] + (0.3 if i % 2 else 0), c[2] + math.sin(a) * rr_)
        out += spk(b, "edge", p, V(*d), h * (0.8 + 0.4 * (i % 3) / 2), r, sides=4, rings=1)
    return out


# ---------------------------------------------------------------- 岩鯨
def whale_extra(rar):
    t = TIER[rar]
    out = {"chestplate": [], "leggings": [], "helmet": [], "boots": []}
    B = "armorBody"
    f = []
    # 縄の帯 (腰を斜めに) と骨の護符二つ
    f += crv(B, "cord", [(-5.0, 16.2, -3.9), (-1.6, 15.0, -4.3), (2.0, 14.0, -4.2), (5.0, 13.4, -3.8)], [0.45] * 4, sides=4)
    for (x, y) in ((-1.6, 14.6), (2.0, 13.6)):
        f += crv(B, "cord", [(x, y, -4.4), (x, y - 1.6, -4.5)], [0.15, 0.15], sides=3)
        f += bx(B, "edge", (x, y - 2.4, -4.5), (0.9, 1.5, 0.5), taper=(0.6, 0.8))
    # 苔の房 (胸の上の縁から)
    for x in (-3.4, 3.0):
        f += crv(B, "acc", [(x, 23.0, -3.9), (x + 0.3, 21.4, -4.2), (x - 0.2, 20.0, -4.1)], [0.5, 0.35, 0.12], sides=4)
    if t >= 1:
        # 背の尾鰭の外套: 腰の後ろから下がる鯨の尾 (二枚の鰭)
        sc = 1.0 + 0.35 * (t - 1)
        f += bx(B, "main", (0, 11.0, 3.9), (3.0, 4.0 * sc, 1.0), taper=(0.7, 0.9))
        for s in (-1, 1):
            f += ext(B, "sec", [(0, 9.4), (s * 3.2 * sc, 8.6 - 0.8 * sc), (s * 5.4 * sc, 7.0 - 1.6 * sc), (s * 3.6 * sc, 7.6 - 0.6 * sc), (0, 8.4)][::s],
                     0.6, c=(0, 0, 4.2))
    out["chestplate"] += f
    for s in (-1, 1):
        ab = ARM[s]
        g = barnacle_cluster(ab, (s * 7.4, 24.4, 0.6), 5 + 2 * t, 1.6, r=0.45, h=0.8)
        if t == 2:
            g += crv(ab, "edge", [(s * 6.2, 25.2, -2.6), (s * 8.8, 26.0, 0), (s * 6.2, 25.2, 2.6)], [0.35] * 3, sides=4)
        out["chestplate"] += g
    # 脚: 腿の外の藤壺と、すねの苔
    for s in (-1, 1):
        lb = LEG[s]
        g = barnacle_cluster(lb, (s * 4.3, 8.2, 0.4), 3 + t, 1.0, r=0.35, h=0.6, d=(s, 0.2, 0))
        g += crv(lb, "acc", [(s * 2.2, 5.0, -3.3), (s * 2.0, 3.6, -3.5)], [0.4, 0.15], sides=4)
        out["leggings"] += g
    return out


# ---------------------------------------------------------------- 余燼
def ember_extra(rar):
    t = TIER[rar]
    out = {"chestplate": [], "leggings": [], "helmet": [], "boots": []}
    B = "armorBody"
    f = []
    # 背の熱の排気の鰭 (肩甲骨の上に左右三枚ずつ、後ろ外へ)
    for s in (-1, 1):
        for k in range(3 + 2 * (t == 2)):
            f += bx(B, "main" if k % 2 == 0 else "acc", (s * (2.2 + 0.4 * k), 21.6 - 1.6 * k, 4.6 + 0.3 * k), (2.6, 0.7, 2.4),
                    rot=rot_x(28) @ rot_z(-s * 20), taper=(0.7, 0.8))
            f += bx(B, "gem", (s * (2.2 + 0.4 * k), 21.2 - 1.6 * k, 4.4 + 0.3 * k), (2.0, 0.25, 1.6), rot=rot_x(28) @ rot_z(-s * 20))
    if t == 2:
        # 尾の先の熾火 (尾板の下)
        f += bx(B, "gem", (0, 4.4, 5.4), (1.2, 1.2, 1.2), rot=rot_y(45))
        f += spk(B, "main", V(0, 5.4, 5.2), V(0, -1, 0.4), 2.4, 0.9, sides=4, rings=1)
    out["chestplate"] += f
    # 大きな左肩の溶岩の筋
    ab = ARM[1]
    out["chestplate"] += bx(ab, "gem", (9.4, 26.5, -2.95), (4.6, 0.35, 0.3), rot=rot_z(-16))
    out["chestplate"] += bx(ab, "gem", (10.8, 23.6, -2.6), (3.2, 0.3, 0.3), rot=rot_z(-34))
    # 腰の前の岩の草摺 (両脚。鉄の縁と鋲)
    for s in (-1, 1):
        lb = LEG[s]
        cx = s * 2.15
        g = []
        for k in range(2):
            g += bx(lb, "main" if k == 0 else "acc", (cx + s * 0.2, 10.6 - 2.6 * k, -3.6 - 0.25 * k), (4.6 - 0.4 * k, 2.6, 0.7), taper=(0.92, 0.9), rot=rot_x(-6 - 4 * k))
            g += bx(lb, "sec", (cx + s * 0.2, 9.4 - 2.6 * k, -3.95 - 0.25 * k), (4.4 - 0.4 * k, 0.4, 0.4))
        if t >= 1:
            g += bx(lb, "gem", (cx, 5.3, -3.85), (2.2, 0.3, 0.3), rot=rot_z(s * 18))
        out["leggings"] += g
    return out


# ---------------------------------------------------------------- 雷翼
def storm_extra(rar):
    t = TIER[rar]
    out = {"chestplate": [], "leggings": [], "helmet": [], "boots": []}
    B = "armorBody"
    f = []
    # 胸の銅の留め輪と、輪から下がる羽の房
    f += crv(B, "acc", [(-1.3, 21.2, -4.3), (0, 22.4, -4.4), (1.3, 21.2, -4.3), (0, 20.0, -4.4), (-1.3, 21.2, -4.3)], [0.32] * 5, sides=4)
    for x in (-0.8, 0.8):
        f += feather(B, "sec", (x, 20.0, -4.5), (x * 1.6, 16.4, -4.7), 0.7, th=0.25, n=(0, 0, 1))
    out["chestplate"] += f
    if t == 2:
        for s in (-1, 1):
            ab = ARM[s]
            for k in range(3):
                out["chestplate"] += feather(ab, "sec", (s * 8.4, 20.0 - 2.4 * k, 0.8), (s * 10.6, 13.6 - 2.4 * k, 1.8), 0.9, th=0.3, n=(0, 0, 1))
    return out


EXTRA = dict(whale=whale_extra, ember=ember_extra, thunder=storm_extra)


def build(T, rar="common"):
    pieces = BASE[T].build(T, rar)
    if T in EXTRA:
        for k, fs in EXTRA[T](rar).items():
            pieces[k] = pieces[k] + fs
    return pieces
