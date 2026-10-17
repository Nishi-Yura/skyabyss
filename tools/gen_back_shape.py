"""巨神の形の背中 (ブロックの鳥・亀) を、巨神本体のモデルから作る共通の道具。

使い方 (リポジトリ直下で):
  python tools/gen_back_shape.py turtle [--preview]   潮殻の背の亀 -> src/main/resources/data/skyabyss/tide_back/turtle.bin
  python tools/gen_back_shape.py bird [--preview]     雷翼の背の鳥  -> src/main/resources/data/skyabyss/storm_back/bird.bin
tools/gen_tide_back_turtle.py は、このファイルの turtle を呼ぶ薄い入口 (同じ turtle.bin ができる)。

作り方 (両方とも同じ):
- 形の元: 各巨神の格子 (0.5 単位。ゲームの geo と同じ形)。格子は GeckoLib の X の反転に合わせて作ってあるので、
  ここで戻して本当の向きにする (頭は -Z、左 = -X)。
- 縮尺: 1 単位 = cfg.S ブロック (両方 0.85。ゲームの巨神の 1.7 倍)。位置は cfg の ymap / 原点で決める。
- ブロックの決め方: ブロックに入る格子のセルの多数決。占める割合が 0.45 (細い骨は 0.3) 以上なら置く。
  光る材料は少しでもあれば優先して残す。材料はモデルのテクスチャの色に近いバニラのブロック (cfg.blocks)。
  同じ材料の中の色の揺れは、ブロックの位置のハッシュで選ぶ (乱数なし)。
- 体の中: 外から 5 ブロックより深い所は凝灰岩 (掘ると部屋の壁を石にする。Java 側で部屋を掘る)。
- 出力の形式 (TBT1): gzip。ヘッダ (原点、大きさ)、ブロックの名前の表、列ごとに甲羅 (体の骨) のいちばん上、
  その列の連続した同じブロックの区間 (開始、長さ、番号)。TideTurtle.java が読む。

亀 (turtle) だけの処理: 甲羅の下に腹の板を足す、水際の藻、潮だまり、甲羅の上の段差。
鳥 (bird) だけの処理: 翼の芯の厚みを 1.4 倍にする (外の形はほとんど変わらず、翼の骨の中に 12〜16 の回廊が入る)。
"""
import gzip
import json
import struct
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools/rough"))

PREVIEW = ROOT / "docs/review/tide_shell_roughs/install"
BIRD_PREVIEW = ROOT / "docs/review/thunder_wing_roughs/install"
BLOCK_COLORS = ROOT / "tools/rough/block_colors.json"
INNER_DEPTH = 5        # これより深い体の中は凝灰岩
WING_SCALE = 1.4       # 鳥の翼の芯の厚み (この倍率。翼の骨の回廊が入るように)

# 名前 (表示用の色の予備。block_colors.json に無いブロックだけ使う)
FALLBACK_COLORS = {
    "waxed_copper_block": (196, 112, 70), "waxed_cut_copper": (200, 118, 76), "white_concrete": (207, 213, 214),
    "light_gray_concrete": (125, 125, 115), "gray_concrete": (55, 58, 62), "light_blue_stained_glass": (120, 170, 220),
    "ochre_froglight": (245, 233, 182), "white_terracotta": (209, 178, 161), "andesite": (136, 136, 136),
    "polished_andesite": (132, 135, 135), "diorite": (188, 188, 188), "polished_diorite": (196, 196, 196),
    "deepslate_tiles": (60, 60, 64), "cobbled_deepslate": (74, 74, 78), "polished_deepslate": (72, 73, 73),
    "tuff_bricks": (110, 112, 110), "polished_tuff": (110, 110, 105), "light_blue_terracotta": (113, 109, 151),
    "bone_block": (229, 226, 208), "stone": (125, 125, 125), "blackstone": (42, 36, 41), "basalt": (72, 72, 78),
    "sea_lantern": (172, 200, 191), "glowstone": (172, 131, 84), "tuff": (108, 109, 103),
    "air": (30, 60, 110), "water": (40, 90, 200),
}


# ---------------------------------------------------------------- 設定

def turtle_cfg():
    """潮殻の亀 (08 の 21 章)。格子は install_tide_shell.build_grid() (tide の番号)"""
    import install_tide_shell as I
    import tide_shell_titan as TT
    M = TT.MAT
    blocks = {
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
    return dict(
        name="turtle", out=ROOT / "src/main/resources/data/skyabyss/tide_back/turtle.bin",
        preview_prefix="tide_back_turtle", preview_dir=PREVIEW,
        build=lambda: np.ascontiguousarray(I.build_grid()[::-1]), mat_of=M,   # GeckoLib 用の反転を戻す (本当の向き)
        cell=TT.CELL, origin=(TT.X0, TT.Y0, TT.Z0), S=0.85,
        # 水面 (geo の y=7) を海面 (y=63) に合わせる
        ymap=lambda u: 63 + (u - 7.0) * 0.85, S_y=0.85, sea=63, wl=7.0,
        blocks=blocks, wet={M["shell"], M["scute"], M["stone"], M["ruin"], M["skin"], M["flip"], M["belly"]},
        wet_blocks=[("dark_prismarine", 3), ("mossy_stone_bricks", 1)],
        glow=list(I.GLOW_MATS), thin={2, 4, 5, 6, 7, 8, 10, I.GATE_PART}, max_part=11,
        deep_parts=None,            # 体 (骨 1) だけ
        wing=None, belly=True, plates=True, belly_mat=M["belly"], belly_y0=47, water=True,
        water_rest=I.WATER_REST, water_band=I.WATER_BAND,
    )


def bird_cfg():
    """雷翼の鳥 (12 の 3.2節)。格子は install_thunder_wing.build_grid() (骨の番号、左 = +X で返る)"""
    import install_thunder_wing as IW
    import thunder_wing_titan as TW
    M = TW.MAT
    B = {
        # 背と翼の上: 灰青の羽 (深い石・灰のコンクリート・灰のテラコッタ)
        "back": [("cobbled_deepslate", 3), ("polished_deepslate", 2), ("basalt", 2)],
        "cov": [("polished_tuff", 3), ("tuff_bricks", 2), ("light_blue_terracotta", 1)],
        "sec": [("gray_concrete", 3), ("basalt", 1)],
        "prim": [("gray_concrete", 2), ("deepslate_tiles", 2), ("blackstone", 1)],
        "tailf": [("basalt", 2), ("cobbled_deepslate", 1)],
        # 腹と翼の下、羽の先: 白い羽
        "belly": [("white_concrete", 3), ("calcite", 1)],
        "under": [("light_gray_concrete", 2), ("white_concrete", 1)],
        "tip": [("calcite", 2), ("white_concrete", 1)],
        "plume": [("calcite", 2), ("white_concrete", 1)],
        "cloud": [("calcite", 1)],
        "cloud_d": [("light_blue_terracotta", 1)],
        # 翼の後縁: 濃い灰 (テクスチャの色が中間なので、いちばん近い石)
        "edge": [("smooth_stone", 1)],
        # 嘴・爪・眉・脚・石・銅
        "beak": [("white_terracotta", 2), ("bone_block", 1)],
        "beak_d": [("deepslate_tiles", 2), ("gray_concrete", 1)],
        "talon": [("blackstone", 3)],
        "leg": [("andesite", 2), ("polished_andesite", 1)],
        "brow": [("blackstone", 2), ("deepslate_tiles", 1)],
        "stone": [("diorite", 2), ("polished_diorite", 1)],
        "copper": [("waxed_copper_block", 2), ("waxed_cut_copper", 1)],
        # 光る材料: 稲妻の筋 (ochre froglight は淡い黄白で光る)、目 (グロー石)、羽根の門の面 (シーランタン)、
        # 手首の瘤 (白青 = シーランタン、金 = グロー石)、天窓のガラス
        "vein": [("ochre_froglight", 1)],
        "bolt": [("ochre_froglight", 1)],
        "eye": [("glowstone", 1)],
        "gate": [("sea_lantern", 1)],
        "knob_w": [("sea_lantern", 1)],
        "knob_t": [("glowstone", 1)],
        "glass": [("light_blue_stained_glass", 1)],
    }
    blocks = {M[k]: v for k, v in B.items()}
    glow = [M[k] for k in ("vein", "eye", "gate", "knob_w", "knob_t", "bolt", "glass")]
    return dict(
        name="bird", out=ROOT / "src/main/resources/data/skyabyss/storm_back/bird.bin",
        preview_prefix="storm_back_bird", preview_dir=BIRD_PREVIEW,
        build=lambda: np.ascontiguousarray(IW.build_grid()[::-1]),   # install の左 (+X) を本当の向き (左 = -X) に戻す
        mat_of=M,
        cell=TW.CELL, origin=(TW.X0, TW.Y0, TW.Z0), S=0.85,
        # 体の中心 (geo の y=40) を y=140 に置く
        ymap=lambda u: 140.0 + (u - 40.0) * 0.85, S_y=0.85,
        blocks=blocks, wet=set(), wet_blocks=[],
        glow=glow, thin={3, 4, 6, 8, 10, 11, 12, 13}, max_part=13,
        deep_parts=set(range(1, 14)),
        wing=({7, 9}, WING_SCALE), belly=False, plates=False, belly_mat=None, belly_y0=None, water=False,
        water_rest=0.0, water_band=1.0,
    )


CONFIGS = {"turtle": turtle_cfg, "bird": bird_cfg}


# ---------------------------------------------------------------- 格子 -> ブロック

def hash3(x, y, z, salt=0):
    h = (np.asarray(x, np.int64) * 73856093) ^ (np.asarray(y, np.int64) * 19349663) ^ \
        (np.asarray(z, np.int64) * 83492791) ^ (salt * 2654435761)
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF) / 65536.0


def axis_map(n, origin, f, cell):
    """格子の番号 -> ブロックの番号 (f: 単位 -> ブロック の式)"""
    u = origin + (np.arange(n) + 0.5) / cell
    return np.floor(f(u)).astype(int)


def block_grid(c, K):
    """格子 K (本当の向き) をブロックの格子に直す。戻り値は (G, 原点)。G の値は 骨 * 32 + 材料"""
    S = c["S"]
    nx, ny, nz = K.shape
    ox, oy, oz = c["origin"]
    bx = axis_map(nx, ox, lambda u: u * S, c["cell"])
    by = axis_map(ny, oy, c["ymap"], c["cell"])
    bz = axis_map(nz, oz, lambda u: u * S, c["cell"])
    x0, y0, z0 = bx.min(), by.min(), bz.min()
    NBX, NBY, NBZ = bx.max() - x0 + 1, by.max() - y0 + 1, bz.max() - z0 + 1
    cx = np.bincount(bx - x0)
    cy = np.bincount(by - y0)
    cz = np.bincount(bz - z0)
    G = np.zeros((NBX, NBY, NBZ), np.int16)
    glow_keys = np.zeros(512, bool)
    for p in range(1, c["max_part"] + 1):
        for gm in c["glow"]:
            glow_keys[p * 32 + gm] = True
    thin_keys = np.zeros(512, bool)
    for p in c["thin"]:
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


def stretch_wings(G, parts, factor):
    """翼の芯の厚みを factor 倍にする (ブロックの列ごとに、翼の範囲の中心から上下へ広げる)。
    翼の外の形はほとんど変わらない。翼以外のブロックは動かさない。書き換えた列の数を返す"""
    W = np.isin(G // 32, list(parts))
    has = W.any(1)
    ii, kk = np.nonzero(has)
    n = 0
    for a, c in zip(ii, kk):
        col = G[a, :, c]
        wcol = W[a, :, c]
        js = np.nonzero(wcol)[0]
        lo, hi = js.min(), js.max()
        c0 = (lo + hi) / 2.0
        half = (hi - lo + 1) / 2.0 * factor
        nlo = int(np.floor(c0 - half))
        nhi = int(np.ceil(c0 + half))
        new = col.copy()
        new[wcol] = 0
        for j in range(max(nlo, 0), min(nhi, len(col) - 1) + 1):
            s = int(round(c0 + (j - c0) / factor))
            if lo <= s <= hi and col[s] != 0 and wcol[s] and new[j] == 0:
                new[j] = col[s]
        G[a, :, c] = new
        n += 1
    return n


def add_belly(G, org, c):
    """甲羅の下に腹の板を足す (甲羅の下端の層に体があり、縁から 3 ブロック以上内側の柱)"""
    from scipy import ndimage
    x0, y0, z0 = org
    jb = 62 - y0
    body = (G[:, jb, :] // 32) == 1
    inner = ndimage.binary_erosion(body, iterations=3)
    jl = c["belly_y0"] - y0
    if jl < 0:
        pad = -jl
        G = np.concatenate([np.zeros((G.shape[0], pad, G.shape[2]), np.int16), G], axis=1)
        y0 -= pad
        jb += pad
        jl = 0
    for j in range(jl, jb):
        layer = G[:, j, :]
        layer[inner & (layer == 0)] = 1 * 32 + c["belly_mat"]
    return G, (x0, y0, z0)


def to_blocks(G, org, c):
    from scipy import ndimage
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
    hw = hash3(wx, wy, wz, 7)
    wet = c["wet"]
    if wet:
        yu = c["wl"] + (wy + 0.5 - c["sea"]) / c["S"]
        wet_p = np.clip((c["water_rest"] + c["water_band"] - yu) / c["water_band"], 0, 1) ** 1.5
    out = np.zeros(len(keys), np.int16)
    mats = keys % 32
    parts = keys // 32
    for mat in np.unique(mats):
        sel = mats == mat
        opts = c["blocks"][int(mat)]
        w = np.cumsum([o[1] for o in opts]).astype(float)
        w /= w[-1]
        pick = np.searchsorted(w, h[sel], side="right")
        pick = np.minimum(pick, len(opts) - 1)
        ids = np.array([idx(o[0]) for o in opts], np.int16)
        out[sel] = ids[pick]
        if int(mat) in wet:
            ws = sel & (hw < wet_p)
            ww = np.cumsum([o[1] for o in c["wet_blocks"]]).astype(float)
            ww /= ww[-1]
            wp = np.minimum(np.searchsorted(ww, h[ws], side="right"), len(c["wet_blocks"]) - 1)
            out[ws] = np.array([idx(o[0]) for o in c["wet_blocks"]], np.int16)[wp]
    if c["belly"]:
        # 腹の板 (水の下)
        belly = (parts == 1) & (mats == c["belly_mat"]) & (wy < 62)
        bw = np.cumsum([o[1] for o in BELLY_BLOCKS]).astype(float)
        bw /= bw[-1]
        bp = np.minimum(np.searchsorted(bw, h[belly], side="right"), len(BELLY_BLOCKS) - 1)
        out[belly] = np.array([idx(o[0]) for o in BELLY_BLOCKS], np.int16)[bp]
    B[ii, jj, kk] = out
    # 外から 5 ブロックより深い体の中は、凝灰岩 (掘ったときの内壁は Java 側が石にする)
    depth = ndimage.distance_transform_cdt(np.pad(B != 0, 1), metric="chessboard")[1:-1, 1:-1, 1:-1]
    deep_mask = (G // 32) == 1 if c["deep_parts"] is None else np.isin(G // 32, list(c["deep_parts"]))
    deep = (depth > INNER_DEPTH) & deep_mask
    B[deep] = idx("tuff")
    # 水 (潮だまり) は、横と下がふさがっているときだけ。漏れる所は青緑の石にする
    water = index.get("water")
    if water is not None:
        for _ in range(4):
            wi = np.argwhere(B == water)
            changed = 0
            for a, b, cc in wi:
                ok = b > 0 and B[a, b - 1, cc] != 0
                for da, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    aa, c2 = a + da, cc + dc
                    if not (0 <= aa < B.shape[0] and 0 <= c2 < B.shape[2]) or B[aa, b, c2] == 0:
                        ok = False
                if not ok:
                    B[a, b, cc] = idx("dark_prismarine")
                    changed += 1
            if not changed:
                break
    return B, names


BELLY_BLOCKS = [("smooth_sandstone", 2), ("sandstone", 1)]


def write(c, B, names, org, G):
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
        for cc in range(NZ):
            col = B[a, :, cc]
            nzj = np.nonzero(col)[0]
            bj = np.nonzero(body[a, :, cc])[0]
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
    c["out"].parent.mkdir(parents=True, exist_ok=True)
    c["out"].write_bytes(gzip.compress(bytes(buf), 9))
    print("wrote", c["out"], "runs", runs, "bytes", c["out"].stat().st_size, "palette", len(names))


def palette_colors(names):
    col = json.loads(BLOCK_COLORS.read_text(encoding="utf-8")) if BLOCK_COLORS.exists() else {}
    out = []
    for n in names:
        if n in col:
            out.append(tuple(col[n]))
        elif n in FALLBACK_COLORS:
            out.append(FALLBACK_COLORS[n])
        else:
            out.append((255, 0, 255))
    return np.array(out, np.uint8)


def preview(c, B, names, org):
    """上・横・正面から見た色の地図 (3 倍)。鳥は正面 (北から) と横 (西から) も描く"""
    from PIL import Image
    pal = palette_colors(names)
    pal[0] = (30, 60, 110)
    NX, NY, NZ = B.shape
    occ = B != 0
    c["preview_dir"].mkdir(parents=True, exist_ok=True)
    pre = c["preview_prefix"]
    top = np.where(occ.any(1), NY - 1 - np.argmax(occ[:, ::-1, :], axis=1), -1)
    img = pal[np.take_along_axis(B, np.maximum(top, 0)[:, None, :], 1)[:, 0, :]]
    tmin, tmax = top[top >= 0].min(), top.max()
    shade = (0.55 + 0.45 * np.clip((top - tmin) / max(1.0, float(tmax - tmin)), 0, 1))[..., None]
    img = (img * shade).astype(np.uint8)
    img[top < 0] = (30, 60, 110)
    Image.fromarray(np.transpose(img, (1, 0, 2))).resize((NX * 3, NZ * 3), Image.NEAREST) \
        .save(c["preview_dir"] / (pre + "_top.png"))
    # 横 (西から見る): 各 (z, y) で x の一番西の塊
    first_x = np.where(occ.any(0), np.argmax(occ, axis=0), -1)
    side = pal[np.take_along_axis(B, np.maximum(first_x, 0)[None, :, :], 0)[0]]
    side[first_x < 0] = (150, 190, 230)
    side = side[::-1, ::-1]
    Image.fromarray(np.ascontiguousarray(side)).resize((NZ * 3, NY * 3), Image.NEAREST) \
        .save(c["preview_dir"] / (pre + "_side.png"))
    # 正面 (北から見る): 各 (x, y) で z の一番北の塊
    first_z = np.where(occ.any(2), np.argmax(occ, axis=2), -1)
    front = pal[np.take_along_axis(B, np.maximum(first_z, 0)[:, :, None], 2)[:, :, 0]]
    front[first_z < 0] = (150, 190, 230)
    front = np.transpose(front[:, ::-1], (1, 0, 2))[::-1]
    Image.fromarray(np.ascontiguousarray(front)).resize((NX * 3, NY * 3), Image.NEAREST) \
        .save(c["preview_dir"] / (pre + "_front.png"))
    print("preview saved", c["preview_dir"])


def walkable_plates(B, G, org):
    """甲羅の上を歩けるようにする: 鱗板の段で、隣が 2〜3 高い柱に、同じ石を 1 段だけ足す (板の縁の面取りの帯になる)。
    甲羅の上の高い所 (y 95 より上) だけ。2 回くり返す"""
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
        for a, cc in zip(ii, kk):
            t = top[a, cc]
            B[a, t + 1, cc] = B[a, t, cc]
            G[a, t + 1, cc] = G[a, t, cc]
        added += len(ii)
    print("plate steps added", added)


def run(kind, preview_on=False):
    c = CONFIGS[kind]()
    K = c["build"]()
    print(kind, "grid", K.shape, "cells", int((K != 0).sum()))
    if c["wing"]:
        parts, factor = c["wing"]
        G0, org = block_grid(c, K)
        print("wing columns stretched", stretch_wings(G0, parts, factor))
        G = G0
    else:
        G, org = block_grid(c, K)
    if c["belly"]:
        G, org = add_belly(G, org, c)
    B, names = to_blocks(G, org, c)
    if c["plates"]:
        walkable_plates(B, G, org)
    nz = np.argwhere(B != 0)
    lo, hi = nz.min(0), nz.max(0)
    print("origin", org, "shape", B.shape, "blocks", len(nz))
    print("world bounds x", lo[0] + org[0], hi[0] + org[0], "y", lo[1] + org[1], hi[1] + org[1],
          "z", lo[2] + org[2], hi[2] + org[2])
    write(c, B, names, org, G)
    if preview_on:
        preview(c, B, names, org)
    return c, B, names, org, G


if __name__ == "__main__":
    kind = sys.argv[1] if len(sys.argv) > 1 else "turtle"
    run(kind, "--preview" in sys.argv)
