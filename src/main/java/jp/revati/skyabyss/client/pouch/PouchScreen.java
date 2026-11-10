package jp.revati.skyabyss.client.pouch;

import jp.revati.skyabyss.client.inventory.GearPanel;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.pouch.PouchData;
import jp.revati.skyabyss.pouch.PouchConfig;
import jp.revati.skyabyss.pouch.PouchEntries;
import jp.revati.skyabyss.pouch.PouchLayout;
import jp.revati.skyabyss.pouch.PouchMenu;
import jp.revati.skyabyss.pouch.PouchUsable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

// 旅袋の画面 (docs/design/22 の 9 章 A つまみ・56 の 4 章)。持ち物 / 装飾 / 旅袋 / 封印の箱 のつまみの三つ目。持ち物と同じ大きい板で、
//   左の欄: ページの栞 (大事な物・巨神の素材・土地の素材・細工) と、重ねた品の説明 (名前・数・使い方)、心臓の遺物の釦
//   右の欄: 品ごとに決まった枠 (区分の名札つき)。まだ手に入れていない品は「？」、いちど手に入れて今は 0 の品は暗い影。
//       枠の置く・取り出す・シフトクリックは PouchMenu が決める (取り出せない決まりのときは何も起きない)
// 枠の並び (pouch/PouchLayout) は幅 176 の升目のまま使い、右の欄の中へ DX・DY だけずらして置く
public class PouchScreen extends AbstractContainerScreen<PouchMenu> implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final int TEXT = 0xFF000000 | Golden.TEXT;
    private static final int DIM = 0xFF000000 | Golden.SOFT;
    private static final int GOLD = 0xFF000000 | Golden.GOLD;
    private static final int OK = 0xFF000000 | Golden.PALE;
    private static final Golden.Box LEFT = GearPanel.LEFT;
    private static final Golden.Box RIGHT = GearPanel.RIGHT;
    // 升目 (PouchLayout の座標) を右の欄の中へ
    private static final int DX = RIGHT.x() + (RIGHT.w() - PouchLayout.COLS * PouchLayout.PITCH) / 2 - PouchLayout.X0;
    private static final int DY = RIGHT.y() + 6 - PouchLayout.TOP;
    // 左の欄: 栞の行、説明の始まり、遺物の釦
    private static final int BOOK_H = 21;
    private static final int INFO_X = LEFT.x() + 6;
    private static final int INFO_W = LEFT.w() - 12;
    private static final int INFO_Y = LEFT.y() + 5 + 4 * (BOOK_H + 1) + 8;
    private static final Golden.Box RELIC = new Golden.Box(LEFT.x() + 6, LEFT.bottom() - 6 - Golden.BTN, LEFT.w() - 12, Golden.BTN);

    public PouchScreen(PouchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = GearPanel.W;
        imageHeight = GearPanel.H;
    }

    @Override
    protected void init() {
        super.init();
        // 枠の置き場 (メニューの並び: 品の枠・転生の余白・細工)
        int n = menu.pouchSlotCount();
        for (int i = 0; i < menu.slots.size(); i++) {
            int[] at = i < n ? new int[] {menu.layout().spots().get(i).x(), menu.layout().spots().get(i).y()}
                    : i < n + menu.spareCount() ? menu.layout().spare().get(i - n) : PouchLayout.part(i - n - menu.spareCount());
            menu.slots.get(i).x = at[0] + DX;
            menu.slots.get(i).y = at[1] + DY;
        }
        GearPanel.addTabs(this::addRenderableWidget, () -> leftPos, () -> topPos, 2);
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
            if (spot.page() == menu.page() && d.isFresh(spot.entry().id()) && d.count(spot.entry().id()) > 0) {
                PouchNew.badge(g, font, leftPos + spot.x() + DX, topPos + spot.y() + DY);
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
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        Golden.frame(g, font, GearPanel.layout(), title);
        Golden.recess(g, LEFT);
        Golden.recess(g, RIGHT);
        g.pose().popPose();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        renderBookmarks(g, x, y, mouseX, mouseY);
        Golden.rule(g, x + LEFT.x() + 6, x + LEFT.right() - 6, y + INFO_Y - 5);
        if (jp.revati.skyabyss.client.reward.HeartRewardClient.pending()) {
            // 心臓の遺物: 金の釦 (ゆっくり点る)
            Golden.Box b = new Golden.Box(x + RELIC.x(), y + RELIC.y(), RELIC.w(), RELIC.h());
            Golden.button(g, font, b, Component.translatable("skyabyss_reward.pouch.button"), true, b.contains(mouseX, mouseY), true);
            int glow = 0x40 + (int) (0x50 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0)));
            g.renderOutline(b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2, (glow << 24) | Golden.GOLD);
        }
        int gx = x + DX;
        int gy = y + DY;
        if (menu.page() == PouchConfig.Page.KEEP) {
            for (int i = 0; i < menu.spareCount(); i++) {
                boolean has = !menu.getSlot(menu.pouchSlotCount() + i).getItem().isEmpty();
                int[] at = menu.layout().spare().get(i);
                frame(g, gx + at[0], gy + at[1], has ? 1 : 0);
            }
        }
        if (menu.page() == PouchConfig.Page.PARTS) {
            PouchData pd = Pouch.data(minecraft.player);
            for (int i = 0; i < PouchData.PARTS; i++) {
                int[] at = PouchLayout.part(i);
                frame(g, gx + at[0], gy + at[1], pd.part(i).isEmpty() ? 0 : 1);
            }
        }
        for (int i = 0; i < menu.pouchSlotCount(); i++) {
            PouchLayout.Spot spot = menu.layout().spots().get(i);
            if (spot.page() != menu.page()) {
                continue;
            }
            PouchEntries.Entry e = spot.entry();
            int n = Pouch.data(minecraft.player).count(e.id());
            boolean usable = n > 0 && e.item() instanceof PouchUsable;
            int sx = gx + spot.x();
            int sy = gy + spot.y();
            // 枠: 持っている品は空色の縁、使える道具は金の縁、ないものは空
            frame(g, sx, sy, usable ? 2 : n > 0 ? 1 : 0);
            if (usable && QuickCard.isPinned(e.id())) {
                // 札に置いた印 (角の金の点)
                g.fill(sx - 1, sy - 1, sx + 2, sy, GOLD);
                g.fill(sx - 1, sy - 1, sx, sy + 2, GOLD);
            }
            if (n <= 0) {
                if (seen(e)) {
                    // いちど手に入れた品: 暗い影 (今は手もとにない)
                    g.renderItem(new ItemStack(e.item()), sx, sy);
                    g.fill(sx, sy, sx + 16, sy + 16, 0xC80A0E1E);
                } else {
                    g.drawString(font, "?", sx + 8 - font.width("?") / 2, sy + 4, 0xFF000000 | Golden.DIM, false);
                }
            }
        }
    }

    // 品の枠 (16 角の品の左上から)。state 0 空 / 1 持っている (空色の縁) / 2 使える道具 (金の縁)
    private static void frame(GuiGraphics g, int x, int y, int state) {
        Golden.slot(g, x - 1, y - 1, 18);
        if (state > 0) {
            Golden.outline(g, x - 1, y - 1, x + 17, y + 17, state == 2 ? GOLD : 0xFF5A8FC0);
        }
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // 旅袋の枠の鐘銭は、枠の上に数を出さない (2026-11-01「数字がすごいため見せなくていい」。数は下の説明の行と店の画面に出る)
    @Override
    protected void renderSlot(GuiGraphics g, net.minecraft.world.inventory.Slot slot) {
        ItemStack s = slot.getItem();
        if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory) && s.is(jp.revati.skyabyss.facility.FacilitySetup.BELL_COIN.get())) {
            g.renderItem(s, slot.x, slot.y);
            return;
        }
        super.renderSlot(g, slot);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        for (PouchLayout.Label label : menu.layout().labels()) {
            if (label.page() != menu.page()) {
                continue;
            }
            g.drawString(font, Component.translatable("skyabyss_pouch.category." + label.category().id), label.x() + DX, label.y() + DY, Golden.PALE, false);
        }
        if (menu.page() == PouchConfig.Page.PARTS) {
            g.drawString(font, Component.translatable("skyabyss_pouch.category.parts"), PouchLayout.X0 + DX, PouchLayout.TOP + DY, Golden.PALE, false);
        }
        if (menu.spareCount() > 0 && menu.page() == PouchConfig.Page.KEEP) {
            int[] at = menu.layout().spareLabel();
            g.drawString(font, Component.translatable("skyabyss_pouch.category.spare"), at[0] + DX, at[1] + DY, Golden.PALE, false);
        }
        renderInfo(g);
    }

    // 左の欄の説明: 枠の上にいるときは品名と数と使い方、いないときは「倒れても残る」の一行 (何も入っていなければ最初の案内)
    private void renderInfo(GuiGraphics g) {
        int y1 = INFO_Y;
        int y2 = y1 + Golden.ROW;
        PouchEntries.Entry e = hoveredEntry();
        if (e == null && menu.page() == PouchConfig.Page.PARTS) {
            ItemStack part = hoveredSlot != null ? hoveredSlot.getItem() : ItemStack.EMPTY;
            Component line = part.isEmpty() ? Component.translatable("skyabyss_pouch.screen.parts_hint")
                    : Component.empty().append(part.getHoverName()).append(" ×" + part.getCount());
            drawWrapped(g, line, y1);
            return;
        }
        if (e == null) {
            boolean empty = Pouch.data(minecraft.player).isEmpty();
            Component line = Component.translatable(empty ? "skyabyss_pouch.screen.empty" : "skyabyss_pouch.screen.kept");
            drawWrapped(g, line, y1);
            return;
        }
        PouchData d = Pouch.data(minecraft.player);
        int n = d.count(e.id());
        if (!seen(e)) {
            g.drawString(font, Component.translatable("skyabyss_pouch.screen.unknown"), INFO_X, y1, DIM, false);
            wrap(g, Component.translatable("skyabyss_pouch.screen.unknown_hint"), y2, 4, DIM);
            return;
        }
        Component count = Component.translatable("skyabyss_pouch.screen.count", n, e.limit());
        int y = wrap(g, e.item().getDescription(), y1, 2, n > 0 ? GOLD : DIM);
        g.drawString(font, count, INFO_X, y, n > 0 ? TEXT : DIM, false);
        y += Golden.ROW;
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
        y = wrap(g, hint, y, 2, color);
        if (n > 0 && e.item() instanceof PouchUsable) {
            wrap(g, Component.translatable(QuickCard.isPinned(e.id()) ? "skyabyss_pouch.screen.unpin" : "skyabyss_pouch.screen.pin"), y, 2, DIM);
        }
    }

    // 左の欄の幅で折り返して描き、次の行の y を返す
    private int wrap(GuiGraphics g, Component text, int y, int maxLines, int color) {
        var lines = font.split(text, INFO_W);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) {
            g.drawString(font, lines.get(i), INFO_X, y + i * Golden.ROW, color, false);
        }
        return y + Math.max(1, n) * Golden.ROW;
    }

    private void drawWrapped(GuiGraphics g, Component line, int y) {
        wrap(g, line, y, 5, DIM);
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
        if (jp.revati.skyabyss.client.reward.HeartRewardClient.pending() && RELIC.contains(mx, my)) {
            g.renderTooltip(font, Component.translatable("skyabyss_reward.pouch.tip"), mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index >= menu.pouchSlotCount() + menu.spareCount()) {
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

    // ---------------------------------------------------------------- ページの栞 (左の欄の上。絵と名前の行)
    private static net.minecraft.world.item.Item bookmarkIcon(PouchConfig.Page p) {
        return switch (p) {
            case KEEP -> jp.revati.skyabyss.registry.ModItems.SKY_KEY_FRAGMENT.get();
            case TITAN -> jp.revati.skyabyss.registry.ModItems.STONE_WHALE_SHARD.get();
            case LAND -> jp.revati.skyabyss.guide.GuideSetup.BONE_CHIP.get();
            case PARTS -> jp.revati.skyabyss.registry.ModItems.ASPECT_CORE.get();
        };
    }

    private static Golden.Box bookmark(PouchConfig.Page p) {
        return new Golden.Box(LEFT.x() + 3, LEFT.y() + 4 + p.ordinal() * (BOOK_H + 1), LEFT.w() - 6, BOOK_H);
    }

    private PouchConfig.Page bookmarkAt(int mouseX, int mouseY) {
        for (PouchConfig.Page p : PouchConfig.Page.values()) {
            if (bookmark(p).contains(mouseX - leftPos, mouseY - topPos)) {
                return p;
            }
        }
        return null;
    }

    // そのページに、まだ見ていない新しい品があるか (栞の点)
    private boolean pageFresh(PouchConfig.Page p) {
        PouchData d = Pouch.data(minecraft.player);
        for (PouchLayout.Spot spot : menu.layout().spots()) {
            if (spot.page() == p && d.isFresh(spot.entry().id()) && d.count(spot.entry().id()) > 0) {
                return true;
            }
        }
        return false;
    }

    private void renderBookmarks(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        for (PouchConfig.Page p : PouchConfig.Page.values()) {
            boolean on = p == menu.page();
            Golden.Box b = bookmark(p);
            Golden.Box at = new Golden.Box(x + b.x(), y + b.y(), b.w(), b.h());
            if (on) {
                Golden.selected(g, at);
            } else if (at.contains(mouseX, mouseY)) {
                g.fill(at.x(), at.y(), at.right(), at.bottom(), 0x18FFFFFF);
            }
            g.renderItem(new ItemStack(bookmarkIcon(p)), at.x() + 5, at.y() + 2);
            g.drawString(font, Component.translatable("skyabyss_pouch.page." + p.id), at.x() + 26, Golden.textY(at.y(), at.h()), on ? Golden.GOLD : Golden.SOFT, false);
            if (pageFresh(p)) {
                g.fill(at.right() - 6, at.y() + 9, at.right() - 3, at.y() + 12, 0xFF000000 | Golden.PALE);
            }
        }
    }

    // ---------------------------------------------------------------- 押す
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;
        if (button == 0 && jp.revati.skyabyss.client.reward.HeartRewardClient.pending() && RELIC.contains(mx, my)) {
            // 遺物の画面はサーバーが開かせる (選べるかを決めるのはサーバー)
            minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
            jp.revati.skyabyss.client.reward.HeartRewardClient.request();
            return true;
        }
        PouchConfig.Page tab = bookmarkAt((int) mouseX, (int) mouseY);
        if (button == 0 && tab != null) {
            if (tab != menu.page()) {
                menu.setPage(tab);
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, tab.ordinal());
                minecraft.player.playSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.6f, 1.1f);
            }
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
        return new int[] {leftPos, topPos, imageWidth, imageHeight};
    }
}
