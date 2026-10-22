package jp.revati.skyabyss.client.accessory;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearLevel;
import jp.revati.skyabyss.item.accessory.AccessoryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

// 装飾の画面。真ん中に人形、左に護符の枠 (金の輪)、右に装飾の枠 2 つ、下に持ち物。
// 画面の上に「持ち物」「装飾」「旅嚢」のつまみ (持ち物の画面にも同じ位置に出す)。
// 背景は 2 倍密度のテクスチャ (textures/gui/accessory/accessory.png、tools/gen/items/gen_accessory_assets.py の案 B)
public class AccessoryScreen extends AbstractContainerScreen<AccessoryMenu> implements jp.revati.skyabyss.client.ui.TextBounded {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/accessory/accessory.png");
    static final int TEX = 512;
    static final int W = 176;
    static final int H = 166;
    static final int TAB_W = 48;
    public static final int TAB_H = 16;
    static final int TABS = 3;
    private static final int DOLL_X0 = 58;
    private static final int DOLL_Y0 = 14;
    private static final int DOLL_X1 = 118;
    private static final int DOLL_Y1 = 78;
    private static final int TEXT = 0xFFD8DCE6;
    private static final int DIM = 0xFF9AA0B0;
    private static final int GOLD = 0xFFF2C14E;

    public AccessoryScreen(AccessoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        addTabs(this::addRenderableWidget, this, 1);
    }

    // 持ち物の画面・装飾の画面・旅嚢の画面の上に、三つのつまみ (0 持ち物・1 装飾・2 旅嚢) を足す。selected = いまの画面のつまみ。
    // 位置は画面の左端に合わせて毎回決める (レシピ本で左右に動くため)。画面の幅は三つとも 176
    // 見出しを切り替えると、画面の開き直しでマウスが画面の中央へ戻される (バニラの releaseMouse)。押した時の位置を覚え、次の画面の
    // 見出しが並ぶとき (開き直しの後) に同じ位置へ戻す
    private static double[] keptCursor;
    private static long keptAt;

    static void keepCursor() {
        Minecraft mc = Minecraft.getInstance();
        keptCursor = new double[] {mc.mouseHandler.xpos(), mc.mouseHandler.ypos()};
        keptAt = net.minecraft.Util.getMillis();
    }

    private static void restoreCursor() {
        double[] c = keptCursor;
        keptCursor = null;
        if (c != null && net.minecraft.Util.getMillis() - keptAt < 4000) {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.mouseHandler.isMouseGrabbed()) {
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), c[0], c[1]);
            }
        }
    }

    public static void addTabs(java.util.function.Consumer<AbstractButton> add, AbstractContainerScreen<?> screen, int selected) {
        restoreCursor();
        for (int i = 0; i < TABS; i++) {
            add.accept(new Tab(screen, i, i == selected));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, W, H, 0, 0, W * 2, H * 2, TEX, TEX);
        // 空の枠に印 (護符 = 首飾り、装飾 = 輪)
        for (int i = 0; i < AccessoryMenu.SLOT_POS.length; i++) {
            if (menu.getSlot(i).getItem().isEmpty()) {
                int u = i == 0 ? 0 : 40;
                g.blit(TEXTURE, x + AccessoryMenu.SLOT_POS[i][0], y + AccessoryMenu.SLOT_POS[i][1], 16, 16, u, 380, 32, 32, TEX, TEX);
            }
        }
        if (minecraft != null && minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + DOLL_X0 + 1, y + DOLL_Y0 + 1, x + DOLL_X1 - 1, y + DOLL_Y1 - 4, 28, 0.0625f,
                    mouseX, mouseY, minecraft.player);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TEXT, false);
        // バニラの「インベントリ」は人形の窓に掛かるので、短い「持ち物」(つまみと同じ名前) にする
        g.drawString(font, Component.translatable("skyabyss_gear.screen.tab_inventory"), 8, inventoryLabelY, TEXT, false);
        // 護符の枠の上に「護符」、下に使うキー。装飾の枠の上に「装飾」、下にいまのレベル
        Component charm = Component.translatable("skyabyss_gear.screen.charm");
        g.drawString(font, charm, 38 - font.width(charm) / 2, 17, GOLD, false);
        Component key = Component.translatable("skyabyss_gear.screen.charm_key", AccessoryClient.CHARM_KEY.getTranslatedKeyMessage());
        g.drawString(font, key, 38 - font.width(key) / 2, 62, DIM, false);
        Component trinket = Component.translatable("skyabyss_gear.screen.trinket");
        g.drawString(font, trinket, 138 - font.width(trinket) / 2, 15, TEXT, false);
        if (minecraft != null && minecraft.player != null) {
            Component lv = Component.translatable("skyabyss_gear.screen.level", GearLevel.level(minecraft.player));
            g.drawString(font, lv, 138 - font.width(lv) / 2, 72, DIM, false);
        }
    }

    // つまみ: 0 = 持ち物、1 = 装飾。選ばれたつまみは押せず、画面の縁とつながる
    static final class Tab extends AbstractButton {
        private final AbstractContainerScreen<?> screen;
        private final int which;
        private final boolean selected;

        Tab(AbstractContainerScreen<?> screen, int which, boolean selected) {
            super(0, 0, TAB_W, TAB_H, Component.translatable(which == 0 ? "skyabyss_gear.screen.tab_inventory"
                    : which == 1 ? "skyabyss_gear.screen.tab_accessories" : "skyabyss_pouch.tab"));
            this.screen = screen;
            this.which = which;
            this.selected = selected;
        }

        private void place() {
            setX(screen.getGuiLeft() + W - (TABS - which) * (TAB_W + 2) - 2);
            setY(screen.getGuiTop() - TAB_H + 2);
        }

        @Override
        public void onPress() {
            if (selected) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            keepCursor();
            if (which == 1) {
                PacketDistributor.sendToServer(new jp.revati.skyabyss.item.accessory.AccessorySetup.OpenPayload());
            } else if (which == 2) {
                PacketDistributor.sendToServer(new jp.revati.skyabyss.pouch.PouchNet.Open());
            } else if (mc.player != null) {
                mc.player.closeContainer();
                mc.setScreen(new InventoryScreen(mc.player));
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            place();
            int state = selected ? 2 : isHoveredOrFocused() ? 1 : 0;
            g.pose().pushPose();
            g.pose().translate(0, 0, selected ? 50 : 0);
            g.blit(TEXTURE, getX(), getY(), TAB_W, TAB_H, state * 100, 340, TAB_W * 2, TAB_H * 2, TEX, TEX);
            var font = Minecraft.getInstance().font;
            int color = selected ? 0xF2C14E : isHoveredOrFocused() ? 0xE6E2D6 : 0xA9AEBC;
            g.drawString(font, getMessage(), getX() + (TAB_W - font.width(getMessage())) / 2, getY() + 5, color, false);
            g.pose().popPose();
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            place();
            return super.isMouseOver(mouseX, mouseY);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    @Override
    public int[] textBounds() {
        return new int[] {leftPos, topPos - TAB_H, W, H + TAB_H};
    }
}
