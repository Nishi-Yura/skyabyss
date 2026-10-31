package jp.revati.skyabyss.endgame.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.endgame.Looks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// 見た目替え (endgame/Looks) した武器の説明に「見た目: 〜」を足す (名前のすぐ下)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class LookTooltip {
    private LookTooltip() {
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        Item look = Looks.look(event.getItemStack());
        if (look != null && !event.getToolTip().isEmpty()) {
            event.getToolTip().add(1, Component.translatable("tooltip.skyabyss.look", new ItemStack(look).getHoverName()).withStyle(ChatFormatting.AQUA));
        }
    }
}
