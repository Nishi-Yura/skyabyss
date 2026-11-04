"""更新履歴の頁 (changelog.html)。版ごとの主な変更を、新しい版を上に並べる。

元の表は日英の対で持つ (サイトは日本語だけなので T が日本語を返す)。新しい版を出したら ver の頭に足す。
"""
from sitelib import REPO, T, page, sec


def changelog():
    # 版ごとの主な変更（試遊の確かめ順と docs/review の記録から）。古い版は要点だけ残す。
    ver = [
        ("v1.1.0", "2026-11-05", [
            ("1.0.0 の世界はそのまま続けられます。大陸の地上（地形・道・見どころ）は開いたときに作り直し、クエスト・持ち物・見つけた場所はそのまま残ります。", "Worlds from 1.0.0 carry over. The continent surface (terrain, roads, points of interest) is rebuilt when the world opens; quests, items and found places are kept."),
            ("戦いの手ざわり: 近接は溜めた分だけ強く（照準のまわりに溜めの輪）、回復と武器の効果は溜めきった一撃だけ。主と名のある敵は崩しの帯を削りきると崩れ、大きく削れます。経験値はクエスト・場所・主・狩りの割合を決め直しました。", "Combat feel: melee hits scale with charge (a charge ring sits around the crosshair), and heals and weapon effects need a full charge. Bosses and named foes have a break gauge that opens them up when emptied. Experience is rebalanced across quests, places, bosses and hunting."),
            ("物語: 一章の「鉄の跡」、二章から五章までイサナが港・風待の里・地下の門で待ち、各章に小さな手がかり（法螺貝・灰の道・炉の火）を足しました。淵から月の核までは昼にも淡い月が見え、結末のあとに語り部とイサナが話します。巨神はその章を進めている人にだけ見えます。", "Story: chapter 1's iron trail; from chapter 2 to 5 Isana waits at the harbor, the windy village and the underground gate, with new small clues in each chapter (the conch, the ash road, the forge fire). A pale day moon shows from the abyss to the moon core, and the storyteller and Isana speak after each ending. Titans are only visible to players on their chapter."),
            ("見どころを作り直しました（どれも名前つき、道しるべ 6 か所、野盗の兄弟、野営の旅人）。訪れた町で起き上がれ、磁石石に名札が付きます。", "Points of interest rebuilt: every place is named, with six waymarks, the bandit brothers and camp travelers. You can respawn at visited towns, and lodestones carry name tags."),
            ("画面を黄金比の形にそろえ、技の木を三つ目の形（使う技 4 本、金の要の節）にしました。クエストログの先頭に「今日・今週」の頁、記録は七つのつまみの一つの画面にまとまりました。", "Screens now share golden-ratio panels, and skill trees take their third form (four active skills, gold keystone nodes). The quest log opens with a today / this week page, and all records sit in one screen with seven tabs."),
            ("やり込み: 土地ごとの巡り路の野営地（三日ごとに替わる報酬）、神器の目覚め（倒した数で三段）、目に見える粉の技、ダンジョンの深潜りと日替わり、封の箱のヒント、魚の注文と釣り場、乗り物の早駆けの道と絆、祝福・狩人の手記・二人乗り・秘密・珍しい獣。", "Endgame: lootrun camps per region with rewards that rotate every three days, artifact awakening in three stages, visible powder arts, dungeon deep dives and daily moods, sealed box hints, fish orders and fishing spots, mount sprint routes and bonds, plus boons, hunter's notes, tandem riding, secrets and rare beasts."),
            ("倒れても持ち物はいつも残ります（墓は無くなり、前の墓の中身は持ち主へ返ります）。倒れたあとは少しのあいだ弱まり、夜の群れは手ごわくなりました。敵から経験値の玉は出ません。", "Items are always kept on death (graves are gone and old graves return their contents to their owner). You are briefly weakened after dying, and night packs are tougher. Foes no longer drop experience orbs."),
            ("終章の雲の上の敵 16 種の声（環境音・傷・倒れる音）が鳴るようになりました（1.0.0 では音の登録が抜けていました）。英語の見どころの書き付けと塔の知らせが、切れずに収まるようになりました。", "The sixteen cloud-sea foes of the finale now have their voices (ambient, hurt and death sounds were missing from 1.0.0). English point-of-interest notes and tower notices now fit without being cut off."),
        ]),
        ("v1.0.0", "2026-11-02", [
            ("正式版です。0.9.23 の候補版のあと、まっさらな NeoForge のサーバーとクライアントで起動・世界の作成・入る所までを確かめ、英語の文を見直しました。", "The release. After the 0.9.23 candidate, startup, world creation and joining were checked on a clean NeoForge server and client, and the English text was reviewed."),
            ("旅の地図を見やすく（土地の名前・方位盤・札）、この mod の三人称（F5 で肩越し、F6 で肩を替える、照準の所へ当たる）を入れました。", "A more readable travel map (region names, compass rose, labels) and the mod's own third-person view (F5 for over-the-shoulder, F6 to swap shoulders, hits land under the crosshair)."),
        ]),
        ("v0.9.23", "2026-11-02", [
            ("正式版の候補です。地下のダンジョンを四つ足しました（鯨骨の坑・潮の洞・雷晶の空洞・熔けた坑道）。町の案内人の「噂を聞く」から始まり、二つの層と主の間を抜けて主を倒すと入口へ戻ります。ダンジョンだけの敵と中ボス、主 4 体、初めて倒したときの装飾品と称号があります。", "Release candidate. Four underground dungeons (the Bone Pit, the Tide Cave, the Storm Hollow and the Molten Mine), started from the town guide's rumors: two floors and a boss room, then back to the entrance. Dungeon-only foes and mid-bosses, four bosses, and a trinket and title for the first clear."),
            ("乗り物を三つ足しました（灯り守の大守宮・星読みの大猫・空を飛べる空鱏）。乗り物の体力の段はなくなり（前に買った分は鐘銭で返します）、速さは 8 段まで上がります。", "Three new mounts (the Lamp-Keeper Gecko, the Star-Reader Lynx and the flying Sky Ray). Mount health tiers are gone (earlier purchases are refunded in coins) and speed now goes up to tier 8."),
            ("神器はそれぞれ大きな能力と欠点を一つずつ持ち、武器には決まった数値の行が付きます。型に 8 点使うと、その型の溜まるもの（マナの帯の上）が目覚めます。技の待ちは短く、マナは少し重くなりました。", "Each divine weapon now has one major ability with a drawback, and weapons carry fixed stat lines. Spending 8 points in an archetype awakens its gauge (above the mana bar). Skill cooldowns are shorter and mana costs slightly higher."),
            ("店の浮く名札がブロックにめり込まなくなりました。都の空き家（旧厩舎・地図屋・空見の館）は戸を塞ぎ、碑もなくしました。わかりにくい文を直しました。", "Shop name tags no longer clip into blocks. The capital's empty houses (the old stable, the mapmaker's and Skywatch Hall) are sealed and their stone tablets removed. Unclear lines were rewritten."),
        ]),
        ("v0.9.22", "2026-11-02", [
            ("Mod の版を上げても、前の版の世界をそのまま続けられます（世界と人のデータの版を持ち、版が上がると直しを自動で流します）。管理者は <code>/skyabyss admin version</code> で版を見られます。", "Worlds from earlier versions carry over when the mod updates: each world and player keeps a data version, and upgrades run automatically. Admins can check it with <code>/skyabyss admin version</code>."),
            ("コマンドを整理: 遊ぶ人は <code>/skyabyss help</code> で一覧、管理者は <code>/skyabyss admin …</code>（権限の段はサーバーの設定 <code>adminPermissionLevel</code>、既定 2）。", "Commands tidied: players get a list with <code>/skyabyss help</code>, admins use <code>/skyabyss admin …</code> (permission level from the server config <code>adminPermissionLevel</code>, default 2)."),
            ("場面・絵芝居・封の箱のルーレットは、どれも Enter で飛ばせます。", "Cutscenes, picture stories and the loot-box roulette are all skipped with Enter."),
            ("松明などの置く品は手に入らなくなり、ブロックが壊れても品が落ちません。破れた頁と伝承の書は、拾う人のほうへ寄ってきます。", "Placeable items such as torches are no longer handed out, and broken blocks drop nothing. Torn pages and the lore book drift toward the player who can pick them up."),
            ("新しく建つ跡地・見どころ・道しるべは道の上に乗りません。はじまりの島の峠の柵をなくしました。空見の館は開かず、人のいない厩舎の名札も消しました。鍛冶師の修理はなくなりました。", "New ruins, points of interest and signposts stay off the roads. The barricade at the island pass is gone. Skywatch Hall stays shut, the empty stable no longer shows a name tag, and the smith no longer repairs."),
            ("前の版で建った都でも、係の人が直した立ち位置に立ち、町のクエストの「〜へ行こう」が着いたときに済みます。", "In capitals built by earlier versions, attendants now stand at their corrected spots and town quest \"go to\" steps complete on arrival."),
        ]),
        ("v0.9.21", "2026-11-02", [
            ("終章のあとに、あいだの塔（大きなレイド）、神器と雲の箱、秘伝、裏ボス「初代の空見」、巡り路・大陸の大事件・名声と旅帳・職の熟練・組合の旗取り・図鑑の報い・見た目替え・釣りを足しました。", "After the finale: the Tower Between (a large raid), divine gear and cloud boxes, secret arts, the hidden boss The First Skywatcher, circuits, continent events, fame and journals, job mastery, guild flags, codex rewards, appearance overrides and fishing."),
        ]),
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
            ("6 職（戦士・鍛冶師・魔法使い・弓使い・暗殺者・呪術師）。島の祠で最初の職を選びます（職の替え方は 0.9.18 から町の職の間）。旅人の木・天職・構え・連鎖・刻み目・橋の木はなくなりました。", "Six jobs (Warrior, Smith, Mage, Archer, Assassin, Shaman). The first job is chosen at the island shrine (since 0.9.18 you switch jobs in a town job room). The Wayfarer tree, callings, stances, chains, breakpoints and bridge trees are gone."),
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
    items = []
    for v, d, notes in ver:
        date = '<p class="date">%s</p>' % d if d else ""
        items.append("<li><h3>%s</h3>%s<ul>%s</ul></li>" % (v, date, "".join("<li>%s</li>" % T(a, b) for a, b in notes)))
    body = ('<div class="wrap" style="padding-top:40px"><p class="eyebrow">天と淵のあいだ</p>'
            '<h1 style="font-family:var(--f-title);font-size:44px">更新履歴</h1>'
            '<p class="small">版ごとの主な変更です。新しい版が上。0.5.6 までは試遊の確かめ順と記録から書きました。</p></div>')
    body += sec("log", "CHANGELOG", "版ごとの変更", "",
                '<ol class="log">%s</ol><p class="small" style="margin-top:28px">細かい変更の一覧は <a href="%s/commits/main">GitHub</a> にあります。</p>'
                % ("".join(items), REPO))
    page("changelog.html", "更新履歴 — 天と淵のあいだ", "天と淵のあいだの版ごとの主な変更。", body)
