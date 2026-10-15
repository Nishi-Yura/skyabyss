"""1章の空「雲の切れ間の巨大な目」v2 のテクスチャ (雲の向こうの巨大な顔の一部) を作る。

使い方:
    python tools/rough/sky_eye_v2.py            # docs/review/sky_eye_roughs/v2_preview.png に確かめ用の合成だけ書く
    python tools/rough/sky_eye_v2.py --install  # 加えて src のテクスチャに書き出す

v1 (sky_eye.py) から引き継ぐもの: sclera.png (白目) と iris.png (瞳。B 月蝕の瞳)。
v2 で書くもの (src/main/resources/assets/skyabyss/textures/environment/sky_eye/):
    face.png  192x112  目のまわりの皮膚と羽毛 (目の幅 W に対して ±0.95W × ±0.55W)。
                       中のアーモンド形は閉じた瞼 (真ん中に合わせ目)。縁は透けて、雲の切れ目の外へ消える
    cloud.png  64x64   切れ間を作る雲の帯の濃淡 (白黒。色は夜の雲の色を掛ける。繰り返して貼る)
    haze.png   64x32   空気の霞 (やわらかい楕円。青灰を重ねて遠くに見せる)

描画の決まり (CloudEyeRenderer と同じ): 目の幅 W、縦の半分 0.21W、切れ間 a(u) = 開き × 0.21W × (1-(2u/W)^2)^0.72。
大きく描くが、画素はマイクラの空と同じく粗いまま (補間しない。1テクセル ≒ W/100)。
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs/review/sky_eye_roughs"
SRC = ROOT / "src/main/resources/assets/skyabyss/textures/environment/sky_eye"

FACE_W, FACE_H = 192, 112
FACE_X, FACE_Y = 0.95, 0.55  # 顔の板の半分の大きさ (W に対する割合)
HALF_HEIGHT = 0.21


def smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


def value_noise(w, h, scale, seed, wrap=False):
    r = np.random.default_rng(seed)
    gw, gh = int(np.ceil(w / scale)), int(np.ceil(h / scale))
    grid = r.random((gh + 1, gw + 1))
    if wrap:
        grid[-1, :] = grid[0, :]
        grid[:, -1] = grid[:, 0]
    ys, xs = np.mgrid[0:h, 0:w]
    fx, fy = xs / scale, ys / scale
    ix, iy = fx.astype(int), fy.astype(int)
    tx, ty = smooth(fx - ix), smooth(fy - iy)
    a, b = grid[iy, ix], grid[iy, ix + 1]
    c, d = grid[iy + 1, ix], grid[iy + 1, ix + 1]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def rgba(rgb, a):
    out = np.zeros(rgb.shape[:2] + (4,), dtype=np.uint8)
    out[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    out[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def quantize(rgb, step=10):
    return np.round(rgb / step) * step


def face():
    ys, xs = np.mgrid[0:FACE_H, 0:FACE_W]
    # W 単位の座標 (上が +y)
    x = ((xs + 0.5) / FACE_W * 2 - 1) * FACE_X
    y = -((ys + 0.5) / FACE_H * 2 - 1) * FACE_Y
    t = 2 * x  # 目の幅で -1〜1
    shape = np.clip(1 - t * t, 0, None) ** 0.72
    aperture = HALF_HEIGHT * shape
    inside = np.abs(y) < aperture
    n1 = value_noise(FACE_W, FACE_H, 6, 11)
    n2 = value_noise(FACE_W, FACE_H, 2.5, 12)

    n3 = value_noise(FACE_W, FACE_H, 14, 13)

    # 目のまわりの楕円の距離 (1 = 目の縁のすぐ外)。ゆがませて、きれいな輪にしない
    ax, ay = 0.53, 0.25
    rho = np.sqrt((x / ax) ** 2 + (y / ay) ** 2) * (1 + 0.16 * (n3 - 0.5))
    phi = np.arctan2(y / ay, x / ax)

    base = np.array([40, 46, 60], float)  # 深い青灰
    rgb = np.broadcast_to(base, (FACE_H, FACE_W, 3)).copy()
    rgb *= (0.88 + 0.24 * n1)[..., None]
    # 目に近いほど少し明るい (瞳の光をうっすら受ける)
    rgb *= (1 + 0.07 * smooth((1.6 - rho) / 0.7))[..., None]

    # 瞼の皮膚: しわは2本だけ (上の瞼の二重のひだが深く、下は浅い)。目尻へ行くほど消える
    lid = (~inside) & (rho < 1.25)
    wob = 0.04 * (n2 - 0.5)
    crease = lid & (y > 0) & (np.abs(rho - 1.14 + wob) < 0.03) & (np.abs(x) < 0.4)
    crease |= lid & (y < 0) & (np.abs(rho - 1.1 + wob) < 0.022) & (np.abs(x) < 0.3)
    rgb[crease] *= 0.7

    # 羽毛: 目から外へ流れる鱗のような重なり。列ごとに大きさと位置がずれ、外ほど大きい。
    # 目尻の側 (x の外) へ流れるように、角度を外へ寄せる
    feather = (~inside) & (rho >= 1.22)
    r0 = 1.22
    k = np.floor(np.log(np.maximum(rho, r0) / r0) / np.log(1.2))
    rk = r0 * 1.2 ** k
    tt = (rho / rk - 1) / 0.2  # 列の中の半径方向 (0 = 根元、1 = 先)
    flow = phi + 0.35 * np.sin(phi) * np.sign(np.cos(phi))
    count = 22 + 3 * k
    ss = (flow / (2 * np.pi) * count + 0.37 * k * k + 0.45 * (n2 - 0.5)) % 1.0
    tip = 0.95 - 0.8 * (2 * ss - 1) ** 2  # 羽の先の丸い形
    shade = np.where(tt < tip, 0.8 + 0.22 * tt / np.maximum(tip, 0.01), 0.74)
    edge = feather & (np.abs(tt - tip) < 0.1)
    rgb[feather] *= shade[feather][..., None]
    rgb[edge] = rgb[edge] * 0.72
    # 羽の先のかすかな白 (目の光を受ける。目に近い列だけ、まばらに)
    lit = feather & (tt > tip - 0.24) & (tt < tip - 0.1) & (k < 2) & (n1 > 0.45)
    rgb[lit] = rgb[lit] * 0.85 + np.array([22, 22, 22])

    # 眉の張り出し (上側): 影が濃い
    brow = (y > 0) & (rho > 1.05)
    rgb[brow] *= (1 - 0.3 * smooth((y[brow] - 0.1) / 0.22))[..., None]

    # 閉じた瞼 (アーモンドの中): まわりの皮膚と同じ色で、上の瞼のふくらみと、真ん中の細い合わせ目
    lidc = base * 0.92 * (0.92 + 0.12 * n1[..., None])
    rgb[inside] = lidc[inside]
    bulge = inside & (y > 0)
    rgb[bulge] *= (1 + 0.1 * (y[bulge] / np.maximum(aperture[bulge], 1e-3)))[..., None]
    low = inside & (y < 0)
    rgb[low] *= (1 - 0.12 * (-y[low] / np.maximum(aperture[low], 1e-3)))[..., None]
    seam = np.abs(y + 0.004) < (0.004 + 0.007 * shape)
    rgb[seam & inside] = np.array([18, 20, 28])
    rim = (~inside) & (np.abs(y) < aperture + 0.018) & (shape > 0)
    rgb[rim] = rgb[rim] * 0.55

    # 透明度: 外の楕円で消える (縁はほつれる)
    # 雲の切れ間 (±0.5W × ±0.3W) より少し外で消える (雲のかたまりの内側に収まる)
    outer = np.sqrt((x / 0.78) ** 2 + (y / 0.46) ** 2)
    a = smooth((1.0 - outer + 0.12 * (n2 - 0.5)) / 0.32)
    return rgba(quantize(rgb, 6), np.round(a * 8) / 8)


def cloud():
    n = (value_noise(64, 64, 16, 21, wrap=True) * 0.6 + value_noise(64, 64, 8, 22, wrap=True) * 0.4)
    v = 0.6 + 0.4 * n
    v = np.round(v * 16) / 16  # 粗い段 (マイクラの雲の面のように)
    # 4 画素ごとのかたまりにする
    v = v[::4, ::4].repeat(4, 0).repeat(4, 1)
    rgb = np.stack([v * 255] * 3, -1)
    return rgba(rgb, np.ones((64, 64)))


def haze():
    ys, xs = np.mgrid[0:32, 0:64]
    u = (xs + 0.5) / 64 * 2 - 1
    v = (ys + 0.5) / 32 * 2 - 1
    a = smooth(1 - np.sqrt(u * u + v * v))
    return rgba(np.full((32, 64, 3), 255.0), np.round(a * 12) / 12)


def preview(face_img):
    """夜空に置いた目 (半分開き / 開き) の確かめ用の合成"""
    sclera = Image.open(SRC / "sclera.png").convert("RGBA")
    iris = Image.open(SRC / "iris.png").convert("RGBA")
    W = 300
    CH = int(W * FACE_Y * 2) + 20
    CW = int(W * FACE_X * 2) + 20
    out = Image.new("RGBA", (CW * 2 + 20, CH), (8, 10, 18, 255))
    for i, op in enumerate((0.0, 1.0)):
        canvas = Image.new("RGBA", (CW, CH), (8, 10, 18, 255))
        fw, fh = int(W * FACE_X * 2), int(W * FACE_Y * 2)
        f = face_img.resize((fw, fh), Image.NEAREST)
        cx, cy = CW // 2, CH // 2
        canvas.alpha_composite(f, (cx - fw // 2, cy - fh // 2))
        if op > 0:
            sw, sh = W, int(W * HALF_HEIGHT * 2)
            s = np.array(sclera.resize((sw, sh), Image.NEAREST)).astype(float)
            ir = int(W * HALF_HEIGHT * 1.08 * 2)
            iv = np.array(iris.resize((ir, ir), Image.NEAREST)).astype(float)
            s[..., :3] *= 0.6
            ox, oy = (sw - ir) // 2, (sh - ir) // 2
            for yy in range(ir):
                for xx in range(ir):
                    if 0 <= oy + yy < sh:
                        al = iv[yy, xx, 3] / 255
                        s[oy + yy, ox + xx, :3] = s[oy + yy, ox + xx, :3] * (1 - al) + iv[yy, xx, :3] * 0.85 * al
            ys2, xs2 = np.mgrid[0:sh, 0:sw]
            t = (xs2 + 0.5) / sw * 2 - 1
            ap = op * np.clip(1 - t * t, 0, None) ** 0.72
            vv = np.abs((ys2 + 0.5) / sh * 2 - 1)
            s[..., 3] = np.where(vv < ap, 255, 0)
            canvas.alpha_composite(Image.fromarray(s.astype(np.uint8), "RGBA"), (cx - sw // 2, cy - sh // 2))
        out.alpha_composite(canvas, (i * (CW + 20), 0))
    return out


def main():
    f = face()
    DOCS.mkdir(parents=True, exist_ok=True)
    preview(f).save(DOCS / "v2_preview.png")
    if "--install" in sys.argv:
        f.save(SRC / "face.png")
        cloud().save(SRC / "cloud.png")
        haze().save(SRC / "haze.png")
        print("installed face/cloud/haze to", SRC)


if __name__ == "__main__":
    main()
