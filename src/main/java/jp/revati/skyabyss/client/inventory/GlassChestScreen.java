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

// 箱の画面 (預かり所と、世界の箱。docs/design/56 の 4 章)。バニラの箱の画面の代わりに開く。入れ物はバニラと同じ (ChestMenu) で、
// 枠の置き場だけを大きい板の上に決め直す: 左の欄に箱の枠 (9 列 × 行)、右の欄に持ち物 27 と手もと 9。
// 預かり所のページの札 (client/facility/StashTabs) は板の右の外に並ぶ
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public class GlassChestScreen extends AbstractContainerScreen<ChestMenu> implements jp.revati.skyabyss.client.ui.TextBounded {
    static final Golden.Layout L = Golden.Layout.large(0);
    static final int COL_W = (L.body().w() - Golden.GAP + 1) / 2;
    static final Golden.Box LEFT = L.body().left(COL_W);
    static final Golden.Box RIGHT = L.body().right(L.body().w() - COL_W - Golden.GAP + 1);
    static final int PITCH = 18;
    static final int GRID_Y = L.body().y() + 18;

    public GlassChestScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = Golden.L_W;
        imageHeight = Golden.L_H;
    }

    @SubscribeEvent
    static void onOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getNewScreen() instanceof ContainerScreen cs && mc.player != null && cs.getMenu().getRowCount() <= 6) {
            event.setNewScreen(new GlassChestScreen(cs.getMenu(), mc.player.getInventory(), cs.getTitle()));
        }
    }

    private static int gridX(Golden.Box column, int col) {
        return column.x() + (column.w() - 9 * PITCH) / 2 + 1 + col * PITCH;
    }

    private int hotbarY() {
        return GRID_Y + 3 * PITCH + 8 + Golden.ROW;
    }

    @Override
    protected void init() {
        super.init();
        // 枠の置き場: 箱 (行 × 9)、持ち物 27、手もと 9
        int box = menu.getRowCount() * 9;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (i < box) {
                s.x = gridX(LEFT, i % 9);
                s.y = GRID_Y + (i / 9) * PITCH;
            } else if (i < box + 27) {
                s.x = gridX(RIGHT, (i - box) % 9);
                s.y = GRID_Y + ((i - box) / 9) * PITCH;
            } else {
                s.x = gridX(RIGHT, i - box - 27);
                s.y = hotbarY();
            }
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
        Golden.frame(g, font, L, title);
        Golden.recess(g, LEFT);
        Golden.recess(g, RIGHT);
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
        g.drawString(font, Component.translatable("gui.skyabyss.chest.inside"), LEFT.x() + 6, LEFT.y() + 6, Golden.PALE, false);
        String count = used + " / " + box;
        g.drawString(font, count, LEFT.right() - 6 - font.width(count), LEFT.y() + 6, Golden.DIM, false);
        g.drawString(font, Component.translatable("gui.skyabyss.gear.bag"), RIGHT.x() + 6, RIGHT.y() + 6, Golden.PALE, false);
        g.drawString(font, Component.translatable("gui.skyabyss.gear.hand"), RIGHT.x() + 6, hotbarY() - Golden.ROW + 2, Golden.PALE, false);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public int[] textBounds() {
        return new int[] {leftPos, topPos, imageWidth, imageHeight};
    }
}
