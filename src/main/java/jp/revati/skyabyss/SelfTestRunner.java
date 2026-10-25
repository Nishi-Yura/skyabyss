package jp.revati.skyabyss;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import jp.revati.skyabyss.item.GearCurveSelfTest;
import jp.revati.skyabyss.item.accessory.AccessorySelfTest;
import jp.revati.skyabyss.item.affix.AffixSelfTest;
import jp.revati.skyabyss.lore.LoreSelfTest;
import jp.revati.skyabyss.progress.ProgressSelfTest;
import jp.revati.skyabyss.prologue.PrologueSelfTest;
import jp.revati.skyabyss.quest.NpcQuestSelfTest;
import jp.revati.skyabyss.quest.free.FreeQuestSelfTest;
import jp.revati.skyabyss.record.RecordSelfTest;
import jp.revati.skyabyss.rpg.GrowthSelfTest;
import jp.revati.skyabyss.rpg.skill.SkillSelfTest;
import jp.revati.skyabyss.sky.ChapterSkySelfTest;
import jp.revati.skyabyss.titan.WhaleKeeper;
import jp.revati.skyabyss.titan.dungeon.TitanBackSelfTest;
import jp.revati.skyabyss.titan.tide.TideSelfTest;
import jp.revati.skyabyss.titan.tide.heart.TideHeartSelfTest;
import jp.revati.skyabyss.titan.tide.mob.TideMobSelfTest;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 自己テストの入口。環境変数 SKYABYSS_SELFTEST=1 で専用サーバーを起動したときだけ動く。
// 各系統を 1 tick に 1 つずつ順に流す (重いものは系統の中をさらに何 tick かに分ける)。
// 1 tick にまとめて動かすと、サーバーのウォッチドッグ (max-tick-time=60000) に止められるため。
// 重い段のあとは、サーバーが予定の時刻に追いつくまで次の段を待つ (下の onServerTick)。
// 結果の行 (SELFTEST <名前> PASSED / FAILED) は各系統が書く。最後のまとめ (SELFTEST PASSED / FAILED) と
// サーバーを止めるのは岩鯨の背の自己テスト (TitanBackSelfTest) の最後の段
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SelfTestRunner {
    // 一段: 前の段が終わってから waitBefore tick 待って、action を毎 tick 呼ぶ。true を返したら次の段へ
    public record Step(String name, int waitBefore, Predicate<MinecraftServer> action) {
        public static Step once(String name, Consumer<MinecraftServer> action) {
            return once(name, 0, action);
        }

        public static Step once(String name, int waitBefore, Consumer<MinecraftServer> action) {
            return new Step(name, waitBefore, server -> {
                action.accept(server);
                return true;
            });
        }

        public static Step until(String name, Predicate<MinecraftServer> action) {
            return new Step(name, 0, action);
        }
    }

    private static final long CATCH_UP_NANOS = 1_000_000_000L;
    private static final ArrayDeque<Step> QUEUE = new ArrayDeque<>();
    private static Step current;
    private static int wait;
    private static int stepTicks;
    private static long stepNanos;
    private static long stepLongest;
    private static long startedAt;
    private static long slowestTick;
    private static String slowestStep = "";

    private SelfTestRunner() {
    }

    public static boolean enabled() {
        return System.getenv("SKYABYSS_SELFTEST") != null;
    }

    private static List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        // 岩鯨の背: 建てる・調べる (番兵を動かす段は最後。サーバーを止めるので)
        steps.addAll(TitanBackSelfTest.buildSteps());
        // グループ (パーティ・一人) の共通部分: 部屋の占有・戦いの参加者・個人の影・乗っている人の数え方・放置の分け前。docs/design/10 の 10 章
        steps.add(Step.once("group", jp.revati.skyabyss.titan.group.GroupSelfTest::runSelfTest));
        // 倒れた参加者の再入場 (20 秒の待ち・回数・全滅でやり直し・ゲームルール)。docs/design/02 の 4.2、10 の 10.4
        steps.add(Step.once("heart reentry", jp.revati.skyabyss.titan.group.ReentrySelfTest::runSelfTest));
        // 全滅・全員が出る・再起動・保存から戻る・背中に誰もいない で、心臓が残らない (2026-10-23 の試遊の不具合)。SKYABYSS_SELFTEST_ONLY="heart orphan"
        steps.addAll(jp.revati.skyabyss.titan.group.HeartOrphanSelfTest.steps());
        // 背の野営の道具 (置けない物の手持ち・置ける道具・上限・外す・背中を出ると戻る)。docs/design/08 の背中の決まり
        steps.add(Step.once("camp blocks", jp.revati.skyabyss.titan.dungeon.CampSelfTest::runSelfTest));
        // パーティの石: 誰かが試練のあとの石を鳴らせば、仲間全員 (離れた人・あとから入った人) のクエストが数える。docs/design/10 の 10.9
        steps.add(Step.once("party steles", jp.revati.skyabyss.titan.group.TrialStelesSelfTest::runSelfTest));
        // 初回と周回の共通部品 (first_cleared・既知の道・場面と追憶の宝の人ごとの記録)。docs/design/21 の 1.7、10 の 10.11
        steps.add(Step.once("back first", jp.revati.skyabyss.titan.group.FirstRunSelfTest::runSelfTest));
        // 周回の誓い (Vow)・周回の言い回し、居座りの早じまい (無人 3 分)。docs/design/21 の 1.8、10 の 10.15、rpg_brushup B4 / B5
        steps.add(Step.once("vow (load)", jp.revati.skyabyss.titan.vow.VowSelfTest::load));
        steps.add(Step.once("vow", 40, jp.revati.skyabyss.titan.vow.VowSelfTest::runSelfTest));
        steps.add(Step.once("stay leave", jp.revati.skyabyss.titan.vow.VowSelfTest::runStaySelfTest));
        // 使い心地の磨き: 背中の復活の守り・石の「誰が鳴らしたか」・墓の標 (rpg_brushup A3 / A4 / A8)
        steps.add(Step.once("ux polish", jp.revati.skyabyss.titan.group.UxPolishSelfTest::runSelfTest));
        // brushup の直し: 戦いの最中の復活先 (四つの背)・復活の守りの後始末・lang の重複と ja/en の一致
        steps.add(Step.once("brushup", jp.revati.skyabyss.titan.group.BrushupSelfTest::runSelfTest));
        steps.add(Step.once("lang", LangSelfTest::runSelfTest));
        steps.add(Step.once("keys", KeyDefaultsSelfTest::runSelfTest));
        // 武器の動き (データ・絵と当たりの同期・連撃の時間・釣り合い) と、預けたクリックが当たる tick に当たる流れ。SKYABYSS_SELFTEST_ONLY="weapon"
        steps.add(Step.once("weapon moves", jp.revati.skyabyss.weapon.WeaponMoveSelfTest::runSelfTest));
        steps.add(Step.until("weapon strike", jp.revati.skyabyss.weapon.WeaponMoveSelfTest::runFlow));
        steps.add(Step.once("weapon strike end", s -> jp.revati.skyabyss.weapon.WeaponMoveSelfTest.finishFlow()));
        // 敵が壁・地面の中に湧かない (すべての湧き口と波に共通の決まり)。SKYABYSS_SELFTEST_ONLY="spawn safety"
        steps.add(Step.once("spawn safety", jp.revati.skyabyss.util.SpawnSafetySelfTest::runSelfTest));
        steps.add(Step.once("lang objective band", ObjectiveBandSelfTest::runSelfTest));
        steps.add(Step.once("players snapshot", jp.revati.skyabyss.util.PlayersSelfTest::runSelfTest));
        // 目的の道しるべ (矢印の角度・歩ける道の探索・予算・持ち主にだけ・パケット)。docs/design/18 の 8.3 節
        steps.add(Step.once("guide nav", jp.revati.skyabyss.guide.nav.GuideNavSelfTest::runSelfTest));
        steps.add(Step.once("party locator", jp.revati.skyabyss.party.PartyLocatorSelfTest::runSelfTest));
        // 旅の地図の印 (序盤の記録から噂/見た、噂の位置の丸め、一人ぶんの集め・パケット)。docs/design/33 の 4 章。SKYABYSS_SELFTEST_ONLY="travel map"
        steps.add(Step.once("travel map", jp.revati.skyabyss.map.MapMarksSelfTest::runSelfTest));
        // 仲間が起こせる仕組み・倒れた回数の札・「ここ」の印。docs/design/10 の 10.12〜10.14
        steps.add(Step.once("party revive", jp.revati.skyabyss.party.ReviveSelfTest::runSelfTest));
        steps.add(Step.once("growth", GrowthSelfTest::runSelfTest));
        // 転生 (戻る・残る範囲、上限 20、代価、恩恵、余白の枠、導きの欠片、共闘の合わせ)。docs/design/31。SKYABYSS_SELFTEST_ONLY="soul"
        steps.add(Step.once("soul", jp.revati.skyabyss.rebirth.SoulSelfTest::runSelfTest));
        steps.add(Step.once("skills", SkillSelfTest::runSelfTest));
        // 画面の知らせ (種類分け・順番待ち)。docs/design/18_notices.md
        steps.add(Step.once("notice", jp.revati.skyabyss.notice.NoticeSelfTest::runSelfTest));
        // HUD の置き場の計算 (倍率 1〜4 × 代表的な窓で重ならない)。docs/design/18 の 14 章
        steps.add(Step.once("hud layout", jp.revati.skyabyss.hud.HudLayoutSelfTest::runSelfTest));
        steps.add(Step.once("tutorial", jp.revati.skyabyss.tutorial.TutorialSelfTest::runSelfTest));
        // 技の稽古 (師範の手ほどき: 覚える・置く・木偶に当てる・背後・構え・振り直し・後の章の一段)。SKYABYSS_SELFTEST_ONLY="skill lesson"
        steps.add(Step.once("skill lesson", jp.revati.skyabyss.tutorial.SkillLessonSelfTest::runSelfTest));
        // 転生の見せ方 (色の段・記号・称号・順位表・同期・記録・墓の飾り)。docs/design/31
        steps.add(Step.once("rebirth load", jp.revati.skyabyss.rebirth.RebirthSelfTest::load));
        steps.add(Step.once("rebirth", 40, jp.revati.skyabyss.rebirth.RebirthSelfTest::runSelfTest));
        // 大事な場面のカメラ演出 (台本・日英の文・座標・サーバーの流れ)。docs/design/20_cinematics.md
        steps.add(Step.once("cinematic", jp.revati.skyabyss.cinematic.CinematicSelfTest::runSelfTest));
        steps.add(jp.revati.skyabyss.cinematic.SightLiveSelfTest.step(jp.revati.skyabyss.progress.Titans.THUNDER_WING));
        // 深淵 (B04): 転生での開閉・修飾の段・心臓の湧き・落とし表・書き換えの効き。SKYABYSS_SELFTEST_ONLY="abyss"
        steps.addAll(jp.revati.skyabyss.abyss.AbyssSelfTest.steps());
        // 深淵術 (光・重さ・鼓動): 鍵・溜まり・灯り・覚える・木・上げる・使う・圧への接ぎ。docs/design/36 の 5 節。SKYABYSS_SELFTEST_ONLY="abyss arts"
        steps.add(Step.once("abyss arts", jp.revati.skyabyss.abyss.arts.AbyssArtsSelfTest::runSelfTest));
        // 深淵の敵の湧き: 建てた深淵の中心の塊を先に読み込んで建てる。SKYABYSS_SELFTEST_ONLY="abyss enemies" (下の三段すべて)
        steps.add(Step.once("abyss enemies (load)", jp.revati.skyabyss.abyss.mobs.AbyssEnemiesSpawnSelfTest::load));
        // 深淵の敵 A (9 種の型・三段の素材・段の決まり・数字の伸び・予告・殻と盾・塩の抜け殻の割れ)。SKYABYSS_SELFTEST_ONLY="abyss enemies a"
        steps.add(Step.once("abyss enemies a", jp.revati.skyabyss.abyss.mobs.AbyssMobsASelfTest::runSelfTest));
        // 深淵の敵 B 組 (灰の層・深み・深淵だけ): 登録・素材・形・数字・灰の這い跡の光・天井歩き・灯喰い・縄の人・湧きの休み場。SKYABYSS_SELFTEST_ONLY="abyss enemies b"
        // 層の主・歪んだ王の見た目 (型・周・段の同期、19 の素材と動きの名前)。SKYABYSS_SELFTEST_ONLY="abyss boss looks"
        steps.add(Step.once("abyss boss looks", jp.revati.skyabyss.abyss.boss.BossLookSelfTest::runSelfTest));
        steps.add(Step.once("abyss enemies b", jp.revati.skyabyss.abyss.mobs.AbyssMobsBSelfTest::runSelfTest));
        // 建てた深淵での湧き (層ごとの種・形・床・休み場・心臓の間・人数の上限・数字・暗さ)。SKYABYSS_SELFTEST_ONLY="abyss enemies spawn"
        steps.add(Step.once("abyss enemies spawn", jp.revati.skyabyss.abyss.mobs.AbyssEnemiesSpawnSelfTest::run));
        steps.add(Step.once("gear curve", GearCurveSelfTest::runSelfTest));
        // 装備の階級 (6 段): 全装備の表・決まりの単調さ・段の中の逆転なし・落とし表・名前とツールチップ。03 の 1.2・1.4節。SKYABYSS_SELFTEST_ONLY="gear tiers"
        steps.add(Step.once("gear tiers", jp.revati.skyabyss.item.GearTierSelfTest::runSelfTest));
        // 心臓四体の手ごたえ (戦いの長さ・一撃・避けそこね)
        steps.add(Step.once("heart balance", jp.revati.skyabyss.combat.HeartBalanceSelfTest::runSelfTest));
        steps.add(Step.once("affix", AffixSelfTest::runSelfTest));
        steps.add(Step.once("gear freedom", jp.revati.skyabyss.item.extra.GearFreedomSelfTest::runSelfTest));
        // 心臓の遺物の選択 (四体・一度だけ・入り直し・二人・あとで・場面の終わり)。docs/design/30_heart_reward_ui.md。SKYABYSS_SELFTEST_ONLY="heart reward"
        steps.addAll(jp.revati.skyabyss.loot.reward.HeartRewardSelfTest.steps());
        steps.add(Step.once("accessory", AccessorySelfTest::runSelfTest));
        steps.add(Step.once("tide gear", jp.revati.skyabyss.titan.tide.gear.TideGearSelfTest::runSelfTest));
        steps.add(Step.once("storm gear", jp.revati.skyabyss.item.storm.StormGearSelfTest::runSelfTest));
        // 余燼の装備と伝承・天の鍵の締め (13 の 11.2節の担当 G)
        steps.add(Step.once("ember gear", jp.revati.skyabyss.titan.ember.gear.EmberGearSelfTest::runSelfTest));
        steps.add(Step.once("abyss gear", jp.revati.skyabyss.abyss.gear.AbyssGearSelfTest::runSelfTest));
        steps.add(Step.once("ember lore", jp.revati.skyabyss.titan.ember.lore.EmberLoreSelfTest::runSelfTest));
        // (大陸の世界は出現地点がはじまりの島へ動いたばかりで、そこのチャンクがまだ動いていないことがある。30 秒で諦めて進む)
        int[] recordWait = {0};
        steps.add(Step.until("record (wait)", server -> {
            var over = server.overworld();
            var spawn = over.getSharedSpawnPos();
            over.setChunkForced(spawn.getX() >> 4, spawn.getZ() >> 4, true);
            return over.isPositionEntityTicking(spawn) || ++recordWait[0] > 600;
        }));
        steps.add(Step.once("record", RecordSelfTest::runSelfTest));
        // 備えの三灯 (式・色の境目・乗り込みの確認の流れ・前室の囁き・控え帳の行)。docs/design/21 の 1.2、11 の 9 章
        steps.add(Step.once("readiness", jp.revati.skyabyss.readiness.ReadinessSelfTest::runSelfTest));
        // 章の導きの基盤 (備えの頼み・旅の覚書・鼓動守・場所の湧き台と宝・章のひとり歩き・経験値の枠)。docs/design/21 の P3
        steps.addAll(jp.revati.skyabyss.guide.GuideSelfTest.steps());
        // 旅装・砥石・糧と薬 (P5) と旅の職人 (P4)。docs/design/21 の 1.2・1.4
        steps.addAll(jp.revati.skyabyss.guide.TravelSelfTest.steps());
        steps.addAll(jp.revati.skyabyss.npc.ArtisanSelfTest.steps());
        // 都の施設 (係の人・鐘銭・店・預かり所・宿・ギルド)。docs/design/35。SKYABYSS_SELFTEST_ONLY=facility
        steps.addAll(jp.revati.skyabyss.facility.FacilitySelfTest.steps());
        // 都の案内人と灯りの道 (印・どの施設にも道・選んだときの知らせ)。docs/design/35 の 6.12。SKYABYSS_SELFTEST_ONLY="town guide"
        steps.addAll(jp.revati.skyabyss.facility.TownGuideSelfTest.steps());
        // 都の町めぐり (docs/design/40 の 4 章)。SKYABYSS_SELFTEST_ONLY="town tour"
        steps.addAll(jp.revati.skyabyss.facility.TownTourSelfTest.steps());
        // 乗り物 (見た目と性能の買い物・呼ぶ/帰す・背中と深淵の禁止)。docs/design/38。SKYABYSS_SELFTEST_ONLY=mount
        steps.addAll(jp.revati.skyabyss.mount.MountSelfTest.steps());
        // 町のクエスト (限定の人・噂・墓守・酔いどれ・地図屋・古井戸)。docs/design/35 の 3.3・3.5。SKYABYSS_SELFTEST_ONLY=town
        steps.addAll(jp.revati.skyabyss.town.TownSelfTest.steps());
        // NPC v2 (人ごとの形・持ち物・動き)。docs/review/npc_quality_2026-10-23.md。SKYABYSS_SELFTEST_ONLY="lowpoly npc"
        steps.addAll(jp.revati.skyabyss.npc.NpcModelSelfTest.steps());
        // 倒れたときの決まりと墓 (docs/design/11 の 6 章)
        steps.add(Step.once("grave (load)", jp.revati.skyabyss.grave.GraveSelfTest::load));
        steps.add(Step.once("grave", 40, jp.revati.skyabyss.grave.GraveSelfTest::runSelfTest));
        // 留め袋 (登録の表・保存と同期・入れる/出す・拾う・掃除・倒れても残る・読む側・移し替え)。docs/design/22 の 13 章
        steps.add(Step.once("pouch", jp.revati.skyabyss.pouch.PouchSelfTest::runSelfTest));
        steps.add(Step.once("playtest", 40, jp.revati.skyabyss.playtest.PlaytestSelfTest::runSelfTest));
        // 試遊コマンド (skyabyss play): 登録・移動先・章の段階・道具・文言。docs/design/37 の 4 節。SKYABYSS_SELFTEST_ONLY="play"
        steps.add(Step.once("playtest play", 40, jp.revati.skyabyss.dev.play.PlaySelfTest::runSelfTest));
        steps.addAll(jp.revati.skyabyss.dev.play.PlayMultiSelfTest.steps());
        steps.add(Step.once("item glow", jp.revati.skyabyss.glow.ItemGlowSelfTest::runSelfTest));
        steps.add(Step.once("progress", ProgressSelfTest::runSelfTest));
        steps.add(Step.once("lore", LoreSelfTest::runSelfTest));
        // 章の写像 (段階 × 序章、伝承の章との一致)。docs/design/10 の 3 章
        steps.add(Step.once("chapters", jp.revati.skyabyss.story.ChaptersSelfTest::runSelfTest));
        // 章の演出の共通部品 (題字・余韻の札、予兆の枠、クエストの章の欄、会話の追加ファイル、空の段)。docs/design/18 の 3 章、01 の 4.1
        steps.add(Step.once("arc", jp.revati.skyabyss.story.arc.ArcSelfTest::runSelfTest));
        steps.add(Step.once("npc", NpcQuestSelfTest::runSelfTest));
        steps.add(Step.once("freequest", FreeQuestSelfTest::runSelfTest));
        // 1 章を 4 体分で (順番・乗る資格・上限・天の鍵・メインクエストの続き)。docs/design/14_beta.md
        steps.add(Step.until("story (load)", jp.revati.skyabyss.story.StorySelfTest::ready));
        steps.add(Step.once("story", jp.revati.skyabyss.story.StorySelfTest::runSelfTest));
        // 序盤の語り (17 の 3〜6 章): 野営地の帳面から宿り・空の影・イサナまで、新しい伝承 6 つ、道すがらの見聞
        steps.add(Step.until("opening story (load)", jp.revati.skyabyss.story.OpeningStorySelfTest::ready));
        steps.add(Step.once("opening story", jp.revati.skyabyss.story.OpeningStorySelfTest::runSelfTest));
        // 序盤の拍子 B0〜B10 (17 の 3 章): 判断の流し込み (新しいワールド・雨・洞窟・寝た人・戦闘・村なし・マルチ)、本物の人での確認
        steps.add(Step.once("opening", jp.revati.skyabyss.opening.OpeningSelfTest::runSelfTest));
        // 雷翼の伝承とクエスト (碑文・囁き・伝承の項目・嵐を渡る翼・背の周回)。docs/design/12_thunder_wing.md の 11.2 節 担当 6
        steps.add(Step.once("storm lore", jp.revati.skyabyss.titan.storm.lore.StormLoreSelfTest::runSelfTest));
        steps.add(Step.once("prologue", PrologueSelfTest::runSelfTest));
        // 序盤の構造物 (野営地・鐘の宿り・巨大な足跡・巨神の骨)。docs/design/17 の 4 章
        steps.addAll(jp.revati.skyabyss.opening.site.SiteSelfTest.steps());
        steps.addAll(jp.revati.skyabyss.opening.site.CapitalDoorsSelfTest.steps());
        steps.addAll(jp.revati.skyabyss.opening.site.SiteGuardSelfTest.steps());
        // 章の跡地 (方位と距離の帯・四つの形・道しるべ・祠の置き場・潮殻の浜)。docs/design/33 の 3 章 (W2)。SKYABYSS_SELFTEST_ONLY="chapter grounds"
        steps.addAll(jp.revati.skyabyss.opening.site.ChapterGroundsSelfTest.steps());
        steps.addAll(ChapterSkySelfTest.steps());
        // 序章・一章の中身 (初日のお使い・旅の跡・歌石・見張りの丘・小さな異変・歩哨・岩鯨の影の出来事・予兆 3 拍)。docs/design/17 の 1.5。岩鯨を出す段より前に置く
        steps.addAll(jp.revati.skyabyss.story.ch1.Ch1SelfTest.steps());
        // 一章の地上の敵 (岩殻虫・棘うさぎ・骨負い・老いた歩哨。P7。21 の 3.1)。SKYABYSS_SELFTEST_ONLY="ch1 mobs"
        steps.addAll(jp.revati.skyabyss.story.ch1.Ch1MobsSelfTest.steps());
        // 二章の地上の敵 (潮だまり貝・砂潜り・宿借り・潮の番人。P12。21 の 4.1)。SKYABYSS_SELFTEST_ONLY="ch2 mobs"
        steps.addAll(jp.revati.skyabyss.entity.Ch2MobsSelfTest.steps());
        // チャンクを読み込んでから、エンティティが入るのを待って試すもの
        // 序盤の流れの保証 (最初の影・書・岩鯨の門・語り部の野営)。平らな草地を作るので、チャンクを読み込んでから
        steps.add(Step.once("opening beats (load)", jp.revati.skyabyss.opening.OpeningBeatsSelfTest::load));
        steps.add(Step.once("opening beats", 60, jp.revati.skyabyss.opening.OpeningBeatsSelfTest::runSelfTest));
        // 巨神の体に入れない仕組み (体積・通り道・押し出し・矢)。docs/design/02 の「体に入れない」
        steps.add(Step.once("titan solid (load)", jp.revati.skyabyss.titan.solid.TitanSolidSelfTest::load));
        steps.add(Step.once("titan solid", 40, jp.revati.skyabyss.titan.solid.TitanSolidSelfTest::runSelfTest));
        steps.add(Step.once("whale (load)", WhaleKeeper::selfTestLoad));
        // 試す所のチャンクが敵の動く状態 (entity ticking) になるまで待つ (ほかの段のあとはサーバーが忙しく、40 tick では読み込みが済まず、
        // 呼び直した岩鯨が見えないまま「登録されていない」と落ちていた)
        steps.add(Step.until("whale (wait)", WhaleKeeper::selfTestReady));
        steps.add(Step.once("whale", 20, WhaleKeeper::selfTestRun));
        // 巨神の子 (笛で現れる・パーティ全員 6 人が乗る・得意ごとの行き先・降りると歌に溶ける・眠りの断り・入り直し)。docs/design/28。SKYABYSS_SELFTEST_ONLY=young
        steps.addAll(jp.revati.skyabyss.titan.young.YoungSelfTest.steps());
        steps.add(Step.once("tide_heart (load)", TideHeartSelfTest::load));
        steps.add(Step.once("tide_heart", 30, TideHeartSelfTest::runSelfTest));
        steps.add(Step.once("titan end (reset after tide_heart)", jp.revati.skyabyss.titan.end.TitanEndSelfTest::resetAll));
        steps.add(Step.once("storm_heart (load)", jp.revati.skyabyss.titan.storm.heart.StormHeartSelfTest::load));
        steps.add(Step.once("storm_heart", 30, jp.revati.skyabyss.titan.storm.heart.StormHeartSelfTest::runSelfTest));
        steps.add(Step.once("titan end (reset after storm_heart)", jp.revati.skyabyss.titan.end.TitanEndSelfTest::resetAll));
        steps.add(Step.once("storm_heart (light)", 40, jp.revati.skyabyss.titan.storm.heart.StormHeartSelfTest::checkLight));
        // 心臓の登場と倒れる演出・高さの固定 (四つの心臓の共通部分 HeartBoss)
        steps.add(Step.once("heart_cinematic (load)", jp.revati.skyabyss.entity.HeartCinematicSelfTest::load));
        steps.add(Step.once("heart_cinematic", 20, jp.revati.skyabyss.entity.HeartCinematicSelfTest::runSelfTest));
        // 心臓四体は「一方向に回り続けるだけ」では倒せない (回る人形は負け、予告を読む人形は生き残る) と、心臓の領域・技の名前。SKYABYSS_SELFTEST_ONLY="heart bots"
        steps.add(Step.once("heart bots (load)", jp.revati.skyabyss.combat.HeartBotSelfTest::load));
        steps.add(Step.once("heart bots", 40, jp.revati.skyabyss.combat.HeartBotSelfTest::runSelfTest));
        // 予告 (Telegraph): 当たる範囲と印が同じ形・予告 0.8 秒以上・番兵長と岩鯨の心臓の技・人数で増える。docs/design/02 の 5.4
        steps.addAll(jp.revati.skyabyss.combat.TelegraphSelfTest.steps());
        // 余燼のレイドの三つの部屋 (熾火運び・鍛冶の手順・炉を守る)。SKYABYSS_SELFTEST_ONLY="ember raid"
        steps.addAll(jp.revati.skyabyss.trial.raid.ember.EmberRaidSelfTest.steps());
        // 岩鯨の背の新しい敵 (骨の砲手・骨鳥・老番兵)。SKYABYSS_SELFTEST_ONLY="ch1 back mobs"。21 の 3.3
        steps.addAll(jp.revati.skyabyss.entity.Ch1BackMobsSelfTest.steps());
        // 巨神の眠りと運び出し (心臓を止めたあと。眠り方・運ぶ人・降ろす場所・初回の場面)。docs/design/01 の 4.3、20 の 3.4
        steps.add(Step.once("titan end (load)", jp.revati.skyabyss.titan.end.TitanEndSelfTest::load));
        steps.add(Step.once("titan end", 60, jp.revati.skyabyss.titan.end.TitanEndSelfTest::runSelfTest));
        steps.addAll(jp.revati.skyabyss.titan.end.HeartAftermathSelfTest.steps());
        steps.add(Step.once("titan end (reset after hearts)", jp.revati.skyabyss.titan.end.TitanEndSelfTest::resetAll));
        steps.add(Step.once("tide (load)", TideSelfTest::load));
        steps.add(Step.once("tide", 50, TideSelfTest::runSelfTest));
        // 巨神が地形にめり込まない (潮殻の休む場所・巡航、雷翼の止まり木、潮殻の乗り場が体の外)。SKYABYSS_SELFTEST_ONLY="titan ground"
        steps.add(new Step("titan ground", 10, jp.revati.skyabyss.titan.tide.TitanGroundSelfTest::tick));
        // 呼びの探し・戻る探しの時間 (読み込まれた地形と新しい地形)。結果は変えない。SKYABYSS_SELFTEST_ONLY="call perf"
        steps.addAll(jp.revati.skyabyss.titan.young.CallPerfSelfTest.steps());
        steps.add(Step.once("tide mobs (load)", TideMobSelfTest::load));
        steps.add(Step.once("tide mobs", 45, TideMobSelfTest::runSelfTest));
        // 潮殻の心臓と中ボス潮招きの予告 (Telegraph): 印と当たりが同じ・予告 0.8 秒以上・安全な床 15% 以上・人数で増える・部屋に収まる。docs/design/08 の 5.5
        steps.addAll(jp.revati.skyabyss.titan.tide.TideTelegraphSelfTest.steps());
        // 潮殻のレイドの順路 (引かれた TR1・智の部屋・TR3 を二人組で順路で動かし、台本で解く・全滅で戻る)。SKYABYSS_SELFTEST_ONLY="tide raid"
        steps.addAll(jp.revati.skyabyss.trial.raid.tide.TideRaidSelfTest.steps());
        // 潮殻のレイドの新しい四部屋 TR4〜TR7 と枠ごとの引き (TR2 は引かれない)。SKYABYSS_SELFTEST_ONLY="tide raid"
        steps.addAll(jp.revati.skyabyss.trial.raid.tide.TideRaid2SelfTest.steps());
        // 雷翼の巨神 (窓・止まり木・昇り風・一体だけ・記録・戻る先・背中の結びつけ)
        steps.add(Step.once("storm (load)", jp.revati.skyabyss.titan.storm.StormSelfTest::load));
        steps.add(Step.once("storm", 50, jp.revati.skyabyss.titan.storm.StormSelfTest::runSelfTest));
        // 三章「風に逆らう嵐」(予兆 3 拍・小要素・風見の向き・影と巨神の見せ場・余韻)。docs/design/12 の 12 章
        steps.add(Step.once("storm chapter (load)", jp.revati.skyabyss.titan.storm.chapter.StormChapterSelfTest::load));
        steps.add(Step.until("storm chapter (wait)", jp.revati.skyabyss.titan.storm.chapter.StormChapterSelfTest::ready));
        steps.add(Step.once("storm chapter", jp.revati.skyabyss.titan.storm.chapter.StormChapterSelfTest::runSelfTest));
        steps.addAll(jp.revati.skyabyss.titan.storm.chapter.StormChapterSelfTest.siteSteps());
        // 三章の地上 (避雷の柱・焼けた見張り塔・空の石・風の道の旗・雷拾いの原・風読みの崖・竜骨の間の入口の遅れた影)。docs/design/34 の C3。SKYABYSS_SELFTEST_ONLY="ch3 ground"
        steps.addAll(jp.revati.skyabyss.titan.storm.chapter.Ch3GroundSelfTest.steps());
        // 三章の地上の敵 (帯電毛玉・峰の雛・雷拾い・焼けた羽守。P16。21 の 5.1)。SKYABYSS_SELFTEST_ONLY="ch3 mobs"
        steps.addAll(jp.revati.skyabyss.titan.storm.chapter.mob.Ch3MobsSelfTest.steps());
        // 四章の地上 (火除けの薬・炉跡・消えた炉の坑・灰の道の標・煤の窯の跡・敵 4 種・四章を心臓と天の鍵まで通す)。docs/design/34 の C4。SKYABYSS_SELFTEST_ONLY="ch4"
        steps.addAll(jp.revati.skyabyss.story.ch4.Ch4GroundSelfTest.steps());
        steps.add(Step.once("ch5 entrance (load)", jp.revati.skyabyss.story.ch5.AbyssEntranceSelfTest::load));
        steps.add(Step.once("ch5 entrance", 60, jp.revati.skyabyss.story.ch5.AbyssEntranceSelfTest::run));
        steps.add(Step.once("abyss pressure", jp.revati.skyabyss.story.ch5.AbyssPressureSelfTest::run));
        steps.add(Step.once("abyss warden (load)", jp.revati.skyabyss.story.ch5.warden.WardenSelfTest::load));
        steps.add(Step.once("abyss warden", 60, jp.revati.skyabyss.story.ch5.warden.WardenSelfTest::run));
        steps.add(Step.once("abyss depths", 20, jp.revati.skyabyss.story.ch5.depths.DepthsV2SelfTest::run));
        steps.add(Step.once("abyss finds (load)", jp.revati.skyabyss.story.ch5.AbyssFindsSelfTest::load));
        steps.add(Step.once("abyss finds", 60, jp.revati.skyabyss.story.ch5.AbyssFindsSelfTest::run));
        // 深淵の層 (36 の 8 章。野営・三本の道・印・門・主の間・下り・心臓まで歩けるか、印と門、二人の段)。SKYABYSS_SELFTEST_ONLY="abyss layers v2"
        steps.add(Step.once("abyss layers v2 (load)", jp.revati.skyabyss.story.ch5.layers.AbyssLayersV2SelfTest::load));
        steps.add(Step.once("abyss layers v2", 60, jp.revati.skyabyss.story.ch5.layers.AbyssLayersV2SelfTest::run));
        // 深淵 v3 (2026-10-23 の試遊の直し: 帰りの祠・道案内・主の技と段とボスバー・縄張りの主)。SKYABYSS_SELFTEST_ONLY="abyss v3"
        steps.add(Step.once("abyss v3 (load)", jp.revati.skyabyss.story.ch5.layers.AbyssV3SelfTest::load));
        steps.add(Step.once("abyss v3", 60, jp.revati.skyabyss.story.ch5.layers.AbyssV3SelfTest::run));
        // 形 B の層の護りの下り道 (塩・灰。道すじに歩いて届く・一人で送りきる・気力が尽きると前の待ち場から・崩れに呑まれると待ち場 3 から・二人で灰を送る)。
        // SKYABYSS_SELFTEST_ONLY="abyss escort"
        steps.add(Step.once("abyss escort (load)", jp.revati.skyabyss.story.ch5.layers.AbyssEscortSelfTest::load));
        steps.add(Step.once("abyss escort", 60, jp.revati.skyabyss.story.ch5.layers.AbyssEscortSelfTest::run));
        // 番人の悪夢 (段の表・入る条件・段の進み・報酬・保存・閉じたあとの後片付け)。docs/design/36 の 9。SKYABYSS_SELFTEST_ONLY="abyss nightmare"
        steps.add(Step.once("abyss nightmare (load)", jp.revati.skyabyss.story.ch5.warden.WardenSelfTest::load));
        steps.add(Step.once("abyss nightmare", 60, jp.revati.skyabyss.story.ch5.warden.NightmareSelfTest::run));
        steps.add(Step.once("abyss relics", 60, jp.revati.skyabyss.story.ch5.AbyssRelicsSelfTest::run));
        steps.add(Step.once("storm mobs (load)", jp.revati.skyabyss.titan.storm.mob.StormMobSelfTest::load));
        steps.add(Step.until("storm mobs (wait)", jp.revati.skyabyss.titan.storm.mob.StormMobSelfTest::ready));
        steps.add(Step.once("storm mobs", 5, jp.revati.skyabyss.titan.storm.mob.StormMobSelfTest::runSelfTest));
        // 雷翼のレイドの三つの部屋 (SR1 避雷の羽運び・SR2 銅の回路つなぎ・SR3 帯電の連鎖)。SKYABYSS_SELFTEST_ONLY="storm raid"
        steps.add(Step.once("storm raid (load)", jp.revati.skyabyss.trial.raid.storm.StormRaidSelfTest::load));
        steps.add(Step.until("storm raid (wait)", jp.revati.skyabyss.trial.raid.storm.StormRaidSelfTest::ready));
        steps.add(Step.once("storm raid", 5, jp.revati.skyabyss.trial.raid.storm.StormRaidSelfTest::runSelfTest));
        // 二章「引く海」(予兆・潮見の貝・大干潮・小要素)。SKYABYSS_SELFTEST_ONLY=ch2
        steps.addAll(jp.revati.skyabyss.titan.tide.chapter.TideChapterSelfTest.steps());
        // 二章の地上 (34 の C2: 塩の蔵・塩の道・難破船・支度の段 2 本)。SKYABYSS_SELFTEST_ONLY="ch2 ground"
        steps.addAll(jp.revati.skyabyss.story.ch2.Ch2GroundSelfTest.steps());
        // 雷翼の中ボス 雷獣 (背中がまだ無ければ、オーバーワールドの仮の巣で)
        steps.add(Step.once("raiju (load)", jp.revati.skyabyss.titan.storm.raiju.RaijuSelfTest::load));
        steps.add(Step.once("raiju", 200, jp.revati.skyabyss.titan.storm.raiju.RaijuSelfTest::runSelfTest));
        // 雷翼の心臓・雷獣の予告 (Telegraph): 印と当たりが同じ形・予告 0.8 秒以上・安全な床・人数で増える・部屋に収まる。docs/design/12 の 5.4
        steps.addAll(jp.revati.skyabyss.titan.storm.StormTelegraphSelfTest.steps());
        // 余燼の骨組み (ディメンション・熱の API・部屋の箱・ID)。docs/design/13_ember.md の 11章
        steps.addAll(jp.revati.skyabyss.titan.ember.EmberSkeletonSelfTest.steps());
        // 余燼の地上 (巨神・熱の口・戻る先・温石・モデルのデータ)。13 の 2章
        steps.addAll(jp.revati.skyabyss.titan.ember.EmberSelfTest.steps());
        // 四章 (余燼) の予兆・小要素・夜の浮上・余韻 (P4d)。13 の 17 章
        steps.addAll(jp.revati.skyabyss.titan.ember.EmberChapterSelfTest.steps());
        // 余燼の背の敵 (強さ・殻と燃え上がり・蛾の熱・燠虫の転がり・落とす物)。13 の 5.1節
        steps.addAll(jp.revati.skyabyss.titan.ember.mob.EmberMobSelfTest.steps());
        // 余燼の心臓 (熾の底・区画・冷え・10 の技・凍え・報酬・天の鍵の四つ目・撃破)。13 の 5.3節
        steps.addAll(jp.revati.skyabyss.titan.ember.heart.EmberHeartSelfTest.steps());
        steps.add(Step.once("titan end (reset after ember heart)", jp.revati.skyabyss.titan.end.TitanEndSelfTest::resetAll));
        // 余燼の中ボス 灰の鍛え手 (技の時間と傷・怒り・報酬・一人で倒せる数値)。東の石の記録を残すので、余燼の背の試練の検査のあとに置く
        steps.addAll(jp.revati.skyabyss.titan.ember.smith.AshSmithSelfTest.steps());
        // 建築の監査 (階段の向き・段差・埋まり・戸・はしご・地形との関係。docs/design/08)。SKYABYSS_SELFTEST_ONLY="build audit" でこれだけ流せる
        steps.addAll(jp.revati.skyabyss.titan.audit.BuildAuditSelfTest.steps());
        // 領域の仕掛けが本物の心臓の間で壁の中でなく、歩いて届く所にあるか (docs/design/02 の 5.9)。SKYABYSS_SELFTEST_ONLY="domain room"
        steps.addAll(jp.revati.skyabyss.titan.domain.DomainRoomSelfTest.steps());
        steps.addAll(TitanBackSelfTest.trialSteps());
        // 巨神の順路 (入口・段の運び・中ボス・あきらめ・区画)。docs/design/32。背が建ててある巨神だけ世界の検査をする。SKYABYSS_SELFTEST_ONLY="trial route"
        steps.addAll(jp.revati.skyabyss.trial.TrialRouteSelfTest.steps());
        steps.addAll(jp.revati.skyabyss.trial.raid.whale.WhaleRaidSelfTest.steps());
        // 岩鯨のレイドの二つ目の部屋 WR4〜WR6 (直接建てて台本で解く)。SKYABYSS_SELFTEST_ONLY="raid"
        steps.addAll(jp.revati.skyabyss.trial.raid.whale.WhaleRaid2SelfTest.steps());
        // 順路の中ボスの間 (四体。三つ目の部屋のあと区画の中ボスの間へ・一人で倒せる・倒すと心臓の段)。SKYABYSS_SELFTEST_ONLY="raid mid"
        steps.addAll(jp.revati.skyabyss.trial.raid.mid.MidRoomSelfTest.steps());
        // どの部屋も一人で越えられる (登録したレイドの部屋すべてと四体の中ボスの間を、偽の人一人ずつで台本どおりに越える)。SKYABYSS_SELFTEST_ONLY="raid solo"
        steps.addAll(jp.revati.skyabyss.trial.raid.RaidSoloSelfTest.steps());
        // レイドの部屋の碑と心の声と伝承 (十二の部屋)。SKYABYSS_SELFTEST_ONLY="raid lore"
        steps.add(Step.once("raid lore", jp.revati.skyabyss.trial.raid.RaidLoreSelfTest::run));
        // レイドの部屋の報い (素材・共鳴の欠片の人ごとの満額・仲間の割合・預かり・称号の一度だけ・崩れなし・周回の割合・保存)。SKYABYSS_SELFTEST_ONLY="raid rewards"
        steps.add(Step.once("raid rewards", jp.revati.skyabyss.trial.raid.RaidRewardsSelfTest::run));
        // 二人で同じレイドの部屋 (一人が倒れても全滅にしない・二人とも倒れたら全滅・外へ出た人を猶予のあと戻す)。SKYABYSS_SELFTEST_ONLY="raid multi"
        steps.add(Step.once("raid multi", jp.revati.skyabyss.trial.raid.RaidMultiSelfTest::run));
        // 岩鯨のレイドの部屋の飾り (仕掛けを上書きしない・閉じている・費用の上限・床の明るさ)。docs/design/39。SKYABYSS_SELFTEST_ONLY="raid decor whale"
        steps.addAll(jp.revati.skyabyss.trial.raid.WhaleDecorSelfTest.steps());
        // 全部の部屋の床の明るさ (二十四の部屋と四体の中ボスの間。夜でも読める床か)。SKYABYSS_SELFTEST_ONLY="raid light"
        steps.addAll(jp.revati.skyabyss.trial.raid.RaidLightSelfTest.steps());
        // 二人の試遊 (2026-10-22): 敵の無敵の間・背の耐久。SKYABYSS_SELFTEST_ONLY="combat rules"
        steps.addAll(jp.revati.skyabyss.combat.CombatRulesSelfTest.steps());
        // 完全な RPG の決まり (採集・水汲み・倒れたときの既定)。docs/design/40。SKYABYSS_SELFTEST_ONLY="rpg rules"
        steps.addAll(jp.revati.skyabyss.rpg.rules.RpgRulesSelfTest.steps());
        // 天と淵の大陸の地形 (図と、大陸の世界なら本物のチャンク)。docs/design/40 の 3 章。SKYABYSS_SELFTEST_ONLY="continent"
        steps.addAll(jp.revati.skyabyss.world.continent.ContinentSelfTest.steps());
        // 順路の部屋が分かれている (封じを壁として歩く)。SKYABYSS_SELFTEST_ONLY="route rooms"
        steps.addAll(jp.revati.skyabyss.trial.RouteRoomsSelfTest.steps());
        steps.addAll(jp.revati.skyabyss.trial.RoutePartySelfTest.steps());
        steps.addAll(jp.revati.skyabyss.titan.BackBuildsSelfTest.steps());
        // 巨神の祠・歌片・巨神の見え方。docs/design/32 の 3 章。SKYABYSS_SELFTEST_ONLY="trial shrine"
        steps.addAll(jp.revati.skyabyss.titan.shrine.ShrineSelfTest.steps());
        return steps;
    }

    // 段の名前から付く分類 (SKYABYSS_SELFTEST_ONLY="group:<タグ>")。名前の語で決める (段を足したら自然に入る)
    private static final java.util.Map<String, java.util.function.Predicate<String>> GROUPS = new java.util.LinkedHashMap<>();

    static {
        GROUPS.put("raid", n -> n.contains("raid"));
        GROUPS.put("abyss", n -> n.startsWith("abyss") || n.startsWith("ch5"));
        GROUPS.put("ui", n -> has(n, "hud", "lang", "keys", "notice", "tutorial", "guide", "travel map", "ux ", "band", "glow", "cinematic"));
        GROUPS.put("story", n -> has(n, "story", "opening", "chapters", "arc", "prologue", "lore", "npc", "freequest", "ch1", "ch2", "ch3", "ch4", "playtest"));
        GROUPS.put("titan", n -> has(n, "titan", "whale", "tide", "storm", "ember", "young", "heart", "trial", "shrine", "raiju"));
        GROUPS.put("combat", n -> has(n, "combat", "telegraph", "room play", "weapon", "heart", "affix", "gear", "accessory", "skills", "growth"));
        GROUPS.put("backs", n -> n.contains("back") || n.contains("build audit") || n.startsWith("trial"));
        GROUPS.put("mobs", n -> n.contains("mob") || n.contains("enemies"));
        GROUPS.put("world", n -> has(n, "town", "site", "grounds", "grave", "mount", "facility", "artisan", "travel", "spawn", "chapter"));
    }

    private static boolean has(String name, String... words) {
        for (String w : words) {
            if (name.contains(w)) {
                return true;
            }
        }
        return false;
    }

    static boolean matches(String name, String token) {
        if (token.startsWith("group:")) {
            var g = GROUPS.get(token.substring(6).trim().toLowerCase(java.util.Locale.ROOT));
            return g != null && g.test(name.toLowerCase(java.util.Locale.ROOT));
        }
        if (token.startsWith("~")) {
            return name.toLowerCase(java.util.Locale.ROOT).contains(token.substring(1).trim().toLowerCase(java.util.Locale.ROOT));
        }
        return name.startsWith(token);
    }

    static void printCatalog(List<Step> all) {
        StringBuilder sb = new StringBuilder("SELFTEST catalog: ").append(all.size()).append(" steps\n");
        for (Step s : all) {
            sb.append("  STEP ").append(s.name()).append('\n');
        }
        GROUPS.forEach((tag, p) -> sb.append("  GROUP group:").append(tag).append(" = ")
                .append(all.stream().filter(s -> p.test(s.name().toLowerCase(java.util.Locale.ROOT))).count()).append(" steps\n"));
        SkyAbyss.LOGGER.info(sb.toString());
    }

    // SKYABYSS_SELFTEST_LIST=1: ワールドを読む前 (mod の構築時) に段の一覧を出して終わる。ふつうの起動には影響しない
    public static void listAndExitIfRequested() {
        if (System.getenv("SKYABYSS_SELFTEST_LIST") == null) {
            return;
        }
        try {
            List<Step> all = steps();
            StringBuilder sb = new StringBuilder();
            for (Step s : all) {
                sb.append("STEP ").append(s.name()).append('\n');
            }
            GROUPS.forEach((tag, p) -> sb.append("GROUP group:").append(tag).append(" = ")
                    .append(all.stream().filter(s -> p.test(s.name().toLowerCase(java.util.Locale.ROOT))).map(Step::name)
                            .collect(java.util.stream.Collectors.joining(" | ")))
                    .append('\n'));
            java.nio.file.Files.writeString(java.nio.file.Path.of("selftest_steps.txt"), sb.toString());
            System.out.println("SELFTEST LIST\n" + sb);
        } catch (Throwable t) {
            System.out.println("SELFTEST LIST failed: " + t);
        }
        Runtime.getRuntime().halt(0);
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (!enabled()) {
            return;
        }
        QUEUE.clear();
        QUEUE.addAll(steps());
        // SKYABYSS_SELFTEST_ONLY=<指定>[,<指定>…] なら、その段だけ。指定は 名前の頭 / "group:<タグ>" / "~<名前に含まれる語>"
        // (例: "storm back"、"travel gear,artisan"、"group:raid"、"~quest")。何にも合わなければ段の名前の一覧を出して止める。終わるとサーバーを止める
        String only = System.getenv("SKYABYSS_SELFTEST_ONLY");
        // 全部を流したときも、終わればサーバーを止める (前は titan back の段が止めていた。2026-10-22 にその段が止めなくなり、
        // サーバーが残って共有のロックが空かなかった)
        haltWhenDone = true;
        if (only != null && !only.isBlank()) {
            List<Step> all = new ArrayList<>(QUEUE);
            List<Step> kept = new ArrayList<>();
            for (String token : only.split(",")) {
                String t = token.trim();
                if (t.isEmpty()) {
                    continue;
                }
                List<Step> hit = all.stream().filter(s -> matches(s.name(), t)).toList();
                if (hit.isEmpty()) {
                    SkyAbyss.LOGGER.warn("SELFTEST: nothing matches \"{}\" (prefix, group:<tag> or ~<substring>)", t);
                }
                for (Step s : hit) {
                    if (!kept.contains(s)) {
                        kept.add(s);
                    }
                }
            }
            // 元の並びを保つ (指定した順ではなく、全体の順に流す。段どうしの前提があるため)
            QUEUE.clear();
            all.stream().filter(kept::contains).forEach(QUEUE::add);
        }
        // 天と淵の大陸: 出現地点ははじまりの島へ動いたばかりで、まわりのチャンクがまだ動いていないことがある。
        // 出現地点を中心に 5 × 5 チャンクを読み込み、生き物が動くまで待ってから始める (多くの試しは出現地点の上空で敵を立てる。30 秒で諦めて進む)
        if (!QUEUE.isEmpty() && jp.revati.skyabyss.world.continent.ContinentInfo.isContinent(event.getServer())) {
            int[] warm = {0};
            QUEUE.addFirst(Step.until("continent spawn (wait)", server -> {
                var over = server.overworld();
                var spawn = over.getSharedSpawnPos();
                boolean ready = true;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        over.setChunkForced((spawn.getX() >> 4) + dx, (spawn.getZ() >> 4) + dz, true);
                        ready &= over.isPositionEntityTicking(spawn.offset(dx * 16, 0, dz * 16));
                    }
                }
                return ready || ++warm[0] > 600;
            }));
        }
        if (QUEUE.isEmpty()) {
            SkyAbyss.LOGGER.error("SELFTEST FAILED: no step matches \"{}\". Available steps and groups:", only);
            printCatalog(steps());
            event.getServer().halt(false);
            return;
        }
        current = null;
        wait = QUEUE.peek().waitBefore();
        startedAt = System.nanoTime();
        slowestTick = 0;
        SkyAbyss.LOGGER.info("SELFTEST runner: {} steps, one per tick", QUEUE.size());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
        current = null;
    }

    private static boolean haltWhenDone;

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (current == null && QUEUE.isEmpty()) {
            return;
        }
        // ウォッチドッグが見るのは 1 tick の長さではなく「予定の時刻からの遅れ」(MinecraftServer.getNextTickTime)。
        // 遅れは重い tick のたびに積み上がり、サーバーが自分で追いつく (Can't keep up) のは 20 秒に一度だけなので、
        // 重い段のあとは、軽い tick を回して遅れが取り戻されるまで次へ進まない (取り戻すのは数百 tick、実時間で 1 秒もかからない)。
        // 待つ tick (チャンクやエンティティの読み込みを待つ) も、追いついてから数える (追いつく間の tick は一瞬で過ぎるため)
        if (Util.getNanos() - event.getServer().getNextTickTime() > CATCH_UP_NANOS) {
            return;
        }
        if (current == null && wait-- > 0) {
            return;
        }
        if (current == null) {
            current = QUEUE.poll();
            stepTicks = 0;
            stepNanos = 0;
            stepLongest = 0;
        }
        Step step = current;
        long t0 = System.nanoTime();
        boolean done;
        try {
            done = step.action().test(event.getServer());
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST " + step.name() + " crashed", e);
            SkyAbyss.LOGGER.error("SELFTEST {} FAILED: crashed: {}", step.name(), e);
            done = true;
        }
        long took = System.nanoTime() - t0;
        stepTicks++;
        stepNanos += took;
        stepLongest = Math.max(stepLongest, took);
        if (took > slowestTick) {
            slowestTick = took;
            slowestStep = step.name();
        }
        if (!done) {
            return;
        }
        SkyAbyss.LOGGER.info("SELFTEST runner: {} took {} ms in {} tick(s), longest tick {} ms", step.name(), stepNanos / 1_000_000, stepTicks,
                stepLongest / 1_000_000);
        current = null;
        if (QUEUE.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST runner: all steps done in {} s, slowest tick {} ms ({})", (System.nanoTime() - startedAt) / 1_000_000_000L,
                    slowestTick / 1_000_000, slowestStep);
            if (haltWhenDone) {
                releaseForced(event.getServer());
                event.getServer().halt(false);
            }
        } else {
            wait = QUEUE.peek().waitBefore();
        }
    }

    // 止める前に、段が外し忘れた強制読み込みを全部外す (残っていると、止めるときの塊の片づけが終わらないことがあった。
    // 2026-10-22 の全部の流しで「Saving worlds」のあと ChunkMap.scheduleUnload が回り続けた)。外した数を書く
    static void releaseForced(net.minecraft.server.MinecraftServer server) {
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            it.unimi.dsi.fastutil.longs.LongSet forced = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(level.getForcedChunks());
            if (forced.isEmpty()) {
                continue;
            }
            SkyAbyss.LOGGER.warn("SELFTEST runner: {} forced chunk(s) left in {} (first {}), releasing", forced.size(), level.dimension().location(),
                    new net.minecraft.world.level.ChunkPos(forced.iterator().nextLong()));
            forced.forEach(c -> level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false));
        }
    }
}
