# -*- coding: utf-8 -*-
"""持ち物の欄の絵 (textures/item/<id>.png、32x32) を、手や地面で見える模型 (lowpoly/items/<id>/<段>.json と絵) から描き直す。
「3D の模型が正しい、欄の絵を模型に合わせる」(docs/design/15 の「点の模型」・memory の model quality bar)。

メッシュを正射影で 4 倍の大きさに描き (奥行きの比べ・絵の色・点の模型と同じ 4 段の陰)、32x32 へ縮めて (いちばん多い色)、
上と左の縁を明るく・下と右の縁を暗く (光る画素は除く) して、1 画素の暗い縁取りを付ける。
向きは少し斜め上からの正面が基本で、細い・薄い品は欄をよく埋める向き (斜め 45 度・上から など) を選ぶ (VIEWS)。

  python -I tools/gen/splat/icon_from_model.py <id> ...       # 書き出し
  python -I tools/gen/splat/icon_from_model.py --list          # MISMATCH の全部
  --dry で docs/review/icon_vs_model/redo_*.png に前後の比べだけ書く
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import splat_lib as S  # noqa: E402

ASSETS = S.ROOT / "src/main/resources/assets/skyabyss"
TIERS = ("legend", "rare", "common")
# 2026-11-16 の見直し (docs/review/icon_vs_model) で、欄の絵と模型が違っていた品
MISMATCH = """abyss_bell_staff cinder_lamp ember_horn ember_lantern ember_mantle ice_bear_fang kindling_charm mine_forge_ember moontide_pearl
moss_talisman pit_jaw_pendant resonance_charm stone_whale_plating storm_anklet storm_charm storm_crystal_shard storm_eye_feather tide_chime_charm
temple_coral_bell tide_spiral_shell wild_ash_hourglass_charm wild_ash_talisman wild_crab_backplate wild_guard_badge wild_hangman_knot wild_herb_vial
wild_mimic_heart_charm wild_plumb_line_ring wild_salttear_earring wild_signal_fire wyrm_ember_scale wild_float_netsuke wild_emberbug_amber""".split()
LEVELS = np.array([0.62, 0.78, 0.92, 1.05])
BIG = 128
OUT = 32


def mesh_of(iid):
    for t in TIERS:
        p = ASSETS / "lowpoly/items" / iid / (t + ".json")
        if p.exists():
            return json.loads(p.read_text(encoding="utf-8")), t
    return None, None


def render(iid, yaw=200.0, pitch=22.0, roll=0.0):
    d, tier = mesh_of(iid)
    tex_name = d.get("texture", "").split(":")[-1]
    tex_name = tex_name[:-4] if tex_name.endswith(".png") else tex_name
    tex = np.array(Image.open(ASSETS / "textures/lowpoly" / (tex_name + ".png")).convert("RGBA"))
    gp = ASSETS / "textures/lowpoly" / (tex_name + "_glow.png")
    glow = np.array(Image.open(gp).convert("RGBA")) if gp.exists() else None
    th, tw = tex.shape[:2]
    tris, uvs = [], []
    for b in d["bones"].values():
        v = np.array(b["vertices"], float).reshape(-1, 3)
        uv = np.array(b["uvs"], float).reshape(-1, 2)
        t = np.array(b["triangles"], int).reshape(-1, 3)
        if len(t) == 0:
            continue
        cs = b.get("cap_vertex_start")
        if cs is not None:
            t = t[(t < cs).all(1)]
        if (uv > 1.0001).any():
            uv = uv / np.array(d.get("texture_size", [tw, th]), float)
        tris.append(v[t])
        uvs.append(uv[t])
    Tr = np.concatenate(tris)
    Uv = np.concatenate(uvs)
    a, b_ = math.radians(yaw), math.radians(pitch)
    R1 = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    R2 = np.array([[1, 0, 0], [0, math.cos(b_), -math.sin(b_)], [0, math.sin(b_), math.cos(b_)]])
    rr = math.radians(roll)
    R3 = np.array([[math.cos(rr), -math.sin(rr), 0], [math.sin(rr), math.cos(rr), 0], [0, 0, 1]])
    Q = Tr.reshape(-1, 3) @ R1.T @ R2.T @ R3.T
    lo, hi = Q.min(0), Q.max(0)
    span = (hi - lo)[:2].max()
    pad = 2.0 / OUT
    s = BIG * (1 - 2 * pad) / span
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    X = (Q[:, 0] - cx) * s + BIG / 2
    Y = -(Q[:, 1] - cy) * s + BIG / 2
    Z = Q[:, 2]
    P2 = np.stack([X, Y, Z], 1).reshape(-1, 3, 3)
    # 面の法線 (陰用。視線の空間)
    N = np.cross(Tr[:, 1] - Tr[:, 0], Tr[:, 2] - Tr[:, 0]) @ R1.T @ R2.T @ R3.T
    N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)
    img = np.zeros((BIG, BIG, 4))
    zb = np.full((BIG, BIG), -1e9)
    key = np.array([-0.4, 0.75, 0.5])
    key /= np.linalg.norm(key)
    for i in range(len(P2)):
        p = P2[i]
        x0, x1 = int(max(0, math.floor(p[:, 0].min()))), int(min(BIG - 1, math.ceil(p[:, 0].max())))
        y0, y1 = int(max(0, math.floor(p[:, 1].min()))), int(min(BIG - 1, math.ceil(p[:, 1].max())))
        if x1 < x0 or y1 < y0:
            continue
        xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        (ax, ay, az), (bx, by, bz), (qx, qy, qz) = p
        den = (by - qy) * (ax - qx) + (qx - bx) * (ay - qy)
        if abs(den) < 1e-9:
            continue
        w0 = ((by - qy) * (xs - qx) + (qx - bx) * (ys - qy)) / den
        w1 = ((qy - ay) * (xs - qx) + (ax - qx) * (ys - qy)) / den
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            continue
        z = w0 * az + w1 * bz + w2 * qz
        # 中心へ少し寄せた uv (絵の縁の黒を拾わない)
        c = [w * 0.88 + 0.04 for w in (w0, w1, w2)]
        u = c[0] * Uv[i, 0, 0] + c[1] * Uv[i, 1, 0] + c[2] * Uv[i, 2, 0]
        v = c[0] * Uv[i, 0, 1] + c[1] * Uv[i, 1, 1] + c[2] * Uv[i, 2, 1]
        tu = np.clip((u * tw).astype(int), 0, tw - 1)
        tv = np.clip((v * th).astype(int), 0, th - 1)
        col = tex[tv, tu]
        m &= col[..., 3] >= 128
        yy, xx = np.nonzero(m)
        gy, gx = yy + y0, xx + x0
        closer = z[yy, xx] > zb[gy, gx]
        if not closer.any():
            continue
        yy, xx, gy, gx = yy[closer], xx[closer], gy[closer], gx[closer]
        zb[gy, gx] = z[yy, xx]
        n = N[i]
        lam = max(0.0, float(n @ key))
        L = 0.5 + 0.35 * lam + 0.15 * (0.5 + 0.5 * n[1])
        lit = np.zeros(len(yy), bool)
        if glow is not None:
            gh, gw = glow.shape[:2]
            gg = glow[np.clip((v[yy, xx] * gh).astype(int), 0, gh - 1), np.clip((u[yy, xx] * gw).astype(int), 0, gw - 1)]
            lit = (gg[:, 3] > 16) & (gg[:, :3].sum(1) > 0)
        lv = LEVELS[np.clip(np.rint((L - 0.5) / 0.5 * 3), 0, 3).astype(int)]
        rgb = col[yy, xx, :3].astype(float)
        rgb = np.where(lit[:, None], rgb, np.clip(rgb * lv, 0, 255))
        img[gy, gx, :3] = rgb
        img[gy, gx, 3] = np.where(lit, 254, 255)
    return img, tier, glowmask_of(img)


def glowmask_of(img):
    return None


def downsample(img):
    """4x4 の塊ごとに、不透明が半分以上なら、いちばん多い色 (近い色をまとめて数える) を取る"""
    k = BIG // OUT
    out = np.zeros((OUT, OUT, 4), np.uint8)
    for y in range(OUT):
        for x in range(OUT):
            blk = img[y * k:(y + 1) * k, x * k:(x + 1) * k].reshape(-1, 4)
            op = blk[blk[:, 3] > 0]
            if len(op) * 2 < len(blk):
                continue
            q = (op[:, :3] // 24).astype(int)
            keys, inv, cnt = np.unique(q[:, 0] * 10000 + q[:, 1] * 100 + q[:, 2], return_inverse=True, return_counts=True)
            sel = inv == np.argmax(cnt)
            out[y, x, :3] = np.clip(op[sel, :3].mean(0), 0, 255).astype(np.uint8)
            out[y, x, 3] = 254 if (op[sel, 3] == 254).mean() > 0.5 else 255
    return out


# 試す向き (yaw, pitch, roll)。最初が基本 (少し斜め上からの正面)。細い・薄い品は、欄をよく埋める向きを選ぶ
VIEWS = ((200, 22, 0), (200, 22, -45), (180, 0, -45), (200, 40, -30), (200, 55, 0))


# 品ごとの向きの決め打ち (目で選んだ。2026-11-16): 杖は武器と同じ斜め、細い羽は斜め、離れた肩当ては斜め上から
VIEW_OF = {"abyss_bell_staff": (200, 22, -45), "storm_eye_feather": (110, 15, -45), "ember_mantle": (200, 40, -30), "cinder_lamp": (200, 22, 0)}


def best_icon(iid):
    """向きを変えて描き、欄を埋める画素がいちばん多い向き (基本の向きがその半分以上なら基本) を選ぶ"""
    if iid in VIEW_OF:
        big, tier, _ = render(iid, *VIEW_OF[iid])
        return downsample(big), tier, VIEW_OF[iid]
    tries = []
    for v in VIEWS:
        big, tier, _ = render(iid, *v)
        small = downsample(big)
        tries.append(((small[..., 3] > 0).sum(), v, small, tier))
    base = tries[0][0]
    best = max(tries, key=lambda t: t[0])
    # 上から見下ろすと形が分からなくなる品が多いので、基本の向きが半分も埋めないときだけ替える
    pick = tries[0] if base >= 0.5 * best[0] else best
    return pick[2], pick[3], pick[1]


def shade_edges(icon):
    """上と左が透明な画素を明るく (光の縁)、下と右が透明な画素を暗く (陰の縁)。光る画素は触らない"""
    a = icon[..., 3] > 0
    res = icon.copy()
    H, W = a.shape
    for y in range(H):
        for x in range(W):
            if not a[y, x] or icon[y, x, 3] == 254:
                continue
            up = y == 0 or not a[y - 1, x]
            left = x == 0 or not a[y, x - 1]
            down = y == H - 1 or not a[y + 1, x]
            right = x == W - 1 or not a[y, x + 1]
            c = icon[y, x, :3].astype(float)
            if up or left:
                c = c + (255 - c) * 0.28
            elif down or right:
                c = c * 0.78
            res[y, x, :3] = np.clip(c, 0, 255).astype(np.uint8)
    return res


def outline(icon):
    """透明な所で不透明に接する画素に、隣の色をうんと暗くした縁取り (欄の絵の決まり)"""
    a = icon[..., 3] > 0
    res = icon.copy()
    H, W = a.shape
    for y in range(H):
        for x in range(W):
            if a[y, x]:
                continue
            nb = [(y + dy, x + dx) for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)) if 0 <= y + dy < H and 0 <= x + dx < W and a[y + dy, x + dx]]
            if nb:
                c = np.mean([icon[p][:3] for p in nb], 0)
                res[y, x, :3] = (c * 0.22 + np.array([12, 10, 18]) * 0.78).astype(np.uint8)
                res[y, x, 3] = 255
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()
    ids = MISMATCH if a.list else a.ids
    cmp_tiles = []
    for iid in ids:
        if mesh_of(iid)[0] is None:
            print("skip (no mesh)", iid)
            continue
        small, tier, view = best_icon(iid)
        icon = outline(shade_edges(small))
        icon[..., 3] = np.where(icon[..., 3] > 0, 255, 0)
        path = ASSETS / "textures/item" / (iid + ".png")
        old = Image.open(path).convert("RGBA") if path.exists() else Image.new("RGBA", (OUT, OUT))
        t = Image.new("RGBA", (OUT * 2 + 2, OUT), (40, 38, 48, 255))
        t.alpha_composite(old.resize((OUT, OUT)), (0, 0))
        t.alpha_composite(Image.fromarray(icon), (OUT + 2, 0))
        cmp_tiles.append(t)
        if not a.dry:
            Image.fromarray(icon).save(path)
        print(iid, tier, view)
    if cmp_tiles:
        cols = 6
        rows = (len(cmp_tiles) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * (OUT * 2 + 6), rows * (OUT + 4)), (14, 12, 18, 255))
        for i, t in enumerate(cmp_tiles):
            sheet.alpha_composite(t, ((i % cols) * (OUT * 2 + 6), (i // cols) * (OUT + 4)))
        out = S.ROOT / "docs/review/icon_vs_model" / ("redo_preview.png" if a.dry else "redo_done.png")
        sheet.resize((sheet.width * 5, sheet.height * 5), Image.NEAREST).save(out)
        print(out)


if __name__ == "__main__":
    main()
