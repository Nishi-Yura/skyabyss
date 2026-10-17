package jp.revati.skyabyss.prologue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 序章の演出の自己テスト。環境変数 SKYABYSS_SELFTEST=1 で専用サーバーを起動したときだけ動く (サーバーはほかのテストが止める)。
// プレイヤーなしで確かめられることを確かめる:
// 段階と間隔の数値、記録の保存と読み込み、パケットの往復、消え方の記録への反映、
// 空中に作った足場での立ち位置選び (距離・向き・地面・見通し・壁で囲むと選ばない・水や葉の上に立たない)、
// 近くの動物がそちらを向いて止まること
public final class PrologueSelfTest {
    // 足場を作る場所 (岩鯨の自己テストの場所 4000,4000 とは離す)
    private static final int CX = -4000;
    private static final int CZ = 4000;
    private static final int Y = 300;
    private static final int RADIUS = 28;

    private PrologueSelfTest() {
    }

    // 呼ぶのは SelfTestRunner (SKYABYSS_SELFTEST=1 のとき、1 tick に 1 系統ずつ)
    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        try {
            checkRegistry(failures);
            checkStages(failures);
            checkGaps(server.overworld(), failures);
            checkData(server, failures);
            checkPayloads(server, failures);
            checkOutcomes(failures);
            checkSpots(server.overworld(), failures);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST prologue crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST prologue PASSED (stages, gaps, data, payloads, outcomes, spot finder, animals)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST prologue FAILED: {}", f));
        }
    }

    private static void checkRegistry(List<String> failures) {
        EntityType<WatcherEntity> type = PrologueRegistry.WATCHER.get();
        if (type.canSummon()) {
            failures.add("the watcher can be summoned");
        }
        if (type.canSerialize()) {
            failures.add("the watcher would be saved");
        }
        if (PrologueRegistry.WATCHER_PRESENCE.get() == null || PrologueRegistry.WATCHER_FADE.get() == null) {
            failures.add("watcher sounds are not registered");
        }
    }

    private static void checkStages(List<String> failures) {
        int[][] expect = {{0, 0}, {1, 1}, {2, 1}, {3, 2}, {50, 2}};
        for (int[] e : expect) {
            if (WatcherStage.of(e[0], 0f).index() != e[1]) {
                failures.add("stage for " + e[0] + " sightings is " + WatcherStage.of(e[0], 0f).index() + ", expected " + e[1]);
            }
        }
        if (WatcherStage.of(0, 0.6f).index() != 1 || WatcherStage.of(5, 1f).index() != 2) {
            failures.add("a near moon does not advance the stage by one (capped)");
        }
        // 1 章以降も出るが、段階 2 より近くならない (00 の 2 = B)
        for (int chapter = 1; chapter <= 3; chapter++) {
            if (WatcherStage.of(50, 1f, chapter).index() != WatcherStage.CHAPTER_CAP
                    || WatcherStage.of(0, 0f, chapter).index() != 0) {
                failures.add("chapter " + chapter + " does not keep the watcher between stage 0 and the cap");
            }
        }
        WatcherStage prev = null;
        for (WatcherStage s : WatcherStage.STAGES) {
            if (s.minDistance() < 40 || s.maxDistance() > 70 || s.minDistance() >= s.maxDistance()) {
                failures.add("stage " + s.index() + " distance out of 40..70: " + s);
            }
            if (prev != null && (s.minDistance() > prev.minDistance() || s.lifetimeTicks() < prev.lifetimeTicks())) {
                failures.add("stage " + s.index() + " is not closer/longer than the previous one");
            }
            prev = s;
        }
    }

    private static void checkGaps(ServerLevel level, List<String> failures) {
        for (int i = 0; i < 200; i++) {
            long gap = WatcherEvents.gap(level, 1.0, 0f);
            if (gap < WatcherEvents.GAP_MIN || gap > WatcherEvents.GAP_MAX) {
                failures.add("gap " + gap + " out of range");
                break;
            }
            long fast = WatcherEvents.gap(level, 2.0, 1f);
            if (fast > WatcherEvents.GAP_MAX * 0.7 / 2.0 + 1 || fast < WatcherEvents.GAP_MIN * 0.7 / 2.0 - 1) {
                failures.add("gap with frequency 2 and a near moon is " + fast);
                break;
            }
        }
        if (WatcherEvents.GAP_MIN < WatcherEvents.DAY * 2) {
            failures.add("the same watcher could come back within two days");
        }
    }

    private static void checkData(MinecraftServer server, List<String> failures) {
        PrologueData data = new PrologueData();
        UUID id = UUID.randomUUID();
        PrologueData.Entry e = data.entry(id);
        e.nextWatcher = 123456L;
        e.sightings = 4;
        e.misses = 1;
        CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
        PrologueData.Entry back = PrologueData.load(tag, server.registryAccess()).entry(id);
        if (back.nextWatcher != 123456L || back.sightings != 4 || back.misses != 1) {
            failures.add("prologue data did not survive a save: " + tag);
        }
    }

    private static void checkPayloads(MinecraftServer server, List<String> failures) {
        WatcherPayload p = new WatcherPayload(77, 10.5, 64.0, -3.5, 135f, 2);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        WatcherPayload.STREAM_CODEC.encode(buf, p);
        if (!WatcherPayload.STREAM_CODEC.decode(buf).equals(p)) {
            failures.add("watcher payload did not round-trip");
        }
        WatcherEndPayload end = new WatcherEndPayload(77, WatcherOutcome.APPROACHED);
        RegistryFriendlyByteBuf buf2 = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        WatcherEndPayload.STREAM_CODEC.encode(buf2, end);
        WatcherEndPayload got = WatcherEndPayload.STREAM_CODEC.decode(buf2);
        if (!got.equals(end) || WatcherOutcome.byId(got.outcome()) != WatcherOutcome.APPROACHED) {
            failures.add("watcher end payload did not round-trip");
        }
        if (WatcherOutcome.byId(99) != WatcherOutcome.INTERRUPTED || WatcherOutcome.byId(-1) != WatcherOutcome.INTERRUPTED) {
            failures.add("an unknown outcome id is not treated as interrupted");
        }
    }

    private static void checkOutcomes(List<String> failures) {
        PrologueData.Entry e = new PrologueData.Entry();
        e.nextWatcher = 100000;
        WatcherEvents.applyOutcome(e, WatcherOutcome.UNSEEN, 30000);
        WatcherEvents.applyOutcome(e, WatcherOutcome.INTERRUPTED, 20000);
        if (e.nextWatcher != 20000 || e.misses != 2) {
            failures.add("unseen watchers do not come back sooner: next " + e.nextWatcher + " misses " + e.misses);
        }
        e.nextWatcher = 100000;
        WatcherEvents.applyOutcome(e, WatcherOutcome.UNSEEN, 10000);
        if (e.nextWatcher != 100000) {
            failures.add("a third unseen watcher still came back sooner");
        }
        WatcherEvents.applyOutcome(e, WatcherOutcome.STARED, 10000);
        if (e.sightings != 1 || e.misses != 0 || e.nextWatcher != 100000) {
            failures.add("a seen watcher did not count as a sighting");
        }
        WatcherEvents.applyOutcome(e, WatcherOutcome.DISABLED, 10);
        if (e.sightings != 1 || e.misses != 0 || e.nextWatcher != 100000) {
            failures.add("a disabled client changed the record");
        }
    }

    // 空中 (y=300) に石の円い足場を作り、その真ん中に立った目の高さから立ち位置を選ばせる
    private static void checkSpots(ServerLevel level, List<String> failures) {
        int minChunkX = (CX - RADIUS) >> 4;
        int maxChunkX = (CX + RADIUS) >> 4;
        int minChunkZ = (CZ - RADIUS) >> 4;
        int maxChunkZ = (CZ + RADIUS) >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
            }
        }
        List<BlockPos> placed = new ArrayList<>();
        try {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dz * dz <= RADIUS * RADIUS) {
                        BlockPos p = new BlockPos(CX + dx, Y, CZ + dz);
                        level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
                        placed.add(p);
                    }
                }
            }
            Vec3 eye = new Vec3(CX + 0.5, Y + 1 + 1.62, CZ + 0.5);
            RandomSource random = RandomSource.create(42);
            float viewYaw = 30f;
            for (int i = 0; i < 20; i++) {
                Optional<WatcherSpot> found = WatcherSpot.find(level, eye, viewYaw, 12, 24, random, true);
                if (found.isEmpty()) {
                    failures.add("no spot on an open platform (try " + i + ")");
                    break;
                }
                WatcherSpot s = found.get();
                if (s.distance() < 11 || s.distance() > 25.5) {
                    failures.add("spot distance " + s.distance() + " out of 12..24");
                }
                if (Mth.floor(s.feet().y) != Y + 1) {
                    failures.add("spot is not on the platform: " + s.feet());
                }
                double dirYaw = Mth.atan2(s.feet().z - eye.z, s.feet().x - eye.x) * Mth.RAD_TO_DEG - 90.0;
                if (Math.abs(Mth.wrapDegrees(dirYaw - viewYaw)) < WatcherSpot.VIEW_HALF_ANGLE - 3) {
                    failures.add("spot is in front of the player (" + Mth.wrapDegrees(dirYaw - viewYaw) + " deg)");
                }
                // 見る者の顔が、目の方を向いているか
                if (Math.abs(Mth.wrapDegrees((float) (dirYaw + 180.0) - s.yaw())) > 2.0) {
                    failures.add("watcher does not face the player: yaw " + s.yaw() + " vs " + (dirYaw + 180));
                }
                if (!WatcherSpot.clear(level, eye, s.feet().add(0, WatcherSpot.HEAD, 0))) {
                    failures.add("no line of sight to the chosen spot");
                }
                if (!s.skyline()) {
                    failures.add("a spot on a sky platform is not against the sky");
                }
            }
            // 水の上、葉の上には立たない
            BlockPos wet = new BlockPos(CX + 15, Y, CZ);
            level.setBlock(wet, Blocks.WATER.defaultBlockState(), 2);
            if (WatcherSpot.standable(level, wet.above())) {
                failures.add("the watcher may stand on water");
            }
            level.setBlock(wet, Blocks.OAK_LEAVES.defaultBlockState(), 2);
            if (WatcherSpot.standable(level, wet.above())) {
                failures.add("the watcher may stand on leaves");
            }
            level.setBlock(wet, Blocks.STONE.defaultBlockState(), 2);
            if (!WatcherSpot.standable(level, wet.above())) {
                failures.add("the watcher may not stand on plain stone in the open");
            }
            // 体の入る高さがない所には立たない
            level.setBlock(wet.above(2), Blocks.STONE.defaultBlockState(), 2);
            placed.add(wet.above(2));
            if (WatcherSpot.standable(level, wet.above())) {
                failures.add("the watcher may stand under a low ceiling");
            }
            // 周りを壁で囲むと、どこも見通せないので選ばない
            BlockState wall = Blocks.STONE.defaultBlockState();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == 3) {
                        for (int dy = 1; dy <= 5; dy++) {
                            BlockPos p = new BlockPos(CX + dx, Y + dy, CZ + dz);
                            level.setBlock(p, wall, 2);
                            placed.add(p);
                        }
                    }
                }
            }
            if (WatcherSpot.find(level, eye, viewYaw, 12, 24, random, false).isPresent()) {
                failures.add("a spot was chosen although the player is walled in");
            }
            // 近くの動物は、見る者の方を向いて足を止める
            Cow cow = EntityType.COW.create(level);
            if (cow == null) {
                failures.add("could not create a cow");
            } else {
                cow.moveTo(CX + 10.5, Y + 1, CZ + 0.5, 0f, 0f);
                level.addFreshEntity(cow);
                int n = WatcherEvents.faceTowards(level, new AABB(eye, eye).inflate(WatcherEvents.ANIMAL_RADIUS),
                        new Vec3(CX + 20.5, Y + 1, CZ + 0.5));
                if (n < 1 || !cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || !cow.getNavigation().isDone()) {
                    failures.add("the nearby cow did not stop to look at the watcher (" + n + ")");
                }
                cow.discard();
            }
        } finally {
            for (BlockPos p : placed) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    level.setChunkForced(x, z, false);
                }
            }
        }
    }
}
