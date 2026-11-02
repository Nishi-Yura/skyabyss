package jp.revati.skyabyss.rpg.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// 技の木 v2 (docs/design/48) で足した使う技。どれも SkillActions の部品 (相手の選び方・傷・音) を組み合わせる。
//   戦士: 盾殴り (前の扇を押して鈍らせる)・鯨骨の振り (見ている 1 体に重い一撃)・回し斬り (まわりを薙ぎながら前へ)
//   暗殺者: 二連牙 (見ている敵に二度。背後で強い)  鍛冶師: 火花散らし (まわりに傷と炎)
//   弓使い: 足止めの矢 (見ている 1 体に矢の傷と鈍足)  魔法使い: 星屑 (まわりに魔法の傷)
final class TreeActions {
    private TreeActions() {
    }

    static String perform(ServerPlayer player, Skill skill, int rank) {
        return switch (skill) {
            case SHIELD_BASH -> shieldBash(player, rank);
            case BONE_SWING -> boneSwing(player, rank);
            case SPIN_CUT -> spinCut(player, rank);
            case TWIN_FANG -> twinFang(player, rank);
            case SPARK_BURST -> sparkBurst(player, rank);
            case SNARE_SHOT -> snareShot(player, rank);
            case STARDUST -> stardust(player, rank);
            default -> "message.skyabyss.skill.unknown";
        };
    }

    // 見ている向きの一番近い敵 (届く所・角度の中)
    private static LivingEntity aimed(ServerPlayer player, double range, double maxAngle) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : SkillActions.foesAround(player, range + 1, 2.0, 3.0)) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d > range + e.getBbWidth() / 2) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1)));
            if (angle > maxAngle) {
                continue;
            }
            double score = angle * 0.2 + d;
            if (score < best) {
                best = score;
                target = e;
            }
        }
        return target;
    }

    // 前の扇の中の敵 (近い順)
    private static List<LivingEntity> cone(ServerPlayer player, double range, double halfAngle) {
        Vec3 dir = SkillActions.flatLook(player);
        Vec3 origin = player.position();
        double cos = Math.cos(Math.toRadians(halfAngle));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : SkillActions.foesAround(player, range + 2, 1.0, 2.0)) {
            Vec3 to = new Vec3(e.getX() - origin.x, 0, e.getZ() - origin.z);
            if (to.length() - e.getBbWidth() / 2 > range) {
                continue;
            }
            if (to.length() < 1.0 || to.normalize().dot(dir) >= cos) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        return out;
    }

    // 盾殴り: 前の狭い扇を盾で突き、押し出して鈍らせる
    static String shieldBash(ServerPlayer player, int rank) {
        float damage = SkillActions.attackDamage(player) * (float) SkillRules.bashMultiplier(rank);
        int n = 0;
        for (LivingEntity e : cone(player, SkillRules.BASH_RANGE, SkillRules.BASH_HALF_ANGLE)) {
            if (n++ >= SkillRules.BASH_MAX_TARGETS) {
                break;
            }
            if (SkillActions.hit(player, e, damage, Skill.SHIELD_BASH)) {
                SkillEffects.slow(e, SkillRules.BASH_SLOW * (SkillActions.isLarge(e) ? 0.5 : 1.0), SkillRules.BASH_SLOW_TICKS);
                e.knockback(SkillActions.push(player, SkillRules.BASH_PUSH), player.getX() - e.getX(), player.getZ() - e.getZ());
            }
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_LUNGE.get(), 0.9f, 0.8f);
        return null;
    }

    // 鯨骨の振り: 見ている 1 体に重い一撃。大きな相手にはさらに
    static String boneSwing(ServerPlayer player, int rank) {
        LivingEntity target = aimed(player, SkillRules.SWING_RANGE, 30);
        if (target == null) {
            return "message.skyabyss.skill.no_target";
        }
        float damage = SkillActions.attackDamage(player) * (float) SkillRules.swingMultiplier(rank);
        if (SkillActions.isLarge(target)) {
            damage *= (float) SkillRules.SWING_LARGE;
        }
        SkillActions.hit(player, target, damage, Skill.BONE_SWING);
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_CLEAVE.get(), 1.0f, 0.7f);
        return null;
    }

    // 回し斬り: まわりを薙ぎながら前へ進む
    static String spinCut(ServerPlayer player, int rank) {
        if (!player.onGround() || player.isPassenger()) {
            return "message.skyabyss.skill.need_ground";
        }
        float damage = SkillActions.attackDamage(player) * (float) SkillRules.spinMultiplier(rank);
        for (LivingEntity e : SkillActions.foesAround(player, SkillRules.SPIN_RADIUS + 1, 1.0, 2.0)) {
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2 > SkillRules.SPIN_RADIUS) {
                continue;
            }
            if (SkillActions.hit(player, e, damage, Skill.SPIN_CUT)) {
                e.knockback(SkillActions.push(player, SkillRules.SPIN_PUSH), -dx, -dz);
            }
        }
        Vec3 dir = SkillActions.flatLook(player).scale(SkillRules.SPIN_DASH);
        player.setDeltaMovement(dir.x, SkillRules.SPIN_LIFT, dir.z);
        player.hurtMarked = true;
        player.fallDistance = 0;
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_SWEEP.get(), 1.0f, 1.2f);
        return null;
    }

    // 二連牙: 見ている敵に二度。背後からならどちらも強い
    static String twinFang(ServerPlayer player, int rank) {
        LivingEntity target = aimed(player, SkillRules.FANG_RANGE, 35);
        if (target == null) {
            return "message.skyabyss.skill.no_target";
        }
        ServerLevel level = player.serverLevel();
        float base = SkillActions.attackDamage(player) * (float) SkillRules.FANG_MULTIPLIER;
        boolean back = SkillActions.behind(target, player);
        float damage = back ? base * (float) SkillRules.FANG_BACK : base;
        SkillActions.hit(player, target, damage, Skill.TWIN_FANG);
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillEffects.later(level, SkillRules.FANG_GAP, () -> {
            if (player.isAlive() && !player.hasDisconnected() && target.isAlive() && player.distanceTo(target) <= SkillRules.FANG_RANGE + 1.5) {
                float second = SkillActions.behind(target, player) ? base * (float) SkillRules.FANG_BACK : base;
                SkillActions.hit(player, target, second, Skill.TWIN_FANG);
                player.swing(InteractionHand.OFF_HAND, true);
                SkillActions.sound(player, Skills.SOUND_RIPOSTE.get(), 0.8f, 1.5f);
            }
        });
        SkillActions.sound(player, Skills.SOUND_RIPOSTE.get(), 0.8f, 1.3f);
        return null;
    }

    // 火花散らし: まわりの敵に傷と炎
    static String sparkBurst(ServerPlayer player, int rank) {
        float damage = SkillActions.attackDamage(player) * (float) SkillRules.sparkMultiplier(rank);
        ServerLevel level = player.serverLevel();
        for (LivingEntity e : SkillActions.foesAround(player, SkillRules.SPARK_RADIUS + 1, 1.0, 2.0)) {
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2 > SkillRules.SPARK_RADIUS) {
                continue;
            }
            if (SkillActions.hit(player, e, damage, Skill.SPARK_BURST)) {
                e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), SkillRules.SPARK_BURN));
            }
        }
        SkillActions.ring(level, net.minecraft.core.particles.ParticleTypes.LAVA, player.position(), SkillRules.SPARK_RADIUS * 0.6, 10, 0.4);
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.6f, 1.6f);
        return null;
    }

    // 足止めの矢: 見ている 1 体に矢の傷と鈍足
    static String snareShot(ServerPlayer player, int rank) {
        LivingEntity target = aimed(player, SkillRules.SNARE_RANGE, 15);
        if (target == null) {
            return "message.skyabyss.skill.no_target";
        }
        float damage = (float) (SkillRules.pierceDamage(rank) * SkillRules.SNARE_MULTIPLIER);
        if (SkillActions.hit(player, target, damage, Skill.SNARE_SHOT)) {
            SkillEffects.slow(target, SkillRules.SNARE_SLOW * (SkillActions.isLarge(target) ? 0.5 : 1.0), SkillRules.snareTicks(rank));
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, Skills.SOUND_PIERCE.get(), 0.9f, 1.1f);
        return null;
    }

    // 星屑: まわりの敵に魔法の傷を降らせ、軽く押し出す
    static String stardust(ServerPlayer player, int rank) {
        float damage = JobActions.spell(player, SkillRules.dustMultiplier(rank));
        ServerLevel level = player.serverLevel();
        for (LivingEntity e : SkillActions.foesAround(player, SkillRules.DUST_RADIUS + 1, 1.5, 3.0)) {
            double dx = e.getX() - player.getX();
            double dz = e.getZ() - player.getZ();
            if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2 > SkillRules.DUST_RADIUS) {
                continue;
            }
            if (SkillActions.hit(player, e, damage, Skill.STARDUST)) {
                e.knockback(SkillRules.DUST_PUSH, -dx, -dz);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() + 0.5, e.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
            }
        }
        SkillActions.ring(level, net.minecraft.core.particles.ParticleTypes.END_ROD, player.position(), SkillRules.DUST_RADIUS * 0.7, 16, 1.6);
        player.swing(InteractionHand.MAIN_HAND, true);
        SkillActions.sound(player, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
        return null;
    }
}
