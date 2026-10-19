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


def index():
    arches = "".join(
        '<a class="panel titan-card" href="titans.html#%s"><div class="pic">%s</div><div class="body"><h3 class="%s">%s</h3>'
        '<p class="muted">%s</p></div></a>' % (
            t["k"], pimg("img/arch_%s.png" % t["k"], "%s のドット絵" % t["ja"], "Pixel art of %s" % t["en"], 170, 168),
            t["cls"], T(t["ch_ja"] + "　" + t["ja"], t["ch_en"] + ": " + t["en"]), T(t["sub_ja"], t["sub_en"]))
        for t in TITANS)

    def feat(img, name_ja, name_en, ja, en, href):
        return ('<a class="panel feature" href="%s">%s<div><h3>%s</h3><p>%s</p></div></a>' % (
            href, '<img class="px" src="img/%s" width="48" height="48" alt="" loading="lazy">' % img, T(name_ja, name_en), T(ja, en)))

    feats = "".join([
        feat("item/stone_whale_call.png", "四体の巨神", "Four titans",
             "空・海・嵐・地の中を行く巨神に、一体ずつ挑みます。", "Take on titans that move through sky, sea, storm and earth, one by one.", "titans.html"),
        feat("skill/05.png", "36 の技と天職", "36 skills, callings",
             "5 つの木と旅人。天職を選び、刻印で技の形を変えます。", "Five trees plus the Wayfarer. Choose a calling and reshape skills with runes.", "skills.html"),
        feat("item/waterway_fang.png", "13 種の武器", "13 weapons",
             "型・階級・特性・伝説の書き換え。周回で揃えます。", "Types, tiers, affixes and legendary rewrites. Collect them over repeat runs.", "gear.html"),
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
<div class="panel stat"><b>12</b><span>%(s2)s</span></div>
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
        d1=T("巨神の背は、巨神自身と同じ形・同じ色のブロックでできた島です。試練の部屋は、すべてその体の中に埋まっています。",
             "Each back is an island of blocks shaped and colored like the titan itself. Every trial room is buried inside its body."),
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
        s1=T("巨神", "titans"), s2=T("背中の試練（3 × 4）", "back trials (3 x 4)"), s3=T("技", "skills"), s4=T("人まで", "players max"),
        h_feat=T("できること", "What you can do"),
        feats=feats,
        h_chap=T("四つの章", "Four chapters"),
        chap_lead=T("巨神 1 体につき 1 章。前の巨神の心臓を自分で止めた人だけが、次の巨神に乗れます。",
                    "One chapter per titan. Only a player who stopped the previous heart can board the next titan."),
        arches=arches,
        h_go=T("はじめる", "Get started"),
        g1h=T("導入", "Install"), g1=T("NeoForge %s と GeckoLib が要ります。jar を mods フォルダへ入れるだけです。" % MC,
                                       "Needs NeoForge %s and GeckoLib. Drop the jars into your mods folder." % MC),
        g1b=T("導入の手順", "Install steps"),
        g2h=T("最初の 1 時間", "Your first hour"), g2=T("野営地で目覚めてから、最初の巨神が空に現れるまでの流れです。", "From waking at a camp to the first titan appearing in the sky."),
        g2b=T("遊び方", "How to play"),
        div=divider(),
    )
    page("index.html", "天と淵のあいだ — 巨神の背を行く Minecraft RPG mod", "Between the Sky and the Abyss — a titan-climbing RPG mod for Minecraft",
         "Minecraft Java 1.21.1（NeoForge）向けの RPG mod。空・海・嵐・地の中を行く巨神の背に乗り、体の中の試練と心臓に挑みます。",
         "An RPG mod for Minecraft Java 1.21.1 (NeoForge). Board titans of sky, sea, storm and earth and face the trials inside their bodies.", body)


# ------------------------------------------------------------------ 遊び方
def play():
    keys = [
        ("J", "ステータス（レベル、力の振り分け、振り直し）", "Status (level, stat points, respec)"),
        ("K", "技の画面（技を覚える、四つの枠に置く、天職・刻印・型の保存）", "Skills screen (learn skills, slots, calling, runes, loadouts)"),
        ("U", "クエストログ（メインとサブ、追跡の切り替え）", "Quest log (main and side quests, tracking)"),
        ("B", "控え帳（巨神の進み、墓の場所、大事な品、呼び戻し）。<code>/skyrecord</code> でも開く", "Record book (titan progress, grave locations, key items, recall); also <code>/skyrecord</code>"),
        ("N", "知らせの記録（流れた語り・獲得・場所の名前を読み返す）", "Notice log (re-read narration, rewards, place names)"),
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
                     "Your first hour, controls, reading the screen and what happens when you fall. No story spoilers.") + """
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
        r2=T("ピースフルでは、巨神の背中の試練は眠ったままで戦えません。", "On Peaceful the back trials stay dormant and cannot be fought."),
        r3=T("みんなで遊ぶときは、全員が同じ jar を使います。", "When playing together, everyone must use the same jar."),
        note=T("迷ったら、画面の左上に出る<strong>目的の帯</strong>か、<kbd>U</kbd> のクエストログを見てください。", "If you get lost, check the <strong>objective strip</strong> at the top left of the screen, or open the quest log with <kbd>U</kbd>."),
        h_hour=T("最初の 1 時間", "The first hour"),
        hour_lead=T("順番はゆるく決まっています。詰まらないための目安です。", "The order is loose. This is just a guide so you never get stuck."),
        s1=T("<strong>野営地で目覚める。</strong>人が去ったあとの野営地です。まわりを見回して、落ちている古い帳面を拾うと、伝承の書の最初の頁になります。", "<strong>Wake at a camp.</strong> Look around and pick up the old notebook; it becomes the first page of your lore book."),
        s2=T("<strong>煙と鐘の音を追う。</strong>遠くの細い煙と、ときどき聞こえる鐘が道しるべです。道には積み石が並んでいます。", "<strong>Follow the smoke and the bell.</strong> A thin column of smoke and an occasional bell guide you; cairns line the road."),
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
        d1=T("装備中の防具・左手・装飾・ホットバー・大事な品は持ったまま復活します（耐久は 1 割ほど減ります）。残りの持ち物と経験値は、苔むした積み石の「墓」に入ります。持ち主だけが開けられ、場所は <kbd>B</kbd> の控え帳に出ます。",
             "You respawn keeping worn armor, offhand, accessories, hotbar and key items (durability drops about 10%). The rest of your inventory and XP go into a mossy cairn grave that only you can open; its location shows in the record book (<kbd>B</kbd>)."),
        d2h=T("取りに行けないとき", "If you cannot reach it"),
        d2=T("語り部に「なくしたものがある」→「取りに行けない墓がある」と頼むと、選んだ墓の中身の半分が戻ります（墓は消えます）。", "Ask the storyteller about a lost item and pick an unreachable grave: half of its contents come back (the grave disappears)."),
        d3=T("巨神の背中の中では、倒れても持ち物と経験値は残り、休み場の焚き火から再開します。戦いの途中で倒れたら、前室で目を覚まし、20 秒後に扉から戻れます（1 つの戦いで 3 回まで）。", "Inside a titan's back you keep your inventory and XP and resume from a rest campfire. If you fall mid-fight you wake in the antechamber and can re-enter after 20 seconds (up to 3 times per fight)."),
        h_stuck=T("困ったとき", "If you are stuck"),
        k1=T("大事な品（鍵の欠片、心核、笛）をなくしたら、<kbd>B</kbd> の控え帳の「呼び戻す」を使います。", "Lost a key item (key fragment, core, whistle)? Use \"Recall\" in the record book (<kbd>B</kbd>)."),
        k2=T("試練で詰まったら、休み場の焚き火にしゃがんで右クリックすると、背中の入口へ戻れます。", "Stuck in a trial? Sneak and right-click a rest campfire to return to the entrance of the back."),
        k3=T("地上へ戻りたいときは、岩鯨は錨石、潮殻は潮門、雷翼は羽根の門、余燼は口の前の「見送りの石」を使います。", "To return to the ground: the anchor stone (Stone Whale), the tide gate (Tide Shell), the feather gate (Thunder Wing) or the farewell stone in front of the mouth (Ember)."),
        k4=T("次の巨神が見つからないときは、<kbd>U</kbd> のクエストログに手がかりがあります。", "Cannot find the next titan? The quest log (<kbd>U</kbd>) has hints."),
    )
    page("play.html", "遊び方 — 天と淵のあいだ", "How to play — Between the Sky and the Abyss",
         "最初の 1 時間、操作キー、画面の見方、倒れたときの扱い。", "Your first hour, controls, reading the HUD and what happens on death.", body)


# ------------------------------------------------------------------ 巨神と背中
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
            h_trials=T("三つの試練", "Three trials"), trials=trials,
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
        b2=T("エリトラ・エンダーパール・ウィンドチャージ・花火・バケツなど、試練を飛ばせる物は使えません。", "Elytra, ender pearls, wind charges, fireworks, buckets and anything else that skips a trial are blocked."),
        b3=T("倒れても持ち物と経験値は残り、休み場の焚き火から再開します。", "Dying keeps your items and XP; you resume at a rest campfire."),
        b4=T("端から落ちると、それぞれの巨神の決まりで戻されます（岩鯨は滑空、潮殻は潮流、雷翼は嵐、余燼は灰）。", "Falling off sends you back in each titan's own way (glide, tide, storm, ash)."),
        b5=T("試練は何度でも挑めます。パーティの誰も背中にいなくなって約 3 分で、そのパーティの試練は最初に戻ります。", "Trials can be repeated. About 3 minutes after nobody from your party remains, that party's trials reset."),
        c_h=T("心臓の戦い", "Heart fights"),
        c1=T("心臓の戦いで倒れたら、前室で目を覚まし、20 秒後に戻れます（1 戦に 3 回まで）。戻ると 3 秒の守りがあります。", "If you fall in a heart fight you wake in the antechamber and may re-enter after 20 s (3 times per fight), with 3 s of protection."),
        c2=T("敵の技は床に予告が出ます。橙〜赤の実線は傷、青白い破線は安全な筋、灰は押されるだけです。", "Attacks are telegraphed on the floor: orange to red solid is damage, pale dashed is a safe lane, gray only pushes."),
        c3=T("体力が半分を切ると、一度だけ「領域」が広がります。35 秒のあいだ領域だけの床の決まりが回り、与えた傷が最大体力の 6% に届くと領域が砕け、心臓は 5 秒のあいだ傷を 1.5 倍受けます。", "When its health drops below half, the heart opens a domain once. For 35 seconds domain-only floor rules run; deal 6% of its max health and the domain shatters, leaving the heart taking 1.5x damage for 5 seconds."),
        c4=T("一方向に回り続けるだけでは勝てません。止まる・逆へ回る・跳ぶ・内外へ出る、で避けます。", "Circling in one direction forever will not work. Stop, reverse, jump or move in and out."),
        d_h=T("備えの三灯", "The three readiness lamps"),
        d1=T("乗り込み口に近づくと、今の備えが三つの灯で出ます。身（防具）・刃（武器）・糧（回復と護りの品）です。", "Near an entrance your readiness shows as three lamps: body (armor), blade (weapon) and provisions (healing and protection)."),
        lamp_alt="備えの三灯の見本", l1=T("緑: 足りている", "Green: ready"), l2=T("黄: 少し足りない", "Yellow: a bit short"), l3=T("赤: 足りない", "Red: lacking"),
        d2=T("足りない灯があると「このまま乗る？」と聞かれ、5 秒以内に同じ操作をすると乗れます。", "If a lamp is short you are asked to confirm; repeat the action within 5 seconds to board."),
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
        c_h=T("心臓の遺物の選択", "Choosing a relic"), c=T("心臓を初めて止めたあと、眠りの場面が終わると専用の画面が開き、その巨神の専用伝説から一つを選びます。パーティの仲間もそれぞれ自分で選びます。「あとで」にして、旅嚢・控え帳・コマンドから開き直せます。", "After first stopping a heart, a dedicated screen opens when the sleep scene ends and you choose one of that titan's legendary relics. Party members choose individually. You can postpone it and reopen from the satchel, record book or a command."),
        d_h=T("周回", "Repeat runs"), d=T("試練と心臓は何度でも挑めます。専用装備は周回で揃い、共鳴の炉で強化・再鍛造・交換できます。", "Trials and hearts can be repeated. Dedicated gear fills in over repeat runs and can be enhanced, reforged and traded at the Resonance Forge."),
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
    body = page_head("巨神と背中", "Titans and their backs", "四体の巨神の姿、乗り方、背中の三つの試練、心臓、領域。仕組みだけを書いています。",
                     "Four titans: how to board, the three trials on each back, the hearts and domains. Mechanics only.") + overview + "".join(cards) + common + after
    page("titans.html", "巨神と背中 — 天と淵のあいだ", "Titans and their backs — Between the Sky and the Abyss",
         "岩鯨・潮殻・雷翼・余燼。乗り方、三つの試練、心臓、領域、装備。", "Stone Whale, Tide Shell, Thunder Wing and Ember: boarding, trials, hearts, domains and gear.", body)


HEARTS = {
    "whale": ["stone_whale_heart_icon_p1.png", "stone_whale_heart_icon_p2.png", "stone_whale_heart_icon_p3.png"],
    "shell": ["tide_heart_icon_p1.png", "tide_heart_icon_p2.png", "tide_heart_icon_p3.png"],
    "wing": ["storm_heart_icon_p1.png", "storm_heart_icon_p2.png", "storm_heart_icon_p3.png"],
    "ember": ["ember_heart_icon_p1.png", "ember_heart_icon_p2.png", "ember_heart_icon_p3.png"],
}

BOARD_SHORT = {
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
        trials=[("西の翼", "West wing", "飛び石・島・崩れる骨の橋をたどるアスレチック。", "A parkour route over stepping stones, islands and crumbling bone bridges."),
                ("東の翼", "East wing", "三つの広間の番兵を倒しながら進み、最後は中ボス「鯨骨の番兵長」。", "Fight through three halls of sentries to the mid-boss, the Whalebone Warden Captain."),
                ("下の水路", "Underside channel", "真っ暗な水没した迷路。灯をたずさえて光の道をたどり、息継ぎをしながら進みます。", "A pitch-dark flooded maze. Carry a light, follow the lit path and surface for breath.")],
        heart=("岩鯨の心臓は 3 段階。体力が半分を切ると領域を広げます。", "The Stone Whale's heart has three phases and opens its domain below half health."),
        dom=("石の海", "Sea of Stone"), domtxt=("部屋の色が変わり、石の海の床の決まりが 35 秒回ります。", "The room changes color and stone-sea floor rules run for 35 seconds."),
        gear=["whalebone_greatsword", "captain_pike", "waterway_fang",
              "stone_whale_plating", "stone_whale_chestplate"]),
    "shell": dict(
        look=("海の底を歩く、山のような大亀。甲羅の上に「潮の民」の遺跡と珊瑚礁を乗せています。背中の亀の島では、2 分ごとの満ち引きで通れる道が変わります。",
              "A mountain-sized sea turtle walking the seabed, carrying the ruins of the Tide Folk and a coral reef on its shell. On its back the tide changes every two minutes and with it the paths you can take."),
        board=("朝と夕の干潮のころ、巨神は浅瀬へ寄って休みます。甲羅の脇にできる「潮門」を、泳ぐか舟でくぐります。二章で拾える「潮見の貝」を持つと、向き・距離・次の干潮までの時間が分かります。",
               "At morning and evening low tide it rests in the shallows. Swim or boat through the tide gate beside the shell. The Tide-Watching Shell found in chapter 2 shows direction, distance and time until low tide."),
        trials=[("西 珊瑚の庭", "West: Coral Garden", "潮の合間をぬって、大きな真珠を祭壇まで運びます。", "Carry a great pearl to the altar between tides."),
                ("東 潮見の間", "East: Tide-Watch Hall", "栓で水位を動かし、三段の溜めを渡る謎解き。", "A puzzle of moving the water level with plugs to cross three basins."),
                ("前 群れの干潟", "Front: Swarm Flats", "干潮のあいだに群れの巣を壊し、最後は中ボス「潮招き」。", "Destroy the swarm nests during low tide, then face the mid-boss, the Tide Beckoner.")],
        heart=("潮殻の心臓は 3 段階。体力が半分を切ると領域を広げます。", "The Tide Shell's heart has three phases and opens its domain below half health."),
        dom=("満ち潮", "Rising Tide"), domtxt=("部屋の色が変わり、満ち潮の床の決まりが 35 秒回ります。", "The room changes color and rising-tide floor rules run for 35 seconds."),
        gear=["tide_shell_trident", "coral_shield", "tidecaller_claw",
              "coral_harpoon", "moontide_pearl", "tide_shell_chestplate"]),
    "wing": dict(
        look=("嵐を衣のように纏って雲の下を飛ぶ、山のような鳥。雨の幕の中で稲妻が光るたびに、巨大な翼の形が一瞬だけ浮かびます。背中は嵐の雲を突き抜けた上にあります。",
              "A mountain-sized bird flying beneath clouds, wearing a storm like a cloak. Each lightning flash reveals the shape of its vast wings for an instant. Its back lies above the storm clouds."),
        board=("午後の夕立のころ、近くのいちばん高い峰に降りて止まります。足もとに「昇り風」の柱が立つので、入ると背中へ運ばれます。風見鶏を持つと次の夕立までの時間と距離が分かります。",
               "At the afternoon squall it lands on the highest nearby peak. An updraft column rises at your feet and carries you to its back. A weathercock shows the time to the next squall and the distance."),
        trials=[("西 風切りの骨", "West: Windcut Bones", "上昇気流と滑空で、空洞の骨の中を手首まで渡ります。", "Ride updrafts and glide through a hollow wing bone to the wrist.")
                , ("東 雷の骨", "East: Thunder Bones", "落雷の予告を読み、雷を避雷の柱へ導いて骨の戸を開けます。中ボスは「雷獣」。", "Read the lightning telegraphs and guide strikes into lightning rods to open the bone door. Mid-boss: the Thunder Beast."),
                ("前 眼の間", "Front: Hall of the Eye", "巨神の眼から差す光を、羽鏡で三つの受け羽へ導きます。", "Steer light from the titan's eye into three receiving feathers with feather mirrors.")],
        heart=("雷翼の心臓は 3 段階で、三つの試練で覚えたことをそれぞれ使います（避雷の柱・昇り風・光）。", "The Thunder Wing's heart has three phases, each using what a trial taught you (lightning rods, updrafts, light)."),
        dom=("雷の檻", "Cage of Thunder"), domtxt=("部屋の色が変わり、雷の檻の床の決まりが 35 秒回ります。", "The room changes color and cage-of-thunder floor rules run for 35 seconds."),
        gear=["thunder_wing_bow", "raiju_claw", "warden_glaive",
              "storm_eye_feather", "storm_anklet", "thunder_wing_chestplate"]),
    "ember": dict(
        look=("地の中を泳ぐ、山のような火蜥蜴。昼は土の下を進み、夜に地上へ浮かび上がって火山のように眠ります。体の中は熱く、背中にいるあいだ一人ずつに「熱」がたまります。",
              "A mountain-sized fire lizard swimming through the earth. By day it travels underground; at night it rises and sleeps like a volcano. Its insides are hot, and while you are on its back each player accumulates heat."),
        board=("夜、地面に顎を下ろして眠ります。息を吸うあいだだけ口が開くので、そのあいだに自分の足で口の中へ歩いて入ります。雷翼のあとに渡される温石で、向きと距離が分かります。",
               "At night it rests its jaw on the ground. The mouth opens only while it inhales; walk in on your own feet during that time. A warm stone, given after Thunder Wing, shows direction and distance."),
        trials=[("西 灰の道", "West: Ash Road", "溶岩と灰の道を、熱をためすぎないように進みます。", "Cross a road of lava and ash while keeping your heat in check."),
                ("東 鍛えの間", "East: Forging Hall", "熱の色を読んで「火の鍵」を鍛え、焼き入れます。扉を開くと中ボス「灰の鍛え手」が目を覚まします。", "Read the colors of heat to forge and quench a fire key. Opening the door wakes the mid-boss, the Ashen Smith."),
                ("南 火守り", "South: Firekeeping", "背の亀裂の底で、消えかけた火床に熾をくべ続けます。", "At the bottom of a rift, keep feeding embers to a fading hearth.")],
        heart=("余燼の心臓は 3 段階。体力が半分を切ると領域を広げます。", "The Ember's heart has three phases and opens its domain below half health."),
        dom=("炉心", "Furnace Core"), domtxt=("部屋の色が変わり、炉心の床の決まりが 35 秒回ります。", "The room changes color and furnace-core floor rules run for 35 seconds."),
        gear=["ember_war_axe", "smith_hammer", "thrall_poker",
              "ember_lantern", "ember_mantle", "ember_chestplate"]),
}
