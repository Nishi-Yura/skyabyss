package jp.revati.skyabyss.titan.tide.heart;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.titan.group.BackGroupData;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.tide.TideTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

// 潮殻の心臓の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST tide_heart PASSED / FAILED (サーバーを止めるのは TitanBackSelfTest)。
// 部屋の形と満ち潮、戦いの始まり・封印・部屋の外へ出た心臓を戻す・放棄で戻す、段階と咆哮の無敵・段階2 の 1.2 倍・人数の倍率、
// 9 つの技が最後まで回る、ボスバーの進みとテクスチャ・模型があるか、保存されない、報酬 (天井・図鑑・欠片は一人一回・初回の選択・
// 巨神ごとの記録・6.4節の模擬)、撃破で進みの記録 (潮殻を止めた) と、もう一度挑めるようにする。
// 戦いと撃破はグループごと: 心臓はグループの印を持ち、ほかのグループには見えず狙わない。A の撃破は B の記録に触れない
public final class TideHeartSelfTest {

    private TideHeartSelfTest() {
    }

    // 呼ぶのは SelfTestRunner: 読み込み (load) → 決まった tick 待つ → 試す (runSelfTest)
    public static void load(MinecraftServer server) {
        ServerLevel back = server.getLevel(TideTravel.TIDE_BACK);
        if (back != null) {
            for (int dx = -2; dx <= 1; dx++) {
                for (int dz = -2; dz <= 1; dz++) {
                    back.setChunkForced(dx, dz, true);
                }
            }
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            run(server, failures, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST tide_heart crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST tide_heart PASSED ({})", String.join("; ", notes));
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST tide_heart FAILED: {}", f));
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    private static void run(MinecraftServer server, List<String> failures, List<String> notes) {
        ServerLevel back = server.getLevel(TideTravel.TIDE_BACK);
        if (back == null) {
            failures.add("tide_back is missing");
            return;
        }
        checkAssets(failures);
        // 部屋は本番の島の殻の底 (中心 0, -30)。人がいないので、部屋のチャンクを読み込んだままにする
        for (int dx = -2; dx <= 1; dx++) {
            for (int dz = -3; dz <= 0; dz++) {
                back.setChunkForced(dx, dz, true);
            }
        }
        checkArena(back, failures);
        checkFlood(back, failures);
        checkBattle(back, failures, notes);
        checkLoot(server, failures, notes);
        checkDefeat(server, back, failures);
    }

    private static void checkAssets(List<String> failures) {
        List<String> paths = new ArrayList<>(List.of("/assets/skyabyss/animations/entity/tide_heart.animation.json"));
        for (int n = 1; n <= TideHeart.PHASE_COUNT; n++) {
            paths.add("/assets/skyabyss/geo/entity/tide_heart_p" + n + ".geo.json");
            paths.add("/assets/skyabyss/textures/entity/tide_heart_p" + n + ".png");
            paths.add("/assets/skyabyss/textures/entity/tide_heart_p" + n + "_glowmask.png");
            paths.add("/assets/skyabyss/textures/gui/boss/tide_heart_frame_p" + n + ".png");
            paths.add("/assets/skyabyss/textures/gui/boss/tide_heart_icon_p" + n + ".png");
        }
        for (String s : List.of("beat", "roar", "breath", "shell", "whirl", "flood", "spear", "bubble", "pearl", "ebb", "wave", "rain", "death")) {
            paths.add("/assets/skyabyss/sounds/tide_heart/" + s + ".ogg");
        }
        for (String p : paths) {
            try (InputStream in = TideHeartSelfTest.class.getResourceAsStream(p)) {
                check(in != null, "missing asset " + p, failures);
            } catch (java.io.IOException e) {
                failures.add("could not read " + p);
            }
        }
        check(TideHeart.bagFor(0).length == 3 && TideHeart.bagFor(1).length == 3 && TideHeart.bagFor(2).length == 3, "each phase should have 3 moves", failures);
    }

    private static void checkArena(ServerLevel back, List<String> failures) {
        jp.revati.skyabyss.titan.tide.TideBackBuilder.ensureBuilt(back);
        TideHeartArena.build(back);
        check(TideHeartArena.isBuilt(back), "chamber not recognised as built", failures);
        int f = TideHeartArena.FLOOR;
        int o = TideHeartArena.ISLAND_OFFSET;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                BlockPos top = new BlockPos(sx * o, f + 1, TideHeartArena.CZ + sz * o);
                check(!back.getBlockState(top).isAir() && back.getBlockState(top.above()).isAir(), "rock island missing or covered at " + top, failures);
            }
        }
        check(back.getBlockState(new BlockPos(13, f - 1, TideHeartArena.CZ + 0)).is(Blocks.WATER), "groove has no water", failures);
        check(back.getBlockState(new BlockPos(3, f, TideHeartArena.CZ + 3)).isAir() && back.getBlockState(new BlockPos(3, TideHeartArena.CEILING - 1, TideHeartArena.CZ + 3)).isAir(),
                "chamber interior is not open", failures);
        check(!back.getBlockState(new BlockPos(0, TideHeartArena.CEILING, TideHeartArena.CZ + 0)).isAir(), "chamber has no ceiling", failures);
        // 入口はいつも扉のブロックで、ふだんは既定で開いている (戦いの間だけ閉じる)
        for (BlockPos g : TideHeartArena.gate()) {
            check(back.getBlockState(g).is(ModBlocks.SEALED_GATE.get()) && back.getBlockState(g).getValue(jp.revati.skyabyss.titan.dungeon.SealedGateBlock.OPEN),
                    "entrance is not an open gate at " + g, failures);
        }
        check(TideHeartArena.ante().contains(TideHeartArena.anteSpawn()), "antechamber spawn is outside the antechamber", failures);
        check(!TideHeartArena.arena().contains(TideHeartArena.anteSpawn()), "antechamber spawn is inside the fight", failures);
        // 前室の南の口は、休み場の小部屋と螺旋の階段へ開いている (本番の島)
        check(back.getBlockState(new BlockPos(0, f, TideHeartArena.CZ + 21)).isAir(), "antechamber has no way out to the stairs", failures);
        check(TideHeartArena.onIsland(o + 0.5, f + 2, TideHeartArena.CZ + o + 0.5) && !TideHeartArena.onIsland(3.5, f, TideHeartArena.CZ + 3.5), "island test is wrong", failures);
        check(TideHeartArena.arena().contains(TideHeartArena.heartHome()), "heart home outside the arena", failures);
    }

    private static void checkFlood(ServerLevel back, List<String> failures) {
        int f = TideHeartArena.FLOOR;
        int o = TideHeartArena.ISLAND_OFFSET;
        TideHeartArena.startFlood(back, 30);
        for (int i = 0; i < 21; i++) {
            TideHeartArena.tickFlood(back);
        }
        check(TideHeartArena.flooded(), "floor is not flooded after filling", failures);
        check(back.getBlockState(new BlockPos(3, f, TideHeartArena.CZ + 3)).is(Blocks.WATER) && back.getBlockState(new BlockPos(-10, f, TideHeartArena.CZ + 2)).is(Blocks.WATER),
                "flood did not cover the floor", failures);
        check(back.getBlockState(new BlockPos(o, f + 2, TideHeartArena.CZ + o)).isAir(), "flood wet the island top", failures);
        for (int i = 0; i < 30 + 13; i++) {
            TideHeartArena.tickFlood(back);
        }
        check(!TideHeartArena.flooding(), "flood did not end", failures);
        check(back.getBlockState(new BlockPos(3, f, TideHeartArena.CZ + 3)).isAir() && back.getBlockState(new BlockPos(-10, f, TideHeartArena.CZ + 2)).isAir(), "flood did not drain", failures);
        check(back.getBlockState(new BlockPos(13, f - 1, TideHeartArena.CZ + 0)).is(Blocks.WATER), "draining emptied the groove", failures);
    }

    // テストのグループ (一人のグループの鍵)。心臓の戦いと撃破の記録はグループごと
    static final UUID GROUP_A = UUID.fromString("7e1de0c4-0000-4000-8000-0000000000a1");
    static final UUID GROUP_B = UUID.fromString("7e1de0c4-0000-4000-8000-0000000000b1");

    private static TideHeart startHeart(ServerLevel back, List<String> failures) {
        TideHeartBattle.abandon(back, GROUP_A);
        jp.revati.skyabyss.titan.tide.back.TideBackTrials.resetGroup(back, GROUP_A, false, null, true);
        check(TideHeartBattle.startNow(back, GROUP_A), "battle did not start", failures);
        TideHeart heart = TideHeartBattle.current(back, GROUP_A);
        if (heart == null) {
            failures.add("no heart after starting (difficulty " + back.getDifficulty() + ", hearts nearby "
                    + back.getEntitiesOfClass(TideHeart.class, TideHeartArena.arena().inflate(8)).size() + ", chunk loaded "
                    + back.isLoaded(net.minecraft.core.BlockPos.containing(TideHeartArena.heartHome())) + ")");
        } else {
            heart.skipIntro();
        }
        return heart;
    }

    // 倒れる演出 (100 tick) を最後まで流す。撃破の報告は演出が終わってから出る
    private static void finishDeath(TideHeart heart) {
        for (int i = 0; i < jp.revati.skyabyss.entity.HeartBoss.DEATH_TICKS + 5 && !heart.isRemoved(); i++) {
            heart.tick();
        }
    }

    private static void tickHeart(TideHeart heart, int ticks) {
        for (int i = 0; i < ticks && heart.isAlive(); i++) {
            heart.tick();
        }
    }

    private static void checkBattle(ServerLevel back, List<String> failures, List<String> notes) {
        TideHeart heart = startHeart(back, failures);
        if (heart == null) {
            return;
        }
        check(TideHeartArena.sealed(back), "entrance not sealed when the fight starts", failures);
        check(heart.getTags().contains(TitanBackTrials.HEART_TAG) && heart.isBound(), "heart not tagged / bound", failures);
        check(GROUP_A.equals(heart.groupId()) && GROUP_A.equals(TideHeartBattle.owner()), "the heart is not group A's / the shell bottom is not held by A", failures);
        // 人数の倍率: パーティは 6 人まで (1 + 0.6 × 5)
        check(Math.abs(TideHeart.scaleFor(jp.revati.skyabyss.party.Parties.MAX_MEMBERS) - 4.0f) < 1e-4, "heart scale for 6 is wrong", failures);
        // A の心臓は B には見えず、B を狙わない
        net.neoforged.neoforge.common.util.FakePlayer b = net.neoforged.neoforge.common.util.FakePlayerFactory.get(back,
                new com.mojang.authlib.GameProfile(GROUP_B, "[TideHeartB]"));
        check(!jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(heart, b) && !jp.revati.skyabyss.titan.group.GroupOwned.mayTarget(heart, b),
                "group A's heart is visible to / targets group B", failures);
        // 殻の底は A が持っている: B が持とうとしても、A の戦いの間は TideHeartBattle の占有のまま (持ち主は A)
        check(TideHeartBattle.room(GROUP_B).bossId() == null, "group B has a heart while A fights", failures);
        check(!heart.shouldBeSaved(), "heart would be saved to disk", failures);
        check(!new TideBubble(TideHeartSetup.TIDE_BUBBLE.get(), back).shouldBeSaved()
                && !new TidePearl(TideHeartSetup.TIDE_PEARL.get(), back).shouldBeSaved(), "bubble or pearl would be saved", failures);
        check(Math.abs(heart.getMaxHealth() - TideHeart.MAX_HEALTH) < 0.01, "max health is " + heart.getMaxHealth(), failures);
        // 段階の境目と受ける傷
        check(TideHeart.phaseFor(1f) == 0 && TideHeart.phaseFor(0.67f) == 0 && TideHeart.phaseFor(0.65f) == 1
                && TideHeart.phaseFor(0.34f) == 1 && TideHeart.phaseFor(0.32f) == 2, "phase thresholds are wrong", failures);
        check(Math.abs(TideHeart.takenFor(1, 10, 1) - 12f) < 1e-4 && Math.abs(TideHeart.takenFor(0, 10, 1) - 10f) < 1e-4
                && Math.abs(TideHeart.takenFor(2, 16, TideHeart.scaleFor(2)) - 10f) < 1e-4, "damage taken multipliers are wrong", failures);
        // 咆哮: 体力を 60% にすると、咆哮 (無敵) のあと段階 2
        heart.setHealth(heart.getMaxHealth() * 0.6f);
        tickHeart(heart, 3);
        check(heart.isRoaring() && heart.currentAction() == TideHeart.Action.ROAR, "no roar after dropping to 60%", failures);
        float before = heart.getHealth();
        heart.hurt(heart.damageSources().generic(), 50f);
        check(heart.getHealth() == before, "heart took damage while roaring", failures);
        heart.invulnerableTime = 0;
        tickHeart(heart, TideHeart.ROAR_TICKS + 2);
        check(heart.getPhase() == 1 && !heart.isRoaring(), "phase did not advance after the roar (phase " + heart.getPhase() + ")", failures);
        check(Math.abs(heart.bossBarProgress() - heart.getHealth() / heart.getMaxHealth()) < 0.01, "boss bar progress does not follow health", failures);
        heart.invulnerableTime = 0;
        float h0 = heart.getHealth();
        heart.hurt(heart.damageSources().magic(), 10f);
        check(Math.abs((h0 - heart.getHealth()) - 12f) < 0.6f, "open shell should take 1.2x (took " + (h0 - heart.getHealth()) + ")", failures);
        // 9 つの技が最後まで回る (人がいないので当たらない)
        int ran = 0;
        for (int phase = 0; phase < 3; phase++) {
            heart.forcePhase(phase);
            for (TideHeart.Action a : TideHeart.bagFor(phase)) {
                heart.forceAction(a);
                tickHeart(heart, 200);
                if (heart.currentAction() == TideHeart.Action.NONE) {
                    ran++;
                } else {
                    failures.add(a + " did not finish");
                }
            }
        }
        TideHeartArena.drainNow(back);
        notes.add(ran + "/9 moves ran to the end");
        // 部屋の外へ出た心臓は戻る。誰もいなければ 3 秒で放棄
        heart.moveTo(0.5, TideHeartArena.FLOOR + 1, -12.5);
        TideHeartBattle.room(GROUP_A).tick(back, TideHeartBattle.HOST);
        check(heart.distanceToSqr(TideHeartArena.heartHome()) < 1, "heart outside the room was not brought back", failures);
        for (int i = 0; i < 6; i++) {
            TideHeartBattle.room(GROUP_A).tick(back, TideHeartBattle.HOST);
        }
        check(!TideHeartBattle.room(GROUP_A).active() && !heart.isAlive() && !TideHeartArena.sealed(back), "empty room was not abandoned", failures);
    }

    private static void checkLoot(MinecraftServer server, List<String> failures, List<String> notes) {
        RandomSource random = RandomSource.create(42);
        TitanMemoryData.Record r = new TitanMemoryData.Record();
        TideLoot.Roll first = TideLoot.rollHeart(r, random, id -> true);
        check(first.draws().stream().anyMatch(d -> d.id().equals(TideLoot.CORE)), "first clear has no core", failures);
        check(first.draws().stream().anyMatch(d -> d.id().equals(TideLoot.SHARD) && d.count() >= 4 && d.count() <= 7), "shards not 4-7", failures);
        check(TideLoot.collection().size() == 10, "collection should have 10 items", failures);
        // ハード天井: 記憶 4 から撃破で 5 になり、未所持を一つ保証
        TitanMemoryData.Record p = new TitanMemoryData.Record();
        p.memory = TitanLoot.HARD_PITY - 1;
        TideLoot.Roll pity = TideLoot.rollHeart(p, random, id -> true);
        check(pity.guaranteed() != null && p.memory == 0, "hard pity did not guarantee a new piece", failures);
        TitanMemoryData.Record all = new TitanMemoryData.Record();
        all.owned.addAll(TideLoot.collection());
        all.memory = TitanLoot.HARD_PITY - 1;
        TideLoot.Roll full = TideLoot.rollHeart(all, random, id -> true);
        check(full.pityShards() && all.memory == 0, "full collection pity should give shards", failures);
        // 作られていない品は出さない
        TitanMemoryData.Record none = new TitanMemoryData.Record();
        for (int i = 0; i < 30; i++) {
            TideLoot.rollHeart(none, random, id -> false);
        }
        check(none.owned.isEmpty(), "unmade items were recorded", failures);
        // 6.4節の模擬 (潮招きなしで平均 16 前後)
        double mean = TideLoot.meanClearsToComplete(4000, 7, id -> true);
        check(mean > 13 && mean < 20, "mean clears to complete the collection is " + mean, failures);
        notes.add(String.format("collection in %.1f clears on average", mean));
        // 撃破の報酬 (いない人の分は預かる)。欠片は一人一回。巨神ごとの記録
        UUID fake = UUID.randomUUID();
        TitanMemoryData memory = TitanMemoryData.get(server);
        ServerLevel back = server.getLevel(TideTravel.TIDE_BACK);
        boolean worldBefore = ProgressData.get(server).worldStopped(TideLoot.TITAN);
        TideLoot.onHeartDefeat(back, List.of(), List.of(fake));
        TitanMemoryData.Record rec = memory.peek(fake, TideLoot.TITAN);
        check(rec != null && rec.clears == 1 && rec.fragment && rec.firstCleared && rec.choicePending, "defeat record is wrong", failures);
        check(memory.peek(fake, TitanLoot.STONE_WHALE) == null, "tide defeat wrote to the stone whale record", failures);
        check(memory.hasPending(fake), "rewards for an absent player were not held", failures);
        TitanMemoryData.Pending held = memory.takePending(fake);
        check(TideLoot.registered(TideLoot.SHARD) && TideLoot.registered(TideLoot.CLAM), "tide shell shard / dried shell are not registered", failures);
        check(held.items.stream().anyMatch(s -> TitanLoot.id(s.getItem()).equals(TideLoot.SHARD) && s.getCount() >= 4),
                "heart reward did not give tide shell shards", failures);
        long fragments = held.items.stream().filter(s -> TitanLoot.id(s.getItem()).equals(TideLoot.SKY_KEY_FRAGMENT)).count();
        check(fragments == 1, "first defeat should give one sky key fragment (" + fragments + ")", failures);
        TideLoot.onHeartDefeat(back, List.of(), List.of(fake));
        held = memory.takePending(fake);
        check(held.items.stream().noneMatch(s -> TitanLoot.id(s.getItem()).equals(TideLoot.SKY_KEY_FRAGMENT)), "second defeat gave another fragment", failures);
        // 進みの記録 (潮殻を止めた): 次の巨神の出現と、乗る資格の元
        check(Progression.stopped(server, fake, TideLoot.TITAN), "defeat did not record the tide shell as stopped", failures);
        check(ProgressData.get(server).worldStopped(TideLoot.TITAN), "world progress did not record the tide shell", failures);
        ProgressData.get(server).clearPersonal(fake, TideLoot.TITAN);
        if (!worldBefore) {
            ProgressData.get(server).clearWorld(TideLoot.TITAN);
        }
    }

    private static void checkDefeat(MinecraftServer server, ServerLevel back, List<String> failures) {
        boolean worldBefore = ProgressData.get(server).worldStopped(TideLoot.TITAN);
        TideHeart heart = startHeart(back, failures);
        if (heart == null) {
            return;
        }
        heart.forcePhase(2);
        heart.setHealth(1f);
        heart.invulnerableTime = 0;
        BackGroupData groups = BackGroupData.get(back);
        BackGroupData.Record b = groups.of(GROUP_B);
        int epochB = b.epoch();
        heart.hurt(heart.damageSources().genericKill(), 1000f);
        BackGroupData.Record a = groups.of(GROUP_A);
        check(!heart.isAlive(), "heart did not die", failures);
        check(!a.isHeartDefeated() && TideHeartBattle.room(GROUP_A).active(), "defeat was reported before the death animation finished", failures);
        finishDeath(heart);
        check(heart.isRemoved(), "heart was not removed after the death animation", failures);
        check(a.isHeartDefeated(), "defeat was not recorded for group A", failures);
        // ほかのグループの記録には触れない
        check(!b.isHeartDefeated() && b.epoch() == epochB, "group A's kill changed group B's record", failures);
        check(jp.revati.skyabyss.titan.tide.back.TideBackState.of(back, GROUP_A).everDefeated()
                && !jp.revati.skyabyss.titan.tide.back.TideBackState.of(back, GROUP_B).everDefeated(), "ever-defeated is not per group", failures);
        check(!TideHeartBattle.room(GROUP_A).active() && !TideHeartArena.sealed(back), "entrance did not reopen after the defeat", failures);
        check(TideHeartArena.gate().stream().allMatch(g -> back.getBlockState(g).is(ModBlocks.SEALED_GATE.get())
                && back.getBlockState(g).getValue(jp.revati.skyabyss.titan.dungeon.SealedGateBlock.OPEN)), "the entrance is not an open gate after the defeat", failures);
        // そのグループの試練ごと戻すと、もう一度挑める
        jp.revati.skyabyss.titan.tide.back.TideBackTrials.resetGroup(back, GROUP_A, false, null, true);
        check(!groups.of(GROUP_A).isHeartDefeated(), "reset did not let group A fight the heart again", failures);
        TideHeartBattle.releaseAll(back);
        groups.remove(GROUP_A);
        groups.remove(GROUP_B);
        if (!worldBefore) {
            ProgressData.get(server).clearWorld(TideLoot.TITAN);
        }
    }
}
