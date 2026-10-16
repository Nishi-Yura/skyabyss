"""装飾の枠の画面の素材を作る (護符 1・装飾 2。03 の 10章)。

使い方:
    python tools/gen_accessory_assets.py            # 決めた案 (LAYOUT) で本番のテクスチャを書く
    python tools/gen_accessory_assets.py --roughs   # 3 案の下書きを docs/review/accessory_slot_roughs/ に書く

出力先: src/main/resources/assets/skyabyss/textures/gui/accessory/accessory.png (512x512、2 倍密度)

    - 0..331 行、0..351 列 : 画面 (176x166)。骨の枠、石板、人形の窓、護符と装飾の枠、持ち物の枠
    - 340..371 行         : つまみ 48x16 (2 倍で 96x32)。押せる・重ねた・選ばれた の 3 つを 100 px ずつ
    - 380..411 行         : 空の枠の印 16x16 (2 倍で 32x32)。護符 (首飾り)・装飾 (指輪と石) を 40 px ずつ

色と骨の枠は共鳴の炉 (gen_forge_assets.py) と同じ。画面の配置は AccessoryScreen.java / AccessoryMenu.java の値と同じにする。
何度実行しても同じ物ができる (乱数の種を固定)。
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_forge_assets as gf  # noqa: E402  骨の枠・石板・くぼみ・枠を使い回す

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUT = ASSETS / "textures/gui/accessory/accessory.png"
ROUGHS = ROOT / "docs/review/accessory_slot_roughs"
gf.rng = np.random.default_rng(20261017)

S = gf.S
OUTLINE, BONE, BONE_LIGHT, BONE_MID, BONE_DARK = gf.OUTLINE, gf.BONE, gf.BONE_LIGHT, gf.BONE_MID, gf.BONE_DARK
TEAL, TEAL_DIM, PALE, GOLD = gf.TEAL, gf.TEAL_DIM, gf.PALE, gf.GOLD
W, H = 176, 166
TAB_W, TAB_H = 48, 16

# 案ごとの配置 (GUI 単位)。doll = 人形の窓 (x0, y0, x1, y1)、charm = 護符の枠、trinkets = 装飾の枠 (枠の中身の左上)
LAYOUTS = {
    # A: 縦の列。左に人形、その右に護符・装飾・装飾を縦に並べ、右は文字の欄
    "a": dict(doll=(8, 16, 58, 78), charm=(68, 18), trinkets=[(68, 40), (68, 58)], info=(88, 16, 169, 78)),
    # B: 人形を囲む。真ん中に人形、左に護符 (大きな輪)、右に装飾 2 つを縦に
    "b": dict(doll=(58, 14, 118, 78), charm=(30, 36), trinkets=[(130, 26), (130, 50)], info=None),
    # C: 棚。左に骨の棚を渡して護符と装飾を横一列、右に人形
    "c": dict(doll=(116, 14, 168, 78), charm=(16, 34), trinkets=[(54, 34), (76, 34)], info=(10, 58, 106, 78)),
}
LAYOUT = "b"


def charm_frame(d, gx, gy):
    """護符の枠: 枠のまわりに金の輪 (使う品なので目立たせる) と、上下の小さな石"""
    gf.slot(d, gx, gy)
    cx, cy = S(gx + 8), S(gy + 8)
    for r, c, wdt in [(S(14), (90, 70, 30, 255), 3), (S(13), gf.GOLD, 2), (S(12) - 2, (120, 96, 40, 255), 1)]:
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=c, width=wdt)
    gf.gem(d, cx, cy - S(14), 4, fill=(120, 96, 40, 255), light=GOLD)
    gf.gem(d, cx, cy + S(14), 4, fill=(120, 96, 40, 255), light=GOLD)
    # 枠そのものは二重の縁
    d.rectangle([S(gx - 2), S(gy - 2), S(gx + 18) - 1, S(gy + 18) - 1], outline=GOLD)


def trinket_frame(d, gx, gy):
    """装飾の枠: 骨の縁と、角に青緑の小さな石"""
    gf.slot(d, gx, gy)
    d.rectangle([S(gx - 2), S(gy - 2), S(gx + 18) - 1, S(gy + 18) - 1], outline=BONE_MID)
    d.rectangle([S(gx - 2) + 1, S(gy - 2) + 1, S(gx + 18) - 2, S(gy + 18) - 2], outline=BONE_DARK)
    for px, py in [(S(gx - 2), S(gy - 2)), (S(gx + 18) - 1, S(gy - 2)), (S(gx - 2), S(gy + 18) - 1), (S(gx + 18) - 1, S(gy + 18) - 1)]:
        d.rectangle([px - 1, py - 1, px + 1, py + 1], fill=TEAL)


def doll_window(d, x0, y0, x1, y1):
    """人形の窓: 暗いくぼみに、下から淡い青緑の光"""
    X0, Y0, X1, Y1 = S(x0), S(y0), S(x1), S(y1)
    for y in range(Y0, Y1):
        t = (y - Y0) / (Y1 - Y0)
        k = max(0.0, t - 0.45) / 0.55
        c = tuple(int(gf.RECESS[i] * (1 - k * 0.6) + TEAL_DIM[i] * k * 0.6) for i in range(3)) + (255,)
        d.line([X0, y, X1 - 1, y], fill=c)
    d.line([X0, Y0, X1 - 1, Y0], fill=OUTLINE)
    d.line([X0, Y0, X0, Y1 - 1], fill=OUTLINE)
    d.line([X0 + 1, Y1 - 1, X1 - 1, Y1 - 1], fill=gf.RECESS_EDGE)
    d.line([X1 - 1, Y0 + 1, X1 - 1, Y1 - 1], fill=gf.RECESS_EDGE)
    # 足もとの台 (骨の薄い円盤)
    cx = (X0 + X1) // 2
    d.ellipse([cx - S(16), Y1 - S(9), cx + S(16), Y1 - S(3)], outline=BONE_MID, fill=(40, 44, 52, 255))
    d.arc([cx - S(16), Y1 - S(9), cx + S(16), Y1 - S(3)], 200, 340, fill=BONE_LIGHT)


def panel(img, layout):
    w, h = S(W), S(H)
    img.paste(gf.stone(w, h), (0, 0))
    d = ImageDraw.Draw(img)
    gf.bone_frame(d, 0, 0, w, h)
    doll_window(d, *layout["doll"])
    if layout["info"]:
        gf.recess(d, S(layout["info"][0]), S(layout["info"][1]), S(layout["info"][2]), S(layout["info"][3]), fill=(22, 25, 33, 255))
    if layout is LAYOUTS["c"]:
        # 骨の棚: 枠の下に渡す
        y = S(54)
        d.rectangle([S(10), y, S(100), y + 5], fill=BONE, outline=OUTLINE)
        d.line([S(10) + 1, y + 1, S(100) - 1, y + 1], fill=BONE_LIGHT)
    charm_frame(d, *layout["charm"])
    for t in layout["trinkets"]:
        trinket_frame(d, *t)
    # 持ち物との境の線 (「持ち物」の文字 (y 72..80) の右から)
    ty = S(81)
    d.line([S(60), ty, w - S(10), ty], fill=TEAL_DIM)
    gf.gem(d, S(120), ty, 4)
    for row in range(3):
        for col in range(9):
            gf.slot(d, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        gf.slot(d, 8 + col * 18, 142)


def tab(img, ox, oy, state):
    """つまみ: 骨の縁の札。state 0 押せる / 1 重ねた / 2 選ばれた (下の縁を開けて画面とつなぐ)"""
    w, h = S(TAB_W), S(TAB_H)
    d = ImageDraw.Draw(img)
    body = {0: (34, 38, 48, 255), 1: (44, 52, 64, 255), 2: gf.SLATE}[state]
    d.rectangle([ox, oy, ox + w - 1, oy + h - 1], fill=body, outline=OUTLINE)
    edge = BONE_MID if state == 0 else BONE if state == 1 else BONE_LIGHT
    d.rectangle([ox + 1, oy + 1, ox + w - 2, oy + h - 1], outline=edge)
    d.line([ox + 2, oy + 2, ox + w - 3, oy + 2], fill=BONE_LIGHT if state else BONE)
    if state == 2:
        d.line([ox + 2, oy + h - 1, ox + w - 3, oy + h - 1], fill=body)
        d.line([ox + 6, oy + 5, ox + w - 7, oy + 5], fill=TEAL)


def icon_charm(d, ox, oy, a=110):
    """空の護符の枠の印: 首飾り (鎖と雫の石)。暗く透かす"""
    c = (159, 150, 124, a)
    d.arc([ox + 6, oy + 2, ox + 26, oy + 20], 200, 340, fill=c, width=2)
    d.line([ox + 8, oy + 9, ox + 16, oy + 18], fill=c, width=2)
    d.line([ox + 24, oy + 9, ox + 16, oy + 18], fill=c, width=2)
    d.polygon([(ox + 16, oy + 16), (ox + 21, oy + 22), (ox + 16, oy + 29), (ox + 11, oy + 22)], outline=c, fill=(42, 94, 106, a))


def icon_trinket(d, ox, oy, a=110):
    """空の装飾の枠の印: 輪と小さな石"""
    c = (159, 150, 124, a)
    d.ellipse([ox + 6, oy + 10, ox + 24, oy + 28], outline=c, width=3)
    d.polygon([(ox + 15, oy + 3), (ox + 20, oy + 8), (ox + 15, oy + 13), (ox + 10, oy + 8)], outline=c, fill=(42, 94, 106, a))


def build(layout_key, path, preview=False):
    layout = LAYOUTS[layout_key]
    img = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    panel(img, layout)
    for state in range(3):
        tab(img, state * 100, 340, state)
    icons = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    di = ImageDraw.Draw(icons)
    icon_charm(di, 0, 380)
    icon_trinket(di, 40, 380)
    img = Image.alpha_composite(img, icons)
    if not preview:
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        print("wrote", path)
        return
    # 下書き: 画面だけを切り出し、上につまみ、枠に品の絵と空の印、人形の影、文字の位置を置いて 2 倍で見る
    sheet = Image.new("RGBA", (S(W) + 40, S(H) + 60), (40, 40, 48, 255))
    sheet.paste(img.crop((0, 0, S(W), S(H))), (20, 50))
    sheet.paste(img.crop((200, 340, 296, 372)), (20 + S(W) - S(TAB_W) - 104, 50 - S(TAB_H) + 4))
    sheet.paste(img.crop((0, 340, 96, 372)), (20 + S(W) - S(TAB_W) - 4, 50 - S(TAB_H) + 4))
    d = ImageDraw.Draw(sheet)
    x0, y0, x1, y1 = layout["doll"]
    cx = 20 + S((x0 + x1) // 2)
    body = (70, 76, 90, 255)
    d.ellipse([cx - 14, 50 + S(y1) - S(56), cx + 14, 50 + S(y1) - S(42)], fill=body)
    d.rectangle([cx - 16, 50 + S(y1) - S(42), cx + 16, 50 + S(y1) - S(22)], fill=body)
    d.rectangle([cx - 14, 50 + S(y1) - S(22), cx + 14, 50 + S(y1) - S(6)], fill=body)
    items = ASSETS / "textures/item"
    charm = Image.open(items / "resonance_charm.png").convert("RGBA").resize((32, 32), Image.NEAREST)
    gx, gy = layout["charm"]
    sheet.alpha_composite(charm, (20 + S(gx), 50 + S(gy)))
    tx, ty = layout["trinkets"][0]
    plating = Image.open(items / "stone_whale_plating.png").convert("RGBA").resize((32, 32), Image.NEAREST)
    sheet.alpha_composite(plating, (20 + S(tx), 50 + S(ty)))
    tx, ty = layout["trinkets"][1]
    sheet.alpha_composite(img.crop((40, 380, 72, 412)), (20 + S(tx), 50 + S(ty)))
    d.text((20 + S(8), 50 + S(6)), "Accessories", fill=(216, 220, 230, 255))
    d.text((20 + S(8), 50 + S(72)), "Inventory", fill=(216, 220, 230, 255))
    d.text((20, 8), f"rough {layout_key.upper()}", fill=(242, 193, 78, 255))
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(path)
    print("wrote", path)


if __name__ == "__main__":
    if "--roughs" in sys.argv:
        for key in LAYOUTS:
            build(key, ROUGHS / f"{key}.png", preview=True)
    else:
        build(LAYOUT, OUT)
