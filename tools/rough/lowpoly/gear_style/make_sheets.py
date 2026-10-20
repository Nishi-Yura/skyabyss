"""3 様式の一式 (岩鯨の 大剣・弓・槍・杖・短剣 + 兜・胸・脚・靴) を、回転の並び・手に持った姿・輪郭・階級の並びに描く。
実行: python make_sheets.py <out_dir> [A B C]
出力: sheet_weapons_<S>.png / sheet_armor_<S>.png / sheet_inhand_<S>.png / sheet_tier_<S>.png / sil_<S>.png / hero_<S>.png"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import rend  # noqa: E402
import arend  # noqa: E402
import aview  # noqa: E402
from rend import Image, ImageDraw, font, view, make, build, PLAT  # noqa: E402
from akit import mannequin, pose_faces, cull_inside  # noqa: E402
from scene import compose  # noqa: E402
from mock import xf  # noqa: E402
from wkit import rot_x, rot_y, rot_z, V  # noqa: E402
import numpy as np  # noqa: E402

NAMES = dict(A="A 量塊 (Carved Mass)", B="B 祭具 (Ritual Grace)", C="C 遺骸 (Grown Remains)")
WEAPONS = [("greatsword", "大剣"), ("bow", "弓"), ("spear", "槍"), ("staff", "杖"), ("dagger", "短剣")]
CEN = dict(greatsword=((0, 9, 0), 9.0), bow=((1, 9, 0), 9.0), spear=((0, 9, 0), 9.0), staff=((0, 9, 0), 9.0), dagger=((0, 7, 0), 13.0))


def weapon_models(style, tier=1):
    mod = importlib.import_module("w_" + style.lower())
    out = {}
    for name, _ in WEAPONS:
        fn = getattr(mod, name)
        faces = fn(tier, 0) if name == "bow" else fn(tier)
        r = make(faces, style)
        r["grip"] = mod.GRIP[name]
        out[name] = r
    return mod, out


def sheet_weapons(style, ms, out):
    TW, TH = 190, 300
    S = Image.new("RGB", (5 * TW, 4 * TH + 30), (22, 24, 30))
    d = ImageDraw.Draw(S)
    for i, (name, jp) in enumerate(WEAPONS):
        cen, sc = CEN[name]
        for j, yaw in enumerate((180, 145, 90, 215)):
            S.paste(rend.tile(ms[name], yaw, -8, (TW, TH), cen, sc), (i * TW, j * TH))
        d.text((i * TW + 6, 4 * TH + 6), f"{jp}  {ms[name]['tris']} 面", font=font(15), fill=(230, 232, 238))
    S.save(out)


def sheet_armor(style, res, out):
    mod = importlib.import_module("ar_" + style.lower())
    W, H = 300, 400
    S = Image.new("RGB", (4 * W, H + 300 + 28), (22, 24, 30))
    d = ImageDraw.Draw(S)
    for i, yaw in enumerate((180, 135, 90, 0)):
        S.paste(arend.render(res, yaw), (i * W, 0))
    # 部位ごとの近接 (3/4)
    for i, (key, cy, sc) in enumerate((("helmet", 28.5, 19), ("chestplate", 18.0, 13), ("leggings", 6.5, 16), ("boots", 3.0, 22))):
        im = arend.render(res, 150, -8, (W, 300), sc, only=[key], center=(0, cy, 0), mann=True)
        S.paste(im, (i * W, H))
        d.text((i * W + 6, H + 300 + 6), {"helmet": "兜", "chestplate": "胸 (肩・背)", "leggings": "脚", "boots": "靴"}[key] + f"  {res[key]['tris']} 面", font=font(15), fill=(230, 232, 238))
    S.save(out)


def sheet_tier(style, out):
    mod = importlib.import_module("w_" + style.lower())
    S = Image.new("RGB", (3 * 2 * 150, 300 + 26), (22, 24, 30))
    d = ImageDraw.Draw(S)
    for t, jp in enumerate(("コモン", "レア", "レジェンド")):
        r = make(mod.greatsword(t), style, ("common", "rare", "legend")[t])
        for j, yaw in enumerate((180, 145)):
            S.paste(rend.tile(r, yaw, -8, (150, 300), (0, 9, 0), 9.0), ((t * 2 + j) * 150, 0))
        d.text((t * 300 + 6, 304), f"{jp} {r['tris']} 面", font=font(14), fill=(230, 232, 238))
    S.save(out)


def silhouette(im, ink=(14, 16, 22)):
    a = im.split()[3] if im.mode == "RGBA" else None
    return a


def sil_sheet(style, ms, res, out, sizes=(1.0, 0.5)):
    """武器 5 点と防具の前を、黒い輪郭だけで (遠目の読み)。大・小の 2 サイズ。"""
    W = 5 * 110 + 160
    S = Image.new("RGB", (W, 2 * 190), (214, 218, 224))
    for row, k in enumerate(sizes):
        for i, (name, jp) in enumerate(WEAPONS):
            cen, sc = CEN[name]
            im = view(ms[name]["faces"], ms[name]["atlas"], ms[name]["glow"], "whale", 180, 0, (110, 190), cen, sc * 0.62 * k * 1.0, ss=2, outline=False)
            a = im.split()[3]
            blk = Image.new("RGB", im.size, (20, 22, 28))
            S.paste(blk, (i * 110, row * 190), a)
        faces, atlas, glow = aview.scene_of(res, None, None, True)
        im = view(faces, atlas, glow, "whale", 180, 0, (160, 190), (0, 18.5, 0), 4.6 * k, ss=2, outline=False)
        a = im.split()[3].transpose(Image.FLIP_LEFT_RIGHT)
        S.paste(Image.new("RGB", im.size, (20, 22, 28)), (5 * 110, row * 190), a)
    S.save(out)


def hand_pose(name):
    # (腕の上げ, 武器の前傾, 武器の roll, 手の握り)
    return dict(greatsword=(58, 40, 90), spear=(74, 70, 90), staff=(40, 8, 90), dagger=(66, 56, 90), bow=(80, 0, 0))[name]


def inhand(style, ms, res, name, yaw=150):
    arm, pitch, roll = hand_pose(name)
    piv = V(-5, 22, 0)
    R = rot_x(arm)
    hand = piv + R @ V(-1.0, -9.0, 0)
    if name == "bow":
        Rw = rot_y(-90) @ rot_x(0)
        piv = V(5, 22, 0)
        hand = piv + rot_x(arm) @ V(1.0, -9.0, 0)
    else:
        Rw = rot_x(-pitch) @ rot_y(roll)
    wf = xf(ms[name]["faces"], Rw, hand, 1.0, V(*ms[name]["grip"]))
    parts = [(r["faces"], r["atlas"], r["glow"]) for r in res.values()] + [(wf, ms[name]["atlas"], ms[name]["glow"])]
    faces, atlas, glow = compose(parts, mannequin())
    pose = {"armorRightArm": (arm, 0, 0)} if name != "bow" else {"armorLeftArm": (arm, 0, 0)}
    if name in ("greatsword", "spear", "staff"):
        pose["armorLeftArm"] = (arm + 8, 0, 18)
    faces = pose_faces(faces, pose)
    im = view(faces, atlas, glow, "whale", yaw, -6, (260, 330), (0, 17, -4), 6.3, ss=2)
    bg = Image.new("RGBA", (260, 330), PLAT + (255,))
    bg.alpha_composite(im)
    return bg.convert("RGB").transpose(Image.FLIP_LEFT_RIGHT)


def sheet_inhand(style, ms, res, out):
    S = Image.new("RGB", (5 * 260, 330 + 26), (22, 24, 30))
    d = ImageDraw.Draw(S)
    for i, (name, jp) in enumerate(WEAPONS):
        S.paste(inhand(style, ms, res, name), (i * 260, 0))
        d.text((i * 260 + 6, 334), jp + " (手に持つ・三人称の模式)", font=font(14), fill=(230, 232, 238))
    S.save(out)


def hero(style, ms, res, out):
    """一式を着た姿 (大剣) と、5 本の武器の並び。"""
    S = Image.new("RGB", (900, 420), (26, 30, 38))
    S.paste(inhand(style, ms, res, "greatsword", 150).resize((312, 396), Image.LANCZOS), (10, 12))
    for i, (name, jp) in enumerate(WEAPONS):
        cen, sc = CEN[name]
        S.paste(rend.tile(ms[name], 160, -8, (115, 400), cen, sc * 0.82), (330 + i * 112, 10))
    S.save(out)


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for s in (sys.argv[2:] or ["A", "B", "C"]):
        mod, ms = weapon_models(s)
        am = importlib.import_module("ar_" + s.lower())
        res = arend.build_set(am.build(), s)
        print(s, "weapons", {k: v["tris"] for k, v in ms.items()}, "armor", arend.tris(res), flush=True)
        sheet_weapons(s, ms, out / f"sheet_weapons_{s}.png")
        sheet_armor(s, res, out / f"sheet_armor_{s}.png")
        sheet_inhand(s, ms, res, out / f"sheet_inhand_{s}.png")
        sheet_tier(s, out / f"sheet_tier_{s}.png")
        sil_sheet(s, ms, res, out / f"sil_{s}.png")
        hero(s, ms, res, out / f"hero_{s}.png")
        print("done", s, flush=True)


if __name__ == "__main__":
    main()
