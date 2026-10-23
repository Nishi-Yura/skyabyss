"""手に持つ 3D メッシュ (lowpoly/items/<id>/<階級>.json + アトラス) を、品の画面の絵 (32x32) の向きで写す。
武器は品の display と同じく z まわり -45 度 (刃先が右上)、盾と装飾は立てたまま。正射影、+Z から見る。
  python icon_from_mesh.py ref <out_dir>        各品の参考画像 (128px) と自動 32px 案 (<id>_auto.png) を書く
  python icon_from_mesh.py sheet <dir> <out>    3D | 旧アイコン | 新アイコン の比較シート (旧は git の rev から)
品の階級は品そのものの階級 (WeaponItemRenderer / AccessoryMeshes の tierName と同じ)。"""
from __future__ import annotations

import io
import json
import math
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[4]
RES = ROOT / "src/main/resources/assets/skyabyss"

# 品 → 階級 (品のクラスの tier())。ModGear でない品は common
WEAPONS = {
    "whalebone_greatsword": "rare", "captain_pike": "legend", "waterway_fang": "legend",
    "tide_shell_trident": "rare", "coral_harpoon": "legend", "tidecaller_claw": "legend", "coral_shield": "rare",
    "thunder_wing_bow": "rare", "warden_glaive": "legend", "raiju_claw": "legend",
    "ember_war_axe": "rare", "smith_hammer": "legend", "thrall_poker": "legend",
    "abyss_greatsword": "legend", "abyss_ash_scythe": "legend", "abyss_bell_staff": "legend", "abyss_storm_bow": "legend",
    "abyss_lamp_sword": "rare", "abyss_sinking_anchor": "legend", "abyss_pulse_dagger": "legend",
}
ACCESSORIES = {
    "resonance_charm": "rare", "moss_talisman": "rare", "stone_whale_plating": "legend",
    "tide_chime_charm": "rare", "moontide_pearl": "legend", "tide_watching_shell": "common", "tide_pearl": "common",
    "storm_charm": "rare", "storm_anklet": "rare", "storm_eye_feather": "legend", "gale_feather": "common",
    "kindling_charm": "rare", "ember_mantle": "rare", "ember_lantern": "legend", "ember_horn": "common", "cinder_lamp": "common",
    "abyss_pulse_listener": "rare", "abyss_lantern_charm": "rare", "abyss_surveyor_plumb": "legend",
}
import os
# 旧アイコンが 3D の形・色と合っている品 (2026-10-23 の比較で確認)。作り直さない
KEEP = set()
AUTO = os.environ.get("ICON_AUTO") == "1"  # 比較シートの 3 列目を自動案にする (確認用)
DAGGER_GROW = {"waterway_fang", "raiju_claw", "tidecaller_claw", "thrall_poker"}


def load(item, tier):
    d = json.loads((RES / f"lowpoly/items/{item}/{tier}.json").read_text(encoding="utf-8"))
    tex = d["texture"]
    atlas = np.array(Image.open(RES / f"textures/lowpoly/{tex}.png").convert("RGBA"))
    gp = RES / f"textures/lowpoly/{tex}_glow.png"
    glow = np.array(Image.open(gp).convert("RGBA"))[..., 3] > 20 if gp.exists() else np.zeros(atlas.shape[:2], bool)
    V, U, T = [], [], []
    for b in d["bones"].values():
        o = len(V)
        V += b["vertices"]
        U += b["uvs"]
        T += [[o + i for i in t] for t in b["triangles"]]
    return np.array(V, float), np.array(U, float), np.array(T, int), atlas, glow


def place(item, V):
    """品の空間 (x 右、y 上、+Z が画面側) へ。"""
    if item in WEAPONS and item != "coral_shield":
        # 弓は画面の上で半回転 (バニラの bow.png と同じく弧が左上、弦が右上から左下)。面の中で回すだけなので左右は入れ替わらない
        a = math.radians(135 if item.endswith("_bow") else -45)
        c, s = math.cos(a), math.sin(a)
        x, y = V[:, 0], V[:, 1]
        V = np.stack([c * x - s * y, s * x + c * y, V[:, 2]], 1)
    elif item in ACCESSORIES:
        V = V * np.array([1, 1, -1])
    return V


def render(V, U, T, atlas, glow, px, ss=4, view_from=+1):
    """正射影。V は品の空間。px x px の RGBA と光る画素の mask を返す (ss 倍で描いて縮める前の大きさ)。"""
    W = px * ss
    P = V.copy()
    if view_from < 0:
        P[:, 0] = -P[:, 0]
        P[:, 2] = -P[:, 2]
    lo, hi = P[:, :2].min(0), P[:, :2].max(0)
    ext = (hi - lo).max()
    sc = (W * 0.94) / ext
    mid = (hi + lo) / 2
    sx = (P[:, 0] - mid[0]) * sc + W / 2
    sy = -(P[:, 1] - mid[1]) * sc + W / 2
    sz = P[:, 2]
    col = np.zeros((W, W, 3))
    cov = np.zeros((W, W), bool)
    gl = np.zeros((W, W), bool)
    zb = np.full((W, W), -1e9)
    H, Wt = atlas.shape[:2]
    key = np.array([-0.45, 0.75, 0.5])
    key /= np.linalg.norm(key)
    for a, b, c in T:
        p0, p1, p2 = P[a], P[b], P[c]
        n = np.cross(p1 - p0, p2 - p0)
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            continue
        n /= ln
        if n[2] < 0:
            n = -n
        mc = 1.0 * max(n[1], 0) ** 2 + 0.5 * max(-n[1], 0) ** 2 + 0.6 * n[0] ** 2 + 0.8 * n[2] ** 2
        sh = 0.55 * mc + 0.5 * (0.3 + 0.7 * max(0.0, float(n @ key)))
        xs, ys = sx[[a, b, c]], sy[[a, b, c]]
        x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
        y0, y1 = int(max(0, math.floor(ys.min()))), int(min(W - 1, math.ceil(ys.max())))
        if x1 < x0 or y1 < y0:
            continue
        den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if abs(den) < 1e-9:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        l0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / den
        l1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / den
        l2 = 1 - l0 - l1
        ins = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
        z = l0 * sz[a] + l1 * sz[b] + l2 * sz[c]
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        upd = ins & (z > sub)
        if not upd.any():
            continue
        u = l0 * U[a, 0] + l1 * U[b, 0] + l2 * U[c, 0]
        v = l0 * U[a, 1] + l1 * U[b, 1] + l2 * U[c, 1]
        ui = np.clip((u * Wt).astype(int), 0, Wt - 1)
        vi = np.clip((v * H).astype(int), 0, H - 1)
        tex = atlas[vi, ui, :3].astype(float)
        g = glow[vi, ui]
        cc = np.where(g[..., None], tex, tex * min(1.15, sh))
        sub[upd] = z[upd]
        col[y0:y1 + 1, x0:x1 + 1][upd] = cc[upd]
        cov[y0:y1 + 1, x0:x1 + 1][upd] = True
        gl[y0:y1 + 1, x0:x1 + 1][upd] = g[upd]
    return col, cov, gl


def side(item):
    """盾は表 (-Z) から、ほかは品の display と同じ +Z から。"""
    return -1 if item == "coral_shield" else +1


def ref_image(item, tier, px=128, view_from=None):
    view_from = side(item) if view_from is None else view_from
    V, U, T, atlas, glow = load(item, tier)
    col, cov, gl = render(place(item, V), U, T, atlas, glow, px, 2, view_from)
    img = np.zeros(col.shape[:2] + (4,), np.uint8)
    img[cov, :3] = np.clip(col[cov], 0, 255).astype(np.uint8)
    img[cov, 3] = 255
    return Image.fromarray(img, "RGBA").resize((px, px), Image.LANCZOS)


def auto_icon(item, tier, view_from=None, gain=1.12, cover=0.42):
    """32px 案: 8 倍で描き、各升の見えている画素の中央値。縁は 1 画素の暗い線、光る升はそのまま明るく。"""
    view_from = side(item) if view_from is None else view_from
    V, U, T, atlas, glow = load(item, tier)
    ss = 8
    col, cov, gl = render(place(item, V), U, T, atlas, glow, 32, ss, view_from)
    out = np.zeros((32, 32, 4), np.uint8)
    solid = np.zeros((32, 32), bool)
    for j in range(32):
        for i in range(32):
            c = cov[j * ss:(j + 1) * ss, i * ss:(i + 1) * ss]
            if c.mean() < cover:
                continue
            px = col[j * ss:(j + 1) * ss, i * ss:(i + 1) * ss][c]
            g = gl[j * ss:(j + 1) * ss, i * ss:(i + 1) * ss][c]
            if g.mean() > 0.35:
                rgb = np.median(px[g], 0)
            else:
                rgb = np.clip(np.median(px, 0) * gain, 0, 255)
            out[j, i, :3] = rgb.astype(np.uint8)
            out[j, i, 3] = 255
            solid[j, i] = True
    # 暗い品 (余燼・深淵) は画面の上で黒くつぶれるので、光らない升の明るさの平均が 78 に届くまで持ち上げる
    gmask = gl.reshape(32, ss, 32, ss).mean((1, 3)) > 0.35
    body = solid & ~gmask
    if body.any():
        lum = (out[body, :3].astype(float) @ np.array([0.3, 0.59, 0.11])).mean()
        if lum < 78:
            lift = min(1.6, 78 / max(lum, 1))
            out[body, :3] = np.clip(out[body, :3].astype(float) * lift + 6, 0, 255).astype(np.uint8)
    _quantize(out, solid, gmask)
    _rim(out, solid)
    # 縁: 外側に接する画素を暗い線へ (形を細らせない = 外へ 1 画素足す)
    near = np.zeros_like(solid)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        near |= np.roll(np.roll(solid, dx, 1), dy, 0)
    edge = ~solid & near
    edge[0, :] = edge[-1, :] = edge[:, 0] = edge[:, -1] = False
    base = out[solid, :3].astype(float)
    dark = (np.percentile(base, 8, axis=0) * 0.45).astype(np.uint8) if len(base) else np.array([16, 16, 22], np.uint8)
    out[edge, :3] = dark
    out[edge, 3] = 255
    return Image.fromarray(out, "RGBA")


def _kmeans(X, k, it=12):
    k = min(k, len(X))
    rng = np.random.default_rng(1)
    C = X[rng.choice(len(X), k, replace=False)].copy()
    for _ in range(it):
        lab = ((X[:, None, :] - C[None]) ** 2).sum(-1).argmin(1)
        for j in range(k):
            m = lab == j
            if m.any():
                C[j] = X[m].mean(0)
    return C, lab


def _quantize(out, solid, glow, k=14, kg=4):
    """色を少数の段へまとめ (画素絵らしく)、周りが全部同じ色の孤立した 1 画素をその色へ寄せる。"""
    rgb = out[..., :3].astype(float)
    idx = np.full((32, 32), -1)
    pal = []
    for mask, kk in ((solid & ~glow, k), (solid & glow, kg)):
        if mask.sum() == 0:
            continue
        C, lab = _kmeans(rgb[mask], kk)
        idx[mask] = lab + len(pal)
        pal += list(C)
    pal = np.array(pal)
    for _ in range(2):
        nxt = idx.copy()
        for j in range(1, 31):
            for i in range(1, 31):
                if idx[j, i] < 0:
                    continue
                nb = [idx[j - 1, i], idx[j + 1, i], idx[j, i - 1], idx[j, i + 1]]
                if min(nb) >= 0 and idx[j, i] not in nb and len(set(nb)) == 1:
                    nxt[j, i] = nb[0]
        idx = nxt
    out[solid, :3] = np.clip(pal[idx[solid]], 0, 255).astype(np.uint8)


def _rim(out, solid):
    """左上の縁を少し明るく、右下の縁を少し暗く (バニラの品の絵の光の向き)。"""
    rgb = out[..., :3].astype(float)
    hi = solid & (~np.roll(solid, 1, 0) | ~np.roll(solid, 1, 1))
    lo = solid & (~np.roll(solid, -1, 0) | ~np.roll(solid, -1, 1)) & ~hi
    rgb[hi] = np.clip(rgb[hi] * 1.18 + 8, 0, 255)
    rgb[lo] = rgb[lo] * 0.82
    out[..., :3] = rgb.astype(np.uint8)


def git_icon(item, rev):
    rel = f"src/main/resources/assets/skyabyss/textures/item/{item}.png"
    r = subprocess.run(["git", "-C", str(ROOT), "show", f"{rev}:{rel}"], capture_output=True)
    if r.returncode != 0:
        return None
    return Image.open(io.BytesIO(r.stdout)).convert("RGBA")


def checker(size):
    bg = Image.new("RGBA", (size, size), (58, 60, 70, 255))
    d = ImageDraw.Draw(bg)
    s = size // 8
    for y in range(8):
        for x in range(8):
            if (x + y) % 2:
                d.rectangle([x * s, y * s, x * s + s - 1, y * s + s - 1], fill=(70, 72, 84, 255))
    return bg


def sheet(ref_dir, out, rev, items=None):
    items = items or list(WEAPONS) + list(ACCESSORIES)
    T = 128
    cols = 2
    rows = math.ceil(len(items) / cols)
    font = None
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if Path(f).exists():
            font = ImageFont.truetype(f, 16)
            break
    cw = 3 * (T + 8) + 220
    W = Image.new("RGB", (cols * cw + 16, rows * (T + 12) + 40), (30, 31, 38))
    d = ImageDraw.Draw(W)
    d.text((10, 8), "3D (手の模型) | 旧アイコン | 新アイコン", fill=(230, 230, 230), font=font)
    for k, it in enumerate(items):
        r, c = divmod(k, cols)
        x0, y0 = 8 + c * cw, 36 + r * (T + 12)
        tier = WEAPONS.get(it) or ACCESSORIES.get(it)
        imgs = [Image.open(Path(ref_dir) / f"{it}_ref.png").convert("RGBA"), git_icon(it, rev),
                Image.open((Path(ref_dir) / f"{it}_auto.png") if AUTO else (RES / f"textures/item/{it}.png")).convert("RGBA")]
        for j, im in enumerate(imgs):
            bg = checker(T)
            if im is not None:
                im = im.resize((T, T), Image.NEAREST if im.size[0] <= 32 else Image.LANCZOS)
                bg.alpha_composite(im)
            W.paste(bg.convert("RGB"), (x0 + j * (T + 8), y0))
        d.text((x0 + 3 * (T + 8) + 4, y0 + 8), it, fill=(235, 235, 235), font=font)
        d.text((x0 + 3 * (T + 8) + 4, y0 + 30), tier, fill=(170, 170, 190), font=font)
    W.save(out)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "ref":
        od = Path(sys.argv[2])
        od.mkdir(parents=True, exist_ok=True)
        only = sys.argv[3:] or list(WEAPONS) + list(ACCESSORIES)
        for it in only:
            tier = WEAPONS.get(it) or ACCESSORIES.get(it)
            ref_image(it, tier).save(od / f"{it}_ref.png")
            auto_icon(it, tier).save(od / f"{it}_auto.png")
            print("ok", it)
    elif cmd == "install":
        # 自動案を textures/item/ へ書く (KEEP は旧の手描きが 3D と合っているので残す)
        od = Path(sys.argv[2])
        for it in list(WEAPONS) + list(ACCESSORIES):
            if it in KEEP:
                continue
            Image.open(od / f"{it}_auto.png").save(RES / f"textures/item/{it}.png", optimize=True)
            print("icon", it)
    elif cmd == "sheet":
        sheet(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else "HEAD", sys.argv[5:] or None)
