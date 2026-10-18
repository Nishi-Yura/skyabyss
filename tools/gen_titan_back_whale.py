"""岩鯨の背 (titan_back) の島を、空を泳ぐ岩鯨のモデル (低ポリ案 C 「皮が岩になる鯨」の近景メッシュ) と同じ形・同じ色の
ブロックの鯨にする (02 の 3.1 章)。潮殻の tools/gen_tide_back_turtle.py と同じ方法。

使い方 (リポジトリ直下で): python tools/gen_titan_back_whale.py [--preview] [--fit] [--nowrite]
形の元: docs/review/lowpoly_roughs/lp_stone_whale/c/near_{mesh.json,atlas.png,glowmask.png}。
        tools/lowpoly_whale_voxel.py が 0.5 単位のセルの格子 (部品 * 16 + 材料) にする (メッシュの座標は geo と同じ。+X がゲームの左)。
        格子は X を反転して置く (頭は北 -Z、錨石は左のわき腹 = 西 -X)。材料の分け方 (皮・岩の殻・割れた岩・腹・苔) は
        lowpoly_whale_voxel.classify (アトラスの色と骨と高さ)。ブロックは下の BLOCKS。
出力: src/main/resources/data/skyabyss/titan_back/whale.bin (gzip。WhaleShape.java が読む。形式は潮殻の turtle.bin と同じ)
      --preview で docs/review/whale_back_roughs/whale_back_{top,side}.png (上と横から見た色の地図)
      --fit で、体の中の部屋が殻の何ブロック内側にあるかを表にする (東の広間 e1 が 1 以上、肋骨の谷 wa が 3 ほど)
      建て直したあとの図と案 C のメッシュとの並べ描きは tools/whale_back_c_views.py

縮尺: 1 単位 = 1.43 ブロック。体の軸 x = -2。環境変数 WS / WY / WX / WZ で縮尺と置き場所を試せる。
高さ: 体の中心 (geo の y=40) を y 95 に置く。geo の z 0 を z 24 に置く。
ブロックの決め方: ブロックに入る格子のセルの多数決。占める割合が 0.45 (細い部品は 0.3) 以上なら置く。
      光る材料 (目) は少しでもあれば残す。同じ材料の中の色の揺れは、ブロックの位置のハッシュで選ぶ (乱数なし)。
"""
import gzip
import struct
import sys
from pathlib import Path

import numpy as np
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools/rough"))
import lowpoly_whale_voxel as I  # noqa: E402

class V:  # 格子の決まり (lowpoly_whale_voxel と同じ)
    CELL, OX, OY, OZ, YC = I.CELL, I.OX, I.OY, I.OZ, I.YC


OUT = ROOT / "src/main/resources/data/skyabyss/titan_back/whale.bin"
PREVIEW = ROOT / "docs/review/whale_back_roughs"

S = float(__import__("os").environ.get("WS", "1.43"))             # 1 単位あたりのブロック (ゲームの岩鯨の 1.4 倍)
CX = float(__import__("os").environ.get("WX", "-2"))            # Blockbench の x=0 (体の軸) を置く世界の x
YW = float(__import__("os").environ.get("WY", "95"))           # geo の y=YC (体の中心) を置く世界の y
ZOFF = float(__import__("os").environ.get("WZ", "24"))         # geo の z=0 を置く世界の z
INNER_DEPTH = 5     # これより深い体の中は、凝灰岩と深層岩 (部屋を掘ると壁は深層岩のレンガになる)

P = I.PARTS
M = I.MAT
BODY_PARTS = {P["head"], P["jaw"], P["body"], P["tail"]}
THIN = {P["fin_left"], P["fin_right"], P["fluke"], P["spine"], P["chain"]}
GLOW = {M["glyph"], M["eye"], M["cyan"]}

# 材料 -> [(ブロック, 重み)]。色は低ポリ案 C のアトラスの色 (lowpoly_whale_voxel.classify の分類) に近いバニラ。
#   stone = 青灰の皮 (風化した皮)、plate = 明るい茶の岩の殻、crust = 暗い割れた岩、belly = 腹、dark = 暗い皮・口
BLOCKS = {
    M["stone"]: [("tuff", 3), ("polished_tuff", 2), ("cobbled_deepslate", 2), ("cyan_terracotta", 1)],
    M["belly"]: [("andesite", 3), ("diorite", 2), ("stone", 1)],
    M["moss"]: [("moss_block", 1)],
    M["dark"]: [("deepslate", 2), ("polished_deepslate", 1)],
    M["bone"]: [("bone_block", 1)],
    M["fin"]: [("cobbled_deepslate", 3), ("polished_deepslate", 2), ("deepslate_tiles", 1)],
    M["ruin"]: [("tuff", 3), ("stone_bricks", 2), ("cracked_stone_bricks", 1), ("mossy_stone_bricks", 1)],
    M["glyph"]: [("sea_lantern", 1)],
    M["eye"]: [("ochre_froglight", 1)],
    M["cyan"]: [("sea_lantern", 1)],
    M["iron"]: [("deepslate_tiles", 2), ("polished_deepslate", 1)],
    M["plate"]: [("packed_mud", 3), ("coarse_dirt", 1), ("mud_bricks", 2), ("granite", 1), ("dripstone_block", 2)],
    M["baleen"]: [("bone_block", 1)],
    M["brow"]: [("deepslate_tiles", 1)],
    M["crust"]: [("brown_terracotta", 3), ("gray_terracotta", 2), ("mud_bricks", 1), ("blackstone", 1), ("dripstone_block", 1)],
}
MOSS_BLOCKS = [("moss_block", 4), ("green_terracotta", 1)]
INNER_BLOCKS = [("tuff", 3), ("deepslate", 1)]


def hash3(x, y, z, salt=0):
    h = (np.asarray(x, np.int64) * 73856093) ^ (np.asarray(y, np.int64) * 19349663) ^ \
        (np.asarray(z, np.int64) * 83492791) ^ (salt * 2654435761)
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF) / 65536.0


def axis_map(n, origin, f):
    u = (np.arange(n) - origin + 0.5) / V.CELL
    return np.floor(f(u)).astype(int)


def model_grid():
    """低ポリ案 C の近景メッシュの体積化 (lowpoly_whale_voxel。数十秒かかるので scratch にキャッシュ)"""
    import hashlib
    import tempfile
    h = hashlib.sha1()
    for f in (ROOT / "tools/lowpoly_whale_voxel.py", I.CDIR / "near_mesh.json", I.CDIR / "near_atlas.png",
              I.CDIR / "near_glowmask.png"):
        h.update(f.read_bytes())
    cache = Path(tempfile.gettempdir()) / ("stone_whale_c_grid_%s.npy" % h.hexdigest()[:12])
    if cache.exists():
        return np.load(cache)
    K = I.voxelize()
    np.save(cache, K)
    return K


def block_grid():
    K = model_grid()
    nx, ny, nz = K.shape
    # X は反転 (Blockbench の +X = ゲームの左 = 頭を北に向けたときの西)
    bx = axis_map(nx, V.OX, lambda u: CX - u * S)
    by = axis_map(ny, V.OY, lambda u: YW + (u - V.YC) * S)
    bz = axis_map(nz, V.OZ, lambda u: ZOFF + u * S)
    x0, y0, z0 = bx.min(), by.min(), bz.min()
    NBX, NBY, NBZ = bx.max() - x0 + 1, by.max() - y0 + 1, bz.max() - z0 + 1
    cx = np.bincount(bx - x0, minlength=NBX)
    cy = np.bincount(by - y0, minlength=NBY)
    cz = np.bincount(bz - z0, minlength=NBZ)
    G = np.zeros((NBX, NBY, NBZ), np.int16)
    NK = 16 * 16
    glow_keys = np.zeros(NK, bool)
    thin_keys = np.zeros(NK, bool)
    for p in range(1, 16):
        for g in GLOW:
            glow_keys[p * 16 + g] = True
        if p in THIN:
            thin_keys[p * 16:(p + 1) * 16] = True
    for b in range(NBZ):
        ks = np.nonzero(bz - z0 == b)[0]
        if len(ks) == 0:
            continue
        sub = K[:, :, ks]
        nzi = np.nonzero(sub)
        if len(nzi[0]) == 0:
            continue
        key = sub[nzi].astype(np.int64)
        lin = ((bx[nzi[0]] - x0) * NBY + (by[nzi[1]] - y0)) * NK + key
        cnt = np.bincount(lin, minlength=NBX * NBY * NK).reshape(NBX * NBY, NK)
        tot = cnt.sum(1)
        cells = (cx[:, None] * cy[None, :]).reshape(-1) * cz[b]
        best = cnt.argmax(1)
        gcnt = np.where(glow_keys[None, :], cnt, 0)
        use_g = gcnt.max(1) >= 2
        k = np.where(use_g, gcnt.argmax(1), best)
        frac = tot / np.maximum(cells, 1)
        thr = np.where(thin_keys[k], 0.3, 0.45)
        keep = (frac >= thr) | use_g
        G[:, :, b] = np.where(keep, k, 0).reshape(NBX, NBY).astype(np.int16)
    return G, (int(x0), int(y0), int(z0))


def to_blocks(G, org):
    x0, y0, z0 = org
    names = ["air"]
    index = {"air": 0}

    def idx(n):
        if n not in index:
            index[n] = len(names)
            names.append(n)
        return index[n]

    def pick(opts, h):
        w = np.cumsum([o[1] for o in opts]).astype(float)
        w /= w[-1]
        p = np.minimum(np.searchsorted(w, h, side="right"), len(opts) - 1)
        return np.array([idx(o[0]) for o in opts], np.int16)[p]

    B = np.zeros(G.shape, np.int16)
    ii, jj, kk = np.nonzero(G)
    keys = G[ii, jj, kk]
    wx, wy, wz = ii + x0, jj + y0, kk + z0
    h = hash3(wx, wy, wz)
    mats = keys % 16
    out = np.zeros(len(keys), np.int16)
    for mat in np.unique(mats):
        sel = mats == mat
        out[sel] = pick(BLOCKS[int(mat)], h[sel])
    B[ii, jj, kk] = out
    # 苔: 体の石の、上を向いた面 (上が空いているブロック)。モデルの苔の帯と同じ高さの割合 (geo YC+6 から 10 単位で 0.6)
    above_air = np.zeros(G.shape, bool)
    above_air[:, :-1, :] = G[:, 1:, :] == 0
    above_air[:, -1, :] = True
    yu = V.YC + (wy + 0.5 - YW) / S
    mp = 0.0 * yu   # 案 C は苔をアトラスの色 (moss 材料) で持つ。塗り足さない
    stone = (mats == M["stone"]) & np.isin(keys // 16, list(BODY_PARTS)) & above_air[ii, jj, kk]
    hm = hash3(wx // 2, wy, wz // 2, 3)
    moss = stone & (hm < mp)
    B[ii[moss], jj[moss], kk[moss]] = pick(MOSS_BLOCKS, h[moss])
    # 外から INNER_DEPTH より深い体の中は、凝灰岩と深層岩 (今の島の中と同じ)
    depth = ndimage.distance_transform_cdt(np.pad(B != 0, 1), metric="chessboard")[1:-1, 1:-1, 1:-1]
    body = np.isin(G // 16, list(BODY_PARTS))
    deep = (depth > INNER_DEPTH) & body
    di = np.argwhere(deep)
    B[deep] = pick(INNER_BLOCKS, hash3(di[:, 0] + x0, di[:, 1] + y0, di[:, 2] + z0, 9))
    return B, names, depth


def walkable(B, G, org):
    """背の上を歩けるようにする: 体の上の面で、隣が 2〜3 高い柱に同じ石を 1 段足す (段の面取り)。2 回くり返す"""
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
        sel = np.isin(part, list(BODY_PARTS)) & (top + y0 > YW + 4) & (diff >= 2) & (diff <= 3) & (top + 1 < NY)
        for a, c in zip(*np.nonzero(sel)):
            t = top[a, c]
            B[a, t + 1, c] = B[a, t, c]
            G[a, t + 1, c] = G[a, t, c]
        added += int(sel.sum())
    print("steps added", added)


def write(B, names, org, G):
    x0, y0, z0 = org
    NX, NY, NZ = B.shape
    body = np.isin(G // 16, list(BODY_PARTS))
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
            bj = np.nonzero(body[a, :, c])[0]
            # 体 (頭・顎・胴・尾) のいちばん上 (無ければ -1)。背の遺跡・背骨・ひれは含まない
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
    print("wrote", OUT, "runs", runs, "bytes", OUT.stat().st_size, "palette", len(names))


def preview(B, names, org):
    import json
    from PIL import Image
    col = json.load(open(ROOT / "tools/rough/block_colors.json"))
    extra = {"sea_lantern": (172, 200, 191), "ochre_froglight": (250, 220, 150), "bone_block": (229, 225, 207),
             "moss_block": (89, 110, 45), "calcite": (223, 224, 220), "white_terracotta": (210, 178, 161),
             "packed_mud": (142, 106, 79), "coarse_dirt": (119, 85, 59), "mud_bricks": (137, 104, 79), "granite": (149, 103, 85),
             "dripstone_block": (134, 107, 92), "brown_terracotta": (77, 51, 36), "gray_terracotta": (57, 42, 35),
             "green_terracotta": (76, 83, 42), "cyan_terracotta": (86, 91, 91)}
    pal = np.array([extra.get(n, col.get(n, (255, 0, 255)))[:3] if n != "air" else (0, 0, 0) for n in names], np.uint8)
    NX, NY, NZ = B.shape
    occ = B != 0
    top = np.where(occ.any(1), NY - 1 - np.argmax(occ[:, ::-1, :], axis=1), -1)
    img = pal[np.take_along_axis(B, np.maximum(top, 0)[:, None, :], 1)[:, 0, :]]
    shade = (0.55 + 0.45 * np.clip((top - (YW - 30 - org[1])) / 60.0, 0, 1))[..., None]
    img = (img * shade).astype(np.uint8)
    img[top < 0] = (200, 214, 230)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.ascontiguousarray(np.transpose(img, (1, 0, 2)))).resize((NX * 3, NZ * 3), Image.NEAREST) \
        .save(PREVIEW / "whale_back_top.png")
    first = np.where(occ.any(0), np.argmax(occ, axis=0), -1)       # 西から見る
    side = pal[np.take_along_axis(B, np.maximum(first, 0)[None, :, :], 0)[0]]
    side[first < 0] = (200, 214, 230)
    side = side[::-1, :]
    Image.fromarray(np.ascontiguousarray(side)).resize((NZ * 3, NY * 3), Image.NEAREST) \
        .save(PREVIEW / "whale_back_side.png")
    print("preview saved", PREVIEW)


def fit(B, org, depth):
    """部屋の外側の箱 (壁を含む) の、殻 (体の外) までのいちばん近い距離 (ブロック)。0 なら殻を突き抜ける"""
    sys.path.insert(0, str(ROOT / "tools"))
    import titan_back_rooms as R
    x0, y0, z0 = org
    worst = []
    for name, (a, b, c, d, e, f) in R.rooms().items():
        sl = (slice(a - x0, b - x0 + 1), slice(e - y0, f - y0 + 1), slice(c - z0, d - z0 + 1))
        sub = depth[sl]
        m = int(sub.min()) - 1 if sub.size else -1
        worst.append((m, name))
    worst.sort()
    for m, name in worst:
        print("  %-20s shell %d" % (name, m))
    return worst


def main():
    G, org = block_grid()
    B, names, depth = to_blocks(G, org)
    walkable(B, G, org)
    nz = np.argwhere(B != 0)
    lo, hi = nz.min(0), nz.max(0)
    print("origin", org, "shape", B.shape, "blocks", len(nz))
    print("world bounds x", lo[0] + org[0], hi[0] + org[0], "y", lo[1] + org[1], hi[1] + org[1],
          "z", lo[2] + org[2], hi[2] + org[2])
    ch = np.argwhere((G // 16) == P["chain"])
    if len(ch):
        print("anchor (chain part) bounds", (ch.min(0) + org).tolist(), (ch.max(0) + org).tolist())
    if "--fit" in sys.argv:
        fit(B, org, depth)
    if "--nowrite" not in sys.argv:
        write(B, names, org, G)
    if "--preview" in sys.argv:
        preview(B, names, org)


if __name__ == "__main__":
    main()
