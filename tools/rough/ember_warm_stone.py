"""温石 (skyabyss:warm_stone) の 32x32 アイコンを 3 案 (a/b/c) で描く。乱数は使わない (同じ入力なら同じ絵)。

  a = 丸い焼き小石: 指の跡のくぼみ (左上が影、右下が明るい縁)。割れ目が三本、細く赤く光る
  b = 平たい楕円の石: うず巻きの指紋 (溝を一段暗く)。割れ目は一本だけ、中心が明るい
  c = 縄を巻いた小石: 手でよじった縄が斜めに巻き、その間から割れ目がのぞく

影の向き: 左上から光が当たる (既存の 32px アイコンと同じ。縁は暗い色の 1px 輪郭、
段は 5〜7 段の明暗、中の小さな斑点で焼き物の表面を出す)。

実行 (リポジトリ直下):
  python tools/rough/ember_warm_stone.py                 # 3 案と比較シートを作る
  python tools/rough/ember_warm_stone.py --install b     # 採用案を本番の 32px に入れる

出力:
  docs/review/ember_roughs/warm_stone/a.png, b.png, c.png    (32x32 そのまま)
  docs/review/ember_roughs/warm_stone/compare.png            (x8 拡大・名前付き・32px の実寸を暗い背景と明るい背景で)
本番: src/main/resources/assets/skyabyss/textures/item/warm_stone.png
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import font  # noqa: E402

OUT = ROOT / "docs/review/ember_roughs/warm_stone"
ITEM = ROOT / "src/main/resources/assets/skyabyss/textures/item/warm_stone.png"
W = 32


def hx(h: str) -> tuple:
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# 焼き物の段 (暗 -> 明)。index 0 と 6 は、くぼみと縁の強調に使う
CLAY = [hx(c) for c in ("#24191a", "#3e2f2a", "#5e4a3f", "#7d6456", "#9c8170", "#bda08c", "#dcc7b3")]
OUTLINE = hx("#1a1214")
CORD = [hx(c) for c in ("#3f2c1b", "#6b4c2c", "#90653a", "#b38750", "#d6ad76")]
CORD_DARK = hx("#2b1d12")
EMBER = {"core": hx("#fff1b0"), "mid": hx("#ffb347"), "red": hx("#ff4a1f"), "deep": hx("#b3260f")}
HALO = hx("#7b2a1a")  # 割れ目のまわりの石を、わずかに赤く照らす

YY, XX = np.mgrid[0:W, 0:W]
PX = XX + 0.5
PY = YY + 0.5


def stone(cx, cy, rx, ry):
    """楕円の石。(mask, 段 0〜6)。段は左上からの光と、縁の丸みで決める"""
    u = (PX - cx) / rx
    v = (PY - cy) / ry
    d = u * u + v * v
    mask = d <= 1.0
    nz = np.sqrt(np.clip(1 - d, 0, 1))
    n = np.stack([u, v, nz], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True) + 1e-9
    L = np.array([-0.5, -0.6, 0.62])
    L /= np.linalg.norm(L)
    s = n @ L
    lv = np.select([s < 0.15, s < 0.38, s < 0.58, s < 0.78], [1, 2, 3, 4], 5)
    lv = np.where(mask & (d > 0.6) & (s < 0.25), 0, lv)          # 右下の縁は暗く
    lv = np.where(mask & (d > 0.72) & (s > 0.6), 6, lv)          # 左上の縁は明るく
    # 焼き物の表面の小さな斑点 (位置の決まった並び。乱数ではない)
    speck = (XX * 7 + YY * 13 + XX * YY) % 17
    lv = np.where(mask & (speck == 0), lv + 1, lv)
    lv = np.where(mask & (speck == 5), lv - 1, lv)
    return mask, np.clip(lv, 0, 6)


def outline_of(mask):
    """マスクの外側の一周 (内側に1画素)"""
    inner = mask.copy()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        sh = np.zeros_like(mask)
        ys = slice(max(dy, 0), W + min(dy, 0))
        yd = slice(max(-dy, 0), W + min(-dy, 0))
        xs = slice(max(dx, 0), W + min(dx, 0))
        xd = slice(max(-dx, 0), W + min(-dx, 0))
        sh[yd, xd] = mask[ys, xs]
        inner &= sh
    return mask & ~inner


def bresenham(p0, p1):
    x0, y0 = p0
    x1, y1 = p1
    pts = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    return pts


def polyline(points):
    out = []
    for a, b in zip(points, points[1:]):
        out += bresenham(a, b)
    return out


def paint(mask, lv, outline, cords=None):
    img = np.zeros((W, W, 4), np.uint8)
    pal = np.array(CLAY, np.uint8)
    body = mask & ~outline
    img[body] = pal[lv[body]]
    if cords is not None:
        img[cords] = np.array(CORD, np.uint8)[np.clip(lv[cords] - 1, 0, 4)]
    img[outline] = np.array(OUTLINE, np.uint8)
    return img


def draw_crack(img, pts, body_mask, halo=True, core=True):
    """割れ目: 赤い線、中心の一画素だけ明るい。まわりの石は赤く照らす"""
    if halo:
        for x, y in pts:
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < W and 0 <= yy < W and body_mask[yy, xx] and (dx, dy) != (0, 0):
                        if (xx, yy) not in pts:
                            img[yy, xx, :3] = (np.array(img[yy, xx, :3], float) * 0.45 + np.array(HALO[:3], float) * 0.55).astype(np.uint8)
    for x, y in pts:
        if 0 <= x < W and 0 <= y < W and body_mask[y, x]:
            img[y, x] = EMBER["red"]
    if core:
        for x, y in pts[1::3]:
            if 0 <= x < W and 0 <= y < W and body_mask[y, x]:
                img[y, x] = EMBER["mid"]


def option_a():
    mask, lv = stone(16, 17, 11, 10)
    # 指の跡: 親指で押したくぼみ。底は一段暗く、左上の内側の縁は影、右下の縁は明るい
    tx, ty, trx, try_ = 14.0, 15.0, 3.6, 4.4
    dd = ((PX - tx) / trx) ** 2 + ((PY - ty) / try_) ** 2
    side = (PX - tx) + (PY - ty)
    inside = mask & (dd <= 0.7)
    lv = np.where(inside, lv - 2, lv)
    ring = mask & (dd > 0.7) & (dd <= 1.0)
    lv = np.where(ring & (side < 0), lv - 1, lv)
    lv = np.where(ring & (side >= 0), lv + 2, lv)
    # 指紋の筋: くぼみの中に、同じ向きの細い弧を二本
    arc = np.sqrt(dd)
    lv = np.where(inside & (arc > 0.42) & (arc < 0.52), lv + 1, lv)
    lv = np.clip(lv, 0, 6)
    img = paint(mask, lv, outline_of(mask))
    body = mask & ~outline_of(mask)
    cracks = [
        [(20, 19), (22, 21), (21, 23), (24, 24)],
        [(22, 21), (25, 20), (26, 22)],
        [(18, 24), (19, 26), (17, 27)],
    ]
    for c in cracks:
        draw_crack(img, polyline(c), body)
    return img


def option_b():
    mask, lv = stone(16, 17, 13, 8)
    # 指紋: うず巻きの溝 (一段暗い線)
    t = np.linspace(0, 4 * np.pi, 600)
    r = 0.3 * t
    gx = np.round(15.5 + r * np.cos(t)).astype(int)
    gy = np.round(16.5 + r * np.sin(t) * 0.75).astype(int)
    groove = np.zeros((W, W), bool)
    for x, y in zip(gx, gy):
        if 0 <= x < W and 0 <= y < W:
            groove[y, x] = True
    lv = np.where(groove & mask, lv - 2, lv)
    lv = np.clip(lv, 0, 6)
    outline = outline_of(mask)
    img = paint(mask, lv, outline)
    body = mask & ~outline
    crack = [(19, 10), (20, 12), (19, 14), (22, 16), (23, 19), (25, 21)]
    draw_crack(img, polyline(crack), body)
    return img


def option_c():
    mask, lv = stone(16, 16.5, 10.5, 10)
    outline = outline_of(mask)
    # 縄: 斜めに巻く帯。帯の中は、よじりで一本おきに濃淡を変える
    k = (XX + 2 * YY) % 8
    cord = mask & (k <= 1)
    twist = ((XX + YY) % 2 == 0)
    lv_cord = np.where(twist, lv, np.clip(lv - 1, 0, 6))
    img = paint(mask, lv, outline, cords=cord & ~outline)
    img[cord & ~outline] = np.array(CORD, np.uint8)[np.clip(lv_cord[cord & ~outline] - 1, 0, 4)]
    img[cord & ~outline & twist] = np.array(CORD, np.uint8)[np.clip(lv_cord[cord & ~outline & twist] - 1, 0, 4)]
    img[outline] = np.array(OUTLINE, np.uint8)
    # 縄の結び目 (上の端)
    for x, y in ((15, 6), (16, 6), (17, 6), (16, 5)):
        if not outline[y, x]:
            img[y, x] = np.array(CORD[1], np.uint8)
    body = mask & ~outline & ~cord
    crack = [(20, 22), (21, 24), (23, 23), (24, 26)]
    draw_crack(img, polyline(crack), body)
    crack2 = [(11, 20), (12, 22), (11, 24)]
    draw_crack(img, polyline(crack2), body)
    return img


OPTIONS = {"a": option_a, "b": option_b, "c": option_c}
TITLES = {
    "a": "a 丸い焼き小石 (指の跡)",
    "b": "b 平たい石 (うず巻きの指紋)",
    "c": "c 縄を巻いた小石",
}


def compare(imgs: dict[str, np.ndarray]) -> Image.Image:
    big = 8
    pad = 24
    cell = W * big
    label_h = 40
    small_h = W + 16
    colw = cell + pad
    H = 60 + label_h + cell + 40 + small_h * 2 + pad * 2
    out = Image.new("RGB", (pad + colw * 3, H), (18, 18, 26))
    d = ImageDraw.Draw(out)
    d.text((pad, 14), "温石 (skyabyss:warm_stone) 3 案", font=font(28), fill=(235, 235, 245))
    for i, key in enumerate(("a", "b", "c")):
        im = Image.fromarray(imgs[key], "RGBA")
        x = pad + i * colw
        d.text((x, 60), TITLES[key], font=font(20), fill=(120, 220, 230))
        y0 = 60 + label_h
        out.paste(im.resize((cell, cell), Image.NEAREST), (x, y0), im.resize((cell, cell), Image.NEAREST))
        # 実寸 (32px) を暗い背景と明るい背景に
        ys = y0 + cell + 30
        dark = Image.new("RGBA", (W + 16, small_h), (32, 32, 40, 255))
        dark.alpha_composite(im, (8, 8))
        light = Image.new("RGBA", (W + 16, small_h), (232, 228, 220, 255))
        light.alpha_composite(im, (8, 8))
        out.paste(dark.convert("RGB"), (x, ys), dark)
        out.paste(light.convert("RGB"), (x + 0, ys + small_h + 6))
        d.text((x + W + 36, ys + 6), "暗い背景 (実寸 32px)", font=font(14), fill=(170, 170, 190))
        d.text((x + W + 36, ys + small_h + 12), "明るい背景 (実寸 32px)", font=font(14), fill=(90, 90, 100))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", choices=["a", "b", "c"], help="採用案を本番の 32px テクスチャに書く")
    args = ap.parse_args()

    imgs = {k: f() for k, f in OPTIONS.items()}
    OUT.mkdir(parents=True, exist_ok=True)
    for k, img in imgs.items():
        Image.fromarray(img, "RGBA").save(OUT / f"{k}.png")
    compare(imgs).save(OUT / "compare.png")
    print("wrote", OUT)

    if args.install:
        ITEM.parent.mkdir(parents=True, exist_ok=True)
        Image.fromarray(imgs[args.install], "RGBA").save(ITEM)
        print("installed", args.install, "->", ITEM)


if __name__ == "__main__":
    main()
