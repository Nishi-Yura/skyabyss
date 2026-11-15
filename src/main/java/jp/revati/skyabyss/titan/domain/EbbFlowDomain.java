package jp.revati.skyabyss.titan.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jp.revati.skyabyss.combat.BossCue;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.titan.tide.heart.TideHeartArena;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 潮殻の心臓の領域「満ち引き」(docs/design/08 の 5.7。2026-11-15 に「合わせ貝」と入れ替え。案 A)。
// 部屋に本当に潮が満ちて引く。低い床は満ち潮のあいだ水に沈み (動きが遅い)、四つの岩の島 (床 +2) は濡れない。
//  一巡 = 満ち潮 (FLOOD) → 引き潮 (EBB) をくり返す。床に「潮の栓」が三つ立ち、**引き潮のあいだだけ** 叩いて抜ける。
//  引き潮のうちに三つとも抜けば巡が済む。間に合わなければ、満ちる潮が栓を押し戻す (抜いた分は戻る。傷はない)。
//  決まり (傷): 満ち潮は「渦潮」(水の中にいる人の足もとに渦。予告 1.4 秒) と「潮しぶき」(島の上の人の足もと)。
//  引き潮は「引き波」(心臓から外へ広がる波。跳べば越える。切れ目が二つ)。巡の合間は人の足もとの渦。
// 部屋の外 (試験の平らな世界など。arenaWater = false) では水を置かず、潮の満ち引きの決まりだけが回る
public final class EbbFlowDomain extends HeartDomain {
    public static final String ID = "ebb_flow";
    // 満ち潮の長さ (水が満ちて満ちたまま。部屋の水は TideHeartArena が 20 tick で満ち 12 tick で引く) と、引き潮の窓
    public static final int FLOOD_TICKS = 140;
    public static final int FIRST_FLOOD_TICKS = 70;
    public static final int EBB_TICKS = 260;
    // 渦潮・しぶき・引き波
    public static final int WHIRL_EVERY = 34;
    public static final int WHIRL_WARN = 28;
    public static final double WHIRL_RADIUS = 2.6;
    public static final float WHIRL_DAMAGE = 12f;
    public static final int SPRAY_EVERY = 52;
    public static final int SPRAY_WARN = 30;
    public static final double SPRAY_RADIUS = 2.2;
    public static final float SPRAY_DAMAGE = 10f;
    public static final int WAVE_FIRST = 24;
    public static final int WAVE_EVERY = 76;
    public static final int WAVE_WARN = 28;
    public static final float WAVE_DAMAGE = 12f;
    // 栓の置き場: 中心からの割合 (島 0.43 と外周の溝のあいだ)。島のない東西南北の向き
    public static final double PLUG_AT = 0.66;

    private final boolean arenaWater;
    // 狙い (回り続けている人には予告が満ちる頃にいる所。心臓の combat/AntiStrafe)。人と予告の長さ → 狙う所
    private final java.util.function.BiFunction<ServerPlayer, Integer, Vec3> aim;
    private boolean flood = true;
    private int tideTicks;
    private int tideLength;
    private int floods;
    private int ebbs;
    private int lastWave = -1;
    private double waveTurn;

    public EbbFlowDomain(Mob heart, double cx, double cz, double floorY, double radius, Predicate<ServerPlayer> victims,
            BiConsumer<ServerPlayer, Float> striker, Supplier<Collection<ServerPlayer>> players, boolean arenaWater,
            java.util.function.BiFunction<ServerPlayer, Integer, Vec3> aim) {
        super(heart, ID, cx, cz, floorY, radius, victims, striker, players, MobSounds.DOMAIN_RISING_TIDE.get());
        this.arenaWater = arenaWater;
        this.aim = aim;
        this.waveTurn = heart.getRandom().nextDouble() * Math.PI * 2;
    }

    // ---- 試験・画面用
    public boolean flooding() {
        return flood;
    }

    public int tideLeft() {
        return Math.max(0, tideLength - tideTicks);
    }

    public int floods() {
        return floods;
    }

    public int ebbs() {
        return ebbs;
    }

    // 試験用: 今の潮をすぐ終える (満ち潮なら引き潮へ、引き潮なら満ち潮へ)
    public void skipTide(ServerLevel level) {
        tideTicks = tideLength;
        turnTide(level);
    }

    // ---- 流れ
    @Override
    protected void onDeclare(ServerLevel level) {
        level.playSound(null, cx, floorY + 3, cz, SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.HOSTILE, 3f, 0.7f);
    }

    @Override
    protected void declareEffects(ServerLevel level, int t) {
        if (t % 4 == 0) {
            double r = radius * (1 - t / (double) DECLARE_TICKS);
            for (int i = 0; i < 18; i++) {
                double a = Math.PI * 2 * i / 18 - t * 0.04;
                level.sendParticles(jp.revati.skyabyss.fx.FxParticles.type(jp.revati.skyabyss.fx.FxParticles.Element.WATER, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), cx + Math.cos(a) * r, floorY + 0.3, cz + Math.sin(a) * r, 2, 0.2, 0.05, 0.2, 0.05);
            }
        }
    }

    @Override
    protected void puzzleBegin(ServerLevel level) {
        // 巡のはじめは満ち潮から (水が満ちるのを見せてから、引き潮で栓を抜く)
        floods = 0;
        ebbs = 0;
        startFlood(level, round() == 0 ? FIRST_FLOOD_TICKS : fast(FLOOD_TICKS));
    }

    private void startFlood(ServerLevel level, int length) {
        flood = true;
        tideTicks = 0;
        tideLength = length;
        floods++;
        if (arenaWater && level.dimension() == heart.level().dimension()) {
            // 満ちる 20 tick + 満ちたまま。引くのは TideHeartArena が DRAIN で
            TideHeartArena.startFlood(level, Math.max(1, length - 20));
        }
        level.playSound(null, cx, floorY + 2, cz, SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE, SoundSource.HOSTILE, 3f, 0.6f);
        announce(level, "message.skyabyss.domain.ebb_flow.flood");
    }

    private void startEbb(ServerLevel level) {
        flood = false;
        tideTicks = 0;
        tideLength = fast(EBB_TICKS);
        ebbs++;
        lastWave = -1;
        level.playSound(null, cx, floorY + 2, cz, SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.HOSTILE, 3f, 0.8f);
        announce(level, "message.skyabyss.domain.ebb_flow.ebb");
        for (Node n : nodes) {
            n.flash = 12;
            n.flashRgb = 0xF6EBC8;
        }
    }

    private void turnTide(ServerLevel level) {
        if (flood) {
            startEbb(level);
            return;
        }
        // 引き潮が終わった: 抜ききれなかった栓は満ちる潮に押し戻される (傷はない)
        if (solvedCount() > 0 && !complete()) {
            for (Node n : nodes) {
                if (n.done) {
                    n.done = false;
                    n.flash = 16;
                    n.flashRgb = 0xE05050;
                }
            }
            tell(level, "message.skyabyss.domain.ebb_flow.pushed");
        }
        startFlood(level, fast(FLOOD_TICKS));
    }

    @Override
    protected void puzzleTick(ServerLevel level, int t) {
        tideTicks++;
        if (tideTicks >= tideLength) {
            turnTide(level);
        }
    }

    @Override
    protected void rule(ServerLevel level, int t) {
        int tt = tideTicks;
        if (flood) {
            if (tt >= 22 && (tt - 22) % fast(WHIRL_EVERY) == 0) {
                BossCue.send(heart, "tide", "ebb_whirl", WHIRL_WARN);
                for (ServerPlayer p : inRoom()) {
                    if (victims.test(p) && !onIsland(p)) {
                        whirl(level, aim.apply(p, WHIRL_WARN), WHIRL_WARN, WHIRL_DAMAGE);
                    }
                }
            }
            if (tt >= 30 && (tt - 30) % fast(SPRAY_EVERY) == 0) {
                for (ServerPlayer p : inRoom()) {
                    if (victims.test(p) && onIsland(p)) {
                        spray(level, aim.apply(p, SPRAY_WARN));
                    }
                }
            }
            return;
        }
        if (tt == WAVE_FIRST || (tt > WAVE_FIRST && (tt - WAVE_FIRST) % fast(WAVE_EVERY) == 0)) {
            if (lastWave != tt) {
                lastWave = tt;
                wave(level);
            }
        }
    }

    // 人が岩の島の上か (部屋の外では島はない)
    private boolean onIsland(ServerPlayer p) {
        return arenaWater && TideHeartArena.onIsland(p.getX(), p.getY(), p.getZ());
    }

    private void whirl(ServerLevel level, Vec3 at, int warn, float damage) {
        double x = at.x;
        double z = at.z;
        tg(level, TelegraphShape.circle(x, z, WHIRL_RADIUS), warn).look(jp.revati.skyabyss.combat.AttackLook.DOMAIN_TIDE_WHIRL)
                .onImpact(s -> {
                    s.playSound(null, x, floorY, z, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.HOSTILE, 1.6f, 0.8f);
                })
                .onHit((s, p) -> {
                    striker.accept(p, damage);
                    // 渦の中心へ少し引き込む
                    Vec3 to = new Vec3(x - p.getX(), 0, z - p.getZ());
                    if (to.lengthSqr() > 0.01) {
                        p.push(to.normalize().x * 0.35, 0.1, to.normalize().z * 0.35);
                        p.hurtMarked = true;
                    }
                })
                .start();
    }

    private void spray(ServerLevel level, Vec3 at) {
        double x = at.x;
        double z = at.z;
        BossCue.send(heart, "tide", "ebb_spray", SPRAY_WARN);
        tg(level, TelegraphShape.circle(x, z, SPRAY_RADIUS), SPRAY_WARN).look(jp.revati.skyabyss.combat.AttackLook.DOMAIN_SHELL_SLAM)
                .onImpact(s -> {
                    s.playSound(null, x, floorY + 2, z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.6f, 0.9f);
                })
                .onHit((s, p) -> striker.accept(p, SPRAY_DAMAGE))
                .start();
    }

    // 引き波: 心臓から外へ広がる波 (跳べば越える)。切れ目が二つ (向き合わせ)。波ごとに切れ目の向きが回る
    private void wave(ServerLevel level) {
        BossCue.send(heart, "tide", "ebb_wave", WAVE_WARN);
        waveTurn += Math.toRadians(55);
        double[] gaps = {waveTurn, waveTurn + Math.PI};
        double gapHalf = Math.toRadians(20);
        double r0 = 3.0;
        int active = (int) Math.ceil((radius - r0) / 0.55);
        tg(level, TelegraphShape.wave(cx, cz, r0, radius, 0.7, gaps, gapHalf), WAVE_WARN).look(jp.revati.skyabyss.combat.AttackLook.DOMAIN_TIDE_WAVE)
                .active(active).jumpClear(0.9)
                .onImpact(s -> s.playSound(null, cx, floorY, cz, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.2f, 0.6f))
                .onHit((s, p) -> {
                    striker.accept(p, WAVE_DAMAGE);
                    Vec3 out = new Vec3(p.getX() - cx, 0, p.getZ() - cz);
                    if (out.lengthSqr() > 0.01) {
                        p.push(out.normalize().x * 0.6, 0.2, out.normalize().z * 0.6);
                        p.hurtMarked = true;
                    }
                })
                .start();
    }

    @Override
    protected void interludeAttack(ServerLevel level) {
        for (ServerPlayer p : inRoom()) {
            if (victims.test(p)) {
                whirl(level, p.position(), WHIRL_WARN, WHIRL_DAMAGE * 0.8f);
            }
        }
    }

    @Override
    protected void ambient(ServerLevel level, int t) {
        double a = heart.getRandom().nextDouble() * Math.PI * 2;
        double r = heart.getRandom().nextDouble() * radius;
        level.sendParticles(jp.revati.skyabyss.fx.FxParticles.type(jp.revati.skyabyss.fx.FxParticles.Element.WATER, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), cx + Math.cos(a) * r, floorY + (flood ? 0.6 : 2.5),
                cz + Math.sin(a) * r, 3, 0.4, 0.1, 0.4, 0.02);
    }

    @Override
    protected void onEnd(ServerLevel level, boolean shattered) {
        drain(level);
        level.playSound(null, cx, floorY + 2, cz, shattered ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.AMBIENT_UNDERWATER_EXIT,
                SoundSource.HOSTILE, shattered ? 1.6f : 2.5f, shattered ? 0.9f : 0.7f);
    }

    @Override
    public void abort(ServerLevel level) {
        drain(level);
        super.abort(level);
    }

    private void drain(ServerLevel level) {
        if (arenaWater) {
            TideHeartArena.drainNow(level);
        }
    }

    // ---- 仕掛け: 潮の栓 (三つ)。島のない東西南北のうち三つの向き (巡ごとに選び直す)
    @Override
    protected List<Vec3> nodeSpots() {
        List<Integer> dirs = new ArrayList<>(List.of(0, 1, 2, 3));
        java.util.Collections.shuffle(dirs, new java.util.Random(heart.getRandom().nextLong()));
        List<Vec3> out = new ArrayList<>();
        for (int k = 0; k < NODES; k++) {
            double a = dirs.get(k) * Math.PI / 2;
            out.add(new Vec3(cx + Math.cos(a) * radius * PLUG_AT, floorY, cz + Math.sin(a) * radius * PLUG_AT));
        }
        return out;
    }

    @Override
    protected boolean strike(ServerLevel level, Node node, ServerPlayer player) {
        if (node.done) {
            return true;
        }
        if (flood) {
            tell(level, "message.skyabyss.domain.ebb_flow.hint");
            tone(level, node.pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1.2f, 0.6f);
            return true;
        }
        node.done = true;
        node.flash = 14;
        node.flashRgb = 0x66E08A;
        burst(level, node, jp.revati.skyabyss.fx.FxParticles.type(jp.revati.skyabyss.fx.FxParticles.Element.WATER, jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), 30, 0.6, 0.2);
        tone(level, node.pos, SoundEvents.BUCKET_EMPTY, 1.6f, 0.7f + 0.15f * solvedCount());
        tell(level, "message.skyabyss.domain.ebb_flow.step", solvedCount(), NODES);
        return true;
    }

    @Override
    protected Hint standing() {
        if (flood) {
            return new Hint("gui.skyabyss.domain.ebb_flow.wait", solvedCount(), NODES);
        }
        return new Hint("gui.skyabyss.domain.ebb_flow.pull", solvedCount(), NODES);
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
    protected Block bodyBlock() {
        return Blocks.DARK_PRISMARINE;
    }

    @Override
    protected float bodyWidth() {
        return 1.0f;
    }

    @Override
    protected Block capBlock(boolean lit) {
        return lit ? Blocks.SEA_LANTERN : Blocks.PRISMARINE_BRICKS;
    }

    @Override
    protected boolean lit(Node n, int t) {
        return !flood && !n.done;
    }

    @Override
    protected int markColor(Node n, int t) {
        if (n.done) {
            return 0x66E08A;
        }
        if (flood) {
            return 0x4A6A88;
        }
        // 引き潮の残りが少ないほど赤みを帯びる
        float left = tideLeft() / (float) Math.max(1, tideLength);
        int r = (int) (246 - 10 * left);
        int g = (int) (120 + 115 * left);
        int b = (int) (90 + 110 * left);
        return (r << 16) | (g << 8) | b;
    }
}
