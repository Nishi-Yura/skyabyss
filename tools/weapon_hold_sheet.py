"""武器の持ち姿のまとめ (日本語)。tools/gen_weapon_hold_shots.py の絵から docs/review/previews/weapon_hold_sheet.png を作る。
列: 一人称 / 三人称 (正面) / 三人称 (斜め) / 伝説の一人称 / 伝説の三人称 / GUI。深淵の武器は伝説のみ。"""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
D = ROOT / "run/shots/weapon_hold/shots/weapon_hold"
TEX = ROOT / "src/main/resources/assets/skyabyss/textures/item"
LANG = json.loads((ROOT / "src/main/resources/assets/skyabyss/lang/ja_jp.json").read_text(encoding="utf-8"))
ITEMS = ["whalebone_greatsword", "captain_pike", "waterway_fang", "tide_shell_trident", "coral_harpoon", "tidecaller_claw", "coral_shield",
         "thunder_wing_bow", "warden_glaive", "raiju_claw", "ember_war_axe", "smith_hammer", "thrall_poker",
         "abyss_greatsword", "abyss_ash_scythe", "abyss_bell_staff", "abyss_storm_bow"]
COLS = [("一人称", "common", "fpR"), ("三人称 正面", "common", "tpR"), ("三人称 斜め", "common", "tpSide"), ("伝説 一人称", "legend", "fpR"),
        ("伝説 三人称", "legend", "tpR"), ("GUI", None, None)]
W, H, LABEL = 300, 240, 150


def font(n):
    for f in (r"C:\Windows\Fonts\YuGothM.ttc", r"C:\Windows\Fonts\meiryo.ttc", r"C:\Windows\Fonts\msgothic.ttc"):
        try:
            return ImageFont.truetype(f, n)
        except OSError:
            pass
    return ImageFont.load_default()


def crop(img, view):
    if view.startswith("tp"):
        return img.crop((380, 120, 680, 390)).resize((W, H))
    return img.crop((440, 150, 960, 540)).resize((W, H))


def main():
    f, fs = font(16), font(14)
    sheet = Image.new("RGB", (LABEL + W * len(COLS), 36 + H * len(ITEMS)), (24, 26, 32))
    d = ImageDraw.Draw(sheet)
    for c, (name, _, _) in enumerate(COLS):
        d.text((LABEL + c * W + 8, 8), name, font=f, fill=(255, 255, 255))
    for r, it in enumerate(ITEMS):
        y = 36 + r * H
        d.text((8, y + 8), LANG.get(f"item.skyabyss.{it}", it), font=f, fill=(255, 230, 160))
        d.text((8, y + 32), it, font=fs, fill=(150, 150, 160))
        for c, (_, tier, view) in enumerate(COLS):
            x = LABEL + c * W
            if tier is None:
                p = TEX / f"{it}.png"
                if p.exists():
                    ic = Image.open(p).convert("RGBA")
                    ic = ic.resize((H - 40, H - 40), Image.NEAREST)
                    sheet.paste(ic, (x + 30, y + 20), ic)
                continue
            t = "legend" if it.startswith("abyss_") else tier
            if it.startswith("abyss_") and tier == "common":
                continue
            p = D / f"hold_{it}_{t}_{view}.png"
            if p.exists():
                sheet.paste(crop(Image.open(p).convert("RGB"), view), (x, y))
    out = ROOT / "docs/review/previews/weapon_hold_sheet.png"
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main()
