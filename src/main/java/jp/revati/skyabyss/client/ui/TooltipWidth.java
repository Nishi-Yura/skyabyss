package jp.revati.skyabyss.client.ui;

import jp.revati.skyabyss.SkyAbyss;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

// アイテムの説明 (ツールチップ) の最大幅。長い説明が一行で画面いっぱいに伸びないよう、画面の幅の約 4 割 (160〜260 画素) で折り返す。
// 日本語は字の間で折る。はみ出す場合の縮小・位置ずらしは GuiGraphicsMixin (画面の中に収める)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TooltipWidth {
    private TooltipWidth() {
    }

    public static int maxFor(int screenWidth) {
        return Math.max(160, Math.min(260, Math.round(screenWidth * 0.4f)));
    }

    @SubscribeEvent
    static void gather(RenderTooltipEvent.GatherComponents event) {
        if (event.getMaxWidth() <= 0 || event.getMaxWidth() > maxFor(event.getScreenWidth())) {
            event.setMaxWidth(maxFor(event.getScreenWidth()));
        }
    }
}
