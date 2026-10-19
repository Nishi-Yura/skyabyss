package jp.revati.skyabyss.titan.domain;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

// 余燼の心臓の領域「炉心」(docs/design/13 の 5.5)。床が炉の底の熾になり、二つの組の床が交互に熱くなる。
//  一巡 = 4 つの型を 60 tick おきに: 輪 A (外側の帯と中の帯)、輪 B (その間の帯)、扇 A (3 方向)、扇 B (その間の 3 方向)。
//  どの型も、予告 1.7 秒のあと 2.5 秒のあいだ 10 tick ごとに傷 (熱い床)。次の型の予告は前の型が熱いうちに始まるが、前の型は次の型が当たる前に冷める
//  (型の間隔 60 tick = 前の型の熱い時間 50 tick + 予告の始めの 10 tick の余り)。同じ組が続けて熱くなることはなく、組が入れ替わる (逃げ先がいつもある)。
//  輪の組: A = 外 [R×0.75, R] と 内 [R×0.25, R×0.5] / B = [R×0.5, R×0.75] と 中心の円 [0, R×0.25]。扇は 60° 幅 6 枚を交互に
// 回り続けると、必ず輪か扇に当たる。技の名前: ember.core_rings / ember.core_fans。傷 7 × 最大 5 回。
public final class HearthCoreDomain extends HeartDomain {
    public static final String ID = "hearth_core";
    public static final int STEP = 60;
    public static final int WARN = 34;
    public static final int ACTIVE = 50;
    public static final int REPEAT = 10;
    public static final float DAMAGE = 7f;
    public static final int FIRST = 24;

    private int steps;
    // 試験用: 最後に出した型 (0 輪 A / 1 輪 B / 2 扇 A / 3 扇 B)
    private int lastKind = -1;
    private double fanBase;

    public HearthCoreDomain(Mob heart, double cx, double cz, double floorY, double radius, Predicate<ServerPlayer> victims,
            BiConsumer<ServerPlayer, Float> striker, Supplier<Collection<ServerPlayer>> players) {
        super(heart, ID, cx, cz, floorY, radius, victims, striker, players, MobSounds.DOMAIN_HEARTH_CORE.get());
        this.fanBase = heart.getRandom().nextDouble() * Math.PI / 3;
    }

    public int lastKind() {
        return lastKind;
    }

    @Override
    protected void onDeclare(ServerLevel level) {
        level.playSound(null, cx, floorY + 3, cz, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 3f, 0.5f);
    }

    @Override
    protected void declareEffects(ServerLevel level, int t) {
        if (t % 4 == 0) {
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16 + t * 0.05;
                double r = radius * (t / (double) DECLARE_TICKS);
                level.sendParticles(ParticleTypes.FLAME, cx + Math.cos(a) * r, floorY + 0.4, cz + Math.sin(a) * r, 1, 0.1, 0.2, 0.1, 0.02);
            }
        }
    }

    @Override
    protected void rule(ServerLevel level, int t) {
        if (t < FIRST || (t - FIRST) % STEP != 0) {
            return;
        }
        int kind = steps++ % 4;
        lastKind = kind;
        double r = radius;
        switch (kind) {
            case 0 -> {
                BossCue.send(heart, "ember", "core_rings", WARN);
                band(level, r * 0.75, r);
                band(level, r * 0.25, r * 0.5);
            }
            case 1 -> {
                BossCue.send(heart, "ember", "core_rings", WARN);
                band(level, r * 0.5, r * 0.75);
                band(level, 0, r * 0.25);
            }
            default -> {
                BossCue.send(heart, "ember", "core_fans", WARN);
                double base = fanBase + (kind == 3 ? Math.PI / 3 : 0);
                for (int i = 0; i < 3; i++) {
                    fan(level, base + Math.PI * 2 * i / 3);
                }
            }
        }
    }

    private void band(ServerLevel level, double in, double out) {
        TelegraphShape shape = in <= 0.01 ? TelegraphShape.circle(cx, cz, out) : TelegraphShape.ring(cx, cz, in, out);
        heat(level, shape, (in + out) / 2);
    }

    private void fan(ServerLevel level, double angle) {
        heat(level, TelegraphShape.cone(cx, cz, angle, radius + 1, Math.PI / 6), radius * 0.6);
    }

    private void heat(ServerLevel level, TelegraphShape shape, double rr) {
        tg(level, shape, WARN)
                .active(ACTIVE).repeatEvery(REPEAT)
                .onImpact(s -> {
                    s.playSound(null, cx, floorY, cz, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.9f, 0.7f);
                    for (int i = 0; i < 12; i++) {
                        double a = Math.PI * 2 * i / 12;
                        s.sendParticles(ParticleTypes.FLAME, cx + Math.cos(a) * rr, floorY + 0.4, cz + Math.sin(a) * rr, 3, 0.4, 0.2, 0.4, 0.04);
                    }
                })
                .onHit((s, p) -> {
                    striker.accept(p, DAMAGE);
                    p.setRemainingFireTicks(Math.max(p.getRemainingFireTicks(), 30));
                })
                .start();
    }

    @Override
    protected void ambient(ServerLevel level, int t) {
        double a = heart.getRandom().nextDouble() * Math.PI * 2;
        double r = heart.getRandom().nextDouble() * radius;
        level.sendParticles(ParticleTypes.SMALL_FLAME, cx + Math.cos(a) * r, floorY + 0.3, cz + Math.sin(a) * r, 2, 0.3, 0.1, 0.3, 0.01);
        level.sendParticles(ParticleTypes.ASH, cx + Math.cos(a) * r, floorY + 2.5, cz + Math.sin(a) * r, 6, 1.5, 1.0, 1.5, 0.0);
    }

    @Override
    protected void onEnd(ServerLevel level, boolean shattered) {
        level.playSound(null, cx, floorY + 2, cz, shattered ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.FIRE_EXTINGUISH,
                SoundSource.HOSTILE, shattered ? 1.8f : 2.5f, shattered ? 0.8f : 0.7f);
        if (shattered) {
            level.sendParticles(ParticleTypes.LAVA, cx, floorY + 2, cz, 50, 3, 1, 3, 0.2);
        }
    }

    // ---------------------------------------------------------------- 仕掛け: 熾の核 (三つ)
    // 核は赤く燃える。核のそば (QUENCH_RANGE) でしゃがみ続けると冷める (しゃがんだ人 1 人あたり毎 tick 1、三人まで。QUENCH = 90 で冷めきる =
    // 一人で 4.5 秒)。離れると 3 tick に 1 ずつ戻る (熱い床の予告を避けて離れても、進みはほとんど失わない)。冷めた核は灰色になって、そのまま。
    // 叩いても冷めない (しゃがむ手がかりの一行が出る)
    public static final int QUENCH = 90;
    public static final double QUENCH_RANGE = 2.6;

    @Override
    protected void puzzleTick(ServerLevel level, int t) {
        java.util.List<ServerPlayer> room = inRoom();
        for (Node n : nodes) {
            if (n.done) {
                continue;
            }
            int near = 0;
            for (ServerPlayer p : room) {
                if (p.isAlive() && !p.isSpectator() && p.isShiftKeyDown() && Math.hypot(p.getX() - n.pos.x, p.getZ() - n.pos.z) <= QUENCH_RANGE
                        && Math.abs(p.getY() - n.pos.y) < 2.5) {
                    near++;
                }
            }
            if (near > 0) {
                n.state = Math.min(QUENCH, n.state + Math.min(near, 3));
                if (t % 5 == 0) {
                    burst(level, n, ParticleTypes.CLOUD, 6, 0.4, 0.05);
                    tone(level, n.pos, SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.2f);
                }
                if (n.state >= QUENCH) {
                    n.done = true;
                    burst(level, n, ParticleTypes.LARGE_SMOKE, 30, 0.6, 0.1);
                    tone(level, n.pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, 2.5f, 0.8f);
                    tell(level, "message.skyabyss.domain.hearth_core.step", solvedCount(), NODES);
                }
            } else if (n.state > 0 && t % 3 == 0) {
                // 離れると、ゆっくり戻る (予告を避けて少し離れても、進みは大きく失わない)
                n.state--;
            }
        }
    }

    @Override
    protected boolean strike(ServerLevel level, Node node, ServerPlayer player) {
        tell(level, "message.skyabyss.domain.hearth_core.hint");
        tone(level, node.pos, SoundEvents.FIRE_AMBIENT, 1.0f, 0.8f);
        return true;
    }

    @Override
    public int solvedCount() {
        int n = 0;
        for (Node node : nodes) {
            if (node.done) {
                n++;
            }
        }
        return n;
    }

    @Override
    protected boolean complete() {
        return nodes.size() == NODES && solvedCount() == NODES;
    }

    @Override
    protected int markColor(Node n, int t) {
        if (n.done) {
            return 0x7A8088;
        }
        float f = n.state / (float) QUENCH;
        int r = 255;
        int g = (int) (80 + 120 * f);
        int b = (int) (20 + 180 * f);
        return (r << 16) | (g << 8) | b;
    }
}
