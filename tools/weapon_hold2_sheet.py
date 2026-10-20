"""持ち方の見直しの絵のまとめ。tools/gen_weapon_hold2_shots.py の絵から docs/review/previews/weapon_hold2_sheet.png を作る。
使い方: python tools/weapon_hold2_sheet.py [出力のパス]   (大きさの順は、同じ縮尺の伝説の三人称の列で見る)"""
import json
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
D = ROOT / "run/shots/weapon_hold2/shots/weapon_hold2"
LANG = json.loads((ROOT / "src/main/resources/assets/skyabyss/lang/ja_jp.json").read_text(encoding="utf-8"))
ITEMS = ["waterway_fang", "raiju_claw", "tidecaller_claw", "whalebone_greatsword", "captain_pike", "tide_shell_trident", "coral_harpoon",
         "warden_glaive", "thrall_poker", "ember_war_axe", "smith_hammer"]
COLS = [("一人称 構え", "common", "fpIdle"), ("一人称 振り", "common", "fpSwing"), ("三人称 構え", "common", "tpIdle"), ("三人称 斜め", "common", "tpSide"),
        ("三人称 振り", "common", "tpSwing"), ("伝説 三人称", "legend", "tpIdle")]
W, H, LABEL = 300, 240, 150


def font(n):
    for f in (r"C:\Windows\Fonts\YuGothM.ttc", r"C:\Windows\Fonts\meiryo.ttc", r"C:\Windows\Fonts\msgothic.ttc"):
        try:
            return ImageFont.truetype(f, n)
        except OSError:
            pass
    return ImageFont.load_default()


def crop(img, view, item=''):
    if item == 'coral_shield' and view.startswith('fp'):  # 盾の一人称は左寄りなので全体を縮める
        return img.resize((W, H))
    if view.startswith("tp"):
        return img.crop((360, 110, 680, 370)).resize((W, int(W * 260 / 320)))
    return img.crop((440, 150, 960, 540)).resize((W, H))


def grid(rows, cols, label_of, path_of, out):
    f, fs = font(16), font(14)
    sheet = Image.new("RGB", (LABEL + W * len(cols), 36 + H * len(rows)), (24, 26, 32))
    d = ImageDraw.Draw(sheet)
    for c, (name, _, _) in enumerate(cols):
        d.text((LABEL + c * W + 8, 8), name, font=f, fill=(255, 255, 255))
    for r, it in enumerate(rows):
        y = 36 + r * H
        d.text((8, y + 8), label_of(it), font=f, fill=(255, 230, 160))
        d.text((8, y + 32), it, font=fs, fill=(150, 150, 160))
        for c, (_, tier, view) in enumerate(cols):
            p = path_of(it, tier, view)
            if p.exists():
                sheet.paste(crop(Image.open(p).convert("RGB"), view, it), (LABEL + c * W, y))
    return sheet


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/review/previews/weapon_hold2_sheet.png"
    lab = lambda it: LANG.get(f"item.skyabyss.{it}", it)
    a = grid(ITEMS, COLS, lab, lambda it, t, v: D / f"{it}_{t}_{v}.png", out)
    # 盾の構え・弓の引き
    cols2 = [("一人称", "", "fp"), ("三人称 正面", "", "tp"), ("三人称 斜め", "", "tpSide")]
    shield = grid(["coral_shield"], [("一人称 構え", "common", "fpIdle"), ("一人称 防ぐ", "common", "fpGuard"), ("三人称 防ぐ", "common", "tpGuard"),
                                     ("三人称 斜め", "common", "tpGuardSide"), ("三人称 構え", "common", "tpIdle")], lab, lambda it, t, v: D / f"{it}_{t}_{v}.png", out)
    bcols = [("引き 序 (5t)", "", "Draw0"), ("引き 中 (15t)", "", "Draw1"), ("引き 満 (26t)", "", "Draw2"), ("放つ (満)", "", "Release2b")]
    # 大きさの比べ: 三人称を横から、同じ距離・同じ切り出しで (伝説 = いちばん大きい階級)
    f = font(16)
    strip = Image.new("RGB", (1920, 2 * (192 + 26) + 30), (24, 26, 32))
    d = ImageDraw.Draw(strip)
    d.text((8, 4), "大きさの比べ (三人称 横・伝説・同じ距離): 短剣 < 槍・斧 < 大剣", font=f, fill=(255, 255, 255))
    for n, it in enumerate(ITEMS):
        x, y = (n % 6) * 320, 30 + (n // 6) * 218
        p = D / f"{it}_legend_tpSide.png"
        if p.exists():
            strip.paste(Image.open(p).convert("RGB").crop((200, 90, 700, 390)).resize((320, 192)), (x, y))
        d.text((x + 6, y + 194), lab(it), font=f, fill=(255, 230, 160))
    parts = [a, shield, strip]
    for tag, name in (("fp", "弓 一人称"), ("tp", "弓 三人称 (放つのは背中側から)")):
        parts.append(grid([f"thunder_wing_bow"], [(n, t, f"{tag}{v}") for n, t, v in (bcols if tag == "fp" else bcols[:3] + [("放つ (満)", "", "Release2b")])], lambda it, nm=name: nm, lambda it, t, v: D / f"{it}_{v}.png", out))
    w = max(p.width for p in parts)
    sheet = Image.new("RGB", (w, sum(p.height for p in parts)), (24, 26, 32))
    y = 0
    for p in parts:
        sheet.paste(p, (0, y))
        y += p.height
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main()
