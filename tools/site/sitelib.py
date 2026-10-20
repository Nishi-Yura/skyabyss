"""サイトの部品 (日英の対、ページの枠、表、技の木の図)。build.py から使う。"""
import html
import os
import re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SITE = os.path.join(ROOT, "site")
REPO = "https://github.com/Nishi-Yura/skyabyss"
SITE_URL = "https://nishi-yura.github.io/skyabyss/"


def version():
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf8") as f:
        for line in f:
            if line.startswith("mod_version="):
                return line.split("=", 1)[1].strip()
    return "?"


def gp(key):
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf8") as f:
        for line in f:
            if line.startswith(key + "="):
                return line.split("=", 1)[1].strip()
    return "?"


VERSION = version()
MC = gp("minecraft_version")
NEO = gp("neo_version")
GECKO = gp("geckolib_version")


_LANG = {}


def _load_lang():
    import glob
    import json
    for lg in ("ja_jp", "en_us"):
        d = {}
        for f in sorted(glob.glob(os.path.join(ROOT, "src", "main", "resources", "assets", "*", "lang", lg + ".json"))):
            d.update(json.load(open(f, encoding="utf8")))
        _LANG[lg] = d


def item_name(iid):
    """品の名前 (日, 英) を lang から引く。"""
    if not _LANG:
        _load_lang()
    k = "item.skyabyss." + iid
    return _LANG["ja_jp"].get(k, iid), _LANG["en_us"].get(k, iid)


def item_li(iid, extra_ja="", extra_en=""):
    ja, en = item_name(iid)
    return '<li>%s%s</li>' % (icon("img/item/%s.png" % iid, ja, en), T(ja + extra_ja, en + extra_en))


def T(ja, en):
    return '<span class="ja" lang="ja">%s</span><span class="en" lang="en">%s</span>' % (ja, en)


def esc(s):
    return html.escape(s, quote=True)


def icon(path, ja, en=None, cls="ic"):
    en = en if en is not None else ja
    return '<img class="%s px" src="%s" width="32" height="32" alt="%s" data-alt-ja="%s" data-alt-en="%s" loading="lazy">' % (
        cls, path, esc(ja), esc(ja), esc(en))


def pimg(path, ja, en, w=None, h=None, cls="px", eager=False):
    size = (' width="%d"' % w if w else "") + (' height="%d"' % h if h else "")
    return '<img class="%s" src="%s"%s alt="%s" data-alt-ja="%s" data-alt-en="%s"%s>' % (
        cls, path, size, esc(ja), esc(ja), esc(en), "" if eager else ' loading="lazy"')


def table(heads, rows, cls=""):
    out = ['<div class="tablewrap"><table%s><thead><tr>' % (' class="%s"' % cls if cls else "")]
    for h in heads:
        out.append("<th scope=\"col\">%s</th>" % h)
    out.append("</tr></thead><tbody>")
    for r in rows:
        out.append("<tr>" + "".join("<td>%s</td>" % c for c in r) + "</tr>")
    out.append("</tbody></table></div>")
    return "".join(out)


def panel(inner, cls=""):
    return '<div class="panel %s">%s</div>' % (cls, inner)


NAV = [
    ("index.html", "ホーム", "Home"),
    ("play.html", "遊び方", "How to play"),
    ("titans.html", "巨神と背中", "Titans"),
    ("skills.html", "スキルとビルド", "Skills"),
    ("rebirth.html", "転生と深淵", "Rebirth"),
    ("gear.html", "装備", "Gear"),
    ("pouch.html", "旅嚢と道具", "Pouch"),
    ("party.html", "パーティとマルチ", "Multiplayer"),
    ("story.html", "物語", "Story"),
    ("faq.html", "導入・FAQ", "Install / FAQ"),
    ("changelog.html", "更新履歴", "Changelog"),
]


def head(title_ja, title_en, desc_ja, desc_en, depth_class="", ogimg="img/plate_title.png"):
    return """<!doctype html>
<html lang="ja" data-lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title data-ja="%(tj)s" data-en="%(te)s">%(tj)s</title>
<meta name="description" content="%(dj)s">
<meta property="og:title" content="%(tj)s">
<meta property="og:description" content="%(dj)s">
<meta property="og:type" content="website">
<meta property="og:image" content="%(site)s%(og)s">
<meta name="theme-color" content="#080e20" media="(prefers-color-scheme: dark)">
<meta name="theme-color" content="#eaf4ff" media="(prefers-color-scheme: light)">
<link rel="icon" type="image/png" href="img/favicon.png">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DotGothic16&family=Noto+Sans+JP:wght@400;700&display=swap">
<link rel="stylesheet" href="css/style.css">
<script src="js/main.js"></script>
</head>
<body class="%(cls)s">
""" % dict(tj=esc(title_ja), te=esc(title_en), dj=esc(desc_ja), site=SITE_URL, og=ogimg, cls=depth_class)


def header(current):
    links = []
    for href, ja, en in NAV:
        cur = ' aria-current="page"' if href == current else ""
        links.append('<a href="%s"%s>%s</a>' % (href, cur, T(ja, en)))
    return """<a class="skip" href="#main">%s</a>
<header class="site-header">
<div class="wrap bar">
<a class="brand" href="index.html"><img class="px" src="img/favicon.png" width="32" height="32" alt=""><span>天と淵のあいだ</span></a>
<span class="lang" role="group" aria-label="Language / 言語"><button type="button" data-set="ja" aria-pressed="true" lang="ja">日本語</button><button type="button" data-set="en" aria-pressed="false" lang="en">English</button></span>
<button class="nav-toggle" type="button" aria-expanded="false" aria-controls="site-nav">%s</button>
<nav class="nav" id="site-nav" aria-label="%s">%s</nav>
</div>
</header>
""" % (T("本文へ", "Skip to content"), T("メニュー", "Menu"), "メニュー / Menu", "".join(links))


def footer():
    foot = "".join('<li><a href="%s">%s</a></li>' % (h, T(j, e)) for h, j, e in NAV)
    return """<footer class="site-footer">
<div class="wrap">
<ul class="foot-links">%s</ul>
<p>%s</p>
<p>%s <a href="%s">GitHub: Nishi-Yura/skyabyss</a></p>
<p><small>%s</small></p>
</div>
</footer>
</body>
</html>
""" % (
        foot,
        T("天と淵のあいだ v%s（ベータ版）。作 Nishi-Yura。All Rights Reserved。巨神の動きに GeckoLib を使っています。" % VERSION,
          "Between the Sky and the Abyss v%s (beta) by Nishi-Yura. All Rights Reserved. Titan animation uses GeckoLib." % VERSION),
        T("ソースと更新:", "Source and updates:"), REPO,
        T("Minecraft はMojang Studios の商標です。このサイトと mod は Mojang・Microsoft とは関係ありません。",
          "Minecraft is a trademark of Mojang Studios. This site and mod are not affiliated with Mojang or Microsoft."),
    )


def page_head(h1_ja, h1_en, sub_ja, sub_en):
    return '<div class="page-head"><div class="wrap"><h1>%s</h1><p>%s</p></div></div>' % (T(h1_ja, h1_en), T(sub_ja, sub_en))


def page(fname, title_ja, title_en, desc_ja, desc_en, body, cls=""):
    out = head(title_ja, title_en, desc_ja, desc_en, cls) + header(fname) + '<main id="main">' + body + "</main>\n" + footer()
    with open(os.path.join(SITE, fname), "w", encoding="utf8", newline="\n") as f:
        f.write(out)


def divider():
    return '<div class="divider" aria-hidden="true"></div>'


# ------------------------------------------------------------- 技の木の図
# (id, 技 ID の並び順, 名前 ja, 名前 en, 種類, 行, 列, 前提, 一言 ja, 一言 en)
def tree_svg(nodes, name_ja, name_en):
    """nodes: [(idx, ja, en, kind, row, col, [prereq idx])] 。kind は A(使う)・P(常に)・K(要)。"""
    pos = {}
    nodew = 64
    xs = {0: 16, 1: 116, 2: 66}
    ys = {0: 22, 1: 128, 2: 234, 3: 340}
    parts = ['<svg class="tree-svg" viewBox="0 0 260 450" role="img" aria-label="%s" data-label-ja="%s" data-label-en="%s">' % (
        esc(name_ja + " の技の木"), esc(name_ja + " の技の木"), esc(name_en + " skill tree"))]
    for (i, ja, en, kind, row, col, pre) in nodes:
        pos[i] = (xs[col] + nodew // 2, ys[row] + nodew // 2, xs[col], ys[row])
    for (i, ja, en, kind, row, col, pre) in nodes:
        for p in pre:
            x1, y1 = pos[p][0], pos[p][1] + nodew // 2
            x2, y2 = pos[i][0], pos[i][1] - nodew // 2
            if x1 == x2:
                parts.append('<path class="ln" d="M%d %d V%d"/>' % (x1, y1, y2))
            else:
                ym = (y1 + y2) // 2
                parts.append('<path class="ln" d="M%d %d V%d H%d V%d"/>' % (x1, y1, ym, x2, y2))
    for lvl, (row, txt) in enumerate(((0, "Lv1"), (1, "Lv5"), (2, "Lv12"), (3, "Lv20"))):
        parts.append('<text class="lv" x="256" y="%d" text-anchor="end">%s</text>' % (ys[row] + 14, txt))
    for (i, ja, en, kind, row, col, pre) in nodes:
        cx, cy, x, y = pos[i]
        cls = {"A": "ring-a", "P": "ring-p", "K": "ring-k"}[kind]
        parts.append('<image href="img/skill/%02d.png" x="%d" y="%d" width="64" height="64"/>' % (i, x, y))
        parts.append('<circle class="%s" cx="%d" cy="%d" r="33"/>' % (cls, cx, cy))
        b = {"A": ("使", "A"), "P": ("常", "P"), "K": ("要", "K")}[kind]
        parts.append('<rect class="badge" x="%d" y="%d" width="18" height="16"/>' % (x - 4, y - 4))
        parts.append('<text class="badge-t ja" x="%d" y="%d">%s</text><text class="badge-t en" x="%d" y="%d">%s</text>' % (x + 5, y + 8, b[0], x + 5, y + 8, b[1]))
        parts.append('<text class="tx ja" x="%d" y="%d" text-anchor="middle">%s</text><text class="tx en" x="%d" y="%d" text-anchor="middle">%s</text>' % (
            cx, y + 80, esc(ja), cx, y + 80, esc(en)))
    parts.append("</svg>")
    return "".join(parts)
