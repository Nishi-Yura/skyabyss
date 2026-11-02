package jp.revati.skyabyss.story.ch5.layers;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 層の主の技 (2026-10-23 の試遊「5章の中ボス系全然作りこめてない攻撃やボスバーも」)。主ごとに、教える仕掛け (AbyssLords の骨・塩・雷・灰) のほかに
// 段で増える 4〜5 の技を持つ。一撃は 2026-10-25 に 3.2 倍 (試遊で「深淵のボスが弱すぎる」。五章の装備で傷にならなかった)。どれも床の赤い予告 (Telegraph の DANGER) と溜め (主が止まり、層の色の粒と音) のあとに当たる。
//   段     体力 66% 超 = 一段目 (技 2〜3)、33% 超 = 二段目 (+1、間隔が詰まる)、33% 以下 = 三段目 (+1、もっと詰まる)。段の変わり目は咆哮 (AbyssLords.phaseShift)
//   間隔   技と技のあいだ 70 / 55 / 42 tick (+ 溜め)。教える仕掛けが出た直後は 50 tick 待つ
//   予告   20〜38 tick。三段目でも 18 を切らない。当たりは予告の最後の一瞬
//   技の名前は LordAnim のとおり (見た目の担当が溜めと当たりの動きをつける)
public final class LordMoves {
    // 技の型
    enum Kind {
        CONE, LINE, SLAM, RING, RAIN, UNDER, CROSS, CHARGE, PILLARS
    }

    record Attack(String anim, Kind kind, int warn, float dmg, double size, double size2, int minPhase) {
    }

    // 層ごとの技 (並び = 使い始める段の順)
    static final Attack[][] MOVES = {
            // 骨: 肋の主 (骨と魂の火)
            {new Attack("rib_sweep", Kind.CONE, 22, 22.5f, 6.5, Math.toRadians(55), 0),
                    new Attack("bone_spikes", Kind.LINE, 26, 25.5f, 15, 1.3, 0),
                    new Attack("marrow_quake", Kind.SLAM, 30, 29f, 5.5, 0, 1),
                    new Attack("bone_rain", Kind.RAIN, 30, 19f, 2.2, 7, 2)},
            // 塩: 白殻の主 (塩と潮)
            {new Attack("shell_slam", Kind.SLAM, 28, 29f, 4.5, 0, 0),
                    new Attack("brine_spray", Kind.CONE, 24, 19f, 9, Math.toRadians(35), 0),
                    new Attack("salt_pillars", Kind.PILLARS, 22, 22.5f, 2.2, 4, 0),
                    new Attack("tide_ring", Kind.RING, 36, 25.5f, 5, 12, 1),
                    new Attack("shell_roll", Kind.CHARGE, 26, 32f, 16, 1.8, 2)},
            // 雷: 鼓動まねの主 (雷)
            {new Attack("jolt_line", Kind.LINE, 22, 22.5f, 15, 1.2, 0),
                    new Attack("static_field", Kind.UNDER, 30, 19f, 2.4, 0, 0),
                    new Attack("thunder_cross", Kind.CROSS, 30, 25.5f, 13, 1.3, 1),
                    new Attack("chain_burst", Kind.SLAM, 26, 29f, 6, 0, 2)},
            // 灰: 灯り喰いの主 (灰と熾火)
            {new Attack("ember_claw", Kind.CONE, 20, 25.5f, 6, Math.toRadians(48), 0),
                    new Attack("ash_breath", Kind.CONE, 30, 22.5f, 12, Math.toRadians(20), 0),
                    new Attack("cinder_rain", Kind.RAIN, 30, 19f, 2.2, 6, 1),
                    new Attack("magma_burst", Kind.RING, 32, 29f, 3, 8, 1),
                    new Attack("lunge", Kind.CHARGE, 24, 32f, 14, 2, 2)},
    };
    static final int[] GAP = {70, 55, 42};
    static final int MIN_WARN = 18;

    private LordMoves() {
    }

    // 段で使える技
    static List<Attack> pool(int li, int phase) {
        List<Attack> out = new ArrayList<>();
        for (Attack a : MOVES[li]) {
            if (a.minPhase() <= phase) {
                out.add(a);
            }
        }
        return out;
    }

    static int warnOf(Attack a, int phase) {
        return Math.max(MIN_WARN, a.warn() - 3 * phase);
    }

    // 主の戦いの 1 tick (AbyssLords.tick から)。溜めの間は主を止める
    static void tick(ServerLevel level, AbyssLords.Fight f, LivingEntity lord, List<ServerPlayer> in, long now) {
        if (f.windupUntil > now) {
            hold(lord);
            if (now % 4 == 0) {
                element(level, f.li, lord.getX(), lord.getY() + lord.getBbHeight() * 0.6, lord.getZ(), 3, 0.5);
            }
            return;
        }
        if (now < f.nextMove || in.isEmpty() || f.roarUntil > now) {
            return;
        }
        List<Attack> pool = pool(f.li, f.phase);
        // 同じ技を続けない
        Attack a = pool.get(f.rng.nextInt(pool.size()));
        if (pool.size() > 1 && a.anim().equals(f.lastMove)) {
            a = pool.get((pool.indexOf(a) + 1) % pool.size());
        }
        ServerPlayer target = nearest(lord, in);
        if (target == null) {
            return;
        }
        int warn = warnOf(a, f.phase);
        start(level, f, lord, target, in, a, warn);
        f.lastMove = a.anim();
        f.windupUntil = now + warn;
        f.nextMove = now + warn + GAP[Math.min(2, f.phase)];
        f.moves++;
    }

    // 技を始める (予告を置き、溜めの合図)。自己テストからも呼ぶ
    static void start(ServerLevel level, AbyssLords.Fight f, LivingEntity lord, ServerPlayer target, List<ServerPlayer> in, Attack a, int warn) {
        double y = floorY(f, lord);
        Vec3 o = lord.position();
        double ang = Math.atan2(target.getZ() - o.z, target.getX() - o.x);
        float dmg = a.dmg() * (1f + 0.15f * f.phase);
        lord.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        LordAnim.windup(lord, "attack_" + a.anim(), warn);
        level.playSound(null, lord.blockPosition(), windSound(f.li), SoundSource.HOSTILE, 1.4f, 0.7f + 0.1f * f.phase);
        Telegraph.Hit hit = (lvl, p) -> hurt(lvl, f, lord, p, dmg, a);
        switch (a.kind()) {
            case CONE -> tele(level, TelegraphShape.cone(o.x, o.z, ang, a.size(), a.size2()), y, warn, lord, hit,
                    lvl -> impact(lvl, f, lord, a, o.x + Math.cos(ang) * a.size() * 0.6, y, o.z + Math.sin(ang) * a.size() * 0.6, a.size() * 0.5));
            case LINE -> tele(level, TelegraphShape.line(o.x, o.z, ang, a.size(), a.size2()), y, warn, lord, hit,
                    lvl -> lineFx(lvl, f, lord, a, o, ang, a.size(), y));
            case SLAM -> tele(level, TelegraphShape.circle(o.x, o.z, a.size()), y, warn, lord, hit,
                    lvl -> impact(lvl, f, lord, a, o.x, y, o.z, a.size()));
            case RING -> tele(level, TelegraphShape.ring(o.x, o.z, a.size(), a.size2()), y, warn, lord, hit,
                    lvl -> ringFx(lvl, f, lord, a, o, y));
            case UNDER -> {
                for (ServerPlayer p : in) {
                    Vec3 c = p.position();
                    tele(level, TelegraphShape.circle(c.x, c.z, a.size()), y, warn, lord, hit, lvl -> impact(lvl, f, lord, a, c.x, y, c.z, a.size()));
                }
            }
            case RAIN -> {
                BlockPos ac = AbyssBuild.mark("arena_" + f.layer);
                int n = (int) a.size2() + f.phase;
                for (int k = 0; k < n; k++) {
                    double x, z;
                    if (k < in.size()) {
                        x = in.get(k).getX();
                        z = in.get(k).getZ();
                    } else {
                        double r = 2 + f.rng.nextDouble() * (Layers.ARENA_R - 4);
                        double t = f.rng.nextDouble() * Math.PI * 2;
                        x = (ac != null ? ac.getX() + 0.5 : o.x) + Math.cos(t) * r;
                        z = (ac != null ? ac.getZ() + 0.5 : o.z) + Math.sin(t) * r;
                    }
                    int w = warn + (k % 3) * 6;
                    double fx = x, fz = z;
                    tele(level, TelegraphShape.circle(x, z, a.size()), y, w, lord, hit, lvl -> impact(lvl, f, lord, a, fx, y, fz, a.size()));
                }
            }
            case CROSS -> {
                double base = f.phase >= 2 && f.moves % 2 == 1 ? Math.PI / 4 : 0;
                for (int k = 0; k < 4; k++) {
                    double t = base + k * Math.PI / 2;
                    tele(level, TelegraphShape.line(o.x, o.z, t, a.size(), a.size2()), y, warn, lord, hit, lvl -> lineFx(lvl, f, lord, a, o, t, a.size(), y));
                }
            }
            case CHARGE -> tele(level, TelegraphShape.line(o.x, o.z, ang, a.size(), a.size2()), y, warn, lord, hit, lvl -> {
                // 予告の線の先まで一気に (間の外へは出ない)
                Vec3 end = clampToArena(f, new Vec3(o.x + Math.cos(ang) * (a.size() - 1), y, o.z + Math.sin(ang) * (a.size() - 1)));
                lineFx(lvl, f, lord, a, o, ang, a.size(), y);
                lord.teleportTo(end.x, end.y, end.z);
            });
            case PILLARS -> {
                int n = (int) a.size2() + f.phase;
                for (int k = 0; k < n; k++) {
                    double d = 2.5 + k * 3.2;
                    double x = o.x + Math.cos(ang) * d, z = o.z + Math.sin(ang) * d;
                    tele(level, TelegraphShape.circle(x, z, a.size()), y, warn + k * 5, lord, hit, lvl -> {
                        impact(lvl, f, lord, a, x, y, z, a.size());
                        pillar(lvl, x, y, z);
                    });
                }
            }
        }
    }

    static void tele(ServerLevel level, TelegraphShape shape, double y, int warn, LivingEntity lord, Telegraph.Hit hit,
            java.util.function.Consumer<ServerLevel> onImpact) {
        Telegraph.in(level, shape).floor(y).warn(warn).danger().owner(lord).onImpact(lvl -> {
            onImpact.accept(lvl);
        }).onHit(hit).start();
    }

    static void hurt(ServerLevel lvl, AbyssLords.Fight f, LivingEntity lord, ServerPlayer p, float dmg, Attack a) {
        p.hurt(lvl.damageSources().mobAttack(lord), dmg);
        double dx = p.getX() - lord.getX(), dz = p.getZ() - lord.getZ();
        double len = Math.max(0.1, Math.hypot(dx, dz));
        switch (f.li) {
            case 0 -> p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, true));
            case 1 -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1, false, true));
            case 2 -> p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 24, 3, false, true));
            default -> p.igniteForTicks(60);
        }
        if (a.kind() == Kind.SLAM || a.kind() == Kind.RING || a.kind() == Kind.CHARGE) {
            p.knockback(0.9, -dx / len, -dz / len);
            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.35, 0));
            p.hurtMarked = true;
        }
    }

    // ---------------------------------------------------------------- 当たりの絵 (層の色)
    static void impact(ServerLevel lvl, AbyssLords.Fight f, LivingEntity lord, Attack a, double x, double y, double z, double r) {
        LordAnim.strike(lord, "attack_" + a.anim());
        int n = (int) Math.max(8, r * r * 3);
        element(lvl, f.li, x, y + 0.3, z, n, r * 0.6);
        lvl.playSound(null, x, y, z, hitSound(f.li), SoundSource.HOSTILE, 1.3f, 0.8f);
        if (f.li == 2 && (a.kind() == Kind.UNDER || a.kind() == Kind.SLAM)) {
            bolt(lvl, x, y, z);
        }
    }

    static void lineFx(ServerLevel lvl, AbyssLords.Fight f, LivingEntity lord, Attack a, Vec3 o, double ang, double len, double y) {
        LordAnim.strike(lord, "attack_" + a.anim());
        for (double d = 1; d <= len; d += 1.2) {
            element(lvl, f.li, o.x + Math.cos(ang) * d, y + 0.4, o.z + Math.sin(ang) * d, 3, 0.3);
        }
        lvl.playSound(null, o.x + Math.cos(ang) * len / 2, y, o.z + Math.sin(ang) * len / 2, hitSound(f.li), SoundSource.HOSTILE, 1.3f, 0.9f);
        if (f.li == 2) {
            bolt(lvl, o.x + Math.cos(ang) * len, y, o.z + Math.sin(ang) * len);
        }
    }

    static void ringFx(ServerLevel lvl, AbyssLords.Fight f, LivingEntity lord, Attack a, Vec3 o, double y) {
        LordAnim.strike(lord, "attack_" + a.anim());
        for (int k = 0; k < 36; k++) {
            double t = k * Math.PI / 18;
            double r = (a.size() + a.size2()) / 2;
            element(lvl, f.li, o.x + Math.cos(t) * r, y + 0.3, o.z + Math.sin(t) * r, 2, 0.4);
        }
        lvl.playSound(null, o.x, y, o.z, hitSound(f.li), SoundSource.HOSTILE, 1.6f, 0.6f);
    }

    static void pillar(ServerLevel lvl, double x, double y, double z) {
        for (int k = 0; k < 6; k++) {
            lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), x, y + k * 0.5, z, 6, 0.3, 0.2, 0.3, 0.05);
        }
    }

    static void bolt(ServerLevel lvl, double x, double y, double z) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(lvl);
        if (b != null) {
            b.moveTo(x, y, z);
            b.setVisualOnly(true);
            lvl.addFreshEntity(b);
        }
    }

    // 層の色の粒 (骨 = 魂の火と骨、塩 = 白い粉としぶき、雷 = 火花、灰 = 炎と煙)
    static void element(ServerLevel lvl, int li, double x, double y, double z, int n, double spread) {
        ParticleOptions a;
        ParticleOptions b;
        switch (li) {
            case 0 -> {
                a = ParticleTypes.SOUL_FIRE_FLAME;
                b = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState());
            }
            case 1 -> {
                a = ParticleTypes.WHITE_ASH;
                b = ParticleTypes.SPLASH;
            }
            case 2 -> {
                a = ParticleTypes.ELECTRIC_SPARK;
                b = ParticleTypes.END_ROD;
            }
            default -> {
                a = ParticleTypes.FLAME;
                b = ParticleTypes.LARGE_SMOKE;
            }
        }
        lvl.sendParticles(a, x, y, z, n, spread, 0.3, spread, 0.03);
        lvl.sendParticles(b, x, y, z, Math.max(2, n / 2), spread, 0.3, spread, 0.02);
    }

    static SoundEvent windSound(int li) {
        return switch (li) {
            case 0 -> SoundEvents.SKELETON_AMBIENT;
            case 1 -> SoundEvents.ELDER_GUARDIAN_AMBIENT;
            case 2 -> SoundEvents.BEACON_POWER_SELECT;
            default -> SoundEvents.BLAZE_AMBIENT;
        };
    }

    static SoundEvent hitSound(int li) {
        return switch (li) {
            case 0 -> SoundEvents.BONE_BLOCK_BREAK;
            case 1 -> SoundEvents.AMETHYST_BLOCK_BREAK;
            case 2 -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            default -> SoundEvents.FIRECHARGE_USE;
        };
    }

    // ---------------------------------------------------------------- 小さな手伝い
    static void hold(LivingEntity lord) {
        if (lord instanceof Mob m) {
            m.getNavigation().stop();
        }
        lord.setDeltaMovement(0, Math.min(0, lord.getDeltaMovement().y), 0);
    }

    static double floorY(AbyssLords.Fight f, LivingEntity lord) {
        BlockPos c = AbyssBuild.mark("arena_" + f.layer);
        return c != null ? c.getY() : lord.getY();
    }

    static Vec3 clampToArena(AbyssLords.Fight f, Vec3 p) {
        BlockPos c = AbyssBuild.mark("arena_" + f.layer);
        if (c == null) {
            return p;
        }
        double cx = c.getX() + 0.5, cz = c.getZ() + 0.5;
        double dx = p.x - cx, dz = p.z - cz;
        double d = Math.hypot(dx, dz);
        double max = Layers.ARENA_R - 2.5;
        if (d <= max) {
            return p;
        }
        return new Vec3(cx + dx / d * max, p.y, cz + dz / d * max);
    }

    @Nullable
    static ServerPlayer nearest(LivingEntity lord, List<ServerPlayer> in) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : in) {
            double d = p.distanceToSqr(lord);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }
}
