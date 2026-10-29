package jp.revati.skyabyss.story.finale;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.finale.mob.CloudMob;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import jp.revati.skyabyss.story.finale.mob.LampEye;
import jp.revati.skyabyss.story.finale.mob.LampMoth;
import jp.revati.skyabyss.story.finale.mob.NetCaster;
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

// 灯の島の主の間 (灯台の上の回廊。docs/design/50 の 4.2.2) と島の敵の湧き。
//   主の間  扉から上がった人が来ると灯守の目が現れ、誰もいなくなると片付ける。倒れたら主の間の内の人 (と組) に合図 finale_lord_lamp。また来れば戦える
//   湧き    島の上 (着く所のまわりと主の間・橋を除く) で、人のまわり 40 に 5 まで。網打ちは固い地面に、灯蛾は地面の上に
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LampArena {
    public static final String TAG = "skyabyss_finale_lamp_arena";
    public static final String LORD = "finale_lord_lamp";
    static final double RADIUS = 14;
    static final int EMPTY_TICKS = 200;
    static final int CAP = 5;
    static final double ARRIVE_SAFE = 14;
    private static int empty;

    private LampArena() {
    }

    public static AABB box() {
        return new AABB(LampIsle.mark("arena")).inflate(RADIUS, 8, RADIUS).move(0, 5, 0);
    }

    public static List<LampEye> lords(ServerLevel level) {
        return level.getEntitiesOfClass(LampEye.class, box(), Entity::isAlive);
    }

    public static List<ServerPlayer> present(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive() && !e.isSpectator());
    }

    static void arrive(ServerLevel level, ServerPlayer p) {
        Notice.place(p, Component.translatable("area.skyabyss.finale_lamp_arena"), Component.translatable("area.skyabyss.finale_lamp_arena.sub"));
        if (lords(level).isEmpty()) {
            Tasks.later(40, () -> summon(level));
        }
    }

    // 灯守の目を呼ぶ (主の出る所の上に浮かぶ)
    public static LampEye summon(ServerLevel level) {
        List<LampEye> now = lords(level);
        if (!now.isEmpty()) {
            return now.get(0);
        }
        BlockPos at = LampIsle.mark("lord_spawn");
        LampEye e = CloudMobsSetup.LAMP_EYE.get().create(level);
        if (e == null) {
            return null;
        }
        e.moveTo(at.getX() + 0.5, at.getY() + LampEye.HOVER, at.getZ() + 0.5, 0f, 0f);
        e.setYHeadRot(0f);
        e.setYBodyRot(0f);
        e.tuneCloud(1, LampEye.HP, LampEye.DMG);
        e.addTag(TAG);
        level.addFreshEntity(e);
        e.playAction("call");
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5, 80, 1.5, 2.0, 1.5, 0.05);
        level.playSound(null, at, CloudMobsSetup.EYE_ROAR.get(), SoundSource.HOSTILE, 2.0f, 0.9f);
        empty = 0;
        return e;
    }

    // 主が呼ぶ灯蛾 (主の間の縁の上に)
    public static void callMoths(ServerLevel level, int n) {
        BlockPos c = LampIsle.mark("lord_spawn");
        for (int i = 0; i < n; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            BlockPos at = BlockPos.containing(c.getX() + 0.5 + Math.cos(a) * 6, c.getY() + 2, c.getZ() + 0.5 + Math.sin(a) * 6);
            CloudMob m = put(level, CloudMobsSetup.LAMP_MOTH.get(), at);
            if (m != null) {
                m.addTag(TAG);
                level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.03);
            }
        }
    }

    // 誰もいなくなったら片付ける (LampIsle から 20 tick ごと)
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
        if (!(event.getEntity() instanceof LampEye lord) || !(lord.level() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL) {
            return;
        }
        List<ServerPlayer> here = present(level);
        Quests.signalWithParty(level, here, LORD);
        for (ServerPlayer p : here) {
            Notice.narrate(p, "message.skyabyss.isle2.lord_down");
            LampIsle.gaze(p, false, LampIsle.HOLD, 0, null);
        }
        for (Entity e : level.getEntitiesOfClass(LampMoth.class, box(), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
    }

    // ---------------------------------------------------------------- 島の敵の湧き (LampIsle から 40 tick ごと、人ごと)
    static boolean spawnable(BlockPos p) {
        return LampIsle.onIsland(p.getCenter()) && !p.closerThan(LampIsle.mark("arrive"), ARRIVE_SAFE);
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
        EntityType<? extends CloudMob> type = r.nextFloat() < 0.55f ? CloudMobsSetup.NET_CASTER.get() : CloudMobsSetup.LAMP_MOTH.get();
        BlockPos spot = spot(level, at, r);
        if (spot != null) {
            put(level, type, type == CloudMobsSetup.LAMP_MOTH.get() ? spot.above(3) : spot);
        }
    }

    // 人から 14〜26 の固い地面 (光の橋の上は選ばない)
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
        if (m instanceof NetCaster) {
            m.tuneCloud(1, NetCaster.HP, NetCaster.DMG);
        } else if (m instanceof LampMoth) {
            m.tuneCloud(1, LampMoth.HP, LampMoth.DMG);
        } else if (m instanceof LampEye) {
            m.tuneCloud(1, LampEye.HP, LampEye.DMG);
        }
        level.addFreshEntity(m);
        return m;
    }
}
