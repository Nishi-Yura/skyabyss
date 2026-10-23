"""武器の品質の見直しの絵のまとめ (tools/gen/shots/gen_weapon_quality_shots.py の絵から)。
使い方: python tools/sheets/weapon_quality_sheet.py [撮影名 (既定 weapon_quality)] [出力のフォルダ (既定 docs/review/weapon_quality)]
出力: <出力>/melee_<n>.png (武器 7 本ずつ。列 = 一人称 構え / 連撃 1〜3 段 (0.25・0.5) / 点検 / 三人称 構え・横・1 段・2 段)、
      <出力>/shield_bows.png、<出力>/icons_inventory.png。絵は横 1/2 に縮める。"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
NAME = sys.argv[1] if len(sys.argv) > 1 else "weapon_quality"
D = ROOT / f"run/shots/{NAME}/shots/{NAME}"
if not D.exists():  # 撮ったあとで外の名前を変えたとき (例: weapon_quality_before) は中の唯一のフォルダ
    D = next((ROOT / f"run/shots/{NAME}/shots").iterdir())
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/review/weapon_quality"
sys.path.insert(0, str(ROOT / "tools/gen/shots"))
from gen_weapon_quality_shots import MELEE, BOWS  # noqa: E402

COLS = [("一人称 構え", "fpIdle"), ("1段 0.25", "fpS0_25"), ("1段 0.5", "fpS0_50"), ("2段 0.25", "fpS1_25"), ("2段 0.5", "fpS1_50"),
        ("3段 0.25", "fpS2_25"), ("3段 0.5", "fpS2_50"), ("点検", "fpInspect"), ("三人称 構え", "tpIdle"), ("三人称 横", "tpSide"),
        ("三人称 1段", "tpS0_50"), ("三人称 2段", "tpS1_50")]
W, H, LABEL = 240, 135, 170


def font(n):
    for f in (r"C:\Windows\Fonts\YuGothM.ttc", r"C:\Windows\Fonts\meiryo.ttc", r"C:\Windows\Fonts\msgothic.ttc"):
        try:
            return ImageFont.truetype(f, n)
        except OSError:
            pass
    return ImageFont.load_default()


def tile(name, view):
    p = D / f"{name}_{view}.png"
    if not p.exists():
        return Image.new("RGB", (W, H), (60, 30, 30))
    im = Image.open(p).convert("RGB")
    if view.startswith("tp"):  # 三人称は体のまわりを切り出す
        w, h = im.size
        im = im.crop((w * 0.25, h * 0.05, w * 0.75, h * 0.95))
    return im.resize((W, H), Image.LANCZOS)


def grid(rows, cols, path):
    f = font(15)
    S = Image.new("RGB", (LABEL + len(cols) * W, 26 + len(rows) * H), (24, 25, 30))
    d = ImageDraw.Draw(S)
    for j, (lab, _) in enumerate(cols):
        d.text((LABEL + j * W + 6, 4), lab, fill=(230, 230, 230), font=f)
    for i, (name, tier) in enumerate(rows):
        d.text((6, 26 + i * H + 8), name, fill=(240, 240, 240), font=f)
        d.text((6, 26 + i * H + 30), tier, fill=(170, 170, 190), font=f)
        for j, (_, view) in enumerate(cols):
            S.paste(tile(name, view), (LABEL + j * W, 26 + i * H))
    S.save(path)
    print(path, S.size)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for n in range(0, len(MELEE), 7):
        grid(MELEE[n:n + 7], COLS, OUT / f"melee_{n // 7 + 1}.png")
    grid([("coral_shield", "rare")] + BOWS, [("一人称 構え", "fpIdle"), ("三人称 構え", "tpIdle"), ("一人称 構え/引き", "fpGuard"), ("三人称 構え/引き", "tpGuard"),
                                          ("一人称 引き", "fpDraw2"), ("三人称 引き", "tpDraw2"), ("三人称 斜め", "tpGuardSide")], OUT / "shield_bows.png")
    inv = D / "icons_inventory.png"
    if inv.exists():
        im = Image.open(inv).convert("RGB")
        w, h = im.size
        im.crop((w // 2 - 290, h // 2 - 260, w // 2 + 290, h // 2 + 260)).save(OUT / "icons_inventory.png")


if __name__ == "__main__":
    main()
