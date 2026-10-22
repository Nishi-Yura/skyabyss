"""ビルド (5 つの木・36 の技・刻印・天職) の画面の素材を作る。docs/design/27_builds_and_classes.md、04 の 7.8。

使い方:
    python tools/gen/ui/gen_builds_assets.py

出力先 (src/main/resources/assets/skyabyss/textures/gui/skill/):

- icons.png        : 256x192、32x32 の升目 8x6。0..35 技 (Skill の宣言の並び)、36..43 木の印 (SkillTree の並び:
                     岩守り・風読み・潮歌い・灰の鍛え人・骨の射手・旅人・星術師・深淵術師)、44 錠、45 封じられた印
- builds_panel.png : 技の画面 (380x184 の 2 倍密度 = 760x368)。(0,376) から 44x44 が 4 つ = 要の印の枠
                     (0 届かない、1 覚えられる、2 覚えた、3 極めた)。配置は SkillScreen.java と同じ

形は「役割」付きで記録し (gen_skill_assets.Pen)、紋章案 (骨の縁の丸い地・木ごとの色) で色を当てる。
何度実行しても同じ物ができる。前の技 10 本の形は gen_skill_assets のものをそのまま使う。
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_skill_assets as base  # noqa: E402
from gen_skill_assets import (Pen, m_blade, m_arrow, m_cloud, draw_ops, outline_sprite, OUTLINE, BONE_LIGHT, BONE_MID,  # noqa: E402
                              BONE, BONE_DARK, GOLD, PALE, TEAL, TEAL_DIM, stone, bone_frame, recess, gem, node_frame)

ROOT = Path(__file__).resolve().parent.parent.parent.parent
GUI = ROOT / "src/main/resources/assets/skyabyss/textures/gui/skill"

# 木の地の色 (明るい側, 暗い側)。0 岩守り 1 風読み 2 潮歌い 3 灰の鍛え人 4 骨の射手 5 旅人 6 星術師 7 深淵術師
TREE_FIELD = {
    0: ((134, 112, 92, 255), (76, 62, 50, 255)),
    1: ((82, 160, 150, 255), (38, 92, 104, 255)),
    2: ((62, 118, 168, 255), (34, 66, 110, 255)),
    3: ((176, 86, 44, 255), (98, 40, 28, 255)),
    4: ((124, 134, 104, 255), (66, 74, 58, 255)),
    5: ((184, 146, 72, 255), (108, 80, 40, 255)),
    6: ((70, 70, 150, 255), (36, 36, 88, 255)),
    7: ((128, 30, 44, 255), (60, 14, 22, 255)),
}


# ================================================================ 新しい技の形 (32x32)
def m_rock_call(p):
    # 岩と、広がる声の弧
    p.poly([(6, 27), (8, 17), (13, 12), (20, 13), (25, 18), (27, 27)], "mass")
    p.line([(13, 17), (16, 23), (14, 27)], "fx2", 1)
    p.arc((0, 2, 32, 32), 215, 325, "fx", 2)
    p.arc((5, 6, 27, 28), 220, 320, "fx2", 1)
    p.dot((19, 19), "accent")


def m_stone_oath(p):
    # 二つの石をつなぐ約束の環
    p.ellipse((3, 14, 14, 26), "mass")
    p.ellipse((18, 14, 29, 26), "mass")
    p.ring((9, 12, 23, 26), "fx", 2)
    p.line([(14, 20), (18, 20)], "accent", 2)
    p.line([(16, 4), (16, 11)], "fx2", 1)
    p.poly([(12, 5), (16, 1), (20, 5), (16, 9)], "accent")


def m_nimble(p):
    # 羽のついた足と風の線
    p.poly([(8, 24), (10, 14), (18, 14), (18, 20), (26, 22), (26, 27), (8, 27)], "hilt")
    p.line([(8, 24), (26, 24)], "mass", 1)
    p.poly([(11, 13), (3, 6), (4, 12), (1, 11), (6, 16)], "blade")
    p.poly([(14, 13), (8, 3), (11, 11)], "edge")
    for y, x0 in [(8, 20), (13, 23), (18, 25)]:
        p.line([(x0, y), (x0 + 6, y)], "fx2", 1)


def m_foresight(p):
    # 危ない円と、その外へ抜ける矢印
    p.ring((2, 8, 20, 26), "fx2", 2)
    p.ring((6, 12, 16, 22), "fx2", 1)
    p.line([(12, 17), (27, 6)], "fx", 2)
    p.poly([(23, 3), (30, 4), (28, 11)], "fx")
    p.poly([(18, 21), (24, 17), (22, 25)], "accent")


def m_wind_rend(p):
    # 交差する二つの斬撃
    p.poly([(2, 5), (6, 3), (29, 26), (25, 29)], "blade")
    p.poly([(30, 5), (26, 3), (3, 26), (7, 29)], "blade")
    p.line([(5, 5), (27, 27)], "edge")
    p.line([(27, 5), (5, 27)], "edge")
    p.arc((6, 6, 26, 26), 200, 340, "fx", 1)
    p.dot((16, 16), "accent")


def m_lone_wind(p):
    # ひとり渦巻く風
    p.arc((4, 4, 28, 28), 20, 300, "fx", 2)
    p.arc((9, 9, 23, 23), 60, 330, "fx2", 2)
    p.arc((13, 13, 19, 19), 100, 340, "fx", 1)
    p.ellipse((14, 14, 18, 18), "accent")
    p.poly([(26, 20), (31, 22), (28, 27)], "fx")


def m_ward_song(p):
    # 音符を包む守りの殻
    p.arc((2, 2, 30, 30), 190, 350, "fx", 2)
    p.ellipse((8, 18, 15, 25), "mass")
    p.ellipse((18, 15, 25, 22), "mass")
    p.line([(14, 21), (14, 8)], "blade", 2)
    p.line([(24, 18), (24, 6)], "blade", 2)
    p.line([(14, 8), (24, 6)], "blade", 2)
    p.dot((16, 28), "accent")
    p.line([(8, 29), (24, 29)], "fx2", 1)


def m_calm_heart(p):
    # 凪いだ水面と一粒の滴
    for y in (13, 19, 25):
        p.arc((3, y - 3, 15, y + 3), 180, 360, "fx", 1)
        p.arc((15, y - 3, 29, y + 3), 0, 180, "fx", 1)
    p.poly([(16, 2), (20, 9), (16, 12), (12, 9)], "cloud")
    p.dot((16, 8), "accent")


def m_ebb_tide(p):
    # 一点へ引き寄せる波
    for y in (6, 16, 26):
        p.arc((1, y - 4, 17, y + 4), 180, 360, "fx", 1)
    p.line([(18, 6), (26, 14)], "fx", 2)
    p.line([(18, 26), (26, 18)], "fx", 2)
    p.line([(18, 16), (28, 16)], "fx", 2)
    p.poly([(26, 11), (31, 16), (26, 21)], "accent")


def m_ripple(p):
    # 広がる波紋
    p.ring((14, 14, 18, 18), "accent", 1)
    p.ring((9, 9, 23, 23), "fx", 1)
    p.ring((4, 4, 28, 28), "fx", 2)
    p.ring((0, 0, 32, 32), "fx2", 1)
    p.dot((16, 16), "accent")


def m_cycle_song(p):
    # めぐる矢印と音符
    p.arc((3, 3, 29, 29), 30, 280, "fx", 2)
    p.poly([(5, 14), (11, 13), (9, 20)], "fx")
    p.ellipse((11, 17, 17, 23), "mass")
    p.line([(16, 20), (16, 9)], "blade", 2)
    p.line([(16, 9), (22, 11)], "blade", 2)


def m_chorus_voice(p):
    # 輪になった仲間と、中心の声
    for a in range(0, 360, 72):
        x = 16 + 11 * math.cos(math.radians(a - 90))
        y = 16 + 11 * math.sin(math.radians(a - 90))
        p.ellipse((x - 3, y - 3, x + 3, y + 3), "mass")
    p.ring((11, 11, 21, 21), "fx", 1)
    p.ellipse((14, 14, 18, 18), "accent")
    p.ring((1, 1, 31, 31), "fx2", 1)


def m_temper(p):
    # 炎をまとった刃
    m_blade(p, (28, 4), (10, 22), 3)
    p.poly([(5, 21), (10, 17), (15, 22), (10, 26)], "hilt")
    p.poly([(15, 18), (18, 8), (21, 15), (24, 9), (24, 19), (20, 22)], "accent")
    p.poly([(18, 18), (20, 13), (22, 18), (20, 21)], "edge")
    p.dot((6, 6), "fx")
    p.dot((9, 3), "fx2")


def m_ember_heart(p):
    # 熾火を抱く盾の輪
    p.ring((3, 3, 29, 29), "fx2", 2)
    p.ellipse((9, 10, 23, 24), "mass")
    p.poly([(12, 22), (14, 14), (16, 18), (18, 12), (20, 22)], "accent")
    p.poly([(14, 22), (16, 17), (18, 22)], "edge")
    p.line([(16, 2), (16, 6)], "fx", 1)
    p.line([(16, 26), (16, 30)], "fx", 1)


def m_ember_hammer(p):
    # 振り下ろす槌と、散る熾
    p.line([(8, 27), (21, 9)], "hilt", 3)
    p.poly([(15, 3), (29, 9), (25, 16), (11, 10)], "mass")
    p.line([(13, 5), (27, 11)], "edge", 1)
    for x, y in [(5, 22), (10, 29), (15, 25), (22, 28), (27, 24)]:
        p.dot((x, y), "accent")
    p.line([(3, 29), (29, 29)], "fx", 1)


def m_forge_memory(p):
    # 金床と溜まる熾
    p.poly([(5, 20), (27, 20), (24, 24), (24, 28), (8, 28), (8, 24)], "mass")
    p.poly([(5, 20), (2, 17), (10, 17), (27, 17), (27, 20)], "blade")
    p.line([(5, 18), (26, 18)], "edge", 1)
    for x in (10, 16, 22):
        p.ellipse((x - 2, 8, x + 2, 14), "accent")
    p.line([(10, 14), (10, 17)], "fx2", 1)
    p.line([(16, 14), (16, 17)], "fx2", 1)
    p.line([(22, 14), (22, 17)], "fx2", 1)


def m_ash_scatter(p):
    # 舞い上がる灰
    m_cloud(p, 12, 11, 1.1)
    m_cloud(p, 22, 17, 0.9)
    for x, y in [(5, 20), (9, 25), (14, 22), (18, 28), (24, 25), (28, 29), (3, 28)]:
        p.dot((x, y), "fx2")
    p.line([(2, 17), (10, 18)], "fx", 1)
    p.dot((22, 8), "accent")


def m_smith_eye(p):
    # 目の中の小さな槌
    p.poly([(2, 16), (10, 9), (22, 9), (30, 16), (22, 23), (10, 23)], "cloud")
    p.line([(2, 16), (10, 9), (22, 9), (30, 16)], "mass", 1)
    p.ellipse((11, 10, 21, 22), "mass")
    p.line([(14, 21), (19, 12)], "hilt", 2)
    p.poly([(15, 11), (21, 12), (20, 15), (14, 14)], "accent")
    for (x0, y0, x1, y1) in [(16, 1, 16, 5), (16, 27, 16, 31), (0, 6, 3, 8), (32, 6, 29, 8)]:
        p.line([(x0, y0), (x1, y1)], "fx", 1)


def m_bone_trap(p):
    # 骨の歯が合わさる罠
    p.arc((2, 8, 30, 36), 180, 360, "blade", 3)
    for x in (6, 11, 16, 21, 26):
        p.poly([(x - 2, 20), (x, 12), (x + 2, 20)], "edge")
    p.poly([(2, 24), (30, 24), (30, 28), (2, 28)], "mass")
    p.dot((16, 26), "accent")
    p.line([(4, 6), (8, 10)], "fx2", 1)
    p.line([(28, 6), (24, 10)], "fx2", 1)


def m_still_string(p):
    # 静かに引かれた弦と、ひと息の輪
    p.arc((2, 2, 24, 30), 280, 80, "hilt", 3)
    p.line([(13, 4), (6, 16), (13, 28)], "fx2", 1)
    m_arrow(p, (6, 16), (30, 16), 4)
    p.ring((20, 4, 30, 14), "fx", 1)
    p.dot((25, 9), "accent")


def m_signal(p):
    # 空へ昇る標の光
    p.poly([(14, 29), (18, 29), (17, 6), (15, 6)], "cloud")
    p.poly([(11, 29), (21, 29), (23, 31), (9, 31)], "mass")
    p.line([(9, 8), (16, 2), (23, 8)], "fx", 2)
    p.line([(6, 12), (16, 5), (26, 12)], "fx2", 1)
    p.dot((16, 4), "accent")
    p.ring((9, 20, 23, 30), "fx2", 1)


def m_readiness_reward(p):
    # 三つ灯った灯り
    for x in (4, 13, 22):
        p.ellipse((x, 8, x + 7, 17), "accent")
        p.line([(x + 3, 17), (x + 3, 22)], "mass", 2)
        p.dot((x + 2, 11), "edge")
    p.line([(2, 24), (30, 24)], "fx", 1)
    p.poly([(4, 26), (28, 26), (26, 29), (6, 29)], "mass")


def m_camp_hand(p):
    # 焚き火と、かざす手
    p.line([(6, 27), (26, 21)], "hilt", 3)
    p.line([(6, 21), (26, 27)], "hilt", 3)
    p.poly([(10, 21), (13, 11), (16, 17), (19, 8), (22, 21)], "accent")
    p.poly([(13, 21), (16, 15), (19, 21)], "edge")
    p.arc((0, 2, 12, 18), 280, 80, "fx2", 1)
    p.arc((20, 2, 32, 18), 100, 260, "fx2", 1)


def m_appraiser(p):
    # 虫眼鏡と光る欠片
    p.ring((3, 3, 21, 21), "blade", 3)
    p.line([(19, 19), (28, 28)], "hilt", 3)
    p.poly([(8, 12), (11, 8), (14, 12), (11, 16)], "accent")
    p.line([(8, 6), (12, 4)], "fx", 1)
    p.dot((27, 8), "fx")
    p.dot((28, 4), "fx2")


def m_fleet_foot(p):
    # 駆ける足跡と風の線
    p.poly([(14, 10), (20, 10), (21, 17), (19, 21), (15, 21), (13, 16)], "hilt")
    p.ellipse((14, 22, 20, 28), "hilt")
    p.poly([(4, 18), (9, 18), (10, 23), (8, 26), (5, 26), (3, 22)], "mass")
    p.ellipse((4, 27, 9, 31), "mass")
    for y, x0 in [(6, 22), (12, 24), (18, 24)]:
        p.line([(x0, y), (x0 + 7, y)], "fx2", 1)
    p.dot((24, 24), "accent")


def m_journal(p):
    # 手帖と栞
    p.poly([(5, 4), (24, 4), (24, 28), (5, 28)], "hilt")
    p.poly([(8, 4), (26, 4), (26, 28), (8, 28)], "cloud")
    p.line([(11, 10), (22, 10)], "fx2", 1)
    p.line([(11, 14), (22, 14)], "fx2", 1)
    p.line([(11, 18), (19, 18)], "fx2", 1)
    p.poly([(21, 4), (24, 4), (24, 14), (22, 12), (21, 14)], "accent")
    p.line([(5, 4), (5, 28)], "mass", 2)


# 木の紋章 (旅人・岩守り・風読み・潮歌い・灰の鍛え人の 5 つ。骨の射手は弓使いの紋章をそのまま)
def m_rock_emblem(p):
    p.poly([(3, 27), (8, 14), (13, 7), (19, 12), (25, 5), (30, 27)], "mass")
    p.line([(13, 7), (14, 16), (11, 24)], "fx2", 1)
    p.line([(25, 5), (22, 14), (24, 22)], "fx2", 1)
    p.line([(2, 28), (30, 28)], "fx", 1)
    p.dot((16, 19), "accent")


def m_wind_emblem(p):
    p.arc((2, 4, 22, 18), 190, 20, "fx", 2)
    p.arc((6, 12, 28, 26), 200, 30, "fx", 2)
    p.arc((0, 18, 16, 30), 180, 0, "fx2", 1)
    p.poly([(23, 3), (30, 6), (26, 11)], "fx")
    p.dot((22, 8), "accent")


def m_tide_emblem(p):
    for y in (10, 17, 24):
        p.arc((1, y - 4, 12, y + 4), 180, 360, "fx", 2)
        p.arc((11, y - 4, 22, y + 4), 0, 180, "fx", 2)
        p.arc((21, y - 4, 31, y + 4), 180, 360, "fx", 2)
    p.dot((16, 5), "accent")


def m_ash_emblem(p):
    p.poly([(4, 20), (28, 20), (25, 24), (25, 29), (7, 29), (7, 24)], "mass")
    p.poly([(4, 20), (0, 16), (10, 16), (28, 16), (28, 20)], "blade")
    p.poly([(11, 14), (14, 5), (17, 10), (20, 3), (22, 14)], "accent")
    p.poly([(14, 14), (17, 9), (19, 14)], "edge")


def m_traveler_emblem(p):
    # 旅人: 方位の環と針
    p.ring((3, 3, 29, 29), "fx", 2)
    p.poly([(16, 4), (19, 16), (16, 28), (13, 16)], "cloud")
    p.poly([(16, 6), (18, 16), (14, 16)], "accent")
    p.dot((16, 16), "mass")
    p.line([(2, 16), (6, 16)], "fx2", 1)
    p.line([(26, 16), (30, 16)], "fx2", 1)


# 技 36 + 木 8 + 錠 + 封じ。技の並びは Skill の宣言の順 (岩守り・風読み・潮歌い・灰の鍛え人・骨の射手・旅人)
SKILL_MOTIFS = [
    # 岩守り
    base.m_titan_cleave, base.m_riposte, m_rock_call, base.m_stone_stance, base.m_whale_sweep, m_stone_oath,
    # 風読み
    base.m_lunge, m_nimble, base.m_backstep, m_foresight, m_wind_rend, m_lone_wind,
    # 潮歌い
    m_ward_song, m_calm_heart, m_ebb_tide, m_ripple, m_cycle_song, m_chorus_voice,
    # 灰の鍛え人
    m_temper, m_ember_heart, m_ember_hammer, m_forge_memory, m_ash_scatter, m_smith_eye,
    # 骨の射手
    base.m_cloud_pierce, base.m_far_shot, m_bone_trap, base.m_hawk_eye, base.m_sky_rain, m_still_string,
    # 旅人
    m_signal, m_readiness_reward, m_camp_hand, m_appraiser, m_fleet_foot, m_journal,
]
TREE_MOTIFS = [m_rock_emblem, m_wind_emblem, m_tide_emblem, m_ash_emblem, base.m_bow_emblem, m_traveler_emblem,
               base.m_star_emblem, base.m_abyss_emblem]
TREE_OF_SKILL = [i // 6 for i in range(36)]


def icon(motif, tree):
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
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
    pen = Pen()
    motif(pen)
    draw_ops(glyph, pen.ops, colors)
    glyph = outline_sprite(glyph)
    if tree is not None:
        a = np.array(glyph)
        edge = (a[..., :3] == np.array(OUTLINE[:3])).all(axis=2) & (a[..., 3] > 0)
        a[edge] = (24, 18, 20, 230)
        glyph = Image.fromarray(a, "RGBA")
    img.alpha_composite(glyph)
    return img


def icon_sheet():
    sheet = Image.new("RGBA", (256, 192), (0, 0, 0, 0))
    for i, motif in enumerate(SKILL_MOTIFS):
        sheet.paste(icon(motif, TREE_OF_SKILL[i]), ((i % 8) * 32, (i // 8) * 32))
    for t, motif in enumerate(TREE_MOTIFS):
        i = 36 + t
        sheet.paste(icon(motif, t), ((i % 8) * 32, (i // 8) * 32))
    sheet.paste(icon(base.m_lock, None), ((44 % 8) * 32, (44 // 8) * 32))
    sheet.paste(icon(base.m_sealed, 7), ((45 % 8) * 32, (45 // 8) * 32))
    return sheet


# ================================================================ 画面 (builds_panel.png)
W, H = 380, 184


def S(v):
    return v * 2


def panel():
    w, h = S(W), S(H)
    img = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    img.paste(stone(w, h), (0, 0))
    d = ImageDraw.Draw(img)
    bone_frame(d, w, h)
    ty = S(19)
    d.line([S(16), ty, w - S(16), ty], fill=TEAL_DIM)
    d.line([S(40), ty + 1, w - S(40), ty + 1], fill=(30, 60, 70, 255))
    gem(d, w // 2, ty, 4)
    # 木のつまみの列
    recess(d, S(6), S(22), S(32), S(152), fill=(19, 22, 29, 255))
    # 木の場
    recess(d, S(34), S(22), S(172), S(150), fill=(22, 25, 33, 255))
    for i in range(5):
        y = S(48) + i * S(24)
        d.arc([S(40), y - S(10), S(166), y + S(14)], 200, 340, fill=(30, 34, 44, 255))
    # 説明の欄
    recess(d, S(174), S(22), S(374), S(150), fill=(19, 22, 29, 255))
    sy = S(152)
    d.line([S(10), sy, w - S(10), sy], fill=TEAL_DIM)
    d.line([S(10), sy + 1, w - S(10), sy + 1], fill=OUTLINE)
    # 枠の列の下地 (5 枠) と型・振り直しの下地
    recess(d, S(82), S(154), S(208), S(181), fill=(19, 22, 29, 255))
    recess(d, S(210), S(154), S(374), S(181), fill=(19, 22, 29, 255))
    return img


def keystone_frame(state):
    """要の印の枠 44x44: 四隅を切った菱形寄りの縁。0 届かない、1 覚えられる、2 覚えた、3 極めた"""
    f = node_frame(state)
    d = ImageDraw.Draw(f)
    rim = [(64, 68, 78, 255), BONE_MID, TEAL, GOLD][state]
    for (x0, y0, x1, y1) in [(0, 0, 5, 5), (38, 0, 43, 5), (0, 38, 5, 43), (38, 38, 43, 43)]:
        d.rectangle([x0, y0, x1, y1], fill=(0, 0, 0, 0))
    for cx, cy in [(5, 5), (38, 5), (5, 38), (38, 38)]:
        d.point((cx, cy), fill=rim)
    # 上と下に小さな菱形
    for cx, cy in [(22, 1), (22, 42)]:
        d.polygon([(cx, cy - 2), (cx + 3, cy), (cx, cy + 2), (cx - 3, cy)], fill=rim, outline=OUTLINE)
    return f


def builds_texture():
    img = panel()
    for s in range(4):
        img.paste(keystone_frame(s), (s * 48, 376))
    return img


def main():
    GUI.mkdir(parents=True, exist_ok=True)
    icon_sheet().save(GUI / "icons.png")
    builds_texture().save(GUI / "builds_panel.png")
    print("wrote", GUI / "icons.png", GUI / "builds_panel.png")


if __name__ == "__main__":
    main()
