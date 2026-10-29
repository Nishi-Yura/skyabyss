package jp.revati.skyabyss.story.finale;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 終章の舞台と行き方の自己テスト (docs/design/50 の 4.1)。段: "cloudsea"
//   ディメンションがある / 着く所の島が流し込まれ、印 (着く所の床・門の鐘・帰りの台・橋の台) がある / 遠くは雲の海 (y 36〜39) で、その下は空気 /
//   雲の下へ落ちた人は島へ戻り、体力が減る / 門の条件 (「雲の上へ」の段 ascend から) / 頼みと紙芝居と文がある
public final class FinaleSelfTest {
    private FinaleSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel cloud = server.getLevel(FinaleWorld.LEVEL);
        FakePlayer p = null;
        try {
            check(f, cloud != null, "the cloud sea dimension is missing");
            if (cloud == null) {
                report(f);
                return;
            }
            FinaleWorld.build(cloud);
            check(f, FinaleWorld.built(server), "the arrival island was not recorded as built");
            FinaleTerrain t = FinaleWorld.arrival();
            BlockPos at = t.mark("arrival");
            cloud.getChunk(at);
            check(f, cloud.getBlockState(at.below()).isFaceSturdy(cloud, at.below(), net.minecraft.core.Direction.UP)
                    && cloud.getBlockState(at).isAir() && cloud.getBlockState(at.above()).isAir(), "the arrival spot is not standable: " + cloud.getBlockState(at.below()));
            check(f, cloud.getBlockState(t.mark("gate")).is(Blocks.BELL), "the gate bell is missing: " + cloud.getBlockState(t.mark("gate")));
            check(f, cloud.getBlockState(t.mark("return")).is(Blocks.LODESTONE), "the return stone is missing");
            BlockPos bridge = t.mark("bridge_n");
            check(f, cloud.getBlockState(bridge.below()).isFaceSturdy(cloud, bridge.below(), net.minecraft.core.Direction.UP), "the bridge pier has no floor");
            for (String m : List.of("whale", "tide", "storm", "ember")) {
                check(f, !cloud.getBlockState(t.mark(m)).isAir(), "the " + m + " memorial plinth is missing");
            }
            BlockPos far = new BlockPos(300, 0, 300);
            cloud.getChunk(far);
            check(f, cloud.getBlockState(far.atY(37)).is(FinaleBlocks.CLOUD.get()) && cloud.getBlockState(far.atY(30)).isAir()
                    && cloud.getBlockState(far.atY(41)).isAir(), "the cloud layer is not at y 36-39 with air below");
            // 雲の下へ落ちると戻る
            p = FakePlayerFactory.get(cloud, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000c10d"), "cloud_fall"));
            p.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            p.setHealth(p.getMaxHealth());
            p.moveTo(at.getX() + 0.5, 10, at.getZ() + 0.5, 0f, 0f);
            boolean back = FinaleWorld.rescueCheck(p);
            check(f, back && p.getY() > FinaleWorld.CLOUD_TOP && p.getHealth() < p.getMaxHealth() && p.getHealth() > 0,
                    "a fall under the clouds was not caught: back " + back + ", y " + p.getY() + ", health " + p.getHealth());
            // 門の条件
            Quests.forgetAll(p);
            check(f, !FinaleGate.open(p), "the gate opened without the quest");
            check(f, QuestManager.quest(FinaleGate.QUEST) != null, "quest " + FinaleGate.QUEST + " is missing");
            Quests.start(p, FinaleGate.QUEST);
            QuestLog.Entry e = QuestLog.of(p).entry(FinaleGate.QUEST);
            check(f, e != null && !FinaleGate.open(p), "the gate opened at the verse stage");
            if (e != null) {
                e.stage = 1;
                check(f, FinaleGate.open(p), "the gate did not open at the ascend stage");
            }
            Quests.forgetAll(p);
            check(f, FinaleSelfTest.class.getResource("/assets/skyabyss/story_reels/" + FinaleGate.REEL + ".json") != null, "reel " + FinaleGate.REEL + " is missing");
            isle1(cloud, p, f);
        } catch (RuntimeException ex) {
            SkyAbyss.LOGGER.error("SELFTEST cloudsea crashed", ex);
            f.add("crashed: " + ex);
        } finally {
            if (p != null) {
                p.discard();
            }
        }
        report(f);
    }

    // 島 1「鐘の島」(50 の 4.2.1): 白い橋が途切れない / 鐘雲は鐘で固まり、無音でほどける / 鐘 7 がある / 扉は段 door から
    static void isle1(ServerLevel cloud, FakePlayer p, List<String> f) {
        check(f, FinaleWorld.version(cloud.getServer()) >= 2, "island 1 was not poured (version " + FinaleWorld.version(cloud.getServer()) + ")");
        FinaleTerrain t = BellIsle.terrain();
        BlockPos s = t.mark("bridge_s");
        BlockPos e = t.mark("arrive");
        int gaps = 0;
        for (int z = s.getZ(); z >= e.getZ(); z--) {
            // 一番上の足場 (石板の半分の段も足場) の上に頭の空きが 2 つ
            boolean floor = false;
            for (int y = 80; y >= 60; y--) {
                BlockPos q = new BlockPos(0, y, z);
                cloud.getChunk(q);
                if (!cloud.getBlockState(q).getCollisionShape(cloud, q).isEmpty()) {
                    floor = cloud.getBlockState(q.above()).getCollisionShape(cloud, q.above()).isEmpty()
                            && cloud.getBlockState(q.above(2)).getCollisionShape(cloud, q.above(2)).isEmpty();
                    break;
                }
            }
            gaps += floor ? 0 : 1;
        }
        check(f, gaps == 0, "the white bridge has " + gaps + " gaps between the arrival pier and island 1");
        for (String k : List.of("bell_c", "bell_w", "bell_e", "bell_n", "arena_bell_1", "arena_bell_2", "arena_bell_3", "arena_bell_4")) {
            check(f, cloud.getBlockState(t.mark(k)).is(Blocks.BELL), "bell " + k + " is missing");
        }
        java.util.List<BlockPos> clouds = BellIsle.clouds();
        check(f, clouds.size() > 300, "too few bell clouds: " + clouds.size());
        BellIsle.reset();
        BlockPos bw = t.mark("bell_w");
        BlockPos near = clouds.stream().filter(c -> c.distSqr(bw) <= 14 * 14).findFirst().orElse(null);
        BlockPos far = clouds.stream().filter(c -> c.distSqr(bw) > 40 * 40).findFirst().orElse(null);
        check(f, near != null && far != null, "no bell cloud near / far from the west bell");
        if (near != null && far != null) {
            BellIsle.update(cloud);
            check(f, !cloud.getBlockState(near).getValue(BellCloudBlock.SOLID), "a bell cloud was solid before any bell rang");
            BellIsle.ring(cloud, bw);
            check(f, cloud.getBlockState(near).getValue(BellCloudBlock.SOLID) && !cloud.getBlockState(far).getValue(BellCloudBlock.SOLID),
                    "ringing the west bell did not solidify only the near clouds");
            check(f, BellIsle.silence(cloud, bw, 8) == 1 && !cloud.getBlockState(near).getValue(BellCloudBlock.SOLID), "silence did not undo the west bell");
        }
        BellIsle.reset();
        BellIsle.update(cloud);
        Quests.forgetAll(p);
        check(f, !BellIsle.doorOpen(p), "the bell tower door opened without the quest");
        Quests.start(p, BellIsle.QUEST);
        QuestLog.Entry qe = QuestLog.of(p).entry(BellIsle.QUEST);
        check(f, qe != null && !BellIsle.doorOpen(p), "the door opened before the three bells");
        if (qe != null) {
            qe.stage = 2;
            check(f, BellIsle.doorOpen(p), "the door did not open at the door stage");
        }
        Quests.forgetAll(p);
        // 敵と主 (素材・湧き・主の間の崩れと戻り)
        for (String id : List.of("bell_mourner", "mist_bird", "last_bellkeeper")) {
            check(f, FinaleSelfTest.class.getResource("/assets/skyabyss/animations/entity/" + id + ".animation.json") != null
                    && FinaleSelfTest.class.getResource("/assets/skyabyss/textures/lowpoly/" + id + "_s2.png") != null, "assets of " + id + " are missing");
        }
        BlockPos bc = t.mark("bell_c");
        BellIsle.ring(cloud, bc);
        check(f, BellIsle.ringingNear(cloud, bc.north(3), 10) == 1, "the plaza bell is not counted as ringing");
        jp.revati.skyabyss.story.finale.mob.CloudMob m = BellArena.put(cloud, jp.revati.skyabyss.story.finale.mob.CloudMobsSetup.BELL_MOURNER.get(), t.mark("door_front"));
        check(f, m != null && Math.abs(m.getMaxHealth() - jp.revati.skyabyss.story.finale.mob.BellMourner.HP) < 0.01, "a bell mourner could not be put");
        if (m != null) {
            m.discard();
        }
        BellIsle.reset();
        BellIsle.update(cloud);
        jp.revati.skyabyss.story.finale.mob.LastBellkeeper lord = BellArena.summon(cloud);
        check(f, lord != null && lord.blockPosition().equals(t.mark("lord_spawn")) && lord.getMaxHealth() > 900, "the last bellkeeper did not appear at its spot");
        BlockPos east = t.mark("arena").east(4);
        BellArena.crumble(cloud);
        check(f, cloud.getBlockState(east).getBlock() instanceof BellCloudBlock && cloud.getBlockState(t.mark("arena")).isFaceSturdy(cloud, t.mark("arena"), net.minecraft.core.Direction.UP),
                "the arena floor did not crumble into bell cloud (east half only)");
        BellArena.clear(cloud);
        check(f, BellArena.lords(cloud).isEmpty() && cloud.getBlockState(east).isFaceSturdy(cloud, east, net.minecraft.core.Direction.UP), "clearing the arena did not restore the floor");
        for (String k : List.of("arena_entry", "door_front", "arrive")) {
            BlockPos q = t.mark(k);
            check(f, cloud.getBlockState(q.below()).isFaceSturdy(cloud, q.below(), net.minecraft.core.Direction.UP) && cloud.getBlockState(q).isAir(), k + " is not standable");
        }
    }

    static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST cloudsea PASSED (dimension, arrival island and marks, cloud layer, fall rescue, gate condition, quest and reel; island 1 bridge, bells, bell clouds ring/silence, tower door, foes, lord and arena floor)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST cloudsea FAILED: {}", m));
        }
    }
}
