package jp.revati.skyabyss.rpg.client.skill;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.skill.Job;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// 武器の説明に「職: 魔法使い（星読み）」を一行。あわせて、特性の出来を Shift で見せるつなぎ (Wynncraft の武器の Class Req と同じ。docs/design/43)。
// 持つとその職になる武器だけ。いまの職と同じなら金色、違えば灰色
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class JobTooltip {
    // 特性の出来は Shift を押しているあいだ出す (item/affix/AffixRules.line)
    static {
        // 撮影用: SKYABYSS_SHOW_ROLL=1 なら Shift なしでも出す
        boolean always = System.getenv("SKYABYSS_SHOW_ROLL") != null;
        jp.revati.skyabyss.item.affix.AffixRules.showRoll = () -> always || net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

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
