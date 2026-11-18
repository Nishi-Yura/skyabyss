package jp.revati.skyabyss.rpg.client;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import jp.revati.skyabyss.client.ui.Golden;
import jp.revati.skyabyss.client.ui.UiFit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

// ステータス画面 (J キー)。見るだけの記録: レベルと経験値、基礎 (筋力・敏捷・精神・耐久) とその効き目、記憶の断片。
// 基礎は技の木の節で上がる (docs/design/48。振り分けの釦はない。基礎は SkillManager.refreshStats が木から写す)。
// 板は黄金比の大きい板 (client/ui/Golden。docs/design/56)。見出しの帯に記録・技・旅のつまみ、主の列 (左) に基礎の 4 行、
// 脇の列 (右) にレベル・経験値・職・記憶の断片。基礎の絵だけ tools/gen/items/gen_growth_assets.py のテクスチャから取る
public class GrowthScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/growth/status.png");
    static final int TEX = 512;
    static final Golden.Layout LAYOUT = Golden.Layout.large(0);
    static final int W = Golden.L_W;
    static final int H = Golden.L_H;
    // 主の列 (基礎の行) と脇の列 (レベル・職・断片)
    static final Golden.Box MAIN = LAYOUT.body().major(true);
    static final Golden.Box SIDE = LAYOUT.body().minor(false);
    // 基礎の行: 高さ 34、あいだ 13
    static final int ROW_H = Golden.S34;
    static final int ROW_STEP = Golden.S34 + Golden.S13;
    // 経験値の帯 (脇の列の中)
    static final int BAR_X = SIDE.x() + Golden.S8;
    static final int BAR_Y = SIDE.y() + Golden.S5 + Golden.ROW + Golden.S3;
    static final int BAR_W = SIDE.w() - 2 * Golden.S8;
    static final int BAR_H = Golden.S5;

    static final int TITLE = Golden.PALE;
    static final int TEXT = Golden.TEXT;
    static final int SOFT = Golden.SOFT;
    static final int GOLD = Golden.GOLD;
    static final int LORE = Golden.TEAL;

    // 画面は窓に合わせて一様に大きくする (描くのも、当たりも、この一つの変換を通す)。論理の原点は (left, top)
    private final UiFit fit = new UiFit();
    private int left;
    private int top;

    public GrowthScreen() {
        super(Component.translatable("gui.skyabyss.growth.title"));
    }

    @Override
    protected void init() {
        fit.fit(width, height, W, H, 3f);
        left = 0;
        top = 0;
        // 技の画面などへのつまみ (見出しの帯の中)
        jp.revati.skyabyss.rpg.client.skill.RpgTabs.addHead(this::addRenderableWidget, LAYOUT.head(),
                jp.revati.skyabyss.rpg.client.skill.RpgTabs.STATUS);
    }

    private GrowthData data() {
        return Growth.get(minecraft.player);
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
        // 全面の暗い地だけ (板は render の中で、窓に合わせた変換の中で描く)
        super.renderBackground(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        renderBackground(g, screenX, screenY, partialTick);
        pendingTip = null;
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.frame(g, font, LAYOUT, title);
        for (Stat stat : Stat.values()) {
            Golden.recess(g, statRow(stat.ordinal()));
        }
        Golden.recess(g, SIDE);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        renderBody(g, mouseX, mouseY);
        fit.end(g);
        if (pendingTip != null) {
            g.renderTooltip(font, pendingTip, screenX, screenY);
            pendingTip = null;
        }
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        return super.mouseClicked(fit.x(screenX), fit.y(screenY), button);
    }

    @Override
    public boolean mouseDragged(double screenX, double screenY, int button, double dx, double dy) {
        return super.mouseDragged(fit.x(screenX), fit.y(screenY), button, dx / fit.scale(), dy / fit.scale());
    }

    @Override
    public boolean mouseReleased(double screenX, double screenY, int button) {
        return super.mouseReleased(fit.x(screenX), fit.y(screenY), button);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double dx, double dy) {
        return super.mouseScrolled(fit.x(screenX), fit.y(screenY), dx, dy);
    }

    // 囲み (ツールチップ) は変換の外で描く (変換の中では窓の端で切れる)。描く場所は画面の座標
    private List<FormattedCharSequence> pendingTip;

    private void tip(List<FormattedCharSequence> lines) {
        pendingTip = lines;
    }

    // 基礎の i 行目の欄
    static Golden.Box statRow(int i) {
        return new Golden.Box(MAIN.x(), MAIN.y() + 1 + i * ROW_STEP, MAIN.w(), ROW_H);
    }

    private void renderBody(GuiGraphics g, int mouseX, int mouseY) {
        GrowthData d = data();
        int sx = SIDE.x() + Golden.S8;
        int sw = SIDE.w() - 2 * Golden.S8;
        int y = SIDE.y() + Golden.S5;

        // レベルと経験値の帯 (脇の列の上)
        boolean max = d.level >= GrowthRules.MAX_LEVEL;
        g.drawString(font, Component.translatable("gui.skyabyss.growth.hud_level", d.level), sx, Golden.textY(y, Golden.ROW), GOLD, false);
        float fraction = max ? 1f : (float) d.xp / Math.max(1, d.xpNeed());
        g.fill(BAR_X, BAR_Y, BAR_X + BAR_W, BAR_Y + BAR_H, 0xFF364064);
        g.fill(BAR_X + 1, BAR_Y + 1, BAR_X + BAR_W - 1, BAR_Y + BAR_H - 1, 0xFF0A0E1E);
        int fill = Mth.clamp(Math.round((BAR_W - 2) * fraction), 0, BAR_W - 2);
        if (fill > 0) {
            g.fill(BAR_X + 1, BAR_Y + 1, BAR_X + 1 + fill, BAR_Y + BAR_H - 1, max ? 0xFF8FD0FF : 0xFFE0B040);
        }
        Component xpText = max ? Component.translatable("gui.skyabyss.growth.max")
                : Component.translatable("gui.skyabyss.growth.xp", d.xp, d.xpNeed());
        int ty = BAR_Y + BAR_H + Golden.S3;
        g.drawString(font, xpText, sx + sw - font.width(xpText), Golden.textY(ty, Golden.ROW), SOFT, false);

        // 職と、基礎の上がり方 (木の節)
        ty += Golden.ROW + Golden.S8;
        jp.revati.skyabyss.rpg.skill.Job job = jp.revati.skyabyss.rpg.skill.Job.byId(jp.revati.skyabyss.rpg.skill.SkillManager.get(minecraft.player).curJob);
        Component jobLine = Component.translatable("gui.skyabyss.growth.job_line",
                job == null ? Component.translatable("gui.skyabyss.growth.job_none") : Component.translatable(job.nameKey()));
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, jobLine, sx, Golden.textY(ty, Golden.ROW), sw, job == null ? SOFT : GOLD, false);
        ty += Golden.ROW;
        for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.growth.from_tree"), sw)) {
            g.drawString(font, line, sx, Golden.textY(ty, Golden.ROW), SOFT, false);
            ty += Golden.ROW;
        }

        // 基礎の行 (主の列)
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            Golden.Box r = statRow(i);
            g.blit(TEXTURE, r.x() + 9, r.y() + 9, 16, 16, i * 32, 384, 32, 32, TEX, TEX);
            int value = d.stat(stat);
            g.drawString(font, Component.translatable(stat.nameKey()), r.x() + Golden.S34, Golden.textY(r.y() + 4, Golden.ROW), TEXT, false);
            String v = String.valueOf(value);
            g.drawString(font, v, r.right() - Golden.S8 - font.width(v), Golden.textY(r.y() + 4, Golden.ROW), value > 0 ? GOLD : TEXT, false);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, effect(stat, value, d.level), r.x() + Golden.S34, Golden.textY(r.y() + 4 + Golden.ROW, Golden.ROW),
                    r.w() - Golden.S34 - Golden.S8, SOFT, false);
        }

        renderHover(g, d, mouseX, mouseY);
    }

    // ステータスの効果の一行
    static Component effect(Stat stat, int value, int level) {
        return switch (stat) {
            case STRENGTH -> Component.translatable("gui.skyabyss.growth.effect.strength", pct(GrowthRules.meleeMultiplier(value)));
            case AGILITY -> Component.translatable("gui.skyabyss.growth.effect.agility",
                    pct(GrowthRules.moveMultiplier(value)), pct(GrowthRules.projectileMultiplier(value)));
            case SPIRIT -> Component.translatable("gui.skyabyss.growth.effect.spirit",
                    (int) Math.round(GrowthRules.BASE_MANA + GrowthRules.bonusMana(value)));
            // レベルで増えた体力も足す (RecordBuilder.effect と同じ)
            case VITALITY -> Component.translatable("gui.skyabyss.growth.effect.vitality_level",
                    jp.revati.skyabyss.Hp.of(GrowthRules.bonusHealth(value) + GrowthRules.levelHealth(level)),
                    jp.revati.skyabyss.Hp.of(GrowthRules.levelHealth(level)));
        };
    }

    static String pct(double v) {
        return String.format("%.1f", v * 100);
    }

    // 名前に重ねると説明、バーに重ねると合計の経験値
    private void renderHover(GuiGraphics g, GrowthData d, int mouseX, int mouseY) {
        for (Stat stat : Stat.values()) {
            if (statRow(stat.ordinal()).contains(mouseX, mouseY)) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(Component.translatable(stat.nameKey()).withColor(GOLD).getVisualOrderText());
                lines.addAll(jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.growth.desc." + stat.id), 200));
                lines.addAll(jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.growth.cap", GrowthRules.STAT_CAP).withColor(SOFT), 200));
                tip(lines);
                return;
            }
        }
        if (mouseX >= BAR_X && mouseX < BAR_X + BAR_W && mouseY >= BAR_Y - Golden.ROW && mouseY < BAR_Y + BAR_H + Golden.ROW) {
            tip(List.of(Component.translatable("gui.skyabyss.growth.lifetime", d.lifetimeXp(),
                    GrowthRules.totalXpFor(GrowthRules.MAX_LEVEL)).getVisualOrderText()));
        }
    }

    @Override
    public int[] textBounds() {
        return fit.screenRect(0, 0, W, H);
    }
}
