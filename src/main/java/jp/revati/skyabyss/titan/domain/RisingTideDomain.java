package jp.revati.skyabyss.titan.domain;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.titan.tide.heart.TideHeartArena;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

// 潮殻の心臓の領域「満ち潮」(docs/design/08 の 5.5)。床が潮に洗われる。外から内へ満ちる四つの輪 (満ち) と、内から外へ引く四つの輪 (引き) が、
// 一巡 (RING_STEP × 8 + 余り) ごとにくり返す。
//  輪 k (0 外〜3 内): 幅 3.5 の輪 [R − 3.5(k+1), R − 3.5k]。予告 1.5 秒、そのあと 1.8 秒のあいだ 12 tick ごとに傷 (高さは床から 1.9 まで = 島の上は濡れない)
//  満ち: 輪 0 → 1 → 2 → 3 の順に RING_STEP (24 tick) 間隔、引き: 輪 3 → 2 → 1 → 0。満ちの終わりと引きの始まりの間は 0.5 秒あけ、
//  どの瞬間も「いま予告が出ているか・まだ予告の出ていない」安全な輪と、島 (四つの岩の島) がある。同時に出ている輪は三つまで
//  中心の円 (輪 3) は満ちの最後・引きの最初に当たるので、心臓を殴れる窓は一巡のうち、満ちの始め (約 5 秒) と引きの終わり
// 本物の水も床に満ちる (TideHeartArena の満ち潮。見た目)。技の名前: tide.flood_rise / tide.flood_ebb。傷 9 × 最大 4 回
public final class RisingTideDomain extends HeartDomain {
    public static final String ID = "rising_tide";
    public static final int RING_STEP = 24;
    public static final int RING_WARN = 30;
    public static final int RING_ACTIVE = 36;
    public static final int RING_REPEAT = 12;
    public static final double RING_WIDTH = 3.5;
    public static final int RINGS = 4;
    public static final int FIRST = 24;
    public static final int EBB_START = RING_STEP * 3 + 48;
    public static final int CYCLE = EBB_START + RING_STEP * 3 + 40;
    public static final float RING_DAMAGE = 9f;
    public static final double FLOOR_REACH = 1.9;

    public RisingTideDomain(Mob heart, double cx, double cz, double floorY, double radius, Predicate<ServerPlayer> victims,
            BiConsumer<ServerPlayer, Float> striker, Supplier<Collection<ServerPlayer>> players) {
        super(heart, ID, cx, cz, floorY, radius, victims, striker, players, MobSounds.DOMAIN_RISING_TIDE.get());
    }

    // 輪 k の内側・外側の半径 (半径が 14 より小さくても、四つの輪で床をちょうど覆う)
    public double inner(int k) {
        return Math.max(0, radius - RING_WIDTH * (k + 1) * (radius / (RING_WIDTH * RINGS)));
    }

    public double outer(int k) {
        return radius - RING_WIDTH * k * (radius / (RING_WIDTH * RINGS));
    }

    @Override
    protected void onDeclare(ServerLevel level) {
        if (heart.level() == level && Math.abs(cx - (TideHeartArena.CX + 0.5)) < 2) {
            TideHeartArena.startFlood(level, DECLARE_TICKS + ACTIVE_TICKS - 20);
        }
        level.playSound(null, cx, floorY + 2, cz, MobSounds.TIDE_HEART_HURT.get(), SoundSource.HOSTILE, 2f, 0.5f);
    }

    @Override
    protected void declareEffects(ServerLevel level, int t) {
        if (t % 4 == 0) {
            double r = radius * (1 - t / (double) DECLARE_TICKS);
            for (int i = 0; i < 32; i++) {
                double a = Math.PI * 2 * i / 32;
                level.sendParticles(ParticleTypes.SPLASH, cx + Math.cos(a) * r, floorY + 0.5, cz + Math.sin(a) * r, 2, 0.2, 0.1, 0.2, 0.1);
            }
        }
    }

    @Override
    protected void rule(ServerLevel level, int t) {
        if (t < FIRST) {
            return;
        }
        int local = (t - FIRST) % CYCLE;
        // 満ち: 外から内へ
        for (int i = 0; i < RINGS; i++) {
            if (local == i * RING_STEP) {
                if (i == 0) {
                    BossCue.send(heart, "tide", "flood_rise", RING_WARN);
                }
                ring(level, i);
            }
        }
        // 引き: 内から外へ
        for (int i = 0; i < RINGS; i++) {
            if (local == EBB_START + i * RING_STEP) {
                if (i == 0) {
                    BossCue.send(heart, "tide", "flood_ebb", RING_WARN);
                }
                ring(level, RINGS - 1 - i);
            }
        }
    }

    private void ring(ServerLevel level, int k) {
        double in = inner(k);
        double out = outer(k);
        double mid = (in + out) / 2;
        TelegraphShape shape = k == RINGS - 1 ? TelegraphShape.circle(cx, cz, out) : TelegraphShape.ring(cx, cz, in, out);
        tg(level, shape, RING_WARN)
                .reach(FLOOR_REACH).active(RING_ACTIVE).repeatEvery(RING_REPEAT)
                .onImpact(s -> {
                    s.playSound(null, cx, floorY, cz, MobSounds.TIDE_HEART_HURT.get(), SoundSource.HOSTILE, 1.4f, 0.7f + 0.1f * k);
                    for (int i = 0; i < 24; i++) {
                        double a = Math.PI * 2 * i / 24;
                        s.sendParticles(ParticleTypes.SPLASH, cx + Math.cos(a) * mid, floorY + 0.8, cz + Math.sin(a) * mid, 6, 0.6, 0.2, 0.6, 0.2);
                    }
                })
                .onHit((s, p) -> striker.accept(p, RING_DAMAGE))
                .start();
    }

    @Override
    protected void ambient(ServerLevel level, int t) {
        double a = heart.getRandom().nextDouble() * Math.PI * 2;
        double r = heart.getRandom().nextDouble() * radius;
        level.sendParticles(ParticleTypes.BUBBLE_POP, cx + Math.cos(a) * r, floorY + 1.0, cz + Math.sin(a) * r, 3, 0.4, 0.3, 0.4, 0.01);
    }

    @Override
    protected void onEnd(ServerLevel level, boolean shattered) {
        if (Math.abs(cx - (TideHeartArena.CX + 0.5)) < 2) {
            TideHeartArena.drainNow(level);
        }
        level.playSound(null, cx, floorY + 2, cz, MobSounds.TIDE_HEART_HURT.get(), SoundSource.HOSTILE, 2.5f, shattered ? 1.4f : 0.8f);
    }

    @Override
    public void abort(ServerLevel level) {
        boolean was = running();
        super.abort(level);
        if (was && Math.abs(cx - (TideHeartArena.CX + 0.5)) < 2) {
            TideHeartArena.drainNow(level);
        }
    }
}
