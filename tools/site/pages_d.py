"""転生・自由度・深いスキル・装備の自由度・深淵・背の大きさ (日英の対)。"""
from sitelib import *


def _item(iid):
    ja, en = item_name(iid)
    return icon("img/item/%s.png" % iid, ja, en) + T(ja, en)


def _sec(id_, h, inner, alt=False):
    return '\n<section%s aria-labelledby="%s" id="%s-s">\n<div class="wrap">\n<h2 id="%s">%s</h2>\n%s\n</div>\n</section>\n' % (
        ' class="alt"' if alt else "", id_, id_, id_, h, inner)


def _grid(panels):
    return '<div class="grid g2">%s</div>' % "".join('<div class="panel"><h3>%s</h3><p>%s</p></div>' % (h, p) for h, p in panels)


def _note(s):
    return '<p class="muted"><small>%s</small></p>' % s


# ------------------------------------------------------------ スキルのページへ
def skills_extra():
    free = _sec("freedom", T("職の中の作り込み", "Building within a job"),
        '<p class="lead">%s</p>' % T("職の木は型ごとの道に分かれ、どの道を伸ばすかで同じ職でも遊び方が変わります。点の総数（Lv 50 で 45、職ごと）と「回復・無敵は作らない」は変わりません。",
                                    "Each job tree branches into paths; which path you grow changes how the same job plays. The total (45 points at Lv 50, per job) is unchanged, and there is still no healing or invulnerability skill.")
        + table([T("仕掛け", "System"), T("決まり", "Rule")], [
            [T("持ち替え", "Weapon swap"), T("職は持った武器で決まります。二つの職の武器を持ち歩けば、戦いの中でも持ち替えでその場で職が替わります（待ちとマナはそのまま）。", "The weapon in your hand sets the job. Carry two jobs' weapons and swap mid-fight; cooldowns and mana carry over.")],
            [T("要（型の奥）", "Keystones"), T("職の木の型ごとに一つ、強い代わりに何かを失う常時の技。戦いの外で無料で外せます。", "One per path in the job tree: a lot of power for a trade-off. Can be removed for free outside combat.")],
            [T("熟練", "Mastery"), T("技を使う・当てる・予告をよけて当てるごとに育ち、1〜5 段。3 段で 4 つ目の「熟練の刻印」、5 段で足元に光の輪。転生しても残ります。", "Each skill grows by use, hits and dodge-hits, from 1 to 5. Mastery 3 opens a fourth \"mastery rune\"; mastery 5 adds a ring of light at your feet. Survives rebirth.")],
            [T("試着", "Try-on"), T("画面だけの写しに技を仮に振り、効果・マナ・待ちを確かめてから確定できます。", "Preview points on a screen-only copy, check effect, mana and cooldown, then confirm.")],
            [T("型の保存", "Loadouts"), T("職ごとに枠と刻印の組を 2 つ保存し、戦いの外でいつでも切り替えられます。", "Save two sets of slots and runes per job and switch outside combat.")],
        ])
        + _note(T("技の力の合計は +100% で頭打ちです。", "Total skill power is capped at +100%.")))
    deep = _sec("deep", T("深いスキル", "Deep skills"),
        '<p class="lead">%s</p>' % T("極めた技と、転生した人のための深い仕掛けです。K 画面の「変異・紋」ページと、木のつまみの淵・前世の技から使います。どの職でも使えます。",
                                    "Systems for mastered skills and for reborn players, found on the Mutations and Glyphs page of the skill screen and on the tree selector. They work for every job.")
        + table([T("仕掛け", "System"), T("決まり", "Rule")], [
            [T("変異", "Mutations"), T("8 つの技に 1 つずつ。その技を極め、巨神の素材（核 1 と鱗・欠片 3）を持っていると選べます。技が形を変えます（例: 巨神斬り→断崖、風裂き→飛ぶ刃）。1 技に 1 つで、外せません。", "One per skill for eight skills. Master the skill and hold titan materials (1 core and 3 scales or fragments) to reshape it, for example Titan Cleave into Cliffbreak or Wind Rend into Flying Blade. One per skill, permanent.")],
            [T("紋の盤", "Glyph board"), T("部屋を越えると紋（岩・風・潮・灰・骨、段 1〜3）が手に入ります。盤の穴は最初 3 つ、転生ごとに 1 つ開いて最大 6。穴ごとに中心の技を選ぶと、その技と隣の技が強まります。", "Clearing rooms yields glyphs of five kinds and tiers 1 to 3. The board starts with three holes and gains one per rebirth, up to six. Pick a centre skill per hole to strengthen it and its neighbours.")],
            [T("前世の技", "Past-life skills"), T("転生 1 回につき別枠の 2 点（最大 12）で取る 6 つ（遺響の一撃・旧き見張り・墓鐘・記憶の刃・最期の光・魂の糸）。", "Six skills bought with a separate pool of 2 points per rebirth (up to 12): Echo Strike, Old Watch, Grave Bell, Memory Blade, Last Light and Soul Thread.")],
            [T("淵の木", "The Abyss tree"), T("6 本目の木。転生した人にだけ見えます。引き・鏡・残響・貢ぎ・顎・誓いの 6 つ。他の木を混ぜるほど効く作りです。", "A sixth tree visible only to reborn players: Pull, Mirror, Echo, Tribute, Maw and Oath. It rewards mixing other trees.")],
        ])
        + _note(T("最強の組み立てでも、心臓や番兵長を倒す速さには下限があり、戦いが崩れないように調整してあります。", "Even the strongest build keeps a floor on how fast the hearts and the Captain can be beaten, so fights never collapse.")), alt=True)
    arts = _sec("abyss-arts", T("深淵術", "Abyss arts"),
        '<p class="lead">%s</p>' % T("深淵の層の溜まりで覚える三つの術です。淵の木と同じ「深淵術師」の木に並び、マナを使い、待ちがあります。段は 3 つまで。",
                                    "Three arts learned at the pools of the abyss layers. They sit in the Abyss Caster tree beside the Abyss tree, use mana and have cooldowns. Each has three ranks.")
        + table([T("術", "Art"), T("鍵（既定）", "Key (default)"), T("覚える所", "Learned at"), T("働き", "Effect")], [
            [T("光の術", "Light"), "<kbd>=</kbd>", T("骨の層の溜まり", "Bone layer pool"), T("灯りを一つ置く。中の敵の輪郭が見え、使った人は暗がりでも見やすい。段で範囲と続く時間が伸びる", "Places a light. Foes inside show their outline, and the caster sees better in the dark. Higher ranks extend range and duration")],
            [T("重さの術", "Weight"), "<kbd>-</kbd>", T("塩の層の溜まり", "Salt layer pool"), T("上るときの重さを緩める。使った人だけ。段で緩みが大きく、続く時間が長くなる", "Eases the weight of the climb, for the caster only. Higher ranks ease more and last longer")],
            [T("鼓動の術", "Heartbeat"), "<kbd>\\</kbd>", T("雷の層の溜まり", "Storm layer pool"), T("近くの敵の鼓動を聞き、その輪郭を一時見せる。近くの人（自分を含む）を癒す", "Hears nearby foes and reveals their outline briefly. Heals allies nearby, including yourself")],
        ])
        + _note(T("段を上げるには、灰の溜まりで深淵術師の木を開き、そのそばで術の鍵をしゃがみながら押します。鍵は操作の設定で変えられます。",
                  "To raise a rank, open the Abyss Caster tree at the ash pool, then crouch and press the art's key beside it. Keys can be rebound in the controls settings.")))
    return free + deep + arts


# ------------------------------------------------------------ 装備のページへ
def gear_extra():
    shards = "".join(icon("img/item/socket_shard_%s.png" % k, j, e) for k, j, e in (
        ("stone", "岩の欠片", "Stone shard"), ("wind", "風の欠片", "Wind shard"), ("tide", "潮の欠片", "Tide shard"),
        ("ash", "灰の欠片", "Ash shard"), ("bone", "骨の欠片", "Bone shard")))
    free = _sec("gearfree", T("装備の自由度", "Gear freedom"),
        '<p class="lead">%s</p>' % T("特性のほかに、穴・欠片・ルーン・アスペクト・世代が装備を育てます。特性の付くレア以上の武器と防具が対象です。",
                                    "Beyond affixes, sockets, shards, runes, aspects and generations let gear grow. They apply to Rare and better weapons and armor.")
        + table([T("仕組み", "System"), T("決まり", "Rule")], [
            [T("穴", "Sockets"), T("レア以上は 0〜2 個（上の階級ほど多く出ます）。共鳴の炉の打ち直しで必ず足せます（最大 2）。", "Rare and better gear has 0 to 2 sockets, more often at higher tiers. The forge can always add one (up to 2).")],
            [T("欠片", "Shards") + " " + shards,
             T("岩・風・潮・灰・骨の 5 種。穴に差すとそのタグが 1 つ数えられ、装備に小さな数字が付きます。心臓や部屋の報酬で出ます。", "Five kinds. Socketed, each counts as one tag for skills and adds a small stat. Dropped by hearts and rooms.")],
            [T("技のルーン", "Skill runes") + " " + icon("img/item/skill_rune.png", "技のルーン", "Skill rune"), T("使う技 1 つに力 +10〜30% か待ち −10〜20%。穴に差します。1 つの技に効くのは 1 枚です。", "+10 to 30% power or -10 to 20% cooldown for one active skill, socketed. Only one rune applies per skill.")],
            [T("アスペクト", "Aspects") + " " + icon("img/item/aspect_core.png", "アスペクトの芯", "Aspect core"), T("書き換えの武器の力を炉で抜き取って「芯」にし、レアの武器・防具に刻めます。その装備を持つ（武器）か着る（防具）あいだ、その書き換えが効きます。", "At the forge extract a rewrite weapon's effect into a core and engrave it on a Rare weapon or armor. It works while you hold (weapon) or wear (armor) that piece.")],
            [T("世代", "Generations"), T("転生するたびに、持ち物の特性付き装備の世代が 1 上がります（最大 12）。世代 3 ごとに形見の小さな特性が 1 つ付きます。", "Each rebirth raises the generation of your affixed gear by one (max 12). Every third generation grants a small heirloom affix.")],
        ])
        + _note(T("共鳴の炉の画面に「刻み」のつまみ（抽出・刻印・穴）が加わりました。", "The forge screen has a new Engrave tab (extract, engrave, sockets).")))
    ab = _sec("abyss-artifacts", T("深淵の専用アーティファクト", "Abyss artifacts"),
        '<p class="lead">%s</p>' % T("深淵の難度（<a href=\"rebirth.html#abyss\">転生のページ</a>）で心臓を止めたときだけ出る、巨神ごとの専用アーティファクトです。まだ書き換えのない 4 つの技を別の形にします。",
                                    "Dropped only when a heart is stopped on Abyss difficulty (see <a href=\"rebirth.html#abyss\">Rebirth</a>), one per titan. Each rewrites a skill that had no rewrite yet.")
        + table([T("品", "Item"), T("巨神", "Titan"), T("書き換わる技", "Skill rewritten"), T("書き換えた形", "New form")], [
            [_item("abyss_greatsword"), T("岩鯨", "Stone Whale"), T("巨神斬り", "Titan Cleave"), T("奈落断ち: 振った扇が 1.2 秒後にもう一度裂ける", "Abyssal Cleave: the sweep splits again 1.2 s later")],
            [_item("abyss_bell_staff"), T("潮殻", "Tide Shell"), T("守りの唄", "Ward Song"), T("沈む唄: 残った守りが体力に沈み、仲間へ届く", "Sinking Song: leftover wards sink into health for allies")],
            [_item("abyss_storm_bow"), T("雷翼", "Thunder Wing"), T("雲穿ち", "Cloud Piercer"), T("天落とし: 矢の止まった所へ雷の柱", "Sky Drop: a lightning pillar where the arrow lands")],
            [_item("abyss_ash_scythe"), T("余燼", "Ember"), T("灰散らし", "Ash Scatter"), T("這う灰: 灰かぶりの敵のまわりへ灰が移る", "Creeping Ash: ash spreads among nearby enemies")],
        ])
        + '<p>%s</p>' % T("心臓を倒した人それぞれに、共鳴の欠片 2 つは必ず、専用アーティファクトは 25%（4 回続けて出なければ次で保証）。持っていれば欠片 10 に替わります。強化・特性・必要 Lv は他のアーティファクトと同じです。", "Every player who clears the heart gets two resonance fragments for sure and a 25% chance at an artifact (guaranteed after four misses). If you already own it you get ten fragments instead. Enhancement, affixes and level requirements match other artifacts."), alt=True)
    return free + ab


# ------------------------------------------------------------ 巨神のページへ
def titans_extra():
    return _sec("bigger", T("大きくなった背", "Bigger backs"),
        '<p class="lead">%s</p>' % T("6 人のパーティが動けるように、四つの背を作り直しました。体は水平に ×1.6、垂直に ×1.3 前後で、部屋の面積は 2.6 倍以上、広間は約 3.5 倍です。",
                                    "The four backs were rebuilt so a party of six can move. Bodies are 1.6x wider and about 1.3x taller, rooms are at least 2.6x the area and halls about 3.5x.")
        + _grid([
            (T("通路と広間", "Corridors and halls"), T("通路は幅 7・高さ 6 が標準で、6 人が並んで通れます。心臓の間だけは戦いの手ごたえを保つため、広げすぎていません。", "Corridors are 7 wide and 6 high, so six can walk abreast. Only the heart chamber stays modest to preserve the fight's feel.")),
            (T("2 本の道", "Two lanes"), T("部屋は 2〜3 本の道と役割のある仕掛けを持ち、一人でも解けます（重りや留めが人の代わりをします）。", "Rooms offer two to three lanes and role-based gimmicks, and every one is solvable solo (weights and latches stand in for people).")),
            (T("任意の翼", "Optional wings"), T("必須の道のほかに、寄り道の翼・塔・庭があり、宝や読み物が置かれています。", "Beyond the main path there are optional wings, towers and gardens with treasure and lore.")),
            (T("歩ける広さ", "Walkable space"), T("雷翼の歩ける床は約 29,300 m²。それでも歩きは 1 分未満で、時間は部屋と戦いで使います。", "Thunder Wing has about 29,300 m² of walkable floor. Walking still takes under a minute; time goes to rooms and fights.")),
        ]), alt=True)


# ------------------------------------------------------------ 五章・深淵 (巨神のページへ)
def titans_abyss():
    pic = pimg("img/arch_abyss.png", "深淵の縦穴のドット絵", "Pixel art of the abyss shaft", 170, 168)
    return """
<section class="titan-sec" id="abyss" aria-labelledby="h-abyss">
<div class="wrap">
<div class="titan-hd"><div class="pic">%(pic)s</div>
<div><h2 id="h-abyss" class="t-abyss">%(ch)s</h2>
<p><span class="tag t-abyss">%(sub)s</span><span class="tag t-abyss">%(where)s</span></p>
<p>%(lead)s</p></div></div>
%(grid)s
<p><strong>%(after_h)s</strong>　%(after)s</p>
<p class="muted"><small>%(note)s</small></p>
</div>
</section>%(sep)s""" % dict(
        pic=pic,
        ch=T("五章　深淵", "Chapter 5: The Abyss"),
        sub=T("地の底へ降りる", "Descend into the deep"),
        where=T("地の底（都の地下から）", "Deep below (from under the capital)"),
        lead=T("四体の心臓を返したあとも、空は静かになりません。都の地下の戸の向こうに、底の見えない大きな縦穴が口を開けています。巨神の背とは別の、深淵という世界です。",
               "Even after the four hearts the sky does not settle. Past the door under the capital yawns a shaft with no visible bottom: the abyss, a world apart from any titan's back."),
        grid=_grid([
            (T("縦穴を下りる", "Down the shaft"), T("一続きの旅として下ります。骨・雷の層は道を探して進み、塩・灰の層は生き残りを守って降ります。層ごとに違う遊びがあります。層のあいだの灯りで、再開できます。",
                                             "One continuous descent through four layers (bone and storm are explored, salt and ash are escorted), each with its own play. Lantern rests between the layers let you resume.")),
            (T("深淵の術", "Abyss arts"), T("層ごとの溜まりで、光・重さ・鼓動の三つの術を覚えます。覚えた術は、次の層の道を開きます。深淵術師の木で育てます。",
                                         "Learn three arts at the pools of each layer: light, weight and heartbeat. Each opens the way ahead. Grow them in the Abyss Caster tree.")),
            (T("重さと闇", "Weight and dark"), T("上るほど体が重くなり、光の届かない所に長くいると、淵の声がたまります。深く潜るほど、敵は強くなり、深度の数字が上がります。",
                                              "The climb grows heavier, and long stays in the dark gather the voice of the deep. The deeper you go, the stronger the foes and the higher the depth number.")),
            (T("底の番人", "The warden below"), T("底には、姿を見せない番人がいます。見えるのは、灯りの中だけです。",
                                              "At the bottom waits a warden that does not show itself. It is seen only in lantern light.")),
        ]),
        after_h=T("クリアのあと", "After the clear"),
        after=T("番人の悪夢（段 1〜10）、前の自分たちの遺品 12 種、そして果てのない深みが残っています。",
                "The warden's nightmare (tiers 1 to 10), twelve past-self relics and endless depths remain."),
        note=T("物語の答えと、番人の戦いの細かい決まりは書いていません。難度は<a href=\"rebirth.html#abyss\">転生のページ</a>、深淵術は<a href=\"skills.html#abyss-arts\">スキルのページ</a>へ。",
               "The answers to the story and the warden's fight details are not written here. For Abyss difficulty see <a href=\"rebirth.html#abyss\">Rebirth</a>; for the abyss arts see <a href=\"skills.html#abyss-arts\">Skills</a>."),
        sep=divider(),
    )


# ------------------------------------------------------------ 転生のページ
def rebirth():
    crystal = icon("img/item/resonance_crystal.png", "共鳴の結晶", "Resonance crystal")
    body = page_head("転生と深淵", "Rebirth and the Abyss", "Lv 50 に着いたら、もう一度 Lv 1 から。装備と巨神の進みは残り、ビルドだけを作り直します。",
                     "Reach Lv 50 and start again from Lv 1. Gear and titan progress stay; only your build is rebuilt.")
    body += toc([("rebirth", "転生の流れ", "How rebirth works"), ("reborn-extra", "転生のあとに増えるもの", "What opens after rebirth"), ("abyss", "深淵の難度", "Abyss difficulty"), ("show", "色・記号・称号・順位", "Colors, symbols, titles, boards"), ("rb-note", "考え方", "Design notes")])
    body += _sec("rebirth", T("転生の流れ", "How rebirth works"),
        '<p class="lead">%s</p>' % T("表示は「Lv 50+n」。レベルは 50 を超えず、n は転生した回数です。上限は 20 回です。", "Shown as \"Lv 50+n\". The level never exceeds 50; n counts your rebirths, up to 20.")
        + '<ul><li>%s</li><li>%s</li><li>%s</li><li>%s</li></ul>' % (
            T("条件: Lv 50、戦いの外、背中の鼓動の間の台座のそば、%s共鳴の結晶 5。倒れたときの罰や装備の損失はありません。" % crystal, "Requirements: Lv 50, outside combat, near the pedestal in a back's heartbeat chamber, and 5 resonance crystals %s. No death penalty or gear loss." % crystal),
            T("戻るもの: レベル・技の点・ステータスの点・覚えた技と刻印（どの職も）。", "Resets: level, skill and stat points, learned skills and runes (for every job)."),
            T("残るもの: 装備・旅嚢・結晶・巨神の進み（パーティの背）・称号・熟練・型の保存・魂盤・恩恵。", "Kept: gear, satchel, crystals, titan progress (the party's back), titles, mastery, saved loadouts, the soul board and boons."),
            T("再レベリングは速くなります（必要経験値が転生ごとに −6%、下限 −40%）。", "Re-leveling gets faster: -6% experience needed per rebirth, down to -40%."))
        + _grid([
            (T("魂盤", "Soul board"), T("6 つの腕（岩・風・潮・灰・骨・旅）に、転生ごとに節を 1 つ足します。足す腕は自分で選びます。10 番目と 20 番目は要の節（値 3 倍）。", "Six arms (Rock, Wind, Tide, Ash, Bone, Wayfarer). Each rebirth adds a node to the arm you choose; the 10th and 20th nodes are keystone nodes worth triple.")),
            (T("3 択の恩恵", "Three boons"), T("転生のたびに 10 種の恩恵から 3 枚が示され、1 つ選びます（同じものを重ねられます）。", "Each rebirth offers three of ten boons; pick one (duplicates stack).")),
            (T("祖の力", "Ancestral power"), T("5 回ごとに全ダメージ +1%、上限 +4%。力の伸びは小さく、10 番目の節から先は収穫逓減です。", "+1% all damage every five rebirths, capped at +4%. Growth is small and diminishes beyond the tenth node.")),
            (T("共闘のレベル合わせ", "Level matching"), T("転生した人が低レベルの仲間と遊ぶときは、与える傷だけが下がります。体力と報酬は変わらず、未転生の人は置き去りになりません。", "A reborn player partnered with a much lower level deals reduced damage only. Health and rewards are untouched, so newcomers are never left behind.")),
            (T("導きの欠片", "Guide shards"), T("未転生の仲間と同じ部屋を越えると転生者に欠片が付き、5 個で魂盤の節が 1 つ。仲間も経験値を得ます。", "Clearing rooms with an unreborn partner earns shards; five grant a soul-board node, and your partner gains experience.")),
            (T("ポーチの余白と絆", "Satchel room and bonds"), T("転生 2・6・12 回でポーチの枠が増え、3・8・15 回で若い運び手が速く、遠くから呼べるようになります。", "Rebirths 2, 6 and 12 add satchel slots; 3, 8 and 15 make young carriers faster and summonable from farther away.")),
        ]))
    body += _sec("reborn-extra", T("転生のあとに増えるもの", "What opens after rebirth"),
        _grid([
            (T("深いスキル", "Deep skills"), T("前世の技、淵の木、紋の盤の穴が増えます。<a href=\"skills.html#deep\">スキルのページ</a>へ。", "Past-life skills, the Abyss tree and more glyph holes. See <a href=\"skills.html#deep\">Skills</a>.")),
            (T("世代と形見", "Generations"), T("装備の世代が上がり、形見の特性が付きます。<a href=\"gear.html#gearfree\">装備のページ</a>へ。", "Your gear gains generations and heirloom affixes. See <a href=\"gear.html#gearfree\">Gear</a>.")),
        ]), alt=True)
    body += _sec("abyss", T("深淵の難度", "Abyss difficulty"),
        '<p class="lead">%s</p>' % T("転生 3 回以上の人が選べる、極の上の難度です。引くものではなく選ぶもので、入れたあとは未転生の仲間も同じ部屋を遊べます。", "Available from three rebirths, above Extreme. It is chosen, not drawn, and unreborn partners can clear the same rooms.")
        + '<ul><li>%s</li><li>%s</li><li>%s</li><li>%s</li></ul>' % (
            T("数字はすべてきびしい段になり、障害が二つ付き、任意の目標が必ず一つ付きます。拍の周期や猶予は短くなります。", "All numbers use the strictest tier, two hazards apply and one optional goal is always present. Beat periods and grace windows shrink."),
            T("予告の見た目は変えず、一人で解けることも、時間・状態で閉じないことも変わりません。", "Telegraphs look the same, rooms stay solvable solo and are never locked by time or state."),
            T("報いは素材 +3、共鳴の欠片が 12%、称号「深淵を渡る者」。", "Rewards: +3 materials, 12% resonance fragment and the title \"Abyss Crosser\"."),
            T("深淵の心臓からは、巨神ごとの専用アーティファクトが出ます（4 つ。<a href=\"gear.html#abyss-artifacts\">装備のページ</a>）。", "Abyss hearts drop one dedicated artifact per titan (four in all; see <a href=\"gear.html#abyss-artifacts\">Gear</a>).")))
    body += _sec("show", T("色・記号・称号・順位", "Colors, symbols, titles, boards"),
        '<p class="lead">%s</p>' % T("転生の回数は、一目で伝わる形で見せられます。色が分からなくても縁の形で分かります。", "Your rebirth count shows at a glance; frame shapes differ so it is readable without color.")
        + table([T("回数", "Rebirths"), T("色", "Color"), T("縁", "Frame")], [
            ["0", T("白", "White"), T("なし", "None")],
            ["1–4", T("青緑", "Teal"), T("細い線", "Thin line")],
            ["5–14", T("金", "Gold"), T("二重線", "Double line")],
            ["15+", T("プリズム（縁が巡る。設定で止められます）", "Prism (cycling edge; can be frozen in settings)"), T("角の飾り", "Corner trim")],
        ])
        + '<p>%s</p>' % T("記号は ✦ → ✧（5）→ ✺（10）にまとまり、7 回なら ✧✦✦ です。名札・チャット・Tab・パーティ一覧・記録画面で共通です。", "The symbol bundles from ✦ to ✧ (5) to ✺ (10); seven rebirths show ✧✦✦. The same colors and symbols appear on nameplates, chat, the Tab list, the party list and the record screen.")
        + _grid([
            (T("称号 24 個", "24 titles"), T("回数・無傷・速さ・巨神・巡り・倒れ・最速。「称号・飾り」画面で選び、解けていない称号は条件が見えます。", "Earned by rebirth count, flawless runs, speed, titans, cycles, falls and records. Pick them in the Titles and Flair screen; locked ones show their condition.")),
            (T("飾り", "Cosmetics"), T("5 回で背中の紋章、10 回で短い布、20 回で光の羽。墓の飾りと、倒れ・復帰の光もあります。自分と他人の分を別々に切れます。", "A back emblem at 5, a short cloth at 10 and light wings at 20, plus grave flair and fall/rise lights. Toggle yours and others' separately.")),
            (T("順位の石と飾り板", "Rank stone and plaques"), T("鼓動の間の順位の石に 3 つの表（転生数・最速の人生・一人生の最少の倒れ）。巨神ごとの飾り板は世界最速を 10 分光らせます。載せない設定もあります。", "The rank stone in the hub shows three boards (rebirths, fastest life, fewest falls in a life). Per-titan plaques glow for ten minutes after a new record. You can opt out.")),
            (T("実績", "Achievements"), T("転生 1・5・10・20 回と、無傷の転生の実績があります。", "Achievements for 1, 5, 10 and 20 rebirths, and a flawless rebirth.")),
        ]), alt=True)
    body += _sec("rb-note", T("考え方", "Design notes"),
        '<ul><li>%s</li><li>%s</li><li>%s</li></ul>' % (
            T("力は小さく固定し、自由と見せ方で何度でも遊べるようにしています。", "Power gains are kept small; freedom and presentation carry replay value."),
            T("転生しても世界は巻き戻らず、巨神の進みはパーティの背に残ります。", "The world does not rewind; titan progress stays on the party's back."),
            T("序盤の敵は巨神の段で決まるので、転生者が序盤を踏み潰すことはありません。", "Early enemies scale with titan stage, so reborn players cannot flatten the opening.")))
    page("rebirth.html", "転生と深淵 — 天と淵のあいだ", "Rebirth and the Abyss — Between the Sky and the Abyss",
         "Lv 50 での転生、魂盤、恩恵、深淵の難度、色と記号、称号、順位。", "Rebirth at Lv 50, the soul board, boons, Abyss difficulty, colors and symbols, titles and boards.", body)
