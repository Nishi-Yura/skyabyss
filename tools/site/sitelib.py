"""サイトの部品 (頁の枠・節・絵・表)。build.py から使う。サイトは日本語だけ。

絵は site/img/shot/<名>.webp (幅 1600) と <名>_s.webp (幅 640)。元は tools/sources/site/shots/ (gen_site_shots.py)。
"""
import glob
import html
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SITE = os.path.join(ROOT, "site")
REPO = "https://github.com/Nishi-Yura/skyabyss"
SITE_URL = "https://nishi-yura.github.io/skyabyss/"


def gp(key):
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf8") as f:
        for line in f:
            if line.startswith(key + "="):
                return line.split("=", 1)[1].strip()
    return "?"


VERSION = gp("mod_version")
MC = gp("minecraft_version")
NEO = gp("neo_version")
GECKO = gp("geckolib_version")

_LANG = {}


def lang(key):
    """ja_jp の文 (全部の名前空間)。無ければ鍵のまま"""
    if not _LANG:
        for f in sorted(glob.glob(os.path.join(ROOT, "src", "main", "resources", "assets", "*", "lang", "ja_jp.json"))):
            _LANG.update(json.load(open(f, encoding="utf8")))
    return _LANG.get(key, key)


def esc(s):
    return html.escape(s, quote=True)


def T(ja, en=None):
    """日英の対の名残。サイトは日本語だけなので ja を返す (更新履歴の元の表が対で持つ)"""
    return ja


# 頁の並び (ファイル, 名前)。トップは題の字から戻る
NAV = [
    ("world.html", "世界と物語"),
    ("combat.html", "戦いと職"),
    ("adventure.html", "冒険の場所"),
    ("gear.html", "装備と成長"),
    ("together.html", "仲間と暮らし"),
    ("start.html", "はじめる"),
    ("changelog.html", "更新履歴"),
    ("wiki.html", "wiki"),
]
FONTS = "https://fonts.googleapis.com/css2?family=DotGothic16&family=Zen+Antique&family=Zen+Kaku+Gothic+New:wght@400;700&display=swap"


def head(title, desc, canonical):
    return """<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>%(t)s</title>
<meta name="description" content="%(d)s">
<link rel="canonical" href="%(site)s%(c)s">
<meta property="og:title" content="%(t)s">
<meta property="og:description" content="%(d)s">
<meta property="og:type" content="website">
<meta property="og:url" content="%(site)s%(c)s">
<meta property="og:image" content="%(site)simg/og.jpg">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#0a1226">
<link rel="icon" type="image/png" href="img/favicon.png">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="%(fonts)s">
<link rel="stylesheet" href="css/style.css">
<script>document.documentElement.classList.add("js")</script>
<script src="js/main.js" defer></script>
</head>
<body>
""" % dict(t=esc(title), d=esc(desc), site=SITE_URL, c="" if canonical == "index.html" else canonical, fonts=FONTS)


def header(current):
    here = "wiki.html" if current.startswith("wiki") else current
    links = "".join('<a href="%s"%s>%s</a>' % (h, ' aria-current="page"' if h == here else "", n) for h, n in NAV)
    return """<a class="skip" href="#main">本文へ</a>
<header class="site-header">
<div class="wrap bar">
<a class="brand" href="index.html"><img src="img/favicon.png" width="28" height="28" alt="">天と淵のあいだ</a>
<button class="nav-toggle" type="button" aria-expanded="false" aria-controls="site-nav">メニュー</button>
<nav class="nav" id="site-nav" aria-label="サイトの頁">%s</nav>
</div>
</header>
""" % links


def footer():
    links = "".join('<li><a href="%s">%s</a></li>' % (h, n) for h, n in [("index.html", "トップ")] + NAV)
    return """<footer class="site-footer">
<div class="wrap">
<ul class="foot-links">%s</ul>
<p>天と淵のあいだ v%s（Minecraft Java %s・NeoForge）。作 Nishi-Yura。All Rights Reserved。巨神の動きに GeckoLib を使っています。</p>
<p>ソースと更新: <a href="%s">GitHub: Nishi-Yura/skyabyss</a></p>
<p><small>Minecraft は Mojang Studios の商標です。このサイトと mod は Mojang・Microsoft とは関係ありません。</small></p>
</div>
</footer>
</body>
</html>
""" % (links, VERSION, MC, REPO)


def page(fname, title, desc, body):
    out = head(title, desc, fname) + header(fname) + '<main id="main">' + body + "</main>\n" + footer()
    with open(os.path.join(SITE, fname), "w", encoding="utf8", newline="\n") as f:
        f.write(out)


def redirect(old, new):
    """前の頁の名前から、今の頁へ移す小さな頁 (外から張られた道を切らない)"""
    out = """<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<title>移動しました — 天と淵のあいだ</title>
<meta name="robots" content="noindex">
<link rel="canonical" href="%(site)s%(new)s">
<meta http-equiv="refresh" content="0; url=%(new)s">
</head>
<body style="background:#0a1226;color:#eaeef7;font-family:sans-serif">
<p>この頁は <a href="%(new)s" style="color:#f3d690">%(new)s</a> へ移りました。</p>
</body>
</html>
""" % dict(site=SITE_URL, new=new)
    with open(os.path.join(SITE, old), "w", encoding="utf8", newline="\n") as f:
        f.write(out)


# ------------------------------------------------------------------ 絵
def img(name, alt, sizes="100vw", eager=False, cls="", style=""):
    """ゲームの中の絵。幅 640 と 1600 の二つから、表示の幅に合うほうを読む"""
    return ('<img%s%s src="img/shot/%s.webp" srcset="img/shot/%s_s.webp 640w, img/shot/%s.webp 1600w" sizes="%s" '
            'width="1600" height="900" alt="%s"%s>') % (
        ' class="%s"' % cls if cls else "", ' style="%s"' % style if style else "", name, name, name, sizes, esc(alt),
        ' fetchpriority="high"' if eager else ' loading="lazy" decoding="async"')


def shot(name, cap, sizes="(max-width: 900px) 100vw, 600px"):
    """説明つきの絵。説明は絵に写っているものだけ書く"""
    return '<figure class="shot">%s<figcaption>%s</figcaption></figure>' % (img(name, cap, sizes), cap)


# ------------------------------------------------------------------ 頁の部品
def page_hero(name, alt, h1, lead, toc=None, pos=None):
    nav = ""
    if toc:
        nav = '<div class="wrap"><nav class="toc" aria-label="この頁の中">%s</nav></div>' % "".join(
            '<a href="#%s">%s</a>' % (a, n) for a, n in toc)
    return ('<div class="page-hero">%s<div class="wrap"><div><p class="eyebrow">天と淵のあいだ</p><h1>%s</h1>'
            '<p class="lead">%s</p></div></div></div>%s') % (img(name, alt, eager=True, style="object-position:%s" % pos if pos else ""), h1, lead, nav)


def sec(id_, kicker, h2, lead, inner, alt=False):
    head_ = '<div class="sec-head"><p class="kicker">%s</p><h2 id="%s-h">%s</h2>%s</div>' % (
        kicker, id_, h2, "<p>%s</p>" % lead if lead else "")
    return '<section class="sec%s" id="%s" aria-labelledby="%s-h"><div class="wrap">%s%s</div></section>\n' % (
        " alt" if alt else "", id_, id_, head_, inner)


def split(name, cap, text, rev=False):
    return '<div class="split%s">%s<div>%s</div></div>' % (" rev" if rev else "", shot(name, cap), text)


def ul(items, cls="list"):
    return '<ul class="%s">%s</ul>' % (cls, "".join("<li>%s</li>" % i for i in items))


def table(heads, rows):
    out = ['<div class="tablewrap"><table><thead><tr>']
    out += ['<th scope="col">%s</th>' % h for h in heads]
    out.append("</tr></thead><tbody>")
    for r in rows:
        out.append("<tr>" + "".join(("<th scope=\"row\">%s</th>" if i == 0 else "<td>%s</td>") % c for i, c in enumerate(r)) + "</tr>")
    out.append("</tbody></table></div>")
    return "".join(out)


def gallery(*items):
    return '<div class="gallery">%s</div>' % "".join(shot(n, c, "(max-width: 900px) 100vw, 380px") for n, c in items)


def note(text, warn=False):
    return '<div class="note%s">%s</div>' % (" warn" if warn else "", text)


def details(summary, inner):
    return "<details><summary>%s</summary><div>%s</div></details>" % (summary, inner)
