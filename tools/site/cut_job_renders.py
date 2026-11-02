"""職の立ち姿の緑の幕を抜いて、背景の透けた絵にする。

入力: run/shots/site_jobs/shots/site_jobs/<職>_<idle|swing>.png (tools/shots/site_jobs.txt。ライムのコンクリートの幕の前で正面から)
出力: tools/sources/site/jobs/<職>.png (幕を抜き、人の形のまわりで切った RGBA) と <職>_face.png (職の一覧の顔。96 四方)。
      サイトの絵は gen_site_shots.py が作る

使い方 (リポジトリ直下で): python -I tools/site/cut_job_renders.py
職ごとの姿 (構えか振りか) は POSE で選ぶ。
"""
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "run", "shots", "site_jobs", "shots", "site_jobs")
OUT = os.path.join(ROOT, "tools", "sources", "site", "jobs")
POSE = {"warrior": "idle", "smith": "swing", "mage": "idle", "archer": "idle", "assassin": "swing", "shaman": "idle"}
MARGIN = 12
# 顔の切り出し (元の 1280 四方の絵の中。カメラと人の位置は台本で決まっているので、どの職も同じ所)
FACE = (470, 470, 800, 800)


def is_screen(r, g, b):
    # ライムの幕 (日なた・日かげ・足もとの影の濃い緑も)。防具に緑はほとんど無い
    return g > 40 and g > r * 1.3 and g > b * 1.6


def main():
    os.makedirs(OUT, exist_ok=True)
    for job, pose in POSE.items():
        im = Image.open(os.path.join(SRC, "%s_%s.png" % (job, pose))).convert("RGBA")
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                r, g, b, _ = px[x, y]
                if is_screen(r, g, b):
                    px[x, y] = (0, 0, 0, 0)
        im.crop(FACE).resize((96, 96), Image.LANCZOS).save(os.path.join(OUT, job + "_face.png"), optimize=True)
        box = im.getbbox()
        box = (max(0, box[0] - MARGIN), max(0, box[1] - MARGIN), min(im.width, box[2] + MARGIN), min(im.height, box[3] + MARGIN))
        im.crop(box).save(os.path.join(OUT, job + ".png"), optimize=True)
        print(job, pose, box)


if __name__ == "__main__":
    main()
