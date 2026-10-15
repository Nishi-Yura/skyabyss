"""ラフ (見た目の試作) を描くための共通ツール。

使い方 (他のスクリプトから import する):
    import sys; sys.path.insert(0, "tools/rough")
    from voxel import *

できること:
- extrude(icon16, depth): 16x16 のアイコンを、画素ごとの厚みを持つボクセルにする (武器の3D化)
- load_geo(geo_json, texture): GeckoLib の geo.json とテクスチャを読み、描ける面の一覧にする (Mob・防具)
- render(faces, yaw, pitch, size): 面の一覧を、Minecraft と同じ面ごとの陰影で描く (正射影)
- check_overlaps(geo_json): 立方体どうしの重なり・埋まりを調べる (重なりは不可という決まり)
- sheet(...): 複数の絵を1枚の比較シートにまとめる (日本語の見出し付き)

座標: 1単位 = テクスチャの1画素 = 1/16 ブロック。y が上。
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/skyabyss/textures"

# Minecraft の面ごとの明るさ (上 1.0、北南 0.8、東西 0.6、下 0.5)
FACE_SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


@dataclass
class Face:
    origin: np.ndarray    # 面の一隅 (3)
    eu: np.ndarray        # 面の横方向の辺 (3)
    ev: np.ndarray        # 面の縦方向の辺 (3)
    tex: np.ndarray       # (H, W, 4) uint8。1x1 なら単色
    glow: np.ndarray | None  # (H, W) bool。光る画素
    shade: float          # 面の向きによる明るさ


# ---------------------------------------------------------------- ボクセル

def load_png(path) -> np.ndarray:
    return np.array(Image.open(path).convert("RGBA"))


def extrude(icon: np.ndarray, depth: np.ndarray | int | None = None, glow: np.ndarray | None = None) -> np.ndarray:
    """アイコン (H, W, 4) を、ボクセルの色の格子 (X, Y, Z, 4) にする。

    depth: 画素ごとの厚み (H, W) か、一定の厚み。省略すると、縁からの距離で厚みを決める
           (縁 1、内側 2、芯 3) — 参考画像のような段のある立体になる。
    厚みは z の中央に対して前後に振り分ける (前後対称)。
    """
    h, w = icon.shape[:2]
    alpha = icon[..., 3] > 0
    if depth is None:
        depth = bevel_depth(alpha)
    elif np.isscalar(depth):
        depth = np.where(alpha, int(depth), 0)
    dmax = int(depth.max()) if depth.size else 1
    zsize = dmax * 2
    grid = np.zeros((w, h, zsize, 4), np.uint8)
    gl = np.zeros((w, h, zsize), bool)
    for py in range(h):
        for px in range(w):
            d = int(depth[py, px])
            if not alpha[py, px] or d <= 0:
                continue
            x, y = px, h - 1 - py
            z0, z1 = dmax - d, dmax + d
            grid[x, y, z0:z1] = icon[py, px]
            if glow is not None and glow[py, px]:
                gl[x, y, z0:z1] = True
    return grid, gl


def bevel_depth(alpha: np.ndarray, levels=(1, 2, 3)) -> np.ndarray:
    """縁からの距離 (4近傍) で厚みを決める。縁の画素ほど薄い。"""
    h, w = alpha.shape
    dist = np.where(alpha, 99, 0)
    frontier = ~alpha
    d = 0
    cur = frontier.copy()
    out = np.zeros((h, w), int)
    remaining = alpha.copy()
    step = 0
    while remaining.any():
        step += 1
        pad = np.pad(cur, 1, constant_values=True)
        near = pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]
        ring = remaining & near
        out[ring] = levels[min(step - 1, len(levels) - 1)]
        cur = cur | ring
        remaining &= ~ring
    return out


def voxel_faces(grid: np.ndarray, glow: np.ndarray | None = None, offset=(0, 0, 0)) -> list[Face]:
    """ボクセルの格子から、外に見えている面だけを取り出す (内側の面は描かない)。"""
    X, Y, Z = grid.shape[:3]
    solid = grid[..., 3] > 0
    pad = np.pad(solid, 1)
    ox, oy, oz = offset
    faces = []
    dirs = {
        "east": ((1, 0, 0), lambda x, y, z: ((x + 1, y, z + 1), (0, 0, -1), (0, 1, 0))),
        "west": ((-1, 0, 0), lambda x, y, z: ((x, y, z), (0, 0, 1), (0, 1, 0))),
        "up": ((0, 1, 0), lambda x, y, z: ((x, y + 1, z), (1, 0, 0), (0, 0, 1))),
        "down": ((0, -1, 0), lambda x, y, z: ((x, y, z + 1), (1, 0, 0), (0, 0, -1))),
        "south": ((0, 0, 1), lambda x, y, z: ((x, y, z + 1), (1, 0, 0), (0, 1, 0))),
        "north": ((0, 0, -1), lambda x, y, z: ((x + 1, y, z), (-1, 0, 0), (0, 1, 0))),
    }
    xs, ys, zs = np.nonzero(solid)
    for name, ((dx, dy, dz), geo) in dirs.items():
        nb = pad[xs + 1 + dx, ys + 1 + dy, zs + 1 + dz]
        for x, y, z in zip(xs[~nb], ys[~nb], zs[~nb]):
            o, u, v = geo(x, y, z)
            c = grid[x, y, z].reshape(1, 1, 4)
            g = None if glow is None else np.array([[glow[x, y, z]]])
            faces.append(Face(np.array(o, float) + offset, np.array(u, float), np.array(v, float), c, g, FACE_SHADE[name]))
    return faces


# ---------------------------------------------------------------- GeckoLib geo

def _rot(rx, ry, rz):
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def _box_uv(u, v, w, h, d):
    # Bedrock の箱UV: 各面の (u, v, 幅, 高さ)
    return {
        "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
        "west": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h),
    }


def load_geo(geo, texture, glowmask=None, pose: dict | None = None) -> list[Face]:
    """geo.json (パスか dict) とテクスチャ (パスか配列) から面の一覧を作る。

    pose: {骨の名前: (rx, ry, rz)} で骨を追加で回す (ポーズ付きのラフ用)。
    """
    if not isinstance(geo, dict):
        geo = json.loads(Path(geo).read_text(encoding="utf-8"))
    g = geo["minecraft:geometry"][0]
    tw = g["description"]["texture_width"]
    th = g["description"]["texture_height"]
    tex = load_png(texture) if not isinstance(texture, np.ndarray) else texture
    sx, sy = tex.shape[1] / tw, tex.shape[0] / th
    glow = None
    if glowmask is not None:
        gm = load_png(glowmask) if not isinstance(glowmask, np.ndarray) else glowmask
        glow = gm[..., 3] > 0
    bones = {b["name"]: b for b in g["bones"]}
    pose = pose or {}
    cache = {}

    def world(name):
        # 骨の変換 (回転行列, 平行移動) を親から積む
        if name in cache:
            return cache[name]
        b = bones[name]
        piv = np.array(b.get("pivot", [0, 0, 0]), float)
        r = np.array(b.get("rotation", [0, 0, 0]), float) + np.array(pose.get(name, (0, 0, 0)), float)
        R = _rot(*r)
        M = (R, piv - R @ piv)
        if "parent" in b:
            PR, Pt = world(b["parent"])
            M = (PR @ M[0], PR @ M[1] + Pt)
        cache[name] = M
        return M

    faces = []
    for b in g["bones"]:
        R, t = world(b["name"])
        for c in b.get("cubes", []):
            o = np.array(c["origin"], float)
            s = np.array(c["size"], float)
            inf = c.get("inflate", 0)
            o, s = o - inf, s + 2 * inf
            cr = _rot(*c.get("rotation", [0, 0, 0]))
            cp = np.array(c.get("pivot", o), float)
            x0, y0, z0 = o
            x1, y1, z1 = o + s
            corners = {
                "north": ((x1, y0, z0), (-s[0], 0, 0), (0, s[1], 0)),
                "south": ((x0, y0, z1), (s[0], 0, 0), (0, s[1], 0)),
                "east": ((x1, y0, z1), (0, 0, -s[2]), (0, s[1], 0)),
                "west": ((x0, y0, z0), (0, 0, s[2]), (0, s[1], 0)),
                "up": ((x0, y1, z1), (s[0], 0, 0), (0, 0, -s[2])),
                "down": ((x0, y0, z0), (s[0], 0, 0), (0, 0, s[2])),
            }
            uv = c.get("uv", [0, 0])
            if isinstance(uv, list):
                w, h, d = (int(round(v)) for v in c["size"])
                rects = _box_uv(uv[0], uv[1], w, h, d)
            else:
                rects = {k: (*f["uv"], *f["uv_size"]) for k, f in uv.items()}
            for name, (co, eu, ev) in corners.items():
                if name not in rects:
                    continue
                u0, v0, uw, vh = rects[name]
                # 画像の上が v の小さい側。面の ev は上向きなので、縦を反転して切り出す
                a0, a1 = sorted((u0 * sx, (u0 + uw) * sx))
                b0, b1 = sorted((v0 * sy, (v0 + vh) * sy))
                ia0, ia1, ib0, ib1 = int(round(a0)), int(round(a1)), int(round(b0)), int(round(b1))
                patch = tex[ib0:max(ib1, ib0 + 1), ia0:max(ia1, ia0 + 1)]
                gp = None if glow is None else glow[ib0:max(ib1, ib0 + 1), ia0:max(ia1, ia0 + 1)]
                if uw < 0:
                    patch = patch[:, ::-1]
                    gp = None if gp is None else gp[:, ::-1]
                # 画像の上の行が面の上 (render は t=1 の側に上の行を置く)。
                # 下の面だけは、画像の上の行が北 (ev の始点側) になるよう反転する
                if name == "down":
                    patch = patch[::-1]
                    gp = None if gp is None else gp[::-1]
                co = np.array(co, float)
                eu = np.array(eu, float)
                ev = np.array(ev, float)
                # 立方体の回転 → 骨の変換
                co = cr @ (co - cp) + cp
                eu, ev = cr @ eu, cr @ ev
                faces.append(Face(R @ co + t, R @ eu, R @ ev, patch, gp, FACE_SHADE[name]))
    return faces


def check_overlaps(geo, tol=1e-6) -> list[str]:
    """立方体どうしの重なり (体積を持つ交差) を調べる。回転のない立方体どうしだけを正確に比べ、
    回転のある立方体は外接箱で比べて「要確認」として報告する。"""
    if not isinstance(geo, dict):
        geo = json.loads(Path(geo).read_text(encoding="utf-8"))
    g = geo["minecraft:geometry"][0]
    bones = {b["name"]: b for b in g["bones"]}

    def rotated(name):
        b = bones[name]
        if any(abs(a) > tol for a in b.get("rotation", [0, 0, 0])):
            return True
        return "parent" in b and rotated(b["parent"])

    boxes = []
    for b in g["bones"]:
        rb = rotated(b["name"])
        for i, c in enumerate(b.get("cubes", [])):
            o = np.array(c["origin"], float) - c.get("inflate", 0)
            s = np.array(c["size"], float) + 2 * c.get("inflate", 0)
            rc = any(abs(a) > tol for a in c.get("rotation", [0, 0, 0]))
            boxes.append((f"{b['name']}[{i}]", o, o + s, rb or rc, b["name"]))
    problems = []
    for i in range(len(boxes)):
        for j in range(i + 1, len(boxes)):
            na, a0, a1, ra, ba = boxes[i]
            nb, b0, b1, rb, bb = boxes[j]
            if ra or rb:
                continue  # 回転した部品は骨の空間が違うので、ここでは比べない
            ov = np.minimum(a1, b1) - np.maximum(a0, b0)
            if (ov > tol).all():
                problems.append(f"overlap {na} x {nb} vol={ov.prod():.2f}")
    return problems


# ---------------------------------------------------------------- 描画

def render(faces: list[Face], yaw=35.0, pitch=28.0, size=(640, 640), bg="dark", margin=0.08,
           outline=True, bloom=True, ss=2, outline_jump=0.6) -> Image.Image:
    """正射影で描く。yaw は y 軸まわり、pitch は見下ろす角度。
    陰影は Minecraft の面ごとの明るさ。奥行きの段差に細い輪郭線を引き、光る画素はにじませる。"""
    W, H = size[0] * ss, size[1] * ss
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    Ry = np.array([[cy, 0, sy_], [0, 1, 0], [-sy_, 0, cy]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    V = Rx @ Ry
    # 全体の大きさから縮尺を決める
    pts = []
    for f in faces:
        for a in (0, 1):
            for b in (0, 1):
                pts.append(V @ (f.origin + a * f.eu + b * f.ev))
    pts = np.array(pts) if pts else np.zeros((1, 3))
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1], 1e-6)
    scale = min(W, H) * (1 - 2 * margin) / span
    cx_, cy_ = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2

    color = np.zeros((H, W, 3), float)
    alpha = np.zeros((H, W), bool)
    depth = np.full((H, W), -np.inf)
    glowbuf = np.zeros((H, W, 3), float)
    fid = np.full((H, W), -1, int)

    view_dir = V.T @ np.array([0, 0, 1.0])  # カメラへ向かう向き (モデル空間)
    for k, f in enumerate(faces):
        n = np.cross(f.eu, f.ev)
        if np.dot(n, view_dir) <= 1e-9:
            continue  # 裏向きの面
        o = V @ f.origin
        u = V @ f.eu
        v = V @ f.ev

        def scr(p):
            return np.array([(p[0] - cx_) * scale + W / 2, -(p[1] - cy_) * scale + H / 2])
        so = scr(o)
        su = u[:2] * scale * np.array([1, -1])
        sv = v[:2] * scale * np.array([1, -1])
        quad = np.array([so, so + su, so + su + sv, so + sv])
        x0, y0 = np.floor(quad.min(0)).astype(int)
        x1, y1 = np.ceil(quad.max(0)).astype(int)
        x0, y0 = max(x0, 0), max(y0, 0)
        x1, y1 = min(x1, W), min(y1, H)
        if x1 <= x0 or y1 <= y0:
            continue
        M = np.array([su, sv]).T
        det = np.linalg.det(M)
        if abs(det) < 1e-9:
            continue
        Minv = np.linalg.inv(M)
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        rel = np.stack([gx - so[0], gy - so[1]], -1) @ Minv.T
        s, t = rel[..., 0], rel[..., 1]
        inside = (s >= -1e-4) & (s <= 1 + 1e-4) & (t >= -1e-4) & (t <= 1 + 1e-4)
        if not inside.any():
            continue
        th, tw = f.tex.shape[:2]
        iu = np.clip((s * tw).astype(int), 0, tw - 1)
        iv = np.clip(((1 - t) * th).astype(int), 0, th - 1)  # tex の上の行 = 面の上
        texel = f.tex[iv, iu]
        inside &= texel[..., 3] > 0
        z = o[2] + s * u[2] + t * v[2]
        sub_d = depth[y0:y1, x0:x1]
        win = inside & (z > sub_d)
        if not win.any():
            continue
        sub_d[win] = z[win]
        rgb = texel[..., :3].astype(float) * f.shade
        color[y0:y1, x0:x1][win] = rgb[win]
        alpha[y0:y1, x0:x1][win] = True
        fid[y0:y1, x0:x1][win] = k
        g = np.zeros_like(win)
        if f.glow is not None:
            g = f.glow[iv, iu] & win
            color[y0:y1, x0:x1][g] = texel[..., :3][g]
        gb = glowbuf[y0:y1, x0:x1]
        gb[win] = 0
        gb[g] = texel[..., :3][g]

    if outline:
        # 奥行きが大きく変わる所と、物の外周に、暗い線を引く (ボクセルの段が読めるように)
        d = np.where(alpha, depth, -1e9)
        jump = np.zeros_like(alpha)
        thr = outline_jump  # これより大きい奥行きの差に線を引く (モデルの単位)
        for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
            nd = np.roll(np.roll(d, dy, 0), dx, 1)
            na = np.roll(np.roll(alpha, dy, 0), dx, 1)
            jump |= alpha & ((~na) | (nd - d > thr))
        color[jump] *= 0.55

    bgimg = _background(W, H, bg)
    out = bgimg.copy()
    out[alpha] = color[alpha]
    if bloom and glowbuf.any():
        gimg = Image.fromarray(np.clip(glowbuf, 0, 255).astype(np.uint8))
        blur = np.array(gimg.filter(ImageFilter.GaussianBlur(10 * ss))).astype(float)
        out = out + blur * 0.9
    out = np.clip(out, 0, 255).astype(np.uint8)
    img = Image.fromarray(out)
    return img.resize(size, Image.LANCZOS)


def _background(W, H, bg):
    if bg == "dark":
        yy, xx = np.mgrid[0:H, 0:W]
        r = np.hypot((xx - W * 0.5) / W, (yy - H * 0.45) / H)
        c0 = np.array([38, 36, 52.0])
        c1 = np.array([10, 10, 16.0])
        k = np.clip(r * 1.6, 0, 1)[..., None]
        return c0 * (1 - k) + c1 * k
    return np.full((H, W, 3), 200.0)


# ---------------------------------------------------------------- シート

FONT_PATHS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"]


def font(size):
    for p in FONT_PATHS:
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def icon_big(icon: np.ndarray, px=12) -> Image.Image:
    """16x16 のアイコンを、画素が見える大きさに拡大し、市松の背景に置く。"""
    h, w = icon.shape[:2]
    board = np.zeros((h, w, 3), np.uint8)
    yy, xx = np.mgrid[0:h, 0:w]
    board[:] = 70
    board[(yy + xx) % 2 == 0] = 58
    a = icon[..., 3:4] / 255.0
    img = (icon[..., :3] * a + board * (1 - a)).astype(np.uint8)
    return Image.fromarray(img).resize((w * px, h * px), Image.NEAREST)


def sheet(title: str, columns: list[tuple[str, list[Image.Image]]], note: str = "") -> Image.Image:
    """案ごとの列に、絵を縦に並べた比較シート。columns = [(見出し, [絵...]), ...]"""
    pad = 24
    colw = max(max(im.width for im in ims) for _, ims in columns) + pad
    rows = max(len(ims) for _, ims in columns)
    rowh = [max((ims[r].height if r < len(ims) else 0) for _, ims in columns) for r in range(rows)]
    head = 70 + (34 if note else 0)
    W = pad + colw * len(columns)
    H = head + 44 + sum(h + pad for h in rowh) + pad
    out = Image.new("RGB", (W, H), (18, 18, 26))
    d = ImageDraw.Draw(out)
    d.text((pad, 18), title, font=font(30), fill=(235, 235, 245))
    if note:
        d.text((pad, 60), note, font=font(18), fill=(170, 170, 190))
    for ci, (label, ims) in enumerate(columns):
        x = pad + ci * colw
        d.text((x, head), label, font=font(24), fill=(120, 220, 230))
        y = head + 44
        for r, im in enumerate(ims):
            out.paste(im, (x + (colw - pad - im.width) // 2, y))
            y += rowh[r] + pad
    return out
