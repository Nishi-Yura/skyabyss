"""雷翼の心臓の段階ごとのボスバー (枠と顔の印) と、技の予告の印を描く。岩鯨・潮殻の心臓のバーと同じ寸法・同じ仕組み。

使い方: python tools/gen/world/gen_storm_heart_bar.py
出力: src/main/resources/assets/skyabyss/textures/gui/boss/storm_heart_frame_p1..3.png (182x16)
      src/main/resources/assets/skyabyss/textures/gui/boss/storm_heart_icon_p1..3.png  (16x16)
      src/main/resources/assets/skyabyss/textures/gui/storm_heart/warn_<技>.png       (32x32。予告の印、StormHeartClient が出す)
確認: docs/review/previews/storm_heart_bar.png (4 倍に拡大し、帯の色を塗った見本と予告の印。git の外)

寸法 (PhasedBossBar と同じ): 枠は 0〜11 行。帯の溝は x 3〜177、y 3〜8 (帯の色はゲームの側で塗る)。12〜15 行は段階の境の印。
色 (12 の 5.3節を採る。14.2節の heart/bars.png の案は 段階 1 青白・2 金・3 白金):
- 段階 1 畳んだ翼: 灰青の縁に銅の橙の内の縁、上下の縁に羽の段。帯は銅の橙
- 段階 2 開いた翼: 風の青白の縁、上の縁に流れる風の筋。帯は風の青白
- 段階 3 骨だけの翼: 白金の縁に稲妻の折れ線、両端に眼。帯は稲妻の白金
差し替えるときは StormHeartClient.BAR と、このファイルの STYLE・FILL を変える。
予告の印は技ごとに形と色を変える (パーティクルの設定に左右されない予告。12 の 5.3節「目盛りの縁の印」の代わりに画面の中央の上に出す)
"""
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src/main/resources/assets/skyabyss/textures/gui/boss"
WARN = ROOT / "src/main/resources/assets/skyabyss/textures/gui/storm_heart"
PREVIEW = ROOT / "docs/review/previews/storm_heart_bar.png"
W, H = 182, 16
TRACK = (3, 3, 178, 9)
DARK = (12, 16, 24)
GROOVE = (19, 24, 34)

STYLE = {
    1: dict(outer=(40, 46, 64), rim=(108, 120, 150), inner=(226, 150, 100), deco=[(196, 112, 70), (214, 218, 228)]),
    2: dict(outer=(46, 70, 104), rim=(196, 224, 255), inner=(150, 200, 250), deco=[(240, 248, 255), (150, 200, 250)]),
    3: dict(outer=(92, 78, 40), rim=(250, 240, 196), inner=(255, 226, 120), deco=[(255, 252, 230), (170, 210, 255)]),
}
FILL = {1: (217, 138, 78), 2: (168, 216, 255), 3: (246, 240, 200)}
MARKS = [round(TRACK[0] + (TRACK[2] - TRACK[0]) * f) for f in (0.33, 0.66)]

# 予告の印 (技 → 色)。StormHeart.Action の名前の小文字
WARN_COLORS = {
    "summon": (255, 255, 255), "blade": (220, 240, 255), "chain": (255, 226, 120),
    "wingbeat": (150, 200, 250), "charge": (110, 170, 255), "feather": (240, 248, 255),
    "flash": (255, 250, 220), "web": (170, 210, 255), "barrage": (255, 240, 170), "roar": (226, 150, 100),
}


def frame(ph):
    s = STYLE[ph]
    a = np.zeros((H, W, 4), np.uint8)

    def px(x, y, c):
        if 0 <= x < W and 0 <= y < H:
            a[y, x] = (*c, 255)

    for y in range(0, 12):
        for x in range(W):
            edge = min(y, 11 - y, x, W - 1 - x)
            c = s["outer"] if edge == 0 else s["rim"] if edge == 1 else DARK if edge == 2 else GROOVE
            px(x, y, c)
    for corner in ((0, 0), (W - 1, 0), (0, 11), (W - 1, 11)):
        a[corner[1], corner[0]] = (0, 0, 0, 0)
    for x in range(3, W - 3):
        px(x, 2, s["inner"])
    if ph == 1:
        # 羽の段: 上の縁に斜めの羽の先を、間をあけて (銅と白を交互に)
        for i, x in enumerate(range(6, W - 6, 9)):
            c = s["deco"][i % 2]
            px(x, 0, c)
            px(x + 1, 0, c)
            px(x + 2, 1, tuple(int(v * 0.8) for v in c))
        for x in range(10, W - 10, 18):
            px(x, 11, s["deco"][0])
    elif ph == 2:
        # 風の筋: 上の縁を流れる明るい筋 (長短)、下の縁に小さな渦
        for x in range(1, W - 1):
            if (x % 14) < 6:
                px(x, 0, s["deco"][0])
        for x in range(8, W - 8, 21):
            px(x, 11, s["deco"][1])
            px(x + 1, 10, s["deco"][0])
    else:
        # 稲妻の折れ線 (上の縁)、両端に眼
        for x in range(1, W - 1):
            if (x // 3) % 4 == 0:
                px(x, 0 if (x // 3) % 8 == 0 else 1, s["deco"][0])
        for x0 in (1, W - 6):
            for dx, dy in [(1, 4), (2, 3), (3, 3), (4, 4), (1, 6), (2, 7), (3, 7), (4, 6)]:
                px(x0 + dx, dy, s["deco"][0])
            px(x0 + 2, 5, s["deco"][1])
            px(x0 + 3, 5, s["deco"][1])
    for mx in MARKS:
        if ph == 1:
            shape, col = [(0, 12), (-1, 13), (0, 13), (1, 13), (-1, 14), (1, 14)], s["inner"]
        elif ph == 2:
            shape, col = [(0, 12), (-1, 13), (0, 13), (1, 13), (0, 14), (0, 15)], s["deco"][0]
        else:
            shape, col = [(0, 12), (1, 13), (0, 14), (1, 15)], s["deco"][0]
        for dx, y in shape:
            px(mx + dx, y, col)
        px(mx, 9, s["inner"])
    return Image.fromarray(a, "RGBA")


def disk(a, cx, cy, r, c):
    h, w = a.shape[:2]
    for yy in range(h):
        for xx in range(w):
            if (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r:
                a[yy, xx] = (*c, 255)


def line(a, x0, y0, x1, y1, c, width=1.0):
    h, w = a.shape[:2]
    n = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
    for k in range(n + 1):
        t = k / n
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
        for yy in range(int(y - width), int(y + width) + 1):
            for xx in range(int(x - width), int(x + width) + 1):
                if 0 <= xx < w and 0 <= yy < h and (xx + 0.5 - x) ** 2 + (yy + 0.5 - y) ** 2 <= width * width:
                    a[yy, xx] = (*c, 255)


def icon(ph):
    """顔の印 (16x16): 白銀の心臓と、段階の翼 (包む / 開く / 骨と稲妻)"""
    a = np.zeros((16, 16, 4), np.uint8)
    storm, storm_d, bone = (104, 114, 138), (60, 66, 88), (222, 212, 190)
    if ph == 1:
        for k in range(60):
            t = k / 59
            ang = np.pi * (0.15 + 0.7 * t)
            for side in (-1, 1):
                x = 7.5 + side * 6.0 * np.sin(ang)
                y = 8.5 - 6.0 * np.cos(ang) * 0.95
                for yy in range(16):
                    for xx in range(16):
                        if (xx + 0.5 - x) ** 2 + (yy + 0.5 - y) ** 2 <= 1.1:
                            a[yy, xx] = (*(storm if t < 0.6 else storm_d), 255)
    elif ph == 2:
        for side in (-1, 1):
            line(a, 7.5 + side * 2, 7, 7.5 + side * 7, 2, storm, 1.0)
            line(a, 7.5 + side * 2, 8, 7.5 + side * 7, 9, storm_d, 0.9)
            line(a, 7.5 + side * 1, 5, 7.5 + side * 3, 0.5, bone, 0.6)
    else:
        for side in (-1, 1):
            line(a, 7.5 + side * 2, 7, 7.5 + side * 7.4, 4, bone, 0.6)
            line(a, 7.5 + side * 3, 4.5, 7.5 + side * 5, 12, (170, 210, 255), 0.5)
    lobe = (176, 186, 212)
    disk(a, 6.3, 8.2, 2.6, lobe)
    disk(a, 9.0, 8.2, 2.6, lobe)
    disk(a, 7.6, 10.4, 2.2, lobe)
    a[12, 7] = (*lobe, 255)
    a[12, 8] = (*lobe, 255)
    core = {1: (250, 246, 210), 2: (190, 226, 255), 3: (255, 252, 230)}[ph]
    disk(a, 7.8, 9.0, 1.3, core)
    a[5, 8] = (196, 112, 70, 255)
    a[4, 9] = (196, 112, 70, 255)
    return Image.fromarray(a, "RGBA")


def warn_icon(name):
    """予告の印 (32x32): 暗い丸の地に、技の形"""
    a = np.zeros((32, 32, 4), np.uint8)
    disk(a, 16, 16, 15.5, (20, 24, 36))
    c = WARN_COLORS[name]
    ring = tuple(int(v * 0.75) for v in c)
    for yy in range(32):
        for xx in range(32):
            d = ((xx + 0.5 - 16) ** 2 + (yy + 0.5 - 16) ** 2) ** 0.5
            if 13.5 <= d <= 15.5:
                a[yy, xx] = (*ring, 255)
    if name == "summon":          # 足もとの輪と、落ちる稲妻
        line(a, 17, 3, 13, 12, c, 1.2)
        line(a, 13, 12, 19, 13, c, 1.2)
        line(a, 19, 13, 15, 21, c, 1.2)
        for k in range(24):
            ang = 2 * np.pi * k / 24
            x, y = 16 + 9 * np.cos(ang), 24 + 3 * np.sin(ang)
            line(a, x, y, x, y, c, 0.8)
    elif name == "blade":         # 頭の高さの三日月と、しゃがむ人
        for k in range(30):
            t = k / 29
            ang = np.pi * (0.1 + 0.8 * t)
            line(a, 16 - 12 * np.cos(ang), 13 - 6 * np.sin(ang) + 4, 16 - 12 * np.cos(ang), 13 - 6 * np.sin(ang) + 4, c, 1.1)
        disk(a, 16, 20, 2.2, (200, 200, 210))
        line(a, 16, 22, 16, 26, (200, 200, 210), 1.2)
        line(a, 13, 27, 19, 27, (200, 200, 210), 1.0)
    elif name == "chain":         # まっすぐ伸びる光の帯
        line(a, 6, 22, 26, 10, c, 2.0)
        disk(a, 6, 22, 3, (250, 246, 210))
    elif name == "wingbeat":      # 打ち下ろす翼と下向きの矢
        line(a, 5, 8, 14, 13, c, 1.3)
        line(a, 27, 8, 18, 13, c, 1.3)
        line(a, 16, 14, 16, 26, c, 1.4)
        line(a, 11, 21, 16, 26, c, 1.4)
        line(a, 21, 21, 16, 26, c, 1.4)
    elif name == "charge":        # 床を這う火花と、上へ逃げる矢
        for x in range(5, 28, 4):
            line(a, x, 25, x + 2, 22, c, 0.9)
            line(a, x + 2, 22, x + 4, 25, c, 0.9)
        line(a, 16, 18, 16, 6, (240, 248, 255), 1.3)
        line(a, 11, 11, 16, 6, (240, 248, 255), 1.3)
        line(a, 21, 11, 16, 6, (240, 248, 255), 1.3)
    elif name == "feather":       # 上の輪と落ちる羽
        for k in range(24):
            ang = 2 * np.pi * k / 24
            x, y = 16 + 9 * np.cos(ang), 8 + 3 * np.sin(ang)
            line(a, x, y, x, y, c, 0.8)
        for x in (11, 16, 21):
            line(a, x, 13, x - 1, 23, c, 1.0)
            line(a, x - 1, 23, x + 1, 25, (180, 190, 210), 0.8)
    elif name == "flash":         # 眼 (目を背けよ)
        for k in range(40):
            t = k / 39
            x = 4 + 24 * t
            dy = 7 * np.sin(np.pi * t)
            line(a, x, 16 - dy, x, 16 - dy, c, 0.9)
            line(a, x, 16 + dy, x, 16 + dy, c, 0.9)
        disk(a, 16, 16, 4.5, c)
        disk(a, 16, 16, 2.0, (20, 24, 36))
    elif name == "web":           # 回る網 (二本の腕と、すき間)
        for ang in (0.4, 0.4 + np.pi):
            for r0, r1 in ((3, 7), (10, 13)):
                line(a, 16 + r0 * np.cos(ang), 16 + r0 * np.sin(ang), 16 + r1 * np.cos(ang), 16 + r1 * np.sin(ang), c, 1.1)
        for k in range(10):
            ang = 0.4 + np.pi / 2 + k * 0.12
            line(a, 16 + 11 * np.cos(ang), 16 + 11 * np.sin(ang), 16 + 11 * np.cos(ang), 16 + 11 * np.sin(ang), ring, 0.7)
    elif name == "barrage":       # 追ってくる三つの輪
        for i, (x, y) in enumerate(((9, 22), (16, 17), (23, 12))):
            for k in range(16):
                ang = 2 * np.pi * k / 16
                line(a, x + 3.5 * np.cos(ang), y + 1.6 * np.sin(ang), x + 3.5 * np.cos(ang), y + 1.6 * np.sin(ang), c, 0.7)
            line(a, x, y - 8, x, y - 2, c, 0.8)
    elif name == "roar":          # 押し返す輪
        for r in (5, 9, 12):
            for k in range(int(r * 5)):
                ang = 2 * np.pi * k / int(r * 5)
                line(a, 16 + r * np.cos(ang), 16 + r * np.sin(ang), 16 + r * np.cos(ang), 16 + r * np.sin(ang), c, 0.7)
    return Image.fromarray(a, "RGBA")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    WARN.mkdir(parents=True, exist_ok=True)
    rows = []
    for ph in (1, 2, 3):
        f = frame(ph)
        ic = icon(ph)
        f.save(OUT / f"storm_heart_frame_p{ph}.png")
        ic.save(OUT / f"storm_heart_icon_p{ph}.png")
        sample = Image.new("RGBA", (W + 22, 20), (24, 26, 34, 255))
        sample.alpha_composite(ic, (0, 2))
        arr = np.array(f.copy())
        n = round((TRACK[2] - TRACK[0]) * {1: 0.85, 2: 0.5, 3: 0.2}[ph])
        arr[TRACK[1]:TRACK[3], TRACK[0]:TRACK[0] + n] = (*FILL[ph], 255)
        arr[TRACK[1], TRACK[0]:TRACK[0] + n] = (*[min(255, v + 40) for v in FILL[ph]], 255)
        sample.alpha_composite(Image.fromarray(arr, "RGBA"), (20, 2))
        rows.append(sample)
    icons = []
    for name in WARN_COLORS:
        im = warn_icon(name)
        im.save(WARN / f"warn_{name}.png")
        icons.append(im)
    width = max(rows[0].width, 34 * len(icons))
    sheet = Image.new("RGBA", (width, 20 * 3 + 36), (24, 26, 34, 255))
    for i, r in enumerate(rows):
        sheet.paste(r, (0, 20 * i))
    for i, im in enumerate(icons):
        sheet.alpha_composite(im, (34 * i, 62))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(PREVIEW)
    print("wrote", OUT, WARN, PREVIEW)


if __name__ == "__main__":
    main()
