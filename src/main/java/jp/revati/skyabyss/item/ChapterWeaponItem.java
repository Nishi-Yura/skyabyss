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
        this(properties, dungeon, attack, speed, tier, descKey, b -> {
        });
    }

    // extra: 攻撃力と速さのほかに手に持つ間の修飾子を足す (野の装備の体力・間合いなど。item/wild/WildGear)
    public ChapterWeaponItem(Item.Properties properties, String dungeon, double attack, double speed, GearTier tier, String descKey,
            java.util.function.Consumer<ItemAttributeModifiers.Builder> extra) {
        super(TIER, properties.attributes(withExtra(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, attack - 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, speed - 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND),
                extra).build()));
        this.dungeon = dungeon;
        this.descKey = descKey;
        this.tier = tier;
    }

    private static ItemAttributeModifiers.Builder withExtra(ItemAttributeModifiers.Builder b, java.util.function.Consumer<ItemAttributeModifiers.Builder> extra) {
        extra.accept(b);
        return b;
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
