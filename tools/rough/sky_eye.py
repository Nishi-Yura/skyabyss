"""1章の空「雲の切れ間の巨大な目」のテクスチャと、ラフ 3 案の比較画像を作る。

使い方:
    python tools/rough/sky_eye.py            # docs/review/sky_eye_roughs/ にラフ 3 案と比較画像だけを書く
    python tools/rough/sky_eye.py --install  # 加えて、採用案 (CHOSEN) を src のテクスチャに書き出す

書き出すテクスチャ (src/main/resources/assets/skyabyss/textures/environment/sky_eye/):
    sclera.png   128x56  白目 (アーモンド形。縁はやわらかく透ける)
    iris.png      48x48  瞳 (金の輪と瞳孔。上下は瞼に隠れる)
    lid.png      128x64  上下の瞼の雲 (上半分 = 上の瞼で下端がほつれた縁、下半分 = 下の瞼で上端が縁)。白黒で、色は夜の雲の色を掛ける
    lid_glow.png 128x16  切れ間の縁の光 (足し合わせ。下端 = 縁が明るい)
    patch.png     64x32  目の周りに集まる雲のかたまり

描画の決まり (CloudEyeRenderer と同じ):
    目の幅 W、縦の半分 H = 0.21W。切れ間の上下の幅 a(u) = 開き具合 × H × (1 - (2u/W)^2)^0.72
    瞳の半径 = 1.08H、瞼の雲の厚さ = 1.35H、瞼の横の長さ = ±0.78W、雲のかたまり = ±1.25W × ±0.62W
マイクラの空 (太陽 32px、月 32px) と同じく、ドットの見える低い解像度にする (補間しない)。
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs/review/sky_eye_roughs"
SRC = ROOT / "src/main/resources/assets/skyabyss/textures/environment/sky_eye"
CHOSEN = "b"

SW, SH = 128, 56
IR = 48
HALF_HEIGHT = 0.21
LID_THICKNESS = 1.35
LID_REACH = 0.78
IRIS_RADIUS = 1.08
# 瞳の動く幅 (目の幅・縦の半分に対する割合)
IRIS_TRAVEL_X = 0.2
IRIS_TRAVEL_Y = 0.55
# 縁の光の強さ
GLOW_ALPHA = 0.35

rng = np.random.default_rng(1017)


def smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


def hash_noise(w, h, scale, seed):
    """なめらかな値ノイズ (0〜1)"""
    r = np.random.default_rng(seed)
    gw, gh = int(w / scale) + 3, int(h / scale) + 3
    grid = r.random((gh, gw))
    ys, xs = np.mgrid[0:h, 0:w]
    fx, fy = xs / scale, ys / scale
    ix, iy = fx.astype(int), fy.astype(int)
    tx, ty = smooth(fx - ix), smooth(fy - iy)
    a = grid[iy, ix]
    b = grid[iy, ix + 1]
    c = grid[iy + 1, ix]
    d = grid[iy + 1, ix + 1]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def almond(w, h):
    """テクスチャの中のアーモンド形の内側の度合い (縁で 0)"""
    ys, xs = np.mgrid[0:h, 0:w]
    u = (xs + 0.5) / w * 2 - 1
    v = (ys + 0.5) / h * 2 - 1
    shape = np.clip(1 - u * u, 0, None) ** 0.72
    return shape - np.abs(v), u, v


def rgba(rgb, a):
    out = np.zeros(rgb.shape[:2] + (4,), dtype=np.uint8)
    out[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    out[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def quantize(rgb, step=8):
    return np.round(rgb / step) * step


# ---------------- 3 案 ----------------

def sclera_a():
    """A 金環の瞳: 象牙色の白目に、瞳へ向かう金のかすかな筋。目尻は影で沈む"""
    inside, u, v = almond(SW, SH)
    n = hash_noise(SW, SH, 6, 11)
    base = np.array([236, 226, 198], float)
    shade = 1 - 0.45 * np.abs(u) ** 1.6 - 0.25 * np.clip(-inside * 0 + np.abs(v), 0, 1) ** 2
    rgb = base * shade[..., None] * (0.92 + 0.08 * n[..., None])
    ang = np.arctan2(v * SH, u * SW)
    veins = (np.sin(ang * 23 + n * 6) > 0.93) & (np.abs(u) > 0.35)
    rgb[veins] = rgb[veins] * 0.7 + np.array([200, 150, 60]) * 0.3
    a = smooth(inside / 0.08)
    return rgba(quantize(rgb), a)


def iris_a():
    ys, xs = np.mgrid[0:IR, 0:IR]
    x = (xs + 0.5) / IR * 2 - 1
    y = (ys + 0.5) / IR * 2 - 1
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    n = hash_noise(IR, IR, 3, 21)
    rays = 0.5 + 0.5 * np.sin(ang * 34 + n * 4)
    outer = np.array([110, 62, 18], float)
    mid = np.array([228, 166, 58], float)
    inner = np.array([255, 226, 138], float)
    t = np.clip((r - 0.3) / 0.62, 0, 1)
    rgb = inner * (1 - t[..., None]) + mid * t[..., None]
    rgb = rgb * (0.82 + 0.18 * rays[..., None])
    limbal = smooth((r - 0.82) / 0.12)
    rgb = rgb * (1 - limbal[..., None]) + outer * limbal[..., None]
    pupil = r < 0.3
    rgb[pupil] = np.array([14, 9, 6])
    ring = (r >= 0.3) & (r < 0.36)
    rgb[ring] = np.array([255, 240, 190])
    a = 1 - smooth((r - 0.94) / 0.06)
    return rgba(quantize(rgb), a)


def sclera_b():
    """B 月蝕の瞳: 白目はほとんど見えない青灰の暗がり (雲の奥)。瞳の近くだけ、金の光が細い筋になって滲む"""
    inside, u, v = almond(SW, SH)
    n = hash_noise(SW, SH, 8, 12)
    base = np.array([66, 72, 92], float)
    rgb = base * (0.75 + 0.25 * n[..., None]) * (1 - 0.45 * np.abs(u)[..., None])
    r = np.hypot(u * SW / SH, v)
    ang = np.arctan2(v, u * SW / SH)
    scatter = smooth(1 - (r - 1.0) / 1.4) * (0.5 + 0.5 * np.sin(ang * 17 + n * 5)) ** 3
    rgb = rgb + np.array([150, 112, 50]) * scatter[..., None] * 0.45
    a = smooth(inside / 0.1) * 0.92
    return rgba(quantize(rgb), a)


def iris_b():
    ys, xs = np.mgrid[0:IR, 0:IR]
    x = (xs + 0.5) / IR * 2 - 1
    y = (ys + 0.5) / IR * 2 - 1
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    n = hash_noise(IR, IR, 4, 22)
    craters = hash_noise(IR, IR, 2.2, 23)
    moon = np.array([228, 230, 222], float) * (0.84 + 0.16 * n[..., None])
    moon[craters > 0.78] *= 0.8
    # 瞳孔のまわりから外へ、金の細い繊維
    fibers = (0.5 + 0.5 * np.sin(ang * 28 + n * 3)) * smooth(1 - (r - 0.46) / 0.25)
    rgb = moon * (1 - 0.45 * fibers[..., None]) + np.array([240, 190, 90]) * (0.45 * fibers[..., None])
    pupil = r < 0.44
    rgb[pupil] = np.array([8, 8, 12])
    corona = (r >= 0.44) & (r < 0.5)
    rgb[corona] = np.array([255, 214, 120])
    # 外側は暗がりへ溶ける (くっきりした縁を作らない)
    edge = smooth((r - 0.78) / 0.18)
    rgb = rgb * (1 - edge[..., None]) + np.array([70, 70, 84]) * edge[..., None]
    a = 1 - smooth((r - 0.9) / 0.1)
    return rgba(quantize(rgb), a)


def sclera_c():
    """C 古き神の縦の瞳: 淡い金の白目。目尻へ、刻んだような細い金の線が流れる"""
    inside, u, v = almond(SW, SH)
    n = hash_noise(SW, SH, 5, 13)
    base = np.array([238, 214, 160], float)
    rgb = base * (1 - 0.4 * np.abs(u) ** 1.5)[..., None] * (0.9 + 0.1 * n[..., None])
    lines = (np.abs(np.sin(v * 9 + u * 3)) < 0.12) & (np.abs(u) > 0.45)
    rgb[lines] = np.array([170, 120, 40])
    a = smooth(inside / 0.08)
    return rgba(quantize(rgb), a)


def iris_c():
    ys, xs = np.mgrid[0:IR, 0:IR]
    x = (xs + 0.5) / IR * 2 - 1
    y = (ys + 0.5) / IR * 2 - 1
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    rings = 0.5 + 0.5 * np.cos(r * 38)
    rgb = np.array([250, 236, 190], float) * (0.75 + 0.25 * rings[..., None])
    rgb = rgb * (1 - 0.35 * r[..., None]) + np.array([200, 140, 40]) * (0.35 * r[..., None])
    # 輪の外側に、刻み目 (8 つ)
    notch = (r > 0.8) & (r < 0.9) & (np.abs(np.sin(ang * 4)) < 0.18)
    rgb[notch] = np.array([150, 95, 25])
    slit = (np.abs(x) < 0.09 * np.sqrt(np.clip(1 - (y / 0.82) ** 2, 0, 1)) + 0.01) & (np.abs(y) < 0.82)
    rgb[slit] = np.array([16, 10, 6])
    a = 1 - smooth((r - 0.94) / 0.06)
    return rgba(quantize(rgb), a)


DESIGNS = {
    "a": ("金環の瞳", sclera_a, iris_a),
    "b": ("月蝕の瞳", sclera_b, iris_b),
    "c": ("古き神の縦の瞳", sclera_c, iris_c),
}


# ---------------- 共通 (雲) ----------------

def lid():
    """上下の瞼の雲。上半分: 上端が厚い雲、下端 (縁) がほつれる。下半分はその上下逆"""
    w, h = 128, 32
    n = hash_noise(w, h, 7, 31) * 0.6 + hash_noise(w, h, 3, 32) * 0.4
    ys, xs = np.mgrid[0:h, 0:w]
    t = (ys + 0.5) / h  # 0 = 外側, 1 = 縁
    edge = 1 - smooth((t - 0.55 - (n - 0.5) * 0.5) / 0.25)
    horizontal = 1 - smooth((np.abs((xs + 0.5) / w * 2 - 1) - 0.72) / 0.28)
    alpha = edge * horizontal * (0.8 + 0.2 * n)
    alpha *= smooth(t / 0.15) * 0.25 + 0.75
    light = 0.75 + 0.25 * n
    top = rgba(np.repeat((light * 255)[..., None], 3, axis=2), alpha)
    bottom = top.transpose(Image.FLIP_TOP_BOTTOM)
    out = Image.new("RGBA", (w, h * 2))
    out.paste(top, (0, 0))
    out.paste(bottom, (0, h))
    return out


def lid_glow():
    w, h = 128, 16
    ys, xs = np.mgrid[0:h, 0:w]
    t = (ys + 0.5) / h
    n = hash_noise(w, h, 5, 33)
    x = np.abs((xs + 0.5) / w * 2 - 1)
    a = t ** 3.0 * (0.35 + 0.65 * smooth((n - 0.25) / 0.5))
    # 目頭・目尻ほど弱く (縁取りの線に見せない)
    a *= (1 - x ** 2) ** 1.5
    return rgba(np.full((h, w, 3), 255.0), a)


def patch():
    w, h = 64, 32
    ys, xs = np.mgrid[0:h, 0:w]
    x = (xs + 0.5) / w * 2 - 1
    y = (ys + 0.5) / h * 2 - 1
    n = hash_noise(w, h, 5, 41) * 0.6 + hash_noise(w, h, 2.5, 42) * 0.4
    r = np.hypot(x, y * 1.1)
    a = 1 - smooth((r - 0.45 - (n - 0.5) * 0.45) / 0.35)
    # 板の端では必ず透明にする (四角い縁を見せない)
    a *= (1 - smooth((np.abs(x) - 0.75) / 0.25)) * (1 - smooth((np.abs(y) - 0.7) / 0.3))
    light = 0.8 + 0.2 * n
    return rgba(np.repeat((light * 255)[..., None], 3, axis=2), a)


# ---------------- 夜空での見え方 (CloudEyeRenderer と同じ組み立て) ----------------

def sample(img, tu, tv):
    arr = np.asarray(img).astype(float) / 255
    h, w = arr.shape[:2]
    ix = np.clip((tu * w).astype(int), 0, w - 1)
    iy = np.clip((tv * h).astype(int), 0, h - 1)
    return arr[iy, ix]


def over(dst, rgb, a):
    dst[...] = dst * (1 - a[..., None]) + rgb * a[..., None]


def render(design, open_, iris=(0.0, 0.0), size=(520, 300), width_px=300, glow_strength=1.0):
    """夜空に雲の目を一つ描く。width_px = 目の幅の画素数"""
    _, sclera_fn, iris_fn = DESIGNS[design]
    sclera, iris_img = sclera_fn(), iris_fn()
    lid_img, glow_img, patch_img = lid(), lid_glow(), patch()
    W, Hh = size
    ys, xs = np.mgrid[0:Hh, 0:W]
    yy = (Hh / 2 - ys - 0.5)
    sky = np.zeros((Hh, W, 3))
    sky[...] = np.array([6, 8, 18]) / 255 + (ys / Hh)[..., None] * (np.array([16, 20, 36]) / 255)
    # 星
    stars = rng.random((Hh, W)) > 0.9975
    sky[stars] = 0.7
    width = float(width_px)
    u = (xs - W / 2 + 0.5)
    v = yy
    halfH = width * HALF_HEIGHT
    cloud = np.array([30, 31, 40]) / 255
    glow = min(1.0, open_ * 1.15) * glow_strength

    # 1. 雲のかたまり
    m = (np.abs(u) < width * 1.25) & (np.abs(v) < width * 0.62)
    px = sample(patch_img, (u + width * 1.25) / (width * 2.5), (width * 0.62 - v) / (width * 1.24))
    a = np.where(m, px[..., 3] * 0.8, 0)
    over(sky, px[..., :3] * cloud * 0.95, a)

    t = 2 * u / width
    shape = np.clip(1 - t * t, 0, None) ** 0.72
    ap = open_ * halfH * shape
    inside = (np.abs(v) < ap) & (np.abs(u) < width / 2)
    if open_ > 0:
        s = 0.32 + 0.5 * glow
        px = sample(sclera, (u + width / 2) / width, (halfH - v) / (2 * halfH))
        over(sky, px[..., :3] * np.array([s, s * 0.97, s * 0.9]), np.where(inside, px[..., 3] * min(1, open_ * 4), 0))
        r = halfH * IRIS_RADIUS
        ix = iris[0] * width * IRIS_TRAVEL_X
        iy = iris[1] * halfH * IRIS_TRAVEL_Y
        m = inside & (np.abs(u - ix) < r) & (np.abs(v - iy) < r)
        px = sample(iris_img, (u - ix + r) / (2 * r), (iy + r - v) / (2 * r))
        ib = 0.42 + 0.58 * glow
        over(sky, px[..., :3] * ib, np.where(m, px[..., 3] * min(1, open_ * 4), 0))

    # 4. 瞼の雲
    reach = width * LID_REACH
    thick = halfH * LID_THICKNESS
    tu = (u + reach) / (2 * reach)
    upper = (v >= ap) & (v < ap + thick) & (np.abs(u) < reach)
    lower = (v <= -ap) & (v > -ap - thick) & (np.abs(u) < reach)
    tv_u = 0.5 - (v - ap) / thick * 0.5
    tv_l = 0.5 + (-ap - v) / thick * 0.5
    px_u = sample(lid_img, tu, np.clip(tv_u, 0, 0.999))
    px_l = sample(lid_img, tu, np.clip(tv_l, 0, 0.999))
    lc = cloud * np.array([1.05, 1.05, 1.08])
    over(sky, px_u[..., :3] * lc, np.where(upper, px_u[..., 3] * 0.95, 0))
    over(sky, px_l[..., :3] * lc, np.where(lower, px_l[..., 3] * 0.95, 0))
    # 縁の光 (足し合わせ)
    if open_ > 0:
        gt = halfH * 0.45
        gu = (u + width / 2) / width
        up_g = (v >= ap - gt * 0.25) & (v < ap + gt) & (np.abs(u) < width / 2)
        lo_g = (v <= -ap + gt * 0.25) & (v > -ap - gt) & (np.abs(u) < width / 2)
        tvu = 1 - (v - (ap - gt * 0.25)) / (gt * 1.25)
        tvl = 1 - ((-ap + gt * 0.25) - v) / (gt * 1.25)
        gold = np.array([1.0, 0.78, 0.42])
        for mask, tv in ((up_g, tvu), (lo_g, tvl)):
            px = sample(glow_img, gu, np.clip(tv, 0, 0.999))
            sky += np.where(mask, px[..., 3] * glow * GLOW_ALPHA, 0)[..., None] * gold
    return Image.fromarray((np.clip(sky, 0, 1) * 255).astype(np.uint8), "RGB")


def label(img, text, font):
    d = ImageDraw.Draw(img)
    d.text((8, 6), text, fill=(220, 220, 220), font=font)
    return img


def get_font(size):
    for name in ["C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"]:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def sheet():
    font = get_font(16)
    cols = [(0.3, (0.5, -0.7), "開きかけ"), (1.0, (0.5, -0.55), "地上を探す"), (1.0, (0.0, 0.0), "こちらを見る")]
    cw, ch = 420, 240
    out = Image.new("RGB", (cw * 4, ch * 3 + 30), (20, 20, 24))
    d = ImageDraw.Draw(out)
    for row, key in enumerate(DESIGNS):
        name = DESIGNS[key][0]
        # 近景 (目の幅 330 画素。視線が合った瞬間)
        cell = render(key, 1.0, (0.0, 0.0), size=(cw, ch), width_px=330)
        label(cell, f"{key.upper()} {name}（近景）", font)
        out.paste(cell, (0, row * ch + 30))
        for col, (o, iris, what) in enumerate(cols):
            img = render(key, o, iris, size=(cw, ch), width_px=200)
            label(img, f"{key.upper()} {what}", font)
            out.paste(img, ((col + 1) * cw, row * ch + 30))
    d.text((8, 6), "雲の切れ間の目 ラフ3案（夜空、目の幅200画素 ≒ 1080p・視野70度で170ブロック先・幅44ブロック）", fill=(230, 230, 230), font=font)
    return out


def night_view():
    """ゲームの画面に近い大きさ: 1280x720、目の幅は 1080p 換算で 44 ブロック / 170 ブロック先 (≒ 視野の15度)"""
    font = get_font(18)
    out = Image.new("RGB", (1280, 720 * 3 // 2), (0, 0, 0))
    w_px = int(1280 * (2 * math.degrees(math.atan(22 / 170))) / (70 * 16 / 9))
    for i, key in enumerate(DESIGNS):
        img = render(key, 1.0, (0.0, 0.0), size=(1280 // 3, 720 // 2 * 3 // 2), width_px=w_px)
        label(img, f"{key.upper()} {DESIGNS[key][0]}", font)
        out.paste(img, (i * (1280 // 3), 0))
    return out


def main():
    install = "--install" in sys.argv
    DOCS.mkdir(parents=True, exist_ok=True)
    for key, (_, s_fn, i_fn) in DESIGNS.items():
        d = DOCS / key
        d.mkdir(exist_ok=True)
        s_fn().save(d / "sclera.png")
        i_fn().save(d / "iris.png")
    lid().save(DOCS / "lid.png")
    lid_glow().save(DOCS / "lid_glow.png")
    patch().save(DOCS / "patch.png")
    sheet().save(DOCS / "sky_eye_roughs.png")
    night_view().save(DOCS / "sky_eye_night.png")
    print("roughs written to", DOCS)
    if install:
        SRC.mkdir(parents=True, exist_ok=True)
        _, s_fn, i_fn = DESIGNS[CHOSEN]
        s_fn().save(SRC / "sclera.png")
        i_fn().save(SRC / "iris.png")
        lid().save(SRC / "lid.png")
        lid_glow().save(SRC / "lid_glow.png")
        patch().save(SRC / "patch.png")
        print("installed design", CHOSEN, "to", SRC)


if __name__ == "__main__":
    main()
