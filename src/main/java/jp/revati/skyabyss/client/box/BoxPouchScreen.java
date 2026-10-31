package jp.revati.skyabyss.client.box;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.accessory.AccessoryScreen;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.loot.box.LootBoxSetup;
import jp.revati.skyabyss.loot.box.SealedBoxItem;
import jp.revati.skyabyss.pouch.Pouch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 封の箱の欄 (旅嚢のつまみの四つ目。docs/design/52 の 7 章・00 の 310)。持っている封の箱を種類ごとに見るだけの画面 (開けるのは町の封切り師)。
//   上: 巨神の箱 4 種 / 中: 階級の箱 (行 = 階級、列 = 帯 I〜V) / 下: 淵の金の箱
public class BoxPouchScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/pouch/pouch.png");
    static final int TEX = 512;
    static final int W = 176, H = 200;
    static final int GOLD = 0xFFF2C14E, TEXT = 0xFFD8DCE6, DIM = 0xFF9AA0B0;
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
            }
        }
        spots = List.copyOf(s);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2 + AccessoryScreen.TAB_H / 2;
    }

    @Override
    protected void init() {
        AccessoryScreen.addTabs(this::addRenderableWidget, this::left, this::top, 3);
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
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        g.blit(TEXTURE, x, y, W, H, 0, 0, W * 2, H * 2, TEX, TEX);
        g.drawString(font, title, x + 8, y + 5, GOLD, false);
        g.drawString(font, Component.translatable("skyabyss_box.pouch.titan"), x + X0, y + 19, DIM, false);
        g.drawString(font, Component.translatable("skyabyss_box.pouch.rarity"), x + X0, y + 52, DIM, false);
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
            g.blit(TEXTURE, sx - 1, sy - 1, 18, 18, n > 0 ? 400f : 360f, 0f, 36, 36, TEX, TEX);
            g.renderItem(stack(s.kind()), sx, sy);
            if (n <= 0) {
                g.fill(sx, sy, sx + 16, sy + 16, 0xC8141820);
            } else {
                String t = n > 999 ? "999+" : String.valueOf(n);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.drawString(font, t, sx + 17 - font.width(t), sy + 9, 0xFFFFFFFF, true);
                g.pose().popPose();
            }
        }
        renderInfo(g, x, y, hovered(mouseX, mouseY));
    }

    private void renderInfo(GuiGraphics g, int x, int y, Spot s) {
        int y1 = y + 167;
        if (s == null) {
            jp.revati.skyabyss.client.ui.TextClip.wrapped(g, font, Component.translatable("skyabyss_box.pouch.hint"), x + 12, y1, W - 24, 3, 10, DIM, false);
            return;
        }
        int n = count(s.kind());
        Component c = Component.translatable("skyabyss_box.screen.count", n);
        jp.revati.skyabyss.client.ui.TextClip.line(g, font, stack(s.kind()).getHoverName(), x + 12, y1, W - 30 - font.width(c), 0xFF000000 | SealedBoxItem.color(s.kind()), false);
        g.drawString(font, c, x + W - 12 - font.width(c), y1, n > 0 ? TEXT : DIM, false);
    }

    private Spot hovered(int mx, int my) {
        int x = left(), y = top();
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
        return new int[] {left(), top() - AccessoryScreen.TAB_H, W, H + AccessoryScreen.TAB_H};
    }
}
