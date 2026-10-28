package jp.revati.skyabyss.client;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

// 持ち物の画面の 2×2 のクラフト枠を隠す (作る仕組みはない。2026-10-28 の点検で枠と「クラフト」の字が残っていた)。
// 枠の絵は UI の皮 (resourcepacks/skyabyss_ui_*。tools/ui/gen_vanilla_skin.py の hide_inventory_craft) で消してある。ここでは
// 字「クラフト」を画面の外へ出し、枠のあった四角のクリックを通さない (見えない枠に品を置けないように)。創造の画面は別の画面なので触らない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class InventoryCraftHide {
    // 画面の左上から (バニラの持ち物の絵の座標)。字 (97, 8)・枠 (98〜133, 18〜53)・矢印・できる物 (154, 28)
    static final int X0 = 95;
    static final int Y0 = 5;
    static final int X1 = 173;
    static final int Y1 = 58;
    private static java.lang.reflect.Field titleX;

    private InventoryCraftHide() {
    }

    @SubscribeEvent
    static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen s) {
            try {
                if (titleX == null) {
                    titleX = AbstractContainerScreen.class.getDeclaredField("titleLabelX");
                    titleX.setAccessible(true);
                }
                titleX.setInt(s, -10000);
            } catch (ReflectiveOperationException | RuntimeException e) {
                SkyAbyss.LOGGER.warn("could not hide the crafting label: {}", e.toString());
            }
        }
    }

    @SubscribeEvent
    static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof InventoryScreen s) {
            double x = event.getMouseX() - s.getGuiLeft();
            double y = event.getMouseY() - s.getGuiTop();
            if (x >= X0 && x < X1 && y >= Y0 && y < Y1) {
                event.setCanceled(true);
            }
        }
    }
}
