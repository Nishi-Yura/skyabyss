"""技の見た目の絵を、サイト用に縮めて site/img/skill_fx/ へ書き出す。

入力: tools/sources/site/skill_fx/<技>_{release,impact,linger}.png（放ち・当たり・残り。残りは無くてよい）
出力: site/img/skill_fx/<技>_<段>.webp（幅 320 px まで。webp が使えなければ optimize した png）

使い方 (リポジトリ直下で): python tools/site/gen_skill_fx.py
pages_b.py の技のページは、ここで書き出した絵だけを参照する（絵が無い技は「準備中」と出す）。
FX が仕上がったら、tools/sources/site/skill_fx/ を新しい撮影で置き換えて、この generator を流す。
手順は docs/review/site_refresh_2026-10-24.md の「技の見た目の更新」を見ること。
"""
import glob
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "tools", "sources", "site", "skill_fx")
OUT = os.path.join(ROOT, "site", "img", "skill_fx")
PHASES = ("release", "impact", "linger")
MAX_W = 320


def convert(src, dst):
    im = Image.open(src).convert("RGB")
    if im.width > MAX_W:
        h = round(im.height * MAX_W / im.width)
        im = im.resize((MAX_W, h), Image.LANCZOS)
    im.save(dst, "WEBP", quality=84, method=6)


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, "*")):
        os.remove(old)
    n = 0
    for src in sorted(glob.glob(os.path.join(SRC, "*.png"))):
        name = os.path.splitext(os.path.basename(src))[0]
        skill, _, phase = name.rpartition("_")
        if phase not in PHASES:
            continue
        convert(src, os.path.join(OUT, name + ".webp"))
        n += 1
    print("skill_fx: %d frames -> %s" % (n, os.path.relpath(OUT, ROOT)))


if __name__ == "__main__":
    main()
