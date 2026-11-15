"""攻撃の見せ方の見本 (岩鯨の心臓) の台紙。

使い方: python -I tools/sheets/attack_pilot_sheet.py
入力: run/shots/attack_pilot_<a|b|c>/shots/attack_pilot/<技>_<1_charge|2_hit|3_after>.png
出力: docs/review/attack_pilot/hits.png (技ごとの当たりの瞬間を 案 A/B/C で横に)、
      docs/review/attack_pilot/<a|b|c>.png (一つの案の 技 x 溜め・当たり・あと)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/attack_pilot"
CELL = (480, 270)
LABEL_H = 22
MOVES = [("thrust", "突き"), ("spike", "骨の棘"), ("leap", "跳躍"), ("crack", "亀裂"), ("volley", "投射"), ("beam", "光線の薙ぎ"),
         ("chain", "鎖"), ("shell_drop", "殻の落下"), ("roar", "咆哮")]
PHASES = [("1_charge", "溜め"), ("2_hit", "当たり"), ("3_after", "あと")]


def font(size):
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc"):
        if Path(f).exists():
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def shot(style, name):
    p = ROOT / f"run/shots/attack_pilot_{style}/shots/attack_pilot/{name}.png"
    return Image.open(p).convert("RGB").resize(CELL, Image.LANCZOS) if p.exists() else None


def grid(out, rows, cols, get):
    fnt = font(13)
    img = Image.new("RGB", (CELL[0] * len(cols), (CELL[1] + LABEL_H) * len(rows)), (14, 14, 20))
    d = ImageDraw.Draw(img)
    for r, rl in enumerate(rows):
        for c, cl in enumerate(cols):
            x, y = c * CELL[0], r * (CELL[1] + LABEL_H)
            d.text((x + 6, y + 3), f"{cl} / {rl[1]}", fill=(230, 230, 230), font=fnt)
            im = get(rl, cl, c)
            if im is None:
                d.rectangle((x, y + LABEL_H, x + CELL[0], y + LABEL_H + CELL[1]), fill=(50, 18, 18))
            else:
                img.paste(im, (x, y + LABEL_H))
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / out)
    print(OUT / out, img.size)


def main():
    styles = ["a", "b", "c"]
    grid("hits.png", MOVES, ["案 A", "案 B", "案 C"], lambda rl, cl, c: shot(styles[c], f"{rl[0]}_2_hit"))
    for s in styles:
        grid(f"{s}.png", MOVES, [p[1] for p in PHASES], lambda rl, cl, c, s=s: shot(s, f"{rl[0]}_{PHASES[c][0]}"))


if __name__ == "__main__":
    main()
