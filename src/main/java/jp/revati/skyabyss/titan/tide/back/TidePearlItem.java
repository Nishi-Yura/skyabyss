package jp.revati.skyabyss.titan.tide.back;

import java.util.List;

import jp.revati.skyabyss.ModText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 潮の真珠 (珊瑚の庭の大貝から取り出し、祭壇の潮鳴り石まで運ぶ)。
// 持っている間は鈍足 I と、全身がうすく光る。頭まで水に入る・落として 15 秒・庭を出る・倒れると、大貝へ戻る (TideGarden)
public class TidePearlItem extends Item {
    public TidePearlItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(ModText.hint("item.skyabyss.tide_pearl.desc"));
        tooltip.add(ModText.hint("item.skyabyss.tide_pearl.desc2"));
    }
}
