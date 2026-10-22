"""潮殻の心臓の段階ごとのボスバー (枠と顔の印) を描く。岩鯨の心臓のバーと同じ寸法・同じ仕組み。

使い方: python tools/gen/world/gen_tide_heart_bar.py
出力: src/main/resources/assets/skyabyss/textures/gui/boss/tide_heart_frame_p1..3.png (182x16)
      src/main/resources/assets/skyabyss/textures/gui/boss/tide_heart_icon_p1..3.png  (16x16)
確認: docs/review/previews/tide_heart_bar.png (4 倍に拡大し、帯の色を塗った見本)

寸法 (StoneWhaleHeartBar / TideHeartBar と同じ):
- 枠は 0〜11 行。帯の溝は x 3〜177、y 3〜8 (帯の色はゲームの側で塗る)。12〜15 行は段階の境の印 (66% と 33%)
色 (08 の 5.3節):
- 段階 1 閉じた殻: 珊瑚の赤の縁、青緑の内側の線、縁にフジツボの粒。帯は青緑
- 段階 2 開いた殻: 真珠の白の縁、薄紫の影、縁に波の模様。帯は真珠の白
- 段階 3 月の潮:   深い青の縁、銀の線、両端に三日月、境の印は水滴。帯は月の銀青
14.2節の色案 (heart/bars.png) では段階 2 が紅。5.3節の白を採った (光る部屋の段階の見た目と合わせる)。
差し替えるときは TideHeartBar.FILL と、このファイルの P2 の色を変える
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/boss"
PREVIEW = ROOT / "docs/review/previews/tide_heart_bar.png"
W, H = 182, 16
TRACK = (3, 3, 178, 9)          # x0, y0, x1, y1 (帯の溝)
DARK = (12, 16, 24)
GROOVE = (19, 24, 34)

# 段階ごとの色: 外の縁、内の縁、溝の縁、飾り
STYLE = {
    1: dict(outer=(122, 44, 52), rim=(214, 96, 98), inner=(84, 196, 188), deco=[(255, 160, 98), (242, 238, 232), (186, 120, 230)]),
    2: dict(outer=(150, 132, 156), rim=(240, 230, 236), inner=(196, 178, 206), deco=[(255, 252, 250), (216, 202, 224)]),
    3: dict(outer=(18, 30, 74), rim=(58, 82, 150), inner=(200, 220, 250), deco=[(228, 238, 255), (160, 252, 244)]),
}
# 帯の色 (見本だけ。ゲームの色は TideHeartBar.FILL)
FILL = {1: (96, 220, 210), 2: (236, 228, 234), 3: (170, 200, 255)}
MARKS = [round(TRACK[0] + (TRACK[2] - TRACK[0]) * f) for f in (0.33, 0.66)]


def frame(ph):
    s = STYLE[ph]
    a = np.zeros((H, W, 4), np.uint8)

    def px(x, y, c):
        if 0 <= x < W and 0 <= y < H:
            a[y, x] = (*c, 255)

    # 縁: 外 (0 と 11 行・両端) → 内の縁 (1 と 10 行) → 溝の縁 (2 と 9 行)
    for y in range(0, 12):
        for x in range(W):
            edge = min(y, 11 - y, x, W - 1 - x)
            if edge == 0:
                c = s["outer"]
            elif edge == 1:
                c = s["rim"]
            elif edge == 2:
                c = DARK
            else:
                c = GROOVE
            px(x, y, c)
    for corner in ((0, 0), (W - 1, 0), (0, 11), (W - 1, 11)):
        a[corner[1], corner[0]] = (0, 0, 0, 0)
    # 溝の上の縁に、内の線 (段階の色)
    for x in range(3, W - 3):
        px(x, 2, s["inner"])
    if ph == 1:
        # フジツボ: 上下の縁に 2 画素の粒を、間をあけて並べる
        for i, x in enumerate(range(9, W - 6, 13)):
            c = s["deco"][i % len(s["deco"])]
            y = 0 if i % 2 == 0 else 11
            px(x, y, c)
            px(x + 1, y, c)
            px(x, 1 if y == 0 else 10, tuple(int(v * 0.75) for v in c))
    elif ph == 2:
        # 波: 上の縁に寄せては返す明暗、下の縁に泡の点
        for x in range(1, W - 1):
            if (x // 4) % 3 == 0:
                px(x, 0, s["deco"][0])
            if x % 9 == 4:
                px(x, 11, s["deco"][1])
                px(x, 10, (255, 255, 255))
    else:
        # 両端に三日月 (縁から外へは出さず、縁の内側に銀の弧)
        for x0, flip in ((0, False), (W - 6, True)):
            for (dx, dy) in [(1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (2, 2), (2, 9), (3, 2), (3, 9)]:
                x = x0 + (5 - dx if flip else dx)
                px(x, dy, s["deco"][0])
        # 上の縁に星のような点
        for x in range(12, W - 12, 17):
            px(x, 0, s["deco"][1])
    # 段階の境の印 (12〜15 行)
    for i, mx in enumerate(MARKS):
        if ph == 1:
            shape = [(0, 12), (-1, 13), (0, 13), (1, 13), (-2, 14), (-1, 14), (0, 14), (1, 14), (2, 14)]
            col = s["rim"]
        elif ph == 2:
            shape = [(0, 12), (-1, 13), (0, 13), (1, 13), (0, 14)]
            col = s["deco"][0]
        else:
            shape = [(0, 12), (0, 13), (-1, 14), (0, 14), (1, 14), (0, 15)]
            col = s["deco"][1]
        for dx, y in shape:
            px(mx + dx, y, col)
        # 境の上の溝の縁を一画素明るく
        px(mx, 9, s["inner"])
    return Image.fromarray(a, "RGBA")


def icon(ph):
    """顔の印 (16x16): 巻貝の殻と、中の核"""
    a = np.zeros((16, 16, 4), np.uint8)
    shell = (216, 202, 224)
    shell_d = (150, 132, 168)
    core = {1: (120, 246, 222), 2: (120, 246, 222), 3: (168, 204, 255)}[ph]
    cx, cy = 7.5, 8.0
    turns = {1: 2.0, 2: 1.25, 3: 0.6}[ph]
    for k in range(400):
        t = k / 399 * turns
        r = 2.4 + 2.6 * t
        th = 2 * np.pi * t + 0.6
        x, y = cx + r * np.cos(th), cy + r * np.sin(th)
        w = 0.6 + 0.6 * t
        for yy in range(16):
            for xx in range(16):
                if (xx + 0.5 - x) ** 2 + (yy + 0.5 - y) ** 2 <= w * w:
                    a[yy, xx] = (*shell, 255)
    # 影 (右下)
    for yy in range(15, -1, -1):
        for xx in range(15, -1, -1):
            if a[yy, xx, 3] and (yy + 1 >= 16 or xx + 1 >= 16 or not a[min(15, yy + 1), min(15, xx + 1), 3]):
                a[yy, xx, :3] = shell_d
    # 核
    for yy in range(16):
        for xx in range(16):
            d = (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2
            if d <= 2.6:
                a[yy, xx] = (*core, 255)
            if d <= 0.6:
                a[yy, xx] = (250, 252, 250, 255)
    if ph == 1:
        for (xx, yy), c in (((2, 3), (255, 118, 128)), ((13, 4), (255, 200, 120)), ((12, 13), (186, 120, 230))):
            a[yy, xx] = (*c, 255)
    if ph == 2:
        # 破れた所から光る部屋
        for (xx, yy) in [(11, 9), (12, 9), (12, 10), (11, 11), (12, 11)]:
            a[yy, xx] = (160, 252, 244, 255)
    if ph == 3:
        # 三日月 (左上)
        for (xx, yy) in [(1, 3), (1, 4), (1, 5), (2, 2), (2, 6), (3, 1), (3, 7), (4, 1)]:
            a[yy, xx] = (228, 238, 255, 255)
        for j in range(8):
            ang = 2 * np.pi * j / 8
            xx, yy = int(cx + 6.8 * np.cos(ang)), int(cy + 6.8 * np.sin(ang))
            if 0 <= xx < 16 and 0 <= yy < 16 and not a[yy, xx, 3]:
                a[yy, xx] = (160, 252, 244, 255)
    return Image.fromarray(a, "RGBA")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for ph in (1, 2, 3):
        f = frame(ph)
        ic = icon(ph)
        f.save(OUT / f"tide_heart_frame_p{ph}.png")
        ic.save(OUT / f"tide_heart_icon_p{ph}.png")
        # 見本: 帯を (段階 1 は 85%、2 は 50%、3 は 20%) 塗る
        sample = Image.new("RGBA", (W + 22, 20), (24, 26, 34, 255))
        sample.alpha_composite(ic, (0, 2))
        bar = f.copy()
        fill = {1: 0.85, 2: 0.5, 3: 0.2}[ph]
        n = round((TRACK[2] - TRACK[0]) * fill)
        arr = np.array(bar)
        arr[TRACK[1]:TRACK[3], TRACK[0]:TRACK[0] + n] = (*FILL[ph], 255)
        arr[TRACK[1], TRACK[0]:TRACK[0] + n] = (*[min(255, v + 50) for v in FILL[ph]], 255)
        sample.alpha_composite(Image.fromarray(arr, "RGBA"), (20, 2))
        rows.append(sample)
    sheet = Image.new("RGBA", (rows[0].width, 20 * 3), (24, 26, 34, 255))
    for i, r in enumerate(rows):
        sheet.paste(r, (0, 20 * i))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(PREVIEW)
    print("wrote", OUT, PREVIEW)


if __name__ == "__main__":
    main()
