package jp.revati.skyabyss.client.trial;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.ui.TextStyle;
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

    private static final int BG = 0x07090E;
    private static final int PANEL = 0x10131A;
    private static final int CARD = 0x171B25;
    private static final int GOLD = 0xF2C14E;
    private static final int TEAL = 0x7FD6D0;
    private static final int TEXT = 0xE6E8EE;
    private static final int DIM = 0x8C98A4;
    private static final int GOOD = 0x7BD66B;
    private static final int RARE = 0xF2C14E;
    private static final int EXTREME = 0xC25BD6;
    private static final int COMMON = 0x4A6A72;
    private static final int ABYSS = 0xD8343C;

    // 板は黄金比の大きい板 (client/ui/Golden。docs/design/56): 中身の一行目に頁の札・歌片を作る・深淵のつまみ、その下に
    // いまの三つ (同じ幅の三列の札) か図鑑 (行 13)、下の段 (34) に順路の記録・歌片・称号・手引き
    private static final Golden.Layout LAYOUT = Golden.Layout.large(Golden.FOOT2);
    private static final Golden.Box BODY = LAYOUT.body();
    private static final Golden.Box CONTENT = BODY.afterTop(Golden.ROW);
    private static final int W = Golden.L_W;
    private static final int H = Golden.L_H;
    private static final int ROW = Golden.ROW;

    private static final int ABYSS_W = 118;
    private static final int CRAFT_W = Golden.S89;
    // 頁は五つ (いま・図鑑・誓い・報い・手引き)。札の幅は四つのときの並びと同じ長さに収める
    private static final int TAB_W = 44;
    private static final int TABS = 5;
    private static final int TOOL_Y = BODY.y();
    // 仲間と渡る (上の段の右。頁の札の右から板の右まで)
    private static final int GO_X = BODY.x() + TABS * (TAB_W + Golden.S3) + Golden.S5;
    private static final int GO_W = BODY.right() - GO_X;
    // 下の段の二行目: 歌片を作る・深淵のつまみ (右寄せ)
    private static final int FOOT_BTN_Y = LAYOUT.foot().y() + 4 + ROW;

    private final jp.revati.skyabyss.client.ui.UiFit fit = new jp.revati.skyabyss.client.ui.UiFit();
    private TrialBoardNet.Open data;
    private int tab;
    private int age;

    public TrialBoardScreen(TrialBoardNet.Open data) {
        super(Component.translatable("gui.skyabyss.trial.title"));
        this.data = data;
    }

    public void update(TrialBoardNet.Open open) {
        this.data = open;
    }

    // 試験・撮影のため
    public void selectTab(int t) {
        tab = Mth.clamp(t, 0, TABS - 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        age++;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_RIGHT) {
            tab = (tab + 1) % TABS;
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            tab = (tab + TABS - 1) % TABS;
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + TABS) {
            tab = key - GLFW.GLFW_KEY_1;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mx = fit.x(screenX);
        double my = fit.y(screenY);
        int px = 0;
        int py = 0;
        if (button == 0 && my >= TOOL_Y && my < TOOL_Y + ROW) {
            for (int t = 0; t < TABS; t++) {
                int tx = BODY.x() + t * (TAB_W + Golden.S3);
                if (mx >= tx && mx < tx + TAB_W) {
                    tab = t;
                    return true;
                }
            }
            // 仲間と渡る (祠のそばだけ。そろっているかはサーバーが確かめる。数え下ろしが見えるように画面を閉じる)
            if (data.atShrine() && mx >= GO_X && mx < GO_X + GO_W) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TrialBoardNet.ShrineGo(data.titan()));
                onClose();
                return true;
            }
        }
        if (button == 0 && my >= FOOT_BTN_Y && my < FOOT_BTN_Y + ROW) {
            // 歌片を作る (祠の炉。足りるかはサーバーが確かめる)
            int cx = BODY.right() - ABYSS_W - Golden.S5 - CRAFT_W;
            if (mx >= cx && mx < cx + CRAFT_W) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TrialBoardNet.CraftShard(data.titan()));
                return true;
            }
            // 深淵のつまみ (物語を終えた人だけ押せる。グループごと。サーバーが確かめる)
            int ax = BODY.right() - ABYSS_W;
            if (mx >= ax && mx < ax + ABYSS_W) {
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
        g.fill(0, 0, width, height, argb(0.80f, BG));
        // 板は窓に合わせて一様に大きく描く (描くのと当たりは同じ変換。画素ちょうど)。論理の (0, 0) に置く
        fit.fit(width, height, W, H, 3f, 0.5f);
        int mouseX = (int) Math.floor(fit.x(screenX));
        int mouseY = (int) Math.floor(fit.y(screenY));
        fit.begin(g);
        Golden.panel(g, LAYOUT.panel());
        Golden.headLine(g, LAYOUT);
        Golden.Box head = LAYOUT.head();
        int hy = Golden.textY(head.y(), head.h());
        g.drawString(font, Component.translatable("gui.skyabyss.trial.title"), head.x() + Golden.S5, hy, argb(1f, GOLD), false);
        Component titan = Component.translatable(TrialCatalog.titanKey(data.titan()));
        Component sub = titan.copy().append("  ").append(Component.translatable("gui.skyabyss.trial.cycle", data.cycle()));
        g.drawString(font, sub, head.right() - Golden.S5 - font.width(sub), hy, argb(1f, TEAL), false);
        // 頁
        String[] tabs = {"gui.skyabyss.trial.tab.now", "gui.skyabyss.trial.tab.codex", "gui.skyabyss.trial.tab.vow", "gui.skyabyss.trial.tab.rewards", "gui.skyabyss.trial.tab.how"};
        for (int t = 0; t < TABS; t++) {
            int tx = BODY.x() + t * (TAB_W + Golden.S3);
            boolean hover = mouseX >= tx && mouseX < tx + TAB_W && mouseY >= TOOL_Y && mouseY < TOOL_Y + ROW;
            Golden.tab(g, font, new Golden.Box(tx, TOOL_Y, TAB_W, ROW), Component.translatable(tabs[t]), tab == t, hover);
        }
        if (data.atShrine()) {
            renderGo(g, font, mouseX, mouseY);
        }
        switch (tab) {
            case 0 -> renderNow(g, font);
            case 1 -> renderCodex(g, font);
            case 2 -> renderVows(g, font, mouseX, mouseY);
            case 3 -> renderRewards(g, font, mouseX, mouseY);
            default -> renderHow(g, font);
        }
        // 足: 順路の記録・歌片・称号と、歌片を作る・深淵のつまみ
        renderFoot(g, font);
        renderAbyssToggle(g, font, BODY.right() - ABYSS_W, FOOT_BTN_Y, mouseX, mouseY);
        renderCraft(g, font, BODY.right() - ABYSS_W - Golden.S5 - CRAFT_W, FOOT_BTN_Y, mouseX, mouseY);
        fit.end(g);
    }

    // 仲間と渡る (金の縁。祠の祭司の「仲間と渡る」と同じ)
    private void renderGo(GuiGraphics g, Font font, int mx, int my) {
        boolean hover = mx >= GO_X && mx < GO_X + GO_W && my >= TOOL_Y && my < TOOL_Y + ROW;
        g.fill(GO_X, TOOL_Y, GO_X + GO_W, TOOL_Y + ROW, argb(1f, hover ? 0x3A3018 : 0x241E10));
        Golden.outline(g, GO_X, TOOL_Y, GO_X + GO_W, TOOL_Y + ROW, argb(1f, GOLD));
        jp.revati.skyabyss.client.ui.FitText.drawCentered(g, font, Component.translatable("gui.skyabyss.shrine.go"), GO_X + GO_W / 2, Golden.textY(TOOL_Y, ROW),
                GO_W - 4, argb(1f, hover ? 0xFFF0C0 : GOLD), false);
        if (hover) {
            g.renderTooltip(font, font.split(Component.translatable("gui.skyabyss.shrine.go_tip"), 200), mx, my);
        }
    }

    // 誓いの頁: 一行目に段と素材の倍率 (選べないときは理由)、その下に誓いを一つずつ (立てているものは金)
    private static final int VOW_TOP = CONTENT.y() + ROW + Golden.S5;
    private static final int VOW_ROW = ROW + Golden.S5;
    private static final int SOFT_DESC = 0xA9AEBC;

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
        for (int i = 0; i < data.vows().size(); i++) {
            TrialBoardNet.VowLine v = data.vows().get(i);
            int y = VOW_TOP + i * VOW_ROW;
            if (y + VOW_ROW > CONTENT.bottom()) {
                break;
            }
            boolean hover = i == hoverRow;
            g.fill(CONTENT.x(), y, CONTENT.right(), y + VOW_ROW - 2, argb(1f, v.on() ? 0x2A2618 : hover ? 0x1E2230 : CARD));
            Golden.outline(g, CONTENT.x(), y, CONTENT.right(), y + VOW_ROW - 2, argb(1f, v.on() ? GOLD : hover ? TEAL : 0x2C3846));
            String box = v.on() ? "◆ " : "◇ ";
            Component name = Component.literal(box).append(Component.translatable("vow.skyabyss." + v.id())).append(Component.literal("  +" + v.points()));
            int ty = Golden.textY(y, VOW_ROW - 2);
            g.drawString(font, name, x0, ty, argb(1f, v.on() ? GOLD : open ? TEXT : DIM), false);
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
                jp.revati.skyabyss.rpg.client.skill.SkillScreenAccess.aspectTile(g, font, cx, cy, tile, a, lv, hover, (int) (mc.level == null ? 0 : mc.level.getGameTime()));
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
            Component text = Component.empty().append(Component.translatable(part[0]).withStyle(st -> st.withColor(GOLD)))
                    .append(Component.literal("　")).append(Component.translatable(part[1]).withStyle(st -> st.withColor(TEXT)));
            for (FormattedCharSequence line : font.split(text, w)) {
                if (y + 9 > CONTENT.bottom()) {
                    return;
                }
                g.drawString(font, line, x0, y, 0xFFFFFFFF, false);
                y += 10;
            }
            y += Golden.S5;
        }
    }

    // 歌片を作るボタン (祠の東の縁の共鳴の炉)
    private void renderCraft(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        boolean hover = mx >= x && mx < x + CRAFT_W && my >= y && my < y + ROW;
        g.fill(x, y, x + CRAFT_W, y + ROW, argb(1f, hover ? 0x2A2618 : CARD));
        Golden.outline(g, x, y, x + CRAFT_W, y + ROW, argb(1f, hover ? GOLD : 0x4A4A2C));
        Component label = Component.translatable("gui.skyabyss.shrine.craft");
        g.drawString(font, label, x + CRAFT_W / 2 - font.width(label) / 2, Golden.textY(y, ROW), argb(1f, hover ? GOLD : TEAL), false);
        if (hover) {
            g.renderTooltip(font, Component.translatable("gui.skyabyss.shrine.craft_tip", jp.revati.skyabyss.trial.TrialBoard.SHARD_MATERIALS,
                    jp.revati.skyabyss.trial.TrialBoard.SHARD_RESONANCE), mx, my);
        }
    }

    // 深淵のつまみ: 閉じている (転生 3 回未満) は灰、選んでいないは暗い縁、選んでいるは赤い縁で脈打つ
    private void renderAbyssToggle(GuiGraphics g, Font font, int x, int y, int mx, int my) {
        boolean open = data.abyssOpen();
        boolean on = data.abyssOn();
        boolean hover = open && mx >= x && mx < x + ABYSS_W && my >= y && my < y + ROW;
        float pulse = on ? 0.75f + 0.25f * (float) Math.sin(age * 0.2) : 1f;
        int line = on ? ABYSS : open ? (hover ? 0x8C4A50 : 0x4A2C32) : 0x2C3846;
        g.fill(x, y, x + ABYSS_W, y + ROW, argb(1f, on ? 0x2A1216 : open ? 0x181014 : CARD));
        Golden.outline(g, x, y, x + ABYSS_W, y + ROW, argb(1f, line));
        String key = !open ? "gui.skyabyss.trial.abyss.locked" : on ? "gui.skyabyss.trial.abyss.on" : "gui.skyabyss.trial.abyss.off";
        Component label = Component.translatable(key);
        jp.revati.skyabyss.client.ui.FitText.drawCentered(g, font, label, x + ABYSS_W / 2, Golden.textY(y, ROW), ABYSS_W - 4, argb(pulse, on ? 0xFF8A90 : open ? 0xC8A0A6 : DIM), false);
        if (hover) {
            g.renderTooltip(font, Component.translatable("gui.skyabyss.trial.abyss.tip"), mx, my);
        }
    }

    // いまの三つ: 同じ幅の三列の札。上から 枠の絵と名前・類と段・説明 (入るだけ。あふれは「…」)・修飾 (一つずつ一行)・状態
    private void renderNow(GuiGraphics g, Font font) {
        int colW = (CONTENT.w() - 2 * Golden.GAP) / 3;
        int i = 0;
        for (TrialBoardNet.Card card : data.cards()) {
            Trial.Def def = TrialCatalog.get(card.cellId());
            if (def == null || i >= 3) {
                continue;
            }
            int x = CONTENT.x() + i * (colW + Golden.GAP);
            int y = CONTENT.y();
            int h = CONTENT.h();
            i++;
            int rc = rarityColor(card.rarity());
            Golden.recess(g, new Golden.Box(x, y, colW, h));
            Golden.outline(g, x, y, x + colW, y + h, argb(1f, rc));
            int tx = x + Golden.S5;
            int tw = colW - 2 * Golden.S5;
            // 枠の絵と名前
            icon(g, slotIcon(def.slot()), tx, y + 2, 12, 1f);
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, Component.translatable(def.nameKey()), tx + 15, Golden.textY(y + 1, ROW), tw - 15, argb(1f, GOLD), true);
            // 類と段
            int ky = y + 1 + ROW;
            icon(g, I_KIND + def.kind().ordinal(), tx, ky + 1, 12, 1f);
            Component kind = Component.translatable("trial.skyabyss.kind." + def.kind().id());
            String st = stars(def.tier());
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, kind, tx + 15, Golden.textY(ky, ROW), tw - 15 - font.width(st) - 4, argb(1f, TEAL), false);
            g.drawString(font, st, tx + tw - font.width(st), Golden.textY(ky, ROW), argb(1f, GOLD), false);
            // 説明: 修飾の札と状態の行を残して入るだけ。入りきらなければ最後の行を横へ流す
            int dy = ky + ROW + 2;
            int bottomRow = y + h - 2 - ROW;
            int chipsH = card.chips().isEmpty() ? ROW : card.chips().size() * 16;
            int maxLines = Math.max(2, (bottomRow - 3 - chipsH - dy) / ROW);
            // 入りきらない行は最後の行へつないで横へ流す (Marquee。前は「…」で切っていた)
            List<FormattedCharSequence> dl = jp.revati.skyabyss.client.ui.Marquee.joinRest(
                    jp.revati.skyabyss.client.ui.JpWrap.split(font, Component.translatable(def.descKey()), tw), maxLines);
            int shown = dl.size();
            for (int l = 0; l < shown; l++) {
                int ly = Golden.textY(dy + l * ROW, ROW);
                jp.revati.skyabyss.client.ui.Marquee.draw(g, font, dl.get(l), tx, ly, tw, argb(1f, DIM), false);
            }
            // 修飾の札 (一つずつ一行)
            int cy = dy + shown * ROW + 3;
            if (card.chips().isEmpty()) {
                g.drawString(font, Component.translatable("trial.skyabyss.chip.plain"), tx, Golden.textY(cy, ROW), argb(1f, DIM), false);
            }
            for (TrialBoardNet.Chip chip : card.chips()) {
                if (cy + 14 > bottomRow) {
                    break;
                }
                int col = rarityColor(chip.rarity());
                g.fill(tx, cy, tx + tw, cy + 14, 0xFF0E1118);
                Golden.outline(g, tx, cy, tx + tw, cy + 14, argb(1f, col));
                icon(g, chipIcon(chip), tx + 2, cy + 1, 12, 1f);
                jp.revati.skyabyss.client.ui.FitText.draw(g, font, chipText(chip), tx + 16, cy + 3, tw - 18, argb(1f, chip.rarity() >= 1 ? col : TEXT), false);
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
                g.drawString(font, Component.translatable(badge), tx, Golden.textY(bottomRow, ROW), argb(1f, card.state() == 0 ? GOLD : GOOD), false);
            }
            if (card.clearedNow()) {
                Component done = Component.translatable("gui.skyabyss.trial.cleared_now");
                g.drawString(font, done, tx + tw - font.width(done), Golden.textY(bottomRow, ROW), argb(1f, GOOD), false);
            }
        }
    }

    // 図鑑: 行 13 (1 行おきに地の色)
    private void renderCodex(GuiGraphics g, Font font) {
        Golden.recess(g, CONTENT);
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
                    g.fill(bx, y + 5, bx + bw, y + 8, 0xFF0A0C12);
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

    // 下の段 (34): 一行目に順路の記録と歌片、二行目に称号 (右に歌片を作る・深淵のつまみ)
    private void renderFoot(GuiGraphics g, Font font) {
        Golden.Box foot = LAYOUT.foot();
        int x = foot.x() + Golden.S5;
        int r1 = Golden.textY(foot.y() + 4, ROW);
        int r2 = Golden.textY(foot.y() + 4 + ROW, ROW);
        // 順路の記録と歌片 (docs/design/32 の 6 章)
        long best = data.routeBest() / 20;
        Component record = data.routeClears() <= 0 ? Component.translatable("gui.skyabyss.shrine.record_none")
                : Component.translatable("gui.skyabyss.shrine.record", data.routeClears(), String.format("%d:%02d", best / 60, best % 60));
        Component shards = Component.translatable("gui.skyabyss.shrine.shards", data.shards());
        int sw = font.width(shards);
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, record, x, r1, foot.w() - Golden.S13 - sw - Golden.S8, argb(1f, TEAL), false);
        g.drawString(font, shards, foot.right() - Golden.S5 - sw, r1, argb(1f, data.shards() > 0 ? GOOD : GOLD), false);
        MutableComponent titles = Component.translatable("gui.skyabyss.trial.titles");
        if (data.titles().isEmpty()) {
            titles.append(Component.translatable("gui.skyabyss.trial.titles_none"));
        } else {
            for (int i = 0; i < data.titles().size(); i++) {
                titles.append(i > 0 ? "　" : "").append(Component.translatable("trial.skyabyss.title." + data.titles().get(i)));
            }
        }
        // 二行目の右は歌片を作る・深淵のつまみ (render が描く)。称号はその左まで
        int buttons = ABYSS_W + Golden.S5 + CRAFT_W;
        jp.revati.skyabyss.client.ui.FitText.draw(g, font, titles, x, r2, foot.w() - Golden.S8 - buttons - Golden.S8, argb(1f, GOLD), false);
    }

    @Override
    public int[] textBounds() {
        // 文字の範囲は描くときと同じ変換を通した画面の矩形 (論理の座標のままだと、倍率が 1 でない GUI で字が切れる)
        fit.fit(width, height, W, H, 3f, 0.5f);
        return fit.screenRect(0, 0, W, H);
    }
}
