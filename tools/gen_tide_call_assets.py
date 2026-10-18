"""潮殻の呼び貝 (skyabyss:tide_shell_call) の素材を作る。潮殻の巨神を海辺で呼ぶ、巻貝の角笛。

使い方:
    python tools/gen_tide_call_assets.py            ラフ 3 案 (docs/review/tide_call_roughs/) と、採用案の素材を src へ書く
    python tools/gen_tide_call_assets.py --roughs   ラフだけ (src へは書かない)

出力 (src/main/resources/assets/skyabyss/ 以下):
    - textures/item/tide_shell_call.png          呼び貝の絵 (32x32)。案 A/B/C から ADOPTED_CALL
    - models/item/tide_shell_call.json           parent minecraft:item/generated、layer0 は上の絵
    - sounds/titan/travel/tide_call.ogg          深い巻貝の角笛 (約 3 秒、息と遠いこだま)
    - sounds/titan/travel/tide_depart.ogg        巨神が歩き出す、大きな波と低いうめき (約 3.5 秒)
    - sounds/titan/travel/tide_current.ogg       深い潮流へ潜る、水中のうなりと泡 (約 3 秒)

色は潮殻の殻片 (tide_shell_shard.png) と真珠、珊瑚の淡い色、碑文の青緑 (gen_forge_assets.py の TEAL)。
岩鯨の呼び笛 (骨の角笛) の形は真似ない。案 A は巻貝、B は甲羅の欠片、C は細長い錐貝。
何度実行しても同じ物ができる (乱数の種を固定)。
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_forge_assets import OUTLINE, TEAL, TEAL_DIM, PALE  # noqa: E402
from gen_titan_travel_assets import outline, tube, bezier  # noqa: E402
import gen_sky_sounds as G  # noqa: E402
from gen_sky_sounds import SR, band_noise, env, finish, reverb, t_axis, write_ogg  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
ROOT = REPO / "src/main/resources/assets/skyabyss"
ROUGHS = REPO / "docs/review/tide_call_roughs"
ITEM_TEX = ROOT / "textures/item/tide_shell_call.png"
ITEM_MODEL = ROOT / "models/item/tide_shell_call.json"
SOUND_OUT = ROOT / "sounds/titan/travel"

G.rng = np.random.default_rng(20261019)
rng = G.rng

# 採用した案 (ラフの比較は docs/review/tide_call_roughs/compare_call.png)
ADOPTED_CALL = "a"

# 潮殻の殻片と真珠と珊瑚の色 (tide_shell_shard.png / tide_pearl.png に合わせる)
SEA_LIGHT = (96, 186, 178, 255)
SEA = (56, 134, 132, 255)
SEA_DARK = (28, 80, 84, 255)
SEA_DEEP = (18, 52, 56, 255)
MOSS_LIGHT = (110, 146, 92, 255)
MOSS = (86, 120, 80, 255)
MOSS_DARK = (52, 76, 54, 255)
KELP = (72, 150, 72, 255)
KELP_LIGHT = (128, 196, 100, 255)
SHELL_LIGHT = (240, 226, 204, 255)
SHELL = (218, 200, 170, 255)
SHELL_MID = (184, 164, 134, 255)
SHELL_DARK = (132, 114, 96, 255)
CORAL = (232, 126, 118, 255)
CORAL_LIGHT = (246, 178, 166, 255)
CORAL_DARK = (170, 80, 84, 255)
PEARL = (236, 242, 240, 255)
PEARL_MID = (180, 198, 202, 255)
PEARL_DARK = (120, 140, 150, 255)
HOLLOW = (26, 22, 24, 255)
GLOW_HI = (170, 238, 255, 255)

SEA_SET = {SEA_LIGHT, SEA, SEA_DARK}


# ---------------------------------------------------------------- 小物

def shell_shade(t, lit):
    if lit > 0.45:
        return SHELL_LIGHT
    if lit > 0.0:
        return SHELL
    if lit > -0.5:
        return SHELL_MID
    return SHELL_DARK


def sea_shade(t, lit):
    if lit > 0.45:
        return SEA_LIGHT
    if lit > -0.1:
        return SEA
    return SEA_DARK


def along(path, radii, t):
    """弧長で t (0 = 先, 1 = 口) の位置、接線 (単位)、半径を返す (tube と同じ向き)"""
    pts = np.array(path, dtype=float)
    seg = np.diff(pts, axis=0)
    L = np.hypot(seg[:, 0], seg[:, 1])
    acc = np.concatenate([[0], np.cumsum(L)])
    s = t * acc[-1]
    i = int(np.clip(np.searchsorted(acc, s, side="right") - 1, 0, len(L) - 1))
    u = (s - acc[i]) / L[i]
    p = pts[i] + seg[i] * u
    tan = seg[i] / L[i]
    r = float(np.interp(t, np.linspace(0, 1, len(radii)), radii))
    return p, tan, r


def across(d, path, radii, t, color, half=1.0, width=1, bow=0.0):
    """体を横切る筋 (巻きの筋や紐)。bow > 0 で先の方へ反らせて、巻きの線に見せる"""
    p, tan, r = along(path, radii, t)
    nrm = np.array([-tan[1], tan[0]])
    a = p + nrm * (r + 0.6) + tan * half
    b = p - nrm * (r + 0.6) - tan * half
    m = p - tan * bow
    for k in range(width):
        off = tan * k
        d.line([tuple(a + off), tuple(m + off), tuple(b + off)], fill=color)


def mouth(img, path, radii, rim, inner, rim_w=1.0):
    """口 (体の終わり) を楕円で描く。楕円は断面に沿う"""
    d = ImageDraw.Draw(img)
    p, tan, r = along(path, radii, 1.0)
    nrm = np.array([-tan[1], tan[0]])

    def ell(R, hw, col):
        pts = []
        for k in range(36):
            a = k / 36 * 2 * math.pi
            q = p + nrm * R * math.cos(a) + tan * hw * math.sin(a)
            pts.append((q[0], q[1]))
        d.polygon(pts, fill=col)

    ell(r, rim_w * 1.6, rim)
    ell(r * 0.66, rim_w * 1.0, inner)


def fill_ellipse(d, cx, cy, r, fill, edge=None):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=edge)


def sea_scutes(img):
    """甲羅の欠片の六角の鱗。縁は暗く、奇数の鱗は藻の緑にする"""
    px = img.load()
    for y in range(32):
        row = y // 3
        off = 2 if row % 2 else 0
        for x in range(32):
            c = px[x, y]
            if c[3] == 0 or c not in SEA_SET:
                continue
            xx = x + off
            if (xx % 4 == 0) or (y % 3 == 0):
                px[x, y] = {SEA_LIGHT: SEA, SEA: SEA_DARK, SEA_DARK: SEA_DEEP}[c]
            elif ((xx // 4) + row) % 3 == 0:
                px[x, y] = {SEA_LIGHT: MOSS_LIGHT, SEA: MOSS, SEA_DARK: MOSS_DARK}[c]


# ---------------------------------------------------------------- 3 案

def call_a():
    """A: 渦を巻く巻貝。潮殻の白い体に珊瑚色の口、青緑の波の帯が 2 本、先に小さな真珠の吹き口"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    path = bezier((7, 7), (6, 22), (22, 24), 14)
    radii = [1.5, 2.4, 3.8, 5.0, 6.2]
    tube(img, path, radii, shell_shade, bands=((0.16, 0.22), (0.44, 0.5)), band_color=TEAL)
    d = ImageDraw.Draw(img)
    for t in (0.3, 0.52, 0.72, 0.88):
        across(d, path, radii, t, SHELL_DARK, half=0.5, bow=1.2)
    mouth(img, path, radii, CORAL_LIGHT, CORAL_DARK)
    # 吹き口の真珠
    fill_ellipse(d, 6, 6, 2.2, PEARL_MID, PEARL_DARK)
    d.point((5, 5), fill=PEARL)
    return outline(img)


def call_b():
    """B: 潮殻の甲羅の欠片を巻いた円錐。六角の鱗 (青緑と藻の緑)、先に真珠の吹き口、口は藻の縁"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    path = bezier((8, 8), (9, 21), (24, 24), 14)
    radii = [2.0, 2.8, 4.0, 5.2, 6.2]
    tube(img, path, radii, sea_shade)
    sea_scutes(img)
    d = ImageDraw.Draw(img)
    mouth(img, path, radii, MOSS, HOLLOW)
    fill_ellipse(d, 7, 7, 2.6, PEARL, PEARL_DARK)
    d.point((6, 6), fill=(255, 255, 255, 255))
    return outline(img)


def call_c():
    """C: 細長い錐貝 (オーガー)。白い体に暗い筋、藻の紐を二回巻く、体の中を青緑の光る螺旋が登る"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    path = bezier((6, 5), (10, 20), (25, 25), 14)
    radii = [1.0, 1.7, 2.4, 3.0, 3.3]
    tube(img, path, radii, shell_shade)
    d = ImageDraw.Draw(img)
    for t in (0.2, 0.36, 0.52, 0.68, 0.84):
        across(d, path, radii, t, SHELL_DARK, half=0.3, bow=0.6)
    for t in (0.3, 0.62):
        across(d, path, radii, t, KELP, half=1.6, width=2, bow=0.5)
    d.point(tuple(along(path, radii, 0.62)[0].round().astype(int)), fill=KELP_LIGHT)
    # 光る螺旋 (体の表側にだけ見える)
    for t in np.linspace(0.06, 0.94, 260):
        p, tan, r = along(path, radii, t)
        nrm = np.array([-tan[1], tan[0]])
        th = 2 * math.pi * t * 3.2
        if math.cos(th) > 0.1:
            q = p + nrm * r * 0.7 * math.sin(th)
            x, y = int(q[0]), int(q[1])
            if 0 <= x < 32 and 0 <= y < 32:
                img.putpixel((x, y), GLOW_HI if math.sin(th) > 0.6 else TEAL)
    mouth(img, path, radii, SHELL_MID, HOLLOW, rim_w=0.8)
    return outline(img)


CALLS = {"a": call_a, "b": call_b, "c": call_c}


# ---------------------------------------------------------------- ラフの比較

def write_roughs():
    ROUGHS.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGBA", (3 * 296, 330), (34, 37, 44, 255))
    d = ImageDraw.Draw(sheet)
    for i, (k, fn) in enumerate(CALLS.items()):
        icon = fn()
        icon.save(ROUGHS / f"call_{k}.png")
        big = icon.resize((256, 256), Image.NEAREST)
        sheet.paste(big, (i * 296 + 20, 20), big)
        # ゲーム内の大きさ (1 倍と 2 倍)
        sheet.paste(icon, (i * 296 + 20, 290), icon)
        half = icon.resize((64, 64), Image.NEAREST)
        sheet.paste(half, (i * 296 + 60, 262), half)
        d.text((i * 296 + 240, 296), k.upper(), fill=(226, 217, 194, 255))
    sheet.save(ROUGHS / "compare_call.png")
    print("roughs ->", ROUGHS)


# ---------------------------------------------------------------- 音

def lowpass(x, hz):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec / (1 + (f / hz) ** 4), len(x))


def bubbles(t, rate, lo, hi, dur=0.05, amp=1.0):
    """泡の粒: 上へ滑る短い正弦波を散らす"""
    out = np.zeros_like(t)
    n = int(rate * t[-1])
    for _ in range(n):
        t0 = rng.uniform(0, t[-1] - dur)
        f0 = rng.uniform(lo, hi)
        i0 = int(t0 * SR)
        k = np.arange(int(dur * SR)) / SR
        f = f0 * (1 + 2.5 * k / dur)
        seg = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-k / (dur * 0.35)) * rng.uniform(0.3, 1.0)
        out[i0:i0 + len(seg)] += seg[:len(out) - i0]
    return out * amp


def sound_tide_call(_rng=None):
    """深い巻貝の角笛: 息を吸う音、低く伸びる一声、遠くの山から返る小さな応え"""
    t = t_axis(3.0)
    n = len(t)
    f = 92 * (1 + 0.025 * np.clip((t - 0.25) / 0.5, 0, 1)) * (1 + 0.005 * np.sin(2 * np.pi * 5.0 * t))
    phase = 2 * np.pi * np.cumsum(f) / SR
    horn = sum(np.sin(h * phase) / (h ** (0.9 if h % 2 else 1.7)) for h in range(1, 11))
    horn = lowpass(horn, 360)
    blast = horn * env(t, [(0, 0), (0.35, 1.0), (2.1, 0.9), (2.5, 0.35), (3.0, 0)])
    breath = band_noise(n, 180, 1100) * env(t, [(0, 0.5), (0.3, 0.9), (1.0, 0.5), (2.2, 0.45), (2.8, 0), (3.0, 0)]) * 0.35
    # 遠くの応え (低く、少し遅れ、こもらせる)
    t2 = np.clip(t - 2.3, 0, None)
    phase2 = 2 * np.pi * np.cumsum(f * 0.75) / SR
    answer = sum(np.sin(h * phase2) / (h ** 1.6) for h in range(1, 5))
    answer = lowpass(answer, 260) * env(t, [(0, 0), (2.3, 0), (2.55, 0.9), (2.9, 0.5), (3.0, 0)]) * 0.35
    dry = blast * 0.75 + breath + answer
    return finish(reverb(dry, 2.6, 0.45)[:n], -2.0)


def sound_tide_depart(_rng=None):
    """巨神が歩き出す: 寄せる大きな波、海底をこする低いうめき、泡"""
    t = t_axis(3.5)
    n = len(t)
    wave = lowpass(band_noise(n, 60, 1100), 1400) * env(t, [(0, 0), (0.9, 0.6), (1.8, 1.0), (2.7, 0.55), (3.5, 0)])
    spray = band_noise(n, 1500, 6000) * env(t, [(0, 0), (0.8, 0.2), (1.8, 0.5), (2.8, 0.2), (3.5, 0)]) * 0.25
    f = 40 + 12 * np.clip(t / 3.5, 0, 1)
    phase = 2 * np.pi * np.cumsum(f) / SR
    groan = sum(np.sin(h * phase + 0.2 * h) / (h ** 1.2) for h in range(1, 6))
    groan = lowpass(groan, 300) * env(t, [(0, 0), (0.5, 0.4), (1.4, 0.9), (2.6, 0.7), (3.3, 0.2), (3.5, 0)]) * 0.8
    grind = band_noise(n, 120, 500) * (0.5 + 0.5 * np.sin(2 * np.pi * 3.0 * t)) * env(t, [(0, 0), (0.6, 0.5), (2.5, 0.6), (3.5, 0)]) * 0.3
    bub = bubbles(t, 6, 200, 500, amp=0.15) * env(t, [(0, 0), (0.5, 1), (2.8, 0.6), (3.5, 0)])
    dry = wave * 0.8 + spray + groan + grind + bub
    return finish(reverb(dry, 2.5, 0.35)[:n], -2.0)


def sound_tide_current(_rng=None):
    """深い潮流へ潜る: 下へ沈むように細る水の唸り、泡の列、低い地鳴り"""
    t = t_axis(3.0)
    n = len(t)
    chunks = 40
    size = n // chunks
    out = np.zeros(n)
    for i in range(chunks):
        fc = 1400 * (1 - i / chunks) ** 1.5 + 250
        seg = band_noise(size * 2, fc * 0.6, fc * 1.4)[:size]
        out[i * size:(i + 1) * size] = seg * np.hanning(size) * 1.6
    rush = out * env(t, [(0, 0), (0.6, 1.0), (2.2, 0.8), (3.0, 0)])
    bub = bubbles(t, 30, 250, 1100, amp=0.3) * env(t, [(0, 0), (0.3, 1), (2.5, 0.8), (3.0, 0)])
    rumble = lowpass(band_noise(n, 35, 90), 200) * env(t, [(0, 0), (0.5, 0.7), (2.4, 0.9), (3.0, 0)]) * 0.8
    hum = np.sin(2 * np.pi * 42 * t) * env(t, [(0, 0), (0.6, 0.3), (2.6, 0.4), (3.0, 0)]) * 0.3
    dry = rush * 0.6 + bub + rumble + hum
    return finish(reverb(dry, 2.5, 0.4)[:n], -3.0)


SOUNDS = (("tide_call", sound_tide_call), ("tide_depart", sound_tide_depart), ("tide_current", sound_tide_current))


# ---------------------------------------------------------------- 書き出し

def install():
    ITEM_TEX.parent.mkdir(parents=True, exist_ok=True)
    CALLS[ADOPTED_CALL]().save(ITEM_TEX)
    ITEM_MODEL.parent.mkdir(parents=True, exist_ok=True)
    model = {"parent": "minecraft:item/generated", "textures": {"layer0": "skyabyss:item/tide_shell_call"}}
    ITEM_MODEL.write_text(json.dumps(model, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    SOUND_OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS:
        data = fn().astype(np.float32)
        write_ogg(SOUND_OUT / f"{name}.ogg", data)
        print(f"{name}.ogg  {len(data) / SR:.2f}s")
    print("installed call", ADOPTED_CALL)


if __name__ == "__main__":
    write_roughs()
    if "--roughs" not in sys.argv:
        install()
