"""サイト用のドット絵を作る (リポジトリ直下で `python tools/site/gen_art.py`)。

- site/img/hero_night.png, hero_day.png   トップの帯 (論理 240x96 を 4 倍にして書き出す)
- site/img/scene_*.png                    巨神の小さな場面 (64x40)
- site/img/divider_night.png, divider_day.png  雲の区切り (タイル)
- site/img/skill/NN.png                   技のアイコン (gui/skill/icons.png から 32px を切り出し)
- site/img/item/*.png, boss/*.png         使う品・ボスの絵を assets から写す
- site/img/arch_*.png, plate_*.png        README の板 (docs/readme) から写す・切り出す

どれもゲームの画面写真ではなく、手作りのドット絵 (PIL で描く)。メモリは数 MB。
"""
import math
import os
import random
import shutil
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "site", "img")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "skyabyss", "textures")


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def ensure(p):
    os.makedirs(p, exist_ok=True)


def save(im, name):
    ensure(os.path.dirname(os.path.join(OUT, name)))
    im.save(os.path.join(OUT, name), optimize=True)


# ---------------------------------------------------------------- helpers
def bands(img, colors, y0, y1):
    """縦に帯を重ねる。帯の境は市松でなじませる。"""
    w = img.width
    n = len(colors)
    px = img.load()
    for y in range(y0, y1):
        t = (y - y0) / max(1, (y1 - y0 - 1)) * (n - 1)
        i = int(t)
        f = t - i
        for x in range(w):
            c = colors[i]
            if i + 1 < n:
                if f >= 0.8:
                    c = colors[i + 1]
                elif f > 0.2 and ((x + y) % 2 == 0):
                    c = colors[i + 1]
            px[x, y] = c


def blob(d, cx, cy, rx, ry, col):
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=col)


def cloud(d, cx, cy, w, col, shade=None):
    """平らな底のもこもこ雲。"""
    h = max(3, w // 5)
    blob(d, cx, cy, w // 2, h // 2, col)
    blob(d, cx - w // 4, cy + 1, w // 4, h // 2 + 1, col)
    blob(d, cx + w // 4, cy + 1, w // 3, h // 2, col)
    d.rectangle([cx - w // 2, cy + 1, cx + w // 2, cy + h // 2 + 1], fill=col)
    if shade:
        d.line([cx - w // 2 + 1, cy + h // 2 + 1, cx + w // 2 - 1, cy + h // 2 + 1], fill=shade)


def poly(d, pts, ox, oy, s, col):
    d.polygon([(ox + round(px * s), oy + round(py * s)) for px, py in pts], fill=col)


# ---------------------------------------------------------------- whale
WHALE_BODY = [(14, 20), (24, 17), (40, 12), (58, 8), (76, 9), (90, 15), (99, 21), (98, 27), (90, 33), (74, 36),
              (56, 35), (40, 31), (26, 25), (14, 23)]
WHALE_FLUKE = [(18, 19), (6, 4), (10, 16), (3, 29), (19, 24)]
WHALE_FIN = [(56, 31), (50, 40), (62, 38), (72, 33)]
WHALE_BELLY = [(40, 30), (56, 34), (74, 35), (90, 32), (98, 26), (80, 29), (60, 30)]


def whale(im, ox, oy, s, body, rim, belly, glow=None, island=True, chains=True):
    d = ImageDraw.Draw(im)
    poly(d, WHALE_FLUKE, ox, oy, s, body)
    poly(d, WHALE_BODY, ox, oy, s, body)
    poly(d, WHALE_FIN, ox, oy, s, body)
    poly(d, WHALE_BELLY, ox, oy, s, belly)
    pts = [(24, 17), (40, 12), (58, 8), (76, 9), (90, 15)]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        d.line([(ox + round(x0 * s), oy + round(y0 * s)), (ox + round(x1 * s), oy + round(y1 * s))], fill=rim)
    if island:
        bx = ox + round(46 * s)
        by = oy + round(9 * s)
        d.rectangle([bx, by - 5, bx + 3, by], fill=body)
        d.rectangle([bx - 1, by - 7, bx + 4, by - 5], fill=rim)
        d.rectangle([bx + 6, by - 2, bx + 12, by], fill=body)
        d.rectangle([bx - 8, by - 1, bx - 4, by], fill=body)
        if glow:
            d.point((bx + 1, by - 3), fill=glow)
            d.point((bx + 8, by - 1), fill=glow)
    d.point((ox + round(88 * s), oy + round(23 * s)), fill=rim)
    d.line([(ox + round(80 * s), oy + round(27 * s)), (ox + round(96 * s), oy + round(25 * s))], fill=rim)
    if chains:
        cx = ox + round(54 * s)
        cy = oy + round(36 * s)
        for i in range(0, 14, 2):
            d.point((cx, cy + i), fill=rim)
            d.point((cx + (i // 4) % 2, cy + i + 1), fill=body)


# ---------------------------------------------------------------- hero
def pit_half(y):
    return 46 - (y - 66) * 0.95


def hero(night):
    W, H = 240, 96
    im = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    rnd = random.Random(7 if night else 11)
    if night:
        sky = [hexc(c) for c in ("#060a1b", "#09112d", "#0d1a40", "#132459", "#1b316f", "#26428a", "#34589f", "#486fb3")]
    else:
        sky = [hexc(c) for c in ("#4f9ae6", "#68aeee", "#84c0f3", "#a0d0f7", "#b8def9", "#cde9fb", "#e0f2fd", "#f1f9ff")]
    bands(im, sky, 0, 70)
    d = ImageDraw.Draw(im)
    if night:
        for _ in range(70):
            x, y = rnd.randrange(W), rnd.randrange(0, 46)
            d.point((x, y), fill=hexc(rnd.choice(["#9fb4e8", "#c9d6f6", "#6e86c8", "#ffffff"])))
        for _ in range(7):
            x, y = rnd.randrange(8, W - 8), rnd.randrange(4, 34)
            d.point((x, y), fill=hexc("#e9f0ff"))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                d.point((x + dx, y + dy), fill=hexc("#7f97d8"))
        clean = im.copy()
        blob(d, 36, 20, 8, 8, hexc("#f4efd2"))
        m = Image.new("L", (W, H), 0)
        ImageDraw.Draw(m).ellipse([32, 10, 48, 26], fill=255)
        im.paste(clean, (0, 0), m)
        d = ImageDraw.Draw(im)
    else:
        blob(d, 36, 22, 8, 8, hexc("#fff6c8"))
        blob(d, 36, 22, 6, 6, hexc("#ffffff"))
    far = hexc("#1a2b62") if night else hexc("#d6ebfb")
    farsh = hexc("#142250") if night else hexc("#bcd9f2")
    for cx, cy, w in ((20, 58, 44), (72, 52, 30), (150, 56, 50), (214, 50, 36)):
        cloud(d, cx, cy, w, far, farsh)
    if night:
        whale(im, 118, 14, 1.0, hexc("#0a1330"), hexc("#4c70b8"), hexc("#15234f"), glow=hexc("#ffd36b"))
    else:
        whale(im, 118, 14, 1.0, hexc("#3a577f"), hexc("#a9c6e8"), hexc("#4f6f9a"), glow=hexc("#fff2a8"))
    near = [hexc(c) for c in (("#1d2f68", "#243876", "#2d4488", "#36519a") if night else ("#ffffff", "#eaf5fe", "#d4e8f9", "#bfdaf3"))]
    abyss = [hexc(c) for c in ("#120a26", "#0d0719", "#080412", "#050209")]
    for y in range(64, H):
        for x in range(W):
            top = 66 + round(2.0 * math.sin(x / 9.0) + 1.5 * math.sin(x / 4.3))
            if y < top:
                continue
            if y >= 68 and abs(x - 122) < pit_half(y):
                im.putpixel((x, y), abyss[min(3, (y - 68) // 8)])
            else:
                im.putpixel((x, y), near[min(3, (y - top) // 3)])
    for y in range(68, H):
        half = pit_half(y)
        for x in (round(122 - half), round(122 + half) - 1):
            if 0 <= x < W:
                im.putpixel((x, y), hexc("#2a1840"))
    gx, gy = 122, H - 6
    for r, col in ((9, "#3a0d16"), (6, "#6a1a1a"), (4, "#c0431b"), (2, "#ff9a3c")):
        d.ellipse([gx - r, gy - r // 2, gx + r, gy + r // 2], fill=hexc(col))
    d.point((gx, gy), fill=hexc("#fff0b0"))
    for _ in range(14):
        x = rnd.randrange(gx - 14, gx + 14)
        y = rnd.randrange(72, H - 8)
        d.point((x, y), fill=hexc(rnd.choice(["#7a2a1a", "#a8431f", "#3b2a5c"])))
    chain = hexc("#27315a") if night else hexc("#5b78a6")
    for y in range(50, 92, 2):
        x = 172 + (y // 6) % 2
        if y < 64 or (y >= 68 and abs(x - 122) < pit_half(y) - 6):
            d.point((x, y), fill=chain)
    return im.resize((W * 4, H * 4), Image.NEAREST)


# ---------------------------------------------------------------- scenes (64x40)
def scene_whale():
    im = Image.new("RGBA", (64, 40), (0, 0, 0, 255))
    bands(im, [hexc(c) for c in ("#3f86d6", "#66a7e8", "#9cc9f3", "#d5ecfb")], 0, 40)
    d = ImageDraw.Draw(im)
    cloud(d, 14, 28, 26, hexc("#ffffff"), hexc("#cfe3f5"))
    cloud(d, 50, 32, 30, hexc("#f2f8ff"), hexc("#bcd6ee"))
    d.rectangle([0, 34, 63, 39], fill=hexc("#e7f2fc"))
    whale(im, 10, 6, 0.46, hexc("#3a5a86"), hexc("#b8d0ee"), hexc("#587aa6"), glow=hexc("#fff2a8"), chains=False)
    return im


def scene_shell():
    im = Image.new("RGBA", (64, 40), (0, 0, 0, 255))
    bands(im, [hexc(c) for c in ("#f0a58f", "#f6c3a0", "#fbe0b8", "#bfe4ee")], 0, 24)
    d = ImageDraw.Draw(im)
    for y in range(24, 40):
        col = hexc("#2f93a8") if y < 30 else hexc("#1e6f8c") if y < 35 else hexc("#154d6b")
        d.line([0, y, 63, y], fill=col)
    for x in range(0, 64, 6):
        d.point((x + (x // 6) % 3, 26 + (x // 6) % 4), fill=hexc("#9fe0e6"))
    sand = hexc("#d8c08a")
    d.polygon([(10, 26), (14, 18), (26, 14), (38, 14), (50, 18), (54, 26)], fill=hexc("#6d5a46"))
    d.polygon([(13, 24), (17, 19), (27, 16), (37, 16), (47, 19), (51, 24)], fill=sand)
    d.rectangle([22, 12, 42, 16], fill=hexc("#e6d4a2"))
    d.rectangle([28, 8, 36, 12], fill=hexc("#efe0b4"))
    d.rectangle([31, 3, 33, 8], fill=hexc("#cfd9e3"))
    d.point((32, 2), fill=hexc("#8fe4ff"))
    d.polygon([(52, 22), (58, 22), (60, 25), (58, 27), (52, 26)], fill=hexc("#5c7a52"))
    d.point((58, 23), fill=hexc("#101820"))
    for x, y, c in ((18, 21, "#e36f8e"), (24, 18, "#7fd0e6"), (40, 18, "#f5c14a"), (46, 21, "#9a7fe0"), (30, 14, "#e36f8e"), (36, 14, "#7fd0e6")):
        d.point((x, y), fill=hexc(c))
        d.point((x, y - 1), fill=hexc(c))
    d.rectangle([14, 26, 18, 30], fill=hexc("#4a5d46"))
    d.rectangle([44, 26, 48, 30], fill=hexc("#4a5d46"))
    d.line([0, 26, 63, 26], fill=hexc("#7fd0e6"))
    return im


def scene_wing():
    im = Image.new("RGBA", (64, 40), (0, 0, 0, 255))
    bands(im, [hexc(c) for c in ("#10162e", "#1a2342", "#27345a", "#3a4a72")], 0, 40)
    d = ImageDraw.Draw(im)
    for cx, cy, w, c in ((10, 8, 26, "#2a3558"), (52, 6, 30, "#303d66"), (34, 33, 44, "#202a4a")):
        cloud(d, cx, cy, w, hexc(c))
    for pts in ([(8, 2), (6, 8), (10, 8), (7, 15)], [(56, 4), (53, 11), (57, 11), (54, 19)]):
        d.line(pts, fill=hexc("#fff6c8"))
    d.polygon([(32, 14), (44, 20), (62, 16), (56, 24), (46, 25), (36, 24), (28, 25), (18, 25), (8, 24), (2, 16), (20, 20)], fill=hexc("#5f6e96"))
    for x in range(6, 60, 6):
        d.line([x, 24, x - 1, 28], fill=hexc("#5f6e96"))
    d.polygon([(26, 17), (32, 12), (38, 17), (36, 26), (28, 26)], fill=hexc("#7c8bb4"))
    d.polygon([(31, 12), (34, 10), (36, 12), (33, 14)], fill=hexc("#e0b25a"))
    d.point((32, 15), fill=hexc("#ffffff"))
    d.line([8, 24, 56, 24], fill=hexc("#f0c24b"))
    return im


def scene_ember():
    im = Image.new("RGBA", (64, 40), (0, 0, 0, 255))
    bands(im, [hexc(c) for c in ("#1a0d16", "#2c1218", "#4a1a1a", "#7a2a1a")], 0, 40)
    d = ImageDraw.Draw(im)
    rnd = random.Random(3)
    for _ in range(18):
        d.point((rnd.randrange(64), rnd.randrange(0, 20)), fill=hexc(rnd.choice(["#7a4a3a", "#a35a30", "#5a3a3a"])))
    for y, x in ((3, 30), (5, 31), (7, 30), (9, 31)):
        d.rectangle([x - 2, y - 1, x + 2, y], fill=hexc("#4a3a40"))
    d.polygon([(0, 40), (0, 30), (10, 26), (22, 18), (28, 11), (36, 11), (42, 18), (54, 26), (63, 30), (63, 40)], fill=hexc("#241418"))
    d.polygon([(22, 18), (28, 11), (36, 11), (42, 18)], fill=hexc("#3a2024"))
    d.rectangle([29, 10, 35, 12], fill=hexc("#ff7a2a"))
    d.point((32, 9), fill=hexc("#ffd36b"))
    for pts in ([(30, 12), (28, 18), (24, 24)], [(34, 12), (36, 19), (42, 25)], [(32, 13), (32, 22), (31, 30)], [(10, 32), (16, 30)], [(50, 32), (56, 30)]):
        d.line(pts, fill=hexc("#e8561c"))
    for pts in ([(30, 13), (29, 17)], [(35, 13), (36, 17)]):
        d.line(pts, fill=hexc("#ffb040"))
    d.rectangle([0, 36, 63, 39], fill=hexc("#150c10"))
    for x in range(0, 64, 5):
        d.point((x, 35), fill=hexc("#5a3a3a"))
    return im


def divider(night):
    im = Image.new("RGBA", (160, 14), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = hexc("#2a3f80") if night else hexc("#9cc4ea")
    c2 = hexc("#f0c24b") if night else hexc("#b07a10")
    for cx, w in ((20, 24), (62, 18), (104, 26), (142, 16)):
        cloud(d, cx, 8, w, c)
    for x in (40, 82, 124):
        d.point((x, 3), fill=c2)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            d.point((x + dx, 3 + dy), fill=c2)
    return im.resize((640, 56), Image.NEAREST)


# ---------------------------------------------------------------- abyss (五章)
def _shaft_edges(y, top, bot, x0t, x1t, x0b, x1b):
    """縦穴の壁の位置 (上が狭く、下が広い)。y の所の左右の x を返す。"""
    t = (y - top) / max(1, bot - top)
    return x0t + (x0b - x0t) * t, x1t + (x1b - x1t) * t


def _ledges(d, top, bot, step, x0t, x1t, x0b, x1b, col, shade):
    """螺旋の段。左右交互に、片側だけ張り出して見せる。"""
    for i, y in enumerate(range(top + 6, bot, step)):
        l, r = _shaft_edges(y, top, bot, x0t, x1t, x0b, x1b)
        if i % 2 == 0:
            d.line([l + 1, y, r * 0.55 + l * 0.45, y], fill=col)
        else:
            d.line([l * 0.45 + r * 0.55, y, r - 1, y], fill=col)
        d.line([l + 1, y + 1, r - 1, y + 1], fill=shade)


def arch_abyss():
    """五章の札: 縦穴を見下ろす、灯りのあかり。170x168 (論理 85x84 を 2 倍)。"""
    bg = hexc("#0b1230")
    L = Image.new("RGBA", (85, 84), hexc("#120c22"))
    bands(L, [hexc(c) for c in ("#0f0a1c", "#1a1230", "#2a1d3e", "#120c22")], 0, 84)
    px = L.load()
    for y in range(42):
        for x in range(85):
            if (x - 42) ** 2 + (y - 42) ** 2 > 41 * 41:
                px[x, y] = bg  # アーチの外
    d = ImageDraw.Draw(L)
    # 縦穴の壁 (上が狭い。底は闇)
    d.polygon([(33, 10), (51, 10), (66, 74), (18, 74)], fill=hexc("#2b2140"))
    d.polygon([(33, 10), (37, 10), (28, 74), (18, 74)], fill=hexc("#463a5e"))
    d.polygon([(51, 10), (47, 10), (56, 74), (66, 74)], fill=hexc("#1a1330"))
    d.rectangle([18, 74, 66, 84], fill=hexc("#07050d"))
    _ledges(d, 10, 74, 9, 33, 51, 18, 66, hexc("#6d5b86"), hexc("#241b38"))
    # 中央の鎖
    for y in range(0, 30, 3):
        d.point((42, y), fill=hexc("#8a8aa0"))
    # 灯り (壁の段のそば。下の光だまり)
    for x, y in ((27, 26), (57, 44), (23, 58), (60, 68)):
        for dx, dy in ((2, 1), (-2, 1), (0, 2), (0, -2)):
            d.point((x + dx, y + dy), fill=hexc("#7a4a1a"))
        d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=hexc("#ffb040"))
        d.point((x, y), fill=hexc("#fff2a8"))
        d.rectangle([x - 4, y + 4, x + 4, y + 5], fill=hexc("#4a2a12"))
    # 底の奥に、かすかな光
    d.point((42, 70), fill=hexc("#5a3a7a"))
    d.point((41, 72), fill=hexc("#5a3a7a"))
    # 骨の白い粒
    for x, y in ((30, 48), (50, 56), (36, 64)):
        d.point((x, y), fill=hexc("#d8d0c0"))
    # アーチの縁
    d.arc([1, 1, 83, 83], 180, 360, fill=hexc("#8a7aa8"), width=2)
    d.line([1, 42, 1, 83], fill=hexc("#8a7aa8"))
    d.line([83, 42, 83, 83], fill=hexc("#8a7aa8"))
    return L.resize((170, 168), Image.NEAREST)


def scene_abyss():
    """五章の小さな場面 (64x40): 縦穴と灯りひとつ。"""
    im = Image.new("RGBA", (64, 40), (0, 0, 0, 255))
    bands(im, [hexc(c) for c in ("#0d0a18", "#1a1230", "#2a1d3e", "#120c22")], 0, 40)
    d = ImageDraw.Draw(im)
    d.polygon([(26, 0), (38, 0), (56, 36), (8, 36)], fill=hexc("#2b2140"))
    d.polygon([(26, 0), (29, 0), (16, 36), (8, 36)], fill=hexc("#463a5e"))
    d.rectangle([0, 36, 63, 39], fill=hexc("#07050d"))
    _ledges(d, 0, 36, 6, 26, 38, 8, 56, hexc("#6d5b86"), hexc("#241b38"))
    for y in range(0, 14, 2):
        d.point((32, y), fill=hexc("#8a8aa0"))
    d.ellipse([28, 16, 36, 24], fill=hexc("#5a3a18"))
    d.rectangle([30, 18, 34, 22], fill=hexc("#ffb040"))
    d.point((32, 20), fill=hexc("#fff2a8"))
    d.rectangle([22, 27, 42, 28], fill=hexc("#4a2a12"))
    return im


# ---------------------------------------------------------------- copy assets
ITEMS = """whalebone_greatsword captain_pike waterway_fang tide_shell_trident coral_shield tidecaller_claw coral_harpoon
thunder_wing_bow raiju_claw warden_glaive ember_war_axe smith_hammer thrall_poker
stone_whale_helmet stone_whale_chestplate stone_whale_leggings stone_whale_boots
tide_shell_helmet tide_shell_chestplate tide_shell_leggings tide_shell_boots
thunder_wing_helmet thunder_wing_chestplate thunder_wing_leggings thunder_wing_boots
ember_helmet ember_chestplate ember_leggings ember_boots
resonance_charm tide_chime_charm storm_charm kindling_charm moss_talisman stone_whale_plating moontide_pearl storm_anklet
storm_eye_feather ember_mantle ember_lantern heartbeat_ward
stone_whale_call tide_shell_call thunder_wing_call ember_call tide_watching_shell gale_feather warm_stone
sky_key sky_key_fragment stone_whale_core tide_shell_core thunder_wing_core ember_core
resonance_crystal resonance_shard_stone_whale resonance_shard_tide_shell resonance_shard_thunder_wing resonance_shard_ember
abyss_greatsword abyss_bell_staff abyss_storm_bow abyss_ash_scythe aspect_core skill_rune
shrine_sword shrine_hammer shrine_staff shrine_bow shrine_dagger shrine_bell_staff socket_shard_stone socket_shard_wind socket_shard_tide socket_shard_ash socket_shard_bone
lore_book lore_page wayfarer_notes weathercock tide_pearl cloud_jerky cloudgap_fruit ember_horn moss_salve rock_whetstone road_hardtack rock_shell_scrap rock_shell_thread stone_whale_shard tide_shell_shard thunder_wing_shard ember_shard bone_chip""".split()

BOSS = """stone_whale_heart_icon_p1 stone_whale_heart_icon_p2 stone_whale_heart_icon_p3
tide_heart_icon_p1 tide_heart_icon_p2 tide_heart_icon_p3 storm_heart_icon_p1 storm_heart_icon_p2 storm_heart_icon_p3
ember_heart_icon_p1 ember_heart_icon_p2 ember_heart_icon_p3 captain_icon tide_beckoner_icon raiju_icon ash_smith_icon""".split()


def copy_assets():
    ensure(os.path.join(OUT, "item"))
    ensure(os.path.join(OUT, "boss"))
    ensure(os.path.join(OUT, "skill"))
    missing = []
    for n in ITEMS:
        src = os.path.join(TEX, "item", n + ".png")
        if os.path.exists(src):
            shutil.copy(src, os.path.join(OUT, "item", n + ".png"))
        else:
            missing.append(n)
    for n in BOSS:
        src = os.path.join(TEX, "gui", "boss", n + ".png")
        if os.path.exists(src):
            shutil.copy(src, os.path.join(OUT, "boss", n + ".png"))
        else:
            missing.append(n)
    sheet = Image.open(os.path.join(TEX, "gui", "skill", "icons.png")).convert("RGBA")
    for i in range(36):
        x, y = (i % 8) * 32, (i // 8) * 32
        sheet.crop((x, y, x + 32, y + 32)).save(os.path.join(OUT, "skill", "%02d.png" % i), optimize=True)
    # 職の新しい技 (星読みから) は icons2.png の 25 番から。番号は Skill.iconIndex() と同じ 125〜 (skilldata.load の icon)
    sheet2 = Image.open(os.path.join(TEX, "gui", "skill", "icons2.png")).convert("RGBA")
    for i in range(25, 37):
        x, y = (i % 8) * 32, (i // 8) * 32
        sheet2.crop((x, y, x + 32, y + 32)).save(os.path.join(OUT, "skill", "%02d.png" % (100 + i)), optimize=True)
    rd = os.path.join(ROOT, "docs", "readme")
    for n in ("plate_title", "plate_chapters", "plate_end", "plate_tagline", "plate_keys"):
        shutil.copy(os.path.join(rd, n + ".png"), os.path.join(OUT, n + ".png"))
    ch = Image.open(os.path.join(rd, "plate_chapters.png")).convert("RGBA")
    for i, name in enumerate(("whale", "shell", "wing", "ember")):
        x0 = (40, 238, 437, 635)[i]
        ch.crop((x0 - 2, 62, x0 + 168, 230)).save(os.path.join(OUT, "arch_%s.png" % name), optimize=True)
    shutil.copy(os.path.join(TEX, "item", "sky_key.png"), os.path.join(OUT, "favicon.png"))
    if missing:
        print("missing:", missing)


def main():
    ensure(OUT)
    save(hero(True), "hero_night.png")
    save(hero(False), "hero_day.png")
    for n, f in (("whale", scene_whale), ("shell", scene_shell), ("wing", scene_wing), ("ember", scene_ember)):
        save(f(), "scene_%s.png" % n)
    save(divider(True), "divider_night.png")
    save(divider(False), "divider_day.png")
    save(arch_abyss(), "arch_abyss.png")
    save(scene_abyss(), "scene_abyss.png")
    copy_assets()
    print("done", OUT)


if __name__ == "__main__":
    main()
