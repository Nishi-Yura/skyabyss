package jp.revati.skyabyss.story.ch1;

import jp.revati.skyabyss.util.Players;

import java.util.List;
import java.util.Optional;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.BoneSentinel;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.opening.OpeningSites;
import jp.revati.skyabyss.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 歩哨の巡回 (E10): 骨の丘 (巨神の骨) のそばを、夜だけ、既存の骨の番兵が一体歩く。昼は眠る (消える)。
// 新しい敵は足さない (番兵は岩鯨の背の既存の敵)。追う範囲を狭めてあり (巡回の範囲 60 ブロック)、近づかなければ襲ってこない。
// 倒すと頁 (ch1/sentry) と経験値。ピースフルでは出ない。丘から 150 ブロック以内に人がいる夜だけ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SentryPatrol {
    public static final String TAG = "skyabyss_ch1_sentry";
    // 巡回の範囲 (骨の丘の中心から)
    static final int RANGE = 60;
    // 丘のまわりのどこに現れるか
    static final int RING_MIN = 28;
    static final int RING_MAX = 44;
    // 人がこの近さにいる夜だけ
    static final double WATCH = 150;
    // 追う範囲 (番兵の既定は 32)
    static final double FOLLOW = 14;
    private static final int EVERY = 100;

    private SentryPatrol() {
    }

    // 夜か (一日の時刻 13000〜23000。純粋)
    public static boolean night(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= 13000 && t < 23000;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || level.getGameTime() % EVERY != 37) {
            return;
        }
        Optional<BlockPos> ribs = OpeningSites.ribs(level.getServer());
        if (ribs.isEmpty() || !OpeningSites.built(level.getServer(), OpeningSites.Kind.RIBS)) {
            return;
        }
        boolean near = false;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (!p.isSpectator() && !(p instanceof FakePlayer) && Math.hypot(p.getX() - ribs.get().getX(), p.getZ() - ribs.get().getZ()) <= WATCH) {
                near = true;
                break;
            }
        }
        sync(level, ribs.get(), night(level.getDayTime()), near);
    }

    // 巡回の番兵を、夜で人が近くにいれば一体に、そうでなければ 0 にそろえる。そろえたあとの数を返す
    static int sync(ServerLevel level, BlockPos center, boolean night, boolean playerNear) {
        List<BoneSentinel> now = patrol(level, center);
        boolean want = night && playerNear && level.getDifficulty() != Difficulty.PEACEFUL;
        if (!want) {
            for (BoneSentinel s : now) {
                // 昼は眠る: 静かに消える
                level.sendParticles(ParticleTypes.POOF, s.getX(), s.getY() + 1.0, s.getZ(), 8, 0.3, 0.5, 0.3, 0.01);
                s.discard();
            }
            return 0;
        }
        // 多すぎれば減らす
        for (int i = 1; i < now.size(); i++) {
            now.get(i).discard();
        }
        if (!now.isEmpty()) {
            return 1;
        }
        return spawn(level, center) ? 1 : 0;
    }

    static List<BoneSentinel> patrol(ServerLevel level, BlockPos center) {
        AABB box = new AABB(center).inflate(RANGE + 40, 120, RANGE + 40);
        return level.getEntitiesOfClass(BoneSentinel.class, box, s -> s.getTags().contains(TAG) && s.isAlive());
    }

    static boolean spawn(ServerLevel level, BlockPos center) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double r = RING_MIN + level.random.nextDouble() * (RING_MAX - RING_MIN);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * r);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * r);
            BlockPos column = new BlockPos(x, center.getY(), z);
            // 読み込まれている所だけ (チャンクを作らない)
            if (!level.hasChunkAt(column)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty() || y < level.getMinBuildHeight() + 2) {
                continue;
            }
            BoneSentinel sentry = ModEntities.BONE_SENTINEL.get().create(level);
            if (sentry == null) {
                return false;
            }
            sentry.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
            sentry.addTag(TAG);
            sentry.setPersistenceRequired();
            sentry.restrictTo(center, RANGE);
            if (sentry.getAttribute(Attributes.FOLLOW_RANGE) != null) {
                sentry.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(FOLLOW);
            }
            sentry.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), MobSpawnType.EVENT, null);
            level.addFreshEntity(sentry);
            return true;
        }
        return false;
    }

    // 倒した: 近くにいた人 (24 ブロック以内) に頁
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof BoneSentinel sentry) || !sentry.getTags().contains(TAG) || !(sentry.level() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (!p.isSpectator() && !(p instanceof FakePlayer) && p.distanceToSqr(sentry) <= 24 * 24) {
                reward(p);
            }
        }
    }

    static void reward(ServerPlayer player) {
        if (Lore.unlock(player, Ch1.SENTRY, true)) {
            Notice.narrate(player, "message.skyabyss.ch1.sentry.page");
        }
    }
}
