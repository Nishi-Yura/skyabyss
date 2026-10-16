"""潮殻の背 (tide_back) の島を、巨神本体のモデル (採用案 B 珊瑚礁の背) と同じ形・同じ色のブロックの亀にする
(08 の 21 章「巨神の形の背中」)。

使い方 (リポジトリ直下で): python tools/gen_tide_back_turtle.py [--preview]
形の元: tools/install_tide_shell.py の build_grid() (ゲームの geo と同じ 0.5 単位の格子。珊瑚の房・塔・西の段々の庭・
        潮門の口の削り直しまで同じ)。build_grid は GeckoLib の X の反転に合わせて格子を反転しているので、ここで戻す
        (ラフの座標 = 本当の向き。頭は -Z、左 = -X。背中のディメンションでは頭を北に向けて置く)。
出力: src/main/resources/data/skyabyss/tide_back/turtle.bin (gzip。TideTurtle.java が読む)
      --preview で docs/review/tide_shell_roughs/install/tide_back_turtle_{top,side}.png (上と横から見た色の地図)

縮尺: 1 単位 = 0.85 ブロック (ゲームの巨神は 0.5 ブロック。その 1.7 倍)。甲羅の長さ 170、幅 143、高さ 53 ブロック。
      ゲームと同じ 1 倍 (長さ 100、高さ 31) では、三つの試練 (庭 44x85、三段の溜め 24 の高さ、干潟 48x33) と
      広間・心臓の間が甲羅の中に入らない。1.5 倍でも潮見の間の岩棚と庭の角が甲羅の外へ出る。1.7 倍で、試し済みの
      部屋の配置 (歩ける道・謎の解き方) を変えずに全部が甲羅の中に入る (08 の 21.1節)。
高さ: 休んでいるときの水面 (geo の y=7、TideShellEntity.REST_DRAFT 3.5 ブロック) を海面 (y=63) に合わせる。
      甲羅の下端 (geo y=6) は y=62、頂 (y=68) は y=114。ひれは水面に平たく出て、頭と首は海から立ち上がる。
ブロックの決め方: ブロックに入る格子のセルの多数決。占める割合が 0.45 (細い骨は 0.3) 以上なら置く。
      材料はモデルのテクスチャの色に近いバニラのブロック (下の BLOCKS)。水際より上 geo 8〜20 は藻で暗くする
      (モデルの wet_apply と同じ帯)。同じ材料の中の色の揺れは、ブロックの位置のハッシュで選ぶ (乱数なし)。
腹: モデルには甲羅の下が無い (水の下で見えない)。背中では心臓の間 (y 50〜62) を体の中に入れるため、
      甲羅の下に腹の板 (y 47〜61) を足す。水の下なので外からはほとんど見えない。
"""
import gzip
import struct
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools/rough"))
import install_tide_shell as I  # noqa: E402
import tide_shell_titan as T  # noqa: E402

OUT = ROOT / "src/main/resources/data/skyabyss/tide_back/turtle.bin"
PREVIEW = ROOT / "docs/review/tide_shell_roughs/install"

S = 0.85            # 1 単位あたりのブロック (ゲームの巨神の 1.7 倍)
WL = 7.0            # 海面 (y=63) に合わせる geo の y
SEA = 63
BELLY_Y0 = 47       # 腹の板の下端
INNER_DEPTH = 5     # これより深い体の中は凝灰岩
THIN = {2, 4, 5, 6, 7, 8, 10, I.GATE_PART}

M = T.MAT
# 材料 -> [(ブロック, 重み)]。色はモデルの TONES / mat_base に近いもの (バニラのテクスチャの平均色で選んだ)
BLOCKS = {
    M["shell"]: [("prismarine", 6), ("oxidized_cut_copper", 3), ("oxidized_copper", 1)],
    M["scute"]: [("prismarine_bricks", 1)],
    M["stone"]: [("smooth_sandstone", 1), ("cut_sandstone", 1)],
    M["ruin"]: [("stone_bricks", 5), ("mossy_stone_bricks", 3), ("cracked_stone_bricks", 2)],
    M["cpink"]: [("pink_concrete", 1), ("pink_wool", 1)],
    M["corange"]: [("orange_wool", 3), ("orange_concrete", 1)],
    M["cpurple"]: [("purpur_block", 1), ("purple_wool", 1)],
    M["cyellow"]: [("yellow_wool", 3), ("yellow_concrete", 1)],
    M["kelp"]: [("moss_block", 1), ("green_wool", 1)],
    M["skin"]: [("waxed_weathered_cut_copper", 2), ("waxed_weathered_copper", 1), ("prismarine", 1)],
    M["belly"]: [("smooth_sandstone", 1)],
    M["dark"]: [("polished_deepslate", 1)],
    M["eye"]: [("shroomlight", 1)],
    M["cyan"]: [("sea_lantern", 1)],
    M["pool"]: [("water", 1)],
    M["gate"]: [("sea_lantern", 1)],
    M["flip"]: [("oxidized_cut_copper", 1), ("prismarine", 1)],
    M["ray"]: [("dark_prismarine", 1)],
    M["barnacle"]: [("calcite", 1)],
}
WET_BLOCKS = [("dark_prismarine", 3), ("mossy_stone_bricks", 1)]
WET = {M["shell"], M["scute"], M["stone"], M["ruin"], M["skin"], M["flip"], M["belly"]}
BELLY_BLOCKS = [("smooth_sandstone", 2), ("sandstone", 1)]


def hash3(x, y, z, salt=0):
    h = (np.asarray(x, np.int64) * 73856093) ^ (np.asarray(y, np.int64) * 19349663) ^ \
        (np.asarray(z, np.int64) * 83492791) ^ (salt * 2654435761)
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF) / 65536.0


def axis_map(n, origin, f):
    """格子の番号 -> ブロックの番号 (f: 単位 -> ブロック の式)"""
    u = origin + (np.arange(n) + 0.5) / T.CELL
    return np.floor(f(u)).astype(int)


def block_grid():
    K = I.build_grid()
    K = np.ascontiguousarray(K[::-1])          # GeckoLib 用の反転を戻す (本当の向き)
    nx, ny, nz = K.shape
    bx = axis_map(nx, T.X0, lambda u: u * S)
    by = axis_map(ny, T.Y0, lambda u: SEA + (u - WL) * S)
    bz = axis_map(nz, T.Z0, lambda u: u * S)
    x0, y0, z0 = bx.min(), by.min(), bz.min()
    NBX, NBY, NBZ = bx.max() - x0 + 1, by.max() - y0 + 1, bz.max() - z0 + 1
    cx = np.bincount(bx - x0)
    cy = np.bincount(by - y0)
    cz = np.bincount(bz - z0)
    G = np.zeros((NBX, NBY, NBZ), np.int16)
    glow_keys = np.zeros(512, bool)
    for p in range(1, 12):
        for gm in I.GLOW_MATS:
            glow_keys[p * 32 + gm] = True
    thin_keys = np.zeros(512, bool)
    for p in THIN:
        thin_keys[p * 32:(p + 1) * 32] = True
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
    """甲羅の下に腹の板を足す (甲羅の下端の層に体があり、縁から 3 ブロック以上内側の柱)"""
    from scipy import ndimage
    x0, y0, z0 = org
    jb = 62 - y0
    body = (G[:, jb, :] // 32) == 1
    inner = ndimage.binary_erosion(body, iterations=3)
    jl = BELLY_Y0 - y0
    if jl < 0:
        pad = -jl
        G = np.concatenate([np.zeros((G.shape[0], pad, G.shape[2]), np.int16), G], axis=1)
        y0 -= pad
        jb += pad
        jl = 0
    for j in range(jl, jb):
        layer = G[:, j, :]
        layer[inner & (layer == 0)] = 1 * 32 + M["belly"]
    return G, (x0, y0, z0)


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
    yu = WL + (wy + 0.5 - SEA) / S
    hw = hash3(wx, wy, wz, 7)
    wet_p = np.clip((I.WATER_REST + I.WATER_BAND - yu) / I.WATER_BAND, 0, 1) ** 1.5
    out = np.zeros(len(keys), np.int16)
    mats = keys % 32
    parts = keys // 32
    for mat in np.unique(mats):
        sel = mats == mat
        opts = BLOCKS[int(mat)]
        w = np.cumsum([o[1] for o in opts]).astype(float)
        w /= w[-1]
        pick = np.searchsorted(w, h[sel], side="right")
        pick = np.minimum(pick, len(opts) - 1)
        ids = np.array([idx(o[0]) for o in opts], np.int16)
        out[sel] = ids[pick]
        if int(mat) in WET:
            ws = sel & (hw < wet_p)
            ww = np.cumsum([o[1] for o in WET_BLOCKS]).astype(float)
            ww /= ww[-1]
            wp = np.minimum(np.searchsorted(ww, h[ws], side="right"), len(WET_BLOCKS) - 1)
            out[ws] = np.array([idx(o[0]) for o in WET_BLOCKS], np.int16)[wp]
    # 腹の板 (水の下)
    belly = (parts == 1) & (mats == M["belly"]) & (wy < 62)
    bw = np.cumsum([o[1] for o in BELLY_BLOCKS]).astype(float)
    bw /= bw[-1]
    bp = np.minimum(np.searchsorted(bw, h[belly], side="right"), len(BELLY_BLOCKS) - 1)
    out[belly] = np.array([idx(o[0]) for o in BELLY_BLOCKS], np.int16)[bp]
    B[ii, jj, kk] = out
    # 外から 5 ブロックより深い体の中は、凝灰岩 (掘ったときの内壁は TideIslandBuilder.skin が潮の民の石にする)
    from scipy import ndimage
    depth = ndimage.distance_transform_cdt(np.pad(B != 0, 1), metric="chessboard")[1:-1, 1:-1, 1:-1]
    deep = (depth > INNER_DEPTH) & ((G // 32) == 1)
    B[deep] = idx("tuff")
    # 水 (潮だまり) は、横と下がふさがっているときだけ。漏れる所は青緑の石にする
    water = index.get("water")
    if water is not None:
        for _ in range(4):
            wi = np.argwhere(B == water)
            changed = 0
            for a, b, c in wi:
                ok = b > 0 and B[a, b - 1, c] != 0
                for da, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    aa, cc = a + da, c + dc
                    if not (0 <= aa < B.shape[0] and 0 <= cc < B.shape[2]) or B[aa, b, cc] == 0:
                        ok = False
                if not ok:
                    B[a, b, c] = idx("dark_prismarine")
                    changed += 1
            if not changed:
                break
    return B, names


def write(B, names, org, G):
    x0, y0, z0 = org
    NX, NY, NZ = B.shape
    body = (G // 32) == 1
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
            # 甲羅 (体の骨) のいちばん上 (無ければ -1)
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
    col = json.load(open(Path(sys.argv[0]).resolve().parent / "rough/block_colors.json")) \
        if (Path(sys.argv[0]).resolve().parent / "rough/block_colors.json").exists() else {}
    pal = np.array([col.get(n, (255, 0, 255)) if n != "air" else (0, 0, 0) for n in names], np.uint8)
    pal[names.index("water") if "water" in names else 0] = (40, 90, 200) if "water" in names else pal[0]
    pal[0] = (30, 60, 110)
    NX, NY, NZ = B.shape
    occ = B != 0
    top = np.where(occ.any(1), NY - 1 - np.argmax(occ[:, ::-1, :], axis=1), -1)
    img = pal[np.take_along_axis(B, np.maximum(top, 0)[:, None, :], 1)[:, 0, :]]
    shade = (0.55 + 0.45 * np.clip((top - (SEA - org[1])) / 46.0, 0, 1))[..., None]
    img = (img * shade).astype(np.uint8)
    img[top < 0] = (30, 60, 110)
    Image.fromarray(np.transpose(img, (1, 0, 2))).resize((NX * 3, NZ * 3), Image.NEAREST) \
        .save(PREVIEW / "tide_back_turtle_top.png")
    # 横 (西から見る)
    first = np.where(occ.any(0), np.argmax(occ, axis=0), -1)
    side = pal[np.take_along_axis(B, np.maximum(first, 0)[None, :, :], 0)[0]]
    side[first < 0] = (150, 190, 230)
    side = side[::-1, ::-1]
    Image.fromarray(np.ascontiguousarray(np.transpose(side[:, :], (0, 1, 2)))).resize((NZ * 3, NY * 3), Image.NEAREST) \
        .save(PREVIEW / "tide_back_turtle_side.png")
    print("preview saved")


def walkable_plates(B, G, org):
    """甲羅の上を歩けるようにする: 鱗板の段 (モデルは 2 単位 = 1.7 ブロックの段) で、隣が 2〜3 高い柱に、
    同じ石を 1 段だけ足す (板の縁の面取りの帯になる)。甲羅の上の高い所 (y 95 より上) だけ。2 回くり返す"""
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
        sel = (part == 1) & (top + y0 > 95) & (diff >= 2) & (diff <= 3) & (top + 1 < NY)
        ii, kk = np.nonzero(sel)
        for a, c in zip(ii, kk):
            t = top[a, c]
            B[a, t + 1, c] = B[a, t, c]
            G[a, t + 1, c] = G[a, t, c]
        added += len(ii)
    print("plate steps added", added)


def main():
    G, org = block_grid()
    G, org = add_belly(G, org)
    B, names = to_blocks(G, org)
    walkable_plates(B, G, org)
    nz = np.argwhere(B != 0)
    lo, hi = nz.min(0), nz.max(0)
    print("origin", org, "shape", B.shape, "blocks", len(nz))
    print("world bounds x", lo[0] + org[0], hi[0] + org[0], "y", lo[1] + org[1], hi[1] + org[1],
          "z", lo[2] + org[2], hi[2] + org[2])
    gate = np.argwhere((G // 32) == I.GATE_PART)
    if len(gate):
        g0, g1 = gate.min(0) + org, gate.max(0) + org
        print("gate bounds", g0.tolist(), g1.tolist())
    np.savez_compressed(Path(__file__).resolve().parent / "rough" / "_tide_back_turtle_cache.npz", G=G, B=B,
                        org=np.array(org), names=np.array(names)) if "--cache" in sys.argv else None
    write(B, names, org, G)
    if "--preview" in sys.argv:
        preview(B, names, org)


if __name__ == "__main__":
    main()
