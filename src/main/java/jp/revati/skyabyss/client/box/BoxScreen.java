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
import jp.revati.skyabyss.client.ui.Golden;
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
// 板は黄金比の大きい板 (356x220。client/ui/Golden。docs/design/56)。GUI の座標に等倍で置く (1280x720 の GUI 3 でちょうど入る)。
// 一覧は脇の列 (左。21 角の枠に箱の絵を等倍で 5 列)、選んだ箱の説明は主の列 (右。行 13)、釦は下の段。品の絵は整数倍だけ (帯は 2 倍、札は 3 倍)
public class BoxScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT);
    static final int W = Golden.L_W, H = Golden.L_H;
    static final Golden.Box LIST = LAYOUT.body().minor(true);
    static final Golden.Box INFO = LAYOUT.body().major(false);
    static final int CELL = Golden.S21 + Golden.S3, COLS = 5;
    static final int SUM_COLS = 13;
    static final int RC = Golden.S34;
    static final int ROW = Golden.ROW;
    static final int GOLD = 0xFFE9C46A, TEXT = 0xFFD8DCE6, DIM = 0xFFA4ACC8, LINE = 0xFF3A4252;
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

    // ---------------------------------------------------------------- ボタン (下の段)
    private void rebuild() {
        clearWidgets();
        int x = left(), y = top();
        Golden.Box foot = LAYOUT.foot();
        int fy = y + foot.y() + 4;
        switch (mode) {
            case LIST -> {
                String k = selectedKind();
                if (k != null) {
                    int n = countOf(k);
                    addRenderableWidget(Button.builder(Component.translatable("skyabyss_box.screen.open"), b -> request(k, 1))
                            .bounds(x + INFO.x(), fy, Golden.S89, Golden.BTN).build());
                    if (n > 1) {
                        int all = Math.min(n, LootBoxes.MAX_BATCH);
                        addRenderableWidget(Button.builder(Component.translatable("skyabyss_box.screen.open_all", all), b -> request(k, all))
                                .bounds(x + INFO.x() + Golden.S89 + Golden.S5, fy, Golden.S89 + Golden.S21, Golden.BTN).build());
                    }
                }
            }
            case CARD -> {
                String k = result.kind();
                if (countOf(k) > 0) {
                    addRenderableWidget(Button.builder(Component.translatable("skyabyss_box.screen.again"), b -> request(k, 1))
                            .bounds(x + W / 2 - Golden.S89 - Golden.S3, fy, Golden.S89, Golden.BTN).build());
                }
                addRenderableWidget(Button.builder(Component.translatable("skyabyss_box.screen.back"), b -> toList())
                        .bounds(x + W / 2 + Golden.S3, fy, Golden.S89, Golden.BTN).build());
            }
            case SUMMARY -> addRenderableWidget(Button.builder(Component.translatable("skyabyss_box.screen.back"), b -> toList())
                    .bounds(x + W / 2 - Golden.S89 / 2, fy, Golden.S89, Golden.BTN).build());
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
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        g.pose().popPose();
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

    // 欄 (板の中の座標 b を、板の左上 x, y からの所に)
    private static void recess(GuiGraphics g, int x, int y, Golden.Box b) {
        Golden.recess(g, new Golden.Box(x + b.x(), y + b.y(), b.w(), b.h()));
    }

    // 品の絵を整数倍で (k = 1, 2, 3)
    private void item(GuiGraphics g, ItemStack s, int x, int y, int k) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1f);
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

    // 文の行 (行 13)。maxY を越える行は描かない。描いた下端を返す
    private int lines(GuiGraphics g, List<Component> lines, int x, int y, int w, int maxY, int color) {
        for (Component c : lines) {
            for (FormattedCharSequence s : jp.revati.skyabyss.client.ui.JpWrap.split(font, c, w)) {
                if (y + ROW > maxY) {
                    return y;
                }
                g.drawString(font, s, x, Golden.textY(y, ROW), color, false);
                y += ROW;
            }
        }
        return y;
    }

    private int cellX(int x, int i) {
        return x + LIST.x() + Golden.S5 + (i % COLS) * CELL;
    }

    private int cellY(int y, int i) {
        return y + LIST.y() + Golden.S5 + (i / COLS) * CELL;
    }

    private void drawList(GuiGraphics g, int x, int y, int mx, int my) {
        Golden.Box head = LAYOUT.head();
        g.drawString(font, title, x + head.x() + Golden.S5, y + Golden.textY(head.y(), head.h()), GOLD, false);
        recess(g, x, y, LIST);
        recess(g, x, y, INFO);
        if (kinds.isEmpty()) {
            lines(g, List.of(Component.translatable("skyabyss_box.screen.none")), x + INFO.x() + Golden.S8, y + INFO.y() + Golden.S5, INFO.w() - 2 * Golden.S8, y + INFO.bottom(), DIM);
            return;
        }
        int cs = Golden.S21;
        for (int i = 0; i < kinds.size(); i++) {
            int cx = cellX(x, i), cy = cellY(y, i);
            if (cy + cs > y + LIST.bottom() - 2) {
                break;
            }
            boolean sel = i == selected;
            int col = boxColor(kinds.get(i));
            g.fill(cx, cy, cx + cs, cy + cs, sel ? col : 0xFF0A0E1E);
            g.fill(cx + 1, cy + 1, cx + cs - 1, cy + cs - 1, sel ? 0xFF262B38 : 0xFF141A34);
            g.fill(cx + 1, cy + cs - 2, cx + cs - 1, cy + cs - 1, col);
            item(g, boxStack(kinds.get(i)), cx + 2, cy + 2, 1);
            String n = String.valueOf(counts.get(i));
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font, n, cx + cs - font.width(n), cy + cs - 8, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
        // 右: 選んだ箱
        String k = selectedKind();
        int px = x + INFO.x() + Golden.S8;
        int pw = INFO.w() - 2 * Golden.S8;
        int bottom = y + INFO.bottom() - 2;
        if (k != null) {
            ItemStack s = boxStack(k);
            item(g, s, px, y + INFO.y() + Golden.S5, 2);
            int ty = lines(g, List.of(s.getHoverName()), px + 32 + Golden.S8, y + INFO.y() + Golden.S5 + 3, pw - 32 - Golden.S8, bottom, 0xFFFFFFFF);
            ty = Math.max(ty, y + INFO.y() + Golden.S5 + 32 + Golden.S3);
            List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, s));
            tip.remove(0);
            ty = lines(g, tip, px, ty, pw, bottom - ROW, DIM);
            g.drawString(font, Component.translatable("skyabyss_box.screen.count", countOf(k)), px, Golden.textY(Math.min(ty, bottom - ROW), ROW), TEXT, false);
        }
        if (mode == Mode.WAIT) {
            Component w = Component.translatable("skyabyss_box.screen.cutting");
            Golden.Box foot = LAYOUT.foot();
            g.drawString(font, w, x + foot.x() + Golden.S5, y + Golden.textY(foot.y(), foot.h()), GOLD, false);
        }
    }

    private void drawSpin(GuiGraphics g, int x, int y) {
        ItemStack box = boxStack(result.kind());
        Component head = box.getHoverName();
        Golden.Box hb = LAYOUT.head();
        int hy = y + Golden.textY(hb.y(), hb.h());
        g.drawString(font, head, x + (W - font.width(head)) / 2, hy, boxColor(result.kind()), false);
        if (result.gear().size() > 1) {
            String n = (spinIndex + 1) + " / " + result.gear().size();
            g.drawString(font, n, x + hb.right() - Golden.S5 - font.width(n), hy, DIM, false);
        }
        List<ItemStack> reel = result.reels().get(spinIndex);
        Golden.Box body = LAYOUT.body();
        int bandH = RC + Golden.S8;
        int bandY = y + body.y() + (body.h() - bandH) / 2 - Golden.S13;
        int cx = x + W / 2;
        recess(g, x, y, new Golden.Box(body.x(), bandY - y, body.w(), bandH));
        float off = offset();
        int[] clip = {x + body.x() + 1, bandY + 1, x + body.right() - 1, bandY + bandH - 1};
        g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
        for (int i = 0; i < reel.size(); i++) {
            float ix = cx + i * RC - off - RC / 2f;
            if (ix < x - RC || ix > x + W) {
                continue;
            }
            int ixi = Math.round(ix);
            ItemStack s = reel.get(i);
            int col = 0xFF000000 | tier(s).rgb();
            g.fill(ixi + 1, bandY + 4, ixi + RC - 1, bandY + bandH - 4, 0xFF141A34);
            g.fill(ixi + 1, bandY + bandH - 6, ixi + RC - 1, bandY + bandH - 4, col);
            item(g, s, ixi + 1, bandY + 4, 2);
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
        g.renderOutline(fx, bandY + 1, RC + 4, bandH - 2, frame);
        g.renderOutline(fx - 1, bandY, RC + 6, bandH, (frame & 0x00FFFFFF) | 0x60000000);
        g.fill(cx - 3, bandY - 4, cx + 4, bandY - 2, GOLD);
        g.fill(cx - 1, bandY - 2, cx + 2, bandY, GOLD);
        g.fill(cx - 3, bandY + bandH + 2, cx + 4, bandY + bandH + 4, GOLD);
        g.fill(cx - 1, bandY + bandH, cx + 2, bandY + bandH + 2, GOLD);
        if (landed) {
            ItemStack win = result.gear().get(spinIndex);
            float a = Mth.clamp((Util.getMillis() - landedAt) / 250f, 0f, 1f);
            int alpha = (int) (a * 255) << 24;
            Component name = win.getHoverName();
            int ny = bandY + bandH + Golden.S8;
            g.drawString(font, name, x + (W - font.width(name)) / 2, Golden.textY(ny, ROW), (tier(win).rgb() & 0xFFFFFF) | Math.max(0x08000000, alpha), false);
            Component tl = Component.translatable(tier(win).translationKey());
            g.drawString(font, tl, x + (W - font.width(tl)) / 2, Golden.textY(ny + ROW, ROW), (DIM & 0xFFFFFF) | Math.max(0x08000000, alpha), false);
        }
        Component hint = Component.translatable("skyabyss_box.screen.skip");
        Golden.Box foot = LAYOUT.foot();
        g.drawString(font, hint, x + (W - font.width(hint)) / 2, y + Golden.textY(foot.y(), foot.h()), 0xFF000000 | Golden.DIM, false);
    }

    private void drawCard(GuiGraphics g, int x, int y) {
        ItemStack win = result.gear().get(0);
        int col = 0xFF000000 | tier(win).rgb();
        Golden.Box body = LAYOUT.body();
        Golden.Box head = LAYOUT.head();
        g.drawString(font, win.getHoverName(), x + head.x() + Golden.S5, y + Golden.textY(head.y(), head.h()), col, false);
        // 品の絵 (3 倍) は 55 角の枠に
        int bx = x + body.x();
        int by = y + body.y();
        int box = Golden.S55;
        g.fill(bx, by, bx + box, by + box, col);
        g.fill(bx + 1, by + 1, bx + box - 1, by + box - 1, 0xFF0A0E1E);
        g.fill(bx + 1, by + box - 3, bx + box - 1, by + box - 1, col);
        item(g, win, bx + 4, by + 3, 3);
        List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, win));
        tip.remove(0);
        lines(g, tip, bx + box + Golden.S8, by, body.w() - box - Golden.S8, y + body.bottom(), DIM);
    }

    private void drawSummary(GuiGraphics g, int x, int y) {
        Golden.Box head = LAYOUT.head();
        g.drawString(font, Component.translatable("skyabyss_box.screen.summary", result.gear().size()), x + head.x() + Golden.S5, y + Golden.textY(head.y(), head.h()), GOLD, false);
        int cs = Golden.S21;
        for (int i = 0; i < result.gear().size() && i < maxSummary(); i++) {
            int cx = sumX(x, i), cy = sumY(y, i);
            ItemStack s = result.gear().get(i);
            int col = 0xFF000000 | tier(s).rgb();
            g.fill(cx, cy, cx + cs, cy + cs, 0xFF0A0E1E);
            g.fill(cx + 1, cy + 1, cx + cs - 1, cy + cs - 1, 0xFF141A34);
            g.fill(cx + 1, cy + cs - 2, cx + cs - 1, cy + cs - 1, col);
            item(g, s, cx + 2, cy + 2, 1);
        }
    }

    private int maxSummary() {
        return SUM_COLS * (LAYOUT.body().h() / CELL);
    }

    private int sumX(int x, int i) {
        return x + LAYOUT.body().x() + (LAYOUT.body().w() - SUM_COLS * CELL + Golden.S3) / 2 + (i % SUM_COLS) * CELL;
    }

    private int sumY(int y, int i) {
        return y + LAYOUT.body().y() + (i / SUM_COLS) * CELL;
    }

    private ItemStack hovered(int mx, int my) {
        int x = left(), y = top();
        int cs = Golden.S21;
        if (mode == Mode.SUMMARY && result != null) {
            for (int i = 0; i < result.gear().size() && i < maxSummary(); i++) {
                int cx = sumX(x, i), cy = sumY(y, i);
                if (mx >= cx && mx < cx + cs && my >= cy && my < cy + cs) {
                    return result.gear().get(i);
                }
            }
        }
        int bx = x + LAYOUT.body().x();
        int by = y + LAYOUT.body().y();
        if (mode == Mode.CARD && result != null && mx >= bx && mx < bx + Golden.S55 && my >= by && my < by + Golden.S55) {
            return result.gear().get(0);
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- 押す
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mode == Mode.LIST && button == 0) {
            int x = left(), y = top();
            int cs = Golden.S21;
            for (int i = 0; i < kinds.size(); i++) {
                int cx = cellX(x, i), cy = cellY(y, i);
                if (mouseX >= cx && mouseX < cx + cs && mouseY >= cy && mouseY < cy + cs) {
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
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if ((keyCode == GLFW.GLFW_KEY_ESCAPE || enter) && mode == Mode.SPIN) {
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
