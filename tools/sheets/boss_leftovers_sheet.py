"""巨神・心臓の残りを直した確かめの絵 (tools/shots/boss_leftovers.txt) をまとめる。
    python tools/sheets/boss_leftovers_sheet.py
入力 run/shots/boss_leftovers/ (下の階層も探す) → docs/review/boss_leftovers/ingame_sheet.png
"""
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "run", "shots", "boss_leftovers")
OUT = os.path.join(ROOT, "docs", "review", "boss_leftovers")

ROWS = [
    ("被弾のひるみ (待ち → 当てた 1 tick 後)", [("tide_idle", "潮殻 待ち"), ("tide_flinch", "潮殻 ひるみ"), ("storm_idle", "雷翼 待ち"),
                                           ("storm_flinch", "雷翼 ひるみ"), ("ember_flinch", "余燼 ひるみ"), ("whale_flinch", "岩鯨 ひるみ")]),
    ("技の溜めの途中で当てる (溜めの形のまま、ひるみが重なる)", [("tide_atk_flinch", "潮殻 真珠の溜め"), ("storm_atk_flinch", "雷翼 刃の溜め"),
                                                ("ember_atk_flinch", "余燼 脈の溜め"), ("whale_atk_flinch", "岩鯨 突きの溜め"),
                                                ("w_flinch", "番人 ひるみ"), ("w_claw_flinch", "番人 爪の溜め")]),
    ("段階 2・3 のアトラス 256 / 羽の雨 (人の目の高さ)", [("tide_p2", "潮殻 段階 2"), ("tide_p3", "潮殻 段階 3"), ("storm_p2", "雷翼 段階 2"),
                                             ("feather_fall", "羽の雨 降る"), ("feather_hit", "羽の雨 当たり"), ("feather_after", "羽の雨 あと")]),
    ("岩鯨の子 (作り直し) / 灰の王 手下を呼ぶ", [("young_whale_front", "岩鯨の子 前"), ("young_whale_side", "岩鯨の子 横"), ("young_whale_back", "岩鯨の子 後ろ"),
                                       ("king_ash_idle", "灰の王 待ち"), ("king_ash_summon", "灰の王 呼ぶ溜め"), ("king_ash_summon_34", "灰の王 呼ぶ溜め (斜め)")]),
]
TW, TH = 480, 270


def font(n):
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if os.path.exists(f):
            return ImageFont.truetype(f, n)
    return ImageFont.load_default()


def find_all():
    out = {}
    for d, _, fs in os.walk(SRC):
        for f in fs:
            if f.endswith(".png"):
                out[f[:-4]] = os.path.join(d, f)
    return out


def main():
    shots = find_all()
    W = 20 + 6 * (TW + 10)
    H = 60 + len(ROWS) * (TH + 70)
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 14), "巨神・心臓の残り (ゲームの絵・音なし・日本語)", font=font(28), fill=(236, 232, 218))
    y = 60
    for title, items in ROWS:
        d.text((20, y), title, font=font(22), fill=(150, 232, 255))
        for i, (name, lab) in enumerate(items):
            x = 20 + i * (TW + 10)
            if name in shots:
                im = Image.open(shots[name]).convert("RGB").resize((TW, TH), Image.LANCZOS)
                cv.paste(im, (x, y + 34))
            else:
                d.rectangle((x, y + 34, x + TW, y + 34 + TH), outline=(120, 60, 60))
            d.rectangle((x + 4, y + 38, x + 12 + 15 * len(lab), y + 64), fill=(0, 0, 0))
            d.text((x + 8, y + 39), lab, font=font(18), fill=(255, 230, 160))
        y += TH + 70
    os.makedirs(OUT, exist_ok=True)
    cv.save(os.path.join(OUT, "ingame_sheet.png"))
    print("wrote", os.path.join(OUT, "ingame_sheet.png"), "shots found:", len(shots))


if __name__ == "__main__":
    main()
