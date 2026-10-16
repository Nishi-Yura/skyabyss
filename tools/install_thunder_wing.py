"""雷翼の巨神 (thunder_wing) の採用案 A 嵐の大鷲をゲームに入れる (GeckoLib の geo、テクスチャ、アニメーション)。

使い方 (リポジトリ直下で): python tools/install_thunder_wing.py
形の元: tools/rough/thunder_wing_titan.py の build("a") (格子はそのまま使う)。関数は変えない。
        この版で、骨の番号を振り直す (首・頭・冠・手・脚・門・遺跡を別の骨にする)、左右を反転する (左 = geo の +X)、
        近景は大きな面を 2 単位の段にそろえる (手・頭・冠・門・遺跡・尾羽の先は 0.5 単位の細かさを残す)。
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/thunder_wing.geo.json        近景。0.5 単位の格子 (2 px/単位)。大きな面は 2 単位の段
  geo/entity/thunder_wing_mid.geo.json    中景。4 単位の格子 (1 px/単位)
  geo/entity/thunder_wing_far.geo.json    遠景。6 単位の格子 (0.5 px/単位)。薄い骨は消さず、離れ島は組ごとに直す
  textures/entity/thunder_wing.png / thunder_wing_glowmask.png   3 つの LOD で共有する 1 枚 (幅 2048)
  animations/entity/thunder_wing.animation.json
確認用 (docs/review/thunder_wing_roughs/install/): thunder_wing_lods.png, thunder_wing_poses.png, thunder_wing_far_read.png
地形の寸法 (手の位置など) は tools/sources/thunder_wing_landmarks.json に書く。

ゲームでの向き (岩鯨・潮殻と同じ決まり): 格子は X を反転してから箱にする。GeckoLib は X を反転して描くので、
geo の +X がゲームの左わき。左の翼・左の手首の瘤 (白青)・左の脚は geo の +X。頭は -Z。
骨: root (空の親)、body (胴)、ruins (風の民の塔・天窓・石段)、gate (羽根の門)、neck (首)、head (頭)、crest (銅の冠)、
wing_l / hand_l、wing_r / hand_r (hand = 手首から先の風切り)、tail (尾羽)、leg_l / leg_r (脚と爪)。
骨の軸 (pivot) はモデルの単位 (geo の単位。1 単位 = 0.5 ブロック)。

箱を減らす方法 (潮殻と同じ): 箱は材料ではなく骨ごとに合わせる。骨ごとに 6 通りの軸の順で貪欲に合わせ、一番少ないものを使う。
隠れた面は uv を書かない。格子の段は LOD ごとに 2 倍ずつ粗くする。光る材料は、少しでもあれば残す。
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/rough"))
import thunder_wing_titan as T  # noqa: E402  (格子と材料の関数だけを使う。thunder_wing_titan.py は変えない)
from voxel import Face, FACE_SHADE, load_geo, render, sheet  # noqa: E402

RES = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW_DIR = ROOT / "docs/review/thunder_wing_roughs/install"
LANDMARKS = ROOT / "tools/sources/thunder_wing_landmarks.json"

CELL = T.CELL
ATLAS_W = 2048
MAT = T.MAT
MAT_NAME = T.MAT_NAME
GLOW_MATS = [MAT["vein"], MAT["eye"], MAT["gate"], MAT["knob_w"], MAT["knob_t"], MAT["bolt"], MAT["glass"]]
# 稲妻の筋 (vein, bolt) は多いと翼全体が光って見えるので、粗い段では多く含むときだけ残す。
# 目・門・手首の瘤・ガラスは少しでも含めば残す (遠くでも顔と門と瘤が光る)
GLOW_STRONG = [MAT["vein"], MAT["bolt"]]
GLOW_RARE = [MAT["eye"], MAT["gate"], MAT["knob_w"], MAT["knob_t"], MAT["glass"]]
GLOW_MIN = {1: 2, 8: 24, 12: 32}          # 粗い格子で、稲妻の筋をこの数だけ含む段は光らせる
GLOW_RARE_MIN = {1: 1, 8: 4, 12: 6}        # 目・門・瘤は、この数だけ含めば光る段として残す

# 骨の番号 (格子の中の値は 骨 * 32 + 材料)
BONE = {"body": 1, "ruins": 2, "gate": 3, "neck": 4, "head": 5, "crest": 6, "wing_l": 7, "hand_l": 8,
        "wing_r": 9, "hand_r": 10, "tail": 11, "leg_l": 12, "leg_r": 13}
BONE_NAME = {v: k for k, v in BONE.items()}
BONE_ORDER = ["body", "ruins", "gate", "neck", "head", "crest", "wing_l", "hand_l", "wing_r", "hand_r",
              "tail", "leg_l", "leg_r"]
PARENT = {"body": "root", "ruins": "body", "gate": "body", "neck": "body", "head": "neck", "crest": "head",
          "wing_l": "body", "hand_l": "wing_l", "wing_r": "body", "hand_r": "wing_r", "tail": "body",
          "leg_l": "body", "leg_r": "body"}
NB = len(BONE)
# 手・頭・冠・門・遺跡・首は細いか顔なので、粗い段でも残りやすくする
THIN_BONES = {BONE["neck"], BONE["crest"], BONE["hand_l"], BONE["hand_r"], BONE["tail"], BONE["leg_l"], BONE["leg_r"]}
# 近景で 2 単位の段にそろえる骨 (頭・冠・手・門・遺跡・首は 0.5 単位の細かさを残す)
SNAP_BONES = [BONE["body"], BONE["neck"], BONE["head"], BONE["wing_l"], BONE["wing_r"], BONE["tail"],
              BONE["leg_l"], BONE["leg_r"]]
# 組ごとに離れ島を見る (同じ組の中だけでつながっているか)
GROUPS = {"static": [1, 2, 3, 4], "head": [5, 6], "tail": [11], "wing_l": [7, 8], "wing_r": [9, 10],
          "leg_l": [12], "leg_r": [13]}

# 骨の軸 (geo の単位)。手と翼は手首・肩で回す。頭は首の上。左 = +X
PIVOT = {"root": [0, 0, 0], "body": [0, 40, 0], "ruins": [0, 40, 0], "gate": None, "neck": [0, 44, -48],
         "head": [0, 51, -80], "crest": [0, 51, -80], "wing_l": [28, 44, 0], "hand_l": None,
         "wing_r": [-28, 44, 0], "hand_r": None, "tail": [0, 42, 56], "leg_l": None, "leg_r": None}

LODS = [("near", 1, 2.0), ("mid", 8, 1.0), ("far", 12, 0.5)]   # 名前, 格子の倍率, 1 単位あたりの画素
FACE_AXIS = {"west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1)}
FILES = {"near": "thunder_wing.geo.json", "mid": "thunder_wing_mid.geo.json", "far": "thunder_wing_far.geo.json"}
ARRAY_SHAPE = None


# ---------------------------------------------------------------- 格子

def build_grid():
    """格子を作り、骨の番号に直して X を反転する。戻り値は (骨 * 32 + 材料) の配列 (int16) と、骨の位置の情報"""
    P = T.build("a")
    K = T.K
    # 首の管は head の一部。最初の関数 (首の管) だけ、別の一時の格子に彫って、どのセルが首かを調べる
    tmp = np.zeros_like(K)
    T.K = tmp
    fn, bx, _ = T.head_parts(P, "a")[0]
    T.fill(fn, bx, T.PART["head"], mode="set")
    T.K = K
    neck = (tmp // 32 == T.PART["head"]) & (K // 32 == T.PART["head"])
    del tmp
    # 門 = 遺跡の中で、中央の狭い所 (羽根の門の枠と面)
    zg = P["body_z1"] - 22
    X = T.X0 + (np.arange(K.shape[0]) + 0.5) / CELL
    Z = T.Z0 + (np.arange(K.shape[2]) + 0.5) / CELL
    gate_x = (np.abs(X) <= 10.5)
    gate_z = (np.abs(Z - zg) <= 3.5)
    gate = (K // 32 == T.PART["ruins"]) & gate_x[:, None, None] & gate_z[None, None, :]
    # 左右の脚の番号 (反転の前の格子で、どちらの脚かを決める)
    leg = K // 32 == T.PART["legs"]
    # X を反転する (X0 = -X1 なので、番号を反転すれば世界の X も反転する)
    K = np.ascontiguousarray(K[::-1])
    neck = np.ascontiguousarray(neck[::-1])
    gate = np.ascontiguousarray(gate[::-1])
    leg = np.ascontiguousarray(leg[::-1])
    part = K // 32
    mat = K % 32
    # 反転後: 左 (+X) の脚は index が大きい側
    half = K.shape[0] // 2
    left_leg = np.zeros(K.shape, bool)
    left_leg[half:] = True
    code = part.astype(np.int16).copy()
    code[neck] = 40
    code[gate] = 41
    code[leg & left_leg] = 42
    code[leg & ~left_leg] = 43
    lut = np.zeros(64, np.int16)
    lut[T.PART["body"]] = BONE["body"]
    lut[T.PART["head"]] = BONE["head"]
    lut[40] = BONE["neck"]
    lut[T.PART["wing_l"]] = BONE["wing_l"]
    lut[T.PART["wing_r"]] = BONE["wing_r"]
    lut[T.PART["hand_l"]] = BONE["hand_l"]
    lut[T.PART["hand_r"]] = BONE["hand_r"]
    lut[T.PART["tail"]] = BONE["tail"]
    lut[42] = BONE["leg_l"]
    lut[43] = BONE["leg_r"]
    lut[T.PART["crest"]] = BONE["crest"]
    lut[T.PART["ruins"]] = BONE["ruins"]
    lut[41] = BONE["gate"]
    lut[T.PART["legs"]] = 0
    lut[T.PART["clouds"]] = 0
    G = np.where(K != 0, lut[code].astype(np.int16) * 32 + mat.astype(np.int16), 0).astype(np.int16)
    G[(K != 0) & (lut[code] == 0)] = 0
    print("grid", G.shape, "cells", int((G != 0).sum()))
    return G


def snap_blocks(K, parts, q):
    """骨ごとに、q x q x q セル (1 単位) の段にそろえる。
    ブロックの半分以上がその骨なら、ブロック全部をその骨にする (空いているセルだけ埋める)。
    そうでなければ、その骨のセルを空ける。段が 2 単位 (q=4) にそろうので箱が減る"""
    a, b, c = (s // q for s in K.shape)
    B = K.reshape(a, q, b, q, c, q).transpose(0, 2, 4, 1, 3, 5).reshape(a, b, c, q ** 3).copy()
    half = q ** 3 / 2
    for p in parts:
        m = (B // 32) == p
        if not m.any():
            continue
        full = m.sum(-1) >= half
        mat = np.where(m, B % 32, 0).max(-1).astype(np.int16)
        newkey = (p * 32 + mat).astype(np.int16)[..., None]
        fill = full[..., None] & (B == 0)
        B = np.where(fill, newkey, B).astype(np.int16)
        B = np.where(((~full)[..., None]) & m, np.int16(0), B).astype(np.int16)
        del m, fill
    K[...] = B.reshape(a, b, c, q, q, q).transpose(0, 3, 1, 4, 2, 5).reshape(K.shape)


def snap_body_tops(K, q):
    """胴の上面を、q セル (1 単位) の段にそろえる (0.5 単位の細かい段を消して箱を減らす)。
    上へ出す所は空いているセルだけ、下げる所は上に他の骨が無いときだけ削る"""
    occ = (K // 32) == BONE["body"]
    has = occ.any(1)
    top = K.shape[1] - 1 - np.argmax(occ[:, ::-1, :], axis=1)
    new = (np.round((top + 1) / q) * q - 1).astype(int)
    changed = 0
    for i, k in np.argwhere(has & (new != top)):
        t, n = int(top[i, k]), int(new[i, k])
        key = int(K[i, t, k])
        if n > t:
            seg = K[i, t + 1:n + 1, k]
            seg[seg == 0] = key
            changed += 1
        else:
            if np.any(K[i, t + 1:, k] != 0):
                continue
            K[i, n + 1:t + 1, k] = 0
            changed += 1
    print("snapped body columns", changed)


def downsample(K, f):
    """f x f x f のセルを 1 つにする。骨は多数決、材料は光る材料があれば優先し、なければ多数決。
    セルを残すかは骨ごとの割合 (細い骨は低い割合でも残す)。光る材料は GLOW_MIN 個あれば残す"""
    if f == 1:
        return K.copy()
    sh = [(-(-n // f)) * f for n in K.shape]
    P = np.zeros(sh, np.int16)
    P[:K.shape[0], :K.shape[1], :K.shape[2]] = K
    a, b, c = (s // f for s in sh)
    blk = P.reshape(a, f, b, f, c, f).transpose(0, 2, 4, 1, 3, 5).reshape(a, b, c, f ** 3)
    occ = np.argwhere((blk != 0).any(-1))
    cells = blk[occ[:, 0], occ[:, 1], occ[:, 2]]          # (N, f^3)
    part = cells // 32
    cnt_p = np.stack([(part == p).sum(1) for p in range(1, NB + 1)], 1)
    pm = cnt_p.argmax(1) + 1
    cp = cnt_p.max(1)
    pmask = (part == pm[:, None]) & (cells != 0)
    mat = cells % 32
    mc = np.stack([((mat == v) & pmask).sum(1) for v in range(32)], 1)
    strong_n = mc[:, GLOW_STRONG].sum(1)
    rare_n = mc[:, GLOW_RARE].sum(1)
    use_glow = (strong_n >= GLOW_MIN.get(f, 4)) | (rare_n >= GLOW_RARE_MIN.get(f, 1))
    glow_pick = np.array(GLOW_MATS)[mc[:, GLOW_MATS].argmax(1)]
    mc_plain = mc.copy()
    mc_plain[:, [0] + GLOW_MATS] = -1
    plain_pick = mc_plain.argmax(1)
    mat_out = np.where(use_glow, glow_pick, plain_pick)
    thr_thick = 0.5 if f <= 4 else 0.45
    thr_thin = 0.5 if f <= 4 else 0.3
    thr = np.where(np.isin(pm, list(THIN_BONES)), thr_thin, thr_thick) * f ** 3
    keep = (cp >= thr) | use_glow
    out = np.zeros((a, b, c), np.int16)
    sel = occ[keep]
    out[sel[:, 0], sel[:, 1], sel[:, 2]] = (pm * 32 + mat_out)[keep]
    return out


def stamp_gate(G, K, f):
    """中景・遠景で羽根の門が消えないように: 近景の門のセルが 1 つでも入る粗いセルは、門のセルにする。
    材料は、光る材料があれば光る材料、なければ枠の石の多数決。書き換えたセルの数を返す"""
    gm = (K // 32) == BONE["gate"]
    idx = np.argwhere(gm)
    if idx.size == 0:
        return 0
    coarse = {}
    for i, j, k in idx:
        coarse.setdefault((i // f, j // f, k // f), []).append(int(K[i, j, k] % 32))
    n = 0
    for (a, b, c), mats in coarse.items():
        if a >= G.shape[0] or b >= G.shape[1] or c >= G.shape[2]:
            continue
        glow = [m for m in mats if m in GLOW_MATS]
        mat = max(set(glow), key=glow.count) if glow else max(set(mats), key=mats.count)
        G[a, b, c] = BONE["gate"] * 32 + mat
        n += 1
    return n


def islands_fix(G, label):
    """組ごとに、一番大きい塊以外を消す (浮いた塊)。接している別の骨があれば、そちらへ移す。ログを返す"""
    P = G // 32
    log = []
    for name, parts in GROUPS.items():
        m = np.isin(P, parts)
        lab, n = ndimage.label(m)
        if n <= 1:
            continue
        sizes = ndimage.sum(m, lab, range(1, n + 1))
        big = int(np.argmax(sizes)) + 1
        for i in range(1, n + 1):
            if i == big:
                continue
            comp = lab == i
            ring = ndimage.binary_dilation(comp) & ~comp
            touch = G[ring] // 32
            touch = touch[(touch != 0) & ~np.isin(touch, parts)]
            if touch.size:
                vals, cnt = np.unique(touch, return_counts=True)
                to = int(vals[np.argmax(cnt)])
                G[comp] = to * 32 + G[comp] % 32
                log.append("%s %s: %d セルの塊を %s に移した" % (label, name, int(sizes[i - 1]), BONE_NAME[to]))
            else:
                G[comp] = 0
                log.append("%s %s: %d セルの浮いた塊を消した" % (label, name, int(sizes[i - 1])))
    return log


def islands(G):
    """離れ島の数 (組ごと)。空なら離れ島なし"""
    P = G // 32
    out = {}
    for name, parts in GROUPS.items():
        m = np.isin(P, parts)
        if m.any():
            n = ndimage.label(m)[1]
            if n > 1:
                out[name] = n - 1
    return out


# ---------------------------------------------------------------- 箱にまとめる (骨ごと)

def _runs(K):
    """K (a, b, c) を c の連続 -> b -> a の順に、同じキーで合わせる。[キー, a0, a1, b0, b1, c0, c1]"""
    A, B, C = K.shape
    pad = np.zeros((A, B, C + 2), K.dtype)
    pad[..., 1:-1] = K
    cur, prv, nxt = pad[..., 1:-1], pad[..., :-2], pad[..., 2:]
    occ = cur != 0
    st = np.argwhere(occ & (prv != cur))
    en = np.argwhere(occ & (nxt != cur))
    kk = cur[st[:, 0], st[:, 1], st[:, 2]].astype(np.int64)
    a, b, c0, c1 = st[:, 0], st[:, 1], st[:, 2], en[:, 2] + 1
    o = np.lexsort((b, a, c1, c0, kk))
    kk, a, b, c0, c1 = kk[o], a[o], b[o], c0[o], c1[o]
    cont = np.zeros(len(kk), bool)
    cont[1:] = (kk[1:] == kk[:-1]) & (c0[1:] == c0[:-1]) & (c1[1:] == c1[:-1]) & (a[1:] == a[:-1]) & (b[1:] == b[:-1] + 1)
    gs = np.flatnonzero(~cont)
    ge = np.r_[gs[1:], len(kk)] - 1
    kk, a, c0, c1, b0, b1 = kk[gs], a[gs], c0[gs], c1[gs], b[gs], b[ge] + 1
    o = np.lexsort((a, b1, b0, c1, c0, kk))
    kk, a, c0, c1, b0, b1 = kk[o], a[o], c0[o], c1[o], b0[o], b1[o]
    cont = np.zeros(len(kk), bool)
    cont[1:] = (kk[1:] == kk[:-1]) & (c0[1:] == c0[:-1]) & (c1[1:] == c1[:-1]) & (b0[1:] == b0[:-1]) & \
        (b1[1:] == b1[:-1]) & (a[1:] == a[:-1] + 1)
    gs = np.flatnonzero(~cont)
    ge = np.r_[gs[1:], len(kk)] - 1
    return np.stack([kk[gs], a[gs], a[ge] + 1, b0[gs], b1[gs], c0[gs], c1[gs]], 1)


def mesh_best(K):
    """6 通りの軸の順で合わせて、箱の一番少ないものを返す (潮殻と同じ)"""
    from itertools import permutations
    best = None
    for perm in permutations(range(3)):
        r = _runs(np.ascontiguousarray(np.transpose(K, perm)))
        out = np.zeros_like(r)
        out[:, 0] = r[:, 0]
        for i, ax in enumerate(perm):
            out[:, 1 + 2 * ax] = r[:, 1 + 2 * i]
            out[:, 2 + 2 * ax] = r[:, 2 + 2 * i]
        if best is None or len(out) < len(best):
            best = out
    return best


def mesh_bones(G):
    """骨ごとに合わせる。[骨の番号, x0, x1, y0, y1, z0, z1] (格子の番号)"""
    P = G // 32
    boxes = []
    for p in sorted(set(np.unique(P).tolist()) - {0}):
        m = P == p
        idx = np.argwhere(m)
        lo, hi = idx.min(0), idx.max(0) + 1
        sl = tuple(slice(l, h) for l, h in zip(lo, hi))
        b = mesh_best(m[sl].astype(np.int16))
        b[:, 0] = p
        b[:, 1:3] += lo[0]
        b[:, 3:5] += lo[1]
        b[:, 5:7] += lo[2]
        boxes.append(b)
    return np.concatenate(boxes)


def face_exposed(G, p, box):
    """面の外側が、同じ骨の格子でないなら見える (範囲の外は見える)"""
    _, x0, x1, y0, y1, z0, z1 = (int(v) for v in box)
    lo, hi = [x0, y0, z0], [x1, y1, z1]
    res = {}
    for fname, (ax, d) in FACE_AXIS.items():
        a, b = list(lo), list(hi)
        if d < 0:
            a[ax], b[ax] = lo[ax] - 1, lo[ax]
        else:
            a[ax], b[ax] = hi[ax], hi[ax] + 1
        if a[ax] < 0 or b[ax] > G.shape[ax]:
            res[fname] = True
            continue
        nb = G[a[0]:b[0], a[1]:b[1], a[2]:b[2]] // 32
        res[fname] = bool(np.any(nb != p))
    return res


def face_px(cells, fname, px_per_cell):
    a, b, d = cells
    w, h = {"up": (a, d), "down": (a, d), "north": (a, b), "south": (a, b), "east": (d, b), "west": (d, b)}[fname]
    return max(1, int(round(w * px_per_cell))), max(1, int(round(h * px_per_cell)))


def build_lod(G, f, ppu):
    """1 つの LOD の箱。戻り値は (箱の一覧, 箱の数 (全部))"""
    unit = f * 0.5
    boxes = mesh_bones(G)
    cubes = []
    for bx in boxes:
        p, x0, x1, y0, y1, z0, z1 = (int(v) for v in bx)
        ex = face_exposed(G, p, bx)
        if not any(ex.values()):
            continue
        cells = (x1 - x0, y1 - y0, z1 - z0)
        o = (T.X0 + x0 * unit, T.Y0 + y0 * unit, T.Z0 + z0 * unit)
        s = (cells[0] * unit, cells[1] * unit, cells[2] * unit)
        faces = {fn: face_px(cells, fn, unit * ppu) for fn, vis in ex.items() if vis}
        cubes.append(dict(part=p, o=o, s=s, faces=faces, box=(x0, x1, y0, y1, z0, z1), f=f))
    return cubes, len(boxes)


# ---------------------------------------------------------------- テクスチャ (面の画素ごとに、裏のセルの材料)

def pack_width(items, W):
    """棚詰め。幅 W に入れ、高さは必要なだけ使う (画素の間は 1 画素あける)。items = [(id, w, h)]"""
    pos, x, y, shelf = {}, 1, 0, 0
    for k, w, h in sorted(items, key=lambda it: (-it[2], -it[1])):
        if x + w + 1 > W:
            x, y, shelf = 0, y + shelf + 1, 0
        pos[k] = (x, y)
        x += w + 1
        shelf = max(shelf, h)
    return pos


def paint_face(G, f, unit, cube, fname, w, h, tex, glow, x0, y0):
    """面の画素を、裏のセルの材料で塗る。戻り値は光る画素の数"""
    P = T.world_grid(cube["o"], cube["s"], fname, w, h)
    ax, d = FACE_AXIS[fname]
    Q = P.copy()
    Q[..., ax] -= d * unit * 0.5
    gi = np.clip(np.floor((Q[..., 0] - T.X0) * CELL / f).astype(int), 0, G.shape[0] - 1)
    gj = np.clip(np.floor((Q[..., 1] - T.Y0) * CELL / f).astype(int), 0, G.shape[1] - 1)
    gk = np.clip(np.floor((Q[..., 2] - T.Z0) * CELL / f).astype(int), 0, G.shape[2] - 1)
    keys = G[gi, gj, gk].astype(np.int64)
    keys[keys == 0] = BONE["body"] * 32 + MAT["back"]
    if fname in ("up", "down"):
        A, B = P[..., 0], P[..., 2]
    elif fname in ("north", "south"):
        A, B = P[..., 0], P[..., 1]
    else:
        A, B = P[..., 2], P[..., 1]
    A2 = np.floor(A * CELL).astype(np.int64)
    B2 = np.floor(B * CELL).astype(np.int64)
    out_t = np.zeros((h, w, 3), np.float64)
    out_g = np.zeros((h, w), bool)
    for key in np.unique(keys):
        mat = int(key % 32)
        sel = keys == key
        base, gm = T.mat_base(mat, A2, B2, P, fname)
        out_t[sel] = np.clip(base, 0, 255)[sel]
        out_g[sel] = (gm & sel)[sel]
    tex[y0:y0 + h, x0:x0 + w, :3] = out_t.astype(np.uint8)
    tex[y0:y0 + h, x0:x0 + w, 3] = 255
    gl = glow[y0:y0 + h, x0:x0 + w]
    gl[out_g, :3] = out_t[out_g].astype(np.uint8)
    gl[out_g, 3] = 255
    return int(out_g.sum())


# ---------------------------------------------------------------- geo と確認用

def make_geo(lod_cubes, uv_of, H, lod_name):
    bones = {name: [] for name in BONE_ORDER}
    for c in lod_cubes:
        uv = {fn: {"uv": list(uv_of[(c["id"], fn)]), "uv_size": list(sz)} for fn, sz in c["faces"].items()}
        bones[BONE_NAME[c["part"]]].append({"origin": [round(v, 3) for v in c["o"]],
                                            "size": [round(v, 3) for v in c["s"]], "uv": uv})
    bl = [{"name": "root", "pivot": PIVOT["root"]}]
    for name in BONE_ORDER:
        b = {"name": name, "pivot": piv[name]}
        b["parent"] = PARENT[name]
        if bones[name]:
            b["cubes"] = bones[name]
        bl.append(b)
    return {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry.thunder_wing", "texture_width": ATLAS_W, "texture_height": H,
        "visible_bounds_width": 480, "visible_bounds_height": 170, "visible_bounds_offset": [0, 50, 0]},
        "bones": bl}]}


piv = {}   # 骨の軸 (build_all で決める)


def cube_count(geo):
    return sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])


def quad_count(geo):
    return sum(len(c["uv"]) for b in geo["minecraft:geometry"][0]["bones"] for c in b.get("cubes", []))


def check_overlap(cubes):
    """箱を 0.5 単位の格子に戻して、2 個以上入るセルを数える (0 なら重なりなし)"""
    lo = np.min([c["o"] for c in cubes], 0)
    hi = np.max([np.add(c["o"], c["s"]) for c in cubes], 0)
    off = np.floor(lo * 2).astype(int)
    grid = np.zeros(np.ceil(hi * 2).astype(int) - off + 1, np.uint8)
    for c in cubes:
        a = np.round(np.array(c["o"]) * 2).astype(int) - off
        b = a + np.round(np.array(c["s"]) * 2).astype(int)
        grid[a[0]:b[0], a[1]:b[1], a[2]:b[2]] += 1
    return int((grid > 1).sum())


# ---------------------------------------------------------------- アニメーション (角度は度)
# 値: 骨ごとに {軸: ("sin", 中心, 振れ, 度/秒, 位相)} か {軸: ("key", [(時刻, 値), ...])}。
# 意図した角度 (左右対称の向きで書く。左 = +X は z が正で上がる、右 = -X は z が負で上がる)。
# GeckoLib は X と Z の符号を反転して回す (潮殻の実物で確かめた)。ファイルには (-X, Y, -Z) の符号で書く。

S = ("sin",)


def sn(c, A, f, p=0.0):
    return ("sin", c, A, f, p)


def ky(*pts):
    return ("key", list(pts))


ANIM = {
    "animation.thunder_wing.fly": (3.0, True, {
        "wing_l": {"z": sn(0, 28, 120)}, "wing_r": {"z": sn(0, -28, 120)},
        "hand_l": {"z": sn(0, 12, 120, -60)}, "hand_r": {"z": sn(0, -12, 120, -60)},
        "tail": {"y": sn(0, 3, 120)},
    }),
    "animation.thunder_wing.glide": (6.0, True, {
        "wing_l": {"z": sn(6, 3, 60)}, "wing_r": {"z": sn(-6, -3, 60)},
        "tail": {"y": sn(0, 3, 60)},
        "head": {"y": sn(0, 4, 60)},
    }),
    "animation.thunder_wing.perch": (8.0, True, {
        # 翼は後ろへ流して胴の脇にたたむ (y は後ろ向きに 88 度。左 (+X) は負、右 (-X) は正)
        "wing_l": {"y": sn(-88, 2, 45), "z": sn(-12, 0, 45)}, "wing_r": {"y": sn(88, -2, 45), "z": sn(12, 0, 45)},
        "hand_l": {"y": sn(-8, 0, 45)}, "hand_r": {"y": sn(8, 0, 45)},
        "head": {"y": sn(0, 3, 45)},
        "tail": {"y": sn(0, 2, 45, 90)},
    }),
    "animation.thunder_wing.land": (3.0, False, {
        "wing_l": {"z": ky((0.0, 0), (1.0, -25), (2.0, -55), (3.0, -75))},
        "wing_r": {"z": ky((0.0, 0), (1.0, 25), (2.0, 55), (3.0, 75))},
        "hand_l": {"z": ky((0.0, 0), (1.5, -20), (3.0, -35))},
        "hand_r": {"z": ky((0.0, 0), (1.5, 20), (3.0, 35))},
    }),
    "animation.thunder_wing.take_off": (3.0, False, {
        "wing_l": {"z": ky((0.0, -75), (1.0, -55), (1.6, 10), (2.2, 30), (3.0, 0))},
        "wing_r": {"z": ky((0.0, 75), (1.0, 55), (1.6, -10), (2.2, -30), (3.0, 0))},
        "hand_l": {"z": ky((0.0, -35), (1.0, -10), (2.0, 10), (3.0, 0))},
        "hand_r": {"z": ky((0.0, 35), (1.0, 10), (2.0, -10), (3.0, 0))},
        "head": {"x": ky((0.0, 0), (1.5, 0), (3.0, 0))},
    }),
    "animation.thunder_wing.cower": (4.0, True, {
        # 翼を前へ回して頭の上にかぶせる (y は前向き。左は正、右は負)
        "wing_l": {"y": sn(70, 3, 90), "z": sn(30, 0, 90)}, "wing_r": {"y": sn(-70, -3, 90), "z": sn(-30, 0, 90)},
        "hand_l": {"y": sn(20, 0, 90)}, "hand_r": {"y": sn(-20, 0, 90)},
        "head": {"y": sn(0, 5, 90)},
    }),
    "animation.thunder_wing.look": (8.0, True, {
        "neck": {"y": sn(0, 8, 45)},
        "head": {"y": sn(0, 20, 45)},
    }),
}
SIGN = {"x": -1, "y": 1, "z": -1}


def _num(v):
    return ("%.3f" % v).rstrip("0").rstrip(".") if abs(v) > 1e-9 else "0"


def molang_axis(axis, spec):
    _, c, A, f, p = spec
    s = SIGN[axis]
    parts = []
    if abs(c) > 1e-9:
        parts.append(_num(s * c))
    if abs(A) > 1e-9:
        parts.append("%s * math.sin(%s * query.anim_time + %s)" % (_num(s * A), _num(f), _num(p)))
    return " + ".join(parts) if parts else "0"


def key_value(t, pts):
    """キーの列 (時刻, 値) の、時刻 t での値 (両端は端の値で止める)"""
    if t <= pts[0][0]:
        return pts[0][1]
    for (t0, v0), (t1, v1) in zip(pts, pts[1:]):
        if t0 <= t <= t1:
            return v0 + (v1 - v0) * (t - t0) / (t1 - t0)
    return pts[-1][1]


def anim_json():
    out = {}
    for name, (length, loop, bones) in ANIM.items():
        bj = {}
        for bone, axes in bones.items():
            if any(v[0] == "key" for v in axes.values()):
                times = sorted({t for v in axes.values() if v[0] == "key" for t, _ in v[1]})
                rot = {}
                for t in times:
                    vec = []
                    for a in ("x", "y", "z"):
                        if a in axes and axes[a][0] == "key":
                            val = key_value(t, axes[a][1])
                        elif a in axes:
                            val = axes[a][1]
                        else:
                            val = 0.0
                        vec.append(round(SIGN[a] * val, 3))
                    rot["%.2f" % t] = {"vector": vec}
                bj[bone] = {"rotation": rot}
            else:
                rot = [molang_axis(a, axes[a]) if a in axes else "0" for a in ("x", "y", "z")]
                bj[bone] = {"rotation": rot}
        out[name] = {"loop": loop, "animation_length": length, "bones": bj}
    return {"format_version": "1.8.0", "animations": out}


def intended_pose(name, t):
    """時刻 t (秒) での、意図した回転 {骨: (x, y, z)} (度)。プレビュー用。循環するものは長さで割った余りで見る"""
    length, loop, bones = ANIM[name]
    if loop:
        t = t % length
    pose = {}
    for bone, axes in bones.items():
        v = [0.0, 0.0, 0.0]
        for i, a in enumerate(("x", "y", "z")):
            if a not in axes:
                continue
            spec = axes[a]
            if spec[0] == "sin":
                _, c, A, f, p = spec
                v[i] = c + A * math.sin(math.radians(f * t + p))
            else:
                v[i] = key_value(t, spec[1])
        pose[bone] = tuple(v)
    return pose


# ---------------------------------------------------------------- 確認用の絵

def mirror_faces(faces):
    """ゲームの向き (X を反転) にする。反転すると面の表裏が入れ替わるので、横の辺の向きを逆にし、画素も横に反転する"""
    m = np.array([-1, 1, 1])
    out = []
    for f in faces:
        g = None if f.glow is None else f.glow[:, ::-1]
        out.append(Face((f.origin + f.eu) * m, -f.eu * m, f.ev * m, f.tex[:, ::-1], g, f.shade))
    return out


def marker(center, size, color):
    """小さな色の箱 (印)。ゲームの向きの座標で置く"""
    c = np.array(center, float)
    h = np.array(size, float) / 2
    x0, y0, z0 = c - h
    x1, y1, z1 = c + h
    sx, sy, sz = 2 * h
    corners = {
        "north": ((x1, y0, z0), (-sx, 0, 0), (0, sy, 0)),
        "south": ((x0, y0, z1), (sx, 0, 0), (0, sy, 0)),
        "east": ((x1, y0, z1), (0, 0, -sz), (0, sy, 0)),
        "west": ((x0, y0, z0), (0, 0, sz), (0, sy, 0)),
        "up": ((x0, y1, z1), (sx, 0, 0), (0, 0, -sz)),
        "down": ((x0, y0, z0), (sx, 0, 0), (0, 0, sz)),
    }
    tex = np.array(color, np.uint8).reshape(1, 1, 4)
    return [Face(np.array(co, float), np.array(eu, float), np.array(ev, float), tex, None, FACE_SHADE[n])
            for n, (co, eu, ev) in corners.items()]


def preview(geos, tex, glow, counts):
    cols = []
    for name, label in (("near", "近景"), ("mid", "中景"), ("far", "遠景")):
        fs = mirror_faces(load_geo(geos[name], tex, glowmask=glow))
        cols.append(("%s %d 箱" % (label, counts[name]),
                     [render(fs, yaw=90, pitch=4, size=(400, 400)),
                      render(fs, yaw=35, pitch=35, size=(400, 400)),
                      render(fs, yaw=180, pitch=8, size=(400, 400)),
                      render(fs, yaw=20, pitch=-52, size=(400, 400))]))
    sht = sheet("雷翼の巨神 LOD (採用案 A 嵐の大鷲、ゲームの向き)", cols,
                note="行: 側面 / 斜め上 / 正面 / 真下から (地上から見上げる)。左 = 白青の瘤側。")
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out = PREVIEW_DIR / "thunder_wing_lods.png"
    sht.save(out)
    print("preview", out)


def preview_poses(geos, tex, glow):
    def posed(name, t):
        return mirror_faces(load_geo(geos["near"], tex, glowmask=glow, pose=intended_pose(name, t)))

    def trio(fs):
        return [render(fs, yaw=90, pitch=4, size=(360, 360)), render(fs, yaw=180, pitch=8, size=(360, 360)),
                render(fs, yaw=20, pitch=-52, size=(360, 360))]

    cols = [("fly (t=0.75s)", trio(posed("animation.thunder_wing.fly", 0.75))),
            ("perch (t=4s)", trio(posed("animation.thunder_wing.perch", 4.0))),
            ("land (t=1.5s)", trio(posed("animation.thunder_wing.land", 1.5))),
            ("cower (t=2s)", trio(posed("animation.thunder_wing.cower", 2.0)))]
    sht = sheet("雷翼の巨神 姿勢 (近景、ゲームの向き)", cols,
                note="行: 側面 / 正面 / 真下から。止まり木 (perch) で翼をたたむ。")
    out = PREVIEW_DIR / "thunder_wing_poses.png"
    sht.save(out)
    print("preview", out)


def preview_far(geos, tex, glow):
    """遠景の読みやすさ: 3 つの LOD を小さく (140px) 描き、嵐の空の色の上で見る"""
    cols = []
    for name in ("near", "mid", "far"):
        fs = mirror_faces(load_geo(geos[name], tex, glowmask=glow))
        cols.append((name, [render(fs, yaw=180, pitch=6, size=(140, 140), margin=0.04),
                            render(fs, yaw=20, pitch=-52, size=(140, 140), margin=0.04),
                            render(fs, yaw=90, pitch=4, size=(140, 140), margin=0.04)]))
    sht = sheet("雷翼の巨神 遠景の読みやすさ (140px)", cols,
                note="行: 正面 / 真下から / 側面。翼の薄い骨と、頭・首・尾が消えないかを見る。")
    out = PREVIEW_DIR / "thunder_wing_far_read.png"
    sht.save(out)
    print("preview", out)


# ---------------------------------------------------------------- 地形の寸法 (Java 側へ渡す値)

def landmarks(G, lods_bounds):
    """近景の格子から、骨の範囲と目印を出す (単位はモデルの単位。ブロックは 0.5 倍)"""
    def bounds_of(bone):
        idx = np.argwhere((G // 32) == BONE[bone])
        if idx.size == 0:
            return None
        lo = [T.X0 + idx[:, 0].min() / CELL, T.Y0 + idx[:, 1].min() / CELL, T.Z0 + idx[:, 2].min() / CELL]
        hi = [T.X0 + (idx[:, 0].max() + 1) / CELL, T.Y0 + (idx[:, 1].max() + 1) / CELL, T.Z0 + (idx[:, 2].max() + 1) / CELL]
        return [round(float(v), 2) for v in lo], [round(float(v), 2) for v in hi]

    def centre_of(mask_bone, mat):
        idx = np.argwhere(((G // 32) == BONE[mask_bone]) & ((G % 32) == MAT[mat]))
        if idx.size == 0:
            return None
        c = idx.mean(0)
        return [round(float(T.X0 + (c[0] + 0.5) / CELL), 2), round(float(T.Y0 + (c[1] + 0.5) / CELL), 2),
                round(float(T.Z0 + (c[2] + 0.5) / CELL), 2)]

    def side_centre(bone, mat, sign):
        idx = np.argwhere(((G // 32) == BONE[bone]) & ((G % 32) == MAT[mat]))
        if idx.size == 0:
            return None
        X = T.X0 + (idx[:, 0] + 0.5) / CELL
        sel = idx[(X * sign) > 0]
        if sel.size == 0:
            return None
        c = sel.mean(0)
        return [round(float(T.X0 + (c[0] + 0.5) / CELL), 2), round(float(T.Y0 + (c[1] + 0.5) / CELL), 2),
                round(float(T.Z0 + (c[2] + 0.5) / CELL), 2)]

    out = {}
    for name in BONE_ORDER:
        bb = bounds_of(name)
        if bb:
            out["bone_%s" % name] = {"min": bb[0], "max": bb[1]}
    out["eye_left"] = side_centre("head", "eye", 1)
    out["eye_right"] = side_centre("head", "eye", -1)
    out["knob_left_centre"] = side_centre("hand_l", "knob_w", 1)
    out["knob_right_centre"] = side_centre("hand_r", "knob_t", -1)
    out["head_centre"] = None
    hb = bounds_of("head")
    if hb:
        out["head_centre"] = [round((hb[0][i] + hb[1][i]) / 2, 2) for i in range(3)]
    out["gate_centre"] = centre_of("gate", "gate")
    out["bounds"] = lods_bounds
    return out


def write_landmarks(G, bnd):
    mn = bnd["near"]
    lm = landmarks(G, bnd)
    gb = lm.get("bone_gate")
    lm["gate_bottom_above_model_bottom"] = round(gb["min"][1] - mn["y"][0], 2) if gb else None
    lm["gate_top_above_model_bottom"] = round(gb["max"][1] - mn["y"][0], 2) if gb else None
    lm["left_foot_offset_from_centre"] = None
    lf = None
    idx = np.argwhere(((G // 32) == BONE["leg_l"]))
    if idx.size:
        Y = T.Y0 + (idx[:, 1] + 0.5) / CELL
        low = idx[Y <= Y.min() + 1e-6]
        X = T.X0 + (low[:, 0].mean() + 0.5) / CELL
        Z = T.Z0 + (low[:, 2].mean() + 0.5) / CELL
        lf = [round(float(X), 2), round(float(Y.min()), 2), round(float(Z), 2)]
        lm["left_foot"] = lf
        lm["left_foot_offset_from_centre"] = [round(lf[0] - (mn["x"][0] + mn["x"][1]) / 2, 2),
                                              round(lf[2] - (mn["z"][0] + mn["z"][1]) / 2, 2)]
    lm["model_units_per_block"] = 2
    lm["note"] = ("単位はモデルの単位 (geo の 1 単位 = 0.5 ブロック = 格子の 1/2 単位)。"
                  "ブロックへは 0.5 倍 (game scale 8)。bounds は geo の範囲。")
    LANDMARKS.parent.mkdir(parents=True, exist_ok=True)
    LANDMARKS.write_text(json.dumps(lm, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print("landmarks", LANDMARKS)


# ---------------------------------------------------------------- 本体

def main():
    global piv
    G0 = build_grid()
    snap_body_tops(G0, 4)
    snap_blocks(G0, SNAP_BONES, 4)
    print("snapped near cells", int((G0 != 0).sum()))

    lods = {}
    stats = {}
    for name, f, ppu in LODS:
        G = downsample(G0, f) if f > 1 else G0.copy()
        if f > 1:
            print(name, "gate cells", stamp_gate(G, G0, f))
        for line in islands_fix(G, name):
            print(line)
        print(name, "islands left", islands(G))
        cubes, raw = build_lod(G, f, ppu)
        for i, c in enumerate(cubes):
            c["id"] = (name, i)
        lods[name] = (G, f, ppu, cubes)
        print(name, "boxes", raw, "kept", len(cubes), "faces", sum(len(c["faces"]) for c in cubes), flush=True)

    # 骨の軸: 手と脚は、その骨の範囲から決める (手首の瘤の中心、脚の付け根の上の中央)
    near = lods["near"][3]
    piv = dict(PIVOT)
    gl = landmarks(G0, {})
    kc_l = gl.get("knob_left_centre")
    kc_r = gl.get("knob_right_centre")
    piv["hand_l"] = [round(v, 2) for v in kc_l] if kc_l else [150, 59, -24]
    piv["hand_r"] = [round(v, 2) for v in kc_r] if kc_r else [-150, 59, -24]
    for bone, s in (("leg_l", 1), ("leg_r", -1)):
        b = gl.get("bone_%s" % bone)
        if b:
            piv[bone] = [round((b["min"][0] + b["max"][0]) / 2, 2), b["max"][1], round((b["min"][2] + b["max"][2]) / 2, 2)]
        else:
            piv[bone] = [s * 12, 26, 26]
    gb = gl.get("bone_gate")
    piv["gate"] = [0, gb["max"][1] - 4, gb["min"][2] + 3] if gb else [0, 60, 44]
    piv["gate"] = [round(v, 2) for v in piv["gate"]]
    print("pivots", json.dumps({k: piv[k] for k in ("hand_l", "hand_r", "leg_l", "leg_r", "gate", "head")}))

    # 1 枚のテクスチャに 3 つの LOD の面を詰める
    items = []
    for name, (G, f, ppu, cubes) in lods.items():
        for c in cubes:
            for fn, (w, h) in c["faces"].items():
                items.append(((c["id"], fn), w, h))
    pos = pack_width(items, ATLAS_W)
    H = -(-max(pos[k][1] + h for k, w, h in items) // 16) * 16
    print("atlas", ATLAS_W, "x", H, "px used", sum(w * h for _, w, h in items))
    tex = np.zeros((H, ATLAS_W, 4), np.uint8)
    glow = np.zeros((H, ATLAS_W, 4), np.uint8)

    geos = {}
    counts = {}
    bnd_all = {}
    for name, (G, f, ppu, cubes) in lods.items():
        uv_of = {}
        glow_px = 0
        for c in cubes:
            for fn, (w, h) in c["faces"].items():
                u, v = pos[(c["id"], fn)]
                uv_of[(c["id"], fn)] = (u, v)
                glow_px += paint_face(G, f, f * 0.5, c, fn, w, h, tex, glow, u, v)
        geos[name] = make_geo(cubes, uv_of, H, name)
        counts[name] = cube_count(geos[name])
        ov = check_overlap(cubes)
        bnd = {"x": (min(c["o"][0] for c in cubes), max(c["o"][0] + c["s"][0] for c in cubes)),
               "y": (min(c["o"][1] for c in cubes), max(c["o"][1] + c["s"][1] for c in cubes)),
               "z": (min(c["o"][2] for c in cubes), max(c["o"][2] + c["s"][2] for c in cubes))}
        bnd_all[name] = {k: [round(float(v[0]), 2), round(float(v[1]), 2)] for k, v in bnd.items()}
        stats[name] = dict(cubes=counts[name], quads=quad_count(geos[name]), overlap=ov, glow_px=glow_px,
                           bounds=bnd_all[name])
        print(name, json.dumps(stats[name], ensure_ascii=False), flush=True)
        assert glow_px > 0, "lit pixels are zero in " + name

    RES_GEO = RES / "geo/entity"
    RES_TEX = RES / "textures/entity"
    RES_ANIM = RES / "animations/entity"
    for d in (RES_GEO, RES_TEX, RES_ANIM):
        d.mkdir(parents=True, exist_ok=True)
    for name, fn in FILES.items():
        (RES_GEO / fn).write_text(json.dumps(geos[name], ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(RES_TEX / "thunder_wing.png", optimize=True)
    Image.fromarray(glow, "RGBA").save(RES_TEX / "thunder_wing_glowmask.png", optimize=True)
    (RES_ANIM / "thunder_wing.animation.json").write_text(json.dumps(anim_json(), ensure_ascii=False, indent=1) + "\n",
                                                          encoding="utf-8")
    write_landmarks(G0, bnd_all)
    preview(geos, tex, glow, counts)
    preview_poses(geos, tex, glow)
    preview_far(geos, tex, glow)
    print("done", json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main()
