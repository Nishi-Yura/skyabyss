package jp.revati.skyabyss.client.ui;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

// 集まりの類の組み方「扇に並ぶ顔札」(UI の 3 周目で集まり A をユーザーが選んだ。docs/design/56 の 2.1):
// 左上に黒の上の字で題と物語 (桃色の菱を添えて)。人は職を選ぶ画面と同じ扇の札 (大きな顔・名前・Lv)、長の札は桃色で上に菱を留める。
// 右に生成りの帯 (一覧・作る欄)、右上から差し込む桃色の紙 (届いた招待・知らせ)。下の山の上に釦。
// 舞台は論理の 408x228 (Paper.STAGE_W/H)
public final class PartyStage {
    private PartyStage() {
    }

    public static final int W = Paper.STAGE_W;
    public static final int H = Paper.STAGE_H;
    // 左上の字の欄
    public static final int HEAD_X = 22;
    public static final int HEAD_W = 220;
    // 右の帯と、その中の欄
    public static final float[] STRIP = {270, 54, 400, 56, 398, 190, 274, 186};
    public static final int STRIP_X = 282;
    public static final int STRIP_W = 110;
    public static final int STRIP_Y = 62;
    // 札
    public static final int CARD_W = 60;
    public static final int CARD_H = 88;
    // 下の山の上の釦の高さ
    public static final int FOOT_Y = 206;
    public static final int ON_PINK = 0xFFF6EA;

    private static final float[][] PEAKS = {{-20, 0}, {18, -12}, {60, -4}, {104, -20}, {150, -8}, {200, -16}, {246, -2}, {290, -22}, {336, -8}, {376, -18}, {428, -6}};
    private static final int[][] DIAMONDS = {{238, 22, 3, 1}, {12, 120, 2, 0}, {258, 146, 2, 1}};
    public static void backdrop(GuiGraphics g, UiFit fit, int width, int height) {
        backdrop(g, fit, width, height, HEAD_TITLE, true);
    }

    // 板の側 (docs/design/56 の 2.1「集まりの型」): 左の見出しの段の下の線 (題だけ・題と物語の三行・題と一行)、下の段の沈んだ欄 (左と右)
    public static final int HEAD_TITLE = 30;
    public static final int HEAD_STORY = 62;
    public static final int HEAD_FINDER = 44;
    public static final int PLATE_FOOT_Y = 199;
    public static final int PLATE_FOOT_H = 22;
    private static final int COL_X = 272;
    private static final int COL_W = 129;
    private static final int COL_BOTTOM = 195;

    // head = 左の見出しの段、footLeft = 左の下の段に欄を敷くか (右の下の段はいつも)
    public static void backdrop(GuiGraphics g, UiFit fit, int width, int height, int head, boolean footLeft) {
        Paper.stage(g, fit, width, height, "party", PEAKS, DIAMONDS);
        Plate.headBand(g, 5, COL_X - 8, head);
        if (footLeft) {
            Plate.well(g, 8, PLATE_FOOT_Y, COL_X - 12, PLATE_FOOT_H);
        }
        Plate.well(g, COL_X, PLATE_FOOT_Y, COL_W, PLATE_FOOT_H);
    }

    // 右の列の沈んだ欄 (板の側。top から下の段の上まで)
    public static void column(GuiGraphics g, int top) {
        Plate.well(g, COL_X, top, COL_W, COL_BOTTOM - top);
    }

    // 下の段の釦の y (板の側は沈んだ欄の真ん中)
    public static int footBtn(int paperY) {
        return PLATE_FOOT_Y + (PLATE_FOOT_H - Golden.BTN) / 2;
    }

    // 題 (桃色の菱と、黒の上の桃色の字)
    // 画面が替わったら開いた時を覚える (題は左から入る。docs/design/56 の 2.1「動き」)
    private static Object owner;
    private static long openedAt;

    // 題の幅 (2 倍で描いたときはその幅)
    public static int titleWidth(Font font, Component title) {
        return font.width(title) * 2 <= HEAD_W ? font.width(title) * 2 : font.width(title);
    }

    public static void title(GuiGraphics g, Font font, Component title) {
        Object s = Minecraft.getInstance().screen;
        if (s != owner) {
            owner = s;
            openedAt = net.minecraft.Util.getMillis();
        }
        float a = jp.revati.skyabyss.rpg.client.skill.SkillStage.out((net.minecraft.Util.getMillis() - openedAt) / 240f);
        int dx = Math.round((1f - a) * -10f);
        Paper.pin(g, HEAD_X - 8, 16, 3, true);
        // 入れば 2 倍
        if (font.width(title) * 2 <= HEAD_W) {
            jp.revati.skyabyss.rpg.client.skill.SkillStage.big(g, font, title, HEAD_X + dx, 9, HEAD_W, jp.revati.skyabyss.rpg.client.skill.SkillStage.fade(Paper.ON_BLACK_HI, a));
        } else {
            FitText.draw(g, font, title, HEAD_X + dx, 12, HEAD_W, jp.revati.skyabyss.rpg.client.skill.SkillStage.fade(Paper.ON_BLACK_PINK, a), false);
        }
    }

    // 右の帯 (見出しつき)
    public static void strip(GuiGraphics g, Font font, Component head) {
        column(g, 55);
        if (head != null) {
            FitText.draw(g, font, Component.literal(head.getString()), STRIP_X, STRIP_Y, STRIP_W, Paper.INK_HEAD, false);
        }
    }

    // 右上から差し込む桃色の紙 (招待・知らせ)。字の左上 {x, y} を返す
    public static int[] note(GuiGraphics g) {
        Plate.tile(g, 250, 7, 151, 38, Plate.ON, Plate.GOLD);
        return new int[] {258, 12};
    }

    // n 枚のうち i 枚目の札の真ん中 {x, y} (扇。左寄り)
    public static int[] cardAt(int i, int n) {
        // 四枚でも右の帯 (x 270 から) にかからないように詰める。五枚・六枚 (パーティーは 6 人まで) は細い札にして同じ幅に収める
        int cx = n >= 5 ? 34 + i * 41 : n >= 4 ? 44 + i * 61 : 52 + i * 68;
        // 板の絵柄では一列にそろえる (切り絵のときは扇の弧)
        int cy = 140;
        return new int[] {cx, cy};
    }

    // 札の幅 (五枚・六枚のときは細い札。顔 32 は入る)
    public static int cardW(int n) {
        return n >= 5 ? 40 : CARD_W;
    }

    // いま描いている札の幅 (card が覚える。cardText が字の幅に使う)
    private static int lastW = CARD_W;

    // 札を一枚 (pink = 長・自分など目立たせる札)
    public static void card(GuiGraphics g, int i, int n, boolean pink) {
        int[] c = cardAt(i, n);
        lastW = cardW(n);
        // 板の絵柄: 札はまっすぐな升 (長・自分は灯る)
        Plate.tile(g, c[0] - lastW / 2, c[1] - CARD_H / 2, lastW, CARD_H, pink ? Plate.ON : Plate.REST, Plate.GOLD);
    }

    // 札の上の顔 (32 角)
    public static void face(GuiGraphics g, String id, int cx, int cy) {
        PlayerFaceRenderer.draw(g, skin(id), cx - 16, cy - 38, 32);
    }

    // 札の字 (名前・二行目・三行目)
    public static void cardText(GuiGraphics g, Font font, int cx, int cy, Component name, Component second, Component third, boolean pink) {
        FitText.drawCentered(g, font, Component.literal(name.getString()), cx, cy, lastW - 6, Paper.INK, false);
        if (second != null) {
            FitText.drawCentered(g, font, second, cx, cy + 12, lastW - 6, pink ? ON_PINK : Paper.INK_HEAD, false);
        }
        if (third != null) {
            FitText.drawCentered(g, font, third, cx, cy + 24, lastW - 6, pink ? ON_PINK : Paper.INK_SOFT, false);
        }
    }

    // 長の印 (札の上の端の菱)
    public static void leaderPin(GuiGraphics g, int cx, int cy) {
        Paper.pin(g, cx, cy - CARD_H / 2 - 2, 4, false);
    }

    public static PlayerSkin skin(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            uuid = new UUID(0, id.hashCode());
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(uuid)) {
            return mc.player.getSkin();
        }
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(uuid);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(uuid);
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
