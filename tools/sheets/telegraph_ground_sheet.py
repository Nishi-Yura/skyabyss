"""予告の床の印を「歩ける床の面にだけ」出す直しの、前後比べの一覧 docs/review/previews/telegraph_ground_sheet.png を作る。

使い方: python tools/sheets/telegraph_ground_sheet.py [出力のパス]
入力: run/shots/tg_before/shots/telegraph_ground/*.png (直す前) と run/shots/tg_after/shots/telegraph_ground/*.png (直した後)。
      どちらも台本 tools/shots/telegraph_ground.txt を -PshotsName=tg_before / tg_after で撮ったもの
      (直す前は TelegraphRenderer・TelegraphClient を古い版に戻して撮る)。
出力: 5 つの場面 (平ら・段差と階段・壁と柱・モブ・低い天井) の行 × [斜め上 前・後 / 床の目の高さ 前・後] の 4 列。
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/telegraph_ground_sheet.png"
CELL = (480, 270)
LABEL_H = 22
SIDE_W = 150
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/msgothic.ttc"]

SCENES = [
    ("flat", "平らな地面", "いつもの床。前と同じに出る"),
    ("steps", "段差と階段", "1・2 段高い床には出る。3 段以上の壁の上には出ない。半ブロック・階段は面の高さに沿う"),
    ("wall", "壁と柱", "壁の面を駆け上がらない。柱の上にも出ない"),
    ("mobs", "モブがいる", "モブの頭・体の高さには出ない。足もとの床だけ"),
    ("ceiling", "低い天井", "空き 2 段の下は床に出る。空き 1 段の下の床と、天井の上には出ない"),
]
COLS = [("obl", "tg_before", "前: 斜め上から"), ("obl", "tg_after", "後: 斜め上から"),
        ("eye", "tg_before", "前: 床の目の高さ"), ("eye", "tg_after", "後: 床の目の高さ")]


def font(size):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, size)
        except OSError:
            continue
    return ImageFont.load_default()


def load(run, view, scene):
    f = ROOT / "run/shots" / run / "shots" / "telegraph_ground" / f"{view}_{scene}.png"
    if not f.exists():
        return Image.new("RGB", CELL, (60, 20, 20))
    return Image.open(f).convert("RGB").resize(CELL, Image.LANCZOS)


def main():
    f12, f10 = font(13), font(11)
    w = SIDE_W + CELL[0] * len(COLS)
    h = LABEL_H + len(SCENES) * CELL[1]
    img = Image.new("RGB", (w, h), (16, 16, 24))
    d = ImageDraw.Draw(img)
    for c, (_, _, label) in enumerate(COLS):
        d.text((SIDE_W + c * CELL[0] + 8, 4), label, fill=(235, 235, 235), font=f12)
    for r, (scene, title, note) in enumerate(SCENES):
        y = LABEL_H + r * CELL[1]
        d.text((8, y + 8), title, fill=(255, 220, 140), font=f12)
        # 説明は幅 SIDE_W - 16 で折り返す
        line, yy = "", y + 32
        for ch in note:
            if d.textlength(line + ch, font=f10) > SIDE_W - 16:
                d.text((8, yy), line, fill=(200, 200, 210), font=f10)
                yy += 14
                line = ch
            else:
                line += ch
        d.text((8, yy), line, fill=(200, 200, 210), font=f10)
        for c, (view, run, _) in enumerate(COLS):
            img.paste(load(run, view, scene), (SIDE_W + c * CELL[0], y))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT, img.size)


if __name__ == "__main__":
    main()
