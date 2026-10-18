"""旅の職人 (traveling artisan) 低ポリ ラフ 3 案 + 推奨案の章ごとの 4 色。

役: 旅の道具を直し、旅装を編む。役名だけで固有の名前はない。語り部・イサナ・見る者と頭の形を変える。
顔は頭の面に描く (目・眉・口)。光らない (glow なし)。ck の paint を包んで、頭の面だけ後から描き足す。

座標: 1 単位 = 1/16 ブロック。前 = -Z、y が上。身長は約 30 単位 (イサナの頭の高さ 21 に近い)。
骨: root > body > head / armL / armR / legL / legR。道具・荷は body か head の子 (同じ骨の面として付ける)。

ids: artisan (3 案のラフ) / artisan_colorways (推奨案の章ごとの 4 色。a〜d = 一〜四章)。
"""
from ck import *  # noqa
import ck as CK
import numpy as np
from ck import part, mat, ring, loft, tube, ellipsoid, spike, build_option, sheet, run  # noqa

# ====================================================================== 色 (ck の STONE・BROWN を使う)

CLOAK = [(26, 34, 46), (44, 58, 78), (68, 86, 112), (94, 114, 138), (122, 142, 164)]     # 灰青の外套 (全章で同じ)
SKIN = [(70, 48, 38), (110, 78, 60), (150, 112, 86), (188, 148, 116), (218, 180, 146)]
HAIR = [(20, 18, 22), (42, 38, 44), (70, 66, 72), (100, 96, 102)]
LINEN = [(78, 74, 62), (118, 110, 92), (156, 148, 124), (190, 182, 156)]
WOOD = [(36, 24, 16), (66, 46, 30), (100, 72, 46), (134, 100, 64)]
IRON = [(28, 30, 34), (58, 62, 68), (94, 98, 106), (132, 136, 144), (172, 176, 182)]
WHET = [(30, 34, 36), (54, 60, 62), (84, 90, 92), (116, 122, 124)]
GLASS = [(40, 60, 70), (78, 104, 116), (120, 150, 160), (168, 196, 204)]
BOOT = [(16, 12, 10), (30, 24, 20), (52, 42, 36)]
CORD = [(20, 18, 22), (38, 34, 40), (60, 56, 62)]                                           # 暗い紐・布の縁
COPPER = [(60, 30, 14), (110, 58, 28), (166, 96, 48), (210, 136, 76), (240, 180, 120)]
SALT = [(116, 122, 126), (160, 166, 168), (200, 206, 204), (228, 232, 226), (246, 248, 242)]
SOOT = [(10, 9, 9), (22, 19, 18), (38, 34, 32), (56, 50, 46)]
OCHRE = [(60, 40, 12), (120, 86, 28), (176, 134, 56)]                                       # 糸玉
INDIGO = [(14, 18, 40), (30, 42, 80), (60, 80, 120)]
MADDER = [(50, 18, 16), (100, 40, 34), (150, 72, 58)]

# 章ごとの色 (apron = 前掛け, trim = 縁の飾り, guard = 腕当て)。ch1: 灰青の外套は全章で同じ
EMBER = [(70, 22, 8), (150, 58, 14), (206, 110, 30), (240, 160, 60)]
CHAPTERS = {
    # hem = 外套の裾の帯 (一章 岩殻の縁・二章 塩の縁・三章 は外套のまま・四章 煤の裾)
    # acc = 襟・袖口・前の釦 (章の飾りの色)
    "a": dict(apron=(STONE, 0.66), trim=CORD, guard=BROWN, acc=(STONE, 0.66), stitch=CORD, hem=(STONE, 0.7)),
    "b": dict(apron=(SALT, 0.7), trim=CORD, guard=BROWN, acc=(SALT, 0.7), stitch=SALT, hem=(SALT, 0.74)),
    "c": dict(apron=(BROWN, 0.66), trim=COPPER, guard=BROWN, acc=(COPPER, 0.7), stitch=COPPER, hem=(CLOAK, 0.7)),
    "d": dict(apron=(BROWN, 0.66), trim=CORD, guard=SOOT, acc=(SOOT, 0.6), stitch=EMBER, hem=(SOOT, 0.6)),
}


def pal_for(key):
    c = CHAPTERS[key]
    return {"mats": {
        "cloak": mat(CLOAK, 0.7, crack=0.0, noise=0.12),
        "apron": mat(c["apron"][0], c["apron"][1], crack=0.12, noise=0.08),
        "trim": mat(c["trim"], 0.7, crack=0.0),
        "guard": mat(c["guard"], 0.64, crack=0.04, noise=0.15),
        "acc": mat(c["acc"][0], c["acc"][1], crack=0.1, noise=0.1),
        "hem": mat(c["hem"][0], c["hem"][1], crack=0.1, noise=0.1),
        "stitch": mat(c["stitch"], 0.72, crack=0.0),
        "skin": mat(SKIN, 0.72, crack=0.0, noise=0.05),
        "hair": mat(HAIR, 0.6, crack=0.0),
        "linen": mat(LINEN, 0.7, crack=0.0, noise=0.2),
        "leather": mat(BROWN, 0.62, crack=0.05),
        "cord": mat(CORD, 0.55, crack=0.0),
        "boot": mat(BOOT, 0.6, crack=0.0),
        "wood": mat(WOOD, 0.66, crack=0.0),
        "iron": mat(IRON, 0.72, crack=0.0),
        "whet": mat(WHET, 0.66, crack=0.1),
        "glass": mat(GLASS, 0.8, crack=0.0),
        "thr_o": mat(OCHRE, 0.7, crack=0.0),
        "thr_i": mat(INDIGO, 0.7, crack=0.0),
        "thr_r": mat(MADDER, 0.7, crack=0.0),
    }, "skull_bone": "head", "face_mat": "skin"}


# ====================================================================== 骨 (三案で共通)

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 12, 0)), "head": ("body", (0, 22, 0)),
         "armL": ("body", (4.8, 20.6, 0)), "armR": ("body", (-4.8, 20.6, 0)),
         "legL": ("body", (2.0, 12.4, 0)), "legR": ("body", (-2.0, 12.4, 0))}

# 顔の位置の補正 (案ごとに頭の中心をずらす。paint の目・眉・口の位置に使う)
FACE = {"dy": 0.0, "dz": 0.0}


# ====================================================================== 共通の部品

def head_std(sx=1.0, sy=1.0, dy=0.0, dz=0.0, neck_y=23.0):
    """neck_y = 襟 (外套の上端の面) の高さ。首はそこから始め、襟の中に埋めない。"""
    FACE["dy"], FACE["dz"] = dy, dz
    F = []
    F += part("head", ellipsoid("head", "skin", (0, 26.2 + dy, -0.2 + dz), (3.6 * sx, 4.2 * sy, 3.5), segs=8, rings=4))
    F += part("nose", ellipsoid("head", "skin", (0, 25.8 + dy, -3.5 + dz), (0.55, 1.0, 0.6), segs=4, rings=1))
    for s in (-1, 1):
        F += part("ear", ellipsoid("head", "skin", (s * 3.5 * sx, 26.2 + dy, -0.2 + dz), (0.6, 1.1, 0.9), segs=4, rings=1))
    # 首は襟の面 (neck_y) から短く出し、先は頭の球の中に入れる (襟や頭の中へ深く埋めない)
    F += part("neck", tube("head", "skin", [(0, neck_y, 0.2 + dz * 0.4), (0, neck_y + 0.4, dz * 0.4)], ra=[1.5, 1.5], sides=5))
    return F


def arm(side, up=1.5, fore=1.35, hand=1.1, sh=None, el=None, wr=None):
    """side = +1 で armL (x 正), -1 で armR。上腕は外套、前腕は腕当て、手は肌。"""
    b = "armL" if side > 0 else "armR"
    sh = sh or (side * 4.8, 20.6, 0.0)
    el = el or (side * 5.2, 16.0, -0.7)
    wr = wr or (side * 5.1, 12.4, -2.6)
    F = []
    F += part(b + "u", tube(b, "cloak", [sh, el], ra=[up, up * 0.92], sides=5))
    F += part(b + "f", tube(b, "guard", [el, wr], ra=[fore, fore * 0.85], sides=5))
    F += part(b + "h", ellipsoid(b, "skin", (wr[0], wr[1] - 0.8, wr[2] - 0.2), (hand, 1.2, 1.0), segs=4, rings=2))
    return F


def legs(boot_r=1.55, cord_top=None):
    F = []
    for s in (-1, 1):
        b = "legL" if s > 0 else "legR"
        F += part(b, tube(b, "cord", [(s * 2.0, 12.6, 0), (s * 2.2, 6.0, 0), (s * 2.2, 2.3, -0.1)],
                          ra=[1.7, 1.55, 1.45], sides=5))
        F += part(b + "b", ellipsoid(b, "boot", (s * 2.2, 1.6, -0.7), (boot_r, 1.45, 2.6), segs=5, rings=2))
    return F


def face_marks(atlas, faces, P, N, M, pal, eyes=()):
    """ck の paint のあとに、頭の前の面へ目・眉・口を描く (光らない。色は暗い茶と髪色)。"""
    fi = np.where(M >= 0, M, 0)
    bones = np.array([f.bone for f in faces])[fi]
    dy, dz = FACE["dy"], FACE["dz"]
    x = np.abs(P[..., 0])
    y = P[..., 1]
    z = P[..., 2]
    front = (bones == "head") & (M >= 0) & (N[..., 2] < -0.25) & (y > 21.5 + dy) & (z < -2.0 + dz)
    eye = front & ((((x - 1.35) / 0.85) ** 2 + ((y - (26.7 + dy)) / 0.62) ** 2) < 1.0)
    iris = eye & ((((x - 1.35) / 0.42) ** 2 + ((y - (26.7 + dy)) / 0.42) ** 2) < 1.0)
    brow = front & (x > 0.6) & (x < 2.6) & (np.abs(y - (28.4 + dy - 0.2 * (x - 0.6))) < 0.3)
    mouth = front & (x < 1.0) & (np.abs(y - (24.1 + dy)) < 0.24)
    out = atlas.copy()
    out[eye, :3] = (34, 22, 20)
    out[iris, :3] = (92, 62, 40)
    out[brow, :3] = (44, 38, 40)
    out[mouth, :3] = (74, 36, 32)
    return out


_orig_paint = CK.paint


def paint_with_face(faces, size, P, N, M, pal, eyes=()):
    img, glow = _orig_paint(faces, size, P, N, M, pal, ())   # 目は光らせない (ck の光る瞳は使わない)
    img = face_marks(img, faces, P, N, M, pal)
    return img, np.zeros_like(glow)


CK.paint = paint_with_face


# ====================================================================== 案 A 針と糸の繕い人 (背に糸玉の架)

def build_a(k):
    F = head_std(dy=-0.8, dz=-2.0)
    # 背を丸めた外套 (上が前へ落ちる)
    F += part("coat", loft("body", "cloak", [ring(10.0, 4.4, -2.6, 2.6), ring(14.0, 4.8, -2.9, 2.9),
                                              ring(18.0, 5.0, -3.0, 3.0), ring(21.0, 4.8, -3.6, 1.6),
                                              ring(23.0, 4.0, -3.0, 1.2)], caps=(False, "cloak")))
    F += part("apron", loft("body", "apron", [ring(9.6, 4.2, -3.6, -3.1), ring(16.0, 4.4, -3.6, -3.0)],
                            caps=("apron", "apron")))
    F += part("trim", tube("body", "trim", [(-4.0, 9.8, -3.7), (0, 9.6, -3.8), (4.0, 9.8, -3.7)], ra=[0.22] * 3, sides=4))
    # 糸玉の架 (背の上の枠。糸玉を 6 つ掛ける)
    for x in (-2.4, 2.4):
        F += part("rack_post", tube("body", "wood", [(x, 11.0, 3.0), (x * 1.05, 29.0, 3.6)], ra=[0.45, 0.42], sides=4))
    F += part("rack_bar_hi", tube("body", "wood", [(-3.0, 28.4, 3.4), (3.0, 28.4, 3.4)], ra=[0.4, 0.4], sides=4))
    F += part("rack_bar_lo", tube("body", "wood", [(-3.0, 19.4, 3.1), (3.0, 19.4, 3.1)], ra=[0.4, 0.4], sides=4))
    spools = [("thr_o", -2.0, 26.9, 3.4), ("thr_i", 0.0, 26.9, 3.4), ("thr_r", 2.0, 26.9, 3.4),
              ("thr_i", -2.0, 17.9, 3.1), ("thr_o", 0.0, 17.9, 3.1), ("thr_r", 2.0, 17.9, 3.1)]
    for m, x, y, z in spools:
        F += part("spool", ellipsoid("body", m, (x, y, z + 0.3), (0.9, 1.2, 0.9), segs=4, rings=2))
    F += part("bundle", ellipsoid("body", "linen", (0, 13.8, 3.6), (3.0, 2.4, 2.0), segs=5, rings=2))
    # 腕 (少し前へ)
    F += arm(-1, up=1.35, fore=1.2, hand=1.0, sh=(-4.8, 20.2, -1.2), el=(-5.0, 16.0, -1.8), wr=(-5.0, 12.6, -3.6))
    F += arm(1, up=1.35, fore=1.2, hand=1.0, sh=(4.8, 20.2, -1.2), el=(5.0, 16.0, -1.8), wr=(5.0, 12.6, -3.6))
    F += legs(boot_r=1.45)
    return F, []


# ====================================================================== 案 B 槌と砥石の荷人 (道具の前掛け・腰の槌と砥石・額の眼鏡)

def build_b(k):
    F = head_std()
    F += part("jacket", loft("body", "cloak", [ring(9.0, 4.6, -2.9, 2.9), ring(13.0, 5.2, -3.1, 3.0),
                                                ring(17.5, 5.8, -3.3, 3.0), ring(21.0, 5.6, -3.0, 2.7),
                                                ring(23.0, 4.4, -2.3, 2.3)], caps=(False, "cloak")))
    F += part("apron", loft("body", "apron", [ring(9.4, 4.1, -3.9, -3.4), ring(17.6, 4.6, -3.7, -3.1)],
                            caps=("apron", "apron")))
    F += part("strap_l", tube("body", "leather", [(-3.9, 17.8, -3.4), (-2.6, 21.2, -2.6), (-2.4, 22.4, -2.4)], ra=[0.3, 0.3, 0.3], sides=4))
    F += part("strap_r", tube("body", "leather", [(3.9, 17.8, -3.4), (2.6, 21.2, -2.6), (2.4, 22.4, -2.4)], ra=[0.3, 0.3, 0.3], sides=4))
    F += part("belt", loft("body", "leather", [ring(10.0, 5.0, -3.2, 3.2), ring(11.0, 5.0, -3.2, 3.2)]))
    # 道具の巻き (革の袋。前掛けの下寄り)
    F += part("toolroll", tube("body", "leather", [(-3.3, 13.2, -4.2), (0, 13.3, -4.35), (3.3, 13.2, -4.2)], ra=[0.85, 0.95, 0.85], sides=5))
    F += part("tool_a", tube("body", "wood", [(-2.0, 13.6, -4.6), (-2.4, 15.6, -5.0)], ra=[0.3, 0.28], sides=4))
    F += part("tool_a_h", ellipsoid("body", "iron", (-2.4, 16.0, -5.1), (0.5, 0.9, 0.5), segs=4, rings=1))
    F += part("tool_b", tube("body", "wood", [(0.8, 13.7, -4.7), (1.2, 16.2, -5.0)], ra=[0.3, 0.28], sides=4))
    F += part("tool_b_h", ellipsoid("body", "iron", (1.3, 16.6, -5.1), (0.4, 0.8, 0.4), segs=4, rings=1))
    # 縁の銅 (前掛けの底と両脇)
    F += part("trim_b", tube("body", "trim", [(-4.0, 9.5, -4.0), (0, 9.4, -4.05), (4.0, 9.5, -4.0)], ra=[0.22] * 3, sides=4))
    F += part("trim_l", tube("body", "trim", [(-3.9, 9.6, -3.95), (-4.1, 17.4, -3.6)], ra=[0.2] * 2, sides=4))
    F += part("trim_r", tube("body", "trim", [(3.9, 9.6, -3.95), (4.1, 17.4, -3.6)], ra=[0.2] * 2, sides=4))
    # 腰の砥石 (左) と槌 (右)
    F += part("whet", ellipsoid("body", "whet", (-5.4, 9.5, -2.2), (0.75, 1.5, 0.6), segs=4, rings=2))
    F += part("hammer_h", tube("body", "wood", [(4.4, 10.2, -3.3), (5.4, 6.6, -3.8)], ra=[0.42, 0.36], sides=4))
    F += part("hammer_head", ellipsoid("body", "iron", (5.5, 6.3, -4.0), (1.0, 0.55, 0.6), segs=4, rings=1))
    # 眼鏡 (額の上へ押し上げた輪。レンズは丸い小さな楕円体)
    F += part("goggle_strap", tube("head", "leather", [(-3.3, 28.2, -1.6), (0, 29.1, -3.0), (3.3, 28.2, -1.6)],
                                   ra=[0.3] * 3, sides=3))
    for s in (-1, 1):
        cx, cy = s * 2.0, 28.9
        F += part("lens", ellipsoid("head", "glass", (cx, cy, -3.2), (0.95, 0.95, 0.42), segs=6, rings=2))
        ring_pts = [(cx + 1.05 * np.cos(2 * np.pi * k / 6), cy + 1.05 * np.sin(2 * np.pi * k / 6), -3.25) for k in range(7)]
        F += part("lens_rim", tube("head", "leather", ring_pts, ra=[0.22] * 7, sides=3))
    F += part("hair", ellipsoid("head", "hair", (0, 28.6, 0.9), (3.7, 2.3, 3.2), segs=8, rings=3))
    # 裾の飾り (章ごとの色・三章だけ外套の色)
    F += part("hem_b", loft("body", "hem", [ring(8.8, 4.7, -3.0, 3.0), ring(9.6, 4.8, -3.05, 3.05)]))
    # 襟の帯 (章ごとの飾り色。首はこの上から出る)
    F += part("collar", loft("body", "acc", [ring(22.5, 4.8, -2.6, 2.5), ring(23.3, 4.3, -2.2, 2.2)]))
    # 釦 (前、章ごとの飾り色)
    for y, z in ((18.6, -3.3), (21.2, -2.85)):
        F += part("button", ellipsoid("body", "acc", (0, y, z), (0.36, 0.36, 0.22), segs=4, rings=1))
    F += arm(-1, up=1.6, fore=1.45, hand=1.25)
    F += arm(1, up=1.6, fore=1.45, hand=1.25)
    F += legs(boot_r=1.6)
    return F, []


# ====================================================================== 案 C 長い外套の灯し人 (細い・高い・提灯と革の筒)

def build_c(k):
    F = head_std(sx=0.85, sy=1.1, dy=0.4, neck_y=22.6)
    F += part("coat", loft("body", "cloak", [ring(2.0, 5.2, -4.2, 4.0), ring(6.0, 4.4, -3.8, 3.4),
                                              ring(11.0, 3.8, -3.0, 3.0), ring(15.5, 3.7, -2.8, 2.8),
                                              ring(20.5, 3.8, -2.9, 2.8), ring(22.6, 3.4, -2.4, 2.3)], caps=(False, "cloak")))
    F += part("apron", loft("body", "apron", [ring(8.0, 3.7, -4.1, -3.4), ring(16.5, 3.8, -3.9, -3.1)],
                            caps=("apron", "apron")))
    F += part("trim_c", tube("body", "trim", [(-3.6, 8.2, -4.15), (0, 8.0, -4.2), (3.6, 8.2, -4.15)], ra=[0.2] * 3, sides=4))
    F += part("belt", loft("body", "leather", [ring(10.2, 3.9, -3.0, 3.0), ring(11.0, 3.9, -3.0, 3.0)]))
    # headscarf (糸の巻き布)
    F += part("scarf", loft("head", "linen", [ring(26.9, 3.4, -3.95, 3.8), ring(27.9, 3.3, -3.85, 3.7),
                                              ring(28.9, 2.9, -3.45, 3.0)]))
    # 提灯 (左の腰に下げる。覆いの円錐。光らない)
    F += part("lantern_chain", tube("body", "iron", [(-3.4, 10.6, -3.0), (-3.9, 9.6, -3.4)], ra=[0.18, 0.18], sides=4))
    F += part("lantern", ellipsoid("body", "iron", (-4.4, 8.4, -3.8), (0.9, 1.2, 0.9), segs=5, rings=2))
    F += part("lantern_hood", spike("body", "iron", (-4.4, 9.5, -3.8), (0, 1, 0), 1.2, 1.1, sides=4, rings=1))
    # 腰の袋 (右)
    F += part("pouch_a", ellipsoid("body", "leather", (4.5, 9.8, -2.8), (0.75, 1.0, 0.6), segs=5, rings=2))
    F += part("pouch_b", ellipsoid("body", "leather", (4.6, 8.2, -2.4), (0.7, 0.9, 0.6), segs=5, rings=2))
    F += part("chisel", tube("body", "wood", [(3.4, 12.6, -3.4), (4.6, 15.4, -4.2)], ra=[0.36, 0.3], sides=4))
    # 背の革の筒 (斜めに掛ける)
    F += part("case", tube("body", "leather", [(-3.0, 22.0, 3.7), (0, 15.0, 4.0), (3.0, 10.6, 3.7)], ra=[0.95, 1.0, 0.95], sides=5))
    F += arm(-1, up=1.2, fore=1.05, hand=0.95, sh=(-4.0, 20.6, 0), el=(-4.2, 16.0, -0.6), wr=(-4.0, 12.4, -2.2))
    F += arm(1, up=1.2, fore=1.05, hand=0.95, sh=(4.0, 20.6, 0), el=(4.2, 16.0, -0.6), wr=(4.0, 12.4, -2.2))
    F += part("boot_l", ellipsoid("body", "boot", (1.5, 1.0, -1.2), (1.3, 1.0, 1.9), segs=5, rings=2))
    F += part("boot_r", ellipsoid("body", "boot", (-1.5, 1.0, -1.2), (1.3, 1.0, 1.9), segs=5, rings=2))
    return F, []


BUILDS = {"a": build_a, "b": build_b, "c": build_c}
REC = "b"          # 推奨案 (理由は ck の説明欄と報告に書く)


def build(k):
    return BUILDS[k](k)


# ====================================================================== 姿勢

POSES = {
    "idle": {"head": (-2, 0, 0)},
    "talk": {"armR": (62, 0, -14), "head": (-4, 10, 0)},
    "work": {"body": (10, 0, 0), "head": (-18, 0, 0), "armR": (78, 0, 0), "armL": (55, 0, 0)},
}

SPEC = dict(
    id="artisan", short="旅の職人", kind="NPC 3案 (旅の職人・一章の色)",
    title="旅の職人 (traveling artisan) — 低ポリ + 顔 3 案",
    sub=["役名だけ (固有の名前なし)。道具を直し、旅装を編む。一章の色 (灰青の外套・岩殻の前掛け) で描いてある。",
         "左の黄の目盛りは人 (1.8 ブロック) の高さ。顔は頭の面に描いた目・眉・口 (光らない)。"],
    labels=["A 糸の繕い人", "B 槌と砥石の荷人", "C 灯し人"],
    rows=[("前から", "idle", 180, 6, 420, (0, 14.5, 0), 12.0),
          ("横", "idle", 270, 6, 420, (0, 14.5, 0), 12.0),
          ("前 3/4", "idle", 212, 12, 420, (0, 14.5, 0), 12.0),
          ("後ろ 3/4 (背の荷)", "idle", 32, 12, 420, (0, 14.5, 0), 12.0),
          ("話す (前 3/4)", "talk", 212, 12, 420, (0, 14.5, 0), 12.0),
          ("作業 (前 3/4)", "work", 212, 12, 420, (0, 14.5, 0), 12.0)],
    options={
        "a": dict(name="A 糸の繕い人", desc="背を丸めた繕い人。背に糸玉の架を負い、六つの糸玉 (オーカー・藍・茶) を掛ける。前かがみの頭と、針を持つ手が前に出る。シルエットが一番「仕事」に寄っている。"),
        "b": dict(name="B 槌と砥石の荷人", desc="肩の広い荷人。前掛けに道具の巻き (革) と、からの柄の先が見える。腰の左に砥石、右に槌。額へ眼鏡を押し上げる。前掛けと腕当ての面積が大きく、章ごとの色が一番よく読める。"),
        "c": dict(name="C 灯し人", desc="細く高い長い外套。左の腰に覆いのついた提灯、右に袋と柄。背に革の筒を斜めに掛け、頭には糸の布を巻く。外套の面が大きく、色は外套の縁の飾りでしか変えられない。")},
    extra="旅の職人の三案 (ch1 の色で描いた)。骨 7 本 (root / body / head / armL / armR / legL / legR)。頭は語り部・イサナ・見る者と違う丸い骨格にし、額の眼鏡・糸の布・眼鏡の有無で区別する。",
    budget="NPC 800 以下", atlas_h=300, pw=440)


# ====================================================================== 推奨案の章ごとの 4 色

COL_SPEC = dict(
    id="artisan_colorways", short="旅の職人の色", kind="NPC 4章の色",
    title="旅の職人 — 章ごとの 4 色 (推奨案 B の形)",
    sub=["同じ形・同じ顔・同じ灰青の外套。章の飾りは、裾の帯・襟の帯・釦・袖口・前掛けの色で出す。",
         "下の一行は各章の色の要点。左から一章・二章・三章・四章。"],
    labels=["一章 岩殻の前掛け", "二章 塩の前掛け", "三章 銅の縁", "四章 煤の腕当て"],
    rows=[("前から", "idle", 180, 6, 420, (0, 14.5, 0), 12.0),
          ("前 3/4", "idle", 212, 12, 420, (0, 14.5, 0), 12.0),
          ("後ろ 3/4", "idle", 32, 12, 420, (0, 14.5, 0), 12.0),
          ("顔の近く (前)", "idle", 180, 0, 300, (0, 26.0, -2.0), 40.0)],
    pw=300, atlas_h=240)


if __name__ == "__main__":
    # 三案 (一章の色)
    run(SPEC, build, pal_for("a"), BONES, POSES, hint=256)
    # 推奨案の章ごとの色 (a〜d = 一〜四章)
    OUTC = CK.OUT
    results = []
    for key in "abcd":
        r = build_option("artisan_colorways", key, lambda kk: BUILDS[REC](kk), pal_for(key), BONES, 256)
        results.append(r)
        print("colorways", key, "tris", r["tris"], "atlas", r["size"], "buried", r["buried"])
    sheet(COL_SPEC, results, BONES, POSES, OUTC / "artisan_colorways.png")
    import json
    tri_txt = " / ".join("%s %d" % (k.upper(), r["tris"]) for k, r in zip("abcd", results))
    bur_txt = " / ".join("%s %s" % (k.upper(), ", ".join("%s-%s:%s" % (a, b, f) for a, b, f in r["buried"]) or "なし")
                         for k, r in zip("abcd", results))
    ent = {
        "id": "ch1_artisan_colorways", "short": COL_SPEC["short"], "kind": COL_SPEC["kind"],
        "title": COL_SPEC["title"], "sheets": ["ch1_roughs/artisan_colorways.png"],
        "extra": ("推奨案 B (槌と砥石の荷人) の章ごとの 4 色。形・顔・骨は同じで、材質の色だけ替える。"
                  "灰青の外套は全章で同じ。"
                  "三角形 (全身): " + tri_txt + " (目安 800 以下)。"
                  "埋まった部品の検査: " + bur_txt +
                  "。メッシュ・アトラスは docs/review/ch1_roughs/artisan_colorways/{a,b,c,d}/。生成: tools/rough/lowpoly/ch1/artisan.py。"),
        "options": {
            "a": {"name": "一章 岩殻の縁", "desc": "灰青の外套。裾と襟の帯、釦を岩殻の石の色に。前掛けも岩殻の色。一章の地上の色 (岩殻虫の殻・石標) と揃える。"},
            "b": {"name": "二章 塩の縁", "desc": "外套の裾と襟の帯、釦、前掛けを塩で白く。潮の民の色 (塩結晶・真珠) と並んで見える。"},
            "c": {"name": "三章 銅の針金の襟", "desc": "外套の色は変えず、襟・袖口・釦を銅の針金に。前掛けは茶の革。雷の章の金属の色。"},
            "d": {"name": "四章 煤の裾・火の糸", "desc": "裾の帯と釦を煤けた黒に、袖口も煤。前掛けの縫い目は火の糸 (橙)。四章の火の色を縫いに置く。"}},
    }
    (OUTC / "artisan_colorways_manifest.json").write_text(json.dumps(ent, ensure_ascii=False, indent=1), encoding="utf-8")
    print("wrote", OUTC / "artisan_colorways.png")
