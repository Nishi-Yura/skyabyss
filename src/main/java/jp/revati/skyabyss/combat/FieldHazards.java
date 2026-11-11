package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 主の技に重ねる「場の技」(2026-11-08 の声「亀の第3形態がめっちゃ面白い。中ダンジョンのボスもそのくらい派手だと楽しい」)。
// 潮殻の心臓の段階3 にならい、gap ごとに一つ、主が技の途中でも出す。三つを順に:
//   降りそそぎ  人の足もとと、まわり 3〜5 先に二つの円 (1.6 秒の予告、最後に石が落ちてくる)
//   回る薙ぎ    主から向かい合わせの二本の帯が 100° まわる (2 秒の予告、1.5 秒)
//   崩れの環    主のまわりの輪 (内 4・外 9) が広がる。内か外へ (1.8 秒の予告)
// 地下のダンジョンの主と中ボス (DungeonBoss)、小ダンジョンの主 (番兵長・潮の番人・羽守・焼けた番人・古い灰被り) が持つ
public final class FieldHazards {
    public static final int RAIN_WARN = 32;
    public static final double RAIN_RADIUS = 2.0;
    public static final int SWEEP_WARN = 40;
    public static final int SWEEP_ACTIVE = 30;
    public static final double SWEEP_LENGTH = 13;
    public static final double SWEEP_HALF_WIDTH = 1.3;
    public static final int RING_WARN = 36;
    public static final double RING_IN = 4;
    public static final double RING_OUT = 9;

    public interface Cue {
        void send(String attack, int ticks);
    }

    private final Mob mob;
    private final Supplier<BlockState> block;
    private final Cue cue;
    private final Supplier<Float> damage;
    private final Supplier<DamageSource> source;
    private final Predicate<ServerPlayer> canHit;
    private final List<Vec3> rainSpots = new ArrayList<>();
    private int rainTicks = -1;
    private int wait = 60;
    private int count;

    public FieldHazards(Mob mob, Supplier<BlockState> block, Cue cue, Supplier<Float> damage, Supplier<DamageSource> source, Predicate<ServerPlayer> canHit) {
        this.mob = mob;
        this.block = block;
        this.cue = cue;
        this.damage = damage;
        this.source = source;
        this.canHit = canHit;
    }

    // 1 tick ごと。on = 出してよい (後半など)、gap = 次の技までの tick
    public void tick(boolean on, int gap) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive()) {
            return;
        }
        if (rainTicks >= 0) {
            falling(level);
        }
        LivingEntity t = mob.getTarget();
        if (!on || t == null || !t.isAlive() || BreakGauge.broken(mob) || --wait > 0) {
            return;
        }
        wait = gap + mob.getRandom().nextInt(Math.max(1, gap / 3));
        switch (count++ % 3) {
            case 0 -> rain(level);
            case 1 -> sweep(level);
            default -> ring(level);
        }
    }

    // 自己テスト: 三つとも出す
    public void all() {
        if (mob.level() instanceof ServerLevel level) {
            rain(level);
            sweep(level);
            ring(level);
        }
    }

    // 主のまわりの、当たりうる人
    public List<ServerPlayer> targetsNear() {
        return mob.level() instanceof ServerLevel level ? targets(level) : List.of();
    }

    List<ServerPlayer> targets(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>(level.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(mob) < 28 * 28 && canHit.test(p)));
        if (mob.getTarget() instanceof ServerPlayer t && !out.contains(t) && t.isAlive() && canHit.test(t)) {
            out.add(t);
        }
        return out;
    }

    void rain(ServerLevel level) {
        List<ServerPlayer> ps = targets(level);
        if (ps.isEmpty()) {
            return;
        }
        cue.send("field_rain", RAIN_WARN);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0f, 0.5f);
        rainSpots.clear();
        for (ServerPlayer p : ps) {
            rainSpots.add(p.position());
            for (int i = 0; i < 2; i++) {
                double a = mob.getRandom().nextDouble() * Math.PI * 2;
                double r = 3.0 + mob.getRandom().nextDouble() * 2.0;
                rainSpots.add(new Vec3(p.getX() + Math.cos(a) * r, p.getY(), p.getZ() + Math.sin(a) * r));
            }
        }
        BlockState b = block.get();
        for (Vec3 c : rainSpots) {
            double x = c.x;
            double z = c.z;
            double y = c.y;
            jp.revati.skyabyss.combat.prop.AttackProps.fall(level, jp.revati.skyabyss.combat.prop.AttackProps.fallKind(b), mob, c, RAIN_WARN, 14, 12f, 1.3f, 14);
            Telegraph.in(level, TelegraphShape.circle(x, z, RAIN_RADIUS)).floor(y).warn(RAIN_WARN).danger().owner(mob).victims(canHit::test)
                    .onImpact(s -> {
                        s.playSound(null, x, y, z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.4f, 0.7f);
                        s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, b), x, y + 0.4, z, 30, 1.0, 0.3, 1.0, 0.15);
                    })
                    .onHit((s, player) -> {
                        player.hurt(source.get(), damage.get() * 0.8f);
                        player.setDeltaMovement(player.getDeltaMovement().add(0, 0.5, 0));
                        player.hurtMarked = true;
                    })
                    .start();
        }
        rainTicks = 0;
    }

    private void falling(ServerLevel level) {
        rainTicks++;
        if (rainTicks >= RAIN_WARN) {
            rainTicks = -1;
            return;
        }
        if (rainTicks >= RAIN_WARN - 10 && rainTicks % 2 == 0) {
            double drop = (RAIN_WARN - rainTicks) / 10.0;
            BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, block.get());
            for (Vec3 c : rainSpots) {
                level.sendParticles(opt, c.x, c.y + 1 + drop * 9, c.z, 3, 0.3, 0.2, 0.3, 0);
            }
        }
    }

    void sweep(ServerLevel level) {
        cue.send("field_sweep", SWEEP_WARN);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.6f, 0.8f);
        double base = mob.getRandom().nextDouble() * Math.PI * 2;
        double span = Math.toRadians(100);
        BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, block.get());
        double cx = mob.getX();
        double cz = mob.getZ();
        double cy = mob.getY();
        for (int i = 0; i < 2; i++) {
            double start = base + Math.PI * i;
            boolean first = i == 0;
            Telegraph.in(level, TelegraphShape.sweep(cx, cz, start, span, SWEEP_LENGTH, SWEEP_HALF_WIDTH)).floor(cy).warn(SWEEP_WARN)
                    .active(SWEEP_ACTIVE).danger().owner(mob).victims(canHit::test)
                    .onImpact(s -> {
                        if (first) {
                            s.playSound(null, cx, cy, cz, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.4f, 1.2f);
                        }
                        for (double d = 2; d <= SWEEP_LENGTH; d += 1.5) {
                            s.sendParticles(opt, cx + Math.cos(start) * d, cy + 0.6, cz + Math.sin(start) * d, 4, 0.3, 0.3, 0.3, 0.1);
                        }
                    })
                    .onHit((s, player) -> {
                        player.hurt(source.get(), damage.get() * 0.9f);
                        Vec3 out = player.position().subtract(cx, player.getY(), cz);
                        player.knockback(1.4, -out.x, -out.z);
                        player.hurtMarked = true;
                    })
                    .start();
        }
    }

    // 崩れの環: 主のまわりの、内 RING_IN から外 RING_OUT までの輪。足もとが割れて噴き上がる
    void ring(ServerLevel level) {
        cue.send("field_ring", RING_WARN);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4f, 0.7f);
        BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, block.get());
        double cx = mob.getX();
        double cz = mob.getZ();
        double cy = mob.getY();
        Telegraph.in(level, TelegraphShape.ring(cx, cz, RING_IN, RING_OUT)).floor(cy).warn(RING_WARN).danger().owner(mob).victims(canHit::test)
                .onImpact(s -> {
                    String spike = jp.revati.skyabyss.combat.prop.AttackProps.spikeKind(block.get());
                    for (int k = 0; k < 10; k++) {
                        double a = k * Math.PI * 2 / 10 + 0.3;
                        double r = (RING_IN + RING_OUT) / 2 + (k % 2 == 0 ? -0.8 : 0.8);
                        jp.revati.skyabyss.combat.prop.AttackProps.rise(s, spike, mob, new Vec3(cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r),
                                (float) Math.toDegrees(a), 0.8f + (k % 3) * 0.15f, 16);
                    }
                    s.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
                    for (int k = 0; k < 24; k++) {
                        double a = k * Math.PI * 2 / 24;
                        double r = (RING_IN + RING_OUT) / 2;
                        s.sendParticles(opt, cx + Math.cos(a) * r, cy + 0.4, cz + Math.sin(a) * r, 8, 1.2, 0.6, 1.2, 0.2);
                    }
                })
                .onHit((s, player) -> {
                    player.hurt(source.get(), damage.get());
                    player.setDeltaMovement(player.getDeltaMovement().add(0, 0.8, 0));
                    player.hurtMarked = true;
                })
                .start();
    }
}
