package jp.revati.skyabyss.rpg.client.skill.fx;

import static jp.revati.skyabyss.rpg.client.skill.SkillFx.burst;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.flash;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.kick;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.line;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.ring;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.rune;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.sky;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.sound;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.spend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.joml.Vector3f;

import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 潮歌い・ 蒸気・ 火の子の一斉 (支え・崩し・面の技)
// 色の決まり: 潮 = 泡の白と海の青 / 旅人 = 金と白 / 橋 = 二つの木の色を混ぜる (岩+風 = 砂と淡い風、潮+灰 = 蒸気と熾、灰+骨 = 熾と骨の白)
public final class FxSupport {
    private FxSupport() {
    }

    // ---------------------------------------------------------------- 色 (AttackFx の値に合わせる)
    private static final DustParticleOptions FOAM = dust(0xF2FFFF, 2.0f);
    private static final DustParticleOptions SEA = dust(0x58D6E8, 1.7f);
    private static final DustParticleOptions CORE_CYAN = dust(0x8CF6FF, 1.7f);
    private static final DustParticleOptions WARM = dust(0xFFE2A0, 1.4f);
    private static final DustParticleOptions MANA = dust(0x6FF0D0, 1.5f);
    private static final DustParticleOptions BEACON_GOLD = dust(0xFFE070, 1.6f);
    private static final DustParticleOptions SAND = dust(0xD9C28A, 1.6f);
    private static final DustParticleOptions WIND = dust(0xE8F4FF, 1.2f);
    private static final DustParticleOptions HEAT = dust(0xFF8A2A, 1.8f);
    private static final DustParticleOptions CINDER = dust(0xFFC860, 1.3f);
    private static final DustParticleOptions BONE = dust(0xE8DCC0, 1.6f);
    private static final DustColorTransitionOptions STEAM = new DustColorTransitionOptions(rgb(0x9CEFFF), rgb(0xFFFFFF), 1.8f);
    private static final DustColorTransitionOptions EMBER_FADE = new DustColorTransitionOptions(rgb(0xFFC860), rgb(0x9CEFFF), 1.4f);
    private static final DustColorTransitionOptions BOLT_FADE = new DustColorTransitionOptions(rgb(0xE8DCC0), rgb(0xFF8A2A), 1.5f);
    private static final BlockParticleOption SAND_BITS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

    public static void register(Map<Skill, Consumer<SkillFx.Cast>> plans) {
        plans.put(Skill.WARD_SONG, FxSupport::wardSong);
        plans.put(Skill.EBB_TIDE, FxSupport::ebbTide);
        plans.put(Skill.CYCLE_SONG, FxSupport::cycleSong);
        plans.put(Skill.SIGNAL, FxSupport::signal);
        plans.put(Skill.DUST_CHARGE, FxSupport::dustCharge);
        plans.put(Skill.STEAM_BELL, FxSupport::steamBell);
        plans.put(Skill.CINDER_VOLLEY, FxSupport::cinderVolley);
    }

    // ================================================================ 守りの唄 (半径 8 の泡の輪が三段で広がり、音の筋が仲間へ渡る)
    private static void wardSong(SkillFx.Cast c) {
        double radius = SkillRules.WARD_SONG_RADIUS;
        // 溜め: 手に音と粒
        c.at(1, x -> {
            burst(x, ParticleTypes.NOTE, x.hand(), 3, 0.15, 0.6);
            burst(x, sky(), x.hand(), sc(x, 6), 0.15, 0.02);
            sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, x.feet(), 0.8f, 1.2f);
        });
        // 体のまわりを昇る泡の螺旋
        for (int t = 2; t <= 6; t++) {
            final int k = t;
            c.at(t, x -> spiral(x, FOAM, x.feet(), 0.95, 0.8, (k - 2) * 0.45, (k - 1) * 0.45, 0.45, 6, k * 1.4, 0.0, 0.015));
        }
        // 放ち: 足もとから三段の輪 (最後が本当の半径)
        c.at(3, x -> ring(x, FOAM, x.feet(), 2.0, 14, 0.1, 0.02));
        c.at(5, x -> ring(x, SEA, x.feet(), 4.5, 20, 0.1, 0.02));
        c.at(7, x -> {
            ring(x, FOAM, x.feet(), radius, sc(x, 30), 0.12, 0.01);
            flash(x, x.feet().add(0, 1.2, 0));
            sound(x, SoundEvents.NOTE_BLOCK_CHIME.value(), x.feet(), 0.7f, 1.4f);
            if (x.chain) {
                burst(x, ParticleTypes.END_ROD, x.feet().add(0, 1.0, 0), 10, 0.4, 0.08);
            }
        });
        // 仲間へ渡る音の筋 (弧を描いて胸へ) と、仲間の足もとの小さな輪
        c.at(8, x -> {
            Vec3 from = x.caster.position().add(0, 1.5, 0);
            for (Player ally : nearbyPlayers(x, radius, 4)) {
                Vec3 to = ally.position().add(0, 1.1, 0);
                arc(x, sky(), from, to, 8, 1.2);
                ring(x, WARM, ally.position().add(0, 0.05, 0), 0.7, 8, 0.05, 0.0);
            }
        });
        // 残り: 足もとの温かい光
        for (int t = 10; t <= 30; t += 5) {
            c.at(t, x -> {
                ring(x, WARM, x.feet(), 1.2, 8, 0.05, 0.0);
                burst(x, sky(), x.feet().add(0, 0.3, 0), 2, 0.6, 0.01);
            });
        }
        c.lasts(40);
    }

    // ================================================================ 引き潮 (前の扇へ寄せ波、敵のまわりで渦、足もとで水しぶき)
    private static void ebbTide(SkillFx.Cast c) {
        double range = SkillRules.ebbRange(c.rank);
        Vec3 base = c.caster.position();
        List<LivingEntity> foes = coneFoes(c, base, range, SkillRules.EBB_ANGLE, 4);
        Vec3 land = new Vec3(base.x + c.fwd.x * 1.3, base.y + 0.05, base.z + c.fwd.z * 1.3);
        c.at(1, x -> {
            burst(x, SEA, x.hand(), sc(x, 6), 0.12, 0.01);
            sound(x, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, x.hand(), 0.7f, 0.8f);
        });
        // 引く波の前線: 遠くから手前へ 6 tick で寄る (速さも手前向き)
        for (int t = 1; t <= 6; t++) {
            final double d = Math.max(1.2, range * (1.0 - (t - 1) / 6.0));
            c.at(t, x -> {
                fan(x, (d > range * 0.5) ? SEA : FOAM, base, x.fwd, d, SkillRules.EBB_ANGLE, sc(x, 10), 0.3, 0.18);
                fan(x, ParticleTypes.SPLASH, base, x.fwd, d, SkillRules.EBB_ANGLE, 2, 0.2, 0.0);
            });
        }
        // 敵のまわりで吸い込む渦 (二段)
        c.at(2, x -> {
            for (LivingEntity e : foes) {
                spiral(x, SEA, e.position(), 1.3, 0.6, 0.2, e.getBbHeight(), 1.2, 12, x.random().nextDouble() * 6.3, 0.12, 0.0);
            }
        });
        c.at(5, x -> {
            for (LivingEntity e : foes) {
                spiral(x, FOAM, e.position(), 0.8, 0.2, e.getBbHeight() * 0.9, 0.2, 1.0, 8, x.random().nextDouble() * 6.3, 0.08, 0.0);
            }
        });
        // 当たり: 寄せた先で水しぶき
        c.at(8, x -> {
            flash(x, land.add(0, 0.6, 0));
            burst(x, ParticleTypes.SPLASH, land.add(0, 0.3, 0), 14, 0.5, 0.1);
            ring(x, FOAM, land, 1.4, 16, 0.1, 0.06);
            for (LivingEntity e : foes) {
                burst(x, ParticleTypes.SPLASH, e.position().add(0, 0.4, 0), 6, 0.35, 0.1);
            }
            sound(x, SoundEvents.PLAYER_SPLASH, land, 0.6f, 1.2f);
            if (x.chain) {
                burst(x, ParticleTypes.END_ROD, land.add(0, 0.5, 0), 8, 0.5, 0.06);
            }
        });
        // 残り: 足もとの潮だまり
        for (int t = 11; t <= 26; t += 5) {
            c.at(t, x -> {
                ring(x, FOAM, land, 0.9, 6, 0.05, 0.0);
                burst(x, ParticleTypes.SPLASH, land.add(0, 0.2, 0), 2, 0.5, 0.02);
            });
        }
        c.lasts(32);
    }

    // ================================================================ 巡りの唄 (体のまわりを二つの光が巡り、半径 8 の輪が三段、マナの粒が仲間へ)
    private static void cycleSong(SkillFx.Cast c) {
        double radius = SkillRules.CYCLE_SONG_RADIUS;
        c.at(1, x -> {
            burst(x, ParticleTypes.NOTE, x.hand(), 2, 0.15, 0.6);
            burst(x, rune(), x.hand(), sc(x, 4), 0.12, 0.02);
            sound(x, SoundEvents.BEACON_POWER_SELECT, x.feet(), 0.6f, 1.6f);
        });
        // 溜め: 二つの光が逆向きに巡る (腰と胸の高さ)
        for (int t = 1; t <= 8; t++) {
            final int k = t;
            c.at(t, x -> {
                Vec3 f = x.caster.position();
                double a = k * 0.8;
                orbitDot(x, MANA, f, 1.2, 1.0, a, 3);
                orbitDot(x, rune(), f, 1.0, 1.6, -a + Math.PI, 3);
            });
        }
        // 放ち: 三段の輪
        c.at(6, x -> ring(x, CORE_CYAN, x.feet(), 3.0, 14, 0.1, 0.02));
        c.at(8, x -> ring(x, MANA, x.feet(), 5.5, 20, 0.1, 0.02));
        c.at(10, x -> {
            ring(x, FOAM, x.feet(), radius, sc(x, 32), 0.12, 0.01);
            flash(x, x.feet().add(0, 1.2, 0));
            kick(x, 0.6f);
            sound(x, SoundEvents.NOTE_BLOCK_CHIME.value(), x.feet(), 0.8f, 0.9f);
            if (x.chain) {
                ring(x, ParticleTypes.END_ROD, x.feet(), 1.5, 12, 1.0, 0.06);
            }
        });
        // 仲間へ渡るマナの筋
        c.at(11, x -> {
            Vec3 from = x.caster.position().add(0, 1.4, 0);
            for (Player ally : nearbyPlayers(x, radius, 4)) {
                arc(x, rune(), from, ally.position().add(0, 1.1, 0), 8, 1.0);
                ring(x, MANA, ally.position().add(0, 0.05, 0), 0.7, 8, 0.05, 0.0);
            }
        });
        // 残り: 足もとの温かい輪と、昇るマナ
        for (int t = 14; t <= 34; t += 5) {
            c.at(t, x -> {
                ring(x, WARM, x.feet(), 1.1, 8, 0.05, 0.0);
                burst(x, rune(), x.feet().add(0, 0.4, 0), 3, 0.5, 0.01);
            });
        }
        c.lasts(42);
    }

    // ================================================================ 合図 (指した先へ光の筋、着いたところで閃き、空へ昇る光の柱)
    private static void signal(SkillFx.Cast c) {
        Vec3 at = signalPoint(c);
        c.at(1, x -> {
            burst(x, ParticleTypes.END_ROD, x.hand(), 6, 0.1, 0.03);
            sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, x.hand(), 0.6f, 1.6f);
        });
        // 放ち: 手から先へ、二 tick で飛ぶ筋
        c.at(2, x -> line(x, BEACON_GOLD, x.hand(), x.hand().lerp(at, 0.5), 12, 0.03, 0.0));
        c.at(3, x -> line(x, BEACON_GOLD, x.hand().lerp(at, 0.5), at, 12, 0.03, 0.0));
        // 当たり: 閃き + 地面の金の輪 + 柱
        c.at(4, x -> {
            flash(x, at.add(0, 0.5, 0));
            ring(x, BEACON_GOLD, at.add(0, 0.05, 0), 1.2, 16, 0.05, 0.08);
            burst(x, ParticleTypes.FIREWORK, at.add(0, 0.5, 0), 10, 0.2, 0.12);
            line(x, ParticleTypes.END_ROD, at, at.add(0, 14, 0), sc(x, 24), 0.05, 0.0);
            line(x, BEACON_GOLD, at, at.add(0, 10, 0), 12, 0.08, 0.0);
            sound(x, SoundEvents.BEACON_ACTIVATE, at, 0.6f, 1.6f);
            if (x.chain) {
                burst(x, ParticleTypes.END_ROD, at.add(0, 0.8, 0), 10, 0.3, 0.1);
            }
        });
        // 柱をもう二度なぞり、上で花火のように開く (遠くからも見える)
        c.at(8, x -> {
            line(x, ParticleTypes.END_ROD, at.add(0, 2, 0), at.add(0, 14, 0), 16, 0.05, 0.0);
            burst(x, ParticleTypes.FIREWORK, at.add(0, 14, 0), 12, 0.3, 0.15);
        });
        c.at(12, x -> line(x, ParticleTypes.END_ROD, at.add(0, 4, 0), at.add(0, 14, 0), 12, 0.05, 0.0));
        // 残り: 足もとの金の輪がゆっくり脈打つ
        for (int t = 10; t <= 28; t += 6) {
            c.at(t, x -> ring(x, BEACON_GOLD, at.add(0, 0.05, 0), 0.8, 8, 0.05, 0.0));
        }
        c.lasts(36);
    }

    // ================================================================ 砂塵の盾突き (岩+風: 砂の帯と淡い風の筋、先で砂が弾ける)
    private static void dustCharge(SkillFx.Cast c) {
        Vec3 start = c.caster.position();
        Vec3[] path = new Vec3[] {start, start};
        c.at(1, x -> {
            burst(x, SAND_BITS, x.hand(), sc(x, 6), 0.15, 0.1);
            burst(x, sky(), x.hand(), 4, 0.15, 0.03);
            sound(x, SoundEvents.SAND_BREAK, x.feet(), 0.7f, 0.8f);
        });
        // 通った道を決める (自分の画面ではもう着いているので、後ろへたどる)
        c.at(2, x -> {
            Vec3 now = x.caster.position();
            if (now.distanceToSqr(start) > 1.0) {
                path[0] = start;
                path[1] = now;
            } else {
                path[0] = backTrace(x, now, SkillRules.DUST_LENGTH);
                path[1] = now;
            }
            line(x, SAND, path[0].add(0, 0.2, 0), path[1].add(0, 0.2, 0), sc(x, 16), 0.3, 0.02);
            streak(x, ParticleTypes.CLOUD, path[0].add(0, 1.0, 0), path[1].add(0, 1.0, 0), 10, 0.25, x.fwd.scale(0.2));
        });
        // 帯の両脇に風の筋 (幅は当たりの幅)
        c.at(3, x -> {
            Vec3 side = x.right.scale(SkillRules.DUST_WIDTH / 2);
            line(x, WIND, path[0].add(side).add(0, 0.6, 0), path[1].add(side).add(0, 0.6, 0), 10, 0.1, 0.0);
            line(x, WIND, path[0].subtract(side).add(0, 0.6, 0), path[1].subtract(side).add(0, 0.6, 0), 10, 0.1, 0.0);
        });
        // 当たり: 先で砂が弾ける
        c.at(4, x -> {
            Vec3 hit = path[1].add(x.fwd.scale(1.2));
            flash(x, hit.add(0, 1.0, 0));
            burst(x, SAND_BITS, hit.add(0, 0.6, 0), sc(x, 16), 0.6, 0.15);
            ring(x, ParticleTypes.DUST_PLUME, hit.add(0, 0.05, 0), 1.0, 12, 0.1, 0.12);
            burst(x, ParticleTypes.GUST_EMITTER_SMALL, hit.add(0, 0.8, 0), 1, 0.0, 0.0);
            sound(x, SoundEvents.BREEZE_JUMP, hit, 0.7f, 0.9f);
            if (x.chain) {
                burst(x, ParticleTypes.CRIT, hit.add(0, 0.8, 0), 10, 0.5, 0.2);
            }
        });
        // 残り: 道に砂が降りる
        for (int t = 6; t <= 21; t += 5) {
            c.at(t, x -> {
                int k = spend(x, 6);
                RandomSource r = x.random();
                for (int i = 0; i < k; i++) {
                    Vec3 p = path[0].lerp(path[1], r.nextDouble());
                    x.level.addParticle(i % 2 == 0 ? SAND : WIND, p.x + sym(r) * 0.6, p.y + 0.3 + r.nextDouble() * 0.8, p.z + sym(r) * 0.6, 0, -0.01, 0);
                }
            });
        }
        c.lasts(28);
    }

    // ================================================================ 蒸気の鐘 (潮+灰: 蒸気の鐘の形が立ち、鳴ると半径 4 へ蒸気と熾の輪)
    private static void steamBell(SkillFx.Cast c) {
        double radius = SkillRules.STEAM_RADIUS;
        c.at(1, x -> {
            burst(x, STEAM, x.hand(), sc(x, 6), 0.12, 0.02);
            burst(x, CINDER, x.hand(), 3, 0.1, 0.02);
            sound(x, SoundEvents.GENERIC_EXTINGUISH_FIRE, x.feet(), 0.5f, 0.7f);
        });
        // 溜め: 体のまわりに蒸気の鐘 (下が広い)
        c.at(2, x -> bell(x, x.feet(), 0.0, 1.2, 14));
        c.at(3, x -> bell(x, x.feet(), 1.2, 2.4, 14));
        // 当たり: 鳴る。三重の輪 (外が本当の半径、熾の縁)
        c.at(4, x -> {
            flash(x, x.feet().add(0, 1.0, 0));
            ring(x, ParticleTypes.CLOUD, x.feet(), 1.5, 14, 0.2, 0.12);
            ring(x, STEAM, x.feet(), 2.8, 18, 0.3, 0.03);
            ring(x, HEAT, x.feet(), radius, sc(x, 24), 0.15, 0.0);
            sound(x, SoundEvents.AMETHYST_BLOCK_RESONATE, x.feet(), 0.8f, 0.5f);
            if (x.chain) {
                ring(x, ParticleTypes.END_ROD, x.feet(), radius * 0.6, 10, 0.8, 0.05);
            }
        });
        c.at(6, x -> {
            ring(x, ParticleTypes.WHITE_ASH, x.feet(), radius, 12, 1.0, 0.0);
            burst(x, ParticleTypes.LAVA, x.feet().add(0, 0.3, 0), 3, radius * 0.7, 0.0);
        });
        // 残り: 輪の中から蒸気が噴き、熾が混じる
        for (int t = 8; t <= 28; t += 5) {
            c.at(t, x -> {
                int k = spend(x, 6);
                RandomSource r = x.random();
                Vec3 f = x.feet();
                for (int i = 0; i < k; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(r.nextDouble()) * radius;
                    x.level.addParticle(i % 3 == 2 ? (ParticleOptions) EMBER_FADE : ParticleTypes.CLOUD,f.x + Math.cos(a) * d, f.y + 0.1, f.z + Math.sin(a) * d, 0, 0.06, 0);
                }
            });
        }
        c.at(12, x -> ring(x, HEAT, x.feet(), radius, 8, 0.05, 0.0));
        c.at(20, x -> ring(x, HEAT, x.feet(), radius, 8, 0.05, 0.0));
        c.lasts(34);
    }

    // ================================================================ 熾の矢雨 (灰+骨: 骨の白い芯に熾の尾。中央から外へ一本ずつ開く扇)
    private static void cinderVolley(SkillFx.Cast c) {
        int bolts = SkillRules.VOLLEY_BOLTS;
        Vec3[] ends = new Vec3[bolts];
        for (int i = 0; i < bolts; i++) {
            ends[i] = boltEnd(c, i, bolts);
        }
        c.at(1, x -> {
            burst(x, CINDER, x.hand(), sc(x, 8), 0.2, 0.01);
            burst(x, BONE, x.hand(), 4, 0.12, 0.01);
        });
        c.at(2, x -> burst(x, ParticleTypes.FLAME, x.hand(), 4, 0.08, 0.01));
        // 放ち: 中央 → ±1 → ±2 の順に、一 tick ずつ
        for (int i = 0; i < bolts; i++) {
            final int b = i;
            int order = Math.abs(i - (bolts - 1) / 2);
            c.at(3 + order, x -> {
                Vec3 from = x.hand();
                line(x, BONE, from, ends[b], 8, 0.02, 0.0);
                line(x, BOLT_FADE, from, ends[b], 6, 0.06, 0.01);
            });
            c.at(4 + order, x -> {
                burst(x, ParticleTypes.FLAME, ends[b], 5, 0.25, 0.05);
                burst(x, ParticleTypes.LAVA, ends[b], 1, 0.1, 0.0);
                burst(x, ParticleTypes.SMOKE, ends[b], 2, 0.2, 0.02);
                if (x.chain) {
                    burst(x, ParticleTypes.ENCHANTED_HIT, ends[b], 3, 0.25, 0.1);
                }
            });
        }
        c.at(3, x -> {
            burst(x, CINDER, x.hand(), 6, 0.15, 0.06);
            kick(x, 0.8f);
            sound(x, SoundEvents.ARROW_SHOOT, x.hand(), 0.6f, 0.8f);
        });
        c.at(4, x -> flash(x, ends[(bolts - 1) / 2]));
        // 残り: 当たった先で灰が舞う
        for (int t = 8; t <= 24; t += 4) {
            c.at(t, x -> {
                Vec3 e = ends[x.random().nextInt(bolts)];
                burst(x, ParticleTypes.ASH, e, 3, 0.4, 0.01);
                burst(x, ParticleTypes.SMOKE, e, 1, 0.2, 0.01);
            });
        }
        c.lasts(30);
    }

    // ================================================================ 道具 (この file の中だけ)

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(rgb(rgb), size);
    }

    private static Vector3f rgb(int rgb) {
        return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    // 位が上がるほど少し増やす (位 1 は n、位 3 で約 1.33 倍)
    private static int sc(SkillFx.Cast c, int n) {
        return n + n * Math.max(0, c.rank - 1) / 6;
    }

    private static double sym(RandomSource r) {
        return r.nextDouble() * 2 - 1;
    }

    // 螺旋: 半径 r0 → r1、高さ h0 → h1 を turns 回まわる。inward で中心へ寄る速さ、up で昇る速さ
    private static void spiral(SkillFx.Cast c, ParticleOptions p, Vec3 center, double r0, double r1, double h0, double h1,
            double turns, int n, double phase, double inward, double up) {
        int k = spend(c, n);
        for (int i = 0; i < k; i++) {
            double t = k <= 1 ? 0 : (double) i / (k - 1);
            double a = phase + turns * Math.PI * 2 * t;
            double r = r0 + (r1 - r0) * t;
            double cs = Math.cos(a);
            double sn = Math.sin(a);
            c.level.addParticle(p, center.x + cs * r, center.y + h0 + (h1 - h0) * t, center.z + sn * r,
                    -cs * inward, up, -sn * inward);
        }
    }

    // 扇の弧 (中心 base、向き dir、半角 deg、半径 dist)。inward で手前へ寄る
    private static void fan(SkillFx.Cast c, ParticleOptions p, Vec3 base, Vec3 dir, double dist, double deg, int n, double lift, double inward) {
        int k = spend(c, n);
        RandomSource r = c.level.random;
        double half = Math.toRadians(deg);
        double yaw0 = Math.atan2(dir.z, dir.x);
        for (int i = 0; i < k; i++) {
            double a = yaw0 + (k <= 1 ? 0 : -half + 2 * half * i / (k - 1)) + sym(r) * 0.03;
            double cs = Math.cos(a);
            double sn = Math.sin(a);
            c.level.addParticle(p, base.x + cs * dist, base.y + lift + r.nextDouble() * 0.3, base.z + sn * dist,
                    -cs * inward, 0.0, -sn * inward);
        }
    }

    // 弧を描く筋 (a から b へ、真ん中が height だけ高い)
    private static void arc(SkillFx.Cast c, ParticleOptions p, Vec3 a, Vec3 b, int n, double height) {
        int k = spend(c, n);
        for (int i = 0; i < k; i++) {
            double t = k <= 1 ? 0 : (double) i / (k - 1);
            Vec3 q = a.lerp(b, t);
            c.level.addParticle(p, q.x, q.y + Math.sin(t * Math.PI) * height, q.z, 0, 0.01, 0);
        }
    }

    // 体のまわりを巡る点 (尾を引くよう、少しずつ角度をずらして n 個)
    private static void orbitDot(SkillFx.Cast c, ParticleOptions p, Vec3 center, double r, double h, double a, int n) {
        int k = spend(c, n);
        for (int i = 0; i < k; i++) {
            double b = a - i * 0.18;
            c.level.addParticle(p, center.x + Math.cos(b) * r, center.y + h, center.z + Math.sin(b) * r, 0, 0, 0);
        }
    }

    // 向きの決まった筋 (速さ vel で流れる)
    private static void streak(SkillFx.Cast c, ParticleOptions p, Vec3 a, Vec3 b, int n, double jitter, Vec3 vel) {
        int k = spend(c, n);
        RandomSource r = c.level.random;
        for (int i = 0; i < k; i++) {
            Vec3 q = a.lerp(b, r.nextDouble());
            c.level.addParticle(p, q.x + sym(r) * jitter, q.y + sym(r) * jitter, q.z + sym(r) * jitter, vel.x, vel.y, vel.z);
        }
    }

    // 鐘の形 (下が広く、上がすぼむ)。高さ h0 → h1 の帯だけ
    private static void bell(SkillFx.Cast c, Vec3 f, double h0, double h1, int n) {
        int k = spend(c, n);
        RandomSource r = c.level.random;
        for (int i = 0; i < k; i++) {
            double h = h0 + (h1 - h0) * r.nextDouble();
            double rad = 0.45 + 0.9 * Math.pow(1.0 - h / 2.4, 2);
            double a = Math.PI * 2 * (i + r.nextDouble() * 0.5) / Math.max(1, k);
            c.level.addParticle(STEAM, f.x + Math.cos(a) * rad, f.y + h, f.z + Math.sin(a) * rad, 0, 0.01, 0);
        }
    }

    // 近くのほかの人 (手元では仲間かどうか分からないので、半径の中のプレイヤー)。近い順に max 人
    private static List<Player> nearbyPlayers(SkillFx.Cast c, double radius, int max) {
        List<Player> out = new ArrayList<>();
        for (Player p : c.level.players()) {
            if (p != c.caster && p.isAlive() && !p.isSpectator() && p.distanceToSqr(c.caster) <= radius * radius) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distanceToSqr(c.caster)));
        return out.size() > max ? out.subList(0, max) : out;
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
