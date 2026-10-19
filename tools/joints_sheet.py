"""tools/shots/joints.txt が撮った絵から、関節の確認シート docs/review/previews/joints_sheet.png を組む (日本語・控えめな色)。
各モデル: 休め / 動作の最大付近 (pose_a と pose_b のうち POSE で選ぶ。既定 a)。
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SHOTS = ROOT / "run/shots/joints/shots/joints"
OUT = ROOT / "docs/review/previews/joints_sheet.png"
NAMES = [("bone_sentinel", "骨の番兵", "a"), ("whalebone_captain", "鯨骨の隊長", "a"), ("static_moth", "静電の蛾", "a"),
         ("moss_crawler", "苔這い", "a"), ("waterway_maw", "水路の顎", "a"), ("elder_sentry", "古い歩哨", "a"),
         ("thunder_supplicant", "雷の請い人", "a"), ("elder_sentinel", "古い番兵", "a")]


def font(size):
    for n in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        try:
            return ImageFont.truetype(n, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main():
    over = dict(a.split("=") for a in sys.argv[1:])
    tw, th, cols = 320, 180, 2  # 一つのモデルで 休め + 最大 の 2 枚、横に 2 モデル
    rows = (len(NAMES) + cols - 1) // cols
    cw = tw * 2
    sheet = Image.new("RGB", (cols * cw, rows * (th + 22)), (34, 36, 40))
    d = ImageDraw.Draw(sheet)
    f = font(13)
    for i, (mid, jp, pose) in enumerate(NAMES):
        pose = over.get(mid, pose)
        x, y = (i % cols) * cw, (i // cols) * (th + 22)
        d.text((x + 6, y + 3), f"{jp}  左: 休め / 右: 動作の最大付近", font=f, fill=(214, 210, 200))
        for k, name in enumerate((f"j_{mid}_rest", f"j_{mid}_pose_{pose}")):
            p = SHOTS / f"{name}.png"
            if p.exists():
                sheet.paste(Image.open(p).convert("RGB").resize((tw, th)), (x + k * tw, y + 22))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(OUT)


main()
