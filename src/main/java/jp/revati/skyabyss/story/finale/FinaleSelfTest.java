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

    static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST cloudsea PASSED (dimension, arrival island and marks, cloud layer, fall rescue, gate condition, quest and reel)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST cloudsea FAILED: {}", m));
        }
    }
}
