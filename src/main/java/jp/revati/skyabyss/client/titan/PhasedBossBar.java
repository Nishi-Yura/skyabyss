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
// テクスチャ: textures/gui/boss/<prefix>_icon_pN.png と <prefix>_frame_pN.png (N は 1 から)
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

    // 見た目: テクスチャの頭、段階ごとの帯の色・帯の上の線・段階の名前の色、段階の名前の翻訳キーの頭
    public record Style(String prefix, int[] fill, int[] fillTop, int[] label, String phaseKey, String roaringKey, int roaringColor) {
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
        int labelX = x + ICON + 4 + mc.font.width(name) + 6;
        Component label = roaring ? Component.translatable(style.roaringKey()) : Component.translatable(style.phaseKey() + n);
        g.drawString(mc.font, label, labelX, y + 4, roaring ? style.roaringColor() : style.label()[p], true);
        String percent = Math.round(boss.getProgress() * 100) + "%";
        g.drawString(mc.font, percent, x + WIDTH - mc.font.width(percent), y + 4, 0xFFFFFF, true);

        int barY = y + ICON + 2;
        g.blit(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/boss/" + style.prefix() + "_frame_p" + n + ".png"),
                x, barY, 0, 0, WIDTH, FRAME_HEIGHT, WIDTH, FRAME_HEIGHT);
        float progress = Math.max(0f, Math.min(1f, boss.getProgress()));
        int fill = Math.round(TRACK_W * progress);
        if (fill > 0) {
            int left = x + TRACK_X;
            int top = barY + TRACK_Y;
            g.fill(left, top, left + fill, top + TRACK_H, 0xFF000000 | style.fill()[p]);
            g.fill(left, top, left + fill, top + 1, 0xFF000000 | style.fillTop()[p]);
        }
    }
}
