package jp.revati.skyabyss.item.storm;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

// 羽守の鉤槍 (伝説): 羽守が握っていた、骨の柄に銅の鉤の付いた槍 (敵の鉤槍 gear/hooked_glaive と同じ形と色)。間合い +1。
// 空中の敵と、落下中の自分の攻撃で傷 ×1.3 (+5 から ×1.4)。空駆け・滑空と組む (StormGearEvents)
public class WardenGlaiveItem extends StormWeaponItem {
    // 攻撃力 10 = 1 + 4 + 5、攻撃速度 1.5 = 4 − 2.5。1 秒あたり 15.0 (空中の上乗せは場面しだいなので数えない)
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 2400, 7.0f, 4.0f, 16,
            () -> Ingredient.of(StormGearSetup.SHARD.get()));

    public WardenGlaiveItem(Properties properties) {
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, 5, -2.5f)
                .withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "warden_glaive_reach"), StormGear.GLAIVE_REACH,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)), GearTier.LEGENDARY, "item.skyabyss.warden_glaive.desc");
    }

    public static double aerial(ItemStack stack) {
        return ModGear.awakened(stack) ? StormGear.GLAIVE_AERIAL_AWAKENED : StormGear.GLAIVE_AERIAL;
    }

    // 空中の戦い: 相手が宙にいる (地面にも水にもいない)、または自分が落ちている。
    // 大きな相手 (最大体力 StormGear.GLAIVE_BIG_TARGET_HP 以上、巨神の心臓など) は宙に浮いたまま戦うので、
    // 相手が宙にいるだけでは数えず、自分が宙にいる (昇り風の中・空駆け・滑空) ときだけ上乗せする (00 の判断)
    public static boolean aerialHit(LivingEntity attacker, LivingEntity target) {
        boolean selfAir = !attacker.onGround() && !attacker.isInWater() && !attacker.isPassenger();
        if (target.getMaxHealth() >= StormGear.GLAIVE_BIG_TARGET_HP) {
            return selfAir;
        }
        boolean targetAir = !target.onGround() && !target.isInWater() && !target.isPassenger();
        // サーバーではプレイヤーの速さは当てにならないので、バニラの会心と同じく落下の距離で見る
        boolean falling = selfAir && attacker.fallDistance > 0;
        return targetAir || falling;
    }
}
