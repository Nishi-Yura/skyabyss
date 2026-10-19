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
import jp.revati.skyabyss.titan.travel.TravelSelfTest;
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
        // 岩鯨の背の拡大 (BackScale・部屋の大きさ・幕・鐘・初回だけの部屋・建て直しで keep.* が残る)。docs/design/26。SKYABYSS_SELFTEST_ONLY="whale scale"
        steps.add(Step.once("whale scale", jp.revati.skyabyss.titan.dungeon.WhaleScaleSelfTest::runSelfTest));
        // グループ (パーティ・一人) の共通部分: 部屋の占有・戦いの参加者・個人の影・乗っている人の数え方・放置の分け前。docs/design/10 の 10 章
        steps.add(Step.once("group", jp.revati.skyabyss.titan.group.GroupSelfTest::runSelfTest));
        // 倒れた参加者の再入場 (20 秒の待ち・回数・全滅でやり直し・ゲームルール)。docs/design/02 の 4.2、10 の 10.4
        steps.add(Step.once("heart reentry", jp.revati.skyabyss.titan.group.ReentrySelfTest::runSelfTest));
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
        steps.add(Step.once("lang objective band", ObjectiveBandSelfTest::runSelfTest));
        steps.add(Step.once("players snapshot", jp.revati.skyabyss.util.PlayersSelfTest::runSelfTest));
        // 目的の道しるべ (矢印の角度・歩ける道の探索・予算・持ち主にだけ・パケット)。docs/design/18 の 8.3 節
        steps.add(Step.once("guide nav", jp.revati.skyabyss.guide.nav.GuideNavSelfTest::runSelfTest));
        steps.add(Step.once("party locator", jp.revati.skyabyss.party.PartyLocatorSelfTest::runSelfTest));
        // 仲間が起こせる仕組み・倒れた回数の札・「ここ」の印。docs/design/10 の 10.12〜10.14
        steps.add(Step.once("party revive", jp.revati.skyabyss.party.ReviveSelfTest::runSelfTest));
        steps.add(Step.once("growth", GrowthSelfTest::runSelfTest));
        steps.add(Step.once("skills", SkillSelfTest::runSelfTest));
        // 画面の知らせ (種類分け・順番待ち)。docs/design/18_notices.md
        steps.add(Step.once("notice", jp.revati.skyabyss.notice.NoticeSelfTest::runSelfTest));
        // HUD の置き場の計算 (倍率 1〜4 × 代表的な窓で重ならない)。docs/design/18 の 14 章
        steps.add(Step.once("hud layout", jp.revati.skyabyss.hud.HudLayoutSelfTest::runSelfTest));
        steps.add(Step.once("tutorial", jp.revati.skyabyss.tutorial.TutorialSelfTest::runSelfTest));
        // 大事な場面のカメラ演出 (台本・日英の文・座標・サーバーの流れ)。docs/design/20_cinematics.md
        steps.add(Step.once("cinematic", jp.revati.skyabyss.cinematic.CinematicSelfTest::runSelfTest));
        steps.add(Step.once("storm scan", jp.revati.skyabyss.titan.storm.back.StormBuildScan::runSelfTest));
        steps.add(jp.revati.skyabyss.cinematic.SightLiveSelfTest.step(jp.revati.skyabyss.progress.Titans.THUNDER_WING));
        steps.add(Step.once("gear curve", GearCurveSelfTest::runSelfTest));
        // 心臓四体の手ごたえ (戦いの長さ・一撃・避けそこね)
        steps.add(Step.once("heart balance", jp.revati.skyabyss.combat.HeartBalanceSelfTest::runSelfTest));
        steps.add(Step.once("affix", AffixSelfTest::runSelfTest));
        // 心臓の遺物の選択 (四体・一度だけ・入り直し・二人・あとで・場面の終わり)。docs/design/30_heart_reward_ui.md。SKYABYSS_SELFTEST_ONLY="heart reward"
        steps.addAll(jp.revati.skyabyss.loot.reward.HeartRewardSelfTest.steps());
        steps.add(Step.once("accessory", AccessorySelfTest::runSelfTest));
        steps.add(Step.once("tide gear", jp.revati.skyabyss.titan.tide.gear.TideGearSelfTest::runSelfTest));
        steps.add(Step.once("storm gear", jp.revati.skyabyss.item.storm.StormGearSelfTest::runSelfTest));
        // 余燼の装備と伝承・天の鍵の締め (13 の 11.2節の担当 G)
        steps.add(Step.once("ember gear", jp.revati.skyabyss.titan.ember.gear.EmberGearSelfTest::runSelfTest));
        steps.add(Step.once("ember lore", jp.revati.skyabyss.titan.ember.lore.EmberLoreSelfTest::runSelfTest));
        steps.add(Step.once("record", RecordSelfTest::runSelfTest));
        // 備えの三灯 (式・色の境目・乗り込みの確認の流れ・前室の囁き・控え帳の行)。docs/design/21 の 1.2、11 の 9 章
        steps.add(Step.once("readiness", jp.revati.skyabyss.readiness.ReadinessSelfTest::runSelfTest));
        // 章の導きの基盤 (備えの頼み・旅の覚書・鼓動守・場所の湧き台と宝・章のひとり歩き・経験値の枠)。docs/design/21 の P3
        steps.addAll(jp.revati.skyabyss.guide.GuideSelfTest.steps());
        // 旅装・砥石・糧と薬 (P5) と旅の職人 (P4)。docs/design/21 の 1.2・1.4
        steps.addAll(jp.revati.skyabyss.guide.TravelSelfTest.steps());
        steps.addAll(jp.revati.skyabyss.npc.ArtisanSelfTest.steps());
        // 倒れたときの決まりと墓 (docs/design/11 の 6 章)
        steps.add(Step.once("grave (load)", jp.revati.skyabyss.grave.GraveSelfTest::load));
        steps.add(Step.once("grave", 40, jp.revati.skyabyss.grave.GraveSelfTest::runSelfTest));
        // 留め袋 (登録の表・保存と同期・入れる/出す・拾う・掃除・倒れても残る・読む側・移し替え)。docs/design/22 の 13 章
        steps.add(Step.once("pouch", jp.revati.skyabyss.pouch.PouchSelfTest::runSelfTest));
        steps.add(Step.once("playtest", 40, jp.revati.skyabyss.playtest.PlaytestSelfTest::runSelfTest));
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
        steps.addAll(ChapterSkySelfTest.steps());
        // 序章・一章の中身 (初日のお使い・旅の跡・歌石・見張りの丘・小さな異変・歩哨・岩鯨の影の出来事・予兆 3 拍)。docs/design/17 の 1.5。岩鯨を出す段より前に置く
        steps.addAll(jp.revati.skyabyss.story.ch1.Ch1SelfTest.steps());
        // 一章の地上の敵 (岩殻虫・棘うさぎ・骨負い・老いた歩哨。P7。21 の 3.1)。SKYABYSS_SELFTEST_ONLY="ch1 mobs"
        steps.addAll(jp.revati.skyabyss.story.ch1.Ch1MobsSelfTest.steps());
        // チャンクを読み込んでから、エンティティが入るのを待って試すもの
        // 序盤の流れの保証 (最初の影・書・岩鯨の門・語り部の野営)。平らな草地を作るので、チャンクを読み込んでから
        steps.add(Step.once("opening beats (load)", jp.revati.skyabyss.opening.OpeningBeatsSelfTest::load));
        steps.add(Step.once("opening beats", 60, jp.revati.skyabyss.opening.OpeningBeatsSelfTest::runSelfTest));
        // 巨神の体に入れない仕組み (体積・通り道・押し出し・矢)。docs/design/02 の「体に入れない」
        steps.add(Step.once("titan solid (load)", jp.revati.skyabyss.titan.solid.TitanSolidSelfTest::load));
        steps.add(Step.once("titan solid", 40, jp.revati.skyabyss.titan.solid.TitanSolidSelfTest::runSelfTest));
        steps.add(Step.once("whale (load)", WhaleKeeper::selfTestLoad));
        steps.add(Step.once("whale", 40, WhaleKeeper::selfTestRun));
        steps.add(Step.once("travel (load)", TravelSelfTest::load));
        steps.add(Step.once("travel", 70, TravelSelfTest::runSelfTest));
        steps.add(Step.until("tide travel (load)", jp.revati.skyabyss.titan.travel.TideTravelSelfTest::load));
        steps.add(Step.once("tide travel", 400, jp.revati.skyabyss.titan.travel.TideTravelSelfTest::runSelfTest));
        // 巨神の呼び (出会った巨神を、いつでも笛で近くへ呼べる)。docs/design/09 の 4.4節。SKYABYSS_SELFTEST_ONLY="titan call"
        steps.add(Step.until("titan call (load)", jp.revati.skyabyss.titan.travel.TitanCallSelfTest::load));
        steps.add(Step.once("titan call", 400, jp.revati.skyabyss.titan.travel.TitanCallSelfTest::runSelfTest));
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
        steps.add(Step.once("titan ground", 10, jp.revati.skyabyss.titan.tide.TitanGroundSelfTest::runSelfTest));
        steps.add(Step.once("tide mobs (load)", TideMobSelfTest::load));
        steps.add(Step.once("tide mobs", 45, TideMobSelfTest::runSelfTest));
        // 潮殻の心臓と中ボス潮招きの予告 (Telegraph): 印と当たりが同じ・予告 0.8 秒以上・安全な床 15% 以上・人数で増える・部屋に収まる。docs/design/08 の 5.5
        steps.addAll(jp.revati.skyabyss.titan.tide.TideTelegraphSelfTest.steps());
        // 雷翼の巨神 (窓・止まり木・昇り風・一体だけ・記録・戻る先・背中の結びつけ)
        steps.add(Step.once("storm (load)", jp.revati.skyabyss.titan.storm.StormSelfTest::load));
        steps.add(Step.once("storm", 50, jp.revati.skyabyss.titan.storm.StormSelfTest::runSelfTest));
        // 雷翼の背 (鳥の形・看板・拍・鏡・柱・歩ける所・リセット)。建てたあと 100 tick 待ってから数える
        steps.addAll(jp.revati.skyabyss.titan.storm.back.StormBackSelfTest.steps());
        // 三章「風に逆らう嵐」(予兆 3 拍・小要素・風見の向き・影と巨神の見せ場・余韻)。docs/design/12 の 12 章
        steps.add(Step.once("storm chapter (load)", jp.revati.skyabyss.titan.storm.chapter.StormChapterSelfTest::load));
        steps.add(Step.until("storm chapter (wait)", jp.revati.skyabyss.titan.storm.chapter.StormChapterSelfTest::ready));
        steps.add(Step.once("storm chapter", jp.revati.skyabyss.titan.storm.chapter.StormChapterSelfTest::runSelfTest));
        steps.addAll(jp.revati.skyabyss.titan.storm.chapter.StormChapterSelfTest.siteSteps());
        steps.add(Step.once("storm mobs (load)", jp.revati.skyabyss.titan.storm.mob.StormMobSelfTest::load));
        steps.add(Step.until("storm mobs (wait)", jp.revati.skyabyss.titan.storm.mob.StormMobSelfTest::ready));
        steps.add(Step.once("storm mobs", 5, jp.revati.skyabyss.titan.storm.mob.StormMobSelfTest::runSelfTest));
        // 潮殻の背の本番 (島・潮の溜め・三つの試練・リセット)。水を流してから数える段がある
        steps.addAll(jp.revati.skyabyss.titan.tide.back.TideBackSelfTest.steps());
        steps.addAll(jp.revati.skyabyss.titan.tide.back.BreathSelfTest.steps());
        steps.addAll(jp.revati.skyabyss.titan.tide.back.PearlKeeperSelfTest.steps());
        // 二章「引く海」(予兆・潮見の貝・大干潮・小要素)。SKYABYSS_SELFTEST_ONLY=ch2
        steps.addAll(jp.revati.skyabyss.titan.tide.chapter.TideChapterSelfTest.steps());
        // 雷翼の中ボス 雷獣 (背中がまだ無ければ、オーバーワールドの仮の巣で)
        steps.add(Step.once("raiju (load)", jp.revati.skyabyss.titan.storm.raiju.RaijuSelfTest::load));
        steps.add(Step.once("raiju", 40, jp.revati.skyabyss.titan.storm.raiju.RaijuSelfTest::runSelfTest));
        // 雷翼の心臓・雷獣の予告 (Telegraph): 印と当たりが同じ形・予告 0.8 秒以上・安全な床・人数で増える・部屋に収まる。docs/design/12 の 5.4
        steps.addAll(jp.revati.skyabyss.titan.storm.StormTelegraphSelfTest.steps());
        // 余燼の骨組み (ディメンション・熱の API・部屋の箱・ID)。docs/design/13_ember.md の 11章
        steps.addAll(jp.revati.skyabyss.titan.ember.EmberSkeletonSelfTest.steps());
        // 余燼の背の体と広間 (体の穴・歩いて行ける所・液体・看板・石と門・リセット・熱)。13 の 18章
        steps.addAll(jp.revati.skyabyss.titan.ember.back.EmberBackSelfTest.steps());
        // 余燼の地上 (巨神・熱の口・戻る先・温石・モデルのデータ)。13 の 2章
        steps.addAll(jp.revati.skyabyss.titan.ember.EmberSelfTest.steps());
        // 四章 (余燼) の予兆・小要素・夜の浮上・余韻 (P4d)。13 の 17 章
        steps.addAll(jp.revati.skyabyss.titan.ember.EmberChapterSelfTest.steps());
        // 余燼の背の敵 (強さ・殻と燃え上がり・蛾の熱・燠虫の転がり・落とす物)。13 の 5.1節
        steps.addAll(jp.revati.skyabyss.titan.ember.mob.EmberMobSelfTest.steps());
        // 余燼の心臓 (熾の底・区画・冷え・10 の技・凍え・報酬・天の鍵の四つ目・撃破)。13 の 5.3節
        steps.addAll(jp.revati.skyabyss.titan.ember.heart.EmberHeartSelfTest.steps());
        steps.add(Step.once("titan end (reset after ember heart)", jp.revati.skyabyss.titan.end.TitanEndSelfTest::resetAll));
        // 余燼の三つの試練 (灰の道・鍛えの間・火守り)。13 の 4 章。建てたあと 100 tick 待ってから検査する
        steps.addAll(jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSelfTest.steps());
        // 余燼の中ボス 灰の鍛え手 (技の時間と傷・怒り・報酬・一人で倒せる数値)。東の石の記録を残すので、余燼の背の試練の検査のあとに置く
        steps.addAll(jp.revati.skyabyss.titan.ember.smith.AshSmithSelfTest.steps());
        // 建築の監査 (階段の向き・段差・埋まり・戸・はしご・地形との関係。docs/design/08)。SKYABYSS_SELFTEST_ONLY="build audit" でこれだけ流せる
        steps.addAll(jp.revati.skyabyss.titan.audit.BuildAuditSelfTest.steps());
        steps.addAll(TitanBackSelfTest.trialSteps());
        // 試練のプール (引き方・修飾・全変種・今週の試練・図鑑・戸・複数グループ)。docs/design/29。背を建て直すので最後。SKYABYSS_SELFTEST_ONLY="trial pool"
        steps.addAll(jp.revati.skyabyss.trial.TrialPoolSelfTest.steps());
        return steps;
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (!enabled()) {
            return;
        }
        QUEUE.clear();
        QUEUE.addAll(steps());
        // SKYABYSS_SELFTEST_ONLY=<名前の頭>[,<名前の頭>…] なら、その段だけ (例: "storm back"、"travel gear,artisan")。終わるとサーバーを止める
        String only = System.getenv("SKYABYSS_SELFTEST_ONLY");
        if (only != null && !only.isBlank()) {
            // カンマ区切りで複数 ("travel gear,artisan")。どれかの頭に合う段を残す
            String[] prefixes = java.util.Arrays.stream(only.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toArray(String[]::new);
            QUEUE.removeIf(s -> java.util.Arrays.stream(prefixes).noneMatch(s.name()::startsWith));
            haltWhenDone = true;
        }
        if (QUEUE.isEmpty()) {
            // 名前の頭に合う段が無いときは、待ち続けずに知らせて止める
            if (haltWhenDone) {
                SkyAbyss.LOGGER.error("SELFTEST FAILED: no step name starts with \"{}\"", only);
                event.getServer().halt(false);
            }
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
                event.getServer().halt(false);
            }
        } else {
            wait = QUEUE.peek().waitBefore();
        }
    }
}
