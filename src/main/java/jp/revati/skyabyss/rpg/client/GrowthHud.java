package jp.revati.skyabyss.rpg.client;

import jp.revati.skyabyss.client.ui.TextStyle;
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
import javax.annotation.Nullable;
import jp.revati.skyabyss.client.hud.HudManager;
import jp.revati.skyabyss.hud.HudLayout.Id;
import jp.revati.skyabyss.hud.HudLayout.Rect;
import jp.revati.skyabyss.hud.HudLayout.Region;
import jp.revati.skyabyss.hud.HudLayout.Request;
import jp.revati.skyabyss.hud.HudLayout.Side;

// 小さな成長の表示: 「Lv 12」と細いバー、得た経験値の「+12」。F1 と F3 の画面では出さない。
// 置き場はクライアント設定 growthHudPosition で選ぶ: 左上 (標準) か、下の経験値バーの左 (バーの高さをそろえる)。
// 経験値バーの左に置くときは、ホットバーの左の枠 (オフハンド・攻撃の目盛り) を避け、「+12」は上へ積む
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

    // レベルアップの文字の高さ (見出し 1 行 + 線 + 1.5 倍の数)
    static final class LevelUp {
        static final int HEIGHT = 32;
    }

    // ---------------------------------------------------------------- 置き場 (HudManager が割り当てる。docs/design/18 の 14 章)
    // 左上に置くときの箱 (Lv の行 + バー + マナの行) と、その下の「+12」の列 (4 行ぶんをいつも空けて、目的の帯が上下に動かない)
    static final int STATUS_W = 72;
    static final int STATUS_H = 23;
    static final int POPUPS_W = 110;
    static final int POPUPS_H = 40;
    // 経験値バーの左に置くときの高さ: Lv の行から 22 (マナの行まで)。「+12」は上へ 10 ずつ積む
    static final int BOTTOM_H = 22;
    // 経験値バーの左の箱の下端 (画面の下から)。マナのバーの下端
    static final int BOTTOM_BASELINE = 18;
    // レベルアップの文字 (見出し 1 行 + 線 + 1.5 倍の数) の幅
    static final int LEVEL_UP_W = 124;

    // HUD の要求: 設定が経験値バーの左なら、入るときだけそこ。入らなければ (左右の枠で埋まった狭い画面) 左上へ
    public static void requests(Minecraft mc, int guiW, int guiH, java.util.List<Request> out) {
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        Request status = Request.of(Id.STATUS_TL, Region.TOP_LEFT, STATUS_W, STATUS_H);
        Request popups = Request.of(Id.POPUPS_TL, Region.TOP_LEFT, POPUPS_W, POPUPS_H);
        if (jp.revati.skyabyss.Config.GROWTH_HUD_POSITION.get() == jp.revati.skyabyss.Config.GrowthHudPosition.XP_BAR) {
            int n = GrowthClient.POPUPS.size();
            out.add(Request.side(Id.GROWTH_BAR, Side.LEFT, STATUS_W, BOTTOM_H + (n > 0 ? n * 10 + 1 : 0), BOTTOM_BASELINE, status, popups));
        } else {
            out.add(status);
            out.add(popups);
        }
        if (GrowthClient.levelUpAge >= 0) {
            out.add(Request.of(Id.LEVEL_UP, Region.TOP_CENTER, LEVEL_UP_W, LevelUp.HEIGHT).minTop((int) (guiH * 0.27f)));
        }
    }

    // 今の Lv の行の左上 (経験値バーの左なら箱の下から 22 上、左上なら箱の中。どちらにも置けなければ null)
    @Nullable
    public static int[] origin() {
        Rect gb = HudManager.rect(Id.GROWTH_BAR);
        if (gb != null) {
            return new int[] {gb.x(), gb.bottom() - BOTTOM_H};
        }
        Rect st = HudManager.rect(Id.STATUS_TL);
        return st == null ? null : new int[] {st.x() + 3, st.y()};
    }

    // マナのバー (SkillHud) の位置: Lv の行の 18 下
    @Nullable
    public static int[] manaOrigin() {
        int[] o = origin();
        return o == null ? null : new int[] {o[0], o[1] + 18};
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        int[] o = origin();
        if (o != null) {
            renderStatus(g, mc, o, HudManager.placed(Id.GROWTH_BAR));
        }
        renderLevelUp(g, mc, delta);
    }

    private static void renderStatus(GuiGraphics g, Minecraft mc, int[] o, boolean bottom) {
        GrowthData data = Growth.get(mc.player);
        Font font = mc.font;
        boolean max = data.level >= GrowthRules.MAX_LEVEL;
        int x = o[0];
        // 一行目: Lv と、振り分けていない力があれば金の印
        Component lv = Component.translatable("gui.skyabyss.growth.hud_level", data.level);
        g.drawString(font, lv, x, o[1], TEXT, true);
        int unspent = data.unspentPoints();
        if (unspent > 0) {
            g.drawString(font, "✦" + unspent, x + font.width(lv) + 4, o[1], GOLD, true);
        }
        // 二行目: 細いバー
        int by = o[1] + 11;
        g.blit(TEXTURE, x, by, BAR_W, BAR_H, 0, 0, 128, 10, 128, 32);
        float fraction = max ? 1f : (float) data.xp / Math.max(1, data.xpNeed());
        int fill = Mth.clamp(Math.round(62 * fraction), 0, 62);
        if (fill > 0) {
            g.blit(TEXTURE, x + 1, by + 1, fill, 3, 2, max ? 16 : 10, fill * 2, 6, 128, 32);
        }
        if (GrowthClient.barFlash > 0 && !max) {
            int a = (int) (GrowthClient.barFlash / 12f * 0x70);
            g.fill(x + 1, by + 1, x + 1 + fill, by + 4, (a << 24) | 0xFFFFFF);
        }
        // 得た経験値: 左上では専用の列 (バーの下) に、経験値バーの左では Lv の上へ積む
        Rect pu = HudManager.rect(Id.POPUPS_TL);
        if (!bottom && pu == null) {
            return;
        }
        int y = bottom ? o[1] - 11 : pu.y();
        int px = bottom ? x : pu.x() + 3;
        int rows = 0;
        for (GrowthClient.Popup p : GrowthClient.POPUPS) {
            if (!bottom && ++rows > GrowthClient.MAX_POPUPS) {
                break;
            }
            float left = 1f - (float) p.age / GrowthClient.POPUP_TICKS;
            int alpha = Mth.clamp((int) (Math.min(1f, left * 3f) * 255), 8, 255);
            Component line = p.reason.isEmpty()
                    ? Component.literal("+" + p.amount)
                    : Component.literal("+" + p.amount + " ").append(Component.translatable(p.reason));
            g.drawString(font, line, px, y, (alpha << 24) | (p.reason.isEmpty() ? SOFT : TEXT), true);
            y += bottom ? -10 : 10;
        }
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
        // 置き場は HudManager が決める (上の帯・ボスバーの下、中央の一行の上)。もらえなければ出さない
        Rect r = HudManager.rect(Id.LEVEL_UP);
        if (r == null) {
            return;
        }
        int cx = r.x() + r.w() / 2;
        int y = r.y();
        Component head = Component.translatable("gui.skyabyss.growth.level_up_head");
        Component level = Component.translatable("gui.skyabyss.growth.level_up_level", GrowthClient.levelUpLevel);
        g.drawCenteredString(font, head, cx, y, (a << 24) | TEXT);
        // 線は時間とともに左右へ伸びる
        int half = (int) (Math.min(1f, t / 20f) * 60);
        int lineColor = ((int) (a * 0.7f) << 24) | 0x7FD6D0;
        g.fill(cx - half, y + 11, cx + half, y + 12, lineColor);
        g.pose().pushPose();
        g.pose().translate(cx, y + 15, 0);
        g.pose().scale(TextStyle.HEADING.scale(), TextStyle.HEADING.scale(), 1f);
        g.drawCenteredString(font, level, 0, 0, (a << 24) | GOLD);
        g.pose().popPose();
    }
}
