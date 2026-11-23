# -*- coding: utf-8 -*-
"""鯨の鎚の設定画（正面、1 画素 = 1 単位 x32）。参照の斧と同じ大きさで暗い背景に並べる。"""
from PIL import Image, ImageDraw

W, H = 64, 80          # 画素の板（x: -32..32, y: 52..-28）
OX, OY = 32, 56        # 原点（握りの真ん中）の位置

def P(x, y):
    return (OX + x, OY - y)

img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

C = {
    "bone": ["#2a2236", "#4a4252", "#7a7062", "#aea084", "#d8caa8", "#fff4dc"],
    "slate": ["#0c0e18", "#1a1e2e", "#2c3348", "#45506a", "#6a7896", "#9eacc8"],
    "moss": ["#0c1810", "#16281a", "#24402a", "#38603c", "#58884e", "#8cb870"],
    "sky": ["#0e2440", "#164a78", "#2280b8", "#48b8e8", "#9ae6ff", "#ffffff"],
}

def rect(x0, y0, x1, y1, col):
    """x0..x1, y0..y1 は単位（含む）"""
    a, b = P(x0, y1), P(x1, y0)
    d.rectangle([a, b], fill=col)

def px(x, y, col):
    img.putpixel(P(x, y), Image.new("RGB", (1, 1), col).getpixel((0, 0)) + (255,))

def poly(pts, col):
    d.polygon([P(x, y) for x, y in pts], fill=col)

# --- 柄頭: 暗い石の段と、下の小さな結晶の尖り
rect(-3, -14, 2, -11, C["slate"][2]); rect(-3, -14, -3, -11, C["slate"][3]); rect(2, -14, 2, -11, C["slate"][1])
rect(-2, -16, 1, -15, C["slate"][3]); rect(-1, -19, 0, -17, C["sky"][3]); px(-1, -18, C["sky"][5])
# --- 握り: 苔の巻き（2 画素ごとに明暗）と骨の輪 4 本
for y in range(-10, 9):
    k = 3 if (y // 2) % 2 == 0 else 2
    rect(-3, y, 2, y, C["moss"][k]); px(-3, y, C["moss"][k + 1]); px(2, y, C["moss"][k - 1])
for y in (-10, -4, 2, 8):
    rect(-4, y, 3, y, C["bone"][4]); px(-4, y, C["bone"][5]); px(3, y, C["bone"][2])
# --- 骨の柄（短め）と節
rect(-2, 9, 1, 21, C["bone"][3]); rect(-2, 9, -2, 21, C["bone"][4]); rect(1, 9, 1, 21, C["bone"][2])
for y in (12, 16, 20):
    px(-1, y, C["bone"][2]); px(0, y, C["bone"][2])
rect(-3, 21, 2, 23, C["bone"][3]); rect(-3, 23, 2, 23, C["bone"][5])
# --- 頭: 斜めに欠けた岩の塊（幅 30・高さ 18、y 24..41）
rock = [(-10, 24), (8, 24), (11, 27), (12, 35), (9, 41), (-6, 41), (-11, 37), (-12, 28)]
poly(rock, C["slate"][3])
poly([(-10, 24), (8, 24), (10, 27), (-11, 27)], C["slate"][2])          # 下の面は暗い
poly([(-6, 41), (9, 41), (11, 38), (-9, 38)], C["slate"][4])            # 上の面は明るい
poly([(-12, 28), (-11, 37), (-9, 38), (-9, 28)], C["slate"][4])         # 左の面（光の側）
# 面の切り分け: 右上の大きな面（中）、右下の影の面（暗）、左上の光の面（明）
poly([(2, 41), (9, 41), (12, 35), (11, 31), (6, 33)], C["slate"][3])
poly([(6, 24), (8, 24), (11, 27), (12, 33), (8, 31)], C["slate"][1])
poly([(-6, 41), (2, 41), (-1, 37), (-8, 36)], C["slate"][5])
poly([(-12, 28), (-10, 24), (-6, 24), (-9, 30)], C["slate"][1])
for (x, y) in [(-4, 30), (-3, 31), (-3, 32), (-2, 33), (5, 36), (6, 35), (7, 35), (3, 27), (4, 28)]:
    px(x, y, C["slate"][0])                                              # ひび
for (x, y) in [(-8, 39), (-7, 39), (2, 40), (-11, 33), (10, 30)]:
    px(x, y, C["slate"][5])                                              # 角の光
# --- 打つ面（+x）: 面取りした暗い石の板、縁が明るい
rect(12, 27, 15, 38, C["slate"][2]); rect(15, 28, 16, 37, C["slate"][3])
rect(12, 38, 15, 38, C["slate"][4]); rect(16, 28, 16, 37, C["slate"][5]); rect(12, 27, 15, 27, C["slate"][1])
# --- 骨の角（-x）: 太い根元から上へ反る、段で細く、育ち筋
horn = [(-12, 30), (-16, 32), (-20, 35), (-23, 40), (-24, 45), (-22, 44), (-20, 40), (-17, 37), (-12, 36)]
poly(horn, C["bone"][3])
poly([(-12, 35), (-17, 37), (-20, 40), (-22, 44), (-24, 45), (-23, 40), (-20, 37), (-16, 34), (-12, 33)], C["bone"][4])
for (x, y) in [(-15, 33), (-15, 34), (-18, 36), (-18, 37), (-21, 40), (-21, 41)]:
    px(x, y, C["bone"][2])
px(-24, 45, C["bone"][5]); px(-23, 44, C["bone"][5])
# --- 骨の肋（頭を包む 2 本の弧。上で反る）
for x0 in (-6, 4):
    rect(x0, 23, x0 + 1, 42, C["bone"][3]); rect(x0, 23, x0, 42, C["bone"][4])
    rect(x0 - 1, 42, x0 + 2, 43, C["bone"][4]); px(x0 - 1, 43, C["bone"][5]); px(x0 + 2, 44, C["bone"][3])
# --- 見せ所: 面取りした八角の骨の台と空色の結晶（暗い縁 → 中 → 明るい芯）
socket = [(-3, 28), (1, 28), (3, 30), (3, 35), (1, 37), (-3, 37), (-5, 35), (-5, 30)]
poly(socket, C["bone"][4])
poly([(-2, 29), (0, 29), (2, 31), (2, 34), (0, 36), (-2, 36), (-4, 34), (-4, 31)], C["sky"][1])
poly([(-2, 30), (0, 30), (1, 31), (1, 34), (0, 35), (-2, 35), (-3, 34), (-3, 31)], C["sky"][3])
rect(-2, 32, 0, 34, C["sky"][4]); px(-2, 34, C["sky"][5]); px(-1, 33, C["sky"][5])
px(-5, 30, C["bone"][2]); px(3, 30, C["bone"][2]); px(-3, 37, C["bone"][5])
# --- 苔の垂れ
for (x, y0, y1) in [(-9, 20, 24), (7, 21, 24)]:
    rect(x, y0, x, y1, C["moss"][3]); px(x, y0, C["moss"][2])

# 縁取りなしの設定画（Blockbench の箱の表と裏に貼る）
img.save(r"C:/Users/nisimoto/Desktop/mod/tools/models/x32/concepts/whale_hammer.png")
# 輪郭の暗い縁取り（外側 1 画素。見比べ用の絵だけ）
out = img.copy()
a = img.split()[3]
for yy in range(H):
    for xx in range(W):
        if a.getpixel((xx, yy)) == 0:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= xx + dx < W and 0 <= yy + dy < H and a.getpixel((xx + dx, yy + dy)) > 0:
                    out.putpixel((xx, yy), (8, 8, 14, 255))
                    break
img = out
big = img.resize((W * 8, H * 8), Image.NEAREST)
bg = Image.new("RGB", (1200, 680), (16, 16, 22))
bg.paste(big, (40, 20), big)
ref = Image.open(r"C:/Users/nisimoto/Desktop/mod/docs/review/refs_1118/02_crystal_axe.jpg").convert("RGB").crop((100, 0, 700, 600)).resize((600, 600), Image.NEAREST)
bg.paste(ref, (580, 40))
bg.save(r"C:/Users/nisimoto/AppData/Local/Temp/claude/789edb19516a07b1/2fb69726-8d58-45da-87eb-2b43026a0285/scratchpad/concept_whale_hammer.png")

