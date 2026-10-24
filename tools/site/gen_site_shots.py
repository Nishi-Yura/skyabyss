"""サイトの全幅の実機の絵を作る。

入力: tools/sources/site/shots/<名前>.png (ゲームの中で撮った絵。16:9 を想定)
出力: site/img/shot/<名前>.webp (幅 1600 まで、画質 80 の webp)

使い方 (リポジトリ直下で): python -I tools/site/gen_site_shots.py
build の前に流す。入力の絵を差し替えたら、もう一度流す。
"""
import glob
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "tools", "sources", "site", "shots")
OUT = os.path.join(ROOT, "site", "img", "shot")
MAX_W = 1600


def main():
    os.makedirs(OUT, exist_ok=True)
    n = 0
    for p in sorted(glob.glob(os.path.join(SRC, "*.png"))):
        name = os.path.splitext(os.path.basename(p))[0]
        im = Image.open(p).convert("RGB")
        if im.width > MAX_W:
            h = round(im.height * MAX_W / im.width)
            im = im.resize((MAX_W, h), Image.LANCZOS)
        im.save(os.path.join(OUT, name + ".webp"), "WEBP", quality=80, method=6)
        n += 1
    print("shots:", n)


if __name__ == "__main__":
    main()
