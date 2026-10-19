package jp.revati.skyabyss.client.pouch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.accessory.AccessoryScreen;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.pouch.PouchData;
import jp.revati.skyabyss.pouch.PouchEntries;
import jp.revati.skyabyss.pouch.PouchLayout;
import jp.revati.skyabyss.pouch.PouchMenu;
import jp.revati.skyabyss.pouch.PouchRebuild;
import jp.revati.skyabyss.pouch.PouchUsable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

// 旅嚢の画面 (docs/design/22 の 9 章 A つまみ)。持ち物 / 装飾 / 旅嚢 のつまみの三つ目。
//   枠: 品ごとに決まった枠 (区分の名札つき)。まだ手に入れていない品は「？」、いちど手に入れて今は 0 の品は暗い影。
//       枠の置く・取り出す・シフトクリックは PouchMenu が決める (取り出せない決まりのときは何も起きない)
//   作り直し: 右上の「作り直し」で一覧に切り替わる。材料に袋の品を使う作業台のレシピを、袋の画面から作る (PouchRebuild)。
//       「修理」は手に持った品を袋の素材で直す。作るのはサーバーが決める (メニューのボタン)
public class PouchScreen extends AbstractContainerScreen<PouchMenu> {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/pouch/pouch.png");
    static final int TEX = 512;
    private static final int TEXT = 0xFFD8DCE6;
    private static final int DIM = 0xFF9AA0B0;
    private static final int GOLD = 0xFFF2C14E;
    private static final int OK = 0xFF7FD6D0;
    private static final int BAD = 0xFFE0505A;
    // 切り替えのボタン (右上)・修理のボタン (作り直しの一覧の下)
    private static final int BTN_W = 44;
    private static final int BTN_H = 12;
    private static final int TOGGLE_X = PouchLayout.W - 8 - BTN_W;
    private static final int TOGGLE_Y = 2;
    private static final int REPAIR_X = 12;
    private static final int REPAIR_Y = PouchLayout.SEPARATOR_Y + 22;
    // 作り直しの一覧
    private static final int ROW_X = 8;
    private static final int ROW_W = PouchLayout.W - 16 - 4;
    private static final int ROW_H = 22;
    private static final int ROW_Y0 = 30;
    private static final int ROWS_VISIBLE = (PouchLayout.SEPARATOR_Y - 2 - ROW_Y0) / ROW_H;

    // 作り直しの一覧の一行
    private record Row(int index, RecipeHolder<CraftingRecipe> holder, ItemStack result, boolean affordable) {
    }

    private List<Row> rows = List.of();
    private int scroll;
    private int refreshAt;

    public PouchScreen(PouchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PouchLayout.W;
        imageHeight = PouchLayout.H;
    }

    @Override
    protected void init() {
        super.init();
        AccessoryScreen.addTabs(this::addRenderableWidget, this, 2);
        refreshRows();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.rebuildView && ++refreshAt >= 5) {
            refreshAt = 0;
            refreshRows();
        }
    }

    // 作り直しの一覧を組み直す (材料の数が変わるので、見ている間は短い間隔で)
    private void refreshRows() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null || !menu.rebuildView) {
            rows = List.of();
            return;
        }
        List<RecipeHolder<CraftingRecipe>> all = PouchRebuild.recipes(minecraft.level.getRecipeManager(), minecraft.level.registryAccess());
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            RecipeHolder<CraftingRecipe> h = all.get(i);
            if (PouchRebuild.visible(minecraft.player, h.value())) {
                out.add(new Row(i, h, h.value().getResultItem(minecraft.level.registryAccess()).copy(), PouchRebuild.plan(minecraft.player, h.value()) != null));
            }
        }
        rows = out;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - ROWS_VISIBLE)));
    }

    // ---------------------------------------------------------------- 描く
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderExtraTooltips(g, mouseX, mouseY);
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
        if (menu.rebuildView) {
            renderRebuildBg(g, x, y, mouseX, mouseY);
            return;
        }
        for (int i = 0; i < menu.pouchSlotCount(); i++) {
            PouchLayout.Spot spot = menu.layout().spots().get(i);
            PouchEntries.Entry e = spot.entry();
            int n = Pouch.data(minecraft.player).count(e.id());
            boolean usable = n > 0 && e.item() instanceof PouchUsable;
            // 枠: 持っている品は青緑の縁、使える道具は金の縁、ないものは空
            float u = usable ? 440f : n > 0 ? 400f : 360f;
            g.blit(TEXTURE, x + spot.x() - 1, y + spot.y() - 1, 18, 18, u, 0f, 36, 36, TEX, TEX);
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

    private void renderRebuildBg(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int mx = mouseX - x;
        int my = mouseY - y;
        for (int r = 0; r < ROWS_VISIBLE; r++) {
            int idx = scroll + r;
            if (idx >= rows.size()) {
                break;
            }
            Row row = rows.get(idx);
            int ry = y + ROW_Y0 + r * ROW_H;
            boolean hover = mx >= ROW_X && mx < ROW_X + ROW_W && my >= ROW_Y0 + r * ROW_H && my < ROW_Y0 + r * ROW_H + ROW_H - 2;
            g.fill(x + ROW_X, ry, x + ROW_X + ROW_W, ry + ROW_H - 2, hover ? 0xFF2A3342 : 0xFF161A23);
            g.renderOutline(x + ROW_X, ry, ROW_W, ROW_H - 2, row.affordable() ? 0xFF2A5E6A : 0xFF30343E);
            g.renderItem(row.result(), x + ROW_X + 3, ry + 2);
            g.renderItemDecorations(font, row.result(), x + ROW_X + 3, ry + 2);
            Component name = row.result().getHoverName();
            String text = font.plainSubstrByWidth(name.getString(), ROW_W - 3 - 18 - 44 - 6);
            g.drawString(font, text, x + ROW_X + 24, ry + 7, row.affordable() ? TEXT : DIM, false);
            // 「作る」ボタン
            int bx = x + ROW_X + ROW_W - 3 - BTN_W + 4;
            boolean bh = row.affordable() && mx >= bx - x && mx < bx - x + BTN_W - 4 && my >= ROW_Y0 + r * ROW_H + 3 && my < ROW_Y0 + r * ROW_H + 3 + BTN_H;
            drawButton(g, bx, ry + 3, BTN_W - 4, BTN_H, row.affordable() ? (bh ? 1 : 0) : 2,
                    Component.translatable(row.affordable() ? "skyabyss_pouch.screen.make" : "skyabyss_pouch.screen.cant"));
        }
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("skyabyss_pouch.screen.rebuild_none"), x + PouchLayout.W / 2, y + 80, DIM);
        }
        // 一覧の位置を示す細い印
        if (rows.size() > ROWS_VISIBLE) {
            int track = ROWS_VISIBLE * ROW_H - 2;
            int barH = Math.max(8, track * ROWS_VISIBLE / rows.size());
            int barY = ROW_Y0 + (track - barH) * scroll / Math.max(1, rows.size() - ROWS_VISIBLE);
            g.fill(x + PouchLayout.W - 7, y + barY, x + PouchLayout.W - 5, y + barY + barH, 0xFF5E6A7A);
        }
        // 修理
        drawButton(g, x + REPAIR_X, y + REPAIR_Y, BTN_W, BTN_H, hit(mx, my, REPAIR_X, REPAIR_Y, BTN_W, BTN_H) ? 1 : 0,
                Component.translatable("skyabyss_pouch.screen.repair_btn"));
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
        boolean toggleHover = hit(mx, my, TOGGLE_X, TOGGLE_Y, BTN_W, BTN_H);
        drawButton(g, TOGGLE_X, TOGGLE_Y, BTN_W, BTN_H, toggleHover ? 1 : 0,
                Component.translatable(menu.rebuildView ? "skyabyss_pouch.screen.back_btn" : "skyabyss_pouch.screen.rebuild_btn"));
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (menu.rebuildView) {
            g.drawString(font, Component.translatable("skyabyss_pouch.screen.rebuild_title"), 8, 20, TEXT, false);
            Component hint = Component.translatable("skyabyss_pouch.screen.rebuild_hint");
            g.drawString(font, font.plainSubstrByWidth(hint.getString(), PouchLayout.W - 70), 56, 20, DIM, false);
            return;
        }
        for (PouchLayout.Label label : menu.layout().labels()) {
            g.drawString(font, Component.translatable("skyabyss_pouch.category." + label.category().id), label.x(), label.y(), DIM, false);
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
            g.drawString(font, Component.translatable("skyabyss_pouch.screen.unknown"), 12, y1, DIM, false);
            g.drawString(font, Component.translatable("skyabyss_pouch.screen.unknown_hint"), 12, y2, DIM, false);
            return;
        }
        g.drawString(font, e.item().getDescription(), 12, y1, n > 0 ? GOLD : DIM, false);
        Component count = Component.translatable("skyabyss_pouch.screen.count", n, e.limit());
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
        g.drawString(font, font.plainSubstrByWidth(hint.getString(), PouchLayout.W - 24), 12, y2, color, false);
    }

    private void drawWrapped(GuiGraphics g, Component line, int y) {
        int yy = y;
        for (var part : font.split(line, PouchLayout.W - 24)) {
            g.drawString(font, part, 12, yy, DIM, false);
            yy += 10;
        }
    }

    private PouchEntries.Entry hoveredEntry() {
        if (hoveredSlot == null || menu.rebuildView) {
            return null;
        }
        int i = hoveredSlot.index;
        return i >= 0 && i < menu.pouchSlotCount() ? menu.entryAt(i) : null;
    }

    // 空の枠の名札、作り直しの一覧の材料の札
    private void renderExtraTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        int mx = mouseX - leftPos;
        int my = mouseY - topPos;
        if (menu.rebuildView) {
            if (hit(mx, my, REPAIR_X, REPAIR_Y, BTN_W, BTN_H)) {
                g.renderTooltip(font, Component.translatable("skyabyss_pouch.screen.repair_hint"), mouseX, mouseY);
                return;
            }
            for (int r = 0; r < ROWS_VISIBLE && scroll + r < rows.size(); r++) {
                if (hit(mx, my, ROW_X, ROW_Y0 + r * ROW_H, ROW_W, ROW_H - 2)) {
                    g.renderComponentTooltip(font, ingredientLines(rows.get(scroll + r)), mouseX, mouseY);
                    return;
                }
            }
            return;
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < menu.pouchSlotCount()) {
            PouchEntries.Entry e = menu.entryAt(hoveredSlot.index);
            Component name = seen(e) ? e.item().getDescription() : Component.translatable("skyabyss_pouch.screen.unknown");
            g.renderTooltip(font, name, mouseX, mouseY);
        }
    }

    // 材料の札: 名前と、袋＋持ち物の持っている数 / 要る数
    private List<Component> ingredientLines(Row row) {
        List<Component> lines = new ArrayList<>();
        lines.add(row.result().getHoverName());
        lines.add(Component.translatable("skyabyss_pouch.screen.need").withStyle(s -> s.withColor(DIM & 0xFFFFFF)));
        Map<String, int[]> need = new LinkedHashMap<>();
        Map<String, Ingredient> byKey = new LinkedHashMap<>();
        for (Ingredient ing : row.holder().value().getIngredients()) {
            if (ing.isEmpty() || ing.getItems().length == 0) {
                continue;
            }
            String key = ing.getItems()[0].getItem().getDescriptionId();
            need.computeIfAbsent(key, k -> new int[]{0})[0]++;
            byKey.putIfAbsent(key, ing);
        }
        for (Map.Entry<String, int[]> en : need.entrySet()) {
            Ingredient ing = byKey.get(en.getKey());
            int have = 0;
            for (ItemStack option : ing.getItems()) {
                have = Math.max(have, Pouch.countAll(minecraft.player, option.getItem()));
            }
            boolean ok = have >= en.getValue()[0];
            Component name = ing.getItems()[0].getHoverName();
            lines.add(Component.empty().append(" ").append(name).append("  " + have + " / " + en.getValue()[0])
                    .withStyle(s -> s.withColor((ok ? OK : BAD) & 0xFFFFFF)));
        }
        return lines;
    }

    // ---------------------------------------------------------------- 押す
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX - leftPos;
        int my = (int) mouseY - topPos;
        if (button == 0 && hit(mx, my, TOGGLE_X, TOGGLE_Y, BTN_W, BTN_H)) {
            menu.rebuildView = !menu.rebuildView;
            scroll = 0;
            refreshRows();
            return true;
        }
        if (menu.rebuildView) {
            if (button == 0 && hit(mx, my, REPAIR_X, REPAIR_Y, BTN_W, BTN_H)) {
                press(PouchRebuild.BUTTON_REPAIR);
                return true;
            }
            if (button == 0) {
                for (int r = 0; r < ROWS_VISIBLE && scroll + r < rows.size(); r++) {
                    Row row = rows.get(scroll + r);
                    if (row.affordable() && hit(mx, my, ROW_X + ROW_W - 3 - BTN_W + 4, ROW_Y0 + r * ROW_H + 3, BTN_W - 4, BTN_H)) {
                        press(PouchRebuild.BUTTON_RECIPE_BASE + row.index());
                        return true;
                    }
                }
            }
            // 一覧の間は枠を押さない (枠は隠れている)
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void press(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            refreshAt = 0;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.rebuildView && rows.size() > ROWS_VISIBLE) {
            scroll = Math.max(0, Math.min(rows.size() - ROWS_VISIBLE, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
    public void removed() {
        menu.rebuildView = false;
        super.removed();
    }
}
