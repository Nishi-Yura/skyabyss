package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// 「一方向に回り続けるだけ」への、心臓ごとの追い技 (四つの心臓が一つずつ持つ。docs/design の各心臓の「回り続けを許さない」の節)。
// AntiStrafe が STRAFE_TICKS (1.2 秒) 回り続けた人を見つけ、さらに PURSUE_TICKS (2 秒) 続くと、心臓の技の合間に、その人だけを狙う技を 3 つの型から出す。
// どれも予告 (床の印) は 0.8 秒以上、避け方は「止まる / 逆へ回る / 跳ぶ / 内外へ出る」。回り続けるだけでは必ず当たる形:
//  先読み  (LEAD)  : 予告が満ちる頃にいる所へ円。14 tick あとに、さらにその先へ二つ目の円 (二度目は、まだ回っていれば)
//  行く手  (CUTOFF): 回っている向きの先を扇でふさぐ (予告が満ちる頃の位置が扇の中)
//  追い薙ぎ (CHASE): 背後から回り込む薙ぎ (跳べば越える)。回る向きと同じ向きに、人より速く振れる
// 技の名前は BossCue で「ボスバーの下の一行」に出る。傷は心臓ごとの theme.damage (その心臓の中くらいの技と同じ重さ)。
// 間隔は 4.5〜6 秒。回っていない人には一切出ない (止まる・逆へ回ると、ここでやり直し)。人数が多いときは、回っている人ごとに最大 ceil(人数 / 3) 人まで。
public final class HeartPursuit {
    public static final int PURSUE_TICKS = 40;
    public static final int LEAD_WARN = 26;
    public static final int LEAD_SECOND_DELAY = 14;
    public static final int LEAD_SECOND_WARN = 24;
    public static final double LEAD_RADIUS = 2.6;
    public static final int CUTOFF_WARN = 28;
    public static final double CUTOFF_HALF = 0.42;
    public static final int CHASE_WARN = 26;
    public static final int CHASE_ACTIVE = 24;
    public static final double CHASE_HALF_WIDTH = 1.25;
    public static final int GAP_MIN = 90;
    public static final int GAP_SPAN = 30;
    public static final String[] KEYS = {"lead", "cutoff", "chase"};

    // 心臓ごとの数: 名前 (gui.skyabyss.attack.<bossKey>.<lead|cutoff|chase>)、傷 (防具の前)、見た目と音
    public record Theme(String bossKey, float damage, ParticleOptions fx, SoundEvent hitSound, float soundVolume, double reach) {
        public Theme(String bossKey, float damage, ParticleOptions fx, SoundEvent hitSound, float soundVolume) {
            this(bossKey, damage, fx, hitSound, soundVolume, 3.5);
        }
    }

    private record Delayed(int at, Runnable run) {
    }

    private final Mob heart;
    private final Theme theme;
    private final AntiStrafe strafe = new AntiStrafe();
    private final Predicate<ServerPlayer> victims;
    private final BiConsumer<ServerPlayer, Float> striker;
    private final Supplier<Collection<ServerPlayer>> players;
    private final Supplier<Vec3> center;
    private final DoubleSupplier floorY;
    private final DoubleSupplier radius;
    private final List<Delayed> delayed = new ArrayList<>();
    private int clock;
    private int cooldown = 80;
    private int index;
    // 試験・開発用
    private boolean disabled;
    private int fired;

    public HeartPursuit(Mob heart, Theme theme, Predicate<ServerPlayer> victims, BiConsumer<ServerPlayer, Float> striker,
            Supplier<Collection<ServerPlayer>> players, Supplier<Vec3> center, DoubleSupplier floorY, DoubleSupplier radius) {
        this.heart = heart;
        this.theme = theme;
        this.victims = victims;
        this.striker = striker;
        this.players = players;
        this.center = center;
        this.floorY = floorY;
        this.radius = radius;
    }

    public AntiStrafe strafe() {
        return strafe;
    }

    public int fired() {
        return fired;
    }

    // 試験用: 追い技を止める (回り続けても出さない。心臓の技だけを見るとき)
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
        strafe.setEnabled(!disabled);
    }

    // 毎 tick。enabled が偽 (咆哮・登場・領域のあいだ) は、見張りだけ続けて技は出さない
    public void tick(ServerLevel level, boolean enabled) {
        clock++;
        Collection<ServerPlayer> all = players.get();
        Vec3 c = center.get();
        strafe.update(c.x, c.z, all);
        for (Iterator<Delayed> it = delayed.iterator(); it.hasNext();) {
            Delayed d = it.next();
            if (clock >= d.at()) {
                it.remove();
                if (enabled) {
                    d.run().run();
                }
            }
        }
        if (!enabled || disabled) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        List<ServerPlayer> strafers = new ArrayList<>();
        for (ServerPlayer p : all) {
            if (strafe.streak(p) >= PURSUE_TICKS) {
                strafers.add(p);
            }
        }
        if (strafers.isEmpty()) {
            return;
        }
        strafers.sort(Comparator.comparingInt((ServerPlayer p) -> strafe.streak(p)).reversed());
        int most = Math.max(1, (int) Math.ceil(all.size() / 3.0));
        for (int i = 0; i < Math.min(most, strafers.size()); i++) {
            launch(level, strafers.get(i), (index + i) % 3);
        }
        index++;
        cooldown = GAP_MIN + heart.getRandom().nextInt(GAP_SPAN);
    }

    // 試験用: 型 kind (0 先読み / 1 行く手 / 2 追い薙ぎ) を、いま回っている人 p へ出す
    public void launch(ServerLevel level, ServerPlayer p, int kind) {
        fired++;
        switch (kind) {
            case 0 -> lead(level, p);
            case 1 -> cutoff(level, p);
            default -> chase(level, p);
        }
    }

    private double roomRadius() {
        return Math.min(15.5, radius.getAsDouble() + 1);
    }

    private Telegraph.Builder tg(ServerLevel level, TelegraphShape shape, int warn) {
        return Telegraph.in(level, shape).floor(floorY.getAsDouble()).warn(warn).danger().owner(heart).victims(victims).reach(theme.reach());
    }

    private void cue(String key, int warn) {
        BossCue.send(heart, theme.bossKey(), key, warn);
    }

    private void burst(ServerLevel level, double x, double y, double z, int count) {
        level.sendParticles(theme.fx(), x, y + 0.6, z, count, 0.8, 0.5, 0.8, 0.08);
        level.playSound(null, x, y, z, theme.hitSound(), SoundSource.HOSTILE, theme.soundVolume(), 1f);
    }

    // 先読み: 満ちる頃の位置へ円。少し遅れて二つ目 (まだ回っているなら、その先)
    private void lead(ServerLevel level, ServerPlayer p) {
        cue("lead", LEAD_WARN);
        circle(level, strafe.predict(p, LEAD_WARN), LEAD_WARN, p);
        final ServerPlayer who = p;
        delayed.add(new Delayed(clock + LEAD_SECOND_DELAY, () -> {
            if (who.isAlive() && strafe.isStrafing(who)) {
                circle(level, strafe.predict(who, LEAD_SECOND_WARN), LEAD_SECOND_WARN, who);
            }
        }));
    }

    private void circle(ServerLevel level, Vec3 at, int warn, ServerPlayer target) {
        double y = floorY.getAsDouble();
        tg(level, TelegraphShape.circle(at.x, at.z, LEAD_RADIUS), warn)
                .target(target)
                .onImpact(s -> burst(s, at.x, y, at.z, 24))
                .onHit((s, victim) -> striker.accept(victim, theme.damage() * 0.85f))
                .start();
    }

    // 行く手: 回る向きの先の扇 (予告が満ちる頃の位置が真ん中より少し手前)
    private void cutoff(ServerLevel level, ServerPlayer p) {
        cue("cutoff", CUTOFF_WARN);
        Vec3 c = center.get();
        int dir = strafe.direction(p);
        double angle = strafe.angleAt(p, CUTOFF_WARN) + dir * 0.15;
        double y = floorY.getAsDouble();
        double range = roomRadius();
        tg(level, TelegraphShape.cone(c.x, c.z, angle, range, CUTOFF_HALF), CUTOFF_WARN)
                .onImpact(s -> {
                    for (double d = 3; d <= range; d += 3) {
                        for (int k = -1; k <= 1; k++) {
                            double a = angle + k * CUTOFF_HALF * 0.7;
                            burst(s, c.x + Math.cos(a) * d, y, c.z + Math.sin(a) * d, 4);
                        }
                    }
                })
                .onHit((s, victim) -> striker.accept(victim, theme.damage()))
                .start();
    }

    // 追い薙ぎ: 背後から、回る向きと同じ向きに人より速く回る帯 (跳べば越える)
    private void chase(ServerLevel level, ServerPlayer p) {
        cue("chase", CHASE_WARN);
        Vec3 c = center.get();
        int dir = strafe.direction(p);
        if (dir == 0) {
            dir = 1;
        }
        double start = strafe.angleAt(p, CHASE_WARN) - dir * 0.8;
        double span = dir * 2.0;
        double length = roomRadius();
        double y = floorY.getAsDouble();
        tg(level, TelegraphShape.sweep(c.x, c.z, start, span, length, CHASE_HALF_WIDTH), CHASE_WARN)
                .active(CHASE_ACTIVE).jumpClear(0.7)
                .onImpact(s -> burst(s, c.x + Math.cos(start) * 4, y, c.z + Math.sin(start) * 4, 10))
                .onHit((s, victim) -> striker.accept(victim, theme.damage() * 0.8f))
                .start();
    }
}
