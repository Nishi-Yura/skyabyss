package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormExtra.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.StormSetup;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

// 任意の翼 (StormExtra) の、拍で動く決まり。どれもその場所にいる人にだけ働き、試練の進みには関わらない (閉じる戸はない)。
//  - 風の外廊 (西): 嵐の横風 (StormWest の拍と同じ 6 秒の周期)。風上 (西) 3 ブロック以内に壁や柱があれば陰。肩の側 (東) へ押し戻す
//  - 避雷の塔 (東): 嵐のあいだ、100 tick ごとに人の「進む先」へ招雷の輪。立ち止まるか走っていると当たる。柱 (3 ブロック以内) があれば柱へ落ちる。凪は柱が眠る (輪は出ない)
//  - 雷の外廊 (東): 嵐のあいだ、70 tick ごとに人の前へ放電の線。低い線は跳び越し、高い線はしゃがんで潜る。凪は線がない
//  - 尾羽の庭 (東): 雷の見本。足もとへ見本の輪 (傷なし)。柱の 3 ブロック以内なら柱へ落ちる。凪は遅く、嵐は速い
final class StormExtraRules {
    static final int TOWER_EVERY = 100;
    static final int TOWER_WARN = 24;
    static final float TOWER_DAMAGE = 8f;
    static final int LINE_EVERY = 70;
    static final int LINE_WARN = 24;
    static final float LINE_DAMAGE = 12f;
    static final int GARDEN_EVERY_STORM = 100;
    static final int GARDEN_EVERY_CALM = 200;
    private static final DustParticleOptions LOW = new DustParticleOptions(new Vector3f(0.75f, 0.9f, 1f), 1.0f);
    private static final DustParticleOptions HIGH = new DustParticleOptions(new Vector3f(1f, 0.95f, 0.6f), 1.0f);

    // 人ごとの次の招雷の時刻・直前の位置
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    private static final List<Line> LINES = new ArrayList<>();
    private static int lineCount;

    record Line(double x, boolean high, UUID group, int[] left) {
    }

    private StormExtraRules() {
    }

    // 柱を登録する (起動と置き直し。メモリだけ)
    static void registerRods(ServerLevel level) {
        for (BlockPos b : allRods()) {
            StormRods.register(level, b);
        }
    }

    static void clear() {
        NEXT.clear();
        LAST.clear();
        LINES.clear();
    }

    static void tick(ServerLevel level, boolean peaceful) {
        long gt = level.getGameTime();
        boolean storm = StormBackClock.phase(level) == StormBackClock.Phase.STORM;
        // 凪では柱が眠る (満ちた段を空に戻す。電球が消える)
        if (!storm && gt % 40 == 0) {
            for (BlockPos b : towerRods()) {
                StormRods.empty(level, b);
            }
            for (BlockPos b : gardenRods()) {
                StormRods.empty(level, b);
            }
        }
        List<ServerPlayer> players = Players.snapshot(level.players());
        for (ServerPlayer p : players) {
            Vec3 pos = p.position();
            Vec3 last = LAST.put(p.getUUID(), pos);
            Vec3 vel = last == null || pos.distanceToSqr(last) > 4 ? Vec3.ZERO : pos.subtract(last);
            if (p.isSpectator() || !p.isAlive()) {
                continue;
            }
            if (inOuter(pos, -1)) {
                crosswind(level, p, gt);
            } else if (inOuter(pos, 1)) {
                if (storm && !peaceful) {
                    maybeLine(level, p, gt);
                }
            } else if (inRodTower(pos)) {
                if (storm && !peaceful && gt >= NEXT.getOrDefault(p.getUUID(), 0L)) {
                    NEXT.put(p.getUUID(), gt + TOWER_EVERY);
                    // 進む先 (1 tick の動き × 輪の予告の長さ) へ。足もとの高さ
                    Vec3 at = new Vec3(pos.x + vel.x * TOWER_WARN, pos.y, pos.z + vel.z * TOWER_WARN);
                    StormStrike.call(level, at, TOWER_WARN, TOWER_DAMAGE, null, Groups.keyOf(p));
                }
            } else if (inGardenEast(pos)) {
                long every = storm ? GARDEN_EVERY_STORM : GARDEN_EVERY_CALM;
                if (gt >= NEXT.getOrDefault(p.getUUID(), 0L)) {
                    NEXT.put(p.getUUID(), gt + every);
                    StormStrike.call(level, pos, storm ? TOWER_WARN : TOWER_WARN + 16, 0f, null, Groups.keyOf(p)).noCharge();
                }
            }
        }
        tickLines(level, gt);
        LAST.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
        NEXT.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }

    // ---------------------------------------------------------------- 風の外廊の横風
    private static void crosswind(ServerLevel level, ServerPlayer p, long gt) {
        int gust = StormWest.gust(level);
        int t = StormWest.gustT(level);
        if (gust == 1 && gt % 6 == 0) {
            for (int i = 0; i < 6; i++) {
                level.sendParticles(p, ParticleTypes.CLOUD, true, p.getX() - 8, p.getY() + 1 + level.random.nextDouble() * 3, p.getZ() - 5 + level.random.nextDouble() * 10, 0, 1, 0, 0, 0.5);
            }
        }
        if (gust == 1 && t == 0) {
            level.playSound(null, p.getX() - 6, p.getY() + 2, p.getZ(), StormSetup.CROSSWIND.get(), SoundSource.AMBIENT, 1.6f, 1f);
        }
        if (gust == 2 && !p.isCreative() && !StormWest.sheltered(level, p)) {
            Vec3 d = Updraft.actualMotion(p);
            p.setDeltaMovement(Math.max(d.x, StormWest.GUST_PUSH), d.y, d.z * 0.9);
            p.hurtMarked = true;
            if (gt % 4 == 0) {
                StormFx.forced(level, ParticleTypes.CLOUD, p.getX() - 1, p.getY() + 1, p.getZ(), 3, 0.3, 0.6, 0.3, 0.1);
            }
        }
    }

    // ---------------------------------------------------------------- 雷の外廊の放電の線
    private static void maybeLine(ServerLevel level, ServerPlayer p, long gt) {
        if (gt % LINE_EVERY != 30 + Math.floorMod(p.getUUID().hashCode(), 7)) {
            return;
        }
        double x = Math.floor(p.getX() + (level.random.nextInt(5) - 2)) + 0.5;
        LINES.add(new Line(x, (lineCount++ & 1) == 1, Groups.keyOf(p), new int[] {LINE_WARN}));
        level.playSound(null, x, DECK_Y + 2, (OUT_Z1 + OUT_Z2) / 2.0, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.7f, 1.8f);
    }

    private static void tickLines(ServerLevel level, long gt) {
        for (Iterator<Line> it = LINES.iterator(); it.hasNext();) {
            Line l = it.next();
            int left = --l.left()[0];
            double y = DECK_Y + 1 + (l.high() ? 1.75 : 0.3);
            Vec3 a = new Vec3(l.x(), y, OUT_Z1);
            Vec3 b = new Vec3(l.x(), y, OUT_Z2 + 1);
            if (left > 0) {
                if (left % 3 == 0) {
                    StormFx.to(l.group(), () -> {
                        StormFx.line(level, l.high() ? HIGH : LOW, a, b, 0.6);
                        StormFx.forced(level, ParticleTypes.ELECTRIC_SPARK, l.x(), y, OUT_Z1 + 0.2, 2, 0.05, 0.1, 0.05, 0.02);
                        StormFx.forced(level, ParticleTypes.ELECTRIC_SPARK, l.x(), y, OUT_Z2 + 0.8, 2, 0.05, 0.1, 0.05, 0.02);
                    });
                }
                continue;
            }
            it.remove();
            StormFx.to(l.group(), () -> {
                StormFx.line(level, ParticleTypes.ELECTRIC_SPARK, a, b, 0.3);
                StormFx.line(level, ParticleTypes.END_ROD, a, b, 1.0);
            });
            level.playSound(null, l.x(), y, (OUT_Z1 + OUT_Z2) / 2.0, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1f, 1.6f);
            for (ServerPlayer p : Groups.alive(level, l.group())) {
                if (p.isCreative() || Math.abs(p.getX() - l.x()) > 0.8 || !inOuter(p.position(), 1)) {
                    continue;
                }
                if (hits(p.getY(), p.getBbHeight(), l.high())) {
                    p.hurt(level.damageSources().lightningBolt(), LINE_DAMAGE);
                }
            }
        }
    }

    // 線に当たるか: 低い線は足が床から 0.6 より上 (跳んでいる) なら当たらない。高い線は背が 1.5 以下 (しゃがむ) なら当たらない
    static boolean hits(double feetY, double height, boolean high) {
        double floor = DECK_Y + 1;
        if (high) {
            return feetY + height > floor + 1.6 && feetY < floor + 2.4;
        }
        return feetY < floor + 0.6;
    }
}
