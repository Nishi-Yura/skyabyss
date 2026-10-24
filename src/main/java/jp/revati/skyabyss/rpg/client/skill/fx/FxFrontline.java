package jp.revati.skyabyss.rpg.client.skill.fx;

import java.util.Map;
import java.util.function.Consumer;

import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import jp.revati.skyabyss.rpg.client.skill.SkillFx.Cast;
import jp.revati.skyabyss.rpg.client.skill.fx.FxDraw.Ease;
import jp.revati.skyabyss.rpg.client.skill.fx.FxDraw.Prim;
import jp.revati.skyabyss.rpg.skill.Skill;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

// 岩守り・風読み・灰の鍛え人の技の段取り (近接・機動)。
// 形は FxDraw の板で描く (主役)。粒は当たりの火花だけ (脇役)。
// 色: 岩 = 石の灰白 0xC9D3CC・青緑 0x4FE6D2・骨 0xEDE4CC / 風 = 白 0xF2FFFF・若葉 0x9EFFD0 / 灰 = 熾 0xFF7A2A・灰 0xB8B0A8・火 0xFFC860
// 当たりの閃光は一つだけ。形はカメラから 1.2 ブロック以上離して置く (一人称)。
// 半径・角度・距離は SkillRules / SkillActions と同じ数。
public final class FxFrontline {
    private FxFrontline() {
    }

    // ---------------------------------------------------------------- 技の形 (SkillRules と同じ数)
    private static final double CLEAVE_RANGE = 4.0;
    private static final double ROCK_CALL_RADIUS = 8;
    private static final double SWEEP_RADIUS = 3.5;
    private static final double LUNGE_RANGE = 8.0;
    private static final double REND_RANGE = 4.0;
    private static final double HAMMER_RADIUS = 3.5;
    private static final double HAMMER_REACH = 2.0;
    private static final double ASH_RADIUS = 5;

    // 見た目だけの数
    private static final double CLEAVE_ARC_R = 2.8;

    // 色
    private static final int STONE = 0xC9D3CC;
    private static final int TEAL = 0x4FE6D2;
    private static final int BONE = 0xEDE4CC;
    private static final int SEA = 0x58D6F0;
    private static final int FOAM = 0xF2FFFF;
    private static final int WHITE = 0xF2FFFF;
    private static final int GREEN = 0x9EFFD0;
    private static final int EMBER = 0xFF7A2A;
    private static final int CINDER = 0xFFC860;
    private static final int ASH = 0xB8B0A8;

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

    // 巨神斬り [溜め 0-1 / 放ち 2 / 当たり 5 / 残り 22]
    // 形: 目を中心に、頭の上から前の地面へ振り下ろす骨色の三日月 (青緑の縁)。当たりの足もとに骨の輪
    private static void titanCleave(Cast c) {
        Vec3 eye = c.origin;
        Vec3 up = new Vec3(0, 1, 0);
        c.at(0, x -> SkillFx.burst(x, SkillFx.rune(), x.hand(), 3, 0.1, 0.02));
        c.at(2, x -> {
            // 太く明るい三日月 (幅 0.9、芯は濃く、縁は柔らかい)。頭が振り抜き、尾が遅れる
            FxDraw.add(x, Prim.arc(eye, x.fwd, up, CLEAVE_ARC_R, 1.2, -0.9).color(BONE).width(0.9).life(8).ease(Ease.OUT).glow(0.35).trail(0.6));
            FxDraw.add(x, Prim.arc(eye, x.fwd, up, CLEAVE_ARC_R - 0.3, 1.1, -0.7).color(TEAL).width(0.3).life(8).ease(Ease.OUT).alpha(0.9f).trail(0.6));
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, eye, 0.6f, 0.6f);
        });
        c.at(5, x -> {
            Vec3 f = x.feet();
            Vec3 hit = f.add(x.fwd.scale(1.9)).add(0, 0.6, 0);
            SkillFx.flash(x, hit);
            FxDraw.add(x, Prim.ring(f, 1.6, 2.6).color(BONE).width(0.35).life(8).glow(0.3));
            FxDraw.add(x, Prim.disc(hit, 0.2, 0.9).color(WHITE).life(4).alpha(0.8f));
            SkillFx.burst(x, SkillFx.rune(), hit, 5, 0.25, 0.12);
            SkillFx.kick(x, 1.0f);
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, f, 0.45f, 0.55f);
        });
        c.at(6, x -> {
            FxDraw.add(x, Prim.ring(x.feet(), CLEAVE_RANGE - 0.3, CLEAVE_RANGE + 0.2).color(TEAL).width(0.16).life(14));
            SkillFx.sound(x, SoundEvents.STONE_BREAK, x.feet(), 0.7f, 0.7f);
        });
        c.lasts(22);
    }

    // 岩呼び [溜め 0 / 放ち 0-4 (柱) / 当たり 4 / 残り 16]
    // 形: 足もとから青緑の柱が昇り、踏みつけの衝撃の殻が半径 8 へ広がる。石色の輪が後を追う
    private static void rockCall(Cast c) {
        Vec3 base = c.feet();
        c.at(0, x -> {
            FxDraw.add(x, Prim.beam(base, 2.6).color(TEAL).width(0.7).life(8).ease(Ease.OUT3).glow(0.4));
            // 短い石の柱が三本、半径 2 に立つ (低く、密に)
            for (int k = 0; k < 3; k++) {
                double ang = Math.PI * 2 * k / 3 + 0.4;
                Vec3 p = base.add(Math.cos(ang) * 2.0, 0, Math.sin(ang) * 2.0);
                FxDraw.add(x, Prim.beam(p, 1.6).color(STONE).width(0.45).life(8).ease(Ease.OUT3));
            }
            SkillFx.burst(x, SkillFx.rune(), base.add(0, 1.2, 0), 3, 0.5, 0.05);
        });
        c.at(4, x -> {
            SkillFx.flash(x, base.add(0, 0.5, 0));
            // 地面の輪 4 → 6 ブロック (幅 0.6、芯は濃く)。大きな面は張らない
            FxDraw.add(x, Prim.ring(base, 3.5, 6.0).color(STONE).width(0.6).life(9).ease(Ease.OUT).glow(0.3));
            SkillFx.burst(x, SkillFx.rune(), base.add(0, 0.6, 0), 3, 0.7, 0.1);
            SkillFx.kick(x, 0.9f);
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, base, 0.5f, 0.5f);
        });
        c.at(5, x -> {
            FxDraw.add(x, Prim.ring(base, 4.5, ROCK_CALL_RADIUS).color(TEAL).width(0.45).life(10).ease(Ease.OUT).glow(0.3));
        });
        c.at(12, x -> FxDraw.add(x, Prim.ring(base, ROCK_CALL_RADIUS - 1.0, ROCK_CALL_RADIUS).color(TEAL).width(0.3).life(8)));
        c.lasts(16);
    }

    // 薙ぎ払い [溜め 0 / 放ち 1-12 (一回転) / 当たり 8 / 残り 22]
    // 形: 腰の高さで 90 度の海色の弧が一回転 (背→右→前→左)。当たりは半径 3.5 の水の輪と殻、霧が残る
    private static void whaleSweep(Cast c) {
        Vec3 waist = c.waist();
        Vec3 flat = new Vec3(1, 0, 0);
        Vec3 flatZ = new Vec3(0, 0, 1);
        c.at(0, x -> SkillFx.burst(x, SkillFx.rune(), x.waist(), 3, 0.5, 0.03));
        c.at(1, x -> {
            FxDraw.add(x, Prim.arc(waist, flat, flatZ, CLEAVE_ARC_R + 0.6, 0, Math.PI * 2).color(SEA).width(0.34).life(12).ease(Ease.LINEAR).trail(0.22).glow(0.5));
            FxDraw.add(x, Prim.arc(waist, flat, flatZ, CLEAVE_ARC_R + 0.5, 0, Math.PI * 2).color(FOAM).width(0.1).life(12).ease(Ease.LINEAR).trail(0.14).alpha(0.9f));
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, x.feet(), 0.6f, 0.7f);
        });
        c.at(8, x -> {
            Vec3 f = x.feet();
            SkillFx.flash(x, waist.add(x.fwd.scale(0.6)));
            FxDraw.add(x, Prim.shell(f.add(0, 0.4, 0), 0.5, SWEEP_RADIUS, 1.3).color(SEA).life(10).ease(Ease.OUT).width(0.4));
            FxDraw.add(x, Prim.ring(f, SWEEP_RADIUS - 0.1, SWEEP_RADIUS + 0.2).color(FOAM).width(0.4).life(10));
            FxDraw.add(x, Prim.disc(f, 0.5, SWEEP_RADIUS).color(SEA).life(22).alpha(0.3f));
            SkillFx.burst(x, SkillFx.rune(), f.add(0, 1.0, 0), 4, 0.8, 0.05);
            SkillFx.kick(x, 0.8f);
            SkillFx.sound(x, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, f, 0.4f, 0.8f);
        });
        c.lasts(22);
    }

    // 突進 [溜め 0 / 放ち 0-5 (白い筋と若葉の筋) / 当たり 6 / 残り 14]
    // 形: 腰の高さから前へ 8 ブロック、白い筋 (芯) と若葉の細い筋が並ぶ。到着点に白い輪
    private static void lunge(Cast c) {
        Vec3 from = c.waist().add(c.fwd.scale(0.3));
        Vec3 to = c.waist().add(c.fwd.scale(LUNGE_RANGE - 1.0));
        Vec3 side = c.right.scale(0.55).add(0, -0.25, 0);
        c.at(0, x -> {
            // 速い筋 (太い白、若葉の縁)。その後ろに残像の影 (半透明の柱) を三つ、跳んだ道に置く
            FxDraw.add(x, Prim.line(from, to).color(WHITE).width(1.5).life(10).ease(Ease.OUT).glow(0.5).trail(0.5));
            FxDraw.add(x, Prim.line(from.add(side), to.add(side)).color(GREEN).width(0.14).life(10).ease(Ease.OUT).alpha(0.9f).trail(0.4));
            for (int k = 1; k <= 3; k++) {
                Vec3 g = from.lerp(to, k / 4.0);
                FxDraw.add(x, Prim.beam(g.subtract(0, 1.0, 0), 2.6).color(GREEN).width(1.5).life(10).ease(Ease.OUT).alpha(0.7f));
            }
            SkillFx.sound(x, SoundEvents.ENDER_DRAGON_FLAP, from, 0.35f, 1.7f);
        });
        c.at(6, x -> {
            Vec3 f = x.feet();
            SkillFx.flash(x, to.add(0, 0.2, 0));
            FxDraw.add(x, Prim.disc(f.add(x.fwd.scale(LUNGE_RANGE - 1.0)), 0.2, 1.8).color(WHITE).life(8).alpha(0.8f));
            FxDraw.add(x, Prim.ring(f.add(x.fwd.scale(LUNGE_RANGE - 1.0)), 0.5, 2.5).color(GREEN).width(0.2).life(10));
            SkillFx.burst(x, SkillFx.rune(), to, 4, 0.5, 0.12);
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_STRONG, to, 0.5f, 1.3f);
        });
        c.lasts(14);
    }

    // 跳び退き [溜め 0 / 放ち 0-4 (残像の柱と白い筋) / 当たり 5 / 残り 12]
    // 形: 出た所に白い残像の柱、後ろへ向けた白い筋。着地に白い輪
    private static void backstep(Cast c) {
        Vec3 start = c.feet();
        Vec3 end = c.waist().add(c.fwd.scale(-2.6));
        Vec3 landing = start.add(c.fwd.scale(-2.6));
        c.at(0, x -> {
            // 残像: 出た所に影の柱 (半透明) と、地面に白い滑りの筋
            FxDraw.add(x, Prim.beam(start, 2.0).color(FOAM).width(0.8).life(14).alpha(0.5f).glow(0.3));
            FxDraw.add(x, Prim.ring(start, 0.6, 1.6).color(GREEN).width(0.4).life(8).alpha(0.8f));
            SkillFx.sound(x, SoundEvents.ENDER_DRAGON_FLAP, start, 0.3f, 1.8f);
        });
        c.at(1, x -> {
            // 後ろへ滑る白い筋 (地面の高さ、太く)
            FxDraw.add(x, Prim.line(start.add(0, 0.1, 0), landing.add(0, 0.1, 0)).color(WHITE).width(0.8).life(12).ease(Ease.OUT).trail(0.6).glow(0.4));
            FxDraw.add(x, Prim.beam(start.add(c.fwd.scale(-1.3)), 1.9).color(FOAM).width(0.7).life(12).alpha(0.35f));
        });
        c.at(5, x -> {
            SkillFx.flash(x, landing.add(0, 0.4, 0));
            FxDraw.add(x, Prim.ring(landing, 0.5, 2.2).color(WHITE).width(0.5).life(12).glow(0.3));
            FxDraw.add(x, Prim.beam(landing, 1.8).color(FOAM).width(0.7).life(10).alpha(0.5f));
            SkillFx.burst(x, SkillFx.rune(), landing.add(0, 0.8, 0), 3, 0.4, 0.05);
        });
        c.lasts(12);
    }

    // 二連の斬り [溜め 0 / 放ち 1 (一撃目) と 5 (二撃目) / 当たり 5 / 残り 22]
    // 形: 白と若葉の二本の三日月が前の宙で × に交わる。二撃目で風の殻
    private static void windRend(Cast c) {
        Vec3 cen = c.origin.add(c.fwd.scale(3.2));
        Vec3 up = new Vec3(0, 1, 0);
        c.at(0, x -> SkillFx.burst(x, SkillFx.rune(), x.hand(), 3, 0.1, 0.02));
        c.at(1, x -> {
            FxDraw.add(x, Prim.arc(cen, x.right, up, 2.0, 2.6, -0.6).color(WHITE).width(0.6).life(8).ease(Ease.OUT).glow(0.5).trail(0.5));
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, cen, 0.5f, 1.6f);
        });
        c.at(3, x -> {
            FxDraw.add(x, Prim.arc(cen, x.right, up, 2.0, 0.6, 3.8).color(GREEN).width(0.6).life(8).ease(Ease.OUT).glow(0.5).trail(0.5));
        });
        c.at(5, x -> {
            Vec3 f = x.feet();
            SkillFx.flash(x, cen);
            FxDraw.add(x, Prim.arc(cen, x.right, up, 2.0, 2.6, -0.6).color(WHITE).width(0.14).life(10).ease(Ease.OUT).alpha(0.9f));
            FxDraw.add(x, Prim.shell(f.add(0, 1.0, 0), 0.5, REND_RANGE, 2.0).color(GREEN).life(10).ease(Ease.OUT).width(0.3));
            SkillFx.kick(x, 0.6f);
            SkillFx.sound(x, SoundEvents.PLAYER_ATTACK_SWEEP, cen, 0.5f, 1.8f);
        });
        c.lasts(22);
    }

    // ================================================================ 灰の鍛え人

    // 武器を熾す [溜め 0-2 / 放ち 0-2 (三本の火柱) / 当たり 6 / 残り 40]
    // 形: 足もとのまわりに三本の熾の柱が 120 度ずつ昇る。当たりは足もとの輪と殻、熾が残る
    private static void temper(Cast c) {
        Vec3 f = c.feet();
        c.at(0, x -> SkillFx.burst(x, SkillFx.rune(), x.hand(), 3, 0.1, 0.02));
        for (int k = 0; k < 3; k++) {
            double ang = Math.PI * 2 * k / 3 + 0.5;
            int i = k;
            c.at(i, x -> {
                Vec3 p = f.add(Math.cos(ang) * 0.9, 0, Math.sin(ang) * 0.9);
                FxDraw.add(x, Prim.beam(p, 2.4).color(EMBER).width(0.45).life(16).ease(Ease.OUT3).glow(0.6));
            });
        }
        c.at(1, x -> SkillFx.sound(x, SoundEvents.ANVIL_USE, x.hand(), 0.35f, 1.4f));
        c.at(6, x -> {
            SkillFx.flash(x, f.add(0, 1.0, 0));
            FxDraw.add(x, Prim.ring(f, 0.5, 2.5).color(CINDER).width(0.3).life(10));
            FxDraw.add(x, Prim.shell(f.add(0, 1.0, 0), 0.5, 1.6, 1.6).color(EMBER).life(8).width(0.3));
            SkillFx.sound(x, SoundEvents.FIRECHARGE_USE, f, 0.4f, 1.3f);
        });
        c.at(10, x -> FxDraw.add(x, Prim.disc(f, 0.8, 1.6).color(EMBER).life(26).alpha(0.5f)));
        c.lasts(40);
    }

    // 熾の打ち下ろし [溜め 0-1 / 放ち 1-4 (熾の筋が前へ落ちる) / 当たり 5 / 残り 40]
    // 形: 頭の上から前 2 ブロックの地面へ熾の筋。着弾で熾の床 (半径 3.5) が残り、灰の輪 (半径 1.5) が広がる
    private static void emberHammer(Cast c) {
        Vec3 top = c.origin.add(0, 0.9, 0).add(c.fwd.scale(0.3));
        Vec3 strike = c.feet().add(c.fwd.scale(HAMMER_REACH)).add(0, 0.1, 0);
        Vec3 center = strike;
        c.at(0, x -> SkillFx.burst(x, SkillFx.rune(), top, 2, 0.1, 0.02));
        c.at(1, x -> FxDraw.add(x, Prim.line(top, strike).color(EMBER).width(0.5).life(4).ease(Ease.IN2).glow(0.6).trail(0.6)));
        c.at(5, x -> {
            SkillFx.flash(x, center.add(0, 0.5, 0));
            FxDraw.add(x, Prim.disc(center, 0.5, HAMMER_RADIUS).color(EMBER).life(40).alpha(0.65f).ease(Ease.OUT));
            FxDraw.add(x, Prim.ring(center, 0.6, 1.5).color(ASH).width(0.35).life(10));
            FxDraw.add(x, Prim.ring(center, 1.6, HAMMER_RADIUS).color(CINDER).width(0.2).life(12).alpha(0.8f));
            FxDraw.add(x, Prim.shell(center.add(0, 0.8, 0), 0.3, 1.5, 1.6).color(ASH).life(10).width(0.2));
            SkillFx.burst(x, SkillFx.rune(), center.add(0, 0.5, 0), 4, 0.6, 0.12);
            SkillFx.kick(x, 1.0f);
            SkillFx.sound(x, SoundEvents.ANVIL_LAND, center, 0.5f, 0.7f);
            SkillFx.sound(x, SoundEvents.FIRECHARGE_USE, center, 0.4f, 0.7f);
        });
        c.at(12, x -> FxDraw.add(x, Prim.disc(center, 1.0, HAMMER_RADIUS).color(ASH).life(24).alpha(0.3f)));
        c.lasts(40);
    }

    // 灰を被せる [溜め 0-7 (外から内へ閉じる熾の輪) / 当たり 8 / 残り 40]
    // 形: 半径 5 の熾の輪が 8 tick かけて内へ縮む。当たりで灰の輪が 2・3.5・5 と広がり、白い灰が降る
    private static void ashScatter(Cast c) {
        Vec3 base = c.feet();
        for (int i = 0; i < 8; i++) {
            c.at(i, x -> FxDraw.add(x, Prim.ring(base, ASH_RADIUS, 0.5).color(EMBER).width(0.2).life(8).alpha(0.5f).ease(Ease.IN2)));
        }
        c.at(8, x -> {
            SkillFx.flash(x, base.add(0, 1.0, 0));
            FxDraw.add(x, Prim.ring(base, 0.5, ASH_RADIUS).color(ASH).width(0.4).life(14).ease(Ease.OUT).glow(0.4));
            FxDraw.add(x, Prim.ring(base, 0.5, 3.5).color(CINDER).width(0.22).life(12));
            FxDraw.add(x, Prim.disc(base, 0.3, ASH_RADIUS).color(ASH).life(30).alpha(0.4f));
            SkillFx.burst(x, SkillFx.rune(), base.add(0, 2.5, 0), 4, 2.0, 0.02);
            SkillFx.kick(x, 0.7f);
            SkillFx.sound(x, SoundEvents.FIRE_EXTINGUISH, base, 0.5f, 0.6f);
            SkillFx.sound(x, SoundEvents.ENDER_DRAGON_FLAP, base, 0.35f, 0.6f);
        });
        c.at(12, x -> FxDraw.add(x, Prim.ring(base, 2.0, 3.5).color(ASH).width(0.2).life(20).alpha(0.5f)));
        c.lasts(40);
    }
}
