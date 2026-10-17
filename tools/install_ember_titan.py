"""余燼の巨神 (ember_titan) の採用案 A「火山の背」をゲームに入れる (GeckoLib の geo、テクスチャ、アニメーション)。

使い方 (リポジトリ直下で): PYTHONIOENCODING=utf-8 python -B tools/install_ember_titan.py
形の元: tools/rough/ember_titan.py の build("a") と同じ体 (E.build_body: 頭・顎・目・脚・尾・胴) を使い、
        背の上だけをこの版で作り直す (ラフのファイルは変えない。関数を差し替えて使う。tide_shell と同じ方法):
        - 火山の円錐は断面を丸く (ラフは 2.3 乗の四角い断面)
        - 煙の房は骨ごとに分ける (smoke_0〜3)。火口の煙の根元は背 (ridge) に含める
        - 背の後ろ半分 (z 12〜100) に、光る割れ目を足す (ラフ C の割れ目。火守りの谷の天井)
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/ember_titan.geo.json       近景。0.5 単位の格子。体・尾・脚は 2 単位 (1 ブロック) の段にそろえる。
                                        頭・顎・目・口・背の火山は細かいまま
  geo/entity/ember_titan_mid.geo.json   中景。4 単位の格子。背の裾の柱は 1 つの丘にまとめる
  geo/entity/ember_titan_far.geo.json   遠景。6 単位の格子。背の火山と煙を大きな塊にまとめる (尾根の形を残す)
  textures/entity/ember_titan.png / ember_titan_glowmask.png   3 つの LOD で共有する 1 枚 (幅 2048)
  animations/entity/ember_titan.animation.json
確認用 (docs/review/ember_roughs/install/): lods.png, poses.png, far_read.png, stats.json

格子の座標: 格子は x を 0 に対して対称にし (-128〜128)、4 の倍数の段が x=0 と z の -212 に合うように 4 セル
(2 単位) ずつ詰めてある。X を反転してから箱にする。GeckoLib は X を反転して描くので、geo の +X がゲームの左わき。
頭は -Z。地面は y=0 (下には何も作らない)。1 単位 = 1/16 ブロック (描画は 8 倍)。

骨: body (根)、ridge (火山・柱・溶岩の流れ・割れ目・煙の根元。body の子)、head (body の子)、
    jaw (head の子。蝶番 (0, 6, -150))、leg_fl / leg_fr / leg_rl / leg_rr (body の子。_l は geo の +X)、
    tail (body の子)、smoke_0〜smoke_3 (ridge の子。煙の房ごと。軸は房の中心)。
離れ島の確認 (GROUPS): static (body + ridge)、head、jaw、各脚、tail。煙の房は浮いてよいので確認から外す。

口 (乗り込み): 舌の上面は y=6 (ML)。乗り込みの判定は x -5〜5、y 6〜14、z -172〜-160 (近景では空気。assert する)。
舌は z -200〜-150 に残す。

アニメの符号 (tide_shell と同じ): GeckoLib は X と Z の回転の符号を反転して回す。ファイルには
(-意図の X, 意図の Y, -意図の Z) を書く。位置は X を反転する (ファイルの x = -意図の x)。
意図の値は tools/rough の voxel.py の回転 (_rot) と同じ向き。
  - 顎が下がる (口が開く): 意図の X が負 (顎の先 z -56 が下へ回る)。ファイルには +6 を書く (breath_in)。
  - 頭を上げる (鼻先が上がる): 意図の X が正。ファイルには -2 を書く (breath_in)。
  - 顎は地面に伏せているので、口を開くと先は地面の下へ数ブロックもぐる (地面に隠れる。ゲームでは未確認)。
"""
import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/rough"))
import ember_titan as E  # noqa: E402  (体と脚と頭の関数だけを使う。ember_titan.py は変えない)
from voxel import Face, FACE_SHADE, _rot, load_geo, render, sheet  # noqa: E402

RES = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW_DIR = ROOT / "docs/review/ember_roughs/install"

CELL = E.CELL                     # 2 (1 セル = 0.5 単位)
PAD = 4                           # x と z の両端に足すセル数 (2 単位)。段が x=0 と z=-212 に合う
GX0, GY0, GZ0 = E.X0 - PAD / CELL, 0.0, E.Z0 - PAD / CELL      # -128, 0, -212
NX, NY, NZ = E.NX + 2 * PAD, E.NY, E.NZ + 2 * PAD             # 512, 384, 928
ATLAS_W = 2048
ML = E.ML                         # 下顎の上面 (舌の上面) = 6
ZH = E.ZH                         # 口の端 (蝶番) = -150
MOUTH = dict(x=(-5.0, 5.0), y=(6.0, 14.0), z=(-172.0, -160.0))    # 乗り込みの判定 (geo 単位)

ZV = E.ZV                         # 火山の中心 z = -40
RB, RR, YB, YT = 52.0, 16.0, 44.0, 128.0                         # 火山の底の半径、火口の半径、底、火口の縁
RIVS = [0.35, 1.55, 2.65, 3.95, 5.15]                            # 溶岩の流れの角度
COLS = ((-40.0, ZV + 34.0, 9), (38.0, ZV - 30.0, 8), (34.0, ZV + 40.0, 7), (-30.0, ZV - 44.0, 6))   # 柱の群れ (ラフ)
COL_CENTERS = [(-gx, gz) for gx, gz, _ in COLS]                  # 反転後の柱の群れの中心 (x は反転)
PUFFS = [(2.0, YT + 10, ZV + 14, 6.0), (4.0, YT + 22, ZV + 19, 7.0),
         (-1.0, YT + 35, ZV + 24, 7.0), (5.0, YT + 47, ZV + 29, 6.0)]   # 煙の房 (中心 x, y, z, 半径)。
# smoke_0 の底が火口の縁の上端 (y 132) に接し、上の房は近く (10〜13 単位ずつ) に重ねる (潜っていて縁だけ出ているとき、
# 煙が火口から立つように見せる。離れて浮くと四角い石に見えた)
MOUND_R = 11.0                                                   # 中景・遠景で柱を一つの丘にまとめる半径

CONE_FINE = None                  # 円錐の細かい格子 (絵を塗るため)。build_grid が入れる
PART = dict(E.PART)               # body 1, ridge 2, head 3, jaw 4, leg_fl 5, leg_fr 6, leg_rl 7, leg_rr 8, tail 9
SMOKE_PART = {i: 11 + i for i in range(4)}                       # smoke_0〜3 (10 は使わない)
NPARTS = 14
PART_BONE = {1: "body", 2: "ridge", 3: "head", 4: "jaw", 5: "leg_fl", 6: "leg_fr", 7: "leg_rl", 8: "leg_rr",
             9: "tail", 11: "smoke_0", 12: "smoke_1", 13: "smoke_2", 14: "smoke_3"}
BONE_ORDER = ["body", "ridge", "head", "jaw", "leg_fl", "leg_fr", "leg_rl", "leg_rr", "tail",
              "smoke_0", "smoke_1", "smoke_2", "smoke_3"]
PARENT = {"ridge": "body", "head": "body", "jaw": "head", "leg_fl": "body", "leg_fr": "body", "leg_rl": "body",
          "leg_rr": "body", "tail": "body", "smoke_0": "ridge", "smoke_1": "ridge", "smoke_2": "ridge",
          "smoke_3": "ridge"}
PIVOT = {"body": [0, 30, 0], "ridge": [0, 60, -20], "head": [0, 16, -136], "jaw": [0, 6, -150],
         "leg_fl": [44, 24, -76], "leg_fr": [-44, 24, -76], "leg_rl": [42, 24, 84], "leg_rr": [-42, 24, 84],
         "tail": [0, 24, 108]}
for _i, (_cx, _cy, _cz, _h) in enumerate(PUFFS):                 # 煙の房の軸は房の中心 (反転後の x)
    PIVOT["smoke_%d" % _i] = [-_cx, _cy, _cz]
GROUPS = {"static": [1, 2], "head": [3], "jaw": [4], "leg_fl": [5], "leg_fr": [6], "leg_rl": [7], "leg_rr": [8],
          "tail": [9]}
# 光の優先 (粗い段で残す材料): 溶岩の流れ・火口・喉の熾・目だけ。背の板の継ぎ目 (seam) は外す (中景・遠景で橙の塊にしない)
GLOW_MATS = [E.MAT["lava"], E.MAT["ember"], E.MAT["molten"], E.MAT["eye"]]
PROT_MATS = [E.MAT[n] for n in ("teeth", "eye", "tongue", "ember", "dark")]   # 頭の板の溝 (slagdark) と耳下腺は段にそろえる (箱を減らす)
GLOW_MIN = {8: 12, 12: 8, 16: 10}         # 粗い格子で、光る材料をこの数だけ含む段は光る材料で残す
THIN_PARTS = {4, 5, 6, 7, 8, 9}   # 脚・顎・尾は細いので、低い割合でも残す
THICK_FRAC, THIN_FRAC = 0.45, 0.3
SNAP_Q = 4                        # 体・背の段 (4 セル = 2 単位 = 1 ブロック)
COARSE_Q = 8                      # 脚・尾の段 (8 セル = 4 単位 = 2 ブロック)
CONE_COARSE_TOP = 112.0           # 円錐をこの高さより下だけ 4 単位の段にする (上の細い所と火口の縁は 2 単位のまま)
BODY_LOW_Y = 32.0                 # 胴をこの高さより下だけ粗くする (geo 単位。8 セルの段にそろう高さ)
HEAD_Q = 8                        # 頭の外側の段 (歯・目・喉・口の中は守る)
GLOW_PARTS = {2}                  # 背 (ridge) の光る筋は、2 セルでも残す
CONE_Y = 4.0                      # 火山の高さの段 (単位)。ラフは 2。4 にして近景の箱を減らす (縁と火口の高さは変えない)
NOTCH = (0.0, 0.0)                # 体の板の継ぎ目の深さ (ラフは 3 と 1。0 で板の上面を平らにして箱を減らす。継ぎ目の材料は残す)

LODS = [("near", 1, 2.0), ("mid", 8, 1.0), ("far", 16, 0.5)]    # 名前, 格子の倍率, 1 単位あたりの画素
FACE_AXIS = {"west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1)}


def ci(x):
    return int(round((x - GX0) * CELL))


def cj(y):
    return int(round((y - GY0) * CELL))


def ck(z):
    return int(round((z - GZ0) * CELL))


# 円錐のまわりの箱 (格子の番号: x0, x1, y0, y1, z0, z1)
CONE_BOX = (ci(-RB - 4), ci(RB + 4), cj(YB - 4), cj(YT + 12), ck(ZV - RB - 4), ck(ZV + RB + 4))


# ---------------------------------------------------------------- 格子 (ラフの体の上に、背を作り直す)

def ridge_cone(X, Y, z):
    """火山の円錐。ラフと同じ寸法と材料で、断面だけを丸く (ラフは 2.3 乗で四角かった)"""
    Xs = E.q2(X)
    zs = math.floor(z / 2.0) * 2.0 + 1.0
    Yl = np.floor(Y / CONE_Y) * CONE_Y + CONE_Y / 2.0     # 高さの段 (ラフは 2 単位。箱を減らすため CONE_Y)
    dx, dz = Xs, zs - ZV
    th = np.arctan2(dz, dx)
    rh = np.sqrt(dx ** 2 + dz ** 2)
    f = np.clip((Yl - YB) / (YT - YB), 0, 1)
    r = RR + (RB - RR) * (1 - f) ** 1.8
    r = r * (1 + 0.05 * np.sin(5 * th + 1.0) + 0.03 * np.sin(9 * th + 2.0))
    sect = np.floor(np.mod(th, 2 * math.pi) / (2 * math.pi) * 18)
    rimh = 2.0 * np.floor(3.0 * E.nzv(sect, 77))
    body = (rh <= r) & (Yl >= YB) & (Yl <= YT)
    rim = (rh <= RR + 1) & (rh > RR - 4.0) & (Yl > YT) & (Yl <= YT + rimh)
    m = body | rim
    crater = (rh < RR - 4.0) & (Yl > YT - 9.0)
    shaft = (rh < 7.0) & (Yl > 72.0)
    m &= ~crater & ~shaft
    shell = rh > r - 2.5
    mat = np.full(m.shape, E.MAT["basalt"], np.int64)
    mat = np.where(shell & (f > 0.72), E.MAT["ash"], mat)
    mat = np.where(shell & (f > 0.6) & (f <= 0.72) & (E.nzv(Xs, zs) < 0.5), E.MAT["ash"], mat)
    mat = np.where(rim | (Yl > YT - 2), E.MAT["slag"], mat)
    for k, a in enumerate(RIVS):
        wig = a + 0.10 * np.sin(Yl / 7.0 + k)
        da = np.abs(np.mod(th - wig + math.pi, 2 * math.pi) - math.pi) * np.maximum(rh, 1)
        mat = np.where(shell & (da < 2.2) & (f < 0.97) & (f > 0.03), E.MAT["lava"], mat)
    floor = (rh < RR - 4.0) & (Yl > YT - 11.0)
    mat = np.where(floor, E.MAT["crust"], mat)
    mat = np.where(floor & (rh < 11.0), E.MAT["lava"], mat)
    wall = (rh >= 7.0) & (rh < 9.5) & (Yl > 72.0) & (Yl < YT - 11.0)
    mat = np.where(wall, np.where(Yl < 104.0, E.MAT["ember"], E.MAT["deepred"]), mat)
    mat = np.where((rh < 7.0) & (Yl > 70.0) & (Yl <= 72.0), E.MAT["molten"], mat)
    return m, mat


def smoke_puffs(puffs):
    """煙の房。房ごとに別の骨 (smoke_N)。塊を 1 つ足して、角ばった房にする (ラフと同じ形)"""
    for i, (cx, cy, cz, h) in enumerate(puffs):
        part = SMOKE_PART[i]
        mat = E.MAT["smoke"] if i % 3 != 1 else E.MAT["smoke2"]
        E.solid_box((cx - h, cx + h, cy - h, cy + h, cz - h, cz + h), part, mat)
        o = h * 0.55
        E.solid_box((cx - o + h * 0.5, cx + o + h * 0.5, cy + h * 0.2, cy + h * 0.2 + 2 * o,
                     cz - o - h * 0.4, cz + o - h * 0.4), part, E.MAT["smoke"])


def build_ridge():
    P = PART
    E.fill(ridge_cone, (-62, 62, YB - 1, YT + 8, ZV - 62, ZV + 62), P["ridge"])
    # 玄武の柱の群れ (火山の裾)
    for gi, (gx, gz, n) in enumerate(COLS):
        for j in range(n):
            ang = 2 * math.pi * E.hz(gi, j, 1)
            rr = 9.0 * math.sqrt(E.hz(gi, j, 2))
            px = math.floor((gx + rr * math.cos(ang)) / 3.0) * 3.0
            pz = math.floor((gz + rr * math.sin(ang)) / 3.0) * 3.0
            base = float(E.topf(np.array(px + 1.5), pz + 1.5))
            if base < 30:
                continue
            h = 6.0 + 2.0 * math.floor(7.0 * E.hz(gi, j, 3))
            E.solid_box((px, px + 3, base - 2, base + h, pz, pz + 3), P["ridge"], E.MAT["column"])
    # 溶岩の流れ (ラフと同じ)
    for side, zf in ((-1, ZV + 16.0), (1, ZV - 6.0)):
        E.lavafall(zf - 2.0, zf + 2.0, side, 64.0)
    # 火口の煙の根元 (煙の柱の根元は背の一部。井戸の真上は空ける)
    E.solid_box((5.0, 9.0, YT - 9.0, YT + 12.0, ZV + 6.0, ZV + 10.0), P["ridge"], E.MAT["smoke2"])
    E.solid_box((3.0, 9.0, YT + 6.0, YT + 12.0, ZV + 8.0, ZV + 14.0), P["ridge"], E.MAT["smoke2"])
    smoke_puffs(PUFFS)


def back_crack(z0, z1):
    """背の後ろ半分の光る割れ目 (ラフ C の割れ目と同じ作り)。火守りの谷の天井。底は溶岩、壁の下は熾火"""
    def crack(X, Y, z):
        zs = math.floor(z) + 0.5
        c = float(E.crack_cx(zs))
        hw = float(E.crack_hw(zs))
        Xs = E.q1(X)
        fl = min(50.0, 2.0 * round(float(E.topf(np.array(c), zs)) / 2.0) - 10.0)
        return (np.abs(Xs - c) <= hw) & (Y > fl), 0
    E.fill(crack, (-16, 16, 20, 120, z0, z1), mode="zero")

    def skin(X, Y, z):
        zs = math.floor(z) + 0.5
        c = float(E.crack_cx(zs))
        hw = float(E.crack_hw(zs))
        Xs = E.q1(X)
        d = np.abs(Xs - c)
        tp = np.round(E.topf(Xs, zs))
        fl = min(50.0, 2.0 * round(float(E.topf(np.array(c), zs)) / 2.0) - 10.0)
        floor = (d <= hw) & (Y > fl - 2.0) & (Y <= fl)
        wall = (d > hw) & (d <= hw + 1.5) & (Y > fl - 2.0)
        lip = (d > hw) & (d <= hw + 3.0) & (Y > tp - 2.0)
        m = floor | wall | lip
        mat = np.where(floor, E.MAT["lava"],
                       np.where(wall & (Y <= fl + 7.0), E.MAT["ember"],
                                np.where(wall & (Y <= fl + 11.0), E.MAT["deepred"], E.MAT["slag"])))
        return m, mat
    E.fill(skin, (-18, 18, 20, 120, z0, z1), PART["ridge"], mode="replace")


def body_fn_var(glow_rate, notch):
    """ラフの E.body_fn と同じ体。板の継ぎ目の段の深さだけ変える (notch = (溝の 1 単位の深さ, 2 単位の深さ))。
    継ぎ目の材料 (暗い鉱滓・溶岩の線) は変えない"""
    def fn(X, Y, z):
        Xc = E.q1(X[:, :1])
        zs = math.floor(z) + 0.5
        w, t, b, yc = (float(v) for v in E.prof(zs))
        cx = float(E.cx_of(zs))
        s = Xc - cx
        costal = (-92 < zs < 104) and (zs % 9.0 < 1.0)
        weff = w - 2.0 if costal else w
        s2 = E.q2(X[:, :1]) - cx
        ue = np.abs(s2) / weff
        Yraw = E.q2(Y[:1, :])
        Yq = E.q1(Y[:1, :])
        yt = E.ytop_raw(ue, t, yc)
        low = (ue ** 2.5 + np.abs((yc - Yraw) / (yc - b)) ** 4) <= 1.0
        raw = (ue <= 1.0) & np.where(Yraw >= yc, Yraw <= yt, low)
        c, rz, px, pz, uc, ptop, d, dx, dz, wc = E.plate_info(s, zs)
        zone = (uc <= 0.7) & (wc > 4.0)
        colTop = np.where(d < 1.0, ptop - notch[0], np.where(d < 2.0, ptop - notch[1], ptop))
        pcell = (Yq <= colTop)
        upper = zone & (Yq >= yc - 2.0)
        inside = np.where(upper, pcell, raw)
        mat = np.full(inside.shape, E.MAT["sbasalt"], np.int64)
        mat = np.where(zone, E.MAT["basalt"], mat)
        groove = zone & (d < 1.0)
        mat = np.where(groove, E.seam_mat(c, rz, px, pz, s, zs, dx, dz, glow_rate), mat)
        if costal:
            mat = np.where(~zone & (np.abs(s2) > weff - 2.5), E.MAT["slag"], mat)
        belly_line = b + (yc - b) * 0.75
        mat = np.where(Yraw < belly_line, E.MAT["belly"], mat)
        return inside, mat
    return fn


def make_leg_notoe(front, side):
    """ラフの make_leg と同じ腕・脚 (肘を外に張る) だが、指と爪は付けない (ラフは 5 本の指 + 爪を付ける)"""
    sx = side
    if front:
        S, S2, W = (44 * sx, 24, -76), (84 * sx, 28, -82), (96 * sx, 6, -100)
        palm = (98 * sx, 3.5, -106)
        r = (13.0, 11.0, 7.5)
    else:
        S, S2, W = (42 * sx, 24, 84), (84 * sx, 28, 92), (96 * sx, 6, 112)
        palm = (98 * sx, 3.5, 118)
        r = (14.5, 12.0, 8.0)
    segs = [(S, S2, r[0], r[1]), (S2, W, r[1], r[2]), (W, palm, r[2], 6.0)]
    pts = np.array([S, S2, W, palm])
    lo = pts.min(0) - 16
    hi = pts.max(0) + 16
    return E.limb_fn(segs, [], side), (lo[0], hi[0], 0.0, hi[1], lo[2], hi[2])


def build_grid():
    """ラフの体 (頭・顎・脚・胴・尾) に、この版の背を足し、格子を詰め、X を反転し、段をそろえる"""
    E.K = np.zeros((E.NX, E.NY, E.NZ), np.int16)
    E.body_fn = lambda g: body_fn_var(g, NOTCH)     # build_body が呼ぶ体の関数だけを差し替える
    E.make_leg = make_leg_notoe                      # 脚の爪 (指) は箱を増やすので外す (脚は先が丸い)
    E.build_body(0.5)
    E.compute_hm()
    build_ridge()
    back_crack(12.0, 100.0)
    E.drop_islands()
    K0 = E.K
    K = np.zeros((NX, NY, NZ), np.int16)
    K[PAD:PAD + E.NX, :, PAD:PAD + E.NZ] = K0
    E.K = None
    del K0
    K = np.ascontiguousarray(K[::-1])              # X を反転 (格子は x に対して対称なので、番号を反転すれば世界の X も反転する)
    # 火山の円錐の下の方 (広い所) は形を 4 単位の段にそろえ、絵は段をそろえる前の細かい格子から塗る
    # (箱は円錐で 1000 を超えていた。溶岩の流れの絵は細いまま残る)
    global CONE_FINE
    cx0, cx1, cy0, cy1, cz0, cz1 = CONE_BOX
    CONE_FINE = K[cx0:cx1, cy0:cy1, cz0:cz1].copy()
    region = np.zeros(K.shape, bool)
    xs = GX0 + (np.arange(cx0, cx1) + 0.5) / CELL
    zs = GZ0 + (np.arange(cz0, cz1) + 0.5) / CELL
    X, Z = np.meshgrid(xs, zs, indexing="ij")
    inside = np.hypot(X, Z - ZV) < RB + 2.0
    region[cx0:cx1, cj(YB - 4):cj(CONE_COARSE_TOP), cz0:cz1] = inside[:, None, :]
    snap_blocks(K, [PART["ridge"]], COARSE_Q, protect=~region)
    del region
    # 胴の脇と腹 (背の板より下、geo y < BODY_LOW_Y) は 4 単位の段。脇は眠るときも地面の近くで、背の板ほど細かく見せなくてよい
    low = np.zeros(K.shape, bool)
    low[:, :cj(BODY_LOW_Y), :] = True
    snap_blocks(K, [PART["body"]], COARSE_Q, protect=~low)
    del low
    snap_blocks(K, [PART["body"], PART["ridge"]], SNAP_Q)
    # 脚と尾は 4 単位 (2 ブロック) の段 (近景の箱を 2500 前後にするため。脚は地に埋まり、尾は細い)
    snap_blocks(K, [PART["tail"]] + [PART[n] for n in ("leg_fl", "leg_fr", "leg_rl", "leg_rr")], COARSE_Q)
    # 頭: 歯・目・喉・口の中 (守る cells) は細かいまま、頭の外側の板と骨は段にそろえる
    part, mat = K // 32, K % 32
    protect = np.isin(mat, PROT_MATS) & np.isin(part, [PART["head"], PART["jaw"]])
    box = np.zeros(K.shape, bool)
    box[ci(-14):ci(14), cj(4):cj(20), ck(-208):ck(-138)] = True
    protect |= box & np.isin(part, [PART["head"], PART["jaw"]])
    del part, mat, box
    snap_blocks(K, [PART["head"]], HEAD_Q, protect=protect)
    del protect
    recarve_crater(K)
    snap_body_tops(K, SNAP_Q)
    return K


def recarve_crater(K):
    """段をそろえると、火口の井戸と煙の穴 (ラフの空洞) が背の箱でふさがる。空洞の所だけ背のセルを消す
    (体・割れ目・煙の根元は残す)"""
    x0, x1 = ci(-RR - 2), ci(RR + 2) + 1
    z0, z1 = ck(ZV - RR - 2), ck(ZV + RR + 2) + 1
    sub = K[x0:x1, :, z0:z1]
    xs = GX0 + (np.arange(x0, x1) + 0.5) / CELL
    zs = GZ0 + (np.arange(z0, z1) + 0.5) / CELL
    ys = GY0 + (np.arange(K.shape[1]) + 0.5) / CELL
    X, Z = np.meshgrid(xs, zs, indexing="ij")
    rh = np.hypot(X, Z - ZV)
    crater = (rh < RR - 4.0)[:, None, :] & (ys > YT - 9.0)[None, :, None]
    shaft = (rh < 7.0)[:, None, :] & (ys > 72.0)[None, :, None]
    ridge = (sub // 32) == PART["ridge"]
    sub[(crater | shaft) & ridge] = 0
    print("crater recarved", int(((crater | shaft) & ridge).sum()), "cells")


def snap_body_tops(K, q):
    """甲羅 (体) の上面を、q セル (2 単位) の段にそろえる。上へ出す所は空いているセルだけ、下げる所は
    上に他の骨が無いときだけ削る。形の差は 1 単位以内"""
    occ = (K // 32) == PART["body"]
    has = occ.any(1)
    top = K.shape[1] - 1 - np.argmax(occ[:, ::-1, :], axis=1)
    new = (np.round((top + 1) / q) * q - 1).astype(int)
    for i, k in np.argwhere(has & (new != top)):
        t, n = int(top[i, k]), int(new[i, k])
        key = int(K[i, t, k])
        if n > t:
            seg = K[i, t + 1:n + 1, k]
            seg[seg == 0] = key
        else:
            if np.any(K[i, t + 1:, k] != 0):
                continue
            K[i, n + 1:t + 1, k] = 0


def snap_blocks(K, parts, q, protect=None):
    """骨ごとに、q x q x q セル (1 単位 = 2 セル... ここでは 2 単位) の段にそろえる。
    ブロックの半分以上がその骨なら、ブロック全部をその骨にする (空いているセルも埋める)。
    材料は、光る材料が 1/4 以上あればそれ、なければ多数決。足の先のように細い骨は、残りの部分を消さない"""
    a, b, c = (s // q for s in K.shape)
    B = K.reshape(a, q, b, q, c, q).transpose(0, 2, 4, 1, 3, 5).reshape(a, b, c, q ** 3).copy()
    PB = None
    if protect is not None:
        PB = protect.reshape(a, q, b, q, c, q).transpose(0, 2, 4, 1, 3, 5).reshape(a, b, c, q ** 3)
    half = q ** 3 / 2
    drop_partial = {PART["body"], PART["tail"], PART["leg_fl"], PART["leg_fr"], PART["leg_rl"], PART["leg_rr"],
                    PART["ridge"]}
    for p in parts:
        any_b = (B // 32 == p).any(-1)
        if not any_b.any():
            continue
        ii, jj, kk = np.nonzero(any_b)
        sl = (slice(ii.min(), ii.max() + 1), slice(jj.min(), jj.max() + 1), slice(kk.min(), kk.max() + 1))
        S = B[sl]
        m = (S // 32) == p
        pr = None if PB is None else PB[sl]
        if pr is not None:
            m = m & ~pr                              # 守る cells (歯・目・喉など) は段にそろえない
        mat = S % 32
        cnt = m.sum(-1)
        full = cnt >= half
        # 光る材料の細い筋 (背の溶岩の流れ) は、2 セルでも段の 1 つとして残す (ほかの骨は 1/4 以上)
        gthr = 2 if p in GLOW_PARTS else q ** 3 / 4
        glow_n = (m & np.isin(mat, GLOW_MATS)).sum(-1)
        if p in GLOW_PARTS:
            full = full | (glow_n >= gthr)
        best_s = np.full(cnt.shape, -1, np.int32)
        best_v = np.zeros(cnt.shape, np.int16)
        for v in np.unique(mat[m]).tolist():
            nv = (m & (mat == v)).sum(-1).astype(np.int32)
            if v in GLOW_MATS and nv.max() >= gthr:
                sc = np.where(nv >= gthr, 100000 + nv, nv)
            else:
                sc = nv
            upd = (sc > best_s) & (nv > 0)
            best_s = np.where(upd, sc, best_s)
            best_v = np.where(upd, v, best_v)
        newkey = (p * 32 + best_v).astype(np.int16)[..., None]
        fillable = (S == 0) if pr is None else ((S == 0) & ~pr)
        setm = full[..., None] & (m | fillable)
        S2 = np.where(setm, newkey, S).astype(np.int16)
        if p in drop_partial:
            S2 = np.where((~full)[..., None] & m, np.int16(0), S2).astype(np.int16)
        B[sl] = S2
    K[...] = B.reshape(a, b, c, q, q, q).transpose(0, 3, 1, 4, 2, 5).reshape(K.shape)


def _group_crop(P, parts):
    """格子 P のうち、組 parts のセルを含む最小の箱 (まわりに 1 セルの余白) の slice。なければ None。
    (メモリ: ラベル付けと膨張を、格子全体でなくこの箱の中だけにする。結果は同じ)"""
    m = np.isin(P, parts)
    if not m.any():
        return None, None
    sl = []
    for ax in range(3):
        other = tuple(a for a in range(3) if a != ax)
        idx = np.flatnonzero(m.any(axis=other))
        sl.append(slice(max(int(idx[0]) - 1, 0), min(int(idx[-1]) + 2, P.shape[ax])))
    sl = tuple(sl)
    return sl, m[sl]


def islands_fix(G, label):
    """組ごとに、一番大きい塊以外を消す (別の骨に接する塊は、その骨に移す)。ログを返す"""
    P = G // 32
    log = []
    for name, parts in GROUPS.items():
        sl, m = _group_crop(P, parts)
        if sl is None:
            continue
        lab, n = ndimage.label(m)
        if n <= 1:
            continue
        sizes = ndimage.sum(m, lab, range(1, n + 1))
        big = int(np.argmax(sizes)) + 1
        Gs = G[sl]
        for i in range(1, n + 1):
            if i == big:
                continue
            comp = lab == i
            ring = ndimage.binary_dilation(comp) & ~comp
            touch = Gs[ring] // 32
            touch = touch[(touch != 0) & ~np.isin(touch, parts)]
            if touch.size:
                vals, cnt = np.unique(touch, return_counts=True)
                to = int(vals[np.argmax(cnt)])
                Gs[comp] = to * 32 + Gs[comp] % 32
                log.append("%s %s: %d セルの塊を %s に移した" % (label, name, int(sizes[i - 1]), PART_BONE[to]))
            else:
                Gs[comp] = 0
                log.append("%s %s: %d セルの浮いた塊を消した" % (label, name, int(sizes[i - 1])))
        # 次の組が見る P も、移したセルの分だけ直す (元は組ごとに P を作り直さず、G の変更を見ない。同じにするため P は触らない)
    return log


def islands(G):
    P = G // 32
    out = {}
    for name, parts in GROUPS.items():
        sl, m = _group_crop(P, parts)
        if sl is None:
            continue
        n = ndimage.label(m)[1]
        if n > 1:
            out[name] = n - 1
    return out


# ---------------------------------------------------------------- 中景・遠景の格子

def mounds(K):
    """中景・遠景: 裾の玄武の柱を、群れごとに 1 つの丘にまとめる (空いているセルだけ埋める)。
    柱の上端まで、中心から半径 MOUND_R の円柱。柱のない所は何もしない"""
    colm = ((K // 32) == PART["ridge"]) & ((K % 32) == E.MAT["column"])
    for cx, cz in COL_CENTERS:
        sub = colm[ci(cx - 14):ci(cx + 14), :, ck(cz - 14):ck(cz + 14)]
        idx = np.argwhere(sub)
        if idx.size == 0:
            continue
        top = int(idx[:, 1].max()) + 1                  # sub は x と z だけ切ったので、y は格子の番号のまま
        i0, i1 = ci(cx - MOUND_R), ci(cx + MOUND_R) + 1
        k0, k1 = ck(cz - MOUND_R), ck(cz + MOUND_R) + 1
        j0, j1 = cj(30.0), min(top + 1, K.shape[1])
        xs = GX0 + (np.arange(i0, i1) + 0.5) / CELL
        zs = GZ0 + (np.arange(k0, k1) + 0.5) / CELL
        X, Z = np.meshgrid(xs, zs, indexing="ij")
        disk = (np.hypot(X - cx, Z - cz) <= MOUND_R)
        sub2 = K[i0:i1, j0:j1, k0:k1]
        for jj in range(sub2.shape[1]):
            layer = sub2[:, jj, :]
            w = disk & (layer == 0)
            layer[w] = PART["ridge"] * 32 + E.MAT["column"]
    return K


def downsample(K, f):
    """f x f x f のセルを 1 つにする。骨は多数決 (細い骨は低い割合でも残す)。材料は、光る材料が
    GLOW_MIN 個あればそれ (どの骨の段でも)、なければ多数決。戻り値は (a, b, c) の骨 * 32 + 材料"""
    sh = [(-(-n // f)) * f for n in K.shape]
    P = np.zeros(sh, np.int16)
    P[:K.shape[0], :K.shape[1], :K.shape[2]] = K
    a, b, c = (s // f for s in sh)
    blk = P.reshape(a, f, b, f, c, f).transpose(0, 2, 4, 1, 3, 5).reshape(a, b, c, f ** 3)
    occ = np.argwhere((blk != 0).any(-1))
    cells = blk[occ[:, 0], occ[:, 1], occ[:, 2]]
    part = cells // 32
    mat = cells % 32
    solid = cells != 0
    cnt_p = np.stack([(part == p).sum(1) for p in range(1, NPARTS + 1)], 1)
    pm = cnt_p.argmax(1) + 1
    cp = cnt_p.max(1)
    pmask = (part == pm[:, None]) & solid
    mc = np.stack([((mat == v) & pmask).sum(1) for v in range(32)], 1)
    mc_plain = mc.copy()
    mc_plain[:, [0] + GLOW_MATS] = -1
    plain_ok = mc_plain.max(1) >= 1
    plain_pick = mc_plain.argmax(1)
    glow_pick_pm = np.array(GLOW_MATS)[mc[:, GLOW_MATS].argmax(1)]
    key_plain = np.where(plain_ok, pm * 32 + plain_pick, pm * 32 + glow_pick_pm)
    glow_m = np.isin(mat, GLOW_MATS) & solid
    gn = glow_m.sum(1)
    use_glow = gn >= GLOW_MIN[f]
    gcnt = np.stack([((mat == v) & glow_m).sum(1) for v in GLOW_MATS], 1)
    glow_pick = np.array(GLOW_MATS)[gcnt.argmax(1)]
    gp = np.stack([((part == p) & glow_m).sum(1) for p in range(1, NPARTS + 1)], 1)
    glow_part = gp.argmax(1) + 1
    key_out = np.where(use_glow, glow_part * 32 + glow_pick, key_plain)
    thr = np.where(np.isin(pm, list(THIN_PARTS)), THIN_FRAC, THICK_FRAC) * f ** 3
    keep = (cp >= thr) | use_glow
    out = np.zeros((a, b, c), np.int16)
    sel = occ[keep]
    out[sel[:, 0], sel[:, 1], sel[:, 2]] = key_out[keep].astype(np.int16)
    return out


# ---------------------------------------------------------------- 箱にまとめる (骨ごと。tide_shell と同じ)

def _runs(K):
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
    """骨ごとに合わせる。[骨の番号, x0, x1, y0, y1, z0, z1] (格子の番号)。材料は見ない (面の画素は裏のセルで塗る)"""
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
    """1 つの LOD の箱。戻り値は (箱の一覧, 箱の数 (見える面がない箱を除く前))"""
    unit = f * 0.5
    boxes = mesh_bones(G)
    cubes = []
    for bx in boxes:
        p, x0, x1, y0, y1, z0, z1 = (int(v) for v in bx)
        ex = face_exposed(G, p, bx)
        if not any(ex.values()):
            continue
        cells = (x1 - x0, y1 - y0, z1 - z0)
        o = (GX0 + x0 * unit, GY0 + y0 * unit, GZ0 + z0 * unit)
        s = (cells[0] * unit, cells[1] * unit, cells[2] * unit)
        faces = {fn: face_px(cells, fn, unit * ppu) for fn, vis in ex.items() if vis}
        cubes.append(dict(part=p, o=o, s=s, faces=faces, box=(x0, x1, y0, y1, z0, z1), key=p * 32 + 1))
    return cubes, len(boxes)


# ---------------------------------------------------------------- テクスチャ (面の画素ごとに、裏のセルの材料)

def paint_face(G, f, cube, fname, w, h, tex, glow, x0, y0):
    """面の画素を、裏のセルの骨と材料で塗る (ラフの mat_base をそのまま使う)。戻り値は光る画素の数"""
    unit = f * 0.5
    P = E.world_grid(cube["o"], cube["s"], fname, w, h)
    ax, d = FACE_AXIS[fname]
    Q = P.copy()
    Q[..., ax] -= d * unit * 0.5
    gi = np.clip(np.floor((Q[..., 0] - GX0) * CELL / f).astype(int), 0, G.shape[0] - 1)
    gj = np.clip(np.floor((Q[..., 1] - GY0) * CELL / f).astype(int), 0, G.shape[1] - 1)
    gk = np.clip(np.floor((Q[..., 2] - GZ0) * CELL / f).astype(int), 0, G.shape[2] - 1)
    keys = G[gi, gj, gk].astype(np.int64)
    if f == 1 and CONE_FINE is not None:
        # 円錐の段をそろえた所は、そろえる前の細かい格子の材料で塗る (同じ骨のセルがあるときだけ。少し奥も見る)
        cx0, cx1, cy0, cy1, cz0, cz1 = CONE_BOX
        for depth in (0.0, 1.0, 2.0):
            R = P.copy()
            R[..., ax] -= d * (0.25 + depth)
            fi = np.floor((R[..., 0] - GX0) * CELL).astype(int) - cx0
            fj = np.floor((R[..., 1] - GY0) * CELL).astype(int) - cy0
            fk = np.floor((R[..., 2] - GZ0) * CELL).astype(int) - cz0
            ok = (fi >= 0) & (fi < cx1 - cx0) & (fj >= 0) & (fj < cy1 - cy0) & (fk >= 0) & (fk < cz1 - cz0)
            fine = np.zeros(keys.shape, np.int64)
            fine[ok] = CONE_FINE[fi[ok], fj[ok], fk[ok]]
            use = ok & (fine != 0) & (fine // 32 == keys // 32) & (keys // 32 == PART["ridge"])
            if depth == 0.0:
                done = np.zeros(keys.shape, bool)
            use &= ~done
            keys[use] = fine[use]
            done |= use
    keys[keys == 0] = cube["key"]
    if fname in ("up", "down"):
        A, B = P[..., 0], P[..., 2]
    elif fname in ("north", "south"):
        A, B = P[..., 0], P[..., 1]
    else:
        A, B = P[..., 2], P[..., 1]
    A2 = np.floor(A * CELL).astype(np.int64)
    B2 = np.floor(B * CELL).astype(np.int64)
    Yw = P[..., 1]
    out_t = np.zeros((h, w, 3), np.float64)
    out_g = np.zeros((h, w), bool)
    for key in np.unique(keys):
        part, mat = int(key // 32), int(key % 32)
        if f > 1 and mat == E.MAT["seam"]:
            mat = E.MAT["slagdark"]                  # 中景・遠景では背の継ぎ目を光らせない (暗い玄武の背を保つ)
        sel = keys == key
        base, gm = E.mat_base(mat, A2, B2, part, fname, Yw)
        out_t[sel] = np.clip(base, 0, 255)[sel]
        out_g[sel] = (gm & sel)[sel]
    tex[y0:y0 + h, x0:x0 + w, :3] = out_t.astype(np.uint8)
    tex[y0:y0 + h, x0:x0 + w, 3] = 255
    gl = glow[y0:y0 + h, x0:x0 + w]
    gl[out_g, :3] = out_t[out_g].astype(np.uint8)
    gl[out_g, 3] = 255
    return int(out_g.sum())


def pack_width(items, W):
    """棚詰め。幅 W に入れ、高さは必要なだけ使う (画素の間は 1 画素あける)。items = [(id, w, h)]"""
    pos, x, y, shelf = {}, 1, 0, 0
    for k, w, h in sorted(items, key=lambda it: (-it[2], -it[1], str(it[0]))):
        if x + w + 1 > W:
            x, y, shelf = 0, y + shelf + 1, 0
        pos[k] = (x, y)
        x += w + 1
        shelf = max(shelf, h)
    return pos


# ---------------------------------------------------------------- geo・アニメ

def make_geo(cubes_by_bone, H):
    bones = []
    for name in BONE_ORDER:
        b = {"name": name, "pivot": PIVOT[name]}
        if name in PARENT:
            b["parent"] = PARENT[name]
        if cubes_by_bone[name]:
            b["cubes"] = cubes_by_bone[name]
        bones.append(b)
    return {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry.ember_titan", "texture_width": ATLAS_W, "texture_height": H,
        "visible_bounds_width": 480, "visible_bounds_height": 200, "visible_bounds_offset": [0, 60, 0]},
        "bones": bones}]}


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


# アニメ。骨ごとに {"rot": [x, y, z], "pos": [x, y, z], "scale": [x, y, z], "keys": {時刻: (x, y, z)}}
# 各軸の値は None (なし) か次のどれか: ("sin", c, A, f, p) = c + A sin(f t + p) (度・単位)、
# ("saw", a0, a1, 周期, p) = a0 + (a1 - a0) * ((t + p) の周期での余り) / 周期 (煙の房の上がりと縮み。戻ると不連続)。
# 意図の角度で書く。ファイルへは SIGN (rot の X と Z は -1、rot の Y は +1、pos の X は -1) を掛ける。
SIGN_ROT = (-1, 1, -1)
SIGN_POS = (-1, 1, 1)
ANIM = {
    "animation.ember_titan.swim": dict(length=8.0, loop=True, bones={
        # 脚は対角の二つずつ (fl と rr、fr と rl) が逆位相で、地中をかく。肩の回りに ±18 度
        "leg_fl": {"rot": [None, ("sin", 0, 18, 45, 0), None]},
        "leg_rr": {"rot": [None, ("sin", 0, 18, 45, 0), None]},
        "leg_fr": {"rot": [None, ("sin", 0, 18, 45, 180), None]},
        "leg_rl": {"rot": [None, ("sin", 0, 18, 45, 180), None]},
        "tail": {"rot": [None, ("sin", 0, 4, 45, 90), None]},
        "head": {"rot": [None, ("sin", 0, 2, 45, 0), None]},
    }),
    "animation.ember_titan.sleep": dict(length=6.0, loop=True, bones={
        # 眠り: 体が 0〜0.6 単位だけ上下に息をする。脚は止まる。尾は少し
        "body": {"pos": [None, ("sin", 0.3, 0.3, 60, 0), None]},
        "tail": {"rot": [None, ("sin", 0, 1.0, 60, 0), None]},
    }),
    "animation.ember_titan.breath_in": dict(length=8.0, loop="hold_on_last_frame", bones={
        # 息を吸う: 顎が 6 度下がる (意図 X = -6)、頭が 2 度上がる (意図 X = +2)。最後の形で止まる
        "jaw": {"keys": {0.0: (0, 0, 0), 8.0: (-6, 0, 0)}},
        "head": {"keys": {0.0: (0, 0, 0), 8.0: (2, 0, 0)}},
    }),
    "animation.ember_titan.breath_out": dict(length=6.0, loop="hold_on_last_frame", bones={
        "jaw": {"keys": {0.0: (-6, 0, 0), 6.0: (0, 0, 0)}},
        "head": {"keys": {0.0: (2, 0, 0), 6.0: (0, 0, 0)}},
    }),
    "animation.ember_titan.smoke": dict(length=12.0, loop=True, bones=dict(
        **{"smoke_%d" % i: {"pos": [None, ("saw", 0, 24, 12, 3 * i), None],
                            "scale": [("saw", 1, 0.35, 12, 3 * i)] * 3} for i in range(4)})),
}


def _num(v):
    return ("%.3f" % v).rstrip("0").rstrip(".") if abs(v) > 1e-9 else "0"


def mol_channel(spec, s):
    kind = spec[0]
    if kind == "sin":
        _, c, A, f, p = spec
        parts = []
        if abs(c) > 1e-9:
            parts.append(_num(s * c))
        if abs(A) > 1e-9:
            parts.append("%s * math.sin(%s * query.anim_time + %s)" % (_num(s * A), _num(f), _num(p)))
        return " + ".join(parts) if parts else "0"
    _, a0, a1, per, p = spec
    return "%s + %s * math.mod(query.anim_time + %s, %s) / %s" % (
        _num(s * a0), _num(s * (a1 - a0)), _num(p), _num(per), _num(per))


def anim_json():
    out = {}
    for name, spec in ANIM.items():
        bj = {}
        for bone, ch in spec["bones"].items():
            entry = {}
            if "keys" in ch:
                entry["rotation"] = {"%.1f" % t: [round(SIGN_ROT[i] * v[i], 3) + 0.0 for i in range(3)]
                                     for t, v in sorted(ch["keys"].items())}
            if "rot" in ch:
                entry["rotation"] = [mol_channel(sp, SIGN_ROT[i]) if sp else "0" for i, sp in enumerate(ch["rot"])]
            if "pos" in ch:
                entry["position"] = [mol_channel(sp, SIGN_POS[i]) if sp else "0" for i, sp in enumerate(ch["pos"])]
            if "scale" in ch:
                entry["scale"] = [mol_channel(sp, 1) if sp else "1" for sp in ch["scale"]]
            bj[bone] = entry
        out[name] = {"loop": spec["loop"], "animation_length": spec["length"], "bones": bj}
    return {"format_version": "1.8.0", "animations": out}


def value_at(spec, t):
    """意図の値 (時刻 t)。sin と saw は式、keys は直線の補間 (最後の値で止まる)。keys の時刻は単位が秒"""
    if spec is None:
        return 0.0
    if spec[0] == "sin":
        _, c, A, f, p = spec
        return c + A * math.sin(math.radians(f * t + p))
    _, a0, a1, per, p = spec

    return a0 + (a1 - a0) * (((t + p) % per) / per)


def pose_at(name, t):
    """時刻 t の意図の姿勢: rot {骨: (x, y, z) 度}、pos {骨: (x, y, z)}、scale {骨: s}。プレビュー用"""
    spec = ANIM[name]
    rot, pos, scale = {}, {}, {}
    for bone, ch in spec["bones"].items():
        if "keys" in ch:
            keys = sorted(ch["keys"].items())
            if t >= keys[-1][0]:
                v = keys[-1][1]
            else:
                v = keys[0][1]
                for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
                    if t0 <= t <= t1:
                        u = (t - t0) / (t1 - t0)
                        v = tuple(a + (b - a) * u for a, b in zip(v0, v1))
                        break
            rot[bone] = tuple(float(x) for x in v)
            continue
        if "rot" in ch:
            rot[bone] = tuple(value_at(sp, t) for sp in ch["rot"])
        if "pos" in ch:
            pos[bone] = tuple(value_at(sp, t) for sp in ch["pos"])
        if "scale" in ch:
            scale[bone] = value_at(ch["scale"][0], t)
    # 煙の房の縮みは 1 軸の値として使う
    return rot, pos, scale


def posed_geo(geo, pos, scale):
    """プレビュー用: 位置と縮みを箱の座標に直す (ファイルの座標。位置の X は反転)。body の位置は全部の箱へ"""
    g = copy.deepcopy(geo)
    shift = pos.get("body", (0.0, 0.0, 0.0))
    for b in g["minecraft:geometry"][0]["bones"]:
        name = b["name"]
        piv = np.array(b.get("pivot", [0, 0, 0]), float)
        sc = scale.get(name, 1.0)
        pv = np.array(pos.get(name, (0.0, 0.0, 0.0)), float)
        sh = np.array([SIGN_POS[0] * (shift[0]), shift[1], shift[2]], float)
        sv = np.array([SIGN_POS[0] * pv[0], pv[1], pv[2]], float)
        for c in b.get("cubes", []):
            o = np.array(c["origin"], float)
            s = np.array(c["size"], float)
            if sc != 1.0:
                o = piv + sc * (o - piv)
                s = s * sc
            o = o + sh + sv
            c["origin"] = o.tolist()
            c["size"] = s.tolist()
    return g


# ---------------------------------------------------------------- 確認用の絵

def mirror_faces(faces):
    """ゲームの向き (X を反転) にする。面の表裏が入れ替わるので、横の辺の向きを逆にし、画素も横に反転する"""
    m = np.array([-1, 1, 1])
    out = []
    for f in faces:
        g = None if f.glow is None else f.glow[:, ::-1]
        out.append(Face((f.origin + f.eu) * m, -f.eu * m, f.ev * m, f.tex[:, ::-1], g, f.shade))
    return out


def marker(center, size, color):
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


def box_outline(lo, hi, color, t=0.7):
    lo, hi = np.array(lo, float), np.array(hi, float)
    out = []
    for ax in range(3):
        for a in (0, 1):
            for b in (0, 1):
                c = [0.0, 0.0, 0.0]
                s = [t, t, t]
                others = [i for i in range(3) if i != ax]
                c[ax] = (lo[ax] + hi[ax]) / 2
                s[ax] = hi[ax] - lo[ax]
                c[others[0]] = lo[others[0]] if a == 0 else hi[others[0]]
                c[others[1]] = lo[others[1]] if b == 0 else hi[others[1]]
                out += marker(c, s, color)
    return out


def load_posed(geo, tex, glow, rot=None, pos=None, scale=None):
    g = posed_geo(geo, pos or {}, scale or {}) if (pos or scale) else geo
    return mirror_faces(load_geo(g, tex, glowmask=glow, pose=rot or {}))


def preview_lods(geos, tex, glow, counts):
    cols = []
    for name, label in (("near", "近景 (0.5 単位の背、体 2 単位)"), ("mid", "中景 (4 単位、柱は 1 つの丘)"),
                        ("far", "遠景 (6 単位、火山と煙を塊に)")):
        fs = load_posed(geos[name], tex, glow)
        cols.append(("%s %d 箱" % (label, counts[name]),
                     [render(fs, yaw=90, pitch=0, size=(460, 460)),
                      render(fs, yaw=35, pitch=35, size=(460, 460)),
                      render(fs, yaw=180, pitch=8, size=(460, 460))]))
    sht = sheet("余燼の巨神 LOD (採用案 A 火山の背、ゲームの向き)", cols,
                note="行: 側面 / 斜め上 / 正面 (頭は手前)。LOD ごとの箱の数は見出し。")
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out = PREVIEW_DIR / "lods.png"
    sht.save(out)
    print("preview", out)


def preview_poses(geos, tex, glow):
    def pair(fs):
        return [render(fs, yaw=90, pitch=0, size=(400, 400)), render(fs, yaw=35, pitch=30, size=(400, 400))]

    def posed(name, t):
        rot, pos, scale = pose_at(name, t)
        return load_posed(geos["near"], tex, glow, rot=rot, pos=pos, scale=scale)

    cols = [("swim 2s (脚が対角に掻く)", pair(posed("animation.ember_titan.swim", 2.0))),
            ("swim 6s", pair(posed("animation.ember_titan.swim", 6.0))),
            ("sleep 3s (息)", pair(posed("animation.ember_titan.sleep", 3.0))),
            ("breath_in 8s (顎が下がる、頭が上がる)", pair(posed("animation.ember_titan.breath_in", 8.0))),
            ("breath_out 3s (途中)", pair(posed("animation.ember_titan.breath_out", 3.0))),
            ("smoke 6s (煙の房が上がって縮む)", pair(posed("animation.ember_titan.smoke", 6.0)))]
    # 口の前: 乗り込みの判定 (紫の枠 x -5..5, y 6..14, z -172..-160)。息を吸い切った姿
    rot, pos, scale = pose_at("animation.ember_titan.breath_in", 8.0)
    fs = load_posed(geos["near"], tex, glow, rot=rot)
    m = MOUTH
    frame = box_outline((m["x"][0], m["y"][0], m["z"][0]), (m["x"][1], m["y"][1], m["z"][1]), (255, 0, 255, 255))
    cols.append(("口の前 (紫の枠 = 乗り込みの判定)", [render(fs + frame, yaw=-20, pitch=10, size=(400, 400), margin=0.05),
                                              render(fs + frame, yaw=8, pitch=4, size=(400, 400), margin=0.05)]))
    # 顎の向きの確認: 意図 X = -6 で顎の先が下がる (voxel の回転と同じ向き)。数値で出す
    tip = np.array([0.0, 0.0, -206.0])
    rel = tip - np.array([0.0, 6.0, -150.0])
    closed = (np.array(_rot(0, 0, 0)) @ rel) + np.array([0.0, 6.0, -150.0])
    opened = (np.array(_rot(-6.0, 0, 0)) @ rel) + np.array([0.0, 6.0, -150.0])
    print("jaw tip y: rest %.2f, breath_in (intended X -6) %.2f" % (closed[1], opened[1]))
    sht = sheet("余燼の巨神 姿勢・口・水面 (ゲームの向き)", cols,
                note="頭は手前 (-Z)。顎の先: 休み y=%.1f、吸う (意図 X -6) y=%.1f。ファイルは X の符号を反転 (+6) して書く。"
                % (closed[1], opened[1]))
    out = PREVIEW_DIR / "poses.png"
    sht.save(out)
    print("preview", out)


def preview_far(geos, tex, glow):
    """遠景の読みやすさ: 3 つの LOD を 140px。地面の高さ: なし / y=120 (土の下の尾根だけが見える) / y=6 (眠り)"""
    planes = [("地面なし", []), ("地面 y=120 (土に潜る。尾根だけ見える)", marker((0, 120, 0), (420, 0.8, 420), (150, 90, 60, 255))),
              ("地面 y=6 (眠り)", marker((0, 6, 0), (420, 0.8, 420), (150, 90, 60, 255)))]
    cols = []
    for name in ("near", "mid", "far"):
        fs = load_geo(geos[name], tex, glowmask=glow)
        fs = mirror_faces(fs)
        ims = []
        for label, pl in planes:
            for yaw, pitch in ((90, 4), (35, 30)):
                ims.append(render(fs + pl if pl else fs, yaw=yaw, pitch=pitch, size=(140, 140), margin=0.04))
        cols.append((name, ims))
    sht = sheet("余燼の巨神 遠景の読みやすさ (140px)", cols,
                note="行: 地面なし (側面 / 斜め)、地面 y=120 (側面 / 斜め)、地面 y=6 (側面 / 斜め)。")
    out = PREVIEW_DIR / "far_read.png"
    sht.save(out)
    print("preview", out)


# ---------------------------------------------------------------- 本体

def main():
    K = build_grid()
    print("grid", K.shape, "non-zero cells", int((K != 0).sum()), flush=True)

    # 乗り込みの口と舌 (近景の格子で確かめる)
    m = MOUTH
    mouth_cells = K[ci(m["x"][0]):ci(m["x"][1]) + 1, cj(m["y"][0]):cj(m["y"][1]),
                    ck(m["z"][0]):ck(m["z"][1])]
    mouth_air = bool((mouth_cells == 0).all())
    assert mouth_air, "mouth trigger cells are not air in near LOD"
    tongue_hits = 0
    tongue_n = 0
    for z in np.arange(-200.0, -150.0, 2.0):
        tongue_n += 1
        seg = K[ci(-2.0):ci(2.0), cj(4.5):cj(6.0), ck(z):ck(z + 2.0)]
        tongue_hits += int((seg != 0).any())
    print("mouth trigger air:", mouth_air, "tongue columns:", tongue_hits, "/", tongue_n, flush=True)

    lods, geos, stats = {}, {}, {}
    for name, f, ppu in LODS:
        G = K if f == 1 else downsample(mounds(K.copy()), f)
        log = islands_fix(G, name)
        for line in log:
            print(line)
        left = islands(G)
        print(name, "islands left", left, flush=True)
        cubes, raw = build_lod(G, f, ppu)
        for i, c in enumerate(cubes):
            c["id"] = (name, i)
        lods[name] = (G, f, ppu, cubes)
        print(name, "boxes", raw, "kept", len(cubes), "faces", sum(len(c["faces"]) for c in cubes), flush=True)
        stats[name] = dict(islands=left)

    # 1 枚のテクスチャに 3 つの LOD の面を詰める
    items = []
    for name, (G, f, ppu, cubes) in lods.items():
        for c in cubes:
            for fn, (w, h) in c["faces"].items():
                items.append(((c["id"], fn), w, h))
    pos = pack_width(items, ATLAS_W)
    H = -(-max(pos[k][1] + h for k, w, h in items) // 16) * 16
    print("atlas", ATLAS_W, "x", H, "px used", sum(w * h for _, w, h in items), flush=True)
    tex = np.zeros((H, ATLAS_W, 4), np.uint8)
    glow = np.zeros((H, ATLAS_W, 4), np.uint8)

    counts = {}
    for name, (G, f, ppu, cubes) in lods.items():
        glow_px = 0
        by_bone = {b: [] for b in BONE_ORDER}
        for c in cubes:
            uv = {}
            for fn, (w, h) in c["faces"].items():
                u, v = pos[(c["id"], fn)]
                uv[fn] = {"uv": [u, v], "uv_size": [w, h]}
                glow_px += paint_face(G, f, c, fn, w, h, tex, glow, u, v)
            if not uv:
                continue
            by_bone[PART_BONE[c["part"]]].append({"origin": [round(q, 3) for q in c["o"]],
                                                  "size": [round(q, 3) for q in c["s"]], "uv": uv})
        geos[name] = make_geo(by_bone, H)
        counts[name] = cube_count(geos[name])
        ov = check_overlap(cubes)
        bnd = {"x": (min(c["o"][0] for c in cubes), max(c["o"][0] + c["s"][0] for c in cubes)),
               "y": (min(c["o"][1] for c in cubes), max(c["o"][1] + c["s"][1] for c in cubes)),
               "z": (min(c["o"][2] for c in cubes), max(c["o"][2] + c["s"][2] for c in cubes))}
        stats[name].update(cubes=counts[name], quads=quad_count(geos[name]), overlap=ov, glow_px=glow_px,
                           bounds={k: [round(float(v[0]), 2), round(float(v[1]), 2)] for k, v in bnd.items()})
        # 骨ごとの箱の数
        stats[name]["bones"] = {b: len(v) for b, v in by_bone.items() if v}
        print(name, json.dumps(stats[name], ensure_ascii=False), flush=True)

    # 煙の上端・火山の縁の上端 (near の格子から)
    # 火口の縁の上端 (近景の背の箱、火口の中心から半径 RR+1 の中)、煙の上端 (近景の煙の箱)
    G = lods["near"][0]
    gs = G[ci(-RR - 1):ci(RR + 1) + 1, :, ck(ZV - RR - 1):ck(ZV + RR + 1) + 1]
    sub = ((gs // 32) == PART["ridge"]) & ((gs % 32) != E.MAT["smoke2"])      # 煙の根元 (smoke2) は除く
    top_rim = (np.argwhere(sub)[:, 1].max() + 1) / CELL + GY0 if sub.any() else 0.0
    smoke_top = 0.0
    for b in geos["near"]["minecraft:geometry"][0]["bones"]:
        if b["name"].startswith("smoke"):
            for c in b.get("cubes", []):
                smoke_top = max(smoke_top, c["origin"][1] + c["size"][1])
    print("volcano rim top y", top_rim, "smoke top y", smoke_top, flush=True)

    geo_dir = RES / "geo/entity"
    tex_dir = RES / "textures/entity"
    anim_dir = RES / "animations/entity"
    for d in (geo_dir, tex_dir, anim_dir):
        d.mkdir(parents=True, exist_ok=True)
    files = {"near": "ember_titan.geo.json", "mid": "ember_titan_mid.geo.json", "far": "ember_titan_far.geo.json"}
    for name, fn in files.items():
        (geo_dir / fn).write_text(json.dumps(geos[name], ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(tex_dir / "ember_titan.png", optimize=True)
    Image.fromarray(glow, "RGBA").save(tex_dir / "ember_titan_glowmask.png", optimize=True)
    (anim_dir / "ember_titan.animation.json").write_text(
        json.dumps(anim_json(), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    preview_lods(geos, tex, glow, counts)
    preview_poses(geos, tex, glow)
    preview_far(geos, tex, glow)
    stats["_global"] = dict(mouth_trigger_air=mouth_air, tongue_columns=[tongue_hits, tongue_n],
                            volcano_rim_top=round(float(top_rim), 2), smoke_top=round(float(smoke_top), 2),
                            texture=[ATLAS_W, H])
    (PREVIEW_DIR / "stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
    print("done", json.dumps(stats, ensure_ascii=False))
    # high-poly near LOD (docs/design/15_high_poly.md, assignment 6b): rebuild the near geo from the raw files just written
    sys.path.insert(0, str(ROOT / "tools/rough"))
    import hp_titans_storm_ember as hp  # noqa: E402
    hp.install_titan("ember_titan")


if __name__ == "__main__":
    main()
