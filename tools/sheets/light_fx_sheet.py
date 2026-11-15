"""光の表現 (2026-11-15) の A/B/C を見比べる台紙を docs/review/light_fx/ に作る。

使い方: python -I tools/sheets/light_fx_sheet.py
入力: run/shots/light_fx/shots/light_fx/*.png (./gradlew runShots -Pscript=tools/shots/light_fx.txt)
出力: 部分ごとに 1 枚 (列 = 案 A/B/C、行 = 巨神や時刻)。選定ページはこれと元の絵を使う。
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "run/shots/light_fx/shots/light_fx"
OUT = ROOT / "docs/review/light_fx"
CELL = (560, 315)
LABEL_H = 24
STYLES = ["a", "b", "c"]
THEME_JA = {"stone_whale": "岩鯨", "tide_shell": "潮殻", "thunder_wing": "雷翼", "ember": "余燼", "abyss": "深淵", "moon": "月"}


def font(size):
    for f in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if Path(f).exists():
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def load(name):
    f = SHOTS / f"{name}.png"
    if not f.exists():
        return None
    return Image.open(f).convert("RGB").resize(CELL, Image.LANCZOS)


def sheet(out, rows, cols):
    """rows: [(行の名, 列ごとの絵の名の関数)], cols: 列の名"""
    fnt = font(14)
    img = Image.new("RGB", (CELL[0] * len(cols), (CELL[1] + LABEL_H) * len(rows)), (14, 14, 20))
    d = ImageDraw.Draw(img)
    for r, (rlabel, namer) in enumerate(rows):
        for c, col in enumerate(cols):
            x = c * CELL[0]
            y = r * (CELL[1] + LABEL_H)
            d.text((x + 8, y + 4), f"{col} / {rlabel}", fill=(235, 235, 235), font=fnt)
            im = load(namer(c))
            if im is None:
                d.rectangle((x, y + LABEL_H, x + CELL[0], y + LABEL_H + CELL[1]), fill=(50, 18, 18))
                d.text((x + 12, y + LABEL_H + 12), "(撮れていない)", fill=(220, 160, 160), font=fnt)
            else:
                img.paste(im, (x, y + LABEL_H))
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / out)
    print(OUT / out, img.size)


def main():
    cols = ["案 A", "案 B", "案 C"]
    # 1. 場の裂け目
    rows = [(f"{THEME_JA[t]}", (lambda c, t=t: f"rift_{t}_{STYLES[c]}")) for t in THEME_JA]
    rows.insert(0, ("開き始め", lambda c: f"rift_open_{STYLES[c]}"))
    rows.append(("広がって溶ける", lambda c: f"rift_burst_{STYLES[c]}"))
    sheet("rift_world.png", rows, cols)
    # 2. 渡る裂け目 (岩鯨、5 つの時刻)
    rows = [(f"{age} tick", (lambda c, age=age: f"pass_stone_whale_{STYLES[c]}_{age:02d}")) for age in (12, 19, 24, 32, 47)]
    sheet("rift_pass.png", rows, cols)
    # 2b. 渡る裂け目の巨神ごとの色 (案 A)
    rows = [(f"{THEME_JA[t]}", (lambda c, t=t: f"pass_{t}_a_{[19, 32][c]:02d}")) for t in THEME_JA if t != "stone_whale"]
    sheet("rift_pass_themes.png", rows, ["開いた所 (A)", "覆った中 (A)"])
    # 3. 当たりの輪
    rows = [("当たってすぐ", lambda c: f"ring_{STYLES[c]}_early"), ("広がる", lambda c: f"ring_{STYLES[c]}_mid")]
    sheet("ring.png", rows, cols)
    # 4. 心臓
    rows = [
        ("岩鯨 核の光", lambda c: f"core_whale_{STYLES[c]}"),
        ("岩鯨 突き 溜め", lambda c: f"whale_thrust_gather_{STYLES[c]}"),
        ("岩鯨 突き 光線", lambda c: f"whale_thrust_fire_{STYLES[c]}"),
        ("岩鯨 突き 当たり", lambda c: f"whale_thrust_after_{STYLES[c]}"),
        ("岩鯨 薙ぎ 溜め", lambda c: f"whale_beam_gather_{STYLES[c]}"),
        ("岩鯨 薙ぎ 最中", lambda c: f"whale_beam_fire_{STYLES[c]}"),
        ("余燼 核の光", lambda c: f"core_ember_{STYLES[c]}"),
        ("余燼 息 溜め", lambda c: f"ember_breath_gather_{STYLES[c]}"),
        ("余燼 息 光線", lambda c: f"ember_breath_fire_{STYLES[c]}"),
    ]
    sheet("hearts.png", rows, cols)
    # 5. 領域
    rows = [("開く / 太る / 溶ける", lambda c: ["domain_open", "domain_wide", "domain_burst"][c])]
    sheet("domain.png", rows, ["開く", "太る", "溶ける"])
    # 6. 軌道
    rows = [(f"{i * 12 + 12} tick", (lambda c, i=i: f"orbit_{STYLES[c]}_{i:02d}")) for i in range(10)]
    sheet("orbit.png", rows, cols)


if __name__ == "__main__":
    main()
