"""防具 8 組の一品一案の見本 (選ぶ頁の絵) と、入れるためのメッシュ・アトラス。
巨神 4 組 = titan_one (土台は style_whale / style_tide / style_storm / style_ember、豪華さをそろえた足し)、3 階級 (+5 / +10 の光)。
旅装 4 組 = travel_one (塗りは tpaint)、階級は一つ (アンコモン)。
実行: python build_one.py [review|install] [組 ...]
  review : docs/review/armor_one/<組>/views.png (前・横・後ろ・3/4。巨神は 3 階級を縦に)
  install: 巨神は docs/review/lowpoly_roughs/armor/c/<組>/ (tools/install/install_armor_lowpoly.py の入力)、
           旅装は docs/review/armor_one/raw/<組>/ (tools/install/install_travel_armor_one.py の入力)
"""
from __future__ import annotations

import sys

from PIL import Image, ImageDraw, ImageFont

import aview
import lpkit
import titan_one
import travel_one
import tpaint
from akit import ROOT, OUT, cull_inside, build_atlas, tri_count

REVIEW = ROOT / "docs/review/armor_one"
TITANS = {"stone_whale": "whale", "tide_shell": "tide", "thunder_wing": "thunder", "ember": "ember"}
TRAVEL_GLOW = {"whale_road": "whale", "tide_wader": "tide", "peak_crossing": "thunder", "ash_walking": "ember"}
VIEWS = ((180, 6, "前"), (90, 6, "横"), (0, 6, "後ろ"), (215, 14, "3/4"))


def font(sz):
    for p in ("C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc"):
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def travel_set(key):
    out = {}
    for k, v in travel_one.SETS[key]().items():
        faces = cull_inside([f for f in v if f is not None])
        lpkit.TEX = 2.0
        size, P, N, M, L = build_atlas(faces, 32, 0)
        atlas, glow = tpaint.paint(faces, size, P, N, M, L, key)
        out[k] = dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))
    return out


def strip(res, glow_key, label):
    TW, TH = 260, 420
    im = Image.new("RGB", (TW * 4, TH + 26), aview.PLAT)
    dr = ImageDraw.Draw(im)
    for i, (yaw, pit, nm) in enumerate(VIEWS):
        im.paste(aview.render(res, glow_key, yaw, pit, (TW, TH), 8.2, center=(0, 19.0, 0)), (TW * i, 0))
        dr.text((TW * i + 8, TH + 4), nm if i else f"{nm}  {label}  ({aview.set_tris(res)} 面)", font=font(15), fill=(230, 222, 200))
    return im


def review(names):
    aview.SS = 2
    for name in names:
        out = REVIEW / name
        out.mkdir(parents=True, exist_ok=True)
        if name in TITANS:
            T = TITANS[name]
            rows = [strip(aview.build_set(titan_one.build(T, rar), T, rar), T, {"common": "通常", "rare": "レア", "legend": "伝説"}[rar])
                    for rar in ("common", "rare", "legend")]
        else:
            rows = [strip(travel_set(name), TRAVEL_GLOW[name], "アンコモン")]
        S = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)))
        y = 0
        for r in rows:
            S.paste(r, (0, y))
            y += r.height
        S.save(out / "views.png")
        print(name, "review", flush=True)


def install(names):
    aview.SS = 2
    for name in names:
        if name in TITANS:
            T = TITANS[name]
            base = OUT / "c" / name
            for rar in ("common", "rare", "legend"):
                suffix = "" if rar == "common" else f"_{rar}"
                res = aview.build_set(titan_one.build(T, rar), T, rar)
                aview.save_set(res, base, name, suffix=suffix)
                if rar != "common":
                    for plus in (5, 10):
                        aview.save_set(aview.build_set(titan_one.build(T, rar), T, rar, plus), base, name, suffix=f"{suffix}_plus{plus}", mesh=False)
        else:
            aview.save_set(travel_set(name), REVIEW / "raw" / name, name)
        print(name, "install files", flush=True)


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "review"
    names = sys.argv[2:] or list(TITANS) + list(TRAVEL_GLOW)
    (review if mode == "review" else install)(names)
