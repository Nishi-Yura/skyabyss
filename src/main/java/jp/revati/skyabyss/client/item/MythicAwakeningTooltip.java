package jp.revati.skyabyss.client.item;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.mythic.MythicAwakening;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// 神器の目覚めのツールチップ (item/mythic/MythicAwakening。docs/design/03 の 13.5): 名前のすぐ下に、真名 (段 3)・段と次までの数・効き
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MythicAwakeningTooltip {
    static final int GOLD = 0xF2C14E;
    static final int TEAL = 0x7FD6D0;
    static final int SOFT = 0xA9AEBC;

    private MythicAwakeningTooltip() {
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack s = event.getItemStack();
        if (s.isEmpty() || !MythicAwakening.isArtifact(s.getItem())) {
            return;
        }
        List<Component> lines = event.getToolTip();
        int at = Math.min(1, lines.size());
        int stage = MythicAwakening.stage(s);
        String id = MythicAwakening.id(s.getItem());
        if (stage >= 3) {
            lines.add(at++, Component.translatable("tooltip.skyabyss.awaken.true_name", Component.translatable("item.skyabyss." + id + ".true_name")).withColor(GOLD));
            lines.add(at++, Component.translatable("tooltip.skyabyss.awaken.done").withColor(TEAL));
        } else {
            lines.add(at++, Component.translatable("tooltip.skyabyss.awaken.progress", stage, String.format(java.util.Locale.ROOT, "%,d", MythicAwakening.toNext(s)))
                    .withColor(TEAL));
        }
        if (stage >= 1) {
            lines.add(at, Component.translatable("tooltip.skyabyss.awaken.effect." + Math.min(3, stage)).withColor(SOFT));
        }
    }
}
