package jp.revati.skyabyss.story.ch6.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 残り鎧 (下弦の淵。docs/design/46 の 4.6・一品一案「砂の詰まった巡礼の鎧」)。前の巡礼の空の鎧に月の砂が詰まって歩くもの。
//   予告つきの大振り (三日月の長柄で前を薙ぐ。予告 0.9 秒、届き 3.5)。前からの傷 ×0.5 (裏へ回る)
//   倒れると 3 秒前の所から残像で一度立ち上がる (RiftMob)。灯りの回廊の残り影の溜まりからも、この残像が立つ
public class RiftArmor extends RiftMob {
    public static final String ID = "rift_armor";
    public static final double HP = 58;
    public static final double DMG = 9;

    public RiftArmor(EntityType<? extends Monster> type, Level level) {
        super(type, level, ID, "swing", "hurt", "death");
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.ATTACK_DAMAGE, DMG)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Tell(this, 1.0, 3.5, 18, 30, "swing", 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // 前 (向いている側の 110 度) からの傷は半分
    public boolean fromFront(DamageSource source) {
        Entity src = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (src == null) {
            return false;
        }
        Vec3 to = src.position().subtract(position());
        Vec3 fwd = Vec3.directionFromRotation(0, yBodyRot);
        return new Vec3(to.x, 0, to.z).normalize().dot(fwd) > 0.35;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && fromFront(source)) {
            amount *= 0.5f;
            if (level() instanceof ServerLevel sl) {
                playSound(MoonMobsSetup.ARMOR_GUARD.get(), 0.8f, 1.2f);
                Vec3 f = position().add(Vec3.directionFromRotation(0, yBodyRot).scale(0.8));
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, f.x, f.y + 1.4, f.z, 6, 0.2, 0.3, 0.2, 0.1);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!echo()) {
            jp.revati.skyabyss.story.ch4.Ch4Mobs.drop(level, this, source, MoonMobsSetup.MOON_DUST.get(), 0.5, 1, 2);
        }
    }
}
