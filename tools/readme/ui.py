"""Section plates, dividers, advancement cards, keycaps, step slots, inventory, footer."""
import numpy as np
from pix import *
import icons

S3 = 3


def stone_fill(a, x0, y0, x1, y1, seed=1):
    rs = np.random.RandomState(seed)
    tones = [STONE, STONE, STONE, STONE_L, STONE_D]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            a[y, x] = tones[rs.randint(len(tones))]


def stone_bricks(a, x0, y0, x1, y1, seed=2):
    """Stone-brick band: mortar lines every 8 rows, offset joints."""
    rs = np.random.RandomState(seed)
    tones = [STONE, STONE, STONE_L, STONE_D]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            a[y, x] = tones[rs.randint(len(tones))]
    return


def plate(title, icon_name, fname, seed):
    W, Hh = 280, 26
    a = canvas(W, Hh)
    stone_fill(a, 0, 0, W - 1, Hh - 1, seed)
    a[0, :] = INK; a[Hh - 1, :] = INK; a[:, 0] = INK; a[:, W - 1] = INK
    a[1, 1:W - 1] = STONE_L; a[Hh - 2, 1:W - 1] = STONE_DD
    a[1:Hh - 1, 1] = STONE_L; a[1:Hh - 1, W - 2] = STONE_DD
    # slots with the icon at both ends
    ic = icons.get(icon_name)
    for sx in (5, W - 5 - 18):
        slot(a, sx, 4, 18)
        paste(a, ic, sx + 1, 5)
    # centred label
    w = text_width(title, 16)
    tx = W // 2 - w // 2
    # nameplate behind the text
    a[5:21, tx - 6:tx + w + 6] = ABYSS2
    a[5, tx - 6:tx + w + 6] = INK; a[20, tx - 6:tx + w + 6] = INK
    a[5:21, tx - 6] = INK; a[5:21, tx + w + 5] = INK
    a[6, tx - 5:tx + w + 5] = ABYSS3
    text(a, tx, 7, title, WHITE, 16, shadow=ABYSS0)
    return save(a, fname, S3)


def divider(fname="divider.png"):
    W, Hh = 280, 10
    a = canvas(W, Hh)
    rs = np.random.RandomState(5)
    for y in range(Hh):
        for x in range(W):
            a[y, x] = [STONE, STONE, STONE_D, STONE_L][rs.randint(4)]
    a[0, :] = INK; a[Hh - 1, :] = INK
    a[1, :] = STONE_L; a[Hh - 2, :] = STONE_DD
    # brick joints
    for x in range(8, W, 16):
        a[2:Hh - 2, x] = STONE_DD
    # centre ornament: a diamond of teal glass in a gold-ish frame
    cx = W // 2
    for d in range(0, 5):
        for dy in range(-d, d + 1):
            pass
    pts = [(cx, 0), (cx + 8, 5), (cx, 9), (cx - 8, 5)]
    img = Image.new("L", (W, Hh), 0)
    ImageDraw.Draw(img).polygon(pts, fill=255)
    m = np.array(img) > 0
    ring = dilate(m, 1) & ~m
    a[ring] = INK
    a[m] = TEAL_D
    inner = Image.new("L", (W, Hh), 0)
    ImageDraw.Draw(inner).polygon([(cx, 2), (cx + 4, 5), (cx, 7), (cx - 4, 5)], fill=255)
    a[np.array(inner) > 0] = TEAL_L
    a[4, cx - 1:cx + 1] = WHITE
    for k in (-20, 20, -40, 40):
        a[3:7, cx + k - 1:cx + k + 1] = TEAL_D
        a[4:6, cx + k - 1:cx + k] = TEAL
    return save(a, fname, S3)


def card(title, lines, icon_name, tcol, fname, accent):
    W, Hh = 280, 46
    a = canvas(W, Hh)
    # outer advancement-style frame
    a[:, :] = H("2c2c30")
    a[0, :] = INK; a[Hh - 1, :] = INK; a[:, 0] = INK; a[:, W - 1] = INK
    a[1, 1:W - 1] = H("c8c8d0"); a[Hh - 2, 1:W - 1] = H("6a6a74")
    a[1:Hh - 1, 1] = H("c8c8d0"); a[1:Hh - 1, W - 2] = H("6a6a74")
    # coloured inner strip (the advancement colour)
    a[2:Hh - 2, 2:5] = accent
    # icon frame, like the advancement toast icon box
    fx, fy = 9, 9
    frame(a, fx, fy, fx + 27, fy + 27, face=H("3a3a40"), hi=accent, lo=H("1a1a1e"))
    a[fy + 2:fy + 26, fx + 2:fx + 26] = H("1e1e22")
    ic = upscale(icons.get(icon_name), 1)
    paste(a, ic, fx + 6, fy + 6)
    # accent corner studs
    for (sx, sy) in ((fx + 2, fy + 2), (fx + 25, fy + 2), (fx + 2, fy + 25), (fx + 25, fy + 25)):
        a[sy, sx] = accent
    text(a, 44, 6, title, tcol, 12, shadow=H("1a1a1e"))
    y = 20
    for ln in lines:
        text(a, 44, y, ln, WHITE, 12, shadow=H("1a1a1e"))
        y += 12
    return save(a, fname, S3)


def keycap(ch, fname):
    W, Hh = 17, 18
    a = canvas(W, Hh)
    # key body: dark rim, lit top face, darker lower lip for depth
    a[:, :] = TRANSP
    a[0:15, 0:16] = INK
    a[1:14, 1:15] = H("d4d4da")
    a[1, 1:15] = H("f4f4f8"); a[1:14, 1] = H("f4f4f8")
    a[13, 1:15] = H("a0a0aa"); a[1:14, 14] = H("a0a0aa")
    a[15:17, 1:15] = H("52525c")
    a[15, 0] = INK; a[16, 0] = INK; a[15, 15] = INK; a[16, 15] = INK
    a[17, 1:15] = INK
    w = text_width(ch, 12)
    text(a, 8 - w // 2 + (0 if w % 2 else 0), 2, ch, H("22222a"), 12)
    return save(a, fname, 4)


def step(n, icon_name, fname):
    a = canvas(22, 22)
    slot(a, 0, 0, 22)
    paste(a, icons.get(icon_name), 3, 3)
    t = str(n)
    text(a, 22 - 2 - 6 - 1, 9, t, WHITE, 12, shadow=H("3f3f3f"))
    return save(a, fname, 3)


def inventory(fname="inventory.png"):
    W, Hh = 252, 136
    a = canvas(W, Hh)
    frame(a, 0, 0, W - 1, Hh - 1, face=H("c6c6c6"), hi=H("ffffff"), lo=H("555555"))
    a[2:Hh - 2, 2:W - 2] = H("c6c6c6")
    a[1, 1:W - 1] = H("ffffff"); a[1:Hh - 1, 1] = H("ffffff")
    a[Hh - 2, 1:W - 1] = H("555555"); a[1:Hh - 1, W - 2] = H("555555")
    rows = [
        (["slash", "volley", "guard", "dash"], list("RGVZ"), [("技を組む", SKY3), ("4つの枠にセットして放つ", H("a8a8a8"))]),
        (None, None, [("巨神の装備", GOLD_L), ("ランダムな特性と共鳴の炉", H("a8a8a8"))]),
        (["book", "torn_page", "tablet"], None, [("伝承の書", TEAL_L), ("見たもの、碑文、聞いた話", H("a8a8a8"))]),
    ]
    y = 8
    for ics, keys, tip in rows:
        arr = ics if ics else None
        icon_imgs = [icons.get(n) for n in ics] if ics else icons.armor_set()
        for i, ic in enumerate(icon_imgs):
            sx = 8 + i * 18
            slot(a, sx, y + 8, 18)
            paste(a, ic, sx + 1, y + 9)
            if keys:
                text(a, sx + 18 - 8, y + 8 + 18 - 12, keys[i], WHITE, 12, shadow=H("3f3f3f"))
        tooltip(a, 8 + 4 * 18 + 8, y, tip)
        y += 42
    return save(a, fname, 3)


def footer(fname="footer.png"):
    W, Hh = 280, 58
    a = canvas(W, Hh)
    vgrad(a, [(14, ABYSS0), (30, ABYSS1), (46, ABYSS2), (Hh, ABYSS3)])
    rs = np.random.RandomState(9)
    for _ in range(46):
        x, y = rs.randint(W), rs.randint(0, 30)
        a[y, x] = [SKY4, SKY3, TEAL_L][rs.randint(3)]
    for (x, y) in ((40, 6), (210, 10), (130, 4), (260, 20)):
        a[y, x] = WHITE; a[y, x - 1] = SKY3; a[y, x + 1] = SKY3; a[y - 1, x] = SKY3; a[y + 1, x] = SKY3
    from scenes import whale
    whale(W, Hh, a, 150, 12, 0.5, body=ABYSS3, lite=H("3a5688"), dark=ABYSS2, bone=H("4a6a98"), ol=ABYSS1)
    cloud(a, 40, 56, 80, 6, ABYSS3, ABYSS2, ABYSS1)
    cloud(a, 230, 56, 90, 6, ABYSS3, ABYSS2, ABYSS1)
    l1, l2 = "空と地の底のあいだで、", "あなたを待っているものがあります。"
    for ln, y in ((l1, 20), (l2, 33)):
        w = text_width(ln, 12)
        text(a, W // 2 - w // 2, y, ln, H("cfe8f0"), 12, shadow=ABYSS0)
    return save(a, fname, S3)


def make_all():
    out = []
    plates = [("できること", "sword", "plate_features.png"), ("はじめかた", "campfire", "plate_start.png"),
              ("操作", "star", "plate_keys.png"), ("もっと知りたいとき", "book", "plate_more.png"),
              ("ベータ版について", "bell", "plate_beta.png")]
    for i, (t, ic, f) in enumerate(plates):
        out.append(plate(t, ic, f, 11 + i))
    out.append(divider())
    cards = [
        ("巨神の背に乗る", ["背中はそれ自体がダンジョン。", "三つの試練と、奥で待つ守護者。"], "whale_icon", GOLD_L, "card_ride.png", GOLD),
        ("静かに迫る異変", ["雲の上を通る、巨大な影。", "驚かせない、静かな怖さ。"], "cloud_shadow", SKY3, "card_omen.png", SKY2),
        ("強くなる手ごたえ", ["力を振り分け、技を組む。", "装備は周回で。天井つき。"], "sword", H("ff9a8a"), "card_grow.png", CORAL),
        ("謎を集める", ["見たもの、読んだ碑文が", "伝承の書に記されていく。"], "book", TEAL_L, "card_lore.png", TEAL),
        ("みんなで", ["パーティで経験値を分け合う。", "報酬は一人ずつ別に抽選。"], "party", H("c8f0a0"), "card_party.png", MOSS),
    ]
    for c in cards:
        out.append(card(*c))
    for ch in "JKUBOHRGVZ":
        out.append(keycap(ch, "key_%s.png" % ch))
    steps = ["campfire", "book", "bell", "footprint", "person", "cloud_shadow", "whale_icon"]
    for i, ic in enumerate(steps, 1):
        out.append(step(i, ic, "step_%d.png" % i))
    out.append(inventory())
    out.append(footer())
    return out


if __name__ == "__main__":
    for p in make_all():
        print(os.path.basename(p[0]), round(p[1], 1))
