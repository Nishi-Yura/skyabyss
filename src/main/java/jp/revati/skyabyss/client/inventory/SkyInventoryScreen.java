package jp.revati.skyabyss.client.inventory;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.item.accessory.AccessorySlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

// 持ち物の画面 (docs/design/56 の 4 章。2026-11-11 の案 A)。バニラの持ち物の画面の代わりに開く (E キー・つまみ)。入れ物はバニラと同じ (InventoryMenu)。
//   真ん中: バニラと同じ形・同じ枠の位置 (防具 4・人形・逆手・袋 27・手もと 9)。作業台の場所に装飾 3 枠 (護符・装飾 2。mixin/InventoryMenuAccessoryMixin)
//   左の板: 名前・Lv とジョブ、体力・マナ・経験値、攻撃と守り。右の板: スキル 4 (見るだけ) とプロフィールの釦
// クラフトの枠とできる物の枠は画面の外へ出す (作る仕組みはない。レシピ本の釦も無い)。クリエイティブはバニラの画面のまま
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public class SkyInventoryScreen extends AbstractContainerScreen<InventoryMenu> implements jp.revati.skyabyss.client.ui.TextBounded {
    static final ResourceLocation ACC_TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/accessory/accessory.png");
    static final int CORE_H = 166;
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
        ProfileButton profile = new ProfileButton();
        profile.place();
        addRenderableWidget(profile);
    }

    // 枠の置き場 (バニラの持ち物の画面と同じ。真ん中の板の左上から): 0 できる物・1〜4 クラフト (外へ)、5〜8 防具、9〜35 袋、36〜44 手もと、
    // 45 逆手、46〜48 装飾 (作業台の場所)
    private void place() {
        if (saved == null) {
            saved = new int[menu.slots.size()][];
            for (int i = 0; i < saved.length; i++) {
                saved[i] = new int[] {menu.slots.get(i).x, menu.slots.get(i).y};
            }
        }
        int cx = GearPanel.CORE_X;
        int cy = GearPanel.CORE_Y;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (i <= 4) {
                s.x = OFF;
                s.y = OFF;
            } else if (i <= 8) {
                s.x = cx + 8;
                s.y = cy + 8 + (i - 5) * 18;
            } else if (i <= 35) {
                s.x = cx + 8 + (i - 9) % 9 * 18;
                s.y = cy + 84 + (i - 9) / 9 * 18;
            } else if (i <= 44) {
                s.x = cx + 8 + (i - 36) * 18;
                s.y = cy + 142;
            } else if (i == 45) {
                s.x = cx + 77;
                s.y = cy + 62;
            } else if (AccessorySlots.isAccessory(i)) {
                int a = i - AccessorySlots.FIRST;
                s.x = cx + AccessorySlots.X[a];
                s.y = cy + AccessorySlots.Y;
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
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(leftPos, topPos, 0);
        GearPanel.panels(g, CORE_H);
        GearPanel.drawSelf(g, font, player, CORE_H);
        GearPanel.drawSkills(g, font, player, CORE_H);
        for (Slot s : menu.slots) {
            if (s.x > OFF && s.isActive()) {
                Golden.slot(g, s.x - 1, s.y - 1, 18);
            }
        }
        int cx = GearPanel.CORE_X;
        int cy = GearPanel.CORE_Y;
        // 選んでいる手もとの枠
        int sel = cx + 8 + player.getInventory().selected * 18;
        Golden.outline(g, sel - 2, cy + 141, sel + 18, cy + 161, 0xFF000000 | Golden.GOLD);
        // 装飾の 3 枠: 金の縁。空の枠には印 (護符 = 首飾り、装飾 = 輪)
        g.drawString(font, Component.translatable("skyabyss_gear.screen.title"), cx + AccessorySlots.X[0], cy + 11, Golden.PALE, false);
        for (int a = 0; a < AccessorySlots.SIZE; a++) {
            int x = cx + AccessorySlots.X[a];
            int y = cy + AccessorySlots.Y;
            Golden.outline(g, x - 1, y - 1, x + 17, y + 17, Golden.LINE);
            if (menu.getSlot(AccessorySlots.FIRST + a).getItem().isEmpty()) {
                g.blit(ACC_TEXTURE, x, y, 16, 16, a == 0 ? 0 : 40, 380, 32, 32, 512, 512);
            }
        }
        Component key = Component.translatable("skyabyss_gear.screen.charm_key", jp.revati.skyabyss.client.accessory.AccessoryClient.CHARM_KEY.getTranslatedKeyMessage());
        g.drawString(font, key, cx + AccessorySlots.X[0], cy + AccessorySlots.Y + 21, Golden.DIM, false);
        // 袋の数 (人形の右下、袋の上)
        int used = 0;
        for (int i = 9; i < 36; i++) {
            if (!player.getInventory().items.get(i).isEmpty()) {
                used++;
            }
        }
        String count = used + " / 27";
        g.drawString(font, count, cx + 168 - font.width(count), cy + 72, Golden.DIM, false);
        g.pose().popPose();
        GearPanel.doll(g, leftPos, topPos, mouseX, mouseY, player);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public int[] textBounds() {
        return new int[] {leftPos, topPos, imageWidth, GearPanel.CORE_Y + CORE_H};
    }

    // プロフィールを開く釦 (右の板の下)
    private final class ProfileButton extends AbstractButton {
        ProfileButton() {
            super(0, 0, GearPanel.WING - 2 * GearPanel.PAD, Golden.BTN, Component.translatable("gui.skyabyss.profile.title"));
        }

        private void place() {
            Golden.Box r = GearPanel.right(CORE_H);
            setX(leftPos + r.x() + GearPanel.PAD);
            setY(topPos + r.bottom() - GearPanel.PAD - Golden.BTN);
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
