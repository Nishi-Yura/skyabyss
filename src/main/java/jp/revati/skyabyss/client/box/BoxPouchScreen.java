package jp.revati.skyabyss.client.box;

import java.util.List;

import jp.revati.skyabyss.client.inventory.GearPanel;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.loot.box.LootBoxSetup;
import jp.revati.skyabyss.loot.box.SealedBoxItem;
import jp.revati.skyabyss.pouch.Pouch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 封の箱の欄 (旅袋のつまみの四つ目。docs/design/52 の 7 章・00 の 310)。持っている封の箱を種類ごとに見る画面 (開けるのは町の封切り師)。
// 押すと一つ (シフトで全部) 持ち物へ取り出せる (落とす・仲間に渡すため。BoxNetwork.TakeOut)。
//   真ん中の板 (幅 176): 上に巨神の箱 4 種、中に階級の箱 (行 = 階級、列 = 帯 I〜V)、下に淵の金の箱・塔の箱・雲上の箱
//   左の板: 重ねた箱の名前と数。右の板: 手引き。持ち物と同じ組み方 (client/inventory/GearPanel。docs/design/56 の 4 章)
public class BoxPouchScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final int W = GearPanel.W, H = GearPanel.H;
    static final int GOLD = 0xFF000000 | Golden.GOLD, TEXT = 0xFF000000 | Golden.TEXT, DIM = 0xFF000000 | Golden.SOFT;
    static final int CORE_H = 180;
    private static final Golden.Box LEFT = GearPanel.left(CORE_H);
    private static final Golden.Box RIGHT = GearPanel.right(CORE_H);
    // 升目 (幅 176 の板の座標) を真ん中の板へ
    private static final int DX = GearPanel.CORE_X;
    private static final int DY = GearPanel.CORE_Y;
    static final int X0 = 8, PITCH = 18, BAND_X = 74;
    static final String[] NUMERALS = {"I", "II", "III", "IV", "V"};
    static final GearTier[] TIERS = {GearTier.UNCOMMON, GearTier.RARE, GearTier.EPIC, GearTier.HEROIC};

    record Spot(BoxKind kind, int x, int y) {
    }

    private final List<Spot> spots;

    public BoxPouchScreen() {
        super(Component.translatable("skyabyss_box.tab"));
        java.util.ArrayList<Spot> s = new java.util.ArrayList<>();
        for (BoxKind k : BoxKind.ALL) {
            switch (k.type()) {
                case TITAN -> s.add(new Spot(k, X0 + BoxKind.TITANS.indexOf(k.titan()) * (PITCH + 4), 29));
                case RARITY -> {
                    int row = java.util.Arrays.asList(TIERS).indexOf(k.tier());
                    s.add(new Spot(k, BAND_X + (k.band() - 1) * PITCH, 69 + row * 19));
                }
                case ARTIFACT -> s.add(new Spot(k, BAND_X, 69 + 4 * 19 + 4));
                case TOWER -> s.add(new Spot(k, BAND_X + PITCH + 4, 69 + 4 * 19 + 4));
                case CLOUD -> s.add(new Spot(k, BAND_X + 2 * (PITCH + 4), 69 + 4 * 19 + 4));
            }
        }
        spots = List.copyOf(s);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        GearPanel.addTabs(this::addRenderableWidget, this::left, this::top, 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static ItemStack stack(BoxKind k) {
        Item item = LootBoxSetup.item(k);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private int count(BoxKind k) {
        Item item = LootBoxSetup.item(k);
        return item == null || minecraft == null || minecraft.player == null ? 0 : Pouch.countAll(minecraft.player, item);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 他のつまみ (持ち物・装飾・旅袋) は AbstractContainerScreen の暗い幕だけ。素の Screen の暈し + 明るい幕を使うと、この欄だけ背景が明るくなる (2026-11-11 の声)
        renderTransparentBackground(g);
        int px = left(), py = top();
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        GearPanel.panels(g, CORE_H);
        // 右の板: 手引き
        int hy = RIGHT.y() + GearPanel.PAD + 1;
        for (var line : font.split(Component.translatable("skyabyss_box.pouch.hint"), RIGHT.w() - 2 * GearPanel.PAD)) {
            g.drawString(font, line, RIGHT.x() + GearPanel.PAD, hy, DIM, false);
            hy += Golden.ROW;
        }
        g.pose().popPose();
        int x = px + DX, y = py + DY;
        g.drawString(font, Component.translatable("skyabyss_box.pouch.titan"), x + X0, y + 19, Golden.PALE, false);
        g.drawString(font, Component.translatable("skyabyss_box.pouch.rarity"), x + X0, y + 52, Golden.PALE, false);
        for (int b = 0; b < 5; b++) {
            String n = NUMERALS[b];
            g.drawString(font, n, x + BAND_X + b * PITCH + (16 - font.width(n)) / 2, y + 58, DIM, false);
        }
        for (int r = 0; r < TIERS.length; r++) {
            g.drawString(font, Component.translatable(TIERS[r].translationKey()), x + X0, y + 73 + r * 19, 0xFF000000 | TIERS[r].rgb(), false);
        }
        g.drawString(font, Component.translatable("skyabyss_box.pouch.artifact"), x + X0, y + 73 + 4 * 19 + 4, 0xFF000000 | GearTier.ARTIFACT.rgb(), false);
        for (Spot s : spots) {
            int n = count(s.kind());
            int sx = x + s.x(), sy = y + s.y();
            Golden.slot(g, sx - 1, sy - 1, 18);
            if (n > 0) {
                Golden.outline(g, sx - 1, sy - 1, sx + 17, sy + 17, 0xFF5A8FC0);
            }
            g.renderItem(stack(s.kind()), sx, sy);
            if (n <= 0) {
                g.fill(sx, sy, sx + 16, sy + 16, 0xC80A0E1E);
            } else {
                String t = n > 999 ? "999+" : String.valueOf(n);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.drawString(font, t, sx + 17 - font.width(t), sy + 9, 0xFFFFFFFF, true);
                g.pose().popPose();
            }
        }
        renderInfo(g, px, py, hovered(mouseX, mouseY));
    }

    // 左の板: 重ねた箱の名前と数。重ねていなければ、持っている箱の合計
    private void renderInfo(GuiGraphics g, int px, int py, Spot s) {
        int x = px + LEFT.x() + GearPanel.PAD;
        int y = py + LEFT.y() + GearPanel.PAD + 1;
        int w = LEFT.w() - 2 * GearPanel.PAD;
        if (s == null) {
            int total = 0;
            for (Spot t : spots) {
                total += count(t.kind());
            }
            g.drawString(font, title, x, y, GOLD, false);
            g.drawString(font, Component.translatable("skyabyss_box.screen.count", total), x, y + Golden.ROW, total > 0 ? TEXT : DIM, false);
            return;
        }
        Component text = stack(s.kind()).getHoverName();
        int color = s == null ? DIM : 0xFF000000 | SealedBoxItem.color(s.kind());
        for (var line : font.split(text, w)) {
            g.drawString(font, line, x, y, color, false);
            y += Golden.ROW;
        }
        if (s != null) {
            int n = count(s.kind());
            g.drawString(font, Component.translatable("skyabyss_box.screen.count", n), x, y, n > 0 ? TEXT : DIM, false);
        }
    }

    private Spot hovered(int mx, int my) {
        int x = left() + DX, y = top() + DY;
        for (Spot s : spots) {
            if (mx >= x + s.x() - 1 && mx < x + s.x() + 17 && my >= y + s.y() - 1 && my < y + s.y() + 17) {
                return s;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Spot s = hovered(mouseX, mouseY);
        if (s != null) {
            g.renderTooltip(font, stack(s.kind()), mouseX, mouseY);
        }
    }

    // 箱を押すと一つ (シフトで全部) 持ち物へ取り出す。持ち物から Q で落とせる・仲間に渡せる (2026-11-06 の声)
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Spot s = hovered((int) mx, (int) my);
        if (s != null && button == 0 && count(s.kind()) > 0) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new jp.revati.skyabyss.loot.box.BoxNetwork.TakeOut(s.kind().id(), hasShiftDown()));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && (minecraft.options.keyInventory.matches(keyCode, scanCode)
                || jp.revati.skyabyss.client.pouch.PouchClient.OPEN_KEY.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public int[] textBounds() {
        return new int[] {left(), top(), W, GearPanel.CORE_Y + CORE_H};
    }
}
