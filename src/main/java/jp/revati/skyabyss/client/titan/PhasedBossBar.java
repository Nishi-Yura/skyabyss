package jp.revati.skyabyss.client.titan;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;

// 段階ごとの枠と色で描くボスバー (08 の 10.2節の PhasedBossBar)。岩鯨の心臓のバー (StoneWhaleHeartBar) と同じ寸法と描き方を、
// テクスチャの名前の頭と段階の色で使い回せる形にした。
//  一段目: 顔の印 (16x16)、名前、段階の名前 (咆哮中は咆哮)、右に割合
//  二段目: 枠 (182x16。溝は x 3〜177、y 3〜8)、そのなかの帯
// テクスチャ: textures/gui/boss/<prefix>_icon_pN.png と <frame>_frame_pN.png (N は 1 から。枠は同じ族のボスで共有できる)
// 段のないボス (phaseKey が null) は段の名を出さない (名前と割合だけ)。
public final class PhasedBossBar {
    public static final int WIDTH = 182;
    public static final int FRAME_HEIGHT = 16;
    public static final int ICON = 16;
    private static final int TRACK_X = 3;
    private static final int TRACK_W = 175;
    private static final int TRACK_Y = 3;
    private static final int TRACK_H = 6;
    // バニラの横棒より高い段の分
    public static final int EXTRA_HEIGHT = 12;

    // 見た目: 印の頭 (icon)、枠の頭 (frame。普通は印と同じ)、段階ごとの帯の色・帯の上の線・段階の名前の色、段階の名前の翻訳キーの頭 (段なしは null)
    public record Style(String prefix, String frame, int[] fill, int[] fillTop, int[] label, String phaseKey, String roaringKey, int roaringColor) {
        public Style(String prefix, int[] fill, int[] fillTop, int[] label, String phaseKey, String roaringKey, int roaringColor) {
            this(prefix, prefix, fill, fillTop, label, phaseKey, roaringKey, roaringColor);
        }

        public int phases() {
            return fill.length;
        }
    }

    private PhasedBossBar() {
    }

    public static void draw(GuiGraphics g, Minecraft mc, Style style, Component name, int phase, boolean roaring, BossEvent boss, int x, int y) {
        int p = Math.min(style.phases() - 1, Math.max(0, phase));
        int n = p + 1;
        g.blit(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/boss/" + style.prefix() + "_icon_p" + n + ".png"),
                x, y, 0, 0, ICON, ICON, ICON, ICON);
        g.drawString(mc.font, name, x + ICON + 4, y + 4, 0xFFFFFF, true);
        if (style.phaseKey() != null) {
            int labelX = x + ICON + 4 + mc.font.width(name) + 6;
            Component label = roaring ? Component.translatable(style.roaringKey()) : Component.translatable(style.phaseKey() + n);
            g.drawString(mc.font, label, labelX, y + 4, roaring ? style.roaringColor() : style.label()[p], true);
        }
        int barY = y + ICON + 2;
        g.blit(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/boss/" + style.frame() + "_frame_p" + n + ".png"),
                x, barY, 0, 0, WIDTH, FRAME_HEIGHT, WIDTH, FRAME_HEIGHT);
        float progress = Math.max(0f, Math.min(1f, boss.getProgress()));
        int fill = Math.round(TRACK_W * progress);
        if (fill > 0) {
            int left = x + TRACK_X;
            int top = barY + TRACK_Y;
            g.fill(left, top, left + fill, top + TRACK_H, 0xFF000000 | style.fill()[p]);
            g.fill(left, top, left + fill, top + 1, 0xFF000000 | style.fillTop()[p]);
        }
        drawHp(g, mc, boss, x, barY + TRACK_Y + TRACK_H / 2);
    }

    // 体力の数を、帯の真ん中に小さく重ねる (一段目の名前・段の名と重ならないように。2026-11-08 の画面で「第一段階」と数が重なっていた)
    // 描いたバーの下の端 (崩しゲージ client/combat/BreakGaugeClient がこのすぐ下に帯を描く。バーごと、描くたびに入れ直す)
    public static final java.util.Map<java.util.UUID, Integer> BOTTOM = new java.util.HashMap<>();

    public static void drawHp(GuiGraphics g, Minecraft mc, BossEvent boss, int x, int midY) {
        BOTTOM.put(boss.getId(), midY + 10);
        String text = hpText(mc, boss);
        float scale = 0.75f;
        g.pose().pushPose();
        g.pose().translate(x + WIDTH / 2f - mc.font.width(text) * scale / 2f, midY - 4 * scale, 200);
        g.pose().scale(scale, scale, 1f);
        g.drawString(mc.font, text, 0, 0, 0xFFFFFF, true);
        g.pose().popPose();
    }

    // 帯の右の数: 持ち主の体力「1,240 / 3,000」(画面の数。Hp.SHOWN 倍)。持ち主が見つからなければ割合
    // (2026-11-08 の声「敵のhp表記、今%だけど、数値にして」)
    public static String hpText(net.minecraft.client.Minecraft mc, net.minecraft.world.BossEvent boss) {
        net.minecraft.world.entity.LivingEntity owner = jp.revati.skyabyss.client.BossOwners.find(mc, boss, net.minecraft.world.entity.LivingEntity.class, 160);
        if (owner == null || owner.getMaxHealth() <= 0) {
            return Math.round(boss.getProgress() * 100) + "%";
        }
        int max = jp.revati.skyabyss.Hp.whole(owner.getMaxHealth());
        int now = Math.min(max, Math.round(boss.getProgress() * max));
        return String.format("%,d/%,d", now, max);
    }
}
