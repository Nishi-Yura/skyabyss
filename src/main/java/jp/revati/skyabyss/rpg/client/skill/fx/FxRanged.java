package jp.revati.skyabyss.rpg.client.skill.fx;

import static jp.revati.skyabyss.rpg.client.skill.SkillFx.arc;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.burst;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.flash;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.kick;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.line;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.pillar;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.put;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.ring;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.sound;
import static jp.revati.skyabyss.rpg.client.skill.SkillFx.spend;

import java.util.Map;
import java.util.function.Consumer;

import org.joml.Vector3f;

import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillRules;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 骨の射手・淵・前世の技の段取り (遠い間合い・罠・雨・淵)
// 決まり: 技ごとに形は一つ (正確な線・弧・輪・柱・螺旋に、小さな粒を等間隔に並べる)。色は主の色と差し色の二つ。
// 形は時間で動かす (矢は飛び、輪は縮む・広がる)。当たりは動きの後。閃光は当たりの一点に一つだけ。
// 半径・距離・tick はサーバーと同じ数 (SkillRules と SkillActions / ExtActions の段取り) を使う。
public final class FxRanged {
    private FxRanged() {
    }

    // ---------------------------------------------------------------- 色
    private static final int SKY_WHITE = 0xEAF6FF;
    private static final int SKY_BLUE = 0x9FD8FF;
    private static final int WHITE = 0xFFFFFF;
    private static final int BONE = 0xE8DCC0;
    private static final int TEAL = 0x5FE3D0;
    private static final int DEEP_VIOLET = 0x4D147A;
    private static final int DEEP_BLACK = 0x120818;
    private static final int DEEP_CORE = 0xC88CFF;
    private static final int GOLD = 0xFFC94D;
    private static final int PALE_GOLD = 0xFFE39A;
    private static final int WARM = 0xFFF1C8;

    // 粒の大きさ (小さく揃える。1.0 を超えない)
    private static final float S_MAIN = 0.75f;
    private static final float S_FINE = 0.6f;
    private static final float S_ACCENT = 0.8f;

    // 雲穿ちの矢: 1 tick に PIERCE_SPEED ブロック進み、PIERCE_LIFETIME tick で消える (サーバーと同じ)
    private static final double ARROW_STEP = SkillRules.PIERCE_SPEED;
    private static final double PIERCE_RANGE = SkillRules.PIERCE_LIFETIME * SkillRules.PIERCE_SPEED;
    // 描くのは最初の 12 tick 分まで (それより先は遠くて粒が見えない)
    private static final int PIERCE_DRAW_TICKS = 12;
    private static final int PIERCE_POINTS = 40;
    // 手元から描き始める距離 (目から 2.5 ブロック以上離す)
    private static final double NEAR_CLEAR = 1.6;

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

    // 雲穿ち: 矢の通り道にまっすぐな一本の筋 (40 粒)。頭が 1 tick に 3.6 ブロックずつ進む。最初の当たりで閃光と 12 粒の輪
    private static void cloudPierce(SkillFx.Cast c) {
        Vec3 hand = c.hand();
        Vec3 start = hand.add(c.look.scale(NEAR_CLEAR));
        Vec3 far = c.aim(PIERCE_RANGE);
        BlockHitResult bh = clip(c, c.origin, far);
        boolean wall = bh.getType() != HitResult.Type.MISS;
        Vec3 stop = wall ? bh.getLocation() : far;
        Entity first = firstFoe(c, c.origin, stop);
        double fullLen = stop.distanceTo(start);
        double drawLen = Math.max(1.0, Math.min(fullLen, PIERCE_DRAW_TICKS * ARROW_STEP));
        Vec3 dir = stop.subtract(start).normalize();
        Vec3 end = start.add(dir.scale(drawLen));
        int steps = Math.max(1, (int) Math.ceil(drawLen / ARROW_STEP));
        DustParticleOptions body = dust(SKY_WHITE, c.chain ? S_ACCENT : S_MAIN);
        DustParticleOptions head = dust(SKY_BLUE, S_ACCENT);

        c.at(1, x -> sound(x, SoundEvents.CROSSBOW_SHOOT, hand, 0.45f, 1.7f));
        // 筋: 40 粒を通り道に等間隔に置き、頭が届いた tick にその区間の粒を出す
        double gap = drawLen / (PIERCE_POINTS - 1);
        for (int s = 1; s <= steps; s++) {
            final double to = Math.min(drawLen, s * ARROW_STEP);
            // この tick の区間 ((s-1)*3.6, s*3.6] に入る粒の番号 i0..i1
            final int i0 = s == 1 ? 0 : (int) Math.floor((s - 1) * ARROW_STEP / gap + 1e-9) + 1;
            final int i1 = s == steps ? PIERCE_POINTS - 1 : Math.min(PIERCE_POINTS - 1, (int) Math.floor(to / gap + 1e-9));
            c.at(s, x -> {
                int k = spend(x, Math.max(0, i1 - i0 + 1));
                for (int i = 0; i < k; i++) {
                    Vec3 q = start.lerp(end, (double) (i0 + i) / (PIERCE_POINTS - 1));
                    put(x, body, q.x, q.y, q.z, 0, 0, 0);
                }
                // 頭 (差し色の 2 粒)
                Vec3 tip = start.add(dir.scale(to));
                line(x, head, tip.subtract(dir.scale(0.35)), tip, 2, 0.0, 0.0);
            });
        }
        // 最初の当たり (矢は貫いて進むので、最初の敵。いなければ刺さる壁)
        if (first != null) {
            int tick = Mth.clamp((int) Math.ceil(first.position().distanceTo(c.origin) / ARROW_STEP), 1, SkillRules.PIERCE_LIFETIME);
            c.at(tick, x -> {
                Vec3 h = chest(first);
                flash(x, h);
                ring(x, head, h, 0.6, 12, 0.0, 0.0);
            });
        } else if (wall && fullLen <= drawLen + 0.01) {
            c.at(steps, x -> {
                flash(x, stop);
                ring(x, head, stop, 0.6, 12, 0.0, 0.0);
            });
        }
        c.lasts(Math.max(steps, 2) + 20);
    }

    // 骨の罠: 罠の場所に骨の輪 (半径 = 踏む距離)。8 tick で半径 0.5 まで内へ閉じ、同じ間に青緑の螺旋が 3 回りして昇り閉じる → 閉じた所で閃光
    private static void boneTrap(SkillFx.Cast c) {
        Vec3 spot = c.feet();
        double r0 = SkillRules.TRAP_TRIGGER;
        double r1 = 0.5;
        int span = 8;
        double turns = 3.0;
        double top = 1.6;
        int helixPer = 5;
        DustParticleOptions bone = dust(BONE, c.chain ? S_ACCENT : S_MAIN);
        DustParticleOptions teal = dust(TEAL, S_FINE);

        c.at(1, x -> sound(x, SoundEvents.BONE_BLOCK_PLACE, spot, 0.9f, 0.8f));
        for (int s = 0; s < span; s++) {
            final double t0 = (double) s / span;
            final double t1 = (double) (s + 1) / span;
            final double r = r0 + (r1 - r0) * t0;
            c.at(1 + s, x -> {
                ring(x, bone, spot, r, 12, 0.08, 0.0);
                // 螺旋のこの区間 (t0 → t1) を等間隔に
                int k = spend(x, helixPer);
                for (int i = 0; i < k; i++) {
                    double t = t0 + (t1 - t0) * (i + 1) / k;
                    double rr = r0 + (r1 - r0) * t;
                    double a = t * turns * Math.PI * 2;
                    put(x, teal, spot.x + Math.cos(a) * rr, spot.y + 0.1 + top * t, spot.z + Math.sin(a) * rr, 0, 0, 0);
                }
            });
        }
        c.at(span + 1, x -> {
            flash(x, spot.add(0, 0.5, 0));
            sound(x, SoundEvents.CHAIN_PLACE, spot, 0.8f, 0.7f);
        });
        c.lasts(span + 24);
    }

    // 天の驟雨: 降る範囲 (半径 4) に淡い 24 粒の輪。サーバーの一打ちごとに、9 ブロック上から 1 本の縦の筋が範囲の中へ落ち、地面で小さくはじける
    private static void skyRain(SkillFx.Cast c) {
        double rad = SkillRules.RAIN_RADIUS;
        Vec3 center = rainCenter(c);
        RandomSource rnd = c.random();
        DustParticleOptions streak = dust(SKY_BLUE, S_MAIN);
        DustParticleOptions edge = dust(SKY_BLUE, S_FINE);
        DustParticleOptions puff = dust(WHITE, S_FINE);
        double sky = 9.0;

        c.at(1, x -> {
            ring(x, edge, center, rad, 24, 0.12, 0.0);
            sound(x, SoundEvents.ARROW_SHOOT, x.hand(), 0.6f, 1.4f);
        });
        // 輪は降り続けるあいだに一度だけ描き直す (粒が消えるので)
        int mid = SkillRules.RAIN_PULSES * SkillRules.RAIN_INTERVAL / 2 + 1;
        c.at(mid, x -> ring(x, edge, center, rad, 24, 0.12, 0.0));
        for (int p = 1; p <= SkillRules.RAIN_PULSES; p++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rnd.nextDouble()) * rad;
            final Vec3 ground = new Vec3(center.x + Math.cos(a) * d, center.y + 0.15, center.z + Math.sin(a) * d);
            final int strike = p * SkillRules.RAIN_INTERVAL;
            final boolean firstStrike = p == 1;
            // 上の半分 → 下の半分 → 地面 (サーバーの一打ちの tick)
            c.at(strike - 2, x -> line(x, streak, ground.add(0, sky, 0), ground.add(0, sky * 0.53, 0), 6, 0.0, 0.0));
            c.at(strike - 1, x -> line(x, streak, ground.add(0, sky * 0.47, 0), ground.add(0, 0.3, 0), 6, 0.0, 0.0));
            c.at(strike, x -> {
                burst(x, puff, ground, 4, 0.15, 0.0);
                if (firstStrike) {
                    flash(x, ground.add(0, 0.2, 0));
                    sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, center, 0.5f, 1.5f);
                }
            });
        }
        c.lasts(SkillRules.RAIN_PULSES * SkillRules.RAIN_INTERVAL + 16);
    }

    // ================================================================ 淵

    // 淵の引き: 足もとの半径 7 (引き寄せる範囲) の紫の輪が、6 tick で前 3 ブロックの寄せ点へ縮みながら寄る → 寄せ点で明るい紫の芯と閃光
    private static void deepPull(SkillFx.Cast c) {
        double rad = SkillRules.PULL_RADIUS;
        Vec3 feet = c.feet();
        Vec3 point = new Vec3(feet.x + c.fwd.x * 3.0, feet.y, feet.z + c.fwd.z * 3.0);
        DustParticleOptions violet = dust(DEEP_VIOLET, S_ACCENT);
        DustParticleOptions core = dust(DEEP_CORE, S_MAIN);
        int span = 6;

        c.at(1, x -> sound(x, SoundEvents.PORTAL_AMBIENT, point, 0.25f, 2.0f));
        for (int s = 0; s < span; s++) {
            final double t = (double) s / (span - 1);
            final double r = rad + (0.6 - rad) * t;
            final Vec3 at = feet.lerp(point, t);
            final int n = 10 + (int) Math.round(16 * r / rad);
            c.at(1 + s, x -> ring(x, violet, at, r, n, 0.1, 0.0));
        }
        c.at(span + 1, x -> {
            Vec3 h = point.add(0, 0.6, 0);
            flash(x, h);
            burst(x, core, h, 8, 0.2, 0.0);
            ring(x, core, point, 0.6, 10, 0.15, 0.0);
            ring(x, ParticleTypes.PORTAL, point, 0.4, 6, 0.3, 0.0);
            sound(x, SoundEvents.CHORUS_FRUIT_TELEPORT, point, 0.6f, 0.6f);
        });
        c.lasts(span + 24);
    }

    // 淵の残響: 足もとに黒紫の輪が三度 (半径 1.4 / 2.4 / 3.4、tick 2 / 6 / 10)。それぞれ 1 ブロック広がって薄れる → 力が続くあいだ、体のまわりを 2 粒が回る
    private static void deepEcho(SkillFx.Cast c) {
        int span = SkillRules.ECHO_TICKS;
        double[] radii = {1.4, 2.4, 3.4};
        int[] ticks = {2, 6, 10};
        int[] counts = {18, 22, 26};
        DustParticleOptions dark = dust(DEEP_BLACK, S_ACCENT);
        DustParticleOptions darkFine = dust(DEEP_BLACK, S_FINE);
        DustParticleOptions glow = dust(DEEP_CORE, S_FINE);

        c.at(1, x -> sound(x, SoundEvents.AMETHYST_BLOCK_RESONATE, x.feet(), 0.8f, 0.5f));
        for (int i = 0; i < 3; i++) {
            final double r = radii[i];
            final int n = counts[i];
            c.at(ticks[i], x -> ring(x, dark, x.feet(), r, n, 0.08, 0.0));
            c.at(ticks[i] + 1, x -> ring(x, darkFine, x.feet(), r + 0.5, n * 3 / 4, 0.08, 0.0));
            c.at(ticks[i] + 2, x -> ring(x, glow, x.feet(), r + 1.0, n / 2, 0.08, 0.0));
        }
        c.at(6, x -> sound(x, SoundEvents.AMETHYST_BLOCK_RESONATE, x.feet(), 0.45f, 0.45f));
        // 回る 2 粒 (今の位置について行く。目から離すため半径 2.6)
        for (int t = 14; t < span; t += 8) {
            final double a = t * 0.35;
            c.at(t, x -> {
                Vec3 p = x.caster.position();
                int k = spend(x, 2);
                for (int i = 0; i < k; i++) {
                    double ang = a + Math.PI * i;
                    put(x, glow, p.x + Math.cos(ang) * 2.6, p.y + 0.6, p.z + Math.sin(ang) * 2.6, 0, 0, 0);
                }
            });
        }
        c.lasts(span + 4);
    }

    // 淵の顎: 手から狙いへ黒い糸 (3 tick) → 狙いの半径 4 の黒い輪が 6 tick で縮む → 噛む tick (サーバーの 8 tick 後) に 12 本の牙が縁から中心へ閉じる → 中心に閃光
    private static void deepMaw(SkillFx.Cast c) {
        double rad = SkillRules.MAW_RADIUS;
        Vec3 center = aimSpot(c, SkillRules.MAW_REACH);
        Vec3 hand = c.hand();
        Vec3 target = center.add(0, 0.5, 0);
        Vec3 toward = target.subtract(hand);
        Vec3 start = toward.lengthSqr() > NEAR_CLEAR * NEAR_CLEAR * 2 ? hand.add(toward.normalize().scale(NEAR_CLEAR)) : hand;
        DustParticleOptions black = dust(DEEP_BLACK, S_ACCENT);
        DustParticleOptions fang = dust(DEEP_CORE, S_MAIN);
        int bite = 9;
        double phase = c.random().nextDouble() * Math.PI;

        // 糸 (12 粒を 3 tick で伸ばす)
        for (int s = 0; s < 3; s++) {
            final Vec3 a = start.lerp(target, s / 3.0);
            final Vec3 b = start.lerp(target, (s + 1) / 3.0);
            c.at(1 + s, x -> line(x, black, a, b, 4, 0.0, 0.0));
        }
        c.at(1, x -> sound(x, SoundEvents.WARDEN_SONIC_CHARGE, center, 0.25f, 1.6f));
        // 縮む輪 (tick 3 → 8)
        for (int s = 0; s < 6; s++) {
            final double r = rad + (rad * 0.3 - rad) * s / 5.0;
            final int n = 10 + (int) Math.round(14 * r / rad);
            c.at(3 + s, x -> ring(x, black, center, r, n, 0.1, 0.0));
        }
        // 牙: 1 本 4 粒。噛む tick に外の 2 粒、次の tick に内の 2 粒 (内へ閉じる)
        for (int half = 0; half < 2; half++) {
            final int h = half;
            c.at(bite + half, x -> {
                int k = spend(x, 24);
                for (int i = 0; i < k; i++) {
                    int tooth = i / 2;
                    int seg = h * 2 + i % 2;
                    double f = seg / 3.0;
                    double a = phase + tooth * Math.PI * 2 / 12;
                    double rr = rad * (1 - 0.7 * f);
                    put(x, fang, center.x + Math.cos(a) * rr, center.y + 0.1 + 0.9 * f, center.z + Math.sin(a) * rr, 0, 0, 0);
                }
                if (h == 0) {
                    flash(x, center.add(0, 0.7, 0));
                    kick(x, 0.9f);
                    sound(x, SoundEvents.WARDEN_SONIC_BOOM, center, 0.3f, 1.5f);
                }
            });
        }
        c.lasts(bite + 20);
    }

    // ================================================================ 前世の技

    // 遺響の一撃: 前に金の三日月 (半径 2.6・-60〜+60 度、2 tick で振る) → 6 tick 後、相手の所に淡い影の弧が現れて 4 tick で手前へ寄る → 二の太刀 (逆向きの弧) と閃光 (サーバーの 10 tick 後)
    private static void echoStrike(SkillFx.Cast c) {
        LivingEntity target = echoTarget(c);
        double a0 = Math.atan2(c.fwd.z, c.fwd.x);
        double span = Math.toRadians(60);
        Vec3 eyeLevel = c.origin.add(0, -0.4, 0);
        Vec3 hit0 = target != null ? chest(target) : c.aim(4);
        DustParticleOptions gold = dust(GOLD, S_ACCENT);
        DustParticleOptions white = dust(WHITE, S_FINE);

        // 一の太刀 (15 粒ずつ 2 tick)
        c.at(1, x -> {
            arc(x, gold, eyeLevel, 2.6, a0 - span, a0, 0.0, 15);
            sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, hit0, 0.8f, 0.7f);
        });
        c.at(2, x -> {
            arc(x, gold, eyeLevel, 2.6, a0, a0 + span, 0.0, 15);
            // 振り終わりの縁に白い差し色
            arc(x, white, eyeLevel, 2.75, a0 + span * 0.7, a0 + span, 0.0, 4);
        });
        // 影の弧: 相手の所から手前へ (tick 7 → 10)
        for (int s = 0; s < 4; s++) {
            final double f = s * 0.12;
            c.at(7 + s, x -> {
                Vec3 at = (target != null && target.isAlive() ? chest(target) : hit0).lerp(eyeLevel, f);
                crescent(x, white, at, x.fwd, 1.0, Math.toRadians(50), 12);
            });
        }
        // 二の太刀 (逆向き。相手の胸を通る弧を 2 tick で)
        for (int s = 0; s < 2; s++) {
            final int ss = s;
            c.at(11 + s, x -> {
                Vec3 h = target != null && target.isAlive() ? chest(target) : hit0;
                Vec3 ctr = h.subtract(x.fwd.scale(1.6));
                double from = ss == 0 ? a0 + span : a0;
                double to = ss == 0 ? a0 : a0 - span;
                arc(x, gold, ctr, 1.6, from, to, 0.0, 15);
                if (ss == 0) {
                    flash(x, h);
                    sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, h, 0.6f, 0.5f);
                }
            });
        }
        c.lasts(32);
    }

    // 墓鐘: 頭の上に金の鐘の形 (縁の半径 1.2 → 上の半径 0.6、3 tick で組む) → 打つ: 鐘から足もとへ 6 ブロックの光の柱、地面の金の輪が半径 1 → 5 へ 6 tick で広がる
    private static void graveBell(SkillFx.Cast c) {
        double rad = SkillRules.BELL_RADIUS;
        Vec3 base = c.feet();
        Vec3 head = base.add(0, 1.9, 0);
        DustParticleOptions gold = dust(GOLD, S_MAIN);
        DustParticleOptions warm = dust(WARM, S_FINE);
        // 鐘の段 (下から): 高さ 0.2 → 1.2、半径 1.2 → 0.6
        double[][] bands = {{0.2, 1.2, 14}, {0.45, 1.05, 10}, {0.7, 0.9, 8}, {0.95, 0.75, 7}, {1.2, 0.6, 6}};
        int strike = 4;

        c.at(1, x -> {
            ring(x, gold, head, bands[0][1], (int) bands[0][2], bands[0][0], 0.0);
            ring(x, gold, head, bands[1][1], (int) bands[1][2], bands[1][0], 0.0);
        });
        c.at(2, x -> {
            ring(x, gold, head, bands[2][1], (int) bands[2][2], bands[2][0], 0.0);
            ring(x, gold, head, bands[3][1], (int) bands[3][2], bands[3][0], 0.0);
        });
        c.at(3, x -> ring(x, gold, head, bands[4][1], (int) bands[4][2], bands[4][0], 0.0));
        c.at(strike, x -> {
            flash(x, head.add(0, 0.7, 0));
            pillar(x, warm, base.add(0, 0.1, 0), 6.0, 14);
            sound(x, SoundEvents.BELL_BLOCK, head, 1.0f, 0.6f);
        });
        // 広がる輪 (tick 4 → 9)
        for (int s = 0; s < 6; s++) {
            final double r = 1.0 + (rad - 1.0) * s / 5.0;
            final int n = 8 + (int) Math.round(16 * r / rad);
            c.at(strike + s, x -> ring(x, gold, base, r, n, 0.1, 0.0));
        }
        c.lasts(strike + 26);
    }

    // 最期の光: 足もとに白金の柱 (6 ブロック・16 粒) と閃光 → 三つの輪 (半径 1.8 / 3.7 / 5.5) が内から順に 3 tick おきに広がる → 外の輪の縁で閃光
    private static void finalLight(SkillFx.Cast c) {
        double[] radii = {1.8, 3.7, SkillRules.LIGHT_RADIUS};
        int[] counts = {26, 42, 56};
        Vec3 base = c.feet();
        DustParticleOptions pale = dust(PALE_GOLD, S_ACCENT);
        DustParticleOptions warm = dust(WARM, S_MAIN);

        c.at(1, x -> {
            pillar(x, warm, base.add(0, 0.1, 0), 6.0, 16);
            flash(x, base.add(0, 0.2, 0));
            kick(x, 1.0f);
            sound(x, SoundEvents.BELL_RESONATE, base, 0.6f, 1.4f);
        });
        // 輪: サーバーと同じ 0 / 3 / 6 tick 後。それぞれ 2 tick で広がる (少し内 → 本当の半径)
        for (int i = 0; i < 3; i++) {
            final double r = radii[i];
            final int n = counts[i];
            final int t = 1 + i * 3;
            c.at(t, x -> ring(x, pale, base, r * 0.85, n * 2 / 5, 0.12, 0.0));
            c.at(t + 1, x -> ring(x, pale, base, r, n * 3 / 5, 0.12, 0.0));
        }
        c.at(8, x -> {
            flash(x, base.add(x.fwd.scale(SkillRules.LIGHT_RADIUS)).add(0, 0.3, 0));
            sound(x, SoundEvents.LIGHTNING_BOLT_THUNDER, base, 0.2f, 1.8f);
        });
        c.lasts(34);
    }

    // ================================================================ 道具

    private static Vector3f rgb(int c) {
        return new Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
    }

    private static DustParticleOptions dust(int color, float size) {
        return new DustParticleOptions(rgb(color), Math.min(1.0f, size));
    }

    // 弧の中ほどが点 at を通る、前向きに膨らんだ水平の三日月 (半径 radius、左右 half ラジアン)
    private static void crescent(SkillFx.Cast c, ParticleOptions p, Vec3 at, Vec3 fwd, double radius, double half, int n) {
        double a0 = Math.atan2(fwd.z, fwd.x);
        Vec3 ctr = at.subtract(fwd.scale(radius));
        arc(c, p, ctr, radius, a0 - half, a0 + half, 0.0, n);
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
}
