"""技の見た目の動画 (撮影の録画の絵) を、サイト用の動く webp にする。

入力: run/shots/skill_fx/<技>/0000.png … (tools/shots/skill_fx.txt の record が書いた絵。1280x720)
      tools/shots/skill_fx_clips.json (生成器 tools/gen/shots/gen_skill_fx_shots.py が書く。fps と、当たりの絵の番号)
出力: site/img/skill_fx/<技>.webp        動く絵 (幅 640、ループ、再生は実時間。目安 500 KB 以下)
      site/img/skill_fx/<技>_poster.webp  止めた絵 (当たりの瞬間。動く絵を読む前・動きを止める人のための絵)

使い方 (リポジトリ直下で): python -I tools/site/encode_skill_fx_clips.py [録画の場所]
  録画の場所の既定は run/shots/skill_fx/ (撮影の台本の名前が skill_fx の場合)。
ffmpeg は使わない (Pillow の動く webp だけ)。大きすぎる動画は、画質を下げて目安の大きさまで縮める。
build の前に流す (site/img/skill_fx/ は書き直すが、無い技は絵が無いまま)。
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
CLIPS = os.path.join(ROOT, "tools", "shots", "skill_fx_clips.json")
OUT = os.path.join(ROOT, "site", "img", "skill_fx")
DEFAULT_FRAMES = os.path.join(ROOT, "run", "shots", "skill_fx")
WIDTH = 640
TARGET_KB = 500
# 目安を超えたら、この順に画質を下げる (lossy webp の品質)
QUALITY_STEPS = (80, 72, 64, 56)


def load_frames(folder, count):
    """録画の絵を、幅 WIDTH に縮めて読む (番号順)。足りなければ None"""
    frames = []
    for i in range(count):
        path = os.path.join(folder, "%04d.png" % i)
        if not os.path.exists(path):
            return None
        im = Image.open(path).convert("RGB")
        h = round(im.height * WIDTH / im.width)
        frames.append(im.resize((WIDTH, h), Image.LANCZOS))
    return frames


def durations(count, fps):
    """各コマの長さ (ms)。合計が実時間 (count / fps 秒) に合うように、端数を散らす"""
    return [round(1000 * (i + 1) / fps) - round(1000 * i / fps) for i in range(count)]


def save_clip(frames, fps, path):
    """動く webp を、目安の大きさに収まる一番高い画質で書く。書いた画質とバイト数を返す"""
    last = None
    for q in QUALITY_STEPS:
        frames[0].save(path, "WEBP", save_all=True, append_images=frames[1:], duration=durations(len(frames), fps),
                       loop=0, quality=q, method=6)
        last = (q, os.path.getsize(path))
        if last[1] <= TARGET_KB * 1024:
            break
    return last


def main():
    folder = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_FRAMES
    with open(CLIPS, encoding="utf-8") as f:
        clips = json.load(f)
    os.makedirs(OUT, exist_ok=True)
    total = 0
    missing = []
    for skill, info in clips.items():
        frames = load_frames(os.path.join(folder, skill), info["frames"])
        if frames is None:
            missing.append(skill)
            continue
        fps = info["fps"]
        clip = os.path.join(OUT, skill + ".webp")
        q, size = save_clip(frames, fps, clip)
        total += size
        poster = frames[min(info["impact_frame"], len(frames) - 1)]
        poster.save(os.path.join(OUT, skill + "_poster.webp"), "WEBP", quality=84, method=6)
        print("%-16s %3d frames %5.2f s  q%d  %6.1f KB" % (skill, len(frames), len(frames) / fps, q, size / 1024))
    print("total %.1f KB -> %s" % (total / 1024, os.path.relpath(OUT, ROOT)))
    if missing:
        print("no frames for: " + ", ".join(missing) + " (%s)" % os.path.relpath(folder, ROOT))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
