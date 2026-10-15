package jp.revati.skyabyss.rpg.client;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import jp.revati.skyabyss.rpg.Rpg;
import jp.revati.skyabyss.rpg.net.GrowthAllocatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

// ステータス画面 (J キー)。力を仮に振ってから「決定」でサーバーへ送る (押しまちがえを戻せるように)。
// 背景は tools/gen_growth_assets.py で作る 2 倍密度のテクスチャ (480x368 を 240x184 で描く)
public class GrowthScreen extends Screen {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/growth/status.png");
    static final int TEX = 512;
    static final int W = 240;
    static final int H = 184;
    // ステータスの行 (gen_growth_assets.py と同じ値)
    static final int ROW_Y = 60;
    static final int ROW_H = 24;
    static final int BAR_X = 62;
    static final int BAR_Y = 27;
    static final int BAR_W = 166;

    static final int TITLE = 0x9FE8FF;
    static final int TEXT = 0xE6E2D6;
    static final int SOFT = 0xA9AEBC;
    static final int GOLD = 0xF2C14E;
    static final int PENDING = 0x9FE8FF;
    static final int LORE = 0x7FD6D0;

    private final int[] pending = new int[Stat.values().length];
    private int left;
    private int top;
    private final List<SmallButton> plus = new ArrayList<>();
    private final List<SmallButton> minus = new ArrayList<>();
    private Button confirm;
    private Button undo;

    public GrowthScreen() {
        super(Component.translatable("gui.skyabyss.growth.title"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        plus.clear();
        minus.clear();
        for (Stat stat : Stat.values()) {
            int ry = top + ROW_Y + stat.ordinal() * ROW_H;
            minus.add(addRenderableWidget(new SmallButton(left + 200, ry + 6, false, () -> change(stat, -1))));
            plus.add(addRenderableWidget(new SmallButton(left + 214, ry + 6, true, () -> change(stat, +1))));
        }
        undo = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.growth.undo"), b -> java.util.Arrays.fill(pending, 0))
                .bounds(left + 136, top + 161, 44, 16).build());
        confirm = addRenderableWidget(Button.builder(Component.translatable("gui.skyabyss.growth.confirm"), b -> send())
                .bounds(left + 184, top + 161, 46, 16).build());
        // 技の画面へのつまみ (上の縁)
        jp.revati.skyabyss.rpg.client.skill.RpgTabs.add(this::addRenderableWidget, width, height, jp.revati.skyabyss.rpg.client.skill.RpgTabs.STATUS);
        refresh();
    }

    private GrowthData data() {
        return Growth.get(minecraft.player);
    }

    private int pendingSum() {
        int sum = 0;
        for (int v : pending) {
            sum += v;
        }
        return sum;
    }

    // シフトを押しながらなら 5 ずつ
    private void change(Stat stat, int sign) {
        GrowthData d = data();
        int step = hasShiftDown() ? 5 : 1;
        int i = stat.ordinal();
        if (sign > 0) {
            int room = Math.min(d.unspentPoints() - pendingSum(), GrowthRules.STAT_CAP - d.stat(stat) - pending[i]);
            pending[i] += Math.max(0, Math.min(step, room));
        } else {
            pending[i] = Math.max(0, pending[i] - step);
        }
        refresh();
    }

    private void send() {
        if (pendingSum() > 0) {
            PacketDistributor.sendToServer(new GrowthAllocatePayload(pending.clone()));
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(Rpg.LEVEL_UP.get(), 1.6f, 0.35f));
        }
        java.util.Arrays.fill(pending, 0);
        refresh();
    }

    private void refresh() {
        GrowthData d = data();
        // 記録が変わって (同期・レベルを下げられた)、仮の振り分けが収まらなくなったら取り消す
        if (pendingSum() > d.unspentPoints()) {
            java.util.Arrays.fill(pending, 0);
        }
        int available = d.unspentPoints() - pendingSum();
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            plus.get(i).active = available > 0 && d.stat(stat) + pending[i] < GrowthRules.STAT_CAP;
            minus.get(i).active = pending[i] > 0;
        }
        confirm.active = pendingSum() > 0;
        undo.active = pendingSum() > 0;
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // J で閉じる、K で技の画面へ
        if (jp.revati.skyabyss.rpg.client.skill.RpgTabs.keyPressed(keyCode, scanCode, jp.revati.skyabyss.rpg.client.skill.RpgTabs.STATUS)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(TEXTURE, left, top, W, H, 0, 0, W * 2, H * 2, TEX, TEX);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        GrowthData d = data();
        g.drawCenteredString(font, title, left + W / 2, top + 8, TITLE);

        // レベルと経験値のバー
        boolean max = d.level >= GrowthRules.MAX_LEVEL;
        g.pose().pushPose();
        g.pose().translate(left + 12, top + 24, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, Component.translatable("gui.skyabyss.growth.hud_level", d.level), 0, 0, GOLD, true);
        g.pose().popPose();
        float fraction = max ? 1f : (float) d.xp / Math.max(1, GrowthRules.xpToNext(d.level));
        int fill = Mth.clamp(Math.round((BAR_W - 4) * fraction), 0, BAR_W - 4);
        if (fill > 0) {
            g.blit(TEXTURE, left + BAR_X + 2, top + BAR_Y + 2, fill, 2, 0, max ? 470 : 466, fill * 2, 4, TEX, TEX);
        }
        Component xpText = max ? Component.translatable("gui.skyabyss.growth.max")
                : Component.translatable("gui.skyabyss.growth.xp", d.xp, GrowthRules.xpToNext(d.level));
        g.drawString(font, xpText, left + BAR_X + BAR_W - font.width(xpText), top + BAR_Y + 9, SOFT, false);

        // 振り分けられる力
        int available = d.unspentPoints() - pendingSum();
        g.drawString(font, Component.translatable("gui.skyabyss.growth.points", available), left + 12, top + 46,
                available > 0 ? GOLD : SOFT, false);

        // ステータスの行
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            int ry = top + ROW_Y + i * ROW_H;
            g.blit(TEXTURE, left + 10, ry + 4, 16, 16, i * 32, 384, 32, 32, TEX, TEX);
            g.drawString(font, Component.translatable(stat.nameKey()), left + 32, ry + 3, TEXT, false);
            int value = d.stat(stat) + pending[i];
            String v = String.valueOf(value);
            int vx = left + 104;
            g.drawString(font, v, vx, ry + 3, pending[i] > 0 ? PENDING : TEXT, false);
            if (pending[i] > 0) {
                g.drawString(font, "(+" + pending[i] + ")", vx + font.width(v) + 3, ry + 3, PENDING, false);
            }
            g.drawString(font, effect(stat, value), left + 32, ry + 13, pending[i] > 0 ? PENDING : SOFT, false);
        }

        // 記憶の断片 (器だけ。効果は後で)
        g.drawString(font, Component.translatable("gui.skyabyss.growth.fragments", d.fragmentCount(), d.fragmentCapacity),
                left + 12, top + 166, LORE, false);

        renderHover(g, d, mouseX, mouseY);
    }

    // ステータスの効果の一行 (振ったあとの値で)
    static Component effect(Stat stat, int value) {
        return switch (stat) {
            case STRENGTH -> Component.translatable("gui.skyabyss.growth.effect.strength", pct(GrowthRules.meleeMultiplier(value)));
            case AGILITY -> Component.translatable("gui.skyabyss.growth.effect.agility",
                    pct(GrowthRules.moveMultiplier(value)), pct(GrowthRules.projectileMultiplier(value)));
            case SPIRIT -> Component.translatable("gui.skyabyss.growth.effect.spirit",
                    (int) Math.round(GrowthRules.BASE_MANA + GrowthRules.bonusMana(value)));
            case VITALITY -> Component.translatable("gui.skyabyss.growth.effect.vitality",
                    String.format("%.1f", GrowthRules.bonusHealth(value)));
        };
    }

    static String pct(double v) {
        return String.format("%.1f", v * 100);
    }

    // 名前に重ねると説明、断片の行に重ねると中身、バーに重ねると合計の経験値
    private void renderHover(GuiGraphics g, GrowthData d, int mouseX, int mouseY) {
        for (Stat stat : Stat.values()) {
            int ry = top + ROW_Y + stat.ordinal() * ROW_H;
            if (mouseX >= left + 8 && mouseX < left + 196 && mouseY >= ry && mouseY < ry + ROW_H - 2) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(Component.translatable(stat.nameKey()).withColor(GOLD).getVisualOrderText());
                lines.addAll(font.split(Component.translatable("gui.skyabyss.growth.desc." + stat.id), 200));
                lines.addAll(font.split(Component.translatable("gui.skyabyss.growth.cap", GrowthRules.STAT_CAP).withColor(SOFT), 200));
                g.renderTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
        if (mouseX >= left + 8 && mouseX < left + 130 && mouseY >= top + 162 && mouseY < top + 176) {
            List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.translatable("gui.skyabyss.growth.fragments_desc"), 200));
            d.fragments.forEach((id, n) -> lines.add(Component.translatable(jp.revati.skyabyss.rpg.MemoryFragments.nameKey(id))
                    .append(" ×" + n).withColor(LORE).getVisualOrderText()));
            g.renderTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (mouseX >= left + BAR_X && mouseX < left + BAR_X + BAR_W && mouseY >= top + BAR_Y - 2 && mouseY < top + BAR_Y + 18) {
            g.renderTooltip(font, Component.translatable("gui.skyabyss.growth.lifetime", d.lifetimeXp(),
                    GrowthRules.totalXpFor(GrowthRules.MAX_LEVEL)), mouseX, mouseY);
        }
    }

    // ＋ と − の小さなボタン (11x11、テクスチャは 22x22 の 2 倍密度)
    static final class SmallButton extends AbstractButton {
        private final boolean plusSign;
        private final Runnable action;

        SmallButton(int x, int y, boolean plusSign, Runnable action) {
            super(x, y, 11, 11, Component.literal(plusSign ? "+" : "-"));
            this.plusSign = plusSign;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int state = !active ? 2 : isHoveredOrFocused() ? 1 : 0;
            int u = (plusSign ? 0 : 72) + state * 24;
            g.blit(TEXTURE, getX(), getY(), 11, 11, u, 416, 22, 22, TEX, TEX);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
