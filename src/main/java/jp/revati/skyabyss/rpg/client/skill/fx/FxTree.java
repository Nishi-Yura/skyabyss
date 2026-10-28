package jp.revati.skyabyss.rpg.client.skill.fx;

import java.util.Map;
import java.util.function.Consumer;

import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import jp.revati.skyabyss.rpg.client.skill.SkillFx.Cast;
import jp.revati.skyabyss.rpg.client.skill.fx.FxDraw.Ease;
import jp.revati.skyabyss.rpg.client.skill.fx.FxDraw.Prim;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

// 技の木 v2 (docs/design/48) で足した使う技の段取り。形は FxDraw の板、粒は当たりの火花だけ。
//   盾殴り (前の扇を石色で押す) / 鯨骨の振り (骨色の重い振り下ろし) / 回し斬り (腰の高さの二重の輪と前への筋)
//   二連牙 (白と若葉の二つの小さな弧) / 火花散らし (熾の輪と散る火花) / 足止めの矢 (骨色の筋と足もとの輪) / 星屑 (星の白が降る柱)
// 半径・角度・距離は SkillRules と同じ数。当たりの閃光は一つだけ
public final class FxTree {
    private FxTree() {
    }

    private static final int STONE = 0xC9D3CC;
    private static final int TEAL = 0x4FE6D2;
    private static final int BONE = 0xEDE4CC;
    private static final int WHITE = 0xF2FFFF;
    private static final int GREEN = 0x9EFFD0;
    private static final int EMBER = 0xFF7A2A;
    private static final int CINDER = 0xFFC860;
    private static final int STAR = 0x9FA8FF;
    private static final int STAR_WHITE = 0xF4F2FF;

    public static void register(Map<Skill, Consumer<SkillFx.Cast>> plans) {
        plans.put(Skill.SHIELD_BASH, FxTree::shieldBash);
        plans.put(Skill.BONE_SWING, FxTree::boneSwing);
        plans.put(Skill.SPIN_CUT, FxTree::spinCut);
        plans.put(Skill.TWIN_FANG, FxTree::twinFang);
        plans.put(Skill.SPARK_BURST, FxTree::sparkBurst);
        plans.put(Skill.SNARE_SHOT, FxTree::snareShot);
        plans.put(Skill.STARDUST, FxTree::stardust);
    }

    // 盾殴り [放ち 0 (石色の扇が前へ) / 当たり 2 (閃光と押し出しの輪) / 残り 12]
    private static void shieldBash(Cast c) {
        Vec3 f = c.feet().add(0, 0.6, 0);
        double dir = Math.atan2(c.fwd.z, c.fwd.x);
        double half = Math.toRadians(SkillRules.BASH_HALF_ANGLE);
        c.at(0, x -> {
            FxDraw.add(x, Prim.fan(f, dir, half, 0.4, SkillRules.BASH_RANGE).color(STONE).life(7).ease(Ease.OUT).alpha(0.75f).glow(0.3));
            FxDraw.add(x, Prim.fan(f, dir, half * 0.6, 0.3, SkillRules.BASH_RANGE - 0.6).color(TEAL).life(6).ease(Ease.OUT).alpha(0.6f));
            SkillFx.sound(x, SoundEvents.SHIELD_BLOCK, f, 0.7f, 0.8f);
        });
        c.at(2, x -> {
            Vec3 hit = x.feet().add(x.fwd.scale(1.6)).add(0, 0.8, 0);
            SkillFx.flash(x, hit);
            SkillFx.hitSpark(x, hit, STONE);
            FxDraw.add(x, Prim.ring(x.feet().add(x.fwd.scale(1.4)), 0.3, 1.6).color(STONE).width(0.3).life(8).ease(Ease.OUT));
            SkillFx.kick(x, 1.2f);
            SkillFx.sound(x, SoundEvents.ANVIL_PLACE, hit, 0.4f, 0.7f);
        });
        c.lasts(14);
    }

    // 鯨骨の振り [溜め 0 (頭の上の骨色の弧) / 振り 3 (振り下ろしの太い弧) / 当たり 6 (重い閃光と地の輪) / 残り 20]
    private static void boneSwing(Cast c) {
        Vec3 eye = c.origin;
        Vec3 up = new Vec3(0, 1, 0);
        c.at(0, x -> {
            FxDraw.add(x, Prim.arc(eye, x.fwd, up, 1.6, 2.4, 1.3).color(BONE).width(0.5).life(6).ease(Ease.OUT).alpha(0.8f));
            SkillFx.burst(x, SkillFx.rune(), x.hand(), 3, 0.1, 0.02);
        });
        c.at(3, x -> {
            FxDraw.add(x, Prim.arc(eye, x.fwd, up, 2.4, 1.3, -0.8).color(BONE).width(1.1).life(9).ease(Ease.OUT).glow(0.4).trail(0.7));
            FxDraw.add(x, Prim.arc(eye, x.fwd, up, 2.1, 1.2, -0.7).color(STONE).width(0.35).life(9).ease(Ease.OUT).alpha(0.9f).trail(0.6));
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, eye, 0.7f, 0.5f);
        });
        c.at(6, x -> {
            Vec3 hit = x.target != null ? x.target : x.feet().add(x.fwd.scale(2.2)).add(0, 0.8, 0);
            SkillFx.flash(x, hit);
            SkillFx.hitSpark(x, hit, BONE);
            Vec3 g = new Vec3(hit.x, x.feet().y + 0.05, hit.z);
            FxDraw.add(x, Prim.ring(g, 0.4, 2.2).color(BONE).width(0.4).life(10).ease(Ease.OUT).glow(0.3));
            FxDraw.add(x, Prim.disc(g, 0.2, 1.4).color(STONE).life(14).alpha(0.4f));
            SkillFx.kick(x, 1.8f);
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, g, 0.6f, 0.5f);
        });
        c.lasts(22);
    }

    // 回し斬り [放ち 0-4 (腰の高さの二重の輪が回る) / 進み 2 (足もとの前への筋) / 当たり 5 / 残り 18]
    private static void spinCut(Cast c) {
        Vec3 waist = c.waist();
        Vec3 flat = new Vec3(1, 0, 0);
        Vec3 flatZ = new Vec3(0, 0, 1);
        c.at(0, x -> {
            FxDraw.add(x, Prim.arc(waist, flat, flatZ, SkillRules.SPIN_RADIUS, 0, Math.PI * 2).color(BONE).width(0.4).life(10).ease(Ease.LINEAR).trail(0.25).glow(0.4));
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, waist, 0.6f, 1.1f);
        });
        c.at(2, x -> {
            Vec3 w = x.waist();
            FxDraw.add(x, Prim.arc(w, flatZ, flat, SkillRules.SPIN_RADIUS - 0.3, 0, Math.PI * 2).color(TEAL).width(0.18).life(9).ease(Ease.LINEAR).trail(0.2).alpha(0.9f));
            Vec3 a = x.feet().add(0, 0.1, 0);
            FxDraw.add(x, Prim.line(a, a.add(x.fwd.scale(SkillRules.SPIN_DASH_BLOCKS))).color(STONE).width(0.3).life(8).ease(Ease.OUT).alpha(0.7f));
        });
        c.at(5, x -> {
            Vec3 f = x.feet();
            SkillFx.flash(x, x.waist().add(x.fwd.scale(0.8)));
            FxDraw.add(x, Prim.ring(f, SkillRules.SPIN_RADIUS - 0.2, SkillRules.SPIN_RADIUS + 0.2).color(BONE).width(0.3).life(9));
            SkillFx.hitSpark(x, f.add(0, 1.0, 0), BONE);
            SkillFx.kick(x, 0.8f);
        });
        c.lasts(18);
    }

    // 二連牙 [一刺し 0 (白の小さな弧) / 二刺し 4 (若葉の弧。逆向き) / 残り 12]
    private static void twinFang(Cast c) {
        Vec3 cen = c.origin.add(c.fwd.scale(1.8));
        Vec3 up = new Vec3(0, 1, 0);
        c.at(0, x -> {
            FxDraw.add(x, Prim.arc(cen, x.right, up, 1.1, 2.2, -0.3).color(WHITE).width(0.35).life(6).ease(Ease.OUT).glow(0.5).trail(0.5));
            SkillFx.hitSpark(x, cen, WHITE);
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, cen, 0.4f, 1.9f);
        });
        c.at(SkillRules.FANG_GAP, x -> {
            Vec3 cen2 = x.origin.add(x.fwd.scale(1.8));
            FxDraw.add(x, Prim.arc(cen2, x.right, up, 1.1, 0.9, 3.4).color(GREEN).width(0.35).life(6).ease(Ease.OUT).glow(0.5).trail(0.5));
            SkillFx.flash(x, cen2);
            SkillFx.hitSpark(x, cen2, GREEN);
            SkillFx.kick(x, 0.5f);
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, cen2, 0.4f, 2.0f);
        });
        c.lasts(14);
    }

    // 火花散らし [放ち 0 (熾の輪と暗い地) / 火花 1 (まわりに短い火の柱が散る) / 残り 16]
    private static void sparkBurst(Cast c) {
        Vec3 f = c.feet();
        c.at(0, x -> {
            FxDraw.add(x, Prim.ring(f, 0.3, SkillRules.SPARK_RADIUS).color(EMBER).width(0.4).life(9).ease(Ease.OUT).glow(0.6));
            FxDraw.add(x, Prim.disc(f, 0.2, SkillRules.SPARK_RADIUS).color(0x3A1408).alpha(0.45f).life(14).ease(Ease.SMOOTH));
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, f, 0.5f, 1.5f);
        });
        c.at(1, x -> {
            for (int i = 0; i < 7; i++) {
                double ang = Math.PI * 2 * i / 7 + x.random().nextDouble() * 0.5;
                double r = 1.0 + x.random().nextDouble() * (SkillRules.SPARK_RADIUS - 1.2);
                Vec3 p = f.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                FxDraw.add(x, Prim.beam(p, 0.8 + x.random().nextDouble() * 0.8).color(CINDER).width(0.14).life(6 + i % 3).ease(Ease.OUT).glow(0.8));
            }
            SkillFx.flash(x, f.add(0, 0.8, 0));
            SkillFx.kick(x, 1.0f);
            SkillFx.sound(x, SoundEvents.FIRE_EXTINGUISH, f, 0.5f, 1.4f);
        });
        c.lasts(16);
    }

    // 足止めの矢 [放ち 0 (手元から骨色の筋) / 当たり 2 (相手の足もとに輪と縄の殻) / 残り 14]
    private static void snareShot(Cast c) {
        Vec3 from = c.hand();
        c.at(0, x -> {
            Vec3 end = x.target != null ? x.target : x.aim(SkillRules.SNARE_RANGE);
            FxDraw.add(x, Prim.line(from, end).color(BONE).width(0.3).life(7).ease(Ease.LINEAR).glow(0.7).trail(0.3));
            SkillFx.burst(x, SkillFx.rune(), from, 2, 0.1, 0.02);
            SkillFx.sound(x, SoundEvents.ARROW_SHOOT, from, 0.6f, 1.2f);
        });
        c.at(2, x -> {
            Vec3 end = x.target != null ? x.target : x.aim(SkillRules.SNARE_RANGE);
            Vec3 g = end.add(0, -0.9, 0);
            SkillFx.flash(x, end);
            SkillFx.hitSpark(x, end, BONE);
            FxDraw.add(x, Prim.ring(g, 0.3, 1.3).color(BONE).width(0.3).life(12).ease(Ease.OUT).glow(0.3));
            FxDraw.add(x, Prim.shell(g, 0.4, 1.0, 0.8).color(STONE).width(0.2).life(14).ease(Ease.OUT).alpha(0.7f));
            SkillFx.sound(x, SoundEvents.ARROW_HIT, end, 0.6f, 0.8f);
        });
        c.lasts(16);
    }

    // 星屑 [放ち 0 (星の殻が広がる) / 降り 2-6 (星の白の柱が降る) / 残り 8 (地の光) / 残り 20]
    private static void stardust(Cast c) {
        Vec3 f = c.feet();
        c.at(0, x -> {
            FxDraw.add(x, Prim.shell(f.add(0, 1.4, 0), 0.4, SkillRules.DUST_RADIUS, 2.4).color(STAR).width(0.3).life(12).ease(Ease.OUT).alpha(0.6f).glow(0.4));
            SkillFx.burst(x, SkillFx.rune(), x.hand(), 4, 0.2, 0.03);
            SkillFx.sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, f, 0.8f, 1.2f);
        });
        for (int k = 0; k < 3; k++) {
            int wave = k;
            c.at(2 + k * 2, x -> {
                for (int i = 0; i < 4; i++) {
                    double ang = x.random().nextDouble() * Math.PI * 2;
                    double r = 0.8 + x.random().nextDouble() * (SkillRules.DUST_RADIUS - 1.0);
                    Vec3 p = f.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
                    FxDraw.add(x, Prim.beam(p, 2.2 - wave * 0.3).color(wave == 1 ? STAR_WHITE : STAR).width(0.16).life(7).ease(Ease.OUT3).glow(0.9));
                }
                if (wave == 1) {
                    SkillFx.flash(x, f.add(0, 1.2, 0));
                    SkillFx.kick(x, 0.6f);
                }
            });
        }
        c.at(8, x -> {
            FxDraw.add(x, Prim.disc(f, 0.3, SkillRules.DUST_RADIUS).color(STAR_WHITE).alpha(0.3f).life(12).ease(Ease.SMOOTH));
            FxDraw.add(x, Prim.ring(f, SkillRules.DUST_RADIUS - 0.2, SkillRules.DUST_RADIUS + 0.2).color(STAR).width(0.2).life(12));
        });
        c.lasts(20);
    }
}
