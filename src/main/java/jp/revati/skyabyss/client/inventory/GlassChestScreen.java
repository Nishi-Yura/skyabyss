package jp.revati.skyabyss.client.inventory;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.Golden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

// 箱の画面 (預かり所と、世界の箱。docs/design/56 の 4 章。2026-11-11 からバニラの箱の形)。バニラの箱の画面の代わりに開く。
// 入れ物も枠の置き場もバニラと同じ (ChestMenu): 上に箱の枠 (9 列 × 行)、下に袋 27 と手もと 9。見た目だけ夜空のガラスの板。
// 預かり所のページの札 (client/facility/StashTabs) は板の右の外に並ぶ
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public class GlassChestScreen extends AbstractContainerScreen<ChestMenu> implements jp.revati.skyabyss.client.ui.TextBounded {
    public GlassChestScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 114 + menu.getRowCount() * 18;
        inventoryLabelY = imageHeight - 94;
    }

    @SubscribeEvent
    static void onOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getNewScreen() instanceof ContainerScreen cs && mc.player != null && cs.getMenu().getRowCount() <= 6) {
            event.setNewScreen(new GlassChestScreen(cs.getMenu(), mc.player.getInventory(), cs.getTitle()));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(leftPos, topPos, 0);
        Golden.panel(g, new Golden.Box(0, 0, imageWidth, imageHeight));
        for (Slot s : menu.slots) {
            Golden.slot(g, s.x - 1, s.y - 1, 18);
        }
        int used = 0;
        int box = menu.getRowCount() * 9;
        for (int i = 0; i < box; i++) {
            if (menu.slots.get(i).hasItem()) {
                used++;
            }
        }
        String count = used + " / " + box;
        g.drawString(font, count, imageWidth - 8 - font.width(count), titleLabelY, Golden.DIM, false);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, Golden.GOLD, false);
        g.drawString(font, Component.translatable("gui.skyabyss.gear.bag"), inventoryLabelX, inventoryLabelY, Golden.PALE, false);
    }

    @Override
    public int[] textBounds() {
        return new int[] {leftPos, topPos, imageWidth, imageHeight};
    }
}
