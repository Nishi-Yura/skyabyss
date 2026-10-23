package jp.revati.skyabyss.rpg.client.skill.fx;

import static jp.revati.skyabyss.rpg.client.skill.SkillFx.burst;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.kick;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.rune;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.sound;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.spend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.joml.Vector3f;

import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import jp.revati.skyabyss.rpg.skill.WeaponType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 潮歌い・ 蒸気・ 火の子の一斉 (支え・崩し・面の技)
// 決まり: 技ごとに形を一つ (輪・扇・螺旋・柱・筋・鐘・五本の矢)。点は正確な曲線の上に等間隔に置き、散らさない。
// 動きで読ませる: 弧と螺旋は 3-4 tick に分けて掃き、輪は数 tick かけて広がり、当たりは動きの後。
// 色は技ごとに二つ (主と差し色)、粒の種類は三つまで。粒の大きさは 0.6-0.8。
// カメラから NEAR ブロック以内の点は置かない (目の前の大きな四角を作らない)。閃光は FLASH_CLEAR より遠くだけ
public final class FxSupport {
    private FxSupport() {
    }

    private static final double NEAR = 2.5;
    private static final double FLASH_CLEAR = 6.0;

    // ---------------------------------------------------------------- 色
    private static final DustParticleOptions FOAM = dust(0xF2FFFF, 0.8f);
    private static final DustParticleOptions SEA = dust(0x58D6E8, 0.7f);
    private static final DustParticleOptions GOLD_WHITE = dust(0xFFF0B8, 0.8f);
    private static final DustParticleOptions MANA = dust(0x6FF0D0, 0.7f);
    private static final DustParticleOptions BEACON_GOLD = dust(0xFFE070, 0.8f);
    private static final DustParticleOptions WHITE = dust(0xFFFFFF, 0.6f);
    private static final DustParticleOptions SAND = dust(0xD9C28A, 0.8f);
    private static final DustParticleOptions WIND = dust(0xE8F4FF, 0.6f);
    private static final DustParticleOptions EMBER = dust(0xFF8A2A, 0.7f);
    private static final DustParticleOptions BONE = dust(0xE8DCC0, 0.7f);
    private static final DustColorTransitionOptions STEAM = new DustColorTransitionOptions(rgb(0x9CEFFF), rgb(0xFFFFFF), 0.8f);

    public static void register(Map<Skill, Consumer<SkillFx.Cast>> plans) {
        plans.put(Skill.WARD_SONG, FxSupport::wardSong);
        plans.put(Skill.EBB_TIDE, FxSupport::ebbTide);
        plans.put(Skill.CYCLE_SONG, FxSupport::cycleSong);
        plans.put(Skill.SIGNAL, FxSupport::signal);
        plans.put(Skill.DUST_CHARGE, FxSupport::dustCharge);
        plans.put(Skill.STEAM_BELL, FxSupport::steamBell);
        plans.put(Skill.CINDER_VOLLEY, FxSupport::cinderVolley);
    }

    // ================================================================ 守りの唄
    // 形: 足もとの泡の輪が半径 1 → 8 (本当の半径) へ 8 tick で広がり、その後に海の青の螺旋 (2 巻き、高さ 2) が昇る
    // 粒: 輪 91 + 最後の輪 36 + 螺旋 44 = 約 171 (連鎖で逆位相の螺旋がもう一本 +44 → 約 215)
    private static void wardSong(SkillFx.Cast c) {
        double radius = SkillRules.WARD_SONG_RADIUS;
        c.at(1, x -> sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, x.feet(), 0.8f, 1.2f));
        for (int t = 1; t <= 8; t++) {
            final double r = 1.0 + (radius - 1.0) * (t - 1) / 7.0;
            final int n = t == 8 ? 36 : 5 + (int) Math.round(2 * r);
            final double phase = t * 0.21;
            c.at(t, x -> emit(x, FOAM, circle(x.feet(), r, n, 0.1, phase)));
        }
        c.at(8, x -> sound(x, SoundEvents.NOTE_BLOCK_CHIME.value(), x.feet(), 0.7f, 1.4f));
        // 螺旋: 4 tick で 2 巻き (1 tick で半周)、下から上へ
        double hr = firstPerson(c) ? 2.6 : 1.1;
        double a0 = yaw(c.fwd);
        for (int k = 0; k < 4; k++) {
            final int s = k;
            c.at(9 + k, x -> {
                Vec3 f = x.feet();
                double h0 = 2.0 * s / 4.0;
                double h1 = 2.0 * (s + 1) / 4.0;
                emit(x, SEA, helix(f, hr, hr, h0, h1, a0 + s * Math.PI, a0 + (s + 1) * Math.PI, 11), 0, 0.02, 0);
                if (x.chain) {
                    emit(x, SEA, helix(f, hr, hr, h0, h1, a0 + Math.PI + s * Math.PI, a0 + Math.PI + (s + 1) * Math.PI, 11), 0, 0.02, 0);
                }
            });
        }
        c.at(12, x -> sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, x.feet(), 0.6f, 1.8f));
        c.lasts(24);
    }

    // ================================================================ 引き潮
    // 形: 前の扇 (半角 EBB_ANGLE、半径 = 本当の届く距離) を、遠い縁から手前へ弧を 6 tick で寄せる。
    // 打った敵のまわりで締まる螺旋 (4 tick)、最後に足もと 1.3 先で泡の輪
    // 粒: 弧 約 87 + 螺旋 (3 体まで) 72 + 輪 24 + 閃光 1 = 約 184
    private static void ebbTide(SkillFx.Cast c) {
        double range = SkillRules.ebbRange(c.rank) + (WeaponType.has(c.caster, WeaponType.LONG) ? 1 : 0);
        double half = Math.toRadians(SkillRules.EBB_ANGLE);
        double yaw0 = yaw(c.fwd);
        Vec3 base = c.caster.position();
        List<LivingEntity> foes = coneFoes(c, base, range, SkillRules.EBB_ANGLE, 3);
        Vec3 land = new Vec3(base.x + c.fwd.x * 1.3, base.y + 0.05, base.z + c.fwd.z * 1.3);
        c.at(1, x -> sound(x, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, base, 0.7f, 0.8f));
        // 寄せる波: 遠くから手前へ
        for (int t = 1; t <= 6; t++) {
            final double d = range - (range - 1.5) * (t - 1) / 5.0;
            final int n = 4 + (int) Math.round(2 * d);
            c.at(t, x -> emit(x, SEA, arcPts(base, d, yaw0 - half, yaw0 + half, 0.15, n)));
        }
        // 敵のまわりで締まる螺旋 (上から下へ、外から内へ)
        for (int k = 0; k < 4; k++) {
            final int s = k;
            c.at(6 + s, x -> {
                for (LivingEntity e : foes) {
                    if (!e.isAlive()) {
                        continue;
                    }
                    double h = e.getBbHeight();
                    double r0 = 1.4 - 1.1 * s / 3.0;
                    double r1 = Math.max(0.2, r0 - 0.3);
                    double ph = (e.getId() % 16) * 0.4 + s * Math.PI * 0.8;
                    emit(x, FOAM, helix(e.position(), r0, r1, h * (0.9 - 0.2 * s), h * (0.75 - 0.2 * s), ph, ph + Math.PI * 0.8, 6));
                }
            });
        }
        // 当たり: 寄せた先の泡の輪
        c.at(10, x -> {
            emit(x, FOAM, circle(land, 1.3, 16, 0.0, 0.0));
            emit(x, SEA, circle(land, 0.7, 8, 0.05, 0.4));
            Vec3 at = land.add(0, 0.6, 0);
            for (LivingEntity e : foes) {
                if (e.isAlive()) {
                    at = e.position().add(0, e.getBbHeight() * 0.5, 0);
                    break;
                }
            }
            flashFar(x, at);
            sound(x, SoundEvents.PLAYER_SPLASH, land, 0.6f, 1.2f);
        });
        c.lasts(22);
    }

    // ================================================================ 巡りの唄
    // 形: 二つの光が体のまわりを逆向きに 2 周 (8 tick)、次に半径 8 の金白の輪が 6 tick で広がり、輪の中で碑文の火花が 20 tick 昇る
    // 粒: 光 48 + 輪 約 97 + 火花 40 = 約 185
    private static void cycleSong(SkillFx.Cast c) {
        double radius = SkillRules.CYCLE_SONG_RADIUS;
        double or = firstPerson(c) ? 2.6 : 1.0;
        double a0 = yaw(c.fwd);
        c.at(1, x -> sound(x, SoundEvents.BEACON_POWER_SELECT, x.feet(), 0.6f, 1.6f));
        for (int t = 1; t <= 8; t++) {
            final double a = t * Math.PI / 2;
            c.at(t, x -> {
                Vec3 f = x.feet();
                // 尾: 今の角度から 90 度ぶん後ろへ 3 点 (続けて見ると円になる)
                emit(x, MANA, arcPts(f, or, a0 + a - Math.PI / 2, a0 + a, 0.9, 3));
                emit(x, MANA, arcPts(f, or, a0 + Math.PI - a + Math.PI / 2, a0 + Math.PI - a, 1.4, 3));
            });
        }
        for (int k = 1; k <= 6; k++) {
            final double r = radius * k / 6.0;
            final int n = k == 6 ? 32 : 5 + (int) Math.round(2 * r);
            c.at(8 + k, x -> emit(x, GOLD_WHITE, circle(x.feet(), r, n, 0.1, 0.0)));
        }
        c.at(14, x -> {
            kick(x, 0.6f);
            sound(x, SoundEvents.NOTE_BLOCK_CHIME.value(), x.feet(), 0.8f, 0.9f);
        });
        // 輪の中で昇る火花 (黄金角で均等に散らす。毎 tick 2 つ)
        final int sparks = 40;
        for (int t = 0; t < 20; t++) {
            final int i0 = t * 2;
            c.at(15 + t, x -> {
                Vec3 f = x.feet();
                Pts p = new Pts();
                for (int i = i0; i < i0 + 2; i++) {
                    double rr = radius * Math.sqrt((i + 0.5) / sparks);
                    double aa = i * 2.39996;
                    p.add(f.x + Math.cos(aa) * rr, f.y + 0.3, f.z + Math.sin(aa) * rr);
                }
                emit(x, rune(), p, 0, 0.05, 0);
            });
        }
        c.lasts(38);
    }

    // ================================================================ 合図
    // 形: 指した点から真上 14 ブロックの金の柱 (白を半歩ずらして重ねる)。下から 2 tick で積み、足もとに金の輪、上で閃光
    // 粒: 輪 16 + 柱 28 + 閃光 1 + なぞり 84 + 輪の脈 24 = 約 153
    private static void signal(SkillFx.Cast c) {
        Vec3 at = signalPoint(c);
        double h = 14;
        int n = 14;
        c.at(1, x -> sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, x.hand(), 0.6f, 1.6f));
        c.at(2, x -> emit(x, BEACON_GOLD, circle(at, 1.2, 16, 0.05, 0.0)));
        c.at(3, x -> beam(x, at, h, n, 0, n / 2));
        c.at(4, x -> beam(x, at, h, n, n / 2, n));
        c.at(5, x -> {
            flashFar(x, at.add(0, h, 0));
            sound(x, SoundEvents.BEACON_ACTIVATE, at, 0.6f, 1.6f);
        });
        for (int t = 7; t <= 13; t += 3) {
            final boolean pulse = t != 10;
            c.at(t, x -> {
                beam(x, at, h, n, 0, n);
                if (pulse) {
                    emit(x, BEACON_GOLD, circle(at, 0.8, 12, 0.05, 0.26));
                }
            });
        }
        c.lasts(24);
    }

    // 柱の一部 (点 from から to まで)。金の点と、その半歩上の白の点
    private static void beam(SkillFx.Cast c, Vec3 at, double h, int n, int from, int to) {
        double step = h / (n - 1);
        Pts gold = new Pts();
        Pts white = new Pts();
        for (int i = from; i < to; i++) {
            gold.add(at.x, at.y + step * i, at.z);
            if (i < n - 1) {
                white.add(at.x, at.y + step * (i + 0.5), at.z);
            }
        }
        emit(c, BEACON_GOLD, gold);
        emit(c, WHITE, white);
    }

    // ================================================================ 砂塵の盾突き
    // 形: 通った道 (6 ブロック) に砂の筋 24 点、両脇 (当たりの縁 = DUST_WIDTH / 2 + 0.3) に細い風の線。3 tick で手前から先へ伸び、先で閃光と小さな砂の煙
    // 粒: 筋 24 + 風 24 + 閃光 1 + 煙 10 + 輪 12 + 降りる砂 24 = 約 95
    private static void dustCharge(SkillFx.Cast c) {
        Vec3 start = c.caster.position();
        Vec3[] path = new Vec3[] {start, start};
        int sandN = 24;
        int windN = 12;
        double side = SkillRules.DUST_WIDTH / 2 + 0.3;
        c.at(1, x -> sound(x, SoundEvents.SAND_BREAK, x.feet(), 0.7f, 0.8f));
        for (int k = 0; k < 3; k++) {
            final int s = k;
            c.at(2 + s, x -> {
                if (s == 0) {
                    // 通った道を決める (自分の画面ではもう着いているので、後ろへたどる)
                    Vec3 now = x.caster.position();
                    path[0] = now.distanceToSqr(start) > 1.0 ? start : backTrace(x, now, SkillRules.DUST_LENGTH);
                    path[1] = now;
                }
                Vec3 a = path[0].add(0, 0.2, 0);
                Vec3 b = path[1].add(0, 0.2, 0);
                emit(x, SAND, seg(a, b, sandN, s * 8, s * 8 + 8));
                Vec3 off = x.right.scale(side);
                Vec3 wa = path[0].add(0, 0.9, 0);
                Vec3 wb = path[1].add(0, 0.9, 0);
                emit(x, WIND, seg(wa.add(off), wb.add(off), windN, s * 4, s * 4 + 4));
                emit(x, WIND, seg(wa.subtract(off), wb.subtract(off), windN, s * 4, s * 4 + 4));
            });
        }
        c.at(5, x -> {
            Vec3 hit = path[1].add(x.fwd.scale(1.2));
            flashFar(x, hit.add(0, 1.0, 0));
            burst(x, SAND, hit.add(0, 0.5, 0), 10, 0.4, 0.03);
            emit(x, SAND, circle(hit, 1.0, 12, 0.05, 0.0));
            kick(x, 0.6f);
            sound(x, SoundEvents.BREEZE_JUMP, hit, 0.7f, 0.9f);
        });
        // 残り: 道に砂が降りる (等間隔に、低く)
        for (int t = 8; t <= 11; t += 3) {
            c.at(t, x -> emit(x, SAND, seg(path[0].add(0, 0.1, 0), path[1].add(0, 0.1, 0), 12, 0, 12), 0, -0.01, 0));
        }
        c.lasts(20);
    }

    // ================================================================ 蒸気の鐘
    // 形: 頭の上に蒸気の鐘 (下の縁が熾の輪、半径 1.2 → 0.6 へすぼむ 5 段) を 3 tick で下から積み、鳴ると足もとの輪が半径 STEAM_RADIUS へ広がる
    // 一人称の本人には頭の上が見えないので、鐘は目の前 3.8 ブロックに立てる
    // 粒: 鐘 約 74 + 輪 68 + 熾の縁 12 = 約 154
    private static void steamBell(SkillFx.Cast c) {
        double radius = SkillRules.STEAM_RADIUS;
        boolean fp = firstPerson(c);
        Vec3 front = new Vec3(c.origin.x + c.fwd.x * 3.8, c.origin.y - 0.6, c.origin.z + c.fwd.z * 3.8);
        c.at(1, x -> sound(x, SoundEvents.GENERIC_EXTINGUISH_FIRE, x.feet(), 0.5f, 0.7f));
        for (int k = 0; k < 3; k++) {
            final int s = k;
            c.at(1 + s, x -> {
                Vec3 top = fp ? front : x.caster.position().add(0, x.caster.getBbHeight(), 0);
                int lo = s * 2;
                int hi = Math.min(5, lo + 2);
                for (int i = lo; i < hi; i++) {
                    double y = 0.2 + 0.25 * i;
                    double r = 0.6 + 0.6 * Math.pow(1.0 - i / 4.0, 1.6);
                    int n = 4 + (int) Math.round(11 * r);
                    emit(x, i == 0 ? EMBER : STEAM, circle(top, r, n, y, i * 0.3));
                }
                if (s == 2) {
                    // 鐘の頭 (小さな輪)
                    emit(x, STEAM, circle(top, 0.25, 5, 1.4, 0.0));
                }
            });
        }
        // 鳴る: 足もとの輪が 4 tick で本当の半径へ
        for (int k = 1; k <= 4; k++) {
            final double r = radius * k / 4.0;
            final int n = k == 4 ? 26 : 6 + (int) Math.round(4 * r);
            final boolean last = k == 4;
            c.at(3 + k, x -> {
                emit(x, STEAM, circle(x.feet(), r, n, 0.1, 0.0));
                if (last) {
                    emit(x, EMBER, circle(x.feet(), r, 12, 0.15, Math.PI / 12));
                }
            });
        }
        c.at(4, x -> {
            Vec3 top = fp ? front : x.caster.position().add(0, x.caster.getBbHeight(), 0);
            flashFar(x, top.add(0, 0.8, 0));
            kick(x, 0.5f);
            sound(x, SoundEvents.AMETHYST_BLOCK_RESONATE, x.feet(), 0.8f, 0.5f);
        });
        c.lasts(16);
    }

    // ================================================================ 熾の矢雨
    // 形: 手から五本の矢 (-18, -9, 0, +9, +18 度、14 ブロック、壁で止まる)。左から一本ずつ 1 tick おきに放ち、
    // 一本は 2 tick で伸びる: 熾の尾 10 点 + 先の 4 割に骨の白い芯 6 点。止まった所に熾の小さな煙、真ん中の矢の先にだけ閃光
    // 粒: 矢 5 × (10 + 6 + 煙 5) + 閃光 1 = 約 106
    private static void cinderVolley(SkillFx.Cast c) {
        int bolts = SkillRules.VOLLEY_BOLTS;
        Vec3[] ends = new Vec3[bolts];
        for (int i = 0; i < bolts; i++) {
            ends[i] = boltEnd(c, i, bolts);
        }
        Vec3 from = c.hand();
        int tailN = 10;
        int coreN = 6;
        c.at(1, x -> sound(x, SoundEvents.GENERIC_EXTINGUISH_FIRE, from, 0.4f, 1.4f));
        c.at(2, x -> kick(x, 0.8f));
        for (int i = 0; i < bolts; i++) {
            final int b = i;
            int t0 = 2 + i;
            c.at(t0, x -> {
                emit(x, EMBER, seg(from, ends[b], tailN, 0, tailN / 2));
                sound(x, SoundEvents.ARROW_SHOOT, from, 0.4f, 0.8f + b * 0.05f);
            });
            c.at(t0 + 1, x -> {
                emit(x, EMBER, seg(from, ends[b], tailN, tailN / 2, tailN));
                emit(x, BONE, seg(from.lerp(ends[b], 0.6), ends[b], coreN, 0, coreN));
            });
            c.at(t0 + 2, x -> burst(x, EMBER, ends[b], 5, 0.2, 0.02));
        }
        int mid = (bolts - 1) / 2;
        c.at(2 + mid + 2, x -> flashFar(x, ends[mid]));
        c.lasts(16);
    }

    // ================================================================ 道具 (この file の中だけ)

    // 点の入れ物 (形を先に作ってから、カメラに近いものを除いて一度に置く)
    private static final class Pts {
        private double[] v = new double[96];
        private int n;

        Pts add(double x, double y, double z) {
            if (n * 3 + 3 > v.length) {
                v = Arrays.copyOf(v, v.length * 2);
            }
            v[n * 3] = x;
            v[n * 3 + 1] = y;
            v[n * 3 + 2] = z;
            n++;
            return this;
        }

        Pts add(Vec3 p) {
            return add(p.x, p.y, p.z);
        }
    }

    private static int emit(SkillFx.Cast c, ParticleOptions p, Pts pts) {
        return emit(c, p, pts, 0, 0, 0);
    }

    // カメラから NEAR 以内の点を除き、残りの数だけ予算を取ってから置く
    private static int emit(SkillFx.Cast c, ParticleOptions p, Pts pts, double vx, double vy, double vz) {
        Vec3 cam = cam();
        int keep = 0;
        for (int i = 0; i < pts.n; i++) {
            double x = pts.v[i * 3];
            double y = pts.v[i * 3 + 1];
            double z = pts.v[i * 3 + 2];
            double dx = x - cam.x;
            double dy = y - cam.y;
            double dz = z - cam.z;
            if (dx * dx + dy * dy + dz * dz >= NEAR * NEAR) {
                pts.v[keep * 3] = x;
                pts.v[keep * 3 + 1] = y;
                pts.v[keep * 3 + 2] = z;
                keep++;
            }
        }
        int k = spend(c, keep);
        for (int i = 0; i < k; i++) {
            SkillFx.put(c, p, pts.v[i * 3], pts.v[i * 3 + 1], pts.v[i * 3 + 2], vx, vy, vz);
        }
        return k;
    }

    // 閃光はカメラから十分遠いときだけ (近いと画面いっぱいの白い四角になる)
    private static void flashFar(SkillFx.Cast c, Vec3 at) {
        if (at.distanceToSqr(cam()) >= FLASH_CLEAR * FLASH_CLEAR) {
            SkillFx.flash(c, at);
        }
    }

    private static Vec3 cam() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    // 本人が一人称で見ている (体のまわりの形はカメラに近すぎるので、外へ広げる)
    private static boolean firstPerson(SkillFx.Cast c) {
        return c.self && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private static double yaw(Vec3 dir) {
        return Math.atan2(dir.z, dir.x);
    }

    // 水平な円 (n 点、等間隔)
    private static Pts circle(Vec3 center, double r, int n, double y, double phase) {
        Pts p = new Pts();
        for (int i = 0; i < n; i++) {
            double a = phase + Math.PI * 2 * i / n;
            p.add(center.x + Math.cos(a) * r, center.y + y, center.z + Math.sin(a) * r);
        }
        return p;
    }

    // 水平な弧 (from から to まで、両端を含めて n 点)
    private static Pts arcPts(Vec3 center, double r, double from, double to, double y, int n) {
        Pts p = new Pts();
        for (int i = 0; i < n; i++) {
            double t = n <= 1 ? 0 : (double) i / (n - 1);
            double a = from + (to - from) * t;
            p.add(center.x + Math.cos(a) * r, center.y + y, center.z + Math.sin(a) * r);
        }
        return p;
    }

    // 螺旋の一区間 (角度 a0 → a1、半径 r0 → r1、高さ h0 → h1。終わりの点は含めない: 次の区間とつなぐ)
    private static Pts helix(Vec3 center, double r0, double r1, double h0, double h1, double a0, double a1, int n) {
        Pts p = new Pts();
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double a = a0 + (a1 - a0) * t;
            double r = r0 + (r1 - r0) * t;
            p.add(center.x + Math.cos(a) * r, center.y + h0 + (h1 - h0) * t, center.z + Math.sin(a) * r);
        }
        return p;
    }

    // 線 a → b を total 点で等分したうちの、from から to の手前までの点
    private static Pts seg(Vec3 a, Vec3 b, int total, int from, int to) {
        Pts p = new Pts();
        for (int i = from; i < Math.min(to, total); i++) {
            double t = total <= 1 ? 0 : (double) i / (total - 1);
            p.add(a.lerp(b, t));
        }
        return p;
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(rgb(rgb), size);
    }

    private static Vector3f rgb(int rgb) {
        return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    // 引き潮の扇の中の生き物 (サーバーと同じ角度と距離。プレイヤーは除く)
    private static List<LivingEntity> coneFoes(SkillFx.Cast c, Vec3 base, double range, double deg, int max) {
        double cos = Math.cos(Math.toRadians(deg));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, c.caster.getBoundingBox().inflate(range + 2, 2, range + 2),
                f -> f != c.caster && f.isAlive() && !(f instanceof Player) && !(f instanceof ArmorStand))) {
            Vec3 to = new Vec3(e.getX() - base.x, 0, e.getZ() - base.z);
            double dist = to.length() - e.getBbWidth() / 2;
            if (dist > range) {
                continue;
            }
            if (to.length() < 1.0 || to.normalize().dot(c.fwd) >= cos) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c.caster)));
        return out.size() > max ? out.subList(0, max) : out;
    }

    // 合図の着く点 (サーバーの signal と同じ: 視線の先 32、生き物に当たればその足もと、宙なら真下 16 まで下ろす)
    private static Vec3 signalPoint(SkillFx.Cast c) {
        Vec3 eye = c.origin;
        Vec3 end = eye.add(c.look.scale(SkillRules.SIGNAL_RANGE));
        BlockHitResult block = c.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.caster));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        Player caster = c.caster;
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster, eye, stop, new AABB(eye, stop).inflate(1.0),
                e -> e instanceof LivingEntity le && le.isAlive() && e != caster && !(e instanceof ArmorStand), eye.distanceToSqr(stop));
        if (hit != null) {
            return hit.getEntity().position();
        }
        if (block.getType() == HitResult.Type.MISS) {
            BlockHitResult ground = c.level.clip(new ClipContext(stop, stop.add(0, -16, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (ground.getType() != HitResult.Type.MISS) {
                return ground.getLocation();
            }
        }
        return stop;
    }

    // 矢の先 (サーバーの cinderVolley と同じ: 9 度ずつの扇、14 ブロック、壁で止まる)
    private static Vec3 boltEnd(SkillFx.Cast c, int i, int bolts) {
        double yaw = Math.toRadians((i - (bolts - 1) / 2.0) * 9.0);
        Vec3 look = c.look;
        Vec3 dir = new Vec3(look.x * Math.cos(yaw) - look.z * Math.sin(yaw), look.y, look.x * Math.sin(yaw) + look.z * Math.cos(yaw)).normalize();
        Vec3 end = c.origin.add(dir.scale(SkillRules.VOLLEY_RANGE));
        BlockHitResult block = c.level.clip(new ClipContext(c.origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.caster));
        return block.getType() == HitResult.Type.MISS ? end : block.getLocation();
    }

    // 盾突きの通り道を後ろへたどる (壁に当たるところまで、0.5 ずつ)
    private static Vec3 backTrace(SkillFx.Cast c, Vec3 to, double len) {
        Vec3 reached = to;
        for (double t = 0.5; t <= len + 1e-6; t += 0.5) {
            Vec3 p = to.subtract(c.fwd.scale(t));
            BlockPos bp = BlockPos.containing(p.x, p.y + 0.5, p.z);
            if (!c.level.getBlockState(bp).getCollisionShape(c.level, bp).isEmpty()) {
                break;
            }
            reached = p;
        }
        return reached;
    }
}
