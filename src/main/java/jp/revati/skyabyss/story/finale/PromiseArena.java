package jp.revati.skyabyss.story.finale;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.finale.mob.CloudMob;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import jp.revati.skyabyss.story.finale.mob.FallenPlume;
import jp.revati.skyabyss.story.finale.mob.PilgrimPlume;
import jp.revati.skyabyss.story.finale.mob.PlumeWarden;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

// 約束の島の主の間 (月見の岩の上。docs/design/50 の 4.2.4) と島の敵の湧き。
//   主の間  扉から上がった人が来ると巡礼者の羽が降りてきて、誰もいなくなると片付ける。倒れたら主の間の内の人 (と組) に合図 finale_lord_promise
//   湧き    島の上 (着く所のまわりと主の間・橋を除く) で、人のまわり 40 に 5 まで。番人は固い地面に、落ち羽は地面の上に
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PromiseArena {
    public static final String TAG = "skyabyss_finale_promise_arena";
    public static final String LORD = "finale_lord_promise";
    static final double RADIUS = 14;
    static final int EMPTY_TICKS = 200;
    static final int CAP = 5;
    static final double ARRIVE_SAFE = 14;
    static final double CAMP_SAFE = 9;
    private static int empty;

    private PromiseArena() {
    }

    public static AABB box() {
        return new AABB(PromiseIsle.mark("arena")).inflate(RADIUS, 8, RADIUS).move(0, 5, 0);
    }

    public static List<PilgrimPlume> lords(ServerLevel level) {
        return level.getEntitiesOfClass(PilgrimPlume.class, box(), Entity::isAlive);
    }

    public static List<ServerPlayer> present(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive() && !e.isSpectator());
    }

    static void arrive(ServerLevel level, ServerPlayer p) {
        Notice.place(p, Component.translatable("area.skyabyss.finale_promise_arena"), Component.translatable("area.skyabyss.finale_promise_arena.sub"));
        if (lords(level).isEmpty()) {
            Tasks.later(60, () -> summon(level));
        }
    }

    public static PilgrimPlume summon(ServerLevel level) {
        List<PilgrimPlume> now = lords(level);
        if (!now.isEmpty()) {
            return now.get(0);
        }
        BlockPos at = PromiseIsle.mark("lord_spawn");
        PilgrimPlume e = CloudMobsSetup.PILGRIM_PLUME.get().create(level);
        if (e == null) {
            return null;
        }
        e.moveTo(at.getX() + 0.5, at.getY() + PilgrimPlume.HOVER, at.getZ() + 0.5, 180f, 0f);
        e.tuneCloud(1, PilgrimPlume.HP, PilgrimPlume.DMG);
        e.addTag(TAG);
        level.addFreshEntity(e);
        e.playAction("call");
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 6, at.getZ() + 0.5, 120, 2.0, 4.0, 2.0, 0.06);
        level.playSound(null, at, CloudMobsSetup.WING_ROAR.get(), SoundSource.HOSTILE, 2.0f, 0.9f);
        empty = 0;
        return e;
    }

    // 主が呼ぶ落ち羽 (床の縁の上に)
    public static void callPlumes(ServerLevel level, int n) {
        BlockPos c = PromiseIsle.mark("lord_spawn");
        for (int i = 0; i < n; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            BlockPos at = BlockPos.containing(c.getX() + 0.5 + Math.cos(a) * 9, c.getY() + 3, c.getZ() + 0.5 + Math.sin(a) * 9);
            CloudMob m = put(level, CloudMobsSetup.FALLEN_PLUME.get(), at);
            if (m != null) {
                m.addTag(TAG);
                level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 16, 0.4, 0.8, 0.4, 0.04);
            }
        }
    }

    static void tick(ServerLevel level) {
        if (lords(level).isEmpty()) {
            return;
        }
        empty = present(level).isEmpty() ? empty + 20 : 0;
        if (empty >= EMPTY_TICKS) {
            clear(level);
        }
    }

    public static void clear(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, box(), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
        empty = 0;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof PilgrimPlume lord) || !(lord.level() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL) {
            return;
        }
        List<ServerPlayer> here = present(level);
        Quests.signalWithParty(level, here, LORD);
        for (ServerPlayer p : here) {
            Notice.narrate(p, "message.skyabyss.isle4.lord_down");
        }
        for (Entity e : level.getEntitiesOfClass(FallenPlume.class, box(), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
    }

    // ---------------------------------------------------------------- 島の敵の湧き (PromiseIsle から 40 tick ごと、人ごと)
    static boolean spawnable(BlockPos p) {
        return PromiseIsle.onIsland(p.getCenter()) && !p.closerThan(PromiseIsle.mark("arrive"), ARRIVE_SAFE)
                && !p.closerThan(PromiseIsle.mark("camp"), CAMP_SAFE) && !box().contains(p.getCenter());
    }

    static void spawn(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        if (!PromiseIsle.onIsland(p.position())) {
            return;
        }
        int near = level.getEntitiesOfClass(CloudMob.class, p.getBoundingBox().inflate(40, 24, 40), e -> e.isAlive() && !e.getTags().contains(TAG)).size();
        if (near >= CAP) {
            return;
        }
        RandomSource r = level.random;
        EntityType<? extends CloudMob> type = r.nextFloat() < 0.55f ? CloudMobsSetup.PLUME_WARDEN.get() : CloudMobsSetup.FALLEN_PLUME.get();
        BlockPos spot = spot(level, at, r);
        if (spot != null) {
            put(level, type, type == CloudMobsSetup.FALLEN_PLUME.get() ? spot.above(2) : spot);
        }
    }

    // 人から 14〜26 の固い地面 (光の橋の上・野営のそばは選ばない)
    static BlockPos spot(ServerLevel level, BlockPos near, RandomSource r) {
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 14 + r.nextDouble() * 12;
            int x = near.getX() + (int) Math.round(Math.cos(a) * d);
            int z = near.getZ() + (int) Math.round(Math.sin(a) * d);
            for (int y = near.getY() + 6; y >= near.getY() - 8; y--) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState below = level.getBlockState(p.below());
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()
                        && below.isFaceSturdy(level, p.below(), Direction.UP) && !below.is(FinaleBlocks.LIGHT_PATH.get())) {
                    if (!spawnable(p)) {
                        break;
                    }
                    return p;
                }
            }
        }
        return null;
    }

    public static CloudMob put(ServerLevel level, EntityType<? extends CloudMob> type, BlockPos at) {
        CloudMob m = type.create(level);
        if (m == null) {
            return null;
        }
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        if (m instanceof PlumeWarden) {
            m.tuneCloud(1, PlumeWarden.HP, PlumeWarden.DMG);
        } else if (m instanceof FallenPlume) {
            m.tuneCloud(1, FallenPlume.HP, FallenPlume.DMG);
        } else if (m instanceof PilgrimPlume) {
            m.tuneCloud(1, PilgrimPlume.HP, PilgrimPlume.DMG);
        }
        level.addFreshEntity(m);
        return m;
    }
}
