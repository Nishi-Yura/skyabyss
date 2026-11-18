"""光の表現 (2026-11-15) の清書の確かめ: 選んだラフ (docs/review/light_fx/rough/) と清書 (撮り直した絵) を左右に並べる。

使い方: python -I tools/sheets/light_fx_sheet.py
入力: docs/review/light_fx/rough/*.png (選んだ案のラフ) と run/shots/light_fx/shots/light_fx/*_f*.png
      (python -I tools/gen/shots/gen_light_fx_shots.py → ./gradlew runShots -Pscript=tools/shots/light_fx.txt)
出力: docs/review/light_fx/final.png (左 = ラフ、右 = 清書) と final_themes.png (清書の巨神ごとの裂け目)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "run/shots/light_fx/shots/light_fx"
ROUGH = ROOT / "docs/review/light_fx/rough"
OUT = ROOT / "docs/review/light_fx"
CELL = (560, 315)
LABEL_H = 24
THEME_JA = {"stone_whale": "岩鯨", "tide_shell": "潮殻", "thunder_wing": "雷翼", "ember": "余燼", "abyss": "深淵", "moon": "月"}


def font(size):
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if Path(f).exists():
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def load(path):
    if not path.exists():
        return None
    return Image.open(path).convert("RGB").resize(CELL, Image.LANCZOS)


def sheet(out, rows, cols):
    fnt = font(14)
    img = Image.new("RGB", (CELL[0] * len(cols), (CELL[1] + LABEL_H) * len(rows)), (14, 14, 20))
    d = ImageDraw.Draw(img)
    for r, (label, paths) in enumerate(rows):
        for c, col in enumerate(cols):
            x = c * CELL[0]
            y = r * (CELL[1] + LABEL_H)
            d.text((x + 8, y + 4), f"{col} / {label}", fill=(235, 235, 235), font=fnt)
            im = load(paths[c]) if c < len(paths) else None
            if im is None:
                d.rectangle((x, y + LABEL_H, x + CELL[0], y + LABEL_H + CELL[1]), fill=(50, 18, 18))
                d.text((x + 12, y + LABEL_H + 12), "(撮れていない)", fill=(220, 160, 160), font=fnt)
            else:
                img.paste(im, (x, y + LABEL_H))
    img.save(OUT / out)
    print(OUT / out, img.size)


def main():
    pairs = [
        ("裂け目 岩鯨", "rift_stone_whale_b", "rift_stone_whale_f"),
        ("裂け目 雷翼", "rift_thunder_wing_b", "rift_thunder_wing_f"),
        ("渡る裂け目 太る", "pass_stone_whale_b_19", "pass_stone_whale_f_19"),
        ("渡る裂け目 閉じる", "pass_stone_whale_b_47", "pass_stone_whale_f_47"),
        ("当たりの輪", "ring_b_early", "ring_f_early"),
        ("当たりの輪 広がる", "ring_b_mid", "ring_f_mid"),
        ("核の光 岩鯨", "core_whale_b", "core_whale_f"),
        ("溜め 岩鯨", "whale_thrust_gather_a", "whale_thrust_gather_f"),
        ("光線 余燼", "ember_breath_fire_a", "ember_breath_fire_f"),
        ("軌道", "orbit_c_03", "orbit_f_03"),
    ]
    sheet("final.png", [(l, [ROUGH / f"{a}.png", SHOTS / f"{b}.png"]) for l, a, b in pairs], ["ラフ", "清書"])
    rows = [(THEME_JA[t], [SHOTS / f"rift_{t}_f.png", SHOTS / f"pass_{t}_f_{32 if t != 'stone_whale' else 32:02d}.png"]) for t in THEME_JA]
    sheet("final_themes.png", rows, ["場の裂け目", "渡る裂け目の中"])


if __name__ == "__main__":
    main()
