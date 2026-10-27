package jp.revati.skyabyss.client.pouch;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.accessory.AccessoryScreen;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.pouch.PouchData;
import jp.revati.skyabyss.pouch.PouchEntries;
import jp.revati.skyabyss.pouch.PouchLayout;
import jp.revati.skyabyss.pouch.PouchMenu;
import jp.revati.skyabyss.pouch.PouchUsable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// 旅嚢の画面 (docs/design/22 の 9 章 A つまみ)。持ち物 / 装飾 / 旅嚢 のつまみの三つ目。
//   枠: 品ごとに決まった枠 (区分の名札つき)。まだ手に入れていない品は「？」、いちど手に入れて今は 0 の品は暗い影。
//       枠の置く・取り出す・シフトクリックは PouchMenu が決める (取り出せない決まりのときは何も起きない)
public class PouchScreen extends AbstractContainerScreen<PouchMenu> implements jp.revati.skyabyss.client.ui.TextBounded {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/pouch/pouch.png");
    static final int TEX = 512;
    private static final int TEXT = 0xFFD8DCE6;
    private static final int DIM = 0xFF9AA0B0;
    private static final int GOLD = 0xFFF2C14E;
    private static final int OK = 0xFF7FD6D0;
    // 心臓の遺物のボタン (受け取れる遺物があるときだけ。右上)
    private static final int BTN_W = 44;
    private static final int BTN_H = 12;
    private static final int RELIC_X = PouchLayout.W - 8 - BTN_W;
    private static final int RELIC_Y = 2;

    public PouchScreen(PouchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PouchLayout.W;
        imageHeight = PouchLayout.H;
    }

    @Override
    protected void init() {
        super.init();
        AccessoryScreen.addTabs(this::addRenderableWidget, this, 2);
    }

    // ---------------------------------------------------------------- 描く
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderNewBadges(g);
        PouchEntries.Entry hovered = hoveredEntry();
        if (hovered != null) {
            // 重ねて見た品の "NEW" は外す
            PouchNew.viewed(hovered.id());
        }
        renderTooltip(g, mouseX, mouseY);
        renderExtraTooltips(g, mouseX, mouseY);
    }

    // 新しく入ってまだ見ていない品の枠に "NEW" の札 (枠の絵の上に重ねる)
    private void renderNewBadges(GuiGraphics g) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        PouchData d = Pouch.data(minecraft.player);
        for (int i = 0; i < menu.pouchSlotCount(); i++) {
            PouchLayout.Spot spot = menu.layout().spots().get(i);
            if (d.isFresh(spot.entry().id()) && d.count(spot.entry().id()) > 0) {
                PouchNew.badge(g, font, leftPos + spot.x(), topPos + spot.y());
            }
        }
    }

    private boolean seen(PouchEntries.Entry e) {
        PouchData d = Pouch.data(minecraft.player);
        return d.hasSeen(e.id()) || d.count(e.id()) > 0;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.blit(TEXTURE, x, y, PouchLayout.W, PouchLayout.H, 0, 0, PouchLayout.W * 2, PouchLayout.H * 2, TEX, TEX);
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        renderSpareBg(g, x, y);
        for (int i = 0; i < menu.pouchSlotCount(); i++) {
            PouchLayout.Spot spot = menu.layout().spots().get(i);
            PouchEntries.Entry e = spot.entry();
            int n = Pouch.data(minecraft.player).count(e.id());
            boolean usable = n > 0 && e.item() instanceof PouchUsable;
            // 枠: 持っている品は青緑の縁、使える道具は金の縁、ないものは空
            float u = usable ? 440f : n > 0 ? 400f : 360f;
            g.blit(TEXTURE, x + spot.x() - 1, y + spot.y() - 1, 18, 18, u, 0f, 36, 36, TEX, TEX);
            if (usable && QuickCard.isPinned(e.id())) {
                // 札に置いた印 (角の金の点)
                g.fill(x + spot.x() - 1, y + spot.y() - 1, x + spot.x() + 2, y + spot.y(), 0xFFF2C14E);
                g.fill(x + spot.x() - 1, y + spot.y() - 1, x + spot.x(), y + spot.y() + 2, 0xFFF2C14E);
            }
            if (n <= 0) {
                if (seen(e)) {
                    // いちど手に入れた品: 暗い影 (今は手もとにない)
                    g.renderItem(new ItemStack(e.item()), x + spot.x(), y + spot.y());
                    g.fill(x + spot.x(), y + spot.y(), x + spot.x() + 16, y + spot.y() + 16, 0xC8141820);
                } else {
                    g.blit(TEXTURE, x + spot.x(), y + spot.y(), 16, 16, 360f, 50f, 32, 32, TEX, TEX);
                }
            }
        }
    }

    // 転生の余白の枠 (docs/design/31 の B13): 名札と枠。中身は標準の枠の描きが重ねる
    private void renderSpareBg(GuiGraphics g, int x, int y) {
        for (int i = 0; i < menu.spareCount(); i++) {
            boolean has = !menu.getSlot(menu.pouchSlotCount() + i).getItem().isEmpty();
            int[] at = menu.layout().spare().get(i);
            g.blit(TEXTURE, x + at[0] - 1, y + at[1] - 1, 18, 18, has ? 400f : 360f, 0f, 36, 36, TEX, TEX);
        }
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ボタンの絵 (pouch.png の 44x16 を、指定の大きさへ)。state 0 押せる / 1 重ねた / 2 押せない
    private void drawButton(GuiGraphics g, int x, int y, int w, int h, int state, Component label) {
        g.blit(TEXTURE, x, y, w, h, state * 100f, 420f, 88, 32, TEX, TEX);
        int color = state == 2 ? 0xFF6A7080 : state == 1 ? 0xFFE6E2D6 : 0xFFA9AEBC;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        g.drawString(font, title, 8, 5, GOLD, false);
        if (jp.revati.skyabyss.client.reward.HeartRewardClient.pending()) {
            // 心臓の遺物: 金の縁のボタン (ゆっくり点る)
            boolean relicHover = hit(mx, my, RELIC_X, RELIC_Y, BTN_W, BTN_H);
            drawButton(g, RELIC_X, RELIC_Y, BTN_W, BTN_H, relicHover ? 1 : 0, Component.translatable("skyabyss_reward.pouch.button"));
            int glow = 0x40 + (int) (0x50 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0)));
            g.renderOutline(RELIC_X - 1, RELIC_Y - 1, BTN_W + 2, BTN_H + 2, (glow << 24) | 0xF2C14E);
        }
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        for (PouchLayout.Label label : menu.layout().labels()) {
            g.drawString(font, Component.translatable("skyabyss_pouch.category." + label.category().id), label.x(), label.y(), DIM, false);
        }
        if (menu.spareCount() > 0) {
            int[] at = menu.layout().spareLabel();
            g.drawString(font, Component.translatable("skyabyss_pouch.category.spare"), at[0], at[1], DIM, false);
        }
        renderInfo(g);
    }

    // 下の説明: 枠の上にいるときは品名と数と使い方、いないときは「倒れても残る」の一行 (何も入っていなければ最初の案内)
    private void renderInfo(GuiGraphics g) {
        int y1 = PouchLayout.SEPARATOR_Y + 7;
        int y2 = y1 + 11;
        PouchEntries.Entry e = hoveredEntry();
        if (e == null) {
            boolean empty = Pouch.data(minecraft.player).isEmpty();
            Component line = Component.translatable(empty ? "skyabyss_pouch.screen.empty" : "skyabyss_pouch.screen.kept");
            drawWrapped(g, line, y1);
            return;
        }
        PouchData d = Pouch.data(minecraft.player);
        int n = d.count(e.id());
        if (!seen(e)) {
            jp.revati.skyabyss.client.ui.TextClip.line(g, font, Component.translatable("skyabyss_pouch.screen.unknown"), 12, y1, PouchLayout.W - 24, DIM, false);
            jp.revati.skyabyss.client.ui.TextClip.line(g, font, Component.translatable("skyabyss_pouch.screen.unknown_hint"), 12, y2, PouchLayout.W - 24, DIM, false);
            return;
        }
        Component count = Component.translatable("skyabyss_pouch.screen.count", n, e.limit());
        jp.revati.skyabyss.client.ui.TextClip.line(g, font, e.item().getDescription(), 12, y1, PouchLayout.W - 24 - font.width(count) - 6, n > 0 ? GOLD : DIM, false);
        g.drawString(font, count, PouchLayout.W - 12 - font.width(count), y1, n > 0 ? TEXT : DIM, false);
        Component hint;
        int color = DIM;
        if (n <= 0) {
            hint = Component.translatable("skyabyss_pouch.screen.missing");
        } else if (e.item() instanceof PouchUsable) {
            hint = Component.translatable("skyabyss_pouch.screen.use_key", PouchClient.USE_KEY.getTranslatedKeyMessage());
            color = OK;
        } else if (e.withdrawable() && menu.withdrawAllowed()) {
            hint = Component.translatable("skyabyss_pouch.screen.withdraw_on");
        } else {
            hint = Component.translatable("skyabyss_pouch.screen.kept");
        }
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(hint.getString()), 12, y2, PouchLayout.W - 24, color, false);
        if (n > 0 && e.item() instanceof PouchUsable) {
            Component pin = Component.translatable(QuickCard.isPinned(e.id()) ? "skyabyss_pouch.screen.unpin" : "skyabyss_pouch.screen.pin");
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(pin.getString()), 12, y2 + 10, PouchLayout.W - 24, DIM, false);
        }
    }

    private void drawWrapped(GuiGraphics g, Component line, int y) {
        jp.revati.skyabyss.client.ui.TextClip.wrapped(g, font, line, 12, y, PouchLayout.W - 24, 3, 10, DIM, false);
    }

    private PouchEntries.Entry hoveredEntry() {
        if (hoveredSlot == null) {
            return null;
        }
        int i = hoveredSlot.index;
        return i >= 0 && i < menu.pouchSlotCount() ? menu.entryAt(i) : null;
    }

    // 空の枠の名札、遺物のボタンの札
    private void renderExtraTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        if (jp.revati.skyabyss.client.reward.HeartRewardClient.pending() && hit(mx, my, RELIC_X, RELIC_Y, BTN_W, BTN_H)) {
            g.renderTooltip(font, Component.translatable("skyabyss_reward.pouch.tip"), mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index >= menu.pouchSlotCount()) {
            g.renderTooltip(font, Component.translatable("skyabyss_pouch.screen.spare_empty"), mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < menu.pouchSlotCount()) {
            PouchEntries.Entry e = menu.entryAt(hoveredSlot.index);
            Component name = seen(e) ? e.item().getDescription() : Component.translatable("skyabyss_pouch.screen.unknown");
            g.renderTooltip(font, name, mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- 押す
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;
        if (button == 0 && jp.revati.skyabyss.client.reward.HeartRewardClient.pending() && hit(mx, my, RELIC_X, RELIC_Y, BTN_W, BTN_H)) {
            // 遺物の画面はサーバーが開かせる (選べるかを決めるのはサーバー)
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
            jp.revati.skyabyss.client.reward.HeartRewardClient.request();
            return true;
        }
        if (button == 1 && hoveredSlot != null && hoveredSlot.index >= 0 && hoveredSlot.index < menu.pouchSlotCount()) {
            // 使える道具の枠を右クリック: 一枚札に置く／はずす
            PouchEntries.Entry e = menu.entryAt(hoveredSlot.index);
            if (e != null && e.item() instanceof PouchUsable && Pouch.data(minecraft.player).count(e.id()) > 0) {
                boolean on = QuickCard.togglePin(e.id());
                minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, on ? 1.2f : 0.9f);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (PouchClient.OPEN_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public int[] textBounds() {
        return new int[] {leftPos, topPos - AccessoryScreen.TAB_H, PouchLayout.W, PouchLayout.H + AccessoryScreen.TAB_H};
    }
}
