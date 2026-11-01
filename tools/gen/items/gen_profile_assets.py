# -*- coding: utf-8 -*-
"""「プロフィール」の画面 (endgame/client/ProfileScreen。持ち物の画面の右上の札から開く) の背景。ステータス画面 (gen_growth_assets.py) と同じ骨の枠と石板。
600x392 を 300x196 で描く (2 倍密度)。箱とつまみの位置は ProfileScreen と同じ値。
    python tools/gen/items/gen_profile_assets.py
"""
import importlib.util
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("growth_assets", HERE / "gen_growth_assets.py")
G = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(G)

OUT = G.ROOT / "textures/gui/profile"
W, H = 300, 196
# 左の箱 (人形と名前)、右の中身の箱、右の上のつまみの段
DOLL = (8, 24, 104, 188)
DOLL_FRAME = (14, 30, 98, 126)
BODY = (110, 40, 292, 188)
SUB_Y = 24
S = G.S


def panel():
    w, h = S(W), S(H)
    img = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    yy = np.linspace(0, 1, h)[:, None]
    base = np.array(G.SLATE[:3], dtype=float) * (1 - yy * 0.25) + np.array(G.SLATE_DARK[:3], dtype=float) * (yy * 0.25)
    noise = np.random.default_rng(11).normal(0, 2.2, (h, w))[..., None]
    stone = np.clip(base[:, None, :].repeat(w, axis=1) + noise, 0, 255).astype(np.uint8)
    alpha = np.full((h, w, 1), 255, dtype=np.uint8)
    img.paste(Image.fromarray(np.concatenate([stone, alpha], axis=2), "RGBA"), (0, 0))
    d = ImageDraw.Draw(img)
    # 骨の枠 (ステータス画面・旅の記録と同じ)
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
    # 左: 人形の箱 (くぼみ + 骨の細い縁の窓) と、名前・称号の台
    G.recess(d, S(DOLL[0]), S(DOLL[1]), S(DOLL[2]), S(DOLL[3]), fill=(19, 22, 29, 255))
    fx0, fy0, fx1, fy1 = (S(v) for v in DOLL_FRAME)
    d.rectangle([fx0 - 2, fy0 - 2, fx1 + 1, fy1 + 1], outline=G.BONE_DARK)
    d.rectangle([fx0 - 1, fy0 - 1, fx1, fy1], outline=G.OUTLINE)
    # 窓の中は少し青い (空の色を薄く)
    win = np.linspace(0, 1, fy1 - fy0)[:, None]
    sky = np.array([34, 44, 58], dtype=float) * (1 - win) + np.array([20, 24, 32], dtype=float) * win
    sky = np.clip(sky[:, None, :].repeat(fx1 - fx0, axis=1), 0, 255).astype(np.uint8)
    img.paste(Image.fromarray(np.concatenate([sky, np.full((fy1 - fy0, fx1 - fx0, 1), 255, np.uint8)], axis=2), "RGBA"), (fx0, fy0))
    d = ImageDraw.Draw(img)
    # 人形の足もとの台
    d.ellipse([fx0 + S(20), fy1 - S(10), fx1 - S(20), fy1 - S(3)], fill=(14, 16, 21, 255))
    # 名前の下の線
    ny = S(152)
    d.line([S(16), ny, S(96), ny], fill=G.TEAL_DIM)
    G.gem(d, S(56), ny, 3)
    # 右: 中身の箱
    G.recess(d, S(BODY[0]), S(BODY[1]), S(BODY[2]), S(BODY[3]), fill=(19, 22, 29, 255))
    return img


def tab(state):
    # 右の上のつまみ (58x14 を 2 倍)。0 = ふつう、1 = 乗せた、2 = 選んだ
    tw, th = S(58), S(14)
    t = Image.new("RGBA", (tw, th), (0, 0, 0, 0))
    d = ImageDraw.Draw(t)
    fill = [(26, 30, 38, 255), (36, 42, 54, 255), (19, 22, 29, 255)][state]
    d.rectangle([0, 0, tw - 1, th - 1], fill=fill, outline=G.OUTLINE)
    edge = G.BONE_MID if state < 2 else G.BONE_LIGHT
    d.line([1, 1, tw - 2, 1], fill=edge)
    d.line([1, 1, 1, th - 1], fill=edge)
    d.line([tw - 2, 1, tw - 2, th - 1], fill=G.BONE_DARK)
    if state == 2:
        d.line([4, th - 3, tw - 5, th - 3], fill=G.GOLD)
    return t


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    img = panel()
    # つまみ (y 400 から、横に三つ)
    for s in range(3):
        img.paste(tab(s), (s * 120, 400))
    img.save(OUT / "profile.png")
    print("wrote", OUT / "profile.png")


if __name__ == "__main__":
    main()
