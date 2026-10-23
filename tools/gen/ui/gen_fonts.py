#!/usr/bin/env python3
"""mod の字のリソースパック (skyabyss_font) を書く。はじめての起動で選んだ状態になり、リソースパックの画面で外すとバニラの字に戻る (client/ui/ModFont)。

  本文の字: DotGothic16 (SIL OFL 1.1。tools/sources/fonts/DotGothic16_OFL.txt)
            バニラの字の前に置き、英数も日本語もこの字で描く (点の字に無い字だけバニラの字・Unifont で出る)。
            元の TTF は点が 1 マスより少し小さく、ゲームの中でふちが灰色ににじむので、16px の白黒の点に描き直し、
            点をちょうど 1 マスの四角にした TTF (dotgothic16.ttf) を作って入れる。GUI の 8 px の高さ (1 マス = GUI の 0.5 px) で描き、
            oversample 6 で 1 マス = 3 画素なので、GUI 倍率 2・3 で画面の画素にちょうど重なる。送りは元の設計どおり (全角 8 px・半角 4 px)。
  題字の字: Zen Antique (SIL OFL 1.1。tools/sources/fonts/ZenAntique_OFL.txt)
            skyabyss:title・title_mid・title_small (描く大きさごと)。章の題字・場所の名前・余韻の札だけに使う (client/ui/ModFont.title)。
            lang に出てくる字とかな・英数・記号だけに削る (文を足したらこれを流し直す。無い字は本文の字で出る)。

出力: src/main/resources/resourcepacks/skyabyss_font/ (pack.mcmeta、assets/minecraft/font/default.json、
      assets/skyabyss/font/dotgothic16.ttf・title*.json・zen_antique.ttf、CREDITS.txt)
使い方: python -I tools/gen/ui/gen_fonts.py   (fontTools と freetype-py が要る)
"""
import glob
import json
import os
import shutil

import freetype
from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SRC = os.path.join(ROOT, "tools", "sources", "fonts")
PACK = os.path.join(ROOT, "src", "main", "resources", "resourcepacks", "skyabyss_font")
FONT_DIR = os.path.join(PACK, "assets", "skyabyss", "font")

BODY_SRC = os.path.join(SRC, "DotGothic16-Regular.ttf")
TITLE_SRC = os.path.join(SRC, "ZenAntique-Regular.ttf")

# 点の字の置き方: 16 マスの高さ、上から 14 マス目が字の下の線 (Unifont と同じ)
CELL = 16
BASELINE = 14
# 点の字の oversample。1 マス = 3 画素。client/ui/ModFont.PIXEL_OVERSAMPLE と同じ値にする (影のずれを直す目印)
PIXEL_OVERSAMPLE = 6

# かな・英数・記号は文に無くても入れておく
BASE_RANGES = [(0x20, 0x7E), (0xA0, 0xFF), (0x2010, 0x2027), (0x2030, 0x203B), (0x2190, 0x2193), (0x25A0, 0x25FF), (0x2600, 0x2606),
               (0x3000, 0x303F), (0x3040, 0x309F), (0x30A0, 0x30FF), (0xFF01, 0xFF5E)]


def lang_chars():
    chars = set()
    for p in sorted(glob.glob(os.path.join(ROOT, "src", "main", "resources", "assets", "*", "lang", "*.json"))):
        with open(p, encoding="utf-8") as f:
            for v in json.load(f).values():
                if isinstance(v, str):
                    chars.update(ord(c) for c in v)
    return chars


def write_font(src, dst, unicodes):
    opts = subset.Options()
    opts.hinting = False
    opts.layout_features = ["kern", "palt", "vert"]
    opts.name_IDs = ["*"]
    opts.name_languages = ["*"]
    opts.notdef_outline = True
    opts.drop_tables += ["DSIG"]
    font = TTFont(src)
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=unicodes)
    sub.subset(font)
    font.save(dst)
    return os.path.getsize(dst)


def dot_glyphs():
    # 各字を 16px の白黒 (FreeType のモノクロ描き) で描き、{字: (行ごとの点の列, 全角か)} にする。点の無い字 (空白) は入れない
    face = freetype.Face(BODY_SRC)
    face.set_pixel_sizes(CELL, CELL)
    flags = freetype.FT_LOAD_RENDER | freetype.FT_LOAD_NO_BITMAP | freetype.FT_LOAD_TARGET_MONO
    out = {}
    for code, gi in face.get_chars():
        if code < 0x20 or 0xE000 <= code <= 0xF8FF:
            continue
        face.load_glyph(gi, flags)
        g = face.glyph
        b = g.bitmap
        # 送りは字形の設計の値で見る (モノクロ描きの送りは丸めで 1 px ずれる)
        full = g.linearHoriAdvance / 65536 > CELL / 2 + 0.5
        width = CELL if full else CELL // 2
        rows = [0] * CELL
        ink = False
        for r in range(b.rows):
            y = BASELINE - g.bitmap_top + r
            if not 0 <= y < CELL:
                continue
            for c in range(b.width):
                if (b.buffer[r * b.pitch + c // 8] >> (7 - c % 8)) & 1:
                    x = g.bitmap_left + c
                    if 0 <= x < width:
                        rows[y] |= 1 << (width - 1 - x)
                        ink = True
        if ink:
            out[code] = (rows, full)
    return out


def build_ttf(glyphs, dst):
    # 点の字を、1 マス = 64 単位の四角の輪郭にした TTF にする (1 em = 16 マス = 1024)。横に続く点は一本の四角に、
    # 同じ並びが縦に続く行はまとめて一つの四角にする (重ならない)
    from fontTools.fontBuilder import FontBuilder
    from fontTools.pens.ttGlyphPen import TTGlyphPen
    unit = 1024 // CELL
    order = [".notdef"]
    cmap = {}
    shapes = {".notdef": []}
    widths = {".notdef": CELL // 2 * unit}
    for code in sorted(glyphs):
        rows, full = glyphs[code]
        width = CELL if full else CELL // 2
        name = f"u{code:04X}"
        order.append(name)
        cmap[code] = name
        widths[name] = width * unit
        rects = []
        open_runs = {}
        for y in range(CELL + 1):
            runs = set()
            if y < CELL:
                x = 0
                while x < width:
                    if rows[y] >> (width - 1 - x) & 1:
                        x1 = x
                        while x1 < width and rows[y] >> (width - 1 - x1) & 1:
                            x1 += 1
                        runs.add((x, x1))
                        x = x1
                    else:
                        x += 1
            for run in list(open_runs):
                if run not in runs:
                    rects.append((run[0], open_runs.pop(run), run[1], y))
            for run in runs:
                open_runs.setdefault(run, y)
        shapes[name] = rects
    fb = FontBuilder(1024, isTTF=True)
    fb.setupGlyphOrder(order)
    fb.setupCharacterMap(cmap)
    built = {}
    for name in order:
        pen = TTGlyphPen(None)
        for x0, y0, x1, y1 in shapes[name]:
            # y は上から数えたマス。字の下の線 (BASELINE) が 0
            top = (BASELINE - y0) * unit
            bottom = (BASELINE - y1) * unit
            pen.moveTo((x0 * unit, bottom))
            pen.lineTo((x0 * unit, top))
            pen.lineTo((x1 * unit, top))
            pen.lineTo((x1 * unit, bottom))
            pen.closePath()
        built[name] = pen.glyph()
    fb.setupGlyf(built)
    fb.setupHorizontalMetrics({n: (widths[n], 0) for n in order})
    fb.setupHorizontalHeader(ascent=BASELINE * unit, descent=-(CELL - BASELINE) * unit)
    fb.setupNameTable({"familyName": "DotGothic16 Pixel", "styleName": "Regular"})
    fb.setupOS2(sTypoAscender=BASELINE * unit, sTypoDescender=-(CELL - BASELINE) * unit, usWinAscent=BASELINE * unit, usWinDescent=(CELL - BASELINE) * unit)
    fb.setupPost(keepGlyphNames=False)
    # 字を整える命令が一つも無いと、FreeType が自動の調整をかけて点のふちが灰色ににじむ。何もしない prep を入れてそれを止める
    from fontTools.ttLib import newTable
    from fontTools.ttLib.tables import ttProgram
    prep = newTable("prep")
    prep.program = ttProgram.Program()
    prep.program.fromAssembly(["PUSHB[ ]", "1", "SCANCTRL[ ]"])
    fb.font["prep"] = prep
    fb.save(dst)
    return os.path.getsize(dst)


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    if os.path.isdir(PACK):
        shutil.rmtree(PACK)
    os.makedirs(FONT_DIR)

    glyphs = dot_glyphs()
    body_size = build_ttf(glyphs, os.path.join(FONT_DIR, "dotgothic16.ttf"))

    want = lang_chars()
    for a, b in BASE_RANGES:
        want.update(range(a, b + 1))
    title_all = set(TTFont(TITLE_SRC).getBestCmap().keys())
    title_set = sorted(want & title_all)
    title_size = write_font(TITLE_SRC, os.path.join(FONT_DIR, "zen_antique.ttf"), title_set)

    dump(os.path.join(PACK, "pack.mcmeta"), {"pack": {"pack_format": 34, "description": "天と淵のあいだ: mod の字 (本文 DotGothic16・題字 Zen Antique)"}})
    # 空白 → 点の字 (英数も) → バニラの字 (点の字に無い字: アクセント付きの欧文・キリル文字など) → Unifont。全角の空白は全角の字と同じ送り
    body = [
        {"type": "space", "advances": {"　": CELL // 2}},
        {"type": "reference", "id": "minecraft:include/space"},
        {"type": "ttf", "file": "skyabyss:dotgothic16.ttf", "size": 8, "oversample": PIXEL_OVERSAMPLE, "shift": [0, 0]},
        {"type": "reference", "id": "minecraft:include/default", "filter": {"uniform": False}},
        {"type": "reference", "id": "minecraft:include/unifont"},
    ]
    dump(os.path.join(PACK, "assets", "minecraft", "font", "default.json"), {"providers": body})
    # 題字の字は大きさで二つ。oversample は画面での拡大の小さい側に合わせる (それより細かく描くと、縮めるときに細い線が消える)
    #   title       : 2 倍より大きく描く所 (章の題名 2.2 倍)。GUI 2 で 4.4 倍 → oversample 4
    #   title_mid   : 1.5 倍で描く所 (場所の名前・余韻の一言)。GUI 2 で 3 倍 → oversample 3
    #   title_small : 1〜1.2 倍で描く所 (章の番号・副題)。GUI 2 で 2 倍 → oversample 2
    # 足りない字は本文の字で。minecraft:default を参照すると同じ TTF の face を二つのスレッドが同時に読んで壊れることがあるので、並びを写す
    for name, ov in (("title", 4), ("title_mid", 3), ("title_small", 2)):
        dump(os.path.join(FONT_DIR, name + ".json"), {"providers": [
            {"type": "ttf", "file": "skyabyss:zen_antique.ttf", "size": 8, "oversample": ov, "shift": [0, 0]}] + [dict(p) for p in body]})
    with open(os.path.join(PACK, "CREDITS.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("DotGothic16 (c) 2020 The DotGothic16 Project Authors - SIL Open Font License 1.1 (redrawn as 16px pixel outlines)\n")
        f.write("Zen Antique (c) 2021 The Zen Antique Project Authors - SIL Open Font License 1.1 (subset)\n\n")
        with open(os.path.join(SRC, "DotGothic16_OFL.txt"), encoding="utf-8") as lic:
            f.write(lic.read())
    print(f"dotgothic16.ttf {body_size // 1024} KB ({len(glyphs)} 字)  zen_antique.ttf {title_size // 1024} KB ({len(title_set)} 字)")


if __name__ == "__main__":
    main()
