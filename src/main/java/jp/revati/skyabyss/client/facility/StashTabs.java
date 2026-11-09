package jp.revati.skyabyss.client.facility;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.StashPages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// 預かり所の箱の右に並ぶ頁の札 (facility/StashPages)。押すとその頁の箱に開き直す。Page Up / Page Down でも替わる。
// 手に品を持っている間は替えない (札を薄くする)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StashTabs {
    static final int W = 22;
    static final int H = 18;
    static final int GAP = 3;

    private static int containerId = -1;
    private static int page;
    private static int pages;

    private StashTabs() {
    }

    public static void receive(StashPages.Tabs t) {
        containerId = t.containerId();
        page = t.page();
        pages = t.pages();
    }

    // この画面が、頁を知らされた預かり所の箱か
    private static AbstractContainerScreen<?> stash(Screen s) {
        if (s instanceof AbstractContainerScreen<?> cs && pages > 1 && cs.getMenu().containerId == containerId) {
            return cs;
        }
        return null;
    }

    private static int x(AbstractContainerScreen<?> cs) {
        return cs.getGuiLeft() + cs.getXSize() + 2;
    }

    private static int y(AbstractContainerScreen<?> cs, int i) {
        return cs.getGuiTop() + 4 + i * (H + GAP);
    }

    private static boolean holding(AbstractContainerScreen<?> cs) {
        return !cs.getMenu().getCarried().isEmpty();
    }

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        AbstractContainerScreen<?> cs = stash(event.getScreen());
        if (cs == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        boolean hold = holding(cs);
        int mx = event.getMouseX();
        int my = event.getMouseY();
        for (int i = 0; i < pages; i++) {
            int n = i + 1;
            int x = x(cs);
            int y = y(cs, i);
            boolean now = n == page;
            boolean hover = !now && !hold && mx >= x && mx < x + W && my >= y && my < y + H;
            int edge = now ? FacilityUi.GOLD : hover ? FacilityUi.TEAL : FacilityUi.LINE;
            g.fill(x, y, x + W, y + H, edge);
            g.fill(x + 1, y + 1, x + W - 1, y + H - 1, now ? FacilityUi.ROW_HOVER : hover ? FacilityUi.ROW_HOVER : FacilityUi.ROW);
            String t = String.valueOf(n);
            int colour = now ? FacilityUi.GOLD : hold ? FacilityUi.DIM : FacilityUi.TEXT;
            g.drawString(font, t, x + (W - font.width(t)) / 2, y + (H - 8) / 2 + 1, colour, false);
            if (hover) {
                g.renderTooltip(font, Component.translatable("gui.skyabyss.stash.page_tab", n), mx, my);
            }
        }
    }

    @SubscribeEvent
    static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        AbstractContainerScreen<?> cs = stash(event.getScreen());
        if (cs == null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        for (int i = 0; i < pages; i++) {
            int x = x(cs);
            int y = y(cs, i);
            if (event.getMouseX() >= x && event.getMouseX() < x + W && event.getMouseY() >= y && event.getMouseY() < y + H) {
                event.setCanceled(true);
                go(cs, i + 1);
                return;
            }
        }
    }

    @SubscribeEvent
    static void onKey(ScreenEvent.KeyPressed.Pre event) {
        AbstractContainerScreen<?> cs = stash(event.getScreen());
        if (cs == null) {
            return;
        }
        if (event.getKeyCode() == GLFW.GLFW_KEY_PAGE_DOWN) {
            event.setCanceled(true);
            go(cs, page % pages + 1);
        } else if (event.getKeyCode() == GLFW.GLFW_KEY_PAGE_UP) {
            event.setCanceled(true);
            go(cs, (page + pages - 2) % pages + 1);
        }
    }

    private static void go(AbstractContainerScreen<?> cs, int to) {
        if (to == page || holding(cs)) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        PacketDistributor.sendToServer(new StashPages.Turn(to));
    }
}
