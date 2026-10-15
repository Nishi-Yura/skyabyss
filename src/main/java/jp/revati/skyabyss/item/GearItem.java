package jp.revati.skyabyss.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 鍛えることのない、持っているだけで働く装飾品 (苔の護石、岩鯨の補強板)。効果は GearEvents が見る
public class GearItem extends Item implements ModGear {
    private final GearTier tier;
    private final String descKey;
    private final boolean foil;

    public GearItem(Properties properties, GearTier tier, String descKey, boolean foil) {
        super(properties);
        this.tier = tier;
        this.descKey = descKey;
        this.foil = foil;
    }

    @Override
    public GearTier tier() {
        return tier;
    }

    @Override
    public boolean enhanceable() {
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier, descKey, false);
    }
}
