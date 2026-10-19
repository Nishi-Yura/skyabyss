package jp.revati.skyabyss.cinematic;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.storm.StormKeeper;
import jp.revati.skyabyss.titan.storm.StormWingEntity;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 巨神の初見の実機に近い確かめ (自己テスト "sight live"): 本物の雷翼を出し、その輪の上を飛ぶあいだ、地上の人が見回りの条件 (CinematicTriggers.run) を
// 1 秒に一度受けて、場面が始まるまでを測る。巨神は人から 250 ブロックの所を中心に周る (出現と同じ 300〜420 の外側から近づく形)
public final class SightLiveSelfTest {
    private static int phase;
    private static int ticks;
    private static FakePlayer player;
    private static ChunkPos forced;
    private static final int LIMIT = 2600;

    private SightLiveSelfTest() {
    }

    public static SelfTestRunner.Step step(String titan) {
        phase = 0;
        return SelfTestRunner.Step.until("sight live " + titan, server -> tick(server, titan));
    }

    private static boolean tick(MinecraftServer server, String titan) {
        ServerLevel level = server.overworld();
        if (phase == 0) {
            player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("5c1e0a2b-0000-4000-8000-0000000d0001"), "[sight_live]"));
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, level.getSharedSpawnPos());
            player.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
            player.setHealth(player.getMaxHealth());
            Cinematics.state(player).forgetAll();
            Cinematics.stop(player, false);
            if (Titans.THUNDER_WING.equals(titan)) {
                StormWingEntity w = StormKeeper.KEEPER.spawn(level, player.getX() + 250, player.getZ(), null);
                SkyAbyss.LOGGER.info("[sightlive] thunder spawned at {} {} {}", (int) w.getX(), (int) w.getY(), (int) w.getZ());
                forceAround(level, w.chunkPosition());
            }
            phase = 1;
            ticks = 0;
            return false;
        }
        ticks++;
        var entity = CinematicTriggers.currentTitan(level, titan);
        if (entity != null) {
            forceAround(level, entity.chunkPosition());
        }
        if (ticks % 20 == 0) {
            boolean started = CinematicTriggers.run(player);
            if (ticks % 100 == 0 || started) {
                SkyAbyss.LOGGER.info("[sightlive] {} t={} titan={} started={}", titan, ticks, entity == null ? "null" : (int) entity.getX() + "," + (int) entity.getY() + "," + (int) entity.getZ(), started);
            }
            if (started) {
                SkyAbyss.LOGGER.info("SELFTEST sight live {} PASSED (scene started after {} ticks)", titan, ticks);
                finish(level);
                return true;
            }
        }
        if (ticks > LIMIT) {
            SkyAbyss.LOGGER.error("SELFTEST sight live {} FAILED: no first-sight scene in {} ticks", titan, LIMIT);
            finish(level);
            return true;
        }
        return false;
    }

    private static void forceAround(ServerLevel level, ChunkPos cp) {
        if (forced != null && forced.equals(cp)) {
            return;
        }
        if (forced != null) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setChunkForced(forced.x + dx, forced.z + dz, false);
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setChunkForced(cp.x + dx, cp.z + dz, true);
            }
        }
        forced = cp;
    }

    private static void finish(ServerLevel level) {
        if (forced != null) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setChunkForced(forced.x + dx, forced.z + dz, false);
                }
            }
            forced = null;
        }
        Cinematics.stop(player, false);
        Cinematics.state(player).forgetAll();
    }
}
