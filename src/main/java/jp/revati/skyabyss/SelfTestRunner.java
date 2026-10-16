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
        steps.add(Step.once("growth", GrowthSelfTest::runSelfTest));
        steps.add(Step.once("skills", SkillSelfTest::runSelfTest));
        steps.add(Step.once("gear curve", GearCurveSelfTest::runSelfTest));
        steps.add(Step.once("affix", AffixSelfTest::runSelfTest));
        steps.add(Step.once("accessory", AccessorySelfTest::runSelfTest));
        steps.add(Step.once("tide gear", jp.revati.skyabyss.titan.tide.gear.TideGearSelfTest::runSelfTest));
        steps.add(Step.once("storm gear", jp.revati.skyabyss.item.storm.StormGearSelfTest::runSelfTest));
        // 余燼の装備と伝承・天の鍵の締め (13 の 11.2節の担当 G)
        steps.add(Step.once("ember gear", jp.revati.skyabyss.titan.ember.gear.EmberGearSelfTest::runSelfTest));
        steps.add(Step.once("ember lore", jp.revati.skyabyss.titan.ember.lore.EmberLoreSelfTest::runSelfTest));
        steps.add(Step.once("record", RecordSelfTest::runSelfTest));
        steps.add(Step.once("progress", ProgressSelfTest::runSelfTest));
        steps.add(Step.once("lore", LoreSelfTest::runSelfTest));
        steps.add(Step.once("npc", NpcQuestSelfTest::runSelfTest));
        steps.add(Step.once("freequest", FreeQuestSelfTest::runSelfTest));
        // 1 章を 4 体分で (順番・乗る資格・上限・天の鍵・メインクエストの続き)。docs/design/14_beta.md
        steps.add(Step.once("story", jp.revati.skyabyss.story.StorySelfTest::runSelfTest));
        // 雷翼の伝承とクエスト (碑文・囁き・伝承の項目・嵐を渡る翼・背の周回)。docs/design/12_thunder_wing.md の 11.2 節 担当 6
        steps.add(Step.once("storm lore", jp.revati.skyabyss.titan.storm.lore.StormLoreSelfTest::runSelfTest));
        steps.add(Step.once("prologue", PrologueSelfTest::runSelfTest));
        steps.addAll(ChapterSkySelfTest.steps());
        // チャンクを読み込んでから、エンティティが入るのを待って試すもの
        steps.add(Step.once("whale (load)", WhaleKeeper::selfTestLoad));
        steps.add(Step.once("whale", 40, WhaleKeeper::selfTestRun));
        steps.add(Step.once("travel (load)", TravelSelfTest::load));
        steps.add(Step.once("travel", 70, TravelSelfTest::runSelfTest));
        steps.add(Step.until("tide travel (load)", jp.revati.skyabyss.titan.travel.TideTravelSelfTest::load));
        steps.add(Step.once("tide travel", 400, jp.revati.skyabyss.titan.travel.TideTravelSelfTest::runSelfTest));
        steps.add(Step.once("tide_heart (load)", TideHeartSelfTest::load));
        steps.add(Step.once("tide_heart", 30, TideHeartSelfTest::runSelfTest));
        steps.add(Step.once("storm_heart (load)", jp.revati.skyabyss.titan.storm.heart.StormHeartSelfTest::load));
        steps.add(Step.once("storm_heart", 30, jp.revati.skyabyss.titan.storm.heart.StormHeartSelfTest::runSelfTest));
        steps.add(Step.once("storm_heart (light)", 40, jp.revati.skyabyss.titan.storm.heart.StormHeartSelfTest::checkLight));
        steps.add(Step.once("tide (load)", TideSelfTest::load));
        steps.add(Step.once("tide", 50, TideSelfTest::runSelfTest));
        steps.add(Step.once("tide mobs (load)", TideMobSelfTest::load));
        steps.add(Step.once("tide mobs", 45, TideMobSelfTest::runSelfTest));
        // 雷翼の巨神 (窓・止まり木・昇り風・一体だけ・記録・戻る先・背中の結びつけ)
        steps.add(Step.once("storm (load)", jp.revati.skyabyss.titan.storm.StormSelfTest::load));
        steps.add(Step.once("storm", 50, jp.revati.skyabyss.titan.storm.StormSelfTest::runSelfTest));
        // 雷翼の背 (鳥の形・看板・拍・鏡・柱・歩ける所・リセット)。建てたあと 100 tick 待ってから数える
        steps.addAll(jp.revati.skyabyss.titan.storm.back.StormBackSelfTest.steps());
        steps.add(Step.once("storm mobs (load)", jp.revati.skyabyss.titan.storm.mob.StormMobSelfTest::load));
        steps.add(Step.until("storm mobs (wait)", jp.revati.skyabyss.titan.storm.mob.StormMobSelfTest::ready));
        steps.add(Step.once("storm mobs", 5, jp.revati.skyabyss.titan.storm.mob.StormMobSelfTest::runSelfTest));
        // 潮殻の背の本番 (島・潮の溜め・三つの試練・リセット)。水を流してから数える段がある
        steps.addAll(jp.revati.skyabyss.titan.tide.back.TideBackSelfTest.steps());
        // 雷翼の中ボス 雷獣 (背中がまだ無ければ、オーバーワールドの仮の巣で)
        steps.add(Step.once("raiju (load)", jp.revati.skyabyss.titan.storm.raiju.RaijuSelfTest::load));
        steps.add(Step.once("raiju", 40, jp.revati.skyabyss.titan.storm.raiju.RaijuSelfTest::runSelfTest));
        // 余燼の骨組み (ディメンション・熱の API・部屋の箱・ID)。docs/design/13_ember.md の 11章
        steps.addAll(jp.revati.skyabyss.titan.ember.EmberSkeletonSelfTest.steps());
        // 余燼の背の体と広間 (体の穴・歩いて行ける所・液体・看板・石と門・リセット・熱)。13 の 18章
        steps.addAll(jp.revati.skyabyss.titan.ember.back.EmberBackSelfTest.steps());
        // 余燼の地上 (巨神・熱の口・戻る先・温石・モデルのデータ)。13 の 2章
        steps.addAll(jp.revati.skyabyss.titan.ember.EmberSelfTest.steps());
        // 余燼の背の敵 (強さ・殻と燃え上がり・蛾の熱・燠虫の転がり・落とす物)。13 の 5.1節
        steps.addAll(jp.revati.skyabyss.titan.ember.mob.EmberMobSelfTest.steps());
<<<<<<< HEAD
        // 余燼の三つの試練 (灰の道・鍛えの間・火守り)。13 の 4 章。建てたあと 100 tick 待ってから検査する
        steps.addAll(jp.revati.skyabyss.titan.ember.back.trial.EmberTrialSelfTest.steps());
=======
        // 余燼の中ボス 灰の鍛え手 (技の時間と傷・怒り・報酬・一人で倒せる数値)。東の石の記録を残すので、余燼の背の試練の検査のあとに置く
        steps.addAll(jp.revati.skyabyss.titan.ember.smith.AshSmithSelfTest.steps());
>>>>>>> m1-sky-pass
        steps.addAll(TitanBackSelfTest.trialSteps());
        return steps;
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (!enabled()) {
            return;
        }
        QUEUE.clear();
        QUEUE.addAll(steps());
        // SKYABYSS_SELFTEST_ONLY=<名前の頭> なら、その段だけ (例: "storm back")。終わるとサーバーを止める
        String only = System.getenv("SKYABYSS_SELFTEST_ONLY");
        if (only != null && !only.isBlank()) {
            QUEUE.removeIf(s -> !s.name().startsWith(only));
            haltWhenDone = true;
        }
        if (QUEUE.isEmpty()) {
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
