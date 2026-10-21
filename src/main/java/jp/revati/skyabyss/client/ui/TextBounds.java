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
        current = s instanceof TextBounded b ? withWidgets(s, b.textBounds()) : null;
    }

    // 範囲の外に置いたボタン (「完了」など) の字が切れないよう、画面のボタンの四角も範囲に含める
    // (2026-10-22: 物語と仲間の画面の「完了」が空のボタンに見えた)
    private static int[] withWidgets(Screen s, int[] b) {
        if (b == null) {
            return null;
        }
        int x0 = b[0], y0 = b[1], x1 = b[0] + b[2], y1 = b[1] + b[3];
        for (var c : s.children()) {
            if (c instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible) {
                x0 = Math.min(x0, w.getX());
                y0 = Math.min(y0, w.getY());
                x1 = Math.max(x1, w.getX() + w.getWidth());
                y1 = Math.max(y1, w.getY() + w.getHeight());
            }
        }
        return new int[] {x0, y0, x1 - x0, y1 - y0};
    }

    @SubscribeEvent
    static void post(ScreenEvent.Render.Post event) {
        current = null;
    }
}
