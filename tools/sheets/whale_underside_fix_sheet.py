"""tools/shots/whale_underside_fix.txt が撮った絵から、確認用の一枚を docs/review/previews/ に組む。

使い方: ./gradlew runShots -Pscript=tools/shots/whale_underside_fix.txt のあと、python -I tools/sheets/whale_underside_fix_sheet.py
出力: docs/review/previews/whale_underside_fix_sheet.png (日本語の見出しつき。音なし)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "run/shots/whale_underside_fix/shots/whale_underside_fix"
OUT = ROOT / "docs/review/previews/whale_underside_fix_sheet.png"

TILES = [
    ("blocks_day", "ブロックの見え方 (昼): 台(まだ)・台(ともした)・苗床・数え石 0・2"),
    ("blocks_night", "同じ、夜 (台のまだ=光 5、ともした=光 15)"),
    ("entry_wide", "入口の休み場 (13×9×4。広くした)"),
    ("entry_board_unlit", "灯の掲示板と数え石: まだ 0 / 3"),
    ("entry_beds_hatch", "苗床 3 つと、水への入口 (床の穴の両脇に光)"),
    ("entry_board_lit1", "灯 1 / 3 (掲示板の左が灯る)"),
    ("entry_board_lit3", "灯 3 / 3 (掲示板が全部灯る)"),
    ("pocket_close", "息継ぎのくぼみ (泡の柱・水域の色の光)"),
    ("pocket_close_b", "別の水域のくぼみ (光の色が違う)"),
    ("stand_unlit_close", "灯の台 (まだ。通路の先から見える)"),
    ("stand_lit_close", "ともした台 (光・天井のランタン)"),
    ("trail_one_lamp", "灯 1 つ: 台から先へ光の道がのびる"),
    ("zone0", "水域 0 (入口の水域)"),
    ("zone1", "水域 1 (台 1 を越えた先)"),
    ("zone2", "水域 2 (台 2 を越えた先)"),
    ("zone3", "水域 3 (出口の水域)"),
    ("cut_closed", "近道の戸 (灯が足りない間は壁)"),
    ("cut_open", "近道の戸 (灯がそろうと開く)"),
    ("trail_full_a", "三つともった: 光の道が出口まで (手前)"),
    ("trail_full_b", "三つともった: 光の道が出口まで (先)"),
    ("exit_gate_open", "出口の戸が開いた (光の道が泡の柱へ)"),
    ("exit_gate_closed", "出口の戸 (まだ。同じ場所)"),
    ("band_carry", "目的の帯: 灯 0 / 3・灯の台へ ↑ 道のり"),
    ("band_two_lit", "目的の帯: 灯が 2 つともったあと"),
]


def font(size):
    for name in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main():
    w, h, label = 640, 360, 34
    cols = 3
    tiles = [(k, t) for k, t in TILES if (SHOTS / f"{k}.png").exists()]
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (w * cols, (h + label) * rows), (18, 22, 28))
    d = ImageDraw.Draw(sheet)
    f = font(20)
    for i, (key, title) in enumerate(tiles):
        im = Image.open(SHOTS / f"{key}.png").convert("RGB").resize((w, h), Image.LANCZOS)
        x, y = (i % cols) * w, (i // cols) * (h + label)
        sheet.paste(im, (x, y + label))
        d.text((x + 8, y + 5), title, fill=(220, 232, 236), font=f)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("wrote", OUT, len(tiles), "tiles")
    missing = [k for k, _ in TILES if not (SHOTS / f"{k}.png").exists()]
    if missing:
        print("missing:", missing)


if __name__ == "__main__":
    main()
