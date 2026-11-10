package jp.revati.skyabyss.client.inventory;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.item.accessory.Accessories;
import jp.revati.skyabyss.item.accessory.AccessoryData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

// 持ち物の画面 (docs/design/56 の 4 章)。バニラの持ち物の画面の代わりに開く (E キー・つまみ)。入れ物はバニラと同じ (InventoryMenu) で、
// 枠の置き場だけを大きい板の上に決め直す。クラフトの枠とできる物の枠は画面の外へ出す (作る仕組みはない。レシピ本の釦も無い)。
// クリエイティブはバニラの画面のまま
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public class SkyInventoryScreen extends AbstractContainerScreen<InventoryMenu> implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int OFF = -10000;
    private final Player player;
    private int[][] saved;

    public SkyInventoryScreen(Player player) {
        super(player.inventoryMenu, player.getInventory(), Component.translatable("skyabyss_gear.screen.tab_inventory"));
        this.player = player;
        imageWidth = GearPanel.W;
        imageHeight = GearPanel.H;
    }

    @SubscribeEvent
    static void onOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getNewScreen() instanceof InventoryScreen && mc.player != null && mc.gameMode != null && !mc.gameMode.hasInfiniteItems()) {
            event.setNewScreen(new SkyInventoryScreen(mc.player));
        }
    }

    @Override
    protected void init() {
        super.init();
        place();
        GearPanel.addTabs(this::addRenderableWidget, () -> leftPos, () -> topPos, 0);
        addRenderableWidget(new ProfileButton());
    }

    // 枠の置き場: 0 できる物・1〜4 クラフト (外へ)、5〜8 防具、9〜35 袋、36〜44 手もと、45 逆手
    private void place() {
        if (saved == null) {
            saved = new int[menu.slots.size()][];
            for (int i = 0; i < saved.length; i++) {
                saved[i] = new int[] {menu.slots.get(i).x, menu.slots.get(i).y};
            }
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (i <= 4) {
                s.x = OFF;
                s.y = OFF;
            } else if (i <= 8) {
                s.x = GearPanel.ARMOR_X;
                s.y = GearPanel.armorY(i - 5);
            } else if (i <= 35) {
                s.x = GearPanel.bagX((i - 9) % 9);
                s.y = GearPanel.bagY((i - 9) / 9);
            } else if (i <= 44) {
                s.x = GearPanel.bagX(i - 36);
                s.y = GearPanel.HOT_Y;
            } else {
                s.x = GearPanel.ARMOR_X;
                s.y = GearPanel.armorY(4);
            }
        }
    }

    // バニラの画面 (クリエイティブに替えたときなど) のために、置き場を戻す
    @Override
    public void removed() {
        if (saved != null) {
            for (int i = 0; i < saved.length && i < menu.slots.size(); i++) {
                menu.slots.get(i).x = saved[i][0];
                menu.slots.get(i).y = saved[i][1];
            }
            saved = null;
        }
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // 装飾は見るだけ (替えるのは「装飾」のつまみ)
        for (int i = 0; i < AccessoryData.SIZE; i++) {
            GearPanel.shown(g, font, leftPos, topPos, GearPanel.ACC_X, GearPanel.armorY(i), Accessories.stack(player, i), mouseX, mouseY);
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        GearPanel.draw(g, font, leftPos, topPos, mouseX, mouseY, player, title);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public int[] textBounds() {
        return new int[] {leftPos, topPos, imageWidth, imageHeight};
    }

    // プロフィールを開く釦 (右の欄の右下)
    private final class ProfileButton extends AbstractButton {
        ProfileButton() {
            super(0, 0, Golden.S55 + Golden.S13, Golden.BTN, Component.translatable("gui.skyabyss.profile.title"));
        }

        private void place() {
            setX(leftPos + GearPanel.RIGHT.right() - 8 - getWidth());
            setY(topPos + GearPanel.RIGHT.bottom() - 8 - Golden.BTN);
        }

        @Override
        public void onPress() {
            jp.revati.skyabyss.endgame.client.ProfileScreen.open();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            place();
            Golden.button(g, font, new Golden.Box(getX(), getY(), getWidth(), getHeight()), getMessage(), false, isHoveredOrFocused(), true);
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
}
