"""書き出した構造 (sbd.Dump) の図。色はブロックの絵の平均 (バニラは build/moddev/artifacts の jar、mod は src の assets)、無ければ tools/rough/block_colors.json、それも無ければ地図の色。

- top(d): 真上から (いちばん上のブロック。高さで陰をつける)
- section(d, axis, value): 断面 (y=高さの水平の断面、x= / z= の縦の断面)。空気は白、水は青、溶岩は橙
- iso(d): 等角の図 (南東の上から)
- overlay_walk(d, dist): 真上から、歩いて行ける所に色 (高さ)
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw

from sbd import Dump

SKY = (232, 236, 240)
AIR = (255, 255, 255)
WATER = (64, 110, 220)
LAVA = (235, 120, 30)
NONE = (150, 150, 150)


_BLOCK_COLORS = None


def _block_colors():
    """tools/rough/block_colors.json (ブロックの絵の平均の色)。地図の色より見た目に近い"""
    global _BLOCK_COLORS
    if _BLOCK_COLORS is None:
        import json
        from pathlib import Path
        f = Path(__file__).resolve().parent.parent / "rough" / "block_colors.json"
        _BLOCK_COLORS = json.loads(f.read_text(encoding="utf-8")) if f.exists() else {}
    return _BLOCK_COLORS


_TEX = {}


def _texture_color(name: str):
    """ブロックの絵 (上の面を優先) の平均の色。バニラは build/moddev/artifacts の client-extra の jar、mod は src の assets から"""
    if name in _TEX:
        return _TEX[name]
    import io
    import zipfile
    from pathlib import Path
    ns, key = name.split(":", 1) if ":" in name else ("minecraft", name)
    root = Path(__file__).resolve().parents[2]
    cands = [f"{key}_top", key, f"{key}_side", key.replace("_stairs", "").replace("_slab", "").replace("_wall", "")]
    cands += [c + "s" for c in cands[-1:]] + [cands[-1].replace("brick", "bricks")]
    rgb = None
    if ns == "minecraft":
        jars = sorted(root.glob("build/moddev/artifacts/*client-extra*.jar")) + sorted(root.parents[2].glob("build/moddev/artifacts/*client-extra*.jar")) if len(root.parents) > 2 else []
        for jar in jars[:1]:
            with zipfile.ZipFile(jar) as z:
                names = set(z.namelist())
                for c in cands:
                    path = f"assets/minecraft/textures/block/{c}.png"
                    if path in names:
                        rgb = _avg(Image.open(io.BytesIO(z.read(path))))
                        break
    else:
        for c in cands:
            f = root / "src/main/resources/assets" / ns / "textures/block" / f"{c}.png"
            if f.exists():
                rgb = _avg(Image.open(f))
                break
    _TEX[name] = rgb
    return rgb


def _avg(img):
    a = np.asarray(img.convert("RGBA"), dtype=np.float32)
    a = a[: a.shape[1]]  # 動く絵は最初の 1 枚
    w = a[..., 3:4] / 255
    if w.sum() == 0:
        return None
    return tuple(int(v) for v in (a[..., :3] * w).sum(axis=(0, 1)) / w.sum())


def _colors(d: Dump):
    c = d.colors.astype(np.int16).copy()
    bc = _block_colors()
    for i, name in enumerate(d.names):
        key = name.split(":", 1)[-1]
        t = _texture_color(name)
        if t is not None:
            c[i] = t
        elif key in bc:
            c[i] = bc[key][:3]
    # 地図の色が無いもの (ガラス・草など) は灰
    blank = (c.sum(axis=1) == 0)
    c[blank] = NONE
    return c


def _visible(d: Dump):
    """図に描くブロック: 空気でない (水も描く)"""
    return ~d.air


def top(d: Dump, scale: int = 2, mask=None) -> Image.Image:
    vis = _visible(d) if mask is None else mask
    sy, sz, sx = d.shape
    any_ = vis.any(axis=0)
    h = sy - 1 - np.argmax(vis[::-1], axis=0)  # いちばん上の添字
    zz, xx = np.indices((sz, sx))
    ids = d.ids[h, zz, xx]
    col = _colors(d)[ids].astype(np.float32)
    col[d.water[h, zz, xx]] = WATER
    # 高さの陰: 北西からの光
    hf = np.where(any_, h, 0).astype(np.float32)
    shade = np.zeros_like(hf)
    shade[1:, 1:] = (hf[1:, 1:] - hf[:-1, :-1]) * 0.06
    shade += (hf - hf[any_].mean() if any_.any() else 0) * 0.004
    col *= np.clip(1 + shade, 0.55, 1.35)[..., None]
    col[~any_] = SKY
    img = Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), "RGB")
    return img.resize((sx * scale, sz * scale), Image.NEAREST)


def section(d: Dump, axis: str, value: int, scale: int = 3) -> Image.Image:
    """axis='y' なら上から見た水平の断面 (北が上)、'x' なら西から見た縦の断面 (北が左)、'z' なら南から見た縦の断面 (西が左)"""
    ox, oy, oz = d.origin
    if axis == "y":
        ids = d.ids[value - oy]
        water, air, lava = d.water[value - oy], d.air[value - oy], d.lava[value - oy]
    elif axis == "x":
        ids = d.ids[::-1, :, value - ox]
        water, air, lava = d.water[::-1, :, value - ox], d.air[::-1, :, value - ox], d.lava[::-1, :, value - ox]
    elif axis == "z":
        ids = d.ids[::-1, value - oz, :]
        water, air, lava = d.water[::-1, value - oz, :], d.air[::-1, value - oz, :], d.lava[::-1, value - oz, :]
    else:
        raise ValueError(axis)
    col = _colors(d)[ids].copy()
    col[air] = AIR
    col[water] = WATER
    col[lava] = LAVA
    img = Image.fromarray(col.astype(np.uint8), "RGB")
    return img.resize((img.width * scale, img.height * scale), Image.NEAREST)


def iso(d: Dump, step: int = 1) -> Image.Image:
    """南東の上から見た等角の図。1 ブロック = 横 4・縦 4 画素 (上の面 2 段、側の面 2 段)。step で間引く"""
    vis = _visible(d)
    if step > 1:
        vis = vis[::step, ::step, ::step]
        ids = d.ids[::step, ::step, ::step]
        water = d.water[::step, ::step, ::step]
    else:
        ids, water = d.ids, d.water
    sy, sz, sx = vis.shape
    # 見える面がある所だけ (上・東・南が空いている)
    exposed = np.zeros_like(vis)
    up = np.ones_like(vis)
    up[:-1] = ~vis[1:]
    east = np.ones_like(vis)
    east[:, :, :-1] = ~vis[:, :, 1:]
    south = np.ones_like(vis)
    south[:, :-1, :] = ~vis[:, 1:, :]
    exposed = vis & (up | east | south)
    y, z, x = np.nonzero(exposed)
    col = _colors(d)[ids[y, z, x]].astype(np.float32)
    col[water[y, z, x]] = WATER
    # 奥から手前へ描く (手前 = 東・南・上)
    order = np.argsort(x + z + y, kind="stable")
    y, z, x, col = y[order], z[order], x[order], col[order]
    u = 2 * (x - z) + 2 * sz
    v = (x + z) - 2 * y + 2 * sy
    W = 2 * (sx + sz) + 4
    H = (sx + sz) + 2 * sy + 6
    img = np.zeros((H, W, 3), dtype=np.float32)
    img[:] = SKY
    hi = np.clip(col * 1.08, 0, 255)
    left = col * 0.78
    right = col * 0.62
    for du in range(4):
        img[v, u + du] = hi
        img[v + 1, u + du] = hi
    for du in range(2):
        img[v + 2, u + du] = left
        img[v + 3, u + du] = left
        img[v + 2, u + 2 + du] = right
        img[v + 3, u + 2 + du] = right
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
    bbox = Image.fromarray((np.abs(img - SKY).sum(axis=2) > 1).astype(np.uint8) * 255).getbbox()
    return out.crop(bbox) if bbox else out


def overlay_walk(d: Dump, dist: dict, scale: int = 2, marks=None) -> Image.Image:
    """真上から: 下地は薄い灰の地形、歩いて行ける所はその足の高さで色 (低い青 → 高い赤)。marks は {名前: (x,y,z)}"""
    base = np.asarray(top(d, scale=1).convert("L").convert("RGB"), dtype=np.float32) * 0.45 + 120
    sy, sz, sx = d.shape
    ox, oy, oz = d.origin
    hmax = np.full((sz, sx), -1, dtype=np.int32)
    for (x, y, z) in dist:
        iz, ix = z - oz, x - ox
        if y > hmax[iz, ix]:
            hmax[iz, ix] = y
    reached = hmax >= 0
    if reached.any():
        lo, hi = hmax[reached].min(), hmax[reached].max()
        t = (hmax - lo) / max(1, hi - lo)
        rgb = np.stack([60 + 195 * t, 90 + 80 * (1 - abs(t - 0.5) * 2), 230 - 190 * t], axis=-1)
        base[reached] = rgb[reached]
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB").resize((sx * scale, sz * scale), Image.NEAREST)
    if marks:
        dr = ImageDraw.Draw(img)
        for name, (x, y, z) in marks.items():
            px, pz = (x - ox) * scale, (z - oz) * scale
            dr.ellipse([px - 4, pz - 4, px + 4, pz + 4], outline=(0, 0, 0), width=2)
            dr.text((px + 6, pz - 6), name, fill=(0, 0, 0))
    return img


def label(img: Image.Image, text: str) -> Image.Image:
    out = Image.new("RGB", (img.width, img.height + 18), (255, 255, 255))
    out.paste(img, (0, 18))
    ImageDraw.Draw(out).text((4, 3), text, fill=(0, 0, 0))
    return out
