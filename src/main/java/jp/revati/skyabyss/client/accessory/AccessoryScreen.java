package jp.revati.skyabyss.client.accessory;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.inventory.GearPanel;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.item.accessory.AccessoryData;
import jp.revati.skyabyss.item.accessory.AccessoryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

// 装飾の画面。持ち物の画面と同じ大きい板 (client/inventory/GearPanel) で、人形の右の 3 枠 (上が護符、下の二つが装飾) が動かせる。
// 防具と逆手は見るだけ。つまみ (持ち物・装飾・旅袋・封の箱) は見出しの帯の左。
// 旅袋と封の箱の画面は前の寸法 (幅 176) のままで、そちらのつまみは画面の上に載る (下の Tab)
public class AccessoryScreen extends AbstractContainerScreen<AccessoryMenu> implements jp.revati.skyabyss.client.ui.TextBounded {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/accessory/accessory.png");
    static final int TEX = 512;
    static final int W = 176;
    static final int TAB_W = 41;
    public static final int TAB_H = 16;
    static final int TABS = 4;
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    public AccessoryScreen(AccessoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = GearPanel.W;
        imageHeight = GearPanel.H;
    }

    @Override
    protected void init() {
        super.init();
        // 枠の置き場: 0〜2 装飾 (0 が護符)、3〜29 袋、30〜38 手もと
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (i < AccessoryData.SIZE) {
                s.x = GearPanel.ACC_X;
                s.y = GearPanel.armorY(i);
            } else if (i < AccessoryData.SIZE + 27) {
                int n = i - AccessoryData.SIZE;
                s.x = GearPanel.bagX(n % 9);
                s.y = GearPanel.bagY(n / 9);
            } else {
                s.x = GearPanel.bagX(i - AccessoryData.SIZE - 27);
                s.y = GearPanel.HOT_Y;
            }
        }
        GearPanel.addTabs(this::addRenderableWidget, () -> leftPos, () -> topPos, 1);
    }

    // 旅袋・封の箱の画面 (幅 176) の上に載るつまみ。selected = いまの画面のつまみ。位置は画面の左端に合わせて毎回決める
    public static void addTabs(java.util.function.Consumer<AbstractButton> add, AbstractContainerScreen<?> screen, int selected) {
        addTabs(add, screen::getGuiLeft, screen::getGuiTop, selected);
    }

    // 入れ物でない画面 (封の箱の欄 client/box/BoxPouchScreen) からも。left / top = 画面の左上
    public static void addTabs(java.util.function.Consumer<AbstractButton> add, java.util.function.IntSupplier left, java.util.function.IntSupplier top, int selected) {
        restoreCursor();
        for (int i = 0; i < TABS; i++) {
            add.accept(new Tab(left, top, i, i == selected));
        }
    }

    // つまみの名前: 0 = 持ち物、1 = 装飾、2 = 旅袋、3 = 封の箱 (docs/design/52)
    public static Component tabName(int which) {
        return Component.translatable(which == 0 ? "skyabyss_gear.screen.tab_inventory"
                : which == 1 ? "skyabyss_gear.screen.tab_accessories" : which == 2 ? "skyabyss_pouch.tab" : "skyabyss_box.tab");
    }

    // つまみを押した: その画面へ。持ち物はバニラの画面を開くと client/inventory/SkyInventoryScreen に替わる
    public static void go(int which) {
        Minecraft mc = Minecraft.getInstance();
        keepCursor();
        if (which == 1) {
            PacketDistributor.sendToServer(new jp.revati.skyabyss.item.accessory.AccessorySetup.OpenPayload());
        } else if (which == 2) {
            PacketDistributor.sendToServer(new jp.revati.skyabyss.pouch.PouchNet.Open());
        } else if (which == 3 && mc.player != null) {
            mc.player.closeContainer();
            mc.setScreen(new jp.revati.skyabyss.client.box.BoxPouchScreen());
        } else if (mc.player != null) {
            mc.player.closeContainer();
            mc.setScreen(new InventoryScreen(mc.player));
        }
    }

    // 見出しを切り替えると、画面の開き直しでマウスが画面の中央へ戻される (バニラの releaseMouse)。押した時の位置を覚え、次の画面の
    // 見出しが並ぶとき (開き直しの後) に同じ位置へ戻す
    private static double[] keptCursor;
    private static long keptAt;

    static void keepCursor() {
        Minecraft mc = Minecraft.getInstance();
        keptCursor = new double[] {mc.mouseHandler.xpos(), mc.mouseHandler.ypos()};
        keptAt = net.minecraft.Util.getMillis();
    }

    public static void restoreCursor() {
        double[] c = keptCursor;
        keptCursor = null;
        if (c != null && net.minecraft.Util.getMillis() - keptAt < 4000) {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.mouseHandler.isMouseGrabbed()) {
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), c[0], c[1]);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // 防具と逆手は見るだけ (替えるのは「持ち物」のつまみ)
        if (minecraft != null && minecraft.player != null) {
            for (int i = 0; i < WORN.length; i++) {
                GearPanel.shown(g, font, leftPos, topPos, GearPanel.ARMOR_X, GearPanel.armorY(i), minecraft.player.getItemBySlot(WORN[i]), mouseX, mouseY);
            }
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        GearPanel.draw(g, font, leftPos, topPos, mouseX, mouseY, minecraft.player, title);
        // 動かせる枠に金の縁。空の枠には印 (護符 = 首飾り、装飾 = 輪)
        for (int i = 0; i < AccessoryData.SIZE; i++) {
            int x = leftPos + GearPanel.ACC_X;
            int y = topPos + GearPanel.armorY(i);
            Golden.outline(g, x - 1, y - 1, x + 17, y + 17, Golden.LINE);
            if (menu.getSlot(i).getItem().isEmpty()) {
                g.blit(TEXTURE, x, y, 16, 16, i == 0 ? 0 : 40, 380, 32, 32, TEX, TEX);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 護符の枠 (いちばん上) の左に、使うキー
        Component key = Component.translatable("skyabyss_gear.screen.charm_key", AccessoryClient.CHARM_KEY.getTranslatedKeyMessage());
        g.drawString(font, key, GearPanel.RIGHT.right() - 8 - font.width(key), GearPanel.RIGHT.bottom() - 8 - 10, Golden.DIM, false);
    }

    // 旅袋・封の箱の画面の上に載るつまみ。選ばれたつまみは押せない
    static final class Tab extends AbstractButton {
        private final java.util.function.IntSupplier left;
        private final java.util.function.IntSupplier top;
        private final int which;
        private final boolean selected;

        Tab(java.util.function.IntSupplier left, java.util.function.IntSupplier top, int which, boolean selected) {
            super(0, 0, TAB_W, TAB_H, tabName(which));
            this.left = left;
            this.top = top;
            this.which = which;
            this.selected = selected;
        }

        private void place() {
            setX(left.getAsInt() + W - (TABS - which) * (TAB_W + 2) - 2);
            setY(top.getAsInt() - TAB_H + 2);
        }

        @Override
        public void onPress() {
            if (!selected) {
                go(which);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            place();
            g.pose().pushPose();
            g.pose().translate(0, 0, selected ? 50 : 0);
            Golden.hudFrame(g, getX(), getY(), getX() + TAB_W, getY() + TAB_H);
            Golden.tab(g, Minecraft.getInstance().font, new Golden.Box(getX() + 2, getY() + 2, TAB_W - 4, TAB_H - 4), getMessage(), selected, isHoveredOrFocused());
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
        return new int[] {leftPos, topPos, imageWidth, imageHeight};
    }
}
