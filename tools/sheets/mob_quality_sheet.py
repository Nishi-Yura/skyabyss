"""敵の質の見直しのスクショ一覧 (docs/review/mob_quality_v2/sheet_<tag>.png) を組む。
入力: run/shots/mob_quality_<tag>/shots/mob_quality_<tag>/mq_<id>_{q,side,back,atk}.png (tools/gen/shots/gen_mob_quality_shots.py の台本)
使い方: python -I tools/sheets/mob_quality_sheet.py <tag> [--pair <before_tag>] [名前...]
  --pair を付けると、名前ごとに「前 (before_tag) / 後 (tag)」の 2 段で並べる (docs/review/mob_quality_v2/<名前>_compare.png)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/mob_quality_v2"
VIEWS = ["q", "side", "back", "atk"]
CW, CH = 320, 180


def font(sz):
    for f in ["C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc", "C:/Windows/Fonts/YuGothM.ttc"]:
        try:
            return ImageFont.truetype(f, sz)
        except OSError:
            pass
    return ImageFont.load_default()


def shots_dir(tag):
    return ROOT / f"run/shots/mob_quality_{tag}/shots/mob_quality_{tag}"


def row(tag, eid):
    d = shots_dir(tag)
    cells = []
    for v in VIEWS:
        p = d / f"mq_{eid}_{v}.png"
        if p.exists():
            im = Image.open(p).convert("RGB")
            # 中央を切り出して縮める (モブが真ん中にいる)
            w, h = im.size
            box = (int(w * 0.18), int(h * 0.1), int(w * 0.82), int(h * 0.9))
            cells.append(im.crop(box).resize((CW, CH), Image.LANCZOS))
        else:
            cells.append(None)
    return cells


def names_of(tag):
    return sorted({p.stem[3:].rsplit("_", 1)[0] for p in shots_dir(tag).glob("mq_*_q.png")})


def main():
    args = sys.argv[1:]
    tag = args.pop(0)
    pair = None
    if args and args[0] == "--pair":
        args.pop(0)
        pair = args.pop(0)
    names = args or names_of(tag)
    OUT.mkdir(parents=True, exist_ok=True)
    f = font(18)
    if pair:
        for eid in names:
            img = Image.new("RGB", (110 + CW * 4, 30 + CH * 2), (24, 24, 28))
            dr = ImageDraw.Draw(img)
            dr.text((8, 6), f"{eid}  斜め前 / 横 / 後ろ / 攻撃の構え", fill=(230, 230, 230), font=f)
            for r, (t, label) in enumerate([(pair, "直す前"), (tag, "直した後")]):
                dr.text((8, 30 + r * CH + CH // 2 - 10), label, fill=(240, 220, 160), font=f)
                for c, cell in enumerate(row(t, eid)):
                    if cell:
                        img.paste(cell, (110 + c * CW, 30 + r * CH))
            img.save(OUT / f"{eid}_compare.png", optimize=True)
            print(OUT / f"{eid}_compare.png")
        return
    per = 14
    for page in range(0, len(names), per):
        chunk = names[page:page + per]
        img = Image.new("RGB", (220 + CW * 4, 30 + CH * len(chunk)), (24, 24, 28))
        dr = ImageDraw.Draw(img)
        dr.text((8, 6), "敵の見直し  斜め前 / 横 / 後ろ / 攻撃の構え", fill=(230, 230, 230), font=f)
        for r, eid in enumerate(chunk):
            dr.text((8, 30 + r * CH + CH // 2 - 10), eid, fill=(240, 220, 160), font=f)
            for c, cell in enumerate(row(tag, eid)):
                if cell:
                    img.paste(cell, (220 + c * CW, 30 + r * CH))
        p = OUT / f"sheet_{tag}_{page // per + 1}.png"
        img.save(p, optimize=True)
        print(p)


main()
