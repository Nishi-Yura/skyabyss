"""余燼の三つの試練 (灰の道・鍛えの間・火守り) の 32px の絵: ブロックの面と品のアイコンを、3 案 (A/B/C) で描く。

13 の 4 章・11.2節の担当 C1・C2・C3。乱数の種は固定 (何度書いても同じ)。

  A = 層の岩 + 細い割れ目: 玄武岩の地層の横筋と、細い割れ目の光。灰色がかった黒に、橙〜黄の火。余燼の背の壁 (玄武岩・黒石) と同じ系統
  B = 柱状節理: 六角に近い柱の断面が並び、継ぎ目が赤く光る。火が強い (赤の暖色寄り)
  C = 灰まみれ: 青みの灰色の岩に、白い灰の粒が多い。火は小さく、低い温度の色

使い方 (リポジトリ直下):
  python tools/rough/ember_trial.py            # ラフを docs/review/ember_trial_roughs/ に書く (比較シート compare_*.png)
  python tools/rough/ember_trial.py --install  # 推奨案 (RECOMMEND) を src の assets に書く (モデル・状態の JSON も)
  python tools/rough/ember_trial.py --install --option cooled_crust=b   # 案を指定 (複数可)
  python tools/rough/ember_trial.py --install --from-sources            # tools/sources/ember_trial/ から書き直す

出力:
  docs/review/ember_trial_roughs/<名前>/<a|b|c>.png                  (各案。採用外も残す)
  docs/review/ember_trial_roughs/compare_<グループ>.png               (比較シート。左から A B C、4 倍)
  tools/sources/ember_trial/<名前>.png                                 (採用した案の絵。再生成の入力)
  src/main/resources/assets/skyabyss/{textures/block,textures/item,models/block,models/item,blockstates}/...
"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
REVIEW = ROOT / "docs/review/ember_trial_roughs"
SOURCES = ROOT / "tools/sources/ember_trial"
N = 32
STYLES = ["a", "b", "c"]


def hx(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(c1, c2, t):
    return tuple(int(round(a * (1 - t) + b * t)) for a, b in zip(c1, c2))


# ---------------------------------------------------------------- 色 (案ごと)
PAL = {
    "a": dict(rock=["#1b191d", "#25222a", "#302c35", "#3b3641"], dust="#6f6862", ember=["#6e1c0e", "#c4410f", "#f0891c", "#ffd25a"],
              white="#fff4d2", iron=["#2b2d33", "#41444d", "#5a5e69", "#7d828f"], water=["#1f5a8a", "#2f7fb8", "#5fb3e0"]),
    "b": dict(rock=["#231613", "#30201b", "#3e2a22", "#4d3429"], dust="#7a6a60", ember=["#8a1c0a", "#d6430c", "#ff8a14", "#ffe066"],
              white="#fff0c0", iron=["#2f2a28", "#473f3b", "#625852", "#8a7d72"], water=["#1b5273", "#2a76a3", "#58a8d0"]),
    "c": dict(rock=["#2d2e38", "#393b47", "#464857", "#555869"], dust="#9a9aa4", ember=["#5a1a14", "#b8421a", "#e8801f", "#f8cf6a"],
              white="#f4efe0", iron=["#33363e", "#4a4e59", "#666b78", "#8d93a1"], water=["#27608c", "#3a86b6", "#6cb8dd"]),
}


def p(style, key, i=None):
    v = PAL[style][key]
    if isinstance(v, list):
        return hx(v[i])
    return hx(v)


class Canvas:
    def __init__(self, fill=(0, 0, 0, 0)):
        self.a = np.zeros((N, N, 4), np.uint8)
        self.a[:, :] = fill if len(fill) == 4 else (*fill, 255)

    def px(self, x, y, c):
        if 0 <= x < N and 0 <= y < N:
            self.a[y, x] = (*c[:3], c[3] if len(c) > 3 else 255)

    def rect(self, x1, y1, x2, y2, c):
        for y in range(y1, y2 + 1):
            for x in range(x1, x2 + 1):
                self.px(x, y, c)

    def get(self, x, y):
        return tuple(int(v) for v in self.a[y, x])

    def image(self):
        return Image.fromarray(self.a, "RGBA")


# ---------------------------------------------------------------- 岩の地
def rock(style, seed, light=0.0):
    """岩の面。A 地層、B 柱状、C 灰の粒"""
    rng = np.random.default_rng(seed)
    cv = Canvas((*p(style, "rock", 1), 255))
    pal = [p(style, "rock", i) for i in range(4)]
    if style == "a":
        for y in range(N):
            base = pal[0] if y % 5 == 0 else pal[1 + (y // 5) % 3]
            for x in range(N):
                cv.px(x, y, mix(base, pal[int(rng.integers(0, 4))], 0.35))
    elif style == "b":
        pts = [(int(rng.integers(0, N)), int(rng.integers(0, N))) for _ in range(9)]
        cols = [pal[int(rng.integers(1, 4))] for _ in pts]
        for y in range(N):
            for x in range(N):
                ds = sorted(((min(abs(x - px), N - abs(x - px)) ** 2 + min(abs(y - py), N - abs(y - py)) ** 2), i) for i, (px, py) in enumerate(pts))
                (d1, i1), (d2, i2) = ds[0], ds[1]
                edge = (d2 ** 0.5 - d1 ** 0.5) < 1.6
                cv.px(x, y, pal[0] if edge else cols[i1])
    else:
        for y in range(N):
            for x in range(N):
                cv.px(x, y, pal[int(rng.integers(0, 3))])
        dust = p(style, "dust")
        for _ in range(70):
            cv.px(int(rng.integers(0, N)), int(rng.integers(0, N)), mix(dust, pal[3], float(rng.random() * 0.5)))
    if light:
        for y in range(N):
            for x in range(N):
                r = cv.get(x, y)
                cv.px(x, y, mix(r[:3], (255, 255, 255), light))
    return cv


def crack(cv, style, seed, level, glow=True, start=None, count=3):
    """割れ目の光。level 0 暗い線だけ、1 橙の筋、2 黄まで光る"""
    rng = np.random.default_rng(seed)
    em = [p(style, "ember", i) for i in range(4)]
    for k in range(count):
        x, y = (start if start and k == 0 else (int(rng.integers(2, N - 2)), int(rng.integers(2, N - 2))))
        for _ in range(int(rng.integers(14, 26))):
            if level == 0:
                cv.px(x, y, p(style, "rock", 0))
            else:
                core = em[2] if level == 1 else em[3]
                halo = em[1] if level == 1 else em[2]
                if glow:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        cx, cy = x + dx, y + dy
                        if 0 <= cx < N and 0 <= cy < N and cv.get(cx, cy)[:3] not in (core, halo):
                            cv.px(cx, cy, mix(cv.get(cx, cy)[:3], halo, 0.55))
                cv.px(x, y, core)
            x += int(rng.integers(-1, 2)) or (1 if rng.random() < 0.5 else -1)
            y += int(rng.integers(0, 2)) * (1 if rng.random() < 0.5 else -1) + (1 if rng.random() < 0.3 else 0)
            x = max(0, min(N - 1, x))
            y = max(0, min(N - 1, y))
    return cv


def border(cv, c, w=1):
    for i in range(w):
        for k in range(N):
            for (x, y) in ((k, i), (k, N - 1 - i), (i, k), (N - 1 - i, k)):
                cv.px(x, y, c)


# ---------------------------------------------------------------- 各品
def cooled_crust(style, stage):
    cv = rock(style, 11 + stage)
    crack(cv, style, 21, 0 if stage == 0 else 1 if stage == 1 else 2, count=2 + stage * 2)
    if stage == 2:
        em = p(style, "ember", 1)
        for y in range(0, N, 4):
            for x in range(0, N, 5):
                cv.px(x + (y // 4) % 2 * 2, y, em)
    border(cv, mix(p(style, "rock", 0), (0, 0, 0), 0.4))
    return cv


BLOOM = [("#242020", "#3a2e2a"), ("#9a1f10", "#d83a14"), ("#d8560e", "#ff8a18"), ("#f0b020", "#ffe060"), ("#ffe8b0", "#ffffff"), None]


def ash_forge(style, stage, cracked):
    cv = Canvas((*p(style, "iron", 0), 255))
    # 炉の口のレンガの枠
    brick = [p(style, "rock", i) for i in range(4)]
    for y in range(N):
        for x in range(N):
            off = 4 if (y // 4) % 2 else 0
            cv.px(x, y, brick[0] if (y % 4 == 0 or (x + off) % 8 == 0) else brick[1 + (x * 7 + y * 3) % 3 % 2])
    cx, cy, r = 15.5, 15.5, 11.5
    for y in range(N):
        for x in range(N):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                if stage == 5:
                    c = mix(hx("#0e0b0b"), p(style, "ember", 0), 0.35 * max(0, 1 - d / r)) if (x * 5 + y * 3) % 11 else p(style, "ember", 1)
                else:
                    c0, c1 = BLOOM[stage]
                    c = mix(hx(c1), hx(c0), min(1, d / r) ** 1.3)
                    if (x * 13 + y * 7) % 17 == 0:
                        c = mix(c, (255, 255, 255), 0.25)
                cv.px(x, y, c)
            elif d <= r + 1.4:
                cv.px(x, y, p(style, "iron", 1) if style != "b" else p(style, "iron", 2))
    if cracked and stage != 5:
        crack(cv, style, 77, 0, count=2, start=(10, 9))
    return cv


def bellows(style):
    cv = Canvas((*hx("#4a3220"), 255))
    wood = [hx("#3d2a1a"), hx("#4f3622"), hx("#63452b")]
    for y in range(N):
        for x in range(N):
            cv.px(x, y, wood[(y // 3 + x // 11) % 3] if y % 3 else wood[0])
    iron = [p(style, "iron", i) for i in range(4)]
    cv.rect(0, 0, 31, 1, iron[1])
    cv.rect(0, 30, 31, 31, iron[1])
    cv.rect(0, 0, 1, 31, iron[1])
    cv.rect(30, 0, 31, 31, iron[1])
    for (x, y) in ((3, 3), (28, 3), (3, 28), (28, 28)):
        cv.rect(x - 1, y - 1, x, y, iron[3])
    # 革の蛇腹 (中ほどの線)
    leather = [hx("#7a5a3a"), hx("#94704a")]
    for y in range(8, 24):
        for x in range(6, 26):
            cv.px(x, y, leather[(y // 2) % 2])
    # 足を踏む印の火
    em = p(style, "ember", 2)
    cv.rect(14, 14, 17, 17, em)
    cv.rect(15, 15, 16, 16, p(style, "ember", 3))
    return cv


def anvil(style):
    cv = Canvas((*p(style, "iron", 1), 255))
    iron = [p(style, "iron", i) for i in range(4)]
    rng = np.random.default_rng(5)
    for y in range(N):
        for x in range(N):
            cv.px(x, y, iron[int(rng.integers(0, 3))])
    cv.rect(0, 0, 31, 2, iron[3])
    cv.rect(0, 29, 31, 31, iron[0])
    em = p(style, "ember", 1)
    for i in range(6):
        cv.px(int(rng.integers(3, 29)), int(rng.integers(5, 27)), em)
    return cv


def trough_side(style):
    cv = rock(style, 33)
    iron = [p(style, "iron", i) for i in range(4)]
    cv.rect(0, 0, 31, 3, iron[2])
    cv.rect(0, 28, 31, 31, iron[1])
    w = [hx(c) if isinstance(c, str) else c for c in PAL[style]["water"]]
    for x in range(3, 29, 6):
        cv.rect(x, 12, x + 1, 20, w[0])
    return cv


def trough_top(style):
    w = [hx(c) for c in PAL[style]["water"]]
    cv = Canvas((*w[1], 255))
    rng = np.random.default_rng(8)
    for y in range(N):
        for x in range(N):
            v = (np.sin(x * 0.7 + y * 0.35) + np.sin(y * 0.9 - x * 0.2)) / 2
            cv.px(x, y, w[0] if v < -0.35 else w[2] if v > 0.55 else w[1])
    border(cv, p(style, "iron", 2), 3)
    return cv


def hearth_top(style, level):
    cv = rock(style, 41)
    cx, cy = 15.5, 15.5
    em = [p(style, "ember", i) for i in range(4)]
    wh = p(style, "white")
    rad = [3.0, 6.0, 9.0, 12.0, 14.0][level]
    for y in range(N):
        for x in range(N):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= rad:
                t = d / rad
                if level == 0:
                    c = mix(p(style, "dust"), em[0], 1 - t)
                else:
                    c = mix(wh if level == 4 else em[3], em[0 if level < 3 else 1], t ** 1.1)
                    if level >= 2 and (x * 3 + y * 5) % 7 == 0:
                        c = mix(c, wh, 0.3)
                cv.px(x, y, c)
    border(cv, p(style, "iron", 1), 2)
    return cv


def hearth_side(style, level):
    cv = rock(style, 42)
    em = [p(style, "ember", i) for i in range(4)]
    h = [1, 3, 6, 9, 12][level]
    for y in range(N - h, N):
        for x in range(N):
            t = (y - (N - h)) / max(1, h)
            if (x * 7 + y * 3) % 5:
                cv.px(x, y, mix(em[0], em[2 if level < 4 else 3], t))
    border(cv, p(style, "iron", 1), 1)
    return cv


def vent_top(style, phase):
    cv = rock(style, 51)
    em = [p(style, "ember", i) for i in range(4)]
    wh = p(style, "white")
    # 真ん中の割れ目 (縦に長い)
    for y in range(3, 29):
        w = 1 + int(2 * np.sin(y * 0.4) ** 2) + (1 if phase else 0)
        for x in range(16 - w, 16 + w):
            if phase == 0:
                c = mix(hx("#120d0d"), em[0], 0.4)
            elif phase == 1:
                c = wh if abs(x - 15.5) < w * 0.6 else mix(wh, em[3], 0.5)
            else:
                c = em[3] if abs(x - 15.5) < w * 0.5 else em[2] if abs(x - 15.5) < w * 0.85 else em[1]
            cv.px(x, y, c)
    border(cv, mix(p(style, "rock", 0), (0, 0, 0), 0.4))
    return cv


def vent_side(style):
    cv = rock(style, 52)
    border(cv, mix(p(style, "rock", 0), (0, 0, 0), 0.4))
    return cv


def urn_stand(style):
    cv = rock(style, 61)
    iron = [p(style, "iron", i) for i in range(4)]
    cv.rect(0, 0, 31, 3, iron[2])
    # 灰の壺の絵 (側面いっぱい)
    for y in range(8, 28):
        half = 8 + int(3 * np.sin((y - 8) / 20 * np.pi))
        for x in range(16 - half, 16 + half):
            cv.px(x, y, mix(p(style, "dust"), p(style, "rock", 3), abs(x - 12) / 24))
    cv.rect(10, 6, 21, 8, p(style, "dust"))
    cv.rect(12, 5, 19, 6, p(style, "white"))
    return cv


# 品のアイコン (32px、透明の背景)
def icon_urn(style):
    cv = Canvas()
    d = p(style, "dust")
    r = [p(style, "rock", i) for i in range(4)]
    for y in range(9, 28):
        half = 6 + int(5 * np.sin((y - 9) / 19 * np.pi)) - (2 if y > 24 else 0)
        for x in range(16 - half, 16 + half):
            sh = (x - (16 - half)) / max(1, 2 * half)
            cv.px(x, y, mix(mix(d, (255, 255, 255), 0.25), r[2], sh))
        cv.px(16 - half, y, r[0])
        cv.px(16 + half - 1, y, r[0])
    cv.rect(11, 6, 20, 8, r[1])
    cv.rect(12, 7, 19, 7, p(style, "dust"))
    cv.rect(13, 4, 18, 5, p(style, "white"))
    em = p(style, "ember", 2)
    for (x, y) in ((14, 5), (17, 4), (19, 6), (15, 3)):
        cv.px(x, y, em)
    cv.rect(12, 18, 19, 19, p(style, "ember", 1))
    return cv


def icon_key(style, hot):
    cv = Canvas()
    iron = [p(style, "iron", i) for i in range(4)]
    em = [p(style, "ember", i) for i in range(4)]
    body = [em[1], em[2], em[3]] if hot else [iron[1], iron[2], iron[3]]
    # 輪
    for y in range(4, 14):
        for x in range(4, 14):
            d = ((x - 8.5) ** 2 + (y - 8.5) ** 2) ** 0.5
            if 2.2 <= d <= 4.8:
                cv.px(x, y, body[1] if d < 3.8 else body[0])
    # 軸 (斜め)
    for i in range(0, 17):
        x, y = 11 + i, 11 + i
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            cv.px(x + dx, y + dy, body[1])
        cv.px(x - 1, y + 1, body[0])
        cv.px(x + 1, y - 1, body[2])
    # 歯
    for (x, y) in ((22, 26), (24, 24), (26, 22)):
        cv.rect(x, y, x + 2, y + 2, body[0])
    if not hot:
        for (x, y) in ((6, 6), (8, 13), (20, 20)):
            cv.px(x, y, em[2])
        cv.px(8, 6, em[3])
    else:
        for (x, y) in ((3, 3), (14, 4), (4, 14), (28, 28)):
            cv.px(x, y, em[3])
    return cv


def icon_coal(style):
    cv = Canvas()
    r = [p(style, "rock", i) for i in range(4)]
    em = [p(style, "ember", i) for i in range(4)]
    rng = np.random.default_rng(71)
    pts = [(16, 18, 9), (11, 21, 6), (21, 22, 6)]
    for (cx, cy, rad) in pts:
        for y in range(N):
            for x in range(N):
                d = ((x - cx) ** 2 + ((y - cy) * 1.15) ** 2) ** 0.5
                if d <= rad:
                    cv.px(x, y, r[int(min(3, max(0, (d / rad) * 2 + rng.random() * 1.2 - (x - cx) / 14)))])
    for _ in range(14):
        x, y = int(rng.integers(8, 25)), int(rng.integers(13, 27))
        if cv.get(x, y)[3]:
            cv.px(x, y, em[int(rng.integers(1, 4))])
            if rng.random() < 0.5 and cv.get(x + 1, y)[3]:
                cv.px(x + 1, y, em[1])
    cv.px(16, 10, em[2])
    cv.px(14, 9, em[1])
    cv.px(18, 8, em[3])
    return cv


# ---------------------------------------------------------------- 品の一覧
# 名前 -> (描く関数, 置き場 block|item, グループ)
ASSETS_LIST: dict[str, tuple] = {}


def reg(name, fn, kind, group):
    ASSETS_LIST[name] = (fn, kind, group)


for _s in range(3):
    reg(f"cooled_crust_{_s}", (lambda st, s=_s: cooled_crust(st, s)), "block", "crust")
reg("ash_urn_stand", urn_stand, "block", "crust")
reg("ash_urn", icon_urn, "item", "crust")
for _s in range(6):
    reg(f"ash_forge_s{_s}", (lambda st, s=_s: ash_forge(st, s, False)), "block", "forge")
for _s in range(5):
    reg(f"ash_forge_s{_s}_cracked", (lambda st, s=_s: ash_forge(st, s, True)), "block", "forge")
reg("forge_bellows", bellows, "block", "forge")
reg("ash_anvil", anvil, "block", "forge")
reg("quench_trough_side", trough_side, "block", "forge")
reg("quench_trough_top", trough_top, "block", "forge")
reg("fire_key_hot", (lambda st: icon_key(st, True)), "item", "forge")
reg("fire_key", (lambda st: icon_key(st, False)), "item", "forge")
for _l in range(5):
    reg(f"ember_hearth_top_{_l}", (lambda st, l=_l: hearth_top(st, l)), "block", "hearth")
    reg(f"ember_hearth_side_{_l}", (lambda st, l=_l: hearth_side(st, l)), "block", "hearth")
for _ph in range(3):
    reg(f"heat_vent_top_{_ph}", (lambda st, ph=_ph: vent_top(st, ph)), "block", "hearth")
reg("heat_vent_side", vent_side, "block", "hearth")
reg("live_coal", icon_coal, "item", "hearth")

# 推奨 (理由は docs/review/ember_trial_roughs/README.md)。ブロックの面は全部同じ案にそろえる (つなぎ目が合わないため)
RECOMMEND = {name: "a" for name in ASSETS_LIST}


# ---------------------------------------------------------------- 書き出し
def write_roughs():
    for name, (fn, kind, group) in ASSETS_LIST.items():
        d = REVIEW / name
        d.mkdir(parents=True, exist_ok=True)
        for st in STYLES:
            fn(st).image().save(d / f"{st}.png")
    for group in ("crust", "forge", "hearth"):
        names = [n for n, (f, k, g) in ASSETS_LIST.items() if g == group]
        cols = 3
        scale = 3
        pad = 4
        rows = len(names)
        sheet = Image.new("RGBA", (cols * (N * scale + pad) + 150, rows * (N * scale + pad)), (30, 28, 34, 255))
        dr = ImageDraw.Draw(sheet)
        for r, n in enumerate(names):
            for c, st in enumerate(STYLES):
                im = Image.open(REVIEW / n / f"{st}.png").resize((N * scale, N * scale), Image.NEAREST)
                sheet.paste(im, (c * (N * scale + pad), r * (N * scale + pad)), im)
            dr.text((cols * (N * scale + pad) + 6, r * (N * scale + pad) + 40), n, fill=(220, 220, 220, 255))
        sheet.save(REVIEW / f"compare_{group}.png")


def install(options, from_sources):
    SOURCES.mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    for name, (fn, kind, group) in ASSETS_LIST.items():
        if from_sources:
            img = Image.open(SOURCES / f"{name}.png").convert("RGBA")
        else:
            img = fn(options.get(name, RECOMMEND[name])).image()
            img.save(SOURCES / f"{name}.png")
        img.save(ASSETS / f"textures/{kind}/{name}.png")
    write_models()


def wj(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))


def box(frm, to, tex_all, faces=None):
    f = {d: {"uv": [0, 0, 16, 16], "texture": tex_all} for d in ("north", "south", "east", "west", "up", "down")}
    if faces:
        for d, t in faces.items():
            f[d]["texture"] = t
    return {"from": frm, "to": to, "faces": f}


def write_models():
    M = ASSETS / "models/block"
    B = ASSETS / "blockstates"
    I = ASSETS / "models/item"

    def cube_all(name, tex):
        wj(M / f"{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"skyabyss:block/{tex}"}})

    # 冷え殻
    for s in range(3):
        cube_all(f"cooled_crust_{s}", f"cooled_crust_{s}")
    wj(B / "cooled_crust.json", {"variants": {f"stage={s}": {"model": f"skyabyss:block/cooled_crust_{s}"} for s in range(3)}})
    # 大炉
    variants = {}
    for s in range(6):
        cube_all(f"ash_forge_s{s}", f"ash_forge_s{s}")
        variants[f"stage={s},cracked=false"] = {"model": f"skyabyss:block/ash_forge_s{s}"}
        t = f"ash_forge_s{s}_cracked" if s < 5 else "ash_forge_s5"
        if s < 5:
            cube_all(f"ash_forge_s{s}_cracked", t)
        variants[f"stage={s},cracked=true"] = {"model": f"skyabyss:block/ash_forge_s{s}_cracked" if s < 5 else "skyabyss:block/ash_forge_s5"}
    wj(B / "ash_forge.json", {"variants": variants})
    # 鞴
    for name, h in (("forge_bellows", 3), ("forge_bellows_pressed", 1.5)):
        wj(M / f"{name}.json", {"textures": {"0": "skyabyss:block/forge_bellows", "particle": "skyabyss:block/forge_bellows"},
                                "elements": [box([1, 0, 1], [15, h, 15], "#0")]})
    wj(B / "forge_bellows.json", {"variants": {"pressed=false": {"model": "skyabyss:block/forge_bellows"},
                                               "pressed=true": {"model": "skyabyss:block/forge_bellows_pressed"}}})
    # 金床
    wj(M / "ash_anvil.json", {"textures": {"0": "skyabyss:block/ash_anvil", "particle": "skyabyss:block/ash_anvil"},
                              "elements": [box([2, 0, 2], [14, 4, 14], "#0"), box([4, 4, 3], [12, 5, 13], "#0"),
                                           box([6, 5, 4], [10, 10, 12], "#0"), box([3, 10, 0], [13, 16, 16], "#0")]})
    wj(B / "ash_anvil.json", {"variants": {"": {"model": "skyabyss:block/ash_anvil"}}})
    # 槽
    wj(M / "quench_trough.json", {"parent": "minecraft:block/cube_bottom_top",
                                  "textures": {"top": "skyabyss:block/quench_trough_top", "side": "skyabyss:block/quench_trough_side",
                                               "bottom": "skyabyss:block/quench_trough_side", "particle": "skyabyss:block/quench_trough_side"}})
    wj(B / "quench_trough.json", {"variants": {"": {"model": "skyabyss:block/quench_trough"}}})
    # 灰の壺の台
    wj(M / "ash_urn_stand.json", {"textures": {"0": "skyabyss:block/ash_urn_stand", "particle": "skyabyss:block/ash_urn_stand"},
                                  "elements": [box([2, 0, 2], [14, 14, 14], "#0")]})
    wj(B / "ash_urn_stand.json", {"variants": {"": {"model": "skyabyss:block/ash_urn_stand"}}})
    # 火床
    for l in range(5):
        wj(M / f"ember_hearth_{l}.json", {"textures": {"top": f"skyabyss:block/ember_hearth_top_{l}", "side": f"skyabyss:block/ember_hearth_side_{l}",
                                                       "particle": f"skyabyss:block/ember_hearth_side_{l}"},
                                          "elements": [box([1, 0, 1], [15, 12, 15], "#side", {"up": "#top", "down": "#side"})]})
    wj(B / "ember_hearth.json", {"variants": {f"level={l}": {"model": f"skyabyss:block/ember_hearth_{l}"} for l in range(5)}})
    # 噴気の割れ目
    for ph in range(3):
        wj(M / f"heat_vent_{ph}.json", {"parent": "minecraft:block/cube_bottom_top",
                                        "textures": {"top": f"skyabyss:block/heat_vent_top_{ph}", "side": "skyabyss:block/heat_vent_side",
                                                     "bottom": "skyabyss:block/heat_vent_side", "particle": "skyabyss:block/heat_vent_side"}})
    wj(B / "heat_vent.json", {"variants": {f"phase={ph}": {"model": f"skyabyss:block/heat_vent_{ph}"} for ph in range(3)}})
    # ブロックの品 (代表の状態)
    for name, model in (("cooled_crust", "cooled_crust_0"), ("ash_urn_stand", "ash_urn_stand"), ("ash_forge", "ash_forge_s2"),
                        ("forge_bellows", "forge_bellows"), ("ash_anvil", "ash_anvil"), ("quench_trough", "quench_trough"),
                        ("ember_hearth", "ember_hearth_2"), ("heat_vent", "heat_vent_0")):
        wj(I / f"{name}.json", {"parent": f"skyabyss:block/{model}"})
    # 品
    for name in ("ash_urn", "fire_key_hot", "fire_key", "live_coal"):
        wj(I / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"skyabyss:item/{name}"}})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--install", action="store_true")
    ap.add_argument("--from-sources", action="store_true")
    ap.add_argument("--option", action="append", default=[], help="name=a|b|c")
    args = ap.parse_args()
    opts = {}
    for o in args.option:
        k, v = o.split("=")
        # 名前に group 接頭辞を使えるように: ash_forge=b は ash_forge_ で始まる全部
        for n in ASSETS_LIST:
            if n == k or n.startswith(k + "_"):
                opts[n] = v
    if not args.install or not args.from_sources:
        write_roughs()
    if args.install:
        install(opts, args.from_sources)
    print("ok", len(ASSETS_LIST), "images")


if __name__ == "__main__":
    main()
