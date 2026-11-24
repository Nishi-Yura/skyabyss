package jp.revati.skyabyss.client.box;

import java.util.ArrayList;
import java.util.List;
import jp.revati.skyabyss.item.GearTier;
import jp.revati.skyabyss.item.ModGear;
import jp.revati.skyabyss.loot.box.BoxKind;
import jp.revati.skyabyss.loot.box.BoxNetwork;
import jp.revati.skyabyss.loot.box.LootBoxSetup;
import jp.revati.skyabyss.loot.box.LootBoxes;
import net.minecraft.Util;
import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.PickStage;
import jp.revati.skyabyss.client.ui.ShopStage;
import jp.revati.skyabyss.client.ui.UiFit;
import net.minecraft.client.gui.GuiGraphics;
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
import jp.revati.skyabyss.client.ui.Paper;

// 封切り師の画面 (docs/design/52 の 6 章)。
//   一覧: 持っている箱 (種類・帯・数) から選び「開ける」「まとめて開ける」
//   ルーレット: 横に流れる品の帯が 3〜4 秒でゆっくり止まり、真ん中の枠に当たり (帯と当たりはサーバーが決めて送る。画面は見せるだけ)。
//               止まる前後の品は表のいちばん上の階級 (惜しい品)。品が枠を通るたびに刻み、止まると階級ごとの音
//   当たり: 大きな札 (名前・階級・特性・必要 Lv)。「もう一つ開ける」「一覧へ」
//   まとめて: 一つ 1 秒で回し、最後に当たりの一覧。Esc で終わりまで飛ばす
// 組み方 (docs/design/56 の 2.1): 一覧は店の類の「帳面と台」(client/ui/ShopStage。帳面に箱を一つ一行、右の黒い紙の台に選んだ箱と説明と釦)。
// ルーレット・当たり・まとめは選ぶ類の「三枚を扇に」(client/ui/PickStage) の舞台: 品は生成りの札で、影は階級の色 (意味の色を黒の側に)。
// 品の絵は整数倍だけ (帯は 2 倍、札は 4 倍)
public class BoxScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final int W = Paper.STAGE_W, H = Paper.STAGE_H;
    // ルーレットの帯 (舞台の横いっぱい) と品の札
    static final int BAND_X0 = 14, BAND_X1 = 394, BAND_Y = 72;
    static final int RC = Golden.S34;
    static final int ROW = Golden.ROW;
    // まとめの札
    static final int SUM_COLS = 12, SUM_CELL = 30, SUM_TILE = 26, SUM_Y = 38;
    // 当たりの札 (左に傾けた生成りの大きな札)
    static final int CARD_CX = 84, CARD_CY = 118;
    static final int TEXT = 0xFFECE4D6, DIM = 0xFF9A929C;
    static final float SINGLE_MS = 3800f, BATCH_MS = 1100f;

    enum Mode {
        LIST, WAIT, SPIN, CARD, SUMMARY
    }

    private final UiFit fit = new UiFit();
    private List<String> kinds = new ArrayList<>();
    private List<Integer> counts = new ArrayList<>();
    private int selected;
    private int scroll;
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

    @Override
    protected void init() {
        fit.fit(width, height, W, H, 3f);
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- ボタン
    private void rebuild() {
        clearWidgets();
        int fy = PickStage.H - 20;
        switch (mode) {
            case LIST -> {
                String k = selectedKind();
                if (k != null) {
                    int n = countOf(k);
                    // 右の黒い紙の下 (台の説明の下)
                    addRenderableWidget(Paper.tag(Component.translatable("skyabyss_box.screen.open"), b -> request(k, 1))
                            .bounds(ShopStage.RIGHT_X, 180, ShopStage.RIGHT_W, Golden.BTN).primary().build());
                    if (n > 1) {
                        int all = Math.min(n, LootBoxes.MAX_BATCH);
                        addRenderableWidget(Paper.tag(Component.translatable("skyabyss_box.screen.open_all", all), b -> request(k, all))
                                .bounds(ShopStage.RIGHT_X, 197, ShopStage.RIGHT_W, Golden.BTN).build());
                    }
                }
            }
            case CARD -> {
                String k = result.kind();
                if (countOf(k) > 0) {
                    addRenderableWidget(Paper.tag(Component.translatable("skyabyss_box.screen.again"), b -> request(k, 1))
                            .bounds(W / 2 - Golden.S89 - Golden.S3, fy, Golden.S89, Golden.BTN).primary().build());
                }
                addRenderableWidget(Paper.tag(Component.translatable("skyabyss_box.screen.back"), b -> toList())
                        .bounds(W / 2 + Golden.S3, fy, Golden.S89, Golden.BTN).build());
            }
            case SUMMARY -> addRenderableWidget(Paper.tag(Component.translatable("skyabyss_box.screen.back"), b -> toList())
                    .bounds(W / 2 - Golden.S89 / 2, fy, Golden.S89, Golden.BTN).build());
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

    // 撮影用: 見本の箱で開く (which = list / spin / card / summary)
    public static Screen demo(String which) {
        List<String> kinds = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < Math.min(9, BoxKind.ALL.size()); i++) {
            kinds.add(BoxKind.ALL.get(i).id());
            counts.add(1 + i * 2);
        }
        BoxScreen s = new BoxScreen(new BoxNetwork.View(kinds, counts));
        if (which.equals("list")) {
            return s;
        }
        BoxKind k = BoxKind.ALL.get(0);
        List<Item> pool = LootBoxes.pool(k);
        List<ItemStack> gear = new ArrayList<>();
        int n = which.equals("summary") ? 20 : 1;
        for (int i = 0; i < n && !pool.isEmpty(); i++) {
            gear.add(new ItemStack(pool.get(i % pool.size())));
        }
        List<List<ItemStack>> reels = new ArrayList<>();
        for (int r = 0; r < n; r++) {
            List<ItemStack> reel = new ArrayList<>();
            for (int i = 0; i < LootBoxes.REEL && !pool.isEmpty(); i++) {
                reel.add(new ItemStack(pool.get((i * 7 + r) % pool.size())));
            }
            reels.add(reel);
        }
        return s.demo(new BoxNetwork.Result(k.id(), gear, reels), which.equals("spin") ? Mode.SPIN : which.equals("card") ? Mode.CARD : Mode.SUMMARY);
    }

    // 撮影用: 見本の結果で当たりの札・まとめ・ルーレットを開く
    BoxScreen demo(BoxNetwork.Result r, Mode m) {
        result = r;
        spinIndex = 0;
        mode = m;
        if (m == Mode.SPIN) {
            startSpin();
        }
        return this;
    }

    // ---------------------------------------------------------------- 描く
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, W, H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mx = (int) Math.floor(fit.x(screenX));
        int my = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        ItemStack hover = ItemStack.EMPTY;
        switch (mode) {
            case LIST, WAIT -> hover = drawList(g, mx, my);
            case SPIN -> drawSpin(g);
            case CARD -> hover = drawCard(g, mx, my);
            case SUMMARY -> hover = drawSummary(g, mx, my);
        }
        for (var r : renderables) {
            r.render(g, mx, my, partialTick);
        }
        fit.end(g);
        if (!hover.isEmpty()) {
            g.renderTooltip(font, hover, screenX, screenY);
        }
    }

    // 品の絵を整数倍で (k = 1, 2, 3, 4)
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

    // 文の行 (行 11)。maxY を越える行は描かない。描いた下端を返す
    private int lines(GuiGraphics g, List<Component> lines, int x, int y, int w, int maxY, int color) {
        for (Component c : lines) {
            for (FormattedCharSequence s : jp.revati.skyabyss.client.ui.JpWrap.split(font, c, w)) {
                if (y + 9 > maxY) {
                    return y;
                }
                g.drawString(font, s, x, y, color, false);
                y += 11;
            }
        }
        return y;
    }

    // 一覧 (帳面と台)。台の品に指があればその品を返す
    private ItemStack drawList(GuiGraphics g, int mx, int my) {
        ShopStage.backdrop(g, fit, width, height);
        ShopStage.title(g, font, title);
        scroll = Mth.clamp(scroll, 0, Math.max(0, kinds.size() - ShopStage.ROWS));
        if (kinds.isEmpty()) {
            lines(g, List.of(Component.translatable("skyabyss_box.screen.none")), ShopStage.LIST_X + 8, ShopStage.LIST_Y + 60, ShopStage.LIST_W - 16, 190, Paper.INK_SOFT);
        }
        for (int i = 0; i < ShopStage.ROWS && i + scroll < kinds.size(); i++) {
            int k = i + scroll;
            boolean hot = k == selected;
            if (hot) {
                ShopStage.hot(g, i);
            }
            int y = ShopStage.rowY(i);
            ItemStack s = boxStack(kinds.get(k));
            item(g, s, ShopStage.LIST_X + 2, y + 4, 1);
            String n = "×" + counts.get(k);
            int nw = font.width(n);
            int right = ShopStage.LIST_X + ShopStage.LIST_W - 4;
            Component name = Component.literal(s.getHoverName().getString());
            int tx = ShopStage.LIST_X + 22;
            int tw = Math.min(font.width(name), right - nw - 10 - tx);
            FitText.draw(g, font, name, tx, y + 8, tw, hot ? ShopStage.ON_PINK : Paper.INK, false);
            ShopStage.leader(g, tx + tw + 4, right - nw - 3, y + 14, hot);
            g.drawString(font, n, right - nw, y + 8, hot ? ShopStage.ON_PINK : Paper.INK_HEAD, false);
        }
        ShopStage.scroll(g, scroll, kinds.size());
        if (mode == Mode.WAIT) {
            ShopStage.hint(g, font, Component.translatable("skyabyss_box.screen.cutting"));
        }
        // 右: 選んだ箱を台に、名前・数・説明
        String k = selectedKind();
        if (k == null) {
            return ItemStack.EMPTY;
        }
        ItemStack s = boxStack(k);
        boolean over = ShopStage.plinth(g, s, mx, my);
        FitText.draw(g, font, Component.literal(s.getHoverName().getString()), ShopStage.RIGHT_X, 114, ShopStage.RIGHT_W, 0xFFF6EA, false);
        g.drawString(font, Component.translatable("skyabyss_box.screen.count", countOf(k)), ShopStage.RIGHT_X, 126, Paper.ON_BLACK_HI, false);
        List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, s));
        tip.remove(0);
        tip.removeIf(c -> c.getString().isBlank());
        lines(g, tip, ShopStage.RIGHT_X, 140, ShopStage.RIGHT_W, 176, Paper.ON_BLACK_SOFT);
        return over ? s : ItemStack.EMPTY;
    }

    private void drawSpin(GuiGraphics g) {
        PickStage.backdrop(g, fit, width, height);
        ItemStack box = boxStack(result.kind());
        PickStage.title(g, font, box.getHoverName(), result.gear().size() > 1 ? Component.literal((spinIndex + 1) + " / " + result.gear().size()) : null);
        List<ItemStack> reel = result.reels().get(spinIndex);
        int bandH = RC + Golden.S8;
        int bandY = BAND_Y;
        int cx = W / 2;
        // 帯の地 (黒の上に一段沈んだ黒)
        g.fill(BAND_X0, bandY, BAND_X1, bandY + bandH, Paper.BLACK_LO);
        float off = offset();
        jp.revati.skyabyss.client.ui.TextClip.clipPosed(g, BAND_X0 + 1, bandY + 1, BAND_X1 - BAND_X0 - 2, bandH - 2);
        for (int i = 0; i < reel.size(); i++) {
            float ix = cx + i * RC - off - RC / 2f;
            if (ix < BAND_X0 - RC || ix > BAND_X1) {
                continue;
            }
            int ixi = Math.round(ix);
            ItemStack s = reel.get(i);
            int col = 0xFF000000 | tier(s).rgb();
            // 品の札 (生成り。下の端に階級の色)
            g.fill(ixi + 2, bandY + 4, ixi + RC - 2, bandY + bandH - 4, jp.revati.skyabyss.client.ui.Plate.card(Paper.CREAM));
            g.fill(ixi + 2, bandY + bandH - 7, ixi + RC - 2, bandY + bandH - 4, col);
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
        // 真ん中の枠 (止まると階級の色) と上下の桃色の菱
        int fx = cx - RC / 2 - 2;
        int frame = landed ? (0xFF000000 | tier(result.gear().get(spinIndex)).rgb()) : Paper.PINK;
        g.renderOutline(fx, bandY + 1, RC + 4, bandH - 2, frame);
        g.renderOutline(fx - 1, bandY, RC + 6, bandH, frame);
        Paper.pin(g, cx, bandY - 4, 3, true);
        Paper.pin(g, cx, bandY + bandH + 4, 3, true);
        if (landed) {
            ItemStack win = result.gear().get(spinIndex);
            Component name = win.getHoverName();
            int ny = bandY + bandH + 14;
            FitText.drawCentered(g, font, name, cx, ny, W - 40, 0xFF000000 | tier(win).rgb(), false);
            FitText.drawCentered(g, font, Component.translatable(tier(win).translationKey()), cx, ny + 12, W - 40, DIM, false);
        }
        PickStage.hint(g, font, Component.translatable("skyabyss_box.screen.skip"));
    }

    // 当たりの札。品に指があればその品を返す
    private ItemStack drawCard(GuiGraphics g, int mx, int my) {
        PickStage.backdrop(g, fit, width, height);
        ItemStack win = result.gear().get(0);
        int col = 0xFF000000 | tier(win).rgb();
        // 題は当たりの名前 (階級の色。黒の上)
        Paper.pin(g, 14, 13, 3, true);
        FitText.draw(g, font, win.getHoverName(), 22, 9, 360, col, false);
        FitText.draw(g, font, Component.translatable(tier(win).translationKey()), 22, 21, 200, Paper.ON_BLACK_SOFT, false);
        // 左に傾けた大きな生成りの札 (影は階級の色) に、品を 4 倍で
        Paper.cut(g, "box_card", Paper.rotRect(CARD_CX, CARD_CY, PickStage.CARD_W, PickStage.CARD_H, -4), Paper.CREAM, col, 3, 4);
        item(g, win, CARD_CX - 32, CARD_CY - 52, 4);
        FitText.drawCentered(g, font, Component.literal(win.getHoverName().getString()), CARD_CX, CARD_CY + 26, PickStage.CARD_W - 16, Paper.INK, false);
        FitText.drawCentered(g, font, Component.literal(Component.translatable(tier(win).translationKey()).getString()), CARD_CX, CARD_CY + 40,
                PickStage.CARD_W - 16, Paper.INK_SOFT, false);
        // 右の黒い紙: 品の説明
        List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, win));
        tip.remove(0);
        lines(g, tip, 156, 40, 236, 196, DIM);
        boolean over = mx >= CARD_CX - 34 && mx < CARD_CX + 34 && my >= CARD_CY - 54 && my < CARD_CY + 14;
        return over ? win : ItemStack.EMPTY;
    }

    private int maxSummary() {
        return SUM_COLS * ((196 - SUM_Y) / SUM_CELL);
    }

    private int sumX(int i) {
        return (W - SUM_COLS * SUM_CELL) / 2 + (i % SUM_COLS) * SUM_CELL + (SUM_CELL - SUM_TILE) / 2;
    }

    private int sumY(int i) {
        return SUM_Y + (i / SUM_COLS) * SUM_CELL;
    }

    // まとめ: 開けた品の札 (生成り、影は階級の色)。指の下の品を返す
    private ItemStack drawSummary(GuiGraphics g, int mx, int my) {
        PickStage.backdrop(g, fit, width, height);
        PickStage.title(g, font, Component.translatable("skyabyss_box.screen.summary", result.gear().size()), null);
        ItemStack hover = ItemStack.EMPTY;
        for (int i = 0; i < result.gear().size() && i < maxSummary(); i++) {
            int x = sumX(i);
            int y = sumY(i);
            ItemStack s = result.gear().get(i);
            boolean on = mx >= x && mx < x + SUM_TILE && my >= y && my < y + SUM_TILE;
            float ang = ((i * 5) % 7 - 3) * 1.2f;
            Paper.cut(g, "box_sum" + i, Paper.rotRect(x + SUM_TILE / 2f, y + SUM_TILE / 2f - (on ? 2 : 0), SUM_TILE, SUM_TILE, ang), on ? Paper.PINK : Paper.CREAM,
                    0xFF000000 | tier(s).rgb(), 2, on ? 5 : 3);
            item(g, s, x + 5, y + 5 - (on ? 2 : 0), 1);
            if (on) {
                hover = s;
            }
        }
        return hover;
    }

    // ---------------------------------------------------------------- 押す
    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        if (mode == Mode.LIST && button == 0) {
            int r = ShopStage.rowAt(mx, my);
            if (r >= 0 && r + scroll < kinds.size()) {
                if (selected != r + scroll) {
                    selected = r + scroll;
                    play(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.3f);
                    rebuild();
                }
                return true;
            }
        }
        if (mode == Mode.SPIN && button == 0) {
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double screenX, double screenY, int button) {
        return super.mouseReleased(fit.x(screenX), fit.y(screenY), button);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double dx, double dy) {
        if (mode == Mode.LIST && dy != 0) {
            scroll -= (int) Math.signum(dy);
            return true;
        }
        return super.mouseScrolled(screenX, screenY, dx, dy);
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
        fit.fit(width, height, W, H, 3f);
        return fit.screenRect(0, 0, W, H);
    }
}
