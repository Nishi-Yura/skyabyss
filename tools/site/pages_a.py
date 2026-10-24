"""トップ・遊び方・巨神と背中。"""
from sitelib import *

TITANS = [
    # key, 名前 ja/en, 章, 副題 ja/en, 色の class, アーチ絵, 推奨 Lv, 場所
    dict(k="whale", ja="岩鯨", en="Stone Whale", ch_ja="一章", ch_en="Chapter 1", sub_ja="空を泳ぐ", sub_en="Swims the sky", cls="t-whale",
         lv=12, where_ja="空", where_en="The sky", elem_ja="空・岩", elem_en="Sky / stone"),
    dict(k="shell", ja="潮殻", en="Tide Shell", ch_ja="二章", ch_en="Chapter 2", sub_ja="海を歩く", sub_en="Walks the sea", cls="t-shell",
         lv=18, where_ja="海", where_en="The sea", elem_ja="海・潮", elem_en="Sea / tide"),
    dict(k="wing", ja="雷翼", en="Thunder Wing", ch_ja="三章", ch_en="Chapter 3", sub_ja="嵐をまとう", sub_en="Wears the storm", cls="t-wing",
         lv=24, where_ja="空の嵐と高い峰", where_en="Storm skies and high peaks", elem_ja="雷・風", elem_en="Lightning / wind"),
    dict(k="ember", ja="余燼", en="Ember", ch_ja="四章", ch_en="Chapter 4", sub_ja="地を泳ぐ", sub_en="Swims the earth", cls="t-ember",
         lv=30, where_ja="陸の地面の中", where_en="Under the land", elem_ja="火・熱", elem_en="Fire / heat"),
]


import pages_d


def index():
    arches = "".join(
        '<a class="panel titan-card" href="titans.html#%s"><div class="pic">%s</div><div class="body"><h3 class="%s">%s</h3>'
        '<p class="muted">%s</p></div></a>' % (
            t["k"], pimg("img/arch_%s.png" % t["k"], "%s のドット絵" % t["ja"], "Pixel art of %s" % t["en"], 170, 168),
            t["cls"], T(t["ch_ja"] + "　" + t["ja"], t["ch_en"] + ": " + t["en"]), T(t["sub_ja"], t["sub_en"]))
        for t in TITANS) + (
        '<a class="panel titan-card" href="titans.html#abyss"><div class="pic">%s</div><div class="body"><h3 class="t-abyss">%s</h3>'
        '<p class="muted">%s</p></div></a>' % (
            pimg("img/arch_abyss.png", "深淵の縦穴のドット絵", "Pixel art of the abyss shaft", 170, 168),
            T("五章　深淵", "Chapter 5: The Abyss"), T("地の底へ降りる", "Descend into the deep")))

    def feat(img, name_ja, name_en, ja, en, href):
        return ('<a class="panel feature" href="%s">%s<div><h3>%s</h3><p>%s</p></div></a>' % (
            href, '<img class="px" src="img/%s" width="48" height="48" alt="" loading="lazy">' % img, T(name_ja, name_en), T(ja, en)))

    feats = "".join([
        feat("item/stone_whale_call.png", "四体の巨神", "Four titans",
             "空・海・嵐・地の中を行く巨神に、一体ずつ挑みます。", "Take on titans that move through sky, sea, storm and earth, one by one.", "titans.html"),
        feat("skill/05.png", "36 の技と天職", "36 skills, callings",
             "5 つの木と旅人。天職を選び、刻印で技の形を変えます。", "Five trees plus the Wayfarer. Choose a calling and reshape skills with runes.", "skills.html"),
        feat("item/resonance_shard_ember.png", "転生と深淵", "Rebirth and the Abyss",
             "Lv 50 で転生し、魂盤と恩恵でビルドを作り直します。深淵の難度も。", "Rebirth at Lv 50, rebuild with the soul board and boons, then try Abyss difficulty.", "rebirth.html"),
        feat("item/waterway_fang.png", "13 種の武器", "13 weapons",
             "型・六段の階級・特性・書き換えの武器。周回で揃えます。", "Types, six tiers, affixes and rewrite weapons. Collect them over repeat runs.", "gear.html"),
        feat("item/resonance_crystal.png", "共鳴の炉", "Resonance Forge",
             "強化・分解・再鍛造・交換。周回の報いを形にします。", "Enhance, dismantle, reforge, trade. Turn runs into gear.", "gear.html#forge"),
        feat("item/sky_key_fragment.png", "旅嚢", "Travel Satchel",
             "鍵・笛・素材は専用の袋へ。倒れても失いません。", "Keys, whistles and materials go into a dedicated satchel and survive death.", "pouch.html"),
        feat("item/lore_book.png", "目的の帯と知らせ", "Objective strip",
             "チャットを流さず、画面の決まった場所に知らせます。", "No chat spam. Notices appear in fixed places on screen.", "play.html#notices"),
        feat("item/heartbeat_ward.png", "ゆるい死のペナルティ", "Soft death penalty",
             "地上で倒れると墓ができます。装備と大事な品は持ったまま復活。", "Die on the ground and leave a grave. You keep gear and key items.", "play.html#death"),
        feat("item/tide_watching_shell.png", "6 人までのパーティ", "Party of up to six",
             "背中の進みはパーティごと。人数に合わせて敵が増えます。", "Back progress is per party. Enemies scale with the party.", "party.html"),
        feat("item/stone_whale_call.png", "町の案内と厩舎", "Town guide and stable",
             "案内人が行き先まで灯りを引き、都の厩舎では乗り物を買えます。M の地図で場所を確かめられます。", "Guides light the way to any destination, and the capital's stable sells mounts. The M map shows where you are.", "play.html#town"),
    ])

    body = """
<section class="hero" aria-labelledby="hero-title">
<div class="wrap">
<h1 id="hero-title">天と淵のあいだ</h1>
<p class="sub">BETWEEN THE SKY AND THE ABYSS</p>
<p class="pitch">%(pitch)s</p>
<ul class="badges">
<li>Minecraft Java %(mc)s</li><li>NeoForge %(mc)s</li><li>%(ver)s</li><li>%(pl)s</li>
</ul>
<div class="ctas"><a class="btn" href="faq.html#install">%(cta1)s</a><a class="btn ghost" href="play.html">%(cta2)s</a><a class="btn ghost" href="titans.html">%(cta3)s</a></div>
</div>
</section>

%(first)s
<section aria-labelledby="diff">
<div class="wrap">
<h2 id="diff">%(h_diff)s</h2>
<div class="grid g2">
<div class="panel"><h3>%(d1h)s</h3><p>%(d1)s</p></div>
<div class="panel"><h3>%(d2h)s</h3><p>%(d2)s</p></div>
<div class="panel"><h3>%(d3h)s</h3><p>%(d3)s</p></div>
<div class="panel"><h3>%(d4h)s</h3><p>%(d4)s</p></div>
</div>
</div>
</section>
%(div)s
<section class="alt" aria-labelledby="stats">
<div class="wrap">
<h2 id="stats" class="sr-only" style="position:absolute;left:-999px">%(h_stats)s</h2>
<div class="grid g4">
<div class="panel stat"><b>4</b><span>%(s1)s</span></div>
<div class="panel stat"><b>28</b><span>%(s2)s</span></div>
<div class="panel stat"><b>36</b><span>%(s3)s</span></div>
<div class="panel stat"><b>6</b><span>%(s4)s</span></div>
</div>
</div>
</section>

<section aria-labelledby="feat">
<div class="wrap">
<h2 id="feat">%(h_feat)s</h2>
<div class="grid g2">%(feats)s</div>
</div>
</section>

<section class="alt" aria-labelledby="chap">
<div class="wrap">
<h2 id="chap">%(h_chap)s</h2>
<p class="lead">%(chap_lead)s</p>
<div class="grid g4">%(arches)s</div>
</div>
</section>

<section aria-labelledby="go">
<div class="wrap">
<h2 id="go">%(h_go)s</h2>
<div class="grid g2">
<div class="panel"><h3>%(g1h)s</h3><p>%(g1)s</p><p><a class="btn" href="faq.html#install">%(g1b)s</a></p></div>
<div class="panel"><h3>%(g2h)s</h3><p>%(g2)s</p><p><a class="btn" href="play.html">%(g2b)s</a></p></div>
</div>
</div>
</section>
""" % dict(
        pitch=T("空を泳ぐ巨神の背に乗り、世界の底の謎へ。建築も採掘も、いつものままに。",
                "Climb onto titans that swim through sky, sea, storm and earth. Build and mine as you always do."),
        mc=MC, ver=T("v%s ベータ版" % VERSION, "v%s beta" % VERSION), pl=T("1〜6 人", "1 to 6 players"),
        cta1=T("導入する", "Install"), cta2=T("遊び方を見る", "How to play"), cta3=T("巨神を知る", "Meet the titans"),
        h_diff=T("ほかと違うところ", "What makes it different"),
        d1h=T("巨神の背が、そのままダンジョン", "A titan's back is the dungeon"),
        d1=T("巨神の背は、巨神自身と同じ形・同じ色のブロックでできた島です。順路の部屋は、背の上の離れた場所に建っています。",
             "Each back is an island of blocks shaped and colored like the titan itself. Its rooms stand apart on the back, one route after another."),
        d2h=T("一体につき一章", "One chapter per titan"),
        d2=T("心臓を止めると、巨神は眠りにつきます。次の巨神が現れ、章が進みます。倒れても持ち物は残り、墓が目印になります。",
             "Stop the heart and the titan falls asleep; the next titan appears and the chapter advances. Death never costs your key items."),
        d3h=T("作り込めるビルド", "Builds with depth"),
        d3=T("5 つの技の木と旅人、36 の技、天職、刻印、要、型。武器との組み合わせで遊びが変わります。",
             "Five skill trees plus the Wayfarer, 36 skills, callings, runes, keystones and weapon types. Gear changes how you play."),
        d4h=T("静かな怖さと、仲間と", "Quiet dread, and company"),
        d4=T("序盤から、静かな不穏が少しずつ重なります（揺れや暗さは設定で弱められます）。6 人までのパーティで、背中の進みはパーティごとです。",
             "Subtle unease builds from the very first minutes (screen shake and darkness can be turned down). Play in a party of up to six; back progress is kept per party."),
        h_stats=T("数で見る", "By the numbers"),
        s1=T("巨神", "titans"), s2=T("順路の部屋（4 体分）", "route rooms (four titans)"), s3=T("技", "skills"), s4=T("人まで", "players max"),
        h_feat=T("できること", "What you can do"),
        feats=feats,
        h_chap=T("五つの章", "Five chapters"),
        chap_lead=T("巨神 1 体につき 1 章。前の巨神の心臓を自分で止めた人だけが、次の巨神に乗れます。四体の心臓を返したあとは、地の底の深淵へ降ります。",
                    "One chapter per titan. Only a player who stopped the previous heart can board the next titan. After the fourth heart, the way leads down into the abyss."),
        arches=arches,
        h_go=T("はじめる", "Get started"),
        g1h=T("導入", "Install"), g1=T("NeoForge %s と GeckoLib が要ります。jar を mods フォルダへ入れるだけです。" % MC,
                                       "Needs NeoForge %s and GeckoLib. Drop the jars into your mods folder." % MC),
        g1b=T("導入の手順", "Install steps"),
        g2h=T("最初の 1 時間", "Your first hour"), g2=T("野営地で目覚めてから、最初の巨神が空に現れるまでの流れです。", "From waking at a camp to the first titan appearing in the sky."),
        g2b=T("遊び方", "How to play"),
        div=divider(),
        first=_first_steps(),
    )
    page("index.html", "天と淵のあいだ — 巨神の背を行く Minecraft RPG mod", "Between the Sky and the Abyss — a titan-climbing RPG mod for Minecraft",
         "Minecraft Java 1.21.1（NeoForge）向けの RPG mod。空・海・嵐・地の中を行く巨神の背に乗り、背の部屋を越えて心臓に挑みます。",
         "An RPG mod for Minecraft Java 1.21.1 (NeoForge). Board titans of sky, sea, storm and earth and clear the rooms on their backs to reach their hearts.", body)


# ------------------------------------------------------------------ 遊び方
def play():
    keys = [
        ("J", "ステータス（レベル、力の振り分け、振り直し）", "Status (level, stat points, respec)"),
        ("K", "技の画面（技を覚える、四つの枠に置く、天職・刻印・型の保存）", "Skills screen (learn skills, slots, calling, runes, loadouts)"),
        ("U", "クエストログ（メインとサブ、追跡の切り替え）", "Quest log (main and side quests, tracking)"),
        ("B", "控え帳（巨神の進み、墓の場所、大事な品、呼び戻し）。<code>/skyrecord</code> でも開く", "Record book (titan progress, grave locations, key items, recall); also <code>/skyrecord</code>"),
        ("N", "知らせの記録（流れた語り・獲得・場所の名前を読み返す）", "Notice log (re-read narration, rewards, place names)"),
        ("M", "地図（ホイールで寄る・引く。自分の矢印と名前の札）", "Map (scroll to zoom; your arrow and place name tags)"),
        ("O", "パーティ（招待、一覧）", "Party (invite, roster)"),
        ("H", "護符を使う（装飾の護符の枠。待ち時間あり）", "Use your charm (charm slot; has a cooldown)"),
        ("R G V Z", "技の枠 1〜4 の技を使う", "Cast the skill in slot 1 to 4"),
        ("I", "旅嚢（mod の品の専用の持ち物）を開く", "Open the Travel Satchel (mod item storage)"),
        ("Y", "長押しで旅嚢の道具の輪。短押しで前回の道具", "Hold for the satchel tool wheel; tap for the last tool"),
    ]
    krows = [['<kbd>%s</kbd>' % k if " " not in k else " ".join('<kbd>%s</kbd>' % c for c in k.split()), T(j, e)] for k, j, e in keys]
    cmds = [
        ("/story", "自分の物語の進み、レベルの上限、巨神ごとの進み", "Your story progress, level cap and per-titan progress"),
        ("/growth, /skill", "レベル・力・技の一覧", "Overview of level, stats and skills"),
        ("/skyrecord", "控え帳を開く", "Open the record book"),
        ("/titanmemory", "巨神ごとの記録と、装備の図鑑", "Per-titan records and gear codex"),
        ("/party invite|accept|leave …", "パーティの招待・参加・脱退", "Party: invite, accept, leave"),
        ("/skyabyss play help", "試遊用の移動の一覧（OP）", "Tester travel commands (op)"),
        ("/skyabyss play raid 巨神 部屋", "背の部屋へ運ぶ（OP）", "Jump to a back room (op)"),
        ("/skyabyss play chapter 5", "深淵の章の始めへ（OP）", "Start of chapter 5, the abyss (op)"),
    ]
    crows = [["<code>%s</code>" % c, T(j, e)] for c, j, e in cmds]
    notices = [
        ("下の中央", "語りの字幕（見たものの言葉）と、手がかり（灰色の字）", "Bottom center", "Narration subtitles and hints (gray text)"),
        ("右上の札", "手に入れたもの（獲得）、先へ進んだこと（進展）", "Top-right cards", "Items gained and progress made"),
        ("画面の上のほう", "場所の名前の大きな題字、戦いの区切りの一行", "Upper screen", "Big place titles and battle milestone lines"),
        ("画面の中央", "レベルが上がったときの一行", "Center", "Level-up line"),
        ("足もと", "できなかった理由、行動のたびの手応え", "Action bar", "Why something failed, feedback per action"),
        ("左上", "目的の帯（いま向かうところ）", "Top left", "Objective strip (where to go now)"),
    ]
    nrows = [[T(a, c), T(b, d)] for a, b, c, d in notices]
    body = page_head("遊び方", "How to play", "最初の 1 時間と、操作、画面の見方、倒れたときのこと。物語の中身は書いていません。",
                     "Your first hour, controls, reading the screen and what happens when you fall. No story spoilers.") + toc(PLAY_TOC) + """
<section aria-labelledby="start">
<div class="wrap">
<h2 id="start">%(h_start)s</h2>
<p class="lead">%(lead)s</p>
<ul>
<li>%(r1)s</li><li>%(r2)s</li><li>%(r3)s</li>
</ul>
<div class="note">%(note)s</div>
</div>
</section>

<section class="alt" aria-labelledby="hour">
<div class="wrap">
<h2 id="hour">%(h_hour)s</h2>
<p>%(hour_lead)s</p>
<ol class="steps">
<li>%(s1)s</li><li>%(s2)s</li><li>%(s3)s</li><li>%(s4)s</li><li>%(s5)s</li><li>%(s6)s</li><li>%(s7)s</li><li>%(s8)s</li>
</ol>
<div class="mock" role="img" aria-label="%(mock_alt)s"><div class="strip"><b>%(mock_b)s</b>%(mock_t)s</div></div>
<p class="muted"><small>%(mock_note)s</small></p>
</div>
</section>

<section aria-labelledby="keys">
<div class="wrap">
<h2 id="keys">%(h_keys)s</h2>
%(ktable)s
<p>%(knote)s</p>
<h3>%(h_cmd)s</h3>
%(ctable)s
</div>
</section>

<section class="alt" aria-labelledby="notices">
<div class="wrap">
<h2 id="notices">%(h_not)s</h2>
<p>%(not_lead)s</p>
%(ntable)s
<p>%(not_note)s</p>
</div>
</section>

<section aria-labelledby="bars">
<div class="wrap">
<h2 id="bars">%(h_bars)s</h2>
<div class="grid g2">
<div class="panel"><h3>%(b1h)s</h3><p>%(b1)s</p>
<div class="mock" role="img" aria-label="%(b1alt)s"><div class="slots">%(slots)s</div></div></div>
<div class="panel"><h3>%(b2h)s</h3><p>%(b2)s</p>
<div class="mock" role="img" aria-label="%(b2alt)s"><div class="floor"><span class="f-hurt">%(f1)s</span><span class="f-safe">%(f2)s</span><span class="f-push">%(f3)s</span></div></div></div>
</div>
</div>
</section>

<section class="alt" aria-labelledby="death">
<div class="wrap">
<h2 id="death">%(h_death)s</h2>
<div class="grid g2">
<div class="panel"><h3>%(d1h)s</h3><p>%(d1)s</p></div>
<div class="panel"><h3>%(d2h)s</h3><p>%(d2)s</p></div>
</div>
<p>%(d3)s</p>
</div>
</section>

%(town_sec)s
<section aria-labelledby="stuck">
<div class="wrap">
<h2 id="stuck">%(h_stuck)s</h2>
<ul>
<li>%(k1)s</li><li>%(k2)s</li><li>%(k3)s</li><li>%(k4)s</li>
</ul>
</div>
</section>
""" % dict(
        h_start=T("はじめる前に", "Before you start"),
        lead=T("新しいワールドで遊んでください。難易度はピースフル以外にします。一人でも、仲間と 6 人まででも遊べます。",
               "Start a new world and use any difficulty except Peaceful. Play solo or with up to six friends."),
        r1=T("巨神の背中の島は、ワールドを開いたあと裏で少しずつ建ちます。最初の数分は動きが少し重くなることがあります。",
             "The titans' back islands are built in the background after you open the world. The first few minutes can feel a little heavy."),
        r2=T("ピースフルでは、巨神の背の部屋は眠ったままで戦えません。", "On Peaceful the back rooms stay dormant and cannot be fought."),
        r3=T("みんなで遊ぶときは、全員が同じ jar を使います。", "When playing together, everyone must use the same jar."),
        note=T("迷ったら、画面の左上に出る<strong>目的の帯</strong>か、<kbd>U</kbd> のクエストログを見てください。", "If you get lost, check the <strong>objective strip</strong> at the top left of the screen, or open the quest log with <kbd>U</kbd>."),
        h_hour=T("最初の 1 時間", "The first hour"),
        hour_lead=T("順番はゆるく決まっています。詰まらないための目安です。", "The order is loose. This is just a guide so you never get stuck."),
        s1=T("<strong>野営地で目覚める。</strong>人が去ったあとの野営地です。まわりを見回して、落ちている古い帳面を拾うと、伝承の書の最初の頁になります。", "<strong>Wake at a camp.</strong> Look around and pick up the old notebook; it becomes the first page of your lore book."),
        s2=T("<strong>煙と鐘の音を追う。</strong>遠くの細い煙と、ときどき聞こえる鐘が目印です。道の脇に案内人の灯りが並ぶこともあります。", "<strong>Follow the smoke and the bell.</strong> A thin column of smoke and an occasional bell guide you; guides' lights may line the road."),
        s3=T("<strong>語り部に話しかける。</strong>鐘の宿りにいます。道すがらに見たものも、話すと反応があります。", "<strong>Talk to the storyteller</strong> at the Bell Lodge. What you noticed on the way gets a reaction."),
        s4=T("<strong>寄り道を楽しむ。</strong>道の脇には巨大な足跡や、骨の丘があります。見つけると書に記されます。", "<strong>Enjoy the detours.</strong> Giant footprints and a hill of bones lie off the road; finding them adds to your book."),
        s5=T("<strong>空に気をつける。</strong>最初の昼ごろ、空を大きな影が通ることがあります。外にいると見えます。", "<strong>Watch the sky.</strong> Around the first midday a large shadow may pass overhead. You see it when outside."),
        s6=T("<strong>夜は耳をすます。</strong>日が落ちて静かになったら、慌てずにやり過ごしましょう。", "<strong>Listen at night.</strong> When it grows quiet after dusk, stay calm and let it pass."),
        s7=T("<strong>旅の人に会う。</strong>語り部の話が進むと、村の外れに焚き火が現れて、旅の人が空の鯨のことを教えてくれます。", "<strong>Meet the traveler.</strong> As the storyteller's tale advances, a campfire appears at the edge of the village with someone who tells of a whale in the sky."),
        s8=T("<strong>岩鯨に乗る。</strong>三日目の夕方ごろ（遅くとも五日目）、空を大きな鯨が泳ぎます。降りてきて垂らす鎖を右クリックすると、背中へ引き上げられます。", "<strong>Board the Stone Whale.</strong> Around the evening of day three (day five at the latest) a giant whale swims overhead. Right-click the chain it lowers to be pulled up onto its back."),
        mock_alt="目的の帯の見本", mock_b=T("目的", "Goal"), mock_t=T("鐘の宿りへ（北東 120 m）", "Head for the Bell Lodge (NE, 120 m)"),
        mock_note=T("目的の帯の見本です。方角と距離が出ます。", "A sample objective strip. It shows direction and distance."),
        h_keys=T("操作キー", "Controls"),
        ktable=table([T("キー", "Key"), T("働き", "Action")], krows),
        knote=T("キーは、設定の「操作」にある「天と淵のあいだ」の項目で変えられます。ほかの mod とぶつかったらここで直してください。装飾品は持っているだけでは効かず、持ち物の画面の右上の「装飾」のつまみから開く専用の枠に付けたときだけ効きます。",
                "Keys can be rebound under Controls in the \"Between the Sky and the Abyss\" category. Accessories only work when placed in the dedicated accessory slots, opened from the \"Accessories\" tab at the top right of the inventory."),
        h_cmd=T("誰でも使えるコマンド", "Commands anyone can use"),
        ctable=table([T("コマンド", "Command"), T("内容", "What it does")], crows),
        h_not=T("画面の知らせ", "Reading the screen"),
        not_lead=T("チャットには人の声とコマンドの返事だけが流れます。ゲームの中の知らせは、種類ごとに決まった場所に出ます。", "Chat carries only players' voices and command replies. In-game notices appear in fixed places by type."),
        ntable=table([T("場所", "Where"), T("出るもの", "What appears")], nrows),
        not_note=T("見逃したときは <kbd>N</kbd> の「知らせの記録」で読み返せます（その遊びのあいだの 200 件まで）。設定ファイルの <code>notifyMode</code>（TOAST・CHAT・BOTH）、<code>noticeSpeed</code>、<code>objectiveStrip</code> で見せ方を変えられます。",
                   "If you miss one, re-read it in the notice log with <kbd>N</kbd> (up to 200 entries per session). The config options <code>notifyMode</code> (TOAST, CHAT, BOTH), <code>noticeSpeed</code> and <code>objectiveStrip</code> change how they are shown."),
        h_bars=T("戦いの画面", "Combat HUD"),
        b1h=T("技の枠", "Skill slots"), b1=T("技は <kbd>R</kbd> <kbd>G</kbd> <kbd>V</kbd> <kbd>Z</kbd> に割り当てた 4 つ（二つ目の天職のあと 5 つ目）の枠から使います。マナを使い、技ごとに待ちがあります。", "Skills are cast from four slots (<kbd>R</kbd> <kbd>G</kbd> <kbd>V</kbd> <kbd>Z</kbd>); a fifth opens after your second calling. They use mana and have cooldowns."),
        b1alt="技の枠の見本", slots="".join('<div class="slot"><img class="px" src="img/skill/%02d.png" width="48" height="48" alt=""><kbd>%s</kbd></div>' % (i, k) for i, k in ((0, "R"), (12, "G"), (6, "V"), (28, "Z"))),
        b2h=T("床の予告", "Floor telegraphs"), b2=T("敵の技は、床に範囲が出てから来ます。印の外に立てば傷を受けません。", "Enemy attacks show their area on the floor first. Stand outside the mark and you take no damage."),
        b2alt="床の予告の見本", f1=T("傷が出る所（橙〜赤）", "Hurts (orange to red)"), f2=T("安全な筋（青白い破線）", "Safe lane (pale dashed)"), f3=T("押されるだけ（灰）", "Push only (gray)"),
        h_death=T("倒れたとき", "When you fall"),
        d1h=T("地上で倒れる（墓）", "On the ground (grave)"),
        d1=T("装備中の防具・左手・装飾・ホットバー・大事な品は持ったまま復活します（耐久は 1 割ほど減ります）。残りの持ち物と経験値は、墓に入ります。持ち主だけが開けられ、場所は <kbd>B</kbd> の控え帳に出ます。",
             "You respawn keeping worn armor, offhand, accessories, hotbar and key items (durability drops about 10%). The rest of your inventory and XP go into a grave that only you can open; its location shows in the record book (<kbd>B</kbd>)."),
        d2h=T("取りに行けないとき", "If you cannot reach it"),
        d2=T("語り部に「なくしたものがある」→「取りに行けない墓がある」と頼むと、選んだ墓の中身の半分が戻ります（墓は消えます）。", "Ask the storyteller about a lost item and pick an unreachable grave: half of its contents come back (the grave disappears)."),
        d3=T("背の順路では、倒れても持ち物と経験値は残ります。部屋の途中で全員が倒れたら、その部屋の最初から始まります。心臓の戦いで倒れたら、前室で目を覚まし、20 秒後に扉から戻れます（1 つの戦いで 3 回まで）。", "On a titan's back you keep your inventory and XP. If everyone falls in a room, it restarts from the room's beginning. In a heart fight you wake in the antechamber and can re-enter after 20 seconds (up to 3 times per fight)."),
        h_stuck=T("困ったとき", "If you are stuck"),
        k1=T("大事な品（鍵の欠片、心核、笛）をなくしたら、<kbd>B</kbd> の控え帳の「呼び戻す」を使います。", "Lost a key item (key fragment, core, whistle)? Use \"Recall\" in the record book (<kbd>B</kbd>)."),
        k2=T("順路で迷ったら、画面の目的の帯の矢印と距離を見てください。", "Lost on a route? Follow the arrow and distance on the objective strip."),
        k3=T("地上へ戻りたいときは、岩鯨は錨石、潮殻は潮門、雷翼は羽根の門、余燼は口の前の「見送りの石」を使います。", "To return to the ground: the anchor stone (Stone Whale), the tide gate (Tide Shell), the feather gate (Thunder Wing) or the farewell stone in front of the mouth (Ember)."),
        k4=T("次の巨神が見つからないときは、<kbd>U</kbd> のクエストログに手がかりがあります。", "Cannot find the next titan? The quest log (<kbd>U</kbd>) has hints."),
        town_sec=_town_sec(),
    )
    page("play.html", "遊び方 — 天と淵のあいだ", "How to play — Between the Sky and the Abyss",
         "最初の 1 時間、操作キー、画面の見方、倒れたときの扱い。", "Your first hour, controls, reading the HUD and what happens on death.", body)


# ------------------------------------------------------------------ 巨神と背中
PLAY_TOC = [("start", "はじめる前に", "Before you start"), ("hour", "最初の 1 時間", "The first hour"),
            ("keys", "操作キー", "Controls"), ("notices", "画面の知らせ", "Reading the screen"),
            ("bars", "戦いの画面", "Combat HUD"), ("death", "倒れたとき", "When you fall"),
            ("town", "町と乗り物", "Town and mounts"), ("stuck", "困ったとき", "If you are stuck")]

TITAN_TOC = [("ov", "四体の巨神", "Four titans"),
             ("whale", "岩鯨", "Stone Whale"), ("shell", "潮殻", "Tide Shell"), ("wing", "雷翼", "Thunder Wing"),
             ("ember", "余燼", "Ember"), ("common", "共通の決まり", "Shared rules"), ("pool", "部屋の組", "Room draws"),
             ("bigger", "大きくなった背", "Bigger backs"), ("travel", "巨神の子と旅", "Young carriers"),
             ("after", "心臓を止めたあと", "After the heart"), ("abyss", "五章　深淵", "Chapter 5: Abyss")]


def _first_steps():
    items = [
        ("faq.html#install", "導入する", "Install", "jar を mods に入れて、新しいワールドを作ります。", "Put the jar in mods and create a new world."),
        ("play.html", "遊び方を読む", "Read how to play", "最初の 1 時間の流れと、操作キー。", "The first hour and the controls."),
        ("titans.html#whale", "最初の巨神を知る", "Meet the first titan", "岩鯨（一章）。空から垂れる鎖で背に乗ります。", "The Stone Whale (chapter 1). Board by the chain it lowers from the sky."),
        ("skills.html", "技と装備を選ぶ", "Choose skills and gear", "五つの木と旅人から、自分のビルドを作ります。", "Build your character from five trees and the Wayfarer."),
    ]
    lis = "".join('<li><a href="%s"><strong>%s</strong></a>　%s</li>' % (h, T(a, b), T(c, d)) for h, a, b, c, d in items)
    return ('<section class="alt" aria-labelledby="first"><div class="wrap"><h2 id="first">%s</h2>'
            '<ol class="steps">%s</ol></div></section>' % (T("はじめての人は、この順で", "New here? Go in this order"), lis))


def _town_sec():
    return ('<section class="alt" aria-labelledby="town" id="town"><div class="wrap">'
            '<h2 id="town">%s</h2><p class="lead">%s</p>'
            '<ol class="steps"><li>%s</li><li>%s</li><li>%s</li><li>%s</li></ol>'
            '<p class="muted"><small>%s</small></p></div></section>\n') % (
        T("町と乗り物", "Town and mounts"),
        T("都は、案内人と灯りで道を示します。乗り物は厩舎で買えます。", "The capital shows the way with guides and lights. Mounts are sold at the stable."),
        T("門と広場の白い外套の案内人に話して、行き先を選びます。", "Talk to a guide in a white cloak at the gate or the square and choose a destination."),
        T("施設を選ぶと、色の灯りが戸の前まで地面に灯ります。", "Pick a building and colored lights line the ground all the way to its door."),
        T("乗り物は、都の東の大通りの南にある厩舎で、番の人に話して買います。最初から荷獣を一つ持っています。", "Buy mounts from the attendant at the stable, south of the east avenue. You start with one pack beast."),
        T("乗り笛を使うと乗り物が呼べ、もう一度で帰せます。速さ・跳び・体力は鐘銭で段ごとに上げられます。", "Use a mount whistle to call a mount and use it again to send it away. Speed, jumps and stamina rise by tier for bell coins."),
        T("道の端の黒い樫の柱と吊りランタンが、夜の目印です。M の地図で、施設と自分の位置を確かめられます。", "Black oak posts and hanging lanterns mark the way at night. The M map shows the buildings and where you are."))


def _pool_html():
    panels = [
        (T("順路", "Route"), T("歩 → 智 → 闘 → 中ボス → 心臓の、一本道です。各枠は 2 部屋のどちらかが選ばれ、周ごとに変わります。", "Traverse, then wit, then combat, then the mid-boss and the heart, in one line. Each slot draws one of its two rooms, and the draw changes each cycle.")),
        (T("一人でも、仲間とでも", "Solo or together"), T("どの部屋も一人で越えられます。分担すると速くなり、部屋の戦いは中にいる人数に合わせて強くなります。", "Every room can be cleared alone. Sharing the work is faster, and fights grow with the number of people inside.")),
        (T("部屋の報い", "Room rewards"), T("部屋を越えると、その巨神の素材と共鳴の欠片が渡ります（歩・智は素材 1・欠片 1、闘は素材 2・欠片 1、中ボスは素材 2・欠片 2・結晶 1。崩れずに順路を越えると欠片 +2）。周回では半分、その日 6 部屋を超えると 4 分の 1。称号は巨神ごとに 3 つ（順路・崩れなし・六部屋）で、地上の祠の試練の板に出ます。", "Clearing a room grants that titan's materials and resonance fragments (traverse and wit: 1 material and 1 fragment; combat: 2 materials and 1 fragment; mid-boss: 2 materials, 2 fragments and 1 crystal; +2 fragments for a route with no collapse). Repeat runs give half, and a quarter after six rooms in a day. Each titan has three titles (route, no collapse, six rooms), shown on the trial board at the ground shrine.")),
        (T("全員が倒れたら", "If everyone falls"), T("その部屋の最初から始まります。背のパーティの全員がいなくなって約 3 分で、順路は最初に戻ります。", "The room restarts from its beginning. About three minutes after no one from the party is on the back, the route resets.")),
    ]
    grid = "".join('<div class="panel"><h3>%s</h3><p>%s</p></div>' % (h, p) for h, p in panels)
    return ('<section aria-labelledby="h-pool" id="pool"><div class="wrap">'
            '<h2 id="h-pool">%s</h2><p class="lead">%s</p><div class="grid g2">%s</div>'
            '<p class="muted"><small>%s</small></p></div></section>\n') % (
        T("部屋の組と、くり返し", "Room draws and repeats"),
        T("各背は、歩・智・闘の三つの枠を持ち、枠ごとに 2 つの部屋があります。周回するたびに枠ごとに一つ選ばれるので、同じ背でも遊びが変わります。",
          "Each back has three slots (traverse, wit, combat) with two rooms apiece. Each cycle draws one room per slot, so the same back plays differently."),
        grid,
        T("部屋の組は順次増えています。深淵の難度は<a href=\"rebirth.html#abyss\">転生のページ</a>へ。", "More room draws are being added. For Abyss difficulty see <a href=\"rebirth.html#abyss\">Rebirth</a>."))


def titans():
    def heart(k):
        return "".join('<img class="px" src="img/boss/%s" width="32" height="32" alt="">' % n for n in HEARTS[k])

    cards = []
    for t in TITANS:
        d = TITAN_BODY[t["k"]]
        trials = "".join("<li><strong>%s</strong>　%s</li>" % (T(a, b), T(c, e)) for a, b, c, e in d["trials"])
        gear = "".join(item_li(g) for g in d["gear"])
        cards.append("""
<section class="titan-sec" id="%(k)s" aria-labelledby="h-%(k)s">
<div class="wrap">
<div class="titan-hd"><div class="pic">%(pic)s</div>
<div><h2 id="h-%(k)s" class="%(cls)s">%(ch)s　%(name)s</h2>
<p><span class="tag %(cls)s">%(sub)s</span><span class="tag %(cls)s">%(elem)s</span><span class="tag %(cls)s">%(lv)s</span></p>
<p>%(look)s</p>
<h4>%(h_board)s</h4><p>%(board)s</p></div></div>
<div class="grid g2">
<div class="panel"><h3>%(h_trials)s</h3><ul>%(trials)s</ul></div>
<div class="panel"><h3>%(h_heart)s</h3><div class="heart-row" role="img" aria-label="%(heart_alt)s">%(hearts)s</div><p>%(hearttxt)s</p>
<p><strong>%(h_dom)s</strong>　%(dom)s</p></div>
</div>
<h3 style="margin-top:1em">%(h_gear)s</h3>
<ul class="iconrow">%(gear)s</ul>
</div>
</section>%(sep)s""" % dict(
            k=t["k"], cls=t["cls"], pic=pimg("img/arch_%s.png" % t["k"], "%s のドット絵" % t["ja"], "Pixel art of %s" % t["en"], 170, 168),
            ch=T(t["ch_ja"], t["ch_en"]), name=T(t["ja"], t["en"]), sub=T(t["sub_ja"], t["sub_en"]), elem=T("属性: " + t["elem_ja"], "Theme: " + t["elem_en"]),
            lv=T("推奨 Lv %d" % t["lv"], "Rec. Lv %d" % t["lv"]),
            look=T(*d["look"]), h_board=T("乗り方", "How to board"), board=T(*d["board"]),
            h_trials=T("歩・智・闘の部屋と中ボス", "Rooms: traverse, wit, combat, and the mid-boss"), trials=trials,
            h_heart=T("心臓", "The heart"), hearts=heart(t["k"]), heart_alt="心臓の三段階の絵",
            hearttxt=T(*d["heart"]), h_dom=T("領域「%s」" % d["dom"][0], "Domain: \"%s\"" % d["dom"][1]), dom=T(*d["domtxt"]),
            h_gear=T("ここで手に入る装備", "Gear from this titan"), gear=gear,
            sep=divider()))

    rows = [[T(t["ch_ja"] + " " + t["ja"], t["ch_en"] + " " + t["en"]), str(t["lv"]), T(t["where_ja"], t["where_en"]), T(*BOARD_SHORT[t["k"]]), T(*CAPS[t["k"]])] for t in TITANS]
    common = """
<section class="alt" aria-labelledby="common">
<div class="wrap">
<h2 id="common">%(h)s</h2>
<div class="grid g2">
<div class="panel"><h3>%(a_h)s</h3><ul><li>%(a1)s</li><li>%(a2)s</li><li>%(a3)s</li><li>%(a4)s</li></ul></div>
<div class="panel"><h3>%(b_h)s</h3><ul><li>%(b1)s</li><li>%(b2)s</li><li>%(b3)s</li><li>%(b4)s</li><li>%(b5)s</li></ul></div>
<div class="panel"><h3>%(c_h)s</h3><ul><li>%(c1)s</li><li>%(c2)s</li><li>%(c3)s</li><li>%(c4)s</li></ul></div>
<div class="panel"><h3>%(d_h)s</h3><p>%(d1)s</p>
<div class="mock" role="img" aria-label="%(lamp_alt)s"><div class="lamps"><span class="lamp g"><i></i>%(l1)s</span><span class="lamp y"><i></i>%(l2)s</span><span class="lamp r"><i></i>%(l3)s</span></div></div>
<p class="muted" style="margin-top:.6em"><small>%(d2)s</small></p></div>
</div>
</div>
</section>
""" % dict(
        h=T("巨神の共通の決まり", "Rules shared by all titans"),
        a_h=T("乗る資格と順番", "Boarding and order"),
        a1=T("巨神は岩鯨 → 潮殻 → 雷翼 → 余燼の順に現れます。誰かが前の心臓を止めると、次が世界に現れます。", "Titans appear in order: Stone Whale, Tide Shell, Thunder Wing, Ember. When someone stops the previous heart, the next appears."),
        a2=T("乗れるのは、自分で前の心臓を止めた人です。連れていってもらうことはできません。", "You can board only if you stopped the previous heart yourself; you cannot be carried along."),
        a3=T("乗れるときは、乗り込み口が遠くからでもほのかに脈打つ光で分かります。", "When you may board, the entrance pulses with a faint glow, visible from afar."),
        a4=T("巨神は止まってから約 20 分のあいだ乗れます（残り 2 分と 30 秒に知らせ）。", "A titan stays boardable for about 20 minutes (warnings at 2 minutes and 30 seconds left)."),
        b_h=T("背中の決まり", "Rules on the back"),
        b1=T("サバイバルではブロックを壊せず、置けません。作業台・かまど・チェストなどの野営の道具は、離れた地面に一人 8 個まで置けます。", "In survival you cannot break or place blocks. Camp tools such as a crafting table, furnace or chest can be placed on safe ground (up to 8 per player)."),
        b2=T("エリトラ・エンダーパール・ウィンドチャージ・花火・バケツなど、部屋を飛ばせる物は使えません。", "Elytra, ender pearls, wind charges, fireworks, buckets and anything else that skips a room is blocked."),
        b3=T("倒れても持ち物と経験値は残り、休み場の焚き火から再開します。", "Dying keeps your items and XP; you resume at a rest campfire."),
        b4=T("端から落ちると、それぞれの巨神の決まりで戻されます（岩鯨は滑空、潮殻は潮流、雷翼は嵐、余燼は灰）。", "Falling off sends you back in each titan's own way (glide, tide, storm, ash)."),
        b5=T("部屋は何度でも挑めます。パーティの誰も背中にいなくなって約 3 分で、そのパーティの順路は最初に戻ります。", "Rooms can be repeated. About 3 minutes after nobody from your party remains, that party's route resets."),
        c_h=T("心臓の戦い", "Heart fights"),
        c1=T("心臓の戦いで倒れたら、前室で目を覚まし、20 秒後に戻れます（1 戦に 3 回まで）。戻ると 3 秒の守りがあります。", "If you fall in a heart fight you wake in the antechamber and may re-enter after 20 s (3 times per fight), with 3 s of protection."),
        c2=T("敵の技は床に予告が出ます。橙〜赤の実線は傷、青白い破線は安全な筋、灰は押されるだけです。", "Attacks are telegraphed on the floor: orange to red solid is damage, pale dashed is a safe lane, gray only pushes."),
        c3=T("体力が半分を切ると、一度だけ「領域」が広がります。領域のあいだ心臓は傷を受けず、部屋に立つ三つの仕掛けを解くまで続きます。解けると心臓は 5 秒のあいだ傷を 1.5 倍受けます。決まりの傷はすべて床の予告から出て、仕掛けは一人でも解けます。", "When its health drops below half, the heart opens a domain once. During it the heart cannot be damaged, and it lasts until you solve three gimmicks in the room; solving them leaves the heart taking 1.5x damage for 5 seconds. All domain damage is telegraphed on the floor and the gimmicks are solvable solo."),
        c4=T("一方向に回り続けるだけでは勝てません。止まる・逆へ回る・跳ぶ・内外へ出る、で避けます。", "Circling in one direction forever will not work. Stop, reverse, jump or move in and out."),
        d_h=T("備えの三灯", "The three readiness lamps"),
        d1=T("乗り込み口に近づくと、今の備えが三つの灯で出ます。身（防具）・刃（武器）・糧（回復と護りの品）です。", "Near an entrance your readiness shows as three lamps: body (armor), blade (weapon) and provisions (healing and protection)."),
        lamp_alt="備えの三灯の見本", l1=T("緑: 足りている", "Green: ready"), l2=T("黄: 少し足りない", "Yellow: a bit short"), l3=T("赤: 足りない", "Red: lacking"),
        d2=T("足りない灯があると「このまま乗る？」と聞かれ、5 秒以内に同じ操作をすると乗れます。", "If a lamp is short you are asked to confirm; repeat the action within 5 seconds to board."),
    )
    pool = _pool_html()
    travel = """
<section class="alt" aria-labelledby="h-travel" id="travel">
<div class="wrap">
<h2 id="h-travel">%(h)s</h2>
<p class="lead">%(lead)s</p>
<ol class="steps">
<li>%(s1)s</li><li>%(s2)s</li><li>%(s3)s</li><li>%(s4)s</li><li>%(s5)s</li>
</ol>
<p class="muted"><small>%(note)s</small></p>
</div>
</section>
""" % dict(
        h=T("巨神の子と旅", "Young carriers and travel"),
        lead=T("成体の巨神は、もう動き回りません。遠くへの旅は、笛で呼ぶ「子」（巨神の分かれた鼓動、歌のかけら）が運びます。",
               "Adult titans no longer roam. Long journeys are carried by a young one, a split beat of the titan, summoned with a whistle."),
        s1=T("<strong>呼ぶ。</strong>笛（潮殻は貝）を使うと、巨神の子が現れます。子は乗っている間だけいます。", "<strong>Summon.</strong> Use the whistle (a conch for Tide Shell) and a young carrier appears. It exists only while someone rides it."),
        s2=T("<strong>全員で乗る。</strong>パーティ全員（最大 6 人）が乗れます。", "<strong>Ride together.</strong> The whole party, up to six, can ride."),
        s3=T("<strong>行き先を選ぶ。</strong>持ち主が海図から、はじまりの地・覚えた碑・出会った巨神のいる場所を選びます。行き先は子の得意で限られ、選べないものには理由が付きます。", "<strong>Pick a destination.</strong> The owner chooses from a chart: the starting land, remembered tablets or the places of titans you have met. Each young carrier has strengths that limit destinations, and unavailable ones show why."),
        s4=T("<strong>出発。</strong>全員が乗っていれば約 3 秒、64 ブロック以内に乗っていない仲間がいれば約 15 秒（最大 30 秒）待ちます。遠い旅は途中で雲の帳に包まれます。", "<strong>Depart.</strong> About 3 seconds if everyone is aboard, about 15 (up to 30) if a party member within 64 blocks has not boarded. Long trips pass through a veil of cloud."),
        s5=T("<strong>着く。</strong>行き先が成体なら、足もとの光の輪に立つと背へ入れます（この旅に乗っていた人だけ）。全員が降りると、子は歌に溶けて消えます。", "<strong>Arrive.</strong> If the destination is an adult titan, stand in the glowing ring at your feet to enter its back (riders only). Once everyone dismounts the young carrier dissolves into song."),
        note=T("親の巨神が眠れば、子も眠ります。誰も乗らないまま 20 秒、行き先を決めないまま約 2 分で子は溶けます。", "If the parent titan sleeps, so does its young one. A young carrier dissolves after 20 seconds with no rider, or about 2 minutes without a destination."),
    )
    after = """
<section aria-labelledby="after">
<div class="wrap">
<h2 id="after">%(h)s</h2>
<div class="grid g2">
<div class="panel"><h3>%(a_h)s</h3><p>%(a)s</p></div>
<div class="panel"><h3>%(b_h)s</h3><p>%(b)s</p></div>
<div class="panel"><h3>%(c_h)s</h3><p>%(c)s</p></div>
<div class="panel"><h3>%(d_h)s</h3><p>%(d)s</p></div>
</div>
</div>
</section>
""" % dict(
        h=T("心臓を止めたあと", "After the heart stops"),
        a_h=T("巨神は眠りにつく", "The titan sleeps"), a=T("心臓を止めても巨神は死にません。鼓動が止まり、深い眠りにつきます。少し静かになって背中が震えたあと、戦った人とそのパーティは巨神の足もとへ降ろされます。眠っているあいだ、巨神は動かず、歌わず、乗れません。鼓動が戻ると、また目を覚まします。", "Stopping the heart does not kill the titan: its beat halts and it falls into deep sleep. After a quiet moment and a tremor, the fighters and their party are set down at its feet. While asleep it neither moves, sings nor can be boarded; when the beat returns it wakes."),
        b_h=T("報酬", "Rewards"), b=T("参加した人それぞれに、天の鍵の欠片（一人一つ）、経験値、装備が渡ります。四つの欠片がそろい、四体の心臓を自分で止めると、欠片は一つの天の鍵になります。", "Every participant gets a sky-key fragment (one each), XP and gear. Collect all four fragments by stopping all four hearts yourself and they become a single sky key."),
        c_h=T("心臓の遺物の選択", "Choosing a relic"), c=T("心臓を初めて止めたあと、眠りの場面が終わると専用の画面が開き、その巨神の専用の遺物から一つを選びます。パーティの仲間もそれぞれ自分で選びます。「あとで」にして、旅嚢・控え帳・コマンドから開き直せます。", "After first stopping a heart, a dedicated screen opens when the sleep scene ends and you choose one of that titan's dedicated relics. Party members choose individually. You can postpone it and reopen from the satchel, record book or a command."),
        d_h=T("周回", "Repeat runs"), d=T("部屋と心臓は何度でも挑めます。専用装備は周回で揃い、共鳴の炉で強化・再鍛造・交換できます。", "Rooms and hearts can be repeated. Dedicated gear fills in over repeat runs and can be enhanced, reforged and traded at the Resonance Forge."),
    )
    overview = """
<section aria-labelledby="ov">
<div class="wrap">
<h2 id="ov">%(h)s</h2>
<p class="lead">%(lead)s</p>
%(t)s
<p class="muted">%(n)s</p>
</div>
</section>%(sep)s""" % dict(
        h=T("四体の巨神", "The four titans"),
        lead=T("巨神の背中は、それ自体がダンジョンです。巨神ごとに、背中の遊び心地も、乗り方も違います。", "A titan's back is itself the dungeon. Each titan plays differently and is boarded differently."),
        t=table([T("巨神", "Titan"), T("推奨 Lv", "Rec. Lv"), T("どこにいる", "Where"), T("乗り方", "Boarding"), T("心臓を止めたあとの上限 Lv", "Level cap after its heart")], rows),
        n=T("推奨より 3 レベルを超えて上だと、倒したときの経験値が減ります。物語の進みで上限が決まります（最初は 15）。", "More than 3 levels above the recommendation reduces XP. Story progress sets the level cap (15 at the start)."),
        sep=divider())
    body = page_head("巨神と背中", "Titans and their backs", "四体の巨神の姿、乗り方、背中の三つの部屋、心臓、領域。五章の深淵の入口も。仕組みだけを書いています。",
                     "Four titans and the fifth chapter, the abyss: how to board, the rooms on each back, the hearts and domains. Mechanics only.") + toc(TITAN_TOC) + overview + "".join(cards) + common + pool + pages_d.titans_extra() + travel + after + pages_d.titans_abyss()
    page("titans.html", "巨神と背中 — 天と淵のあいだ", "Titans and their backs — Between the Sky and the Abyss",
         "岩鯨・潮殻・雷翼・余燼。乗り方、三つの部屋、心臓、領域、装備。五章の深淵。", "Stone Whale, Tide Shell, Thunder Wing and Ember: boarding, trials, hearts, domains and gear. Chapter 5, the abyss.", body)


HEARTS = {
    "whale": ["stone_whale_heart_icon_p1.png", "stone_whale_heart_icon_p2.png", "stone_whale_heart_icon_p3.png"],
    "shell": ["tide_heart_icon_p1.png", "tide_heart_icon_p2.png", "tide_heart_icon_p3.png"],
    "wing": ["storm_heart_icon_p1.png", "storm_heart_icon_p2.png", "storm_heart_icon_p3.png"],
    "ember": ["ember_heart_icon_p1.png", "ember_heart_icon_p2.png", "ember_heart_icon_p3.png"],
}

BOARD_SHORT = {  # 乗り込み口 (成体の巨神は動かない。遠い旅は子が運ぶ)
    "whale": ("空から垂れる鎖を右クリック", "Right-click the chain lowered from the sky"),
    "shell": ("干潮のころ、浅瀬の「潮門」を泳ぐか舟でくぐる", "At low tide, swim or boat through the tide gate"),
    "wing": ("夕立のころ、峰に立つ「昇り風」に入る", "At the evening squall, step into the updraft on the peak"),
    "ember": ("夜、息を吸うあいだに口の中へ歩いて入る", "At night, walk into the mouth while it inhales"),
}
CAPS = {"whale": ("21", "21"), "shell": ("27", "27"), "wing": ("33", "33"), "ember": ("40", "40")}

TITAN_BODY = {
    "whale": dict(
        look=("空を泳ぐ、岩のような巨大な鯨。ときどき降りてきて、背中の錨石から鎖を地上へ垂らします。背中は同じ形の鯨のブロックの島で、尾の付け根の広場から、苔むした背を歩いて祠へ向かいます。",
              "A huge rock-like whale swimming the sky. Now and then it descends and lowers a chain from the anchor stone on its back. The back is an island shaped like the whale; you walk the mossy spine to a shrine."),
        board=("岩鯨が降りてくると、左のわき腹から鎖が垂れます。右クリックで背中へ引き上げられます。三日目の夕方に姿を現し、遅くとも五日目には現れます。降りるときは背中の錨石を右クリックします。",
               "When the whale descends, a chain hangs from its left flank; right-click it to be hoisted up. It appears on the evening of day three (day five at the latest). To leave, right-click the anchor stone."),
        trials=[("歩　骨玉運び", "Traverse: bone ball carry", "骨玉を祭壇まで運びます。持つあいだは遅く、跳べません。", "Carry a bone ball to the altar. You move slowly and cannot jump while holding it."),
                ("智　共鳴の骨", "Wit: resonant bones", "骨が鳴った順に叩き返します。", "Strike the bones back in the order they chimed."),
                ("闘　鯨の核を守る", "Combat: guard the whale core", "三つの波を、核を守りきって越えます。", "Hold the core through three waves."),
                ("中ボス　鯨骨の番兵長", "Mid-boss: Whalebone Warden Captain", "三つの部屋のあと、中ボスの間へ運ばれます。", "After the three rooms you are carried into the mid-boss chamber.")],
        heart=("岩鯨の心臓は 3 段階。体力が半分を切ると領域を広げます。", "The Stone Whale's heart has three phases and opens its domain below half health."),
        dom=("石の海", "Sea of Stone"), domtxt=("心臓は傷を受けなくなり、広がる輪を跳んで越えながら、部屋の三本の共鳴する骨を、鳴った順に叩き返すと解けます。", "The heart becomes invulnerable. Jump over the expanding rings and strike the three resonant bones back in the order they chimed to break the domain."),
        gear=["whalebone_greatsword", "captain_pike", "waterway_fang",
              "stone_whale_plating", "stone_whale_chestplate"]),
    "shell": dict(
        look=("海の底を歩く、山のような大亀。甲羅の上に「潮の民」の遺跡と珊瑚礁を乗せています。背中の亀の島では、2 分ごとの満ち引きで通れる道が変わります。",
              "A mountain-sized sea turtle walking the seabed, carrying the ruins of the Tide Folk and a coral reef on its shell. On its back the tide changes every two minutes and with it the paths you can take."),
        board=("朝と夕の干潮のころ、巨神は浅瀬へ寄って休みます。甲羅の脇にできる「潮門」を、泳ぐか舟でくぐります。二章で拾える「潮見の貝」を持つと、向き・距離・次の干潮までの時間が分かります。",
               "At morning and evening low tide it rests in the shallows. Swim or boat through the tide gate beside the shell. The Tide-Watching Shell found in chapter 2 shows direction, distance and time until low tide."),
        trials=[("歩　真珠の灯り運び", "Traverse: pearl light carry", "真珠を持つ人だけが照らせます。持つ人が道を選びます。", "Only the person holding the pearl can light the way; that person picks the route."),
                ("智　貝の音合わせ", "Wit: shell tune match", "同じ音の貝を探して、音を合わせます。", "Find the shells that play the same tone and match them."),
                ("闘　珊瑚の苗を守る", "Combat: guard the coral saplings", "苗を狙う群れから守り切ります。", "Defend the saplings from the swarm."),
                ("中ボス　潮招き", "Mid-boss: Tide Beckoner", "三つの部屋のあと、中ボスの間へ運ばれます。", "After the three rooms you are carried into the mid-boss chamber.")],
        heart=("潮殻の心臓は 3 段階。体力が半分を切ると領域を広げます。", "The Tide Shell's heart has three phases and opens its domain below half health."),
        dom=("合わせ貝", "Matching Shells"), domtxt=("心臓は傷を受けなくなり、同じ印の板を叩いて合わせます。水は出ません。", "The heart becomes invulnerable. Strike the plates that bear the same mark to match them. No water rises."),
        gear=["tide_shell_trident", "coral_shield", "tidecaller_claw",
              "coral_harpoon", "moontide_pearl", "tide_shell_chestplate"]),
    "wing": dict(
        look=("嵐を衣のように纏って雲の下を飛ぶ、山のような鳥。雨の幕の中で稲妻が光るたびに、巨大な翼の形が一瞬だけ浮かびます。背中は嵐の雲を突き抜けた上にあります。",
              "A mountain-sized bird flying beneath clouds, wearing a storm like a cloak. Each lightning flash reveals the shape of its vast wings for an instant. Its back lies above the storm clouds."),
        board=("午後の夕立のころ、近くのいちばん高い峰に降りて止まります。足もとに「昇り風」の柱が立つので、入ると背中へ運ばれます。風見鶏を持つと次の夕立までの時間と距離が分かります。",
               "At the afternoon squall it lands on the highest nearby peak. An updraft column rises at your feet and carries you to its back. A weathercock shows the time to the next squall and the distance."),
        trials=[("歩　避雷の羽運び", "Traverse: lightning-rod feather carry", "羽を持つ人に雷が落ちます。避雷の柱へ導いて運びます。", "Lightning strikes whoever carries the feather; lead it to the rods."),
                ("智　銅の回路つなぎ", "Wit: copper circuit hookup", "銅の線をつないで、雷の道を通します。", "Connect copper wires to route the lightning."),
                ("闘　帯電の連鎖", "Combat: charged chain", "雷の連鎖を断ちながら進みます。", "Break the chain of lightning as you advance."),
                ("中ボス　雷獣", "Mid-boss: Thunder Beast", "三つの部屋のあと、中ボスの間へ運ばれます。", "After the three rooms you are carried into the mid-boss chamber.")],
        heart=("雷翼の心臓は 3 段階。部屋で扱った雷と羽の仕掛けが、心臓の戦いにも出ます。", "The Thunder Wing's heart has three phases, and the lightning and feather mechanics from its rooms return in the fight."),
        dom=("雷の檻", "Cage of Thunder"), domtxt=("心臓は傷を受けなくなり、三本の避雷の柱が順に雷を溜めます。溜まりきる前に叩いて地へ逃がし、三本とも打てば解けます。", "The heart becomes invulnerable. Three lightning rods charge one after another; strike each to ground the bolt before it fully charges."),
        gear=["thunder_wing_bow", "raiju_claw", "warden_glaive",
              "storm_eye_feather", "storm_anklet", "thunder_wing_chestplate"]),
    "ember": dict(
        look=("地の中を泳ぐ、山のような火蜥蜴。昼は土の下を進み、夜に地上へ浮かび上がって火山のように眠ります。体は熱く、背中にいるあいだ一人ずつに「熱」がたまります。",
              "A mountain-sized fire lizard swimming through the earth. By day it travels underground; at night it rises and sleeps like a volcano. Its insides are hot, and while you are on its back each player accumulates heat."),
        board=("夜、地面に顎を下ろして眠ります。息を吸うあいだだけ口が開くので、そのあいだに自分の足で口の中へ歩いて入ります。雷翼のあとに渡される温石で、向きと距離が分かります。",
               "At night it rests its jaw on the ground. The mouth opens only while it inhales; walk in on your own feet during that time. A warm stone, given after Thunder Wing, shows direction and distance."),
        trials=[("歩　熾火運び", "Traverse: ember carry", "熾火を冷める前に炉へ運びます。", "Carry embers to the furnace before they cool."),
                ("智　鍛冶の手順", "Wit: forging steps", "壁画の順に、鍛冶の手順をなぞります。", "Follow the forging steps in the order of the mural."),
                ("闘　炉を守る", "Combat: guard the furnace", "炉を守って、押し寄せる敵を退けます。", "Protect the furnace and hold back the foes."),
                ("中ボス　灰の鍛え手", "Mid-boss: Ashen Smith", "三つの部屋のあと、中ボスの間へ運ばれます。", "After the three rooms you are carried into the mid-boss chamber.")],
        heart=("余燼の心臓は 3 段階。体力が半分を切ると領域を広げます。", "The Ember's heart has three phases and opens its domain below half health."),
        dom=("炉心", "Furnace Core"), domtxt=("心臓は傷を受けなくなり、三つの熾の核のそばでしゃがみ続けて冷まします（熱い床の予告を避けながら）。三つ冷ませば解けます。", "The heart becomes invulnerable. Crouch beside each of three ember cores to quench them while dodging heated floor telegraphs."),
        gear=["ember_war_axe", "smith_hammer", "thrall_poker",
              "ember_lantern", "ember_mantle", "ember_chestplate"]),
}
