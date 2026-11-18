# -*- coding: utf-8 -*-
"""取り込んだ模型（lowpoly/look/a/<id>.json + png）を、ゲームと同じ形とテクスチャで並べて描く。

シリーズの品を同じ大きさ・同じ角度で並べ、表の斜めと裏の斜めの 2 段にする（60 の 1.0 の確認用）。
面は外から見て反時計回りのときだけ描く（裏返った面は抜けて見えるので、向きの間違いに気づける）。
透明な画素は描かない（葉・星の板・炎の切り抜き）。光る画素は陰を付けない。

  python -I tools/gen/pixel/look_lineup.py out.png shrine_staff shrine_sword ...
  python -I tools/gen/pixel/look_lineup.py out.png old:whale_hammer=前 whale_hammer=新   （old: で前の模型）
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
A = ROOT / "src/main/resources/assets/skyabyss"
SCALE = 9.0          # 1 単位 = 9 画素
CELL_W, CELL_H = 340, 470
ORIGIN_Y = 300       # 握り（原点）の高さ（セルの上から）
LIGHT = np.array([0.35, 0.8, 0.5]) / np.linalg.norm([0.35, 0.8, 0.5])


def load(id_):
    """id の頭に "old:" を付けると、前の模型 (lowpoly/items/<id>/<段>.json。段は一番上のもの) を読む"""
    if id_.startswith("old:"):
        d = A / "lowpoly/items" / id_[4:]
        f = sorted(q for q in d.glob("*.json") if "_pull" not in q.stem)[-1]
        j = json.loads(f.read_text(encoding="utf-8"))
    else:
        j = json.loads((A / "lowpoly/look/a" / f"{id_}.json").read_text(encoding="utf-8"))
    tex = np.asarray(Image.open(A / "textures/lowpoly" / f"{j['texture']}.png").convert("RGBA")).astype(float)
    gp = A / "textures/lowpoly" / f"{j['texture']}_glow.png"
    glow = np.asarray(Image.open(gp).convert("RGBA"))[..., 3] > 0 if gp.exists() else np.zeros(tex.shape[:2], bool)
    V, UV, T = [], [], []
    for b in j["bones"].values():                 # 骨が一つでも複数でも、全部まとめて描く
        n0 = len(V)
        V += b["vertices"]
        UV += b["uvs"]
        T += [[n0 + a, n0 + c, n0 + d] for a, c, d in b["triangles"]]
    return np.array(V, float), np.array(UV, float), np.array(T, int), tex, glow


def rot(yaw, pitch):
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    return rx @ ry


def render(id_, yaw_deg, pitch_deg=12):
    V, UV, T, tex, glow = load(id_)
    two_sided = id_.startswith("old:")           # 前の模型は面の向きがそろっていないので両面で描く
    R = rot(math.radians(yaw_deg), math.radians(pitch_deg))
    P = V @ R.T                                   # 見る向きへ回す（カメラは +z から -z を見る）
    img = np.zeros((CELL_H, CELL_W, 4), float)
    zb = np.full((CELL_H, CELL_W), -1e9)
    sx = CELL_W / 2 + P[:, 0] * SCALE
    sy = ORIGIN_Y - P[:, 1] * SCALE
    th, tw = tex.shape[:2]
    for a, b, c in T:
        x0, y0, x1, y1, x2, y2 = sx[a], sy[a], sx[b], sy[b], sx[c], sy[c]
        area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
        if area >= -1e-9 and not two_sided:       # 画面は y が下向きなので、表の面は負になる
            continue
        if abs(area) < 1e-9:
            continue
        n = np.cross(P[b] - P[a], P[c] - P[a])
        n /= (np.linalg.norm(n) + 1e-12)
        shade = 0.55 + 0.45 * max(0.0, float(n @ LIGHT))
        minx, maxx = int(max(0, math.floor(min(x0, x1, x2)))), int(min(CELL_W - 1, math.ceil(max(x0, x1, x2))))
        miny, maxy = int(max(0, math.floor(min(y0, y1, y2)))), int(min(CELL_H - 1, math.ceil(max(y0, y1, y2))))
        if minx > maxx or miny > maxy:
            continue
        xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
        w0 = ((x1 - xs) * (y2 - ys) - (x2 - xs) * (y1 - ys)) / area
        w1 = ((x2 - xs) * (y0 - ys) - (x0 - xs) * (y2 - ys)) / area
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue
        z = w0 * P[a, 2] + w1 * P[b, 2] + w2 * P[c, 2]
        u = w0 * UV[a, 0] + w1 * UV[b, 0] + w2 * UV[c, 0]
        v = w0 * UV[a, 1] + w1 * UV[b, 1] + w2 * UV[c, 1]
        tx = np.clip((u * tw).astype(int), 0, tw - 1)
        ty = np.clip((v * th).astype(int), 0, th - 1)
        col = tex[ty, tx]
        lit = glow[ty, tx]
        sub_z = zb[miny:maxy + 1, minx:maxx + 1]
        ok = inside & (col[..., 3] >= 128) & (z > sub_z)
        if not ok.any():
            continue
        rgb = col[..., :3] * np.where(lit, 1.0, shade)[..., None]
        sub = img[miny:maxy + 1, minx:maxx + 1]
        sub[ok, :3] = rgb[ok]
        sub[ok, 3] = 255
        sub_z[ok] = z[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def main(out, ids, labels=None):
    bg = (214, 226, 240, 255)
    font = ImageFont.truetype("C:/Windows/Fonts/meiryob.ttc", 22)
    rows = [("表の斜め", 30), ("裏の斜め", 210)]
    W = Image.new("RGBA", (CELL_W * len(ids), CELL_H * len(rows) + 40), bg)
    d = ImageDraw.Draw(W)
    for r, (rl, yaw) in enumerate(rows):
        for i, id_ in enumerate(ids):
            W.alpha_composite(render(id_, yaw), (i * CELL_W, 40 + r * CELL_H))
        d.text((8, 40 + r * CELL_H + 6), rl, fill=(40, 40, 50), font=font)
    for i, id_ in enumerate(ids):
        d.text((i * CELL_W + 10, 8), (labels or ids)[i], fill=(30, 30, 40), font=font)
    W.convert("RGB").save(out)


if __name__ == "__main__":
    args = sys.argv[2:]
    ids = [a.split("=")[0] for a in args]
    labels = [a.split("=")[1] if "=" in a else a for a in args]
    main(sys.argv[1], ids, labels)
