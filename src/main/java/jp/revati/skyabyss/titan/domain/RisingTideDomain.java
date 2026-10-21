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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

// 潮殻の心臓の領域「満ち潮」(docs/design/08 の 5.5)。床が潮に洗われる。外から内へ満ちる四つの輪 (満ち) と、内から外へ引く四つの輪 (引き) が、
// 一巡 (RING_STEP × 8 + 余り) ごとにくり返す。
//  輪 k (0 外〜3 内): 幅 3.5 の輪 [R − 3.5(k+1), R − 3.5k]。予告 1.5 秒、そのあと 1.5 秒のあいだ 12 tick ごとに傷 (高さは床から 1.9 まで = 島の上は濡れない)
//  満ち: 輪 0 → 1 → 2 → 3 の順に RING_STEP (24 tick) 間隔、引き: 輪 3 → 2 → 1 → 0。満ちの終わりと引きの始まりの間は 0.5 秒あけ、
//  どの瞬間も「いま予告が出ているか・まだ予告の出ていない」安全な輪と、島 (四つの岩の島) がある。同時に出ている輪は三つまで
//  中心の円 (輪 3) は満ちの最後・引きの最初に当たるので、心臓を殴れる窓は一巡のうち、満ちの始め (約 5 秒) と引きの終わり
// 本物の水も床に満ちる (TideHeartArena の満ち潮。見た目)。技の名前: tide.flood_rise / tide.flood_ebb。傷 8 × 最大 3 回
public final class RisingTideDomain extends HeartDomain {
    public static final String ID = "rising_tide";
    public static final int RING_STEP = 24;
    public static final int RING_WARN = 30;
    public static final int RING_ACTIVE = 30;
    public static final int RING_REPEAT = 12;
    public static final double RING_WIDTH = 2.5;   // 2026-10-22 の二人の試遊で細く (3.5 → 2.5)
    public static final int RINGS = 4;
    public static final int FIRST = 24;
    public static final int EBB_START = RING_STEP * 3 + 48;
    public static final int CYCLE = EBB_START + RING_STEP * 3 + 40;
    public static final float RING_DAMAGE = 8f;
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
            TideHeartArena.startFlood(level, DECLARE_TICKS + 20 * 60 * 3);
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
        int step = fast(RING_STEP);
        int ebbStart = step * 3 + 48;
        int local = (t - FIRST) % (ebbStart + step * 3 + 40);
        // 満ち: 外から内へ
        for (int i = 0; i < RINGS; i++) {
            if (local == i * step) {
                if (i == 0) {
                    BossCue.send(heart, "tide", "flood_rise", RING_WARN);
                }
                ring(level, i);
            }
        }
        // 引き: 内から外へ
        for (int i = 0; i < RINGS; i++) {
            if (local == ebbStart + i * step) {
                if (i == 0) {
                    BossCue.send(heart, "tide", "flood_ebb", RING_WARN);
                }
                ring(level, RINGS - 1 - i);
            }
        }
    }

    // 巡の合間: 外の輪と内の円が一度に満ちる (島の上は濡れない)
    @Override
    protected void interludeAttack(ServerLevel level) {
        ring(level, 0);
        ring(level, RINGS - 1);
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

    // ---------------------------------------------------------------- 仕掛け: 潮の水門 (三つ)
    // 水門は岩の島の上 (四つのうち三つ。島は満ち潮の輪が届かない) に立つ。叩くと OPEN_TICKS (45 秒) のあいだ開く。
    // 三つが同時に開いていれば潮が引いて解ける。残りが WARN_TICKS (8 秒) を切ると水門が赤く点滅し、音で知らせる。叩き直せば 45 秒に戻る
    public static final int OPEN_TICKS = 900;
    public static final int WARN_TICKS = 160;
    // 巡ごとの水門の開いている時間 (OPEN_TICKS の割合): 45 秒 → 30 秒 → 21 秒。新しい巡ごとに島の組み合わせも替わる
    public static final double[] OPEN_SCALE = {1.0, 0.67, 0.47};
    private int lastSkip = -1;

    public int openTicks() {
        return fast((int) Math.round(OPEN_TICKS * OPEN_SCALE[Math.min(round(), OPEN_SCALE.length - 1)]));
    }

    @Override
    protected java.util.List<Vec3> nodeSpots() {
        if (Math.abs(cx - (TideHeartArena.CX + 0.5)) < 2) {
            int skip;
            do {
                skip = heart.getRandom().nextInt(4);
            } while (skip == lastSkip);
            lastSkip = skip;
            java.util.List<Vec3> out = new java.util.ArrayList<>();
            int k = 0;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    if (k++ == skip) {
                        continue;
                    }
                    out.add(new Vec3(TideHeartArena.CX + 0.5 + sx * TideHeartArena.ISLAND_OFFSET, floorY + TideHeartArena.ISLAND_HEIGHT,
                            TideHeartArena.CZ + 0.5 + sz * TideHeartArena.ISLAND_OFFSET));
                }
            }
            return out;
        }
        return super.nodeSpots();
    }

    @Override
    protected Hint standing() {
        for (Node n : nodes) {
            if (n.done && n.state < WARN_TICKS) {
                return new Hint("gui.skyabyss.domain.rising_tide.warn", solvedCount(), NODES);
            }
        }
        return new Hint("gui.skyabyss.domain.rising_tide.keep", solvedCount(), NODES);
    }

    @Override
    protected Block bodyBlock() {
        return Blocks.PRISMARINE_BRICKS;
    }

    @Override
    protected float bodyWidth() {
        return 1.4f;
    }

    @Override
    protected Block capBlock(boolean lit) {
        return lit ? Blocks.SEA_LANTERN : Blocks.DARK_PRISMARINE;
    }

    @Override
    protected boolean lit(Node n, int t) {
        return n.done;
    }

    @Override
    protected boolean strike(ServerLevel level, Node node, ServerPlayer player) {
        boolean wasOpen = node.state > 0;
        node.state = openTicks();
        node.done = true;
        tone(level, node.pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 2.5f, 1.0f);
        tone(level, node.pos, SoundEvents.BARREL_OPEN, 1.5f, 0.6f);
        burst(level, node, ParticleTypes.BUBBLE_POP, 24, 0.5, 0.1);
        if (!wasOpen) {
            tell(level, "message.skyabyss.domain.rising_tide.open", solvedCount(), NODES);
        }
        return true;
    }

    @Override
    protected void puzzleTick(ServerLevel level, int t) {
        for (Node n : nodes) {
            if (n.state <= 0) {
                continue;
            }
            n.state--;
            if (n.state == 0) {
                n.done = false;
                n.flash = 16;
                n.flashRgb = 0xE0402A;
                tone(level, n.pos, SoundEvents.IRON_DOOR_CLOSE, 2.0f, 0.6f);
                burst(level, n, ParticleTypes.SMOKE, 12, 0.3, 0.02);
                tell(level, "message.skyabyss.domain.rising_tide.close");
            } else if (n.state < WARN_TICKS && n.state % 20 == 0) {
                tone(level, n.pos, SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 1.6f);
            }
        }
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
        if (!n.done) {
            return 0xB9C4D2;
        }
        return n.state < WARN_TICKS && (t / 6) % 2 == 0 ? 0xFF6A4D : 0x4DC3FF;
    }
}
