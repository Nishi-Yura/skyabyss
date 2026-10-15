package jp.revati.skyabyss.rpg.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthData;
import jp.revati.skyabyss.rpg.GrowthRules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// 画面左上の小さな成長の表示: 「Lv 12」と細いバー、得た経験値の「+12」。
// バニラの経験値バー (画面の下) には重ならない。F1 と F3 の画面では出さない
public final class GrowthHud {
    // テクスチャは 2 倍の密度 (128x32 を 64x16 で描く)。0〜9 行目が枠、10〜15 行目が中身、16〜21 行目が上限の金
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/growth/hud.png");
    static final int X = 6;
    static final int Y = 6;
    static final int BAR_W = 64;
    static final int BAR_H = 5;

    static final int TEXT = 0x9FE8FF;
    static final int GOLD = 0xF2C14E;
    static final int SOFT = 0xB8B8C8;

    private GrowthHud() {
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        GrowthData data = Growth.get(mc.player);
        Font font = mc.font;
        boolean max = data.level >= GrowthRules.MAX_LEVEL;
        // 一行目: Lv と、振り分けていない力があれば金の印
        Component lv = Component.translatable("gui.skyabyss.growth.hud_level", data.level);
        g.drawString(font, lv, X, Y, TEXT, true);
        int unspent = data.unspentPoints();
        if (unspent > 0) {
            g.drawString(font, "✦" + unspent, X + font.width(lv) + 4, Y, GOLD, true);
        }
        // 二行目: 細いバー
        int by = Y + 11;
        g.blit(TEXTURE, X, by, BAR_W, BAR_H, 0, 0, 128, 10, 128, 32);
        float fraction = max ? 1f : (float) data.xp / Math.max(1, GrowthRules.xpToNext(data.level));
        int fill = Mth.clamp(Math.round(62 * fraction), 0, 62);
        if (fill > 0) {
            g.blit(TEXTURE, X + 1, by + 1, fill, 3, 2, max ? 16 : 10, fill * 2, 6, 128, 32);
        }
        if (GrowthClient.barFlash > 0 && !max) {
            int a = (int) (GrowthClient.barFlash / 12f * 0x70);
            g.fill(X + 1, by + 1, X + 1 + fill, by + 4, (a << 24) | 0xFFFFFF);
        }
        // 得た経験値: バーの下に、薄れながら並ぶ
        int y = by + 8;
        for (GrowthClient.Popup p : GrowthClient.POPUPS) {
            float left = 1f - (float) p.age / GrowthClient.POPUP_TICKS;
            int alpha = Mth.clamp((int) (Math.min(1f, left * 3f) * 255), 8, 255);
            Component line = p.reason.isEmpty()
                    ? Component.literal("+" + p.amount)
                    : Component.literal("+" + p.amount + " ").append(Component.translatable(p.reason));
            g.drawString(font, line, X, y, (alpha << 24) | (p.reason.isEmpty() ? SOFT : TEXT), true);
            y += 10;
        }
        renderLevelUp(g, mc, delta);
    }

    // レベルアップの文字: 画面の中ほどより少し上に、控えめに一行と細い線
    private static void renderLevelUp(GuiGraphics g, Minecraft mc, DeltaTracker delta) {
        if (GrowthClient.levelUpAge < 0) {
            return;
        }
        float t = GrowthClient.levelUpAge + delta.getGameTimeDeltaPartialTick(false);
        float alpha;
        if (t < 10) {
            alpha = t / 10f;
        } else if (t < 60) {
            alpha = 1f;
        } else {
            alpha = Math.max(0f, 1f - (t - 60) / 30f);
        }
        int a = Mth.clamp((int) (alpha * 255), 8, 255);
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 - 58;
        Component head = Component.translatable("gui.skyabyss.growth.level_up_head");
        Component level = Component.translatable("gui.skyabyss.growth.level_up_level", GrowthClient.levelUpLevel);
        g.drawCenteredString(font, head, cx, y, (a << 24) | TEXT);
        // 線は時間とともに左右へ伸びる
        int half = (int) (Math.min(1f, t / 20f) * 60);
        int lineColor = ((int) (a * 0.7f) << 24) | 0x7FD6D0;
        g.fill(cx - half, y + 11, cx + half, y + 12, lineColor);
        g.pose().pushPose();
        g.pose().translate(cx, y + 15, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawCenteredString(font, level, 0, 0, (a << 24) | GOLD);
        g.pose().popPose();
    }
}
