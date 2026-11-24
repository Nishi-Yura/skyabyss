package jp.revati.skyabyss.client.trial;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.trial.Trial;
import jp.revati.skyabyss.trial.TrialBoardNet;
import jp.revati.skyabyss.trial.TrialCatalog;
import jp.revati.skyabyss.client.ui.Golden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import jp.revati.skyabyss.client.ui.Paper;

// 祠の画面 (試練の板。docs/design/29 の 2.7・4.2)。祠の祭司に話しかけると開く。暗い石板に画素の枠。四つの頁:
//   いまの三つ : 引いた三つの試練。枠 (歩・智・闘) の絵・名前・類・段・修飾の札。稀は金の縁、極は赤紫の縁。極のうち一つは「？」(入ってから分かる)
//   図鑑       : その巨神の試練ごとに、済んだ回数・任意の目標の数・出会った修飾の数。まだ引いていないものは「？」。称号
//   誓い       : 周回だけの縛り。祠のそばで、試練を進める前だけクリックで立てる / 降ろす (前は碑をしゃがんで右クリックした話の欄)
//   心得       : 祠・試練・誓い・深淵の手引き
// 上の段の右に「仲間と渡る」(祠のそばだけ)。下の段に順路の記録・歌片・称号と、歌片を作る・深淵のつまみ。
// Tab で頁を替える。Esc で閉じる。見せるだけで、決めるのはサーバー
public class TrialBoardScreen extends Screen implements jp.revati.skyabyss.client.ui.TextBounded {
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/trial/icons.png");
    private static final int SHEET_W = 256;
    private static final int SHEET_H = 96;

    private static final int BG = 0x05070F;
    private static final int CARD = 0x16151E;
    private static final int GOLD = 0xF89CD2;
    private static final int TEAL = 0xFFF6EA;
    private static final int TEXT = 0xECE4D6;
    private static final int DIM = 0x9A929C;
    private static final int GOOD = 0x7BD66B;
    private static final int RARE = 0xF89CD2;
    private static final int EXTREME = 0xC25BD6;
    private static final int COMMON = 0x4A6A72;
    private static final int ABYSS = 0xD8343C;

    // 組み方は選ぶ類の「三枚を扇に」(UI の 3 周目で選ぶ A。docs/design/56 の 2.1)。舞台は論理の 408x228:
    // 左上に題・巨神と周回・称号、右の端につまみを縦の短冊で、その下に「仲間と渡る」。中身は左の黒い紙の上 (いまの三つは扇の札)。
    // 下の山の上に順路の記録と歌片、「歌片を作る」と深淵のつまみ
    private static final int W = jp.revati.skyabyss.client.ui.Paper.STAGE_W;
    private static final int H = jp.revati.skyabyss.client.ui.Paper.STAGE_H;
    private static final Golden.Box CONTENT = new Golden.Box(10, 34, 340, 160);
    private static final int ROW = Golden.ROW;
    // 深淵のつまみ・歌片を作る (下の山の上)
    private static final int ABYSS_W = 92;
    private static final int CRAFT_W = 76;
    private static final int FOOT_BTN_Y = 207;
    private static final int CRAFT_X = W - 12 - ABYSS_W - 6 - CRAFT_W;
    private static final int ABYSS_X = W - 12 - ABYSS_W;
    // つまみ (右の端に縦)
    private static final int TAB_X = 350;
    private static final int TAB_W = 48;
    private static final int TAB_STEP = 18;
    private static final int TABS = 5;
    private static final int TAB_Y = 12;
    // 仲間と渡る (つまみの下)
    private static final int GO_X = 350;
    private static final int GO_Y = TAB_Y + TABS * TAB_STEP + 8;
    private static final int GO_W = 50;
    // いまの三つの札
    private static final int CARD_W = 100;
    private static final int CARD_H = 152;
    private static final float[] CARD_TILT = {-5, 0, 4};
    private static final float[][] PEAKS = {{-20, 0}, {18, -12}, {60, -4}, {104, -20}, {150, -8}, {200, -16}, {246, -2}, {290, -22}, {336, -8}, {376, -18}, {428, -6}};
    private static final int[][] DIAMONDS = {{398, 140, 3, 1}, {352, 20, 2, 0}};

    private static int tabY(int t) {
        return TAB_Y + t * TAB_STEP;
    }

    // いまの三つの i 枚目の札の真ん中
    private static int[] cardCenter(int i, boolean lift) {
        return new int[] {CONTENT.x() + 56 + i * 114, CONTENT.y() + 82 + (i == 1 ? -4 : 2) - (lift ? 8 : 0)};
    }

    private int hoverCard(double mx, double my) {
        if (tab != 0) {
            return -1;
        }
        for (int i = 2; i >= 0; i--) {
            int[] c = cardCenter(i, false);
            if (Math.abs(mx - c[0]) <= CARD_W / 2f && my >= c[1] - CARD_H / 2f - 8 && my <= c[1] + CARD_H / 2f) {
                return i;
            }
        }
        return -1;
    }

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private TrialBoardNet.Open data;
    private int tab;
    private int age;
    // 動き (docs/design/56 の 2.1「動き」): 開くと題が入り、札が配られ、つまみが右から順に入る。頁を替えると中身が滑りこむ
    private final long openedAt = net.minecraft.Util.getMillis();
    private long tabAt = openedAt;
    private int lastHot = -1;
    // 仲間と渡る: 一度目で構え (残りの帯が減る)、二度目で渡る
    private int goArmed;
    private static final int GO_TICKS = 60;
    // 誓いを立てた / 降ろした手応え (その行の菱に判)
    private java.util.Map<String, Boolean> vowSeen;
    private final java.util.Map<String, Long> vowAt = new java.util.HashMap<>();

    private float since(long delay, float ms) {
        return (net.minecraft.Util.getMillis() - openedAt - delay) / ms;
    }

    private void setTab(int t) {
        if (t != tab) {
            tab = t;
            tabAt = net.minecraft.Util.getMillis();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f, 0.8f));
        }
    }

    // 撮影用: 頁を選んで開く
    // demo for shots: a card shown as hovered, or the go button armed
    private int demoHot = -1;

    public TrialBoardScreen demoHover(int i) {
        demoHot = i;
        return this;
    }

    public TrialBoardScreen demoArmed() {
        goArmed = 1000;
        return this;
    }

    public TrialBoardScreen page(int t) {
        tab = t;
        return this;
    }

    public TrialBoardScreen(TrialBoardNet.Open data) {
        super(Component.translatable("gui.skyabyss.trial.title"));
        this.data = data;
    }

    public void update(TrialBoardNet.Open open) {
        this.data = open;
    }


    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
        if (goArmed > 0) {
            goArmed--;
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_RIGHT) {
            setTab((tab + 1) % TABS);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            setTab((tab + TABS - 1) % TABS);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + TABS) {
            setTab(key - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        if (button == 0) {
            for (int t = 0; t < TABS; t++) {
                if (mx >= TAB_X && mx < TAB_X + TAB_W && my >= tabY(t) && my < tabY(t) + ROW) {
                    setTab(t);
                    return true;
                }
            }
            // 仲間と渡る (祠のそばだけ。そろっているかはサーバーが確かめる。数え下ろしが見えるように画面を閉じる)
            if (data.atShrine() && mx >= GO_X && mx < GO_X + GO_W && my >= GO_Y && my < GO_Y + ROW) {
                if (goArmed <= 0) {
                    goArmed = GO_TICKS;
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.6f));
                    return true;
                }
                goArmed = 0;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TrialBoardNet.ShrineGo(data.titan()));
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.6f, 0.5f));
                onClose();
                return true;
            }
        }
        if (button == 0 && my >= FOOT_BTN_Y && my < FOOT_BTN_Y + ROW) {
            // 歌片を作る (祠の炉。足りるかはサーバーが確かめる)
            if (mx >= CRAFT_X && mx < CRAFT_X + CRAFT_W) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TrialBoardNet.CraftShard(data.titan()));
                return true;
            }
            // 深淵のつまみ (物語を終えた人だけ押せる。グループごと。サーバーが確かめる)
            if (mx >= ABYSS_X && mx < ABYSS_X + ABYSS_W) {
                if (data.abyssOpen()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TrialBoardNet.AbyssToggle(data.titan(), !data.abyssOn()));
                }
                return true;
            }
        }
        // 誓いの行 (選べるときだけ)
        if (button == 0 && tab == 2 && data.vowClosed().isEmpty()) {
            int i = vowRowAt(mx, my);
            if (i >= 0) {
                TrialBoardNet.VowLine v = data.vows().get(i);
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TrialBoardNet.VowChange(data.titan(), v.id(), !v.on()));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ---------------------------------------------------------------- 絵
    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, int line, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF03050A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, argb(1f, line));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, argb(1f, fill));
        g.fill(x + 2, y + 2, x + w - 2, y + 3, argb(0.10f, 0xFFFFFF));
        g.fill(x + 2, y + 2, x + 3, y + h - 2, argb(0.06f, 0xFFFFFF));
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, argb(0.35f, 0x000000));
    }

    // 絵の番号 (icons.png。8 列 × 3 行、32 px)
    private static final int I_WALK = 0, I_WIT = 1, I_FIGHT_SLOT = 2, I_LAYOUT = 3, I_NUMBER = 4, I_ENEMY = 5, I_HAZARD = 6, I_GOAL = 7;
    private static final int I_KIND = 8, I_QUIET = 15, I_EXTREME = 16, I_UNKNOWN = 17;

    private void icon(GuiGraphics g, int index, int x, int y, int size, float alpha) {
        int u = (index % 8) * 32;
        int v = (index / 8) * 32;
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(ICONS, x, y, size, size, u, v, 32, 32, SHEET_W, SHEET_H);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static int slotIcon(Trial.Slot s) {
        return switch (s) {
            case WALK -> I_WALK;
            case WIT -> I_WIT;
            case FIGHT -> I_FIGHT_SLOT;
        };
    }

    private static int rarityColor(int r) {
        return r >= 3 ? ABYSS : r == 2 ? EXTREME : r == 1 ? RARE : COMMON;
    }

    static String stars(int tier) {
        return jp.revati.skyabyss.trial.TrialChips.stars(tier);
    }

    // 札の文
    static Component chipText(TrialBoardNet.Chip c) {
        return jp.revati.skyabyss.trial.TrialChips.text(c);
    }

    static int chipIcon(TrialBoardNet.Chip c) {
        if (c.hidden()) {
            return I_UNKNOWN;
        }
        return switch (c.type()) {
            case "layout" -> I_LAYOUT;
            case "number" -> I_NUMBER;
            case "enemy" -> I_ENEMY;
            case "hazard" -> I_HAZARD;
            case "goal" -> I_GOAL;
            case "extreme", "abyss" -> I_EXTREME;
            case "quiet" -> I_QUIET;
            default -> I_UNKNOWN;
        };
    }

    @Override
    public void render(GuiGraphics g, int screenX, int screenY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        jp.revati.skyabyss.client.ui.Paper.dim(g, width, height);
        // 舞台は窓に合わせて一様に大きく描く (描くのと当たりは同じ変換。画素ちょうど)
        fit.fit(width, height, W, H, 3f, 0.5f);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        jp.revati.skyabyss.client.ui.Paper.stage(g, fit, width, height, "trial", PEAKS, DIAMONDS);
        // 題・巨神と周回・称号 (左上)
        // 題: 小さな題 (巨神の祠・周目) と、2 倍の巨神の名。称号は名の右に判で (無ければ出さない)
        float h0 = jp.revati.skyabyss.rpg.client.skill.SkillStage.out(since(0, 240));
        jp.revati.skyabyss.client.ui.Paper.pin(g, 14, 12, 3, true);
        Component head = Component.translatable("gui.skyabyss.trial.title").copy().append("  ").append(Component.translatable("gui.skyabyss.trial.cycle", data.cycle()));
        g.drawString(font, head, 22 + Math.round((1f - h0) * -8f), 8, jp.revati.skyabyss.rpg.client.skill.SkillStage.fade(GOLD, h0), false);
        Component titan = Component.translatable(TrialCatalog.titanKey(data.titan()));
        int nx = 22 + Math.round((1f - h0) * -12f);
        jp.revati.skyabyss.rpg.client.skill.SkillStage.big(g, font, titan, nx, 18, 200, jp.revati.skyabyss.rpg.client.skill.SkillStage.fade(0xFFF6EA, h0));
        int sx = nx + font.width(titan) * 2 + 8;
        for (int i = 0; i < data.titles().size() && sx < 330; i++) {
            Component t = Component.translatable("trial.skyabyss.title." + data.titles().get(i));
            sx += jp.revati.skyabyss.rpg.client.skill.SkillStage.stamp(g, font, t, sx, 20, jp.revati.skyabyss.rpg.client.skill.SkillStage.clamp01(since(140 + i * 60L, 260)), i == 0) + 4;
        }
        // 頁 (右の端の縦の短冊。選んだ頁は左へ飛び出す)
        String[] tabs = {"gui.skyabyss.trial.tab.now", "gui.skyabyss.trial.tab.codex", "gui.skyabyss.trial.tab.vow", "gui.skyabyss.trial.tab.rewards", "gui.skyabyss.trial.tab.how"};
        for (int t = 0; t < TABS; t++) {
            boolean hover = mouseX >= TAB_X && mouseX < TAB_X + TAB_W && mouseY >= tabY(t) && mouseY < tabY(t) + ROW;
            // 開くと右から 35ms ずつ遅れて入る
            int in = Math.round((1f - jp.revati.skyabyss.rpg.client.skill.SkillStage.out(since(60 + t * 35L, 220))) * 40f);
            jp.revati.skyabyss.client.ui.Paper.tab(g, font, TAB_X - (tab == t ? 6 : 0) + in, tabY(t), TAB_W, ROW, Component.translatable(tabs[t]), tab == t, hover);
        }
        if (data.atShrine()) {
            renderGo(g, font, mouseX, mouseY);
        }
        // 頁の中身は替えると右から 8 だけ滑りこむ (180ms)
        int slide = Math.round((1f - jp.revati.skyabyss.rpg.client.skill.SkillStage.out((net.minecraft.Util.getMillis() - tabAt) / 180f)) * 8f);
        g.pose().pushPose();
        g.pose().translate(tab == 0 ? 0 : slide, 0, 0);
        switch (tab) {
            case 0 -> renderNow(g, font, mouseX, mouseY);
            case 1 -> renderCodex(g, font);
            case 2 -> renderVows(g, font, mouseX, mouseY);
            case 3 -> renderRewards(g, font, mouseX, mouseY);
            default -> renderHow(g, font);
        }
        g.pose().popPose();
        // 足: 順路の記録・歌片と、歌片を作る・深淵のつまみ
        renderFoot(g, font);
        renderAbyssToggle(g, font, ABYSS_X, FOOT_BTN_Y, mouseX, mouseY);
        renderCraft(g, font, CRAFT_X, FOOT_BTN_Y, mouseX, mouseY);
        fit.end(g);
    }

    // 仲間と渡る (主の釦。祠の祭司の「仲間と渡る」と同じ)
    private void renderGo(GuiGraphics g, Font font, int mx, int my) {
        boolean hover = mx >= GO_X && mx < GO_X + GO_W && my >= GO_Y && my < GO_Y + ROW;
        boolean armed = goArmed > 0;
        jp.revati.skyabyss.client.ui.Paper.button(g, font, GO_X, GO_Y, GO_W, ROW, Component.translatable(armed ? "gui.skyabyss.shrine.go_confirm" : "gui.skyabyss.shrine.go"), true,
                jp.revati.skyabyss.client.ui.Paper.state(true, hover));
        if (armed) {
            // 二度押しの待ち: 釦の下の端に、減っていく生成りの帯
            float f = Math.min(1f, Math.max(0f, (goArmed - Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) / GO_TICKS));
            g.fill(GO_X + 3, GO_Y + ROW - 3, GO_X + 3 + Math.round((GO_W - 6) * f), GO_Y + ROW - 1, 0xFF000000 | 0xFFF6EA);
        }
        if (hover && !armed) {
            g.renderTooltip(font, font.split(Component.translatable("gui.skyabyss.shrine.go_tip"), 200), mx, my);
        }
    }

    // 誓いの頁: 一行目に段と素材の倍率 (選べないときは理由)、その下に誓いを一つずつ (立てているものは金)
    private static final int VOW_TOP = CONTENT.y() + ROW + Golden.S5;
    private static final int VOW_ROW = ROW + Golden.S5;
    private static final int SOFT_DESC = 0x9A929C;

    private int vowRowAt(double mx, double my) {
        if (mx < CONTENT.x() || mx >= CONTENT.right() || my < VOW_TOP) {
            return -1;
        }
        int i = (int) ((my - VOW_TOP) / VOW_ROW);
        return i < data.vows().size() && VOW_TOP + (i + 1) * VOW_ROW <= CONTENT.bottom() ? i : -1;
    }

    private void renderVows(GuiGraphics g, Font font, int mx, int my) {
        int x0 = CONTENT.x() + Golden.S5;
        int level = 0;
        for (TrialBoardNet.VowLine v : data.vows()) {
            if (v.on()) {
                level += v.points();
            }
        }
        Component head = data.vowClosed().isEmpty()
                ? Component.translatable("gui.skyabyss.shrine.vow_head", level, Math.round(jp.revati.skyabyss.titan.vow.Vows.multiplier(level) * 100),
                        jp.revati.skyabyss.titan.vow.Vows.MAX_VOWS, data.vowBest())
                : Component.translatable(data.vowClosed());
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, head, x0, Golden.textY(CONTENT.y(), ROW), CONTENT.w() - Golden.S13,
                argb(1f, data.vowClosed().isEmpty() ? GOLD : Golden.DENY), false);
        boolean open = data.vowClosed().isEmpty();
        int hoverRow = open ? vowRowAt(mx, my) : -1;
        long nowV = net.minecraft.Util.getMillis();
        if (vowSeen != null) {
            for (TrialBoardNet.VowLine v : data.vows()) {
                Boolean was = vowSeen.get(v.id());
                if (was != null && was != v.on()) {
                    vowAt.put(v.id(), nowV);
                }
            }
        }
        vowSeen = new java.util.HashMap<>();
        for (TrialBoardNet.VowLine v : data.vows()) {
            vowSeen.put(v.id(), v.on());
        }
        for (int i = 0; i < data.vows().size(); i++) {
            TrialBoardNet.VowLine v = data.vows().get(i);
            int y = VOW_TOP + i * VOW_ROW;
            if (y + VOW_ROW > CONTENT.bottom()) {
                break;
            }
            boolean hover = i == hoverRow;
            g.fill(CONTENT.x(), y, CONTENT.right(), y + VOW_ROW - 2, argb(1f, v.on() ? 0x3A1A2E : hover ? 0x24202E : CARD));
            Paper.edge(g, CONTENT.x(), y, CONTENT.right(), y + VOW_ROW - 2, argb(1f, v.on() ? GOLD : hover ? TEAL : 0x2C2834));
            Component name = Component.translatable("vow.skyabyss." + v.id()).copy().append(Component.literal("  +" + v.points()));
            int ty = Golden.textY(y, VOW_ROW - 2);
            // 立てた誓いは桃色の菱。立てた / 降ろした瞬間は菱が大きく浮いて押される
            Long t0 = vowAt.get(v.id());
            float press = t0 == null ? 1f : jp.revati.skyabyss.rpg.client.skill.SkillStage.back((nowV - t0) / 220f);
            float sc = 1f + (1f - press) * 0.8f;
            g.pose().pushPose();
            g.pose().translate(x0 + 3, ty + 4, 0);
            g.pose().scale(sc, sc, 1f);
            boolean on = v.on();
            jp.revati.skyabyss.client.ui.Paper.draw(g, "vow_mark_" + (on ? 1 : 0), "", -3, -3, 7, 7, sh -> sh.diamond(3, 3, 3, on ? jp.revati.skyabyss.client.ui.Paper.PINK : jp.revati.skyabyss.client.ui.Paper.EDGE, on ? jp.revati.skyabyss.client.ui.Paper.PINK_HI : jp.revati.skyabyss.client.ui.Paper.GREY_LO));
            g.pose().popPose();
            g.drawString(font, name, x0 + 11, ty, argb(1f, v.on() ? GOLD : open ? TEXT : DIM), false);
            int dx = x0 + Golden.S89 + Golden.S13;
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable("vow.skyabyss." + v.id() + ".desc"), dx, ty,
                    CONTENT.right() - Golden.S5 - dx, argb(1f, SOFT_DESC), false);
        }
        // 指した誓いの説明を全部 (行では「…」で切れる)。選べるときは押し方も
        int at = vowRowAt(mx, my);
        if (at >= 0) {
            java.util.List<FormattedCharSequence> tip = new java.util.ArrayList<>(font.split(Component.translatable("vow.skyabyss." + data.vows().get(at).id() + ".desc"), 220));
            if (open) {
                tip.add(Component.translatable("gui.skyabyss.shrine.vow_tip").withStyle(st -> st.withColor(TEAL)).getVisualOrderText());
            }
            g.renderTooltip(font, tip, mx, my);
        }
    }

    // 心得の頁: 祠・試練・誓い・深淵を、見出しと折り返した文で
    // 報い: この祠から出るアスペクト (今の職の分。共通のレア・エピックと、この祠だけのミシック)。持っている Lv と、出る割合・ミシックまでの回数
    private void renderRewards(GuiGraphics g, Font font, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        jp.revati.skyabyss.rpg.skill.SkillData d = jp.revati.skyabyss.rpg.skill.SkillManager.get(mc.player);
        jp.revati.skyabyss.rpg.skill.Job job = jp.revati.skyabyss.rpg.skill.Job.byId(d.curJob);
        int x0 = CONTENT.x() + Golden.S5;
        int w = CONTENT.w() - Golden.S13;
        int y = CONTENT.y() + 2;
        Component head = Component.translatable("gui.skyabyss.aspect.pool_head",
                job == null ? Component.literal("-") : Component.translatable(job.nameKey()));
        g.drawString(font, head, x0, y, argb(1f, GOLD), false);
        Component pity = Component.translatable("gui.skyabyss.aspect.pool_pity", Math.max(1, jp.revati.skyabyss.rpg.aspect.Aspects.PITY - d.aspectPity + 1));
        g.drawString(font, pity, x0 + w - font.width(pity), y, argb(1f, TEAL), false);
        y += 11;
        for (FormattedCharSequence line : font.split(Component.translatable("gui.skyabyss.aspect.pool_rates"), w)) {
            g.drawString(font, line, x0, y, argb(1f, DIM), false);
            y += 10;
        }
        y += 3;
        if (job == null) {
            return;
        }
        // 二段の棚 (どの祠でも・この祠だけ。一段 10 品)。品は K 画面のアスペクトの頁と同じ札 (持っていない品は「？」で伏せる)
        java.util.List<jp.revati.skyabyss.rpg.aspect.Aspect> pool = jp.revati.skyabyss.rpg.aspect.Aspects.pool(job, data.titan());
        int tile = 20;
        int per = 10;
        int gap = Math.max(2, Math.min(Golden.S8, (w - per * tile) / (per - 1)));
        int rowW = per * tile + (per - 1) * gap;
        int tx0 = x0 + Math.max(0, (w - rowW) / 2);
        java.util.List<Component> tip = null;
        for (String grp : new String[] {"", data.titan()}) {
            java.util.List<jp.revati.skyabyss.rpg.aspect.Aspect> list = new java.util.ArrayList<>();
            for (jp.revati.skyabyss.rpg.aspect.Aspect a : pool) {
                if (a.shrine().equals(grp)) {
                    list.add(a);
                }
            }
            list.sort(java.util.Comparator.comparingInt(jp.revati.skyabyss.rpg.aspect.Aspect::rarity));
            if (y + 10 + tile > CONTENT.bottom()) {
                break;
            }
            g.drawString(font, jp.revati.skyabyss.rpg.client.skill.SkillScreenAccess.groupName(grp), tx0, y, argb(1f, TEXT), false);
            y += 11;
            for (int i = 0; i < list.size(); i++) {
                jp.revati.skyabyss.rpg.aspect.Aspect a = list.get(i);
                int cx = tx0 + (i % per) * (tile + gap);
                int cy = y + (i / per) * (tile + 6);
                int lv = jp.revati.skyabyss.rpg.aspect.Aspects.level(d, a);
                boolean hover = mx >= cx && mx < cx + tile && my >= cy && my < cy + tile;
                if (lv <= 0) {
                    // まだ持っていない品: 沈んだ黒い升と小さな菱 (「？」は並ぶとうるさい。名残のページと同じ)
                    g.fill(cx + 1, cy + 1, cx + tile - 1, cy + tile - 1, hover ? jp.revati.skyabyss.client.ui.Paper.EDGE : jp.revati.skyabyss.client.ui.Paper.BLACK_LO);
                    int r = hover ? 2 : 1;
                    int col = hover ? jp.revati.skyabyss.client.ui.Paper.GREY : jp.revati.skyabyss.client.ui.Paper.GREY_LO;
                    for (int k = -r; k <= r; k++) {
                        int hw = r - Math.abs(k);
                        g.fill(cx + tile / 2 - hw, cy + tile / 2 + k, cx + tile / 2 + hw + 1, cy + tile / 2 + k + 1, col);
                    }
                } else {
                    jp.revati.skyabyss.rpg.client.skill.SkillScreenAccess.aspectTile(g, font, cx, cy, tile, a, lv, hover, i);
                }
                if (hover) {
                    tip = jp.revati.skyabyss.rpg.client.skill.SkillScreenAccess.aspectTip(d, a);
                }
            }
            y += ((list.size() + per - 1) / per) * (tile + 6) + 3;
        }
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    private void renderHow(GuiGraphics g, Font font) {
        int x0 = CONTENT.x() + Golden.S5;
        int w = CONTENT.w() - Golden.S13;
        int y = CONTENT.y() + 2;
        String[][] parts = {
                {"gui.skyabyss.shrine.how_shrine", "gui.skyabyss.shrine.how_shrine_text"},
                {"gui.skyabyss.shrine.how_trial", "gui.skyabyss.shrine.how_trial_text"},
                {"gui.skyabyss.shrine.how_vow", "gui.skyabyss.shrine.how_vow_text"},
                {"gui.skyabyss.shrine.how_abyss", "gui.skyabyss.shrine.how_abyss_text"}};
        // 見出し (金) に続けて文 (白) を折り返す。四つが板に収まるように一続きで
        for (String[] part : parts) {
            // 見出しは桃色の菱と一行、文はその下に字下げして (一続きの字の壁にしない)
            if (y + 9 > CONTENT.bottom()) {
                return;
            }
            jp.revati.skyabyss.client.ui.Paper.pin(g, x0 + 3, y + 4, 2, true);
            g.drawString(font, Component.translatable(part[0]), x0 + 10, y, argb(1f, GOLD), false);
            y += 11;
            for (FormattedCharSequence line : font.split(Component.translatable(part[1]), w - 10)) {
                if (y + 9 > CONTENT.bottom()) {
                    return;
                }
                g.drawString(font, line, x0 + 10, y, argb(1f, TEXT), false);
                y += 10;
            }
            y += Golden.S5;
        }
    }

    // 歌片を作るボタン (祠の東の縁の共鳴の炉)
    private void renderCraft(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        boolean hover = mx >= x && mx < x + CRAFT_W && my >= y && my < y + ROW;
        jp.revati.skyabyss.client.ui.Paper.button(g, font, x, y, CRAFT_W, ROW, Component.translatable("gui.skyabyss.shrine.craft"), false,
                jp.revati.skyabyss.client.ui.Paper.state(true, hover));
        if (hover) {
            g.renderTooltip(font, Component.translatable("gui.skyabyss.shrine.craft_tip", jp.revati.skyabyss.trial.TrialBoard.SHARD_MATERIALS,
                    jp.revati.skyabyss.trial.TrialBoard.SHARD_RESONANCE), mx, my);
        }
    }

    // 深淵のつまみ: 閉じている (転生 3 回未満) は灰の平らな紙、選んでいないは生成り、選んでいるは桃色
    private void renderAbyssToggle(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        boolean open = data.abyssOpen();
        boolean on = data.abyssOn();
        boolean hover = open && mx >= x && mx < x + ABYSS_W && my >= y && my < y + ROW;
        String key = !open ? "gui.skyabyss.trial.abyss.locked" : on ? "gui.skyabyss.trial.abyss.on" : "gui.skyabyss.trial.abyss.off";
        jp.revati.skyabyss.client.ui.Paper.button(g, font, x, y, ABYSS_W, ROW, Component.translatable(key), on, jp.revati.skyabyss.client.ui.Paper.state(open, hover));
        if (hover) {
            g.renderTooltip(font, Component.translatable("gui.skyabyss.trial.abyss.tip"), mx, my);
        }
    }

    // いまの三つ: 三枚の背の高い札を扇に (乗せた札は桃色で持ち上がる)。上から 枠の絵と名前・類と段・説明・修飾 (一つずつ一行の紙)・状態。
    // 稀さは札の右上の菱 (稀 = 灰・極 = 桃色・深淵 = 大きな桃色)
    private void renderNow(GuiGraphics g, Font font, int mx, int my) {
        int hot = hoverCard(mx, my);
        if (hot < 0) {
            hot = demoHot;
        }
        if (hot != lastHot && hot >= 0) {
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.12f));
        }
        lastHot = hot;
        int i = 0;
        for (TrialBoardNet.Card card : data.cards()) {
            Trial.Def def = TrialCatalog.get(card.cellId());
            if (def == null || i >= 3) {
                continue;
            }
            boolean lift = i == hot;
            int[] c = cardCenter(i, lift);
            // 配る: 一枚ずつ 60ms 遅れて、下から少し回りながら入り、行き過ぎて止まる
            float deal = jp.revati.skyabyss.rpg.client.skill.SkillStage.back(since(90 + i * 60L, 260));
            if (deal < 0.12f) {
                i++;
                continue;
            }
            g.pose().pushPose();
            g.pose().translate(c[0], c[1] + (1f - deal) * 60f, 0);
            if (!jp.revati.skyabyss.client.ui.UiLook.plate()) {
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((1f - deal) * (i - 1) * 10f));
            }
            g.pose().translate(-c[0], -c[1], 0);
            jp.revati.skyabyss.client.ui.Paper.cut(g, "trial_card" + i, jp.revati.skyabyss.client.ui.Paper.rotRect(c[0], c[1], CARD_W, CARD_H, CARD_TILT[i]),
                    lift ? jp.revati.skyabyss.client.ui.Paper.PINK : jp.revati.skyabyss.client.ui.Paper.CREAM,
                    lift ? jp.revati.skyabyss.client.ui.Paper.BLACK_LO : jp.revati.skyabyss.client.ui.Paper.PINK_LO, 2, 4);
            if (card.rarity() >= 1) {
                jp.revati.skyabyss.client.ui.Paper.pin(g, c[0] + CARD_W / 2 - 8, c[1] - CARD_H / 2 + 4, card.rarity() >= 3 ? 5 : 3, card.rarity() >= 2 && !lift);
            }
            i++;
            int ink = lift ? 0xFFF6EA : jp.revati.skyabyss.client.ui.Paper.INK;
            int head = lift ? 0xFFF6EA : jp.revati.skyabyss.client.ui.Paper.INK_HEAD;
            int soft = lift ? 0xFFF6EA : jp.revati.skyabyss.client.ui.Paper.INK_SOFT;
            int tx = c[0] - CARD_W / 2 + 8;
            int tw = CARD_W - 16;
            int y = c[1] - CARD_H / 2 + 6;
            // 枠の絵と名前
            icon(g, slotIcon(def.slot()), tx, y + 1, 12, 1f);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal(Component.translatable(def.nameKey()).getString()), tx + 15, Golden.textY(y, ROW), tw - 15 - 8, argb(1f, head), false);
            // 類と段
            int ky = y + ROW;
            icon(g, I_KIND + def.kind().ordinal(), tx, ky + 1, 12, 1f);
            Component kind = Component.literal(Component.translatable("trial.skyabyss.kind." + def.kind().id()).getString());
            String st = stars(def.tier());
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, kind, tx + 15, Golden.textY(ky, ROW), tw - 15 - font.width(st) - 4, argb(1f, ink), false);
            g.drawString(font, st, tx + tw - font.width(st), Golden.textY(ky, ROW), argb(1f, head), false);
            // 説明: 修飾の札と状態の行を残して入るだけ。入りきらなければ最後の行を横へ流す
            int dy = ky + ROW + 3;
            int bottomRow = c[1] + CARD_H / 2 - 4 - ROW;
            int chipsH = card.chips().isEmpty() ? ROW : card.chips().size() * 16;
            int maxLines = Math.max(2, (bottomRow - 3 - chipsH - dy) / ROW);
            List<FormattedCharSequence> dl = jp.revati.skyabyss.client.ui.Marquee.joinRest(
                    jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.literal(Component.translatable(def.descKey()).getString()), tw), maxLines);
            int shown = dl.size();
            for (int l = 0; l < shown; l++) {
                jp.revati.skyabyss.client.ui.Marquee.draw(g, font, dl.get(l), tx, Golden.textY(dy + l * ROW, ROW), tw, argb(1f, soft), false);
            }
            // 修飾の紙 (一つずつ一行。稀以上は桃色の紙)
            int cy = dy + shown * ROW + 3;
            if (card.chips().isEmpty()) {
                g.drawString(font, Component.translatable("trial.skyabyss.chip.plain"), tx, Golden.textY(cy, ROW), argb(1f, soft), false);
            }
            int k = 0;
            for (TrialBoardNet.Chip chip : card.chips()) {
                if (cy + 14 > bottomRow) {
                    break;
                }
                boolean rare = chip.rarity() >= 1;
                int body = lift ? jp.revati.skyabyss.client.ui.Paper.CREAM : rare ? jp.revati.skyabyss.client.ui.Paper.PINK : jp.revati.skyabyss.client.ui.Paper.CREAM_LO;
                jp.revati.skyabyss.client.ui.Paper.cut(g, "trial_chip" + (i - 1) + "_" + k++, new float[] {tx, cy, tx + tw, cy - 1, tx + tw - 1, cy + 14, tx + 1, cy + 14}, body, 0, 0, 0);
                icon(g, chipIcon(chip), tx + 2, cy + 1, 12, 1f);
                jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.literal(chipText(chip).getString()), tx + 16, cy + 3, tw - 18,
                        argb(1f, rare && !lift ? 0xFFF6EA : jp.revati.skyabyss.client.ui.Paper.INK), false);
                cy += 14 + 2;
            }
            // 状態 (下の端): 左に図鑑の印、右に今回済み
            String badge = switch (card.state()) {
                case 0 -> "gui.skyabyss.trial.state.new";
                case 2 -> "gui.skyabyss.trial.state.cleared";
                case 3 -> "gui.skyabyss.trial.state.all";
                default -> null;
            };
            if (badge != null) {
                g.drawString(font, Component.translatable(badge), tx, Golden.textY(bottomRow, ROW), argb(1f, head), false);
            }
            if (card.clearedNow()) {
                Component done = Component.translatable("gui.skyabyss.trial.cleared_now");
                g.drawString(font, done, tx + tw - font.width(done), Golden.textY(bottomRow, ROW), argb(1f, head), false);
            }
            // 札の絵: 修飾の下が空いていれば、枠の絵を大きく (札ごとに何の試練か一目で)
            if (cy + 6 <= bottomRow - 34) {
                icon(g, slotIcon(def.slot()), c[0] - 16, bottomRow - 36, 32, 1f);
            }
            g.pose().popPose();
        }
    }

    // 図鑑: 行 13 (1 行おきに地の色)
    private void renderCodex(GuiGraphics g, Font font) {
        int x0 = CONTENT.x() + Golden.S5;
        int y = CONTENT.y() + 2;
        int row = 0;
        for (TrialBoardNet.Row r : data.codex()) {
            Trial.Def def = TrialCatalog.get(r.cellId());
            if (def == null || y + ROW > CONTENT.bottom() - 2) {
                continue;
            }
            if (row % 2 == 0) {
                Golden.stripe(g, new Golden.Box(CONTENT.x() + 1, y, CONTENT.w() - 2, ROW));
            }
            int ty = Golden.textY(y, ROW);
            icon(g, slotIcon(def.slot()), x0, y, 12, r.seen() ? 1f : 0.35f);
            Component name = r.seen() ? Component.translatable(def.nameKey()) : Component.translatable("trial.skyabyss.unknown");
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, name, x0 + 16, ty, 120, argb(1f, r.seen() ? GOLD : DIM), false);
            if (r.seen()) {
                icon(g, I_KIND + def.kind().ordinal(), x0 + 140, y, 12, 1f);
                g.drawString(font, stars(def.tier()), x0 + 156, ty, argb(1f, GOLD), false);
                Component n = Component.translatable("gui.skyabyss.trial.codex.cleared", r.cleared());
                g.drawString(font, n, x0 + 196, ty, argb(1f, r.cleared() > 0 ? GOOD : DIM), false);
                if (r.modsTotal() > 0) {
                    int bx = x0 + 252;
                    int bw = Golden.S34 + Golden.S8;
                    g.fill(bx, y + 5, bx + bw, y + 8, 0xFF0E0D14);
                    g.fill(bx, y + 5, bx + Math.min(bw, bw * r.modsSeen() / r.modsTotal()), y + 8, argb(1f, TEAL));
                    g.drawString(font, r.modsSeen() + "/" + r.modsTotal(), bx + bw + 3, ty, argb(1f, DIM), false);
                }
                if (r.goals() > 0) {
                    icon(g, I_GOAL, CONTENT.right() - Golden.S5 - 12, y, 12, 1f);
                }
            }
            y += ROW;
            row++;
        }
        if (row == 0) {
            g.drawString(font, Component.translatable("gui.skyabyss.trial.codex.empty"), x0, Golden.textY(y, ROW), argb(1f, DIM), false);
        }
    }

    // 下の段 (山の上): 順路の記録と歌片
    private void renderFoot(GuiGraphics g, Font font) {
        int x = 14;
        int y = FOOT_BTN_Y + 3;
        // 順路の記録と歌片 (docs/design/32 の 6 章)
        long best = data.routeBest() / 20;
        Component record = data.routeClears() <= 0 ? Component.translatable("gui.skyabyss.shrine.record_none")
                : Component.translatable("gui.skyabyss.shrine.record", data.routeClears(), String.format("%d:%02d", best / 60, best % 60));
        // 歌片: 小さな字と 2 倍の数 (渡るのに要るもの)
        Component sl = Component.translatable("gui.skyabyss.shrine.shards_label");
        String num = String.valueOf(data.shards());
        int sw = font.width(sl) + 4 + font.width(num) * 2;
        int room = CRAFT_X - 8 - x;
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, record, x, y, room - sw - 10, argb(1f, TEAL), false);
        int lx = x + room - sw;
        g.drawString(font, sl, lx, y, argb(1f, TEAL), false);
        g.pose().pushPose();
        g.pose().translate(lx + font.width(sl) + 4, y - 5, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, num, 0, 0, argb(1f, data.shards() > 0 ? 0xFFF6EA : DIM), false);
        g.pose().popPose();
    }

    @Override
    public int[] textBounds() {
        // 文字の範囲は描くときと同じ変換を通した画面の矩形 (論理の座標のままだと、倍率が 1 でない GUI で字が切れる)
        fit.fit(width, height, W, H, 3f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}
