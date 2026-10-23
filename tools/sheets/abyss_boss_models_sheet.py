"""層の主・歪んだ王の撮影 (tools/shots/abyss_boss_models.txt) をまとめる。
    python tools/sheets/abyss_boss_models_sheet.py
入力 run/shots/abyss_boss_models/shots/abyss_boss_models/*.png → docs/review/abyss_boss_models/ (縮めた絵 + sheet_lords.png・sheet_kings.png)
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "run", "shots", "abyss_boss_models", "shots", "abyss_boss_models")
OUT = os.path.join(ROOT, "docs", "review", "abyss_boss_models")

JA = {"lord_rib": "肋の主 (肋の檻の灯守)", "lord_salt": "白殻の主 (巻貝の巨殻)", "lord_storm": "鼓動まねの主 (心臓むき出しの袋)", "lord_ash": "灯り喰いの主 (灯の山を吊る黒い口)",
      "king_bone": "骨の王 (肋の檻の王)", "king_salt": "塩の王 (結晶の冠の王)", "king_storm": "雷の王 (避雷針の林の王)", "king_ash": "灰の王 (灯火の冠の僧)",
      "king_shadow": "影の王 (這いずる影)"}
ACT = {"idle": "待機", "snuff": "吹き消しの溜め", "crush": "押しつぶしの溜め", "phase": "段の変わり目", "roar": "三度吠える溜め", "slam": "叩きつけの溜め",
       "charge": "突進の溜め", "bolts": "落ちる光の溜め", "summon": "手下を呼ぶ溜め", "sweep": "衝撃の輪の溜め"}


def font(n):
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if os.path.exists(f):
            return ImageFont.truetype(f, n)
    return ImageFont.load_default()


def crop(im):
    w, h = im.size
    return im.crop((w // 2 - h * 0.5, 0, w // 2 + h * 0.5, h)).resize((420, 420), Image.LANCZOS)


def sheet(names, out, title):
    files = sorted(os.listdir(SRC))
    rows = []
    for n in names:
        fs = [f for f in files if f.startswith(n + "_c") and f.endswith(".png")]
        order = sorted(fs, key=lambda f: (f.split("_c")[1][0], 0 if f.endswith("_idle.png") else 1))
        rows.append((n, order))
    cols = max(len(r[1]) for r in rows)
    W = 20 + cols * 430
    H = 70 + len(rows) * (420 + 46)
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 16), title, font=font(30), fill=(236, 232, 218))
    y = 70
    for n, fs in rows:
        d.text((20, y), JA[n], font=font(24), fill=(150, 232, 255))
        for i, f in enumerate(fs):
            im = crop(Image.open(os.path.join(SRC, f)).convert("RGB"))
            cv.paste(im, (20 + i * 430, y + 34))
            cyc = f.split("_c")[1][0]
            act = f[:-4].split("_")[-1]
            lab = ("周 %s " % cyc if n.startswith("king") else "") + ACT.get(act, act)
            d.rectangle((24 + i * 430, y + 38, 30 + i * 430 + 14 * len(lab) + 40, y + 68), fill=(0, 0, 0))
            d.text((30 + i * 430, y + 40), lab, font=font(20), fill=(255, 230, 160))
        y += 420 + 46
    cv.save(os.path.join(OUT, out))


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(SRC):
        if f.endswith(".png"):
            im = Image.open(os.path.join(SRC, f)).convert("RGB")
            im.resize((640, 360), Image.LANCZOS).save(os.path.join(OUT, f))
    sheet(["lord_rib", "lord_salt", "lord_storm", "lord_ash"], "sheet_lords.png", "層の主 4 体 (待機・技の溜め)")
    sheet(["king_bone", "king_salt", "king_storm", "king_ash", "king_shadow"], "sheet_kings.png", "歪んだ王 5 型 (周 0・1・2、待機・技の溜め)")
    print("ok")


if __name__ == "__main__":
    main()
