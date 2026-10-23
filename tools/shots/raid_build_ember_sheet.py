# 余燼のレイドの部屋の見比べ (tools/shots/raid_build_ember.txt の絵を並べる)。
#   python -I tools/shots/raid_build_ember_sheet.py <絵のフォルダ> <出力.png> [<比べるフォルダ>]
# 部屋ごとに一行 (入口・中・見下ろし・振り返り)。比べるフォルダを渡すと、部屋ごとに 前 (上) / 後 (下) の二行
import os
import sys

from PIL import Image, ImageDraw

ROOMS = ["er1", "er2", "er3", "er4", "er5", "er6", "erm"]
VIEWS = ["a_entry", "b_inside", "c_overview", "d_back"]
W, H = 320, 180


def row(folder, room):
    out = []
    for v in VIEWS:
        p = os.path.join(folder, f"{room}_{v}.png")
        if not os.path.exists(p):
            p = p[:-4] + ".jpg"
        out.append(Image.open(p).convert("RGB").resize((W, H)) if os.path.exists(p) else Image.new("RGB", (W, H), (40, 0, 0)))
    return out


def main():
    src, dst = sys.argv[1], sys.argv[2]
    other = sys.argv[3] if len(sys.argv) > 3 else None
    lines = []
    for r in ROOMS:
        if other:
            lines.append((r.upper() + " before", row(src, r)))
            lines.append((r.upper() + " after", row(other, r)))
        else:
            lines.append((r.upper(), row(src, r)))
    sheet = Image.new("RGB", (W * len(VIEWS), H * len(lines)), (0, 0, 0))
    d = ImageDraw.Draw(sheet)
    for i, (label, imgs) in enumerate(lines):
        for j, im in enumerate(imgs):
            sheet.paste(im, (j * W, i * H))
        d.text((6, i * H + 4), label, fill=(255, 255, 0))
    sheet.save(dst)


if __name__ == "__main__":
    main()
