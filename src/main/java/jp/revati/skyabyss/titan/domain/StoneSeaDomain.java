package jp.revati.skyabyss.titan.domain;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

// 岩鯨の心臓の領域「石の海」(docs/design/02 の 5.9)。床が石の海になり、心臓から波が打ち寄せる。
//  石の波 (STONE_WAVE): 44 tick ごとに、心臓から外へ広がる輪 (跳べば越える)。輪には安全な筋 (緑の扇) が二つ (三本目の波は三つ) で、
//     筋の場所は波ごとに ±55° ずつ向きを替えて動く (一方向に回り続けても、次の筋はこちらへは来ない)。三波に一度、二つ目の輪が 14 tick 遅れて続く
//  石棘の輪 (STONE_RING): 二波に一度、波と同時に、心臓から半径 6.5 / 10.5 の円周に石の棘が 6 本 (半径 1.5 の円。予告 1.2 秒)。棘の間は安全
// 技の名前: whale.sea_wave / whale.sea_spike。傷: 波 16、棘 18 (心臓の通常の技と同じ重さ)。
public final class StoneSeaDomain extends HeartDomain {
    public static final String ID = "stone_sea";
    public static final int WAVE_EVERY = 44;
    public static final int WAVE_WARN = 28;
    public static final double WAVE_SPEED = 0.45;
    public static final double WAVE_START = 2.0;
    public static final double WAVE_HALF_THICKNESS = 0.7;   // 2026-10-22 の二人の試遊で細く (1.0 → 0.7)
    public static final double GAP_HALF = Math.toRadians(24);
    public static final float WAVE_DAMAGE = 16f;
    public static final int SPIKE_WARN = 24;
    public static final double SPIKE_RADIUS = 1.1;   // 2026-10-22: 1.5 → 1.1
    public static final float SPIKE_DAMAGE = 18f;

    private double gapBase;
    private int waves;
    // 試験用: 最後に出した波の安全な筋の向き
    private double[] lastGaps = new double[0];

    public StoneSeaDomain(Mob heart, double cx, double cz, double floorY, double radius, Predicate<ServerPlayer> victims,
            BiConsumer<ServerPlayer, Float> striker, Supplier<Collection<ServerPlayer>> players) {
        super(heart, ID, cx, cz, floorY, radius, victims, striker, players, MobSounds.DOMAIN_STONE_SEA.get());
        this.gapBase = heart.getRandom().nextDouble() * Math.PI * 2;
    }

    public double[] lastGaps() {
        return lastGaps;
    }

    @Override
    protected void onDeclare(ServerLevel level) {
        level.playSound(null, cx, floorY + 3, cz, MobSounds.STONE_HEART_ROAR.get(), SoundSource.HOSTILE, 3f, 0.8f);
    }

    @Override
    protected void declareEffects(ServerLevel level, int t) {
        if (t % 6 == 0) {
            double r = (t / (double) DECLARE_TICKS) * radius;
            for (int i = 0; i < 28; i++) {
                double a = Math.PI * 2 * i / 28;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                        cx + Math.cos(a) * r, floorY + 0.3, cz + Math.sin(a) * r, 2, 0.2, 0.2, 0.2, 0.05);
            }
        }
        if (t == DECLARE_TICKS - 1) {
            level.playSound(null, cx, floorY + 1, cz, MobSounds.STONE_HEART_SHOCKWAVE.get(), SoundSource.HOSTILE, 3f, 0.7f);
        }
    }

    @Override
    protected void rule(ServerLevel level, int t) {
        if (t < 30 || (t - 30) % fast(WAVE_EVERY) != 0) {
            return;
        }
        fireWave(level, waves++);
    }

    // 巡の合間: 波を一本 (筋は二つ。棘・二つ目の輪はなし)
    @Override
    protected void interludeAttack(ServerLevel level) {
        fireWave(level, 0);
    }

    private void fireWave(ServerLevel level, int n) {
        // 筋の向きは波ごとに ±55°、向きも替える
        gapBase += (n % 2 == 0 ? 1 : -1) * Math.toRadians(55) + Math.toRadians(17);
        int gapCount = n % 3 == 2 ? 3 : 2;
        double[] gaps = new double[gapCount];
        for (int i = 0; i < gapCount; i++) {
            gaps[i] = gapBase + Math.PI * 2 * i / gapCount;
        }
        lastGaps = gaps;
        BossCue.send(heart, "whale", "sea_wave", WAVE_WARN);
        wave(level, gaps, 0);
        for (double gap : gaps) {
            tg(level, TelegraphShape.cone(cx, cz, gap, radius + 1, GAP_HALF), WAVE_WARN).safe().start();
        }
        if (n % 3 == 1) {
            wave(level, gaps, 14);
        }
        if (n % 2 == 1) {
            BossCue.send(heart, "whale", "sea_spike", SPIKE_WARN);
            double ring = n % 4 == 1 ? 6.5 : 10.5;
            double off = gapBase * 0.5;
            for (int i = 0; i < 6; i++) {
                double a = off + Math.PI * 2 * i / 6;
                spike(level, cx + Math.cos(a) * ring, cz + Math.sin(a) * ring);
            }
        }
    }

    private void wave(ServerLevel level, double[] gaps, int extraWarn) {
        double reach = radius + 0.5;
        int active = (int) Math.ceil((reach - WAVE_START) / WAVE_SPEED);
        tg(level, TelegraphShape.wave(cx, cz, WAVE_START, reach, WAVE_HALF_THICKNESS, gaps, GAP_HALF), WAVE_WARN + extraWarn)
                .active(active).jumpClear(0.9)
                .onImpact(s -> {
                    s.playSound(null, cx, floorY + 1, cz, MobSounds.STONE_HEART_SHOCKWAVE.get(), SoundSource.HOSTILE, 2.2f, 0.9f);
                    s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), cx, floorY + 0.5, cz, 50, 2.0, 0.4, 2.0, 0.2);
                })
                .onHit((s, p) -> striker.accept(p, WAVE_DAMAGE))
                .start();
    }

    private void spike(ServerLevel level, double x, double z) {
        tg(level, TelegraphShape.circle(x, z, SPIKE_RADIUS), SPIKE_WARN)
                .onImpact(s -> {
                    s.playSound(null, x, floorY, z, MobSounds.STONE_HEART_SPIKE_BURST.get(), SoundSource.HOSTILE, 1.3f, 1.0f);
                    s.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), x, floorY + 0.5, z, 20, 0.5, 0.5, 0.5, 0.2);
                })
                .onHit((s, p) -> striker.accept(p, SPIKE_DAMAGE))
                .start();
    }

    @Override
    protected void ambient(ServerLevel level, int t) {
        double a = heart.getRandom().nextDouble() * Math.PI * 2;
        double r = heart.getRandom().nextDouble() * radius;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_STONE_BRICKS.defaultBlockState()),
                cx + Math.cos(a) * r, floorY + 0.4, cz + Math.sin(a) * r, 3, 0.4, 0.3, 0.4, 0.02);
    }

    @Override
    protected void onEnd(ServerLevel level, boolean shattered) {
        level.playSound(null, cx, floorY + 2, cz, shattered ? MobSounds.STONE_HEART_PLATE_LOCK.get() : MobSounds.STONE_HEART_PHASE.get(),
                SoundSource.HOSTILE, 3f, shattered ? 0.7f : 1f);
        if (shattered) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), cx, floorY + 3, cz, 80, 2.5, 1.5, 2.5, 0.3);
        }
    }

    // ---------------------------------------------------------------- 仕掛け: 共鳴する骨 (三本)
    // 骨は三本、それぞれ音程が違う。鳴る順 (旋律。SHOW_GAP tick おきに三つ) を SHOW_EVERY tick ごとにくり返して見せる (光る柱と音)。
    // 同じ順に叩き返せば解ける。順を間違えたら叩いた分は消えて、旋律をすぐ見せ直す (傷はない)。叩くのは左クリックでも右クリックでもよい
    public static final int SHOW_GAP = 22;
    public static final int SHOW_EVERY = 200;
    private static final float[] PITCH = {0.8f, 1.0f, 1.26f};

    // 巡ごとの手本の長さ (骨の鳴る回数): 3 → 4 → 5。巡が変わるたびに新しい手本
    public static final int[] SEQ_LEN = {3, 4, 5};
    private int[] order = {0, 1, 2};
    private int step;
    private int nextShow;
    private int showStart = -1;
    // いま手本のどこまで見せたか (1〜3。見せていないとき 0)
    private int shown;

    @Override
    protected Hint standing() {
        if (step >= NODES) {
            return null;
        }
        return showStart >= 0 ? new Hint("gui.skyabyss.domain.stone_sea.watch", Math.max(1, shown), seq())
                : new Hint("gui.skyabyss.domain.stone_sea.tap", step + 1, seq());
    }

    @Override
    protected Block bodyBlock() {
        return Blocks.BONE_BLOCK;
    }

    @Override
    protected Block capBlock(boolean lit) {
        return lit ? Blocks.GLOWSTONE : Blocks.CHISELED_STONE_BRICKS;
    }

    // 試験用: 鳴る順と、次に叩く骨 (終われば -1)
    public int[] order() {
        return order.clone();
    }

    public int expected() {
        return step >= seq() ? -1 : order[step];
    }

    // いまの巡の手本の長さ
    public int seq() {
        return order.length;
    }

    @Override
    protected void puzzleBegin(ServerLevel level) {
        int len = SEQ_LEN[Math.min(round(), SEQ_LEN.length - 1)];
        java.util.Random rnd = new java.util.Random(heart.getRandom().nextLong());
        int[] prev = order;
        int[] next;
        do {
            next = new int[len];
            if (len == NODES) {
                java.util.List<Integer> list = new java.util.ArrayList<>(java.util.List.of(0, 1, 2));
                java.util.Collections.shuffle(list, rnd);
                for (int i = 0; i < len; i++) {
                    next[i] = list.get(i);
                }
            } else {
                // 同じ骨が続けて鳴らない・三本とも使う
                for (int i = 0; i < len; i++) {
                    int v;
                    do {
                        v = rnd.nextInt(NODES);
                    } while (i > 0 && v == next[i - 1]);
                    next[i] = v;
                }
            }
        } while ((round() > 0 && java.util.Arrays.equals(next, prev)) || !usesAll(next));
        order = next;
        step = 0;
        nextShow = ticks() + 30;
        showStart = -1;
    }

    private static boolean usesAll(int[] seq) {
        boolean[] used = new boolean[NODES];
        for (int v : seq) {
            used[v] = true;
        }
        return used[0] && used[1] && used[2];
    }

    private void note(ServerLevel level, Node n, boolean show) {
        n.flash = show ? 16 : 12;
        n.flashRgb = show ? 0xFFD23C : 0x66E08A;
        tone(level, n.pos, SoundEvents.NOTE_BLOCK_BELL.value(), 2.4f, PITCH[n.index]);
        if (show) {
            // 手本は部屋の人の耳元でも鳴らす (遠くても聞こえる)
            for (ServerPlayer p : inRoom()) {
                p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, PITCH[n.index]);
            }
        }
        burst(level, n, show ? ParticleTypes.END_ROD : ParticleTypes.NOTE, show ? 14 : 6, 0.25, show ? 0.05 : 0.3);
        column(level, n, show ? 0xFFD76A : 0x66E08A, 1.6f, 8);
    }

    @Override
    protected void puzzleTick(ServerLevel level, int t) {
        if (step >= seq()) {
            return;
        }
        if (showStart < 0 && t >= nextShow) {
            showStart = t;
        }
        if (showStart >= 0) {
            int k = t - showStart;
            if (k % fast(SHOW_GAP) == 0 && k / fast(SHOW_GAP) < seq()) {
                shown = k / fast(SHOW_GAP) + 1;
                note(level, nodes.get(order[k / fast(SHOW_GAP)]), true);
            }
            if (k >= fast(SHOW_GAP) * seq() + 20) {
                showStart = -1;
                shown = 0;
                nextShow = t + SHOW_EVERY;
            }
        }
    }

    @Override
    protected boolean strike(ServerLevel level, Node node, ServerPlayer player) {
        if (step >= seq()) {
            return true;
        }
        if (node.index == order[step]) {
            node.done = true;
            step++;
            note(level, node, false);
            if (step < seq()) {
                tell(level, "message.skyabyss.domain.stone_sea.step", step, seq());
            }
        } else {
            step = 0;
            for (Node n : nodes) {
                n.done = false;
            }
            node.flash = 16;
            node.flashRgb = 0xE0402A;
            tone(level, node.pos, SoundEvents.NOTE_BLOCK_BASS.value(), 2.0f, 0.5f);
            burst(level, node, ParticleTypes.SMOKE, 10, 0.3, 0.02);
            tell(level, "message.skyabyss.domain.stone_sea.wrong");
            // 旋律をすぐ見せ直す
            showStart = -1;
            nextShow = ticks() + 30;
        }
        return true;
    }

    @Override
    public int solvedCount() {
        return step * NODES / seq();
    }

    @Override
    protected boolean complete() {
        return step >= seq();
    }

    @Override
    protected int markColor(Node n, int t) {
        return n.done ? 0x66E08A : 0xB9C4D2;
    }
}
