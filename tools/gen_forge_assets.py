"""共鳴の炉の画面の素材を作る (装備の第2段階: 強化・再鍛造・交換)。

使い方: python tools/gen_forge_assets.py
出力先: src/main/resources/assets/skyabyss/textures/gui/forge/forge.png (1024x512、2 倍密度)

    - 0..331 行、0..351 列   : 左の板 (176x166)。骨の枠、石板、装備の枠と炉の光、持ち物の枠
    - 0..331 行、360..663 列 : 右の板 (152x166)。骨の枠、つまみの台、中身のくぼみ
    - 352..379 行           : つまみ 48x14 (2 倍で 96x28)。押せる・重ねた・選ばれた の 3 つを 100 px ずつ
    - 384..407 行           : つまみの印 12x12 (2 倍で 24x24)。強化 (槌)・再鍛造 (渦)・交換 (天秤) を 32 px ずつ
    - 416..477 行           : 候補の札の枠 136x31 (2 倍で 272x62)。ふつう・重ねた・選んだ の 3 つを 280 px おきに

色は成長・技の画面 (gen_growth_assets.py) と同じ石と骨。画面の配置は ResonanceForgeScreen.java の値と同じにする。
何度実行しても同じ物ができる (乱数の種を固定)。
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss"
OUT = ROOT / "textures/gui/forge/forge.png"
rng = np.random.default_rng(20261017)

OUTLINE = (12, 14, 18, 255)
SLATE = (28, 32, 41, 255)
SLATE_DARK = (20, 23, 30, 255)
RECESS = (15, 17, 23, 255)
RECESS_EDGE = (52, 58, 72, 255)
BONE_LIGHT = (226, 217, 194, 255)
BONE = (196, 186, 160, 255)
BONE_MID = (160, 150, 124, 255)
BONE_DARK = (110, 102, 84, 255)
TEAL = (127, 214, 208, 255)
TEAL_DIM = (42, 94, 106, 255)
PALE = (159, 232, 255, 255)
GOLD = (242, 193, 78, 255)
VIOLET = (184, 156, 255, 255)

# 配置 (GUI 単位。テクスチャは 2 倍)
MAIN_W, H = 176, 166
SIDE_X, SIDE_W = 180, 152
GEAR = (44, 35)
TAB_W, TAB_H = 48, 14
CARD_W, CARD_H = 136, 31


def S(v):
    return v * 2


def stone(w, h):
    yy = np.linspace(0, 1, h)[:, None]
    base = np.array(SLATE[:3], dtype=float) * (1 - yy * 0.25) + np.array(SLATE_DARK[:3], dtype=float) * (yy * 0.25)
    noise = rng.normal(0, 2.2, (h, w))[..., None]
    rgb = np.clip(base[:, None, :].repeat(w, axis=1) + noise, 0, 255).astype(np.uint8)
    alpha = np.full((h, w, 1), 255, dtype=np.uint8)
    return Image.fromarray(np.concatenate([rgb, alpha], axis=2), "RGBA")


def gem(d, cx, cy, r, fill=TEAL_DIM, light=TEAL):
    d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=fill, outline=OUTLINE)
    d.polygon([(cx, cy - r + 2), (cx + r - 2, cy), (cx, cy)], fill=light)
    d.point((cx - 1, cy - r + 3), fill=PALE)


def bone_frame(d, x0, y0, w, h, band=6):
    """骨の枠 (成長の画面と同じ作り)。x0, y0, w, h は 2 倍の px"""
    x1, y1 = x0 + w - 1, y0 + h - 1
    d.rectangle([x0, y0, x1, y1], outline=OUTLINE)
    for i in range(1, band + 1):
        c = BONE_LIGHT if i <= 2 else BONE if i <= 4 else BONE_MID
        d.rectangle([x0 + i, y0 + i, x1 - i, y1 - i], outline=c)
    for i in range(1, band + 1):
        shade = BONE_DARK if i <= 3 else BONE_MID
        d.line([x1 - i, y0 + i, x1 - i, y1 - i], fill=shade)
        d.line([x0 + i, y1 - i, x1 - i, y1 - i], fill=shade)
    d.rectangle([x0 + band + 1, y0 + band + 1, x1 - band - 1, y1 - band - 1], outline=OUTLINE)
    for x in range(x0 + 24, x1 - 16, 24):
        d.line([x, y0 + 2, x, y0 + band], fill=BONE_DARK)
        d.line([x + 1, y0 + 2, x + 1, y0 + band], fill=BONE_LIGHT)
        d.line([x, y1 - band, x, y1 - 2], fill=BONE_DARK)
    for y in range(y0 + 24, y1 - 16, 24):
        d.line([x0 + 2, y, x0 + band, y], fill=BONE_DARK)
        d.line([x1 - band, y, x1 - 2, y], fill=BONE_DARK)
    for cx, cy in [(x0 + 7, y0 + 7), (x1 - 7, y0 + 7), (x0 + 7, y1 - 7), (x1 - 7, y1 - 7)]:
        gem(d, cx, cy, 6)


def recess(d, x0, y0, x1, y1, fill=RECESS):
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=fill)
    d.line([x0, y0, x1 - 1, y0], fill=OUTLINE)
    d.line([x0, y0, x0, y1 - 1], fill=OUTLINE)
    d.line([x0 + 1, y1 - 1, x1 - 1, y1 - 1], fill=RECESS_EDGE)
    d.line([x1 - 1, y0 + 1, x1 - 1, y1 - 1], fill=RECESS_EDGE)


def slot(d, gx, gy):
    """持ち物の枠 18x18 (GUI 単位の左上 = 枠の中身の 1 つ外)"""
    recess(d, S(gx - 1), S(gy - 1), S(gx + 17), S(gy + 17))
    # 枠の内側のふち (下と右にかすかな光)
    d.line([S(gx - 1) + 2, S(gy + 17) - 2, S(gx + 17) - 2, S(gy + 17) - 2], fill=(30, 34, 44, 255))


def main_panel(img):
    w, h = S(MAIN_W), S(H)
    img.paste(stone(w, h), (0, 0))
    d = ImageDraw.Draw(img)
    bone_frame(d, 0, 0, w, h)
    # 装備の枠のまわりに炉の光 (上が青緑、下へ石に溶ける)
    gx0, gy0, gx1, gy1 = S(8), S(16), S(66), S(74)
    for y in range(gy0, gy1):
        t = (y - gy0) / (gy1 - gy0)
        c = tuple(int(TEAL_DIM[i] * (1 - t) * 0.75 + SLATE[i] * (1 - (1 - t) * 0.75)) for i in range(3)) + (255,)
        d.line([gx0, y, gx1 - 1, y], fill=c)
    d.rectangle([gx0, gy0, gx1 - 1, gy1 - 1], outline=OUTLINE)
    # 炉の口: 装備の枠の下に、熾の細い弧
    for i, c in enumerate([(90, 60, 40, 255), (150, 96, 52, 255), GOLD]):
        d.arc([S(GEAR[0]) - 10 + i * 2, S(GEAR[1] + 18) - 6 + i, S(GEAR[0] + 16) + 10 - i * 2, S(GEAR[1] + 18) + 14 - i], 20, 160, fill=c, width=2)
    slot(d, *GEAR)
    # 装備の枠は二重の縁 (淡い青)
    d.rectangle([S(GEAR[0] - 3), S(GEAR[1] - 3), S(GEAR[0] + 19) - 1, S(GEAR[1] + 19) - 1], outline=PALE)
    # 情報の欄: 薄いくぼみ
    recess(d, S(69), S(16), S(169), S(75), fill=(22, 25, 33, 255))
    # 持ち物との境の線と飾り (「持ち物」の文字 (y 72..80) の右から)
    ty = S(77)
    d.line([S(70), ty, w - S(10), ty], fill=TEAL_DIM)
    gem(d, S(120), ty, 4)
    for row in range(3):
        for col in range(9):
            slot(d, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        slot(d, 8 + col * 18, 142)


def side_panel(img):
    x0 = S(SIDE_X)
    w, h = S(SIDE_W), S(H)
    img.paste(stone(w, h), (x0, 0))
    d = ImageDraw.Draw(img)
    bone_frame(d, x0, 0, w, h)
    # つまみの台の下の線
    ty = S(20)
    d.line([x0 + S(6), ty, x0 + w - S(6), ty], fill=TEAL_DIM)
    d.line([x0 + S(20), ty + 1, x0 + w - S(20), ty + 1], fill=(30, 60, 70, 255))
    # 中身のくぼみ
    recess(d, x0 + S(6), S(23), x0 + w - S(6), h - S(6), fill=(22, 25, 33, 255))


def tab(img, ox, oy, state):
    """つまみ: 骨の縁の小さな札。state 0 押せる / 1 重ねた / 2 選ばれた"""
    w, h = S(TAB_W), S(TAB_H)
    d = ImageDraw.Draw(img)
    body = {0: (34, 38, 48, 255), 1: (44, 52, 64, 255), 2: (30, 70, 80, 255)}[state]
    d.rectangle([ox, oy, ox + w - 1, oy + h - 1], fill=body, outline=OUTLINE)
    edge = BONE_MID if state == 0 else BONE if state == 1 else BONE_LIGHT
    d.rectangle([ox + 1, oy + 1, ox + w - 2, oy + h - 2], outline=edge)
    d.line([ox + 2, oy + h - 2, ox + w - 3, oy + h - 2], fill=BONE_DARK)
    if state == 2:
        d.line([ox + 4, oy + h - 4, ox + w - 5, oy + h - 4], fill=TEAL)


def icon_hammer(d, ox, oy):
    d.rectangle([ox + 4, oy + 4, ox + 17, oy + 10], fill=BONE, outline=OUTLINE)
    d.line([ox + 5, oy + 5, ox + 16, oy + 5], fill=BONE_LIGHT)
    d.rectangle([ox + 10, oy + 10, ox + 13, oy + 21], fill=(120, 90, 60, 255), outline=OUTLINE)
    d.point((ox + 19, oy + 6), fill=GOLD)
    d.point((ox + 20, oy + 9), fill=GOLD)


def icon_swirl(d, ox, oy):
    cx, cy = ox + 12, oy + 12
    for r, c in [(9, TEAL_DIM), (6, TEAL), (3, PALE)]:
        d.arc([cx - r, cy - r, cx + r, cy + r], 30, 300, fill=c, width=2)
    d.point((cx, cy), fill=PALE)


def icon_scale(d, ox, oy):
    d.line([ox + 12, oy + 3, ox + 12, oy + 20], fill=BONE, width=2)
    d.line([ox + 4, oy + 7, ox + 20, oy + 7], fill=BONE, width=2)
    d.polygon([(ox + 2, oy + 14), (ox + 8, oy + 14), (ox + 5, oy + 9)], outline=TEAL)
    d.polygon([(ox + 16, oy + 14), (ox + 22, oy + 14), (ox + 19, oy + 9)], outline=TEAL)
    d.line([ox + 8, oy + 21, ox + 16, oy + 21], fill=BONE_MID, width=2)
    gem(d, ox + 12, oy + 4, 3, fill=(120, 96, 40, 255), light=GOLD)


def card(img, ox, oy, state):
    """候補の札。state 0 ふつう / 1 重ねた / 2 選んだ (金の縁)"""
    w, h = S(CARD_W), S(CARD_H)
    d = ImageDraw.Draw(img)
    body = {0: (26, 30, 39, 255), 1: (32, 38, 49, 255), 2: (36, 34, 30, 255)}[state]
    d.rectangle([ox, oy, ox + w - 1, oy + h - 1], fill=body, outline=OUTLINE)
    edge = {0: (58, 64, 80, 255), 1: TEAL_DIM, 2: GOLD}[state]
    d.rectangle([ox + 1, oy + 1, ox + w - 2, oy + h - 2], outline=edge)
    # 左の細い帯 (碑文の青緑、選んだら金)
    d.rectangle([ox + 3, oy + 4, ox + 4, oy + h - 5], fill=GOLD if state == 2 else TEAL_DIM)


def build():
    img = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    main_panel(img)
    side_panel(img)
    for state in range(3):
        tab(img, state * 100, 352, state)
    d = ImageDraw.Draw(img)
    icon_hammer(d, 0, 384)
    icon_swirl(d, 32, 384)
    icon_scale(d, 64, 384)
    # 札は 3 つを横に (280 px おき)
    card(img, 0, 416, 0)
    card(img, 280, 416, 1)
    card(img, 560, 416, 2)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    build()
