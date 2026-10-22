"""余燼の背 (ember_back) の体を、巨神本体のモデル (低ポリ案 C 「皮が岩になる火蜥蜴」の近景メッシュ) と同じ形・同じ色の
ブロックの火蜥蜴にする (13 の 3.2節。型は岩鯨の tools/gen/items/gen_titan_back_whale.py)。

使い方 (リポジトリ直下で): python tools/gen/items/gen_ember_back_body.py [--preview] [--cache]
形の元: docs/review/lowpoly_roughs/lp_ember_titan/c/near_{mesh.json,atlas.png,glowmask.png}。
        tools/lib/lowpoly_ember_voxel.py が 0.5 単位のセルの格子 (部品 * 16 + 材料) にする (向きは geo のまま。頭は -Z、反転なし)。
出力: src/main/resources/data/skyabyss/ember_back/body.bin (gzip。TitanBodyBlocks が読む。形式は "TBT1")
      --preview で docs/review/previews/ember_back/body_{top,side,front}.png (リポジトリには入れない)
      --cache で tools/rough/_ember_back_body_cache.npz (調べる用。tools/sheets/ember_back_c_views.py の入力)

縮尺: 1 単位 = 0.85 ブロック。z に ZSHIFT (+7) ブロックずらし、火口の鉢の中心を炉心の広間の真上 z -34 に合わせる。
高さ: モデルの地面 (y=0) を灰の原のいちばん上 (y=63) の上 (y=64) に合わせる。地面より下 (脚の先) は作らない。
ブロックの決め方: ブロックに入る格子のセルの多数決。占める割合が 0.45 (牙・角は 0.3) 以上なら置く。
      光る材料 (溶岩の割れ目) は 3 セル以上あれば優先する。同じ材料の中の色の揺れはブロックの位置のハッシュで選ぶ (乱数なし)。
      溶岩は流れないマグマブロック・シュルームライトにする (流体は置かない)。煙は置かない (粒で出す)。
腹: 頭・顎・胴・尾の足あとの 3 ブロック内側を、灰の原の上 (y 64〜69) まで埋める。
中: 外から 5 ブロックより深い所は凝灰岩 (掘った壁は EmberBodyBuilder が「肉と骨の壁」にする)。下 (灰の原) は外に数えない。
背: 隣が 2〜3 高い柱に同じ石を 1 段足し、背の上を歩けるようにする (y 100 より上)。
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../../lib')]
import gzip
import struct
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))
import lowpoly_ember_voxel as I  # noqa: E402

import os as _os
OUT = Path(_os.environ["EMBER_OUT"]) if "EMBER_OUT" in _os.environ else ROOT / "src/main/resources/data/skyabyss/ember_back/body.bin"
PREVIEW = ROOT / "docs/review/previews/ember_back"

_E = _os.environ
S = 0.85            # 1 単位あたりのブロック (ゲームの巨神の 1.7 倍。版 12 まで)
# 倍率 (設計書 26)。既定は水平 x1.6・垂直 x1.3 = EmberBackLayout の版 13。WH=1 WV=1 で版 12 の形
WH = float(_E.get("WH", "1.6"))
WV = float(_E.get("WV", "1.3"))
SH = S * WH         # 水平 (x・z) の 1 単位あたりのブロック
SV = S * WV         # 垂直 (y) の 1 単位あたりのブロック
GROUND = 64         # モデルの y=0 を置く高さ (灰の原のいちばん上 y 63 の上)
BELLY_Y = 67        # 腹の下端 (この段の足あとを下へ埋める)
INNER_DEPTH = 5     # これより深い体の中は凝灰岩
GLOW_MIN = 3        # 光る材料はブロックに 3 セル以上あれば優先 (割れ目を太らせすぎない)
ZSHIFT = int(_E.get("ZSHIFT", str(round(7 * WH))))          # 火口の鉢の中心 (モデルでは 体の中心の 48 単位前) を炉心の広間の真上 z -34 に合わせる

M = I.MAT
P = I.PARTS
# 材料 -> [(ブロック, 重み)]。色は低ポリ案 C のアトラスの色 (lowpoly_ember_voxel.classify) に近いバニラ。
#   shell = 黒い殻の板、skin = 灰茶の皮、redskin = 赤茶の皮、belly = 腹、lava/molten = 割れ目の溶岩 (流れないマグマ・灯り)、bone = 牙・角、mouth = 冷めかけの赤・口の中
BLOCKS = {
    M["shell"]: [("blackstone", 4), ("basalt", 3), ("polished_basalt", 2), ("smooth_basalt", 1)],
    M["skin"]: [("tuff", 3), ("brown_terracotta", 2), ("polished_deepslate", 1), ("deepslate", 1)],
    M["redskin"]: [("red_terracotta", 3), ("brown_terracotta", 1), ("terracotta", 1)],
    M["belly"]: [("polished_tuff", 2), ("tuff_bricks", 1), ("smooth_basalt", 1)],
    M["lava"]: [("magma_block", 3), ("shroomlight", 1)],
    M["molten"]: [("shroomlight", 2), ("ochre_froglight", 1)],
    M["bone"]: [("bone_block", 1)],
    M["mouth"]: [("red_nether_bricks", 2), ("nether_wart_block", 1)],
}
GLOW_MATS = {M["lava"], M["molten"]}
THIN_MATS = {M["bone"]}
SHELL_PARTS = {P["body"], P["ridge"], P["tail"]}
BELLY_BLOCKS = [("polished_tuff", 2), ("tuff_bricks", 1)]
DEEP = "tuff"
# block_colors.json に無いブロックの色 (見た目の確認だけに使う)
EXTRA_COLORS = {
    "polished_basalt": (88, 88, 92), "smooth_basalt": (72, 72, 78), "red_nether_bricks": (69, 7, 9),
    "nether_wart_block": (114, 2, 2), "magma_block": (142, 63, 31), "shroomlight": (240, 146, 70),
    "ochre_froglight": (250, 220, 150), "brown_terracotta": (77, 51, 36), "red_terracotta": (143, 61, 46),
    "terracotta": (152, 94, 67), "bone_block": (229, 225, 207), "polished_deepslate": (72, 72, 73),
}


def hash3(x, y, z, salt=0):
    h = (np.asarray(x, np.int64) * 73856093) ^ (np.asarray(y, np.int64) * 19349663) ^ \
        (np.asarray(z, np.int64) * 83492791) ^ (salt * 2654435761)
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF) / 65536.0


def axis_map(n, origin, f):
    u = (np.arange(n) - origin + 0.5) / I.CELL
    return np.floor(f(u)).astype(int)


def model_grid():
    """低ポリ案 C の近景メッシュの体積化 (lowpoly_ember_voxel。1 分ほどかかるので scratch にキャッシュ)"""
    import hashlib
    import tempfile
    h = hashlib.sha1()
    for f in (ROOT / "tools/lib/lowpoly_ember_voxel.py", I.CDIR / "near_mesh.json", I.CDIR / "near_atlas.png",
              I.CDIR / "near_glowmask.png"):
        h.update(f.read_bytes())
    cache = Path(tempfile.gettempdir()) / ("ember_titan_c_grid_%s.npy" % h.hexdigest()[:12])
    if cache.exists():
        return np.load(cache)
    K = I.voxelize()
    np.save(cache, K)
    return K


def block_grid():
    K = model_grid()
    nx, ny, nz = K.shape
    # 向きは今までと同じ (頭は -Z、反転なし。尾は -X へ曲がる)
    bx = axis_map(nx, I.OX, lambda u: u * SH)
    by = axis_map(ny, I.OY, lambda u: GROUND + u * SV)
    bz = axis_map(nz, I.OZ, lambda u: u * SH + ZSHIFT)
    keep = by >= GROUND             # 地面より下 (脚の先) は作らない
    x0, y0, z0 = bx.min(), GROUND, bz.min()
    NBX, NBY, NBZ = bx.max() - x0 + 1, by.max() - y0 + 1, bz.max() - z0 + 1
    cx = np.bincount(bx - x0, minlength=NBX)
    cy = np.bincount(by[keep] - y0, minlength=NBY)
    cz = np.bincount(bz - z0, minlength=NBZ)
    G = np.zeros((NBX, NBY, NBZ), np.int16)
    NK = 256
    glow_keys = np.zeros(NK, bool)
    thin_keys = np.zeros(NK, bool)
    for p in range(1, 10):
        for gm in GLOW_MATS:
            glow_keys[p * 16 + gm] = True
        for tm in THIN_MATS:
            thin_keys[p * 16 + tm] = True
    ysel = np.nonzero(keep)[0]
    for b in range(NBZ):
        ks = np.nonzero(bz - z0 == b)[0]
        if len(ks) == 0:
            continue
        sub = K[:, ysel[0]:ysel[-1] + 1, :][:, :, ks]
        nzi = np.nonzero(sub)
        if len(nzi[0]) == 0:
            continue
        key = sub[nzi].astype(np.int64)
        yy = by[ysel[0] + nzi[1]] - y0
        lin = ((bx[nzi[0]] - x0) * NBY + yy) * NK + key
        cnt = np.bincount(lin, minlength=NBX * NBY * NK).reshape(NBX * NBY, NK)
        tot = cnt.sum(1)
        cells = (cx[:, None] * cy[None, :]).reshape(-1) * cz[b]
        best = cnt.argmax(1)
        gcnt = np.where(glow_keys[None, :], cnt, 0)
        use_g = gcnt.max(1) >= GLOW_MIN
        k = np.where(use_g, gcnt.argmax(1), best)
        frac = tot / np.maximum(cells, 1)
        thr = np.where(thin_keys[k], 0.3, 0.45)
        keepb = (frac >= thr) | use_g
        G[:, :, b] = np.where(keepb, k, 0).reshape(NBX, NBY).astype(np.int16)
    return G, (int(x0), int(y0), int(z0))


def add_belly(G, org):
    """腹の下 (灰の原の上、y 64〜69) を、体 (頭・顎・胴・尾。脚は除く) の足あとの 3 ブロック内側だけ下まで埋める。
    首の下がくぼんでいても、喉の坂の床の下が空かないようにする"""
    from scipy import ndimage
    x0, y0, z0 = org
    jb = BELLY_Y + 3 - y0
    body = np.isin(G[:, jb, :] // 16, [P["body"], P["ridge"], P["head"], P["jaw"], P["tail"]]) & (G[:, jb, :] != 0)
    inner = ndimage.binary_erosion(body, iterations=3)
    jl = GROUND - y0
    for j in range(jl, jb):
        layer = G[:, j, :]
        layer[inner & (layer == 0)] = P["body"] * 16 + M["belly"]
    return G


def to_blocks(G, org):
    x0, y0, z0 = org
    names = ["air"]
    index = {"air": 0}

    def idx(n):
        if n not in index:
            index[n] = len(names)
            names.append(n)
        return index[n]

    B = np.zeros(G.shape, np.int16)
    ii, jj, kk = np.nonzero(G)
    keys = G[ii, jj, kk]
    wx, wy, wz = ii + x0, jj + y0, kk + z0
    h = hash3(wx, wy, wz)
    out = np.zeros(len(keys), np.int16)
    mats = keys % 16
    for mat in np.unique(mats):
        sel = mats == mat
        opts = BLOCKS[int(mat)]
        if not opts:
            continue
        w = np.cumsum([o[1] for o in opts]).astype(float)
        w /= w[-1]
        pick = np.minimum(np.searchsorted(w, h[sel], side="right"), len(opts) - 1)
        ids = np.array([idx(o[0]) for o in opts], np.int16)
        out[sel] = ids[pick]
    # 腹の下の詰め (灰の原の上、y 64〜66)
    fill = (mats == M["belly"]) & (wy < BELLY_Y + 3)
    bw = np.cumsum([o[1] for o in BELLY_BLOCKS]).astype(float)
    bw /= bw[-1]
    bp = np.minimum(np.searchsorted(bw, h[fill], side="right"), len(BELLY_BLOCKS) - 1)
    out[fill] = np.array([idx(o[0]) for o in BELLY_BLOCKS], np.int16)[bp]
    B[ii, jj, kk] = out
    G[B == 0] = 0
    # 外から 5 ブロックより深い体の中は凝灰岩 (下の灰の原は外に数えない)
    from scipy import ndimage
    occ = np.pad(B != 0, 1)
    occ[:, 0, :] = True
    depth = ndimage.distance_transform_cdt(occ, metric="chessboard")[1:-1, 1:-1, 1:-1]
    deep = (depth > INNER_DEPTH) & (B != 0)
    B[deep] = idx(DEEP)
    return B, names


def walkable_plates(B, G, org):
    """背の上を歩けるようにする: 鱗板の段で、隣が 2〜3 高い柱に同じ石を 1 段だけ足す (y 100 より上。2 回)"""
    x0, y0, z0 = org
    added = 0
    for _ in range(2):
        occ = B != 0
        NY = B.shape[1]
        top = np.where(occ.any(1), NY - 1 - np.argmax(occ[:, ::-1, :], axis=1), -1)
        part = np.take_along_axis(G, np.maximum(top, 0)[:, None, :], 1)[:, 0, :] // 16
        nb = np.full(top.shape, -1)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nb = np.maximum(nb, np.roll(np.roll(top, dx, 0), dz, 1))
        diff = nb - top
        sel = np.isin(part, list(SHELL_PARTS)) & (top + y0 > 100) & (diff >= 2) & (diff <= 3) & (top + 1 < NY)
        ii, kk = np.nonzero(sel)
        for a, c in zip(ii, kk):
            t = top[a, c]
            B[a, t + 1, c] = B[a, t, c]
            G[a, t + 1, c] = G[a, t, c]
        added += len(ii)
    print("plate steps added", added)


def write(B, names, org, G):
    x0, y0, z0 = org
    NX, NY, NZ = B.shape
    shell = np.isin(G // 16, list(SHELL_PARTS)) & (B != 0)
    buf = bytearray()
    buf += b"TBT1"
    buf += struct.pack(">iiiiii", x0, y0, z0, NX, NY, NZ)
    buf += struct.pack(">h", len(names))
    for n in names:
        e = n.encode("utf-8")
        buf += struct.pack(">h", len(e)) + e
    runs = 0
    for a in range(NX):
        for c in range(NZ):
            col = B[a, :, c]
            nzj = np.nonzero(col)[0]
            bj = np.nonzero(shell[a, :, c])[0]
            buf += struct.pack(">h", int(bj.max()) if len(bj) else -1)
            if len(nzj) == 0:
                buf += struct.pack(">h", 0)
                continue
            seg = []
            j = nzj[0]
            while j < NY:
                v = col[j]
                if v == 0:
                    j += 1
                    continue
                s = j
                while j < NY and col[j] == v:
                    j += 1
                seg.append((s, j - s, int(v)))
            buf += struct.pack(">h", len(seg))
            for s, n, v in seg:
                buf += struct.pack(">hhh", s, n, v)
            runs += len(seg)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(gzip.compress(bytes(buf), 9))
    print("wrote", OUT.relative_to(ROOT), "runs", runs, "bytes", OUT.stat().st_size, "palette", len(names))


def preview(B, names, org):
    import json
    from PIL import Image
    col = json.load(open(ROOT / "tools/rough/block_colors.json"))
    col.update(EXTRA_COLORS)
    pal = np.array([col.get(n, (255, 0, 255)) for n in names], np.uint8)
    pal[0] = (40, 28, 26)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    NX, NY, NZ = B.shape
    occ = B != 0
    top = np.where(occ.any(1), NY - 1 - np.argmax(occ[:, ::-1, :], axis=1), -1)
    img = pal[np.take_along_axis(B, np.maximum(top, 0)[:, None, :], 1)[:, 0, :]]
    shade = (0.5 + 0.5 * np.clip(top / float(NY), 0, 1))[..., None]
    img = (img * shade).astype(np.uint8)
    img[top < 0] = (40, 28, 26)
    Image.fromarray(np.ascontiguousarray(np.transpose(img, (1, 0, 2)))).resize((NX * 2, NZ * 2), Image.NEAREST) \
        .save(PREVIEW / "body_top.png")
    first = np.where(occ.any(0), np.argmax(occ, axis=0), -1)        # 西から
    side = pal[np.take_along_axis(B, np.maximum(first, 0)[None, :, :], 0)[0]]
    side[first < 0] = (40, 28, 26)
    Image.fromarray(np.ascontiguousarray(side[::-1, :])).resize((NZ * 2, NY * 2), Image.NEAREST).save(PREVIEW / "body_side.png")
    firstz = np.where(occ.any(2), np.argmax(occ, axis=2), -1)       # 北から
    front = pal[np.take_along_axis(B, np.maximum(firstz, 0)[:, :, None], 2)[:, :, 0]]
    front[firstz < 0] = (40, 28, 26)
    Image.fromarray(np.ascontiguousarray(np.transpose(front, (1, 0, 2))[::-1])).resize((NX * 2, NY * 2), Image.NEAREST) \
        .save(PREVIEW / "body_front.png")
    print("preview saved", PREVIEW.relative_to(ROOT))


def main():
    G, org = block_grid()
    G = add_belly(G, org)
    B, names = to_blocks(G, org)
    walkable_plates(B, G, org)
    # 空の端 (煙を消した上の空など) を切り落とす (置くときに見る範囲を小さくする)
    nz = np.argwhere(B != 0)
    lo, hi = nz.min(0), nz.max(0)
    B = np.ascontiguousarray(B[lo[0]:hi[0] + 1, lo[1]:hi[1] + 1, lo[2]:hi[2] + 1])
    G = np.ascontiguousarray(G[lo[0]:hi[0] + 1, lo[1]:hi[1] + 1, lo[2]:hi[2] + 1])
    org = (org[0] + int(lo[0]), org[1] + int(lo[1]), org[2] + int(lo[2]))
    nz = np.argwhere(B != 0)
    lo, hi = nz.min(0), nz.max(0)
    print("origin", org, "shape", B.shape, "blocks", len(nz))
    print("world bounds x", lo[0] + org[0], hi[0] + org[0], "y", lo[1] + org[1], hi[1] + org[1],
          "z", lo[2] + org[2], hi[2] + org[2])
    if "--cache" in sys.argv:
        np.savez_compressed(ROOT / "tools/rough/_ember_back_body_cache.npz", G=G, B=B, org=np.array(org), names=np.array(names))
    write(B, names, org, G)
    if "--preview" in sys.argv:
        preview(B, names, org)


if __name__ == "__main__":
    main()
