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
    private static final double SIGNAL_RANGE = 32;
    private static final double SIGNAL_HEIGHT = 14;
    private static final double DUST_LEN = 6;
    private static final double STEAM_RADIUS = 4;
    private static final double VOLLEY_LEN = 14;

    // 色
    private static final int SEA = 0x58D6F0;
    private static final int FOAM = 0xF2FFFF;
    private static final int GOLD = 0xFFD868;
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
        plans.put(Skill.SIGNAL, FxSupport::signal);
        plans.put(Skill.DUST_CHARGE, FxSupport::dustCharge);
        plans.put(Skill.STEAM_BELL, FxSupport::steamBell);
        plans.put(Skill.CINDER_VOLLEY, FxSupport::cinderVolley);
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
            SkillFx.burst(x, SkillFx.rune(), pull, 3, 0.4, 0.08);
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

    // 合図の標 [溜め 0 / 放ち 1-6 (金の線が先へ飛ぶ) / 当たり 6 / 残り 36]
    // 形: 手から視線の先 32 へ金の線。着いたところに高さ 14 の金の柱と、金の輪、花火。柱は残る
    private static void signal(Cast c) {
        Vec3 hand = c.hand();
        Vec3 spot = c.aim(SIGNAL_RANGE);
        c.at(0, x -> SkillFx.burst(x, SkillFx.rune(), x.hand(), 3, 0.1, 0.02));
        c.at(1, x -> {
            FxDraw.add(x, Prim.line(hand, spot).color(GOLD).width(0.22).life(6).ease(Ease.OUT).glow(0.5).trail(0.3));
            SkillFx.sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, x.hand(), 0.6f, 1.6f);
        });
        c.at(6, x -> {
            SkillFx.flash(x, spot);
            // 太い金の柱 (遠くから見えるように幅 2.4) と、その上に昇る輪
            FxDraw.add(x, Prim.beam(spot, SIGNAL_HEIGHT).color(GOLD).width(2.4).life(36).ease(Ease.OUT3).alpha(0.9f));
            FxDraw.add(x, Prim.beam(spot, SIGNAL_HEIGHT * 0.6).color(GOLD_WHITE).width(0.9).life(36).ease(Ease.OUT3));
            FxDraw.add(x, Prim.ring(spot.add(0, SIGNAL_HEIGHT * 0.5, 0), 1.0, 3.5).color(GOLD).width(0.5).life(30).ease(Ease.OUT));
            FxDraw.add(x, Prim.ring(spot, 0.5, 4.0).color(GOLD).width(0.35).life(12).ease(Ease.OUT).glow(0.4));
            FxDraw.add(x, Prim.disc(spot, 0.3, 1.8).color(GOLD_WHITE).life(12).alpha(0.7f));
            SkillFx.burst(x, SkillFx.rune(), spot.add(0, SIGNAL_HEIGHT, 0), 5, 1.2, 0.08);
            SkillFx.sound(x, SoundEvents.BEACON_ACTIVATE, spot, 0.6f, 1.6f);
        });
        c.at(16, x -> FxDraw.add(x, Prim.ring(spot, 1.0, 5.0).color(GOLD).width(0.2).life(14).alpha(0.6f)));
        c.lasts(36);
    }

    // 砂の突進 [溜め 0 / 放ち 1-3 (砂の帯と風の線が前へ伸びる) / 当たり 3 / 残り 28]
    // 形: 通った道に砂色の太い帯 (6 ブロック)、その両脇に細い白い風の線。先で砂の殻が広がる
    private static void dustCharge(Cast c) {
        Vec3 from = c.feet().add(0, 0.2, 0);
        Vec3 to = c.feet().add(c.fwd.scale(DUST_LEN)).add(0, 0.2, 0);
        Vec3 side = c.right.scale(0.8);
        c.at(0, x -> SkillFx.burst(x, SkillFx.rune(), x.hand(), 2, 0.2, 0.02));
        c.at(1, x -> {
            FxDraw.add(x, Prim.line(from, to).color(SAND).width(1.2).life(14).ease(Ease.OUT).alpha(0.55f).trail(0.6));
            // 砂けむりの柱が道に沿って三本 (背の高さ、後ろから見て分かるように)
            for (int k = 1; k <= 3; k++) {
                Vec3 g = from.lerp(to, k / 4.0);
                FxDraw.add(x, Prim.beam(g, 2.6).color(SAND).width(1.3).life(14).ease(Ease.OUT).alpha(0.7f));
            }
            FxDraw.add(x, Prim.line(from.add(side), to.add(side)).color(WHITE).width(0.08).life(10).ease(Ease.OUT).alpha(0.8f).trail(0.4));
            FxDraw.add(x, Prim.line(from.subtract(side), to.subtract(side)).color(WHITE).width(0.08).life(10).ease(Ease.OUT).alpha(0.8f).trail(0.4));
            SkillFx.sound(x, SoundEvents.SAND_BREAK, x.feet(), 0.7f, 0.8f);
        });
        c.at(3, x -> {
            SkillFx.flash(x, to.add(0, 0.6, 0));
            FxDraw.add(x, Prim.shell(to, 0.5, 3.0, 1.5).color(SAND).life(10).ease(Ease.OUT).width(0.3));
            FxDraw.add(x, Prim.disc(to, 0.3, 1.6).color(SAND).life(10).alpha(0.6f));
            SkillFx.kick(x, 0.6f);
            SkillFx.sound(x, SoundEvents.BREEZE_JUMP, to, 0.7f, 0.9f);
        });
        c.lasts(28);
    }

    // 蒸気の鐘 [溜め 0-3 (頭の上に鐘の形) / 放ち 3 (鐘が鳴る) / 当たり 6 / 残り 34]
    // 形: 頭の上に蒸気の鐘 (縁が熾の輪、上が小さい三段の輪)。鳴ると足もとに蒸気と熾の二重の輪が半径 4
    private static void steamBell(Cast c) {
        Vec3 f = c.feet();
        Vec3 head = c.caster.position().add(0, 2.4, 0);
        c.at(0, x -> {
            FxDraw.add(x, Prim.ring(head.add(0, 0.5, 0), 0.6, 0.6).color(STEAM).width(0.12).life(8).alpha(0.9f));
            FxDraw.add(x, Prim.ring(head.add(0, 0.1, 0), 0.9, 0.9).color(STEAM).width(0.14).life(8).alpha(0.9f));
            FxDraw.add(x, Prim.ring(head.add(0, -0.3, 0), 1.2, 1.2).color(EMBER).width(0.16).life(8).alpha(0.9f));
        });
        c.at(3, x -> {
            SkillFx.flash(x, head);
            FxDraw.add(x, Prim.ring(f, 0.5, STEAM_RADIUS).color(STEAM).width(0.5).life(20).ease(Ease.OUT).glow(0.4));
            FxDraw.add(x, Prim.ring(f, 0.5, STEAM_RADIUS - 0.5).color(EMBER).width(0.3).life(16).ease(Ease.OUT));
            FxDraw.add(x, Prim.shell(f.add(0, 1.0, 0), 0.5, STEAM_RADIUS, 2.2).color(STEAM).life(14).ease(Ease.OUT).width(0.3));
            SkillFx.kick(x, 0.7f);
            SkillFx.sound(x, SoundEvents.BELL_BLOCK, head, 0.9f, 0.6f);
        });
        c.at(6, x -> {
            FxDraw.add(x, Prim.disc(f, 0.5, STEAM_RADIUS).color(STEAM).life(30).alpha(0.35f));
            SkillFx.burst(x, SkillFx.rune(), f.add(0, 1.2, 0), 5, 1.2, 0.05);
            SkillFx.sound(x, SoundEvents.BELL_RESONATE, f, 0.4f, 1.0f);
        });
        c.lasts(34);
    }

    // 火の矢の一斉 [溜め 0 / 放ち 1-5 (五本が左から一本ずつ) / 当たり 7 / 残り 30]
    // 形: 手から五本の熾の筋 (-18・-9・0・+9・+18 度)、芯は白い。着いたところに小さな熾の円盤、灰が残る
    private static void cinderVolley(Cast c) {
        Vec3 hand = c.hand();
        double[] degs = { -18, -9, 0, 9, 18 };
        c.at(0, x -> SkillFx.burst(x, SkillFx.rune(), x.hand(), 3, 0.1, 0.02));
        for (int i = 0; i < degs.length; i++) {
            int k = i;
            c.at(1 + i, x -> {
                Vec3 d = rotate(x.fwd, degs[k]);
                Vec3 end = hand.add(d.scale(VOLLEY_LEN));
                FxDraw.add(x, Prim.line(hand, end).color(EMBER).width(0.26).life(8).ease(Ease.OUT).glow(0.6).trail(0.25));
                FxDraw.add(x, Prim.line(hand, end).color(WHITE).width(0.08).life(8).ease(Ease.OUT).trail(0.25));
                SkillFx.sound(x, SoundEvents.ARROW_SHOOT, hand, 0.4f, 0.8f + k * 0.05f);
            });
        }
        c.at(7, x -> {
            SkillFx.flash(x, hand.add(x.fwd.scale(VOLLEY_LEN)));
            for (double deg : degs) {
                Vec3 end = hand.add(rotate(x.fwd, deg).scale(VOLLEY_LEN));
                FxDraw.add(x, Prim.disc(end, 0.3, 1.2).color(CINDER).life(10).alpha(0.7f));
            }
            FxDraw.add(x, Prim.ring(x.feet(), 0.5, 2.5).color(ASH).width(0.2).life(14).alpha(0.6f));
            SkillFx.kick(x, 0.8f);
            SkillFx.sound(x, SoundEvents.GENERIC_EXTINGUISH_FIRE, x.feet(), 0.4f, 1.4f);
        });
        c.lasts(30);
    }

    // 水平に角度を回す (y 軸まわり・度)
    private static Vec3 rotate(Vec3 v, double deg) {
        double r = Math.toRadians(deg);
        double cs = Math.cos(r);
        double sn = Math.sin(r);
        return new Vec3(v.x * cs - v.z * sn, 0, v.x * sn + v.z * cs).normalize();
    }
}
