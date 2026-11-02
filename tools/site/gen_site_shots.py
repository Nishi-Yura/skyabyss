"""サイトのゲームの中の絵を作る。

入力: tools/sources/site/shots/<名前>.webp (ゲームの中で撮った絵。16:9・幅 1600。撮り方は
      tools/shots/site_showcase.txt・site_titans.txt・site_extra.txt。ほかの絵は各機能の確かめの撮影から選んだ)
出力: site/img/shot/<名前>.webp (幅 1600 と、一覧用の幅 640 の <名前>_s.webp。画質 80)
      site/img/og.jpg (共有したときに出る 1200x630 の絵。hero から切り出す)
      site/img/job/<職>.webp (職の立ち姿。元は tools/sources/site/jobs/<職>.png。cut_job_renders.py が作る)
      site/img/mob/<敵>.webp (wiki の敵の姿。元は tools/sources/site/mobs/<敵>.png。cut_mob_shots.py が作る)

使い方 (リポジトリ直下で): python -I tools/site/gen_site_shots.py
build の前に流す。入力にない古い出力は消す。
"""
import glob
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "tools", "sources", "site", "shots")
OUT = os.path.join(ROOT, "site", "img", "shot")
SIZES = {"": 1600, "_s": 640}


def resized(im, w):
    if im.width <= w:
        return im
    return im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)


def main():
    os.makedirs(OUT, exist_ok=True)
    keep = set()
    for p in sorted(glob.glob(os.path.join(SRC, "*.webp")) + glob.glob(os.path.join(SRC, "*.png"))):
        name = os.path.splitext(os.path.basename(p))[0]
        im = Image.open(p).convert("RGB")
        for suffix, w in SIZES.items():
            out = name + suffix + ".webp"
            resized(im, w).save(os.path.join(OUT, out), "WEBP", quality=80, method=6)
            keep.add(out)
        if name == "hero":
            # 1200x630 (1.91:1) へ、上下を切って縮める
            h = round(im.width / 1.905)
            top = (im.height - h) // 2
            im.crop((0, top, im.width, top + h)).resize((1200, 630), Image.LANCZOS).save(
                os.path.join(ROOT, "site", "img", "og.jpg"), "JPEG", quality=85)
    for p in glob.glob(os.path.join(OUT, "*.webp")):
        if os.path.basename(p) not in keep:
            os.remove(p)
    print("shots:", len(keep))
    # wiki の敵の姿 (cut_mob_shots.py が幕を抜いた 256 四方の RGBA)。透けたまま webp に
    mobs_out = os.path.join(ROOT, "site", "img", "mob")
    os.makedirs(mobs_out, exist_ok=True)
    for p in sorted(glob.glob(os.path.join(ROOT, "tools", "sources", "site", "mobs", "*.png"))):
        Image.open(p).convert("RGBA").save(os.path.join(mobs_out, os.path.splitext(os.path.basename(p))[0] + ".webp"), "WEBP", quality=85, method=6)
    # 職の立ち姿 (cut_job_renders.py が幕を抜いた RGBA)。高さ 720 まで、透けたまま webp に
    jobs_out = os.path.join(ROOT, "site", "img", "job")
    os.makedirs(jobs_out, exist_ok=True)
    for p in sorted(glob.glob(os.path.join(ROOT, "tools", "sources", "site", "jobs", "*.png"))):
        im = Image.open(p).convert("RGBA")
        if im.height > 720:
            im = im.resize((round(im.width * 720 / im.height), 720), Image.LANCZOS)
        im.save(os.path.join(jobs_out, os.path.splitext(os.path.basename(p))[0] + ".webp"), "WEBP", quality=88, method=6)


if __name__ == "__main__":
    main()
