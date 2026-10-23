package jp.revati.skyabyss.abyss.gear;

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

// 五章「深淵」の手の武器 (灯り番の剣・沈め錨・鼓動の短剣) の共通の形。由来は深淵、必要 Lv はレア 40・伝説 42。
// 強化 (攻撃力の 3%/段) と特性は SwordItem なので GearEvents・AffixEvents がそのまま扱う (余燼の EmberWeaponItem と同じ)
public abstract class AbyssGearWeaponItem extends SwordItem implements ModGear {
    // 品の素の攻撃力の上乗せ 5 (攻撃力 = 1 + 5 + 品ごとの値)。錨鉄で直す。耐久は余燼の武器より少し多い
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2900, 8.0f, 5.0f, 15,
            () -> Ingredient.of(AbyssGearSetup.SHARD.get()));

    private final GearTier gearTier;
    private final String descKey;

    protected AbyssGearWeaponItem(Properties properties, GearTier gearTier, String descKey) {
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
        return AbyssGear.DUNGEON;
    }

    @Override
    public int requiredLevel() {
        return AbyssGear.requiredLevel(gearTier);
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
