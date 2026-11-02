"""月の空に浮かぶ地上 (大陸) の絵 (docs/design/46 の 3.1。client/MoonClient が描く)。旅の地図の大陸の絵 (textures/gui/map/continent.png) から作る:
道の線をならし、海を深い青へ、薄い雲を流し、球の陰 (縁ほど暗い) と、外の淡い大気の輪をつける。

  python -I tools/gen/world/gen_moon_earth.py   -> assets/skyabyss/textures/environment/moon_earth.png (256x256)
"""
import math
import os

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
SRC = os.path.join(ROOT, "src/main/resources/assets/skyabyss/textures/gui/map/continent.png")
OUT = os.path.join(ROOT, "src/main/resources/assets/skyabyss/textures/environment/moon_earth.png")
N = 256


def noise(n, scale, seed):
    rng = np.random.default_rng(seed)
    g = rng.random((n // scale + 2, n // scale + 2))
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
    return np.asarray(img, float) / 255.0


def main():
    big = np.asarray(Image.open(SRC).convert("RGB"), float)
    # 道 (赤茶の細い線) と記号を消す: その色の画素を、ぼかした絵で埋める
    road = (big[..., 0] > big[..., 1] + 25) & (big[..., 1] < 120) & (big[..., 2] < 100)
    road = np.asarray(Image.fromarray((road * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(7)), float) > 0
    blur = np.asarray(Image.fromarray(big.astype(np.uint8)).filter(ImageFilter.GaussianBlur(9)), float)
    big[road] = blur[road]
    m = Image.fromarray(big.astype(np.uint8)).filter(ImageFilter.MedianFilter(9)).resize((N, N), Image.LANCZOS)
    a = np.asarray(m, float)
    # 海: 地図の淡い青灰色 → 深い青 (陸との境の白い縁は浅い海の色)
    sea = (a[..., 2] > a[..., 0] + 4) & (a[..., 1] > a[..., 0])
    a[sea] = a[sea] * 0.35 + np.array([24, 60, 120]) * 0.65
    # 雲
    cl = noise(N, 24, 3) * 0.6 + noise(N, 8, 4) * 0.4
    cl = np.clip((cl - 0.58) / 0.25, 0, 1) * 0.75
    a = a * (1 - cl[..., None]) + 245 * cl[..., None]
    # 球: 縁ほど暗く、左上から光
    yy, xx = np.mgrid[0:N, 0:N]
    cx = cy = (N - 1) / 2
    rad = N * 0.40
    dx, dy = (xx - cx) / rad, (yy - cy) / rad
    d = np.sqrt(dx * dx + dy * dy)
    inside = d <= 1
    z = np.sqrt(np.clip(1 - d * d, 0, 1))
    light = np.clip(0.35 + 0.75 * (-0.45 * dx - 0.45 * dy + 0.77 * z), 0.12, 1.1)
    # 円の中へ地図の全部を写す (まわりの海ごと)
    out = np.zeros((N, N, 4), float)
    sx = np.clip((cx + dx * N * 0.5).astype(int), 0, N - 1)
    sy = np.clip((cy + dy * N * 0.5).astype(int), 0, N - 1)
    col = a[sy, sx] * light[..., None]
    out[..., :3] = np.where(inside[..., None], col, 0)
    out[..., 3] = np.where(inside, 255, 0)
    # 大気の輪 (球の外へ淡い青が薄れていく)
    halo = np.clip(1 - (d - 1) / 0.18, 0, 1) * (d > 1)
    out[..., :3] += (np.array([120, 170, 255]) * halo[..., None]) * (~inside[..., None])
    out[..., 3] += 150 * halo ** 1.6
    rim = np.clip(1 - (1 - d) / 0.06, 0, 1) * inside
    out[..., :3] = out[..., :3] * (1 - rim[..., None] * 0.5) + np.array([140, 190, 255]) * rim[..., None] * 0.5
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA").save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
