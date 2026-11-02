package jp.revati.skyabyss.trial.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

// 試練をそろえる共通の決まり (RaidPace)。SKYABYSS_SELFTEST_ONLY="raid pace"
public final class RaidPaceSelfTest {
    private RaidPaceSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        RaidRooms.ensureLoaded();
        ServerLevel level = server.overworld();
        // 時計のない謎解き (WR2) と闘 (WR3): 決まりの時間、全滅で延びる
        RaidRoom wit = RaidRooms.create("WR2");
        RaidRoom fight = RaidRooms.create("WR3");
        if (wit == null || fight == null) {
            f.add("WR2 / WR3 are not registered");
        } else {
            RaidCtx ctx = RaidCtx.forTest(level, UUID.randomUUID(), "stone_whale", "WR2", 0, 2, 1L);
            RaidRun.Run run = new RaidRun.Run(wit, ctx, 2, false);
            if (RaidPace.limit(run) != RaidPace.LIMIT_WALK_WIT) {
                f.add("a wit room without a clock should get " + RaidPace.LIMIT_WALK_WIT + " ticks (" + RaidPace.limit(run) + ")");
            }
            run.wipes = 2;
            if (RaidPace.limit(run) != RaidPace.LIMIT_WALK_WIT + 2 * RaidPace.EXTRA_PER_WIPE) {
                f.add("each wipe should add a minute");
            }
            RaidRun.Run fr = new RaidRun.Run(fight, RaidCtx.forTest(level, UUID.randomUUID(), "stone_whale", "WR3", 0, 3, 1L), 3, false);
            if (RaidPace.limit(fr) != RaidPace.LIMIT_FIGHT) {
                f.add("a fight room should get " + RaidPace.LIMIT_FIGHT + " ticks (" + RaidPace.limit(fr) + ")");
            }
            RaidRun.Run mid = new RaidRun.Run(fight, ctx, 4, true);
            if (RaidPace.limit(mid) != -1) {
                f.add("the mid-boss hall should keep no generic limit");
            }
        }
        // 救い: 全滅 2 回で体力 x0.76
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z != null) {
            float base = z.getMaxHealth();
            RaidPace.assist(z, 2);
            if (Math.abs(z.getMaxHealth() - base * (1 - 2 * RaidPace.ASSIST_STEP)) > 0.01) {
                f.add("assist after two wipes should leave " + (1 - 2 * RaidPace.ASSIST_STEP) + " of the health (" + z.getMaxHealth() + " / " + base + ")");
            }
            z.discard();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raid pace PASSED (time limits by slot, +1 min per wipe, none for the mid-boss, assist x{} per wipe)", 1 - RaidPace.ASSIST_STEP);
        } else {
            f.forEach(s -> SkyAbyss.LOGGER.error("SELFTEST raid pace FAILED: {}", s));
        }
    }
}
