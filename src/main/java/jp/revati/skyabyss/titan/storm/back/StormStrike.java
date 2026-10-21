package jp.revati.skyabyss.titan.storm.back;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 招雷の輪 (12 の 4.2節)。足もとに白い輪を warnTicks 出し、そこへ雷を落とす。雷乞い・東の試練・雷獣・心臓が呼ぶ。
// - 輪が避雷の柱から 3 ブロック以内なら、雷は柱へ落ちる (人には当たらない。柱が 1 段満ちる)
// - そうでなければ輪の半径 1.5 の中の人 (と、持ち主でない生き物) に damage と 0.5 秒の鈍足
// 輪の粒は overrideLimiter で送り、音も鳴らす。雷は見た目だけ (火も地形の変化もない)
// グループ (docs/design/10 の 9 章): 招雷はグループを持つ (持ち主の敵のグループ、または試練が狙ったグループ)。
// グループのある招雷は、そのグループの人と敵にだけ当たり、輪の粒もそのグループの人にだけ見える。グループのない招雷 (卵の敵) は誰にでも当たる
public final class StormStrike {
    public static final double RADIUS = 1.5;
    public static final double ROD_REACH = 3.0;
    private static final DustParticleOptions RING = new DustParticleOptions(new Vector3f(0.92f, 0.96f, 1f), 1.4f);

    private static final List<Strike> PENDING = new ArrayList<>();
    private static final List<Consumer<Strike>> LISTENERS = new ArrayList<>();

    // 一つの招雷。落ちたあと rod (柱へ落ちたとき) と hit (当たった人) が入る
    public static final class Strike {
        public final ServerLevel level;
        public final Vec3 pos;
        public final float damage;
        @Nullable
        public final Entity owner;
        // この招雷のグループ (null なら誰にでも)
        @Nullable
        public final UUID group;
        public final int warnTicks;
        int left;
        // 柱へ落ちたらその柱。輪を出した時点で決める (輪のあいだに柱が満ちても同じ柱へ)
        @Nullable
        public BlockPos rod;
        public final List<LivingEntity> hit = new ArrayList<>();
        boolean landed;
        // 段を満たすか (段階 3 の招雷の連打は柱へ落ちても満たさない)
        public boolean charges = true;

        Strike(ServerLevel level, Vec3 pos, int warnTicks, float damage, @Nullable Entity owner, @Nullable UUID group) {
            this.level = level;
            this.pos = pos;
            this.warnTicks = warnTicks;
            this.left = warnTicks;
            this.damage = damage;
            this.owner = owner;
            this.group = group;
        }

        public boolean landed() {
            return landed;
        }

        public int ticksLeft() {
            return left;
        }

        public Strike noCharge() {
            charges = false;
            return this;
        }
    }

    private StormStrike() {
    }

    // target の足もとに輪を出し、warnTicks 後に雷を落とす (目安: 1.2 秒 = 24 tick)
    public static Strike call(ServerLevel level, Entity target, int warnTicks, float damage, @Nullable Entity owner) {
        return call(level, target.position(), warnTicks, damage, owner);
    }

    public static Strike call(ServerLevel level, Vec3 at, int warnTicks, float damage, @Nullable Entity owner) {
        return call(level, at, warnTicks, damage, owner, Groups.keyOf(owner));
    }

    // グループを決めて呼ぶ (試練が人を狙うとき: その人のグループ)
    public static Strike call(ServerLevel level, Vec3 template, int warnTicks, float damage, @Nullable Entity owner, @Nullable UUID group) {
        // 区画の写しのセルの決まりの中 (TrialFrame) で呼ばれたら、落ちる所を写しの座標にする (落ちるのは決まりの外の tick)
        Vec3 at = jp.revati.skyabyss.trial.TrialFrame.current().world(template);
        Strike s = new Strike(level, at, Math.max(1, warnTicks), damage, owner, group);
        s.rod = StormRods.find(level, at, ROD_REACH);
        PENDING.add(s);
        jp.revati.skyabyss.trial.TrialFx.worldSound(level, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.8f, 1.9f);
        return s;
    }

    // 落ちた雷を知らせる (心臓が柱の段を数える、試練が骨の戸を開ける、など)
    public static void listen(Consumer<Strike> listener) {
        LISTENERS.add(listener);
    }

    public static List<Strike> pending(ServerLevel level) {
        return PENDING.stream().filter(s -> s.level == level).toList();
    }

    // そのディメンションの予告中の雷を消す (リセット・戦いの終わり)
    public static void cancelAll(ServerLevel level) {
        PENDING.removeIf(s -> s.level == level);
    }

    // そのグループの予告中の雷を消す (グループのリセット)
    public static void cancel(ServerLevel level, UUID group) {
        PENDING.removeIf(s -> s.level == level && group.equals(s.group));
    }

    // 箱の中の予告中の雷を消す (部屋の持ち主が変わったとき)
    public static void cancelIn(ServerLevel level, AABB box) {
        PENDING.removeIf(s -> s.level == level && box.contains(s.pos));
    }

    // public: 敵の自己テストが予告から落雷までを進めるために呼ぶ
    public static void tick(ServerLevel level) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Strike> landed = new ArrayList<>();
        for (Iterator<Strike> it = PENDING.iterator(); it.hasNext();) {
            Strike s = it.next();
            if (s.level != level) {
                continue;
            }
            s.left--;
            if (s.left > 0) {
                if (s.left % 3 == 0) {
                    warn(s);
                }
                continue;
            }
            it.remove();
            StormFx.to(s.group, () -> land(s));
            landed.add(s);
        }
        for (Strike s : landed) {
            for (Consumer<Strike> l : List.copyOf(LISTENERS)) {
                l.accept(s);
            }
        }
    }

    private static void warn(Strike s) {
        StormFx.to(s.group, () -> warnFx(s));
    }

    private static void warnFx(Strike s) {
        // 白い輪 (外の縁と内の輪)、柱へ落ちる輪は柱の先へ向かう火花も
        StormFx.ring(s.level, RING, s.pos, RADIUS, 16);
        StormFx.ring(s.level, RING, s.pos, RADIUS * 0.5, 8);
        if (s.rod != null) {
            Vec3 tip = new Vec3(s.rod.getX() + 0.5, s.rod.getY() + StormRods.HEIGHT, s.rod.getZ() + 0.5);
            StormFx.forced(s.level, ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 3, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private static void land(Strike s) {
        s.landed = true;
        ServerLevel level = s.level;
        if (s.rod != null && StormRods.get(level, s.rod) != null) {
            Vec3 tip = new Vec3(s.rod.getX() + 0.5, s.rod.getY() + StormRods.HEIGHT, s.rod.getZ() + 0.5);
            bolt(level, tip.add(0, 18, 0), tip);
            if (s.charges) {
                StormRods.charge(level, s.rod, 1);
            }
            return;
        }
        s.rod = null;
        bolt(level, s.pos.add(0, 18, 0), s.pos);
        AABB box = new AABB(s.pos.x - RADIUS, s.pos.y - 1, s.pos.z - RADIUS, s.pos.x + RADIUS, s.pos.y + 2.5, s.pos.z + RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == s.owner || !e.isAlive()) {
                continue;
            }
            // 別のグループの人・敵には当たらない
            UUID other = Groups.keyOf(e);
            if (s.group != null && other != null && !s.group.equals(other)) {
                continue;
            }
            // 持ち主の仲間 (同じ種類の敵) には当てない。人には必ず当てる
            if (!(e instanceof Player) && s.owner != null && e.getType() == s.owner.getType()) {
                continue;
            }
            double dx = e.getX() - s.pos.x;
            double dz = e.getZ() - s.pos.z;
            if (dx * dx + dz * dz > RADIUS * RADIUS) {
                continue;
            }
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }
            e.hurt(source(level, s.owner), s.damage);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false, true));
            s.hit.add(e);
        }
    }

    private static DamageSource source(ServerLevel level, @Nullable Entity owner) {
        if (owner instanceof LivingEntity le) {
            return level.damageSources().mobAttack(le);
        }
        return level.damageSources().lightningBolt();
    }

    // 見た目だけの雷 (光る筋と音)。バニラの雷の実体は火を付けるので使わない
    public static void bolt(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int n = 10;
        Vec3 prev = from;
        java.util.Random r = new java.util.Random((long) (from.x * 31 + from.z * 17 + level.getGameTime()));
        for (int i = 1; i <= n; i++) {
            Vec3 q = from.add(d.scale(i / (double) n));
            if (i < n) {
                q = q.add((r.nextDouble() - 0.5) * 1.2, 0, (r.nextDouble() - 0.5) * 1.2);
            }
            StormFx.line(level, ParticleTypes.ELECTRIC_SPARK, prev, q, 0.35);
            StormFx.line(level, ParticleTypes.END_ROD, prev, q, 0.9);
            prev = q;
        }
        StormFx.burst(level, ParticleTypes.FLASH, to, 1, 0);
        StormFx.burst(level, ParticleTypes.ELECTRIC_SPARK, to, 24, 0.5);
        jp.revati.skyabyss.trial.TrialFx.worldSound(level, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.6f, 1.2f);
        jp.revati.skyabyss.trial.TrialFx.worldSound(level, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.7f, 1.6f);
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID)
    static final class Ticker {
        private Ticker() {
        }

        @SubscribeEvent
        static void onLevelTick(LevelTickEvent.Post event) {
            if (event.getLevel() instanceof ServerLevel level) {
                tick(level);
            }
        }

        @SubscribeEvent
        static void onStopped(ServerStoppedEvent event) {
            PENDING.clear();
        }
    }
}
