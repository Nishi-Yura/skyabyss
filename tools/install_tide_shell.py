"""潮殻の巨神 (tide_shell) の採用案 B「珊瑚礁の背」をゲームに入れる (GeckoLib の geo、テクスチャ、アニメーション)。

使い方 (リポジトリ直下で): python tools/install_tide_shell.py
形の元: tools/rough/tide_shell_titan.py の build("b")。格子はそのまま使う。関数は変えず、
        甲羅の縁のサンゴは水面に接地させ (lift 0)、縁の海藻は甲羅の縁に寄せ、潮門の枠は gate の骨にする。
        背中の島 (08 の 3.3節) とつながるように、B の塔を少し高くし (頂の月の窓)、西に半分埋まった段々の庭を足す。
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/tide_shell.geo.json        近景。0.5 単位の格子 (2 px/単位)。ただし甲羅・頭・ひれ・尾・珊瑚・海藻は 2 単位 (1 ブロック) の段にそろえる。
                                        遺跡と潮門だけ 0.5 単位の細かさを残す
  geo/entity/tide_shell_mid.geo.json    中景。4 単位の格子 (1 px/単位)。珊瑚は房ごとに 1 色の丸い塊にまとめる
  geo/entity/tide_shell_far.geo.json    遠景。6 単位の格子 (0.5 px/単位)。近い房をまとめて大きな塊にし、縁の珊瑚は消す
                                        (鋸歯の縁・頭・首・ひれの形を珊瑚で崩さない)
  textures/entity/tide_shell.png / tide_shell_glowmask.png   3 つの LOD で共有する 1 枚 (幅 2048)
  animations/entity/tide_shell.animation.json
確認用 (docs/review/tide_shell_roughs/install/): tide_shell_lods.png, tide_shell_poses.png, tide_shell_far_read.png

ゲームでの向き (岩鯨と同じ決まり): 格子は X を反転してから箱にする。GeckoLib は X を反転して描くので、
geo の +X がゲームの左わき。潮門 (遺跡の左後ろ) は geo の +X 側に来る。頭は -Z。
骨: body (甲羅)、ruins (遺跡)、gate (潮門の枠・光る面・ランタン)、coral、kelp、head、tail、
fin_front_left / fin_front_right / fin_rear_left / fin_rear_right (geo の +X が左)。
骨は全部 body の子。body・ruins・gate・coral・kelp は動かないので 1 つのかたまりとして離れ島を調べる。
head・tail・各ひれは、それぞれ 1 つのかたまりにする。

箱を減らす方法 (岩鯨と同じ):
- 箱は材料ではなく骨ごとに合わせる。骨ごとに 6 通りの軸の順で貪欲に合わせ、一番少ないものを使う。
  面の画素は、その裏のセルの材料で塗る。
- 隠れた面は uv を書かない。全部の面が隠れた箱は置かない。
- 格子の段は LOD ごとに 2 倍ずつ粗くする。光る材料 (目・ランタン・潮門) は、少しでもあれば残す。
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
import tide_shell_titan as T  # noqa: E402  (格子と材料の関数だけを使う。tide_shell_titan.py は変えない)
from voxel import Face, FACE_SHADE, load_geo, render, sheet  # noqa: E402

RES = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW_DIR = ROOT / "docs/review/tide_shell_roughs/install"

CELL = T.CELL
ATLAS_W = 2048
WATER_REST = 8.0          # 休んでいるときの水面 (geo y)。ここより下は一年中濡れている
WATER_BAND = 20.0         # ここまで上へ、藻が薄くなりながら続く
GATE_PART = 11            # 潮門を遺跡から切り離す骨の番号 (tide_shell_titan の 1..10 の次)
MAT = T.MAT
GLOW_MATS = [MAT["eye"], MAT["cyan"], MAT["gate"]]
THIN_PARTS = {2, 4, 5, 6, 7, 8, 10}       # 遺跡 (塔の柱・アーチ)・ひれ・尾・海藻は細いので、低い割合でも残す
GLOW_MIN = {1: 2, 4: 4, 8: 12, 12: 16, 16: 24}            # 粗い格子で、光る材料をこの数だけ含む段は光る材料で残す

PART_BONE = {1: "body", 2: "ruins", GATE_PART: "gate", 3: "head", 4: "fin_front_left", 5: "fin_front_right",
             6: "fin_rear_left", 7: "fin_rear_right", 8: "tail", 9: "coral", 10: "kelp"}
BONE_ORDER = ["body", "ruins", "gate", "coral", "kelp", "head", "tail",
              "fin_front_left", "fin_front_right", "fin_rear_left", "fin_rear_right"]
# 骨の軸 (geo の単位)。ひれは反転後の位置 (左 = +X)
PIVOT = {"body": [0, 30, 0], "ruins": [0, 30, 0], "gate": [0, 30, 0], "coral": [0, 30, 0], "kelp": [0, 30, 0],
         "head": [0, 20, -100], "tail": [0, 16, 92],
         "fin_front_left": [76, 9, -40], "fin_front_right": [-76, 9, -40],
         "fin_rear_left": [62, 9, 50], "fin_rear_right": [-62, 9, 50]}
# 離れ島を見るときの組。動かない骨は 1 つの組、head・tail・各ひれは別々
GROUPS = {"static": [1, 2, GATE_PART, 9, 10], "head": [3], "tail": [8], "fin_fl": [4], "fin_fr": [5],
          "fin_rl": [6], "fin_rr": [7]}

LODS = [("near", 1, 2.0), ("mid", 8, 1.0), ("far", 12, 0.5)]   # 名前, 格子の倍率, 1 単位あたりの画素
FACE_AXIS = {"west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1)}


# ---------------------------------------------------------------- 格子

def _kelp_rim(theta, height, seed):
    """海藻: 甲羅の縁の下に根を置く (半径 0.97)。tide_shell_titan の kelp_strand は縁の外 (1.06) で、
    甲羅に接さず浮いたので、この版で置き直す (縁の下の面に根が接する)"""
    kx = T.HW * 0.97 * math.cos(theta)
    kz = T.ZC + T.HL * 0.97 * math.sin(theta)
    leaves = [(2.0 + 4.0 * T.hz(seed, j, 5), 4.0 + j * 5.0 + 3.0 * T.hz(seed, j, 6), 1 if j % 2 else -1)
              for j in range(max(1, int(height // 6)))]
    lf = [(yy, sd) for (_, yy, sd) in leaves if yy < height]

    def fn(X, Y, z):
        m = ((X - kx) ** 2 + (z - kz) ** 2 <= 1.0) & (Y >= 0.0) & (Y <= height)
        for yy, sd in lf:
            d = ((X - kx - 2.6 * sd) / 2.6) ** 2 + ((Y - yy) / 1.3) ** 2 + ((z - kz) / 1.0) ** 2
            m |= d <= 1.0
        return m, MAT["kelp"]
    return fn, (kx - 4.5, kx + 4.5, -1.0, height + 2.0, kz - 4.5, kz + 4.5)


_coral_orig = T.coral_at


def _coral_ground(cx, cz, kind, s, seed, mat_pick, ge, lift=0.0):
    """サンゴは甲羅の表面に接して置く (tide_shell_titan は lift 6 で浮かせていたので、離れ島になる)"""
    return _coral_orig(cx, cz, kind, s, seed, mat_pick, ge, lift=0.0)


_tower_orig = T.tower
_build_b_orig = T.build_b


def _tower_b(topf, T0, h, sc=1.0):
    """B の塔 (ラフは h 0.55) を少しだけ高くし、頂の柱の冠を出す。背中の島の潮の広間 (頂の月の窓) の目印。
    A の塔 (h 1.0、1.5 倍) よりはずっと低く、珊瑚の房から頭が出る程度"""
    return _tower_orig(topf, T0, h=0.7, sc=1.2)


def _build_b_install(topf, T0, ge):
    """ラフの B に、背中の島 (08 の 3.3節) の西の珊瑚の庭へつながる、半分埋まった段々の庭を足す。
    庭の範囲 (島の x -60〜-20、z -40〜40) に、外へ下る 3 段の低い石の段 (各 2 単位、甲羅から 1〜3 単位だけ出る)。
    ラフの座標はまだ反転前 (潮門は -X) なので、島の西 = ラフの -X"""
    _build_b_orig(topf, T0, ge)
    # ゲームで脇 (水面の高さ) から見ると、ラフの珊瑚は頂の近くにしかなく、甲羅がただの緑の丘に見えた。
    # 甲羅の肩 (平面の r 0.55〜0.75) に房を 5 つ足して、近景の華やかさを脇からも見えるようにする。
    # 潮門 (x -72〜-60、z 56〜72) と西の段々の庭 (x -58〜-24、z -26〜26) は避ける
    for i, (x, z, kind, s) in enumerate(((50.0, -52.0, "brain", 2.6), (62.0, 12.0, "table", 2.8),
                                         (40.0, 58.0, "branch", 2.6), (-36.0, 52.0, "brain", 2.4),
                                         (-22.0, -72.0, "table", 2.4))):
        T.coral_at(x, z, kind, s, 400 + i, T.CORAL_MATS[(i + 1) % 4], ge, lift=0.0)
    for k, (xa, xb) in enumerate(((-34.0, -24.0), (-46.0, -34.0), (-58.0, -46.0))):
        za, zb = -26.0 + 4 * k, 26.0 - 4 * k
        rise = 3.0 - k

        def fn(X, Y, z, xa=xa, xb=xb, za=za, zb=zb, rise=rise):
            top = topf(X, z)
            # 段の上面は段ごとに平ら (段の中央の甲羅の高さ + rise)。内側は甲羅に埋まる
            flat = float(topf(np.array((xa + xb) / 2), (za + zb) / 2)) + rise
            m = inb(X, xa, xb) & inb(z, za, zb) & (Y > top - 2.0) & (Y <= flat)
            edge = (np.abs(X - xa) <= 1.0) | (np.abs(z - za) <= 1.0) | (np.abs(z - zb) <= 1.0)
            return m, np.where(edge, MAT["stone"], MAT["ruin"])
        T.fill(fn, (xa - 1, xb + 1, 30, 90, za - 1, zb + 1), T.PART["ruins"])


def inb(v, a, b):
    return (v >= a) & (v <= b)


def build_grid():
    """格子を作って X を反転する。戻り値は (a, b, c) の骨の番号 * 32 + 材料 の配列 (int16)"""
    T.kelp_strand = _kelp_rim
    T.coral_at = _coral_ground
    T.tower = _tower_b
    T.build_b = _build_b_install
    T.build("b")
    K = T.K
    # 潮門の枠・光る面・ランタンを、遺跡 (ruins) から切り離して gate の骨にする (岩鯨と同じく骨で分ける)
    i0, i1 = T.ci(-80), T.ci(-55)
    k0, k1 = T.ck(49), T.ck(79)
    j1 = T.cj(46)
    sub = K[i0:i1, :j1, k0:k1]
    m = (sub // 32) == 2
    sub[m] = GATE_PART * 32 + sub[m] % 32
    K = np.ascontiguousarray(K[::-1])          # X を反転 (X0 = -X1 なので、格子の番号を反転すれば世界の X も反転する)
    fill_enclosed(K)
    snap_body_tops(K, 4)
    snap_blocks(K, [1, 3, 4, 5, 6, 7, 8, 9, 10], 4)
    # 段をそろえると、甲羅に彫った潮門の口 (反転後の x 58〜74, y 9〜31, z 55〜73) が甲羅でふさがる。
    # ゲームで潮門の口が見えて入れるように、甲羅・珊瑚・海藻のセルだけをもう一度削る (枠と光る面 = gate の骨は残す)
    sub = K[T.ci(58):T.ci(74), T.cj(9):T.cj(31), T.ck(55):T.ck(73)]
    sub[np.isin(sub // 32, [1, 9, 10])] = 0
    return K


def snap_blocks(K, parts, q):
    """骨ごとに、q x q x q セル (1 単位) の段にそろえる。
    ブロックの半分以上がその骨なら、ブロック全部をその骨にする (空いているセルだけ埋める)。
    そうでなければ、その骨のセルを空ける。段が 1 単位にそろうので箱が減る"""
    a, b, c = (s // q for s in K.shape)
    B = K.reshape(a, q, b, q, c, q).transpose(0, 2, 4, 1, 3, 5).reshape(a, b, c, q ** 3).copy()
    half = q ** 3 / 2
    for p in parts:
        part = B // 32
        m = part == p
        del part
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
    """甲羅の上面を、q セル (1 単位) の段にそろえる (0.5 単位の細かい段を消して箱を減らす)。
    上へ出す所は空いているセルだけ、下げる所は上に他の骨が無いときだけ削る。形の差は 0.5 単位以内"""
    occ = (K // 32) == 1
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


def fill_enclosed(K):
    """外とつながらない空洞 (見えない) を、まわりでいちばん多い骨と材料で埋める"""
    nz = np.argwhere(K != 0)
    lo, hi = nz.min(0), nz.max(0) + 1
    S = np.pad(K[lo[0]:hi[0], lo[1]:hi[1], lo[2]:hi[2]], 1)
    lab, n = ndimage.label(S == 0)
    border = set()
    for face in (lab[0], lab[-1], lab[:, 0], lab[:, -1], lab[:, :, 0], lab[:, :, -1]):
        border |= set(np.unique(face).tolist())
    border.discard(0)
    filled = 0
    for i, sl in enumerate(ndimage.find_objects(lab), start=1):
        if i in border or sl is None:
            continue
        sl2 = tuple(slice(max(0, s.start - 1), min(S.shape[d], s.stop + 1)) for d, s in enumerate(sl))
        comp = lab[sl2] == i
        ring = ndimage.binary_dilation(comp) & ~comp
        keys = S[sl2][ring]
        if keys.size == 0:
            continue
        vals, cnt = np.unique(keys, return_counts=True)
        S[sl2][comp] = vals[np.argmax(cnt)]
        filled += int(comp.sum())
    K[lo[0]:hi[0], lo[1]:hi[1], lo[2]:hi[2]] = S[1:-1, 1:-1, 1:-1]
    print("enclosed cells filled:", filled)


# ---------------------------------------------------------------- LOD の格子

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
    cnt_p = np.stack([(part == p).sum(1) for p in range(1, GATE_PART + 1)], 1)
    pm = cnt_p.argmax(1) + 1
    cp = cnt_p.max(1)
    pmask = (part == pm[:, None]) & (cells != 0)
    mat = cells % 32
    mc = np.stack([((mat == v) & pmask).sum(1) for v in range(32)], 1)
    glow_n = mc[:, GLOW_MATS].sum(1)
    use_glow = glow_n >= GLOW_MIN[f]
    glow_pick = np.array(GLOW_MATS)[mc[:, GLOW_MATS].argmax(1)]
    mc_plain = mc.copy()
    mc_plain[:, [0] + GLOW_MATS] = -1
    plain_pick = mc_plain.argmax(1)
    mat_out = np.where(use_glow, glow_pick, plain_pick)
    thr_thick = 0.5 if f <= 4 else 0.45
    thr_thin = 0.5 if f <= 4 else 0.3
    thr = np.where(np.isin(pm, list(THIN_PARTS)), thr_thin, thr_thick) * f ** 3
    keep = (cp >= thr) | use_glow
    out = np.zeros((a, b, c), np.int16)
    sel = occ[keep]
    out[sel[:, 0], sel[:, 1], sel[:, 2]] = (pm * 32 + mat_out)[keep]
    return out


# 中景・遠景の珊瑚: 房ごとに 1 色の丸い塊にする (B の弱み「遠くでは珊瑚が点に散って、形がにじむ」の直し)。
# merge: この距離 (単位) より近い房は 1 つの塊にまとめる。rmin: 塊の半径の下限 (単位)。
# rmax: 半径の上限 (単位。まとめた塊が背を覆って、塔や甲羅の丸みを隠さないように)。
# rim: 甲羅の縁 (平面の r がこれより外) の房は消す (鋸歯の縁を珊瑚で崩さない)。None なら残す
CORAL_LUMP = {8: dict(merge=0.0, rmin=7.0, rmax=12.0, rim=None), 12: dict(merge=50.0, rmin=12.0, rmax=16.0, rim=0.86)}
CORAL_PART = 9


def coral_clumps(K):
    """近景の格子の珊瑚を房ごとに分ける。[{i, k (中心の格子の番号), y0, y1, r (セル), mat, n}]"""
    m = (K // 32) == CORAL_PART
    lab, _ = ndimage.label(ndimage.binary_dilation(m, iterations=6))
    lab = np.where(m, lab, 0)
    out = []
    for i, sl in enumerate(ndimage.find_objects(lab), start=1):
        if sl is None:
            continue
        idx = np.argwhere(lab[sl] == i) + np.array([s.start for s in sl])
        if len(idx) < 8:
            continue
        vals, cnt = np.unique(K[tuple(idx.T)] % 32, return_counts=True)
        lo, hi = idx.min(0), idx.max(0) + 1
        out.append(dict(i=float(idx[:, 0].mean()), k=float(idx[:, 2].mean()), y0=int(lo[1]), y1=int(hi[1]),
                        r=max(hi[0] - lo[0], hi[2] - lo[2]) / 2.0, mat=int(vals[np.argmax(cnt)]), n=len(idx)))
    return out


def coral_lumps(K, f):
    """f 倍の LOD に落とす前に、珊瑚を丸い塊に置き換えた格子を返す (近景はそのまま)"""
    spec = CORAL_LUMP.get(f)
    if spec is None:
        return K
    clumps = coral_clumps(K)
    if spec["rim"] is not None:
        keep = []
        for c in clumps:
            x = T.X0 + (c["i"] + 0.5) / CELL          # 平面は X について左右対称なので、反転後の番号のままでよい
            z = T.Z0 + (c["k"] + 0.5) / CELL
            r, _ = T.plan(np.array(x), z)
            if float(r) <= spec["rim"]:
                keep.append(c)
        clumps = keep
    # 近い房をまとめる (大きい房から順に、まだ入っていない近い房を集める)
    groups = []
    for c in sorted(clumps, key=lambda c: -c["n"]):
        for g in groups:
            if math.hypot(g[0]["i"] - c["i"], g[0]["k"] - c["k"]) <= spec["merge"] * CELL:
                g.append(c)
                break
        else:
            groups.append([c])
    G = K.copy()
    G[(G // 32) == CORAL_PART] = 0
    for g in groups:
        w = np.array([c["n"] for c in g], float)
        ci_ = float(np.average([c["i"] for c in g], weights=w))
        ck_ = float(np.average([c["k"] for c in g], weights=w))
        R = max(max(math.hypot(c["i"] - ci_, c["k"] - ck_) + c["r"] for c in g), spec["rmin"] * CELL)
        R = min(R, spec["rmax"] * CELL)
        y0 = min(c["y0"] for c in g)
        Ry = max(max(c["y1"] for c in g) - y0, 0.75 * R)
        i0, i1 = max(int(ci_ - R) - 1, 0), min(int(ci_ + R) + 2, G.shape[0])
        k0, k1 = max(int(ck_ - R) - 1, 0), min(int(ck_ + R) + 2, G.shape[2])
        # 下は深く (0.9R) 伸ばす。空いているセルだけ埋めるので甲羅の斜面に沿う裾になり、粗い格子でも甲羅から離れない
        j0, j1 = max(int(y0 - 0.9 * R), 0), min(int(y0 + Ry) + 2, G.shape[1])
        I, J, Kk = np.meshgrid(np.arange(i0, i1), np.arange(j0, j1), np.arange(k0, k1), indexing="ij")
        dy = np.where(J >= y0, (J - y0) / Ry, (J - y0) / (0.9 * R))
        m = ((I - ci_) / R) ** 2 + dy ** 2 + ((Kk - ck_) / R) ** 2 <= 1.0
        sub = G[i0:i1, j0:j1, k0:k1]
        sub[m & (sub == 0)] = CORAL_PART * 32 + g[0]["mat"]
    print("coral lumps f=%d: %d 房 -> %d 塊" % (f, len(clumps), len(groups)))
    tower_core(G)
    return G


def tower_core(G):
    """粗い LOD では塔の頂の 4 本の柱が細くて消え、屋根が浮く。柱のあいだを石の芯で埋めて屋根を支える
    (塔の寸法は _tower_b: h 0.7、1.2 倍)"""
    sc, h = 1.2, 0.7
    T0 = float(T.top_at(np.array(0.0), T.ZC, 0.0))
    up = (12 * h + 10) * sc
    y0, y1 = T0 + up - 1.0, T0 + up + 10 * sc + 0.5
    half = 4.0 * sc
    sub = G[T.ci(-half):T.ci(half), T.cj(y0):T.cj(y1), T.ck(T.ZC - half):T.ck(T.ZC + half)]
    sub[sub == 0] = 2 * 32 + MAT["ruin"]


def islands_fix(G, label):
    """組ごとに、一番大きい塊以外を消す (浮いた塊)。消した数を返す。組は GROUPS"""
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
                # 別の骨に接している塊は、一番多く接している骨に移す (材料はそのまま。その骨と一緒に動く)
                vals, cnt = np.unique(touch, return_counts=True)
                to = int(vals[np.argmax(cnt)])
                G[comp] = to * 32 + G[comp] % 32
                log.append("%s %s: %d セルの塊を %s に移した" % (label, name, int(sizes[i - 1]), PART_BONE[to]))
            else:
                G[comp] = 0
                log.append("%s %s: %d セルの浮いた塊を消した" % (label, name, int(sizes[i - 1])))
    return log


def islands(G):
    """離れ島の数 (組ごと)。0 なら離れ島なし"""
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
    """6 通りの軸の順で合わせて、箱の一番少ないものを返す (岩鯨と同じ)"""
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

def wet_apply(base, mat, A2, B2, Yw):
    """水面より下は藻で暗くする。休んでいる水面 (y=8) より下は全部濡れている。y=20 で消える。
    水際の明るい筋は付けない。藤壺の白い粒は y=8 より下の甲羅と遺跡だけ"""
    name = T.MAT_NAME[mat]
    if name not in T.WET:
        return base
    n = T.nzv(A2, B2)
    t = np.clip((WATER_BAND - Yw) / (WATER_BAND - WATER_REST), 0, 1)
    mix = (t * (0.6 + 0.3 * n))[..., None]
    base = base * (1 - mix) + T.ALGAE * mix
    if name in ("shell", "scute", "stone", "ruin"):
        bar = (Yw < WATER_REST + 1.0) & (Yw > WATER_REST - 9.0) & (n > 0.86)
        base[bar] = (222, 216, 200)
    return base


def paint_face(G, f, unit, cube, fname, w, h, tex, glow, x0, y0):
    """面の画素を、裏のセルの骨と材料で塗る。戻り値は光る画素の数"""
    P = T.world_grid(cube["o"], cube["s"], fname, w, h)
    ax, d = FACE_AXIS[fname]
    Q = P.copy()
    Q[..., ax] -= d * unit * 0.5
    gi = np.clip(np.floor((Q[..., 0] - T.X0) * CELL / f).astype(int), 0, G.shape[0] - 1)
    gj = np.clip(np.floor((Q[..., 1] - T.Y0) * CELL / f).astype(int), 0, G.shape[1] - 1)
    gk = np.clip(np.floor((Q[..., 2] - T.Z0) * CELL / f).astype(int), 0, G.shape[2] - 1)
    keys = G[gi, gj, gk].astype(np.int64)
    keys[keys == 0] = 1 * 32 + 1
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
        sel = keys == key
        base, gm = T.mat_base(mat, A2, B2, part)
        base = wet_apply(base, mat, A2, B2, Yw)
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
    for k, w, h in sorted(items, key=lambda it: (-it[2], -it[1])):
        if x + w + 1 > W:
            x, y, shelf = 0, y + shelf + 1, 0
        pos[k] = (x, y)
        x += w + 1
        shelf = max(shelf, h)
    return pos


# ---------------------------------------------------------------- geo と確認用

def make_geo(lod_cubes, uv_of, H, lod_name):
    bones = {name: [] for name in BONE_ORDER}
    for ci, c in enumerate(lod_cubes):
        uv = {fn: {"uv": list(uv_of[(c["id"], fn)]), "uv_size": list(sz)} for fn, sz in c["faces"].items()}
        bones[PART_BONE[c["part"]]].append({"origin": [round(v, 3) for v in c["o"]],
                                            "size": [round(v, 3) for v in c["s"]], "uv": uv})
    bl = []
    for name in BONE_ORDER:
        b = {"name": name, "pivot": PIVOT[name]}
        if name != "body":
            b["parent"] = "body"
        if bones[name]:
            b["cubes"] = bones[name]
        bl.append(b)
    return {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry.tide_shell", "texture_width": ATLAS_W, "texture_height": H,
        "visible_bounds_width": 96, "visible_bounds_height": 48, "visible_bounds_offset": [0, 6, 0]},
        "bones": bl}]}


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
# 値の書き方: 骨ごとに {軸: (c, A, f, p)}。意図した回転 = c + A * sin(f * 時間 + p) (度)。
# GeckoLib は X と Z の符号を反転して回す (実物で確かめた: ひれ・頭の 'X' と 'Z' は符号を付けて書く。
# 'Y' はそのまま)。ファイルには (-意図の X, 意図の Y, -意図の Z) を書く。

ANIM = {
    "animation.tide_shell.walk": (8.0, {
        "body": {"x": (0, 0.4, 45, 0), "z": (0, 0.6, 45, 90)},
        "fin_front_left": {"y": (0, 22, 45, 0), "z": (0, 8, 90, 0)},
        "fin_front_right": {"y": (0, 22, 45, 180), "z": (0, 8, 90, 180)},
        "fin_rear_left": {"y": (0, 8, 45, 90)},
        "fin_rear_right": {"y": (0, 8, 45, 270)},
        "tail": {"y": (0, 3, 45, 90)},
    }),
    "animation.tide_shell.rest": (10.0, {
        "body": {"x": (0, 0.3, 36, 0)},
        "fin_front_left": {"z": (-6, 1.0, 36, 0)},
        "fin_front_right": {"z": (6, 1.0, 36, 0)},
        "fin_rear_left": {"z": (-4, 0.8, 36, 90)},
        "fin_rear_right": {"z": (4, 0.8, 36, 90)},
    }),
    "animation.tide_shell.head_idle": (8.0, {
        "head": {"x": (0, 1.5, 45, 90), "y": (0, 3, 45, 0)},
    }),
    "animation.tide_shell.look": (10.0, {
        # 頭を上げる。ゲームで確かめた向き: 意図した X が正 (ファイルには -20) のとき鼻先が上がる。
        # (ファイルに +20 を書いた版は、ゲームで頭が下がった。下のプレビューの look の絵は向きが逆に見えるので当てにしない)
        "head": {"x": (20, 1.0, 36, 0), "y": (0, 2, 36, 0)},
    }),
}
SIGN = {"x": -1, "y": 1, "z": -1}


def _num(v):
    return ("%.3f" % v).rstrip("0").rstrip(".") if abs(v) > 1e-9 else "0"


def molang_axis(axis, spec):
    c, A, f, p = spec
    s = SIGN[axis]
    parts = []
    if abs(c) > 1e-9:
        parts.append(_num(s * c))
    if abs(A) > 1e-9:
        parts.append("%s * math.sin(%s * query.anim_time + %s)" % (_num(s * A), _num(f), _num(p)))
    return " + ".join(parts) if parts else "0"


def anim_json():
    out = {}
    for name, (length, bones) in ANIM.items():
        bj = {}
        for bone, axes in bones.items():
            rot = [molang_axis(a, axes[a]) if a in axes else "0" for a in ("x", "y", "z")]
            bj[bone] = {"rotation": rot}
        out[name] = {"loop": True, "animation_length": length, "bones": bj}
    return {"format_version": "1.8.0", "animations": out}


def intended_pose(name, t):
    """時刻 t (秒) での、意図した回転 {骨: (x, y, z)} (度)。プレビュー用"""
    _, bones = ANIM[name]
    pose = {}
    for bone, axes in bones.items():
        v = [0.0, 0.0, 0.0]
        for i, a in enumerate(("x", "y", "z")):
            if a in axes:
                c, A, f, p = axes[a]
                v[i] = c + A * math.sin(math.radians(f * t + p))
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


def box_outline(lo, hi, color, t=0.7):
    """箱の 12 本の辺を細い箱で描く (ゲームの向き)"""
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


def top_y_check(lods):
    return {n: max(c["o"][1] + c["s"][1] for c in cubes) for n, cubes in lods.items()}


def preview(geos, tex, glow, counts):
    def faces_of(name, pose=None):
        return mirror_faces(load_geo(geos[name], tex, glowmask=glow, pose=pose))

    cols = []
    for name, label in (("near", "近景 (遺跡 0.5 / 体 2 単位)"), ("mid", "中景 (4 単位、珊瑚は房ごとの塊)"),
                        ("far", "遠景 (6 単位、近い房をまとめた塊)")):
        fs = faces_of(name)
        cols.append(("%s %d 箱" % (label, counts[name]),
                     [render(fs, yaw=90, pitch=0, size=(460, 460)),
                      render(fs, yaw=35, pitch=35, size=(460, 460)),
                      render(fs, yaw=180, pitch=8, size=(460, 460))]))
    sht = sheet("潮殻の巨神 LOD (採用案 B 珊瑚礁の背、ゲームの向き)", cols,
                note="行: 側面 / 斜め上 / 正面。頭は左 (-Z のゲーム側は画面の右)。LOD ごとの箱の数は見出し。")
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    out = PREVIEW_DIR / "tide_shell_lods.png"
    sht.save(out)
    print("preview", out)


def preview_far(geos, tex, glow):
    """遠景の読みやすさ: 3 つの LOD を小さく (140px) 描き、ラフ B の遠景と並べる。休む水面 (y=8) の板あり"""
    rest_plane = marker((0, WATER_REST, 0), (330, 0.8, 330), (34, 92, 128, 255))
    cols = []
    for name in ("near", "mid", "far"):
        fs = mirror_faces(load_geo(geos[name], tex, glowmask=glow))
        ims = []
        for planes in ([], rest_plane):
            for yaw, pitch in ((90, 4), (35, 30), (180, 10)):
                ims.append(render(fs + planes, yaw=yaw, pitch=pitch, size=(140, 140), margin=0.04))
        cols.append((name, ims))
    rough = ROOT / "docs/review/tide_shell_roughs/titan/b/far.png"
    if rough.exists():
        im = Image.open(rough).convert("RGB")
        cols.append(("ラフ B の遠景", [im.crop((0, 0, 140, 140)), im.crop((150, 0, 290, 140))]))
    sht = sheet("潮殻の巨神 遠景の読みやすさ (140px)", cols,
                note="行: 側面 / 斜め上 / 正面、下の 3 行は休む水面 (y=8) の板つき。遠景は縁の珊瑚を消し、背の房を大きな塊にまとめた。")
    out = PREVIEW_DIR / "tide_shell_far_read.png"
    sht.save(out)
    print("preview", out)


def preview_poses(geos, tex, glow):
    near = geos["near"]
    base_faces = load_geo(near, tex, glowmask=glow)

    def posed(name, t):
        return mirror_faces(load_geo(geos["near"], tex, glowmask=glow, pose=intended_pose(name, t)))

    def pair(fs):
        return [render(fs, yaw=90, pitch=0, size=(420, 420)), render(fs, yaw=35, pitch=35, size=(420, 420))]

    cols = []
    cols.append(("walk 1 (t=2s, 前ひれ後ろ)", pair(posed("animation.tide_shell.walk", 2.0))))
    cols.append(("walk 2 (t=6s, 前ひれ前)", pair(posed("animation.tide_shell.walk", 6.0))))
    cols.append(("rest (t=5s)", pair(posed("animation.tide_shell.rest", 5.0))))
    look = posed("animation.tide_shell.look", 2.0)
    cols.append(("look (頭を上げる、t=2s)", pair(look)))
    # 潮門 (ゲームの左後ろ): ゲームの向きで、x < -40、z > 30 の面だけ
    gate_faces = [f for f in mirror_faces(base_faces) if f.origin[0] < -40 and f.origin[2] > 30]
    gate_center = (-67.0, 20.0, 64.0)
    gate_box = box_outline((-74, 9, 55), (-58, 31, 73), (255, 0, 255, 255))
    gate_close = [render(gate_faces + gate_box, yaw=-40, pitch=12, size=(460, 460), margin=0.05),
                  render(gate_faces + gate_box, yaw=20, pitch=20, size=(460, 460), margin=0.05)]
    cols.append(("潮門 (左後ろ、紫の枠 = 潮門の開口)", gate_close))
    # 水面の高さ: 休み y=8 (水色)、巡航 y=58 (濃い水色)。板の上に出ているところを見る
    all_faces = mirror_faces(base_faces)
    rest_plane = marker((0, WATER_REST, 0), (420, 0.8, 420), (40, 120, 200, 255))
    cruise_plane = marker((0, 58, 0), (420, 0.8, 420), (60, 150, 230, 255))
    cols.append(("水面 y=8 (休み、水色の板)", [render(all_faces + rest_plane, yaw=90, pitch=4, size=(420, 420)),
                                              render(all_faces + rest_plane, yaw=35, pitch=20, size=(420, 420))]))
    cols.append(("水面 y=58 (巡航、板)", [render(all_faces + cruise_plane, yaw=90, pitch=4, size=(420, 420)),
                                          render(all_faces + cruise_plane, yaw=35, pitch=20, size=(420, 420))]))
    sht = sheet("潮殻の巨神 姿勢と潮門と水面 (ゲームの向き)", cols,
                note="頭は上がっていること (look)。潮門の紫の枠 = 開口 (x -74..-58, y 9..31, z 55..73、ゲームの座標)。")
    out = PREVIEW_DIR / "tide_shell_poses.png"
    sht.save(out)
    print("preview", out)


# ---------------------------------------------------------------- 本体

def main():
    K = build_grid()
    print("grid", K.shape, "non-zero cells", int((K != 0).sum()))

    lods = {}
    geos = {}
    stats = {}
    for name, f, ppu in LODS:
        G = downsample(coral_lumps(K, f), f) if f > 1 else K.copy()
        log = islands_fix(G, name)
        for line in log:
            print(line)
        left = islands(G)
        print(name, "islands left", left)
        cubes, raw = build_lod(G, f, ppu)
        for i, c in enumerate(cubes):
            c["id"] = (name, i)
        lods[name] = (G, f, ppu, cubes)
        print(name, "boxes", raw, "kept", len(cubes), "faces", sum(len(c["faces"]) for c in cubes), flush=True)

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

    counts = {}
    for name, (G, f, ppu, cubes) in lods.items():
        uv_of = {}
        glow_px = 0
        for c in cubes:
            c["uv"] = {}
            for fn, (w, h) in c["faces"].items():
                u, v = pos[(c["id"], fn)]
                c["uv"][fn] = (u, v)
                uv_of[(c["id"], fn)] = (u, v)
                glow_px += paint_face(G, f, f * 0.5, c, fn, w, h, tex, glow, u, v)
        geos[name] = make_geo(cubes, uv_of, H, name)
        counts[name] = cube_count(geos[name])
        ov = check_overlap(cubes)
        bnd = {"x": (min(c["o"][0] for c in cubes), max(c["o"][0] + c["s"][0] for c in cubes)),
               "y": (min(c["o"][1] for c in cubes), max(c["o"][1] + c["s"][1] for c in cubes)),
               "z": (min(c["o"][2] for c in cubes), max(c["o"][2] + c["s"][2] for c in cubes))}
        stats[name] = dict(cubes=counts[name], quads=quad_count(geos[name]), overlap=ov, glow_px=glow_px,
                           bounds=bnd)
        print(name, json.dumps(stats[name], ensure_ascii=False), flush=True)

    geo_dir = RES / "geo/entity"
    tex_dir = RES / "textures/entity"
    anim_dir = RES / "animations/entity"
    for d in (geo_dir, tex_dir, anim_dir):
        d.mkdir(parents=True, exist_ok=True)
    files = {"near": "tide_shell.geo.json", "mid": "tide_shell_mid.geo.json", "far": "tide_shell_far.geo.json"}
    for name, fn in files.items():
        (geo_dir / fn).write_text(json.dumps(geos[name], ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(tex_dir / "tide_shell.png", optimize=True)
    Image.fromarray(glow, "RGBA").save(tex_dir / "tide_shell_glowmask.png", optimize=True)
    (anim_dir / "tide_shell.animation.json").write_text(json.dumps(anim_json(), ensure_ascii=False, indent=1) + "\n",
                                                        encoding="utf-8")
    preview(geos, tex, glow, counts)
    preview_poses(geos, tex, glow)
    preview_far(geos, tex, glow)
    print("done", json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main()
