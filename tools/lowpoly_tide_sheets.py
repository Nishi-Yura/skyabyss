#!/usr/bin/env python3
"""Copy the tide low-poly shots from run/shots/lp_tide_<name>/shots/lp_tide_<name>/ to docs/review/previews/ and build one contact sheet per model
(docs/review/previews/lp_tide_<name>_sheet.png: mesh row over cube row, same pose in the same column).
    python tools/lowpoly_tide_sheets.py"""
import glob
import os
import shutil

from PIL import Image, ImageDraw

OUT = os.path.join("docs", "review", "previews")
W, H = 427, 240

MODELS = {
    "scuttler": ["scuttler_idle_front", "scuttler_idle_three_quarter", "scuttler_idle_side", "scuttler_idle_back", "scuttler_attack_06", "scuttler_attack_12"],
    "puffer": ["puffer_idle_front", "puffer_idle_three_quarter", "puffer_idle_side", "puffer_inflated_a", "puffer_inflated_front", "puffer_inflated_top"],
    "beckoner": ["beckoner_idle_front", "beckoner_idle_three_quarter", "beckoner_idle_side", "beckoner_slam_raise_08", "beckoner_slam_strike_06", "beckoner_beckon_12"],
    "heart": ["heart_p1_front", "heart_p1_three_quarter", "heart_p2_front", "heart_p2_three_quarter", "heart_p3_front", "heart_p3_three_quarter"],
}


def tile(path, label):
    if not os.path.exists(path):
        im = Image.new("RGB", (W, H), (40, 40, 40))
    else:
        im = Image.open(path).convert("RGB").resize((W, H), Image.LANCZOS)
    ImageDraw.Draw(im).text((6, 4), label, fill=(255, 255, 0))
    return im


def sheet(name, stems, pairs=True):
    cols = len(stems)
    img = Image.new("RGB", (W * cols, H * 2))
    for i, s in enumerate(stems):
        img.paste(tile(os.path.join(OUT, "lp_tide_" + s + ".png"), "mesh " + s), (W * i, 0))
        img.paste(tile(os.path.join(OUT, "cubes_tide_" + s + ".png"), "cube " + s), (W * i, H))
    path = os.path.join(OUT, "lp_tide_%s_sheet.png" % name)
    img.save(path)
    print("wrote", path)


def main():
    os.makedirs(OUT, exist_ok=True)
    for d in glob.glob(os.path.join("run", "shots", "lp_tide_*", "shots", "lp_tide_*")):
        for f in glob.glob(os.path.join(d, "*.png")):
            shutil.copyfile(f, os.path.join(OUT, os.path.basename(f)))
    for name, stems in MODELS.items():
        sheet(name, stems)
    # heart: the intro and the death
    extra = ["heart_intro_24", "heart_intro_56", "heart_death_24", "heart_death_64"]
    sheet("heart_cinematic", extra)


if __name__ == "__main__":
    main()
