"""岩鯨・潮殻の武器・盾の最終低ポリ (7 品) を作る。
各品: 通常・レア・伝説のメッシュ + 絵 + glowmask、強化 +5 / +10 の絵と glowmask (メッシュは同じ)。
出力: docs/review/lowpoly_roughs/weapons_final/<item_id>/ と weapons_final_whale_tide.png
実行: python build_whale_tide.py [item_id ...]   (引数なしで全部)"""
from __future__ import annotations

import importlib
import json
import sys

from PIL import ImageDraw, ImageFont

from scene import *
from paint import paint
import mock
from mock import third_person, first_person, xf, body_parts
from wkit import rot_x, rot_y, rot_z, V

FINAL = ROOT / "docs/review/lowpoly_roughs/weapons_final"
SHEET = ROOT / "docs/review/lowpoly_roughs/weapons_final_whale_tide.png"
ITEMS = [  # (module, 巨神の日本語, 手に持つ型)
    ("item_whalebone_greatsword", "岩鯨", "whale"),
    ("item_captain_pike", "岩鯨", "tide"),
    ("item_waterway_fang", "岩鯨", "fangw"),
    ("item_tide_shell_trident", "潮殻", "tide"),
    ("item_coral_harpoon", "潮殻", "tide"),
    ("item_tidecaller_claw", "潮殻", "clawt"),
    ("item_coral_shield", "潮殻", "shield"),
]
BUDGET = dict(common=400, rare=450, legend=520)   # D2 (整え) で +25-50%
PLAT = (36, 40, 48)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"]

# 手に持った模式 (mock の型に足す)
mock.POSES["fangw"] = dict(arm=58, pitch=44, roll=90, s3=0.85, s1=0.6)
mock.POSES["clawt"] = dict(arm=58, pitch=44, roll=90, s3=0.85, s1=0.6)
mock.FP["fangw"] = mock.FP["whale"]
mock.FP["clawt"] = mock.FP["whale"]
for k, t in (("fangw", "whale"), ("clawt", "tide")):
    GLOW_RGB[k] = GLOW_RGB[t]


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def make(mod, rarity, plus=0):
    faces = mod.faces(rarity)
    faces, size, P, N, M, L = build(faces)
    atlas, glow = paint(faces, size, P, N, M, L, mod.TITAN, rarity, plus, mod.inlay, mod.EDGE)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def write_glow(r, path):
    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
    gm[r["glow"]] = (255, 255, 255, 255)
    save_img(gm, path)


def build_item(mod):
    d = FINAL / mod.ID
    d.mkdir(parents=True, exist_ok=True)
    res = {}
    for rar in ("common", "rare", "legend"):
        r = make(mod, rar)
        assert r["tris"] <= BUDGET[rar], f"{mod.ID} {rar} {r['tris']} > {BUDGET[rar]}"
        stem = rar
        save_img(r["atlas"], d / f"{stem}_atlas.png")
        write_glow(r, d / f"{stem}_glowmask.png")
        export_mesh(r["faces"], r["size"], d / f"{stem}_mesh.json", f"{mod.ID}_{rar}", f"{stem}_atlas.png", f"{stem}_glowmask.png")
        res[(rar, 0)] = r
        for plus in (5, 10):
            p = make(mod, rar, plus)
            assert p["size"] == r["size"] and p["tris"] == r["tris"]
            save_img(p["atlas"], d / f"{rar}_plus{plus}_atlas.png")
            write_glow(p, d / f"{rar}_plus{plus}_glowmask.png")
            res[(rar, plus)] = p
    print(mod.ID, {k: v["tris"] for k, v in res.items() if k[1] == 0}, "atlas", res[("common", 0)]["size"])
    return res


def tile(mod, r, yaw, pitch, size, scale, cen=(0, 8, 0)):
    im = view(r["faces"], r["atlas"], r["glow"], mod.TITAN, yaw, pitch, size, cen, scale)
    bg = Image.new("RGBA", size, (30, 34, 42, 255))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def shield_mock(mod, r, first):
    """盾: 腕を前へ、盾の面を前へ向けて持つ模式。"""
    p = dict(arm=62)
    parts, hand = body_parts(p["arm"], right_only=first)
    if first:
        R = rot_y(180) @ rot_z(-6)
        wf = xf(r["faces"], R, hand + V(-6, 0, -4), 1.0, V(*mod.GRIP))
        allf, at_, gl_ = compose([(wf, r["atlas"], r["glow"])], parts)
        return view(allf, at_, gl_, mod.TITAN, 20, -10, (230, 300), (4, 14, -10), 8.0)
    R = rot_y(180) @ rot_x(-12)
    wf = xf(r["faces"], R, hand + V(0, 0, -3.5), 1.0, V(*mod.GRIP))
    allf, at_, gl_ = compose([(wf, r["atlas"], r["glow"])], parts)
    return view(allf, at_, gl_, mod.TITAN, 205, -8, (230, 300), (0, 22, -10), 6.0)


def hand(mod, r, key, first):
    if key == "shield":
        im = shield_mock(mod, r, first)
    elif first:
        im = first_person(r["faces"], r["atlas"], r["glow"], key, mod.GRIP, size=(230, 300))
    else:
        im = third_person(r["faces"], r["atlas"], r["glow"], key, mod.GRIP, size=(230, 300))
    bg = Image.new("RGBA", im.size, (30, 34, 42, 255))
    bg.alpha_composite(im)
    return bg.convert("RGB")


TW, TH = 138, 300


def lab(dr, xy, t, sz=14, fill=(230, 232, 238)):
    dr.text(xy, t, font=font(sz), fill=fill)


def block_for(mod, jp_titan, key, res):
    W = 20 + 6 * TW + 12 + 2 * 232 + 30
    H = 40 + 2 * (TH + 24) + 10
    im = Image.new("RGB", (W, H), PLAT)
    dr = ImageDraw.Draw(im)
    nm = mod.NAME
    lab(dr, (20, 8), f"{jp_titan}  {nm}  ({mod.ID})   通常 {res[('common', 0)]['tris']} / レア {res[('rare', 0)]['tris']} / 伝説 {res[('legend', 0)]['tris']} 面",
        20, (255, 232, 160))
    sc = 11.0 if mod.ID != "coral_shield" else 12.5
    y = 40
    x = 20
    for rar, jp in (("common", "通常"), ("rare", "レア"), ("legend", "伝説")):
        r = res[(rar, 0)]
        for yaw, pitch in ((180, 0), (150, -12)):
            im.paste(tile(mod, r, yaw, pitch, (TW, TH), sc), (x, y)); x += TW
        lab(dr, (x - 2 * TW + 2, y + TH + 2), f"{jp} {r['tris']}面  (正面 / 3/4)", 14)
    x += 12
    im.paste(hand(mod, res[("rare", 0)], key, True), (x, y)); lab(dr, (x + 2, y + TH + 2), "一人称 (模式・レア)", 14)
    im.paste(hand(mod, res[("rare", 0)], key, False), (x + 232, y)); lab(dr, (x + 234, y + TH + 2), "三人称 (模式・レア)", 14)
    y += TH + 24
    x = 20
    for rar, jp in (("common", "通常"), ("rare", "レア"), ("legend", "伝説")):
        for plus in (0, 5, 10):
            r = res[(rar, plus)]
            pass
    # 強化の行: レア +0 +5 +10、伝説 +0 +5 +10 を 3/4 で
    for rar, jp in (("rare", "レア"), ("legend", "伝説")):
        for plus in (0, 5, 10):
            im.paste(tile(mod, res[(rar, plus)], 165, -8, (TW, TH), sc), (x, y))
            lab(dr, (x + 2, y + TH + 2), f"{jp} +{plus}", 14); x += TW
    x += 12
    # 通常 +0 / +10 (通常でも強化で光ることを見せる) を小さく
    for plus in (5, 10):
        im.paste(tile(mod, res[("common", plus)], 165, -8, (TW, TH), sc), (x, y))
        lab(dr, (x + 2, y + TH + 2), f"通常 +{plus}", 14); x += TW
    return im


def main():
    want = set(sys.argv[1:])
    rows, summary = [], {}
    for modname, jp_titan, key in ITEMS:
        mod = importlib.import_module(modname)
        if want and mod.ID not in want:
            continue
        res = build_item(mod)
        summary[mod.ID] = {rar: res[(rar, 0)]["tris"] for rar in ("common", "rare", "legend")}
        rows.append(block_for(mod, jp_titan, key, res))
    if not rows:
        return
    W = max(r.size[0] for r in rows)
    H = sum(r.size[1] for r in rows) + 80
    sheet = Image.new("RGB", (W, H), PLAT)
    dr = ImageDraw.Draw(sheet)
    lab(dr, (20, 10), "岩鯨・潮殻の武器・盾 (低ポリ最終)  通常 250 / レア 300 / 伝説 350 面以下。メッシュは階級ごとに 1 つ、強化 +5/+10 は絵と glowmask だけ", 22, (255, 255, 255))
    lab(dr, (20, 44), "上段: 階級 (通常 → レア → 伝説、正面と 3/4) と手に持った模式。下段: 強化 (レアと伝説の +0/+5/+10、通常の +5/+10)", 15, (200, 205, 215))
    y = 80
    for r in rows:
        sheet.paste(r, (0, y)); y += r.size[1]
    out = SHEET if not want else SHEET.with_name("weapons_final_whale_tide_partial.png")
    sheet.save(out)
    (FINAL / "tris.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding="utf-8") if not want else None
    print("sheet", out, sheet.size)


if __name__ == "__main__":
    main()
