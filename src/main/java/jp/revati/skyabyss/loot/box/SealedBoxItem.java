package jp.revati.skyabyss.loot.box;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 封の箱 (docs/design/52)。中身は持たない (人ごとの封の列 LootBoxData にある) ので、同じ種類なら重なる。
// 開けるのは町の封切り師 (LootBoxes.open)。名前の色は箱の色 (巨神の箱は巨神の色、階級の箱は階級の色)
public class SealedBoxItem extends Item {
    public final BoxKind kind;

    public SealedBoxItem(Item.Properties properties, BoxKind kind) {
        super(properties);
        this.kind = kind;
    }

    public static int color(BoxKind kind) {
        if (kind.type() != BoxKind.Type.TITAN) {
            return kind.tier().rgb();
        }
        return switch (kind.titan()) {
            case "tide_shell" -> 0x7FE0D0;
            case "thunder_wing" -> 0xE8C46A;
            case "ember" -> 0xFF8A4A;
            default -> 0xA8DCEB;
        };
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(s -> s.withColor(color(kind)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Component head = switch (kind.type()) {
            case TITAN -> Component.translatable("item.skyabyss.box.kind_titan", Component.translatable("gear.skyabyss.origin." + kind.titan()));
            case RARITY -> Component.translatable("item.skyabyss.box.kind_rarity", Component.translatable(kind.tier().translationKey()),
                    kind.minLevel(), kind.maxLevel());
            case ARTIFACT -> Component.translatable("item.skyabyss.box.kind_artifact");
        };
        tooltip.add(head.copy().withStyle(s -> s.withColor(color(kind))));
        tooltip.add(Component.translatable("item.skyabyss.box.contents").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyabyss.box.open_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
