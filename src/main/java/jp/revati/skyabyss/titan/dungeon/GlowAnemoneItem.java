package jp.revati.skyabyss.titan.dungeon;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// 光る海のイソギンチャク (docs/design/23。闇の水路で持ち運ぶ灯)。手に持つと、まわりをうっすら照らす (AnemoneLights の持ち歩きの光)。
// 灯の台にともすと、そのグループの水路に灯が残る。使い切り (台にともすと一つ減る)。台は 3 つ、グループがともせる灯も 3 つ (スタックも 3)。足りなくなったら、苗床で摘める
public class GlowAnemoneItem extends Item {
    public GlowAnemoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyabyss.glow_anemone.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyabyss.glow_anemone.desc2").withStyle(ChatFormatting.DARK_AQUA));
    }
}
