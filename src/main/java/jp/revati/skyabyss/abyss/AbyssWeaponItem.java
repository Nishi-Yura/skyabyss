package jp.revati.skyabyss.abyss;

import java.util.List;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

// 深淵の専用伝説 (剣型: 大剣・杖・鎌。docs/design/31 の B04)。階級は伝説、由来は巨神 (dungeon)。強化・特性・必要 Lv は ModGear の共通の扱い。
// 固有の性質は持たない: 「技の書き換え」(Rune の 4 番目。主の手に持つあいだだけ) が、この品の役目。ツールチップに書き換える技の一行 (SkillClient)
public class AbyssWeaponItem extends SwordItem implements ModGear {
    // 耐久はネザライトの剣より少し多い。echo の欠片で直す
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2600, 8.0f, 5.0f, 15, () -> Ingredient.of(Items.ECHO_SHARD));

    private final String dungeon;
    private final String descKey;

    // attackBonus: 攻撃力 = 1 + 5 + attackBonus。speed: 攻撃速度の差 (4 + speed が実際の速さ)
    public AbyssWeaponItem(Properties properties, String dungeon, int attackBonus, float speed, String descKey) {
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, attackBonus, speed)));
        this.dungeon = dungeon;
        this.descKey = descKey;
    }

    @Override
    public GearTier tier() {
        return GearTier.LEGENDARY;
    }

    @Override
    public String dungeon() {
        return dungeon;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), GearTier.LEGENDARY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, GearTier.LEGENDARY, descKey, true);
    }
}
