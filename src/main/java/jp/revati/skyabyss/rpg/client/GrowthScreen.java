package jp.revati.skyabyss.rpg.client;

import java.util.ArrayList;
import java.util.List;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import jp.revati.skyabyss.client.ui.FitText;
import jp.revati.skyabyss.client.ui.LedgerStage;
import jp.revati.skyabyss.client.ui.UiFit;
import jp.revati.skyabyss.rpg.client.skill.RpgTabs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import jp.revati.skyabyss.client.ui.Paper;

// ステータス画面 (J キー)。見るだけの記録: レベルと経験値、基礎 (筋力・敏捷・精神・耐久) とその効き目。
// 基礎は技の木の節で上がる (docs/design/48。振り分けの釦はない。基礎は SkillManager.refreshStats が木から写す)。
// 組み方は帳の類の「黒に書いて、目録は帯に」(client/ui/LedgerStage。docs/design/56 の 2.1): つまみはスキルの木と同じ左上、その右に題。
// 左の黒い紙に基礎の 4 行 (絵・名前・値、二行目に効き目)、右の生成りの帯にレベル・経験値・職・基礎の上がり方。
// 基礎の絵だけ tools/gen/items/gen_growth_assets.py のテクスチャから取る
public class GrowthScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/growth/status.png");
    static final int TEX = 512;
    // 基礎の行
    static final int ROW_X = LedgerStage.PAGE_X;
    static final int ROW_W = LedgerStage.PAGE_W;
    static final int ROW_Y = LedgerStage.K_BODY_Y + 4;
    static final int ROW_H = 34;
    static final int ROW_STEP = 40;
    // 右の帯
    static final int SIDE_X = LedgerStage.INDEX_X;
    static final int SIDE_W = LedgerStage.INDEX_W - 4;
    // 経験値の帯
    static final int BAR_X = SIDE_X;
    static final int BAR_Y = 30;
    static final int BAR_W = SIDE_W;
    static final int BAR_H = 5;

    static final int TEXT = jp.revati.skyabyss.client.ui.Golden.TEXT;
    static final int SOFT = Paper.ON_BLACK_SOFT;
    static final int GOLD = Paper.ON_BLACK_PINK;

    // 画面は窓に合わせて一様に大きくする (描くのも、当たりも、この一つの変換を通す)
    private final UiFit fit = new UiFit();

    public GrowthScreen() {
        super(Component.translatable("gui.skyabyss.growth.title"));
    }

    @Override
    protected void init() {
        fit.fit(width, height, LedgerStage.W, LedgerStage.H, 3f);
        // 技の画面などへのつまみ (スキルの木と同じ左上)
        RpgTabs.addHead(this::addRenderableWidget, LedgerStage.K_HEAD, RpgTabs.STATUS, true);
    }

    private GrowthData data() {
        return Growth.get(minecraft.player);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // J で閉じる、K で技の画面へ
        if (RpgTabs.keyPressed(keyCode, scanCode, RpgTabs.STATUS)) {
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
        Paper.dim(g, width, height);
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        fit.fit(width, height, LedgerStage.W, LedgerStage.H, 3f);
        renderBackground(g, screenX, screenY, partialTick);
        pendingTip = null;
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        LedgerStage.backdrop(g, fit, width, height, LedgerStage.HEAD_K, false);
        LedgerStage.kTitle(g, font, title, LedgerStage.K_HEAD.x() + RpgTabs.headWidth(RpgTabs.STATUS));
        renderBody(g, mouseX, mouseY);
        for (net.minecraft.client.gui.components.Renderable r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
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

    static int rowY(int i) {
        return ROW_Y + i * ROW_STEP;
    }

    static boolean inRow(int i, int mx, int my) {
        return mx >= ROW_X && mx < ROW_X + ROW_W && my >= rowY(i) && my < rowY(i) + ROW_H;
    }

    private void renderBody(GuiGraphics g, int mouseX, int mouseY) {
        GrowthData d = data();
        // 基礎の行 (左の黒い紙)
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            int y = rowY(i);
            if (i > 0) {
                for (int x = ROW_X; x < ROW_X + ROW_W; x += 3) {
                    g.fill(x, y - 4, x + 1, y - 3, Paper.PINK_LO);
                }
            }
            g.blit(TEXTURE, ROW_X + 2, y + 6, 20, 20, i * 32, 384, 32, 32, TEX, TEX);
            int value = d.stat(stat);
            g.drawString(font, Component.translatable(stat.nameKey()), ROW_X + 30, y + 4, TEXT, false);
            String v = String.valueOf(value);
            g.drawString(font, v, ROW_X + ROW_W - font.width(v), y + 4, value > 0 ? GOLD : TEXT, false);
            FitText.draw(g, font, effect(stat, value, d.level), ROW_X + 30, y + 17, ROW_W - 30, SOFT, false);
        }

        // 右の帯: レベルと経験値・職・基礎の上がり方 (字は墨へ写る)
        Paper.inkBegin();
        int sx = SIDE_X;
        int sw = SIDE_W;
        boolean max = d.level >= GrowthRules.MAX_LEVEL;
        g.drawString(font, Component.translatable("gui.skyabyss.growth.hud_level", d.level), sx, 16, GOLD, false);
        float fraction = max ? 1f : (float) d.xp / Math.max(1, d.xpNeed());
        g.fill(BAR_X, BAR_Y, BAR_X + BAR_W, BAR_Y + BAR_H, Paper.CREAM_LO);
        g.fill(BAR_X + 1, BAR_Y + 1, BAR_X + BAR_W - 1, BAR_Y + BAR_H - 1, Paper.CREAM);
        int fill = Mth.clamp(Math.round((BAR_W - 2) * fraction), 0, BAR_W - 2);
        if (fill > 0) {
            g.fill(BAR_X + 1, BAR_Y + 1, BAR_X + 1 + fill, BAR_Y + BAR_H - 1, max ? Paper.PINK_LO : Paper.PINK);
        }
        Component xpText = max ? Component.translatable("gui.skyabyss.growth.max")
                : Component.translatable("gui.skyabyss.growth.xp", d.xp, d.xpNeed());
        int ty = BAR_Y + BAR_H + 4;
        FitText.draw(g, font, xpText, sx, ty, sw, SOFT, false);
        ty += 16;
        g.fill(sx, ty - 4, sx + sw, ty - 3, Paper.CREAM_LO);
        // 職と、基礎の上がり方 (木の節)
        jp.revati.skyabyss.rpg.skill.Job job = jp.revati.skyabyss.rpg.skill.Job.byId(jp.revati.skyabyss.rpg.skill.SkillManager.get(minecraft.player).curJob);
        Component jobLine = Component.translatable("gui.skyabyss.growth.job_line",
                job == null ? Component.translatable("gui.skyabyss.growth.job_none") : Component.translatable(job.nameKey()));
        for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, jobLine, sw)) {
            g.drawString(font, line, sx, ty, job == null ? SOFT : GOLD, false);
            ty += 11;
        }
        ty += 4;
        for (FormattedCharSequence line : jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.growth.from_tree"), sw)) {
            if (ty + 9 > 214) {
                break;
            }
            g.drawString(font, line, sx, ty, SOFT, false);
            ty += 11;
        }
        Paper.inkEnd();

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
            if (inRow(stat.ordinal(), mouseX, mouseY)) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(Component.translatable(stat.nameKey()).withColor(GOLD).getVisualOrderText());
                lines.addAll(jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.growth.desc." + stat.id), 200));
                lines.addAll(jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.growth.cap", GrowthRules.STAT_CAP).withColor(SOFT), 200));
                tip(lines);
                return;
            }
        }
        if (mouseX >= BAR_X && mouseX < BAR_X + BAR_W && mouseY >= BAR_Y - 13 && mouseY < BAR_Y + BAR_H + 13) {
            tip(List.of(Component.translatable("gui.skyabyss.growth.lifetime", d.lifetimeXp(),
                    GrowthRules.totalXpFor(GrowthRules.MAX_LEVEL)).getVisualOrderText()));
        }
    }

    @Override
    public int[] textBounds() {
        return fit.screenRect(0, 0, LedgerStage.W, LedgerStage.H);
    }
}
