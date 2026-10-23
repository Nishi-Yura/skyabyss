package jp.revati.skyabyss.client.ui;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

// 階級の色の縁 (03 の 1.2節)。
//  - ツールチップ: このModの装備は、縁を階級の色にする (上が階級の色、下が暗い同じ色。背景は絵柄の UiSkin のまま。UiSkin より後に走る)
//  - 箱の画面の枠: 装備の入った枠に、階級の色の薄い地と 1 画素の縁を描く (品の絵の下。アンコモン以上。コモンの白は枠の灰に溶けるので描かない)。
//    持ち物・チェスト・共鳴の炉・装飾の画面など、AbstractContainerScreen ならどこでも同じ
//  強化の光 (+5・+10 の光る画素) は品の絵の中なので、この縁とは重ならない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GearTierFrames {
    private GearTierFrames() {
    }

    // 暗い同じ色 (縁の下側)
    public static int dark(int rgb) {
        int r = (rgb >> 16 & 0xFF) * 45 / 100;
        int g = (rgb >> 8 & 0xFF) * 45 / 100;
        int b = (rgb & 0xFF) * 45 / 100;
        return r << 16 | g << 8 | b;
    }

    public static GearTier tierOf(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ModGear g ? g.tier() : null;
    }

    // 16x16 の枠に、階級の地と縁 (x, y は枠の左上)
    public static void slotFrame(GuiGraphics g, int x, int y, GearTier tier) {
        if (tier == null || tier == GearTier.COMMON) {
            return;
        }
        int rgb = tier.rgb();
        g.fill(x, y, x + 16, y + 16, 0x30000000 | rgb);
        int edge = 0xB0000000 | rgb;
        g.fill(x, y, x + 16, y + 1, edge);
        g.fill(x, y + 15, x + 16, y + 16, edge);
        g.fill(x, y + 1, x + 1, y + 15, edge);
        g.fill(x + 15, y + 1, x + 16, y + 15, edge);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onTooltipColor(RenderTooltipEvent.Color event) {
        GearTier tier = tierOf(event.getItemStack());
        if (tier == null) {
            return;
        }
        event.setBorderStart(0xFF000000 | tier.rgb());
        event.setBorderEnd(0xFF000000 | dark(tier.rgb()));
    }

    @SubscribeEvent
    static void onContainerBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        GuiGraphics g = event.getGuiGraphics();
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) {
                continue;
            }
            GearTier tier = tierOf(slot.getItem());
            if (tier != null) {
                slotFrame(g, left + slot.x, top + slot.y, tier);
            }
        }
    }
}
