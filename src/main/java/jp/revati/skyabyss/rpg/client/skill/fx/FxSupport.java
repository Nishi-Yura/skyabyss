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

// 潮・旅人・橋・灰の技の段取り (歌と支援・合図・火の矢)。
// 形は FxDraw の板で描く (主役)。粒は当たりの火花だけ (脇役)。
// 色: 潮 = 海の青 0x58D6F0・泡 0xF2FFFF / 金 = 金 0xFFD868・白金 0xFFF0B8 / 砂 0xD9C28A / 熾 0xFF7A2A・火 0xFFC860 / 蒸気 0xDDE6EA
// 当たりの閃光は一つだけ。形はカメラから 1.2 ブロック以上離して置く (一人称)。
public final class FxSupport {
    private FxSupport() {
    }

    // 技の形 (SkillRules と同じ数)
    private static final double WARD_RADIUS = 8;
    private static final double EBB_RANGE = 6;
    private static final double EBB_HALF = 0.95;
    private static final double CYCLE_RADIUS = 8;

    // 色
    private static final int SEA = 0x58D6F0;
    private static final int FOAM = 0xF2FFFF;
    private static final int GOLD = 0xFFB43C;
    private static final int GOLD_WHITE = 0xFFF0B8;
    private static final int SAND = 0xD9C28A;
    private static final int EMBER = 0xFF7A2A;
    private static final int CINDER = 0xFFC860;
    private static final int STEAM = 0xDDE6EA;
    private static final int WHITE = 0xFFFFFF;
    private static final int ASH = 0xB8B0A8;

    public static void register(Map<Skill, Consumer<SkillFx.Cast>> plans) {
        plans.put(Skill.WARD_SONG, FxSupport::wardSong);
        plans.put(Skill.EBB_TIDE, FxSupport::ebbTide);
        plans.put(Skill.CYCLE_SONG, FxSupport::cycleSong);
    }

    // 守りの歌 [溜め 0 / 放ち 1-8 (泡の輪が広がる) / 当たり 8 / 残り 20]
    // 形: 足もとから海の青の柱が昇り、泡の輪が半径 8 へ。外へ衝撃の殻
    private static void wardSong(Cast c) {
        Vec3 base = c.feet();
        c.at(0, x -> FxDraw.add(x, Prim.beam(base, 2.2).color(SEA).width(0.8).life(14).ease(Ease.OUT3).glow(0.5)));
        c.at(1, x -> {
            FxDraw.add(x, Prim.ring(base, 1.0, WARD_RADIUS).color(FOAM).width(0.35).life(9).ease(Ease.OUT));
            FxDraw.add(x, Prim.ring(base, 0.5, WARD_RADIUS).color(SEA).width(0.25).life(12).ease(Ease.OUT).alpha(0.8f));
            SkillFx.sound(x, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, base, 0.7f, 0.8f);
        });
        c.at(8, x -> {
            SkillFx.flash(x, base.add(0, 1.0, 0));
            FxDraw.add(x, Prim.shell(base.add(0, 0.8, 0), 1.0, WARD_RADIUS, 2.0).color(SEA).life(10).ease(Ease.OUT).width(0.4));
            SkillFx.burst(x, SkillFx.rune(), base.add(0, 1.2, 0), 4, 0.8, 0.05);
            SkillFx.sound(x, SoundEvents.NOTE_BLOCK_CHIME.value(), base, 0.7f, 1.4f);
        });
        c.lasts(20);
    }

    // 引き潮 [溜め 0 / 放ち 1-6 (前の扇が広がる) / 当たり 6 / 残り 20]
    // 形: 前の扇 (半径 6) が広がり、その縁の輪が手前へ寄せる。寄せ点に海の円盤が残る
    private static void ebbTide(Cast c) {
        double dir = Math.atan2(c.fwd.z, c.fwd.x);
        Vec3 f = c.feet();
        c.at(0, x -> FxDraw.add(x, Prim.fan(f, dir, EBB_HALF, 0.5, EBB_RANGE).color(SEA).alpha(0.45f).life(10).ease(Ease.OUT)));
        c.at(1, x -> {
            SkillFx.sound(x, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, f, 0.5f, 1.1f);
        });
        c.at(6, x -> {
            Vec3 pull = f.add(x.fwd.scale(2.0));
            SkillFx.flash(x, pull.add(0, 0.5, 0));
            FxDraw.add(x, Prim.ring(f, EBB_RANGE, 1.5).color(FOAM).width(0.35).life(8).ease(Ease.IN2).glow(0.3));
            FxDraw.add(x, Prim.disc(pull, 0.5, 2.0).color(SEA).life(14).alpha(0.6f));
            SkillFx.hitSpark(x, pull, SEA);
            SkillFx.sound(x, SoundEvents.PLAYER_SPLASH, pull, 0.6f, 1.2f);
        });
        c.lasts(20);
    }

    // 循環の歌 [溜め 0 / 放ち 0-8 (二つの光が逆向きに 2 周) / 当たり 8 / 残り 24]
    // 形: 腰のまわりを二つの金の弧が逆向きに回る。当たりで金白の輪が半径 8 へ広がり、碑文の火花が昇る
    private static void cycleSong(Cast c) {
        Vec3 waist = c.waist();
        Vec3 ex = new Vec3(1, 0, 0);
        Vec3 ez = new Vec3(0, 0, 1);
        c.at(0, x -> {
            FxDraw.add(x, Prim.arc(waist, ex, ez, 1.2, 0, Math.PI * 2 * 2).color(GOLD).width(0.3).life(8).ease(Ease.LINEAR).trail(0.25).glow(0.5));
            FxDraw.add(x, Prim.arc(waist, ex, ez, 1.2, Math.PI * 2 * 2, 0).color(GOLD_WHITE).width(0.3).life(8).ease(Ease.LINEAR).trail(0.25).glow(0.5));
        });
        c.at(8, x -> {
            Vec3 f = x.feet();
            SkillFx.flash(x, waist);
            FxDraw.add(x, Prim.ring(f, 1.0, CYCLE_RADIUS).color(GOLD_WHITE).width(0.4).life(10).ease(Ease.OUT).glow(0.4));
            FxDraw.add(x, Prim.shell(waist, 0.8, CYCLE_RADIUS, 1.6).color(GOLD).life(8).width(0.25));
            SkillFx.burst(x, SkillFx.rune(), f.add(0, 1.0, 0), 6, 1.5, 0.06);
            SkillFx.sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, f, 0.8f, 1.2f);
        });
        c.at(16, x -> FxDraw.add(x, Prim.ring(x.feet(), CYCLE_RADIUS - 1.0, CYCLE_RADIUS).color(GOLD).width(0.2).life(10).alpha(0.7f)));
        c.lasts(24);
    }

}
