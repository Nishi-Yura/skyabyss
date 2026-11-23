# -*- coding: utf-8 -*-
"""設定画（正面のドット絵、1 画素 = 1 単位 x32）から、Blockbench に置く箱の面の UV と絵の板を作る。

設定画の通りに見えるよう、箱の表（south, +z を向く面）に設定画を、裏（north）に左右を返した設定画を貼る。
側面と上下は素材の小さな絵（タイル）を貼り、そのあと Blockbench の筆で描き込む。

  python -I tools/gen/pixel/concept_boxes.py <spec.py> <出力フォルダ>
spec.py に書くもの:
  CONCEPT = 設定画の PNG のパス、OX, OY = 設定画の中の原点の画素
  RAMPS = {素材: [暗→明 6 色]}
  BOXES = [(名前, 素材, [x0, y0, z0], [x1, y1, z1]), ...]   x, y は設定画の画素の端（x1, y1 は含まない端）
出力: atlas.png（絵の板 128 か 256）と boxes.json（place_cube に渡す要素と面の UV）
"""
import importlib.util
import json
import sys
from pathlib import Path

from PIL import Image


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def fill_holes(img):
    """透明な画素を、隣の色で埋める（箱の角が抜けて見えないように）"""
    w, h = img.size
    px = img.load()
    for _ in range(3):
        src = img.copy().load()
        for y in range(h):
            for x in range(w):
                if src[x, y][3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        if 0 <= x + dx < w and 0 <= y + dy < h and src[x + dx, y + dy][3] > 0:
                            px[x, y] = src[x + dx, y + dy]
                            break
    return img


def tile(ramp, w, h, kind):
    """側面の素材の絵（左が明るく右が暗い丸み、上から下へ少し暗く、境に暗い線）"""
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            t = 3
            if x < w * 0.25:
                t += 1
            elif x >= w * 0.75:
                t -= 1
            if y >= h * 0.7:
                t -= 1
            if kind == "band" and (y // 2) % 2:
                t -= 1
            if (x * 7 + y * 13) % 17 == 0:
                t -= 1
            if x == 0 or y == 0:
                t += 1
            if x == w - 1 or y == h - 1:
                t = 0
            img.putpixel((x, y), hexrgb(ramp[max(0, min(5, t))]))
    return img


def main(spec_path, out_dir):
    spec = importlib.util.spec_from_file_location("spec", spec_path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    con = fill_holes(Image.open(m.CONCEPT).convert("RGBA"))
    cw, ch = con.size
    W = 256 if cw * 2 > 128 or ch + 64 > 128 else 128
    atlas = Image.new("RGBA", (W, W))
    atlas.paste(con, (0, 0))
    atlas.paste(con.transpose(Image.FLIP_LEFT_RIGHT), (cw, 0))
    # 素材のタイル（側面用 32×24、下の面用は一段暗い）
    tiles = {}
    tx, ty = 0, ch
    for mat, ramp in m.RAMPS.items():
        for kind in ("side", "band"):
            if tx + 32 > W:
                tx, ty = 0, ty + 24
            atlas.paste(tile(ramp, 32, 24, kind), (tx, ty))
            tiles[(mat, kind)] = (tx, ty)
            tx += 32
    assert ty + 24 <= W, "atlas full"
    out = []
    for b in m.BOXES:
        name, mat, frm, to = b[:4]
        kind = b[4] if len(b) > 4 else "side"
        x0, y0, z0 = frm
        x1, y1, z1 = to
        # 設定画の画素: u = OX + x、v = OY - y（y は上が大きい）
        su = [m.OX + x0, m.OY - y1, m.OX + x1, m.OY - y0]
        nu = [cw + (cw - (m.OX + x1)), m.OY - y1, cw + (cw - (m.OX + x0)), m.OY - y0]
        tx0, ty0 = tiles[(mat, kind)]
        dz, dx, dy = z1 - z0, x1 - x0, y1 - y0
        side = lambda w, h: [tx0, ty0, tx0 + min(w, 32), ty0 + min(h, 24)]
        faces = [{"face": "south", "uv": su}, {"face": "north", "uv": nu},
                 {"face": "east", "uv": side(dz, dy)}, {"face": "west", "uv": side(dz, dy)},
                 {"face": "up", "uv": side(dx, dz)}, {"face": "down", "uv": side(dx, dz)}]
        el = {"name": name, "from": list(frm), "to": list(to), "origin": [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2]}
        out.append({"element": el, "faces": faces})
    Path(out_dir).mkdir(parents=True, exist_ok=True)
    atlas.save(Path(out_dir) / "atlas.png")
    (Path(out_dir) / "boxes.json").write_text(json.dumps({"size": W, "boxes": out}, ensure_ascii=False), encoding="utf-8")
    print(len(out), "boxes, atlas", W)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
