package jp.revati.skyabyss.titan.ember.gear;

import java.util.List;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

// 余燼の手の武器 (戦斧・大鎚・火掻き) の共通の形: 由来は余燼、名前は階級の色、ツールチップは岩鯨・潮殻と同じ並び。
// 強化 (攻撃力の 3%/段) と特性は SwordItem なので GearEvents・AffixEvents がそのまま扱う (潮殻の TideWeaponItem と同じ)
public abstract class EmberWeaponItem extends SwordItem implements ModGear {
    // 品の素の攻撃力の上乗せ 5 (攻撃力 = 1 + 5 + 品ごとの値)。余燼の燠石で直す。耐久はネザライトの剣より少し多い
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2600, 8.0f, 5.0f, 15,
            () -> Ingredient.of(EmberGearSetup.SHARD.get()));

    private final GearTier gearTier;
    private final String descKey;

    protected EmberWeaponItem(Properties properties, GearTier gearTier, String descKey) {
        super(TIER, properties);
        this.gearTier = gearTier;
        this.descKey = descKey;
    }

    @Override
    public GearTier tier() {
        return gearTier;
    }

    @Override
    public String dungeon() {
        return EmberGear.TITAN;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), gearTier);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, gearTier, descKey, true);
    }
}
