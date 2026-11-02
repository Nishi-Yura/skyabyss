package jp.revati.skyabyss.story.ch6.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 月の霧の色を最後に黒へ戻す。暗視の効果はバニラが霧の色を明るい方へ引き伸ばすので (暗い紺 → 明るい青)、月の空が昼の青になっていた
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MoonFogClient {
    private MoonFogClient() {
    }

    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.dimension() == MoonWorld.LEVEL) {
            event.setRed(0.012f);
            event.setGreen(0.014f);
            event.setBlue(0.024f);
        }
    }
}
