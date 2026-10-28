package jp.revati.skyabyss.rpg.client.skill.fx;

import java.util.Map;
import java.util.function.Consumer;

import jp.revati.skyabyss.rpg.client.skill.SkillFx;
import jp.revati.skyabyss.rpg.client.skill.SkillFx.Cast;
import jp.revati.skyabyss.rpg.client.skill.fx.FxDraw.Ease;
import jp.revati.skyabyss.rpg.client.skill.fx.FxDraw.Prim;
import jp.revati.skyabyss.rpg.skill.Skill;
import jp.revati.skyabyss.rpg.skill.SkillRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

// 職の新しい技 (docs/design/43。サーバーの中身は JobActions) の手元の段取り。形は FxDraw の板で描く (主役)、粒は脇役。
// 色: 星読み = 星の白 0xEAF0FF・夜の藍 0x6F7DFF・雷の黄 0xFFF0A0・熾の橙 0xFF8A3A・凍ての青 0xA8E6FF /
//     炉 = 橙 0xFF7A2A / 霧 = 灰白 0xD8DEE6 / 呪い = 紫 0xB05CFF / 灯 = 灯の金 0xFFD98A
// 盾の踏み込み・射手の跳び退きは、風読みの踏み込み・跳び退きと同じ段取り (同じ動きの技)
public final class FxJobs {
    private FxJobs() {
    }

    private static final int STAR_WHITE = 0xEAF0FF;
    private static final int NIGHT = 0x6F7DFF;
    private static final int BOLT = 0xFFF0A0;
    private static final int EMBER = 0xFF8A3A;
    private static final int FROST = 0xA8E6FF;
    private static final int FORGE = 0xFF7A2A;
    private static final int MIST = 0xD8DEE6;
    private static final int CURSE = 0xB05CFF;
    private static final int LAMP = 0xFFD98A;

    public static void register(Map<Skill, Consumer<Cast>> plans) {
        plans.put(Skill.THUNDER_BOLT, FxJobs::thunderBolt);
        plans.put(Skill.EMBER_ORB, FxJobs::emberOrb);
        plans.put(Skill.FROST_TIDE, FxJobs::frostTide);
        plans.put(Skill.BLINK, FxJobs::blink);
        plans.put(Skill.FORGE_STOMP, FxJobs::forgeStomp);
        plans.put(Skill.MIST_VEIL, FxJobs::mistVeil);
        plans.put(Skill.CURSE_MARK, FxJobs::curseMark);
        plans.put(Skill.LAMP_CHARM, FxJobs::lampCharm);
        if (plans.containsKey(Skill.LUNGE)) {
            plans.put(Skill.SHIELD_RUSH, plans.get(Skill.LUNGE));
        }
        if (plans.containsKey(Skill.BACKSTEP)) {
            plans.put(Skill.ARCHER_LEAP, plans.get(Skill.BACKSTEP));
        }
        // 木 v2 で足した技 (docs/design/48): 似た技の絵を借りる
        Skill[][] borrow = {{Skill.SHIELD_BASH, Skill.LUNGE}, {Skill.BONE_SWING, Skill.TITAN_CLEAVE}, {Skill.SPIN_CUT, Skill.WHALE_SWEEP},
                {Skill.TWIN_FANG, Skill.WIND_REND}, {Skill.SPARK_BURST, Skill.FORGE_STOMP}, {Skill.SNARE_SHOT, Skill.CLOUD_PIERCE}, {Skill.STARDUST, Skill.FROST_TIDE}};
        for (Skill[] b : borrow) {
            if (plans.containsKey(b[1])) {
                plans.put(b[0], plans.get(b[1]));
            }
        }
    }

    // 雷の矢 [放ち 0 (手元の閃きと、狙いへ折れ曲がる稲妻) / 当たり 1 (当たりの火花) / 残り 10]
    private static void thunderBolt(Cast c) {
        Vec3 end = c.target != null ? c.target : c.aim(SkillRules.BOLT_RANGE);
        Vec3 from = c.hand();
        c.at(0, x -> {
            FxDraw.add(x, Prim.bolt(from, end, 0.5, x.caster.getId() * 31L + x.level.getGameTime()).color(BOLT).width(0.18).life(7).ease(Ease.OUT).glow(0.9));
            FxDraw.add(x, Prim.bolt(from, end, 0.8, x.level.getGameTime()).color(STAR_WHITE).width(0.08).life(5).ease(Ease.OUT).glow(0.7));
            SkillFx.flash(x, from);
            SkillFx.sound(x, SoundEvents.LIGHTNING_BOLT_IMPACT, from, 0.4f, 1.9f);
        });
        c.at(1, x -> {
            SkillFx.hitSpark(x, end, BOLT);
            FxDraw.add(x, Prim.ring(end, 0.2, 1.2).color(BOLT).width(0.2).life(8).ease(Ease.OUT).glow(0.6));
        });
        c.lasts(10);
    }

    // 熾火の玉 [溜め 0 (手元に火の輪) / 放ち 2 (前へ火の尾) / 残り 14]。玉そのものと弾けはサーバーの粒
    private static void emberOrb(Cast c) {
        Vec3 hand = c.hand();
        c.at(0, x -> {
            FxDraw.add(x, Prim.arc(hand, x.right, new Vec3(0, 1, 0), 0.5, 0, Math.PI * 2).color(EMBER).width(0.25).life(8).ease(Ease.OUT).glow(0.6));
            SkillFx.burst(x, ParticleTypes.FLAME, hand, 6, 0.15, 0.02);
        });
        c.at(2, x -> {
            FxDraw.add(x, Prim.line(hand, x.aim(6)).color(EMBER).width(0.35).life(10).ease(Ease.OUT).trail(0.6).glow(0.5));
            SkillFx.sound(x, SoundEvents.BLAZE_SHOOT, hand, 0.5f, 1.2f);
        });
        c.lasts(14);
    }

    // 凍て潮 [放ち 0 (足もとから冷たい輪が二重に広がる) / 残り 18 (霜の円盤)]
    private static void frostTide(Cast c) {
        Vec3 f = c.feet();
        double r = SkillRules.frostRadius(Math.max(1, c.rank));
        c.at(0, x -> {
            FxDraw.add(x, Prim.ring(f, 0.5, r).color(FROST).width(0.45).life(10).ease(Ease.OUT).glow(0.5));
            FxDraw.add(x, Prim.ring(f, 0.3, r * 0.7).color(STAR_WHITE).width(0.25).life(12).ease(Ease.OUT));
            SkillFx.sound(x, SoundEvents.GLASS_BREAK, f, 0.5f, 1.6f);
        });
        c.at(3, x -> FxDraw.add(x, Prim.disc(f, 0.2, r).color(FROST).alpha(0.35f).life(16).ease(Ease.SMOOTH)));
        c.lasts(18);
    }

    // 転移 [立ち去り 0 (元の所に夜の藍の柱) / 着き 1 (着いた所に星の白の柱と輪)]
    private static void blink(Cast c) {
        Vec3 from = c.feet();
        c.at(0, x -> FxDraw.add(x, Prim.beam(from, 2.0).color(NIGHT).width(0.7).life(10).ease(Ease.OUT3).glow(0.6)));
        c.at(1, x -> {
            Vec3 to = x.target != null ? x.target : x.feet();
            FxDraw.add(x, Prim.beam(to, 2.0).color(STAR_WHITE).width(0.6).life(10).ease(Ease.OUT3).glow(0.7));
            FxDraw.add(x, Prim.ring(to.add(0, 0.05, 0), 0.3, 1.6).color(NIGHT).width(0.3).life(9).ease(Ease.OUT));
            FxDraw.add(x, Prim.line(from.add(0, 1.0, 0), to.add(0, 1.0, 0)).color(NIGHT).width(0.12).life(6).ease(Ease.OUT).alpha(0.6f));
        });
        c.lasts(12);
    }

    // 炉の踏み [踏み 0 (足もとの橙の輪が三重に走る・割れ目の円盤) / 残り 16]
    private static void forgeStomp(Cast c) {
        Vec3 f = c.feet();
        c.at(0, x -> {
            for (int i = 0; i < 3; i++) {
                FxDraw.add(x, Prim.ring(f, 0.4 + i * 0.4, SkillRules.STOMP_RADIUS - i * 0.6).color(FORGE).width(0.4 - i * 0.08).life(8 + i * 3).ease(Ease.OUT).glow(0.6));
            }
            FxDraw.add(x, Prim.disc(f, 0.2, SkillRules.STOMP_RADIUS).color(0x3A1408).alpha(0.5f).life(16).ease(Ease.SMOOTH));
            SkillFx.kick(x, 3f);
        });
        c.lasts(16);
    }

    // 霧隠れ [包み 0 (体のまわりに灰白の殻と足もとの霧の円盤) / 残り 14]
    private static void mistVeil(Cast c) {
        Vec3 f = c.feet();
        c.at(0, x -> {
            FxDraw.add(x, Prim.shell(f.add(0, 0.9, 0), 0.4, 1.3, 2.0).color(MIST).width(0.3).life(12).ease(Ease.OUT).alpha(0.7f));
            FxDraw.add(x, Prim.disc(f, 0.2, 2.2).color(MIST).alpha(0.4f).life(14).ease(Ease.SMOOTH));
        });
        c.lasts(14);
    }

    // 呪いの印 [放ち 0 (手元から紫の線) / 印 2 (敵の足もとに紫の輪二重)]
    private static void curseMark(Cast c) {
        Vec3 hand = c.hand();
        Vec3 at = c.target != null ? c.target : c.aim(8);
        c.at(0, x -> FxDraw.add(x, Prim.line(hand, at).color(CURSE).width(0.15).life(8).ease(Ease.OUT).glow(0.5)));
        c.at(2, x -> {
            FxDraw.add(x, Prim.ring(at, 0.6, 1.2).color(CURSE).width(0.25).life(16).ease(Ease.SMOOTH).glow(0.4));
            FxDraw.add(x, Prim.ring(at, 1.4, 0.4).color(0x2A0F3D).width(0.2).life(12).ease(Ease.IN2));
        });
        c.lasts(18);
    }

    // 灯の守り札 [置き 0 (灯の金の柱) / 広がり 2 (守りの輪)]。場の灯りは毎秒サーバーの粒
    private static void lampCharm(Cast c) {
        Vec3 f = c.feet();
        c.at(0, x -> FxDraw.add(x, Prim.beam(f, 1.6).color(LAMP).width(0.5).life(14).ease(Ease.OUT3).glow(0.7)));
        c.at(2, x -> {
            FxDraw.add(x, Prim.ring(f, 0.5, SkillRules.LAMP_RADIUS).color(LAMP).width(0.3).life(14).ease(Ease.OUT).glow(0.4));
            FxDraw.add(x, Prim.shell(f.add(0, 0.5, 0), 0.5, SkillRules.LAMP_RADIUS, 1.6).color(LAMP).width(0.25).life(12).ease(Ease.OUT).alpha(0.5f));
        });
        c.lasts(18);
    }
}
