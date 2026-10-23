package jp.revati.skyabyss.abyss.gear;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

// 沈め錨 (伝説): 深淵の心臓につながる錨の、欠けた爪を柄に付けた両手の錨。攻撃力 21、速さ 0.8。
// 三度目ごとに (+5 から二度目ごとに) 「錨の重し」: 打った相手のまわり半径 3 (+10 で 4) の敵に攻撃力の 50%、鈍足 III 1.5 秒、下へ引く。
// 上昇の重みを敵に背負わせる形。「両手」は説明と持ち方だけ (余燼の大鎚と同じく、左手の盾は使える)
public class SinkingAnchorItem extends AbyssGearWeaponItem {
    private static final String HITS = SkyAbyss.MODID + ":sinking_anchor_hits";

    public SinkingAnchorItem(Properties properties) {
        // 攻撃力 21 = 1 + 5 + 15、攻撃速度 0.8 = 4 − 3.2
        super(properties.attributes(SwordItem.createAttributes(TIER, 15, -3.2f)), GearTier.LEGENDARY, "item.skyabyss.abyss_sinking_anchor.desc");
    }

    public static int every(ItemStack stack) {
        return ModGear.awakened(stack) ? 2 : 3;
    }

    public static double radius(ItemStack stack) {
        return ModGear.enhance(stack) >= ModGear.MAX_ENHANCE ? AbyssGear.ANCHOR_RADIUS_MAX : AbyssGear.ANCHOR_RADIUS;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level && GearLevel.usable(attacker, stack)) {
            int hits = attacker.getPersistentData().getInt(HITS) + 1;
            if (hits >= every(stack)) {
                hits = 0;
                float damage = (float) (attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * AbyssGear.ANCHOR_RATE);
                sink(level, attacker, target, radius(stack), damage);
            }
            attacker.getPersistentData().putInt(HITS, hits);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    // 錨の重し: 打った相手と、まわりの敵へ。打った相手にも当たる (一体への上乗せ ×(1 + 0.5 / 間隔))
    public static int sink(ServerLevel level, LivingEntity attacker, LivingEntity target, double r, float damage) {
        DamageSource source = attacker instanceof Player p ? attacker.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker);
        Vec3 c = target.position();
        int hit = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(r, 1.5, r),
                e -> e != attacker && e.isAlive() && (e == target || e instanceof Enemy) && e.position().distanceToSqr(c) <= (r + 0.5) * (r + 0.5))) {
            e.invulnerableTime = 0;
            if (e.hurt(source, damage)) {
                hit++;
            }
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AbyssGear.ANCHOR_SLOW_TICKS, 2, true, true));
            Vec3 v = e.getDeltaMovement();
            e.setDeltaMovement(v.x * 0.3, Math.min(v.y, 0) - 0.5, v.z * 0.3);
            e.hurtMarked = true;
        }
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            level.sendParticles(ParticleTypes.SCULK_SOUL, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0, -0.02, 0, 0.01);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.5, c.z, 20, r * 0.4, 0.2, r * 0.4, 0.02);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.6f);
        return hit;
    }
}
