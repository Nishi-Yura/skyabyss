package jp.revati.skyabyss.rpg.client.skill.fx;

import java.util.Map;
import java.util.function.Consumer;

import org.joml.Vector3f;

import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import jp.revati.skyabyss.rpg.client.skill.SkillFx.Cast;
import jp.revati.skyabyss.rpg.skill.Skill;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 岩守り・風読み・灰の鍛え人の技の段取り (近接・機動)
// 色の決まり: 岩 = 骨色の粉と青緑の芯・岩の欠片 / 風 = 白と淡い緑の筋・GUST / 灰 = 熾の橙・炎・溶岩・灰
// 輪の半径・扇の角度・当たる点は SkillActions / SkillRules と同じ数で置く (刻印の違いは手元では分からないので素の形)
public final class FxFrontline {
    private FxFrontline() {
    }

    // ---------------------------------------------------------------- 技の形 (SkillRules と同じ数)
    private static final double CLEAVE_RANGE = 4.0;
    private static final double CLEAVE_HALF = 60;
    private static final double CLEAVE_ARC_R = 2.6;
    private static final double ROCK_CALL_RADIUS = 8;
    private static final double SWEEP_RADIUS = 3.5;
    private static final double LUNGE_STOP = 1.4;
    private static final double REND_RANGE = 4.0;
    private static final int REND_GAP = 4;
    private static final double TEMPER_RANGE = 6;
    private static final double HAMMER_RADIUS = 3.5;
    private static final double HAMMER_REACH = 2.0;
    private static final double ASH_RADIUS = 5;

    // ---------------------------------------------------------------- 色
    // 岩
    private static final DustParticleOptions BONE = dust(0xE8DCC0, 1.8f);
    private static final DustParticleOptions BONE_S = dust(0xD8CCB0, 1.2f);
    private static final DustParticleOptions CYAN = dust(0x8CF6FF, 1.7f);
    private static final DustParticleOptions FOAM = dust(0xF2FFFF, 2.0f);
    private static final DustParticleOptions SEA = dust(0x58D6E8, 1.7f);
    // 風
    private static final DustParticleOptions WIND = dust(0xEFFFF6, 1.4f);
    private static final DustParticleOptions WIND_GREEN = dust(0xB8F2C8, 1.2f);
    // 灰
    private static final DustParticleOptions HEAT = dust(0xFF8A2A, 1.8f);
    private static final DustParticleOptions CINDER = dust(0xFFC860, 1.3f);
    private static final DustParticleOptions ASH_GREY = dust(0x5A5450, 2.2f);
    private static final DustColorTransitionOptions EMBER_TO_ASH = new DustColorTransitionOptions(
            rgb(0xFF7A20), rgb(0x4A4440), 1.9f);
    // 連ね (chain) の明るい差し色
    private static final DustParticleOptions GOLD = dust(0xFFF0A0, 1.4f);

    private static final BlockParticleOption CALCITE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState());
    private static final BlockParticleOption BONE_BLOCK = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState());
    private static final BlockParticleOption MAGMA = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MAGMA_BLOCK.defaultBlockState());
    private static final BlockParticleOption BASALT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState());

    public static void register(Map<Skill, Consumer<SkillFx.Cast>> plans) {
        plans.put(Skill.TITAN_CLEAVE, FxFrontline::titanCleave);
        plans.put(Skill.ROCK_CALL, FxFrontline::rockCall);
        plans.put(Skill.WHALE_SWEEP, FxFrontline::whaleSweep);
        plans.put(Skill.LUNGE, FxFrontline::lunge);
        plans.put(Skill.BACKSTEP, FxFrontline::backstep);
        plans.put(Skill.WIND_REND, FxFrontline::windRend);
        plans.put(Skill.TEMPER, FxFrontline::temper);
        plans.put(Skill.EMBER_HAMMER, FxFrontline::emberHammer);
        plans.put(Skill.ASH_SCATTER, FxFrontline::ashScatter);
    }

    // ================================================================ 岩守り

    // 巨神斬り: 頭上に骨色の粉が集まり → 右上から左下へ半径 2.6 の弧 (前方 120 度) → 扇の地面に欠片と輪 → 粉が立ちのぼる
    private static void titanCleave(Cast c) {
        Vec3 base = c.caster.position();
        Vec3 up = c.hand().add(0, 0.6, 0);
        c.at(1, x -> {
            converge(x, ParticleTypes.END_ROD, up, 1.2, n(x, 8), 0.09);
            SkillFx.burst(x, BONE_S, up, n(x, 6), 0.15, 0.0);
        });
        c.at(2, x -> {
            converge(x, SkillFx.rune(), up, 0.9, n(x, 6), 0.08);
            SkillFx.burst(x, CYAN, up, 6, 0.1, 0.0);
        });
        // 放ち: 弧の前半 (右上 → 正面)
        c.at(3, x -> {
            SkillFx.line(x, CYAN, up, arcPoint(x, base, CLEAVE_ARC_R, CLEAVE_HALF, 2.0), 5, 0.04, 0.0);
            arc(x, BONE, base, x.fwd, CLEAVE_ARC_R, CLEAVE_HALF, 0, n(x, 14), 2.0, 1.2, 0.05);
            arc(x, CYAN, base, x.fwd, CLEAVE_ARC_R - 0.3, CLEAVE_HALF, 0, 8, 1.9, 1.15, 0.0);
            SkillFx.burst(x, ParticleTypes.SWEEP_ATTACK, arcPoint(x, base, CLEAVE_ARC_R, 30, 1.6), 1, 0.0, 0.0);
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, base, 0.6f, 0.6f);
        });
        // 当たり: 弧の後半 (正面 → 左下)、閃光、欠片
        c.at(4, x -> {
            arc(x, BONE, base, x.fwd, CLEAVE_ARC_R, 0, -CLEAVE_HALF, n(x, 14), 1.2, 0.4, 0.05);
            arc(x, CYAN, base, x.fwd, CLEAVE_ARC_R - 0.3, 0, -CLEAVE_HALF, 8, 1.15, 0.4, 0.0);
            SkillFx.burst(x, ParticleTypes.SWEEP_ATTACK, arcPoint(x, base, CLEAVE_ARC_R, -30, 0.8), 1, 0.0, 0.0);
            SkillFx.flash(x, base.add(x.fwd.scale(2.6)).add(0, 0.7, 0));
            spray(x, CALCITE, base.add(x.fwd.scale(2.4)), n(x, 10), 1.2, 0.12, 0.32);
            spray(x, BONE_BLOCK, base.add(x.fwd.scale(2.4)), 6, 1.0, 0.1, 0.28);
            if (x.chain) {
                arc(x, GOLD, base, x.fwd, CLEAVE_ARC_R + 0.25, CLEAVE_HALF, -CLEAVE_HALF, 12, 2.1, 0.5, 0.0);
                SkillFx.flash(x, up);
            }
            SkillFx.kick(x, 1.0f);
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, base, 0.45f, 0.55f);
        });
        // 地面の扇 (射程 4 の縁と内側の輪)
        c.at(5, x -> {
            arc(x, BONE, base, x.fwd, CLEAVE_RANGE, CLEAVE_HALF, -CLEAVE_HALF, n(x, 18), 0.1, 0.1, 0.0);
            arc(x, CYAN, base, x.fwd, 2.0, CLEAVE_HALF, -CLEAVE_HALF, 10, 0.12, 0.12, 0.0);
            SkillFx.burst(x, ParticleTypes.CRIT, base.add(x.fwd.scale(2.4)).add(0, 1.0, 0), 8, 1.1, 0.25);
            SkillFx.sound(x, SoundEvents.STONE_BREAK, base.add(x.fwd.scale(2.4)), 0.7f, 0.7f);
        });
        // 残り: 扇から粉が立ちのぼる
        c.at(8, x -> {
            fan(x, BONE_S, base, x.fwd, 0.8, CLEAVE_RANGE, CLEAVE_HALF, 10, 0.2, 0.0);
            fan(x, SkillFx.rune(), base, x.fwd, 0.8, CLEAVE_RANGE, CLEAVE_HALF, 4, 0.2, 0.0);
        });
        c.at(14, x -> fan(x, BONE_S, base, x.fwd, 0.8, CLEAVE_RANGE, CLEAVE_HALF, 8, 0.4, 0.0));
        c.at(20, x -> fan(x, ParticleTypes.WHITE_ASH, base, x.fwd, 0.8, CLEAVE_RANGE, CLEAVE_HALF, 6, 0.5, 0.0));
        c.lasts(36);
    }

    // 岩呼び: 手を掲げて集め → 足もとに青緑の柱 → 踏みつけ (欠片・閃光) → 衝撃の輪が 2 → 4 → 6 → 8 と広がる → 縁から内へ光が寄る → 身のまわりに碑文の渦
    private static void rockCall(Cast c) {
        Vec3 base = c.caster.position();
        Vec3 up = c.hand().add(0, 0.6, 0);
        c.at(1, x -> {
            converge(x, SkillFx.rune(), up, 1.4, n(x, 10), 0.08);
            SkillFx.burst(x, BONE_S, up, 6, 0.12, 0.0);
        });
        c.at(2, x -> column(x, CYAN, base, 2.6, 10, 0.25));
        c.at(3, x -> {
            SkillFx.ring(x, ParticleTypes.CLOUD, base, 0.8, 10, 0.1, 0.15);
            spray(x, CALCITE, base, n(x, 12), 0.6, 0.15, 0.3);
            SkillFx.flash(x, base.add(0, 0.3, 0));
            SkillFx.ring(x, BONE, base, 2.0, 14, 0.15, 0.0);
            SkillFx.kick(x, 0.9f);
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, base, 0.5f, 0.5f);
        });
        c.at(4, x -> {
            SkillFx.ring(x, BONE, base, ROCK_CALL_RADIUS * 0.5, n(x, 22), 0.15, 0.0);
            SkillFx.ring(x, CYAN, base, ROCK_CALL_RADIUS * 0.45, 10, 0.25, 0.0);
        });
        c.at(5, x -> SkillFx.ring(x, BONE, base, ROCK_CALL_RADIUS * 0.75, n(x, 28), 0.15, 0.0));
        c.at(6, x -> {
            SkillFx.ring(x, CYAN, base, ROCK_CALL_RADIUS, n(x, 36), 0.2, 0.0);
            if (x.chain) {
                SkillFx.ring(x, GOLD, base, ROCK_CALL_RADIUS - 0.3, 16, 0.4, 0.0);
            }
            SkillFx.sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, base, 0.8f, 0.6f);
        });
        // 引く: 縁から自分のほうへ光が寄る
        c.at(10, x -> SkillFx.ring(x, ParticleTypes.END_ROD, base, ROCK_CALL_RADIUS - 0.5, 10, 0.6, -0.25));
        // 残り: 身のまわりの碑文の渦 (今の位置について回る)
        for (int k = 0; k < 3; k++) {
            int t = 14 + k * 10;
            double phase = k * 2.1;
            c.at(t, x -> spiral(x, SkillFx.rune(), x.feet(), 0.9, 0.2, 2.0, 1.0, phase, 6, 0.0));
        }
        c.lasts(44);
    }

    // 鯨尾薙ぎ: 腰に集め → 身をひねって 4 tick で一回り (半径 2.8 の泡の弧・薙ぎの絵) → 半径 3.5 の水しぶきの輪 → 骨色の輪の跡 → 霧が立つ
    private static void whaleSweep(Cast c) {
        c.at(1, x -> {
            converge(x, SkillFx.rune(), x.feet().add(0, 0.9, 0), 1.2, 8, 0.08);
            SkillFx.burst(x, FOAM, x.hand(), 6, 0.12, 0.0);
        });
        // 後ろ → 右 → 前 → 左 → 後ろ (arc の角度は 0 = 前、+90 = 右)
        double[][] quarters = { { 180, 90 }, { 90, 0 }, { 0, -90 }, { -90, -180 } };
        for (int q = 0; q < 4; q++) {
            double from = quarters[q][0];
            double to = quarters[q][1];
            int tick = 2 + q;
            c.at(tick, x -> {
                Vec3 f = x.feet();
                arc(x, FOAM, f, x.fwd, SWEEP_RADIUS - 0.7, from, to, n(x, 10), 1.0, 0.8, 0.0);
                arc(x, SEA, f, x.fwd, SWEEP_RADIUS - 0.2, from, to, 6, 0.7, 0.6, 0.0);
                SkillFx.burst(x, ParticleTypes.SWEEP_ATTACK, arcPoint(x, f, SWEEP_RADIUS - 0.9, (from + to) / 2, 1.0), 1, 0.0, 0.0);
            });
        }
        c.at(2, x -> SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, x.feet(), 0.6f, 0.7f));
        c.at(4, x -> SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, x.feet(), 0.5f, 0.9f));
        // 当たり: 外縁の水しぶき
        c.at(5, x -> {
            Vec3 f = x.feet();
            SkillFx.ring(x, ParticleTypes.SPLASH, f, SWEEP_RADIUS, n(x, 24), 0.2, 0.1);
            SkillFx.ring(x, ParticleTypes.CLOUD, f, SWEEP_RADIUS - 0.5, 12, 0.3, 0.12);
            SkillFx.flash(x, f.add(0, 1.0, 0));
            if (x.chain) {
                SkillFx.ring(x, GOLD, f, SWEEP_RADIUS - 1.0, 12, 0.9, 0.0);
            }
            SkillFx.kick(x, 0.8f);
            SkillFx.sound(x, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, f, 0.4f, 0.8f);
        });
        c.at(6, x -> SkillFx.ring(x, BONE, x.feet(), SWEEP_RADIUS, 20, 0.1, 0.0));
        for (int k = 0; k < 3; k++) {
            c.at(10 + k * 6, x -> {
                disc(x, FOAM, x.feet(), SWEEP_RADIUS, 6, 0.2, 0.0);
                disc(x, ParticleTypes.SPLASH, x.feet(), SWEEP_RADIUS, 2, 0.2, 0.0);
            });
        }
        c.lasts(34);
    }

    // ================================================================ 風読み

    // 踏み込み: 蹴り出しの GUST と土煙 → 元の場所から着いた所まで白い筋 (END_ROD の芯) → 着いた先で閃光・薙ぎの絵・CRIT・足もとの淡い緑の輪 → 筋に沿って空の粒が残る
    private static void lunge(Cast c) {
        Vec3 start = c.caster.position();
        Vec3[] end = { start };
        c.at(1, x -> {
            SkillFx.burst(x, ParticleTypes.GUST, start.add(0, 0.5, 0), 1, 0.0, 0.0);
            SkillFx.ring(x, ParticleTypes.CLOUD, start, 0.6, 8, 0.1, 0.08);
            SkillFx.burst(x, WIND, x.hand(), 6, 0.1, 0.0);
        });
        c.at(2, x -> {
            // 自分の画面ではテレポートが合図より先に届くことがある: そのときは向きの逆へ尾を引く
            Vec3 now = x.caster.position();
            Vec3 from = start;
            if (now.distanceToSqr(start) < 1.0) {
                from = now.subtract(x.fwd.scale(3.5));
            }
            end[0] = now;
            double len = from.distanceTo(now);
            Vec3 a = from.add(0, 0.9, 0);
            Vec3 b = now.add(0, 0.9, 0);
            SkillFx.line(x, WIND, a, b, clamp((int) (len * 4), 8, 32), 0.15, 0.0);
            SkillFx.line(x, ParticleTypes.END_ROD, a, b, clamp((int) (len * 1.2), 3, 10), 0.05, 0.0);
            SkillFx.burst(x, ParticleTypes.GUST, a.lerp(b, 0.5), 1, 0.0, 0.0);
            if (x.chain) {
                SkillFx.line(x, GOLD, a.add(0, 0.25, 0), b.add(0, 0.25, 0), 10, 0.05, 0.0);
            }
            SkillFx.sound(x, SoundEvents.ENDER_DRAGON_FLAP, now, 0.35f, 1.7f);
        });
        c.at(3, x -> {
            Vec3 now = end[0];
            Vec3 d = new Vec3(now.x - start.x, 0, now.z - start.z);
            Vec3 dir = d.lengthSqr() > 0.25 ? d.normalize() : x.fwd;
            Vec3 hit = now.add(dir.scale(LUNGE_STOP * 0.85)).add(0, 1.1, 0);
            SkillFx.flash(x, hit);
            SkillFx.burst(x, ParticleTypes.SWEEP_ATTACK, hit, 2, 0.2, 0.0);
            SkillFx.burst(x, ParticleTypes.CRIT, hit, n(x, 10), 0.3, 0.3);
            SkillFx.ring(x, WIND_GREEN, now, 1.2, n(x, 12), 0.1, 0.0);
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_STRONG, hit, 0.5f, 1.3f);
        });
        c.at(7, x -> SkillFx.line(x, SkillFx.sky(), start.add(0, 0.9, 0), end[0].add(0, 0.9, 0), 6, 0.3, 0.01));
        c.at(13, x -> SkillFx.line(x, WIND_GREEN, start.add(0, 0.7, 0), end[0].add(0, 0.7, 0), 6, 0.3, 0.0));
        c.lasts(26);
    }

    // 跳び退き: 元の場所に GUST と輪・白い残像の柱 → 跳んでいるあいだ足もとに淡い筋が続く → 着地の小さな雲の輪 → 元の場所に風の粒が残る
    private static void backstep(Cast c) {
        Vec3 start = c.caster.position();
        c.at(1, x -> {
            SkillFx.burst(x, ParticleTypes.GUST, start.add(x.fwd.scale(0.6)).add(0, 0.6, 0), 1, 0.0, 0.0);
            SkillFx.ring(x, ParticleTypes.POOF, start, 0.8, 8, 0.05, 0.06);
            SkillFx.burst(x, WIND_GREEN, x.hand(), 5, 0.1, 0.0);
            SkillFx.sound(x, SoundEvents.ENDER_DRAGON_FLAP, start, 0.3f, 1.8f);
        });
        // 残像: 元の場所に立ったままの白い影
        c.at(2, x -> {
            column(x, WIND, start, 1.8, 12, 0.22);
            if (x.chain) {
                column(x, GOLD, start, 1.8, 6, 0.3);
            }
        });
        for (int t = 2; t <= 8; t++) {
            c.at(t, x -> {
                Vec3 f = x.feet().add(0, 0.6, 0);
                SkillFx.burst(x, WIND, f, 3, 0.2, 0.0);
                SkillFx.burst(x, SkillFx.sky(), f, 1, 0.2, 0.01);
            });
        }
        c.at(10, x -> SkillFx.ring(x, ParticleTypes.CLOUD, x.feet(), 0.9, n(x, 10), 0.05, 0.08));
        c.at(12, x -> disc(x, SkillFx.sky(), start, 1.0, 6, 0.4, 0.0));
        c.at(20, x -> disc(x, WIND_GREEN, start, 1.0, 5, 0.5, 0.0));
        c.lasts(32);
    }

    // 風裂き: 手から相手へ空の粒 → 右上から左下の白い斜め斬り (薙ぎの絵・CRIT・GUST) → 4 tick 後に逆の斜め (X の形)、閃光 → 淡い緑の風が漂う
    private static void windRend(Cast c) {
        LivingEntity foe = nearestFoe(c, REND_RANGE, 45);
        Vec3 fallback = c.aim(2.4);
        Vec3 r = c.right;
        Vec3 upv = new Vec3(0, 1, 0);
        for (int s = 0; s < 2; s++) {
            int tick = 1 + s * REND_GAP;
            boolean second = s == 1;
            c.at(tick, x -> {
                Vec3 m = foe != null && foe.isAlive() ? foe.position().add(0, foe.getBbHeight() * 0.6, 0) : fallback;
                double side = second ? -1 : 1;
                Vec3 a = m.add(r.scale(0.9 * side)).add(upv.scale(0.8));
                Vec3 b = m.add(r.scale(-0.9 * side)).add(upv.scale(-0.6));
                SkillFx.line(x, SkillFx.sky(), x.hand(), m, 6, 0.05, 0.0);
                SkillFx.line(x, WIND, a, b, n(x, 14), 0.04, 0.0);
                SkillFx.line(x, WIND_GREEN, a.add(0, 0.12, 0), b.add(0, 0.12, 0), 6, 0.06, 0.0);
                SkillFx.burst(x, ParticleTypes.SWEEP_ATTACK, m, 1, 0.0, 0.0);
                SkillFx.burst(x, ParticleTypes.CRIT, m, n(x, 6), 0.25, 0.3);
                SkillFx.burst(x, ParticleTypes.GUST, m, 1, 0.0, 0.0);
                if (second) {
                    SkillFx.flash(x, m);
                    if (x.chain) {
                        SkillFx.burst(x, GOLD, m, 10, 0.4, 0.0);
                    }
                }
                SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, m, 0.5f, second ? 1.8f : 1.6f);
            });
        }
        for (int k = 0; k < 2; k++) {
            c.at(10 + k * 6, x -> {
                Vec3 m = foe != null && foe.isAlive() ? foe.position().add(0, foe.getBbHeight() * 0.5, 0) : fallback;
                SkillFx.burst(x, WIND_GREEN, m, 5, 0.5, 0.0);
                SkillFx.burst(x, SkillFx.sky(), m, 3, 0.5, 0.01);
            });
        }
        c.lasts(28);
    }

    // ================================================================ 灰の鍛え人

    // 焼き入れ: 炎が手元へ寄る → 手で火花が弾ける (金床の音)・仲間へは炎の筋 → 受けた人のまわりに炎の螺旋が昇る → 手の武器から熾が零れる (2 秒)
    private static void temper(Cast c) {
        Player ally = lookedAlly(c);
        Player target = ally != null ? ally : c.caster;
        c.at(1, x -> converge(x, ParticleTypes.FLAME, x.hand(), 1.0, n(x, 8), 0.1));
        c.at(2, x -> {
            converge(x, ParticleTypes.FLAME, x.hand(), 0.8, 6, 0.1);
            SkillFx.burst(x, HEAT, x.hand(), 5, 0.08, 0.0);
        });
        c.at(3, x -> {
            Vec3 h = x.hand();
            SkillFx.burst(x, CINDER, h, n(x, 10), 0.15, 0.0);
            SkillFx.burst(x, ParticleTypes.LAVA, h, 2, 0.05, 0.0);
            SkillFx.burst(x, ParticleTypes.CRIT, h, 6, 0.1, 0.25);
            SkillFx.flash(x, h);
            if (target != x.caster) {
                SkillFx.line(x, ParticleTypes.FLAME, h, target.getEyePosition().add(0, -0.5, 0), 12, 0.05, 0.0);
            }
            SkillFx.sound(x, SoundEvents.ANVIL_USE, h, 0.35f, 1.4f);
        });
        // 受けた人のまわりの炎の螺旋 (3 段で昇る)
        for (int k = 0; k < 3; k++) {
            int t = 4 + k * 2;
            double y0 = k * 0.7;
            double phase = k * 2.2;
            c.at(t, x -> {
                Vec3 f = target.position();
                spiral(x, ParticleTypes.FLAME, f, 0.7, y0, y0 + 0.7, 0.7, phase, n(x, 8), 0.02);
                spiral(x, HEAT, f, 0.75, y0, y0 + 0.7, 0.7, phase + 0.4, 5, 0.0);
            });
        }
        c.at(4, x -> SkillFx.sound(x, SoundEvents.FIRECHARGE_USE, target.position(), 0.4f, 1.3f));
        c.at(9, x -> {
            Vec3 top = target.position().add(0, 2.1, 0);
            SkillFx.burst(x, CINDER, top, 8, 0.3, 0.0);
            if (x.chain) {
                SkillFx.burst(x, GOLD, top, 8, 0.3, 0.0);
            }
        });
        // 残り: 武器から熾が零れる
        for (int t = 12; t <= 40; t += 7) {
            c.at(t, x -> {
                if (!target.isAlive()) {
                    return;
                }
                Vec3 h = handOf(target);
                SkillFx.burst(x, ParticleTypes.FLAME, h, 2, 0.08, 0.01);
                SkillFx.burst(x, CINDER, h, 2, 0.1, 0.0);
                SkillFx.burst(x, ParticleTypes.SMOKE, h, 1, 0.05, 0.01);
            });
        }
        c.lasts(46);
    }

    // 熾火の槌: 振りかぶった槌に熾が集まる → 前 2 の点へ打ち下ろす筋 → 閃光・欠片・溶岩・熾の柱 → 半径 1.5 → 3.5 の熾から灰へ変わる輪 → 2 秒、熾が立ちのぼる
    private static void emberHammer(Cast c) {
        Vec3 center = c.caster.position().add(c.fwd.scale(HAMMER_REACH));
        Vec3 up = c.hand().add(0, 0.7, 0);
        c.at(1, x -> {
            converge(x, ParticleTypes.FLAME, up, 1.2, n(x, 8), 0.1);
            SkillFx.burst(x, ParticleTypes.SMOKE, up, 3, 0.15, 0.01);
        });
        c.at(2, x -> {
            converge(x, ParticleTypes.FLAME, up, 0.9, 6, 0.1);
            SkillFx.burst(x, HEAT, up, 6, 0.1, 0.0);
        });
        c.at(3, x -> SkillFx.burst(x, CINDER, up, 6, 0.12, 0.0));
        // 放ち: 打ち下ろす筋
        c.at(4, x -> {
            SkillFx.line(x, CINDER, up, center.add(0, 0.3, 0), 10, 0.06, 0.0);
            SkillFx.line(x, ParticleTypes.FLAME, up, center.add(0, 0.3, 0), 6, 0.05, 0.0);
        });
        // 当たり
        c.at(5, x -> {
            SkillFx.flash(x, center.add(0, 0.3, 0));
            spray(x, MAGMA, center, n(x, 8), 0.6, 0.12, 0.32);
            spray(x, BASALT, center, 6, 0.6, 0.12, 0.28);
            SkillFx.burst(x, ParticleTypes.LAVA, center.add(0, 0.3, 0), 6, 0.4, 0.0);
            column(x, ParticleTypes.FLAME, center, 2.5, 10, 0.15);
            SkillFx.ring(x, EMBER_TO_ASH, center, HAMMER_RADIUS * 0.43, 12, 0.15, 0.0);
            if (x.chain) {
                SkillFx.ring(x, GOLD, center, HAMMER_RADIUS * 0.7, 14, 0.2, 0.0);
            }
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, center, 0.5f, 0.7f);
            SkillFx.sound(x, SoundEvents.FIRECHARGE_USE, center, 0.4f, 0.7f);
        });
        c.at(6, x -> {
            SkillFx.ring(x, EMBER_TO_ASH, center, HAMMER_RADIUS, n(x, 26), 0.1, 0.0);
            SkillFx.ring(x, ParticleTypes.LARGE_SMOKE, center, HAMMER_RADIUS * 0.8, 8, 0.2, 0.03);
            column(x, HEAT, center, 2.8, 8, 0.2);
        });
        c.at(7, x -> column(x, CINDER, center, 3.2, 8, 0.3));
        // 残り: 熾の床から熾が立ちのぼる (熾火の続く 4 秒のうち 2 秒)
        for (int t = 10; t <= 40; t += 6) {
            c.at(t, x -> {
                disc(x, ParticleTypes.FLAME, center, HAMMER_RADIUS, 3, 0.1, 0.03);
                disc(x, CINDER, center, HAMMER_RADIUS, 3, 0.3, 0.0);
                disc(x, ParticleTypes.SMOKE, center, HAMMER_RADIUS, 2, 0.2, 0.02);
            });
        }
        c.lasts(46);
    }

    // 灰散らし: 炎が身へ吸い込まれ灰が渦巻く → 閃光と共に灰が爆ぜて 2 → 3.5 → 5 と輪が広がる (大きな煙の縁) → 半径 5 の範囲に灰が降る (2 秒)
    private static void ashScatter(Cast c) {
        Vec3 base = c.caster.position();
        c.at(1, x -> {
            converge(x, ParticleTypes.FLAME, x.feet().add(0, 1.0, 0), 2.0, n(x, 10), 0.1);
            spiral(x, ASH_GREY, x.feet(), 1.6, 0.2, 1.6, 1.0, 0, 8, 0.0);
        });
        c.at(2, x -> spiral(x, ASH_GREY, x.feet(), 1.0, 0.2, 1.8, 1.0, 1.5, 8, 0.0));
        c.at(3, x -> {
            spiral(x, HEAT, x.feet(), 0.6, 0.4, 1.6, 1.0, 3.0, 6, 0.0);
            SkillFx.burst(x, CINDER, x.hand(), 5, 0.1, 0.0);
        });
        // 放ち
        c.at(4, x -> {
            Vec3 chest = x.feet().add(0, 1.0, 0);
            SkillFx.flash(x, chest);
            SkillFx.burst(x, ParticleTypes.LAVA, chest, 4, 0.3, 0.0);
            SkillFx.burst(x, ParticleTypes.ASH, chest, 10, 0.6, 0.05);
            SkillFx.ring(x, EMBER_TO_ASH, base, 2.0, n(x, 16), 0.5, 0.0);
            SkillFx.kick(x, 0.7f);
            SkillFx.sound(x, SoundEvents.FIRE_EXTINGUISH, base, 0.5f, 0.6f);
            SkillFx.sound(x, SoundEvents.ENDER_DRAGON_FLAP, base, 0.35f, 0.6f);
        });
        c.at(5, x -> {
            SkillFx.ring(x, EMBER_TO_ASH, base, 3.5, n(x, 22), 0.4, 0.0);
            SkillFx.ring(x, ParticleTypes.WHITE_ASH, base, 3.2, 10, 0.8, 0.0);
        });
        c.at(6, x -> {
            SkillFx.ring(x, ASH_GREY, base, ASH_RADIUS, n(x, 28), 0.3, 0.0);
            SkillFx.ring(x, ParticleTypes.LARGE_SMOKE, base, ASH_RADIUS - 0.4, 12, 0.4, 0.03);
            if (x.chain) {
                SkillFx.ring(x, HEAT, base, ASH_RADIUS - 0.2, 14, 0.5, 0.0);
            }
        });
        // 残り: 灰が降る
        for (int t = 10; t <= 40; t += 6) {
            c.at(t, x -> {
                disc(x, ParticleTypes.WHITE_ASH, base, ASH_RADIUS, 5, 2.4, 0.0);
                disc(x, ParticleTypes.ASH, base, ASH_RADIUS, 4, 2.0, 0.0);
            });
        }
        c.lasts(46);
    }

    // ================================================================ 形の道具 (粒はみな SkillFx.spend を通す)

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(rgb(rgb), size);
    }

    private static Vector3f rgb(int rgb) {
        return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    // 段で少し増やす (1 段ごとに +10%)
    private static int n(Cast c, int base) {
        int r = Math.max(0, Math.min(3, c.rank - 1));
        return (int) Math.round(base * (1 + 0.1 * r));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double sym(RandomSource r) {
        return r.nextDouble() * 2 - 1;
    }

    // 水平の扇の上の一点 (deg: 0 = 前、+ = 右)
    private static Vec3 arcPoint(Cast c, Vec3 center, double radius, double deg, double y) {
        double a = Math.toRadians(deg);
        return center.add(c.fwd.scale(Math.cos(a) * radius)).add(c.right.scale(Math.sin(a) * radius)).add(0, y, 0);
    }

    // 弧: from 度から to 度へ、高さ y0 → y1。swirl があれば弧の進む向きへ流す
    private static void arc(Cast c, ParticleOptions p, Vec3 center, Vec3 dir, double radius, double fromDeg, double toDeg,
            int n, double y0, double y1, double swirl) {
        int k = SkillFx.spend(c, n);
        if (k <= 0) {
            return;
        }
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        double sign = Math.signum(toDeg - fromDeg);
        RandomSource r = c.random();
        for (int i = 0; i < k; i++) {
            double t = k <= 1 ? 0.5 : (double) i / (k - 1);
            double a = Math.toRadians(fromDeg + (toDeg - fromDeg) * t);
            double cs = Math.cos(a);
            double sn = Math.sin(a);
            double px = center.x + (dir.x * cs + side.x * sn) * radius + sym(r) * 0.06;
            double pz = center.z + (dir.z * cs + side.z * sn) * radius + sym(r) * 0.06;
            double py = center.y + y0 + (y1 - y0) * t + sym(r) * 0.05;
            // 接線 (角度が増える向き): d/da (dir cos + side sin) = -dir sin + side cos
            double tx = (-dir.x * sn + side.x * cs) * swirl * sign;
            double tz = (-dir.z * sn + side.z * cs) * swirl * sign;
            c.level.addParticle(p, px, py, pz, tx, 0, tz);
        }
    }

    // 扇の中の散らばった点 (rmin〜rmax、±half 度)。vy で昇る
    private static void fan(Cast c, ParticleOptions p, Vec3 center, Vec3 dir, double rmin, double rmax, double halfDeg,
            int n, double lift, double vy) {
        int k = SkillFx.spend(c, n);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        RandomSource r = c.random();
        for (int i = 0; i < k; i++) {
            double a = Math.toRadians(sym(r) * halfDeg);
            double d = rmin + (rmax - rmin) * Math.sqrt(r.nextDouble());
            double cs = Math.cos(a) * d;
            double sn = Math.sin(a) * d;
            c.level.addParticle(p, center.x + dir.x * cs + side.x * sn, center.y + lift + r.nextDouble() * 0.3,
                    center.z + dir.z * cs + side.z * sn, 0, vy, 0);
        }
    }

    // 円の中の散らばった点
    private static void disc(Cast c, ParticleOptions p, Vec3 center, double radius, int n, double lift, double vy) {
        int k = SkillFx.spend(c, n);
        RandomSource r = c.random();
        for (int i = 0; i < k; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = radius * Math.sqrt(r.nextDouble());
            c.level.addParticle(p, center.x + Math.cos(a) * d, center.y + lift + r.nextDouble() * 0.3, center.z + Math.sin(a) * d,
                    0, vy, 0);
        }
    }

    // 縦の柱 (足もとから height まで)
    private static void column(Cast c, ParticleOptions p, Vec3 base, double height, int n, double radius) {
        int k = SkillFx.spend(c, n);
        RandomSource r = c.random();
        for (int i = 0; i < k; i++) {
            double t = k <= 1 ? 0.5 : (double) i / (k - 1);
            c.level.addParticle(p, base.x + sym(r) * radius, base.y + t * height, base.z + sym(r) * radius, 0, 0.01, 0);
        }
    }

    // 昇る螺旋 (y0 → y1、turns 回り)
    private static void spiral(Cast c, ParticleOptions p, Vec3 center, double radius, double y0, double y1, double turns,
            double phase, int n, double vy) {
        int k = SkillFx.spend(c, n);
        for (int i = 0; i < k; i++) {
            double t = k <= 1 ? 0.5 : (double) i / (k - 1);
            double a = phase + t * turns * Math.PI * 2;
            c.level.addParticle(p, center.x + Math.cos(a) * radius, center.y + y0 + (y1 - y0) * t, center.z + Math.sin(a) * radius,
                    0, vy, 0);
        }
    }

    // まわりから一点へ寄ってくる (END_ROD・FLAME・rune のように速さを守る粒向け。pull は 1 tick あたりの寄せ具合)
    private static void converge(Cast c, ParticleOptions p, Vec3 at, double radius, int n, double pull) {
        int k = SkillFx.spend(c, n);
        RandomSource r = c.random();
        for (int i = 0; i < k; i++) {
            Vec3 u = new Vec3(sym(r), sym(r) * 0.7, sym(r));
            if (u.lengthSqr() < 1e-4) {
                u = new Vec3(1, 0, 0);
            }
            Vec3 pos = at.add(u.normalize().scale(radius * (0.6 + 0.4 * r.nextDouble())));
            Vec3 v = at.subtract(pos).scale(pull);
            c.level.addParticle(p, pos.x, pos.y, pos.z, v.x, v.y, v.z);
        }
    }

    // 地面から跳ねる欠片 (BLOCK の粒など。重さで落ちる)
    private static void spray(Cast c, ParticleOptions p, Vec3 at, int n, double spread, double hSpeed, double vy) {
        int k = SkillFx.spend(c, n);
        RandomSource r = c.random();
        for (int i = 0; i < k; i++) {
            c.level.addParticle(p, at.x + sym(r) * spread, at.y + 0.1 + r.nextDouble() * 0.2, at.z + sym(r) * spread,
                    sym(r) * hSpeed, vy * (0.6 + 0.4 * r.nextDouble()), sym(r) * hSpeed);
        }
    }

    // 手元で見当をつける: 見ている先 (range 以内・maxDeg 以内) の一番近い生き物 (風裂きの的。サーバーの選び方に合わせる)
    private static LivingEntity nearestFoe(Cast c, double range, double maxDeg) {
        Vec3 eye = c.origin;
        AABB box = c.caster.getBoundingBox().inflate(range + 1, 2.0, range + 1);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != c.caster && e.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand))) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d < 1e-3 || d > range + e.getBbWidth() / 2) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(c.look)))));
            if (angle > maxDeg) {
                continue;
            }
            double score = angle * 0.2 + d;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    // 焼き入れの的: 視線 6 ブロックの先にいる仲間 (いなければ null = 自分)
    private static Player lookedAlly(Cast c) {
        Vec3 eye = c.origin;
        Vec3 end = eye.add(c.look.scale(TEMPER_RANGE));
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : c.level.players()) {
            if (p == c.caster || !p.isAlive() || p.isSpectator()) {
                continue;
            }
            var hit = p.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(eye);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
        }
        return best;
    }

    // ある人の右手のあたり (今の向きで)
    private static Vec3 handOf(Player p) {
        Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 right = new Vec3(-f.z, 0, f.x);
        return p.getEyePosition().add(f.scale(0.55)).add(right.scale(0.38)).add(0, -0.42, 0);
    }
}
