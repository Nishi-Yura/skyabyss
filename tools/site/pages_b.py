"""スキルとビルド・装備・旅嚢と道具・パーティとマルチ。"""
from sitelib import *
import skilldata
import pages_d


GEAR_TOC = [("weap", "13 種の武器", "Thirteen weapons"), ("tiers", "階級", "Tiers"), ("affix", "特性", "Affixes"), ("forge", "共鳴の炉", "Resonance Forge"), ("leg", "書き換えの武器", "Rewrite weapons"), ("acc", "装飾品", "Accessories"), ("armor", "防具", "Armor"), ("gearfree", "装備の自由度", "Gear freedom")]
POUCH_TOC = [("why", "旅嚢とは", "What is the satchel"), ("what", "入るもの", "What goes in"), ("use", "使い方", "Using it"), ("tools", "そのほかの道具", "Other tools")]
PARTY_TOC = [("party", "パーティを組む", "Forming a party"), ("shared", "背中の進みはパーティごと", "Per-party progress"), ("scale", "人数による強さ", "Scaling"), ("down", "倒れたとき", "Falling"), ("mark", "仲間の目印", "Party markers"), ("roles", "役目と設定", "Roles and settings")]
SKILL_TOC = [("grow", "成長", "Growth"), ("trees", "五つの木と技", "Trees and skills"), ("calling", "天職", "Callings"), ("runes", "刻印と書き換え", "Runes and rewrites"), ("keys", "要", "Keystones"), ("bp", "ステータスの刻み目", "Stat breakpoints"), ("types", "武器の型", "Weapon types"), ("freedom", "自由に混ぜる", "Mixing freely"), ("respec", "振り直しと型の保存", "Respec and loadouts")]


def skills():
    sk, tn = skilldata.load()
    by = {t: [s for s in sk if s["tree"] == t] for t in skilldata.TREES}
    cards = []
    lists = []
    for t in skilldata.TREES:
        nm = tn[t]
        meta = skilldata.TREE_META[t]
        idx = {s["id"]: s["idx"] for s in sk}
        nodes = [(s["idx"], s["ja"], s["en"], s["kind"], s["row"], s["col"], [idx[p] for p in s["pre"]]) for s in by[t]]
        svg = tree_svg(nodes, nm[0], nm[1])
        cards.append('<div class="panel tree-card"><h3><img class="px" src="%s" width="32" height="32" alt="" style="vertical-align:middle;margin-right:6px">%s</h3><p class="meta">%s</p>%s</div>' % (
            meta[4], T(nm[0], nm[1]), T("%s ／ %s" % (meta[0], meta[1]), "%s / %s" % (meta[2], meta[3])), svg))
        lists.append('<h3 id="list-%s">%s</h3><div class="skill-list">%s</div>' % (
            t, T(nm[0] + "の技", nm[1] + " skills"), "".join(skill_card(s) for s in by[t])))
    legend = '<ul class="legend"><li><i class="a"></i>%s</li><li><i></i>%s</li><li><i class="k"></i>%s</li></ul>' % (
        T("使う技（枠に置いて使う）", "Active (put in a slot)"), T("パッシブ（覚えるだけで効く）", "Passive (works once learned)"),
        T("要（かなめ。癖のある常時の技）", "Keystone (quirky always-on skill)"))

    ptable = table([T("レベル", "Level"), "1", "5", "10", "15", "21", "27", "33", "40"],
                   [[T("技の点（合計）", "Skill points (total)"), "1", "3", "5", "10", "16", "22", "28", "35"]])

    traits = table([T("天職", "Calling"), T("天性（一つ目の天職だけ）", "Innate trait (first calling only)")], [
        [T("岩守り", "Rockwarden"), T("岩の肌: 強靭 +0.5、ノックバック耐性 +10%", "Stone Skin: +0.5 toughness, +10% knockback resistance")],
        [T("風読み", "Windreader"), T("移動 +3%", "+3% movement speed")],
        [T("潮歌い", "Tidesinger"), T("歌い手: 最大マナ +10、仲間に向ける技の範囲 +1 ブロック", "Singer: +10 max mana, +1 block range on skills aimed at allies")],
        [T("灰の鍛え人", "Ashsmith"), T("炉の手: 武器の強化の上乗せ +10%、火の傷 −10%", "Forge Hand: +10% weapon enhancement bonus, -10% fire damage")],
        [T("骨の射手", "Boneshooter"), T("骨の目: 飛び道具 +5%、撃った矢の 25% は戻る", "Bone Eye: +5% projectile damage, 25% of arrows return")],
    ])

    def sname(i):
        s = [x for x in sk if x["id"] == i][0]
        return T(s["ja"], s["en"])

    legend_rows = [[item_li_inline("captain_pike"), sname("lunge")], [item_li_inline("waterway_fang"), sname("wind_rend")],
                   [item_li_inline("tidecaller_claw"), sname("whale_sweep")], [item_li_inline("coral_harpoon"), sname("ebb_tide")],
                   [item_li_inline("raiju_claw"), sname("backstep")], [item_li_inline("warden_glaive"), sname("rock_call")],
                   [item_li_inline("smith_hammer"), sname("ember_hammer")], [item_li_inline("thrall_poker"), sname("temper")]]

    keys = table([T("木", "Tree"), T("要", "Keystone"), T("働き", "Effect")], [
        [T("岩守り", "Rockwarden"), sname("stone_oath"), T("8 ブロック以内の仲間が受ける傷の 10% を自分が受ける（1 回 1 ハートまで）", "Take 10% of damage dealt to allies within 8 blocks (max 1 heart at a time)")],
        [T("風読み", "Windreader"), sname("lone_wind"), T("16 ブロック以内に他のプレイヤーがいない間、攻撃力 +12%・移動 +6%。一人遊びの木", "While no other player is within 16 blocks: +12% attack, +6% speed. The solo tree")],
        [T("潮歌い", "Tidesinger"), sname("chorus_voice"), T("8 ブロック以内の仲間 1 人につき（最大 5）、技の効果 +4%", "+4% skill effect per ally within 8 blocks (up to 5)")],
        [T("灰の鍛え人", "Ashsmith"), sname("smith_eye"), T("手の武器の強化段階 1 つにつき技の傷 +1.5%（+10 で +15%）", "+1.5% skill damage per weapon enhancement level (+15% at +10)")],
        [T("骨の射手", "Boneshooter"), sname("still_string"), T("3 秒動かずに引いた矢の威力 +25%。動くとやり直し", "Arrows drawn after 3 s of standing still deal +25%; moving resets it")],
        [T("旅人", "Wayfarer"), sname("journal"), T("戦っていないあいだなら、どこでも刻印と型の保存を切り替えられる", "Outside combat you can swap runes and loadouts anywhere")],
    ])

    bp = table([T("ステータス", "Stat"), T("15 で", "At 15"), T("30 で", "At 30")], [
        [T("筋力", "Strength"), T("重い武器の振り +8%", "Heavy weapon swings +8%"), T("範囲の技の押し出し ×1.25", "Area skill knockback x1.25")],
        [T("敏捷", "Agility"), T("予告をよけた直後 2 秒、移動 +8%", "+8% speed for 2 s after dodging a telegraph"), T("踏み込み・跳び退き・風裂きの待ち −10%", "Lunge, Backstep, Wind Rend cooldowns -10%")],
        [T("精神", "Spirit"), T("マナの回復 +10%", "+10% mana regeneration"), T("仲間に向ける技（唄・合図）の効果 +10%", "+10% effect of ally-targeted skills (songs, signal)")],
        [T("耐久", "Vitality"), T("ノックバック耐性 +10%", "+10% knockback resistance"), T("火・毒の傷 −15%", "-15% fire and poison damage")],
    ])

    types = table([T("型", "Type"), T("武器", "Weapons"), T("技との噛み合い（縛りはなし）", "Synergy (never required)")], [
        [T("重", "Heavy"), T("鯨骨の大剣・余燼の戦斧・鍛え手の大鎚", "Whalebone Greatsword, Ember War Axe, Smith's Sledge"), T("巨神斬り・熾火の槌・焼き入れが強くなる", "Boosts Titan Cleave, Ember Hammer, Temper")],
        [T("長", "Long"), T("番兵長の鉾・潮殻の三叉槍・珊瑚憑きの銛・羽守の鉤槍・灰被りの火掻き", "Captain's Glaive, Tide Shell Trident, Coral Thrall's Harpoon, Warden's Hooked Glaive, Thrall's Poker"), T("踏み込み・引き潮の届き +1 ブロック、鯨尾薙ぎの半径 +0.5", "+1 block reach on Lunge and Ebb Tide, +0.5 radius on Whale-Tail Sweep")],
        [T("速", "Fast"), T("水路の顎牙・雷獣の爪・潮招きの大鋏", "Waterway Fang, Raiju Claw, Tidecaller's Claw"), T("風裂きの倍率、見切りの持続など", "Wind Rend multiplier, Foresight duration, and more")],
        [T("遠", "Ranged"), T("雷翼の弓", "Thunder Wing Bow"), T("骨の射手の技の貫通・印・遠さ", "Piercing, marking and range of Boneshooter skills")],
        [T("守", "Guard"), T("珊瑚の盾", "Coral Shield"), T("岩呼びの持続、岩の構えの防具", "Rock Call duration, Stone Stance armor")],
    ])

    resp = table([T("できること", "Action"), T("決まり", "Rule")], [
        [T("技の振り直し", "Respec skills"), T("Lv 15 まではどこでも無料。そのあとは共鳴の炉のそばでいつでも共鳴の結晶 1（旅人の目利きがあれば無料）", "Free anywhere until Lv 15. After that, near a Resonance Forge at any time for 1 resonance crystal (free with the Wayfarer's Eye)")],
        [T("戻るもの", "What returns"), T("覚えた技・刻印・要がすべて戻り、技の点は全部戻る。天職は変わらない", "All learned skills, runes and keystones reset and every point is refunded. Your calling stays")],
        [T("刻印の付け替え", "Swap runes"), T("戦いの外で、炉か焚き火の近く（旅の手帖があればどこでも）。初回は無料、以後は結晶 1", "Outside combat near a forge or campfire (anywhere with the Traveler's Journal). First swap free, then 1 crystal")],
        [T("型の保存", "Loadouts"), T("枠・刻印・連鎖の組を 4 つ保存でき、戦いの外でいつでも切り替えられる", "Save four sets of slots, runes and chains and switch outside combat")],
        [T("ステータスの振り直し", "Respec stats"), T("技と同じ炉・結晶・費用の表。回数は技と別に数える", "Same forge and cost table as skills; counted separately")],
    ])

    body = page_head("スキルとビルド", "Skills and builds", "5 つの木と旅人、36 の技、天職、刻印、要、型。選ぶほど、遊び方が変わります。",
                     "Five trees plus the Wayfarer, 36 skills, callings, runes, keystones and weapon types. What you choose changes how you play.") + toc(SKILL_TOC) + """
<section aria-labelledby="grow">
<div class="wrap">
<h2 id="grow">%(h_grow)s</h2>
<p class="lead">%(grow)s</p>
<div class="grid g2">
<div class="panel"><h3>%(g1h)s</h3><p>%(g1)s</p></div>
<div class="panel"><h3>%(g2h)s</h3><p>%(g2)s</p></div>
</div>
%(ptable)s
<p class="muted"><small>%(pnote)s</small></p>
</div>
</section>

<section class="alt" aria-labelledby="trees">
<div class="wrap">
<h2 id="trees">%(h_trees)s</h2>
<p>%(trees_lead)s</p>
%(legend)s
<div class="trees">%(cards)s</div>
<h3 style="margin-top:1.4em">%(h_lists)s</h3>
%(lists)s
</div>
</section>

<section aria-labelledby="calling">
<div class="wrap">
<h2 id="calling">%(h_call)s</h2>
<p class="lead">%(call)s</p>
<ul>
<li>%(c1)s</li><li>%(c2)s</li><li>%(c3)s</li><li>%(c4)s</li>
</ul>
%(traits)s
</div>
</section>

<section class="alt" aria-labelledby="runes">
<div class="wrap">
<h2 id="runes">%(h_run)s</h2>
<p class="lead">%(run)s</p>
<ul><li>%(r1)s</li><li>%(r2)s</li><li>%(r3)s</li></ul>
<h3>%(h_leg)s</h3>
<p>%(leg)s</p>
%(legt)s
</div>
</section>

<section aria-labelledby="keys">
<div class="wrap">
<h2 id="keys">%(h_key)s</h2>
<p>%(key)s</p>
%(keyt)s
</div>
</section>

<section class="alt" aria-labelledby="bp">
<div class="wrap">
<h2 id="bp">%(h_bp)s</h2>
<p>%(bp)s</p>
%(bpt)s
</div>
</section>

<section aria-labelledby="types">
<div class="wrap">
<h2 id="types">%(h_types)s</h2>
<p>%(types)s</p>
%(typet)s
</div>
</section>

%(freedom)s
<section class="alt" aria-labelledby="respec">
<div class="wrap">
<h2 id="respec">%(h_resp)s</h2>
%(respt)s
<p>%(mk)s <a href="gear.html#affix">%(mk2)s</a></p>
</div>
</section>
""" % dict(
        h_grow=T("成長の仕組み", "How you grow"),
        grow=T("レベルが上がると、力の振り分けと技の点が増えます。5 つの木と旅人は、最初から全部の行が取れます。天職は木を開く鍵ではなく、あなたの専門です。",
               "Leveling grants stat points and skill points. Every tree and the Wayfarer is open from the start; a calling is not a key but your specialty."),
        g1h=T("4 つのステータス", "Four stats"), g1=T("筋力・敏捷・精神・耐久を、ステータス画面（<kbd>J</kbd>）で振り分けます。15 と 30 に小さな分かれ目があります。", "Spend points on Strength, Agility, Spirit and Vitality in the status screen (<kbd>J</kbd>). Each has small breakpoints at 15 and 30."),
        g2h=T("技の点", "Skill points"), g2=T("技の画面（<kbd>K</kbd>）で、1 段 1 点。どの技も同じ値段です。Lv 10 までは 1 レベルおきに増え、そのあとは 1 レベルごとに 1 点です。", "Each rank costs 1 point in the skill screen (<kbd>K</kbd>). Points come every other level up to Lv 10, then 1 per level."),
        ptable=ptable, pnote=T("三本の木を極めることはできません。何を選ぶかが、ビルドになります。", "You cannot max out three trees. What you pick is your build."),
        h_trees=T("五つの木と旅人", "Five trees and the Wayfarer"),
        trees_lead=T("1 本の木は 6 つの技。1 行目に入口の 2 つ、2 行目に 2 つ、3 行目に奥義の使う技、4 行目に要です。行ごとに必要レベルがあります（1・5・12・20）。線は前提です。",
                     "Each tree has six skills: two entry skills in row 1, two in row 2, a signature active in row 3 and a keystone in row 4. Rows unlock at levels 1, 5, 12 and 20. Lines show prerequisites."),
        legend=legend, cards="".join(cards), h_lists=T("技の一覧", "Skill lists"), lists="".join(lists),
        h_call=T("天職", "Callings"),
        call=T("天職は専門です。木はどれも最初から開いています。選んだ木の要を授かり、天性が付き、その木の技 3 つぶんが 1 点ずつ安くなります。", "A calling is a specialty. Every tree is already open. Choosing one grants that tree's keystone, an innate trait and a one-point discount on three of its skill ranks."),
        c1=T("岩鯨の心臓を止め、心臓の報酬を選び終えると、天職を選ぶ画面が開きます。「あとで」にしても、技の画面から選べます。", "After you stop the Stone Whale's heart and choose its reward, a calling screen opens. You can postpone it and pick from the skill screen later."),
        c2=T("一つ目の天職: その木の要を点なし・Lv 条件なしで授かり、天性が付き、その木の技の段（要・刻印は除く）3 つぶんが 1 点ずつ安くなります（戻る点）。", "First calling: you receive the tree's keystone free of points and level, gain the innate trait, and three of its skill ranks (not keystone or runes) cost 1 point less, refunded."),
        c3=T("二つ目の天職（雷翼の心臓を止めたあと）: その木の要を授かり、5 つ目の技の枠が開きます。天性と割引はありません。", "Second calling (after Thunder Wing's heart): you receive that tree's keystone and a fifth skill slot unlocks. No innate trait or discount."),
        c4=T("天職は転生で空に戻り、選び直せます。変えるには画面で二度押します。", "Callings reset on rebirth so you can pick again. Confirm a change by pressing twice."),
        traits=traits,
        h_run=T("刻印（ルーン）", "Runes"), run=T("使う技のうち 10 本には 3 択の刻印があり、技の形が変わります。", "Ten active skills each have three runes that reshape the skill."),
        r1=T("対象: 巨神斬り・鯨尾薙ぎ・岩呼び・踏み込み・跳び退き・風裂き・守りの唄・引き潮・熾火の槌・雲穿ち", "Skills: Titan Cleave, Whale-Tail Sweep, Rock Call, Lunge, Backstep, Wind Rend, Ward Song, Ebb Tide, Ember Hammer, Cloud Piercer"),
        r2=T("技を極める（最大段）と開きます。天職の前でも、どの木の技でも選べます。最初に選ぶのは 1 点です。", "Maxing a skill unlocks its runes, in any tree and even before choosing a calling. Choosing the first rune costs 1 point."),
        r3=T("付け替えは戦いの外で、炉・焚き火の近く。初回は無料、あとは共鳴の結晶 1。同時に効くのは 1 つです。", "Swap outside combat near a forge or campfire. The first swap is free, then 1 resonance crystal. Only one rune is active at a time."),
        h_leg=T("書き換えの武器（4 番目の刻印）", "Rewrite weapons (the fourth rune)"),
        leg=T("8 本の書き換えの武器が、1 つの技を別の形に書き換えます。その武器を主の手に持つあいだだけ選べて効きます。", "Eight rewrite weapons each change one skill. They can be chosen and work only while held in your main hand."),
        legt=table([T("書き換えの武器", "Rewrite weapon"), T("書き換わる技", "Skill rewritten")], legend_rows),
        h_key=T("要（かなめ）", "Keystones"), key=T("各木の 4 行目。得と引き換えに癖のある、パッシブです。段は 1 つだけ。", "Row 4 of each tree: a quirky always-on skill with a trade-off. One rank only."), keyt=keys,
        h_bp=T("ステータスの刻み目", "Stat breakpoints"), bp=T("4 つのステータスそれぞれに、15 と 30 で小さな分かれ目があります（計 8 つ）。全部を 30 にするには力が 120 要るので、どこを届かせるかが選びです。", "Each stat has a small breakpoint at 15 and 30 (eight in total). Getting all four to 30 takes 120 points, so where to reach is a choice."), bpt=bp,
        h_types=T("武器の型", "Weapon types"), types=T("13 種の武器は 5 つの型に分かれます。合う型の武器を持つと、技に小さなおまけが付きます。縛りはありません。", "The 13 weapons fall into five types. Holding a matching type gives a small bonus; nothing is ever required."), typet=types,
        freedom=pages_d.skills_extra(),
        h_resp=T("振り直しと型の保存", "Respec and loadouts"), respt=resp,
        mk=T("装備の「木の印」の特性は、その木の技の力を伸ばします。", "The \"tree mark\" affixes on gear boost the power of that tree's skills."), mk2=T("装備のページへ", "See the gear page"),
    )
    page("skills.html", "スキルとビルド — 天と淵のあいだ", "Skills and builds — Between the Sky and the Abyss",
         "5 つの木と旅人、36 の技、天職、刻印、要、型、振り直し。", "Five trees plus the Wayfarer, 36 skills, callings, runes, keystones, weapon types and respec.", body)


# 技の見た目 (site/img/skill_fx/<技>.webp の動く絵と <技>_poster.webp の止めた絵。tools/site/encode_skill_fx_clips.py が作る)
ELEMENT = {"rock": ("岩", "Rock"), "wind": ("風", "Wind"), "tide": ("潮", "Tide"), "ash": ("灰", "Ash"),
           "bone": ("骨", "Bone"), "traveler": ("旅", "Traveler")}


def skill_card(s):
    """技ひとつぶんの開閉パネル。中身は、一行の説明・属性・見た目 (放ってから消えるまでの動く絵)・最大段。
    動く絵は、パネルを開いたときに初めて読む (それまでは止めた絵)。動きを減らす設定の人は、再生ボタンを押したときに読む (js/main.js)。"""
    sid = s["id"]
    kind = {"A": ("使う", "Active"), "P": ("パッシブ", "Passive"), "K": ("要", "Keystone")}[s["kind"]]
    el = ELEMENT[s["tree"]]
    fx_dir = os.path.join(ROOT, "site", "img", "skill_fx")
    if os.path.exists(os.path.join(fx_dir, sid + ".webp")) and os.path.exists(os.path.join(fx_dir, sid + "_poster.webp")):
        alt_ja = "%s の見た目（動く絵）" % s["ja"]
        alt_en = "%s: the look in motion" % s["en"]
        visual = ('<div class="fx-frames"><figure class="fx-clip" data-src="img/skill_fx/%(id)s.webp">'
                  '<div class="fx-stage">'
                  '<img class="fx-poster" src="img/skill_fx/%(id)s_poster.webp" width="640" height="360" loading="lazy" '
                  'alt="%(alt_ja)s" data-alt-ja="%(alt_ja)s" data-alt-en="%(alt_en)s">'
                  '<span class="fx-slot" aria-hidden="true"></span>'
                  '<button type="button" class="fx-play" hidden>%(play)s</button>'
                  '</div><figcaption>%(cap)s</figcaption></figure></div>') % dict(
            id=sid, alt_ja=esc(alt_ja), alt_en=esc(alt_en),
            play=T("▶ 動く絵を見る", "▶ Play the clip"),
            cap=T("放ってから消えるまで（実時間）", "From cast to fade (real time)"))
    elif s["kind"] == "A":
        visual = '<p class="fx-none">%s</p>' % T("見た目は撮影を待っています。", "The in-game look is waiting for its shots.")
    else:
        visual = '<p class="fx-none">%s</p>' % T("パッシブなので、画面に出る見た目はありません。", "This skill is always on, so there is no on-screen visual.")
    return (
        '<details class="skill" id="skill-%(id)s"><summary>'
        '<img class="ic px" src="img/skill/%(idx)02d.png" width="32" height="32" alt="">'
        '<span class="sk-name">%(name)s</span>'
        '<span class="tag">%(kind)s</span><span class="tag tag-el">%(el)s</span>'
        '</summary><div class="sk-body">'
        '<p class="sk-desc">%(desc)s</p>'
        '<p class="sk-meta">%(el_line)s　%(rank)s</p>'
        '%(visual)s</div></details>' % dict(
            id=sid, idx=s["idx"], name=T(s["ja"], s["en"]), kind=T(*kind), el=T(el[0] + "属性", el[1] + " tree"),
            desc=T(s["dja"], s["den"]),
            el_line=T("属性: %s" % el[0], "Element: %s" % el[1]),
            rank=T("最大 %d 段" % s["rank"], "Max rank %d" % s["rank"]), visual=visual))


def item_li_inline(iid):
    ja, en = item_name(iid)
    return icon("img/item/%s.png" % iid, ja, en) + T(ja, en)


# ------------------------------------------------------------------- 装備
WEAPONS = [
    ("whalebone_greatsword", "重", "Heavy", "岩鯨", "Stone Whale", "エピック", "Epic"),
    ("captain_pike", "長", "Long", "岩鯨", "Stone Whale", "ヒロイック", "Heroic"),
    ("waterway_fang", "速", "Fast", "岩鯨", "Stone Whale", "ヒロイック", "Heroic"),
    ("tide_shell_trident", "長", "Long", "潮殻", "Tide Shell", "エピック", "Epic"),
    ("coral_shield", "守", "Guard", "潮殻", "Tide Shell", "エピック", "Epic"),
    ("tidecaller_claw", "速", "Fast", "潮殻", "Tide Shell", "ヒロイック", "Heroic"),
    ("coral_harpoon", "長", "Long", "潮殻", "Tide Shell", "ヒロイック", "Heroic"),
    ("thunder_wing_bow", "遠", "Ranged", "雷翼", "Thunder Wing", "エピック", "Epic"),
    ("raiju_claw", "速", "Fast", "雷翼", "Thunder Wing", "ヒロイック", "Heroic"),
    ("warden_glaive", "長", "Long", "雷翼", "Thunder Wing", "ヒロイック", "Heroic"),
    ("ember_war_axe", "重", "Heavy", "余燼", "Ember", "エピック", "Epic"),
    ("smith_hammer", "重", "Heavy", "余燼", "Ember", "ヒロイック", "Heroic"),
    ("thrall_poker", "長", "Long", "余燼", "Ember", "ヒロイック", "Heroic"),
]


def gear():
    wrows = []
    for iid, tj, te, nj, ne, rj, re_ in WEAPONS:
        wrows.append([item_li_inline(iid), T(tj, te), T(nj, ne), T(rj, re_)])
    tiers = table([T("階級", "Tier"), T("特性の数", "Affixes"), T("主な入手元", "Main source"), T("役割", "Role")], [
        [T("コモン", "Common"), "0", T("雑魚、通常の宝箱", "Trash mobs, normal chests"), T("素材・入門用", "Materials, entry level")],
        [T("アンコモン", "Uncommon"), "0", T("宝箱、広間の撃破", "Chests, hall clears"), T("序盤の繋ぎ（食料・矢など）", "Early stopgaps (food, arrows)")],
        [T("レア（青）", "Rare (blue)"), "1–2", T("心臓の図鑑の品（甲殻・基本の武器・護符）", "Heart codex items (armor, base weapons, charms)"), T("各巨神の主力", "Mainstay of each titan")],
        [T("エピック（紫）", "Epic (purple)"), "1–2", T("心臓・中ボスの通常の報い、交換", "Regular hearts and mid-boss rewards, trading"), T("巨神の基本の武器と、護符・装飾", "Base weapons, charms and accessories")],
        [T("ヒロイック（橙）", "Heroic (orange)"), "2–3", T("心臓・中ボスの低確率、初回の選択、天井保証", "Low-chance drops from hearts and mid-bosses, first-clear choice, pity guarantee"), T("専用の武器と、技を書き換える武器", "Dedicated weapons, and the weapons that rewrite skills")],
        [T("アーティファクト（金）", "Artifact (gold)"), "2–3", T("深淵の心臓と番人", "Abyss hearts and the warden"), T("深淵の専用装備", "Abyss-only gear")],
    ])
    affix = table([T("付く先", "On"), T("効果の例", "Example effect"), T("幅（レア）", "Range (Rare)")], [
        [T("武器", "Weapon"), T("攻撃力 +5%", "+5% attack"), "4–8%"],
        [T("武器", "Weapon"), T("攻撃速度 +6%", "+6% attack speed"), "4–10%"],
        [T("武器", "Weapon"), T("吸血 3%（0.25 秒に一度、1 回 2 まで）", "3% lifesteal (once per 0.25 s, max 2 per hit)"), "2–4%"],
        [T("武器", "Weapon"), T("最大マナ +7", "+7 max mana"), "4–10"],
        [T("武器", "Weapon"), T("技の待ち −6%（合計の上限 15%）", "-6% skill cooldown (cap 15%)"), "4–10%"],
        [T("武器", "Weapon"), T("木の印: 岩・風・潮・灰・骨の技の力 +6%（1 装備に 1 つまで）", "Tree mark: +6% power for Rock, Wind, Tide, Ash or Bone skills (max one per item)"), "5–10%"],
        [T("防具", "Armor"), T("防具強度 +0.5", "+0.5 armor toughness"), "0.5–1.0"],
        [T("防具", "Armor"), T("最大体力 +0.5 ハート", "+0.5 max hearts"), "+0.25–0.5"],
        [T("防具", "Armor"), T("移動速度 +2%", "+2% movement speed"), "1.5–3%"],
        [T("防具", "Armor"), T("受ける傷 −2%（合計の上限 10%）", "-2% damage taken (cap 10%)"), "1.5–3%"],
        [T("防具", "Armor"), T("最大マナ +4", "+4 max mana"), "3–6"],
        [T("防具", "Armor"), T("ノックバック耐性 +8%", "+8% knockback resistance"), "5–10%"],
    ])
    levels = table([T("巨神", "Titan"), T("コモン〜エピックの必要 Lv", "Common to Epic"), T("ヒロイック以上の必要 Lv", "Heroic and above")], [
        [T("岩鯨（推奨 12）", "Stone Whale (rec. 12)"), "8", "10"], [T("潮殻（推奨 18）", "Tide Shell (rec. 18)"), "14", "16"],
        [T("雷翼（推奨 24）", "Thunder Wing (rec. 24)"), "20", "22"], [T("余燼（推奨 30）", "Ember (rec. 30)"), "26", "28"]])
    armor = "".join('<li>%s</li>' % item_li_inline(i) for i in ("stone_whale_chestplate", "tide_shell_chestplate", "thunder_wing_chestplate", "ember_chestplate"))
    acc = "".join('<li>%s</li>' % item_li_inline(i) for i in ("resonance_charm", "tide_chime_charm", "storm_charm", "kindling_charm"))
    acc2 = "".join('<li>%s</li>' % item_li_inline(i) for i in ("stone_whale_plating", "moss_talisman", "moontide_pearl", "storm_anklet", "storm_eye_feather", "ember_mantle", "ember_lantern"))
    body = page_head("装備", "Gear", "13 種の武器、階級と特性、共鳴の炉、装飾品、防具。周回して揃えます。",
                     "Thirteen weapons, tiers and affixes, the Resonance Forge, accessories and armor. Collect them across repeat runs.") + toc(GEAR_TOC) + """
<section aria-labelledby="weap">
<div class="wrap">
<h2 id="weap">%(h_weap)s</h2>
<p class="lead">%(weap)s</p>
%(wt)s
<p>%(wnote)s</p>
</div>
</section>

<section class="alt" aria-labelledby="tiers">
<div class="wrap">
<h2 id="tiers">%(h_tiers)s</h2>
%(tt)s
<p>%(tnote)s</p>
<ul><li>%(t1)s</li><li>%(t2)s</li><li>%(t3)s</li></ul>
</div>
</section>

<section aria-labelledby="h-affix" id="affix">
<div class="wrap">
<h2 id="h-affix">%(h_aff)s</h2>
<p>%(aff)s</p>
%(at)s
<ul><li>%(a1)s</li><li>%(a2)s</li><li>%(a3)s</li></ul>
</div>
</section>

<section class="alt" aria-labelledby="h-forge" id="forge">
<div class="wrap">
<h2 id="h-forge">%(h_forge)s</h2>
<p class="lead">%(forge)s</p>
<div class="grid g2">
<div class="panel"><h3>%(f1h)s</h3><p>%(f1)s</p></div>
<div class="panel"><h3>%(f2h)s</h3><p>%(f2)s</p></div>
<div class="panel"><h3>%(f3h)s</h3><p>%(f3)s</p></div>
<div class="panel"><h3>%(f4h)s</h3><p>%(f4)s</p></div>
</div>
</div>
</section>

%(gfree)s
<section aria-labelledby="leg">
<div class="wrap">
<h2 id="leg">%(h_leg)s</h2>
<p>%(leg)s <a href="skills.html#runes">%(leg2)s</a></p>
</div>
</section>

<section class="alt" aria-labelledby="acc">
<div class="wrap">
<h2 id="acc">%(h_acc)s</h2>
<p class="lead">%(acc)s</p>
<div class="grid g2">
<div class="panel"><h3>%(c1h)s</h3><ul class="iconrow">%(accl)s</ul><p>%(c1)s</p></div>
<div class="panel"><h3>%(c2h)s</h3><ul class="iconrow">%(acc2l)s</ul><p>%(c2)s</p></div>
</div>
</div>
</section>

<section aria-labelledby="armor">
<div class="wrap">
<h2 id="armor">%(h_armor)s</h2>
<p>%(armor)s</p>
<ul class="iconrow">%(armorl)s</ul>
<ul><li>%(ar1)s</li><li>%(ar2)s</li></ul>
<h3>%(h_lv)s</h3>
<p>%(lv)s</p>
%(levels)s
</div>
</section>
""" % dict(
        h_weap=T("13 種の武器", "Thirteen weapons"),
        weap=T("巨神ごとに、エピックの基本の武器と、ヒロイックの専用武器があります。剣・鉾・短剣・槍・銛・盾・弓・斧・鎚と、形もさまざまです。", "Each titan drops Epic base weapons and Heroic dedicated ones, in many shapes: sword, glaive, dagger, trident, harpoon, shield, bow, axe and hammer."),
        wt=table([T("武器", "Weapon"), T("型", "Type"), T("巨神", "Titan"), T("階級", "Tier")], wrows),
        wnote=T("型は技との噛み合いです。<a href=\"skills.html#types\">スキルのページ</a>を見てください。", "Types describe synergy with skills; see <a href=\"skills.html#types\">the skills page</a>."),
        h_tiers=T("階級", "Tiers"), tt=tiers,
        tnote=T("武器の見た目は階級で輪郭が変わります。コモンからレアで部品が一つ増え、ヒロイック以上は輪郭を変える大きな部品が付きます。強化が +5 以上（覚醒）になると、縁の金属が一段明るくなり、刃の中心に光の筋が入ります。", "A weapon's silhouette changes with tier: Common to Rare adds a part, and Heroic and above add a large part that changes the outline. Enhancement of +5 or more (awakening) brightens the rim metal and adds a glowing line to the blade."),
        t1=T("入手は個人ごとの抽選です。他人が取っても、自分の機会は減りません。", "Drops are rolled per player; someone else's drop never reduces your chance."),
        t2=T("「巨神の記憶」の天井（保証）があり、運が悪い周回が続いても報いがあります。", "A \"titan memory\" pity system guarantees a Heroic item after a run of bad luck."),
        t3=T("人数が増えると敵と心臓は強くなりますが、報酬は減りません。", "More players make enemies and hearts tougher, but rewards are never reduced."),
        h_aff=T("特性（アフィックス）", "Affixes"),
        aff=T("レア以上の武器と防具には特性が付きます（レア・エピック 1〜2、ヒロイック・アーティファクト 2〜3）。値は幅の中でランダムで、ヒロイック以上は幅が 1.25 倍です。", "Rare and better weapons and armor carry affixes (Rare and Epic 1-2, Heroic and Artifact 2-3). Values roll within a range; Heroic and above use 1.25x ranges."),
        at=affix,
        a1=T("同じ種類は一つの装備に二度付きません。", "The same affix never appears twice on one item."),
        a2=T("武器の特性は利き手に持つとき、防具の特性は着ているときだけ効きます。", "Weapon affixes work when held in the main hand; armor affixes while worn."),
        a3=T("護符・護石・装飾品には付きません（重ねて持てるため）。", "Charms, talismans and accessories carry none, since they stack in slots."),
        h_forge=T("共鳴の炉", "Resonance Forge"),
        forge=T("岩鯨の背に着いた台などにある炉です。作ることもできます。何も預からないので、何人でも同時に使えます。", "Found near the Stone Whale's tail platform, and craftable. It holds nothing, so any number of players can use it at once."),
        f1h=T("強化（+0〜+10）", "Enhance (+0 to +10)"), f1=T("その巨神の素材と共鳴の結晶で 1 段ずつ上げます。失敗はありません。武器は 1 段ごとに攻撃力の 3%（+10 で ×1.3）、防具は 1 段 1 部位ごとに強靭 +0.3。+5 で覚醒し、固有効果が強まります。", "Spend that titan's materials and resonance crystals to raise one step at a time; no failure. Weapons gain 3% attack per step (x1.3 at +10), armor +0.3 toughness per piece per step. At +5 it awakens and unique effects strengthen."),
        f2h=T("分解", "Dismantle"), f2=T("ヒロイック以上は共鳴の欠片 10 ＋素材の半分、それより下の階級はその巨神の素材になります。特性の付く品は共鳴の結晶も出ます。", "Heroic and above yield 10 resonance fragments plus half the materials; lower tiers yield that titan's materials. Items with affixes also give resonance crystals."),
        f3h=T("再鍛造", "Reforge"), f3=T("結晶 1 つで、特性をすべて振り直した候補を 1 組作ります（3 組まで）。候補のどれか、または「今のまま」を選んで確定するので、弱くなることはありません。", "One crystal makes a candidate set with all affixes rerolled (up to three). You pick one or \"keep current\", so you can never end up worse."),
        f4h=T("交換", "Trade"), f4=T("巨神の共鳴の欠片で、その巨神の専用の品 3 つから好きな物を選んで手に入れます。", "Spend a titan's resonance fragments to pick any of that titan's three dedicated items."),
        gfree=pages_d.gear_extra(),
        h_leg=T("書き換えの武器", "Rewrite weapons"),
        leg=T("書き換えの武器 8 本は、持っているあいだ、特定の技を別の形に書き換えられます。", "Eight rewrite weapons can change a specific skill while you hold them."), leg2=T("一覧はスキルのページへ。", "See the list on the skills page."),
        h_acc=T("装飾品", "Accessories"),
        acc=T("護符と装飾品は、持っているだけでは効きません。持ち物の画面の右上の「装飾」のつまみから開く専用の枠に付けたときだけ効きます。持ち物にある品を右クリックしても付けられます。", "Charms and accessories do nothing from the inventory. They work only in the dedicated slots opened from the \"Accessories\" tab at the top right of the inventory; right-clicking an item in your inventory also equips it."),
        c1h=T("護符（1 枠）", "Charm (1 slot)"), accl=acc, c1=T("使って効く品です。<kbd>H</kbd> で使い、待ち時間があります。", "Used actively with <kbd>H</kbd>; has a cooldown."),
        c2h=T("装飾（2 枠）", "Accessories (2 slots)"), acc2l=acc2, c2=T("付けているあいだ効く品です。巨神ごとの「鼓動守」は、心臓の戦いで一度だけ致命の一撃を体力 1 で耐えます。", "Work while worn. Each titan's Heartbeat Ward lets you survive one fatal blow at 1 health, once per heart fight."),
        h_armor=T("防具", "Armor"),
        armor=T("巨神ごとに、立体の防具一式（兜・胸当て・脚・靴）があります。防御力そのものではなく、強靭（防具強度）と特性で強くなります。", "Each titan has a full 3D armor set (helmet, chestplate, leggings, boots). They scale through toughness and affixes rather than raw defense."),
        armorl=armor,
        ar1=T("防具には必要レベルがあり、足りないと着られません。", "Armor has a required level and cannot be worn below it."),
        ar2=T("武器は必要レベルが足りなくても持てますが、素手と同じ力しか出ません。", "Weapons can be held below their level but deal only bare-hand damage."),
        h_lv=T("必要レベル", "Required levels"), lv=T("推奨レベルから、コモン〜エピックは −4、ヒロイック以上は −2 です。", "Common to Epic is the recommended level -4; Heroic and above is -2."),
        levels=levels,
    )
    page("gear.html", "装備 — 天と淵のあいだ", "Gear — Between the Sky and the Abyss",
         "13 種の武器、階級、特性、共鳴の炉、装飾品、防具、必要レベル。", "Thirteen weapons, tiers, affixes, the Resonance Forge, accessories, armor and level requirements.", body)


# ------------------------------------------------------------------ 旅嚢
def pouch():
    def row(ids, ja, en, cap, extra_ja, extra_en):
        return ['<ul class="iconrow" style="margin:0">%s</ul>' % "".join("<li>%s</li>" % item_li_inline(i) for i in ids), T(ja, en), cap, T(extra_ja, extra_en)]

    rows = [
        row(["stone_whale_call", "tide_shell_call", "thunder_wing_call", "ember_call", "tide_watching_shell"], "使う道具", "Tools", "1", "呼び笛・呼び貝・潮見の貝・風見鶏。キーで使えます。", "Whistles, conches, the tide shell and the weathercock; usable by key."),
        row(["sky_key_fragment", "sky_key", "stone_whale_core", "tide_shell_core", "thunder_wing_core", "ember_core"], "鍵と心核", "Keys and cores", "4 / 1 / 99", "天の鍵の欠片・天の鍵・心核。なくすと困る品。", "Sky-key fragments, the sky key and titan cores: items you cannot afford to lose."),
        row(["resonance_crystal", "resonance_shard_stone_whale", "resonance_shard_tide_shell", "resonance_shard_thunder_wing", "resonance_shard_ember"], "共鳴", "Resonance", "999", "共鳴の結晶と欠片。炉の費用と通貨。", "Crystals and fragments: forge costs and currency."),
        row(["lore_book", "lore_page"], "書", "Books", "1 / 99", "伝承の書と破れた頁。", "The lore book and torn pages."),
        row(["stone_whale_shard", "tide_shell_shard", "thunder_wing_shard", "ember_shard"], "巨神の素材", "Titan materials", "999", "強化と鍛えに使う素材。", "Materials for enhancing and forging."),
        row(["bone_chip", "tide_pearl"], "小さな素材", "Small materials", "999", "敵が落とす細かい素材。", "Small drops from enemies."),
    ]
    body = page_head("旅嚢と道具", "Travel Satchel and tools", "mod の集める品・鍵・笛・素材だけを入れる専用の持ち物と、控え帳、伝承の書。",
                     "A dedicated inventory for the mod's keys, whistles and materials, plus the record book and the lore book.") + toc(POUCH_TOC) + """
<section aria-labelledby="why">
<div class="wrap">
<h2 id="why">%(h_why)s</h2>
<p class="lead">%(why)s</p>
<div class="grid g2">
<div class="panel"><h3>%(a_h)s</h3><p>%(a)s</p></div>
<div class="panel"><h3>%(b_h)s</h3><p>%(b)s</p></div>
<div class="panel"><h3>%(c_h)s</h3><p>%(c)s</p></div>
<div class="panel"><h3>%(d_h)s</h3><p>%(d)s</p></div>
</div>
</div>
</section>

<section class="alt" aria-labelledby="what">
<div class="wrap">
<h2 id="what">%(h_what)s</h2>
%(t)s
<p>%(n)s</p>
</div>
</section>

<section aria-labelledby="use">
<div class="wrap">
<h2 id="use">%(h_use)s</h2>
<ul>
<li>%(u1)s</li><li>%(u2)s</li><li>%(u3)s</li><li>%(u4)s</li>
</ul>
</div>
</section>

<section class="alt" aria-labelledby="tools">
<div class="wrap">
<h2 id="tools">%(h_tools)s</h2>
<div class="grid g2">
<div class="panel"><h3>%(r_h)s</h3><p>%(r)s</p></div>
<div class="panel"><h3>%(l_h)s</h3><p>%(l)s</p></div>
<div class="panel"><h3>%(t_h)s</h3><p>%(tt)s</p></div>
<div class="panel"><h3>%(w_h)s</h3><p>%(w)s</p></div>
<div class="panel"><h3>%(n_h)s</h3><p>%(nn)s</p></div>
<div class="panel"><h3>%(f_h)s</h3><p>%(f)s</p></div>
</div>
</div>
</section>
""" % dict(
        h_why=T("旅嚢とは", "What is the satchel"),
        why=T("バニラの持ち物には、剣や食べ物や建材と、この mod の鍵・心核・欠片・鱗片・笛が混ざってかさばります。旅嚢は、この mod の「集める品・鍵・笛」だけを入れる専用の持ち物です。", "Vanilla inventories get crowded when swords, food and blocks mix with this mod's keys, cores, fragments, scales and whistles. The satchel is a dedicated inventory for the mod's collectibles, keys and whistles."),
        a_h=T("拾えば自動で入る", "Auto-collect"), a=T("旅嚢の品は拾うと勝手に袋へ入ります。持ち物の枠を取りません。", "Satchel items go straight into the bag on pickup and take no inventory slots."),
        b_h=T("倒れても失わない", "Death-proof"), b=T("袋の品は倒れても失わず、墓にも入りません。鍵の欠片や心核を探し回る苦労がありません。", "Satchel contents survive death and never go into a grave, so you never hunt for key fragments or cores."),
        c_h=T("品ごとの固定枠", "Fixed slots"), c=T("品の種類ごとに決まった枠があり、並びは変わりません。空の枠の輪郭が集める楽しみになります。", "Every item type has its own fixed slot and the layout never shifts; empty outlines invite you to collect."),
        d_h=T("扉も通す", "Opens doors"), d=T("鍵や欠片は、袋にあれば持っていなくても扉や仕掛けを通ります。", "Keys and fragments in the satchel open doors and mechanisms without being held."),
        h_what=T("入るもの", "What goes in"), t=table([T("品", "Items"), T("区分", "Category"), T("上限", "Cap"), T("備考", "Notes")], rows),
        n=T("武器・防具・盾・弓と矢、護符・装飾、食べ物、バニラの品、仕掛けの道具（手で運ぶこと自体が仕掛けのもの）は、バニラの持ち物のままです。", "Weapons, armor, shields, bows and arrows, charms and accessories, food, vanilla items and puzzle tools (those you must physically carry) stay in the normal inventory."),
        h_use=T("使い方", "Using it"),
        u1=T("<kbd>I</kbd> で旅嚢の画面を直接開きます。持ち物の画面（サバイバル）の右上の「持ち物」「装飾」「旅嚢」のつまみからも開けます。", "Press <kbd>I</kbd> to open the satchel directly, or use the Inventory / Accessories / Satchel tabs at the top right of the survival inventory."),
        u2=T("<kbd>Y</kbd> を長押しすると道具の輪が開きます。マウスの向きで選び、離すと使います。短く押すと、前回の道具をすぐ使います。", "Hold <kbd>Y</kbd> for the tool wheel: aim with the mouse and release to use. Tap it to use the last tool."),
        u3=T("作業台と持ち物の画面の右に「旅嚢から」の帯が出て、袋の素材で作れます（レシピブックの「置く」も袋から）。", "A \"From satchel\" strip appears beside the crafting table and inventory so you can craft with satchel materials; the recipe book's place action also draws from it."),
        u4=T("大事な品は倒れても残り、Q キーで投げ捨てられません（人に渡すときは箱を通します）。", "Key items survive death and cannot be thrown away with Q (hand them over through a chest)."),
        h_tools=T("そのほかの道具", "Other tools"),
        r_h=T("控え帳（B）", "Record book (B)"), r=T("巨神の進み、墓の場所、大事な品の在りか、備えの行、呼び戻しのボタン。<code>/skyrecord</code> でも開けます。", "Titan progress, grave locations, where your key items are, the readiness row and a Recall button. Also opens with <code>/skyrecord</code>."),
        l_h=T("伝承の書", "Lore book"), l=T("見たこと、読んだ物、聞いた話が少しずつ記されていきます。持って右クリックで開きます。なくしても戻ります。", "Records what you see, read and hear. Right-click it to open. If you lose it, it returns."),
        t_h=T("知らせの記録（N）", "Notice log (N)"), tt=T("流れた語り・獲得・場所の名前を新しい順に読み返します。", "Re-read narration, rewards and place names, newest first."),
        w_h=T("呼び笛と呼び貝", "Whistles and conches"), w=T("旅のために巨神の「子」を呼ぶ品です。鯨は笛、潮殻は貝。子にはパーティ全員（最大 6 人）が乗れ、海図で行き先を選びます。出発は全員が乗れば約 3 秒後で、乗っていない近くの仲間がいれば最大 30 秒待ちます。成体の巨神はもう動きません（<a href=\"titans.html#travel\">くわしくは巨神のページ</a>）。", "Items that summon a young carrier of the titan for travel: a whistle for the whale, a conch for the shell. The whole party (up to six) rides and picks a destination from a chart. Departure comes about 3 seconds after everyone is aboard, waiting up to 30 seconds for nearby party members who have not boarded. Adult titans no longer move (<a href=\"titans.html#travel\">details on the titans page</a>)."),
        n_h=T("野営の道具", "Camp tools"), nn=T("背中の中でも、作業台・かまど・金床・チェストなどを、仕掛けや戦いの場所から離れた地面に一人 8 個まで置けます。背中を出ると中身ごと手もとへ戻ります。", "On a titan's back you may place crafting tables, furnaces, anvils and chests (up to 8 per player) away from puzzle areas. They return to you with contents when you leave."),
        f_h=T("心臓の遺物の選択", "Relic choice"), f=T("心臓を初めて止めたあとに選べる遺物の画面を、旅嚢や控え帳からいつでも開き直せます。", "Reopen the relic choice screen from the satchel or the record book at any time."),
    )
    page("pouch.html", "旅嚢と道具 — 天と淵のあいだ", "Travel Satchel and tools — Between the Sky and the Abyss",
         "旅嚢（mod の品の専用の持ち物）、控え帳、伝承の書、呼び笛。", "The Travel Satchel, record book, lore book and whistles.", body)


# ------------------------------------------------------------------ パーティ
def party():
    rules = table([T("ゲームルール", "Game rule"), T("内容", "Effect")], [
        ["<code>skyabyssPartyShare</code>", T("その場にいなかった仲間への経験値の分け前（50〜80%）", "XP share for party members who were not present (50-80%)")],
        ["<code>skyabyssHeartReentryLimit</code>", T("戦いで倒れた人が、20 秒のあとに戻れる回数（既定 3。0 で数えない）", "How often a fallen player may re-enter after 20 s (default 3; 0 = unlimited)")],
    ])
    body = page_head("パーティとマルチ", "Party and multiplayer", "6 人まで。背中の進みはパーティごと。人数に合わせて敵が増え、報酬は減りません。",
                     "Up to six players. Back progress is per party. Enemies scale with the party and rewards never shrink.") + toc(PARTY_TOC) + """
<section aria-labelledby="party">
<div class="wrap">
<h2 id="party">%(h_party)s</h2>
<p class="lead">%(party)s</p>
<div class="grid g2">
<div class="panel"><h3>%(a_h)s</h3><p>%(a)s</p></div>
<div class="panel"><h3>%(b_h)s</h3><p>%(b)s</p></div>
<div class="panel"><h3>%(c_h)s</h3><p>%(c)s</p></div>
<div class="panel"><h3>%(d_h)s</h3><p>%(d)s</p></div>
</div>
</div>
</section>

<section class="alt" aria-labelledby="shared">
<div class="wrap">
<h2 id="shared">%(h_shared)s</h2>
<p class="lead">%(shared)s</p>
<ul>
<li>%(s1)s</li><li>%(s2)s</li><li>%(s3)s</li><li>%(s4)s</li><li>%(s5)s</li>
</ul>
</div>
</section>

<section aria-labelledby="scale">
<div class="wrap">
<h2 id="scale">%(h_scale)s</h2>
<ul>
<li>%(c1)s</li><li>%(c2)s</li><li>%(c3)s</li><li>%(c4)s</li>
</ul>
</div>
</section>

<section class="alt" aria-labelledby="down">
<div class="wrap">
<h2 id="down">%(h_down)s</h2>
<ul><li>%(w1)s</li><li>%(w2)s</li><li>%(w3)s</li></ul>
</div>
</section>

<section aria-labelledby="mark">
<div class="wrap">
<h2 id="mark">%(h_mark)s</h2>
<p>%(mark)s</p>
<div class="mock" role="img" aria-label="%(mock_alt)s"><div class="marks"><span class="marker">%(m1)s</span><span class="marker">%(m2)s</span><span class="marker">%(m3)s</span></div></div>
<p class="muted" style="margin-top:.6em"><small>%(mnote)s</small></p>
</div>
</section>

<section class="alt" aria-labelledby="roles">
<div class="wrap">
<h2 id="roles">%(h_roles)s</h2>
<p>%(roles)s</p>
%(rules)s
</div>
</section>
""" % dict(
        h_party=T("パーティを組む", "Forming a party"),
        party=T("<kbd>O</kbd> のパーティの画面、または <code>/party invite &lt;名前&gt;</code> で組みます。人数は 6 人までです。", "Open the party screen with <kbd>O</kbd> or use <code>/party invite &lt;name&gt;</code>. Parties hold up to six players."),
        a_h=T("みんなで同じ jar", "Same jar for everyone"), a=T("サーバーでも、遊ぶ全員が同じ jar（と GeckoLib）を入れます。サーバーなら、サーバーの mods フォルダにも入れます。", "Everyone playing, and the server itself, needs the same jar and GeckoLib in the mods folder."),
        b_h=T("乗る資格は個人", "Boarding is personal"), b=T("乗れるのは、自分で前の心臓を止めた人です。先に進んだ仲間に連れていってもらうことはできません。追いつくには、一緒に前の心臓を止めに行きます。", "You can board only if you personally stopped the previous heart. A friend cannot carry you; to catch up, go stop that heart together."),
        c_h=T("経験値", "Experience"), c=T("道中の敵の経験値は近くにいる全員で分けます。ボスは参加者それぞれに満額です。その場にいなかった仲間にも、いくらかの分け前が入ります。", "XP from regular enemies is split among nearby players. Bosses give full XP to each participant, and absent party members receive a share."),
        d_h=T("装備と報酬", "Gear and rewards"), d=T("専用装備は一人ずつの抽選です。心臓の遺物の選択も、仲間それぞれの画面で自分で選びます。", "Dedicated gear is rolled per player, and each party member picks their own heart relic on their own screen."),
        h_shared=T("背中の進みはパーティごと", "Back progress is per party"),
        shared=T("開けた扉、倒した心臓、部屋のリセットは、パーティ（入っていない人は一人のパーティ）ごとに持ちます。", "Opened doors, defeated hearts and room resets are kept per party (a solo player counts as a party of one)."),
        s1=T("同じ背中に別のパーティがいても、扉や敵は混ざりません。ほかのパーティの敵は見えず、狙われもしません。", "Even with another party on the same back, doors and enemies never mix; you cannot see or be targeted by their enemies."),
        s2=T("水や足場が動く部屋は、一度に一つのパーティだけが入れます。使っているときは「別のパーティーがこの部屋に挑んでいる」と出ます。", "Rooms with moving water or platforms admit one party at a time; a notice says another party is in that room."),
        s3=T("途中でパーティを組む・抜けると、背中の進みはパーティの記録に切り替わります。部屋の中にいるときは鼓動の間へ戻されます。", "Joining or leaving a party mid-run switches to that party's record; if you are inside a room you are returned to the hub."),
        s4=T("全員が同じ順番で進みます。先に進んだ人がいても、遅れた人は自分の順番で進めます。", "Everyone progresses in the same order; stragglers keep their own pace."),
        s5=T("部屋を越えると、次の部屋への道が開きます。越えた部屋は、そのパーティの記録に残ります。", "Clearing a room opens the way to the next. Cleared rooms stay recorded for that party."),
        h_scale=T("人数による強さ", "Scaling by entrants"),
        c1=T("背の部屋は、6 人までを想定した敵の数です。人数に合わせて敵が増える部屋があります。", "Back rooms are tuned for up to six; many rooms add enemies as the party grows."),
        c2=T("心臓の体力は参加者の数に合わせて実質的に増え、一人あたりの戦いの長さが人数によらず 4 分ほどになるように調整されています（岩鯨）。", "Heart durability scales with participants so that the fight lasts about four minutes per player regardless of party size (Stone Whale)."),
        c3=T("戦いの強さは、部屋の中にいる人数に合わせて決まります。報酬は人数が増えても減りません。", "Combat strength follows the number of people inside the room. Rewards do not shrink as the party grows."),
        c4=T("一人でも遊べます。", "Solo play is fully supported."),
        h_down=T("倒れたとき（蘇生と復帰）", "Falling (revive and re-entry)"),
        w1=T("パーティの仲間が近くにいると、倒れてもすぐには死にません。約 15 秒の「倒れた」状態になり、その場で這うだけで何もできず、敵に狙われず、傷も受けません。", "With a living party member in the same dimension, you do not die at once. You go down for about 15 seconds: you can only crawl, enemies ignore you and you take no damage."),
        w2=T("仲間が 3.5 ブロック以内で「使う」を 3 秒押し続けると起こせます。体力 3 割で戻り、短い守りが付きます。傷を受ける・離れると中断します。起こされた人は復帰の回数に数えません。ゲームルール <code>skyabyssRevive</code>（既定 true）で切れます。", "A teammate within 3.5 blocks who holds Use for 3 seconds revives you at 30% health with brief protection; taking damage or moving away interrupts it. Being revived does not use a re-entry. The game rule <code>skyabyssRevive</code> (default true) turns it off."),
        w3=T("誰も起こせないまま時間が来たとき（一人遊びや仲間が全員倒れたときを含む）は、そのまま倒れます。心臓の戦いなら前室で目を覚まし、20 秒後に扉から戻れます（1 戦に 3 回まで。戻ると 3 秒の守り）。", "If nobody can revive you in time (including solo play, or when every party member is down) you fall for real. In a heart fight you wake in the antechamber and may re-enter after 20 seconds, up to 3 times per fight, with 3 seconds of protection."),
        h_mark=T("仲間の目印", "Party markers"),
        mark=T("仲間の位置は、壁の向こうでも頭の上の札（名前と距離）と、画面の端の矢印で分かります。別の背中にいる仲間は右の一覧に「別の場所」と出ます。設定 <code>partyMarkers</code> で消せます。旅人の技「合図」で、見ている先に全員に見える光の標も立てられます。", "Teammates show through walls as overhead tags (name and distance) and edge-of-screen arrows. A teammate on another back appears in the right-hand list as \"elsewhere\". Turn it off with the <code>partyMarkers</code> setting. The Wayfarer skill Signal plants a beacon everyone can see."),
        mock_alt="仲間の目印の見本", m1=T("Alex　24 m", "Alex  24 m"), m2=T("Steve　58 m ▶", "Steve  58 m ▶"), m3=T("別の場所　Sam", "Elsewhere: Sam"),
        mnote=T("見本です。名前は例です。", "A sample. The names are made up."),
        h_roles=T("役目と管理者向けの設定", "Roles and admin settings"),
        roles=T("岩守りは壁、潮歌いは支え、灰の鍛え人と骨の射手は攻め、風読みは機動と崩し。技の木の組み合わせで、パーティでの役目が分かれます。", "Rockwarden holds the line, Tidesinger supports, Ashsmith and Boneshooter attack, Windreader skirmishes. Tree choices give each party member a role."),
        rules=rules,
    )
    page("party.html", "パーティとマルチ — 天と淵のあいだ", "Party and multiplayer — Between the Sky and the Abyss",
         "6 人までのパーティ、背中の進みはパーティごと、人数による強さ、仲間の目印。", "Parties of up to six, per-party back progress, scaling and party markers.", body)
