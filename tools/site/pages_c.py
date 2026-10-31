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
        plate5=pimg("img/scene_abyss.png", "縦穴の灯りのドット絵", "Pixel art of a lantern in the abyss shaft", 256, 160, "plate px"),
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
        ["<code>/skyabyss play help</code>", T("試遊用の一覧（章・場所・道具・ボス・部屋・頼み・状態）", "Tester commands: chapter, place, kit, boss, room, quest, state")],
        ["<code>/skyabyss play chapter &lt;番号&gt;</code>", T("その章の始めへ（例: <code>5</code> で深淵）", "Jump to the start of a chapter (e.g. <code>5</code> for the abyss)")],
        ["<code>/skyabyss play raid &lt;巨神&gt; &lt;部屋&gt;</code>", T("背の順路の部屋へ運ぶ", "Jump to a back room")],
        ["<code>/skyabyss play now</code> / <code>next</code>", T("今の目的の出来事を起こす／今の目的を済ませて次へ", "Trigger the current objective's event / complete it and move on")],
        ["<code>/skyabyss inspect …</code>", T("今の状態を読む（進み・頼み・持ち物）", "Read the current state (progress, quests, items)")],
    ])
    qa = [
        ("どの Minecraft で遊べますか。", "Which Minecraft versions work?", "Java 版 %s と NeoForge %s の組み合わせだけです。Forge や Fabric、統合版では動きません。" % (MC, MC),
         "Only Java Edition %s with NeoForge %s. Forge, Fabric and Bedrock are not supported." % (MC, MC)),
        ("既存のワールドで遊べますか。", "Can I use an existing world?", "いいえ。世界の種類は「天と淵の大陸」だけで、はじまりの島から始まります。新しいワールドを作ってください（専用サーバーは <code>server.properties</code> に <code>level-type=skyabyss\\:continent</code>）。", "No. The only world type is the continent, starting on the island. Create a new world (on a dedicated server set <code>level-type=skyabyss\\:continent</code> in <code>server.properties</code>)."),
        ("世界を開いた直後が重いです。", "The game is heavy right after opening the world.", "巨神の背中の島を裏で少しずつ建てています。数分で落ち着きます。そのあいだに岩鯨の背に乗ろうとすると、一瞬止まることがあります。", "The titan back islands are built in the background. It settles in a few minutes; boarding the Stone Whale in the meantime may freeze briefly."),
        ("ピースフルで遊べますか。", "Can I play on Peaceful?", "いいえ。ピースフルでは巨神の背の部屋が眠ったままです。難易度を上げてください。", "No. On Peaceful the back rooms stay dormant; raise the difficulty."),
        ("怖い演出を減らせますか。", "Can I reduce the scary effects?", "設定ファイルの <code>horrorEventFrequency</code>、<code>watcherFrequency</code>、<code>cloudEyeFrequency</code>（0 で出なくなる）と、クライアント設定で、揺れや暗さを弱められます。", "Lower <code>horrorEventFrequency</code>, <code>watcherFrequency</code> and <code>cloudEyeFrequency</code> in the config (0 disables them); screen shake and darkness can be reduced in client settings."),
        ("キーがほかの mod とぶつかります。", "Keys clash with another mod.", "設定の「操作」にある「天と淵のあいだ」の項目で変えられます。", "Rebind them under Controls in the \"Between the Sky and the Abyss\" category."),
        ("大事な品をなくしました。", "I lost a key item.", "<kbd>B</kbd> の控え帳を開き、品の在りかと「呼び戻す」ボタンを使ってください。", "Open the record book (<kbd>B</kbd>) to see where it is and use the Recall button."),
        ("好きな順に巨神に挑めますか。", "Can I fight the titans in any order?", "既定では順番があります。管理者がゲームルール <code>skyabyssStoryGate</code> を <code>false</code> にすると好きな順に乗れます。", "By default there is an order. An admin can set the game rule <code>skyabyssStoryGate</code> to <code>false</code> to board in any order."),
        ("他の mod と一緒に使えますか。", "Does it work with other mods?", "ほかの mod との組み合わせは確かめていません。ブロックを壊す・置く・作る mod は、アドベンチャーの決まりで効きません。", "Combinations with other mods are untested. Mods that break, place or craft blocks are blocked by the adventure rules."),
        ("建築や採掘はできますか。", "Can I build or mine?", "できません。この mod は、Minecraft の見た目をした RPG です。アドベンチャーモードで、採集は道具ごとの決まった場所だけです。", "No. This mod is an RPG with Minecraft's look: adventure mode, with gathering only at set spots per tool."),
    ]
    qa_html = "".join("<details><summary>%s</summary><p>%s</p></details>" % (T(a, b), T(c, d)) for a, b, c, d in qa)
    limits = [
        ("序章から終章「雲海」の結末まで遊べます。終章は、人が通しで遊んでいる途中です（島の主・座の三形態・巡礼の試しの難しさ、予告の見やすさ、四人の術の分かりやすさを教えてください）。", "You can play from the prologue to the ending of the final chapter, the sea of clouds. The final chapter is still being playtested: tell us about the island lords, the three forms of the seat, the difficulty of the pilgrim's trial, how readable the telegraphs are and whether the four selves' arts are clear."),
        ("章の小さな仕掛けと予兆、雷翼・余燼の背の通し、二人以上での潮殻の背は、人の手で通して確かめていません。", "Chapter side elements and omens, full runs of the Thunder Wing and Ember backs, and the Tide Shell back with two or more players have not been verified end to end by people."),
        ("マルチは、まだ人の手で通しの確認をしていません（特に 2 つのパーティが同じ背中にいるとき）。", "Multiplayer has not yet been verified end to end by people (especially with two parties on one back)."),
        ("潮の真珠と潮鳴り石の絵は仮のものです。", "The tide pearl and tide stone icons are placeholders."),
    ]
    lim = "".join("<li>%s</li>" % T(a, b) for a, b in limits)
    body = page_head("導入・FAQ", "Install and FAQ", "必要なもの、入れ方、サーバーの管理、よくある質問、既知の制限。",
                     "Requirements, installation, server administration, FAQ and known limits.") + toc([("requirements", "必要なもの", "Requirements"), ("install", "導入の手順", "Installation"), ("server", "サーバーの管理者向け", "For server administrators"), ("faq", "よくある質問", "FAQ"), ("limits", "既知の制限", "Known limits")]) + """
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
        i4=T("新しいワールドを作ります（世界の種類は「天と淵の大陸」だけです）。難易度はピースフル以外にします。", "Create a new world (the only world type is the continent) on any difficulty except Peaceful."),
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
        lim_end=T("噛み合わない所や、おかしな動きを見つけたら、ぜひ教えてください。開いた直後の数分は、巨神の背の島を建てるので少し重くなります。", "If you find something that does not fit or behaves oddly, please tell us. The first few minutes after opening a world are a little heavy while the back islands are built."),
    )
    # faq 見出しの id を整える
    body = body.replace('aria-labelledby="faq" id="faq"', 'aria-labelledby="faqh" id="faq"')
    page("faq.html", "導入・FAQ — 天と淵のあいだ", "Install and FAQ — Between the Sky and the Abyss",
         "必要なもの、導入の手順、サーバー管理者向けのゲームルール、よくある質問、既知の制限。",
         "Requirements, installation steps, game rules for server admins, FAQ and known limits.", body)


def changelog():
    # 版ごとの主な変更（試遊の確かめ順と docs/review の記録から）。古い版は要点だけ残す。
    ver = [
        ("v0.9.20", "2026-10-31", [
            ("はじまりの島は、遊び方を教える場所だけに（職・帳面・足跡・峠の野盗・休み場・祠・渡し守）。見張り塔の跡・大樹などの寄り道は消しました。木の蜂の巣が鳴りやまない音も止めました。", "The starting island now holds only the places that teach the game (job, journal, footprints, the pass bandits, rest spot, shrine, ferryman). Side trips such as the old watchtower and the great tree are gone, and beehives no longer buzz nonstop."),
            ("町の人を見直しました: 書庫番と地図屋はいなくなり、祠と跡地は最初から地図に、跡地の噂は都の案内人に。店は冒険モードで置けないブロックを売らず、同じ品は一つの店に。封切り師は町の案内の一覧に載り、町ごとに挨拶が違います。", "Townsfolk review: the archivist and the mapmaker are gone; shrines and ruins are on the map from the start, and the capital guide tells ruin rumors. Shops no longer sell blocks you cannot place in adventure mode, and each good has one shop. The box opener is in the town guide list and greets you differently in each town."),
            ("レベルが上がったときは技の点の数を知らせます（残っていた「振り分けられる力」の文を消しました）。ヒロイックの防具 4 式に外套・光る継ぎ目・頭の飾りを足しました。", "Level-ups now report skill points (the leftover stat-point text is gone). The four heroic armor sets gain capes, glowing seams and crown ornaments."),
        ]),
        ("v0.9.19", "2026-10-31", [
            ("野の装備: 物語の報酬とは別に、土地の敵・名のある強敵・層の主から出る武器 102 本（職ごと・Lv 帯ごと）、防具 18 式、装飾と護符 26 品。どれも自分の形と能力を持ちます。アンコモンは鍛冶屋でも買えます。", "Wild gear: beyond story rewards, 102 job weapons by level band, 18 armor sets and 26 trinkets and charms drop from regional foes, named foes and layer lords, each with its own model and ability. Uncommon pieces are also sold by the smith."),
            ("装備は封の箱で落ちます: 巨神の箱 4 種、階級の箱（Lv の帯ごと）、淵の金の箱。同じ箱は重なり、旅嚢の新しいつまみ「封の箱」に入ります。都と四つの町の封切り師がルーレットで開けてくれます（無料）。", "Gear now drops in sealed boxes: four titan boxes, rarity boxes per level band, and the abyssal gold box. Boxes of a kind stack and go to the new Sealed Boxes tab of the satchel. The box opener in the capital and the four towns opens them with a roulette (free)."),
        ]),
        ("v0.9.18", "2026-10-31", [
            ("全体監査の直し: 看板のはみ出し（日英）、一章の地上の敵の足音、転生の墓の札のログ、書類とサイトを今の版に。", "Audit fixes: sign lines that overflowed (ja/en), footstep sounds of the chapter 1 ground foes, the rebirth grave label log line, and docs and site brought up to date."),
            ("終章「雲海」。月の核のあと語り部に話すと「雲の上へ」が始まり、四体の巨神に運ばれて雲の上の海へ。鐘の島・灯の島・炉の島・約束の島の四つを光の橋で渡り、巡礼者の座で雲上の巡礼者と戦います（手 → 目 → 月の三形態。第三形態では前の自分たちが加勢）。", "The final chapter, the sea of clouds: after the moon's core, speak to the storyteller to begin \"Above the Clouds\". The four titans carry you up; cross the Isle of Bells, the Isle of the Lamp, the Isle of the Forge and the Isle of the Promise over bridges of light, and face the Pilgrim above the Clouds at the Pilgrim's seat (hand, eye, moon; your former selves join in the third form)."),
            ("結末は三つ（鎮める・壊す・受け入れる）。結末ごとに地上の空と都が変わり、座に触れると選び直せます。神器は職ごとに一つ（雲上の心核の欠片で強化）。クリア後は巡礼の試し（段 1〜5）。", "Three endings (calm, break, accept). Each changes the sky and the capital, and you can choose again at the seat. One divine relic per job, strengthened with shards of the cloud core. After the clear: the Pilgrim's Trial, tiers 1 to 5."),
            ("六章「月」が全部入りました: 新月の谷・上弦の崖・満月の野・下弦の裂け目、月の核、月の装備（職ごとにレアとヒロイック）、位相の挑戦。", "Chapter 6, the Moon, is complete: the New Moon Valley, the Waxing Cliffs, the Full Moon Field, the Waning Rift, the moon's core, moon gear (a rare and a heroic per job) and the phase trials."),
            ("技の木 v2: 職ごとの Wynncraft 型の木。使う技は 4 本まで、基礎は木の節で上がります。職は町の職の間で替えます。ステータス画面は見るだけになりました。", "Skill tree v2: a Wynncraft-style tree per job. Up to four active skills; stats come from tree nodes. Change jobs at the town's job hall. The status screen is now read-only."),
            ("BGM 17 曲（場所と戦いごと）。技の木の節は種類ごとに大きさが違います（使う技は金の縁）。コマンドの空白は全角でも通ります。", "17 BGM tracks for places and fights. Skill tree nodes are sized by kind (active skills have a gold border). Commands accept full-width spaces."),
        ]),
        ("v0.8.1", "2026-10-28", [
            ("巨神は四体とも決まった居場所で休み続けます（人のそばへは来ず、20 分で去りもしません）。体の下の地面は地形で下げてあり、巨神がブロックを壊すことはありません。", "All four titans now rest at fixed lairs: they no longer come to you or leave after 20 minutes, and the ground under them is lowered by terrain generation, so they never break blocks."),
            ("心臓を止めると、土地の町を出た道ばたに祠が建ちます。周回はそこから挑みます。", "Stopping a heart raises a shrine beside the road just outside the regional town; repeat runs start there."),
            ("戦いの手ごたえ: 主と名のある敵は構えていて硬く、技を出し終えた直後の 3 秒だけ大きく削れます。殴っても下がりません。ふつうの敵は連打されると身構えます。戦いの中の食事は 20 秒に一つ。", "Combat feel: bosses and named foes guard hard and open up for 3 seconds right after a move; they no longer get knocked back. Ordinary foes brace when spammed. Eating mid-fight is limited to one bite per 20 seconds."),
            ("巨神ごとに職の武器のヒロイック 17 本。心臓を止めるたびに一本ずつ低い確率で落ち、それぞれ技の書き換えを一つ持ちます。心臓の報酬を選ぶ画面はなくなりました。", "17 heroic job weapons across the titans, each with one skill rewrite, dropping at a low chance whenever a heart is stopped. The heart reward choice screen is gone."),
            ("潮殻・雷翼・余燼の心臓の間が、鯨の間と同じ大広間になりました（深い谷と高い円天井）。", "The Tide Shell, Thunder Wing and Ember heart chambers are now great halls like the whale's, with a deep chasm and a high dome."),
            ("試練の部屋に時間切れ（歩・智 5 分、闘 6 分、全滅ごとに +1 分）。歩・智の部屋に邪魔の敵。同じ部屋で全滅を重ねると敵が弱まります。", "Trial rooms now have time limits (5 min for traverse and wit, 6 for combat, +1 per wipe), interfering foes in traverse and wit rooms, and foes weaken after repeated wipes in the same room."),
            ("武器を持ち替えても技の帯が消えません。章の表示は進めている頼みの章に。緑の経験値バーは出ません。月では M の地図が月の絵になります。", "The skill bar no longer vanishes when you swap weapons; the chapter label follows the quest you are on; the green XP bar is hidden; on the moon the M map shows the moon."),
            ("製品版で見えるコマンドは <code>/skyabyss play</code> と <code>/skyabyss inspect</code> だけです。", "Only <code>/skyabyss play</code> and <code>/skyabyss inspect</code> are visible in release builds."),
        ]),
        ("v0.8.0", "2026-10-28", [
            ("五章の終わりから六章「月」へ。夜に月を見上げると「近づく月」が始まり、都の大鐘楼の上の門から月の面へ渡ります。", "From the end of chapter 5 to chapter 6, the Moon: look up at the moon at night to start \"The Moon Draws Near\", then cross from the gate atop the capital's bell tower."),
            ("六章の最初の区画「新月の谷」: 谷の地形、星の灯、三つの仕掛け、月の敵と谷の主、前のループの自分との約束。谷を終えると、この版はここまでという札が出ます。", "The first district of chapter 6, the New Moon Valley: terrain, star lamps, three puzzles, moon foes and the valley's lord, and a promise with your past self. After the valley, a card says this is where the version ends."),
            ("見比べて揃えました: 一章の素材の絵、旅装、土地の町の人の体と肖像、章の小要素 39 か所、仲間と称号の画面。持ち物のクラフト枠は消しました。", "Leveled up to the best: chapter 1 material icons, travel outfits, regional townsfolk models and portraits, 39 chapter sites, the party and title screens. The crafting grid is gone from the inventory."),
            ("「踏破の時間」の目的と、週替わり「速い踏破」。組合に踏破の記録。", "A run-time objective and the weekly \"swift run\"; the guild keeps run records."),
        ]),
        ("v0.7.3", "2026-10-28", [
            ("深淵の心臓の番人を最後のボスらしく（体力 1000、技 7 つを段で解放）。層の主 4 体に技ごとの構えと打ちの動き。", "The abyss warden is now a proper final boss (1000 health, seven moves unlocked by phase). The four layer lords got per-move stances and strikes."),
            ("見どころ 24 か所を建築の水準へ建て直し、町と都の壁の外に畑・木立・小物。", "24 points of interest rebuilt to the build standard; fields, groves and props outside town and capital walls."),
            ("心臓の間の入口は戦いのあいだ閉じます。深みの順位の石を画面に。", "The heart chamber entrance seals during the fight. The depths rank stone has its own screen."),
        ]),
        ("v0.7.1", "2026-10-27", [
            ("市に四人（食料・武具・素材・道具の店）。薬師は薬だけ。名のある強敵は土地ごとの棲み家に出て、旅の地図に髑髏の印。", "Four market stalls (food, arms, materials, tools); the apothecary sells only remedies. Named foes live in one lair per region, marked with a skull on the map."),
            ("採集は道具ごと（斧・つるはし・シャベル）の決まった場所で。採った物は素材屋が買います。持ち物は倒れても残ります。", "Gathering is per tool (axe, pickaxe, shovel) at set spots; the material trader buys what you gather. Your inventory survives death."),
            ("巨神が早く休みます。頼みの報酬からバニラのがらくたを外しました。試練に入った瞬間に倒れる不具合（世界の縁）を直しました。", "Titans settle sooner. Vanilla junk removed from quest rewards. Fixed dying on entering a trial (world border)."),
        ]),
        ("v0.7.0", "2026-10-27", [
            ("伝説 20 本に技の書き換え。クリエイティブの欄を五つに分け、装備の名前を階級の色に。どの章にも 6 職の武器がそろいました。", "20 legendary weapons gained skill rewrites. Five creative tabs, gear names in tier colors, and weapons for all six jobs in every chapter."),
            ("一人称で武器が見えます。古い作りの武器 12 本を描き直しました。", "Weapons show in first person; 12 older weapon models were redrawn."),
            ("はじまりの島が広くなり、寄り道が五つ。道は町と都の門までつながり、四つの土地の町を街道と市場の並びで建て直しました。土地の町にも都と同じ施設があります。", "A bigger starting island with five side trips. Roads now reach every town and capital gate; the four regional towns were rebuilt around a high street and market, with the same facilities as the capital."),
        ]),
        ("v0.6.0", "2026-10-26", [
            ("完全な RPG へ。世界は手で描いた一枚の大陸（はじまりの島 → 渡し船 → 鐘の都 → 四つの土地の町）。世界の種類は「天と淵の大陸」だけです。", "A full RPG: the world is one hand-drawn continent (starting island, ferry, the bell capital, four regional towns). The only world type is the continent."),
            ("アドベンチャーモードで、掘らない・建てない・作らない。倒れても落とさず、装備はすり減りません。", "Adventure mode: no mining, building or crafting. Nothing drops on death and gear never wears down."),
            ("6 職（戦士・鍛冶師・魔法使い・弓使い・暗殺者・呪術師）。持った武器で職が決まり、島の祠で最初の職を選びます。旅人の木・天職・構え・連鎖・刻み目・橋の木はなくなりました。", "Six jobs (Warrior, Smith, Mage, Archer, Assassin, Shaman). The weapon in hand sets the job; the first job is chosen at the island shrine. The Wayfarer tree, callings, stances, chains, breakpoints and bridge trees are gone."),
            ("敵はこの mod の敵だけ。土地・昼夜・地形ごとの湧き。バニラの敵・動物・進捗・レシピは使いません。", "Only the mod's own enemies spawn, by region, time and terrain. Vanilla mobs, animals, advancements and recipes are not used."),
        ]),
        ("v0.5.6", "2026-10-24", [
            ("二人で順路の部屋: 部屋の外に出てしまった仲間は、3 秒で部屋の入口へ戻ります。入口では仲間が横に並んで立ちます。", "Raid rooms with friends: a party member left outside the room is brought back to its entrance after 3 seconds, and the party stands side by side at the entrance."),
            ("F1 で、この mod の表示（レベル・目的の帯・技の枠・仲間の札）も消えます。", "F1 now also hides this mod's HUD (level, objective band, skill slots, party cards)."),
            ("技の見た目: 天の驟雨は狙った先に降り、雲穿ちは矢の道に光の輪が続きます。長く続く技の光が途中で消えなくなりました。合図と淵の顎も、当たる所に出ます。", "Skill visuals: Sky Rain now falls where you aim, Cloud Pierce leaves rings of light along the arrow, long skills keep drawing to the end, and Signal and Deep Maw appear where they land."),
            ("看板の文を、新しい字の幅に収まるよう短くしました（日本語と英語の 40 行）。", "Sign texts shortened to fit the new font (40 lines in Japanese and English)."),
            ("試練の板の「図鑑」の札が、歌片の札の下に隠れていたのを直しました。", "The trial board's Codex tab is no longer hidden under the shard button."),
        ]),
        ("v0.5.5", "2026-10-24", [
            ("技の見た目: 25 の技を、光の弧・地面の輪・光の柱・残像で描き直しました。一目でどの技か分かり、夜は光ります。", "Skill visuals: all 25 skills redrawn with arcs of light, ground rings, light pillars and afterimages. Each reads at a glance and glows at night."),
            ("部屋の報い: 部屋を越えると素材と共鳴の欠片。称号は巨神ごとに 3 つ（順路・崩れなし・六部屋）。", "Room rewards: clearing rooms grants materials and resonance fragments. Three titles per titan (route, no collapse, six rooms)."),
            ("試練の板: GUI の大きさ 2 の崩れを直し、部屋の絵と類の印を出しました。説明は二行で。", "Trial board: fixed the layout at GUI scale 2, added room and kind icons, and wrapped descriptions to two lines."),
            ("知らせの札は、受け取った物を全部出します（四行まで）。", "Notice cards list everything received (up to four lines)."),
        ]),
        ("v0.5.4", "2026-10-24", [
            ("都の案内人（白い外套）に行き先を選ぶと、施設まで色の灯りが道に灯ります。", "A guide in a white cloak lights a colored path to the building you choose."),
            ("M の地図を追加。ホイールで寄る・引く、名前の札と自分の矢印。", "Added the M map: scroll to zoom, with name tags and your arrow."),
            ("夜の町の灯り（道の端の黒い樫の柱と吊りランタン、戸の前の灯り）。北東の大広場の段は一段ずつ登れるように。", "Night lighting in town (black oak posts with hanging lanterns, lights by doors). The steps in the northeast square climb one block at a time."),
            ("背のレイドの部屋（歩・智・闘）の明るさと階段の向きを直し、岩鯨の石の海の床に板目を付けました。", "Brighter raid rooms with corrected stair directions; the Stone Whale's sea-of-stone floor now shows plank seams."),
            ("都の厩舎で乗り物（7 体）を買えるように。乗り笛で呼び、もう一度で帰せます。", "Mounts (seven kinds) are sold at the capital's stable. A mount whistle calls one; use it again to send it back."),
            ("字: 本文は DotGothic16（英数も）、章の題字と場所の名前は Zen Antique。斜体をやめ、場所の副題の下に暗い帯。", "Type: body text in DotGothic16 (including Latin), chapter titles and place names in Zen Antique. No italics; place subtitles sit on a dark band."),
            ("深淵: 骨・雷の層は道を探して進み、塩・灰の層は生き残りを守って降ります。層の主の技を先に見せ、番人の戦いのやることを帯に出します。", "Abyss: bone and storm layers are explored; salt and ash layers are escorted. Layer bosses show their moves first, and the warden's fight lists what to do on the objective strip."),
            ("試遊用のコマンド /skyabyss play（OP）。/skyabyss play help で一覧を出せます。", "Tester commands /skyabyss play (op). /skyabyss play help lists them."),
            ("潜水服の鎧: 肩の板を胴に付け直しました。", "Diver armor: shoulder plates are attached to the chest again."),
            ("変異の素材は、留め袋に入った素材も数えます。", "Mutation materials now count items stored in the pouch too."),
            ("jar を軽くしました（53.7 MB → 41.7 MB）。使っていない模型と絵を外しています。", "Lighter jar (53.7 MB to 41.7 MB) with unused models and images removed."),
        ]),
        ("v0.5.3", "2026-10-24", [
            ("宿のベッドで寝ると、起きたあとは町の鐘のそばから始まります。", "Sleeping in a bed now wakes you by the town bell."),
            ("門と広場に案内人、町の施設の内装と階段、夜の町の灯りを整えました。", "Guides at the gate and square, interiors and stairs for town buildings."),
            ("敵の頭の上に体力の帯。体力の右にマナを出します。", "Health bars over enemies; mana shows right of health."),
            ("乗り物を作り直し、乗り笛で呼べるように。都の師範が技の稽古をつけます。", "Remade mounts, called with the whistle. The capital's master gives skill lessons."),
            ("巨神の背: 乗ると一つ目の部屋へ直接入ります。歩・智・闘は枠ごとに 2 部屋のどちらかが選ばれます。", "On a back you enter the first room directly. Each traverse, wit or combat slot picks one of two rooms."),
            ("心臓の攻撃の見た目を技ごとに分け、受けたときのひるみを付けました。", "Heart attacks now look different per skill and cause a flinch when they land."),
            ("深淵: 黒い鉄の器、大坑道、底の門、深淵術（右クリックで光、しゃがみ＋右クリックで鼓動、しゃがみ＋ジャンプで重さ）。帰りは白い石英の祠から。", "Abyss: black iron vessels, the great shaft, the bottom gate, abyss arts (right-click for light, crouch and right-click for heartbeat, crouch and jump for weight). Return via the white quartz shrine."),
            ("レア度を 6 段に: コモン・アンコモン・レア・エピック・ヒロイック・アーティファクト。", "Six rarity tiers: Common, Uncommon, Rare, Epic, Heroic and Artifact."),
            ("盾の調整: ふつうの敵の傷は全部止め、大物と予告の技は正面で 6 割止めます。", "Shield tuning: normal enemies' hits are fully blocked; big foes and telegraphed skills are blocked 60% from the front."),
            ("雑魚の体力を 8 割、与える傷を 9 割に。文の言い回しもそろえました。", "Ordinary mobs: 80% health and 90% damage. Wording made consistent throughout."),
        ]),
        ("v0.5.2", "2026-10-23", [
            ("敵の頭の上に体力の帯が出ます（無傷のときは薄く）。盾は傷を全部は止めません。", "Enemy health bars appear above foes; shields no longer block everything."),
            ("倒れている間は動けず、技も使えません。岩以外の技を強くしました。", "While downed you cannot move or cast. Skills outside Rock got stronger."),
            ("敵を呼ぶ技の間隔を伸ばし、呼ぶ数を減らしました。", "Summoning skills fire less often and call fewer foes."),
            ("都の東の大通りの南に厩舎ができました（乗り物の店）。", "A stable opened south of the east avenue (mount shop)."),
            ("レイドの部屋: 巨神ごとに歩・智・闘の三部屋、中ボス、心臓の順。岩鯨は骨玉運び・共鳴の骨・鯨の核を守る。潮殻は真珠の灯り・貝の音合わせ・珊瑚の苗を守る。雷翼は避雷の羽・銅の回路・帯電の連鎖。余燼は熾火運び・鍛冶の手順・炉を守る。", "Raid rooms: each titan has traverse, wit and combat rooms, a mid-boss and the heart. Stone Whale: bone-ball carry, resonant bones, guard the core. Tide Shell: pearl light, shell tune match, coral saplings. Thunder Wing: feather carry, copper circuits, charged chain. Ember: ember carry, forging steps, guard the furnace."),
            ("深淵: 層の形を、野営 → 三本の道 → 印 → 門 → 層の主に。生き残りの探掘隊と話し、手記を集められます。", "Abyss: layers now run camp, three paths, marks, gate, layer boss. Survivors of the survey party can be spoken to, and their notes collected."),
            ("クリア後の深み: 階ごとに作り直される部屋。五階ごとに歪んだ王。", "Depths after the clear: rooms are rebuilt per floor, with a twisted king every five floors."),
            ("深淵の入口で落ちていた不具合を直しました。", "Fixed a fall at the abyss entrance."),
        ]),
        ("v0.5.1", "2026-10-23", [
            ("敵を殴るときの当たり判定を広げました（攻撃が当たりやすく）。", "Wider hit area when attacking enemies, so strikes land more reliably."),
        ]),
        ("v0.5.0", "2026-10-23", [
            ("五章「深淵」を追加。都の地下の戸から大坑道を下り、四つの層を渡って、心臓の番人に挑みます。", "Added chapter 5, the abyss: down the great shaft under the capital, through four layers, to the warden of the heart."),
            ("深淵術（光・重さ・鼓動）と、深淵術師の木を追加。", "Added the three abyss arts (light, weight, heartbeat) and the Abyss Caster tree."),
            ("クリアのあとに、番人の悪夢（段 1〜10）、前の自分たちの遺品 12 種（セットの効果つき）、心臓の下の果てない深みを追加。", "After the clear: the warden's nightmare (tiers 1 to 10), twelve past-self relics with set bonuses, and endless depths below the heart."),
            ("深淵の敵 18 種を追加。深く潜るほど、同じ敵の変わり果てた姿が出ます。", "Added 18 abyss enemies. The deeper you go, the more twisted forms of the same foes appear."),
            ("画面にゆとり。巻物のパネル、技の木のドラッグとズーム、画面の大きさに合わせた表示。", "Roomier screens: scroll panels, drag and zoom on the skill tree, and layouts scaled to the window."),
            ("1〜5 章の文章を見直し、用語をそろえました。", "Reviewed the text of chapters 1 to 5 and made the terms consistent."),
        ]),
        ("v0.2.2 以降（開発中・リリース前）", "2026-10-20", [
            ("転生（Lv 50+n）を追加。魂盤・3 択の恩恵・祖の力・色と記号・称号・順位の石・飾りがあります。", "Added rebirth (Lv 50+n) with the soul board, three-choice boons, ancestral power, colors and symbols, titles, a rank stone and cosmetics."),
            ("スキルと装備の自由度を追加（タグ・セット・連鎖・構え・熟練・キーストーン・型、穴・欠片・技のルーン・世代）。", "Added skill and gear freedom: tags, sets, chains, stances, mastery, keystones, loadouts, sockets, shards, skill runes and generations."),
            ("倒れた仲間を起こせるようになりました（約 15 秒の「倒れた」状態、3 秒の長押しで蘇生）。", "Party members can revive a downed ally (about 15 s downed, 3 s hold to revive)."),
            ("巨神の子が運ぶ旅を追加（笛で子を呼び、パーティ全員で乗って海図から行き先を選ぶ）。成体はもう動きません。", "Added travel by young carriers (summon with a whistle, ride as a party, choose a destination from a chart). Adult titans no longer move."),
            ("5 つの木と旅人の 36 の技、天職、刻印、要、型、ステータスの刻み目を追加。", "Added 36 skills across five trees and the Wayfarer, callings, runes, keystones, weapon types and stat breakpoints."),
        ]),
        ("v0.2.2", "2026-10-19", [
            ("岩鯨の背を大きくしました。ボスの技の予告を床に出すようにしました。", "Enlarged the Stone Whale's back. Boss attacks are telegraphed on the floor."),
            ("備えの三灯、戦いの復帰（20 秒・3 回まで）、旅嚢（キー I、長押しの輪 Y）、心臓の遺物の選択画面を追加。", "Added the readiness lamps, fight re-entry (20 s, three times), the Travel Satchel (I, hold-wheel Y) and the heart relic screen."),
        ]),
        ("v0.2.1", "2026-10-18", [
            ("後から入った人に序盤や場面が出ない不具合を直しました。", "Fixed the opening and cutscenes not showing for players who join later."),
        ]),
        ("v0.2.0", "2026-10-18", [
            ("章に分かれました。潮見の貝、地上で倒れたときの墓、装飾の枠、READMEとドット絵の刷新。", "Split the story into chapters. Added the Tide-watching Shell, graves on ground deaths, accessory slots, and refreshed the README and pixel art."),
        ]),
    ]
    TITLE_EN = {"v0.2.2 以降（開発中・リリース前）": "After v0.2.2 (in development, unreleased)"}
    items = []
    for v, d, notes in ver:
        date = '<p class="date">%s</p>' % d if d else ""
        cls = ' class="next"' if v.startswith("次") else ""
        items.append('<li%s><h3>%s</h3>%s<ul>%s</ul></li>' % (
            cls, T(v, TITLE_EN.get(v, v)), date,
            "".join("<li>%s</li>" % T(a, b) for a, b in notes)))
    body = page_head("更新履歴", "Changelog", "ベータ版の主な変更です。新しい版が上。0.5.6 までは試遊の確かめ順と記録から書きました。",
                     "Main changes in the beta, newest first. Versions up to 0.5.6 are written from the playtest checklists and notes.") + """
<section aria-labelledby="log">
<div class="wrap">
<h2 id="log" style="position:absolute;left:-999px">%s</h2>
<ol class="log">%s</ol>
<p>%s <a href="%s/commits/main">GitHub</a></p>
</div>
</section>
""" % (T("更新履歴", "Changelog"), "".join(items), T("細かい変更の一覧は", "The full list of changes is on"), REPO)
    page("changelog.html", "更新履歴 — 天と淵のあいだ", "Changelog — Between the Sky and the Abyss", "ベータ版の主な変更。最新は %s。" % VERSION, "Main changes in the beta. Latest: %s." % VERSION, body)
