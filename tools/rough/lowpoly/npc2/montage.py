"""NPC v2 の見本 (roughs/<名>.png) から、3/4・後ろ・話す・顔を抜き出して一覧にする。
  python -I tools/rough/lowpoly/npc2/montage.py <出力.png> [名 ...] [--cells 1,3,4,5] [--w 160]"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent.parent))
OUT = HERE.parents[3] / "docs/review/npc_models_v2/roughs"


def main(argv):
    cells = [1, 3, 5]
    w = 160
    if "--cells" in argv:
        i = argv.index("--cells")
        cells = [int(x) for x in argv[i + 1].split(",")]
        argv = argv[:i] + argv[i + 2:]
    if "--w" in argv:
        i = argv.index("--w")
        w = int(argv[i + 1])
        argv = argv[:i] + argv[i + 2:]
    out = argv[0]
    names = argv[1:]
    h = int(w * 400 / 300)
    per = 4 if len(cells) <= 3 else 3
    tiles = []
    for n in names:
        im = Image.open(OUT / f"{n}.png").convert("RGB")
        t = Image.new("RGB", (w * len(cells), h + 18), (16, 16, 22))
        for j, c in enumerate(cells):
            t.paste(im.crop((c * 300, 40, c * 300 + 300, 440)).resize((w, h), Image.LANCZOS), (j * w, 18))
        ImageDraw.Draw(t).text((4, 2), n, fill=(236, 232, 218))
        tiles.append(t)
    tw, th = tiles[0].size
    rows = (len(tiles) + per - 1) // per
    sheet = Image.new("RGB", (tw * per, th * rows), (10, 10, 14))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % per) * tw, (i // per) * th))
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main(sys.argv[1:])
