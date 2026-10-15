"""岩鯨の甲殻 (armor) のラフ。案 A/B/C を作る。

実行 (リポジトリ直下): python tools/rough/armor.py
出力:
  docs/roughs/armor/<a|b|c>/ : helmet/chestplate/leggings/boots の 16x16 PNG, geo.json, texture.png, glowmask.png
  docs/roughs/armor.png      : 比較シート (列 A/B/C)
  docs/roughs/armor.md       : 日本語の説明と重なり検査の結果

アイコンの形は docs/roughs/vanilla_ref/iron_*.png の輪郭 (VAN) を型にし、その中を
石板・鯨骨・水色の共鳴の模様で塗る。3D の前面は、アイコンの画素をそのまま板にする
(1 画素 = 1 単位)。アイコンの透明な所は、板の中では輪郭色 (K) で埋める。

座標: 1単位 = 1画素 = 1/16 ブロック。前面は -z (north)。
アイコンの列 c は、前面で x = 7 - c .. 8 - c (左が +x)。行 r は y = 36 - r (頭)。
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import _box_uv, check_overlaps, icon_big, load_geo, render, sheet  # noqa: E402

TEXW, TEXH = 128, 64
SEED = 20261015
OUT = ROOT / "docs/roughs"

# ------------------------------------------------------------------ パレット
# K 輪郭 / D 暗い石 / M 石 / L 明るい石 / H 上端の光 / W 鯨骨 / w 鯨骨の影
# C 共鳴の水色 / c 共鳴の芯 / G 苔 / g 明るい苔
PAL = {
    "A": dict(K=(20, 23, 32), D=(47, 56, 71), M=(85, 96, 122), L=(129, 144, 168), H=(180, 191, 211),
              W=(236, 226, 200), w=(181, 166, 132), C=(57, 211, 230), c=(201, 251, 255),
              G=(79, 112, 73), g=(118, 160, 106)),
    "B": dict(K=(16, 18, 24), D=(35, 42, 54), M=(63, 72, 88), L=(93, 104, 124), H=(138, 149, 170),
              W=(217, 205, 175), w=(152, 137, 106), C=(47, 199, 220), c=(166, 243, 255),
              G=(61, 92, 58), g=(95, 134, 86)),
    "C": dict(K=(15, 29, 34), D=(31, 58, 64), M=(47, 92, 98), L=(79, 138, 140), H=(143, 196, 191),
              W=(239, 227, 200), w=(180, 157, 122), C=(94, 242, 255), c=(224, 255, 255),
              G=(44, 79, 70), g=(79, 160, 124)),
}

# 石板の模様 (アイコンと 3D の面に使う)
STYLE = {
    "A": dict(ph=4, pw=4, tones="MMMD", rib=False, moss=0.0),
    "B": dict(ph=5, pw=5, tones="DMMD", rib=True, moss=0.04),
    "C": dict(ph=3, pw=4, tones="MLMD", rib=False, moss=0.0),
}

# ------------------------------------------------------------------ 形の型 (vanilla_ref の輪郭)
VAN = {
    "helmet": [
        "................", "................", "................", ".....######.....",
        "....########....", "...##########...", "...##########...", "...##########...",
        "...##########...", "...##########...", "...##########...", "....##....##....",
        "................", "................", "................", "................",
    ],
    "chest": [
        "................", "................", ".#####....#####.", ".#####....#####.",
        ".######..######.", ".##############.", ".##############.", ".##############.",
        "...##########...", "...##########...", "...##########...", "...##########...",
        "...##########...", "....########....", ".....######.....", "................",
    ],
    "legs": [
        "................", "................", "....########....", "...##########...",
        "...##########...", "...##########...", "...##########...", "...####..####...",
        "...####..####...", "...####..####...", "...####..####...", "...####..####...",
        "...####..####...", "...####..####...", "................", "................",
    ],
    "boots": [
        "................", "................", "................", "....###..###....",
        "...####..####...", "...####..####...", "...####..####...", "...####..####...",
        "...####..####...", "..#####..#####..", ".######..######.", ".######..######.",
        ".####......####.", "................", "................", "................",
    ],
}

# 模様 (opt, piece) -> [(文字, [(行, 列), ...]), ...]。型の外にも描ける (角・飾り)。
MOTIF = {
    ("A", "helmet"): [
        ("W", [(8, c) for c in range(4, 12)]),
        ("K", [(9, c) for c in range(6, 10)]),
        ("c", [(6, 7)]), ("C", [(6, 8), (5, 5), (5, 10)]),
    ],
    ("A", "chest"): [
        ("W", [(8, c) for c in range(3, 13)]),
        ("c", [(10, 7)]), ("C", [(10, 8), (10, 4), (10, 11)]),
        ("D", [(13, c) for c in range(4, 12)]),
    ],
    ("A", "legs"): [
        ("W", [(7, c) for c in list(range(3, 7)) + list(range(9, 13))]),
        ("C", [(9, 4), (9, 10)]),
    ],
    ("A", "boots"): [
        ("W", [(7, c) for c in list(range(3, 7)) + list(range(9, 13))]),
        ("C", [(11, 4), (11, 10)]),
    ],
    ("B", "helmet"): [
        ("W", [(7, c) for c in range(4, 12)]),
        ("w", [(10, c) for c in range(4, 12)]),
        ("K", [(9, c) for c in range(6, 10)]),
        ("G", [(5, 5), (5, 9)]), ("g", [(6, 11)]),
        ("C", [(6, 7), (6, 8)]),
        ("K", [(5, 1), (6, 1), (7, 1), (8, 1), (5, 14), (6, 14), (7, 14), (8, 14)]),
        ("M", [(6, 2), (7, 2), (8, 2), (6, 13), (7, 13), (8, 13)]),
        ("C", [(5, 2), (5, 13)]),
    ],
    ("B", "chest"): [
        ("W", [(4, c) for c in range(3, 13, 2)] + [(8, c) for c in range(3, 13, 2)] + [(12, c) for c in range(3, 13, 2)]),
        ("w", [(4, c) for c in range(4, 13, 2)] + [(8, c) for c in range(4, 13, 2)] + [(12, c) for c in range(4, 13, 2)]),
        ("G", [(6, 4), (10, 10)]), ("g", [(6, 9)]),
        ("C", [(9, 7)]), ("c", [(9, 8)]),
    ],
    ("B", "legs"): [
        ("W", [(7, c) for c in list(range(3, 7)) + list(range(9, 13))]),
        ("w", [(8, c) for c in list(range(3, 7)) + list(range(9, 13))]),
        ("G", [(10, 5), (9, 11)]), ("g", [(11, 11)]),
    ],
    ("B", "boots"): [
        ("W", [(7, c) for c in list(range(3, 7)) + list(range(9, 13))]),
        ("w", [(8, c) for c in list(range(3, 7)) + list(range(9, 13))]),
        ("G", [(9, 4)]), ("C", [(11, 10)]),
    ],
    ("C", "helmet"): [
        # 背びれ (頭の上、x -2..2 の 4x3)
        ("K", [(0, c) for c in range(6, 10)] + [(1, 6), (1, 9), (2, 6), (2, 9)]),
        ("W", [(1, 7), (1, 8)]), ("c", [(2, 7)]), ("C", [(2, 8)]),
        ("W", [(7, c) for c in range(4, 12, 2)]), ("w", [(7, c) for c in range(5, 12, 2)]),
        ("K", [(9, c) for c in range(6, 10)]),
        ("C", [(5, 5), (5, 10)]),
    ],
    ("C", "chest"): [
        ("W", [(5, c) for c in range(3, 13, 2)] + [(11, c) for c in range(3, 13, 2)]),
        ("w", [(5, c) for c in range(4, 13, 2)] + [(11, c) for c in range(4, 13, 2)]),
        ("C", [(9, 6), (9, 9)]), ("c", [(8, 7)]),
    ],
    ("C", "legs"): [
        ("W", [(7, c) for c in list(range(3, 7)) + list(range(9, 13)) if c % 2 == 1]),
        ("w", [(7, c) for c in list(range(3, 7)) + list(range(9, 13)) if c % 2 == 0]),
        ("C", [(9, 4), (9, 10)]), ("c", [(10, 4), (10, 10)]),
    ],
    ("C", "boots"): [
        ("W", [(7, c) for c in list(range(3, 7)) + list(range(9, 13))]),
        ("C", [(11, 3), (11, 11)]), ("c", [(12, 5), (12, 10)]),
    ],
}

N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def icon(opt, key):
    """型の中を、石板の模様で塗り、模様を重ねる (16x16 の文字格子)。"""
    st = STYLE[opt]
    mask = [[ch == "#" for ch in row] for row in VAN[key]]

    def inside(y, x):
        return 0 <= y < 16 and 0 <= x < 16 and mask[y][x]

    g = [["." for _ in range(16)] for _ in range(16)]
    ph, pw, tones = st["ph"], st["pw"], st["tones"]
    seed = sum(map(ord, key + opt))
    for y in range(16):
        for x in range(16):
            if not mask[y][x]:
                continue
            if not all(inside(y + dy, x + dx) for dy, dx in N4):
                g[y][x] = "K"
                continue
            if not inside(y - 1, x):
                g[y][x] = "H"
                continue
            r, yy = divmod(y, ph)
            off = (r % 2) * (pw // 2)
            xx, cc = (x + off) % pw, (x + off) // pw
            if yy == 0 or xx == 0:
                g[y][x] = "D"
            else:
                g[y][x] = tones[(r * 73 + cc * 151 + seed) % len(tones)]
    for ch, pts in MOTIF.get((opt, key), []):
        for r, c in pts:
            g[r][c] = ch
    return g


def fill_dark(g):
    """3D の板で使う版: 透明な画素を輪郭色で埋める (箱の面に穴を空けないため)。"""
    return [["D" if ch == "." else ch for ch in row] for row in g]


# ------------------------------------------------------------------ 部品 (立方体)
def cubes_for(opt):
    """前面 (icon) の対応: 頭の低い部分 rows 6..11, 頭の丸み rows 3..5, 胴 rows 2..13,
    脚 rows 7..12 (右脚は cols 9..12, 左脚は cols 3..6), 足は同じ行。"""
    arm_w = 6 if opt == "B" else 4
    rx = -5 - arm_w  # 胴 (x -5..5) の外側に接する
    cubes = [
        dict(name="headLow", bone="armorHead", origin=[-5, 24, -5], size=[10, 6, 10], icon=("helmet", 3, 6), trim_b=1),
        dict(name="headDome", bone="armorHead", origin=[-4, 30, -4], size=[8, 3, 8], icon=("helmet", 4, 3)),
        dict(name="chest", bone="armorBody", origin=[-5, 12, -3], size=[10, 12, 6], icon=("chest", 3, 2), trim_b=1, glow=True),
        dict(name="rSleeve", bone="armorRightArm", origin=[rx, 12, -3], size=[arm_w, 8, 6], trim_b=1),
        dict(name="rPauldron", bone="armorRightArm", origin=[rx, 20, -3], size=[arm_w, 4, 6], trim_b=1),
        dict(name="lSleeve", bone="armorLeftArm", origin=[5, 12, -3], size=[arm_w, 8, 6], trim_b=1),
        dict(name="lPauldron", bone="armorLeftArm", origin=[5, 20, -3], size=[arm_w, 4, 6], trim_b=1),
        dict(name="rLeg", bone="armorRightLeg", origin=[-5, 6, -3], size=[4, 6, 6], icon=("legs", 9, 7), trim_t=0),
        dict(name="lLeg", bone="armorLeftLeg", origin=[1, 6, -3], size=[4, 6, 6], icon=("legs", 3, 7)),
        dict(name="rBoot", bone="armorRightBoot", origin=[-5, 0, -3], size=[4, 6, 6], icon=("boots", 9, 7)),
        dict(name="lBoot", bone="armorLeftBoot", origin=[1, 0, -3], size=[4, 6, 6], icon=("boots", 3, 7)),
    ]
    if opt == "B":
        cubes += [
            dict(name="hornR", bone="armorHead", origin=[-7, 27, -2], size=[2, 4, 4], glow=True),
            dict(name="hornL", bone="armorHead", origin=[5, 27, -2], size=[2, 4, 4], glow=True),
            dict(name="ridge", bone="armorBody", origin=[-3, 13, -5], size=[6, 10, 2], icon=("chest", 5, 3)),
        ]
    if opt == "C":
        cubes += [
            dict(name="fin", bone="armorHead", origin=[-2, 33, -3], size=[4, 3, 3], icon=("helmet", 6, 0)),
            dict(name="tail", bone="armorHead", origin=[0, 36, 0], size=[2, 3, 2], glow=True),
            dict(name="spout", bone="armorBody", origin=[-2, 15, 3], size=[4, 4, 2], glow=True),
            dict(name="rToe", bone="armorRightBoot", origin=[-5, 0, -5], size=[4, 3, 2]),
            dict(name="lToe", bone="armorLeftBoot", origin=[1, 0, -5], size=[4, 3, 2]),
        ]
    return cubes


BONES = {
    "armorHead": [0, 24, 0], "armorBody": [0, 24, 0],
    "armorRightArm": [-5, 22, 0], "armorLeftArm": [5, 22, 0],
    "armorRightLeg": [-1.9, 12, 0], "armorLeftLeg": [1.9, 12, 0],
    "armorRightBoot": [-1.9, 12, 0], "armorLeftBoot": [1.9, 12, 0],
}


# ------------------------------------------------------------------ 描画用の板
def to_rgba(grid, pal):
    h, w = len(grid), len(grid[0])
    out = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        for x in range(w):
            ch = grid[y][x]
            if ch != ".":
                out[y, x, :3] = pal[ch]
                out[y, x, 3] = 255
    return out


def paint(w, h, st, seed, trim_top=0, trim_bottom=0, glow=()):
    """アイコンに無い面を、石板の模様で塗る (文字の格子を返す)。"""
    rng = np.random.default_rng(seed)
    tones, ph, pw = st["tones"], st["ph"], st["pw"]
    g = [["."] * w for _ in range(h)]
    for y in range(h):
        r, yy = divmod(y, ph)
        for x in range(w):
            off = (r % 2) * (pw // 2)
            xx = (x + off) % pw
            c = (x + off) // pw
            ch = tones[(r * 73 + c * 151 + seed * 17) % len(tones)]
            if yy == 0 or xx == 0:
                ch = "D"
            if st["moss"] and ch in tones and rng.random() < st["moss"]:
                ch = "G" if rng.random() < 0.6 else "g"
            g[y][x] = ch
    for y in range(h):
        g[y][0] = "D"
    for x in range(w):
        g[0][x] = "H"
        g[h - 1][x] = "D"
    for y in range(h):
        if y < trim_top or y >= h - trim_bottom:
            for x in range(w):
                g[y][x] = "W" if (x % 2 == 0 or not st["rib"]) else "w"
    for gx, gy in glow:
        if 0 <= gx < w and 0 <= gy < h:
            g[gy][gx] = "c"
            for dx, dy in N4:
                nx, ny = gx + dx, gy + dy
                if 0 <= nx < w and 0 <= ny < h and g[ny][nx] not in ("W", "w", "D"):
                    g[ny][nx] = "C"
    return g


def pack(sizes):
    """箱UV (幅 2(w+d), 高さ d+h) を、テクスチャに棚詰めで置く。"""
    order = sorted(range(len(sizes)), key=lambda i: -(sizes[i][2] + sizes[i][1]))
    uv, x, y, shelf = {}, 0, 0, 0
    for i in order:
        w, h, d = sizes[i]
        bw, bh = 2 * (w + d), d + h
        if x + bw > TEXW:
            x, y, shelf = 0, y + shelf, 0
        uv[i] = (x, y)
        x += bw
        shelf = max(shelf, bh)
    assert y + shelf <= TEXH, "texture too small"
    return uv


def build(opt):
    cubes = cubes_for(opt)
    st = STYLE[opt]
    pal = PAL[opt]
    icons = {k: icon(opt, k) for k in VAN}
    uv = pack([tuple(c["size"]) for c in cubes])
    atlas = [["."] * TEXW for _ in range(TEXH)]
    for i, c in enumerate(cubes):
        u, v = uv[i]
        w, h, d = c["size"]
        c["uv"] = [u, v]
        for face, (fu, fv, fw, fh) in _box_uv(u, v, w, h, d).items():
            if face == "north" and c.get("icon"):
                key, c0, r0 = c["icon"]
                src = fill_dark(icons[key])
                grid = [src[r0 + yy][c0:c0 + fw] for yy in range(fh)]
                assert len(grid) == fh and all(len(r) == fw for r in grid), (opt, c["name"])
            else:
                side = face in ("west", "east")
                band = face in ("west", "east", "south", "north")
                grid = paint(
                    fw, fh, st, seed=SEED + i * 31 + len(face),
                    trim_top=c.get("trim_t", 0) if band else 0,
                    trim_bottom=c.get("trim_b", 0) if band else 0,
                    glow=((fw // 2, fh // 2),) if (side and c.get("glow")) else (),
                )
            for yy in range(fh):
                for xx in range(fw):
                    atlas[fv + yy][fu + xx] = grid[yy][xx]

    tex = to_rgba(atlas, pal)
    gm_grid = [["c" if ch in ("C", "c") else "." for ch in row] for row in atlas]
    glowmask = to_rgba(gm_grid, {"c": (255, 255, 255)})

    bones = []
    for name, piv in BONES.items():
        cs = [{"origin": c["origin"], "size": c["size"], "uv": c["uv"]} for c in cubes if c["bone"] == name]
        if cs:
            bones.append({"name": name, "pivot": piv, "cubes": cs})
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.skyabyss_armor_{opt.lower()}",
                "texture_width": TEXW, "texture_height": TEXH,
                "visible_bounds_width": 3, "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": bones,
        }],
    }
    return geo, tex, glowmask, icons


def mannequin():
    def b(name, piv, origin, size):
        return {"name": name, "pivot": piv, "cubes": [{"origin": origin, "size": size, "uv": [0, 0]}]}
    return {"minecraft:geometry": [{"description": {"texture_width": 64, "texture_height": 64}, "bones": [
        b("head", [0, 24, 0], [-4, 24, -4], [8, 8, 8]),
        b("body", [0, 24, 0], [-4, 12, -2], [8, 12, 4]),
        b("rightArm", [-5, 22, 0], [-8, 12, -2], [4, 12, 4]),
        b("leftArm", [5, 22, 0], [4, 12, -2], [4, 12, 4]),
        b("rightLeg", [-1.9, 12, 0], [-4, 0, -2], [4, 12, 4]),
        b("leftLeg", [1.9, 12, 0], [0, 0, -2], [4, 12, 4]),
    ]}]}


def grid4(ims):
    w = ims[0].width
    out = Image.new("RGB", (2 * w + 8, 2 * w + 8), (18, 18, 26))
    for k, im in enumerate(ims):
        out.paste(im, ((k % 2) * (w + 8), (k // 2) * (w + 8)))
    return out


ICON_FILES = [("helmet", "helmet.png"), ("chest", "chestplate.png"), ("legs", "leggings.png"), ("boots", "boots.png")]
CONCEPT = {
    "A": ("王道・素直",
          "ヴァニラの兜・胸当て・脚絆・靴の形を、鉄灰の石板で作った。兜の前に鯨骨の帯、額の水色の結晶。胸の中央にも結晶。",
          "石板: 鉄灰 (M / L / D)。鯨骨の帯: 白 (W)。共鳴: 水色 (C / c)。",
          "形は一番素直。肩当ては 4x4 の箱で、腕の上に乗る。前面はアイコンの画素をそのまま板にした。"),
    "B": ("重厚・威圧",
          "同じ形を、濃い板金と苔で重くした。兜の両側に角、胸の前に鯨骨の肋 (リブ) の板。腕は 1 単位太い。",
          "石板: 濃い鉄灰 (M / D)。苔: 深緑 (G / g)。鯨骨: 黄みの白 (W / w)。共鳴は少なめ。",
          "角と肋の板が、他の案より大きい輪郭を作る。角は前面のアイコンの外に出る。"),
    "C": ("異形・神秘",
          "頭の上に背びれ型のクレスト、背中に噴気口の箱、つま先は前に出る鰭。ティール系のうろこ模様。",
          "石板: ティール (M / L / D)。鯨骨: 白 (W / w)。共鳴: 明るい水色 (C / c) を多めに。",
          "形はヴァニラに近いが、背びれ・噴気口・つま先の鰭で異形にした。左右は同じ形。"),
}


def main():
    grey = np.full((64, 64, 4), (150, 150, 150, 255), np.uint8)
    mann = mannequin()
    columns, notes = [], []
    for opt in ("A", "B", "C"):
        geo, tex, gm, icons = build(opt)
        probs = check_overlaps(geo)
        notes.append((opt, probs))
        d = OUT / "armor" / opt.lower()
        d.mkdir(parents=True, exist_ok=True)
        rgba_icons = {}
        for key, fname in ICON_FILES:
            rgba = to_rgba(icons[key], PAL[opt])
            Image.fromarray(rgba).save(d / fname)
            rgba_icons[key] = rgba
        (d / "geo.json").write_text(json.dumps(geo, ensure_ascii=False, indent=2), encoding="utf-8")
        Image.fromarray(tex).save(d / "texture.png")
        Image.fromarray(gm).save(d / "glowmask.png")

        faces = load_geo(mann, grey) + load_geo(geo, tex, glowmask=gm)
        front = render(faces, yaw=145, pitch=10, size=(440, 520))
        back = render(faces, yaw=35, pitch=10, size=(440, 520))
        ims = [icon_big(rgba_icons[k], px=8) for k, _ in ICON_FILES]
        texview = icon_big(tex, px=3)
        title = CONCEPT[opt][0]
        columns.append((f"{opt}  {title}", [grid4(ims), front, back, texview]))

    sheet_img = sheet(
        "岩鯨の甲殻 (armor) ラフ: A / B / C",
        columns,
        "上: アイコン 4 種 (頭・胴・脚・足、x8)  /  中: 正面 3/4 と背面 3/4 (灰の人形に装着)  /  下: テクスチャ 128x64 (x3)",
    )
    sheet_img.save(OUT / "armor.png")

    lines = [
        "# 岩鯨の甲殻 (armor) ラフ", "",
        "岩の鯨の背で見つかる甲殻。石板の鎧に鯨骨の縁取りと、共鳴の水色の小さな光を付ける。",
        "3案。シートは `docs/roughs/armor.png` (列 A/B/C)。素材は `docs/roughs/armor/<a|b|c>/`。", "",
        "共通の決まり: テクスチャ 128x64 (1画素 = 1単位)。骨は頭・胴・右腕・左腕・右脚・左脚・右足・左足の 8 つ。",
        "アイコンの形は `docs/roughs/vanilla_ref/iron_*.png` の輪郭を型にし、その中を石板・鯨骨・水色の模様で塗った。",
        "3D の前面は、アイコンの画素をそのまま板にする (1 画素 = 1 単位)。アイコンの透明な所は輪郭色で埋める。",
        "甲殻はプレイヤーの体の外側に置き、部品どうしは面で接する (重なりなし)。", "",
        "注意: 脚は 4 単位幅で、前面の上 5 行 (腰の帯) は 3D に入らない (脚の高さ 6 単位のため)。", "",
    ]
    for opt in ("A", "B", "C"):
        title, concept, color, feat = CONCEPT[opt]
        probs = dict(notes)[opt]
        check = "重なり検査 (check_overlaps): 0 件 (重なりなし)" if not probs else f"重なり検査: {len(probs)} 件 → " + "; ".join(probs)
        lines += [
            f"## {opt} {title}", "",
            f"- コンセプト: {concept}",
            f"- 色: {color}",
            f"- 特徴: {feat}",
            f"- {check}", "",
        ]
    (OUT / "armor.md").write_text("\n".join(lines), encoding="utf-8")
    for opt, probs in notes:
        print(opt, "overlaps:", probs if probs else "none")


if __name__ == "__main__":
    main()
