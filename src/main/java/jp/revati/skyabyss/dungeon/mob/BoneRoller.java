package jp.revati.skyabyss.dungeon.mob;

import jp.revati.skyabyss.entity.foe.RegionBoss;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 骨甲の団子虫 (docs/design/55 の 4 の 7、00 の 373 = B。鯨骨の坑の道中の群れ)。
// 少し離れて丸まり、まっすぐ転がって突っ込む (前の長い帯、予告 0.9 秒。横へ避ける)。丸まっている間は甲が硬い (受ける傷が半分)
public class BoneRoller extends RegionBoss {
    static final int ROLL = 1;
    public static final int WARN = 18;
    public static final double LENGTH = 11;
    public static final double SPEED = 0.75;
    public static final float CURLED_TAKEN = 0.5f;

    public BoneRoller(EntityType<? extends Monster> type, Level level) {
        super(type, level, "bone_roller", () -> SoundEvents.SILVERFISH_AMBIENT, () -> SoundEvents.SILVERFISH_HURT, () -> SoundEvents.SILVERFISH_DEATH,
                "roll", "hurt", "death");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 12)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 22);
    }

    @Override
    protected void registerGoals() {
        baseGoals(new ApproachGoal(this, 1.0, 5.0));
    }

    @Override
    protected void choose(LivingEntity target, double distance) {
        if (distance < 2.5 || distance > LENGTH) {
            return;
        }
        charge(target, ROLL, "roll", WARN, LENGTH, 0.8, SPEED, () -> SoundEvents.SILVERFISH_STEP);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, act > 0 ? amount * CURLED_TAKEN : amount);
    }

    @Override
    protected int length() {
        return WARN + (int) Math.ceil(LENGTH / SPEED) + 10;
    }

    @Override
    protected void finish() {
        super.finish();
        cooldown = 40 + random.nextInt(30);
    }
}
