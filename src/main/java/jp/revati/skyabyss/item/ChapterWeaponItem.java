package jp.revati.skyabyss.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

// 章ごとの杖と鈴杖 (ChapterGear) の品。エピック、由来は章の巨神 (必要 Lv はその巨神の推奨から GearLevel が決める)。
// 強化と特性は SwordItem なので GearEvents・AffixEvents がそのまま扱う
public class ChapterWeaponItem extends SwordItem implements ModGear {
    public static final SimpleTier TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 2000, 6.0f, 0.0f, 15, () -> Ingredient.EMPTY);

    private final String dungeon;
    private final String descKey;
    private final GearTier tier;

    public ChapterWeaponItem(Item.Properties properties, String dungeon, double attack, double speed, String descKey) {
        this(properties, dungeon, attack, speed, GearTier.EPIC, descKey);
    }

    // tier: 章の武器はエピック、巨神のヒロイック武器 (item/HeroicGear) はヒロイック
    public ChapterWeaponItem(Item.Properties properties, String dungeon, double attack, double speed, GearTier tier, String descKey) {
        super(TIER, properties.attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, attack - 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, speed - 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build()));
        this.dungeon = dungeon;
        this.descKey = descKey;
        this.tier = tier;
    }

    @Override
    public GearTier tier() {
        return tier;
    }

    @Override
    public String dungeon() {
        return dungeon;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ModGear.name(stack, super.getName(stack), tier);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ModGear.tooltip(stack, tooltip, tier, descKey, true);
    }
}
