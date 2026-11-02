package jp.revati.skyabyss.story.finale.founder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.story.finale.FinaleTerrain;
import jp.revati.skyabyss.story.finale.FinaleWorld;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import jp.revati.skyabyss.story.finale.mob.FounderBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 鐘の内 (docs/design/53 の 4 章)。雲海の次元の南の閉じた大鐘 (地形 data/skyabyss/finale/founder.json。tools/gen/world/build_bell_inside.py。FinaleWorld の版 8)。
//   始まり  人が着くと 2 秒あとに初代の空見が出る (始祖の出る所)。誰もいなくなって 10 秒で片付ける
//   落ちる  床の縁から鐘の口の下へ落ちた人は、床の真ん中の南へ戻し、体力の 1 割を失う
//   帰り    南の縁の帰りの台 (天然磁石) を右クリックで都の門のそばへ
//   倒れた  鐘の内で倒れた人は、起きたら都の門のそばへ (持ち物は墓の決まりのまま)
//   退けた  始祖がやっつめを歌い終えると、鐘の内の人 (と組) に合図 founder_down と報酬 (Founder.onDefeat)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FounderArena {
    public static final String TAG = "skyabyss_founder_arena";
    static final String TERRAIN = "founder";
    static final int EMPTY_TICKS = 200;
    static final float FALL_COST = 0.1f;
    private static int empty;
    private static long startedAt = -1;
    private static final Set<UUID> FELL_HERE = new HashSet<>();

    private FounderArena() {
    }

    public static FinaleTerrain terrain() {
        return FinaleTerrain.of(TERRAIN);
    }

    public static BlockPos mark(String key) {
        return terrain().mark(key);
    }

    public static int floor() {
        return mark("center").getY() - 1;
    }

    public static AABB box() {
        BlockPos c = mark("center");
        return new AABB(c).inflate(21, 0, 21).expandTowards(0, 38, 0).expandTowards(0, -10, 0);
    }

    public static boolean inside(ServerPlayer p) {
        return FinaleWorld.in(p) && box().contains(p.position());
    }

    public static List<ServerPlayer> present(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, box(), e -> e.isAlive() && !e.isSpectator());
    }

    public static List<FounderBoss> bosses(ServerLevel level) {
        if (level.dimension() != FinaleWorld.LEVEL) {
            return List.of();
        }
        return level.getEntitiesOfClass(FounderBoss.class, box(), Entity::isAlive);
    }

    public static FounderBoss real(ServerLevel level) {
        for (FounderBoss b : bosses(level)) {
            if (!b.echo()) {
                return b;
            }
        }
        return null;
    }

    public static List<FounderBoss> echoes(ServerLevel level) {
        return bosses(level).stream().filter(FounderBoss::echo).toList();
    }

    // 人が鐘の内に着いた (Founder.enter から)
    static void arrive(ServerLevel level, ServerPlayer p) {
        Notice.place(p, Component.translatable("area.skyabyss.founder_arena"), Component.translatable("area.skyabyss.founder_arena.sub"));
        if (real(level) == null) {
            Tasks.later(40, () -> summon(level));
        }
    }

    public static FounderBoss summon(ServerLevel level) {
        FounderBoss now = real(level);
        if (now != null) {
            return now;
        }
        BlockPos at = mark("founder_spawn");
        FounderBoss e = CloudMobsSetup.FOUNDER.get().create(level);
        if (e == null) {
            return null;
        }
        BlockPos entry = mark("arrive");
        float yaw = (float) Math.toDegrees(Math.atan2(-(entry.getX() - at.getX()), entry.getZ() - at.getZ()));
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.tuneCloud(1, FounderBoss.HP, FounderBoss.DMG);
        e.addTag(TAG);
        level.addFreshEntity(e);
        e.playAction("call");
        level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.5, at.getZ() + 0.5, 60, 0.6, 1.2, 0.6, 0.04);
        level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0f, 0.5f);
        for (ServerPlayer p : present(level)) {
            Notice.narrate(p, "message.skyabyss.founder.appear");
        }
        empty = 0;
        startedAt = level.getGameTime();
        return e;
    }

    // ななつの写し (本物の左右どちらか 5 先、床の内)
    public static FounderBoss spawnEcho(ServerLevel level, FounderBoss real) {
        FounderBoss e = CloudMobsSetup.FOUNDER.get().create(level);
        if (e == null) {
            return null;
        }
        BlockPos c = mark("center");
        double a = Math.toRadians(real.getYRot()) + (level.random.nextBoolean() ? Math.PI / 2 : -Math.PI / 2);
        double x = real.getX() + Math.cos(a) * 5;
        double z = real.getZ() + Math.sin(a) * 5;
        if (Math.hypot(x - c.getX() - 0.5, z - c.getZ() - 0.5) > 14) {
            x = real.getX() - Math.cos(a) * 5;
            z = real.getZ() - Math.sin(a) * 5;
        }
        e.moveTo(x, real.getY(), z, real.getYRot(), 0f);
        e.tuneCloud(1, FounderBoss.HP, FounderBoss.DMG);
        e.addTag(TAG);
        e.setHealth(real.getHealth());
        level.addFreshEntity(e);
        e.makeEcho(real);
        level.sendParticles(ParticleTypes.CLOUD, x, real.getY() + 1.2, z, 20, 0.3, 0.8, 0.3, 0.02);
        return e;
    }

    // 始祖がやっつめを歌い終えた
    public static void defeated(ServerLevel level, FounderBoss boss) {
        long ticks = startedAt < 0 ? -1 : level.getGameTime() - startedAt;
        startedAt = -1;
        Founder.onDefeat(level, present(level), ticks);
    }

    public static void clear(ServerLevel level) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, box(), e -> e.getTags().contains(TAG))) {
            e.discard();
        }
        empty = 0;
        startedAt = -1;
    }

    // ---------------------------------------------------------------- 見回り
    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != FinaleWorld.LEVEL || level.getGameTime() % 5 != 0) {
            return;
        }
        if (FinaleWorld.version(level.getServer()) < 8) {
            return;
        }
        int floor = floor();
        BlockPos c = mark("center");
        AABB fallBox = box().expandTowards(0, -140, 0);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, fallBox, e -> e.isAlive() && !e.isSpectator())) {
            if (p.getY() < floor - 5) {
                p.teleportTo(level, c.getX() + 0.5, c.getY(), c.getZ() + 5.5, 180f, 0f);
                p.resetFallDistance();
                p.setDeltaMovement(0, 0, 0);
                if (!p.isCreative()) {
                    p.setHealth(Math.max(1f, p.getHealth() - p.getMaxHealth() * FALL_COST));
                }
                level.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8f, 1.4f);
                Notice.hint(p, "message.skyabyss.founder.fell");
            }
        }
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        if (real(level) == null) {
            return;
        }
        empty = present(level).isEmpty() ? empty + 20 : 0;
        if (empty >= EMPTY_TICKS) {
            clear(level);
        }
    }

    // 帰りの台
    @SubscribeEvent
    static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand() != InteractionHand.MAIN_HAND || !FinaleWorld.in(p)
                || FinaleWorld.version(p.server) < 8) {
            return;
        }
        if (event.getPos().equals(mark("return"))) {
            event.setCanceled(true);
            p.serverLevel().playSound(null, event.getPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 0.7f);
            FinaleWorld.leave(p);
        }
    }

    // 鐘の内で倒れた人は、起きたら都の門のそばへ
    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inside(p)) {
            FELL_HERE.add(p.getUUID());
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !event.isEndConquered() && FELL_HERE.remove(p.getUUID())) {
            Tasks.later(2, () -> {
                if (p.isAlive() && !p.hasDisconnected()) {
                    FinaleWorld.leave(p);
                    Notice.narrate(p, "message.skyabyss.founder.woke");
                }
            });
        }
    }

    // サーバーが止まったとき (FinaleReset)
    public static void resetAll() {
        empty = 0;
        startedAt = -1;
        FELL_HERE.clear();
    }
}
