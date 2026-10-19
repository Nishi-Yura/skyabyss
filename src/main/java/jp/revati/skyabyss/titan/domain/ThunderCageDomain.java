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

// 雷翼の心臓の領域「雷の檻」(docs/design/12 の 5.5)。部屋が雷の格子で区切られる。
// 64 tick ごとに一巡: 格子の線 (間隔 GRID = 7、幅 2.2 = 半幅 1.1) がすべて同時に予告 (1.5 秒) され、落ちる。線は床から天井までの柱
// (空中にいても当たる。昇り風の上でも同じ)。線と線のあいだの 4.8 マス四方の「房」は安全で、心臓は房の一つの中。
//  一巡目: 軸に沿った格子 (中心を通る)。二巡目: 半分 (3.5) ずらした格子。三巡目: 45° 回した格子 — 以後この三つを順にくり返す。
// 房の場所が毎回変わる (回り続けて通る道はいつも線の上になる)。線の数は半径 / 7 で、人数が増えても線の数は変わらない (房は人数ぶん十分ある)。
// 心臓は領域のあいだ床の近くに下りている (空中戦の段階でも、殴れるように)。技の名前: storm.cage_bars / storm.cage_cross。傷 20。
public final class ThunderCageDomain extends HeartDomain {
    public static final String ID = "thunder_cage";
    public static final int PULSE = 64;
    public static final int WARN = 30;
    public static final double GRID = 7.0;
    public static final double HALF_WIDTH = 1.1;
    public static final double REACH = 24;
    public static final float DAMAGE = 20f;
    public static final int FIRST = 24;

    private int pulses;
    // 試験用: 最後に出した格子の種類 (0 軸 / 1 ずらし / 2 斜め)
    private int lastKind = -1;

    public ThunderCageDomain(Mob heart, double cx, double cz, double floorY, double radius, Predicate<ServerPlayer> victims,
            BiConsumer<ServerPlayer, Float> striker, Supplier<Collection<ServerPlayer>> players) {
        super(heart, ID, cx, cz, floorY, radius, victims, striker, players, MobSounds.DOMAIN_THUNDER_CAGE.get());
    }

    public int lastKind() {
        return lastKind;
    }

    @Override
    protected void onDeclare(ServerLevel level) {
        level.playSound(null, cx, floorY + 4, cz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 4f, 0.9f);
    }

    @Override
    protected void declareEffects(ServerLevel level, int t) {
        if (t % 5 == 0) {
            for (int i = 0; i < 6; i++) {
                double a = heart.getRandom().nextDouble() * Math.PI * 2;
                double r = heart.getRandom().nextDouble() * radius;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx + Math.cos(a) * r, floorY + 1 + heart.getRandom().nextDouble() * 6, cz + Math.sin(a) * r, 4, 0.3, 0.6, 0.3, 0.2);
            }
        }
    }

    @Override
    protected void rule(ServerLevel level, int t) {
        if (t < FIRST || (t - FIRST) % PULSE != 0) {
            return;
        }
        int kind = pulses++ % 3;
        lastKind = kind;
        BossCue.send(heart, "storm", kind == 2 ? "cage_cross" : "cage_bars", WARN);
        double length = radius * 2 + 4;
        if (kind == 2) {
            lines(level, Math.PI / 4, 0, length);
            lines(level, Math.PI * 3 / 4, 0, length);
        } else {
            double off = kind == 1 ? GRID / 2 : 0;
            lines(level, 0, off, length);
            lines(level, Math.PI / 2, off, length);
        }
    }

    // 角度 theta の線を、垂直の向きに GRID おきに並べる (中心を通る線 + off ずらし)
    private void lines(ServerLevel level, double theta, double off, double length) {
        double tx = Math.cos(theta);
        double tz = Math.sin(theta);
        double nx = -tz;
        double nz = tx;
        int max = (int) Math.ceil((radius + 2) / GRID);
        boolean first = true;
        for (int k = -max; k <= max; k++) {
            double d = off + k * GRID;
            if (Math.abs(d) > radius + HALF_WIDTH) {
                continue;
            }
            double sx = cx + nx * d - tx * length / 2;
            double sz = cz + nz * d - tz * length / 2;
            boolean sounds = first;
            first = false;
            tg(level, TelegraphShape.line(sx, sz, theta, length, HALF_WIDTH), WARN)
                    .reach(REACH)
                    .onImpact(s -> {
                        if (sounds) {
                            s.playSound(null, cx, floorY + 3, cz, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.6f, 1.4f);
                        }
                        for (double u = 0; u <= length; u += 3.5) {
                            s.sendParticles(ParticleTypes.ELECTRIC_SPARK, sx + tx * u, floorY + 1.5, sz + tz * u, 4, 0.2, 1.2, 0.2, 0.15);
                        }
                    })
                    .onHit((s, p) -> striker.accept(p, DAMAGE))
                    .start();
        }
    }

    @Override
    protected void ambient(ServerLevel level, int t) {
        double a = heart.getRandom().nextDouble() * Math.PI * 2;
        double r = heart.getRandom().nextDouble() * radius;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx + Math.cos(a) * r, floorY + 0.5 + heart.getRandom().nextDouble() * 5, cz + Math.sin(a) * r, 2, 0.3, 0.5, 0.3, 0.05);
    }

    @Override
    protected void onEnd(ServerLevel level, boolean shattered) {
        level.playSound(null, cx, floorY + 3, cz, shattered ? SoundEvents.LIGHTNING_BOLT_IMPACT : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.HOSTILE, 3f, shattered ? 1.2f : 1.4f);
        if (shattered) {
            level.sendParticles(ParticleTypes.END_ROD, cx, floorY + 3, cz, 100, 3, 1.5, 3, 0.4);
        }
    }

    // ---------------------------------------------------------------- 仕掛け: 避雷の柱 (三本)
    // 柱は順に (一本ずつ) 雷を溜める。溜まっている柱は強く光り、音が鳴る。CHARGE tick のあいだに叩けば、雷は地へ逃げて柱は済む。
    // 叩かないまま溜まりきると、最後の TELE_WARN tick に柱のまわりに予告 (床の円) が出て、雷が柱の足もとへ落ちる (予告は叩けば消える)。
    // 散ったあとは RESET tick おいて、同じ柱がまた溜まる。人が多ければ、近くの人が叩ける
    public static final int CHARGE = 160;
    public static final int TELE_WARN = 28;
    public static final int GAP = 30;
    public static final int RESET = 70;
    public static final double ROD_RADIUS = 3.4;
    public static final float ROD_DAMAGE = 14f;

    private int[] order = {0, 1, 2};
    private int stage;
    private int chargeStart = -1;
    private int readyAt;
    private Telegraph pending;

    public int[] order() {
        return order.clone();
    }

    // 試験用: いま溜まっている柱 (なければ -1) と、溜まり始めの tick
    public int charging() {
        return chargeStart >= 0 && stage < NODES ? order[stage] : -1;
    }

    public int chargeStart() {
        return chargeStart;
    }

    @Override
    protected void puzzleBegin(ServerLevel level) {
        java.util.List<Integer> list = new java.util.ArrayList<>(java.util.List.of(0, 1, 2));
        java.util.Collections.shuffle(list, new java.util.Random(heart.getRandom().nextLong()));
        order = new int[] {list.get(0), list.get(1), list.get(2)};
        stage = 0;
        chargeStart = -1;
        readyAt = 50;
        pending = null;
    }

    @Override
    protected void puzzleTick(ServerLevel level, int t) {
        if (stage >= NODES) {
            return;
        }
        Node n = nodes.get(order[stage]);
        if (chargeStart < 0) {
            if (t >= readyAt) {
                chargeStart = t;
                tone(level, n.pos, SoundEvents.BEACON_POWER_SELECT, 3.0f, 1.8f);
                tell(level, "message.skyabyss.domain.thunder_cage.charging");
            }
            return;
        }
        int k = t - chargeStart;
        if (k % 8 == 0) {
            burst(level, n, ParticleTypes.ELECTRIC_SPARK, 6 + k / 12, 0.4, 0.3);
            ring(level, n.pos, ROD_RADIUS, k > CHARGE - TELE_WARN ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK, 12);
        }
        if (k % 20 == 0) {
            tone(level, n.pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f + k / 200f, 1.8f);
        }
        if (k == CHARGE - TELE_WARN) {
            double x = n.pos.x;
            double z = n.pos.z;
            pending = tg(level, TelegraphShape.circle(x, z, ROD_RADIUS), TELE_WARN)
                    .onImpact(s -> s.playSound(null, x, floorY + 1, z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.5f, 1.2f))
                    .onHit((s, p) -> striker.accept(p, ROD_DAMAGE))
                    .start();
        }
        if (k >= CHARGE) {
            // 溜まりきって落ちた (傷は予告が出した)。柱はしばらくして、また溜まる
            pending = null;
            chargeStart = -1;
            readyAt = t + RESET;
            burst(level, n, ParticleTypes.FLASH, 1, 0.1, 0);
            tell(level, "message.skyabyss.domain.thunder_cage.spent");
        }
    }

    @Override
    protected boolean strike(ServerLevel level, Node node, ServerPlayer player) {
        if (stage >= NODES) {
            return true;
        }
        if (chargeStart >= 0 && node.index == order[stage]) {
            node.done = true;
            if (pending != null) {
                pending.cancel();
                pending = null;
            }
            chargeStart = -1;
            stage++;
            readyAt = ticks() + GAP;
            tone(level, node.pos, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.6f, 1.4f);
            burst(level, node, ParticleTypes.ELECTRIC_SPARK, 40, 0.6, 0.5);
            tell(level, "message.skyabyss.domain.thunder_cage.grounded", stage, NODES);
        } else {
            tone(level, node.pos, SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f, 0.8f);
            tell(level, "message.skyabyss.domain.thunder_cage.idle");
        }
        return true;
    }

    @Override
    public int solvedCount() {
        return stage;
    }

    @Override
    protected boolean complete() {
        return stage >= NODES;
    }

    @Override
    protected int markColor(Node n, int t) {
        if (n.done) {
            return 0x66E08A;
        }
        if (charging() == n.index) {
            return (t / 4) % 2 == 0 ? 0xFFF6A0 : 0xFFD23C;
        }
        return 0x7C93B8;
    }
}
