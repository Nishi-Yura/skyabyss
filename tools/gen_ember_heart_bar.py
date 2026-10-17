"""余燼の心臓の段階ごとのボスバー (枠と顔の印) を描く。岩鯨・潮殻の心臓のバーと同じ寸法・同じ仕組み (PhasedBossBar)。

使い方: python tools/gen_ember_heart_bar.py
出力: src/main/resources/assets/skyabyss/textures/gui/boss/ember_heart_frame_p1..3.png (182x16)
      src/main/resources/assets/skyabyss/textures/gui/boss/ember_heart_icon_p1..3.png  (16x16)
確認: docs/review/previews/ember_heart_bar.png (4 倍に拡大し、帯の色を塗った見本)

寸法 (PhasedBossBar): 枠は 0〜11 行。帯の溝は x 3〜177、y 3〜8 (帯の色はゲームの側 EmberHeartClient.BAR で塗る)。12〜15 行は段階の境の印 (66% と 33%)
色 (13 の 5.3節、案は docs/review/ember_roughs/heart/bars.png):
- 段階 1 熔岩の殻:     黒い玄武岩の縁、橙の割れ目の線。帯は熾の橙
- 段階 2 熔け落ちる殻: 白熱の黄の縁、縁から垂れる滴。帯は熔けた黄
- 段階 3 余燼:         灰の白の縁、暗い赤の残り火の点、境の印は火の粉。帯は灰の白とくすんだ赤 (帯はくすんだ赤、枠が灰の白)
差し替えるときは EmberHeartClient.BAR と、このファイルの STYLE・FILL を変える
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/boss"
PREVIEW = ROOT / "docs/review/previews/ember_heart_bar.png"
W, H = 182, 16
TRACK = (3, 3, 178, 9)          # x0, y0, x1, y1 (帯の溝)
DARK = (12, 16, 24)
GROOVE = (19, 24, 34)

# 段階ごとの色: 外の縁、内の縁、溝の縁、飾り
STYLE = {
    1: dict(outer=(30, 26, 30), rim=(86, 78, 80), inner=(246, 126, 40), deco=[(255, 190, 80), (200, 70, 30), (255, 230, 150)]),
    2: dict(outer=(150, 100, 30), rim=(255, 230, 130), inner=(255, 214, 96), deco=[(255, 250, 200), (255, 190, 60)]),
    3: dict(outer=(110, 104, 98), rim=(232, 226, 216), inner=(150, 42, 32), deco=[(216, 90, 70), (240, 236, 228)]),
}
# 帯の色 (見本だけ。ゲームの色は EmberHeartClient.BAR)
FILL = {1: (246, 126, 40), 2: (255, 214, 96), 3: (150, 42, 32)}
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
        # 割れ目: 上下の縁に橙の短い筋を、間をあけて並べる
        for i, x in enumerate(range(8, W - 6, 11)):
            c = s["deco"][i % len(s["deco"])]
            y = 0 if i % 2 == 0 else 11
            for dx in range(3):
                px(x + dx, y, c)
            px(x + 1, 1 if y == 0 else 10, tuple(int(v * 0.7) for v in c))
    elif ph == 2:
        # 垂れる滴: 下の縁から短く垂れる黄の点、上の縁に白熱の明暗
        for x in range(1, W - 1):
            if (x // 5) % 3 == 0:
                px(x, 0, s["deco"][0])
            if x % 11 == 5:
                px(x, 11, s["deco"][1])
                px(x, 10, s["deco"][0])
    else:
        # 灰の縁に、暗い赤の残り火の点
        for x in range(10, W - 10, 19):
            px(x, 0, s["deco"][0])
            px(x + 1, 11, s["deco"][1])
        for x0, flip in ((0, False), (W - 4, True)):
            for (dx, dy) in [(1, 4), (1, 5), (2, 3), (2, 6), (2, 7)]:
                px(x0 + (3 - dx if flip else dx), dy, s["deco"][0])
    # 段階の境の印 (12〜15 行)
    for i, mx in enumerate(MARKS):
        if ph == 1:
            shape = [(0, 12), (-1, 13), (0, 13), (1, 13), (-1, 14), (0, 14), (1, 14)]
            col = s["inner"]
        elif ph == 2:
            shape = [(0, 12), (0, 13), (-1, 14), (0, 14), (1, 14), (0, 15)]
            col = s["deco"][0]
        else:
            shape = [(0, 12), (-1, 13), (1, 13), (0, 14)]
            col = s["deco"][0]
        for dx, y in shape:
            px(mx + dx, y, col)
        # 境の上の溝の縁を一画素明るく
        px(mx, 9, s["inner"])
    return Image.fromarray(a, "RGBA")


def icon(ph):
    """顔の印 (16x16): 殻の丸と、中の核 (段階 1 は割れ目の走る黒い玉、段階 2 は熔けて垂れる白熱の玉、段階 3 は灰の殻と暗い残り火)"""
    a = np.zeros((16, 16, 4), np.uint8)
    cx, cy = 7.5, 7.0
    shell = {1: (52, 46, 50), 2: (120, 76, 40), 3: (206, 200, 190)}[ph]
    shell_d = {1: (28, 24, 28), 2: (80, 48, 28), 3: (140, 134, 126)}[ph]
    core = {1: (246, 126, 40), 2: (255, 226, 120), 3: (160, 40, 30)}[ph]
    for yy in range(16):
        for xx in range(16):
            d = (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2
            if d <= 30:
                a[yy, xx] = (*shell, 255)
    # 影 (右下)
    for yy in range(15, -1, -1):
        for xx in range(15, -1, -1):
            if a[yy, xx, 3] and (yy + 1 >= 16 or xx + 1 >= 16 or not a[min(15, yy + 1), min(15, xx + 1), 3]):
                a[yy, xx, :3] = shell_d
    if ph == 1:
        for (xx, yy) in [(4, 5), (5, 6), (6, 7), (7, 8), (8, 9), (9, 9), (10, 10), (9, 4), (10, 5), (11, 6), (5, 10), (6, 11)]:
            a[yy, xx] = (*core, 255)
    elif ph == 2:
        # 上が熔けて白熱の核がむき出し、下へ滴が垂れる
        for yy in range(16):
            for xx in range(16):
                d = (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2
                if yy < 8 and d <= 14:
                    a[yy, xx] = (*core, 255)
        for (xx, yy) in [(4, 11), (4, 12), (7, 12), (7, 13), (7, 14), (10, 11), (10, 12), (11, 12)]:
            a[yy, xx] = (255, 200, 80, 255)
        a[6, 7] = a[6, 8] = a[5, 7] = (255, 250, 220, 255)
    else:
        # 灰の殻に穴があき、中に小さく暗い赤
        for (xx, yy) in [(6, 6), (7, 6), (6, 7), (7, 7), (8, 7), (7, 8)]:
            a[yy, xx] = (*core, 255)
        for (xx, yy) in [(3, 4), (11, 4), (5, 11), (10, 10)]:
            a[yy, xx] = (110, 104, 98, 255)
        for (xx, yy) in [(7, 1), (9, 0), (6, 0)]:
            a[yy, xx] = (216, 90, 70, 255)
    return Image.fromarray(a, "RGBA")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for ph in (1, 2, 3):
        f = frame(ph)
        ic = icon(ph)
        f.save(OUT / f"ember_heart_frame_p{ph}.png")
        ic.save(OUT / f"ember_heart_icon_p{ph}.png")
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
