"""tools/gen/shots/gen_weapon_pose_shots.py の絵を、武器ごとの 1 枚 (行 = 一人称 / 三人称正面 / 三人称横向き、列 = 構え / 0.2 / 0.4 / 0.55 / 0.75) にまとめる。

使い方: python tools/sheets/weapon_pose_sheet.py [撮った絵のフォルダ] [出力フォルダ (既定 docs/review/previews)]
出力: <出力>/weapon_pose_<武器>.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run/shots/weapon_poses/shots/weapon_poses"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/previews"
COLS = ["idle", "0.2", "0.4", "0.55", "0.75"]
ROWS = {"fp": (480, 100, 1280, 720), "tp": (420, 190, 860, 620), "tp34": (420, 190, 860, 620)}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    names = sorted({p.name.rsplit("_", 2)[0] for p in SRC.glob("*_fp_idle.png")})
    for w in names:
        cells = []
        for view, box in ROWS.items():
            row = []
            for c in COLS:
                f = SRC / f"{w}_{view}_{c}.png"
                if not f.exists():
                    continue
                im = Image.open(f).convert("RGB").crop(box)
                s = 400 / max(im.size)
                row.append(im.resize((int(im.width * s), int(im.height * s)), Image.LANCZOS))
            if row:
                cells.append((view, row))
        if not cells:
            continue
        cw, ch = 400, 400
        sheet = Image.new("RGB", (cw * len(COLS), ch * len(cells)), (20, 20, 24))
        d = ImageDraw.Draw(sheet)
        for r, (view, row) in enumerate(cells):
            for c, im in enumerate(row):
                sheet.paste(im, (c * cw, r * ch))
                d.text((c * cw + 6, r * ch + 4), f"{view} {COLS[c]}", fill=(255, 255, 0))
        sheet.save(OUT / f"weapon_pose_{w}.png")
        print(w)


if __name__ == "__main__":
    main()
