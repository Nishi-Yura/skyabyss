package jp.revati.skyabyss.item.storm;

import java.util.List;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

// 雷翼の手の武器 (雷獣の爪・羽守の鉤槍) の共通の形: 由来は雷翼、名前は階級の色、ツールチップは岩鯨・潮殻と同じ並び。
// 強化 (攻撃力の 3%/段) と特性は SwordItem なので GearEvents・AffixEvents がそのまま扱う (潮殻の TideWeaponItem と同じ)
public abstract class StormWeaponItem extends SwordItem implements ModGear {
    private final GearTier gearTier;
    private final String descKey;

    protected StormWeaponItem(Tier tier, Properties properties, GearTier gearTier, String descKey) {
        super(tier, properties);
        this.gearTier = gearTier;
        this.descKey = descKey;
    }

    @Override
    public GearTier tier() {
        return gearTier;
    }

    @Override
    public String dungeon() {
        return StormGear.TITAN;
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
