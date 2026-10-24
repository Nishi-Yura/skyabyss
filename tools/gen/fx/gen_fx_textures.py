#!/usr/bin/env python3
"""技の見た目 (client/skill/fx/FxDraw) が使う柔らかい textures を作る。

使い方 (リポジトリ直下で):  python tools/gen/fx/gen_fx_textures.py

出すもの (src/main/resources/assets/skyabyss/textures/fx/):
  glow.png  64x64 ・ 中心が白く、縁へ向かって消える円 (地面の円盤・閃光の光の玉)
  band.png  4x64  ・ 縦方向だけ変わる帯の断面 (中心が明るく、両の縁が消える)。弧・筋・柱の幅に使う
どちらも白の RGB と、アルファだけに形が入る。色は頂点の色で付ける (テクスチャは色を持たない)。
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/fx"


def smooth(x: float) -> float:
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def glow() -> Image.Image:
    n = 64
    im = Image.new("RGBA", (n, n), (255, 255, 255, 0))
    px = im.load()
    for y in range(n):
        for x in range(n):
            dx = (x + 0.5) / n * 2 - 1
            dy = (y + 0.5) / n * 2 - 1
            r = (dx * dx + dy * dy) ** 0.5
            a = smooth(1 - r) ** 1.6
            px[x, y] = (255, 255, 255, round(a * 255))
    return im


def band() -> Image.Image:
    w, h = 4, 64
    im = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    px = im.load()
    for y in range(h):
        v = (y + 0.5) / h * 2 - 1  # -1 縁 .. 0 中心 .. 1 縁
        a = smooth(1 - abs(v)) ** 1.3
        for x in range(w):
            px[x, y] = (255, 255, 255, round(a * 255))
    return im


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    glow().save(OUT / "glow.png", optimize=True)
    band().save(OUT / "band.png", optimize=True)
    print("wrote", OUT / "glow.png", "and", OUT / "band.png")


if __name__ == "__main__":
    main()
