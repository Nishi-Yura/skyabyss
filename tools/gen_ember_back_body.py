"""余燼の背 (ember_back) の体を、巨神本体のモデル (推奨 A 火山の背) と同じ形・同じ色のブロックの火蜥蜴にする
(13 の 3.2節、18 章。型は潮殻の gen_tide_back_turtle.py と 08 の 21 章)。

使い方 (リポジトリ直下で): python tools/gen_ember_back_body.py [--preview] [--cache]
形の元: tools/rough/ember_titan.py の build("a") (ラフの 0.5 単位の格子。本当の向き: 頭は -Z、左 = -X。反転はない)。
        取り込み (A の install_ember_titan.py) で形を変えたら、ここも同じ格子を読むように直す。
出力: src/main/resources/data/skyabyss/ember_back/body.bin (gzip。TitanBodyBlocks が読む。形式は "TBT1")
      --preview で docs/review/previews/ember_back/body_{top,side,front}.png (上・西・北から見た色の地図。git には入れない)
      --cache で tools/rough/_ember_back_body_cache.npz (調べる用)

縮尺: 1 単位 = 0.85 ブロック (ゲームの巨神は 0.5 ブロック。その 1.7 倍。潮殻と同じ。15 章の判断 2)。
      頭の先から尾の先まで 386、胴の幅 95、脚を入れた幅 202、火口の縁 y 172 前後。
高さ: モデルの地面 (y=0) を灰の原のいちばん上 (y=63) の上 (y=64) に合わせる。顎は灰の原に下りている。
ブロックの決め方: ブロックに入る格子のセルの多数決。占める割合が 0.45 (歯と爪は 0.3) 以上なら置く。光る材料 (継ぎ目・溶岩・熾・目) は
      3 セル以上あれば優先する。材料はモデルの色に近いバニラのブロック (下の BLOCKS。13 の 3.2節の表)。煙は置かない (粒で出す)。
      色の揺れはブロックの位置のハッシュで選ぶ (乱数なし。何度動かしても同じ物ができる)。
      溶岩の材料は流れないマグマブロックにする (外の溶岩が流れ出さないように)。灰溜まりの灰色の羊毛は体に使わない (熱が冷える目印のため)。
腹: 腹の下端 (y 67) と灰の原のあいだ (y 64〜66) を、腹の足あとの 3 ブロック内側だけ埋める (伏せている姿。中の部屋を低く掘れるように)。
中: 外から 5 ブロックより深い所は凝灰岩 (掘った壁は EmberBodyBuilder が「肉と骨の壁」にする)。下 (灰の原) は外に数えない。
背: 鱗板の段 (モデルは 2 単位 = 1.7 ブロック) に面取りの帯を 1 段足し、背の上を歩けるようにする (潮殻と同じ。y 100 より上)。
"""
import gzip
import struct
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/rough"))
import ember_titan as T  # noqa: E402

OUT = ROOT / "src/main/resources/data/skyabyss/ember_back/body.bin"
PREVIEW = ROOT / "docs/review/previews/ember_back"

S = 0.85            # 1 単位あたりのブロック (ゲームの巨神の 1.7 倍)
GROUND = 64         # モデルの y=0 を置く高さ (灰の原のいちばん上 y 63 の上)
BELLY_Y = 67        # 腹の下端 (この段の足あとを下へ埋める)
INNER_DEPTH = 5     # これより深い体の中は凝灰岩

M = T.MAT
P = T.PART
# 材料 -> [(ブロック, 重み)]
BLOCKS = {
    M["basalt"]: [("blackstone", 5), ("polished_basalt", 3), ("basalt", 2)],          # 鱗板
    M["sbasalt"]: [("smooth_basalt", 5), ("polished_deepslate", 1), ("deepslate_tiles", 1)],   # 脇腹・頭
    M["belly"]: [("polished_tuff", 2), ("tuff_bricks", 1), ("smooth_basalt", 1)],      # 腹 (中の凝灰岩と分ける)
    M["ash"]: [("light_gray_concrete", 3), ("andesite", 1)],                          # 火山の灰
    M["slag"]: [("mud_bricks", 2), ("packed_mud", 1), ("brown_terracotta", 1)],        # 鉱滓
    M["deepred"]: [("red_nether_bricks", 2), ("nether_wart_block", 1)],
    M["lava"]: [("magma_block", 1)],                                                  # 溶岩の流れ (流れないマグマ)
    M["seam"]: [("magma_block", 1)],                                                  # 鱗板の継ぎ目 (光る、踏むと熱)
    M["ember"]: [("magma_block", 2), ("shroomlight", 1)],                             # 喉の奥・火の井戸の壁
    M["molten"]: [("ochre_froglight", 1)],
    M["eye"]: [("shroomlight", 1)],
    M["dark"]: [("black_concrete", 1)],
    M["tongue"]: [("red_terracotta", 1)],
    M["teeth"]: [("bone_block", 1)],
    M["claw"]: [("polished_blackstone", 1)],
    M["bronze"]: [("waxed_exposed_cut_copper", 1), ("waxed_weathered_cut_copper", 1)],
    M["brick"]: [("red_nether_bricks", 1), ("polished_blackstone_bricks", 1)],
    M["column"]: [("basalt", 2), ("polished_basalt", 1)],
    M["slagdark"]: [("polished_blackstone", 2), ("blackstone", 1)],                    # 光らない継ぎ目
    M["crust"]: [("blackstone", 1), ("nether_bricks", 1)],                            # 火口の底
    M["gland"]: [("cobbled_deepslate", 1)],
    M["cinder"]: [("tuff", 1)],
    M["smoke"]: [], M["smoke2"]: [], M["steam"]: [],                                  # 置かない
}
GLOW_MATS = {M["lava"], M["seam"], M["ember"], M["molten"], M["eye"]}
THIN_MATS = {M["teeth"], M["claw"]}
SHELL_PARTS = {P["body"], P["ridge"], P["tail"]}
BELLY_BLOCKS = [("polished_tuff", 2), ("tuff_bricks", 1)]
DEEP = "tuff"
# block_colors.json に無いブロックの色 (見た目の確認だけに使う)
EXTRA_COLORS = {
    "polished_basalt": (88, 88, 92), "smooth_basalt": (72, 72, 78), "light_gray_concrete": (125, 125, 115),
    "red_nether_bricks": (69, 7, 9), "nether_wart_block": (114, 2, 2), "magma_block": (142, 63, 31),
    "polished_blackstone": (53, 48, 56), "waxed_exposed_cut_copper": (154, 121, 101),
    "polished_blackstone_bricks": (48, 42, 49),
}


def hash3(x, y, z, salt=0):
    h = (np.asarray(x, np.int64) * 73856093) ^ (np.asarray(y, np.int64) * 19349663) ^ \
        (np.asarray(z, np.int64) * 83492791) ^ (salt * 2654435761)
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF) / 65536.0


def axis_map(n, origin, f):
    u = origin + (np.arange(n) + 0.5) / T.CELL
    return np.floor(f(u)).astype(int)


def block_grid():
    T.build("a")
    K = T.K
    # 煙は置かない
    K[(K // 32) == P["smoke"]] = 0
    nx, ny, nz = K.shape
    bx = axis_map(nx, T.X0, lambda u: u * S)
    by = axis_map(ny, T.Y0, lambda u: GROUND + u * S)
    bz = axis_map(nz, T.Z0, lambda u: u * S)
    x0, y0, z0 = bx.min(), by.min(), bz.min()
    NBX, NBY, NBZ = bx.max() - x0 + 1, by.max() - y0 + 1, bz.max() - z0 + 1
    cx = np.bincount(bx - x0)
    cy = np.bincount(by - y0)
    cz = np.bincount(bz - z0)
    G = np.zeros((NBX, NBY, NBZ), np.int16)
    glow_keys = np.zeros(512, bool)
    thin_keys = np.zeros(512, bool)
    for p in range(1, 16):
        for gm in GLOW_MATS:
            glow_keys[p * 32 + gm] = True
        for tm in THIN_MATS:
            thin_keys[p * 32 + tm] = True
    for b in range(NBZ):
        ks = np.nonzero(bz - z0 == b)[0]
        if len(ks) == 0:
            continue
        sub = K[:, :, ks]
        nzi = np.nonzero(sub)
        if len(nzi[0]) == 0:
            continue
        key = sub[nzi].astype(np.int64)
        lin = ((bx[nzi[0]] - x0) * NBY + (by[nzi[1]] - y0)) * 512 + key
        cnt = np.bincount(lin, minlength=NBX * NBY * 512).reshape(NBX * NBY, 512)
        tot = cnt.sum(1)
        cells = (cx[:, None] * cy[None, :]).reshape(-1) * cz[b]
        best = cnt.argmax(1)
        gcnt = np.where(glow_keys[None, :], cnt, 0)
        gbest = gcnt.argmax(1)
        use_g = gcnt.max(1) >= 3
        k = np.where(use_g, gbest, best)
        frac = tot / np.maximum(cells, 1)
        thr = np.where(thin_keys[k], 0.3, 0.45)
        keep = (frac >= thr) | use_g
        G[:, :, b] = np.where(keep, k, 0).reshape(NBX, NBY).astype(np.int16)
    return G, (int(x0), int(y0), int(z0))


def add_belly(G, org):
    """腹の下端の段の足あと (3 ブロック内側) を、灰の原の上まで下へ埋める"""
    from scipy import ndimage
    x0, y0, z0 = org
    jb = BELLY_Y - y0
    body = np.isin(G[:, jb, :] // 32, list(SHELL_PARTS))
    inner = ndimage.binary_erosion(body, iterations=3)
    jl = GROUND - y0
    for j in range(jl, jb):
        layer = G[:, j, :]
        layer[inner & (layer == 0)] = P["body"] * 32 + M["belly"]
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
    mats = keys % 32
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
    fill = (mats == M["belly"]) & (wy < BELLY_Y)
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
        part = np.take_along_axis(G, np.maximum(top, 0)[:, None, :], 1)[:, 0, :] // 32
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
    shell = np.isin(G // 32, list(SHELL_PARTS)) & (B != 0)
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
