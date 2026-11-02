package jp.revati.skyabyss.entity.foe;

import jp.revati.skyabyss.registry.MobSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

// 野犬 (docs/design/44): 草原の森に群れで出る。近ければ跳びかかって噛む。群れの一頭が天を仰いで吠える (1.0 秒) と、
// まわりの野犬が一斉に、それぞれ床の帯を出して跳びかかる (帯の外へ出れば当たらない)
public class FeralDog extends PackBeast {
    public FeralDog(EntityType<? extends Monster> type, Level level) {
        super(type, level, "feral_dog", MobSounds.FERAL_DOG_AMBIENT, MobSounds.FERAL_DOG_HURT, MobSounds.FERAL_DOG_DEATH, MobSounds.FERAL_DOG_BITE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void howlStarted(LivingEntity target) {
        server().playSound(null, getX(), getY(), getZ(), MobSounds.FERAL_DOG_HOWL.get(), SoundSource.HOSTILE, 1.4f, 1f);
    }

    @Override
    protected void howlPeak(LivingEntity target) {
        for (FeralDog mate : level().getEntitiesOfClass(FeralDog.class, getBoundingBox().inflate(PACK_RADIUS), e -> e != this && e.isAlive())) {
            mate.answer(mate.getTarget() != null ? mate.getTarget() : target);
        }
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }
}
