# -*- coding: utf-8 -*-
"""公式サイトの wiki (site/wiki*.html)。この mod の全部の品・敵・技・クエスト・店・場所・伝承・称号・記し・乗り物を、
ゲームの中身から作る (手で書かない。遊びを変えたら書き出し直して build する)。

元:
- tools/sources/site/wiki.json: ゲームの中の書き出し (tools/shots/wiki_dump.txt の `wikidump`。client/dev/WikiDump)。
  品の説明の行 (色つき、遊ぶ人の画面と同じ)・生き物の基の属性・技の段ごとの効果の文
- src/main/resources: lang (ja_jp)・技の木 (assets/skyabyss/skill_tree)・クエスト・店・湧き・伝承・進捗 (data/skyabyss)・大陸 (continent)
- コードの表: ダンジョン (dungeon/Dungeon.java)・乗り物 (mount/MountLook.java)
- エンドコンテンツの頁 (wiki-endgame.html): 設計書の表と、tools/site/endgame.py の手で書いた要約 (仕様を変えたら、そこも直す)
数字: 体力と攻撃は画面と同じ 10 倍 (Hp.SHOWN)。
"""
import glob
import json
import os
import re
import shutil

from sitelib import ROOT, SITE, VERSION, esc, lang, page

RES = os.path.join(ROOT, "src", "main", "resources")
DATA = os.path.join(RES, "data", "skyabyss")
ASSETS = os.path.join(RES, "assets", "skyabyss")
JAVA = os.path.join(ROOT, "src", "main", "java", "jp", "revati", "skyabyss")
DUMP = os.path.join(ROOT, "tools", "sources", "site", "wiki.json")
ICONS = os.path.join(SITE, "img", "wiki")
SHOWN = 10

TIERS = ["common", "uncommon", "rare", "epic", "heroic", "artifact", "mythic"]
TIER_JA = {t: lang("gear.skyabyss.tier." + t) for t in TIERS}
TIER_RGB = {"common": "E4E4E4", "uncommon": "7BD66B", "rare": "5AA9FF", "epic": "B98CFF", "heroic": "FF7A3D", "artifact": "F2C94C", "mythic": "E040FB"}
JOBS = [("warrior", "戦士"), ("smith", "鍛冶師"), ("mage", "魔法使い"), ("archer", "弓使い"), ("assassin", "暗殺者"), ("shaman", "呪術師")]
TREE_OF_JOB = {"warrior": "rock", "smith": "ash", "mage": "star", "archer": "bone", "assassin": "wind", "shaman": "tide"}

# wiki の頁 (ファイル, 名前, 一言)
PAGES = [
    ("wiki-weapons.html", "武器", "ジョブごとの全部の武器"),
    ("wiki-armor.html", "防具", "一式ごとの全部の防具"),
    ("wiki-accessories.html", "アクセサリー", "護符・護石・章など"),
    ("wiki-items.html", "道具と素材", "薬・素材・魚・封印の箱・大事な品"),
    ("wiki-enemies.html", "敵と主", "場所ごとの敵と主、体力と攻撃"),
    ("wiki-skills.html", "スキルツリー", "ジョブごとのツリーの全部のノード"),
    ("wiki-quests.html", "クエスト", "メイン・サブ・日替わり・週替わり"),
    ("wiki-endgame.html", "エンドコンテンツ", "物語のあとの遊びの全部（塔・裏ボス・巡り路・神器・秘伝など）"),
    ("wiki-shops.html", "店", "店ごとの品と値段"),
    ("wiki-places.html", "場所", "土地・町・見どころ・ダンジョン・巨神の居場所"),
    ("wiki-mounts.html", "乗り物", "見た目と値段"),
    ("wiki-titles.html", "称号", "称号と手に入れ方"),
    ("wiki-deeds.html", "記し", "この mod の進捗"),
    ("wiki-lore.html", "伝承の書", "伝承の全部のページ（ネタばれ）"),
]

_search = []
COUNTS = {}


def load(path):
    with open(path, encoding="utf8") as f:
        return json.load(f)


def has(key):
    return lang(key) != key


def plain(line):
    return "".join(s["t"] for s in line)


# ================================================================== 品のアイコン
def icon_src(item_id):
    """品の絵 (textures/item)。モデルの layer0 を引く。無ければ空"""
    path = item_id.split(":", 1)[1]
    ns = item_id.split(":", 1)[0]
    model = os.path.join(RES, "assets", ns, "models", "item", path + ".json")
    tex = None
    if os.path.exists(model):
        m = load(model)
        t = (m.get("textures") or {}).get("layer0")
        if t:
            tns, tpath = t.split(":", 1) if ":" in t else ("minecraft", t)
            tex = os.path.join(RES, "assets", tns, "textures", tpath + ".png")
    if not tex or not os.path.exists(tex):
        tex = os.path.join(RES, "assets", ns, "textures", "item", path + ".png")
    if not os.path.exists(tex):
        return ""
    os.makedirs(ICONS, exist_ok=True)
    name = path + ".png"
    shutil.copyfile(tex, os.path.join(ICONS, name))
    return "img/wiki/" + name


# ================================================================== 品の札 (ゲームの中の説明と同じ行)
def tip_card(item, anchor=True):
    tier = item.get("tier")
    lines = item["tooltip"]
    icon = icon_src(item["id"])
    out = []
    for i, line in enumerate(lines):
        if not plain(line).strip():
            out.append('<p class="gap"></p>')
            continue
        spans = "".join('<span style="color:#%s%s%s">%s</span>' % (
            s.get("c", "FFFFFF"), ";font-weight:700" if s.get("b") else "", ";font-style:italic" if s.get("i") else "", esc(s["t"]))
            for s in line)
        out.append('<p%s>%s</p>' % (' class="nm"' if i == 0 else "", spans))
    iid = "i-" + item["id"].split(":", 1)[1]
    text = " ".join(plain(l) for l in lines)
    return ('<article class="tip"%s style="--tc:#%s" data-name="%s" data-tier="%s" data-text="%s">%s<div class="tip-lines">%s</div></article>' % (
        ' id="%s"' % iid if anchor else "", TIER_RGB.get(tier, "8A93A8"), esc(item["name"]), tier or "", esc(text),
        '<img class="icon" src="%s" width="32" height="32" alt="" loading="lazy">' % icon if icon else '<span class="icon"></span>', "".join(out)))


def req_level(item):
    for line in item["tooltip"]:
        m = re.search(r"必要 Lv (\d+)", plain(line))
        if m:
            return int(m.group(1))
    return 0


def job_of(item):
    for line in item["tooltip"]:
        m = re.match(r"ジョブ: (\S+?)（", plain(line))
        if m:
            return m.group(1)
    return ""


def tier_rank(item):
    return TIERS.index(item["tier"]) if item.get("tier") in TIERS else -1


def filters(with_tiers=True):
    chips = "".join('<button type="button" class="f-tier" data-tier="%s" aria-pressed="false" style="--tc:#%s">%s</button>' % (
        t, TIER_RGB[t], TIER_JA[t]) for t in TIERS) if with_tiers else ""
    return ('<div class="wiki-filter"><label for="f-text">このページで絞る</label><input id="f-text" type="search" placeholder="名前・効き目の言葉" autocomplete="off">'
            '%s<span class="f-count" aria-live="polite"></span></div>') % (('<span class="f-tiers">%s</span>' % chips) if chips else "")


# ================================================================== 頁の枠
def wiki_nav(current):
    links = "".join('<a href="%s"%s>%s</a>' % (f, ' aria-current="page"' if f == current else "", n) for f, n, _ in PAGES)
    return ('<div class="wiki-bar"><div class="wrap"><div class="wiki-search"><label for="wiki-q" class="kicker">WIKI を探す</label>'
            '<input id="wiki-q" type="search" placeholder="品・敵・スキル・クエストの名前" autocomplete="off" aria-controls="wiki-results">'
            '<div id="wiki-results" class="wiki-results" role="listbox" hidden></div></div>'
            '<nav class="wiki-nav" aria-label="wiki のページ"><a href="wiki.html"%s>wiki の入口</a>%s</nav></div></div>') % (
        ' aria-current="page"' if current == "wiki.html" else "", links)


def wiki_page(fname, title, lead, body, spoiler=False, count=None):
    if count is not None:
        COUNTS[fname] = count
    head_ = ('<div class="wrap wiki-head"><p class="eyebrow">天と淵のあいだ WIKI</p><h1>%s</h1><p class="lead">%s</p>%s</div>'
             % (title, lead, '<p class="spoiler-note">このページはネタばれを含みます。</p>' if spoiler else ""))
    page(fname, "%s — 天と淵のあいだ wiki" % title, lead, wiki_nav(fname) + head_ + body + '<script src="js/wiki.js?v=%s" defer></script>' % VERSION)


def section(id_, h, inner, note=""):
    return '<section class="wiki-sec" id="%s"><div class="wrap"><h2>%s</h2>%s%s</div></section>' % (
        id_, h, '<p class="small">%s</p>' % note if note else "", inner)


try:
    import pykakasi
    _KKS = pykakasi.kakasi()
except ImportError:
    _KKS = None


# 辞書の読みが、この mod で使う読みと違う言葉 (長いものを先に)
READING_FIX = [("大百足", "おおむかで"), ("百足", "むかで"), ("旅袋", "たびのう")]


def reading(name):
    """名前のひらがなの読み (pykakasi があるときだけ。漢字の名前を読みでも探せるように)"""
    if not _KKS:
        return ""
    for word, kana in READING_FIX:
        name = name.replace(word, kana)
    return "".join(x["hira"] for x in _KKS.convert(name))


def add_search(name, kind, url):
    e = {"n": name, "k": kind, "u": url}
    r = reading(name)
    if r and r != name:
        e["r"] = r
    _search.append(e)


# ================================================================== 品
def classify(item):
    cls = item["class"].rsplit(".", 1)[-1]
    if cls == "DeferredSpawnEggItem" or item["id"].endswith("_spawn_egg") or cls == "BlockItem":
        # 生き物の卵と、置き物のブロック (封の門・共鳴の炉など。遊ぶ人には渡さない) は載せない
        return None
    if item.get("armor_slot"):
        return "armor"
    text = " ".join(plain(l) for l in item["tooltip"])
    if job_of(item) and "攻撃力" in text:
        return "weapon"
    if "装飾の枠" in text or "Accessory" in cls or "Relic" in cls or "Ward" in cls:
        return "accessory"
    if cls == "SealedBoxItem":
        return "box"
    if "Fish" in cls:
        return "fish"
    if cls in ("MaterialItem",) or "Shard" in cls:
        return "material"
    if "Draught" in cls or "Consumable" in item["class"] or "Food" in cls:
        return "consumable"
    return "other"


def gear_pages(items):
    by = {}
    for it in items:
        k = classify(it)
        if k:
            by.setdefault(k, []).append(it)
    for it in items:
        k = classify(it)
        if k:
            page_ = {"weapon": "wiki-weapons.html", "armor": "wiki-armor.html", "accessory": "wiki-accessories.html"}.get(k, "wiki-items.html")
            add_search(it["name"], {"weapon": "武器", "armor": "防具", "accessory": "アクセサリー"}.get(k, "品"), "%s#i-%s" % (page_, it["id"].split(":", 1)[1]))

    # ---- 武器: 職ごと
    weapons = by.get("weapon", [])
    body = '<div class="wrap">%s</div>' % filters()
    for _, job in JOBS:
        ws = sorted([w for w in weapons if job_of(w) == job], key=lambda w: (req_level(w), tier_rank(w), w["name"]))
        body += section("job-" + job, "%s の武器（%d）" % (job, len(ws)), '<div class="tips">%s</div>' % "".join(tip_card(w) for w in ws))
    rest = [w for w in weapons if job_of(w) not in dict(JOBS).values() and job_of(w) not in [j for _, j in JOBS]]
    if rest:
        body += section("job-other", "そのほか", '<div class="tips">%s</div>' % "".join(tip_card(w) for w in rest))
    wiki_page("wiki-weapons.html", "武器", "全部の武器 %d 本を、ジョブごとに必要レベルの順で。説明はゲームの中と同じ行です（特性・穴は手に入れたときに決まります）。" % len(weapons), body, count="%d 本" % len(weapons))

    # ---- 防具: 一式ごと
    armor = by.get("armor", [])
    sets = {}
    for a in armor:
        key = re.sub(r"_(helmet|chestplate|leggings|boots)$", "", a["id"].split(":", 1)[1])
        sets.setdefault(key, []).append(a)
    order = {"head": 0, "helmet": 0, "chest": 1, "chestplate": 1, "legs": 2, "leggings": 2, "feet": 3, "boots": 3}
    rows = []
    for key, parts in sets.items():
        parts.sort(key=lambda p: order.get(p.get("armor_slot", ""), 9))
        head = parts[0]
        nm = re.sub(r"の(兜|帽子|頭巾|冠|鉢巻|面|笠|頭|鉢の兜|飾り)$", "", head["name"]) if len(parts) > 1 else head["name"]
        rows.append((req_level(head), tier_rank(head), nm, key, parts))
    rows.sort()
    body = '<div class="wrap">%s</div>' % filters()
    inner = ""
    for lv, _, nm, key, parts in rows:
        inner += '<div class="armor-set" id="set-%s"><h3>%s<small>%s</small></h3><div class="tips">%s</div></div>' % (
            key, esc(nm), ("必要 Lv %d" % lv) if lv else "", "".join(tip_card(p) for p in parts))
    body += section("sets", "一式（%d）" % len(rows), inner, "四つの部位をそろえると、一式の効き目が出ます。")
    wiki_page("wiki-armor.html", "防具", "全部の防具 %d 点を、一式ごとに必要レベルの順で。" % len(armor), body, count="%d 点（%d 式）" % (len(armor), len(rows)))

    # ---- 装飾品
    acc = sorted(by.get("accessory", []), key=lambda a: (req_level(a), tier_rank(a), a["name"]))
    body = '<div class="wrap">%s</div>' % filters()
    body += section("all", "アクセサリー（%d）" % len(acc), '<div class="tips">%s</div>' % "".join(tip_card(a) for a in acc),
                    "アクセサリーは、持ち物の画面の「アクセサリー」のスロットに付けたときだけ効きます。")
    wiki_page("wiki-accessories.html", "アクセサリー", "護符・護石・章・遺物など、アクセサリースロットに付ける品 %d 点。" % len(acc), body, count="%d 点" % len(acc))

    # ---- 道具と素材
    groups = [("consumable", "薬と食べ物"), ("material", "素材"), ("fish", "魚"), ("box", "封印の箱"), ("other", "大事な品・道具・そのほか")]
    body = '<div class="wrap">%s</div>' % filters(False)
    for k, h in groups:
        its = sorted(by.get(k, []), key=lambda a: a["name"])
        if its:
            body += section(k, "%s（%d）" % (h, len(its)), '<div class="tips">%s</div>' % "".join(tip_card(a) for a in its))
    n = sum(len(by.get(k, [])) for k, _ in groups)
    wiki_page("wiki-items.html", "道具と素材", "装備のほかの品 %d 点。旅袋に入る品も、ここにあります。" % n, body, count="%d 点" % n)


# ================================================================== 敵と主
def dungeon_table():
    src = open(os.path.join(JAVA, "dungeon", "Dungeon.java"), encoding="utf8").read()
    out = []
    for m in re.finditer(r'[A-Z_]+\("(\w+)", ContinentMap\.\w+, (\d+), Element\.\w+, "([\w:]+)", "([\w:]+)",\s*Map\.of\((.*?)\)\)', src, re.S):
        foes = re.findall(r'"\w+", "([\w:]+)"', m.group(5))
        out.append({"id": m.group(1), "level": int(m.group(2)), "mid": m.group(3), "boss": m.group(4), "foes": foes})
    return out


# 主の技の表 (設計書。どの書類も今の仕様を書く決まり)。(生き物の id, 書類, 見出し, 表の何番目か)。
# 表は見出しの下の n 番目。「前」の列 (作り直す前の形) は載せない
BOSS_MOVES = [
    ("skyabyss:whalebone_captain", "02_stone_whale.md", "### 5.2 中ボス「鯨骨の番兵長」", 0),
    ("skyabyss:tide_beckoner", "08_tide_shell.md", "### 5.2 中ボス「潮招き」", 0),
    ("skyabyss:tide_heart", "08_tide_shell.md", "### 5.5 予告つきの技（before → after）", 0),
    ("skyabyss:raiju", "12_thunder_wing.md", "### 5.2 中ボス「雷獣」", 0),
    ("skyabyss:thunder_wing_heart", "12_thunder_wing.md", "### 5.5 予告化（Telegraph）の前と後", 0),
    ("skyabyss:ash_smith", "13_ember.md", "### 7.2 技（時間は tick）", 0),
    ("skyabyss:ember_heart", "13_ember.md", "### 18.1 技ごとの予告と数", 0),
    ("skyabyss:founder", "53_founder.md", "### 5.2 竿の技（いつでも）", 0),
    ("skyabyss:founder", "53_founder.md", "### 5.3 歌の技（数え歌の七行）", 0),
    ("skyabyss:tower_master", "54_endgame_plan.md", "### 2.5 主「あいだに座すもの」（動かない）", 0),
]
DOCS = os.path.join(ROOT, "docs", "design")
MOB_IMG = os.path.join(SITE, "img", "mob")


def md_inline(t):
    t = esc(t)
    t = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", t)
    return re.sub(r"`([^`]+)`", r"\1", t)


def doc_table(doc, heading, nth):
    """書類の見出しの下の nth 番目の表を (見出しの列, 行) で返す。「前」の列は落とす"""
    lines = open(os.path.join(DOCS, doc), encoding="utf8").read().split("\n")
    i = lines.index(heading)
    tables, cur = [], None
    for ln in lines[i + 1:]:
        if ln.startswith("#"):
            break
        if ln.startswith("|"):
            cur = cur if cur is not None else []
            cur.append([c.strip() for c in ln.strip().strip("|").split("|")])
        elif cur is not None:
            tables.append(cur)
            cur = None
    if cur:
        tables.append(cur)
    t = tables[nth]
    head, rows = t[0], [r for r in t[2:] if len(r) == len(t[0])]
    keep = [k for k, h in enumerate(head) if h != "前"]
    return [head[k] for k in keep], [[r[k] for k in keep] for r in rows]


def mob_img(mid, size):
    name = mid.split(":", 1)[1]
    if not os.path.exists(os.path.join(MOB_IMG, name + ".webp")):
        return ""
    return '<img class="mob-img" src="img/mob/%s.webp" width="%d" height="%d" alt="" loading="lazy">' % (name, size, size)


AREA_BY_PKG = [  # (パッケージの頭, 場所)
    ("dungeon.mob", "地下のダンジョン"), ("abyss.mobs", "五章 深淵"), ("story.ch5", "五章 深淵"),
    ("story.ch6", "六章 月"), ("story.finale", "終章 雲海"), ("tower.mob", "あいだの塔"),
    ("titan.storm", "雷翼の背と嵐の高地"), ("titan.ember", "余燼の背と灰の荒野"), ("titan.tide", "潮殻の背と潮の浜"),
    ("story.ch4", "灰の荒野"), ("entity.foe", "大陸の野"), ("endgame", "大陸の催し"), ("entity", "岩鯨・潮殻の背と一章・二章の場所"),
]
NOT_FOES = ("facility", "npc", "mount", "town", "item", "prologue", "titan.young")


def enemies_page(mobs):
    spawns = {}
    region_ja = {"island": "はじまりの島", "meadow": "都のまわり", "whale": "岩鯨の丘陵", "tide": "潮の浜", "storm": "嵐の高地", "ember": "灰の荒野"}
    time_ja = {"day": "昼", "night": "夜", "any": "昼夜"}
    for f in sorted(glob.glob(os.path.join(DATA, "spawns", "*.json"))):
        reg = os.path.splitext(os.path.basename(f))[0]
        for e in load(f).get("entries", []):
            spawns.setdefault(e["entity"], []).append("%s（%s）" % (region_ja.get(reg, reg), time_ja.get(e.get("time", "any"), "")))
    dungeons = dungeon_table()
    in_dungeon = {}
    for d in dungeons:
        nm = lang("dungeon.skyabyss." + d["id"])
        for f in d["foes"]:
            in_dungeon.setdefault(f, []).append(nm)
        in_dungeon.setdefault(d["mid"], []).append(nm + "（中ボス）")
        in_dungeon.setdefault(d["boss"], []).append(nm + "（主）")

    moves_of = {}
    for mid, doc, heading, nth in BOSS_MOVES:
        moves_of.setdefault(mid, []).append((doc, heading, nth))
    groups = {}
    for m in mobs:
        pkg = ".".join(m.get("class", "").split(".")[3:-1])
        if not m.get("living") or not m.get("stats") or any(pkg.startswith(p) for p in NOT_FOES):
            continue
        hp = m["stats"].get("generic.max_health", 0)
        if m["id"] in spawns:
            area = "大陸の野"
        else:
            area = next((a for p, a in AREA_BY_PKG if pkg.startswith(p)), "そのほか")
        groups.setdefault(area, []).append(m)
    order = ["大陸の野", "灰の荒野", "岩鯨・潮殻の背と一章・二章の場所", "潮殻の背と潮の浜", "雷翼の背と嵐の高地", "余燼の背と灰の荒野",
             "地下のダンジョン", "五章 深淵", "六章 月", "終章 雲海", "あいだの塔", "大陸の催し", "そのほか"]
    body = '<div class="wrap">%s<p class="small">体力と攻撃は画面と同じ 10 倍の数字です。体力・攻撃は生まれたときの基の値で、巨神の背・ダンジョン・深淵などでは人数・段・深さで強くなります。体力が大きい相手（主・中ボス・名のある強敵）には「主」の印があります。</p></div>' % filters(False)
    total = 0
    for area in order:
        ms = groups.get(area)
        if not ms:
            continue
        ms.sort(key=lambda m: (m["stats"].get("generic.max_health", 0), m["name"]))
        rows = []
        for m in ms:
            st = m["stats"]
            hp = st.get("generic.max_health", 0)
            where = spawns.get(m["id"], []) + in_dungeon.get(m["id"], [])
            aid = "m-" + m["id"].split(":", 1)[1]
            boss = hp >= 150
            moves = ' <a class="moves-link" href="#mv-%s">技</a>' % m["id"].split(":", 1)[1] if m["id"] in moves_of else ""
            rows.append('<tr id="%s" data-name="%s" data-text="%s"><th scope="row"><span class="mob-cell">%s<span>%s%s%s</span></span></th><td class="num">%s</td><td class="num">%s</td><td class="num">%s</td><td>%s</td></tr>' % (
                aid, esc(m["name"]), esc(m["name"] + " " + " ".join(where)), mob_img(m["id"], 56), esc(m["name"]), ' <span class="boss">主</span>' if boss else "", moves,
                "{:,}".format(int(round(hp * SHOWN))), "{:,}".format(int(round(st.get("generic.attack_damage", 0) * SHOWN))) if st.get("generic.attack_damage") else "—",
                ("%g" % st.get("generic.armor", 0)) if st.get("generic.armor") else "—", "、".join(dict.fromkeys(where)) or "—"))
            add_search(m["name"], "敵", "wiki-enemies.html#" + aid)
            total += 1
        body += section("a-%d" % order.index(area), "%s（%d）" % (area, len(ms)),
                        '<div class="tablewrap"><table class="mobs"><thead><tr><th scope="col">名前</th><th scope="col">体力</th><th scope="col">攻撃</th><th scope="col">防具</th><th scope="col">出る所</th></tr></thead><tbody>%s</tbody></table></div>' % "".join(rows))
    dun = "".join('<tr><th scope="row">%s</th><td class="num">%d</td><td>%s</td><td>%s</td><td>%s</td></tr>' % (
        lang("dungeon.skyabyss." + d["id"]), d["level"], "、".join(dict.fromkeys(lang("entity." + f.replace(":", ".")) for f in d["foes"])),
        lang("entity." + d["mid"].replace(":", ".")), lang("entity." + d["boss"].replace(":", "."))) for d in dungeons)
    names_by_id = {m["id"]: m["name"] for m in mobs}
    mv = ""
    for mid, specs in moves_of.items():
        tabs = ""
        for doc, heading, nth in specs:
            head, rows_ = doc_table(doc, heading, nth)
            tabs += '<p class="small">%s（設計書 %s）</p><div class="tablewrap"><table class="moves"><thead><tr>%s</tr></thead><tbody>%s</tbody></table></div>' % (
                esc(heading.lstrip("# ")), doc[:2], "".join('<th scope="col">%s</th>' % esc(h) for h in head),
                "".join("<tr>%s</tr>" % "".join(("<th scope=\"row\">%s</th>" if k == 0 else "<td>%s</td>") % md_inline(c) for k, c in enumerate(r)) for r in rows_))
        nm = names_by_id.get(mid, mid)
        mv += '<details class="boss-moves" id="mv-%s"><summary>%s%s</summary><div>%s</div></details>' % (mid.split(":", 1)[1], mob_img(mid, 40), esc(nm), tabs)
        add_search(nm + " の技", "主の技", "wiki-enemies.html#mv-" + mid.split(":", 1)[1])
    body += section("moves", "主の技", mv, "心臓・中ボス・裏ボス・あいだの塔の主の技と予兆。設計書（今の仕様）の表をそのまま載せています。数字の tick は 1/20 秒、n は人数です。")
    body += section("dungeons", "地下のダンジョンの顔ぶれ",
                    '<div class="tablewrap"><table><thead><tr><th scope="col">ダンジョン</th><th scope="col">推奨 Lv</th><th scope="col">道中の敵</th><th scope="col">中ボス</th><th scope="col">主</th></tr></thead><tbody>%s</tbody></table></div>' % dun)
    wiki_page("wiki-enemies.html", "敵と主", "この mod の敵 %d 種を、出る場所ごとに。敵はこの mod の敵だけです。" % total, body, count="%d 種" % total)


# ================================================================== 技の木
def skills_page(dump_skills):
    sk = {s["id"]: s for s in dump_skills}
    stat_ja = {"strength": "筋力", "agility": "敏捷", "spirit": "精神", "vitality": "耐久"}
    kind_ja = {"active": "使うスキル", "stat": "基礎", "keystone": "要", "trait": "型の要", "effect": "常に効く", "threshold": "閾値",
               "rune": "スキルを変える", "rank": "スキルの段", "passive": "常に効く", "mutation": "変異"}
    body = ""
    trees = [(j, n) for j, n in JOBS] + [("deep", "淵のスキル（転生の報い）"), ("past", "前世のスキル（転生の報い）")]
    toc = "".join('<a href="#tree-%s">%s</a>' % (j, n) for j, n in trees)
    body += '<div class="wrap"><nav class="toc" aria-label="木">%s</nav>%s</div>' % (toc, filters(False))
    total_nodes = 0
    for job, jname in trees:
        f = os.path.join(ASSETS, "skill_tree", job + ".json")
        if not os.path.exists(f):
            continue
        t = load(f)
        nodes = t["nodes"]
        total_nodes += len(nodes)
        names = {}
        for n in nodes:
            names[n["id"]] = node_name(job, n, sk, stat_ja)
        archs = t.get("archetypes", [])
        inner = ""
        groups = [("", "幹（どの型からも）")] + [(a, lang("arch.skyabyss.%s.%s" % (job, a))) for a in archs]
        for arch, aname in groups:
            ns = [n for n in nodes if (n.get("arch") or "") == arch]
            if not ns:
                continue
            ns.sort(key=lambda n: (n.get("y", 0), n.get("x", 0)))
            rows = []
            for n in ns:
                eff = node_effect(job, n, sk, stat_ja)
                req = "、".join(names.get(r, r) for r in n.get("requires", []))
                extra = []
                if n.get("arch_points"):
                    extra.append("型の点 %d" % n["arch_points"])
                nid = "n-%s-%s" % (job, n["id"])
                rows.append('<tr id="%s" data-name="%s" data-text="%s"><th scope="row">%s</th><td>%s</td><td class="num">%s</td><td>%s</td><td>%s</td></tr>' % (
                    nid, esc(names[n["id"]]), esc(names[n["id"]] + " " + eff), esc(names[n["id"]]), kind_ja.get(n.get("kind"), n.get("kind", "")),
                    n.get("cost", 1), eff, esc(req + ("（%s）" % "・".join(extra) if extra else ""))))
                if n.get("kind") in ("active", "keystone", "trait"):
                    add_search(names[n["id"]], "スキル（%s）" % jname, "wiki-skills.html#" + nid)
            inner += '<h3>%s<small>%d 節</small></h3><div class="tablewrap"><table class="nodes"><thead><tr><th scope="col">節</th><th scope="col">種類</th><th scope="col">点</th><th scope="col">効き目</th><th scope="col">前提</th></tr></thead><tbody>%s</tbody></table></div>' % (
                esc(aname), len(ns), "".join(rows))
        body += section("tree-" + job, "%s（%d 節）" % (jname, len(nodes)), inner)
    wiki_page("wiki-skills.html", "スキルツリー", "ジョブごとのスキルツリーの全部のノード。使うスキルの効き目は、1 段目の数字です（段を上げると変わります）。組んでみるときは <a href=\"skill-tree.html\">スキルツリーを試す</a> へ。", body, count="%d 節" % total_nodes)


def node_name(job, n, sk, stat_ja):
    k = n.get("kind")
    if n.get("skill") and k in ("active",):
        return sk.get(n["skill"], {}).get("name") or lang("skill.skyabyss." + n["skill"])
    if k == "stat":
        return "%s +%d" % (stat_ja.get(n.get("stat"), n.get("stat")), n.get("amount", 0))
    if n.get("rune"):
        key = "rune.skyabyss.%s" % n["rune"]
        if has(key):
            return lang(key)
    for key in ("node.skyabyss.%s.%s" % (job, n["id"]), "keystone.skyabyss.%s" % n["id"], "trait.skyabyss.%s" % n["id"]):
        if has(key):
            return lang(key)
    if n.get("skill"):
        return lang("skill.skyabyss." + n["skill"])
    return n["id"]


def node_effect(job, n, sk, stat_ja):
    k = n.get("kind")
    if k == "active" and n.get("skill") in sk:
        return esc(sk[n["skill"]]["effect"][0])
    if k == "stat":
        return "%s +%d" % (stat_ja.get(n.get("stat")), n.get("amount", 0))
    for key in ("node.skyabyss.%s.%s.effect" % (job, n["id"]), "keystone.skyabyss.%s.effect" % n["id"], "trait.skyabyss.%s.effect" % n["id"]):
        if has(key):
            return esc(lang(key))
    if n.get("rune"):
        for key in ("rune.skyabyss.%s.effect" % n["rune"], "rune.skyabyss.%s.desc" % n["rune"]):
            if has(key):
                return esc(lang(key))
    if n.get("skill") and n["skill"] in sk:
        return esc(sk[n["skill"]]["effect"][0])
    return ""


# ================================================================== クエスト
def quests_page(names):
    qs = []
    root = os.path.join(DATA, "quest")
    for f in glob.glob(os.path.join(root, "**", "*.json"), recursive=True):
        q = load(f)
        # id は quest/ からの道 (ch1/lost_traveller)。文の鍵は / を . にした形 (quest.skyabyss.ch1.lost_traveller.title)
        q["_id"] = os.path.splitext(os.path.relpath(f, root))[0].replace(os.sep, "/")
        qs.append(q)
    cat = {"main": "メイン", "sub": "サブ", "side": "サイド"}
    rep = {"daily": "日替わり", "weekly": "週替わり"}
    groups = {}
    for q in qs:
        g = rep.get(q.get("repeat")) or cat.get(q.get("category"), q.get("category", "そのほか"))
        groups.setdefault(g, []).append(q)
    body = '<div class="wrap">%s</div>' % filters(False)
    for g in ["メイン", "サブ", "サイド", "日替わり", "週替わり"] + [k for k in groups if k not in ("メイン", "サブ", "サイド", "日替わり", "週替わり")]:
        lst = sorted(groups.get(g, []), key=lambda q: (q.get("order", 999), q["_id"]))
        if not lst:
            continue
        cards = []
        for q in lst:
            qid = q["_id"].replace("/", ".")
            title = lang("quest.skyabyss.%s.title" % qid)
            summary = lang("quest.skyabyss.%s.summary" % qid)
            stages = []
            for st in q.get("stages", []):
                j = lang("quest.skyabyss.%s.%s.journal" % (qid, st["id"]))
                objs = []
                for o in st.get("objectives", []):
                    key = "quest.skyabyss.%s.%s.%s" % (qid, st["id"], o.get("id", ""))
                    objs.append(lang(key) if has(key) else o.get("type", ""))
                stages.append("<li>%s%s</li>" % ("<p>%s</p>" % esc(j) if has("quest.skyabyss.%s.%s.journal" % (qid, st["id"])) else "",
                                                 ("<p class='obj'>%s</p>" % esc(" / ".join(objs))) if objs else ""))
            rw = q.get("reward") or {}
            rws = []
            if rw.get("xp"):
                rws.append("経験 %d" % rw["xp"])
            if rw.get("coins"):
                rws.append("鐘銭 %d" % rw["coins"])
            for it in rw.get("items", []):
                rws.append("%s ×%d" % (names.get(it.get("item"), it.get("item")), it.get("count", 1)))
            hint = lang("quest.skyabyss.%s.unlock_hint" % qid) if has("quest.skyabyss.%s.unlock_hint" % qid) else ""
            aid = "q-" + qid.replace(".", "-")
            add_search(title, "クエスト（%s）" % g, "wiki-quests.html#" + aid)
            cards.append('<details class="quest" id="%s" data-name="%s" data-text="%s"><summary><b>%s</b><span>%s</span></summary><div>%s%s<ol class="stages">%s</ol>%s</div></details>' % (
                aid, esc(title), esc(title + " " + summary), esc(title), esc(summary) if has("quest.skyabyss.%s.summary" % qid) else "",
                ("<p class='small'>受けられるとき: %s</p>" % esc(hint)) if hint else "", "",
                "".join(stages), ("<p class='reward'>報酬: %s</p>" % esc("、".join(rws))) if rws else ""))
        gid = {"メイン": "main", "サブ": "sub", "サイド": "side", "日替わり": "daily", "週替わり": "weekly"}.get(g, "other")
        body += section("g-" + gid, "%s（%d）" % (g, len(lst)), "".join(cards),
                        "メインのクエストは物語のネタばれを含みます。開くと段ごとの日記と報酬が出ます。" if g == "メイン" else "")
    wiki_page("wiki-quests.html", "クエスト", "全部のクエスト %d 件。段ごとの日記（クエストログの文）と報酬。" % len(qs), body, spoiler=True, count="%d 件" % len(qs))


# ================================================================== 店
# 品の職 (rpg/skill/Job.of と同じ語の見分け。店の品の "job" があればそれ)。職の品でなければ None
_JOB_WORDS = [
    ("shaman", ("rite", "totem", "censer")),
    ("mage", ("staff", "wand", "rod", "scepter")),
    ("archer", ("bow", "crossbow", "longbow")),
    ("assassin", ("fang", "dagger", "claw", "shears", "pincer", "knife")),
    ("smith", ("hammer", "warhammer", "axe", "waraxe", "battleaxe", "greataxe", "poker", "mace", "maul", "anchor", "scythe")),
    ("warrior", ("sword", "greatsword", "blade", "pike", "spear", "lance", "glaive", "halberd", "harpoon", "trident", "shield")),
]


def good_job(g):
    if g.get("job"):
        return g["job"]
    path = (g.get("item") or "").split(":")[-1]
    toks = path.split("_")
    if path.startswith("gather_") or path in ("fishing_rod", "lightning_rod", "blaze_rod", "breeze_rod", "ember_rod"):
        return None
    if "bell" in toks and ("staff" in toks or "wand" in toks):
        return "shaman"
    for job, words in _JOB_WORDS:
        if any(t == w or t == w + "s" for t in toks for w in words):
            return job
    return None


def shops_page(names):
    body = ""
    for f in sorted(glob.glob(os.path.join(DATA, "shop", "*.json"))):
        sid = os.path.splitext(os.path.basename(f))[0]
        s = load(f)
        rows = []
        for key in ("goods", "daily", "pool", "stock"):
            for g in s.get(key, []) if isinstance(s.get(key), list) else []:
                nm = names.get(g.get("item"), g.get("item"))
                mats = "、".join("%s ×%d" % (names.get(m.get("item"), m.get("item")), m.get("count", 1)) for m in g.get("materials", []))
                job = good_job(g)
                notes = [x for x in ("日替わり" if key == "daily" else "", ("%s章から" % "一二三四五六"[g["chapter"] - 1]) if isinstance(g.get("chapter"), int) and 1 <= g["chapter"] <= 6 else "",
                                     (lang("job.skyabyss." + job) + "だけ") if job else "") if x]
                rows.append('<tr><th scope="row"><a href="%s">%s</a></th><td class="num">%s</td><td class="num">%s</td><td>%s</td><td>%s</td></tr>' % (
                    item_link(g.get("item")), esc(nm), g.get("count", 1), g.get("coins", "—"), esc(mats) or "—", "・".join(notes)))
        buys = []
        for b in s.get("buys", []) if isinstance(s.get("buys"), list) else []:
            nm = names.get(b.get("item"), b.get("item"))
            buys.append('<tr><th scope="row"><a href="%s">%s</a></th><td class="num">%s</td><td class="num">%s</td></tr>' % (
                item_link(b.get("item")), esc(nm), b.get("per", 1), b.get("coins", "—")))
        name = lang("gui.skyabyss.shop." + sid)
        add_search(name, "店", "wiki-shops.html#s-" + sid)
        inner = '<div class="tablewrap"><table><thead><tr><th scope="col">品</th><th scope="col">数</th><th scope="col">鐘銭</th><th scope="col">材料</th><th scope="col"></th></tr></thead><tbody>%s</tbody></table></div>' % "".join(rows)
        if buys:
            inner += '<h3>買い取り</h3><div class="tablewrap"><table><thead><tr><th scope="col">品</th><th scope="col">いくつで</th><th scope="col">鐘銭</th></tr></thead><tbody>%s</tbody></table></div>' % "".join(buys)
        body += section("s-" + sid, name, inner)
    wiki_page("wiki-shops.html", "店", "店ごとの品と値段（鐘銭）。ジョブの品（「〇〇だけ」）は、そのジョブの人にだけ並びます。", body, count="%d 軒" % len(glob.glob(os.path.join(DATA, "shop", "*.json"))))


_link_page = {}


def item_link(iid):
    p = _link_page.get(iid)
    return "%s#i-%s" % (p, iid.split(":", 1)[1]) if p and iid else "#"


# ================================================================== 場所
def places_page():
    lay = load(os.path.join(DATA, "continent", "layout.json"))
    pois = load(os.path.join(DATA, "continent", "pois.json"))
    lairs = load(os.path.join(DATA, "continent", "lairs.json"))
    region_ja = {"whale": "岩鯨の丘陵", "tide": "潮の浜", "storm": "嵐の高地", "ember": "灰の荒野"}
    town_ja = {"whale": "鯨見", "tide": "汐守", "storm": "風待", "ember": "灰炉"}
    rows = ['<tr><th scope="row">鐘の都「鐘の宿り」</th><td>—</td><td class="num">%d, %d</td></tr>' % tuple(lay["capital"])]
    for k, (x, z) in lay["towns"].items():
        rows.append('<tr><th scope="row">%s</th><td>%s</td><td class="num">%d, %d</td></tr>' % (town_ja.get(k, k), region_ja.get(k, k), x, z))
    body = section("towns", "都と町", '<div class="tablewrap"><table><thead><tr><th scope="col">町</th><th scope="col">土地</th><th scope="col">x, z</th></tr></thead><tbody>%s</tbody></table></div>' % "".join(rows))
    titan_ja = {"stone_whale": "岩鯨", "tide_shell": "潮殻", "thunder_wing": "雷翼", "ember": "余燼"}
    rows = ['<tr><th scope="row">%s</th><td class="num">%d, %d</td></tr>' % (titan_ja.get(k, k), v["x"], v["z"]) for k, v in lairs.items() if isinstance(v, dict) and k in titan_ja]
    body += section("lairs", "巨神の居場所", '<div class="tablewrap"><table><thead><tr><th scope="col">巨神</th><th scope="col">x, z</th></tr></thead><tbody>%s</tbody></table></div>' % "".join(rows),
                    "巨神は四体とも、この居場所で休み続けます。")
    kinds = {}
    for p in pois:
        kinds.setdefault(p["kind"], []).append(p)
    rows = []
    for k, ps in sorted(kinds.items(), key=lambda kv: -len(kv[1])):
        key = "map.skyabyss.poi." + k
        nm = lang(key) if has(key) else (lang("dungeon.skyabyss." + k[8:]) if k.startswith("dungeon_") and has("dungeon.skyabyss." + k[8:]) else k)
        rows.append('<tr data-name="%s" data-text="%s"><th scope="row">%s</th><td class="num">%d</td><td class="coords">%s</td></tr>' % (
            esc(nm), esc(nm), esc(nm), len(ps), "　".join("%d, %d" % (p["x"], p["z"]) for p in ps)))
        add_search(nm, "見どころ", "wiki-places.html#pois")
    body += section("pois", "見どころ（%d か所）" % len(pois), '<div class="wrap-in">%s</div><div class="tablewrap"><table><thead><tr><th scope="col">見どころ</th><th scope="col">数</th><th scope="col">x, z</th></tr></thead><tbody>%s</tbody></table></div>' % (filters(False), "".join(rows)))
    dun = "".join('<tr><th scope="row">%s</th><td class="num">%d</td></tr>' % (lang("dungeon.skyabyss." + d["id"]), d["level"]) for d in dungeon_table())
    body += section("dungeons", "地下のダンジョン", '<div class="tablewrap"><table><thead><tr><th scope="col">ダンジョン</th><th scope="col">推奨 Lv</th></tr></thead><tbody>%s</tbody></table></div>' % dun,
                    "入口は見どころの表の各ダンジョンの所です。顔ぶれは「敵と主」のページに。")
    wiki_page("wiki-places.html", "場所", "大陸の町・巨神の居場所・見どころ（座標つき）・地下のダンジョン。座標は x, z（東西, 南北）です。", body, count="見どころ %d か所" % len(pois))


# ================================================================== 乗り物・称号・記し・伝承
def mounts_page():
    src = open(os.path.join(JAVA, "mount", "MountLook.java"), encoding="utf8").read()
    when = {1: "はじめから", 2: "一章を越えたら", 3: "二章を越えたら", 4: "三章を越えたら", 5: "四章を越えたら", 6: "五章を越えたら", 7: "六章を越えたら", 8: "終章を越えたら"}
    rows = []
    for m in re.finditer(r'^\s+[A-Z_]+\("(\w+)", (\d+), (\d+),', src, re.M):
        lid, stage, price = m.group(1), int(m.group(2)), int(m.group(3))
        nm = lang("mount.skyabyss.look." + lid)
        rows.append('<tr id="mount-%s"><th scope="row">%s</th><td>%s</td><td class="num">%s</td><td>%s</td></tr>' % (
            lid, esc(nm), when.get(stage, stage), "最初から持つ" if price == 0 else price, esc(lang("mount.skyabyss.look.%s.desc" % lid))))
        add_search(nm, "乗り物", "wiki-mounts.html#mount-" + lid)
    body = section("looks", "見た目（%d）" % len(rows), '<div class="tablewrap"><table><thead><tr><th scope="col">見た目</th><th scope="col">開く時</th><th scope="col">鐘銭</th><th scope="col">姿</th></tr></thead><tbody>%s</tbody></table></div>' % "".join(rows),
                   "都の子の止まり木で買います。見た目は一度買えば、性能の段はどの見た目にも付きます。")
    wiki_page("wiki-mounts.html", "乗り物", "乗り笛で呼ぶ乗り物の見た目と値段。", body, count="%d" % len(rows))


def titles_page():
    rows = []
    for k, v in sorted(_all_lang().items()):
        m = re.match(r"^title\.(skyabyss_rebirth|skyabyss)\.([a-z0-9_]+)$", k)
        if m and has(k + ".how"):
            rows.append('<tr data-name="%s" data-text="%s"><th scope="row">%s</th><td>%s</td></tr>' % (esc(v), esc(v + " " + lang(k + ".how")), esc(v), esc(lang(k + ".how"))))
            add_search(v, "称号", "wiki-titles.html")
    body = '<div class="wrap">%s</div>' % filters(False) + section("all", "称号（%d）" % len(rows), '<div class="tablewrap"><table><thead><tr><th scope="col">称号</th><th scope="col">手に入れ方</th></tr></thead><tbody>%s</tbody></table></div>' % "".join(rows),
                                                             "称号は名前の上か下に出せます。")
    wiki_page("wiki-titles.html", "称号", "全部の称号と手に入れ方。", body, count="%d" % len(rows))


def deeds_page():
    rows = []
    seen = set()
    for f in sorted(glob.glob(os.path.join(DATA, "advancement", "**", "*.json"), recursive=True)):
        a = load(f)
        disp = a.get("display") or {}
        t = disp.get("title", {})
        d = disp.get("description", {})
        tk = t.get("translate") if isinstance(t, dict) else None
        dk = d.get("translate") if isinstance(d, dict) else None
        if not tk or tk in seen:
            continue
        seen.add(tk)
        rows.append('<tr data-name="%s" data-text="%s"><th scope="row">%s</th><td>%s</td></tr>' % (
            esc(lang(tk)), esc(lang(tk) + " " + (lang(dk) if dk else "")), esc(lang(tk)), esc(lang(dk)).replace("\n", "<br>") if dk else ""))
        add_search(lang(tk), "記し", "wiki-deeds.html")
    body = '<div class="wrap">%s</div>' % filters(False) + section("all", "記し（%d）" % len(rows), '<div class="tablewrap"><table><thead><tr><th scope="col">記し</th><th scope="col">中身と報酬</th></tr></thead><tbody>%s</tbody></table></div>' % "".join(rows),
                                                             "L キーの記しの画面と同じものです。")
    wiki_page("wiki-deeds.html", "記し", "この mod の進捗（記し）の全部。", body, count="%d" % len(rows))


def lore_page():
    groups = {}
    for k, v in _all_lang().items():
        m = re.match(r"^lore\.skyabyss\.(.+)\.title$", k)
        if m and not m.group(1).startswith("chapter."):
            lid = m.group(1)
            ch = lid.split(".")[0] if "." in lid else "そのほか"
            groups.setdefault(ch, []).append((lid, v, lang("lore.skyabyss.%s.body" % lid)))
    ch_ja = {"prologue": "序章", "town": "都と町", "chapter1": "巨神編（一〜四章）", "ch1": "一章", "ch2": "二章", "ch3": "三章", "ch4": "四章",
             "ch5": "五章", "ch6": "六章", "finale": "終章"}
    body = '<div class="wrap">%s</div>' % filters(False)
    n = 0
    for ch in sorted(groups, key=lambda c: (list(ch_ja).index(c) if c in ch_ja else 99, c)):
        items = sorted(groups[ch])
        n += len(items)
        cards = "".join('<details class="lore" data-name="%s" data-text="%s"><summary>%s</summary><div><p>%s</p></div></details>' % (
            esc(t), esc(t), esc(t), esc(b).replace("\n", "<br>")) for _, t, b in items)
        body += section("ch-" + ch.replace(".", "-"), "%s（%d）" % (ch_ja.get(ch, ch), len(items)), cards)
    wiki_page("wiki-lore.html", "伝承の書", "伝承の書に記されるページ %d 件。物語の核心のネタばれを含みます。" % n, body, spoiler=True, count="%d ページ" % n)


# ================================================================== エンドコンテンツ
def _doc_cell(c):
    # 設計書の表の字の中の、作りの名前 (`code`) を落とす
    c = re.sub(r"（`[^`]*`）", "", c)
    c = re.sub(r"（\d\d の [^）]*）", "", c)  # 設計書どうしの参照 (「（03 の 13.4。欠点つき）」)
    c = re.sub(r"`[A-Za-z0-9_./:<>-]+`", "", c)
    return md_inline(c).strip()


def endgame_page():
    """物語のあとの遊びの全部 (tools/site/endgame.py の要約と、設計書の表)"""
    import endgame as EG
    by = {}
    for e in EG.ENTRIES:
        by.setdefault(e["group"], []).append(e)
    over = ""
    for gid, gname, _ in EG.GROUPS:
        for e in by.get(gid, []):
            over += '<tr><th scope="row"><a href="#%s">%s</a></th><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>' % (
                e["id"], esc(e["name"]), esc(gname), esc(e["when"]), esc(e["cadence"]), esc(e["reward"]))
    body = ('<div class="wrap"><p class="small">日替わり・週替わりは、どれも実時間です（朝 5 時で日が、月曜の朝 5 時で週が替わります。ゲームの中で寝ても替わりません）。'
            'U のクエストログの先頭の「今日・今週」のページに、替わるまでの時間がまとまっています。下の表の一部は設計書の表をそのまま載せています。</p></div>')
    body += section("all", "一覧（%d）" % len(EG.ENTRIES),
                    '<div class="tablewrap"><table><thead><tr><th scope="col">もの</th><th scope="col">群</th><th scope="col">始まる時</th>'
                    '<th scope="col">回数</th><th scope="col">主な報い</th></tr></thead><tbody>%s</tbody></table></div>' % over)
    for gid, gname, gnote in EG.GROUPS:
        inner = ""
        for e in by.get(gid, []):
            chips = "".join('<span class="chip%s">%s: %s</span>' % (" g" if k == "始まる時" else "", k, esc(v))
                            for k, v in (("始まる時", e["when"]), ("回数", e["cadence"]), ("報い", e["reward"])) if v and v != "—")
            inner += '<h3 id="%s">%s</h3><div class="chips">%s</div>' % (e["id"], esc(e["name"]), chips)
            inner += '<ul class="list" style="margin-top:10px">%s</ul>' % "".join("<li>%s</li>" % esc(b) for b in e["bullets"])
            for doc, heading, nth, cap in e["tables"]:
                head, rows_ = doc_table(doc, heading, nth)
                inner += ('<p class="small" style="margin-top:12px">%s</p><div class="tablewrap"><table><thead><tr>%s</tr></thead><tbody>%s</tbody></table></div>' % (
                    esc(cap), "".join('<th scope="col">%s</th>' % _doc_cell(h) for h in head),
                    "".join("<tr>%s</tr>" % "".join(("<th scope=\"row\">%s</th>" if k == 0 else "<td>%s</td>") % _doc_cell(c) for k, c in enumerate(r)) for r in rows_)))
            if e["links"]:
                inner += '<p class="small" style="margin-top:10px">関連: %s</p>' % "・".join('<a href="%s">%s</a>' % (h, esc(t)) for h, t in e["links"])
            add_search(e["name"], "エンドコンテンツ", "wiki-endgame.html#" + e["id"])
        body += section("g-" + gid, gname, inner, gnote)
    wiki_page("wiki-endgame.html", "エンドコンテンツ", "物語のあと（と、物語の途中から続けて遊べるもの）の遊びを全部。どこで始まり、何度でき、何がもらえるか。",
              body, spoiler=True, count="%d 種" % len(EG.ENTRIES))


_LANG_ALL = {}


def _all_lang():
    if not _LANG_ALL:
        for f in sorted(glob.glob(os.path.join(RES, "assets", "*", "lang", "ja_jp.json"))):
            _LANG_ALL.update(load(f))
    return _LANG_ALL


# ================================================================== 入口
def index_page(counts):
    tiles = "".join('<a class="wiki-tile" href="%s"><b>%s</b><span>%s</span><em>%s</em></a>' % (f, n, d, counts.get(f, "")) for f, n, d in PAGES)
    body = ('<div class="wrap"><p class="small">wiki の中身は、ゲームの中身（品の説明・敵の値・スキルツリー・クエスト・店・伝承）から作っています。'
            'ゲームの版が上がるたびに作り直します（今は v%s）。体力と攻撃の数字は、画面と同じ 10 倍です。</p></div>' % VERSION)
    body += section("cats", "ページ", '<div class="wiki-tiles">%s</div>' % tiles)
    wiki_page("wiki.html", "wiki", "天と淵のあいだの全部の情報。上の箱で名前から探せます。", body)


def build():
    dump = load(DUMP)
    items = dump["items"]
    names = {it["id"]: it["name"] for it in items}
    for it in items:
        k = classify(it)
        if k:
            _link_page[it["id"]] = {"weapon": "wiki-weapons.html", "armor": "wiki-armor.html", "accessory": "wiki-accessories.html"}.get(k, "wiki-items.html")
    if os.path.isdir(ICONS):
        shutil.rmtree(ICONS)
    _search.clear()
    COUNTS.clear()
    gear_pages(items)
    enemies_page(dump["mobs"])
    skills_page(dump["skills"])
    quests_page(names)
    endgame_page()
    shops_page(names)
    places_page()
    mounts_page()
    titles_page()
    deeds_page()
    lore_page()
    index_page(COUNTS)
    with open(os.path.join(SITE, "js", "wiki-search.json"), "w", encoding="utf8", newline="\n") as f:
        json.dump(_search, f, ensure_ascii=False, separators=(",", ":"))
    print("wiki: %d search entries" % len(_search))
