"""README concept A: a travel journal / picture book. Pages of parchment with pasted pixel photographs."""
from common import *

PARCH = H("f1e4c3"); PARCH_S = H("e8d8b0"); PARCH_L = H("f8eed4"); PARCH_D = H("c9b583"); PARCH_DD = H("a88f5c")
INKB = H("4a3826"); INKB_L = H("7a634a")
LEA = H("5b3a28"); LEA_L = H("73492f"); LEA_D = H("432a1c"); LEA_DD = H("2c1a10")
STITCH = H("d9b36a"); NOTE = H("f6e08a"); NOTE_D = H("d8bd5a"); TAPE = H("e9dfa0"); TAPE_D = H("cdbf78")
PIN = H("c8453c"); PIN_L = H("f08a7a")


def parchment(W, Hh, seed):
    a = canvas(W, Hh)
    noise_fill(a, 0, 0, W - 1, Hh - 1, [PARCH, PARCH_S, PARCH_L], [14, 3, 2], seed)
    a[0, :] = INKB; a[Hh - 1, :] = INKB; a[:, 0] = INKB; a[:, W - 1] = INKB
    a[1, 1:W - 1] = PARCH_D; a[Hh - 2, 1:W - 1] = PARCH_DD
    a[1:Hh - 1, 1] = PARCH_D; a[1:Hh - 1, W - 2] = PARCH_DD
    clip_corners(a, 3)
    return a


def tape(a, x, y, w=16, h=6):
    a[y:y + h, x:x + w] = TAPE
    a[y, x:x + w] = TAPE_D; a[y + h - 1, x:x + w] = TAPE_D
    for yy in range(y, y + h, 2):
        a[yy, x] = TRANSP if False else TAPE_D; a[yy, x + w - 1] = TAPE_D


def pin(a, x, y):
    a[y - 1:y + 2, x - 1:x + 2] = PIN
    a[y - 1, x - 1] = PIN_L
    a[y + 2, x] = INKB_L


def polaroid(a, x, y, scene, caption=None, tint=None):
    sh, sw = scene.shape[:2]
    pw, ph = sw + 6, sh + 6 + (15 if caption else 6)
    a[y + 2:y + ph + 2, x + 2:x + pw + 2] = PARCH_DD          # drop shadow
    a[y:y + ph, x:x + pw] = H("fbf6e8")
    a[y, x:x + pw] = INKB; a[y + ph - 1, x:x + pw] = INKB; a[y:y + ph, x] = INKB; a[y:y + ph, x + pw - 1] = INKB
    a[y + 3:y + 3 + sh, x + 3:x + 3 + sw] = scene
    if caption:
        w = text_width(caption, 12)
        text(a, x + pw // 2 - w // 2, y + 3 + sh + 3, caption, INKB, 12)
    return pw, ph


def header(a, W, y, title, sub=None, icon=None):
    w = text_width(title, 16)
    x = W // 2 - w // 2
    text(a, x, y, title, INKB, 16)
    a[y + 19, x - 4:x + w + 4] = PARCH_DD
    a[y + 20, x - 4 + 2:x + w + 4 - 2] = PARCH_D
    if icon:
        paste(a, icons.get(icon), x - 24, y - 1)
        paste(a, icons.get(icon), x + w + 8, y - 1)


def cover():
    W, Hh = 280, 150
    a = canvas(W, Hh)
    noise_fill(a, 0, 0, W - 1, Hh - 1, [LEA, LEA, LEA_L, LEA_D], [10, 6, 3, 3], 3)
    a[0, :] = INK; a[Hh - 1, :] = INK; a[:, 0] = INK; a[:, W - 1] = INK
    # stitched border
    for x in range(8, W - 8, 4):
        a[8, x:x + 2] = STITCH; a[Hh - 9, x:x + 2] = STITCH
    for y in range(8, Hh - 8, 4):
        a[y:y + 2, 8] = STITCH; a[y:y + 2, W - 9] = STITCH
    # brass corners
    for (cx, cy) in ((3, 3), (W - 8, 3), (3, Hh - 8), (W - 8, Hh - 8)):
        a[cy:cy + 5, cx:cx + 5] = GOLD_D
        a[cy + 1:cy + 4, cx + 1:cx + 4] = GOLD
        a[cy + 1, cx + 1] = GOLD_L
    # sunken sky window with the whale
    wx0, wy0, wx1, wy1 = 22, 16, W - 23, 82
    sky = canvas(wx1 - wx0 + 1, wy1 - wy0 + 1)
    vgrad(sky, [(14, SKY1), (28, SKY2), (44, SKY3), (sky.shape[0], SKY4)])
    star_field(sky, 0, 12, 14, 4, (SKY4, SKY3))
    scenes.whale(sky, 40, 20, 168, scenes.WHALE_HAZE, chain=False)
    cloud(sky, 36, 58, 70, 7); cloud(sky, 150, 62, 100, 8); cloud(sky, 235, 56, 60, 6)
    cloud(sky, 100, 50, 26, 4, CLOUD, CLOUD_S)
    sh, sw = sky.shape[:2]
    a[wy0 - 2:wy1 + 3, wx0 - 2:wx1 + 3] = LEA_DD
    a[wy0 - 1:wy1 + 2, wx0 - 1:wx1 + 2] = STITCH
    a[wy0:wy1 + 1, wx0:wx1 + 1] = sky
    # title plate over the lower part
    fancy_title(a, W // 2, 25, "天と淵のあいだ", 28, 1, face="biz", light=GOLD_L, mid=GOLD, dark=GOLD_D, side=GOLD_D, depth=3, bold=True)
    s = "空を追う人の　旅の手帳"
    w = text_width(s, 12)
    a[90:107, W // 2 - w // 2 - 8:W // 2 + w // 2 + 8] = LEA_DD
    a[90, W // 2 - w // 2 - 8:W // 2 + w // 2 + 8] = STITCH; a[106, W // 2 - w // 2 - 8:W // 2 + w // 2 + 8] = STITCH
    text(a, W // 2 - w // 2, 93, s, GOLD_L, 12, shadow=LEA_DD)
    s2 = "BETWEEN THE SKY AND THE ABYSS"
    w2 = text_width(s2, 12)
    text(a, W // 2 - w2 // 2, 113, s2, STITCH, 12, shadow=LEA_DD)
    # ribbon bookmark
    a[Hh - 22:Hh + 0, 236:244] = CORAL_D
    a[Hh - 22:Hh, 236] = INK; a[Hh - 22:Hh, 243] = INK
    a[Hh - 22:Hh, 237:243] = CORAL
    for i in range(4):
        a[Hh - 4 + i, 240 - 4 + i] = LEA; a[Hh - 4 + i, 239 + (3 - i)] = LEA
    pix_plaque = H("233a68")
    vw = text_width(VERSION, 12) + 8
    frame(a, 22, 132, 22 + vw - 1, 144, face=ABYSS2, hi=ABYSS3, lo=ABYSS1)
    text(a, 26, 133, VERSION, GOLD_L, 12, shadow=ABYSS0)
    return save(a, "cover.png", 3)


def page_sky():
    W, Hh = 280, 128
    a = parchment(W, Hh, 21)
    polaroid(a, 14, 14, scenes.scene_whale(), "雲の上にて")
    tape(a, 20, 10); tape(a, 80, 10)
    x = 124
    y = 14
    y = lines(a, x, y, ["空を、何かが", "通った。"], INKB, 16, 20)
    y = lines(a, x, y + 4, ["山より大きな影が、", "雲の上を、音もなく。"], INKB, 12, 16)
    y = lines(a, x, y + 6, ["遠くの煙、響く鐘、", "巨大すぎる足跡。"], INKB_L, 12, 15)
    paste(a, icons.get("footprint"), W - 34, 12)
    text(a, W - 60, Hh - 16, "― 1 ―", INKB_L, 12)
    return save(a, "page_sky.png", 3)


def page_titans():
    W, Hh = 280, 246
    a = parchment(W, Hh, 22)
    header(a, W, 9, "出会った四体", icon="whale_icon")
    items = [("whale", "一章　岩鯨", "空を泳ぐ", 0, 0), ("tide", "二章　潮殻", "海を歩く", 1, 3),
             ("storm", "三章　雷翼", "嵐をまとう", 0, 3), ("ember", "四章　余燼", "地を泳ぐ", 1, 0)]
    for k, (sc, cap, desc, col, dy) in enumerate(items):
        r = k // 2 if k < 2 else 1
        c = k % 2
        x = 18 + c * 134
        y = 38 + r * 100 + (dy - 2)
        polaroid(a, x, y, scene_img(sc), cap)
        tape(a, x + 40, y - 3, 16, 5)
        w = text_width(desc, 12)
        text(a, x + 51 - w // 2, y + 79, desc, INKB_L, 12)
    text(a, W // 2 - 6, Hh - 14, "― 2 ―", INKB_L, 12)
    return save(a, "page_titans.png", 3)


def check(a, x, y, ok=True):
    a[y:y + 9, x:x + 9] = INKB
    a[y + 1:y + 8, x + 1:x + 8] = PARCH_L
    if ok:
        for i, (dx, dy) in enumerate(((1, 4), (2, 5), (3, 6), (4, 5), (5, 4), (6, 3), (7, 2))):
            a[y + dy - 1:y + dy + 1, x + dx] = MOSS_D


def page_pack():
    W, Hh = 280, 126
    a = parchment(W, Hh, 23)
    header(a, W, 9, "旅の支度", icon="campfire")
    rows = ["Minecraft Java 版 1.21.1", "NeoForge 1.21.1", "GeckoLib（NeoForge 用）", "Java 21"]
    y = 40
    for s in rows:
        check(a, 18, y, True)
        text(a, 34, y - 1, s, INKB, 12)
        a[y + 11, 34:34 + text_width(s, 12)] = PARCH_D
        y += 17
    # sticky note with a pin
    nx, ny, nw, nh = 184, 28, 88, 84
    a[ny + 2:ny + nh + 2, nx + 2:nx + nw + 2] = PARCH_DD
    a[ny:ny + nh, nx:nx + nw] = NOTE
    a[ny + nh - 3:ny + nh, nx:nx + nw] = NOTE_D
    pin(a, nx + nw // 2, ny + 4)
    lines(a, nx + 8, ny + 12, ["仲間と行く", "なら、みんな", "同じ jar を。", "版が違うと", "入れません。"], INKB, 12, 13)
    text(a, W // 2 - 6, Hh - 14, "― 3 ―", INKB_L, 12)
    return save(a, "page_pack.png", 3)


def page_keys():
    W, Hh = 280, 148
    a = parchment(W, Hh, 24)
    header(a, W, 9, "手の動き", icon="star")
    left = [("J", "ステータス"), ("K", "技"), ("U", "クエストログ"), ("B", "控え帳")]
    right = [("O", "パーティ"), ("H", "護符を使う"), ("N", "知らせの記録"), ("R G V Z", "技の枠 1〜4")]
    for col, items in ((0, left), (1, right)):
        y = 36
        for ch, lab in items:
            x = 16 + col * 124
            if " " in ch:
                for i, c in enumerate(ch.split()):
                    paste(a, keycap_arr(c), x + i * 15, y)
                tx = x + 3 * 15 + 17 + 2
            else:
                paste(a, keycap_arr(ch), x, y)
                tx = x + 20
            text(a, tx - 1 if " " not in ch else tx, y + 3, lab, INKB, 12)
            y += 22
    text(a, W // 2 - text_width("キーは、設定の「操作」で変えられる。", 12) // 2, Hh - 24,"キーは、設定の「操作」で変えられる。", INKB_L, 12)
    return save(a, "page_keys.png", 3)


def page_end():
    W, Hh = 280, 78
    a = canvas(W, Hh)
    vgrad(a, [(18, ABYSS1), (44, ABYSS2), (Hh, ABYSS3)])
    star_field(a, 0, 30, 30, 7, (SKY4, SKY3, TEAL_L))
    cloud(a, 40, 76, 90, 7, ABYSS3, ABYSS2, ABYSS1)
    cloud(a, 236, 76, 100, 7, ABYSS3, ABYSS2, ABYSS1)
    a[0, :] = INK; a[Hh - 1, :] = INK; a[:, 0] = INK; a[:, W - 1] = INK
    clip_corners(a, 3)
    for i, s in enumerate(("空と地の底のあいだで、", "あなたを待っているものがあります。")):
        w = text_width(s, 12)
        text(a, W // 2 - w // 2, 24 + i * 15, s, H("cfe8f0"), 12, shadow=ABYSS0)
    twinkle(a, 24, 14); twinkle(a, 250, 20)
    return save(a, "page_end.png", 3)


def page_torn():
    W, Hh = 280, 36
    a = parchment(W, Hh, 25)
    # ragged bottom edge
    rs = np.random.RandomState(5)
    for x in range(W):
        n = rs.randint(0, 4)
        a[Hh - n:, x] = TRANSP
    s = "まだ書きかけの頁"
    w = text_width(s, 16)
    text(a, W // 2 - w // 2, 6, s, INKB, 16)
    paste(a, icons.get("torn_page"), W // 2 - w // 2 - 24, 6)
    paste(a, icons.get("torn_page"), W // 2 + w // 2 + 8, 6)
    return save(a, "page_torn.png", 3)


def page_admin():
    W, Hh = 280, 36
    a = parchment(W, Hh, 26)
    s = "見張り番（サーバーの管理者）の頁"
    w = text_width(s, 12)
    text(a, W // 2 - w // 2, 12, s, INKB, 12)
    paste(a, icons2.cog(), W // 2 - w // 2 - 24, 9)
    paste(a, icons2.cog(), W // 2 + w // 2 + 8, 9)
    return save(a, "page_admin.png", 3)


if __name__ == "__main__":
    outdir("a")
    for f in (cover, page_sky, page_titans, page_pack, page_keys, page_end, page_torn, page_admin):
        p = f()
        print(os.path.basename(p[0]), round(p[1], 1))
