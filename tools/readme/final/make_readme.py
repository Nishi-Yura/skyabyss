"""Final README plates (concept C: title cards, bound like a field journal). Output: docs/readme/plate_*.png and preview_final*.png.
Run: python tools/readme/final/make_readme.py"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "options"))
from common import *  # noqa
import make_a as A
import make_c as C
from PIL import Image

OUTD = os.path.abspath(os.path.join(HERE, "..", "..", "..", "docs", "readme"))
pix.OUT = OUTD
W = 280
BAND = 7
PALE = C.PALE
INKB, INKB_L = A.INKB, A.INKB_L
PARCH, PARCH_L, PARCH_D, PARCH_DD = A.PARCH, A.PARCH_L, A.PARCH_D, A.PARCH_DD
STITCH = A.STITCH
NAVY_L = [ABYSS2, ABYSS3, ABYSS1]
BROWN_L = [A.LEA, A.LEA_L, A.LEA_D]

SIGILS = {
    "whale": ["..####.", ".######", "#######", ".#####.", ".#..##."],
    "tide": ["..###..", ".#####.", "#######", "#.#.#.#", ".#.#.#.", "..###.."],
    "storm": ["...###.", "..###..", ".#####.", "...##..", "..##...", ".#....."],
    "ember": ["...#...", "..##...", ".####..", ".#####.", "#######", ".#####.", "..###.."],
}


def sigil_arr(name, col=INK):
    rows = SIGILS[name]
    a = canvas(7, 7)
    oy = (7 - len(rows)) // 2
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c == "#":
                a[oy + y, x] = col
    return a


def check_w(lines_, size, maxw):
    for s in lines_:
        if s and text_width(s, size) > maxw:
            raise SystemExit("too wide (%d>%d): %s" % (text_width(s, size), maxw, s))


def body(Hh, kind, seed, stars=30):
    """Plate interior. kind: night | paper."""
    a = canvas(W, Hh)
    if kind == "night":
        third = Hh // 3
        vgrad(a, [(third, ABYSS0), (2 * third, ABYSS1), (Hh, ABYSS2)])
        star_field(a, BAND + 2, int(Hh * .7), stars, seed, (SKY4, SKY3, TEAL_L, ABYSS3))
        return a, NAVY_L
    noise_fill(a, 0, 0, W - 1, Hh - 1, [PARCH, A.PARCH_S, PARCH_L], [14, 3, 2], seed)
    return a, BROWN_L


def bind(a, leather, kind, sig=("whale", "tide", "storm", "ember")):
    """Leather binding band with gold stitches and brass corner plaques carrying the four titan sigils."""
    h, w = a.shape[:2]
    inner = a[BAND:h - BAND, BAND:w - BAND].copy()
    noise_fill(a, 0, 0, w - 1, h - 1, leather, [10, 5, 3], 77)
    a[BAND:h - BAND, BAND:w - BAND] = inner
    a[0, :] = INK; a[h - 1, :] = INK; a[:, 0] = INK; a[:, w - 1] = INK
    a[BAND - 1, BAND - 1:w - BAND + 1] = INK; a[h - BAND, BAND - 1:w - BAND + 1] = INK
    a[BAND - 1:h - BAND + 1, BAND - 1] = INK; a[BAND - 1:h - BAND + 1, w - BAND] = INK
    edge = PARCH_D if kind == "night" else A.LEA_DD
    a[BAND, BAND:w - BAND] = edge; a[h - BAND - 1, BAND:w - BAND] = edge
    a[BAND:h - BAND, BAND] = edge; a[BAND:h - BAND, w - BAND - 1] = edge
    for x in range(14, w - 14, 4):
        a[3, x:x + 2] = STITCH; a[h - 4, x:x + 2] = STITCH
    for y in range(14, h - 14, 4):
        a[y:y + 2, 3] = STITCH; a[y:y + 2, w - 4] = STITCH
    for (cx, cy), nm in zip(((0, 0), (w - 11, 0), (0, h - 11), (w - 11, h - 11)), sig):
        a[cy:cy + 11, cx:cx + 11] = INK
        a[cy + 1:cy + 10, cx + 1:cx + 10] = GOLD_D
        a[cy + 2:cy + 9, cx + 2:cx + 9] = GOLD
        a[cy + 2, cx + 2:cx + 9] = GOLD_L; a[cy + 2:cy + 9, cx + 2] = GOLD_L
        paste(a, sigil_arr(nm, A.LEA_DD), cx + 2, cy + 2)
    return a


def cap(a, y, s, size=12, col=INKB):
    text(a, W // 2 - text_width(s, size) // 2, y, s, col, size)


def head(a, y, title, icon=None):
    A.header(a, W, y, title, icon=icon)


GREEN = H("2a6b4a")


# ---------------------------------------------------------------- plates
def p_title():
    Hh = 176
    a, lea = body(Hh, "night", 31, 40)
    silhouette_whale(a, 30, 88, 220, ABYSS3, ABYSS2)
    cloud(a, 60, 154, 120, 9, ABYSS3, ABYSS2, ABYSS1)
    cloud(a, 220, 156, 110, 9, ABYSS3, ABYSS2, ABYSS1)
    cloud(a, 140, 164, 130, 8, ABYSS2, ABYSS1, ABYSS0)
    twinkle(a, 44, 28, WHITE, SKY3); twinkle(a, 236, 42, WHITE, SKY3)
    fancy_title(a, W // 2, 38, "天と淵のあいだ", 26, 1, face="biz", light=WHITE, mid=PALE, dark=SKY3, side=ABYSS3, depth=2, bold=True)
    s = "BETWEEN THE SKY AND THE ABYSS"
    text(a, W // 2 - text_width(s, 12) // 2, 76, s, TEAL_L, 12, shadow=ABYSS0)
    s = "空を追う人の　旅の手帳"
    text(a, W // 2 - text_width(s, 12) // 2, 96, s, PARCH_D, 12, shadow=ABYSS0)
    s = VERSION + "  BETA"
    text(a, W // 2 - text_width(s, 12) // 2, Hh - 28, s, GOLD_L, 12, shadow=ABYSS0)
    bind(a, lea, "night")
    return save(a, "plate_title.png", 3)


def p_tagline():
    Hh = 146
    a, lea = body(Hh, "night", 35, 18)
    q = ["空を、何かが通った。", "山より大きな影が、雲の上を、音もなく。"]
    d = ["Minecraft Java 版 1.21.1（NeoForge）", "向けの RPG mod。", "建築も採掘も、いつものままに。", "そこへ、怖さと冒険と、", "少しずつ解けていく謎が重なります。"]
    check_w(q + d, 12, W - 36)
    y = lines(a, 0, 17, q, PALE, 12, 16, shadow=ABYSS0, center=W // 2)
    cx, cy = W // 2, y + 5
    for dx, col in ((-22, TEAL_D), (0, GOLD), (22, TEAL_D)):
        a[cy, cx + dx - 2:cx + dx + 3] = col; a[cy - 1, cx + dx - 1:cx + dx + 2] = col
        a[cy + 1, cx + dx - 1:cx + dx + 2] = col; a[cy - 2, cx + dx] = col; a[cy + 2, cx + dx] = col
    lines(a, 0, cy + 11, d, SKY3, 12, 14, shadow=ABYSS0, center=W // 2)
    bind(a, lea, "night")
    return save(a, "plate_tagline.png", 3)


def p_four():
    Hh = 176
    a, lea = body(Hh, "night", 32, 26)
    cw, gap = 54, 12
    x0 = (W - (4 * cw + 3 * gap)) // 2
    items = [("whale", "一章", "岩鯨", "空を泳ぐ", H("d8d8d8")), ("tide", "二章", "潮殻", "海を歩く", CORAL_L),
             ("storm", "三章", "雷翼", "嵐をまとう", GOLD_L), ("ember", "四章", "余燼", "地を泳ぐ", EMBER_L)]
    for i, (sc, ch, nm, ac, col) in enumerate(items):
        x = x0 + i * (cw + gap)
        win = C.arch(scene_img(sc), cw, 54)
        m = win[:, :, 3] > 0
        full = canvas(cw + 6, 60)
        pm = np.zeros((60, cw + 6), bool); pm[3:57, 3:3 + cw] = m
        full[dilate(pm, 1) & ~pm] = col
        paste(full, win, 3, 3)
        paste(a, full, x - 3, 18)
        for s, yy, sz, c in ((ch, 84, 12, col), (nm, 99, 16, WHITE), (ac, 121, 12, SKY3)):
            text(a, x + cw // 2 - text_width(s, sz) // 2, yy, s, c, sz, shadow=ABYSS0)
    ls = ["巨神 1 体につき 1 章。", "その背は、それ自体がダンジョンです。"]
    check_w(ls, 12, W - 36)
    lines(a, 0, 143, ls, PALE, 12, 14, shadow=ABYSS0, center=W // 2)
    bind(a, lea, "night")
    return save(a, "plate_chapters.png", 3)


def check_row(a, x, y, s):
    A.check(a, x, y, True)
    text(a, x + 16, y - 1, s, INKB, 12)
    a[y + 11, x + 16:x + 16 + text_width(s, 12)] = PARCH_D


def p_pack():
    rows = ["Minecraft Java 版 1.21.1", "NeoForge 1.21.1", "GeckoLib（NeoForge 1.21.1 用）", "Java 21",
            "skyabyss-0.2.2.jar を mods へ", "GeckoLib も同じ mods へ", "サーバーなら、サーバーの mods にも","新しいワールドで始める", "難易度はピースフル以外",
            "一人でも、仲間と（6 人まで）でも"]
    check_w(rows, 12, W - 32 - 16)
    Hh = 52 + 17 * len(rows) + 50
    a, lea = body(Hh, "paper", 23)
    head(a, 13, "旅の支度", "campfire")
    y = 46
    for s in rows:
        check_row(a, 18, y, s)
        y += 17
    nx, ny, nw, nh = 24, y + 8, W - 48, 22
    a[ny + 2:ny + nh + 2, nx + 2:nx + nw + 2] = PARCH_DD
    a[ny:ny + nh, nx:nx + nw] = A.NOTE
    a[ny + nh - 2:ny + nh, nx:nx + nw] = A.NOTE_D
    A.pin(a, nx + 6, ny + 3)
    s = "みんなで遊ぶなら、同じ jar を。"
    text(a, W // 2 - text_width(s, 12) // 2 + 4, ny + 5, s, INKB, 12)
    bind(a, lea, "paper")
    return save(a, "plate_pack.png", 3)


def p_keys():
    Hh = 226
    a, lea = body(Hh, "paper", 24)
    head(a, 13, "手の動き", "star")
    left = [("J", "ステータス"), ("K", "技"), ("U", "クエストログ"), ("B", "控え帳"), ("R G V Z", "技の枠 1〜4")]
    right = [("O", "パーティ"), ("H", "護符を使う"), ("N", "知らせの記録")]
    for col, items in ((0, left), (1, right)):
        y = 46
        for ch, lab in items:
            x = 20 + col * 124
            if " " in ch:
                for i, c in enumerate(ch.split()):
                    paste(a, keycap_arr(c), x + i * 15, y)
                tx = x + 4 * 15 + 3
            else:
                paste(a, keycap_arr(ch), x, y)
                tx = x + 20
            text(a, tx, y + 3, lab, INKB, 12)
            y += 22
    a[164, 24:W - 24] = PARCH_D
    ls = ["控え帳 ＝ 巨神の記録・墓の場所・大事な品", "迷ったら、画面の「目的の帯」か U を見る。", "キーは、設定の「操作」で変えられる。"]
    check_w(ls, 12, W - 36)
    lines(a, 0, 172, ls, INKB_L, 12, 15, center=W // 2)
    bind(a, lea, "paper")
    return save(a, "plate_keys.png", 3)


RULES = [
    ("skyabyssStoryGate", "false で巨神の順番と資格を切る"),
    ("skyabyssStoryLevelCap", "false で物語のレベル上限を切る"),
    ("skyabyssPartyShare", "いない仲間への経験値の分け前"),
    ("skyabyssDeathMode", "死亡の型（0 墓・1 バニラ・2・3）"),
    ("skyabyssGraveKeepGear", "墓の型で装備中の防具を持つ"),
    ("skyabyssGraveOwnerOnly", "墓は持ち主だけが開けられる"),
    ("skyabyssGraveExpire", "墓が消えるまでの分（0 で消えない）"),
    ("skyabyssGraveRescue", "語り部の救いで戻る割合（％）"),
    ("skyabyssDeathDurability", "耐久だけ減る型の減る割合（％）"),
    ("skyabyssHeartReentryLimit", "戦いで倒れた人の戻れる回数"),
]
CMDS = [("opening reset <名前>", "序章をもう一度"), ("cinematic reset <名前>", "場面をもう一度"),
        ("titan reset <titan>", "その巨神の進みを戻す"), ("playtest now / next", "試遊用（目的を起こす）")]


def p_admin():
    Hh = 56 + 30 * len(RULES) + 26 + 16 * len(CMDS) + 44
    a, lea = body(Hh, "paper", 26)
    head(a, 13, "見張り番の頁", "cog")
    cap(a, 38, "サーバーの管理者へ（/gamerule）", 12, INKB_L)
    y = 58
    for nm, ds in RULES:
        check_w([ds], 12, W - 44)
        text(a, 20, y, nm, GREEN, 12)
        text(a, 28, y + 14, ds, INKB, 12)
        y += 30
    a[y - 2, 24:W - 24] = PARCH_D
    text(a, 20, y + 4, "/skyabyss …（OP 用）", GREEN, 12)
    y += 22
    for c, d in CMDS:
        text(a, 24, y, c, INKB, 12)
        y += 16
    s = "ほかは docs/playtest_commands.md"
    check_w([s], 12, W - 36)
    cap(a, y + 6, s, 12, INKB_L)
    bind(a, lea, "paper")
    return save(a, "plate_admin.png", 3)


ISSUES = [
    ["マルチは、まだ人の手で通しの確認を", "していません（特に 2 パーティが", "同じ背中にいるとき）。"],
    ["雷翼と余燼では、巨神に乗って移動", "する旅が、まだありません。"],
    ["序盤の演出・予兆・仕掛けの濃さは、", "通しで確かめていません。"],
    ["余燼の背中は、人が通しで遊んで", "いません。噛み合わない所は教えて。"],
    ["開いた直後の数分は、背中の島を", "建てるので、少し重くなります。"],
    ["風見鶏と潮の真珠の絵は仮のものです。"],
    ["五章以降は、まだ枠だけです。"],
]


def p_issues():
    n = sum(len(i) for i in ISSUES)
    Hh = 52 + n * 15 + len(ISSUES) * 5 + 40
    a, lea = body(Hh, "paper", 25)
    head(a, 13, "まだ書きかけの頁", "torn_page")
    y = 46
    for it in ISSUES:
        check_w(it, 12, W - 60)
        a[y + 4:y + 7, 22:25] = PARCH_DD
        a[y + 3, 23] = INKB_L
        for s in it:
            text(a, 32, y, s, INKB, 12)
            y += 15
        y += 5
    cap(a, y + 4, "気づいたことは、ぜひ教えてください。", 12, INKB_L)
    bind(a, lea, "paper")
    return save(a, "plate_issues.png", 3)


def p_end():
    Hh = 112
    a, lea = body(Hh, "night", 34, 26)
    silhouette_whale(a, 92, 58, 96, ABYSS3, ABYSS2)
    cloud(a, 40, 108, 90, 7, ABYSS3, ABYSS2, ABYSS1)
    cloud(a, 244, 108, 100, 7, ABYSS3, ABYSS2, ABYSS1)
    lines(a, 0, 20, ["空と地の底のあいだで、", "あなたを待っているものがあります。"], PALE, 12, 15, shadow=ABYSS0, center=W // 2)
    twinkle(a, 32, 34); twinkle(a, 248, 28)
    bind(a, lea, "night")
    return save(a, "plate_end.png", 3)


def p_credits():
    Hh = 96
    a, lea = body(Hh, "paper", 27)
    cap(a, 15, "奥付", 12, INKB_L)
    a[30, W // 2 - 14:W // 2 + 14] = PARCH_D
    ls = ["作　Nishi-Yura", "All Rights Reserved", "巨神の動きに GeckoLib を使っています"]
    check_w(ls, 12, W - 36)
    lines(a, 0, 38, ls, INKB, 12, 16, center=W // 2)
    bind(a, lea, "paper")
    return save(a, "plate_credits.png", 3)


PLATES = [p_title, p_tagline, p_four, p_pack, p_keys, p_admin, p_issues, p_end, p_credits]


def preview(paths):
    for mode, bg in (("", (255, 255, 255)), ("_dark", (13, 17, 23))):
        wpage = 880
        ims = [Image.open(p).convert("RGB") for p in paths]
        h = 30 + sum(i.height + 24 for i in ims) + 30
        pg = Image.new("RGB", (wpage, h), bg)
        y = 30
        for im in ims:
            pg.paste(im, ((wpage - im.width) // 2, y))
            y += im.height + 24
        pg.save(os.path.join(OUTD, "preview_final%s.png" % mode), optimize=True)


if __name__ == "__main__":
    paths = []
    for f in PLATES:
        p = f()
        paths.append(p[0])
        print(os.path.basename(p[0]), round(p[1], 1))
    preview(paths)
