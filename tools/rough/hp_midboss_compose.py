"""Side-by-side before/after of the in-game shots (tools/shots/hp_midboss.txt). python tools/rough/hp_midboss_compose.py
-> docs/review/high_poly_roughs/ingame/midboss_<name>.png (left = before, right = after)"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
B = ROOT / "run/shots/hp_midboss_before/shots/hp_midboss"
A = ROOT / "run/shots/hp_midboss_after/shots/hp_midboss"
OUT = ROOT / "docs/review/high_poly_roughs/ingame"
OUT.mkdir(parents=True, exist_ok=True)
MODELS = {
    "captain": ["front", "three_quarter", "face", "hands"],
    "beckoner": ["front", "three_quarter", "claw", "top"],
    "raiju": ["front", "three_quarter", "face", "back"],
    "smith": ["front", "three_quarter", "face", "back"],
}
W, H = 800, 450
for name, views in MODELS.items():
    sheet = Image.new("RGB", (W * 2 + 30, (H + 24) * len(views) + 30), (16, 16, 24))
    d = ImageDraw.Draw(sheet)
    for i, v in enumerate(views):
        y = 24 + i * (H + 24)
        for j, (src, label) in enumerate(((B, "before"), (A, "after"))):
            im = Image.open(src / f"{name}_{v}.png").convert("RGB").resize((W, H), Image.LANCZOS)
            sheet.paste(im, (10 + j * (W + 10), y))
            d.text((14 + j * (W + 10), y - 14), f"{name} {v} - {label}", fill=(160, 220, 230))
    sheet.save(OUT / f"midboss_{name}.png")
    print("wrote", name)
im = Image.open(A / "all_four.png").convert("RGB")
im.save(OUT / "midboss_all_four_after.png")
Image.open(B / "all_four.png").convert("RGB").save(OUT / "midboss_all_four_before.png")
