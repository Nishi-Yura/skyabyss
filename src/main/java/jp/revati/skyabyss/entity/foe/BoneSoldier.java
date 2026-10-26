package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 骸の歩兵・弓兵の共通: 骸の旗手が旗を突き立てると、旗のそばの骸に「旗の守り」が付き、倒れるはずの一撃で一度だけ起き上がる (体力半分)
public abstract class BoneSoldier extends ContinentFoe {
    public static final String WARD_TAG = "skyabyss_banner_ward";

    protected BoneSoldier(EntityType<? extends Monster> type, Level level, String animPrefix, String... actions) {
        super(type, level, animPrefix, MobSounds.BONE_SOLDIER_AMBIENT, MobSounds.BONE_SOLDIER_HURT, MobSounds.BONE_SOLDIER_DEATH, actions);
    }

    public boolean warded() {
        return getTags().contains(WARD_TAG);
    }

    public void ward() {
        addTag(WARD_TAG);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isAlive() && warded() && amount >= getHealth() && !isInvulnerableTo(source)) {
            removeTag(WARD_TAG);
            setHealth(getMaxHealth() * 0.5f);
            Telegraph.cancelAll(this);
            act = 0;
            cooldown = 20;
            playAction("rise");
            level().playSound(null, getX(), getY(), getZ(), MobSounds.BONE_SOLDIER_RISE.get(), SoundSource.HOSTILE, 1f, 1f);
            server().sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.8, getZ(), 14, 0.35, 0.6, 0.35, 0.03);
            invulnerableTime = 20;
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        modStep(MobSounds.BONE_SOLDIER_STEP.get(), 0.35f);
    }
}
