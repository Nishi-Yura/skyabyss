# 上から落ちる影の before / after を一枚にまとめる。
# python tools/overhead_shadow_sheet.py <before の空の影> <after の空の影> <before の鯨> <after の鯨>  (run/shots/osp/shots/overhead_shadow_{pass_off,pass,whale_off,whale}) [出力]
import sys
from PIL import Image, ImageDraw, ImageFont

SKY = [("a_open_out", "sky / outside, roof"), ("a_roof_out", "sky / in house, door out"), ("a_roof_up", "sky / under roof, up"), ("a_tree_up", "sky / under tree, up"),
       ("a_doorway_in", "sky / under cabin roof"), ("b_edge_front_high", "sky edge / high view"), ("b_edge_side_wide", "sky edge / side view"), ("b_edge_door", "sky edge / house wall")]
WHALE = [("whale_out_wide", "whale / outside"), ("whale_roof_edge", "whale / roof edge"), ("whale_inside", "whale / in house"),
         ("whale_doorway_in", "whale / under cabin"), ("whale_tree", "whale / tree")]


def main():
    sb, sa, wb, wa = sys.argv[1:5]
    out = sys.argv[5] if len(sys.argv) > 5 else "docs/review/previews/overhead_shadow_sheet.png"
    w, h, pad, top, cols = 400, 225, 6, 24, 4
    try:
        font = ImageFont.truetype("arial.ttf", 15)
    except OSError:
        font = ImageFont.load_default()
    blocks = [("BEFORE (sky)", sb, SKY), ("AFTER (sky)", sa, SKY), ("BEFORE (whale)", wb, WHALE), ("AFTER (whale)", wa, WHALE)]
    height = pad
    for _, _, names in blocks:
        height += top + ((len(names) + cols - 1) // cols) * (h + pad)
    sheet = Image.new("RGB", (cols * (w + pad) + pad, height), (20, 20, 24))
    d = ImageDraw.Draw(sheet)
    y = pad
    for title, folder, names in blocks:
        d.text((pad, y + 3), title, fill=(255, 255, 255), font=font)
        y += top
        for i, (n, label) in enumerate(names):
            x = pad + (i % cols) * (w + pad)
            yy = y + (i // cols) * (h + pad)
            try:
                im = Image.open(f"{folder}/{n}.png").convert("RGB").resize((w, h), Image.LANCZOS)
                sheet.paste(im, (x, yy))
            except OSError:
                d.rectangle((x, yy, x + w, yy + h), outline=(120, 60, 60))
            d.text((x + 6, yy + 4), label, fill=(255, 255, 160), font=font)
        y += ((len(names) + cols - 1) // cols) * (h + pad)
    sheet.save(out)
    print(out, sheet.size)


main()
