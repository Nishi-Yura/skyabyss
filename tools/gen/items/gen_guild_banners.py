# -*- coding: utf-8 -*-
"""ギルドの旗の模様の見本と、この世界の紋 (旗の模様の絵) のラフ。
  1. 見本 docs/review/guild_banners/emblems.png : GuildEmblems.java の模様 (バニラの模様の重ね) をすべて、旗の形で並べる (2026-11-01 に足したものに「新」)
  2. 紋のラフ docs/review/guild_banners/motifs.png : この世界の紋 12 (鯨・鐘・巨神の目・月…) を三つの色の組で
     紋の絵そのもの (64x64 の旗の模様。表の面は (1,1) から 20x40) は docs/review/guild_banners/motifs/<id>.png。決まったら assets へ移す
バニラの旗の模様の絵は Minecraft の資源の jar から読む (引数で展開した場所を渡す):
    python tools/gen/items/gen_guild_banners.py <展開した場所>/assets/minecraft/textures/entity/banner
"""
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent.parent.parent
JAVA = ROOT / "src/main/java/jp/revati/skyabyss/facility/guild/GuildEmblems.java"
LANG = ROOT / "src/main/resources/assets/skyabyss_fac/lang/ja_jp.json"
OUT = ROOT / "docs/review/guild_banners"
FONT = ROOT / "tools/sources/fonts/DotGothic16-Regular.ttf"

DYE = {"white": 0xF9FFFE, "orange": 0xF9801D, "magenta": 0xC74EBD, "light_blue": 0x3AB3DA, "yellow": 0xFED83D, "lime": 0x80C71F,
       "pink": 0xF38BAA, "gray": 0x474F52, "light_gray": 0x9D9D97, "cyan": 0x169C9C, "purple": 0x8932B8, "blue": 0x3C44AA,
       "brown": 0x835432, "green": 0x5E7C16, "red": 0xB02E26, "black": 0x1D1D21}
# BannerPatterns の名前 → 絵の名前 (違うものだけ)
TEX = {"STRIPE_SMALL": "small_stripes", "CIRCLE_MIDDLE": "circle", "RHOMBUS_MIDDLE": "rhombus", "DIAGONAL_RIGHT": "diagonal_up_right",
       "DIAGONAL_LEFT_MIRROR": "diagonal_up_left", "DIAGONAL_RIGHT_MIRROR": "diagonal_right", "HALF_VERTICAL_MIRROR": "half_vertical_right",
       "HALF_HORIZONTAL_MIRROR": "half_horizontal_bottom"}
# 見本の色の組 (地, 模様)
PAIRS = [("blue", "yellow"), ("red", "white"), ("black", "orange"), ("cyan", "white"), ("purple", "yellow"), ("green", "light_gray"),
         ("white", "blue"), ("brown", "yellow"), ("gray", "light_blue"), ("black", "red")]
SCALE = 5
FACE = (1, 1, 21, 41)


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def face(img):
    return img.crop(FACE)


def tint(mask, color):
    # 模様の絵 (白と透明) を染料の色で
    r, g, b = rgb(DYE[color])
    m = mask.convert("RGBA")
    px = m.load()
    out = Image.new("RGBA", m.size)
    o = out.load()
    for y in range(m.size[1]):
        for x in range(m.size[0]):
            pr, pg, pb, pa = px[x, y]
            lum = (pr + pg + pb) / (3 * 255)
            o[x, y] = (int(r * lum), int(g * lum), int(b * lum), pa)
    return out


def banner(tex, layers, base, accent):
    cloth = tint(face(Image.open(tex / "base.png")), base)
    for pat, is_accent in layers:
        src = pat if isinstance(pat, Image.Image) else Image.open(tex / (TEX.get(pat, pat.lower()) + ".png"))
        cloth = Image.alpha_composite(cloth, tint(face(src), accent if is_accent else base))
    return cloth.resize((cloth.width * SCALE, cloth.height * SCALE), Image.NEAREST)


def emblems():
    src = JAVA.read_text(encoding="utf-8")
    out = []
    new = False
    for line in src.splitlines():
        if "2026-11-01" in line:
            new = True
        if "// 功績で開く" in line:
            new = False
        m = re.match(r'\s*e\("(\w+)", (null|"(\w+)"), (.*)\)[,;)]*\s*$', line)
        if not m:
            continue
        layers = [(p, k == "a") for k, p in re.findall(r"(a|b)\(BannerPatterns\.(\w+)\)", m.group(4))]
        out.append((m.group(1), m.group(3), layers, new or m.group(1) in ("flower_cross", "star_globe", "gale")))
    return out


def names():
    import json
    d = json.loads(LANG.read_text(encoding="utf-8"))
    return {k.split(".")[-1]: v for k, v in d.items() if k.startswith("gui.skyabyss.guild.pattern.")}


def sheet(tiles, cols, title, path):
    font = ImageFont.truetype(str(FONT), 16)
    small = ImageFont.truetype(str(FONT), 12)
    tw, th = 20 * SCALE + 24, 40 * SCALE + 44
    rows = math.ceil(len(tiles) / cols)
    img = Image.new("RGB", (cols * tw + 24, rows * th + 56), (24, 27, 34))
    d = ImageDraw.Draw(img)
    d.text((16, 14), title, font=font, fill=(230, 226, 214))
    for i, (pic, label, sub, mark) in enumerate(tiles):
        x = 12 + (i % cols) * tw + 12
        y = 48 + (i // cols) * th
        d.rectangle([x - 3, y - 3, x + pic.width + 2, y + pic.height + 2], fill=(14, 16, 21), outline=(110, 102, 84))
        img.paste(pic, (x, y), pic)
        lw = d.textlength(label, font=small)
        d.text((x + (pic.width - lw) / 2, y + pic.height + 6), label, font=small, fill=(230, 226, 214))
        if sub:
            sw = d.textlength(sub, font=small)
            d.text((x + (pic.width - sw) / 2, y + pic.height + 22), sub, font=small, fill=(127, 214, 208) if mark else (169, 174, 188))
    img.save(path)
    print("wrote", path)


# ---------------------------------------------------------------- この世界の紋 (旗の模様の絵)。8 倍で描いて 20x40 に縮め、はっきりした縁にする
SS = 8


def canvas():
    return Image.new("L", (20 * SS, 40 * SS), 0)


def finish(big):
    small = big.resize((20, 40), Image.BOX).point(lambda v: 255 if v >= 128 else 0)
    tex = Image.new("RGBA", (64, 64), (255, 255, 255, 0))
    face_img = Image.merge("RGBA", (Image.new("L", (20, 40), 255),) * 3 + (small,))
    tex.paste(face_img, (1, 1))
    return tex


def m_whale():
    c = canvas()
    d = ImageDraw.Draw(c)
    # 頭を下に潜る岩鯨: 太い体と丸い頭 (下)、上に横へ大きく張る尾びれ、横に胸びれ、背の岩の割れ目
    d.ellipse([22, 96, 138, 300], fill=255)
    d.polygon([(64, 110), (96, 110), (90, 70), (70, 70)], fill=255)
    d.polygon([(80, 82), (4, 30), (10, 20), (56, 40), (80, 58), (104, 40), (150, 20), (156, 30)], fill=255)
    d.polygon([(30, 210), (2, 262), (16, 268), (46, 232)], fill=255)
    d.polygon([(130, 210), (158, 262), (144, 268), (114, 232)], fill=255)
    d.line([(40, 150), (70, 176), (60, 214), (84, 240)], fill=0, width=8)
    d.line([(118, 132), (100, 168)], fill=0, width=8)
    return c


def m_bell():
    c = canvas()
    d = ImageDraw.Draw(c)
    d.rectangle([72, 40, 88, 66], fill=255)
    d.ellipse([60, 30, 100, 54], outline=255, width=8)
    d.polygon([(80, 60), (44, 90), (40, 200), (20, 236), (140, 236), (120, 200), (116, 90)], fill=255)
    d.ellipse([44, 62, 116, 120], fill=255)
    d.rectangle([20, 228, 140, 244], fill=255)
    d.ellipse([66, 252, 94, 280], fill=255)
    d.line([(50, 200), (110, 200)], fill=0, width=6)
    return c


def m_eye():
    c = canvas()
    d = ImageDraw.Draw(c)
    # 巨神の目: アーモンドの目と縦の瞳、上下に光の筋
    pts = []
    for i in range(41):
        t = i / 40
        x = 10 + 140 * t
        y = 160 - math.sin(math.pi * t) * 56
        pts.append((x, y))
    for i in range(41):
        t = 1 - i / 40
        x = 10 + 140 * t
        y = 160 + math.sin(math.pi * t) * 56
        pts.append((x, y))
    d.polygon(pts, fill=255)
    d.ellipse([46, 122, 114, 198], fill=0)
    d.ellipse([70, 122, 90, 198], fill=255)
    for a in (-60, -30, 0, 30, 60):
        r = math.radians(a)
        d.line([(80 + math.sin(r) * 70, 160 - math.cos(r) * 70 - 8), (80 + math.sin(r) * 110, 160 - math.cos(r) * 110 - 8)], fill=255, width=8)
        d.line([(80 + math.sin(r) * 70, 160 + math.cos(r) * 70 + 8), (80 + math.sin(r) * 110, 160 + math.cos(r) * 110 + 8)], fill=255, width=8)
    return c


def m_moon():
    c = canvas()
    d = ImageDraw.Draw(c)
    d.ellipse([20, 90, 140, 210], fill=255)
    d.ellipse([52, 74, 168, 190], fill=0)
    for x, y, r in ((116, 220, 9), (130, 70, 7), (40, 50, 6), (60, 268, 6)):
        d.polygon([(x, y - r * 2), (x + r * 0.6, y - r * 0.6), (x + r * 2, y), (x + r * 0.6, y + r * 0.6), (x, y + r * 2), (x - r * 0.6, y + r * 0.6),
                   (x - r * 2, y), (x - r * 0.6, y - r * 0.6)], fill=255)
    return c


def m_wing():
    c = canvas()
    d = ImageDraw.Draw(c)
    # 雷翼の翼 (付け根は下。上へ広がり、外の縁に三枚の羽の先)
    d.polygon([(60, 300), (40, 220), (30, 120), (52, 40), (96, 16), (150, 14), (126, 60), (152, 74), (118, 120), (146, 140), (104, 180), (124, 204),
               (84, 236), (92, 300)], fill=255)
    d.line([(64, 286), (70, 200), (90, 110), (126, 40)], fill=0, width=7)
    d.line([(76, 196), (118, 168)], fill=0, width=6)
    d.line([(88, 132), (128, 104)], fill=0, width=6)
    return c


def m_shell():
    c = canvas()
    d = ImageDraw.Draw(c)
    # 潮殻: 扇の貝 (太い筋が四本、縁は波)、下に蝶番の耳
    d.pieslice([8, 64, 152, 240], 180, 360, fill=255)
    d.polygon([(8, 152), (152, 152), (100, 244), (60, 244)], fill=255)
    for k in range(0, 7):
        a = math.pi + math.pi * (k + 0.5) / 7
        d.ellipse([80 + math.cos(a) * 72 - 12, 152 + math.sin(a) * 88 - 12, 80 + math.cos(a) * 72 + 12, 152 + math.sin(a) * 88 + 12], fill=255)
    for k in range(1, 5):
        a = math.pi + math.pi * k / 5
        d.line([(80, 236), (80 + math.cos(a) * 82, 152 + math.sin(a) * 96)], fill=0, width=9)
    d.polygon([(44, 236), (116, 236), (128, 266), (32, 266)], fill=255)
    return c


def m_ember():
    c = canvas()
    d = ImageDraw.Draw(c)
    # 余燼: 太い炎 (先は右へなびく)、内に小さな炎の抜き、下に熾
    d.ellipse([22, 120, 138, 250], fill=255)
    d.polygon([(26, 170), (60, 60), (76, 100), (104, 20), (118, 96), (134, 170)], fill=255)
    d.ellipse([56, 168, 104, 236], fill=0)
    d.polygon([(66, 186), (84, 130), (96, 190)], fill=0)
    d.rectangle([30, 262, 130, 280], fill=255)
    d.line([(56, 262), (64, 280)], fill=0, width=6)
    d.line([(100, 262), (94, 280)], fill=0, width=6)
    return c


def m_bolt():
    c = canvas()
    d = ImageDraw.Draw(c)
    d.polygon([(100, 20), (40, 170), (78, 170), (52, 300), (126, 130), (88, 130), (124, 20)], fill=255)
    return c


def m_tower():
    c = canvas()
    d = ImageDraw.Draw(c)
    # あいだの塔: 細く高い塔、上に狭間、窓は抜き、足もとは雲
    d.polygon([(56, 70), (104, 70), (116, 290), (44, 290)], fill=255)
    d.rectangle([46, 50, 114, 76], fill=255)
    for x in (46, 74, 102):
        d.rectangle([x, 34, x + 12, 50], fill=255)
    d.rectangle([74, 110, 86, 140], fill=0)
    d.rectangle([74, 180, 86, 210], fill=0)
    for cx in (20, 60, 100, 140):
        d.ellipse([cx - 26, 270, cx + 26, 316], fill=255)
    return c


def m_compass():
    c = canvas()
    d = ImageDraw.Draw(c)
    cx, cy = 80, 160
    for a in range(0, 360, 45):
        r = math.radians(a)
        long = 130 if a % 180 == 0 else (70 if a % 90 == 0 else 52)
        w = 16 if a % 90 == 0 else 11
        tip = (cx + math.sin(r) * long, cy - math.cos(r) * long)
        l = (cx + math.sin(r + math.pi / 2) * w, cy - math.cos(r + math.pi / 2) * w)
        rr = (cx + math.sin(r - math.pi / 2) * w, cy - math.cos(r - math.pi / 2) * w)
        d.polygon([tip, l, rr], fill=255)
    d.ellipse([cx - 18, cy - 18, cx + 18, cy + 18], fill=0)
    d.ellipse([cx - 8, cy - 8, cx + 8, cy + 8], fill=255)
    return c


def m_cloud():
    c = canvas()
    d = ImageDraw.Draw(c)
    # 雲海: 上に昇る日、下に二段の雲 (段のあいだに隙間、上の縁は丸い瘤)
    d.ellipse([44, 36, 116, 108], fill=255)
    for y, xs in ((176, (10, 54, 98, 142)), (250, (-12, 32, 76, 120, 164))):
        for cx in xs:
            d.ellipse([cx - 26, y - 30, cx + 26, y + 18], fill=255)
        d.rectangle([0, y - 6, 160, y + 30], fill=255)
    d.rectangle([0, 210, 160, 222], fill=0)
    return c


def m_heart():
    c = canvas()
    d = ImageDraw.Draw(c)
    # 巨神の心臓: 心臓の形に、上へ伸びる二本の脈、真ん中に割れ目
    d.ellipse([18, 110, 86, 180], fill=255)
    d.ellipse([74, 110, 142, 180], fill=255)
    d.polygon([(20, 156), (140, 156), (80, 250)], fill=255)
    d.rectangle([44, 60, 58, 120], fill=255)
    d.rectangle([100, 70, 114, 120], fill=255)
    d.line([(84, 130), (72, 166), (90, 186), (78, 220)], fill=0, width=7)
    return c


MOTIFS = [("whale", "岩鯨", m_whale), ("bell", "鐘", m_bell), ("titan_eye", "巨神の目", m_eye), ("moon", "月と星", m_moon),
          ("wing", "雷翼", m_wing), ("shell", "潮殻", m_shell), ("ember", "余燼", m_ember), ("bolt", "雷", m_bolt),
          ("tower", "あいだの塔", m_tower), ("compass", "八方の針", m_compass), ("cloud_sea", "雲海", m_cloud), ("heart", "巨神の心臓", m_heart)]
MOTIF_PAIRS = [("blue", "yellow"), ("black", "light_blue"), ("red", "white")]


def main():
    tex = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    nm = names()
    tiles = []
    for i, (eid, unlock, layers, new) in enumerate(emblems()):
        base, accent = PAIRS[i % len(PAIRS)]
        sub = ("新" if new else "") + ("・功績" if unlock and new else "功績" if unlock else "")
        tiles.append((banner(tex, layers, base, accent), nm.get(eid, eid), sub, new))
    sheet(tiles, 12, f"ギルドの旗の模様 {len(tiles)} 種 (青緑の「新」= 2026-11-01 に足したもの。色は見本)", OUT / "emblems.png")
    (OUT / "motifs").mkdir(exist_ok=True)
    tiles = []
    for mid, label, fn in MOTIFS:
        mask = finish(fn())
        mask.save(OUT / "motifs" / (mid + ".png"))
        for k, (base, accent) in enumerate(MOTIF_PAIRS):
            tiles.append((banner(tex, [(mask, True)], base, accent), label if k == 0 else "", "", False))
    sheet(tiles, 12, "この世界の紋のラフ 12 種 (それぞれ三つの色の組。旗の模様の絵として足し、ほかの模様と重ねられる)", OUT / "motifs.png")


if __name__ == "__main__":
    main()
