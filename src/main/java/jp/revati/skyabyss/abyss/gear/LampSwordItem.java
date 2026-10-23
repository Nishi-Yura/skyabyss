package jp.revati.skyabyss.abyss.gear;

import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

// 灯り番の剣 (レア): 探掘隊の灯り番が角灯を吊るして振った片手の剣。鍔の小さな角灯が光る。
// 灯りの中 (明るさ 8 以上。ワールドの明るさか深淵の灯り) で振ると傷 +10% (AbyssGearEvents)。
// 闇の中で斬った敵は 4 秒光る (+5 で 6 秒、まわり 4 ブロックの敵も)。闇に紛れる深淵の敵と、姿を見せない番人の輪郭を追える
public class LampSwordItem extends AbyssGearWeaponItem {
    public LampSwordItem(Properties properties) {
        // 攻撃力 11 = 1 + 5 + 5、攻撃速度 1.6 = 4 − 2.4
        super(properties.attributes(SwordItem.createAttributes(TIER, 5, -2.4f)), GearTier.RARE, "item.skyabyss.abyss_lamp_sword.desc");
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level && GearLevel.usable(attacker, stack) && AbyssGear.inDark(attacker)) {
            boolean awake = ModGear.awakened(stack);
            int ticks = awake ? AbyssGear.SWORD_MARK_TICKS_AWAKENED : AbyssGear.SWORD_MARK_TICKS;
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false));
            if (awake) {
                double r = AbyssGear.SWORD_MARK_RADIUS;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(r),
                        e -> e != attacker && e != target && e.isAlive() && e instanceof Enemy)) {
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false));
                }
            }
            level.sendParticles(ParticleTypes.WAX_OFF, target.getX(), target.getY(0.6), target.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.4f);
        }
        return super.hurtEnemy(stack, target, attacker);
    }
}
