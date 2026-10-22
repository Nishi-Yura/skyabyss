"""tools/gen/shots/gen_weapon_lp_shots.py の絵を、武器ごとのコンタクトシートにまとめる。

使い方: python tools/sheets/weapon_lp_sheet.py [武器の id ...]  (省くと撮れているもの全部)
入力: run/shots/weapon_lp_<id>/shots/weapon_lp_<id>/*.png
出力: docs/review/previews/weapon_lp_<id>.png (行 = 一人称 / 三人称、列 = 階級 x 強化 9 + 元のボクセル) と、
      弓の引き・盾の構え・地面と額縁 (weapon_lp_<id>_{draw,block,world}.png)、元の絵のコピー weapon_lp_<id>_<名前>.png
"""
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/previews"
TIERS = ["common", "rare", "legend"]
LEVELS = [0, 5, 10]
CROP = {"fp": (480, 100, 1280, 720), "tp": (420, 150, 860, 640), "world": (140, 120, 1140, 700), "draw": (420, 100, 1280, 720), "block": (420, 100, 1280, 720)}


def load(src, name, kind):
    f = src / f"{name}.png"
    if not f.exists():
        return None
    im = Image.open(f).convert("RGB").crop(CROP.get(kind, (0, 0, 1280, 720)))
    s = 300 / max(im.size)
    return im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.LANCZOS)


def grid(rows, labels_cols, labels_rows, path, cell=300):
    sheet = Image.new("RGB", (cell * len(labels_cols) + 70, cell * len(labels_rows) + 18), (20, 20, 24))
    d = ImageDraw.Draw(sheet)
    for c, t in enumerate(labels_cols):
        d.text((70 + c * cell + 4, 3), t, fill=(255, 255, 0))
    for r, t in enumerate(labels_rows):
        d.text((4, 18 + r * cell + cell // 2), t, fill=(255, 255, 0))
        for c, im in enumerate(rows[r]):
            if im is not None:
                sheet.paste(im, (70 + c * cell, 18 + r * cell))
    sheet.save(path)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    ids = sys.argv[1:] or sorted(p.name[len("weapon_lp_"):] for p in (ROOT / "run/shots").glob("weapon_lp_*") if (p / "shots" / p.name).is_dir() and p.name not in ("weapon_lp_smoke", "weapon_lp_perf"))
    for w in ids:
        src = ROOT / f"run/shots/weapon_lp_{w}/shots/weapon_lp_{w}"
        if not src.is_dir():
            continue
        cols = [f"{t} +{lv}" for t in TIERS for lv in LEVELS] + ["voxel"]
        rows = []
        for v in ("fp", "tp"):
            row = [load(src, f"{w}_{v}_{t}_p{lv}", v) for t in TIERS for lv in LEVELS] + [load(src, f"{w}_{v}_voxel", v)]
            rows.append(row)
        grid(rows, cols, ["fp", "tp"], OUT / f"weapon_lp_{w}.png")
        extra = {"draw": ["draw0", "draw1", "draw2"], "block": ["block_rare_p0", "block_legend_p10"], "world": ["world_p0", "world_p10"]}
        for kind, names in extra.items():
            r = []
            for v in (("fp", "tp") if kind in ("draw", "block") else ("",)):
                row = [load(src, f"{w}_{v}_{n}" if v else f"{w}_{n}", kind) for n in names]
                if any(x is not None for x in row):
                    r.append((v or "world", row))
            if r:
                grid([x[1] for x in r], names, [x[0] for x in r], OUT / f"weapon_lp_{w}_{kind}.png")
        for f in src.glob("*.png"):
            shutil.copyfile(f, OUT / f"weapon_lp_{f.name}")
        print(w)


if __name__ == "__main__":
    main()
