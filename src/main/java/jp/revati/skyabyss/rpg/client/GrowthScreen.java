package jp.revati.skyabyss.rpg.client;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.rpg.GrowthRules.Stat;
import jp.revati.skyabyss.client.ui.UiFit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

// ステータス画面 (J キー)。見るだけの記録: レベルと経験値、基礎 (筋力・敏捷・精神・耐久) とその効き目、記憶の断片。
// 基礎は技の木の節で上がる (docs/design/48。振り分けの釦はない。基礎は SkillManager.refreshStats が木から写す)。
// 背景は tools/gen/items/gen_growth_assets.py で作る 2 倍密度のテクスチャ (480x368 を 240x184 で描く)
public class GrowthScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/growth/status.png");
    static final int TEX = 512;
    static final int W = 240;
    static final int H = 184;
    // ステータスの行 (tools/gen/items/gen_growth_assets.py と同じ値)
    static final int ROW_Y = 60;
    static final int ROW_H = 24;
    static final int BAR_X = 62;
    static final int BAR_Y = 27;
    static final int BAR_W = 166;

    static final int TITLE = 0x9FE8FF;
    static final int TEXT = 0xE6E2D6;
    static final int SOFT = 0xA9AEBC;
    static final int GOLD = 0xF2C14E;
    static final int LORE = 0x7FD6D0;

    // 画面は窓に合わせて一様に大きくする (描くのも、当たりも、この一つの変換を通す)。論理の原点は (left, top)
    private final UiFit fit = new UiFit();
    private int left;
    private int top;

    public GrowthScreen() {
        super(Component.translatable("gui.skyabyss.growth.title"));
    }

    @Override
    protected void init() {
        fit.fit(width, height, W + 4, H + 24, 2.5f);
        left = 2;
        top = 20;
        // 技の画面へのつまみ (上の縁)
        jp.revati.skyabyss.rpg.client.skill.RpgTabs.addAt(this::addRenderableWidget, left + W / 2, top,
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
        g.blit(TEXTURE, left, top, W, H, 0, 0, W * 2, H * 2, TEX, TEX);
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

    private void renderBody(GuiGraphics g, int mouseX, int mouseY) {
        GrowthData d = data();
        g.drawCenteredString(font, title, left + W / 2, top + 8, TITLE);

        // レベルと経験値のバー
        boolean max = d.level >= GrowthRules.MAX_LEVEL;
        g.pose().pushPose();
        g.pose().translate(left + 12, top + 24, 0);
        g.pose().scale(jp.revati.skyabyss.client.ui.TextStyle.HEADING.scale(), jp.revati.skyabyss.client.ui.TextStyle.HEADING.scale(), 1f);
        g.drawString(font, Component.translatable("gui.skyabyss.growth.hud_level", d.level), 0, 0, GOLD, true);
        g.pose().popPose();
        float fraction = max ? 1f : (float) d.xp / Math.max(1, d.xpNeed());
        int fill = Mth.clamp(Math.round((BAR_W - 4) * fraction), 0, BAR_W - 4);
        if (fill > 0) {
            g.blit(TEXTURE, left + BAR_X + 2, top + BAR_Y + 2, fill, 2, 0, max ? 470 : 466, fill * 2, 4, TEX, TEX);
        }
        Component xpText = max ? Component.translatable("gui.skyabyss.growth.max")
                : Component.translatable("gui.skyabyss.growth.xp", d.xp, d.xpNeed());
        g.drawString(font, xpText, left + BAR_X + BAR_W - font.width(xpText), top + BAR_Y + 9, SOFT, false);

        // 職と、基礎の上がり方 (木の節)
        jp.revati.skyabyss.rpg.skill.Job job = jp.revati.skyabyss.rpg.skill.Job.byId(jp.revati.skyabyss.rpg.skill.SkillManager.get(minecraft.player).curJob);
        Component jobLine = Component.translatable("gui.skyabyss.growth.job_line",
                job == null ? Component.translatable("gui.skyabyss.growth.job_none") : Component.translatable(job.nameKey()));
        g.drawString(font, jobLine, left + 12, top + 46, job == null ? SOFT : GOLD, false);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("gui.skyabyss.growth.from_tree"), left + 12 + font.width(jobLine) + 8, top + 46,
                W - 24 - font.width(jobLine) - 8, SOFT, false);

        // ステータスの行
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            int ry = top + ROW_Y + i * ROW_H;
            g.blit(TEXTURE, left + 10, ry + 4, 16, 16, i * 32, 384, 32, 32, TEX, TEX);
            g.drawString(font, Component.translatable(stat.nameKey()), left + 32, ry + 3, TEXT, false);
            int value = d.stat(stat);
            g.drawString(font, String.valueOf(value), left + 104, ry + 3, value > 0 ? GOLD : TEXT, false);
            g.drawString(font, effect(stat, value), left + 32, ry + 13, SOFT, false);
        }

        // 記憶の断片 (器だけ。効果は後で)
        g.drawString(font, Component.translatable("gui.skyabyss.growth.fragments", d.fragmentCount(), d.fragmentCapacity),
                left + 12, top + 166, LORE, false);

        renderHover(g, d, mouseX, mouseY);
    }

    // ステータスの効果の一行
    static Component effect(Stat stat, int value) {
        return switch (stat) {
            case STRENGTH -> Component.translatable("gui.skyabyss.growth.effect.strength", pct(GrowthRules.meleeMultiplier(value)));
            case AGILITY -> Component.translatable("gui.skyabyss.growth.effect.agility",
                    pct(GrowthRules.moveMultiplier(value)), pct(GrowthRules.projectileMultiplier(value)));
            case SPIRIT -> Component.translatable("gui.skyabyss.growth.effect.spirit",
                    (int) Math.round(GrowthRules.BASE_MANA + GrowthRules.bonusMana(value)));
            case VITALITY -> Component.translatable("gui.skyabyss.growth.effect.vitality",
                    jp.revati.skyabyss.Hp.of(GrowthRules.bonusHealth(value)));
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
                lines.addAll(jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.growth.desc." + stat.id), 200));
                lines.addAll(jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.growth.cap", GrowthRules.STAT_CAP).withColor(SOFT), 200));
                tip(lines);
                return;
            }
        }
        if (mouseX >= left + 8 && mouseX < left + 130 && mouseY >= top + 162 && mouseY < top + 176) {
            List<FormattedCharSequence> lines = new ArrayList<>(jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable("gui.skyabyss.growth.fragments_desc"), 200));
            d.fragments.forEach((id, n) -> lines.add(Component.translatable(jp.revati.skyabyss.rpg.MemoryFragments.nameKey(id))
                    .append(" ×" + n).withColor(LORE).getVisualOrderText()));
            tip(lines);
            return;
        }
        if (mouseX >= left + BAR_X && mouseX < left + BAR_X + BAR_W && mouseY >= top + BAR_Y - 2 && mouseY < top + BAR_Y + 18) {
            tip(List.of(Component.translatable("gui.skyabyss.growth.lifetime", d.lifetimeXp(),
                    GrowthRules.totalXpFor(GrowthRules.MAX_LEVEL)).getVisualOrderText()));
        }
    }

    @Override
    public int[] textBounds() {
        return fit.screenRect(left - 2, top - 18, W + 4, H + 20);
    }
}
