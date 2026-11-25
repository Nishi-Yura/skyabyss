# -*- coding: utf-8 -*-
"""鯨見の町の試作の比べ絵: 同じ場所・向きの「今」と「前もって組んだ」を左右に並べる。

    python -I tools/gen/world/authored/cmp_sheet.py  → docs/review/authored_whale_cmp.png
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[4]
BEFORE = ROOT / "run/shots/whale_authored/shots/whale_cmp_before"
AFTER = ROOT / "run/shots/whale_after/shots/whale_cmp_after"
OUT = ROOT / "docs/review/authored_whale_cmp.png"
VIEWS = [("air_se", "南東の空から"), ("air_far_s", "南の丘から町へ"), ("gate_east", "東の街道から門へ"),
         ("gate_north", "北の街道から門へ"), ("gate_west", "西の道から"), ("spire_se", "南西の岩山 (町の西から)"),
         ("spire_nw", "北西の岩山 (町の北から)"), ("hill_view", "南西の丘の上から"),
         ("pond", "南東の池と滝"), ("arch", "北東の街道の岩の門")]
W, H = 720, 405


def font(size):
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


def main():
    f = font(22)
    head = 44
    sheet = Image.new("RGB", (W * 2 + 12, head + len(VIEWS) * (H + 34)), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    d.text((12, 10), "今", font=f, fill=(230, 230, 230))
    d.text((W + 24, 10), "前もって組んだ", font=f, fill=(250, 220, 140))
    y = head
    for name, label in VIEWS:
        d.text((12, y + 4), label, font=font(18), fill=(200, 200, 200))
        for k, base in enumerate((BEFORE, AFTER)):
            p = base / f"{name}.png"
            if p.exists():
                sheet.paste(Image.open(p).convert("RGB").resize((W, H)), (k * (W + 12), y + 30))
        y += H + 34
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(OUT)


if __name__ == "__main__":
    main()


def trees():
    """mod の木のラフ 3 案 (A 鯨骨・B 苔衣・C 灯り葉) と並べた姿 → docs/review/authored_whale_trees.png"""
    out = ROOT / "docs/review/authored_whale_trees.png"
    items = [("trees_all", "三つ並べて (北から見て、左から C・B・A)"), ("tree_a", "A 鯨骨の巨木"), ("tree_b", "B 苔衣の大樹"), ("tree_c", "C 灯り葉の樹")]
    sheet = Image.new("RGB", (W * 2 + 12, 2 * (H + 34) + 10), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    for k, (name, label) in enumerate(items):
        x0, y0 = (k % 2) * (W + 12), 10 + (k // 2) * (H + 34)
        d.text((x0 + 8, y0 + 2), label, font=font(18), fill=(230, 220, 160))
        p = AFTER / f"{name}.png"
        if p.exists():
            sheet.paste(Image.open(p).convert("RGB").resize((W, H)), (x0, y0 + 28))
    sheet.save(out)
    print(out)


def nature():
    """岩山と灯り葉の樹の林 → docs/review/authored_whale_nature.png"""
    out = ROOT / "docs/review/authored_whale_nature.png"
    items = [("spire_se", "南西の岩山 (なだらかにした)"), ("air_far_s", "南の丘から町へ (左右に岩山)"),
             ("massif_s", "町の南の岩山と木"), ("pond", "池と滝の岩"),
             ("grove_over", "北の森の灯り葉の樹の林 (上から)"), ("grove_in", "林の中")]
    rows = (len(items) + 1) // 2
    sheet = Image.new("RGB", (W * 2 + 12, rows * (H + 34) + 10), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    for k, (name, label) in enumerate(items):
        x0, y0 = (k % 2) * (W + 12), 10 + (k // 2) * (H + 34)
        d.text((x0 + 8, y0 + 2), label, font=font(18), fill=(230, 220, 160))
        p = AFTER / f"{name}.png"
        if p.exists():
            sheet.paste(Image.open(p).convert("RGB").resize((W, H)), (x0, y0 + 28))
    sheet.save(out)
    print(out)


def forest():
    """森のふつうの木 (垂れ枝の樅・骨白の細木・苔の低木) と灯り葉の樹の林 → docs/review/authored_whale_forest.png"""
    out = ROOT / "docs/review/authored_whale_forest.png"
    items = [("air_se", "南東の空から"), ("spire_nw", "町の北の森 (垂れ枝の樅)"), ("air_far_s", "南の丘から町へ"),
             ("grove_over", "北の森 (灯り葉の樹の林と樅)"), ("hill_view", "南西の岩山と森"), ("gate_north", "北の門の前")]
    sheet = Image.new("RGB", (W * 2 + 12, 3 * (H + 34) + 10), (24, 24, 28))
    d = ImageDraw.Draw(sheet)
    for k, (name, label) in enumerate(items):
        x0, y0 = (k % 2) * (W + 12), 10 + (k // 2) * (H + 34)
        d.text((x0 + 8, y0 + 2), label, font=font(18), fill=(230, 220, 160))
        p = AFTER / f"{name}.png"
        if p.exists():
            sheet.paste(Image.open(p).convert("RGB").resize((W, H)), (x0, y0 + 28))
    sheet.save(out)
    print(out)


if __name__ == "__main__" and len(__import__("sys").argv) > 1:
    {"trees": trees, "nature": nature, "forest": forest}[__import__("sys").argv[1]]()
