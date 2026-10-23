"""天の鍵 (四つの欠片が一つになった鍵) の絵と、鍵がそろう場面の音を作る。docs/design/14_beta.md の 4 章。

実行 (リポジトリ直下):
    python tools/gen/story/gen_story_assets.py                 3 案の比較シートと、選んだ案 (既定 C) の絵、音を書き出す
    python tools/gen/story/gen_story_assets.py --option B      B を入れる
出力:
    docs/review/sky_key_roughs/compare.png                     3 案の比較 (32px を 8 倍、欠片と並べて)
    src/main/resources/assets/skyabyss/textures/item/sky_key.png
    src/main/resources/assets/skyabyss_story/sounds/*.ogg      合成した音 (既存の音は使わない。01 の決定)
    src/main/resources/assets/skyabyss_story/sounds.json
"""
from __future__ import annotations
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../sound',)]

import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
TEX = ROOT / "src/main/resources/assets/skyabyss/textures/item"
REVIEW = ROOT / "docs/review/sky_key_roughs"
SND = ROOT / "src/main/resources/assets/skyabyss_story/sounds"

OUTLINE = (34, 38, 46, 255)
STONE = [(92, 98, 108), (128, 134, 142), (164, 168, 174), (198, 200, 204)]
# 四つの巨神の色 (岩鯨の青、潮殻の青緑、雷翼の淡い黄、余燼の熾火)
TITAN = [(64, 196, 238), (70, 214, 176), (238, 226, 120), (240, 128, 64)]
SEAM = (236, 250, 255)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def outline(img):
    a = np.array(img)
    alpha = a[:, :, 3] > 0
    out = a.copy()
    h, w = alpha.shape
    for y in range(h):
        for x in range(w):
            if alpha[y, x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and alpha[ny, nx]:
                    out[y, x] = OUTLINE
                    break
    return Image.fromarray(out)


def stone_px(x, y, cx, cy, r):
    # 左上から光が当たる石の陰影
    d = ((x - cx) * -0.7 + (y - cy) * -0.7) / max(r, 1)
    i = 1 if d < -0.25 else 2 if d < 0.35 else 3
    if (x * 7 + y * 13) % 11 == 0:
        i = max(0, i - 1)
    return STONE[i] + (255,)


def disc_option(img, cx, cy, r, hole):
    """A: 四つの欠片が組み合わさった円盤。継ぎ目が十字に光り、真ん中に小さな穴"""
    p = img.load()
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r or d < hole:
                continue
            q = (0 if dx < 0 else 1) + (0 if dy < 0 else 2)
            seam = abs(dx) < 0.9 or abs(dy) < 0.9
            if seam:
                p[x, y] = SEAM + (255,)
                continue
            # 欠片ごとの象嵌 (中ほどの輪に色)
            if r * 0.45 < d < r * 0.78:
                p[x, y] = shade(TITAN[q], 1.0 if (dx + dy) < 0 else 0.82)
            else:
                p[x, y] = stone_px(x, y, cx, cy, r)


def option_a():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    disc_option(img, 16, 16, 13.2, 1.6)
    return outline(img)


def option_b():
    """B: 鍵の形。輪 (四色の欠片の環) と柄、先の歯。柄は斜め下へ"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = img.load()
    cx, cy, r = 11, 11, 8.6
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if 3.2 < d <= r:
                ang = (math.atan2(dy, dx) + math.pi * 1.25) % (2 * math.pi)
                q = int(ang / (math.pi / 2)) % 4
                edge = abs((ang % (math.pi / 2))) < 0.18
                p[x, y] = (SEAM + (255,)) if edge else shade(TITAN[q], 0.95 if d < 6 else 0.78)
    # 柄 (斜め 45 度、右下へ。太さ 3 画素)
    for y in range(32):
        for x in range(32):
            if p[x, y][3] == 0 and abs((x - y)) <= 1 and 17 <= x + y <= 56:
                p[x, y] = stone_px(x, y, 22, 22, 10)
    # 歯
    for (x, y) in ((24, 28), (25, 28), (25, 29), (28, 24), (28, 25), (29, 25), (21, 26), (22, 27)):
        if 0 <= x < 32 and 0 <= y < 32:
            p[x, y] = stone_px(x, y, 22, 22, 10)
    return outline(img)


def option_c():
    """C: 円盤 (A を小さく) から、下へ向かって一本の柄が伸びる。鍵が足もとを指す"""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    disc_option(img, 16, 11, 9.4, 1.2)
    p = img.load()
    for y in range(20, 31):
        for x in (15, 16):
            p[x, y] = stone_px(x, y, 16, 24, 6) if y < 29 else (SEAM + (255,))
        if y in (25, 28):
            p[17, y] = stone_px(17, y, 16, 24, 6)
            p[18, y] = stone_px(18, y, 16, 24, 6)
    # 柄の芯に淡い光
    for y in range(21, 29, 2):
        p[16, y] = shade(SEAM, 0.9)
    return outline(img)


OPTIONS = {"A": option_a, "B": option_b, "C": option_c}
LABELS = {"A": "A 四つが組んだ円盤", "B": "B 四色の輪の鍵", "C": "C 下を指す鍵"}


def compare_sheet():
    REVIEW.mkdir(parents=True, exist_ok=True)
    frag = Image.open(TEX / "sky_key_fragment.png").convert("RGBA")
    scale = 8
    cell = 32 * scale + 24
    sheet = Image.new("RGBA", (cell * 4 + 24, cell + 60), (40, 44, 52, 255))
    d = ImageDraw.Draw(sheet)
    items = [("欠片 (今)", frag)] + [(LABELS[k], OPTIONS[k]()) for k in "ABC"]
    for i, (name, im) in enumerate(items):
        x0 = 24 + i * cell
        sheet.paste(im.resize((32 * scale, 32 * scale), Image.NEAREST), (x0, 24), im.resize((32 * scale, 32 * scale), Image.NEAREST))
        d.text((x0, 32 * scale + 32), name.encode("ascii", "replace").decode() if False else ["fragment", "A disc", "B key", "C down"][i],
               fill=(220, 220, 230, 255))
    sheet.save(REVIEW / "compare.png")


# ---------------------------------------------------------------- 音
def sounds():
    sys.path.insert(0, str(ROOT / "tools"))
    import gen_sky_sounds as G  # noqa: E402
    SND.mkdir(parents=True, exist_ok=True)
    SR = G.SR

    # 欠片が触れ合い、一つになる: 四つの澄んだ鳴りが寄り合って一つの和音になり、低い響きが残る
    t = G.t_axis(4.5)
    x = np.zeros_like(t)
    base = [523.25, 659.25, 783.99, 987.77]
    for i, f in enumerate(base):
        t0 = 0.15 + i * 0.28
        e = np.where(t > t0, np.exp(-(t - t0) * 1.6), 0.0)
        x += 0.22 * e * np.sin(2 * np.pi * f * t) * (1 + 0.3 * np.sin(2 * np.pi * 5.0 * t))
    t1 = 1.45
    e2 = np.where(t > t1, np.exp(-(t - t1) * 0.7), 0.0)
    for f in (261.63, 392.0, 523.25):
        x += 0.18 * e2 * np.sin(2 * np.pi * f * t)
    x += 0.35 * e2 * np.sin(2 * np.pi * 55.0 * t) * np.exp(-(t - t1).clip(0) * 0.3)
    x = G.reverb(x, seconds=3.0, mix=0.4)
    G.write_ogg(SND / "key_form.ogg", G.finish(x[: len(t)], peak_db=-3.0))

    # 足もとのずっと下で打つもの: くぐもった二拍 (遠い)
    t = G.t_axis(2.4)
    x = np.zeros_like(t)
    for t0, a in ((0.1, 1.0), (0.42, 0.7)):
        e = np.where(t > t0, np.exp(-(t - t0) * 9.0), 0.0)
        x += a * e * np.sin(2 * np.pi * (38 + 20 * np.exp(-(t - t0).clip(0) * 20)) * t)
    x += 0.15 * G.band_noise(len(t), 30, 120) * np.exp(-t * 1.5)
    x = G.reverb(x, seconds=2.0, mix=0.3)
    G.write_ogg(SND / "deep_beat.ogg", G.finish(x[: len(t)], peak_db=-4.0))

    js = {
        "key_form": {"subtitle": "subtitles.skyabyss_story.key_form", "sounds": [{"name": "skyabyss_story:key_form", "stream": False}]},
        "deep_beat": {"subtitle": "subtitles.skyabyss_story.deep_beat", "sounds": [{"name": "skyabyss_story:deep_beat", "stream": False}]},
    }
    (SND.parent / "sounds.json").write_text(json.dumps(js, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", default="C", choices=list(OPTIONS))
    ap.add_argument("--no-sound", action="store_true")
    args = ap.parse_args()
    compare_sheet()
    OPTIONS[args.option]().save(TEX / "sky_key.png")
    if not args.no_sound:
        sounds()
    print("sky key option", args.option)


if __name__ == "__main__":
    main()
