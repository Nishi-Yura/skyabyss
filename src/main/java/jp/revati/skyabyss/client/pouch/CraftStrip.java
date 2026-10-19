package jp.revati.skyabyss.client.pouch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.pouch.PouchConfig;
import jp.revati.skyabyss.pouch.PouchCraft;
import jp.revati.skyabyss.pouch.PouchEntries;
import jp.revati.skyabyss.pouch.PouchNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 作業台 (と持ち物の 2x2) の画面の右に出す「旅嚢から」の帯 (docs/design/22 の 17 章)。
// 旅嚢の材料のうち、作業台のレシピの材料になる品を並べる。左クリック = 一スタックをカーソルへ取る、右クリック = 一つ取る。
// カーソルに同じ品を持っているときは、左クリック = 全部、右クリック = 一つを旅嚢へ戻す。決めるのはサーバー (PouchNet.Grid)。
// 位置は画面の右の縁に毎回合わせる (レシピブックを開いて画面が動いても付いていく)。持ち物の画面・作業台の画面だけ (クリエイティブには足さない)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class CraftStrip {
    private static final int CELL = 18;
    private static final int ROWS = 8;
    private static final int HEAD = 12;

    private CraftStrip() {
    }

    private record Cell(PouchEntries.Entry entry, int x, int y, int count) {
    }

    private static boolean applies(Screen screen) {
        return (screen instanceof CraftingScreen || screen instanceof InventoryScreen) && PouchConfig.enabled();
    }

    // 帯に出す品 (旅嚢に在るもの + カーソルに持っている旅嚢の品) と、画面の上の位置
    private static List<Cell> cells(AbstractContainerScreen<?> screen) {
        LocalPlayer player = Minecraft.getInstance().player;
        List<Cell> out = new ArrayList<>();
        if (player == null || Minecraft.getInstance().level == null) {
            return out;
        }
        ItemStack carried = screen.getMenu().getCarried();
        int x0 = screen.getGuiLeft() + screen.getXSize() + 4;
        int y0 = screen.getGuiTop() + HEAD;
        int i = 0;
        for (PouchEntries.Entry e : PouchCraft.usableInRecipes(Minecraft.getInstance().level.getRecipeManager())) {
            int n = Pouch.count(player, e.item());
            if (n <= 0 && !carried.is(e.item())) {
                continue;
            }
            out.add(new Cell(e, x0 + (i / ROWS) * CELL, y0 + (i % ROWS) * CELL, n));
            i++;
        }
        return out;
    }

    @Nullable
    private static Cell at(List<Cell> cells, double mx, double my) {
        for (Cell c : cells) {
            if (mx >= c.x() && mx < c.x() + CELL && my >= c.y() && my < c.y() + CELL) {
                return c;
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        if (!applies(event.getScreen()) || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        List<Cell> cells = cells(screen);
        if (cells.isEmpty()) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int x0 = cells.get(0).x();
        int y0 = screen.getGuiTop();
        int cols = (cells.size() + ROWS - 1) / ROWS;
        int rows = Math.min(ROWS, cells.size());
        int w = cols * CELL;
        // 台座と見出し
        g.fill(x0 - 2, y0, x0 + w + 2, y0 + HEAD + rows * CELL + 2, 0xE0201A14);
        g.renderOutline(x0 - 2, y0, w + 4, HEAD + rows * CELL + 2, 0xFF8A7A5A);
        g.drawString(font, Component.translatable("skyabyss_pouch.craft.title"), x0 + 1, y0 + 2, 0xFFE8D8A8, false);
        Cell hover = at(cells, event.getMouseX(), event.getMouseY());
        for (Cell c : cells) {
            g.fill(c.x() + 1, c.y() + 1, c.x() + CELL - 1, c.y() + CELL - 1, c == hover ? 0xFF5A5040 : 0xFF3A3328);
            ItemStack shown = new ItemStack(c.entry().item());
            g.renderItem(shown, c.x() + 1, c.y() + 1);
            g.renderItemDecorations(font, shown, c.x() + 1, c.y() + 1, c.count() > 0 ? Integer.toString(c.count()) : "0");
        }
        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(hover.entry().item().getDescription());
            tip.add(Component.translatable("skyabyss_pouch.craft.have", hover.count()).withStyle(net.minecraft.ChatFormatting.GRAY));
            tip.add(Component.translatable("skyabyss_pouch.craft.hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            g.renderTooltip(font, tip, Optional.empty(), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!applies(event.getScreen()) || !(event.getScreen() instanceof AbstractContainerScreen<?> screen) || event.getButton() > 1) {
            return;
        }
        Cell cell = at(cells(screen), event.getMouseX(), event.getMouseY());
        if (cell != null) {
            PacketDistributor.sendToServer(new PouchNet.Grid(cell.entry().id(), event.getButton() == 1));
            event.setCanceled(true);
        }
    }
}
