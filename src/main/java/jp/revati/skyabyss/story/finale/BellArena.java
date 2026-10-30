package jp.revati.skyabyss.story.finale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.finale.mob.BellMourner;
import jp.revati.skyabyss.story.finale.mob.CloudMob;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import jp.revati.skyabyss.story.finale.mob.LastBellkeeper;
import jp.revati.skyabyss.story.finale.mob.MistBird;
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

// 鐘の島の主の間 (鐘楼の上。docs/design/50 の 4.2.1) と島の敵の湧き。
//   主の間  扉から上がった人が来ると最後の鐘守が現れ、誰もいなくなると片付ける。倒れたら主の間の内の人 (と組) に合図 finale_lord_bell。
//           また来れば戦える (繰り返し)。段 3 で床の東の半分が鐘雲になり (crumble)、片付け・倒れたときに戻す
//   湧き    島の上 (着く所のまわりと主の間を除く) で、人のまわり 40 に 5 まで。亡者は固い地面に、霞鳥は地面の上に
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BellArena {
    public static final String TAG = "skyabyss_finale_bell_arena";
    public static final String LORD = "finale_lord_bell";
    static final double RADIUS = 13;
    static final int EMPTY_TICKS = 200;
    static final int CAP = 5;
    static final double ARRIVE_SAFE = 16;
    private static final Map<BlockPos, BlockState> CRUMBLED = new HashMap<>();
    private static int empty;

    private BellArena() {
    }

    static AABB box() {
        return new AABB(BellIsle.mark("arena")).inflate(RADIUS, 10, RADIUS).move(0, 6, 0);
    }

    public static List<LastBellkeeper> lords(ServerLevel level) {
        return level.getEntitiesOfClass(LastBellkeeper.class, box(), Entity::isAlive);
    }

    public static List<ServerPlayer> present(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive() && !e.isSpectator());
    }

    static void arrive(ServerLevel level, ServerPlayer p) {
        Notice.place(p, Component.translatable("area.skyabyss.finale_bell_arena"), Component.translatable("area.skyabyss.finale_bell_arena.sub"));
        if (lords(level).isEmpty()) {
            Tasks.later(40, () -> summon(level));
        }
    }

    // 最後の鐘守を呼ぶ (主の出る所に、鐘の音と光の柱)
    public static LastBellkeeper summon(ServerLevel level) {
        List<LastBellkeeper> now = lords(level);
        if (!now.isEmpty()) {
            return now.get(0);
        }
        BlockPos at = BellIsle.mark("lord_spawn");
        LastBellkeeper e = CloudMobsSetup.LAST_BELLKEEPER.get().create(level);
        if (e == null) {
            return null;
        }
        BlockPos entry = BellIsle.mark("arena_entry");
        float yaw = (float) Math.toDegrees(Math.atan2(-(entry.getX() - at.getX()), entry.getZ() - at.getZ()));
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.tuneCloud(1, LastBellkeeper.HP, LastBellkeeper.DMG);
        e.addTag(TAG);
        level.addFreshEntity(e);
        e.playAction("call");
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5, 80, 1.2, 3.0, 1.2, 0.05);
        level.playSound(null, at, CloudMobsSetup.KEEPER_ROAR.get(), SoundSource.HOSTILE, 2.0f, 0.8f);
        empty = 0;
        return e;
    }

    // 主が呼ぶ亡者 (主の間の床の縁に)
    public static void callMourners(ServerLevel level, int n) {
        BlockPos c = BellIsle.mark("lord_spawn");
        for (int i = 0; i < n; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            BlockPos at = BlockPos.containing(c.getX() + 0.5 + Math.cos(a) * 3.8, c.getY(), c.getZ() + 0.5 + Math.sin(a) * 3.8);
            CloudMob m = put(level, CloudMobsSetup.BELL_MOURNER.get(), at);
            if (m != null) {
                m.addTag(TAG);
                level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 16, 0.4, 0.8, 0.4, 0.02);
            }
        }
    }

    // 段 3: 床の東の半分を鐘雲にする (主の足もとと小さな鐘の台は残す)
    public static void crumble(ServerLevel level) {
        if (!CRUMBLED.isEmpty()) {
            return;
        }
        BlockPos c = BellIsle.mark("arena");
        List<BlockPos> bells = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            bells.add(BellIsle.mark("arena_bell_" + i).below(2));
        }
        BlockState cloud = FinaleBlocks.BELL_CLOUD.get().defaultBlockState();
        for (int dx = 2; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                BlockPos q = c.offset(dx, 0, dz);
                if (Math.hypot(dx, dz) > 5.2 || bells.contains(q)) {
                    continue;
                }
                BlockState was = level.getBlockState(q);
                if (was.isAir() || was.getBlock() instanceof BellCloudBlock) {
                    continue;
                }
                CRUMBLED.put(q.immutable(), was);
                level.setBlock(q, cloud, 2);
                level.sendParticles(ParticleTypes.CLOUD, q.getX() + 0.5, q.getY() + 1, q.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.02);
            }
        }
        BellIsle.addExtra(CRUMBLED.keySet());
        BellIsle.update(level);
    }

    static void restore(ServerLevel level) {
        if (CRUMBLED.isEmpty()) {
            return;
        }
        BellIsle.clearExtra();
        for (Map.Entry<BlockPos, BlockState> e : CRUMBLED.entrySet()) {
            level.setBlock(e.getKey(), e.getValue(), 2);
        }
        CRUMBLED.clear();
    }

    // 誰もいなくなったら片付ける (BellIsle から 20 tick ごと)
    static void tick(ServerLevel level) {
        List<LastBellkeeper> l = lords(level);
        if (l.isEmpty()) {
            if (!CRUMBLED.isEmpty() && present(level).isEmpty()) {
                restore(level);
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
        restore(level);
        empty = 0;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof LastBellkeeper lord) || !(lord.level() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL) {
            return;
        }
        List<ServerPlayer> here = present(level);
        Quests.signalWithParty(level, here, LORD);
        for (ServerPlayer p : here) {
            Notice.narrate(p, "message.skyabyss.isle1.lord_down");
        }
        for (Entity e : level.getEntitiesOfClass(BellMourner.class, box(), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
        restore(level);
    }

    // ---------------------------------------------------------------- 島の敵の湧き (BellIsle から 40 tick ごと、人ごと)
    static boolean spawnable(BlockPos p) {
        return BellIsle.terrain().inside(p) && !p.closerThan(BellIsle.mark("arrive"), ARRIVE_SAFE) && !box().contains(p.getCenter());
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
        EntityType<? extends CloudMob> type = r.nextFloat() < 0.55f ? CloudMobsSetup.BELL_MOURNER.get() : CloudMobsSetup.MIST_BIRD.get();
        BlockPos spot = spot(level, at, r);
        if (spot != null) {
            put(level, type, type == CloudMobsSetup.MIST_BIRD.get() ? spot.above(5) : spot);
        }
    }

    // 人から 14〜26 の固い地面 (鐘雲・雲の上は選ばない)
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
                        && below.isFaceSturdy(level, p.below(), Direction.UP) && !(below.getBlock() instanceof BellCloudBlock)) {
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
        if (m instanceof BellMourner) {
            m.tuneCloud(1, BellMourner.HP, BellMourner.DMG);
        } else if (m instanceof MistBird) {
            m.tuneCloud(1, MistBird.HP, MistBird.DMG);
        } else if (m instanceof LastBellkeeper) {
            m.tuneCloud(1, LastBellkeeper.HP, LastBellkeeper.DMG);
        }
        level.addFreshEntity(m);
        return m;
    }

    // サーバーが止まったとき (FinaleReset)
    static void resetAll() {
        CRUMBLED.clear();
        empty = 0;
    }
}
