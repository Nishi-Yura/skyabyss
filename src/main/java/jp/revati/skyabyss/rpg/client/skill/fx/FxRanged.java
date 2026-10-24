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

// 骨・淵・前世の技の段取り (射撃・罠・淵・金の鐘)。
// 形は FxDraw の板で描く (主役)。粒は当たりの火花と煙だけ (脇役)。
// 色: 空 = 白 0xEAF6FF・空の青 0x9FD8FF / 骨 = 骨 0xE8DCC0・青緑 0x5FE3D0 / 淵 = 紫 0x9A4DFF・黒紫 0x2A0F3D・芯 0xC88CFF / 金 = 金 0xFFC94D・白金 0xFFE39A
// 形はカメラから 1.2 ブロック以上離して置く (一人称)。数はサーバーの SkillRules と同じ。
public final class FxRanged {
    private FxRanged() {
    }

    // 技の形 (SkillRules と同じ数・見た目だけの数)
    private static final double PIERCE_RANGE = SkillRules.PIERCE_LIFETIME * SkillRules.PIERCE_SPEED;
    private static final double RAIN_RADIUS = 4;
    private static final double PULL_RADIUS = 7;
    private static final double PULL_POINT = 3;
    private static final double MAW_RANGE = 10;
    private static final double MAW_RADIUS = 4;
    private static final double STRIKE_RANGE = 3.5;
    private static final double BELL_HEAD = 2.4;
    private static final double LIGHT_RADIUS = 5.5;

    // 色
    private static final int SKY_WHITE = 0xEAF6FF;
    private static final int SKY_BLUE = 0x9FD8FF;
    private static final int WHITE = 0xFFFFFF;
    private static final int BONE = 0xE8DCC0;
    private static final int TEAL = 0x5FE3D0;
    private static final int VIOLET = 0x9A4DFF;
    private static final int DEEP_VIOLET = 0x4D147A;
    private static final int DEEP_BLACK = 0x120818;
    private static final int DEEP_CORE = 0xC88CFF;
    private static final int GOLD = 0xFFC94D;
    private static final int PALE_GOLD = 0xFFE39A;
    private static final int SHADOW = 0x17222E;

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

    // 雲穿ち [溜め 0 / 放ち 1 (足もとの衝撃の輪・弓のまわりの雲の輪) / 飛ぶ 1〜 (矢の道に光の輪が奥へ連なる) / 当たり 壁 / 残り 34]
    // 形: 矢の道は視線の向きなので、後ろからは線が点に縮む。道に垂直な光の輪を、矢が通る順に奥へ並べる (後ろから見ると奥へ続く光の筒)。
    // 壁に当たればそこで閃く (矢の届く 108 ブロックの先は見えないので、壁が無ければ閃かない)
    private static void cloudPierce(Cast c) {
        Vec3 start = c.origin.add(c.look.scale(1.3));
        Vec3 right = c.right;
        Vec3 up = new Vec3(0, 1, 0);
        // 道に垂直な縦の向き (視線が上下を向いていても、輪が道に垂直になる)
        Vec3 ringUp = right.cross(c.look).normalize();
        if (ringUp.y < 0) {
            ringUp = ringUp.scale(-1);
        }
        Vec3 ringV = ringUp;
        Vec3 wall = wallAlong(c, PIERCE_RANGE);
        double reach = wall == null ? TUNNEL_LENGTH : Math.min(TUNNEL_LENGTH, wall.distanceTo(c.origin));
        c.at(0, x -> SkillFx.burst(x, SkillFx.rune(), x.hand(), 2, 0.2, 0.02));
        c.at(1, x -> {
            // 放つ: 弓のまわりに雲の輪 (視線に向く縦の輪が二重) と、足もとの衝撃の輪
            FxDraw.add(x, Prim.arc(start, right, up, 0.9, 0, Math.PI * 2).color(SKY_WHITE).width(0.4).life(12).ease(Ease.OUT).glow(0.5));
            FxDraw.add(x, Prim.arc(start, right, up, 1.7, 0, Math.PI * 2).color(SKY_BLUE).width(0.22).life(14).ease(Ease.OUT).alpha(0.7f));
            FxDraw.add(x, Prim.ring(x.feet().add(0, 0.05, 0), 0.6, 3.4).color(SKY_WHITE).width(0.4).life(10).ease(Ease.OUT).glow(0.5));
            FxDraw.add(x, Prim.line(start, x.aim(reach)).color(SKY_WHITE).width(0.6).life(8).ease(Ease.LINEAR).glow(0.9).trail(0.3));
            // 矢の道の影: 地面に、輪と同じ長さで暗い円盤 (背景の空と緑の上で、道の先まで読める)
            Vec3 foot = x.feet();
            for (int k = 1; k <= 6; k++) {
                Vec3 g = foot.add(x.fwd.scale(reach * k / 7.0)).add(0, 0.05, 0);
                FxDraw.add(x, Prim.disc(g, 0.6, 1.4).color(SHADOW).life(24).alpha(0.55f));
            }
            SkillFx.sound(x, SoundEvents.CROSSBOW_SHOOT, x.hand(), 0.45f, 1.7f);
        });
        // 光の輪: 矢が通る順に、2.5 ブロックおきに道へ垂直な輪 (奥ほど小さく見えて、筒になる)
        int rings = (int) Math.floor(reach / TUNNEL_STEP);
        for (int k = 1; k <= rings; k++) {
            double d = k * TUNNEL_STEP;
            Vec3 p = c.origin.add(c.look.scale(d));
            int at = 1 + (int) Math.round(d / SkillRules.PIERCE_SPEED);
            int color = k % 2 == 0 ? SKY_BLUE : SKY_WHITE;
            c.at(at, x -> FxDraw.add(x, Prim.arc(p, right, ringV, 1.3, 0, Math.PI * 2).color(color).width(0.4).life(22).ease(Ease.OUT).glow(0.9)));
        }
        if (wall != null) {
            int hitAt = 1 + (int) Math.ceil(wall.distanceTo(c.origin) / SkillRules.PIERCE_SPEED);
            c.at(hitAt, x -> {
                // 壁に当たった: 明るい柱と、まわりの縦の輪
                SkillFx.flash(x, wall);
                SkillFx.hitSpark(x, wall, SKY_WHITE);
                FxDraw.add(x, Prim.ring(wall, 0.2, 2.6).color(WHITE).width(0.35).life(12).glow(0.6));
                FxDraw.add(x, Prim.beam(wall, 6.0).color(SKY_WHITE).width(1.6).life(16).ease(Ease.OUT3).glow(1.0));
                FxDraw.add(x, Prim.arc(wall, right, ringV, 1.4, 0, Math.PI * 2).color(WHITE).width(0.35).life(14).ease(Ease.OUT).glow(0.5));
                SkillFx.sound(x, SoundEvents.ARROW_HIT, wall, 0.5f, 1.4f);
            });
        }
        c.lasts(34);
    }

    // 雲穿ちの光の輪を並べる長さと間 (見た目だけ。矢そのものは PIERCE_RANGE まで飛ぶ)
    private static final double TUNNEL_LENGTH = 22;
    private static final double TUNNEL_STEP = 2.5;

    // 視線の先の壁 (ブロック)。range の中に無ければ null
    @javax.annotation.Nullable
    private static Vec3 wallAlong(Cast c, double range) {
        net.minecraft.world.phys.BlockHitResult hit = c.level.clip(new net.minecraft.world.level.ClipContext(c.origin, c.aim(range),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, c.caster));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? null : hit.getLocation();
    }

    // 骨の罠 [溜め 0 / 放ち 0-8 (罠の輪が内へ閉じる・三本の光の柱が昇る) / 当たり 11 / 残り 44]
    // 形: 足もとに骨の輪 (踏む距離 2)。三本の青緑の柱が昇りながら、輪が半径 0.5 へ閉じる。閉じたところで弾ける
    private static void boneTrap(Cast c) {
        Vec3 base = c.feet();
        c.at(0, x -> {
            FxDraw.add(x, Prim.ring(base, 2.0, 2.0).color(BONE).width(0.2).life(12).alpha(0.9f));
            SkillFx.sound(x, SoundEvents.BONE_BLOCK_PLACE, base, 0.9f, 0.8f);
        });
        c.at(1, x -> {
            for (int k = 0; k < 3; k++) {
                double ang = Math.PI * 2 * k / 3;
                Vec3 p = base.add(Math.cos(ang) * 1.5, 0, Math.sin(ang) * 1.5);
                FxDraw.add(x, Prim.beam(p, 2.6).color(TEAL).width(0.7).life(10).ease(Ease.OUT));
            }
        });
        c.at(2, x -> FxDraw.add(x, Prim.ring(base, 2.0, 0.5).color(BONE).width(0.25).life(8).ease(Ease.IN2)));
        c.at(11, x -> {
            SkillFx.flash(x, base.add(0, 0.4, 0));
            FxDraw.add(x, Prim.disc(base, 0.3, 2.0).color(TEAL).life(10).alpha(0.7f));
            FxDraw.add(x, Prim.shell(base.add(0, 0.5, 0), 0.5, 2.0, 1.0).color(BONE).life(8).width(0.25));
            SkillFx.hitSpark(x, base.add(0, 0.5, 0), BONE);
            SkillFx.sound(x, SoundEvents.CHAIN_PLACE, base, 0.8f, 0.7f);
        });
        c.at(20, x -> FxDraw.add(x, Prim.ring(base, 2.0, 2.0).color(TEAL).width(0.14).life(24).alpha(0.3f)));
        c.lasts(44);
    }

    // 天の驟雨 [溜め 0 / 放ち 0-2 (降る所の輪・雲) / 当たり 一打ちごと 10 tick・8 回 / 残り 96]
    // 降る所はサーバーが決めた点 (狙った先の地面・途中の敵の足もと。合図の target)。古い合図なら手元で同じように求める。
    // 形: 降る所の地面に半径 4 の輪、上空 4.5 に雲の円盤 (三人称の後ろからも枠に入る高さ)。一打ちごとに光の筋が三本、雲から地面へ落ちて弾ける
    private static void skyRain(Cast c) {
        Vec3 center = c.target != null ? c.target : rainCenter(c);
        Vec3 cloud = center.add(0, RAIN_CLOUD, 0);
        c.at(0, x -> {
            SkillFx.sound(x, SoundEvents.ARROW_SHOOT, x.hand(), 0.6f, 1.4f);
            // 弓から降る所の雲へ、細い光の筋 (どこへ降らせたかが分かる)
            FxDraw.add(x, Prim.line(x.hand(), cloud).color(SKY_WHITE).width(0.25).life(10).ease(Ease.OUT).glow(0.6).trail(0.5));
            // 雲: 下が空の青、縁が白。下から見上げても、後ろから見ても面が読める
            FxDraw.add(x, Prim.disc(cloud, 0.5, RAIN_RADIUS + 1.5).color(SKY_BLUE).life(96).alpha(0.55f));
            FxDraw.add(x, Prim.ring(cloud, RAIN_RADIUS + 0.5, RAIN_RADIUS + 1.5).color(SKY_WHITE).width(0.7).life(96).alpha(0.75f).glow(0.4));
            // 降る所の輪 (地面)。敵がどこまで巻き込まれるか
            FxDraw.add(x, Prim.ring(center.add(0, 0.05, 0), RAIN_RADIUS, RAIN_RADIUS).color(SKY_WHITE).width(0.3).life(96).alpha(0.6f));
            // 雲から地面までの薄い幕 (雨の柱の形)
            FxDraw.add(x, Prim.shell(center, RAIN_RADIUS, RAIN_RADIUS, RAIN_CLOUD).color(SKY_BLUE).width(0.3).life(96).alpha(0.16f));
        });
        for (int i = 0; i < SkillRules.RAIN_PULSES; i++) {
            int at = 4 + i * SkillRules.RAIN_INTERVAL;
            for (int j = 0; j < 3; j++) {
                int n = i * 3 + j;
                double a = n * 2.399;
                double r = RAIN_RADIUS * Math.sqrt((n % 8 + 0.5) / 8.0);
                Vec3 spot = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                int fall = at + j * 2;
                c.at(fall, x -> {
                    // 太い光の筋 (雲から落ちる。後ろから見て細くならないよう幅 0.8)
                    FxDraw.add(x, Prim.line(spot.add(0, RAIN_CLOUD, 0), spot).color(SKY_WHITE).width(0.8).life(8).ease(Ease.LINEAR).glow(1.2).trail(0.7));
                });
                c.at(fall + 3, x -> {
                    // 着地: 地面に広がる輪と、短い光の柱
                    FxDraw.add(x, Prim.ring(spot.add(0, 0.05, 0), 0.3, 1.8).color(SKY_WHITE).width(0.35).life(8).ease(Ease.OUT).glow(0.4));
                    FxDraw.add(x, Prim.beam(spot, 1.4).color(SKY_WHITE).width(0.6).life(6).ease(Ease.OUT).glow(0.8));
                    SkillFx.burst(x, SkillFx.rune(), spot.add(0, 0.3, 0), 2, 0.3, 0.05);
                });
            }
            c.at(at, x -> SkillFx.sound(x, SoundEvents.ARROW_SHOOT, center, 0.3f, 1.8f));
        }
        c.at(4, x -> SkillFx.flash(x, center.add(0, 1.0, 0)));
        c.at(96 - 20, x -> FxDraw.add(x, Prim.shell(center, 0.5, RAIN_RADIUS, RAIN_CLOUD).color(SKY_BLUE).life(20).ease(Ease.OUT).width(0.4)));
        c.lasts(96);
    }

    // 天の驟雨の雲の高さ (見た目だけ)
    private static final double RAIN_CLOUD = 4.5;

    // 降る所を手元で求める (サーバーの SkillActions.rain と同じ: 視線の先 24 の壁か終わり、途中の敵の足もと、宙なら 8 下の地面)
    private static Vec3 rainCenter(Cast c) {
        Vec3 end = c.aim(SkillRules.RAIN_REACH);
        net.minecraft.world.phys.BlockHitResult block = c.level.clip(new net.minecraft.world.level.ClipContext(c.origin, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, c.caster));
        Vec3 center = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : block.getLocation();
        var entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(c.caster, c.origin, center,
                new net.minecraft.world.phys.AABB(c.origin, center).inflate(1.0),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive(), c.origin.distanceToSqr(center));
        if (entityHit != null) {
            return entityHit.getEntity().position();
        }
        net.minecraft.world.phys.BlockHitResult ground = c.level.clip(new net.minecraft.world.level.ClipContext(center.add(0, 0.5, 0), center.add(0, -8, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, c.caster));
        return ground.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? center : ground.getLocation();
    }

    // 淵の引き [溜め 0 / 放ち 1-6 (紫の輪が寄せ点へ縮む) / 当たり 6 / 残り 26]
    // 形: 足もとの半径 7 の紫の輪が、前 3 ブロックの寄せ点へ縮む。寄せ点に明るい芯と黒紫の殻、紫の渦が残る
    private static void deepPull(Cast c) {
        Vec3 point = c.feet().add(c.fwd.scale(PULL_POINT));
        c.at(0, x -> {
            FxDraw.add(x, Prim.ring(point, PULL_RADIUS, 0.5).color(VIOLET).width(0.45).life(6).ease(Ease.IN2).glow(0.6));
            // 引く柱: 寄せ点から上へ伸びる紫の幕 (後ろから見て分かる縦の形)
            FxDraw.add(x, Prim.beam(point, 3.4).color(VIOLET).width(1.5).life(12).ease(Ease.OUT3).alpha(0.8f));
            SkillFx.sound(x, SoundEvents.PORTAL_AMBIENT, point, 0.25f, 2.0f);
        });
        c.at(6, x -> {
            SkillFx.flash(x, point.add(0, 1.0, 0));
            FxDraw.add(x, Prim.disc(point, 0.3, 1.6).color(DEEP_CORE).life(10).alpha(0.9f));
            FxDraw.add(x, Prim.shell(point.add(0, 0.8, 0), 0.5, 3.0, 1.6).color(DEEP_BLACK).life(10).width(0.5));
            FxDraw.add(x, Prim.ring(point, 1.5, 2.8).color(VIOLET).width(0.3).life(26).alpha(0.4f));
            SkillFx.hitSpark(x, point, VIOLET);
            SkillFx.sound(x, SoundEvents.CHORUS_FRUIT_TELEPORT, point, 0.6f, 0.6f);
        });
        c.lasts(26);
    }

    // 淵の残響 [溜め 0-1 / 放ち 1-10 (黒い輪がこだまのように三回) / 残り 106 (渦が体について回る)]
    // 形: 足もとに黒紫の輪が三度 (半径 1.4 / 2.4 / 3.4)。そのあと腰のまわりを淡い紫の弧が回り、切れるとき消える
    private static void deepEcho(Cast c) {
        Vec3 f = c.feet();
        Vec3 waist = c.waist();
        c.at(1, x -> SkillFx.sound(x, SoundEvents.AMETHYST_BLOCK_RESONATE, f, 0.8f, 0.5f));
        double[] radii = { 1.4, 2.4, 3.4 };
        int[] ticks = { 2, 6, 10 };
        for (int i = 0; i < 3; i++) {
            double r = radii[i];
            c.at(ticks[i], x -> {
                FxDraw.add(x, Prim.ring(f, r, r + 1.0).color(DEEP_BLACK).width(0.35).life(12).ease(Ease.OUT).glow(0.3));
                FxDraw.add(x, Prim.ring(f, r - 0.2, r + 0.6).color(VIOLET).width(0.18).life(12).ease(Ease.OUT).alpha(0.7f));
            });
        }
        c.at(12, x -> {
            SkillFx.flash(x, waist);
            SkillFx.hitSpark(x, waist, DEEP_CORE);
            // 腰のまわりを回る紫の輪 (立った輪: 後ろから見ても円に見える)、その外に黒紫の柱
            FxDraw.add(x, Prim.arc(waist, new Vec3(1, 0, 0), new Vec3(0, 1, 0), 1.2, 0, Math.PI * 2).color(VIOLET).width(0.45).life(100).ease(Ease.LINEAR).trail(0.3).alpha(0.9f));
            FxDraw.add(x, Prim.beam(f, 3.0).color(DEEP_BLACK).width(1.2).life(20).ease(Ease.OUT3).alpha(0.8f));
            SkillFx.sound(x, SoundEvents.AMETHYST_BLOCK_RESONATE, f, 0.45f, 0.45f);
        });
        c.lasts(106);
    }

    // 淵の顎 [溜め 0-2 (手から狙いへ闇の糸) / 放ち 3-8 (狙いの黒い輪が縮む) / 噛む 8 (牙が縁から中心へ) / 残り 40]
    // 形: 手から狙いへ黒紫の糸。狙いの地面に半径 4 の黒い輪が 6 tick で縮み、噛む瞬間に縁から牙が立つ
    private static void deepMaw(Cast c) {
        Vec3 hand = c.hand();
        Vec3 aim = c.ahead(MAW_RANGE);
        c.at(0, x -> {
            FxDraw.add(x, Prim.line(hand, aim.add(0, 1.0, 0)).color(DEEP_VIOLET).width(0.16).life(4).ease(Ease.OUT).glow(0.3).trail(0.6));
            SkillFx.sound(x, SoundEvents.WARDEN_SONIC_CHARGE, x.feet(), 0.25f, 1.6f);
        });
        c.at(3, x -> FxDraw.add(x, Prim.ring(aim, MAW_RADIUS, 0.5).color(DEEP_BLACK).width(0.4).life(6).ease(Ease.IN2).glow(0.3)));
        c.at(8, x -> {
            SkillFx.flash(x, aim.add(0, 0.5, 0));
            SkillFx.hitSpark(x, aim.add(0, 0.5, 0), DEEP_CORE);
            // 顎: 上下の二つの弧 (縦の面、前から後ろへ見て口の形)。牙は縁から立つ
            // 紫の縁を先に描いて、夜の空でも顎の形が読めるように。黒は内側に重ねる
            FxDraw.add(x, Prim.arc(aim.add(0, 1.2, 0), x.fwd, new Vec3(0, 1, 0), 3.2, 0.5, 2.6).color(0xB58CFF).width(2.6).life(14).ease(Ease.OUT).glow(0.8).alpha(0.9f));
            FxDraw.add(x, Prim.arc(aim.add(0, 1.2, 0), x.fwd, new Vec3(0, 1, 0), 3.2, -0.5, -2.6).color(0xB58CFF).width(2.6).life(14).ease(Ease.OUT).glow(0.8).alpha(0.9f));
            FxDraw.add(x, Prim.arc(aim.add(0, 1.2, 0), x.fwd, new Vec3(0, 1, 0), 3.2, 0.5, 2.6).color(DEEP_BLACK).width(1.5).life(14).ease(Ease.OUT).alpha(0.95f));
            FxDraw.add(x, Prim.arc(aim.add(0, 1.2, 0), x.fwd, new Vec3(0, 1, 0), 3.2, -0.5, -2.6).color(DEEP_BLACK).width(1.5).life(14).ease(Ease.OUT).alpha(0.95f));
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                Vec3 edge = aim.add(Math.cos(a) * MAW_RADIUS, 0, Math.sin(a) * MAW_RADIUS);
                FxDraw.add(x, Prim.beam(edge, 2.2).color(0xB58CFF).width(1.5).life(14).ease(Ease.OUT).glow(0.5).alpha(0.9f));
            }
            FxDraw.add(x, Prim.shell(aim.add(0, 0.8, 0), 0.5, MAW_RADIUS, 1.8).color(DEEP_CORE).life(10).ease(Ease.OUT).width(0.3));
            SkillFx.kick(x, 0.9f);
            SkillFx.sound(x, SoundEvents.WARDEN_SONIC_BOOM, aim, 0.3f, 1.5f);
        });
        c.at(12, x -> FxDraw.add(x, Prim.disc(aim, 0.5, MAW_RADIUS).color(DEEP_BLACK).life(26).alpha(0.4f)));
        c.lasts(40);
    }

    // 遺響の一撃 [溜め 0 / 放ち 1-3 (金の三日月) / 相手 6-10 (淡い影の弧が寄る) / 残り 30]
    // 形: 前に金の三日月 (半径 2.6・-60〜+60 度)。少し後、相手の所に淡い影の弧が出て、手前へ寄る
    private static void echoStrike(Cast c) {
        Vec3 eye = c.origin;
        Vec3 target = c.aim(STRIKE_RANGE);
        Vec3 up = new Vec3(0, 1, 0);
        c.at(0, x -> FxDraw.add(x, Prim.line(x.hand(), target).color(GOLD).width(0.1).life(4).ease(Ease.OUT).alpha(0.6f)));
        c.at(1, x -> {
            // 縦の斜めの三日月 (前へ 2.4、半径 2.2、幅 0.8): 後ろから見ても太い弧に見える
            FxDraw.add(x, Prim.arc(eye.add(x.fwd.scale(2.4)), x.right, up, 2.2, 0.9, -0.9).color(GOLD).width(0.8).life(8).ease(Ease.OUT).glow(0.4).trail(0.6));
            SkillFx.sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, eye, 0.6f, 1.2f);
        });
        c.at(6, x -> {
            FxDraw.add(x, Prim.arc(target, x.fwd, up, 1.2, -Math.PI / 2, Math.PI / 2).color(DEEP_BLACK).width(0.25).life(8).ease(Ease.OUT).alpha(0.7f));
        });
        c.at(9, x -> {
            SkillFx.flash(x, target.add(0, 0.2, 0));
            SkillFx.hitSpark(x, target.add(0, 0.2, 0), GOLD);
            FxDraw.add(x, Prim.arc(eye.add(x.fwd.scale(2.4)), x.right, up, 2.2, -0.9, 0.9).color(PALE_GOLD).width(0.7).life(8).ease(Ease.OUT).glow(0.4).trail(0.6));
            SkillFx.sound(x, SoundEvents.AMETHYST_BLOCK_CHIME, target, 0.6f, 0.7f);
        });
        c.at(12, x -> FxDraw.add(x, Prim.ring(target.add(0, -1.0, 0), 0.5, 1.6).color(GOLD).width(0.2).life(12)));
        c.lasts(30);
    }

    // 墓鐘 [溜め 0-3 (頭の上に金の鐘) / 放ち 3 (打つ・鐘から足もとへ光の柱) / 当たり 3 (鐘の縁が響く) / 残り 64]
    // 形: 頭の上に金の鐘 (縁が広く、上が細い三段の輪)。打つと鐘から足もとへ光の柱、金の輪が 2.5 と 5 へ広がり、鐘の縁が二度響く
    private static void graveBell(Cast c) {
        Vec3 f = c.feet();
        Vec3 head = c.caster.position().add(0, BELL_HEAD, 0);
        c.at(0, x -> {
            FxDraw.add(x, Prim.ring(head.add(0, 0.5, 0), 0.6, 0.6).color(PALE_GOLD).width(0.12).life(8).alpha(0.9f));
            FxDraw.add(x, Prim.ring(head.add(0, 0.1, 0), 0.9, 0.9).color(PALE_GOLD).width(0.14).life(8).alpha(0.9f));
            FxDraw.add(x, Prim.ring(head.add(0, -0.3, 0), 1.2, 1.2).color(GOLD).width(0.16).life(8).alpha(0.9f));
        });
        c.at(3, x -> {
            SkillFx.flash(x, head);
            SkillFx.hitSpark(x, head, PALE_GOLD);
            FxDraw.add(x, Prim.beam(f, BELL_HEAD).color(GOLD).width(0.6).life(16).ease(Ease.OUT3).glow(0.5));
            FxDraw.add(x, Prim.ring(f, 0.5, 2.5).color(GOLD).width(0.4).life(14).ease(Ease.OUT).glow(0.4));
            FxDraw.add(x, Prim.ring(f, 0.5, 5.0).color(PALE_GOLD).width(0.3).life(18).ease(Ease.OUT));
            SkillFx.kick(x, 1.0f);
            SkillFx.sound(x, SoundEvents.BELL_BLOCK, head, 1.0f, 0.6f);
        });
        c.at(9, x -> {
            FxDraw.add(x, Prim.ring(f, 4.8, 5.0).color(GOLD).width(0.2).life(14).alpha(0.7f));
            SkillFx.sound(x, SoundEvents.BELL_RESONATE, f, 0.6f, 1.4f);
        });
        c.at(16, x -> FxDraw.add(x, Prim.ring(f, 4.8, 5.0).color(PALE_GOLD).width(0.15).life(16).alpha(0.4f)));
        c.lasts(64);
    }

    // 最期の光 [溜め 0 / 放ち 0 (白金の柱) / 当たり 3・6・9 (三つの輪が内から順に) / 残り 50]
    // 形: 足もとに白金の柱 (6 ブロック) と閃光。三つの輪 (半径 1.8・3.7・5.5) が 3 tick おきに広がり、外の輪に光の杭 8 本
    private static void finalLight(Cast c) {
        Vec3 f = c.feet();
        c.at(0, x -> {
            SkillFx.flash(x, f.add(0, 1.0, 0));
            SkillFx.hitSpark(x, f.add(0, 1.0, 0), PALE_GOLD);
            FxDraw.add(x, Prim.beam(f, 6.0).color(PALE_GOLD).width(0.9).life(40).ease(Ease.OUT3).glow(0.7));
            SkillFx.sound(x, SoundEvents.LIGHTNING_BOLT_THUNDER, f, 0.2f, 1.8f);
        });
        double[] radii = { 1.8, 3.7, LIGHT_RADIUS };
        for (int i = 0; i < 3; i++) {
            double r = radii[i];
            int outer = i == 2 ? 1 : 0;
            c.at(3 + 3 * i, x -> {
                FxDraw.add(x, Prim.ring(f, r - 0.8, r).color(outer == 1 ? WHITE : PALE_GOLD).width(0.4).life(14).ease(Ease.OUT).glow(0.4));
                if (x.self) {
                    SkillFx.kick(x, 0.4f);
                }
            });
        }
        c.at(12, x -> {
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                Vec3 p = f.add(Math.cos(a) * LIGHT_RADIUS, 0, Math.sin(a) * LIGHT_RADIUS);
                FxDraw.add(x, Prim.line(p, p.add(0, 2.5, 0)).color(GOLD).width(0.16).life(20).ease(Ease.OUT).glow(0.5));
            }
            SkillFx.kick(x, 1.0f);
            SkillFx.sound(x, SoundEvents.BELL_RESONATE, f, 0.6f, 1.4f);
        });
        c.lasts(50);
    }
}
