package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 技の木 v3 の変化 (2026-11-04。docs/design/48 の 4 章)。SkillActions.perform が技の前 (before) と後 (after) に呼ぶ。
//   発動から外した技を残った技の変化にしたもの: 鯨骨の振り (巨神斬り 2)・回し斬り (鯨尾薙ぎ 8)・巡りの唄 (守りの唄 8)・足止めの矢 (雲穿ち 8)・
//     火花散らし (炉の踏み 1)・星屑 (雷の矢 1) は元の技の代わりに動く。霧隠れ (踏み込み 8)・盾殴り (盾の踏み込み 1) は元の技のあとに続く。
//     外した技の武器の書き換え (霧隠れ・巡りの唄・回し斬り・火花散らし・星屑・足止めの矢) は、その武器を持っていれば変化のときに効く
//   新しい変化: 三連牙・毒牙 (二連牙)、長い印・移る印 (呪いの印)、歩む灯・熾す灯 (灯の守り札)、重い踏み (炉の踏み)、濃い灰・灰の風 (灰散らし)、
//     投げ罠・大罠 (骨の罠)、置き矢 (射手の跳び退き)、枝雷・重い雷 (雷の矢)、氷の輪・霜の道 (凍て潮)、星の残り・二度の転移 (転移)。
//     深い焼き・分け火 (焼き入れ)・狭い雨・長雨 (天の驟雨)・三つ玉・残り火 (熾火の玉) は元の技の中 (SkillActions・JobActions)
//   変異 (魔法使い): 雷雲 (雷の矢)・凍て海 (凍て潮)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TreeVariants {
    // 元の技のまま動かすとき
    static final String PASS = "skyabyss:pass";

    // 二連牙
    static final double TRIPLE_FANG_SCALE = 0.7;
    static final int VENOM_TICKS = 80;
    // 呪いの印
    static final double LONG_MARK_TIME = 2.0;
    static final double LONG_MARK_POWER = 0.7;
    static final double PASS_RANGE = 8;
    // 灯の守り札
    static final double WALKING_LAMP_ABSORB = 0.7;
    static final double BURNING_LAMP_DAMAGE = 0.35;
    // 炉の踏み
    static final double HEAVY_STOMP_RADIUS = 1.0;
    static final int HEAVY_STOMP_BIND = 20;
    // 灰散らし
    static final double THICK_ASH_RADIUS = 0.5;
    static final double ASH_WIND_LENGTH = 8;
    static final double ASH_WIND_WIDTH = 1.6;
    // 骨の罠
    static final double THROWN_TRAP_RANGE = 12;
    static final double GREAT_TRAP_RADIUS = 2.0;
    public static final double GREAT_TRAP_BIND = 1.5;
    // 射手の跳び退き
    static final int PARTING_ARROWS = 3;
    static final double PARTING_RANGE = 10;
    static final double PARTING_DAMAGE = 0.5;
    // 雷の矢
    static final int BRANCH_TARGETS = 2;
    static final double BRANCH_SCALE = 0.7;
    static final double HEAVY_BOLT_SCALE = 1.4;
    // 凍て潮
    static final double ICE_RING_INNER = 2.0;
    static final double ICE_RING_OUTER = 2.0;
    static final double ICE_RING_SCALE = 1.3;
    static final double FROST_ROAD_LENGTH = 8;
    static final double FROST_ROAD_WIDTH = 1.8;
    // 転移
    static final int STAR_REMNANT_DELAY = 20;
    static final double STAR_REMNANT_RADIUS = 3;
    static final double STAR_REMNANT_DAMAGE = 0.8;
    static final int DOUBLE_BLINK_WINDOW = 60;
    public static final double DOUBLE_BLINK_RANGE = 0.5;
    // 雷雲・凍て海
    static final int CLOUD_TICKS = 80;
    static final int CLOUD_EVERY = 16;
    static final double CLOUD_RADIUS = 3;
    static final double CLOUD_SCALE = 0.6;
    static final int SEA_TICKS = 120;
    static final int SEA_FREEZE = 40;

    private record Pass(UUID owner, long until, double bonus, int ticks) {
    }

    private static final Map<UUID, Pass> PASSING = new HashMap<>();
    private static final Map<UUID, Long> SECOND_BLINK = new HashMap<>();

    private TreeVariants() {
    }

    static int rune(ServerPlayer player, Skill skill) {
        return SkillManager.runeOf(player, skill);
    }

    // ---------------------------------------------------------------- 技の前: 元の技の代わりに動く変化
    static String before(ServerPlayer player, Skill skill, int rank) {
        int r = rune(player, skill);
        if (r == 0) {
            return PASS;
        }
        Vec3 from = player.position();
        return switch (skill) {
            case TITAN_CLEAVE -> r == 2 ? inner(player, Skill.BONE_SWING, rank, from, TreeActions.boneSwing(player, rank)) : PASS;
            case WHALE_SWEEP -> r == 8 ? inner(player, Skill.SPIN_CUT, rank, from, TreeActions.spinCut(player, rank)) : PASS;
            case WARD_SONG -> r == 8 ? inner(player, Skill.CYCLE_SONG, rank, from, SkillActions.cycleSong(player, rank)) : PASS;
            case CLOUD_PIERCE -> r == 8 ? inner(player, Skill.SNARE_SHOT, rank, from, TreeActions.snareShot(player, rank)) : PASS;
            case FORGE_STOMP -> r == 1 ? inner(player, Skill.SPARK_BURST, rank, from, TreeActions.sparkBurst(player, rank)) : r == 2 ? heavyStomp(player, rank) : PASS;
            case THUNDER_BOLT -> r == 1 ? inner(player, Skill.STARDUST, rank, from, TreeActions.stardust(player, rank)) : r == 2 || r == 3 ? bolt(player, rank, r) : PASS;
            case TWIN_FANG -> r == 1 || r == 2 ? fang(player, rank, r) : PASS;
            case CURSE_MARK -> curse(player, rank, r);
            case LAMP_CHARM -> r == 1 ? walkingLamp(player, rank) : PASS;
            case ASH_SCATTER -> r == 1 || r == 2 ? ash(player, rank, r) : PASS;
            case BONE_TRAP -> r == 1 || r == 2 ? trap(player, rank, r) : PASS;
            case FROST_TIDE -> r == 1 || r == 2 ? frost(player, rank, r) : PASS;
            default -> PASS;
        };
    }

    // 外した技の武器の書き換えを、変化のときに足す。見た目も中身の技のものに
    private static String inner(ServerPlayer player, Skill inner, int rank, Vec3 from, String result) {
        if (result == null) {
            LegendRewrites.afterHeld(player, inner, rank, from);
            SkillActions.fxSkill = inner;
        }
        return result;
    }

    // ---------------------------------------------------------------- 技の後: 元の技に続く変化
    static void after(ServerPlayer player, Skill skill, int rank, Vec3 from) {
        int r = rune(player, skill);
        switch (skill) {
            case LUNGE -> {
                if (r == 8 && JobActions.mistVeil(player, rank) == null) {
                    LegendRewrites.afterHeld(player, Skill.MIST_VEIL, rank, from);
                }
            }
            case SHIELD_RUSH -> {
                if (r == 1) {
                    TreeActions.shieldBash(player, rank);
                }
            }
            case ARCHER_LEAP -> {
                if (r == 1) {
                    partingArrows(player, from);
                }
            }
            case LAMP_CHARM -> {
                if (r == 2) {
                    burningLamp(player, rank, from);
                }
            }
            case BLINK -> {
                if (r == 1) {
                    starRemnant(player, from);
                } else if (r == 2) {
                    doubleBlink(player);
                }
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 二連牙: 三連牙 (三度刺す。一度 ×0.7)・毒牙 (刺した敵が 4 秒の毒)
    private static String fang(ServerPlayer player, int rank, int r) {
        String res = TreeActions.twinFang(player, rank);
        if (res != null) {
            return res;
        }
        LivingEntity target = SkillActions.lastHit(player);
        if (target == null) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        if (r == 2) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, VENOM_TICKS, 1), player);
            level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        } else {
            float third = SkillActions.attackDamage(player) * (float) (SkillRules.FANG_MULTIPLIER * TRIPLE_FANG_SCALE);
            SkillEffects.later(level, SkillRules.FANG_GAP * 2, () -> {
                if (player.isAlive() && !player.hasDisconnected() && target.isAlive() && player.distanceTo(target) <= SkillRules.FANG_RANGE + 1.5) {
                    SkillActions.hit(player, target, SkillActions.behind(target, player) ? third * (float) SkillRules.FANG_BACK : third, Skill.TWIN_FANG);
                    player.swing(InteractionHand.MAIN_HAND, true);
                }
            });
        }
        return null;
    }

    // ---------------------------------------------------------------- 呪いの印: 長い印 (2 倍続く・増える傷 ×0.7)・移る印 (倒れると近くの一体へ移る)
    private static String curse(ServerPlayer player, int rank, int r) {
        if (r != 1 && r != 2) {
            return PASS;
        }
        String res = JobActions.curseMark(player, rank);
        if (res != null) {
            return res;
        }
        LivingEntity target = SkillActions.lastHitOrAimed(player);
        if (target == null) {
            return null;
        }
        long now = player.level().getGameTime();
        int ticks = SkillRules.curseTicks(rank);
        double bonus = SkillRules.curseBonus(rank);
        if (r == 1) {
            ticks = (int) Math.round(ticks * LONG_MARK_TIME);
            bonus *= LONG_MARK_POWER;
            SkillEvents.mark(target, bonus, now, ticks, player);
        } else {
            PASSING.put(target.getUUID(), new Pass(player.getUUID(), now + ticks, bonus, ticks));
        }
        return null;
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Pass p = PASSING.remove(dead.getUUID());
        if (p == null || !(dead.level() instanceof ServerLevel level) || level.getGameTime() > p.until) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(p.owner);
        if (owner == null) {
            return;
        }
        LivingEntity next = null;
        double best = PASS_RANGE * PASS_RANGE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(PASS_RANGE), e -> e != dead && SkillActions.isFoe(owner, e))) {
            double d = e.distanceToSqr(dead);
            if (d < best) {
                best = d;
                next = e;
            }
        }
        if (next != null) {
            long now = level.getGameTime();
            SkillEvents.mark(next, p.bonus, now, p.ticks, owner);
            PASSING.put(next.getUUID(), new Pass(p.owner, now + p.ticks, p.bonus, p.ticks));
            level.sendParticles(ParticleTypes.SOUL, next.getX(), next.getY() + next.getBbHeight() + 0.3, next.getZ(), 10, 0.3, 0.2, 0.3, 0.01);
            level.playSound(null, next.getX(), next.getY(), next.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.6f, 1.6f);
        }
    }

    // ---------------------------------------------------------------- 灯の守り札: 歩む灯 (灯を置かず、自分が灯になる。満たす量 ×0.7)・熾す灯 (灯のそばの敵を焼く)
    private static String walkingLamp(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        double radius = SkillRules.LAMP_RADIUS + Gauges.lampRadius(player);
        int ticks = SkillRules.lampTicks(rank) + Gauges.lampTicks(player);
        for (int t = 0; t <= ticks; t += SkillRules.LAMP_PULSE) {
            SkillEffects.later(level, t, () -> {
                if (player.isAlive() && !player.hasDisconnected()) {
                    JobActions.lampPulse(player, player.position(), rank, radius, WALKING_LAMP_ABSORB);
                }
            });
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    private static void burningLamp(ServerPlayer player, int rank, Vec3 at) {
        ServerLevel level = player.serverLevel();
        double radius = SkillRules.LAMP_RADIUS + Gauges.lampRadius(player);
        int ticks = SkillRules.lampTicks(rank) + Gauges.lampTicks(player);
        for (int t = 20; t <= ticks; t += 20) {
            SkillEffects.later(level, t, () -> {
                if (!player.isAlive() || player.hasDisconnected() || player.level() != level) {
                    return;
                }
                float dmg = JobActions.spell(player, BURNING_LAMP_DAMAGE);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 2, radius), e -> SkillActions.isFoe(player, e))) {
                    if (e.position().distanceToSqr(at) <= radius * radius) {
                        SkillActions.hit(player, e, dmg, Skill.LAMP_CHARM);
                        e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 30));
                    }
                }
                level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.5, at.z, 10, radius * 0.4, 0.2, radius * 0.4, 0.01);
            });
        }
    }

    // ---------------------------------------------------------------- 炉の踏み: 重い踏み (範囲 −1、浮かせず 1 秒縛る)
    private static String heavyStomp(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        double radius = Math.max(1.5, SkillRules.STOMP_RADIUS - HEAVY_STOMP_RADIUS);
        float dmg = SkillActions.attackDamage(player) * (float) SkillRules.stompMultiplier(rank);
        for (LivingEntity e : SkillActions.foesAround(player, radius, 1.0, 2.0)) {
            SkillActions.hit(player, e, dmg, Skill.FORGE_STOMP);
            e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), SkillRules.STOMP_BURN));
            if (!SkillActions.isLarge(e)) {
                SkillEffects.bind(e, HEAVY_STOMP_BIND);
            }
        }
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.2, player.getZ(), 10, radius * 0.4, 0.1, radius * 0.4, 0.0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.2, player.getZ(), 6, radius * 0.4, 0.1, radius * 0.4, 0.01);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 0.4f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // ---------------------------------------------------------------- 灰散らし: 濃い灰 (範囲半分・弱める量 2 倍)・灰の風 (前へ 8 の帯に撒く)
    private static String ash(ServerPlayer player, int rank, int r) {
        List<LivingEntity> foes = new ArrayList<>();
        if (r == 1) {
            double radius = SkillRules.ASH_SCATTER_RADIUS * THICK_ASH_RADIUS;
            for (LivingEntity e : SkillActions.foesAroundAny(player, radius)) {
                if (e.distanceTo(player) - e.getBbWidth() / 2 <= radius) {
                    foes.add(e);
                }
            }
        } else {
            Vec3 dir = SkillActions.flatLook(player);
            Vec3 a = player.position();
            for (LivingEntity e : SkillActions.foesAroundAny(player, ASH_WIND_LENGTH + 1)) {
                Vec3 to = e.position().subtract(a);
                double along = to.x * dir.x + to.z * dir.z;
                double side = Math.abs(to.x * dir.z - to.z * dir.x);
                if (along >= 0 && along <= ASH_WIND_LENGTH && side <= ASH_WIND_WIDTH + e.getBbWidth() / 2) {
                    foes.add(e);
                }
            }
            ServerLevel level = player.serverLevel();
            for (double d = 1; d <= ASH_WIND_LENGTH; d += 1) {
                Vec3 q = a.add(dir.scale(d));
                level.sendParticles(ParticleTypes.ASH, q.x, q.y + 0.8, q.z, 6, 0.6, 0.4, 0.6, 0.02);
            }
        }
        if (foes.isEmpty()) {
            return "message.skyabyss.skill.no_enemy_near";
        }
        ServerLevel level = player.serverLevel();
        for (LivingEntity e : foes) {
            SkillEffects.ashFall(e, SkillRules.ashScatterTicks(rank));
            if (r == 1) {
                SkillEffects.thickAsh(e, SkillRules.ashScatterTicks(rank));
            }
            level.sendParticles(ParticleTypes.ASH, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_SWEEP.get(), 0.8f, 0.5f);
        return null;
    }

    // ---------------------------------------------------------------- 骨の罠: 投げ罠 (見ている先 12 に置く)・大罠 (一つだけ・半径 2 倍・縛り 1.5 倍)
    private static String trap(ServerPlayer player, int rank, int r) {
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        double radius = SkillRules.TRAP_TRIGGER;
        if (r == 1) {
            Vec3 eye = player.getEyePosition();
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(THROWN_TRAP_RANGE)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            Vec3 p = hit.getType() == HitResult.Type.MISS ? eye.add(player.getLookAngle().scale(THROWN_TRAP_RANGE)) : hit.getLocation();
            BlockHitResult ground = level.clip(new ClipContext(p.add(0, 0.5, 0), p.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (ground.getType() == HitResult.Type.MISS) {
                return "message.skyabyss.skill.no_target";
            }
            at = ground.getLocation();
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.3, at.z, 10, 0.3, 0.1, 0.3, 0.05);
        } else {
            if (!player.onGround() || player.isPassenger()) {
                return "message.skyabyss.skill.need_ground";
            }
            for (SkillEffects.Zone z : SkillEffects.zonesListOf(SkillEffects.ZoneKind.TRAP, player.getUUID())) {
                z.spent = true;
            }
            radius *= GREAT_TRAP_RADIUS;
        }
        SkillEffects.Zone z = SkillEffects.zone(SkillEffects.ZoneKind.TRAP, player, at, radius, SkillRules.TRAP_LIFETIME, rank);
        z.great = r == 2;
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_BACKSTEP.get(), 0.7f, 0.5f);
        return null;
    }

    // ---------------------------------------------------------------- 射手の跳び退き: 置き矢 (跳んだ元の場所から、近くの敵へ矢を 3 本)
    private static void partingArrows(ServerPlayer player, Vec3 from) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(PARTING_RANGE, 3, PARTING_RANGE),
                e -> SkillActions.isFoe(player, e));
        near.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(from)));
        float dmg = SkillActions.attackDamage(player) * (float) PARTING_DAMAGE;
        for (int i = 0; i < Math.min(PARTING_ARROWS, near.size()); i++) {
            LivingEntity e = near.get(i);
            int delay = 2 + i * 3;
            SkillEffects.later(level, delay, () -> {
                if (e.isAlive() && player.isAlive()) {
                    SkillActions.hit(player, e, dmg, Skill.ARCHER_LEAP);
                    level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.6f, 1.2f);
                }
            });
        }
    }

    // ---------------------------------------------------------------- 雷の矢: 枝雷 (はじめから近くの 2 体へ跳ねる・一度 ×0.7)・重い雷 (跳ねない・×1.4)
    private static String bolt(ServerPlayer player, int rank, int r) {
        ServerLevel level = player.serverLevel();
        Vec3[] stop = new Vec3[1];
        LivingEntity target = JobActions.aimed(player, SkillRules.BOLT_RANGE, stop);
        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6)).subtract(0, 0.25, 0);
        JobActions.line(level, from, stop[0], r == 3 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, r == 3 ? 0.35 : 0.5);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, r == 3 ? 1.2f : 1.9f);
        if (target != null) {
            float dmg = JobActions.spell(player, SkillRules.boltMultiplier(rank));
            if (r == 3) {
                SkillActions.hit(player, target, dmg * (float) HEAVY_BOLT_SCALE, Skill.THUNDER_BOLT);
                level.sendParticles(ParticleTypes.FLASH, stop[0].x, stop[0].y, stop[0].z, 1, 0, 0, 0, 0);
            } else {
                float part = dmg * (float) BRANCH_SCALE;
                SkillActions.hit(player, target, part, Skill.THUNDER_BOLT);
                List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(SkillRules.BOLT_CHAIN_RANGE),
                        e -> e != target && SkillActions.isFoe(player, e));
                near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(target)));
                Vec3 at = stop[0];
                for (int i = 0; i < Math.min(BRANCH_TARGETS, near.size()); i++) {
                    LivingEntity e = near.get(i);
                    Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0);
                    JobActions.line(level, at, to, ParticleTypes.ELECTRIC_SPARK, 0.4);
                    SkillActions.hit(player, e, part, Skill.THUNDER_BOLT);
                }
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, stop[0].x, stop[0].y, stop[0].z, 12, 0.3, 0.3, 0.3, 0.2);
        }
        SkillActions.fxTarget = stop[0];
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // ---------------------------------------------------------------- 凍て潮: 氷の輪 (半径 2 より外の輪だけ・×1.3)・霜の道 (前へ 8 の帯)
    private static String frost(ServerPlayer player, int rank, int r) {
        ServerLevel level = player.serverLevel();
        double radius = SkillRules.frostRadius(rank);
        float dmg = JobActions.spell(player, SkillRules.frostMultiplier(rank));
        List<LivingEntity> hits = new ArrayList<>();
        Vec3 c = player.position();
        if (r == 1) {
            double outer = radius + ICE_RING_OUTER;
            for (LivingEntity e : SkillActions.foesAround(player, outer, 1.0, 2.0)) {
                double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                if (d >= ICE_RING_INNER) {
                    hits.add(e);
                }
            }
            dmg *= (float) ICE_RING_SCALE;
            JobActions.ring(level, c, outer, ParticleTypes.SNOWFLAKE, (int) (outer * 10));
            JobActions.ring(level, c, ICE_RING_INNER, ParticleTypes.SNOWFLAKE, 16);
        } else {
            Vec3 dir = SkillActions.flatLook(player);
            for (LivingEntity e : SkillActions.foesAround(player, FROST_ROAD_LENGTH + 1, 1.0, 2.0)) {
                Vec3 to = e.position().subtract(c);
                double along = to.x * dir.x + to.z * dir.z;
                double side = Math.abs(to.x * dir.z - to.z * dir.x);
                if (along >= 0 && along <= FROST_ROAD_LENGTH && side <= FROST_ROAD_WIDTH + e.getBbWidth() / 2) {
                    hits.add(e);
                }
            }
            for (double d = 1; d <= FROST_ROAD_LENGTH; d += 0.8) {
                Vec3 q = c.add(dir.scale(d));
                level.sendParticles(ParticleTypes.SNOWFLAKE, q.x, q.y + 0.3, q.z, 4, 0.5, 0.1, 0.5, 0.01);
            }
        }
        for (LivingEntity e : hits) {
            SkillActions.hit(player, e, dmg, Skill.FROST_TIDE);
            SkillEffects.slow(e, SkillRules.FROST_SLOW * (SkillActions.isLarge(e) ? 0.5 : 1.0), SkillRules.frostTicks(rank));
            Gauges.chill(e, SkillRules.frostTicks(rank));
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.8f, 0.9f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // ---------------------------------------------------------------- 転移: 星の残り (元の場所に 1 秒後に弾ける星)・二度の転移 (3 秒のうちにもう一度跳べる)
    private static void starRemnant(ServerPlayer player, Vec3 at) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 12, 0.2, 0.3, 0.2, 0.02);
        SkillEffects.later(level, STAR_REMNANT_DELAY, () -> {
            if (!player.isAlive() || player.level() != level) {
                return;
            }
            float dmg = JobActions.spell(player, STAR_REMNANT_DAMAGE);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(STAR_REMNANT_RADIUS), e -> SkillActions.isFoe(player, e))) {
                if (e.position().distanceToSqr(at) <= STAR_REMNANT_RADIUS * STAR_REMNANT_RADIUS) {
                    SkillActions.hit(player, e, dmg, Skill.BLINK);
                }
            }
            level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y + 1, at.z, 30, 0.6, 0.6, 0.6, 0.15);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 1.3f);
        });
    }

    private static void doubleBlink(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long open = SECOND_BLINK.get(player.getUUID());
        if (open != null && now <= open) {
            // 二度目: ふつうの再使用まで
            SECOND_BLINK.remove(player.getUUID());
            return;
        }
        SECOND_BLINK.put(player.getUUID(), now + DOUBLE_BLINK_WINDOW);
        // 一度目: 再使用までを外す (SkillManager.cast が技のあとに待ちを付けるので、次の tick に外す)
        SkillEffects.later(player.serverLevel(), 1, () -> {
            SkillData d = SkillManager.get(player);
            d.cooldowns.remove(Skill.BLINK.id);
            SkillManager.sync(player);
        });
    }

    // ---------------------------------------------------------------- 変異: 雷雲 (見ている先の雷雲が 4 秒、下の敵へ雷を落とす)
    static String thunderCloud(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(SkillRules.BOLT_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 center = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        float dmg = JobActions.spell(player, SkillRules.boltMultiplier(rank) * CLOUD_SCALE);
        for (int t = 0; t < CLOUD_TICKS; t += CLOUD_EVERY) {
            int tt = t;
            SkillEffects.later(level, t, () -> {
                if (!player.isAlive() || player.level() != level) {
                    return;
                }
                level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 4, center.z, 14, CLOUD_RADIUS * 0.5, 0.3, CLOUD_RADIUS * 0.5, 0.01);
                List<LivingEntity> under = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(CLOUD_RADIUS, 4, CLOUD_RADIUS),
                        e -> SkillActions.isFoe(player, e));
                if (!under.isEmpty()) {
                    LivingEntity e = under.get((tt / CLOUD_EVERY) % under.size());
                    JobActions.line(level, new Vec3(e.getX(), center.y + 4, e.getZ()), e.position().add(0, e.getBbHeight() * 0.5, 0), ParticleTypes.ELECTRIC_SPARK, 0.3);
                    SkillActions.hit(player, e, dmg, Skill.THUNDER_BOLT);
                    level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4f, 1.6f);
                }
            });
        }
        SkillActions.fxTarget = center;
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    // ---------------------------------------------------------------- 変異: 凍て海 (冷たい潮の代わりに 6 秒の氷の床。小さな敵は 2 秒凍る)
    static String frozenSea(ServerPlayer player, int rank) {
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position();
        double radius = SkillRules.frostRadius(rank) + 1;
        float dmg = JobActions.spell(player, SkillRules.frostMultiplier(rank) * 0.5);
        for (int t = 0; t < SEA_TICKS; t += 20) {
            int tt = t;
            SkillEffects.later(level, t, () -> {
                if (!player.isAlive() || player.level() != level) {
                    return;
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, 2, radius), e -> SkillActions.isFoe(player, e))) {
                    if (Math.hypot(e.getX() - c.x, e.getZ() - c.z) > radius) {
                        continue;
                    }
                    if (tt == 0) {
                        SkillActions.hit(player, e, dmg, Skill.FROST_TIDE);
                    }
                    if (SkillActions.isLarge(e)) {
                        SkillEffects.slow(e, SkillRules.FROST_SLOW * 0.5, 30);
                    } else {
                        SkillEffects.bind(e, SEA_FREEZE);
                        Gauges.chill(e, SEA_FREEZE);
                    }
                }
                JobActions.ring(level, c, radius, ParticleTypes.SNOWFLAKE, (int) (radius * 9));
            });
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
        player.swing(InteractionHand.MAIN_HAND, true);
        return null;
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        PASSING.clear();
        SECOND_BLINK.clear();
    }
}
