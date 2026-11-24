package jp.revati.skyabyss.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// 帳の類の組み方「黒に書いて、目録は帯に」(UI の 3 周目で帳 C をユーザーが選んだ。docs/design/56 の 2.1):
// 読む中身は左の黒い紙にじかに生成りの字で (題は桃色の菱を添えて、その下に濃い桃色の線)。
// 一覧 (目録) は右に縦長の生成りの帯 (少し傾けて)、選んだ行は帯から左へはみ出す桃色の付箋。下の段 (報酬・釦・手引き) は左下の山の上。
// 舞台は論理の 408x228 (Paper.STAGE_W/H)
public final class LedgerStage {
    private LedgerStage() {
    }

    public static final int W = Paper.STAGE_W;
    public static final int H = Paper.STAGE_H;
    // 目録の帯と、その中の一覧の欄・見出し
    public static final float[] INDEX = {270, 4, 404, 8, 400, 222, 274, 218};
    public static final int INDEX_X = 282;
    public static final int INDEX_W = 112;
    public static final int INDEX_HEAD_Y = 14;
    public static final int LIST_Y = 30;
    public static final int LIST_H = 184;
    // 読む頁 (黒い紙の上)
    public static final int PAGE_X = 18;
    public static final int PAGE_Y = 10;
    public static final int PAGE_W = 236;
    // 題の線の下から中身
    public static final int BODY_Y = 42;
    public static final int BODY_H = 140;
    // 下の段 (山の上)
    public static final int FOOT_Y = 196;
    // 字の色 (桃色の付箋の上)
    public static final int ON_PINK = 0xFFF6EA;

    private static final float[][] PEAKS = {{-20, -14}, {40, -26}, {90, -10}, {140, -30}, {196, -12}, {240, -20}, {300, -4}, {428, -2}};
    private static final int[][] DIAMONDS = {{258, 14, 2, 0}, {262, 120, 3, 1}};

    // K の頁 (Growth・Journey・Profile・Soul): 記録・スキル・旅のつまみはスキルの木と同じ所 (SkillScreen.HEAD)。頁の題はつまみの右、中身は K_BODY_Y から
    public static final Golden.Box K_HEAD = new Golden.Box(10, 5, 252, 21);
    public static final int K_BODY_Y = 32;

    // 板の側 (docs/design/56 の 2.1「帳の型」): 左の頁の見出しの段の下の線 (題と添えの頁・K の頁・題なし)、下の段の沈んだ欄
    public static final int HEAD_PAGE = PAGE_Y + 28;
    public static final int HEAD_K = 28;
    public static final int HEAD_NONE = 0;
    public static final int PLATE_FOOT_Y = 199;
    public static final int PLATE_FOOT_H = 22;
    private static final int PLATE_LEFT_X = 8;
    private static final int PLATE_LEFT_W = 260;
    // 目録の欄 (板の側)
    private static final int WELL_X = 272;
    private static final int WELL_W = 129;

    // 下の段の釦の y (板の側は沈んだ欄の真ん中)
    public static int footBtn(int paperY) {
        return UiLook.plate() ? PLATE_FOOT_Y + (PLATE_FOOT_H - Golden.BTN) / 2 : paperY;
    }

    // 動き (docs/design/56 の 2.1「動き」): 画面が替わったら開いた時を覚える。題は左から入り、選んだ行の付箋は前の行から滑って移る
    private static Object owner;
    private static long openedAt;
    private static float flagY = Float.NaN;
    private static int flagTarget;
    private static float flagFrom;
    private static long flagAt;
    private static int lastHoverY = Integer.MIN_VALUE;
    private static long lastHoverFrame;

    private static void track() {
        Object s = net.minecraft.client.Minecraft.getInstance().screen;
        if (s != owner) {
            owner = s;
            openedAt = net.minecraft.Util.getMillis();
            flagY = Float.NaN;
            lastHoverY = Integer.MIN_VALUE;
        }
    }

    private static float open(long delay) {
        return jp.revati.skyabyss.rpg.client.skill.SkillStage.out((net.minecraft.Util.getMillis() - openedAt - delay) / 240f);
    }

    // K の頁の題 (つまみの右。x = つまみの右の端)。入れば 2 倍
    public static void kTitle(GuiGraphics g, Font font, Component title, int x) {
        track();
        float a = open(0);
        Paper.pin(g, x + 4, 15, 3, true);
        int dx = Math.round((1f - a) * -10f);
        if (font.width(title) * 2 <= 262 - x - 12) {
            jp.revati.skyabyss.rpg.client.skill.SkillStage.big(g, font, title, x + 12 + dx, 8, 262 - x - 12, jp.revati.skyabyss.rpg.client.skill.SkillStage.fade(Paper.ON_BLACK_HI, a));
        } else {
            FitText.draw(g, font, title, x + 12 + dx, 11, 262 - x - 12, jp.revati.skyabyss.rpg.client.skill.SkillStage.fade(Paper.ON_BLACK_PINK, a), false);
        }
        if (!UiLook.plate()) {
            g.fill(PAGE_X, 26, PAGE_X + PAGE_W, 27, Paper.PINK_LO);
        }
    }

    // 黒い紙・山・目録の帯 (fit.begin のあと)
    public static void backdrop(GuiGraphics g, UiFit fit, int width, int height) {
        backdrop(g, fit, width, height, HEAD_PAGE, false);
    }

    // head = 左の頁の見出しの段 (HEAD_PAGE・HEAD_K・HEAD_NONE)、foot = 下の段の欄を敷くか (板の側だけ)
    public static void backdrop(GuiGraphics g, UiFit fit, int width, int height, int head, boolean foot) {
        if (UiLook.plate()) {
            // 板の絵柄: 板を一枚。左の頁は見出しの段を溝で区切り、中身は板にじか。目録は右の縁の内側いっぱいの沈んだ欄
            Plate.openFade(g);
            Plate.panel(g, 0, 0, W, H, 0, Plate.GOLD);
            if (head > 0) {
                Plate.headBand(g, 5, WELL_X - 8, head);
            }
            Plate.well(g, WELL_X, 5, WELL_W, H - 10);
            if (foot) {
                Plate.well(g, PLATE_LEFT_X, PLATE_FOOT_Y, PLATE_LEFT_W, PLATE_FOOT_H);
            }
            return;
        }
        Paper.stage(g, fit, width, height, "ledger", PEAKS, DIAMONDS);
        Paper.cut(g, "ledger_index", INDEX, Paper.CREAM, Paper.BLACK_LO, -3, 3);
    }

    // 目録の見出し (帯の上の端)
    public static void indexHead(GuiGraphics g, Font font, Component title) {
        FitText.draw(g, font, Component.literal(title.getString()), INDEX_X + 2, INDEX_HEAD_Y, INDEX_W - 2, Paper.INK_HEAD, false);
        if (UiLook.plate()) {
            Plate.rule(g, INDEX_X - 4, 25, INDEX_W + 8);
            return;
        }
        g.fill(INDEX_X + 2, 25, INDEX_X + INDEX_W, 26, Paper.CREAM_LO);
    }

    // 選んだ行の付箋 (帯から左へはみ出す)。y は行の上、h は行の高さ。行を替えると前の行から 160ms で滑って移る
    public static void flag(GuiGraphics g, int y, int h) {
        track();
        long now = net.minecraft.Util.getMillis();
        if (Float.isNaN(flagY)) {
            flagY = y;
            flagFrom = y;
            flagTarget = y;
            flagAt = now - 1000;
        } else if (y != flagTarget) {
            flagFrom = flagY;
            flagTarget = y;
            flagAt = now;
        }
        flagY = flagFrom + (flagTarget - flagFrom) * jp.revati.skyabyss.rpg.client.skill.SkillStage.out((now - flagAt) / 160f);
        int dy = Math.round(flagY) - y;
        g.pose().pushPose();
        g.pose().translate(0, dy, 0);
        if (UiLook.plate()) {
            // 選んだ行: 欄の内側いっぱいの灯った升 (左の端に金の印)
            Plate.tile(g, WELL_X + 2, y - 1, WELL_W - 4, h + 2, Plate.ON, Plate.GOLD);
            g.fill(WELL_X + 4, y + 2, WELL_X + 6, y + h - 2, 0xFFF2C47C);
            g.pose().popPose();
            return;
        }
        Paper.cut(g, "ledger_flag", new float[] {252, y - 1, INDEX_X + INDEX_W + 2, y - 1, INDEX_X + INDEX_W + 4, y + h, 256, y + h + 1}, Paper.PINK, Paper.BLACK_LO, 1, 2);
        Paper.pin(g, 260, y + h / 2, 2, false);
        g.pose().popPose();
    }

    // 乗せた行の地 (帯の上)。乗せる行が替わったら小さな音
    public static void hover(GuiGraphics g, int y, int h) {
        track();
        long now = net.minecraft.Util.getMillis();
        if (y != lastHoverY && now - lastHoverFrame < 200) {
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.1f));
        }
        lastHoverY = y;
        lastHoverFrame = now;
        if (UiLook.plate()) {
            // 板の側: 欄の内側を少し明るく、左の端に金の筋
            g.fill(WELL_X + 2, y, WELL_X + WELL_W - 2, y + h, 0x18F2C47C);
            g.fill(WELL_X + 2, y, WELL_X + 4, y + h, 0xFFC48A44);
            return;
        }
        g.fill(INDEX_X - 2, y, INDEX_X + INDEX_W, y + h, 0x30E45A9C);
        g.fill(INDEX_X - 2, y, INDEX_X, y + h, 0xFFE45A9C);
    }

    // 見出しの行 (目録の中の区切り: 字と、右へ伸びる線)
    public static void section(GuiGraphics g, Font font, Component label, int x, int y, int h, int color) {
        Component c = Component.literal(label.getString());
        int tw = Math.min(font.width(c), INDEX_X + INDEX_W - x - 6);
        FitText.draw(g, font, c, x, y + (h - 7) / 2, tw, color, false);
        if (x + tw + 4 < INDEX_X + INDEX_W && UiLook.plate()) {
            Plate.rule(g, x + tw + 4, y + h / 2, INDEX_X + INDEX_W - x - tw - 4);
        } else if (x + tw + 4 < INDEX_X + INDEX_W) {
            g.fill(x + tw + 4, y + h / 2, INDEX_X + INDEX_W, y + h / 2 + 1, Paper.CREAM_LO);
        }
    }

    // 読む頁の題 (桃色の菱・題・添え)
    public static void pageTitle(GuiGraphics g, Font font, Component title, Component sub) {
        track();
        float a = open(0);
        int dx = Math.round((1f - a) * -10f);
        Paper.pin(g, PAGE_X + 2, PAGE_Y + 5, 3, true);
        // 添えが無ければ題は 2 倍 (入れば)。添えがあるときは 1 倍の題と添え (題の線までに収まらない)
        if (sub == null && font.width(title) * 2 <= PAGE_W - 10) {
            jp.revati.skyabyss.rpg.client.skill.SkillStage.big(g, font, title, PAGE_X + 10 + dx, PAGE_Y - 1, PAGE_W - 10, jp.revati.skyabyss.rpg.client.skill.SkillStage.fade(Paper.ON_BLACK_HI, a));
        } else {
            FitText.draw(g, font, title, PAGE_X + 10 + dx, PAGE_Y + 1, PAGE_W - 10, jp.revati.skyabyss.rpg.client.skill.SkillStage.fade(Paper.ON_BLACK_PINK, a), false);
        }
        if (sub != null) {
            FitText.draw(g, font, sub, PAGE_X + 10 + dx, PAGE_Y + 13, PAGE_W - 10, jp.revati.skyabyss.rpg.client.skill.SkillStage.fade(Paper.ON_BLACK_SOFT, a), false);
        }
        if (!UiLook.plate()) {
            g.fill(PAGE_X, PAGE_Y + 26, PAGE_X + PAGE_W, PAGE_Y + 27, Paper.PINK_LO);
        }
    }

    // 下の段の手引き (山の上の字)
    public static void footHint(GuiGraphics g, Font font, Component text, int x, int w) {
        if (UiLook.plate()) {
            // 板の側: 下の段の沈んだ欄の中に、縮めずに 2 行まで折り返す
            int c = 0xFF000000 | Plate.pal().inkSoft;
            java.util.List<net.minecraft.util.FormattedCharSequence> lines = Marquee.joinRest(font.split(text, w), 2);
            int y = lines.size() == 1 ? PLATE_FOOT_Y + (PLATE_FOOT_H - 8) / 2 : PLATE_FOOT_Y + 2;
            for (net.minecraft.util.FormattedCharSequence l : lines) {
                Marquee.draw(g, font, l, x, y, w, c, false);
                y += 10;
            }
            return;
        }
        FitText.draw(g, font, text, x, FOOT_Y + 4, w, ON_PINK, false);
    }

    // 黒い紙の上の字の色を、生成りの帯の上の墨の色へ写す (目録の行の色)
    public static int ink(int rgb) {
        return Paper.inkOf(rgb) & 0xFFFFFF;
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
