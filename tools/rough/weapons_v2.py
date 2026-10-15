"""武器ラフ 第4版: 16x16 のアイコンの形と色を保ったまま、4倍 (または6倍) の細かさで彫り出した3D。

ユーザーの指摘「3dのもっと解像度高く。ただ厚くしただけの見た目はやめてほしい」への案。
採用済みのアイコン (各武器の案A、docs/roughs/weapons/<item>/a/icon.png) を元にする。

彫り方:
- 輪郭: 16x16 の各部位の形を、なめらかに拡大して細かい輪郭にする (斜めの刃が階段にならない)。
- 刃: 縁へ向かって薄くなるレンズ形の断面。中央に稜線 (案Bは樋 = 溝)。縁には研いだ明るい帯。
- 鍔: 角を面取りした厚い板。案Bは鋲と彫り込み、案Cは反りと段。
- 柄: 丸い断面 (円柱)。巻き紐の段。
- 柄頭・宝石: 半球に近い盛り上がり。宝石は光る。

3案 = 同じデザインの作り込みの方向:
  A 忠実: 4倍。形はアイコンどおり、断面だけを彫る。
  B 装飾: 4倍。刃の樋、鍔の鋲と彫り、柄の巻きを強く。
  C 精細: 6倍。断面を深く、稜線と面取りを細かく、宝石を多面体に。

使い方: python tools/rough/weapons_v2.py
出力: docs/roughs/v2/weapons/<item>/<a|b|c>/voxels.npz、docs/roughs/v2/weapons_<item>.png
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, str(Path(__file__).parent))
from voxel import icon_big, load_png, render, sheet, voxel_faces  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs/roughs/weapons"
OUT = ROOT / "docs/roughs/v2"

BLADE, GUARD, GRIP, POMMEL, GEM = 1, 2, 3, 4, 5


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# 部位を色で見分ける (weapons2.py の案A の配色)
REGION_COLORS = {
    "whalebone_greatsword": {GUARD: ["#8a6a4a", "#5a4230"], GRIP: ["#6b4a32", "#43301f"], POMMEL: ["#d9c9a0"], GEM: ["#5fe8ff"]},
    "captain_pike": {GRIP: ["#d8cba8", "#8f7f5e"], GEM: ["#5fe8ff"]},
    "waterway_fang": {GUARD: ["#2c4a55", "#1a2d35"], GRIP: ["#35505a", "#203540"]},
}


def regions(item, icon):
    a = icon[..., 3] > 0
    reg = np.where(a, BLADE, 0)
    table = {hexc(c): r for r, cs in REGION_COLORS[item].items() for c in cs}
    for y in range(16):
        for x in range(16):
            if not a[y, x]:
                continue
            col = tuple(int(v) for v in icon[y, x, :3])
            if col in table:
                reg[y, x] = table[col]
            u = x - y
            # 柄頭・石突きは位置で決める (刃と同じ色を使っているため)
            if item == "whalebone_greatsword" and u <= -10:
                reg[y, x] = POMMEL
            if item == "captain_pike" and u <= -16:
                reg[y, x] = POMMEL
            if item == "waterway_fang" and u <= -9:
                reg[y, x] = POMMEL
    return reg


def upsample_regions(reg, S):
    """部位ごとの 0/1 の面を、ぼかしてから拡大し、なめらかな細かい輪郭にする。"""
    n = 16 * S
    best = np.zeros((n, n))
    out = np.zeros((n, n), int)
    for r in (BLADE, GUARD, GRIP, POMMEL, GEM):
        m = (reg == r).astype(np.float32)
        if not m.any():
            continue
        if r == GRIP:
            # 柄は画素の段をなぞらず、主軸に沿ったまっすぐな丸棒 (カプセル) にする
            yy, xx = np.nonzero(m)
            pts = np.stack([xx + 0.5, yy + 0.5], 1) * S
            c = pts.mean(0)
            _, _, vt = np.linalg.svd(pts - c)
            ax = vt[0]
            t = (pts - c) @ ax
            p0, p1 = c + ax * (t.min() - S * 0.3), c + ax * (t.max() + S * 0.3)
            width = m.sum() / max(1.0, (t.max() - t.min()) / S + 1)  # 1行あたりの画素数 ≈ 太さ
            rad = max(0.6, min(width * 0.4, 1.0)) * S
            gy, gx = np.mgrid[0:n, 0:n] + 0.5
            q = np.stack([gx, gy], -1) - p0
            seg = p1 - p0
            h = np.clip((q @ seg) / (seg @ seg), 0, 1)
            dist = np.linalg.norm(q - h[..., None] * seg, axis=-1)
            big = np.clip(1.0 - (dist - rad) / S, 0, 1).astype(np.float32) * (dist <= rad + 0.5)
        else:
            big = np.array(Image.fromarray(m, "F").resize((n, n), Image.BILINEAR))
            big = ndimage.gaussian_filter(big, S * 0.25)
        take = big > np.maximum(best, 0.42)
        out[take] = r
        best = np.maximum(best, big)
    return out


def nearest_colour(icon, reg, hi, S):
    """細かい格子の各点に、同じ部位で最も近いアイコンの画素の色を割り当てる。"""
    n = hi.shape[0]
    col = np.zeros((n, n, 3), float)
    for r in np.unique(hi):
        if r == 0:
            continue
        src = reg == r
        # 部位 r の画素だけを残し、ほかは最も近い r の画素の色で埋める
        idx = ndimage.distance_transform_edt(~src, return_distances=False, return_indices=True)
        fill = icon[idx[0], idx[1], :3].astype(float)
        yy, xx = np.nonzero(hi == r)
        col[yy, xx] = fill[np.minimum(yy // S, 15), np.minimum(xx // S, 15)]
    return col


def smooth_colours(col, hi, icon, reg, S):
    """部位の中で色をぼかして画素の段のジグザグを消し、その部位のアイコンの色に寄せ直す。"""
    out = col.copy()
    for r in np.unique(hi):
        if r == 0:
            continue
        m = (hi == r).astype(float)
        num = np.stack([ndimage.gaussian_filter(col[..., i] * m, S * 0.9) for i in range(3)], -1)
        den = ndimage.gaussian_filter(m, S * 0.9)[..., None]
        blur = num / np.maximum(den, 1e-6)
        pal = np.unique(icon[reg == r][:, :3], axis=0).astype(float)
        yy, xx = np.nonzero(hi == r)
        d = ((blur[yy, xx][:, None, :] - pal[None]) ** 2).sum(-1)
        out[yy, xx] = pal[d.argmin(1)]
    return out


def build(item, opt):
    icon = load_png(SRC / item / "a" / "icon.png")
    S = 6 if opt == "c" else 4
    reg = regions(item, icon)
    hi = upsample_regions(reg, S)
    col = smooth_colours(nearest_colour(icon, reg, hi, S), hi, icon, reg, S)
    # 斜めの武器を縦にまっすぐ立てて彫る (斜めのまま立方体で作ると縁が必ず階段になるため)。
    # ゲームでは要素を z 軸まわりに 45° 回して、アイコンと同じ斜めの向きに戻す
    hi = ndimage.rotate(hi, 45, order=0, reshape=True)
    col = np.stack([ndimage.rotate(col[..., i], 45, order=0, reshape=True) for i in range(3)], -1)
    ys, xs = np.nonzero(hi)
    hi = hi[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    col = col[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    side = max(hi.shape)
    pad_y, pad_x = side - hi.shape[0], side - hi.shape[1]
    hi = np.pad(hi, ((pad_y // 2, pad_y - pad_y // 2), (pad_x // 2, pad_x - pad_x // 2)))
    col = np.pad(col, ((pad_y // 2, pad_y - pad_y // 2), (pad_x // 2, pad_x - pad_x // 2), (0, 0)))
    n = hi.shape[0]
    # 柄は縦の軸に沿ったまっすぐな棒に整える (回したときのぎざぎざを消す)
    g = hi == GRIP
    if g.any():
        ys, xs = np.nonzero(g)
        cx = int(round(np.median(xs)))
        w = max(2, int(round(np.median([np.sum(g[y]) for y in np.unique(ys)]))))
        colg = np.median(col[g], axis=0)
        hi[g] = 0
        x0 = cx - w // 2
        hi[ys.min():ys.max() + 1, x0:x0 + w][hi[ys.min():ys.max() + 1, x0:x0 + w] == 0] = GRIP
        col[ys.min():ys.max() + 1, x0:x0 + w] = np.where((hi[ys.min():ys.max() + 1, x0:x0 + w] == GRIP)[..., None],
                                                        colg, col[ys.min():ys.max() + 1, x0:x0 + w])
    solid = hi > 0
    # 縁からの距離 (細かい格子の単位)
    dist = ndimage.distance_transform_edt(solid)
    half = np.zeros((n, n))
    glow = np.zeros((n, n), bool)
    k = S / 4.0  # 4倍のときの寸法を基準にする

    # 刃: 縁へ薄いレンズ形。中央の稜線を高く
    bl = hi == BLADE
    blade_d = ndimage.distance_transform_edt(bl)
    maxd = max(blade_d[bl].max(), 1) if bl.any() else 1
    rel = blade_d / maxd  # 0=縁, 1=中央
    if opt == "c":
        half[bl] = (0.6 + 2.6 * rel[bl] ** 0.8) * k
    else:
        half[bl] = (0.6 + 2.0 * rel[bl]) * k
    if opt == "b":
        # 樋 (溝): 中央より少し外側を一段へこませる
        groove = bl & (rel > 0.55) & (rel < 0.8)
        half[groove] -= 0.8 * k
    # 研いだ縁の明るい帯と、稜線の明るい筋
    edge_band = bl & (blade_d <= 1.5 * k)
    col[edge_band] = np.minimum(col[edge_band] * 1.18 + 12, 255)
    ridge = bl & (rel > 0.9)
    col[ridge] = np.minimum(col[ridge] * 1.12 + 10, 255)

    # 鍔: 面取りした厚い板
    gd = hi == GUARD
    gdist = ndimage.distance_transform_edt(gd)
    half[gd] = np.minimum(1.5 + gdist[gd] * 0.9, 4.0 if opt != "c" else 5.0) * k
    if opt == "b" and gd.any():
        # 鋲: 鍔の芯に沿って一定間隔で盛り上げる
        core = gd & (gdist >= gdist[gd].max() - 0.5)
        yy, xx = np.nonzero(core)
        for y, x in zip(yy, xx):
            if (x + y) % (3 * S) < S // 2 + 1:
                half[y, x] += 1.0 * k
                col[y, x] = np.minimum(col[y, x] * 1.35 + 20, 255)

    # 柄: 丸い断面。巻き紐の段
    gp = hi == GRIP
    pd = ndimage.distance_transform_edt(gp)
    r = max(pd[gp].max(), 1) if gp.any() else 1
    half[gp] = np.sqrt(np.maximum(0, r ** 2 - (r - pd[gp]) ** 2)) * 0.95
    wrap_period = 2 * S if opt != "b" else int(1.5 * S)
    yy, xx = np.nonzero(gp)
    wrap = (yy // max(1, wrap_period // 2)) % 2 == 0
    if item != "captain_pike":
        half[yy[wrap], xx[wrap]] += 0.5 * k
    col[yy[~wrap], xx[~wrap]] *= 0.82

    # 柄頭・宝石: 半球
    for rr, extra in ((POMMEL, 0.0), (GEM, 0.8)):
        m = hi == rr
        if not m.any():
            continue
        d = ndimage.distance_transform_edt(m)
        rad = d[m].max()
        half[m] = (np.sqrt(np.maximum(0, rad ** 2 - (rad - d[m]) ** 2)) + 1.0 * k + extra * k)
        if rr == GEM:
            glow |= m
            if opt == "c":
                # 多面体: 面ごとに明暗を変える
                yy, xx = np.nonzero(m)
                cy, cx = yy.mean(), xx.mean()
                ang = np.arctan2(yy - cy, xx - cx)
                facet = ((ang + np.pi) / (np.pi / 3)).astype(int) % 2
                col[yy[facet == 1], xx[facet == 1]] *= 0.8

    half = np.where(solid, np.maximum(np.round(half), 1), 0).astype(int)
    hmax = int(half.max())
    Z = 2 * hmax
    grid = np.zeros((n, n, Z, 4), np.uint8)
    gl = np.zeros((n, n, Z), bool)
    for y in range(n):
        for x in range(n):
            h = half[y, x]
            if h <= 0:
                continue
            X, Yv = x, n - 1 - y
            z0, z1 = hmax - h, hmax + h
            c = np.clip(col[y, x], 0, 255).astype(np.uint8)
            grid[X, Yv, z0:z1, :3] = c
            grid[X, Yv, z0:z1, 3] = 255
            # 断面の上下の面を、外へ行くほど少し暗く (彫りの陰)
            if glow[y, x]:
                gl[X, Yv, z0:z1] = True
    return icon, grid, gl, S


def main():
    names = {"whalebone_greatsword": "鯨骨の大剣", "captain_pike": "番兵長の鉾", "waterway_fang": "水路の顎牙"}
    labels = {"a": "A 忠実 (4倍)", "b": "B 装飾 (4倍)", "c": "C 精細 (6倍)"}
    for item, title in names.items():
        cols = []
        for opt in "abc":
            icon, grid, gl, S = build(item, opt)
            folder = OUT / "weapons" / item / opt
            folder.mkdir(parents=True, exist_ok=True)
            np.savez_compressed(folder / "voxels.npz", grid=grid, glow=gl, scale=S)
            faces = voxel_faces(grid, gl)
            v1 = render(faces, yaw=30, pitch=22, size=(520, 520), margin=0.04, outline_jump=2.5 * S / 4)
            v2 = render(faces, yaw=-60, pitch=12, size=(520, 520), margin=0.04, outline_jump=2.5 * S / 4)
            cols.append((labels[opt], [icon_big(icon, 12), v1, v2]))
            print(item, opt, "voxels", int((grid[..., 3] > 0).sum()), "faces", len(faces))
        sheet(f"{title} 第2回 (案A の高解像度化)", cols,
              "上: 採用した 16x16 アイコン (変えない) / 中: 斜め / 下: 反対側から。3D だけを彫り出している").save(OUT / f"weapons_{item}.png")


if __name__ == "__main__":
    main()
