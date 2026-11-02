package jp.revati.skyabyss.story.ch6.field;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.FullKeeper;
import jp.revati.skyabyss.town.TownGuestEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// 星術「一瞬止め」(満月の野の月の溜まりで覚える。docs/design/46 の 4.2・4.5)。月の砂時計 (MoonHourglassItem) を右クリックで、
//   まわり 10 の内の敵と飛び物を 2 秒止める (止まった敵は傷 ×1.25。下弦の淵の自分の残像は 5 秒、残月の剣守の残像は止まらない)。まわり 10 の内の満ち引きの石は 2 秒、今の状態のまま (FieldTide.hold)。
//   望の灯守が光放ちの予告のあいだに止まると、放ちが崩れる (FullKeeper.stopped)。使い直しは 12 秒。月の上でだけ。
//   止まったものは tick を飛ばす (動かない・考えない)。飛び物は速さを 0 にして、解けたら戻す (見る側の先読みで動かないように)。
//   覚えた印は倒れても残す (util/LifeKeep)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MomentStop {
    public static final String TAG = "skyabyss_moon_moment_stop";
    public static final double RADIUS = 10;
    public static final int DURATION = 40;
    public static final int COOLDOWN = 240;
    public static final int ECHO_DURATION = 100;
    public static final float FROZEN_TAKEN = 1.25f;
    // 止めたもの: 生き物の番号 -> 解ける tick。飛び物の元の速さと重さ
    private static final Map<Integer, Long> FROZEN = new HashMap<>();
    private static final Map<Integer, Vec3> SPEED = new HashMap<>();
    private static final Map<Integer, Boolean> GRAVITY = new HashMap<>();

    private MomentStop() {
    }

    public static boolean learned(ServerPlayer p) {
        return p.getPersistentData().getBoolean(TAG);
    }

    public static void learn(ServerPlayer p) {
        p.getPersistentData().putBoolean(TAG, true);
    }

    public static void forget(ServerPlayer p) {
        p.getPersistentData().remove(TAG);
    }

    public static boolean frozen(Entity e) {
        Long until = FROZEN.get(e.getId());
        return until != null && until > e.level().getGameTime();
    }

    // 使う。止めたものの数 (石を含む) を返す。月の外では -1
    public static int cast(ServerPlayer p) {
        if (p.level().dimension() != MoonWorld.LEVEL) {
            Notice.narrate(p, "message.skyabyss.moon.moment_stop.not_moon");
            return -1;
        }
        ServerLevel level = p.serverLevel();
        long until = level.getGameTime() + DURATION;
        int n = 0;
        AABB box = p.getBoundingBox().inflate(RADIUS, 6, RADIUS);
        for (Entity e : level.getEntities((Entity) null, box, e -> e != p && e.isAlive() && e.distanceTo(p) <= RADIUS + 1 && stoppable(e))) {
            // 自分の残像 (下弦の淵) は長く止まる (足場にする)
            freeze(e, e instanceof jp.revati.skyabyss.story.ch6.rift.EchoShade ? level.getGameTime() + ECHO_DURATION : until);
            n++;
        }
        if (MoonField.inside(p.blockPosition()) && jp.revati.skyabyss.story.ch6.MoonWorld.fieldBuilt(level.getServer())) {
            for (MoonField.Stone s : MoonField.stones()) {
                if (s.pos().closerToCenterThan(p.position(), RADIUS) && FieldTide.hold(level, s.pos(), until)) {
                    n++;
                }
            }
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), FieldBlocks.MOMENT_STOP.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        for (int ring = 1; ring <= 3; ring++) {
            double r = RADIUS * ring / 3.0;
            int k = 16 + ring * 10;
            for (int i = 0; i < k; i++) {
                double a = i / (double) k * Math.PI * 2;
                level.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * r, p.getY() + 0.2 + ring * 0.3, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        return n;
    }

    static boolean stoppable(Entity e) {
        if (e instanceof Player || e instanceof TownGuestEntity) {
            return false;
        }
        // 残月の剣守の残像は止まらない (主を止めても、残像は過ぎた 3 秒をなぞり続ける)
        if (e instanceof jp.revati.skyabyss.story.ch6.mob.WaningBlade b && b.echo()) {
            return false;
        }
        return e instanceof Enemy || e instanceof Projectile || e instanceof FallingLight || e instanceof jp.revati.skyabyss.story.ch6.rift.EchoShade;
    }

    public static void freeze(Entity e, long until) {
        if (e instanceof FullKeeper k) {
            k.stopped();
        }
        FROZEN.put(e.getId(), until);
        if (!(e instanceof LivingEntity) && !SPEED.containsKey(e.getId())) {
            SPEED.put(e.getId(), e.getDeltaMovement());
            GRAVITY.put(e.getId(), e.isNoGravity());
            e.setDeltaMovement(Vec3.ZERO);
            e.setNoGravity(true);
            e.hurtMarked = true;
        }
    }

    static void thaw(Entity e) {
        FROZEN.remove(e.getId());
        restore(e);
    }

    private static void restore(Entity e) {
        Vec3 v = SPEED.remove(e.getId());
        Boolean g = GRAVITY.remove(e.getId());
        if (v != null) {
            e.setDeltaMovement(v);
            e.setNoGravity(g != null && g);
            e.hurtMarked = true;
        }
    }

    // 止まっているものは tick を飛ばす (月の世界のサーバーの側だけ)
    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Pre event) {
        Entity e = event.getEntity();
        if (FROZEN.isEmpty() || e.level().isClientSide || e.level().dimension() != MoonWorld.LEVEL) {
            return;
        }
        Long until = FROZEN.get(e.getId());
        if (until == null) {
            return;
        }
        if (until <= e.level().getGameTime() || !e.isAlive()) {
            thaw(e);
            return;
        }
        event.setCanceled(true);
        if (e.level() instanceof ServerLevel sl && (e.level().getGameTime() + e.getId()) % 6 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 2, e.getBbWidth() * 0.5, e.getBbHeight() * 0.3,
                    e.getBbWidth() * 0.5, 0.0);
        }
    }

    // 止まった敵は傷が通りやすい
    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        if (!FROZEN.isEmpty() && frozen(event.getEntity())) {
            event.setAmount(event.getAmount() * FROZEN_TAKEN);
        }
    }

    // 消えた生き物の札を片付ける (FieldTicker から 20 tick ごと)
    static void sweep(ServerLevel level) {
        long gt = level.getGameTime();
        for (Iterator<Map.Entry<Integer, Long>> it = FROZEN.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Long> en = it.next();
            Entity e = level.getEntity(en.getKey());
            if (e == null) {
                it.remove();
                SPEED.remove(en.getKey());
                GRAVITY.remove(en.getKey());
            } else if (en.getValue() <= gt) {
                it.remove();
                restore(e);
            }
        }
    }

    // 自己テスト用
    public static boolean anyFrozenNear(ServerLevel level, BlockPos at, double r) {
        return level.getEntities((Entity) null, new AABB(at).inflate(r), MomentStop::frozen).size() > 0;
    }
}
