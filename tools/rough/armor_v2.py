"""岩鯨の甲殻 (armor) 第2回ラフ: 0.5単位の立方体、1単位=2画素、面ごとの uv。

案 A (第1回の A の高解像度化) / B (装飾・彫刻を足す) / C (有機的な鯨肌・フジツボ)。

実行 (リポジトリ直下): python tools/rough/armor_v2.py
出力:
  docs/roughs/v2/armor/<a|b|c>/ : geo.json, texture.png, glowmask.png
  docs/roughs/v2/armor.png      : 比較シート (左端は前回の A)
  docs/roughs/v2/armor.md       : 説明と検査結果

方式: 0.5 単位の格子 (1セル = 0.5 単位 = 1 画素) に形を置く。各部品は形の判定で
セルを選び、部品ごとに骨 (bone) を割り当てる。同じ骨の中のセルを貪欲に箱へまとめるので、
箱どうしは重ならない (構造的に重なりは起きない)。各箱の各面は、1 セル = 1 画素の uv を持つ。
テクスチャの画素は、その面のセルの位置と向きから決める (板金の段、縁の光、継ぎ目、鋲、欠け)。

座標: 1単位 = 1/16 ブロック。y が上。前面は -z (north)。プレイヤーの体 (灰の人形) は
PLAYER の箱で、甲殻はそこから 0.5 単位以上離して置く。
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import check_overlaps, load_geo, load_png, render, sheet  # noqa: E402
from armor import BONES, PAL, mannequin  # noqa: E402

OUT = ROOT / "docs/roughs/v2"
V1 = ROOT / "docs/roughs/armor/a"
TEXW = 256
CELL = 0.5
OX, OY, OZ = -12.0, 0.0, -8.0          # 格子の原点 (単位)
NX, NY, NZ = 48, 76, 32                # 格子の大きさ (セル数)
BONE_NAMES = ["armorHead", "armorBody", "armorRightArm", "armorLeftArm",
              "armorRightLeg", "armorLeftLeg", "armorRightBoot", "armorLeftBoot"]
FACES = ["north", "south", "east", "west", "up", "down"]
MS, MB, MX, MK, MG, MR = 1, 2, 3, 4, 5, 6  # 石板, 鯨骨, 共鳴の結晶, 暗部, フジツボ, 金具の縁
# 灰の人形 (プレイヤーの体): x0, x1, y0, y1, z0, z1
PLAYER = [(-4, 4, 24, 32, -4, 4), (-4, 4, 12, 24, -2, 2), (-8, -4, 12, 24, -2, 2),
          (4, 8, 12, 24, -2, 2), (-4, 0, 0, 12, -2, 2), (0, 4, 0, 12, -2, 2)]
STY = {
    "A": dict(curve=False, cham=1.75, ornate=False, barn=False, scales=False),
    "B": dict(curve=False, cham=1.75, ornate=True, barn=False, scales=False),
    "C": dict(curve=True, cham=1.5, ornate=False, barn=True, scales=True),
}
TITLE = {"A": "忠実・高解像度", "B": "装飾・彫刻", "C": "有機・鯨肌"}
GREY = np.full((64, 64, 4), (150, 150, 150, 255), np.uint8)
DIRS6 = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
SEED = 20261015


def _grid():
    xs = OX + (np.arange(NX) + 0.5) * CELL
    ys = OY + (np.arange(NY) + 0.5) * CELL
    zs = OZ + (np.arange(NZ) + 0.5) * CELL
    return np.meshgrid(xs, ys, zs, indexing="ij")


X, Y, Z = _grid()  # セルの中心の座標


def keepout(pad=0.5):
    """灰の人形から pad 以内のセル。甲殻はここに置かない (体の外側に置く)。"""
    k = np.zeros(X.shape, bool)
    for x0, x1, y0, y1, z0, z1 in PLAYER:
        k |= (X > x0 - pad) & (X < x1 + pad) & (Y > y0 - pad) & (Y < y1 + pad) & (Z > z0 - pad) & (Z < z1 + pad)
    return k


# ------------------------------------------------------------------ 部品の形 (セルの判定)
# 各関数は (マスク, 材料) の並び。先に書いたものが優先する。

def part_helmet(st):
    A, Az = np.abs(X), np.abs(Z)
    if st["curve"]:
        t = np.clip((Y - 28.0) / 7.0, 0.0, 1.0)
        dome = np.floor(5.5 * np.sqrt(np.clip(1.0 - t * t, 0.0, 1.0)) * 2.0) / 2.0
        hw = np.where(Y < 28.0, 5.5, dome)              # 楕円のドーム (0.5 単位の段)
    else:
        t = np.clip((Y - 30.0) / 5.0, 0.0, 1.0)                 # 0.5 単位刻みのなめらかなドーム
        hw = np.where(Y < 30.0, 5.5, np.floor(5.5 * np.sqrt(np.clip(1.0 - t * t, 0.0, 1.0)) * 2.0) / 2.0)
    cham = st["cham"]
    env = (Y >= 24.0) & (Y < 35.0) & (np.maximum(A, Az) <= hw) & (A + Az <= cham * hw)
    slit = (A <= 3.5) & (Y >= 26.0) & (Y < 27.0) & (Z < -5.0)           # 視界の溝 (前半分を削る)
    walls = (A <= 3.5) & (((Y >= 25.0) & (Y < 26.0)) | ((Y >= 27.0) & (Y < 28.0)) |
                          ((Y >= 26.0) & (Y < 27.0) & (Z >= -5.0))) & (Z < -4.5) & env   # 溝の壁と奥 (暗い)
    band = (Y >= 27.0) & (Y < 28.0) & (np.maximum(A, Az) <= 6.0) & (A + Az <= cham * 6.0)  # 鯨骨の帯 (1段出る)
    s = A + np.abs(Y - 31.0)                                             # 額の結晶 (菱形)
    core = (s <= 1.0) & (Z >= -6.5) & (Z < -5.5)
    bezel = (s > 1.0) & (s <= 1.75) & (Z >= -6.0) & (Z < -5.5)
    brow = ((Y >= 28.0) & (Y < 29.0) & (A <= 4.0) & (Z >= -6.0) & (Z < -5.5)) | \
           ((Y >= 29.0) & (Y < 30.5) & (A <= 3.5) & (Z >= -6.5) & (Z < -5.5))   # 眉の稜
    cheek = (A >= 5.5) & (A < 6.5) & (((Y >= 24.0) & (Y < 26.0) & (Az <= 3.0)) |
                                       ((Y >= 26.0) & (Y < 27.0) & (Az <= 2.0)))  # 頬当て
    parts = [(core, MX), (bezel, MR), (walls, MK), (band, MB), (brow, MS), (cheek, MS)]
    if st["ornate"]:
        crest = ((A <= 1.5) & (Y >= 35.0) & (Y < 37.0) & (Az <= 2.5)) | \
                ((A <= 0.5) & (Y >= 37.0) & (Y < 38.0) & (Az <= 1.5))       # 高い鶏冠 (兜の上に立つ)
        spike = (A >= 6.5) & (A < 7.5) & (Y >= 25.0) & (Y < 26.0) & (Az <= 0.5)  # 頬の棘
        parts += [(crest, MS), (spike, MB)]
    if st["curve"]:
        fin = (A <= 0.5) & (Y >= 34.0) & (Y < 36.0) & (Z >= -1.0) & (Z < 2.5)   # 背びれ
        parts += [(fin, MS)]
    parts.append((env & ~slit, MS))
    return parts


def part_chest(st):
    A = np.abs(X)
    lip = ((Y - 12.0) % 2.0) < 0.5                     # 板の段の下端は 0.5 単位前に出る
    zf = np.where(lip, -4.0, -3.5)
    if st["curve"]:
        zf = zf - 0.5 * (A < 2.5) - 0.5 * (A < 1.0)    # 胸の膨らみ (段で曲げる)
    hw = np.where(Y >= 23.0, 5.0, 5.5)
    env = (Y >= 11.5) & (Y < 24.5) & (A <= hw) & (Z >= zf) & (Z <= 3.5)
    c = A + np.abs(Y - 18.0)
    crystal = (c <= 1.0) & (Z >= -5.0) & (Z < -3.5)                     # 胸の結晶
    bezel = (c > 1.0) & (c <= 1.75) & (Z >= -4.5) & (Z < -3.5)          # 結晶の縁
    ribs = (((A >= 2.0) & (A < 2.5)) | ((A >= 4.0) & (A < 4.5))) & (Y >= 14.0) & (Y < 22.5) & (Z >= -4.5) & (Z < -3.0)
    arcs = (A <= 4.5) & (((Y >= 13.5) & (Y < 14.0)) | ((Y >= 21.0) & (Y < 21.5))) & (Z >= -4.5) & (Z < -3.0)
    spine = (A < 0.5) & (Y >= 13.0) & (Y < 23.0) & (Z >= 3.5) & (Z < 4.5)  # 背骨の隆起
    parts = [(crystal, MX), (bezel, MR), (ribs | arcs, MB), (spine, MB)]
    if st["ornate"]:
        collar = (Y >= 23.0) & (Y < 24.0) & (A <= 5.0) & (Z >= -4.0) & (Z < -3.0)  # 首の鯨骨の襟
        bars = (A <= 3.0) & (Z >= -4.5) & (Z < -3.5) & (((Y >= 15.0) & (Y < 15.5)) | ((Y >= 20.0) & (Y < 20.5)))
        parts += [(collar, MB), (bars, MS)]                                         # 浮き出た刻みの帯
    parts.append((env, MS))
    return parts


def part_arm(st, s):
    xl = X * s                       # 腕の外側へ向かう距離 (右: -x, 左: +x)
    Az = np.abs(Z)
    wrist = (Y >= 12.0) & (Y < 14.0) & (xl >= 3.0) & (xl < 9.0) & (Az <= 3.0)
    sleeve = (Y >= 14.0) & (Y < 22.0) & (xl >= 2.5) & (xl < 9.5) & (Az <= 3.5)
    band = (Y >= 18.0) & (Y < 19.0) & (xl >= 2.5) & (xl < 9.5) & (Az <= 3.5)  # 鯨骨の帯
    if st["curve"]:
        p0 = (Y >= 22.0) & (Y < 23.5) & (((xl - 6.75) / 4.25) ** 2 + (Az / 3.5) ** 2 <= 1.0)
        p1 = (Y >= 23.5) & (Y < 25.0) & (((xl - 6.5) / 4.0) ** 2 + (Az / 3.0) ** 2 <= 1.0)
        p2 = (Y >= 25.0) & (Y < 26.0) & (((xl - 6.5) / 3.0) ** 2 + (Az / 2.5) ** 2 <= 1.0)
    else:
        p0 = (Y >= 22.0) & (Y < 23.5) & (xl >= 2.5) & (xl < 11.0) & (Az <= 3.5)
        p1 = (Y >= 23.5) & (Y < 25.0) & (xl >= 2.5) & (xl < 10.5) & (Az <= 3.0)
        p2 = (Y >= 25.0) & (Y < 26.0) & (xl >= 3.5) & (xl < 9.5) & (Az <= 2.5)
    parts = [(band, MB), (p2, MS), (p1, MS), (p0, MS), (sleeve, MS), (wrist, MS)]
    if st["ornate"]:
        spike = (Y >= 26.0) & (Y < 27.5) & (xl >= 7.5) & (xl < 8.5) & (Az <= 0.5)  # 肩の棘
        parts.insert(0, (spike, MB))
    return parts


def part_leg(st, s):
    xl = X * s
    Az = np.abs(Z)
    thigh = (Y >= 6.0) & (Y < 12.0) & (xl >= 0.0) & (xl < 5.5) & (Az <= 3.5)
    trim = (Y >= 11.0) & (Y < 11.5) & (xl >= 0.0) & (xl < 5.5) & (Az <= 3.5)      # 腰の鯨骨の帯
    knee = (Y >= 6.0) & (Y < 8.0) & (xl >= 0.5) & (xl < 3.5) & (Z >= -4.5) & (Z < -3.5)  # 膝当て
    tas1 = (Y >= 8.5) & (Y < 10.5) & (xl >= 0.5) & (xl < 5.0) & (Z >= -4.5) & (Z < -3.5)  # 垂れ布 (下)
    tas2 = (Y >= 10.5) & (Y < 11.0) & (xl >= 0.5) & (xl < 5.0) & (Z >= -4.0) & (Z < -3.5)  # 垂れ布 (上)
    parts = [(trim, MB), (knee, MS), (tas1, MS), (tas2, MS), (thigh, MS)]
    if st["ornate"]:
        kring = (Y >= 6.0) & (Y < 6.5) & (xl >= 0.0) & (xl < 5.5) & (Az <= 3.5)
        parts.insert(0, (kring, MB))
    return parts


def part_boot(st, s):
    xl = X * s
    Az = np.abs(Z)
    env = (Y >= 0.0) & (Y < 6.0) & (xl >= 0.0) & (xl < 5.5) & (Az <= 3.5)
    sole = (Y < 0.5) & (xl >= 0.0) & (xl < 5.5) & (Az <= 3.5)
    toe = (xl >= 0.5) & (xl < 3.5) & (Z >= -5.5) & (Z < -3.5) & (Y >= 1.0) & (Y < 2.0)
    toe = toe | ((xl >= 0.5) & (xl < 3.5) & (Z >= -5.0) & (Z < -3.5) &
                 (((Y >= 0.0) & (Y < 1.0)) | ((Y >= 2.0) & (Y < 3.0))))           # 爪先の覆い
    ring = (Y >= 3.0) & (Y < 3.5) & (xl >= 0.0) & (xl < 6.0) & (Az <= 4.0)        # 足首の輪
    cuff = (Y >= 5.0) & (Y < 6.0) & (xl >= 0.0) & (xl < 6.0) & (Az <= 4.0)        # 上縁
    return [(sole, MK), (ring, MB), (toe, MS), (cuff, MS), (env, MS)]


# ------------------------------------------------------------------ 組み立て

def claim(owner, mat, kout, bi, parts):
    for m, code in parts:
        sel = m & (owner < 0) & (~kout)
        owner[sel] = bi
        mat[sel] = code


def barnacles(owner, mat, kout, rng_seed):
    """C: 外側の面にフジツボの粒を散らす (1 セル出る)。"""
    rng = np.random.default_rng(rng_seed)
    for i, j, k in np.argwhere(owner >= 0):
        if rng.random() > 0.012:
            continue
        for d in rng.permutation(len(DIRS6)):
            dx, dy, dz = DIRS6[d]
            a, b, c = i + dx, j + dy, k + dz
            if 0 <= a < NX and 0 <= b < NY and 0 <= c < NZ and owner[a, b, c] < 0 and not kout[a, b, c]:
                owner[a, b, c] = owner[i, j, k]
                mat[a, b, c] = MG
                break


def mesh(mask):
    """マスクのセルを、貪欲に箱へまとめる。戻り値は (i, j, k, di, dj, dk) のセル単位。"""
    m = mask.copy()
    out = []
    for i, j, k in np.argwhere(m):
        if not m[i, j, k]:
            continue
        dk = 1
        while k + dk < NZ and m[i, j, k + dk]:
            dk += 1
        di = 1
        while i + di < NX and m[i + di, j, k:k + dk].all():
            di += 1
        dj = 1
        while j + dj < NY and m[i:i + di, j + dj, k:k + dk].all():
            dj += 1
        m[i:i + di, j:j + dj, k:k + dk] = False
        out.append((int(i), int(j), int(k), di, dj, dk))
    return out


def face_dims(c, fn):
    if fn in ("north", "south"):
        return c["di"], c["dj"]
    if fn in ("east", "west"):
        return c["dk"], c["dj"]
    return c["di"], c["dk"]  # up / down


def face_geom(c, fn):
    """Bedrock の面の (一隅, 横の辺, 縦の辺, 外向きの法線)。load_geo と同じ向き。"""
    x0 = OX + c["i"] * CELL
    y0 = OY + c["j"] * CELL
    z0 = OZ + c["k"] * CELL
    sx, sy, sz = c["di"] * CELL, c["dj"] * CELL, c["dk"] * CELL
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    g = {
        "north": ((x1, y0, z0), (-sx, 0, 0), (0, sy, 0), (0, 0, -1)),
        "south": ((x0, y0, z1), (sx, 0, 0), (0, sy, 0), (0, 0, 1)),
        "east": ((x1, y0, z1), (0, 0, -sz), (0, sy, 0), (1, 0, 0)),
        "west": ((x0, y0, z0), (0, 0, sz), (0, sy, 0), (-1, 0, 0)),
        "up": ((x0, y1, z1), (sx, 0, 0), (0, 0, -sz), (0, 1, 0)),
        "down": ((x0, y0, z0), (sx, 0, 0), (0, 0, sz), (0, -1, 0)),
    }[fn]
    return tuple(np.array(v, float) for v in g)


def hfrac(*a):
    """セルの位置から決まる、決まった乱数 (0..1)。"""
    h = 2166136261
    for v in a:
        h = ((h ^ (int(v) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    return (h % 100000) / 100000.0


def texel_char(st, code, i, j, k, n, up_e, dn_e, xc, yc):
    """1 画素 (= 1 セルの面) の色の文字。PAL の鍵。"""
    h = hfrac(i, j, k, 3 * n[0] + 5 * n[1] + 7 * n[2])
    if code == MS:
        row = math.floor((yc - 12.0) / 2.0)
        off = 1.5 if row % 2 else 0.0
        xp = xc + 12.0 + off
        col = math.floor(xp / 3.0)
        tone = ["M", "M", "L", "M", "D"][int(hfrac(row, col, 9) * 5)]
        if h < 0.03:
            return "K"                                          # 欠け
        if (yc - 12.0) % 2.0 < 0.5 or xp % 3.0 < 0.5:
            return "D"                                          # 板の継ぎ目
        if abs(xp % 3.0 - 1.25) < 0.1 and abs((yc - 12.0) % 2.0 - 1.25) < 0.1:
            return "H"                                          # 鋲
        if st["scales"] and (j % 3 == 0 or (i + (j // 3) * 2) % 4 == 0):
            return "D"                                          # 鯨肌のうろこの線
        if st["ornate"] and tuple(n) == (0, 0, -1) and abs(xc) <= 3.5 and \
                ((13.0 <= yc < 23.0) or (28.0 <= yc < 31.0)) and (i + j) % 4 == 0:
            return "D"                                          # 刻みの文字 (ルーン)
        if up_e:
            return "H"                                          # 上の縁の光
        if dn_e:
            return "D"                                          # 下の縁の影
        return tone
    if code == MB:
        if h < 0.03:
            return "w"
        if st["ornate"] and (i + 2 * j) % 6 == 0:
            return "w"                                          # 鯨骨の唐草
        if dn_e:
            return "w"
        return "W"
    if code == MX:
        if up_e or h < 0.25:
            return "c"
        return "C"
    if code == MR:
        if up_e:
            return "H"
        if dn_e:
            return "D"
        return "L"
    if code == MK:
        return "K"
    if code == MG:
        if h < 0.4 or up_e:
            return "g"
        return "G"
    return "D"


def build(opt):
    st = STY[opt]
    kout = keepout()
    owner = np.full((NX, NY, NZ), -1, np.int16)
    mat = np.zeros((NX, NY, NZ), np.uint8)
    claim(owner, mat, kout, 0, part_helmet(st))
    claim(owner, mat, kout, 1, part_chest(st))
    claim(owner, mat, kout, 2, part_arm(st, -1))
    claim(owner, mat, kout, 3, part_arm(st, 1))
    claim(owner, mat, kout, 4, part_leg(st, -1))
    claim(owner, mat, kout, 5, part_leg(st, 1))
    claim(owner, mat, kout, 6, part_boot(st, -1))
    claim(owner, mat, kout, 7, part_boot(st, 1))
    if st["barn"]:
        barnacles(owner, mat, kout, SEED + 7)

    cubes = []
    for bi, name in enumerate(BONE_NAMES):
        for i, j, k, di, dj, dk in mesh(owner == bi):
            cubes.append(dict(bone=name, bi=bi, i=i, j=j, k=k, di=di, dj=dj, dk=dk))

    # テクスチャの置き場 (棚詰め)。1 セル = 1 画素
    jobs = [(ci, fn) + face_dims(c, fn) for ci, c in enumerate(cubes) for fn in FACES]
    pos, x, y, shelf = {}, 0, 0, 0
    for ci, fn, W, H in sorted(jobs, key=lambda t: (-t[3], -t[2])):
        if x + W > TEXW:
            x, y, shelf = 0, y + shelf, 0
        pos[(ci, fn)] = (x, y)
        x += W
        shelf = max(shelf, H)
    TEXH = y + shelf
    atlas = np.zeros((TEXH, TEXW, 4), np.uint8)
    glow = np.zeros((TEXH, TEXW, 4), np.uint8)
    pal = PAL[opt]
    for ci, c in enumerate(cubes):
        for fn in FACES:
            o, eu, ev, n = face_geom(c, fn)
            W, H = face_dims(c, fn)
            u0, v0 = pos[(ci, fn)]
            cc = (np.arange(W) + 0.5) / W
            rr = (np.arange(H) + 0.5) / H
            tt = rr if fn == "down" else 1 - rr          # load_geo と同じ向き
            P = o[None, None] + cc[None, :, None] * eu[None, None] + tt[:, None, None] * ev[None, None]
            Pin = P - n[None, None] * 0.25               # 面の少し内側 = 面に接するセル
            idx = np.floor((Pin - np.array([OX, OY, OZ])) * 2.0).astype(int)
            up = (0, 1, 0) if n[1] == 0 else (0, 0, -1)
            for r in range(H):
                for q in range(W):
                    i, j, k = idx[r, q]
                    if not (0 <= i < NX and 0 <= j < NY and 0 <= k < NZ) or owner[i, j, k] != ci_bone(c):
                        ch = "D"
                    else:
                        code = mat[i, j, k]
                        up_e = not solid(owner, i + up[0], j + up[1], k + up[2])
                        dn_e = not solid(owner, i - up[0], j - up[1], k - up[2])
                        ch = texel_char(st, code, i, j, k, n, up_e, dn_e,
                                        OX + (i + 0.5) * CELL, OY + (j + 0.5) * CELL)
                    atlas[v0 + r, u0 + q, :3] = pal[ch]
                    atlas[v0 + r, u0 + q, 3] = 255
                    if ch in ("C", "c"):
                        glow[v0 + r, u0 + q] = (255, 255, 255, 255)

    bones = []
    for name in BONE_NAMES:
        cs = []
        for ci, c in enumerate(cubes):
            if c["bone"] != name:
                continue
            uv = {}
            for fn in FACES:
                W, H = face_dims(c, fn)
                u, v = pos[(ci, fn)]
                uv[fn] = {"uv": [int(u), int(v)], "uv_size": [int(W), int(H)]}
            cs.append({
                "origin": [round(OX + c["i"] * CELL, 3), round(OY + c["j"] * CELL, 3), round(OZ + c["k"] * CELL, 3)],
                "size": [round(c["di"] * CELL, 3), round(c["dj"] * CELL, 3), round(c["dk"] * CELL, 3)],
                "uv": uv,
            })
        if cs:
            bones.append({"name": name, "pivot": BONES[name], "cubes": cs})
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.skyabyss_armor_v2_{opt.lower()}",
                "texture_width": TEXW, "texture_height": int(TEXH),
                "visible_bounds_width": 3, "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": bones,
        }],
    }
    info = dict(cubes=len(cubes), cells=int((owner >= 0).sum()), tex=(TEXW, int(TEXH)))
    return geo, atlas, glow, info


_CUR = {"owner": None}


def ci_bone(c):
    return c["bi"]


def solid(owner, i, j, k):
    return 0 <= i < NX and 0 <= j < NY and 0 <= k < NZ and owner[i, j, k] >= 0


def player_overlaps(geo):
    n = 0
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b["cubes"]:
            o = np.array(c["origin"], float)
            e = o + np.array(c["size"], float)
            for x0, x1, y0, y1, z0, z1 in PLAYER:
                lo, hi = np.array([x0, y0, z0], float), np.array([x1, y1, z1], float)
                ov = np.minimum(e, hi) - np.maximum(o, lo)
                if (ov > 1e-6).all():
                    n += 1
    return n


def subset(geo, names):
    g = json.loads(json.dumps(geo))
    g["minecraft:geometry"][0]["bones"] = [b for b in g["minecraft:geometry"][0]["bones"] if b["name"] in names]
    return g


def mann_faces(names=None):
    m = mannequin()
    return load_geo(subset(m, names) if names else m, GREY)


def armor_faces(geo, tex, gm, names=None):
    g = subset(geo, names) if names else geo
    return load_geo(g, tex, glowmask=gm)


def save(opt, geo, atlas, glow):
    d = OUT / "armor" / opt.lower()
    d.mkdir(parents=True, exist_ok=True)
    (d / "geo.json").write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")
    Image.fromarray(atlas).save(d / "texture.png")
    Image.fromarray(glow).save(d / "glowmask.png")


CONCEPT = {
    "A": "第1回の A の形と配色 (石板・鯨骨の帯・額と胸の水色の結晶) を、0.5 単位の段で彫り直した。"
         "兜は 0.5 単位刻みの丸いドームで、眉の稜、頬当て、視界の溝 (前を削った凹み、奥は暗い) を持つ。胸は板の段と、浮き出た鯨骨の肋、結晶を縁取りで囲む。"
         "肩当ては 3 層の段。脚は垂れ布と膝当て、靴は爪先の覆いと足首の輪。",
    "B": "A と同じ形に、目立つ装飾を足した。兜の上に高い鶏冠 (1.5 単位の段で立つ) と頬の棘、"
         "胸に浮き出た刻みの帯 2 本と首元の鯨骨の襟、肩の棘、膝の鯨骨の輪。兜と胸の板には刻みの線を入れた。"
         "A との違いは、兜の鶏冠の輪郭と胸の帯で一目で分かる。",
    "C": "曲面と有機的な段で作り直した。兜のドームと胸の膨らみは楕円を段にしたもの、肩当ては丸い層。"
         "板の表面は鯨の皮膚のようなうろこの線。背びれを付け、フジツボは数か所の小さな群れだけにした。",
}


def main():
    grey = GREY
    cols = []
    v1g = json.loads((V1 / "geo.json").read_text(encoding="utf-8"))
    v1t = load_png(V1 / "texture.png")
    v1gm = load_png(V1 / "glowmask.png")
    v1 = mann_faces() + armor_faces(v1g, v1t, v1gm)
    cols.append(("前回の採用案 A (v1)", [render(v1, yaw=145, pitch=10, size=(440, 520)),
                                     render(v1, yaw=35, pitch=10, size=(440, 520))]))
    report = {}
    for opt in ("A", "B", "C"):
        geo, atlas, glow, info = build(opt)
        save(opt, geo, atlas, glow)
        probs = check_overlaps(geo)
        pov = player_overlaps(geo)
        report[opt] = dict(info, overlaps=probs, player=pov)
        tex_img, gm_img = atlas, glow
        full = mann_faces() + armor_faces(geo, tex_img, gm_img)
        front = render(full, yaw=145, pitch=10, size=(440, 520))
        back = render(full, yaw=35, pitch=10, size=(440, 520))
        side = render(full, yaw=90, pitch=10, size=(440, 520))
        helm = render(mann_faces(["head"]) + armor_faces(geo, tex_img, gm_img, ["armorHead"]),
                      yaw=145, pitch=8, size=(440, 440))
        chest = render(mann_faces(["body"]) + armor_faces(geo, tex_img, gm_img, ["armorBody"]),
                       yaw=145, pitch=8, size=(440, 440))
        cols.append((f"{opt}  {TITLE[opt]}", [front, back, side, helm, chest]))
        print(opt, info, "overlaps:", probs or "none", "player:", pov)

    sheet_img = sheet(
        "岩鯨の甲殻 (armor) 第2回: 高解像度 A / B / C",
        cols,
        "上から: 正面 3/4 / 背面 3/4 / 側面 (腕・肩当て) / 兜の近接 / 胸の近接 (灰の人形に装着。1単位 = 2画素)",
    )
    sheet_img.save(OUT / "armor.png")
    write_md(report)


def write_md(report):
    lines = [
        "# 岩鯨の甲殻 (armor) 第2回ラフ (高解像度)", "",
        "第1回の A を基に、0.5 単位の立方体、テクスチャ 2 画素/単位 (1 セル = 1 画素)、面ごとの uv で作り直した。",
        "3案。シートは `docs/roughs/v2/armor.png` (左端は前回の採用案 A)。素材は `docs/roughs/v2/armor/<a|b|c>/`。",
        "生成: `tools/rough/armor_v2.py` (seed 固定)。", "",
        "## 共通の決まり", "",
        "- 骨と支点は第1回と同じ (armorHead / armorBody / armorRightArm / armorLeftArm / armorRightLeg / armorLeftLeg / armorRightBoot / armorLeftBoot)。",
        "- 形は 0.5 単位の格子に置き、同じ骨の中のセルを貪欲に箱へまとめる。箱どうしは重ならない。",
        "- 甲殻は灰の人形の箱から 0.5 単位以上離して置く (体の中に入らない)。", "",
    ]
    for opt in ("A", "B", "C"):
        r = report[opt]
        ov = "0 件 (重なりなし)" if not r["overlaps"] else f"{len(r['overlaps'])} 件: " + "; ".join(r["overlaps"][:5])
        lines += [
            f"## {opt} {TITLE[opt]}", "",
            f"- コンセプト: {CONCEPT[opt]}",
            f"- 立方体: {r['cubes']} 個 / 占有セル: {r['cells']} (1 セル = 0.5 単位)",
            f"- テクスチャ: {r['tex'][0]} x {r['tex'][1]} (2 画素/単位)",
            f"- 重なり検査 (check_overlaps): {ov}",
            f"- 灰の人形との重なり: {r['player']} 件", "",
        ]
    (OUT / "armor.md").write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
