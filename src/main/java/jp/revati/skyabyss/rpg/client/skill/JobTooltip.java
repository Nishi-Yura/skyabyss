package jp.revati.skyabyss.rpg.client.skill;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// 武器の説明に「職: 魔法使い（星読み）」を一行 (Wynncraft の武器の Class Req と同じ。docs/design/43)。
// 持つとその職になる武器だけ。いまの職と同じなら金色、違えば灰色
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class JobTooltip {
    private JobTooltip() {
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        Job job = Job.of(event.getItemStack());
        if (job == null || event.getToolTip().isEmpty()) {
            return;
        }
        boolean mine = event.getEntity() != null && Job.held(event.getEntity()) == job;
        Component line = Component.translatable("gui.skyabyss.job.tooltip",
                Component.translatable("job.skyabyss.subtitle", Component.translatable(job.nameKey()), Component.translatable(job.tree.nameKey())))
                .withStyle(s -> s.withColor(mine ? 0xF2C14E : 0x9AA4B2));
        event.getToolTip().add(1, line);
    }
}
