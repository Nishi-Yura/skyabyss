"""旅嚢 (docs/design/22_mod_pouch.md) の画面の素材を作る。装飾の画面 (tools/gen/items/gen_accessory_assets.py) と同じ作り: 2 倍密度 (GUI の 1 単位 = 2 px、32 画素の絵に合わせた細かさ)、
骨の枠・石板・くぼみ・枠・角の石は共鳴の炉 (tools/gen/items/gen_forge_assets.py) と同じ。つまみは装飾の画面の素材 (accessory.png) を使うのでここでは作らない。

使い方:
    python tools/gen/items/gen_pouch_assets.py            # 本番のテクスチャを書く
    python tools/gen/items/gen_pouch_assets.py --preview  # docs/review/_gallery_pouch/pouch_screen_preview.png に、品の絵と文字を載せた見本を書く

出力: src/main/resources/assets/skyabyss/textures/gui/pouch/pouch.png (512x512)
    (   0,   0) 352x400 : 画面 176x200 (骨の枠・石板・名札の下の線・下の説明のくぼみ)
    ( 360,   0) 36x36   : 枠 (空)         ( 400,   0) 36x36 : 枠 (品あり、青緑の縁)    ( 440,   0) 36x36 : 枠 (金。初めて入った・使える品)
    ( 360,  50) 32x32   : 「？」の印 (まだ手に入れていない品。16x16 に縮めて枠の中へ)
    (   0, 420) 88x32   : ボタン 44x16 押せる      ( 100, 420) : 重ねた      ( 200, 420) : 押せない
    ( 360, 150) 56x56   : 輪の札 28x28 (選ばれていない)   ( 420, 150) : 輪の札 (選ばれた)   ( 360, 220) 80x80 : 輪の中心
画面の配置は pouch/PouchLayout.java と client/pouch/PouchScreen.java の値と同じにする。何度実行しても同じ物ができる (乱数の種を固定)。
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_forge_assets as gf  # noqa: E402  骨の枠・石板・くぼみ・角の石を使い回す

ROOT = Path(__file__).resolve().parent.parent.parent.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
OUT = ASSETS / "textures/gui/pouch/pouch.png"
PREVIEW = ROOT / "docs/review/_gallery_pouch/pouch_screen_preview.png"
gf.rng = np.random.default_rng(20261020)

S = gf.S
OUTLINE, BONE, BONE_LIGHT, BONE_MID, BONE_DARK = gf.OUTLINE, gf.BONE, gf.BONE_LIGHT, gf.BONE_MID, gf.BONE_DARK
TEAL, TEAL_DIM, PALE, GOLD = gf.TEAL, gf.TEAL_DIM, gf.PALE, gf.GOLD
W, H = 176, 200
SEPARATOR_Y = 160


def panel(img):
    w, h = S(W), S(H)
    img.paste(gf.stone(w, h), (0, 0))
    d = ImageDraw.Draw(img)
    gf.bone_frame(d, 0, 0, w, h)
    # 名札の欄の下に細い線 (題の下)
    ty = S(16)
    d.line([S(8), ty, w - S(10), ty], fill=TEAL_DIM)
    gf.gem(d, S(W // 2), ty, 4)
    # 枠の並びと下の説明の境
    sy = S(SEPARATOR_Y)
    d.line([S(8), sy, w - S(10), sy], fill=TEAL_DIM)
    gf.gem(d, S(W // 2), sy, 4)
    # 下の説明のくぼみ
    gf.recess(d, S(8), S(SEPARATOR_Y + 4), S(W - 8), S(H - 8), fill=(22, 25, 33, 255))


def slot_frame(img, ox, oy, kind):
    """枠 18x18 (2 倍で 36x36)。kind: empty / filled (青緑の縁) / gold (金の縁)"""
    d = ImageDraw.Draw(img)
    gf.recess(d, ox, oy, ox + 36, oy + 36)
    d.line([ox + 2, oy + 36 - 2, ox + 36 - 2, oy + 36 - 2], fill=(30, 34, 44, 255))
    if kind == "filled":
        d.rectangle([ox + 1, oy + 1, ox + 34, oy + 34], outline=TEAL_DIM)
    elif kind == "gold":
        d.rectangle([ox, oy, ox + 35, oy + 35], outline=GOLD)
        d.rectangle([ox + 1, oy + 1, ox + 34, oy + 34], outline=(150, 118, 48, 255))


def unknown_mark(img, ox, oy):
    """「？」の印 16x16 (2 倍で 32x32): 暗い輪郭の上に、淡い「？」"""
    d = ImageDraw.Draw(img)
    c = (92, 104, 122, 235)
    dark = (58, 68, 84, 235)
    # 「？」: 上の弧、右へ折れて、下の点
    for dx, dy in ((1, 1), (0, 0)):
        col = dark if dx else c
        d.arc([ox + 9 + dx, oy + 4 + dy, ox + 22 + dx, oy + 17 + dy], 180, 90, fill=col, width=3)
        d.line([ox + 16 + dx, oy + 16 + dy, ox + 16 + dx, oy + 21 + dy], fill=col, width=3)
        d.rectangle([ox + 15 + dx, oy + 25 + dy, ox + 18 + dx, oy + 28 + dy], fill=col)


def button(img, ox, oy, state):
    """ボタン 44x16 (2 倍で 88x32): 押せる / 重ねた / 押せない"""
    d = ImageDraw.Draw(img)
    w, h = 88, 32
    body = {0: (34, 38, 48, 255), 1: (44, 52, 64, 255), 2: (26, 29, 36, 255)}[state]
    d.rectangle([ox, oy, ox + w - 1, oy + h - 1], fill=body, outline=OUTLINE)
    edge = {0: BONE_MID, 1: BONE, 2: BONE_DARK}[state]
    d.rectangle([ox + 1, oy + 1, ox + w - 2, oy + h - 2], outline=edge)
    d.line([ox + 2, oy + 2, ox + w - 3, oy + 2], fill=BONE_LIGHT if state == 1 else BONE if state == 0 else BONE_MID)
    d.line([ox + 2, oy + h - 3, ox + w - 3, oy + h - 3], fill=OUTLINE)
    for px, py in ((ox + 3, oy + 3), (ox + w - 4, oy + 3), (ox + 3, oy + h - 4), (ox + w - 4, oy + h - 4)):
        d.rectangle([px - 1, py - 1, px, py], fill=TEAL if state != 2 else TEAL_DIM)


def wheel_plate(img, ox, oy, selected):
    """輪の札 28x28 (2 倍で 56x56): 骨の縁の丸い札。選ばれたものは金の縁と明るい地"""
    d = ImageDraw.Draw(img)
    r = 27
    cx, cy = ox + 28, oy + 28
    fill = (50, 58, 74, 255) if selected else (26, 30, 39, 255)
    d.ellipse([cx - r, cy - r, cx + r - 1, cy + r - 1], fill=OUTLINE)
    d.ellipse([cx - r + 1, cy - r + 1, cx + r - 2, cy + r - 2], fill=GOLD if selected else BONE_MID)
    d.ellipse([cx - r + 4, cy - r + 4, cx + r - 5, cy + r - 5], fill=BONE_DARK if not selected else (150, 118, 48, 255))
    d.ellipse([cx - r + 6, cy - r + 6, cx + r - 7, cy + r - 7], fill=fill)
    if selected:
        d.arc([cx - r + 8, cy - r + 8, cx + r - 9, cy + r - 9], 200, 290, fill=(255, 226, 150, 255), width=2)


def wheel_center(img, ox, oy):
    """輪の中心 40x40 (2 倍で 80x80): 小さな暗い円と青緑の縁。取りやめの場所"""
    d = ImageDraw.Draw(img)
    cx, cy = ox + 40, oy + 40
    d.ellipse([cx - 39, cy - 39, cx + 38, cy + 38], fill=OUTLINE)
    d.ellipse([cx - 38, cy - 38, cx + 37, cy + 37], fill=TEAL_DIM)
    d.ellipse([cx - 35, cy - 35, cx + 34, cy + 34], fill=(18, 21, 28, 255))
    d.arc([cx - 30, cy - 30, cx + 29, cy + 29], 200, 340, fill=TEAL, width=2)


def build(path):
    img = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    panel(img)
    slot_frame(img, 360, 0, "empty")
    slot_frame(img, 400, 0, "filled")
    slot_frame(img, 440, 0, "gold")
    unknown_mark(img, 360, 50)
    for state in range(3):
        button(img, state * 100, 420, state)
    wheel_plate(img, 360, 150, False)
    wheel_plate(img, 420, 150, True)
    wheel_center(img, 360, 220)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print("wrote", path)
    return img


def preview(img):
    """見本: 画面に枠・名札・品の絵を載せる (文字は PIL の標準の字体なので位置だけの目安)"""
    items = ASSETS / "textures/item"
    sheet = img.crop((0, 0, S(W), S(H))).copy()
    d = ImageDraw.Draw(sheet)
    rows = [("tool", ["stone_whale_call", "tide_shell_call", None, None, "tide_watching_shell", None]),
            ("key", ["sky_key_fragment", "sky_key", None, "stone_whale_core", None, None])]
    y = 19
    for _label, names in rows:
        for i, name in enumerate(names):
            x = 8 + i * 18
            kind = (440, 0) if name else (360, 0)
            sheet.alpha_composite(img.crop((kind[0], kind[1], kind[0] + 36, kind[1] + 36)), (S(x) - 2, S(y + 9) - 2))
            if name and (items / f"{name}.png").exists():
                icon = Image.open(items / f"{name}.png").convert("RGBA").resize((32, 32), Image.NEAREST)
                sheet.alpha_composite(icon, (S(x), S(y + 9)))
            elif not name:
                sheet.alpha_composite(img.crop((360, 50, 392, 82)), (S(x), S(y + 9)))
        y += 27
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW)
    print("wrote", PREVIEW)


if __name__ == "__main__":
    built = build(OUT)
    if "--preview" in sys.argv:
        preview(built)
