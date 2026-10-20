"""物語・導入とFAQ・更新履歴・別案 B / C。"""
from sitelib import *
from pages_a import TITANS


def story():
    body = page_head("物語", "Story", "ここにあるのは、これだけです。あとは、遊んで確かめてください。", "This is all there is here. The rest is for you to find in the game.") + """
<section aria-labelledby="story">
<div class="wrap">
<h2 id="story" style="position:absolute;left:-999px">%(h)s</h2>
%(plate)s
<p class="quote"><span>%(q1a)s</span><span>%(q1b)s</span></p>
<p class="quote"><span>%(q2a)s</span><span>%(q2b)s</span></p>
<div class="note">%(note)s</div>
</div>
</section>
""" % dict(
        h=T("物語", "Story"),
        plate=pimg("img/plate_end.png", "空と地の底のあいだで、あなたを待っているものがあります。", "Between the sky and the bottom of the earth, something is waiting for you.", 840, 336, "plate px"),
        q1a=T("空を、何かが通った。", "Something passed through the sky."),
        q1b=T("山より大きな影が、雲の上を、音もなく。", "A shadow larger than a mountain, above the clouds, without a sound."),
        q2a=T("空と地の底のあいだで、", "Between the sky and the bottom of the earth,"),
        q2b=T("あなたを待っているものがあります。", "something is waiting for you."),
        note=T("設計書（<code>docs/design/</code>）には物語の核心が書かれています。まっさらな気持ちで遊びたい人は開かないでください。このサイトにも核心は書いていません。",
               "The design documents (<code>docs/design/</code>) contain the heart of the story. If you want to play unspoiled, do not open them. This site contains no spoilers either."),
    )
    page("story.html", "物語 — 天と淵のあいだ", "Story — Between the Sky and the Abyss",
         "空を、何かが通った。山より大きな影が、雲の上を、音もなく。", "Something passed through the sky. A shadow larger than a mountain, above the clouds, without a sound.", body)


def faq():
    req = table([T("もの", "Requirement"), T("内容", "Details")], [
        ["Minecraft", T("Java 版 %s" % MC, "Java Edition %s" % MC)],
        [T("導入先", "Loader"), T("NeoForge %s（開発では %s）" % (MC, NEO), "NeoForge %s (developed on %s)" % (MC, NEO))],
        [T("必要な mod", "Required mod"), T("GeckoLib（NeoForge %s 用。開発では %s）。モデルの動きに使っています" % (MC, GECKO), "GeckoLib for NeoForge %s (developed on %s); used for model animation" % (MC, GECKO))],
        ["Java", "21"],
        [T("本体の jar", "Mod jar"), "<code>skyabyss-%s.jar</code>" % VERSION],
    ])
    gr = table([T("ゲームルール", "Game rule"), T("内容", "Effect")], [
        ["<code>skyabyssStoryGate</code>", T("<code>false</code> で巨神の順番と乗る資格の決まりを切る（好きな順に乗れる）", "<code>false</code> removes the titan order and boarding requirement (board in any order)")],
        ["<code>skyabyssStoryLevelCap</code>", T("<code>false</code> で物語の進みによるレベル上限を切る", "<code>false</code> removes the story-based level cap")],
        ["<code>skyabyssPartyShare</code>", T("その場にいなかった仲間への経験値の分け前（50〜80%）", "XP share for absent party members (50-80%)")],
        ["<code>skyabyssDeathMode</code>", T("地上で倒れたときの型（0 墓 / 1 バニラ / 2 全部残す / 3 耐久だけ減る。既定 0）", "Ground death mode (0 grave / 1 vanilla / 2 keep all / 3 durability loss only; default 0)")],
        ["<code>skyabyssGraveKeepGear</code>", T("墓の型で、装備中の防具を持って復活する（既定 true）", "In grave mode, respawn wearing your armor (default true)")],
        ["<code>skyabyssGraveOwnerOnly</code>", T("墓を開けられるのは持ち主だけ（既定 true）", "Only the owner can open a grave (default true)")],
        ["<code>skyabyssGraveExpire</code>", T("墓が消えるまでの分（0 で消えない）", "Minutes before a grave vanishes (0 = never)")],
        ["<code>skyabyssGraveRescue</code>", T("語り部の救いで戻る割合（%。0 で救いなし）", "Percent returned by the storyteller's rescue (0 = no rescue)")],
        ["<code>skyabyssDeathDurability</code>", T("耐久だけ減る型での減る割合（%）", "Durability loss percent in durability-only mode")],
        ["<code>skyabyssRevive</code>", T("パーティの仲間が倒れた人を起こせる仕組み（既定 true。false で従来どおり死ぬ）", "Whether party members can revive a downed player (default true; false means normal death)")],
        ["<code>skyabyssHeartReentryLimit</code>", T("戦いで倒れた人が 20 秒後に戻れる回数（既定 3。0 で数えない）", "Re-entries allowed per fight after 20 s (default 3; 0 = uncounted)")],
    ])
    cmds = table([T("コマンド（OP・権限 2）", "Command (op level 2)"), T("働き", "What it does")], [
        ["<code>/skyabyss opening reset &lt;名前&gt;</code>", T("序章を、もう一度遊べるように戻す", "Reset the prologue so it can be played again")],
        ["<code>/skyabyss cinematic reset &lt;名前&gt;</code>", T("見た扱いの場面を戻す", "Reset which cutscenes count as seen")],
        ["<code>/skyabyss titan reset &lt;titan&gt;</code>", T("その巨神の進みを戻す（例: <code>ember</code>）", "Reset that titan's progress (e.g. <code>ember</code>)")],
        ["<code>/skyabyss playtest now</code> / <code>next</code>", T("今の目的の出来事を起こす／今の目的を済ませて次へ", "Trigger the current objective's event / complete it and move on")],
    ])
    qa = [
        ("どの Minecraft で遊べますか。", "Which Minecraft versions work?", "Java 版 %s と NeoForge %s の組み合わせだけです。Forge や Fabric、統合版では動きません。" % (MC, MC),
         "Only Java Edition %s with NeoForge %s. Forge, Fabric and Bedrock are not supported." % (MC, MC)),
        ("既存のワールドで遊べますか。", "Can I use an existing world?", "新しいワールドをおすすめします。長く遊んでいる世界でも、今の章から始まります。", "A new world is recommended. In a long-running world the mod simply starts from the current chapter."),
        ("世界を開いた直後が重いです。", "The game is heavy right after opening the world.", "巨神の背中の島を裏で少しずつ建てています。数分で落ち着きます。そのあいだに岩鯨の背に乗ろうとすると、一瞬止まることがあります。", "The titan back islands are built in the background. It settles in a few minutes; boarding the Stone Whale in the meantime may freeze briefly."),
        ("ピースフルで遊べますか。", "Can I play on Peaceful?", "いいえ。ピースフルでは巨神の背中の試練が眠ったままです。難易度を上げてください。", "No. On Peaceful the back trials stay dormant; raise the difficulty."),
        ("怖い演出を減らせますか。", "Can I reduce the scary effects?", "設定ファイルの <code>horrorEventFrequency</code>、<code>watcherFrequency</code>、<code>cloudEyeFrequency</code>（0 で出なくなる）と、クライアント設定で、揺れや暗さを弱められます。", "Lower <code>horrorEventFrequency</code>, <code>watcherFrequency</code> and <code>cloudEyeFrequency</code> in the config (0 disables them); screen shake and darkness can be reduced in client settings."),
        ("キーがほかの mod とぶつかります。", "Keys clash with another mod.", "設定の「操作」にある「天と淵のあいだ」の項目で変えられます。", "Rebind them under Controls in the \"Between the Sky and the Abyss\" category."),
        ("大事な品をなくしました。", "I lost a key item.", "<kbd>B</kbd> の控え帳を開き、品の在りかと「呼び戻す」ボタンを使ってください。", "Open the record book (<kbd>B</kbd>) to see where it is and use the Recall button."),
        ("好きな順に巨神に挑めますか。", "Can I fight the titans in any order?", "既定では順番があります。管理者がゲームルール <code>skyabyssStoryGate</code> を <code>false</code> にすると好きな順に乗れます。", "By default there is an order. An admin can set the game rule <code>skyabyssStoryGate</code> to <code>false</code> to board in any order."),
        ("他の mod と一緒に使えますか。", "Does it work with other mods?", "ほかの mod との組み合わせは確かめていません。ブロックを壊す・置く mod は背中の中では効きません。", "Combinations with other mods are untested. Mods that break or place blocks do not work inside a titan's back."),
    ]
    qa_html = "".join("<details><summary>%s</summary><p>%s</p></details>" % (T(a, b), T(c, d)) for a, b, c, d in qa)
    limits = [
        ("マルチは、まだ人の手で通しの確認をしていません（特に 2 つのパーティが同じ背中にいるとき）。", "Multiplayer has not yet been verified end to end by people (especially with two parties on one back)."),
        ("序盤の演出・予兆・仕掛けの濃さは、通しで確かめていません。", "The density of early-game staging, omens and small set pieces has not been playtested end to end."),
        ("余燼の背中は、人が通しで遊んでいません。", "Ember's back has not been played through by a person."),
        ("風見鶏と潮の真珠の絵は仮のものです。", "The art for the weathercock and the tide pearl is placeholder."),
        ("五章以降は、まだ枠だけです。", "Chapter 5 onward is only a framework so far."),
    ]
    lim = "".join("<li>%s</li>" % T(a, b) for a, b in limits)
    body = page_head("導入・FAQ", "Install and FAQ", "必要なもの、入れ方、サーバーの管理、よくある質問、既知の制限。",
                     "Requirements, installation, server administration, FAQ and known limits.") + """
<section aria-labelledby="req" id="requirements">
<div class="wrap">
<h2 id="req">%(h_req)s</h2>
%(req)s
</div>
</section>

<section class="alt" aria-labelledby="inst" id="install">
<div class="wrap">
<h2 id="inst">%(h_inst)s</h2>
<ol class="steps">
<li>%(i1)s</li><li>%(i2)s</li><li>%(i3)s</li><li>%(i4)s</li><li>%(i5)s</li>
</ol>
<div class="note warn"><strong>%(norel)s</strong></div>
<div class="note">%(dl)s</div>
<p><a class="btn" href="%(repo)s/releases">%(btn)s</a> <a class="btn ghost" style="color:var(--ink);background:var(--surface);border-color:var(--edge)" href="%(repo)s">%(btn2)s</a></p>
</div>
</section>

<section aria-labelledby="srvh" id="server">
<div class="wrap">
<h2 id="srvh">%(h_srv)s</h2>
<p>%(srv)s</p>
<h3>%(h_gr)s</h3>
%(gr)s
<h3>%(h_cmd)s</h3>
%(cmds)s
<p>%(cfg)s <a href="https://github.com/Nishi-Yura/skyabyss/blob/main/docs/playtest_commands.md">docs/playtest_commands.md</a></p>
</div>
</section>

<section class="alt" aria-labelledby="faq" id="faq">
<div class="wrap">
<h2 id="faqh">%(h_faq)s</h2>
%(qa)s
</div>
</section>

<section aria-labelledby="lim" id="limits">
<div class="wrap">
<h2 id="lim">%(h_lim)s</h2>
<p>%(lim_lead)s</p>
<ul>%(lim)s</ul>
<p>%(lim_end)s</p>
</div>
</section>
""" % dict(
        h_req=T("必要なもの", "Requirements"), req=req, repo=REPO,
        h_inst=T("導入の手順", "Installation"),
        i1=T("NeoForge %s を入れて、一度起動します。" % MC, "Install NeoForge %s and launch it once." % MC),
        i2=T("<code>skyabyss-%s.jar</code> と GeckoLib の jar を、どちらも <code>mods</code> フォルダへ入れます。" % VERSION, "Put <code>skyabyss-%s.jar</code> and the GeckoLib jar into the <code>mods</code> folder." % VERSION),
        i3=T("サーバーで遊ぶときは、サーバーの <code>mods</code> にも同じものを入れます。みんなで同じ jar を使います。", "For a server, put the same files into the server's <code>mods</code> folder. Everyone must use the same jar."),
        i4=T("新しいワールドを作ります。難易度はピースフル以外にします。", "Create a new world on any difficulty except Peaceful."),
        i5=T("目覚めたら、画面の左上の目的の帯に従って進みます。", "When you wake, follow the objective strip at the top left."),
        dl=T("jar は開発者から受け取るか、ソースから <code>./gradlew build</code> で作ると <code>build/libs/</code> に出ます。", "Get the jar from the developer, or build it from source with <code>./gradlew build</code>; it appears in <code>build/libs/</code>."),
        norel=T("まだ公開リリースはありません。今は、ソースから <code>./gradlew build</code> で jar を作って使います（下のリリースのページは、公開されたときの置き場です）。", "There is no public release yet. For now, build the jar from source with <code>./gradlew build</code> (the Releases page below is where it will appear once published)."),
        btn=T("リリースのページ", "Releases page"), btn2=T("ソース（GitHub）", "Source (GitHub)"),
        h_srv=T("サーバーの管理者向け", "For server administrators"),
        srv=T("<code>/gamerule</code> で決まりを変えられます。巨神の背中は専用のディメンションなので、ワールドのバックアップは通常どおりで構いません。", "Rules can be changed with <code>/gamerule</code>. Titan backs live in dedicated dimensions, so normal world backups work as usual."),
        h_gr=T("ゲームルール", "Game rules"), gr=gr, h_cmd=T("OP 用のコマンド（抜粋）", "Operator commands (excerpt)"), cmds=cmds,
        cfg=T("設定ファイルで、不穏な出来事の頻度（<code>horrorEventFrequency</code> など）や知らせの見せ方を変えられます。試遊用コマンドの全部は", "The config file adjusts omen frequency (<code>horrorEventFrequency</code> and others) and notice display. All playtest commands are in"),
        h_faq=T("よくある質問", "FAQ"), qa=qa_html,
        h_lim=T("既知の制限", "Known limits"),
        lim_lead=T("これはベータ版です。作ったばかりで、人が通しで遊んでいない所があります。", "This is a beta. Some parts are brand new and have not been played through by people."),
        lim=lim,
        lim_end=T("噛み合わない所や、おかしな動きを見つけたら、ぜひ教えてください。開いた直後の数分は、背中の島を建てるので少し重くなります。", "If you find something that does not fit or behaves oddly, please tell us. The first few minutes after opening a world are a little heavy while the back islands are built."),
    )
    # faq 見出しの id を整える
    body = body.replace('aria-labelledby="faq" id="faq"', 'aria-labelledby="faqh" id="faq"')
    page("faq.html", "導入・FAQ — 天と淵のあいだ", "Install and FAQ — Between the Sky and the Abyss",
         "必要なもの、導入の手順、サーバー管理者向けのゲームルール、よくある質問、既知の制限。",
         "Requirements, installation steps, game rules for server admins, FAQ and known limits.", body)


def changelog():
    ver = [
        (T("0.2.2 以降（開発中・リリース前）", "After 0.2.2 (in development, unreleased)"), "2026-10-20", [
            ("転生（Lv 50+n）を追加。魂盤・3 択の恩恵・祖の力・色と記号・称号 24・順位の石・飾りがあります。深淵の難度と、巨神ごとの専用伝説 4 つも加わりました。", "Added rebirth (Lv 50+n) with the soul board, three-choice boons, ancestral power, colors and symbols, 24 titles, a rank stone and cosmetics. Abyss difficulty and four dedicated abyss legendaries arrive with it."),
            ("スキルの自由度を追加: 木は全部最初から開き、天職は専門に。タグ・セット・連鎖・構え・熟練・キーストーン・試着・型 4 つ。変異・橋・紋の盤・前世の技・淵の木も。", "Added skill freedom: every tree open from the start, callings as specialties, tags, sets, chains, stances, mastery, keystones, try-on and four loadouts, plus mutations, bridges, the glyph board, past-life skills and the Abyss tree."),
            ("装備の自由度を追加: 穴・欠片・技のルーン・アスペクト・世代と、炉の「刻み」画面。", "Added gear freedom: sockets, shards, skill runes, aspects, generations and the forge's Engrave tab."),
            ("試練のプールが各巨神 7 つ以上（原型 3 と新作 4 以上）になりました。巨神の背を大きく作り直しました（水平 ×1.6）。", "The trial pool now holds at least seven trials per titan (three originals plus four or more new), and the backs were rebuilt larger (1.6x horizontally)."),
            ("心臓の領域が変わりました。時間では終わらず、三つの仕掛け（共鳴する骨・潮の水門・避雷の柱・熾の核）を解くまで続き、そのあいだ心臓は傷を受けません。", "Heart domains changed: they no longer time out and last until three gimmicks (resonant bones, tide valves, lightning rods, ember cores) are solved; the heart is invulnerable meanwhile."),
            ("倒れた仲間を起こせるようになりました（約 15 秒の「倒れた」状態、3 秒の長押しで蘇生）。", "Party members can now revive a downed ally (about 15 s downed state, 3 s hold to revive)."),
            ("試練のプール（巨神ごとに十二、周回ごとに歩・智・闘から一つずつ）、試練の板・図鑑・称号・今週の試練を追加。試練を越えると鼓動の間へ戻り、碑が光ります。", "Added the trial pool (twelve per titan, one drawn each cycle per Traverse/Wit/Combat slot), the trial board, codex, titles and weekly trial. Clearing a trial returns you to the hub and lights its tablet."),
            ("巨神の子が運ぶ旅を追加（笛で子を呼び、パーティ全員で乗って海図から行き先を選ぶ）。成体はもう動きません。", "Added travel by young carriers (summon with a whistle, ride as a party, choose a destination from a chart); adult titans no longer move."),
            ("5 つの木と旅人の 36 の技、天職、刻印、要、型、伝説の書き換え、ステータスの刻み目を追加。", "Added 36 skills across five trees and the Wayfarer, callings, runes, keystones, weapon types, legendary rewrites and stat breakpoints."),
        ]),
        ("0.2.2", "2026-10-19", [
            ("岩鯨の背を大きくしました（広間・心臓の間・水路）。", "Enlarged the Stone Whale's back (halls, heart chamber, channel)."),
            ("ボスの技の予告を床に出すようにしました（橙・青白の破線・灰）。", "Boss attacks are now telegraphed on the floor (orange, pale dashed, gray)."),
            ("備えの三灯、戦いの復帰（20 秒・3 回まで）、巨神の停泊 20 分を追加。", "Added the three readiness lamps, fight re-entry (20 s, 3 times) and the 20-minute titan stay."),
            ("旅嚢（キー I、長押しの輪 Y）、心臓の遺物の選択画面、作業台が旅嚢の素材を使う仕組みを追加。", "Added the Travel Satchel (key I, hold-wheel Y), the heart relic selection screen and crafting from satchel materials."),
            ("墓の置き方、パーティの仲間の札と矢印、背中の詰みの監査と修正。", "Reworked graves, added party tags and arrows, and audited and fixed dead-ends on the backs."),
        ]),
        ("0.2.1", "2026-10-18", [
            ("後から入った人に序盤や場面が出ない不具合を直しました。", "Fixed the opening and cutscenes not showing for players who join later."),
            ("みんなで遊ぶときは、全員が同じ jar を使います。", "When playing together, everyone must use the same jar."),
        ]),
        ("0.2.0", "2026-10-18", [
            ("章に分かれました（題字の札、章の終わりの札、各章の小さな仕掛けと予兆）。", "Split the story into chapters (title cards, end cards, small set pieces and omens)."),
            ("潮見の貝、地上で倒れたときの墓、装飾の枠、READMEとドット絵の刷新。", "Added the Tide-watching Shell, graves on ground deaths, accessory slots, and refreshed the README and pixel art."),
        ]),
    ]
    items = "".join('<li><h3>%s</h3><p class="date">%s</p><ul>%s</ul></li>' % (v if v.startswith("<") else "v" + v, d, "".join("<li>%s</li>" % T(a, b) for a, b in notes)) for v, d, notes in ver)
    body = page_head("更新履歴", "Changelog", "ベータ版の主な変更です。", "Main changes in the beta versions.") + """
<section aria-labelledby="log">
<div class="wrap">
<h2 id="log" style="position:absolute;left:-999px">%s</h2>
<ol class="log">%s</ol>
<p>%s <a href="%s/commits/main">GitHub</a></p>
</div>
</section>
""" % (T("更新履歴", "Changelog"), items, T("細かい変更の一覧は", "The full list of changes is on"), REPO)
    page("changelog.html", "更新履歴 — 天と淵のあいだ", "Changelog — Between the Sky and the Abyss", "ベータ版 0.2.0 から 0.2.2 までの主な変更。", "Main changes from beta 0.2.0 to 0.2.2.", body)


# ---------------------------------------------------------------- 別案
def alt_b():
    scenes = "".join('<figure>%s<figcaption>%s</figcaption></figure>' % (
        pimg("img/scene_%s.png" % t["k"], t["ja"], t["en"], 64, 40), T(t["ja"], t["en"])) for t in TITANS)
    body = """
<section class="hero" aria-labelledby="hero-title">
<div class="wrap">
<p class="sub">BETWEEN THE SKY AND THE ABYSS　%(note)s</p>
<h1 id="hero-title">天と淵のあいだ</h1>
<p class="pitch">%(pitch)s</p>
<div class="ctas"><a class="btn" href="faq.html#install">%(c1)s</a><a class="btn ghost" style="color:var(--ink);background:var(--surface);border-color:var(--edge)" href="play.html">%(c2)s</a></div>
<p style="margin-top:1.4em">%(plate)s</p>
</div>
</section>
<section>
<div class="wrap">
<div class="note-sheet">
<h2>%(h1)s</h2>
<p>%(t1)s</p>
<h2>%(h2)s</h2>
<p>%(t2)s</p>
<h2>%(h3)s</h2>
<p>%(t3)s</p>
</div>
</div>
</section>
<section class="alt" aria-labelledby="sc">
<div class="wrap">
<h2 id="sc">%(h_sc)s</h2>
<div class="scenes">%(scenes)s</div>
</div>
</section>
<section>
<div class="wrap">
<p><a href="index.html">%(back)s</a>　<a href="alt_c.html">%(c_alt)s</a></p>
</div>
</section>
""" % dict(
        note=T("別案 B（紙の手帳）", "Alt B (paper notebook)"),
        plate=pimg("img/plate_title.png", "天と淵のあいだ BETWEEN THE SKY AND THE ABYSS 空を追う人の旅の手帳", "Between the Sky and the Abyss: the notebook of someone chasing the sky", 840, 528, "plate px", True),
        pitch=T("空を泳ぐ巨神の背に乗り、世界の底の謎へ。建築も採掘も、いつものままに。", "Climb onto titans that swim through sky, sea, storm and earth. Build and mine as you always do."),
        c1=T("導入する", "Install"), c2=T("遊び方", "How to play"),
        h1=T("旅の手帳より　一", "From the notebook: one"), t1=T("巨神は四体。空を泳ぐもの、海を歩くもの、嵐をまとうもの、地を泳ぐもの。その背は、そのままダンジョンです。", "Four titans: one swims the sky, one walks the sea, one wears the storm, one swims the earth. Their backs are the dungeons."),
        h2=T("二", "Two"), t2=T("5 つの木と旅人、36 の技。天職を選び、刻印で技の形を変え、武器と噛み合わせます。", "Five trees plus the Wayfarer, 36 skills. Pick a calling, reshape skills with runes and match them to your weapon."),
        h3=T("三", "Three"), t3=T("6 人までのパーティ。倒れても大事な品は残ります。チャットは流れず、画面の決まった場所に知らせが出ます。", "Parties of up to six. Key items survive death. No chat spam; notices appear in fixed places on screen."),
        h_sc=T("四つの背", "Four backs"), scenes=scenes,
        back=T("A（本番）へ", "To A (main)"), c_alt=T("C を見る", "See C"),
    )
    page("alt_b.html", "別案 B 紙の手帳 — 天と淵のあいだ", "Alt B paper notebook — Between the Sky and the Abyss", "トップページの別案 B。", "Alternative top page B.", body, "alt-b")


def alt_c():
    idx = [("titans.html", "01", "巨神と背中", "Titans", "四体の巨神と背中の試練", "Four titans and their trials"),
           ("skills.html", "02", "スキルとビルド", "Skills", "36 の技、天職、刻印", "36 skills, callings, runes"),
           ("gear.html", "03", "装備", "Gear", "13 種の武器と共鳴の炉", "13 weapons and the forge"),
           ("pouch.html", "04", "旅嚢と道具", "Pouch", "専用の持ち物", "A dedicated inventory"),
           ("party.html", "05", "パーティとマルチ", "Party", "6 人まで", "Up to six"),
           ("play.html", "06", "遊び方", "How to play", "最初の 1 時間と操作", "First hour and controls"),
           ("faq.html", "07", "導入・FAQ", "Install / FAQ", "必要なものと入れ方", "Requirements and install")]
    index = "".join('<a href="%s"><b>%s</b><div>%s<span>%s</span></div></a>' % (h, n, T(a, b), T(c, d)) for h, n, a, b, c, d in idx)
    body = """
<section class="hero" aria-labelledby="hero-title">
<div class="wrap">
<p class="sub">BETWEEN THE SKY AND THE ABYSS　%(note)s</p>
<h1 id="hero-title">天と淵のあいだ</h1>
<p class="pitch">%(pitch)s</p>
<div class="ctas" style="margin-top:1em"><a class="btn" href="faq.html#install">%(c1)s</a></div>
</div>
</section>
<div class="wrap"><picture><source srcset="img/hero_night.png" media="(prefers-color-scheme: dark)"><img class="px" style="display:block;width:100%%;border:3px solid var(--edge)" src="img/hero_day.png" width="960" height="384" alt="%(hero_alt)s" data-alt-ja="%(hero_alt)s" data-alt-en="%(hero_alt_en)s"></picture></div>
<section>
<div class="wrap">
<div class="split">
<div class="sky"><h2>%(sky)s</h2><p>%(skyt)s</p></div>
<div class="abyss"><h2>%(aby)s</h2><p>%(abyt)s</p></div>
</div>
</div>
</section>
<section class="alt" aria-labelledby="idx">
<div class="wrap">
<h2 id="idx">%(h_idx)s</h2>
<div class="index">%(index)s</div>
</div>
</section>
<section>
<div class="wrap">
<p><a href="index.html">%(back)s</a>　<a href="alt_b.html">%(b_alt)s</a></p>
</div>
</section>
""" % dict(
        note=T("別案 C（索引）", "Alt C (index)"),
        hero_alt="雲の海の上を泳ぐ鯨と、雲の裂け目の底に光る熾の絵", hero_alt_en="Pixel art of a whale over a sea of clouds and a glowing ember at the bottom of a rift",
        pitch=T("空を泳ぐ巨神の背に乗り、世界の底の謎へ。建築も採掘も、いつものままに。", "Climb onto titans that swim through sky, sea, storm and earth. Build and mine as you always do."),
        c1=T("導入する", "Install"),
        sky=T("天", "Sky"), skyt=T("四体の巨神が、空・海・嵐・地の中を行きます。背に乗れば、そこが試練の場です。", "Four titans move through sky, sea, storm and earth. Climb aboard and the back is your trial ground."),
        aby=T("淵", "Abyss"), abyt=T("体の中の試練、心臓、そのさらに奥に何かがあります。眠りについた巨神の下で、旅は続きます。", "Trials inside the body, the heart, and something deeper still. The journey continues beneath the sleeping titans."),
        h_idx=T("案内", "Contents"), index=index,
        back=T("A（本番）へ", "To A (main)"), b_alt=T("B を見る", "See B"),
    )
    page("alt_c.html", "別案 C 索引 — 天と淵のあいだ", "Alt C index — Between the Sky and the Abyss", "トップページの別案 C。", "Alternative top page C.", body, "alt-c")
