package jp.revati.skyabyss.story.finale;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.finale.mob.AshStriker;
import jp.revati.skyabyss.story.finale.mob.Backfire;
import jp.revati.skyabyss.story.finale.mob.CloudMob;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import jp.revati.skyabyss.story.finale.mob.Rewinder;
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

// 炉の島の主の間 (大炉の上。docs/design/50 の 4.2.3) と島の敵の湧き。
//   主の間  扉から上がった人が来ると戻し手が現れ、誰もいなくなると片付ける (外の輪の巻き戻りも止める)。倒れたら主の間の内の人 (と組) に合図 finale_lord_forge
//   湧き    島とかけらの上 (着く所のまわりと主の間・橋を除く) で、人のまわり 40 に 5 まで。打ち手は固い地面に、戻り火は地面の上に
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class ForgeArena {
    public static final String TAG = "skyabyss_finale_forge_arena";
    public static final String LORD = "finale_lord_forge";
    static final double RADIUS = 14;
    static final int EMPTY_TICKS = 200;
    static final int CAP = 5;
    static final double ARRIVE_SAFE = 14;
    private static int empty;

    private ForgeArena() {
    }

    public static AABB box() {
        return new AABB(ForgeIsle.mark("arena")).inflate(RADIUS, 8, RADIUS).move(0, 5, 0);
    }

    public static List<Rewinder> lords(ServerLevel level) {
        return level.getEntitiesOfClass(Rewinder.class, box(), Entity::isAlive);
    }

    public static List<ServerPlayer> present(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive() && !e.isSpectator());
    }

    static void arrive(ServerLevel level, ServerPlayer p) {
        Notice.place(p, Component.translatable("area.skyabyss.finale_forge_arena"), Component.translatable("area.skyabyss.finale_forge_arena.sub"));
        if (lords(level).isEmpty()) {
            Tasks.later(40, () -> summon(level));
        }
    }

    public static Rewinder summon(ServerLevel level) {
        List<Rewinder> now = lords(level);
        if (!now.isEmpty()) {
            return now.get(0);
        }
        BlockPos at = ForgeIsle.mark("lord_spawn");
        Rewinder e = CloudMobsSetup.REWINDER.get().create(level);
        if (e == null) {
            return null;
        }
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        e.tuneCloud(1, Rewinder.HP, Rewinder.DMG);
        e.addTag(TAG);
        level.addFreshEntity(e);
        e.playAction("call");
        level.sendParticles(ParticleTypes.FLAME, at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5, 60, 1.0, 2.0, 1.0, 0.04);
        level.playSound(null, at, CloudMobsSetup.REWINDER_ROAR.get(), SoundSource.HOSTILE, 2.0f, 0.9f);
        empty = 0;
        return e;
    }

    // 主が呼ぶ打ち手 (内の床の縁に)
    public static void callStrikers(ServerLevel level, int n) {
        BlockPos c = ForgeIsle.mark("lord_spawn");
        for (int i = 0; i < n; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            BlockPos at = BlockPos.containing(c.getX() + 0.5 + Math.cos(a) * 4.5, c.getY(), c.getZ() + 0.5 + Math.sin(a) * 4.5);
            CloudMob m = put(level, CloudMobsSetup.ASH_STRIKER.get(), at);
            if (m != null) {
                m.addTag(TAG);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 16, 0.4, 0.8, 0.4, 0.02);
            }
        }
    }

    static void tick(ServerLevel level) {
        if (lords(level).isEmpty()) {
            if (ForgeIsle.arenaRewinds && present(level).isEmpty()) {
                ForgeIsle.arenaRewinds = false;
            }
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
        ForgeIsle.arenaRewinds = false;
        ForgeIsle.update(level, true);
        empty = 0;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Rewinder lord) || !(lord.level() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL) {
            return;
        }
        List<ServerPlayer> here = present(level);
        Quests.signalWithParty(level, here, LORD);
        for (ServerPlayer p : here) {
            Notice.narrate(p, "message.skyabyss.isle3.lord_down");
        }
        for (Entity e : level.getEntitiesOfClass(AshStriker.class, box(), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
        ForgeIsle.arenaRewinds = false;
        ForgeIsle.update(level, true);
    }

    // ---------------------------------------------------------------- 島の敵の湧き (ForgeIsle から 40 tick ごと、人ごと)
    static boolean spawnable(BlockPos p) {
        return ForgeIsle.onIsles(p.getCenter()) && !p.closerThan(ForgeIsle.mark("arrive"), ARRIVE_SAFE) && !box().contains(p.getCenter());
    }

    static void spawn(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        if (!spawnable(at)) {
            return;
        }
        int near = level.getEntitiesOfClass(CloudMob.class, p.getBoundingBox().inflate(40, 24, 40), e -> e.isAlive() && !e.getTags().contains(TAG)).size();
        if (near >= CAP) {
            return;
        }
        RandomSource r = level.random;
        EntityType<? extends CloudMob> type = r.nextFloat() < 0.55f ? CloudMobsSetup.ASH_STRIKER.get() : CloudMobsSetup.BACKFIRE.get();
        BlockPos spot = spot(level, at, r);
        if (spot != null) {
            put(level, type, type == CloudMobsSetup.BACKFIRE.get() ? spot.above(3) : spot);
        }
    }

    // 人から 14〜26 の固い地面 (戻り石と光の橋の上は選ばない)
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
                        && below.isFaceSturdy(level, p.below(), Direction.UP) && !below.is(FinaleBlocks.LIGHT_PATH.get())
                        && !(below.getBlock() instanceof RewindStoneBlock) && !below.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)) {
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
        if (m instanceof AshStriker) {
            m.tuneCloud(1, AshStriker.HP, AshStriker.DMG);
        } else if (m instanceof Backfire) {
            m.tuneCloud(1, Backfire.HP, Backfire.DMG);
        } else if (m instanceof Rewinder) {
            m.tuneCloud(1, Rewinder.HP, Rewinder.DMG);
        }
        level.addFreshEntity(m);
        return m;
    }
}
