package jp.revati.skyabyss.client.box;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.loot.box.BoxNetwork;
import jp.revati.skyabyss.loot.box.LootBoxSetup;
import jp.revati.skyabyss.loot.box.LootBoxes;
import jp.revati.skyabyss.loot.box.SealedBoxItem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// 封切り師の画面 (docs/design/52 の 6 章)。
//   一覧: 持っている箱 (種類・帯・数) から選び「開ける」「まとめて開ける」
//   ルーレット: 横に流れる品の帯が 3〜4 秒でゆっくり止まり、真ん中の枠に当たり (帯と当たりはサーバーが決めて送る。画面は見せるだけ)。
//               止まる前後の品は表のいちばん上の階級 (惜しい品)。品が枠を通るたびに刻み、止まると階級ごとの音
//   当たり: 大きなカード (名前・階級・特性・必要 Lv)。「もう一つ開ける」「一覧へ」
//   まとめて: 一つ 1 秒で回し、最後に当たりの一覧。Esc で終わりまで飛ばす
public class BoxScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final int W = 300, H = 200;
    static final int CELL = 30, COLS = 5;
    static final int RC = 28;
    static final int GOLD = 0xFFF2C14E, TEXT = 0xFFD8DCE6, DIM = 0xFF9AA0B0, LINE = 0xFF3A4252;
    static final float SINGLE_MS = 3800f, BATCH_MS = 1100f;

    enum Mode {
        LIST, WAIT, SPIN, CARD, SUMMARY
    }

    private List<String> kinds = new ArrayList<>();
    private List<Integer> counts = new ArrayList<>();
    private int selected;
    private Mode mode = Mode.LIST;
    private BoxNetwork.Result result;
    private int spinIndex;
    private long spinStart;
    private float spinMs;
    private float jitter;
    private int lastTick;
    private boolean landed;
    private long landedAt;

    BoxScreen(BoxNetwork.View view) {
        super(Component.translatable("skyabyss_box.screen.title"));
        update(view);
    }

    void update(BoxNetwork.View view) {
        String keep = selectedKind();
        kinds = new ArrayList<>(view.kinds());
        counts = new ArrayList<>(view.counts());
        int i = keep == null ? -1 : kinds.indexOf(keep);
        selected = i >= 0 ? i : Mth.clamp(selected, 0, Math.max(0, kinds.size() - 1));
        if (mode == Mode.LIST || mode == Mode.CARD) {
            rebuild();
        }
    }

    private String selectedKind() {
        return selected >= 0 && selected < kinds.size() ? kinds.get(selected) : null;
    }

    private int countOf(String kind) {
        int i = kinds.indexOf(kind);
        return i < 0 ? 0 : counts.get(i);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- ボタン
    private void rebuild() {
        clearWidgets();
        int x = left(), y = top();
        switch (mode) {
            case LIST -> {
                String k = selectedKind();
                if (k != null) {
                    int n = countOf(k);
                    addRenderableWidget(Button.builder(Component.translatable("skyabyss_box.screen.open"), b -> request(k, 1))
                            .bounds(x + 172, y + H - 54, 116, 20).build());
                    if (n > 1) {
                        int all = Math.min(n, LootBoxes.MAX_BATCH);
                        addRenderableWidget(Button.builder(Component.translatable("skyabyss_box.screen.open_all", all), b -> request(k, all))
                                .bounds(x + 172, y + H - 30, 116, 20).build());
                    }
                }
            }
            case CARD -> {
                String k = result.kind();
                if (countOf(k) > 0) {
                    addRenderableWidget(Button.builder(Component.translatable("skyabyss_box.screen.again"), b -> request(k, 1))
                            .bounds(x + W / 2 - 122, y + H - 28, 118, 20).build());
                }
                addRenderableWidget(Button.builder(Component.translatable("skyabyss_box.screen.back"), b -> toList())
                        .bounds(x + W / 2 + 4, y + H - 28, 118, 20).build());
            }
            case SUMMARY -> addRenderableWidget(Button.builder(Component.translatable("skyabyss_box.screen.back"), b -> toList())
                    .bounds(x + W / 2 - 59, y + H - 28, 118, 20).build());
            default -> {
            }
        }
    }

    private void request(String kind, int n) {
        mode = Mode.WAIT;
        rebuild();
        PacketDistributor.sendToServer(new BoxNetwork.Open(kind, n));
    }

    private void toList() {
        mode = Mode.LIST;
        result = null;
        rebuild();
    }

    // ---------------------------------------------------------------- ルーレット
    void spin(BoxNetwork.Result r) {
        result = r;
        spinIndex = 0;
        mode = Mode.SPIN;
        startSpin();
        rebuild();
    }

    private void startSpin() {
        spinStart = Util.getMillis();
        spinMs = result.gear().size() > 1 ? BATCH_MS : SINGLE_MS;
        // 止まる所を枠の中で少しずらす (いつも真ん中ぴったりでは作り物に見える)
        jitter = (float) (Math.random() - 0.5) * 0.56f;
        lastTick = -1;
        landed = false;
    }

    private float offset() {
        float t = Mth.clamp((Util.getMillis() - spinStart) / spinMs, 0f, 1f);
        float e = 1f - (float) Math.pow(1f - t, 4);
        return (LootBoxes.WIN + jitter * (1f - landedPull())) * RC * e;
    }

    // 止まってから、ずれを真ん中へゆっくり寄せる
    private float landedPull() {
        return landed ? Mth.clamp((Util.getMillis() - landedAt) / 400f, 0f, 1f) : 0f;
    }

    @Override
    public void tick() {
        if (mode != Mode.SPIN || result == null) {
            return;
        }
        long now = Util.getMillis();
        if (!landed && now - spinStart >= spinMs) {
            land(result.gear().get(spinIndex));
        }
        boolean batch = result.gear().size() > 1;
        if (landed && now - landedAt > (batch ? 380 : 900)) {
            if (spinIndex + 1 < result.gear().size()) {
                spinIndex++;
                startSpin();
            } else {
                mode = batch ? Mode.SUMMARY : Mode.CARD;
                rebuild();
            }
        }
    }

    private void land(ItemStack gear) {
        landed = true;
        landedAt = Util.getMillis();
        GearTier t = tier(gear);
        if (t.atLeast(GearTier.HEROIC)) {
            play(SoundEvents.BELL_BLOCK, 0.8f, 1.0f);
            play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
        } else if (t.atLeast(GearTier.EPIC)) {
            play(SoundEvents.PLAYER_LEVELUP, 0.6f, 1.3f);
        } else {
            play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.1f);
        }
    }

    private void skip() {
        GearTier best = GearTier.COMMON;
        ItemStack top = ItemStack.EMPTY;
        for (int i = spinIndex; i < result.gear().size(); i++) {
            GearTier t = tier(result.gear().get(i));
            if (top.isEmpty() || t.atLeast(best)) {
                best = t;
                top = result.gear().get(i);
            }
        }
        if (!landed && !top.isEmpty()) {
            land(top);
        }
        mode = result.gear().size() > 1 ? Mode.SUMMARY : Mode.CARD;
        rebuild();
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(sound, volume, pitch);
        }
    }

    static GearTier tier(ItemStack s) {
        return s.getItem() instanceof ModGear g ? g.tier() : GearTier.COMMON;
    }

    // ---------------------------------------------------------------- 描く
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0000000);
        int x = left(), y = top();
        panel(g, x, y, W, H, LINE);
        switch (mode) {
            case LIST, WAIT -> drawList(g, x, y, mouseX, mouseY);
            case SPIN -> drawSpin(g, x, y);
            case CARD -> drawCard(g, x, y);
            case SUMMARY -> drawSummary(g, x, y);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ItemStack hover = hovered(mouseX, mouseY);
        if (!hover.isEmpty()) {
            g.renderTooltip(font, hover, mouseX, mouseY);
        }
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, int line) {
        g.fill(x, y, x + w, y + h, 0xFF03050A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, line);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF151A24);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x1AFFFFFF);
    }

    private void item(GuiGraphics g, ItemStack s, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
    }

    private static ItemStack boxStack(String kind) {
        BoxKind k = BoxKind.byId(kind);
        Item item = k == null ? null : LootBoxSetup.item(k);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static int boxColor(String kind) {
        BoxKind k = BoxKind.byId(kind);
        return 0xFF000000 | (k == null ? 0x808080 : SealedBoxItem.color(k));
    }

    private int lines(GuiGraphics g, List<Component> lines, int x, int y, int w, int maxY, int color) {
        for (Component c : lines) {
            for (FormattedCharSequence s : font.split(c, w)) {
                if (y + 9 > maxY) {
                    return y;
                }
                g.drawString(font, s, x, y, color, false);
                y += 10;
            }
        }
        return y;
    }

    private void drawList(GuiGraphics g, int x, int y, int mx, int my) {
        g.drawString(font, title, x + 10, y + 9, GOLD, false);
        g.fill(x + 10, y + 21, x + W - 10, y + 22, LINE);
        if (kinds.isEmpty()) {
            lines(g, List.of(Component.translatable("skyabyss_box.screen.none")), x + 20, y + 80, W - 40, y + H - 10, DIM);
            return;
        }
        for (int i = 0; i < kinds.size(); i++) {
            int cx = x + 10 + (i % COLS) * CELL, cy = y + 28 + (i / COLS) * CELL;
            boolean sel = i == selected;
            int col = boxColor(kinds.get(i));
            g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, sel ? col : 0xFF0A0D14);
            g.fill(cx + 1, cy + 1, cx + CELL - 3, cy + CELL - 3, sel ? 0xFF262B38 : 0xFF1B202B);
            g.fill(cx + 1, cy + CELL - 4, cx + CELL - 3, cy + CELL - 3, col);
            item(g, boxStack(kinds.get(i)), cx + 2, cy + 2, 1.5f);
            String n = String.valueOf(counts.get(i));
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, n, cx + CELL - 3 - font.width(n), cy + CELL - 12, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
        // 右: 選んだ箱
        String k = selectedKind();
        if (k != null) {
            ItemStack s = boxStack(k);
            int px = x + 172;
            g.fill(px - 6, y + 28, px - 5, y + H - 10, LINE);
            List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, s));
            int ty = lines(g, List.of(s.getHoverName()), px, y + 30, W - 182, y + H - 60, 0xFFFFFFFF);
            tip.remove(0);
            ty = lines(g, tip, px, ty + 3, W - 182, y + H - 70, DIM);
            g.drawString(font, Component.translatable("skyabyss_box.screen.count", countOf(k)), px, ty + 4, TEXT, false);
        }
        if (mode == Mode.WAIT) {
            Component w = Component.translatable("skyabyss_box.screen.cutting");
            g.drawString(font, w, x + 172, y + H - 70, GOLD, false);
        }
    }

    private void drawSpin(GuiGraphics g, int x, int y) {
        ItemStack box = boxStack(result.kind());
        Component head = box.getHoverName();
        g.drawString(font, head, x + (W - font.width(head)) / 2, y + 12, boxColor(result.kind()), false);
        if (result.gear().size() > 1) {
            String n = (spinIndex + 1) + " / " + result.gear().size();
            g.drawString(font, n, x + W - 12 - font.width(n), y + 12, DIM, false);
        }
        List<ItemStack> reel = result.reels().get(spinIndex);
        int bandY = y + H / 2 - 26;
        int cx = x + W / 2;
        g.fill(x + 10, bandY - 2, x + W - 10, bandY + 42, 0xFF0A0D14);
        float off = offset();
        g.enableScissor(x + 11, bandY - 2, x + W - 11, bandY + 42);
        for (int i = 0; i < reel.size(); i++) {
            float ix = cx + i * RC - off - RC / 2f;
            if (ix < x - RC || ix > x + W) {
                continue;
            }
            int ixi = Math.round(ix);
            ItemStack s = reel.get(i);
            int col = 0xFF000000 | tier(s).rgb();
            g.fill(ixi + 1, bandY + 2, ixi + RC - 1, bandY + 38, 0xFF1B202B);
            g.fill(ixi + 1, bandY + 35, ixi + RC - 1, bandY + 38, col);
            item(g, s, ixi + 3, bandY + 8, 1.375f);
        }
        g.disableScissor();
        // 刻み: 品が真ん中の枠を通るたび
        int idx = (int) Math.floor((off + RC / 2f) / RC);
        if (idx != lastTick && !landed) {
            if (lastTick >= 0) {
                float t = Mth.clamp((Util.getMillis() - spinStart) / spinMs, 0f, 1f);
                play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.35f, 1.4f + 0.4f * t);
            }
            lastTick = idx;
        }
        // 真ん中の枠
        int fx = cx - RC / 2 - 2;
        int frame = landed ? (0xFF000000 | tier(result.gear().get(spinIndex)).rgb()) : GOLD;
        g.renderOutline(fx, bandY - 1, RC + 4, 42, frame);
        g.renderOutline(fx - 1, bandY - 2, RC + 6, 44, (frame & 0x00FFFFFF) | 0x60000000);
        g.fill(cx - 3, bandY - 6, cx + 4, bandY - 4, GOLD);
        g.fill(cx - 1, bandY - 4, cx + 2, bandY - 2, GOLD);
        g.fill(cx - 3, bandY + 44, cx + 4, bandY + 46, GOLD);
        g.fill(cx - 1, bandY + 42, cx + 2, bandY + 44, GOLD);
        if (landed) {
            ItemStack win = result.gear().get(spinIndex);
            float a = Mth.clamp((Util.getMillis() - landedAt) / 250f, 0f, 1f);
            int alpha = (int) (a * 255) << 24;
            Component name = win.getHoverName();
            g.drawString(font, name, x + (W - font.width(name)) / 2, bandY + 56, (tier(win).rgb() & 0xFFFFFF) | Math.max(0x08000000, alpha), false);
            Component tl = Component.translatable(tier(win).translationKey());
            g.drawString(font, tl, x + (W - font.width(tl)) / 2, bandY + 68, (DIM & 0xFFFFFF) | Math.max(0x08000000, alpha), false);
        }
        Component hint = Component.translatable("skyabyss_box.screen.skip");
        g.drawString(font, hint, x + (W - font.width(hint)) / 2, y + H - 16, 0xFF6A7080, false);
    }

    private void drawCard(GuiGraphics g, int x, int y) {
        ItemStack win = result.gear().get(0);
        int col = 0xFF000000 | tier(win).rgb();
        g.fill(x + 14, y + 16, x + 72, y + 74, col);
        g.fill(x + 15, y + 17, x + 71, y + 73, 0xFF0A0D14);
        g.fill(x + 15, y + 70, x + 71, y + 73, col);
        item(g, win, x + 19, y + 21, 3f);
        List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, win));
        int ty = lines(g, List.of(win.getHoverName()), x + 82, y + 18, W - 94, y + H - 34, 0xFFFFFFFF);
        tip.remove(0);
        lines(g, tip, x + 82, ty + 3, W - 94, y + H - 34, DIM);
    }

    private void drawSummary(GuiGraphics g, int x, int y) {
        Component head = Component.translatable("skyabyss_box.screen.summary", result.gear().size());
        g.drawString(font, head, x + 10, y + 9, GOLD, false);
        g.fill(x + 10, y + 21, x + W - 10, y + 22, LINE);
        for (int i = 0; i < result.gear().size() && i < 45; i++) {
            int cx = x + 15 + (i % 9) * CELL, cy = y + 28 + (i / 9) * CELL;
            ItemStack s = result.gear().get(i);
            int col = 0xFF000000 | tier(s).rgb();
            g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, 0xFF0A0D14);
            g.fill(cx + 1, cy + 1, cx + CELL - 3, cy + CELL - 3, 0xFF1B202B);
            g.fill(cx + 1, cy + CELL - 4, cx + CELL - 3, cy + CELL - 3, col);
            item(g, s, cx + 3, cy + 3, 1.375f);
        }
    }

    private ItemStack hovered(int mx, int my) {
        int x = left(), y = top();
        if (mode == Mode.SUMMARY && result != null) {
            for (int i = 0; i < result.gear().size() && i < 45; i++) {
                int cx = x + 15 + (i % 9) * CELL, cy = y + 28 + (i / 9) * CELL;
                if (mx >= cx && mx < cx + CELL - 2 && my >= cy && my < cy + CELL - 2) {
                    return result.gear().get(i);
                }
            }
        }
        if (mode == Mode.CARD && result != null && mx >= x + 14 && mx < x + 72 && my >= y + 16 && my < y + 74) {
            return result.gear().get(0);
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- 押す
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mode == Mode.LIST && button == 0) {
            int x = left(), y = top();
            for (int i = 0; i < kinds.size(); i++) {
                int cx = x + 10 + (i % COLS) * CELL, cy = y + 28 + (i / COLS) * CELL;
                if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2) {
                    if (selected != i) {
                        selected = i;
                        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.3f);
                        rebuild();
                    }
                    return true;
                }
            }
        }
        if (mode == Mode.SPIN && button == 0) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && mode == Mode.SPIN) {
            skip();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && mode == Mode.WAIT) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        PacketDistributor.sendToServer(new BoxNetwork.Closed());
    }

    @Override
    public int[] textBounds() {
        return new int[] {left(), top(), W, H};
    }
}
