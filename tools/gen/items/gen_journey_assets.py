# -*- coding: utf-8 -*-
"""「旅の記録」の画面 (client/journey/JourneyScreen。記録・技のつまみの「旅」) の背景。ステータス画面 (gen_growth_assets.py) と同じ骨の枠と石板。
480x368 を 240x184 で描く (2 倍密度)。行と箱の位置は JourneyScreen と同じ値。
    python tools/gen/items/gen_journey_assets.py
"""
import importlib.util
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("growth_assets", HERE / "gen_growth_assets.py")
G = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(G)

OUT = G.ROOT / "textures/gui/journey"
W, H = 240, 184
ROW_Y, ROW_H, ROWS = 24, 20, 5
BOX_Y, BOX_H = 128, 47
S = G.S


def panel():
    w, h = S(W), S(H)
    img = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    yy = np.linspace(0, 1, h)[:, None]
    base = np.array(G.SLATE[:3], dtype=float) * (1 - yy * 0.25) + np.array(G.SLATE_DARK[:3], dtype=float) * (yy * 0.25)
    noise = np.random.default_rng(7).normal(0, 2.2, (h, w))[..., None]
    stone = np.clip(base[:, None, :].repeat(w, axis=1) + noise, 0, 255).astype(np.uint8)
    alpha = np.full((h, w, 1), 255, dtype=np.uint8)
    img.paste(Image.fromarray(np.concatenate([stone, alpha], axis=2), "RGBA"), (0, 0))
    d = ImageDraw.Draw(img)
    # 骨の枠 (ステータス画面と同じ)
    d.rectangle([0, 0, w - 1, h - 1], outline=G.OUTLINE)
    for i in range(1, 7):
        c = G.BONE_LIGHT if i <= 2 else G.BONE if i <= 4 else G.BONE_MID
        d.rectangle([i, i, w - 1 - i, h - 1 - i], outline=c)
    for i in range(1, 7):
        d.line([w - 1 - i, i, w - 1 - i, h - 1 - i], fill=G.BONE_DARK if i <= 3 else G.BONE_MID)
        d.line([i, h - 1 - i, w - 1 - i, h - 1 - i], fill=G.BONE_DARK if i <= 3 else G.BONE_MID)
    d.rectangle([7, 7, w - 8, h - 8], outline=G.OUTLINE)
    for x in range(24, w - 16, 24):
        d.line([x, 2, x, 6], fill=G.BONE_DARK)
        d.line([x + 1, 2, x + 1, 6], fill=G.BONE_LIGHT)
        d.line([x, h - 7, x, h - 3], fill=G.BONE_DARK)
    for y in range(24, h - 16, 24):
        d.line([2, y, 6, y], fill=G.BONE_DARK)
        d.line([w - 7, y, w - 3, y], fill=G.BONE_DARK)
    for cx, cy in [(7, 7), (w - 8, 7), (7, h - 8), (w - 8, h - 8)]:
        G.gem(d, cx, cy, 6)
    # 題の下の線
    ty = S(19)
    d.line([S(16), ty, w - S(16), ty], fill=G.TEAL_DIM)
    d.line([S(40), ty + 1, w - S(40), ty + 1], fill=(30, 60, 70, 255))
    G.gem(d, w // 2, ty, 4)
    # 土地の行のくぼみ (左に印の台)
    for i in range(ROWS):
        ry = S(ROW_Y + i * ROW_H)
        G.recess(d, S(8), ry, S(W - 8), ry + S(ROW_H - 2), fill=(19, 22, 29, 255))
        ix, iy = S(10), ry + S(3)
        d.rectangle([ix, iy, ix + S(12) - 1, iy + S(12) - 1], fill=G.BONE_DARK, outline=G.OUTLINE)
        d.rectangle([ix + 2, iy + 2, ix + S(12) - 3, iy + S(12) - 3], fill=G.RECESS)
        # 名声のバーの溝
        G.recess(d, S(28), ry + S(13), S(170), ry + S(16), fill=(12, 14, 19, 255))
    # 下の二つの箱 (図鑑・熟練)
    for x0, x1 in ((8, 118), (122, 232)):
        G.recess(d, S(x0), S(BOX_Y), S(x1), S(BOX_Y + BOX_H), fill=(19, 22, 29, 255))
        d.line([S(x0) + 6, S(BOX_Y + 12), S(x1) - 6, S(BOX_Y + 12)], fill=G.TEAL_DIM)
        G.recess(d, S(x0 + 6), S(BOX_Y + 37), S(x1 - 6), S(BOX_Y + 40), fill=(12, 14, 19, 255))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    img = panel()
    # バーの中身 (青緑 = 名声・熟練、金 = 最上)。y 466・470
    img.paste(G.strip(S(160), 4, (191, 244, 255), (79, 184, 200)), (0, 466))
    img.paste(G.strip(S(160), 4, (255, 224, 138), (224, 160, 48)), (0, 470))
    img.save(OUT / "journey.png")
    print("wrote", OUT / "journey.png")


if __name__ == "__main__":
    main()
