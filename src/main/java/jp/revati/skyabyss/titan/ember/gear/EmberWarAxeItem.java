package jp.revati.skyabyss.titan.ember.gear;

import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

// 余燼の戦斧 (レア): 玄武岩の刃に熾火の割れ目が走る斧。命中した相手を 4 秒くすぶらせる (燃える相手は燃やし、
// 火に強い相手にも「くすぶり」の印を付ける)。燃えている・くすぶる相手への傷 +10% (EmberGearEvents)。
// 余燼の背の敵は火に強いので、バニラの燃焼だけだと効かなくなる。そのため印を別に持つ (03 の 13.1節)
public class EmberWarAxeItem extends EmberWeaponItem {
    public EmberWarAxeItem(Properties properties) {
        // 攻撃力 16 = 1 + 5 + 10、攻撃速度 1.0 = 4 − 3
        super(properties.attributes(SwordItem.createAttributes(TIER, 10, -3.0f)), GearTier.RARE, "item.skyabyss.ember_war_axe.desc");
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level && GearLevel.usable(attacker, stack)) {
            EmberGearEvents.smolder(target, EmberGear.WAR_AXE_SMOLDER_TICKS);
            level.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY(0.6), target.getZ(), 3, 0.25, 0.25, 0.25, 0.0);
            level.sendParticles(ParticleTypes.SMALL_FLAME, target.getX(), target.getY(0.5), target.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35f, 1.5f);
        }
        return super.hurtEnemy(stack, target, attacker);
    }
}
