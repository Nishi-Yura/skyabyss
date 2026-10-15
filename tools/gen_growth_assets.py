"""成長 (レベル・ステータス) の素材を作る。

使い方: python tools/gen_growth_assets.py
出力先 (src/main/resources/assets/skyabyss/ 以下):

- textures/gui/growth/status.png : ステータス画面 (512x512。左上 480x368 が 240x184 の画面の 2 倍密度)
    - 0..367 行   : 画面の背景 (骨の枠、石板、行のくぼみ、経験値バーの枠)
    - 384..415 行 : ステータスの印 32x32 ×4 (筋力・敏捷・精神・耐久)
    - 416..437 行 : ＋/－ のボタン 22x22 (＋ 通常・重ねた・押せない、－ 通常・重ねた・押せない。24 px ずつ)
    - 466..469 行 : 経験値バーの中身 (青緑)、470..473 行: 上限の金
- textures/gui/growth/hud.png : 左上の小さなバー (128x32。0..9 行が枠、10..15 行が中身、16..21 行が上限の金)
- sounds/rpg/level_up.ogg : レベルアップの音 (共鳴石に近い、澄んだ三つの鐘と低い響き)

画面の配置 (GrowthScreen.java の値と同じ) を変えたら、ここの値も変える。
何度実行しても同じ物ができる (乱数の種を固定。ogg は符号化の通し番号でバイト列だけ毎回変わるが、音は同じ)。
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/skyabyss"
GUI = ROOT / "textures/gui/growth"
SOUND = ROOT / "sounds/rpg"
rng = np.random.default_rng(20261016)

# ---------------------------------------------------------------- 色 (共鳴の炉の画面・ModText と揃える)
OUTLINE = (12, 14, 18, 255)
SLATE = (28, 32, 41, 255)
SLATE_DARK = (20, 23, 30, 255)
RECESS = (15, 17, 23, 255)
BONE_LIGHT = (226, 217, 194, 255)
BONE = (196, 186, 160, 255)
BONE_MID = (160, 150, 124, 255)
BONE_DARK = (110, 102, 84, 255)
TEAL = (127, 214, 208, 255)
TEAL_DIM = (42, 94, 106, 255)
PALE = (159, 232, 255, 255)
GOLD = (242, 193, 78, 255)

# 画面の配置 (GUI 単位。テクスチャは 2 倍)
W, H = 240, 184
ROW_Y, ROW_H = 60, 24
BAR_X, BAR_Y, BAR_W = 62, 27, 166


def S(v):
    return v * 2


def bevel_rect(d, x0, y0, x1, y1, light, mid, dark):
    """[x0, x1) × [y0, y1) を、左上が明るく右下が暗い帯で塗る"""
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=mid)
    d.line([x0, y0, x1 - 1, y0], fill=light)
    d.line([x0, y0, x0, y1 - 1], fill=light)
    d.line([x0, y1 - 1, x1 - 1, y1 - 1], fill=dark)
    d.line([x1 - 1, y0, x1 - 1, y1 - 1], fill=dark)


def recess(d, x0, y0, x1, y1, fill=RECESS):
    """くぼみ: 上と左が暗く、下と右に細い光"""
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=fill)
    d.line([x0, y0, x1 - 1, y0], fill=OUTLINE)
    d.line([x0, y0, x0, y1 - 1], fill=OUTLINE)
    d.line([x0 + 1, y1 - 1, x1 - 1, y1 - 1], fill=(52, 58, 72, 255))
    d.line([x1 - 1, y0 + 1, x1 - 1, y1 - 1], fill=(52, 58, 72, 255))


def gem(d, cx, cy, r):
    """小さな青緑の結晶 (菱形)"""
    d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=TEAL_DIM, outline=OUTLINE)
    d.polygon([(cx, cy - r + 2), (cx + r - 2, cy), (cx, cy)], fill=TEAL)
    d.point((cx - 1, cy - r + 3), fill=PALE)


def panel():
    w, h = S(W), S(H)
    img = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    # 石板: 上から下へ少し暗く、細かなむら
    yy = np.linspace(0, 1, h)[:, None]
    base = np.array(SLATE[:3], dtype=float) * (1 - yy * 0.25) + np.array(SLATE_DARK[:3], dtype=float) * (yy * 0.25)
    noise = rng.normal(0, 2.2, (h, w))[..., None]
    stone = np.clip(base[:, None, :].repeat(w, axis=1) + noise, 0, 255).astype(np.uint8)
    alpha = np.full((h, w, 1), 255, dtype=np.uint8)
    img.paste(Image.fromarray(np.concatenate([stone, alpha], axis=2), "RGBA"), (0, 0))
    d = ImageDraw.Draw(img)

    # 骨の枠: 外側の黒い線、骨の帯 (6 px)、内側の黒い線
    d.rectangle([0, 0, w - 1, h - 1], outline=OUTLINE)
    for i in range(1, 7):
        c = BONE_LIGHT if i <= 2 else BONE if i <= 4 else BONE_MID
        d.rectangle([i, i, w - 1 - i, h - 1 - i], outline=c)
    # 右と下は影
    for i in range(1, 7):
        d.line([w - 1 - i, i, w - 1 - i, h - 1 - i], fill=BONE_DARK if i <= 3 else BONE_MID)
        d.line([i, h - 1 - i, w - 1 - i, h - 1 - i], fill=BONE_DARK if i <= 3 else BONE_MID)
    d.rectangle([7, 7, w - 8, h - 8], outline=OUTLINE)
    # 肋骨の節: 骨の帯に 24 px ごとの刻み
    for x in range(24, w - 16, 24):
        d.line([x, 2, x, 6], fill=BONE_DARK)
        d.line([x + 1, 2, x + 1, 6], fill=BONE_LIGHT)
        d.line([x, h - 7, x, h - 3], fill=BONE_DARK)
    for y in range(24, h - 16, 24):
        d.line([2, y, 6, y], fill=BONE_DARK)
        d.line([w - 7, y, w - 3, y], fill=BONE_DARK)
    # 四隅の結晶
    for cx, cy in [(7, 7), (w - 8, 7), (7, h - 8), (w - 8, h - 8)]:
        gem(d, cx, cy, 6)

    # 題の下の細い線と、中央の飾り
    ty = S(19)
    d.line([S(16), ty, w - S(16), ty], fill=TEAL_DIM)
    d.line([S(40), ty + 1, w - S(40), ty + 1], fill=(30, 60, 70, 255))
    gem(d, w // 2, ty, 4)

    # 経験値バーの枠 (骨の縁のくぼみ)
    bx0, by0 = S(BAR_X), S(BAR_Y)
    bx1, by1 = S(BAR_X + BAR_W), S(BAR_Y + 6)
    d.rectangle([bx0, by0, bx1 - 1, by1 - 1], fill=BONE_MID, outline=OUTLINE)
    d.line([bx0 + 1, by0 + 1, bx1 - 2, by0 + 1], fill=BONE_LIGHT)
    recess(d, bx0 + 3, by0 + 3, bx1 - 3, by1 - 3)

    # ステータスの行のくぼみと、印の台
    for i in range(4):
        ry = S(ROW_Y + i * ROW_H)
        recess(d, S(8), ry, S(W - 8), ry + S(ROW_H - 2), fill=(19, 22, 29, 255))
        # 印の台 (骨の縁)
        ix, iy = S(9), ry + S(3)
        d.rectangle([ix, iy, ix + 35, iy + 35], fill=BONE_DARK, outline=OUTLINE)
        d.rectangle([ix + 2, iy + 2, ix + 33, iy + 33], fill=RECESS)
        # 行の右端に、ボタンの下地の細い線
        d.line([S(196), ry + 6, S(196), ry + S(ROW_H - 2) - 6], fill=(40, 46, 58, 255))

    # 下の区切り
    sy = S(157)
    d.line([S(10), sy, w - S(10), sy], fill=TEAL_DIM)
    d.line([S(10), sy + 1, w - S(10), sy + 1], fill=OUTLINE)
    return img


def outline_sprite(spr):
    """不透明な画素のまわりに 1 px の黒い縁を付ける"""
    a = np.array(spr)
    op = a[..., 3] > 0
    grown = op.copy()
    for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
        grown |= np.roll(np.roll(op, dy, axis=0), dx, axis=1)
    edge = grown & ~op
    a[edge] = OUTLINE
    return Image.fromarray(a, "RGBA")


def icon_strength():
    """筋力: 鯨骨の大剣 (斜め)"""
    s = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)
    # 刃
    d.polygon([(25, 3), (28, 4), (12, 20), (9, 19)], fill=BONE)
    d.polygon([(25, 3), (28, 4), (27, 6), (11, 21)], fill=BONE_MID)
    d.line([(25, 4), (10, 19)], fill=BONE_LIGHT)
    # 鍔
    d.polygon([(6, 16), (9, 13), (17, 21), (14, 24)], fill=(176, 120, 60, 255))
    d.line([(7, 16), (9, 14)], fill=GOLD)
    # 柄と柄頭
    d.line([(10, 21), (5, 26)], fill=(110, 70, 40, 255), width=3)
    d.ellipse([2, 25, 6, 29], fill=GOLD)
    return outline_sprite(s)


def icon_agility():
    """敏捷: 風を切る羽"""
    s = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)
    vane = [(26, 3), (29, 6), (24, 16), (16, 24), (8, 27), (10, 20), (16, 12)]
    d.polygon(vane, fill=(88, 176, 160, 255))
    d.polygon([(26, 3), (29, 6), (24, 16), (17, 22), (20, 13)], fill=(127, 214, 190, 255))
    # 切れ込み
    for x0, y0 in [(22, 17), (17, 21), (13, 24)]:
        d.line([(x0, y0), (x0 + 3, y0 + 2)], fill=(0, 0, 0, 0))
    # 軸
    d.line([(27, 5), (5, 28)], fill=BONE_LIGHT, width=1)
    d.line([(8, 25), (4, 29)], fill=BONE, width=2)
    # 風の線
    d.line([(3, 9), (10, 9)], fill=PALE)
    d.line([(5, 13), (11, 13)], fill=(110, 170, 190, 255))
    return outline_sprite(s)


def icon_spirit():
    """精神: 三日月と小さな星"""
    s = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)
    d.ellipse([4, 4, 26, 26], fill=(190, 214, 255, 255))
    d.ellipse([10, 1, 31, 22], fill=(0, 0, 0, 0))
    # 月の縁の光
    d.arc([4, 4, 26, 26], 100, 220, fill=(235, 245, 255, 255))
    d.point([(9, 20), (11, 23), (8, 15)], fill=(140, 160, 220, 255))
    # 星
    cx, cy = 23, 22
    d.polygon([(cx, cy - 5), (cx + 1, cy - 1), (cx + 5, cy), (cx + 1, cy + 1), (cx, cy + 5), (cx - 1, cy + 1),
               (cx - 5, cy), (cx - 1, cy - 1)], fill=PALE)
    d.point((cx, cy), fill=(255, 255, 255, 255))
    return outline_sprite(s)


def icon_vitality():
    """耐久: 石の殻に包まれた心臓"""
    s = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(s)
    d.ellipse([3, 5, 17, 19], fill=(196, 60, 52, 255))
    d.ellipse([15, 5, 29, 19], fill=(196, 60, 52, 255))
    d.polygon([(4, 14), (28, 14), (16, 28)], fill=(196, 60, 52, 255))
    # 影と光
    d.polygon([(16, 28), (28, 14), (27, 18), (17, 27)], fill=(140, 36, 36, 255))
    d.ellipse([6, 8, 10, 12], fill=(240, 130, 110, 255))
    # 殻のひび (石の色)
    d.line([(16, 10), (14, 15), (17, 19), (15, 24)], fill=(120, 110, 100, 255))
    d.line([(22, 9), (20, 13)], fill=(120, 110, 100, 255))
    return outline_sprite(s)


def small_button(plus, state):
    """＋/－ のボタン 22x22。state: 0 通常、1 重ねた、2 押せない"""
    b = Image.new("RGBA", (22, 22), (0, 0, 0, 0))
    d = ImageDraw.Draw(b)
    rim = [BONE, BONE_LIGHT, (90, 92, 100, 255)][state]
    fill = [(24, 28, 36, 255), TEAL_DIM, (22, 24, 28, 255)][state]
    sym = [TEAL, (220, 252, 255, 255), (80, 86, 96, 255)][state]
    d.rectangle([0, 0, 21, 21], fill=OUTLINE)
    d.rectangle([1, 1, 20, 20], fill=rim)
    d.rectangle([3, 3, 18, 18], fill=fill)
    d.rectangle([5, 10, 16, 11], fill=sym)
    if plus:
        d.rectangle([10, 5, 11, 16], fill=sym)
    return b


def strip(width, height, top, bottom):
    a = np.zeros((height, width, 4), dtype=np.uint8)
    for y in range(height):
        t = y / max(1, height - 1)
        a[y, :, :3] = (np.array(top) * (1 - t) + np.array(bottom) * t).astype(np.uint8)
        a[y, :, 3] = 255
    # 細かな明暗 (流れる光のような)
    a[:, ::6, :3] = np.clip(a[:, ::6, :3].astype(int) + 18, 0, 255).astype(np.uint8)
    return Image.fromarray(a, "RGBA")


def status_texture():
    img = panel()
    for i, fn in enumerate([icon_strength, icon_agility, icon_spirit, icon_vitality]):
        img.paste(fn(), (i * 32, 384))
    for plus in (True, False):
        for state in range(3):
            img.paste(small_button(plus, state), ((0 if plus else 72) + state * 24, 416))
    img.paste(strip(S(BAR_W - 4), 4, (191, 244, 255), (79, 184, 200)), (0, 466))
    img.paste(strip(S(BAR_W - 4), 4, (255, 224, 138), (224, 160, 48)), (0, 470))
    return img


def hud_texture():
    img = Image.new("RGBA", (128, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 枠: 黒い縁、骨の細い帯、中はくぼみ
    d.rectangle([0, 0, 127, 9], fill=OUTLINE)
    d.rectangle([1, 1, 126, 8], fill=BONE_MID)
    d.line([1, 1, 126, 1], fill=BONE_LIGHT)
    d.rectangle([2, 2, 125, 7], fill=RECESS)
    img.paste(strip(124, 6, (191, 244, 255), (63, 160, 180)), (0, 10))
    img.paste(strip(124, 6, (255, 224, 138), (210, 150, 40)), (0, 16))
    return img


# ---------------------------------------------------------------- 音
SR = 44100


def bell(freq, seconds, decay):
    t = np.arange(int(SR * seconds)) / SR
    # 鐘らしい、整数倍でない倍音
    partials = [(1.0, 1.0), (2.76, 0.35), (5.40, 0.12), (0.5, 0.25)]
    out = np.zeros_like(t)
    for mul, amp in partials:
        out += amp * np.sin(2 * np.pi * freq * mul * t) * np.exp(-t * decay * (0.6 + mul * 0.4))
    attack = np.clip(t / 0.006, 0, 1)
    return out * attack


def reverb(x, seconds=2.2, mix=0.35):
    m = int(SR * seconds)
    ir = rng.standard_normal(m) * np.exp(-np.arange(m) / (SR * seconds / 6.0))
    ir = np.fft.irfft(np.fft.rfft(ir) * np.exp(-np.fft.rfftfreq(m, 1 / SR) / 3000), m)
    ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x) + m
    wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)
    dry = np.concatenate([x, np.zeros(m)])
    return dry * (1 - mix) + wet * mix


def level_up_sound():
    total = 2.6
    out = np.zeros(int(SR * total))
    # 三つの澄んだ鐘が、少しずつ上がる (ラ・ミ・ラ)
    for i, (f, start, amp) in enumerate([(440.0, 0.0, 0.55), (659.25, 0.11, 0.5), (880.0, 0.22, 0.45)]):
        b = bell(f, total - start, 2.2) * amp
        s = int(SR * start)
        out[s:s + len(b)] += b[:len(out) - s]
    # 底に低い響き (巨神の鼓動のような、ゆっくりふくらむ音)
    t = np.arange(len(out)) / SR
    hum = np.sin(2 * np.pi * 110 * t) * 0.18 * np.sin(np.pi * np.clip(t / 1.6, 0, 1)) ** 2
    out += hum
    out = reverb(out)[:int(SR * (total + 0.8))]
    fade = np.ones_like(out)
    tail = int(SR * 0.6)
    fade[-tail:] = np.linspace(1, 0, tail)
    out *= fade
    out /= np.max(np.abs(out)) + 1e-9
    return (out * 0.8).astype(np.float32)


def main():
    import soundfile as sf

    GUI.mkdir(parents=True, exist_ok=True)
    SOUND.mkdir(parents=True, exist_ok=True)
    status_texture().save(GUI / "status.png")
    hud_texture().save(GUI / "hud.png")
    sf.write(SOUND / "level_up.ogg", level_up_sound(), SR, format="OGG", subtype="VORBIS")
    print("wrote", GUI / "status.png", GUI / "hud.png", SOUND / "level_up.ogg")


if __name__ == "__main__":
    main()
