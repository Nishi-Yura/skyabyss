package jp.revati.skyabyss.rpg.skill;

import java.util.Comparator;
import java.util.List;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 巨神ごとの 6 職の伝説 (abyss/LegendGear の 20 本) の「技の書き換え」(Rune の 4 番目。2026-10-27)。
// 技の中身はそのまま動き、そのあとに巨神の色の上乗せが続く (岩鯨 = 岩と打ち上げ、潮殻 = 水と押し引き・癒し、雷翼 = 雷と連なり、余燼 = 火と燃え残り)。
// 効くのは、その技を極めて書き換えを選び、その伝説を主の手に持っているときだけ (SkillManager.legendRune)。
// 傷は武器の攻撃力 (魔法使い・呪術師は spell) に倍率を掛け、技の力の倍率 (SkillActions.hit) を通す
public final class LegendRewrites {
    private LegendRewrites() {
    }

    // SkillActions.perform が、技が成った直後に呼ぶ。from = 技を使う前に立っていた所 (跳ぶ技の元の場所)
    static void after(ServerPlayer player, Skill skill, int rank, Vec3 from) {
        Rune r = SkillManager.legendRune(player, skill);
        if (r == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 f = flatLook(player);
        switch (r) {
            // ---- 戦士
            case TIDE_WALL -> SkillEffects.later(level, 8, () -> {
                Vec3 c = player.position();
                for (LivingEntity e : cone(player, c, f, 4.0, 60)) {
                    SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.5), skill);
                    push(e, c, 1.2);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), player);
                }
                wall(level, c.add(f.scale(2.0)), f, ParticleTypes.SPLASH);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1f, 0.8f);
            });
            case STORM_CLEAVE -> {
                List<LivingEntity> near = cone(player, player.position(), f, 5.0, 70);
                near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
                for (LivingEntity e : near.subList(0, Math.min(3, near.size()))) {
                    bolt(level, e.position());
                    SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.6), skill);
                }
            }
            case EMBER_SWEEP -> {
                Vec3 c = player.position();
                for (int k = 0; k < 3; k++) {
                    SkillEffects.later(level, 20 * k + 10, () -> {
                        ring(level, c, 3.5, ParticleTypes.FLAME, 24);
                        for (LivingEntity e : around(player, c, 3.5)) {
                            SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.2), skill);
                            e.igniteForSeconds(3);
                        }
                    });
                }
            }
            // ---- 鍛冶師
            case WHALE_STOMP -> {
                Vec3 c = player.position();
                SkillEffects.later(level, 16, () -> {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), c.x, c.y + 0.2, c.z, 50, 2.5, 0.3, 2.5, 0.2);
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
                    for (LivingEntity e : around(player, c, 5.0)) {
                        SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.7), skill);
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.55, 0));
                        e.hurtMarked = true;
                    }
                });
            }
            case TIDE_HAMMER -> {
                Vec3 c = aimPoint(player, 5.0);
                level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.3, c.z, 40, 1.5, 0.3, 1.5, 0.1);
                level.sendParticles(ParticleTypes.HEART, c.x, c.y + 1.0, c.z, 4, 1.2, 0.3, 1.2, 0.0);
                for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(5.0), ServerPlayer::isAlive)) {
                    ally.heal((float) (JobActions.weaponDamage(player) * 0.5));
                }
            }
            case STORM_ASH -> {
                Vec3 c = player.position().add(f.scale(3.0));
                for (int k = 1; k <= 3; k++) {
                    SkillEffects.later(level, 40 * k, () -> {
                        List<LivingEntity> in = around(player, c, 4.0);
                        if (!in.isEmpty()) {
                            LivingEntity e = in.get(player.getRandom().nextInt(in.size()));
                            bolt(level, e.position());
                            SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.5), skill);
                        }
                    });
                }
            }
            // ---- 魔法使い
            case STORM_BOLT -> {
                LivingEntity first = aimFoe(player, SkillRules.BOLT_RANGE);
                if (first != null) {
                    List<LivingEntity> next = around(player, first.position(), 6.0);
                    next.remove(first);
                    next.sort(Comparator.comparingDouble(e -> e.distanceToSqr(first)));
                    Vec3 a = first.position().add(0, first.getBbHeight() * 0.6, 0);
                    for (LivingEntity e : next.subList(0, Math.min(2, next.size()))) {
                        Vec3 b = e.position().add(0, e.getBbHeight() * 0.6, 0);
                        line(level, a, b, ParticleTypes.ELECTRIC_SPARK);
                        SkillActions.hit(player, e, JobActions.spell(player, 0.5), skill);
                    }
                }
            }
            case SPLIT_EMBER -> {
                Vec3 c = aimPoint(player, 20.0);
                SkillEffects.later(level, 10, () -> {
                    for (int k = 0; k < 3; k++) {
                        double a = k * Math.PI * 2 / 3 + player.getYRot() * Math.PI / 180;
                        Vec3 p = c.add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
                        level.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.5, p.z, 16, 0.6, 0.4, 0.6, 0.03);
                        level.sendParticles(ParticleTypes.LAVA, p.x, p.y + 0.5, p.z, 3, 0.3, 0.2, 0.3, 0.0);
                        for (LivingEntity e : around(player, p, 1.8)) {
                            SkillActions.hit(player, e, JobActions.spell(player, 0.4), skill);
                            e.igniteForSeconds(3);
                        }
                    }
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 1.2f);
                });
            }
            case HIGH_TIDE -> SkillEffects.later(level, 20, () -> {
                Vec3 c = player.position();
                Vec3 look = flatLook(player);
                wall(level, c.add(look.scale(3.0)), look, ParticleTypes.SPLASH);
                for (LivingEntity e : cone(player, c, look, 7.0, 50)) {
                    SkillActions.hit(player, e, JobActions.spell(player, 0.6), skill);
                    push(e, c, 0.9);
                }
                level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.2f, 0.6f);
            });
            case STONE_DOUBLE -> {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.05);
                SkillEffects.later(level, 20, () -> {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), from.x, from.y + 1, from.z, 60, 1.5, 0.8, 1.5, 0.2);
                    level.playSound(null, from.x, from.y, from.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.4f, 0.6f);
                    for (LivingEntity e : around(player, from, 3.0)) {
                        SkillActions.hit(player, e, JobActions.spell(player, 0.8), skill);
                    }
                });
            }
            // ---- 弓使い
            case WHALE_CAGE -> {
                Vec3 c = aimPoint(player, 12.0);
                ring(level, c, 3.0, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), 20);
                for (LivingEntity e : around(player, c, 4.0)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6), player);
                    SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.3), skill);
                }
            }
            case TIDE_RAIN -> {
                Vec3 c = aimPoint(player, 24.0);
                level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.5, c.z, 60, 3.0, 0.4, 3.0, 0.1);
                for (LivingEntity e : around(player, c, 5.0)) {
                    Vec3 d = c.subtract(e.position()).multiply(1, 0, 1);
                    if (d.lengthSqr() > 0.01) {
                        e.setDeltaMovement(e.getDeltaMovement().add(d.normalize().scale(0.6)));
                        e.hurtMarked = true;
                    }
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), player);
                }
            }
            case FIRE_LEAP -> {
                level.sendParticles(ParticleTypes.FLAME, from.x, from.y + 0.3, from.z, 40, 1.5, 0.3, 1.5, 0.05);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 0.8f);
                for (LivingEntity e : around(player, from, 3.0)) {
                    SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.7), skill);
                    e.igniteForSeconds(3);
                }
            }
            // ---- 暗殺者
            case TIDE_VEIL -> {
                Vec3 c = player.position();
                for (int k = 1; k <= 4; k++) {
                    SkillEffects.later(level, 20 * k, () -> {
                        level.sendParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 1.5, c.z, 20, 2.0, 0.4, 2.0, 0.0);
                        for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(4.0), ServerPlayer::isAlive)) {
                            ally.heal((float) (JobActions.weaponDamage(player) * 0.15));
                        }
                    });
                }
            }
            case WHALE_FANG -> SkillEffects.later(level, 8, () -> {
                Vec3 c = player.position();
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), c.x, c.y + 0.2, c.z, 30, 1.5, 0.2, 1.5, 0.2);
                for (LivingEntity e : around(player, c, 2.5)) {
                    SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.6), skill);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
                    e.hurtMarked = true;
                }
            });
            case STORM_MARK -> {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y + 0.2, from.z, 20, 0.6, 0.1, 0.6, 0.1);
                SkillEffects.later(level, 20, () -> {
                    bolt(level, from);
                    for (LivingEntity e : around(player, from, 2.5)) {
                        SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.9), skill);
                    }
                });
            }
            case EMBER_REND -> {
                List<LivingEntity> cut = cone(player, player.position(), f, 3.5, 70);
                for (LivingEntity e : cut) {
                    e.igniteForSeconds(4);
                }
                SkillEffects.later(level, 20, () -> {
                    for (LivingEntity e : cut) {
                        if (e.isAlive()) {
                            level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
                            SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.4), skill);
                        }
                    }
                });
            }
            // ---- 呪術師
            case ROCK_SONG -> {
                Vec3 c = player.position();
                ring(level, c, 6.0, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), 30);
                for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(6.0), ServerPlayer::isAlive)) {
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0), player);
                }
            }
            case STORM_CURSE -> {
                LivingEntity mark = aimFoe(player, 16.0);
                if (mark != null) {
                    SkillEffects.later(level, 60, () -> {
                        if (!mark.isAlive()) {
                            return;
                        }
                        bolt(level, mark.position());
                        SkillActions.hit(player, mark, JobActions.spell(player, 0.8), skill);
                        List<LivingEntity> near = around(player, mark.position(), 4.0);
                        near.remove(mark);
                        for (LivingEntity e : near.subList(0, Math.min(2, near.size()))) {
                            line(level, mark.position().add(0, 1, 0), e.position().add(0, 1, 0), ParticleTypes.ELECTRIC_SPARK);
                            SkillActions.hit(player, e, JobActions.spell(player, 0.4), skill);
                        }
                    });
                }
            }
            case EMBER_LAMP -> {
                Vec3 c = player.position();
                for (int k = 1; k <= 5; k++) {
                    SkillEffects.later(level, 20 * k, () -> {
                        ring(level, c, 4.0, ParticleTypes.SMALL_FLAME, 20);
                        for (LivingEntity e : around(player, c, 4.0)) {
                            SkillActions.hit(player, e, JobActions.spell(player, 0.2), skill);
                            e.igniteForSeconds(2);
                        }
                    });
                }
            }
            // ---- 巨神のヒロイック武器 (2026-10-28)
            case WHALE_TEMPER -> {
                Vec3 c = player.position();
                ring(level, c, 6.0, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), 24);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f);
                for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(6.0), ServerPlayer::isAlive)) {
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0), player);
                }
            }
            case WHALE_STARFALL -> {
                LivingEntity first = aimFoe(player, SkillRules.BOLT_RANGE);
                Vec3 c = first != null ? first.position() : aimPoint(player, SkillRules.BOLT_RANGE);
                SkillEffects.later(level, 20, () -> {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), c.x, c.y + 0.3, c.z, 40, 1.2, 0.3, 1.2, 0.2);
                    level.playSound(null, c.x, c.y, c.z, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
                    for (LivingEntity e : around(player, c, 2.5)) {
                        SkillActions.hit(player, e, JobActions.spell(player, 0.6), skill);
                        e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
                        e.hurtMarked = true;
                    }
                });
            }
            case WHALE_RIB_PIERCE -> {
                for (LivingEntity e : cone(player, player.position(), f, 14.0, 10)) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), e.getX(), e.getY() + 0.2, e.getZ(), 14, 0.4, 0.2, 0.4, 0.1);
                    SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.4), skill);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.45, 0));
                    e.hurtMarked = true;
                }
            }
            case WHALE_DEEP_WARD -> {
                Vec3 c = player.position();
                ring(level, c, 5.0, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()), 24);
                level.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2f, 0.5f);
                for (LivingEntity e : around(player, c, 5.0)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6), player);
                }
            }
            case TIDE_STOMP -> SkillEffects.later(level, 8, () -> {
                Vec3 c = player.position();
                level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.3, c.z, 50, 2.5, 0.3, 2.5, 0.1);
                for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(5.0), ServerPlayer::isAlive)) {
                    ally.heal((float) (JobActions.weaponDamage(player) * 0.4));
                }
            });
            case TIDE_BUBBLE -> {
                Vec3 c = aimPoint(player, 20.0);
                for (int k = 1; k <= 3; k++) {
                    SkillEffects.later(level, 20 * k, () -> {
                        level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 1.0, c.z, 24, 1.5, 0.6, 1.5, 0.02);
                        for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(4.0), ServerPlayer::isAlive)) {
                            ally.heal(JobActions.spell(player, 0.15));
                        }
                    });
                }
            }
            case TIDE_CORAL_TRAP -> {
                Vec3 c = aimPoint(player, 12.0);
                level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.3, c.z, 30, 2.0, 0.3, 2.0, 0.1);
                for (LivingEntity e : around(player, c, 4.0)) {
                    Vec3 d = c.subtract(e.position()).multiply(1, 0, 1);
                    if (d.lengthSqr() > 0.01) {
                        e.setDeltaMovement(e.getDeltaMovement().add(d.normalize().scale(0.6)));
                        e.hurtMarked = true;
                    }
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), player);
                }
            }
            case TIDE_SURF_LAMP -> {
                Vec3 c = player.position();
                for (int k = 1; k <= 5; k++) {
                    SkillEffects.later(level, 20 * k, () -> {
                        ring(level, c, 4.0, ParticleTypes.FALLING_WATER, 20);
                        for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(4.0), ServerPlayer::isAlive)) {
                            ally.heal(JobActions.spell(player, 0.12));
                        }
                    });
                }
            }
            case STORM_ANVIL -> {
                Vec3 c = aimPoint(player, 5.0);
                List<LivingEntity> near = around(player, c, 5.0);
                near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c.x, c.y, c.z)));
                for (LivingEntity e : near.subList(0, Math.min(3, near.size()))) {
                    bolt(level, e.position());
                    SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.5), skill);
                }
            }
            case STORM_SURGE -> SkillEffects.later(level, 10, () -> {
                List<LivingEntity> ahead = cone(player, player.position(), flatLook(player), 7.0, 50);
                ahead.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
                for (LivingEntity e : ahead.subList(0, Math.min(3, ahead.size()))) {
                    line(level, player.position().add(0, 1, 0), e.position().add(0, 1, 0), ParticleTypes.ELECTRIC_SPARK);
                    SkillActions.hit(player, e, JobActions.spell(player, 0.4), skill);
                }
            });
            case STORM_GALE_RAIN -> {
                Vec3 c = aimPoint(player, 24.0);
                level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.5, c.z, 40, 3.0, 0.4, 3.0, 0.2);
                for (LivingEntity e : around(player, c, 5.0)) {
                    push(e, c, 1.1);
                }
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1), player);
            }
            case STORM_BELL_PULL -> SkillEffects.later(level, 20, () -> {
                Vec3 c = player.position();
                for (LivingEntity e : around(player, c, 4.0)) {
                    bolt(level, e.position());
                    SkillActions.hit(player, e, JobActions.spell(player, 0.5), skill);
                }
            });
            case EMBER_RUSH -> {
                Vec3 to = player.position();
                for (int k = 0; k <= 4; k++) {
                    Vec3 p = from.lerp(to, k / 4.0);
                    level.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.2, p.z, 8, 0.6, 0.1, 0.6, 0.01);
                    for (LivingEntity e : around(player, p, 2.0)) {
                        if (e.getRemainingFireTicks() <= 0) {
                            SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.3), skill);
                        }
                        e.igniteForSeconds(3);
                    }
                }
            }
            case EMBER_ASH_VEIL -> {
                Vec3 c = player.position();
                level.sendParticles(ParticleTypes.WHITE_ASH, c.x, c.y + 1, c.z, 60, 2.5, 0.8, 2.5, 0.02);
                for (LivingEntity e : around(player, c, 4.0)) {
                    e.igniteForSeconds(4);
                }
            }
            case EMBER_KILN_BLINK -> {
                level.sendParticles(ParticleTypes.FLAME, from.x, from.y + 0.6, from.z, 40, 1.2, 0.5, 1.2, 0.05);
                level.sendParticles(ParticleTypes.LAVA, from.x, from.y + 0.6, from.z, 6, 0.6, 0.3, 0.6, 0.0);
                level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 0.7f);
                for (LivingEntity e : around(player, from, 3.0)) {
                    SkillActions.hit(player, e, JobActions.spell(player, 0.6), skill);
                    e.igniteForSeconds(3);
                }
            }
            case EMBER_FLARE -> {
                LivingEntity first = aimFoe(player, 24.0);
                if (first != null) {
                    Vec3 c = first.position();
                    level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 1, c.z, 24, 1.2, 0.6, 1.2, 0.03);
                    for (LivingEntity e : around(player, c, 2.5)) {
                        SkillActions.hit(player, e, (float) (JobActions.weaponDamage(player) * 0.3), skill);
                        e.igniteForSeconds(4);
                    }
                }
            }
            case EMBER_CENSER_SONG -> {
                Vec3 c = player.position();
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y + 1, c.z, 20, 3.0, 0.5, 3.0, 0.01);
                for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(6.0), ServerPlayer::isAlive)) {
                    ally.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 160, 0), player);
                    ally.heal(JobActions.spell(player, 0.2));
                }
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 道具
    static Vec3 flatLook(ServerPlayer player) {
        Vec3 l = player.getLookAngle().multiply(1, 0, 1);
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    static List<LivingEntity> around(ServerPlayer player, Vec3 c, double r) {
        return new java.util.ArrayList<>(player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r, 2.5, r),
                e -> e.isAlive() && e != player && SkillActions.isFoe(player, e) && e.position().distanceToSqr(c) <= r * r + 4));
    }

    static List<LivingEntity> cone(ServerPlayer player, Vec3 c, Vec3 dir, double r, double halfDeg) {
        double cos = Math.cos(Math.toRadians(halfDeg));
        List<LivingEntity> out = around(player, c, r);
        out.removeIf(e -> {
            Vec3 d = e.position().subtract(c).multiply(1, 0, 1);
            return d.lengthSqr() > 0.25 && d.normalize().dot(dir) < cos;
        });
        return out;
    }

    // 見ている先の地面 (届かなければ届く所)
    static Vec3 aimPoint(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        var hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    // 見ている先の最初の敵 (壁で止まる)
    static LivingEntity aimFoe(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 stop = aimPoint(player, range);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, stop, new AABB(eye, stop).inflate(1.0),
                e -> e instanceof LivingEntity le && le.isAlive() && e != player && SkillActions.isFoe(player, e), eye.distanceToSqr(stop));
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    static void push(LivingEntity e, Vec3 from, double strength) {
        Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
        if (d.lengthSqr() > 1.0E-4) {
            e.setDeltaMovement(e.getDeltaMovement().add(d.normalize().scale(strength).add(0, 0.2, 0)));
            e.hurtMarked = true;
        }
    }

    static void bolt(ServerLevel level, Vec3 at) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(level);
        if (b != null) {
            b.moveTo(at.x, at.y, at.z);
            b.setVisualOnly(true);
            level.addFreshEntity(b);
        }
    }

    static void ring(ServerLevel level, Vec3 c, double r, ParticleOptions p, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions p) {
        Vec3 d = b.subtract(a);
        int n = Math.max(1, (int) (d.length() / 0.4));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    // 前に立つ水の壁 (向き dir に直交する横一列)
    static void wall(ServerLevel level, Vec3 c, Vec3 dir, ParticleOptions p) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int i = -6; i <= 6; i++) {
            Vec3 q = c.add(side.scale(i * 0.4));
            level.sendParticles(p, q.x, q.y + 0.5, q.z, 4, 0.1, 0.6, 0.1, 0.05);
        }
    }
}
