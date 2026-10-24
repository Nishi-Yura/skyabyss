"""技 (スキル) の素材を作る。

使い方:
    python tools/gen/ui/gen_skill_assets.py            # 採用版 (ADOPTED) を src に書き、ラフ 3 案と比較画像を docs に書く
    python tools/gen/ui/gen_skill_assets.py --roughs   # ラフ 3 案と比較画像だけ (src は触らない)

出力先 (src/main/resources/assets/skyabyss/ 以下):

- textures/gui/skill/icons.png  : 技のアイコン (256x64、32x32 の升目 8x2)。0..9 技 (Skill の並び)、10..13 木の印
                                  (剣士・弓使い・星術師・深淵術師)、14 錠、15 封じられた印
- textures/gui/skill/skills.png : 技の画面と小さな表示 (1024x512)。配置は SkillScreen.java / SkillHud.java / RpgTabs.java と同じ
    - (0,0) 600x368      : 技の画面の背景 (300x184 の 2 倍密度)
    - (s*48, 384) 44x44  : 技の印の枠 (s = 0 封じ・届かない、1 覚えられる、2 覚えた、3 極めた)。(192,384) 48x48 は選んだ印の輪
    - (240+s*56, 384) 52x52 : 木のつまみ (0 ふつう、1 選んだ、2 封じ)
    - (420,384) 44x44    : 画面の技の枠。(468,384) 40x40 は画面左上・ホットバー横の小さな枠
    - (240,440) 128x8    : マナのバーの枠。(240,450) 124x4 は中身 (月の青)
    - (0,470) / (116,470) 112x32 : 「記録」「技」のつまみ (ふつう / 選んだ)
- textures/particle/rune_spark_{0..3}.png、sky_mote_{0..3}.png (16x16) と particles/*.json
- sounds/rpg/skill/*.ogg : 技の音 10 種 (既存の音は使わず、ここで合成する)

docs/review/skill_icons_roughs/ に、アイコンのラフ 3 案 (a: 碑文、b: 骨と空、c: 紋章) と比較画像 compare.png を書く。
何度実行しても同じ物ができる (乱数の種を固定。ogg は符号化の通し番号でバイト列だけ毎回変わるが、音は同じ)。
"""
# tools 内の他グループのモジュールを import できるようにする（フォルダ移動後も兄弟モジュールを解決）
import os as _os
import sys as _sys
_sys.path[:0] = [_os.path.join(_os.path.dirname(_os.path.abspath(__file__)), p) for p in ('../items',)]
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_growth_assets import (OUTLINE, SLATE, SLATE_DARK, RECESS, BONE_LIGHT, BONE, BONE_MID, BONE_DARK,  # noqa: E402
                               TEAL, TEAL_DIM, PALE, GOLD, recess, gem, outline_sprite, reverb, bell)

ROOT = Path(__file__).resolve().parent.parent.parent.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
GUI = ASSETS / "textures/gui/skill"
PARTICLE_TEX = ASSETS / "textures/particle"
PARTICLE_DEF = ASSETS / "particles"
SOUND = ASSETS / "sounds/rpg/skill"
ROUGHS = ROOT / "docs/review/skill_icons_roughs"

# 比べて選んだ案 (docs/design/04 の 10.7 節に理由)
ADOPTED = "c"

rng = np.random.default_rng(20261017)

MOON = (190, 214, 255, 255)
MOON_DEEP = (90, 120, 220, 255)
CLOUD = (232, 238, 246, 255)
CLOUD_SHADE = (176, 190, 210, 255)
STONE = (128, 122, 112, 255)
STONE_DARK = (88, 84, 78, 255)
WOOD = (120, 78, 44, 255)
STEEL = (206, 214, 222, 255)
CRIMSON = (170, 40, 52, 255)


# ================================================================ アイコンの形 (3 案で共有する)
class Pen:
    """形を「役割」付きで記録する。案ごとに役割へ色を当てて描き直す。
    役割: blade (刃・骨)、edge (刃の光)、hilt (柄・木)、fx (技の光の線)、fx2 (控えめな光)、
          mass (石・体)、cloud (雲)、accent (金・瞳)"""

    def __init__(self):
        self.ops = []

    def line(self, pts, role, w=1):
        self.ops.append(("line", pts, role, w))

    def poly(self, pts, role):
        self.ops.append(("poly", pts, role, 0))

    def ellipse(self, box, role):
        self.ops.append(("ellipse", box, role, 0))

    def ring(self, box, role, w=1):
        self.ops.append(("ring", box, role, w))

    def arc(self, box, a0, a1, role, w=1):
        self.ops.append(("arc", (box, a0, a1), role, w))

    def dot(self, xy, role):
        self.ops.append(("dot", xy, role, 0))


def m_blade(p, tip, base, width=2):
    """斜めの刃 (先 → 根元)。width は片側の太さ"""
    (x0, y0), (x1, y1) = tip, base
    dx, dy = x1 - x0, y1 - y0
    n = math.hypot(dx, dy)
    nx, ny = -dy / n, dx / n
    a = (x0, y0)
    b = (x1 + nx * width, y1 + ny * width)
    c = (x1 - nx * width, y1 - ny * width)
    p.poly([a, b, c], "blade")
    p.line([(x0 + dx * 0.1, y0 + dy * 0.1), (x1 - nx * (width - 1), y1 - ny * (width - 1))], "edge")


def m_titan_cleave(p):
    # 振り下ろす大剣と、大きな弧
    p.arc((3, 3, 29, 29), 200, 330, "fx", 2)
    p.arc((6, 6, 26, 26), 210, 320, "fx2", 1)
    m_blade(p, (28, 4), (13, 19), 4)
    p.poly([(9, 17), (12, 14), (18, 20), (15, 23)], "hilt")
    p.line([(12, 21), (6, 27)], "hilt", 3)
    p.dot((4, 28), "accent")
    p.dot((5, 28), "accent")
    # 叩きつけた地面の割れ
    p.line([(18, 29), (22, 26), (26, 29)], "fx", 1)
    p.line([(22, 26), (22, 23)], "fx2", 1)


def m_riposte(p):
    # 受けた刃と、返す弧の矢印
    m_blade(p, (28, 8), (8, 24), 2)
    p.poly([(5, 22), (8, 19), (11, 22), (8, 25)], "hilt")
    p.arc((6, 4, 26, 24), 150, 330, "fx", 2)
    p.poly([(22, 3), (28, 8), (21, 10)], "fx")
    p.dot((14, 17), "accent")


def m_lunge(p):
    # 前へ突き出す刃と、速さの線
    p.poly([(30, 16), (14, 13), (14, 19)], "blade")
    p.line([(28, 16), (15, 16)], "edge")
    p.poly([(11, 10), (14, 10), (14, 22), (11, 22)], "hilt")
    p.line([(10, 16), (6, 16)], "hilt", 3)
    for y, x0 in [(8, 2), (24, 2), (12, 0), (20, 0)]:
        p.line([(x0, y), (x0 + 7, y)], "fx", 1)
    p.dot((4, 16), "accent")


def m_stone_stance(p):
    # 地に根を張る岩: 押さえつける山形の線と、地へ伸びる根
    p.poly([(5, 27), (8, 18), (13, 13), (20, 14), (25, 19), (27, 27)], "mass")
    p.line([(12, 17), (15, 22), (13, 26)], "fx2", 1)
    p.line([(21, 18), (19, 23)], "fx2", 1)
    p.line([(2, 28), (30, 28)], "fx", 1)
    p.line([(9, 28), (7, 31)], "fx2", 1)
    p.line([(16, 28), (16, 31)], "fx2", 1)
    p.line([(23, 28), (25, 31)], "fx2", 1)
    p.line([(8, 4), (16, 9), (24, 4)], "fx", 2)
    p.dot((16, 19), "accent")


def m_whale_sweep(p):
    # 鯨の尾びれと、巡る弧
    p.arc((2, 2, 30, 30), 20, 330, "fx", 1)
    p.poly([(16, 26), (14, 18), (16, 13), (18, 18)], "mass")
    p.poly([(16, 14), (6, 7), (4, 10), (10, 15), (16, 17)], "blade")
    p.poly([(16, 14), (26, 7), (28, 10), (22, 15), (16, 17)], "blade")
    p.line([(7, 9), (15, 15)], "edge")
    p.line([(25, 9), (17, 15)], "edge")
    p.poly([(26, 26), (30, 24), (28, 29)], "fx")
    p.dot((16, 22), "accent")


def m_arrow(p, tail, tip, head=4, fletch=True):
    (x0, y0), (x1, y1) = tail, tip
    dx, dy = x1 - x0, y1 - y0
    n = math.hypot(dx, dy)
    ux, uy = dx / n, dy / n
    nx, ny = -uy, ux
    p.line([tail, (x1 - ux * head * 0.6, y1 - uy * head * 0.6)], "hilt", 1)
    p.poly([(x1, y1), (x1 - ux * head + nx * head * 0.6, y1 - uy * head + ny * head * 0.6),
            (x1 - ux * head - nx * head * 0.6, y1 - uy * head - ny * head * 0.6)], "blade")
    if fletch:
        for s in (1, -1):
            p.line([(x0 + ux * 1, y0 + uy * 1), (x0 - ux * 2 + nx * 3 * s, y0 - uy * 2 + ny * 3 * s)], "fx2", 1)
            p.line([(x0 + ux * 3, y0 + uy * 3), (x0 + nx * 3 * s, y0 + ny * 3 * s)], "fx2", 1)


def m_cloud(p, cx, cy, s=1.0):
    p.ellipse((cx - 7 * s, cy - 2 * s, cx - 1 * s, cy + 3 * s), "cloud")
    p.ellipse((cx - 4 * s, cy - 5 * s, cx + 3 * s, cy + 2 * s), "cloud")
    p.ellipse((cx, cy - 3 * s, cx + 7 * s, cy + 3 * s), "cloud")


def m_cloud_pierce(p):
    m_cloud(p, 10, 13, 0.9)
    m_cloud(p, 23, 21, 0.8)
    p.line([(1, 20), (8, 15)], "fx", 1)
    m_arrow(p, (3, 26), (30, 6), 5)
    p.line([(0, 28), (5, 24)], "fx", 1)


def m_far_shot(p):
    # 遠い的と、点線の軌跡
    p.ring((19, 3, 29, 13), "fx", 1)
    p.ring((22, 6, 26, 10), "accent", 1)
    for i in range(5):
        x = 8 + i * 3
        y = 21 - i * 3
        p.dot((x, y), "fx2")
    m_arrow(p, (2, 30), (9, 22), 4)


def m_backstep(p):
    # 後ろへ跳ぶ曲がった矢印と、羽
    p.arc((6, 6, 28, 28), 190, 350, "fx", 2)
    p.poly([(4, 15), (9, 11), (10, 19)], "fx")
    p.poly([(18, 30), (22, 20), (26, 17), (24, 24)], "blade")
    p.line([(18, 30), (25, 19)], "edge")
    p.line([(10, 26), (14, 26)], "fx2", 1)
    p.line([(8, 23), (12, 23)], "fx2", 1)


def m_hawk_eye(p):
    # 鋭い目と、菱形の印
    p.poly([(2, 16), (10, 9), (22, 9), (30, 16), (22, 23), (10, 23)], "cloud")
    p.line([(2, 16), (10, 9), (22, 9), (30, 16)], "mass", 1)
    p.poly([(16, 11), (21, 16), (16, 21), (11, 16)], "accent")
    p.dot((16, 16), "mass")
    p.dot((15, 15), "edge")
    for (x0, y0, x1, y1) in [(16, 1, 16, 5), (16, 27, 16, 31), (0, 6, 3, 8), (32, 6, 29, 8)]:
        p.line([(x0, y0), (x1, y1)], "fx", 1)


def m_sky_rain(p):
    m_cloud(p, 15, 7, 1.15)
    for x in (6, 13, 20, 27):
        m_arrow(p, (x + 2, 13), (x - 2, 25 + (x % 3)), 3, fletch=False)
    p.line([(3, 30), (29, 30)], "fx2", 1)


def m_sword_emblem(p):
    m_blade(p, (16, 2), (16, 21), 3)
    p.poly([(9, 21), (23, 21), (23, 23), (9, 23)], "hilt")
    p.line([(16, 24), (16, 28)], "hilt", 3)
    p.ellipse((14, 28, 18, 31), "accent")


def m_bow_emblem(p):
    p.arc((4, 2, 26, 30), 280, 80, "hilt", 3)
    p.line([(19, 4), (19, 28)], "fx2", 1)
    m_arrow(p, (5, 16), (29, 16), 4)


def m_star_emblem(p):
    p.ellipse((4, 4, 26, 26), "cloud")
    p.ellipse((10, 1, 31, 22), "erase")
    cx, cy = 23, 22
    p.poly([(cx, cy - 6), (cx + 1.5, cy - 1.5), (cx + 6, cy), (cx + 1.5, cy + 1.5), (cx, cy + 6), (cx - 1.5, cy + 1.5),
            (cx - 6, cy), (cx - 1.5, cy - 1.5)], "accent")


def m_abyss_emblem(p):
    p.ring((3, 3, 29, 29), "fx2", 1)
    p.ring((7, 7, 25, 25), "fx", 1)
    p.ellipse((11, 9, 21, 23), "mass")
    p.poly([(16, 10), (18, 16), (16, 22), (14, 16)], "accent")
    p.line([(2, 16), (6, 16)], "fx", 1)
    p.line([(26, 16), (30, 16)], "fx", 1)


def m_lock(p):
    p.arc((19, 13, 28, 24), 180, 360, "blade", 2)
    p.poly([(17, 19), (30, 19), (30, 30), (17, 30)], "mass")
    p.dot((23, 23), "accent")
    p.line([(23, 24), (23, 26)], "accent", 1)


def m_sealed(p):
    p.arc((6, 6, 26, 22), 20, 160, "fx2", 1)
    for x0, x1 in [(9, 7), (16, 16), (23, 25)]:
        p.line([(x0, 20), (x1, 24)], "fx2", 1)
    p.ring((3, 3, 29, 29), "fx2", 1)


MOTIFS = [m_titan_cleave, m_riposte, m_lunge, m_stone_stance, m_whale_sweep,
          m_cloud_pierce, m_far_shot, m_backstep, m_hawk_eye, m_sky_rain,
          m_sword_emblem, m_bow_emblem, m_star_emblem, m_abyss_emblem, m_lock, m_sealed]
NAMES = ["巨神斬り", "返し刃", "踏み込み", "岩の構え", "鯨尾薙ぎ", "雲穿ち", "遠矢の心得", "跳び退き", "鷹の目", "天の驟雨",
         "剣士", "弓使い", "星術師", "深淵術師", "錠", "封"]
# 木 (紋章の地の色に使う): 0 剣、1 弓、2 星、3 淵、None は地なし
TREE_OF = [0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 1, 2, 3, None, None]


def draw_ops(img, ops, colors):
    d = ImageDraw.Draw(img)
    for kind, data, role, w in ops:
        c = colors.get(role)
        if role == "erase":
            c = (0, 0, 0, 0)
        if c is None:
            continue
        if kind == "line":
            d.line([tuple(map(round, pt)) for pt in data], fill=c, width=w)
        elif kind == "poly":
            d.polygon([tuple(map(round, pt)) for pt in data], fill=c)
        elif kind == "ellipse":
            d.ellipse(tuple(map(round, data)), fill=c)
        elif kind == "ring":
            d.ellipse(tuple(map(round, data)), outline=c, width=w)
        elif kind == "arc":
            box, a0, a1 = data
            d.arc(tuple(map(round, box)), a0, a1, fill=c, width=w)
        elif kind == "dot":
            d.point(tuple(map(round, data)), fill=c)


def motif_ops(i):
    p = Pen()
    MOTIFS[i](p)
    return p.ops


def dilate(mask, steps=1):
    out = mask.copy()
    for _ in range(steps):
        g = out.copy()
        for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            g |= np.roll(np.roll(out, dy, axis=0), dx, axis=1)
        out = g
    return out


# ---------------------------------------------------------------- 案 a: 碑文 (石板に刻まれ、青緑に光る線)
def style_a(i):
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if TREE_OF[i] is not None or i == 15:
        # 角を落とした石板
        d.rounded_rectangle([1, 1, 30, 30], radius=5, fill=(30, 35, 44, 255), outline=OUTLINE)
        d.line([3, 2, 28, 2], fill=(58, 66, 80, 255))
        d.line([2, 3, 2, 28], fill=(58, 66, 80, 255))
        d.line([4, 29, 28, 29], fill=(18, 20, 26, 255))
    glyph = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    teal = (127, 214, 208, 255)
    colors = {"blade": teal, "edge": (220, 252, 248, 255), "hilt": (96, 178, 176, 255), "fx": (159, 232, 255, 255),
              "fx2": (84, 160, 170, 255), "mass": (70, 140, 146, 255), "cloud": (110, 196, 200, 255),
              "accent": (242, 193, 78, 255)}
    draw_ops(glyph, motif_ops(i), colors)
    a = np.array(glyph)
    lit = a[..., 3] > 0
    halo = dilate(lit, 1) & ~lit
    base = np.array(img)
    hal = base.copy()
    hal[halo] = (36, 92, 100, 255)
    out = Image.fromarray(hal, "RGBA")
    out.alpha_composite(glyph)
    return out


# ---------------------------------------------------------------- 案 b: 骨と空 (素材の色で描く絵。地なし、黒い縁)
def style_b(i):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    colors = {"blade": BONE, "edge": BONE_LIGHT, "hilt": WOOD, "fx": PALE, "fx2": (110, 170, 190, 255),
              "mass": STONE, "cloud": CLOUD, "accent": GOLD}
    if i in (5, 6, 7, 9, 11):
        colors["blade"] = STEEL
        colors["edge"] = (245, 250, 255, 255)
    if i == 8:
        colors["mass"] = (40, 46, 60, 255)
    if i == 13:
        colors["mass"] = CRIMSON
        colors["fx"] = (200, 70, 80, 255)
        colors["fx2"] = (120, 40, 50, 255)
    if i == 12:
        colors["cloud"] = MOON
    draw_ops(img, motif_ops(i), colors)
    return outline_sprite(img)


# ---------------------------------------------------------------- 案 c: 紋章 (骨の縁の丸い地に、木ごとの色。形は骨の白)
TREE_FIELD = {0: ((150, 84, 52, 255), (92, 48, 32, 255)),    # 剣士: 熾の赤茶
              1: ((62, 118, 168, 255), (34, 66, 110, 255)),  # 弓使い: 空の青
              2: ((70, 70, 150, 255), (36, 36, 88, 255)),    # 星術師: 夜の藍
              3: ((128, 30, 44, 255), (60, 14, 22, 255))}    # 深淵術師: 深紅


def style_c(i):
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    tree = TREE_OF[i]
    if tree is not None:
        light, dark = TREE_FIELD[tree]
        yy, xx = np.mgrid[0:size, 0:size]
        r = np.hypot(xx - 15.5, yy - 15.5)
        t = np.clip((yy + xx) / 62.0, 0, 1)[..., None]
        field = (np.array(light) * (1 - t) + np.array(dark) * t).astype(np.uint8)
        a = np.zeros((size, size, 4), dtype=np.uint8)
        inside = r <= 13.2
        a[inside] = field[inside]
        rim = (r > 13.2) & (r <= 15.2)
        a[rim] = BONE_MID
        a[(r > 13.2) & (r <= 14.0) & (yy + xx < 31)] = BONE_LIGHT
        a[(r > 14.4) & (r <= 15.2) & (yy + xx > 31)] = BONE_DARK
        a[(r > 15.2) & (r <= 16.0)] = OUTLINE
        img = Image.fromarray(a, "RGBA")
    glyph = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    colors = {"blade": BONE_LIGHT, "edge": (255, 252, 240, 255), "hilt": BONE_MID, "fx": PALE, "fx2": (200, 230, 240, 255),
              "mass": BONE, "cloud": (246, 248, 252, 255), "accent": GOLD}
    if i == 8:
        colors["mass"] = (30, 34, 46, 255)
    if i == 15:
        colors["fx2"] = (110, 116, 130, 255)
    draw_ops(glyph, motif_ops(i), colors)
    # 形に暗い縁 (地の上でも読めるように)
    glyph = outline_sprite(glyph)
    if tree is not None:
        # 縁は地の暗い色に寄せる (黒すぎない)
        a = np.array(glyph)
        edge = (a[..., :3] == np.array(OUTLINE[:3])).all(axis=2) & (a[..., 3] > 0)
        a[edge] = (24, 18, 20, 230)
        glyph = Image.fromarray(a, "RGBA")
    img.alpha_composite(glyph)
    return img


STYLES = {"a": style_a, "b": style_b, "c": style_c}
STYLE_NAMES = {"a": "A 碑文 (石板に光る線)", "b": "B 骨と空 (素材の絵)", "c": "C 紋章 (木ごとの色の丸い地)"}


def icon_sheet(style):
    sheet = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    for i in range(16):
        sheet.paste(STYLES[style](i), ((i % 8) * 32, (i // 8) * 32))
    return sheet


# ================================================================ 画面 (skills.png)
W, H = 300, 184


def S(v):
    return v * 2


def stone(w, h):
    yy = np.linspace(0, 1, h)[:, None]
    base = np.array(SLATE[:3], dtype=float) * (1 - yy * 0.25) + np.array(SLATE_DARK[:3], dtype=float) * (yy * 0.25)
    noise = rng.normal(0, 2.2, (h, w))[..., None]
    rgb = np.clip(base[:, None, :].repeat(w, axis=1) + noise, 0, 255).astype(np.uint8)
    alpha = np.full((h, w, 1), 255, dtype=np.uint8)
    return Image.fromarray(np.concatenate([rgb, alpha], axis=2), "RGBA")


def bone_frame(d, w, h):
    d.rectangle([0, 0, w - 1, h - 1], outline=OUTLINE)
    for i in range(1, 7):
        c = BONE_LIGHT if i <= 2 else BONE if i <= 4 else BONE_MID
        d.rectangle([i, i, w - 1 - i, h - 1 - i], outline=c)
    for i in range(1, 7):
        d.line([w - 1 - i, i, w - 1 - i, h - 1 - i], fill=BONE_DARK if i <= 3 else BONE_MID)
        d.line([i, h - 1 - i, w - 1 - i, h - 1 - i], fill=BONE_DARK if i <= 3 else BONE_MID)
    d.rectangle([7, 7, w - 8, h - 8], outline=OUTLINE)
    for x in range(24, w - 16, 24):
        d.line([x, 2, x, 6], fill=BONE_DARK)
        d.line([x + 1, 2, x + 1, 6], fill=BONE_LIGHT)
        d.line([x, h - 7, x, h - 3], fill=BONE_DARK)
    for y in range(24, h - 16, 24):
        d.line([2, y, 6, y], fill=BONE_DARK)
        d.line([w - 7, y, w - 3, y], fill=BONE_DARK)
    for cx, cy in [(7, 7), (w - 8, 7), (7, h - 8), (w - 8, h - 8)]:
        gem(d, cx, cy, 6)


def panel():
    w, h = S(W), S(H)
    img = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    img.paste(stone(w, h), (0, 0))
    d = ImageDraw.Draw(img)
    bone_frame(d, w, h)
    # 題の下の線
    ty = S(19)
    d.line([S(16), ty, w - S(16), ty], fill=TEAL_DIM)
    d.line([S(40), ty + 1, w - S(40), ty + 1], fill=(30, 60, 70, 255))
    gem(d, w // 2, ty, 4)
    # 木のつまみの列 (細いくぼみ)
    recess(d, S(6), S(23), S(36), S(148), fill=(19, 22, 29, 255))
    # 木の場 (碑文の刻まれた石)
    recess(d, S(38), S(22), S(127), S(150), fill=(22, 25, 33, 255))
    # 木の場に、薄い肋骨の線 (背景の飾り)
    for i in range(5):
        y = S(40) + i * S(24)
        d.arc([S(42), y - S(10), S(123), y + S(14)], 200, 340, fill=(30, 34, 44, 255))
    # 説明の欄
    recess(d, S(130), S(22), S(292), S(150), fill=(19, 22, 29, 255))
    # 下の区切り
    sy = S(152)
    d.line([S(10), sy, w - S(10), sy], fill=TEAL_DIM)
    d.line([S(10), sy + 1, w - S(10), sy + 1], fill=OUTLINE)
    # 枠の列の下地
    recess(d, S(129), S(155), S(226), S(181), fill=(19, 22, 29, 255))
    return img


def node_frame(state):
    """技の印の枠 44x44。0 封じ・届かない、1 覚えられる、2 覚えた、3 極めた"""
    f = Image.new("RGBA", (44, 44), (0, 0, 0, 0))
    d = ImageDraw.Draw(f)
    rim = [(64, 68, 78, 255), BONE_MID, TEAL, GOLD][state]
    rim_light = [(84, 88, 98, 255), BONE_LIGHT, (190, 245, 240, 255), (255, 230, 150, 255)][state]
    rim_dark = [(40, 42, 50, 255), BONE_DARK, TEAL_DIM, (160, 110, 30, 255)][state]
    d.rectangle([0, 0, 43, 43], fill=OUTLINE)
    d.rectangle([1, 1, 42, 42], fill=rim)
    d.line([1, 1, 42, 1], fill=rim_light)
    d.line([1, 1, 1, 42], fill=rim_light)
    d.line([2, 42, 42, 42], fill=rim_dark)
    d.line([42, 2, 42, 42], fill=rim_dark)
    inner = [(16, 18, 23, 255), (20, 23, 30, 255), (18, 34, 40, 255), (34, 30, 20, 255)][state]
    d.rectangle([4, 4, 39, 39], fill=OUTLINE)
    d.rectangle([5, 5, 38, 38], fill=inner)
    if state >= 2:
        # 内側にかすかな光
        d.rectangle([5, 5, 38, 5], fill=tuple(min(255, c + 30) for c in inner[:3]) + (255,))
    return f


def selection_ring():
    f = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, 47, 47], outline=(220, 252, 255, 255), width=2)
    d.rectangle([2, 2, 45, 45], outline=(127, 214, 208, 160), width=1)
    for cx, cy in [(1, 1), (46, 1), (1, 46), (46, 46)]:
        d.rectangle([cx - 2, cy - 2, cx + 2, cy + 2], fill=PALE)
    return f


def tree_tab(state):
    f = Image.new("RGBA", (52, 52), (0, 0, 0, 0))
    d = ImageDraw.Draw(f)
    rim = [BONE_MID, BONE_LIGHT, (70, 72, 80, 255)][state]
    fill = [(26, 30, 38, 255), (24, 52, 58, 255), (18, 19, 23, 255)][state]
    d.rectangle([0, 0, 51, 51], fill=OUTLINE)
    d.rectangle([1, 1, 50, 50], fill=rim)
    d.rectangle([4, 4, 47, 47], fill=OUTLINE)
    d.rectangle([5, 5, 46, 46], fill=fill)
    if state == 1:
        d.rectangle([5, 5, 46, 46], outline=TEAL)
        d.line([0, 0, 0, 51], fill=PALE)
    if state == 2:
        # 封じ: 斜めの鎖の線
        for k in range(-40, 60, 10):
            d.line([5 + k, 46, 5 + k + 41, 5], fill=(30, 31, 36, 255))
    return f


def slot_frame(size, hud):
    f = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(f)
    if hud:
        d.rectangle([0, 0, size - 1, size - 1], fill=(10, 12, 16, 200))
        d.rectangle([1, 1, size - 2, size - 2], outline=(150, 140, 116, 220))
        d.rectangle([3, 3, size - 4, size - 4], fill=(16, 18, 24, 190))
        for cx, cy in [(1, 1), (size - 2, 1), (1, size - 2), (size - 2, size - 2)]:
            d.point((cx, cy), fill=TEAL)
        return f
    d.rectangle([0, 0, size - 1, size - 1], fill=OUTLINE)
    d.rectangle([1, 1, size - 2, size - 2], fill=BONE)
    d.line([1, 1, size - 2, 1], fill=BONE_LIGHT)
    d.line([2, size - 2, size - 2, size - 2], fill=BONE_DARK)
    d.rectangle([4, 4, size - 5, size - 5], fill=OUTLINE)
    d.rectangle([5, 5, size - 6, size - 6], fill=(14, 16, 22, 255))
    for cx, cy in [(6, 6), (size - 7, 6), (6, size - 7), (size - 7, size - 7)]:
        d.point((cx, cy), fill=TEAL_DIM)
    return f


def screen_tab(active):
    f = Image.new("RGBA", (112, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(f)
    body = SLATE if active else (22, 25, 32, 255)
    d.rectangle([0, 0, 111, 31], fill=OUTLINE)
    d.rectangle([1, 1, 110, 31], fill=BONE_LIGHT if active else BONE_MID)
    d.rectangle([4, 4, 107, 31], fill=OUTLINE)
    d.rectangle([5, 5, 106, 31], fill=body)
    if active:
        # 画面の縁とつながる (下は開いている)
        d.rectangle([1, 28, 110, 31], fill=body)
        d.line([0, 28, 0, 31], fill=BONE_LIGHT)
        d.line([111, 28, 111, 31], fill=BONE_DARK)
        gem(d, 56, 3, 3)
    else:
        d.line([0, 31, 111, 31], fill=OUTLINE)
    return f


def strip(width, height, top, bottom):
    a = np.zeros((height, width, 4), dtype=np.uint8)
    for y in range(height):
        t = y / max(1, height - 1)
        a[y, :, :3] = (np.array(top[:3]) * (1 - t) + np.array(bottom[:3]) * t).astype(np.uint8)
        a[y, :, 3] = 255
    a[:, ::6, :3] = np.clip(a[:, ::6, :3].astype(int) + 22, 0, 255).astype(np.uint8)
    return Image.fromarray(a, "RGBA")


def skills_texture():
    img = panel()
    for s in range(4):
        img.paste(node_frame(s), (s * 48, 384))
    img.paste(selection_ring(), (192, 384))
    for s in range(3):
        img.paste(tree_tab(s), (240 + s * 56, 384))
    img.paste(slot_frame(44, False), (420, 384))
    img.paste(slot_frame(40, True), (468, 384))
    # マナのバー (小さな表示)
    bar = Image.new("RGBA", (128, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(bar)
    d.rectangle([0, 0, 127, 7], fill=OUTLINE)
    d.rectangle([1, 1, 126, 6], fill=BONE_MID)
    d.line([1, 1, 126, 1], fill=BONE_LIGHT)
    d.rectangle([2, 2, 125, 5], fill=RECESS)
    img.paste(bar, (240, 440))
    img.paste(strip(124, 4, (184, 196, 255), (88, 92, 210)), (240, 450))
    img.paste(screen_tab(False), (0, 470))
    img.paste(screen_tab(True), (116, 470))
    return img


# ================================================================ 光の粒
def particle_frames(kind):
    frames = []
    for f in range(4):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        c = 7.5
        if kind in ("rune_spark", "sky_mote"):
            # 柔らかい光の点 (白い十字や角ばった四角にしない)。中心は明るく、縁へ向けて消える
            s = ([3.4, 2.8, 2.0, 1.4] if kind == "rune_spark" else [2.6, 2.1, 1.6, 1.1])[f]
            ys, xs = np.mgrid[0:16, 0:16]
            dist2 = (xs + 0.5 - c) ** 2 + (ys + 0.5 - c) ** 2
            a = np.exp(-dist2 / (2 * s * s))
            px = np.zeros((16, 16, 4), dtype=np.uint8)
            cool = np.array([127, 214, 208] if kind == "rune_spark" else [150, 200, 255], dtype=float)
            hot = np.array([236, 255, 252] if kind == "rune_spark" else [240, 248, 255], dtype=float)
            mix = np.clip(a * 1.4, 0, 1)[..., None]
            px[..., :3] = (cool * (1 - mix) + hot * mix).astype(np.uint8)
            px[..., 3] = (np.clip(a, 0, 1) * 255).astype(np.uint8)
            img = Image.fromarray(px, "RGBA")
            frames.append(img)
            continue
        else:
            r = [4, 3, 2, 1][f]
            d.polygon([(c, c - r - 2), (c + r, c), (c, c + r + 2), (c - r, c)], fill=(170, 200, 255, 140))
            d.polygon([(c, c - r), (c + max(1, r - 1), c), (c, c + r), (c - max(1, r - 1), c)], fill=(236, 244, 255, 255))
        frames.append(img)
    return frames


# ================================================================ 音
SR = 44100


def noise(seconds):
    return rng.standard_normal(int(SR * seconds))


def env(n, attack, release, shape=2.0):
    t = np.linspace(0, 1, n)
    a = np.clip(t / max(attack, 1e-6), 0, 1)
    r = np.clip((1 - t) / max(release, 1e-6), 0, 1) ** shape
    return a * r


def bandpass_sweep(x, f0, f1, q=4.0):
    """周波数を f0 → f1 へ動かす簡単な共振フィルタ (状態変数フィルタ)"""
    n = len(x)
    freqs = np.geomspace(f0, f1, n)
    low = band = 0.0
    out = np.zeros(n)
    damp = 1.0 / q
    for i in range(n):
        fc = 2 * math.sin(math.pi * min(freqs[i], SR / 6) / SR)
        high = x[i] - low - damp * band
        band += fc * high
        low += fc * band
        out[i] = band
    return out


def lowpass(x, fc):
    a = math.exp(-2 * math.pi * fc / SR)
    out = np.zeros_like(x)
    y = 0.0
    for i in range(len(x)):
        y = (1 - a) * x[i] + a * y
        out[i] = y
    return out


def glide(f0, f1, seconds, decay):
    t = np.arange(int(SR * seconds)) / SR
    f = np.geomspace(f0, f1, len(t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * decay)


def place(out, sig, start, gain=1.0):
    s = int(SR * start)
    e = min(len(out), s + len(sig))
    out[s:e] += sig[:e - s] * gain


def finish(x, peak=0.85, tail=0.25, verb=0.0, verb_len=1.2):
    if verb > 0:
        x = reverb(x, verb_len, verb)
    n = int(SR * tail)
    if n > 0 and len(x) > n:
        x[-n:] *= np.linspace(1, 0, n)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    return x.astype(np.float32)


def snd_cleave():
    out = np.zeros(int(SR * 1.0))
    w = bandpass_sweep(noise(0.42), 180, 900, 3.0) * env(int(SR * 0.42), 0.35, 0.5)
    place(out, w, 0.0, 1.0)
    t = np.arange(int(SR * 0.5)) / SR
    thump = np.sin(2 * np.pi * 62 * t) * np.exp(-t * 9)
    place(out, thump, 0.30, 1.1)
    crack = lowpass(noise(0.12), 2400) * env(int(SR * 0.12), 0.02, 0.9, 3)
    place(out, crack, 0.30, 0.9)
    place(out, bell(330, 0.7, 4.0), 0.31, 0.25)
    return finish(out, tail=0.3, verb=0.25)


def snd_riposte():
    out = np.zeros(int(SR * 0.7))
    place(out, bell(1175, 0.5, 7.0), 0.0, 0.6)
    place(out, bell(1568, 0.5, 8.0), 0.06, 0.5)
    place(out, lowpass(noise(0.05), 5000) * env(int(SR * 0.05), 0.05, 0.9), 0.0, 0.4)
    return finish(out, tail=0.2, verb=0.25)


def snd_lunge():
    out = np.zeros(int(SR * 0.5))
    w = bandpass_sweep(noise(0.22), 300, 2200, 3.5) * env(int(SR * 0.22), 0.6, 0.35)
    place(out, w, 0.0, 1.0)
    t = np.arange(int(SR * 0.25)) / SR
    place(out, np.sin(2 * np.pi * 90 * t) * np.exp(-t * 18), 0.18, 0.6)
    return finish(out, tail=0.15, verb=0.15)


def snd_sweep():
    out = np.zeros(int(SR * 1.0))
    n = int(SR * 0.62)
    w = bandpass_sweep(noise(0.62), 240, 1300, 2.5)
    t = np.arange(n) / SR
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * 9 * t)
    place(out, w * env(n, 0.4, 0.5) * trem, 0.0, 1.0)
    t2 = np.arange(int(SR * 0.9)) / SR
    place(out, np.sin(2 * np.pi * 98 * t2) * np.exp(-t2 * 3.5) * 0.5, 0.05, 0.6)
    return finish(out, tail=0.3, verb=0.3)


def snd_pierce():
    out = np.zeros(int(SR * 0.7))
    place(out, glide(2100, 900, 0.3, 9.0), 0.0, 0.55)
    hiss = bandpass_sweep(noise(0.18), 3500, 1500, 5.0) * env(int(SR * 0.18), 0.05, 0.7)
    place(out, hiss, 0.0, 0.9)
    place(out, bell(880, 0.5, 6.0), 0.02, 0.2)
    return finish(out, tail=0.2, verb=0.3)


def snd_backstep():
    out = np.zeros(int(SR * 0.45))
    w = lowpass(noise(0.28), 900) * env(int(SR * 0.28), 0.25, 0.6)
    place(out, w, 0.0, 1.0)
    place(out, glide(520, 700, 0.2, 10.0), 0.0, 0.15)
    return finish(out, peak=0.7, tail=0.15, verb=0.2)


def snd_mark():
    out = np.zeros(int(SR * 0.8))
    place(out, bell(1568, 0.6, 6.0), 0.0, 0.5)
    place(out, bell(2093, 0.5, 9.0), 0.04, 0.25)
    return finish(out, peak=0.6, tail=0.25, verb=0.35)


def snd_rain():
    total = 3.2
    out = np.zeros(int(SR * total))
    # 降る光の、小さな鈴の粒
    for k in range(46):
        t0 = rng.uniform(0.05, 3.0)
        f = rng.choice([1760, 1976, 2349, 2637, 2960, 3136])
        place(out, bell(f, 0.25, 18.0) * rng.uniform(0.15, 0.35), t0)
    # 6 度の、空を切る音 (技の 0.5 秒ごとの当たりに合わせる)
    for k in range(6):
        place(out, bandpass_sweep(noise(0.16), 2600, 700, 4.0) * env(int(SR * 0.16), 0.1, 0.6), 0.42 + k * 0.5, 0.35)
    # 底に薄い風
    bed = lowpass(noise(total), 500)
    out += bed * np.sin(np.pi * np.clip(np.arange(len(bed)) / len(bed), 0, 1)) * 0.25
    return finish(out, peak=0.75, tail=0.5, verb=0.3, verb_len=1.6)


def snd_learn():
    out = np.zeros(int(SR * 1.4))
    for f, s, a in [(587.33, 0.0, 0.5), (739.99, 0.09, 0.45), (880.0, 0.18, 0.4)]:
        place(out, bell(f, 1.2 - s, 3.2), s, a)
    return finish(out, peak=0.7, tail=0.4, verb=0.35)


def snd_fail():
    out = np.zeros(int(SR * 0.35))
    place(out, glide(190, 120, 0.25, 16.0), 0.0, 0.8)
    place(out, lowpass(noise(0.08), 700) * env(int(SR * 0.08), 0.05, 0.8), 0.0, 0.4)
    return finish(out, peak=0.55, tail=0.1)


SOUNDS = {"cleave": snd_cleave, "riposte": snd_riposte, "lunge": snd_lunge, "sweep": snd_sweep, "pierce": snd_pierce,
          "backstep": snd_backstep, "mark": snd_mark, "rain": snd_rain, "learn": snd_learn, "fail": snd_fail}


# ================================================================ ラフと比較
def compare_image(sheets):
    scale = 3
    pad = 16
    cell = 32 * scale + 12
    font = None
    for name in ["C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc", "C:/Windows/Fonts/YuGothM.ttc"]:
        if Path(name).exists():
            font = ImageFont.truetype(name, 14)
            break
    font = font or ImageFont.load_default()
    rows = len(sheets)
    width = pad * 2 + cell * 8 + 330
    row = cell * 2 + 70
    height = pad + rows * row + 10
    img = Image.new("RGBA", (width, height), (20, 22, 28, 255))
    d = ImageDraw.Draw(img)
    frames = [node_frame(s) for s in range(4)]
    for r, (key, sheet) in enumerate(sheets.items()):
        y0 = pad + r * row
        d.text((pad, y0), STYLE_NAMES[key], fill=(242, 193, 78), font=font)
        for i in range(16):
            icon = sheet.crop(((i % 8) * 32, (i // 8) * 32, (i % 8) * 32 + 32, (i // 8) * 32 + 32))
            x = pad + (i % 8) * cell
            y = y0 + 22 + (i // 8) * (cell + 20)
            d.rectangle([x - 2, y - 2, x + 32 * scale + 1, y + 32 * scale + 1], fill=(30, 34, 42))
            img.alpha_composite(icon.resize((32 * scale, 32 * scale), Image.NEAREST), (x, y))
            d.text((x, y + 32 * scale), NAMES[i], fill=(200, 200, 210), font=font)
        # 実際の大きさで、画面の枠に入れたところ (2 倍の画面と同じ見え方)
        cx = pad + cell * 8 + 20
        d.text((cx, y0 + 22), "画面の大きさ (2 倍)", fill=(170, 174, 188), font=font)
        for i in range(10):
            fx = cx + (i % 5) * 50
            fy = y0 + 46 + (i // 5) * 56
            state = [3, 2, 2, 1, 0, 2, 3, 1, 2, 0][i]
            img.alpha_composite(frames[state], (fx, fy))
            icon = sheet.crop(((i % 8) * 32, (i // 8) * 32, (i % 8) * 32 + 32, (i // 8) * 32 + 32))
            if state == 0:
                a = np.array(icon).astype(float)
                a[..., :3] *= np.array([0.42, 0.42, 0.48])
                icon = Image.fromarray(a.astype(np.uint8), "RGBA")
            img.alpha_composite(icon, (fx + 6, fy + 6))
    return img


def write_roughs():
    ROUGHS.mkdir(parents=True, exist_ok=True)
    sheets = {}
    for key in STYLES:
        sheet = icon_sheet(key)
        (ROUGHS / key).mkdir(exist_ok=True)
        sheet.save(ROUGHS / key / "icons.png")
        sheets[key] = sheet
    compare_image(sheets).save(ROUGHS / "compare.png")
    print("wrote", ROUGHS / "compare.png")


def main():
    write_roughs()
    if "--roughs" in sys.argv:
        return
    import soundfile as sf

    GUI.mkdir(parents=True, exist_ok=True)
    icon_sheet(ADOPTED).save(GUI / "icons.png")
    skills_texture().save(GUI / "skills.png")
    PARTICLE_TEX.mkdir(parents=True, exist_ok=True)
    PARTICLE_DEF.mkdir(parents=True, exist_ok=True)
    for kind in ("rune_spark", "sky_mote"):
        names = []
        for f, img in enumerate(particle_frames(kind)):
            img.save(PARTICLE_TEX / f"{kind}_{f}.png")
            names.append(f'"skyabyss:{kind}_{f}"')
        (PARTICLE_DEF / f"{kind}.json").write_text('{\n  "textures": [' + ", ".join(names) + "]\n}\n", encoding="utf-8")
    SOUND.mkdir(parents=True, exist_ok=True)
    for name, fn in SOUNDS.items():
        data = fn()
        sf.write(SOUND / f"{name}.ogg", data, SR, format="OGG", subtype="VORBIS")
        print(f"{name}.ogg  {len(data) / SR:.2f}s")
    print("wrote", GUI / "icons.png", GUI / "skills.png")


if __name__ == "__main__":
    main()
