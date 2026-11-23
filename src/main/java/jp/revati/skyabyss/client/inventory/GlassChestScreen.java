package jp.revati.skyabyss.client.inventory;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.facility.StashTabs;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.ShopStage;
import jp.revati.skyabyss.client.ui.UiFit;
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
import jp.revati.skyabyss.client.ui.Paper;

// 箱の画面 (預かり所と、世界の箱。docs/design/56 の 4 章)。バニラの箱の画面の代わりに開く。入れ物はバニラと同じ (ChestMenu)。
// 組み方は店の類の「帳面と台」(client/ui/ShopStage。docs/design/56 の 2.1。作業台の 3 案からユーザーが A を選んだ): 舞台 408x228 を GUI の等倍で真ん中に置き
// (枠の当たりは GUI の座標のまま)、左の生成りの帳面に箱の題・入っている数・箱の枠 (9 列 × 行)、預かり所の頁は帳面の上の辺のつまみ (client/facility/StashTabs)。
// 右の黒い紙に「袋」の題と、袋 27・手もと 9 の枠。枠の場所は画面の側で動かす (Slot.x/y。accesstransformer)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public class GlassChestScreen extends AbstractContainerScreen<ChestMenu> implements jp.revati.skyabyss.client.ui.TextBounded {
    // 箱の枠 (帳面の中) と袋の枠 (右の黒い紙)
    private static final int BOX_X = 40;
    private static final int BOX_Y = 42;
    private static final int BAG_X = 244;
    private static final int BAG_Y = 34;
    private final UiFit fit = new UiFit();

    public GlassChestScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = Paper.STAGE_W;
        imageHeight = Paper.STAGE_H;
    }

    @SubscribeEvent
    static void onOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getNewScreen() instanceof ContainerScreen cs && mc.player != null && cs.getMenu().getRowCount() <= 6) {
            event.setNewScreen(new GlassChestScreen(cs.getMenu(), mc.player.getInventory(), cs.getTitle()));
        }
    }

    @Override
    protected void init() {
        super.init();
        // 舞台を GUI の等倍で真ん中に。枠を舞台の場所へ動かす (画面の側だけ。サーバーは枠の場所を使わない)
        fit.fit(width, height, imageWidth, imageHeight, 1f, 1f);
        int[] r = fit.screenRect(0, 0, imageWidth, imageHeight);
        leftPos = r[0];
        topPos = r[1];
        int box = menu.getRowCount() * 9;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (i < box) {
                s.x = BOX_X + 1 + i % 9 * 18;
                s.y = BOX_Y + 1 + i / 9 * 18;
            } else if (i < box + 27) {
                s.x = BAG_X + 1 + (i - box) % 9 * 18;
                s.y = BAG_Y + 1 + (i - box) / 9 * 18;
            } else {
                s.x = BAG_X + 1 + (i - box - 27) * 18;
                s.y = BAG_Y + 1 + 58;
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 薄く暗くしてから舞台 (AbstractContainerScreen は renderBackground の中で renderBg を呼ぶ)
        Paper.dim(g, width, height);
        renderBg(g, partialTick, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        fit.fit(width, height, imageWidth, imageHeight, 1f, 1f);
        fit.begin(g);
        // 窓が舞台よりずっと大きい (GUI 2 など) ときは、窓の端まで敷かずに舞台の大きさの一枚の紙にする (箱の画面は大きくできないため)
        jp.revati.skyabyss.client.ui.UiFit back = fit;
        int bw = width;
        int bh = height;
        if (width > imageWidth * 3 / 2 || height > imageHeight * 3 / 2) {
            bw = imageWidth + 24;
            bh = imageHeight + 24;
            back = new jp.revati.skyabyss.client.ui.UiFit();
            back.fit(bw, bh, imageWidth, imageHeight, 1f, 1f);
        }
        ShopStage.backdrop(g, back, bw, bh);
        int box = menu.getRowCount() * 9;
        // 預かり所の頁 (帳面の上の辺のつまみ)
        int pages = StashTabs.pages(this);
        for (int i = 0; i < pages; i++) {
            ShopStage.tab(g, font, i, Component.literal(String.valueOf(i + 1)), i + 1 == StashTabs.page(), mouseX - leftPos, mouseY - topPos);
        }
        // 箱の題と入っている数 (帳面の上。墨)
        Paper.pin(g, BOX_X - 18, 32, 3, true);
        g.drawString(font, title, BOX_X - 10, 28, Paper.INK_HEAD, false);
        int used = 0;
        for (int i = 0; i < box; i++) {
            if (menu.slots.get(i).hasItem()) {
                used++;
            }
        }
        String count = used + " / " + box;
        g.drawString(font, count, BOX_X + 162 - font.width(count), 28, Paper.INK_SOFT, false);
        // 袋 (右の黒い紙の題)
        ShopStage.title(g, font, Component.translatable("gui.skyabyss.gear.bag"));
        fit.end(g);
        g.pose().pushPose();
        g.pose().translate(leftPos, topPos, 0);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (i < box) {
                // 帳面の上の枠 (少し濃い生成りの窪み)
                g.fill(s.x - 1, s.y - 1, s.x + 17, s.y + 17, Paper.CREAM_LO);
                g.fill(s.x, s.y, s.x + 17, s.y + 17, 0xFFDCD2C2);
            } else {
                Golden.slot(g, s.x - 1, s.y - 1, 18);
            }
        }
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int pages = StashTabs.pages(this);
        for (int i = 0; i < pages; i++) {
            if (button == 0 && ShopStage.in(mouseX - leftPos, mouseY - topPos, ShopStage.tabX(i), ShopStage.TAB_Y, ShopStage.TAB_W, 13)) {
                StashTabs.go(this, i + 1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public int[] textBounds() {
        return new int[] {leftPos, topPos, imageWidth, imageHeight};
    }
}
