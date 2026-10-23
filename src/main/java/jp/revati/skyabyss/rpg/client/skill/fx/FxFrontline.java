package jp.revati.skyabyss.rpg.client.skill.fx;

import java.util.Map;
import java.util.function.Consumer;

import org.joml.Vector3f;

import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import jp.revati.skyabyss.rpg.client.skill.SkillFx.Cast;
import jp.revati.skyabyss.rpg.skill.Skill;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 岩守り・風読み・灰の鍛え人の技の段取り (近接・機動)
// 決まり: 技ごとに形を一つだけ (弧・輪・柱・筋・X・螺旋)。形は正確な曲線の上に小さな粒を等間隔に並べ、
// 時間で分けて描く (弧は 3〜4 tick で振り抜き、輪は tick ごとに広がり、当たりは動きの後)。
// 色は技ごとに二つ (主の色と差し色)。粒の大きさは 0.6〜0.8。閃光は一つだけ。
// 粒はみな SkillFx の道具 (spend を通す・カメラの 2.4 ブロック以内は出さない) で置く。
// 半径・角度・距離は SkillRules / SkillActions と同じ数。
public final class FxFrontline {
    private FxFrontline() {
    }

    // ---------------------------------------------------------------- 技の形 (SkillRules と同じ数)
    private static final double CLEAVE_RANGE = 4.0;
    private static final double CLEAVE_HALF = 60;
    private static final double ROCK_CALL_RADIUS = 8;
    private static final double SWEEP_RADIUS = 3.5;
    private static final double LUNGE_RANGE = 8.0;
    private static final double LUNGE_STOP = 1.4;
    private static final double REND_RANGE = 4.0;
    private static final int REND_GAP = 4;
    private static final double TEMPER_RANGE = 6;
    private static final double HAMMER_RADIUS = 3.5;
    private static final double HAMMER_REACH = 2.0;
    private static final double ASH_RADIUS = 5;

    // ---------------------------------------------------------------- 見た目だけの数
    // 巨神斬りの弧 (目の高さが中心。目から 2.8 離れた所を振り抜く)
    private static final double CLEAVE_ARC_R = 2.8;
    // 自分の画面で、跳ぶ前の場所が分からないときの踏み込みの長さ (跳んだ後に合図が届いたとき)
    private static final double LUNGE_GUESS = 4.0;
    // 跳び退きの筋の長さ (押し 0.85/tick で 3 tick ぶん)
    private static final double BACKSTEP_LEN = 2.6;
    // 自分の画面で、形をカメラから離しておく距離
    private static final double NEAR = 2.6;

    // ---------------------------------------------------------------- 色 (粒の大きさは 0.6〜0.8)
    // 岩
    private static final DustParticleOptions BONE = dust(0xE8DCC0, 0.8f);
    private static final DustParticleOptions CYAN = dust(0x8CF6FF, 0.7f);
    private static final DustParticleOptions STONE = dust(0xA8B0AC, 0.8f);
    private static final DustParticleOptions TEAL = dust(0x4FD8C8, 0.8f);
    private static final DustParticleOptions SEA = dust(0x58D6E8, 0.8f);
    private static final DustParticleOptions FOAM = dust(0xF2FFFF, 0.7f);
    // 風
    private static final DustParticleOptions WIND = dust(0xE8F4FF, 0.8f);
    private static final DustParticleOptions WIND_GREEN = dust(0x9EFFD0, 0.7f);
    // 灰
    private static final DustParticleOptions HEAT = dust(0xFF8A2A, 0.8f);
    private static final DustParticleOptions CINDER = dust(0xFFC860, 0.7f);
    private static final DustParticleOptions ASH_GREY = dust(0xB0A8A0, 0.8f);

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

    // 巨神斬り: 目のまわり半径 2.8、右 60 度から左 60 度へ、上から下へ 3 tick で振り抜く骨色の弧 (内側に青緑の縁)
    //   → 足もとに半径 4 の骨色の輪 (振りの届く距離)・閃光は弧の終わりの正面
    private static void titanCleave(Cast c) {
        Vec3 eye = c.origin;
        // 3 区間: 角度 60 → 20 → -20 → -60、高さ 目+0.6 → 目-1.3
        double[] deg = { CLEAVE_HALF, CLEAVE_HALF / 3, -CLEAVE_HALF / 3, -CLEAVE_HALF };
        double[] ys = { 0.6, -0.03, -0.67, -1.3 };
        for (int s = 0; s < 3; s++) {
            int i = s;
            c.at(1 + s, x -> {
                tiltArc(x, BONE, eye, CLEAVE_ARC_R, deg[i], deg[i + 1], ys[i], ys[i + 1], 18);
                tiltArc(x, CYAN, eye, CLEAVE_ARC_R - 0.2, deg[i], deg[i + 1], ys[i] - 0.08, ys[i + 1] - 0.08, 10);
                if (x.chain) {
                    tiltArc(x, CYAN, eye, CLEAVE_ARC_R + 0.2, deg[i], deg[i + 1], ys[i] + 0.08, ys[i + 1] + 0.08, 6);
                }
            });
        }
        c.at(1, x -> SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, eye, 0.6f, 0.6f));
        // 当たり: 弧の終わり (左下) の少し先、正面の地面近くで閃く。骨色の輪が地面に
        c.at(4, x -> {
            Vec3 f = x.feet();
            SkillFx.flash(x, f.add(x.fwd.scale(CLEAVE_ARC_R)).add(0, 0.4, 0));
            SkillFx.ring(x, BONE, f, CLEAVE_RANGE, 32, 0.1, 0.0);
            SkillFx.kick(x, 1.0f);
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, f, 0.45f, 0.55f);
        });
        c.at(5, x -> {
            SkillFx.ring(x, CYAN, x.feet(), CLEAVE_RANGE + 0.3, 20, 0.12, 0.0);
            SkillFx.sound(x, SoundEvents.STONE_BREAK, x.feet(), 0.7f, 0.7f);
        });
        c.lasts(24);
    }

    // 岩呼び: 足もとから青緑の石の柱が 3 tick で昇る (2.5 ブロック・14 点) → 踏みつけ (閃光・暗い煙の輪)
    //   → 石色の地面の輪が半径 4 から 8 へ 6 tick で広がる (最後の輪は青緑)
    private static void rockCall(Cast c) {
        Vec3 base = c.caster.position().add(0, 0.05, 0);
        double seg = 2.5 / 3;
        int[] pts = { 5, 5, 4 };
        for (int s = 0; s < 3; s++) {
            int i = s;
            c.at(1 + s, x -> SkillFx.pillar(x, TEAL, base.add(0, seg * i, 0), seg, pts[i]));
        }
        c.at(4, x -> {
            SkillFx.flash(x, base.add(0, 0.3, 0));
            SkillFx.ring(x, ParticleTypes.SMOKE, base, 1.2, 12, 0.15, 0.0);
            SkillFx.kick(x, 0.9f);
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, base, 0.5f, 0.5f);
        });
        // 広がる輪: 4 → 8 (6 tick)。点の数は円周に合わせて間隔をそろえる
        for (int s = 0; s < 6; s++) {
            double r = 4.0 + (ROCK_CALL_RADIUS - 4.0) * s / 5.0;
            boolean last = s == 5;
            c.at(4 + s, x -> SkillFx.ring(x, last ? TEAL : STONE, base, r, (int) Math.round(r * 3.0), 0.12, 0.0));
        }
        // 差し色: 暗い煙の輪が二つ (広がる輪に重ねて少し上)
        c.at(6, x -> SkillFx.ring(x, ParticleTypes.SMOKE, base, 5.6, 10, 0.5, 0.0));
        c.at(8, x -> SkillFx.ring(x, ParticleTypes.SMOKE, base, 7.2, 10, 0.5, 0.0));
        c.at(9, x -> {
            if (x.chain) {
                SkillFx.ring(x, TEAL, base, ROCK_CALL_RADIUS, 14, 0.5, 0.0);
            }
            SkillFx.sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, base, 0.8f, 0.6f);
        });
        c.lasts(26);
    }

    // 鯨尾薙ぎ: 腰の高さ (足もと +1.0)、半径 3.5 の一回り。左 → 前 → 右 → 後ろ → 左と 1 tick に 90 度ずつ海の青で描く
    //   (内側に泡の白の縁) → 外縁の地面に水しぶきの輪・閃光は正面
    private static void whaleSweep(Cast c) {
        for (int q = 0; q < 4; q++) {
            double from = -90 + q * 90;
            double to = from + 90;
            c.at(1 + q, x -> {
                Vec3 f = x.feet();
                SkillFx.arc(x, SEA, f, SWEEP_RADIUS, rad(x, from), rad(x, to), 1.0, 22);
                SkillFx.arc(x, FOAM, f, SWEEP_RADIUS - 0.3, rad(x, from), rad(x, to), 0.92, x.chain ? 12 : 8);
            });
        }
        c.at(1, x -> SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, x.feet(), 0.6f, 0.7f));
        c.at(3, x -> SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, x.feet(), 0.5f, 0.9f));
        c.at(5, x -> {
            Vec3 f = x.feet();
            SkillFx.flash(x, f.add(x.fwd.scale(SWEEP_RADIUS)).add(0, 1.0, 0));
            SkillFx.ring(x, ParticleTypes.SPLASH, f, SWEEP_RADIUS, 20, 0.2, 0.0);
            SkillFx.kick(x, 0.8f);
            SkillFx.sound(x, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, f, 0.4f, 0.8f);
        });
        c.lasts(22);
    }

    // ================================================================ 風読み

    // 踏み込み: 元の場所に淡い緑の輪 → 元の場所から着いた所まで白い筋 (32 点) が 4 tick で前へ伸びる
    //   → 着いた所の地面に白い輪が広がり、相手の先で閃光 → 筋に沿って淡い緑の跡
    private static void lunge(Cast c) {
        Vec3 cast = c.caster.position();
        Vec3[] path = { cast, cast };
        c.at(1, x -> {
            Vec3 now = x.caster.position();
            Vec3 from;
            Vec3 to;
            if (x.self) {
                // 自分の画面: 合図の後にテレポートが届いたなら今が着いた所。先に届いていたら元の場所は向きの逆から見積もる
                to = now;
                from = horiz(now, cast) > 1.0 ? cast : now.subtract(x.fwd.scale(LUNGE_GUESS));
            } else {
                // 他の人: 位置は数 tick かけて動くので、サーバーと同じ選び方で相手を見つけて着く所を決める
                from = cast;
                to = lungeEnd(x, cast);
            }
            if (horiz(from, to) < 1.0) {
                from = to.subtract(x.fwd.scale(2.5));
            }
            path[0] = from;
            path[1] = to;
            SkillFx.ring(x, WIND_GREEN, from, 1.0, 16, 0.1, 0.0);
            streak(x, WIND, path[0], path[1], 0, 4, 8, 0.9);
            SkillFx.sound(x, SoundEvents.ENDER_DRAGON_FLAP, from, 0.35f, 1.7f);
        });
        for (int s = 1; s < 4; s++) {
            int i = s;
            c.at(1 + s, x -> streak(x, WIND, path[0], path[1], i, 4, 8, 0.9));
        }
        c.at(5, x -> {
            Vec3 end = path[1];
            Vec3 dir = flatDir(path[0], end, x.fwd);
            // 着いた所の輪 (自分の画面では足もとの真下は見えないので半径 2.0 から)
            SkillFx.ring(x, WIND, end.add(0, 0.05, 0), 2.0, 20, 0.1, 0.0);
            double reach = x.self ? NEAR : LUNGE_STOP;
            SkillFx.flash(x, end.add(dir.scale(reach)).add(0, 1.0, 0));
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_STRONG, end, 0.5f, 1.3f);
        });
        c.at(6, x -> SkillFx.ring(x, WIND_GREEN, path[1].add(0, 0.05, 0), 2.6, 22, 0.1, 0.0));
        c.at(8, x -> {
            SkillFx.line(x, WIND_GREEN, path[0].add(0, 0.6, 0), path[1].add(0, 0.6, 0), 16, 0.0, 0.0);
            if (x.chain) {
                SkillFx.line(x, WIND, path[0].add(0, 1.2, 0), path[1].add(0, 1.2, 0), 12, 0.0, 0.0);
            }
        });
        c.lasts(22);
    }

    // 跳び退き: 足もとから後ろへ白い筋 (16 点) が 3 tick で伸びる・元の場所の淡い緑の輪が 1.6 → 0.6 へ縮む
    //   → 着地した所で小さな土煙
    private static void backstep(Cast c) {
        Vec3 start = c.caster.position().add(0, 0.05, 0);
        Vec3 end = start.subtract(c.fwd.scale(BACKSTEP_LEN));
        int[] pts = { 6, 5, 5 };
        for (int s = 0; s < 3; s++) {
            int i = s;
            c.at(1 + s, x -> streak(x, WIND, start, end, i, 3, pts[i], 0.25));
        }
        c.at(1, x -> SkillFx.sound(x, SoundEvents.ENDER_DRAGON_FLAP, start, 0.3f, 1.8f));
        // 縮む輪 (跳んで離れてから見えるよう 2 tick 目から)
        double[] radii = { 1.6, 1.1, 0.6 };
        int[] ringPts = { 18, 14, 10 };
        for (int s = 0; s < 3; s++) {
            int i = s;
            c.at(2 + s, x -> SkillFx.ring(x, WIND_GREEN, start, radii[i], ringPts[i], 0.1, 0.0));
        }
        c.at(5, x -> {
            SkillFx.line(x, WIND_GREEN, start.add(0, 0.45, 0), end.add(0, 0.45, 0), 10, 0.0, 0.0);
            if (x.chain) {
                SkillFx.line(x, WIND, start.add(0, 0.65, 0), end.add(0, 0.65, 0), 8, 0.0, 0.0);
            }
        });
        // 着地 (押し 0.85・持ち上げ 0.42 でおよそ 9 tick 後)
        c.at(9, x -> {
            Vec3 f = x.feet();
            SkillFx.burst(x, ParticleTypes.POOF, f.add(0, 0.1, 0), 6, 0.3, 0.02);
            SkillFx.ring(x, WIND, f, 0.9, 12, 0.08, 0.0);
        });
        c.lasts(20);
    }

    // 風裂き: 正面に X。一太刀目は左上 → 右下 (30 点を 2 tick)、4 tick 後に二太刀目は右上 → 左下。
    //   白が主・淡い緑が差し色。閃光は二太刀目が交わった所
    private static void windRend(Cast c) {
        LivingEntity foe = nearestFoe(c, REND_RANGE, 45);
        for (int s = 0; s < 2; s++) {
            boolean second = s == 1;
            int t0 = 1 + s * REND_GAP;
            for (int h = 0; h < 2; h++) {
                double from = h * 0.5;
                double to = from + 0.5;
                boolean done = h == 1;
                c.at(t0 + h, x -> {
                    Vec3 m = rendCenter(x, foe);
                    Vec3 d = flatDir(x.origin, m, x.fwd);
                    Vec3 r = new Vec3(-d.z, 0, d.x);
                    double side = second ? 1 : -1;
                    Vec3 a = m.add(r.scale(side)).add(0, 1.0, 0);
                    Vec3 b = m.add(r.scale(-side)).add(0, -1.0, 0);
                    Vec3 bow = d.scale(0.3);
                    slash(x, WIND, a, b, bow, from, to, 15);
                    slash(x, WIND_GREEN, a.add(0, -0.12, 0), b.add(0, -0.12, 0), bow, from, to, x.chain ? 10 : 6);
                    if (done && second) {
                        SkillFx.flash(x, m);
                    }
                    if (!done) {
                        SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, m, 0.5f, second ? 1.8f : 1.6f);
                    }
                });
            }
        }
        c.at(1 + REND_GAP + 4, x -> SkillFx.burst(x, WIND_GREEN, rendCenter(x, foe), 6, 0.3, 0.0));
        c.lasts(20);
    }

    // ================================================================ 灰の鍛え人

    // 焼き入れ: 受ける人のまわりに熾の橙の螺旋 (3 回り・高さ 2) が 6 tick で昇る (黄の差し色の螺旋が逆側に添う)
    //   → 頂で閃光 → 火の粒がこぼれ落ちる。仲間がいなければ自分のまわり (半径 2.6: 自分の画面でも前を回るのが見える)
    private static void temper(Cast c) {
        Player ally = lookedAlly(c);
        Player target = ally != null ? ally : c.caster;
        boolean self = ally == null;
        double radius = self ? NEAR : 0.8;
        int main = self ? 16 : 10;
        int accent = self ? 6 : 4;
        if (!self) {
            c.at(1, x -> SkillFx.line(x, HEAT, x.hand(), target.position().add(0, 1.0, 0), 12, 0.0, 0.0));
        }
        c.at(1, x -> SkillFx.sound(x, SoundEvents.ANVIL_USE, x.hand(), 0.35f, 1.4f));
        // 6 区間 (半回りずつ)
        double phase0 = Math.atan2(c.fwd.z, c.fwd.x) - Math.PI / 2;
        for (int s = 0; s < 6; s++) {
            double t0 = s / 6.0;
            double t1 = (s + 1) / 6.0;
            c.at(2 + s, x -> {
                Vec3 f = target.position();
                helix(x, HEAT, f, radius, 2.0, 3, phase0, t0, t1, main);
                helix(x, CINDER, f, radius, 2.0, 3, phase0 + Math.PI, t0, t1, accent);
            });
        }
        c.at(2, x -> SkillFx.sound(x, SoundEvents.FIRECHARGE_USE, target.position(), 0.4f, 1.3f));
        c.at(8, x -> {
            Vec3 top = target.position().add(0, 2.0, 0);
            if (self) {
                // 自分の頭の上はカメラに近すぎるので、螺旋の正面の頂で閃く
                top = top.add(x.fwd.scale(radius));
            }
            SkillFx.flash(x, top);
        });
        // 火の粒が落ちる: 螺旋の頂の円に等間隔
        for (int k = 0; k < 2; k++) {
            int t = 9 + k * 3;
            double ph = k * Math.PI / 4;
            c.at(t, x -> {
                Vec3 f = target.position();
                int n = SkillFx.spend(x, 4);
                for (int i = 0; i < n; i++) {
                    double a = ph + Math.PI * 2 * i / Math.max(1, n);
                    SkillFx.put(x, ParticleTypes.FALLING_LAVA, f.x + Math.cos(a) * radius, f.y + 2.0, f.z + Math.sin(a) * radius, 0, 0, 0);
                }
            });
        }
        c.lasts(28);
    }

    // 熾火の槌: 前 2 の点へ真上 3.5 から熾の筋が 2 tick で打ち下ろす (16 点) → 閃光 → 熾の輪が半径 1 から 3.5 へ 5 tick で広がる
    //   → 半径 3.5 の地面に熾の床がまばらに 20 tick 残る
    private static void emberHammer(Cast c) {
        Vec3 center = c.caster.position().add(c.fwd.scale(HAMMER_REACH)).add(0, 0.05, 0);
        // 筋だけは、自分の画面でカメラに近すぎないよう少し先 (2.6) に立てる。当たりと輪は本当の点
        Vec3 strike = c.self ? c.caster.position().add(c.fwd.scale(NEAR)).add(0, 0.05, 0) : center;
        c.at(1, x -> SkillFx.pillar(x, CINDER, strike.add(0, 1.75, 0), 1.75, 8));
        c.at(2, x -> SkillFx.pillar(x, HEAT, strike, 1.75, 8));
        c.at(3, x -> {
            SkillFx.flash(x, center.add(0, 0.2, 0));
            SkillFx.kick(x, 1.0f);
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, center, 0.5f, 0.7f);
            SkillFx.sound(x, SoundEvents.FIRECHARGE_USE, center, 0.4f, 0.7f);
        });
        for (int s = 0; s < 5; s++) {
            double r = 1.0 + (HAMMER_RADIUS - 1.0) * s / 4.0;
            c.at(3 + s, x -> SkillFx.ring(x, HEAT, center, r, (int) Math.round(r * 5), 0.1, 0.0));
        }
        c.at(7, x -> SkillFx.ring(x, CINDER, center, HAMMER_RADIUS - 0.2, x.chain ? 22 : 14, 0.15, 0.0));
        // 熾の床
        for (int k = 0; k < 6; k++) {
            int t = 8 + k * 4;
            c.at(t, x -> floor(x, (x.random().nextBoolean() ? HEAT : CINDER), center, HAMMER_RADIUS, 5, 0.08));
        }
        c.lasts(32);
    }

    // 灰散らし: 灰色の地面の輪が半径 1 から 5 へ 6 tick で広がる (内側に熾の縁が追う) → 白い灰が半径 5 の中に 20 tick かけて降る
    private static void ashScatter(Cast c) {
        Vec3 base = c.caster.position().add(0, 0.05, 0);
        c.at(1, x -> {
            SkillFx.flash(x, base.add(0, 1.0, 0));
            SkillFx.kick(x, 0.7f);
            SkillFx.sound(x, SoundEvents.FIRE_EXTINGUISH, base, 0.5f, 0.6f);
            SkillFx.sound(x, SoundEvents.ENDER_DRAGON_FLAP, base, 0.35f, 0.6f);
        });
        for (int s = 0; s < 6; s++) {
            double r = 1.0 + (ASH_RADIUS - 1.0) * s / 5.0;
            c.at(2 + s, x -> {
                SkillFx.ring(x, ASH_GREY, base, r, (int) Math.round(r * 4 + 2), 0.12, 0.0);
                if (r > 1.4) {
                    SkillFx.ring(x, HEAT, base, r - 0.35, (int) Math.round(r * (x.chain ? 3 : 2)), 0.1, 0.0);
                }
            });
        }
        // 降る灰: 半径 5 の円の中、高さ 2.5〜3 から
        for (int k = 0; k < 6; k++) {
            int t = 9 + k * 4;
            c.at(t, x -> {
                int n = SkillFx.spend(x, 6);
                RandomSource r = x.random();
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double d = ASH_RADIUS * Math.sqrt(r.nextDouble());
                    SkillFx.put(x, ParticleTypes.WHITE_ASH, base.x + Math.cos(a) * d, base.y + 2.5 + r.nextDouble() * 0.5,
                            base.z + Math.sin(a) * d, 0, -0.02, 0);
                }
            });
        }
        c.lasts(34);
    }

    // ================================================================ 形の道具 (粒はみな SkillFx.spend と SkillFx.put を通す)

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(rgb(rgb), size);
    }

    private static Vector3f rgb(int rgb) {
        return new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    // 発動の向きから deg 度 (+ = 右) の向きを、SkillFx.arc の角度 (ラジアン、x 軸から z 軸へ) にする
    private static double rad(Cast c, double deg) {
        return Math.atan2(c.fwd.z, c.fwd.x) + Math.toRadians(deg);
    }

    private static double horiz(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vec3 flatDir(Vec3 from, Vec3 to, Vec3 fallback) {
        Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
        return d.lengthSqr() < 1e-4 ? fallback : d.normalize();
    }

    // 傾いた弧: 中心のまわり半径 r、fromDeg → toDeg (発動の向きが 0、+ = 右)、高さ y0 → y1。等間隔
    private static void tiltArc(Cast c, ParticleOptions p, Vec3 center, double r, double fromDeg, double toDeg,
            double y0, double y1, int n) {
        int k = SkillFx.spend(c, n);
        for (int i = 0; i < k; i++) {
            double t = k <= 1 ? 0 : (double) i / (k - 1);
            double a = rad(c, fromDeg + (toDeg - fromDeg) * t);
            SkillFx.put(c, p, center.x + Math.cos(a) * r, center.y + y0 + (y1 - y0) * t, center.z + Math.sin(a) * r, 0, 0, 0);
        }
    }

    // 筋の一区間: a → b を slices に分けた i 番目を、足もとから高さ y で並べる (前へ伸びる筋)
    private static void streak(Cast c, ParticleOptions p, Vec3 a, Vec3 b, int i, int slices, int n, double y) {
        Vec3 s = a.lerp(b, (double) i / slices).add(0, y, 0);
        Vec3 e = a.lerp(b, (double) (i + 1) / slices).add(0, y, 0);
        SkillFx.line(c, p, s, e, n, 0.0, 0.0);
    }

    // 斬りの線: a → b の t0〜t1 の区間。bow の向きに真ん中が膨らむ (sin)
    private static void slash(Cast c, ParticleOptions p, Vec3 a, Vec3 b, Vec3 bow, double t0, double t1, int n) {
        int k = SkillFx.spend(c, n);
        for (int i = 0; i < k; i++) {
            double t = t0 + (t1 - t0) * (k <= 1 ? 0 : (double) i / (k - 1));
            Vec3 q = a.lerp(b, t).add(bow.scale(Math.sin(Math.PI * t)));
            SkillFx.put(c, p, q.x, q.y, q.z, 0, 0, 0);
        }
    }

    // 昇る螺旋の t0〜t1 の区間 (全体で turns 回り、高さ h)
    private static void helix(Cast c, ParticleOptions p, Vec3 base, double r, double h, int turns, double phase,
            double t0, double t1, int n) {
        int k = SkillFx.spend(c, n);
        for (int i = 0; i < k; i++) {
            double t = t0 + (t1 - t0) * (k <= 1 ? 0 : (double) i / (k - 1));
            double a = phase + t * turns * Math.PI * 2;
            SkillFx.put(c, p, base.x + Math.cos(a) * r, base.y + 0.1 + h * t, base.z + Math.sin(a) * r, 0, 0, 0);
        }
    }

    // 地面の円の中にまばらな点 (熾の床)
    private static void floor(Cast c, ParticleOptions p, Vec3 center, double radius, int n, double lift) {
        int k = SkillFx.spend(c, n);
        RandomSource r = c.random();
        for (int i = 0; i < k; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = radius * Math.sqrt(r.nextDouble());
            SkillFx.put(c, p, center.x + Math.cos(a) * d, center.y + lift, center.z + Math.sin(a) * d, 0, 0, 0);
        }
    }

    // 風裂きの X の中心: 相手の胸 (自分の画面では目から 3 ブロックより近づけない)。相手がいなければ視線の 3 先
    private static Vec3 rendCenter(Cast c, LivingEntity foe) {
        if (foe == null || !foe.isAlive()) {
            return c.aim(3.0);
        }
        Vec3 m = foe.position().add(0, foe.getBbHeight() * 0.6, 0);
        if (c.self) {
            double d = horiz(c.origin, m);
            if (d < 3.0) {
                Vec3 dir = flatDir(c.origin, m, c.fwd);
                m = new Vec3(c.origin.x + dir.x * 3.0, m.y, c.origin.z + dir.z * 3.0);
            }
        }
        return m;
    }

    // 踏み込みの着く所 (サーバーと同じ: 視線 25 度以内の相手の手前 LUNGE_STOP)。相手が見つからなければ今の位置
    private static Vec3 lungeEnd(Cast c, Vec3 start) {
        LivingEntity foe = nearestFoe(c, LUNGE_RANGE + 1, 25);
        if (foe == null) {
            return c.caster.position();
        }
        Vec3 flat = new Vec3(foe.getX() - start.x, 0, foe.getZ() - start.z);
        double total = flat.length() - foe.getBbWidth() / 2 - LUNGE_STOP;
        if (total < 1.0 || flat.lengthSqr() < 1e-6) {
            return start;
        }
        return start.add(flat.normalize().scale(total));
    }

    // 手元で見当をつける: 見ている先 (range 以内・maxDeg 以内) の一番近い生き物 (サーバーの選び方に合わせる)
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
}
