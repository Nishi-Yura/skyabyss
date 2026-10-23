"""都の施設の番人 (facility attendants) 低ポリ ラフ 3 案 x 16 役。

役ごとに一人、施設の前に立つ。旅の職人と同じ NPC の作り (骨 7 本・同じピボット) なので、旅の職人のアニメ
(idle / walk / talk) がそのまま動く。案ごとにメッシュは 1 つ・UV も 1 つで、役の違いは絵 (アトラスの色) だけ。
道具などの小物はメッシュに付けない (全役で同じ形を使うため)。腰の袋だけは誰が持っていてもおかしくないので付ける。

案:
  A 上着と前掛け   旅の職人 B の体 (上着・前掛け・襟・釦)。役の色は前掛け・縁・襟。いまの NPC に一番近い (推奨)
  B 長い衣と帯     裾の長い衣に幅の広い帯。役の色は衣と帯。脚は衣の裾の下から出る
  C 短い上着と肩掛け  短い上着の上に肩掛けと、背へ下ろした頭巾。役の色は肩掛けと頭巾

顔: 頭の前の面に目・眉・口を描く (光らない)。顔を隠した商人は、鼻から下を布の色で塗る (形は同じで絵だけ)。
女将は唇の色と、まつげの線を足す。酔いどれは鼻と頬を赤く。
座標: 1 単位 = 1/16 ブロック。前 = -Z、y が上。

  python tools/rough/lowpoly/fac/attendants.py
    -> docs/review/npc_roughs/{a,b,c}/attendant_<look>_mesh.json, attendant_<look>_<role>_atlas.png, attendant_<look>_detail.png
       docs/review/npc_roughs/look_{a,b,c}.png (16 役の前 3/4) と compare.png (3 案 x 数役)
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "ch1"))
import ck as CK  # noqa: E402
from ck import part, mat, ring, loft, tube, ellipsoid, check_buried, export_mesh, render_view, sheet, build_atlas  # noqa: E402
import artisan as ART  # noqa: E402  (head_std・FACE を借りる。CK.paint の差し替えはここでは使わない)

ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/npc_roughs"
RA_PAINT = ART._orig_paint          # 元の ck の paint (ra_common.paint)
voxel = CK.voxel

# 骨は旅の職人と同じ (ピボットも同じ。アニメを共有する)
BONES = ART.BONES

ROLES = ["guild_clerk", "smith", "tool_merchant", "apothecary", "storage_keeper", "innkeeper", "perch_keeper",
         "shrine_priest", "keep_veteran", "archivist", "training_master", "black_marketeer", "gravekeeper",
         "mapmaker", "drunkard", "trade_keeper", "stable_keeper", "guide"]
ROLE_JA = {"guild_clerk": "組合の受付の書記", "smith": "鍛冶師", "tool_merchant": "道具屋", "apothecary": "薬師",
           "storage_keeper": "預かり番", "innkeeper": "宿の女将", "perch_keeper": "止まり木の番", "shrine_priest": "社の祭司",
           "keep_veteran": "砦の老兵", "archivist": "書庫番", "training_master": "師範", "black_marketeer": "顔を隠した商人",
           "gravekeeper": "墓守", "mapmaker": "地図屋", "drunkard": "酔いどれ",
           "trade_keeper": "取引所の番", "stable_keeper": "厩舎の番", "guide": "都の案内人"}


# ====================================================================== 色

def ramp(base, n=5, lo=0.32, hi=1.32):
    """基準色から暗→明の n 段の階調を作る (暗い側は少し青へ寄せる)。"""
    out = []
    for k in range(n):
        t = lo + (hi - lo) * k / (n - 1)
        c = np.array(base, float) * t
        if t < 1:
            c = c + (np.array([8, 10, 22]) * (1 - t))
        else:
            c = c + (255 - c) * (t - 1) * 0.35
        out.append(tuple(int(v) for v in np.clip(c, 0, 255)))
    return out


SKINS = {
    "pale": (206, 168, 140), "mid": (186, 142, 108), "tan": (164, 116, 82), "brown": (128, 86, 60),
    "ruddy": (196, 136, 108), "old": (190, 156, 130),
}
# coat = 上着・衣の地 / role = 役の色 (前掛け・衣・肩掛け) / trim = 縁 / acc = 襟・釦 / hair = 髪 (頭の上の面。頭巾や布にもなる)
# guard = 前腕 / legs = 脚 / boot = 靴 / sash = 帯 (B・C)
PAL = {
    "guild_clerk":     dict(coat=(70, 84, 104), role=(176, 138, 58), trim=(60, 44, 30), acc=(196, 158, 76), hair=(86, 62, 44), skin="mid", guard=(92, 74, 56), sash=(120, 52, 40)),
    "smith":           dict(coat=(58, 56, 58), role=(122, 74, 46), trim=(30, 26, 24), acc=(170, 98, 52), hair=(32, 28, 28), skin="tan", guard=(46, 40, 38), sash=(90, 50, 30)),
    "tool_merchant":   dict(coat=(86, 96, 62), role=(170, 150, 108), trim=(92, 64, 40), acc=(178, 142, 70), hair=(104, 74, 48), skin="mid", guard=(110, 82, 54), sash=(120, 90, 50)),
    "apothecary":      dict(coat=(84, 110, 90), role=(196, 200, 172), trim=(60, 86, 52), acc=(120, 150, 96), hair=(140, 136, 128), skin="pale", guard=(84, 100, 76), sash=(70, 104, 70)),
    "storage_keeper":  dict(coat=(98, 76, 56), role=(170, 92, 46), trim=(70, 74, 80), acc=(120, 126, 134), hair=(60, 46, 36), skin="brown", guard=(80, 66, 52), sash=(70, 74, 80)),
    "innkeeper":       dict(coat=(122, 58, 56), role=(222, 210, 180), trim=(150, 62, 56), acc=(206, 170, 96), hair=(160, 60, 48), skin="ruddy", guard=(160, 120, 96), sash=(200, 150, 70), lady=True),
    "perch_keeper":    dict(coat=(98, 120, 140), role=(212, 218, 214), trim=(54, 118, 124), acc=(90, 160, 166), hair=(120, 92, 62), skin="tan", guard=(84, 98, 112), sash=(54, 118, 124)),
    "shrine_priest":   dict(coat=(222, 218, 206), role=(176, 56, 44), trim=(200, 160, 70), acc=(214, 176, 80), hair=(236, 232, 222), skin="old", guard=(204, 200, 188), sash=(176, 56, 44), legs=(150, 48, 40)),
    "keep_veteran":    dict(coat=(88, 92, 98), role=(124, 38, 38), trim=(150, 154, 160), acc=(170, 174, 180), hair=(196, 194, 188), skin="old", guard=(110, 112, 118), sash=(90, 30, 30)),
    "archivist":       dict(coat=(52, 58, 96), role=(200, 184, 140), trim=(36, 40, 72), acc=(176, 148, 82), hair=(118, 112, 110), skin="pale", guard=(66, 70, 104), sash=(110, 90, 60)),
    "training_master": dict(coat=(214, 206, 184), role=(48, 60, 104), trim=(28, 30, 40), acc=(40, 44, 60), hair=(40, 34, 32), skin="tan", guard=(196, 188, 168), sash=(28, 30, 40), legs=(44, 54, 92)),
    "black_marketeer": dict(coat=(36, 34, 40), role=(68, 44, 68), trim=(22, 20, 26), acc=(120, 104, 70), hair=(28, 26, 32), skin="mid", guard=(42, 40, 46), sash=(58, 36, 58), mask=(70, 58, 78)),
    "gravekeeper":     dict(coat=(92, 92, 88), role=(74, 86, 70), trim=(50, 52, 50), acc=(150, 146, 132), hair=(176, 174, 168), skin="pale", guard=(72, 72, 70), sash=(56, 60, 54)),
    "mapmaker":        dict(coat=(54, 104, 112), role=(196, 172, 122), trim=(40, 52, 76), acc=(186, 146, 70), hair=(150, 96, 56), skin="mid", guard=(70, 96, 100), sash=(150, 70, 50)),
    "drunkard":        dict(coat=(110, 92, 70), role=(122, 62, 78), trim=(76, 60, 46), acc=(140, 120, 80), hair=(96, 76, 58), skin="ruddy", guard=(100, 84, 66), sash=(98, 70, 50), drunk=True),
    "trade_keeper":    dict(coat=(44, 84, 82), role=(196, 160, 82), trim=(58, 40, 28), acc=(214, 184, 104), hair=(70, 52, 40), skin="mid", guard=(52, 72, 70), sash=(150, 110, 50)),
    "stable_keeper":   dict(coat=(116, 84, 56), role=(92, 120, 76), trim=(70, 48, 30), acc=(204, 172, 104), hair=(104, 76, 52), skin="tan", guard=(86, 70, 52), sash=(92, 120, 76)),
    "guide":           dict(coat=(232, 230, 222), role=(242, 240, 234), trim=(196, 162, 78), acc=(206, 170, 90), hair=(70, 52, 40), skin="mid", guard=(214, 210, 200), sash=(70, 108, 150)),
}

LEATHER = CK.BROWN
BOOT = [(16, 12, 10), (30, 24, 20), (52, 42, 36)]
LEGS = [(20, 18, 22), (38, 34, 40), (60, 56, 62)]


def pal_for(role):
    p = PAL[role]
    return {"mats": {
        "cloak": mat(ramp(p["coat"]), 0.66, crack=0.0, noise=0.12),
        "apron": mat(ramp(p["role"]), 0.64, crack=0.1, noise=0.08),
        "role": mat(ramp(p["role"]), 0.64, crack=0.0, noise=0.1),
        "trim": mat(ramp(p["trim"], 4), 0.7, crack=0.0),
        "acc": mat(ramp(p["acc"], 4), 0.66, crack=0.0),
        "sash": mat(ramp(p["sash"], 4), 0.66, crack=0.0, noise=0.08),
        "guard": mat(ramp(p["guard"]), 0.62, crack=0.04, noise=0.12),
        "skin": mat(ramp(SKINS[p["skin"]], 5, 0.36, 1.2), 0.72, crack=0.0, noise=0.05),
        "hair": mat(ramp(p["hair"], 4, 0.4, 1.25), 0.6, crack=0.0),
        "leather": mat(LEATHER, 0.62, crack=0.05),
        "cord": mat(ramp(p["legs"], 4, 0.35, 1.1) if "legs" in p else LEGS, 0.55, crack=0.0),
        "boot": mat(BOOT, 0.6, crack=0.0),
    }}


# ====================================================================== 顔 (頭の前の面に描く)

def face_paint(atlas, faces, P, N, M, role):
    fi = np.where(M >= 0, M, 0)
    bones = np.array([f.bone for f in faces])[fi]
    mats = np.array([f.mat for f in faces])[fi]
    dy, dz = ART.FACE["dy"], ART.FACE["dz"]
    x = np.abs(P[..., 0])
    y = P[..., 1]
    z = P[..., 2]
    p = PAL[role]
    skin = (bones == "head") & (M >= 0) & (mats == "skin")
    front = skin & (N[..., 2] < -0.25) & (y > 21.5 + dy) & (z < -2.0 + dz)
    eye = front & ((((x - 1.35) / 0.85) ** 2 + ((y - (26.7 + dy)) / 0.62) ** 2) < 1.0)
    iris = eye & ((((x - 1.35) / 0.42) ** 2 + ((y - (26.7 + dy)) / 0.42) ** 2) < 1.0)
    brow = front & (x > 0.6) & (x < 2.6) & (np.abs(y - (28.4 + dy - 0.2 * (x - 0.6))) < 0.3)
    mouth = front & (x < 1.0) & (np.abs(y - (24.1 + dy)) < 0.24)
    out = atlas.copy()
    hair = np.array(p["hair"]) * 0.55
    out[eye, :3] = (34, 22, 20)
    out[iris, :3] = (92, 62, 40)
    out[brow, :3] = np.clip(hair, 20, 200).astype(int)
    if p.get("lady"):
        lash = front & (x > 0.4) & (x < 2.4) & (np.abs(y - (27.4 + dy)) < 0.22)
        out[lash, :3] = (30, 20, 22)
        out[mouth, :3] = (150, 50, 52)
    else:
        out[mouth, :3] = (74, 36, 32)
    if p.get("drunk"):
        # 鼻と頬を赤く (鼻の先・頬の丸)
        nose = skin & (z < -3.0 + dz) & (x < 0.7) & (np.abs(y - (25.8 + dy)) < 1.0)
        cheek = front & ((((x - 1.9) / 0.8) ** 2 + ((y - (25.4 + dy)) / 0.55) ** 2) < 1.0)
        out[nose | cheek, :3] = (np.array(out[nose | cheek, :3], float) * 0.55 + np.array([200, 70, 62]) * 0.45).astype(np.uint8)
    if p.get("mask"):
        # 鼻から下を覆う布 (顔の前と頬・首の前まで)。上の縁は少し暗く
        m = np.array(p["mask"], float)
        cover = (bones == "head") & (M >= 0) & (mats == "skin") & (y < 25.9 + dy) & (z < 0.6 + dz)
        shade = np.clip(0.75 + 0.35 * (-N[..., 2]) + 0.1 * N[..., 1], 0.5, 1.15)
        col = np.clip(m[None, None, :] * shade[..., None], 0, 255)
        bay = CK.BAYER[np.arange(out.shape[0])[:, None] % 4, np.arange(out.shape[1])[None, :] % 4] - 0.5
        col = col * (1 + 0.08 * bay[..., None])
        out[cover, :3] = col[cover].astype(np.uint8)
        top = cover & (y > 25.3 + dy)
        out[top, :3] = (np.array(out[top, :3], float) * 0.7).astype(np.uint8)
    return out


# ====================================================================== 部品

def arm(side, up=1.6, fore=1.45, hand=1.25, m_up="cloak", m_fore="guard", sh=None, el=None, wr=None, cuff=None):
    b = "armL" if side > 0 else "armR"
    sh = sh or (side * 4.8, 20.6, 0.0)
    el = el or (side * 5.2, 16.0, -0.7)
    wr = wr or (side * 5.1, 12.4, -2.6)
    F = []
    F += part(b + "u", tube(b, m_up, [sh, el], ra=[up, up * 0.92], sides=5))
    F += part(b + "f", tube(b, m_fore, [el, wr], ra=[fore, fore * 0.85], sides=5))
    if cuff:
        # 袖口の帯 (前腕の手首寄りに少し太く)
        c0 = np.array(el) * 0.3 + np.array(wr) * 0.7
        c1 = np.array(el) * 0.12 + np.array(wr) * 0.88
        F += part(b + "c", tube(b, cuff, [tuple(c0), tuple(c1)], ra=[fore * 0.98, fore * 0.92], sides=5))
    F += part(b + "h", ellipsoid(b, "skin", (wr[0], wr[1] - 0.8, wr[2] - 0.2), (hand, 1.2, 1.0), segs=4, rings=2))
    return F


def legs(top=12.6, boot_r=1.6, lr=(1.7, 1.55, 1.45)):
    F = []
    for s in (-1, 1):
        b = "legL" if s > 0 else "legR"
        F += part(b, tube(b, "cord", [(s * 2.0, top, 0), (s * 2.2, min(6.0, top - 1.0), 0), (s * 2.2, 2.3, -0.1)],
                          ra=list(lr), sides=5))
        F += part(b + "b", ellipsoid(b, "boot", (s * 2.2, 1.6, -0.7), (boot_r, 1.45, 2.6), segs=5, rings=2))
    return F


def hair_cap(dy=0.0):
    return part("hair", ellipsoid("head", "hair", (0, 28.6 + dy, 0.9), (3.7, 2.3, 3.2), segs=8, rings=3))


def pouch():
    # 腰の袋 (右の腰。帯の下に下げる。全役で同じ)
    return part("pouch", ellipsoid("body", "leather", (4.6, 9.4, -1.6), (0.8, 1.1, 0.75), segs=5, rings=2))


# ====================================================================== 案 A 上着と前掛け (旅の職人 B の体)

def build_a():
    F = ART.head_std()
    F += hair_cap()
    F += part("jacket", loft("body", "cloak", [ring(9.0, 4.6, -2.9, 2.9), ring(13.0, 5.2, -3.1, 3.0),
                                                ring(17.5, 5.8, -3.3, 3.0), ring(21.0, 5.6, -3.0, 2.7),
                                                ring(23.0, 4.4, -2.3, 2.3)], caps=(False, "cloak")))
    F += part("apron", loft("body", "apron", [ring(9.4, 4.1, -3.9, -3.4), ring(17.6, 4.6, -3.7, -3.1)],
                            caps=("apron", "apron")))
    F += part("strap_l", tube("body", "leather", [(-3.9, 17.8, -3.4), (-2.6, 21.2, -2.6), (-2.4, 22.4, -2.4)], ra=[0.3] * 3, sides=4))
    F += part("strap_r", tube("body", "leather", [(3.9, 17.8, -3.4), (2.6, 21.2, -2.6), (2.4, 22.4, -2.4)], ra=[0.3] * 3, sides=4))
    F += part("belt", loft("body", "leather", [ring(10.0, 5.0, -3.2, 3.2), ring(11.0, 5.0, -3.2, 3.2)]))
    F += part("trim_b", tube("body", "trim", [(-4.0, 9.5, -4.0), (0, 9.4, -4.05), (4.0, 9.5, -4.0)], ra=[0.22] * 3, sides=4))
    F += part("trim_l", tube("body", "trim", [(-3.9, 9.6, -3.95), (-4.1, 17.4, -3.6)], ra=[0.2] * 2, sides=4))
    F += part("trim_r", tube("body", "trim", [(3.9, 9.6, -3.95), (4.1, 17.4, -3.6)], ra=[0.2] * 2, sides=4))
    # 前掛けの胸当ての縁 (上の帯)
    F += part("trim_t", tube("body", "trim", [(-4.4, 17.5, -3.75), (0, 17.6, -3.85), (4.4, 17.5, -3.75)], ra=[0.2] * 3, sides=4))
    F += part("hem_b", loft("body", "trim", [ring(8.8, 4.7, -3.0, 3.0), ring(9.6, 4.8, -3.05, 3.05)]))
    F += part("collar", loft("body", "acc", [ring(22.5, 4.8, -2.6, 2.5), ring(23.3, 4.3, -2.2, 2.2)]))
    for y, z in ((18.6, -3.3), (21.2, -2.85)):
        F += part("button", ellipsoid("body", "acc", (0, y, z), (0.36, 0.36, 0.22), segs=4, rings=1))
    F += pouch()
    F += arm(-1, cuff="trim")
    F += arm(1, cuff="trim")
    F += legs()
    return F


# ====================================================================== 案 B 長い衣と帯

def build_b():
    F = ART.head_std(neck_y=22.8)
    F += hair_cap(-0.1)
    # 髷 (頭の後ろの小さな結い。髪の色なので頭巾や布にも見える)
    F += part("knot", ellipsoid("head", "hair", (0, 28.4, 3.6), (1.2, 1.1, 1.0), segs=5, rings=2))
    # 衣: 肩から脛まで。裾は少し広がる。裾の面で閉じる (下から中が見えない)
    F += part("robe", loft("body", "role", [ring(5.0, 5.0, -3.6, 3.6), ring(8.0, 4.7, -3.3, 3.3),
                                             ring(12.0, 4.7, -3.1, 3.0), ring(17.0, 5.4, -3.2, 3.0),
                                             ring(21.0, 5.4, -2.9, 2.7), ring(23.0, 4.2, -2.2, 2.2)],
                                       caps=("role", "role")))
    # 裾の縁と、前の合わせ (右前に斜め)
    F += part("hem", loft("body", "trim", [ring(4.7, 5.08, -3.68, 3.68), ring(5.6, 5.02, -3.62, 3.62)]))
    F += part("lapel", tube("body", "trim", [(-2.4, 22.5, -2.55), (0.4, 18.6, -3.32), (1.6, 15.0, -3.35)], ra=[0.24] * 3, sides=4))
    F += part("collar", loft("body", "acc", [ring(22.4, 4.7, -2.6, 2.5), ring(23.2, 4.2, -2.25, 2.25)]))
    # 幅の広い帯と、前の結び目 (垂れ)
    F += part("sash", loft("body", "sash", [ring(11.2, 4.85, -3.25, 3.15), ring(13.4, 4.85, -3.25, 3.15)]))
    F += part("sash_knot", ellipsoid("body", "sash", (-1.6, 12.3, -3.5), (0.9, 0.8, 0.5), segs=5, rings=2))
    F += part("sash_tail", tube("body", "sash", [(-1.6, 11.6, -3.6), (-1.9, 9.6, -3.75), (-2.1, 8.4, -3.7)], ra=[0.5, 0.45, 0.4], sides=4))
    F += pouch()
    # 袖は衣の色の広い筒、前腕も衣 (袖口は縁の色)
    F += arm(-1, up=1.7, fore=1.55, hand=1.15, m_up="role", m_fore="role", cuff="trim")
    F += arm(1, up=1.7, fore=1.55, hand=1.15, m_up="role", m_fore="role", cuff="trim")
    # 脚は衣の裾の下だけ (付け根のピボットは同じ 12.4。歩くと裾の下で脛と靴が振れる)
    F += legs(top=5.4, boot_r=1.55, lr=(1.35, 1.3, 1.3))
    return F


# ====================================================================== 案 C 短い上着と肩掛け (背に下ろした頭巾)

def build_c():
    F = ART.head_std()
    F += hair_cap()
    F += part("coat", loft("body", "cloak", [ring(8.2, 4.8, -3.1, 3.1), ring(12.0, 4.8, -3.0, 3.0),
                                              ring(16.5, 5.2, -3.1, 2.9), ring(20.0, 5.3, -2.9, 2.7),
                                              ring(22.8, 4.3, -2.3, 2.3)], caps=(False, "cloak")))
    F += part("hem", loft("body", "trim", [ring(7.9, 4.88, -3.18, 3.18), ring(8.8, 4.85, -3.15, 3.15)]))
    F += part("belt", loft("body", "leather", [ring(10.4, 4.9, -3.1, 3.1), ring(11.3, 4.9, -3.1, 3.1)]))
    F += part("buckle", ellipsoid("body", "acc", (0, 10.85, -3.25), (0.55, 0.5, 0.25), segs=4, rings=1))
    # 前の釦の列
    for y in (13.2, 15.6, 18.0):
        F += part("button", ellipsoid("body", "acc", (0, y, -3.12), (0.32, 0.32, 0.2), segs=4, rings=1))
    # 肩掛け (肩から胸の半ばまでの短い外套。役の色)
    F += part("mantle", loft("body", "role", [ring(16.4, 6.4, -3.9, 3.8), ring(18.6, 6.4, -3.8, 3.7),
                                               ring(21.2, 5.9, -3.4, 3.4), ring(23.2, 4.6, -2.6, 2.6)],
                                         caps=("role", "role")))
    F += part("mantle_hem", loft("body", "trim", [ring(16.0, 6.45, -3.95, 3.85), ring(16.7, 6.48, -3.98, 3.88)]))
    # 背へ下ろした頭巾 (首の後ろのたまり)
    F += part("hood", ellipsoid("body", "role", (0, 22.6, 3.4), (3.4, 2.0, 1.9), segs=6, rings=2))
    F += part("clasp", ellipsoid("body", "acc", (0, 22.0, -2.75), (0.5, 0.5, 0.28), segs=4, rings=1))
    F += pouch()
    F += arm(-1, up=1.55, fore=1.4, hand=1.2, cuff="trim")
    F += arm(1, up=1.55, fore=1.4, hand=1.2, cuff="trim")
    F += legs()
    return F


LOOKS = {"a": build_a, "b": build_b, "c": build_c}
LOOK_JA = {"a": "A 上着と前掛け", "b": "B 長い衣と帯", "c": "C 短い上着と肩掛け"}
REC = "a"
POSES = {"idle": {"head": (-2, 0, 0)}, "talk": {"armR": (62, 0, -14), "head": (-4, 10, 0)}}


# ====================================================================== 作る

def build_look(k):
    faces = LOOKS[k]()
    d = OUT / k
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(faces, 256, 0)
    atlases = {}
    for role in ROLES:
        img, glow = RA_PAINT(faces, size, P, N, M, pal_for(role), ())
        img = face_paint(img, faces, P, N, M, role)
        atlases[role] = img
        Image.fromarray(img, "RGBA").save(d / f"attendant_{k}_{role}_atlas.png")
    # 空の光る画素のマスク (入れない。ラフの道具が glow を探すときのため)
    data = export_mesh(faces, size, d / f"attendant_{k}_mesh.json", f"attendant_{k}", f"attendant_{k}_guild_clerk_atlas.png", BONES)
    bad = check_buried(faces)
    glow = np.zeros((size, size), bool)
    return dict(key=k, faces=faces, size=size, atlases=atlases, glow=glow, data=data, buried=bad, tris=data["triangle_count"])


def view(r, role, yaw=212, pitch=12, size=(220, 330), pose="idle", center=(0, 15.0, 0), scale=10.0):
    return render_view(r, r["atlases"][role], r["glow"], POSES[pose], BONES, yaw, pitch, size, center, scale, ref=False)


def contact(r):
    """15 役の前 3/4 を 5 x 3 に並べる。"""
    F = voxel.font
    cols, PW, PH, GAP = 5, 220, 330, 10
    W = 20 * 2 + cols * PW + (cols - 1) * GAP
    rows = 3
    H = 96 + rows * (PH + 50) + 10
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((24, 14), f"都の施設の番人 — {LOOK_JA[r['key']]} (15 役・前 3/4)", font=F(26), fill=CK.WH)
    d.text((24, 52), f"形・UV は 15 役で同じ ({r['tris']} 三角形, アトラス {r['size']}x{r['size']})。違いは色だけ。顔を隠した商人は鼻から下を布の色で塗る。",
           font=F(15), fill=CK.GR)
    for i, role in enumerate(ROLES):
        x = 20 + (i % cols) * (PW + GAP)
        y = 96 + (i // cols) * (PH + 50)
        cv.paste(view(r, role), (x, y))
        d.rectangle([x - 1, y - 1, x + PW, y + PH], outline=(60, 60, 78))
        d.text((x + 2, y + PH + 4), ROLE_JA[role], font=F(15), fill=CK.CY)
        d.text((x + 2, y + PH + 24), role, font=F(13), fill=CK.GR)
    return cv


CMP_ROLES = ["guild_clerk", "smith", "innkeeper", "shrine_priest", "black_marketeer"]


def compare(results):
    F = voxel.font
    PW, PH, GAP = 200, 300, 8
    cols = len(CMP_ROLES)
    W = 180 + cols * (PW + GAP) + 10
    H = 90 + len(results) * (PH + 16) + 10
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((24, 14), "都の施設の番人 — 3 案の比べ (前 3/4)", font=F(26), fill=CK.WH)
    for j, role in enumerate(CMP_ROLES):
        d.text((180 + j * (PW + GAP) + 4, 60), ROLE_JA[role], font=F(16), fill=CK.CY)
    for i, r in enumerate(results):
        y = 90 + i * (PH + 16)
        d.text((14, y + PH // 2 - 20), LOOK_JA[r["key"]].replace(" ", "\n", 1), font=F(18), fill=CK.CY)
        d.text((14, y + PH // 2 + 30), f"{r['tris']} 三角形", font=F(14), fill=CK.GR)
        for j, role in enumerate(CMP_ROLES):
            x = 180 + j * (PW + GAP)
            cv.paste(view(r, role, size=(PW, PH), scale=9.0), (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + PH], outline=(60, 60, 78))
    return cv


def detail(r):
    """3 役 (書記・女将・顔を隠した商人) を前・横・後ろ 3/4・話す で。ck の sheet を使う。"""
    roles = ["guild_clerk", "innkeeper", "black_marketeer"]
    res = [dict(faces=r["faces"], atlas=r["atlases"][x], glow=r["glow"], size=r["size"], tris=r["tris"]) for x in roles]
    spec = dict(title=f"都の施設の番人 — {LOOK_JA[r['key']]} の姿", labels=[ROLE_JA[x] for x in roles],
                sub=["同じメッシュ・同じ UV。役ごとのアトラスだけを替える。骨とピボットは旅の職人と同じ。",
                     "左の黄の目盛りは人 (1.8 ブロック) の高さ。"],
                rows=[("前から", "idle", 180, 6, 400, (0, 14.5, 0), 12.0),
                      ("横", "idle", 270, 6, 400, (0, 14.5, 0), 12.0),
                      ("後ろ 3/4", "idle", 32, 12, 400, (0, 14.5, 0), 12.0),
                      ("話す (前 3/4)", "talk", 212, 12, 400, (0, 14.5, 0), 12.0),
                      ("顔の近く", "idle", 196, 0, 260, (0, 26.0, -2.0), 36.0)],
                pw=380, atlas_h=280)
    sheet(spec, res, BONES, POSES, OUT / r["key"] / f"attendant_{r['key']}_detail.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    report = {}
    for k in "abc":
        r = build_look(k)
        results.append(r)
        contact(r).save(OUT / f"look_{k}.png")
        detail(r)
        report[k] = dict(tris=r["tris"], atlas=r["size"], buried=r["buried"],
                         bones={b: v["triangle_count"] for b, v in r["data"]["bones"].items()})
        print("look", k, "tris", r["tris"], "atlas", r["size"], "buried", r["buried"])
    compare(results).save(OUT / "compare.png")
    (OUT / "attendants_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
