package jp.revati.skyabyss.client.ui;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

// 画面を描くあいだだけ、その画面の文字の範囲を立てる (TextBounded)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TextBounds {
    private static int[] current;

    private TextBounds() {
    }

    // GuiGraphicsMixin から。範囲があれば {x, y, 幅, 高さ}
    public static int[] current() {
        return current;
    }

    @SubscribeEvent
    static void pre(ScreenEvent.Render.Pre event) {
        Screen s = event.getScreen();
        current = s instanceof TextBounded b ? b.textBounds() : null;
    }

    @SubscribeEvent
    static void post(ScreenEvent.Render.Post event) {
        current = null;
    }
}
