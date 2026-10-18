"""描画の補助: 今の item モデルの読み込み、平色の面 (プレイヤーの模型) との合成、光る色つきの render。"""
from __future__ import annotations

import copy
import inspect
import json
import math

import numpy as np
from PIL import Image

from wkit import *  # noqa: F401,F403
from wkit import ASSETS, _mk, block, V, rot_x, rot_y, rot_z, transform
import lpkit

_src = inspect.getsource(lpkit.render).replace("def render(", "def render_c(gcolor, ", 1)
_src = _src.replace("np.array([140, 230, 255], float)", "np.array(gcolor, float)")
_ns = dict(lpkit.__dict__)
exec(_src, _ns)
render_c = _ns["render_c"]

GLOW_RGB = dict(whale=(120, 215, 245), tide=(255, 170, 205), thunder=(190, 220, 255), ember=(255, 140, 40))


def compose(parts, flat=()):
    """parts: [(faces, atlas, glow)] (uv は各 atlas の座標)。flat: [(faces, rgb)] は平色。
    1 枚のアトラスに並べ、面の uv を付け替えて (faces, atlas, glow) を返す。atlas は (S,S,4)。"""
    S = max(a.shape[0] for _, a, _ in parts)
    colors = {}
    for fs, rgb in flat:
        colors.setdefault(tuple(rgb), None)
    band = 4
    S2 = S + band
    atlas = np.zeros((S2, S2, 4), np.uint8)
    glow = np.zeros((S2, S2), bool)
    # 最初の部品はそのまま左上へ。2 つ目以降は縦に積むので大きさを取り直す
    ys = 0
    offs = []
    for fs, a, g in parts:
        offs.append(ys)
        ys += a.shape[0]
    S2 = max(S, ys) + band + 4
    S2 = max(S2, 64)
    atlas = np.zeros((S2, S2, 4), np.uint8)
    glow = np.zeros((S2, S2), bool)
    outf = []
    for (fs, a, g), oy in zip(parts, offs):
        n = a.shape[0]
        atlas[oy:oy + n, :n] = a
        glow[oy:oy + n, :n] = g
        for f in fs:
            h = copy.copy(f)
            h.uv = f.uv + np.array([0.0, oy])
            outf.append(h)
    # 平色の面: 一番下の帯に 1 色 1 テクセル
    cy, cx = S2 - 2, 0
    for k, (fs, rgb) in enumerate(flat):
        key = tuple(rgb)
        if colors[key] is None:
            colors[key] = (cx, cy)
            atlas[cy, cx] = (*rgb, 255)
            cx += 1
            if cx >= S2:
                cx, cy = 0, cy - 1
        tx, ty = colors[key]
        for f in fs:
            h = copy.copy(f)
            h.uv = np.tile(np.array([[tx + 0.5, ty + 0.5]]), (len(f.verts), 1))
            outf.append(h)
    return outf, atlas, glow


# ------------------------------------------------------------------ 今の item モデル

def load_current(name, pal_name=None):
    """今の item モデル (回転 -45 度を掛ける前の直立の形) を、色つきの箱の面にして返す。(faces_with_colors)"""
    d = json.loads((ASSETS / f"models/item/{name}.json").read_text(encoding="utf-8"))
    b = d["base"]
    tex = b["textures"]["pal"].split(":")[1]
    pal = np.array(Image.open(ASSETS / f"textures/{tex}.png").convert("RGBA"))
    ph, pw = pal.shape[:2]
    out = {}
    for e in b["elements"]:
        x0, y0, z0 = e["from"]
        x1, y1, z1 = e["to"]
        x0, x1 = x0 - 8, x1 - 8
        z0, z1 = z0 - 8, z1 - 8
        c = V((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        corners = {
            "down": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            "north": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
            "south": [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
            "west": [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
            "east": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
        }
        for fname, fd in e["faces"].items():
            u = fd["uv"]
            px = int(np.clip((u[0] + u[2]) / 2 / 16 * pw, 0, pw - 1))
            py = int(np.clip((u[1] + u[3]) / 2 / 16 * ph, 0, ph - 1))
            rgba = pal[py, px]
            if rgba[3] < 10:
                continue
            f = _mk([V(*p) for p in corners[fname]], "w", "cur", c)
            if f is not None:
                out.setdefault(tuple(int(v) for v in rgba[:3]), []).append(f)
    return out


def current_scene(name):
    groups = load_current(name)
    flat = [(fs, rgb) for rgb, fs in groups.items()]
    allf = [f for fs, _ in flat for f in fs]
    dummy = (allf[:0], np.zeros((32, 32, 4), np.uint8), np.zeros((32, 32), bool))
    faces, atlas, glow = compose([dummy], flat)
    return faces, atlas, glow


def view(faces, atlas, glow, titan, yaw, pitch, size, center, scale, ss=2, outline=True):
    return render_c(GLOW_RGB.get(titan, (140, 230, 255)), faces, atlas, glow, yaw, pitch, size, center, scale,
                    ss=ss, outline=outline)
