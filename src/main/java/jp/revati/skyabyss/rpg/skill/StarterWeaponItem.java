package jp.revati.skyabyss.rpg.skill;

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

// 島の祠で職を選ぶと受け取る始めの武器 (docs/design/43 の 4 節)。ふつうの階級、必要 Lv なし。強さは鉄の剣ほど。
// 強化と特性は SwordItem なので GearEvents・AffixEvents がそのまま扱う
public class StarterWeaponItem extends SwordItem implements ModGear {
    // 品の素の攻撃力の上乗せ 2 (鉄の剣と同じ)。鉄で直す
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 400, 6.0f, 2.0f, 14,
            () -> Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT));

    private final String descKey;

    public StarterWeaponItem(Properties properties, int damage, float speed, String descKey) {
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, damage, speed - 4.0f)));
        this.descKey = descKey;
    }

    @Override
    public GearTier tier() {
        return GearTier.COMMON;
    }

    @Override
    public int requiredLevel() {
        return 0;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), GearTier.COMMON);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, GearTier.COMMON, descKey, true);
    }
}
