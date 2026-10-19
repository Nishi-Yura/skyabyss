package jp.revati.skyabyss.client.rebirth;

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

// K 画面の「転生」パネル (docs/design/31、見取り図は docs/review/rebirth/index.html)。
// 左: 転生の準備 (いまと転生後、代価、恩恵の 3 択、ボタン)。中: 残るもの・戻るもの・魂盤の力。右: 魂盤 (6 つの腕。次の節を足す腕を選ぶ)。
// ボタンは二度押しで送る (押しまちがえない)。Lv 50 になるまで・20 回を超えたあとは、灰色の説明だけ
public class SoulScreen extends Screen {
    static final int W = 380;
    static final int H = 184;

    static final int TITLE = 0x9FE8FF;
    static final int TEXT = 0xE6E2D6;
    static final int SOFT = 0xA9AEBC;
    static final int GOLD = 0xF2C14E;
    static final int DENY = 0xD9779A;
    static final int OK = 0x7FD6D0;
    static final int DIM = 0x5E6472;
    static final int PANEL = 0xF0141820;
    static final int PANEL_EDGE = 0xFF3A4252;
    static final int BOX = 0xFF1B212C;

    // 左の列・真ん中の列・右の列
    static final int C1 = 10;
    static final int C1W = 118;
    static final int C2 = 134;
    static final int C2W = 116;
    static final int C3 = 256;
    static final int BOARD_CX = 314;
    static final int BOARD_CY = 82;

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
        left = (width - W) / 2;
        top = (height - H) / 2;
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
            boons.add(addRenderableWidget(new BoonButton(left + C1, top + 91 + i * 22, i)));
        }
        go = addRenderableWidget(Button.builder(Component.empty(), b -> press()).bounds(left + C1, top + 158, C1W, 16).build());
        shardButton = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.rebirth.shard_btn"), b -> claimShard())
                .bounds(left + C3 + 66, top + 160, 50, 14).build());
        RpgTabs.add(this::addRenderableWidget, width, height, RpgTabs.REBIRTH);
        refresh();
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
        for (BoonButton b : boons) {
            b.visible = ready && b.index < offer.size();
            b.active = b.visible;
        }
        for (ArmButton a : arms) {
            a.active = true;
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
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + W, top + H, PANEL);
        g.renderOutline(left, top, W, H, PANEL_EDGE);
        g.renderOutline(left + 2, top + 2, W - 4, H - 4, 0xFF222834);
        // 列の箱
        box(g, C1 - 4, 20, C1W + 8, H - 24);
        box(g, C2 - 4, 20, C2W + 8, H - 24);
        box(g, C3 - 4, 20, W - C3 - 4, H - 24);
    }

    private void box(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(left + x, top + y, left + x + w, top + y + h, BOX);
        g.renderOutline(left + x, top + y, w, h, 0xFF2A3342);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + W / 2, top + 7, TITLE);
        GrowthData gd = growth();
        SoulData rd = Rebirth.data(minecraft.player);
        int n = gd.rebirths;
        long ms = System.currentTimeMillis();
        drawLeft(g, gd, n, ms);
        drawMiddle(g, gd, rd, n);
        drawBoard(g, rd, n, ms);
        drawTooltips(g, rd, mouseX, mouseY);
    }

    private void drawLeft(GuiGraphics g, GrowthData gd, int n, long ms) {
        int x = left + C1;
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.prep"), x, top + 24, GOLD, false);
        // いま
        String now = Rebirth.levelLabel(gd.level, n);
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.now", now), x, top + 35, 0xFF000000 | Rebirth.tierColor(n, ms), false);
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.count", n, RebirthRules.MAX_REBIRTHS), x, top + 45, SOFT, false);
        if (!ready()) {
            // Lv 50 になるまで、20 回を超えたあと: 灰色の説明だけ
            int y = top + 60;
            boolean maxed = n >= RebirthRules.MAX_REBIRTHS;
            y = line(g, x, y, C1W, SOFT, Component.translatable(maxed ? "gui.skyabyss.rebirth.maxed" : "gui.skyabyss.rebirth.locked", RebirthRules.MIN_LEVEL));
            y += 6;
            line(g, x, y, C1W, DIM, Component.translatable("gui.skyabyss.rebirth.altar"));
            go.visible = false;
            return;
        }
        go.visible = true;
        // 経験値のバー (Lv 50 は満ちている)
        g.fill(x, top + 56, x + C1W, top + 58, 0xFF2E7F85);
        // 転生後
        int after = n + 1;
        Component afterLabel = Component.translatable("gui.skyabyss.rebirth.after_label");
        g.drawString(font, afterLabel, x, top + 62, SOFT, false);
        int lx = x + font.width(afterLabel) + 5;
        g.drawString(font, Rebirth.levelLabel(RebirthRules.MIN_LEVEL, after) + " " + Rebirth.symbol(after), lx, top + 62, 0xFF000000 | Rebirth.tierColor(after, ms), false);
        // 代価
        boolean cost = minecraft.player.isCreative() || crystals() >= RebirthRules.COST_CRYSTALS;
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.cost", RebirthRules.COST_CRYSTALS, crystals()), x, top + 73, cost ? OK : DENY, false);
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.boon_title"), x, top + 82, SOFT, false);
    }

    private void drawMiddle(GuiGraphics g, GrowthData gd, SoulData rd, int n) {
        int x = left + C2;
        int y = top + 24;
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.kept_title"), x, y, OK, false);
        y += 11;
        y = lines(g, x, y, C2W, SOFT, "gui.skyabyss.rebirth.kept.gear", "gui.skyabyss.rebirth.kept.story", "gui.skyabyss.rebirth.kept.records");
        y = line(g, x, y, C2W, SOFT, Component.translatable("gui.skyabyss.rebirth.kept.board", rd.nodes.size(), rd.boons.size()));
        y += 3;
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.reset_title"), x, y, DENY, false);
        y += 11;
        int skillPts = jp.revati.skyabyss.rpg.skill.SkillManager.get(minecraft.player).spentPoints();
        y = line(g, x, y, C2W, SOFT, Component.translatable("gui.skyabyss.rebirth.reset.level", Math.round((1 - RebirthRules.xpScale(n + 1)) * 100)));
        y = line(g, x, y, C2W, SOFT, Component.translatable("gui.skyabyss.rebirth.reset.points", skillPts, gd.spentPoints()));
        y = line(g, x, y, C2W, SOFT, Component.translatable("gui.skyabyss.rebirth.reset.calling"));
        y += 3;
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.power_title"), x, y, GOLD, false);
        y += 11;
        RebirthRules.Effects e = Rebirth.effects(minecraft.player);
        String[] keys = {"damage", "projectile", "reduction", "cooldown", "mana", "move", "pickup", "crystal"};
        double[] values = {e.damage() * 100, e.projectile() * 100, e.reduction() * 100, e.cooldown() * 100, e.maxMana(), e.moveSpeed() * 100,
                e.pickup(), e.crystal() * 100};
        int shown = 0;
        for (int i = 0; i < keys.length && y < top + H - 12; i++) {
            if (fx(g, x, y, keys[i], values[i], i == 4 ? 0 : 1)) {
                y += 9;
                shown++;
            }
        }
        if (shown == 0) {
            g.drawString(font, Component.translatable("gui.skyabyss.rebirth.power_none"), x, y, DIM, false);
        }
    }

    private boolean fx(GuiGraphics g, int x, int y, String key, double value, int decimals) {
        if (value <= 0) {
            return false;
        }
        String v = decimals == 0 ? String.valueOf(Math.round(value)) : String.format("%.1f", value);
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.fx." + key, v), x, y, TEXT, false);
        return true;
    }

    private int lines(GuiGraphics g, int x, int y, int w, int color, String... keys) {
        for (String k : keys) {
            y = line(g, x, y, w, color, Component.translatable(k));
        }
        return y;
    }

    private int line(GuiGraphics g, int x, int y, int w, int color, Component text) {
        for (FormattedCharSequence part : font.split(text, w)) {
            g.drawString(font, part, x, y, color, false);
            y += 9;
        }
        return y;
    }

    // ---------------------------------------------------------------- 魂盤
    private void drawBoard(GuiGraphics g, SoulData rd, int n, long ms) {
        int cx = left + BOARD_CX;
        int cy = top + BOARD_CY;
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.board_title"), left + C3, top + 24, GOLD, false);
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
                g.fill(cx + (int) Math.round(dx * r), cy + (int) Math.round(dy * r), cx + (int) Math.round(dx * r) + 1, cy + (int) Math.round(dy * r) + 1, 0xFF2E3644);
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
        g.drawString(font, Component.translatable("gui.skyabyss.rebirth.shards", rd.shards, RebirthRules.SHARDS_PER_NODE), left + C3, top + 164, rd.shards > 0 ? TEXT : SOFT, false);
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
        if (shardButton.isHovered() || (mouseX >= left + C3 && mouseX < left + C3 + 64 && mouseY >= top + 160 && mouseY < top + 174)) {
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
            g.fill(getX(), getY(), getX() + 18, getY() + 18, sel ? 0xFF26303D : isHoveredOrFocused() ? 0xFF1F2733 : 0xFF141922);
            g.renderOutline(getX(), getY(), 18, 18, sel ? 0xFFF2C14E : isHoveredOrFocused() ? 0xFFE6E2D6 : color & 0xFF8A8A8A | 0xFF303030);
            Component name = getMessage();
            g.drawString(font, name, getX() + (18 - font.width(name)) / 2, getY() + 5, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    // 恩恵の札 (3 枚)。選んでいる札は金の枠
    final class BoonButton extends AbstractButton {
        final int index;

        BoonButton(int x, int y, int index) {
            super(x, y, C1W, 21, Component.empty());
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
            g.fill(getX(), getY(), getX() + width, getY() + height, sel ? 0xFF2A3342 : isHoveredOrFocused() ? 0xFF222A37 : 0xFF161B24);
            g.renderOutline(getX(), getY(), width, height, sel ? 0xFFF2C14E : 0xFF303846);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(boon.nameKey()).getString(), width - 6), getX() + 4, getY() + 3, sel ? GOLD : TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(boon.descKey()).getString(), width - 6), getX() + 4, getY() + 12, SOFT, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
