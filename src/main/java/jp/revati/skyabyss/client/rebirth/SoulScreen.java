package jp.revati.skyabyss.client.rebirth;

import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.LedgerStage;
import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.rebirth.Boon;
import jp.revati.skyabyss.rebirth.Rebirth;
import jp.revati.skyabyss.rebirth.RebirthActionPayload;
import jp.revati.skyabyss.rebirth.SoulData;
import jp.revati.skyabyss.rebirth.RebirthRules;
import jp.revati.skyabyss.rebirth.SoulArm;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.client.skill.RpgTabs;
import jp.revati.skyabyss.rpg.skill.SkillManager;
import jp.revati.skyabyss.titan.TitanBacks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import jp.revati.skyabyss.client.ui.Paper;

// K 画面の「転生」パネル (docs/design/31、見取り図は docs/review/rebirth/index.html)。
// 組み方は帳の類の「黒に書いて、目録は帯に」(client/ui/LedgerStage。docs/design/56 の 2.1): 記録・技・転生・旅のつまみはスキルの木と同じ左上、その右に題。
// 左の黒い紙: 転生の準備 (いまと転生後、代価) と、その右に魂盤 (6 つの腕。次の節を足す腕を選ぶ) か、残るもの・戻るもの・魂盤の力 (送れる)。
// 右の生成りの帯: 恩恵の 3 択 (選んだ札は桃色) と転生の釦。左下の山の上に頁のつまみ (魂盤 / 残るもの) と欠片の釦。
// 転生の釦を一度押す (確かめ待ち) と、頁は「残るもの」になる。
// ボタンは二度押しで送る (押しまちがえない)。Lv 50 になるまで・20 回を超えたあとは、灰色の説明だけ
public class SoulScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final int W = LedgerStage.W;
    static final int H = LedgerStage.H;
    // 準備の列 (黒い紙の左) と、魂盤・残るものの列 (黒い紙の右)
    static final Golden.Box LEFT = new Golden.Box(LedgerStage.PAGE_X - 4, LedgerStage.K_BODY_Y + 2, 112, 158);
    static final Golden.Box RIGHT = new Golden.Box(134, LedgerStage.K_BODY_Y, 124, 162);
    static final int ROW = Golden.ROW;

    static final int TEXT = Golden.TEXT;
    static final int SOFT = Paper.ON_BLACK_SOFT;
    static final int GOLD = Paper.ON_BLACK_PINK;
    static final int DENY = Golden.DENY;
    static final int OK = Paper.ON_BLACK_HI;
    static final int DIM = Paper.ON_BLACK_DIM;

    // 準備の列の字の左と幅
    static final int C1 = LEFT.x();
    static final int C1W = LEFT.w();
    // 恩恵の札 (右の帯。高さ 26 = 二行) と転生の釦
    static final int BOON_X = LedgerStage.INDEX_X - 2;
    static final int BOON_W = LedgerStage.INDEX_W - 4;
    static final int BOON_H = 2 * ROW;
    static final int BOON_Y = 30;
    static final int GO_Y = 198;
    // 魂盤の真ん中
    static final int BOARD_CX = RIGHT.cx();
    static final int BOARD_CY = RIGHT.y() + ROW + Golden.S5 + 56;
    // 頁 (0 = 魂盤、1 = 残るもの・戻るもの・力) と、そのつまみ (左下の山の上)
    static final int PAGE_W = Golden.S55;
    private static int page;
    private final jp.revati.skyabyss.client.ui.ScrollPanel terms = new jp.revati.skyabyss.client.ui.ScrollPanel(ROW * 3);

    private int left;
    private int top;
    private int selArm;
    private int selBoon;
    private int armed;
    private final List<ArmButton> arms = new ArrayList<>();
    private final List<BoonButton> boons = new ArrayList<>();
    private Button go;
    private Button shardButton;
    private List<Boon> offer = List.of();
    private int offerFor = -1;

    public SoulScreen() {
        super(Component.translatable("gui.skyabyss.rebirth.title"));
    }

    @Override
    protected void init() {
        fit.fit(width, height, W, H, 3f);
        left = 0;
        top = 0;
        terms.setViewport(RIGHT.x() + 2, RIGHT.y() + 2, RIGHT.w() - 4, RIGHT.h() - 4);
        arms.clear();
        boons.clear();
        SoulData d = Rebirth.data(minecraft.player);
        // 次の節の腕: いちばん節の少ない腕を、はじめの選びにする
        int best = Integer.MAX_VALUE;
        for (SoulArm a : SoulArm.values()) {
            if (d.nodesOf(a) < best) {
                best = d.nodesOf(a);
                selArm = a.ordinal();
            }
        }
        for (SoulArm a : SoulArm.values()) {
            double ang = Math.toRadians(-90 + 60 * a.ordinal());
            int x = left + BOARD_CX + (int) Math.round(Math.cos(ang) * 47) - 9;
            int y = top + BOARD_CY + (int) Math.round(Math.sin(ang) * 47) - 9;
            arms.add(addRenderableWidget(new ArmButton(x, y, a)));
        }
        for (int i = 0; i < 3; i++) {
            boons.add(addRenderableWidget(new BoonButton(BOON_X, BOON_Y + i * (BOON_H + Golden.S5), i)));
        }
        go = addRenderableWidget(Paper.tag(Component.empty(), b -> press()).bounds(BOON_X, GO_Y, BOON_W, Golden.BTN).primary().build());
        shardButton = addRenderableWidget(Paper.tag(Component.translatable("gui.skyabyss.rebirth.shard_btn"), b -> claimShard())
                .bounds(RIGHT.x() + 4, LedgerStage.FOOT_Y + 4, Golden.S55, Golden.BTN).build());
        RpgTabs.addHead(this::addRenderableWidget, LedgerStage.K_HEAD, RpgTabs.REBIRTH, true);
        refresh();
    }

    // 撮影用: 頁を「残るもの」にして開く
    public SoulScreen termsPage() {
        page = 1;
        return this;
    }

    private GrowthData growth() {
        return Growth.get(minecraft.player);
    }

    private boolean ready() {
        GrowthData g = growth();
        return g.level >= RebirthRules.MIN_LEVEL && g.rebirths < RebirthRules.MAX_REBIRTHS;
    }

    private int crystals() {
        return SkillManager.countCrystals(minecraft.player);
    }

    private boolean inBack() {
        return TitanBacks.isBack(minecraft.player.level().dimension());
    }

    // 画面が出している恩恵の候補 (サーバーと同じ決め方。回数が変わったら引き直す)
    private void refreshOffer() {
        int n = growth().rebirths;
        if (offerFor != n) {
            offerFor = n;
            offer = n >= RebirthRules.MAX_REBIRTHS ? List.of() : Rebirth.offer(minecraft.player);
            selBoon = 0;
            armed = 0;
        }
    }

    private void refresh() {
        refreshOffer();
        boolean ready = ready();
        boolean cost = minecraft.player.isCreative() || crystals() >= RebirthRules.COST_CRYSTALS;
        boolean place = minecraft.player.isCreative() || inBack();
        go.active = ready && cost && place && !offer.isEmpty();
        go.setMessage(Component.translatable(armed > 0 ? "gui.skyabyss.rebirth.confirm" : !place && ready && cost ? "gui.skyabyss.rebirth.go_hub" : "gui.skyabyss.rebirth.button",
                RebirthRules.COST_CRYSTALS));
        SoulData d = Rebirth.data(minecraft.player);
        shardButton.active = d.canClaimShardNode();
        shardButton.visible = page == 0;
        for (BoonButton b : boons) {
            b.visible = ready && b.index < offer.size();
            b.active = b.visible;
        }
        for (ArmButton a : arms) {
            a.active = true;
            a.visible = page == 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (armed > 0 && --armed == 0) {
            refresh();
        }
        if (offerFor != growth().rebirths) {
            refresh();
        }
    }

    private void press() {
        if (armed <= 0) {
            armed = 80;
            page = 1;
            refresh();
            return;
        }
        armed = 0;
        Boon boon = selBoon < offer.size() ? offer.get(selBoon) : null;
        if (boon != null) {
            PacketDistributor.sendToServer(new RebirthActionPayload(RebirthActionPayload.REBIRTH, SoulArm.values()[selArm].id, boon.id));
        }
        refresh();
    }

    private void claimShard() {
        PacketDistributor.sendToServer(new RebirthActionPayload(RebirthActionPayload.SHARD_NODE, SoulArm.values()[selArm].id, ""));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (RpgTabs.keyPressed(keyCode, scanCode, RpgTabs.REBIRTH)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- 描く
    // 舞台は窓に合わせて一様に大きく描く (描くのと当たりは同じ変換: fit)
    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, W, H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        renderPanel(g);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        renderBody(g, mouseX, mouseY, partialTick);
        fit.end(g);
    }

    // 頁のつまみの左端 (左下の山の上)
    private static int pageX(int i) {
        return LedgerStage.PAGE_X + i * (PAGE_W + Golden.S3);
    }

    private static final int PAGE_TAB_Y = LedgerStage.FOOT_Y + 4;

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        int ty = PAGE_TAB_Y;
        if (button == 0 && my >= ty && my < ty + Golden.BTN) {
            for (int i = 0; i < 2; i++) {
                if (mx >= pageX(i) && mx < pageX(i) + PAGE_W) {
                    page = i;
                    refresh();
                    return true;
                }
            }
        }
        if (page == 1 && terms.mouseClicked(mx, my, button)) {
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double screenX, double screenY, int button, double dx, double dy) {
        if (page == 1 && terms.mouseDragged(fit.x(screenX), fit.y(screenY), button, dx / fit.scale(), dy / fit.scale())) {
            return true;
        }
        return super.mouseDragged(fit.x(screenX), fit.y(screenY), button, dx / fit.scale(), dy / fit.scale());
    }

    @Override
    public boolean mouseReleased(double screenX, double screenY, int button) {
        terms.mouseReleased(fit.x(screenX), fit.y(screenY), button);
        return super.mouseReleased(fit.x(screenX), fit.y(screenY), button);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double dx, double dy) {
        if (page == 1 && terms.mouseScrolled(fit.x(screenX), fit.y(screenY), dy)) {
            return true;
        }
        return super.mouseScrolled(fit.x(screenX), fit.y(screenY), dx, dy);
    }

    private void renderPanel(GuiGraphics g) {
        LedgerStage.backdrop(g, fit, width, height);
        LedgerStage.kTitle(g, font, title, LedgerStage.K_HEAD.x() + RpgTabs.headWidth(RpgTabs.REBIRTH));
        // 準備の列と魂盤のあいだの点線
        for (int y = RIGHT.y() + 4; y < RIGHT.bottom() - 4; y += 3) {
            g.fill(RIGHT.x() - 3, y, RIGHT.x() - 2, y + 1, Paper.PINK_LO);
        }
    }

    private void renderBody(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 頁のつまみ
        int ty = PAGE_TAB_Y;
        String[] keys = {"gui.skyabyss.rebirth.page.board", "gui.skyabyss.rebirth.page.terms"};
        for (int i = 0; i < 2; i++) {
            boolean hover = mouseX >= pageX(i) && mouseX < pageX(i) + PAGE_W && mouseY >= ty && mouseY < ty + Golden.BTN;
            Paper.tab(g, font, pageX(i), ty, PAGE_W, Golden.BTN, Component.translatable(keys[i]), page == i, hover);
        }
        GrowthData gd = growth();
        SoulData rd = Rebirth.data(minecraft.player);
        int n = gd.rebirths;
        long ms = System.currentTimeMillis();
        drawLeft(g, gd, n, ms);
        drawStrip(g);
        if (page == 0) {
            drawBoard(g, rd, n, ms);
        } else {
            drawMiddle(g, gd, rd, n);
        }
        drawTooltips(g, rd, mouseX, mouseY);
    }

    private void drawLeft(GuiGraphics g, GrowthData gd, int n, long ms) {
        int x = C1;
        int y = LEFT.y() + 2;
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.prep"), x, Golden.textY(y, ROW), GOLD, false);
        y += ROW;
        // いまと回数 (一行)
        String now = Rebirth.levelLabel(gd.level, n);
        Component nowLine = Component.translatable("gui.skyabyss.rebirth.now", now).copy().append("  ")
                .append(Component.translatable("gui.skyabyss.rebirth.count", n, RebirthRules.MAX_REBIRTHS).withColor(SOFT));
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, nowLine, x, Golden.textY(y, ROW), C1W, 0xFF000000 | Rebirth.tierColor(n, ms), false);
        y += ROW;
        if (!ready()) {
            // Lv 50 になるまで、20 回を超えたあと: 灰色の説明だけ
            boolean maxed = n >= RebirthRules.MAX_REBIRTHS;
            line(g, x, y + Golden.S5, C1W, SOFT, Component.translatable(maxed ? "gui.skyabyss.rebirth.maxed" : "gui.skyabyss.rebirth.locked", RebirthRules.MIN_LEVEL));
            go.visible = false;
            return;
        }
        go.visible = true;
        // 経験値の帯 (Lv 50 は満ちている)
        g.fill(x, y + 1, x + C1W, y + 3, 0xFFE45A9C);
        y += Golden.S5;
        // 転生後
        int after = n + 1;
        Component afterLabel = Component.translatable("gui.skyabyss.rebirth.after_label");
        g.drawString(font, afterLabel, x, Golden.textY(y, ROW), SOFT, false);
        g.drawString(font, Rebirth.levelLabel(RebirthRules.MIN_LEVEL, after), x + font.width(afterLabel) + 5, Golden.textY(y, ROW), 0xFF000000 | Rebirth.tierColor(after, ms), false);
        y += ROW;
        // 代価
        boolean cost = minecraft.player.isCreative() || crystals() >= RebirthRules.COST_CRYSTALS;
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.rebirth.cost", RebirthRules.COST_CRYSTALS, crystals()), x, Golden.textY(y, ROW), C1W,
                cost ? OK : DENY, false);
        y += ROW;
    }

    // 右の帯: 恩恵の 3 択の見出し (札と転生の釦は widget)。まだ転生できないときは祭壇の手引き
    private void drawStrip(GuiGraphics g) {
        LedgerStage.indexHead(g, font, Component.translatable("gui.skyabyss.rebirth.boon_title"));
        if (!ready()) {
            int y = BOON_Y;
            for (FormattedCharSequence part : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.rebirth.altar"), BOON_W)) {
                g.drawString(font, part, BOON_X + 2, y, Paper.INK_SOFT, false);
                y += 11;
            }
        }
    }

    // 残るもの・戻るもの・魂盤の力 (右の列の二頁目。行 13、送れる)
    private void drawMiddle(GuiGraphics g, GrowthData gd, SoulData rd, int n) {
        int x = terms.x();
        int w = terms.width();
        terms.begin(g);
        int y = terms.top();
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.kept_title"), x, Golden.textY(y, ROW), OK, false);
        y += ROW;
        y = lines(g, x, y, w, SOFT, "gui.skyabyss.rebirth.kept.gear", "gui.skyabyss.rebirth.kept.story", "gui.skyabyss.rebirth.kept.records");
        y = line(g, x, y, w, SOFT, Component.translatable("gui.skyabyss.rebirth.kept.board", rd.nodes.size(), rd.boons.size()));
        y += Golden.S5;
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.reset_title"), x, Golden.textY(y, ROW), DENY, false);
        y += ROW;
        int skillPts = jp.revati.skyabyss.rpg.skill.SkillManager.get(minecraft.player).spentPoints();
        y = line(g, x, y, w, SOFT, Component.translatable("gui.skyabyss.rebirth.reset.level", Math.round((1 - RebirthRules.xpScale(n + 1)) * 100)));
        y = line(g, x, y, w, SOFT, Component.translatable("gui.skyabyss.rebirth.reset.points", skillPts, gd.spentPoints()));
        y = line(g, x, y, w, SOFT, Component.translatable("gui.skyabyss.rebirth.reset.calling"));
        y += Golden.S5;
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.power_title"), x, Golden.textY(y, ROW), GOLD, false);
        y += ROW;
        RebirthRules.Effects e = Rebirth.effects(minecraft.player);
        String[] keys = {"damage", "projectile", "reduction", "cooldown", "mana", "move", "pickup", "crystal"};
        double[] values = {e.damage() * 100, e.projectile() * 100, e.reduction() * 100, e.cooldown() * 100, e.maxMana(), e.moveSpeed() * 100,
                e.pickup(), e.crystal() * 100};
        int shown = 0;
        for (int i = 0; i < keys.length; i++) {
            if (fx(g, x, y, keys[i], values[i], i == 4 ? 0 : 1)) {
                y += ROW;
                shown++;
            }
        }
        if (shown == 0) {
            g.drawString(font, Component.translatable("gui.skyabyss.rebirth.power_none"), x, Golden.textY(y, ROW), DIM, false);
            y += ROW;
        }
        terms.end(g);
        terms.setContentHeight(y - terms.top());
        terms.drawBar(g, 0xFF0E0D14, 0xAAE45A9C);
    }

    private boolean fx(GuiGraphics g, int x, int y, String key, double value, int decimals) {
        if (value <= 0) {
            return false;
        }
        String v = decimals == 0 ? String.valueOf(Math.round(value)) : String.format("%.1f", value);
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.fx." + key, v), x, Golden.textY(y, ROW), TEXT, false);
        return true;
    }

    private int lines(GuiGraphics g, int x, int y, int w, int color, String... keys) {
        for (String k : keys) {
            y = line(g, x, y, w, color, Component.translatable(k));
        }
        return y;
    }

    private int line(GuiGraphics g, int x, int y, int w, int color, Component text) {
        for (FormattedCharSequence part : jp.revati.skyabyss.client.ui.JpWrap.split(font, text, w)) {
            g.drawString(font, part, x, Golden.textY(y, ROW), color, false);
            y += ROW;
        }
        return y;
    }

    // ---------------------------------------------------------------- 魂盤
    private void drawBoard(GuiGraphics g, SoulData rd, int n, long ms) {
        int cx = left + BOARD_CX;
        int cy = top + BOARD_CY;
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.board_title"), RIGHT.x() + 4, Golden.textY(RIGHT.y() + 2, ROW), GOLD, false);
        // 中心: 転生の回数
        int tint = 0xFF000000 | Rebirth.tierColor(n, ms);
        g.fill(cx - 7, cy - 1, cx + 7, cy + 1, tint & 0x80FFFFFF | 0x40000000);
        g.fill(cx - 1, cy - 7, cx + 1, cy + 7, tint & 0x80FFFFFF | 0x40000000);
        g.fill(cx - 4, cy - 4, cx + 4, cy + 4, 0xFF10141B);
        g.renderOutline(cx - 4, cy - 4, 8, 8, tint);
        String num = String.valueOf(n);
        g.drawString(font, num, cx - font.width(num) / 2 + 1, cy - 3, tint, false);
        for (SoulArm a : SoulArm.values()) {
            double ang = Math.toRadians(-90 + 60 * a.ordinal());
            double dx = Math.cos(ang);
            double dy = Math.sin(ang);
            int color = 0xFF000000 | a.color;
            // 腕に沿った点線
            for (int r = 9; r <= 36; r += 3) {
                g.fill(cx + (int) Math.round(dx * r), cy + (int) Math.round(dy * r), cx + (int) Math.round(dx * r) + 1, cy + (int) Math.round(dy * r) + 1, 0xFF3A3644);
            }
            // 節 (転生した順に外へ。要の節は大きく)
            int k = 0;
            int count = 0;
            for (int i = 0; i < rd.nodes.size(); i++) {
                if (!a.id.equals(rd.nodes.get(i))) {
                    continue;
                }
                count++;
                if (k < 6) {
                    int r = 13 + k * 4;
                    int nx = cx + (int) Math.round(dx * r);
                    int ny = cy + (int) Math.round(dy * r);
                    boolean key = RebirthRules.isKey(i + 1);
                    int s = key ? 3 : 2;
                    g.fill(nx - s + 1, ny - s + 1, nx + s, ny + s, color);
                    if (key) {
                        g.renderOutline(nx - s, ny - s, s * 2 + 1, s * 2 + 1, 0xFFFFE9A8);
                    }
                    k++;
                }
            }
            // 次の節 (選んでいる腕に、点滅する枠)
            if (n < RebirthRules.MAX_REBIRTHS && a.ordinal() == selArm && ready()) {
                int r = 13 + Math.min(k, 6) * 4;
                int nx = cx + (int) Math.round(dx * r);
                int ny = cy + (int) Math.round(dy * r);
                int pulse = 0x60 + (int) (0x90 * (0.5 + 0.5 * Math.sin(ms / 250.0)));
                g.renderOutline(nx - 2, ny - 2, 5, 5, (pulse << 24) | 0xFFE9A8);
            }
            if (count > 6) {
                String more = "+" + (count - 6);
                int mx = cx + (int) Math.round(dx * 38) - font.width(more) / 2;
                g.drawString(font, more, mx, cy + (int) Math.round(dy * 38) - 3, color, false);
            }
        }
        // 欠片
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.shards", rd.shards, RebirthRules.SHARDS_PER_NODE), RIGHT.x() + 4,
                Golden.textY(RIGHT.bottom() - Golden.BTN, Golden.BTN), rd.shards > 0 ? TEXT : SOFT, false);
    }

    private void drawTooltips(GuiGraphics g, SoulData rd, int mouseX, int mouseY) {
        for (ArmButton a : arms) {
            if (a.isHovered()) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable(a.arm.nameKey()).withColor(a.arm.color));
                tip.add(Component.translatable(a.arm.descKey()).withColor(SOFT));
                tip.add(Component.translatable("gui.skyabyss.rebirth.arm_nodes", rd.nodesOf(a.arm)).withColor(TEXT));
                tip.add(Component.translatable("gui.skyabyss.rebirth.arm_pick").withColor(DIM));
                g.renderComponentTooltip(font, tip, mouseX, mouseY);
                return;
            }
        }
        for (BoonButton b : boons) {
            if (b.isHovered() && b.visible && b.index < offer.size()) {
                Boon boon = offer.get(b.index);
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable(boon.nameKey()).withColor(GOLD));
                tip.add(Component.translatable(boon.descKey()).withColor(SOFT));
                tip.add(Component.translatable("gui.skyabyss.rebirth.boon_have", rd.boonCount(boon)).withColor(DIM));
                g.renderComponentTooltip(font, tip, mouseX, mouseY);
                return;
            }
        }
        if (page == 0 && (shardButton.isHovered() || (mouseX >= RIGHT.x() && mouseX < RIGHT.right() && mouseY >= RIGHT.bottom() - Golden.BTN && mouseY < RIGHT.bottom()))) {
            g.renderComponentTooltip(font, List.of(Component.translatable("gui.skyabyss.rebirth.shards_desc", RebirthRules.GUIDE_PER_DAY,
                    RebirthRules.MAX_SHARD_NODES).withColor(SOFT)), mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- 部品
    // 魂盤の腕を選ぶ丸いボタン (18x18)。選んでいる腕は金の枠
    final class ArmButton extends AbstractButton {
        final SoulArm arm;

        ArmButton(int x, int y, SoulArm arm) {
            super(x, y, 18, 18, Component.translatable(arm.shortKey()));
            this.arm = arm;
        }

        @Override
        public void onPress() {
            selArm = arm.ordinal();
            armed = 0;
            refresh();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean sel = selArm == arm.ordinal();
            int color = 0xFF000000 | arm.color;
            g.fill(getX(), getY(), getX() + 18, getY() + 18, sel ? 0xFF2E2A38 : isHoveredOrFocused() ? 0xFF24202E : 0xFF16151E);
            g.renderOutline(getX(), getY(), 18, 18, sel ? 0xFFF89CD2 : isHoveredOrFocused() ? 0xFFECE8DC : color & 0xFF8A8A8A | 0xFF303030);
            Component name = getMessage();
            g.drawString(font, name, getX() + (18 - font.width(name)) / 2, getY() + 5, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    // 恩恵の札 (3 枚、右の帯の上)。生成りの紙で、選んでいる札は桃色の紙
    final class BoonButton extends AbstractButton {
        final int index;

        BoonButton(int x, int y, int index) {
            super(x, y, BOON_W, BOON_H, Component.empty());
            this.index = index;
        }

        @Override
        public void onPress() {
            selBoon = index;
            armed = 0;
            refresh();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (index >= offer.size()) {
                return;
            }
            Boon boon = offer.get(index);
            boolean sel = selBoon == index;
            boolean hot = isHoveredOrFocused();
            int x0 = getX();
            int y0 = getY();
            Paper.cut(g, "soul_boon" + index, new float[] {x0, y0 + (index % 2), x0 + width, y0 + 1 - (index % 2), x0 + width + 1, y0 + height, x0 + 1, y0 + height - 1},
                    sel ? Paper.PINK : hot ? Paper.CREAM_HI : Paper.CREAM, sel ? Paper.BLACK_LO : Paper.PINK_LO, 2, 2);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(Component.translatable(boon.nameKey()).getString()), x0 + 4,
                    Golden.textY(y0, ROW), width - 6, sel ? LedgerStage.ON_PINK : Paper.INK_HEAD, false);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(Component.translatable(boon.descKey()).getString()), x0 + 4,
                    Golden.textY(y0 + ROW, ROW), width - 6, sel ? LedgerStage.ON_PINK : Paper.INK_SOFT, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    @Override
    public int[] textBounds() {
        return fit.screenRect(0, 0, W, H);
    }
}
