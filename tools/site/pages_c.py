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

<section class="alt" aria-labelledby="story5">
<div class="wrap">
<h2 id="story5">%(h5)s</h2>
%(plate5)s
<p class="quote"><span>%(q5a)s</span><span>%(q5b)s</span></p>
<p>%(t5)s</p>
</div>
</section>
""" % dict(
        h=T("物語", "Story"),
        h5=T("五章　深淵", "Chapter 5: The Abyss"),
        plate5=pimg("img/scene_abyss.png", "縦穴の灯籠のドット絵", "Pixel art of a lantern in the abyss shaft", 256, 160, "plate px"),
        q5a=T("四つを返した。それでも、まだ足りないのだという。", "Four were returned. Still, they say, it is not enough."),
        q5b=T("底へ降りるほど、あなたより前の誰かの声が近くなる。", "The deeper you go, the closer a voice from someone who came before you."),
        t5=T("都の地下に、閉じられた戸があります。その先は、地の底の深淵です。語り部は、そこへ降りた人のことを、多くは語りません。",
             "Beneath the capital is a sealed door. Beyond it lies the abyss, deep under the earth. The storyteller says little about those who went down."),
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
        ("五章「深淵」は、まだ人の手で通しに遊ばれていません（特に番人の戦いと、クリア後の深みを）。", "Chapter 5, the abyss, has not yet been played through end to end by people (especially the warden's fight and the depths after the clear)."),
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
<h3>%(h_tst)s</h3>
<p>%(tst)s <a href="https://github.com/Nishi-Yura/skyabyss/blob/main/docs/design/37_playtest_commands.md">docs/design/37_playtest_commands.md</a></p>
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
        h_tst=T("試遊する人へ", "For testers"),
        tst=T("試遊の人は、OP で <code>/skyabyss play help</code> と打つと、章・場所・道具・ボスを移す一覧が出ます（例: <code>/skyabyss play chapter 5</code> で深淵の章の始めへ）。",
              "Testers with op can type <code>/skyabyss play help</code> for the list of chapter, place, kit and boss commands (e.g. <code>/skyabyss play chapter 5</code> starts the abyss chapter)."),
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
        ("0.5.0", "2026-10-23", [
            ("五章「深淵」を追加。都の地下の戸から大坑道を下り、骨・塩・雷・灰の四つの層を渡って、心臓の番人に挑みます。", "Added chapter 5, the abyss: down the great shaft under the capital, through four layers (bone, salt, storm, ash), to the warden of the heart."),
            ("深淵術（光・重さ・鼓動）と、深淵術師の木を追加。", "Added the three abyss arts (light, weight, heartbeat) and the Abyss Caster tree."),
            ("クリアのあとに、番人の悪夢（段 1〜10）、前の自分たちの遺品 12 種（セットの効果つき）、心臓の下の果てない深み（区画 8 種・仕掛け 8 種）を追加。", "After the clear: the warden's nightmare (tiers 1 to 10), twelve past-self relics with set bonuses, and endless depths below the heart (8 section kinds, 8 gimmicks)."),
            ("深淵の敵 18 種を追加。深く潜るほど、同じ敵の変わり果てた姿が出ます。", "Added 18 abyss enemies. The deeper you go, the more twisted forms of the same foes appear."),
            ("画面にゆとり。巻物のパネル、技の木のドラッグとズーム、画面の大きさに合わせた表示。", "Roomier screens: scroll panels, drag and zoom on the skill tree, and layouts scaled to the window."),
            ("HUD の描画を軽くしました。", "Lighter HUD drawing."),
            ("1〜5 章の文章を見直し、用語をそろえました（灯りは「灯り」、灰被りは「灰の番人」、目的にイサナの名）。", "Reviewed the text of chapters 1 to 5 and made the terms consistent (灯り for lights, 灰の番人 for the ash warden, Isana named in the objectives)."),
            ("試遊用のコマンド <code>/skyabyss play</code>（OP 用）を追加。", "Added the <code>/skyabyss play</code> tester commands (op only)."),
        ]),
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
    page("changelog.html", "更新履歴 — 天と淵のあいだ", "Changelog — Between the Sky and the Abyss", "ベータ版の主な変更。最新は 0.5.0。", "Main changes in the beta. Latest: 0.5.0.", body)

