# -*- coding: utf-8 -*-
"""ボスの後光の絵 (client/entity/PaperCutLayer が体の後ろに、カメラへ向けて足して描く。色は頂点の色で付ける)。
  halo_ring.png     : 淵の主 (金継ぎの漆) の輪。太い輪と細い輪、ぼかしたにじみ
  halo_crescent.png : 月の主 (螺鈿の月) の三日月
    python -I -X utf8 tools/gen/mobs/gen_boss_halos.py
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parents[3] / "src/main/resources/assets/skyabyss/textures/entity"
S = 256


def save(img, name):
    img.save(OUT / name)
    print("wrote", OUT / name)


def ring():
    g = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(g)
    d.ellipse([12, 12, S - 12, S - 12], outline=255, width=10)
    d.ellipse([40, 40, S - 40, S - 40], outline=150, width=3)
    for k in range(24):
        a = k / 24 * 2 * np.pi
        r0, r1 = S / 2 - 22, S / 2 - 12
        c = S / 2
        d.line([c + np.cos(a) * r0, c + np.sin(a) * r0, c + np.cos(a) * r1, c + np.sin(a) * r1], fill=200, width=3)
    blur = g.filter(ImageFilter.GaussianBlur(6))
    a = np.maximum(np.asarray(g, float), np.asarray(blur, float) * 0.8)
    img = np.zeros((S, S, 4), np.uint8)
    # 足して描く (RenderType.eyes は透けを見ない) ので、色に透けを掛けておく
    img[..., :3] = np.clip(a, 0, 255).astype(np.uint8)[..., None]
    img[..., 3] = np.clip(a, 0, 255).astype(np.uint8)
    save(Image.fromarray(img, "RGBA"), "halo_ring.png")


def crescent():
    g = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(g)
    d.ellipse([16, 16, S - 16, S - 16], fill=255)
    d.ellipse([60, 4, S + 20, S - 30], fill=0)
    blur = g.filter(ImageFilter.GaussianBlur(8))
    a = np.maximum(np.asarray(g, float) * 0.9, np.asarray(blur, float) * 0.7)
    img = np.zeros((S, S, 4), np.uint8)
    img[..., :3] = np.clip(a, 0, 255).astype(np.uint8)[..., None]
    img[..., 3] = np.clip(a, 0, 255).astype(np.uint8)
    save(Image.fromarray(img, "RGBA"), "halo_crescent.png")


if __name__ == "__main__":
    ring()
    crescent()
