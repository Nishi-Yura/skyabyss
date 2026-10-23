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

import java.util.Map;
import java.util.function.Consumer;

import org.joml.Vector3f;

import jp.revati.skyabyss.abyss.AbyssFx;
import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 骨の射手・淵・前世の技の段取り (遠い間合い・罠・雨・淵)
// 色は木ごとに分ける: 骨の射手 = 空の白と骨の象牙、淵 = 紫と黒 (芯だけ明るい紫)、前世 = 金と温かい白
// 半径・距離はサーバーと同じ数 (SkillRules) を使う。狙いの点は手元で同じように線を引いて求める
public final class FxRanged {
    private FxRanged() {
    }

    // ---------------------------------------------------------------- 色
    private static final int SKY_WHITE = 0xEAF6FF;
    private static final int SKY_BLUE = 0x9FD8FF;
    private static final int BONE = 0xE8DCC0;
    private static final int BONE_SHADOW = 0xA89A80;
    private static final int TEAL = 0x5FE3D0;
    private static final int DEEP_VIOLET = 0x4D147A;
    private static final int DEEP_BLACK = 0x120818;
    private static final int DEEP_CORE = 0xC88CFF;
    private static final int GOLD = 0xFFC94D;
    private static final int PALE_GOLD = 0xFFE39A;
    private static final int WARM = 0xFFF1C8;
    private static final int GHOST = 0xDDE8FF;

    // 矢の速さ (1 tick に進むブロック): 筋の頭が矢と一緒に進むように
    private static final double ARROW_STEP = SkillRules.PIERCE_SPEED;
    private static final double PIERCE_DRAW = 40;

    public static void register(Map<Skill, Consumer<SkillFx.Cast>> plans) {
        plans.put(Skill.CLOUD_PIERCE, FxRanged::cloudPierce);
        plans.put(Skill.BONE_TRAP, FxRanged::boneTrap);
        plans.put(Skill.SKY_RAIN, FxRanged::skyRain);
        plans.put(Skill.DEEP_PULL, FxRanged::deepPull);
        plans.put(Skill.DEEP_ECHO, FxRanged::deepEcho);
        plans.put(Skill.DEEP_MAW, FxRanged::deepMaw);
        plans.put(Skill.ECHO_STRIKE, FxRanged::echoStrike);
        plans.put(Skill.GRAVE_BELL, FxRanged::graveBell);
        plans.put(Skill.FINAL_LIGHT, FxRanged::finalLight);
    }

    // ================================================================ 骨の射手

    // 雲貫き: 手元の閃きと雲の輪 → 矢と一緒に進む空の筋 → 当たった所で閃光と輪 → 筋の跡がゆっくり昇って消える
    private static void cloudPierce(SkillFx.Cast c) {
        int rk = rank(c);
        Vec3 hand = c.hand();
        Vec3 far = c.aim(PIERCE_DRAW);
        BlockHitResult bh = clip(c, c.origin, far);
        boolean wall = bh.getType() != HitResult.Type.MISS;
        Vec3 end = wall ? bh.getLocation() : far;
        double len = Math.max(1.0, end.distanceTo(c.origin));
        Entity first = firstFoe(c, c.origin, end);
        int steps = Math.max(1, Math.min(12, (int) Math.ceil(len / ARROW_STEP)));

        // 放ち
        c.at(1, x -> {
            burst(x, sky(), hand, 8, 0.12, 0.03);
            flash(x, hand.add(x.look.scale(0.4)));
            disc(x, ParticleTypes.CLOUD, x.aim(1.8), x.look, 0.2, 10 + 2 * rk, 0.12);
            line(x, dust(SKY_WHITE, x.chain ? 1.1f : 0.85f), hand, end, 20, 0.02, 0.0);
            sound(x, SoundEvents.CROSSBOW_SHOOT, hand, 0.45f, 1.7f);
            if (x.chain) {
                line(x, ParticleTypes.ELECTRIC_SPARK, hand, hand.lerp(end, Math.min(1.0, 16 / len)), 10, 0.05, 0.02);
            }
        });
        // 進む筋の頭 (矢の速さで 1 tick ずつ)
        for (int s = 1; s <= steps; s++) {
            final Vec3 a = hand.lerp(end, Math.min(1.0, (s - 1) * ARROW_STEP / len));
            final Vec3 b = hand.lerp(end, Math.min(1.0, s * ARROW_STEP / len));
            c.at(s, x -> {
                line(x, sky(), a, b, 4, 0.04, 0.0);
                burst(x, ParticleTypes.CRIT, b, 1, 0.05, 0.05);
            });
        }
        // 当たり: 最初の敵 (矢は貫いて進む) と、刺さる壁
        if (first != null) {
            int tick = 1 + (int) Math.ceil(first.position().distanceTo(c.origin) / ARROW_STEP);
            c.at(tick, x -> {
                Vec3 h = chest(first);
                flash(x, h);
                burst(x, ParticleTypes.ENCHANTED_HIT, h, 10, 0.3, 0.25);
                disc(x, sky(), h, x.look, 0.7, 10, 0.06);
            });
        }
        int endTick = 1 + steps;
        if (wall) {
            BlockState hitState = c.level.getBlockState(bh.getBlockPos());
            c.at(endTick, x -> {
                flash(x, end);
                burst(x, ParticleTypes.CLOUD, end, 5, 0.2, 0.04);
                burst(x, sky(), end, 8, 0.3, 0.08);
                if (!hitState.isAir()) {
                    burst(x, new BlockParticleOption(ParticleTypes.BLOCK, hitState), end, 6, 0.2, 0.1);
                }
            });
        } else {
            c.at(endTick, x -> burst(x, sky(), end, 6, 0.3, 0.03));
        }
        // 残り: 筋の跡が淡く昇る
        c.at(endTick + 4, x -> line(x, sky(), hand, end, 12, 0.15, 0.012));
        c.lasts(endTick + 22);
    }

    // 骨の罠: 骨を足もとへ落とす → 本当の踏み幅 (半径 2) の骨の輪 → 骨と青緑の螺旋が昇りながら閉じる → 内へ弾けて噛み合う → 淡い輪が二度
    private static void boneTrap(SkillFx.Cast c) {
        int rk = rank(c);
        Vec3 spot = c.feet();
        double r = SkillRules.TRAP_TRIGGER;

        c.at(1, x -> {
            burst(x, dust(BONE, 1.0f), x.hand(), 6, 0.1, 0.0);
            line(x, dust(BONE, 0.8f), x.hand(), spot.add(0, 0.1, 0), 6, 0.05, 0.0);
            sound(x, SoundEvents.BONE_BLOCK_PLACE, spot, 0.9f, 0.8f);
        });
        c.at(2, x -> {
            ring(x, dust(BONE, x.chain ? 1.5f : 1.3f), spot, r, 22 + 4 * rk, 0.08, 0.0);
            ring(x, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), spot, r * 0.9, 8, 0.1, 0.06);
            ring(x, rune(), spot, r * 0.5, 8, 0.05, 0.0);
        });
        // 昇って閉じる螺旋 (3 本)
        for (int s = 0; s < 8; s++) {
            final double t = (s + 1) / 8.0;
            c.at(3 + s, x -> spiralStep(x, fade(BONE, TEAL, 1.0f), spot, r * (1 - 0.85 * t), 0.1 + 1.3 * t, t * Math.PI * 2.5, 3));
        }
        // 噛み合う
        c.at(11, x -> {
            flash(x, spot.add(0, 0.8, 0));
            burst(x, ParticleTypes.CRIT, spot.add(0, 0.8, 0), 10, 0.25, 0.2);
            ring(x, rune(), spot, r, 16, 0.15, -0.28);
            sound(x, SoundEvents.CHAIN_PLACE, spot, 0.8f, 0.7f);
            if (x.chain) {
                burst(x, ParticleTypes.ELECTRIC_SPARK, spot.add(0, 0.6, 0), 8, 0.4, 0.08);
            }
        });
        // 残り: 罠の場所が分かる淡い輪
        c.at(22, x -> ring(x, dust(BONE_SHADOW, 1.0f), spot, r, 12, 0.06, 0.0));
        c.at(34, x -> ring(x, dust(BONE_SHADOW, 0.8f), spot, r, 10, 0.06, 0.0));
        c.lasts(44);
    }

    // 天の驟雨: 空へ放つ筋 → 降る範囲 (半径 4) の輪 → 上に光の雲 → 8 度、光の矢が降って地面ではじける (サーバーの一打ちに合わせる) → 輪が昇って消える
    private static void skyRain(SkillFx.Cast c) {
        int rk = rank(c);
        double rad = SkillRules.RAIN_RADIUS;
        Vec3 center = rainCenter(c);
        Vec3 hand = c.hand();
        Vec3 up = hand.add(c.fwd.scale(1.5)).add(0, 7, 0);
        RandomSource rnd = c.random();

        c.at(1, x -> {
            burst(x, sky(), hand, 8, 0.12, 0.03);
            flash(x, hand.add(0, 0.3, 0));
            line(x, sky(), hand, up, 12, 0.03, 0.0);
            sound(x, SoundEvents.ARROW_SHOOT, hand, 0.6f, 1.4f);
        });
        c.at(3, x -> {
            ring(x, dust(SKY_BLUE, x.chain ? 1.5f : 1.2f), center, rad, 26 + 2 * rk, 0.12, 0.0);
            ring(x, sky(), center, rad * 0.5, 10, 0.12, 0.0);
        });
        c.at(6, x -> {
            Vec3 cloud = center.add(0, 6.5, 0);
            burst(x, ParticleTypes.CLOUD, cloud, 6, rad * 0.5, 0.01);
            burst(x, sky(), cloud, 10, rad * 0.6, 0.02);
        });
        int pulses = SkillRules.RAIN_PULSES;
        int every = SkillRules.RAIN_INTERVAL;
        int bolts = 3;
        for (int p = 1; p <= pulses; p++) {
            final double[][] spots = new double[bolts][2];
            for (int i = 0; i < bolts; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(rnd.nextDouble()) * rad;
                spots[i][0] = center.x + Math.cos(a) * d;
                spots[i][1] = center.z + Math.sin(a) * d;
            }
            final boolean edge = p == 1 || p == pulses;
            final boolean firstPulse = p == 1;
            // 降り始め (光は 5 tick ほどで地面に着く)
            c.at(p * every - 4, x -> {
                for (double[] s : spots) {
                    fall(x, sky(), s[0], center.y + 6.0, s[1], 3, 0.45, 1.4);
                }
            });
            // 着地 (サーバーの一打ちの tick)
            c.at(p * every + 1, x -> {
                ParticleOptions pop = x.chain ? ParticleTypes.ELECTRIC_SPARK : sky();
                for (double[] s : spots) {
                    burst(x, pop, new Vec3(s[0], center.y + 0.15, s[1]), 2, 0.15, 0.08);
                }
                if (edge) {
                    flash(x, center.add(0, 0.3, 0));
                }
                if (firstPulse) {
                    sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, center, 0.5f, 1.5f);
                }
            });
        }
        int last = pulses * every + 4;
        c.at(last, x -> ring(x, sky(), center, rad * 0.8, 10, 0.3, 0.02));
        c.lasts(last + 12);
    }

    // ================================================================ 淵

    // 淵の引き: 手に闇が吸い込まれる → 本当の引き寄せ範囲 (足もと半径 7) の紫黒の輪 → 輪が寄せ点へ縮みながら粒が流れ込む → 寄せ点で明るい芯と黒い衝撃の輪 → 細い渦が残る
    private static void deepPull(SkillFx.Cast c) {
        int rk = rank(c);
        double rad = SkillRules.PULL_RADIUS;
        Vec3 feet = c.feet();
        Vec3 point = new Vec3(feet.x + c.fwd.x * 3.0, feet.y, feet.z + c.fwd.z * 3.0);

        c.at(1, x -> {
            converge(x, x.hand(), 0.6, 1.0, 8, 0.0);
            burst(x, dust(DEEP_BLACK, 1.2f), x.hand(), 5, 0.15, 0.0);
            ring(x, fade(DEEP_VIOLET, DEEP_BLACK, 1.4f), feet, rad, 26 + 2 * rk, 0.08, 0.0);
            sound(x, SoundEvents.PORTAL_AMBIENT, point, 0.25f, 2.0f);
        });
        // 縮む輪と流れ込み (足もとの輪から寄せ点へ)
        for (int s = 0; s < 6; s++) {
            final double t = (s + 1) / 7.0;
            final double r = rad * (1 - t);
            final Vec3 mid = feet.lerp(point, (s + 1) / 6.0);
            c.at(2 + s, x -> {
                ring(x, dust(DEEP_VIOLET, 1.2f), mid, r, 6, 0.1, 0.0);
                converge(x, point.add(0, 0.3, 0), r * 0.6, r + 0.8, 8, 0.4);
            });
        }
        c.at(8, x -> {
            Vec3 core = point.add(0, 0.6, 0);
            flash(x, core);
            burst(x, dust(DEEP_CORE, x.chain ? 1.7f : 1.4f), core, 10, 0.3, 0.0);
            ring(x, ParticleTypes.SMOKE, point, 0.8, 14, 0.15, 0.18);
            ring(x, ParticleTypes.REVERSE_PORTAL, point, 0.5, 8, 0.2, 0.08);
            sound(x, SoundEvents.CHORUS_FRUIT_TELEPORT, point, 0.6f, 0.6f);
            if (x.chain) {
                burst(x, ParticleTypes.WITCH, core, 8, 0.4, 0.02);
            }
        });
        // 残り: 寄せ点に細い渦
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            c.at(12 + k * 6, x -> spiralStep(x, AbyssFx.DUST, point, 0.6, 0.2 + kk * 0.4, kk * 1.3, 3));
        }
        c.lasts(40);
    }

    // 淵の残響: 体に闇が吸い込まれる → 胸で明るい芯、足もとから黒い輪が三度 (こだまのように小さく) → 5 秒のあいだ体に淡い渦 → 切れる時に吸い込まれて消える
    private static void deepEcho(SkillFx.Cast c) {
        int rk = rank(c);
        Vec3 feet = c.feet();
        int span = SkillRules.ECHO_TICKS;

        c.at(1, x -> {
            converge(x, x.hand(), 0.5, 0.9, 8, 0.0);
            converge(x, feet.add(0, 1.0, 0), 1.2, 2.0, 8, 0.0);
            sound(x, SoundEvents.AMETHYST_BLOCK_RESONATE, feet, 0.8f, 0.5f);
        });
        c.at(3, x -> {
            Vec3 chest = x.caster.position().add(0, 1.1, 0);
            flash(x, chest);
            burst(x, dust(DEEP_CORE, x.chain ? 1.6f : 1.3f), chest, 8, 0.25, 0.0);
            ring(x, fade(DEEP_CORE, DEEP_BLACK, 1.5f), feet, 1.4, 18 + 2 * rk, 0.08, 0.0);
            ring(x, ParticleTypes.SMOKE, feet, 1.0, 12, 0.1, 0.16);
            ring(x, ParticleTypes.SCULK_CHARGE_POP, feet, 1.4, 6, 0.15, 0.0);
        });
        c.at(8, x -> {
            ring(x, fade(DEEP_VIOLET, DEEP_BLACK, 1.3f), feet, 2.4, 16, 0.08, 0.0);
            sound(x, SoundEvents.AMETHYST_BLOCK_RESONATE, feet, 0.45f, 0.45f);
        });
        c.at(13, x -> ring(x, fade(DEEP_VIOLET, DEEP_BLACK, 1.1f), feet, 3.4, 14, 0.08, 0.0));
        // 力が続くあいだの淡い渦 (今の位置について行く)
        for (int t = 18; t < span; t += 8) {
            final int tt = t;
            c.at(t, x -> {
                Vec3 p = x.caster.position();
                spiralStep(x, AbyssFx.DUST, p, 0.55, 0.3 + (tt % 24) / 24.0 * 1.3, tt * 0.7, 2);
                burst(x, ParticleTypes.REVERSE_PORTAL, p.add(0, 1.0, 0), x.chain ? 3 : 2, 0.4, 0.01);
            });
        }
        c.at(span, x -> converge(x, x.caster.position().add(0, 1.0, 0), 0.6, 1.0, 6, 0.0));
        c.lasts(span + 6);
    }

    // 淵の顎: 手から狙いへ闇の糸 → 本当の範囲 (半径 4) の黒い輪 → 輪が縮みながら粒が引きずり込まれる → 0.4 秒後、地面から牙が内へ閉じ、明るい芯と黒い衝撃の輪・揺れ → 煙と渦が残る
    private static void deepMaw(SkillFx.Cast c) {
        int rk = rank(c);
        double rad = SkillRules.MAW_RADIUS;
        Vec3 center = aimSpot(c, SkillRules.MAW_REACH);

        c.at(1, x -> {
            converge(x, x.hand(), 0.5, 0.9, 6, 0.0);
            line(x, fade(DEEP_CORE, DEEP_BLACK, 1.0f), x.hand(), center.add(0, 0.5, 0), 14, 0.05, 0.0);
            ring(x, fade(DEEP_BLACK, DEEP_VIOLET, 1.6f), center, rad, 26 + 2 * rk, 0.08, 0.0);
        });
        for (int s = 0; s < 6; s++) {
            final double r = rad * (1 - (s + 1) / 7.0);
            final double lift = 0.12 + s * 0.05;
            c.at(2 + s, x -> {
                ring(x, dust(DEEP_VIOLET, 1.2f), center, r, 6, lift, 0.0);
                converge(x, center.add(0, 0.4, 0), 1.0, rad, 8, 0.3);
            });
        }
        c.at(7, x -> burst(x, dust(DEEP_CORE, 1.5f), center.add(0, 0.7, 0), 6, 0.15, 0.0));
        // 噛む (サーバーの 8 tick 後)
        c.at(9, x -> {
            Vec3 core = center.add(0, 0.7, 0);
            flash(x, core);
            teeth(x, fade(DEEP_BLACK, DEEP_CORE, 1.3f), center, rad, 12, 1.7);
            ring(x, ParticleTypes.LARGE_SMOKE, center, 1.2, 10, 0.2, 0.22);
            burst(x, dust(DEEP_CORE, x.chain ? 1.6f : 1.2f), core, 8, 0.5, 0.0);
            kick(x, 0.9f);
            sound(x, SoundEvents.WARDEN_SONIC_BOOM, center, 0.3f, 1.5f);
            if (x.chain) {
                burst(x, ParticleTypes.WITCH, core, 8, 0.5, 0.02);
            }
        });
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            c.at(13 + k * 6, x -> {
                spiralStep(x, AbyssFx.DUST, center, rad * 0.4, 0.3 + kk * 0.5, kk * 1.7, 2);
                burst(x, ParticleTypes.SMOKE, center.add(0, 0.4, 0), 2, rad * 0.4, 0.01);
            });
        }
        c.lasts(40);
    }

    // ================================================================ 前世の技

    // 遺響の一撃: 手から相手へ金の筋と斜めの一閃 → 前の人生の影 (淡い人影) が相手へ歩み寄る → 0.5 秒後、逆向きの二の太刀と閃光 → 金の粒が残る
    private static void echoStrike(SkillFx.Cast c) {
        int rk = rank(c);
        LivingEntity target = echoTarget(c);
        Vec3 hand = c.hand();
        Vec3 feet = c.feet();
        Vec3 hit0 = target != null ? chest(target) : c.aim(4);
        Vec3 ghostTo = target != null ? target.position() : c.ahead(3);

        c.at(1, x -> {
            burst(x, dust(PALE_GOLD, 1.0f), hand, 6, 0.12, 0.0);
            line(x, fade(WARM, GHOST, 0.9f), hand, hit0, 12, 0.04, 0.0);
            slashArc(x, dust(GOLD, 1.2f), hit0, x.fwd, false, 14 + 2 * rk);
            burst(x, ParticleTypes.SWEEP_ATTACK, hit0, 1, 0.0, 0.0);
            flash(x, hit0);
            sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, hit0, 0.8f, 0.7f);
        });
        // 前の人生の影が歩み寄る
        for (int s = 0; s < 7; s++) {
            final Vec3 at = feet.lerp(ghostTo, (s + 1) / 8.0 * 0.85);
            c.at(3 + s, x -> line(x, fade(GHOST, WARM, 0.9f), at, at.add(0, 1.8, 0), 4, 0.06, 0.0));
        }
        c.at(11, x -> {
            Vec3 h = target != null && target.isAlive() ? chest(target) : hit0;
            Vec3 ground = target != null && target.isAlive() ? target.position() : ghostTo;
            slashArc(x, dust(WARM, 1.2f), h, x.fwd, true, 12 + 2 * rk);
            flash(x, h);
            ring(x, fade(GOLD, GHOST, 1.0f), ground, 0.9, 10, 0.05, 0.03);
            burst(x, ParticleTypes.END_ROD, h, 5, 0.3, 0.03);
            sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, h, 0.6f, 0.5f);
            if (x.chain) {
                burst(x, ParticleTypes.ELECTRIC_SPARK, h, 8, 0.4, 0.1);
            }
        });
        c.at(16, x -> {
            Vec3 h = target != null && target.isAlive() ? chest(target) : hit0;
            burst(x, dust(PALE_GOLD, 0.8f), h, 8, 0.5, 0.0);
        });
        c.lasts(30);
    }

    // 墓鐘: 頭上に金の鐘の形 → 打つ (閃光・鐘の音・鐘から足もとへ光の柱) → 輪が半径 2.5 と 5 (本当の範囲) に広がる → 鐘の縁が二度響き、遅くする間 (3 秒) 淡い輪
    private static void graveBell(SkillFx.Cast c) {
        int rk = rank(c);
        double rad = SkillRules.BELL_RADIUS;
        Vec3 base = c.feet();
        Vec3 bell = base.add(0, 2.9, 0);

        c.at(1, x -> {
            burst(x, dust(PALE_GOLD, 1.0f), x.hand(), 6, 0.12, 0.0);
            bellShape(x, bell, dust(GOLD, 1.1f));
        });
        c.at(4, x -> {
            flash(x, bell.add(0, 0.3, 0));
            sound(x, SoundEvents.BELL_BLOCK, bell, 1.0f, 0.6f);
            ring(x, ParticleTypes.END_ROD, bell, 1.0, 10, 0.0, 0.06);
            line(x, ParticleTypes.END_ROD, base.add(0, 0.1, 0), bell, 8, 0.12, 0.004);
        });
        c.at(5, x -> ring(x, fade(GOLD, WARM, 1.3f), base, rad * 0.5, 18, 0.15, 0.0));
        c.at(7, x -> {
            ring(x, fade(WARM, GOLD, 1.5f), base, rad, 30 + 4 * rk, 0.12, 0.0);
            ring(x, ParticleTypes.CRIT, base, rad, 8, 0.4, 0.02);
            if (x.chain) {
                ring(x, ParticleTypes.ELECTRIC_SPARK, base, rad * 0.75, 10, 0.3, 0.03);
            }
        });
        c.at(14, x -> ring(x, dust(PALE_GOLD, 0.8f), bell, 0.95, 10, 0.0, 0.0));
        c.at(24, x -> ring(x, dust(PALE_GOLD, 0.7f), bell, 0.95, 8, 0.0, 0.0));
        c.at(20, x -> ring(x, dust(BONE_SHADOW, 0.9f), base, rad, 12, 0.08, 0.0));
        c.at(40, x -> ring(x, dust(BONE_SHADOW, 0.8f), base, rad, 10, 0.08, 0.0));
        c.lasts(64);
    }

    // 最期の光: 足もとに光が立ち上がる (閃光・揺れ・鐘の高い音) → サーバーと同じ三つの輪 (半径 1.8 / 3.7 / 5.5) → 高い光の柱 → 外の輪に光の杭が並び、遠い雷鳴 → 温かい火の粉が昇って残る
    private static void finalLight(SkillFx.Cast c) {
        int rk = rank(c);
        double rad = SkillRules.LIGHT_RADIUS;
        Vec3 base = c.feet();

        c.at(1, x -> {
            burst(x, dust(WARM, 1.2f), x.hand(), 8, 0.15, 0.0);
            line(x, fade(GOLD, WARM, 1.2f), base, base.add(0, 2.6, 0), 10, 0.1, 0.0);
            flash(x, base.add(0, 1.1, 0));
            kick(x, 1.0f);
            sound(x, SoundEvents.BELL_RESONATE, base, 0.6f, 1.4f);
        });
        for (int i = 0; i < 3; i++) {
            final double r = rad * (i + 1) / 3.0;
            final int n = 16 + i * 7 + 2 * rk;
            final float size = 1.4f + 0.1f * i;
            c.at(1 + i * 3, x -> {
                ring(x, fade(WARM, GOLD, size), base, r, n, 0.12, 0.0);
                if (x.chain) {
                    ring(x, ParticleTypes.CRIT, base, r, 10, 0.3, 0.03);
                }
            });
        }
        c.at(2, x -> {
            line(x, ParticleTypes.END_ROD, base.add(0, 0.2, 0), base.add(0, 6, 0), 14, 0.15, 0.004);
            burst(x, ParticleTypes.FIREWORK, base.add(0, 6, 0), 6, 0.3, 0.06);
        });
        c.at(7, x -> {
            pillars(x, fade(WARM, GOLD, 1.2f), base, rad, rk >= 3 ? 10 : 8, 1.6);
            flash(x, base.add(0, 0.4, 0));
            sound(x, SoundEvents.LIGHTNING_BOLT_THUNDER, base, 0.2f, 1.8f);
        });
        for (int k = 0; k < 4; k++) {
            c.at(12 + k * 6, x -> embers(x, base, rad, 5));
        }
        c.lasts(50);
    }

    // ================================================================ 道具

    private static int rank(SkillFx.Cast c) {
        return Mth.clamp(c.rank, 1, 3);
    }

    private static Vector3f rgb(int c) {
        return new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
    }

    private static DustParticleOptions dust(int color, float size) {
        return new DustParticleOptions(rgb(color), size);
    }

    private static DustColorTransitionOptions fade(int from, int to, float size) {
        return new DustColorTransitionOptions(rgb(from), rgb(to), size);
    }

    private static BlockHitResult clip(SkillFx.Cast c, Vec3 from, Vec3 to) {
        return c.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.caster));
    }

    // 手元で分かる範囲の「敵らしい」相手 (他の遊び手・鎧立ては除く)
    private static boolean foe(SkillFx.Cast c, Entity e) {
        return e != c.caster && e != c.caster.getVehicle() && e instanceof LivingEntity le && le.isAlive()
                && !(e instanceof Player) && !(e instanceof ArmorStand) && !e.isSpectator();
    }

    private static Entity firstFoe(SkillFx.Cast c, Vec3 from, Vec3 to) {
        EntityHitResult h = ProjectileUtil.getEntityHitResult(c.caster, from, to, new AABB(from, to).inflate(1.0), e -> foe(c, e),
                from.distanceToSqr(to));
        return h == null ? null : h.getEntity();
    }

    private static Vec3 chest(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.6, 0);
    }

    // 驟雨の中心 (サーバーの rain と同じ求め方)
    private static Vec3 rainCenter(SkillFx.Cast c) {
        Vec3 end = c.aim(SkillRules.RAIN_REACH);
        BlockHitResult b = clip(c, c.origin, end);
        Vec3 center = b.getType() == HitResult.Type.MISS ? end : b.getLocation();
        Entity e = firstFoe(c, c.origin, center);
        if (e != null) {
            return e.position();
        }
        BlockHitResult g = clip(c, center.add(0, 0.5, 0), center.add(0, -8, 0));
        return g.getType() == HitResult.Type.MISS ? center : g.getLocation();
    }

    // 淵の顎の狙い (サーバーの ExtActions.aim と同じ求め方)
    private static Vec3 aimSpot(SkillFx.Cast c, double reach) {
        Vec3 end = c.aim(reach);
        BlockHitResult b = clip(c, c.origin, end);
        Vec3 center = b.getType() == HitResult.Type.MISS ? end : b.getLocation();
        Entity e = firstFoe(c, c.origin, center);
        if (e != null) {
            return e.position();
        }
        return b.getType() != HitResult.Type.MISS ? center.add(0, 0.1, 0) : center;
    }

    // 遺響の一撃の相手 (サーバーの echoStrike と同じ選び方: 前 60 度・7 ブロック)
    private static LivingEntity echoTarget(SkillFx.Cast c) {
        double range = SkillRules.ECHO_STRIKE_RANGE;
        LivingEntity best = null;
        double score = Double.MAX_VALUE;
        AABB box = c.caster.getBoundingBox().inflate(range + 1, 2.0, range + 1);
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, f -> foe(c, f))) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(c.origin);
            double dist = to.length();
            if (dist > range + e.getBbWidth() / 2 || dist < 1e-4) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(c.look), -1, 1)));
            if (angle > 60) {
                continue;
            }
            double s = angle * 0.2 + dist;
            if (s < score) {
                score = s;
                best = e;
            }
        }
        return best;
    }

    // 向き dir に垂直な円盤の輪 (外へ広がる)。矢の口の雲の輪、当たりの輪
    private static void disc(SkillFx.Cast c, ParticleOptions p, Vec3 center, Vec3 dir, double radius, int n, double outward) {
        Vec3 d = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = d.cross(ref).normalize();
        Vec3 b = d.cross(a).normalize();
        int k = spend(c, n);
        RandomSource r = c.random();
        for (int i = 0; i < k; i++) {
            double ang = Math.PI * 2 * (i + r.nextDouble() * 0.4) / Math.max(1, k);
            Vec3 o = a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
            c.level.addParticle(p, center.x + o.x * radius, center.y + o.y * radius, center.z + o.z * radius,
                    o.x * outward + d.x * 0.02, o.y * outward + d.y * 0.02, o.z * outward + d.z * 0.02);
        }
    }

    // 螺旋の一段: strands 本の筋を、この高さ・半径・角度に 2 粒ずつ
    private static void spiralStep(SkillFx.Cast c, ParticleOptions p, Vec3 center, double radius, double y, double angle, int strands) {
        int k = spend(c, strands * 2);
        for (int i = 0; i < k; i++) {
            double a = angle + (i / 2) * Math.PI * 2 / strands - (i % 2) * 0.25;
            c.level.addParticle(p, center.x + Math.cos(a) * radius, center.y + y - (i % 2) * 0.08, center.z + Math.sin(a) * radius, 0, 0, 0);
        }
    }

    // 淵の粒 (PORTAL) が中心へ流れ込む: 粒は中心 + 速さの位置に現れて中心へ戻る
    private static void converge(SkillFx.Cast c, Vec3 at, double rMin, double rMax, int n, double up) {
        int k = spend(c, n);
        RandomSource r = c.random();
        for (int i = 0; i < k; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = rMin + r.nextDouble() * Math.max(0, rMax - rMin);
            c.level.addParticle(ParticleTypes.PORTAL, at.x, at.y, at.z, Math.cos(a) * d, (r.nextDouble() - 0.3) * up, Math.sin(a) * d);
        }
    }

    // 縦に並んだ粒を真下へ落とす (降る光の矢)
    private static void fall(SkillFx.Cast c, ParticleOptions p, double x, double top, double z, int n, double gap, double speed) {
        int k = spend(c, n);
        for (int i = 0; i < k; i++) {
            c.level.addParticle(p, x, top + i * gap, z, 0, -speed, 0);
        }
    }

    // 斜めの三日月の一閃 (相手の胸の前。reverse で逆の斜め)
    private static void slashArc(SkillFx.Cast c, ParticleOptions p, Vec3 center, Vec3 fwd, boolean reverse, int n) {
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 diag = right.add(0, reverse ? -0.8 : 0.8, 0).normalize();
        int k = spend(c, n);
        for (int i = 0; i < k; i++) {
            double t = k <= 1 ? 0 : (double) i / (k - 1);
            double th = Math.toRadians(-70 + 140 * t);
            Vec3 q = center.add(fwd.scale(-Math.cos(th) * 0.35)).add(diag.scale(Math.sin(th) * 1.0));
            c.level.addParticle(p, q.x, q.y, q.z, 0, 0, 0);
        }
    }

    // 地面から内へ傾いて閉じる牙 (淵の顎)。1 本 3 粒
    private static void teeth(SkillFx.Cast c, ParticleOptions p, Vec3 center, double radius, int count, double height) {
        int k = spend(c, count * 3);
        RandomSource r = c.random();
        double phase = r.nextDouble() * Math.PI;
        for (int i = 0; i < k; i++) {
            int tooth = i / 3;
            int seg = i % 3;
            double a = phase + tooth * Math.PI * 2 / count;
            double f = seg / 2.0;
            double rr = radius * (1 - 0.45 * f);
            c.level.addParticle(p, center.x + Math.cos(a) * rr, center.y + 0.1 + height * f, center.z + Math.sin(a) * rr, 0, 0, 0);
        }
    }

    // 鐘の形 (上から肩・胴・縁の輪と舌)。約 36 粒
    private static void bellShape(SkillFx.Cast c, Vec3 lip, ParticleOptions p) {
        ring(c, p, lip, 0.22, 4, 1.05, 0.0);
        ring(c, p, lip, 0.42, 6, 0.85, 0.0);
        ring(c, p, lip, 0.5, 6, 0.55, 0.0);
        ring(c, p, lip, 0.62, 8, 0.25, 0.0);
        ring(c, p, lip, 0.9, 10, 0.0, 0.0);
        line(c, p, lip.add(0, 0.5, 0), lip.add(0, -0.15, 0), 2, 0.0, 0.0);
    }

    // 外の輪に並ぶ光の杭 (1 本 3 粒)
    private static void pillars(SkillFx.Cast c, ParticleOptions p, Vec3 center, double radius, int count, double height) {
        int k = spend(c, count * 3);
        double phase = c.random().nextDouble() * Math.PI;
        for (int i = 0; i < k; i++) {
            double a = phase + (i / 3) * Math.PI * 2 / count;
            double y = 0.15 + height * (i % 3) / 2.0;
            c.level.addParticle(p, center.x + Math.cos(a) * radius, center.y + y, center.z + Math.sin(a) * radius, 0, 0, 0);
        }
    }

    // 範囲の中から昇る温かい火の粉 (END_ROD はゆっくり漂って残る)
    private static void embers(SkillFx.Cast c, Vec3 center, double radius, int n) {
        int k = spend(c, n);
        RandomSource r = c.random();
        for (int i = 0; i < k; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(r.nextDouble()) * radius;
            c.level.addParticle(ParticleTypes.END_ROD, center.x + Math.cos(a) * d, center.y + 0.2 + r.nextDouble() * 0.6, center.z + Math.sin(a) * d,
                    0, 0.03 + r.nextDouble() * 0.02, 0);
        }
    }
}
