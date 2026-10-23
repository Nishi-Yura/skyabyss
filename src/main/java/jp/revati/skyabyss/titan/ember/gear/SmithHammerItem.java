package jp.revati.skyabyss.titan.ember.gear;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

// 鍛え手の大鎚 (ヒロイック): 灰の鍛え手が振るっていた両手の大鎚 (中ボスの大鎚と同じ形)。攻撃力 19、速さ 0.8。
// 三度目ごとに (+5 から二度目ごとに)、打った先の床へ亀裂が走り、0.8 秒後に噴く (長さ 5、+10 で 7。攻撃力の 50%。打った相手にも当たる)。
// 鍛え手の技「亀裂」と同じく二段の攻め: 走った線が赤く光ってから噴く (EmberGearEvents の Crack)
public class SmithHammerItem extends EmberWeaponItem {
    private static final String HITS = SkyAbyss.MODID + ":smith_hammer_hits";

    public SmithHammerItem(Properties properties) {
        // 攻撃力 19 = 1 + 5 + 13、攻撃速度 0.8 = 4 − 3.2
        super(properties.attributes(SwordItem.createAttributes(TIER, 13, -3.2f)), GearTier.HEROIC, "item.skyabyss.smith_hammer.desc");
    }

    // 何度目ごとに亀裂が走るか
    public static int every(ItemStack stack) {
        return ModGear.awakened(stack) ? 2 : 3;
    }

    public static double crackLength(ItemStack stack) {
        return ModGear.enhance(stack) >= ModGear.MAX_ENHANCE ? EmberGear.HAMMER_CRACK_LENGTH_MAX : EmberGear.HAMMER_CRACK_LENGTH;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.level() instanceof ServerLevel level && GearLevel.usable(attacker, stack)) {
            int hits = attacker.getPersistentData().getInt(HITS) + 1;
            if (hits >= every(stack)) {
                hits = 0;
                float damage = (float) (attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * EmberGear.HAMMER_CRACK_RATE);
                Vec3 look = attacker.getLookAngle().multiply(1, 0, 1);
                if (look.lengthSqr() < 1e-4) {
                    look = target.position().subtract(attacker.position()).multiply(1, 0, 1);
                }
                EmberGearEvents.crack(level, attacker, target, look, crackLength(stack), damage);
            }
            attacker.getPersistentData().putInt(HITS, hits);
        }
        return super.hurtEnemy(stack, target, attacker);
    }
}
