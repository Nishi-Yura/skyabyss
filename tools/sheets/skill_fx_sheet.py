from __future__ import annotations
"""技の見た目 (撮影台本 tools/shots/skill_fx.txt) から、技ごとのコンタクトシートを作る。

使い方 (リポジトリ直下で):  python tools/sheets/skill_fx_sheet.py [--night]  (--night で夜のシートを作る)
入力: run/shots/skill_fx/ 以下の <技>_3p_{b,c,d}.png と <技>_1p_c.png (撮影の名前は tools/gen/shots/gen_skill_fx_shots.py が決める)
出力: docs/review/previews/skill_fx_sheet.png (docs/review/previews は git 管理外。追跡は git add -f)
行 = 技 (25)。左から 三人称の放ち | 三人称の当たり | 三人称の残り | 一人称の当たり。
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/previews/skill_fx_sheet.png"
OUT_NIGHT = ROOT / "docs/review/previews/skill_fx_night_sheet.png"
SHOTS = ROOT / "run/shots/skill_fx"
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/msgothic.ttc",
         "/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf"]

# 技 id, 表示名
SKILLS = [
    ("titan_cleave", "巨神斬り"), ("rock_call", "岩呼び"), ("whale_sweep", "薙ぎ払い"), ("lunge", "突進"),
    ("backstep", "跳び退き"), ("wind_rend", "二連の斬り"), ("ward_song", "守りの歌"), ("ebb_tide", "引き潮"),
    ("cycle_song", "循環の歌"), ("temper", "武器を熾す"), ("ember_hammer", "熾の打ち下ろし"), ("ash_scatter", "灰を被せる"),
    ("cloud_pierce", "雲穿ち"), ("bone_trap", "骨の罠"), ("sky_rain", "天の驟雨"), ("signal", "合図の標"),
    ("dust_charge", "砂の突進"), ("steam_bell", "蒸気の鐘"), ("cinder_volley", "火の矢の一斉"), ("deep_pull", "淵の引き"),
    ("deep_echo", "淵の残響"), ("deep_maw", "淵の顎"), ("echo_strike", "遺響の一撃"), ("grave_bell", "墓鐘"),
    ("final_light", "最期の光"),
]
# 列: (名の末尾, 見出し)
COLS = [("3p_b", "三人称 放ち"), ("3p_c", "三人称 当たり"), ("3p_d", "三人称 残り"), ("1p_c", "一人称 当たり")]
# 夜の列 (台本で夜は放ちと当たりだけ撮る)
COLS_NIGHT = [("night_3p_b", "夜 三人称 放ち"), ("night_3p_c", "夜 三人称 当たり"), ("night_1p_c", "夜 一人称 当たり")]
CELL_W, CELL_H = 336, 189
LABEL_W = 210
HEAD_H = 40


def font(size: int):
    for f in FONTS:
        if Path(f).exists():
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def find(name: str):
    # 撮影の出力は run/shots/skill_fx/ の直下か、一段下にある
    for p in (SHOTS / f"{name}.png", *SHOTS.rglob(f"{name}.png")):
        if p.exists():
            return p
    return None


def cell(img_path: Path | None) -> Image.Image:
    box = Image.new("RGB", (CELL_W, CELL_H), (30, 30, 34))
    if img_path is None:
        d = ImageDraw.Draw(box)
        d.text((12, CELL_H // 2 - 8), "(撮れていない)", fill=(255, 120, 120), font=font(16))
        return box
    im = Image.open(img_path).convert("RGB")
    im.thumbnail((CELL_W - 2, CELL_H - 2))
    box.paste(im, ((CELL_W - im.width) // 2, (CELL_H - im.height) // 2))
    return box


def main() -> int:
    night = "--night" in sys.argv
    cols = COLS_NIGHT if night else COLS
    out = OUT_NIGHT if night else OUT
    OUT.parent.mkdir(parents=True, exist_ok=True)
    W = LABEL_W + CELL_W * len(cols) + 8
    H = HEAD_H + CELL_H * len(SKILLS) + 8
    sheet = Image.new("RGB", (W, H), (18, 18, 22))
    d = ImageDraw.Draw(sheet)
    fh = font(18)
    fl = font(20)
    for i, (_, head) in enumerate(cols):
        d.text((LABEL_W + i * CELL_W + 8, 10), head, fill=(230, 230, 230), font=fh)
    missing = 0
    for r, (skill, label) in enumerate(SKILLS):
        y = HEAD_H + r * CELL_H
        d.text((10, y + CELL_H // 2 - 14), f"{label}", fill=(240, 240, 240), font=fl)
        d.text((10, y + CELL_H // 2 + 10), skill, fill=(150, 150, 160), font=font(13))
        for c, (suffix, _) in enumerate(cols):
            p = find(f"{skill}_{suffix}")
            if p is None:
                missing += 1
            sheet.paste(cell(p), (LABEL_W + c * CELL_W, y))
    sheet.save(out)
    print("wrote", out, "missing cells", missing)
    return 0 if missing == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
